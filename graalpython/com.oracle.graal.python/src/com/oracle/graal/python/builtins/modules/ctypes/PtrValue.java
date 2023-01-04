/*
 * Copyright (c) 2021, 2023, Oracle and/or its affiliates. All rights reserved.
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

import com.oracle.graal.python.builtins.modules.ctypes.FFIType.FFI_TYPES;
import com.oracle.graal.python.builtins.objects.buffer.PythonBufferAccessLibrary;
import com.oracle.graal.python.builtins.objects.memoryview.PMemoryView;
import com.oracle.graal.python.builtins.objects.object.PythonObject;
import com.oracle.graal.python.util.PythonUtils;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.TruffleLanguage.Env;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;

final class PtrValue implements TruffleObject {
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

    protected void toBytes(FFIType type, byte[] bytes) {
        ptr = new ByteArrayStorage(type.type, bytes);
        offset = 0;
    }

    protected boolean isManagedBytes() {
        return ptr instanceof ByteArrayStorage;
    }

    protected boolean isMemoryView() {
        return ptr instanceof MemoryViewStorage;
    }

    protected boolean isNativePointer() {
        return ptr instanceof NativePointerStorage;
    }

    protected boolean isNil() {
        return ptr instanceof NullStorage;
    }

    protected Object getPrimitiveValue(FFIType type) {
        if (ptr instanceof PrimitiveStorage) {
            return ((PrimitiveStorage) ptr).value;
        } else {
            return readArrayElement(type, 0);
        }
    }

    protected void ensureCapacity(int size) {
        if (isManagedBytes()) {
            ptr = ptr.resize(size);
        }
    }

    protected Object readArrayElement(FFIType type, int idx) {
        if (isManagedBytes()) {
            return CtypesNodes.getValue(type.type, ((ByteArrayStorage) ptr).value, offset + idx);
        } else if (ptr instanceof MemoryViewStorage) {
            PMemoryView mem = ((MemoryViewStorage) ptr).value;
            byte[] bytes = new byte[type.size];
            for (int i = 0; i < bytes.length; i++) {
                bytes[i] = PythonBufferAccessLibrary.getUncached().readByte(mem, offset + i);
            }
            return CtypesNodes.getValue(type.type, bytes, 0);
        } else {
            throw CompilerDirectives.shouldNotReachHere("not implemented");
        }
    }

    protected void writeArrayElement(FFIType type, int idx, Object value) {
        if (isManagedBytes()) {
            CtypesNodes.setValue(type.type, ((ByteArrayStorage) ptr).value, offset + idx, value);
        } else if (ptr instanceof MemoryViewStorage) {
            PMemoryView mem = ((MemoryViewStorage) ptr).value;
            byte[] bytes = new byte[type.size];
            CtypesNodes.setValue(type.type, bytes, 0, value);
            for (int i = 0; i < bytes.length; i++) {
                PythonBufferAccessLibrary.getUncached().writeByte(mem, offset + idx + i, bytes[i]);
            }
        }
    }

    protected void writeBytesArrayElement(byte[] value) {
        if (isManagedBytes()) {
            ensureCapacity(offset + value.length);
            PythonUtils.arraycopy(value, 0, ((ByteArrayStorage) ptr).value, offset, value.length);
        } else if (ptr instanceof MemoryViewStorage) {
            PMemoryView mem = ((MemoryViewStorage) ptr).value;
            for (int i = 0; i < value.length; i++) {
                PythonBufferAccessLibrary.getUncached().writeByte(mem, offset + i, value[i]);
            }
        }
    }

    protected void writePrimitive(FFIType type, Object val) {
        if (!ptr.type.isArray()) {
            if (isNil() || (ptr.type != type.type)) {
                toPrimitive(type, val);
            } else {
                assert ptr instanceof PrimitiveStorage : " wrong storage type!";
                ((PrimitiveStorage) ptr).value = val;
            }
        } else {
            writeArrayElement(type, 0, val);
        }
    }

    protected static PtrValue nil() {
        return new PtrValue(NULL_STORAGE, 0);
    }

    protected static PtrValue allocate(FFIType type, int size) {
        return new PtrValue(createStorageInternal(type, size, null), 0);
    }

    protected static PtrValue bytes(FFIType type, byte[] bytes) {
        return new PtrValue(new ByteArrayStorage(type.type, bytes), 0);
    }

    protected void toPrimitive(FFIType type, Object v) {
        assert !type.type.isArray() : "Cannot convert array to primitive";
        ptr = new PrimitiveStorage(v, type.type);
    }

    protected void toNil() {
        ptr = NULL_STORAGE;
        offset = 0;
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

    protected void createStorage(FFIType type, int size, Object value) {
        if (isNil()) {
            ptr = createStorageInternal(type, size, value);
            offset = 0;
        }
    }

    private static Storage createStorageInternal(FFIType type, int size, Object value) {
        FFI_TYPES t = type.type;
        switch (t) {
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
                return new PrimitiveStorage(value != null ? value : t.getInitValue(), type.type);

            case FFI_TYPE_STRUCT: // TODO
            case FFI_TYPE_STRING:
            case FFI_TYPE_UINT8_ARRAY:
            case FFI_TYPE_SINT8_ARRAY:
            case FFI_TYPE_UINT16_ARRAY:
            case FFI_TYPE_SINT16_ARRAY:
            case FFI_TYPE_UINT32_ARRAY:
            case FFI_TYPE_SINT32_ARRAY:
            case FFI_TYPE_UINT64_ARRAY:
            case FFI_TYPE_SINT64_ARRAY:
            case FFI_TYPE_FLOAT_ARRAY:
            case FFI_TYPE_DOUBLE_ARRAY:
                return new ByteArrayStorage(type.type, new byte[size]);

            case FFI_TYPE_POINTER:
                return new NativePointerStorage(value);
            default:
                throw CompilerDirectives.shouldNotReachHere("Not supported type!");

        }
    }

    protected static PtrValue create(FFIType type, int size, Object value, int offset) {
        return new PtrValue(createStorageInternal(type, size, value), offset);
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

        protected abstract Object getValue();
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
        protected Object getValue() {
            return null;
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
        protected Object getValue() {
            return value;
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
        protected Object getValue() {
            return value;
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

    @ExportLibrary(PythonBufferAccessLibrary.class)
    static class ByteArrayStorage extends ArrayStorage {
        byte[] value;

        ByteArrayStorage(FFI_TYPES type, int size) {
            this(type, new byte[size]);
        }

        ByteArrayStorage(FFI_TYPES type, byte[] bytes) {
            super(type);
            this.value = bytes;
        }

        @Override
        protected final Object getValue(int idx) {
            return value[idx];
        }

        @Override
        protected void setValue(Object v, int idx) {
            CtypesNodes.setValue(value, v, idx);
        }

        @Override
        protected Object getValue() {
            return value;
        }

        @Override
        protected Object getNativeObject(Env env) {
            return env.asGuestValue(value);
        }

        @Override
        protected Storage resize(int length) {
            if (length > value.length) {
                byte[] newStorage = new byte[length];
                PythonUtils.arraycopy(value, 0, newStorage, 0, value.length);
                this.value = newStorage;
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
            return new ByteArrayStorage(type, PythonUtils.arrayCopyOf(value, value.length));
        }

        @ExportMessage
        @SuppressWarnings("static-method")
        boolean isBuffer() {
            return true;
        }

        @ExportMessage
        int getBufferLength() {
            return value.length;
        }

        @ExportMessage
        byte readByte(int byteIndex) {
            return value[byteIndex];
        }

        @ExportMessage
        @SuppressWarnings("static-method")
        boolean isReadonly() {
            return false;
        }

        @ExportMessage
        void writeByte(int byteIndex, byte byteValue) {
            value[byteIndex] = byteValue;
        }

        @ExportMessage
        @SuppressWarnings("static-method")
        boolean hasInternalByteArray() {
            return true;
        }

        @ExportMessage
        byte[] getInternalByteArray() {
            return value;
        }
    }

    @ExportLibrary(value = PythonBufferAccessLibrary.class, delegateTo = "value")
    static class MemoryViewStorage extends ArrayStorage {
        final PMemoryView value;
        final int length;

        @TruffleBoundary
        MemoryViewStorage(PMemoryView bytes) {
            super(FFI_TYPES.FFI_TYPE_SINT8_ARRAY);
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
        protected Object getValue() {
            return value;
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
                ByteArrayStorage storage = new ByteArrayStorage(type, len);
                PythonUtils.arraycopy(bytes, 0, storage.value, 0, len);
                return storage;
            }
            return this;
        }

        @TruffleBoundary
        @Override
        ArrayStorage copy() {
            byte[] bytes = PythonBufferAccessLibrary.getUncached().getInternalOrCopiedByteArray(value);
            return new ByteArrayStorage(type, bytes);
        }
    }
}
