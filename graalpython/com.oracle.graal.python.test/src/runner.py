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
import re
import signal
import subprocess
import sys
import tempfile
import threading
import time
import tomllib
import traceback
import unittest
import unittest.loader
from abc import abstractmethod
from collections import defaultdict
from dataclasses import dataclass
from functools import lru_cache
from pathlib import Path

DIR = Path(__file__).parent
IS_GRAALPY = sys.implementation.name == 'graalpy'

if sys.implementation.version < (3, 12):
    # XXX Temporary hack: python 3.12 will have a toml parser in standard library, for now we load vendored one from pip
    [pip_wheel] = (DIR.parent.parent / 'lib-python' / '3' / 'ensurepip' / '_bundled').glob('pip*.whl')
    sys.path.append(pip_wheel)

    sys.path.pop()
else:
    pass


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
                print(f"\r{first_line:{pad}}{newline}{rest}", end=end, flush=True)
            else:
                print(msg, end=end, flush=True)
            self.incomplete_line = msg if incomplete else None


log = Logger().log


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
    def __init__(self, failfast):
        self.failfast = failfast
        self.events = []
        self.results = []

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
        add_item(TestStatus.UNTAGGED, "not tagged")
        add_item(TestStatus.NOT_EXECUTED, "not executed")

        total = sum(count for status, count in counts.items() if status in EXECUTED_STATES)

        log(f"Ran {total} tests")  # TODO timing
        log()
        summary = ", ".join(items)
        overall_status = 'FAILED' if self.tests_failed() else 'OK'
        if summary:
            log(f"{overall_status} ({summary})")
        else:
            log(overall_status)

        if self.failfast and counts.get(TestStatus.NOT_EXECUTED):
            log("\nWARNING: Did not execute all tests because 'failfast' mode is on")

    def run_tests(self, tests: list['TestSuite']):
        for test_suite in tests:
            result = DirectResult(test_suite, self)
            result.failfast = self.failfast
            test_suite.run(result)
            test_suite.add_unexecuted(self.results)
        self.display_summary()


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
    def __init__(self, failfast, num_processes):
        super().__init__(failfast=failfast)
        self.num_processes = num_processes
        self.stop_event = threading.Event()
        self.crashes = []
        self.last_started_test = None
        self.last_out_pos = 0
        self.last_started_timestamp = None
        self.default_test_timeout = 600

    def report_result(self, result: TestResult):
        if self.failfast and result.status in FAILED_STATES:
            self.stop_event.set()
        super().report_result(result)

    def tests_failed(self):
        return super().tests_failed() or bool(self.crashes)

    def run_tests(self, tests: list['TestSuite']):
        if tests:
            num_processes = min(self.num_processes, len(tests))
            with concurrent.futures.ThreadPoolExecutor(num_processes) as executor:
                futures = [
                    executor.submit(self.run_in_subprocess_and_watch, test_suite)
                    for test_suite in tests
                ]
                try:
                    concurrent.futures.wait(futures)
                except KeyboardInterrupt:
                    self.stop_event.set()
                    concurrent.futures.wait(futures)
                    print("Interrupted!")
                    sys.exit(1)

        self.display_summary()

        if self.crashes:
            for crash in self.crashes:
                log('Internal error, test worker crashed outside of tests:')
                log(crash)

    def process_event(self, event, out_file):
        self.events.append(event)
        match event['event']:
            case 'testStarted':
                self.last_started_test = event['test']
                self.last_out_pos = event['out_pos']
                self.report_start(event['test'])
                self.last_started_timestamp = time.time()
            case 'testResult':
                self.last_started_test = None
                out_end = event['out_pos']
                test_output = ''
                if self.last_out_pos != out_end:
                    out_file.seek(self.last_out_pos)
                    test_output = out_file.read(out_end - self.last_out_pos)
                result = TestResult(
                    test_id=event['test'],
                    status=event['status'],
                    param=event.get('param'),
                    output=test_output,
                )
                self.report_result(result)
                self.last_out_pos = event['out_pos']
                self.last_started_timestamp = None

    def run_in_subprocess_and_watch(self, test_suite: 'TestSuite'):
        conn, child_conn = multiprocessing.Pipe()
        with tempfile.NamedTemporaryFile(prefix='graalpytest-out-', mode='w+') as out_file:
            env = os.environ.copy()
            env['IN_PROCESS'] = '1'
            remaining_tests = test_suite.collected_tests
            while remaining_tests and not self.stop_event.is_set():
                self.last_out_pos = out_file.tell()
                python_args = ['-u']
                if IS_GRAALPY:
                    python_args += [
                        "--vm.ea",
                        "--experimental-options=true",
                        "--python.EnableDebuggingBuiltins",
                    ]
                cmd = [sys.executable, *python_args, __file__, '--pipe-fd', str(child_conn.fileno())]
                if self.failfast:
                    cmd.append('--failfast')
                cmd += [str(s) for s in remaining_tests]
                process = subprocess.Popen(
                    cmd,
                    stdout=out_file,
                    stderr=out_file,
                    env=env,
                    pass_fds=[child_conn.fileno()],
                )

                timed_out = False

                while process.poll() is None:
                    while conn.poll(0.1):
                        self.process_event(conn.recv(), out_file)
                    if self.stop_event.is_set():
                        interrupt_process(process)
                        break
                    if self.last_started_timestamp is not None and time.time() - self.last_started_timestamp >= self.default_test_timeout:
                        interrupt_process(process)
                        timed_out = True
                        # Drain the pipe
                        while conn.poll(0.1):
                            conn.recv()
                        break
                returncode = process.wait()
                if self.stop_event.is_set():
                    test_suite.add_unexecuted(self.results)
                    return
                while conn.poll(0.1):
                    self.process_event(conn.recv(), out_file)
                if returncode != 0 or timed_out:
                    out_file.seek(self.last_out_pos)
                    output = out_file.read()
                    if self.last_started_test:
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
                            test_id=self.last_started_test,
                            status=TestStatus.ERROR,
                            param=message,
                            output=output,
                        ))
                        remaining_tests = remaining_tests[remaining_tests.index(self.last_started_test) + 1:]
                        self.last_started_timestamp = None
                        self.last_started_test = None
                        continue
                    else:
                        # Crashed outside of tests, don't retry
                        self.crashes.append(output)
                test_suite.add_unexecuted(self.results)
                return


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
    run_top_level_functions: bool = False


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
            run_top_level_functions = config_dict.get('run_top_level_functions', False)
            return Config(rootdir=rootdir, tags_dir=tags_dir, run_top_level_functions=run_top_level_functions)


