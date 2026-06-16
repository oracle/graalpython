# Copyright (c) 2018, 2026, Oracle and/or its affiliates. All rights reserved.
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
import _io
import functools
import io
import itertools
import json
import os
import pickle
import tempfile
import unittest

from . import CPyExtTestCase, CPyExtFunction, CPyExtFunctionOutVars, unhandled_error_compare, CPyExtType, \
    is_native_object


def _reference_getslice(args):
    t = args[0]
    start = args[1]
    end = args[2]
    return t[start:end]


def _reference_getitem(args):
    t = args[0]
    idx = args[1]
    if idx < 0 or idx >= len(t):
        raise IndexError('tuple index out of range')
    return t[idx]


def _reference_setitem(args):
    t = list(args[0])
    idx = args[1]
    value = args[2]
    if idx < 0 or idx >= len(t):
        raise IndexError('tuple index out of range')
    t[idx] = value
    return tuple(t)


def _reference_resize(args):
    original = args[0]
    newsize = args[1]
    return original[:newsize] + (None,) * (newsize - len(original))
    return tuple(result)


class MyStr(str):

    def __init__(self, s):
        self.s = s

    def __repr__(self):
        return self.s


TupleSubclass = CPyExtType(
    "TupleSubclass",
    """
        static PyObject* tuple_subclass_new(PyTypeObject* type, PyObject* args, PyObject* kwargs) {
            args = Py_BuildValue("(O)", args);
            if (args == NULL)
                return NULL;
            PyObject* result = PyTuple_Type.tp_new(type, args, kwargs);
            return result;
       }
    """,
    struct_base='PyTupleObject tuple;',
    tp_base='&PyTuple_Type',
    tp_new='tuple_subclass_new',
    tp_alloc='0',
    tp_free='0',
)


