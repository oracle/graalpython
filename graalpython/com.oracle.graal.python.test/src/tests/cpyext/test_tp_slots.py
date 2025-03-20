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
import itertools
import sys

import operator
from . import CPyExtType, CPyExtHeapType, compile_module_from_string, assert_raises, compile_module_from_file


def get_delegate(o):
    if hasattr(o, 'delegate'):
        return get_delegate(o.delegate)
    return o


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
    assert operator.concat(x, x) is x
    assert x.__add__(x) is x

    class SqAddManaged(SqAdd): pass
    x = SqAddManaged()
    assert x + x is x
    assert x.__add__(x) is x
    assert operator.concat(x, x) is x

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
    assert operator.concat(x, y) is x

    SqAddAndNbAddNoImplemented = CPyExtHeapType("SqAddAndNbAddNoImplemented",
                                   slots= [
                                       '{Py_sq_concat, &concat}',
                                       '{Py_nb_add, &myadd}',
                                   ],
                                   code=
                                   'PyObject* concat(PyObject* a, PyObject *b) { Py_INCREF(a); return a; }' +
                                   'PyObject* myadd(PyObject* a, PyObject *b) { Py_RETURN_NOTIMPLEMENTED; }')
    x = SqAddAndNbAddNoImplemented()
    assert x + 1 is x


def test_inplace_fallback():
    WithSlot = CPyExtHeapType(
        "NbAdd1",
        slots=[
            '{Py_nb_add, &nb_add}',
        ],
        code='''
        PyObject* nb_add(PyObject* a, PyObject *b) { return PyUnicode_FromString("add1"); }
        ''',
    )
    WithInplaceSlot = CPyExtHeapType(
        "NbInplaceAdd1",
        slots=[
            '{Py_nb_add, &nb_add}',
            '{Py_nb_inplace_add, &nb_inplace_add}',
        ],
        code='''
        PyObject* nb_add(PyObject* a, PyObject *b) { return PyUnicode_FromString("add2"); }
        PyObject* nb_inplace_add(PyObject* a, PyObject *b) { return PyUnicode_FromString("inplace_add"); }
        ''',
    )
    x = WithSlot()
    y = WithInplaceSlot()
    x += y
    assert x == "add1"
    x = WithSlot()
    y += x
    assert y == "inplace_add"
    x = object()
    y = WithInplaceSlot()
    x += y
    assert x == "add2"


def test_sq_inplace_concat_vs_nb_inplace_add():
    SqInplaceConcat = CPyExtHeapType(
        "SqInplaceConcat",
        slots=[
            '{Py_sq_concat, &concat}',
            '{Py_sq_inplace_concat, &inplace_concat}',
        ],
        code='''
        PyObject* concat(PyObject* a, PyObject *b) { return PyUnicode_FromString("concat"); }
        PyObject* inplace_concat(PyObject* a, PyObject *b) { return PyUnicode_FromString("inplace_concat"); }
        ''',
    )
    x = SqInplaceConcat()
    x += 1
    assert x == "inplace_concat"
    x = SqInplaceConcat()
    assert operator.iconcat(x, []) == "inplace_concat"

    SqInplaceConcatAndNbInplaceAdd = CPyExtHeapType(
        "SqInplaceConcatAndNbInplaceAdd",
        slots=['{Py_sq_inplace_concat, &inplace_concat}', '{Py_nb_inplace_add, &inplace_add}'],
        code='''
            PyObject* inplace_concat(PyObject* a, PyObject *b) { return PyUnicode_FromString("inplace_concat"); }
            PyObject* inplace_add(PyObject* a, PyObject *b) { return PyUnicode_FromString("inplace_add"); }
            ''',
    )

    x = SqInplaceConcatAndNbInplaceAdd()
    x += 1
    assert x == "inplace_add"
    x = SqInplaceConcatAndNbInplaceAdd()
    assert operator.iconcat(x, 1) == "inplace_concat"

    SqInplaceConcatAndNbInplaceAddNotImplemented = CPyExtHeapType(
        "InplaceConcatAddNotImpl",
        slots=['{Py_sq_inplace_concat, &inplace_concat}', '{Py_nb_inplace_add, &inplace_add}'],
        code='''
            PyObject* inplace_concat(PyObject* a, PyObject *b) { return PyUnicode_FromString("inplace_concat"); }
            PyObject* inplace_add(PyObject* a, PyObject *b) { Py_RETURN_NOTIMPLEMENTED; }
            ''',
    )

    x = SqInplaceConcatAndNbInplaceAddNotImplemented()
    x += 1
    assert x == "inplace_concat"

    class InplaceConcatSubclass(SqInplaceConcat):
        pass

    x = InplaceConcatSubclass()
    assert operator.iconcat(x, 1) == "inplace_concat"

    SqConcat = CPyExtHeapType(
        "SqConcat",
        slots=['{Py_sq_concat, &concat}'],
        code='PyObject* concat(PyObject* a, PyObject *b) { return PyUnicode_FromString("concat"); }',
    )

    x = SqConcat()
    x += 1
    assert x == "concat"
    x = SqConcat()
    assert operator.iconcat(x, 1) == "concat"

    NbInplaceAdd = CPyExtHeapType(
        "NbInplaceAdd",
        slots=['{Py_nb_inplace_add, &inplace_add}'],
        code='PyObject* inplace_add(PyObject* a, PyObject *b) { return PyUnicode_FromString("inplace_add"); }',
    )

    x = NbInplaceAdd()
    assert_raises(TypeError, operator.iconcat, x, 1)
    assert_raises(TypeError, operator.iconcat, x, [])

    SequenceWithNbInplaceAdd = CPyExtHeapType(
        "SequenceWithNbInplaceAdd",
        slots=['{Py_nb_inplace_add, &inplace_add}', '{Py_sq_item, &item}'],
        code='''
        PyObject* inplace_add(PyObject* a, PyObject *b) { return PyUnicode_FromString("inplace_add"); }
        PyObject* item(PyObject* a, PyObject *b) { return PyUnicode_FromString("item"); }
        ''',
    )

    x = SequenceWithNbInplaceAdd()
    assert_raises(TypeError, operator.iconcat, x, 1)
    assert operator.iconcat(x, []) == "inplace_add"

