/*
 * Copyright (c) 2026, 2026, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */

package com.oracle.graal.python.builtins.objects.cext.capi;

import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.CHAR_PTR;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.InquiryResult;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.Int;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.Pointer;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.PrimitiveResult64;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.PyObject;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.PyObjectReturn;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.PyTypeObject;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.Py_ssize_t;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.Void;

import com.oracle.graal.python.annotations.CApiExternalFunctionSignatures;
import com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor;

/**
 * Enum of well-known function and slot signatures. The integer values must stay in sync with the
 * definition in {code capi.h}.
 */
@CApiExternalFunctionSignatures
public enum ExternalFunctionSignature {
    // typedef PyObject * (*unaryfunc)(PyObject *);
    UNARYFUNC(PyObjectReturn, PyObject),
    // typedef PyObject * (*binaryfunc)(PyObject *, PyObject *);
    BINARYFUNC(PyObjectReturn, PyObject, PyObject),
    // typedef PyObject * (*ternaryfunc)(PyObject *, PyObject *, PyObject *);
    TERNARYFUNC(PyObjectReturn, PyObject, PyObject, PyObject),
    // typedef int (*inquiry)(PyObject *);
    INQUIRY(InquiryResult, PyObject),
    // typedef Py_ssize_t (*lenfunc)(PyObject *);
    LENFUNC(PrimitiveResult64, PyObject),
    // typedef PyObject *(*ssizeargfunc)(PyObject *, Py_ssize_t);
    SSIZEARGFUNC(PyObjectReturn, PyObject, Py_ssize_t),

    // typedef PyObject *(*ssizessizeargfunc)(PyObject *, Py_ssize_t, Py_ssize_t);
    SSIZESSIZEARGFUNC(PyObjectReturn, PyObject, Py_ssize_t, Py_ssize_t),
    // typedef int(*ssizeobjargproc)(PyObject *, Py_ssize_t, PyObject *);
    SSIZEOBJARGPROC(Int, PyObject, Py_ssize_t, PyObject),
    // typedef int(*ssizessizeobjargproc)(PyObject *, Py_ssize_t, Py_ssize_t, PyObject *);
    SSIZESSIZEOBJARGPROC(Int, PyObject, Py_ssize_t, Py_ssize_t, PyObject),
    // typedef int(*objobjargproc)(PyObject *, PyObject *, PyObject *);
    OBJOBJARGPROC(Int, PyObject, PyObject, PyObject),

    // typedef int (*objobjproc)(PyObject *, PyObject *);
    OBJOBJPROC(Int, PyObject, PyObject),
    // typedef int (*visitproc)(PyObject *, void *);
    VISITPROC(Int, PyObject, Pointer),
    // typedef int (*traverseproc)(PyObject *, visitproc, void *);
    TRAVERSEPROC(Int, PyObject, Pointer, Pointer),

    // typedef void (*freefunc)(void *);
    FREEFUNC(Void, Pointer),
    // typedef void (*destructor)(PyObject *);
    DESTRUCTOR(Void, PyObject),
    // typedef PyObject *(*getattrfunc)(PyObject *, char *);
    GETATTRFUNC(PyObjectReturn, PyObject, CHAR_PTR),
    // typedef PyObject *(*getattrofunc)(PyObject *, PyObject *);
    GETATTROFUNC(PyObjectReturn, PyObject, PyObject),
    // typedef int (*setattrfunc)(PyObject *, char *, PyObject *);
    SETATTRFUNC(Int, PyObject, CHAR_PTR, PyObject),
    // typedef int (*setattrofunc)(PyObject *, PyObject *, PyObject *);
    SETATTROFUNC(Int, PyObject, PyObject, PyObject),
    // typedef PyObject *(*reprfunc)(PyObject *);
    REPRFUNC(PyObjectReturn, PyObject),
    // typedef Py_hash_t (*hashfunc)(PyObject *);
    HASHFUNC(Py_ssize_t, PyObject),
    // typedef PyObject *(*richcmpfunc) (PyObject *, PyObject *, int);
    RICHCMPFUNC(PyObjectReturn, PyObject, PyObject, Int),
    // typedef PyObject *(*getiterfunc) (PyObject *);
    GETITERFUNC(PyObjectReturn, PyObject),
    // typedef PyObject *(*iternextfunc) (PyObject *);
    ITERNEXTFUNC(PyObjectReturn, PyObject),
    // typedef PyObject *(*descrgetfunc) (PyObject *, PyObject *, PyObject *);
    DESCRGETFUNC(PyObjectReturn, PyObject, PyObject, PyObject),
    // typedef int (*descrsetfunc) (PyObject *, PyObject *, PyObject *);
    DESCRSETFUNC(Int, PyObject, PyObject, PyObject),
    // typedef int (*initproc)(PyObject *, PyObject *, PyObject *);
    INITPROC(Int, PyObject, PyObject, PyObject),
    // typedef PyObject *(*newfunc)(PyTypeObject *, PyObject *, PyObject *);
    NEWFUNC(PyObjectReturn, PyTypeObject, PyObject, PyObject),
    // typedef PyObject *(*allocfunc)(PyTypeObject *, Py_ssize_t);
    ALLOCFUNC(PyObjectReturn, PyTypeObject, Py_ssize_t),

    // TODO(fa): should be an implicit signature
    GCCOLLECT(Py_ssize_t, Int);

    public final ArgDescriptor returnValue;
    public final ArgDescriptor[] arguments;

    ExternalFunctionSignature(ArgDescriptor returnValue, ArgDescriptor... arguments) {
        this.returnValue = returnValue;
        this.arguments = arguments;
    }
}
