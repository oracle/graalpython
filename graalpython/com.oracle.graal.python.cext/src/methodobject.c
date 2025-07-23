/*
 * Copyright (c) 2018, 2025, Oracle and/or its affiliates. All rights reserved.
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

/* undefine macro trampoline to PyCFunction_NewEx */
#undef PyCFunction_New
/* undefine macro trampoline to PyCMethod_New */
#undef PyCFunction_NewEx

typedef PyObject *(*PyCFunction)(PyObject *, PyObject *);

PyObject *PyCFunction_New(PyMethodDef *ml, PyObject *self) {
    return PyCFunction_NewEx(ml, self, NULL);
}

PyObject *PyCFunction_NewEx(PyMethodDef *ml, PyObject *self, PyObject *module) {
    return PyCMethod_New(ml, self, module, NULL);
}


PyObject* PyCMethod_New(PyMethodDef *ml, PyObject *self, PyObject *module, PyTypeObject *cls) {
    return GraalPyPrivate_CMethod_NewEx(ml, ml->ml_name,
                                           ml->ml_meth,
                                           ml->ml_flags,
                                           get_method_flags_wrapper(ml->ml_flags),
                                           self,
                                           module,
                                           cls,
                                           ml->ml_doc);
}

PyCFunction PyCFunction_GetFunction(PyObject *func) {
    PyMethodDef* def = GraalPyPrivate_GET_PyCFunctionObject_m_ml(func);
    return def->ml_meth;
}

PyObject * PyCFunction_GetSelf(PyObject *func) {
    PyMethodDef* def = GraalPyPrivate_GET_PyCFunctionObject_m_ml(func);
    return def->ml_flags & METH_STATIC ? NULL : GraalPyPrivate_GET_PyCFunctionObject_m_self(func);
}

int PyCFunction_GetFlags(PyObject *func) {
    PyMethodDef* def = GraalPyPrivate_GET_PyCFunctionObject_m_ml(func);
    return def->ml_flags;
}

PyTypeObject * GraalPyCMethod_GetClass(PyObject *func) {
    PyMethodDef* def = GraalPyPrivate_GET_PyCFunctionObject_m_ml(func);
    return def->ml_flags & METH_METHOD ? GraalPyPrivate_GET_PyCMethodObject_mm_class(func) : NULL;
}

PyObject* GraalPyCFunction_GetModule(PyObject *func) {
    return GraalPyPrivate_GET_PyCFunctionObject_m_module(func);
}

PyMethodDef* GraalPyCFunction_GetMethodDef(PyObject *func) {
    return GraalPyPrivate_GET_PyCFunctionObject_m_ml(func);
}

void GraalPyCFunction_SetModule(PyObject *func, PyObject *mod) {
    GraalPyPrivate_SET_PyCFunctionObject_m_module(func, mod);
}

void GraalPyCFunction_SetMethodDef(PyObject *func, PyMethodDef *def) {
    GraalPyPrivate_SET_PyCFunctionObject_m_ml(func, def);
}

const char *
GraalPyCFunction_GetDoc(PyObject *func) {
    return GraalPyPrivate_GET_PyCFunctionObject_m_ml(func)->ml_doc;
}

void
GraalPyCFunction_SetDoc(PyObject *func, const char *doc) {
    GraalPyPrivate_GET_PyCFunctionObject_m_ml(func)->ml_doc = doc;
    if (points_to_py_handle_space(func)) {
        GraalPyPrivate_CFunction_SetDoc(func, doc);
    }
}
