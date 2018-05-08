/*
 * Copyright (c) 2018, Oracle and/or its affiliates.
 *
 * The Universal Permissive License (UPL), Version 1.0
 *
 * Subject to the condition set forth below, permission is hereby granted to any
 * person obtaining a copy of this software, associated documentation and/or data
 * (collectively the "Software"), free of charge and under any and all copyright
 * rights in the Software, and any and all patent rights owned or freely
 * licensable by each licensor hereunder covering either (i) the unmodified
 * Software as contributed to or provided by such licensor, or (ii) the Larger
 * Works (as defined below), to deal in both
 *
 * (a) the Software, and
 * (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
 *     one is included with the Software (each a "Larger Work" to which the
 *     Software is contributed by such licensors),
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
	ADD=0, SUB, MUL, TRUEDIV, LSHIFT, RSHIFT, OR, AND, XOR, FLOORDIV, MOD
} BinOp;

typedef enum e_unaryop {
	POS=0, NEG
} UnaryOp;

static PyObject* null_error(void) {
    if (!PyErr_Occurred()) {
        PyErr_SetString(PyExc_SystemError, "null argument to internal routine");
    }
    return NULL;
}

int PyNumber_Check(PyObject *o) {
    PyObject *result = truffle_invoke(PY_TRUFFLE_CEXT, "PyNumber_Check", to_java(o));
    if(result == Py_True) {
    	return 1;
    }
    return 0;
}

static PyObject * do_unaryop(PyObject *v, UnaryOp unaryop, char *unaryop_name) {
    PyObject *result = truffle_invoke(PY_TRUFFLE_CEXT, "PyNumber_UnaryOp", to_java(v), unaryop, truffle_read_string(unaryop_name));
    if (result == ERROR_MARKER) {
    	return NULL;
    }
    return to_sulong(result);
}

static PyObject * do_binop(PyObject *v, PyObject *w, BinOp binop, char *binop_name) {
    PyObject *result = truffle_invoke(PY_TRUFFLE_CEXT, "PyNumber_BinOp", to_java(v), to_java(w), binop, truffle_read_string(binop_name));
    if (result == ERROR_MARKER) {
    	return NULL;
    }
    return to_sulong(result);
}

PyObject * PyNumber_Add(PyObject *o1, PyObject *o2) {
	return do_binop(o1, o2, ADD, "+");
}

PyObject * PyNumber_Subtract(PyObject *o1, PyObject *o2) {
	return do_binop(o1, o2, SUB, "-");
}

PyObject * PyNumber_Multiply(PyObject *o1, PyObject *o2) {
	return do_binop(o1, o2, MUL, "*");
}

PyObject * PyNumber_TrueDivide(PyObject *o1, PyObject *o2) {
	return do_binop(o1, o2, TRUEDIV, "/");
}

PyObject * PyNumber_FloorDivide(PyObject *o1, PyObject *o2) {
	return do_binop(o1, o2, FLOORDIV, "//");
}

PyObject * PyNumber_Remainder(PyObject *o1, PyObject *o2) {
	return do_binop(o1, o2, MOD, "%");
}

PyObject * PyNumber_Lshift(PyObject *o1, PyObject *o2) {
	return do_binop(o1, o2, LSHIFT, "<<");
}

PyObject * PyNumber_Rshift(PyObject *o1, PyObject *o2) {
	return do_binop(o1, o2, RSHIFT, ">>");
}

PyObject * PyNumber_Or(PyObject *o1, PyObject *o2) {
	return do_binop(o1, o2, OR, "|");
}

PyObject * PyNumber_And(PyObject *o1, PyObject *o2) {
	return do_binop(o1, o2, AND, "&");
}

PyObject * PyNumber_Xor(PyObject *o1, PyObject *o2) {
	return do_binop(o1, o2, XOR, "^");
}

PyObject * PyNumber_Positive(PyObject *o) {
	return do_unaryop(o, POS, "+");
}

PyObject * PyNumber_Negative(PyObject *o) {
	return do_unaryop(o, NEG, "-");
}

PyObject * PyNumber_Index(PyObject *o) {
    if (o == NULL) {
        return null_error();
    }
    PyObject *result = truffle_invoke(PY_TRUFFLE_CEXT, "PyNumber_Index", to_java(o));
    if (result == ERROR_MARKER) {
    	return NULL;
    }
    return to_sulong(result);
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
    	PyObject* t = PyTuple_New(1);
    	PyTuple_SetItem(t, 0, PyObject_Type(item));
    	truffle_invoke(PY_TRUFFLE_CEXT, "PyErr_Format", to_java(err), truffle_read_string("cannot fit '%s' into an index-sized integer"), to_java(t));
    }

    Py_DECREF(value);
    return -1;
}

PyObject * PyNumber_Long(PyObject *o) {
    PyObject *result = truffle_invoke(PY_TRUFFLE_CEXT, "PyNumber_Long", to_java(o));
    if (result == ERROR_MARKER) {
    	return NULL;
    }
    return to_sulong(result);
}

PyObject * PyNumber_Float(PyObject *o) {
    PyObject *result = truffle_invoke(PY_TRUFFLE_CEXT, "PyNumber_Float", to_java(o));
    if (result == ERROR_MARKER) {
    	return NULL;
    }
    return to_sulong(result);
}

PyObject * PyIter_Next(PyObject *iter) {
	void* result = polyglot_invoke(PY_TRUFFLE_CEXT, "PyIter_Next", to_java(iter));
	if (result == ERROR_MARKER && PyErr_Occurred() && PyErr_ExceptionMatches(PyExc_StopIteration)) {
        PyErr_Clear();
		return NULL;
	}
    return to_sulong(result);
}

int PySequence_Check(PyObject *s) {
	if (s == NULL) {
		return 0;
	}
	return polyglot_as_i64(polyglot_invoke(PY_TRUFFLE_CEXT, "PySequence_Check", to_java(s)));
}

Py_ssize_t PySequence_Size(PyObject *s) {
	return polyglot_as_i64(polyglot_invoke(PY_TRUFFLE_CEXT, "PyObject_Size", to_java(s)));
}

// taken from CPython "Objects/abstract.c"
#undef PySequence_Length
Py_ssize_t PySequence_Length(PyObject *s) {
    return PySequence_Size(s);
}
#define PySequence_Length PySequence_Size

PyObject* PySequence_GetItem(PyObject *s, Py_ssize_t i) {
	void* result = polyglot_invoke(PY_TRUFFLE_CEXT, "PySequence_GetItem", to_java(s), i);
	if(result == ERROR_MARKER) {
		return NULL;
	}
	return to_sulong(result);
}

int PySequence_SetItem(PyObject *s, Py_ssize_t i, PyObject *o) {
	return polyglot_as_i32(polyglot_invoke(PY_TRUFFLE_CEXT, "PySequence_SetItem", to_java(s), i, to_java(o)));
}

PyObject* PySequence_Tuple(PyObject *v) {
	void* result = polyglot_invoke(PY_TRUFFLE_CEXT, "PySequence_Tuple", to_java(v));
	if(result == ERROR_MARKER) {
		return NULL;
	}
	return to_sulong(result);
}

PyObject * PySequence_Fast(PyObject *v, const char *m) {
	void* result = polyglot_invoke(PY_TRUFFLE_CEXT, "PySequence_Fast", to_java(v), polyglot_from_string(m, "ascii"));
	if(result == ERROR_MARKER) {
		return NULL;
	}
	return to_sulong(result);
}

PyObject * PyMapping_GetItemString(PyObject *o, const char *key) {
	void* result = polyglot_invoke(PY_TRUFFLE_CEXT, "PyObject_GetItem", to_java(o), polyglot_from_string(key, "utf-8"));
	if(result == ERROR_MARKER) {
		return NULL;
	}
	return to_sulong(result);
}

