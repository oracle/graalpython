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

import com.oracle.graal.python.builtins.objects.buffer.PythonBufferAccessLibrary;
import com.oracle.graal.python.builtins.objects.memoryview.PMemoryView;
import com.oracle.graal.python.builtins.objects.object.PythonObject;
import com.oracle.graal.python.runtime.NFICtypesSupport;
import com.oracle.graal.python.runtime.NativeLibrary.InvokeNativeFunction;
import com.oracle.graal.python.util.PythonUtils;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.memory.ByteArraySupport;

final class PtrValue {
    private static final ByteArraySupport SERIALIZE = ByteArraySupport.littleEndian();
    private static final EmptyStorage EMPTY_STORAGE = new EmptyStorage();
    Storage ptr; // can be guest value
    int offset;

    protected PtrValue(Storage ptr, int offset) {
        this.ptr = ptr;
        this.offset = offset;
    }

    protected PtrValue() {
        this(new EmptyStorage(), 0);
    }

    protected static PtrValue empty() {
        return new PtrValue(EMPTY_STORAGE, 0);
    }

    protected static PtrValue bytes(byte[] bytes) {
        return new PtrValue(new ByteArrayStorage(bytes), 0);
    }

    protected static PtrValue bytes(int size) {
        return new PtrValue(new ByteArrayStorage(size), 0);
    }

    protected static PtrValue nativeBytes(int size) {
        return new PtrValue(new NativeByteArrayStorage(size), 0);
    }

    protected static PtrValue memoryView(PMemoryView mv) {
        return new PtrValue(new MemoryViewStorage(mv), 0);
    }

    protected static PtrValue object(Object o) {
        return new PtrValue(new ObjectStorage(o), 0);
    }

    protected static PtrValue object(Object o, int offset) { // TODO
        return new PtrValue(new ObjectStorage(o), offset);
    }

    protected static PtrValue ref(Storage storage) {
        return new PtrValue(storage, 0);
    }

    protected PtrValue ref(int incOffset) {
        return new PtrValue(ptr, offset + incOffset);
    }

    protected Object getObject() {
        assert ptr instanceof ObjectStorage;
        return ((ObjectStorage) ptr).value;
    }

    protected PtrValue copy() {
        if (ptr instanceof ArrayStorage) {
            return new PtrValue(((ArrayStorage) ptr).copy(), offset);
        }
        return new PtrValue(ptr, offset);
    }

    protected static boolean isNull(PtrValue b_ptr) {
        return b_ptr == null || b_ptr.ptr instanceof EmptyStorage;
    }

    /*-
    protected static boolean _CDataObject_HasExternalBuffer(CDataObject obj) {
        // ((v)->b_ptr != (char *)&(v)->b_value)
        return obj.b_ptr.ptr != obj.b_value;
    }
    */

    public enum StorageType {
        UNINITIALIZED(0),
        INT8(Byte.BYTES),
        INT16(Short.BYTES),
        INT32(Integer.BYTES),
        INT64(Long.BYTES),
        FLOAT(Float.BYTES),
        DOUBLE(Double.BYTES),
        POINTER(Long.BYTES),
        STRING(Character.BYTES),
        OBJECT(Long.BYTES);

        final short size;

        StorageType(int size) {
            this.size = (short) size;
        }
    }

