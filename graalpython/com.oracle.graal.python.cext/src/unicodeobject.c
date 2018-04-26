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

PyTypeObject PyUnicode_Type = PY_TRUFFLE_TYPE("str", &PyType_Type, Py_TPFLAGS_DEFAULT | Py_TPFLAGS_BASETYPE | Py_TPFLAGS_UNICODE_SUBCLASS);

void* PyTruffle_Unicode_FromString(const char* o) {
    return truffle_invoke(PY_TRUFFLE_CEXT, "PyUnicode_FromString", polyglot_from_string(o, "utf-8"));
}

PyObject* PyUnicode_FromString(const char* o) {
    return to_sulong(PyTruffle_Unicode_FromString(o));
}

static PyObject* _PyUnicode_FromUTF8(const char* o) {
	PyObject *s = PyTruffle_Unicode_FromUTF8(o, ERROR_MARKER);
	if(s == ERROR_MARKER) {
		return NULL;
	}
    return to_sulong(s);
}

PyObject * PyUnicode_FromStringAndSize(const char *u, Py_ssize_t size) {
	if (size < 0) {
		PyErr_SetString(PyExc_SystemError, "Negative size passed to PyUnicode_FromStringAndSize");
        return NULL;
    }
    return to_sulong(truffle_invoke(PY_TRUFFLE_CEXT, "PyUnicode_FromString", truffle_read_n_string(u, size)));
}

PyObject* PyTruffle_Unicode_FromFormat(const char* fmt, int s, void* v0, void* v1, void* v2, void* v3, void* v4, void* v5, void* v6, void* v7, void* v8, void* v9) {
    char** allocated_strings = calloc(sizeof(char*), s);
#   define ASSIGN(n, value)            \
    switch(n) {                        \
    case 0: v0 = value; break;         \
    case 1: v1 = value; break;         \
    case 2: v2 = value; break;         \
    case 3: v3 = value; break;         \
    case 4: v4 = value; break;         \
    case 5: v5 = value; break;         \
    case 6: v6 = value; break;         \
    case 7: v7 = value; break;         \
    case 8: v8 = value; break;         \
    case 9: v9 = value; break;         \
    }

    char* fmtcpy = strdup(fmt);
    char* c = fmtcpy;
    char* allocated;
    int cnt = 0;

    while (c[0] && cnt < s) {
        if (c[0] == '%') {
            switch (c[1]) {
            case 'c':
                c[1] = 'd';
                break;
            case 'A':
                c[1] = 's';
                allocated_strings[cnt] = allocated = as_char_pointer(truffle_invoke(PY_TRUFFLE_CEXT, "builtin_ascii", to_java(truffle_get_arg(cnt + 2))));
                ASSIGN(cnt, allocated);
                break;
            case 'U':
                c[1] = 's';
                allocated_strings[cnt] = allocated = as_char_pointer(PyObject_Str(truffle_get_arg(cnt + 2)));
                ASSIGN(cnt, allocated);
                break;
            case 'S':
                c[1] = 's';
                allocated_strings[cnt] = allocated = as_char_pointer(PyObject_Str(truffle_get_arg(cnt + 2)));
                ASSIGN(cnt, allocated);
                break;
            case 'R':
                c[1] = 's';
                allocated_strings[cnt] = allocated = as_char_pointer(PyObject_Repr(truffle_get_arg(cnt + 2)));
                ASSIGN(cnt, allocated);
                break;
            }
            cnt++;
            c += 1;
        }
        c += 1;
    }

    char buffer[2048] = {'\0'};
    snprintf(buffer, 2047, fmtcpy, v0, v1, v2, v3, v4, v5, v6, v7, v8, v9);

    for (int i = 0; i < s; i++) {
        if (allocated_strings[i] != NULL) {
            truffle_free_cstr(allocated_strings[i]);
        }
    }

    return PyUnicode_FromString(buffer);

#   undef ASSIGN
}


PyObject * PyUnicode_FromUnicode(const Py_UNICODE *u, Py_ssize_t size) {
	PyObject *result;
	int i;
    result = truffle_invoke(PY_TRUFFLE_CEXT, "PyTruffle_Unicode_FromWchar", truffle_read_n_bytes((char *)u, size * Py_UNICODE_SIZE), Py_UNICODE_SIZE, ERROR_MARKER);
    if (result == ERROR_MARKER) {
    	return NULL;
    }
    return to_sulong(result);
}

PyObject* PyUnicode_FromObject(PyObject* o) {
     PyObject *result;
     result = truffle_invoke(PY_TRUFFLE_CEXT, "PyUnicode_FromObject", to_java(o), ERROR_MARKER);
     if (result == ERROR_MARKER) {
    	 return NULL;
     }
     return to_sulong(result);
}

Py_ssize_t PyUnicode_GetLength(PyObject *unicode) {
    return truffle_invoke_l(PY_TRUFFLE_CEXT, "PyUnicode_GetLength", to_java(unicode));
}

PyObject * PyUnicode_Concat(PyObject *left, PyObject *right) {
     PyObject *result;
     result = truffle_invoke(PY_TRUFFLE_CEXT, "PyUnicode_Concat", to_java(left), to_java(right), ERROR_MARKER);
     if (result == ERROR_MARKER) {
    	 return NULL;
     }
     return to_sulong(result);
}

