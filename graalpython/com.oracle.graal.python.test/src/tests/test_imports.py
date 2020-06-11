# Copyright (c) 2018, 2020, Oracle and/or its affiliates. All rights reserved.
# DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
#
# The Universal Permissive License (UPL), Version 1.0
#
# Subject to the condition set forth below, permission is hereby granted to any
# person obtaining a copy of this software, associated documentation and/or
# data (collectively the "Software"), free of charge and under any and all
# copyright rights in the Software, and any and all patent rights owned or
# freely licensable by each licensor hereunder covering either (i) the
# unmodified Software as contributed to or provided by such licensor, or (ii)
# the Larger Works (as defined below), to deal in both
#
# (a) the Software, and
#
# (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
# one is included with the Software each a "Larger Work" to which the Software
# is contributed by such licensors),
#
# without restriction, including without limitation the rights to copy, create
# derivative works of, display, perform, and distribute the Software and make,
# use, sell, offer for sale, import, export, have made, and have sold the
# Software and the Larger Work(s), and to sublicense the foregoing rights on
# either these or other terms.
#
# This license is subject to the following condition:
#
# The above copyright notice and either this complete permission notice or at a
# minimum a reference to the UPL must be included in all copies or substantial
# portions of the Software.
#
# THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
# IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
# FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
# AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
# LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
# OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
# SOFTWARE.

import sys
from posix import *

sys.path.append(sys._getframe().f_code.co_filename.rpartition("/")[0])


def test_relative_import():
    # this is to prevent ides from optimising out the unused import
    try:
        import package
    except Exception as e:
        raise e


def test_module_docstring():
    import package
    assert package.__doc__ == "PACKAGE DOC"
    from package import moduleA
    assert moduleA.__doc__ == "MODULE A DOC"


def test_dotted_import():
    # this is to prevent ides from optimising out the unused import
    try:
        import package.moduleY
    except Exception as e:
        raise e


def test_recursive_import():
    # this is to prevent ides from optimising out the unused import
    try:
        import package.moduleRecursive
    except Exception as e:
        raise e


def test_recursive_import2():
    # this is to prevent ides from optimising out the unused import
    try:
        import package.moduleRecursive2
    except Exception as e:
        raise e


def test_import_star_has_to_be_module():
    compile("from foo import *", "exec", "exec")

    try:
        compile("def bar(): from foo import *", "exec", "exec")
    except SyntaxError:
        assert True
    else:
        assert False


def test_import_as_local():
    from sys import path
    from sys import path as mypath
    assert path is sys.path
    assert mypath is sys.path


def test_import_as_class_local():
    class A():
        from sys import path
    assert A.path is sys.path


def test_import_error():
    try:
        from sys import foo
    except Exception as e:
        assert isinstance(e, ImportError)


def test_import_some_star():
    import posix
    assert stat == posix.stat


def test_imp_fix_co_filename():
    import _imp

    def func(x):
        return x+x

    code = func.__code__
    old_name = code.co_filename
    _imp._fix_co_filename(code, old_name + '_more_path')
    assert code.co_filename == old_name + '_more_path'


def test_recursive_import_from():
    if sys.version_info.minor >= 6:
        import package.recpkg
        assert package.recpkg.context is package.recpkg.reduction.context


if sys.implementation.name == "graalpython":
    def test_imp_cached_imports():
        import _imp

        finder = _imp.CachedImportFinder

        spec = finder.find_spec("encodings", None)
        assert spec.submodule_search_locations


def test_import_package_all() :
    import package1
    expected_syms = ["moduleX", "lib1_hello", "lib1_world"]
    cnt = 0
    for expected_sym in expected_syms:
        assert hasattr(package1, expected_sym), "'package1' does not have attribute '%s'" % expected_sym
        cnt += 1
    assert package1.exported.__testname__ == "package1.exported", "expected 'test_import_package_all' but was '%s'" % str(package1.exported.__testname__)
