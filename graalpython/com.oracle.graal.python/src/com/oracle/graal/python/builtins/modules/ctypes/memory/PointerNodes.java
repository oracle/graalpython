/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.builtins.modules.ctypes.memory;

import static com.oracle.graal.python.builtins.PythonBuiltinClassType.NotImplementedError;
import static com.oracle.graal.python.builtins.modules.ctypes.CtypesNodes.WCHAR_T_SIZE;
import static com.oracle.graal.python.util.PythonUtils.ARRAY_ACCESSOR;

import com.oracle.graal.python.builtins.modules.ctypes.memory.Pointer.ByteArrayStorage;
import com.oracle.graal.python.builtins.modules.ctypes.memory.Pointer.LongPointerStorage;
import com.oracle.graal.python.builtins.modules.ctypes.memory.Pointer.MemoryBlock;
import com.oracle.graal.python.builtins.modules.ctypes.memory.Pointer.MemoryViewStorage;
import com.oracle.graal.python.builtins.modules.ctypes.memory.Pointer.NullStorage;
import com.oracle.graal.python.builtins.modules.ctypes.memory.Pointer.ObjectPointerStorage;
import com.oracle.graal.python.builtins.modules.ctypes.memory.Pointer.PointerArrayStorage;
import com.oracle.graal.python.builtins.modules.ctypes.memory.Pointer.PythonObjectStorage;
import com.oracle.graal.python.builtins.modules.ctypes.memory.Pointer.Storage;
import com.oracle.graal.python.builtins.modules.ctypes.memory.Pointer.ZeroStorage;
import com.oracle.graal.python.builtins.objects.buffer.PythonBufferAccessLibrary;
import com.oracle.graal.python.builtins.objects.bytes.PBytesLike;
import com.oracle.graal.python.builtins.objects.cext.PythonNativeVoidPtr;
import com.oracle.graal.python.builtins.objects.cext.capi.CExtNodes;
import com.oracle.graal.python.builtins.objects.cext.capi.transitions.CApiTransitions;
import com.oracle.graal.python.builtins.objects.common.SequenceStorageNodes;
import com.oracle.graal.python.builtins.objects.memoryview.PMemoryView;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.util.CastToJavaUnsignedLongNode;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.graal.python.runtime.sequence.storage.ByteSequenceStorage;
import com.oracle.graal.python.runtime.sequence.storage.NativeSequenceStorage;
import com.oracle.graal.python.util.PythonUtils;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.GenerateCached;
import com.oracle.truffle.api.dsl.GenerateInline;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.nodes.Node;

import sun.misc.Unsafe;

public abstract class PointerNodes {
    @GenerateUncached
    @GenerateInline
    @GenerateCached(false)
    public abstract static class ReadBytesNode extends Node {
        public final void execute(Node inliningTarget, byte[] dst, int dstOffset, Pointer src, int size) {
            execute(inliningTarget, dst, dstOffset, src.memory, src.memory.storage, src.offset, size);
        }

        public final byte[] execute(Node inliningTarget, Pointer src, int size) {
            byte[] result = new byte[size];
            execute(inliningTarget, result, 0, src, size);
            return result;
        }

        protected abstract void execute(Node inliningTarget, byte[] dst, int dstOffset, MemoryBlock srcMemory, Storage src, int srcOffset, int size);

        @Specialization
        static void doZero(byte[] dst, int dstOffset, @SuppressWarnings("unused") MemoryBlock srcMemory, ZeroStorage src, int srcOffset, int size) {
            src.boundsCheck(srcOffset, size);
            PythonUtils.fill(dst, dstOffset, dstOffset + size, (byte) 0);
        }

        @Specialization
        static void doBytes(byte[] dst, int dstOffset, @SuppressWarnings("unused") MemoryBlock srcMemory, ByteArrayStorage src, int srcOffset, int size) {
            PythonUtils.arraycopy(src.bytes, srcOffset, dst, dstOffset, size);
        }

        @Specialization(limit = "1")
        static void doMemoryView(byte[] dst, int dstOffset, @SuppressWarnings("unused") MemoryBlock srcMemory, MemoryViewStorage src, int srcOffset, int size,
                        @CachedLibrary("src.memoryView") PythonBufferAccessLibrary bufferLib) {
            bufferLib.readIntoByteArray(src.memoryView, srcOffset, dst, dstOffset, size);
        }

        @Specialization
        @SuppressWarnings("unused")
        static void doNull(byte[] dst, int dstOffset, MemoryBlock srcMemory, NullStorage src, int srcOffset, int size) {
            // We allow reading nothing
            if (size != 0) {
                throw CompilerDirectives.shouldNotReachHere("Reading from NULL pointer");
            }
        }

        @Specialization
        static void doPointerArray(Node inliningTarget, byte[] dst, int dstOffset, MemoryBlock srcMemory, PointerArrayStorage src, int srcOffset, int size,
                        @Cached PointerArrayToBytesNode toBytesNode) {
            ByteArrayStorage newStorage = toBytesNode.execute(inliningTarget, srcMemory, src);
            doBytes(dst, dstOffset, srcMemory, newStorage, srcOffset, size);
        }