CallRepeatHelper = CPyExtType(
    "CallRepeatHelper",
    code='''
    PyObject* call_repeat(PyObject* unused, PyObject* args) {
        PyObject* obj;
        Py_ssize_t times;
        if (!PyArg_ParseTuple(args, "On", &obj, &times))
            return NULL;
        return PySequence_Repeat(obj, times);
    }
    PyObject* call_inplace_repeat(PyObject* unused, PyObject* args) {
        PyObject* obj;
        Py_ssize_t times;
        if (!PyArg_ParseTuple(args, "On", &obj, &times))
            return NULL;
        return PySequence_InPlaceRepeat(obj, times);
    }
    ''',
    tp_methods='''
    {"PySequence_Repeat", (PyCFunction)call_repeat, METH_VARARGS | METH_STATIC, ""},
    {"PySequence_InPlaceRepeat", (PyCFunction)call_inplace_repeat, METH_VARARGS | METH_STATIC, ""}
    '''
)


def test_repeat_vs_multiply():
    SqRepeat = CPyExtHeapType(
        "SqRepeat",
        slots=['{Py_sq_repeat, &repeat}'],
        code='PyObject* repeat(PyObject* a, Py_ssize_t times) { return PyUnicode_FromString("repeat"); }',
    )

    x = SqRepeat()
    assert x * 2 == "repeat"
    assert 2 * x == "repeat"
    assert CallRepeatHelper.PySequence_Repeat(x, 2) == "repeat"
    assert x.__mul__(2) == "repeat"
    assert x.__rmul__(2) == "repeat"
    assert_raises(TypeError, operator.mul, x, x)

    class SqRepeatManaged(SqRepeat): pass

    x = SqRepeatManaged()
    assert x * 2 == "repeat"
    assert 2 * x == "repeat"
    assert CallRepeatHelper.PySequence_Repeat(x, 2) == "repeat"
    assert x.__mul__(2) == "repeat"
    assert x.__rmul__(2) == "repeat"
    assert_raises(TypeError, operator.mul, x, x)

    SqRepeatAndNbMultiply = CPyExtHeapType(
        "SqRepeatAndNbMultiply",
        slots=[
            '{Py_sq_repeat, &repeat}',
            '{Py_nb_multiply, &mymultiply}',
        ],
        code='''
            PyObject* repeat(PyObject* a, Py_ssize_t times) { return PyUnicode_FromString("repeat"); }
            PyObject* mymultiply(PyObject* a, PyObject *b) { return PyUnicode_FromString("multiply"); }
            ''',
    )

    x = SqRepeatAndNbMultiply()
    assert x * 2 == "multiply"
    assert 2 * x == "multiply"
    assert CallRepeatHelper.PySequence_Repeat(x, 2) == "repeat"

    SqRepeatAndNbMultiplyNoImplemented = CPyExtHeapType(
        "RepeatAndMulNotImpl",
        slots=[
            '{Py_sq_repeat, &repeat}',
            '{Py_nb_multiply, &mymultiply}',
        ],
        code='''
            PyObject* repeat(PyObject* a, Py_ssize_t times) { return PyUnicode_FromString("repeat"); }
            PyObject* mymultiply(PyObject* a, PyObject *b) { Py_RETURN_NOTIMPLEMENTED; }
            ''',
    )
    x = SqRepeatAndNbMultiplyNoImplemented()
    assert x * 2 == "repeat"


