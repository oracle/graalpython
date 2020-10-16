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


RUNNER = os.path.join(os.path.dirname(__file__), "run_cpython_test.py")


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
            cmd += ["-S", RUNNER]
            for testpattern in working_test[1]:
                cmd.extend(["-k", testpattern])
            testmod = working_test[0].rpartition(".")[2]
            print("Running test:", working_test[0])
            testfile = os.path.join(os.path.dirname(test.__file__), "%s.py" % testmod)
            if not os.path.isfile(testfile):
                testfile = os.path.join(os.path.dirname(test.__file__), "%s/__init__.py" % testmod)
            cmd.append(testfile)
            subprocess.check_call(cmd)
            print(working_test[0], "was finished.")

        fun.__name__ = "%s[%d/%d]" % (working_test[0], idx + 1, len(WORKING_TESTS))
        return fun

    test_f = make_test_func(working_test)
    setattr(TestAllWorkingTests, test_f.__name__, test_f)
    del test_f


# This function has a unittest in test_tagger
def parse_unittest_output(output):
    # The whole reason for this function's complexity is that we want to consume arbitrary
    # warnings after the '...' part without accidentally consuming the next test result
    import re
    re_test_result = re.compile(r"""\b(test\S+) \(([^\s]+)\)(?:\n.*?)?? \.\.\. """, re.MULTILINE | re.DOTALL)
    re_test_status = re.compile(r"""\b(ok|skipped (?:'[^']*'|"[^"]*")|FAIL|ERROR)$""", re.MULTILINE | re.DOTALL)
    pos = 0
    current_result = None
    while True:
        result_match = re_test_result.search(output, pos)
        status_match = re_test_status.search(output, pos)
        if current_result and status_match and (not result_match or status_match.start() < result_match.start()):
            yield current_result.group(1), current_result.group(2), status_match.group(1)
            current_result = None
            pos = status_match.end()
        elif result_match:
            current_result = result_match
            pos = result_match.end()
        else:
            return


if __name__ == "__main__":
    # find working tests
    import re

    executable = sys.executable.split(" ") # HACK: our sys.executable on Java is a cmdline
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
            if not (arg.endswith(".py") or arg.endswith("*")):
                arg += ".py"
            glob_pattern = os.path.join(os.path.dirname(test.__file__), arg)

    p = subprocess.run(["/usr/bin/which", "timeout" if sys.platform != 'darwin' else 'gtimeout'], **kwargs)
    if p.returncode != 0:
        print("Cannot find the 'timeout' GNU tool. Do you have coreutils installed?")
        sys.exit(1)
    timeout = p.stdout.strip()

    testfiles = glob.glob(glob_pattern)
    testfiles += glob.glob(glob_pattern.replace(".py", "/__init__.py"))

    for idx, testfile in enumerate(testfiles):
        for repeat in range(maxrepeats):
            # we always do this multiple times, because sometimes the tagging
            # doesn't quite work e.g. we create a tags file that'll still fail
            # when we use it. Thus, when we run this multiple times, we'll just
            # use the tags and if it fails in the last run, we assume something
            # sad is happening and delete the tags file to skip the tests
            # entirely
            if testfile.endswith("__init__.py"):
                testfile_stem = os.path.basename(os.path.dirname(testfile))
            else:
                testfile_stem = os.path.splitext(os.path.basename(testfile))[0]
            testmod = "test." + testfile_stem
            cmd = [timeout, "-s", "9", "240"] + executable
            if repeat == 0:
                # Allow catching Java exceptions in the first iteration only, so that subsequent iterations
                # (there will be one even if everything succeeds) filter out possible false-passes caused by
                # the tests catching all exceptions somewhere
                cmd += ['--experimental-options', '--python.CatchAllExceptions']
            cmd += ["-S", RUNNER, "-v"]
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

            # n.b.: we add a '*' in the front, so that unittests doesn't add
            # its own asterisks, because now this is already a pattern
            for funcname, classname, result in parse_unittest_output(stderr):
                # We consider skipped tests as passing in order to avoid a situation where a Linux run
                # untags a Darwin-only test and vice versa
                if result == 'ok' or result.startswith('skipped'):
                    passing_tests.append(f"*{classname}.{funcname}")

            with open(tagfile, "w") as f:
                for passing_test in sorted(passing_tests):
                    f.write(passing_test)
                    f.write("\n")
            if not passing_tests:
                os.unlink(tagfile)
                print("No successful tests remaining")
                break

            if p.returncode == 0:
                if repeat == 0 and maxrepeats > 1:
                    print(f"Suite succeeded with {len(passing_tests)} tests, retrying to confirm tags are correct")
                    continue
                print(f"Suite succeeded with {len(passing_tests)} tests")
                break
            else:
                print(f"Suite failed, retrying with {len(passing_tests)} tests")

        else:
            # we tried the last time and failed, so our tags don't work for
            # some reason
            print("The suite failed even in the last attempt, untagging completely")
            try:
                os.unlink(tagfile)
            except Exception:
                pass
