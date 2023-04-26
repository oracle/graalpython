/*
 * Copyright (c) 2021, 2023, Oracle and/or its affiliates. All rights reserved.
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

#define Py_BUILD_CORE

#include <Python.h>

#include "capi.h"

#include <frameobject.h>
#include <pycore_pymem.h>
#include <pycore_moduleobject.h>

#include <stdio.h>
#include <stdint.h>
#include <time.h>

#define MUST_INLINE __attribute__((always_inline)) inline
/*
#define TYPE_OBJECTS \
TYPE_OBJECT(PyTypeObject*, PyCapsule_Type, capsule, _object) \

#define GLOBAL_VARS \
GLOBAL_VAR(struct _longobject*, _Py_FalseStructReference, Py_False) \
GLOBAL_VAR(struct _longobject*, _Py_TrueStructReference, Py_True) \
GLOBAL_VAR(PyObject*, _Py_EllipsisObjectReference, Py_Ellipsis) \
GLOBAL_VAR(PyObject*, _Py_NoneStructReference, Py_None) \
GLOBAL_VAR(PyObject*, _Py_NotImplementedStructReference, Py_NotImplemented) \
GLOBAL_VAR(PyObject*, _PyTruffle_Zero, _PyTruffle_Zero) \
GLOBAL_VAR(PyObject*, _PyTruffle_One, _PyTruffle_One) \
GLOBAL_VAR(PyObject*, _PyLong_Zero, PyLong_Zero) \
GLOBAL_VAR(PyObject*, _PyLong_One, PyLong_One) \

#define GLOBAL_VAR_COPIES \
GLOBAL_VAR(struct _PyTraceMalloc_Config, _Py_tracemalloc_config) \
GLOBAL_VAR(_Py_HashSecret_t, _Py_HashSecret) \
GLOBAL_VAR(int, Py_DebugFlag) \
GLOBAL_VAR(int, Py_VerboseFlag) \
GLOBAL_VAR(int, Py_QuietFlag) \
GLOBAL_VAR(int, Py_InteractiveFlag) \
GLOBAL_VAR(int, Py_InspectFlag) \
GLOBAL_VAR(int, Py_OptimizeFlag) \
GLOBAL_VAR(int, Py_NoSiteFlag) \
GLOBAL_VAR(int, Py_BytesWarningFlag) \
GLOBAL_VAR(int, Py_FrozenFlag) \
GLOBAL_VAR(int, Py_IgnoreEnvironmentFlag) \
GLOBAL_VAR(int, Py_DontWriteBytecodeFlag) \
GLOBAL_VAR(int, Py_NoUserSiteDirectory) \
GLOBAL_VAR(int, Py_UnbufferedStdioFlag) \
GLOBAL_VAR(int, Py_HashRandomizationFlag) \
GLOBAL_VAR(int, Py_IsolatedFlag) \



#define DEFINE_TYPE_OBJECT(NAME, TYPENAME) PyTypeObject NAME;
PY_TYPE_OBJECTS(DEFINE_TYPE_OBJECT)
#undef DEFINE_TYPE_OBJECT

#define TYPE_OBJECT(CTYPE, NAME, TYPENAME, STRUCT_TYPE) CTYPE NAME##Reference;
TYPE_OBJECTS
#undef TYPE_OBJECT

#define GLOBAL_VAR(TYPE, NAME, INTERNAL_NAME) TYPE NAME;
GLOBAL_VARS
#undef GLOBAL_VAR

#define GLOBAL_VAR(TYPE, NAME) TYPE NAME;
GLOBAL_VAR_COPIES
#undef GLOBAL_VAR

#define EXCEPTION(NAME) PyObject* PyExc_##NAME;
PY_EXCEPTIONS
#undef EXCEPTION


#define BUILTIN(NAME, RET, ...) RET (*Graal##NAME)(__VA_ARGS__);
CAPI_BUILTINS
#undef BUILTIN
*/

uint32_t Py_Truffle_Options;

void initializeCAPIForwards(void* (*getAPI)(const char*));

int initNativeForwardCalled = 0;

/**
 * Returns 1 on success, 0 on error (if it was already initialized).
 */
