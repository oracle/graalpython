/*
 * Copyright (c) 2019, 2023, Oracle and/or its affiliates. All rights reserved.
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

import com.oracle.graal.python.builtins.objects.bytes.PBytesLike;
import com.oracle.graal.python.builtins.objects.cext.capi.PySequenceArrayWrapperFactory.ToNativeStorageNodeGen;
import com.oracle.graal.python.builtins.objects.common.SequenceStorageNodes;
import com.oracle.graal.python.runtime.sequence.PSequence;
import com.oracle.graal.python.runtime.sequence.storage.EmptySequenceStorage;
import com.oracle.graal.python.runtime.sequence.storage.NativeSequenceStorage;
import com.oracle.graal.python.runtime.sequence.storage.SequenceStorage;
import com.oracle.graal.python.util.PythonUtils;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.GenerateCached;
import com.oracle.truffle.api.dsl.GenerateInline;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.profiles.InlinedConditionProfile;

/**
 * Wraps a sequence object (like a list) such that it behaves like a bare C array.
 */
public final class PySequenceArrayWrapper {

    @GenerateInline
    @GenerateCached(false)
    @GenerateUncached
    public abstract static class ToNativeStorageNode extends Node {

        public abstract NativeSequenceStorage execute(Node inliningTarget, SequenceStorage object, boolean isBytesLike);

        public static NativeSequenceStorage executeUncached(SequenceStorage object, boolean isBytesLike) {
            return ToNativeStorageNodeGen.getUncached().execute(null, object, isBytesLike);
        }

        public static boolean isEmptySequenceStorage(SequenceStorage s) {
            return s instanceof EmptySequenceStorage;
        }

        @Specialization(guards = {"!isNative(s)", "!isEmptySequenceStorage(s)"})
        static NativeSequenceStorage doManaged(Node inliningTarget, SequenceStorage s, @SuppressWarnings("unused") boolean isBytesLike,
                        @Cached InlinedConditionProfile isObjectArrayProfile,
                        @Shared("storageToNativeNode") @Cached(inline = false) SequenceStorageNodes.StorageToNativeNode storageToNativeNode,
                        @Cached SequenceStorageNodes.GetInternalArrayNode getInternalArrayNode) {
            Object array = getInternalArrayNode.execute(inliningTarget, s);
            if (isBytesLike) {
                assert array instanceof byte[];
            } else if (!isObjectArrayProfile.profile(inliningTarget, array instanceof Object[])) {
                array = generalize(s);
            }
            return storageToNativeNode.execute(array, s.length());
        }

        @TruffleBoundary
        private static Object generalize(SequenceStorage s) {
            return s.getInternalArray();
        }

        @Specialization
        static NativeSequenceStorage doNative(NativeSequenceStorage s, @SuppressWarnings("unused") boolean isBytesLike) {
            return s;
        }

        @Specialization
        static NativeSequenceStorage doEmptyStorage(@SuppressWarnings("unused") EmptySequenceStorage s, @SuppressWarnings("unused") boolean isBytesLike,
                        @Shared("storageToNativeNode") @Cached(inline = false) SequenceStorageNodes.StorageToNativeNode storageToNativeNode) {
            // TODO(fa): not sure if that completely reflects semantics
            return storageToNativeNode.execute(PythonUtils.EMPTY_BYTE_ARRAY, 0);
        }

        protected static boolean isNative(SequenceStorage s) {
            return s instanceof NativeSequenceStorage;
        }
    }

    @TruffleBoundary
    public static Object ensureNativeSequence(PSequence sequence) {
        SequenceStorage sequenceStorage = sequence.getSequenceStorage();
        if (sequenceStorage instanceof NativeSequenceStorage nativeStorage) {
            return nativeStorage.getPtr();
        }
        NativeSequenceStorage nativeStorage = ToNativeStorageNode.executeUncached(sequenceStorage, sequence instanceof PBytesLike);
        sequence.setSequenceStorage(nativeStorage);
        return nativeStorage.getPtr();
    }
}
