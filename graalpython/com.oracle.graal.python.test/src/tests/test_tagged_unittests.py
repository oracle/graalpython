# Copyright (c) 2019, 2020, Oracle and/or its affiliates. All rights reserved.
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

import glob
import os
import subprocess
import sys
import test


if os.environ.get("ENABLE_CPYTHON_TAGGED_UNITTESTS") == "true" or __name__ == "__main__":
    TAGS_DIR = os.path.join(os.path.dirname(__file__), "unittest_tags")
else:
    TAGS_DIR = "null"


def working_selectors(tagfile):
    if os.path.exists(tagfile):
        with open(tagfile) as f:
            return [line.strip() for line in f if line]
    else:
        return None


def working_tests():
    working_tests = []
    glob_pattern = os.path.join(TAGS_DIR, "*.txt")
    for arg in sys.argv:
        if arg.startswith("--tagfile="):
            glob_pattern = os.path.join(TAGS_DIR, arg.partition("=")[2])
            sys.argv.remove(arg)
            break
    for tagfile in glob.glob(glob_pattern):
        test = os.path.splitext(os.path.basename(tagfile))[0]
        working_tests.append((test, working_selectors(tagfile)))
    return working_tests


class TestAllWorkingTests():
    pass

WORKING_TESTS = working_tests()
for idx, working_test in enumerate(WORKING_TESTS):
    def make_test_func(working_test):
        def fun(self):
            cmd = [sys.executable]
            if "--inspect" in sys.argv:
                cmd.append("--inspect")
            if "-debug-java" in sys.argv:
                cmd.append("-debug-java")
            cmd += ["-S", "-m", "unittest"]
            for testpattern in working_test[1]:
                cmd.extend(["-k", testpattern])
            testmod = working_test[0].rpartition(".")[2]
            print("Running test:", working_test[0])
            cmd.append(os.path.join(os.path.dirname(test.__file__), "%s.py" % testmod))
            subprocess.check_call(cmd)
            print(working_test[0], "was finished.")
            
        fun.__name__ = "%s[%d/%d]" % (working_test[0], idx + 1, len(WORKING_TESTS))
        return fun

    test_f = make_test_func(working_test)
    setattr(TestAllWorkingTests, test_f.__name__, test_f)
    del test_f


if __name__ == "__main__":
    # find working tests
    import re

    executable = sys.executable.split(" ") # HACK: our sys.executable on Java is a cmdline
    re_test_result = re.compile(r"""(test\S+) \(([^\s]+)\)(?:\n.*?)? \.\.\. \b(ok|skipped(?: ["'][^\n]+)?|ERROR|FAIL)$""", re.MULTILINE | re.DOTALL)
    kwargs = {"stdout": subprocess.PIPE, "stderr": subprocess.PIPE, "text": True, "check": False}

    glob_pattern = os.path.join(os.path.dirname(test.__file__), "test_*.py")
    retag = False
    maxrepeats = 4
    for arg in sys.argv[1:]:
        if arg == "--retag":
            retag = True
        elif arg.startswith("--maxrepeats="):
            maxrepeats = int(arg.partition("=")[2])
        elif arg == "--help":
            print(sys.argv[0] + " [--retag] [--maxrepeats=n] [glob]")
        else:
            glob_pattern = os.path.join(os.path.dirname(test.__file__), arg)

    p = subprocess.run(["/usr/bin/which", "timeout" if sys.platform != 'darwin' else 'gtimeout'], **kwargs)
    if p.returncode != 0:
        print("Cannot find the 'timeout' GNU tool. Do you have coreutils installed?")
        sys.exit(1)
    timeout = p.stdout.strip()

    testfiles = glob.glob(glob_pattern)
    for idx, testfile in enumerate(testfiles):
        for repeat in range(maxrepeats):
            # we always do this multiple times, because sometimes the tagging
            # doesn't quite work e.g. we create a tags file that'll still fail
            # when we use it. Thus, when we run this multiple times, we'll just
            # use the tags and if it fails in the last run, we assume something
            # sad is happening and delete the tags file to skip the tests
            # entirely
            testfile_stem = os.path.splitext(os.path.basename(testfile))[0]
            testmod = "test." + testfile_stem
            cmd = [timeout, "-s", "9", "120"] + executable + ["-S", "-m"]
            tagfile = os.path.join(TAGS_DIR, testfile_stem + ".txt")
            if retag and repeat == 0:
                test_selectors = []
            else:
                test_selectors = working_selectors(tagfile)

            if test_selectors is None:
                # there's no tagfile for this, so it's not working at all (or
                # shouldn't be tried).
                continue

            print("[%d/%d, Try %d] Testing %s" %(idx + 1, len(testfiles), repeat + 1, testmod))
            cmd += ["unittest", "-v"]
            for selector in test_selectors:
                cmd += ["-k", selector]
            cmd.append(testfile)

            print(" ".join(cmd))
            p = subprocess.run(cmd, **kwargs)
            print("*stdout*")
            print(p.stdout)
            print("*stderr*")
            print(p.stderr)

            passing_tests = []
            failed_tests = []

            def get_pass_name(funcname, classname):
                try:
                    imported_test_module = __import__(testmod)
                except Exception:
                    pass
                else:
                    # try hard to get a most specific pattern
                    classname = "".join(classname.rpartition(testmod)[1:])
                    clazz = imported_test_module
                    path_to_class = classname.split(".")[1:]
                    for part in path_to_class:
                        clazz = getattr(clazz, part, None)
                    if clazz:
                        func = getattr(clazz, funcname, None)
                        if func:
                            return func.__qualname__
                return funcname

            stderr = p.stderr.replace("Please note: This Python implementation is in the very early stages, and can run little more than basic benchmarks at this point.\n", '')

            # n.b.: we add a '*' in the front, so that unittests doesn't add
            # its own asterisks, because now this is already a pattern
            for funcname, classname, result in re_test_result.findall(stderr):
                # We consider skipped tests as passing in order to avoid a situation where a Linux run
                # untags a Darwin-only test and vice versa
                if result == 'ok' or result.startswith('skipped'):
                    passing_tests.append("*" + get_pass_name(funcname, classname))
                else:
                    failed_tests.append("*" + get_pass_name(funcname, classname))

            # n.b.: unittests uses the __qualname__ of the function as
            # pattern, which we're trying to do as well. however, sometimes
            # the same function is shared in multiple test classes, and
            # fails in some. so we always subtract the failed patterns from
            # the passed patterns
            passing_only_patterns = set(passing_tests) - set(failed_tests)
            with open(tagfile, "w") as f:
                for passing_test in sorted(passing_only_patterns):
                    f.write(passing_test)
                    f.write("\n")
            if not passing_only_patterns:
                os.unlink(tagfile)

            if p.returncode == 0:
                break

        else:
            # we tried the last time and failed, so our tags don't work for
            # some reason
            try:
                os.unlink(tagfile)
            except Exception:
                pass
