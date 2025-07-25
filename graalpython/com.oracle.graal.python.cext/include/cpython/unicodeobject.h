/* Copyright (c) 2020, 2025, Oracle and/or its affiliates.
 * Copyright (C) 1996-2020 Python Software Foundation
 *
 * Licensed under the PYTHON SOFTWARE FOUNDATION LICENSE VERSION 2
 */
#ifndef Py_CPYTHON_UNICODEOBJECT_H
#  error "this header file must not be included directly"
#endif

/* Py_UNICODE was the native Unicode storage format (code unit) used by
   Python and represents a single Unicode element in the Unicode type.
   With PEP 393, Py_UNICODE is deprecated and replaced with a
   typedef to wchar_t. */
#define PY_UNICODE_TYPE wchar_t
/* Py_DEPRECATED(3.3) */ typedef wchar_t Py_UNICODE;

/* --- Internal Unicode Operations ---------------------------------------- */

// Static inline functions to work with surrogates
static inline int Py_UNICODE_IS_SURROGATE(Py_UCS4 ch) {
    return (0xD800 <= ch && ch <= 0xDFFF);
}
static inline int Py_UNICODE_IS_HIGH_SURROGATE(Py_UCS4 ch) {
    return (0xD800 <= ch && ch <= 0xDBFF);
}
static inline int Py_UNICODE_IS_LOW_SURROGATE(Py_UCS4 ch) {
    return (0xDC00 <= ch && ch <= 0xDFFF);
}

// Join two surrogate characters and return a single Py_UCS4 value.
static inline Py_UCS4 Py_UNICODE_JOIN_SURROGATES(Py_UCS4 high, Py_UCS4 low)  {
    assert(Py_UNICODE_IS_HIGH_SURROGATE(high));
    assert(Py_UNICODE_IS_LOW_SURROGATE(low));
    return 0x10000 + (((high & 0x03FF) << 10) | (low & 0x03FF));
}

// High surrogate = top 10 bits added to 0xD800.
// The character must be in the range [U+10000; U+10ffff].
static inline Py_UCS4 Py_UNICODE_HIGH_SURROGATE(Py_UCS4 ch) {
    assert(0x10000 <= ch && ch <= 0x10ffff);
    return (0xD800 - (0x10000 >> 10) + (ch >> 10));
}

// Low surrogate = bottom 10 bits added to 0xDC00.
// The character must be in the range [U+10000; U+10ffff].
static inline Py_UCS4 Py_UNICODE_LOW_SURROGATE(Py_UCS4 ch) {
    assert(0x10000 <= ch && ch <= 0x10ffff);
    return (0xDC00 + (ch & 0x3FF));
}

/* --- Unicode Type ------------------------------------------------------- */

/* ASCII-only strings created through PyUnicode_New use the PyASCIIObject
   structure. state.ascii and state.compact are set, and the data
   immediately follow the structure. utf8_length can be found
   in the length field; the utf8 pointer is equal to the data pointer. */
typedef struct {
    /* There are 4 forms of Unicode strings:

       - compact ascii:

         * structure = PyASCIIObject
         * test: PyUnicode_IS_COMPACT_ASCII(op)
         * kind = PyUnicode_1BYTE_KIND
         * compact = 1
         * ascii = 1
         * (length is the length of the utf8)
         * (data starts just after the structure)
         * (since ASCII is decoded from UTF-8, the utf8 string are the data)

       - compact:

         * structure = PyCompactUnicodeObject
         * test: PyUnicode_IS_COMPACT(op) && !PyUnicode_IS_ASCII(op)
         * kind = PyUnicode_1BYTE_KIND, PyUnicode_2BYTE_KIND or
           PyUnicode_4BYTE_KIND
         * compact = 1
         * ascii = 0
         * utf8 is not shared with data
         * utf8_length = 0 if utf8 is NULL
         * (data starts just after the structure)

       - legacy string:

         * structure = PyUnicodeObject structure
         * test: !PyUnicode_IS_COMPACT(op)
         * kind = PyUnicode_1BYTE_KIND, PyUnicode_2BYTE_KIND or
           PyUnicode_4BYTE_KIND
         * compact = 0
         * data.any is not NULL
         * utf8 is shared and utf8_length = length with data.any if ascii = 1
         * utf8_length = 0 if utf8 is NULL

       Compact strings use only one memory block (structure + characters),
       whereas legacy strings use one block for the structure and one block
       for characters.

       Legacy strings are created by subclasses of Unicode.

       See also _PyUnicode_CheckConsistency().
    */
    PyObject_HEAD
    Py_ssize_t length;          /* Number of code points in the string */
    Py_hash_t hash;             /* Hash value; -1 if not set */
    struct {
        /* If interned is non-zero, the two references from the
           dictionary to this object are *not* counted in ob_refcnt.
           The possible values here are:
               0: Not Interned
               1: Interned
               2: Interned and Immortal
               3: Interned, Immortal, and Static
           This categorization allows the runtime to determine the right
           cleanup mechanism at runtime shutdown. */
        unsigned int interned:2;
        /* Character size:

           - PyUnicode_1BYTE_KIND (1):

             * character type = Py_UCS1 (8 bits, unsigned)
             * all characters are in the range U+0000-U+00FF (latin1)
             * if ascii is set, all characters are in the range U+0000-U+007F
               (ASCII), otherwise at least one character is in the range
               U+0080-U+00FF

           - PyUnicode_2BYTE_KIND (2):

             * character type = Py_UCS2 (16 bits, unsigned)
             * all characters are in the range U+0000-U+FFFF (BMP)
             * at least one character is in the range U+0100-U+FFFF

           - PyUnicode_4BYTE_KIND (4):

             * character type = Py_UCS4 (32 bits, unsigned)
             * all characters are in the range U+0000-U+10FFFF
             * at least one character is in the range U+10000-U+10FFFF
         */
        unsigned int kind:3;
        /* Compact is with respect to the allocation scheme. Compact unicode
           objects only require one memory block while non-compact objects use
           one block for the PyUnicodeObject struct and another for its data
           buffer. */
        unsigned int compact:1;
        /* The string only contains characters in the range U+0000-U+007F (ASCII)
           and the kind is PyUnicode_1BYTE_KIND. If ascii is set and compact is
           set, use the PyASCIIObject structure. */
        unsigned int ascii:1;
        /* The object is statically allocated. */
        unsigned int statically_allocated:1;
        /* Padding to ensure that PyUnicode_DATA() is always aligned to
           4 bytes (see issue #19537 on m68k). */
        unsigned int :24;
    } state;
} PyASCIIObject;

