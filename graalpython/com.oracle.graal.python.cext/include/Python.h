/*
 * Copyright (c) 2017, 2019, Oracle and/or its affiliates. All rights reserved.
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
#ifndef Py_PYTHON_H
#define Py_PYTHON_H

#define HAVE_UTIME_H
#define HAVE_UNISTD_H
#define HAVE_SIGNAL_H
#define HAVE_FCNTL_H
#define HAVE_SYS_WAIT_H

#define PYPY_VERSION 0
#define PYPY_VERSION_NUM 0

#include <truffle.h>
#include <stdio.h>
#include <string.h>
#include <errno.h>
#include <stdlib.h>
#include <sys/stat.h>
#include <sys/types.h>
#include <sys/dir.h>
#include <dirent.h>
#include <locale.h>
#include <langinfo.h>
#include <assert.h>
#include <unistd.h>
#include <math.h>

#include "pyport.h"
#include "pymacro.h"
#include "object.h"
#include "abstract.h"
#include "methodobject.h"
#include "moduleobject.h"
#include "unicodeobject.h"
#include "pystate.h"
#include "pyarena.h"
#include "compile.h"
#include "pythonrun.h"
#include "ceval.h"
#include "pyerrors.h"
#include "modsupport.h"
#include "tupleobject.h"
#include "structseq.h"
#include "structmember.h"
#include "pytime.h"
#include "pymem.h"
#include "objimpl.h"
#include "bytesobject.h"
#include "longobject.h"
#include "longintrepr.h"
#include "boolobject.h"
#include "floatobject.h"
#include "dictobject.h"
#include "setobject.h"
#include "complexobject.h"
#include "listobject.h"
#include "sliceobject.h"
#include "descrobject.h"
#include "fileobject.h"
#include "pyctype.h"
#include "bytearrayobject.h"
#include "warnings.h"
#include "patchlevel.h"
#include "pymath.h"
#include "pyhash.h"
#include "import.h"
#include "pycapsule.h"
#include "pylifecycle.h"
#include "pydebug.h"
#include "code.h"
#include "pyfpe.h"
#include "memoryobject.h"
#include "pystrhex.h"
#include "codecs.h"
#include "frameobject.h"
#include "traceback.h"
#include "classobject.h"
#include "pythread.h"
#include "funcobject.h"
#include "iterobject.h"
#include "datetime.h"
#include "typeslots.h"
#include "weakrefobject.h"
#include "sysmodule.h"
#include "fileutils.h"

// TODO: we must extend the refcounting behavior to support handles to managed objects
#undef Py_DECREF
#define Py_DECREF(o) 0
#undef Py_INCREF
#define Py_INCREF(o) 0

// TODO: (tfel) Is this necessary?
#ifndef Py_BuildValue
#define Py_BuildValue _Py_BuildValue_SizeT
#endif

/* 
 * #define Py_INCREF(op) (                         \
 *     _Py_INC_REFTOTAL  _Py_REF_DEBUG_COMMA       \
 *     ((PyObject *)(op))->ob_refcnt++)
 * 
 * #define Py_DECREF(op)                                                   \
 *     do {                                                                \
 *         void* handle = op;                                              \
 *         PyObject *_py_decref_tmp = (PyObject *)((truffle_is_handle_to_managed(handle) ? truffle_managed_from_handle(handle) : handle)); \
 *         if (_Py_DEC_REFTOTAL  _Py_REF_DEBUG_COMMA                       \
 *             --(_py_decref_tmp)->ob_refcnt != 0) {                       \
 *             _Py_CHECK_REFCNT(_py_decref_tmp)                            \
 *             else                                                        \
 *                 _Py_Dealloc(_py_decref_tmp);                            \
 *     } while (0)
 */


#undef Py_RETURN_NONE
#define Py_RETURN_NONE return Py_None;

#define _PyLong_FromTime_t(o) ((long)o)

extern int PyTruffle_Arg_ParseTupleAndKeywords(PyObject *argv, PyObject *kwds, const char *format, char** kwdnames, int outc, void *v0, void *v1, void *v2, void *v3, void *v4, void *v5, void *v6, void *v7, void *v8, void *v9, void *v10, void *v11, void *v12, void *v13, void *v14, void *v15, void *v16, void *v17, void *v18, void *v19);

