# Copyright (c) 2024, 2025, Oracle and/or its affiliates. All rights reserved.
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
import operator
import sys

from . import CPyExtType, CPyExtHeapType, compile_module_from_string, assert_raises, compile_module_from_file

SlotsGetter = CPyExtType("SlotsGetter",
                         """
                         static PyObject* get_tp_attr(PyObject* unused, PyObject* object) {
                             return PyLong_FromVoidPtr(Py_TYPE(object)->tp_getattr);
                         }
                         static PyObject* get_tp_attro(PyObject* unused, PyObject* object) {
                             return PyLong_FromVoidPtr(Py_TYPE(object)->tp_getattro);
                         }
                         static PyObject* get_tp_setattr(PyObject* unused, PyObject* object) {
                             return PyLong_FromVoidPtr(Py_TYPE(object)->tp_setattr);
                         }
                         static PyObject* get_tp_setattro(PyObject* unused, PyObject* object) {
                             return PyLong_FromVoidPtr(Py_TYPE(object)->tp_setattro);
                         }
                         static PyObject* get_nb_bool(PyObject* unused, PyObject* object) {
                             return PyLong_FromVoidPtr(Py_TYPE(object)->tp_as_number == NULL ? NULL : Py_TYPE(object)->tp_as_number->nb_bool);
                         }
                         static PyObject* get_tp_as_number(PyObject* unused, PyObject* object) {
                             return PyLong_FromVoidPtr(Py_TYPE(object)->tp_as_number);
                         }
                         static PyObject* get_sq_concat(PyObject* unused, PyObject* object) {
                             return PyLong_FromVoidPtr(Py_TYPE(object)->tp_as_sequence == NULL ? NULL : Py_TYPE(object)->tp_as_sequence->sq_concat);
                         }
                         static PyObject* get_tp_descr_get(PyObject* unused, PyObject* object) {
                             return PyLong_FromVoidPtr(Py_TYPE(object)->tp_descr_get);
                         }
                         static PyObject* get_nb_add(PyObject* unused, PyObject* object) {
                             return PyLong_FromVoidPtr(Py_TYPE(object)->tp_as_number == NULL ? NULL : Py_TYPE(object)->tp_as_number->nb_add);
                         }
                         """,
                         tp_methods=
                         '{"get_tp_attr", (PyCFunction)get_tp_attr, METH_O | METH_STATIC, ""},' +
                         '{"get_tp_attro", (PyCFunction)get_tp_attro, METH_O | METH_STATIC, ""},' +
                         '{"get_tp_setattr", (PyCFunction)get_tp_setattr, METH_O | METH_STATIC, ""},' +
                         '{"get_tp_setattro", (PyCFunction)get_tp_setattro, METH_O | METH_STATIC, ""},' +
                         '{"get_nb_bool", (PyCFunction)get_nb_bool, METH_O | METH_STATIC, ""},' +
                         '{"get_tp_as_number", (PyCFunction)get_tp_as_number, METH_O | METH_STATIC, ""},' +
                         '{"get_sq_concat", (PyCFunction)get_sq_concat, METH_O | METH_STATIC, ""},' +
                         '{"get_nb_add", (PyCFunction)get_nb_add, METH_O | METH_STATIC, ""},' +
                         '{"get_tp_descr_get", (PyCFunction)get_tp_descr_get, METH_O | METH_STATIC, ""}')




def test_descr():
    TestDescrSetAndDel = CPyExtType("TestDescrSetAndDel",
                              '''
                              int testdescr_set(PyObject* self, PyObject* key, PyObject* value) {
                                  Py_XDECREF(((TestDescrSetAndDelObject*)self)->payload);
                                  if (value != NULL) {
                                      Py_INCREF(value);
                                  }
                                  ((TestDescrSetAndDelObject*)self)->payload = value;
                                  return 0;
                              }

                              PyObject* testdescr_get(PyObject* self, PyObject* key, PyObject* type) {
                                  PyObject* r = ((TestDescrSetAndDelObject*)self)->payload;
                                  if (r == NULL) {
                                      Py_RETURN_NONE;
                                  }
                                  Py_INCREF(r);
                                  return r;
                              }
                              ''',
                              cmembers='PyObject* payload;',
                              tp_descr_set="(descrsetfunc) testdescr_set",
                              tp_descr_get="(descrgetfunc) testdescr_get")
    class MyC:
        prop = TestDescrSetAndDel()

    x = MyC()
    x.prop = 42
    assert x.prop == 42
    del x.prop
    assert x.prop is None

    x = MyC()
    x.prop = 42
    assert x.prop == 42
    x.__delattr__('prop')
    assert x.prop is None

    raw = TestDescrSetAndDel()
    raw.__set__('foo', 42)
    assert raw.__get__(raw, 'foo') == 42
    assert raw.__get__(raw) == 42
    raw.__delete__(raw)
    assert raw.__get__(raw, 'foo') is None