/* Non-ASCII strings allocated through PyUnicode_New use the
   PyCompactUnicodeObject structure. state.compact is set, and the data
   immediately follow the structure. */
typedef struct {
    PyASCIIObject _base;
    Py_ssize_t utf8_length;     /* Number of bytes in utf8, excluding the
                                 * terminating \0. */
    char *utf8;                 /* UTF-8 representation (null-terminated) */
} PyCompactUnicodeObject;

/* Object format for Unicode subclasses. */
typedef struct {
    PyCompactUnicodeObject _base;
    union {
        void *any;
        Py_UCS1 *latin1;
        Py_UCS2 *ucs2;
        Py_UCS4 *ucs4;
    } data;                     /* Canonical, smallest-form Unicode buffer */
} PyUnicodeObject;

PyAPI_FUNC(int) _PyUnicode_CheckConsistency(
    PyObject *op,
    int check_content);


#define _PyASCIIObject_CAST(op) \
    (assert(PyUnicode_Check(op)), \
     _Py_CAST(PyASCIIObject*, (op)))
#define _PyCompactUnicodeObject_CAST(op) \
    (assert(PyUnicode_Check(op)), \
     _Py_CAST(PyCompactUnicodeObject*, (op)))
#define _PyUnicodeObject_CAST(op) \
    (assert(PyUnicode_Check(op)), \
     _Py_CAST(PyUnicodeObject*, (op)))


/* --- Flexible String Representation Helper Macros (PEP 393) -------------- */

/* Values for PyASCIIObject.state: */

/* Interning state. */
#define SSTATE_NOT_INTERNED 0
#define SSTATE_INTERNED_MORTAL 1
#define SSTATE_INTERNED_IMMORTAL 2
#define SSTATE_INTERNED_IMMORTAL_STATIC 3

/* Use only if you know it's a string */
PyAPI_FUNC(unsigned int) GraalPyUnicode_CHECK_INTERNED(PyObject *op);
static inline unsigned int PyUnicode_CHECK_INTERNED(PyObject *op) {
    return GraalPyUnicode_CHECK_INTERNED(op);
}
#define PyUnicode_CHECK_INTERNED(op) PyUnicode_CHECK_INTERNED(_PyObject_CAST(op))

/* For backward compatibility */
static inline unsigned int PyUnicode_IS_READY(PyObject* Py_UNUSED(op)) {
    return 1;
}
#define PyUnicode_IS_READY(op) PyUnicode_IS_READY(_PyObject_CAST(op))

/* Return true if the string contains only ASCII characters, or 0 if not. The
   string may be compact (PyUnicode_IS_COMPACT_ASCII) or not, but must be
   ready. */
PyAPI_FUNC(unsigned int) GraalPyUnicode_IS_ASCII(PyObject *op);
static inline unsigned int PyUnicode_IS_ASCII(PyObject *op) {
    return GraalPyUnicode_IS_ASCII(op);
}
#define PyUnicode_IS_ASCII(op) PyUnicode_IS_ASCII(_PyObject_CAST(op))

/* Return true if the string is compact or 0 if not.
   No type checks or Ready calls are performed. */
PyAPI_FUNC(unsigned int) GraalPyUnicode_IS_COMPACT(PyObject *op);
static inline unsigned int PyUnicode_IS_COMPACT(PyObject *op) {
    return GraalPyUnicode_IS_COMPACT(op);
}
#define PyUnicode_IS_COMPACT(op) PyUnicode_IS_COMPACT(_PyObject_CAST(op))

/* Return true if the string is a compact ASCII string (use PyASCIIObject
   structure), or 0 if not.  No type checks or Ready calls are performed. */
