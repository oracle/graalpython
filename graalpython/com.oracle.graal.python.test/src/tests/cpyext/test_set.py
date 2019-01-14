# Copyright (c) 2018, Oracle and/or its affiliates. All rights reserved.
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
from . import CPyExtTestCase, CPyExtFunction, CPyExtFunctionOutVars, unhandled_error_compare, GRAALPYTHON
__dir__ = __file__.rpartition("/")[0]


def raise_Py6_SystemError():
    if sys.version_info.minor >= 6:
        raise SystemError
    else:
        return -1
    

def _reference_contains(args):
    if not (isinstance(args[0], set) or isinstance(args[0], frozenset)):
        raise SystemError
    try:
        return args[1] in args[0]
    except TypeError:
        return raise_Py6_SystemError()

class FrozenSetSubclass(frozenset):
    pass


class SetSubclass(set):
    pass


default_typecheck_args = lambda: (
            (set(),),
            (set([1, 2, 3]),),
            (set({'a', 'b'}),),
            (set([None]),),
            (frozenset(),),
            (frozenset([1, 2, 3]),),
            (frozenset({'a', 'b'}),),
            (frozenset([None]),),
            (FrozenSetSubclass(),),
            (SetSubclass([None]),),
            ({'a': "hello", 'b': "world"},),
            ('a',),
            (1,),
            (None,),
        )


class TestPySet(CPyExtTestCase):

    def compile_module(self, name):
        type(self).mro()[1].__dict__["test_%s" % name].create_module(name)
        super(TestPySet, self).compile_module(name)

    test_PySet_New = CPyExtFunction(
        lambda args: set(args[0]),
        lambda: (
            ([1, 2, 3],),
            ({'a': "hello", 'b': "world"},),
            ({'a', 'b'},),
            ('a',),
            (1,),
        ),
        resultspec="O",
        argspec='O',
        arguments=["PyObject* iterable"],
        cmpfunc=unhandled_error_compare
    )

    test_PyFrozenSet_New = CPyExtFunction(
        lambda args: frozenset(args[0]),
        lambda: (
            ([1, 2, 3],),
            ({'a': "hello", 'b': "world"},),
            ({'a', 'b'},),
            ('a',),
            (1,),
        ),
        resultspec="O",
        argspec='O',
        arguments=["PyObject* iterable"],
        cmpfunc=unhandled_error_compare
    )

    test_PyFrozenSet_Check = CPyExtFunction(
        lambda args: isinstance(args[0], frozenset),
        default_typecheck_args,
        resultspec="i",
        argspec='O',
        arguments=["PyObject* o"],
        cmpfunc=unhandled_error_compare
    )

    test_PyFrozenSet_CheckExact = CPyExtFunction(
        lambda args: type(args[0]) is frozenset,
        default_typecheck_args,
        resultspec="i",
        argspec='O',
        arguments=["PyObject* o"],
        cmpfunc=unhandled_error_compare
    )

    test_PyAnySet_CheckExact = CPyExtFunction(
        lambda args: type(args[0]) is set or type(args[0]) is frozenset,
        default_typecheck_args,
        resultspec="i",
        argspec='O',
        arguments=["PyObject* o"],
        cmpfunc=unhandled_error_compare
    )

    test_PyAnySet_Check = CPyExtFunction(
        lambda args: isinstance(args[0], set) or isinstance(args[0], frozenset),
        default_typecheck_args,
        resultspec="i",
        argspec='O',
        arguments=["PyObject* o"],
        cmpfunc=unhandled_error_compare
    )

    test_PySet_Check = CPyExtFunction(
        lambda args: isinstance(args[0], set),
        default_typecheck_args,
        resultspec="i",
        argspec='O',
        arguments=["PyObject* o"],
        cmpfunc=unhandled_error_compare
    )

    test_PySet_Contains = CPyExtFunction(
        _reference_contains,
        lambda: (
            (set(),"A"),
            (set(),None),
            (set([1, 2, 3]),1),
            (set({'a', 'b'}),'a'),
            (frozenset([1, 2, 3]),[1,2,3]),
            (frozenset({'a', 'b'}),'A'),
            (frozenset([None]),None),
            (FrozenSetSubclass(),None),
            (SetSubclass([None]),None),
        ),
        resultspec="i",
        argspec='OO',
        arguments=["PyObject* o", "PyObject* item"],
        cmpfunc=unhandled_error_compare
    )
