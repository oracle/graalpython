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

#include <stdarg.h>
#include <stddef.h>

// taken from CPython "Objects/bytesobject.c"
#define PyBytesObject_SIZE (offsetof(PyBytesObject, ob_sval) + 1)

PyTypeObject PyBytes_Type = PY_TRUFFLE_TYPE("bytes", &PyType_Type, Py_TPFLAGS_DEFAULT | Py_TPFLAGS_BASETYPE | Py_TPFLAGS_BYTES_SUBCLASS, PyBytesObject_SIZE);

typedef PyObject* (*fromStringAndSize_fun_t)(int8_t* str, int64_t sz);

UPCALL_ID(PyBytes_FromStringAndSize);
UPCALL_ID(PyTruffle_Bytes_EmptyWithCapacity);
PyObject* PyBytes_FromStringAndSize(const char* str, Py_ssize_t sz) {
	if (str != NULL) {
		return ((fromStringAndSize_fun_t)_jls_PyBytes_FromStringAndSize)(polyglot_from_i8_array(str, sz), sz);
	}
	return UPCALL_CEXT_O(_jls_PyTruffle_Bytes_EmptyWithCapacity, sz);
}

PyObject * PyBytes_FromString(const char *str) {
	if (str != NULL) {
		return ((fromStringAndSize_fun_t)_jls_PyBytes_FromStringAndSize)(polyglot_from_i8_array(str, strlen(str)), strlen(str));
	}
	return UPCALL_CEXT_O(_jls_PyTruffle_Bytes_EmptyWithCapacity, 0);
}

UPCALL_ID(PyTruffle_Bytes_AsString);
char* PyBytes_AsString(PyObject *obj) {
    return (char*)(UPCALL_CEXT_NOCAST(_jls_PyTruffle_Bytes_AsString, native_to_java(obj), ERROR_MARKER));
}

UPCALL_ID(PyBytes_AsStringCheckEmbeddedNull);
int PyBytes_AsStringAndSize(PyObject *obj, char **s, Py_ssize_t *len) {
    setlocale(LC_ALL, NULL);
    const char* encoding = nl_langinfo(CODESET);
    PyObject *result = UPCALL_CEXT_O(_jls_PyBytes_AsStringCheckEmbeddedNull, native_to_java(obj), polyglot_from_string(encoding, SRC_CS));
    if(result == NULL) {
        return -1;
    }

    *s = (char *)as_char_pointer(result);

    if (len != NULL) {
        *len = polyglot_as_i64(polyglot_invoke(PY_TRUFFLE_CEXT, "PyTruffle_Object_LEN", native_to_java(obj)));
    }

    return 0;
}

PyObject * PyBytes_FromFormat(const char *format, ...) {
    PyObject* ret;
    va_list vargs;

#ifdef HAVE_STDARG_PROTOTYPES
    va_start(vargs, format);
#else
    va_start(vargs);
#endif
    ret = PyBytes_FromFormatV(format, vargs);
    va_end(vargs);
    return ret;
}


UPCALL_ID(PyBytes_FromFormat);
UPCALL_TYPED_ID(PyTuple_SetItem, setitem_fun_t);
PyObject* PyBytes_FromFormatV(const char *format, va_list vargs) {
    /* Unfortunately, we need to know the expected types of the arguments before we can do an upcall. */
    char *s;
    const char *f = format;
    int longflag;
    int size_tflag;
    char buffer[1024];
    int buffer_pos = 0;

#define SETSPEC(__buffer, __pos, __spec) do {\
                                             (__buffer)[(__pos)++] = (__spec);\
                                         } while(0)

    for(int i=0; i < sizeof(buffer); i++) {
    	buffer[i] = '\0';
    }

    for (int i=0; f[i] != '\0'; i++) {
        if (f[i] != '%') {
            continue;
        }

        i++;

        /* ignore the width (ex: 10 in "%10s") */
        while (Py_ISDIGIT(f[i])) {
            i++;
        }

        /* parse the precision (ex: 10 in "%.10s") */
        if (f[i] == '.') {
            i++;
            while (Py_ISDIGIT(f[i])) {
            	i++;
            }
        }

        while (f[i] && f[i] != '%' && !Py_ISALPHA(f[i])) {
            i++;
        }

        /* handle the long flag ('l'), but only for %ld and %lu.
           others can be added when necessary. */
        longflag = 0;
        if (f[i] == 'l' && (f[i+1] == 'd' || f[i+1] == 'u')) {
            longflag = 1;
            i++;
        }

        /* handle the size_t flag ('z'). */
        size_tflag = 0;
        if (f[i] == 'z' && (f[i+1] == 'd' || f[i+1] == 'u')) {
            size_tflag = 1;
            i++;
        }

        switch (f[i]) {
        case 'c':
        	SETSPEC(buffer, buffer_pos, 'c');
            break;

        case 'd':
            if (longflag) {
            	SETSPEC(buffer, buffer_pos, 'D');
            } else if (size_tflag) {
        	   	SETSPEC(buffer, buffer_pos, 't');
            } else {
        	   	SETSPEC(buffer, buffer_pos, 'd');
            }
            assert(strlen(buffer) < sizeof(buffer));
            break;

        case 'u':
            if (longflag) {
        	   	SETSPEC(buffer, buffer_pos, 'U');
            } else if (size_tflag) {
        	   	SETSPEC(buffer, buffer_pos, 't');
            } else {
        	   	SETSPEC(buffer, buffer_pos, 'u');
            }
            assert(strlen(buffer) < sizeof(buffer));
            break;

        case 'i':
        	SETSPEC(buffer, buffer_pos, 'i');
            break;

        case 'x':
        	SETSPEC(buffer, buffer_pos, 'x');
            break;

        case 's':
        	SETSPEC(buffer, buffer_pos, 's');
            break;

        case 'p':
        	SETSPEC(buffer, buffer_pos, 'p');
            break;

        case '%':
            break;

        default:
            // TODO correctly handle this case
            return UPCALL_CEXT_O(_jls_PyBytes_FromFormat, polyglot_from_string(format, SRC_CS), f+i);
        }
    }