PyAPI_FUNC(int) initNativeForward(void* (*getBuiltin)(int), void* (*getAPI)(const char*), void* (*getType)(const char*), void (*setTypeStore)(const char*, void*), void (*initialize_native_locations)(void*,void*,void*)) {
    if (initNativeForwardCalled) {
    	return 0;
    }
    initNativeForwardCalled = 1;
    clock_t t;
    t = clock();
/*
#define SET_TYPE_OBJECT_STORE(NAME, TYPENAME) setTypeStore(#TYPENAME, (void*) &NAME);
    PY_TYPE_OBJECTS(SET_TYPE_OBJECT_STORE)
#undef SET_TYPE_OBJECT_STORE

#define TYPE_OBJECT(CTYPE, NAME, TYPENAME, STRUCT_TYPE) NAME##Reference = (CTYPE) getType(#TYPENAME);
    TYPE_OBJECTS
#undef TYPE_OBJECT

#define GLOBAL_VAR(TYPE, NAME, INTERNAL_NAME) NAME = (TYPE) getType(#INTERNAL_NAME);
    GLOBAL_VARS
#undef GLOBAL_VAR

#define GLOBAL_VAR(TYPE, NAME) memcpy((void*) &NAME, getType(#NAME), sizeof(NAME));
    GLOBAL_VAR_COPIES
#undef GLOBAL_VAR

#define EXCEPTION(NAME) PyExc_##NAME = (PyObject*) getType(#NAME);
	PY_EXCEPTIONS
#undef EXCEPTION

    // now force all classes toNative:
#define SET_TYPE_OBJECT_STORE(NAME, TYPENAME) \
    if (strcmp(#TYPENAME, "unimplemented") != 0) { \
        getType(#TYPENAME); \
    }
    PY_TYPE_OBJECTS(SET_TYPE_OBJECT_STORE)
#undef SET_TYPE_OBJECT_STORE

    int id = 0;
#define BUILTIN(NAME, RET, ...) Graal##NAME = (RET(*)(__VA_ARGS__)) getBuiltin(id++);
CAPI_BUILTINS
#undef BUILTIN
*/
    Py_Truffle_Options = GraalPyTruffle_Native_Options();
    initializeCAPIForwards(getAPI);

    // send the locations of these values to Sulong - the values need to be shared
    initialize_native_locations(&PyTruffle_AllocatedMemory, &PyTruffle_MaxNativeMemory, &PyTruffle_NativeMemoryGCBarrier);

//    if (PyTruffle_Log_Fine()) {
//    	// provide some timing info for native/Java boundary
//
//        clock_t start;
//        for (int run = 0; run < 1000; run++) {
//			start = clock();
//			int COUNT = 10000;
//			for (int i = 0; i < COUNT; i++) {
//				GraalPyTruffleLong_Zero();
//			}
//			double delta = ((double) (clock() - start)) / CLOCKS_PER_SEC;
//			if ((run % 100) == 0) {
//				PyTruffle_Log(PY_TRUFFLE_LOG_FINE, "C API Timing probe: %.0fns", delta * 1000000000 / COUNT);
//			}
//        }
//        PyTruffle_Log(PY_TRUFFLE_LOG_FINE, "initNativeForward: %fs", ((double) (clock() - t)) / CLOCKS_PER_SEC);
//    }

    return 1;
}

/*
 * This header includes definitions for constant arrays like:
 * _Py_ascii_whitespace, _Py_ctype_table, _Py_ctype_tolower, _Py_ctype_toupper.
 */
#include "const_arrays.h"

/* Private types are defined here because we need to declare the type cast. */

typedef struct mmap_object mmap_object;

#include "capi_forwards.h"

#define IS_HANDLE(x) ((((intptr_t) (x)) & 0x8000000000000000L) != 0)

inline void assertHandleOrPointer(PyObject* o) {
#ifndef NDEBUG
    if (IS_HANDLE(o)) {
        if ((((intptr_t) o) & 0x7FFFFFFF00000000L) != 0) {
            printf("suspiciously large handle: %lx\n", (unsigned long) o);
        }
    } else {
        if ((((intptr_t) o) & 0x7FFFFFFFFF000000L) == 0) {
            printf("suspiciously small address: %lx\n", (unsigned long) o);
        }
    }
#endif
}

