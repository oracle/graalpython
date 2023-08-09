# MIT License
# 
# Copyright (c) 2020, 2023, Oracle and/or its affiliates.
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
from .support import HPyTest
from hpy.devel.abitag import get_hpy_ext_suffix

@pytest.fixture(params=['cpython', 'universal', 'hybrid'] + (['debug'] if HPyTest.supports_debug_mode() else []))
def hpy_abi(request):
    abi = request.param
    yield abi


class TestImporting(HPyTest):

    def full_import(self, name, mod_filename):
        import importlib
        import sys
        import os
        if name in sys.modules:
            raise ValueError(
                "Test module {!r} already present in sys.modules".format(name))
        importlib.invalidate_caches()
        mod_dir = os.path.dirname(mod_filename)
        sys.path.insert(0, mod_dir)
        try:
            module = importlib.import_module(name)
            assert sys.modules[name] is module
        finally:
            # assert that the module import didn't change the sys.path entry
            # that was added above, then remove the entry.
            assert sys.path[0] == mod_dir
            del sys.path[0]
            if name in sys.modules:
                del sys.modules[name]
        return module

    def test_importing_attributes(self, hpy_abi, tmpdir):
        import pytest
        if not self.supports_ordinary_make_module_imports():
            pytest.skip()
        mod = self.make_module("""
            @INIT
        """, name='mytest')
        mod = self.full_import(mod.__name__, mod.__file__)
        assert mod.__name__ == 'mytest'
        assert mod.__package__ == ''
        assert mod.__doc__ == 'some test for hpy'
        assert mod.__loader__.name == 'mytest'
        assert mod.__spec__.loader is mod.__loader__
        assert mod.__spec__.name == 'mytest'
        assert mod.__file__

        if hpy_abi == 'debug':
            hpy_abi = 'universal'
        ext_suffix = get_hpy_ext_suffix(hpy_abi)
        assert repr(mod) == '<module \'mytest\' from {}>'.format(
            repr(str(tmpdir.join('mytest' + ext_suffix))))
