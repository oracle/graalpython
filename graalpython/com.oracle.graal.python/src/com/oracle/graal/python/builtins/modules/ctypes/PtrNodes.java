package com.oracle.graal.python.builtins.modules.ctypes;

import static com.oracle.graal.python.builtins.modules.ctypes.CtypesNodes.WCHAR_T_SIZE;
import static com.oracle.graal.python.util.PythonUtils.ARRAY_ACCESSOR;

import java.lang.reflect.Field;

import com.oracle.graal.python.builtins.modules.ctypes.PtrValue.ByteArrayStorage;
import com.oracle.graal.python.builtins.modules.ctypes.PtrValue.MemoryViewStorage;
import com.oracle.graal.python.builtins.modules.ctypes.PtrValue.NativeMemoryStorage;
import com.oracle.graal.python.builtins.modules.ctypes.PtrValue.NativePointerStorage;
import com.oracle.graal.python.builtins.modules.ctypes.PtrValue.NullStorage;
import com.oracle.graal.python.builtins.modules.ctypes.PtrValue.PointerArrayStorage;
import com.oracle.graal.python.builtins.modules.ctypes.PtrValue.PythonObjectStorage;
import com.oracle.graal.python.builtins.modules.ctypes.PtrValue.Storage;
import com.oracle.graal.python.builtins.objects.buffer.PythonBufferAccessLibrary;
import com.oracle.graal.python.builtins.objects.memoryview.PMemoryView;
import com.oracle.graal.python.util.PythonUtils;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.dsl.Bind;
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

public abstract class PtrNodes {
    @GenerateUncached
    public abstract static class ReadBytesNode extends Node {
        public final void execute(byte[] dst, int dstOffset, PtrValue src, int size) {
            execute(dst, dstOffset, src.ptr, src.offset, size);
        }

        public final byte[] execute(PtrValue src, int size) {
            byte[] result = new byte[size];
            execute(result, 0, src, size);
            return result;
        }

        protected abstract void execute(byte[] dst, int dstOffset, Storage src, int srcOffset, int size);

        @Specialization
        static void doBytes(byte[] dst, int dstOffset, ByteArrayStorage src, int srcOffset, int size) {
            PythonUtils.arraycopy(src.value, srcOffset, dst, dstOffset, size);
        }

        @Specialization(limit = "1")
        static void doMemoryView(byte[] dst, int dstOffset, MemoryViewStorage src, int srcOffset, int size,
                        @CachedLibrary("src.value") PythonBufferAccessLibrary bufferLib) {
            bufferLib.readIntoByteArray(src.value, srcOffset, dst, dstOffset, size);
        }

        @Specialization
        @SuppressWarnings("unused")
        static void doNull(byte[] dst, int dstOffset, NullStorage src, int srcOffset, int size) {
            if (size != 0) {
                throw CompilerDirectives.shouldNotReachHere("Reading from NULL pointer");
            }
        }

        @Specialization
        static void doPointerArray(byte[] dst, int dstOffset, PointerArrayStorage src, int srcOffset, int size,
                        @Bind("this") Node inliningTarget,
                        @Cached PointerArrayToBytesNode toBytesNode) {
            toBytesNode.execute(inliningTarget, src);
            PythonUtils.arraycopy(src.nativePointerBytes, srcOffset, dst, dstOffset, size);
        }

        @Specialization
        static void doNativeMemory(byte[] dst, int dstOffset, NativeMemoryStorage src, int srcOffset, int size) {
            UNSAFE.copyMemory(null, src.pointer + srcOffset, dst, byteArrayOffset(dstOffset), size);
        }
    }

    @GenerateUncached
    public abstract static class ReadByteNode extends Node {
        public final byte execute(PtrValue ptr) {
            return execute(ptr.ptr, ptr.offset);
        }

        protected abstract byte execute(Storage storage, int offset);

        @Specialization
        static byte doBytes(ByteArrayStorage storage, int offset) {
            return storage.value[offset];
        }