        @Specialization
        static void doNativeMemory(Node inliningTarget, byte[] dst, int dstOffset, @SuppressWarnings("unused") MemoryBlock srcMemory, LongPointerStorage src, int srcOffset, int size) {
            PythonContext context = PythonContext.get(inliningTarget);
            context.copyNativeMemory(dst, dstOffset, src.pointer + srcOffset, size);
        }
    }

    @GenerateUncached
    @GenerateInline
    @GenerateCached(false)
    public abstract static class ReadByteNode extends Node {
        public final byte execute(Node inliningTarget, Pointer ptr) {
            return execute(inliningTarget, ptr.memory, ptr.memory.storage, ptr.offset);
        }

        protected abstract byte execute(Node inliningTarget, MemoryBlock memory, Storage storage, int offset);

        @Specialization
        static byte doBytes(@SuppressWarnings("unused") MemoryBlock memory, ByteArrayStorage storage, int offset) {
            return storage.bytes[offset];
        }

        @Specialization
        static byte doZero(@SuppressWarnings("unused") MemoryBlock memory, ZeroStorage storage, int offset) {
            storage.boundsCheck(offset, Byte.BYTES);
            return 0;
        }

        @Specialization(limit = "1")
        static byte doMemoryView(@SuppressWarnings("unused") MemoryBlock memory, MemoryViewStorage storage, int offset,
                        @CachedLibrary("storage.memoryView") PythonBufferAccessLibrary bufferLib) {
            return bufferLib.readByte(storage.memoryView, offset);
        }

        @Specialization
        static byte doNativeMemory(Node inliningTarget, @SuppressWarnings("unused") MemoryBlock memory, LongPointerStorage storage, int offset) {
            Unsafe unsafe = PythonContext.get(inliningTarget).getUnsafe();
            return unsafe.getByte(storage.pointer + offset);
        }

        @Fallback
        static byte doOther(Node inliningTarget, MemoryBlock memory, Storage storage, int offset,
                        @Cached ReadBytesNode read) {
            byte[] tmp = new byte[Byte.BYTES];
            read.execute(inliningTarget, tmp, 0, memory, storage, offset, tmp.length);
            return ARRAY_ACCESSOR.getByte(tmp, 0);
        }
    }

    @GenerateUncached
    @GenerateInline
    @GenerateCached(false)
    public abstract static class ReadShortNode extends Node {
        public final short execute(Node inliningTarget, Pointer ptr) {
            return execute(inliningTarget, ptr.memory, ptr.memory.storage, ptr.offset);
        }

        protected abstract short execute(Node inliningTarget, MemoryBlock memory, Storage storage, int offset);

        @Specialization
        static short doBytes(@SuppressWarnings("unused") MemoryBlock memory, ByteArrayStorage storage, int offset) {
            return ARRAY_ACCESSOR.getShort(storage.bytes, offset);
        }

        @Specialization
        static short doZero(@SuppressWarnings("unused") MemoryBlock memory, ZeroStorage storage, int offset) {
            storage.boundsCheck(offset, Short.BYTES);
            return 0;
        }

        @Specialization
        static short doNativeMemory(Node inliningTarget, @SuppressWarnings("unused") MemoryBlock memory, LongPointerStorage storage, int offset) {
            Unsafe unsafe = PythonContext.get(inliningTarget).getUnsafe();
            return unsafe.getShort(storage.pointer + offset);
        }

        @Fallback
        static short doOther(Node inliningTarget, MemoryBlock memory, Storage storage, int offset,
                        @Cached ReadBytesNode read) {
            byte[] tmp = new byte[Short.BYTES];
            read.execute(inliningTarget, tmp, 0, memory, storage, offset, tmp.length);
            return ARRAY_ACCESSOR.getShort(tmp, 0);
        }
    }

    @GenerateUncached
    @GenerateInline
    @GenerateCached(false)
    public abstract static class ReadIntNode extends Node {
        public final int execute(Node inliningTarget, Pointer ptr) {
            return execute(inliningTarget, ptr.memory, ptr.memory.storage, ptr.offset);
        }

        protected abstract int execute(Node inliningTarget, MemoryBlock memory, Storage storage, int offset);

        @Specialization
        static int doBytes(@SuppressWarnings("unused") MemoryBlock memory, ByteArrayStorage storage, int offset) {
            return ARRAY_ACCESSOR.getInt(storage.bytes, offset);
        }

        @Specialization
        static int doZero(@SuppressWarnings("unused") MemoryBlock memory, ZeroStorage storage, int offset) {
            storage.boundsCheck(offset, Integer.BYTES);
            return 0;
        }

        @Specialization
        static int doNativeMemory(Node inliningTarget, @SuppressWarnings("unused") MemoryBlock memory, LongPointerStorage storage, int offset) {
            Unsafe unsafe = PythonContext.get(inliningTarget).getUnsafe();
            return unsafe.getInt(storage.pointer + offset);
        }