def config_for_file(test_file: Path) -> Config:
    path = test_file.parent
    while path.is_dir():
        if config := config_for_dir(path):
            return config
        if path.parent == path:
            break
        path = path.parent
    return Config()


@dataclass
class TestSuite:
    config: Config
    test_file: Path
    pythonpath: list[str]
    test_suite: unittest.TestSuite
    collected_tests: list[TestId]
    untagged_tests: list[TestId]

    def run(self, result):
        saved_path = sys.path[:]
        sys.path[:] = self.pythonpath
        try:
            self.test_suite.run(result)
        finally:
            sys.path[:] = saved_path

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


def group_specifiers_by_file(specifiers: list[TestSpecifier]) -> dict[Path, list[TestSpecifier]]:
    by_file = defaultdict(list)
    for specifier in specifiers:
        by_file[specifier.test_file].append(specifier)
    return by_file


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
                sys.exit(f"Test path {path} doesn't exist")
            if path.is_dir():
                if path.name.startswith('test_') and (path / '__init__.py').exists():
                    expanded_paths.append(path)
                expanded_paths.extend(list(path.glob("test_*.py")))
                expanded_paths.extend((p.parent for p in path.glob("test_*/__init__.py")))
            else:
                if path.name == '__init__.py':
                    path = path.parent
                expanded_paths.append(path)
        for path in expanded_paths:
            expanded_specifiers.append(TestSpecifier(path, specifier.test_name))
    return expanded_specifiers