#define PyTruffle_Arg_ParseTupleAndKeywords_0(ARGV, KWDS, FORMAT, KWDNAMES) PyTruffle_Arg_ParseTupleAndKeywords(ARGV, KWDS, FORMAT, KWDNAMES, 0, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL)
#define PyTruffle_Arg_ParseTupleAndKeywords_1(ARGV, KWDS, FORMAT, KWDNAMES, V1) PyTruffle_Arg_ParseTupleAndKeywords(ARGV, KWDS, FORMAT, KWDNAMES, 1, (void*)(V1), NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL)
#define PyTruffle_Arg_ParseTupleAndKeywords_2(ARGV, KWDS, FORMAT, KWDNAMES, V1, V2) PyTruffle_Arg_ParseTupleAndKeywords(ARGV, KWDS, FORMAT, KWDNAMES, 2, (void*)(V1), (void*)(V2), NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL)
#define PyTruffle_Arg_ParseTupleAndKeywords_3(ARGV, KWDS, FORMAT, KWDNAMES, V1, V2, V3) PyTruffle_Arg_ParseTupleAndKeywords(ARGV, KWDS, FORMAT, KWDNAMES, 3, (void*)(V1), (void*)(V2), (void*)(V3), NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL)
#define PyTruffle_Arg_ParseTupleAndKeywords_4(ARGV, KWDS, FORMAT, KWDNAMES, V1, V2, V3, V4) PyTruffle_Arg_ParseTupleAndKeywords(ARGV, KWDS, FORMAT, KWDNAMES, 4, (void*)(V1), (void*)(V2), (void*)(V3), (void*)(V4), NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL)
#define PyTruffle_Arg_ParseTupleAndKeywords_5(ARGV, KWDS, FORMAT, KWDNAMES, V1, V2, V3, V4, V5) PyTruffle_Arg_ParseTupleAndKeywords(ARGV, KWDS, FORMAT, KWDNAMES, 5, (void*)(V1), (void*)(V2), (void*)(V3), (void*)(V4), (void*)(V5), NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL)
#define PyTruffle_Arg_ParseTupleAndKeywords_6(ARGV, KWDS, FORMAT, KWDNAMES, V1, V2, V3, V4, V5, V6) PyTruffle_Arg_ParseTupleAndKeywords(ARGV, KWDS, FORMAT, KWDNAMES, 6, (void*)(V1), (void*)(V2), (void*)(V3), (void*)(V4), (void*)(V5), (void*)(V6), NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL)
#define PyTruffle_Arg_ParseTupleAndKeywords_7(ARGV, KWDS, FORMAT, KWDNAMES, V1, V2, V3, V4, V5, V6, V7) PyTruffle_Arg_ParseTupleAndKeywords(ARGV, KWDS, FORMAT, KWDNAMES, 7, (void*)(V1), (void*)(V2), (void*)(V3), (void*)(V4), (void*)(V5), (void*)(V6), (void*)(V7), NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL)
#define PyTruffle_Arg_ParseTupleAndKeywords_8(ARGV, KWDS, FORMAT, KWDNAMES, V1, V2, V3, V4, V5, V6, V7, V8) PyTruffle_Arg_ParseTupleAndKeywords(ARGV, KWDS, FORMAT, KWDNAMES, 8, (void*)(V1), (void*)(V2), (void*)(V3), (void*)(V4), (void*)(V5), (void*)(V6), (void*)(V7), (void*)(V8), NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL)
#define PyTruffle_Arg_ParseTupleAndKeywords_9(ARGV, KWDS, FORMAT, KWDNAMES, V1, V2, V3, V4, V5, V6, V7, V8, V9) PyTruffle_Arg_ParseTupleAndKeywords(ARGV, KWDS, FORMAT, KWDNAMES, 9, (void*)(V1), (void*)(V2), (void*)(V3), (void*)(V4), (void*)(V5), (void*)(V6), (void*)(V7), (void*)(V8), (void*)(V9), NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL)
#define PyTruffle_Arg_ParseTupleAndKeywords_10(ARGV, KWDS, FORMAT, KWDNAMES, V1, V2, V3, V4, V5, V6, V7, V8, V9, V10) PyTruffle_Arg_ParseTupleAndKeywords(ARGV, KWDS, FORMAT, KWDNAMES, 10, (void*)(V1), (void*)(V2), (void*)(V3), (void*)(V4), (void*)(V5), (void*)(V6), (void*)(V7), (void*)(V8), (void*)(V9), (void*)(V10), NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL)
#define PyTruffle_Arg_ParseTupleAndKeywords_11(ARGV, KWDS, FORMAT, KWDNAMES, V1, V2, V3, V4, V5, V6, V7, V8, V9, V10, V11) PyTruffle_Arg_ParseTupleAndKeywords(ARGV, KWDS, FORMAT, KWDNAMES, 11, (void*)(V1), (void*)(V2), (void*)(V3), (void*)(V4), (void*)(V5), (void*)(V6), (void*)(V7), (void*)(V8), (void*)(V9), (void*)(V10), (void*)(V11), NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL)
#define PyTruffle_Arg_ParseTupleAndKeywords_12(ARGV, KWDS, FORMAT, KWDNAMES, V1, V2, V3, V4, V5, V6, V7, V8, V9, V10, V11, V12) PyTruffle_Arg_ParseTupleAndKeywords(ARGV, KWDS, FORMAT, KWDNAMES, 12, (void*)(V1), (void*)(V2), (void*)(V3), (void*)(V4), (void*)(V5), (void*)(V6), (void*)(V7), (void*)(V8), (void*)(V9), (void*)(V10), (void*)(V11), (void*)(V12), NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL)
#define PyTruffle_Arg_ParseTupleAndKeywords_13(ARGV, KWDS, FORMAT, KWDNAMES, V1, V2, V3, V4, V5, V6, V7, V8, V9, V10, V11, V12, V13) PyTruffle_Arg_ParseTupleAndKeywords(ARGV, KWDS, FORMAT, KWDNAMES, 13, (void*)(V1), (void*)(V2), (void*)(V3), (void*)(V4), (void*)(V5), (void*)(V6), (void*)(V7), (void*)(V8), (void*)(V9), (void*)(V10), (void*)(V11), (void*)(V12), (void*)(V13), NULL, NULL, NULL, NULL, NULL, NULL, NULL)
#define PyTruffle_Arg_ParseTupleAndKeywords_14(ARGV, KWDS, FORMAT, KWDNAMES, V1, V2, V3, V4, V5, V6, V7, V8, V9, V10, V11, V12, V13, V14) PyTruffle_Arg_ParseTupleAndKeywords(ARGV, KWDS, FORMAT, KWDNAMES, 14, (void*)(V1), (void*)(V2), (void*)(V3), (void*)(V4), (void*)(V5), (void*)(V6), (void*)(V7), (void*)(V8), (void*)(V9), (void*)(V10), (void*)(V11), (void*)(V12), (void*)(V13), (void*)(V14), NULL, NULL, NULL, NULL, NULL, NULL)
#define PyTruffle_Arg_ParseTupleAndKeywords_15(ARGV, KWDS, FORMAT, KWDNAMES, V1, V2, V3, V4, V5, V6, V7, V8, V9, V10, V11, V12, V13, V14, V15) PyTruffle_Arg_ParseTupleAndKeywords(ARGV, KWDS, FORMAT, KWDNAMES, 15, (void*)(V1), (void*)(V2), (void*)(V3), (void*)(V4), (void*)(V5), (void*)(V6), (void*)(V7), (void*)(V8), (void*)(V9), (void*)(V10), (void*)(V11), (void*)(V12), (void*)(V13), (void*)(V14), (void*)(V15), NULL, NULL, NULL, NULL, NULL)
#define PyTruffle_Arg_ParseTupleAndKeywords_16(ARGV, KWDS, FORMAT, KWDNAMES, V1, V2, V3, V4, V5, V6, V7, V8, V9, V10, V11, V12, V13, V14, V15, V16) PyTruffle_Arg_ParseTupleAndKeywords(ARGV, KWDS, FORMAT, KWDNAMES, 16, (void*)(V1), (void*)(V2), (void*)(V3), (void*)(V4), (void*)(V5), (void*)(V6), (void*)(V7), (void*)(V8), (void*)(V9), (void*)(V10), (void*)(V11), (void*)(V12), (void*)(V13), (void*)(V14), (void*)(V15), (void*)(V16), NULL, NULL, NULL, NULL)
#define PyTruffle_Arg_ParseTupleAndKeywords_17(ARGV, KWDS, FORMAT, KWDNAMES, V1, V2, V3, V4, V5, V6, V7, V8, V9, V10, V11, V12, V13, V14, V15, V16, V17) PyTruffle_Arg_ParseTupleAndKeywords(ARGV, KWDS, FORMAT, KWDNAMES, 17, (void*)(V1), (void*)(V2), (void*)(V3), (void*)(V4), (void*)(V5), (void*)(V6), (void*)(V7), (void*)(V8), (void*)(V9), (void*)(V10), (void*)(V11), (void*)(V12), (void*)(V13), (void*)(V14), (void*)(V15), (void*)(V16), (void*)(V17), NULL, NULL, NULL)
#define PyTruffle_Arg_ParseTupleAndKeywords_18(ARGV, KWDS, FORMAT, KWDNAMES, V1, V2, V3, V4, V5, V6, V7, V8, V9, V10, V11, V12, V13, V14, V15, V16, V17, V18) PyTruffle_Arg_ParseTupleAndKeywords(ARGV, KWDS, FORMAT, KWDNAMES, 18, (void*)(V1), (void*)(V2), (void*)(V3), (void*)(V4), (void*)(V5), (void*)(V6), (void*)(V7), (void*)(V8), (void*)(V9), (void*)(V10), (void*)(V11), (void*)(V12), (void*)(V13), (void*)(V14), (void*)(V15), (void*)(V16), (void*)(V17), (void*)(V18), NULL, NULL)
#define PyTruffle_Arg_ParseTupleAndKeywords_19(ARGV, KWDS, FORMAT, KWDNAMES, V1, V2, V3, V4, V5, V6, V7, V8, V9, V10, V11, V12, V13, V14, V15, V16, V17, V18, V19) PyTruffle_Arg_ParseTupleAndKeywords(ARGV, KWDS, FORMAT, KWDNAMES, 19, (void*)(V1), (void*)(V2), (void*)(V3), (void*)(V4), (void*)(V5), (void*)(V6), (void*)(V7), (void*)(V8), (void*)(V9), (void*)(V10), (void*)(V11), (void*)(V12), (void*)(V13), (void*)(V14), (void*)(V15), (void*)(V16), (void*)(V17), (void*)(V18), (void*)(V19), NULL)
#define PyTruffle_Arg_ParseTupleAndKeywords_20(ARGV, KWDS, FORMAT, KWDNAMES, V1, V2, V3, V4, V5, V6, V7, V8, V9, V10, V11, V12, V13, V14, V15, V16, V17, V18, V19, V20) PyTruffle_Arg_ParseTupleAndKeywords(ARGV, KWDS, FORMAT, KWDNAMES, 20, (void*)(V1), (void*)(V2), (void*)(V3), (void*)(V4), (void*)(V5), (void*)(V6), (void*)(V7), (void*)(V8), (void*)(V9), (void*)(V10), (void*)(V11), (void*)(V12), (void*)(V13), (void*)(V14), (void*)(V15), (void*)(V16), (void*)(V17), (void*)(V18), (void*)(V19), (void*)V20)
#define ARG_PARSE_TUPLE_IMPL(_0, _1, _2, _3, _4, _5, _6, _7, _8, _9, _10, _11, _12, _13, _14, _15, _16, _17, _18, _19, _20, NAME, ...) NAME
#ifdef PyArg_ParseTupleAndKeywords
#undef PyArg_ParseTupleAndKeywords
#endif
#define PyArg_ParseTupleAndKeywords(ARGV, KWDS, FORMAT, ...) ARG_PARSE_TUPLE_IMPL(__VA_ARGS__, PyTruffle_Arg_ParseTupleAndKeywords_20, PyTruffle_Arg_ParseTupleAndKeywords_19, PyTruffle_Arg_ParseTupleAndKeywords_18, PyTruffle_Arg_ParseTupleAndKeywords_17, PyTruffle_Arg_ParseTupleAndKeywords_16, PyTruffle_Arg_ParseTupleAndKeywords_15, PyTruffle_Arg_ParseTupleAndKeywords_14, PyTruffle_Arg_ParseTupleAndKeywords_13, PyTruffle_Arg_ParseTupleAndKeywords_12, PyTruffle_Arg_ParseTupleAndKeywords_11, PyTruffle_Arg_ParseTupleAndKeywords_10, PyTruffle_Arg_ParseTupleAndKeywords_9, PyTruffle_Arg_ParseTupleAndKeywords_8, PyTruffle_Arg_ParseTupleAndKeywords_7, PyTruffle_Arg_ParseTupleAndKeywords_6, PyTruffle_Arg_ParseTupleAndKeywords_5, PyTruffle_Arg_ParseTupleAndKeywords_4, PyTruffle_Arg_ParseTupleAndKeywords_3, PyTruffle_Arg_ParseTupleAndKeywords_2, PyTruffle_Arg_ParseTupleAndKeywords_1, PyTruffle_Arg_ParseTupleAndKeywords_0)(ARGV, KWDS, FORMAT, __VA_ARGS__)

#ifdef PyArg_ParseTuple
#undef PyArg_ParseTuple
#endif
#define PyArg_ParseTuple(ARGV, FORMAT, ...) PyArg_ParseTupleAndKeywords(ARGV, PyDict_New(), FORMAT, NULL, ##__VA_ARGS__)

#ifdef _PyArg_ParseTupleAndKeywordsFast
#undef _PyArg_ParseTupleAndKeywordsFast
#endif
#define _PyArg_ParseTupleAndKeywordsFast(ARGS, KWARGS, PARSER, ...) PyArg_ParseTupleAndKeywords(ARGS, KWARGS, (PARSER)->format, (PARSER)->keywords, __VA_ARGS__)

#ifdef PyArg_Parse
#undef PyArg_Parse
#endif
#define PyArg_Parse(ARGV, FORMAT, ...) PyArg_ParseTupleAndKeywords(PyTuple_Pack(1, ARGV), PyDict_New(), FORMAT, NULL, __VA_ARGS__)

