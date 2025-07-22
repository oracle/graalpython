# Copyright (c) 2020, 2025, Oracle and/or its affiliates. All rights reserved.
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
import os
import pickle
import platform
import re
import select
import shlex
import signal
import socket
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
from textwrap import dedent

DIR = Path(__file__).parent.resolve()
GRAALPYTHON_DIR = DIR.parent.parent.resolve()
UNIT_TEST_ROOT = (DIR / 'tests').resolve()
TAGGED_TEST_ROOT = (GRAALPYTHON_DIR / 'lib-python' / '3' / 'test').resolve()
IS_GRAALPY = sys.implementation.name == 'graalpy'

PLATFORM_KEYS = {sys.platform, platform.machine(), sys.implementation.name}
if IS_GRAALPY:
    # noinspection PyUnresolvedReferences
    PLATFORM_KEYS.add('native_image' if __graalpython__.is_native else 'jvm')

CURRENT_PLATFORM = f'{sys.platform}-{platform.machine()}'
CURRENT_PLATFORM_KEYS = frozenset({CURRENT_PLATFORM})


class Logger:
    report_incomplete = sys.stdout.isatty()

    def __init__(self):
        self.lock = threading.RLock()
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


logger = Logger()
log = logger.log


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

    def normalized(self):
        return TestId(self.test_file.resolve(), self.test_name)

    @classmethod
    def from_str(cls, s: str):
        test_file, _, test_id = s.partition('::')
        if test_id is None:
            raise ValueError(f"Invalid test ID {s}")
        return cls(Path(test_file), test_id)

    @classmethod
    def from_test_case(cls, test_file: Path, test: unittest.TestCase):
        test_id = test.id()
        if type(test).__module__ == 'unittest.suite' and type(test).__name__ == '_ErrorHolder':
            if match := re.match(r'(\S+) \(([^)]+)\)', test_id):
                action = match.group(1)
                class_name = match.group(2)
                if 'Module' in action:
                    test_id = f'<{action}>'
                else:
                    test_id = f'{class_name}.<{action}>'
        elif type(test).id is not unittest.TestCase.id and type(test) is not unittest.FunctionTestCase:
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
        return 0


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
        duration = 0.0
        if self.start_time:
            duration = time.time() - self.start_time
        return TestResult(status=status, test_id=self.test_id(test), duration=duration, **kwargs)

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