        @Specialization(limit = "1")
        static byte doMemoryView(MemoryViewStorage storage, int offset,
                        @CachedLibrary("storage.value") PythonBufferAccessLibrary bufferLib) {
            return bufferLib.readByte(storage, offset);
        }

        @Specialization
        static byte doNativeMemory(NativeMemoryStorage storage, int offset) {
            return UNSAFE.getByte(storage.pointer + offset);
        }

        @Fallback
        static byte doOther(Storage storage, int offset,
                        @Cached ReadBytesNode read) {
            byte[] tmp = new byte[Byte.BYTES];
            read.execute(tmp, 0, storage, offset, tmp.length);
            return ARRAY_ACCESSOR.getByte(tmp, 0);
        }
    }

    @GenerateUncached
    public abstract static class ReadShortNode extends Node {
        public final short execute(PtrValue ptr) {
            return execute(ptr.ptr, ptr.offset);
        }

        protected abstract short execute(Storage storage, int offset);

        @Specialization
        static short doBytes(ByteArrayStorage storage, int offset) {
            return ARRAY_ACCESSOR.getShort(storage.value, offset);
        }

        @Specialization
        static short doNativeMemory(NativeMemoryStorage storage, int offset) {
            return UNSAFE.getShort(storage.pointer + offset);
        }

        @Fallback
        static short doOther(Storage storage, int offset,
                        @Cached ReadBytesNode read) {
            byte[] tmp = new byte[Short.BYTES];
            read.execute(tmp, 0, storage, offset, tmp.length);
            return ARRAY_ACCESSOR.getShort(tmp, 0);
        }
    }

    @GenerateUncached
    public abstract static class ReadIntNode extends Node {
        public final int execute(PtrValue ptr) {
            return execute(ptr.ptr, ptr.offset);
        }

        protected abstract int execute(Storage storage, int offset);

        @Specialization
        static int doBytes(ByteArrayStorage storage, int offset) {
            return ARRAY_ACCESSOR.getInt(storage.value, offset);
        }

        @Specialization
        static int doNativeMemory(NativeMemoryStorage storage, int offset) {
            return UNSAFE.getInt(storage.pointer + offset);
        }

        @Fallback
        static int doOther(Storage storage, int offset,
                        @Cached ReadBytesNode read) {
            byte[] tmp = new byte[Integer.BYTES];
            read.execute(tmp, 0, storage, offset, tmp.length);
            return ARRAY_ACCESSOR.getInt(tmp, 0);
        }
    }

    @GenerateUncached
    public abstract static class ReadLongNode extends Node {
        public final long execute(PtrValue ptr) {
            return execute(ptr.ptr, ptr.offset);
        }

        protected abstract long execute(Storage storage, int offset);

        @Specialization
        static long doBytes(ByteArrayStorage storage, int offset) {
            return ARRAY_ACCESSOR.getLong(storage.value, offset);
        }

        @Specialization
        static long doNativeMemory(NativeMemoryStorage storage, int offset) {
            return UNSAFE.getLong(storage.pointer + offset);
        }

        @Specialization(limit = "1")
        static long doNativePointer(NativePointerStorage storage, int offset,
                        @CachedLibrary("storage.value") InteropLibrary ilib) {
            if (offset != 0) {
                throw CompilerDirectives.shouldNotReachHere("Invalid offset for a pointer");
            }
            try {
                return ilib.asPointer(storage.value);
            } catch (UnsupportedMessageException e) {
                throw CompilerDirectives.shouldNotReachHere(e);
            }
        }

        @Fallback
        static long doOther(Storage storage, int offset,
                        @Cached ReadBytesNode read) {
            byte[] tmp = new byte[Long.BYTES];
            read.execute(tmp, 0, storage, offset, tmp.length);
            return ARRAY_ACCESSOR.getLong(tmp, 0);
        }
    }

