/*
 * Copyright (c) 2018, 2019, Oracle and/or its affiliates. All rights reserved.
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

PyTypeObject PyUnicode_Type = PY_TRUFFLE_TYPE("str", &PyType_Type, Py_TPFLAGS_DEFAULT | Py_TPFLAGS_BASETYPE | Py_TPFLAGS_UNICODE_SUBCLASS, sizeof(PyUnicodeObject));

/* The empty Unicode object is shared to improve performance. */
static PyObject *unicode_empty = NULL;

#define _Py_RETURN_UNICODE_EMPTY()                      \
    do {                                                \
        _Py_INCREF_UNICODE_EMPTY();                     \
        return unicode_empty;                           \
    } while (0)

// partially taken from CPython "Objects/unicodeobject.c"
const unsigned char _Py_ascii_whitespace[] = {
    0, 0, 0, 0, 0, 0, 0, 0,
/*     case 0x0009: * CHARACTER TABULATION */
/*     case 0x000A: * LINE FEED */
/*     case 0x000B: * LINE TABULATION */
/*     case 0x000C: * FORM FEED */
/*     case 0x000D: * CARRIAGE RETURN */
    0, 1, 1, 1, 1, 1, 0, 0,
    0, 0, 0, 0, 0, 0, 0, 0,
/*     case 0x001C: * FILE SEPARATOR */
/*     case 0x001D: * GROUP SEPARATOR */
/*     case 0x001E: * RECORD SEPARATOR */
/*     case 0x001F: * UNIT SEPARATOR */
    0, 0, 0, 0, 1, 1, 1, 1,
/*     case 0x0020: * SPACE */
    1, 0, 0, 0, 0, 0, 0, 0,
    0, 0, 0, 0, 0, 0, 0, 0,
    0, 0, 0, 0, 0, 0, 0, 0,
    0, 0, 0, 0, 0, 0, 0, 0,

    0, 0, 0, 0, 0, 0, 0, 0,
    0, 0, 0, 0, 0, 0, 0, 0,
    0, 0, 0, 0, 0, 0, 0, 0,
    0, 0, 0, 0, 0, 0, 0, 0,
    0, 0, 0, 0, 0, 0, 0, 0,
    0, 0, 0, 0, 0, 0, 0, 0,
    0, 0, 0, 0, 0, 0, 0, 0,
    0, 0, 0, 0, 0, 0, 0, 0
};

static PyObject * _PyUnicode_FromUCS1(const Py_UCS1 *s, Py_ssize_t size);
static PyObject * _PyUnicode_FromUCS2(const Py_UCS2 *s, Py_ssize_t size);
static PyObject * _PyUnicode_FromUCS4(const Py_UCS4 *s, Py_ssize_t size);

// partially taken from CPython "Objects/unicodeobject.c"
static Py_ssize_t unicode_aswidechar(PyObject *unicode, wchar_t *w, Py_ssize_t size) {
    Py_ssize_t res;
    const wchar_t *wstr;

    wstr = PyUnicode_AsUnicodeAndSize(unicode, &res);
    if (wstr == NULL) {
        return -1;
    }

    if (w != NULL) {
        if (size > res)
            size = res + 1;
        else
            res = size;
        bytes_copy2mem((char*)w, (char*)wstr, size * SIZEOF_WCHAR_T);
        return res;
    }
    else {
        return res + 1;
    }
}

PyObject* PyUnicode_FromString(const char* o) {
    return to_sulong(polyglot_from_string(o, SRC_CS));
}

static PyObject* _PyUnicode_FromUTF8(const char* o) {
    return to_sulong(polyglot_from_string(o, "utf-8"));
}

PyObject * PyUnicode_FromStringAndSize(const char *u, Py_ssize_t size) {
    if (size < 0) {
        PyErr_SetString(PyExc_SystemError, "Negative size passed to PyUnicode_FromStringAndSize");
        return NULL;
    }
    return to_sulong(polyglot_from_string_n(u, size, SRC_CS));
}

