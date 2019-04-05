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

import os
import subprocess
import sys
import test


TAGS_FILE = os.path.join(os.path.dirname(__file__), "working_unittests.txt")


def working_tests():
    working_tests = []

    def parse_line(iterator, line):
        line = line.strip()
        if line.endswith(":"):
            test_selectors = []
            for testline in iterator:
                if testline.startswith(" "):
                    test_selectors.append(testline.strip())
                else:
                    parse_line(iterator, testline)
            working_tests.append((line[:-1], test_selectors))
        elif line:
            working_tests.append(line)

    with open(TAGS_FILE) as f:
        fiter = iter(f)
        for line in fiter:
            parse_line(fiter, line)

    return working_tests


for working_test in working_tests():
    def make_test_func(working_test):
        def fun():
            if isinstance(working_test, str):
                subprocess.check_call([sys.executable, "-m", working_test])
            else:
                cmd = [sys.executable, "-m", "unittest"]
                for testpattern in working_test[1]:
                    cmd.extend(["-k", testpattern])
                testmod = working_test[0].rpartition(".")[2]
                cmd.append(os.path.join(os.path.dirname(test.__file__), "%s.py" % testmod))
                subprocess.check_call(cmd)

        fun.__name__ = working_test if isinstance(working_test, str) else working_test[0]
        return fun

    test_f = make_test_func(working_test)
    globals()[test_f.__name__] = test_f
    del test_f


if __name__ == "__main__":
    # find working tests
    import glob
    import re

    executable = sys.executable.split(" ") # HACK: our sys.executable on Java is a cmdline
    re_success = re.compile("^(test[^ ]+).* ... ok")
    with open(TAGS_FILE, "w") as f:
        for testfile in glob.glob(os.path.join(os.path.dirname(test.__file__), "test_*.py")):
            testmod = "test.%s" % os.path.splitext(os.path.basename(testfile))[0]
            print("Testing", testmod)
            try:
                subprocess.check_call(["/usr/bin/timeout", "100"] + executable + ["-m", testmod, "-f"])
                f.write(testmod)
                f.write("\n")
            except BaseException as e:
                print(e)
                p = subprocess.run(["/usr/bin/timeout", "100", sys.executable, "-m", testmod, "-v"], stdout=subprocess.PIPE, stderr=subprocess.PIPE, text=True, check=False)
                print("***")
                print(p.stdout)
                print("***")
                print(p.stderr)
                print("***")
                passing_tests = []
                for m in re_success.findall(p.stdout):
                    passing_tests.append(m)
                for m in re_success.findall(p.stderr):
                    passing_tests.append(m)
                if passing_tests:
                    f.write(testmod)
                    f.write(":\n")
                    for passing_test in passing_tests:
                        f.write("  ")
                        f.write(passing_test)
                        f.write("\n")
