/* MIT License
 *
 * Copyright (c) 2021, 2022, Oracle and/or its affiliates.
 * Copyright (c) 2019 pyhandle
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

#ifndef HPY_COMMON_RUNTIME_CTX_TYPE_H
#define HPY_COMMON_RUNTIME_CTX_TYPE_H

#include <Python.h>
#include "hpy.h"
#include "hpy/hpytype.h"

_HPy_HIDDEN PyMethodDef *create_method_defs(HPyDef *hpydefs[],
                                            PyMethodDef *legacy_methods);

_HPy_HIDDEN int call_traverseproc_from_trampoline(HPyFunc_traverseproc tp_traverse,
                                                  PyObject *self,
                                                  cpy_visitproc cpy_visit,
                                                  void *cpy_arg);

/* The C structs of pure HPy (i.e. non-legacy) custom types do NOT include
 * PyObject_HEAD. So, the CPython implementation of HPy_New must allocate a
 * memory region which is big enough to contain PyObject_HEAD + any eventual
 * extra padding + the actual user struct. We use union alignment to ensure
 * that the payload is correctly aligned for every possible struct.
 *
 * Legacy custom types already include PyObject_HEAD and so do not need to
 * allocate extra memory region or use _HPy_PyObject_HEAD_SIZE.
 */
typedef struct {
    PyObject_HEAD
    union {
        unsigned char payload[1];
        // these fields are never accessed: they are present just to ensure
        // the correct alignment of payload
        unsigned short _m_short;
        unsigned int _m_int;
        unsigned long _m_long;
        unsigned long long _m_longlong;
        float _m_float;
        double _m_double;
        long double _m_longdouble;
        void *_m_pointer;
    };
} _HPy_FullyAlignedSpaceForPyObject_HEAD;

#define _HPy_PyObject_HEAD_SIZE (offsetof(_HPy_FullyAlignedSpaceForPyObject_HEAD, payload))

// Return a pointer to the area of memory AFTER the PyObject_HEAD
static inline void *_HPy_PyObject_Payload(PyObject *obj)
{
    return (void *) ((char *) obj + _HPy_PyObject_HEAD_SIZE);
}


#endif /* HPY_COMMON_RUNTIME_CTX_TYPE_H */
