/*
 * Copyright (c) 2018, Oracle and/or its affiliates.
 *
 * The Universal Permissive License (UPL), Version 1.0
 *
 * Subject to the condition set forth below, permission is hereby granted to any
 * person obtaining a copy of this software, associated documentation and/or data
 * (collectively the "Software"), free of charge and under any and all copyright
 * rights in the Software, and any and all patent rights owned or freely
 * licensable by each licensor hereunder covering either (i) the unmodified
 * Software as contributed to or provided by such licensor, or (ii) the Larger
 * Works (as defined below), to deal in both
 *
 * (a) the Software, and
 * (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
 *     one is included with the Software (each a "Larger Work" to which the
 *     Software is contributed by such licensors),
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

PyObject* get_arg_or_kw(PyObject* argv, PyObject* kwds, char** kwdnames, int argnum, int is_optional, int is_keyword) {
    if (!is_keyword) {
        int l = truffle_invoke_i(PY_TRUFFLE_CEXT, "PyObject_LEN", to_java(argv));
        if (argnum < l) {
            return PyTuple_GetItem(argv, argnum);
        }
    }
    const char* kwdname = kwdnames[argnum];
    void* kwarg = PyDict_GetItem(kwds, to_sulong(truffle_read_string(kwdname)));
    if (kwarg == Py_None) {
        return NULL;
    } else {
        return kwarg;
    }
}

/* argparse */
int PyTruffle_Arg_ParseTupleAndKeywords(PyObject *argv, PyObject *kwds, const char *format, char** kwdnames, int outc, void *v0, void *v1, void *v2, void *v3, void *v4, void *v5, void *v6, void *v7, void *v8, void *v9, void *v10, void *v11, void *v12, void *v13, void *v14, void *v15, void *v16, void *v17, void *v18, void *v19) {
    int outputn = 0;
    int formatn = 0;
    int valuen = 0;
    int rest_optional = 0;
    int rest_keywords = 0;

#   define ASSIGN(T, arg) _ASSIGN(T, outputn, arg); outputn++
#   define _ASSIGN(T, n, arg)                   \
    switch(n) {                                 \
    case 0: __ASSIGN(T, 0, arg); break;         \
    case 1: __ASSIGN(T, 1, arg); break;         \
    case 2: __ASSIGN(T, 2, arg); break;         \
    case 3: __ASSIGN(T, 3, arg); break;         \
    case 4: __ASSIGN(T, 4, arg); break;         \
    case 5: __ASSIGN(T, 5, arg); break;         \
    case 6: __ASSIGN(T, 6, arg); break;         \
    case 7: __ASSIGN(T, 7, arg); break;         \
    case 8: __ASSIGN(T, 8, arg); break;         \
    case 9: __ASSIGN(T, 9, arg); break;         \
    case 10: __ASSIGN(T, 10, arg); break;       \
    case 11: __ASSIGN(T, 11, arg); break;       \
    case 12: __ASSIGN(T, 12, arg); break;       \
    case 13: __ASSIGN(T, 13, arg); break;       \
    case 14: __ASSIGN(T, 14, arg); break;       \
    case 15: __ASSIGN(T, 15, arg); break;       \
    case 16: __ASSIGN(T, 16, arg); break;       \
    case 17: __ASSIGN(T, 17, arg); break;       \
    case 18: __ASSIGN(T, 18, arg); break;       \
    case 19: __ASSIGN(T, 19, arg); break;       \
    }
#   define __ASSIGN(T, num, arg) *((T*)_ARG(num)) = (T)arg
#   define _ARG(num) v ## num
#   define ARG(n) (((n) == 0) ? v0 : (((n) == 1) ? v1 : (((n) == 2) ? v2 : (((n) == 3) ? v3 : (((n) == 4) ? v4 : (((n) == 5) ? v5 : (((n) == 6) ? v6 : (((n) == 7) ? v7 : (((n) == 8) ? v8 : (((n) == 9) ? v9 : (((n) == 10) ? v10 : (((n) == 11) ? v11 : (((n) == 12) ? v12 : (((n) == 13) ? v13 : (((n) == 14) ? v14 : (((n) == 15) ? v15 : (((n) == 16) ? v16 : (((n) == 17) ? v17 : (((n) == 18) ? v18 : (((n) == 19) ? v19 : NULL))))))))))))))))))))

#   define PEEKFMT format[formatn]
#   define POPFMT format[formatn++]
#   define POPARG get_arg_or_kw(argv, kwds, kwdnames, valuen++, rest_optional, rest_keywords)
#   define POPOUTPUTVARIABLE ARG(outputn++)

    int max = strlen(format);
    while (outputn < outc) {
        char c = POPFMT;

        if (c == 's' || c == 'z' || c == 'y') {
            PyObject* arg = POPARG;
            if (c == 'z' && arg == Py_None) {
                ASSIGN(const char*, NULL);
                if (PEEKFMT == '#') {
                    ASSIGN(int, NULL);
                    POPFMT;
                }
            } else if (PEEKFMT == '*') {
                // TODO: bytes
                ASSIGN(const char*, as_char_pointer(arg));
                POPFMT;
            } else if (PEEKFMT == '#') {
                ASSIGN(const char*, as_char_pointer(arg));
                ASSIGN(int, as_int(truffle_invoke(to_java(arg), "__len__")));
                POPFMT;
            } else {
                ASSIGN(const char*, as_char_pointer(arg));
            }
        } else if (c == 'S') {
            PyObject* arg = POPARG;
            truffle_invoke(PY_TRUFFLE_CEXT, "check_argtype", outputn, to_java(arg), truffle_read(PY_BUILTIN, "bytes"));
            ASSIGN(PyObject*, arg);
        } else if (c == 'Y') {
            goto error;
        } else if (c == 'u' || c == 'Z') {
            if (PEEKFMT == '#') {
                POPFMT;
            }
            goto error;
        } else if (c == 'U') {
            goto error;
        } else if (c == 'w' && PEEKFMT == '*') {
            POPFMT;
            goto error;
        } else if (c == 'e') {
            c = POPFMT;
            if (c == 's') {
                if (PEEKFMT == '#') {
                    POPFMT;
                }
            } else if (c == 't') {
            }
            goto error;
        } else if (c == 'b') {
            ASSIGN(unsigned char, as_uchar(POPARG));
        } else if (c == 'B') {
            ASSIGN(unsigned char, as_uchar(POPARG));
        } else if (c == 'h') {
            ASSIGN(short int, as_short(POPARG));
        } else if (c == 'H') {
            ASSIGN(short int, as_short(POPARG));
        } else if (c == 'i') {
            ASSIGN(int, as_int(POPARG));
        } else if (c == 'I') {
            ASSIGN(int, as_int(POPARG));
        } else if (c == 'l') {
            ASSIGN(long, as_long(POPARG));
        } else if (c == 'k') {
            ASSIGN(unsigned long, as_long(POPARG));
        } else if (c == 'L') {
            ASSIGN(long long, POPARG);
        } else if (c == 'K') {
            ASSIGN(unsigned long long, POPARG);
        } else if (c == 'n') {
            ASSIGN(Py_ssize_t, as_long(POPARG));
        } else if (c == 'c') {
            PyObject* arg = POPARG;
            ASSIGN(char, as_char(truffle_invoke(to_java(arg), "__getitem__", 0)));
        } else if (c == 'C') {
            PyObject* arg = POPARG;
            ASSIGN(int, as_int(truffle_invoke(to_java(arg), "__getitem__", 0)));
        } else if (c == 'f') {
            ASSIGN(float, as_float(POPARG));
        } else if (c == 'd') {
            ASSIGN(double, as_double(POPARG));
        } else if (c == 'D') {
            goto error;
        } else if (c == 'O') {
            if (PEEKFMT == '!') {
                POPFMT;
                PyTypeObject* typeobject = (PyTypeObject*)POPOUTPUTVARIABLE;
                PyObject* arg = POPARG;
                if (!(Py_TYPE(arg) == typeobject)) {
                    goto error;
                } else {
                    ASSIGN(PyObject*, arg);
                }
            } else if (PEEKFMT == '&') {
                POPFMT;
                void* (*converter)(PyObject*,void*) = POPOUTPUTVARIABLE;
                PyObject* arg = POPARG;
                int status = converter(arg, POPOUTPUTVARIABLE);
                if (!status) { // converter should have set exception
                    return NULL;
                }
            } else {
                ASSIGN(PyObject*, POPARG);
            }
        } else if (c == 'p') {
            ASSIGN(int, as_int(truffle_invoke(to_java(POPARG), "__bool__")));
        } else if (c == '(') {
            goto error;
        } else if (c == '|') {
            rest_optional = 1;
        } else if (c == '$') {
            rest_keywords = 1;
        } else if (c == ':') {
            break;
        } else if (c == ';') {
            break;
        } else {
            goto error;
        }
    }
    return outputn;

 error:
    fprintf(stderr, "ERROR: unimplemented format '%s'\n", format + formatn -1);
    return outputn;

#   undef ASSIGN
#   undef _ASSIGN
#   undef __ASSIGN
#   undef _ARG
#   undef ARG
#   undef PEEKFMT
#   undef POPFMT
#   undef POPARG
#   undef POPOUTPUTVARIABLE
}

