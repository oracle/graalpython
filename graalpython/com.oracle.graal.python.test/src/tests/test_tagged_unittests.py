# Copyright (c) 2019, Oracle and/or its affiliates. All rights reserved.
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


if os.environ.get("ENABLE_CPYTHON_TAGGED_UNITTESTS") == "true" or os.environ.get("CI") is None:
    # On the CI, I'd like to explicitly enable these, so we can run them in a
    # separate job easily. But it's not important for local execution
    TAGS_DIR = os.path.join(os.path.dirname(__file__), "unittest_tags")
else:
    TAGS_DIR = "null"


def working_selectors(tagfile):
    if os.path.exists(tagfile):
        with open(tagfile) as f:
            return [line.strip() for line in f if line]
    else:
        return []


def working_tests():
    working_tests = []
    for tagfile in glob.glob(os.path.join(TAGS_DIR, "*.txt")):
        test = os.path.splitext(os.path.basename(tagfile))[0]
        working_tests.append((test, working_selectors(tagfile)))
    return working_tests


for working_test in working_tests():
    def make_test_func(working_test):
        def fun():
            cmd = [sys.executable, "-m", "unittest"]
            for testpattern in working_test[1]:
                cmd.extend(["-k", testpattern])
            testmod = working_test[0].rpartition(".")[2]
            print()
            cmd.append(os.path.join(os.path.dirname(test.__file__), "%s.py" % testmod))
            subprocess.check_call(cmd)

        fun.__name__ = working_test[0]
        return fun

    test_f = make_test_func(working_test)
    globals()[test_f.__name__] = test_f
    del test_f


if __name__ == "__main__":
    # find working tests
    import re

    executable = sys.executable.split(" ") # HACK: our sys.executable on Java is a cmdline
    re_success = re.compile("^(test\S+)[^\r\n]* \.\.\. ok$", re.MULTILINE)
    kwargs = {"stdout": subprocess.PIPE, "stderr": subprocess.PIPE, "text": True, "check": False}

    if len(sys.argv) > 1:
        glob_pattern = sys.argv[1]
    else:
        glob_pattern = os.path.join(os.path.dirname(test.__file__), "test_*.py")

    testfiles = glob.glob(glob_pattern)
    for idx, testfile in enumerate(testfiles):
        testfile_stem = os.path.splitext(os.path.basename(testfile))[0]
        testmod = "test." + testfile_stem
        cmd = ["/usr/bin/timeout", "-s", "9", "60"] + executable + ["-m"]
        tagfile = os.path.join(TAGS_DIR, testfile_stem + ".txt")
        test_selectors = working_selectors(tagfile)

        print("[%d/%d] Testing %s" %(idx, len(testfiles), testmod))
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

        if p.returncode == 0 and not os.path.exists(tagfile):
            # if we're re-tagging a test without tags, all passed
            with open(tagfile, "w") as f:
                pass
        else:
            passing_tests = []
            for m in re_success.findall(p.stdout):
                passing_tests.append(m)
            for m in re_success.findall(p.stderr):
                passing_tests.append(m)
            with open(tagfile, "w") as f:
                for passing_test in passing_tests:
                    f.write(passing_test)
                    f.write("\n")
            if not passing_tests:
                os.unlink(tagfile)
