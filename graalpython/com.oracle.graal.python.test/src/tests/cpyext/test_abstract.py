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

import array
import collections
import sys

from . import CPyExtTestCase, CPyExtFunction, CPyExtType, unhandled_error_compare

__dir__ = __file__.rpartition("/")[0]


def _safe_check(v, type_check):
    try:
        return type_check(v)
    except:
        return False


def _reference_checknumber(args):
    v = args[0]
    if isinstance(v, str):
        return False
    return _safe_check(v, lambda x: isinstance(int(x), int)) or _safe_check(v, lambda x: isinstance(float(x), float))

def _reference_tobase(args):
    n = args[0]
    base = args[1]
    if base not in (2, 8, 10, 16):
        raise SystemError("PyNumber_ToBase: base must be 2, 8, 10 or 16")
    if not hasattr(n, "__index__"):
        raise TypeError
    b_index = n.__index__()
    if base == 2:
        return bin(b_index)
    elif base == 8:
        return oct(b_index)
    elif base == 10:
        return str(b_index)
    elif base == 16:
        return hex(b_index)

def _reference_index(args):
    v = args[0]
    if not hasattr(v, "__index__"):
        raise TypeError
    result = v.__index__()
    if not isinstance(result, int):
        raise TypeError
    return result


def _reference_asssize_t(args):
    v = args[0]
    err = args[1]
    result = -1
    return _reference_index((v,))


def _reference_next(args):
    iterObj = args[0]
    n = args[1]
    try:
        for i in range(n - 1):
            next(iterObj)
        return next(iterObj)
    except BaseException:
        raise SystemError

def raise_type_error():
    raise TypeError

def _reference_seq_size(args):
    seq = args[0]
           
    if istypeof(seq, [dict, type(type.__dict__)]):
        # checking for the exact type 
        # even if a dict or mappingproxy isn't accepted
        # a subclass with overriden __len__ is 
        raise_type_error()  
        
    return len(seq)

def _reference_mapping_size(args):
    m = args[0]
    
    if istypeof(m, [set, frozenset, collections.deque]):
        # checking for the exact type 
        # even if a set or deque isn't accepted
        # a subclass with overriden __len__ is 
        raise_type_error()            
    
    try:
        return len(m)    
    except:
        pass
    
    raise_type_error()
        
def _reference_object_size(args):
    obj = args[0]        
    
    try:
        return len(obj)
    except:
        pass
    
    raise_type_error()

def _reference_sequence_check(args):
    obj = args[0]

    if isinstanceof(obj, [dict, type(type.__dict__), set, frozenset]):
        return False  
    
    return hasattr(obj, '__getitem__')

def _reference_mapping_check(args):
    obj = args[0]
    
    if isinstanceof(obj, [collections.deque]):
        return False  
    
    if isinstanceof(obj, [array.array, bytearray, bytes, dict, type(type.__dict__), list, memoryview, range]):
        return True
    
    return hasattr(obj, '__getitem__')

def isinstanceof(obj, types):
    for t in types:
        if isinstance(obj, t):
            return True
    return False

def istypeof(obj, types):
    for t in types:
        if type(obj) == t:
            return True
    return False

def _reference_getitem(args):
    seq = args[0]
    idx = args[1]
    if not hasattr(seq, '__getitem__'):
        raise TypeError
    return seq.__getitem__(idx)


def _reference_setitem(args):
    seq = args[0]
    idx = args[1]
    value = args[2]
    if not hasattr(seq, '__setitem__'):
        raise TypeError
    seq.__setitem__(idx, value)
    return seq


def _reference_fast(args):
    obj = args[0]
    if isinstance(obj, tuple) or isinstance(obj, list):
        return obj
    return list(obj)

def _wrap_slice_fun(fun, since=0, default=None):
    def wrapped_fun(args):
        if not isinstance(args[0], list) and not isinstance(args[0], set) and not isinstance(args[0], tuple) and not isinstance(args[0], str):
            if sys.version_info.minor >= since:
                raise SystemError("expected list type")
            else:
                return default
        return fun(args)
    return wrapped_fun

class NoNumber():
    pass


class DummyIntable():

    def __int__(self):
        return 0xCAFE


class DummyIndexable():

    def __index__(self):
        return 0xCAFE


class DummyIntSubclass(int):

    def __int__(self):
        return 0xBABE


class DummyFloatable():

    def __float__(self):
        return 3.14159


class DummyFloatSubclass(float):

    def __float__(self):
        return 2.71828


class DummySequence():

    def __getitem__(self, idx):
        raise IndexError
    
class DummyLen():
    def __len__(self):
        return 42
    
class DummyDict(dict):
    pass

class DummyDictLen(dict):    
    def __len__(self):
        return 42
    

class DummyListSubclass(list):
    pass

class DummyListLen(list):    
    def __len__(self):
        return 42
    
class DummySet(set):    
    pass
    
class DummySetLen(set):    
    def __len__(self):
        return 42
    
class DummyDeque(set):    
    pass
    
class DummyDequeLen(set):    
    def __len__(self):
        return 42

def _default_bin_arith_args():
    return (
        (0, 0),
        (0, -1),
        (3, 2),
        (3, 64),
        (1<<32, 128),
        (10, 5),
        (29.3, 4.7),
        (0.3, -1.5),
        (False, 1),
        (False, 1.3),
        (True, 1),
        (True, 1.3),
        ("hello, ", "world"),
        ("hello, ", 3),
        ((1, 2, 3), (4, 5, 6)),
        ((1, 2, 3), 2),
        (0x7fffffff, 0x7fffffff),
        (0xffffffffffffffffffffffffffffffff, -1),
        (0xffffffffffffffffffffffffffffffff, 1024),
        (DummyIntable(), 0xBABE),
        (0xBABE, DummyIntable()),
        (DummyIntSubclass(), 0xCAFE),
        (0xCAFE, DummyIntSubclass()),
        (NoNumber(), 1),
        (4, NoNumber()),
    )