int _PyArg_ParseStack_SizeT(PyObject** args, Py_ssize_t nargs, PyObject* kwnames, struct _PyArg_Parser* parser, ...) {
    va_list vl;
    va_start(vl, parser);
    int* fd = va_arg(vl,int*);
    *fd = args[0];
    return 1;
}

typedef struct _build_stack {
    PyObject* list;
    struct _build_stack* prev;
} build_stack;

PyObject* _Py_BuildValue_SizeT(const char *format, ...) {
#   define ARG truffle_get_arg(value_idx)
#   define APPEND_VALUE(list, value) PyList_Append(list, value); value_idx++

    unsigned int value_idx = 1;
    unsigned int format_idx = 0;
    build_stack *v = (build_stack*)calloc(1, sizeof(build_stack));
    build_stack *next;
    v->list = PyList_New(0);

    char c = format[format_idx];
    while (c != '\0') {
        PyObject* list = v->list;

        switch(c) {
        case 'n':
            APPEND_VALUE(list, (Py_ssize_t)ARG);
            break;
        case 'i':
            APPEND_VALUE(list, PyLong_FromLong((int)ARG));
            break;
        case 's':
            if (ARG == NULL) {
                APPEND_VALUE(list, Py_None);
            } else {
                APPEND_VALUE(list, polyglot_from_string((char*)ARG, "utf-8"));
            }
            break;
        case 'd':
            APPEND_VALUE(list, PyFloat_FromDouble((double)(unsigned long long)ARG));
            break;
        case 'l':
            APPEND_VALUE(list, (long)ARG);
            break;
        case 'L':
            APPEND_VALUE(list, (long long)ARG);
            break;
        case 'k':
            APPEND_VALUE(list, (unsigned long)ARG);
            break;
        case 'K':
            APPEND_VALUE(list, (unsigned long long)ARG);
            break;
        case 'N':
        case 'S':
        case 'O':
            if (ARG == NULL && !PyErr_Occurred()) {
                /* If a NULL was passed because a call that should have constructed a value failed, that's OK,
                 * and we pass the error on; but if no error occurred it's not clear that the caller knew what she was doing. */
                PyErr_SetString(PyExc_SystemError, "NULL object passed to Py_BuildValue");
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
                PyList_Append(v->prev->list, polyglot_invoke(PY_TRUFFLE_CEXT, "dict_from_list", to_java(v->list)));
                next = v;
                v = v->prev;
                free(next);
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