    protected void specialize(int typeSize, int length, Object value) {
        if (typeSize == ptr.type.size) {
            if (length > 1 && ptr instanceof ArrayStorage) {
                return;
            }
            if (length == 1 && !(ptr instanceof ArrayStorage)) {
                return;
            }
        }
        switch (typeSize) {
            case Byte.BYTES:
                if (length > 1) {
                    if (ptr instanceof EmptyStorage) {
                        ptr = new ByteArrayStorage(length);
                    } else {
                        ptr = ptr.resize(length);
                    }
                } else {
                    ptr = new ByteStorage((byte) 0);
                }
                break;
            case Short.BYTES:
                if (length > 1) {
                    if (ptr instanceof EmptyStorage) {
                        ptr = new ShortArrayStorage(length);
                    } else {
                        ptr = ptr.resize(length);
                    }
                } else {
                    ptr = new ShortStorage((byte) 0);
                }
                break;
            case Integer.BYTES:
                if (value instanceof Double || value instanceof Float) {
                    if (length > 1) {
                        if (ptr instanceof EmptyStorage) {
                            ptr = new FloatArrayStorage(length);
                        } else {
                            ptr = ptr.resize(length);
                        }
                    } else {
                        ptr = new FloatStorage((byte) 0);
                    }

                } else {
                    if (length > 1) {
                        if (ptr instanceof EmptyStorage) {
                            ptr = new IntArrayStorage(length);
                        } else {
                            ptr = ptr.resize(length);
                        }
                    } else {
                        ptr = new IntStorage((byte) 0);
                    }
                }
                break;
            case Long.BYTES:
                if (value instanceof Integer || value instanceof Long) {
                    if (length > 1) {
                        if (ptr instanceof EmptyStorage) {
                            ptr = new LongArrayStorage(length);
                        } else {
                            ptr = ptr.resize(length);
                        }
                    } else {
                        ptr = new LongStorage((byte) 0);
                    }
                } else if (value instanceof Double) {
                    if (length > 1) {
                        if (ptr instanceof EmptyStorage) {
                            ptr = new DoubleArrayStorage(length);
                        } else {
                            ptr = ptr.resize(length);
                        }
                    } else {
                        ptr = new DoubleStorage((byte) 0);
                    }
                } else {
                    if (length > 1) {
                        if (ptr instanceof EmptyStorage) {
                            ptr = new ObjectArrayStorage(length);
                        } else {
                            ptr = ptr.resize(length);
                        }
                    } else {
                        ptr = new ObjectStorage(value);
                    }
                }
                break;
        }
    }

    abstract static class Storage {
        final StorageType type;

        Storage(StorageType type) {
            this.type = type;
        }

        protected Object getValue(@SuppressWarnings("unused") int idx) {
            throw CompilerDirectives.shouldNotReachHere("Abstract Storage!");
        }

        protected Object getNativeObject(@SuppressWarnings("unused") NFICtypesSupport nfiCtypesSupport) {
            throw CompilerDirectives.shouldNotReachHere("Abstract Storage!");
        }

        protected void setValue(@SuppressWarnings("unused") Object v, @SuppressWarnings("unused") int idx) {
            throw CompilerDirectives.shouldNotReachHere("Abstract Storage!");
        }

        protected abstract Storage resize(int length);
    }

    static final class EmptyStorage extends Storage {
        EmptyStorage() {
            super(StorageType.UNINITIALIZED);
        }

        @Override
        protected Object getValue(int idx) {
            throw CompilerDirectives.shouldNotReachHere("Empty Pointer Storage! Specialize first");
        }

        @Override
        protected Object getNativeObject(NFICtypesSupport nfiCtypesSupport) {
            throw CompilerDirectives.shouldNotReachHere("Empty Pointer Storage! Specialize first");
        }

        @Override
        protected void setValue(Object v, int idx) {
            throw CompilerDirectives.shouldNotReachHere("Empty Pointer Storage! Specialize first");
        }

        @Override
        protected Storage resize(int length) {
            throw CompilerDirectives.shouldNotReachHere("Empty Pointer Storage!");
        }
    }

    static class ObjectStorage extends Storage {
        Object value;

        ObjectStorage(Object value) {
            super(StorageType.OBJECT);
            this.value = value;
        }

        @Override
        protected final Object getValue(int idx) {
            assert idx == 0;
            return value;
        }

        @Override
        protected void setValue(Object v, int idx) {
            assert idx == 0;
            value = v;
        }

        @Override
        protected Object getNativeObject(NFICtypesSupport nfiCtypesSupport) {
            return nfiCtypesSupport.getContext().getEnv().asGuestValue(value);
        }

        @Override
        protected Storage resize(int length) {
            if (length > 1) {
                ObjectArrayStorage storage = new ObjectArrayStorage(length);
                storage.value[0] = value;
                return storage;
            }
            return this;
        }
    }

    static class ByteStorage extends Storage {
        byte value;

        ByteStorage(byte value) {
            super(StorageType.INT8);
            this.value = value;
        }

        @Override
        protected final Object getValue(int idx) {
            assert idx == 0;
            return value;
        }

        @Override
        protected void setValue(Object v, int idx) {
            assert idx == 0;
            assert v instanceof Byte;
            value = (byte) v;
        }

        @Override
        protected Object getNativeObject(NFICtypesSupport nfiCtypesSupport) {
            return value;
        }

        @Override
        protected Storage resize(int length) {
            if (length > 1) {
                ByteArrayStorage storage = new ByteArrayStorage(length);
                storage.value[0] = value;
                return storage;
            }
            return this;
        }
    }

