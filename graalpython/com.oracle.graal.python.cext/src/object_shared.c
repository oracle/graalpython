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

#include "pycore_pymem.h"
#include "pycore_object.h"

Py_ssize_t _Py_REFCNT(const PyObject *obj) {
	return PyObject_ob_refcnt(obj);
}

Py_ssize_t _Py_SET_REFCNT(PyObject* obj, Py_ssize_t cnt) {
	set_PyObject_ob_refcnt(obj, cnt);
	return cnt;
}

PyTypeObject* _Py_TYPE(const PyObject *a) {
	return PyObject_ob_type(a);
}

Py_ssize_t _Py_SIZE(const PyVarObject *a) {
	return PyVarObject_ob_size(a);
}

void _Py_SET_TYPE(PyObject *a, PyTypeObject *b) {
	if (points_to_py_handle_space(a)) {
		printf("changing the type of an object is not supported\n");
	} else {
		a->ob_type = b;
	}
}
void _Py_SET_SIZE(PyVarObject *a, Py_ssize_t b) {
	if (points_to_py_handle_space(a)) {
		printf("changing the size of an object is not supported\n");
	} else {
		a->ob_size = b;
	}
}

#undef Py_Is
#undef Py_IsNone
#undef Py_IsTrue
#undef Py_IsFalse

// Export Py_Is(), Py_IsNone(), Py_IsTrue(), Py_IsFalse() as regular functions
// for the stable ABI.
int Py_Is(PyObject *x, PyObject *y)
{
    return (x == y);
}

int Py_IsNone(PyObject *x)
{
    return Py_Is(x, Py_None);
}

int Py_IsTrue(PyObject *x)
{
    return Py_Is(x, Py_True);
}

int Py_IsFalse(PyObject *x)
{
    return Py_Is(x, Py_False);
}

void _Py_IncRef(PyObject *op) {
    Py_SET_REFCNT(op, Py_REFCNT(op) + 1);
}

void _Py_DecRef(PyObject *op) {
    Py_ssize_t cnt = Py_REFCNT(op) - 1;
    Py_SET_REFCNT(op, cnt);
    if (cnt != 0) {
    }
    else {
        _Py_Dealloc(op);
    }
}

void Py_IncRef(PyObject *op) {
	if (op != NULL) {
		_Py_IncRef(op);
	}
}

void Py_DecRef(PyObject *op) {
	if (op != NULL) {
		_Py_DecRef(op);
	}
}


#undef _Py_Dealloc

void
_Py_Dealloc(PyObject *op)
{
    destructor dealloc = Py_TYPE(op)->tp_dealloc;
#ifdef Py_TRACE_REFS
    _Py_ForgetReference(op);
#endif
    (*dealloc)(op);
}


void
_Py_NewReference(PyObject *op)
{
    if (_Py_tracemalloc_config.tracing) {
        _PyTraceMalloc_NewReference(op);
    }
#ifdef Py_REF_DEBUG
    _Py_RefTotal++;
#endif
    Py_SET_REFCNT(op, 1);
#ifdef Py_TRACE_REFS
    _Py_AddToAllObjects(op, 1);
#endif
}

PyObject* PyObject_Init(PyObject *op, PyTypeObject *tp) {
    if (op == NULL) {
        return PyErr_NoMemory();
    }

    _PyObject_Init(op, tp);
    return op;
}

// taken from CPython "Objects/object.c"
PyVarObject* PyObject_InitVar(PyVarObject *op, PyTypeObject *tp, Py_ssize_t size) {
    if (op == NULL) {
        return (PyVarObject*) PyErr_NoMemory();
    }

    _PyObject_InitVar(op, tp, size);
    return op;
}

PyObject* _PyObject_New(PyTypeObject *tp) {
    PyObject *op = (PyObject*)PyObject_MALLOC(_PyObject_SIZE(tp));
    if (op == NULL) {
        return PyErr_NoMemory();
    }
    return PyObject_INIT(op, tp);
}

