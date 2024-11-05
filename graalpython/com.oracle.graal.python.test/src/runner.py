# Copyright (c) 2020, 2024, Oracle and/or its affiliates. All rights reserved.
# DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
#
# The Universal Permissive License (UPL), Version 1.0
#
# Subject to the condition set forth below, permission is hereby granted to any
# person obtaining a copy of this software, associated documentation and/or
# data (collectively the "Software"), free of charge and under any and all
# copyright rights in the Software, and any and all patent rights owned or
# freely licensable by each licensor hereunder covering either (i) the
# unmodified Software as contributed to or provided by such licensor, or (ii)
# the Larger Works (as defined below), to deal in both
#
# (a) the Software, and
#
# (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
# one is included with the Software each a "Larger Work" to which the Software
# is contributed by such licensors),
#
# without restriction, including without limitation the rights to copy, create
# derivative works of, display, perform, and distribute the Software and make,
# use, sell, offer for sale, import, export, have made, and have sold the
# Software and the Larger Work(s), and to sublicense the foregoing rights on
# either these or other terms.
#
# This license is subject to the following condition:
#
# The above copyright notice and either this complete permission notice or at a
# minimum a reference to the UPL must be included in all copies or substantial
# portions of the Software.
#
# THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
# IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
# FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
# AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
# LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
# OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
# SOFTWARE.
import argparse
import concurrent.futures
import enum
import fnmatch
import json
import math
import multiprocessing
import os
import pickle
import re
import shlex
import signal
import subprocess
import sys
import tempfile
import threading
import time
import tomllib
import traceback
import typing
import unittest
import unittest.loader
from abc import abstractmethod
from collections import defaultdict
from dataclasses import dataclass, field
from functools import lru_cache
from pathlib import Path

DIR = Path(__file__).parent.resolve()
UNIT_TEST_ROOT = (DIR / 'tests').resolve()
TAGGED_TEST_ROOT = (DIR.parent.parent / 'lib-python' / '3' / 'test').resolve()
IS_GRAALPY = sys.implementation.name == 'graalpy'


class Logger:
    report_incomplete = sys.stdout.isatty()

    def __init__(self):
        self.lock = threading.Lock()
        self.incomplete_line = None

    def log(self, msg='', incomplete=False):
        if incomplete and not self.report_incomplete:
            return
        with self.lock:
            end = ('' if incomplete else None)
            if self.report_incomplete and self.incomplete_line:
                first_line, newline, rest = msg.partition('\n')
                pad = len(self.incomplete_line)
                print(f"\r{first_line:{pad}}{newline}{rest}", file=sys.stderr, end=end, flush=True)
            else:
                print(msg, file=sys.stderr, end=end, flush=True)
            self.incomplete_line = msg if incomplete else None


log = Logger().log


class TestStatus(enum.StrEnum):
    SUCCESS = "ok"
    FAILURE = "FAIL"
    ERROR = "ERROR"
    SKIPPED = "skipped"
    EXPECTED_FAILURE = "expected failure"
    UNEXPECTED_SUCCESS = "unexpected success"


SUCCESSFUL_STATES = TestStatus.SUCCESS, TestStatus.SKIPPED, TestStatus.EXPECTED_FAILURE
FAILED_STATES = TestStatus.FAILURE, TestStatus.ERROR, TestStatus.UNEXPECTED_SUCCESS


@dataclass(repr=False, frozen=True)
class TestId:
    test_file: Path
    test_name: str

    def __repr__(self):
        return f'{self.test_file}::{self.test_name}'

    @classmethod
    def from_str(cls, s: str):
        test_file, _, test_id = s.partition('::')
        if test_id is None:
            raise ValueError(f"Invalid test ID {s}")
        return cls(Path(test_file), test_id)

    @classmethod
    def from_test_case(cls, test_file: Path, test: unittest.TestCase):
        test_id = test.id()
        if type(test).id is not unittest.TestCase.id:
            # Qualify doctests so that we know what they are
            test_id = f'{type(test).__qualname__}.{test_id}'
        return cls(test_file, test_id)


@dataclass(frozen=True)
class TestSpecifier:
    test_file: Path
    test_name: str | None

    def __repr__(self):
        if self.test_name is None:
            return str(self.test_file)
        return f'{self.test_file}::{self.test_name}'

    def with_test_file(self, test_file: Path):
        return TestSpecifier(test_file, self.test_name)

    @classmethod
    def from_str(cls, s: str):
        test_file, _, test_id = s.partition('::')
        return cls(Path(test_file), test_id or None)

    def match(self, test_id: TestId):
        # Globs in path are expanded when processing specifiers
        if self.test_file != test_id.test_file:
            return False
        if self.test_name is None:
            return True
        if '*' in self.test_name:
            pattern = re.escape(self.test_name).replace(r'\*', '.*') + r'($|\..*)'
            return re.match(pattern, test_id.test_name)
        return self.test_name == test_id.test_name or test_id.test_name.startswith(self.test_name + '.')


