/* Copyright (c) 2018, Oracle and/or its affiliates.
 * Copyright (C) 1996-2017 Python Software Foundation
 *
 * Licensed under the PYTHON SOFTWARE FOUNDATION LICENSE VERSION 2
 */
/* pyconfig.h.  Generated from pyconfig.h.in by configure.  */
/* pyconfig.h.in.  Generated from configure.ac by autoheader.  */


#ifndef Py_PYCONFIG_H
#define Py_PYCONFIG_H


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
#define INT_MIN ((-__INT32_MAX__)-1)
#define INT_MAX __INT32_MAX__
#define UINT_MAX __UINT32_MAX__
#define SHRT_MIN ((-__INT16_MAX__)-1)
#define SHRT_MAX __INT16_MAX__
#define USHRT_MAX __UINT16_MAX__
#define CHAR_BIT __CHAR_BIT__
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

#endif /*Py_PYCONFIG_H*/