extern PyObject * PyTruffle_Unicode_FromFormat(const char *fmt, int s, void* v0, void* v1, void* v2, void* v3, void* v4, void* v5, void* v6, void* v7, void* v8, void* v9, void* v10, void* v11, void* v12, void* v13, void* v14, void* v15, void* v16, void* v17, void* v18, void* v19);
#define PyTruffle_Unicode_FromFormat_0(F1) PyTruffle_Unicode_FromFormat(F1, 0, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL)
#define PyTruffle_Unicode_FromFormat_1(F1, V1) PyTruffle_Unicode_FromFormat(F1, 1, (void*)(V1), NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL)
#define PyTruffle_Unicode_FromFormat_2(F1, V1, V2) PyTruffle_Unicode_FromFormat(F1, 2, (void*)(V1), (void*)(V2), NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL)
#define PyTruffle_Unicode_FromFormat_3(F1, V1, V2, V3) PyTruffle_Unicode_FromFormat(F1, 3, (void*)(V1), (void*)(V2), (void*)(V3), NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL)
#define PyTruffle_Unicode_FromFormat_4(F1, V1, V2, V3, V4) PyTruffle_Unicode_FromFormat(F1, 4, (void*)(V1), (void*)(V2), (void*)(V3), (void*)(V4), NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL)
#define PyTruffle_Unicode_FromFormat_5(F1, V1, V2, V3, V4, V5) PyTruffle_Unicode_FromFormat(F1, 5, (void*)(V1), (void*)(V2), (void*)(V3), (void*)(V4), (void*)(V5), NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL)
#define PyTruffle_Unicode_FromFormat_6(F1, V1, V2, V3, V4, V5, V6) PyTruffle_Unicode_FromFormat(F1, 6, (void*)(V1), (void*)(V2), (void*)(V3), (void*)(V4), (void*)(V5), (void*)(V6), NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL)
#define PyTruffle_Unicode_FromFormat_7(F1, V1, V2, V3, V4, V5, V6, V7) PyTruffle_Unicode_FromFormat(F1, 7, (void*)(V1), (void*)(V2), (void*)(V3), (void*)(V4), (void*)(V5), (void*)(V6), (void*)(V7), NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL)
#define PyTruffle_Unicode_FromFormat_8(F1, V1, V2, V3, V4, V5, V6, V7, V8) PyTruffle_Unicode_FromFormat(F1, 8, (void*)(V1), (void*)(V2), (void*)(V3), (void*)(V4), (void*)(V5), (void*)(V6), (void*)(V7), (void*)(V8), NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL)
#define PyTruffle_Unicode_FromFormat_9(F1, V1, V2, V3, V4, V5, V6, V7, V8, V9) PyTruffle_Unicode_FromFormat(F1, 9, (void*)(V1), (void*)(V2), (void*)(V3), (void*)(V4), (void*)(V5), (void*)(V6), (void*)(V7), (void*)(V8), (void*)(V9), NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL)
#define PyTruffle_Unicode_FromFormat_10(F1, V1, V2, V3, V4, V5, V6, V7, V8, V9, V10) PyTruffle_Unicode_FromFormat(F1, 10, (void*)(V1), (void*)(V2), (void*)(V3), (void*)(V4), (void*)(V5), (void*)(V6), (void*)(V7), (void*)(V8), (void*)(V9), (void*)(V10), NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL)
#define PyTruffle_Unicode_FromFormat_11(F1, V1, V2, V3, V4, V5, V6, V7, V8, V9, V10, V11) PyTruffle_Unicode_FromFormat(F1, 11, (void*)(V1), (void*)(V2), (void*)(V3), (void*)(V4), (void*)(V5), (void*)(V6), (void*)(V7), (void*)(V8), (void*)(V9), (void*)(V10), (void*)(V11), NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL)
#define PyTruffle_Unicode_FromFormat_12(F1, V1, V2, V3, V4, V5, V6, V7, V8, V9, V10, V11, V12) PyTruffle_Unicode_FromFormat(F1, 12, (void*)(V1), (void*)(V2), (void*)(V3), (void*)(V4), (void*)(V5), (void*)(V6), (void*)(V7), (void*)(V8), (void*)(V9), (void*)(V10), (void*)(V11), (void*)(V12), NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL)
#define PyTruffle_Unicode_FromFormat_13(F1, V1, V2, V3, V4, V5, V6, V7, V8, V9, V10, V11, V12, V13) PyTruffle_Unicode_FromFormat(F1, 13, (void*)(V1), (void*)(V2), (void*)(V3), (void*)(V4), (void*)(V5), (void*)(V6), (void*)(V7), (void*)(V8), (void*)(V9), (void*)(V10), (void*)(V11), (void*)(V12), (void*)(V13), NULL, NULL, NULL, NULL, NULL, NULL, NULL)
#define PyTruffle_Unicode_FromFormat_14(F1, V1, V2, V3, V4, V5, V6, V7, V8, V9, V10, V11, V12, V13, V14) PyTruffle_Unicode_FromFormat(F1, 14, (void*)(V1), (void*)(V2), (void*)(V3), (void*)(V4), (void*)(V5), (void*)(V6), (void*)(V7), (void*)(V8), (void*)(V9), (void*)(V10), (void*)(V11), (void*)(V12), (void*)(V13), (void*)(V14), NULL, NULL, NULL, NULL, NULL, NULL)
#define PyTruffle_Unicode_FromFormat_15(F1, V1, V2, V3, V4, V5, V6, V7, V8, V9, V10, V11, V12, V13, V14, V15) PyTruffle_Unicode_FromFormat(F1, 15, (void*)(V1), (void*)(V2), (void*)(V3), (void*)(V4), (void*)(V5), (void*)(V6), (void*)(V7), (void*)(V8), (void*)(V9), (void*)(V10), (void*)(V11), (void*)(V12), (void*)(V13), (void*)(V14), (void*)(V15), NULL, NULL, NULL, NULL, NULL)
#define PyTruffle_Unicode_FromFormat_16(F1, V1, V2, V3, V4, V5, V6, V7, V8, V9, V10, V11, V12, V13, V14, V15, V16) PyTruffle_Unicode_FromFormat(F1, 16, (void*)(V1), (void*)(V2), (void*)(V3), (void*)(V4), (void*)(V5), (void*)(V6), (void*)(V7), (void*)(V8), (void*)(V9), (void*)(V10), (void*)(V11), (void*)(V12), (void*)(V13), (void*)(V14), (void*)(V15), (void*)(V16), NULL, NULL, NULL, NULL)
#define PyTruffle_Unicode_FromFormat_17(F1, V1, V2, V3, V4, V5, V6, V7, V8, V9, V10, V11, V12, V13, V14, V15, V16, V17) PyTruffle_Unicode_FromFormat(F1, 17, (void*)(V1), (void*)(V2), (void*)(V3), (void*)(V4), (void*)(V5), (void*)(V6), (void*)(V7), (void*)(V8), (void*)(V9), (void*)(V10), (void*)(V11), (void*)(V12), (void*)(V13), (void*)(V14), (void*)(V15), (void*)(V16), (void*)(V17), NULL, NULL, NULL)
#define PyTruffle_Unicode_FromFormat_18(F1, V1, V2, V3, V4, V5, V6, V7, V8, V9, V10, V11, V12, V13, V14, V15, V16, V17, V18) PyTruffle_Unicode_FromFormat(F1, 18, (void*)(V1), (void*)(V2), (void*)(V3), (void*)(V4), (void*)(V5), (void*)(V6), (void*)(V7), (void*)(V8), (void*)(V9), (void*)(V10), (void*)(V11), (void*)(V12), (void*)(V13), (void*)(V14), (void*)(V15), (void*)(V16), (void*)(V17), (void*)(V18), NULL, NULL)
#define PyTruffle_Unicode_FromFormat_19(F1, V1, V2, V3, V4, V5, V6, V7, V8, V9, V10, V11, V12, V13, V14, V15, V16, V17, V18, V19) PyTruffle_Unicode_FromFormat(F1, 19, (void*)(V1), (void*)(V2), (void*)(V3), (void*)(V4), (void*)(V5), (void*)(V6), (void*)(V7), (void*)(V8), (void*)(V9), (void*)(V10), (void*)(V11), (void*)(V12), (void*)(V13), (void*)(V14), (void*)(V15), (void*)(V16), (void*)(V17), (void*)(V18), (void*)(V19), NULL)
#define PyTruffle_Unicode_FromFormat_20(F1, V1, V2, V3, V4, V5, V6, V7, V8, V9, V10, V11, V12, V13, V14, V15, V16, V17, V18, V19, V20) PyTruffle_Unicode_FromFormat(F1, 20, (void*)(V1), (void*)(V2), (void*)(V3), (void*)(V4), (void*)(V5), (void*)(V6), (void*)(V7), (void*)(V8), (void*)(V9), (void*)(V10), (void*)(V11), (void*)(V12), (void*)(V13), (void*)(V14), (void*)(V15), (void*)(V16), (void*)(V17), (void*)(V18), (void*)(V19), (void*)(V20))
#define ARG_PARSE_PyUnicode_FromFormat_IMPL(DUMMY, _0, _1, _2, _3, _4, _5, _6, _7, _8, _9, _10, _11, _12, _13, _14, _15, _16, _17, _18, _19, NAME, ...) NAME
#define PyUnicode_FromFormat(F1, ...) ARG_PARSE_PyUnicode_FromFormat_IMPL(NULL, ##__VA_ARGS__, PyTruffle_Unicode_FromFormat_20, PyTruffle_Unicode_FromFormat_19, PyTruffle_Unicode_FromFormat_18, PyTruffle_Unicode_FromFormat_17, PyTruffle_Unicode_FromFormat_16, PyTruffle_Unicode_FromFormat_15, PyTruffle_Unicode_FromFormat_14, PyTruffle_Unicode_FromFormat_13, PyTruffle_Unicode_FromFormat_12, PyTruffle_Unicode_FromFormat_11, PyTruffle_Unicode_FromFormat_10, PyTruffle_Unicode_FromFormat_9, PyTruffle_Unicode_FromFormat_8, PyTruffle_Unicode_FromFormat_7, PyTruffle_Unicode_FromFormat_6, PyTruffle_Unicode_FromFormat_5, PyTruffle_Unicode_FromFormat_4, PyTruffle_Unicode_FromFormat_3, PyTruffle_Unicode_FromFormat_2, PyTruffle_Unicode_FromFormat_1, PyTruffle_Unicode_FromFormat_0)(F1, ## __VA_ARGS__)

