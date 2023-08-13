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
from pathlib import Path
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
    defines = '\n    '.join(defines_table)
    init_code = support.DefaultExtensionTemplate.INIT_TEMPLATE % {
        'defines': defines,
        'legacy_methods': 'NULL',
        'name': 'mytest',
        'init_types': '',
        'globals_defs': '',
        'globals_field': '',
    }
    assert expanded.rstrip() == f"""#include <hpy.h>

some more C stuff
{init_code}
""".rstrip()


TEST_DIR = Path(__file__).parent


def test_source_dump(tmpdir):
    import pytest
    test_file = 'test_00_basic'
    parts = ['TestBasic', 'test_noop_function']
    rc = pytest.main(['--dump-dir=' + str(tmpdir), '::'.join([str(TEST_DIR.joinpath(test_file + ".py"))] + parts)])
    assert rc == 0
    expected_dir = '_'.join([test_file] + parts)
    print("expected_dir = " + expected_dir)
    test_dump_dir = None
    for child in Path(tmpdir).iterdir():
        if expected_dir in str(child):
            test_dump_dir = Path(child)
    assert test_dump_dir and test_dump_dir.exists()
    assert test_dump_dir.joinpath('mytest.c').exists()
