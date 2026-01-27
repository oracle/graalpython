/*
 * Copyright (c) 2022, 2026, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.builtins.modules.cext;

import static com.oracle.graal.python.builtins.modules.cext.PythonCextBuiltins.CApiCallPath.Direct;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.PYWEAKREFERENCE_PTR;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.PyObject;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.PyObjectRawPointer;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.PyObjectTransfer;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.Void;
import static com.oracle.graal.python.nfi2.NativeMemory.NULLPTR;
import static com.oracle.graal.python.nodes.BuiltinNames.T_PROXY_TYPE;
import static com.oracle.graal.python.nodes.BuiltinNames.T__WEAKREF;

import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.modules.cext.PythonCextBuiltins.CApiBinaryBuiltinNode;
import com.oracle.graal.python.builtins.modules.cext.PythonCextBuiltins.CApiBuiltin;
import com.oracle.graal.python.builtins.modules.weakref.PProxyType;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.cext.capi.ExternalFunctionNodes.ToNativeBorrowedNode;
import com.oracle.graal.python.builtins.objects.cext.capi.transitions.CApiTransitions.NativeToPythonNode;
import com.oracle.graal.python.builtins.objects.cext.capi.transitions.CApiTransitions.PythonToNativeNewRefNode;
import com.oracle.graal.python.builtins.objects.module.PythonModule;
import com.oracle.graal.python.builtins.objects.referencetype.PReferenceType;
import com.oracle.graal.python.builtins.objects.referencetype.ReferenceTypeBuiltins.ReferenceTypeNode;
import com.oracle.graal.python.lib.PyObjectCallMethodObjArgs;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Specialization;

public final class PythonCextWeakrefBuiltins {

    @CApiBuiltin(ret = Void, args = {PyObjectRawPointer}, call = Direct, acquireGil = false, canRaise = false)
    public static void PyObject_ClearWeakRefs(@SuppressWarnings("unused") long pyObject) {
        // TODO: Implement
    }

    @CApiBuiltin(ret = PyObjectTransfer, args = {PyObject, PyObject}, call = Direct, acquireGil = false, canRaise = true)
    abstract static class PyWeakref_NewRef extends CApiBinaryBuiltinNode {
        @Specialization
        static Object refType(Object object, Object callback,
                        @Cached ReferenceTypeNode referenceType) {
            return referenceType.execute(PythonBuiltinClassType.PReferenceType, object, callback);
        }
    }

    @CApiBuiltin(ret = PyObjectRawPointer, args = {PyObjectRawPointer, PyObjectRawPointer}, call = Direct, acquireGil = false, canRaise = true)
    public static long PyWeakref_NewProxy(long objectPtr, long callbackPtr) {
        PythonModule weakrefModule = PythonContext.get(null).lookupBuiltinModule(T__WEAKREF);
        Object callback;
        if (callbackPtr == NULLPTR) {
            callback = PNone.NO_VALUE;
        } else {
            callback = NativeToPythonNode.executeRawUncached(callbackPtr);
        }
        Object object = NativeToPythonNode.executeRawUncached(objectPtr);
        Object proxy = PyObjectCallMethodObjArgs.executeUncached(weakrefModule, T_PROXY_TYPE, object, callback);
        return PythonToNativeNewRefNode.executeLongUncached(proxy);
    }

    @CApiBuiltin(ret = PyObjectRawPointer, args = {PyObjectRawPointer}, call = Direct, acquireGil = false)
    public static long PyWeakref_GetObject(long referencePtr) {
        Object reference = NativeToPythonNode.executeRawUncached(referencePtr);
        if (reference instanceof PReferenceType ref) {
            return ToNativeBorrowedNode.executeUncached(ref.getPyObject());
        } else if (reference instanceof PProxyType proxy) {
            PReferenceType ref = proxy.weakReference;
            if (ref != null) {
                return ToNativeBorrowedNode.executeUncached(ref.getPyObject());
            }
        } else {
            throw PythonCextBuiltins.badInternalCall("PyWeakref_GetObject", "referencePtr");
        }
        /*
         * This weak reference has died in the managed side due to its referent being collected.
         */
        return PythonContext.get(null).getCApiContext().getNonePtr();
    }

    @CApiBuiltin(name = "_PyWeakref_ClearRef", ret = Void, args = {PYWEAKREFERENCE_PTR}, call = Direct, acquireGil = false, canRaise = false)
    public static void PyWeakref_ClearRef(long referencePtr) {
        Object reference = NativeToPythonNode.executeRawUncached(referencePtr);
        if (reference instanceof PReferenceType ref) {
            ref.clearRef();
        }
    }
}
