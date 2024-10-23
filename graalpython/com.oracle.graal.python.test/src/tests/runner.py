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
import multiprocessing
import multiprocessing.connection
import os
import signal
import subprocess
import sys
import tempfile
import threading
import tomllib
import traceback
import unittest
import unittest.loader
from abc import abstractmethod
from collections import defaultdict
from contextlib import contextmanager
from dataclasses import dataclass
from functools import lru_cache
from pathlib import Path

sys.path.insert(0, os.getcwd())

DIR = Path(__file__).parent

if sys.implementation.version < (3, 12):
    # XXX Temporary hack: python 3.12 will have a toml parser in standard library, for now we load vendored one from pip
    [pip_wheel] = (DIR.parent.parent.parent / 'lib-python' / '3' / 'ensurepip' / '_bundled').glob('pip*.whl')
    sys.path.append(pip_wheel)

    sys.path.pop()
else:
    pass


class TestStatus(enum.StrEnum):
    SUCCESS = "ok"
    FAILURE = "FAIL"
    ERROR = "ERROR"
    SKIPPED = "skipped"
    EXPECTED_FAILURE = "expected failure"
    UNEXPECTED_SUCCESS = "unexpected success"
    NOT_EXECUTED = "not executed"
    UNTAGGED = "untagged"


SUCCESSFUL_STATES = TestStatus.SUCCESS, TestStatus.SKIPPED, TestStatus.EXPECTED_FAILURE
FAILED_STATES = TestStatus.FAILURE, TestStatus.ERROR, TestStatus.UNEXPECTED_SUCCESS
EXECUTED_STATES = *SUCCESSFUL_STATES, *FAILED_STATES


@dataclass(repr=False)
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
        return cls(test_file, test.id())


@dataclass(repr=False)
class TestSpecifier:
    test_file: Path
    test_name: str | None

    def __repr__(self):
        if self.test_name is None:
            return str(self.test_file)
        return f'{self.test_file}::{self.test_name}'

    @classmethod
    def from_str(cls, s: str):
        test_file, _, test_id = s.partition('::')
        return cls(Path(test_file), test_id or None)

    def match(self, test_id: TestId):
        return self.test_file == test_id.test_file and (self.test_name is None or self.test_name == test_id.test_name)


@dataclass
class TestResult:
    test_id: TestId
    status: TestStatus
    param: str | None = None
    output: str = ''


def group_specifiers_by_file(specifiers: list[TestSpecifier]) -> dict[Path, list[TestSpecifier]]:
    by_file = defaultdict(list)
    for specifier in specifiers:
        by_file[specifier.test_file].append(specifier)
    return by_file


def format_exception(err):
    _, exc, tb = err
    while tb and '__unittest' in tb.tb_frame.f_globals:
        tb = tb.tb_next
    exc.__traceback__ = tb
    return ''.join(traceback.format_exception(exc))


def out_tell():
    try:
        return os.lseek(1, 0, os.SEEK_CUR)
    except OSError:
        return -1


class AbstractResult(unittest.TestResult):
    def __init__(self, test_suite: 'TestSuite'):
        super().__init__()
        self.test_suite = test_suite

    def test_id(self, test):
        return TestId.from_test_case(self.test_suite.test_file, test)

    @abstractmethod
    def report_result(self, result: TestResult):
        pass

    def addSuccess(self, test):
        super().addSuccess(test)
        self.report_result(TestResult(status=TestStatus.SUCCESS, test_id=self.test_id(test)))

    def addFailure(self, test, err):
        super().addFailure(test, err)
        self.report_result(
            TestResult(status=TestStatus.FAILURE, test_id=self.test_id(test), param=format_exception(err)))

    def addError(self, test, err):
        super().addError(test, err)
        self.report_result(TestResult(status=TestStatus.ERROR, test_id=self.test_id(test), param=format_exception(err)))

    def addSkip(self, test, reason):
        super().addSkip(test, reason)
        self.report_result(TestResult(status=TestStatus.SKIPPED, test_id=self.test_id(test), param=reason))

    def addExpectedFailure(self, test, err):
        super().addExpectedFailure(test, err)
        self.report_result(
            TestResult(status=TestStatus.EXPECTED_FAILURE, test_id=self.test_id(test), param=format_exception(err)))

    def addUnexpectedSuccess(self, test):
        super().addUnexpectedSuccess(test)
        self.report_result(TestResult(status=TestStatus.UNEXPECTED_SUCCESS, test_id=self.test_id(test)))


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
        self.emit(event='testResult', status=result.status, test=result.test_id, param=result.param, out_pos=out_tell())

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


