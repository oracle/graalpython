/*
 * Copyright (c) 2018, 2023, Oracle and/or its affiliates. All rights reserved.
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


PyObject* PyBytes_FromStringAndSize(const char* str, Py_ssize_t sz) {
    if (sz < 0) {
        PyErr_SetString(PyExc_SystemError, "Negative size passed to PyBytes_FromStringAndSize");
        return NULL;
    }
    if (str != NULL) {
        return GraalPyTruffleBytes_FromStringAndSize(str, sz);
    }
    return GraalPyTruffle_Bytes_EmptyWithCapacity(sz);
}

PyObject* PyByteArray_FromStringAndSize(const char* str, Py_ssize_t sz) {
    if (sz < 0) {
        PyErr_SetString(PyExc_SystemError, "Negative size passed to PyByteArray_FromStringAndSize");
        return NULL;
    }
    if (str != NULL) {
        return GraalPyTruffleByteArray_FromStringAndSize(str, sz);
    }
    return GraalPyTruffle_ByteArray_EmptyWithCapacity(sz);
}

PyObject * PyBytes_FromString(const char *str) {
	if (str != NULL) {
		return GraalPyTruffleBytes_FromStringAndSize(str, strlen(str));
	}
	return GraalPyTruffle_Bytes_EmptyWithCapacity(0);
}


int PyBytes_AsStringAndSize(PyObject *obj, char **s, Py_ssize_t *len) {
    *s = (char*)PyBytes_AsString(obj);
    if (*s == NULL) {
        return -1;
    }
    if (len != NULL) {
        *len = GraalPyBytes_Size(obj);
        return 0;
    } else {
    	return GraalPyTruffle_Bytes_CheckEmbeddedNull(obj);
    }
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
        	Py_FatalError("unhandled case in PyBytes_FromFormatV");
        }
    }

    // do actual conversion using one-character type specifiers
    int conversions = strlen(buffer);
    PyObject* args = PyTuple_New(conversions);
    for (int i=0; i < conversions; i++) {
    	PyObject* entry = NULL;
    	switch(buffer[i]) {
    	case 'c':
    	case 'i':
    	case 'x':
    	case 'd':
            entry = PyLong_FromLong(va_arg(vargs, int));
    		break;
    	case 'D':
    		entry = PyLong_FromLong(va_arg(vargs, long));
    		break;
    	case 'u':
    		entry = PyLong_FromUnsignedLong(va_arg(vargs, unsigned int));
    		break;
    	case 'U':
    		entry = PyLong_FromUnsignedLong(va_arg(vargs, unsigned long));
    		break;
    	case 't':
    		entry = PyLong_FromSize_t(va_arg(vargs, size_t));
            break;
    	case 's':
    		entry = PyUnicode_FromString(va_arg(vargs, const char*));
    		break;
    	case 'p':
    		entry = PyLong_FromVoidPtr(va_arg(vargs, void*));
    		break;
    	}
    	GraalPyTuple_SetItem(args, i, entry);
    }
    PyObject* result = GraalPyTruffleBytes_FromFormat(truffleString(format), args);
  	Py_DecRef(args);
    return result;
}

void PyBytes_Concat(PyObject **bytes, PyObject *newpart) {
    *bytes = GraalPyTruffleBytes_Concat(*bytes, newpart);
}

void PyBytes_ConcatAndDel(PyObject **bytes, PyObject *newpart) {
    PyBytes_Concat(bytes, newpart);
    Py_DECREF(newpart);
}

int bytes_buffer_getbuffer(PyBytesObject *self, Py_buffer *view, int flags) {
    return PyBuffer_FillInfo(view, (PyObject*)self, (void *)PyBytes_AS_STRING(self), Py_SIZE(self), 1, flags);
}

int bytes_copy2mem(char* target, char* source, size_t nbytes) {
    size_t i;
    for (i = 0; i < nbytes; i++) {
        target[i] = source[i];
    }
    return 0;
}

int _PyBytes_Resize(PyObject **pv, Py_ssize_t newsize) {
    return Graal_PyTruffleBytes_Resize(*pv, newsize);
}

#define OVERALLOCATE_FACTOR 4

// Taken from CPython
void
_PyBytesWriter_Init(_PyBytesWriter *writer)
{
    /* Set all attributes before small_buffer to 0 */
    memset(writer, 0, offsetof(_PyBytesWriter, small_buffer));
