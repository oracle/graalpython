/*
 * Copyright (c) 2018, 2020, Oracle and/or its affiliates. All rights reserved.
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

#include <stdio.h>

static int getbuffer(PyObject *arg, Py_buffer *view, const char **errmsg) {
    if (PyObject_GetBuffer(arg, view, PyBUF_SIMPLE) != 0) {
        *errmsg = "bytes-like object";
        return -1;
    }
    if (!PyBuffer_IsContiguous(view, 'C')) {
        PyBuffer_Release(view);
        *errmsg = "contiguous buffer";
        return -1;
    }
    return 0;
}

int get_buffer_r(PyObject *arg, Py_buffer *view) {
    if (PyObject_GetBuffer(arg, view, PyBUF_SIMPLE) != 0) {
        return -1;
    }
    if (!PyBuffer_IsContiguous(view, 'C')) {
        PyBuffer_Release(view);
        return -2;
    }
    return 0;
}

int get_buffer_rw(PyObject *arg, Py_buffer *view) {
    if (PyObject_GetBuffer(arg, view, PyBUF_WRITABLE) != 0) {
        return -1;
    }
    if (!PyBuffer_IsContiguous(view, 'C')) {
        PyBuffer_Release(view);
        return -2;
    }
    return 0;
}

typedef union {
	void *ptr;
	float f;
	double d;
	int64_t i64;
	int32_t i32;
	int16_t i16;
	int8_t i8;
	uint64_t u64;
	uint32_t u32;
	uint16_t u16;
	uint8_t u8;
	Py_complex c;
} OutVar;

POLYGLOT_DECLARE_TYPE(OutVar);

typedef struct { OutVar *content; } OutVarPtr;

POLYGLOT_DECLARE_TYPE(OutVarPtr);

typedef char* CharPtr;
POLYGLOT_DECLARE_TYPE(CharPtr);


typedef int (*parseargs_func)(PyObject *argv, PyObject *kwds, const char *format, void* kwdnames, void* varargs);

static parseargs_func PyTruffle_Arg_ParseTupleAndKeywords;

__attribute__((constructor))
static void init_upcall_PyTruffle_Arg_ParseTupleAndKeyword(void) {
	PyTruffle_Arg_ParseTupleAndKeywords = polyglot_get_member(PY_TRUFFLE_CEXT, polyglot_from_string("PyTruffle_Arg_ParseTupleAndKeywords", SRC_CS));
}

#define CallParseTupleAndKeywordsWithPolyglotArgs(__res__, __offset__, __args__, __kwds__, __fmt__, __kwdnames__) \
    int __poly_argc = polyglot_get_arg_count() - (__offset__); \
    void **__poly_args = truffle_managed_malloc(sizeof(void*) * __poly_argc); \
    int __kwdnames_cnt = 0; \
    for (int i = 0; i < __poly_argc; i++) { \
        __poly_args[i] = polyglot_get_arg(i + (__offset__)); \
    } \
    if((__kwdnames__) != NULL){ \
    	for (; (__kwdnames__)[__kwdnames_cnt] != NULL ; __kwdnames_cnt++); \
    } \
    __res__ = PyTruffle_Arg_ParseTupleAndKeywords((__args__), (__kwds__), polyglot_from_string((__fmt__), SRC_CS), polyglot_from_CharPtr_array(__kwdnames__, __kwdnames_cnt), polyglot_from_OutVarPtr_array((OutVarPtr*)__poly_args, __poly_argc));


#define CallParseTupleWithPolyglotArgs(__res__, __offset__, __args__, __fmt__) \
    int __poly_argc = polyglot_get_arg_count() - (__offset__); \
    void **__poly_args = truffle_managed_malloc(sizeof(void*) * __poly_argc); \
    for (int i = 0; i < __poly_argc; i++) { \
        __poly_args[i] = polyglot_get_arg(i + (__offset__)); \
    } \
    __res__ = PyTruffle_Arg_ParseTupleAndKeywords((__args__), NULL, polyglot_from_string((__fmt__), SRC_CS), NULL, polyglot_from_OutVarPtr_array((OutVarPtr*)__poly_args, __poly_argc));


#define CallParseStackWithPolyglotArgs(__res__, __offset__, __args__, __nargs__, __fmt__) \
    int __poly_argc = polyglot_get_arg_count() - (__offset__); \
    void **__poly_args = truffle_managed_malloc(sizeof(void*) * __poly_argc); \
    for (int i = 0; i < __poly_argc; i++) { \
        __poly_args[i] = polyglot_get_arg(i + (__offset__)); \
    } \
    __res__ = PyTruffle_Arg_ParseTupleAndKeywords(polyglot_from_PyObjectPtr_array((__args__), (__nargs__)), NULL, polyglot_from_string((__fmt__), SRC_CS), NULL, polyglot_from_OutVarPtr_array((OutVarPtr*)__poly_args, __poly_argc));


#define PyTruffleVaArgI8(poly_args, offset, va) (poly_args == NULL ? va_arg(va, int8_t) : polyglot_as_i8((poly_args[offset++])))
#define PyTruffleVaArgI32(poly_args, offset, va) (poly_args == NULL ? va_arg(va, int32_t) : polyglot_as_i32((poly_args[offset++])))
#define PyTruffleVaArgI64(poly_args, offset, va, T) (poly_args == NULL ? va_arg(va, T) : ((T)polyglot_as_i64((poly_args[offset++]))))
#define PyTruffleVaArgDouble(poly_args, offset, va) (poly_args == NULL ? va_arg(va, double) : polyglot_as_double((poly_args[offset++])))
#define PyTruffleVaArg(poly_args, offset, va, T) (poly_args == NULL ? va_arg(va, T) : (T)(poly_args[offset++]))

/* argparse */