extern PyObject* PyTruffle_Err_Format(PyObject* exception, const char* fmt, int s, void* v0, void* v1, void* v2, void* v3, void* v4, void* v5, void* v6, void* v7, void* v8, void* v9);
#define PyTruffle_Err_Format_0(EXC, FORMAT) PyTruffle_Err_Format(EXC, FORMAT, 0, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL)
#define PyTruffle_Err_Format_1(EXC, FORMAT, V1) PyTruffle_Err_Format(EXC, FORMAT, 1, (void*)(V1), NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL)
#define PyTruffle_Err_Format_2(EXC, FORMAT, V1, V2) PyTruffle_Err_Format(EXC, FORMAT, 2, (void*)(V1), (void*)(V2), NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL)
#define PyTruffle_Err_Format_3(EXC, FORMAT, V1, V2, V3) PyTruffle_Err_Format(EXC, FORMAT, 3, (void*)(V1), (void*)(V2), (void*)(V3), NULL, NULL, NULL, NULL, NULL, NULL, NULL)
#define PyTruffle_Err_Format_4(EXC, FORMAT, V1, V2, V3, V4) PyTruffle_Err_Format(EXC, FORMAT, 4, (void*)(V1), (void*)(V2), (void*)(V3), (void*)(V4), NULL, NULL, NULL, NULL, NULL, NULL)
#define PyTruffle_Err_Format_5(EXC, FORMAT, V1, V2, V3, V4, V5) PyTruffle_Err_Format(EXC, FORMAT, 5, (void*)(V1), (void*)(V2), (void*)(V3), (void*)(V4), (void*)(V5), NULL, NULL, NULL, NULL, NULL)
#define PyTruffle_Err_Format_6(EXC, FORMAT, V1, V2, V3, V4, V5, V6) PyTruffle_Err_Format(EXC, FORMAT, 6, (void*)(V1), (void*)(V2), (void*)(V3), (void*)(V4), (void*)(V5), (void*)(V6), NULL, NULL, NULL, NULL)
#define PyTruffle_Err_Format_7(EXC, FORMAT, V1, V2, V3, V4, V5, V6, V7) PyTruffle_Err_Format(EXC, FORMAT, 7, (void*)(V1), (void*)(V2), (void*)(V3), (void*)(V4), (void*)(V5), (void*)(V6), (void*)(V7), NULL, NULL, NULL)
#define PyTruffle_Err_Format_8(EXC, FORMAT, V1, V2, V3, V4, V5, V6, V7, V8) PyTruffle_Err_Format(EXC, FORMAT, 8, (void*)(V1), (void*)(V2), (void*)(V3), (void*)(V4), (void*)(V5), (void*)(V6), (void*)(V7), (void*)(V8), NULL, NULL)
#define PyTruffle_Err_Format_9(EXC, FORMAT, V1, V2, V3, V4, V5, V6, V7, V8, V9) PyTruffle_Err_Format(EXC, FORMAT, 9, (void*)(V1), (void*)(V2), (void*)(V3), (void*)(V4), (void*)(V5), (void*)(V6), (void*)(V7), (void*)(V8), (void*)(V9), NULL)
#define PyTruffle_Err_Format_10(EXC, FORMAT, V1, V2, V3, V4, V5, V6, V7, V8, V9, V10) PyTruffle_Err_Format(EXC, FORMAT, 10, (void*)(V1), (void*)(V2), (void*)(V3), (void*)(V4), (void*)(V5), (void*)(V6), (void*)(V7), (void*)(V8), (void*)(V9), (void*)V10)
#define ARG_PARSE_ERR_FORMAT_IMPL(_0, _1, _2, _3, _4, _5, _6, _7, _8, _9, _10, NAME, ...) NAME
#ifdef PyErr_Format
#undef PyErr_Format
#endif
#define PyErr_Format(EXC, ...) ARG_PARSE_ERR_FORMAT_IMPL(__VA_ARGS__, PyTruffle_Err_Format_10, PyTruffle_Err_Format_9, PyTruffle_Err_Format_8, PyTruffle_Err_Format_7, PyTruffle_Err_Format_6, PyTruffle_Err_Format_5, PyTruffle_Err_Format_4, PyTruffle_Err_Format_3, PyTruffle_Err_Format_2, PyTruffle_Err_Format_1, PyTruffle_Err_Format_0)(EXC, __VA_ARGS__)

extern int PyTruffle_Arg_ParseStack_SizeT(PyObject *const *args, Py_ssize_t nargs, const char *format, int s, void* v0, void* v1, void* v2, void* v3, void* v4, void* v5, void* v6, void* v7, void* v8, void* v9, void* v10, void* v11, void* v12, void* v13, void* v14, void* v15, void* v16, void* v17, void* v18, void* v19);
#define PyTruffle_Arg_ParseStack_SizeT_0(F1, F2, F3) PyTruffle_Arg_ParseStack_SizeT(F1, F2, F3, 0, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL)
#define PyTruffle_Arg_ParseStack_SizeT_1(F1, F2, F3, V1) PyTruffle_Arg_ParseStack_SizeT(F1, F2, F3, 1, (void*)(V1), NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL)
#define PyTruffle_Arg_ParseStack_SizeT_2(F1, F2, F3, V1, V2) PyTruffle_Arg_ParseStack_SizeT(F1, F2, F3, 2, (void*)(V1), (void*)(V2), NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL)
#define PyTruffle_Arg_ParseStack_SizeT_3(F1, F2, F3, V1, V2, V3) PyTruffle_Arg_ParseStack_SizeT(F1, F2, F3, 3, (void*)(V1), (void*)(V2), (void*)(V3), NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL)
#define PyTruffle_Arg_ParseStack_SizeT_4(F1, F2, F3, V1, V2, V3, V4) PyTruffle_Arg_ParseStack_SizeT(F1, F2, F3, 4, (void*)(V1), (void*)(V2), (void*)(V3), (void*)(V4), NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL)
#define PyTruffle_Arg_ParseStack_SizeT_5(F1, F2, F3, V1, V2, V3, V4, V5) PyTruffle_Arg_ParseStack_SizeT(F1, F2, F3, 5, (void*)(V1), (void*)(V2), (void*)(V3), (void*)(V4), (void*)(V5), NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL)
#define PyTruffle_Arg_ParseStack_SizeT_6(F1, F2, F3, V1, V2, V3, V4, V5, V6) PyTruffle_Arg_ParseStack_SizeT(F1, F2, F3, 6, (void*)(V1), (void*)(V2), (void*)(V3), (void*)(V4), (void*)(V5), (void*)(V6), NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL)
#define PyTruffle_Arg_ParseStack_SizeT_7(F1, F2, F3, V1, V2, V3, V4, V5, V6, V7) PyTruffle_Arg_ParseStack_SizeT(F1, F2, F3, 7, (void*)(V1), (void*)(V2), (void*)(V3), (void*)(V4), (void*)(V5), (void*)(V6), (void*)(V7), NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL)
#define PyTruffle_Arg_ParseStack_SizeT_8(F1, F2, F3, V1, V2, V3, V4, V5, V6, V7, V8) PyTruffle_Arg_ParseStack_SizeT(F1, F2, F3, 8, (void*)(V1), (void*)(V2), (void*)(V3), (void*)(V4), (void*)(V5), (void*)(V6), (void*)(V7), (void*)(V8), NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL)
#define PyTruffle_Arg_ParseStack_SizeT_9(F1, F2, F3, V1, V2, V3, V4, V5, V6, V7, V8, V9) PyTruffle_Arg_ParseStack_SizeT(F1, F2, F3, 9, (void*)(V1), (void*)(V2), (void*)(V3), (void*)(V4), (void*)(V5), (void*)(V6), (void*)(V7), (void*)(V8), (void*)(V9), NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL)
#define PyTruffle_Arg_ParseStack_SizeT_10(F1, F2, F3, V1, V2, V3, V4, V5, V6, V7, V8, V9, V10) PyTruffle_Arg_ParseStack_SizeT(F1, F2, F3, 10, (void*)(V1), (void*)(V2), (void*)(V3), (void*)(V4), (void*)(V5), (void*)(V6), (void*)(V7), (void*)(V8), (void*)(V9), (void*)(V10), NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL)
#define PyTruffle_Arg_ParseStack_SizeT_11(F1, F2, F3, V1, V2, V3, V4, V5, V6, V7, V8, V9, V10, V11) PyTruffle_Arg_ParseStack_SizeT(F1, F2, F3, 11, (void*)(V1), (void*)(V2), (void*)(V3), (void*)(V4), (void*)(V5), (void*)(V6), (void*)(V7), (void*)(V8), (void*)(V9), (void*)(V10), (void*)(V11), NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL)
#define PyTruffle_Arg_ParseStack_SizeT_12(F1, F2, F3, V1, V2, V3, V4, V5, V6, V7, V8, V9, V10, V11, V12) PyTruffle_Arg_ParseStack_SizeT(F1, F2, F3, 12, (void*)(V1), (void*)(V2), (void*)(V3), (void*)(V4), (void*)(V5), (void*)(V6), (void*)(V7), (void*)(V8), (void*)(V9), (void*)(V10), (void*)(V11), (void*)(V12), NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL)
#define PyTruffle_Arg_ParseStack_SizeT_13(F1, F2, F3, V1, V2, V3, V4, V5, V6, V7, V8, V9, V10, V11, V12, V13) PyTruffle_Arg_ParseStack_SizeT(F1, F2, F3, 13, (void*)(V1), (void*)(V2), (void*)(V3), (void*)(V4), (void*)(V5), (void*)(V6), (void*)(V7), (void*)(V8), (void*)(V9), (void*)(V10), (void*)(V11), (void*)(V12), (void*)(V13), NULL, NULL, NULL, NULL, NULL, NULL, NULL)
#define PyTruffle_Arg_ParseStack_SizeT_14(F1, F2, F3, V1, V2, V3, V4, V5, V6, V7, V8, V9, V10, V11, V12, V13, V14) PyTruffle_Arg_ParseStack_SizeT(F1, F2, F3, 14, (void*)(V1), (void*)(V2), (void*)(V3), (void*)(V4), (void*)(V5), (void*)(V6), (void*)(V7), (void*)(V8), (void*)(V9), (void*)(V10), (void*)(V11), (void*)(V12), (void*)(V13), (void*)(V14), NULL, NULL, NULL, NULL, NULL, NULL)
#define PyTruffle_Arg_ParseStack_SizeT_15(F1, F2, F3, V1, V2, V3, V4, V5, V6, V7, V8, V9, V10, V11, V12, V13, V14, V15) PyTruffle_Arg_ParseStack_SizeT(F1, F2, F3, 15, (void*)(V1), (void*)(V2), (void*)(V3), (void*)(V4), (void*)(V5), (void*)(V6), (void*)(V7), (void*)(V8), (void*)(V9), (void*)(V10), (void*)(V11), (void*)(V12), (void*)(V13), (void*)(V14), (void*)(V15), NULL, NULL, NULL, NULL, NULL)
#define PyTruffle_Arg_ParseStack_SizeT_16(F1, F2, F3, V1, V2, V3, V4, V5, V6, V7, V8, V9, V10, V11, V12, V13, V14, V15, V16) PyTruffle_Arg_ParseStack_SizeT(F1, F2, F3, 16, (void*)(V1), (void*)(V2), (void*)(V3), (void*)(V4), (void*)(V5), (void*)(V6), (void*)(V7), (void*)(V8), (void*)(V9), (void*)(V10), (void*)(V11), (void*)(V12), (void*)(V13), (void*)(V14), (void*)(V15), (void*)(V16), NULL, NULL, NULL, NULL)
#define PyTruffle_Arg_ParseStack_SizeT_17(F1, F2, F3, V1, V2, V3, V4, V5, V6, V7, V8, V9, V10, V11, V12, V13, V14, V15, V16, V17) PyTruffle_Arg_ParseStack_SizeT(F1, F2, F3, 17, (void*)(V1), (void*)(V2), (void*)(V3), (void*)(V4), (void*)(V5), (void*)(V6), (void*)(V7), (void*)(V8), (void*)(V9), (void*)(V10), (void*)(V11), (void*)(V12), (void*)(V13), (void*)(V14), (void*)(V15), (void*)(V16), (void*)(V17), NULL, NULL, NULL)
#define PyTruffle_Arg_ParseStack_SizeT_18(F1, F2, F3, V1, V2, V3, V4, V5, V6, V7, V8, V9, V10, V11, V12, V13, V14, V15, V16, V17, V18) PyTruffle_Arg_ParseStack_SizeT(F1, F2, F3, 18, (void*)(V1), (void*)(V2), (void*)(V3), (void*)(V4), (void*)(V5), (void*)(V6), (void*)(V7), (void*)(V8), (void*)(V9), (void*)(V10), (void*)(V11), (void*)(V12), (void*)(V13), (void*)(V14), (void*)(V15), (void*)(V16), (void*)(V17), (void*)(V18), NULL, NULL)
#define PyTruffle_Arg_ParseStack_SizeT_19(F1, F2, F3, V1, V2, V3, V4, V5, V6, V7, V8, V9, V10, V11, V12, V13, V14, V15, V16, V17, V18, V19) PyTruffle_Arg_ParseStack_SizeT(F1, F2, F3, 19, (void*)(V1), (void*)(V2), (void*)(V3), (void*)(V4), (void*)(V5), (void*)(V6), (void*)(V7), (void*)(V8), (void*)(V9), (void*)(V10), (void*)(V11), (void*)(V12), (void*)(V13), (void*)(V14), (void*)(V15), (void*)(V16), (void*)(V17), (void*)(V18), (void*)(V19), NULL)
#define PyTruffle_Arg_ParseStack_SizeT_20(F1, F2, F3, V1, V2, V3, V4, V5, V6, V7, V8, V9, V10, V11, V12, V13, V14, V15, V16, V17, V18, V19, V20) PyTruffle_Arg_ParseStack_SizeT(F1, F2, F3, 20, (void*)(V1), (void*)(V2), (void*)(V3), (void*)(V4), (void*)(V5), (void*)(V6), (void*)(V7), (void*)(V8), (void*)(V9), (void*)(V10), (void*)(V11), (void*)(V12), (void*)(V13), (void*)(V14), (void*)(V15), (void*)(V16), (void*)(V17), (void*)(V18), (void*)(V19), (void*)(V20))
#define ARG_PARSE__PyArg_ParseStack_SizeT_IMPL(_0, _1, _2, _3, _4, _5, _6, _7, _8, _9, _10, _11, _12, _13, _14, _15, _16, _17, _18, _19, NAME, ...) NAME
#define _PyArg_ParseStack_SizeT(F1, F2, F3, ...) ARG_PARSE__PyArg_ParseStack_SizeT_IMPL(__VA_ARGS__, PyTruffle_Arg_ParseStack_SizeT_20, PyTruffle_Arg_ParseStack_SizeT_19, PyTruffle_Arg_ParseStack_SizeT_18, PyTruffle_Arg_ParseStack_SizeT_17, PyTruffle_Arg_ParseStack_SizeT_16, PyTruffle_Arg_ParseStack_SizeT_15, PyTruffle_Arg_ParseStack_SizeT_14, PyTruffle_Arg_ParseStack_SizeT_13, PyTruffle_Arg_ParseStack_SizeT_12, PyTruffle_Arg_ParseStack_SizeT_11, PyTruffle_Arg_ParseStack_SizeT_10, PyTruffle_Arg_ParseStack_SizeT_9, PyTruffle_Arg_ParseStack_SizeT_8, PyTruffle_Arg_ParseStack_SizeT_7, PyTruffle_Arg_ParseStack_SizeT_6, PyTruffle_Arg_ParseStack_SizeT_5, PyTruffle_Arg_ParseStack_SizeT_4, PyTruffle_Arg_ParseStack_SizeT_3, PyTruffle_Arg_ParseStack_SizeT_2, PyTruffle_Arg_ParseStack_SizeT_1, PyTruffle_Arg_ParseStack_SizeT_0)(F1, F2, F3, ##__VA_ARGS__)