#ifndef NDEBUG
    memset(writer->small_buffer, PYMEM_CLEANBYTE,
           sizeof(writer->small_buffer));
#endif
}

// Taken from CPython
void
_PyBytesWriter_Dealloc(_PyBytesWriter *writer)
{
    Py_CLEAR(writer->buffer);
}

// Taken from CPython
Py_LOCAL_INLINE(char*)
_PyBytesWriter_AsString(_PyBytesWriter *writer)
{
    if (writer->use_small_buffer) {
        assert(writer->buffer == NULL);
        return writer->small_buffer;
    }
    else if (writer->use_bytearray) {
        assert(writer->buffer != NULL);
        return PyByteArray_AS_STRING(writer->buffer);
    }
    else {
        assert(writer->buffer != NULL);
        return PyBytes_AS_STRING(writer->buffer);
    }
}

// Taken from CPython
Py_LOCAL_INLINE(Py_ssize_t)
_PyBytesWriter_GetSize(_PyBytesWriter *writer, char *str)
{
    const char *start = _PyBytesWriter_AsString(writer);
    assert(str != NULL);
    assert(str >= start);
    assert(str - start <= writer->allocated);
    return str - start;
}

// Taken from CPython
void*
_PyBytesWriter_Resize(_PyBytesWriter *writer, void *str, Py_ssize_t size)
{
    Py_ssize_t allocated, pos;

    assert(_PyBytesWriter_CheckConsistency(writer, str));
    assert(writer->allocated < size);

    allocated = size;
    if (writer->overallocate
        && allocated <= (PY_SSIZE_T_MAX - allocated / OVERALLOCATE_FACTOR)) {
        /* overallocate to limit the number of realloc() */
        allocated += allocated / OVERALLOCATE_FACTOR;
    }

    pos = _PyBytesWriter_GetSize(writer, str);
    if (!writer->use_small_buffer) {
        if (writer->use_bytearray) {
            if (PyByteArray_Resize(writer->buffer, allocated))
                goto error;
            /* writer->allocated can be smaller than writer->buffer->ob_alloc,
               but we cannot use ob_alloc because bytes may need to be moved
               to use the whole buffer. bytearray uses an internal optimization
               to avoid moving or copying bytes when bytes are removed at the
               beginning (ex: del bytearray[:1]). */
        }
        else {
            if (_PyBytes_Resize(&writer->buffer, allocated))
                goto error;
        }
    }
    else {
        /* convert from stack buffer to bytes object buffer */
        assert(writer->buffer == NULL);

        if (writer->use_bytearray)
            writer->buffer = PyByteArray_FromStringAndSize(NULL, allocated);
        else
            writer->buffer = PyBytes_FromStringAndSize(NULL, allocated);
        if (writer->buffer == NULL)
            goto error;

        if (pos != 0) {
            char *dest;
            if (writer->use_bytearray)
                dest = PyByteArray_AS_STRING(writer->buffer);
            else
                dest = PyBytes_AS_STRING(writer->buffer);
            memcpy(dest,
                      writer->small_buffer,
                      pos);
        }

        writer->use_small_buffer = 0;
#ifndef NDEBUG
        memset(writer->small_buffer, PYMEM_CLEANBYTE,
               sizeof(writer->small_buffer));
#endif
    }
    writer->allocated = allocated;

    str = _PyBytesWriter_AsString(writer) + pos;
    assert(_PyBytesWriter_CheckConsistency(writer, str));
    return str;

error:
    _PyBytesWriter_Dealloc(writer);
    return NULL;
}

