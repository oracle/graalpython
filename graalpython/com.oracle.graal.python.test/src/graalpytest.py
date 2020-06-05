# Copyright (c) 2018, 2020, Oracle and/or its affiliates. All rights reserved.
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

#!/usr/bin/env mx python
import _io
import sys
import time
import _thread

os = sys.modules.get("posix", sys.modules.get("nt", None))
if os is None:
    raise ImportError("posix or nt module is required in builtin modules")

FAIL = '\033[91m'
ENDC = '\033[0m'
BOLD = '\033[1m'

verbose = False

print_lock = _thread.RLock()
class ThreadPool():
    cnt_lock = _thread.RLock()
    cnt = 0
    if os.environ.get(b"ENABLE_THREADED_GRAALPYTEST") == b"true":
        maxcnt = min(os.cpu_count(), 16)
        sleep = time.sleep
        start_new_thread = _thread.start_new_thread
        print("Running with %d threads" % maxcnt)
    else:
        sleep = lambda x: x
        start_new_thread = lambda f, args: f(*args)
        maxcnt = 1

    @classmethod
    def start(self, function):
        self.acquire_token()
        def runner():
            try:
                function()
            finally:
                self.release_token()
        self.start_new_thread(runner, ())
        self.sleep(0.5)

    @classmethod
    def acquire_token(self):
        while True:
            with self.cnt_lock:
                if self.cnt < self.maxcnt:
                    self.cnt += 1
                    break
            self.sleep(1)

    @classmethod
    def release_token(self):
        with self.cnt_lock:
            self.cnt -= 1

    @classmethod
    def shutdown(self):
        self.sleep(2)
        while self.cnt > 0:
            self.sleep(2)


def dump_truffle_ast(func):
    try:
        print(__dump_truffle_ast__(func))
    except:
        pass


class SkipTest(BaseException):
    pass


