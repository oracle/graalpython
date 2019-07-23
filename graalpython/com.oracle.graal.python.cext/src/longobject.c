/*
 * Copyright (c) 2018, 2019, Oracle and/or its affiliates. All rights reserved.
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

#include <stdbool.h>
#include <stddef.h>

PyTypeObject PyLong_Type = PY_TRUFFLE_TYPE("int", &PyType_Type, Py_TPFLAGS_DEFAULT | Py_TPFLAGS_BASETYPE | Py_TPFLAGS_LONG_SUBCLASS, offsetof(PyLongObject, ob_digit));

PyObject * _PyLong_Zero;
PyObject * _PyLong_One;

UPCALL_ID(PyLong_AsPrimitive);
long PyLong_AsLong(PyObject *obj) {
    return UPCALL_CEXT_L(_jls_PyLong_AsPrimitive, native_to_java(obj), 1, sizeof(long));
}

long PyLong_AsLongAndOverflow(PyObject *obj, int *overflow) {
    if (obj == NULL) {
        PyErr_BadInternalCall();
        return -1;
    }
    long result = UPCALL_CEXT_L(_jls_PyLong_AsPrimitive, native_to_java(obj), 1, sizeof(long));
    *overflow = result == -1L && PyErr_Occurred() != NULL;
    return result;
}

long long PyLong_AsLongLong(PyObject *obj) {
    return as_long_long(obj);
}

long long PyLong_AsLongLongAndOverflow(PyObject *obj, int *overflow) {
    long long result = PyLong_AsLongLong(obj);
    *overflow = result == -1L && PyErr_Occurred() != NULL;
    return result;
}

unsigned long long PyLong_AsUnsignedLongLong(PyObject *obj) {
    return as_unsigned_long_long(obj);
}

unsigned long PyLong_AsUnsignedLong(PyObject *obj) {
    if (obj == NULL) {
        PyErr_BadInternalCall();
        return (unsigned long)-1;
    }
    return (unsigned long) UPCALL_CEXT_L(_jls_PyLong_AsPrimitive, native_to_java(obj), 0, sizeof(unsigned long));
}
PyObject * PyLong_FromSsize_t(Py_ssize_t n) {
	return PyLong_FromLongLong(n);
}

UPCALL_ID(PyLong_FromDouble);
PyObject * PyLong_FromDouble(double n) {
    return UPCALL_CEXT_O(_jls_PyLong_FromDouble, n);
}

Py_ssize_t PyLong_AsSsize_t(PyObject *obj) {
    return UPCALL_CEXT_L(_jls_PyLong_AsPrimitive, native_to_java(obj), 1, sizeof(Py_ssize_t));
}

size_t PyLong_AsSize_t(PyObject *obj) {
    return UPCALL_CEXT_L(_jls_PyLong_AsPrimitive, native_to_java(obj), 0, sizeof(size_t));
}

UPCALL_ID(PyLong_FromLongLong);
PyObject * PyLong_FromLong(long n)  {
    return UPCALL_CEXT_O(_jls_PyLong_FromLongLong, n, 1);
}

PyObject * PyLong_FromLongLong(long long n)  {
    return UPCALL_CEXT_O(_jls_PyLong_FromLongLong, n, 1);
}

PyObject * PyLong_FromUnsignedLong(unsigned long n) {
	return PyLong_FromUnsignedLongLong(n);
}

PyObject * PyLong_FromUnsignedLongLong(unsigned long long n) {
    return UPCALL_CEXT_O(_jls_PyLong_FromLongLong, n, 0);
}

PyObject * PyLong_FromVoidPtr(void *p) {
	// directly do the upcall to avoid a cast to primitive
    return UPCALL_CEXT_O(_jls_PyLong_FromLongLong, p, 0);
}

UPCALL_ID(PyLong_AsVoidPtr);
void * PyLong_AsVoidPtr(PyObject *obj){
    return (void *)UPCALL_CEXT_PTR(_jls_PyLong_AsVoidPtr, native_to_java(obj));
}

UPCALL_ID(_PyLong_Sign);
int _PyLong_Sign(PyObject *vv) {
    return UPCALL_CEXT_I(_jls__PyLong_Sign, native_to_java(vv));
}

PyObject * PyLong_FromSize_t(size_t n)  {
	return PyLong_FromUnsignedLongLong(n);
}

UPCALL_ID(PyLong_FromString);
// partially taken from CPython 3.7.0 "Objects/longobject.c"
PyObject * PyLong_FromString(const char* inputStr, char** pend, int base) {
    int negative = 0, error_if_nonzero = 0;
    char* str = (char*)inputStr;
    if ((base != 0 && base < 2) || base > 36) {
        PyErr_SetString(PyExc_ValueError, "int() arg 2 must be >= 2 and <= 36");
        return NULL;
    }
    while (*str != '\0' && Py_ISSPACE(Py_CHARMASK(*str))) {
        str++;
    }
    if (*str == '+') {
        ++str;
    } else if (*str == '-') {
        ++str;
        negative = 1;
    }
    if (base == 0) {
        if (str[0] != '0') {
            base = 10;
        } else if (str[1] == 'x' || str[1] == 'X') {
            base = 16;
            str += 2;
        } else if (str[1] == 'o' || str[1] == 'O') {
            base = 8;
            str += 2;
        } else if (str[1] == 'b' || str[1] == 'B') {
            base = 2;
            str += 2;
        } else {
            /* "old" (C-style) octal literal, now invalid.
               it might still be zero though */
            error_if_nonzero = 1;
            base = 10;
        }
    }

    char* numberStart = str;
    int digits = 0;
    char prev;
    while (_PyLong_DigitValue[Py_CHARMASK(*str)] < base || *str == '_') {
        if (*str == '_') {
            if (prev == '_') {
                goto error;
            }
        }
        prev = *str;
        ++str;
        ++digits;
    }
    if (prev == '_') {
        /* Trailing underscore not allowed. */
        goto error;
    }
    while (*str != '\0' && Py_ISSPACE(Py_CHARMASK(*str))) {
        str++;
    }
    if (pend != NULL) {
        *pend = str;
    }

    return UPCALL_CEXT_O(_jls_PyLong_FromString, polyglot_from_string_n(numberStart, digits, "ascii"), base, negative);

 error:
    PyErr_Format(PyExc_ValueError,
                 "invalid literal for int() with base %d: %.200R",
                 base, PyUnicode_FromString(inputStr));
    return NULL;
}