        @Fallback
        static int doOther(Node inliningTarget, MemoryBlock memory, Storage storage, int offset,
                        @Cached ReadBytesNode read) {
            byte[] tmp = new byte[Integer.BYTES];
            read.execute(inliningTarget, tmp, 0, memory, storage, offset, tmp.length);
            return ARRAY_ACCESSOR.getInt(tmp, 0);
        }
    }

    @GenerateUncached
    @GenerateInline
    @GenerateCached(false)
    public abstract static class ReadLongNode extends Node {
        public final long execute(Node inliningTarget, Pointer ptr) {
            return execute(inliningTarget, ptr.memory, ptr.memory.storage, ptr.offset);
        }

        protected abstract long execute(Node inliningTarget, MemoryBlock memory, Storage storage, int offset);

        @Specialization
        static long doBytes(@SuppressWarnings("unused") MemoryBlock memory, ByteArrayStorage storage, int offset) {
            return ARRAY_ACCESSOR.getLong(storage.bytes, offset);
        }

        @Specialization
        static long doZero(@SuppressWarnings("unused") MemoryBlock memory, ZeroStorage storage, int offset) {
            storage.boundsCheck(offset, Long.BYTES);
            return 0;
        }

        @Specialization
        static long doNativeMemory(Node inliningTarget, @SuppressWarnings("unused") MemoryBlock memory, LongPointerStorage storage, int offset) {
            Unsafe unsafe = PythonContext.get(inliningTarget).getUnsafe();
            return unsafe.getLong(storage.pointer + offset);
        }

        @Fallback
        static long doOther(Node inliningTarget, MemoryBlock memory, Storage storage, int offset,
                        @Cached ReadBytesNode read) {
            byte[] tmp = new byte[Long.BYTES];
            read.execute(inliningTarget, tmp, 0, memory, storage, offset, tmp.length);
            return ARRAY_ACCESSOR.getLong(tmp, 0);
        }
    }

    @GenerateUncached
    @GenerateInline
    @GenerateCached(false)
    public abstract static class WriteBytesNode extends Node {
        public final void execute(Node inliningTarget, Pointer dst, byte[] src) {
            execute(inliningTarget, dst, src, 0, src.length);
        }

        public final void execute(Node inliningTarget, Pointer dst, long value) {
            byte[] tmp = new byte[8];
            ARRAY_ACCESSOR.putLong(tmp, 0, value);
            execute(inliningTarget, dst, tmp);
        }

        public final void execute(Node inliningTarget, Pointer dst, byte[] src, int srcOffset, int size) {
            execute(inliningTarget, dst.memory, dst.memory.storage, dst.offset, src, srcOffset, size);
        }

        protected abstract void execute(Node inliningTarget, MemoryBlock dstMemory, Storage dst, int dstOffset, byte[] src, int srcOffset, int size);

        @Specialization
        static void doBytes(@SuppressWarnings("unused") MemoryBlock dstMemory, ByteArrayStorage dst, int dstOffset, byte[] src, int srcOffset, int size) {
            PythonUtils.arraycopy(src, srcOffset, dst.bytes, dstOffset, size);
        }

        @Specialization
        static void doZero(MemoryBlock dstMemory, ZeroStorage dst, int dstOffset, byte[] src, int srcOffset, int size) {
            doBytes(dstMemory, dst.allocateBytes(dstMemory), dstOffset, src, srcOffset, size);
        }

        @Specialization(limit = "1")
        static void doMemoryView(@SuppressWarnings("unused") MemoryBlock dstMemory, MemoryViewStorage dst, int dstOffset, byte[] src, int srcOffset, int size,
                        @CachedLibrary("dst.memoryView") PythonBufferAccessLibrary bufferLib) {
            bufferLib.writeFromByteArray(dst.memoryView, dstOffset, src, srcOffset, size);
        }

        @Specialization
        static void doNativeMemory(Node inliningTarget, @SuppressWarnings("unused") MemoryBlock dstMemory, LongPointerStorage dst, int dstOffset, byte[] src, int srcOffset, int size) {
            PythonContext context = PythonContext.get(inliningTarget);
            context.copyNativeMemory(dst.pointer + dstOffset, src, srcOffset, size);
        }

        @Specialization
        static void doPointerArray(Node inliningTarget, MemoryBlock dstMemory, PointerArrayStorage dst, int dstOffset, byte[] src, int srcOffset, int size,
                        @Cached PointerArrayToBytesNode toBytesNode) {
            ByteArrayStorage newStorage = toBytesNode.execute(inliningTarget, dstMemory, dst);
            doBytes(dstMemory, newStorage, dstOffset, src, srcOffset, size);
        }
    }

    @GenerateUncached
    @GenerateInline
    @GenerateCached(false)
    public abstract static class WriteByteNode extends Node {
        public final void execute(Node inliningTarget, Pointer dst, byte value) {
            execute(inliningTarget, dst.memory, dst.memory.storage, dst.offset, value);
        }

        protected abstract void execute(Node inliningTarget, MemoryBlock dstMemory, Storage dst, int dstOffset, byte value);

        @Specialization
        static void doBytes(@SuppressWarnings("unused") MemoryBlock dstMemory, ByteArrayStorage dst, int dstOffset, byte value) {
            dst.bytes[dstOffset] = value;
        }