def test_sq_inplace_repeat_vs_nb_inplace_multiply():
    SqInplaceRepeat = CPyExtHeapType(
        "SqInplaceRepeat",
        slots=[
            '{Py_sq_repeat, &repeat}',
            '{Py_sq_inplace_repeat, &inplace_repeat}'
        ],
        code='''
        PyObject* repeat(PyObject* a, PyObject *b) { return PyUnicode_FromString("repeat"); }
        PyObject* inplace_repeat(PyObject* a, Py_ssize_t times) { return PyUnicode_FromString("inplace_repeat"); }
        ''',
    )
    x = SqInplaceRepeat()
    x *= 1
    assert x == "inplace_repeat"
    x = SqInplaceRepeat()
    assert CallRepeatHelper.PySequence_InPlaceRepeat(x, 1) == "inplace_repeat"

    x = 1
    x *= SqInplaceRepeat()
    assert x == "repeat"

    SqInplaceRepeatAndNbInplaceMultiply = CPyExtHeapType(
        "SqInplaceRepeatAndNbInMul",
        slots=['{Py_sq_inplace_repeat, &inplace_repeat}', '{Py_nb_inplace_multiply, &inplace_multiply}'],
        code='''
            PyObject* inplace_repeat(PyObject* a, Py_ssize_t times) { return PyUnicode_FromString("inplace_repeat"); }
            PyObject* inplace_multiply(PyObject* a, PyObject* b) { return PyUnicode_FromString("inplace_multiply"); }
            ''',
    )

    x = SqInplaceRepeatAndNbInplaceMultiply()
    x *= 1
    assert x == "inplace_multiply"
    x = SqInplaceRepeatAndNbInplaceMultiply()
    assert CallRepeatHelper.PySequence_InPlaceRepeat(x, 1) == "inplace_repeat"

    SqInplaceRepeatAndNbInplaceMultiplyNotImplemented = CPyExtHeapType(
        "InplaceRepeatAndMulNotImpl",
        slots=['{Py_sq_inplace_repeat, &inplace_repeat}', '{Py_nb_inplace_multiply, &inplace_multiply}'],
        code='''
            PyObject* inplace_repeat(PyObject* a, Py_ssize_t times) { return PyUnicode_FromString("inplace_repeat"); }
            PyObject* inplace_multiply(PyObject* a, PyObject *b) { Py_RETURN_NOTIMPLEMENTED; }
            ''',
    )

    x = SqInplaceRepeatAndNbInplaceMultiplyNotImplemented()
    x *= 1
    assert x == "inplace_repeat"

    class InplaceRepeatSubclass(SqInplaceRepeat):
        pass

    x = InplaceRepeatSubclass()
    assert CallRepeatHelper.PySequence_InPlaceRepeat(x, 1) == "inplace_repeat"

    SqRepeat = CPyExtHeapType(
        "SqRepeat2",
        slots=['{Py_sq_repeat, &repeat}'],
        code='PyObject* repeat(PyObject* a, PyObject *b) { return PyUnicode_FromString("repeat"); }',
    )

    x = SqRepeat()
    x *= 1
    assert x == "repeat"
    x = SqRepeat()
    assert CallRepeatHelper.PySequence_InPlaceRepeat(x, 1) == "repeat"

    NbInplaceMultiply = CPyExtHeapType(
        "NbInplaceMultiply",
        slots=['{Py_nb_inplace_multiply, &inplace_multiply}'],
        code='PyObject* inplace_multiply(PyObject* a, PyObject *b) { return PyUnicode_FromString("inplace_multiply"); }',
    )

    x = NbInplaceMultiply()
    assert_raises(TypeError, CallRepeatHelper.PySequence_InPlaceRepeat, x, 1)
    assert_raises(TypeError, CallRepeatHelper.PySequence_InPlaceRepeat, x, [])

    SequenceWithNbInplaceMultiply = CPyExtHeapType(
        "SequenceWithNbInplaceMultiply",
        slots=['{Py_nb_inplace_multiply, &inplace_multiply}', '{Py_sq_item, &item}'],
        code='''
        PyObject* inplace_multiply(PyObject* a, PyObject *b) { return PyUnicode_FromString("inplace_multiply"); }
        PyObject* item(PyObject* a, PyObject *b) { return PyUnicode_FromString("item"); }
        ''',
    )

    x = SequenceWithNbInplaceMultiply()
    assert_raises(TypeError, CallRepeatHelper.PySequence_InPlaceRepeat, x, [])
    assert CallRepeatHelper.PySequence_InPlaceRepeat(x, 1) == "inplace_multiply"


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
        assert type(obj).__hash__ is None, f"{type(obj).__hash__=}, {TypeWithoutHash.has_hash_not_implemented(obj)=}"
        assert TypeWithoutHash.has_hash_not_implemented(obj), f"{type(obj).__hash__=}, {TypeWithoutHash.has_hash_not_implemented(obj)=}"

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

    TypeWithoutHashExplicit = CPyExtType(
        "TypeWithoutHashExplicit",
        tp_hash='PyObject_HashNotImplemented',
        code = '''
            static hashfunc myglobal = PyObject_HashNotImplemented;
            typedef struct { hashfunc x; } _mystruct_t;
            static _mystruct_t mystruct = { PyObject_HashNotImplemented };
        ''',
        ready_code = '''
            // printf("TypeWithoutHashExplicitType.tp_hash=%p, PyObject_HashNotImplemented=%p, myglobal=%p, mystruct.x=%p\\n", TypeWithoutHashExplicitType.tp_hash, &PyObject_HashNotImplemented, myglobal, mystruct.x);
            // printf("TypeWithoutHashExplicitType.tp_as_mapping=%p, TypeWithoutHashExplicit_mapping_methods=%p\\n", TypeWithoutHashExplicitType.tp_as_mapping, &TypeWithoutHashExplicit_mapping_methods);
            // printf("offsetof(PyTypeObject, tp_hash)=%p, tp_as_mapping=%p\\n", offsetof(PyTypeObject, tp_hash), offsetof(PyTypeObject, tp_as_mapping));
            // For some reason MSVC initializes tp_hash to some different pointer that also seems to point to PyObject_HashNotImplemented (some dynamic linking issue?)
            // This happens on both CPython 3.11 and GraalPy. The printouts above can help debug the issue in the future.
            #if defined(_MSC_VER)
              TypeWithoutHashExplicitType.tp_hash=PyObject_HashNotImplemented;
            #endif
        '''
    )

    assert_has_no_hash(TypeWithoutHashExplicit())


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


