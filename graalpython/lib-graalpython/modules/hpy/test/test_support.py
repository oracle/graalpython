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

def expand_template(template, name):
    return support.DefaultExtensionTemplate(template, name).expand()


def test_expand_template():
    expanded = expand_template("""
        @EXPORT(f)
        @EXPORT(g)
        some more C stuff
        @INIT
    """, name='mytest')
    defines_table = ['&f,', '&g,']
    defines = '\n        '.join(defines_table)
    init_code = support.DefaultExtensionTemplate.INIT_TEMPLATE % {
        'defines': defines,
        'legacy_methods': 'NULL',
        'name': 'mytest',
        'init_types': '',
    }
    assert expanded.rstrip() == f"""#include <hpy.h>

some more C stuff
{init_code}
""".rstrip()
