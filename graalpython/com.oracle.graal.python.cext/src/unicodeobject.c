/* Copyright (c) 2018, 2023, Oracle and/or its affiliates.
 * Copyright (C) 1996-2020 Python Software Foundation
 *
 * Licensed under the PYTHON SOFTWARE FOUNDATION LICENSE VERSION 2
 */
#include "capi.h"


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

// '_Py_ascii_whitespace' was moved to 'const_arrays.h'

static PyObject * _PyUnicode_FromUCS1(const Py_UCS1 *s, Py_ssize_t size);
static PyObject * _PyUnicode_FromUCS2(const Py_UCS2 *s, Py_ssize_t size);
static PyObject * _PyUnicode_FromUCS4(const Py_UCS4 *s, Py_ssize_t size);

static MUST_INLINE void* convert_errors(const char *errors) {
    return errors != NULL ? truffleString(errors) : truffleString("strict");
}

static MUST_INLINE void* convert_encoding(const char *errors) {
    return errors != NULL ? truffleString(errors) : truffleString("utf-8");
}

/* Like 'memcpy' but can read/write from/to managed objects. */
int bytes_copy2mem(char* target, char* source, size_t nbytes);


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
        return NULL;
    }

    self = type->tp_alloc(type, 0);
    if (self == NULL) {
        return NULL;
    }
    kind = PyUnicode_KIND(unicode);
    length = PyASCIIObject_length(unicode);

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
        Py_DECREF(self);
        return NULL;
    }
    data = PyObject_MALLOC((length + 1) * char_size);
    if (data == NULL) {
        PyErr_NoMemory();
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

    return (PyUnicodeObject*) polyglot_from_PyUnicodeObject((PyUnicodeObject*)self);
}

PyObject* PyUnicode_DecodeFSDefaultAndSize(const char* o, Py_ssize_t size) {
    // TODO: this implementation does not honor Py_FileSystemDefaultEncoding and Py_FileSystemDefaultEncodeErrors
    return pythonToNative(javaStringToTruffleString(polyglot_from_string_n(o, size, "utf-8")));
}


PyObject * PyUnicode_FromStringAndSize(const char *u, Py_ssize_t size) {
    if (size < 0) {
        PyErr_SetString(PyExc_SystemError, "Negative size passed to PyUnicode_FromStringAndSize");
        return NULL;
    }
    return pythonToNative(javaStringToTruffleString(polyglot_from_string_n(u, size, SRC_CS)));
}

PyObject* PyUnicode_FromFormatV(const char* format, va_list va) {
	va_list lva;
	va_copy(lva, va);
    PyObject* res = GraalPyTruffle_Unicode_FromFormat(polyglot_from_string(format, "ascii"), &lva);
    va_end(lva);
    return res;
}

NO_INLINE
PyObject* PyUnicode_FromFormat(const char* format, ...) {
    va_list args;
    va_start(args, format);
    PyObject* result = GraalPyTruffle_Unicode_FromFormat(polyglot_from_string(format, "ascii"), &args);
    va_end(args);
    return result;
}

PyObject * PyUnicode_FromUnicode(const Py_UNICODE *u, Py_ssize_t size) {
    if (u == NULL) {
        return pythonToNative(javaStringToTruffleString(polyglot_from_string_n("", 0, "utf-16le")));
    }

    switch(Py_UNICODE_SIZE) {
    case 2:
        return pythonToNative(javaStringToTruffleString(polyglot_from_string_n((const char*)u, size*2, "utf-16le")));
    case 4:
        return pythonToNative(javaStringToTruffleString(polyglot_from_string_n((const char*)u, size*4, "utf-32le")));
    }
    return NULL;
}

