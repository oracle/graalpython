# Copyright (c) 2024, 2026, Oracle and/or its affiliates. All rights reserved.
# DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
#
# The Universal Permissive License (UPL), Version 1.0
#
# Subject to the condition set forth below, permission is hereby granted to any
# person obtaining a copy of this software, associated documentation and/or
# data (collectively the "Software"), free of charge and under any and all
# copyright rights in the Software, and any and all patent rights owned or
# freely licensable by each licensor hereunder covering either (i) the
# unmodified Software as contributed to or provided by such licensor, or (ii)
# the Larger Works (as defined below), to deal in both
#
# (a) the Software, and
#
# (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
# one is included with the Software each a "Larger Work" to which the Software
# is contributed by such licensors),
#
# without restriction, including without limitation the rights to copy, create
# derivative works of, display, perform, and distribute the Software and make,
# use, sell, offer for sale, import, export, have made, and have sold the
# Software and the Larger Work(s), and to sublicense the foregoing rights on
# either these or other terms.
#
# This license is subject to the following condition:
#
# The above copyright notice and either this complete permission notice or at a
# minimum a reference to the UPL must be included in all copies or substantial
# portions of the Software.
#
# THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
# IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
# FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
# AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
# LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
# OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
# SOFTWARE.

import contextvars
from . import CPyExtType
from ..util import assert_raises


ContextHelper = CPyExtType(
    'TestContextvarsContextHelper',
    '''
    static PyObject* context_enter(PyObject* unused, PyObject* args) {
        PyObject *ctx;
        if (!PyArg_ParseTuple(args, "O", &ctx))
            return NULL;
        PyContext_Enter(ctx);
        Py_RETURN_NONE;
    }
    static PyObject* context_exit(PyObject* unused, PyObject* args) {
        PyObject *ctx;
        if (!PyArg_ParseTuple(args, "O", &ctx))
            return NULL;
        PyContext_Exit(ctx);
        Py_RETURN_NONE;
    }
    static PyObject* context_copy(PyObject* unused, PyObject* args) {
        PyObject *ctx;
        if (!PyArg_ParseTuple(args, "O", &ctx))
            return NULL;
        return PyContext_Copy(ctx);
    }
    static PyObject* context_new(PyObject* unused, PyObject* args) {
        return PyContext_New();
    }
    static PyObject* context_copy_current(PyObject* unused, PyObject* args) {
        return PyContext_CopyCurrent();
    }
    static PyObject* contextvar_new(PyObject* unused, PyObject* args) {
        const char *name;
        PyObject *def = NULL;
        if (!PyArg_ParseTuple(args, "s|O", &name, &def))
            return NULL;
        return PyContextVar_New(name, def);
    }
    static PyObject* contextvar_get(PyObject* unused, PyObject* args) {
        PyObject *var, *value, *def = NULL;
        if (!PyArg_ParseTuple(args, "O|O", &var, &def))
            return NULL;
        if (PyContextVar_Get(var, def, &value) < 0)
            return NULL;
        if (value == NULL)
            Py_RETURN_NONE;
        return value;
    }
    static PyObject* contextvar_set(PyObject* unused, PyObject* args) {
        PyObject *var, *value;
        if (!PyArg_ParseTuple(args, "OO", &var, &value))
            return NULL;
        return PyContextVar_Set(var, value);
    }
    static PyObject* contextvar_reset(PyObject* unused, PyObject* args) {
        PyObject *var, *token;
        if (!PyArg_ParseTuple(args, "OO", &var, &token))
            return NULL;
        int result = PyContextVar_Reset(var, token);
        if (result < 0)
            return NULL;
        return PyLong_FromLong(result);
    }
    static PyObject* context_is_exact_type(PyObject* unused, PyObject* args) {
        PyObject *obj;
        int kind;
        if (!PyArg_ParseTuple(args, "Oi", &obj, &kind))
            return NULL;
        PyTypeObject *expected_types[] = {
            &PyContext_Type,
            &PyContextVar_Type,
            &PyContextToken_Type,
        };
        return PyBool_FromLong(Py_IS_TYPE(obj, expected_types[kind]));
    }
    ''',
    tp_methods='''
        {"var_new", (PyCFunction)contextvar_new, METH_VARARGS | METH_STATIC, ""},
        {"var_get", (PyCFunction)contextvar_get, METH_VARARGS | METH_STATIC, ""},
        {"var_set", (PyCFunction)contextvar_set, METH_VARARGS | METH_STATIC, ""},
        {"var_reset", (PyCFunction)contextvar_reset, METH_VARARGS | METH_STATIC, ""},
        {"enter", (PyCFunction)context_enter, METH_VARARGS | METH_STATIC, ""},
        {"exit", (PyCFunction)context_exit, METH_VARARGS | METH_STATIC, ""},
        {"copy", (PyCFunction)context_copy, METH_VARARGS | METH_STATIC, ""},
        {"new", (PyCFunction)context_new, METH_VARARGS | METH_STATIC, ""},
        {"copy_current", (PyCFunction)context_copy_current, METH_VARARGS | METH_STATIC, ""},
        {"is_exact_type", (PyCFunction)context_is_exact_type, METH_VARARGS | METH_STATIC, ""}
    '''
)


