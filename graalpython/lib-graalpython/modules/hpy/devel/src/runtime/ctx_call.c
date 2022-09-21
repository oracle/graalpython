/* MIT License
 *
 * Copyright (c) 2021, Oracle and/or its affiliates.
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

#ifdef HPY_UNIVERSAL_ABI
   // for _h2py and _py2h
#  include "handles.h"
#endif

_HPy_HIDDEN HPy
ctx_CallTupleDict(HPyContext *ctx, HPy callable, HPy args, HPy kw)
{
    PyObject *obj;
    if (!HPy_IsNull(args) && !HPyTuple_Check(ctx, args)) {
       HPyErr_SetString(ctx, ctx->h_TypeError,
           "HPy_CallTupleDict requires args to be a tuple or null handle");
       return HPy_NULL;
    }
    if (!HPy_IsNull(kw) && !HPyDict_Check(ctx, kw)) {
       HPyErr_SetString(ctx, ctx->h_TypeError,
           "HPy_CallTupleDict requires kw to be a dict or null handle");
       return HPy_NULL;
    }
    if (HPy_IsNull(kw)) {
        obj = PyObject_CallObject(_h2py(callable), _h2py(args));
    }
    else if (!HPy_IsNull(args)){
        obj = PyObject_Call(_h2py(callable), _h2py(args), _h2py(kw));
    }
    else {
        // args is null, but kw is not, so we need to create an empty args tuple
        // for CPython's PyObject_Call
        HPy *items = NULL;
        HPy empty_tuple = HPyTuple_FromArray(ctx, items, 0);
        obj = PyObject_Call(_h2py(callable), _h2py(empty_tuple), _h2py(kw));
        HPy_Close(ctx, empty_tuple);
    }
    return _py2h(obj);
}
