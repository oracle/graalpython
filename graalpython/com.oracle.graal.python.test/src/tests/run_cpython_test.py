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

@dataclass
class TestResult:
    test_id: str
    status: TestStatus
    param: str | None = None
    output: str = ''


def test_id(test):
    if type(test).id is unittest.TestCase.id:
        return test.id()
    else:
        return f"{type(test).__qualname__}:{test.id()}"


def format_exception(err):
    _, exc, tb = err
    while tb and '__unittest' in tb.tb_frame.f_globals:
        tb = tb.tb_next
    exc.__traceback__ = tb
    return ''.join(traceback.format_exception(exc))


def out_tell():
    return os.lseek(1, 0, os.SEEK_CUR)


class SimpleResult(unittest.TestResult):
    def emit(self, **data):
        print(data)

    def startTest(self, test):
        super().startTest(test)
        self.emit(
            event='testStarted',
            test=test_id(test),
            out_pos=out_tell(),
        )

    def stopTest(self, test):
        super().stopTest(test)
        self.emit(
            event='testStopped',
            test=test_id(test),
            out_pos=out_tell(),
        )

    def addSuccess(self, test):
        super().addSuccess(test)
        self.emit(
            event='testResult',
            status=TestStatus.SUCCESS,
            test=test_id(test),
            out_pos=out_tell(),
        )

    def addFailure(self, test, err):
        super().addFailure(test, err)
        self.emit(
            event='testResult',
            status=TestStatus.FAILURE,
            test=test_id(test),
            param=format_exception(err),
            out_pos=out_tell(),
        )

    def addError(self, test, err):
        super().addError(test, err)
        self.emit(
            event='testResult',
            status=TestStatus.FAILURE,
            test=test_id(test),
            param=format_exception(err),
            out_pos=out_tell(),
        )

    def addSkip(self, test, reason):
        super().addSkip(test, reason)
        self.emit(
            event='testResult',
            status=TestStatus.SKIPPED,
            test=test_id(test),
            param=reason,
            out_pos=out_tell(),
        )

    def addExpectedFailure(self, test, err):
        super().addExpectedFailure(test, err)
        self.emit(
            event='testResult',
            status=TestStatus.EXPECTED_FAILURE,
            test=test_id(test),
            param=format_exception(err),
            out_pos=out_tell(),
        )

    def addUnexpectedSuccess(self, test):
        super().addUnexpectedSuccess(test)
        self.emit(
            event='testResult',
            status=TestStatus.UNEXPECTED_SUCCESS,
            test=test_id(test),
            out_pos=out_tell(),
        )


class SendResult(SimpleResult):
    def __init__(self, conn):
        super().__init__()
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


def gather_test_ids(test):
    if isinstance(test, unittest.BaseTestSuite):
        for subtest in test:
            yield from gather_test_ids(subtest)
    else:
        yield test_id(test)


class TestingManager:
    def __init__(self):
        self.lock = threading.Lock()
        self.events = []
        self.results = []
        self.kill_event = threading.Event()

    def report_result(self, result: TestResult):
        self.results.append(result)
        with self.lock:
            message = f"{result.test_id} ... {result.status}"
            if result.status == TestStatus.SKIPPED and result.param:
                message = f"{message} {result.param!r}"
            print(message)

    def shutdown(self):
        self.kill_event.set()

    def should_shutdown(self):
        return self.kill_event.is_set()

    def tests_failed(self):
        return any(result.status not in (TestStatus.SUCCESS, TestStatus.EXPECTED_FAILURE) for result in self.results)

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
                print(f"{fail_type}: {result.test_name}")
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

    def run_tests(self, test_paths: list[str]):
        threads = []
        for test_path in test_paths:
            thread = threading.Thread(target=self.run_in_subprocess_and_watch, args=(test_path,))
            thread.start()
            threads.append(thread)

        for thread in threads:
            thread.join()

        self.display_summary()

    def run_in_subprocess_and_watch(self, test_path: str):
        test_name = _convert_name(test_path)
        loader = unittest.loader.defaultTestLoader
        test_suite = loader.loadTestsFromName(test_name)

        conn, child_conn = multiprocessing.Pipe()
        with tempfile.NamedTemporaryFile(prefix='graalpy-test-out-', mode='w+') as out_file:
            env = os.environ.copy()
            env['IN_PROCESS'] = '1'
            process = subprocess.Popen(
                [sys.executable, '-u', __file__, '--pipe-fd', str(child_conn.fileno())],
                stdout=out_file,
                stderr=out_file,
                env=env,
                pass_fds=[child_conn.fileno()],
            )
            conn.send(test_suite)

            last_started: str | None = None
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


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('tests', nargs='+')

    args = parser.parse_args()

    manager = TestingManager()
    manager.run_tests(args.tests)
    if manager.tests_failed():
        sys.exit(1)


def in_process():
    assert sys.argv[1] == '--pipe-fd'
    fd = int(sys.argv[2])
    conn = multiprocessing.connection.Connection(fd)
    test = conn.recv()
    result = SendResult(conn)
    test(result)


if __name__ == '__main__':
    if os.environ.get('IN_PROCESS') == '1':
        in_process()
    else:
        main()