class DelegateSlot:
    def __set_name__(self, owner, name):
        self.name = name

    def __get__(self, obj, objtype=None):
        return getattr(obj.delegate, self.name)


class DelegateInplaceSlot(DelegateSlot):
    def __get__(self, obj, objtype=None):
        method = getattr(obj.delegate, self.name, None)
        if method is None:
            method = getattr(obj.delegate, self.name.replace('__i', '__'))

        def wrapper(*args):
            self.delegate = method(*args)
            return self

        return wrapper


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
        ('proxy_nb_binary_inplace_slot', 'nb_inplace_add'),
        ('proxy_nb_binary_inplace_slot', 'nb_inplace_subtract'),
        ('proxy_nb_binary_inplace_slot', 'nb_inplace_multiply'),
        ('proxy_nb_binary_inplace_slot', 'nb_inplace_remainder'),
        ('proxy_nb_ternary_inplace_slot', 'nb_inplace_power'),
        ('proxy_nb_binary_inplace_slot', 'nb_inplace_lshift'),
        ('proxy_nb_binary_inplace_slot', 'nb_inplace_rshift'),
        ('proxy_nb_binary_inplace_slot', 'nb_inplace_and'),
        ('proxy_nb_binary_inplace_slot', 'nb_inplace_xor'),
        ('proxy_nb_binary_inplace_slot', 'nb_inplace_or'),
        ('proxy_nb_binary_slot', 'nb_floor_divide'),
        ('proxy_nb_binary_slot', 'nb_true_divide'),
        ('proxy_nb_binary_inplace_slot', 'nb_inplace_floor_divide'),
        ('proxy_nb_binary_inplace_slot', 'nb_inplace_true_divide'),
        ('proxy_nb_unary_slot', 'nb_index'),
        ('proxy_nb_binary_slot', 'nb_matrix_multiply'),
        ('proxy_nb_binary_inplace_slot', 'nb_inplace_matrix_multiply'),
    ]
    NativeNbSlotProxy = CPyExtType(
        name='NativeNbSlotProxy',
        cmembers='PyObject* delegate;',
        code=r'''
            typedef NativeNbSlotProxyObject ProxyObject;
            static PyObject* get_delegate(PyObject* self) {
                return ((ProxyObject*)self)->delegate;
            }
            static void set_delegate(PyObject* self, PyObject* delegate) {
                Py_XSETREF(((ProxyObject*)self)->delegate, delegate);
            }
            static PyObject* proxy_tp_new(PyTypeObject* type, PyObject* args, PyObject* kwargs) {
                PyObject* delegate;
                if (!PyArg_UnpackTuple(args, "NativeNbSlotProxy", 0, 1, &delegate))
                    return NULL;
                ProxyObject* obj = (ProxyObject*)type->tp_alloc(type, 0);
                if (!obj)
                    return NULL;
                obj->delegate = Py_NewRef(delegate);  // leaked
                return (PyObject*)obj;
            }
            static PyTypeObject NativeNbSlotProxyType;
            #define proxy_nb_unary_slot(slot) \
                static PyObject* proxy_##slot(PyObject *a) { \
                    PyObject* delegate = get_delegate(a); \
                    return Py_TYPE(delegate)->tp_as_number->slot(delegate); \
                }
            #define proxy_nb_inquiry_slot(slot) \
                static int proxy_##slot(PyObject *a) { \
                    PyObject* delegate = get_delegate(a); \
                    return Py_TYPE(delegate)->tp_as_number->slot(delegate); \
                }
            #define proxy_nb_binary_slot(slot) \
                static PyObject* proxy_##slot(PyObject *a, PyObject *b) { \
                    if (Py_TYPE(a) == &NativeNbSlotProxyType) { \
                        PyObject* delegate = get_delegate(a); \
                        return Py_TYPE(delegate)->tp_as_number->slot(delegate, b); \
                    } else { \
                        PyObject* delegate = get_delegate(b); \
                        return Py_TYPE(delegate)->tp_as_number->slot(a, delegate); \
                    } \
                }
            #define proxy_nb_binary_inplace_slot(slot) \
                static PyObject* proxy_##slot(PyObject *self, PyObject *other) { \
                    PyObject* delegate = get_delegate(self); \
                    PyObject* result = Py_TYPE(delegate)->tp_as_number->slot(delegate, other); \
                    if (!result) \
                        return NULL; \
                    set_delegate(self, result); \
                    return Py_NewRef(self); \
                }
            #define proxy_nb_ternary_slot(slot) \
                static PyObject* proxy_##slot(PyObject *a, PyObject *b, PyObject* c) { \
                    if (Py_TYPE(a) == &NativeNbSlotProxyType) { \
                        PyObject* delegate = get_delegate(a); \
                        return Py_TYPE(delegate)->tp_as_number->slot(delegate, b, c); \
                    } else if (Py_TYPE(b) == &NativeNbSlotProxyType) { \
                        PyObject* delegate = get_delegate(b); \
                        return Py_TYPE(delegate)->tp_as_number->slot(a, delegate, c); \
                    } else { \
                        PyObject* delegate = get_delegate(c); \
                        return Py_TYPE(delegate)->tp_as_number->slot(a, b, delegate); \
                    } \
                }
            #define proxy_nb_ternary_inplace_slot(slot) \
                static PyObject* proxy_##slot(PyObject *self, PyObject *b, PyObject* c) { \
                    PyObject* delegate = get_delegate(self); \
                    PyObject* result = Py_TYPE(delegate)->tp_as_number->slot(delegate, b, c); \
                    if (!result) \
                        return NULL; \
                    set_delegate(self, result); \
                    return Py_NewRef(self); \
                }
        ''' + '\n'.join(f'{macro}({slot})' for macro, slot in slots),
        tp_new='proxy_tp_new',
        tp_members='{"delegate", T_OBJECT, offsetof(ProxyObject, delegate), 0, NULL}',
        **{slot: f'proxy_{slot}' for _, slot in slots},
    )

    class PureSlotProxy:
        def __init__(self, delegate):
            self.delegate = delegate

        __add__ = DelegateSlot()
        __radd__ = DelegateSlot()
        __iadd__ = DelegateInplaceSlot()
        __sub__ = DelegateSlot()
        __rsub__ = DelegateSlot()
        __isub__ = DelegateInplaceSlot()
        __mul__ = DelegateSlot()
        __rmul__ = DelegateSlot()
        __imul__ = DelegateInplaceSlot()
        __mod__ = DelegateSlot()
        __rmod__ = DelegateSlot()
        __imod__ = DelegateInplaceSlot()
        __floordiv__ = DelegateSlot()
        __rfloordiv__ = DelegateSlot()
        __ifloordiv__ = DelegateInplaceSlot()
        __truediv__ = DelegateSlot()
        __rtruediv__ = DelegateSlot()
        __itruediv__ = DelegateInplaceSlot()
        __divmod__ = DelegateSlot()
        __rdivmod__ = DelegateSlot()
        __pow__ = DelegateSlot()
        __rpow__ = DelegateSlot()
        __ipow__ = DelegateInplaceSlot()
        __pos__ = DelegateSlot()
        __neg__ = DelegateSlot()
        __abs__ = DelegateSlot()
        __bool__ = DelegateSlot()
        __invert__ = DelegateSlot()
        __index__ = DelegateSlot()
        __int__ = DelegateSlot()
        __float__ = DelegateSlot()
        __lshift__ = DelegateSlot()
        __rlshift__ = DelegateSlot()
        __ilshift__ = DelegateInplaceSlot()
        __rshift__ = DelegateSlot()
        __rrshift__ = DelegateSlot()
        __irshift__ = DelegateInplaceSlot()
        __and__ = DelegateSlot()
        __rand__ = DelegateSlot()
        __iand__ = DelegateInplaceSlot()
        __or__ = DelegateSlot()
        __ror__ = DelegateSlot()
        __ior__ = DelegateInplaceSlot()
        __xor__ = DelegateSlot()
        __rxor__ = DelegateSlot()
        __ixor__ = DelegateInplaceSlot()
        __matmul__ = DelegateSlot()
        __rmatmul__ = DelegateSlot()
        __imatmul__ = DelegateInplaceSlot()

    class ObjWithMatmul:
        def __matmul__(self, other):
            return "@"

        def __rmatmul__(self, other):
            return "@"

    for obj in [NativeNbSlotProxy(3), NativeNbSlotProxy(PureSlotProxy(3))]:
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
        assert obj ** 2 == 9
        assert 2 ** obj == 8
        assert pow(obj, 2, 2) == 1
        if isinstance(obj.delegate, int):  # pow doesn't call __rpow__
            assert pow(2, obj, 2) == 0
            assert pow(2, 2, obj) == 1
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

    obj = NativeNbSlotProxy(ObjWithMatmul())
    assert obj @ 1 == '@'
    assert 1 @ obj == '@'

    obj = NativeNbSlotProxy(PureSlotProxy(3))
    obj += 2
    assert obj.delegate.delegate == 5
    obj = NativeNbSlotProxy(PureSlotProxy(3))
    obj -= 2
    assert obj.delegate.delegate == 1
    obj = NativeNbSlotProxy(PureSlotProxy(3))
    obj *= 2
    assert obj.delegate.delegate == 6
    obj = NativeNbSlotProxy(PureSlotProxy(3))
    obj %= 2
    assert obj.delegate.delegate == 1
    obj = NativeNbSlotProxy(PureSlotProxy(3))
    obj **= 2
    assert obj.delegate.delegate == 9
    obj = NativeNbSlotProxy(PureSlotProxy(3))
    obj <<= 2
    assert obj.delegate.delegate == 12
    obj = NativeNbSlotProxy(PureSlotProxy(3))
    obj >>= 2
    assert obj.delegate.delegate == 0
    obj = NativeNbSlotProxy(PureSlotProxy(3))
    obj &= 2
    assert obj.delegate.delegate == 2
    obj = NativeNbSlotProxy(PureSlotProxy(3))
    obj |= 2
    assert obj.delegate.delegate == 3
    obj = NativeNbSlotProxy(PureSlotProxy(3))
    obj ^= 2
    assert obj.delegate.delegate == 1
    obj = NativeNbSlotProxy(PureSlotProxy(3))
    obj //= 2
    assert obj.delegate.delegate == 1
    obj = NativeNbSlotProxy(PureSlotProxy(3))
    obj /= 2
    assert obj.delegate.delegate == 1.5
    obj = NativeNbSlotProxy(PureSlotProxy(ObjWithMatmul()))
    obj @= 1
    assert obj.delegate.delegate == '@'