extern int PyTruffle_Arg_ParseStackAndKeywords_SizeT(PyObject *const *args, Py_ssize_t nargs, PyObject *kwnames,  struct _PyArg_Parser *parser, int s, void* v0, void* v1, void* v2, void* v3, void* v4, void* v5, void* v6, void* v7, void* v8, void* v9, void* v10, void* v11, void* v12, void* v13, void* v14, void* v15, void* v16, void* v17, void* v18, void* v19);
#define PyTruffle_Arg_ParseStackAndKeywords_SizeT_0(F1, F2, F3, F4) PyTruffle_Arg_ParseStackAndKeywords_SizeT(F1, F2, F3, F4, 0, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL)
#define PyTruffle_Arg_ParseStackAndKeywords_SizeT_1(F1, F2, F3, F4, V1) PyTruffle_Arg_ParseStackAndKeywords_SizeT(F1, F2, F3, F4, 1, (void*)(V1), NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL)
#define PyTruffle_Arg_ParseStackAndKeywords_SizeT_2(F1, F2, F3, F4, V1, V2) PyTruffle_Arg_ParseStackAndKeywords_SizeT(F1, F2, F3, F4, 2, (void*)(V1), (void*)(V2), NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL)
#define PyTruffle_Arg_ParseStackAndKeywords_SizeT_3(F1, F2, F3, F4, V1, V2, V3) PyTruffle_Arg_ParseStackAndKeywords_SizeT(F1, F2, F3, F4, 3, (void*)(V1), (void*)(V2), (void*)(V3), NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL)
#define PyTruffle_Arg_ParseStackAndKeywords_SizeT_4(F1, F2, F3, F4, V1, V2, V3, V4) PyTruffle_Arg_ParseStackAndKeywords_SizeT(F1, F2, F3, F4, 4, (void*)(V1), (void*)(V2), (void*)(V3), (void*)(V4), NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL)
#define PyTruffle_Arg_ParseStackAndKeywords_SizeT_5(F1, F2, F3, F4, V1, V2, V3, V4, V5) PyTruffle_Arg_ParseStackAndKeywords_SizeT(F1, F2, F3, F4, 5, (void*)(V1), (void*)(V2), (void*)(V3), (void*)(V4), (void*)(V5), NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL)
#define PyTruffle_Arg_ParseStackAndKeywords_SizeT_6(F1, F2, F3, F4, V1, V2, V3, V4, V5, V6) PyTruffle_Arg_ParseStackAndKeywords_SizeT(F1, F2, F3, F4, 6, (void*)(V1), (void*)(V2), (void*)(V3), (void*)(V4), (void*)(V5), (void*)(V6), NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL)
#define PyTruffle_Arg_ParseStackAndKeywords_SizeT_7(F1, F2, F3, F4, V1, V2, V3, V4, V5, V6, V7) PyTruffle_Arg_ParseStackAndKeywords_SizeT(F1, F2, F3, F4, 7, (void*)(V1), (void*)(V2), (void*)(V3), (void*)(V4), (void*)(V5), (void*)(V6), (void*)(V7), NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL)
#define PyTruffle_Arg_ParseStackAndKeywords_SizeT_8(F1, F2, F3, F4, V1, V2, V3, V4, V5, V6, V7, V8) PyTruffle_Arg_ParseStackAndKeywords_SizeT(F1, F2, F3, F4, 8, (void*)(V1), (void*)(V2), (void*)(V3), (void*)(V4), (void*)(V5), (void*)(V6), (void*)(V7), (void*)(V8), NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL)
#define PyTruffle_Arg_ParseStackAndKeywords_SizeT_9(F1, F2, F3, F4, V1, V2, V3, V4, V5, V6, V7, V8, V9) PyTruffle_Arg_ParseStackAndKeywords_SizeT(F1, F2, F3, F4, 9, (void*)(V1), (void*)(V2), (void*)(V3), (void*)(V4), (void*)(V5), (void*)(V6), (void*)(V7), (void*)(V8), (void*)(V9), NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL)
#define PyTruffle_Arg_ParseStackAndKeywords_SizeT_10(F1, F2, F3, F4, V1, V2, V3, V4, V5, V6, V7, V8, V9, V10) PyTruffle_Arg_ParseStackAndKeywords_SizeT(F1, F2, F3, F4, 10, (void*)(V1), (void*)(V2), (void*)(V3), (void*)(V4), (void*)(V5), (void*)(V6), (void*)(V7), (void*)(V8), (void*)(V9), (void*)(V10), NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL)
#define PyTruffle_Arg_ParseStackAndKeywords_SizeT_11(F1, F2, F3, F4, V1, V2, V3, V4, V5, V6, V7, V8, V9, V10, V11) PyTruffle_Arg_ParseStackAndKeywords_SizeT(F1, F2, F3, F4, 11, (void*)(V1), (void*)(V2), (void*)(V3), (void*)(V4), (void*)(V5), (void*)(V6), (void*)(V7), (void*)(V8), (void*)(V9), (void*)(V10), (void*)(V11), NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL)
#define PyTruffle_Arg_ParseStackAndKeywords_SizeT_12(F1, F2, F3, F4, V1, V2, V3, V4, V5, V6, V7, V8, V9, V10, V11, V12) PyTruffle_Arg_ParseStackAndKeywords_SizeT(F1, F2, F3, F4, 12, (void*)(V1), (void*)(V2), (void*)(V3), (void*)(V4), (void*)(V5), (void*)(V6), (void*)(V7), (void*)(V8), (void*)(V9), (void*)(V10), (void*)(V11), (void*)(V12), NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL)
#define PyTruffle_Arg_ParseStackAndKeywords_SizeT_13(F1, F2, F3, F4, V1, V2, V3, V4, V5, V6, V7, V8, V9, V10, V11, V12, V13) PyTruffle_Arg_ParseStackAndKeywords_SizeT(F1, F2, F3, F4, 13, (void*)(V1), (void*)(V2), (void*)(V3), (void*)(V4), (void*)(V5), (void*)(V6), (void*)(V7), (void*)(V8), (void*)(V9), (void*)(V10), (void*)(V11), (void*)(V12), (void*)(V13), NULL, NULL, NULL, NULL, NULL, NULL, NULL)
#define PyTruffle_Arg_ParseStackAndKeywords_SizeT_14(F1, F2, F3, F4, V1, V2, V3, V4, V5, V6, V7, V8, V9, V10, V11, V12, V13, V14) PyTruffle_Arg_ParseStackAndKeywords_SizeT(F1, F2, F3, F4, 14, (void*)(V1), (void*)(V2), (void*)(V3), (void*)(V4), (void*)(V5), (void*)(V6), (void*)(V7), (void*)(V8), (void*)(V9), (void*)(V10), (void*)(V11), (void*)(V12), (void*)(V13), (void*)(V14), NULL, NULL, NULL, NULL, NULL, NULL)
#define PyTruffle_Arg_ParseStackAndKeywords_SizeT_15(F1, F2, F3, F4, V1, V2, V3, V4, V5, V6, V7, V8, V9, V10, V11, V12, V13, V14, V15) PyTruffle_Arg_ParseStackAndKeywords_SizeT(F1, F2, F3, F4, 15, (void*)(V1), (void*)(V2), (void*)(V3), (void*)(V4), (void*)(V5), (void*)(V6), (void*)(V7), (void*)(V8), (void*)(V9), (void*)(V10), (void*)(V11), (void*)(V12), (void*)(V13), (void*)(V14), (void*)(V15), NULL, NULL, NULL, NULL, NULL)
#define PyTruffle_Arg_ParseStackAndKeywords_SizeT_16(F1, F2, F3, F4, V1, V2, V3, V4, V5, V6, V7, V8, V9, V10, V11, V12, V13, V14, V15, V16) PyTruffle_Arg_ParseStackAndKeywords_SizeT(F1, F2, F3, F4, 16, (void*)(V1), (void*)(V2), (void*)(V3), (void*)(V4), (void*)(V5), (void*)(V6), (void*)(V7), (void*)(V8), (void*)(V9), (void*)(V10), (void*)(V11), (void*)(V12), (void*)(V13), (void*)(V14), (void*)(V15), (void*)(V16), NULL, NULL, NULL, NULL)
#define PyTruffle_Arg_ParseStackAndKeywords_SizeT_17(F1, F2, F3, F4, V1, V2, V3, V4, V5, V6, V7, V8, V9, V10, V11, V12, V13, V14, V15, V16, V17) PyTruffle_Arg_ParseStackAndKeywords_SizeT(F1, F2, F3, F4, 17, (void*)(V1), (void*)(V2), (void*)(V3), (void*)(V4), (void*)(V5), (void*)(V6), (void*)(V7), (void*)(V8), (void*)(V9), (void*)(V10), (void*)(V11), (void*)(V12), (void*)(V13), (void*)(V14), (void*)(V15), (void*)(V16), (void*)(V17), NULL, NULL, NULL)
#define PyTruffle_Arg_ParseStackAndKeywords_SizeT_18(F1, F2, F3, F4, V1, V2, V3, V4, V5, V6, V7, V8, V9, V10, V11, V12, V13, V14, V15, V16, V17, V18) PyTruffle_Arg_ParseStackAndKeywords_SizeT(F1, F2, F3, F4, 18, (void*)(V1), (void*)(V2), (void*)(V3), (void*)(V4), (void*)(V5), (void*)(V6), (void*)(V7), (void*)(V8), (void*)(V9), (void*)(V10), (void*)(V11), (void*)(V12), (void*)(V13), (void*)(V14), (void*)(V15), (void*)(V16), (void*)(V17), (void*)(V18), NULL, NULL)
#define PyTruffle_Arg_ParseStackAndKeywords_SizeT_19(F1, F2, F3, F4, V1, V2, V3, V4, V5, V6, V7, V8, V9, V10, V11, V12, V13, V14, V15, V16, V17, V18, V19) PyTruffle_Arg_ParseStackAndKeywords_SizeT(F1, F2, F3, F4, 19, (void*)(V1), (void*)(V2), (void*)(V3), (void*)(V4), (void*)(V5), (void*)(V6), (void*)(V7), (void*)(V8), (void*)(V9), (void*)(V10), (void*)(V11), (void*)(V12), (void*)(V13), (void*)(V14), (void*)(V15), (void*)(V16), (void*)(V17), (void*)(V18), (void*)(V19), NULL)
#define PyTruffle_Arg_ParseStackAndKeywords_SizeT_20(F1, F2, F3, F4, V1, V2, V3, V4, V5, V6, V7, V8, V9, V10, V11, V12, V13, V14, V15, V16, V17, V18, V19, V20) PyTruffle_Arg_ParseStackAndKeywords_SizeT(F1, F2, F3, F4, 20, (void*)(V1), (void*)(V2), (void*)(V3), (void*)(V4), (void*)(V5), (void*)(V6), (void*)(V7), (void*)(V8), (void*)(V9), (void*)(V10), (void*)(V11), (void*)(V12), (void*)(V13), (void*)(V14), (void*)(V15), (void*)(V16), (void*)(V17), (void*)(V18), (void*)(V19), (void*)(V20))
#define ARG_PARSE__PyArg_ParseTupleAndKeywords_SizeT_IMPL(_0, _1, _2, _3, _4, _5, _6, _7, _8, _9, _10, _11, _12, _13, _14, _15, _16, _17, _18, _19, NAME, ...) NAME
#define _PyArg_ParseTupleAndKeywords_SizeT(F1, F2, F3, F4, ...) ARG_PARSE__PyArg_ParseTupleAndKeywords_SizeT_IMPL(__VA_ARGS__, PyTruffle_Arg_ParseStackAndKeywords_SizeT_20, PyTruffle_Arg_ParseStackAndKeywords_SizeT_19, PyTruffle_Arg_ParseStackAndKeywords_SizeT_18, PyTruffle_Arg_ParseStackAndKeywords_SizeT_17, PyTruffle_Arg_ParseStackAndKeywords_SizeT_16, PyTruffle_Arg_ParseStackAndKeywords_SizeT_15, PyTruffle_Arg_ParseStackAndKeywords_SizeT_14, PyTruffle_Arg_ParseStackAndKeywords_SizeT_13, PyTruffle_Arg_ParseStackAndKeywords_SizeT_12, PyTruffle_Arg_ParseStackAndKeywords_SizeT_11, PyTruffle_Arg_ParseStackAndKeywords_SizeT_10, PyTruffle_Arg_ParseStackAndKeywords_SizeT_9, PyTruffle_Arg_ParseStackAndKeywords_SizeT_8, PyTruffle_Arg_ParseStackAndKeywords_SizeT_7, PyTruffle_Arg_ParseStackAndKeywords_SizeT_6, PyTruffle_Arg_ParseStackAndKeywords_SizeT_5, PyTruffle_Arg_ParseStackAndKeywords_SizeT_4, PyTruffle_Arg_ParseStackAndKeywords_SizeT_3, PyTruffle_Arg_ParseStackAndKeywords_SizeT_2, PyTruffle_Arg_ParseStackAndKeywords_SizeT_1, PyTruffle_Arg_ParseStackAndKeywords_SizeT_0)(F1, F2, F3, F4, ##__VA_ARGS__)