def _default_unarop_args():
    return (
        (0,),
        (-1,),
        (0.1,),
        (-1.3,),
        (False,),
        (True,),
        ("hello",),
        ("1",),
        ((1, 2, 3),),
        (0x7fffffff,),
        (0xffffffffffffffffffffffffffffffff,),
        (DummyIntable(),),
        (DummyIntSubclass(),),
        (DummyIndexable(), ),
        (NoNumber(),),
        (DummyFloatable(),),
        (DummyFloatSubclass(),),
    )

def _size_and_check_args():
        return (
            (42,),
            (object(),),
            (DummySequence(),),
            (DummyLen(),),
            (DummyListSubclass(),),
            (DummyListSubclass([1,2,3]),),
            ('hello',),
            (tuple(),),
            ((1, 2, 3),),
            ((None,),),
            ([],),
            (['a', 'b', 'c'],),
            ([None],),
            (DummyListLen(),),
            (DummyListLen((1,2,3)),),
            (set(),),
            (frozenset(),),
            (DummySet(),),
            (DummySet([1,2,3]),),
            (DummySetLen(),),
            (DummySetLen([1,2,3]),),
            ({1,2,3},),            
            (frozenset({1,2,3}),),
            (dict(),),
            ({'a':0, 'b':1},),
            (type.__dict__,), #mappingproxy
            (DummyDict(),),
            (DummyDict({'a1': 1, 'b': 2}),),
            (DummyDictLen(),),
            (DummyDictLen({'a2': 1, 'b': 2}),),
            (sys.modules,),
            (NoNumber(),),
            (range(2),),
            (b'abc',),
            (bytearray(b'abc'),),
            (memoryview(b'abc'),),
            (array.array('I', [1,2,3]),),
            (collections.deque([1,2,3,]),),  
            (DummyDeque(),),  
            (DummyDeque([1,2,3]),),  
            (DummyDequeLen(),),  
            (DummyDequeLen([1,2,3]),),              
        )
        
