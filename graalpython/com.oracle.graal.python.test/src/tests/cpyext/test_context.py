# Copyright (c) 2024, 2024, Oracle and/or its affiliates. All rights reserved.
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
    ''',
    tp_methods='''
        {"enter", (PyCFunction)context_enter, METH_VARARGS | METH_STATIC, ""},
        {"exit", (PyCFunction)context_exit, METH_VARARGS | METH_STATIC, ""},
        {"copy", (PyCFunction)context_copy, METH_VARARGS | METH_STATIC, ""},
        {"new", (PyCFunction)context_new, METH_VARARGS | METH_STATIC, ""},
        {"copy_current", (PyCFunction)context_copy_current, METH_VARARGS | METH_STATIC, ""}
    '''
)


def test_cext_context_management():
    v = contextvars.ContextVar('test1', default='default value')
    assert v.get() == 'default value'

    token = v.set('new value')

    assert v.get() == 'new value'
    current_copy = ContextHelper.copy_current()

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