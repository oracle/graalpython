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
import com.oracle.graal.python.builtins.objects.cext.CApiGuards;
import com.oracle.graal.python.builtins.objects.cext.CExtNodes.GetRefCntNode;
import com.oracle.graal.python.builtins.objects.cext.CExtNodesFactory.GetRefCntNodeGen;
import com.oracle.graal.python.builtins.objects.cext.PythonAbstractNativeObject;
import com.oracle.graal.python.builtins.objects.cext.capi.CApiContext.NativeObjectReference;
import com.oracle.graal.python.nodes.util.CannotCastException;
import com.oracle.graal.python.nodes.util.CastToJavaLongNode;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.truffle.api.Assumption;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.TruffleLogger;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.CachedContext;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.interop.ArityException;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.api.nodes.ControlFlowException;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.profiles.ConditionProfile;
import com.oracle.truffle.api.profiles.IntValueProfile;
import com.oracle.truffle.llvm.spi.ReferenceLibrary;

@ExportLibrary(InteropLibrary.class)
public final class NativeReferenceCache implements TruffleObject {

    private final boolean steal;

    public NativeReferenceCache(boolean steal) {
        this.steal = steal;
    }

    @ExportMessage
    @SuppressWarnings("static-method")
    boolean isExecutable() {
        return true;
    }

    @ExportMessage
    Object execute(Object[] arguments,
                    @Cached ResolveNativeReferenceNode resolveNativeReferenceNode,
                    @Cached("createIdentityProfile()") IntValueProfile arityProfile) throws ArityException {
        int profiledArity = arityProfile.profile(arguments.length);
        if (profiledArity == 1) {
            return resolveNativeReferenceNode.execute(arguments[0], steal);
        } else if (profiledArity == 2) {
            return resolveNativeReferenceNode.execute(arguments[0], arguments[1], steal);
        }
        CompilerDirectives.transferToInterpreterAndInvalidate();
        throw ArityException.create(1, arguments.length);
    }

    @GenerateUncached
    @ImportStatic(CApiGuards.class)
    public abstract static class ResolveNativeReferenceNode extends Node {
        private static final TruffleLogger LOGGER = PythonLanguage.getLogger(ResolveNativeReferenceNode.class);

        private static final Object NO_REF_CNT = new Object();

        public abstract Object execute(Object pointerObject, Object refCnt, boolean steal);

        public final Object execute(Object pointerObject, boolean steal) {
            return execute(pointerObject, NO_REF_CNT, steal);
        }

        @Specialization(guards = "isResolved(pointerObject)")
        static Object doNativeWrapper(Object pointerObject, @SuppressWarnings("unused") Object refCnt, @SuppressWarnings("unused") boolean steal) {
            return pointerObject;
        }

        @Specialization(guards = {"!isResolved(pointerObject)", "ref != null", "isSame(referenceLibrary, pointerObject, ref)"}, //
                        rewriteOn = {CannotCastException.class, InvalidCacheEntry.class}, //
                        assumptions = "singleContextAssumption()", //
                        limit = "1")
        static PythonAbstractNativeObject doCachedPointer(@SuppressWarnings("unused") Object pointerObject, @SuppressWarnings("unused") Object refCnt, boolean steal,
                        @Shared("context") @CachedContext(PythonLanguage.class) @SuppressWarnings("unused") PythonContext context,
                        @Shared("stealProfile") @Cached("createBinaryProfile()") ConditionProfile stealProfile,
                        @Cached("lookupNativeReference(context, pointerObject, refCnt)") NativeObjectReference ref,
                        @CachedLibrary("ref.ptrObject") @SuppressWarnings("unused") ReferenceLibrary referenceLibrary) {
            PythonAbstractNativeObject wrapper = ref.get();
            if (wrapper != null) {
                // If this is stealing the reference, we need to fixup the managed reference count.
                if (stealProfile.profile(steal)) {
                    ref.managedRefCount++;
                }
                return wrapper;
            }
            throw InvalidCacheEntry.INSTANCE;
        }

        @Specialization(guards = {"!isResolved(pointerObject)", "!isNoRefCnt(refCnt)"}, rewriteOn = CannotCastException.class, replaces = "doCachedPointer")
        static Object doGenericIntWithRefCnt(Object pointerObject, Object refCnt, boolean steal,
                        @Shared("castToJavaLongNode") @Cached(value = "createLossy()", uncached = "getLossyUncached()") CastToJavaLongNode castToJavaLongNode,
                        @Shared("contextAvailableProfile") @Cached("createBinaryProfile()") ConditionProfile contextAvailableProfile,
                        @Shared("wrapperExistsProfile") @Cached("createBinaryProfile()") ConditionProfile wrapperExistsProfile,
                        @Shared("stealProfile") @Cached("createBinaryProfile()") ConditionProfile stealProfile,
                        @Shared("context") @CachedContext(PythonLanguage.class) PythonContext context) throws CannotCastException {
            CApiContext cApiContext = context.getCApiContext();
            // The C API context may be null during initialization of the C API.
            if (contextAvailableProfile.profile(cApiContext != null)) {
                int idx = CApiContext.idFromRefCnt(castToJavaLongNode.execute(refCnt));
                return lookupNativeObjectReference(pointerObject, idx, steal, wrapperExistsProfile, stealProfile, cApiContext);
            }
            return pointerObject;
        }

