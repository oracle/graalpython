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
package com.oracle.graal.python.builtins.modules.ctypes.memory;

import static com.oracle.graal.python.util.PythonUtils.ARRAY_ACCESSOR;

import com.oracle.graal.python.builtins.modules.ctypes.FFIType;
import com.oracle.graal.python.builtins.modules.ctypes.FFIType.FFI_TYPES;
import com.oracle.graal.python.builtins.objects.memoryview.PMemoryView;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;

@ExportLibrary(InteropLibrary.class)
public final class Pointer implements TruffleObject {
    public static final Pointer NULL = new Pointer(new NullStorage(), 0);

    final MemoryBlock memory;
    final int offset;

    private Pointer(Storage storage, int offset) {
        this(new MemoryBlock(storage), offset);
    }

    private Pointer(MemoryBlock memory, int offset) {
        this.memory = memory;
        this.offset = offset;
    }

    @ExportMessage
    public boolean isNull() {
        return this == NULL;
    }

    public static Pointer allocate(FFIType type, int size) {
        return new Pointer(createStorageInternal(type, size, null), 0);
    }

    public static Pointer bytes(byte[] bytes) {
        return bytes(bytes, 0);
    }

    public static Pointer bytes(byte[] bytes, int offset) {
        return new Pointer(new ByteArrayStorage(bytes), offset);
    }

    public static Pointer nativeMemory(long nativePointer) {
        if (nativePointer == 0) {
            return NULL;
        }
        return new Pointer(new LongPointerStorage(nativePointer), 0);
    }

    public static Pointer nativeMemory(Object nativePointer) {
        if (nativePointer instanceof Long value) {
            return nativeMemory((long) value);
        }
        return new Pointer(new NFIPointerStorage(nativePointer), 0);
    }

    public static Pointer pythonObject(Object object) {
        return new Pointer(new PythonObjectStorage(object), 0);
    }

    private static Storage createStorageInternal(FFIType type, int size, Object value) {
        FFI_TYPES t = type.type;
        return switch (t) {
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
                    ARRAY_ACCESSOR.putInt(bytes, 0, Float.floatToRawIntBits((float) value));
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
            case FFI_TYPE_POINTER, FFI_TYPE_STRUCT -> {
                if (value == null) {
                    yield new ZeroStorage(size);
                } else {
                    Pointer valuePtr = nativeMemory(value);
                    yield new PointerArrayStorage(new Pointer[]{valuePtr});
                }
            }
            default -> throw CompilerDirectives.shouldNotReachHere("Not supported type!");
        };
    }

    public static Pointer create(FFIType type, int size, Object value, int offset) {
        return new Pointer(createStorageInternal(type, size, value), offset);
    }

    public static Pointer memoryView(PMemoryView mv) {
        return new Pointer(new MemoryViewStorage(mv), 0);
    }

    public Pointer withOffset(int incOffset) {
        return new Pointer(memory, offset + incOffset);
    }

    public Pointer createReference() {
        return createReference(0);
    }

    public Pointer createReference(int offset) {
        return new Pointer(new PointerArrayStorage(new Pointer[]{this}), offset);
    }

    static final class MemoryBlock {
        Storage storage;

        public MemoryBlock(Storage storage) {
            this.storage = storage;
        }
    }

    abstract static class Storage {
    }

    static final class NullStorage extends Storage {

        NullStorage() {
        }
    }

    /**
     * Newly allocated storage that will be rewritten to a more appropriate one upon the first
     * write. Reads return zeros.
     */
    static final class ZeroStorage extends Storage {
        int size;

        public ZeroStorage(int size) {
            this.size = size;
        }

        void boundsCheck(int offset, int size) {
            if (offset + size > this.size) {
                throw CompilerDirectives.shouldNotReachHere("Out of bounds-read");
            }
        }

        ByteArrayStorage allocateBytes(MemoryBlock memory) {
            ByteArrayStorage newStorage = new ByteArrayStorage(new byte[size]);
            memory.storage = newStorage;
            return newStorage;
        }

        PointerArrayStorage allocatePointers(MemoryBlock memory) {
            PointerArrayStorage newStorage = new PointerArrayStorage(new Pointer[(size + 7) / 8]);
            memory.storage = newStorage;
            return newStorage;
        }
    }

    static final class PointerArrayStorage extends Storage {
        Pointer[] pointers;

        public PointerArrayStorage(Pointer[] pointers) {
            this.pointers = pointers;
        }
    }

    static final class ByteArrayStorage extends Storage {
        final byte[] bytes;

        ByteArrayStorage(byte[] bytes) {
            this.bytes = bytes;
        }
    }

    static final class MemoryViewStorage extends Storage {
        final PMemoryView memoryView;

        MemoryViewStorage(PMemoryView memoryView) {
            this.memoryView = memoryView;
        }
    }

    static final class LongPointerStorage extends Storage {
        final long pointer;

        public LongPointerStorage(long pointer) {
            this.pointer = pointer;
        }
    }

    static final class NFIPointerStorage extends Storage {
        final Object pointer;

        public NFIPointerStorage(Object pointer) {
            this.pointer = pointer;
        }
    }

    static final class PythonObjectStorage extends Storage {
        final Object pythonObject;

        public PythonObjectStorage(Object pythonObject) {
            this.pythonObject = pythonObject;
        }
    }
}