@dataclass
class TestResult:
    test_id: TestId
    status: TestStatus
    param: str | None = None
    output: str = ''
    duration: float = 0.0


def format_exception(exc):
    tb = exc.__traceback__
    while tb and '__unittest' in tb.tb_frame.f_globals:
        tb = tb.tb_next
    exc.__traceback__ = tb
    return ''.join(traceback.format_exception(exc))


def out_tell():
    try:
        return os.lseek(1, 0, os.SEEK_CUR)
    except OSError:
        return -1


T = typing.TypeVar('T')


def partition_list(l: list[T], fn: typing.Callable[[T], bool]):
    a = []
    b = []
    for item in l:
        (a if fn(item) else b).append(item)
    return a, b


class AbstractResult(unittest.TestResult):
    def __init__(self, test_suite: 'TestSuite'):
        super().__init__()
        self.test_suite = test_suite
        self.start_time = None

    def test_id(self, test):
        return TestId.from_test_case(self.test_suite.test_file.path, test)

    def startTest(self, test):
        self.start_time = time.time()

    @abstractmethod
    def report_result(self, result: TestResult):
        pass

    def make_result(self, test, status: TestStatus, **kwargs):
        return TestResult(status=status, test_id=self.test_id(test), duration=(time.time() - self.start_time), **kwargs)

    def addSuccess(self, test):
        super().addSuccess(test)
        self.report_result(self.make_result(test, status=TestStatus.SUCCESS))

    def addFailure(self, test, err):
        super().addFailure(test, err)
        self.report_result(self.make_result(test, status=TestStatus.FAILURE, param=format_exception(err[1])))

    def addError(self, test, err):
        super().addError(test, err)
        self.report_result(self.make_result(test, status=TestStatus.ERROR, param=format_exception(err[1])))

    def addSkip(self, test, reason):
        super().addSkip(test, reason)
        self.report_result(self.make_result(test, status=TestStatus.SKIPPED, param=reason))

    def addExpectedFailure(self, test, err):
        super().addExpectedFailure(test, err)
        self.report_result(self.make_result(test, status=TestStatus.EXPECTED_FAILURE, param=format_exception(err[1])))

    def addUnexpectedSuccess(self, test):
        super().addUnexpectedSuccess(test)
        self.report_result(self.make_result(test, status=TestStatus.UNEXPECTED_SUCCESS))


class DirectResult(AbstractResult):
    def __init__(self, test_suite: 'TestSuite', test_runner: 'TestRunner'):
        super().__init__(test_suite)
        self.runner = test_runner

    def report_result(self, result: TestResult):
        self.runner.report_result(result)

    def startTest(self, test):
        super().startTest(test)
        self.runner.report_start(self.test_id(test))


class AbstractRemoteResult(AbstractResult):
    @abstractmethod
    def emit(self, **data):
        pass

    def report_result(self, result: TestResult):
        self.emit(
            event='testResult',
            status=result.status,
            test=result.test_id,
            param=result.param,
            out_pos=out_tell(),
            duration=result.duration,
        )

    def startTest(self, test):
        super().startTest(test)
        self.emit(
            event='testStarted',
            test=self.test_id(test),
            out_pos=out_tell(),
        )


class PipeResult(AbstractRemoteResult):
    def __init__(self, test_suite: 'TestSuite', conn):
        super().__init__(test_suite)
        self.conn = conn

    def emit(self, **data):
        self.conn.send(data)


class SimpleResult(AbstractRemoteResult):
    def __init__(self, test_suite: 'TestSuite', data: list[dict]):
        super().__init__(test_suite)
        self.data = data

    def emit(self, **data):
        self.data.append(data)


def test_path_to_module(test_file: 'TestFile'):
    path = test_file.path.resolve().relative_to(test_file.config.rootdir)
    return str(path).removesuffix('.py').replace(os.sep, '.')


