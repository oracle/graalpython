/*
 * Copyright (c) 2019, 2023, Oracle and/or its affiliates. All rights reserved.
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

import com.oracle.graal.python.builtins.objects.PythonAbstractObject;
import com.oracle.graal.python.builtins.objects.cext.PythonNativeObject;
import com.oracle.graal.python.builtins.objects.cext.capi.CExtNodes.FromCharPointerNode;
import com.oracle.graal.python.builtins.objects.cext.capi.CExtNodes.PCallCapiFunction;
import com.oracle.graal.python.builtins.objects.cext.capi.NativeCAPISymbol;
import com.oracle.graal.python.builtins.objects.cext.capi.PythonNativeWrapper;
import com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor;
import com.oracle.graal.python.builtins.objects.cext.capi.transitions.CApiTransitions.NativePtrToPythonNode;
import com.oracle.graal.python.builtins.objects.cext.capi.transitions.CApiTransitions.NativeToPythonNode;
import com.oracle.graal.python.builtins.objects.cext.capi.transitions.CApiTransitions.NativeToPythonStealingNode;
import com.oracle.graal.python.builtins.objects.cext.capi.transitions.CApiTransitions.PointerContainer;
import com.oracle.graal.python.builtins.objects.cext.capi.transitions.CApiTransitions.PythonToNativeNewRefNode;
import com.oracle.graal.python.builtins.objects.cext.capi.transitions.CApiTransitions.PythonToNativeNode;
import com.oracle.graal.python.builtins.objects.cext.structs.CStructAccessFactory.FreeNodeGen;
import com.oracle.graal.python.builtins.objects.cext.structs.CStructAccessFactory.ReadCharPtrNodeGen;
import com.oracle.graal.python.builtins.objects.cext.structs.CStructAccessFactory.ReadObjectNodeGen;
import com.oracle.graal.python.builtins.objects.cext.structs.CStructAccessFactory.ReadObjectStealingNodeGen;
import com.oracle.graal.python.builtins.objects.cext.structs.CStructAccessFactory.ReadPointerNodeGen;
import com.oracle.graal.python.nodes.PGuards;
import com.oracle.graal.python.util.PythonUtils;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.strings.TruffleString;

import sun.misc.Unsafe;

public class CStructAccess {

    private static boolean validPointer(Object pointer) {
        return !(pointer instanceof PythonAbstractObject) && !(pointer instanceof PythonNativeWrapper);
    }

    @ImportStatic(PGuards.class)
    @GenerateUncached
    public abstract static class AllocateNode extends Node {

        abstract Object execute(long size, boolean allocatePyMem);

        public final Object alloc(CStructs struct) {
            return execute(struct.size(), false);
        }

        public final Object alloc(CStructs struct, boolean allocatePyMem) {
            return execute(struct.size(), allocatePyMem);
        }

        public final Object alloc(long size) {
            return execute(size, false);
        }

        public Object alloc(int size, boolean allocatePyMem) {
            return execute(size, allocatePyMem);
        }

        @Specialization(guards = "!allocatePyMem")
        static long allocLong(long size, @SuppressWarnings("unused") boolean allocatePyMem) {
            assert size >= 0;
            long memory = UNSAFE.allocateMemory(size);
            UNSAFE.setMemory(memory, size, (byte) 0);
            return memory;
        }

        @Specialization(guards = "allocatePyMem")
        static Object allocLong(long size, @SuppressWarnings("unused") boolean allocatePyMem,
                        @Cached PCallCapiFunction call) {
            assert size >= 0;
            return call.call(NativeCAPISymbol.FUN_PYMEM_ALLOC, size, 1);
        }
    }

    @ImportStatic(PGuards.class)
    @GenerateUncached
    public abstract static class FreeNode extends Node {

        abstract void execute(Object pointer);

        public final void free(Object pointer) {
            execute(pointer);
        }

        @Specialization
        static void freeLong(long pointer) {
            UNSAFE.freeMemory(pointer);
        }

        @Specialization(guards = {"!isLong(pointer)", "lib.isPointer(pointer)"})
        static void freePointer(Object pointer,
                        @CachedLibrary(limit = "3") InteropLibrary lib) {
            freeLong(asPointer(pointer, lib));
        }

        @Specialization(guards = {"!isLong(pointer)", "!lib.isPointer(pointer)"})
        static void freeManaged(Object pointer,
                        @SuppressWarnings("unused") @CachedLibrary(limit = "3") InteropLibrary lib,
                        @Cached PCallCapiFunction call) {
            assert validPointer(pointer);
            call.call(NativeCAPISymbol.FUN_FREE, pointer);
        }

        public static FreeNode create() {
            return FreeNodeGen.create();
        }
    }

    public abstract static class ReadBaseNode extends Node implements CStructAccessNode {

        abstract Object executeGeneric(Object pointer, long offset);

        public final Object readGeneric(Object pointer, long offset) {
            return executeGeneric(pointer, offset);
        }
    }

    @ImportStatic(PGuards.class)
    @GenerateUncached
    public abstract static class GetElementPtrNode extends ReadBaseNode {

        abstract Object execute(Object pointer, long offset);

        public final Object getElementPtr(Object pointer, CFields field) {
            assert accepts(field);
            return execute(pointer, field.offset());
        }

        public final boolean accepts(ArgDescriptor desc) {
            return true;
        }

        @Specialization
        static Object getLong(long pointer, long offset) {
            assert offset >= 0;
            return pointer + offset;
        }

        @Specialization(guards = {"!isLong(pointer)", "lib.isPointer(pointer)"})
        static Object getPointer(Object pointer, long offset,
                        @CachedLibrary(limit = "3") InteropLibrary lib) {
            return getLong(asPointer(pointer, lib), offset);
        }

        @Specialization(guards = {"!isLong(pointer)", "!lib.isPointer(pointer)"})
        static Object getManaged(Object pointer, long offset,
                        @SuppressWarnings("unused") @CachedLibrary(limit = "3") InteropLibrary lib,
                        @Cached PCallCapiFunction call) {
            assert validPointer(pointer);
            return call.call(NativeCAPISymbol.FUN_PTR_ADD, pointer, offset);
        }
    }

    @ImportStatic(PGuards.class)
    @GenerateUncached
    public abstract static class ReadByteNode extends ReadBaseNode {

        abstract int execute(Object pointer, long offset);

        public final int read(Object pointer, CFields field) {
            assert accepts(field);
            return execute(pointer, field.offset());
        }

        public final int readFromObj(PythonNativeObject self, CFields field) {
            return read(self.getPtr(), field);
        }

        public final boolean accepts(ArgDescriptor desc) {
            return desc.isI8();
        }

        public final byte[] readByteArray(Object pointer, int elements) {
            return readByteArray(pointer, elements, 0);
        }

        public final byte[] readByteArray(Object pointer, int elements, int sourceOffset) {
            byte[] result = new byte[elements];
            for (int i = 0; i < result.length; i++) {
                result[i] = (byte) execute(pointer, (i + sourceOffset) * Byte.BYTES);
            }
            return result;
        }

        public final void readByteArray(Object pointer, byte[] target, int length, int sourceOffset, int targetOffset) {
            for (int i = 0; i < length; i++) {
                target[i + targetOffset] = (byte) execute(pointer, (i + sourceOffset) * Byte.BYTES);
            }
        }

        public final byte readArrayElement(Object pointer, long element) {
            return (byte) execute(pointer, element);
        }

        @Specialization
        static int readLong(long pointer, long offset) {
            assert offset >= 0;
            return UNSAFE.getByte(pointer + offset);
        }

        @Specialization(guards = {"!isLong(pointer)", "lib.isPointer(pointer)"})
        static int readPointer(Object pointer, long offset,
                        @CachedLibrary(limit = "3") InteropLibrary lib) {
            return readLong(asPointer(pointer, lib), offset);
        }

        @Specialization(guards = {"!isLong(pointer)", "!lib.isPointer(pointer)"})
        static int readManaged(Object pointer, long offset,
                        @SuppressWarnings("unused") @CachedLibrary(limit = "3") InteropLibrary lib,
                        @Cached PCallCapiFunction call) {
            assert validPointer(pointer);
            return (int) call.call(NativeCAPISymbol.FUN_READ_CHAR_MEMBER, pointer, offset);
        }
    }

    @ImportStatic(PGuards.class)
    @GenerateUncached
    public abstract static class ReadI16Node extends ReadBaseNode {

        abstract int execute(Object pointer, long offset);

        public final int read(Object pointer, CFields field) {
            assert accepts(field);
            return execute(pointer, field.offset());
        }

        public final boolean accepts(ArgDescriptor desc) {
            return desc.isI16();
        }

        public final int readOffset(Object pointer, long offset) {
            return execute(pointer, offset);
        }

        public final int readArrayElement(Object pointer, long element) {
            return execute(pointer, element * Short.BYTES);
        }

        @Specialization
        static int readLong(long pointer, long offset) {
            assert offset >= 0;
            return UNSAFE.getShort(pointer + offset);
        }

        @Specialization(guards = {"!isLong(pointer)", "lib.isPointer(pointer)"})
        static int readPointer(Object pointer, long offset,
                        @CachedLibrary(limit = "3") InteropLibrary lib) {
            return readLong(asPointer(pointer, lib), offset);
        }

        @Specialization(guards = {"!isLong(pointer)", "!lib.isPointer(pointer)"})
        static int readManaged(Object pointer, long offset,
                        @SuppressWarnings("unused") @CachedLibrary(limit = "3") InteropLibrary lib,
                        @Cached PCallCapiFunction call) {
            assert validPointer(pointer);
            return (int) call.call(NativeCAPISymbol.FUN_READ_SHORT_MEMBER, pointer, offset);
        }
    }

    @ImportStatic(PGuards.class)
    @GenerateUncached
    public abstract static class ReadI32Node extends ReadBaseNode {

        abstract int execute(Object pointer, long offset);

        public final int read(Object pointer, CFields field) {
            assert accepts(field);
            return execute(pointer, field.offset());
        }

        public final boolean accepts(ArgDescriptor desc) {
            return desc.isI32();
        }

        public final int readOffset(Object pointer, long offset) {
            return execute(pointer, offset);
        }

        public final int readArrayElement(Object pointer, long element) {
            return execute(pointer, element * Integer.BYTES);
        }

        public final int readStructArrayElement(Object pointer, long element, CFields field) {
            assert accepts(field);
            return execute(pointer, element * field.struct.size() + field.offset());
        }

        @Specialization
        static int readLong(long pointer, long offset) {
            assert offset >= 0;
            return UNSAFE.getInt(pointer + offset);
        }

        @Specialization(guards = {"!isLong(pointer)", "lib.isPointer(pointer)"})
        static int readPointer(Object pointer, long offset,
                        @CachedLibrary(limit = "3") InteropLibrary lib) {
            return readLong(asPointer(pointer, lib), offset);
        }

        @Specialization(guards = {"!isLong(pointer)", "!lib.isPointer(pointer)"})
        static int readManaged(Object pointer, long offset,
                        @SuppressWarnings("unused") @CachedLibrary(limit = "3") InteropLibrary lib,
                        @Cached PCallCapiFunction call) {
            assert validPointer(pointer);
            return (int) call.call(NativeCAPISymbol.FUN_READ_INT_MEMBER, pointer, offset);
        }
    }

    @ImportStatic(PGuards.class)
    @GenerateUncached
    public abstract static class ReadI64Node extends ReadBaseNode {

        abstract long execute(Object pointer, long offset);

        public final long read(Object pointer, CFields field) {
            assert accepts(field);
            return execute(pointer, field.offset());
        }

        public final long readFromObj(PythonNativeObject self, CFields field) {
            return read(self.getPtr(), field);
        }

        public final boolean accepts(ArgDescriptor desc) {
            return desc.isI64();
        }

        public final long[] readLongArray(Object pointer, int elements) {
            return readLongArray(pointer, elements, 0);
        }

        public final long[] readLongArray(Object pointer, int elements, int offset) {
            long[] result = new long[elements];
            for (int i = 0; i < result.length; i++) {
                result[i] = execute(pointer, (i + offset) * POINTER_SIZE);
            }
            return result;
        }

        public final int[] readLongAsIntArray(Object pointer, int elements) {
            return readLongAsIntArray(pointer, elements, 0);
        }

        public final int[] readLongAsIntArray(Object pointer, int elements, int offset) {
            int[] result = new int[elements];
            for (int i = 0; i < result.length; i++) {
                result[i] = (int) execute(pointer, (i + offset) * POINTER_SIZE);
            }
            return result;
        }

        public final long readArrayElement(Object pointer, long element) {
            return execute(pointer, element * POINTER_SIZE);
        }

        public final long readStructArrayElement(Object pointer, long element, CFields field) {
            assert accepts(field);
            return execute(pointer, element * field.struct.size() + field.offset());
        }

        @Specialization
        static long readLong(long pointer, long offset) {
            assert offset >= 0;
            return UNSAFE.getLong(pointer + offset);
        }

        @Specialization(guards = {"!isLong(pointer)", "lib.isPointer(pointer)"})
        static long readPointer(Object pointer, long offset,
                        @CachedLibrary(limit = "3") InteropLibrary lib) {
            return readLong(asPointer(pointer, lib), offset);
        }

        @Specialization(guards = {"!isLong(pointer)", "!lib.isPointer(pointer)"})
        static long readManaged(Object pointer, long offset,
                        @SuppressWarnings("unused") @CachedLibrary(limit = "3") InteropLibrary lib,
                        @CachedLibrary(limit = "3") InteropLibrary resultLib,
                        @Cached PCallCapiFunction call) {
            assert validPointer(pointer);
            Object result = call.call(NativeCAPISymbol.FUN_READ_LONG_MEMBER, pointer, offset);
            if (result instanceof Long) {
                return (long) result;
            }
            try {
                return resultLib.asLong(result);
            } catch (UnsupportedMessageException e) {
                throw CompilerDirectives.shouldNotReachHere();
            }
        }

        public static ReadI64Node getUncached() {
            return CStructAccessFactory.ReadI64NodeGen.getUncached();
        }

        public static ReadI64Node create() {
            return CStructAccessFactory.ReadI64NodeGen.create();
        }
    }

    /**
     * Note that this node returns a double, not a float, even though it reads only 32 bits.
     */
    @ImportStatic(PGuards.class)
    @GenerateUncached
    public abstract static class ReadFloatNode extends ReadBaseNode {

        abstract double execute(Object pointer, long offset);

        public final double read(Object pointer, CFields field) {
            assert accepts(field);
            return execute(pointer, field.offset());
        }

        public final double readFromObj(PythonNativeObject self, CFields field) {
            return read(self.getPtr(), field);
        }

        public final boolean accepts(ArgDescriptor desc) {
            return desc.isDouble();
        }

        public final double readArrayElement(Object pointer, int element) {
            return execute(pointer, element * Float.BYTES);
        }

        @Specialization
        static double readLong(long pointer, long offset) {
            assert offset >= 0;
            return UNSAFE.getFloat(pointer + offset);
        }

        @Specialization(guards = {"!isLong(pointer)", "lib.isPointer(pointer)"})
        static double readPointer(Object pointer, long offset,
                        @CachedLibrary(limit = "3") InteropLibrary lib) {
            return readLong(asPointer(pointer, lib), offset);
        }

        @Specialization(guards = {"!isLong(pointer)", "!lib.isPointer(pointer)"})
        static double readManaged(Object pointer, long offset,
                        @SuppressWarnings("unused") @CachedLibrary(limit = "3") InteropLibrary lib,
                        @CachedLibrary(limit = "3") InteropLibrary resultLib,
                        @Cached PCallCapiFunction call) {
            assert validPointer(pointer);
            Object result = call.call(NativeCAPISymbol.FUN_READ_FLOAT_MEMBER, pointer, offset);
            if (result instanceof Double) {
                return (double) result;
            }
            try {
                return resultLib.asFloat(result);
            } catch (UnsupportedMessageException e) {
                throw CompilerDirectives.shouldNotReachHere();
            }
        }

        public static ReadFloatNode getUncached() {
            return CStructAccessFactory.ReadFloatNodeGen.getUncached();
        }

        public static ReadFloatNode create() {
            return CStructAccessFactory.ReadFloatNodeGen.create();
        }
    }

    @ImportStatic(PGuards.class)
    @GenerateUncached
    public abstract static class ReadDoubleNode extends ReadBaseNode {

        abstract double execute(Object pointer, long offset);

        public final double read(Object pointer, CFields field) {
            assert accepts(field);
            return execute(pointer, field.offset());
        }

        public final double readFromObj(PythonNativeObject self, CFields field) {
            return read(self.getPtr(), field);
        }

        public final boolean accepts(ArgDescriptor desc) {
            return desc.isDouble();
        }

        public final double readArrayElement(Object pointer, int element) {
            return execute(pointer, element * Double.BYTES);
        }

        @Specialization
        static double readLong(long pointer, long offset) {
            assert offset >= 0;
            return UNSAFE.getDouble(pointer + offset);
        }

        @Specialization(guards = {"!isLong(pointer)", "lib.isPointer(pointer)"})
        static double readPointer(Object pointer, long offset,
                        @CachedLibrary(limit = "3") InteropLibrary lib) {
            return readLong(asPointer(pointer, lib), offset);
        }

        @Specialization(guards = {"!isLong(pointer)", "!lib.isPointer(pointer)"})
        static double readManaged(Object pointer, long offset,
                        @SuppressWarnings("unused") @CachedLibrary(limit = "3") InteropLibrary lib,
                        @CachedLibrary(limit = "3") InteropLibrary resultLib,
                        @Cached PCallCapiFunction call) {
            assert validPointer(pointer);
            Object result = call.call(NativeCAPISymbol.FUN_READ_DOUBLE_MEMBER, pointer, offset);
            if (result instanceof Double) {
                return (double) result;
            }
            try {
                return resultLib.asDouble(result);
            } catch (UnsupportedMessageException e) {
                throw CompilerDirectives.shouldNotReachHere();
            }
        }

        public static ReadDoubleNode getUncached() {
            return CStructAccessFactory.ReadDoubleNodeGen.getUncached();
        }

        public static ReadDoubleNode create() {
            return CStructAccessFactory.ReadDoubleNodeGen.create();
        }
    }

    @ImportStatic(PGuards.class)
    @GenerateUncached
    public abstract static class ReadPointerNode extends ReadBaseNode {

        abstract Object execute(Object pointer, long offset);

        public final Object read(Object pointer, CFields field) {
            assert accepts(field);
            return execute(pointer, field.offset());
        }

        public final Object readFromObj(PythonNativeObject self, CFields field) {
            return read(self.getPtr(), field);
        }

        public final boolean accepts(ArgDescriptor desc) {
            return desc.isPyObjectOrPointer();
        }

        public final Object readArrayElement(Object pointer, long element) {
            return execute(pointer, element * POINTER_SIZE);
        }

        public final Object readStructArrayElement(Object pointer, long element, CFields field) {
            assert accepts(field);
            return execute(pointer, element * field.struct.size() + field.offset());
        }

        @Specialization
        static Object readLong(long pointer, long offset) {
            assert offset >= 0;
            return new PointerContainer(UNSAFE.getLong(pointer + offset));
        }

        @Specialization(guards = {"!isLong(pointer)", "lib.isPointer(pointer)"})
        static Object readPointer(Object pointer, long offset,
                        @CachedLibrary(limit = "3") InteropLibrary lib) {
            return readLong(asPointer(pointer, lib), offset);
        }

        @Specialization(guards = {"!isLong(pointer)", "!lib.isPointer(pointer)"})
        static Object readManaged(Object pointer, long offset,
                        @SuppressWarnings("unused") @CachedLibrary(limit = "3") InteropLibrary lib,
                        @Cached PCallCapiFunction call) {
            assert validPointer(pointer);
            return call.call(NativeCAPISymbol.FUN_READ_POINTER_MEMBER, pointer, offset);
        }

        public static ReadPointerNode getUncached() {
            return ReadPointerNodeGen.getUncached();
        }
    }

    @ImportStatic(PGuards.class)
    @GenerateUncached
    public abstract static class ReadObjectNode extends ReadBaseNode {
        abstract Object execute(Object pointer, long offset);

        public final Object read(Object pointer, CFields field) {
            assert accepts(field);
            return execute(pointer, field.offset());
        }

        public final Object readFromObj(PythonNativeObject self, CFields field) {
            return read(self.getPtr(), field);
        }

        public final boolean accepts(ArgDescriptor desc) {
            return desc.isPyObject();
        }

        public final Object readArrayElement(Object pointer, long element) {
            return execute(pointer, element * POINTER_SIZE);
        }

        public final Object[] readPyObjectArray(Object pointer, int elements) {
            return readPyObjectArray(pointer, elements, 0);
        }

        public final Object[] readPyObjectArray(Object pointer, int elements, int offset) {
            Object[] result = new Object[elements];
            for (int i = 0; i < result.length; i++) {
                result[i] = execute(pointer, (i + offset) * POINTER_SIZE);
            }
            return result;
        }

        public final Object readStructArrayElement(Object pointer, long element, CFields field) {
            assert accepts(field);
            return execute(pointer, element * field.struct.size() + field.offset());
        }

        @Specialization
        static Object readLong(long pointer, long offset,
                        @Shared @Cached NativePtrToPythonNode toPython) {
            assert offset >= 0;
            return toPython.execute(UNSAFE.getLong(pointer + offset), false);
        }

        @Specialization(guards = {"!isLong(pointer)", "lib.isPointer(pointer)"})
        static Object readPointer(Object pointer, long offset,
                        @CachedLibrary(limit = "3") InteropLibrary lib,
                        @Shared @Cached NativePtrToPythonNode toPython) {
            return readLong(asPointer(pointer, lib), offset, toPython);
        }

        @Specialization(guards = {"!isLong(pointer)", "!lib.isPointer(pointer)"})
        static Object readManaged(Object pointer, long offset,
                        @SuppressWarnings("unused") @CachedLibrary(limit = "3") InteropLibrary lib,
                        @Cached PCallCapiFunction call,
                        @Cached NativeToPythonNode toPython) {
            assert validPointer(pointer);
            return toPython.execute(call.call(NativeCAPISymbol.FUN_READ_POINTER_MEMBER, pointer, offset));
        }

        public static ReadObjectNode getUncached() {
            return ReadObjectNodeGen.getUncached();
        }
    }

    @ImportStatic(PGuards.class)
    @GenerateUncached
    public abstract static class ReadObjectStealingNode extends ReadBaseNode {
        abstract Object execute(Object pointer, long offset);

        public final Object read(Object pointer, CFields field) {
            assert accepts(field);
            return execute(pointer, field.offset());
        }

        public final Object readFromObj(PythonNativeObject self, CFields field) {
            return read(self.getPtr(), field);
        }

        public final boolean accepts(ArgDescriptor desc) {
            return desc.isPyObject();
        }

        public final Object readArrayElement(Object pointer, long element) {
            return execute(pointer, element * POINTER_SIZE);
        }

        public final Object[] readPyObjectArray(Object pointer, int elements) {
            return readPyObjectArray(pointer, elements, 0);
        }

        public final Object[] readPyObjectArray(Object pointer, int elements, int offset) {
            Object[] result = new Object[elements];
            for (int i = 0; i < result.length; i++) {
                result[i] = execute(pointer, (i + offset) * POINTER_SIZE);
            }
            return result;
        }

        public final Object readStructArrayElement(Object pointer, long element, CFields field) {
            assert accepts(field);
            return execute(pointer, element * field.struct.size() + field.offset());
        }

        @Specialization
        static Object readLong(long pointer, long offset,
                        @Shared @Cached NativePtrToPythonNode toPython) {
            assert offset >= 0;
            return toPython.execute(UNSAFE.getLong(pointer + offset), true);
        }

        @Specialization(guards = {"!isLong(pointer)", "lib.isPointer(pointer)"})
        static Object readPointer(Object pointer, long offset,
                        @CachedLibrary(limit = "3") InteropLibrary lib,
                        @Shared @Cached NativePtrToPythonNode toPython) {
            return readLong(asPointer(pointer, lib), offset, toPython);
        }

        @Specialization(guards = {"!isLong(pointer)", "!lib.isPointer(pointer)"})
        static Object readManaged(Object pointer, long offset,
                        @SuppressWarnings("unused") @CachedLibrary(limit = "3") InteropLibrary lib,
                        @Cached PCallCapiFunction call,
                        @Cached NativeToPythonStealingNode toPython) {
            assert validPointer(pointer);
            return toPython.execute(call.call(NativeCAPISymbol.FUN_READ_POINTER_MEMBER, pointer, offset));
        }

        public static ReadObjectStealingNode getUncached() {
            return ReadObjectStealingNodeGen.getUncached();
        }
    }

    @ImportStatic(PGuards.class)
    @GenerateUncached
    public abstract static class ReadCharPtrNode extends ReadBaseNode {
        abstract TruffleString execute(Object pointer, long offset);

        public final TruffleString read(Object pointer, CFields field) {
            assert accepts(field);
            return execute(pointer, field.offset());
        }

        public final TruffleString readFromObj(PythonNativeObject self, CFields field) {
            return read(self.getPtr(), field);
        }

        public final boolean accepts(ArgDescriptor desc) {
            return desc.isCharPtr();
        }

        public final TruffleString readArrayElement(Object pointer, long element) {
            return execute(pointer, element * POINTER_SIZE);
        }

        public final TruffleString readStructArrayElement(Object pointer, long element, CFields field) {
            assert accepts(field);
            return execute(pointer, element * field.struct.size() + field.offset());
        }

        @Specialization
        static TruffleString readLong(long pointer, long offset,
                        @Shared @Cached FromCharPointerNode toPython) {
            assert offset >= 0;
            return toPython.execute(UNSAFE.getLong(pointer + offset));
        }

        @Specialization(guards = {"!isLong(pointer)", "lib.isPointer(pointer)"})
        static TruffleString readPointer(Object pointer, long offset,
                        @CachedLibrary(limit = "3") InteropLibrary lib,
                        @Shared @Cached FromCharPointerNode toPython) {
            return readLong(asPointer(pointer, lib), offset, toPython);
        }

        @Specialization(guards = {"!isLong(pointer)", "!lib.isPointer(pointer)"})
        static TruffleString readManaged(Object pointer, long offset,
                        @SuppressWarnings("unused") @CachedLibrary(limit = "3") InteropLibrary lib,
                        @Cached PCallCapiFunction call,
                        @Shared @Cached FromCharPointerNode toPython) {
            assert validPointer(pointer);
            return toPython.execute(call.call(NativeCAPISymbol.FUN_READ_POINTER_MEMBER, pointer, offset));
        }

        public static ReadCharPtrNode getUncached() {
            return ReadCharPtrNodeGen.getUncached();
        }
    }

    @ImportStatic(PGuards.class)
    @GenerateUncached
    public abstract static class WriteByteNode extends Node implements CStructAccessNode {

        abstract void execute(Object pointer, long offset, byte value);

        public final void write(Object pointer, CFields field, byte value) {
            assert accepts(field);
            execute(pointer, field.offset(), value);
        }

        public final void write(Object pointer, byte value) {
            execute(pointer, 0, value);
        }

        public final void writeToObject(PythonNativeObject self, CFields field, byte value) {
            write(self.getPtr(), field, value);
        }

        public final boolean accepts(ArgDescriptor desc) {
            return desc.isI8();
        }

        public final void writeByteArray(Object pointer, byte[] values) {
            writeByteArray(pointer, values, values.length, 0, 0);
        }

        public final void writeByteArray(Object pointer, byte[] values, int length, int sourceOffset, int targetOffset) {
            for (int i = 0; i < length; i++) {
                execute(pointer, (i + targetOffset) * Byte.BYTES, values[i + sourceOffset]);
            }
        }

        public final void writeArrayElement(Object pointer, long element, byte value) {
            execute(pointer, element * Byte.BYTES, value);
        }

        @Specialization
        static void writeLong(long pointer, long offset, byte value) {
            assert offset >= 0;
            UNSAFE.putByte(pointer + offset, value);
        }

        @Specialization(guards = {"!isLong(pointer)", "lib.isPointer(pointer)"})
        static void writePointer(Object pointer, long offset, byte value,
                        @CachedLibrary(limit = "3") InteropLibrary lib) {
            writeLong(asPointer(pointer, lib), offset, value);
        }

        @Specialization(guards = {"!isLong(pointer)", "!lib.isPointer(pointer)"})
        static void writeManaged(Object pointer, long offset, byte value,
                        @SuppressWarnings("unused") @CachedLibrary(limit = "3") InteropLibrary lib,
                        @Cached PCallCapiFunction call) {
            assert validPointer(pointer);
            call.call(NativeCAPISymbol.FUN_WRITE_CHAR_MEMBER, pointer, offset, value);
        }
    }

    @ImportStatic(PGuards.class)
    @GenerateUncached
    public abstract static class WriteDoubleNode extends Node implements CStructAccessNode {

        abstract void execute(Object pointer, long offset, double value);

        public final void write(Object pointer, CFields field, double value) {
            assert accepts(field);
            execute(pointer, field.offset(), value);
        }

        public final void write(Object pointer, double value) {
            execute(pointer, 0, value);
        }

        public final void writeArrayElement(Object pointer, long element, double value) {
            execute(pointer, element * Double.BYTES, value);
        }

        public final boolean accepts(ArgDescriptor desc) {
            return desc.isDouble();
        }

        @Specialization
        static void writeLong(long pointer, long offset, double value) {
            assert offset >= 0;
            UNSAFE.putDouble(pointer + offset, value);
        }

        @Specialization(guards = {"!isLong(pointer)", "lib.isPointer(pointer)"})
        static void writePointer(Object pointer, long offset, double value,
                        @CachedLibrary(limit = "3") InteropLibrary lib) {
            writeLong(asPointer(pointer, lib), offset, value);
        }

        @Specialization(guards = {"!isLong(pointer)", "!lib.isPointer(pointer)"})
        static void writeManaged(Object pointer, long offset, double value,
                        @SuppressWarnings("unused") @CachedLibrary(limit = "3") InteropLibrary lib,
                        @Cached PCallCapiFunction call) {
            assert validPointer(pointer);
            call.call(NativeCAPISymbol.FUN_WRITE_DOUBLE_MEMBER, pointer, offset, value);
        }
    }

    @ImportStatic(PGuards.class)
    @GenerateUncached
    public abstract static class WriteFloatNode extends Node implements CStructAccessNode {

        abstract void execute(Object pointer, long offset, float value);

        public final void write(Object pointer, CFields field, float value) {
            assert accepts(field);
            execute(pointer, field.offset(), value);
        }

        public final void write(Object pointer, float value) {
            execute(pointer, 0, value);
        }

        public final boolean accepts(ArgDescriptor desc) {
            return desc.isFloat();
        }

        @Specialization
        static void writeLong(long pointer, long offset, float value) {
            assert offset >= 0;
            UNSAFE.putFloat(pointer + offset, value);
        }

        @Specialization(guards = {"!isLong(pointer)", "lib.isPointer(pointer)"})
        static void writePointer(Object pointer, long offset, float value,
                        @CachedLibrary(limit = "3") InteropLibrary lib) {
            writeLong(asPointer(pointer, lib), offset, value);
        }

        @Specialization(guards = {"!isLong(pointer)", "!lib.isPointer(pointer)"})
        static void writeManaged(Object pointer, long offset, float value,
                        @SuppressWarnings("unused") @CachedLibrary(limit = "3") InteropLibrary lib,
                        @Cached PCallCapiFunction call) {
            assert validPointer(pointer);
            call.call(NativeCAPISymbol.FUN_WRITE_FLOAT_MEMBER, pointer, offset, value);
        }
    }

    @ImportStatic(PGuards.class)
    @GenerateUncached
    public abstract static class WriteI16Node extends Node implements CStructAccessNode {

        abstract void execute(Object pointer, long offset, short value);

        public final void write(Object pointer, CFields field, short value) {
            assert accepts(field);
            execute(pointer, field.offset(), value);
        }

        public final void write(Object pointer, short value) {
            execute(pointer, 0, value);
        }

        public final boolean accepts(ArgDescriptor desc) {
            return desc.isI16();
        }

        @Specialization
        static void writeLong(long pointer, long offset, short value) {
            assert offset >= 0;
            UNSAFE.putShort(pointer + offset, value);
        }

        @Specialization(guards = {"!isLong(pointer)", "lib.isPointer(pointer)"})
        static void writePointer(Object pointer, long offset, short value,
                        @CachedLibrary(limit = "3") InteropLibrary lib) {
            writeLong(asPointer(pointer, lib), offset, value);
        }

        @Specialization(guards = {"!isLong(pointer)", "!lib.isPointer(pointer)"})
        static void writeManaged(Object pointer, long offset, short value,
                        @SuppressWarnings("unused") @CachedLibrary(limit = "3") InteropLibrary lib,
                        @Cached PCallCapiFunction call) {
            assert validPointer(pointer);
            call.call(NativeCAPISymbol.FUN_WRITE_SHORT_MEMBER, pointer, offset, value);
        }
    }

    @ImportStatic(PGuards.class)
    @GenerateUncached
    public abstract static class WriteIntNode extends Node implements CStructAccessNode {

        abstract void execute(Object pointer, long offset, int value);

        public final void write(Object pointer, CFields field, int value) {
            assert accepts(field);
            execute(pointer, field.offset(), value);
        }

        public final void write(Object pointer, int value) {
            execute(pointer, 0, value);
        }

        public final boolean accepts(ArgDescriptor desc) {
            return desc.isI32();
        }

        public final void writeArray(Object pointer, int[] values) {
            writeArray(pointer, values, values.length, 0, 0);
        }

        public final void writeArray(Object pointer, int[] values, int length, int sourceOffset, int targetOffset) {
            for (int i = 0; i < length; i++) {
                execute(pointer, (i + targetOffset) * Integer.BYTES, values[i + sourceOffset]);
            }
        }

        public final void writeArrayElement(Object pointer, long element, int value) {
            execute(pointer, element * Integer.BYTES, value);
        }

        @Specialization
        static void writeLong(long pointer, long offset, int value) {
            assert offset >= 0;
            UNSAFE.putInt(pointer + offset, value);
        }

        @Specialization(guards = {"!isLong(pointer)", "lib.isPointer(pointer)"})
        static void writePointer(Object pointer, long offset, int value,
                        @CachedLibrary(limit = "3") InteropLibrary lib) {
            writeLong(asPointer(pointer, lib), offset, value);
        }

        @Specialization(guards = {"!isLong(pointer)", "!lib.isPointer(pointer)"})
        static void writeManaged(Object pointer, long offset, int value,
                        @SuppressWarnings("unused") @CachedLibrary(limit = "3") InteropLibrary lib,
                        @Cached PCallCapiFunction call) {
            assert validPointer(pointer);
            call.call(NativeCAPISymbol.FUN_WRITE_INT_MEMBER, pointer, offset, value);
        }
    }

    @ImportStatic(PGuards.class)
    @GenerateUncached
    public abstract static class WriteLongNode extends Node implements CStructAccessNode {

        abstract void execute(Object pointer, long offset, long value);

        public final void write(Object pointer, CFields field, long value) {
            assert accepts(field);
            execute(pointer, field.offset(), value);
        }

        public final void writeToObject(PythonNativeObject self, CFields field, long value) {
            write(self.getPtr(), field, value);
        }

        public final void write(Object pointer, long value) {
            execute(pointer, 0, value);
        }

        public final void writeLongArray(Object pointer, long[] values) {
            writeLongArray(pointer, values, values.length, 0, 0);
        }

        public final void writeLongArray(Object pointer, long[] values, int length, int sourceOffset, int targetOffset) {
            for (int i = 0; i < length; i++) {
                execute(pointer, (i + targetOffset) * Long.BYTES, values[i + sourceOffset]);
            }
        }

        public final boolean accepts(ArgDescriptor desc) {
            return desc.isI64();
        }

        @Specialization
        static void writeLong(long pointer, long offset, long value) {
            assert offset >= 0;
            UNSAFE.putLong(pointer + offset, value);
        }

        @Specialization(guards = {"!isLong(pointer)", "lib.isPointer(pointer)"})
        static void writePointer(Object pointer, long offset, long value,
                        @CachedLibrary(limit = "3") InteropLibrary lib) {
            writeLong(asPointer(pointer, lib), offset, value);
        }

        @Specialization(guards = {"!isLong(pointer)", "!lib.isPointer(pointer)"})
        static void writeManaged(Object pointer, long offset, long value,
                        @SuppressWarnings("unused") @CachedLibrary(limit = "3") InteropLibrary lib,
                        @Cached PCallCapiFunction call) {
            assert validPointer(pointer);
            call.call(NativeCAPISymbol.FUN_WRITE_LONG_MEMBER, pointer, offset, value);
        }
    }

    @ImportStatic(PGuards.class)
    @GenerateUncached
    public abstract static class WritePointerNode extends Node implements CStructAccessNode {

        abstract void execute(Object pointer, long offset, Object value);

        public final void write(Object pointer, CFields field, Object value) {
            assert accepts(field);
            execute(pointer, field.offset(), value);
        }

        public final void write(Object pointer, Object value) {
            execute(pointer, 0, value);
        }

        public final boolean accepts(ArgDescriptor desc) {
            return desc.isPyObjectOrPointer();
        }

        @Specialization
        static void writeLong(long pointer, long offset, Object value,
                        @CachedLibrary(limit = "3") InteropLibrary valueLib) {
            assert offset >= 0;
            UNSAFE.putLong(pointer + offset, PythonNativeWrapper.coerceToLong(value, valueLib));
        }

        @Specialization(guards = {"!isLong(pointer)", "lib.isPointer(pointer)"})
        static void writePointer(Object pointer, long offset, Object value,
                        @CachedLibrary(limit = "3") InteropLibrary lib,
                        @CachedLibrary(limit = "3") InteropLibrary valueLib) {
            writeLong(asPointer(pointer, lib), offset, value, valueLib);
        }

        @Specialization(guards = {"!isLong(pointer)", "!lib.isPointer(pointer)"})
        static void writeManaged(Object pointer, long offset, Object value,
                        @SuppressWarnings("unused") @CachedLibrary(limit = "3") InteropLibrary lib,
                        @Cached PCallCapiFunction call) {
            assert validPointer(pointer);
            call.call(NativeCAPISymbol.FUN_WRITE_LONG_MEMBER, pointer, offset, value);
        }
    }

    @ImportStatic(PGuards.class)
    @GenerateUncached
    public abstract static class WriteObjectNode extends Node implements CStructAccessNode {

        abstract void execute(Object pointer, long offset, Object value);

        public final void write(Object pointer, CFields field, Object value) {
            assert accepts(field);
            execute(pointer, field.offset(), value);
        }

        public final void write(Object pointer, Object value) {
            execute(pointer, 0, value);
        }

        public final boolean accepts(ArgDescriptor desc) {
            return desc.isPyObject();
        }

        public final void writeArray(Object pointer, Object[] values) {
            writeArray(pointer, values, values.length, 0, 0);
        }

        public final void writeArray(Object pointer, Object[] values, int length, int sourceOffset, int targetOffset) {
            for (int i = 0; i < length; i++) {
                execute(pointer, (i + targetOffset) * POINTER_SIZE, values[i + sourceOffset]);
            }
        }

        public final void writeArrayElement(Object pointer, long element, Object value) {
            execute(pointer, element * POINTER_SIZE, value);
        }

        @Specialization
        static void writeLong(long pointer, long offset, Object value,
                        @Shared @Cached PythonToNativeNode toNative) {
            assert offset >= 0;
            UNSAFE.putLong(pointer + offset, toNative.executeLong(value));
        }

        @Specialization(guards = {"!isLong(pointer)", "lib.isPointer(pointer)"})
        static void writePointer(Object pointer, long offset, Object value,
                        @Shared @Cached PythonToNativeNode toNative,
                        @CachedLibrary(limit = "3") InteropLibrary lib) {
            writeLong(asPointer(pointer, lib), offset, value, toNative);
        }

        @Specialization(guards = {"!isLong(pointer)", "!lib.isPointer(pointer)"})
        static void writeManaged(Object pointer, long offset, Object value,
                        @SuppressWarnings("unused") @CachedLibrary(limit = "3") InteropLibrary lib,
                        @Shared @Cached PythonToNativeNode toNative,
                        @Cached PCallCapiFunction call) {
            assert validPointer(pointer);
            call.call(NativeCAPISymbol.FUN_WRITE_LONG_MEMBER, pointer, offset, toNative.executeLong(value));
        }
    }

    @ImportStatic(PGuards.class)
    @GenerateUncached
    public abstract static class WriteObjectNewRefNode extends Node implements CStructAccessNode {

        abstract void execute(Object pointer, long offset, Object value);

        public final void write(Object pointer, CFields field, Object value) {
            assert accepts(field);
            execute(pointer, field.offset(), value);
        }

        public final void writeToObject(PythonNativeObject self, CFields field, Object value) {
            write(self.getPtr(), field, value);
        }

        public final void write(Object pointer, Object value) {
            execute(pointer, 0, value);
        }

        public final boolean accepts(ArgDescriptor desc) {
            return desc.isPyObject();
        }

        public final void writeArray(Object pointer, Object[] values) {
            writeArray(pointer, values, values.length, 0, 0);
        }

        public final void writeArray(Object pointer, Object[] values, int length, int sourceOffset, int targetOffset) {
            for (int i = 0; i < length; i++) {
                execute(pointer, (i + targetOffset) * POINTER_SIZE, values[i + sourceOffset]);
            }
        }

        public final void writeArrayElement(Object pointer, long element, Object value) {
            execute(pointer, element * POINTER_SIZE, value);
        }

        @Specialization
        static void writeLong(long pointer, long offset, Object value,
                        @Shared @Cached NativePtrToPythonNode toPython,
                        @Shared @Cached PythonToNativeNewRefNode toNative,
                        @CachedLibrary(limit = "3") InteropLibrary valueLib) {
            assert offset >= 0;
            long old = UNSAFE.getLong(pointer + offset);
            if (old != 0) {
                toPython.execute(old, true);
            }
            UNSAFE.putLong(pointer + offset, PythonNativeWrapper.coerceToLong(toNative.execute(value), valueLib));
        }

        @Specialization(guards = {"!isLong(pointer)", "lib.isPointer(pointer)"})
        static void writePointer(Object pointer, long offset, Object value,
                        @Shared @Cached NativePtrToPythonNode toPython,
                        @Shared @Cached PythonToNativeNewRefNode toNative,
                        @CachedLibrary(limit = "3") InteropLibrary lib,
                        @CachedLibrary(limit = "3") InteropLibrary valueLib) {
            writeLong(asPointer(pointer, lib), offset, value, toPython, toNative, valueLib);
        }

        @Specialization(guards = {"!isLong(pointer)", "!lib.isPointer(pointer)"})
        static void writeManaged(Object pointer, long offset, Object value,
                        @SuppressWarnings("unused") @CachedLibrary(limit = "3") InteropLibrary lib,
                        @Shared @Cached PythonToNativeNewRefNode toNative,
                        @Cached PCallCapiFunction call) {
            assert validPointer(pointer);
            call.call(NativeCAPISymbol.FUN_WRITE_LONG_MEMBER, pointer, offset, toNative.execute(value));
        }
    }

    private interface CStructAccessNode {
        boolean accepts(ArgDescriptor desc);

        default boolean accepts(CFields field) {
            return accepts(field.type);
        }
    }

    public static final long POINTER_SIZE = 8;
    private static final Unsafe UNSAFE = PythonUtils.initUnsafe();

    static long asPointer(Object value, InteropLibrary lib) {
        assert validPointer(value);
        try {
            return lib.asPointer(value);
        } catch (final UnsupportedMessageException e) {
            throw CompilerDirectives.shouldNotReachHere(e);
        }
    }

}