        @Specialization(guards = {"!isResolved(pointerObject)", "isNoRefCnt(refCnt)"}, replaces = "doCachedPointer")
        static Object doGenericInt(Object pointerObject, @SuppressWarnings("unused") Object refCnt, boolean steal,
                        @Shared("getObRefCnt") @Cached GetRefCntNode getRefCntNode,
                        @Shared("contextAvailableProfile") @Cached("createBinaryProfile()") ConditionProfile contextAvailableProfile,
                        @Shared("wrapperExistsProfile") @Cached("createBinaryProfile()") ConditionProfile wrapperExistsProfile,
                        @Shared("stealProfile") @Cached("createBinaryProfile()") ConditionProfile stealProfile,
                        @Shared("context") @CachedContext(PythonLanguage.class) PythonContext context) {
            CApiContext cApiContext = context.getCApiContext();
            // The C API context may be null during initialization of the C API.
            if (contextAvailableProfile.profile(cApiContext != null)) {
                int idx = CApiContext.idFromRefCnt(getRefCntNode.execute(pointerObject));
                return lookupNativeObjectReference(pointerObject, idx, steal, wrapperExistsProfile, stealProfile, cApiContext);
            }
            return pointerObject;
        }

        @Specialization(guards = "!isResolved(pointerObject)", replaces = {"doCachedPointer", "doGenericIntWithRefCnt", "doGenericInt"})
        static Object doGeneric(Object pointerObject, Object refCnt, boolean steal,
                        @Shared("getObRefCnt") @Cached GetRefCntNode getRefCntNode,
                        @Shared("castToJavaLongNode") @Cached(value = "createLossy()", uncached = "getLossyUncached()") CastToJavaLongNode castToJavaLongNode,
                        @Shared("contextAvailableProfile") @Cached("createBinaryProfile()") ConditionProfile contextAvailableProfile,
                        @Shared("wrapperExistsProfile") @Cached("createBinaryProfile()") ConditionProfile wrapperExistsProfile,
                        @Shared("stealProfile") @Cached("createBinaryProfile()") ConditionProfile stealProfile,
                        @Shared("context") @CachedContext(PythonLanguage.class) PythonContext context) {
            if (isNoRefCnt(refCnt)) {
                return doGenericInt(pointerObject, refCnt, steal, getRefCntNode, contextAvailableProfile, wrapperExistsProfile, stealProfile, context);
            }
            try {
                return doGenericIntWithRefCnt(pointerObject, refCnt, steal, castToJavaLongNode, contextAvailableProfile, wrapperExistsProfile, stealProfile, context);
            } catch (CannotCastException e) {
                return pointerObject;
            }
        }

        private static Object lookupNativeObjectReference(Object pointerObject, int idx, boolean steal, ConditionProfile wrapperExistsProfile, ConditionProfile stealProfile, CApiContext cApiContext) {
            if (wrapperExistsProfile.profile(idx > 0)) {
                NativeObjectReference ref = cApiContext.lookupNativeObjectReference(idx);

                PythonAbstractObject object = ref.get();
                if (object != null) {
                    // If this is stealing the reference, we need to fixup the managed reference
                    // count.
                    if (stealProfile.profile(steal)) {
                        ref.managedRefCount++;
                    }
                    return object;
                }
            } else if (idx < 0) {
                LOGGER.warning(() -> String.format("negative native reference ID %d for object %s", idx, CApiContext.asHex(pointerObject)));
            }
            return pointerObject;
        }

        static boolean isSame(ReferenceLibrary referenceLibrary, Object pointerObject, NativeObjectReference cachedObjectRef) {
            return referenceLibrary.isSame(cachedObjectRef.ptrObject, pointerObject);
        }

        static NativeObjectReference lookupNativeReference(PythonContext context, Object pointerObject, Object refCnt) {
            CApiContext cApiContext = context.getCApiContext();
            // The C API context may be null during initialization of the C API.
            if (cApiContext != null) {
                int idx;
                if (isNoRefCnt(refCnt)) {
                    idx = CApiContext.idFromRefCnt(GetRefCntNodeGen.getUncached().execute(pointerObject));
                } else {
                    idx = CApiContext.idFromRefCnt(CastToJavaLongNode.getLossyUncached().execute(refCnt));
                }
                return cApiContext.lookupNativeObjectReference(idx);
            }
            return null;
        }

        static boolean isResolved(Object object) {
            return CApiGuards.isNativeWrapper(object) || object instanceof String;
        }

        static Assumption singleContextAssumption() {
            return PythonLanguage.getCurrent().singleContextAssumption;
        }

        static boolean isNoRefCnt(Object refCnt) {
            return refCnt == NO_REF_CNT;
        }
    }

    static class InvalidCacheEntry extends ControlFlowException {
        private static final long serialVersionUID = 1L;
        private static final InvalidCacheEntry INSTANCE = new InvalidCacheEntry();
    }
}
