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

"""
Some of the files in this repo are used also by PyPy tests, which run on
python2.7.

This script tries to import all of them: it does not check any behavior, just
that they are importable and thus are not using any py3-specific syntax.

This script assumes that pathlib and pytest are installed (because the modules
try to import them).
"""

from __future__ import print_function
import sys
import traceback
import py

ROOT = py.path.local(__file__).join('..', '..')

def try_import(name):
    try:
        print('Trying to import %s... ' % name, end='')
        __import__(name)
    except:
        print('ERROR!')
        print()
        traceback.print_exc(file=sys.stdout)
        print()
        return False
    else:
        print('OK')
        return True

def try_import_hpy_devel():
    """
    To import hpy.devel we need to create an empty hpy/__init__.py, because
    python2.7 does not support namespace packages.

    Return the number of failed imports.
    """
    failed = 0
    init_py = ROOT.join('hpy', '__init__.py')
    assert init_py.check(exists=False)
    try:
        init_py.write('') # create an empty __init__.py
        if not try_import('hpy.devel'):
            failed += 1
    finally:
        init_py.remove()
    return failed

def try_import_tests():
    failed = 0
    for t in ROOT.join('test').listdir('test_*.py'):
        if t.purebasename == 'test_support':
            continue
        if not try_import('test.%s' % t.purebasename):
            failed += 1
    return failed

def main():
    if sys.version_info[:2] != (2, 7):
        print('ERROR: this script should be run on top of python 2.7')
        sys.exit(1)

    sys.path.insert(0, str(ROOT))
    failed = 0
    failed += try_import_hpy_devel()
    failed += try_import_tests()
    print()
    if failed == 0:
        print('Everything ok!')
    else:
        print('%d failed imports :(' % failed)
        sys.exit(1)

if __name__ == '__main__':
    main()
