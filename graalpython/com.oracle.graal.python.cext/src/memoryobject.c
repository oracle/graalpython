/* Copyright (c) 2018, 2024, Oracle and/or its affiliates.
 * Copyright (C) 1996-2020 Python Software Foundation
 *
 * Licensed under the PYTHON SOFTWARE FOUNDATION LICENSE VERSION 2
 */
#include "capi.h"

/****************************************************************************/
/*                             MemoryView Object                            */
/****************************************************************************/

/* In the process of breaking reference cycles mbuf_release() can be
   called before memory_release(). */
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

/* See gh-92888. These macros signal that we need to check the memoryview
   again due to possible read after frees. */
#define CHECK_RELEASED_AGAIN(mv) CHECK_RELEASED(mv)
#define CHECK_RELEASED_INT_AGAIN(mv) CHECK_RELEASED_INT(mv)

#define CHECK_LIST_OR_TUPLE(v) \
    if (!PyList_Check(v) && !PyTuple_Check(v)) { \
        PyErr_SetString(PyExc_TypeError,         \
            #v " must be a list or a tuple");    \
        return NULL;                             \
    }

#define VIEW_ADDR(mv) (&((PyMemoryViewObject *)mv)->view)

/* Check for the presence of suboffsets in the first dimension. */
#define HAVE_PTR(suboffsets, dim) (suboffsets && suboffsets[dim] >= 0)
/* Adjust ptr if suboffsets are present. */
#define ADJUST_PTR(ptr, suboffsets, dim) \
    (HAVE_PTR(suboffsets, dim) ? *((char**)ptr) + suboffsets[dim] : ptr)

/* Memoryview buffer properties */
#define MV_C_CONTIGUOUS(flags) (flags&(_Py_MEMORYVIEW_SCALAR|_Py_MEMORYVIEW_C))
#define MV_F_CONTIGUOUS(flags) \
    (flags&(_Py_MEMORYVIEW_SCALAR|_Py_MEMORYVIEW_FORTRAN))
#define MV_ANY_CONTIGUOUS(flags) \
    (flags&(_Py_MEMORYVIEW_SCALAR|_Py_MEMORYVIEW_C|_Py_MEMORYVIEW_FORTRAN))

/* Fast contiguity test. Caller must ensure suboffsets==NULL and ndim==1. */
#define MV_CONTIGUOUS_NDIM1(view) \
    ((view)->shape[0] == 1 || (view)->strides[0] == (view)->itemsize)

/* getbuffer() requests */
#define REQ_INDIRECT(flags) ((flags&PyBUF_INDIRECT) == PyBUF_INDIRECT)
#define REQ_C_CONTIGUOUS(flags) ((flags&PyBUF_C_CONTIGUOUS) == PyBUF_C_CONTIGUOUS)
#define REQ_F_CONTIGUOUS(flags) ((flags&PyBUF_F_CONTIGUOUS) == PyBUF_F_CONTIGUOUS)
#define REQ_ANY_CONTIGUOUS(flags) ((flags&PyBUF_ANY_CONTIGUOUS) == PyBUF_ANY_CONTIGUOUS)
#define REQ_STRIDES(flags) ((flags&PyBUF_STRIDES) == PyBUF_STRIDES)
#define REQ_SHAPE(flags) ((flags&PyBUF_ND) == PyBUF_ND)
#define REQ_WRITABLE(flags) (flags&PyBUF_WRITABLE)
#define REQ_FORMAT(flags) (flags&PyBUF_FORMAT)


/**************************************************************************/
/*                       Copy memoryview buffers                          */
/**************************************************************************/

/* The functions in this section take a source and a destination buffer
   with the same logical structure: format, itemsize, ndim and shape
   are identical, with ndim > 0.

   NOTE: All buffers are assumed to have PyBUF_FULL information, which
   is the case for memoryviews! */


/* Assumptions: ndim >= 1. The macro tests for a corner case that should
   perhaps be explicitly forbidden in the PEP. */
#define HAVE_SUBOFFSETS_IN_LAST_DIM(view) \
    (view->suboffsets && view->suboffsets[dest->ndim-1] >= 0)

static inline int
last_dim_is_contiguous(const Py_buffer *dest, const Py_buffer *src)
{
    assert(dest->ndim > 0 && src->ndim > 0);
    return (!HAVE_SUBOFFSETS_IN_LAST_DIM(dest) &&
            !HAVE_SUBOFFSETS_IN_LAST_DIM(src) &&
            dest->strides[dest->ndim-1] == dest->itemsize &&
            src->strides[src->ndim-1] == src->itemsize);
}

/* This is not a general function for determining format equivalence.
   It is used in copy_single() and copy_buffer() to weed out non-matching
   formats. Skipping the '@' character is specifically used in slice
   assignments, where the lvalue is already known to have a single character
   format. This is a performance hack that could be rewritten (if properly
   benchmarked). */
static inline int
equiv_format(const Py_buffer *dest, const Py_buffer *src)
{
    const char *dfmt, *sfmt;

    assert(dest->format && src->format);
    dfmt = dest->format[0] == '@' ? dest->format+1 : dest->format;
    sfmt = src->format[0] == '@' ? src->format+1 : src->format;

    if (strcmp(dfmt, sfmt) != 0 ||
        dest->itemsize != src->itemsize) {
        return 0;
    }

    return 1;
}

/* Two shapes are equivalent if they are either equal or identical up
   to a zero element at the same position. For example, in NumPy arrays
   the shapes [1, 0, 5] and [1, 0, 7] are equivalent. */
static inline int
equiv_shape(const Py_buffer *dest, const Py_buffer *src)
{
    int i;

    if (dest->ndim != src->ndim)
        return 0;

    for (i = 0; i < dest->ndim; i++) {
        if (dest->shape[i] != src->shape[i])
            return 0;
        if (dest->shape[i] == 0)
            break;
    }

    return 1;
}

/* Check that the logical structure of the destination and source buffers
   is identical. */
static int
equiv_structure(const Py_buffer *dest, const Py_buffer *src)
{
    if (!equiv_format(dest, src) ||
        !equiv_shape(dest, src)) {
        PyErr_SetString(PyExc_ValueError,
            "memoryview assignment: lvalue and rvalue have different "
            "structures");
        return 0;
    }

    return 1;
}

/* Base case for recursive multi-dimensional copying. Contiguous arrays are
   copied with very little overhead. Assumptions: ndim == 1, mem == NULL or
   sizeof(mem) == shape[0] * itemsize. */
static void
copy_base(const Py_ssize_t *shape, Py_ssize_t itemsize,
          char *dptr, const Py_ssize_t *dstrides, const Py_ssize_t *dsuboffsets,
          char *sptr, const Py_ssize_t *sstrides, const Py_ssize_t *ssuboffsets,
          char *mem)
{
    if (mem == NULL) { /* contiguous */
        Py_ssize_t size = shape[0] * itemsize;
        if (dptr + size < sptr || sptr + size < dptr)
            memcpy(dptr, sptr, size); /* no overlapping */
        else
            memmove(dptr, sptr, size);
    }
    else {
        char *p;
        Py_ssize_t i;
        for (i=0, p=mem; i < shape[0]; p+=itemsize, sptr+=sstrides[0], i++) {
            char *xsptr = ADJUST_PTR(sptr, ssuboffsets, 0);
            memcpy(p, xsptr, itemsize);
        }
        for (i=0, p=mem; i < shape[0]; p+=itemsize, dptr+=dstrides[0], i++) {
            char *xdptr = ADJUST_PTR(dptr, dsuboffsets, 0);
            memcpy(xdptr, p, itemsize);
        }
    }

}

/* Recursively copy a source buffer to a destination buffer. The two buffers
   have the same ndim, shape and itemsize. */
static void
copy_rec(const Py_ssize_t *shape, Py_ssize_t ndim, Py_ssize_t itemsize,
         char *dptr, const Py_ssize_t *dstrides, const Py_ssize_t *dsuboffsets,
         char *sptr, const Py_ssize_t *sstrides, const Py_ssize_t *ssuboffsets,
         char *mem)
{
    Py_ssize_t i;

    assert(ndim >= 1);

    if (ndim == 1) {
        copy_base(shape, itemsize,
                  dptr, dstrides, dsuboffsets,
                  sptr, sstrides, ssuboffsets,
                  mem);
        return;
    }

    for (i = 0; i < shape[0]; dptr+=dstrides[0], sptr+=sstrides[0], i++) {
        char *xdptr = ADJUST_PTR(dptr, dsuboffsets, 0);
        char *xsptr = ADJUST_PTR(sptr, ssuboffsets, 0);

        copy_rec(shape+1, ndim-1, itemsize,
                 xdptr, dstrides+1, dsuboffsets ? dsuboffsets+1 : NULL,
                 xsptr, sstrides+1, ssuboffsets ? ssuboffsets+1 : NULL,
                 mem);
    }
}

/* Faster copying of one-dimensional arrays. */
static int
copy_single(PyMemoryViewObject *self, const Py_buffer *dest, const Py_buffer *src)
{
    CHECK_RELEASED_INT_AGAIN(self);
    char *mem = NULL;

    assert(dest->ndim == 1);

    if (!equiv_structure(dest, src))
        return -1;

    if (!last_dim_is_contiguous(dest, src)) {
        mem = PyMem_Malloc(dest->shape[0] * dest->itemsize);
        if (mem == NULL) {
            PyErr_NoMemory();
            return -1;
        }
    }

    copy_base(dest->shape, dest->itemsize,
              dest->buf, dest->strides, dest->suboffsets,
              src->buf, src->strides, src->suboffsets,
              mem);

    if (mem)
        PyMem_Free(mem);

    return 0;
}

/* Recursively copy src to dest. Both buffers must have the same basic
   structure. Copying is atomic, the function never fails with a partial
   copy. */
static int
copy_buffer(const Py_buffer *dest, const Py_buffer *src)
{
    char *mem = NULL;

    assert(dest->ndim > 0);

    if (!equiv_structure(dest, src))
        return -1;

    if (!last_dim_is_contiguous(dest, src)) {
        mem = PyMem_Malloc(dest->shape[dest->ndim-1] * dest->itemsize);
        if (mem == NULL) {
            PyErr_NoMemory();
            return -1;
        }
    }

    copy_rec(dest->shape, dest->ndim, dest->itemsize,
             dest->buf, dest->strides, dest->suboffsets,
             src->buf, src->strides, src->suboffsets,
             mem);

    if (mem)
        PyMem_Free(mem);

    return 0;
}

/* Initialize strides for a C-contiguous array. */
static inline void
init_strides_from_shape(Py_buffer *view)
{
    Py_ssize_t i;

    assert(view->ndim > 0);

    view->strides[view->ndim-1] = view->itemsize;
    for (i = view->ndim-2; i >= 0; i--)
        view->strides[i] = view->strides[i+1] * view->shape[i+1];
}

/* Initialize strides for a Fortran-contiguous array. */
static inline void
init_fortran_strides_from_shape(Py_buffer *view)
{
    Py_ssize_t i;

    assert(view->ndim > 0);

    view->strides[0] = view->itemsize;
    for (i = 1; i < view->ndim; i++)
        view->strides[i] = view->strides[i-1] * view->shape[i-1];
}

/* Copy src to a contiguous representation. order is one of 'C', 'F' (Fortran)
   or 'A' (Any). Assumptions: src has PyBUF_FULL information, src->ndim >= 1,
   len(mem) == src->len. */
static int
buffer_to_contiguous(char *mem, const Py_buffer *src, char order)
{
    Py_buffer dest;
    Py_ssize_t *strides;
    int ret;

    assert(src->ndim >= 1);
    assert(src->shape != NULL);
    assert(src->strides != NULL);

    strides = PyMem_Malloc(src->ndim * (sizeof *src->strides));
    if (strides == NULL) {
        PyErr_NoMemory();
        return -1;
    }

    /* initialize dest */
    dest = *src;
    dest.buf = mem;
    /* shape is constant and shared: the logical representation of the
       array is unaltered. */

    /* The physical representation determined by strides (and possibly
       suboffsets) may change. */
    dest.strides = strides;
    if (order == 'C' || order == 'A') {
        init_strides_from_shape(&dest);
    }
    else {
        init_fortran_strides_from_shape(&dest);
    }

    dest.suboffsets = NULL;

    ret = copy_buffer(&dest, src);

    PyMem_Free(strides);
    return ret;
}


/****************************************************************************/
/*                               Constructors                               */
/****************************************************************************/

/* Initialize values that are shared with the managed buffer. */
static inline void
init_shared_values(Py_buffer *dest, const Py_buffer *src)
{
    dest->obj = src->obj;
    dest->buf = src->buf;
    dest->len = src->len;
    dest->itemsize = src->itemsize;
    dest->readonly = src->readonly;
    dest->format = src->format ? src->format : "B";
    dest->internal = src->internal;
}

/* Copy shape and strides. Reconstruct missing values. */
static void
init_shape_strides(Py_buffer *dest, const Py_buffer *src)
{
    Py_ssize_t i;

    if (src->ndim == 0) {
        dest->shape = NULL;
        dest->strides = NULL;
        return;
    }
    if (src->ndim == 1) {
        dest->shape[0] = src->shape ? src->shape[0] : src->len / src->itemsize;
        dest->strides[0] = src->strides ? src->strides[0] : src->itemsize;
        return;
    }

    for (i = 0; i < src->ndim; i++)
        dest->shape[i] = src->shape[i];
    if (src->strides) {
        for (i = 0; i < src->ndim; i++)
            dest->strides[i] = src->strides[i];
    }
    else {
        init_strides_from_shape(dest);
    }
}

static inline void
init_suboffsets(Py_buffer *dest, const Py_buffer *src)
{
    Py_ssize_t i;

    if (src->suboffsets == NULL) {
        dest->suboffsets = NULL;
        return;
    }
    for (i = 0; i < src->ndim; i++)
        dest->suboffsets[i] = src->suboffsets[i];
}

/****************************************************************************/
/*                         Previously in abstract.c                         */
/****************************************************************************/

typedef struct {
    Py_buffer view;
    Py_ssize_t array[1];
} Py_buffer_full;

int
PyBuffer_ToContiguous(void *buf, const Py_buffer *src, Py_ssize_t len, char order)
{
    Py_buffer_full *fb = NULL;
    int ret;

    assert(order == 'C' || order == 'F' || order == 'A');

    if (len != src->len) {
        PyErr_SetString(PyExc_ValueError,
            "PyBuffer_ToContiguous: len != view->len");
        return -1;
    }

    if (PyBuffer_IsContiguous(src, order)) {
        memcpy((char *)buf, src->buf, len);
        return 0;
    }

    /* buffer_to_contiguous() assumes PyBUF_FULL */
    fb = PyMem_Malloc(sizeof *fb + 3 * src->ndim * (sizeof *fb->array));
    if (fb == NULL) {
        PyErr_NoMemory();
        return -1;
    }
    fb->view.ndim = src->ndim;
    fb->view.shape = fb->array;
    fb->view.strides = fb->array + src->ndim;
    fb->view.suboffsets = fb->array + 2 * src->ndim;

    init_shared_values(&fb->view, src);
    init_shape_strides(&fb->view, src);
    init_suboffsets(&fb->view, src);

    src = &fb->view;

    ret = buffer_to_contiguous(buf, src, order);
    PyMem_Free(fb);
    return ret;
}

// Taken from memory_getbuf
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

// GraalPy additions
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

PyObject* PyMemoryView_FromBuffer(const Py_buffer *buffer) {
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