class TestRunner:
    def __init__(self, *, failfast: bool, report_durations: int | None):
        self.failfast = failfast
        self.report_durations = report_durations
        self.results: list[TestResult] = []
        self.total_duration = 0.0

    @staticmethod
    def report_start(test_id: TestId):
        log(f"{test_id} ... ", incomplete=True)

    def report_result(self, result: TestResult):
        self.results.append(result)
        message = f"{result.test_id} ... {result.status}"
        if result.status == TestStatus.SKIPPED and result.param:
            message = f"{message} {result.param!r}"
        log(message)

    def tests_failed(self):
        return any(result.status in FAILED_STATES for result in self.results)

    def display_summary(self):
        counts = defaultdict(int)
        for result in self.results:
            counts[result.status] += 1

        log()

        if self.results and self.report_durations:
            slowest_tests = sorted(self.results, key=lambda r: r.duration, reverse=True)
            if self.report_durations > 0:
                slowest_tests = slowest_tests[:self.report_durations]
            log("Slowest test durations:")
            for result in slowest_tests:
                log(f"- {result.test_id}: {result.duration:.2f}s")
            log()

        for result in self.results:
            fail_type = None
            match result.status:
                case TestStatus.ERROR:
                    fail_type = 'ERROR'
                case TestStatus.FAILURE:
                    fail_type = 'FAIL'
                case TestStatus.UNEXPECTED_SUCCESS:
                    fail_type = 'UNEXPECTED SUCCESS'
            if fail_type:
                log("======================================================================")
                log(f"{fail_type}: {result.test_id}")
                log("----------------------------------------------------------------------")
                if result.param:
                    log(result.param.rstrip('\n'))
                if result.output:
                    log("------------------------- captured output ----------------------------")
                    log(result.output.rstrip('\n'))
                log()

        items = []

        def add_item(status, name):
            if counts[status]:
                items.append(f"{name}={counts[status]}")

        add_item(TestStatus.SUCCESS, "passed")
        add_item(TestStatus.FAILURE, "failures")
        add_item(TestStatus.ERROR, "errors")
        add_item(TestStatus.SKIPPED, "skipped")
        add_item(TestStatus.EXPECTED_FAILURE, "expected failures")
        add_item(TestStatus.UNEXPECTED_SUCCESS, "unexpected successes")

        total = sum(counts.values())

        log(f"Ran {total} tests in {self.total_duration:.2f}s")
        log()
        summary = ", ".join(items)
        overall_status = 'FAILED' if self.tests_failed() else 'OK'
        if summary:
            log(f"{overall_status} ({summary})")
        else:
            log(overall_status)

        if self.failfast and self.tests_failed():
            log("\nWARNING: Did not execute all tests because 'failfast' mode is on")

    def run_tests(self, tests: list['TestSuite']):
        start_time = time.time()
        for test_suite in tests:
            result = DirectResult(test_suite, self)
            result.failfast = self.failfast
            test_suite.run(result)
        self.total_duration = time.time() - start_time
        self.display_summary()

    def generate_mx_report(self, path: str):
        report_data = []
        for result in self.results:
            match result.status:
                case TestStatus.SUCCESS | TestStatus.EXPECTED_FAILURE:
                    status = 'PASSED'
                case TestStatus.SKIPPED:
                    status = 'IGNORED'
                case _:
                    status = 'FAILED'
            report_data.append({
                'name': str(result.test_id),
                'status': status,
                'duration': result.duration,
            })
        with open(path, 'w') as f:
            # noinspection PyTypeChecker
            json.dump(report_data, f)

    def generate_tags(self, append=False):
        by_file = defaultdict(list)
        for result in self.results:
            by_file[result.test_id.test_file].append(result)
        for test_file, results in by_file.items():
            test_file = configure_test_file(test_file)
            tag_file = test_file.get_tag_file()
            if not tag_file:
                log(f"WARNNING: no tag directory for test file {test_file}")
                continue
            tags = {result.test_id.test_name for result in results if result.status == TestStatus.SUCCESS}
            if append:
                tags |= {test.test_name for test in read_tags(test_file)}
            with open(tag_file, 'w') as f:
                for test_name in sorted(tags):
                    f.write(f'{test_name}\n')


def interrupt_process(process: subprocess.Popen):
    sig = signal.SIGINT if sys.platform != 'win32' else signal.CTRL_C_EVENT
    process.send_signal(sig)
    try:
        process.wait(3)
    except subprocess.TimeoutExpired:
        process.terminate()
        try:
            process.wait(3)
        except subprocess.TimeoutExpired:
            process.kill()


