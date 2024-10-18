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
import traceback
import unittest
from abc import abstractmethod
from collections import defaultdict
from dataclasses import dataclass

sys.path.insert(0, os.getcwd())


class TestStatus(enum.StrEnum):
    SUCCESS = "ok"
    FAILURE = "FAIL"
    ERROR = "ERROR"
    SKIPPED = "skipped"
    EXPECTED_FAILURE = "expected failure"
    UNEXPECTED_SUCCESS = "unexpected success"
    NOT_EXECUTED = "not executed"


FAILED_STATES = TestStatus.FAILURE, TestStatus.ERROR, TestStatus.UNEXPECTED_SUCCESS


class TestSpecifier:
    def __init__(self, *components):
        assert components
        self.components = components

    def __repr__(self):
        return '::'.join(self.components)

    def __hash__(self):
        return hash(self.components)

    def __eq__(self, other):
        if isinstance(other, TestSpecifier):
            return self.components == other.components
        return NotImplemented

    @classmethod
    def from_str(cls, s: str):
        return cls(*s.split('::'))

    @classmethod
    def from_test(cls, test_file: str, test):
        if isinstance(test, unittest.TestCase):
            name = test._testMethodName if type(test).id is unittest.TestCase.id else test.id()
            return cls(test_file, type(test).__qualname__, name)
        return cls(test_file, type(test).__qualname__)

    def matches(self, other: 'TestSpecifier'):
        for c1, c2 in zip(self.components, other.components):
            if c1 != c2:
                return False
        return True


@dataclass
class TestResult:
    test_id: TestSpecifier
    status: TestStatus
    param: str | None = None
    output: str = ''


def test_id(test_file, test):
    name = test._testMethodName if type(test).id is unittest.TestCase.id else test.id()
    return f"{test_file}::{type(test).__qualname__}::{name}"


def test_matches_specifier(test: str, specifier: list[str]):
    split = test.split('::')
    for match in specifier:
        for a, b in zip(split, match.split('::')):
            if a != b:
                break
        return True
    return False


def group_tests_by_file(tests: list[str]):
    by_file: dict[str, list | None] = defaultdict(list)
    for test in tests:
        specifier = TestSpecifier.from_str(test)
        by_file[specifier.components[0]].append(specifier)
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
    def __init__(self, test_file):
        super().__init__()
        self.test_file = test_file

    def test_id(self, test):
        return TestSpecifier.from_test(self.test_file, test)

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
    def __init__(self, test_file, test_runner: 'TestRunner'):
        super().__init__(test_file)
        self.runner = test_runner

    def report_result(self, result: TestResult):
        self.runner.report_result(result)


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

    def stopTest(self, test):
        super().stopTest(test)
        self.emit(
            event='testStopped',
            test=self.test_id(test),
            out_pos=out_tell(),
        )


class PipeResult(AbstractRemoteResult):
    def __init__(self, test_file, conn):
        super().__init__(test_file)
        self.conn = conn

    def emit(self, **data):
        self.conn.send(data)


# Copied from unittest
def _convert_name(name):
    # on Linux / Mac OS X 'foo.PY' is not importable, but on
    # Windows it is. Simpler to do a case insensitive match
    # a better check would be to check that the name is a
    # valid Python module name.
    if os.path.isfile(name) and name.lower().endswith('.py'):
        if os.path.isabs(name):
            rel_path = os.path.relpath(name, os.getcwd())
            if os.path.isabs(rel_path) or rel_path.startswith(os.pardir):
                return name
            name = rel_path
        # on Windows both '\' and '/' are used as path
        # separators. Better to replace both than rely on os.path.sep
        return os.path.normpath(name)[:-3].replace('\\', '.').replace('/', '.')
    return name


class TestRunner:
    def __init__(self):
        self.events = []
        self.results = []

    def report_result(self, result: TestResult):
        self.results.append(result)
        message = f"{result.test_id} ... {result.status}"
        if result.status == TestStatus.SKIPPED and result.param:
            message = f"{message} {result.param!r}"
        print(message)

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

        add_item(TestStatus.FAILURE, "failures")
        add_item(TestStatus.ERROR, "errors")
        add_item(TestStatus.SKIPPED, "skipped")
        add_item(TestStatus.EXPECTED_FAILURE, "expected failures")
        add_item(TestStatus.UNEXPECTED_SUCCESS, "unexpected successes")
        add_item(TestStatus.NOT_EXECUTED, "not executed")

        print(f"Ran {sum(counts.values())} tests")  # TODO timing
        print()
        summary = ", ".join(items)
        overall_status = 'FAILED' if self.tests_failed() else 'OK'
        if summary:
            print(f"{overall_status} ({summary})")
        else:
            print(overall_status)

    def run_tests(self, tests: list[str]):
        collected = collect(tests)
        for test_file, test_suite, found_tests in collected:
            test_suite(DirectResult(test_file, self))
        self.display_summary()