    @GenerateUncached
    public abstract static class WriteBytesNode extends Node {
        public final void execute(PtrValue dst, byte[] src) {
            execute(dst, src, 0, src.length);
        }

        public final void execute(PtrValue dst, long value) {
            byte[] tmp = new byte[8];
            ARRAY_ACCESSOR.putLong(tmp, 0, value);
            execute(dst, tmp);
        }

        public final void execute(PtrValue dst, byte[] src, int srcOffset, int size) {
            execute(dst.ptr, dst.offset, src, srcOffset, size);
        }

        protected abstract void execute(Storage dst, int dstOffset, byte[] src, int srcOffset, int size);

        @Specialization
        static void doBytes(ByteArrayStorage dst, int dstOffset, byte[] src, int srcOffset, int size) {
            PythonUtils.arraycopy(src, srcOffset, dst.value, dstOffset, size);
        }

        @Specialization(limit = "1")
        static void doMemoryView(MemoryViewStorage dst, int dstOffset, byte[] src, int srcOffset, int size,
                        @CachedLibrary("dst.value") PythonBufferAccessLibrary bufferLib) {
            bufferLib.writeFromByteArray(dst.value, dstOffset, src, srcOffset, size);
        }

        @Specialization
        static void doNativeMemory(NativeMemoryStorage dst, int dstOffset, byte[] src, int srcOffset, int size) {
            UNSAFE.copyMemory(src, byteArrayOffset(srcOffset), null, dst.pointer + dstOffset, size);
        }

        @Specialization
        static void doPointerArray(PointerArrayStorage dst, int dstOffset, byte[] src, int srcOffset, int size,
                        @Bind("this") Node inliningTarget,
                        @Cached PointerArrayToBytesNode toBytesNode) {
            toBytesNode.execute(inliningTarget, dst);
            PythonUtils.arraycopy(src, srcOffset, dst.nativePointerBytes, dstOffset, size);
        }
    }

    @GenerateUncached
    public abstract static class WriteByteNode extends Node {
        public final void execute(PtrValue dst, byte value) {
            execute(dst.ptr, dst.offset, value);
        }

        protected abstract void execute(Storage dst, int dstOffset, byte value);

        @Specialization
        static void doBytes(ByteArrayStorage dst, int dstOffset, byte value) {
            dst.value[dstOffset] = value;
        }

        @Specialization
        static void doNativeMemory(NativeMemoryStorage dst, int dstOffset, byte value) {
            UNSAFE.putByte(dst.pointer + dstOffset, value);
        }

        @Fallback
        static void doOther(Storage dst, int dstOffset, byte value,
                        @Cached WriteBytesNode writeBytesNode) {
            writeBytesNode.execute(dst, dstOffset, new byte[]{value}, 0, Byte.BYTES);
        }
    }

    @GenerateUncached
    public abstract static class WriteShortNode extends Node {
        public final void execute(PtrValue dst, short value) {
            execute(dst.ptr, dst.offset, value);
        }

        protected abstract void execute(Storage dst, int dstOffset, short value);

        @Specialization
        static void doBytes(ByteArrayStorage dst, int dstOffset, short value) {
            ARRAY_ACCESSOR.putShort(dst.value, dstOffset, value);
        }

        @Specialization
        static void doNativeMemory(NativeMemoryStorage dst, int dstOffset, short value) {
            UNSAFE.putShort(dst.pointer + dstOffset, value);
        }

        @Fallback
        static void doOther(Storage dst, int dstOffset, short value,
                        @Cached WriteBytesNode writeBytesNode) {
            byte[] tmp = new byte[Short.BYTES];
            ARRAY_ACCESSOR.putShort(tmp, 0, value);
            writeBytesNode.execute(dst, dstOffset, tmp, 0, tmp.length);
        }
    }

    @GenerateUncached
    public abstract static class WriteIntNode extends Node {
        public final void execute(PtrValue dst, int value) {
            execute(dst.ptr, dst.offset, value);
        }