OutVarPtr* allocate_outvar() {
	return polyglot_from_OutVarPtr(truffle_managed_malloc(sizeof(OutVarPtr)));
}

void get_next_vaarg(va_list *p_va, OutVarPtr *p_outvar) {
	p_outvar->content = (OutVar *)va_arg(*p_va, void *);
}

static parseargs_func PyTruffle_Arg_ParseTupleAndKeywords_VaList;

__attribute__((constructor))
static void init_upcall_PyTruffle_Arg_ParseTupleAndKeyword_VaList(void) {
	PyTruffle_Arg_ParseTupleAndKeywords_VaList = polyglot_get_member(PY_TRUFFLE_CEXT, polyglot_from_string("PyTruffle_Arg_ParseTupleAndKeywords_VaList", SRC_CS));
}

int PyArg_VaParseTupleAndKeywords(PyObject *argv, PyObject *kwds, const char *format, char **kwdnames, va_list va) {
	va_list lva;
	va_copy(lva, va);
    int __kwdnames_cnt = 0;
    int res = 0;
    if(kwdnames != NULL) {
    	for (; kwdnames[__kwdnames_cnt] != NULL ; __kwdnames_cnt++);
    }
    res =PyTruffle_Arg_ParseTupleAndKeywords_VaList(native_to_java_slim(argv), native_to_java_slim(kwds), polyglot_from_string(format, SRC_CS), polyglot_from_CharPtr_array(kwdnames, __kwdnames_cnt), &lva);
    va_end(lva);
    return res;
}

int _PyArg_VaParseTupleAndKeywords_SizeT(PyObject *argv, PyObject *kwds, const char *format, char **kwdnames, va_list va) {
	va_list lva;
	va_copy(lva, va);
    int __kwdnames_cnt = 0;
    int res = 0;
    if(kwdnames != NULL) {
    	for (; kwdnames[__kwdnames_cnt] != NULL ; __kwdnames_cnt++);
    }
    res =PyTruffle_Arg_ParseTupleAndKeywords_VaList(native_to_java_slim(argv), native_to_java_slim(kwds), polyglot_from_string(format, SRC_CS), polyglot_from_CharPtr_array(kwdnames, __kwdnames_cnt), &lva);
    va_end(lva);
    return res;
}

NO_INLINE
int PyArg_ParseTupleAndKeywords(PyObject *argv, PyObject *kwds, const char *format, char** kwdnames, ...) {
	CallParseTupleAndKeywordsWithPolyglotArgs(int result, 4, native_to_java_slim(argv), native_to_java_slim(kwds), format, kwdnames);
    return result;
}

NO_INLINE
int _PyArg_ParseTupleAndKeywords_SizeT(PyObject *argv, PyObject *kwds, const char *format, char** kwdnames, ...) {
	CallParseTupleAndKeywordsWithPolyglotArgs(int result, 4, native_to_java_slim(argv), native_to_java_slim(kwds), format, kwdnames);
    return result;
}