static inline int PyUnicode_IS_COMPACT_ASCII(PyObject *op) {
    return (PyUnicode_IS_ASCII(op) && PyUnicode_IS_COMPACT(op));
}
#define PyUnicode_IS_COMPACT_ASCII(op) PyUnicode_IS_COMPACT_ASCII(_PyObject_CAST(op))

enum PyUnicode_Kind {
/* Return values of the PyUnicode_KIND() function: */
    PyUnicode_1BYTE_KIND = 1,
    PyUnicode_2BYTE_KIND = 2,
    PyUnicode_4BYTE_KIND = 4
};

PyAPI_FUNC(int) GraalPyUnicode_KIND(PyObject*);
// PyUnicode_KIND(): Return one of the PyUnicode_*_KIND values defined above.
//
// gh-89653: Converting this macro to a static inline function would introduce
// new compiler warnings on "kind < PyUnicode_KIND(str)" (compare signed and
// unsigned numbers) where kind type is an int or on
// "unsigned int kind = PyUnicode_KIND(str)" (cast signed to unsigned).
#define PyUnicode_KIND(op) ((enum PyUnicode_Kind)GraalPyUnicode_KIND(_PyObject_CAST(op)))

/* Return a void pointer to the raw unicode buffer. */
static inline void* _PyUnicode_COMPACT_DATA(PyObject *Py_UNUSED(op)) {
    // strings are never compact in GraalPy
    return NULL;
}

PyAPI_FUNC(void*) GraalPyUnicode_NONCOMPACT_DATA(PyObject *op);
static inline void* _PyUnicode_NONCOMPACT_DATA(PyObject *op) {
    return GraalPyUnicode_NONCOMPACT_DATA(op);
}

static inline void* PyUnicode_DATA(PyObject *op) {
    if (PyUnicode_IS_COMPACT(op)) {
        return _PyUnicode_COMPACT_DATA(op);
    }
    return _PyUnicode_NONCOMPACT_DATA(op);
}
#define PyUnicode_DATA(op) PyUnicode_DATA(_PyObject_CAST(op))

/* Return pointers to the canonical representation cast to unsigned char,
   Py_UCS2, or Py_UCS4 for direct character access.
   No checks are performed, use PyUnicode_KIND() before to ensure
   these will work correctly. */

#define PyUnicode_1BYTE_DATA(op) _Py_STATIC_CAST(Py_UCS1*, PyUnicode_DATA(op))
#define PyUnicode_2BYTE_DATA(op) _Py_STATIC_CAST(Py_UCS2*, PyUnicode_DATA(op))
#define PyUnicode_4BYTE_DATA(op) _Py_STATIC_CAST(Py_UCS4*, PyUnicode_DATA(op))

/* Returns the length of the unicode string. */
PyAPI_FUNC(Py_ssize_t) GraalPyUnicode_GET_LENGTH(PyObject *op);
static inline Py_ssize_t PyUnicode_GET_LENGTH(PyObject *op) {
    return GraalPyUnicode_GET_LENGTH(op);
}
#define PyUnicode_GET_LENGTH(op) PyUnicode_GET_LENGTH(_PyObject_CAST(op))

/* Write into the canonical representation, this function does not do any sanity
   checks and is intended for usage in loops.  The caller should cache the
   kind and data pointers obtained from other function calls.
   index is the index in the string (starts at 0) and value is the new
   code point value which should be written to that location. */
static inline void PyUnicode_WRITE(int kind, void *data,
                                   Py_ssize_t index, Py_UCS4 value)
{
    assert(index >= 0);
    if (kind == PyUnicode_1BYTE_KIND) {
        assert(value <= 0xffU);
        _Py_STATIC_CAST(Py_UCS1*, data)[index] = _Py_STATIC_CAST(Py_UCS1, value);
    }
    else if (kind == PyUnicode_2BYTE_KIND) {
        assert(value <= 0xffffU);
        _Py_STATIC_CAST(Py_UCS2*, data)[index] = _Py_STATIC_CAST(Py_UCS2, value);
    }
    else {
        assert(kind == PyUnicode_4BYTE_KIND);
        assert(value <= 0x10ffffU);
        _Py_STATIC_CAST(Py_UCS4*, data)[index] = value;
    }
}
#define PyUnicode_WRITE(kind, data, index, value) \
    PyUnicode_WRITE(_Py_STATIC_CAST(int, kind), _Py_CAST(void*, data), \
                    (index), _Py_STATIC_CAST(Py_UCS4, value))

/* Read a code point from the string's canonical representation.  No checks
   or ready calls are performed. */
static inline Py_UCS4 PyUnicode_READ(int kind,
                                     const void *data, Py_ssize_t index)
{
    assert(index >= 0);
    if (kind == PyUnicode_1BYTE_KIND) {
        return _Py_STATIC_CAST(const Py_UCS1*, data)[index];
    }
    if (kind == PyUnicode_2BYTE_KIND) {
        return _Py_STATIC_CAST(const Py_UCS2*, data)[index];
    }
    assert(kind == PyUnicode_4BYTE_KIND);
    return _Py_STATIC_CAST(const Py_UCS4*, data)[index];
}
#define PyUnicode_READ(kind, data, index) \
    PyUnicode_READ(_Py_STATIC_CAST(int, kind), \
                   _Py_STATIC_CAST(const void*, data), \
                   (index))

