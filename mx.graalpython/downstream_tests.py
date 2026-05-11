# Copyright (c) 2025, 2026, Oracle and/or its affiliates. All rights reserved.
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
import re
import shlex
import shutil
import subprocess
from pathlib import Path

DIR = Path(__file__).parent.parent
DOWNSTREAM_TESTS = {}
CI = os.environ.get('CI', '').lower() in ('1', 'true')


def run(*args, check=True, **kwargs):
    return subprocess.run(*args, check=check, **kwargs)


def run_in_venv(venv, cmd, **kwargs):
    return run(['sh', '-c', f". {venv}/bin/activate && {shlex.join(cmd)}"], **kwargs)


def replace_in_file(path: Path, pattern, replacement):
    path.write_text(re.sub(pattern, replacement, path.read_text()))


def downstream_test(name):
    def decorator(fn):
        DOWNSTREAM_TESTS[name] = fn
        return fn

    return decorator


@downstream_test('hpy')
def downstream_test_hpy(graalpy, testdir=None, args=None, env=None, check=True, timeout=None):
    if not testdir:
        testdir = Path('downstream-tests').absolute()
        shutil.rmtree(testdir, ignore_errors=True)
        testdir.mkdir(exist_ok=True)
    hpy_root = DIR / "graalpython" / "hpy"
    shutil.copytree(hpy_root, testdir / "hpy")
    hpy_root = testdir / "hpy"
    hpy_test_root = hpy_root / "test"
    venv = testdir / 'hpy_venv'
    run([graalpy, "-m", "venv", str(venv)])
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
    run(['git', 'clone', 'https://github.com/pybind/pybind11.git', '--depth', '1'], cwd=testdir)
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
    run_in_venv(venv, ['pytest', '-v', '--tb=short', '-o', 'xfail_strict=False', 'tests'], cwd=src, env=env)


@downstream_test('virtualenv')
def downstream_test_virtualenv(graalpy, testdir):
    run(['git', 'clone', 'https://github.com/pypa/virtualenv.git', '-b', 'main', '--depth', '1'], cwd=testdir)
    src = testdir / 'virtualenv'
    venv = src / 'venv'
    run([graalpy, '-m', 'venv', str(venv)])
    env = os.environ.copy()
    env.pop('VIRTUAL_ENV_DISABLE_PROMPT', None)
    env['CI_RUN'] = '1'
    # Need to avoid pulling in graalpy seeder
    env['PIP_GRAALPY_DISABLE_PATCHING'] = '1'
    # Update to pip that supports --group
    run_in_venv(venv, ['pip', 'install', 'pip>=26'], env=env)
    run_in_venv(venv, ['pip', 'install', '--group=test', '.'], env=env, cwd=src)
    # Allow newer CPython for building zipapp, we don't have 3.11 in the CI anymore
    replace_in_file(
        src / 'tests/integration/test_zipapp.py',
        r'version in range\(11, 6, -1\)',
        'version in range(14, 6, -1)',
    )
    # Don't activate the venv, it interferes with the test
    run([
        str(venv / 'bin' / 'pytest'), '-v', '--tb=short', 'tests',
        '-k', 'not fish and not csh and not nushell and not powershell',
    ], cwd=src, env=env)


@downstream_test('pyo3')
def downstream_test_pyo3(graalpy, testdir):
    run(['git', 'clone', 'https://github.com/PyO3/pyo3.git', '-b', 'main', '--depth', '1'], cwd=testdir)
    src = testdir / 'pyo3'
    venv = src / 'venv'
    run([graalpy, '-m', 'venv', str(venv)])
    run_in_venv(venv, ['python', '-m', 'pip', 'install', '--upgrade', 'pip', 'nox[uv]'])
    env = os.environ.copy()
    env['NOX_DEFAULT_VENV_BACKEND'] = 'uv'
    run_in_venv(venv, ['nox', '-s', 'test-py'], cwd=src, env=env)


@downstream_test('pydantic-core')
def downstream_test_pydantic_core(graalpy, testdir):
    run(['git', 'clone', 'https://github.com/pydantic/pydantic.git', '-b', 'main', '--depth', '1'], cwd=testdir)
    repo = testdir / 'pydantic'
    env = os.environ.copy()
    env['UV_PYTHON_DOWNLOADS'] = 'never'
    env['UV_PYTHON'] = str(graalpy)
    # Needed for rpds-py dependency
    env['UNSAFE_PYO3_SKIP_VERSION_CHECK'] = '1'
    # Commands taken from upstream. The upstream has some TODOs so we'll likely need to sync this again soon
    run(
        ['uv', 'sync', '--directory', 'pydantic-core', '--group', 'testing-extra', '--no-install-package', 'pydantic-core'],
        cwd=repo,
        env=env,
    )
    run(
        [
            'uv', 'sync', '--group', 'testing-extra', '--no-install-package', 'pydantic-core',
            '--no-install-package', 'pytest-memray', '--no-install-package', 'memray',
            '--no-install-package', 'pytest-codspeed', '--no-install-package', 'cffi', '--inexact',
            # GraalPy change: greenlet crashes on import
            '--no-install-package', 'greenlet',
        ],
        cwd=repo,
        env=env,
    )
    del env['UV_PYTHON']
    run(
        ['uv', 'pip', 'install', './pydantic-core', '--no-deps', '--force-reinstall'],
        cwd=repo,
        env=env,
    )
    run(
        ['uv', 'run', '--no-sync', 'pytest', 'tests/pydantic_core', '--ignore=tests/pydantic_core/test_docstrings.py'],
        cwd=repo,
        env=env,
    )


