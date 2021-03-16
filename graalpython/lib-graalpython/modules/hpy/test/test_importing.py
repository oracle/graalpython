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

from .support import HPyTest


class TestImporting(HPyTest):

    def test_importing_attributes(self):
        import pytest
        if not self.supports_ordinary_make_module_imports():
            pytest.skip()
        mod = self.make_module("""
            @INIT
        """, name='mytest')
        assert mod.__name__ == 'mytest'
        assert mod.__package__ == ''
        assert mod.__doc__ == 'some test for hpy'
        assert mod.__loader__.name == 'mytest'
        assert mod.__spec__.loader is mod.__loader__
        assert mod.__spec__.name == 'mytest'
        assert mod.__file__
