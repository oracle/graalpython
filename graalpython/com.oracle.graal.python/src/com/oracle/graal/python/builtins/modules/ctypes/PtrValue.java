/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.builtins.modules.ctypes;

import java.util.Arrays;

import com.oracle.graal.python.builtins.modules.ctypes.FFIType.FFI_TYPES;
import com.oracle.graal.python.builtins.objects.buffer.PythonBufferAccessLibrary;
import com.oracle.graal.python.builtins.objects.memoryview.PMemoryView;
import com.oracle.graal.python.builtins.objects.object.PythonObject;
import com.oracle.graal.python.builtins.objects.str.PString;
import com.oracle.graal.python.util.PythonUtils;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.TruffleLanguage.Env;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.memory.ByteArraySupport;

final class PtrValue implements TruffleObject {
    private static final ByteArraySupport SERIALIZE_LE = ByteArraySupport.littleEndian();
    private static final NullStorage NULL_STORAGE = new NullStorage();
    Storage ptr;
    int offset;

    protected PtrValue(Storage ptr, int offset) {
        this.ptr = ptr;
        this.offset = offset;
    }

    protected PtrValue() {
        this(NULL_STORAGE, 0);
    }

    protected void toBytes(byte[] bytes) {
        ptr = new ByteArrayStorage(bytes);
        offset = 0;
    }

    protected boolean isManagedBytes() {
        return ptr instanceof ByteArrayStorage;
    }

    protected boolean isNativePointer() {
        return ptr instanceof NativePointerStorage;
    }

    protected static PtrValue nil() {
        return new PtrValue(NULL_STORAGE, 0);
    }

    protected static PtrValue bytes(byte[] bytes) {
        return new PtrValue(new ByteArrayStorage(bytes), 0);
    }

    protected void toNativePointer(Object o) {
        if (o instanceof PtrValue) {
            ptr = ((PtrValue) o).ptr;
            offset = ((PtrValue) o).offset;
        } else {
            ptr = new NativePointerStorage(o);
            offset = 0;
        }
    }

    protected void toPrimitive(FFIType type, Object v) {
        assert !type.type.isArray() : "Cannot convert array to primitive";
        ptr = new PrimitiveStorage(v, type.type);
    }

    private static final int DEFAULT_ARRAY_SIZE = 16;

    protected void createStorage(FFIType type, Object value) {
        if (ptr == NULL_STORAGE) {
            ptr = createStorageInternal(type, value);
            offset = 0;
        }
    }

    private static Storage createStorageInternal(FFIType type, Object value) {
        switch (type.type) {
            case FFI_TYPE_VOID:
                return NULL_STORAGE;

            case FFI_TYPE_UINT8:
            case FFI_TYPE_SINT8:
            case FFI_TYPE_UINT16:
            case FFI_TYPE_SINT16:
            case FFI_TYPE_UINT32:
            case FFI_TYPE_SINT32:
            case FFI_TYPE_UINT64:
            case FFI_TYPE_SINT64:
            case FFI_TYPE_FLOAT:
            case FFI_TYPE_DOUBLE:
                return new PrimitiveStorage(value, type.type);

            case FFI_TYPE_STRUCT: // TODO
            case FFI_TYPE_STRING:
            case FFI_TYPE_UINT8_ARRAY:
            case FFI_TYPE_SINT8_ARRAY:
                return new ByteArrayStorage(DEFAULT_ARRAY_SIZE);

            case FFI_TYPE_UINT16_ARRAY:
            case FFI_TYPE_SINT16_ARRAY:
                return new ShortArrayStorage(DEFAULT_ARRAY_SIZE);

            case FFI_TYPE_UINT32_ARRAY:
            case FFI_TYPE_SINT32_ARRAY:
                return new IntArrayStorage(DEFAULT_ARRAY_SIZE);

            case FFI_TYPE_UINT64_ARRAY:
            case FFI_TYPE_SINT64_ARRAY:
                return new LongArrayStorage(DEFAULT_ARRAY_SIZE);

            case FFI_TYPE_FLOAT_ARRAY:
                return new FloatArrayStorage(DEFAULT_ARRAY_SIZE);

            case FFI_TYPE_DOUBLE_ARRAY:
                return new DoubleArrayStorage(DEFAULT_ARRAY_SIZE);

            case FFI_TYPE_POINTER:
                return new NativePointerStorage(value);
            default:
                throw CompilerDirectives.shouldNotReachHere("Not supported type!");

        }
    }

    protected static PtrValue create(FFIType type, Object value, int offset) {
        return new PtrValue(createStorageInternal(type, value), offset);
    }

    protected static PtrValue create(FFIType type, Object value) {
        return create(type, value, 0);
    }