class ParallelTestRunner(TestRunner):
    def __init__(self, *, num_processes, subprocess_args, separate_workers, **kwargs):
        super().__init__(**kwargs)
        self.num_processes = num_processes
        self.subprocess_args = subprocess_args
        self.separate_workers = separate_workers
        self.stop_event = threading.Event()
        self.crashes = []
        self.default_test_timeout = 600

    def report_result(self, result: TestResult):
        if self.failfast and result.status in FAILED_STATES:
            self.stop_event.set()
        super().report_result(result)

    def tests_failed(self):
        return super().tests_failed() or bool(self.crashes)

    def partition_tests_into_processes(self, suites: list['TestSuite']) -> list[list['Test']]:
        if self.separate_workers:
            per_file_suites = suites
            unpartitioned = []
        else:
            per_file_suites, unpartitioned = partition_list(
                suites,
                lambda suite: suite.test_file.config.new_worker_per_file,
            )
        partitions = [suite.collected_tests for suite in per_file_suites]
        per_partition = int(math.ceil(len(unpartitioned) / max(1, self.num_processes)))
        while unpartitioned:
            partitions.append([test for suite in unpartitioned[:per_partition] for test in suite.collected_tests])
            unpartitioned = unpartitioned[per_partition:]
        return partitions

    def run_tests(self, tests: list['TestSuite']):
        serial_suites, parallel_suites = partition_list(
            tests,
            lambda suite: suite.test_file.test_config.serial,
        )
        parallel_partitions = self.partition_tests_into_processes(parallel_suites)
        serial_partitions = self.partition_tests_into_processes(serial_suites)

        start_time = time.time()
        if parallel_partitions:
            num_processes = max(1, min(self.num_processes, len(parallel_partitions)))
            with concurrent.futures.ThreadPoolExecutor(num_processes) as executor:
                self.run_partitions_in_subprocesses(executor, parallel_partitions)
        if serial_partitions:
            with concurrent.futures.ThreadPoolExecutor(1) as executor:
                self.run_partitions_in_subprocesses(executor, serial_partitions)

        self.total_duration = time.time() - start_time
        self.display_summary()

        if self.crashes:
            for crash in self.crashes:
                log('Internal error, test worker crashed outside of tests:')
                log(crash)

    def run_partitions_in_subprocesses(self, executor, partitions: list[list['Test']]):
        futures = [
            executor.submit(self.run_in_subprocess_and_watch, partition)
            for partition in partitions
        ]
        try:
            concurrent.futures.wait(futures)
            for future in futures:
                future.result()
        except KeyboardInterrupt:
            self.stop_event.set()
            concurrent.futures.wait(futures)
            print("Interrupted!")
            sys.exit(1)

    def run_in_subprocess_and_watch(self, tests: list['Test']):
        # noinspection PyUnresolvedReferences
        use_pipe = sys.platform != 'win32' and (not IS_GRAALPY or __graalpython__.posix_module_backend() == 'native')
        tests_by_id = {test.test_id: test for test in tests}
        remaining_test_ids = [test.test_id for test in tests]
        last_started_test: Test | None = None
        last_started_time: float | None = None
        with tempfile.TemporaryDirectory(prefix='graalpytest-') as tmp_dir:
            tmp_dir = Path(tmp_dir)
            env = os.environ.copy()
            env['IN_PROCESS'] = '1'

            if use_pipe:
                pipe, child_pipe = multiprocessing.Pipe()
            else:
                result_file = tmp_dir / 'result'

            def process_event(event):
                nonlocal remaining_test_ids, last_started_test, last_started_time, last_out_pos
                match event['event']:
                    case 'testStarted':
                        remaining_test_ids.remove(event['test'])
                        self.report_start(event['test'])
                        last_started_test = tests_by_id[event['test']]
                        last_started_time = time.time()
                        last_out_pos = event['out_pos']
                    case 'testResult':
                        out_end = event['out_pos']
                        test_output = ''
                        if last_out_pos != out_end:
                            out_file.seek(last_out_pos)
                            test_output = out_file.read(out_end - last_out_pos)
                        result = TestResult(
                            test_id=event['test'],
                            status=event['status'],
                            param=event.get('param'),
                            output=test_output,
                            duration=event.get('duration'),
                        )
                        self.report_result(result)
                        last_started_test = None
                        last_started_time = None
                        last_out_pos = event['out_pos']

            while remaining_test_ids and not self.stop_event.is_set():
                with (
                    open(tmp_dir / 'out', 'w+') as out_file,
                    open(tmp_dir / 'tests', 'w+') as tests_file,
                ):
                    last_out_pos = 0
                    cmd = [
                        sys.executable,
                        '-u',
                        *self.subprocess_args,
                        __file__,
                        '--tests-file', str(tests_file.name),
                    ]
                    if use_pipe:
                        cmd += ['--pipe-fd', str(child_pipe.fileno())]
                    else:
                        cmd += ['--result-file', str(result_file)]
                    if self.failfast:
                        cmd.append('--failfast')
                    # We communicate the tests through a temp file to avoid running into too long commandlines on windows
                    tests_file.seek(0)
                    tests_file.truncate()
                    tests_file.write('\n'.join(map(str, remaining_test_ids)))
                    tests_file.flush()
                    popen_kwargs: dict = dict(
                        stdout=out_file,
                        stderr=out_file,
                        env=env,
                    )
                    if use_pipe:
                        popen_kwargs.update(pass_fds=[child_pipe.fileno()])
                    process = subprocess.Popen(cmd, **popen_kwargs)

                    timed_out = False

                    if use_pipe:
                        while process.poll() is None:
                            while pipe.poll(0.1):
                                process_event(pipe.recv())
                            if self.stop_event.is_set():
                                interrupt_process(process)
                                break
                            if last_started_test is not None:
                                timeout = last_started_test.test_file.test_config.per_test_timeout
                                if time.time() - last_started_time >= timeout:
                                    interrupt_process(process)
                                    timed_out = True
                                    # Drain the pipe
                                    while pipe.poll(0.1):
                                        pipe.recv()
                                    break

                    returncode = process.wait()
                    if self.stop_event.is_set():
                        return
                    if use_pipe:
                        while pipe.poll(0.1):
                            process_event(pipe.recv())
                    else:
                        with open(result_file, 'rb') as f:
                            for file_event in pickle.load(f):
                                process_event(file_event)

                    if returncode != 0 or timed_out:
                        out_file.seek(last_out_pos)
                        output = out_file.read()
                        if last_started_test:
                            if timed_out:
                                message = "Timed out"
                            elif returncode >= 0:
                                message = f"Test process exitted with code {returncode}"
                            else:
                                try:
                                    signal_name = signal.Signals(-returncode).name
                                except ValueError:
                                    signal_name = str(-returncode)
                                message = f"Test process killed by signal {signal_name}"
                            self.report_result(TestResult(
                                test_id=last_started_test.test_id,
                                status=TestStatus.ERROR,
                                param=message,
                                output=output,
                            ))
                            continue
                        else:
                            # Crashed outside of tests, don't retry
                            self.crashes.append(output or 'Runner subprocess crashed')
                            return


