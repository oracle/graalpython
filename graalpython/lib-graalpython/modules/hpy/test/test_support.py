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

from . import support

def test_expand_template():
    expanded = support.expand_template("""
        @EXPORT test_f HPy_METH_NOARGS
        @EXPORT test_g METH_O
        @EXPORT test_h METH_VARARGS | METH_KEYWORDS
        some more C stuff
        @INIT
    """, name='mytest')
    method_table = [
        '{"test_f", test_f, HPy_METH_NOARGS, NULL},',
        '{"test_g", (HPyMeth)test_g, METH_O, NULL},',
        '{"test_h", (HPyMeth)test_h, METH_VARARGS | METH_KEYWORDS, NULL},'
        ]
    methods = '\n    '.join(method_table)
    init_code = support.INIT_TEMPLATE % {'methods': methods, 'name': 'mytest'}
    assert expanded.rstrip() == f"""#include <hpy.h>

        some more C stuff
{init_code}
""".rstrip()