def test_cext_context_management():
    v = contextvars.ContextVar('test1', default='default value')
    assert v.get() == 'default value'

    token = v.set('new value')

    assert v.get() == 'new value'
    current_copy = ContextHelper.copy_current()

    assert ContextHelper.is_exact_type(current_copy, 0)
    assert ContextHelper.is_exact_type(v, 1)
    assert ContextHelper.is_exact_type(token, 2)

    assert v.get() == 'new value'
    assert current_copy.run(v.get) == 'new value'

    current_copy.run(v.set, 'newer value')
    assert v.get() == 'new value'
    assert current_copy.run(v.get) == 'newer value'

    ContextHelper.enter(current_copy)
    try:
        assert v.get() == 'newer value'
        assert_raises(RuntimeError, current_copy.run, v.get, err_check='cannot enter context')
        token_in_copy = v.set('newer value 2')
    finally:
        ContextHelper.exit(current_copy)

    assert v.get() == 'new value'
    assert current_copy.run(v.get) == 'newer value 2'

    v.reset(token)
    assert v.get() == 'default value'
    assert current_copy.run(v.get) == 'newer value 2'

    copy_of_copy = ContextHelper.copy(current_copy)
    current_copy.run(v.reset, token_in_copy)
    assert v.get() == 'default value'
    assert current_copy.run(v.get) == 'newer value'
    assert copy_of_copy.run(v.get) == 'newer value 2'

    new_ctx = ContextHelper.new()
    assert new_ctx.run(v.get) == 'default value'



def test_cext_contextvar_reset():
    for default_args in ((), ('default value',), (None,)):
        var = ContextHelper.var_new('test_reset', *default_args)
        assert isinstance(var, contextvars.ContextVar)
        assert ContextHelper.var_get(var) == (default_args[0] if default_args else None)
        assert ContextHelper.var_get(var, 'fallback') == 'fallback'

        token = ContextHelper.var_set(var, 'first value')
        assert token.old_value is contextvars.Token.MISSING
        assert ContextHelper.var_get(var) == 'first value'
        inner_token = ContextHelper.var_set(var, None)
        assert inner_token.old_value == 'first value'
        assert ContextHelper.var_reset(var, inner_token) == 0
        assert var.get() == 'first value'
        assert ContextHelper.var_reset(var, token) == 0
        assert var not in contextvars.copy_context()
        if default_args:
            assert var.get() == default_args[0]
        else:
            assert_raises(LookupError, var.get)

        # Tokens created in Python must work with the C API and vice versa.
        token = var.set('python value')
        assert ContextHelper.var_reset(var, token) == 0
        token = ContextHelper.var_set(var, 'native value')
        var.reset(token)
        assert var not in contextvars.copy_context()

        token = ContextHelper.var_set(var, None)
        inner_token = ContextHelper.var_set(var, 'new value')
        assert ContextHelper.var_reset(var, inner_token) == 0
        assert var.get() is None
        assert ContextHelper.var_reset(var, token) == 0


def test_cext_contextvar_reset_errors():
    var = contextvars.ContextVar('test_reset_errors')
    other_var = contextvars.ContextVar('other_var')
    token = ContextHelper.var_set(var, 'value')
    assert_raises(TypeError, ContextHelper.var_reset, object(), token, err_check='instance of ContextVar')
    assert_raises(TypeError, ContextHelper.var_reset, var, object(), err_check='instance of Token')
    assert_raises(ValueError, ContextHelper.var_reset, other_var, token, err_check='different ContextVar')
    other_context = ContextHelper.copy_current()
    assert_raises(ValueError, other_context.run, ContextHelper.var_reset, var, token, err_check='different Context')
    assert_raises(ValueError, other_context.run, var.reset, token, err_check='different Context')
    assert var.get() == 'value'
    assert other_context.run(var.get) == 'value'

    # Rejected resets must leave the token usable in its original context.
    assert ContextHelper.var_reset(var, token) == 0
    assert_raises(RuntimeError, ContextHelper.var_reset, var, token, err_check='already been used once')
    assert_raises(RuntimeError, ContextHelper.var_reset, other_var, token, err_check='already been used once')
    assert var not in contextvars.copy_context()