def test_attrs():
    Dummy = CPyExtHeapType("DummyThatShouldInheritBuiltinGetattro",
                                   slots= ['{Py_nb_add, &myadd}'],
                                   code='PyObject* myadd(PyObject* a, PyObject *b) { Py_INCREF(b); return b; }')
    # Check that we inherit the slot value and do not wrap it with some indirection
    # This should ensure that fast-paths for builtin tp_getattro work correctly
    assert SlotsGetter.get_tp_attro(Dummy()) == SlotsGetter.get_tp_attro(object())
    assert Dummy.__getattribute__ == object.__getattribute__

    class AttrManaged:
        def __init__(self):
            self.bar = 1

        def __getattr__(self, item):
            return 42

    assert SlotsGetter.get_tp_attr(AttrManaged()) == 0
    assert SlotsGetter.get_tp_attro(AttrManaged()) != 0
    assert AttrManaged().bar == 1
    assert AttrManaged().foo == 42

    import sys
    TestAttrOStolenFromModule = CPyExtType("TestAttrOStolenFromModule",
                                           ready_code = 'TestAttrOStolenFromModuleType.tp_getattro = PyModule_Type.tp_getattro;')
    assert SlotsGetter.get_tp_attro(sys) == SlotsGetter.get_tp_attro(TestAttrOStolenFromModule())
    assert sys.__getattribute__.__objclass__ == type(sys)
    assert TestAttrOStolenFromModule.__getattribute__.__objclass__ == TestAttrOStolenFromModule

    TestAttrEmptyDummy = CPyExtType("TestAttrEmptyDummy")
    assert TestAttrEmptyDummy.__getattribute__ == object.__getattribute__

    TestAttrSet = CPyExtType("TestAttrSet",
                              '''
                              int testattro_set(PyObject* self, PyObject* key, PyObject* value) {
                                  Py_XDECREF(((TestAttrSetObject*)self)->payload);
                                  if (value != NULL) {
                                      Py_INCREF(value);
                                  }
                                  ((TestAttrSetObject*)self)->payload = value;
                                  return 0;
                              }

                              PyObject* testattro_get(PyObject* self, PyObject* key) {
                                  PyObject* r = ((TestAttrSetObject*)self)->payload;
                                  if (r == NULL) Py_RETURN_NONE;
                                  Py_INCREF(r);
                                  return r;
                              }
                              ''',
                              cmembers='PyObject* payload;',
                             tp_getattro="(getattrofunc) testattro_get",
                             tp_setattro="(setattrofunc) testattro_set")

    x = TestAttrSet()
    x.foo = 42
    assert x.foo == 42
    del x.foo
    assert x.foo is None


def test_setattr_str_subclass():
    TestAttrSetWitStrSubclass = CPyExtType("TestAttrSetWitStrSubclass",
                             '''
                             int testattro_set(PyObject* self, PyObject* key, PyObject* value) {
                                 Py_XDECREF(((TestAttrSetWitStrSubclassObject*)self)->payload);
                                 if (key != NULL) {
                                     Py_INCREF(key);
                                 }
                                 ((TestAttrSetWitStrSubclassObject*)self)->payload = key;
                                 return 0;
                             }

                             PyObject* my_repr(PyObject *self) {
                                  PyObject* r = ((TestAttrSetWitStrSubclassObject*)self)->payload;
                                  if (r == NULL) Py_RETURN_NONE;
                                  Py_INCREF(r);
                                  return r;
                             }
                             ''',
                             cmembers='PyObject* payload;',
                             tp_repr="my_repr",
                             tp_setattro="(setattrofunc) testattro_set")
    class MyStr(str):
        pass

    x = TestAttrSetWitStrSubclass()
    setattr(x, MyStr('hello'), 42)
    assert type(repr(x)) == MyStr
    assert repr(x) == 'hello'


def test_setattr_wrapper():
    TestSetAttrWrapperReturn = CPyExtType("TestSetAttrWrapperReturn",
                                          "int myset(PyObject* self, PyObject* key, PyObject* value) { return 0; }",
                                           tp_setattro="(setattrofunc) myset")
    x = TestSetAttrWrapperReturn()
    assert x.__setattr__("bar", 42) is None


