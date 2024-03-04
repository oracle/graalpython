/* Copyright (c) 2018, 2024, Oracle and/or its affiliates.
 * Copyright (C) 1996-2017 Python Software Foundation
 *
 * Licensed under the PYTHON SOFTWARE FOUNDATION LICENSE VERSION 2
 */
#include "capi.h"

#include <stdbool.h>
#include <stddef.h>

unsigned char _PyLong_DigitValue[];

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
    int overflow = 0;
    int digits = 0;
    char prev;
    long value;
    while (true) {
    	if (*str == '_') {
            if (prev == '_') {
                goto error;
            }
    	} else {
    		unsigned char digit = _PyLong_DigitValue[Py_CHARMASK(*str)];
    		if (digit >= base) {
    			break;
    		}
    		long new_value = value * base - digit;
    		if (new_value > value) {
    			// overflow
    			overflow = 1;
    		}
    		value = new_value;
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
    if (value == LONG_MIN && !negative) {
    	overflow = 1;
    }

    if (overflow) {
    	PyObject* string = PyUnicode_FromStringAndSize(numberStart, digits);
    	PyObject* result = GraalPyTruffleLong_FromString(string, base, negative);
    	Py_DecRef(string);
    	return result;
    } else {
    	return PyLong_FromLong(negative ? value : -value);
    }

 error:
    PyErr_Format(PyExc_ValueError,
                 "invalid literal for int() with base %d: %.200R",
                 base, PyUnicode_FromString(inputStr));
    return NULL;
}


/*
 * There are 4 different modes for 'PyLong_AsPrimitive:
 * - MODE_COERCE_UNSIGNED
 *     Will coerce the object to a Python integer and returns it as unsigned primitive.
 * - MODE_COERCE_SIGNED 1
 *     Will coerce the object to a Python integer and returns it as signed primitive.
 * - MODE_PINT_UNSIGNED 2
 *     Requires the object to be a Python integer and returns it as unsigned primitive.
 * - MODE_PINT_SIGNED 3
 *     Requires the object to be a Python integer and returns it as signed primitive.
 * - MODE_COERCE_MASK 4
 *     Will coerce the object to a Python integer and does a lossy cast to an unsigned primitive.
 */
#define MODE_COERCE_UNSIGNED 0
#define MODE_COERCE_SIGNED 1
#define MODE_PINT_UNSIGNED 2
#define MODE_PINT_SIGNED 3
#define MODE_COERCE_MASK 4

long PyLong_AsLong(PyObject *obj) {
    return GraalPyTruffleLong_AsPrimitive(obj, MODE_COERCE_SIGNED, sizeof(long));
}

long PyLong_AsLongAndOverflow(PyObject *obj, int *overflow) {
    if (obj == NULL) {
        PyErr_BadInternalCall();
        return -1;
    }
    long result = GraalPyTruffleLong_AsPrimitive(obj, MODE_COERCE_SIGNED, sizeof(long));
    if (result == -1L && PyErr_Occurred() && PyErr_ExceptionMatches(PyExc_OverflowError)) {
        PyErr_Clear();
        *overflow = _PyLong_Sign(obj);
    } else {
        *overflow = 0;
    }
    return result;
}

long long PyLong_AsLongLong(PyObject *obj) {
    if (obj == NULL) {
        PyErr_BadInternalCall();
        return -1;
    }
    return (long long) GraalPyTruffleLong_AsPrimitive(obj, MODE_COERCE_SIGNED, sizeof(long));
}

long long PyLong_AsLongLongAndOverflow(PyObject *obj, int *overflow) {
    long long result = PyLong_AsLongLong(obj);
    if (result == -1L && PyErr_Occurred() && PyErr_ExceptionMatches(PyExc_OverflowError)) {
        PyErr_Clear();
        *overflow = _PyLong_Sign(obj);
    } else {
        *overflow = 0;
    }
    return result;
}

unsigned long long PyLong_AsUnsignedLongLong(PyObject *obj) {
    if (obj == NULL) {
        PyErr_BadInternalCall();
        return (unsigned long long) -1;
    }
    return (unsigned long long) GraalPyTruffleLong_AsPrimitive(obj, MODE_PINT_UNSIGNED, sizeof(unsigned long long));
}

unsigned long long PyLong_AsUnsignedLongLongMask(PyObject *obj) {
    if (obj == NULL) {
        PyErr_BadInternalCall();
        return (unsigned long long) -1;
    }
    return (unsigned long long) GraalPyTruffleLong_AsPrimitive(obj, MODE_COERCE_MASK, sizeof(unsigned long long));
}

unsigned long PyLong_AsUnsignedLong(PyObject *obj) {
    if (obj == NULL) {
        PyErr_BadInternalCall();
        return (unsigned long) -1;
    }
    return (unsigned long) GraalPyTruffleLong_AsPrimitive(obj, MODE_PINT_UNSIGNED, sizeof(unsigned long));
}

unsigned long PyLong_AsUnsignedLongMask(PyObject *obj) {
    if (obj == NULL) {
        PyErr_BadInternalCall();
        return (unsigned long) -1;
    }
    return (unsigned long) GraalPyTruffleLong_AsPrimitive(obj, MODE_COERCE_MASK, sizeof(unsigned long));
}

Py_ssize_t PyLong_AsSsize_t(PyObject *obj) {
    return GraalPyTruffleLong_AsPrimitive(obj, MODE_PINT_SIGNED, sizeof(Py_ssize_t));
}

size_t PyLong_AsSize_t(PyObject *obj) {
    return GraalPyTruffleLong_AsPrimitive(obj, MODE_PINT_UNSIGNED, sizeof(size_t));
}

typedef PyObject* (*fromVoidPtr_fun_t)(void*);
PyObject * PyLong_FromVoidPtr(void *p) {
	// directly do the upcall to avoid a cast to primitive and reference counting
    return ((fromVoidPtr_fun_t)GraalPyLong_FromUnsignedLongLong)(p);
}

double PyLong_AsDouble(PyObject *v)  {
    return (double)PyLong_AsLongLong(v);
}

// Taken from CPython 3.8.1
int _PyLong_AsInt(PyObject *obj) {
    int overflow;
    long result = PyLong_AsLongAndOverflow(obj, &overflow);
    if (overflow || result > INT_MAX || result < INT_MIN) {
        /* XXX: could be cute and give a different
           message for overflow == -1 */
        PyErr_SetString(PyExc_OverflowError,
                        "Python int too large to convert to C int");
        return -1;
    }
    return (int)result;
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

int
_PyLong_UnsignedLong_Converter(PyObject *obj, void *ptr)
{
    unsigned long uval;

    if (PyLong_Check(obj) && _PyLong_Sign(obj) < 0) {
        PyErr_SetString(PyExc_ValueError, "value must be positive");
        return 0;
    }
    uval = PyLong_AsUnsignedLong(obj);
    if (uval == (unsigned long)-1 && PyErr_Occurred())
        return 0;

    *(unsigned long *)ptr = uval;
    return 1;
}