extern int PyTruffle_UnpackStack(PyObject *const *args,  Py_ssize_t nargs,  const char *name,  Py_ssize_t min,  Py_ssize_t max, int s, void* v0, void* v1, void* v2, void* v3, void* v4, void* v5, void* v6, void* v7, void* v8, void* v9, void* v10, void* v11, void* v12, void* v13, void* v14, void* v15, void* v16, void* v17, void* v18, void* v19);
#define PyTruffle_UnpackStack_0(F1, F2, F3, F4, F5) PyTruffle_UnpackStack(F1, F2, F3, F4, F5, 0, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL)
#define PyTruffle_UnpackStack_1(F1, F2, F3, F4, F5, V1) PyTruffle_UnpackStack(F1, F2, F3, F4, F5, 1, (void*)(V1), NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL)
#define PyTruffle_UnpackStack_2(F1, F2, F3, F4, F5, V1, V2) PyTruffle_UnpackStack(F1, F2, F3, F4, F5, 2, (void*)(V1), (void*)(V2), NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL)
#define PyTruffle_UnpackStack_3(F1, F2, F3, F4, F5, V1, V2, V3) PyTruffle_UnpackStack(F1, F2, F3, F4, F5, 3, (void*)(V1), (void*)(V2), (void*)(V3), NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL)
#define PyTruffle_UnpackStack_4(F1, F2, F3, F4, F5, V1, V2, V3, V4) PyTruffle_UnpackStack(F1, F2, F3, F4, F5, 4, (void*)(V1), (void*)(V2), (void*)(V3), (void*)(V4), NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL)
#define PyTruffle_UnpackStack_5(F1, F2, F3, F4, F5, V1, V2, V3, V4, V5) PyTruffle_UnpackStack(F1, F2, F3, F4, F5, 5, (void*)(V1), (void*)(V2), (void*)(V3), (void*)(V4), (void*)(V5), NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL)
#define PyTruffle_UnpackStack_6(F1, F2, F3, F4, F5, V1, V2, V3, V4, V5, V6) PyTruffle_UnpackStack(F1, F2, F3, F4, F5, 6, (void*)(V1), (void*)(V2), (void*)(V3), (void*)(V4), (void*)(V5), (void*)(V6), NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL)
#define PyTruffle_UnpackStack_7(F1, F2, F3, F4, F5, V1, V2, V3, V4, V5, V6, V7) PyTruffle_UnpackStack(F1, F2, F3, F4, F5, 7, (void*)(V1), (void*)(V2), (void*)(V3), (void*)(V4), (void*)(V5), (void*)(V6), (void*)(V7), NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL)
#define PyTruffle_UnpackStack_8(F1, F2, F3, F4, F5, V1, V2, V3, V4, V5, V6, V7, V8) PyTruffle_UnpackStack(F1, F2, F3, F4, F5, 8, (void*)(V1), (void*)(V2), (void*)(V3), (void*)(V4), (void*)(V5), (void*)(V6), (void*)(V7), (void*)(V8), NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL)
#define PyTruffle_UnpackStack_9(F1, F2, F3, F4, F5, V1, V2, V3, V4, V5, V6, V7, V8, V9) PyTruffle_UnpackStack(F1, F2, F3, F4, F5, 9, (void*)(V1), (void*)(V2), (void*)(V3), (void*)(V4), (void*)(V5), (void*)(V6), (void*)(V7), (void*)(V8), (void*)(V9), NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL)
#define PyTruffle_UnpackStack_10(F1, F2, F3, F4, F5, V1, V2, V3, V4, V5, V6, V7, V8, V9, V10) PyTruffle_UnpackStack(F1, F2, F3, F4, F5, 10, (void*)(V1), (void*)(V2), (void*)(V3), (void*)(V4), (void*)(V5), (void*)(V6), (void*)(V7), (void*)(V8), (void*)(V9), (void*)(V10), NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL)
#define PyTruffle_UnpackStack_11(F1, F2, F3, F4, F5, V1, V2, V3, V4, V5, V6, V7, V8, V9, V10, V11) PyTruffle_UnpackStack(F1, F2, F3, F4, F5, 11, (void*)(V1), (void*)(V2), (void*)(V3), (void*)(V4), (void*)(V5), (void*)(V6), (void*)(V7), (void*)(V8), (void*)(V9), (void*)(V10), (void*)(V11), NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL)
#define PyTruffle_UnpackStack_12(F1, F2, F3, F4, F5, V1, V2, V3, V4, V5, V6, V7, V8, V9, V10, V11, V12) PyTruffle_UnpackStack(F1, F2, F3, F4, F5, 12, (void*)(V1), (void*)(V2), (void*)(V3), (void*)(V4), (void*)(V5), (void*)(V6), (void*)(V7), (void*)(V8), (void*)(V9), (void*)(V10), (void*)(V11), (void*)(V12), NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL)
#define PyTruffle_UnpackStack_13(F1, F2, F3, F4, F5, V1, V2, V3, V4, V5, V6, V7, V8, V9, V10, V11, V12, V13) PyTruffle_UnpackStack(F1, F2, F3, F4, F5, 13, (void*)(V1), (void*)(V2), (void*)(V3), (void*)(V4), (void*)(V5), (void*)(V6), (void*)(V7), (void*)(V8), (void*)(V9), (void*)(V10), (void*)(V11), (void*)(V12), (void*)(V13), NULL, NULL, NULL, NULL, NULL, NULL, NULL)
#define PyTruffle_UnpackStack_14(F1, F2, F3, F4, F5, V1, V2, V3, V4, V5, V6, V7, V8, V9, V10, V11, V12, V13, V14) PyTruffle_UnpackStack(F1, F2, F3, F4, F5, 14, (void*)(V1), (void*)(V2), (void*)(V3), (void*)(V4), (void*)(V5), (void*)(V6), (void*)(V7), (void*)(V8), (void*)(V9), (void*)(V10), (void*)(V11), (void*)(V12), (void*)(V13), (void*)(V14), NULL, NULL, NULL, NULL, NULL, NULL)
#define PyTruffle_UnpackStack_15(F1, F2, F3, F4, F5, V1, V2, V3, V4, V5, V6, V7, V8, V9, V10, V11, V12, V13, V14, V15) PyTruffle_UnpackStack(F1, F2, F3, F4, F5, 15, (void*)(V1), (void*)(V2), (void*)(V3), (void*)(V4), (void*)(V5), (void*)(V6), (void*)(V7), (void*)(V8), (void*)(V9), (void*)(V10), (void*)(V11), (void*)(V12), (void*)(V13), (void*)(V14), (void*)(V15), NULL, NULL, NULL, NULL, NULL)
#define PyTruffle_UnpackStack_16(F1, F2, F3, F4, F5, V1, V2, V3, V4, V5, V6, V7, V8, V9, V10, V11, V12, V13, V14, V15, V16) PyTruffle_UnpackStack(F1, F2, F3, F4, F5, 16, (void*)(V1), (void*)(V2), (void*)(V3), (void*)(V4), (void*)(V5), (void*)(V6), (void*)(V7), (void*)(V8), (void*)(V9), (void*)(V10), (void*)(V11), (void*)(V12), (void*)(V13), (void*)(V14), (void*)(V15), (void*)(V16), NULL, NULL, NULL, NULL)
#define PyTruffle_UnpackStack_17(F1, F2, F3, F4, F5, V1, V2, V3, V4, V5, V6, V7, V8, V9, V10, V11, V12, V13, V14, V15, V16, V17) PyTruffle_UnpackStack(F1, F2, F3, F4, F5, 17, (void*)(V1), (void*)(V2), (void*)(V3), (void*)(V4), (void*)(V5), (void*)(V6), (void*)(V7), (void*)(V8), (void*)(V9), (void*)(V10), (void*)(V11), (void*)(V12), (void*)(V13), (void*)(V14), (void*)(V15), (void*)(V16), (void*)(V17), NULL, NULL, NULL)
#define PyTruffle_UnpackStack_18(F1, F2, F3, F4, F5, V1, V2, V3, V4, V5, V6, V7, V8, V9, V10, V11, V12, V13, V14, V15, V16, V17, V18) PyTruffle_UnpackStack(F1, F2, F3, F4, F5, 18, (void*)(V1), (void*)(V2), (void*)(V3), (void*)(V4), (void*)(V5), (void*)(V6), (void*)(V7), (void*)(V8), (void*)(V9), (void*)(V10), (void*)(V11), (void*)(V12), (void*)(V13), (void*)(V14), (void*)(V15), (void*)(V16), (void*)(V17), (void*)(V18), NULL, NULL)
#define PyTruffle_UnpackStack_19(F1, F2, F3, F4, F5, V1, V2, V3, V4, V5, V6, V7, V8, V9, V10, V11, V12, V13, V14, V15, V16, V17, V18, V19) PyTruffle_UnpackStack(F1, F2, F3, F4, F5, 19, (void*)(V1), (void*)(V2), (void*)(V3), (void*)(V4), (void*)(V5), (void*)(V6), (void*)(V7), (void*)(V8), (void*)(V9), (void*)(V10), (void*)(V11), (void*)(V12), (void*)(V13), (void*)(V14), (void*)(V15), (void*)(V16), (void*)(V17), (void*)(V18), (void*)(V19), NULL)
#define PyTruffle_UnpackStack_20(F1, F2, F3, F4, F5, V1, V2, V3, V4, V5, V6, V7, V8, V9, V10, V11, V12, V13, V14, V15, V16, V17, V18, V19, V20) PyTruffle_UnpackStack(F1, F2, F3, F4, F5, 20, (void*)(V1), (void*)(V2), (void*)(V3), (void*)(V4), (void*)(V5), (void*)(V6), (void*)(V7), (void*)(V8), (void*)(V9), (void*)(V10), (void*)(V11), (void*)(V12), (void*)(V13), (void*)(V14), (void*)(V15), (void*)(V16), (void*)(V17), (void*)(V18), (void*)(V19), (void*)(V20))
#define ARG_PARSE__PyArg_UnpackStack_IMPL(_0, _1, _2, _3, _4, _5, _6, _7, _8, _9, _10, _11, _12, _13, _14, _15, _16, _17, _18, _19, NAME, ...) NAME
#define _PyArg_UnpackStack(F1, F2, F3, F4, F5, ...) ARG_PARSE__PyArg_UnpackStack_IMPL(__VA_ARGS__, PyTruffle_UnpackStack_20, PyTruffle_UnpackStack_19, PyTruffle_UnpackStack_18, PyTruffle_UnpackStack_17, PyTruffle_UnpackStack_16, PyTruffle_UnpackStack_15, PyTruffle_UnpackStack_14, PyTruffle_UnpackStack_13, PyTruffle_UnpackStack_12, PyTruffle_UnpackStack_11, PyTruffle_UnpackStack_10, PyTruffle_UnpackStack_9, PyTruffle_UnpackStack_8, PyTruffle_UnpackStack_7, PyTruffle_UnpackStack_6, PyTruffle_UnpackStack_5, PyTruffle_UnpackStack_4, PyTruffle_UnpackStack_3, PyTruffle_UnpackStack_2, PyTruffle_UnpackStack_1, PyTruffle_UnpackStack_0)(F1, F2, F3, F4, F5, ##__VA_ARGS__)

