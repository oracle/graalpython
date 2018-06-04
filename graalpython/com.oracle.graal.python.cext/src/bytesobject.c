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

#include <stdarg.h>
#include <stddef.h>

// taken from CPython "Objects/bytesobject.c"
#define PyBytesObject_SIZE (offsetof(PyBytesObject, ob_sval) + 1)

PyTypeObject PyBytes_Type = PY_TRUFFLE_TYPE("bytes", &PyType_Type, Py_TPFLAGS_DEFAULT | Py_TPFLAGS_BASETYPE | Py_TPFLAGS_BYTES_SUBCLASS, PyBytesObject_SIZE);

PyObject* PyBytes_FromStringAndSize(const char* str, Py_ssize_t sz) {
    setlocale(LC_ALL, NULL);
    const char* encoding = nl_langinfo(CODESET);
    return to_sulong(truffle_invoke(PY_TRUFFLE_CEXT, "PyBytes_FromStringAndSize", truffle_read_n_string(str, sz), truffle_read_string(encoding)));
}

PyObject * PyBytes_FromString(const char *str) {
	setlocale(LC_ALL, NULL);
	const char* encoding = nl_langinfo(CODESET);
	return to_sulong(truffle_invoke(PY_TRUFFLE_CEXT, "PyBytes_FromStringAndSize", truffle_read_string(str), truffle_read_string(encoding)));
}

char* PyBytes_AsString(PyObject *obj) {
	if (obj == NULL) {
		return NULL;
	}
	// returns a Java byte array
 	void *result = truffle_invoke(PY_TRUFFLE_CEXT, "PyTruffle_Bytes_AsString", to_java(obj), ERROR_MARKER);
 	if (result == ERROR_MARKER) {
 		return NULL;
 	}
 	if(truffle_has_size(result)) {
 		int n = truffle_get_size(result);
 		char *barr = (char *) malloc(n*sizeof(char));

 		// we need to read element-by-element
 		int i;
 		for(i=0; i < n; i++) {
 			barr[i] = truffle_read_idx_c(result, i);
 		}
 		return barr;
 	}
 	return NULL;
}

int PyBytes_AsStringAndSize(PyObject *obj, char **s, Py_ssize_t *len) {
	setlocale(LC_ALL, NULL);
	const char* encoding = nl_langinfo(CODESET);
 	PyObject *result = to_sulong(truffle_invoke(PY_TRUFFLE_CEXT, "PyBytes_AsStringCheckEmbeddedNull", to_java(obj), truffle_read_string(encoding)));
 	if(result == NULL) {
 		return -1;
 	}

 	*s = as_char_pointer(result);

    if (len != NULL) {
    	*len = truffle_invoke_i(PY_TRUFFLE_CEXT, "PyObject_LEN", to_java(obj));
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


PyObject *
PyBytes_FromFormatV(const char *format, va_list vargs) {
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
            return to_sulong(truffle_invoke(PY_TRUFFLE_CEXT, "PyBytes_FromFormat", truffle_read_string(format), f+i));

        }
    }


#define SETARG(__args, __i, __arg) truffle_invoke(PY_TRUFFLE_CEXT, "PyTuple_SetItem", (__args), (__i), (__arg));

    // do actual conversion using one-character type specifiers
    int conversions = strlen(buffer);
    PyObject* args = to_java(PyTuple_New(conversions));
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
            SETARG(args, i, truffle_read_string(va_arg(vargs, const char*)));
    		break;
    	case 'p':
            SETARG(args, i, to_java(va_arg(vargs, void*)));
    		break;
    	}
    }
    PyObject *res = to_sulong(truffle_invoke(PY_TRUFFLE_CEXT, "PyBytes_FromFormat", truffle_read_string(format), args));
    return res;
}

void PyBytes_Concat(PyObject **bytes, PyObject *newpart) {
    *bytes = to_sulong(truffle_invoke(PY_TRUFFLE_CEXT, "PyBytes_Concat", to_java(*bytes), to_java(newpart)));
}

void PyBytes_ConcatAndDel(PyObject **bytes, PyObject *newpart) {
    PyBytes_Concat(bytes, newpart);
    Py_DECREF(newpart);
}

Py_ssize_t PyBytes_Size(PyObject *bytes) {
	return truffle_invoke_i(PY_TRUFFLE_CEXT, "PyBytes_Size", to_java(bytes));
}

int bytes_buffer_getbuffer(PyBytesObject *self, Py_buffer *view, int flags) {
    return PyBuffer_FillInfo(view, (PyObject*)self, (void *)self->ob_sval, Py_SIZE(self), 1, flags);
}
