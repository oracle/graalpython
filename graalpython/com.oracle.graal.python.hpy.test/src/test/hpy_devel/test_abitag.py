# MIT License
#
# Copyright (c) 2023, Oracle and/or its affiliates.
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

from hpy.devel.abitag import parse_ext_suffix, get_hpy_ext_suffix, HPY_ABI_TAG

def test_parse_ext_suffix_ext():
    _, ext = parse_ext_suffix('.cpython-310-x86_64-linux-gnu.so')
    assert ext == 'so'

def test_parse_ext_suffix_abi_tag():
    def abi_tag(suffix):
        tag, ext = parse_ext_suffix(suffix)
        return tag

    assert abi_tag('.cpython-38-x86_64-linux-gnu.so') == 'cp38'
    assert abi_tag('.cpython-38d-x86_64-linux-gnu.so') == 'cp38d'
    assert abi_tag('.cpython-310-x86_64-linux-gnu.so') == 'cp310'
    assert abi_tag('.cpython-310d-x86_64-linux-gnu.so') == 'cp310d'
    assert abi_tag('.cpython-310-darwin.so') == 'cp310'
    assert abi_tag('.cp310-win_amd64.pyd') == 'cp310'
    assert abi_tag('.pypy38-pp73-x86_64-linux-gnu.so') == 'pypy38-pp73'
    assert abi_tag('.graalpy-38-native-x86_64-darwin.dylib') == 'graalpy-38-native'

def test_get_hpy_ext_suffix():
    get = get_hpy_ext_suffix
    hpy0 = HPY_ABI_TAG
    assert get('universal', '.cpython-38-x86_64-linux-gnu.so') == f'.{hpy0}.so'
    assert get('hybrid', '.cpython-38-x86_64-linux-gnu.so') == f'.{hpy0}-cp38.so'
