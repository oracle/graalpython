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

#include <stddef.h>
#include <Python.h>
#include "hpy.h"

#ifdef HPY_UNIVERSAL_ABI
   // for _h2py and _py2h
#  include "handles.h"
#endif


_HPy_HIDDEN HPyTupleBuilder
ctx_TupleBuilder_New(HPyContext ctx, HPy_ssize_t initial_size)
{
    PyObject *tup = PyTuple_New(initial_size);
    if (tup == NULL) {
        PyErr_Clear();   /* delay the MemoryError */
        /* note: it's done this way so that the caller doesn't need to
           check if HPyTupleBuilder_New() or every HPyTupleBuilder_Set()
           raised.  If there is a rare error like a MemoryError somewhere,
           further calls to the HPyTupleBuilder are ignored.  The final
           HPyTupleBuilder_Build() will re-raise the MemoryError and so
           it's enough for the caller to check at that point. */
    }
    return (HPyTupleBuilder){(HPy_ssize_t)tup};
}

_HPy_HIDDEN void
ctx_TupleBuilder_Set(HPyContext ctx, HPyTupleBuilder builder,
                     HPy_ssize_t index, HPy h_item)
{
    PyObject *tup = (PyObject *)builder._tup;
    if (tup != NULL) {
        PyObject *item = _h2py(h_item);
        assert(index >= 0 && index < PyTuple_GET_SIZE(tup));
        assert(PyTuple_GET_ITEM(tup, index) == NULL);
        Py_INCREF(item);
        PyTuple_SET_ITEM(tup, index, item);
    }
}

_HPy_HIDDEN HPy
ctx_TupleBuilder_Build(HPyContext ctx, HPyTupleBuilder builder)
{
    PyObject *tup = (PyObject *)builder._tup;
    if (tup == NULL) {
        PyErr_NoMemory();
        return HPy_NULL;
    }
    builder._tup = 0;
    return _py2h(tup);
}

_HPy_HIDDEN void
ctx_TupleBuilder_Cancel(HPyContext ctx, HPyTupleBuilder builder)
{
    PyObject *tup = (PyObject *)builder._tup;
    builder._tup = 0;
    Py_XDECREF(tup);
}

