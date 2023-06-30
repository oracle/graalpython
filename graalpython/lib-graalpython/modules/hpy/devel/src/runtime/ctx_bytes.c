/* MIT License
 *  
 * Copyright (c) 2020, 2021, Oracle and/or its affiliates. 
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

#include <Python.h>
#include "hpy.h"
#include "hpy/runtime/ctx_funcs.h"

#ifndef HPY_ABI_CPYTHON
   // for _h2py and _py2h
#  include "handles.h"
#endif


_HPy_HIDDEN HPy
ctx_Bytes_FromStringAndSize(HPyContext *ctx, const char *v, HPy_ssize_t len)
{
    if (v == NULL) {
        // The CPython API allows passing a null pointer to
        // PyBytes_FromStringAndSize and returns uninitialized memory of the
        // requested size which can then be initialized after the call.
        // In HPy the underlying memory is opaque and so cannot be initialized
        // after the call and so we raise an error instead.
        HPyErr_SetString(ctx, ctx->h_ValueError,
                         "NULL char * passed to HPyBytes_FromStringAndSize");
        return HPy_NULL;
    }
    return _py2h(PyBytes_FromStringAndSize(v, len));
}