PyVarObject* _PyObject_NewVar(PyTypeObject *tp, Py_ssize_t nitems) {
    PyVarObject* op;
    const size_t size = _PyObject_VAR_SIZE(tp, nitems);
    op = (PyVarObject*) PyObject_MALLOC(size);
    if (op == NULL)
        return (PyVarObject*)PyErr_NoMemory();
    return PyObject_INIT_VAR(op, tp, nitems);
}

PyObject* _PyObject_GC_New(PyTypeObject *tp) {
    return _PyObject_New(tp);
}

PyVarObject* _PyObject_GC_NewVar(PyTypeObject *tp, Py_ssize_t nitems) {
    return _PyObject_NewVar(tp, nitems);
}

void PyObject_GC_Del(void *tp) {
	PyObject_Free(tp);
}

inline int is_single_arg(const char* fmt) {
	if (fmt[0] == 0) {
		return 0;
	}
	if (fmt[1] == 0) {
		return 1;
	}
	if (fmt[2] != 0) {
		return 0;
	}
	switch (fmt[1]) {
		case '#':
		case '&':
		case ',':
		case ':':
		case ' ':
		case '\t':
			return 1;
		default:
			return 0;
	}
}

PyObject* PyObject_Call(PyObject* callable, PyObject* args, PyObject* kwargs) {
	return Graal_PyTruffleObject_Call1(callable, args, kwargs, 0);
}

PyObject* PyObject_CallObject(PyObject* callable, PyObject* args) {
	return Graal_PyTruffleObject_Call1(callable, args, NULL, 0);
}

PyObject* PyObject_CallNoArgs(PyObject* callable) {
    return Graal_PyTruffleObject_Call1(callable, NULL, NULL, 0);
}

PyObject* PyObject_CallFunction(PyObject* callable, const char* fmt, ...) {
    if (fmt == NULL || fmt[0] == '\0') {
	    return Graal_PyTruffleObject_Call1(callable, NULL, NULL, 0);
    }
    va_list va;
    va_start(va, fmt);
    PyObject* args = Py_VaBuildValue(fmt, va);
    va_end(va);
    // A special case in CPython for backwards compatibility
    if (is_single_arg(fmt) && PyTuple_Check(args)) {
    	return Graal_PyTruffleObject_Call1(callable, args, NULL, 0);
    }
	return Graal_PyTruffleObject_Call1(callable, args, NULL, is_single_arg(fmt));
}

PyObject* _PyObject_CallFunction_SizeT(PyObject* callable, const char* fmt, ...) {
    if (fmt == NULL || fmt[0] == '\0') {
	    return Graal_PyTruffleObject_Call1(callable, NULL, NULL, 0);
    }
    va_list va;
    va_start(va, fmt);
    PyObject* args = _Py_VaBuildValue_SizeT(fmt, va);
    va_end(va);
    // A special case in CPython for backwards compatibility
    if (is_single_arg(fmt) && PyTuple_Check(args)) {
    	return Graal_PyTruffleObject_Call1(callable, args, NULL, 0);
    }
	return Graal_PyTruffleObject_Call1(callable, args, NULL, is_single_arg(fmt));
}

PyObject* PyObject_CallMethod(PyObject* object, const char* method, const char* fmt, ...) {
    PyObject* args;
    if (fmt == NULL || fmt[0] == '\0') {
        return Graal_PyTruffleObject_CallMethod1(object, truffleString(method), NULL, 0);
    }
    va_list va;
    va_start(va, fmt);
    args = Py_VaBuildValue(fmt, va);
    va_end(va);
    return Graal_PyTruffleObject_CallMethod1(object, truffleString(method), args, is_single_arg(fmt));
}

PyObject* _PyObject_CallMethod_SizeT(PyObject* object, const char* method, const char* fmt, ...) {
    PyObject* args;
    if (fmt == NULL || fmt[0] == '\0') {
        return Graal_PyTruffleObject_CallMethod1(object, truffleString(method), NULL, 0);
    }
    va_list va;
    va_start(va, fmt);
    args = _Py_VaBuildValue_SizeT(fmt, va);
    va_end(va);
    return Graal_PyTruffleObject_CallMethod1(object, truffleString(method), args, is_single_arg(fmt));
}