#define AS_I64(__arg__) (polyglot_fits_in_i64((__arg__)) ? polyglot_as_i64((__arg__)) : (int64_t)(__arg__))

MUST_INLINE PyObject* PyTruffle_Unicode_FromFormat(const char *fmt, va_list va, void **args, int argc) {
    size_t fmt_size = strlen(fmt) + 1;
    char* fmtcpy = strdup(fmt);
    char* c = fmtcpy;
    int use_valist = args == NULL;

    int remaining_space = 2047;
    char* buffer = (char*)calloc(sizeof(char), remaining_space + 1);
    char* full_buffer = buffer;

    void *variable = NULL;
    char *allocated = NULL; // points to the same as variable, if it has to be free'd

    while (c[0]) {
        if (c[0] == '%') {
            // we've reached the next directive, write until here
            c[0] = '\0';
            int bytes_written;
            if (variable != NULL) {
                bytes_written = snprintf(buffer, remaining_space, fmtcpy, AS_I64(variable));
                if (allocated != NULL) {
                    free(allocated);
                    allocated = NULL;
                }
                variable = NULL;
            } else if (use_valist) {
                bytes_written = vsnprintf(buffer, remaining_space, fmtcpy, va);
            } else {
                strncpy(buffer, fmtcpy, remaining_space);
                bytes_written = strlen(fmtcpy);
            }
            remaining_space -= bytes_written;
            buffer += bytes_written;
            fmtcpy = c;
            c[0] = '%';

            // now decide if we need to do something special with this directive
            PyObject* (*converter)(PyObject*) = NULL;
            switch (c[1]) {
            case 'A':
                // The conversion cases, these all use a function to convert the
                // PyObject* to a char* and they fall through
                converter = PyObject_ASCII;
            case 'U':
                if (converter == NULL) converter = PyObject_Str;
            case 'S':
                if (converter == NULL) converter = PyObject_Str;
            case 'R':
                if (converter == NULL) converter = PyObject_Repr;
                c[1] = 's';
                allocated = variable = as_char_pointer(converter(use_valist ? va_arg(va, PyObject*) : (PyObject*)(args[argc++])));
                break;
            case '%':
                // literal %
                break;
            case 'c':
                // This case should just treat it's argument as an integer
                c[1] = 'd';
            default:
                // if we're reading args from a void* array, read it now,
                // otherwise there's nothing to do
                if (args != NULL) {
                    variable = args[argc++];
                }
            }
            // skip over next char, we checked it
            c += 1;
        }
        c += 1;
    }

    // write the remaining buffer
    if (variable != NULL) {
        snprintf(buffer, remaining_space, fmtcpy, AS_I64(variable));
        if (allocated) {
            free(allocated);
        }
    } else if (use_valist) {
        vsnprintf(buffer, remaining_space, fmtcpy, va);
    } else {
        strncpy(buffer, fmtcpy, remaining_space);
    }

    PyObject* result = PyUnicode_FromString(full_buffer);
    free(full_buffer);
    return result;
}

PyObject* PyUnicode_FromFormatV(const char* format, va_list va) {
    return PyTruffle_Unicode_FromFormat(format, va, NULL, 0);
}

NO_INLINE
PyObject* PyUnicode_FromFormat(const char* format, ...) {
    CallWithPolyglotArgs(PyObject* result, format, 1, PyTruffle_Unicode_FromFormat, format);
    return result;
}

PyObject * PyUnicode_FromUnicode(const Py_UNICODE *u, Py_ssize_t size) {
    if (u == NULL) {
        return to_sulong(polyglot_from_string_n("", 0, "utf-16le"));
    }

    switch(Py_UNICODE_SIZE) {
    case 2:
        return to_sulong(polyglot_from_string_n((const char*)u, size*2, "utf-16le"));
    case 4:
        return to_sulong(polyglot_from_string_n((const char*)u, size*4, "utf-32le"));
    }
    return NULL;
}

