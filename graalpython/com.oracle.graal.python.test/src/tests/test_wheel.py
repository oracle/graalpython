# Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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
import shlex
import shutil
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path


class TestWheelBuildAndRun(unittest.TestCase):
    def test_build_install_and_run(self):
        # Build a C library and a wheel that depends on it. Then run it through repair_wheel to vendor the library in and verify that it can be imported
        orig_root = (Path(__file__).parent / "example_wheel").resolve()
        orig_testwheel = orig_root / "testwheel"
        orig_answerlib = orig_root / "answerlib"

        with tempfile.TemporaryDirectory(prefix="testwheel_tmp_") as tmpdir:
            tmpdir = Path(tmpdir)
            tmp_testwheel = tmpdir / "testwheel"
            tmp_answerlib = tmpdir / "answerlib"
            venv_dir = tmpdir / "venv"
            wheel_dir = tmpdir / "wheels"
            repaired_dir = tmpdir / "repaired"

            shutil.copytree(orig_testwheel, tmp_testwheel)
            shutil.copytree(orig_answerlib, tmp_answerlib)

            answerlib_build = tmp_answerlib / "build"
            if answerlib_build.exists():
                shutil.rmtree(answerlib_build)
            answerlib_build.mkdir(parents=True, exist_ok=True)

            cmd = ["cmake", "-DCMAKE_BUILD_TYPE=Release", ".."]
            print("Running:", shlex.join(cmd))
            subprocess.check_call(cmd, cwd=str(answerlib_build))
            cmd = ["cmake", "--build", ".", "--config", "Release"]
            print("Running:", shlex.join(cmd))
            subprocess.check_call(cmd, cwd=str(answerlib_build))

            cmd = [sys.executable, "-m", "venv", str(venv_dir)]
            print("Running:", shlex.join(cmd))
            subprocess.check_call(cmd)
            if sys.platform != 'win32':
                python = venv_dir / "bin" / "python"
            else:
                python = venv_dir / "Scripts" / "python.exe"

            cmd = [str(python), "-m", "pip", "install", "build"]
            print("Running:", shlex.join(cmd))
            subprocess.check_call(cmd)

            wheelhouse_dir = repaired_dir / "wheelhouse"
            wheelhouse_dir.mkdir(parents=True, exist_ok=True)

            wheel_dir.mkdir(parents=True, exist_ok=True)
            cmd = [str(python), "-m", "build", "--wheel", "--outdir", str(wheel_dir)]
            print("Running:", shlex.join(cmd))
            subprocess.check_call(cmd, cwd=str(tmp_testwheel))

            built_wheels = list(wheel_dir.glob("testwheel-*.whl"))
            self.assertTrue(built_wheels, "Wheel was not built!")

            repair_env = os.environ.copy()
            if sys.platform.startswith("linux"):
                repair_env["LD_LIBRARY_PATH"] = str(answerlib_build)
            elif sys.platform == "darwin":
                repair_env["DYLD_LIBRARY_PATH"] = str(answerlib_build)
            elif sys.platform == "win32":
                repair_env["PATH"] = str(answerlib_build) + os.pathsep + repair_env.get("PATH", "")
            cmd = [
                str(python),
                str((Path(
                    __file__).parent.parent.parent.parent.parent / "scripts" / "wheelbuilder" / "repair_wheels.py").resolve()),
                "--wheelhouse",
                str(wheelhouse_dir)
            ]
            print("Running:", shlex.join(cmd))
            subprocess.check_call(cmd, cwd=str(wheel_dir), env=repair_env)

            repaired_wheels = list(wheelhouse_dir.glob("testwheel-*.whl"))
            self.assertTrue(repaired_wheels, "Repair tool did not produce repaired wheel")
            repaired_wheel = str(repaired_wheels[0])

            cmd = [str(python), "-m", "pip", "install", "--force-reinstall", repaired_wheel]
            print("Running:", shlex.join(cmd))
            subprocess.check_call(cmd)

            code = (
                "import testwheel.answer; "
                "print(testwheel.answer.get_answer())"
            )
            cmd = [str(python), "-c", code]
            print("Running:", shlex.join(cmd))
            result = subprocess.check_output(
                cmd, universal_newlines=True
            ).strip()

            self.assertEqual(result, "42")


if __name__ == "__main__":
    unittest.main()