def test_sq_slot_calls():
    NativeSqSlotProxy = CPyExtType(
        name='NativeSqSlotProxy',
        cmembers='PyObject* delegate;',
        code=r'''
            typedef NativeSqSlotProxyObject ProxyObject;
            static PyObject* get_delegate(PyObject* self) {
                return ((ProxyObject*)self)->delegate;
            }
            static void set_delegate(PyObject* self, PyObject* delegate) {
                Py_XSETREF(((ProxyObject*)self)->delegate, delegate);
            }
            static PyObject* proxy_tp_new(PyTypeObject* type, PyObject* args, PyObject* kwargs) {
                PyObject* delegate;
                if (!PyArg_UnpackTuple(args, "NativeSqSlotProxy", 0, 1, &delegate))
                    return NULL;
                ProxyObject* obj = (ProxyObject*)type->tp_alloc(type, 0);
                if (!obj)
                    return NULL;
                obj->delegate = Py_NewRef(delegate);  // leaked
                return (PyObject*)obj;
            }
            static Py_ssize_t proxy_sq_length(PyObject* self) {
                PyObject* delegate = get_delegate(self);
                return Py_TYPE(delegate)->tp_as_sequence->sq_length(delegate);
            }
            static PyObject* proxy_sq_item(PyObject* self, Py_ssize_t item) {
                PyObject* delegate = get_delegate(self);
                return Py_TYPE(delegate)->tp_as_sequence->sq_item(delegate, item);
            }
            static int proxy_sq_ass_item(PyObject* self, Py_ssize_t item, PyObject* value) {
                PyObject* delegate = get_delegate(self);
                return Py_TYPE(delegate)->tp_as_sequence->sq_ass_item(delegate, item, value);
            }
            static int proxy_sq_contains(PyObject* self, PyObject* item) {
                PyObject* delegate = get_delegate(self);
                return Py_TYPE(delegate)->tp_as_sequence->sq_contains(delegate, item);
            }
            static PyObject* proxy_sq_concat(PyObject* self, PyObject* item) {
                PyObject* delegate = get_delegate(self);
                return Py_TYPE(delegate)->tp_as_sequence->sq_concat(delegate, item);
            }
            static PyObject* proxy_sq_inplace_concat(PyObject* self, PyObject* item) {
                PyObject* delegate = get_delegate(self);
                PyObject* result = Py_TYPE(delegate)->tp_as_sequence->sq_inplace_concat(delegate, item);
                if (!result)
                    return NULL;
                set_delegate(self, result);
                return Py_NewRef(self);
            }
            static PyObject* proxy_sq_repeat(PyObject* self, Py_ssize_t times) {
                PyObject* delegate = get_delegate(self);
                return Py_TYPE(delegate)->tp_as_sequence->sq_repeat(delegate, times);
            }
            static PyObject* proxy_sq_inplace_repeat(PyObject* self, Py_ssize_t times) {
                PyObject* delegate = get_delegate(self);
                PyObject* result = Py_TYPE(delegate)->tp_as_sequence->sq_inplace_repeat(delegate, times);
                if (!result)
                    return NULL;
                set_delegate(self, result);
                return Py_NewRef(self);
            }
        ''',
        tp_new='proxy_tp_new',
        tp_members='{"delegate", T_OBJECT, offsetof(ProxyObject, delegate), 0, NULL}',
        sq_length='proxy_sq_length',
        sq_item='proxy_sq_item',
        sq_ass_item='proxy_sq_ass_item',
        sq_contains='proxy_sq_contains',
        sq_concat='proxy_sq_concat',
        sq_inplace_concat='proxy_sq_inplace_concat',
        sq_repeat='proxy_sq_repeat',
        sq_inplace_repeat='proxy_sq_inplace_repeat',
    )

    class PureSlotProxy:
        def __init__(self, delegate):
            self.delegate = delegate

        __len__ = DelegateSlot()
        __getitem__ = DelegateSlot()
        __setitem__ = DelegateSlot()
        __delitem__ = DelegateSlot()
        __contains__ = DelegateSlot()

    for obj in [NativeSqSlotProxy([1]), NativeSqSlotProxy(PureSlotProxy([1]))]:
        assert len(obj) == 1
        assert bool(obj)
        assert 1 in obj
        assert 2 not in obj

        assert obj[0] == 1
        assert_raises(IndexError, operator.getitem, obj, 1)
        assert_raises(IndexError, operator.getitem, obj, 1 << 65)
        assert_raises(TypeError, operator.getitem, obj, "a")

        assert_raises(IndexError, operator.setitem, obj, 1, 3)
        assert_raises(IndexError, operator.setitem, obj, 1 << 65, 3)
        assert_raises(TypeError, operator.setitem, obj, "a", 3)
        assert_raises(TypeError, operator.setitem, obj, slice(0), 3)
        assert get_delegate(obj) == [1]
        obj[0] = 2
        assert get_delegate(obj) == [2]

        assert_raises(IndexError, operator.delitem, obj, 1)
        assert_raises(IndexError, operator.delitem, obj, 1 << 65)
        assert_raises(TypeError, operator.delitem, obj, "a")
        assert_raises(TypeError, operator.delitem, obj, slice(0))
        assert get_delegate(obj) == [2]
        del obj[0]
        assert get_delegate(obj) == []
        assert not bool(obj)

    obj = NativeSqSlotProxy([1])
    assert obj + [2] == [1, 2]
    obj += [2]
    assert obj.delegate == [1, 2]
    obj = NativeSqSlotProxy([1])
    assert obj * 2 == [1, 1]
    obj *= 2
    assert obj.delegate == [1, 1]
    obj = NativeSqSlotProxy([1])
    assert_raises(TypeError, operator.mul, obj, "a")
    assert_raises(OverflowError, operator.mul, obj, 1 << 65)
    try:
        obj *= "a"
    except TypeError:
        pass
    else:
        assert False
    try:
        obj *= 1 << 65
    except OverflowError:
        pass
    else:
        assert False
    assert get_delegate(obj) == [1]


