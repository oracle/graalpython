/*
 * Copyright (c) 2026, 2026, Oracle and/or its affiliates. All rights reserved.
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

package com.oracle.graal.python.builtins.objects.cext.capi;

import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.CharPtrAsTruffleString;
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
import com.oracle.graal.python.nfi2.Nfi;
import com.oracle.graal.python.nfi2.NfiDowncallSignature;
import com.oracle.graal.python.nfi2.NfiType;

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
    GETATTRFUNC(PyObjectReturn, PyObject, CharPtrAsTruffleString),
    // typedef PyObject *(*getattrofunc)(PyObject *, PyObject *);
    GETATTROFUNC(PyObjectReturn, PyObject, PyObject),
    // typedef int (*setattrfunc)(PyObject *, char *, PyObject *);
    SETATTRFUNC(Int, PyObject, CharPtrAsTruffleString, PyObject),
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

    // typedef PyObject *(*getter)(PyObject *, void *);
    GETTER(PyObjectReturn, PyObject, Pointer),
    // typedef int (*setter)(PyObject *, PyObject *, void *);
    SETTER(Int, PyObject, PyObject, Pointer),

    // TODO(fa): should be an implicit signature
    GCCOLLECT(Py_ssize_t, Int);

    public final NfiDowncallSignature nfiSignature;
    public final ArgDescriptor returnValue;
    public final ArgDescriptor[] arguments;

    ExternalFunctionSignature(ArgDescriptor returnValue, ArgDescriptor... arguments) {
        this.returnValue = returnValue;
        this.arguments = arguments;
        NfiType[] nfiTypes = new NfiType[arguments.length];
        for (int i = 0; i < nfiTypes.length; i++) {
            nfiTypes[i] = arguments[i].getNFI2Type();
        }
        this.nfiSignature = Nfi.createDowncallSignature(returnValue.getNFI2Type(), nfiTypes);
    }
}
