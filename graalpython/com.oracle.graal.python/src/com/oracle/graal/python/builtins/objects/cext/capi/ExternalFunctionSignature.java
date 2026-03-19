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
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.Int;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.Pointer;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.PrimitiveResult64;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.PyObject;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.PyObjectReturn;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.PyTypeObject;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.Py_ssize_t;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.Void;
import static com.oracle.graal.python.util.PythonUtils.toTruffleStringUncached;

import com.oracle.graal.python.annotations.CApiExternalFunctionSignatures;
import com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor;
import com.oracle.graal.python.builtins.objects.cext.common.NativeCExtSymbol;
import com.oracle.truffle.api.strings.TruffleString;

/**
 * Enum of well-known function and slot signatures. The integer values must stay in sync with the
 * definition in {code capi.h}.
 */
@CApiExternalFunctionSignatures
public enum ExternalFunctionSignature implements NativeCExtSymbol {
    // typedef PyObject *(*PyCFunction)(PyObject *, PyObject *);
    PYCFUNCTION(false, PyObjectReturn, PyObject, PyObject),
    // typedef PyObject *(*PyCFunctionWithKeywords)(PyObject *, PyObject *, PyObject *);
    PYCFUNCTION_WITH_KEYWORDS(false, PyObjectReturn, PyObject, PyObject, PyObject),
    // typedef PyObject *(*_PyCFunctionFast) (PyObject *, PyObject *const *, Py_ssize_t);
    PYCFUNCTION_FAST(false, PyObjectReturn, PyObject, Pointer, Py_ssize_t),
    // typedef PyObject *(*_PyCFunctionFastWithKeywords) (PyObject *, PyObject *const *, Py_ssize_t,
    // PyObject *);
    PYCFUNCTION_FAST_WITH_KEYWORDS(false, PyObjectReturn, PyObject, Pointer, Py_ssize_t, PyObject),
    // typedef PyObject *(*PyCMethod)(PyObject *, PyTypeObject *, PyObject *const *, size_t,
    // PyObject *);
    PYCMETHOD(false, PyObjectReturn, PyObject, PyTypeObject, Pointer, Py_ssize_t, PyObject),

    // typedef PyObject * (*unaryfunc)(PyObject *);
    UNARYFUNC(false, PyObjectReturn, PyObject),
    // typedef PyObject * (*binaryfunc)(PyObject *, PyObject *);
    BINARYFUNC(false, PyObjectReturn, PyObject, PyObject),
    // typedef PyObject * (*ternaryfunc)(PyObject *, PyObject *, PyObject *);
    TERNARYFUNC(false, PyObjectReturn, PyObject, PyObject, PyObject),
    // typedef int (*inquiry)(PyObject *);
    INQUIRY(false, Int, PyObject),
    // typedef Py_ssize_t (*lenfunc)(PyObject *);
    LENFUNC(false, PrimitiveResult64, PyObject),
    // typedef PyObject *(*ssizeargfunc)(PyObject *, Py_ssize_t);
    SSIZEARGFUNC(false, PyObjectReturn, PyObject, Py_ssize_t),

    // typedef PyObject *(*ssizessizeargfunc)(PyObject *, Py_ssize_t, Py_ssize_t);
    SSIZESSIZEARGFUNC(false, PyObjectReturn, PyObject, Py_ssize_t, Py_ssize_t),
    // typedef int(*ssizeobjargproc)(PyObject *, Py_ssize_t, PyObject *);
    SSIZEOBJARGPROC(false, Int, PyObject, Py_ssize_t, PyObject),
    // typedef int(*ssizessizeobjargproc)(PyObject *, Py_ssize_t, Py_ssize_t, PyObject *);
    SSIZESSIZEOBJARGPROC(false, Int, PyObject, Py_ssize_t, Py_ssize_t, PyObject),
    // typedef int(*objobjargproc)(PyObject *, PyObject *, PyObject *);
    OBJOBJARGPROC(false, Int, PyObject, PyObject, PyObject),

    // typedef int (*objobjproc)(PyObject *, PyObject *);
    OBJOBJPROC(false, Int, PyObject, PyObject),
    // typedef int (*visitproc)(PyObject *, void *);
    VISITPROC(false, Int, PyObject, Pointer),
    // typedef int (*traverseproc)(PyObject *, visitproc, void *);
    TRAVERSEPROC(false, Int, PyObject, Pointer, Pointer),
    // PyObject *PyType_GenericAlloc(PyTypeObject *, Py_ssize_t);
    TYPE_GENERIC_ALLOC(false, PyObjectReturn, PyTypeObject, Py_ssize_t),