class ParallelTestRunner(TestRunner):
    def __init__(self, num_processes: int):
        super().__init__()
        self.num_processes = num_processes
        self.lock = threading.Lock()
        self.kill_event = threading.Event()

    def report_result(self, result: TestResult):
        with self.lock:
            super().report_result(result)

    def shutdown(self):
        self.kill_event.set()

    def should_shutdown(self):
        return self.kill_event.is_set()

    def run_tests(self, tests: list[str]):
        collected = collect(tests)
        futures = []
        num_processes = min(self.num_processes, len(collected))
        with concurrent.futures.ThreadPoolExecutor(num_processes) as executor:
            for test_file, test_suite, found_tests in collected:
                futures.append(executor.submit(self.run_in_subprocess_and_watch, found_tests))
            concurrent.futures.wait(futures)

        self.display_summary()

    def run_in_subprocess_and_watch(self, specifiers: list[TestSpecifier]):
        conn, child_conn = multiprocessing.Pipe()
        with tempfile.NamedTemporaryFile(prefix='graalpy-test-out-', mode='w+') as out_file:
            env = os.environ.copy()
            env['IN_PROCESS'] = '1'
            remaining_specifiers = specifiers
            while remaining_specifiers:
                process = subprocess.Popen(
                    [sys.executable, '-u', __file__, '--pipe-fd', str(child_conn.fileno())],
                    stdout=out_file,
                    stderr=out_file,
                    env=env,
                    pass_fds=[child_conn.fileno()],
                )
                conn.send([str(s) for s in remaining_specifiers])

                last_started: TestSpecifier | None = None
                out_start = -1

                def process_event(event):
                    nonlocal last_started, out_start
                    self.events.append(event)
                    match event['event']:
                        case 'testStarted':
                            last_started = event['test']
                            out_start = event['out_pos']
                        case 'testResult':
                            last_started = None
                            out_end = event['out_pos']
                            output = ''
                            if out_start != out_end:
                                out_file.seek(out_start)
                                output = out_file.read(out_end - out_start)
                            result = TestResult(
                                test_id=event['test'],
                                status=event['status'],
                                param=event.get('param'),
                                output=output,
                            )
                            self.report_result(result)

                while process.poll() is None:
                    if self.should_shutdown():
                        try:
                            process.terminate()
                            try:
                                process.wait(2)
                            except subprocess.TimeoutExpired:
                                process.kill()
                        except OSError:
                            break
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
                    remaining_specifiers = remaining_specifiers[remaining_specifiers.index(last_started) + 1:]
                    continue
                break


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('-n', '--num-processes', type=int)
    parser.add_argument('tests', nargs='+')

    args = parser.parse_args()

    runner = TestRunner() if args.num_processes is None else ParallelTestRunner(args.num_processes)
    runner.run_tests(args.tests)
    if runner.tests_failed():
        sys.exit(1)


def filter_tree(test_file, test_suite, specifiers):
    keep_tests = []
    found_specifiers = []
    for test in test_suite:
        if hasattr(test, '__iter__'):
            sub_specifiers = filter_tree(test_file, test, specifiers)
            if sub_specifiers:
                keep_tests.append(test)
                found_specifiers += sub_specifiers
        else:
            specifier = TestSpecifier.from_test(test_file, test)
            if any(s.matches(specifier) for s in specifiers):
                keep_tests.append(test)
                found_specifiers.append(specifier)
    test_suite._tests = keep_tests
    return found_specifiers


def collect(tests: list[str]) -> list[tuple[str, unittest.TestSuite, list[TestSpecifier]]]:
    to_run = []
    for test_file, specifiers in group_tests_by_file(tests).items():
        loader = unittest.defaultTestLoader
        test_suite = loader.loadTestsFromName(_convert_name(test_file))
        found_tests = filter_tree(test_file, test_suite, specifiers)
        if found_tests:
            to_run.append((test_file, test_suite, found_tests))
    return to_run


def in_process():
    assert sys.argv[1] == '--pipe-fd'
    fd = int(sys.argv[2])
    conn = multiprocessing.connection.Connection(fd)
    tests = conn.recv()
    collected = collect(tests)
    for test_file, test_suite, found_tests in collected:
        test_suite(PipeResult(test_file, conn))


if __name__ == '__main__':
    if os.environ.get('IN_PROCESS') == '1':
        in_process()
    else:
        main()