def test_path_to_module(path: Path):
    return str(path).removesuffix('.py').replace(os.sep, '.')


class TestRunner:
    def __init__(self, args):
        self.args = args
        self.events = []
        self.results = []
        self.skipped_files = []
        self.incomplete_line = False
        self.report_incomplete = sys.stdout.isatty()

    def report_start(self, test_id: TestId):
        if self.report_incomplete:
            if self.incomplete_line:
                print('\r\033[K', end='')
            print(f"{test_id} ... ", end='', flush=True)
            self.incomplete_line = True

    def report_result(self, result: TestResult):
        self.results.append(result)
        if self.incomplete_line:
            print('\r', end='')
            self.incomplete_line = False
        message = f"{result.test_id} ... {result.status}"
        if result.status == TestStatus.SKIPPED and result.param:
            message = f"{message} {result.param!r}"
        print(message, flush=True)

    def tests_failed(self):
        return any(result.status in FAILED_STATES for result in self.results)

    def display_summary(self):
        counts = defaultdict(int)
        for result in self.results:
            counts[result.status] += 1

        print()

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
                print("======================================================================")
                print(f"{fail_type}: {result.test_id}")
                print("----------------------------------------------------------------------")
                if result.param:
                    print(result.param.rstrip('\n'))
                if result.output:
                    print("------------------------- captured output ----------------------------")
                    print(result.output.rstrip('\n'))
                print()

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
        if not self.skipped_files:
            add_item(TestStatus.UNTAGGED, "not tagged")
            add_item(TestStatus.NOT_EXECUTED, "not executed")

        total = sum(count for status, count in counts.items() if status in EXECUTED_STATES)

        print(f"Ran {total} tests")  # TODO timing
        print()
        summary = ", ".join(items)
        overall_status = 'FAILED' if self.tests_failed() else 'OK'
        if summary:
            print(f"{overall_status} ({summary})")
        else:
            print(overall_status)

        if self.args.failfast and counts.get(TestStatus.NOT_EXECUTED):
            print("\nWARNING: Did not execute all tests because 'failfast' mode is on")

    def collect(self, all_specifiers: list[TestSpecifier]) -> list['TestSuite']:
        to_run = []
        for test_file, specifiers in group_specifiers_by_file(all_specifiers).items():
            if not test_file.exists():
                sys.exit(f"File does not exist: {test_file}")
            collected = collect_module(test_file, specifiers, use_tags=(not self.args.all))
            if collected:
                to_run.append(collected)
            else:
                self.skipped_files.append(test_file)
        return to_run

    def run_tests(self, tests: list[TestSpecifier]):
        for test_suite in self.collect(tests):
            result = DirectResult(test_suite, self)
            result.failfast = self.args.failfast
            test_suite.run(result)
            test_suite.add_unexecuted(self.results)
        self.display_summary()