        @Specialization
        static void doZero(MemoryBlock dstMemory, ZeroStorage dst, int dstOffset, byte value) {
            if (value != 0) {
                doBytes(dstMemory, dst.allocateBytes(dstMemory), dstOffset, value);
            }
        }

        @Specialization
        static void doNativeMemory(Node inliningTarget, @SuppressWarnings("unused") MemoryBlock dstMemory, LongPointerStorage dst, int dstOffset, byte value) {
            Unsafe unsafe = PythonContext.get(inliningTarget).getUnsafe();
            unsafe.putByte(dst.pointer + dstOffset, value);
        }

        @Fallback
        static void doOther(Node inliningTarget, MemoryBlock dstMemory, Storage dst, int dstOffset, byte value,
                        @Cached WriteBytesNode writeBytesNode) {
            writeBytesNode.execute(inliningTarget, dstMemory, dst, dstOffset, new byte[]{value}, 0, Byte.BYTES);
        }
    }

    @GenerateUncached
    @GenerateInline
    @GenerateCached(false)
    public abstract static class WriteShortNode extends Node {
        public final void execute(Node inliningTarget, Pointer dst, short value) {
            execute(inliningTarget, dst.memory, dst.memory.storage, dst.offset, value);
        }

        protected abstract void execute(Node inliningTarget, MemoryBlock dstMemory, Storage dst, int dstOffset, short value);

        @Specialization
        static void doBytes(@SuppressWarnings("unused") MemoryBlock dstMemory, ByteArrayStorage dst, int dstOffset, short value) {
            ARRAY_ACCESSOR.putShort(dst.bytes, dstOffset, value);
        }

        @Specialization
        static void doZero(MemoryBlock dstMemory, ZeroStorage dst, int dstOffset, short value) {
            if (value != 0) {
                doBytes(dstMemory, dst.allocateBytes(dstMemory), dstOffset, value);
            }
        }

        @Specialization
        static void doNativeMemory(Node inliningTarget, @SuppressWarnings("unused") MemoryBlock dstMemory, LongPointerStorage dst, int dstOffset, short value) {
            Unsafe unsafe = PythonContext.get(inliningTarget).getUnsafe();
            unsafe.putShort(dst.pointer + dstOffset, value);
        }

        @Fallback
        static void doOther(Node inliningTarget, MemoryBlock dstMemory, Storage dst, int dstOffset, short value,
                        @Cached WriteBytesNode writeBytesNode) {
            byte[] tmp = new byte[Short.BYTES];
            ARRAY_ACCESSOR.putShort(tmp, 0, value);
            writeBytesNode.execute(inliningTarget, dstMemory, dst, dstOffset, tmp, 0, tmp.length);
        }
    }

    @GenerateUncached
    @GenerateInline
    @GenerateCached(false)
    public abstract static class WriteIntNode extends Node {
        public final void execute(Node inliningTarget, Pointer dst, int value) {
            execute(inliningTarget, dst.memory, dst.memory.storage, dst.offset, value);
        }

        protected abstract void execute(Node inliningTarget, MemoryBlock dstMemory, Storage dst, int dstOffset, int value);

        @Specialization
        static void doBytes(@SuppressWarnings("unused") MemoryBlock dstMemory, ByteArrayStorage dst, int dstOffset, int value) {
            ARRAY_ACCESSOR.putInt(dst.bytes, dstOffset, value);
        }

        @Specialization
        static void doZero(MemoryBlock dstMemory, ZeroStorage dst, int dstOffset, int value) {
            if (value != 0) {
                doBytes(dstMemory, dst.allocateBytes(dstMemory), dstOffset, value);
            }
        }

        @Specialization
        static void doNativeMemory(Node inliningTarget, @SuppressWarnings("unused") MemoryBlock dstMemory, LongPointerStorage dst, int dstOffset, int value) {
            Unsafe unsafe = PythonContext.get(inliningTarget).getUnsafe();
            unsafe.putInt(dst.pointer + dstOffset, value);
        }

        @Fallback
        static void doOther(Node inliningTarget, MemoryBlock dstMemory, Storage dst, int dstOffset, int value,
                        @Cached WriteBytesNode writeBytesNode) {
            byte[] tmp = new byte[Integer.BYTES];
            ARRAY_ACCESSOR.putInt(tmp, 0, value);
            writeBytesNode.execute(inliningTarget, dstMemory, dst, dstOffset, tmp, 0, tmp.length);
        }
    }

    @GenerateUncached
    @GenerateInline
    @GenerateCached(false)
    @ImportStatic(PointerNodes.class)
    public abstract static class WriteLongNode extends Node {
        public final void execute(Node inliningTarget, Pointer dst, long value) {
            execute(inliningTarget, dst.memory, dst.memory.storage, dst.offset, value);
        }

        protected abstract void execute(Node inliningTarget, MemoryBlock dstMemory, Storage dst, int dstOffset, long value);