def collect_module(test_file: Path, specifiers: list[TestSpecifier], use_tags=False) -> TestSuite | None:
    config = config_for_file(test_file)
    saved_path = sys.path[:]
    sys.path.insert(0, str(config.rootdir))
    try:
        loader = TopLevelFunctionLoader() if config.run_top_level_functions else unittest.TestLoader()
        tags = None
        if use_tags and config.tags_dir:
            tags = read_tags(test_file, config)
            if not tags:
                return None
        try:
            test_module = test_path_to_module(test_file.resolve().relative_to(config.rootdir))
            test_suite = loader.loadTestsFromName(test_module)
        except unittest.SkipTest as e:
            log(f"Test file {test_file} skipped: {e}")
            return
        collected_tests, untagged_tests = filter_tree(test_file, test_suite, specifiers, tags)
        if collected_tests:
            return TestSuite(config, test_file, sys.path[:], test_suite, collected_tests, untagged_tests)
    finally:
        sys.path[:] = saved_path


def collect(all_specifiers: list[TestSpecifier], use_tags=False) -> list[TestSuite]:
    to_run = []
    all_specifiers = expand_specifier_paths(all_specifiers)
    for test_file, specifiers in group_specifiers_by_file(all_specifiers).items():
        if not test_file.exists():
            sys.exit(f"File does not exist: {test_file}")
        collected = collect_module(test_file, specifiers, use_tags=use_tags)
        if collected:
            to_run.append(collected)
    return to_run


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


class TopLevelFunctionLoader(unittest.loader.TestLoader):
    def loadTestsFromModule(self, module, *, pattern=None):
        test_suite = super().loadTestsFromModule(module, pattern=pattern)
        for name, obj in vars(module).items():
            if name.startswith('test_') and callable(obj):
                test_suite.addTest(unittest.FunctionTestCase(obj))
        return test_suite


def in_process():
    parser = argparse.ArgumentParser()
    parser.add_argument('--pipe-fd', type=int, required=True)
    parser.add_argument('--failfast', action='store_true')
    parser.add_argument('tests', nargs='*', type=TestSpecifier.from_str)
    args = parser.parse_args()
    conn = multiprocessing.connection.Connection(args.pipe_fd)
    for test_suite in collect(args.tests):
        result = PipeResult(test_suite, conn)
        result.failfast = args.failfast
        test_suite.run(result)


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('-n', '--num-processes', type=int)
    parser.add_argument('-f', '--failfast', action='store_true')
    parser.add_argument('--all', action='store_true')
    parser.add_argument('--collect-only', action='store_true')
    parser.add_argument('tests', nargs='+', type=TestSpecifier.from_str)

    args = parser.parse_args()

    if IS_GRAALPY:
        if os.environ.get('GRAALPYTEST_ALLOW_NO_JAVA_ASSERTIONS', '').lower() not in ('true', '1'):
            if not __graalpython__.java_assert():
                sys.exit(
                    "Java assertions are not enabled, refusing to run. Add --vm.ea to your invocation. Set GRAALPYTEST_ALLOW_NO_JAVA_ASSERTIONS=true to disable this check\n")
        if not hasattr(__graalpython__, 'tdebug'):
            sys.exit("Needs to be run with --experimental-options --python.EnableDebuggingBuiltins\n")

    tests = collect(args.tests, use_tags=(not args.all))
    if args.collect_only:
        for test_suite in tests:
            for test in test_suite.collected_tests:
                print(test)
        return

    if not tests:
        sys.exit("No tests matched\n")

    runner_args = {
        'failfast': args.failfast,
    }
    if not args.num_processes:
        runner = TestRunner(**runner_args)
    else:
        runner = ParallelTestRunner(**runner_args, num_processes=args.num_processes)

    runner.run_tests(tests)
    if runner.tests_failed():
        sys.exit(1)


if __name__ == '__main__':
    if os.environ.get('IN_PROCESS') == '1':
        in_process()
    else:
        main()