class TestPyTuple(CPyExtTestCase):

    # PyTuple_Size
    test_PyTuple_Size = CPyExtFunction(
        lambda args: len(args[0]),
        lambda: (
            (tuple(),),
            ((1, 2, 3),),
            (("a", "b"),),
            (TupleSubclass(1, 2, 3),),
        ),
        resultspec="n",
        argspec='O',
        arguments=["PyObject* tuple"],
    )

    # PyTuple_GET_SIZE
    test_PyTuple_GET_SIZE = CPyExtFunction(
        lambda args: len(args[0]),
        lambda: (
            (tuple(),),
            ((1, 2, 3),),
            (("a", "b"),),
            (TupleSubclass(1, 2, 3),),
            # no type checking, must not use non-tuple objects
        ),
        resultspec="n",
        argspec='O',
        arguments=["PyObject* tuple"],
    )

    # PyTuple_GetItem
    test_PyTuple_GetItem = CPyExtFunctionOutVars(
        _reference_getitem,
        lambda: (
            ((1, 2, 3), 1),
            (TupleSubclass(1, 2, 3), 1),
            ((1, 2, 3), -1),
            ((1, 2, 3), 3),
        ),
        resultspec="O",
        argspec='On',
        arguments=["PyObject* tuple", "Py_ssize_t index"],
        resulttype="PyObject*",
    )

    test_PyTuple_SetItem = CPyExtFunction(
        _reference_setitem,
        lambda: (
            ((1, 2, 3), 1, "a"),
            (TupleSubclass(1, 2, 3), 1, 5),
            ((1, 2, 3), -1, []),
            ((1, 2, 3), 3, str),
        ),
        code="""
        PyObject* wrap_PyTuple_SetItem(PyObject* original, Py_ssize_t index, PyObject* value) {
            Py_ssize_t size = PyTuple_Size(original);
            if (size < 0)
                return NULL;
            PyObject* tuple = PyTuple_New(size);
            if (!tuple)
                return NULL;
            for (int i = 0; i < size / 2; i++) {
                PyObject* item = PyTuple_GetItem(original, i);
                if (!item) {
                    Py_DECREF(tuple);
                    return NULL;
                }
                Py_INCREF(item);
                PyTuple_SET_ITEM(tuple, i, item);
            }

            #ifdef GRAALVM_PYTHON
            // test that we also have it as API function on GraalPy
            #undef PyTuple_SET_ITEM
            #endif

            for (int i = size / 2; i < size; i++) {
                PyObject* item = PyTuple_GetItem(original, i);
                if (!item) {
                    Py_DECREF(tuple);
                    return NULL;
                }
                Py_INCREF(item);
                PyTuple_SET_ITEM(tuple, i, item);
            }

            Py_INCREF(value);
            if (PyTuple_SetItem(tuple, index, value) < 0) {
                Py_DECREF(tuple);
                return NULL;
            }
            return tuple;
        }
        """,
        resultspec="O",
        argspec='OnO',
        arguments=["PyObject* tuple", "Py_ssize_t index", "PyObject* value"],
        callfunction="wrap_PyTuple_SetItem",
        cmpfunc=unhandled_error_compare,
    )

    # PyTuple_GetSlice
    test_PyTuple_GetSlice = CPyExtFunctionOutVars(
        _reference_getslice,
        lambda: (
            (tuple(), 0, 0),
            ((1, 2, 3), 0, 2),
            ((4, 5, 6), 1, 2),
            ((7, 8, 9), 2, 2),
            (TupleSubclass(1, 2, 3), 1, 2),
        ),
        resultspec="O",
        argspec='Onn',
        arguments=["PyObject* tuple", "Py_ssize_t start", "Py_ssize_t end"],
        resulttype="PyObject*",
    )

    # PyTuple_Pack
    test_PyTuple_Pack = CPyExtFunctionOutVars(
        lambda vals: vals[1:],
        lambda: ((3, MyStr("hello"), MyStr("beautiful"), MyStr("world")),),
        resultspec="O",
        argspec='nOOO',
        arguments=["Py_ssize_t n", "PyObject* arg0", "PyObject* arg1", "PyObject* arg2"],
         resulttype="PyObject*",
    )

    # PyTuple_Check
    test_PyTuple_Check = CPyExtFunction(
        lambda args: isinstance(args[0], tuple),
        lambda: (
            (tuple(),),
            (("hello", "world"),),
            (TupleSubclass(1, 2, 3),),
            ((None,),),
            ([],),
            ({},),
        ),
        resultspec="i",
        argspec='O',
        arguments=["PyObject* o"],
        cmpfunc=unhandled_error_compare
    )

    # PyTuple_Check
    test_PyTuple_CheckExact = CPyExtFunction(
        lambda args: type(args[0]) is tuple,
        lambda: (
            (tuple(),),
            (("hello", "world"),),
            (TupleSubclass(1, 2, 3),),
            ((None,),),
            ([],),
            ({},),
        ),
        resultspec="i",
        argspec='O',
        arguments=["PyObject* o"],
        cmpfunc=unhandled_error_compare
    )

    # _PyTuple_Resize
    test_PyTuple_Resize = CPyExtFunction(
        _reference_resize,
        lambda: (
            ((1, 2, 3), 1),
            ((), 1),
            ((1, 2, 3), 5),
        ),
        code="""
        PyObject* wrap_PyTuple_Resize(PyObject* original, int newsize) {
            int size = PyTuple_Size(original);
            PyObject *freshTuple = PyTuple_New(size);
            for (int i = 0; i < size; ++i) {
                PyTuple_SET_ITEM(freshTuple, i, Py_NewRef(PyTuple_GetItem(original, i)));
            }
            _PyTuple_Resize(&freshTuple, newsize);
            for (int i = size; i < newsize; i++) {
                PyTuple_SET_ITEM(freshTuple, i, Py_NewRef(Py_None));
            }
            return freshTuple;
        }
        """,
        resultspec="O",
        argspec='Oi',
        arguments=["PyObject* original", "Py_ssize_t newsize"],
        callfunction="wrap_PyTuple_Resize",
        cmpfunc=unhandled_error_compare,
    )


class TestNativeTupleStorage(unittest.TestCase):
    def test_marshal_native_tuple(self):
        import marshal

        TestNativeTupleFactory = CPyExtType(
            "TestNativeTupleFactory",
            """
            static PyObject* get_tuple(PyObject* cls) {
                PyObject* value = PyLong_FromLong(42);
                if (value == NULL)
                    return NULL;
                PyObject* text = PyUnicode_FromString("native");
                if (text == NULL) {
                    Py_DECREF(value);
                    return NULL;
                }
                PyObject* result = PyTuple_Pack(2, value, text);
                Py_DECREF(value);
                Py_DECREF(text);
                return result;
            }
            """,
            tp_methods='{"get_tuple", (PyCFunction)get_tuple, METH_NOARGS | METH_CLASS, ""}',
        )

        native_tuple = TestNativeTupleFactory.get_tuple()
        assert is_native_object(native_tuple)
        assert marshal.loads(marshal.dumps(native_tuple)) == (42, "native")