UPCALL_ID(PyUnicode_FromObject);
PyObject* PyUnicode_FromObject(PyObject* o) {
    return UPCALL_CEXT_O(_jls_PyUnicode_FromObject, native_to_java(o));
}

UPCALL_ID(PyUnicode_GetLength);
Py_ssize_t PyUnicode_GetLength(PyObject *unicode) {
    return UPCALL_CEXT_L(_jls_PyUnicode_GetLength, native_to_java(unicode));
}

UPCALL_ID(PyUnicode_Concat);
PyObject * PyUnicode_Concat(PyObject *left, PyObject *right) {
    return UPCALL_CEXT_O(_jls_PyUnicode_Concat, native_to_java(left), native_to_java(right));
}

UPCALL_ID(PyUnicode_FromEncodedObject);
PyObject * PyUnicode_FromEncodedObject(PyObject *obj, const char *encoding, const char *errors) {
    // TODO buffer treatment
    return UPCALL_CEXT_O(_jls_PyUnicode_FromEncodedObject, native_to_java(obj), polyglot_from_string(encoding, SRC_CS), polyglot_from_string(errors, SRC_CS));
}

UPCALL_ID(PyUnicode_InternInPlace);
void PyUnicode_InternInPlace(PyObject **s) {
    *s = UPCALL_CEXT_O(_jls_PyUnicode_InternInPlace, native_to_java(*s));
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
const char* PyUnicode_AsUTF8(PyObject *unicode) {
    return PyUnicode_AsUTF8AndSize(unicode, NULL);
}

const char* PyUnicode_AsUTF8AndSize(PyObject *unicode, Py_ssize_t *psize) {
    PyObject *result;
    result = _PyUnicode_AsUTF8String(unicode, NULL);
    if (psize) {
        *psize = PyObject_Length(result);
    }
    return PyBytes_AsString(result);
}

UPCALL_ID(_PyUnicode_AsUTF8String);
PyObject* _PyUnicode_AsUTF8String(PyObject *unicode, const char *errors) {
    void *jerrors = errors != NULL ? polyglot_from_string(errors, SRC_CS) : NULL;
    return UPCALL_CEXT_O(_jls__PyUnicode_AsUTF8String, native_to_java(unicode), native_to_java(jerrors), NULL);
}

// taken from CPython "Python/Objects/unicodeobject.c"
PyObject * PyUnicode_AsUTF8String(PyObject *unicode) {
    return _PyUnicode_AsUTF8String(unicode, NULL);
}

PyObject * PyUnicode_DecodeUTF32(const char *s, Py_ssize_t size, const char *errors, int *byteorder) {
    PyObject *result;
    void *jerrors = errors != NULL ? polyglot_from_string(errors, SRC_CS) : NULL;
    int bo = byteorder != NULL ? *byteorder : 0;
    return polyglot_invoke(PY_TRUFFLE_CEXT, "PyTruffle_Unicode_DecodeUTF32", s, size, native_to_java(jerrors), bo, NULL);
}

Py_ssize_t PyUnicode_AsWideChar(PyObject *unicode, wchar_t *w, Py_ssize_t size) {
    Py_ssize_t n;
    char* data;
    int i;
    if (w == NULL) {
        return PyObject_Size(unicode)+1;
    }
    if (unicode == NULL) {
        PyErr_BadInternalCall();
        return -1;
    }
    return unicode_aswidechar(unicode, w, size);
}

UPCALL_ID(_PyTruffle_Unicode_AsLatin1String);
PyObject* _PyUnicode_AsLatin1String(PyObject *unicode, const char *errors) {
    void *jerrors = errors != NULL ? polyglot_from_string(errors, SRC_CS) : NULL;
    return UPCALL_CEXT_O(_jls__PyTruffle_Unicode_AsLatin1String, native_to_java(unicode), native_to_java(jerrors), ERROR_MARKER);
}

// taken from CPython "Python/Objects/unicodeobject.c"
PyObject* PyUnicode_AsLatin1String(PyObject *unicode) {
    return _PyUnicode_AsLatin1String(unicode, NULL);
}

UPCALL_ID(_PyTruffle_Unicode_AsASCIIString);
PyObject* _PyUnicode_AsASCIIString(PyObject *unicode, const char *errors) {
    void *jerrors = errors != NULL ? polyglot_from_string(errors, SRC_CS) : NULL;
    return UPCALL_CEXT_O(_jls__PyTruffle_Unicode_AsASCIIString, native_to_java(unicode), native_to_java(jerrors), ERROR_MARKER);
}

// taken from CPython "Python/Objects/unicodeobject.c"
PyObject* PyUnicode_AsASCIIString(PyObject *unicode) {
    return _PyUnicode_AsASCIIString(unicode, NULL);
}

UPCALL_ID(PyUnicode_Format);
PyObject* PyUnicode_Format(PyObject *format, PyObject *args) {
    if (format == NULL || args == NULL) {
        PyErr_BadInternalCall();
        return NULL;
    }
    return UPCALL_CEXT_O(_jls_PyUnicode_Format, native_to_java(format), native_to_java(args));
}

Py_UNICODE* PyUnicode_AsUnicode(PyObject *unicode) {
    Py_ssize_t size = 0;
    return PyUnicode_AsUnicodeAndSize(unicode, &size);
}

UPCALL_ID(PyTruffle_Unicode_AsWideChar);
Py_UNICODE* PyUnicode_AsUnicodeAndSize(PyObject *unicode, Py_ssize_t *size) {
    PyObject* bytes = UPCALL_CEXT_O(_jls_PyTruffle_Unicode_AsWideChar, native_to_java(unicode), Py_UNICODE_SIZE, native_to_java(Py_None), ERROR_MARKER);
    if (bytes != NULL) {
        // exclude null terminator at the end
        *size = PyBytes_Size(bytes) / Py_UNICODE_SIZE;
        return (Py_UNICODE*) PyBytes_AsString(bytes);
    }
    return NULL;
}

int _PyUnicode_Ready(PyObject *unicode) {
    // TODO(fa) anything we need to initialize here?
    return 0;
}

UPCALL_ID(PyUnicode_FindChar);
Py_ssize_t PyUnicode_FindChar(PyObject *str, Py_UCS4 ch, Py_ssize_t start, Py_ssize_t end, int direction) {
    return UPCALL_CEXT_L(_jls_PyUnicode_FindChar, native_to_java(str), ch, start, end, direction);
}

UPCALL_ID(PyUnicode_Substring);
PyObject* PyUnicode_Substring(PyObject *self, Py_ssize_t start, Py_ssize_t end) {
    return UPCALL_CEXT_O(_jls_PyUnicode_Substring, native_to_java(self), start, end);
}

UPCALL_ID(PyUnicode_Join);
PyObject* PyUnicode_Join(PyObject *separator, PyObject *seq) {
    return UPCALL_CEXT_O(_jls_PyUnicode_Join, native_to_java(separator), native_to_java(seq));
}

PyObject* PyUnicode_New(Py_ssize_t size, Py_UCS4 maxchar) {
    return to_sulong(polyglot_from_string("", "ascii"));
}

UPCALL_ID(PyUnicode_Compare);
int PyUnicode_Compare(PyObject *left, PyObject *right) {
	return UPCALL_CEXT_I(_jls_PyUnicode_Compare, native_to_java(left), native_to_java(right));
}

int _PyUnicode_EqualToASCIIString( PyObject *left, const char *right) {
	return UPCALL_CEXT_I(_jls_PyUnicode_Compare, native_to_java(left), polyglot_from_string(right, SRC_CS)) == 0;
}

static PyObject* _PyUnicode_FromUCS1(const Py_UCS1* u, Py_ssize_t size) {
	// CPython assumes latin1 when decoding an UCS1 array
	return polyglot_from_string((const char *) u, "ISO-8859-1");
}

UPCALL_ID(PyTruffle_Unicode_FromWchar);
static PyObject* _PyUnicode_FromUCS2(const Py_UCS2 *u, Py_ssize_t size) {
	return UPCALL_CEXT_O(_jls_PyTruffle_Unicode_FromWchar, polyglot_from_i16_array(u, size), 2, NULL);
}

static PyObject* _PyUnicode_FromUCS4(const Py_UCS4 *u, Py_ssize_t size) {
	return UPCALL_CEXT_O(_jls_PyTruffle_Unicode_FromWchar, polyglot_from_i32_array(u, size), 4, NULL);
}

// taken from CPython "Python/Objects/unicodeobject.c"
PyObject*
PyUnicode_FromKindAndData(int kind, const void *buffer, Py_ssize_t size)
{
    if (size < 0) {
        PyErr_SetString(PyExc_ValueError, "size must be positive");
        return NULL;
    }
    switch (kind) {
    case PyUnicode_1BYTE_KIND:
        return _PyUnicode_FromUCS1(buffer, size);
    case PyUnicode_2BYTE_KIND:
        return _PyUnicode_FromUCS2(buffer, size);
    case PyUnicode_4BYTE_KIND:
        return _PyUnicode_FromUCS4(buffer, size);
    default:
        PyErr_SetString(PyExc_SystemError, "invalid kind");
        return NULL;
    }
}

PyObject * PyUnicode_FromOrdinal(int ordinal) {
    return UPCALL_O(PY_BUILTIN, polyglot_from_string("chr", SRC_CS), ordinal);
}

// taken from CPython "Python/Objects/unicodeobject.c"
PyObject * PyUnicode_DecodeUTF8(const char *s, Py_ssize_t size, const char *errors) {
    return PyUnicode_DecodeUTF8Stateful(s, size, errors, NULL);
}

UPCALL_ID(PyUnicode_DecodeUTF8Stateful);
PyObject * PyUnicode_DecodeUTF8Stateful(const char *s, Py_ssize_t size, const char *errors, Py_ssize_t *consumed) {
	PyObject* result = UPCALL_CEXT_O(_jls_PyUnicode_DecodeUTF8Stateful, polyglot_from_i8_array(s, size), polyglot_from_string(errors, SRC_CS), consumed != NULL ? 1 : 0);
	if (result != NULL) {
		if (consumed != NULL) {
			*consumed = PyLong_AsSsize_t(PyTuple_GetItem(result, 1));
		}
		return PyTuple_GetItem(result, 0);
	}
	PyErr_SetString(PyExc_SystemError, "expected tuple but got NULL");
	return NULL;
}

// partially taken from CPython "Python/Objects/unicodeobject.c"
PyObject * _PyUnicode_FromId(_Py_Identifier *id) {
    if (!id->object) {
        id->object = PyUnicode_DecodeUTF8Stateful(id->string, strlen(id->string), NULL, NULL);
        if (!id->object) {
            return NULL;
        }
        PyUnicode_InternInPlace(&id->object);
        assert(!id->next);
        id->next = NULL;
    }
    return id->object;
}

UPCALL_ID(PyUnicode_AsUnicodeEscapeString);
PyObject * PyUnicode_AsUnicodeEscapeString(PyObject *unicode) {
	return UPCALL_CEXT_O(_jls_PyUnicode_AsUnicodeEscapeString, native_to_java(unicode));
}

UPCALL_ID(PyUnicode_Decode);
PyObject * PyUnicode_Decode(const char *s, Py_ssize_t size, const char *encoding, const char *errors) {
	if (errors == NULL) {
		errors = "strict";
	}
    if (encoding == NULL) {
        return PyUnicode_DecodeUTF8Stateful(s, size, errors, NULL);
    }
	return UPCALL_CEXT_O(_jls_PyUnicode_Decode, s, size, polyglot_from_string(encoding, SRC_CS), polyglot_from_string(errors, SRC_CS));
}

PyObject * PyUnicode_DecodeASCII(const char *s, Py_ssize_t size, const char *errors) {
	return PyUnicode_Decode(s, size, "ascii", errors);
}