def test_setattr_vs_setattro_inheritance():
    TestSetAttrOInheritance = CPyExtType("TestSetAttrOInheritance",
                                         '''
                                         int testattr_set(PyObject* self, char* key, PyObject* value) {
                                             Py_XDECREF(((TestSetAttrOInheritanceObject*)self)->payload);
                                             if (value != NULL) {
                                                 Py_INCREF(value);
                                             }
                                             ((TestSetAttrOInheritanceObject*)self)->payload = value;
                                             return 0;
                                         }

                                         PyObject* get_payload(PyObject *self) {
                                              PyObject* r = ((TestSetAttrOInheritanceObject*)self)->payload;
                                              if (r == NULL) Py_RETURN_NONE;
                                              Py_INCREF(r);
                                              return r;
                                         }
                                         ''',
                                         cmembers='PyObject* payload;',
                                         tp_methods='{"get_payload", (PyCFunction)get_payload, METH_NOARGS, ""}',
                                         tp_setattr="testattr_set")

    x = TestSetAttrOInheritance()
    x.foo = 42
    assert x.get_payload() == 42

    class Managed(TestSetAttrOInheritance):
        pass

    x = Managed()
    x.foo = 42  # calls slot_tp_setattro, which calls __setattr__, which wraps the object.tp_setattro
    assert x.get_payload() is None

    assert SlotsGetter.get_tp_setattro(object()) == SlotsGetter.get_tp_setattro(Managed())
    assert SlotsGetter.get_tp_setattro(TestSetAttrOInheritance()) == 0
    assert SlotsGetter.get_tp_setattr(TestSetAttrOInheritance()) != 0


def test_sq_ass_item_wrapper():
    TestSqAssItemWrapperReturn = CPyExtType("TestSqAssItemWrapperReturn",
                                          "static int myassitem(PyObject *self, Py_ssize_t i, PyObject *v) { return 0; }",
                                            sq_ass_item="myassitem")
    x = TestSqAssItemWrapperReturn()
    assert x.__setitem__(42, 42) is None


def test_concat_vs_add():
    # Inheritance of the Py_sq_concat slot:
    SqAdd = CPyExtHeapType("SqAdd",
                   slots= ['{Py_sq_concat, &concat}'],
                   code= 'PyObject* concat(PyObject* a, PyObject *b) { Py_INCREF(a); return a; }')
    x = SqAdd()

    assert x + x is x
    # TODO: assert _operator.concat(x, x) is x when _operator.concat is implemented
    assert x.__add__(x) is x

    class SqAddManaged(SqAdd): pass
    x = SqAddManaged()
    assert x + x is x
    assert x.__add__(x) is x
    # TODO: assert _operator.concat(x, x) is x when _operator.concat is implemented

    SqAddAndNbAdd = CPyExtHeapType("SqAddAndNbAdd",
                           slots= [
                               '{Py_sq_concat, &concat}',
                               '{Py_nb_add, &myadd}',
                           ],
                           code=
                               'PyObject* concat(PyObject* a, PyObject *b) { Py_INCREF(a); return a; }' +
                               'PyObject* myadd(PyObject* a, PyObject *b) { Py_INCREF(b); return b; }')

    x = SqAddAndNbAdd()
    y = SqAddAndNbAdd()
    assert x + y is y
    # TODO: assert _operator.concat(x, x) is x when _operator.concat is implemented


def test_incompatible_slots_assignment():
    def assert_type_error(code):
        try:
            code()
            assert False, "should have raised TypeError: attribute name must be string"
        except TypeError:
            pass

    HackySlotsWithBuiltin = CPyExtType("HackySlots",
                                       ready_code='HackySlotsType.tp_descr_get = (void*)PyBaseObject_Type.tp_getattro;')

    class BarAttr(HackySlotsWithBuiltin):
        def __init__(self):
            self.bar = 42

    class MyClassWithDescr:
        descr = BarAttr()

    assert SlotsGetter.get_tp_descr_get(HackySlotsWithBuiltin()) == SlotsGetter.get_tp_attro(object())
    assert BarAttr().__get__('bar') == 42
    assert_type_error(lambda: MyClassWithDescr().descr)

    class ManagedAttrO:
        def __getattribute__(self, item):
            return item

    try:
        sys.modules["test_incompatible_slots_assignment_managed"] = ManagedAttrO
        HackySlotsWithManaged = CPyExtType("HackySlotsWithManaged",
                                           ready_code="""
                                 PyTypeObject* ManagedAttrO = (PyTypeObject*) PyDict_GetItemString(PyImport_GetModuleDict(), "test_incompatible_slots_assignment_managed");
                                 HackySlotsWithManagedType.tp_descr_get = (void*)ManagedAttrO->tp_getattro;
                                 """)

        class FooAttr(HackySlotsWithManaged):
            def __init__(self):
                self.foo = 42

        class MyClassWithDescr2:
            descr = FooAttr()

        assert FooAttr().__get__('foo') == 42, FooAttr().__get__('foo')
        assert_type_error(lambda: MyClassWithDescr2().descr)
        # This does not hold on GraalPy, because we need a different closure, because the lookup result has changed:
        # assert SlotsGetter.get_tp_descr_get(HackySlotsWithManaged()) == SlotsGetter.get_tp_attro(ManagedAttrO())
    finally:
        sys.modules.pop("test_incompatible_slots_assignment_managed", None)


