/* MIT License
 *
 * Copyright (c) 2020, 2022, Oracle and/or its affiliates.
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

#ifndef HPY_ABI_CPYTHON
   // for _h2py and _py2h
#  include "handles.h"
#endif


_HPy_HIDDEN HPyListBuilder
ctx_ListBuilder_New(HPyContext *ctx, HPy_ssize_t size)
{
    PyObject *lst = PyList_New(size);
    if (lst == NULL)
        PyErr_Clear();   /* delay the MemoryError */
#ifdef GRAALVM_PYTHON_LLVM
    return (HPyListBuilder){(void*)lst};
#else
    return (HPyListBuilder){(HPy_ssize_t)lst};
#endif
}

_HPy_HIDDEN void
ctx_ListBuilder_Set(HPyContext *ctx, HPyListBuilder builder,
                    HPy_ssize_t index, HPy h_item)
{
    PyObject *lst = (PyObject *)builder._lst;
    if (lst != NULL) {
        PyObject *item = _h2py(h_item);
        assert(index >= 0 && index < PyList_GET_SIZE(lst));
        assert(PyList_GET_ITEM(lst, index) == NULL);
        Py_INCREF(item);
        PyList_SET_ITEM(lst, index, item);
    }
}

_HPy_HIDDEN HPy
ctx_ListBuilder_Build(HPyContext *ctx, HPyListBuilder builder)
{
    PyObject *lst = (PyObject *)builder._lst;
    if (lst == NULL) {
        PyErr_NoMemory();
        return HPy_NULL;
    }
    builder._lst = 0;
    return _py2h(lst);
}

_HPy_HIDDEN void
ctx_ListBuilder_Cancel(HPyContext *ctx, HPyListBuilder builder)
{
    PyObject *lst = (PyObject *)builder._lst;
    if (lst == NULL) {
        // we don't report the memory error here: the builder
        // is being cancelled (so the result of the builder is not being used)
        // and likely it's being cancelled during the handling of another error
        return;
    }
    builder._lst = 0;
    Py_XDECREF(lst);
}
