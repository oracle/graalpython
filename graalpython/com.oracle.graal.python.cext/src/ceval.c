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

#include "pycore_ceval.h"         // _PyEval_SignalAsyncExc()

PyObject* PyEval_CallObjectWithKeywords(PyObject *func, PyObject *args, PyObject *kwargs) {
    return PyObject_Call(func, args, kwargs);
}

void PyEval_InitThreads() {
    // Nothing to do
}

int PyEval_ThreadsInitialized() {
    return 1;
}

int PyEval_MergeCompilerFlags(PyCompilerFlags *cf) {
    return 0;
}


void PyThread_free_lock(PyThread_type_lock lock) {
}

PyObject *
PyEval_EvalCode(PyObject *co, PyObject *globals, PyObject *locals)
{
    return PyEval_EvalCodeEx(co,
                      globals, locals,
                      (PyObject **)NULL, 0,
                      (PyObject **)NULL, 0,
                      (PyObject **)NULL, 0,
                      NULL, NULL);
}

PyObject *
PyEval_EvalCodeEx(PyObject *_co, PyObject *globals, PyObject *locals,
                  PyObject *const *args, int argcount,
                  PyObject *const *kws, int kwcount,
                  PyObject *const *defs, int defcount,
                  PyObject *kwdefs, PyObject *closure)
{
    if (globals == NULL) {
        PyErr_SetString(PyExc_SystemError, "PyEval_EvalCodeEx: NULL globals");
        return NULL;
    }
    return Graal_PyTruffleEval_EvalCodeEx(_co, globals, locals != NULL ? locals : Py_None,
                                  args, argcount,
                                  kws, kwcount,
                                  defs, defcount,
                                  kwdefs, closure);
}

/* The function _Py_EnterRecursiveCallTstate() only calls _Py_CheckRecursiveCall()
   if the recursion_depth reaches recursion_limit. */
int
_Py_CheckRecursiveCall(PyThreadState *tstate, const char *where)
{
    /* Check against global limit first. */
    int depth = tstate->recursion_limit - tstate->recursion_remaining;
    /* GraalVM change:
    if (depth < tstate->interp->ceval.recursion_limit) {
        tstate->recursion_limit = tstate->interp->ceval.recursion_limit;
    */
    if (depth < Py_DEFAULT_RECURSION_LIMIT) {
        tstate->recursion_limit = Py_DEFAULT_RECURSION_LIMIT;
        tstate->recursion_remaining = tstate->recursion_limit - depth;
        assert(tstate->recursion_remaining > 0);
        return 0;
    }
#ifdef USE_STACKCHECK
    if (PyOS_CheckStack()) {
        ++tstate->recursion_remaining;
        _PyErr_SetString(tstate, PyExc_MemoryError, "Stack overflow");
        return -1;
    }
#endif
    if (tstate->recursion_headroom) {
        if (tstate->recursion_remaining < -50) {
            /* Overflowing while handling an overflow. Give up. */
            Py_FatalError("Cannot recover from stack overflow.");
        }
    }
    else {
        if (tstate->recursion_remaining <= 0) {
            tstate->recursion_headroom++;
            /* GraalVM change:
            _PyErr_Format(tstate, PyExc_RecursionError,
            */
            PyErr_Format(PyExc_RecursionError,
                        "maximum recursion depth exceeded%s",
                        where);
            tstate->recursion_headroom--;
            ++tstate->recursion_remaining;
            return -1;
        }
    }
    return 0;
}


/* Implement Py_EnterRecursiveCall() and Py_LeaveRecursiveCall() as functions
   for the limited API. */

#undef Py_EnterRecursiveCall

int Py_EnterRecursiveCall(const char *where)
{
    return _Py_EnterRecursiveCall(where);
}

#undef Py_LeaveRecursiveCall

void Py_LeaveRecursiveCall(void)
{
    _Py_LeaveRecursiveCall();
}