@downstream_test('jiter')
def downstream_test_jiter(graalpy, testdir):
    run(['git', 'clone', 'https://github.com/pydantic/jiter.git', '-b', 'main', '--depth', '1'], cwd=testdir)
    src = testdir / 'jiter'
    env = os.environ.copy()
    env['UV_PYTHON_DOWNLOADS'] = 'never'
    run(['uv', 'sync', '--python', graalpy, '--group', 'dev', '--all-packages'], cwd=src, env=env)
    run(['uv', 'run', '--python', graalpy, 'pytest', '-v', '--tb=short', 'crates/jiter-python/tests'], cwd=src, env=env)
    run(['uv', 'run', '--python', graalpy, 'crates/jiter-python/bench.py', 'jiter', 'jiter-cache', '--fast'], cwd=src, env=env)


@downstream_test('cython')
def downstream_test_cython(graalpy, testdir):
    run(['git', 'clone', 'https://github.com/cython/cython.git', '-b', 'master', '--depth', '1'], cwd=testdir)
    src = testdir / 'cython'
    venv = src / 'venv'
    env = os.environ.copy()
    env["PYTHON_VERSION"] = "graalpy"
    env["BACKEND"] = "c"
    run([graalpy, '-m', 'venv', str(venv)])
    if not CI:
        replace_in_file(src / 'Tools/ci-run.sh', r'^\s*sudo', '# sudo')
        try:
            run([graalpy, '--version', '--experimental-options', '--engine.Compilation=false'])
        except subprocess.CalledProcessError:
            replace_in_file(src / 'Tools/ci-run.sh', r'--engine\.Compilation=false', '')
    run_in_venv(venv, ["bash", "./Tools/ci-run.sh"], cwd=src, env=env)


# To run locally, use:
# docker run --rm -it -p 1521:1521 -p 5500:5500 -e ORACLE_PWD=asdf12345678 container-registry.oracle.com/database/free@sha256:51940ce2a4c9a085c9deb715713d68c579756e9bf09a0d7318c7e3e28f70ba1e
@downstream_test('oracledb')
def downstream_test_oracledb(graalpy, testdir):
    run([
        'git', 'clone', 'https://github.com/oracle/python-oracledb.git',
        '-b', 'main',
        '--depth', '1',
        '--recurse-submodules',
    ], cwd=testdir)
    src = testdir / 'python-oracledb'
    venv = src / 'venv'
    env = os.environ.copy()
    env.setdefault('PYO_TEST_CONNECT_STRING', "127.0.0.1:1521/FREEPDB1")
    env.setdefault('PYO_TEST_ADMIN_USER', "SYSTEM")
    env.setdefault('PYO_TEST_ADMIN_PASSWORD', "asdf12345678")
    env.setdefault('PYO_TEST_MAIN_USER', "pythontest")
    env.setdefault('PYO_TEST_MAIN_PASSWORD', "testpasswordAx3")
    env.setdefault('PYO_TEST_PROXY_USER', "pythontestproxy")
    env.setdefault('PYO_TEST_PROXY_PASSWORD', "testpasswordAx3")
    run([graalpy, '-m', 'venv', str(venv)])
    run_in_venv(venv, ['pip', 'install', '.[test]'], cwd=src)
    run_in_venv(venv, ['pip', 'install', 'pytest-rerunfailures'], cwd=src)
    run_in_venv(venv, ['pytest', '--tb=short', 'tests/create_schema.py'], cwd=src, env=env)
    try:
        for mode_arg in ([], ['--use-thick-mode']):
            run_in_venv(venv, [
                'pytest',
                '--tb=short',
                '-v',
                '-rs',
                '--reruns',
                '1',
                '--reruns-delay',
                '3',
                '--only-rerun',
                'Listener refused connection',
                'tests',
                '--ignore',
                'tests/ext',
                *mode_arg,
            ], cwd=src, env=env)
    finally:
        run_in_venv(venv, ['pytest', '--tb=short', 'tests/drop_schema.py'], cwd=src, env=env)


def run_downstream_test(python, project):
    testdir = Path('downstream-tests').absolute()
    shutil.rmtree(testdir, ignore_errors=True)
    testdir.mkdir(exist_ok=True)
    python = os.path.abspath(python)
    DOWNSTREAM_TESTS[project](python, testdir)


def main():
    parser = argparse.ArgumentParser("Runs important downstream packages tests using their main branch")
    parser.add_argument("python")
    parser.add_argument("project", choices=sorted(DOWNSTREAM_TESTS))
    args = parser.parse_args()
    run_downstream_test(args.python, args.project)


if __name__ == '__main__':
    main()
