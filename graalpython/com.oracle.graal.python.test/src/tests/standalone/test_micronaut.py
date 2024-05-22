# Copyright (c) 2023, 2024, Oracle and/or its affiliates. All rights reserved.
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
import unittest
import shutil
import difflib
import tempfile
import util

is_enabled = 'ENABLE_MICRONAUT_UNITTESTS' in os.environ and os.environ['ENABLE_MICRONAUT_UNITTESTS'] == "true"

def create_hello_app(hello_app_dir, target_dir):
    for root, dirs, files in os.walk(hello_app_dir):
        for file in files:

            if file.endswith("ContextFactory.j"):
                # will copy later, when needed
                continue

            source_file = os.path.join(root, file)
            if file.endswith(".j"):
                file = file[0:len(file)- 1] + "java"
            elif file.endswith(".p"):
                file = file[0:len(file)- 1] + "py"
            target_root = os.path.join(target_dir, root[len(hello_app_dir) + 1:])
            target_file = os.path.join(target_root, file)
            os.makedirs(os.path.dirname(target_file), exist_ok=True)
            shutil.copyfile(source_file, target_file)

def diff_texts(a, b, a_filename, b_filename):
    a = a.splitlines()
    b = b.splitlines()
    return difflib.unified_diff(a, b, a_filename, b_filename, "(generated)", "(expexted)", lineterm="")

def check_golden_file(file, golden):
    if not os.path.exists(golden):
        shutil.copyfile(file, golden)
        return

    found_diff = False
    with open(file) as f, open(golden) as fg:
        f_contents = f.read()
        fg_contets = fg.read()

        diff = diff_texts(f_contents, fg_contets, file, golden)
        for s in diff:
            found_diff = True
            print(s)

    return found_diff

class MicronautAppTest(unittest.TestCase):
    def setUpClass(self):
        if not is_enabled:
            return

        self.env = os.environ.copy()
        self.env["PYLAUNCHER_DEBUG"] = "1"

    @unittest.skipUnless(is_enabled, "ENABLE_MICRONAUT_UNITTESTS is not true")
    def test_hello_app(self):
        hello_app_dir = os.path.join(os.path.dirname(__file__), "micronaut/hello")
        with tempfile.TemporaryDirectory() as target_dir:
            create_hello_app(hello_app_dir, target_dir)

            pom_file = os.path.join(target_dir, "pom.xml")
            util.patch_pom_repositories(pom_file)

            mvn_cmd = util.get_mvn_wrapper(target_dir, self.env)

            # clean
            print("clean micronaut hello app ...")
            cmd = mvn_cmd + ["clean"]
            out, return_code = util.run_cmd(cmd, self.env, cwd=target_dir)
            util.check_ouput("BUILD SUCCESS", out)

            # build
            # java unittests are executed during the build
            print("build micronaut hello app ...")
            cmd = mvn_cmd + ["package"]
            out, return_code = util.run_cmd(cmd, self.env, cwd=target_dir)
            util.check_ouput("BUILD SUCCESS", out)
            util.check_ouput("=== CREATED REPLACE CONTEXT ===", out, False)

            # build and execute tests with a custom graalpy context factory
            source_file = os.path.join(hello_app_dir, "src/main/java/example/micronaut/ContextFactory.j")
            target_file = os.path.join(target_dir, "src/main/java/example/micronaut/ContextFactory.java")
            shutil.copyfile(source_file, target_file)

            print("build micronaut hello app with custom context factory ...")
            cmd = mvn_cmd + ["package"]
            out, return_code = util.run_cmd(cmd, self.env, cwd=target_dir)
            util.check_ouput("BUILD SUCCESS", out)
            util.check_ouput("=== CREATED REPLACE CONTEXT ===", out)

unittest.skip_deselected_test_functions(globals())