@dataclass
class TestFileConfig:
    serial: bool = False
    partial_splits: bool = False
    per_test_timeout: float = 300
    exclude: bool = False

    @classmethod
    def from_dict(cls, config: dict):
        exclude_keys = {sys.platform}
        if IS_GRAALPY:
            # noinspection PyUnresolvedReferences
            exclude_keys.add('native_image' if __graalpython__.is_native else 'jvm')
        return cls(
            serial=config.get('serial', cls.serial),
            partial_splits=config.get('partial_splits_individual_tests', cls.partial_splits),
            per_test_timeout=config.get('per_test_timeout', cls.per_test_timeout),
            exclude=bool(set(config.get('exclude_on', set())) & exclude_keys),
        )

    def combine(self, other: 'TestFileConfig'):
        return TestFileConfig(
            serial=self.serial or other.serial,
            partial_splits=self.partial_splits or other.partial_splits,
            per_test_timeout=max(self.per_test_timeout, other.per_test_timeout),
            exclude=self.exclude or other.exclude,
        )


class TestFileRule:
    def __init__(self, rule: dict):
        selector = [name.removesuffix('.py') for name in rule['selector']]
        globs, exact_matches = partition_list(selector, lambda x: '*' in x)
        self.exact_matches = frozenset(exact_matches)
        self.globs = [re.compile(fnmatch.translate(glob)) for glob in globs]
        self.test_config = TestFileConfig.from_dict(rule)

    def matches(self, name):
        return name in self.exact_matches or any(glob.match(name) for glob in self.globs)


@dataclass
class Config:
    configdir: Path = Path('.').resolve()
    rootdir: Path = Path('.').resolve()
    tags_dir: Path | None = None
    run_top_level_functions: bool = False
    new_worker_per_file: bool = False
    rules: list[TestFileRule] = field(default_factory=list)

    @classmethod
    @lru_cache
    def parse_config(cls, config_path):
        with open(config_path, 'rb') as f:
            config_dict = tomllib.load(f)
            settings = config_dict.get('settings', {})
            rules = [TestFileRule(rule) for rule in config_dict.get('test_rules', ())]
            tags_dir = None
            if config_tags_dir := settings.get('tags_dir'):
                tags_dir = (config_path.parent / config_tags_dir).resolve()
            return cls(
                configdir=config_path.parent.resolve(),
                rootdir=config_path.parent.parent.resolve(),
                tags_dir=tags_dir,
                run_top_level_functions=settings.get('run_top_level_functions', cls.run_top_level_functions),
                new_worker_per_file=settings.get('new_worker_per_file', cls.new_worker_per_file),
                rules=rules,
            )


@dataclass(frozen=True)
class TestFile:
    path: Path
    name: str
    config: Config
    test_config: TestFileConfig

    def __str__(self):
        return str(self.path)

    def __eq__(self, other):
        return self.path == other.path

    def get_tag_file(self):
        if self.config.tags_dir:
            return self.config.tags_dir / (self.name.removesuffix('.py') + '.txt')


