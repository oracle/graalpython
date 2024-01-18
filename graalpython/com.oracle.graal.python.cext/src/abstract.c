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

typedef enum e_binop {
    ADD=0, SUB, MUL, TRUEDIV, LSHIFT, RSHIFT, OR, AND, XOR, FLOORDIV, MOD,
    INPLACE_OFFSET, MATRIX_MUL
} BinOp;

typedef enum e_unaryop {
	POS=0, NEG, INVERT
} UnaryOp;

static PyObject* null_error(void) {
    if (!PyErr_Occurred()) {
        PyErr_SetString(PyExc_SystemError, "null argument to internal routine");
    }
    return NULL;
}

static PyObject * do_unaryop(PyObject *v, UnaryOp unaryop) {
    return GraalPyTruffleNumber_UnaryOp(v, (int) unaryop);
}

MUST_INLINE static PyObject * do_binop(PyObject *v, PyObject *w, BinOp binop) {
    return GraalPyTruffleNumber_BinOp(v, w, (int) binop);
}

MUST_INLINE static PyObject * do_inplace_binop(PyObject *v, PyObject *w, BinOp binop) {
    return GraalPyTruffleNumber_InPlaceBinOp(v, w, (int) binop);
}

PyObject * PyNumber_Add(PyObject *o1, PyObject *o2) {
	return do_binop(o1, o2, ADD);
}

PyObject * PyNumber_Subtract(PyObject *o1, PyObject *o2) {
	return do_binop(o1, o2, SUB);
}

PyObject * PyNumber_Multiply(PyObject *o1, PyObject *o2) {
	return do_binop(o1, o2, MUL);
}


PyObject * PyNumber_MatrixMultiply(PyObject *o1, PyObject *o2) {
	return do_binop(o1, o2, MATRIX_MUL);
}

PyObject * PyNumber_TrueDivide(PyObject *o1, PyObject *o2) {
	return do_binop(o1, o2, TRUEDIV);
}

PyObject * PyNumber_FloorDivide(PyObject *o1, PyObject *o2) {
	return do_binop(o1, o2, FLOORDIV);
}

PyObject * PyNumber_Remainder(PyObject *o1, PyObject *o2) {
	return do_binop(o1, o2, MOD);
}

PyObject * PyNumber_Lshift(PyObject *o1, PyObject *o2) {
	return do_binop(o1, o2, LSHIFT);
}

PyObject * PyNumber_Rshift(PyObject *o1, PyObject *o2) {
	return do_binop(o1, o2, RSHIFT);
}

PyObject * PyNumber_Or(PyObject *o1, PyObject *o2) {
	return do_binop(o1, o2, OR);
}

PyObject * PyNumber_And(PyObject *o1, PyObject *o2) {
	return do_binop(o1, o2, AND);
}

PyObject * PyNumber_Xor(PyObject *o1, PyObject *o2) {
	return do_binop(o1, o2, XOR);
}

PyObject * PyNumber_Positive(PyObject *o) {
	return do_unaryop(o, POS);
}

PyObject * PyNumber_Negative(PyObject *o) {
	return do_unaryop(o, NEG);
}

PyObject * PyNumber_Invert(PyObject *o) {
	return do_unaryop(o, INVERT);
}

PyObject* PyNumber_InPlaceAdd(PyObject *o1, PyObject *o2) {
	return do_inplace_binop(o1, o2, ADD);
}

PyObject* PyNumber_InPlaceSubtract(PyObject *o1, PyObject *o2) {
	return do_inplace_binop(o1, o2, SUB);
}

PyObject* PyNumber_InPlaceMultiply(PyObject *o1, PyObject *o2) {
	return do_inplace_binop(o1, o2, MUL);
}

PyObject* PyNumber_InPlaceMatrixMultiply(PyObject *o1, PyObject *o2) {
	return do_inplace_binop(o1, o2, MATRIX_MUL);
}

PyObject* PyNumber_InPlaceFloorDivide(PyObject *o1, PyObject *o2) {
	return do_inplace_binop(o1, o2, FLOORDIV);
}

PyObject * PyNumber_InPlaceTrueDivide(PyObject *o1, PyObject *o2) {
    return do_inplace_binop(o1, o2, TRUEDIV);
}

PyObject* PyNumber_InPlaceRemainder(PyObject *o1, PyObject *o2) {
	return do_inplace_binop(o1, o2, MOD);
}

PyObject* PyNumber_InPlaceLshift(PyObject *o1, PyObject *o2) {
	return do_inplace_binop(o1, o2, LSHIFT);
}

PyObject* PyNumber_InPlaceRshift(PyObject *o1, PyObject *o2) {
	return do_inplace_binop(o1, o2, RSHIFT);
}