NO_INLINE
int PyArg_ParseStack(PyObject **args, Py_ssize_t nargs, const char* format, ...) {
	CallParseStackWithPolyglotArgs(int result, 3, args, nargs, format);
    return result;
}

NO_INLINE
int _PyArg_ParseStack_SizeT(PyObject **args, Py_ssize_t nargs, const char* format, ...) {
	CallParseStackWithPolyglotArgs(int result, 3, args, nargs, format);
    return result;
}

int _PyArg_VaParseTupleAndKeywordsFast(PyObject *args, PyObject *kwargs, struct _PyArg_Parser *parser, va_list va) {
    return _PyArg_VaParseTupleAndKeywords_SizeT(args, kwargs, parser->format, parser->keywords, va);
}

int _PyArg_VaParseTupleAndKeywordsFast_SizeT(PyObject *args, PyObject *kwargs, struct _PyArg_Parser *parser, va_list va) {
    return _PyArg_VaParseTupleAndKeywords_SizeT(args, kwargs, parser->format, parser->keywords, va);
}

NO_INLINE
int _PyArg_ParseTupleAndKeywordsFast(PyObject *args, PyObject *kwargs, struct _PyArg_Parser *parser, ...) {
	CallParseTupleAndKeywordsWithPolyglotArgs(int result, 3, native_to_java_slim(args), native_to_java_slim(kwargs), parser->format, parser->keywords);
    return result;
}

NO_INLINE
int _PyArg_ParseTupleAndKeywordsFast_SizeT(PyObject *args, PyObject *kwargs, struct _PyArg_Parser *parser, ...) {
	CallParseTupleAndKeywordsWithPolyglotArgs(int result, 3, native_to_java_slim(args), native_to_java_slim(kwargs), parser->format, parser->keywords);
    return result;
}


NO_INLINE
int PyArg_ParseTuple(PyObject *args, const char *format, ...) {
	CallParseTupleWithPolyglotArgs(int result, 2, native_to_java_slim(args), format);
	return result;
}

NO_INLINE
int _PyArg_ParseTuple_SizeT(PyObject *args, const char *format, ...) {
	CallParseTupleWithPolyglotArgs(int result, 2, native_to_java_slim(args), format);
	return result;
}

int PyArg_VaParse(PyObject *args, const char *format, va_list va) {
    return _PyArg_VaParseTupleAndKeywords_SizeT(PyTuple_Pack(1, args), NULL, format, NULL, va);
}

int _PyArg_VaParse_SizeT(PyObject *args, const char *format, va_list va) {
    return _PyArg_VaParseTupleAndKeywords_SizeT(PyTuple_Pack(1, args), NULL, format, NULL, va);
}

NO_INLINE
int PyArg_Parse(PyObject *args, const char *format, ...) {
	CallParseTupleWithPolyglotArgs(int result, 2, native_to_java_slim(PyTuple_Pack(1, args)), format);
	return result;
}

NO_INLINE
int _PyArg_Parse_SizeT(PyObject *args, const char *format, ...) {
	CallParseTupleWithPolyglotArgs(int result, 2, native_to_java_slim(PyTuple_Pack(1, args)), format);
    return result;
}

typedef struct _build_stack {
    PyObject* list;
    struct _build_stack* prev;
} build_stack;