        @Specialization
        static void doBytes(@SuppressWarnings("unused") MemoryBlock dstMemory, ByteArrayStorage dst, int dstOffset, long value) {
            ARRAY_ACCESSOR.putLong(dst.bytes, dstOffset, value);
        }

        @Specialization
        static void doZero(MemoryBlock dstMemory, ZeroStorage dst, int dstOffset, long value) {
            if (value != 0) {
                doBytes(dstMemory, dst.allocateBytes(dstMemory), dstOffset, value);
            }
        }

        // Avoid converting pointer array to bytes if writing a NULL
        @Specialization(guards = {"value == 0", "isMultipleOf8(dstOffset)"})
        static void doPointerArray(@SuppressWarnings("unused") MemoryBlock dstMemory, PointerArrayStorage dst, int dstOffset, @SuppressWarnings("unused") long value) {
            dst.pointers[dstOffset / 8] = Pointer.NULL;
        }

        @Specialization
        static void doNativeMemory(Node inliningTarget, @SuppressWarnings("unused") MemoryBlock dstMemory, LongPointerStorage dst, int dstOffset, long value) {
            Unsafe unsafe = PythonContext.get(inliningTarget).getUnsafe();
            unsafe.putLong(dst.pointer + dstOffset, value);
        }

        @Fallback
        static void doOther(Node inliningTarget, MemoryBlock dstMemory, Storage dst, int dstOffset, long value,
                        @Cached WriteBytesNode writeBytesNode) {
            byte[] tmp = new byte[Long.BYTES];
            ARRAY_ACCESSOR.putLong(tmp, 0, value);
            writeBytesNode.execute(inliningTarget, dstMemory, dst, dstOffset, tmp, 0, tmp.length);
        }
    }

    @GenerateUncached
    @GenerateInline
    @GenerateCached(false)
    @ImportStatic(PointerNodes.class)
    public abstract static class MemcpyNode extends Node {
        public final void execute(Node inliningTarget, Pointer dst, Pointer src, int size) {
            execute(inliningTarget, dst.memory, dst.memory.storage, dst.offset, src.memory, src.memory.storage, src.offset, size);
        }

        protected abstract void execute(Node inliningTarget, MemoryBlock dstMemory, Storage dst, int dstOffset, MemoryBlock srcMemory, Storage src, int srcOffset, int size);

        @Specialization
        static void doBytesBytes(@SuppressWarnings("unused") MemoryBlock dstMemory, ByteArrayStorage dst, int dstOffset, @SuppressWarnings("unused") MemoryBlock srcMemory, ByteArrayStorage src,
                        int srcOffset, int size) {
            PythonUtils.arraycopy(src.bytes, srcOffset, dst.bytes, dstOffset, size);
        }

        @Specialization(guards = {"isMultipleOf8(size)", "isMultipleOf8(dstOffset)", "isMultipleOf8(srcOffset)"})
        static void doPointerPointer(@SuppressWarnings("unused") MemoryBlock dstMemory, PointerArrayStorage dst, int dstOffset, @SuppressWarnings("unused") MemoryBlock srcMemory,
                        PointerArrayStorage src, int srcOffset, int size) {
            for (int i = 0; i < size; i += 8) {
                dst.pointers[(dstOffset + i) / 8] = src.pointers[(srcOffset + i) / 8];
            }
        }

        @Specialization(guards = {"isMultipleOf8(size)", "isMultipleOf8(dstOffset)", "isMultipleOf8(srcOffset)"})
        static void doZeroPointer(MemoryBlock dstMemory, ZeroStorage dst, int dstOffset, @SuppressWarnings("unused") MemoryBlock srcMemory,
                        PointerArrayStorage src, int srcOffset, int size) {
            doPointerPointer(dstMemory, dst.allocatePointers(dstMemory), dstOffset, srcMemory, src, srcOffset, size);
        }

        @Fallback
        static void doOther(Node inliningTarget, MemoryBlock dstMemory, Storage dst, int dstOffset, MemoryBlock srcMemory, Storage src, int srcOffset, int size,
                        @Cached ReadBytesNode readBytesNode,
                        @Cached WriteBytesNode writeBytesNode) {
            byte[] tmp = new byte[size];
            readBytesNode.execute(inliningTarget, tmp, 0, srcMemory, src, srcOffset, size);
            writeBytesNode.execute(inliningTarget, dstMemory, dst, dstOffset, tmp, 0, size);
        }
    }

    @GenerateUncached
    @GenerateInline
    @GenerateCached(false)
    public abstract static class StrLenNode extends Node {
        public final int execute(Node inliningTarget, Pointer ptr) {
            return execute(inliningTarget, ptr, -1);
        }

        public final int execute(Node inliningTarget, Pointer ptr, int max) {
            return execute(inliningTarget, ptr.memory, ptr.memory.storage, ptr.offset, max);
        }

        protected abstract int execute(Node inliningTarget, MemoryBlock memory, Storage storage, int offset, int max);