class ConnectionResult(AbstractRemoteResult):
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
        else:
            message = f"{message} ({result.duration:.2f}s)"
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
            # Skip synthetic results for failed class setups and such
            if '<' in result.test_id.test_name:
                continue
            match result.status:
                case TestStatus.SUCCESS | TestStatus.EXPECTED_FAILURE:
                    status = 'PASSED'
                case TestStatus.SKIPPED:
                    status = 'IGNORED'
                case _:
                    status = 'FAILED'
            report_data.append({
                'name': str(result.test_id).replace('\\', '/'),
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
            update_tags(
                test_file,
                results,
                tag_platform=CURRENT_PLATFORM,
                untag_failed=(not append),
                untag_skipped=(not append),
                untag_missing=(not append),
            )


def update_tags(test_file: 'TestFile', results: list[TestResult], tag_platform: str,
                untag_failed=False, untag_skipped=False, untag_missing=False):
    current = read_tags(test_file, allow_exclusions=True)
    exclusions, current = partition_list(current, lambda t: isinstance(t, TagExclusion))
    status_by_id = {r.test_id.normalized(): r.status for r in results}
    tag_by_id = {}
    for tag in current:
        if untag_missing and tag.test_id not in status_by_id:
            tag = tag.without_key(tag_platform)
        if tag:
            tag_by_id[tag.test_id] = tag

    for test_id, status in status_by_id.items():
        if tag := tag_by_id.get(test_id):
            if status == TestStatus.SUCCESS:
                tag_by_id[test_id] = tag.with_key(tag_platform)
            elif (untag_skipped and status == TestStatus.SKIPPED
                  or untag_failed and status == TestStatus.FAILURE):
                tag = tag.without_key(tag_platform)
                if tag:
                    tag_by_id[test_id] = tag
                else:
                    del tag_by_id[test_id]
        elif status == TestStatus.SUCCESS:
            tag_by_id[test_id] = Tag.for_key(test_id, tag_platform)

    for exclusion in exclusions:
        tag_by_id.pop(exclusion.test_id, None)

    tags = set(tag_by_id.values()) | set(exclusions)
    write_tags(test_file, tags)


def write_tags(test_file: 'TestFile', tags: typing.Iterable['Tag']):
    tag_file = test_file.get_tag_file()
    if not tag_file:
        log(f"WARNING: no tag directory for test file {test_file}")
        return
    if not tags:
        tag_file.unlink(missing_ok=True)
        return
    with open(tag_file, 'w') as f:
        for tag in sorted(tags, key=lambda t: t.test_id.test_name):
            f.write(f'{tag}\n')


def interrupt_process(process: subprocess.Popen):
    if hasattr(signal, 'SIGINT'):
        try:
            process.send_signal(signal.SIGINT)
            process.wait(3)
            return
        except (OSError, subprocess.TimeoutExpired):
            pass
    process.terminate()
    try:
        process.wait(3)
    except subprocess.TimeoutExpired:
        process.kill()


class ParallelTestRunner(TestRunner):
    def __init__(self, *, num_processes, subprocess_args, separate_workers, timeout_factor, **kwargs):
        super().__init__(**kwargs)
        self.num_processes = num_processes
        self.subprocess_args = subprocess_args
        self.separate_workers = separate_workers
        self.timeout_factor = timeout_factor
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
        workers = [SubprocessWorker(self, partition) for i, partition in enumerate(partitions)]
        futures = [executor.submit(worker.run_in_subprocess_and_watch) for worker in workers]

        def dump_worker_status():
            with logger.lock:
                log("=" * 80)
                log("Dumping test worker status:")
                for i, partition in enumerate(partitions):
                    not_started = 0
                    if futures[i].running():
                        log(f"Worker on {workers[i].thread}: {workers[i].get_status()}")
                    elif not futures[i].done():
                        not_started += len(partition)
                if not_started:
                    log(f"There are {not_started} tests not assigned to any worker")
                log("=" * 80)

        try:
            def sigterm_handler(_signum, _frame):
                dump_worker_status()
                # noinspection PyUnresolvedReferences,PyProtectedMember
                os._exit(1)

            prev_sigterm = signal.signal(signal.SIGTERM, sigterm_handler)
        except Exception:
            prev_sigterm = None

        try:
            try:
                concurrent.futures.wait(futures)
                for future in futures:
                    future.result()
            except KeyboardInterrupt:
                log("Received keyboard interrupt, stopping")
                dump_worker_status()
                self.stop_event.set()
                concurrent.futures.wait(futures)
                sys.exit(1)
        finally:
            if prev_sigterm:
                signal.signal(signal.SIGTERM, prev_sigterm)


class SubprocessWorker:
    def __init__(self, runner: ParallelTestRunner, tests: list['Test']):
        self.runner = runner
        self.stop_event = runner.stop_event
        self.lock = threading.RLock()
        self.remaining_test_ids = [test.test_id for test in tests]
        self.tests_by_id = {test.test_id: test for test in tests}
        self.out_file: typing.TextIO | None = None
        self.last_started_test_id: TestId | None = None
        self.last_started_time: float | None = None
        self.last_out_pos = 0
        self.last_test_id_for_blame: TestId | None = None
        self.process: subprocess.Popen | None = None
        self.thread = None

    def process_event(self, event):
        test_id = event['test']
        match event['event']:
            case 'testStarted':
                try:
                    self.remaining_test_ids.remove(test_id)
                except ValueError:
                    # It executed something we didn't ask for. Not sure why this happens
                    log(f'WARNING: unexpected test started {test_id}')
                self.runner.report_start(test_id)
                with self.lock:
                    self.last_started_test_id = test_id
                    self.last_started_time = time.time()
                    self.last_out_pos = event['out_pos']
            case 'testResult':
                status = event['status']
                out_end = event['out_pos']
                test_output = ''
                if self.last_out_pos != out_end:
                    self.out_file.seek(self.last_out_pos)
                    test_output = self.out_file.read(out_end - self.last_out_pos)
                result = TestResult(
                    test_id=test_id,
                    status=status,
                    param=event.get('param'),
                    output=test_output,
                    duration=event.get('duration'),
                )
                self.runner.report_result(result)
                with self.lock:
                    self.last_started_test_id = None
                    self.last_started_time = time.time()  # Starts timeout for the following teardown/setup
                    self.last_test_id_for_blame = test_id
                    self.last_out_pos = event['out_pos']
                    if test_id.test_name.endswith('>'):
                        class_name = test_id.test_name[:test_id.test_name.find('<')].rstrip('.')
                        specifier = TestSpecifier(test_id.test_file, class_name or None)
                        self.remaining_test_ids = [
                            test for test in self.remaining_test_ids if not specifier.match(test)
                        ]

    def get_status(self):
        with self.lock:
            if not self.process:
                process_status = "not started"
            elif self.process.poll() is not None:
                process_status = f"exitted with code {self.process.returncode}"
            else:
                process_status = "running"

            last_test_id = self.get_test_to_blame()
            if last_test_id is not None:
                if last_test_id is not self.last_started_test_id:
                    last_test_id = f'{last_test_id} (approximate)'
                duration = time.time() - self.last_started_time
                test_status = f"executing {last_test_id} for {duration:.2f}s"
            else:
                test_status = "no current test"
            remaining = len(self.remaining_test_ids)
            return f"test: {test_status}; remaining: {remaining}; process status: {process_status}"

    def get_test_to_blame(self):
        if self.last_started_test_id:
            return self.last_started_test_id
        # XXX unittest doesn't report module/class setups/teardowns, so if a test hard crashes or times out during
        # those, we can't tell which one is to blame. So we make a combined result for both as a last resort
        next_test_id = self.remaining_test_ids[0] if self.remaining_test_ids else None
        if self.last_test_id_for_blame is None:
            return TestId(next_test_id.test_file, f'<before> {next_test_id}')
        if next_test_id is None:
            return TestId(self.last_test_id_for_blame.test_file, f'<after> {self.last_test_id_for_blame}')
        return TestId(
            Path(''),
            f'<between> {self.last_test_id_for_blame} <and> {next_test_id}',
        )

    def run_in_subprocess_and_watch(self):
        self.thread = threading.current_thread()
        with (
            tempfile.TemporaryDirectory(prefix='graalpytest-') as tmp_dir,
            socket.create_server(('0.0.0.0', 0)) as server,
        ):
            tmp_dir = Path(tmp_dir)

            port = server.getsockname()[1]
            assert port

            retries = 3

            while self.remaining_test_ids and not self.stop_event.is_set():
                last_remaining_count = len(self.remaining_test_ids)
                with open(tmp_dir / 'out', 'w+') as self.out_file:
                    self.last_out_pos = 0
                    self.last_started_time = time.time()
                    cmd = [
                        sys.executable,
                        '-u',
                        *self.runner.subprocess_args,
                        __file__,
                        'worker',
                        '--port', str(port),
                    ]
                    if self.runner.failfast:
                        cmd.append('--failfast')

                    self.process = None
                    try:
                        self.process = subprocess.Popen(cmd, stdout=self.out_file, stderr=self.out_file)
                        server.settimeout(180.0)
                        sock = server.accept()[0]
                    except (TimeoutError, OSError):
                        if self.process:
                            interrupt_process(self.process)
                        retries -= 1
                        if retries:
                            continue
                        sys.exit("Worker failed to start/connect to runner multiple times")

                    with sock:
                        conn = Connection(sock)

                        conn.send([TestSpecifier(t.test_file, t.test_name) for t in self.remaining_test_ids])

                        timed_out = None

                        try:
                            while True:
                                while conn.poll(0.1):
                                    event = conn.recv()
                                    self.process_event(event)
                                if self.stop_event.is_set():
                                    interrupt_process(self.process)
                                    break
                                if self.last_started_test_id:
                                    last_started_test = self.tests_by_id.get(self.last_started_test_id)
                                    timeout = (
                                            last_started_test.test_file.test_config.per_test_timeout
                                            or self.runner.default_test_timeout
                                    )
                                else:
                                    timeout = self.runner.default_test_timeout
                                timeout *= self.runner.timeout_factor
                                if time.time() - self.last_started_time >= timeout:
                                    interrupt_process(self.process)
                                    timed_out = timeout
                                    break
                        except (ConnectionClosed, OSError):
                            # The socket closed or got connection reset, that's normal if the worker exitted or crashed
                            pass
                    try:
                        self.process.wait(self.runner.default_test_timeout)
                    except subprocess.TimeoutExpired:
                        log("Warning: Worker didn't shutdown in a timely manner, interrupting it")
                        interrupt_process(self.process)

                    returncode = self.process.wait()

                    if self.stop_event.is_set():
                        return

                    if returncode != 0 or timed_out is not None:
                        self.out_file.seek(self.last_out_pos)
                        output = self.out_file.read()
                        if timed_out is not None:
                            message = f"Timed out in {timed_out}s"
                        elif returncode >= 0:
                            message = f"Test process exitted with code {returncode}"
                        else:
                            try:
                                signal_name = signal.Signals(-returncode).name
                            except ValueError:
                                signal_name = str(-returncode)
                            message = f"Test process killed by signal {signal_name}"
                        blame_id = self.get_test_to_blame()
                        self.runner.report_result(TestResult(
                            test_id=blame_id,
                            status=TestStatus.ERROR,
                            param=message,
                            output=output,
                            duration=(time.time() - self.last_started_time),
                        ))
                        if blame_id is not self.last_started_test_id:
                            # If we're here, it means we didn't know exactly which test we were executing, we were
                            # somewhere in between
                            if self.last_test_id_for_blame:
                                # Retry the same test again, if it crashes again, we would get into the else branch
                                self.last_started_test_id = None
                                self.last_test_id_for_blame = None
                                continue
                            else:
                                # The current test caused the crash for sure, continue with the next
                                if self.remaining_test_ids:
                                    del self.remaining_test_ids[0]
                    self.last_started_test_id = None
                    if last_remaining_count == len(self.remaining_test_ids):
                        log(f"Worker is not making progress, remaining: {self.remaining_test_ids}")


def platform_keys_match(items: typing.Iterable[str]):
    return any(all(key in PLATFORM_KEYS for key in item.split('-')) for item in items)


@dataclass
class TestFileConfig:
    serial: bool | None = None
    partial_splits: bool | None = None
    per_test_timeout: float | None = None
    exclude: bool = False

    @classmethod
    def from_dict(cls, config: dict):
        return cls(
            serial=config.get('serial', cls.serial),
            partial_splits=config.get('partial_splits_individual_tests', cls.partial_splits),
            per_test_timeout=config.get('per_test_timeout', cls.per_test_timeout),
            exclude=platform_keys_match(config.get('exclude_on', ())),
        )

    def combine(self, other: 'TestFileConfig'):
        return TestFileConfig(
            serial=(self.serial if other.serial is None else other.serial),
            partial_splits=(self.partial_splits if other.partial_splits is None else other.partial_splits),
            per_test_timeout=(self.per_test_timeout if other.per_test_timeout is None else other.per_test_timeout),
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
    def parse_config(cls, config_path: Path):
        with open(config_path, 'rb') as f:
            config_dict = tomllib.load(f)
            settings = config_dict.get('settings', {})
            rules = [TestFileRule(rule) for rule in config_dict.get('test_rules', ())]
            tags_dir = None
            if config_tags_dir := settings.get('tags_dir'):
                tags_dir = (config_path.parent / config_tags_dir).resolve()
            # Temporary hack for Bytecode DSL development in master branch:
            if IS_GRAALPY and __graalpython__.is_bytecode_dsl_interpreter and tags_dir:
                new_tags_dir = (config_path.parent / (config_tags_dir + '_bytecode_dsl')).resolve()
                if new_tags_dir.exists():
                    tags_dir = new_tags_dir
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
    name = str(resolved).replace(os.sep, '/').removesuffix('.py')
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
                tagged_ids: list[TestId] | None):
    keep_tests = []
    collected_tests = []
    for test in test_suite:
        # When test loading fails, unittest just creates an instance of _FailedTest
        if exception := getattr(test, '_exception', None):
            raise exception
        if type(test).__module__ == 'unittest.loader' and type(test).__name__ == 'ModuleSkipped':
            skipped_test, reason = test().skipped[0]
            log(f"Test module {skipped_test.id().removeprefix('unittest.loader.ModuleSkipped.')} skipped: {reason}")
            return
        if hasattr(test, '__iter__'):
            sub_collected = filter_tree(test_file, test, specifiers, tagged_ids)
            if sub_collected:
                keep_tests.append(test)
                collected_tests += sub_collected
        else:
            test_id = TestId.from_test_case(test_file.path, test)
            if any(s.match(test_id) for s in specifiers):
                if tagged_ids is None or test_id.normalized() in tagged_ids:
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
        tagged_ids = None
        if use_tags and config.tags_dir:
            tagged_ids = [tag.test_id for tag in read_tags(test_file) if platform_keys_match(tag.keys)]
            if not tagged_ids:
                return None
        test_module = test_path_to_module(test_file)
        try:
            test_suite = loader.loadTestsFromName(test_module)
        except unittest.SkipTest as e:
            log(f"Test file {test_file} skipped: {e}")
            return
        collected_tests = filter_tree(test_file, test_suite, specifiers, tagged_ids)
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
            if not any(path_for_comparison(test_file.path).is_relative_to(i) for i in ignore)
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


@dataclass(frozen=True)
class Tag:
    test_id: TestId
    keys: frozenset[str]

    @classmethod
    def for_key(cls, test_id, key):
        return Tag(test_id, frozenset({key}))

    def with_key(self, key: str):
        return Tag(self.test_id, self.keys | {key})

    def without_key(self, key: str):
        if key not in self.keys:
            return self
        keys = self.keys - {key}
        if keys:
            return Tag(self.test_id, keys)

    def __str__(self):
        return f'{self.test_id.test_name} @ {",".join(sorted(self.keys))}'


@dataclass(frozen=True)
class TagExclusion(Tag):
    comment: str | None

    def __str__(self):
        s = f'!{self.test_id.test_name}'
        if self.keys:
            s += f' @ {",".join(sorted(self.keys))}'
        if self.comment:
            s = f'{self.comment}{s}'
        return s


def read_tags(test_file: TestFile, allow_exclusions=False) -> list[Tag]:
    # To make them easily comparable
    test_path = test_file.path.resolve()
    tag_file = test_file.get_tag_file()
    tags = []
    if tag_file.exists():
        with open(tag_file) as f:
            comment = None
            for line in f:
                if line.startswith('#'):
                    if comment:
                        comment += line
                    else:
                        comment = line
                    continue
                test, _, keys = line.partition('@')
                test = test.strip()
                keys = keys.strip()
                if test.startswith('!'):
                    if allow_exclusions:
                        test = test.removeprefix('!')
                        tags.append(TagExclusion(
                            TestId(test_path, test),
                            frozenset(keys.split(',')) if keys else frozenset(),
                            comment,
                        ))
                else:
                    if not keys:
                        log(f'WARNING: invalid tag {test}: missing platform keys')
                    tags.append(Tag(
                        TestId(test_path, test),
                        frozenset(keys.split(',')),
                    ))
                comment = None
    return tags


class TopLevelFunctionLoader(unittest.loader.TestLoader):
    def loadTestsFromModule(self, module, *, pattern=None):
        test_suite = super().loadTestsFromModule(module, pattern=pattern)
        for name, obj in vars(module).items():
            if name.startswith('test_') and callable(obj):
                test_suite.addTest(unittest.FunctionTestCase(obj))
        return test_suite


class ConnectionClosed(Exception):
    pass


class Connection:
    def __init__(self, sock):
        self.socket = sock

    def send(self, obj):
        data = pickle.dumps(obj)
        header = len(data).to_bytes(8, byteorder='big')
        self.socket.sendall(header)
        self.socket.sendall(data)

    def _recv(self, size):
        data = b''
        while len(data) < size:
            read = self.socket.recv(size - len(data))
            if not read:
                return data
            data += read
        return data

    def recv(self):
        size = int.from_bytes(self._recv(8), byteorder='big')
        if not size:
            raise ConnectionClosed
        data = self._recv(size)
        return pickle.loads(data)

    def poll(self, timeout=None):
        rlist, wlist, xlist = select.select([self.socket], [], [], timeout)
        return bool(rlist)


def main_worker(args):
    with socket.create_connection(('localhost', args.port)) as sock:
        conn = Connection(sock)

        tests = conn.recv()

        for test_suite in collect(tests, no_excludes=True):
            result = ConnectionResult(test_suite, conn)
            result.failfast = args.failfast
            test_suite.run(result)


def main_merge_tags(args):
    with open(args.report_path) as f:
        report = json.load(f)
    status_map = {
        'PASSED': TestStatus.SUCCESS,
        'FAILED': TestStatus.FAILURE,
        'IGNORED': TestStatus.SKIPPED,
    }
    all_results = [
        TestResult(
            test_id=TestId.from_str(result['name']),
            status=status_map[result['status']],
        )
        for result in report
        if '<' not in result['name']
    ]
    by_file = defaultdict(list)
    for result in all_results:
        by_file[result.test_id.test_file].append(result)
    for test_file, results in by_file.items():
        test_file = configure_test_file(test_file)
        update_tags(
            test_file,
            results,
            tag_platform=args.platform,
            untag_failed=False,
            untag_skipped=True,
            untag_missing=True,
        )


def get_bool_env(name: str):
    return os.environ.get(name, '').lower() in ('true', '1')


def main():
    is_mx_graalpytest = get_bool_env('MX_GRAALPYTEST')
    parent_parser = argparse.ArgumentParser(formatter_class=argparse.RawTextHelpFormatter)
    subparsers = parent_parser.add_subparsers()

    # run command declaration
    run_parser = subparsers.add_parser(
        'run',
        prog=('mx graalpytest' if is_mx_graalpytest else None),
        help="Run GraalPy unittests or CPython unittest with tagging",
    )
    run_parser.set_defaults(main=main_run)
    if is_mx_graalpytest:
        # mx graalpytest takes this option, but it forwards --help here, so pretend we take it
        run_parser.add_argument('--python', help="Run tests with given Python binary")
        run_parser.add_argument('--svm', action='store_true', help="Use SVM standalone")
    run_parser.add_argument(
        '-t', '--tagged', action='store_true',
        help="Interpret test file names relative to tagged test directory",
    )
    run_parser.add_argument(
        '-n', '--num-processes', type=int,
        help="Run tests in N subprocess workers. Adds crash recovery, output capture and timeout handling",
    )
    run_parser.add_argument(
        '--separate-workers', action='store_true',
        help="Create a new worker process for each test file (when -n is specified). Default for tagged unit tests",
    )
    run_parser.add_argument(
        '--ignore', type=Path, action='append', default=[],
        help="Ignore path during collection (multi-allowed)",
    )
    run_parser.add_argument(
        '--no-excludes', action='store_true',
        help="Don't apply configuration exclusions",
    )
    run_parser.add_argument(
        '-f', '--failfast', action='store_true',
        help="Exit immediately after the first failure",
    )
    run_parser.add_argument(
        '--all', action='store_true',
        help="Run tests that are normally not enabled due to tags. Implies --tagged",
    )
    run_parser.add_argument(
        '--retag', dest='retag_mode', action='store_const', const='replace',
        help="Run tests and regenerate tags based on the results. Implies --all, --tagged and -n",
    )
    run_parser.add_argument(
        '--retag-append', dest='retag_mode', action='store_const', const='append',
        help="Like --retag, but doesn't remove existing tags. Useful for regtagging subsets of tests",
    )
    run_parser.add_argument(
        '--collect-only', action='store_true',
        help="Print found tests IDs without running tests",
    )
    run_parser.add_argument(
        '--continue-on-collection-errors', action='store_true',
        help="Collection errors are not fatal",
    )
    run_parser.add_argument(
        '--durations', type=int, default=0,
        help="Show durations of N slowest tests (-1 to show all)",
    )
    run_parser.add_argument(
        '--mx-report',
        help="Produce a json report file in format expected by mx_gate.make_test_report",
    )
    run_parser.add_argument(
        '--untag-unmatched', action='store_true',
        help="Remove tests that were not collected from tags. Useful for pruning removed tests",
    )
    run_parser.add_argument(
        '--timeout-factor', type=float, default=1.0,
        help="Multiply all timeouts by this number",
    )
    run_parser.add_argument(
        '--exit-success-on-failures', action='store_true',
        help=dedent(
            """\
            Exit successfully regardless of the test results. Useful to distinguish test failures
            from runner crashes in jobs like retagger or coverage where failures are expected.
            """
        ),
    )
    run_parser.add_argument(
        '--subprocess-args',
        type=shlex.split,
        default=[
            "--vm.ea",
            "--experimental-options=true",
            "--python.EnableDebuggingBuiltins",
        ] if IS_GRAALPY else [],
        help="Interpreter arguments to pass for subprocess invocation (when using -n)",
    )
    run_parser.add_argument(
        '--append-path',
        help="Append the path to sys.path",
    )
    run_parser.add_argument(
        'tests', nargs='+', type=TestSpecifier.from_str,
        help=dedent(
            """\
            List of test specifiers. A specifier can be:
            - A test file name. It will be looked up in our unittests or, if you pass --tagged, in tagged tests. Example: test_int
            - A test file path. Example: graalpython/lib-python/3/test/test_int.py. Note you do not need to pass --tagged to refer to a tagged test by path
            - A test directory name or path. Example: cpyext
            - A test file name/path with a selector for a specific test. Example: test_int::tests.test_int.ToBytesTests.test_WrongTypes
            - A test file name/path with a selector for multiple tests. Example: test_int::tests.test_int.ToBytesTests
            - You can use wildcards in tests paths and selectors. Example: 'test_int::test_create*'

            Tip: the test IDs printed in test results directly work as specifiers here.
            """
        ),
    )

    # worker command declaration
    worker_parser = subparsers.add_parser('worker', help="Internal command for subprocess workers")
    worker_parser.set_defaults(main=main_worker)
    worker_parser.add_argument('--port', type=int)
    worker_parser.add_argument('--failfast', action='store_true')

    # merge-tags-from-report command declaration
    merge_tags_parser = subparsers.add_parser('merge-tags-from-report', help="Merge tags from automated retagger")
    merge_tags_parser.set_defaults(main=main_merge_tags)
    merge_tags_parser.add_argument('platform')
    merge_tags_parser.add_argument('report_path')

    # run the appropriate command
    args = parent_parser.parse_args()
    args.main(args)


def main_run(args):
    if args.append_path:
        sys.path.append(args.append_path)
    if args.retag_mode:
        args.all = True
        args.tagged = True
        args.num_processes = args.num_processes or 1

    if args.all:
        args.tagged = True

    if get_bool_env('GRAALPYTEST_FAIL_FAST'):
        args.failfast = True

    if IS_GRAALPY:
        if not get_bool_env('GRAALPYTEST_ALLOW_NO_JAVA_ASSERTIONS'):
            # noinspection PyUnresolvedReferences
            if not __graalpython__.java_assert():
                sys.exit(
                    "Java assertions are not enabled, refusing to run. Add --vm.ea to your invocation. Set GRAALPYTEST_ALLOW_NO_JAVA_ASSERTIONS=true to disable this check\n")
        # noinspection PyUnresolvedReferences
        if not hasattr(__graalpython__, 'tdebug'):
            sys.exit("Needs to be run with --experimental-options --python.EnableDebuggingBuiltins\n")

    implicit_root = Path(os.path.relpath(TAGGED_TEST_ROOT if args.tagged else UNIT_TEST_ROOT))
    for i, test in enumerate(args.tests):
        if not test.test_file.is_absolute() and not test.test_file.resolve().is_relative_to(GRAALPYTHON_DIR):
            args.tests[i] = test.with_test_file(implicit_root / test.test_file)
    for i, ignore in enumerate(args.ignore):
        ignore_path = Path(ignore)
        if not ignore_path.is_absolute() and not ignore_path.resolve().is_relative_to(GRAALPYTHON_DIR):
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

    log(f"Collected {sum(len(test_suite.collected_tests) for test_suite in tests)} tests")

    if not tests:
        sys.exit("No tests matched\n")

    if args.untag_unmatched:
        for test_suite in tests:
            test_file = test_suite.test_file
            tags = read_tags(test_file)
            if tags:
                filtered_tags = []
                for tag in tags:
                    if not any(tag.test_id.test_name == test.test_id.test_name for test in test_suite.collected_tests):
                        log(f"Removing tag for {test_file}::{tag.test_id.test_name}")
                    else:
                        filtered_tags.append(tag)
                write_tags(test_file, filtered_tags)
        return

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
            timeout_factor=args.timeout_factor,
        )

    runner.run_tests(tests)
    if args.mx_report:
        runner.generate_mx_report(args.mx_report)
    if args.retag_mode:
        runner.generate_tags(append=(args.retag_mode == 'append'))
        return
    if runner.tests_failed() and not args.exit_success_on_failures:
        sys.exit(1)


if __name__ == '__main__':
    main()