class ParallelTestRunner(TestRunner):
    def __init__(self, args):
        super().__init__(args)
        self.lock = threading.Lock()
        self.stop_event = threading.Event()

    def report_start(self, test_id: TestId):
        with self.lock:
            super().report_start(test_id)

    def report_result(self, result: TestResult):
        if self.args.failfast and result.status in FAILED_STATES:
            self.stop_event.set()
        with self.lock:
            super().report_result(result)

    def run_tests(self, tests: list[TestSpecifier]):
        collected = self.collect(tests)
        if collected:
            num_processes = min(self.args.num_processes, len(collected))
            with concurrent.futures.ThreadPoolExecutor(num_processes) as executor:
                concurrent.futures.wait([
                    executor.submit(self.run_in_subprocess_and_watch, test_suite)
                    for test_suite in collected
                ])

        self.display_summary()

    def run_in_subprocess_and_watch(self, test_suite: 'TestSuite'):
        conn, child_conn = multiprocessing.Pipe()
        with tempfile.NamedTemporaryFile(prefix='graalpy-test-out-', mode='w+') as out_file:
            env = os.environ.copy()
            env['IN_PROCESS'] = '1'
            remaining_tests = test_suite.collected_tests
            while remaining_tests and not self.stop_event.is_set():
                cmd = [sys.executable, '-u', __file__, '--pipe-fd', str(child_conn.fileno())]
                if self.args.failfast:
                    cmd.append('--failfast')
                cmd += [str(s) for s in remaining_tests]
                process = subprocess.Popen(
                    cmd,
                    stdout=out_file,
                    stderr=out_file,
                    env=env,
                    pass_fds=[child_conn.fileno()],
                )

                last_started: TestId | None = None
                out_start = -1

                def process_event(event):
                    nonlocal last_started, out_start
                    self.events.append(event)
                    match event['event']:
                        case 'testStarted':
                            last_started = event['test']
                            out_start = event['out_pos']
                            self.report_start(event['test'])
                        case 'testResult':
                            last_started = None
                            out_end = event['out_pos']
                            test_output = ''
                            if out_start != out_end:
                                out_file.seek(out_start)
                                test_output = out_file.read(out_end - out_start)
                            result = TestResult(
                                test_id=event['test'],
                                status=event['status'],
                                param=event.get('param'),
                                output=test_output,
                            )
                            self.report_result(result)

                while process.poll() is None:
                    if conn.poll(0.1):
                        process_event(conn.recv())
                process.wait()
                while conn.poll(0.1):
                    process_event(conn.recv())
                if last_started:
                    out_file.seek(out_start)
                    output = out_file.read()
                    if process.returncode >= 0:
                        message = f"Test process exitted with code {process.returncode}"
                    else:
                        try:
                            signal_name = signal.Signals(-process.returncode).name
                        except ValueError:
                            signal_name = str(-process.returncode)
                        message = f"Test process killed by signal {signal_name}"
                    self.report_result(TestResult(
                        test_id=last_started,
                        status=TestStatus.ERROR,
                        param=message,
                        output=output,
                    ))
                    remaining_tests = remaining_tests[remaining_tests.index(last_started) + 1:]
                    continue
                test_suite.add_unexecuted(self.results)
                break


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('-n', '--num-processes', type=int)
    parser.add_argument('-f', '--failfast', action='store_true')
    parser.add_argument('--all', action='store_true')
    parser.add_argument('tests', nargs='+', type=TestSpecifier.from_str)

    args = parser.parse_args()

    runner = TestRunner(args) if args.num_processes is None else ParallelTestRunner(args)
    runner.run_tests(args.tests)
    if runner.tests_failed():
        sys.exit(1)


def filter_tree(test_file: Path, test_suite: unittest.TestSuite, specifiers: list[TestSpecifier],
                tags: list[TestId] | None):
    keep_tests = []
    untagged_tests = []
    collected_tests = []
    for test in test_suite:
        # When test loading fails, unittest just creates an instance of _FailedTest
        if exception := getattr(test, '_exception', None):
            raise exception
        if hasattr(test, '__iter__'):
            sub_collected, sub_untagged = filter_tree(test_file, test, specifiers, tags)
            if sub_collected:
                keep_tests.append(test)
                collected_tests += sub_collected
            untagged_tests += sub_untagged
        else:
            specifier = TestId.from_test_case(test_file, test)
            if any(s.match(specifier) for s in specifiers):
                if tags is None or specifier in tags:
                    keep_tests.append(test)
                    collected_tests.append(specifier)
                elif tags is not None:
                    untagged_tests.append(specifier)
    test_suite._tests = keep_tests
    return collected_tests, untagged_tests


