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
#ifndef Py_PYCONFIG_H
#define Py_PYCONFIG_H

// required for __UINT32_MAX__ etc.
#include <limits.h>

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
#define _Py_BEGIN_SUPPRESS_IPH
#define _Py_END_SUPPRESS_IPH
// END TRUFFLE DEFS

#define HAVE_ACOSH 1
#define HAVE_ASINH 1
#define HAVE_ATANH 1
#define HAVE_CLOCK 1
#define HAVE_DIRENT_H 1
#define HAVE_SENDFILE 1

#define HAVE_STDARG_PROTOTYPES

#define HAVE_WCHAR_H 1

#define WITH_THREAD 1

#endif /*Py_PYCONFIG_H*/

