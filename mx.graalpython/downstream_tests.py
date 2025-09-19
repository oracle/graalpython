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

import argparse
import os
import shlex
import shutil
import subprocess
from pathlib import Path

DIR = Path(__file__).parent.parent
DOWNSTREAM_TESTS = {}


def run(*args, check=True, **kwargs):
    return subprocess.run(*args, check=check, **kwargs)


def run_in_venv(venv, cmd, **kwargs):
    return run(['sh', '-c', f". {venv}/bin/activate && {shlex.join(cmd)}"], **kwargs)


def downstream_test(name):
    def decorator(fn):
        DOWNSTREAM_TESTS[name] = fn
        return fn

    return decorator


@downstream_test('hpy')
def downstream_test_hpy(graalpy, testdir=None, args=None, env=None, check=True, timeout=None):
    if not testdir:
        testdir = Path('upstream-tests').absolute()
        shutil.rmtree(testdir, ignore_errors=True)
        testdir.mkdir(exist_ok=True)
    hpy_root = DIR / "graalpython" / "hpy"
    shutil.copytree(hpy_root, testdir / "hpy")
    hpy_root = testdir / "hpy"
    hpy_test_root = hpy_root / "test"
    venv = testdir / 'hpy_venv'
    run([graalpy, "-m", "venv", str(venv)])
    if graalpy.endswith('.sh'):
        # Workaround jacoco launcher creating broken venv
        venv_launcher_path = venv / "bin" / "graalpy.sh"
        launcher = venv_launcher_path.read_text()
        venv_launcher_path.unlink()
        launcher = launcher.replace(
            '--python.Executable=',
            f'--python.Executable={venv_launcher_path.absolute()} --python.BaseExecutable=',
        )
        venv_launcher_path.write_text(launcher)
        venv_launcher_path.chmod(0o755)
        run([str(venv_launcher_path), '-m', 'ensurepip', '--default-pip'])
    run_in_venv(venv, ["pip", "install", "pytest", "pytest-xdist", "pytest-rerunfailures!=16.0", "filelock"])
    env = env or os.environ.copy()
    env["SETUPTOOLS_SCM_PRETEND_VERSION"] = "0.9.0"
    run_in_venv(venv, ["pip", "install", "-e", "."], cwd=str(hpy_root), env=env)
    parallelism = str(min(os.cpu_count(), int(os.cpu_count() / 4)))
    args = args or []
    args = [
        "python",
        "--vm.ea",
        "--experimental-options=true",
        "--python.EnableDebuggingBuiltins",
        *args,
        "-m", "pytest",
        "-v",
        # for those cases where testing invalid handles corrupts the process so
        # much that we crash - we don't recover gracefully in some cases :(
        "--reruns", "3",
        "-n", parallelism,
        str(hpy_test_root),
        # test_distutils is just slow and testing the build infrastructure
        "-k", "not test_distutils"
    ]
    return run_in_venv(venv, args, env=env, cwd=str(hpy_root), check=check, timeout=timeout)


@downstream_test('pybind11')
def downstream_test_pybind11(graalpy, testdir):
    run(['git', 'clone', 'https://github.com/pybind/pybind11.git'], cwd=testdir)
    src = testdir / 'pybind11'
    venv = src / 'venv'
    run([graalpy, '-m', 'venv', str(venv)])
    run_in_venv(venv, ['pip', 'install', 'pytest'])
    run_in_venv(venv, ['cmake', '-S', '.', '-B', 'build', '-DPYBIND11_WERROR=ON'], cwd=src)
    # GitHub actions tend to OOM here
    parallel_arg = ['--parallel'] if "GITHUB_ACTIONS" not in os.environ else []
    run_in_venv(venv, ['cmake', '--build', 'build', *parallel_arg], cwd=src)
    env = os.environ.copy()
    env['PYTHONPATH'] = 'build/tests'
    run_in_venv(venv, ['pytest', '-v', '--tb=short', 'tests'], cwd=src, env=env)


@downstream_test('virtualenv')
def downstream_test_virtualenv(graalpy, testdir):
    run(['git', 'clone', 'https://github.com/pypa/virtualenv.git', '-b', 'main'], cwd=testdir)
    src = testdir / 'virtualenv'
    venv = src / 'venv'
    run([graalpy, '-m', 'venv', str(venv)])
    env = os.environ.copy()
    env.pop('VIRTUAL_ENV_DISABLE_PROMPT', None)
    env['CI_RUN'] = '1'
    # Need to avoid pulling in graalpy seeder
    env['PIP_GRAALPY_DISABLE_PATCHING'] = '1'
    run_in_venv(venv, ['pip', 'install', f'{src}[test]'], env=env)
    # Don't activate the venv, it interferes with the test
    run([
        str(venv / 'bin' / 'pytest'), '-v', '--tb=short', 'tests',
        '-k', 'not fish and not csh and not nushell and not powershell',
    ], cwd=src, env=env)


@downstream_test('pyo3')
def downstream_test_pyo3(graalpy, testdir):
    run(['git', 'clone', 'https://github.com/PyO3/pyo3.git', '-b', 'main'], cwd=testdir)
    src = testdir / 'pyo3'
    venv = src / 'venv'
    run([graalpy, '-m', 'venv', str(venv)])
    run_in_venv(venv, ['pip', 'install', 'nox'])
    run_in_venv(venv, ['nox', '-s', 'test-py'], cwd=src)


@downstream_test('pydantic-core')
def downstream_test_pydantic_core(graalpy, testdir):
    run(['git', 'clone', 'https://github.com/pydantic/pydantic-core.git', '-b', 'main'], cwd=testdir)
    src = testdir / 'pydantic-core'
    run(['uv', 'sync', '--python', graalpy, '--group', 'testing'], cwd=src)
    run(['uv', 'pip', 'install', '-e', '.'], cwd=src)
    run(['uv', 'run', 'pytest', '-v', '--tb=short'], cwd=src)


@downstream_test('jiter')
def downstream_test_jiter(graalpy, testdir):
    run(['git', 'clone', 'https://github.com/pydantic/jiter.git', '-b', 'main'], cwd=testdir)
    src = testdir / 'jiter'
    venv = src / 'venv'
    run([graalpy, '-m', 'venv', str(venv)])
    run_in_venv(venv, ['pip', 'install', '-r', 'crates/jiter-python/tests/requirements.txt'], cwd=src)
    run_in_venv(venv, ['pip', 'install', '-e', 'crates/jiter-python',
                       '--config-settings=build-args=--profile dev'], cwd=src)
    run_in_venv(venv, ['pytest', '-v', '--tb=short', 'crates/jiter-python/tests'], cwd=src)
    run_in_venv(venv, ['python', 'crates/jiter-python/bench.py', 'jiter', 'jiter-cache', '--fast'], cwd=src)


def run_downstream_test(python, project):
    testdir = Path('upstream-tests').absolute()
    shutil.rmtree(testdir, ignore_errors=True)
    testdir.mkdir(exist_ok=True)
    python = os.path.abspath(python)
    DOWNSTREAM_TESTS[project](python, testdir)


def main():
    parser = argparse.ArgumentParser("Runs important upstream packages tests using their main branch")
    parser.add_argument("python")
    parser.add_argument("project", choices=sorted(DOWNSTREAM_TESTS))
    args = parser.parse_args()
    run_downstream_test(args.python, args.project)


if __name__ == '__main__':
    main()
