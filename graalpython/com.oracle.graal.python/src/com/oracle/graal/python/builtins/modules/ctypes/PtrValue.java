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

import static com.oracle.graal.python.util.PythonUtils.ARRAY_ACCESSOR;

import com.oracle.graal.python.builtins.modules.ctypes.FFIType.FFI_TYPES;
import com.oracle.graal.python.builtins.objects.memoryview.PMemoryView;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.interop.TruffleObject;

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

    protected boolean isNil() {
        return ptr instanceof NullStorage;
    }

    protected static PtrValue nil() {
        return new PtrValue(NULL_STORAGE, 0);
    }

    protected static PtrValue allocate(FFIType type, int size) {
        return new PtrValue(createStorageInternal(type, size, null), 0);
    }

    protected static PtrValue bytes(byte[] bytes) {
        return new PtrValue(new ByteArrayStorage(bytes), 0);
    }

    protected static PtrValue nativeMemory(long nativePointer) {
        return new PtrValue(new NativeMemoryStorage(nativePointer), 0);
    }

    protected void toNil() {
        ptr = NULL_STORAGE;
        offset = 0;
    }

    protected void createStorage(FFIType type, int size, Object value) {
        if (isNil()) {
            ptr = createStorageInternal(type, size, value);
            offset = 0;
        }
    }

    private static Storage createStorageInternal(FFIType type, int size, Object value) {
        FFI_TYPES t = type.type;
        return switch (t) {
            case FFI_TYPE_VOID -> NULL_STORAGE;
            case FFI_TYPE_UINT8, FFI_TYPE_SINT8 -> {
                byte[] bytes = new byte[Byte.BYTES];
                if (value != null) {
                    bytes[0] = (byte) value;
                }
                yield new ByteArrayStorage(bytes);
            }
            case FFI_TYPE_UINT16, FFI_TYPE_SINT16 -> {
                byte[] bytes = new byte[Short.BYTES];
                if (value != null) {
                    ARRAY_ACCESSOR.putShort(bytes, 0, (short) value);
                }
                yield new ByteArrayStorage(bytes);
            }
            case FFI_TYPE_UINT32, FFI_TYPE_SINT32 -> {
                byte[] bytes = new byte[Integer.BYTES];
                if (value != null) {
                    ARRAY_ACCESSOR.putInt(bytes, 0, (int) value);
                }
                yield new ByteArrayStorage(bytes);
            }
            case FFI_TYPE_UINT64, FFI_TYPE_SINT64 -> {
                byte[] bytes = new byte[Long.BYTES];
                if (value != null) {
                    ARRAY_ACCESSOR.putLong(bytes, 0, (long) value);
                }
                yield new ByteArrayStorage(bytes);
            }
            case FFI_TYPE_FLOAT -> {
                byte[] bytes = new byte[Float.BYTES];
                if (value != null) {
                    ARRAY_ACCESSOR.putLong(bytes, 0, Float.floatToRawIntBits((float) value));
                }
                yield new ByteArrayStorage(bytes);
            }
            case FFI_TYPE_DOUBLE -> {
                byte[] bytes = new byte[Double.BYTES];
                if (value != null) {
                    ARRAY_ACCESSOR.putLong(bytes, 0, Double.doubleToRawLongBits((double) value));
                }
                yield new ByteArrayStorage(bytes);
            }
            case FFI_TYPE_UINT8_ARRAY, FFI_TYPE_SINT8_ARRAY, FFI_TYPE_UINT16_ARRAY, FFI_TYPE_SINT16_ARRAY, FFI_TYPE_UINT32_ARRAY, FFI_TYPE_SINT32_ARRAY, FFI_TYPE_UINT64_ARRAY, FFI_TYPE_SINT64_ARRAY,
                            FFI_TYPE_FLOAT_ARRAY, FFI_TYPE_DOUBLE_ARRAY, FFI_TYPE_STRING -> {
                assert value == null;
                if (size % 8 == 0) {
                    PtrValue[] pointers = new PtrValue[size / 8];
                    for (int i = 0; i < pointers.length; i++) {
                        pointers[i] = PtrValue.nil();
                    }
                    yield new PointerArrayStorage(pointers);
                } else {
                    yield new ByteArrayStorage(new byte[size]);
                }
            }
            case FFI_TYPE_POINTER -> {
                if (value == null) {
                    PtrValue[] pointers = new PtrValue[]{PtrValue.nil()};
                    yield new PointerArrayStorage(pointers);
                } else {
                    yield new NativePointerStorage(value);
                }
            }
            case FFI_TYPE_STRUCT -> {
                if (value == null) {
                    yield new ByteArrayStorage(new byte[size]);
                } else {
                    yield new NativePointerStorage(value);
                }
            }
            default -> throw CompilerDirectives.shouldNotReachHere("Not supported type!");
        };
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

    protected PtrValue withOffset(int incOffset) {
        return new PtrValue(ptr, offset + incOffset);
    }

    protected PtrValue createReference() {
        return createReference(0);
    }

    protected PtrValue createReference(int offset) {
        return new PtrValue(new PointerArrayStorage(new PtrValue[]{this}), offset);
    }

    protected static boolean isNull(PtrValue b_ptr) {
        return b_ptr == null || b_ptr.ptr == NULL_STORAGE;
    }

    abstract static class Storage {
    }

    static final class NullStorage extends Storage {

        NullStorage() {
        }
    }

    static final class NativePointerStorage extends Storage {

        Object value;

        NativePointerStorage(Object pointer) {
            this.value = pointer;
        }
    }

    static final class PointerArrayStorage extends Storage {
        PtrValue[] objects;
        byte[] nativePointerBytes;

        public PointerArrayStorage(PtrValue[] objects) {
            this.objects = objects;
        }
    }

    static final class ByteArrayStorage extends Storage {
        final byte[] value;

        ByteArrayStorage(byte[] bytes) {
            this.value = bytes;
        }
    }

    static final class MemoryViewStorage extends Storage {
        final PMemoryView value;

        MemoryViewStorage(PMemoryView bytes) {
            this.value = bytes;
        }
    }

    static final class NativeMemoryStorage extends Storage {
        final long pointer;

        public NativeMemoryStorage(long pointer) {
            this.pointer = pointer;
        }
    }
}
