/*
 * Copyright (c) 2018, Oracle and/or its affiliates.
 *
 * The Universal Permissive License (UPL), Version 1.0
 *
 * Subject to the condition set forth below, permission is hereby granted to any
 * person obtaining a copy of this software, associated documentation and/or data
 * (collectively the "Software"), free of charge and under any and all copyright
 * rights in the Software, and any and all patent rights owned or freely
 * licensable by each licensor hereunder covering either (i) the unmodified
 * Software as contributed to or provided by such licensor, or (ii) the Larger
 * Works (as defined below), to deal in both
 *
 * (a) the Software, and
 * (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
 *     one is included with the Software (each a "Larger Work" to which the
 *     Software is contributed by such licensors),
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

PyObject* PyImport_ImportModule(const char *name) {
    return UPCALL_CEXT_O("PyImport_ImportModule", polyglot_from_string(name, SRC_CS));
}

PyObject* PyImport_GetModuleDict() {
    return UPCALL_CEXT_O("PyImport_GetModuleDict");
}

PyObject* PyImport_AddModuleObject(PyObject *name) {
    return _PyImport_AddModuleObject(name, PyImport_GetModuleDict());
}

PyObject* PyImport_AddModule(const char *name) {
    PyObject *nameobj = PyUnicode_FromString(name);
    if (nameobj == NULL) {
        return NULL;
    }
    return PyImport_AddModuleObject(nameobj);
}

PyObject* _PyImport_AddModuleObject(PyObject *name, PyObject *modules) {
    PyObject* m = PyObject_GetItem(modules, name);
    if (PyErr_ExceptionMatches(PyExc_KeyError)) {
        PyErr_Clear();
    }
    if (PyErr_Occurred()) {
        return NULL;
    }
    if (m != NULL && PyModule_Check(m)) {
        return m;
    }
    m = PyModule_NewObject(name);
    if (m == NULL) {
        return NULL;
    }
    if (PyObject_SetItem(modules, name, m) != 0) {
        return NULL;
    }
    return m;
}