    protected static PtrValue bytes(int size) {
        return new PtrValue(new ByteArrayStorage(size), 0);
    }

    protected static PtrValue memoryView(PMemoryView mv) {
        return new PtrValue(new MemoryViewStorage(mv), 0);
    }

    protected static PtrValue nativePointer(Object o) {
        return new PtrValue(new NativePointerStorage(o), 0);
    }

    protected static PtrValue ref(Storage storage) {
        return new PtrValue(storage, 0);
    }

    protected PtrValue ref(int incOffset) {
        return new PtrValue(ptr, offset + incOffset);
    }

    protected Object getNativePointer() {
        assert ptr instanceof NativePointerStorage;
        return ((NativePointerStorage) ptr).value;
    }

    protected PtrValue copy() {
        if (ptr instanceof ArrayStorage) {
            return new PtrValue(((ArrayStorage) ptr).copy(), offset);
        }
        return new PtrValue(ptr, offset);
    }

    protected static boolean isNull(PtrValue b_ptr) {
        return b_ptr == null || b_ptr.ptr == NULL_STORAGE;
    }

    /*-
    protected static boolean _CDataObject_HasExternalBuffer(CDataObject obj) {
        // ((v)->b_ptr != (char *)&(v)->b_value)
        return obj.b_ptr.ptr != obj.b_value;
    }
    */

    abstract static class Storage {
        final FFI_TYPES type;

        Storage(FFI_TYPES type) {
            this.type = type;
        }

        protected Object getValue(@SuppressWarnings("unused") int idx) {
            throw CompilerDirectives.shouldNotReachHere("Abstract Storage!");
        }

        protected Object getNativeObject(@SuppressWarnings("unused") Env env) {
            throw CompilerDirectives.shouldNotReachHere("Abstract Storage!");
        }

        protected void setValue(@SuppressWarnings("unused") Object v, @SuppressWarnings("unused") int idx) {
            throw CompilerDirectives.shouldNotReachHere("Abstract Storage!");
        }

        protected abstract Storage resize(int length);
    }

    static final class NullStorage extends Storage {

        NullStorage() {
            super(FFI_TYPES.FFI_TYPE_VOID);
        }

        @Override
        protected Object getValue(int idx) {
            throw CompilerDirectives.shouldNotReachHere("Null Storage!");
        }

        @Override
        protected void setValue(Object v, int idx) {
            throw CompilerDirectives.shouldNotReachHere("Null Storage!");
        }

        @Override
        protected Storage resize(int length) {
            throw CompilerDirectives.shouldNotReachHere("Null Storage!");
        }

        @Override
        protected Object getNativeObject(Env env) {
            return env.asGuestValue(null);
        }
    }

    static final class PrimitiveStorage extends Storage {

        Object value;

        PrimitiveStorage(Object o, FFI_TYPES type) {
            super(type);
            this.value = o;
        }

        @Override
        protected Object getValue(int idx) {
            return value;
        }

        @Override
        protected void setValue(Object v, int idx) {
            value = v;
        }

        @Override
        protected Storage resize(int length) {
            throw CompilerDirectives.shouldNotReachHere("Primitive Storage!");
        }

        @Override
        protected Object getNativeObject(Env env) {
            return value;
        }
    }

    static final class NativePointerStorage extends Storage {

        Object value;

        NativePointerStorage(Object o) {
            super(FFI_TYPES.FFI_TYPE_POINTER);
            this.value = o;
        }

        @Override
        protected Object getValue(int idx) {
            return value;
        }

        @Override
        protected void setValue(Object v, int idx) {
            value = v;
        }

        @Override
        protected Storage resize(int length) {
            throw CompilerDirectives.shouldNotReachHere("Native Pointer Storage!");
        }

        @Override
        protected Object getNativeObject(Env env) {
            return env.asGuestValue(value);
        }
    }

    abstract static class ArrayStorage extends Storage {
        ArrayStorage(FFI_TYPES type) {
            super(type);
        }

        abstract ArrayStorage copy();
    }

    static class ByteArrayStorage extends ArrayStorage {
        byte[] value;

        ByteArrayStorage(int size) {
            super(FFI_TYPES.FFI_TYPE_SINT8);
            this.value = new byte[size];
        }

        ByteArrayStorage(byte[] bytes) {
            super(FFI_TYPES.FFI_TYPE_SINT8);
            this.value = bytes;
        }

        @Override
        protected final Object getValue(int idx) {
            return value[idx];
        }

