# Copyright (c) 2018, 2024, Oracle and/or its affiliates. All rights reserved.
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

from . import CPyExtTestCase, CPyExtFunction, CPyExtFunctionOutVars, unhandled_error_compare


def _reference_contains(args):
    if not (isinstance(args[0], set) or isinstance(args[0], frozenset)):
        raise SystemError
    return args[1] in args[0]


def _reference_clear(args):
    args[0].clear()
    return args[0]


def _reference_next(args):
    try:
        s = args[0]
        i = 0
        for k in s:
            if i == args[1]:
                return (1, k, hash(k))
            i = i + 1
        return (0, None, 0)
    except:
        return (0, None, 0)


def _reference_pop(args):
    s = args[0]
    try:
        return s.pop()
    except AttributeError:
        raise SystemError

    
def _reference_discard(args):
    s = args[0]
    if not (isinstance(s, set)):
        raise SystemError
    
    if args[1] in s:
        s.discard(args[1])
        return 1
    return 0

    
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

    test_PySet_GET_SIZE = CPyExtFunction(
        lambda args: len(args[0]),
        lambda: (
            (set(),),
            (set(),),
            (set([1, 2, 3]),),
            (set({'a', 'b'}),),
            (frozenset([1, 2, 3]),),
            (frozenset({'a', 'b'}),),
            (frozenset([None]),),
            (FrozenSetSubclass(),),
            (SetSubclass([None]),),
        ),
        resultspec="n",
        argspec='O',
        arguments=["PyObject* o"],
        cmpfunc=unhandled_error_compare
    )

    test_PySet_Clear = CPyExtFunction(
        _reference_clear,
        lambda: (
            ({1, 2, 3},),
            ({1, "a", 0.1},),
        ),
        code='''PyObject* wrap_PySet_Clear(PyObject* set) {
            if (PySet_Clear(set)) {
                return NULL;
            }
            Py_INCREF(set);
            return set;
        }
        ''',
        resultspec="O",
        argspec='O',
        arguments=["PyObject* set"],
        callfunction="wrap_PySet_Clear",
        cmpfunc=unhandled_error_compare
    )

    test_PySet_NextEntry = CPyExtFunctionOutVars(
        _reference_next,
        lambda: ((set(),1),
            (set([1, 2, 3]),1),
            (set({'a', 'b'}),1),
            (frozenset([1, 2, 3]),1),
            (frozenset({'a', 'b'}),1),
            (frozenset([None]),1),
            (FrozenSetSubclass(),1),
            (SetSubclass([None]),1),),
        code='''int wrap_PySet_NextEntry(PyObject* set, Py_ssize_t* ppos, PyObject **key, Py_hash_t* hash) {
            int res = 0;
            Py_ssize_t iterations = *ppos;
            Py_ssize_t i;
            *ppos = 0;
            for(i=0; i < iterations; i++) {
                _PySet_NextEntry(set, ppos, key, hash);
            }
            res = _PySet_NextEntry(set, ppos, key, hash);
            if (!res) {
                // avoid problems when building the result value
                *key = set;
                *hash = 0;     
                Py_INCREF(set);
            }
            return res;
        }
        ''',
        resultspec="iOn",
        argspec='On',
        arguments=("PyObject* set", "Py_ssize_t ppos"),
        resulttype="int",
        argumentnames=("set, &ppos"),
        resultvars=("PyObject* key", "Py_hash_t hash"),
        callfunction="wrap_PySet_NextEntry",
        cmpfunc=lambda x, y: type(x) == tuple and type(y) == tuple and len(x) == 3 and len(y) == 3 and (x[0] == 0 and y[0] == 0 or x == y)
    )

    # PySet_Pop
    test_PySet_Pop = CPyExtFunction(
        _reference_pop,
        lambda: ((set(),),
            (set([1, 2, 3]),),
            (set({'a', 'b'}),),
            (frozenset([1, 2, 3]),),
            (FrozenSetSubclass(),),
            (SetSubclass([None]),),),
        resultspec="O",
        argspec='O',
        cmpfunc=unhandled_error_compare
    )
    
    # PySet_Discard
    test_PySet_Discard = CPyExtFunction(
        _reference_discard,
        lambda: ((set(),1),
            (set([1, 2, 3]),1),
            (set([1, 2, 3]),None),
            (set({'a', 'b'}),1),
            (frozenset([1, 2, 3]),1),
            (FrozenSetSubclass(),1),
            (SetSubclass([None]),1)),
        resultspec="i",
        argspec='OO',
        arguments=("PyObject* set", "PyObject* key"),
        argumentnames=("set, key"),
        cmpfunc=unhandled_error_compare
    )
