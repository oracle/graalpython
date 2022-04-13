/*
 * Copyright (c) 2018, 2021, Oracle and/or its affiliates. All rights reserved.
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

POLYGLOT_DECLARE_TYPE(wchar_t);
POLYGLOT_DECLARE_TYPE(Py_UCS1);
POLYGLOT_DECLARE_TYPE(Py_UCS2);
POLYGLOT_DECLARE_TYPE(Py_UCS4);

/* The empty Unicode object is shared to improve performance. */
static PyObject *unicode_empty = NULL;

#define MAX_UNICODE 0x10ffff

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

static MUST_INLINE void* convert_errors(const char *errors) {
    return errors != NULL ? polyglot_from_string(errors, SRC_CS) : polyglot_from_string("strict", SRC_CS);
}

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

#define _PyUnicode_UTF8(op)                             \
    (((PyCompactUnicodeObject*)(op))->utf8)
#define _PyUnicode_UTF8_LENGTH(op)                      \
    (((PyCompactUnicodeObject*)(op))->utf8_length)
#define _PyUnicode_WSTR(op)                             \
    (((PyASCIIObject*)(op))->wstr)
#define _PyUnicode_WSTR_LENGTH(op)                      \
    (((PyCompactUnicodeObject*)(op))->wstr_length)
#define _PyUnicode_LENGTH(op)                           \
    (((PyASCIIObject *)(op))->length)
#define _PyUnicode_STATE(op)                            \
    (((PyASCIIObject *)(op))->state)
#define _PyUnicode_DATA_ANY(op)                         \
    (((PyUnicodeObject*)(op))->data.any)

POLYGLOT_DECLARE_TYPE(PyUnicodeObject);

PyUnicodeObject* unicode_subtype_new(PyTypeObject *type, PyObject *unicode) {
    PyObject *self;
    Py_ssize_t length, char_size;
    int share_wstr, share_utf8;
    unsigned int kind;
    void *data;

    if (unicode == NULL)
        return NULL;
    assert(_PyUnicode_CHECK(unicode));
    if (PyUnicode_READY(unicode) == -1) {
        Py_DECREF(unicode);
        return NULL;
    }

    self = type->tp_alloc(type, 0);
    if (self == NULL) {
        Py_DECREF(unicode);
        return NULL;
    }
    kind = PyUnicode_KIND(unicode);
    length = PyUnicode_GET_LENGTH(unicode);

    _PyUnicode_LENGTH(self) = length;
    _PyUnicode_STATE(self).interned = 0;
    _PyUnicode_STATE(self).kind = kind;
    _PyUnicode_STATE(self).compact = 0;
    _PyUnicode_STATE(self).ascii = _PyUnicode_STATE(unicode).ascii;
    _PyUnicode_STATE(self).ready = 1;
    _PyUnicode_WSTR(self) = NULL;
    _PyUnicode_UTF8_LENGTH(self) = 0;
    _PyUnicode_UTF8(self) = NULL;
    _PyUnicode_WSTR_LENGTH(self) = 0;
    _PyUnicode_DATA_ANY(self) = NULL;

    share_utf8 = 0;
    share_wstr = 0;
    if (kind == PyUnicode_1BYTE_KIND) {
        char_size = 1;
        if (PyUnicode_MAX_CHAR_VALUE(unicode) < 128)
            share_utf8 = 1;
    }
    else if (kind == PyUnicode_2BYTE_KIND) {
        char_size = 2;
        if (sizeof(wchar_t) == 2)
            share_wstr = 1;
    }
    else {
        assert(kind == PyUnicode_4BYTE_KIND);
        char_size = 4;
        if (sizeof(wchar_t) == 4)
            share_wstr = 1;
    }

    /* Ensure we won't overflow the length. */
    if (length > (PY_SSIZE_T_MAX / char_size - 1)) {
        PyErr_NoMemory();
        Py_DECREF(unicode);
        Py_DECREF(self);
        return NULL;
    }
    data = PyObject_MALLOC((length + 1) * char_size);
    if (data == NULL) {
        PyErr_NoMemory();
        Py_DECREF(unicode);
        Py_DECREF(self);
        return NULL;
    }

    _PyUnicode_DATA_ANY(self) = data;
    if (share_utf8) {
        _PyUnicode_UTF8_LENGTH(self) = length;
        _PyUnicode_UTF8(self) = data;
    }
    if (share_wstr) {
        _PyUnicode_WSTR_LENGTH(self) = length;
        _PyUnicode_WSTR(self) = (wchar_t *)data;
    }

    memcpy(data, PyUnicode_DATA(unicode),
              kind * (length + 1));
    assert(_PyUnicode_CheckConsistency(self, 1));
    Py_DECREF(unicode);
    return (PyUnicodeObject*) polyglot_from_PyUnicodeObject((PyUnicodeObject*)self);
}