class TestAbstractWithNative(object):
    def test_sequence_check(self):
        TestSequenceCheck = CPyExtType("TestSequenceCheck",
                             """
                             PyObject* test_sq_item(PyObject *a, Py_ssize_t i) {
                                 Py_INCREF(a);
                                 return a;
                             }
                             PyObject* callCheck(PyObject* a) {
                                 return PyLong_FromLong(PySequence_Check(a));
                             }
                             """,
                             tp_methods='{"callCheck", (PyCFunction)callCheck, METH_O, ""}',
                             sq_item="&test_sq_item",
        )
        tester = TestSequenceCheck()
        assert tester.callCheck(tester)

    def test_sequence_size(self):
        TestSequenceSize = CPyExtType("TestSequenceSize",
                             """
                             Py_ssize_t test_sq_length(PyObject* a) {
                                 return 10;
                             }
                             PyObject* callSize(PyObject* a) {
                                 Py_ssize_t res = PySequence_Size(a);
                                 if (PyErr_Occurred()) {
                                     return NULL;
                                 }
                                 return PyLong_FromSsize_t(res);
                             }
                             """,
                             tp_methods='{"callSize", (PyCFunction)callSize, METH_O, ""}',
                             sq_length="&test_sq_length",
        )
        tester = TestSequenceSize()
        assert tester.callSize(tester) == 10
        
    def test_sequence_size_err(self):
        TestSequenceSizeErr = CPyExtType("TestSequenceSizeErr",
                             """
                             Py_ssize_t test_sq_length(PyObject* a) {
                                 PyErr_Format(PyExc_TypeError, "test type error");
                                 return -1;
                             }
                             PyObject* callSize(PyObject* a) {
                                 Py_ssize_t res = PySequence_Size(a);
                                 if (PyErr_Occurred()) {
                                     return NULL;
                                 }
                                 return PyLong_FromSsize_t(res);
                             }
                             """,
                             tp_methods='{"callSize", (PyCFunction)callSize, METH_O, ""}',
                             sq_length="&test_sq_length",
        )
        tester = TestSequenceSizeErr()        
        try:
            tester.callSize(tester)
        except TypeError:
            assert True
        else:
            assert False
            
    def test_sequence_size_err2(self):
        TestSequenceSizeErr2 = CPyExtType("TestSequenceSizeErr2",
                             """
                             Py_ssize_t test_sq_length(PyObject* a) {
                                 PyErr_Format(PyExc_TypeError, "test type error");
                                 return -2;
                             }
                             PyObject* callSize(PyObject* a) {
                                 Py_ssize_t res = PySequence_Size(a);
                                 if (PyErr_Occurred()) {
                                     return NULL;
                                 }
                                 return PyLong_FromSsize_t(res);
                             }
                             """,
                             tp_methods='{"callSize", (PyCFunction)callSize, METH_O, ""}',
                             sq_length="&test_sq_length",
        )
        tester = TestSequenceSizeErr2()        
        try:
            tester.callSize(tester)
        except TypeError:
            assert True
        else:
            assert False            
        
    def test_mapping_check(self):
        TestMappingCheck = CPyExtType("TestMappingCheck",
                             """
                             PyObject* test_mp_subscript(PyObject* a, PyObject* b) {
                                 Py_INCREF(a);
                                 return a;
                             }
                             PyObject* callCheck(PyObject* a) {
                                 return PyLong_FromLong(PyMapping_Check(a));
                             }
                             """,
                             tp_methods='{"callCheck", (PyCFunction)callCheck, METH_O, ""}',
                             mp_subscript="&test_mp_subscript",
        )
        tester = TestMappingCheck()
        assert tester.callCheck(tester)
        
    def test_mapping_size(self):
        TestMappingSize = CPyExtType("TestMappingSize",
                             """
                             Py_ssize_t test_mp_length(PyObject* a) {
                                 return 11;
                             }
                             PyObject* callSize(PyObject* a) {
                                 Py_ssize_t res = PyMapping_Size(a);
                                 if (PyErr_Occurred()) {
                                     return NULL;
                                 }
                                 return PyLong_FromSsize_t(res);
                             }
                             """,
                             tp_methods='{"callSize", (PyCFunction)callSize, METH_O, ""}',
                             mp_length="&test_mp_length",
        )
        tester = TestMappingSize()
        assert tester.callSize(tester) == 11
        
    def test_mapping_size_err(self):
        TestMappingSizeErr = CPyExtType("TestMappingSizeErr",
                             """
                             Py_ssize_t test_mp_length(PyObject* a) {
                                 PyErr_Format(PyExc_TypeError, "test type error");
                                 return -1;
                             }
                             PyObject* callSize(PyObject* a) {
                                 Py_ssize_t res = PyMapping_Size(a);
                                 if (PyErr_Occurred()) {
                                     return NULL;
                                 }
                                 return PyLong_FromSsize_t(res);
                             }
                             """,
                             tp_methods='{"callSize", (PyCFunction)callSize, METH_O, ""}',
                             mp_length="&test_mp_length",
        )
        tester = TestMappingSizeErr()   
        try:
            tester.callSize(tester)
        except TypeError:
            assert True
        else:
            assert False
        
    def test_object_size_sq(self):
        TestObjectSizeSQ = CPyExtType("TestObjectSizeSQ",
                             """
                             Py_ssize_t test_sq_length(PyObject* a) {
                                 return 12;
                             }
                             PyObject* callSize(PyObject* a) {
                                 Py_ssize_t res = PyObject_Size(a);
                                 if (PyErr_Occurred()) {
                                     return NULL;
                                 }
                                 return PyLong_FromSsize_t(res);
                             }
                             """,
                             tp_methods='{"callSize", (PyCFunction)callSize, METH_O, ""}',
                             sq_length="&test_sq_length",
        )
        tester = TestObjectSizeSQ()
        assert tester.callSize(tester) == 12
        
    def test_object_size_mp(self):
        TestObjectSizeMP = CPyExtType("TestObjectSizeMP",
                             """
                             Py_ssize_t test_sq_length(PyObject* a) {
                                 return 13;
                             }
                             PyObject* callSize(PyObject* a) {
                                 Py_ssize_t res = PyObject_Size(a);
                                 if (PyErr_Occurred()) {
                                     return NULL;
                                 }
                                 return PyLong_FromSsize_t(res);
                             }
                             """,
                             tp_methods='{"callSize", (PyCFunction)callSize, METH_O, ""}',
                             mp_length="&test_sq_length",
        )
        tester = TestObjectSizeMP()
        assert tester.callSize(tester) == 13            
        
    def test_object_size_err(self):
        TestObjectSizeErr = CPyExtType("TestObjectSizeErr",
                             """
                             Py_ssize_t test_sq_length(PyObject* a) {
                                 PyErr_Format(PyExc_TypeError, "test type error");
                                 return -1;
                             }
                             PyObject* callSize(PyObject* a) {
                                 Py_ssize_t res = PyObject_Size(a);
                                 if (PyErr_Occurred()) {
                                     return NULL;
                                 }
                                 return PyLong_FromSsize_t(res);
                             }
                             """,
                             tp_methods='{"callSize", (PyCFunction)callSize, METH_O, ""}',
                             mp_length="&test_sq_length",
        )
        tester = TestObjectSizeErr()
        try:
            tester.callSize(tester)
        except TypeError:
            assert True
        else:
            assert False

    def test_sequence_mapping_inheritance(self):
        NativeMapping = CPyExtType(
            "NativeMapping",
            '''
            Py_ssize_t mp_length(PyObject* a) {
                return 1;
            }

            PyObject* mp_subscript(PyObject* self, PyObject* key) {
                Py_INCREF(key);
                return key;
            }

            PyObject* is_mapping(PyObject* self, PyObject* object) {
                return PyBool_FromLong(PyMapping_Check(object));
            }
            ''',
            mp_length='mp_length',
            mp_subscript='mp_subscript',
            tp_methods='{"is_mapping", (PyCFunction)is_mapping, METH_O | METH_STATIC, ""}',
        )

        NativeSequence = CPyExtType(
            "NativeSequence",
            '''
            Py_ssize_t sq_length(PyObject* a) {
                return 1;
            }

            PyObject* sq_item(PyObject* self, Py_ssize_t index) {
                return PyLong_FromLong(index);
            }

            PyObject* is_sequence(PyObject* self, PyObject* object) {
                return PyBool_FromLong(PySequence_Check(object));
            }
            ''',
            sq_length='sq_length',
            sq_item='sq_item',
            tp_methods='{"is_sequence", (PyCFunction)is_sequence, METH_O | METH_STATIC, ""}',
        )

        class MappingSubclass(NativeMapping):
            pass

        mapping = MappingSubclass()
        assert len(mapping) == 1
        assert mapping['key'] == 'key'
        assert NativeMapping.is_mapping(mapping)
        assert not NativeSequence.is_sequence(mapping)

        class SequenceSubclass(NativeSequence):
            pass

        sequence = SequenceSubclass()
        assert len(sequence) == 1
        assert sequence[1] == 1
        assert NativeSequence.is_sequence(sequence)
        assert not NativeMapping.is_mapping(sequence)

    def test___name__(self):
        QualifiedType = CPyExtType("QualifiedType", tp_name="my.nested.package.QualifiedType")
        assert QualifiedType.__name__ == 'QualifiedType'


