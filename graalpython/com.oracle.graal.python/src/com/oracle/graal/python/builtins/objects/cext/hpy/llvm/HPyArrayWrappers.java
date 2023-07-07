/*
 * Copyright (c) 2020, 2023, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.builtins.objects.cext.hpy.llvm;

import java.util.Arrays;

import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContext;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyHandle;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyNodes.HPyAsHandleNode;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyNodes.HPyCloseHandleNode;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyNodesFactory.HPyCloseHandleNodeGen;
import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Exclusive;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.GenerateCached;
import com.oracle.truffle.api.dsl.GenerateInline;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.InvalidArrayIndexException;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.profiles.ConditionProfile;
import com.oracle.truffle.api.profiles.InlinedConditionProfile;
import com.oracle.truffle.llvm.spi.NativeTypeLibrary;

abstract class HPyArrayWrappers {

    /**
     * Wraps a sequence object (like a list) such that it behaves like a {@code HPy} array (C type
     * {@code HPy *}).
     */
    @ExportLibrary(InteropLibrary.class)
    @ExportLibrary(value = NativeTypeLibrary.class, useForAOT = false)
    static final class HPyArrayWrapper implements TruffleObject {

        private static final int UNINITIALIZED = 0;
        private static final int INVALIDATED = -1;

        final GraalHPyContext hpyContext;

        final Object[] delegate;

        private final GraalHPyHandle[] wrappers;
        private long nativePointer = UNINITIALIZED;

        public HPyArrayWrapper(GraalHPyContext hpyContext, Object[] delegate) {
            this.hpyContext = hpyContext;
            this.delegate = delegate;
            this.wrappers = new GraalHPyHandle[delegate.length];
        }

        public Object[] getDelegate() {
            return delegate;
        }

        void setNativePointer(long nativePointer) {
            assert nativePointer != UNINITIALIZED;
            this.nativePointer = nativePointer;
        }

        long getNativePointer() {
            return this.nativePointer;
        }

        @Override
        public int hashCode() {
            CompilerAsserts.neverPartOfCompilation();
            final int prime = 31;
            int result = 1;
            result = prime * result + Arrays.hashCode(delegate);
            return result;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (obj == null) {
                return false;
            }
            if (getClass() != obj.getClass()) {
                return false;
            }
            // n.b.: (tfel) This is hopefully fine here, since if we get to this
            // code path, we don't speculate that either of those objects is
            // constant anymore, so any caching on them won't happen anyway
            return delegate == ((HPyArrayWrapper) obj).delegate;
        }

        @SuppressWarnings("static-method")
        @ExportMessage
        boolean hasArrayElements() {
            return true;
        }

        @ExportMessage
        long getArraySize() {
            return delegate.length;
        }

        @ExportMessage
        boolean isArrayElementReadable(long index) {
            return 0 <= index && index < delegate.length;
        }

        @ExportMessage
        Object readArrayElement(long index,
                        @Cached HPyAsHandleNode asHandleNode) throws InvalidArrayIndexException {
            if (index < 0 || index > delegate.length) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                throw InvalidArrayIndexException.create(index);
            }
            int i = (int) index;
            GraalHPyHandle wrapper = wrappers[i];
            if (wrapper == null) {
                wrapper = asHandleNode.execute(delegate[i]);
                wrappers[i] = wrapper;
            }
            return wrapper;
        }

        @ExportMessage
        boolean isPointer() {
            return nativePointer != UNINITIALIZED;
        }

        @ExportMessage
        long asPointer() throws UnsupportedMessageException {
            if (!isPointer()) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                throw UnsupportedMessageException.create();
            }
            return nativePointer;
        }

        @ExportMessage
        void toNative() {
            if (!isPointer()) {
                setNativePointer(hpyContext.createNativeArguments(delegate));
            }
        }

        @ExportMessage
        @SuppressWarnings("static-method")
        boolean hasNativeType() {
            return hpyContext.getBackend() instanceof GraalHPyLLVMContext;
        }

        @ExportMessage
        Object getNativeType() {
            if (hpyContext.getBackend() instanceof GraalHPyLLVMContext llvmContext) {
                return llvmContext.getHPyArrayNativeType();
            }
            throw CompilerDirectives.shouldNotReachHere();
        }

        void close() {
            for (int i = 0; i < wrappers.length; i++) {
                if (wrappers[i] != null) {
                    HPyCloseHandleNodeGen.getUncached().execute(wrappers[i]);
                    wrappers[i] = null;
                }
            }
            if (isPointer()) {
                hpyContext.freeNativeArgumentsArray(delegate.length);
            }
        }
    }

    @GenerateInline
    @GenerateCached(false)
    abstract static class HPyCloseArrayWrapperNode extends Node {

        public abstract void execute(Node inliningTarget, HPyArrayWrapper wrapper);

        @Specialization(guards = {"cachedLen == wrapper.delegate.length", "cachedLen <= 8"}, limit = "1")
        @ExplodeLoop
        static void doCachedLen(Node inliningTarget, HPyArrayWrapper wrapper,
                        @Cached("wrapper.delegate.length") int cachedLen,
                        @Shared @Cached(inline = false) HPyCloseHandleNode closeHandleNode,
                        @Cached(value = "createProfiles(cachedLen)", dimensions = 1) ConditionProfile[] profiles,
                        @Shared @Cached InlinedConditionProfile isPointerProfile) {
            for (int i = 0; i < cachedLen; i++) {
                Object element = wrapper.delegate[i];
                if (profiles[i].profile(element instanceof GraalHPyHandle)) {
                    closeHandleNode.execute(element);
                }
            }
            if (isPointerProfile.profile(inliningTarget, wrapper.isPointer())) {
                wrapper.hpyContext.freeNativeArgumentsArray(wrapper.delegate.length);
                wrapper.setNativePointer(HPyArrayWrapper.INVALIDATED);
            }
        }

        @Specialization(replaces = "doCachedLen")
        static void doLoop(Node inliningTarget, HPyArrayWrapper wrapper,
                        @Shared @Cached(inline = false) HPyCloseHandleNode closeHandleNode,
                        @Exclusive @Cached InlinedConditionProfile profile,
                        @Shared @Cached InlinedConditionProfile isPointerProfile) {
            int n = wrapper.delegate.length;
            for (int i = 0; i < n; i++) {
                Object element = wrapper.delegate[i];
                if (profile.profile(inliningTarget, element instanceof GraalHPyHandle)) {
                    closeHandleNode.execute(element);
                }
            }
            if (isPointerProfile.profile(inliningTarget, wrapper.isPointer())) {
                wrapper.hpyContext.freeNativeArgumentsArray(wrapper.delegate.length);
                wrapper.setNativePointer(HPyArrayWrapper.INVALIDATED);
            }
        }

        static ConditionProfile[] createProfiles(int n) {
            ConditionProfile[] profiles = new ConditionProfile[n];
            for (int i = 0; i < profiles.length; i++) {
                profiles[i] = ConditionProfile.create();
            }
            return profiles;
        }
    }
}