        @Specialization
        static int doOther(Node inliningTarget, MemoryBlock memory, Storage storage, int offset, int max,
                        @Cached ReadByteNode readByteNode) {
            int maxlen = Integer.MAX_VALUE;
            if (max >= 0) {
                maxlen = offset + max;
            }
            for (int i = offset; i < maxlen; i++) {
                if (readByteNode.execute(inliningTarget, memory, storage, i) == '\0') {
                    return i - offset;
                }
            }
            if (max >= 0) {
                return max;
            } else {
                throw CompilerDirectives.shouldNotReachHere("NULL terminator not found");
            }
        }
    }

    protected static boolean isMultipleOf8(int num) {
        return num % 8 == 0;
    }

    @GenerateUncached
    @GenerateInline
    @GenerateCached(false)
    public abstract static class WCsLenNode extends Node {
        public final int execute(Node inliningTarget, Pointer ptr) {
            return execute(inliningTarget, ptr, -1);
        }

        public final int execute(Node inliningTarget, Pointer ptr, int max) {
            return execute(inliningTarget, ptr.memory, ptr.memory.storage, ptr.offset, max);
        }

        protected abstract int execute(Node inliningTarget, MemoryBlock memory, Storage storage, int offset, int max);

        @Specialization
        static int doOther(Node inliningTarget, MemoryBlock memory, Storage storage, int offset, int max,
                        @Cached ReadByteNode readByteNode) {
            int maxlen = Integer.MAX_VALUE;
            if (max >= 0) {
                maxlen = offset + max * WCHAR_T_SIZE;
            }
            outer: for (int i = offset; i < maxlen; i += WCHAR_T_SIZE) {
                for (int j = 0; j < WCHAR_T_SIZE; j++) {
                    if (readByteNode.execute(inliningTarget, memory, storage, i + j) != '\0') {
                        continue outer;
                    }
                }
                return (i / WCHAR_T_SIZE) - offset;
            }
            if (max >= 0) {
                return max;
            } else {
                throw CompilerDirectives.shouldNotReachHere("NULL terminator not found");
            }
        }
    }

    @GenerateCached(false)
    public abstract static class AbstractGetPointerValueNode extends Node {
        @Specialization
        @SuppressWarnings("unused")
        static long doNull(MemoryBlock memory, NullStorage storage, int offset) {
            return offset;
        }

        @Specialization
        static long doNativePointer(@SuppressWarnings("unused") MemoryBlock memory, LongPointerStorage storage, int offset) {
            return storage.pointer + offset;
        }

        @Specialization
        static long doBytes(Node inliningTarget, MemoryBlock memory, ByteArrayStorage storage, int offset) {
            int len = storage.bytes.length;
            // We need to copy the whole memory block to keep the pointer offsets consistent
            PythonContext context = PythonContext.get(inliningTarget);
            long pointer = context.allocateNativeMemory(len);
            context.copyNativeMemory(pointer, storage.bytes, 0, len);
            memory.storage = new LongPointerStorage(pointer);
            return pointer + offset;
        }

        @Specialization
        static long doZero(Node inliningTarget, MemoryBlock memory, ZeroStorage storage, int offset) {
            int len = storage.size;
            PythonContext context = PythonContext.get(inliningTarget);
            long pointer = context.allocateNativeMemory(len);
            context.setNativeMemory(pointer, len, (byte) 0);
            memory.storage = new LongPointerStorage(pointer);
            return pointer + offset;
        }

        @Specialization
        static long doPointerArray(Node inliningTarget, MemoryBlock memory, PointerArrayStorage storage, int offset,
                        @Cached(inline = false) GetPointerValueAsLongNode toNativeNode) {
            PythonContext context = PythonContext.get(inliningTarget);
            Unsafe unsafe = context.getUnsafe();
            long pointer = context.allocateNativeMemory(storage.pointers.length * 8L);
            for (int i = 0; i < storage.pointers.length; i++) {
                Pointer itemPointer = storage.pointers[i];
                long subpointer = toNativeNode.execute(inliningTarget, itemPointer.memory, itemPointer.memory.storage, itemPointer.offset);
                unsafe.putLong(pointer + i * 8L, subpointer);
            }
            memory.storage = new LongPointerStorage(pointer);
            return pointer + offset;
        }

        @Specialization
        static long doMemoryView(Node inliningTarget, MemoryBlock memory, MemoryViewStorage storage, int offset,
                        @CachedLibrary(limit = "1") InteropLibrary ilib,
                        @Cached(inline = false) SequenceStorageNodes.StorageToNativeNode storageToNativeNode) {
            PMemoryView mv = storage.memoryView;
            Object ptr = null;
            if (mv.getBufferPointer() != null) {
                ptr = mv.getBufferPointer();
            } else if (mv.getBuffer() instanceof PBytesLike bytes) {
                if (bytes.getSequenceStorage() instanceof NativeSequenceStorage nativeSequenceStorage) {
                    ptr = nativeSequenceStorage.getPtr();
                }
                if (bytes.getSequenceStorage() instanceof ByteSequenceStorage byteSequenceStorage) {
                    NativeSequenceStorage nativeStorage = storageToNativeNode.execute(byteSequenceStorage.getInternalByteArray(), byteSequenceStorage.length());
                    bytes.setSequenceStorage(nativeStorage);
                    ptr = nativeStorage.getPtr();
                }
            }
            if (ptr != null && ilib.isPointer(ptr)) {
                try {
                    long nativePointer = ilib.asPointer(ptr);
                    memory.storage = new LongPointerStorage(nativePointer);
                    return nativePointer + offset;
                } catch (UnsupportedMessageException e) {
                    throw CompilerDirectives.shouldNotReachHere(e);
                }
            }
            throw PRaiseNode.raiseUncached(inliningTarget, NotImplementedError, ErrorMessages.MEMORYVIEW_CANNOT_BE_CONVERTED_TO_NATIVE_MEMORY);
        }