        @Override
        protected void setValue(Object v, int idx) {
            if (v instanceof Byte) {
                value[idx] = (byte) v;
                return;
            } else if (v instanceof Short) {
                SERIALIZE_LE.putShort(value, idx, (short) v);
                return;
            } else if (v instanceof Integer) {
                SERIALIZE_LE.putInt(value, idx, (int) v);
                return;
            } else if (v instanceof Long) {
                SERIALIZE_LE.putLong(value, idx, (long) v);
                return;
            } else if (v instanceof Double) {
                SERIALIZE_LE.putDouble(value, idx, (double) v);
                return;
            } else if (v instanceof Boolean) {
                value[idx] = (byte) (((boolean) v) ? 1 : 0);
                return;
            } else if (v instanceof Float) {
                SERIALIZE_LE.putFloat(value, idx, (float) v);
                return;
            } else if (v instanceof String) {
                String s = (String) v;
                if (PString.length(s) == 1) {
                    value[idx] = (byte) PString.charAt(s, 0);
                    return;
                }
            }
            throw CompilerDirectives.shouldNotReachHere("Incompatible value type for ByteArrayStorage");
        }

        @Override
        protected Object getNativeObject(Env env) {
            return env.asGuestValue(value);
        }

        @Override
        protected Storage resize(int length) {
            if (length > value.length) {
                ByteArrayStorage storage = new ByteArrayStorage(length);
                PythonUtils.arraycopy(value, 0, storage.value, 0, length);
                return storage;
            }
            return this;
        }

        protected void memcpy(int offset, byte[] src, int srcOffset, int length) {
            PythonUtils.arraycopy(src, srcOffset, value, offset, length);
        }

        protected void memcpy(int offset, byte[] src) {
            memcpy(offset, src, 0, src.length);
        }

        protected byte[] trim(int offset) {
            return trim(this, offset);
        }

        protected static byte[] trim(ByteArrayStorage storage, int offset) {
            byte[] bytes = storage.value;
            int end = bytes.length;
            for (int i = offset; i < end; i++) {
                if (bytes[i] == 0) {
                    end = i;
                    break;
                }
            }
            return PythonUtils.arrayCopyOfRange(bytes, offset, end);
        }

        @Override
        ArrayStorage copy() {
            return new ByteArrayStorage(PythonUtils.arrayCopyOf(value, value.length));
        }
    }

    static class MemoryViewStorage extends ArrayStorage {
        PMemoryView value;
        final int length;

        @TruffleBoundary
        MemoryViewStorage(PMemoryView bytes) {
            super(FFI_TYPES.FFI_TYPE_SINT8);
            this.value = bytes;
            this.length = PythonBufferAccessLibrary.getUncached().getBufferLength(bytes);
        }

        @TruffleBoundary
        @Override
        protected final Object getValue(int idx) {
            return PythonBufferAccessLibrary.getUncached().readByte(value, idx);
        }

        @TruffleBoundary
        @Override
        protected void setValue(Object v, int idx) {
            PythonBufferAccessLibrary.getUncached().writeByte(value, idx, (byte) v);
        }

        @Override
        protected Object getNativeObject(Env env) {
            if (value.getBuffer() instanceof PythonObject) {
                byte[] bytes = PythonBufferAccessLibrary.getUncached().getInternalByteArray(value.getBuffer());
                return env.asGuestValue(bytes);
            }
            return value.getBufferPointer();
        }

        @Override
        protected Storage resize(int len) {
            if (len > length) {
                byte[] bytes = PythonBufferAccessLibrary.getUncached().getInternalOrCopiedByteArray(value);
                ByteArrayStorage storage = new ByteArrayStorage(len);
                PythonUtils.arraycopy(bytes, 0, storage.value, 0, len);
                return storage;
            }
            return this;
        }

        @TruffleBoundary
        @Override
        ArrayStorage copy() {
            byte[] bytes = PythonBufferAccessLibrary.getUncached().getInternalOrCopiedByteArray(value);
            return new ByteArrayStorage(bytes);
        }
    }

    static class ShortArrayStorage extends ArrayStorage {
        short[] value;

        ShortArrayStorage(int size) {
            super(FFI_TYPES.FFI_TYPE_SINT16);
            this.value = new short[size];
        }

        @Override
        protected final Object getValue(int idx) {
            return (int) value[idx];
        }

        @Override
        protected void setValue(Object v, int idx) {
            assert v instanceof Short;
            value[idx] = (short) v;
        }

        @Override
        protected Object getNativeObject(Env env) {
            return env.asGuestValue(value);
        }

        @Override
        protected Storage resize(int length) {
            if (length > value.length) {
                ShortArrayStorage storage = new ShortArrayStorage(length);
                PythonUtils.arraycopy(value, 0, storage.value, 0, length);
                return storage;
            }
            return this;
        }

        @Override
        ArrayStorage copy() {
            ShortArrayStorage s = new ShortArrayStorage(0);
            try {
                s.value = Arrays.copyOf(value, value.length);
            } catch (Throwable t) {
                // Break exception edges
                CompilerDirectives.transferToInterpreterAndInvalidate();
                throw t;
            }
            return s;
        }
    }