        protected abstract void execute(Storage dst, int dstOffset, int value);

        @Specialization
        static void doBytes(ByteArrayStorage dst, int dstOffset, int value) {
            ARRAY_ACCESSOR.putInt(dst.value, dstOffset, value);
        }

        @Specialization
        static void doNativeMemory(NativeMemoryStorage dst, int dstOffset, int value) {
            UNSAFE.putInt(dst.pointer + dstOffset, value);
        }

        @Fallback
        static void doOther(Storage dst, int dstOffset, int value,
                        @Cached WriteBytesNode writeBytesNode) {
            byte[] tmp = new byte[Integer.BYTES];
            ARRAY_ACCESSOR.putInt(tmp, 0, value);
            writeBytesNode.execute(dst, dstOffset, tmp, 0, tmp.length);
        }
    }

    @GenerateUncached
    public abstract static class WriteLongNode extends Node {
        public final void execute(PtrValue dst, long value) {
            execute(dst.ptr, dst.offset, value);
        }

        protected abstract void execute(Storage dst, int dstOffset, long value);

        @Specialization
        static void doBytes(ByteArrayStorage dst, int dstOffset, long value) {
            ARRAY_ACCESSOR.putLong(dst.value, dstOffset, value);
        }

        @Specialization
        static void doNativeMemory(NativeMemoryStorage dst, int dstOffset, long value) {
            UNSAFE.putLong(dst.pointer + dstOffset, value);
        }

        @Fallback
        static void doOther(Storage dst, int dstOffset, long value,
                        @Cached WriteBytesNode writeBytesNode) {
            byte[] tmp = new byte[Long.BYTES];
            ARRAY_ACCESSOR.putLong(tmp, 0, value);
            writeBytesNode.execute(dst, dstOffset, tmp, 0, tmp.length);
        }
    }

    @GenerateUncached
    @ImportStatic(PtrNodes.class)
    public abstract static class MemcpyNode extends Node {
        public final void execute(PtrValue dst, PtrValue src, int size) {
            execute(dst.ptr, dst.offset, src.ptr, src.offset, size);
        }

        protected abstract void execute(Storage dst, int dstOffset, Storage src, int srcOffset, int size);

        @Specialization
        static void doBytesBytes(ByteArrayStorage dst, int dstOffset, ByteArrayStorage src, int srcOffset, int size) {
            PythonUtils.arraycopy(src.value, srcOffset, dst.value, dstOffset, size);
        }

        @Specialization(guards = "isMultipleOf8(size)")
        static void doPointerPointer(PointerArrayStorage dst, int dstOffset, PointerArrayStorage src, int srcOffset, int size,
                        @Bind("this") Node inliningTarget,
                        @Cached ReadPointerFromPointerArrayNode readPointerFromPointerArrayNode,
                        @Cached WritePointerToPointerArrayNode writePointerToPointerArrayNode) {
            for (int i = 0; i < size; i += 8) {
                PtrValue value = readPointerFromPointerArrayNode.execute(inliningTarget, src, srcOffset + i);
                writePointerToPointerArrayNode.execute(inliningTarget, dst, dstOffset + i, value);
            }
        }

        @Fallback
        static void doOther(Storage dst, int dstOffset, Storage src, int srcOffset, int size,
                        @Cached ReadBytesNode readBytesNode,
                        @Cached WriteBytesNode writeBytesNode) {
            byte[] tmp = new byte[size];
            readBytesNode.execute(tmp, 0, src, srcOffset, size);
            writeBytesNode.execute(dst, dstOffset, tmp, 0, size);
        }
    }

    @GenerateUncached
    public abstract static class StrLenNode extends Node {
        public final int execute(PtrValue ptr) {
            return execute(ptr, -1);
        }

        public final int execute(PtrValue ptr, int max) {
            return execute(ptr.ptr, ptr.offset, max);
        }