def test_type_not_ready():
    module = compile_module_from_string("""
        #define PY_SSIZE_T_CLEAN
        #include <Python.h>

        static PyObject* my_getattro(PyObject* self, PyObject* key) {
            return Py_NewRef(key);
        }

        static PyTypeObject NotReadyType = {
            PyVarObject_HEAD_INIT(NULL, 0)
            .tp_name = "NotReadyType",
            .tp_basicsize = sizeof(PyObject),
            .tp_dealloc = (destructor)PyObject_Del,
            .tp_getattro = my_getattro
        };

        static PyObject* create_not_ready(PyObject* module, PyObject* unused) {
            return PyObject_New(PyObject, &NotReadyType);
        }

        static PyMethodDef module_methods[] = {
            {"create_not_ready", _PyCFunction_CAST(create_not_ready), METH_NOARGS, ""},
            {NULL}
        };

        static PyModuleDef NotReadyTypeModule = {
            PyModuleDef_HEAD_INIT, "NotReadyType", "", -1, module_methods
        };

        PyMODINIT_FUNC
        PyInit_NotReadyType(void)
        {
            return PyModule_Create(&NotReadyTypeModule);
        }
    """, "NotReadyType")
    not_ready = module.create_not_ready()
    assert not_ready.foo == 'foo'


def test_sq_len_and_item():
    MySqLenItem = CPyExtType("MySqLenItem",
                             '''
                             Py_ssize_t my_sq_len(PyObject* a) { return 10; }
                             PyObject* my_sq_item(PyObject* self, Py_ssize_t index) { return PyLong_FromSsize_t(index); }
                             ''',
                             sq_length="&my_sq_len",
                             sq_item="my_sq_item")
    MySqItemAddDunderLen = CPyExtHeapType("MySqItemAddDunderLen",
                              code = '''
                                        PyObject* my_sq_item(PyObject* self, Py_ssize_t index) { return PyLong_FromSsize_t(index); }
                                        ''',
                              slots=[
                                  '{Py_sq_item, &my_sq_item}',
                              ])
    MySqItemAddDunderLen.__len__ = lambda self: 10

    def verify(x):
        assert x[5] == 5
        assert x.__getitem__(5) == 5
        assert x[-1] == 9
        assert x.__getitem__(-1) == 9
        assert x[-20] == -10
        assert x.__getitem__(-20) == -10

    verify(MySqLenItem())
    verify(MySqItemAddDunderLen())


def test_mp_len_and_sq_item():
    MyMpLenSqItem = CPyExtType("MyMpLenSqItem",
                             '''
                             Py_ssize_t my_mp_len(PyObject* a) { return 10; }
                             PyObject* my_sq_item(PyObject* self, Py_ssize_t index) { return PyLong_FromSsize_t(index); }
                             ''',
                             mp_length="&my_mp_len",
                             sq_item="my_sq_item")
    MyMpLenSqItemHeap = CPyExtHeapType("MyMpLenSqItemHeap",
                                       code = '''
                                        Py_ssize_t my_mp_len(PyObject* a) { return 10; }
                                        PyObject* my_sq_item(PyObject* self, Py_ssize_t index) { return PyLong_FromSsize_t(index); }
                                        ''',
                                       slots=[
                                           '{Py_mp_length, &my_mp_len}',
                                           '{Py_sq_item, &my_sq_item}',
                                       ])
    def verify(x):
        assert x[5] == 5
        assert x.__getitem__(5) == 5
        # no sq_length, negative index is just passed to sq_item
        assert x[-1] == -1
        assert x.__getitem__(-1) == -1
        assert x[-20] == -20
        assert x.__getitem__(-20) == -20

    verify(MyMpLenSqItem())
    verify(MyMpLenSqItemHeap())