    // typedef void (*freefunc)(void *);
    FREEFUNC(false, Void, Pointer),
    // typedef void (*destructor)(PyObject *);
    DESTRUCTOR(false, Void, PyObject),
    // void _Py_Dealloc(PyObject *);
    PY_DEALLOC(false, Void, PyObject),
    // typedef PyObject *(*getattrfunc)(PyObject *, char *);
    GETATTRFUNC(false, PyObjectReturn, PyObject, CharPtrAsTruffleString),
    // typedef PyObject *(*getattrofunc)(PyObject *, PyObject *);
    GETATTROFUNC(false, PyObjectReturn, PyObject, PyObject),
    // typedef int (*setattrfunc)(PyObject *, char *, PyObject *);
    SETATTRFUNC(false, Int, PyObject, CharPtrAsTruffleString, PyObject),
    // typedef int (*setattrofunc)(PyObject *, PyObject *, PyObject *);
    SETATTROFUNC(false, Int, PyObject, PyObject, PyObject),
    // typedef PyObject *(*reprfunc)(PyObject *);
    REPRFUNC(false, PyObjectReturn, PyObject),
    // typedef Py_hash_t (*hashfunc)(PyObject *);
    HASHFUNC(false, Py_ssize_t, PyObject),
    // typedef PyObject *(*richcmpfunc) (PyObject *, PyObject *, int);
    RICHCMPFUNC(false, PyObjectReturn, PyObject, PyObject, Int),
    // typedef PyObject *(*getiterfunc) (PyObject *);
    GETITERFUNC(false, PyObjectReturn, PyObject),
    // typedef PyObject *(*iternextfunc) (PyObject *);
    ITERNEXTFUNC(false, PyObjectReturn, PyObject),
    // typedef PyObject *(*descrgetfunc) (PyObject *, PyObject *, PyObject *);
    DESCRGETFUNC(false, PyObjectReturn, PyObject, PyObject, PyObject),
    // typedef int (*descrsetfunc) (PyObject *, PyObject *, PyObject *);
    DESCRSETFUNC(false, Int, PyObject, PyObject, PyObject),
    // typedef int (*initproc)(PyObject *, PyObject *, PyObject *);
    INITPROC(false, Int, PyObject, PyObject, PyObject),
    // typedef PyObject *(*newfunc)(PyTypeObject *, PyObject *, PyObject *);
    NEWFUNC(false, PyObjectReturn, PyTypeObject, PyObject, PyObject),

    // typedef PyObject *(*getter)(PyObject *, void *);
    GETTER(false, PyObjectReturn, PyObject, Pointer),
    // typedef int (*setter)(PyObject *, PyObject *, void *);
    SETTER(false, Int, PyObject, PyObject, Pointer),
    // typedef PyObject *(*Py_mod_create)(PyObject *, PyModuleDef *);
    MODCREATE(false, PyObjectReturn, Pointer, Pointer),
    // typedef int (*Py_mod_exec)(PyObject *);
    MODEXEC(false, Int, Pointer),
    // typedef PyObject *(*PyInit_mod)(void);
    MODINIT(false, Pointer),
    // typedef PThreadState** (*initialize_graal_capi)(void *, void *, void *, void *);
    CAPIINIT(false, Pointer, Pointer, Pointer, Pointer, Pointer),
    // typedef void *(*GraalPyPrivate_GetFinalizeCApiPointer)(void);
    GETFINALIZECAPIPOINTER(false, Pointer),

    // TODO(fa): should be an implicit signature
    GCCOLLECT(false, Py_ssize_t, Int),
    GETDICTPTRFUN(true, Pointer, PyObject);

    public final ArgDescriptor returnValue;
    public final ArgDescriptor[] arguments;

    /**
     * If {@code true}, the function will be called without a call boundary (see
     * {@link com.oracle.graal.python.runtime.ExecutionContext.BoundaryCallContext}). Hence, the
     * native function must not raise Python exception.
     */
    public final boolean cannotRaise;

    ExternalFunctionSignature(boolean cannotRaise, ArgDescriptor returnValue, ArgDescriptor... arguments) {
        this.cannotRaise = cannotRaise;
        this.returnValue = returnValue;
        this.arguments = arguments;
    }

    @Override
    public String getName() {
        return name();
    }

    @Override
    public TruffleString getTsName() {
        return toTruffleStringUncached(name());
    }

    @Override
    public ArgDescriptor getReturnValue() {
        return returnValue;
    }

    @Override
    public ArgDescriptor[] getArguments() {
        return arguments;
    }
}
