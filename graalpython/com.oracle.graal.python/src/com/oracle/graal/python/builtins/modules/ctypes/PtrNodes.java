package com.oracle.graal.python.builtins.modules.ctypes;

import static com.oracle.graal.python.util.PythonUtils.ARRAY_ACCESSOR;

import com.oracle.graal.python.builtins.modules.ctypes.PtrValue.ByteArrayStorage;
import com.oracle.graal.python.builtins.modules.ctypes.PtrValue.NativePointerStorage;
import com.oracle.graal.python.builtins.modules.ctypes.PtrValue.NullStorage;
import com.oracle.graal.python.builtins.modules.ctypes.PtrValue.Storage;
import com.oracle.graal.python.util.PythonUtils;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.nodes.Node;

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
        void doBytes(byte[] dst, int dstOffset, ByteArrayStorage src, int srcOffset, int size) {
            PythonUtils.arraycopy(src.value, srcOffset, dst, dstOffset, size);
        }

        @Specialization
        @SuppressWarnings("unused")
        void doNull(byte[] dst, int dstOffset, NullStorage src, int srcOffset, int size) {
            if (size != 0) {
                throw CompilerDirectives.shouldNotReachHere("Reading from NULL pointer");
            }
        }
    }

    @GenerateUncached
    public abstract static class ReadByteNode extends Node {
        public final byte execute(PtrValue ptr) {
            return execute(ptr.ptr, ptr.offset);
        }

        protected abstract byte execute(Storage storage, int offset);

        @Specialization
        byte doBytes(ByteArrayStorage storage, int offset) {
            return storage.value[offset];
        }

        @Fallback
        byte doOther(Storage storage, int offset,
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
        short doBytes(ByteArrayStorage storage, int offset) {
            return ARRAY_ACCESSOR.getShort(storage.value, offset);
        }

        @Fallback
        short doOther(Storage storage, int offset,
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
        int doBytes(ByteArrayStorage storage, int offset) {
            return ARRAY_ACCESSOR.getInt(storage.value, offset);
        }

        @Fallback
        int doOther(Storage storage, int offset,
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
        long doBytes(ByteArrayStorage storage, int offset) {
            return ARRAY_ACCESSOR.getLong(storage.value, offset);
        }

        @Fallback
        long doOther(Storage storage, int offset,
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
        void doBytes(ByteArrayStorage dst, int dstOffset, byte[] src, int srcOffset, int size) {
            PythonUtils.arraycopy(src, srcOffset, dst.value, dstOffset, size);
        }

        // TODO other types
    }

    @GenerateUncached
    public abstract static class WriteByteNode extends Node {
        public final void execute(PtrValue dst, byte value) {
            execute(dst.ptr, dst.offset, value);
        }

        protected abstract void execute(Storage dst, int dstOffset, byte value);

        @Specialization
        void doBytes(ByteArrayStorage dst, int dstOffset, byte value) {
            dst.value[dstOffset] = value;
        }

        @Fallback
        void doOther(Storage dst, int dstOffset, byte value,
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
        void doBytes(ByteArrayStorage dst, int dstOffset, short value) {
            ARRAY_ACCESSOR.putShort(dst.value, dstOffset, value);
        }

        @Fallback
        void doOther(Storage dst, int dstOffset, short value,
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
        void doBytes(ByteArrayStorage dst, int dstOffset, int value) {
            ARRAY_ACCESSOR.putInt(dst.value, dstOffset, value);
        }

        @Fallback
        void doOther(Storage dst, int dstOffset, int value,
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
        void doBytes(ByteArrayStorage dst, int dstOffset, long value) {
            ARRAY_ACCESSOR.putLong(dst.value, dstOffset, value);
        }

        @Fallback
        void doOther(Storage dst, int dstOffset, long value,
                        @Cached WriteBytesNode writeBytesNode) {
            byte[] tmp = new byte[Long.BYTES];
            ARRAY_ACCESSOR.putLong(tmp, 0, value);
            writeBytesNode.execute(dst, dstOffset, tmp, 0, tmp.length);
        }
    }

    @GenerateUncached
    public abstract static class MemcpyNode extends Node {
        public final void execute(PtrValue dst, PtrValue src, int size) {
            execute(dst.ptr, dst.offset, src.ptr, src.offset, size);
        }

        protected abstract void execute(Storage dst, int dstOffset, Storage src, int srcOffset, int size);

        @Specialization
        void doBytes(ByteArrayStorage dst, int dstOffset, Storage src, int srcOffset, int size,
                        @Cached ReadBytesNode readBytesNode) {
            readBytesNode.execute(dst.value, dstOffset, src, srcOffset, size);
        }

        // TODO other
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
        int doBytes(ByteArrayStorage storage, int offset, int max) {
            int maxlen = storage.value.length;
            if (max >= 0) {
                maxlen = offset + max;
            }
            for (int i = offset; i < maxlen; i++) {
                if (storage.value[i] == '\0') {
                    return i - offset;
                }
            }
            if (max >= 0) {
                return max;
            } else {
                throw CompilerDirectives.shouldNotReachHere("NULL terminator not found");
            }
        }

        // TODO other types
    }

    @GenerateUncached
    public abstract static class WCsLenNode extends Node {
        public final int execute(PtrValue ptr, int wcharSize) {
            return execute(ptr, wcharSize, -1);
        }

        public final int execute(PtrValue ptr, int wcharSize, int max) {
            return execute(ptr.ptr, ptr.offset, wcharSize, max);
        }

        protected abstract int execute(Storage storage, int offset, int wcharSize, int max);

        @Specialization
        int doBytes(ByteArrayStorage storage, int offset, int wcharSize, int max) {
            int maxlen = storage.value.length;
            if (max >= 0) {
                maxlen = offset + max * wcharSize;
            }
            outer: for (int i = offset; i < maxlen; i += wcharSize) {
                for (int j = 0; j < wcharSize; j++) {
                    if (storage.value[i + j] != '\0') {
                        continue outer;
                    }
                }
                return (i / wcharSize) - offset;
            }
            if (max >= 0) {
                return max;
            } else {
                throw CompilerDirectives.shouldNotReachHere("NULL terminator not found");
            }
        }

        // TODO other types
    }

    @GenerateUncached
    public abstract static class ReadPointerNode extends Node {
        public final Object execute(PtrValue ptr) {
            return execute(ptr.ptr, ptr.offset);
        }

        protected abstract Object execute(Storage storage, int offset);

        @Specialization
        long doBytes(ByteArrayStorage storage, int offset) {
            return ARRAY_ACCESSOR.getLong(storage.value, offset);
        }

        @Specialization
        Object doNativePointer(NativePointerStorage storage, int offset) {
            if (offset != 0) {
                throw CompilerDirectives.shouldNotReachHere("Invalid offset for NativePointerStorage");
            }
            return storage.value;
        }

        // TODO more types
    }

    public abstract static class ConvertToNFIParameter extends Node {
        public final Object execute(PtrValue ptr, FFIType ffiType) {
            return execute(ptr.ptr, ptr.offset, ffiType);
        }

        protected abstract Object execute(Storage storage, int offset, FFIType ffiType);

        @Specialization
        Object doBytes(ByteArrayStorage storage, int offset, FFIType ffiType) {
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
        Object doNativePointer(NativePointerStorage storage, int offset, FFIType ffiType) {
            assert ffiType.type.isArray() || ffiType == FFIType.ffi_type_pointer;
            if (offset != 0) {
                throw CompilerDirectives.shouldNotReachHere("Invalid offset for NativePointerStorage");
            }
            return storage.value;
        }

        // TODO more types
    }
}