MUST_INLINE static PyObject* _PyTruffle_BuildValue(const char* format, va_list va, void** poly_args, int argc) {
    PyObject* (*converter)(void*) = NULL;
    int offset = 0;
    char argchar[2] = {'\0'};
    unsigned int format_idx = 0;
    build_stack *v = (build_stack*)calloc(1, sizeof(build_stack));
    build_stack *next;
    v->list = PyList_New(0);

    char *char_arg;
    void *void_arg;

    char c = format[format_idx];
    while (c != '\0') {
        PyObject* list = v->list;

        switch(c) {
        case 's':
        case 'z':
        case 'U':
            char_arg = PyTruffleVaArg(poly_args, offset, va, char*);
            if (format[format_idx + 1] == '#') {
                int size = PyTruffleVaArgI64(poly_args, offset, va, int);
                if (char_arg == NULL) {
                    PyList_Append(list, Py_None);
                } else {
                    PyList_Append(list, PyUnicode_FromStringAndSize(char_arg, size));
                }
                format_idx++;
            } else {
                if (char_arg == NULL) {
                    PyList_Append(list, Py_None);
                } else {
                    PyList_Append(list, PyUnicode_FromString(char_arg));
                }
            }
            break;
        case 'y':
            char_arg = PyTruffleVaArg(poly_args, offset, va, char*);
            if (format[format_idx + 1] == '#') {
                int size = PyTruffleVaArgI64(poly_args, offset, va, int);
                if (char_arg == NULL) {
                    PyList_Append(list, Py_None);
                } else {
                    PyList_Append(list, PyBytes_FromStringAndSize(char_arg, size));
                }
                format_idx++;
            } else {
                if (char_arg == NULL) {
                    PyList_Append(list, Py_None);
                } else {
                    PyList_Append(list, PyBytes_FromString(char_arg));
                }
            }
            break;
        case 'u':
            fprintf(stderr, "error: unsupported format 'u'\n");
            break;
        case 'i':
        case 'b':
        case 'h':
            PyList_Append(list, PyLong_FromLong(PyTruffleVaArgI64(poly_args, offset, va, int)));
            break;
        case 'l':
            PyList_Append(list, PyLong_FromLong(PyTruffleVaArgI64(poly_args, offset, va, long)));
            break;
        case 'B':
        case 'H':
        case 'I':
            PyList_Append(list, PyLong_FromUnsignedLong(PyTruffleVaArgI64(poly_args, offset, va, unsigned int)));
            break;
        case 'k':
            PyList_Append(list, PyLong_FromUnsignedLong(PyTruffleVaArgI64(poly_args, offset, va, unsigned long)));
            break;
        case 'L':
            PyList_Append(list, PyLong_FromLongLong(PyTruffleVaArgI64(poly_args, offset, va, long long)));
            break;
        case 'K':
            PyList_Append(list, PyLong_FromUnsignedLongLong(PyTruffleVaArgI64(poly_args, offset, va, unsigned long long)));
            break;
        case 'n':
            PyList_Append(list, PyLong_FromSsize_t(PyTruffleVaArgI64(poly_args, offset, va, Py_ssize_t)));
            break;
        case 'c':
            // note: a vararg char is promoted to int according to the C standard
            argchar[0] = PyTruffleVaArgI64(poly_args, offset, va, int);
            PyList_Append(list, PyBytes_FromStringAndSize(argchar, 1));
            break;
        case 'C':
            // note: a vararg char is promoted to int according to the C standard
            argchar[0] = PyTruffleVaArgI64(poly_args, offset, va, int);
            PyList_Append(list, polyglot_from_string(argchar, "ascii"));
            break;
        case 'd':
        case 'f':
            PyList_Append(list, PyFloat_FromDouble(PyTruffleVaArgDouble(poly_args, offset, va)));
            break;
        case 'D':
            fprintf(stderr, "error: unsupported format 'D'\n");
            break;
        case 'O':
        case 'S':
        case 'N':
            void_arg = PyTruffleVaArg(poly_args, offset, va, void*);
            if (c == 'O') {
                if (format[format_idx + 1] == '&') {
                    converter = PyTruffleVaArg(poly_args, offset, va, void*);
                }
            }

            if (void_arg == NULL) {
                if (!PyErr_Occurred()) {
                    /* If a NULL was passed because a call that should have constructed a value failed, that's OK,
                     * and we pass the error on; but if no error occurred it's not clear that the caller knew what she was doing. */
                    PyErr_SetString(PyExc_SystemError, "NULL object passed to Py_BuildValue");
                }
                return NULL;
            } else if (converter != NULL) {
                PyList_Append(list, converter(void_arg));
                converter = NULL;
                format_idx++;
            } else {
                if (c != 'N') {
                    Py_INCREF((PyObject*)void_arg);
                }
                PyList_Append(list, (PyObject*)void_arg);
            }
            break;
        case '(':
            next = (build_stack*)calloc(1, sizeof(build_stack));
            next->list = PyList_New(0);
            next->prev = v;
            v = next;
            break;
        case ')':
            if (v->prev == NULL) {
                PyErr_SetString(PyExc_SystemError, "')' without '(' in Py_BuildValue");
            } else {
                PyList_Append(v->prev->list, PyList_AsTuple(v->list));
                next = v;
                v = v->prev;
                free(next);
            }
            break;
        case '[':
            next = (build_stack*)calloc(1, sizeof(build_stack));
            next->list = PyList_New(0);
            next->prev = v;
            v = next;
            break;
        case ']':
            if (v->prev == NULL) {
                PyErr_SetString(PyExc_SystemError, "']' without '[' in Py_BuildValue");
            } else {
                PyList_Append(v->prev->list, v->list);
                next = v;
                v = v->prev;
                free(next);
            }
            break;
        case '{':
            next = (build_stack*)calloc(1, sizeof(build_stack));
            next->list = PyList_New(0);
            next->prev = v;
            v = next;
            break;
        case '}':
            if (v->prev == NULL) {
                PyErr_SetString(PyExc_SystemError, "'}' without '{' in Py_BuildValue");
            } else {
                PyList_Append(v->prev->list, to_sulong(polyglot_invoke(PY_TRUFFLE_CEXT, "dict_from_list", to_java(v->list))));
                next = v;
                v = v->prev;
                free(next);
            }
            break;
        case ':':
        case ',':
            if (v->prev == NULL) {
                PyErr_SetString(PyExc_SystemError, "':' without '{' in Py_BuildValue");
            }
            break;
        default:
            fprintf(stderr, "error: unsupported format starting at %d : '%s'\n", format_idx, format);
        }
        c = format[++format_idx];
    }

    if (v->prev != NULL) {
        PyErr_SetString(PyExc_SystemError, "dangling group in Py_BuildValue");
        return NULL;
    }

    switch (PyList_Size(v->list)) {
    case 0:
        return Py_None;
    case 1:
        // single item gets unwrapped
        return PyList_GetItem(v->list, 0);
    default:
        return PyList_AsTuple(v->list);
    }
}