    static class IntArrayStorage extends ArrayStorage {
        int[] value;

        IntArrayStorage(int size) {
            super(FFI_TYPES.FFI_TYPE_SINT32);
            this.value = new int[size];
        }

        @Override
        protected final Object getValue(int idx) {
            return value[idx];
        }

        @Override
        protected void setValue(Object v, int idx) {
            assert v instanceof Integer;
            value[idx] = (int) v;
        }

        @Override
        protected Object getNativeObject(Env env) {
            return env.asGuestValue(value);
        }

        @Override
        protected Storage resize(int length) {
            if (length > value.length) {
                IntArrayStorage storage = new IntArrayStorage(length);
                PythonUtils.arraycopy(value, 0, storage.value, 0, length);
                return storage;
            }
            return this;
        }

        @Override
        ArrayStorage copy() {
            IntArrayStorage s = new IntArrayStorage(0);
            s.value = PythonUtils.arrayCopyOf(value, value.length);
            return s;
        }
    }

    static class LongArrayStorage extends ArrayStorage {
        long[] value;

        LongArrayStorage(int size) {
            super(FFI_TYPES.FFI_TYPE_SINT64);
            this.value = new long[size];
        }

        @Override
        protected final Object getValue(int idx) {
            return value[idx];
        }

        @Override
        protected void setValue(Object v, int idx) {
            assert v instanceof Long || v instanceof Integer;
            value[idx] = (long) v;
        }

        @Override
        protected Object getNativeObject(Env env) {
            return env.asGuestValue(value);
        }

        @Override
        protected Storage resize(int length) {
            if (length > value.length) {
                LongArrayStorage storage = new LongArrayStorage(length);
                PythonUtils.arraycopy(value, 0, storage.value, 0, length);
                return storage;
            }
            return this;
        }

        @Override
        ArrayStorage copy() {
            LongArrayStorage s = new LongArrayStorage(0);
            try {
                s.value = Arrays.copyOf(value, value.length);
            } catch (Throwable t) {
                // Break exception edges
                CompilerDirectives.transferToInterpreterAndInvalidate();
                throw t;
            }
            return s;
        }
    }

    static class FloatArrayStorage extends ArrayStorage {
        float[] value;

        FloatArrayStorage(int size) {
            super(FFI_TYPES.FFI_TYPE_FLOAT);
            this.value = new float[size];
        }

        @Override
        protected final Object getValue(int idx) {
            return (double) value[idx];
        }

        @Override
        protected void setValue(Object v, int idx) {
            assert v instanceof Float;
            value[idx] = (float) v;
        }

        @Override
        protected Object getNativeObject(Env env) {
            return env.asGuestValue(value);
        }

        @Override
        protected Storage resize(int length) {
            if (length > value.length) {
                FloatArrayStorage storage = new FloatArrayStorage(length);
                PythonUtils.arraycopy(value, 0, storage.value, 0, length);
                return storage;
            }
            return this;
        }

        @TruffleBoundary
        @Override
        ArrayStorage copy() {
            FloatArrayStorage s = new FloatArrayStorage(0);
            try {
                s.value = Arrays.copyOf(value, value.length);
            } catch (Throwable t) {
                // this is really unexpected and we want to break exception edges in compiled code
                CompilerDirectives.transferToInterpreterAndInvalidate();
                throw t;
            }
            return s;
        }
    }

    static class DoubleArrayStorage extends ArrayStorage {
        double[] value;

        DoubleArrayStorage(int size) {
            super(FFI_TYPES.FFI_TYPE_DOUBLE);
            this.value = new double[size];
        }

        @Override
        protected final Object getValue(int idx) {
            return value[idx];
        }

        @Override
        protected void setValue(Object v, int idx) {
            assert v instanceof Double;
            value[idx] = (double) v;
        }

        @Override
        protected Object getNativeObject(Env env) {
            return env.asGuestValue(value);
        }

        @Override
        protected Storage resize(int length) {
            if (length > value.length) {
                DoubleArrayStorage storage = new DoubleArrayStorage(length);
                PythonUtils.arraycopy(value, 0, storage.value, 0, length);
                return storage;
            }
            return this;
        }

        @TruffleBoundary
        @Override
        ArrayStorage copy() {
            DoubleArrayStorage s = new DoubleArrayStorage(0);
            try {
                s.value = Arrays.copyOf(value, value.length);
            } catch (Throwable t) {
                // this is really unexpected and we want to break exception edges in compiled code
                CompilerDirectives.transferToInterpreterAndInvalidate();
                throw t;
            }
            return s;
        }
    }
}
