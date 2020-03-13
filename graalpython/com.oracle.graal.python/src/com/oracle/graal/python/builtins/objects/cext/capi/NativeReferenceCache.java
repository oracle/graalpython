/*
 * Copyright (c) 2020, Oracle and/or its affiliates. All rights reserved.
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

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.builtins.objects.PythonAbstractObject;
import com.oracle.graal.python.builtins.objects.cext.PythonAbstractNativeObject;
import com.oracle.graal.python.builtins.objects.cext.capi.CApiContext.NativeObjectReference;
import com.oracle.graal.python.nodes.util.CastToJavaLongNode;
import com.oracle.graal.python.nodes.util.CastToJavaLongNode.CannotCastException;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.truffle.api.Assumption;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.CachedContext;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.interop.ArityException;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.api.nodes.ControlFlowException;
import com.oracle.truffle.api.profiles.ConditionProfile;
import com.oracle.truffle.llvm.spi.ReferenceLibrary;

@ExportLibrary(InteropLibrary.class)
public final class NativeReferenceCache implements TruffleObject {
    public static final int CACHE_SIZE = 3;

    final long[] keys;

    int pos = 0;

    public NativeReferenceCache() {
        keys = new long[CACHE_SIZE];
    }

    @ExportMessage
    @SuppressWarnings("static-method")
    boolean isExecutable() {
        return true;
    }

    @ExportMessage
    static class Execute {

        @Specialization(guards = {"arguments.length == 2", "ref != null", "isSame(referenceLibrary, arguments, ref)"}, //
                        rewriteOn = InvalidCacheEntry.class, //
                        assumptions = "singleContextAssumption()", //
                        limit = "1")
        static PythonAbstractNativeObject doCachedPointer(@SuppressWarnings("unused") NativeReferenceCache receiver, @SuppressWarnings("unused") Object[] arguments,
                        @Shared("context") @CachedContext(PythonLanguage.class) @SuppressWarnings("unused") PythonContext context,
                        @Cached(value = "lookupNativeReference(context, arguments)", uncached = "lookupNativeReferenceUncached(context, arguments)") NativeObjectReference ref,
                        @CachedLibrary("ref.ptrObject") @SuppressWarnings("unused") ReferenceLibrary referenceLibrary) {
            PythonAbstractNativeObject wrapper = ref.get();
            if (wrapper != null) {
                return wrapper;
            }
            throw InvalidCacheEntry.INSTANCE;
        }

        @Specialization(guards = {"arguments.length == 2"}, rewriteOn = CannotCastException.class, replaces = "doCachedPointer")
        static Object doGenericInt(@SuppressWarnings("unused") NativeReferenceCache receiver, Object[] arguments,
                        @Shared("castToJavaLongNode") @Cached CastToJavaLongNode castToJavaLongNode,
                        @Shared("contextAvailableProfile") @Cached("createBinaryProfile()") ConditionProfile contextAvailableProfile,
                        @Shared("wrapperExistsProfile") @Cached("createBinaryProfile()") ConditionProfile wrapperExistsProfile,
                        @Shared("context") @CachedContext(PythonLanguage.class) PythonContext context) throws CannotCastException {
            CApiContext cApiContext = context.getCApiContext();
            // The C API context may be null during initialization of the C API.
            if (contextAvailableProfile.profile(cApiContext != null)) {
                int idx = CApiContext.idFromRefCnt(castToJavaLongNode.execute(arguments[1]));
                if (wrapperExistsProfile.profile(idx != 0)) {
                    PythonAbstractObject object = cApiContext.lookupNativeObjectReference(idx).get();
                    if (object != null) {
                        return object;
                    }
                }
            }
            return arguments[0];
        }

        @Specialization(guards = "arguments.length == 2", replaces = {"doCachedPointer", "doGenericInt"})
        static Object doGeneric(@SuppressWarnings("unused") NativeReferenceCache receiver, Object[] arguments,
                        @Shared("castToJavaLongNode") @Cached CastToJavaLongNode castToJavaLongNode,
                        @Shared("contextAvailableProfile") @Cached("createBinaryProfile()") ConditionProfile contextAvailableProfile,
                        @Shared("wrapperExistsProfile") @Cached("createBinaryProfile()") ConditionProfile wrapperExistsProfile,
                        @Shared("context") @CachedContext(PythonLanguage.class) PythonContext context) {
            try {
                return doGenericInt(receiver, arguments, castToJavaLongNode, contextAvailableProfile, wrapperExistsProfile, context);
            } catch (CannotCastException e) {
                return arguments[0];
            }
        }

        @Specialization(guards = "arguments.length != 2")
        static Object doInvalidArity(@SuppressWarnings("unused") NativeReferenceCache receiver, Object[] arguments) throws ArityException {
            throw ArityException.create(2, arguments.length);
        }

        static boolean isSame(ReferenceLibrary referenceLibrary, Object[] arguments, NativeObjectReference cachedObjectRef) {
            return referenceLibrary.isSame(cachedObjectRef.ptrObject, arguments[0]);
        }

        static NativeObjectReference lookupNativeReference(PythonContext context, Object[] arguments) {
            CApiContext cApiContext = context.getCApiContext();
            // The C API context may be null during initialization of the C API.
            if (cApiContext != null) {
                int idx = CApiContext.idFromRefCnt(CastToJavaLongNode.getUncached().execute(arguments[1]));
                return cApiContext.lookupNativeObjectReference(idx);
            }
            return null;
        }

        static NativeObjectReference lookupNativeReferenceUncached(PythonContext context, Object[] arguments) {
            // TODO(fa): this should never happen since is should always be shadowed by 'doIndexed'
            return null;
        }

        static Assumption singleContextAssumption() {
            return PythonLanguage.getCurrent().singleContextAssumption;
        }
    }

    static class InvalidCacheEntry extends ControlFlowException {
        private static final long serialVersionUID = 1L;
        private static final InvalidCacheEntry INSTANCE = new InvalidCacheEntry();
    }
}