// Taken from CPython
void*
_PyBytesWriter_Prepare(_PyBytesWriter *writer, void *str, Py_ssize_t size)
{
    Py_ssize_t new_min_size;

    assert(_PyBytesWriter_CheckConsistency(writer, str));
    assert(size >= 0);

    if (size == 0) {
        /* nothing to do */
        return str;
    }

    if (writer->min_size > PY_SSIZE_T_MAX - size) {
        PyErr_NoMemory();
        _PyBytesWriter_Dealloc(writer);
        return NULL;
    }
    new_min_size = writer->min_size + size;

    if (new_min_size > writer->allocated)
        str = _PyBytesWriter_Resize(writer, str, new_min_size);

    writer->min_size = new_min_size;
    return str;
}

// Taken from CPython
void*
_PyBytesWriter_Alloc(_PyBytesWriter *writer, Py_ssize_t size)
{
    /* ensure that _PyBytesWriter_Alloc() is only called once */
    assert(writer->min_size == 0 && writer->buffer == NULL);
    assert(size >= 0);

    writer->use_small_buffer = 1;
#ifndef NDEBUG
    writer->allocated = sizeof(writer->small_buffer) - 1;
    /* In debug mode, don't use the full small buffer because it is less
       efficient than bytes and bytearray objects to detect buffer underflow
       and buffer overflow. Use 10 bytes of the small buffer to test also
       code using the smaller buffer in debug mode.

       Don't modify the _PyBytesWriter structure (use a shorter small buffer)
       in debug mode to also be able to detect stack overflow when running
       tests in debug mode. The _PyBytesWriter is large (more than 512 bytes),
       if Py_EnterRecursiveCall() is not used in deep C callback, we may hit a
       stack overflow. */
    writer->allocated = Py_MIN(writer->allocated, 10);
    /* _PyBytesWriter_CheckConsistency() requires the last byte to be 0,
       to detect buffer overflow */
    writer->small_buffer[writer->allocated] = 0;
#else
    writer->allocated = sizeof(writer->small_buffer);
#endif
    return _PyBytesWriter_Prepare(writer, writer->small_buffer, size);
}

// Taken from CPython
PyObject *
_PyBytesWriter_Finish(_PyBytesWriter *writer, void *str)
{
    Py_ssize_t size;
    PyObject *result;

    assert(_PyBytesWriter_CheckConsistency(writer, str));

    size = _PyBytesWriter_GetSize(writer, str);
    if (size == 0 && !writer->use_bytearray) {
        Py_CLEAR(writer->buffer);
        /* Get the empty byte string singleton */
        result = PyBytes_FromStringAndSize(NULL, 0);
    }
    else if (writer->use_small_buffer) {
        if (writer->use_bytearray) {
            result = PyByteArray_FromStringAndSize(writer->small_buffer, size);
        }
        else {
            result = PyBytes_FromStringAndSize(writer->small_buffer, size);
        }
    }
    else {
        result = writer->buffer;
        writer->buffer = NULL;

        if (size != writer->allocated) {
            if (writer->use_bytearray) {
                if (PyByteArray_Resize(result, size)) {
                    Py_DECREF(result);
                    return NULL;
                }
            }
            else {
                if (_PyBytes_Resize(&result, size)) {
                    assert(result == NULL);
                    return NULL;
                }
            }
        }
    }
    return result;
}

// Taken from CPython
void*
_PyBytesWriter_WriteBytes(_PyBytesWriter *writer, void *ptr,
                          const void *bytes, Py_ssize_t size)
{
    char *str = (char *)ptr;

    str = _PyBytesWriter_Prepare(writer, str, size);
    if (str == NULL)
        return NULL;

    memcpy(str, bytes, size);
    str += size;

    return str;
}

PyObject* bytes_subtype_new(PyTypeObject *type, int8_t* contents, Py_ssize_t n) {
    PyObject* bytes = type->tp_alloc(type, n);
    if (bytes != NULL) {
        char* dst = ((PyBytesObject*)bytes)->ob_sval;
        memcpy(dst, contents, n);
        dst[n] = '\0';
    }
    return bytes;
}