/* PyUnicode_READ_CHAR() is less efficient than PyUnicode_READ() because it
   calls PyUnicode_KIND() and might call it twice.  For single reads, use
   PyUnicode_READ_CHAR, for multiple consecutive reads callers should
   cache kind and use PyUnicode_READ instead. */
static inline Py_UCS4 PyUnicode_READ_CHAR(PyObject *unicode, Py_ssize_t index)
{
    int kind;

    assert(index >= 0);
    // Tolerate reading the NUL character at str[len(str)]
    assert(index <= PyUnicode_GET_LENGTH(unicode));

    kind = PyUnicode_KIND(unicode);
    if (kind == PyUnicode_1BYTE_KIND) {
        return PyUnicode_1BYTE_DATA(unicode)[index];
    }
    if (kind == PyUnicode_2BYTE_KIND) {
        return PyUnicode_2BYTE_DATA(unicode)[index];
    }
    assert(kind == PyUnicode_4BYTE_KIND);
    return PyUnicode_4BYTE_DATA(unicode)[index];
}
#define PyUnicode_READ_CHAR(unicode, index) \
    PyUnicode_READ_CHAR(_PyObject_CAST(unicode), (index))

/* Return a maximum character value which is suitable for creating another
   string based on op.  This is always an approximation but more efficient
   than iterating over the string. */
static inline Py_UCS4 PyUnicode_MAX_CHAR_VALUE(PyObject *op)
{
    int kind;

    if (PyUnicode_IS_ASCII(op)) {
        return 0x7fU;
    }

    kind = PyUnicode_KIND(op);
    if (kind == PyUnicode_1BYTE_KIND) {
       return 0xffU;
    }
    if (kind == PyUnicode_2BYTE_KIND) {
        return 0xffffU;
    }
    assert(kind == PyUnicode_4BYTE_KIND);
    return 0x10ffffU;
}
#define PyUnicode_MAX_CHAR_VALUE(op) \
    PyUnicode_MAX_CHAR_VALUE(_PyObject_CAST(op))

/* === Public API ========================================================= */

/* --- Plain Py_UNICODE --------------------------------------------------- */

/* With PEP 393, this is the recommended way to allocate a new unicode object.
   This function will allocate the object and its buffer in a single memory
   block.  Objects created using this function are not resizable. */
PyAPI_FUNC(PyObject*) PyUnicode_New(
    Py_ssize_t size,            /* Number of code points in the new string */
    Py_UCS4 maxchar             /* maximum code point value in the string */
    );

/* For backward compatibility */
static inline int PyUnicode_READY(PyObject* Py_UNUSED(op))
{
    return 0;
}
#define PyUnicode_READY(op) PyUnicode_READY(_PyObject_CAST(op))

/* Get a copy of a Unicode string. */
PyAPI_FUNC(PyObject*) _PyUnicode_Copy(
    PyObject *unicode
    );

/* Copy character from one unicode object into another, this function performs
   character conversion when necessary and falls back to memcpy() if possible.

   Fail if to is too small (smaller than *how_many* or smaller than
   len(from)-from_start), or if kind(from[from_start:from_start+how_many]) >
   kind(to), or if *to* has more than 1 reference.

   Return the number of written character, or return -1 and raise an exception
   on error.

   Pseudo-code:

       how_many = min(how_many, len(from) - from_start)
       to[to_start:to_start+how_many] = from[from_start:from_start+how_many]
       return how_many

   Note: The function doesn't write a terminating null character.
   */
PyAPI_FUNC(Py_ssize_t) PyUnicode_CopyCharacters(
    PyObject *to,
    Py_ssize_t to_start,
    PyObject *from,
    Py_ssize_t from_start,
    Py_ssize_t how_many
    );

/* Unsafe version of PyUnicode_CopyCharacters(): don't check arguments and so
   may crash if parameters are invalid (e.g. if the output string
   is too short). */
PyAPI_FUNC(void) _PyUnicode_FastCopyCharacters(
    PyObject *to,
    Py_ssize_t to_start,
    PyObject *from,
    Py_ssize_t from_start,
    Py_ssize_t how_many
    );

/* Fill a string with a character: write fill_char into
   unicode[start:start+length].

   Fail if fill_char is bigger than the string maximum character, or if the
   string has more than 1 reference.

   Return the number of written character, or return -1 and raise an exception
   on error. */
PyAPI_FUNC(Py_ssize_t) PyUnicode_Fill(
    PyObject *unicode,
    Py_ssize_t start,
    Py_ssize_t length,
    Py_UCS4 fill_char
    );

/* Unsafe version of PyUnicode_Fill(): don't check arguments and so may crash
   if parameters are invalid (e.g. if length is longer than the string). */