        protected abstract int execute(Storage storage, int offset, int max);

        @Specialization
        static int doOther(Storage storage, int offset, int max,
                        @Cached ReadByteNode readByteNode) {
            int maxlen = Integer.MAX_VALUE;
            if (max >= 0) {
                maxlen = offset + max;
            }
            for (int i = offset; i < maxlen; i++) {
                if (readByteNode.execute(storage, i) == '\0') {
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
    @ImportStatic(PtrNodes.class)
    abstract static class ReadPointerFromPointerArrayNode extends Node {
        abstract PtrValue execute(Node inliningTarget, PointerArrayStorage storage, int offset);

        @Specialization(guards = {"storage.objects != null", "isMultipleOf8(offset)"})
        static PtrValue read(PointerArrayStorage storage, int offset) {
            return storage.objects[offset / 8];
        }

        @Fallback
        static PtrValue read(Node inliningTarget, PointerArrayStorage storage, int offset,
                        @Cached PointerArrayToBytesNode toBytesNode) {
            toBytesNode.execute(inliningTarget, storage);
            long nativePointer = ARRAY_ACCESSOR.getLong(storage.nativePointerBytes, offset);
            return PtrValue.nativeMemory(nativePointer);
        }
    }

    @GenerateUncached
    @GenerateInline
    @GenerateCached(false)
    @ImportStatic(PtrNodes.class)
    abstract static class WritePointerToPointerArrayNode extends Node {
        abstract void execute(Node inliningTarget, PointerArrayStorage storage, int offset, PtrValue value);

        @Specialization(guards = {"storage.objects != null", "isMultipleOf8(offset)"})
        static void write(PointerArrayStorage storage, int offset, PtrValue value) {
            storage.objects[offset / 8] = value;
        }

        @Fallback
        static void write(Node inliningTarget, PointerArrayStorage storage, int offset, PtrValue value,
                        @Cached StorageToNativeNode toNativeNode,
                        @Cached PointerArrayToBytesNode toBytesNode) {
            toBytesNode.execute(inliningTarget, storage);
            long nativePointer = toNativeNode.execute(value);
            ARRAY_ACCESSOR.putLong(storage.nativePointerBytes, offset, nativePointer);
        }
    }

    @GenerateUncached
    public abstract static class WCsLenNode extends Node {
        public final int execute(PtrValue ptr) {
            return execute(ptr, -1);
        }

        public final int execute(PtrValue ptr, int max) {
            return execute(ptr.ptr, ptr.offset, max);
        }

        protected abstract int execute(Storage storage, int offset, int max);

        @Specialization
        static int doOther(Storage storage, int offset, int max,
                        @Cached ReadByteNode readByteNode) {
            int maxlen = Integer.MAX_VALUE;
            if (max >= 0) {
                maxlen = offset + max * WCHAR_T_SIZE;
            }
            outer: for (int i = offset; i < maxlen; i += WCHAR_T_SIZE) {
                for (int j = 0; j < WCHAR_T_SIZE; j++) {
                    if (readByteNode.execute(storage, i + j) != '\0') {
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
    public abstract static class GetPointerValue extends Node {
        public final Object execute(PtrValue ptr) {
            return execute(ptr.ptr, ptr.offset);
        }

        protected abstract Object execute(Storage storage, int offset);

        @Specialization
        static long doBytes(ByteArrayStorage storage, int offset) {
            return ARRAY_ACCESSOR.getLong(storage.value, offset);
        }

        @Specialization
        static Object doNativePointer(NativePointerStorage storage, int offset) {
            if (offset != 0) {
                throw CompilerDirectives.shouldNotReachHere("Invalid offset for NativePointerStorage");
            }
            return storage.value;
        }

        // FIXME this doesn't make sense, one of the specializations is wrong
        @Specialization
        static Object doNativePointer(NativeMemoryStorage storage, int offset) {
            if (offset != 0) {
                throw CompilerDirectives.shouldNotReachHere("Invalid offset for NativePointerStorage");
            }
            return storage.pointer;
        }

        // TODO memoryview
    }

    @GenerateUncached
    public abstract static class SetPointerValue extends Node {
        public final void execute(PtrValue ptr, Object value) {
            execute(ptr.ptr, ptr.offset, value);
        }

        protected abstract void execute(Storage storage, int offset, Object value);

        @Specialization
        static void doNativePointer(NativePointerStorage storage, int offset, Object value) {
            if (offset != 0) {
                throw CompilerDirectives.shouldNotReachHere("Invalid offset for NativePointerStorage");
            }
            storage.value = value;
        }

        // TODO memoryview
    }

    @GenerateUncached
    public abstract static class ReadPointerNode extends Node {
        public final PtrValue execute(PtrValue ptr) {
            return execute(ptr.ptr, ptr.offset);
        }

        protected abstract PtrValue execute(Storage storage, int offset);

        @Specialization
        static PtrValue doPointerArray(PointerArrayStorage storage, int offset,
                        @Bind("this") Node inliningTarget,
                        @Cached ReadPointerFromPointerArrayNode readPointerFromPointerArrayNode) {
            return readPointerFromPointerArrayNode.execute(inliningTarget, storage, offset);
        }

        @Fallback
        static PtrValue doOther(Storage storage, int offset,
                        @Cached ReadLongNode readLongNode) {
            return PtrValue.nativeMemory(readLongNode.execute(storage, offset));
        }
    }

    @GenerateUncached
    public abstract static class ReadPythonObject extends Node {
        public final Object execute(PtrValue ptr) {
            return execute(ptr.ptr, ptr.offset);
        }

        protected abstract Object execute(Storage storage, int offset);

        @Specialization(guards = "offset == 0")
        static Object doPythonObject(PythonObjectStorage storage, @SuppressWarnings("unused") int offset) {
            return storage.pythonObject;
        }
    }

    @GenerateUncached
    public abstract static class WritePointerNode extends Node {
        public final void execute(PtrValue ptr, PtrValue value) {
            execute(ptr.ptr, ptr.offset, value);
        }

        protected abstract void execute(Storage storage, int offset, PtrValue value);

        @Specialization
        static void doPointerArray(PointerArrayStorage storage, int offset, PtrValue value,
                        @Bind("this") Node inliningTarget,
                        @Cached WritePointerToPointerArrayNode writePointerToPointerArrayNode) {
            writePointerToPointerArrayNode.execute(inliningTarget, storage, offset, value);
        }

        @Fallback
        static void doOther(Storage storage, int offset, PtrValue value,
                        @Cached WriteLongNode writeLongNode,
                        @Cached StorageToNativeNode toNativeNode) {
            long nativePointer = toNativeNode.execute(value.ptr, value.offset);
            writeLongNode.execute(storage, offset, nativePointer);
        }
    }

    public abstract static class ConvertToParameter extends Node {
        public final Object execute(PtrValue ptr, FFIType ffiType) {
            return execute(ptr.ptr, ptr.offset, ffiType);
        }

        protected abstract Object execute(Storage storage, int offset, FFIType ffiType);

        @Specialization
        static Object doBytes(ByteArrayStorage storage, int offset, FFIType ffiType) {
            if (ffiType.type.isArray() && ffiType != FFIType.ffi_type_pointer) {
                return storage.value;
            }
            return switch (ffiType.size) {
                case 1 -> storage.value[offset];
                case 2 -> ARRAY_ACCESSOR.getShort(storage.value, offset);
                case 4 -> ARRAY_ACCESSOR.getInt(storage.value, offset);
                case 8 -> ARRAY_ACCESSOR.getLong(storage.value, offset);
                default -> throw CompilerDirectives.shouldNotReachHere("Unexpected type size");
            };
        }

        @Specialization
        static Object doNativePointer(NativePointerStorage storage, int offset, FFIType ffiType) {
            assert ffiType.type.isArray() || ffiType == FFIType.ffi_type_pointer;
            if (offset != 0) {
                throw CompilerDirectives.shouldNotReachHere("Invalid offset for a pointer");
            }
            return storage.value;
        }

        @Specialization
        static Object doPointerArray(PointerArrayStorage storage, int offset, FFIType ffiType,
                        @Bind("this") Node inliningTarget,
                        @Cached ReadPointerNode readPointerNode,
                        @Cached PointerArrayToBytesNode toBytesNode,
                        @CachedLibrary(limit = "1") PythonBufferAccessLibrary bufferLib) {
            assert ffiType.type.isArray() || ffiType == FFIType.ffi_type_pointer;
            PtrValue pointer = readPointerNode.execute(storage, offset);
            if (pointer.isNil()) {
                return 0L;
            } else if (pointer.ptr instanceof ByteArrayStorage derefedStorage && pointer.offset == 0) {
                /*
                 * We can pass it as a byte array and NFI will convert it to a pointer to the array.
                 * Note we change all array/pointer FFI types into [UINT_8] later before passing to
                 * NFI.
                 */
                return derefedStorage.value;
            } else if (pointer.ptr instanceof PointerArrayStorage derefedStorage && pointer.offset == 0) {
                toBytesNode.execute(inliningTarget, derefedStorage);
                return derefedStorage.nativePointerBytes;
            } else if (pointer.ptr instanceof NativeMemoryStorage derefedStorage) {
                return derefedStorage.pointer;
            } else if (pointer.ptr instanceof MemoryViewStorage derefedStorage) {
                PMemoryView mv = derefedStorage.value;
                if (bufferLib.hasInternalByteArray(mv)) {
                    return bufferLib.getInternalByteArray(mv);
                } else if (mv.getBufferPointer() != null && mv.getOffset() == 0) {
                    return mv.getBufferPointer();
                }
            }
            throw CompilerDirectives.shouldNotReachHere("Not implemented");
        }

        // TODO memoryview
    }

    @GenerateUncached
    @GenerateInline
    @GenerateCached(false)
    abstract static class PointerArrayToBytesNode extends Node {
        abstract void execute(Node inliningTarget, PointerArrayStorage storage);

        @Specialization(guards = "storage.nativePointerBytes != null")
        static void nop(@SuppressWarnings("unused") PointerArrayStorage storage) {
        }

        @Specialization(guards = "storage.nativePointerBytes == null")
        static void convert(PointerArrayStorage storage,
                        @Cached StorageToNativeNode toNativeNode) {
            byte[] bytes = new byte[storage.objects.length * 8];
            for (int i = 0; i < storage.objects.length; i++) {
                PtrValue itemPointer = storage.objects[i];
                long pointer = toNativeNode.execute(itemPointer.ptr, itemPointer.offset);
                ARRAY_ACCESSOR.putLong(bytes, i * 8, pointer);
            }
            storage.nativePointerBytes = bytes;
            storage.objects = null;
        }
    }

    @GenerateUncached
    abstract static class StorageToNativeNode extends Node {

        final long execute(PtrValue ptr) {
            return execute(ptr.ptr, ptr.offset);
        }

        abstract long execute(Storage storage, int offset);

        @Specialization
        @SuppressWarnings("unused")
        static long doNull(NullStorage storage, int offset) {
            return 0L;
        }

        @Specialization
        @SuppressWarnings("unused")
        static long doNative(NativeMemoryStorage storage, int offset) {
            return storage.pointer + offset;
        }

        @Specialization
        static long doBytes(ByteArrayStorage storage, int offset) {
            int len = storage.value.length - offset;
            // TODO check permissions
            long pointer = UNSAFE.allocateMemory(len);
            UNSAFE.copyMemory(storage.value, byteArrayOffset(offset), null, pointer, len);
            return pointer;
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
