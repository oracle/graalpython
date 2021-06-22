# MIT License
# 
# Copyright (c) 2020, 2021, Oracle and/or its affiliates.
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
from .support import ExtensionCompiler
from hpy.debug.pytest import hpy_debug # make it available to all tests

GRAALPYTHON = sys.implementation.name == "graalpython"

def pytest_addoption(parser):
    parser.addoption(
        "--compiler-v", action="store_true",
        help="Print to stdout the commands used to invoke the compiler")

@pytest.fixture(scope='session')
def hpy_devel(request):
    from hpy.devel import HPyDevel
    return HPyDevel()

@pytest.fixture(params=['cpython', 'universal', 'debug', 'nfi'] if GRAALPYTHON else ['cpython', 'universal', 'debug'])
def hpy_abi(request):
    return request.param

@pytest.fixture
def compiler(request, tmpdir, hpy_devel, hpy_abi):
    compiler_verbose = request.config.getoption('--compiler-v')
    if hpy_abi == "nfi":
        assert GRAALPYTHON, "NFI mode is only supported on GraalVM"
        hpy_abi = "universal"
        from distutils.sysconfig import get_config_vars
        from os.path import join
        conf = get_config_vars()
        clang = join(os.path.sep, "usr", "bin", "clang")
        conf["CC"] = clang
        conf['CXX'] = join(os.path.sep, "usr", "bin", "clang++")
        conf['LDSHARED_LINUX'] = clang + " -shared -fPIC"
        # if on Darwin and in native mode
        if sys.platform == "darwin" and __graalpython__.platform_id == "native":
            conf['LDSHARED'] = clang + " -bundle -undefined dynamic_lookup"
            conf['LDFLAGS'] = "-bundle -undefined dynamic_lookup"
        else:
            conf['LDSHARED'] = conf['LDSHARED_LINUX']

    return ExtensionCompiler(tmpdir, hpy_devel, hpy_abi,
                             compiler_verbose=compiler_verbose)