class TestAbstract(CPyExtTestCase):

    test_PyNumber_Absolute = CPyExtFunction(
        lambda args: abs(args[0]),
        _default_unarop_args,
        resultspec="O",
        argspec='O',
        arguments=["PyObject* v"],
        cmpfunc=unhandled_error_compare
    )

    test_PyNumber_Invert = CPyExtFunction(
        lambda args:~(args[0]),
        _default_unarop_args,
        resultspec="O",
        argspec='O',
        arguments=["PyObject* v"],
        cmpfunc=unhandled_error_compare
    )

    test_PyNumber_Check = CPyExtFunction(
        _reference_checknumber,
        _default_unarop_args,
        resultspec="i",
        argspec='O',
        arguments=["PyObject* v"],
        cmpfunc=unhandled_error_compare
    )

    test_PyNumber_ToBase = CPyExtFunction(
        _reference_tobase,
        lambda: (
            (2, 0),
            ("hello", 0),
            (1.1, 0),
            (NoNumber(), 0),
            (2, 0),
            (2, 0),
            (2, 2),
            (2, 8),
            (2, 10),
            (2, 16),
            ("1", 2),
            ("1", 8),
            ("1", 10),
            ("1", 16),
            (1.1, 2),
            (1.1, 8),
            (1.1, 10),
            (1.1, 16),
            (False, 2),
            (False, 8),
            (False, 10),
            (False, 16),
            (True, 16),
            ("hello, ", 2),
            ("hello, ", 8),
            ("hello, ", 10),
            ("hello, ", 16),
            (DummyIntable(), 2),
            (DummyIntable(), 8),
            (DummyIntable(), 10),
            (DummyIntable(), 16),
            (DummyIntSubclass(), 2),
            (DummyIntSubclass(), 8),
            (DummyIntSubclass(), 10),
            (DummyIntSubclass(), 16),
            (NoNumber(), 2),
            (NoNumber(), 8),
            (NoNumber(), 10),
            (NoNumber(), 16),
        ),
        resultspec="O",
        argspec='Oi',
        arguments=["PyObject* n", "int base"],
        cmpfunc=unhandled_error_compare
    )

    test_PyNumber_Add = CPyExtFunction(
        lambda args: args[0] + args[1],
        lambda: (
            (0, 0),
            (0, -1),
            (0.1, 0.0),
            (0.3, -1.5),
            (False, 1),
            (False, 1.3),
            (True, 1),
            (True, 1.3),
            ("hello, ", "world"),
            ((1, 2, 3), (4, 5, 6)),
            (0x7fffffff, 0x7fffffff),
            (0xffffffffffffffffffffffffffffffff, -1),
            (DummyIntable(), 0xCAFE),
            (DummyIntSubclass(), 0xBABE),
            (NoNumber(), 1),
        ),
        resultspec="O",
        argspec='OO',
        arguments=["PyObject* v", "PyObject* w"],
        cmpfunc=unhandled_error_compare
    )

    test_PyNumber_Subtract = CPyExtFunction(
        lambda args: args[0] - args[1],
        lambda: (
            (0, 0),
            (0, -1),
            (0.1, 0.0),
            (0.3, -1.5),
            (False, 1),
            (False, 1.3),
            (True, 1),
            (True, 1.3),
            ("hello, ", "world"),
            ((1, 2, 3), (4, 5, 6)),
            (0x7fffffff, 0x7fffffff),
            (0xffffffffffffffffffffffffffffffff, -1),
            (DummyIntable(), 0xCAFE),
            (DummyIntSubclass(), 0xBABE),
            (NoNumber(), 1),
        ),
        resultspec="O",
        argspec='OO',
        arguments=["PyObject* v", "PyObject* w"],
        cmpfunc=unhandled_error_compare
    )

    test_PyNumber_Multiply = CPyExtFunction(
        lambda args: args[0] * args[1],
        lambda: (
            (0, 0),
            (0, -1),
            (0.1, 0.0),
            (0.3, -1.5),
            (False, 1),
            (False, 1.3),
            (True, 1),
            (True, 1.3),
            ("hello, ", "world"),
            ("hello, ", 3),
            ("hello, ", 0),
            ((1, 2, 3), (4, 5, 6)),
            ((1, 2, 3), 2),
            ((1, 2, 3), 0),
            (0x7fffffff, 0x7fffffff),
            (0xffffffffffffffffffffffffffffffff, -1),
            (DummyIntable(), 2.3),
            (DummyIntSubclass(), 2.3),
            (NoNumber(), 2),
        ),
        resultspec="O",
        argspec='OO',
        arguments=["PyObject* v", "PyObject* w"],
        cmpfunc=unhandled_error_compare
    )

    test_PyNumber_TrueDivide = CPyExtFunction(
        lambda args: args[0] / args[1],
        _default_bin_arith_args,
        resultspec="O",
        argspec='OO',
        arguments=["PyObject* v", "PyObject* w"],
        cmpfunc=unhandled_error_compare
    )

    test_PyNumber_FloorDivide = CPyExtFunction(
        lambda args: args[0] // args[1],
        _default_bin_arith_args,
        resultspec="O",
        argspec='OO',
        arguments=["PyObject* v", "PyObject* w"],
        cmpfunc=unhandled_error_compare
    )

    test_PyNumber_Divmod = CPyExtFunction(
        lambda args: divmod(args[0], args[1]),
        _default_bin_arith_args,
        resultspec="O",
        argspec='OO',
        arguments=["PyObject* v", "PyObject* w"],
        cmpfunc=unhandled_error_compare
    )

    test_PyNumber_Remainder = CPyExtFunction(
        lambda args: args[0] % args[1],
        _default_bin_arith_args,
        resultspec="O",
        argspec='OO',
        arguments=["PyObject* v", "PyObject* w"],
        cmpfunc=unhandled_error_compare
    )

    test_PyNumber_Lshift = CPyExtFunction(
        lambda args: args[0] << args[1],
        lambda: (
            (0, 0),
            (0, -1),
            (3, 2),
            (10, 5),
            (29.3, 4.7),
            (0.3, -1.5),
            (False, 1),
            (False, 1.3),
            (True, 1),
            (True, 1.3),
            ("hello, ", "world"),
            ("hello, ", 3),
            ((1, 2, 3), (4, 5, 6)),
            ((1, 2, 3), 2),
            (0xffffffffffffffffffffffffffffffff, -1),
            (DummyIntable(), 0xBABE),
            (0xBABE, DummyIntable()),
            (DummyIntSubclass(), 0xCAFE),
            (0xCAFE, DummyIntSubclass()),
            (NoNumber(), 1),
            (4, NoNumber()),
            ),
        resultspec="O",
        argspec='OO',
        arguments=["PyObject* v", "PyObject* w"],
        cmpfunc=unhandled_error_compare
    )

    test_PyNumber_Rshift = CPyExtFunction(
        lambda args: args[0] >> args[1],
        _default_bin_arith_args,
        resultspec="O",
        argspec='OO',
        arguments=["PyObject* v", "PyObject* w"],
        cmpfunc=unhandled_error_compare
    )

    test_PyNumber_Or = CPyExtFunction(
        lambda args: args[0] | args[1],
        _default_bin_arith_args,
        resultspec="O",
        argspec='OO',
        arguments=["PyObject* v", "PyObject* w"],
        cmpfunc=unhandled_error_compare
    )

    test_PyNumber_And = CPyExtFunction(
        lambda args: args[0] & args[1],
        _default_bin_arith_args,
        resultspec="O",
        argspec='OO',
        arguments=["PyObject* v", "PyObject* w"],
        cmpfunc=unhandled_error_compare
    )

    test_PyNumber_Xor = CPyExtFunction(
        lambda args: args[0] ^ args[1],
        _default_bin_arith_args,
        resultspec="O",
        argspec='OO',
        arguments=["PyObject* v", "PyObject* w"],
        cmpfunc=unhandled_error_compare
    )

    test_PyNumber_Positive = CPyExtFunction(
        lambda args:+args[0],
        lambda: (
            (0,),
            (1,),
            (-1,),
            (0x7FFFFFFFFFFFFFF,),
            (DummyIntable(),),
            (DummyIntSubclass(),),
            (NoNumber(),),
        ),
        resultspec="O",
        argspec='O',
        arguments=["PyObject* v"],
        cmpfunc=unhandled_error_compare
    )

    test_PyNumber_Negative = CPyExtFunction(
        lambda args:-args[0],
        lambda: (
            (0,),
            (1,),
            (-1,),
            (0x7FFFFFFFFFFFFFF,),
            (DummyIntable(),),
            (DummyIntSubclass(),),
            (NoNumber(),),
        ),
        resultspec="O",
        argspec='O',
        arguments=["PyObject* v"],
        cmpfunc=unhandled_error_compare
    )

    test_PyNumber_Index = CPyExtFunction(
        _reference_index,
        lambda: (
            (0,),
            (1,),
            (-1,),
            (1.0,),
            ("1",),
            (0x7FFFFFFF,),
            (0x7FFFFFFFFFFFFFFF,),
            (DummyIntable(),),
            (DummyIntSubclass(),),
            (NoNumber(),),
        ),
        resultspec="O",
        argspec='O',
        arguments=["PyObject* v"],
        cmpfunc=unhandled_error_compare
    )

    test_PyNumber_AsSsize_t = CPyExtFunction(
        _reference_asssize_t,
        lambda: (
            (0, OverflowError),
            (1, ValueError),
            (1, OverflowError),
            (-1, OverflowError),
            (1.0, OverflowError),
            (0x7FFFFFFF, OverflowError),
            (0x7FFFFFFFFFFFFFFF, OverflowError),
            (DummyIntable(), OverflowError),
            (DummyIntSubclass(), OverflowError),
            (NoNumber(), OverflowError),
            (NoNumber(), ValueError),
        ),
        resultspec="n",
        argspec='OO',
        arguments=["PyObject* v", "PyObject* err"],
        cmpfunc=unhandled_error_compare
    )

    test_PyNumber_Long = CPyExtFunction(
        lambda args: int(args[0]),
        lambda: (
            (0,),
            (1,),
            (-1,),
            (1.0,),
            ("1",),
            (0x7FFFFFFF,),
            (0x7FFFFFFFFFFFFFFF,),
            (DummyIntable(),),
            (DummyIntSubclass(),),
            (NoNumber(),),
        ),
        resultspec="O",
        argspec='O',
        arguments=["PyObject* v"],
        cmpfunc=unhandled_error_compare
    )

    test_PyNumber_Float = CPyExtFunction(
        lambda args: float(args[0]),
        lambda: (
            (0,),
            (1,),
            (-1,),
            (1.0,),
            (0x7FFFFFFF,),
            (0x7FFFFFFFFFFFFFFF,),
            (DummyIntable(),),
            (DummyIntSubclass(),),
            (NoNumber(),),
        ),
        resultspec="O",
        argspec='O',
        arguments=["PyObject* v"],
        cmpfunc=unhandled_error_compare
    )

    test_PySequence_Fast_GET_SIZE = CPyExtFunction(
        lambda args: len(args[0]),
        lambda: (    
            (tuple(),),
            (list(),),
            ((1, 2, 3),),
            (("a", "b"),),
        ),
        resultspec="n",
        argspec='O',
        arguments=["PyObject* tuple"],
    )

    test_PySequence_Fast_GET_ITEM = CPyExtFunction(
        lambda args: args[0][args[1]],
        lambda: (
            ((1, 2, 3), 0),
            ((1, 2, 3), 1),
            ((1, 2, 3), 2),
            (['a', 'b', 'c'], 0),
            (['a', 'b', 'c'], 1),
            (['a', 'b', 'c'], 2),
        ),
        resultspec="O",
        argspec='On',
        arguments=["PyObject* sequence", "Py_ssize_t idx"],
    )

    test_PySequence_Fast_GET_SIZE = CPyExtFunction(
        lambda args: len(args[0]),
        lambda: (
            (tuple(),),
            ((1, 2, 3),),
            ((None,),),
            ([],),
            (['a', 'b', 'c'],),
            ([None],),
        ),
        resultspec="n",
        argspec='O',
        arguments=["PyObject* sequence"],
    )

    test_PySequence_Fast_ITEMS = CPyExtFunction(
        lambda args: list(args[0]),
        lambda: (
            (tuple(),),
            ((1, 2, 3),),
            ((None,),),
            ([],),
            (['a', 'b', 'c'],),
            ([None],),
        ),
        code='''PyObject* wrap_PySequence_Fast_ITEMS(PyObject* sequence) {
            Py_ssize_t i;
            Py_ssize_t n = PySequence_Fast_GET_SIZE(sequence);
            PyObject **items = PySequence_Fast_ITEMS(sequence);
            PyObject* result = PyList_New(n);
            for (i = 0; i < n; i++) {
                Py_INCREF(items[i]);
                PyList_SetItem(result, i, items[i]);
            }
            return result;
        }
        ''',
        resultspec="O",
        argspec='O',
        callfunction="wrap_PySequence_Fast_ITEMS",
        arguments=["PyObject* sequence"],
    )

    test_PyIter_Next = CPyExtFunction(
        _reference_next,
        lambda: (
            (iter((1, 2, 3)), 0),
            (iter((1, 2, 3)), 3),
            (iter((None,)), 1),
            (iter([]), 1),
            (iter(['a', 'b', 'c']), 2),
            (iter({'a':0, 'b':1, 'c':2}), 2)
        ),
        code='''PyObject* wrap_PyIter_Next(PyObject* iter, int n) {
            int i;
            for (i = 0; i < n - 1; i++) {
                PyIter_Next(iter);
            }
            return PyIter_Next(iter);
        }
        ''',
        resultspec="O",
        argspec='Oi',
        callfunction="wrap_PyIter_Next",
        arguments=["PyObject* sequence", "int n"],
        cmpfunc=unhandled_error_compare
    )

    test_PySequence_Check = CPyExtFunction(
        _reference_sequence_check,
        _size_and_check_args,
        resultspec="i",
        argspec='O',
        arguments=["PyObject* sequence"],
    )

    test_PySequence_Size = CPyExtFunction(
        _reference_seq_size,
        _size_and_check_args,
        resultspec="n",
        argspec='O',
        arguments=["PyObject* sequence"],
        cmpfunc=unhandled_error_compare
    )

    # 'PySequence_Length' is just a redefinition of 'PySequence_Size'
    test_PySequence_Length = test_PySequence_Size.copy()

    test_PySequence_GetItem = CPyExtFunction(
        _reference_getitem,
        lambda: (
            (tuple(), 10),
            ((1, 2, 3), 2),
            ((None,), 0),
            ([], 10),
            (['a', 'b', 'c'], 2),
            ([None], 0),
            (set(), 0),
            ({'a', 'b'}, 0),
            (DummyListSubclass(), 1),
            ('hello', 1),
        ),
        resultspec="O",
        argspec='On',
        arguments=["PyObject* sequence", "Py_ssize_t idx"],
        cmpfunc=unhandled_error_compare
    )
    
    test_PySequence_GetSlice = CPyExtFunction(
        _wrap_slice_fun(lambda args: args[0][args[1]:args[2]]),
        lambda: (
            (tuple(), 0, 1),
            ((1, 2, 3), 1, 2),
            ((None,), 1, 2),
            ([], 0, 1),
            (['a', 'b', 'c'], 1, 2),
            ([None], 0, 1),
            (set(), 0, 1),
            ({'a', 'b'}, 1, 2),
            (DummyListSubclass(), 0, 1),
            ('hello', 0, 1),
        ),
        resultspec="O",
        argspec='Onn',
        arguments=["PyObject* sequence", "Py_ssize_t ilow", "Py_ssize_t ihigh"],
        cmpfunc=unhandled_error_compare
    )
    
    test_PySequence_Contains = CPyExtFunction(
        lambda args: args[1] in args[0],
        lambda: (
            (tuple(), 1),
            ((1, 2, 3), 1),
            ((1, 2, 3), 4),
            ((None,), 1),
            ([], 1),
            (['a', 'b', 'c'], 'a'),
            (['a', 'b', 'c'], 'd'),
            ([None], 1),
            (set(), 1),
            ({'a', 'b'}, 'a'),
            ({'a', 'b'}, 'c'),
            (DummyListSubclass(), 1),
            ('hello', 'e'),
            ('hello', 'x'),
        ),
        resultspec="i",
        argspec='OO',
        arguments=["PyObject* haystack", "PyObject* needle"],
        cmpfunc=unhandled_error_compare
    )

    def index_func(args):
        for idx,obj in enumerate(args[0]):
            if obj == args[1]:
                return idx
        raise ValueError

    test_PySequence_Index = CPyExtFunction(
        index_func,
        lambda: (
            (tuple(), 1),
            ((1, 2, 3), 1),
            ((1, 2, 3), 4),
            ((None,), 1),
            ([], 1),
            (['a', 'b', 'c'], 'a'),
            (['a', 'b', 'c'], 'd'),
            ([None], 1),
            (set(), 1),
            ({'a', 'b'}, 'a'),
            ({'a', 'b'}, 'c'),
            (DummyListSubclass(), 1),
            ('hello', 'e'),
            ('hello', 'x'),
        ),
        resultspec="i",
        argspec='OO',
        arguments=["PyObject* haystack", "PyObject* needle"],
        cmpfunc=unhandled_error_compare
    )

    def count_func(args):
        c = 0
        for obj in args[0]:
            if obj == args[1]:
                c += 1
        return c

    test_PySequence_Count = CPyExtFunction(
        count_func,
        lambda: (
            (tuple(), 1),
            ((1, 2, 3), 1),
            ((1, 2, 3), 4),
            ((None,), 1),
            ([], 1),
            (['a', 'b', 'c'], 'a'),
            (['a', 'b', 'c'], 'd'),
            ([None], 1),
            (set(), 1),
            ({'a', 'b'}, 'a'),
            ({'a', 'b'}, 'c'),
            (DummyListSubclass(), 1),
            ('hello', 'e'),
            ('hello', 'x'),
        ),
        resultspec="i",
        argspec='OO',
        arguments=["PyObject* haystack", "PyObject* needle"],
        cmpfunc=unhandled_error_compare
    )

    def _reference_setslice(args):
        args[0][args[1]:args[2]] = args[3]
        return 0;

    test_PySequence_SetSlice = CPyExtFunction(
        _reference_setslice,
        lambda: (
            ([1,2,3,4],0,4,[5,6,7,8]),
            ([],1,2, [5,6]),
            ([1,2,3,4],10,20,[5,6,7,8]),
        ),
        resultspec="i",
        argspec='OnnO',
        arguments=["PyObject* op", "Py_ssize_t ilow", "Py_ssize_t ihigh", "PyObject* v"],
        cmpfunc=unhandled_error_compare
    )

    def _reference_delslice(args):
        del args[0][args[1]:args[2]]
        return 0;

    test_PySequence_DelSlice = CPyExtFunction(
        _reference_delslice,
        lambda: (
            ([1,2,3,4],0,4),
            ([],1,2),
            ([1,2,3,4],10,20),
        ),
        resultspec="i",
        argspec='Onn',
        arguments=["PyObject* op", "Py_ssize_t ilow", "Py_ssize_t ihigh"],
        cmpfunc=unhandled_error_compare
    )

    test_PySequence_ITEM = CPyExtFunction(
        _reference_getitem,
        lambda: (
            (tuple(), 10),
            ((1, 2, 3), 2),
            ((None,), 0),
            ([], 10),
            (['a', 'b', 'c'], 2),
            ([None], 0),
            ('hello', 0),
        ),
        resultspec="O",
        argspec='On',
        arguments=["PyObject* sequence", "Py_ssize_t idx"],
        cmpfunc=unhandled_error_compare
    )

    test_PySequence_SetItem = CPyExtFunction(
        _reference_setitem,
        lambda: (
            (tuple(), 0, 'a'),
            ((1, 2, 3), 2, 99),
            ((None,), 1, None),
            ([], 10, 1),
            (['a', 'b', 'c'], 2, 'z'),
            ('hello', 2, 'z'),
        ),
        code=''' PyObject* wrap_PySequence_SetItem(PyObject* sequence, Py_ssize_t idx, PyObject* value) {
            if (PySequence_SetItem(sequence, idx, value) < 0) {
                return NULL;
            }
            return sequence;
        }
        ''',
        resultspec="O",
        argspec='OnO',
        arguments=["PyObject* sequence", "Py_ssize_t idx", "PyObject* value"],
        callfunction="wrap_PySequence_SetItem",
        cmpfunc=unhandled_error_compare
    )

    test_PySequence_Tuple = CPyExtFunction(
        lambda args: tuple(args[0]),
        lambda: (
            (tuple(),),
            ((1, 2, 3),),
            ((None,),),
            ([],),
            (['a', 'b', 'c'],),
            ({'a', 'b', 'c'},),
            ({'a': 0, 'b': 1, 'c': 2},),
            (None,),
            (0,),
            ('hello',),
        ),
        resultspec="O",
        argspec='O',
        arguments=["PyObject* sequence"],
        cmpfunc=unhandled_error_compare
    )
    
    test_PySequence_List = CPyExtFunction(
        lambda args: list(args[0]),
        lambda: (
            (list(),),
            ((1, 2, 3),),
            ((None,),),
            ([],),
            (['a', 'b', 'c'],),
            ({'a', 'b', 'c'},),
            ({'a': 0, 'b': 1, 'c': 2},),
            (None,),
            (0,),
            ('hello',),
        ),
        resultspec="O",
        argspec='O',
        arguments=["PyObject* sequence"],
        cmpfunc=unhandled_error_compare
    )

    test_PySequence_Fast = CPyExtFunction(
        _reference_fast,
        lambda: (
            (tuple(), "should not be an error"),
            ((1, 2, 3), "should not be an error"),
            ((None,), "should not be an error"),
            ([], "should not be an error"),
            (['a', 'b', 'c'], "should not be an error"),
            ({'a', 'b', 'c'}, "should not be an error"),
            ({'a': 0, 'b': 1, 'c': 2}, "should not be an error"),
            (None, "None cannot be a sequence"),
            (0, "int cannot be a sequence"),
        ),
        resultspec="O",
        argspec='Os',
        arguments=["PyObject* sequence", "char* error_msg"],
        cmpfunc=unhandled_error_compare
    )

    test_PyMapping_GetItemString = CPyExtFunction(
        lambda args: args[0][args[1]],
        lambda: (
            (tuple(), "hello"),
            ((1, 2, 3), "1"),
            (['a', 'b', 'c'], "nothing"),
            ({'a', 'b', 'c'}, "a"),
            ({'a': 0, 'b': 1, 'c': 2}, "nothing"),
            ({'a': 0, 'b': 1, 'c': 2}, "c"),
        ),
        resultspec="O",
        argspec='Os',
        arguments=["PyObject* mapping", "char* keyStr"],
        cmpfunc=unhandled_error_compare
    )

    test_PyMapping_Check = CPyExtFunction(
        _reference_mapping_check,
        _size_and_check_args,
        resultspec="i",
        argspec='O',
        arguments=["PyObject* mapping"],
    )
    
    test_PyMapping_Size = CPyExtFunction(
        _reference_mapping_size,
        _size_and_check_args,
        resultspec="n",
        argspec='O',
        arguments=["PyObject* mapping"],
        cmpfunc=unhandled_error_compare
    )    
    
    test_PyIndex_Check = CPyExtFunction(
        lambda args: hasattr(args[0], "__index__"),
        lambda: (
            (1,),
            ("not a number",),
            (tuple(),),
            (dict(),),
            (list(),),
            (DummyFloatable(),),
            (DummyFloatSubclass(),),
            (DummyIntable(),),
            (DummyIntSubclass(),),
            (NoNumber(),),
        ),
        resultspec="i",
        argspec='O',
        arguments=["PyObject* obj"],
        cmpfunc=unhandled_error_compare
    )

    test_PySequence_Repeat = CPyExtFunction(
        lambda args: args[0] * args[1],
        lambda: (
            ((1,), 0),
            ((1,), 1),
            ((1,), 3),
            ([1], 0),
            ([1], 1),
            ([1], 3),
            ("hello", 0),
            ("hello", 1),
            ("hello", 3),
            ({}, 0),
        ),
        resultspec="O",
        argspec='On',
        arguments=["PyObject* obj", "Py_ssize_t n"],
        cmpfunc=unhandled_error_compare
    )

    test_PySequence_InPlaceRepeat = CPyExtFunction(
        lambda args: args[0] * args[1],
        lambda: (
            ((1,), 0),
            ((1,), 1),
            ((1,), 3),
            ([1], 0),
            ([1], 1),
            ([1], 3),
            ("hello", 0),
            ("hello", 1),
            ("hello", 3),
            ({}, 0),
        ),
        resultspec="O",
        argspec='On',
        arguments=["PyObject* obj", "Py_ssize_t n"],
        cmpfunc=unhandled_error_compare
    )

    test_PySequence_Concat = CPyExtFunction(
        lambda args: args[0] + args[1],
        lambda: (
            ((1,), tuple()),
            ((1,), list()),
            ((1,), (2,)),
            ((1,), [2,]),
            ([1], tuple()),
            ([1], list()),
            ([1], (2,)),
            ([1], [2,]),
            ("hello", "world"),
            ("hello", ""),
            ({}, []),
            ([], {}),
        ),
        resultspec="O",
        argspec='OO',
        arguments=["PyObject* s", "PyObject* o"],
        cmpfunc=unhandled_error_compare
    )

    test_PySequence_InPlaceConcat = CPyExtFunction(
        lambda args: args[0] + list(args[1]) if isinstance(args[0], list) else args[0] + args[1],
        lambda: (
            ((1,), tuple()),
            ((1,), list()),
            ((1,), (2,)),
            ((1,), [2,]),
            ([1], tuple()),
            ([1], list()),
            ([1], (2,)),
            ([1], [2,]),
            ("hello", "world"),
            ("hello", ""),
            ({}, []),
            ([], {}),
        ),
        resultspec="O",
        argspec='OO',
        arguments=["PyObject* s", "PyObject* o"],
        cmpfunc=unhandled_error_compare
    )

    test_PyObject_Size = CPyExtFunction(
        _reference_object_size,
        _size_and_check_args,
        resultspec="n",
        argspec='O',
        arguments=["PyObject* obj"],
        cmpfunc=unhandled_error_compare
    )

    test_PyObject_Length = test_PyObject_Size.copy()