def test_tp_hash():
    TypeWithHash = CPyExtType(
        "TypeWithHash",
        '''
        static PyObject* richcompare(PyObject* self, PyObject* other, int cmp) {
            Py_RETURN_NOTIMPLEMENTED;
        }
        static Py_hash_t hash(PyObject* self) {
            return 123;
        }
        ''',
        tp_richcompare='richcompare',
        tp_hash='hash',
    )
    assert TypeWithHash.__hash__
    assert hash(TypeWithHash()) == 123

    class InheritsHash(TypeWithHash):
        pass

    assert InheritsHash.__hash__
    assert hash(InheritsHash()) == 123

    TypeWithoutHash = CPyExtType(
        "TypeWithoutHash",
        '''
        static PyObject* richcompare(PyObject* self, PyObject* other, int cmp) {
            Py_RETURN_NOTIMPLEMENTED;
        }
        static PyObject* has_hash_not_implemented(PyObject* unused, PyObject* obj) {
            return PyBool_FromLong(Py_TYPE(obj)->tp_hash == PyObject_HashNotImplemented);
        }
        ''',
        tp_richcompare='richcompare',
        tp_methods='{"has_hash_not_implemented", (PyCFunction)has_hash_not_implemented, METH_STATIC | METH_O, ""}',
    )

    def assert_has_no_hash(obj):
        assert_raises(TypeError, hash, obj)
        assert type(obj).__hash__ is None
        assert TypeWithoutHash.has_hash_not_implemented(obj)

    assert_has_no_hash(TypeWithoutHash())

    class OverridesEq1:
        def __eq__(self, other):
            return self is other

    assert_has_no_hash(OverridesEq1())

    class OverridesEq2(TypeWithHash):
        def __eq__(self, other):
            return self is other

    assert_has_no_hash(OverridesEq2())

    class DisablesHash1:
        __hash__ = None

    assert_has_no_hash(DisablesHash1())

    class DisablesHash2(TypeWithHash):
        __hash__ = None

    assert_has_no_hash(DisablesHash2())

    # TODO GR-55196
    # TypeWithoutHashExplicit = CPyExtType(
    #     "TypeWithoutHashExplicit",
    #     tp_hash='PyObject_HashNotImplemented',
    # )
    #
    # assert_has_no_hash(TypeWithoutHashExplicit())


def test_attr_update():
    # Note: version with managed super type whose attribute is updated and should
    # be propagated to the native subtype segfaults on CPython in various ways
    TypeToBeUpdated = CPyExtHeapType("TypeToBeUpdated")
    assert SlotsGetter.get_tp_as_number(TypeToBeUpdated()) != 0

    TypeToBeUpdated.__bool__ = lambda self: False
    assert not bool(TypeToBeUpdated())

    TypeToBeUpdated.__add__ = lambda self,other: f"plus {other}"
    assert TypeToBeUpdated() + "test" == "plus test"

    class ManagedGoesNative:
        pass

    assert bool(ManagedGoesNative())
    assert SlotsGetter.get_nb_bool(ManagedGoesNative()) == 0 # Sends it to native
    assert SlotsGetter.get_tp_as_number(ManagedGoesNative()) != 0
    assert bool(ManagedGoesNative())

    ManagedGoesNative.__bool__ = lambda self: False
    assert not bool(ManagedGoesNative())
    assert SlotsGetter.get_nb_bool(ManagedGoesNative()) != 0


def test_nb_add_inheritace_does_not_add_sq_concat():
    NbAddOnlyHeapType = CPyExtHeapType("NbAddOnlyHeapType",
                                       code = 'PyObject* my_nb_add(PyObject* self, PyObject *other) { return Py_NewRef(self); }',
                                       slots=['{Py_nb_add, &my_nb_add}'])
    class ManagedSub(NbAddOnlyHeapType):
        pass

    assert ManagedSub.__add__
    assert SlotsGetter.get_sq_concat(ManagedSub()) == 0

    class ManagedSub2(NbAddOnlyHeapType):
        def __add__(self, other): return NotImplemented

    assert SlotsGetter.get_sq_concat(ManagedSub2()) == 0


def test_nb_add_sq_concat_static_managed_heap_inheritance():
    NbAddSqConcatStaticType = CPyExtType("NbAddSqConcatStaticType",
                                       code = 'PyObject* my_nb_add(PyObject* self, PyObject *other) { return PyLong_FromLong(42); }',
                                       nb_add = 'my_nb_add',
                                     sq_concat = 'my_nb_add')

    # fixup_slot_dispatchers should figure out that __add__ and __radd__ descriptors wrap the same
    # native call and set nb_add to it instead of the Python dispatcher C function
    class ManagedDummy(NbAddSqConcatStaticType):
        pass

    assert SlotsGetter.get_nb_add(ManagedDummy()) == SlotsGetter.get_nb_add(NbAddSqConcatStaticType())