PyObject* Py_VaBuildValue(const char *format, va_list va) {
    return _Py_VaBuildValue_SizeT(format, va);
}

PyObject* _Py_VaBuildValue_SizeT(const char *format, va_list va) {
    return _PyTruffle_BuildValue(format, va, NULL, 0);
}

NO_INLINE
PyObject* Py_BuildValue(const char *format, ...) {
    CallWithPolyglotArgs(PyObject* result, format, 1, _PyTruffle_BuildValue, format);
    return result;
}

NO_INLINE
PyObject* _Py_BuildValue_SizeT(const char *format, ...) {
    CallWithPolyglotArgs(PyObject* result, format, 1, _PyTruffle_BuildValue, format);
    return result;
}

// taken from CPython "Python/modsupport.c"
int PyModule_AddStringConstant(PyObject *m, const char *name, const char *value) {
    PyObject *o = PyUnicode_FromString(value);
    if (!o)
        return -1;
    if (PyModule_AddObject(m, name, o) == 0)
        return 0;
    Py_DECREF(o);
    return -1;
}

// partially taken from CPython 3.6.4 "Python/getargs.c"
int _PyArg_UnpackStack(PyObject *const *args, Py_ssize_t nargs, const char *name, Py_ssize_t min, Py_ssize_t max, ...) {
    Py_ssize_t i;
    PyObject **o;

    assert(min >= 0);
    assert(min <= max);


    if (nargs < min) {
        if (name != NULL)
            PyErr_Format(
                PyExc_TypeError,
                "%.200s expected %s%zd arguments, got %zd",
                name, (min == max ? "" : "at least "), min, nargs);
        else
            PyErr_Format(
                PyExc_TypeError,
                "unpacked tuple should have %s%zd elements,"
                " but has %zd",
                (min == max ? "" : "at least "), min, nargs);
        return 0;
    }

    if (nargs == 0) {
        return 1;
    }

    if (nargs > max) {
        if (name != NULL)
            PyErr_Format(
                PyExc_TypeError,
                "%.200s expected %s%zd arguments, got %zd",
                name, (min == max ? "" : "at most "), max, nargs);
        else
            PyErr_Format(
                PyExc_TypeError,
                "unpacked tuple should have %s%zd elements,"
                " but has %zd",
                (min == max ? "" : "at most "), max, nargs);
        return 0;
    }

    for (i = 0; i < nargs; i++) {
        o = polyglot_get_arg(i + 5);
        *o = args[i];
    }
    return 1;
}

