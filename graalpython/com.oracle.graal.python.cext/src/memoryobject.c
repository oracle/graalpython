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

/* Macros taken from CPython */
/* Memoryview buffer properties */
#define MV_C_CONTIGUOUS(flags) (flags&(_Py_MEMORYVIEW_SCALAR|_Py_MEMORYVIEW_C))
#define MV_F_CONTIGUOUS(flags) \
    (flags&(_Py_MEMORYVIEW_SCALAR|_Py_MEMORYVIEW_FORTRAN))
#define MV_ANY_CONTIGUOUS(flags) \
    (flags&(_Py_MEMORYVIEW_SCALAR|_Py_MEMORYVIEW_C|_Py_MEMORYVIEW_FORTRAN))

/* getbuffer() requests */
#define REQ_INDIRECT(flags) ((flags&PyBUF_INDIRECT) == PyBUF_INDIRECT)
#define REQ_C_CONTIGUOUS(flags) ((flags&PyBUF_C_CONTIGUOUS) == PyBUF_C_CONTIGUOUS)
#define REQ_F_CONTIGUOUS(flags) ((flags&PyBUF_F_CONTIGUOUS) == PyBUF_F_CONTIGUOUS)
#define REQ_ANY_CONTIGUOUS(flags) ((flags&PyBUF_ANY_CONTIGUOUS) == PyBUF_ANY_CONTIGUOUS)
#define REQ_STRIDES(flags) ((flags&PyBUF_STRIDES) == PyBUF_STRIDES)
#define REQ_SHAPE(flags) ((flags&PyBUF_ND) == PyBUF_ND)
#define REQ_WRITABLE(flags) (flags&PyBUF_WRITABLE)
#define REQ_FORMAT(flags) (flags&PyBUF_FORMAT)

#define BASE_INACCESSIBLE(mv) \
    (((PyMemoryViewObject *)mv)->flags&_Py_MEMORYVIEW_RELEASED)
#define CHECK_RELEASED(mv) \
    if (BASE_INACCESSIBLE(mv)) {                                  \
        PyErr_SetString(PyExc_ValueError,                         \
            "operation forbidden on released memoryview object"); \
        return NULL;                                              \
    }
#define CHECK_RELEASED_INT(mv) \
    if (BASE_INACCESSIBLE(mv)) {                                  \
        PyErr_SetString(PyExc_ValueError,                         \
            "operation forbidden on released memoryview object"); \
        return -1;                                                \
    }

/* called from memoryview implementation to do pointer arithmetics currently not possible from Java */
int8_t* truffle_add_suboffset(int8_t *ptr, Py_ssize_t offset, Py_ssize_t suboffset) {
        return *(int8_t**)(ptr + offset) + suboffset;
}

/* called back from the above upcall only if the object was native */
PyObject* PyTruffle_MemoryViewFromObject(PyObject *v, int flags) {
    if (PyObject_CheckBuffer(v)) {
        Py_buffer* buffer = malloc(sizeof(Py_buffer));
        if (PyObject_GetBuffer(v, buffer, flags) < 0) {
            return NULL;
        }
        Py_ssize_t ndim = buffer->ndim;
        int needs_release = 0;
        if (buffer->obj != NULL) {
            PyBufferProcs *pb;
            pb = Py_TYPE(buffer->obj)->tp_as_buffer;
            if (pb) {
                needs_release = pb->bf_releasebuffer != NULL;
            }
        }
        PyObject *mv = GraalPyTruffle_MemoryViewFromBuffer(
                needs_release ? buffer : NULL, /* We only need the ptr for the release */
                buffer->obj,
                buffer->len,
                buffer->readonly,
                buffer->itemsize,
                buffer->format ? buffer->format : "B",
                buffer->ndim,
                buffer->buf,
                buffer->shape,
                buffer->strides,
                buffer->suboffsets);
        if (!needs_release) {
            free(buffer);
        }
        return mv;
    }

    PyErr_Format(PyExc_TypeError,
        "memoryview: a bytes-like object is required, not '%.200s'",
        Py_TYPE(v)->tp_name);
    return NULL;
}

Py_buffer* _PyMemoryView_GetBuffer(PyObject* op) {
	return (&((PyMemoryViewObject *)(op))->view);
}

/* Release buffer struct allocated in PyTruffle_MemoryViewFromObject */
void PyTruffle_ReleaseBuffer(Py_buffer* buffer) {
    if (buffer->obj != NULL) {
        PyBufferProcs *pb;
        pb = Py_TYPE(buffer->obj)->tp_as_buffer;
        if (pb) {
            pb->bf_releasebuffer(buffer->obj, buffer);
        }
    }
    free(buffer);
}