PyAPI_FUNC(void) _PyUnicode_FastFill(
    PyObject *unicode,
    Py_ssize_t start,
    Py_ssize_t length,
    Py_UCS4 fill_char
    );

/* Create a new string from a buffer of Py_UCS1, Py_UCS2 or Py_UCS4 characters.
   Scan the string to find the maximum character. */
PyAPI_FUNC(PyObject*) PyUnicode_FromKindAndData(
    int kind,
    const void *buffer,
    Py_ssize_t size);

/* Create a new string from a buffer of ASCII characters.
   WARNING: Don't check if the string contains any non-ASCII character. */
PyAPI_FUNC(PyObject*) _PyUnicode_FromASCII(
    const char *buffer,
    Py_ssize_t size);

/* Compute the maximum character of the substring unicode[start:end].
   Return 127 for an empty string. */
PyAPI_FUNC(Py_UCS4) _PyUnicode_FindMaxChar (
    PyObject *unicode,
    Py_ssize_t start,
    Py_ssize_t end);

/* --- _PyUnicodeWriter API ----------------------------------------------- */

typedef struct {
    PyObject *buffer;
    void *data;
    int kind;
    Py_UCS4 maxchar;
    Py_ssize_t size;
    Py_ssize_t pos;

    /* minimum number of allocated characters (default: 0) */
    Py_ssize_t min_length;

    /* minimum character (default: 127, ASCII) */
    Py_UCS4 min_char;

    /* If non-zero, overallocate the buffer (default: 0). */
    unsigned char overallocate;

    /* If readonly is 1, buffer is a shared string (cannot be modified)
       and size is set to 0. */
    unsigned char readonly;
} _PyUnicodeWriter ;

/* Initialize a Unicode writer.
 *
 * By default, the minimum buffer size is 0 character and overallocation is
 * disabled. Set min_length, min_char and overallocate attributes to control
 * the allocation of the buffer. */
PyAPI_FUNC(void)
_PyUnicodeWriter_Init(_PyUnicodeWriter *writer);

/* Prepare the buffer to write 'length' characters
   with the specified maximum character.

   Return 0 on success, raise an exception and return -1 on error. */
#define _PyUnicodeWriter_Prepare(WRITER, LENGTH, MAXCHAR)             \
    (((MAXCHAR) <= (WRITER)->maxchar                                  \
      && (LENGTH) <= (WRITER)->size - (WRITER)->pos)                  \
     ? 0                                                              \
     : (((LENGTH) == 0)                                               \
        ? 0                                                           \
        : _PyUnicodeWriter_PrepareInternal((WRITER), (LENGTH), (MAXCHAR))))

/* Don't call this function directly, use the _PyUnicodeWriter_Prepare() macro
   instead. */
PyAPI_FUNC(int)
_PyUnicodeWriter_PrepareInternal(_PyUnicodeWriter *writer,
                                 Py_ssize_t length, Py_UCS4 maxchar);

/* Prepare the buffer to have at least the kind KIND.
   For example, kind=PyUnicode_2BYTE_KIND ensures that the writer will
   support characters in range U+000-U+FFFF.

   Return 0 on success, raise an exception and return -1 on error. */
#define _PyUnicodeWriter_PrepareKind(WRITER, KIND)                    \
    ((KIND) <= (WRITER)->kind                                         \
     ? 0                                                              \
     : _PyUnicodeWriter_PrepareKindInternal((WRITER), (KIND)))

/* Don't call this function directly, use the _PyUnicodeWriter_PrepareKind()
   macro instead. */
PyAPI_FUNC(int)
_PyUnicodeWriter_PrepareKindInternal(_PyUnicodeWriter *writer,
                                     int kind);

/* Append a Unicode character.
   Return 0 on success, raise an exception and return -1 on error. */
PyAPI_FUNC(int)
_PyUnicodeWriter_WriteChar(_PyUnicodeWriter *writer,
    Py_UCS4 ch
    );

/* Append a Unicode string.
   Return 0 on success, raise an exception and return -1 on error. */
PyAPI_FUNC(int)
_PyUnicodeWriter_WriteStr(_PyUnicodeWriter *writer,
    PyObject *str               /* Unicode string */
    );

/* Append a substring of a Unicode string.
   Return 0 on success, raise an exception and return -1 on error. */
PyAPI_FUNC(int)
_PyUnicodeWriter_WriteSubstring(_PyUnicodeWriter *writer,
    PyObject *str,              /* Unicode string */
    Py_ssize_t start,
    Py_ssize_t end
    );

/* Append an ASCII-encoded byte string.
   Return 0 on success, raise an exception and return -1 on error. */
PyAPI_FUNC(int)
_PyUnicodeWriter_WriteASCIIString(_PyUnicodeWriter *writer,
    const char *str,           /* ASCII-encoded byte string */
    Py_ssize_t len             /* number of bytes, or -1 if unknown */
    );

/* Append a latin1-encoded byte string.
   Return 0 on success, raise an exception and return -1 on error. */
PyAPI_FUNC(int)
_PyUnicodeWriter_WriteLatin1String(_PyUnicodeWriter *writer,
    const char *str,           /* latin1-encoded byte string */
    Py_ssize_t len             /* length in bytes */
    );

