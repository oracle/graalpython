# Copyright (c) 2020, 2024, Oracle and/or its affiliates. All rights reserved.
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

from . import CPyExtType, CPyExtTestCase, CPyExtFunction, unhandled_error_compare 
__dir__ = __file__.rpartition("/")[0]


def _reference_classmethod(args):
    if isinstance(args[0], type(list.append)):
        return classmethod(args[0])()
    raise TypeError

class TestDescrObject(object):

    def test_new_classmethod(self):

        TestNewClassMethod = CPyExtType("TestNewClassMethod",
                             """
                             static PyObject * c_void_p_from_param(PyObject *self, PyObject *value) {
                                 /* just returns self which should be the class */
                                 return self;
                             }
                             static PyMethodDef c_void_p_method = { "from_param", c_void_p_from_param, METH_O };
                             """,
                             post_ready_code="""
                             PyObject *meth = PyDescr_NewClassMethod(&TestNewClassMethodType, &c_void_p_method);
                             if (!meth) {
                                 return NULL;
                             }
                             int x = PyDict_SetItemString(TestNewClassMethodType.tp_dict, c_void_p_method.ml_name, meth);
                             Py_DECREF(meth);
                             if (x == -1) {
                                 return NULL;
                             }
                             """,
                             )

        tester = TestNewClassMethod()
        assert tester.from_param(123) == TestNewClassMethod

    def test_new_descr(self):
        C = CPyExtType("C_", 
                            '''
                            PyTypeObject A_Type = {
                                PyVarObject_HEAD_INIT(NULL, 0)
                                .tp_name = "A",
                                .tp_basicsize = sizeof(C_Object),
                                .tp_flags = Py_TPFLAGS_DEFAULT | Py_TPFLAGS_BASETYPE,
                            };

                            static PyObject *
                            foo(PyObject *type, PyObject *value) {
                                Py_INCREF(Py_None);
                                return Py_None;
                            }

                            static PyMethodDef foo_method = {
                                 "foo", foo, METH_O 
                            };

                            static PyObject* B_new(PyTypeObject* type, PyObject* a, PyObject* b) {
                                PyTypeObject *result;
                                PyMethodDef *ml;
                                PyObject *meth;
                                int x;
                                result = (PyTypeObject *)PyType_Type.tp_new(type, a, b);
                                if (result == NULL)
                                    return NULL;

                                ml = &foo_method;

                                meth = PyDescr_NewClassMethod(result, ml);
                                if (!meth) {
                                    Py_DECREF(result);
                                    return NULL;
                                }
                                x = PyDict_SetItemString(result->tp_dict,
                                                        ml->ml_name,
                                                        meth);
                                Py_DECREF(meth);
                                if (x == -1) {
                                    Py_DECREF(result);
                                    return NULL;
                                }

                                return (PyObject *) result;
                            }

                            PyTypeObject B_Type = {
                                PyVarObject_HEAD_INIT(NULL, 0)
                                .tp_name = "B",
                                .tp_flags = Py_TPFLAGS_DEFAULT | Py_TPFLAGS_BASETYPE,
                                .tp_new = B_new,
                            };

                            static PyObject* C_new(PyTypeObject* cls, PyObject* a, PyObject* b) {
                                 return cls->tp_alloc(cls, 0);
                            }

                            static int
                            C_traverse(C_Object *self, visitproc visit, void *arg) {
                                // This helps to avoid setting 'Py_TPFLAGS_HAVE_GC'
                                // see typeobject.c:inherit_special:241
                                return 0;
                            }

                            static int
                            C_clear(C_Object *self) {
                                // This helps to avoid setting 'Py_TPFLAGS_HAVE_GC'
                                // see typeobject.c:inherit_special:241
                                return 0;
                            }

                            static int
                            C_init(C_Object *self, PyObject *a, PyObject *k)
                            {
                                return 0;
                            }

                             ''',
                             tp_traverse="(traverseproc)C_traverse",
                             tp_clear="(inquiry)C_clear",
                             tp_new="C_new",
                             tp_init="(initproc)C_init",
                             cmembers="int some_int;",
                             ready_code='''
                                if (PyType_Ready(&A_Type) < 0)
                                    return NULL;

                                B_Type.tp_base = &PyType_Type;
                                if (PyType_Ready(&B_Type) < 0)
                                    return NULL;
                                    
                                Py_SET_TYPE(&C_Type, &B_Type); 
                                C_Type.tp_base = &A_Type;''',
                            )
        
        class bar(C):
            pass
        assert bar().foo(None) is None

    def test_new_descr_getset(self):
        F = CPyExtType("F_",
                            '''
                            PyTypeObject D_Type = {
                                PyVarObject_HEAD_INIT(NULL, 0)
                                .tp_name = "A",
                                .tp_basicsize = sizeof(F_Object),
                                .tp_flags = Py_TPFLAGS_DEFAULT | Py_TPFLAGS_BASETYPE,
                            };

                            static PyObject *
                            foo_get(F_Object *obj, void *Py_UNUSED(ignored))
                            {
                                Py_INCREF(obj->obj);
                                return obj->obj;
                            }

                            static int
                            foo_set(F_Object *obj, PyObject *value, void *Py_UNUSED(ignored))
                            {
                                size_t v = PyLong_AsSize_t(value) + 1;
                                obj->obj = PyLong_FromSize_t(v);
                                Py_INCREF(obj->obj);
                                return 0;
                            }

                            static PyGetSetDef foo_getset = {
                                "foo", (getter)foo_get, (setter)foo_set, "bar"
                            };

                            static PyObject* E_new(PyTypeObject* type, PyObject* a, PyObject* b) {
                                PyTypeObject *result;
                                PyGetSetDef *getset_def;
                                PyObject *get_set;
                                int x;
                                result = (PyTypeObject *)PyType_Type.tp_new(type, a, b);
                                if (result == NULL)
                                    return NULL;

                                getset_def = &foo_getset;

                                get_set = PyDescr_NewGetSet(result, getset_def);
                                if (!get_set) {
                                    Py_DECREF(result);
                                    return NULL;
                                }
                                x = PyDict_SetItemString(result->tp_dict,
                                                        getset_def->name,
                                                        get_set);
                                Py_DECREF(get_set);
                                if (x == -1) {
                                    Py_DECREF(result);
                                    return NULL;
                                }

                                return (PyObject *) result;
                            }

                            PyTypeObject E_Type = {
                                PyVarObject_HEAD_INIT(NULL, 0)
                                .tp_name = "B",
                                .tp_flags = Py_TPFLAGS_DEFAULT | Py_TPFLAGS_BASETYPE,
                                .tp_new = E_new,
                            };

                            static PyObject* F_new(PyTypeObject* cls, PyObject* a, PyObject* b) {
                                 return cls->tp_alloc(cls, 0);
                            }

                            static int
                            F_traverse(F_Object *self, visitproc visit, void *arg) {
                                // This helps to avoid setting 'Py_TPFLAGS_HAVE_GC'
                                // see typeobject.c:inherit_special:241
                                return 0;
                            }

                            static int
                            F_clear(F_Object *self) {
                                // This helps to avoid setting 'Py_TPFLAGS_HAVE_GC'
                                // see typeobject.c:inherit_special:241
                                return 0;
                            }

                            static int
                            F_init(F_Object *self, PyObject *a, PyObject *k)
                            {
                                return 0;
                            }

                             ''',
                             tp_traverse="(traverseproc)F_traverse",
                             tp_clear="(inquiry)F_clear",
                             tp_new="F_new",
                             tp_init="(initproc)F_init",
                             cmembers="PyObject* obj;",
                             ready_code='''
                                if (PyType_Ready(&D_Type) < 0)
                                    return NULL;

                                E_Type.tp_base = &PyType_Type;
                                if (PyType_Ready(&E_Type) < 0)
                                    return NULL;

                                Py_SET_TYPE(&F_Type, &E_Type);
                                F_Type.tp_base = &D_Type;''',
                            )

        class baz(F):
            pass
        b = baz()
        b.foo = 41
        assert b.foo == 42

