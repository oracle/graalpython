/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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
#ifndef Py_CPYTHON_BYTESOBJECT_H
#  error "this header file must not be included directly"
#endif

typedef struct {
    PyObject_VAR_HEAD
    Py_hash_t ob_shash;
    // Truffle change: char ob_sval[1] doesn't work for us in Sulong
    char *ob_sval;

    /* Invariants:
     *     ob_sval contains space for 'ob_size+1' elements.
     *     ob_sval[ob_size] == 0.
     *     ob_shash is the hash of the string or -1 if not computed yet.
     */
} PyBytesObject;

PyAPI_FUNC(int) _PyBytes_Resize(PyObject **, Py_ssize_t);
PyAPI_FUNC(PyObject*) _PyBytes_FormatEx(
    const char *format,
    Py_ssize_t format_len,
    PyObject *args,
    int use_bytearray);
PyAPI_FUNC(PyObject*) _PyBytes_FromHex(
    PyObject *string,
    int use_bytearray);

/* Helper for PyBytes_DecodeEscape that detects invalid escape chars. */
PyAPI_FUNC(PyObject *) _PyBytes_DecodeEscape(const char *, Py_ssize_t,
                                             const char *, const char **);

/* Macro, trading safety for speed */
#define PyBytes_AS_STRING(op) (assert(PyBytes_Check(op)), \
                                (((PyBytesObject *)(op))->ob_sval))
#define PyBytes_GET_SIZE(op)  (assert(PyBytes_Check(op)),Py_SIZE(op))

/* _PyBytes_Join(sep, x) is like sep.join(x).  sep must be PyBytesObject*,
   x must be an iterable object. */
PyAPI_FUNC(PyObject *) _PyBytes_Join(PyObject *sep, PyObject *x);


/* The _PyBytesWriter structure is big: it contains an embedded "stack buffer".
   A _PyBytesWriter variable must be declared at the end of variables in a
   function to optimize the memory allocation on the stack. */
typedef struct {
    /* bytes, bytearray or NULL (when the small buffer is used) */
    PyObject *buffer;

    /* Number of allocated size. */
    Py_ssize_t allocated;

    /* Minimum number of allocated bytes,
       incremented by _PyBytesWriter_Prepare() */
    Py_ssize_t min_size;

    /* If non-zero, use a bytearray instead of a bytes object for buffer. */
    int use_bytearray;

    /* If non-zero, overallocate the buffer (default: 0).
       This flag must be zero if use_bytearray is non-zero. */
    int overallocate;

    /* Stack buffer */
    int use_small_buffer;
    char small_buffer[512];
} _PyBytesWriter;

/* Initialize a bytes writer

   By default, the overallocation is disabled. Set the overallocate attribute
   to control the allocation of the buffer. */
PyAPI_FUNC(void) _PyBytesWriter_Init(_PyBytesWriter *writer);

/* Get the buffer content and reset the writer.
   Return a bytes object, or a bytearray object if use_bytearray is non-zero.
   Raise an exception and return NULL on error. */
PyAPI_FUNC(PyObject *) _PyBytesWriter_Finish(_PyBytesWriter *writer,
    void *str);

/* Deallocate memory of a writer (clear its internal buffer). */
PyAPI_FUNC(void) _PyBytesWriter_Dealloc(_PyBytesWriter *writer);

/* Allocate the buffer to write size bytes.
   Return the pointer to the beginning of buffer data.
   Raise an exception and return NULL on error. */
PyAPI_FUNC(void*) _PyBytesWriter_Alloc(_PyBytesWriter *writer,
    Py_ssize_t size);

/* Ensure that the buffer is large enough to write *size* bytes.
   Add size to the writer minimum size (min_size attribute).

   str is the current pointer inside the buffer.
   Return the updated current pointer inside the buffer.
   Raise an exception and return NULL on error. */
PyAPI_FUNC(void*) _PyBytesWriter_Prepare(_PyBytesWriter *writer,
    void *str,
    Py_ssize_t size);

/* Resize the buffer to make it larger.
   The new buffer may be larger than size bytes because of overallocation.
   Return the updated current pointer inside the buffer.
   Raise an exception and return NULL on error.

   Note: size must be greater than the number of allocated bytes in the writer.

   This function doesn't use the writer minimum size (min_size attribute).

   See also _PyBytesWriter_Prepare().
   */
PyAPI_FUNC(void*) _PyBytesWriter_Resize(_PyBytesWriter *writer,
    void *str,
    Py_ssize_t size);

/* Write bytes.
   Raise an exception and return NULL on error. */
PyAPI_FUNC(void*) _PyBytesWriter_WriteBytes(_PyBytesWriter *writer,
    void *str,
    const void *bytes,
    Py_ssize_t size);