/* Get the value of the writer as a Unicode string. Clear the
   buffer of the writer. Raise an exception and return NULL
   on error. */
PyAPI_FUNC(PyObject *)
_PyUnicodeWriter_Finish(_PyUnicodeWriter *writer);

/* Deallocate memory of a writer (clear its internal buffer). */
PyAPI_FUNC(void)
_PyUnicodeWriter_Dealloc(_PyUnicodeWriter *writer);


/* Format the object based on the format_spec, as defined in PEP 3101
   (Advanced String Formatting). */
PyAPI_FUNC(int) _PyUnicode_FormatAdvancedWriter(
    _PyUnicodeWriter *writer,
    PyObject *obj,
    PyObject *format_spec,
    Py_ssize_t start,
    Py_ssize_t end);

/* --- Manage the default encoding ---------------------------------------- */

/* Returns a pointer to the default encoding (UTF-8) of the
   Unicode object unicode.

   Like PyUnicode_AsUTF8AndSize(), this also caches the UTF-8 representation
   in the unicodeobject.

   _PyUnicode_AsString is a #define for PyUnicode_AsUTF8 to
   support the previous internal function with the same behaviour.

   Use of this API is DEPRECATED since no size information can be
   extracted from the returned data.
*/

PyAPI_FUNC(const char *) PyUnicode_AsUTF8(PyObject *unicode);

#define _PyUnicode_AsString PyUnicode_AsUTF8

/* --- UTF-7 Codecs ------------------------------------------------------- */

PyAPI_FUNC(PyObject*) _PyUnicode_EncodeUTF7(
    PyObject *unicode,          /* Unicode object */
    int base64SetO,             /* Encode RFC2152 Set O characters in base64 */
    int base64WhiteSpace,       /* Encode whitespace (sp, ht, nl, cr) in base64 */
    const char *errors          /* error handling */
    );

/* --- UTF-8 Codecs ------------------------------------------------------- */

PyAPI_FUNC(PyObject*) _PyUnicode_AsUTF8String(
    PyObject *unicode,
    const char *errors);

/* --- UTF-32 Codecs ------------------------------------------------------ */

PyAPI_FUNC(PyObject*) _PyUnicode_EncodeUTF32(
    PyObject *object,           /* Unicode object */
    const char *errors,         /* error handling */
    int byteorder               /* byteorder to use 0=BOM+native;-1=LE,1=BE */
    );

/* --- UTF-16 Codecs ------------------------------------------------------ */

/* Returns a Python string object holding the UTF-16 encoded value of
   the Unicode data.

   If byteorder is not 0, output is written according to the following
   byte order:

   byteorder == -1: little endian
   byteorder == 0:  native byte order (writes a BOM mark)
   byteorder == 1:  big endian

   If byteorder is 0, the output string will always start with the
   Unicode BOM mark (U+FEFF). In the other two modes, no BOM mark is
   prepended.
*/
PyAPI_FUNC(PyObject*) _PyUnicode_EncodeUTF16(
    PyObject* unicode,          /* Unicode object */
    const char *errors,         /* error handling */
    int byteorder               /* byteorder to use 0=BOM+native;-1=LE,1=BE */
    );

/* --- Unicode-Escape Codecs ---------------------------------------------- */

/* Variant of PyUnicode_DecodeUnicodeEscape that supports partial decoding. */
PyAPI_FUNC(PyObject*) _PyUnicode_DecodeUnicodeEscapeStateful(
        const char *string,     /* Unicode-Escape encoded string */
        Py_ssize_t length,      /* size of string */
        const char *errors,     /* error handling */
        Py_ssize_t *consumed    /* bytes consumed */
);
/* Helper for PyUnicode_DecodeUnicodeEscape that detects invalid escape
   chars. */
PyAPI_FUNC(PyObject*) _PyUnicode_DecodeUnicodeEscapeInternal(
        const char *string,     /* Unicode-Escape encoded string */
        Py_ssize_t length,      /* size of string */
        const char *errors,     /* error handling */
        Py_ssize_t *consumed,   /* bytes consumed */
        const char **first_invalid_escape  /* on return, points to first
                                              invalid escaped char in
                                              string. */
);

/* --- Raw-Unicode-Escape Codecs ---------------------------------------------- */

/* Variant of PyUnicode_DecodeRawUnicodeEscape that supports partial decoding. */
PyAPI_FUNC(PyObject*) _PyUnicode_DecodeRawUnicodeEscapeStateful(
        const char *string,     /* Unicode-Escape encoded string */
        Py_ssize_t length,      /* size of string */
        const char *errors,     /* error handling */
        Py_ssize_t *consumed    /* bytes consumed */
);

/* --- Latin-1 Codecs ----------------------------------------------------- */

PyAPI_FUNC(PyObject*) _PyUnicode_AsLatin1String(
    PyObject* unicode,
    const char* errors);

/* --- ASCII Codecs ------------------------------------------------------- */

PyAPI_FUNC(PyObject*) _PyUnicode_AsASCIIString(
    PyObject* unicode,
    const char* errors);

