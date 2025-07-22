/*
 * Copyright (c) 2017, 2025, Oracle and/or its affiliates. All rights reserved.
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
#ifndef Py_PYCONFIG_H
#define Py_PYCONFIG_H

#define GRAALVM_PYTHON 1
#cmakedefine GRAALPY_VERSION "@GRAALPY_VERSION@"
#cmakedefine GRAALPY_VERSION_NUM @GRAALPY_VERSION_NUM@

// The graalpy build always sets MS_WINDOWS, so when this is not set, we are
// dealing with an extension build. In that case, if we're on Windows, we need
// to set the appropriate flags to link against our python C API dll.
#if !defined(Py_BUILD_CORE) && !defined(Py_BUILD_CORE_MODULE) && !defined(MS_WINDOWS)
# ifdef _MSC_VER
#  define MS_WINDOWS
#  define Py_ENABLE_SHARED
#  define HAVE_DECLSPEC_DLL
// This pragma is only understood by MSVC, not our LLVM toolchain, so it's only
// relevant for code that is compiled without bitcode and will run only
// natively. Since the pythonjni library contains all the trampolines to call
// into the python-native.dll in this case, we must only depend on that.
#  pragma comment(lib, "python312.lib")
# endif
#endif

/* If Cython is involved, avoid accesses to internal structures. While we are
 * supporting this in many cases, it still involves overhead. */
#define CYTHON_USE_TYPE_SLOTS 0
#define CYTHON_USE_PYTYPE_LOOKUP 0
#define CYTHON_UNPACK_METHODS 0
#define CYTHON_FAST_PYCALL 0
#define CYTHON_FAST_PYCCALL 0
#define CYTHON_USE_DICT_VERSIONS 0
#define CYTHON_AVOID_BORROWED_REFS 1
#define CYTHON_USE_TP_FINALIZE 0
#define CYTHON_USE_PYLIST_INTERNALS 0
#define CYTHON_USE_UNICODE_INTERNALS 0
#define CYTHON_USE_PYLONG_INTERNALS 0
#define CYTHON_USE_ASYNC_SLOTS 1
#define CYTHON_USE_UNICODE_WRITER 0
#define CYTHON_USE_EXC_INFO_STACK 0
#define CYTHON_FAST_THREAD_STATE 0
#define CYTHON_PROFILE 0
#define CYTHON_TRACE 0
#define CYTHON_UPDATE_DESCRIPTOR_DOC 0
// This s a workaround for a Cython bug that it uses a macro that CPython already removed
#define _Py_DEC_REFTOTAL

/* Enable GNU extensions on systems that have them. */
#ifndef _GNU_SOURCE
#define _GNU_SOURCE 1
#endif

// required for __UINT32_MAX__ etc.
#include <limits.h>

#if defined(_MSC_VER) && !defined(__clang__)
// defines based on MSVC documentation
#define __SIZEOF_INT__ 4
#define __SIZEOF_SHORT__ 2
#define __SIZEOF_LONG__ 4
#define __SIZEOF_LONG_LONG__ 8
#define __SIZEOF_FLOAT__ 4
#define __SIZEOF_DOUBLE__ 8
#define __SIZEOF_LONG_DOUBLE__ 8
#define __SIZEOF_SIZE_T__ 8
#define __SIZEOF_UINTPTR_T__ 8
#define __SIZEOF_POINTER__ 8
#define __SIZEOF_WCHAR_T__ 2
#define pid_t int
#endif

// defines based on Clang defines
#define SIZEOF_DOUBLE __SIZEOF_DOUBLE__
#define SIZEOF_FLOAT __SIZEOF_FLOAT__
#define SIZEOF_FPOS_T __SIZEOF_INT128__
#define SIZEOF_INT __SIZEOF_INT__
#define SIZEOF_LONG __SIZEOF_LONG__
#define SIZEOF_LONG_DOUBLE __SIZEOF_LONG_DOUBLE__
#define SIZEOF_LONG_LONG __SIZEOF_LONG_LONG__
#define SIZEOF_OFF_T __SIZEOF_SIZE_T__
#define SIZEOF_PID_T __SIZEOF_INT__
#define SIZEOF_PTHREAD_T __SIZEOF_LONG__
#define SIZEOF_SHORT __SIZEOF_SHORT__
#define SIZEOF_SIZE_T __SIZEOF_SIZE_T__
#define SIZEOF_TIME_T __SIZEOF_POINTER__
#define SIZEOF_UINTPTR_T __SIZEOF_POINTER__
#define SIZEOF_VOID_P __SIZEOF_POINTER__
#define SIZEOF_WCHAR_T __SIZEOF_WCHAR_T__
#define SIZEOF__BOOL 1

/* Define if your compiler supports function prototype */
#define HAVE_PROTOTYPES 1

/* Define to 1 if you have the ANSI C header files. */
#define STDC_HEADERS 1

# ifndef UINT_MAX
#define UINT_MAX __UINT32_MAX__
#endif
# ifndef SHRT_MIN
#define SHRT_MIN ((-__INT16_MAX__)-1)
#endif
# ifndef SHRT_MAX
#define SHRT_MAX __INT16_MAX__
#endif
# ifndef USHRT_MAX
#define USHRT_MAX __UINT16_MAX__
#endif
# ifndef CHAR_BIT
#define CHAR_BIT __CHAR_BIT__
#endif
// #define Py_LIMITED_API 1
// END TRUFFLE DEFS

#cmakedefine HAVE_ACOSH 1
#cmakedefine HAVE_ASINH 1
#cmakedefine HAVE_ATANH 1
#cmakedefine HAVE_COPYSIGN 1
#cmakedefine HAVE_ROUND 1
#cmakedefine HAVE_HYPOT 1
#cmakedefine HAVE_CLOCK 1
#cmakedefine HAVE_SENDFILE 1
#cmakedefine HAVE_DIRENT_H 1
#cmakedefine HAVE_ERRNO_H 1
#cmakedefine HAVE_UTIME_H 1
#cmakedefine HAVE_SIGNAL_H 1
#cmakedefine HAVE_FCNTL_H 1
#cmakedefine HAVE_WCHAR_H 1
#cmakedefine HAVE_UNISTD_H 1
#cmakedefine HAVE_PTHREAD_H 1
#cmakedefine HAVE_SYS_WAIT_H 1
#cmakedefine HAVE_SYS_TIME_H 1
#cmakedefine HAVE_SYS_STAT_H 1

#cmakedefine TIME_WITH_SYS_TIME 1
#cmakedefine NT_THREADS 1

#define HAVE_STDARG_PROTOTYPES 1
#define WITH_THREAD 1
#define WITH_DOC_STRINGS 1

/* Define if C doubles are 64-bit IEEE 754 binary format, stored with the
   least significant byte first */
#cmakedefine DOUBLE_IS_LITTLE_ENDIAN_IEEE754 1
#cmakedefine DOUBLE_IS_BIG_ENDIAN_IEEE754 1


#endif /*Py_PYCONFIG_H*/