PyObject* PyNumber_InPlaceAnd(PyObject *o1, PyObject *o2) {
	return do_inplace_binop(o1, o2, AND);
}

PyObject* PyNumber_InPlaceXor(PyObject *o1, PyObject *o2) {
	return do_inplace_binop(o1, o2, XOR);
}

PyObject* PyNumber_InPlaceOr(PyObject *o1, PyObject *o2) {
	return do_inplace_binop(o1, o2, OR);
}

Py_ssize_t PyNumber_AsSsize_t(PyObject *item, PyObject *err) {
    Py_ssize_t result;
    PyObject *runerr;
    PyObject *value = PyNumber_Index(item);
    if (value == NULL) {
        return -1;
    }

    /* We're done if PyLong_AsSsize_t() returns without error. */
    result = PyLong_AsSsize_t(value);
    if (result != -1 || !(runerr = PyErr_Occurred())) {
    	return result;
    }

    /* Error handling code -- only manage OverflowError differently */
    if (!PyErr_GivenExceptionMatches(runerr, PyExc_OverflowError)) {
    	return result;
    }

    PyErr_Clear();
    /* If no error-handling desired then the default clipping
       is sufficient.
     */
    if (!err) {
        /* Whether or not it is less than or equal to
           zero is determined by the sign of ob_size
        */
        if (_PyLong_Sign(value) < 0)
            result = PY_SSIZE_T_MIN;
        else
            result = PY_SSIZE_T_MAX;
        return result;
    }
    else {
        /* Otherwise replace the error with caller's error object. */
    	PyErr_Format(err, "cannot fit '%s' into an index-sized integer", PyObject_Type(item));
    }

    Py_DECREF(value);
    return -1;
}

// downcall for native python objects
// taken from CPython "Objects/abstract.c PySequence_Check()"
PyAPI_FUNC(int) PyTruffle_PySequence_Check(PyObject *s) {
    if (PyDict_Check(s))
        return 0;
    PySequenceMethods* seq = Py_TYPE(s)->tp_as_sequence;
    return seq && seq->sq_item != NULL;
}

// downcall for native python objects
// partially taken from CPython "Objects/abstract.c/PySequence_GetItem"
PyAPI_FUNC(PyObject*) PyTruffle_PySequence_GetItem(PyObject *s, Py_ssize_t i)
{
    PySequenceMethods *m = Py_TYPE(s)->tp_as_sequence;
    if (m && m->sq_item) {
        if (i < 0) {
            if (m->sq_length) {
                Py_ssize_t l = (*m->sq_length)(s);
                if (l < 0) {
                    return NULL;
                }
                i += l;
            }
        }
        PyObject *res = m->sq_item(s, i);
        return res;
    }

    if (Py_TYPE(s)->tp_as_mapping && Py_TYPE(s)->tp_as_mapping->mp_subscript) {
        return PyErr_Format(PyExc_TypeError, "%.200s is not a sequence", Py_TYPE(s)->tp_name);
    }
    return PyErr_Format(PyExc_TypeError, "'%.200s' object does not support indexing", Py_TYPE(s)->tp_name);
}

// downcall for native python objects
// taken from CPython "Objects/abstract.c/Py_Sequence_Size"
PyAPI_FUNC(Py_ssize_t) PyTruffle_PySequence_Size(PyObject *s) {
    PySequenceMethods *seq;
    PyMappingMethods *m;

    if (s == NULL) {
        null_error();
        return -1;
    }

    seq = Py_TYPE(s)->tp_as_sequence;
    if (seq && seq->sq_length) {
        Py_ssize_t len = seq->sq_length(s);
        assert(len >= 0 || PyErr_Occurred());
        return len;
    }

    m = Py_TYPE(s)->tp_as_mapping;
    if (m && m->mp_length) {
        PyErr_Format(PyExc_TypeError, "PyTruffle_PySequence_Size(): object of type '%s' is not a sequence", Py_TYPE(s)->tp_name);
        return -1;
    }
    PyErr_Format(PyExc_TypeError, "PyTruffle_PySequence_Size(): object of type '%s' has no len()", Py_TYPE(s)->tp_name);
    return -1;    
}