int PyArg_UnpackTuple(PyObject *args, const char *name, Py_ssize_t min, Py_ssize_t max, ...) {
    Py_ssize_t i, l;
    PyObject **o;

    assert(min >= 0);
    assert(min <= max);
    if (!PyTuple_Check(args)) {
        PyErr_SetString(PyExc_SystemError,
            "PyArg_UnpackTuple() argument list is not a tuple");
        return 0;
    }
    l = PyTuple_GET_SIZE(args);
    if (l < min) {
        if (name != NULL)
            PyErr_Format(
                PyExc_TypeError,
                "%s expected %s%zd arguments, got %zd",
                name, (min == max ? "" : "at least "), min, l);
        else
            PyErr_Format(
                PyExc_TypeError,
                "unpacked tuple should have %s%zd elements,"
                " but has %zd",
                (min == max ? "" : "at least "), min, l);
        return 0;
    }
    if (l == 0)
        return 1;
    if (l > max) {
        if (name != NULL)
            PyErr_Format(
                PyExc_TypeError,
                "%s expected %s%zd arguments, got %zd",
                name, (min == max ? "" : "at most "), max, l);
        else
            PyErr_Format(
                PyExc_TypeError,
                "unpacked tuple should have %s%zd elements,"
                " but has %zd",
                (min == max ? "" : "at most "), max, l);
        return 0;
    }

    for (i = 0; i < l; i++) {
        o = polyglot_get_arg(i + 4);
        *o = PyTuple_GET_ITEM(args, i);
    }
    return 1;
}

#undef _PyArg_NoKeywords
#undef _PyArg_NoPositional

// taken from CPython "Python/getargs.c"
int _PyArg_NoKeywords(const char *funcname, PyObject *kwargs) {
    if (kwargs == NULL) {
        return 1;
    }
    if (!PyDict_CheckExact(kwargs)) {
        PyErr_BadInternalCall();
        return 0;
    }
    if (PyDict_GET_SIZE(kwargs) == 0) {
        return 1;
    }

    PyErr_Format(PyExc_TypeError, "%.200s() takes no keyword arguments",
                    funcname);
    return 0;
}

// taken from CPython "Python/getargs.c"
int _PyArg_NoPositional(const char *funcname, PyObject *args) {
    if (args == NULL) {
        return 1;
    }
    if (!PyTuple_CheckExact(args)) {
        PyErr_BadInternalCall();
        return 0;
    }
    if (PyTuple_GET_SIZE(args) == 0) {
        return 1;
    }

    PyErr_Format(PyExc_TypeError, "%.200s() takes no positional arguments",
                    funcname);
    return 0;
}

// paritally taken from CPython "Python/getargs.c", removed format processing as in our code, it should only be called with format == NULL
static int
parser_init(struct _PyArg_Parser *parser)
{
    const char * const *keywords;
    const char *format, *msg;
    int i, len, min, max, nkw;
    PyObject *kwtuple;

    assert(parser->keywords != NULL);
    if (parser->kwtuple != NULL) {
        return 1;
    }

    keywords = parser->keywords;
    /* scan keywords and count the number of positional-only parameters */
    for (i = 0; keywords[i] && !*keywords[i]; i++) {
    }
    parser->pos = i;
    /* scan keywords and get greatest possible nbr of args */
    for (; keywords[i]; i++) {
        if (!*keywords[i]) {
            PyErr_SetString(PyExc_SystemError,
                            "Empty keyword parameter name");
            return 0;
        }
    }
    len = i;

    nkw = len - parser->pos;
    kwtuple = PyTuple_New(nkw);
    if (kwtuple == NULL) {
        return 0;
    }
    keywords = parser->keywords + parser->pos;
    for (i = 0; i < nkw; i++) {
        PyObject *str = PyUnicode_FromString(keywords[i]);
        if (str == NULL) {
            Py_DECREF(kwtuple);
            return 0;
        }
        PyUnicode_InternInPlace(&str);
        PyTuple_SET_ITEM(kwtuple, i, str);
    }
    parser->kwtuple = kwtuple;

    return 1;
}

// taken from CPython "Python/getargs.c", changed comparison function
static PyObject*
find_keyword(PyObject *kwnames, PyObject *const *kwstack, PyObject *key)
{
    Py_ssize_t i, nkwargs;

    nkwargs = PyTuple_GET_SIZE(kwnames);
    for (i=0; i < nkwargs; i++) {
        PyObject *kwname = PyTuple_GET_ITEM(kwnames, i);

        /* ptr==ptr should match in most cases since keyword keys
           should be interned strings */
        if (kwname == key) {
            return kwstack[i];
        }
        if (!PyUnicode_Check(kwname)) {
            /* ignore non-string keyword keys:
               an error will be raised below */
            continue;
        }
        if (PyUnicode_Compare(kwname, key) == 0) {
            return kwstack[i];
        }
    }
    return NULL;
}

