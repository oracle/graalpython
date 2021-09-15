import pytest
from .support import ExtensionCompiler, DefaultExtensionTemplate
from hpy.debug.leakdetector import LeakDetector

def pytest_addoption(parser):
    parser.addoption(
        "--compiler-v", action="store_true",
        help="Print to stdout the commands used to invoke the compiler")

@pytest.fixture(scope='session')
def hpy_devel(request):
    from hpy.devel import HPyDevel
    return HPyDevel()

@pytest.fixture(params=['cpython', 'universal', 'debug'])
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