def test_mp_slot_calls():
    NativeMpSlotProxy = CPyExtType(
        name='NativeMpSlotProxy',
        cmembers='PyObject* delegate;',
        code=r'''
            typedef NativeMpSlotProxyObject ProxyObject;
            static PyObject* get_delegate(PyObject* self) {
                return ((ProxyObject*)self)->delegate;
            }
            static PyObject* proxy_tp_new(PyTypeObject* type, PyObject* args, PyObject* kwargs) {
                PyObject* delegate;
                if (!PyArg_UnpackTuple(args, "NativeMpSlotProxy", 0, 1, &delegate))
                    return NULL;
                ProxyObject* obj = (ProxyObject*)type->tp_alloc(type, 0);
                if (!obj)
                    return NULL;
                obj->delegate = Py_NewRef(delegate);  // leaked
                return (PyObject*)obj;
            }
            static Py_ssize_t proxy_mp_length(PyObject* self) {
                PyObject* delegate = get_delegate(self);
                return Py_TYPE(delegate)->tp_as_mapping->mp_length(delegate);
            }
            static PyObject* proxy_mp_subscript(PyObject* self, PyObject* item) {
                PyObject* delegate = get_delegate(self);
                return Py_TYPE(delegate)->tp_as_mapping->mp_subscript(delegate, item);
            }
            static int proxy_mp_ass_subscript(PyObject* self, PyObject* item, PyObject* value) {
                PyObject* delegate = get_delegate(self);
                return Py_TYPE(delegate)->tp_as_mapping->mp_ass_subscript(delegate, item, value);
            }
        ''',
        tp_new='proxy_tp_new',
        tp_members='{"delegate", T_OBJECT, offsetof(ProxyObject, delegate), 0, NULL}',
        mp_length='proxy_mp_length',
        mp_subscript='proxy_mp_subscript',
        mp_ass_subscript='proxy_mp_ass_subscript',
    )

    class PureSlotProxy:
        def __init__(self, delegate):
            self.delegate = delegate

        __len__ = DelegateSlot()
        __getitem__ = DelegateSlot()
        __setitem__ = DelegateSlot()
        __delitem__ = DelegateSlot()
        __contains__ = DelegateSlot()

    for obj in [NativeMpSlotProxy({'a': 1}), NativeMpSlotProxy(PureSlotProxy({'a': 1}))]:
        assert len(obj) == 1
        assert bool(obj)

        assert obj['a'] == 1
        assert_raises(KeyError, operator.getitem, obj, 'b')
        obj['b'] = 2
        assert get_delegate(obj) == {'a': 1, 'b': 2}

        assert_raises(KeyError, operator.delitem, obj, 'c')
        del obj['b']
        assert get_delegate(obj) == {'a': 1}
        del obj['a']
        assert not bool(obj)


