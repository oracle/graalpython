/*
 * Copyright (c) 2018, 2023, Oracle and/or its affiliates. All rights reserved.
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
    if (result == -1L && PyErr_Occurred() != NULL) {
    	PyErr_Clear();
    	*overflow = 1;
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
    if (result == -1L && PyErr_Occurred() != NULL) {
    	PyErr_Clear();
    	*overflow = 1;
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

// taken from CPython "Objects/longobject.c"
int _PyLong_AsByteArray(PyLongObject* v, unsigned char* bytes, size_t n, int little_endian, int is_signed) {
    Py_ssize_t i;               /* index into v->ob_digit */
    Py_ssize_t ndigits;         /* |v->ob_size| */
    twodigits accum;            /* sliding register */
    unsigned int accumbits;     /* # bits in accum */
    int do_twos_comp;           /* store 2's-comp?  is_signed and v < 0 */
    digit carry;                /* for computing 2's-comp */
    size_t j;                   /* # bytes filled */
    unsigned char* p;           /* pointer to next byte in bytes */
    int pincr;                  /* direction to move p */

    assert(v != NULL && PyLong_Check(v));

    if (Py_SIZE(v) < 0) {
        ndigits = -(Py_SIZE(v));
        if (!is_signed) {
            PyErr_SetString(PyExc_OverflowError,
                            "can't convert negative int to unsigned");
            return -1;
        }
        do_twos_comp = 1;
    }
    else {
        ndigits = Py_SIZE(v);
        do_twos_comp = 0;
    }

    if (little_endian) {
        p = bytes;
        pincr = 1;
    }
    else {
        p = bytes + n - 1;
        pincr = -1;
    }

    /* Copy over all the Python digits.
       It's crucial that every Python digit except for the MSD contribute
       exactly PyLong_SHIFT bits to the total, so first assert that the int is
       normalized. */
    digit* digits = PyLongObject_ob_digit(v);
    assert(ndigits == 0 || digits[ndigits - 1] != 0);
    j = 0;
    accum = 0;
    accumbits = 0;
    carry = do_twos_comp ? 1 : 0;
    for (i = 0; i < ndigits; ++i) {
        digit thisdigit = digits[i];
        if (do_twos_comp) {
            thisdigit = (thisdigit ^ PyLong_MASK) + carry;
            carry = thisdigit >> PyLong_SHIFT;
            thisdigit &= PyLong_MASK;
        }
        /* Because we're going LSB to MSB, thisdigit is more
           significant than what's already in accum, so needs to be
           prepended to accum. */
        accum |= (twodigits)thisdigit << accumbits;

        /* The most-significant digit may be (probably is) at least
           partly empty. */
        if (i == ndigits - 1) {
            /* Count # of sign bits -- they needn't be stored,
             * although for signed conversion we need later to
             * make sure at least one sign bit gets stored. */
            digit s = do_twos_comp ? thisdigit ^ PyLong_MASK : thisdigit;
            while (s != 0) {
                s >>= 1;
                accumbits++;
            }
        }
        else
            accumbits += PyLong_SHIFT;

        /* Store as many bytes as possible. */
        while (accumbits >= 8) {
            if (j >= n)
                goto Overflow;
            ++j;
            *p = (unsigned char)(accum & 0xff);
            p += pincr;
            accumbits -= 8;
            accum >>= 8;
        }
    }

    /* Store the straggler (if any). */
    assert(accumbits < 8);
    assert(carry == 0);  /* else do_twos_comp and *every* digit was 0 */
    if (accumbits > 0) {
        if (j >= n)
            goto Overflow;
        ++j;
        if (do_twos_comp) {
            /* Fill leading bits of the byte with sign bits
               (appropriately pretending that the int had an
               infinite supply of sign bits). */
            accum |= (~(twodigits)0) << accumbits;
        }
        *p = (unsigned char)(accum & 0xff);
        p += pincr;
    }
    else if (j == n && n > 0 && is_signed) {
        /* The main loop filled the byte array exactly, so the code
           just above didn't get to ensure there's a sign bit, and the
           loop below wouldn't add one either.  Make sure a sign bit
           exists. */
        unsigned char msb = *(p - pincr);
        int sign_bit_set = msb >= 0x80;
        assert(accumbits == 0);
        if (sign_bit_set == do_twos_comp)
            return 0;
        else
            goto Overflow;
    }

    /* Fill remaining bytes with copies of the sign bit. */
    {
        unsigned char signbyte = do_twos_comp ? 0xffU : 0U;
        for ( ; j < n; ++j, p += pincr)
            *p = signbyte;
    }

    return 0;

  Overflow:
    PyErr_SetString(PyExc_OverflowError, "int too big to convert");
    return -1;

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