def configure_test_file(path: Path) -> TestFile:
    config = config_for_file(path)
    resolved = path.resolve().relative_to(config.configdir)
    name = str(resolved).removesuffix('.py')
    test_config = TestFileConfig()
    for rule in config.rules:
        if rule.matches(name):
            test_config = test_config.combine(rule.test_config)
    return TestFile(
        path=path,
        name=name,
        config=config,
        test_config=test_config
    )


@lru_cache
def config_for_dir(path: Path) -> Config:
    while path.is_dir():
        config_path = path / 'conftest.toml'
        if config_path.exists():
            return Config.parse_config(config_path)
        if path.parent == path:
            break
        path = path.parent
    return Config()


def config_for_file(test_file: Path) -> Config:
    path = test_file if test_file.is_dir() else test_file.parent
    return config_for_dir(path)


@dataclass
class TestSuite:
    test_file: TestFile
    pythonpath: list[str]
    test_suite: unittest.TestSuite
    collected_tests: list['Test']

    def run(self, result):
        saved_path = sys.path[:]
        sys.path[:] = self.pythonpath
        try:
            self.test_suite.run(result)
        finally:
            sys.path[:] = saved_path


@dataclass
class Test:
    test_id: TestId
    test_file: TestFile

    def __str__(self):
        return repr(self.test_id)


def filter_tree(test_file: TestFile, test_suite: unittest.TestSuite, specifiers: list[TestSpecifier],
                tags: list[TestId] | None):
    keep_tests = []
    collected_tests = []
    for test in test_suite:
        # When test loading fails, unittest just creates an instance of _FailedTest
        if exception := getattr(test, '_exception', None):
            raise exception
        if hasattr(test, '__iter__'):
            sub_collected = filter_tree(test_file, test, specifiers, tags)
            if sub_collected:
                keep_tests.append(test)
                collected_tests += sub_collected
        else:
            test_id = TestId.from_test_case(test_file.path, test)
            if any(s.match(test_id) for s in specifiers):
                if tags is None or test_id in tags:
                    keep_tests.append(test)
                    collected_tests.append(Test(test_id, test_file))
    test_suite._tests = keep_tests
    return collected_tests


def expand_specifier_paths(specifiers: list[TestSpecifier]) -> list[TestSpecifier]:
    expanded_specifiers = []
    for specifier in specifiers:
        str_path = str(specifier.test_file)
        if '*' in str_path:
            paths = list(Path('.').glob(str_path))
        else:
            paths = [specifier.test_file]
        expanded_paths = []
        for path in paths:
            if not path.exists():
                with_suffix = path.parent / f'{path.name}.py'
                if with_suffix.exists():
                    path = with_suffix
                else:
                    sys.exit(f"Test path {path} doesn't exist")
            config = config_for_dir(path)
            if path.is_dir():
                if config.tags_dir:
                    if path.parent.absolute() == config.rootdir.absolute():
                        expanded_paths += path.glob('test_*.py')
                        expanded_paths += (p.parent for p in path.glob('test_*/__init__.py'))
                    else:
                        if (path / '__init__.py').exists():
                            expanded_paths.append(path)
                else:
                    expanded_paths += path.rglob("test*.py")
            else:
                if config.tags_dir and path.name == '__init__.py':
                    path = path.parent
                expanded_paths.append(path)
        expanded_paths.sort()
        for path in expanded_paths:
            expanded_specifiers.append(specifier.with_test_file(path))
    return expanded_specifiers


def collect_module(test_file: TestFile, specifiers: list[TestSpecifier], use_tags=False,
                   partial=None) -> TestSuite | None:
    config = test_file.config
    saved_path = sys.path[:]
    sys.path.insert(0, str(config.rootdir))
    try:
        loader = TopLevelFunctionLoader() if config.run_top_level_functions else unittest.TestLoader()
        tags = None
        if use_tags and config.tags_dir:
            tags = read_tags(test_file)
            if not tags:
                return None
        test_module = test_path_to_module(test_file)
        try:
            test_suite = loader.loadTestsFromName(test_module)
        except unittest.SkipTest as e:
            log(f"Test file {test_file} skipped: {e}")
            return
        collected_tests = filter_tree(test_file, test_suite, specifiers, tags)
        if partial and test_file.test_config.partial_splits:
            selected, total = partial
            collected_tests = collected_tests[selected::total]
        if collected_tests:
            return TestSuite(test_file, sys.path[:], test_suite, collected_tests)
    finally:
        sys.path[:] = saved_path


def path_for_comparison(p: Path):
    p = p.resolve()
    return p.parent / p.name.removesuffix('.py')


