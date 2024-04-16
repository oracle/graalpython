/*
 * Copyright (c) 2019, 2024, Oracle and/or its affiliates. All rights reserved.
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

import java.util.logging.Level;

import com.oracle.graal.python.builtins.objects.bytes.PBytesLike;
import com.oracle.graal.python.builtins.objects.cext.capi.PySequenceArrayWrapperFactory.ToNativeStorageNodeGen;
import com.oracle.graal.python.builtins.objects.common.SequenceStorageNodes;
import com.oracle.graal.python.builtins.objects.common.SequenceStorageNodesFactory.StorageToNativeNodeGen;
import com.oracle.graal.python.builtins.objects.type.PythonAbstractClass;
import com.oracle.graal.python.runtime.sequence.PSequence;
import com.oracle.graal.python.runtime.sequence.storage.BasicSequenceStorage;
import com.oracle.graal.python.runtime.sequence.storage.ByteSequenceStorage;
import com.oracle.graal.python.runtime.sequence.storage.EmptySequenceStorage;
import com.oracle.graal.python.runtime.sequence.storage.MroSequenceStorage;
import com.oracle.graal.python.runtime.sequence.storage.NativeSequenceStorage;
import com.oracle.graal.python.runtime.sequence.storage.SequenceStorage;
import com.oracle.graal.python.util.PythonUtils;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.TruffleLogger;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.GenerateCached;
import com.oracle.truffle.api.dsl.GenerateInline;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.nodes.Node;

/**
 * Wraps a sequence object (like a list) such that it behaves like a bare C array.
 */
public final class PySequenceArrayWrapper {

    private static final TruffleLogger LOGGER = CApiContext.getLogger(PySequenceArrayWrapper.class);

    @GenerateInline
    @GenerateCached(false)
    @GenerateUncached
    public abstract static class ToNativeStorageNode extends Node {

        public abstract NativeSequenceStorage execute(Node inliningTarget, SequenceStorage object, boolean isBytesLike);

        public static NativeSequenceStorage executeUncached(SequenceStorage object, boolean isBytesLike) {
            return ToNativeStorageNodeGen.getUncached().execute(null, object, isBytesLike);
        }

        @Specialization(guards = "!isMroSequenceStorage(s)")
        static NativeSequenceStorage doManaged(Node inliningTarget, BasicSequenceStorage s, boolean isBytesLike,
                        @Shared("storageToNativeNode") @Cached SequenceStorageNodes.StorageToNativeNode storageToNativeNode,
                        @Cached SequenceStorageNodes.GetInternalObjectArrayNode getInternalArrayNode) {
            Object array;
            if (isBytesLike) {
                ByteSequenceStorage byteStorage = (ByteSequenceStorage) s;
                array = byteStorage.getInternalByteArray();
            } else {
                array = getInternalArrayNode.execute(inliningTarget, s);
            }
            return storageToNativeNode.execute(inliningTarget, array, s.length());
        }

        /*
         * This specialization uses a TruffleBoundary because we assume that there is a fixed number
         * of types (and therefore MroSequenceStorages). If types are created on a fast path, this
         * won't be fast anyway.
         */
        @Specialization
        @TruffleBoundary
        static NativeSequenceStorage doMroSequenceStorage(Node inliningTarget, MroSequenceStorage mro, boolean isBytesLike) {
            if (mro.getNativeMirror() != null) {
                return mro.getNativeMirror();
            } else {
                assert !isBytesLike;
                PythonAbstractClass[] internalClassArray = mro.getInternalClassArray();
                assert mro.length() <= internalClassArray.length;
                NativeSequenceStorage ns = StorageToNativeNodeGen.getUncached().execute(inliningTarget, internalClassArray, mro.length());
                mro.setNativeMirror(ns);
                return ns;
            }
        }

        @Specialization
        static NativeSequenceStorage doNative(NativeSequenceStorage s, @SuppressWarnings("unused") boolean isBytesLike) {
            return s;
        }

        @Specialization
        static NativeSequenceStorage doEmptyStorage(Node inliningTarget, @SuppressWarnings("unused") EmptySequenceStorage s, boolean isBytesLike,
                        @Shared("storageToNativeNode") @Cached SequenceStorageNodes.StorageToNativeNode storageToNativeNode) {
            return storageToNativeNode.execute(inliningTarget, isBytesLike ? PythonUtils.EMPTY_BYTE_ARRAY : PythonUtils.EMPTY_OBJECT_ARRAY, 0);
        }

        static boolean isNative(SequenceStorage s) {
            return s instanceof NativeSequenceStorage;
        }

        static boolean isEmptySequenceStorage(SequenceStorage s) {
            return s instanceof EmptySequenceStorage;
        }

        static boolean isMroSequenceStorage(SequenceStorage s) {
            return s instanceof MroSequenceStorage;
        }
    }

    @TruffleBoundary
    public static Object ensureNativeSequence(PSequence sequence) {
        boolean loggable = LOGGER.isLoggable(Level.FINE);
        if (loggable) {
            LOGGER.fine(String.format("ensureNativeSequence(%s)", sequence));
        }
        /*
         * MroSequenceStorages are special. We cannot simply replace them with a
         * NativeSequenceStorage because we still need the "managed" one due to the assumptions.
         * Hence, if an MroSequenceStorage goes to native, we will create an additional
         * NativeSequenceStorage and link to it.
         */
        Object result;
        SequenceStorage sequenceStorage = sequence.getSequenceStorage();
        if (sequenceStorage instanceof NativeSequenceStorage nativeStorage) {
            result = nativeStorage.getPtr();
        } else if (sequenceStorage instanceof MroSequenceStorage mro && mro.isNative()) {
            result = mro.getNativeMirror().getPtr();
        } else {
            NativeSequenceStorage nativeStorage = ToNativeStorageNode.executeUncached(sequenceStorage, sequence instanceof PBytesLike);
            if (!(sequenceStorage instanceof MroSequenceStorage)) {
                sequence.setSequenceStorage(nativeStorage);
            }
            result = nativeStorage.getPtr();
        }
        if (loggable) {
            LOGGER.fine(String.format("ensureNativeSequence(%s) = %s", sequence, PythonUtils.formatPointer(result)));
        }
        return result;
    }
}
