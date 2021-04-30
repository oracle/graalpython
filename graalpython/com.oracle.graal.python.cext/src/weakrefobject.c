/*
 * Copyright (c) 2019, 2021, Oracle and/or its affiliates. All rights reserved.
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

PyTypeObject _PyWeakref_RefType = PY_TRUFFLE_TYPE("weakref", &PyType_Type, Py_TPFLAGS_DEFAULT | Py_TPFLAGS_HAVE_GC | Py_TPFLAGS_BASETYPE, sizeof(PyWeakReference));
PyTypeObject _PyWeakref_ProxyType = PY_TRUFFLE_TYPE("weakproxy", &PyType_Type, Py_TPFLAGS_DEFAULT | Py_TPFLAGS_HAVE_GC, sizeof(PyWeakReference));
PyTypeObject _PyWeakref_CallableProxyType = PY_TRUFFLE_TYPE("weakcallableproxy", &PyType_Type, Py_TPFLAGS_DEFAULT | Py_TPFLAGS_HAVE_GC, sizeof(PyWeakReference));

void PyObject_ClearWeakRefs(PyObject *object) {
	// TODO: implement
}

void *PY_WEAKREF_MODULE;
__attribute__((constructor (__COUNTER__)))
static void initialize_upcall_functions() {
    PY_WEAKREF_MODULE = (void*)polyglot_eval("python", "import _weakref\n_weakref");
}

PyObject *PyWeakref_NewRef(PyObject *object, PyObject *callback) {
    if (callback == NULL) {
        return UPCALL_O(PY_WEAKREF_MODULE, polyglot_from_string("ReferenceType", SRC_CS), native_to_java(object));
    } else {
        return UPCALL_O(PY_WEAKREF_MODULE, polyglot_from_string("ReferenceType", SRC_CS), native_to_java(object), native_to_java(callback));
    }
}

PyObject *PyWeakref_GetObject(PyObject *ref) {
    return UPCALL_O(native_to_java(ref), polyglot_from_string("__call__", SRC_CS));
}