def collect(all_specifiers: list[TestSpecifier], *, use_tags=False, ignore=None, partial=None,
            continue_on_errors=False, no_excludes=False) -> list[TestSuite]:
    to_run = []
    all_specifiers = expand_specifier_paths(all_specifiers)
    test_files = []
    test_paths = set()
    for specifier in all_specifiers:
        if specifier.test_file in test_paths:
            continue
        test_paths.add(specifier.test_file)
        if not specifier.test_file.exists():
            sys.exit(f"File does not exist: {specifier.test_file}")
        test_files.append(configure_test_file(specifier.test_file))
    if ignore:
        ignore = [path_for_comparison(i) for i in ignore]
        test_files = [
            test_file for test_file in test_files
            if any(path_for_comparison(test_file.path).is_relative_to(i) for i in ignore)
        ]
    if not no_excludes:
        excluded, test_files = partition_list(test_files, lambda f: f.test_config.exclude)
        for file in excluded:
            log(f"Test file {file} is excluded on this platform/configuration, use --no-excludes to overrride")
    if partial:
        selected, total = partial
        to_split = []
        partial_files = []
        # Always keep files that are split per-test
        for test_file in test_files:
            if test_file.test_config.partial_splits:
                partial_files.append(test_file)
            else:
                to_split.append(test_file)
        partial_files += to_split[selected::total]
        test_files = [f for f in test_files if f in partial_files]
    for test_file in test_files:
        specifiers = [s for s in all_specifiers if s.test_file == test_file.path]
        try:
            collected = collect_module(test_file, specifiers, use_tags=use_tags, partial=partial)
        except Exception as e:
            if continue_on_errors:
                log(f"WARNING: Failed to collect {test_file}:\n{format_exception(e)}")
                continue
            raise e
        if collected:
            to_run.append(collected)
    return to_run


def read_tags(test_file: TestFile) -> list[TestId]:
    tag_file = test_file.get_tag_file()
    tags = []
    if tag_file.exists():
        with open(tag_file) as f:
            for line in f:
                test = line.strip()
                tags.append(TestId(test_file.path, test))
        return tags
    return tags


class TopLevelFunctionLoader(unittest.loader.TestLoader):
    def loadTestsFromModule(self, module, *, pattern=None):
        test_suite = super().loadTestsFromModule(module, pattern=pattern)
        for name, obj in vars(module).items():
            if name.startswith('test_') and callable(obj):
                test_suite.addTest(unittest.FunctionTestCase(obj))
        return test_suite


def in_process():
    parser = argparse.ArgumentParser()
    group = parser.add_mutually_exclusive_group()
    group.add_argument('--pipe-fd', type=int)
    group.add_argument('--result-file', type=Path)
    parser.add_argument('--tests-file', type=Path, required=True)
    parser.add_argument('--failfast', action='store_true')
    args = parser.parse_args()
    tests = []
    with open(args.tests_file) as f:
        for line in f:
            tests.append(TestSpecifier.from_str(line.strip()))

    data = []
    if args.pipe_fd:
        import multiprocessing.connection
        conn = multiprocessing.connection.Connection(args.pipe_fd)

        def result_factory(suite):
            return PipeResult(suite, conn)
    else:
        def result_factory(suite):
            return SimpleResult(suite, data)

    for test_suite in collect(tests, no_excludes=True):
        result = result_factory(test_suite)
        result.failfast = args.failfast
        test_suite.run(result)

    if args.result_file:
        with open(args.result_file, 'wb') as f:
            # noinspection PyTypeChecker
            pickle.dump(data, f)


def get_bool_env(name: str):
    return os.environ.get(name, '').lower() in ('true', '1')


