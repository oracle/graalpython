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
import re
import shutil
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path

SETUP_PY_TEMPLATE = '''
from setuptools import setup, find_packages
setup(
    name={name!r},
    version={version!r},
    author='GraalPy developers',
    author_email='graalvm-users@oss.oracle.com',
    package_dir={{'': 'src'}},
    packages=find_packages(where='src'),
)
'''

SOURCE_CONTENT = '''
def test_fun():
    return 'Unpatched'
'''

PATCH_TEMPLATE = '''
--- a/patched_package/__init__.py	2023-01-04 12:36:48.112003339 +0100
+++ b/patched_package/__init__.py	2023-01-04 12:36:19.708004285 +0100
@@ -1,2 +1,2 @@
 def test_fun():
-    return 'Unpatched'
+    return '{}'
'''

if sys.implementation.name == "graalpy":

    class PipPatchingTest(unittest.TestCase):
        """
        Checks that our patched pip correctly patches or does not patch a package
        installed from source or binary distribution.
        """

        def setUpClass(self):
            self.venv_dir = Path(tempfile.mkdtemp()).resolve()
            subprocess.check_output([sys.executable, "-m", "venv", str(self.venv_dir)])
            self.venv_python = str(self.venv_dir / 'bin' / 'python')
            subprocess.check_output([self.venv_python, '-m', 'pip', 'install', 'wheel'])
            self.venv_template_dir = f'{self.venv_dir}.template'
            self.venv_dir.rename(self.venv_template_dir)
            self.build_dir = Path(tempfile.mkdtemp()).resolve()
            self.package_cache = {}

        def tearDownClass(self):
            shutil.rmtree(self.venv_template_dir, ignore_errors=True)
            shutil.rmtree(self.build_dir, ignore_errors=True)

        def setUp(self):
            shutil.copytree(self.venv_template_dir, self.venv_dir, symlinks=True)
            self.patch_dir = Path(tempfile.mkdtemp()).resolve()
            self.pip_env = os.environ.copy()
            self.pip_env['PIP_GRAALPY_PATCHES_URL'] = str(self.patch_dir)
            self.index_dir = Path(tempfile.mkdtemp()).resolve()

        def tearDown(self):
            shutil.rmtree(self.venv_dir, ignore_errors=True)
            shutil.rmtree(self.patch_dir, ignore_errors=True)
            shutil.rmtree(self.index_dir, ignore_errors=True)

        def prepare_config(self, name, rules):
            toml_lines = []
            for rule in rules:
                toml_lines.append(f'[[{name}.rules]]')
                for k, v in rule.items():
                    if not k.startswith('$'):
                        toml_lines.append(f'{k} = {v!r}')
                if patch := rule.get('patch'):
                    with open(self.patch_dir / patch, 'w') as f:
                        f.write(PATCH_TEMPLATE.format(rule.get('$patch-text', 'Patched')))
            with open(self.patch_dir / 'metadata.toml', 'w') as f:
                f.write('\n'.join(toml_lines))

        def build_package(self, name, version):
            cache_key = (name, version)
            if existing := self.package_cache.get(cache_key):
                return existing
            src_dir = self.build_dir / f'{name}-{version}'
            if not src_dir.exists():
                src_dir.mkdir()
                package_dir = src_dir / 'src' / 'patched_package'
                package_dir.mkdir(parents=True)
                with open(package_dir / '__init__.py', 'w') as f:
                    f.write(SOURCE_CONTENT)
                with open(src_dir / 'setup.py', 'w') as f:
                    f.write(SETUP_PY_TEMPLATE.format(name=name, version=version))
                subprocess.run([self.venv_python, 'setup.py', 'sdist'],
                               cwd=src_dir, check=True, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
                dist_dir = src_dir / 'dist'
                sdist = list(dist_dir.glob(f'*.tar.gz'))[0]
                subprocess.run([self.venv_python, 'setup.py', 'bdist_wheel'],
                               cwd=src_dir, check=True, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
                wheel = list(dist_dir.glob(f'*.whl'))[0]
                self.package_cache[cache_key] = {'sdist': sdist, 'wheel': wheel}
                return self.package_cache[cache_key]

        def add_package_to_index(self, name, version, dist_type):
            package = self.build_package(name, version)[dist_type]
            shutil.copy(package, self.index_dir)

        def run_venv_pip_install(self, package, extra_env=None):
            env = self.pip_env.copy()
            if extra_env:
                env.update(extra_env)
            out = subprocess.check_output([
                self.venv_python,
                '--experimental-options', '--python.EnableDebuggingBuiltins=true',
                '-m', 'pip', 'install', '--force-reinstall',
                '--find-links', self.index_dir, '--no-index', '--no-cache-dir',
                package],
                env=env, universal_newlines=True)
            assert 'Applying GraalPy patch failed for' not in out
            return re.findall(r'Successfully installed (\S+)', out)

        def run_test_fun(self):
            code = "import patched_package; print(patched_package.test_fun())"
            return subprocess.check_output([self.venv_python, '-c', code], universal_newlines=True).strip()

        def test_pip_launcher(self):
            subprocess.check_output([str(self.venv_dir / 'bin' / 'pip'), 'install', '--help'])

        def test_wheel_unpatched_version(self):
            self.add_package_to_index('foo', '1.0.0', 'wheel')
            self.prepare_config('foo', [{
                'patch': 'foo-1.1.0.patch',
                'version': '== 1.1.0',
                'subdir': 'src',
            }])
            self.run_venv_pip_install('foo')
            assert self.run_test_fun() == "Unpatched"

        def test_wheel_patched_version(self):
            self.add_package_to_index('foo', '1.1.0', 'wheel')
            self.prepare_config('foo', [{
                'patch': 'foo-1.1.0.patch',
                'version': '== 1.1.0',
                'subdir': 'src',
            }])
            self.run_venv_pip_install('foo')
            assert self.run_test_fun() == "Patched"

        def test_sdist_unpatched_version(self):
            self.add_package_to_index('foo', '1.0.0', 'sdist')
            self.prepare_config('foo', [{
                'patch': 'foo-1.1.0.patch',
                'version': '== 1.1.0',
                'subdir': 'src',
            }])
            self.run_venv_pip_install('foo')
            assert self.run_test_fun() == "Unpatched"

        def test_sdist_patched_version(self):
            self.add_package_to_index('foo', '1.1.0', 'sdist')
            self.prepare_config('foo', [{
                'patch': 'foo-1.1.0.patch',
                'version': '== 1.1.0',
                'subdir': 'src',
            }])
            self.run_venv_pip_install('foo')
            assert self.run_test_fun() == "Patched"

        def test_different_patch_wheel_sdist1(self):
            self.add_package_to_index('foo', '1.1.0', 'sdist')
            self.prepare_config('foo', [
                {
                    'patch': 'foo-1.1.0.patch',
                    'version': '== 1.1.0',
                    'dist-type': 'wheel',
                    '$patch-text': 'wheel',
                },
                {
                    'patch': 'foo-1.1.0.patch',
                    'version': '== 1.1.0',
                    'subdir': 'src',
                    'dist-type': 'sdist',
                    '$patch-text': 'sdist',
                },
                {
                    'patch': 'foo.patch',
                    'subdir': 'src',
                },
            ])
            self.run_venv_pip_install('foo')
            assert self.run_test_fun() == "sdist"

        def test_different_patch_wheel_sdist2(self):
            self.add_package_to_index('foo', '1.1.0', 'wheel')
            self.prepare_config('foo', [
                {
                    'patch': 'foo-1.1.0.patch',
                    'version': '== 1.1.0',
                    'dist-type': 'wheel',
                    '$patch-text': 'wheel',
                },
                {
                    'patch': 'foo-1.1.0.patch',
                    'version': '== 1.1.0',
                    'subdir': 'src',
                    'dist-type': 'sdist',
                    '$patch-text': 'sdist'
                },
                {
                    'patch': 'foo.patch',
                    'subdir': 'src',
                },
            ])
            self.run_venv_pip_install('foo')
            assert self.run_test_fun() == "sdist"

        def test_rule_matching1(self):
            self.add_package_to_index('foo', '1.1.0', 'wheel')
            self.prepare_config('foo', [
                {
                    'patch': 'foo-1.1.0.patch',
                    'version': '== 1.1.0',
                    '$patch-text': 'patch 1',
                },
                {
                    'patch': 'foo.patch',
                    '$patch-text': 'patch 2',
                }
            ])
            self.run_venv_pip_install('foo')
            assert self.run_test_fun() == "patch 1"

        def test_rule_matching2(self):
            self.add_package_to_index('foo', '1.0.0', 'wheel')
            self.prepare_config('foo', [
                {
                    'patch': 'foo-1.1.0.patch',
                    'version': '== 1.1.0',
                    '$patch-text': 'patch 1',
                },
                {
                    'patch': 'foo.patch',
                    '$patch-text': 'patch 2',
                }
            ])
            self.run_venv_pip_install('foo')
            assert self.run_test_fun() == "patch 2"

        def test_version_range_inside(self):
            self.add_package_to_index('foo', '1.1.0', 'wheel')
            self.prepare_config('foo', [{
                'patch': 'foo.patch',
                'version': '> 1.0.0',
            }])
            self.run_venv_pip_install('foo')
            assert self.run_test_fun() == "Patched"

        def test_version_range_outside(self):
            self.add_package_to_index('foo', '1.0.0', 'wheel')
            self.prepare_config('foo', [{
                'patch': 'foo.patch',
                'version': '> 1.0.0',
            }])
            self.run_venv_pip_install('foo')
            assert self.run_test_fun() == "Unpatched"

        def test_version_range_multiple(self):
            self.add_package_to_index('foo', '1.1.0', 'wheel')
            self.prepare_config('foo', [{
                'patch': 'foo.patch',
                'version': '> 1.0.0, < 2.0.0',
            }])
            self.run_venv_pip_install('foo')
            assert self.run_test_fun() == "Patched"

        def test_version_selection_default(self):
            self.add_package_to_index('foo', '1.0.0', 'wheel')
            self.add_package_to_index('foo', '1.1.0', 'wheel')
            self.add_package_to_index('foo', '1.2.0', 'wheel')
            self.prepare_config('foo', [{
                'patch': 'foo.patch',
                'version': '<= 1.1.0',
            }])
            assert self.run_venv_pip_install('foo') == ['foo-1.1.0']
            assert self.run_venv_pip_install('foo==1.2') == ['foo-1.2.0']

        def test_version_selection_explicit_demoted(self):
            self.add_package_to_index('foo', '1.0.0', 'wheel')
            self.add_package_to_index('foo', '1.1.0', 'wheel')
            self.prepare_config('foo', [{
                'patch': 'foo.patch',
                'version': '< 1.1.0',
                'install-priority': 0,
            }])
            assert self.run_venv_pip_install('foo') == ['foo-1.1.0']
            assert self.run_test_fun() == "Unpatched"
            assert self.run_venv_pip_install('foo==1.0.0') == ['foo-1.0.0']
            assert self.run_test_fun() == "Patched"

        def test_version_selection_explicit_promoted(self):
            self.add_package_to_index('foo', '1.0.0', 'wheel')
            self.add_package_to_index('foo', '1.1.0', 'wheel')
            self.prepare_config('foo', [
                {
                    'patch': 'foo.patch',
                    'version': '< 1.1.0',
                    'install-priority': 2,
                    '$patch-text': 'old patch',
                },
                {
                    'patch': 'foo-1.1.0.patch',
                    'version': '== 1.1.0',
                    '$patch-text': 'new patch',
                },
            ])
            assert self.run_venv_pip_install('foo') == ['foo-1.0.0']
            assert self.run_test_fun() == "old patch"
            assert self.run_venv_pip_install('foo>1.0.0') == ['foo-1.1.0']
            assert self.run_test_fun() == "new patch"

        def test_version_selection_no_patch(self):
            self.add_package_to_index('foo', '1.0.0', 'wheel')
            self.add_package_to_index('foo', '1.1.0', 'wheel')
            self.prepare_config('foo', [{
                'version': '== 1.0.0',
            }])
            assert self.run_venv_pip_install('foo') == ['foo-1.0.0']
            assert self.run_test_fun() == "Unpatched"

        def test_name_with_underscores(self):
            self.add_package_to_index('package_with_underscores', '1.0.0', 'wheel')
            self.add_package_to_index('package_with_underscores', '2.0.0', 'wheel')
            self.prepare_config('package_with_underscores', [{
                'version': '== 1.0.0',
                'patch': 'package_with_underscores.patch',
            }])
            assert self.run_venv_pip_install('package_with_underscores') == ['package_with_underscores-1.0.0']
            assert self.run_test_fun() == "Patched"

        def test_name_with_dashes(self):
            self.add_package_to_index('package-with-dashes', '1.0.0', 'wheel')
            self.add_package_to_index('package-with-dashes', '2.0.0', 'wheel')
            self.prepare_config('package-with-dashes', [{
                'version': '== 1.0.0',
                'patch': 'package-with-dashes.patch',
            }])
            assert self.run_venv_pip_install('package-with-dashes') == ['package-with-dashes-1.0.0']
            assert self.run_test_fun() == "Patched"
