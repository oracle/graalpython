# MIT License
#
# Copyright (c) 2020, Oracle and/or its affiliates.
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

import pytest
from .support import ExtensionCompiler

def pytest_addoption(parser):
    parser.addoption(
        "--correct", action="store_true",
        help="Test against headers installed through hpy_devel"
    )
    parser.addoption(
        "--compiler-v", action="store_true",
        help="Print to stdout the commands used to invoke the compiler")


@pytest.fixture(scope='session')
def hpy_include_dir(request):
    if request.config.getoption('--correct'):
        from hpy.devel import get_include
        return get_include()
    else:
        import os
        THIS_DIR = os.path.dirname(__file__)
        return os.path.join(THIS_DIR, '../hpy/devel/include')

@pytest.fixture(scope='session')
def abimode():
    return "universal"

@pytest.fixture
def compiler(request, tmpdir, abimode, hpy_include_dir):
    compiler_verbose = request.config.getoption('--compiler-v')
    return ExtensionCompiler(tmpdir, abimode, hpy_include_dir,
                             compiler_verbose=compiler_verbose)