PyObject * PyUnicode_FromEncodedObject(PyObject *obj, const char *encoding, const char *errors) {
     PyObject *result;
     result = truffle_invoke(PY_TRUFFLE_CEXT, "PyUnicode_FromEncodedObject", to_java(obj), truffle_read_string(encoding), truffle_read_string(errors), ERROR_MARKER);
     if (result == ERROR_MARKER) {
    	 return NULL;
     }

     // TODO buffer treatment
     return to_sulong(result);
}

void PyUnicode_InternInPlace(PyObject **s) {
     PyObject *interned = truffle_invoke(PY_TRUFFLE_CEXT, "PyUnicode_InternInPlace", to_java(*s));
     *s = to_sulong(interned);
}

// taken from CPython "Python/Objects/unicodeobject.c"
PyObject * PyUnicode_InternFromString(const char *cp) {
    PyObject *s = _PyUnicode_FromUTF8(cp);
    if (s == NULL) {
        return NULL;
    }
    PyUnicode_InternInPlace(&s);
    return s;
}

// taken from CPython "Python/Objects/unicodeobject.c"
char* PyUnicode_AsUTF8(PyObject *unicode) {
    return PyUnicode_AsUTF8AndSize(unicode, NULL);
}

char* PyUnicode_AsUTF8AndSize(PyObject *unicode, Py_ssize_t *psize) {
     PyObject *result;
     result = _PyUnicode_AsUTF8String(unicode, NULL);
     if (psize) {
    	 *psize = PyObject_Length(result);
     }
     return PyBytes_AsString(result);
}

PyObject* _PyUnicode_AsUTF8String(PyObject *unicode, const char *errors) {
     PyObject *result;
     void *jerrors = NULL;
     if (errors != NULL) {
    	 jerrors = truffle_read_string(errors);
     }
     result = truffle_invoke(PY_TRUFFLE_CEXT, "_PyUnicode_AsUTF8String", to_java(unicode), to_java(jerrors), ERROR_MARKER);
     if (result == ERROR_MARKER) {
    	 return NULL;
     }
     return to_sulong(result);
}

// taken from CPython "Python/Objects/unicodeobject.c"
PyObject * PyUnicode_AsUTF8String(PyObject *unicode) {
    return _PyUnicode_AsUTF8String(unicode, NULL);
}

PyObject * PyUnicode_DecodeUTF32(const char *s, Py_ssize_t size, const char *errors, int *byteorder) {
	PyObject *result;
	void *jerrors = NULL;
	if (errors != NULL) {
		jerrors = truffle_read_string(errors);
	}
	int bo = 0;
    if (byteorder != NULL) {
        bo = *byteorder;
    }
	result = truffle_invoke(PY_TRUFFLE_CEXT, "PyTruffle_Unicode_DecodeUTF32", truffle_read_n_bytes(s, size), to_java(jerrors), bo, ERROR_MARKER);
	return to_sulong(result);
}

Py_ssize_t PyUnicode_AsWideChar(PyObject *unicode, wchar_t *w, Py_ssize_t size) {
    PyObject* result;
    Py_ssize_t n;
    char* data;
	int i;
    if (unicode == NULL) {
        PyErr_BadInternalCall();
        return -1;
    }
	result = truffle_invoke(PY_TRUFFLE_CEXT, "PyTruffle_Unicode_AsWideChar", to_java(unicode), SIZEOF_WCHAR_T, size, ERROR_MARKER);
    if (result == ERROR_MARKER) {
    	return -1;
    }
    PyObject* n_result = to_sulong(result);
    data = PyBytes_AsString(n_result);
    n = PyBytes_Size(n_result);
    memcpy(w, data, n);
	return n / SIZEOF_WCHAR_T;
}

PyObject * _PyUnicode_AsLatin1String(PyObject *unicode, const char *errors) {
     PyObject *result;
     void *jerrors = NULL;
     if (errors != NULL) {
    	 jerrors = truffle_read_string(errors);
     }
     result = truffle_invoke(PY_TRUFFLE_CEXT, "_PyTruffle_Unicode_AsLatin1String", to_java(unicode), to_java(jerrors), ERROR_MARKER);
     if (result == ERROR_MARKER) {
    	 return NULL;
     }
     return to_sulong(result);
}

// taken from CPython "Python/Objects/unicodeobject.c"
PyObject* PyUnicode_AsLatin1String(PyObject *unicode) {
    return _PyUnicode_AsLatin1String(unicode, NULL);
}

PyObject * _PyUnicode_AsASCIIString(PyObject *unicode, const char *errors) {
     PyObject *result;
     void *jerrors = NULL;
     if (errors != NULL) {
    	 jerrors = truffle_read_string(errors);
     }
     result = truffle_invoke(PY_TRUFFLE_CEXT, "_PyTruffle_Unicode_AsASCIIString", to_java(unicode), to_java(jerrors), ERROR_MARKER);
     if (result == ERROR_MARKER) {
    	 return NULL;
     }
     return to_sulong(result);
}

// taken from CPython "Python/Objects/unicodeobject.c"
PyObject * PyUnicode_AsASCIIString(PyObject *unicode) {
    return _PyUnicode_AsASCIIString(unicode, NULL);
}

PyObject * PyUnicode_Format(PyObject *format, PyObject *args) {
	if (format == NULL || args == NULL) {
        PyErr_BadInternalCall();
        return NULL;
    }
    PyObject *result = truffle_invoke(PY_TRUFFLE_CEXT, "PyUnicode_Format", to_java(format), to_java(args), ERROR_MARKER);
    if (result == ERROR_MARKER) {
    	return NULL;
    }
    return to_sulong(result);
}
