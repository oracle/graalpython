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

PyTypeObject PyCapsule_Type = PY_TRUFFLE_TYPE("PyCapsule", &PyType_Type, 0);

PyObject* PyCapsule_New(void *pointer, const char *name, PyCapsule_Destructor destructor) {
    return (PyObject *)polyglot_as_PyCapsule(to_sulong(polyglot_invoke(PY_TRUFFLE_CEXT, "PyCapsule", name ? polyglot_from_string(name, "ascii") : to_java(Py_None), pointer, destructor)));
}

void* PyCapsule_GetContext(PyObject *o) {
    void *result = polyglot_invoke(PY_TRUFFLE_CEXT, "PyCapsule_GetContext", to_java(o));
    if (result == ERROR_MARKER) {
        return NULL;
    }
    return result;
}

void* PyCapsule_GetPointer(PyObject *o, const char *name) {
    void* namearg;
    if (name == NULL) {
        namearg = to_java(Py_None);
    } else {
        namearg = polyglot_from_string(name, "ascii");
    }
    void *result = polyglot_invoke(PY_TRUFFLE_CEXT, "PyCapsule_GetPointer", to_java(o), namearg);
    if (result == ERROR_MARKER) {
        return NULL;
    }
    return result;
}

void* PyCapsule_Import(const char *name, int no_block) {
    // TODO (tfel): no_block is currently ignored
    void *result = polyglot_invoke(PY_TRUFFLE_CEXT, "PyCapsule_Import", polyglot_from_string(name, "ascii"), no_block);
    if (result == ERROR_MARKER) {
        return NULL;
    }
    return (void*)to_sulong(result);
}

int PyCapsule_IsValid(PyObject *o, const char *name) {
    return o != NULL && polyglot_invoke(PY_TRUFFLE_CEXT, "PyCapsule_IsValid", to_java(o), polyglot_from_string(name, "ascii"));
}
