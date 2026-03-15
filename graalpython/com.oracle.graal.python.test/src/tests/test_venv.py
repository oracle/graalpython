# Copyright (c) 2019, 2026, Oracle and/or its affiliates. All rights reserved.
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
import shutil
import subprocess
import sys
import tempfile
import textwrap
import unittest

BINDIR = 'bin' if sys.platform != 'win32' else 'Scripts'
EXESUF = '' if sys.platform != 'win32' else '.exe'


class VenvTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.env_dir = os.path.realpath(tempfile.mkdtemp())
        cls.env_dir2 = os.path.realpath(tempfile.mkdtemp())

    @classmethod
    def tearDownClass(cls):
        shutil.rmtree(cls.env_dir)
        shutil.rmtree(cls.env_dir2)

    def test_venv_launcher(self):
        if sys.platform != "win32":
            return
        import venv
        import struct
        with tempfile.TemporaryDirectory() as d:
            tmpfile = os.path.join(d, "venvlauncher.exe")
            launcher_command = f'"{os.path.realpath(sys.executable)}" -S'
            shutil.copy(os.path.join(venv.__path__[0], "scripts", "nt", "graalpy.exe"), tmpfile)
            with open(tmpfile, "ab") as f:
                sz = f.write(launcher_command.encode("utf-16le"))
                assert f.write(struct.pack("@I", sz)) == 4
            try:
                out = subprocess.check_output([tmpfile, "-c", """if True:
                import sys, os
                x = os
                print("Hello", sys.executable)
                print("Base", sys._base_executable)
                print("Original", __graalpython__.venvlauncher_command)
                """], env={"PYLAUNCHER_DEBUG": "1"}, text=True)
            except subprocess.CalledProcessError as err:
                out = err.output.decode(errors="replace") if err.output else ""
            print("out=", out, sep="\n")
            assert f"Hello {tmpfile}" in out, out
            assert f"Base {os.path.realpath(sys.executable)}" in out, out
            assert f'Original {launcher_command}' in out, out

    def test_nested_windows_venv_preserves_base_executable(self):
        if sys.platform != "win32" or sys.implementation.name != "graalpy":
            return
        expected_base = os.path.realpath(getattr(sys, "_base_executable", sys.executable))
        with tempfile.TemporaryDirectory() as outer_dir, tempfile.TemporaryDirectory() as inner_root:
            inner_dir = os.path.join(inner_root, "inner")
            extra_args = [
                f'--vm.Dpython.EnableBytecodeDSLInterpreter={repr(__graalpython__.is_bytecode_dsl_interpreter).lower()}'
            ]
            subprocess.check_output([sys.executable] + extra_args + ["-m", "venv", outer_dir, "--without-pip"], stderr=subprocess.STDOUT)
            outer_python = os.path.join(outer_dir, BINDIR, f"python{EXESUF}")
            out = subprocess.check_output([
                outer_python,
                "-c",
                textwrap.dedent(f"""
                    import os
                    import sys
                    import venv

                    inner_dir = {inner_dir!r}
                    venv.EnvBuilder(with_pip=False).create(inner_dir)
                    print("OUTER_BASE", os.path.realpath(sys._base_executable))
                    with open(os.path.join(inner_dir, "pyvenv.cfg"), encoding="utf-8") as cfg:
                        print(cfg.read())
                """)
            ], text=True)
            assert f"OUTER_BASE {expected_base}" in out, out
            assert f"base-executable = {expected_base}" in out, out

    def test_create_and_use_basic_venv(self):
        run = None
        run_output = ''
        try:
            extra_args = []
            if sys.implementation.name == "graalpy":
                extra_args = [f'--vm.Dpython.EnableBytecodeDSLInterpreter={repr(__graalpython__.is_bytecode_dsl_interpreter).lower()}']
            subprocess.check_output([sys.executable] + extra_args + ["-m", "venv", self.env_dir, "--without-pip"], stderr=subprocess.STDOUT)
            run = subprocess.getoutput(f"{self.env_dir}/{BINDIR}/python{EXESUF} -m site")
        except subprocess.CalledProcessError as err:
            if err.output:
                run_output = err.output.decode(errors="replace")
        assert run, run_output
        assert "ENABLE_USER_SITE: False" in run, run
        if sys.platform != 'win32':
            assert self.env_dir in run, run

    def test_create_and_use_venv_with_pip(self):
        run = None
        msg = ''
        try:
            extra_args = []
            if sys.implementation.name == "graalpy":
                extra_args = [f'--vm.Dpython.EnableBytecodeDSLInterpreter={repr(__graalpython__.is_bytecode_dsl_interpreter).lower()}']
            subprocess.check_output([sys.executable] + extra_args + ["-m", "venv", self.env_dir2], stderr=subprocess.STDOUT)
            run = subprocess.getoutput(f"{self.env_dir2}/{BINDIR}/python{EXESUF} -m pip list")
        except subprocess.CalledProcessError as err:
            if err.output:
                run_output = err.output.decode(errors="replace")
        assert run, run_output
        assert "pip" in run, run
