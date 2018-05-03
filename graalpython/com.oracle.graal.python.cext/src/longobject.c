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

#include <stdbool.h>

PyTypeObject PyLong_Type = PY_TRUFFLE_TYPE("int", &PyType_Type, Py_TPFLAGS_DEFAULT | Py_TPFLAGS_BASETYPE | Py_TPFLAGS_LONG_SUBCLASS);

long PyLong_AsLong(PyObject *obj) {
    return truffle_invoke_l(PY_TRUFFLE_CEXT, "PyLong_AsPrimitive", to_java(obj), true, sizeof(long), truffle_read_string("long"));
}

long PyLong_AsLongAndOverflow(PyObject *obj, int *overflow) {
    if (obj == NULL) {
        PyErr_BadInternalCall();
        return -1;
    }
    long result = truffle_invoke_l(PY_TRUFFLE_CEXT, "PyLong_AsPrimitive", to_java(obj), true, sizeof(long), truffle_read_string("long"));
    *overflow = result == -1L && PyErr_Occurred();
    return result;
}

unsigned long PyLong_AsUnsignedLong(PyObject *obj) {
    if (obj == NULL) {
        PyErr_BadInternalCall();
        return (unsigned long)-1;
    }
    return (unsigned long) truffle_invoke_l(PY_TRUFFLE_CEXT, "PyLong_AsPrimitive", to_java(obj), false, sizeof(unsigned long), truffle_read_string("unsigned long"));
}
PyObject * PyLong_FromSsize_t(Py_ssize_t n) {
	return PyLong_FromLongLong(n);
}

PyObject * PyLong_FromDouble(double n) {
    PyObject *result = truffle_invoke(PY_TRUFFLE_CEXT, "PyLong_FromLongLong", n, true, ERROR_MARKER);
    if (result == ERROR_MARKER) {
    	return NULL;
    }
    return to_sulong(result);
}

Py_ssize_t PyLong_AsSsize_t(PyObject *obj) {
    return truffle_invoke_l(PY_TRUFFLE_CEXT, "PyLong_AsPrimitive", to_java(obj), true, sizeof(Py_ssize_t), truffle_read_string("ssize_t"));
}

PyObject * PyLong_FromVoidPtr(void *p) {
#if SIZEOF_VOID_P <= SIZEOF_LONG
    return PyLong_FromUnsignedLongLong((unsigned long)p);
#else

#if SIZEOF_LONG_LONG < SIZEOF_VOID_P
#   error "PyLong_FromVoidPtr: sizeof(long long) < sizeof(void*)"
#endif
    return PyLong_FromUnsignedLongLong((unsigned long long)(uintptr_t)p);
#endif /* SIZEOF_VOID_P <= SIZEOF_LONG */
}
void * PyLong_AsVoidPtr(PyObject *obj){
	return (void *)PyLong_AsSsize_t(obj);
}
PyObject * PyLong_FromLong(long n)  {
	return PyLong_FromUnsignedLongLong(n);
}

PyObject * PyLong_FromLongLong(long long n)  {
    PyObject *result = truffle_invoke(PY_TRUFFLE_CEXT, "PyLong_FromLongLong", n, true, ERROR_MARKER);
    if (result == ERROR_MARKER) {
    	return NULL;
    }
    return to_sulong(result);
}

PyObject * PyLong_FromUnsignedLong(unsigned long n) {
	return PyLong_FromUnsignedLongLong(n);
}

PyObject * PyLong_FromUnsignedLongLong(unsigned long long n) {
    PyObject *result = truffle_invoke(PY_TRUFFLE_CEXT, "PyLong_FromLongLong", n, false, ERROR_MARKER);
    if (result == ERROR_MARKER) {
    	return NULL;
    }
    return to_sulong(result);
}

int _PyLong_Sign(PyObject *vv) {
    return truffle_invoke_i(PY_TRUFFLE_CEXT, "_PyLong_Sign", to_java(vv));
}
