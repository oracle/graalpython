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

PyObject* PyDescr_NewClassMethod(PyTypeObject *type, PyMethodDef *method) {
    int flags = method->ml_flags;
    return GraalPyPrivate_Descr_NewClassMethod(method,
                    method->ml_name,
                    method->ml_doc,
                    flags,
                    get_method_flags_wrapper(flags),
                    method->ml_meth,
                    type);
}

PyObject* PyDescr_NewGetSet(PyTypeObject *type, PyGetSetDef *getset) {
    getter getter_fun = GraalPyPrivate_GET_PyGetSetDef_get(getset);
    setter setter_fun = GraalPyPrivate_GET_PyGetSetDef_set(getset);
    return GraalPyPrivate_Descr_NewGetSet(GraalPyPrivate_GET_PyGetSetDef_name(getset),
                    type,
                    getter_fun,
                    setter_fun,
                    GraalPyPrivate_GET_PyGetSetDef_doc(getset),
                    GraalPyPrivate_GET_PyGetSetDef_closure(getset));
}


PyMethodDef* GraalPyMethodDescrObject_GetMethod(PyObject* descr) {
    return GraalPyPrivate_GET_PyMethodDescrObject_d_method(descr);
}

PyTypeObject* GraalPyDescrObject_GetType(PyObject* descr) {
    return GraalPyPrivate_GET_PyDescrObject_d_type(descr);
}

PyObject* GraalPyDescrObject_GetName(PyObject* descr) {
    return GraalPyPrivate_GET_PyDescrObject_d_name(descr);
}

int PyDescr_IsData(PyObject *ob) {
    return Py_TYPE(ob)->tp_descr_set != NULL;
}