def test_sq_repeat_mul_without_rmul_inheritance():
    mod = compile_module_from_file("fuzzer_test10")
    Native0 = mod.create_Native0((object, ))
    class Managed1(Native0):
        def __add__(self): return self
        def __mul__(self,o): return "__mul__result: " + str(o)
        def __radd__(self): return NotImplemented
        def __len__(self): return 1
        def __getattribute__(self,name): return name
        def __get__(self,obj,objtype=None): return "dummy"
        def __setattr__(self,name,value): return None

    assert 3 * Managed1() == 3
    assert Managed1() * 3 == "__mul__result: 3"


def test_PyType_Modified_doesnt_change_slots():
    TypeWithSqItemAndMpSubscr = CPyExtType(
        "TypeWithSqItemAndMpSubscr",
        code='''
        static PyObject* sq_item(PyObject* self, Py_ssize_t index) {
            return PyUnicode_FromString("sq_item");
        }
        static PyObject* mp_subscript(PyObject* self, PyObject* index) {
            return PyUnicode_FromString("mp_subscript");
        }
        static PyObject* call_PySequence_GetItem(PyObject* self, PyObject* index) {
            Py_ssize_t i = PyLong_AsSsize_t(index);
            if (i == -1 && PyErr_Occurred())
                return NULL;
            return PySequence_GetItem(self, i);
        }
        ''',
        sq_item="sq_item",
        mp_subscript="mp_subscript",
        tp_methods='{"call_PySequence_GetItem", (PyCFunction)call_PySequence_GetItem, METH_O, ""}',
        post_ready_code="PyType_Modified(&TypeWithSqItemAndMpSubscrType);"
    )
    tester = TypeWithSqItemAndMpSubscr()
    assert tester[1] == 'mp_subscript'
    assert tester.call_PySequence_GetItem(1) == 'sq_item'