def main():
    is_mx_graalpytest = get_bool_env('MX_GRAALPYTEST')
    parser = argparse.ArgumentParser(prog=('mx graalpytest' if is_mx_graalpytest else None))
    if is_mx_graalpytest:
        # mx graalpytest takes this option, but it forwards --help here, so pretend we take it
        parser.add_argument('--python', help="Run tests with given Python binary")
    parser.add_argument('-t', '--tagged', action='store_true',
                        help="Interpret test file names relative to tagged test directory")
    parser.add_argument('-n', '--num-processes', type=int,
                        help="Run tests in N subprocess workers. Adds crash recovery, output capture and timeout handling")
    parser.add_argument('--separate-workers', action='store_true',
                        help="Create a new worker process for each test file (when -n is specified). Default for tagged unit tests")
    parser.add_argument('--ignore', type=Path, action='append', default=[],
                        help="Ignore path during collection (multi-allowed)")
    parser.add_argument('--no-excludes', action='store_true',
                        help="Don't apply configuration exclusions")
    parser.add_argument('-f', '--failfast', action='store_true',
                        help="Exit immediately after the first failure")
    parser.add_argument('--all', action='store_true',
                        help="Run tests that are normally not enabled due to tags")
    parser.add_argument('--retag', dest='retag_mode', action='store_const', const='replace',
                        help="Run tests and regenerate tags based on the results. Implies --all, --tagged and -n")
    parser.add_argument('--retag-append', dest='retag_mode', action='store_const', const='append',
                        help="Like --retag, but doesn't remove existing tags. Useful for regtagging subsets of tests")
    parser.add_argument('--collect-only', action='store_true',
                        help="Print found tests IDs without running tests")
    parser.add_argument('--continue-on-collection-errors', action='store_true',
                        help="Collection errors are not fatal")
    parser.add_argument('--durations', type=int, default=0,
                        help="Show durations of N slowest tests (-1 to show all)")
    parser.add_argument('--mx-report',
                        help="Produce a json report file in format expected by mx_gate.make_test_report")
    parser.add_argument(
        '--subprocess-args',
        type=shlex.split,
        default=[
            "--vm.ea",
            "--experimental-options=true",
            "--python.EnableDebuggingBuiltins",
        ] if IS_GRAALPY else [],
        help="Interpreter arguments to pass for subprocess invocation (when using -n)")
    parser.add_argument('tests', nargs='+', type=TestSpecifier.from_str,
                        help="""
                        List of test specifiers. A specifier can be:
                        - A test file name. It will be looked up in our unittests or, if you pass --tagged, in tagged tests. Example: test_int
                        - A test file path. Example: graalpython/lib-python/3/test/test_int.py. Note you do not need to pass --tagged to refer to a tagged test by path
                        - A test directory name or path. Example: cpyext
                        - A test file name/path with a selector for a specific test. Example: test_int::tests.test_int.ToBytesTests.test_WrongTypes
                        - A test file name/path with a selector for multiple tests. Example: test_int::tests.test_int.ToBytesTests
                        - You can use wildcards in tests paths and selectors. Example: 'test_int::test_create*'

                        Tip: the test IDs printed in test results directly work as specifiers here.
                        """)

    args = parser.parse_args()

    if args.retag_mode:
        args.all = True
        args.tagged = True
        args.num_processes = args.num_processes or 1

    if get_bool_env('GRAALPYTEST_FAIL_FAST'):
        args.failfast = True

    if IS_GRAALPY:
        if get_bool_env('GRAALPYTEST_ALLOW_NO_JAVA_ASSERTIONS'):
            # noinspection PyUnresolvedReferences
            if not __graalpython__.java_assert():
                sys.exit(
                    "Java assertions are not enabled, refusing to run. Add --vm.ea to your invocation. Set GRAALPYTEST_ALLOW_NO_JAVA_ASSERTIONS=true to disable this check\n")
        # noinspection PyUnresolvedReferences
        if not hasattr(__graalpython__, 'tdebug'):
            sys.exit("Needs to be run with --experimental-options --python.EnableDebuggingBuiltins\n")

    implicit_root = (TAGGED_TEST_ROOT if args.tagged else UNIT_TEST_ROOT).relative_to(Path('.').resolve())
    for i, test in enumerate(args.tests):
        if not test.test_file.is_absolute() and not test.test_file.resolve().is_relative_to(DIR.parent.parent):
            args.tests[i] = test.with_test_file(implicit_root / test.test_file)
    for i, ignore in enumerate(args.ignore):
        ignore_path = Path(ignore)
        if not ignore_path.is_absolute() and not ignore_path.resolve().is_relative_to(DIR.parent.parent):
            args.ignore[i] = implicit_root / ignore_path

    partial = None
    if partial_env := os.environ.get('TAGGED_UNITTEST_PARTIAL'):
        selected_str, total_str = partial_env.split('/', 1)
        partial = int(selected_str) - 1, int(total_str)

    tests = collect(
        args.tests,
        use_tags=(not args.all),
        ignore=args.ignore,
        partial=partial,
        continue_on_errors=args.continue_on_collection_errors,
        no_excludes=args.no_excludes,
    )
    if args.collect_only:
        for test_suite in tests:
            for test in test_suite.collected_tests:
                print(test)
        return

    if not tests:
        sys.exit("No tests matched\n")

    runner_args = dict(
        failfast=args.failfast,
        report_durations=args.durations,
    )
    if not args.num_processes:
        runner = TestRunner(**runner_args)
    else:
        runner = ParallelTestRunner(
            **runner_args,
            num_processes=args.num_processes,
            subprocess_args=args.subprocess_args,
            separate_workers=args.separate_workers,
        )

    runner.run_tests(tests)
    if args.mx_report:
        runner.generate_mx_report(args.mx_report)
    if args.retag_mode:
        runner.generate_tags(append=(args.retag_mode == 'append'))
        return
    if runner.tests_failed():
        sys.exit(1)


if __name__ == '__main__':
    if os.environ.get('IN_PROCESS') == '1':
        in_process()
    else:
        main()
