/* Copyright (c) 2022, 2024, Oracle and/or its affiliates.
 * Copyright (C) 1996-2022 Python Software Foundation
 *
 * Licensed under the PYTHON SOFTWARE FOUNDATION LICENSE VERSION 2
 */
// Entry point of the Python C API.
// C extensions should only #include <Python.h>, and not include directly
// the other Python header files included by <Python.h>.

#ifndef Py_PYTHON_H
#define Py_PYTHON_H

// Since this is a "meta-include" file, no #ifdef __cplusplus / extern "C" {

// Include Python header files
#include "patchlevel.h"

// can be used to bulk-rename fields, e.g., by concatenating "_internal"
#define Py_HIDE_IMPL_FIELD(name) name

#include "pyconfig.h"

#if defined(__sgi) && !defined(_SGI_MP_SOURCE)
#  define _SGI_MP_SOURCE
#endif

// stdlib.h, stdio.h, errno.h and string.h headers are not used by Python
// headers, but kept for backward compatibility. They are excluded from the
// limited C API of Python 3.11.
#if !defined(Py_LIMITED_API) || Py_LIMITED_API+0 < 0x030b0000
#  include <stdlib.h>
#  include <stdio.h>              // FILE*
#  include <errno.h>              // errno
#  include <string.h>             // memcpy()
#endif
#ifndef MS_WINDOWS
#  include <unistd.h>
#else
struct timeval;
#endif
#ifdef HAVE_STDDEF_H
#  include <stddef.h>             // size_t
#endif

#include <assert.h>               // assert()
#include <wchar.h>                // wchar_t

#include "pyport.h"
#include "pymacro.h"
#include "pymath.h"
#include "pymem.h"
#include "pytypedefs.h"
#include "pybuffer.h"
#include "object.h"
#include "objimpl.h"
#include "typeslots.h"
#include "pyhash.h"
#include "cpython/pydebug.h"
#include "bytearrayobject.h"
#include "bytesobject.h"
#include "unicodeobject.h"
#include "cpython/initconfig.h"
#include "pystate.h"
#include "pyerrors.h"
#include "longobject.h"
#include "cpython/longintrepr.h"
#include "boolobject.h"
#include "floatobject.h"
#include "complexobject.h"
#include "rangeobject.h"
#include "memoryobject.h"
#include "tupleobject.h"
#include "listobject.h"
#include "dictobject.h"
#include "setobject.h"
#include "methodobject.h"
#include "moduleobject.h"
#include "cpython/funcobject.h"
#include "cpython/classobject.h"
#include "fileobject.h"
#include "pycapsule.h"
#include "cpython/code.h"
#include "pyframe.h"
#include "traceback.h"
#include "sliceobject.h"
#include "cpython/cellobject.h"
#include "iterobject.h"
#include "cpython/genobject.h"
#include "descrobject.h"
#include "genericaliasobject.h"
#include "warnings.h"
#include "weakrefobject.h"
#include "structseq.h"
#include "cpython/pytime.h"
#include "codecs.h"
#include "pythread.h"
#include "cpython/context.h"
#include "modsupport.h"
#include "compile.h"
#include "pythonrun.h"
#include "pylifecycle.h"
#include "ceval.h"
#include "sysmodule.h"
#include "osmodule.h"
#include "import.h"
#include "abstract.h"
#include "cpython/pyctype.h"
#include "pystrtod.h"
#include "pystrcmp.h"
#include "fileutils.h"
#include "cpython/pyfpe.h"
#include "tracemalloc.h"

// helper macro for quick printf debugging
#define __PD {printf("%i \t%s\n", __LINE__, __func__); }

#endif /* !Py_PYTHON_H */
