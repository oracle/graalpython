/*
 * Copyright (c) 2019, 2024, Oracle and/or its affiliates. All rights reserved.
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
#include "Python.h"

#include "pycore_call.h"          // _PyObject_CallMethod()
#include "pycore_ceval.h"         // _PyEval_FiniGIL()
#include "pycore_fileutils.h"     // _Py_ResetForceASCII()
#include "pycore_floatobject.h"   // _PyFloat_InitTypes()
#include "pycore_long.h"          // _PyLong_InitTypes()
#include "pycore_object.h"        // _PyDebug_PrintTotalRefs()
#include "pycore_pyerrors.h"      // _PyErr_Occurred()
#include "pycore_pymem.h"         // _PyObject_DebugMallocStats()
#include "pycore_pystate.h"       // _PyThreadState_GET()
#include "pycore_runtime.h"       // _Py_ID()
#include "pycore_runtime_init.h"  // _PyRuntimeState_INIT
#include "pycore_typeobject.h"    // _PyTypes_InitTypes()
#include "pycore_unicodeobject.h" // _PyUnicode_InitTypes()


#include <locale.h>               // setlocale()
#include <stdlib.h>               // getenv()
#ifdef HAVE_UNISTD_H
#  include <unistd.h>             // isatty()
#endif

#include "capi.h"

int Py_IsInitialized(void) {
    return !graalpy_finalizing;
}

int
_Py_IsFinalizing(void)
{
    return graalpy_finalizing;
}

void _Py_NO_RETURN  _Py_FatalErrorFunc(const char *func, const char *msg) {
	GraalPyTruffle_FatalErrorFunc(func, msg, -1);
	/* If the above upcall returns, then we just fall through to the 'abort' call. */
	abort();
}

_PyRuntimeState _PyRuntime
#if defined(__linux__) && (defined(__GNUC__) || defined(__clang__))
__attribute__ ((section (".PyRuntime")))
#endif
= _PyRuntimeState_INIT(_PyRuntime);
_Py_COMP_DIAG_POP

static int runtime_initialized = 0;