// downcall for native python objects
// partially taken from CPython "Objects/abstract.c/Py_Sequence_SetItem
PyAPI_FUNC(int) PyTruffle_PySequence_SetItem(PyObject *s, Py_ssize_t i, PyObject *o)
{
    PySequenceMethods *m = Py_TYPE(s)->tp_as_sequence;
    if (m && m->sq_ass_item) {
        if (i < 0) {
            if (m->sq_length) {
                Py_ssize_t l = (*m->sq_length)(s);
                assert(_Py_CheckSlotResult(s, "__len__", l >= 0));
                if (l < 0) {
                    return -1;
                }
                i += l;
            }
        }
        int res = m->sq_ass_item(s, i, o);
        return res;
    }

    if (Py_TYPE(s)->tp_as_mapping && Py_TYPE(s)->tp_as_mapping->mp_ass_subscript) {
        return PyErr_Format(PyExc_TypeError, "%.200s is not a sequence", Py_TYPE(s)->tp_name);
    }
    return PyErr_Format(PyExc_TypeError, "'%.200s' object does not support item assignment", Py_TYPE(s)->tp_name);
}

// downcall for native python objects
// partially taken from CPython "Objects/abstract.c/Py_Sequence_SetItem
PyAPI_FUNC(int) PyTruffle_PySequence_DelItem(PyObject *s, Py_ssize_t i)
{
    PySequenceMethods *m = Py_TYPE(s)->tp_as_sequence;
    if (m && m->sq_ass_item) {
        if (i < 0) {
            if (m->sq_length) {
                Py_ssize_t l = (*m->sq_length)(s);
                assert(_Py_CheckSlotResult(s, "__len__", l >= 0));
                if (l < 0) {
                    return -1;
                }
                i += l;
            }
        }
        int res = m->sq_ass_item(s, i, (PyObject *)NULL);
        return res;
    }

    if (Py_TYPE(s)->tp_as_mapping && Py_TYPE(s)->tp_as_mapping->mp_ass_subscript) {
        return PyErr_Format(PyExc_TypeError, "%.200s is not a sequence", Py_TYPE(s)->tp_name);
    }
    return PyErr_Format(PyExc_TypeError, "'%.200s' object doesn't support item deletion", Py_TYPE(s)->tp_name);
}

PyObject * PySequence_Fast(PyObject *v, const char *m) {
    PyObject *res;
    if (v == NULL) {
        return null_error();
    }

    if (PyList_CheckExact(v) || PyTuple_CheckExact(v)) {
        Py_INCREF(v);
        return v;
    }

    return GraalPySequence_List(v);
}

PyObject * PyMapping_GetItemString(PyObject *o, const char *key) {
    return GraalPyTruffleObject_GetItemString(o, key);
}

// downcall for native python objects
// taken from CPython "Objects/abstract.c/PyObject_Size"
PyAPI_FUNC(Py_ssize_t) PyTruffle_PyObject_Size(PyObject *o) {
    PySequenceMethods *m;

    if (o == NULL) {
        null_error();
        return -1;
    }

    m = Py_TYPE(o)->tp_as_sequence;
    if (m && m->sq_length) {
        Py_ssize_t len = m->sq_length(o);
        assert(len >= 0 || PyErr_Occurred());
        return len;
    }

    return PyMapping_Size(o);
}

// downcall for native python objects
// taken from CPython "Objects/abstract.c PyMapping_Check"
PyAPI_FUNC(int) PyTruffle_PyMapping_Check(PyObject *o) {
    return o && Py_TYPE(o)->tp_as_mapping && Py_TYPE(o)->tp_as_mapping->mp_subscript;
}

// taken from CPython "Objects/abstract.c"
int PyObject_CheckBuffer(PyObject *obj) {
    PyBufferProcs *tp_as_buffer = Py_TYPE(obj)->tp_as_buffer;
    return (tp_as_buffer != NULL && tp_as_buffer->bf_getbuffer != NULL);
}

// taken from CPython "Objects/abstract.c"
int PyObject_GetBuffer(PyObject *obj, Py_buffer *view, int flags) {
    PyBufferProcs *pb = Py_TYPE(obj)->tp_as_buffer;

    if (pb == NULL || pb->bf_getbuffer == NULL) {
        PyErr_Format(PyExc_TypeError,
                     "a bytes-like object is required, not '%.100s'",
                     Py_TYPE(obj)->tp_name);
        return -1;
    }
    return (*pb->bf_getbuffer)(obj, view, flags);
}

// taken from CPython "Objects/abstract.c"
void PyBuffer_Release(Py_buffer *view) {
    PyObject *obj = view->obj;
    PyBufferProcs *pb;
    if (obj == NULL)
        return;
    pb = Py_TYPE(obj)->tp_as_buffer;
    if (pb && pb->bf_releasebuffer)
        pb->bf_releasebuffer(obj, view);
    view->obj = NULL;
    Py_DECREF(obj);
}

