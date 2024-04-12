# Copyright (c) 2024, 2024, Oracle and/or its affiliates. All rights reserved.
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
from . import CPyExtType, CPyExtTypeDecl, CPyExtHeapType
import sys

GRAALPYTHON = sys.implementation.name == "graalpy"

def test_from_python():
    def assert_true(kls, has_dunder_bool=True):
        obj = kls()
        assert obj, f"{kls.__name__} should give True"
        if has_dunder_bool:
            assert obj.__bool__(), f"{kls.__name__} should give True"

    def assert_false(kls, has_dunder_bool=True):
        obj = kls()
        assert not obj, f"{kls.__name__} should give False"
        if has_dunder_bool:
            assert not obj.__bool__(), f"{kls.__name__} should give False"

    NbBoolFalse = CPyExtType("NbBoolFalse",
                             "int my_nb_bool(PyObject* a) { return 0; }",
                             nb_bool="&my_nb_bool")
    NbBoolTrue = CPyExtType("NbBoolTrue",
                            "int my_nb_bool(PyObject* a) { return 1; }",
                            nb_bool="&my_nb_bool")
    SqLenFalse = CPyExtType("SqLenFalse",
                            "Py_ssize_t my_sq_len(PyObject* a) { return 0; }",
                            sq_length="&my_sq_len")
    SqLenTrue = CPyExtType("SqLenTrue",
                           "Py_ssize_t my_sq_len(PyObject* a) { return 42; }",
                           sq_length="&my_sq_len")

    assert_true(NbBoolTrue)
    assert_false(NbBoolFalse)

    assert_true(SqLenTrue, has_dunder_bool=False)
    assert_false(SqLenFalse, has_dunder_bool=False)

    SqLenTrueBoolFalse = CPyExtType("SqLenTrueBoolFalse",
                                    "Py_ssize_t my_sq_len(PyObject* a) { return 42; }" +
                                    "int my_nb_bool(PyObject* a) { return 0; }",
                                    sq_length="&my_sq_len",
                                    nb_bool="&my_nb_bool")

    assert_false(SqLenTrueBoolFalse)

    class ManagedBoolFalse(NbBoolTrue):
        def __bool__(self):
            return False

    class ManagedBoolTrue(SqLenTrueBoolFalse):
        def __bool__(self):
            return True

    class ManagedLenFalse:
        def __len__(self):
            return 0

    class ManagedLenTrueNbBoolFalse(NbBoolFalse):
        def __len__(self):
            return 1

    assert_true(ManagedBoolTrue)
    assert_false(ManagedBoolFalse)
    assert_false(ManagedLenTrueNbBoolFalse)
    assert_false(ManagedLenFalse, has_dunder_bool=False)

    NbBoolRaises = CPyExtType("NbBoolRaises",
                              '''int my_nb_bool(PyObject* a) {
                                         PyErr_SetString(PyExc_RuntimeError, "Expected error");
                                         return -1;
                                      }''',
                              nb_bool="&my_nb_bool")

    try:
        if NbBoolRaises():
            assert False
    except RuntimeError as e:
        assert 'Expected error' in str(e)


    # Test difference between sq_len and mp_len
    MpLenFalse = CPyExtType("MpLenFalse",
                            "Py_ssize_t my_mp_len(PyObject* a) { return 0; }",
                            mp_length="&my_mp_len")
    assert_false(MpLenFalse, has_dunder_bool=False)


    # Test calling nb_bool directly from native
    Tester = CPyExtType("Tester",
                        """
                        static PyObject* test(PyObject* unused, PyObject* object) {
                            return PyLong_FromLong(Py_TYPE(object)->tp_as_number->nb_bool(object));
                        }
                        """,
                        tp_methods='{"test", (PyCFunction)test, METH_O, ""}')
    tester = Tester()

    assert tester.test(True) == 1
    assert tester.test(False) == 0
    assert tester.test(42) == 1

    assert tester.test(ManagedBoolTrue()) == 1
    assert tester.test(ManagedBoolFalse()) == 0

    assert tester.test(ManagedLenTrueNbBoolFalse()) == 0
    assert tester.test(NbBoolTrue()) == 1
    assert tester.test(NbBoolFalse()) == 0

    # -----------------
    # Inheritance tests:

    # Python class from builtin
    class MyInt(int): pass
    class MyNbBoolTrueInt(int):
        def __bool__(self): return True
    class MyNbBoolFalseInt(int):
        def __bool__(self): return False

    assert MyInt(42)
    assert not MyInt(0)

    assert MyNbBoolTrueInt(0)
    assert MyNbBoolTrueInt(42)
    assert not MyNbBoolFalseInt(0)
    assert not MyNbBoolFalseInt(42)

    # Native class from builtin
    MyNativeInt = CPyExtType("MyNativeInt",
                             ready_code = "MyNativeIntType.tp_new = PyLong_Type.tp_new;",
                             tp_base='&PyLong_Type',
                             struct_base='PyLongObject base;')
    assert MyNativeInt(42) == 42
    assert not MyNativeInt(0)
    assert MyNativeInt(42)

    # Python class from native class
    class MyPyInheritedNbBool(MyNativeInt):
        pass

    assert not MyPyInheritedNbBool(0)
    assert MyPyInheritedNbBool(42)

    # Python class inherits nb_bool from native, it is not overridden by declaring __len__
    class MyPyInheritedNbBoolWithLen(MyNativeInt):
        def __len__(self):
            return 0

    assert MyPyInheritedNbBoolWithLen(42)

    # Native class inherits native class
    def create_native(name, base, code='', **kwargs):
        typedecl = CPyExtTypeDecl(name, code, **kwargs)
        creator = CPyExtType(name + 'Creator',
                             code =
                             typedecl +
                             """
                                 static PyObject* create(PyObject* unused, PyObject* object) {{
                                     {0}Type.tp_base = (PyTypeObject*) object;
                                     if (PyType_Ready(&{0}Type) < 0)
                                         return NULL;
                                     return (PyObject*) &{0}Type;
                                 }}
                             """.format(name),
                             tp_methods='{"create", (PyCFunction)create, METH_O, ""}')
        type = creator().create(base)
        return type()

    assert not create_native("NativeInheritingNbBoolFalse", NbBoolFalse)
    assert create_native("NativeInheritingNbBoolTrue", NbBoolTrue)

    is_windows = sys.platform == "win32"

    x = create_native("NInhSqLenTNbBoolF", SqLenTrueBoolFalse)
    assert len(x) == 42
    assert not x

    x = create_native(
        "NInhSqLenFOverrNbBoolT",
        SqLenFalse,
        "int my_nb_bool(PyObject* a) { return 1; }",
        nb_bool="&my_nb_bool")
    assert len(x) == 0
    assert x

    x = create_native(
        "NInhSqLenFOverrNbBoolTLen42",
        SqLenFalse,
        "Py_ssize_t my_sq_len(PyObject* a) { return 42; }" +
        "int my_nb_bool(PyObject* a) { return 1; }",
        sq_length="&my_sq_len",
        nb_bool="&my_nb_bool")
    assert len(x) == 42
    assert x

    # Native heap type extending managed class
    def create_heap(name, base=object, code='', slots=''):
        creator = CPyExtType(name + 'Creator',
                             code = code +
                                    """
                                        static PyObject* create(PyObject* unused, PyObject* bases) {{
                                           PyType_Slot slots[] = {{
                                               {slots}
                                               {{0}}
                                           }};
                                           PyType_Spec spec = {{ "{name}Type", sizeof(PyHeapTypeObject), 0, Py_TPFLAGS_DEFAULT | Py_TPFLAGS_BASETYPE, slots }};
                                           PyObject* result = PyType_FromSpecWithBases(&spec, bases);
                                           return result;
                                        }}
                                    """.format(name=name, slots=slots),
                             tp_methods='{"create", (PyCFunction)create, METH_O, ""}')
        type = creator().create((base,))
        return type()

    class Dummy: pass
    assert create_heap('NativeInheritingDummy', base=Dummy)

    assert not create_heap('NInhManBoolF', base=ManagedBoolFalse)

    #x = create_heap("NativeInheritingManagedBoolTrue", base=ManagedBoolTrue)
    #assert len(x) == 42
    #assert x

    x = create_heap("NInhManLenTrueNbBoolF", base=ManagedLenTrueNbBoolFalse)
    assert len(x) == 1
    assert not x

    # Native heap type extending native static type
    NativeNbBoolFalse = type(create_native("NInhNbBoolFalse2", base=NbBoolFalse))
    x = create_heap("ComplexInheritance", NativeNbBoolFalse,
                    slots='{Py_sq_length, &my_sq_len},',
                    code='Py_ssize_t my_sq_len(PyObject* a) { return 42; }')
    assert not x
    assert len(x) == 42

    # Inheritance of the sq_len & mp_len slots:
    SqMpLen = type(create_heap("SqMpLen",
                               slots=
                               '{Py_sq_length, &my_sq_len},' +
                               '{Py_mp_length, &my_mp_len},',
                               code=
                               'Py_ssize_t my_sq_len(PyObject* a) { return 22; }' +
                               'Py_ssize_t my_mp_len(PyObject* a) { return 0; }'
                               ))
    # __len__ gets mapped to my_mp_len (in add_operators), but builtin len (PyObject_Size) tries sq_len first,
    # however PyObject_IsTrue tries mp_len first:
    x = SqMpLen()
    assert len(x) == 22
    assert x.__len__() == 0
    assert not x

    x = create_heap("NDummyExtSqMpLen", base=SqMpLen)
    assert len(x) == 22
    assert x.__len__() == 0
    assert not x

    class ManagedLen(SqMpLen): pass
    # fixup_slot_dispatchers finds the inherited "__len__" in MRO, it unwraps the descriptor
    # to find out address of my_mp_len and sets that as the slot value of both sq_len and mp_len
    x = ManagedLen()
    assert len(x) == 0
    assert x.__len__() == 0
    assert not x

    # Native extends managed which extends native: seqfault on 3.10:
    # x = create_heap("SqMpLenExtendingManaged", base=ManagedLen)

    # Some interaction with the test driver or something else causes this test to segfault
    # on CPython 3.11 (debug mode doesn't give anything useful). This test case worked on
    # CPython in a self-contained test case generated by slots_fuzzer.py. It still tests
    # useful corner case (we need to update lookups cached in TpSlotPython on inheritance)
    if GRAALPYTHON:
        class ManagedLen0(object):
            def __len__(self): return 0

        TestNbBoolMpLenNoSqLen = CPyExtHeapType("TestNbBMpLenNoSqLen",
                                                bases=(ManagedLen0,),
                                                slots=[
                                                    '{Py_nb_bool, &my_bool}',
                                                    '{Py_mp_length, &my_mp_len}',
                                                ],
                                                code='''
                                    int my_bool(PyObject* self) { return 1; }
                                    Py_ssize_t my_mp_len(PyObject* self) { return 1; }
                               ''')
        # What happens here:
        # 1) add_operators for TestNbBoolMpLenNoSqLenType adds __len__,
        # because of mp_length in the declared slots of TestNbBoolMpLenNoSqLenType
        # 2) type_ready_inherit inherits sq_length from ManagedLen0, which is a wrapper C function
        # that looks up and calls __len__, but when this wrapper C function gets called on
        # TestNbBoolMpLenNoSqLen, it finds and calls the __len__ created in add_operators
        # instead of the __len__ from ManagedLen0
        assert len(TestNbBoolMpLenNoSqLen()) == 1
        assert TestNbBoolMpLenNoSqLen().__len__() == 1
        assert TestNbBoolMpLenNoSqLen().__len__ != ManagedLen0().__len__

        # NOTE: if we compared the native pointers, we'd get one observable difference between
        # CPython and GraalPy, because GraalPy slots close over the lookup results, we must use
        # different slot pointer in TestNbBoolMpLenNoSqLenType.sq_length than in ManagedLen0.sq_length