Py_ssize_t PyUnicode_GetLength(PyObject *unicode) {
    return PyUnicode_GET_LENGTH(unicode);
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

static PyObject *interned = NULL;

void PyUnicode_InternInPlace(PyObject **p) {
    PyObject *s = *p;
    if (s == NULL || !PyUnicode_Check(s)) {
        return;
    }

    /* If it's a subclass, we don't really know what putting
       it in the interned dict might do. */
    if (!PyUnicode_CheckExact(s)) {
        return;
    }

    // will be checked by `GraalPyTruffleUnicode_LookupAndIntern` upcall
    // if (PyUnicode_CHECK_INTERNED(s)) {
    //     return;
    // }

    // if (PyUnicode_READY(s) == -1) {
    //     PyErr_Clear();
    //     return;
    // }

    if (interned == NULL) {
        interned = PyDict_New();
        if (interned == NULL) {
            PyErr_Clear(); /* Don't leave an exception */
            return;
        }
    }

    // PyObject *t = PyDict_SetDefault(interned, s, s);
	PyObject *t = GraalPyTruffleUnicode_LookupAndIntern(interned, s);
    if (t == NULL) {
        PyErr_Clear();
        return;
    }

    if (t != s) {
        Py_INCREF(t);
        Py_SETREF(*p, t);
        return;
    }

    /* The two references in interned dict (key and value) are not counted by
       refcnt. unicode_dealloc() and _PyUnicode_ClearInterned() take care of
       this. */
    // has been set already by `GraalPyTruffleUnicode_LookupAndIntern` upcall
    // Py_SET_REFCNT(s, Py_REFCNT(s) - 2);
    // _PyUnicode_STATE(s).interned = SSTATE_INTERNED_MORTAL;

}

// taken from CPython "Python/Objects/unicodeobject.c"
PyObject * PyUnicode_InternFromString(const char *cp) {
    PyObject *s = PyUnicode_FromString(cp);
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
    const char* charptr = GraalPyTruffle_Unicode_AsUTF8AndSize_CharPtr(unicode);
    if (charptr && psize) {
        *psize = GraalPyTruffle_Unicode_AsUTF8AndSize_Size(unicode);
    }
    return charptr;
}

// taken from CPython "Python/Objects/unicodeobject.c"
PyObject * PyUnicode_AsUTF8String(PyObject *unicode) {
    return _PyUnicode_AsUTF8String(unicode, NULL);
}

PyObject * PyUnicode_DecodeUTF32(const char *s, Py_ssize_t size, const char *errors, int *byteorder) {
    PyObject *result;
    int bo = byteorder != NULL ? *byteorder : 0;
    return GraalPyTruffle_Unicode_DecodeUTF32(polyglot_from_i8_array(s, size), size, convert_errors(errors), bo);
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

// taken from CPython "Python/Objects/unicodeobject.c"
PyObject* PyUnicode_AsLatin1String(PyObject *unicode) {
    return _PyUnicode_AsLatin1String(unicode, NULL);
}

// taken from CPython "Python/Objects/unicodeobject.c"
PyObject* PyUnicode_AsASCIIString(PyObject *unicode) {
    return _PyUnicode_AsASCIIString(unicode, NULL);
}

Py_UNICODE* PyUnicode_AsUnicode(PyObject *unicode) {
    Py_ssize_t size = 0;
    return PyUnicode_AsUnicodeAndSize(unicode, &size);
}

Py_UNICODE* PyUnicode_AsUnicodeAndSize(PyObject *unicode, Py_ssize_t *size) {
    Py_UNICODE* charptr = GraalPyTruffle_Unicode_AsUnicodeAndSize_CharPtr(unicode);
    if (charptr && size) {
        *size = GraalPyTruffle_Unicode_AsUnicodeAndSize_Size(unicode);
    }
    return charptr;
}

int _PyUnicode_Ready(PyObject *unicode) {
    // TODO(fa) anything we need to initialize here?
    return 0;
}

PyObject* PyUnicode_New(Py_ssize_t size, Py_UCS4 maxchar) {
    /* add one to size for the null character */
	int is_ascii = 0;
    if (maxchar < 128) {
        /* We intentionally use 'size' (which is one element less than the allocated array)
         * because interop users should not see the null character. */
        return GraalPyTruffleUnicode_New(polyglot_from_Py_UCS1_array((Py_UCS1 *) calloc(size + 1, PyUnicode_1BYTE_KIND), size), PyUnicode_1BYTE_KIND, 1);
    } else if (maxchar < 256) {
        return GraalPyTruffleUnicode_New(polyglot_from_Py_UCS1_array((Py_UCS1 *) calloc(size + 1, PyUnicode_1BYTE_KIND), size), PyUnicode_1BYTE_KIND, 0);
    } else if (maxchar < 65536) {
        return GraalPyTruffleUnicode_New(polyglot_from_Py_UCS2_array((Py_UCS2 *) calloc(size + 1, PyUnicode_2BYTE_KIND), size), PyUnicode_2BYTE_KIND, 0);
    } else {
        if (maxchar > MAX_UNICODE) {
            PyErr_SetString(PyExc_SystemError,
                            "invalid maximum character passed to PyUnicode_New");
            return NULL;
        }
        return GraalPyTruffleUnicode_New(polyglot_from_Py_UCS4_array((Py_UCS4 *) calloc(size + 1, PyUnicode_4BYTE_KIND), size), PyUnicode_4BYTE_KIND, 0);
    }
    /* should never be reached */
    return NULL;
}

PyObject * PyUnicode_FromWideChar(const wchar_t *u, Py_ssize_t size) {
    if (size == -1) {
        size = wcslen(u);
    }
	return GraalPyTruffle_Unicode_FromWchar(polyglot_from_wchar_t_array(u, size), sizeof(wchar_t));
}

static PyObject* _PyUnicode_FromUCS1(const Py_UCS1* u, Py_ssize_t size) {
    return GraalPyTruffleUnicode_FromUCS(polyglot_from_i8_array((int8_t *)u, size), size, PyUnicode_1BYTE_KIND);
}

static PyObject* _PyUnicode_FromUCS2(const Py_UCS2 *u, Py_ssize_t size) {
    const Py_ssize_t byte_size = size * PyUnicode_2BYTE_KIND;
    return GraalPyTruffleUnicode_FromUCS(polyglot_from_i8_array((int8_t *)u, byte_size), byte_size, PyUnicode_2BYTE_KIND);
}

static PyObject* _PyUnicode_FromUCS4(const Py_UCS4 *u, Py_ssize_t size) {
    const Py_ssize_t byte_size = size * PyUnicode_4BYTE_KIND;
    return GraalPyTruffleUnicode_FromUCS(polyglot_from_i8_array((int8_t *)u, byte_size), byte_size, PyUnicode_4BYTE_KIND);
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

// taken from CPython "Python/Objects/unicodeobject.c"
PyObject * PyUnicode_DecodeUTF8(const char *s, Py_ssize_t size, const char *errors) {
    return PyUnicode_DecodeUTF8Stateful(s, size, errors, NULL);
}

PyObject * PyUnicode_DecodeUTF8Stateful(const char *s, Py_ssize_t size, const char *errors, Py_ssize_t *consumed) {
	PyObject* result = GraalPyTruffleUnicode_DecodeUTF8Stateful(
                                                polyglot_from_i8_array(s, size),
                                                convert_errors(errors),
                                                consumed != NULL ? 1 : 0);
	if (result != NULL) {
		if (consumed != NULL) {
			*consumed = PyLong_AsSsize_t(PyTuple_GetItem(result, 1));
		}
		PyObject* string = PyTuple_GetItem(result, 0);
		Py_IncRef(string);
		Py_DecRef(result);
		return string;
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
    }
    return id->object;
}

PyObject * PyUnicode_Decode(const char *s, Py_ssize_t size, const char *encoding, const char *errors) {
    if (encoding == NULL) {
        return PyUnicode_DecodeUTF8Stateful(s, size, errors, NULL);
    }
    PyObject *mv = PyMemoryView_FromMemory(s, size, PyBUF_READ);
    if (!mv) {
        return NULL;
    }
	return GraalPyTruffleUnicode_Decode(mv, truffleString(encoding), convert_errors(errors));
}

PyObject * PyUnicode_DecodeASCII(const char *s, Py_ssize_t size, const char *errors) {
	return PyUnicode_Decode(s, size, "ascii", errors);
}

PyObject * PyUnicode_DecodeLatin(const char *s, Py_ssize_t size, const char *errors) {
    if (size < 0) {
        PyErr_SetString(PyExc_SystemError,
                        "Negative size passed to PyUnicode_New");
        return NULL;
    }
    return pythonToNative(javaStringToTruffleString(polyglot_from_string_n(s, size, "latin1")));
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


static Py_UCS4*
as_ucs4(PyObject *string, Py_UCS4 *target, Py_ssize_t targetsize,
        int copy_null)
{
    int kind;
    void *data;
    Py_ssize_t len, targetlen;
    if (PyUnicode_READY(string) == -1)
        return NULL;
    kind = PyUnicode_KIND(string);
    data = PyUnicode_DATA(string);
    len = PyUnicode_GET_LENGTH(string);
    targetlen = len;
    if (copy_null)
        targetlen++;
    if (!target) {
        target = PyMem_New(Py_UCS4, targetlen);
        if (!target) {
            PyErr_NoMemory();
            return NULL;
        }
    }
    else {
        if (targetsize < targetlen) {
            PyErr_Format(PyExc_SystemError,
                         "string is longer than the buffer");
            if (copy_null && 0 < targetsize)
                target[0] = 0;
            return NULL;
        }
    }
    if (kind == PyUnicode_1BYTE_KIND) {
        Py_UCS1 *start = (Py_UCS1 *) data;
        _PyUnicode_CONVERT_BYTES(Py_UCS1, Py_UCS4, start, start + len, target);
    }
    else if (kind == PyUnicode_2BYTE_KIND) {
        Py_UCS2 *start = (Py_UCS2 *) data;
        _PyUnicode_CONVERT_BYTES(Py_UCS2, Py_UCS4, start, start + len, target);
    }
    else {
        assert(kind == PyUnicode_4BYTE_KIND);
        memcpy(target, data, len * sizeof(Py_UCS4));
    }
    if (copy_null)
        target[len] = 0;
    return target;
}

Py_UCS4*
PyUnicode_AsUCS4(PyObject *string, Py_UCS4 *target, Py_ssize_t targetsize,
                 int copy_null)
{
    if (target == NULL || targetsize < 0) {
        PyErr_BadInternalCall();
        return NULL;
    }
    return as_ucs4(string, target, targetsize, copy_null);
}

Py_UCS4*
PyUnicode_AsUCS4Copy(PyObject *string)
{
    return as_ucs4(string, NULL, 0, 1);
}

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
    len = PyASCIIObject_length(string);
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

// taken from CPython "Objects/unicodeobject.c"
PyObject *
PyUnicode_DecodeLatin1(const char *s,
                       Py_ssize_t size,
                       const char *errors)
{
    /* Latin-1 is equivalent to the first 256 ordinals in Unicode. */
    return _PyUnicode_FromUCS1((const unsigned char*)s, size);
}

int _PyUnicode_EqualToASCIIId(PyObject *left, _Py_Identifier *right) {
    return _PyUnicode_EqualToASCIIString(left, right->string);
}

Py_ssize_t _PyASCIIObject_LENGTH(PyASCIIObject* op) {
	return PyASCIIObject_length(op);
}

wchar_t* _PyASCIIObject_WSTR(PyASCIIObject* op) {
	return PyASCIIObject_wstr(op);
}

unsigned int _PyASCIIObject_STATE_ASCII(PyASCIIObject* op) {
	return GET_SLOT_SPECIAL(op, PyASCIIObject, state_ascii, state.ascii);
}

unsigned int _PyASCIIObject_STATE_COMPACT(PyASCIIObject* op) {
	return GET_SLOT_SPECIAL(op, PyASCIIObject, state_compact, state.compact);
}

unsigned int _PyASCIIObject_STATE_KIND(PyASCIIObject* op) {
	return GET_SLOT_SPECIAL(op, PyASCIIObject, state_kind, state.kind);
}

unsigned int _PyASCIIObject_STATE_READY(PyASCIIObject* op) {
	return GET_SLOT_SPECIAL(op, PyASCIIObject, state_ready, state.ready);
}

void* _PyUnicodeObject_DATA(PyUnicodeObject* op) {
	return GET_SLOT_SPECIAL(op, PyUnicodeObject, data, data.any);
}

Py_ssize_t _PyUnicode_get_wstr_length(PyObject* op) {
    return PyUnicode_IS_COMPACT_ASCII(op) ?
            PyASCIIObject_length(op) :
            PyCompactUnicodeObject_wstr_length(op);
}

// from CPython unicodeobject.c:
int
PyUnicode_FSConverter(PyObject* arg, void* addr)
{
    PyObject *path = NULL;
    PyObject *output = NULL;
    Py_ssize_t size;
    const char *data;
    if (arg == NULL) {
        Py_DECREF(*(PyObject**)addr);
        *(PyObject**)addr = NULL;
        return 1;
    }
    path = PyOS_FSPath(arg);
    if (path == NULL) {
        return 0;
    }
    if (PyBytes_Check(path)) {
        output = path;
    }
    else {  // PyOS_FSPath() guarantees its returned value is bytes or str.
        output = PyUnicode_EncodeFSDefault(path);
        Py_DECREF(path);
        if (!output) {
            return 0;
        }
        assert(PyBytes_Check(output));
    }

    size = PyBytes_GET_SIZE(output);
    data = PyBytes_AS_STRING(output);
    if ((size_t)size != strlen(data)) {
        PyErr_SetString(PyExc_ValueError, "embedded null byte");
        Py_DECREF(output);
        return 0;
    }
    *(PyObject**)addr = output;
    return Py_CLEANUP_SUPPORTED;
}