class TestCase(object):

    def __init__(self):
        self.exceptions = []
        self.passed = 0
        self.failed = 0

    def get_useful_frame(self, tb):
        from traceback import extract_tb
        frame_summaries = extract_tb(tb)
        frame_summaries.reverse()
        for summary in frame_summaries:
            # Skip frame summary entries that refer to this file. These summaries will mostly be there because of the
            # assert functions and their location is not interesting.
            if summary[0] != __file__:
                return summary


    def run_safely(self, func, print_immediately=False):
        if verbose:
            with print_lock:
                print(u"\n\t\u21B3 ", func.__name__, " ", end="")
        try:
            func()
        except BaseException as e:
            if isinstance(e, SkipTest):
                print("Skipped: %s" % e)
            else:
                if print_immediately:
                    print("Exception during setup occurred: %s\n" % e)
                code = func.__code__
                _, _, tb = sys.exc_info()
                try:
                    filename, line, func, text = self.get_useful_frame(tb)
                    self.exceptions.append(
                        ("In test '%s': %s:%d (%s)" % (code.co_filename, filename, line, func), e)
                    )
                except BaseException:
                    self.exceptions.append(
                        ("%s:%d (%s)" % (code.co_filename, code.co_firstlineno, func), e)
                    )
                return False
        else:
            return True

    def run_test(self, func):
        if "test_main" in str(func):
            pass
        elif not hasattr(func, "__call__"):
            pass
        else:
            def do_run():
                start = time.monotonic()
                r = self.run_safely(func)
                end = time.monotonic() - start
                with print_lock:
                    self.success(end) if r else self.failure(end)
            ThreadPool.start(do_run)

    def success(self, time):
        self.passed += 1
        if verbose:
            print("[%.3fs]" % time, end=" ")
        print(".", end="", flush=True)

    def failure(self, time):
        self.failed += 1
        fail_msg = FAIL + BOLD + "F" + ENDC if verbose else "F"
        if verbose:
            print("[%.3fs]" % time, end=" ")
        print(fail_msg, end="", flush=True)

    def assertIsInstance(self, value, cls):
        assert isinstance(value, cls), "Expected %r to be instance of %r" % (value, cls)

    def assertTrue(self, value, msg=""):
        assert value, msg

    def assertFalse(self, value, msg=""):
        assert not value, msg

    def assertIsNone(self, value, msg=""):
        if not msg:
            msg = "Expected '%r' to be None" % value
        assert value is None, msg

    def assertIsNotNone(self, value, msg=""):
        if not msg:
            msg = "Expected '%r' to not be None" % value
        assert value is not None, msg

    def assertIs(self, actual, expected, msg=""):
        if not msg:
            msg = "Expected '%r' to be '%r'" % (actual, expected)
        assert actual is expected, msg

    def assertIsNot(self, actual, expected, msg=""):
        if not msg:
            msg = "Expected '%r' not to be '%r'" % (actual, expected)
        assert actual is not expected, msg

    def assertEqual(self, expected, actual, msg=None):
        if not msg:
            msg = "Expected '%r' to be equal to '%r'" % (actual, expected)
        assert expected == actual, msg

    def assertNotEqual(self, expected, actual, msg=None):
        if not msg:
            msg = "Expected '%r' to not be equal to '%r'" % (actual, expected)
        assert expected != actual, msg

    def assertAlmostEqual(self, expected, actual, msg=None):
        self.assertEqual(round(expected, 2), round(actual, 2), msg)

    def assertGreater(self, expected, actual, msg=None):
        if not msg:
            msg = "Expected '%r' to be greater than '%r'" % (actual, expected)
        assert expected > actual, msg

    def assertSequenceEqual(self, expected, actual, msg=None):
        if not msg:
            msg = "Expected '%r' to be equal to '%r'" % (actual, expected)
        assert len(expected) == len(actual), msg
        actual_iter = iter(actual)
        for expected_value in expected:
            assert expected_value == next(actual_iter), msg

    def assertIsInstance(self, obj, cls, msg=None):
        """Same as self.assertTrue(isinstance(obj, cls)), with a nicer
        default message."""
        if not isinstance(obj, cls):
            message = msg
            if msg is None:
                message = '%s is not an instance of %r' % (obj, cls)
            assert False, message

    def fail(self, msg):
        assert False, msg

    def assertRaises(self, exc_type, function=None, *args, **kwargs):
        return self.assertRaisesRegex(exc_type, None, function, *args, **kwargs)

    class assertRaisesRegex():
        def __init__(self, exc_type, exc_regex, function=None, *args, **kwargs):
            import re
            function = function
            if function is None:
                self.exc_type = exc_type
                self.exc_regex = exc_regex
            else:
                try:
                    function(*args, **kwargs)
                except exc_type as exc:
                    if exc_regex:
                        assert re.search(exc_regex, str(exc)), "%s does not match %s" % (exc_regex, exc)
                else:
                    assert False, "expected '%r' to raise '%r'" % (function, exc_type)

        def __enter__(self):
            return self

        def __exit__(self, exc_type, exc, traceback):
            import re
            if not exc_type:
                assert False, "expected '%r' to be raised" % self.exc_type
            elif self.exc_type in exc_type.mro():
                self.exception = exc
                if self.exc_regex:
                    assert re.search(self.exc_regex, str(exc)), "%s does not match %s" % (self.exc_regex, exc)
                return True

    def assertIn(self, expected, in_str, msg=""):
        if not msg:
            msg = "Expected '%r' to be in '%r'" % (expected, in_str)
        assert expected in in_str, msg

    @classmethod
    def run(cls, items=None):
        instance = cls()
        if items is None:
            items = []
            for typ in cls.mro():
                if typ is TestCase:
                    break
                items += typ.__dict__.items()
        if hasattr(instance, "setUp"):
            if not instance.run_safely(instance.setUp, print_immediately=True):
                return instance
        for k, v in items:
            if k.startswith("test"):
                if patterns:
                    if not any(p in k for p in patterns):
                        continue
                instance.run_test(getattr(instance, k, v))
        if hasattr(instance, "tearDown"):
            instance.run_safely(instance.tearDown)
        return instance

    @staticmethod
    def runClass(cls):
        if TestCase in cls.mro():
            return cls.run()
        class ThisTestCase(cls, TestCase): pass
        return ThisTestCase.run()


