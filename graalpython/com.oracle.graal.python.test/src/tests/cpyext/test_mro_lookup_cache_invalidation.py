# Copyright (c) 2020, 2026, Oracle and/or its affiliates. All rights reserved.
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
from . import CPyExtHeapType, CPyExtType


TypeAttrHelper = CPyExtType(
    "TypeAttrHelper",
    """
    PyObject* changeAttr(PyObject *self, PyObject *args) {
        PyObject *type;
        PyObject *name;
        PyObject *value;
        if (!PyArg_ParseTuple(args, "O!UO:changeAttr", &PyType_Type, &type, &name, &value)) {
            return NULL;
        }
        PyTypeObject *type_obj = (PyTypeObject *)type;
        // This is a hack. Python docs say that tp_dict should be treated as read-only,
        // and for static types, tp_dict is no longer valid anymore, but there are extensions
        // using this pattern (as of 2026, e.g., `siplib` or `BTrees`).
        if (PyDict_SetItem(type_obj->tp_dict, name, value) < 0) {
            return NULL;
        }
        PyType_Modified(type_obj);
        Py_RETURN_NONE;
    }
    PyObject* deleteAttr(PyObject *self, PyObject *args) {
        PyObject *type;
        PyObject *name;
        if (!PyArg_ParseTuple(args, "O!U:deleteAttr", &PyType_Type, &type, &name)) {
            return NULL;
        }
        PyTypeObject *type_obj = (PyTypeObject *)type;
        if (PyDict_DelItem(type_obj->tp_dict, name) < 0) {
            return NULL;
        }
        PyType_Modified(type_obj);
        Py_RETURN_NONE;
    }
    """,
    tp_methods='{"changeAttr", (PyCFunction)changeAttr, METH_VARARGS | METH_STATIC, ""},'
                ' {"deleteAttr", (PyCFunction)deleteAttr, METH_VARARGS | METH_STATIC, ""}',
)


def test_attribute_assignment_invalidates_transitive_subclass_lookups():
    class PythonBase:
        x = 1

    class PythonSubclass(PythonBase):
        pass

    class PythonSubclass2(PythonSubclass):
        pass

    x = PythonSubclass2()
    for i in range(10):
        assert x.x == 1

    PythonBase.x = 42
    for i in range(10):
        assert x.x == 42


def test_pytype_modified_on_native_type_invalidates_subclass_lookups():
    NativeBase = CPyExtHeapType(
        "TypeCacheBase",
        code="""
            int base_bool(PyObject *self) {
                return 0;
            }
        """,
        slots=['{Py_nb_bool, base_bool}'],
    )
    NativeBase.x = 1

    NativeType = CPyExtHeapType(
        "TypeCacheMiddle",
        bases=(NativeBase,),
    )

    class PythonSubclass(NativeType):
        pass

    class PythonSubclass2(PythonSubclass):
        pass

    def do_lookup(obj):
        return obj.x

    x = PythonSubclass2()
    # Lookup resolves to the attribute inherited from NativeBase.
    # Run do_lookup multiple times to make sure lookup caches are initialized.
    for i in range(10):
        assert do_lookup(x) == 1

    # NativeType gains x == 42, which must invalidate cached lookups on PythonSubclass.
    TypeAttrHelper.changeAttr(NativeType, "x", 42)
    for i in range(10):
        assert do_lookup(x) == 42

    # PyType_Modified invalidates lookups, but does not itself rebuild a type's slot table.
    NativeType.__bool__ = lambda self: False
    for i in range(10):
        assert bool(x) is False

    TypeAttrHelper.changeAttr(NativeType, "__bool__", lambda self: True)
    for i in range(10):
        assert bool(x) is True