class TestNativeSubclass(unittest.TestCase):
    def _verify(self, t):
        assert is_native_object(t)
        assert t
        assert len(t) == 3
        assert t[1] == 2
        assert t[1:] == (2, 3)
        assert t == (1, 2, 3)
        assert t > (1, 2, 2)
        assert t < (1, 2, 4)
        assert 2 in t
        assert t + (4, 5) == (1, 2, 3, 4, 5)
        assert t * 2 == (1, 2, 3, 1, 2, 3)
        assert list(t) == [1, 2, 3]
        assert repr(t) == "(1, 2, 3)"
        assert t.index(2) == 1
        assert t.count(2) == 1

    def test_builtins(self):
        t = TupleSubclass(1, 2, 3)
        assert type(t) == TupleSubclass
        self._verify(t)

    def test_managed_sublclass(self):
        class ManagedSubclass(TupleSubclass):
            pass

        t = ManagedSubclass(1, 2, 3)
        assert is_native_object(t)
        assert type(t) == ManagedSubclass
        self._verify(t)

    def test_str_startswith_endswith_native_tuple(self):
        prefixes = TupleSubclass("other", "native")
        suffixes = TupleSubclass("other", ".py")
        assert is_native_object(prefixes)
        assert is_native_object(suffixes)
        assert "native.py".startswith(prefixes)
        assert "native.py".endswith(suffixes)

    def test_bytes_startswith_endswith_native_tuple(self):
        prefixes = TupleSubclass(b"other", b"native")
        suffixes = TupleSubclass(b"other", b".py")
        assert is_native_object(prefixes)
        assert is_native_object(suffixes)
        assert b"native.py".startswith(prefixes)
        assert b"native.py".endswith(suffixes)
        assert bytearray(b"native.py").startswith(prefixes)
        assert bytearray(b"native.py").endswith(suffixes)

    def test_memoryview_cast_shape_native_tuple(self):
        shape = TupleSubclass(2, 2)
        assert is_native_object(shape)
        view = memoryview(b"abcd").cast("B", shape)
        assert view.shape == (2, 2)

    def test_utime_ns_divmod_native_tuple(self):
        class NativeDivmod:
            def __divmod__(self, other):
                assert other == 1000000000
                result = TupleSubclass(1, 234567890)
                assert is_native_object(result)
                return result

        with tempfile.NamedTemporaryFile() as f:
            os.utime(f.name, ns=(NativeDivmod(), NativeDivmod()))
            stat = os.stat(f.name)
            expected = 1234567890
            # We don't care about the exact value. It's about if native tuples are accepted.
            tolerance = 1_000_000_000
            assert abs(stat.st_atime_ns - expected) <= tolerance
            assert abs(stat.st_mtime_ns - expected) <= tolerance

    def test_percent_format_native_tuple(self):
        args = TupleSubclass("native", 7)
        assert is_native_object(args)
        assert "%s:%d" % args == "native:7"

        bytes_args = TupleSubclass(b"native", 7)
        assert is_native_object(bytes_args)
        assert b"%s:%d" % bytes_args == b"native:7"

    def test_incremental_newline_decoder_getstate_native_tuple(self):
        class Decoder:
            def getstate(self):
                state = TupleSubclass(b"", 0)
                assert is_native_object(state)
                return state

        decoder = _io.IncrementalNewlineDecoder(Decoder(), translate=False)
        assert decoder.getstate() == (b"", 0)

    def test_incremental_newline_decoder_setstate_native_tuple(self):
        state = TupleSubclass(b"buffer", 1)
        assert is_native_object(state)

        decoder = _io.IncrementalNewlineDecoder(None, translate=False)
        decoder.setstate(state)
        assert decoder.getstate() == (b"", 1)

        class Decoder:
            def setstate(self, state):
                self.state = state

            def getstate(self):
                return self.state

        state = TupleSubclass(b"buffer", 3)
        assert is_native_object(state)

        wrapped_decoder = Decoder()
        decoder = _io.IncrementalNewlineDecoder(wrapped_decoder, translate=False)
        decoder.setstate(state)
        assert wrapped_decoder.state == (b"buffer", 1)
        assert decoder.getstate() == (b"buffer", 3)

    def test_functools_partial_setstate_native_tuple_args(self):
        args = TupleSubclass(2, 5)
        assert is_native_object(args)
        partial = functools.partial(int)
        partial.__setstate__((pow, args, None, None))
        assert partial() == 32

    def test_pickle_memo_accepts_native_tuple_value(self):
        value = TupleSubclass(11, "memo")
        assert is_native_object(value)
        pickler = pickle.Pickler(io.BytesIO())
        pickler.memo = {0: value}

    def test_json_encodes_native_tuple(self):
        array = TupleSubclass("native", 7)
        assert is_native_object(array)
        assert json.dumps(array, separators=(",", ":")) == '["native",7]'

    def test_itertools_cycle_setstate_native_tuple(self):
        state = TupleSubclass([], 0)
        assert is_native_object(state)
        cycle = itertools.cycle([1])
        cycle.__setstate__(state)
        assert next(cycle) == 1
