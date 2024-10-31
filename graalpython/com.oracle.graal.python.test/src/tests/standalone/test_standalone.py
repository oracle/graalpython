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
import tempfile
import unittest
import util

is_enabled = 'ENABLE_STANDALONE_UNITTESTS' in os.environ and os.environ['ENABLE_STANDALONE_UNITTESTS'] == "true"

@unittest.skipUnless(is_enabled, "ENABLE_STANDALONE_UNITTESTS is not true")
def test_native_executable_one_file():
    graalpy = util.get_gp()
    if graalpy is None:
        return
    env = os.environ.copy()
    env["MVN_GRAALPY_VERSION"] = util.get_graalvm_version()

    with tempfile.TemporaryDirectory() as tmpdir:

        source_file = os.path.join(tmpdir, "hello.py")
        with open(source_file, 'w') as f:
            f.write("import sys\n")
            f.write("print('hello world, argv[1:]:', sys.argv[1:])")

        target_file = os.path.join(tmpdir, "hello")
        cmd = [graalpy, "-m", "standalone", "--verbose", "native", "-ce", "-m", source_file, "-o", target_file]

        out, return_code = util.run_cmd(cmd, env, print_out=True)
        util.check_ouput("Bundling Python resources into", out)
        util.check_ouput("Finished generating 'hello' in", out)

        cmd = [target_file, "arg1", "arg2"]
        out, return_code = util.run_cmd(cmd, env)
        util.check_ouput("hello world, argv[1:]: " + str(cmd[1:]), out)

@unittest.skipUnless(is_enabled, "ENABLE_STANDALONE_UNITTESTS is not true")
def test_native_executable_venv_and_one_file():
    graalpy = util.get_gp()
    if graalpy is None:
        return
    env = os.environ.copy()
    env["MVN_GRAALPY_VERSION"] = util.get_graalvm_version()

    with tempfile.TemporaryDirectory() as target_dir:
        source_file = os.path.join(target_dir, "hello.py")
        with open(source_file, 'w') as f:
            f.write("from termcolor import colored, cprint\n")
            f.write("colored_text = colored('hello standalone world', 'red', attrs=['reverse', 'blink'])\n")
            f.write("print(colored_text)\n")
            f.write("import ujson\n")
            f.write('d = ujson.loads("""{"key": "value"}""")\n')
            f.write("print('key=' + d['key'])\n")

        venv_dir = os.path.join(target_dir, "venv")
        cmd = [graalpy, "-m", "venv", venv_dir]
        out, return_code = util.run_cmd(cmd, env)

        venv_python = os.path.join(venv_dir, "Scripts", "python.exe") if os.name == "nt" else os.path.join(venv_dir, "bin", "python")
        cmd = [venv_python, "-m", "pip", "install", "termcolor", "ujson"]
        out, return_code = util.run_cmd(cmd, env)

        target_file = os.path.join(target_dir, "hello")
        cmd = [graalpy, "-m", "standalone", "--verbose", "native", "-ce", "-Os", "-m", source_file, "--venv", venv_dir, "-o", target_file]
        out, return_code = util.run_cmd(cmd, env)
        util.check_ouput("Bundling Python resources into", out)
        util.check_ouput("Finished generating 'hello' in", out)

        cmd = [target_file]
        out, return_code = util.run_cmd(cmd, env)
        util.check_ouput("hello standalone world", out)
        util.check_ouput("key=value", out)

@unittest.skipUnless(is_enabled, "ENABLE_STANDALONE_UNITTESTS is not true")
def test_native_executable_module():
    graalpy = util.get_gp()
    if graalpy is None:
        return
    env = os.environ.copy()
    env["MVN_GRAALPY_VERSION"] = util.get_graalvm_version()

    with tempfile.TemporaryDirectory() as tmp_dir:

        module_dir = os.path.join(tmp_dir, "hello_app")
        os.makedirs(module_dir, exist_ok=True)

        source_file = os.path.join(module_dir, "hello.py")
        with open(source_file, 'w') as f:
            f.write("def print_hello():\n")
            f.write("    print('hello standalone world')\n")

        source_file = os.path.join(module_dir, "__main__.py")
        with open(source_file, 'w') as f:
            f.write("import hello\n")
            f.write("hello.print_hello()\n")

        target_file = os.path.join(tmp_dir, "hello")
        cmd = [graalpy, "-m", "standalone", "--verbose", "native", "-ce", "-Os", "-m", module_dir, "-o", target_file]
        out, return_code = util.run_cmd(cmd, env)
        util.check_ouput("Bundling Python resources into", out)
        util.check_ouput("Finished generating 'hello' in", out)

        cmd = [target_file]
        out, return_code = util.run_cmd(cmd, env)
        util.check_ouput("hello standalone world", out)

unittest.skip_deselected_test_functions(globals())
