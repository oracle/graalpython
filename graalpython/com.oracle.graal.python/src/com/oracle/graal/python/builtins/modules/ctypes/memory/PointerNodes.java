package com.oracle.graal.python.builtins.modules.ctypes.memory;

import static com.oracle.graal.python.builtins.modules.ctypes.CtypesNodes.WCHAR_T_SIZE;
import static com.oracle.graal.python.util.PythonUtils.ARRAY_ACCESSOR;

import java.lang.reflect.Field;

import com.oracle.graal.python.builtins.modules.ctypes.FFIType;
import com.oracle.graal.python.builtins.modules.ctypes.memory.Pointer.ByteArrayStorage;
import com.oracle.graal.python.builtins.modules.ctypes.memory.Pointer.LongPointerStorage;
import com.oracle.graal.python.builtins.modules.ctypes.memory.Pointer.MemoryBlock;
import com.oracle.graal.python.builtins.modules.ctypes.memory.Pointer.MemoryViewStorage;
import com.oracle.graal.python.builtins.modules.ctypes.memory.Pointer.NFIPointerStorage;
import com.oracle.graal.python.builtins.modules.ctypes.memory.Pointer.NullStorage;
import com.oracle.graal.python.builtins.modules.ctypes.memory.Pointer.PointerArrayStorage;
import com.oracle.graal.python.builtins.modules.ctypes.memory.Pointer.PythonObjectStorage;
import com.oracle.graal.python.builtins.modules.ctypes.memory.Pointer.Storage;
import com.oracle.graal.python.builtins.objects.buffer.PythonBufferAccessLibrary;
import com.oracle.graal.python.builtins.objects.cext.PythonNativeVoidPtr;
import com.oracle.graal.python.builtins.objects.memoryview.PMemoryView;
import com.oracle.graal.python.nodes.util.CastToJavaUnsignedLongNode;
import com.oracle.graal.python.util.PythonUtils;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.GenerateCached;
import com.oracle.truffle.api.dsl.GenerateInline;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.Specialization;
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
        static void doBytes(byte[] dst, int dstOffset, @SuppressWarnings("unused") MemoryBlock srcMemory, ByteArrayStorage src, int srcOffset, int size) {
            PythonUtils.arraycopy(src.bytes, srcOffset, dst, dstOffset, size);
        }

        @Specialization(limit = "1")
        static void doMemoryView(byte[] dst, int dstOffset, @SuppressWarnings("unused") MemoryBlock srcMemory, MemoryViewStorage src, int srcOffset, int size,
                        @CachedLibrary("src.value") PythonBufferAccessLibrary bufferLib) {
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
        static void doNativeMemory(byte[] dst, int dstOffset, @SuppressWarnings("unused") MemoryBlock srcMemory, LongPointerStorage src, int srcOffset, int size) {
            UNSAFE.copyMemory(null, src.pointer + srcOffset, dst, byteArrayOffset(dstOffset), size);
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

        @Specialization(limit = "1")
        static byte doMemoryView(@SuppressWarnings("unused") MemoryBlock memory, MemoryViewStorage storage, int offset,
                        @CachedLibrary("storage.value") PythonBufferAccessLibrary bufferLib) {
            return bufferLib.readByte(storage, offset);
        }

        @Specialization
        static byte doNativeMemory(@SuppressWarnings("unused") MemoryBlock memory, LongPointerStorage storage, int offset) {
            return UNSAFE.getByte(storage.pointer + offset);
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
        static short doNativeMemory(@SuppressWarnings("unused") MemoryBlock memory, LongPointerStorage storage, int offset) {
            return UNSAFE.getShort(storage.pointer + offset);
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
        static int doNativeMemory(@SuppressWarnings("unused") MemoryBlock memory, LongPointerStorage storage, int offset) {
            return UNSAFE.getInt(storage.pointer + offset);
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
        static long doNativeMemory(@SuppressWarnings("unused") MemoryBlock memory, LongPointerStorage storage, int offset) {
            return UNSAFE.getLong(storage.pointer + offset);
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

        @Specialization(limit = "1")
        static void doMemoryView(@SuppressWarnings("unused") MemoryBlock dstMemory, MemoryViewStorage dst, int dstOffset, byte[] src, int srcOffset, int size,
                        @CachedLibrary("dst.value") PythonBufferAccessLibrary bufferLib) {
            bufferLib.writeFromByteArray(dst.memoryView, dstOffset, src, srcOffset, size);
        }

        @Specialization
        static void doNativeMemory(@SuppressWarnings("unused") MemoryBlock dstMemory, LongPointerStorage dst, int dstOffset, byte[] src, int srcOffset, int size) {
            UNSAFE.copyMemory(src, byteArrayOffset(srcOffset), null, dst.pointer + dstOffset, size);
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
        static void doNativeMemory(@SuppressWarnings("unused") MemoryBlock dstMemory, LongPointerStorage dst, int dstOffset, byte value) {
            UNSAFE.putByte(dst.pointer + dstOffset, value);
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
        static void doNativeMemory(@SuppressWarnings("unused") MemoryBlock dstMemory, LongPointerStorage dst, int dstOffset, short value) {
            UNSAFE.putShort(dst.pointer + dstOffset, value);
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
        static void doNativeMemory(@SuppressWarnings("unused") MemoryBlock dstMemory, LongPointerStorage dst, int dstOffset, int value) {
            UNSAFE.putInt(dst.pointer + dstOffset, value);
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
        static void doNativeMemory(@SuppressWarnings("unused") MemoryBlock dstMemory, LongPointerStorage dst, int dstOffset, long value) {
            UNSAFE.putLong(dst.pointer + dstOffset, value);
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

    @GenerateUncached
    @GenerateInline
    @GenerateCached(false)
    public abstract static class GetPointerValueNode extends Node {
        public final Object execute(Node inliningTarget, Pointer ptr) {
            return execute(inliningTarget, ptr.memory, ptr.memory.storage, ptr.offset);
        }

        protected abstract Object execute(Node inliningTarget, MemoryBlock memory, Storage storage, int offset);

        @Specialization
        static long doBytes(@SuppressWarnings("unused") MemoryBlock memory, ByteArrayStorage storage, int offset) {
            return ARRAY_ACCESSOR.getLong(storage.bytes, offset);
        }

        @Specialization
        static Object doNativePointer(@SuppressWarnings("unused") MemoryBlock memory, LongPointerStorage storage, int offset) {
            return storage.pointer + offset;
        }

        @Specialization
        static Object doNFIPointer(@SuppressWarnings("unused") MemoryBlock memory, NFIPointerStorage storage, int offset) {
            if (offset != 0) {
                throw CompilerDirectives.shouldNotReachHere("Invalid offset for a pointer");
            }
            return storage.pointer;
        }
    }

    @GenerateUncached
    @GenerateInline
    @GenerateCached(false)
    public abstract static class PointerFromLongNode extends Node {
        public abstract Pointer execute(Node inliningTarget, Object value);

        @Specialization
        Pointer doNativeVoidPtr(PythonNativeVoidPtr value) {
            Object pointerObject = value.getPointerObject();
            if (pointerObject instanceof Pointer pointer) {
                return pointer;
            }
            return Pointer.nativeMemory(pointerObject);
        }

        @Fallback
        Pointer doLong(Object value,
                        @Cached(inline = false) CastToJavaUnsignedLongNode cast) {
            long pointer = cast.execute(value);
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
            return execute(inliningTarget, ptr.memory.storage, ptr.offset);
        }

        protected abstract Object execute(Node inliningTarget, Storage storage, int offset);

        @Specialization(guards = "offset == 0")
        static Object doPythonObject(PythonObjectStorage storage, @SuppressWarnings("unused") int offset) {
            return storage.pythonObject;
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

        @Fallback
        static void doOther(Node inliningTarget, MemoryBlock memory, Storage storage, int offset, Pointer value,
                        @Cached WriteLongNode writeLongNode,
                        @Cached StorageToNativeNode toNativeNode) {
            long nativePointer = toNativeNode.execute(inliningTarget, value.memory, value.memory.storage, value.offset);
            writeLongNode.execute(inliningTarget, memory, storage, offset, nativePointer);
        }
    }

    @GenerateInline
    @GenerateCached(false)
    public abstract static class ConvertToParameter extends Node {
        public final Object execute(Node inliningTarget, Pointer ptr, FFIType ffiType) {
            return execute(inliningTarget, ptr.memory, ptr.memory.storage, ptr.offset, ffiType);
        }

        protected abstract Object execute(Node inliningTarget, MemoryBlock memory, Storage storage, int offset, FFIType ffiType);

        @Specialization
        static Object doBytes(@SuppressWarnings("unused") MemoryBlock memory, ByteArrayStorage storage, int offset, FFIType ffiType) {
            if (ffiType.type.isArray() && ffiType != FFIType.ffi_type_pointer) {
                return storage.bytes;
            }
            return switch (ffiType.type) {
                case FFI_TYPE_SINT8, FFI_TYPE_UINT8 -> storage.bytes[offset];
                case FFI_TYPE_SINT16, FFI_TYPE_UINT16 -> ARRAY_ACCESSOR.getShort(storage.bytes, offset);
                case FFI_TYPE_SINT32, FFI_TYPE_UINT32 -> ARRAY_ACCESSOR.getInt(storage.bytes, offset);
                case FFI_TYPE_SINT64, FFI_TYPE_UINT64, FFI_TYPE_POINTER -> ARRAY_ACCESSOR.getLong(storage.bytes, offset);
                case FFI_TYPE_FLOAT -> ARRAY_ACCESSOR.getFloat(storage.bytes, offset);
                case FFI_TYPE_DOUBLE -> ARRAY_ACCESSOR.getDouble(storage.bytes, offset);
                default -> throw CompilerDirectives.shouldNotReachHere("Unexpected FFI type");
            };
        }

        @Specialization
        static Object doLongPointer(@SuppressWarnings("unused") MemoryBlock memory, LongPointerStorage storage, int offset, FFIType ffiType) {
            assert ffiType.type.isArray() || ffiType == FFIType.ffi_type_pointer;
            return storage.pointer + offset;
        }

        @Specialization
        static Object doNFIPointer(@SuppressWarnings("unused") MemoryBlock memory, NFIPointerStorage storage, int offset, FFIType ffiType) {
            assert ffiType.type.isArray() || ffiType == FFIType.ffi_type_pointer;
            if (offset != 0) {
                throw CompilerDirectives.shouldNotReachHere("Invalid offset for a pointer");
            }
            return storage.pointer;
        }

        @Specialization
        static Object doPointerArray(Node inliningTarget, MemoryBlock memory, PointerArrayStorage storage, int offset, FFIType ffiType,
                        @Cached ReadPointerNode readPointerNode,
                        @Cached PointerArrayToBytesNode toBytesNode,
                        @CachedLibrary(limit = "1") PythonBufferAccessLibrary bufferLib) {
            assert ffiType.type.isArray() || ffiType == FFIType.ffi_type_pointer;
            Pointer pointer = readPointerNode.execute(inliningTarget, memory, storage, offset);
            if (pointer.isNull()) {
                return 0L;
            } else if (pointer.memory.storage instanceof ByteArrayStorage derefedStorage && pointer.offset == 0) {
                /*
                 * We can pass it as a byte array and NFI will convert it to a pointer to the array.
                 * Note we change all array/pointer FFI types into [UINT_8] later before passing to
                 * NFI.
                 */
                return derefedStorage.bytes;
            } else if (pointer.memory.storage instanceof PointerArrayStorage derefedStorage && pointer.offset == 0) {
                ByteArrayStorage newStorage = toBytesNode.execute(inliningTarget, pointer.memory, derefedStorage);
                return newStorage.bytes;
            } else if (pointer.memory.storage instanceof LongPointerStorage derefedStorage) {
                return derefedStorage.pointer + pointer.offset;
            } else if (pointer.memory.storage instanceof NFIPointerStorage derefedStorage && pointer.offset == 0) {
                return derefedStorage.pointer;
            } else if (pointer.memory.storage instanceof MemoryViewStorage derefedStorage && pointer.offset == 0) {
                PMemoryView mv = derefedStorage.memoryView;
                if (bufferLib.hasInternalByteArray(mv)) {
                    return bufferLib.getInternalByteArray(mv);
                } else if (mv.getBufferPointer() != null && mv.getOffset() == 0) {
                    return mv.getBufferPointer();
                }
            }
            throw CompilerDirectives.shouldNotReachHere("Not implemented");
        }
    }

    @GenerateUncached
    @GenerateInline
    @GenerateCached(false)
    abstract static class PointerArrayToBytesNode extends Node {
        abstract ByteArrayStorage execute(Node inliningTarget, MemoryBlock memory, PointerArrayStorage storage);

        @Specialization
        static ByteArrayStorage convert(Node inliningTarget, MemoryBlock memory, PointerArrayStorage storage,
                        @Cached StorageToNativeNode toNativeNode) {
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

    @GenerateUncached
    @GenerateInline
    @GenerateCached(false)
    abstract static class StorageToNativeNode extends Node {

        abstract long execute(Node inliningTarget, MemoryBlock memory, Storage storage, int offset);

        @Specialization
        @SuppressWarnings("unused")
        static long doNull(MemoryBlock memory, NullStorage storage, int offset) {
            return 0L;
        }

        @Specialization
        @SuppressWarnings("unused")
        static long doNative(MemoryBlock memory, LongPointerStorage storage, int offset) {
            return storage.pointer + offset;
        }

        @Specialization
        static long doBytes(MemoryBlock memory, ByteArrayStorage storage, int offset) {
            int len = storage.bytes.length;
            // TODO check permissions
            // We need to copy the whole memory block to keep the pointer offsets consistent
            long pointer = UNSAFE.allocateMemory(len);
            UNSAFE.copyMemory(storage.bytes, byteArrayOffset(0), null, pointer, len);
            memory.storage = new LongPointerStorage(pointer);
            return pointer + offset;
        }

        // TODO other storages
    }

    private static Unsafe UNSAFE = getUnsafe();

    private static Unsafe getUnsafe() {
        try {
            return Unsafe.getUnsafe();
        } catch (SecurityException e) {
        }
        try {
            Field theUnsafeInstance = Unsafe.class.getDeclaredField("theUnsafe");
            theUnsafeInstance.setAccessible(true);
            return (Unsafe) theUnsafeInstance.get(Unsafe.class);
        } catch (Exception e) {
            throw new RuntimeException("exception while trying to get Unsafe.theUnsafe via reflection:", e);
        }
    }

    private static long byteArrayOffset(int offset) {
        return (long) Unsafe.ARRAY_BYTE_BASE_OFFSET + (long) Unsafe.ARRAY_BYTE_INDEX_SCALE * (long) offset;
    }
}
