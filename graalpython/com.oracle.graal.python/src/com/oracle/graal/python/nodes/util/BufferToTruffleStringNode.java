/*
 * Copyright (c) 2022, 2023, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.nodes.util;

import com.oracle.graal.python.builtins.objects.buffer.PythonBufferAccessLibrary;
import com.oracle.graal.python.builtins.objects.bytes.PBytesLike;
import com.oracle.graal.python.builtins.objects.memoryview.PMemoryView;
import com.oracle.graal.python.builtins.objects.mmap.PMMap;
import com.oracle.graal.python.nodes.PGuards;
import com.oracle.graal.python.nodes.PNodeWithContext;
import com.oracle.graal.python.runtime.PosixSupportLibrary;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.graal.python.runtime.sequence.storage.NativeByteSequenceStorage;
import com.oracle.graal.python.runtime.sequence.storage.SequenceStorage;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.NeverDefault;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.profiles.InlinedBranchProfile;
import com.oracle.truffle.api.strings.TruffleString;

@ImportStatic(PGuards.class)
public abstract class BufferToTruffleStringNode extends PNodeWithContext {

    protected final boolean allowMemoryView;

    protected BufferToTruffleStringNode(boolean allowMemoryView) {
        this.allowMemoryView = allowMemoryView;
    }

    public abstract TruffleString execute(Object buffer, int byteOffset);

    @Specialization(guards = "bufferLib.hasInternalByteArray(buffer)", limit = "4")
    static TruffleString doWithInternalByteArray(Object buffer,
                    int byteOffset,
                    @CachedLibrary(value = "buffer") PythonBufferAccessLibrary bufferLib,
                    @Shared @Cached TruffleString.FromByteArrayNode fromByteArrayNode) {
        PythonBufferAccessLibrary.assertIsBuffer(buffer);
        byte[] bytes = bufferLib.getInternalByteArray(buffer);
        int bytesLen = bufferLib.getBufferLength(buffer);
        return fromByteArrayNode.execute(bytes, byteOffset, bytesLen - byteOffset, TruffleString.Encoding.ISO_8859_1, false);
    }

    @Specialization(guards = "isNativeByteSequenceStorage(bytes.getSequenceStorage())")
    static TruffleString doNativeBytesLike(PBytesLike bytes,
                    int byteOffset,
                    @Shared @Cached TruffleString.FromNativePointerNode fromNativePointerNode) {
        NativeByteSequenceStorage store = (NativeByteSequenceStorage) bytes.getSequenceStorage();
        Object ptr = store.getPtr();
        int bytesLen = store.length();
        return fromNativePointerNode.execute(ptr, byteOffset, bytesLen - byteOffset, TruffleString.Encoding.ISO_8859_1, false);
    }

    @Specialization
    static TruffleString doMMap(PMMap mmap, int byteOffset,
                    @Bind("this") Node inliningTarget,
                    @Cached InlinedBranchProfile unsupportedPosix,
                    @CachedLibrary(limit = "4") PosixSupportLibrary posixLib,
                    @CachedLibrary(limit = "1") PythonBufferAccessLibrary bufferLib,
                    @Shared @Cached TruffleString.FromNativePointerNode fromNativePointerNode,
                    @Shared @Cached TruffleString.FromByteArrayNode fromByteArrayNode) {
        int bytesLen = bufferLib.getBufferLength(mmap);
        try {
            Object ptr = new MMapPointer(posixLib.mmapGetPointer(PythonContext.get(inliningTarget).getPosixSupport(), mmap.getPosixSupportHandle()));
            return fromNativePointerNode.execute(ptr, byteOffset, bytesLen - byteOffset, TruffleString.Encoding.ISO_8859_1, false);
        } catch (PosixSupportLibrary.UnsupportedPosixFeatureException e) {
            unsupportedPosix.enter(inliningTarget);
            byte[] bytes = bufferLib.getInternalOrCopiedByteArray(mmap);
            return fromByteArrayNode.execute(bytes, byteOffset, bytesLen - byteOffset, TruffleString.Encoding.ISO_8859_1, false);
        }
    }

    @Specialization(guards = "allowMemoryView")
    static TruffleString doMemoryView(PMemoryView memoryView,
                    int byteOffset,
                    @Cached("create(false)") BufferToTruffleStringNode bufferToTruffleStringNode) {
        int internalByteOffset = memoryView.getOffset();
        return bufferToTruffleStringNode.execute(memoryView.getBuffer(), byteOffset + internalByteOffset);
    }

    @Fallback
    static TruffleString doWithInternalOrCopiedByteArray(Object buffer,
                    int byteOffset,
                    @CachedLibrary(limit = "5") PythonBufferAccessLibrary bufferLib,
                    @Shared @Cached TruffleString.FromByteArrayNode fromByteArrayNode) {
        PythonBufferAccessLibrary.assertIsBuffer(buffer);
        byte[] bytes = bufferLib.getInternalOrCopiedByteArray(buffer);
        int bytesLen = bufferLib.getBufferLength(buffer);
        return fromByteArrayNode.execute(bytes, byteOffset, bytesLen - byteOffset, TruffleString.Encoding.ISO_8859_1, false);
    }

    protected static boolean isNativeByteSequenceStorage(SequenceStorage store) {
        return store instanceof NativeByteSequenceStorage;
    }

    @NeverDefault
    public static BufferToTruffleStringNode create() {
        return create(true);
    }

    @NeverDefault
    public static BufferToTruffleStringNode create(boolean allowMemoryView) {
        return BufferToTruffleStringNodeGen.create(allowMemoryView);
    }

    @ExportLibrary(InteropLibrary.class)
    public static final class MMapPointer implements TruffleObject {
        public final long pointer;

        public MMapPointer(long pointer) {
            this.pointer = pointer;
        }

        @ExportMessage
        @SuppressWarnings("static-method")
        boolean isPointer() {
            return true;
        }

        @ExportMessage
        long asPointer() {
            return pointer;
        }
    }
}
