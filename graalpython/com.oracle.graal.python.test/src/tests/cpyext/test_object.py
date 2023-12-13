# Copyright (c) 2018, 2023, Oracle and/or its affiliates. All rights reserved.
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

from . import CPyExtType, CPyExtTestCase, CPyExtFunction, GRAALPYTHON, unhandled_error_compare, assert_raises

__dir__ = __file__.rpartition("/")[0]


def _reference_bytes(args):
    obj = args[0]
    if type(obj) == bytes:
        return obj
    if hasattr(obj, "__bytes__"):
        res = obj.__bytes__()
        if not isinstance(res, bytes):
            raise TypeError("__bytes__ returned non-bytes (type %s)" % type(res).__name__)
        return res
    if isinstance(obj, (list, tuple, memoryview)) or (not isinstance(obj, str) and hasattr(obj, "__iter__")):
        return bytes(obj)
    raise TypeError("cannot convert '%s' object to bytes" % type(obj).__name__)

def _reference_hash(args):
    return hash(args[0])


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

    def test_inherit_slots_with_managed_class(self):
        ClassWithTpAlloc = CPyExtType(
            "ClassWithTpAlloc",
            """
            static PyObject *test_alloc(PyTypeObject *type, Py_ssize_t nitems) {
                PyErr_SetString(PyExc_RuntimeError, "Should not call this tp_alloc");
                return NULL;
            }
            static PyObject* testslots_tp_alloc(PyObject* self, PyObject *cls) {
                return ((PyTypeObject *)cls)->tp_alloc((PyTypeObject *) cls, 0);
            }
            """,
            tp_alloc="test_alloc",
            tp_methods='{"call_tp_alloc", (PyCFunction)testslots_tp_alloc, METH_O | METH_STATIC, ""}',
            tp_flags='Py_TPFLAGS_DEFAULT | Py_TPFLAGS_HEAPTYPE | Py_TPFLAGS_BASETYPE | Py_TPFLAGS_HAVE_GC',
            tp_free='PyObject_GC_Del',
            ready_code="ClassWithTpAllocType.tp_new = PyBaseObject_Type.tp_new;"
        )

        class ManagedSubclass(ClassWithTpAlloc):
            pass

        assert ClassWithTpAlloc.call_tp_alloc(ManagedSubclass)
        assert type(ManagedSubclass()) is ManagedSubclass
        assert type(object.__new__(ManagedSubclass)) is ManagedSubclass
        

    def test_float_binops(self):
        TestFloatBinop = CPyExtType("TestFloatBinop",
                             """
                             PyObject* test_float_impl(PyObject* self) {
                                 PyErr_SetString(PyExc_RuntimeError, "Should not call __float__");
                                 return NULL;
                             }
                             PyObject* test_add_impl(PyObject* a, PyObject* b) {
                                 return PyLong_FromLong(42);
                             }
                             PyObject* test_sub_impl(PyObject* a, PyObject* b) {
                                 return PyLong_FromLong(4242);
                             }
                             PyObject* test_mul_impl(PyObject* a, PyObject* b) {
                                 return PyLong_FromLong(424242);
                             }
                             PyObject* test_pow_impl(PyObject* a, PyObject* b, PyObject* c) {
                                 return PyLong_FromLong(42424242);
                             }
                             """,
                             nb_float="test_float_impl",
                             nb_add="test_add_impl",
                             nb_subtract="test_sub_impl",
                             nb_multiply="test_mul_impl",
                             nb_power="test_pow_impl"
        )
        x = TestFloatBinop()
        assert 10.0 + x == 42
        assert 10.0 - x == 4242
        assert 10.0 * x == 424242
        assert 10.0 ** x == 42424242

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

    def test_slots_binops(self):
        TestSlotsBinop = CPyExtType("TestSlotsBinop",
                             """
                             PyObject* test_int_impl(PyObject* self) {
                                 PyErr_SetString(PyExc_RuntimeError, "Should not call __int__");
                                 return NULL;
                             }
                             PyObject* test_index_impl(PyObject* self) {
                                 PyErr_SetString(PyExc_RuntimeError, "Should not call __index__");
                                 return NULL;
                             }
                             PyObject* test_mul_impl(PyObject* a, PyObject* b) {
                                 return PyLong_FromLong(42);
                             }
                             """,
                             nb_int="test_int_impl",
                             nb_index="test_index_impl",
                             nb_multiply="test_mul_impl"
        )
        assert [4, 2] * TestSlotsBinop() == 42

    def test_inheret_numbers_slots(self):
        X = CPyExtType("X_", 
                            '''
                            PyObject* test_add_impl(PyObject* a, PyObject* b) {
                                return PyLong_FromLong(42);
                            }

                            static PyNumberMethods A_number_methods = {
                                test_add_impl,
                            };

                            PyTypeObject A_Type = {
                                PyVarObject_HEAD_INIT(NULL, 0)
                                .tp_name = "A",
                                .tp_flags = Py_TPFLAGS_DEFAULT | Py_TPFLAGS_BASETYPE,
                                .tp_as_number = &A_number_methods,
                            };

                            PyTypeObject B_Type = {
                                PyVarObject_HEAD_INIT(NULL, 0)
                                .tp_name = "B",
                                .tp_flags = Py_TPFLAGS_DEFAULT | Py_TPFLAGS_BASETYPE,
                                .tp_base = &A_Type,
                            };
                            
                            static PyObject* create_B(PyObject* cls) {
                                return (&B_Type)->tp_alloc(&B_Type, 0);
                            }

                            static PyObject* B_has_add_slot(PyObject* cls) {
                                return Py_NewRef((&B_Type)->tp_as_number != NULL && (&B_Type)->tp_as_number->nb_add != NULL ? Py_True : Py_False);
                            }

                            ''',
                            tp_methods='''{"create_B", (PyCFunction)create_B, METH_NOARGS | METH_CLASS, ""},
                                            {"B_has_add_slot", (PyCFunction)B_has_add_slot, METH_NOARGS  | METH_CLASS, ""}''',
                            ready_code='''
                               if (PyType_Ready(&A_Type) < 0)
                                   return NULL;

                               if (PyType_Ready(&B_Type) < 0)
                                   return NULL;
                               ''',
                            )
        
        Y = CPyExtType("Y_", 
                            '''
                            PyObject* test_C_add_impl(PyObject* a, PyObject* b) {
                                return PyLong_FromLong(4242);
                            }

                            static PyNumberMethods C_number_methods = {
                                test_C_add_impl,
                            };

                            PyTypeObject C_Type = {
                                PyVarObject_HEAD_INIT(NULL, 0)
                                .tp_name = "C",
                                .tp_flags = Py_TPFLAGS_DEFAULT | Py_TPFLAGS_BASETYPE,
                                .tp_as_number = &C_number_methods,
                            };

                            PyTypeObject D_Type = {
                                PyVarObject_HEAD_INIT(NULL, 0)
                                .tp_name = "D",
                                .tp_flags = Py_TPFLAGS_DEFAULT | Py_TPFLAGS_BASETYPE,
                                .tp_base = &C_Type,
                            };

                            PyTypeObject E_Type = {
                                PyVarObject_HEAD_INIT(NULL, 0)
                                .tp_name = "E",
                                .tp_flags = Py_TPFLAGS_DEFAULT | Py_TPFLAGS_BASETYPE,
                                .tp_base = &D_Type,
                            };

                            static PyObject* create_E(PyObject* cls) {
                                return (&E_Type)->tp_alloc(&E_Type, 0);
                            }

                            static PyObject* E_has_add_slot(PyObject* cls) {
                                return Py_NewRef((&E_Type)->tp_as_number != NULL && (&E_Type)->tp_as_number->nb_add != NULL ? Py_True : Py_False);
                            }
                            ''',
                            tp_methods='''{"create_E", (PyCFunction)create_E, METH_NOARGS | METH_CLASS, ""},
                                            {"E_has_add_slot", (PyCFunction)E_has_add_slot, METH_NOARGS  | METH_CLASS, ""}''',
                            ready_code='''
                               if (PyType_Ready(&C_Type) < 0)
                                   return NULL;

                               if (PyType_Ready(&D_Type) < 0)
                                   return NULL;
                                   
                               if (PyType_Ready(&E_Type) < 0)
                                   return NULL;
                               ''',
                            )
        B = X.create_B()
        E = Y.create_E()
        assert B + E == 42
        assert E + B == 4242
        assert X.B_has_add_slot()
        assert Y.E_has_add_slot()

        # check dir & __dir__
        assert sorted(list(B.__dir__())) == dir(B)

    def test_managed_class_with_native_base(self):
        NativeModule = CPyExtType("NativeModule_", 
                            '''
                            PyTypeObject NativeBase_Type = {
                                PyVarObject_HEAD_INIT(&PyType_Type, 0)
                                .tp_name = "NativeModule_.NativeBase",
                                .tp_flags = Py_TPFLAGS_DEFAULT | Py_TPFLAGS_BASETYPE,
                                .tp_base = &PyModule_Type,
                            };

                            static PyObject* get_NativeBase_type(PyObject* cls) {
                                return (PyObject*) &NativeBase_Type;
                            }

                            ''',
                            tp_methods='''{"get_NativeBase_type", (PyCFunction)get_NativeBase_type, METH_NOARGS | METH_CLASS, ""}''',
                            ready_code='''
                               /* testing lazy type initialization */
                               // if (PyType_Ready(&NativeBase_Type) < 0)
                               //     return NULL;
                               ''',
                            )
        NativeBase = NativeModule.get_NativeBase_type()
        assert NativeBase
        class ManagedType(NativeBase):
            def __init__(self):
                super(ManagedType, self).__init__("DummyModuleName")
        assert ManagedType()

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

    def test_getattro_inheritance(self):
        TestWithGetattro = CPyExtType(
            'TestWithGetattro',
            '''
            static PyObject* getattro(PyObject* self, PyObject* key) {
                if (PyObject_IsTrue(key)) {
                    Py_INCREF(key);
                    return key;
                }
                return PyErr_Format(PyExc_AttributeError, "Nope");
            }

            static PyObject* call_getattro_slot(PyObject* unused, PyObject* args) {
                PyObject* object;
                PyObject* key;
                if (!PyArg_ParseTuple(args, "OO", &object, &key))
                    return NULL;
                return Py_TYPE(object)->tp_getattro(object, key);
            }
            ''',
            tp_getattro='getattro',
            tp_methods='{"call_getattro_slot", (PyCFunction)call_getattro_slot, METH_VARARGS | METH_STATIC, ""}'
        )

        def validate(cls, has_getattro, has_getattr):
            foo = cls()
            if has_getattro:
                assert foo.asdf == 'asdf'
                assert TestWithGetattro.call_getattro_slot(foo, 'asdf') == 'asdf'
            elif has_getattr:
                assert foo.asdf == 3
                assert TestWithGetattro.call_getattro_slot(foo, 'asdf') == 3
            if has_getattro and not has_getattr:
                assert_raises(AttributeError, lambda: getattr(foo, ''))
                assert_raises(AttributeError, TestWithGetattro.call_getattro_slot, foo, '')
            if has_getattr:
                assert getattr(foo, '') == 3
                assert TestWithGetattro.call_getattro_slot(foo, '') == 3

        validate(TestWithGetattro, True, False)

        class Subclass(TestWithGetattro):
            pass

        validate(Subclass, True, False)

        class ObjWithGetattr:
            def __getattr__(self, item):
                return 3

        validate(ObjWithGetattr, False, True)

        class SubclassWithGetattr(TestWithGetattro):
            def __getattr__(self, item):
                return 3

        validate(SubclassWithGetattr, True, True)

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

    def test_base_type(self):
        AcceptableBaseType = CPyExtType("AcceptableBaseType", 
                            '''
                            PyTypeObject TestBase_Type = {
                                PyVarObject_HEAD_INIT(NULL, 0)
                                .tp_name = "TestBase",
                                .tp_flags = Py_TPFLAGS_DEFAULT | Py_TPFLAGS_BASETYPE,
                            };

                            static int
                            AcceptableBaseType_traverse(AcceptableBaseTypeObject *self, visitproc visit, void *arg) {
                                // This helps to avoid setting 'Py_TPFLAGS_HAVE_GC'
                                // see typeobject.c:inherit_special:241
                                return 0;
                            }

                            static int
                            AcceptableBaseType_clear(AcceptableBaseTypeObject *self) {
                                // This helps to avoid setting 'Py_TPFLAGS_HAVE_GC'
                                // see typeobject.c:inherit_special:241
                                return 0;
                            }
                             ''',
                             tp_traverse="(traverseproc)AcceptableBaseType_traverse",
                             tp_clear="(inquiry)AcceptableBaseType_clear",
                             ready_code='''
                                TestBase_Type.tp_base = &PyType_Type;
                                if (PyType_Ready(&TestBase_Type) < 0)
                                    return NULL;
                                    
                                Py_SET_TYPE(&AcceptableBaseTypeType, &TestBase_Type); 
                                AcceptableBaseTypeType.tp_base = &PyType_Type;''',
                             )
        class Foo(AcceptableBaseType):
            # This shouldn't fail
            pass

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

    def test_init(self):
        TestInit = CPyExtType("TestInit", 
                             '''static PyObject* testnew_new(PyTypeObject* cls, PyObject* a, PyObject* b) {
                                 PyObject* obj;
                                 TestInitObject* typedObj;
                                 obj = PyBaseObject_Type.tp_new(cls, a, b);

                                 typedObj = ((TestInitObject*)obj);
                                 typedObj->dict = (PyDictObject*) PyDict_Type.tp_new(&PyDict_Type, a, b);
                                 PyDict_Type.tp_init((PyObject*) typedObj->dict, a, b);
                                 PyDict_SetItemString((PyObject*) typedObj->dict, "test", PyLong_FromLong(42));
                                 
                                 Py_XINCREF(obj);
                                 return obj;
                            }
                            static PyObject* get_dict_item(PyObject* self) {
                                PyObject* result = PyDict_GetItemString((PyObject*) ((TestInitObject*)self)->dict, "test");
                                Py_INCREF(result);
                                return result;
                            }
                             ''',
                             cmembers="PyDictObject *dict;",
                             tp_new="testnew_new",
                             tp_methods='{"get_dict_item", (PyCFunction)get_dict_item, METH_NOARGS, ""}'
                             )
        tester = TestInit()
        assert tester.get_dict_item() == 42

    def test_slots(self):
        TestSlots = CPyExtType("TestSlots", 
                               '''
                               static PyObject* testslots_bincomp(PyObject* cls) {
                                   return Py_NewRef(((PyTypeObject*)cls)->tp_basicsize == sizeof(TestSlotsObject) ? Py_True : Py_False);
                               }
                               ''',
                              includes='#include "datetime.h"',
                              cmembers="PyDateTime_DateTime __pyx_base;",
                              tp_methods='{"is_binary_compatible", (PyCFunction)testslots_bincomp, METH_NOARGS | METH_CLASS, ""}',
                              ready_code='''PyTypeObject* datetime_type = NULL;
                              PyDateTime_IMPORT;
                              datetime_type = PyDateTimeAPI->DateTimeType;
                              Py_XINCREF(datetime_type);
                              TestSlotsType.tp_base = (PyTypeObject*) datetime_type;
                              TestSlotsType.tp_new = datetime_type->tp_new;
                              ''')
        tester = TestSlots(1, 1, 1)
        assert tester.year == 1, "year was %s "% tester.year
        assert tester.is_binary_compatible()

    def test_subclasses(self):
        TestSubclasses = CPyExtType(
            'TestSubclasses',
            '''
            static PyObject* create_type(PyObject* unused, PyObject* args) {
                PyObject* bases;
                if (!PyArg_ParseTuple(args, "O", &bases))
                    return NULL;
                PyType_Slot slots[] = {
                    { 0 }
                };
                PyType_Spec spec = { "DynamicType", sizeof(PyHeapTypeObject), 0, Py_TPFLAGS_DEFAULT | Py_TPFLAGS_BASETYPE, slots };
                PyObject* result = PyType_FromSpecWithBases(&spec, bases);
                return result;
            }
            ''',
            tp_methods='{"create_type", (PyCFunction)create_type, METH_VARARGS | METH_STATIC, ""}'
        )

        class ManagedBase:
            pass
        DynamicType = TestSubclasses.create_type((ManagedBase,))
        assert ManagedBase.__subclasses__() == [DynamicType]

        def add(a, b):
            return 42
        ManagedBase.__add__ = add
        foo = DynamicType()
        assert foo + foo == 42

    def test_tp_name(self):
        TestTpName = CPyExtType("TestTpName",
                                '''
                                static PyObject* testslots_tp_name(PyObject* self, PyObject *cls) {
                                    return PyUnicode_FromString(((PyTypeObject*)cls)->tp_name);
                                }
                                ''',
                                tp_methods='{"get_tp_name", (PyCFunction)testslots_tp_name, METH_O, ""}',
                                )
        tester = TestTpName()
        class MyClass:
            pass
        assert tester.get_tp_name(MyClass) == 'MyClass'
        assert tester.get_tp_name(int) == 'int'
        assert tester.get_tp_name(type(tester)) == 'TestTpName.TestTpName'

    def test_tp_alloc(self):
        TestTpAlloc = CPyExtType("TestTpAlloc",
                                '''
                                static PyObject* testslots_tp_alloc(PyObject* self) {
                                    return (PyObject*) PyType_Type.tp_alloc(&PyType_Type, 0);
                                }
                                ''',
                                tp_methods='{"get_tp_alloc", (PyCFunction)testslots_tp_alloc, METH_NOARGS, ""}',
                                )
        tester = TestTpAlloc()
        assert tester.get_tp_alloc() != None

    def test_slots_initialized(self):
        TestSlotsInitialized = CPyExtType("TestSlotsInitialized", 
                              '''
                              static PyTypeObject* datetime_type = NULL;
                                
                              PyObject* TestSlotsInitialized_new(PyTypeObject* self, PyObject* args, PyObject* kwargs) {
                                  return Py_XNewRef(datetime_type->tp_new(self, args, kwargs));
                              }
                              ''',
                              includes='#include "datetime.h"',
                              cmembers="PyDateTime_DateTime __pyx_base;",
                              ready_code='''
                              PyDateTime_IMPORT;
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

        obj.__dict__ = {'a': 1}
        assert obj.a == 1

    def ignore_test_float_subclass(self):
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
                                           return Py_NewRef(Py_NotImplemented);
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
        assert hash(tester) != 0

    def test_float_subclass2(self):
        NativeFloatSubclass = CPyExtType(
            "NativeFloatSubclass",
            """
            static PyObject* fp_tp_new(PyTypeObject* type, PyObject* args, PyObject* kwds) {
                PyObject *result = PyFloat_Type.tp_new(type, args, kwds);
                NativeFloatSubclassObject *nfs = (NativeFloatSubclassObject *)result;
                nfs->myobval = PyFloat_AsDouble(result);
                return result;
            }

            static PyObject* fp_tp_repr(PyObject* self) {
                NativeFloatSubclassObject *nfs = (NativeFloatSubclassObject *)self;
                return PyUnicode_FromFormat("native %S", PyFloat_FromDouble(nfs->myobval));
            }
            """,
            struct_base="PyFloatObject base;",
            cmembers="double myobval;",
            tp_base="&PyFloat_Type",
            tp_new="fp_tp_new",
            tp_repr="fp_tp_repr"
        )
        class MyFloat(NativeFloatSubclass):
            pass
        assert MyFloat() == 0.0
        assert MyFloat(123.0) == 123.0
        assert repr(MyFloat()) == "native 0.0"
        assert repr(MyFloat(123.0)) == "native 123.0"

    def test_custom_basicsize(self):
        TestCustomBasicsize = CPyExtType("TestCustomBasicsize", 
                                      '''
                                          Py_ssize_t global_basicsize = -1;

                                          static PyObject* get_basicsize(PyObject* self, PyObject* ignored) {
                                              return PyLong_FromSsize_t(global_basicsize + 2 * sizeof(PyObject*));
                                          }
                                      ''',
                                      cmembers='''long long field0;
                                      int field1;
                                      ''',
                                      tp_methods='{"get_basicsize", (PyCFunction)get_basicsize, METH_NOARGS, ""}',
                                      post_ready_code="global_basicsize = TestCustomBasicsizeType.tp_basicsize;"
                                      )
        class TestCustomBasicsizeSubclass(TestCustomBasicsize):
            pass
        
        obj = TestCustomBasicsizeSubclass()

        expected_basicsize = obj.get_basicsize()
        actual_basicsize = TestCustomBasicsizeSubclass.__basicsize__
        assert expected_basicsize == actual_basicsize, "expected = %s, actual = %s" % (expected_basicsize, actual_basicsize)

    def test_tp_basicsize(self):
        TpBasicsize1Type = CPyExtType("TpBasicsize1",
                             '''
                                int vv = 0;

                                static PyObject* set_values(PyObject* oself) {
                                    TpBasicsize1Object * self = (TpBasicsize1Object *) oself;
                                    for (int i = 0; i < 20; i++) {
                                        self->f[i] = vv++;
                                    }
                                    Py_RETURN_NONE;
                                }
                                static PyObject* get_values(PyObject* self, PyObject* idx) {
                                    int i = (int)PyNumber_AsSsize_t(idx, NULL);
                                    return PyLong_FromLong(((TpBasicsize1Object *) self)->f[i]);
                                }
                            ''',
                            tp_methods='''
                            {"set_values", (PyCFunction)set_values, METH_NOARGS, NULL},
                            {"get_value", (PyCFunction)get_values, METH_O, NULL}
                            ''',
                            cmembers='Py_ssize_t f[20];',
        )

        TpBasicsize2Type = CPyExtType("TpBasicsize2",
                             '''
                                int vvv = 0;

                                static PyObject* set_values(PyObject* oself) {
                                    TpBasicsize2Object * self = (TpBasicsize2Object *) oself;
                                    for (int i = 0; i < 10; i++) {
                                        self->f[i] = vvv++;
                                    }
                                    Py_RETURN_NONE;
                                }
                                static PyObject* get_values(PyObject* self, PyObject* idx) {
                                    int i = (int)PyNumber_AsSsize_t(idx, NULL);
                                    return PyLong_FromLong(((TpBasicsize2Object *) self)->f[i]);
                                }
                            ''',
                            tp_methods='''
                            {"set_values", (PyCFunction)set_values, METH_NOARGS, NULL},
                            {"get_value", (PyCFunction)get_values, METH_O, NULL}
                            ''',
                            cmembers='Py_ssize_t f[10];',
        )
        
        TpBasicsize3Type = CPyExtType("TpBasicsize3",
                            '''
                            ''',
                            cmembers='',
        )
        
        try:
            class Foo(TpBasicsize2Type, TpBasicsize1Type):
                pass
        except TypeError:
            pass
        else:
            assert False, "should raise: TypeError: multiple bases have instance lay-out conflict"

        class Foo(TpBasicsize3Type, TpBasicsize1Type):
            pass

        assert Foo.__base__ == TpBasicsize1Type
        assert Foo.__basicsize__ == 192, "Foo.__basicsize__ %d != 192" % Foo.__basicsize__
        objs = [Foo() for i in range(5)]
        for foo in objs:
            foo.set_values()
        vv = 0
        for foo in objs:
            for i in range(20):
                assert foo.get_value(i) == vv
                vv += 1

        class Foo(TpBasicsize2Type, TpBasicsize3Type):
            pass

        assert Foo.__base__ == TpBasicsize2Type
        assert Foo.__basicsize__ == 112, "Foo.__basicsize__ %d != 112" % Foo.__basicsize__
        objs = [Foo() for i in range(5)]
        for foo in objs:
            foo.set_values()
        vvv = 0
        for foo in objs:
            for i in range(10):
                assert foo.get_value(i) == vvv, "Failed"
                vvv += 1

    def test_new_inherited_from_dominant_base(self):
        DominantBase = CPyExtType(
            'DominantBase',
            '''
            PyObject* base_new(PyTypeObject* type, PyObject* args, PyObject* kwargs) {
                return Py_NewRef(Py_Ellipsis);
            }
            ''',
            cmembers='int foo; int bar;',
            tp_new='base_new',
        )
        assert DominantBase() is Ellipsis

        WeakBase = CPyExtType('WeakBase')

        class Subclass(WeakBase, DominantBase):
            pass

        # In CPython 3.10, Subclass.__new__ is WeakBase.__new__, but Subclass.tp_new is DominantBase.tp_new

        assert Subclass() is Ellipsis

    def test_descrget(self):
        TestDescrGet = CPyExtType(
            "TestDescrGet",
            '''
            PyObject* testdescr_get(PyObject* self, PyObject* obj, PyObject* type) {
                if (obj == NULL) {
                    obj = Py_NewRef(Py_Ellipsis);
                }
                if (type == NULL) {
                    type = Py_NewRef(Py_Ellipsis);
                }
                return Py_BuildValue("OOO", self, obj, type);
            }
            ''',
            tp_descr_get="(descrgetfunc) testdescr_get",
        )

        descr = TestDescrGet()

        class Test:
            getter = descr

        obj = Test()
        # Using Ellipsis as a placeholder for C NULL
        assert obj.getter == (descr, obj, Test)
        assert Test.getter == (descr, ..., Test)
        assert descr.__get__(1, int) == (descr, 1, int)
        assert descr.__get__(1, None) == (descr, 1, ...)
        assert descr.__get__(None, int) == (descr, ..., int)

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


    def ignore_test_str_subclass(self):
        TestStrSubclass = CPyExtType("TestStrSubclass",
                                       r"""
                                       static PyTypeObject* testStrSubclassPtr = NULL;
                                    
                                        #define MAX_UNICODE 0x10ffff
                                    
                                        #define _PyUnicode_UTF8(op)                             \
                                            (((PyCompactUnicodeObject*)(op))->utf8)
                                        #define PyUnicode_UTF8(op)                              \
                                            (assert(_PyUnicode_CHECK(op)),                      \
                                             assert(PyUnicode_IS_READY(op)),                    \
                                             PyUnicode_IS_COMPACT_ASCII(op) ?                   \
                                                 ((char*)((PyASCIIObject*)(op) + 1)) :          \
                                                 _PyUnicode_UTF8(op))
                                        #define _PyUnicode_UTF8_LENGTH(op)                      \
                                            (((PyCompactUnicodeObject*)(op))->utf8_length)
                                        #define PyUnicode_UTF8_LENGTH(op)                       \
                                            (assert(_PyUnicode_CHECK(op)),                      \
                                             assert(PyUnicode_IS_READY(op)),                    \
                                             PyUnicode_IS_COMPACT_ASCII(op) ?                   \
                                                 ((PyASCIIObject*)(op))->length :               \
                                                 _PyUnicode_UTF8_LENGTH(op))
                                        #define _PyUnicode_WSTR(op)                             \
                                            (((PyASCIIObject*)(op))->wstr)
                                        #define _PyUnicode_WSTR_LENGTH(op)                      \
                                            (((PyCompactUnicodeObject*)(op))->wstr_length)
                                        #define _PyUnicode_LENGTH(op)                           \
                                            (((PyASCIIObject *)(op))->length)
                                        #define _PyUnicode_STATE(op)                            \
                                            (((PyASCIIObject *)(op))->state)
                                        #define _PyUnicode_HASH(op)                             \
                                            (((PyASCIIObject *)(op))->hash)
                                        #define _PyUnicode_KIND(op)                             \
                                            (assert(_PyUnicode_CHECK(op)),                      \
                                             ((PyASCIIObject *)(op))->state.kind)
                                        #define _PyUnicode_GET_LENGTH(op)                       \
                                            (assert(_PyUnicode_CHECK(op)),                      \
                                             ((PyASCIIObject *)(op))->length)
                                        #define _PyUnicode_DATA_ANY(op)                         \
                                            (((PyUnicodeObject*)(op))->data.any)
    
                                        // that's taken from CPython's 'PyUnicode_New'
                                        static PyUnicodeObject * new_empty_unicode(Py_ssize_t size, Py_UCS4 maxchar) {
                                            PyUnicodeObject *obj;
                                            PyCompactUnicodeObject *unicode;
                                            void *data;
                                            enum PyUnicode_Kind kind;
                                            int is_sharing, is_ascii;
                                            Py_ssize_t char_size;
                                            Py_ssize_t struct_size;
                                        
                                            is_ascii = 0;
                                            is_sharing = 0;
                                            struct_size = sizeof(PyCompactUnicodeObject);
                                            if (maxchar < 128) {
                                                kind = PyUnicode_1BYTE_KIND;
                                                char_size = 1;
                                                is_ascii = 1;
                                                struct_size = sizeof(PyASCIIObject);
                                            }
                                            else if (maxchar < 256) {
                                                kind = PyUnicode_1BYTE_KIND;
                                                char_size = 1;
                                            }
                                            else if (maxchar < 65536) {
                                                kind = PyUnicode_2BYTE_KIND;
                                                char_size = 2;
                                                if (sizeof(wchar_t) == 2)
                                                    is_sharing = 1;
                                            }
                                            else {
                                                if (maxchar > MAX_UNICODE) {
                                                    PyErr_SetString(PyExc_SystemError,
                                                                    "invalid maximum character passed to PyUnicode_New");
                                                    return NULL;
                                                }
                                                kind = PyUnicode_4BYTE_KIND;
                                                char_size = 4;
                                                if (sizeof(wchar_t) == 4)
                                                    is_sharing = 1;
                                            }
                                        
                                            /* Ensure we won't overflow the size. */
                                            if (size < 0) {
                                                PyErr_SetString(PyExc_SystemError,
                                                                "Negative size passed to PyUnicode_New");
                                                return NULL;
                                            }
                                            if (size > ((PY_SSIZE_T_MAX - struct_size) / char_size - 1))
                                                return NULL;
                                        
                                            /* Duplicated allocation code from _PyObject_New() instead of a call to
                                             * PyObject_New() so we are able to allocate space for the object and
                                             * it's data buffer.
                                             */
                                            obj = (PyUnicodeObject *) PyObject_MALLOC(struct_size + (size + 1) * char_size);
                                            if (obj == NULL)
                                                return NULL;
                                            obj = (PyUnicodeObject *) PyObject_INIT(obj, testStrSubclassPtr);
                                            if (obj == NULL)
                                                return NULL;
                                        
                                            unicode = (PyCompactUnicodeObject *)obj;
                                            if (is_ascii)
                                                data = ((PyASCIIObject*)obj) + 1;
                                            else
                                                data = unicode + 1;
                                            _PyUnicode_LENGTH(unicode) = size;
                                            _PyUnicode_HASH(unicode) = -1;
                                            _PyUnicode_STATE(unicode).interned = 0;
                                            _PyUnicode_STATE(unicode).kind = kind;
                                            _PyUnicode_STATE(unicode).compact = 1;
                                            _PyUnicode_STATE(unicode).ready = 1;
                                            _PyUnicode_STATE(unicode).ascii = is_ascii;
                                            if (is_ascii) {
                                                ((char*)data)[size] = 0;
                                                _PyUnicode_WSTR(unicode) = NULL;
                                            }
                                            else if (kind == PyUnicode_1BYTE_KIND) {
                                                ((char*)data)[size] = 0;
                                                _PyUnicode_WSTR(unicode) = NULL;
                                                _PyUnicode_WSTR_LENGTH(unicode) = 0;
                                                unicode->utf8 = NULL;
                                                unicode->utf8_length = 0;
                                            }
                                            else {
                                                unicode->utf8 = NULL;
                                                unicode->utf8_length = 0;
                                                if (kind == PyUnicode_2BYTE_KIND)
                                                    ((Py_UCS2*)data)[size] = 0;
                                                else /* kind == PyUnicode_4BYTE_KIND */
                                                    ((Py_UCS4*)data)[size] = 0;
                                                if (is_sharing) {
                                                    _PyUnicode_WSTR_LENGTH(unicode) = size;
                                                    _PyUnicode_WSTR(unicode) = (wchar_t *)data;
                                                }
                                                else {
                                                    _PyUnicode_WSTR_LENGTH(unicode) = 0;
                                                    _PyUnicode_WSTR(unicode) = NULL;
                                                }
                                            }
                                            return obj;
                                        }
                                        
                                       static PyObject* nstr_tpnew(PyTypeObject* type, PyObject* args, PyObject* kwargs) {
                                            char *ascii_data = NULL;
                                            Py_XINCREF(args);
                                            if (!PyArg_ParseTuple(args, "s", &ascii_data)) {{
                                                return NULL;
                                            }}
                                           Py_ssize_t len = strlen(ascii_data);
                                           PyUnicodeObject* strObj = new_empty_unicode(len, (Py_UCS4) 127);
                                           memcpy(PyUnicode_1BYTE_DATA(strObj), (Py_UCS1*)ascii_data, len);
                                           return (PyObject*) strObj;
                                       }
                                       """,
                                       cmembers="""PyUnicodeObject base;
                                                   int marker;""",
                                       tp_base="&PyUnicode_Type",
                                       tp_new="nstr_tpnew",
                                     ready_code="TestStrSubclassType.tp_richcompare = PyUnicode_Type.tp_richcompare;",
                                     post_ready_code="testStrSubclassPtr = &TestStrSubclassType; Py_INCREF(testStrSubclassPtr);"
                                       )
        tester = TestStrSubclass("hello\nworld")
        assert tester == "hello\nworld"
        assert str(tester) == "hello\nworld"
        assert tester.splitlines() == ['hello', 'world']
        assert tester >= "hello"
        assert not (tester >= "helloasdfasdfasdf")
        assert tester <= "helloasdfasdfasdf"
        assert not (tester <= "hello")
        assert tester.startswith("hello")
        assert tester.endswith("rld")
        assert tester.join(["a", "b"]) == "ahello\nworldb"
        assert tester.upper() == "HELLO\nWORLD"
        assert tester.replace("o", "uff") == "helluff\nwuffrld"
        assert tester.replace("o", "uff", 1) == "helluff\nworld"

    def test_doc(self):
        TestDoc = CPyExtType("TestDoc",
                                         '''
                                             Py_ssize_t global_basicsize = -1;
   
                                             static PyObject* some_member(PyObject* self) {
                                                 return PyLong_FromLong(42);
                                             }
                                         ''',
                                         tp_methods='{"some_member", (PyCFunction)some_member, METH_NOARGS, "This is some member that returns some value."}',
                                         )
        obj = TestDoc()
        expected_doc = "This is some member that returns some value."
        assert obj.some_member() == 42
        assert len(obj.some_member.__doc__) == len(expected_doc)
        assert obj.some_member.__doc__ == expected_doc

    def test_multiple_inheritance_with_native(self):
        _A = CPyExtType("_A","")
        class B:
            def __getattr__(self, name):
                return name
        class X(_A, B):
            b = 2
        x = X()
        assert x.foo == "foo"

    def test_getset(self):
        TestGetter = CPyExtType(
            "TestGetter",
            """
            static PyObject* foo_getter(PyObject* self, void* unused) {
                return PyUnicode_FromString("getter");
            }
            """,
            tp_getset='{"foo", foo_getter, (setter)NULL, NULL, NULL}',
        )
        obj = TestGetter()
        assert obj.foo == 'getter'

        def call_set():
            obj.foo = 'set'

        assert_raises(AttributeError, call_set)

        TestSetter = CPyExtType(
            "TestSetter",
            """
            static int state;

            static PyObject* foo_getter(PyObject* self, void* unused) {
                if (state == 0)
                    return PyUnicode_FromString("unset");
                else
                    return PyUnicode_FromString("set");
            }

            static int foo_setter(PyObject* self, PyObject* val, void* unused) {
                state = val != NULL;
                return 0;
            }
            """,
            tp_getset='{"foo", foo_getter, (setter)foo_setter, NULL, NULL}',
        )
        obj = TestSetter()
        assert obj.foo == 'unset'
        obj.foo = 'asdf'
        assert obj.foo == 'set'
        del obj.foo
        assert obj.foo == 'unset'

    def test_member_kind_precedence(self):
        TestWithConflictingMember1 = CPyExtType(
            "TestWithConflictingMember1",
            """
            static PyObject* foo_method(PyObject* self, PyObject* unused) {
                return PyUnicode_FromString("method");
            }

            static PyObject* foo_getter(PyObject* self, void* unused) {
                return PyUnicode_FromString("getter");
            }
            """,
            cmembers="PyObject* foo_member;",
            tp_members='{"foo", T_OBJECT, offsetof(TestWithConflictingMember1Object, foo_member), 0, NULL}',
            tp_methods='{"foo", foo_method, METH_NOARGS, ""}',
            tp_getset='{"foo", foo_getter, (setter)NULL, NULL, NULL}',
        )
        obj = TestWithConflictingMember1()
        assert obj.foo() == 'method'

        TestWithConflictingMember2 = CPyExtType(
            "TestWithConflictingMember2",
            """
            static PyObject* foo_getter(PyObject* self, void* unused) {
                return PyUnicode_FromString("getter");
            }
            """,
            cmembers="PyObject* foo_member;",
            tp_members='{"foo", T_OBJECT, offsetof(TestWithConflictingMember2Object, foo_member), 0, NULL}',
            tp_getset='{"foo", foo_getter, (setter)NULL, NULL, NULL}',
        )
        obj = TestWithConflictingMember2()
        assert obj.foo is None  # The member takes precedence

    def test_slot_precedence(self):
        MapAndSeq = CPyExtType("MapAndSeq",
                               '''
                               static PyObject * mas_nb_add(PyObject *self, PyObject *other) {
                                   return PyUnicode_FromString("mas_nb_add");
                               }
                               static Py_ssize_t mas_sq_length(PyObject *self) {
                                   return 111;
                               }
                               static PyObject *mas_sq_item(PyObject *self, Py_ssize_t idx) {
                                   return PyUnicode_FromString("sq_item");
                               }
                               static PyObject * mas_sq_concat(PyObject *self, PyObject *other) {
                                   return PyUnicode_FromString("mas_sq_concat");
                               }
                               static Py_ssize_t mas_mp_length(PyObject *self) {
                                   return 222;
                               }
                               static PyObject * mas_mp_subscript(PyObject *self, PyObject *key) {
                                   return PyUnicode_FromString("mp_subscript");
                               }
                               ''',
                               nb_add='mas_nb_add',
                               sq_length='mas_sq_length',
                               sq_item='mas_sq_item',
                               sq_concat='mas_sq_concat',
                               mp_length='mas_mp_length',
                               mp_subscript='mas_mp_subscript',
                               )
        obj = MapAndSeq()
        # Note: len(obj) uses 'PyObject_Lenght' which does not use the attribute but first tries
        # 'sq_length' and falls back to 'mp_length'. Therefore, we just look at '__len__' here.
        assert obj.__len__() == 222
        assert obj['hello'] == 'mp_subscript'
        assert obj + 'hello' == 'mas_nb_add'

    def test_take_ownership(self):
        import gc
        ValueType = CPyExtType(
            "Value",
            '''
            static PyObject* set_value(PyObject* self, PyObject *arg) {
                ValueObject *value_obj = (ValueObject *) self;
                Py_INCREF(arg);
                Py_XSETREF(value_obj->value, arg);
                Py_RETURN_NONE;
            }

            static PyObject* from_tuple(PyObject* self, PyObject *arg) {
                if (!PyTuple_CheckExact(arg)) {
                    PyErr_SetString(PyExc_TypeError, "arg must be a tuple");
                    return NULL;
                }
                // returns a borrowed ref
                PyObject *value = PyTuple_GetItem(arg, 0);
                return set_value(self, value);
            }

            static PyObject* get_value(PyObject* self) {
                ValueObject *value_obj = (ValueObject *) self;
                PyObject *res = value_obj->value;
                Py_INCREF(res);
                return res;
            }

            static PyObject* clear_value(PyObject* self) {
                ValueObject *value_obj = (ValueObject *) self;
                Py_XSETREF(value_obj->value, NULL);
                Py_RETURN_NONE;
            }

            static PyObject* own_a_lot(PyObject* self, PyObject *arg) {
                PyObject *dummy;
                int i;
                for (i=0; i < 64; i++) {
                    Py_INCREF(arg);
                    Py_DECREF(arg);
                    dummy = PyUnicode_FromString("abc");
                    Py_DECREF(dummy);
                    Py_INCREF(arg);
                    Py_DECREF(arg);
                }
                Py_RETURN_NONE;
            }

           ''',
           tp_methods='''
           {"set_value", (PyCFunction)set_value, METH_O, NULL},
           {"from_tuple", (PyCFunction)from_tuple, METH_O, NULL},
           {"get_value", (PyCFunction)get_value, METH_NOARGS, NULL},
           {"clear_value", (PyCFunction)clear_value, METH_NOARGS, NULL},
           {"own_a_lot", (PyCFunction)own_a_lot, METH_O, NULL}
           ''',
            cmembers='PyObject *value;',
        )

        dummy = object()
        obj = ValueType()
        obj.set_value(dummy)

        # dummy is also kept alive by Python code
        assert obj.get_value() is dummy

        # delete dummy here; should still be available from native
        r = repr(dummy)
        del dummy

        # no guarantee but increases chances that ref will be collected
        for _ in range(3):
            gc.collect()

        assert repr(obj.get_value()) == r
        obj.clear_value()

        # same as before but getting borrowed value from tuple
        dummy = object()
        obj.from_tuple((dummy, ))
        assert obj.get_value() is dummy
        r = repr(dummy)
        del dummy
        for _ in range(3):
            gc.collect()
        assert repr(obj.get_value()) == r
        obj.clear_value()

        dummy = object()
        obj.own_a_lot(dummy)


class CBytes: 
    def __bytes__(self):
        return b'abc'
    
class CBytesWrongReturn: 
    def __bytes__(self):
        return 'abc'
    
class DummyBytes(bytes): 
    pass     

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
            (CBytes(),),
            (CBytesWrongReturn(),),
            (DummyBytes(),),
            ([1,2,3],),
        ),
        arguments=["PyObject* obj"],
        resultspec="O",
        argspec="O",
        cmpfunc=unhandled_error_compare
    )

    test_Py_SIZE = CPyExtFunction(
        lambda args: args[1],
        lambda: (
            (0, 0),
            (1, 1),
            (False, 0),
            (True, 1),
            (-1, -1),
            (1, 1),
            (1<<29, 1),
            ((1<<30) - 1, 1),
            (1<<30, 2),
            (-1073741824, -2),
            ((1<<60) - 1, 2),
            (1<<60, 3),
            (-1152921504606846976, -3)
        ),
        code='''static Py_ssize_t wrap_Py_SIZE(PyObject* object, PyObject* unused) {
            return Py_SIZE(object);
        }
        ''',
        arguments=["PyObject* object", "PyObject* unused"],
        resultspec="n",
        argspec="OO",
        callfunction="wrap_Py_SIZE",
        cmpfunc=unhandled_error_compare
    )

    test_dealloc = CPyExtFunction(
        lambda args: None,
        lambda: (
            (None, ),
        ),
        code='''PyObject* dealloc_tuple(PyObject* element) {
            PyObject** native_storage = (PyObject**) malloc(sizeof(PyObject*));
            // returns a tuple with refcnt == 1
            PyObject* object = PyTuple_New(1);
            PyTuple_SetItem(object, 0, element);
            
            // seal tuple; refcnt == 2
            Py_INCREF(object);
            
            // this will force the object to native
            native_storage[0] = object;
            
            Py_DECREF(object);
            // this will free the tuple
            Py_DECREF(object);
            
            Py_RETURN_NONE;
        }
        ''',
        arguments=["PyObject* element"],
        resultspec="O",
        argspec="O",
        callfunction="dealloc_tuple",
        cmpfunc=unhandled_error_compare
    )

    class MyObject():
        def __hash__(self):
            return 42

    __MyObject_SINGLETON = MyObject()

    test_PyObject_Hash = CPyExtFunction(
        _reference_hash,
        lambda: (
            (0,),
            ("hello",),
            (memoryview(b"world"),),
            (1.234,),
            (bytearray(b"blah"),),
            ({1: 2, 3: 4},),
            ([1,2,3,4],),
            ({1,2,3,4},),
            (slice(1,100,2),),
            (TestObjectFunctions.__MyObject_SINGLETON,)
        ),
        arguments=["PyObject* obj"],
        resultspec="n",
        argspec="O",
        cmpfunc=unhandled_error_compare
    )


class TestPickleNative:
    def test_pickle_native(self):
        import pickle
        TestPicklable = CPyExtType("TestPicklable", "")
        assert type(pickle.loads(pickle.dumps(TestPicklable()))) == TestPicklable
        TestUnPicklable = CPyExtType("TestUnPicklable", "", cmembers="int foo;")
        try:
            pickle.dumps(TestUnPicklable())
        except TypeError:
            pass
        else:
            assert False, "Expected TypeError"
