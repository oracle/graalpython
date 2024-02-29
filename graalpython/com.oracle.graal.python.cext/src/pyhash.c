/*
 * Copyright (c) 2018, 2024, Oracle and/or its affiliates. All rights reserved.
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

long _PyHASH_INF;
long _PyHASH_MODULUS;
long _PyHASH_IMAG;
_Py_HashSecret_t _Py_HashSecret = {{0}};

void initialize_hashes() {
    _PyHASH_INF = GraalPyTruffle_HashConstant(0);
    _PyHASH_MODULUS = GraalPyTruffle_HashConstant(1);
    _PyHASH_IMAG = GraalPyTruffle_HashConstant(2);
    GraalPyTruffleHash_InitSecret((int8_t *)&_Py_HashSecret);
}

/* taken from CPython */
Py_hash_t
_Py_HashPointerRaw(const void *p)
{
    size_t y = (size_t)p;
    /* bottom 3 or 4 bits are likely to be 0; rotate y by 4 to avoid
       excessive hash collisions for dicts and sets */
    y = (y >> 4) | (y << (8 * SIZEOF_VOID_P - 4));
    return (Py_hash_t)y;
}

/* taken from CPython */
Py_hash_t
_Py_HashPointer(const void *p)
{
    Py_hash_t x = _Py_HashPointerRaw(p);
    if (x == -1) {
        x = -2;
    }
    return x;
}

/* taken from CPython */
Py_hash_t
_Py_HashDouble(PyObject *inst, double v)
{
    int e, sign;
    double m;
    Py_uhash_t x, y;

    if (!Py_IS_FINITE(v)) {
        if (Py_IS_INFINITY(v))
            return v > 0 ? _PyHASH_INF : -_PyHASH_INF;
        else
            return _Py_HashPointer(inst);
    }
    // GraalPy change: different implementation
    return Graal_PyTruffle_HashDouble(v);
}