        @Specialization
        static long doPythonObject(Node inliningTarget, @SuppressWarnings("unused") MemoryBlock memory, PythonObjectStorage storage, int offset,
                        @Cached(inline = false) CApiTransitions.PythonToNativeNode toNativeNode,
                        @CachedLibrary(limit = "1") InteropLibrary lib) {
            Object nativeObject = toNativeNode.execute(storage.pythonObject);
            long nativePointer;
            if (nativeObject instanceof Long longPointer) {
                nativePointer = longPointer;
            } else {
                if (!lib.isPointer(nativeObject)) {
                    lib.toNative(nativeObject);
                    if (!lib.isPointer(nativeObject)) {
                        throw PRaiseNode.raiseUncached(inliningTarget, NotImplementedError, ErrorMessages.CANNOT_CONVERT_OBJECT_POINTER_TO_NATIVE);
                    }
                }
                try {
                    nativePointer = lib.asPointer(nativeObject);
                } catch (UnsupportedMessageException e) {
                    throw CompilerDirectives.shouldNotReachHere(e);
                }
            }
            memory.storage = new LongPointerStorage(nativePointer);
            return nativePointer + offset;
        }
    }

    @GenerateUncached
    @GenerateInline
    @GenerateCached(false)
    public abstract static class GetPointerValueAsObjectNode extends AbstractGetPointerValueNode {
        public final Object execute(Node inliningTarget, Pointer ptr) {
            return execute(inliningTarget, ptr.memory, ptr.memory.storage, ptr.offset);
        }

        protected abstract Object execute(Node inliningTarget, MemoryBlock memory, Storage storage, int offset);

        @Specialization
        static Object doObjectPointer(Node inliningTarget, @SuppressWarnings("unused") MemoryBlock memory, ObjectPointerStorage storage, int offset) {
            if (offset != 0) {
                throw PRaiseNode.raiseUncached(inliningTarget, NotImplementedError, ErrorMessages.CANNOT_APPLY_OFFSET_TO_AN_OBJECT_POINTER);
            }
            return storage.pointer;
        }
    }

    @GenerateUncached
    @GenerateInline
    public abstract static class GetPointerValueAsLongNode extends AbstractGetPointerValueNode {
        public final long execute(Node inliningTarget, Pointer ptr) {
            return execute(inliningTarget, ptr.memory, ptr.memory.storage, ptr.offset);
        }

        protected abstract long execute(Node inliningTarget, MemoryBlock memory, Storage storage, int offset);

        @Specialization
        @SuppressWarnings("unused")
        static long doObjectPointer(Node inliningTarget, MemoryBlock memory, ObjectPointerStorage storage, int offset) {
            throw PRaiseNode.raiseUncached(inliningTarget, NotImplementedError, ErrorMessages.CANNOT_CONVERT_OBJECT_POINTER_TO_NATIVE);
        }
    }

    @GenerateUncached
    @GenerateInline
    @GenerateCached(false)
    public abstract static class FreeNode extends Node {
        public final void execute(Node inliningTarget, Pointer pointer) {
            execute(inliningTarget, pointer.memory.storage, pointer.offset);
            pointer.memory.storage = Pointer.NULL.memory.storage;
        }

        abstract void execute(Node inliningTarget, Storage storage, int offset);

        @Specialization
        @TruffleBoundary
        void doNativeMemory(Node inliningTarget, LongPointerStorage storage, int offset) {
            PythonContext context = PythonContext.get(inliningTarget);
            context.freeNativeMemory(storage.pointer + offset);
        }

        @Specialization
        @SuppressWarnings("unused")
        void doObjectPointer(ObjectPointerStorage storage, int offset) {
            /*
             * TODO This should call free using NFI. If it ever does, we should probably update
             * PointerReference to use a call target around this
             */
        }

        @Fallback
        @SuppressWarnings("unused")
        void doNothing(Storage storage, int offset) {
        }

        public static FreeNode getUncached() {
            return PointerNodesFactory.FreeNodeGen.getUncached();
        }
    }

    @GenerateUncached
    @GenerateInline
    @GenerateCached(false)
    public abstract static class PointerFromLongNode extends Node {
        public abstract Pointer execute(Node inliningTarget, Object value);

        @Specialization
        static Pointer doNativeVoidPtr(PythonNativeVoidPtr value) {
            Object pointerObject = value.getPointerObject();
            if (pointerObject instanceof Pointer pointer) {
                return pointer;
            }
            return Pointer.nativeMemory(pointerObject);
        }