// taken from CPython "Objects/abstract.c"
/* we do this in native code since we need to fill in the values in a given 'Py_buffer' struct */
int PyBuffer_FillInfo(Py_buffer *view, PyObject *obj, void *buf, Py_ssize_t len, int readonly, int flags) {
    if (view == NULL) {
        PyErr_SetString(PyExc_BufferError,
            "PyBuffer_FillInfo: view==NULL argument is obsolete");
        return -1;
    }

    if (((flags & PyBUF_WRITABLE) == PyBUF_WRITABLE) &&
        (readonly == 1)) {
        PyErr_SetString(PyExc_BufferError,
                        "Object is not writable.");
        return -1;
    }

    view->obj = obj;
    if (obj)
        Py_INCREF(obj);
    view->buf = buf;
    view->len = len;
    view->readonly = readonly;
    view->itemsize = 1;
    view->format = NULL;
    if ((flags & PyBUF_FORMAT) == PyBUF_FORMAT)
        view->format = "B";
    view->ndim = 1;
    view->shape = NULL;
    if ((flags & PyBUF_ND) == PyBUF_ND)
        view->shape = &(view->len);
    view->strides = NULL;
    if ((flags & PyBUF_STRIDES) == PyBUF_STRIDES)
        view->strides = &(view->itemsize);
    view->suboffsets = NULL;
    view->internal = NULL;
    return 0;
}

// taken from CPython "Objects/abstract.c"
static int _IsFortranContiguous(const Py_buffer *view) {
    Py_ssize_t sd, dim;
    int i;

    /* 1) len = product(shape) * itemsize
       2) itemsize > 0
       3) len = 0 <==> exists i: shape[i] = 0 */
    if (view->len == 0) return 1;
    if (view->strides == NULL) {  /* C-contiguous by definition */
        /* Trivially F-contiguous */
        if (view->ndim <= 1) return 1;

        /* ndim > 1 implies shape != NULL */
        assert(view->shape != NULL);

        /* Effectively 1-d */
        sd = 0;
        for (i=0; i<view->ndim; i++) {
            if (view->shape[i] > 1) sd += 1;
        }
        return sd <= 1;
    }

    /* strides != NULL implies both of these */
    assert(view->ndim > 0);
    assert(view->shape != NULL);

    sd = view->itemsize;
    for (i=0; i<view->ndim; i++) {
        dim = view->shape[i];
        if (dim > 1 && view->strides[i] != sd) {
            return 0;
        }
        sd *= dim;
    }
    return 1;
}

// taken from CPython "Objects/abstract.c"
static int _IsCContiguous(const Py_buffer *view) {
    Py_ssize_t sd, dim;
    int i;

    /* 1) len = product(shape) * itemsize
       2) itemsize > 0
       3) len = 0 <==> exists i: shape[i] = 0 */
    if (view->len == 0) return 1;
    if (view->strides == NULL) return 1; /* C-contiguous by definition */

    /* strides != NULL implies both of these */
    assert(view->ndim > 0);
    assert(view->shape != NULL);

    sd = view->itemsize;
    for (i=view->ndim-1; i>=0; i--) {
        dim = view->shape[i];
        if (dim > 1 && view->strides[i] != sd) {
            return 0;
        }
        sd *= dim;
    }
    return 1;
}

// taken from CPython "Objects/abstract.c"
int PyBuffer_IsContiguous(const Py_buffer *view, char order) {

    if (view->suboffsets != NULL) return 0;

    if (order == 'C')
        return _IsCContiguous(view);
    else if (order == 'F')
        return _IsFortranContiguous(view);
    else if (order == 'A')
        return (_IsCContiguous(view) || _IsFortranContiguous(view));
    return 0;
}

// PyMapping_Size downcall for native python objects
// partially taken from CPython "Objects/abstract.c/Py_Mapping_Size"
PyAPI_FUNC(Py_ssize_t) PyTruffle_PyMapping_Size(PyObject *o) {
    PyMappingMethods *m;

    if (o == NULL) {
        null_error();
        return -1;
    }

    m = Py_TYPE(o)->tp_as_mapping;
    if (m && m->mp_length) {
        Py_ssize_t len = m->mp_length(o);
        assert(len >= 0 || PyErr_Occurred());
        return len;
    }

    PyErr_Format(PyExc_TypeError, "PyTruffle_PyMapping_Size(): object of type '%s' has no len()", Py_TYPE(o)->tp_name);
    return -1;
}

PyObject ** _PySequence_Fast_ITEMS(PyObject *o) {
    return PyList_Check(o) ? PyListObject_ob_item(o) : PyTupleObject_ob_item(o);
}

PyObject* _PySequence_ITEM(PyObject* obj, Py_ssize_t index) {
	PySequenceMethods* methods = Py_TYPE(obj)->tp_as_sequence;
	return methods->sq_item(obj, index);
}