/* --- Character Map Codecs ----------------------------------------------- */

/* Translate an Unicode object by applying a character mapping table to
   it and return the resulting Unicode object.

   The mapping table must map Unicode ordinal integers to Unicode strings,
   Unicode ordinal integers or None (causing deletion of the character).

   Mapping tables may be dictionaries or sequences. Unmapped character
   ordinals (ones which cause a LookupError) are left untouched and
   are copied as-is.
*/
PyAPI_FUNC(PyObject*) _PyUnicode_EncodeCharmap(
    PyObject *unicode,          /* Unicode object */
    PyObject *mapping,          /* encoding mapping */
    const char *errors          /* error handling */
    );

/* --- Decimal Encoder ---------------------------------------------------- */

/* Coverts a Unicode object holding a decimal value to an ASCII string
   for using in int, float and complex parsers.
   Transforms code points that have decimal digit property to the
   corresponding ASCII digit code points.  Transforms spaces to ASCII.
   Transforms code points starting from the first non-ASCII code point that
   is neither a decimal digit nor a space to the end into '?'. */

PyAPI_FUNC(PyObject*) _PyUnicode_TransformDecimalAndSpaceToASCII(
    PyObject *unicode           /* Unicode object */
    );

/* --- Methods & Slots ---------------------------------------------------- */

PyAPI_FUNC(PyObject *) _PyUnicode_JoinArray(
    PyObject *separator,
    PyObject *const *items,
    Py_ssize_t seqlen
    );

/* Test whether a unicode is equal to ASCII identifier.  Return 1 if true,
   0 otherwise.  The right argument must be ASCII identifier.
   Any error occurs inside will be cleared before return. */
PyAPI_FUNC(int) _PyUnicode_EqualToASCIIId(
    PyObject *left,             /* Left string */
    _Py_Identifier *right       /* Right identifier */
    );

/* Test whether a unicode is equal to ASCII string.  Return 1 if true,
   0 otherwise.  The right argument must be ASCII-encoded string.
   Any error occurs inside will be cleared before return. */
PyAPI_FUNC(int) _PyUnicode_EqualToASCIIString(
    PyObject *left,
    const char *right           /* ASCII-encoded string */
    );

/* Externally visible for str.strip(unicode) */
PyAPI_FUNC(PyObject *) _PyUnicode_XStrip(
    PyObject *self,
    int striptype,
    PyObject *sepobj
    );

/* Using explicit passed-in values, insert the thousands grouping
   into the string pointed to by buffer.  For the argument descriptions,
   see Objects/stringlib/localeutil.h */
PyAPI_FUNC(Py_ssize_t) _PyUnicode_InsertThousandsGrouping(
    _PyUnicodeWriter *writer,
    Py_ssize_t n_buffer,
    PyObject *digits,
    Py_ssize_t d_pos,
    Py_ssize_t n_digits,
    Py_ssize_t min_width,
    const char *grouping,
    PyObject *thousands_sep,
    Py_UCS4 *maxchar);

/* === Characters Type APIs =============================================== */

/* These should not be used directly. Use the Py_UNICODE_IS* and
   Py_UNICODE_TO* macros instead.

   These APIs are implemented in Objects/unicodectype.c.

*/

PyAPI_FUNC(int) _PyUnicode_IsLowercase(
    Py_UCS4 ch       /* Unicode character */
    );

PyAPI_FUNC(int) _PyUnicode_IsUppercase(
    Py_UCS4 ch       /* Unicode character */
    );

PyAPI_FUNC(int) _PyUnicode_IsTitlecase(
    Py_UCS4 ch       /* Unicode character */
    );

PyAPI_FUNC(int) _PyUnicode_IsXidStart(
    Py_UCS4 ch       /* Unicode character */
    );

PyAPI_FUNC(int) _PyUnicode_IsXidContinue(
    Py_UCS4 ch       /* Unicode character */
    );

PyAPI_FUNC(int) _PyUnicode_IsWhitespace(
    const Py_UCS4 ch         /* Unicode character */
    );

PyAPI_FUNC(int) _PyUnicode_IsLinebreak(
    const Py_UCS4 ch         /* Unicode character */
    );

/* Py_DEPRECATED(3.3) */ PyAPI_FUNC(Py_UCS4) _PyUnicode_ToLowercase(
    Py_UCS4 ch       /* Unicode character */
    );

/* Py_DEPRECATED(3.3) */ PyAPI_FUNC(Py_UCS4) _PyUnicode_ToUppercase(
    Py_UCS4 ch       /* Unicode character */
    );

Py_DEPRECATED(3.3) PyAPI_FUNC(Py_UCS4) _PyUnicode_ToTitlecase(
    Py_UCS4 ch       /* Unicode character */
    );

PyAPI_FUNC(int) _PyUnicode_ToLowerFull(
    Py_UCS4 ch,       /* Unicode character */
    Py_UCS4 *res
    );

PyAPI_FUNC(int) _PyUnicode_ToTitleFull(
    Py_UCS4 ch,       /* Unicode character */
    Py_UCS4 *res
    );

