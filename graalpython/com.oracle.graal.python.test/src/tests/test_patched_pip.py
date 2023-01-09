# Copyright (c) 2023, 2023, Oracle and/or its affiliates. All rights reserved.
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

import sys

if sys.implementation.name == "graalpy":

    import os
    import shutil
    import subprocess
    import tempfile

    PKG_SRC_DIR = os.path.join(os.path.abspath(os.path.dirname(__file__)), 'patched_package')
    DIST_DIR = os.path.join(PKG_SRC_DIR, 'dist')
    BDIST_1_0_0 = os.path.join(DIST_DIR, 'patched_package-1.0.0-py3-none-any.whl')
    BDIST_1_1_0 = os.path.join(DIST_DIR, 'patched_package-1.1.0-py3-none-any.whl')
    SDIST_1_0_0 = os.path.join(DIST_DIR, 'patched_package-1.0.0.tar.gz')
    SDIST_1_1_0 = os.path.join(DIST_DIR, 'patched_package-1.1.0.tar.gz')
    PATCHES_DIRS = os.path.join(PKG_SRC_DIR, 'patches')


    class PipPatchingTest():
        """
        Checks that our patched pip correctly patches or does not patch a package
        installed from source or binary distribution. We use package "patched_package"
        located next to this test file. The distributions of that package must
        be created manually and are checked into git.
        """
        def setUpClass(self):
            self.env_dir = os.path.realpath(tempfile.mkdtemp())
            subprocess.check_output([sys.executable, "-m", "venv", self.env_dir])
            self.venv_python = os.path.join(self.env_dir, 'bin', 'python')
            self.pip_env = os.environ.copy()
            self.pip_env['PIPLOADER_PATCHES_BASE_DIRS'] = PATCHES_DIRS

        def tearDownClass(self):
            shutil.rmtree(self.env_dir)

        def run_venv_pip_install(self, package, extra_env=None):
            env = self.pip_env.copy()
            if extra_env:
                env.update(extra_env)
            subprocess.check_output([
                self.venv_python,
                '--experimental-options', '--python.EnableDebuggingBuiltins=true',
                '-m', 'pip', 'install', '--force-reinstall', package],
                env=env)

        def run_test_fun(self):
            code = "import patched_package; print(patched_package.test_fun())"
            out = subprocess.getoutput(f"{self.venv_python} -c '{code}'").strip()
            return out

        def test_pip_launcher(self):
            subprocess.check_output([
                os.path.join(self.env_dir, 'bin', 'pip'),
                'install', '--help'])

        def test_wheel_unpatched_version(self):
            self.run_venv_pip_install(BDIST_1_0_0)
            assert self.run_test_fun() == "Unpatched"

        def test_wheel_patched_version(self):
            self.run_venv_pip_install(BDIST_1_1_0)
            assert self.run_test_fun() == "Patched"

        def test_sdist_unpatched_version(self):
            # Note: PKG_VERSION is used by setup.py
            self.run_venv_pip_install(SDIST_1_0_0, extra_env={'PKG_VERSION': '1.0.0'})
            assert self.run_test_fun() == "Unpatched"

        def test_sdist_patched_version(self):
            self.run_venv_pip_install(SDIST_1_1_0, extra_env={'PKG_VERSION': '1.1.0'})
            assert self.run_test_fun() == "Patched"