def test_tp_slot_calls():
    NativeSlotProxy = CPyExtType(
        name='TpSlotProxy',
        cmembers='PyObject* delegate;',
        code=r'''
            typedef TpSlotProxyObject ProxyObject;
            static PyObject* get_delegate(PyObject* self) {
                return ((ProxyObject*)self)->delegate;
            }
            static PyObject* proxy_tp_new(PyTypeObject* type, PyObject* args, PyObject* kwargs) {
                PyObject* delegate;
                if (!PyArg_UnpackTuple(args, "NativeTpSlotProxy", 0, 1, &delegate))
                    return NULL;
                ProxyObject* obj = (ProxyObject*)type->tp_alloc(type, 0);
                if (!obj)
                    return NULL;
                obj->delegate = Py_NewRef(delegate);  // leaked
                return (PyObject*)obj;
            }
            static PyObject* proxy_tp_iter(PyObject* self) {
                PyObject* delegate = get_delegate(self);
                return Py_TYPE(delegate)->tp_iter(delegate);
            }
            static PyObject* proxy_tp_iternext(PyObject* self) {
                PyObject* delegate = get_delegate(self);
                return Py_TYPE(delegate)->tp_iternext(delegate);
            }
        ''',
        tp_new='proxy_tp_new',
        tp_members='{"delegate", T_OBJECT, offsetof(ProxyObject, delegate), 0, NULL}',
        tp_iter='proxy_tp_iter',
        tp_iternext='proxy_tp_iternext',
    )

    class PureSlotProxy:
        def __init__(self, delegate):
            self.delegate = delegate

        __iter__ = DelegateSlot()
        __next__ = DelegateSlot()

    for obj in [NativeSlotProxy([1]), NativeSlotProxy(PureSlotProxy([1]))]:
        assert isinstance(iter(obj), type(iter([])))

    for obj in [NativeSlotProxy(iter([1])), NativeSlotProxy(PureSlotProxy(iter([1])))]:
        assert next(obj) == 1
        assert_raises(StopIteration, next, obj)