// taken from CPython "Python/getargs.c"
#undef _PyArg_UnpackKeywords
PyObject * const *
_PyArg_UnpackKeywords(PyObject *const *args, Py_ssize_t nargs,
                      PyObject *kwargs, PyObject *kwnames,
                      struct _PyArg_Parser *parser,
                      int minpos, int maxpos, int minkw,
                      PyObject **buf)
{
    PyObject *kwtuple;
    PyObject *keyword;
    int i, posonly, minposonly, maxargs;
    int reqlimit = minkw ? maxpos + minkw : minpos;
    Py_ssize_t nkwargs;
    PyObject *current_arg;
    PyObject * const *kwstack = NULL;

    assert(kwargs == NULL || PyDict_Check(kwargs));
    assert(kwargs == NULL || kwnames == NULL);

    if (parser == NULL) {
        PyErr_BadInternalCall();
        return NULL;
    }

    if (kwnames != NULL && !PyTuple_Check(kwnames)) {
        PyErr_BadInternalCall();
        return NULL;
    }

    if (args == NULL && nargs == 0) {
        args = buf;
    }

    if (!parser_init(parser)) {
        return NULL;
    }

    kwtuple = parser->kwtuple;
    posonly = parser->pos;
    minposonly = Py_MIN(posonly, minpos);
    maxargs = posonly + (int)PyTuple_GET_SIZE(kwtuple);

    if (kwargs != NULL) {
        nkwargs = PyDict_GET_SIZE(kwargs);
    }
    else if (kwnames != NULL) {
        nkwargs = PyTuple_GET_SIZE(kwnames);
        kwstack = args + nargs;
    }
    else {
        nkwargs = 0;
    }
    if (nkwargs == 0 && minkw == 0 && minpos <= nargs && nargs <= maxpos) {
        /* Fast path. */
        return args;
    }
    if (nargs + nkwargs > maxargs) {
        /* Adding "keyword" (when nargs == 0) prevents producing wrong error
           messages in some special cases (see bpo-31229). */
        PyErr_Format(PyExc_TypeError,
                     "%.200s%s takes at most %d %sargument%s (%zd given)",
                     (parser->fname == NULL) ? "function" : parser->fname,
                     (parser->fname == NULL) ? "" : "()",
                     maxargs,
                     (nargs == 0) ? "keyword " : "",
                     (maxargs == 1) ? "" : "s",
                     nargs + nkwargs);
        return NULL;
    }
    if (nargs > maxpos) {
        if (maxpos == 0) {
            PyErr_Format(PyExc_TypeError,
                         "%.200s%s takes no positional arguments",
                         (parser->fname == NULL) ? "function" : parser->fname,
                         (parser->fname == NULL) ? "" : "()");
        }
        else {
            PyErr_Format(PyExc_TypeError,
                         "%.200s%s takes %s %d positional argument%s (%zd given)",
                         (parser->fname == NULL) ? "function" : parser->fname,
                         (parser->fname == NULL) ? "" : "()",
                         (minpos < maxpos) ? "at most" : "exactly",
                         maxpos,
                         (maxpos == 1) ? "" : "s",
                         nargs);
        }
        return NULL;
    }
    if (nargs < minposonly) {
        PyErr_Format(PyExc_TypeError,
                     "%.200s%s takes %s %d positional argument%s"
                     " (%zd given)",
                     (parser->fname == NULL) ? "function" : parser->fname,
                     (parser->fname == NULL) ? "" : "()",
                     minposonly < maxpos ? "at least" : "exactly",
                     minposonly,
                     minposonly == 1 ? "" : "s",
                     nargs);
        return NULL;
    }

    /* copy tuple args */
    for (i = 0; i < nargs; i++) {
        buf[i] = args[i];
    }

    /* copy keyword args using kwtuple to drive process */
    for (i = Py_MAX((int)nargs, posonly); i < maxargs; i++) {
        if (nkwargs) {
            keyword = PyTuple_GET_ITEM(kwtuple, i - posonly);
            if (kwargs != NULL) {
                current_arg = PyDict_GetItemWithError(kwargs, keyword);
                if (!current_arg && PyErr_Occurred()) {
                    return NULL;
                }
            }
            else {
                current_arg = find_keyword(kwnames, kwstack, keyword);
            }
        }
        else if (i >= reqlimit) {
            break;
        }
        else {
            current_arg = NULL;
        }

        buf[i] = current_arg;

        if (current_arg) {
            --nkwargs;
        }
        else if (i < minpos || (maxpos <= i && i < reqlimit)) {
            /* Less arguments than required */
            keyword = PyTuple_GET_ITEM(kwtuple, i - posonly);
            PyErr_Format(PyExc_TypeError,  "%.200s%s missing required "
                         "argument '%U' (pos %d)",
                         (parser->fname == NULL) ? "function" : parser->fname,
                         (parser->fname == NULL) ? "" : "()",
                         keyword, i+1);
            return NULL;
        }
    }

    if (nkwargs > 0) {
        Py_ssize_t j;
        /* make sure there are no arguments given by name and position */
        for (i = posonly; i < nargs; i++) {
            keyword = PyTuple_GET_ITEM(kwtuple, i - posonly);
            if (kwargs != NULL) {
                current_arg = PyDict_GetItemWithError(kwargs, keyword);
                if (!current_arg && PyErr_Occurred()) {
                    return NULL;
                }
            }
            else {
                current_arg = find_keyword(kwnames, kwstack, keyword);
            }
            if (current_arg) {
                /* arg present in tuple and in dict */
                PyErr_Format(PyExc_TypeError,
                             "argument for %.200s%s given by name ('%U') "
                             "and position (%d)",
                             (parser->fname == NULL) ? "function" : parser->fname,
                             (parser->fname == NULL) ? "" : "()",
                             keyword, i+1);
                return NULL;
            }
        }
        /* make sure there are no extraneous keyword arguments */
        j = 0;
        while (1) {
            int match;
            if (kwargs != NULL) {
                if (!PyDict_Next(kwargs, &j, &keyword, NULL))
                    break;
            }
            else {
                if (j >= PyTuple_GET_SIZE(kwnames))
                    break;
                keyword = PyTuple_GET_ITEM(kwnames, j);
                j++;
            }

            if (!PyUnicode_Check(keyword)) {
                PyErr_SetString(PyExc_TypeError,
                                "keywords must be strings");
                return NULL;
            }
            match = PySequence_Contains(kwtuple, keyword);
            if (match <= 0) {
                if (!match) {
                    PyErr_Format(PyExc_TypeError,
                                 "'%U' is an invalid keyword "
                                 "argument for %.200s%s",
                                 keyword,
                                 (parser->fname == NULL) ? "this function" : parser->fname,
                                 (parser->fname == NULL) ? "" : "()");
                }
                return NULL;
            }
        }
    }

    return buf;
}