    static class ShortStorage extends Storage {
        short value;

        ShortStorage(short value) {
            super(StorageType.INT16);
            this.value = value;
        }

        @Override
        protected final Object getValue(int idx) {
            assert idx == 0;
            return value;
        }

        @Override
        protected void setValue(Object v, int idx) {
            assert idx == 0;
            assert v instanceof Short;
            value = (short) v;
        }

        @Override
        protected Object getNativeObject(NFICtypesSupport nfiCtypesSupport) {
            return value;
        }

        @Override
        protected Storage resize(int length) {
            if (length > 1) {
                ShortArrayStorage storage = new ShortArrayStorage(length);
                storage.value[0] = value;
                return storage;
            }
            return this;
        }
    }

    static class IntStorage extends Storage {
        int value;

        IntStorage(int value) {
            super(StorageType.INT32);
            this.value = value;
        }

        @Override
        protected final Object getValue(int idx) {
            return value;
        }

        @Override
        protected void setValue(Object v, int idx) {
            assert idx == 0;
            assert v instanceof Integer;
            value = (int) v;
        }

        @Override
        protected Object getNativeObject(NFICtypesSupport nfiCtypesSupport) {
            return value;
        }

        @Override
        protected Storage resize(int length) {
            if (length > 1) {
                IntArrayStorage storage = new IntArrayStorage(length);
                storage.value[0] = value;
                return storage;
            }
            return this;
        }
    }

    static class LongStorage extends Storage {
        long value;

        LongStorage(long value) {
            super(StorageType.INT64);
            this.value = value;
        }

        @Override
        protected final Object getValue(int idx) {
            assert idx == 0;
            return value;
        }

        @Override
        protected void setValue(Object v, int idx) {
            assert idx == 0;
            assert v instanceof Long;
            value = (long) v;
        }

        @Override
        protected Object getNativeObject(NFICtypesSupport nfiCtypesSupport) {
            return value;
        }

        @Override
        protected Storage resize(int length) {
            if (length > 1) {
                FloatArrayStorage storage = new FloatArrayStorage(length);
                storage.value[0] = value;
                return storage;
            }
            return this;
        }
    }

    static class FloatStorage extends Storage {
        float value;

        FloatStorage(float value) {
            super(StorageType.FLOAT);
            this.value = value;
        }

        @Override
        protected final Object getValue(int idx) {
            assert idx == 0;
            return value;
        }

        @Override
        protected void setValue(Object v, int idx) {
            assert idx == 0;
            assert v instanceof Float;
            value = (float) v;
        }

        @Override
        protected Object getNativeObject(NFICtypesSupport nfiCtypesSupport) {
            return value;
        }

        @Override
        protected Storage resize(int length) {
            if (length > 1) {
                FloatArrayStorage storage = new FloatArrayStorage(length);
                storage.value[0] = value;
                return storage;
            }
            return this;
        }
    }

    static class DoubleStorage extends Storage {
        double value;

        DoubleStorage(double value) {
            super(StorageType.DOUBLE);
            this.value = value;
        }

        @Override
        protected final Object getValue(int idx) {
            assert idx == 0;
            return value;
        }

        @Override
        protected void setValue(Object v, int idx) {
            assert idx == 0;
            assert v instanceof Double;
            value = (double) v;
        }

        @Override
        protected Object getNativeObject(NFICtypesSupport nfiCtypesSupport) {
            return value;
        }

        @Override
        protected Storage resize(int length) {
            if (length > 1) {
                DoubleArrayStorage storage = new DoubleArrayStorage(length);
                storage.value[0] = value;
                return storage;
            }
            return this;
        }
    }

    abstract static class ArrayStorage extends Storage {
        ArrayStorage(StorageType type) {
            super(type);
        }

        abstract ArrayStorage copy();
    }

    static class ByteArrayStorage extends ArrayStorage {
        byte[] value;

        ByteArrayStorage(int size) {
            super(StorageType.INT8);
            this.value = new byte[size];
        }

        ByteArrayStorage(byte[] bytes) {
            super(StorageType.INT8);
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
            } else if (v instanceof Integer) {
                SERIALIZE.putInt(value, idx, (int) v);
                return;
            } else if (v instanceof Long) {
                SERIALIZE.putLong(value, idx, (long) v);
                return;
            } else if (v instanceof Double) {
                SERIALIZE.putDouble(value, idx, (double) v);
                return;
            } else if (v instanceof Float) {
                SERIALIZE.putFloat(value, idx, (float) v);
                return;
            }
            throw CompilerDirectives.shouldNotReachHere("Incompatible value type for ByteArrayStorage");
        }

