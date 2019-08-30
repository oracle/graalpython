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

UPCALL_ID(PyNumber_Check);
int PyNumber_Check(PyObject *o) {
    PyObject *result = UPCALL_CEXT_O(_jls_PyNumber_Check, native_to_java(o));
    if(result == Py_True) {
    	return 1;
    }
    return 0;
}

UPCALL_ID(PyNumber_UnaryOp);
static PyObject * do_unaryop(PyObject *v, UnaryOp unaryop) {
    return UPCALL_CEXT_O(_jls_PyNumber_UnaryOp, native_to_java(v), unaryop);
}

UPCALL_ID(PyNumber_BinOp);
MUST_INLINE static PyObject * do_binop(PyObject *v, PyObject *w, BinOp binop) {
    return UPCALL_CEXT_O(_jls_PyNumber_BinOp, native_to_java(v), native_to_java(w), binop);
}

UPCALL_ID(PyNumber_InPlaceBinOp);
MUST_INLINE static PyObject * do_inplace_binop(PyObject *v, PyObject *w, BinOp binop) {
    return UPCALL_CEXT_O(_jls_PyNumber_InPlaceBinOp, native_to_java(v), native_to_java(w), binop);
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

PyObject * PyNumber_Power(PyObject *v, PyObject *w, PyObject *z) {
    return UPCALL_O(PY_BUILTIN, polyglot_from_string("pow", SRC_CS), native_to_java(v), native_to_java(w), native_to_java(z));
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

PyObject* PyNumber_InPlacePower(PyObject *o1, PyObject *o2, PyObject *o3) {
	// TODO
	PyErr_SetNone(PyExc_NotImplementedError);
    return NULL;

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

UPCALL_ID(PyNumber_Index);
PyObject * PyNumber_Index(PyObject *o) {
    if (o == NULL) {
        return null_error();
    }
    return UPCALL_CEXT_O(_jls_PyNumber_Index, native_to_java(o));
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

UPCALL_ID(PyNumber_Long);
PyObject * PyNumber_Long(PyObject *o) {
    return UPCALL_CEXT_O(_jls_PyNumber_Long, native_to_java(o));
}

UPCALL_ID(PyNumber_Float);
PyObject * PyNumber_Float(PyObject *o) {
    return ((PyObject* (*)(void*))_jls_PyNumber_Float)(native_to_java(o));
}

UPCALL_ID(PyNumber_Absolute);
PyObject * PyNumber_Absolute(PyObject *o) {
    return UPCALL_CEXT_O(_jls_PyNumber_Absolute, native_to_java(o));
}

UPCALL_ID(PyNumber_Divmod);
PyObject * PyNumber_Divmod(PyObject *a, PyObject *b) {
    return UPCALL_CEXT_O(_jls_PyNumber_Divmod, native_to_java(a), native_to_java(b));
}


UPCALL_ID(PyIter_Next);
PyObject * PyIter_Next(PyObject *iter) {
    return UPCALL_CEXT_O(_jls_PyIter_Next, native_to_java(iter));
}

UPCALL_ID(PySequence_Check);
int PySequence_Check(PyObject *s) {
    if (s == NULL) {
        return 0;
    }
    return UPCALL_CEXT_I(_jls_PySequence_Check, native_to_java(s));
}

UPCALL_ID(PyObject_Size);
Py_ssize_t PySequence_Size(PyObject *s) {
    return UPCALL_CEXT_L(_jls_PyObject_Size, native_to_java(s));
}

UPCALL_ID(PySequence_Contains);
int PySequence_Contains(PyObject *seq, PyObject *obj) {
    return UPCALL_CEXT_I(_jls_PySequence_Contains, native_to_java(seq), native_to_java(obj));
}

// taken from CPython "Objects/abstract.c"
#undef PySequence_Length
Py_ssize_t PySequence_Length(PyObject *s) {
    return PySequence_Size(s);
}
#define PySequence_Length PySequence_Size

UPCALL_ID(PySequence_GetItem);
PyObject* PySequence_GetItem(PyObject *s, Py_ssize_t i) {
    return UPCALL_CEXT_O(_jls_PySequence_GetItem, native_to_java(s), i);
}

UPCALL_ID(PySequence_SetItem);
int PySequence_SetItem(PyObject *s, Py_ssize_t i, PyObject *o) {
    return UPCALL_CEXT_I(_jls_PySequence_SetItem, native_to_java(s), i, native_to_java(o));
}

UPCALL_ID(PySequence_GetSlice);
PyObject* PySequence_GetSlice(PyObject *s, Py_ssize_t i1, Py_ssize_t i2) {
	return UPCALL_CEXT_O(_jls_PySequence_GetSlice, native_to_java(s), i1, i2);
}

UPCALL_ID(PySequence_Tuple);
PyObject* PySequence_Tuple(PyObject *v) {
    return UPCALL_CEXT_O(_jls_PySequence_Tuple, native_to_java(v));
}

UPCALL_ID(PySequence_List);
PyObject* PySequence_List(PyObject *v) {
    return UPCALL_CEXT_O(_jls_PySequence_List, native_to_java(v));
}

PyObject * PySequence_Fast(PyObject *v, const char *m) {
    if (v == NULL) {
        return null_error();
    }

    if (PyList_CheckExact(v) || PyTuple_CheckExact(v)) {
        Py_INCREF(v);
        return v;
    }

	return UPCALL_CEXT_O(_jls_PySequence_List, native_to_java(v));
}

UPCALL_ID(PyObject_GetItem);
PyObject * PyMapping_GetItemString(PyObject *o, const char *key) {
    return UPCALL_CEXT_O(_jls_PyObject_GetItem, native_to_java(o), polyglot_from_string(key, SRC_CS));
}

UPCALL_ID(PyMapping_Keys);
PyObject * PyMapping_Keys(PyObject *o) {
    return UPCALL_CEXT_O(_jls_PyMapping_Keys, native_to_java(o));
}

UPCALL_ID(PyMapping_Values);
PyObject * PyMapping_Values(PyObject *o) {
    if (o == NULL) {
        return null_error();
    }
    return UPCALL_CEXT_O(_jls_PyMapping_Values, native_to_java(o));
}

// taken from CPython "Objects/abstract.c"
int PyMapping_Check(PyObject *o) {
    return o && o->ob_type->tp_as_mapping && o->ob_type->tp_as_mapping->mp_subscript;
}

// taken from CPython "Objects/abstract.c"
int PyObject_GetBuffer(PyObject *obj, Py_buffer *view, int flags) {
    PyBufferProcs *pb = obj->ob_type->tp_as_buffer;

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

UPCALL_ID(PySequence_DelItem);
int PySequence_DelItem(PyObject *s, Py_ssize_t i) {
    return UPCALL_CEXT_I(_jls_PySequence_DelItem, native_to_java(s), i);
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

// partially taken from CPython "Objects/abstract.c"
Py_ssize_t PyMapping_Size(PyObject *o) {
    PyMappingMethods *m;

    if (o == NULL) {
        null_error();
        return -1;
    }

    m = o->ob_type->tp_as_mapping;
    if (m && m->mp_length) {
        Py_ssize_t len = m->mp_length(o);
        assert(len >= 0 || PyErr_Occurred());
        return len;
    }

    PyErr_Format(PyExc_TypeError, "object of type '%s' has no len()", Py_TYPE(o)->tp_name);
    return -1;
}

UPCALL_ID(PySequence_Repeat);
PyObject* PySequence_Repeat(PyObject *o, Py_ssize_t count) {
	return UPCALL_CEXT_O(_jls_PySequence_Repeat, native_to_java(o), count);
}

UPCALL_ID(PySequence_Concat);
PyObject* PySequence_Concat(PyObject *s, PyObject *o) {
	return UPCALL_CEXT_O(_jls_PySequence_Concat, native_to_java(s), native_to_java(o));
}

UPCALL_ID(PySequence_InPlaceRepeat);
PyObject* PySequence_InPlaceRepeat(PyObject *o, Py_ssize_t count) {
	return UPCALL_CEXT_O(_jls_PySequence_Repeat, native_to_java(o), count);
}

UPCALL_ID(PySequence_InPlaceConcat);
PyObject* PySequence_InPlaceConcat(PyObject *s, PyObject *o) {
	return UPCALL_CEXT_O(_jls_PySequence_Concat, native_to_java(s), native_to_java(o));
}
