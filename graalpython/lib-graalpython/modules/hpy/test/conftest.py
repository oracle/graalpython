# MIT License
#
# Copyright (c) 2020, 2022, Oracle and/or its affiliates.
# Copyright (c) 2019 pyhandle
#
# Permission is hereby granted, free of charge, to any person obtaining a copy
# of this software and associated documentation files (the "Software"), to deal
# in the Software without restriction, including without limitation the rights
# to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
# copies of the Software, and to permit persons to whom the Software is
# furnished to do so, subject to the following conditions:
#
# The above copyright notice and this permission notice shall be included in all
# copies or substantial portions of the Software.
#
# THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
# IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
# FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
# AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
# LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
# OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
# SOFTWARE.
import os
import sys
import pytest
from .support import ExtensionCompiler, DefaultExtensionTemplate,\
    PythonSubprocessRunner, HPyDebugCapture
from hpy.debug.leakdetector import LeakDetector

SELECTED_ABI_MODE = os.environ.get("TEST_HPY_ABI", None)
if SELECTED_ABI_MODE:
    SELECTED_ABI_MODE = [SELECTED_ABI_MODE]
IS_VALGRIND_RUN = False
GRAALPYTHON_NATIVE = sys.implementation.name == 'graalpython' and __graalpython__.platform_id == 'native'
def pytest_addoption(parser):
    parser.addoption(
        "--compiler-v", action="store_true",
        help="Print to stdout the commands used to invoke the compiler")
    parser.addoption(
        "--subprocess-v", action="store_true",
        help="Print to stdout the stdout and stderr of Python subprocesses"
             "executed via run_python_subprocess")


@pytest.hookimpl(trylast=True)
def pytest_configure(config):
    global IS_VALGRIND_RUN
    IS_VALGRIND_RUN = config.pluginmanager.hasplugin('valgrind_checker')
    config.addinivalue_line(
        "markers", "syncgc: Mark tests that rely on a synchronous GC."
    )
    config.addinivalue_line(
        "markers", "tp_traverse: Mark tests that rely tp_traverse being called."
    )


def pytest_runtest_setup(item):
    if (sys.implementation.name in ["graalpy", "pypy"] and
        "syncgc" in [mark.name for mark in item.iter_markers()]):
        pytest.skip(f"cannot run syncgc test on {sys.implementation.name}")
    if (sys.implementation.name in ["graalpy"] and
        "tp_traverse" in [mark.name for mark in item.iter_markers()]):
        pytest.skip(f"{sys.implementation.name} does not call tp_traverse")


@pytest.fixture(scope='session')
def hpy_devel(request):
    from hpy.devel import HPyDevel
    return HPyDevel()

@pytest.fixture(params=SELECTED_ABI_MODE or (['cpython', 'universal', 'debug', 'nfi'] if GRAALPYTHON_NATIVE else ['cpython', 'universal', 'debug']))
def hpy_abi(request):
    abi = request.param
    if abi == 'debug':
        with LeakDetector():
            yield abi
    else:
        yield abi

@pytest.fixture
def ExtensionTemplate():
    return DefaultExtensionTemplate


@pytest.fixture
def compiler(request, tmpdir, hpy_devel, hpy_abi, ExtensionTemplate):
    compiler_verbose = request.config.getoption('--compiler-v')
    return ExtensionCompiler(tmpdir, hpy_devel, hpy_abi,
                             compiler_verbose=compiler_verbose,
                             ExtensionTemplate=ExtensionTemplate)

@pytest.fixture()
def skip_nfi(hpy_abi):
    # skip all tests in this class for NFI mode
    if hpy_abi == 'nfi':
        pytest.skip()


@pytest.fixture()
def skip_cpython_abi(hpy_abi):
    # skip all tests in this class for CPython ABI mode
    if hpy_abi == 'cpython':
        pytest.skip()


@pytest.fixture(scope="session")
def fatal_exit_code(request):
    import sys
    return {
        "linux": -6,  # SIGABRT
        # See https://bugs.python.org/issue36116#msg336782 -- the
        # return code from abort on Windows 8+ is a stack buffer overrun.
        # :|
        "win32": 0xC0000409,  # STATUS_STACK_BUFFER_OVERRUN
    }.get(sys.platform, -6)


@pytest.fixture
def python_subprocess(request, hpy_abi):
    verbose = request.config.getoption('--subprocess-v')
    yield PythonSubprocessRunner(verbose, hpy_abi)


@pytest.fixture()
def hpy_debug_capture(request, hpy_abi):
    assert hpy_abi == 'debug'
    with HPyDebugCapture() as reporter:
        yield reporter
