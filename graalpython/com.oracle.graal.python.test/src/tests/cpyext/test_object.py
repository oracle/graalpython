# Copyright (c) 2018, 2019, Oracle and/or its affiliates. All rights reserved.
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

from . import CPyExtType, CPyExtTestCase, CPyExtFunction, GRAALPYTHON, unhandled_error_compare

__dir__ = __file__.rpartition("/")[0]


def _reference_bytes(args):
    obj = args[0]
    if type(obj) == bytes:
        return obj
    if hasattr(obj, "__bytes__"):
        res = obj.__bytes__()
        if not isinstance(res, bytes):
            raise TypeError("__bytes__ returned non-bytes (type %s)" % type(res).__name__)
    if isinstance(obj, (list, tuple, memoryview)) or (not isinstance(obj, str) and hasattr(obj, "__iter__")):
        return bytes(obj)
    raise TypeError("cannot convert '%s' object to bytes" % type(obj).__name__)


class AttroClass(object):
    def __getattribute__(self, key):
        if key == "foo":
            return "foo"
        else:
            return object.__getattribute__(self, key)


class TestObject(object):
    def test_add(self):
        TestAdd = CPyExtType("TestAdd",
                             """
                             PyObject* test_add(PyObject* a, PyObject* b) {
                                 return PyTuple_Pack(2, a, b);
                             }
                             """,
                             nb_add="test_add"
        )
        tester = TestAdd()
        assert tester + 12 == (tester, 12)

    def test_pow(self):
        TestPow = CPyExtType("TestPow",
                             """
                             PyObject* test_pow(PyObject* a, PyObject* b, PyObject* c) {
                                 return PyTuple_Pack(3, a, b, c);
                             }
                             """,
                             nb_power="test_pow"
        )
        tester = TestPow()
        assert tester ** 12 == (tester, 12, None), tester ** 12
        assert 12 ** tester == (12, tester, None), 12 ** tester
        assert pow(tester, 48, 2) == (tester, 48, 2), pow(tester, 48, 2)
        assert pow(48, tester, 2) == (48, tester, 2), pow(48, tester, 2)

    def test_int(self):
        TestInt = CPyExtType("TestInt",
                             """
                             PyObject* test_int(PyObject* self) {
                                 return PyLong_FromLong(42);
                             }
                             """,
                             nb_int="test_int"
        )
        tester = TestInt()
        assert int(tester) == 42

    def test_index(self):
        TestIndex = CPyExtType("TestIndex",
                             """
                             PyObject* test_index(PyObject* self) {
                                 return PyLong_FromLong(1);
                             }
                             """,
                             nb_index="test_index"
        )
        tester = TestIndex()
        assert [0, 1][tester] == 1

    def test_getattro(self):
        return  # TODO: not working yet
        # XXX: Cludge to get type into C
        sys.modules["test_getattro_AttroClass"] = AttroClass
        try:
            TestInt = CPyExtType("TestGetattro",
                                 """
                                 """,
                                 ready_code="""
                                 PyObject* AttroClass = PyDict_GetItemString(PyImport_GetModuleDict(), "test_getattro_AttroClass");
                                 TestGetattroType.tp_getattro = ((PyTypeObject*)AttroClass)->tp_getattro;
                                 """
            )
        finally:
            del sys.modules["test_getattro_AttroClass"]
        tester = TestInt()
        assert tester.foo == "foo"

    def test_dict(self):
        TestDict = CPyExtType("TestDict",
                             """static PyObject* custom_dict = NULL;
                             static PyObject* get_dict(PyObject* self, PyObject* kwargs) {
                                 Py_INCREF(custom_dict);
                                 return custom_dict;
                             }
                             """,
                             ready_code="""PyObject* descr;
                                 custom_dict = PyDict_New();
                                 Py_XINCREF(custom_dict);
                                 descr = PyUnicode_FromString("first custom property");
                                 Py_INCREF(descr);
                                 PyDict_SetItemString(custom_dict, "hello", descr);
                                 TestDictType.tp_dict = custom_dict;
                             """,
                             post_ready_code="""
                                 descr = PyUnicode_FromString("second custom property");
                                 Py_INCREF(descr);
                                 PyDict_SetItemString(TestDictType.tp_dict, "world", descr);
                             """,
                             tp_methods='{"get_dict", get_dict, METH_NOARGS, ""}'
        )
        tester = TestDict()
        assert tester.hello == "first custom property"
        assert tester.world == "second custom property"
        assert "hello" in tester.get_dict().keys() and "world" in tester.get_dict().keys(), "was: %s" % tester.get_dict().keys()
        tester.get_dict()["extra"] = "blah"
        assert tester.extra == "blah"

    def test_repr(self):
        TestRepr = CPyExtType("TestRepr", '')
        tester = TestRepr()
        try:
            repr(tester)
        except Exception:
            assert False
        assert True

    def test_new(self):
        TestNew = CPyExtType("TestNew", 
                             '''static PyObject* testnew_new(PyTypeObject* cls, PyObject* a, PyObject* b) {
                                 PyObject* obj;
                                 TestNewObject* typedObj;
                                 obj = PyBaseObject_Type.tp_new(cls, a, b);
                                 
                                 typedObj = ((TestNewObject*)obj);
                                 typedObj->none = Py_None;
                                 Py_INCREF(Py_None);
                                 Py_XINCREF(obj);
                                 return obj;
                            }
                            static PyObject* get_none(PyObject* self) {
                                return ((TestNewObject*)self)->none;
                            }
                             ''',
                             cmembers="PyObject* none;",
                             tp_new="testnew_new",
                             tp_methods='{"get_none", (PyCFunction)get_none, METH_NOARGS, ""}'
                             )
        tester = TestNew()
        assert tester.get_none() is None

    def test_slots(self):
        TestSlots = CPyExtType("TestSlots", 
                               '',
                              includes='#include "datetime.h"',
                              cmembers="PyDateTime_DateTime __pyx_base;",
                              ready_code='''PyTypeObject* datetime_type = NULL;
                              PyDateTime_IMPORT;
                              Py_INCREF(PyDateTimeAPI);
                              datetime_type = PyDateTimeAPI->DateTimeType;
                              Py_XINCREF(datetime_type);
                              TestSlotsType.tp_base = (PyTypeObject*) datetime_type;
                              TestSlotsType.tp_new = datetime_type->tp_new;
                              ''')
        tester = TestSlots(1, 1, 1)
        assert tester.year == 1, "year was %s "% tester.year

    def test_slots_initialized(self):
        TestSlotsInitialized = CPyExtType("TestSlotsInitialized", 
                              '''
                              static PyTypeObject* datetime_type = NULL;
                                
                              PyObject* TestSlotsInitialized_new(PyTypeObject* self, PyObject* args, PyObject* kwargs) {
                                  PyObject* result =  datetime_type->tp_new(self, args, kwargs);
                                  Py_XINCREF(result);
                                  return result;
                              }
                              ''',
                              includes='#include "datetime.h"',
                              cmembers="PyDateTime_DateTime __pyx_base;",
                              ready_code='''
                              PyDateTime_IMPORT;
                              Py_INCREF(PyDateTimeAPI);
                              datetime_type = PyDateTimeAPI->DateTimeType;
                              Py_INCREF(datetime_type);
                              TestSlotsInitializedType.tp_base = datetime_type;
                              ''',
                              tp_new="TestSlotsInitialized_new")
        tester = TestSlotsInitialized(2012, 4, 4)
        assert tester.year == 2012, "year was %s "% tester.year

    def test_no_dictoffset(self):
        TestNoDictoffset = CPyExtType("TestNoDictoffset", "")
        
        class TestNoDictoffsetSubclass(TestNoDictoffset):
            pass
        
        obj = TestNoDictoffsetSubclass()
        
        obj.__dict__["newAttr"] = 123
        assert obj.newAttr == 123, "invalid attr"

    def test_float_subclass(self):
        TestFloatSubclass = CPyExtType("TestFloatSubclass",
                                       """
                                       static PyTypeObject* testFloatSubclassPtr = NULL;
 
                                       static PyObject* new_fp(double val) {
                                           PyFloatObject* fp = PyObject_New(PyFloatObject, testFloatSubclassPtr);
                                           fp->ob_fval = val;
                                           return (PyObject*)fp;
                                       }
 
                                       static PyObject* fp_tpnew(PyTypeObject* type, PyObject* args, PyObject* kwargs) {
                                            double dval = 0.0;
                                            Py_XINCREF(args);
                                            if (!PyArg_ParseTuple(args, "d", &dval)) {{
                                                return NULL;
                                            }}
                                            return new_fp(dval);
                                       }
                                        
                                       static PyObject* fp_add(PyObject* l, PyObject* r) {
                                           if (PyFloat_Check(l)) {
                                               if (PyFloat_Check(r)) {
                                                   return new_fp(PyFloat_AS_DOUBLE(l) + PyFloat_AS_DOUBLE(r));
                                               } else if (PyLong_Check(r)) {
                                                   return new_fp(PyFloat_AS_DOUBLE(l) + PyLong_AsLong(r));
                                               }
                                           } else if (PyLong_Check(l)) {
                                               if (PyFloat_Check(r)) {
                                                   return new_fp(PyLong_AsLong(l) + PyFloat_AS_DOUBLE(r));
                                               } else if (PyLong_Check(r)) {
                                                   return new_fp(PyLong_AsLong(l) + PyLong_AsLong(r));
                                               }
                                           }
                                           return Py_NotImplemented;
                                       }
                                       """,
                                       cmembers="PyFloatObject base;",
                                       tp_base="&PyFloat_Type", 
                                       nb_add="fp_add",
                                       tp_new="fp_tpnew",
                                       post_ready_code="testFloatSubclassPtr = &TestFloatSubclassType; Py_INCREF(testFloatSubclassPtr);"
        )
        tester = TestFloatSubclass(41.0)
        res = tester + 1
        assert res == 42.0, "expected 42.0 but was %s" % res
        
    def test_custom_basicsize(self):
        TestCustomBasicsize = CPyExtType("TestCustomBasicsize", 
                                      '''
                                          Py_ssize_t global_basicsize = -1;

                                          static PyObject* get_basicsize(PyObject* self, PyObject* is_graalpython) {
                                              // The basicsize will be the struct's size plus a pointer to the object's dict and weaklist.
                                              // Graalpython does currently not implement the weaklist, so do not add in this case.
                                              if (PyObject_IsTrue(is_graalpython)) {
                                                  return PyLong_FromSsize_t(global_basicsize + sizeof(PyObject*));
                                              } else {
                                                  return PyLong_FromSsize_t(global_basicsize + 2 * sizeof(PyObject*));
                                              }
                                          }
                                      ''',
                                      cmembers='''long long field0;
                                      int field1;
                                      ''',
                                      tp_methods='{"get_basicsize", (PyCFunction)get_basicsize, METH_O, ""}',
                                      post_ready_code="global_basicsize = TestCustomBasicsizeType.tp_basicsize;"
                                      )
        class TestCustomBasicsizeSubclass(TestCustomBasicsize):
            pass
        
        obj = TestCustomBasicsizeSubclass()

        # TODO pass False as soon as we implement 'tp_weaklistoffset'
        expected_basicsize = obj.get_basicsize(GRAALPYTHON)
        actual_basicsize = TestCustomBasicsizeSubclass.__basicsize__
        assert expected_basicsize == actual_basicsize, "expected = %s, actual = %s" % (expected_basicsize, actual_basicsize)


    def test_descrset(self):
        TestDescrSet = CPyExtType("TestDescrSet",
                             '''
                             int testdescr_set(PyObject* self, PyObject* key, PyObject* value) {
                                     Py_XDECREF(((TestDescrSetObject*)self)->payload);
                                     Py_XINCREF(value);
                                     ((TestDescrSetObject*)self)->payload = value;
                                     return 0;
                             }

                             PyObject* testdescr_get(PyObject* self, PyObject* key, PyObject* type) {
                                     return ((TestDescrSetObject*)self)->payload;
                             }
                             ''',
                             cmembers='PyObject* payload;',
                             tp_descr_set="(descrsetfunc) testdescr_set",
                             tp_descr_get="(descrgetfunc) testdescr_get",
        )
        
        class Uff:
            hello = TestDescrSet()
        
        obj = Uff()
        obj.hello = "world"
        assert obj.hello == "world", 'expected "world" but was %s' % obj.hello

    def test_reverse_ops(self):
        TestReverseOps = CPyExtType("TestReverseOps",
                               """
                               PyObject* generic_nb(PyObject* self, PyObject* other) {
                                   return PyLong_FromLong(123);
                               }
                               """,
                               nb_add="generic_nb",
                               nb_subtract="generic_nb",
                               nb_multiply="generic_nb",
                               nb_remainder="generic_nb",
                               nb_divmod="generic_nb",
                               nb_floor_divide="generic_nb",
                               nb_true_divide="generic_nb",
                               nb_rshift="generic_nb",
                               nb_lshift="generic_nb",
                               nb_matrix_multiply="generic_nb",
                               )
        tester = TestReverseOps()
        assert 1 + tester == 123, "__radd__ failed"
        assert 1 - tester == 123, "__rsub__ failed"
        assert 1 * tester == 123, "__rmul__ failed"
        assert 1 / tester == 123, "__rtruediv__ failed"
        assert 1 // tester == 123, "__rfloordiv__ failed"
        assert 1 % tester == 123, "__rmod__ failed"
        assert 1 << tester == 123, "__rlshift__ failed"
        assert 1 >> tester == 123, "__rrshift__ failed"
        assert 1 @ tester == 123, "__rmatmul__ failed"


class TestObjectFunctions(CPyExtTestCase):
    def compile_module(self, name):
        type(self).mro()[1].__dict__["test_%s" % name].create_module(name)
        super().compile_module(name)

    test_PyCallable_Check = CPyExtFunction(
        lambda args: callable(args[0]),
        lambda: (
            (len,),
            (sum,),
            (int,),
            ("hello",),
            (3,),
            (None,),
        ),
        arguments=["PyObject* callable"],
        resultspec="i",
        argspec="O",
    )

    test_PyObject_Bytes = CPyExtFunction(
        _reference_bytes,
        lambda: (
            (0,),
            ("hello",),
            (memoryview(b"world"),),
            (1.234,),
            (bytearray(b"blah"),),
        ),
        arguments=["PyObject* obj"],
        resultspec="O",
        argspec="O",
        cmpfunc=unhandled_error_compare
    )