@dataclass
class Config:
    rootdir: Path = Path('.').resolve()
    tags_dir: Path | None = None


@lru_cache
def config_for_dir(path: Path) -> Config | None:
    config_path = path / 'conftest.toml'
    if config_path.exists():
        with open(config_path, 'rb') as f:
            config_dict = tomllib.load(f)['tests']
            rootdir = path
            if config_rootdir := config_dict.get('rootdir'):
                rootdir /= config_rootdir
            rootdir = rootdir.resolve()
            tags_dir = None
            if config_tags_dir := config_dict.get('tags_dir'):
                tags_dir = (path / config_tags_dir).resolve()
            return Config(rootdir=rootdir, tags_dir=tags_dir)


def config_for_file(test_file: Path) -> Config:
    path = test_file.parent
    while path.is_dir():
        if config := config_for_dir(path):
            return config
        if path.parent == path:
            break
        path = path.parent
    return Config()


@contextmanager
def rootdir_from_config(config: Config):
    saved_path = sys.path[:]
    sys.path.insert(0, str(config.rootdir))
    try:
        yield config.rootdir
    finally:
        sys.path[:] = saved_path


@dataclass
class TestSuite:
    config: Config
    test_file: Path
    test_suite: unittest.TestSuite
    collected_tests: list[TestId]
    untagged_tests: list[TestId]

    def run(self, result):
        with rootdir_from_config(self.config):
            self.test_suite.run(result)

    def add_unexecuted(self, results: list[TestResult]):
        executed = [r.test_id for r in results]
        results += [
            TestResult(status=TestStatus.NOT_EXECUTED, test_id=test)
            for test in self.collected_tests
            if test not in executed
        ]
        results += [
            TestResult(status=TestStatus.UNTAGGED, test_id=test)
            for test in self.untagged_tests
        ]


def collect_module(test_file: Path, specifiers: list[TestSpecifier], use_tags=False) -> TestSuite | None:
    config = config_for_file(test_file)
    with rootdir_from_config(config) as rootdir:
        loader = unittest.defaultTestLoader
        tags = None
        if use_tags and config.tags_dir:
            tags = read_tags(test_file, config)
            if not tags:
                return None
        test_suite = loader.loadTestsFromName(test_path_to_module(test_file.resolve().relative_to(rootdir)))
        collected_tests, untagged_tests = filter_tree(test_file, test_suite, specifiers, tags)
        if collected_tests:
            return TestSuite(config, test_file, test_suite, collected_tests, untagged_tests)


def read_tags(test_file: Path, config: Config) -> list[TestId]:
    tag_file = config.tags_dir / (test_file.name.removesuffix('.py') + '.txt')
    tags = []
    if tag_file.exists():
        with open(tag_file) as f:
            for line in f:
                test = line.strip().replace('*graalpython.lib-python.3.', '').replace('*', '')
                tags.append(TestId(test_file, test))
        return tags
    return tags


def in_process():
    parser = argparse.ArgumentParser()
    parser.add_argument('--pipe-fd', type=int, required=True)
    parser.add_argument('--failfast', action='store_true')
    parser.add_argument('tests', nargs='*', type=TestSpecifier.from_str)
    args = parser.parse_args()
    conn = multiprocessing.connection.Connection(args.pipe_fd)
    for test_file, specifiers in group_specifiers_by_file(args.tests).items():
        test_suite = collect_module(test_file, specifiers)
        result = PipeResult(test_suite, conn)
        result.failfast = args.failfast
        test_suite.run(result)


if __name__ == '__main__':
    if os.environ.get('IN_PROCESS') == '1':
        in_process()
    else:
        main()
