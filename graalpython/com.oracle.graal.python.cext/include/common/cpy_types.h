/* MIT License
 *
 * Copyright (c) 2020, Oracle and/or its affiliates.
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

#ifndef HPY_UNIVERSAL_CPY_TYPES_H
#define HPY_UNIVERSAL_CPY_TYPES_H

#include "Python.h"

/* generally speaking, we can't #include Python.h, but there are a bunch of
 * types defined there that we need to use.  Here, we redefine all the types
 * we need, with a cpy_ prefix.
 */

typedef struct _object cpy_PyObject;
typedef cpy_PyObject *(*cpy_PyCFunction)(cpy_PyObject *, cpy_PyObject *);
typedef struct PyMethodDef cpy_PyMethodDef;
typedef PyType_Slot cpy_PyTypeSlot;
typedef PyGetSetDef cpy_PyGetSetDef;
typedef PyMemberDef cpy_PyMemberDef;


#endif /* HPY_UNIVERSAL_CPY_TYPES_H */