extern int PyTruffle_Arg_UnpackTuple(PyObject *args,  const char *name,  Py_ssize_t min,  Py_ssize_t max, int s, void* v0, void* v1, void* v2, void* v3, void* v4, void* v5, void* v6, void* v7, void* v8, void* v9, void* v10, void* v11, void* v12, void* v13, void* v14, void* v15, void* v16, void* v17, void* v18, void* v19);
#define PyTruffle_Arg_UnpackTuple_0(F1, F2, F3, F4) PyTruffle_Arg_UnpackTuple(F1, F2, F3, F4, 0, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL)
#define PyTruffle_Arg_UnpackTuple_1(F1, F2, F3, F4, V1) PyTruffle_Arg_UnpackTuple(F1, F2, F3, F4, 1, (void*)(V1), NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL)
#define PyTruffle_Arg_UnpackTuple_2(F1, F2, F3, F4, V1, V2) PyTruffle_Arg_UnpackTuple(F1, F2, F3, F4, 2, (void*)(V1), (void*)(V2), NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL)
#define PyTruffle_Arg_UnpackTuple_3(F1, F2, F3, F4, V1, V2, V3) PyTruffle_Arg_UnpackTuple(F1, F2, F3, F4, 3, (void*)(V1), (void*)(V2), (void*)(V3), NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL)
#define PyTruffle_Arg_UnpackTuple_4(F1, F2, F3, F4, V1, V2, V3, V4) PyTruffle_Arg_UnpackTuple(F1, F2, F3, F4, 4, (void*)(V1), (void*)(V2), (void*)(V3), (void*)(V4), NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL)
#define PyTruffle_Arg_UnpackTuple_5(F1, F2, F3, F4, V1, V2, V3, V4, V5) PyTruffle_Arg_UnpackTuple(F1, F2, F3, F4, 5, (void*)(V1), (void*)(V2), (void*)(V3), (void*)(V4), (void*)(V5), NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL)
#define PyTruffle_Arg_UnpackTuple_6(F1, F2, F3, F4, V1, V2, V3, V4, V5, V6) PyTruffle_Arg_UnpackTuple(F1, F2, F3, F4, 6, (void*)(V1), (void*)(V2), (void*)(V3), (void*)(V4), (void*)(V5), (void*)(V6), NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL)
#define PyTruffle_Arg_UnpackTuple_7(F1, F2, F3, F4, V1, V2, V3, V4, V5, V6, V7) PyTruffle_Arg_UnpackTuple(F1, F2, F3, F4, 7, (void*)(V1), (void*)(V2), (void*)(V3), (void*)(V4), (void*)(V5), (void*)(V6), (void*)(V7), NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL)
#define PyTruffle_Arg_UnpackTuple_8(F1, F2, F3, F4, V1, V2, V3, V4, V5, V6, V7, V8) PyTruffle_Arg_UnpackTuple(F1, F2, F3, F4, 8, (void*)(V1), (void*)(V2), (void*)(V3), (void*)(V4), (void*)(V5), (void*)(V6), (void*)(V7), (void*)(V8), NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL)
#define PyTruffle_Arg_UnpackTuple_9(F1, F2, F3, F4, V1, V2, V3, V4, V5, V6, V7, V8, V9) PyTruffle_Arg_UnpackTuple(F1, F2, F3, F4, 9, (void*)(V1), (void*)(V2), (void*)(V3), (void*)(V4), (void*)(V5), (void*)(V6), (void*)(V7), (void*)(V8), (void*)(V9), NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL)
#define PyTruffle_Arg_UnpackTuple_10(F1, F2, F3, F4, V1, V2, V3, V4, V5, V6, V7, V8, V9, V10) PyTruffle_Arg_UnpackTuple(F1, F2, F3, F4, 10, (void*)(V1), (void*)(V2), (void*)(V3), (void*)(V4), (void*)(V5), (void*)(V6), (void*)(V7), (void*)(V8), (void*)(V9), (void*)(V10), NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL)
#define PyTruffle_Arg_UnpackTuple_11(F1, F2, F3, F4, V1, V2, V3, V4, V5, V6, V7, V8, V9, V10, V11) PyTruffle_Arg_UnpackTuple(F1, F2, F3, F4, 11, (void*)(V1), (void*)(V2), (void*)(V3), (void*)(V4), (void*)(V5), (void*)(V6), (void*)(V7), (void*)(V8), (void*)(V9), (void*)(V10), (void*)(V11), NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL)
#define PyTruffle_Arg_UnpackTuple_12(F1, F2, F3, F4, V1, V2, V3, V4, V5, V6, V7, V8, V9, V10, V11, V12) PyTruffle_Arg_UnpackTuple(F1, F2, F3, F4, 12, (void*)(V1), (void*)(V2), (void*)(V3), (void*)(V4), (void*)(V5), (void*)(V6), (void*)(V7), (void*)(V8), (void*)(V9), (void*)(V10), (void*)(V11), (void*)(V12), NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL)
#define PyTruffle_Arg_UnpackTuple_13(F1, F2, F3, F4, V1, V2, V3, V4, V5, V6, V7, V8, V9, V10, V11, V12, V13) PyTruffle_Arg_UnpackTuple(F1, F2, F3, F4, 13, (void*)(V1), (void*)(V2), (void*)(V3), (void*)(V4), (void*)(V5), (void*)(V6), (void*)(V7), (void*)(V8), (void*)(V9), (void*)(V10), (void*)(V11), (void*)(V12), (void*)(V13), NULL, NULL, NULL, NULL, NULL, NULL, NULL)
#define PyTruffle_Arg_UnpackTuple_14(F1, F2, F3, F4, V1, V2, V3, V4, V5, V6, V7, V8, V9, V10, V11, V12, V13, V14) PyTruffle_Arg_UnpackTuple(F1, F2, F3, F4, 14, (void*)(V1), (void*)(V2), (void*)(V3), (void*)(V4), (void*)(V5), (void*)(V6), (void*)(V7), (void*)(V8), (void*)(V9), (void*)(V10), (void*)(V11), (void*)(V12), (void*)(V13), (void*)(V14), NULL, NULL, NULL, NULL, NULL, NULL)
#define PyTruffle_Arg_UnpackTuple_15(F1, F2, F3, F4, V1, V2, V3, V4, V5, V6, V7, V8, V9, V10, V11, V12, V13, V14, V15) PyTruffle_Arg_UnpackTuple(F1, F2, F3, F4, 15, (void*)(V1), (void*)(V2), (void*)(V3), (void*)(V4), (void*)(V5), (void*)(V6), (void*)(V7), (void*)(V8), (void*)(V9), (void*)(V10), (void*)(V11), (void*)(V12), (void*)(V13), (void*)(V14), (void*)(V15), NULL, NULL, NULL, NULL, NULL)
#define PyTruffle_Arg_UnpackTuple_16(F1, F2, F3, F4, V1, V2, V3, V4, V5, V6, V7, V8, V9, V10, V11, V12, V13, V14, V15, V16) PyTruffle_Arg_UnpackTuple(F1, F2, F3, F4, 16, (void*)(V1), (void*)(V2), (void*)(V3), (void*)(V4), (void*)(V5), (void*)(V6), (void*)(V7), (void*)(V8), (void*)(V9), (void*)(V10), (void*)(V11), (void*)(V12), (void*)(V13), (void*)(V14), (void*)(V15), (void*)(V16), NULL, NULL, NULL, NULL)
#define PyTruffle_Arg_UnpackTuple_17(F1, F2, F3, F4, V1, V2, V3, V4, V5, V6, V7, V8, V9, V10, V11, V12, V13, V14, V15, V16, V17) PyTruffle_Arg_UnpackTuple(F1, F2, F3, F4, 17, (void*)(V1), (void*)(V2), (void*)(V3), (void*)(V4), (void*)(V5), (void*)(V6), (void*)(V7), (void*)(V8), (void*)(V9), (void*)(V10), (void*)(V11), (void*)(V12), (void*)(V13), (void*)(V14), (void*)(V15), (void*)(V16), (void*)(V17), NULL, NULL, NULL)
#define PyTruffle_Arg_UnpackTuple_18(F1, F2, F3, F4, V1, V2, V3, V4, V5, V6, V7, V8, V9, V10, V11, V12, V13, V14, V15, V16, V17, V18) PyTruffle_Arg_UnpackTuple(F1, F2, F3, F4, 18, (void*)(V1), (void*)(V2), (void*)(V3), (void*)(V4), (void*)(V5), (void*)(V6), (void*)(V7), (void*)(V8), (void*)(V9), (void*)(V10), (void*)(V11), (void*)(V12), (void*)(V13), (void*)(V14), (void*)(V15), (void*)(V16), (void*)(V17), (void*)(V18), NULL, NULL)
#define PyTruffle_Arg_UnpackTuple_19(F1, F2, F3, F4, V1, V2, V3, V4, V5, V6, V7, V8, V9, V10, V11, V12, V13, V14, V15, V16, V17, V18, V19) PyTruffle_Arg_UnpackTuple(F1, F2, F3, F4, 19, (void*)(V1), (void*)(V2), (void*)(V3), (void*)(V4), (void*)(V5), (void*)(V6), (void*)(V7), (void*)(V8), (void*)(V9), (void*)(V10), (void*)(V11), (void*)(V12), (void*)(V13), (void*)(V14), (void*)(V15), (void*)(V16), (void*)(V17), (void*)(V18), (void*)(V19), NULL)
#define PyTruffle_Arg_UnpackTuple_20(F1, F2, F3, F4, V1, V2, V3, V4, V5, V6, V7, V8, V9, V10, V11, V12, V13, V14, V15, V16, V17, V18, V19, V20) PyTruffle_Arg_UnpackTuple(F1, F2, F3, F4, 20, (void*)(V1), (void*)(V2), (void*)(V3), (void*)(V4), (void*)(V5), (void*)(V6), (void*)(V7), (void*)(V8), (void*)(V9), (void*)(V10), (void*)(V11), (void*)(V12), (void*)(V13), (void*)(V14), (void*)(V15), (void*)(V16), (void*)(V17), (void*)(V18), (void*)(V19), (void*)(V20))
#define ARG_PARSE_PyArg_UnpackTuple_IMPL(_0, _1, _2, _3, _4, _5, _6, _7, _8, _9, _10, _11, _12, _13, _14, _15, _16, _17, _18, _19, NAME, ...) NAME
#define PyArg_UnpackTuple(F1, F2, F3, F4, ...) ARG_PARSE_PyArg_UnpackTuple_IMPL(__VA_ARGS__, PyTruffle_Arg_UnpackTuple_20, PyTruffle_Arg_UnpackTuple_19, PyTruffle_Arg_UnpackTuple_18, PyTruffle_Arg_UnpackTuple_17, PyTruffle_Arg_UnpackTuple_16, PyTruffle_Arg_UnpackTuple_15, PyTruffle_Arg_UnpackTuple_14, PyTruffle_Arg_UnpackTuple_13, PyTruffle_Arg_UnpackTuple_12, PyTruffle_Arg_UnpackTuple_11, PyTruffle_Arg_UnpackTuple_10, PyTruffle_Arg_UnpackTuple_9, PyTruffle_Arg_UnpackTuple_8, PyTruffle_Arg_UnpackTuple_7, PyTruffle_Arg_UnpackTuple_6, PyTruffle_Arg_UnpackTuple_5, PyTruffle_Arg_UnpackTuple_4, PyTruffle_Arg_UnpackTuple_3, PyTruffle_Arg_UnpackTuple_2, PyTruffle_Arg_UnpackTuple_1, PyTruffle_Arg_UnpackTuple_0)(F1, F2, F3, F4, ##__VA_ARGS__)

#endif