PyObject* PyUnicode_FromString(const char* o) {
    return to_sulong(polyglot_from_string(o, SRC_CS));
}

UPCALL_ID(PyUnicode_EncodeFSDefault);
PyObject* PyUnicode_EncodeFSDefault(PyObject* o) {
    return UPCALL_CEXT_O(_jls_PyUnicode_EncodeFSDefault, native_to_java(o));
}

PyObject* PyUnicode_DecodeFSDefault(const char* o) {
    // TODO: this implementation does not honor Py_FileSystemDefaultEncoding and Py_FileSystemDefaultEncodeErrors
    return to_sulong(polyglot_from_string(o, "utf-8"));
}

PyObject* PyUnicode_DecodeFSDefaultAndSize(const char* o, Py_ssize_t size) {
    // TODO: this implementation does not honor Py_FileSystemDefaultEncoding and Py_FileSystemDefaultEncodeErrors
    return to_sulong(polyglot_from_string_n(o, size, "utf-8"));
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

typedef PyObject* (*unicode_fromformat_fun_t)(void* jstr, va_list va);
UPCALL_TYPED_ID(PyTruffle_Unicode_FromFormat, unicode_fromformat_fun_t);
PyObject* PyUnicode_FromFormatV(const char* format, va_list va) {
    return _jls_PyTruffle_Unicode_FromFormat(polyglot_from_string(format, "ascii"), va);
}

NO_INLINE
PyObject* PyUnicode_FromFormat(const char* format, ...) {
    va_list args;
    va_start(args, format);
    PyObject* result = _jls_PyTruffle_Unicode_FromFormat(polyglot_from_string(format, "ascii"), args);
    va_end(args);
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

Py_ssize_t PyUnicode_GetLength(PyObject *unicode) {
    return PyUnicode_GET_LENGTH(unicode);
}

UPCALL_ID(PyUnicode_Concat);
PyObject * PyUnicode_Concat(PyObject *left, PyObject *right) {
    return UPCALL_CEXT_O(_jls_PyUnicode_Concat, native_to_java(left), native_to_java(right));
}

void PyUnicode_Append(PyObject **p_left, PyObject *right) {
    // XX: This implementation misses the fact that some unicode storages can be resized.
    *p_left = PyUnicode_Concat(*p_left, right);
}

// taken from CPython "Python/Objects/unicodeobject.c"
void PyUnicode_AppendAndDel(PyObject **pleft, PyObject *right) {
    PyUnicode_Append(pleft, right);
    Py_XDECREF(right);
}

UPCALL_ID(PyUnicode_FromEncodedObject);
PyObject * PyUnicode_FromEncodedObject(PyObject *obj, const char *encoding, const char *errors) {
    // TODO buffer treatment
    return UPCALL_CEXT_O(_jls_PyUnicode_FromEncodedObject, native_to_java(obj), polyglot_from_string(encoding, SRC_CS), convert_errors(errors));
}

UPCALL_ID(PyUnicode_InternInPlace);
void PyUnicode_InternInPlace(PyObject **s) {
	PyObject *t = UPCALL_CEXT_O(_jls_PyUnicode_InternInPlace, native_to_java(*s));
	if (t != *s) {
		Py_INCREF(t);
		Py_SETREF(*s, t);
	}
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
    return UPCALL_CEXT_O(_jls__PyUnicode_AsUTF8String, native_to_java(unicode), convert_errors(errors), NULL);
}

// taken from CPython "Python/Objects/unicodeobject.c"
PyObject * PyUnicode_AsUTF8String(PyObject *unicode) {
    return _PyUnicode_AsUTF8String(unicode, NULL);
}

PyObject * PyUnicode_DecodeUTF32(const char *s, Py_ssize_t size, const char *errors, int *byteorder) {
    PyObject *result;
    int bo = byteorder != NULL ? *byteorder : 0;
    return polyglot_invoke(PY_TRUFFLE_CEXT, "PyTruffle_Unicode_DecodeUTF32", polyglot_from_i8_array(s, size), size, convert_errors(errors), bo, NULL);
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
    return UPCALL_CEXT_O(_jls__PyTruffle_Unicode_AsLatin1String, native_to_java(unicode), convert_errors(errors), ERROR_MARKER);
}

// taken from CPython "Python/Objects/unicodeobject.c"
PyObject* PyUnicode_AsLatin1String(PyObject *unicode) {
    return _PyUnicode_AsLatin1String(unicode, NULL);
}

UPCALL_ID(_PyTruffle_Unicode_AsASCIIString);
PyObject* _PyUnicode_AsASCIIString(PyObject *unicode, const char *errors) {
    return UPCALL_CEXT_O(_jls__PyTruffle_Unicode_AsASCIIString, native_to_java(unicode), convert_errors(errors), ERROR_MARKER);
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
    PyObject* bytes = UPCALL_CEXT_O(_jls_PyTruffle_Unicode_AsWideChar, native_to_java(unicode), Py_UNICODE_SIZE, ERROR_MARKER);
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

typedef PyObject* (*unicode_new_fun_t)(void* data, int elementSize, int is_ascii);
UPCALL_TYPED_ID(PyUnicode_New, unicode_new_fun_t);
PyObject* PyUnicode_New(Py_ssize_t size, Py_UCS4 maxchar) {
    /* add one to size for the null character */
	int is_ascii = 0;
    if (maxchar < 128) {
        /* We intentionally use 'size' (which is one element less than the allocated array)
         * because interop users should not see the null character. */
        return _jls_PyUnicode_New(polyglot_from_Py_UCS1_array((Py_UCS1 *) calloc(size + 1, PyUnicode_1BYTE_KIND), size), PyUnicode_1BYTE_KIND, 1);
    } else if (maxchar < 256) {
        return _jls_PyUnicode_New(polyglot_from_Py_UCS1_array((Py_UCS1 *) calloc(size + 1, PyUnicode_1BYTE_KIND), size), PyUnicode_1BYTE_KIND, 0);
    } else if (maxchar < 65536) {
        return _jls_PyUnicode_New(polyglot_from_Py_UCS2_array((Py_UCS2 *) calloc(size + 1, PyUnicode_2BYTE_KIND), size), PyUnicode_2BYTE_KIND, 0);
    } else {
        if (maxchar > MAX_UNICODE) {
            PyErr_SetString(PyExc_SystemError,
                            "invalid maximum character passed to PyUnicode_New");
            return NULL;
        }
        return _jls_PyUnicode_New(polyglot_from_Py_UCS4_array((Py_UCS4 *) calloc(size + 1, PyUnicode_4BYTE_KIND), size), PyUnicode_4BYTE_KIND, 0);
    }
    /* should never be reached */
    return NULL;
}

UPCALL_ID(PyUnicode_Compare);
int PyUnicode_Compare(PyObject *left, PyObject *right) {
	return UPCALL_CEXT_I(_jls_PyUnicode_Compare, native_to_java(left), native_to_java(right));
}

int _PyUnicode_EqualToASCIIString( PyObject *left, const char *right) {
	return UPCALL_CEXT_I(_jls_PyUnicode_Compare, native_to_java(left), polyglot_from_string(right, SRC_CS)) == 0;
}

typedef PyObject* (*unicode_fromwchar_fun_t)(void* data, size_t element_size, void* errorMarker);
UPCALL_TYPED_ID(PyTruffle_Unicode_FromWchar, unicode_fromwchar_fun_t);
PyObject * PyUnicode_FromWideChar(const wchar_t *u, Py_ssize_t size) {
    if (size == -1) {
        size = wcslen(u);
    }
	return _jls_PyTruffle_Unicode_FromWchar(polyglot_from_wchar_t_array(u, size), sizeof(wchar_t), NULL);
}

static PyObject* _PyUnicode_FromUCS1(const Py_UCS1* u, Py_ssize_t size) {
	// CPython assumes latin1 when decoding an UCS1 array
	return polyglot_from_string((const char *) u, "ISO-8859-1");
}

static PyObject* _PyUnicode_FromUCS2(const Py_UCS2 *u, Py_ssize_t size) {
	// This does deliberately not use UPCALL_CEXT_O to avoid argument conversion since
	// 'PyTruffle_Unicode_FromWchar' really expects the bare pointer.
	return _jls_PyTruffle_Unicode_FromWchar(polyglot_from_Py_UCS2_array(u, size), sizeof(Py_UCS2), NULL);
}

static PyObject* _PyUnicode_FromUCS4(const Py_UCS4 *u, Py_ssize_t size) {
	// This does deliberately not use UPCALL_CEXT_O to avoid argument conversion since
	// 'PyTruffle_Unicode_FromWchar' really expects the bare pointer.
	return _jls_PyTruffle_Unicode_FromWchar(polyglot_from_Py_UCS4_array(u, size), sizeof(Py_UCS4), NULL);
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

typedef PyObject* (*unicode_DecodeUTF8Stateful_fun_t)(void *data, const char *errors, int consumed);
UPCALL_TYPED_ID(PyUnicode_DecodeUTF8Stateful, unicode_DecodeUTF8Stateful_fun_t);
PyObject * PyUnicode_DecodeUTF8Stateful(const char *s, Py_ssize_t size, const char *errors, Py_ssize_t *consumed) {
	// This does deliberately not use UPCALL_CEXT_O to avoid argument conversion since
	// 'PyUnicode_DecodeUTF8Stateful' really expects the bare pointer.
	PyObject* result = _jls_PyUnicode_DecodeUTF8Stateful( 
                                                polyglot_from_i8_array(s, size), 
                                                convert_errors(errors),
                                                consumed != NULL ? 1 : 0);
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
        id->object = PyUnicode_DecodeUTF8Stateful(id->string, 
                                                        strlen(id->string), 
                                                        "strict", 
                                                        NULL);
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
    if (encoding == NULL) {
        return PyUnicode_DecodeUTF8Stateful(s, size, errors, NULL);
    }
    PyObject *mv = PyMemoryView_FromMemory(s, size, PyBUF_READ);
    if (!mv) {
        return NULL;
    }
	return UPCALL_CEXT_O(_jls_PyUnicode_Decode, mv, polyglot_from_string(encoding, SRC_CS), convert_errors(errors));
}

PyObject * PyUnicode_DecodeASCII(const char *s, Py_ssize_t size, const char *errors) {
	return PyUnicode_Decode(s, size, "ascii", errors);
}

PyObject * PyUnicode_DecodeLatin(const char *s, Py_ssize_t size, const char *errors) {
	return PyUnicode_Decode(s, size, "latin1", errors);
}

UPCALL_ID(PyUnicode_Tailmatch);
Py_ssize_t PyUnicode_Tailmatch(PyObject *str, PyObject *substr, Py_ssize_t start, Py_ssize_t end, int direction) {
	return UPCALL_CEXT_L(_jls_PyUnicode_Tailmatch, native_to_java(str), native_to_java(substr), start, end, direction);
}

UPCALL_ID(PyUnicode_AsEncodedString);
PyObject * PyUnicode_AsEncodedString(PyObject *unicode, const char *encoding, const char *errors) {
	return UPCALL_CEXT_O(_jls_PyUnicode_AsEncodedString, native_to_java(unicode), polyglot_from_string(encoding, SRC_CS), convert_errors(errors));
}

UPCALL_ID(PyUnicode_Replace);
PyObject * PyUnicode_Replace(PyObject *str, PyObject *substr, PyObject *replstr, Py_ssize_t maxcount) {
	return UPCALL_CEXT_O(_jls_PyUnicode_Replace, native_to_java(str), native_to_java(substr), native_to_java(replstr), maxcount);
}

/* Generic helper macro to convert characters of different types.
   from_type and to_type have to be valid type names, begin and end
   are pointers to the source characters which should be of type
   "from_type *".  to is a pointer of type "to_type *" and points to the
   buffer where the result characters are written to. */
#define _PyUnicode_CONVERT_BYTES(from_type, to_type, begin, end, to) \
    do {                                                \
        to_type *_to = (to_type *)(to);                \
        const from_type *_iter = (from_type *)(begin);  \
        const from_type *_end = (from_type *)(end);     \
        Py_ssize_t n = (_end) - (_iter);                \
        const from_type *_unrolled_end =                \
            _iter + _Py_SIZE_ROUND_DOWN(n, 4);          \
        while (_iter < (_unrolled_end)) {               \
            _to[0] = (to_type) _iter[0];                \
            _to[1] = (to_type) _iter[1];                \
            _to[2] = (to_type) _iter[2];                \
            _to[3] = (to_type) _iter[3];                \
            _iter += 4; _to += 4;                       \
        }                                               \
        while (_iter < (_end))                          \
            *_to++ = (to_type) *_iter++;                \
    } while (0)


/* used from Java only to decode a native unicode object */
void* native_unicode_as_string(PyObject *string) {
	Py_UCS4 *target = NULL;
    int kind = 0;
    void *data = NULL;
    void *result = NULL;
    Py_ssize_t len;
    if (PyUnicode_READY(string) == -1) {
    	PyErr_Format(PyExc_TypeError, "provided unicode object is not ready");
        return NULL;
    }
    kind = PyUnicode_KIND(string);
    data = PyUnicode_DATA(string);
    len = PyUnicode_GET_LENGTH(string);
    if (kind == PyUnicode_1BYTE_KIND) {
        Py_UCS1 *start = (Py_UCS1 *) data;
    	if (PyUnicode_IS_COMPACT_ASCII(string)) {
            return polyglot_from_string_n((const char *)data, sizeof(Py_UCS1) * len, "ascii");
    	}
        return polyglot_from_string_n((const char *)data, sizeof(Py_UCS1) * len, "latin1");
    }
    else if (kind == PyUnicode_2BYTE_KIND) {
        Py_UCS2 *start = (Py_UCS2 *) data;
        target = PyMem_New(Py_UCS4, len);
        if (!target) {
            PyErr_NoMemory();
            return NULL;
        }
        _PyUnicode_CONVERT_BYTES(Py_UCS2, Py_UCS4, start, start + len, target);
        result = polyglot_from_string_n((const char *)target, sizeof(Py_UCS4) * len, "UTF-32LE");
        free(target);
        return result;
    }
    assert(kind == PyUnicode_4BYTE_KIND);
    return polyglot_from_string_n((const char *)data, sizeof(Py_UCS4) * len, "UTF-32LE");
}

UPCALL_ID(PyUnicode_ReadChar);
Py_UCS4 PyUnicode_ReadChar(PyObject *unicode, Py_ssize_t index) {
	return UPCALL_CEXT_I(_jls_PyUnicode_ReadChar, native_to_java(unicode), index);
}

UPCALL_ID(PyUnicode_Contains)
int PyUnicode_Contains(PyObject *str, PyObject *substr) {
    return UPCALL_CEXT_I(_jls_PyUnicode_Contains, native_to_java(str), native_to_java(substr));
}

UPCALL_ID(PyUnicode_Split)
PyObject* PyUnicode_Split(PyObject *s, PyObject *sep, Py_ssize_t maxsplit) {
    return UPCALL_CEXT_O(_jls_PyUnicode_Split, native_to_java(s), native_to_java(sep), maxsplit);
}