#define SETARG(__args, __i, __arg) _jls_PyTuple_SetItem(native_to_java(__args), (__i), (__arg))

    // do actual conversion using one-character type specifiers
    int conversions = strlen(buffer);
    PyObject* args = PyTuple_New(conversions);
    for (int i=0; i < conversions; i++) {
    	switch(buffer[i]) {
    	case 'c':
    	case 'i':
    	case 'x':
    	case 'd':
            SETARG(args, i, va_arg(vargs, int));
    		break;
    	case 'D':
            SETARG(args, i, va_arg(vargs, long));
    		break;
    	case 'u':
            SETARG(args, i, va_arg(vargs, unsigned int));
    		break;
    	case 'U':
            SETARG(args, i, va_arg(vargs, unsigned long));
    		break;
    	case 't':
            SETARG(args, i, va_arg(vargs, size_t));
            break;
    	case 's':
            SETARG(args, i, polyglot_from_string(va_arg(vargs, const char*), SRC_CS));
    		break;
    	case 'p':
            SETARG(args, i, native_to_java(va_arg(vargs, void*)));
    		break;
    	}
    }
    return UPCALL_CEXT_O(_jls_PyBytes_FromFormat, polyglot_from_string(format, SRC_CS), native_to_java(args));
}

UPCALL_ID(PyBytes_Concat);
void PyBytes_Concat(PyObject **bytes, PyObject *newpart) {
    *bytes = UPCALL_CEXT_O(_jls_PyBytes_Concat, native_to_java(*bytes), native_to_java(newpart));
}

void PyBytes_ConcatAndDel(PyObject **bytes, PyObject *newpart) {
    PyBytes_Concat(bytes, newpart);
    Py_DECREF(newpart);
}

UPCALL_ID(PyBytes_Size);
Py_ssize_t PyBytes_Size(PyObject *bytes) {
    return UPCALL_CEXT_L(_jls_PyBytes_Size, native_to_java(bytes));
}

int bytes_buffer_getbuffer(PyBytesObject *self, Py_buffer *view, int flags) {
    return PyBuffer_FillInfo(view, (PyObject*)self, (void *)self->ob_sval, Py_SIZE(self), 1, flags);
}

int bytes_copy2mem(char* target, char* source, size_t nbytes) {
    size_t i;
    for (i = 0; i < nbytes; i++) {
        target[i] = source[i];
    }
    return 0;
}

UPCALL_ID(PyBytes_Join);
PyObject *_PyBytes_Join(PyObject *sep, PyObject *x) {
    return UPCALL_CEXT_O(_jls_PyBytes_Join, native_to_java(sep), native_to_java(x));
}

UPCALL_ID(_PyBytes_Resize);
int _PyBytes_Resize(PyObject **pv, Py_ssize_t newsize) {
    return UPCALL_CEXT_I(_jls__PyBytes_Resize, native_to_java(*pv), newsize);
}

UPCALL_ID(PyBytes_FromObject);
PyObject * PyBytes_FromObject(PyObject *x) {
    return UPCALL_CEXT_O(_jls_PyBytes_FromObject, native_to_java(x));
}
