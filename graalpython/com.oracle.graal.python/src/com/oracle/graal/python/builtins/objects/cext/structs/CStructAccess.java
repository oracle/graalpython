/*
 * Copyright (c) 2019, 2026, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.builtins.objects.cext.structs;

import static com.oracle.graal.python.nfi2.NativeMemory.NULLPTR;
import static com.oracle.graal.python.nfi2.NativeMemory.POINTER_SIZE;
import static com.oracle.truffle.api.CompilerDirectives.shouldNotReachHere;

import com.oracle.graal.python.builtins.objects.cext.PythonNativeObject;
import com.oracle.graal.python.builtins.objects.cext.capi.CExtNodes.FromCharPointerNode;
import com.oracle.graal.python.builtins.objects.cext.capi.CExtNodes.PCallCapiFunction;
import com.oracle.graal.python.builtins.objects.cext.capi.NativeCAPISymbol;
import com.oracle.graal.python.builtins.objects.cext.capi.transitions.CApiTransitions.NativePtrToPythonNode;
import com.oracle.graal.python.builtins.objects.cext.capi.transitions.CApiTransitions.PythonToNativeNewRefNode;
import com.oracle.graal.python.builtins.objects.cext.common.NativePointer;
import com.oracle.graal.python.builtins.objects.cext.structs.CStructAccessFactory.ReadCharPtrNodeGen;
import com.oracle.graal.python.builtins.objects.cext.structs.CStructAccessFactory.ReadObjectNodeGen;
import com.oracle.graal.python.nfi2.NativeMemory;
import com.oracle.graal.python.nodes.PGuards;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateInline;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.strings.TruffleString;

@SuppressWarnings("truffle-inlining")
public class CStructAccess {

    public static long allocate(CStructs struct) {
        return NativeMemory.calloc(struct.size());
    }

    public static long getFieldPtr(long structBasePtr, CFields field) {
        return NativeMemory.getFieldPtr(structBasePtr, field.offset());
    }

    public static byte readByteField(long structBasePtr, CFields field) {
        assert field.type.isI8();
        return NativeMemory.readByte(getFieldPtr(structBasePtr, field));
    }

    public static void writeByteField(long structBasePtr, CFields field, byte value) {
        assert field.type.isI8();
        NativeMemory.writeByte(getFieldPtr(structBasePtr, field), value);
    }

    public static int readIntField(long structBasePtr, CFields field) {
        assert field.type.isI32();
        return NativeMemory.readInt(getFieldPtr(structBasePtr, field));
    }

    public static void writeIntField(long structBasePtr, CFields field, int value) {
        assert field.type.isI32();
        NativeMemory.writeInt(getFieldPtr(structBasePtr, field), value);
    }

    public static int readStructArrayIntField(long arrayPtr, long index, CFields field) {
        return readIntField(getArrayElementPtr(arrayPtr, index, field.struct), field);
    }

    public static void writeStructArrayIntField(long arrayPtr, long index, CFields field, int value) {
        writeIntField(getArrayElementPtr(arrayPtr, index, field.struct), field, value);
    }

    public static long readLongField(long structBasePtr, CFields field) {
        assert field.type.isI64();
        return NativeMemory.readLong(getFieldPtr(structBasePtr, field));
    }

    public static void writeLongField(long structBasePtr, CFields field, long value) {
        assert field.type.isI64();
        NativeMemory.writeLong(getFieldPtr(structBasePtr, field), value);
    }

    public static long readStructArrayLongField(long arrayPtr, long index, CFields field) {
        return readLongField(getArrayElementPtr(arrayPtr, index, field.struct), field);
    }

    public static void writeStructArrayLongField(long arrayPtr, long index, CFields field, long value) {
        writeLongField(getArrayElementPtr(arrayPtr, index, field.struct), field, value);
    }

    public static double readDoubleField(long structBasePtr, CFields field) {
        assert field.type.isDouble();
        return NativeMemory.readDouble(getFieldPtr(structBasePtr, field));
    }

    public static void writeDoubleField(long structBasePtr, CFields field, double value) {
        assert field.type.isDouble();
        NativeMemory.writeDouble(getFieldPtr(structBasePtr, field), value);
    }

    public static long readPtrField(long structBasePtr, CFields field) {
        assert field.type.isPyObjectOrPointer();
        return NativeMemory.readLong(getFieldPtr(structBasePtr, field));
    }

    public static void writePtrField(long structBasePtr, CFields field, long value) {
        assert field.type.isPyObjectOrPointer();
        NativeMemory.writeLong(getFieldPtr(structBasePtr, field), value);
    }

    public static long readStructArrayPtrField(long arrayPtr, long index, CFields field) {
        return readPtrField(getArrayElementPtr(arrayPtr, index, field.struct), field);
    }

    public static void writeStructArrayPtrField(long arrayPtr, long index, CFields field, long value) {
        writePtrField(getArrayElementPtr(arrayPtr, index, field.struct), field, value);
    }

    private static long getArrayElementPtr(long arrayPtr, long index, CStructs struct) {
        return arrayPtr + index * struct.size();
    }

    @GenerateUncached
    @GenerateInline(false)
    public abstract static class AllocatePyMemNode extends Node {

        abstract long execute(long count, long elsize);

        // TODO(NFi2) review usages
        public long alloc(int size) {
            return execute(1, size);
        }

        @Specialization
        static long allocLongPyMem(long count, long elsize,
                        @Cached PCallCapiFunction call) {
            assert elsize >= 0;
            return (long) call.call(NativeCAPISymbol.FUN_PYMEM_ALLOC, count, elsize);
        }
    }

    @ImportStatic(PGuards.class)
    @GenerateUncached
    @GenerateInline(false)
    public abstract static class ReadObjectNode extends Node {
        abstract Object execute(long pointer, long offset);

        public final Object read(long pointer, CFields field) {
            assert field.type.isPyObject();
            return execute(pointer, field.offset());
        }

        public final Object read(long pointer, long offset) {
            return execute(pointer, offset);
        }

        public final Object readFromObj(PythonNativeObject self, CFields field) {
            return read(self.getPtr(), field);
        }

        public final Object[] readPyObjectArray(long pointer, int elements) {
            return readPyObjectArray(pointer, elements, 0);
        }

        public final Object[] readPyObjectArray(long pointer, int elements, int offset) {
            Object[] result = new Object[elements];
            for (int i = 0; i < result.length; i++) {
                result[i] = execute(pointer, (i + offset) * POINTER_SIZE);
            }
            return result;
        }

        @Specialization
        static Object readLong(long pointer, long offset,
                        @Cached NativePtrToPythonNode toPython) {
            assert offset >= 0;
            return toPython.execute(NativeMemory.readPtr(pointer + offset), false);
        }

        public static ReadObjectNode getUncached() {
            return ReadObjectNodeGen.getUncached();
        }
    }

    @ImportStatic(PGuards.class)
    @GenerateUncached
    @GenerateInline(false)
    public abstract static class ReadCharPtrNode extends Node {
        abstract TruffleString execute(long pointer, CFields field);

        public static TruffleString executeUncached(long pointer, CFields field) {
            return ReadCharPtrNodeGen.getUncached().execute(pointer, field);
        }

        public final TruffleString readFromObj(PythonNativeObject self, CFields field) {
            return execute(self.getPtr(), field);
        }

        @Specialization
        static TruffleString readLong(long pointer, CFields field,
                        @Cached FromCharPointerNode toPython) {
            assert field.type.isCharPtr();
            return toPython.execute(readPtrField(pointer, field));
        }
    }

    @GenerateUncached
    @GenerateInline(false)
    public abstract static class WriteTruffleStringNode extends Node {

        abstract void execute(long dstPointer, int dstOffset, TruffleString src, int srcOffset, int length, TruffleString.Encoding encoding);

        public final void write(long dstPointer, TruffleString src, TruffleString.Encoding encoding) {
            execute(dstPointer, 0, src, 0, src.byteLength(encoding), encoding);
        }

        @Specialization
        static void writeLong(long dstPointer, int dstOffset, TruffleString src, int srcOffset, int length, TruffleString.Encoding encoding,
                        @Cached TruffleString.CopyToNativeMemoryNode copyToNativeMemoryNode) {
            copyToNativeMemoryNode.execute(src, srcOffset, NativePointer.wrap(dstPointer), dstOffset, length, encoding);
        }
    }

    @ImportStatic(PGuards.class)
    @GenerateUncached
    @GenerateInline(false)
    public abstract static class WriteObjectNewRefNode extends Node {

        abstract void execute(long pointer, long offset, Object value);

        public final void write(long pointer, CFields field, Object value) {
            assert field.type.isPyObject();
            execute(pointer, field.offset(), value);
        }

        public final void writeToObject(PythonNativeObject self, CFields field, Object value) {
            write(self.getPtr(), field, value);
        }

        public final void write(long pointer, Object value) {
            execute(pointer, 0, value);
        }

        public final void writeArray(long pointer, Object[] values, int length, int sourceOffset, long targetOffset) {
            if (length > values.length) {
                throw shouldNotReachHere();
            }
            for (int i = 0; i < length; i++) {
                execute(pointer, (i + targetOffset) * POINTER_SIZE, values[i + sourceOffset]);
            }
        }

        @Specialization
        static void writeLong(long pointer, long offset, Object value,
                        @Cached NativePtrToPythonNode toPython,
                        @Cached PythonToNativeNewRefNode toNative) {
            assert offset >= 0;
            long old = NativeMemory.readPtr(pointer + offset);
            if (old != NULLPTR) {
                toPython.execute(old, true);
            }
            NativeMemory.writePtr(pointer + offset, toNative.executeLong(value));
        }
    }
}