class TestRunner(object):

    def __init__(self, paths):
        self.testfiles = TestRunner.find_testfiles(paths)
        self.exceptions = []
        self.passed = 0
        self.failed = 0

    @staticmethod
    def find_testfiles(paths):
        testfiles = []
        while paths:
            path = paths.pop()
            if path.endswith(".py") and path.rpartition("/")[2].startswith("test_"):
                testfiles.append(path)
            else:
                try:
                    paths += [(path + f if path.endswith("/") else "%s/%s" % (path, f)) for f in os.listdir(path)]
                except OSError:
                    pass
        return testfiles

    def test_modules(self):
        for testfile in self.testfiles:
            name = testfile.rpartition("/")[2].partition(".")[0].replace('.py', '')
            directory = testfile.rpartition("/")[0]
            pkg = []
            while any(f.endswith("__init__.py") for f in os.listdir(directory)):
                directory, slash, postfix = directory.rpartition("/")
                pkg.insert(0, postfix)
            if pkg:
                sys.path.insert(0, directory)
                try:
                    test_module = __import__(".".join(pkg + [name]))
                    for p in pkg[1:]:
                        test_module = getattr(test_module, p)
                    test_module = getattr(test_module, name)
                except BaseException as e:
                    _, _, tb = sys.exc_info()
                    try:
                        from traceback import extract_tb
                        filename, line, func, text = extract_tb(tb)[-1]
                        self.exceptions.append(
                            ("In test '%s': Exception occurred in %s:%d" % (testfile, filename, line), e)
                        )
                    except BaseException:
                        self.exceptions.append((testfile, e))
                else:
                    yield test_module
                sys.path.pop(0)
            else:
                test_module = type(sys)(name, testfile)
                try:
                    with _io.FileIO(testfile, "r") as f:
                        test_module.__file__ = testfile
                        exec(compile(f.readall(), testfile, "exec"), test_module.__dict__)
                except BaseException as e:
                    self.exceptions.append((testfile, e))
                else:
                    yield test_module

    def run(self):
        for module in self.test_modules():
            if verbose:
                print(u"\n\u25B9 ", module.__name__, end="")
            # some tests can modify the global scope leading to a RuntimeError: test_scope.test_nesting_plus_free_ref_to_global
            module_dict = dict(module.__dict__)
            for k, v in module_dict.items():
                if (k.startswith("Test") or k.endswith("Test") or k.endswith("Tests")) and isinstance(v, type):
                    testcase = TestCase.runClass(v)
                else:
                    testcase = TestCase.run(items=[(k, v)])
                self.exceptions += testcase.exceptions
                self.passed += testcase.passed
                self.failed += testcase.failed
            if verbose:
                print()
        ThreadPool.shutdown()
        print("\n\nRan %d tests (%d passes, %d failures)" % (self.passed + self.failed, self.passed, self.failed))
        for e in self.exceptions:
            msg, exc = e
            print(msg)
            if verbose:
                try:
                    import traceback
                    traceback.print_exception(type(exc), exc, exc.__traceback__)
                except Exception:
                    pass
            else:
                print(exc)

        if self.exceptions or self.failed:
            os._exit(1)


def skipIf(boolean, *args, **kwargs):
    return skipUnless(not boolean, *args, **kwargs)


def skipUnless(boolean, msg=""):
    if not boolean:
        def decorator(f):
            def wrapper(*args, **kwargs):
                pass
            return wrapper
    else:
        def decorator(f):
            return f
    return decorator


class TextTestResult():
    "Just a dummy to satisfy the unittest.support import"
    pass


if __name__ == "__main__":
    sys.modules["unittest"] = sys.modules["__main__"]
    patterns = []
    argv = sys.argv[:]
    idx = 0
    while idx < len(argv):
        if argv[idx] == "-k":
            argv.pop(idx)
            try:
                patterns.append(argv.pop(idx))
            except IndexError:
                print("-k needs an argument")
        idx += 1

    if argv[1] == "-v":
        verbose = True
        paths = argv[2:]
    else:
        verbose = False
        paths = argv[1:]

    python_paths = set()
    for pth in paths:
        module_path = pth
        if pth.endswith('.py'):
            module_path = pth.rsplit('/', 1)[0]
        python_paths.add(module_path)

    for pth in python_paths:
        sys.path.append(pth)

    TestRunner(paths).run()