PyAPI_FUNC(int) _PyUnicode_ToUpperFull(
    Py_UCS4 ch,       /* Unicode character */
    Py_UCS4 *res
    );

PyAPI_FUNC(int) _PyUnicode_ToFoldedFull(
    Py_UCS4 ch,       /* Unicode character */
    Py_UCS4 *res
    );

PyAPI_FUNC(int) _PyUnicode_IsCaseIgnorable(
    Py_UCS4 ch         /* Unicode character */
    );

PyAPI_FUNC(int) _PyUnicode_IsCased(
    Py_UCS4 ch         /* Unicode character */
    );

PyAPI_FUNC(int) _PyUnicode_ToDecimalDigit(
    Py_UCS4 ch       /* Unicode character */
    );

PyAPI_FUNC(int) _PyUnicode_ToDigit(
    Py_UCS4 ch       /* Unicode character */
    );

PyAPI_FUNC(double) _PyUnicode_ToNumeric(
    Py_UCS4 ch       /* Unicode character */
    );

PyAPI_FUNC(int) _PyUnicode_IsDecimalDigit(
    Py_UCS4 ch       /* Unicode character */
    );

PyAPI_FUNC(int) _PyUnicode_IsDigit(
    Py_UCS4 ch       /* Unicode character */
    );

PyAPI_FUNC(int) _PyUnicode_IsNumeric(
    Py_UCS4 ch       /* Unicode character */
    );

PyAPI_FUNC(int) _PyUnicode_IsPrintable(
    Py_UCS4 ch       /* Unicode character */
    );

PyAPI_FUNC(int) _PyUnicode_IsAlpha(
    Py_UCS4 ch       /* Unicode character */
    );

// Helper array used by Py_UNICODE_ISSPACE().
PyAPI_DATA(const unsigned char) _Py_ascii_whitespace[];

// Since splitting on whitespace is an important use case, and
// whitespace in most situations is solely ASCII whitespace, we
// optimize for the common case by using a quick look-up table
// _Py_ascii_whitespace (see below) with an inlined check.
static inline int Py_UNICODE_ISSPACE(Py_UCS4 ch) {
    if (ch < 128) {
        return _Py_ascii_whitespace[ch];
    }
    return _PyUnicode_IsWhitespace(ch);
}

#define Py_UNICODE_ISLOWER(ch) _PyUnicode_IsLowercase(ch)
#define Py_UNICODE_ISUPPER(ch) _PyUnicode_IsUppercase(ch)
#define Py_UNICODE_ISTITLE(ch) _PyUnicode_IsTitlecase(ch)
#define Py_UNICODE_ISLINEBREAK(ch) _PyUnicode_IsLinebreak(ch)

#define Py_UNICODE_TOLOWER(ch) _PyUnicode_ToLowercase(ch)
#define Py_UNICODE_TOUPPER(ch) _PyUnicode_ToUppercase(ch)
#define Py_UNICODE_TOTITLE(ch) _PyUnicode_ToTitlecase(ch)

#define Py_UNICODE_ISDECIMAL(ch) _PyUnicode_IsDecimalDigit(ch)
#define Py_UNICODE_ISDIGIT(ch) _PyUnicode_IsDigit(ch)
#define Py_UNICODE_ISNUMERIC(ch) _PyUnicode_IsNumeric(ch)
#define Py_UNICODE_ISPRINTABLE(ch) _PyUnicode_IsPrintable(ch)

#define Py_UNICODE_TODECIMAL(ch) _PyUnicode_ToDecimalDigit(ch)
#define Py_UNICODE_TODIGIT(ch) _PyUnicode_ToDigit(ch)
#define Py_UNICODE_TONUMERIC(ch) _PyUnicode_ToNumeric(ch)

#define Py_UNICODE_ISALPHA(ch) _PyUnicode_IsAlpha(ch)

static inline int Py_UNICODE_ISALNUM(Py_UCS4 ch) {
   return (Py_UNICODE_ISALPHA(ch)
           || Py_UNICODE_ISDECIMAL(ch)
           || Py_UNICODE_ISDIGIT(ch)
           || Py_UNICODE_ISNUMERIC(ch));
}


/* === Misc functions ===================================================== */

PyAPI_FUNC(PyObject*) _PyUnicode_FormatLong(PyObject *, int, int, int);

/* Return an interned Unicode object for an Identifier; may fail if there is no memory.*/
PyAPI_FUNC(PyObject*) _PyUnicode_FromId(_Py_Identifier*);

/* Fast equality check when the inputs are known to be exact unicode types
   and where the hash values are equal (i.e. a very probable match) */
PyAPI_FUNC(int) _PyUnicode_EQ(PyObject *, PyObject *);

/* Equality check. */
PyAPI_FUNC(int) _PyUnicode_Equal(PyObject *, PyObject *);

PyAPI_FUNC(int) _PyUnicode_WideCharString_Converter(PyObject *, void *);
PyAPI_FUNC(int) _PyUnicode_WideCharString_Opt_Converter(PyObject *, void *);

PyAPI_FUNC(Py_ssize_t) _PyUnicode_ScanIdentifier(PyObject *);
