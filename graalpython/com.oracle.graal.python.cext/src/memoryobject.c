/*
 * Copyright (c) 2018, 2020, Oracle and/or its affiliates. All rights reserved.
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

PyTypeObject PyMemoryView_Type = PY_TRUFFLE_TYPE_WITH_ITEMSIZE("memoryview", &PyType_Type, Py_TPFLAGS_DEFAULT | Py_TPFLAGS_HAVE_GC, offsetof(PyMemoryViewObject, ob_array), sizeof(Py_ssize_t));
PyTypeObject PyBuffer_Type = PY_TRUFFLE_TYPE("buffer", &PyType_Type, Py_TPFLAGS_DEFAULT | Py_TPFLAGS_HAVE_GC, sizeof(PyBufferDecorator));

int bufferdecorator_getbuffer(PyBufferDecorator *self, Py_buffer *view, int flags) {
    return PyBuffer_FillInfo(view, (PyObject*)self, polyglot_get_member(self, "buf_delegate"), PyObject_Size((PyObject *)self) * sizeof(PyObject*), self->readonly, flags);
}

/* called from memoryview implementation to do pointer arithmetics currently not possible from Java */
int8_t* truffle_add_suboffset(int8_t *ptr, Py_ssize_t offset, Py_ssize_t suboffset, Py_ssize_t remaining_length) {
	return polyglot_from_i8_array(*(int8_t**)(ptr + offset) + suboffset, remaining_length);
}

UPCALL_ID(PyMemoryView_FromObject)
PyObject* PyMemoryView_FromObject(PyObject *v) {
	return UPCALL_CEXT_O(_jls_PyMemoryView_FromObject, native_to_java(v));
}

/* called back from the above upcall only if the object was native */
PyObject* PyTruffle_MemoryViewFromObject(PyObject *v) {
	// TODO resource management
	Py_buffer buffer;
    if (PyObject_CheckBuffer(v)) {
        PyObject *ret;
        if (PyObject_GetBuffer(v, &buffer, PyBUF_FULL_RO) < 0) {
            return NULL;
        }
        return PyMemoryView_FromBuffer(&buffer);
    }

    PyErr_Format(PyExc_TypeError,
        "memoryview: a bytes-like object is required, not '%.200s'",
        Py_TYPE(v)->tp_name);
    return NULL;
}

#if SIZEOF_SIZE_T == 8
#define polyglot_from_size_array polyglot_from_i64_array
#elif SIZEOF_SIZE_T == 4
#define polyglot_from_size_array polyglot_from_i32_array
#endif


PyObject* PyMemoryView_FromBuffer(Py_buffer *buffer) {
	Py_ssize_t ndim = buffer->ndim;
	return polyglot_invoke(PY_TRUFFLE_CEXT, "PyTruffle_MemoryViewFromBuffer",
			buffer,
			native_to_java(buffer->obj),
			buffer->len,
			buffer->readonly,
			buffer->itemsize,
			polyglot_from_string(buffer->format ? buffer->format : "B", "ascii"),
			buffer->ndim,
			polyglot_from_i8_array(buffer->buf, buffer->len),
			buffer->shape ? polyglot_from_size_array(buffer->shape, ndim) : NULL,
			buffer->strides ? polyglot_from_size_array(buffer->strides, ndim) : NULL,
			buffer->suboffsets ? polyglot_from_size_array(buffer->suboffsets, ndim) : NULL);
}