PyObject* PyMemoryView_FromBuffer(Py_buffer *buffer) {
    Py_ssize_t ndim = buffer->ndim;
    if (buffer->buf == NULL) {
        PyErr_SetString(PyExc_ValueError,
            "PyMemoryView_FromBuffer(): info->buf must not be NULL");
        return NULL;
    }
    return GraalPyTruffle_MemoryViewFromBuffer(
            NULL,
            NULL,
            buffer->len,
            buffer->readonly,
            buffer->itemsize,
            buffer->format ? buffer->format : "B",
            buffer->ndim,
            buffer->buf,
            buffer->shape,
            buffer->strides,
            buffer->suboffsets);
}

PyObject *PyMemoryView_FromMemory(char *mem, Py_ssize_t size, int flags) {
    assert(mem != NULL);
    assert(flags == PyBUF_READ || flags == PyBUF_WRITE);
    int readonly = (flags == PyBUF_WRITE) ? 0 : 1;
    return GraalPyTruffle_MemoryViewFromBuffer(
            NULL, NULL, size, readonly, 1, "B", 1, (int8_t*)mem, NULL, NULL, NULL);
}

/* Taken from CPython memoryobject.c: memory_getbuf */
int memoryview_getbuffer(PyMemoryViewObject *self, Py_buffer *view, int flags)
{
    Py_buffer *base = &self->view;
    int baseflags = self->flags;

    CHECK_RELEASED_INT(self);

    /* start with complete information */
    //*view = *base;
    view->buf = base->buf;
    view->format = base->format;
    view->itemsize = base->itemsize;
    view->len = base->len;
    view->ndim = base->ndim;
    view->readonly = base->readonly;
    view->shape = base->shape;
    view->strides = base->strides;
    view->suboffsets = base->suboffsets;
    view->obj = NULL;

    if (REQ_WRITABLE(flags) && base->readonly) {
        PyErr_SetString(PyExc_BufferError,
            "memoryview: underlying buffer is not writable");
        return -1;
    }
    if (!REQ_FORMAT(flags)) {
        /* NULL indicates that the buffer's data type has been cast to 'B'.
           view->itemsize is the _previous_ itemsize. If shape is present,
           the equality product(shape) * itemsize = len still holds at this
           point. The equality calcsize(format) = itemsize does _not_ hold
           from here on! */
        view->format = NULL;
    }

    if (REQ_C_CONTIGUOUS(flags) && !MV_C_CONTIGUOUS(baseflags)) {
        PyErr_SetString(PyExc_BufferError,
            "memoryview: underlying buffer is not C-contiguous");
        return -1;
    }
    if (REQ_F_CONTIGUOUS(flags) && !MV_F_CONTIGUOUS(baseflags)) {
        PyErr_SetString(PyExc_BufferError,
            "memoryview: underlying buffer is not Fortran contiguous");
        return -1;
    }
    if (REQ_ANY_CONTIGUOUS(flags) && !MV_ANY_CONTIGUOUS(baseflags)) {
        PyErr_SetString(PyExc_BufferError,
            "memoryview: underlying buffer is not contiguous");
        return -1;
    }
    if (!REQ_INDIRECT(flags) && (baseflags & _Py_MEMORYVIEW_PIL)) {
        PyErr_SetString(PyExc_BufferError,
            "memoryview: underlying buffer requires suboffsets");
        return -1;
    }
    if (!REQ_STRIDES(flags)) {
        if (!MV_C_CONTIGUOUS(baseflags)) {
            PyErr_SetString(PyExc_BufferError,
                "memoryview: underlying buffer is not C-contiguous");
            return -1;
        }
        view->strides = NULL;
    }
    if (!REQ_SHAPE(flags)) {
        /* PyBUF_SIMPLE or PyBUF_WRITABLE: at this point buf is C-contiguous,
           so base->buf = ndbuf->data. */
        if (view->format != NULL) {
            /* PyBUF_SIMPLE|PyBUF_FORMAT and PyBUF_WRITABLE|PyBUF_FORMAT do
               not make sense. */
            PyErr_Format(PyExc_BufferError,
                "memoryview: cannot cast to unsigned bytes if the format flag "
                "is present");
            return -1;
        }
        /* product(shape) * itemsize = len and calcsize(format) = itemsize
           do _not_ hold from here on! */
        view->ndim = 1;
        view->shape = NULL;
    }


    view->obj = (PyObject *)self;
    Py_INCREF(view->obj);
    self->exports++;

    return 0;
}

void memoryview_releasebuffer(PyMemoryViewObject *self, Py_buffer *view) {
        self->exports--;
}