def test_nb_slot_calls():
    slots = [
        ('proxy_nb_binary_slot', 'nb_add'),
        ('proxy_nb_binary_slot', 'nb_subtract'),
        ('proxy_nb_binary_slot', 'nb_multiply'),
        ('proxy_nb_binary_slot', 'nb_remainder'),
        ('proxy_nb_binary_slot', 'nb_divmod'),
        ('proxy_nb_ternary_slot', 'nb_power'),
        ('proxy_nb_unary_slot', 'nb_negative'),
        ('proxy_nb_unary_slot', 'nb_positive'),
        ('proxy_nb_unary_slot', 'nb_absolute'),
        ('proxy_nb_inquiry_slot', 'nb_bool'),
        ('proxy_nb_unary_slot', 'nb_invert'),
        ('proxy_nb_binary_slot', 'nb_lshift'),
        ('proxy_nb_binary_slot', 'nb_rshift'),
        ('proxy_nb_binary_slot', 'nb_and'),
        ('proxy_nb_binary_slot', 'nb_xor'),
        ('proxy_nb_binary_slot', 'nb_or'),
        ('proxy_nb_unary_slot', 'nb_int'),
        ('proxy_nb_unary_slot', 'nb_float'),
        ('proxy_nb_binary_slot', 'nb_inplace_add'),
        ('proxy_nb_binary_slot', 'nb_inplace_subtract'),
        ('proxy_nb_binary_slot', 'nb_inplace_multiply'),
        ('proxy_nb_binary_slot', 'nb_inplace_remainder'),
        ('proxy_nb_ternary_slot', 'nb_inplace_power'),
        ('proxy_nb_binary_slot', 'nb_inplace_lshift'),
        ('proxy_nb_binary_slot', 'nb_inplace_rshift'),
        ('proxy_nb_binary_slot', 'nb_inplace_and'),
        ('proxy_nb_binary_slot', 'nb_inplace_xor'),
        ('proxy_nb_binary_slot', 'nb_inplace_or'),
        ('proxy_nb_binary_slot', 'nb_floor_divide'),
        ('proxy_nb_binary_slot', 'nb_true_divide'),
        ('proxy_nb_binary_slot', 'nb_inplace_floor_divide'),
        ('proxy_nb_binary_slot', 'nb_inplace_true_divide'),
        ('proxy_nb_unary_slot', 'nb_index'),
        ('proxy_nb_binary_slot', 'nb_matrix_multiply'),
        ('proxy_nb_binary_slot', 'nb_inplace_matrix_multiply'),
    ]
    NativeSlotProxy = CPyExtType(
        name='NativeSlotProxy',
        cmembers='PyObject* delegate;',
        code=r'''
            static PyObject* proxy_tp_new(PyTypeObject* type, PyObject* args, PyObject* kwargs) {
                PyObject* delegate;
                if (!PyArg_UnpackTuple(args, "NativeSlotProxy", 0, 1, &delegate))
                    return NULL;
                NativeSlotProxyObject* obj = (NativeSlotProxyObject*)type->tp_alloc(type, 0);
                if (!obj)
                    return NULL;
                obj->delegate = Py_NewRef(delegate);  // leaked
                return (PyObject*)obj;
            }
            static PyTypeObject NativeSlotProxyType;
            static void unpack(PyObject** obj) {
                if (Py_TYPE(*obj) == &NativeSlotProxyType) {
                    *obj = ((NativeSlotProxyObject*)*obj)->delegate;
                }
            }
            #define proxy_nb_unary_slot(slot) \
                static PyObject* proxy_##slot(PyObject *a) { \
                    unpack(&a); \
                    return Py_TYPE(a)->tp_as_number->slot(a); \
                }
            #define proxy_nb_inquiry_slot(slot) \
                static int proxy_##slot(PyObject *a) { \
                    unpack(&a); \
                    return Py_TYPE(a)->tp_as_number->slot(a); \
                }
            #define proxy_nb_binary_slot(slot) \
                static PyObject* proxy_##slot(PyObject *a, PyObject *b) { \
                    if (Py_TYPE(a) == &NativeSlotProxyType) { \
                        unpack(&a); \
                        return Py_TYPE(a)->tp_as_number->slot(a, b); \
                    } else { \
                        unpack(&b); \
                        return Py_TYPE(b)->tp_as_number->slot(a, b); \
                    } \
                }
            #define proxy_nb_ternary_slot(slot) \
                static PyObject* proxy_##slot(PyObject *a, PyObject *b, PyObject* c) { \
                    if (Py_TYPE(a) == &NativeSlotProxyType) { \
                        unpack(&a); \
                        return Py_TYPE(a)->tp_as_number->slot(a, b, c); \
                    } else if (Py_TYPE(b) == &NativeSlotProxyType) { \
                        unpack(&b); \
                        return Py_TYPE(b)->tp_as_number->slot(a, b, c); \
                    } else { \
                        unpack(&c); \
                        return Py_TYPE(c)->tp_as_number->slot(a, b, c); \
                    } \
                }
        ''' + '\n'.join(f'{macro}({slot})' for macro, slot in slots),
        tp_new='proxy_tp_new',
        tp_members='{"delegate", T_OBJECT, offsetof(NativeSlotProxyObject, delegate), 0, NULL}',
        **{slot: f'proxy_{slot}' for _, slot in slots},
    )

    def _unary_op(op):
        def fn(a):
            return op(a.delegate)

        return fn

    def _binary_op(op):
        def fn(a, b):
            return op(a.delegate, b)

        return fn

    def _binary_op_r(op):
        def fn(a, b):
            return op(b, a.delegate)

        return fn

    def _binary_op_inplace(op):
        def fn(a, b):
            a.delegate = op(a.delegate, b)
            return a

        return fn

    class PureSlotProxy:
        def __init__(self, delegate):
            self.delegate = delegate

        __add__ = _binary_op(operator.add)
        __radd__ = _binary_op_r(operator.add)
        __iadd__ = _binary_op_inplace(operator.add)
        __sub__ = _binary_op(operator.sub)
        __rsub__ = _binary_op_r(operator.sub)
        __isub__ = _binary_op_inplace(operator.sub)
        __mul__ = _binary_op(operator.mul)
        __rmul__ = _binary_op_r(operator.mul)
        __imul__ = _binary_op_inplace(operator.mul)
        __mod__ = _binary_op(operator.mod)
        __rmod__ = _binary_op_r(operator.mod)
        __imod__ = _binary_op_inplace(operator.mod)
        __floordiv__ = _binary_op(operator.floordiv)
        __rfloordiv__ = _binary_op_r(operator.floordiv)
        __ifloordiv__ = _binary_op_inplace(operator.floordiv)
        __truediv__ = _binary_op(operator.truediv)
        __rtruediv__ = _binary_op_r(operator.truediv)
        __itruediv__ = _binary_op_inplace(operator.truediv)
        __divmod__ = _binary_op(divmod)
        __rdivmod__ = _binary_op_r(divmod)

        def __pow__(self, power, modulo=None):
            return pow(self.delegate, power, modulo)

        def __rpow__(self, other):
            return other ** self.delegate

        def __ipow__(self, other):
            self.delegate = self.delegate ** other
            return self

        __pos__ = _unary_op(operator.pos)
        __neg__ = _unary_op(operator.neg)
        __abs__ = _unary_op(abs)
        __bool__ = _unary_op(bool)
        __invert__ = _unary_op(operator.invert)
        __index__ = _unary_op(operator.index)
        __int__ = _unary_op(int)
        __float__ = _unary_op(float)
        __lshift__ = _binary_op(operator.lshift)
        __rlshift__ = _binary_op_r(operator.lshift)
        __ilshift__ = _binary_op_inplace(operator.lshift)
        __rshift__ = _binary_op(operator.rshift)
        __rrshift__ = _binary_op_r(operator.rshift)
        __irshift__ = _binary_op_inplace(operator.rshift)
        __and__ = _binary_op(operator.and_)
        __rand__ = _binary_op_r(operator.and_)
        __iand__ = _binary_op_inplace(operator.and_)
        __or__ = _binary_op(operator.or_)
        __ror__ = _binary_op_r(operator.or_)
        __ior__ = _binary_op_inplace(operator.or_)
        __xor__ = _binary_op(operator.xor)
        __rxor__ = _binary_op_r(operator.xor)
        __ixor__ = _binary_op_inplace(operator.xor)
        __matmul__ = _binary_op(operator.matmul)
        __rmatmul__ = _binary_op_r(operator.matmul)
        __imatmul__ = _binary_op_inplace(operator.matmul)

    class ObjWithMatmul:
        def __matmul__(self, other):
            return "@"

        def __rmatmul__(self, other):
            return "@"

    for obj in [NativeSlotProxy(3), NativeSlotProxy(PureSlotProxy(3))]:
        assert obj + 2 == 5
        assert 2 + obj == 5
        assert obj - 2 == 1
        assert 2 - obj == -1
        assert obj * 2 == 6
        assert 2 * obj == 6
        assert obj % 2 == 1
        assert 2 % obj == 2
        assert divmod(obj, 2) == (1, 1)
        assert divmod(2, obj) == (0, 2)
        # TODO fix on graalpy
        # assert obj ** 2 == 9
        # assert 2 ** obj == 8
        # assert pow(obj, 2, 2) == 1
        # if isinstance(obj.delegate, int):  # pow doesn't call __rpow__
        #     assert pow(2, obj, 2) == 0
        #     assert pow(2, 2, obj) == 1
        assert -obj == -3
        assert +obj == 3
        assert abs(obj) == 3
        assert bool(obj)
        assert ~obj == -4
        assert obj << 2 == 12
        assert 2 << obj == 16
        assert obj >> 2 == 0
        assert 2 >> obj == 0
        assert obj & 2 == 2
        assert 2 & obj == 2
        assert obj | 2 == 3
        assert 2 | obj == 3
        assert obj ^ 2 == 1
        assert 2 ^ obj == 1
        assert int(obj) == 3
        assert float(obj) == 3.0
        assert operator.index(obj) == 3
        assert obj // 2 == 1
        assert 2 // obj == 0
        assert obj / 2 == 1.5
        assert 2 / obj == 2 / 3

    obj = NativeSlotProxy(ObjWithMatmul())
    assert obj @ 1 == '@'
    assert 1 @ obj == '@'

    obj = NativeSlotProxy(PureSlotProxy(3))
    obj += 2
    assert obj.delegate == 5
    obj = NativeSlotProxy(PureSlotProxy(3))
    obj -= 2
    assert obj.delegate == 1
    obj = NativeSlotProxy(PureSlotProxy(3))
    obj *= 2
    assert obj.delegate == 6
    obj = NativeSlotProxy(PureSlotProxy(3))
    obj %= 2
    assert obj.delegate == 1
    # TODO fix on graalpy
    # obj = NativeSlotProxy(PureSlotProxy(3))
    # obj **= 2
    # assert obj.delegate == 9
    obj = NativeSlotProxy(PureSlotProxy(3))
    obj <<= 2
    assert obj.delegate == 12
    obj = NativeSlotProxy(PureSlotProxy(3))
    obj >>= 2
    assert obj.delegate == 0
    obj = NativeSlotProxy(PureSlotProxy(3))
    obj &= 2
    assert obj.delegate == 2
    obj = NativeSlotProxy(PureSlotProxy(3))
    obj |= 2
    assert obj.delegate == 3
    obj = NativeSlotProxy(PureSlotProxy(3))
    obj ^= 2
    assert obj.delegate == 1
    obj = NativeSlotProxy(PureSlotProxy(3))
    obj //= 2
    assert obj.delegate == 1
    obj = NativeSlotProxy(PureSlotProxy(3))
    obj /= 2
    assert obj.delegate == 1.5
    # TODO fix on graalpy
    # obj = NativeSlotProxy(PureSlotProxy(ObjWithMatmul()))
    # obj @= 1
    # assert obj.delegate == '@'