def test_tp_iternext_not_implemented():
    class ManagedTypeWithVanishingNext:
        def __next__(self):
            del type(self).__next__
            return True

    NativeTypeWithVanishingNext = CPyExtHeapType(
        name='TypeWithVanishingNext',
        code=r'''
            static PyObject* tp_iternext(TypeWithVanishingNextObject* self) {
                if (PyObject_DelAttrString((PyObject*)Py_TYPE(self), "__next__") < 0)
                    return NULL;
                Py_RETURN_TRUE;
            }
        ''',
        slots=['{Py_tp_iternext, tp_iternext}'],
    )

    tester = CPyExtType(
        'IterableTester',
        r'''
        PyObject* iter_check(PyObject* unused, PyObject* obj) {
            return PyBool_FromLong(PyIter_Check(obj));
        }
        PyObject* not_impl_check(PyObject* unused, PyObject* obj) {
            return PyBool_FromLong(Py_TYPE(obj)->tp_iternext == _PyObject_NextNotImplemented);
        }
        ''',
        tp_methods='''
        {"PyIter_Check", iter_check, METH_O | METH_STATIC, ""},
        {"has_not_implemented_iternext", not_impl_check, METH_O | METH_STATIC, ""}
        ''',
    )

    for TypeWithVanishingNext in (ManagedTypeWithVanishingNext, NativeTypeWithVanishingNext):

        class Iterable:
            def __iter__(self):
                return TypeWithVanishingNext()

        try:
            # We need to use a builtin that stores the iterator inside without rechecking it
            i = itertools.takewhile(lambda x: x, Iterable())
            assert next(i) is True
            next(i)
        except TypeError as e:
            # We need to check the message, because it's not the one from `next`, but from _PyObject_NextNotImplemented
            assert str(e).endswith("is not iterable")
        else:
            assert False

        i = TypeWithVanishingNext()
        try:
            next(i)
        except TypeError as e:
            # Different message, now from `next` directly
            assert str(e).endswith("is not an iterator")

        assert not tester.PyIter_Check(i)
        assert tester.has_not_implemented_iternext(i)

    NativeTypeWithNotImplementedNext = CPyExtHeapType(
        name='TypeWithNotImplNext',
        slots=['{Py_tp_iternext, _PyObject_NextNotImplemented}'],
    )
    i = NativeTypeWithNotImplementedNext()
    assert not tester.PyIter_Check(i)
    assert tester.has_not_implemented_iternext(i)
    try:
        next(i)
    except TypeError as e:
        assert str(e).endswith("is not an iterator")


def test_richcmp():
    MyNativeIntSubType = CPyExtType("MyNativeIntSubTypeForRichCmpTest",
                             ready_code = "MyNativeIntSubTypeForRichCmpTestType.tp_new = PyLong_Type.tp_new;",
                             tp_base='&PyLong_Type',
                             struct_base='PyLongObject base;')
    assert MyNativeIntSubType(42) == 42
    assert MyNativeIntSubType(42) == MyNativeIntSubType(42)
