/*
 * Copyright (c) 2018, Oracle and/or its affiliates. All rights reserved.
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

typedef struct _positional_argstack {
    PyObject* argv;
    int argnum;
    struct _positional_argstack* prev;
} positional_argstack;

UPCALL_ID(PyObject_LEN);
PyObject* PyTruffle_GetArg(positional_argstack* p, PyObject* kwds, char** kwdnames, unsigned char keywords_only) {
    void* out = NULL;
    if (!keywords_only) {
        int l = UPCALL_CEXT_I(_jls_PyObject_LEN, native_to_java(p->argv));
        if (p->argnum < l) {
            out = PyTuple_GET_ITEM(p->argv, p->argnum);
        }
    }
    if (out == NULL && p->prev == NULL && kwdnames != NULL) { // only the bottom argstack can have keyword names
        const char* kwdname = kwdnames[p->argnum];
        if (kwdname != NULL) {
            out = PyDict_GetItemString(kwds, kwdname);
        }
    }
    (p->argnum)++;
    return out;
}

#define PyTruffle_WriteOut(n, T, arg) {         \
        T __oai = arg;                          \
        if (PyErr_Occurred()) {                 \
            return 0;                           \
        }                                       \
        *((T*)polyglot_get_arg(n)) = __oai;     \
        n++;                                    \
    } while(0);

#define PyTruffle_ArgN(n) polyglot_get_arg(n)

#define PyTruffle_SkipOptionalArg(n, arg, optional)     \
    if (arg == NULL && optional) {                      \
        n++;                                            \
        break;                                          \
    }

MUST_INLINE PyObject* PyTruffle_Stack2Tuple(PyObject** args, Py_ssize_t nargs) {
    PyObject* argv = PyTuple_New(nargs);
    Py_ssize_t i;
    for (i=0; i < nargs; i++) {
        PyTuple_SetItem(argv, i, args[i]);
    }
    return argv;
}

/* argparse */
UPCALL_ID(__bool__);
int _PyArg_ParseTupleAndKeywords_SizeT(PyObject *argv, PyObject *kwds, const char *format, char** kwdnames, ...) {
    PyObject* arg;
    int format_idx = 0;
    int output_idx = 4;
    unsigned char rest_optional = 0;
    unsigned char rest_keywords_only = 0;

    positional_argstack *v = (positional_argstack*)calloc(1, sizeof(positional_argstack));
    v->argv = argv;
    v->argnum = 0;
    positional_argstack *next;

    char c = format[format_idx];
    while (c != '\0') {
        switch (c) {
        case 's':
        case 'z':
        case 'y':
            arg = PyTruffle_GetArg(v, kwds, kwdnames, rest_keywords_only);
            if (format[format_idx + 1] == '*') {
                format_idx++; // skip over '*'
                PyErr_Format(PyExc_TypeError, "%c* not supported", c);
                return 0;
            } else if (arg == Py_None) {
                if (c == 'z') {
                    PyTruffle_WriteOut(output_idx, const char*, NULL);
                    if (format[format_idx + 1] == '#') {
                        format_idx++; // skip over '#'
                        PyTruffle_WriteOut(output_idx, int, 0);
                    }
                } else {
                    PyErr_Format(PyExc_TypeError, "expected str or bytes-like, got None");
                    return 0;
                }
            } else {
                PyTruffle_SkipOptionalArg(output_idx, arg, rest_optional);
                PyTruffle_WriteOut(output_idx, const char*, as_char_pointer(arg));
                if (format[format_idx + 1] == '#') {
                    format_idx++;
                    PyTruffle_WriteOut(output_idx, int, Py_SIZE(arg));
                }
            }
            break;
        case 'S':
            arg = PyTruffle_GetArg(v, kwds, kwdnames, rest_keywords_only);
            PyTruffle_SkipOptionalArg(output_idx, arg, rest_optional);
            if (!PyBytes_Check(arg)) {
                PyErr_Format(PyExc_TypeError, "expected bytes, got %R", Py_TYPE(arg));
                return 0;
            }
            PyTruffle_WriteOut(output_idx, PyObject*, arg);
            break;
        case 'Y':
            arg = PyTruffle_GetArg(v, kwds, kwdnames, rest_keywords_only);
            PyTruffle_SkipOptionalArg(output_idx, arg, rest_optional);
            if (!PyByteArray_Check(arg)) {
                PyErr_Format(PyExc_TypeError, "expected bytearray, got %R", Py_TYPE(arg));
                return 0;
            }
            PyTruffle_WriteOut(output_idx, PyObject*, arg);
            break;
        case 'u':
        case 'Z':
            PyErr_Format(PyExc_TypeError, "Py_UNICODE argument parsing not supported");
            return 0;
        case 'U':
            arg = PyTruffle_GetArg(v, kwds, kwdnames, rest_keywords_only);
            PyTruffle_SkipOptionalArg(output_idx, arg, rest_optional);
            if (!PyUnicode_Check(arg)) {
                PyErr_Format(PyExc_TypeError, "expected str, got %R", Py_TYPE(arg));
                return 0;
            }
            PyTruffle_WriteOut(output_idx, PyObject*, arg);
            break;
        case 'w':
            PyErr_Format(PyExc_TypeError, "'w' format specifier in argument parsing not supported");
            return 0;
        case 'e':
            switch (format[++format_idx]) {
            case 's':
            case 't':
                break;
            }
            if (format[format_idx + 1] == '#') {
                format_idx++;
            }
            PyErr_Format(PyExc_TypeError, "'e*' format specifiers are not supported");
            return 0;
        case 'b':
            arg = PyTruffle_GetArg(v, kwds, kwdnames, rest_keywords_only);
            PyTruffle_SkipOptionalArg(output_idx, arg, rest_optional);
            if (_PyLong_Sign(arg) < 0) {
                PyErr_Format(PyExc_TypeError, "expected non-negative integer");
                return 0;
            }
            PyTruffle_WriteOut(output_idx, unsigned char, as_uchar(arg));
            break;
        case 'B':
            arg = PyTruffle_GetArg(v, kwds, kwdnames, rest_keywords_only);
            PyTruffle_SkipOptionalArg(output_idx, arg, rest_optional);
            PyTruffle_WriteOut(output_idx, unsigned char, as_uchar(arg));
            break;
        case 'h':
            arg = PyTruffle_GetArg(v, kwds, kwdnames, rest_keywords_only);
            PyTruffle_SkipOptionalArg(output_idx, arg, rest_optional);
            if (_PyLong_Sign(arg) < 0) {
                PyErr_Format(PyExc_TypeError, "expected non-negative integer");
                return 0;
            }
            PyTruffle_WriteOut(output_idx, short int, as_short(arg));
            break;
        case 'H':
            arg = PyTruffle_GetArg(v, kwds, kwdnames, rest_keywords_only);
            PyTruffle_SkipOptionalArg(output_idx, arg, rest_optional);
            PyTruffle_WriteOut(output_idx, short int, as_short(arg));
            break;
        case 'i':
            arg = PyTruffle_GetArg(v, kwds, kwdnames, rest_keywords_only);
            PyTruffle_SkipOptionalArg(output_idx, arg, rest_optional);
            PyTruffle_WriteOut(output_idx, int, as_int(arg));
            break;
        case 'I':
            arg = PyTruffle_GetArg(v, kwds, kwdnames, rest_keywords_only);
            PyTruffle_SkipOptionalArg(output_idx, arg, rest_optional);
            PyTruffle_WriteOut(output_idx, unsigned int, as_int(arg));
            break;
        case 'l':
            arg = PyTruffle_GetArg(v, kwds, kwdnames, rest_keywords_only);
            PyTruffle_SkipOptionalArg(output_idx, arg, rest_optional);
            PyTruffle_WriteOut(output_idx, long, as_long(arg));
            break;
        case 'k':
            arg = PyTruffle_GetArg(v, kwds, kwdnames, rest_keywords_only);
            PyTruffle_SkipOptionalArg(output_idx, arg, rest_optional);
            PyTruffle_WriteOut(output_idx, unsigned long, as_long(arg));
            break;
        case 'L':
            arg = PyTruffle_GetArg(v, kwds, kwdnames, rest_keywords_only);
            PyTruffle_SkipOptionalArg(output_idx, arg, rest_optional);
            PyTruffle_WriteOut(output_idx, long long, as_long_long(arg));
            break;
        case 'K':
            arg = PyTruffle_GetArg(v, kwds, kwdnames, rest_keywords_only);
            PyTruffle_SkipOptionalArg(output_idx, arg, rest_optional);
            PyTruffle_WriteOut(output_idx, unsigned long long, as_unsigned_long_long(arg));
            break;
        case 'n':
            arg = PyTruffle_GetArg(v, kwds, kwdnames, rest_keywords_only);
            PyTruffle_SkipOptionalArg(output_idx, arg, rest_optional);
            PyTruffle_WriteOut(output_idx, Py_ssize_t, as_long(arg));
            break;
        case 'c':
            arg = PyTruffle_GetArg(v, kwds, kwdnames, rest_keywords_only);
            PyTruffle_SkipOptionalArg(output_idx, arg, rest_optional);
            if (!(PyBytes_Check(arg) || PyByteArray_Check(arg))) {
                PyErr_Format(PyExc_TypeError, "expected bytes or bytearray, got %R", Py_TYPE(arg));
                return 0;
            }
            if (Py_SIZE(arg) != 1) {
                PyErr_Format(PyExc_TypeError, "expected bytes or bytearray of length 1, was length %d", Py_SIZE(arg));
                return 0;
            }
            PyTruffle_WriteOut(output_idx, char, as_char(polyglot_invoke(to_java(arg), "__getitem__", 0)));
            break;
        case 'C':
            arg = PyTruffle_GetArg(v, kwds, kwdnames, rest_keywords_only);
            PyTruffle_SkipOptionalArg(output_idx, arg, rest_optional);
            if (!PyUnicode_Check(arg)) {
                PyErr_Format(PyExc_TypeError, "expected bytes or bytearray, got %R", Py_TYPE(arg));
                return 0;
            }
            if (Py_SIZE(arg) != 1) {
                PyErr_Format(PyExc_TypeError, "expected str of length 1, was length %d", Py_SIZE(arg));
                return 0;
            }
            PyTruffle_WriteOut(output_idx, int, as_int(polyglot_invoke(to_java(arg), "__getitem__", 0)));
            break;
        case 'f':
            arg = PyTruffle_GetArg(v, kwds, kwdnames, rest_keywords_only);
            PyTruffle_SkipOptionalArg(output_idx, arg, rest_optional);
            PyTruffle_WriteOut(output_idx, float, as_float(arg));
            break;
        case 'd':
            arg = PyTruffle_GetArg(v, kwds, kwdnames, rest_keywords_only);
            PyTruffle_SkipOptionalArg(output_idx, arg, rest_optional);
            PyTruffle_WriteOut(output_idx, double, as_double(arg));
            break;
        case 'D':
            PyErr_Format(PyExc_TypeError, "converting complex arguments not implemented, yet");
            return 0;
        case 'O':
            arg = PyTruffle_GetArg(v, kwds, kwdnames, rest_keywords_only);
            if (format[format_idx + 1] == '!') {
                format_idx++;
                PyTruffle_SkipOptionalArg(output_idx, arg, rest_optional);
                PyTypeObject* typeobject = (PyTypeObject*)PyTruffle_ArgN(output_idx);
                output_idx++;
                if (!PyType_IsSubtype(Py_TYPE(arg), typeobject)) {
                    PyErr_Format(PyExc_TypeError, "expected object of type %R, got %R", typeobject, Py_TYPE(arg));
                    return 0;
                }
                PyTruffle_WriteOut(output_idx, PyObject*, arg);
            } else if (format[format_idx + 1] == '&') {
                format_idx++;
                void* (*converter)(PyObject*,void*) = PyTruffle_ArgN(output_idx);
                output_idx++;
                PyTruffle_SkipOptionalArg(output_idx, arg, rest_optional);
                void* output = PyTruffle_ArgN(output_idx);
                output_idx++;
                int status = converter(arg, output);
                if (!status) {
                    if (!PyErr_Occurred()) {
                        // converter should have set exception
                        PyErr_Format(PyExc_TypeError, "converter function failed to set an error on failure");
                    }
                    return 0;
                }
            } else {
                PyTruffle_SkipOptionalArg(output_idx, arg, rest_optional);
                PyTruffle_WriteOut(output_idx, PyObject*, arg);
            }
            break;
        case 'p':
            arg = PyTruffle_GetArg(v, kwds, kwdnames, rest_keywords_only);
            PyTruffle_SkipOptionalArg(output_idx, arg, rest_optional);
            PyTruffle_WriteOut(output_idx, int, (UPCALL_I(native_to_java(arg), polyglot_from_string("__bool__", SRC_CS))));
            break;
        case '(':
            arg = PyTruffle_GetArg(v, kwds, kwdnames, rest_keywords_only);
            PyTruffle_SkipOptionalArg(output_idx, arg, rest_optional);
            if (!PyTuple_Check(arg)) {
                PyErr_Format(PyExc_TypeError, "expected tuple, got %R", Py_TYPE(arg));
                return 0;
            }
            next = (positional_argstack*)calloc(1, sizeof(positional_argstack));
            next->argv = arg;
            next->argnum = 0;
            next->prev = v;
            v = next;
            break;
        case ')':
            if (v->prev == NULL) {
                PyErr_SetString(PyExc_SystemError, "')' without '(' in argument parsing");
            } else {
                next = v;
                v = v->prev;
                free(next);
            }
            break;
        case '|':
            rest_optional = 1;
            break;
        case '$':
            rest_keywords_only = 1;
            break;
        case ':':
            // TODO: adapt error message based on string after this
            goto end;
        case ';':
            // TODO: adapt error message based on string after this
            goto end;
        default:
            PyErr_Format(PyExc_TypeError, "unrecognized format char in arguments parsing: %c", c);
        }
        c = format[++format_idx];
    }

 end:
    free(v);
    return 1;
}