        @Fallback
        static Pointer doLong(Node inliningTarget, Object value,
                        @Cached CastToJavaUnsignedLongNode cast) {
            long pointer = cast.execute(inliningTarget, value);
            return Pointer.nativeMemory(pointer);
        }
    }

    @GenerateUncached
    @GenerateInline
    @GenerateCached(false)
    @ImportStatic(PointerNodes.class)
    public abstract static class ReadPointerNode extends Node {
        public final Pointer execute(Node inliningTarget, Pointer ptr) {
            return execute(inliningTarget, ptr.memory, ptr.memory.storage, ptr.offset);
        }

        protected abstract Pointer execute(Node inliningTarget, MemoryBlock memory, Storage storage, int offset);

        @Specialization(guards = "isMultipleOf8(offset)")
        static Pointer doPointerArray(@SuppressWarnings("unused") MemoryBlock memory, PointerArrayStorage storage, int offset) {
            return storage.pointers[offset / 8];
        }

        @Fallback
        static Pointer doOther(Node inliningTarget, MemoryBlock memory, Storage storage, int offset,
                        @Cached ReadLongNode readLongNode) {
            return Pointer.nativeMemory(readLongNode.execute(inliningTarget, memory, storage, offset));
        }
    }

    @GenerateUncached
    @GenerateInline
    @GenerateCached(false)
    public abstract static class ReadPythonObject extends Node {
        public final Object execute(Node inliningTarget, Pointer ptr) {
            return execute(inliningTarget, ptr.memory, ptr.memory.storage, ptr.offset);
        }

        protected abstract Object execute(Node inliningTarget, MemoryBlock memory, Storage storage, int offset);

        @Specialization(guards = "offset == 0")
        static Object doPythonObject(@SuppressWarnings("unused") MemoryBlock memory, PythonObjectStorage storage, @SuppressWarnings("unused") int offset) {
            return storage.pythonObject;
        }

        @Fallback
        static Object doGeneric(Node inliningTarget, MemoryBlock memory, Storage storage, int offset,
                        @Cached GetPointerValueAsObjectNode getPointerValueAsObjectNode,
                        @Cached CExtNodes.ResolvePointerNode resolveHandleNode,
                        @Cached(inline = false) CApiTransitions.NativeToPythonNode nativeToPythonNode) {
            /*
             * We might get a pointer to a PyObject as a long when calling Python C API functions
             * through ctypes.
             */
            Object pointerObject = getPointerValueAsObjectNode.execute(inliningTarget, memory, storage, offset);
            return nativeToPythonNode.execute(resolveHandleNode.execute(inliningTarget, pointerObject));
        }
    }

    @GenerateUncached
    @GenerateInline
    @GenerateCached(false)
    @ImportStatic(PointerNodes.class)
    public abstract static class WritePointerNode extends Node {
        public final void execute(Node inliningTarget, Pointer ptr, Pointer value) {
            execute(inliningTarget, ptr.memory, ptr.memory.storage, ptr.offset, value);
        }

        protected abstract void execute(Node inliningTarget, MemoryBlock memory, Storage storage, int offset, Pointer value);

        @Specialization(guards = "isMultipleOf8(offset)")
        static void doPointerArray(@SuppressWarnings("unused") MemoryBlock memory, PointerArrayStorage storage, int offset, Pointer value) {
            storage.pointers[offset / 8] = value;

        }

        @Specialization
        static void doZero(MemoryBlock memory, ZeroStorage storage, int offset, Pointer value) {
            doPointerArray(memory, storage.allocatePointers(memory), offset, value);
        }

        @Fallback
        static void doOther(Node inliningTarget, MemoryBlock memory, Storage storage, int offset, Pointer value,
                        @Cached WriteLongNode writeLongNode,
                        @Cached GetPointerValueAsLongNode toNativeNode) {
            long nativePointer = toNativeNode.execute(inliningTarget, value.memory, value.memory.storage, value.offset);
            writeLongNode.execute(inliningTarget, memory, storage, offset, nativePointer);
        }
    }

    @GenerateUncached
    @GenerateInline
    @GenerateCached(false)
    abstract static class PointerArrayToBytesNode extends Node {
        abstract ByteArrayStorage execute(Node inliningTarget, MemoryBlock memory, PointerArrayStorage storage);

        @Specialization
        static ByteArrayStorage convert(Node inliningTarget, MemoryBlock memory, PointerArrayStorage storage,
                        @Cached GetPointerValueAsLongNode toNativeNode) {
            byte[] bytes = new byte[storage.pointers.length * 8];
            for (int i = 0; i < storage.pointers.length; i++) {
                Pointer itemPointer = storage.pointers[i];
                long pointer = toNativeNode.execute(inliningTarget, itemPointer.memory, itemPointer.memory.storage, itemPointer.offset);
                ARRAY_ACCESSOR.putLong(bytes, i * 8, pointer);
            }
            ByteArrayStorage newStorage = new ByteArrayStorage(bytes);
            memory.storage = newStorage;
            return newStorage;
        }
    }
}