/* Table of digit values for 8-bit string -> integer conversion.
 * '0' maps to 0, ..., '9' maps to 9.
 * 'a' and 'A' map to 10, ..., 'z' and 'Z' map to 35.
 * All other indices map to 37.
 * Note that when converting a base B string, a char c is a legitimate
 * base B digit iff _PyLong_DigitValue[Py_CHARPyLong_MASK(c)] < B.
 */
unsigned char _PyLong_DigitValue[256] = {
    37, 37, 37, 37, 37, 37, 37, 37, 37, 37, 37, 37, 37, 37, 37, 37,
    37, 37, 37, 37, 37, 37, 37, 37, 37, 37, 37, 37, 37, 37, 37, 37,
    37, 37, 37, 37, 37, 37, 37, 37, 37, 37, 37, 37, 37, 37, 37, 37,
    0,  1,  2,  3,  4,  5,  6,  7,  8,  9,  37, 37, 37, 37, 37, 37,
    37, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22, 23, 24,
    25, 26, 27, 28, 29, 30, 31, 32, 33, 34, 35, 37, 37, 37, 37, 37,
    37, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22, 23, 24,
    25, 26, 27, 28, 29, 30, 31, 32, 33, 34, 35, 37, 37, 37, 37, 37,
    37, 37, 37, 37, 37, 37, 37, 37, 37, 37, 37, 37, 37, 37, 37, 37,
    37, 37, 37, 37, 37, 37, 37, 37, 37, 37, 37, 37, 37, 37, 37, 37,
    37, 37, 37, 37, 37, 37, 37, 37, 37, 37, 37, 37, 37, 37, 37, 37,
    37, 37, 37, 37, 37, 37, 37, 37, 37, 37, 37, 37, 37, 37, 37, 37,
    37, 37, 37, 37, 37, 37, 37, 37, 37, 37, 37, 37, 37, 37, 37, 37,
    37, 37, 37, 37, 37, 37, 37, 37, 37, 37, 37, 37, 37, 37, 37, 37,
    37, 37, 37, 37, 37, 37, 37, 37, 37, 37, 37, 37, 37, 37, 37, 37,
    37, 37, 37, 37, 37, 37, 37, 37, 37, 37, 37, 37, 37, 37, 37, 37,
};
