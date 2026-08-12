/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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

#if PY_VERSION_HEX >= 0x030F0000
_Static_assert(sizeof(((GraalPyArray_Descriptor *)0)->typecode) == 3,
               "GraalPyArray_Descriptor.typecode must support multi-character Python 3.15 array typecodes");
#endif

#define GRAALPY_ARRAY_DESCRIPTOR(TYPECODE, TYPE) \
    {TYPECODE, sizeof(TYPE)}

static GraalPyArray_Descriptor array_descriptor_b = GRAALPY_ARRAY_DESCRIPTOR('b', signed char);
static GraalPyArray_Descriptor array_descriptor_B = GRAALPY_ARRAY_DESCRIPTOR('B', unsigned char);
static GraalPyArray_Descriptor array_descriptor_u = GRAALPY_ARRAY_DESCRIPTOR('u', Py_UCS4);
static GraalPyArray_Descriptor array_descriptor_w = GRAALPY_ARRAY_DESCRIPTOR('w', Py_UCS4);
static GraalPyArray_Descriptor array_descriptor_h = GRAALPY_ARRAY_DESCRIPTOR('h', short);
static GraalPyArray_Descriptor array_descriptor_H = GRAALPY_ARRAY_DESCRIPTOR('H', unsigned short);
static GraalPyArray_Descriptor array_descriptor_i = GRAALPY_ARRAY_DESCRIPTOR('i', int);
static GraalPyArray_Descriptor array_descriptor_I = GRAALPY_ARRAY_DESCRIPTOR('I', unsigned int);
static GraalPyArray_Descriptor array_descriptor_l = GRAALPY_ARRAY_DESCRIPTOR('l', long);
static GraalPyArray_Descriptor array_descriptor_L = GRAALPY_ARRAY_DESCRIPTOR('L', unsigned long);
static GraalPyArray_Descriptor array_descriptor_q = GRAALPY_ARRAY_DESCRIPTOR('q', long long);
static GraalPyArray_Descriptor array_descriptor_Q = GRAALPY_ARRAY_DESCRIPTOR('Q', unsigned long long);
static GraalPyArray_Descriptor array_descriptor_f = GRAALPY_ARRAY_DESCRIPTOR('f', float);
static GraalPyArray_Descriptor array_descriptor_d = GRAALPY_ARRAY_DESCRIPTOR('d', double);

#undef GRAALPY_ARRAY_DESCRIPTOR

GraalPyArray_Descriptor *
GraalPyArray_GetDescriptor(PyObject *array)
{
    switch (GraalPyPrivate_Array_TypeCode(array)) {
        case 'b': return &array_descriptor_b;
        case 'B': return &array_descriptor_B;
        case 'u': return &array_descriptor_u;
        case 'w': return &array_descriptor_w;
        case 'h': return &array_descriptor_h;
        case 'H': return &array_descriptor_H;
        case 'i': return &array_descriptor_i;
        case 'I': return &array_descriptor_I;
        case 'l': return &array_descriptor_l;
        case 'L': return &array_descriptor_L;
        case 'q': return &array_descriptor_q;
        case 'Q': return &array_descriptor_Q;
        case 'f': return &array_descriptor_f;
        case 'd': return &array_descriptor_d;
        default:
            PyErr_BadInternalCall();
            return NULL;
    }
}

PyObject *
GraalPyArray_New(PyObject *type, Py_ssize_t size, GraalPyArray_Descriptor *descriptor)
{
    if (type == NULL || descriptor == NULL || size < 0) {
        PyErr_BadInternalCall();
        return NULL;
    }
    return GraalPyPrivate_Array_New(type, size, (unsigned char)descriptor->typecode);
}