class TestDescr(CPyExtTestCase):

    def compile_module(self, name):
        type(self).mro()[1].__dict__["test_%s" % name].create_module(name)
        super(TestDescr, self).compile_module(name)

    test_PyDescr_NewClassMethod = CPyExtFunction(
        _reference_classmethod,
        lambda: (
            (type.__repr__,),
            (lambda x: x,),
        ),
        code="""
        static PyObject* wrap_PyDescr_NewClassMethod(PyObject* method) {
            PyTypeObject *methoddescr_type = NULL;
            PyObject *meth = PyObject_GetAttrString((PyObject*)&PyList_Type, "append");
            if (!meth) return NULL;
            methoddescr_type = Py_TYPE(meth);
            Py_DECREF(meth);
            if (PyObject_TypeCheck(method, (PyTypeObject *)methoddescr_type)) {
                PyMethodDescrObject *descr = (PyMethodDescrObject *)method;
#ifdef GRAALVM_PYTHON
                PyTypeObject *d_type = PyDescrObject_GetType(method);
                PyObject *class_method = PyDescr_NewClassMethod(d_type, PyMethodDescrObject_GetMethod(method));
#else
                PyTypeObject *d_type = descr->d_common.d_type;
                PyObject *class_method = PyDescr_NewClassMethod(d_type, descr->d_method);
#endif
                PyObject *args = PyTuple_New(0);
                Py_INCREF(args);
                PyObject *result = PyObject_CallObject(class_method, args);
                Py_DECREF(args); Py_DECREF(args);
                Py_DECREF(class_method);
                return result;
            }
            PyErr_SetString(PyExc_TypeError, "Class-level classmethod() can only be called on a method_descriptor or instance method.");
            return NULL;
        }
        """,
        resultspec="O",
        argspec="O",
        arguments=["PyObject* method"],
        callfunction="wrap_PyDescr_NewClassMethod",
        cmpfunc=unhandled_error_compare
    )