// Taken from CPython 3.8 getargs.c
void _PyArg_BadArgument(const char *fname, const char *displayname,
                   const char *expected, PyObject *arg) {
    PyErr_Format(PyExc_TypeError,
                 "%.200s() %.200s must be %.50s, not %.50s",
                 fname, displayname, expected,
                 arg == Py_None ? "None" : arg->ob_type->tp_name);
}

#undef _PyArg_CheckPositional

// Taken from CPython 3.8 getargs.c
int
_PyArg_CheckPositional(const char *name, Py_ssize_t nargs,
                       Py_ssize_t min, Py_ssize_t max)
{
    assert(min >= 0);
    assert(min <= max);

    if (nargs < min) {
        if (name != NULL)
            PyErr_Format(
                PyExc_TypeError,
                "%.200s expected %s%zd argument%s, got %zd",
                name, (min == max ? "" : "at least "), min, min == 1 ? "" : "s", nargs);
        else
            PyErr_Format(
                PyExc_TypeError,
                "unpacked tuple should have %s%zd element%s,"
                " but has %zd",
                (min == max ? "" : "at least "), min, min == 1 ? "" : "s", nargs);
        return 0;
    }

    if (nargs == 0) {
        return 1;
    }

    if (nargs > max) {
        if (name != NULL)
            PyErr_Format(
                PyExc_TypeError,
                "%.200s expected %s%zd argument%s, got %zd",
                name, (min == max ? "" : "at most "), max, max == 1 ? "" : "s", nargs);
        else
            PyErr_Format(
                PyExc_TypeError,
                "unpacked tuple should have %s%zd element%s,"
                " but has %zd",
                (min == max ? "" : "at most "), max, max == 1 ? "" : "s", nargs);
        return 0;
    }

    return 1;
}