/*
This is a workaround for C++ modules, namely PyTorch, that declare global/static variables with destructors that call
_Py_DECREF. The destructors get called by libc during exit during which we cannot make upcalls as that would segfault.
So we rebind them to no-ops when exiting.
*/
Py_ssize_t nop_GraalPy_get_PyObject_ob_refcnt(PyObject* obj) {
	return 100; // large dummy refcount
}
void nop_GraalPy_set_PyObject_ob_refcnt(PyObject* obj, Py_ssize_t refcnt) {
	// do nothing
}
PyAPI_FUNC(void) finalizeCAPI() {
	GraalPy_get_PyObject_ob_refcnt = nop_GraalPy_get_PyObject_ob_refcnt;
	GraalPy_set_PyObject_ob_refcnt = nop_GraalPy_set_PyObject_ob_refcnt;
}

PyObject* PyTuple_Pack(Py_ssize_t n, ...) {
    va_list vargs;
    va_start(vargs, n);
    PyObject *result = PyTuple_New(n);
    if (result == NULL) {
        goto end;
    }
    for (int i = 0; i < n; i++) {
        PyObject *o = va_arg(vargs, PyObject *);
        Py_XINCREF(o);
        PyTuple_SetItem(result, i, o);
    }
 end:
    va_end(vargs);
    return result;
}

int (*PyOS_InputHook)(void) = NULL;

int _PyArg_ParseStack_SizeT(PyObject **args, Py_ssize_t nargs, const char* format, ...) {
    printf("_PyArg_ParseStack_SizeT not implemented in capi_native - exiting\n");
    exit(-1);
}

PyAPI_FUNC(int) PyArg_Parse(PyObject* a, const char* b, ...) {
    va_list args;
    va_start(args, b);
    int result = (int) PyArg_VaParse(PyTuple_Pack(1, a), b, args);
    va_end(args);
    return result;
}

PyAPI_FUNC(int) _PyArg_Parse_SizeT(PyObject* a, const char* b, ...) {
    va_list args;
    va_start(args, b);
    int result = (int) PyArg_VaParse(PyTuple_Pack(1, a), b, args);
    va_end(args);
    return result;
}

int PyType_IsSubtype(PyTypeObject *a, PyTypeObject *b) {

	// stay in native code if possible

	PyTypeObject* t = a;
    do {
        if (t == b)
            return 1;
        t = t->tp_base;
    } while (t != NULL);

	return PyType_IsSubtype_Inlined(a, b);
}


/*
 * This dummy implementation is needed until we can properly transition the PyThreadState data structure to native.
 */

PyThreadState mainThreadState; // dummy
PyThreadState * PyThreadState_Get() {
    mainThreadState.interp = (PyInterpreterState*) 0;
    return &mainThreadState;
}

/*
 * The following source files contain code that can be compiled directly and does not need to be called via stubs in Sulong:
 */

#include "_warnings.c"
#include "abstract.c"
#include "boolobject.c"
#include "bytearrayobject.c"
#include "bytesobject.c"
#include "ceval.c"
#include "classobject.c"
#include "codecs.c"
#include "compile.c"
#include "complexobject.c"
#include "context.c"
#include "descrobject.c"
#include "dictobject.c"
#include "errors.c"
#include "fileobject.c"
#include "floatobject.c"
#include "frameobject.c"
#include "genobject.c"
#include "getbuildinfo.c"
#include "getcompiler.c"
#include "getversion.c"
#include "import.c"
#include "longobject.c"
#include "memoryobject.c"
#include "methodobject.c"
#include "modsupport_shared.c"
#include "moduleobject.c"
#include "mysnprintf.c"
#include "mystrtoul.c"
#include "object.c"
#include "obmalloc.c"
#include "pyhash.c"
#include "pylifecycle.c"
#include "pystate.c"
#include "pystrcmp.c"
#include "pystrhex.c"
#include "pystrtod.c"
#include "pytime.c"
#include "setobject.c"
#include "signals.c"
#include "sliceobject.c"
#include "sysmodule.c"
#include "structseq.c"
#include "thread.c"
#include "traceback.c"
#include "unicodectype.c"
#include "weakrefobject.c"


/*
 * This mirrors the definition in capi.c that we us on Sulong, and needs to be
 * fixed when that is.
 */
const char *Py_FileSystemDefaultEncoding = "utf-8";