def test_pytype_modified_on_python_type_invalidates_subclass_lookups():
    class PythonBase:
        x = 1

        def __repr__(self):
            return 'expected repr'

        def __bool__(self):
            return False

    class PythonType(PythonBase):
        pass

    class PythonSubclass(PythonType):
        pass

    class PythonSubclass2(PythonSubclass):
        pass

    def do_lookup(obj):
        return obj.x

    x = PythonSubclass2()
    for i in range(10):
        assert do_lookup(x) == 1

    TypeAttrHelper.changeAttr(PythonType, "x", 42)
    for i in range(10):
        assert do_lookup(x) == 42

    for i in range(10):
        assert bool(x) is False

    # Although PyType_Modified does not rebuild a type's slot table, we
    # can just assign the attribute and see the method being dispatched to
    # because CPython slot wrappers always perform the MRO lookup. GraalPy
    # slots cache MRO lookups, and so GraalPy must invalidate those caches.
    TypeAttrHelper.changeAttr(PythonType, "__bool__", lambda self: True)
    assert repr(x) == 'expected repr'
    for i in range(10):
        assert bool(x) is True


def test_pytype_modified_after_deleting_special_method_invalidates_slot_lookup():
    class PythonBase:
        def __len__(self):
            return 1

    class PythonSubclass(PythonBase):
        pass

    class PythonSubclass2(PythonSubclass):
        pass

    x = PythonSubclass2()
    for i in range(10):
        assert len(x) == 1

    TypeAttrHelper.deleteAttr(PythonBase, "__len__")
    try:
        len(x)
    except AttributeError as e:
        assert '__len__' in str(e)
    else:
        assert False, "len() unexpectedly succeeded"


def test_bases_assignment_invalidates_subclass_lookups():
    class OriginalBase:
        x = 1

    class NewBase:
        x = 42

    class Parent(OriginalBase):
        pass

    class OtherSubclass(Parent):
        pass

    class CachedSubclass(Parent):
        pass

    class CachedSubclass2(CachedSubclass):
        pass

    def do_lookup(obj):
        return obj.x

    x = CachedSubclass2()
    for i in range(10):
        assert do_lookup(x) == 1

    Parent.__bases__ = (NewBase,)
    for i in range(10):
        assert do_lookup(x) == 42


def test_pytype_modified_during_subclass_initialization_does_not_crash():
    class Meta(type):
        def mro(cls):
            if cls.__name__ == "Child":
                TypeAttrHelper.changeAttr(Base, "x", 42)
            return super().mro()

    class Base(metaclass=Meta):
        pass

    class Child(Base, metaclass=Meta):
        pass

    assert Child.__mro__ == (Child, Base, object)


def test_bases_assignment_with_diamond_in_subclasses():
    class OriginalBase:
        pass

    class NewBase:
        pass

    class A(OriginalBase):
        pass

    class B(A):
        X = 1

    class C(A):
        X = 42

    class D(B, C):
        pass

    A.__bases__ = (NewBase,)
    d = D()
    assert d.X == 1


def test_bases_assignment_updates_diamond_mro_in_topological_order():
    class OriginalBase:
        pass

    class NewBase:
        pass

    class A(OriginalBase):
        pass

    class B(A):
        pass

    class C(A):
        pass

    class D(B, C):
        pass

    A.__bases__ = (NewBase,)

    assert D.__mro__ == (D, B, C, A, NewBase, object)


def test_new_class_attribute_invalidates_mro_shape_in_multi_context_mode():
    class Base:
        pass

    instance = Base()

    def lookup(obj):
        return getattr(obj, "new_attribute", "missing")

    assert lookup(instance) == "missing"
    Base.new_attribute = 42
    assert lookup(instance) == 42


def test_descendant_mro_shape_is_invalidated_after_base_shape_budget_is_exhausted():
    class Base:
        pass

    for index in range(6):
        setattr(Base, "old_attribute_" + str(index), index)

    class Child(Base):
        pass

    instance = Child()

    def lookup(obj):
        return getattr(obj, "new_attribute", "missing")

    assert lookup(instance) == "missing"
    Base.new_attribute = 42
    assert lookup(instance) == 42
