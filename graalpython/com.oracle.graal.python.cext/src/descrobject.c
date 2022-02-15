/*
 * Copyright (c) 2018, 2022, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 *
 * Subject to the condition set forth below, permission is hereby granted to any
 * person obtaining a copy of this software, associated documentation and/or
 * data (collectively the "Software"), free of charge and under any and all
 * copyright rights in the Software, and any and all patent rights owned or
 * freely licensable by each licensor hereunder covering either (i) the
 * unmodified Software as contributed to or provided by such licensor, or (ii)
 * the Larger Works (as defined below), to deal in both
 *
 * (a) the Software, and
 *
 * (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
 * one is included with the Software each a "Larger Work" to which the Software
 * is contributed by such licensors),
 *
 * without restriction, including without limitation the rights to copy, create
 * derivative works of, display, perform, and distribute the Software and make,
 * use, sell, offer for sale, import, export, have made, and have sold the
 * Software and the Larger Work(s), and to sublicense the foregoing rights on
 * either these or other terms.
 *
 * This license is subject to the following condition:
 *
 * The above copyright notice and either this complete permission notice or at a
 * minimum a reference to the UPL must be included in all copies or substantial
 * portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
#include "capi.h"

// taken from CPython "Objects/descrobject.c"
typedef struct {
    PyObject_HEAD
    PyObject *mapping;
} mappingproxyobject;

PyTypeObject PyGetSetDescr_Type = PY_TRUFFLE_TYPE("getset_descriptor", &PyType_Type, Py_TPFLAGS_DEFAULT | Py_TPFLAGS_HAVE_GC, sizeof(PyGetSetDescrObject));
/* NOTE: we use the same Python type (namely 'PBuiltinFunction') for 'wrapper_descriptor' as for 'method_descriptor'; so the flags must be the same! */
PyTypeObject PyWrapperDescr_Type = PY_TRUFFLE_TYPE("wrapper_descriptor", &PyType_Type, Py_TPFLAGS_DEFAULT | Py_TPFLAGS_HAVE_GC | Py_TPFLAGS_METHOD_DESCRIPTOR, sizeof(PyWrapperDescrObject));
PyTypeObject PyMemberDescr_Type = PY_TRUFFLE_TYPE("member_descriptor", &PyType_Type, Py_TPFLAGS_DEFAULT | Py_TPFLAGS_HAVE_GC, sizeof(PyMemberDescrObject));
PyTypeObject PyMethodDescr_Type = PY_TRUFFLE_TYPE_WITH_VECTORCALL("method_descriptor", &PyType_Type, Py_TPFLAGS_DEFAULT | Py_TPFLAGS_HAVE_GC | Py_TPFLAGS_METHOD_DESCRIPTOR| _Py_TPFLAGS_HAVE_VECTORCALL, sizeof(PyMethodDescrObject), offsetof(PyMethodDescrObject, vectorcall));
PyTypeObject PyDictProxy_Type = PY_TRUFFLE_TYPE("mappingproxy", &PyType_Type, Py_TPFLAGS_DEFAULT | Py_TPFLAGS_HAVE_GC, sizeof(mappingproxyobject));
PyTypeObject PyProperty_Type = PY_TRUFFLE_TYPE("property", &PyType_Type, Py_TPFLAGS_DEFAULT | Py_TPFLAGS_HAVE_GC | Py_TPFLAGS_BASETYPE, sizeof(propertyobject));

POLYGLOT_DECLARE_TYPE(mappingproxyobject);

/* Dicts */
UPCALL_ID(PyDictProxy_New);
PyObject* PyDictProxy_New(PyObject *mapping) {
    return (PyObject*) UPCALL_CEXT_O(_jls_PyDictProxy_New, native_to_java(mapping));
}

typedef PyObject* (*PyDescr_NewClassMethod_fun_t)(PyMethodDef* methodDef,
                                                    void* name,
                                                    const char* doc,
                                                    int flags,
                                                    int wrapper,
                                                    void* methObj,
                                                    void* primary);
UPCALL_TYPED_ID(PyDescr_NewClassMethod, PyDescr_NewClassMethod_fun_t);
PyObject* PyDescr_NewClassMethod(PyTypeObject *type, PyMethodDef *method) {
    int flags = method->ml_flags;
    return _jls_PyDescr_NewClassMethod(method,
                    polyglot_from_string(method->ml_name, SRC_CS),
                    method->ml_doc,
                    flags,
                    get_method_flags_wrapper(flags),
                    native_pointer_to_java(method->ml_meth),
                    type);
}

typedef PyObject* (*PyDescr_NewGetSet_fun_t)(void* name,
                                                    PyTypeObject *type,
                                                    void *get,
                                                    void *set,
                                                    const char* doc,
                                                    void *closure);
UPCALL_TYPED_ID(PyDescr_NewGetSet, PyDescr_NewGetSet_fun_t);
PyObject* PyDescr_NewGetSet(PyTypeObject *type, PyGetSetDef *getset) {
    getter getter_fun = getset->get;
    setter setter_fun = getset->set;
    return _jls_PyDescr_NewGetSet(polyglot_from_string(getset->name, SRC_CS),
                    type,
                    getter_fun != NULL ? function_pointer_to_java(getter_fun) : NULL,
                    setter_fun != NULL ? function_pointer_to_java(setter_fun) : NULL,
                    getset->doc,
                    getset->closure);
}