        @Override
        protected Object getNativeObject(NFICtypesSupport nfiCtypesSupport) {
            return nfiCtypesSupport.getContext().getEnv().asGuestValue(value);
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

        @Override
        ArrayStorage copy() {
            return new ByteArrayStorage(PythonUtils.arrayCopyOf(value, value.length));
        }
    }

    static class NativeByteArrayStorage extends ArrayStorage {
        byte[] value;
        Object guest;

        NativeByteArrayStorage(int size) {
            super(StorageType.INT8);
            this.value = new byte[size];
        }

        NativeByteArrayStorage(byte[] bytes) {
            super(StorageType.INT8);
            this.value = bytes;
        }

        @Override
        protected final Object getValue(int idx) {
            return value[idx];
        }

        @Override
        protected void setValue(Object v, int idx) {
            assert guest == null; // TODO: free native memory
            assert v instanceof Byte;
            value[idx] = (byte) v;
        }

        @Override
        protected Object getNativeObject(NFICtypesSupport nfiCtypesSupport) {
            if (guest == null) {
                Object g = nfiCtypesSupport.getContext().getEnv().asGuestValue(value);
                guest = nfiCtypesSupport.toNative(g, value.length, InvokeNativeFunction.getUncached());
            }
            return guest;
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

        @Override
        ArrayStorage copy() {
            return new NativeByteArrayStorage(PythonUtils.arrayCopyOf(value, value.length));
        }
    }

    static class MemoryViewStorage extends ArrayStorage {
        PMemoryView value;
        final int length;

        @TruffleBoundary
        MemoryViewStorage(PMemoryView bytes) {
            super(StorageType.INT8);
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
        protected Object getNativeObject(NFICtypesSupport nfiCtypesSupport) {
            if (value.getBuffer() instanceof PythonObject) {
                byte[] bytes = PythonBufferAccessLibrary.getUncached().getInternalByteArray(value.getBuffer());
                return nfiCtypesSupport.getContext().getEnv().asGuestValue(bytes);
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
            super(StorageType.INT16);
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
        protected Object getNativeObject(NFICtypesSupport nfiCtypesSupport) {
            return nfiCtypesSupport.getContext().getEnv().asGuestValue(value);
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
            super(StorageType.INT32);
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
        protected Object getNativeObject(NFICtypesSupport nfiCtypesSupport) {
            return nfiCtypesSupport.getContext().getEnv().asGuestValue(value);
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
            super(StorageType.INT64);
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
        protected Object getNativeObject(NFICtypesSupport nfiCtypesSupport) {
            return nfiCtypesSupport.getContext().getEnv().asGuestValue(value);
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
            super(StorageType.FLOAT);
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
        protected Object getNativeObject(NFICtypesSupport nfiCtypesSupport) {
            return nfiCtypesSupport.getContext().getEnv().asGuestValue(value);
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
            super(StorageType.DOUBLE);
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
        protected Object getNativeObject(NFICtypesSupport nfiCtypesSupport) {
            return nfiCtypesSupport.getContext().getEnv().asGuestValue(value);
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

    static class ObjectArrayStorage extends ArrayStorage {
        Object[] value;

        ObjectArrayStorage(int size) {
            super(StorageType.OBJECT);
            this.value = new Object[size];
        }

        @Override
        protected final Object getValue(int idx) {
            return value[idx];
        }

        @Override
        protected void setValue(Object v, int idx) {
            value[idx] = v;
        }

        @Override
        protected Object getNativeObject(NFICtypesSupport nfiCtypesSupport) {
            return nfiCtypesSupport.getContext().getEnv().asGuestValue(value);
        }

        @Override
        protected Storage resize(int length) {
            if (length > value.length) {
                ObjectArrayStorage storage = new ObjectArrayStorage(length);
                PythonUtils.arraycopy(value, 0, storage.value, 0, length);
                return storage;
            }
            return this;
        }

        @Override
        ArrayStorage copy() {
            ObjectArrayStorage s = new ObjectArrayStorage(0);
            s.value = PythonUtils.arrayCopyOf(value, value.length);
            return s;
        }
    }

}