typedef struct _build_stack {
    PyObject* list;
    struct _build_stack* prev;
} build_stack;

PyObject* _Py_BuildValue_SizeT(const char *format, ...) {
#   define ARG polyglot_get_arg(value_idx)
#   define APPEND_VALUE(list, value) PyList_Append(list, value); value_idx++

    PyObject* (*converter)(void*) = NULL;
    char argchar[2] = {'\0'};
    unsigned int value_idx = 1;
    unsigned int format_idx = 0;
    build_stack *v = (build_stack*)calloc(1, sizeof(build_stack));
    build_stack *next;
    v->list = PyList_New(0);

    char c = format[format_idx];
    while (c != '\0') {
        PyObject* list = v->list;

        switch(c) {
        case 's':
        case 'z':
        case 'U':
            if (format[format_idx + 1] == '#') {
                int size = (int)polyglot_get_arg(value_idx + 1);
                if (ARG == NULL) {
                    APPEND_VALUE(list, Py_None);
                } else {
                    APPEND_VALUE(list, PyUnicode_FromStringAndSize((char*)ARG, size));
                }
                value_idx++; // skip length argument
                format_idx++;
            } else {
                if (ARG == NULL) {
                    APPEND_VALUE(list, Py_None);
                } else {
                    APPEND_VALUE(list, PyUnicode_FromString((char*)ARG));
                }
            }
            break;
        case 'y':
            if (format[format_idx + 1] == '#') {
                int size = (int)polyglot_get_arg(value_idx + 1);
                if (ARG == NULL) {
                    APPEND_VALUE(list, Py_None);
                } else {
                    APPEND_VALUE(list, PyBytes_FromStringAndSize((char*)ARG, size));
                }
                value_idx++; // skip length argument
                format_idx++;
            } else {
                if (ARG == NULL) {
                    APPEND_VALUE(list, Py_None);
                } else {
                    APPEND_VALUE(list, PyBytes_FromString((char*)ARG));
                }
            }
            break;
        case 'u':
            fprintf(stderr, "error: unsupported format 'u'\n");
            break;
        case 'i':
        case 'b':
        case 'h':
            APPEND_VALUE(list, PyLong_FromLong((int)ARG));
            break;
        case 'l':
            APPEND_VALUE(list, PyLong_FromLong((long)ARG));
            break;
        case 'B':
        case 'H':
        case 'I':
            APPEND_VALUE(list, PyLong_FromUnsignedLong((unsigned int)ARG));
            break;
        case 'k':
            APPEND_VALUE(list, PyLong_FromUnsignedLong((unsigned long)ARG));
            break;
        case 'L':
            APPEND_VALUE(list, PyLong_FromLongLong((long long)ARG));
            break;
        case 'K':
            APPEND_VALUE(list, PyLong_FromLongLong((unsigned long long)ARG));
            break;
        case 'n':
            APPEND_VALUE(list, PyLong_FromSsize_t((Py_ssize_t)ARG));
            break;
        case 'c':
            argchar[0] = (char)ARG;
            APPEND_VALUE(list, PyBytes_FromStringAndSize(argchar, 1));
            break;
        case 'C':
            argchar[0] = (char)ARG;
            APPEND_VALUE(list, polyglot_from_string(argchar, "ascii"));
            break;
        case 'd':
        case 'f':
            APPEND_VALUE(list, PyFloat_FromDouble((double)(unsigned long long)ARG));
            break;
        case 'D':
            fprintf(stderr, "error: unsupported format 'D'\n");
            break;
        case 'O':
            if (format[format_idx + 1] == '&') {
                converter = polyglot_get_arg(value_idx + 1);
            }
        case 'S':
        case 'N':
            if (ARG == NULL) {
                if (!PyErr_Occurred()) {
                    /* If a NULL was passed because a call that should have constructed a value failed, that's OK,
                     * and we pass the error on; but if no error occurred it's not clear that the caller knew what she was doing. */
                    PyErr_SetString(PyExc_SystemError, "NULL object passed to Py_BuildValue");
                }
                return NULL;
            } else if (converter != NULL) {
                APPEND_VALUE(list, converter(ARG));
                converter = NULL;
                value_idx++; // skip converter argument
                format_idx++;
            } else {
                APPEND_VALUE(list, ARG);
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

#   undef APPEND_VALUE
#   undef ARG

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
