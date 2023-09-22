/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.builtins.objects.cext.hpy.jni;

import static com.oracle.graal.python.builtins.objects.cext.common.CArrayWrappers.UNSAFE;
import static com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyHandle.NULL_HANDLE_DELEGATE;
import static com.oracle.graal.python.builtins.objects.cext.hpy.jni.GraalHPyJNIContext.coerceToPointer;
import static com.oracle.graal.python.util.PythonUtils.TS_ENCODING;

import com.oracle.graal.python.builtins.objects.cext.common.NativePointer;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyBoxing;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyCAccess.AllocateNode;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyCAccess.BulkFreeHandleReferencesNode;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyCAccess.FreeNode;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyCAccess.GetElementPtrNode;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyCAccess.IsNullNode;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyCAccess.ReadDoubleNode;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyCAccess.ReadFloatNode;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyCAccess.ReadGenericNode;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyCAccess.ReadHPyArrayNode;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyCAccess.ReadHPyFieldNode;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyCAccess.ReadHPyNode;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyCAccess.ReadI32Node;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyCAccess.ReadI64Node;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyCAccess.ReadI8ArrayNode;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyCAccess.ReadPointerNode;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyCAccess.WriteDoubleNode;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyCAccess.WriteGenericNode;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyCAccess.WriteHPyFieldNode;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyCAccess.WriteHPyNode;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyCAccess.WriteI32Node;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyCAccess.WriteI64Node;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyCAccess.WritePointerNode;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyCAccess.WriteSizeTNode;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContext;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContext.GraalHPyHandleReference;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyData;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyNodes.HPyFromCharPointerNode;
import com.oracle.graal.python.builtins.objects.cext.hpy.HPyContextSignatureType;
import com.oracle.graal.python.builtins.objects.ints.PInt;
import com.oracle.graal.python.builtins.objects.object.PythonObject;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Exclusive;
import com.oracle.truffle.api.dsl.GenerateInline;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.NodeCost;
import com.oracle.truffle.api.profiles.InlinedConditionProfile;
import com.oracle.truffle.api.strings.TruffleString;
import com.oracle.truffle.api.strings.TruffleString.Encoding;

import sun.misc.Unsafe;

abstract class GraalHPyJNINodes {

    private GraalHPyJNINodes() {
    }

    static final class UnsafeIsNullNode extends IsNullNode {

        static final UnsafeIsNullNode UNCACHED = new UnsafeIsNullNode();

        private UnsafeIsNullNode() {
        }

        @Override
        protected boolean execute(@SuppressWarnings("unused") GraalHPyContext ctx, Object pointer) {
            return coerceToPointer(pointer) == 0;
        }

        @Override
        public NodeCost getCost() {
            return NodeCost.POLYMORPHIC;
        }

        @Override
        public boolean isAdoptable() {
            return false;
        }
    }

    static final class UnsafeAllocateNode extends AllocateNode {

        static final UnsafeAllocateNode UNCACHED = new UnsafeAllocateNode();

        private UnsafeAllocateNode() {
        }

        @Override
        protected Object execute(@SuppressWarnings("unused") GraalHPyContext ctx, long size, boolean zero) {
            long result = UNSAFE.allocateMemory(size);
            if (zero) {
                UNSAFE.setMemory(result, size, (byte) 0);
            }
            return result;
        }

        @Override
        public NodeCost getCost() {
            return NodeCost.POLYMORPHIC;
        }

        @Override
        public boolean isAdoptable() {
            return false;
        }
    }

    static final class UnsafeFreeNode extends FreeNode {

        static final UnsafeFreeNode UNCACHED = new UnsafeFreeNode();

        private UnsafeFreeNode() {
        }

        @Override
        protected void execute(@SuppressWarnings("unused") GraalHPyContext ctx, Object pointer) {
            UNSAFE.freeMemory(coerceToPointer(pointer));
        }

        @Override
        public NodeCost getCost() {
            return NodeCost.POLYMORPHIC;
        }

        @Override
        public boolean isAdoptable() {
            return false;
        }
    }

    static final class UnsafeBulkFreeHandleReferencesNode extends BulkFreeHandleReferencesNode {

        private static final int BULK_CAPACITY = 1024;

        static final UnsafeBulkFreeHandleReferencesNode UNCACHED = new UnsafeBulkFreeHandleReferencesNode();

        private UnsafeBulkFreeHandleReferencesNode() {
        }

        @Override
        @TruffleBoundary
        protected void execute(@SuppressWarnings("unused") GraalHPyContext ctx, GraalHPyHandleReference[] references) {
            long[] nativeSpacePtrs = new long[BULK_CAPACITY];
            long[] destroyFuncPtrs = new long[BULK_CAPACITY];
            int i = 0;
            for (GraalHPyHandleReference ref : references) {
                long destroyFunPtr = coerceToPointer(ref.getDestroyFunc());
                long nativeSpacePtr = coerceToPointer(ref.getNativeSpace());
                if (destroyFunPtr == 0) {
                    // in this case, we can just use 'free'
                    UNSAFE.freeMemory(nativeSpacePtr);
                } else {
                    if (i >= BULK_CAPACITY) {
                        GraalHPyJNIContext.bulkFreeNativeSpace(nativeSpacePtrs, destroyFuncPtrs, BULK_CAPACITY);
                        i = 0;
                    }
                    destroyFuncPtrs[i] = destroyFunPtr;
                    nativeSpacePtrs[i] = nativeSpacePtr;
                    i++;
                }
            }
            if (i > 0) {
                GraalHPyJNIContext.bulkFreeNativeSpace(nativeSpacePtrs, destroyFuncPtrs, i);
            }
        }

        @Override
        public NodeCost getCost() {
            return NodeCost.POLYMORPHIC;
        }

        @Override
        public boolean isAdoptable() {
            return false;
        }
    }

    static final class UnsafeGetElementPtrNode extends GetElementPtrNode {
        static final UnsafeGetElementPtrNode UNCACHED = new UnsafeGetElementPtrNode();

        private UnsafeGetElementPtrNode() {
        }

        @Override
        public Object execute(@SuppressWarnings("unused") GraalHPyContext ctx, Object pointer, long offset) {
            return coerceToPointer(pointer) + offset;
        }

        @Override
        public NodeCost getCost() {
            return NodeCost.MONOMORPHIC;
        }

        @Override
        public boolean isAdoptable() {
            return false;
        }
    }

    static final class UnsafeReadI8ArrayNode extends ReadI8ArrayNode {

        static final UnsafeReadI8ArrayNode UNCACHED = new UnsafeReadI8ArrayNode();

        private UnsafeReadI8ArrayNode() {
        }

        @Override
        protected byte[] execute(GraalHPyContext ctx, Object pointer, long offset, long n) {
            if (!PInt.isIntRange(n)) {
                throw CompilerDirectives.shouldNotReachHere("cannot fit long into int");
            }
            byte[] result = new byte[(int) n];
            long ptr = coerceToPointer(pointer) + offset;
            UNSAFE.copyMemory(null, ptr, result, Unsafe.ARRAY_BYTE_BASE_OFFSET, n);
            return result;
        }

        @Override
        public NodeCost getCost() {
            return NodeCost.POLYMORPHIC;
        }

        @Override
        public boolean isAdoptable() {
            return false;
        }
    }

    static final class UnsafeReadHPyNode extends ReadHPyNode {

        static final UnsafeReadHPyNode UNCACHED = new UnsafeReadHPyNode();

        private UnsafeReadHPyNode() {
        }

        @Override
        protected Object execute(GraalHPyContext ctx, Object pointer, long offset, boolean close) {
            assert ctx.getCTypeSize(HPyContextSignatureType.HPy) == UNSAFE.addressSize();
            long bits = UNSAFE.getAddress(coerceToPointer(pointer) + offset);
            Object result = ctx.bitsAsPythonObject(bits);
            if (close && GraalHPyBoxing.isBoxedHandle(bits)) {
                ctx.releaseHPyHandleForObject(GraalHPyBoxing.unboxHandle(bits));
            }
            return result;
        }

        @Override
        public NodeCost getCost() {
            return NodeCost.POLYMORPHIC;
        }

        @Override
        public boolean isAdoptable() {
            return false;
        }
    }

    static final class UnsafeReadHPyFieldNode extends ReadHPyFieldNode {

        static final UnsafeReadHPyFieldNode UNCACHED = new UnsafeReadHPyFieldNode();

        private UnsafeReadHPyFieldNode() {
        }

        @Override
        protected Object execute(GraalHPyContext ctx, PythonObject owner, Object pointer, long offset, boolean close) {
            assert ctx.getCTypeSize(HPyContextSignatureType.HPy) == UNSAFE.addressSize();
            long bits = UNSAFE.getAddress(coerceToPointer(pointer) + offset);
            if (!PInt.isIntRange(bits)) {
                throw CompilerDirectives.shouldNotReachHere();
            }
            int idx = (int) bits;
            if (idx == 0) {
                return NULL_HANDLE_DELEGATE;
            }
            return GraalHPyData.getHPyField(owner, idx);
        }

        @Override
        public NodeCost getCost() {
            return NodeCost.POLYMORPHIC;
        }

        @Override
        public boolean isAdoptable() {
            return false;
        }
    }

    static final class UnsafeReadHPyArrayNode extends ReadHPyArrayNode {

        static final UnsafeReadHPyArrayNode UNCACHED = new UnsafeReadHPyArrayNode();

        private UnsafeReadHPyArrayNode() {
        }

        @Override
        protected Object[] execute(GraalHPyContext ctx, Object pointer, long offset, long n) {
            if (!PInt.isIntRange(n)) {
                throw CompilerDirectives.shouldNotReachHere("cannot fit long into int");
            }
            long basePtr = coerceToPointer(pointer);
            Object[] result = new Object[(int) n];
            for (int i = 0; i < result.length; i++) {
                result[i] = ctx.bitsAsPythonObject(UNSAFE.getAddress(basePtr + (i + offset) * UNSAFE.addressSize()));
            }
            return result;
        }

        @Override
        public NodeCost getCost() {
            return NodeCost.POLYMORPHIC;
        }

        @Override
        public boolean isAdoptable() {
            return false;
        }
    }

    static final class UnsafeReadI32Node extends ReadI32Node {

        static final UnsafeReadI32Node UNCACHED = new UnsafeReadI32Node();

        private UnsafeReadI32Node() {
        }

        @Override
        protected int execute(@SuppressWarnings("unused") GraalHPyContext ctx, Object pointer, long offset) {
            return UNSAFE.getInt(coerceToPointer(pointer) + offset);
        }

        @Override
        public NodeCost getCost() {
            return NodeCost.POLYMORPHIC;
        }

        @Override
        public boolean isAdoptable() {
            return false;
        }
    }

    static final class UnsafeReadI64Node extends ReadI64Node {

        static final UnsafeReadI64Node UNCACHED = new UnsafeReadI64Node();

        private UnsafeReadI64Node() {
        }

        @Override
        protected long execute(@SuppressWarnings("unused") GraalHPyContext ctx, Object pointer, long offset) {
            return UNSAFE.getLong(coerceToPointer(pointer) + offset);
        }

        @Override
        public NodeCost getCost() {
            return NodeCost.POLYMORPHIC;
        }

        @Override
        public boolean isAdoptable() {
            return false;
        }
    }

    static final class UnsafeReadFloatNode extends ReadFloatNode {

        static final UnsafeReadFloatNode UNCACHED = new UnsafeReadFloatNode();

        private UnsafeReadFloatNode() {
        }

        @Override
        protected double execute(@SuppressWarnings("unused") GraalHPyContext ctx, Object pointer, long offset) {
            return UNSAFE.getFloat(coerceToPointer(pointer) + offset);
        }

        @Override
        public NodeCost getCost() {
            return NodeCost.POLYMORPHIC;
        }

        @Override
        public boolean isAdoptable() {
            return false;
        }
    }

    static final class UnsafeReadDoubleNode extends ReadDoubleNode {

        static final UnsafeReadDoubleNode UNCACHED = new UnsafeReadDoubleNode();

        private UnsafeReadDoubleNode() {
        }

        @Override
        protected double execute(@SuppressWarnings("unused") GraalHPyContext ctx, Object pointer, long offset) {
            return UNSAFE.getDouble(coerceToPointer(pointer) + offset);
        }

        @Override
        public NodeCost getCost() {
            return NodeCost.POLYMORPHIC;
        }

        @Override
        public boolean isAdoptable() {
            return false;
        }
    }

    static final class UnsafeReadPointerNode extends ReadPointerNode {

        static final UnsafeReadPointerNode UNCACHED = new UnsafeReadPointerNode();

        private UnsafeReadPointerNode() {
        }

        @Override
        protected Object execute(@SuppressWarnings("unused") GraalHPyContext ctx, Object pointer, long offset) {
            return UNSAFE.getAddress(coerceToPointer(pointer) + offset);
        }

        @Override
        public NodeCost getCost() {
            return NodeCost.POLYMORPHIC;
        }

        @Override
        public boolean isAdoptable() {
            return false;
        }
    }

    static final class UnsafeReadGenericNode extends ReadGenericNode {

        static final UnsafeReadGenericNode UNCACHED = new UnsafeReadGenericNode();

        private UnsafeReadGenericNode() {
        }

        @Override
        protected Object execute(@SuppressWarnings("unused") GraalHPyContext ctx, Object pointer, long offset, HPyContextSignatureType type) {
            long addr = coerceToPointer(pointer) + offset;
            switch (type) {
                case Int8_t, Uint8_t, Bool:
                    return UNSAFE.getByte(addr);
                case Int16_t, Uint16_t:
                    return UNSAFE.getShort(addr);
                case Int32_t, Uint32_t:
                    return UNSAFE.getInt(addr);
                case Int64_t, Uint64_t:
                    return UNSAFE.getLong(addr);
                case CFloat:
                    return UNSAFE.getFloat(addr);
                case CDouble:
                    return UNSAFE.getDouble(addr);
                case HPyContextPtr, VoidPtr, VoidPtrPtr, HPyPtr, ConstHPyPtr, Wchar_tPtr, ConstWchar_tPtr, CharPtr, ConstCharPtr, DataPtr, DataPtrPtr, Cpy_PyObjectPtr, HPyModuleDefPtr,
                                HPyType_SpecPtr, HPyType_SpecParamPtr, HPyDefPtr, HPyFieldPtr, HPyGlobalPtr, HPyCapsule_DestructorPtr, PyType_SlotPtr:
                    return UNSAFE.getAddress(addr);
                default:
                    int size = ctx.getCTypeSize(type);
                    switch (size) {
                        case 1:
                            return UNSAFE.getByte(addr);
                        case 2:
                            return UNSAFE.getShort(addr);
                        case 4:
                            return UNSAFE.getInt(addr);
                        case 8:
                            return UNSAFE.getLong(addr);
                    }

                    throw CompilerDirectives.shouldNotReachHere();
            }
        }

        @Override
        protected int executeInt(@SuppressWarnings("unused") GraalHPyContext ctx, Object pointer, long offset, HPyContextSignatureType type) {
            return (int) executeLong(ctx, pointer, offset, type);
        }

        @Override
        protected long executeLong(@SuppressWarnings("unused") GraalHPyContext ctx, Object pointer, long offset, HPyContextSignatureType type) {
            long addr = coerceToPointer(pointer) + offset;
            int size;
            switch (type) {
                case Int8_t, Uint8_t, Bool:
                    size = 1;
                    break;
                case Int16_t, Uint16_t:
                    size = 2;
                    break;
                case Int32_t, Uint32_t:
                    size = 4;
                    break;
                case Int64_t, Uint64_t:
                    size = 8;
                    break;
                default:
                    size = ctx.getCTypeSize(type);
                    break;
            }
            switch (size) {
                case 1:
                    return UNSAFE.getByte(addr);
                case 2:
                    return UNSAFE.getShort(addr);
                case 4:
                    return UNSAFE.getInt(addr);
                case 8:
                    return UNSAFE.getLong(addr);
                default:
                    throw CompilerDirectives.shouldNotReachHere();
            }
        }

        @Override
        public NodeCost getCost() {
            return NodeCost.POLYMORPHIC;
        }

        @Override
        public boolean isAdoptable() {
            return false;
        }
    }

    static final class UnsafeWriteI32Node extends WriteI32Node {

        static final UnsafeWriteI32Node UNCACHED = new UnsafeWriteI32Node();

        private UnsafeWriteI32Node() {
        }

        @Override
        protected void execute(@SuppressWarnings("unused") GraalHPyContext ctx, Object pointer, long offset, int value) {
            UNSAFE.putInt(coerceToPointer(pointer) + offset, value);
        }

        @Override
        public NodeCost getCost() {
            return NodeCost.POLYMORPHIC;
        }

        @Override
        public boolean isAdoptable() {
            return false;
        }
    }

    static final class UnsafeWriteI64Node extends WriteI64Node {

        static final UnsafeWriteI64Node UNCACHED = new UnsafeWriteI64Node();

        private UnsafeWriteI64Node() {
        }

        @Override
        protected void execute(@SuppressWarnings("unused") GraalHPyContext ctx, Object pointer, long offset, long value) {
            UNSAFE.putLong(coerceToPointer(pointer) + offset, value);
        }

        @Override
        public NodeCost getCost() {
            return NodeCost.POLYMORPHIC;
        }

        @Override
        public boolean isAdoptable() {
            return false;
        }
    }

    static final class UnsafeWriteDoubleNode extends WriteDoubleNode {

        static final UnsafeWriteDoubleNode UNCACHED = new UnsafeWriteDoubleNode();

        private UnsafeWriteDoubleNode() {
        }

        @Override
        protected void execute(@SuppressWarnings("unused") GraalHPyContext ctx, Object pointer, long offset, double value) {
            UNSAFE.putDouble(coerceToPointer(pointer) + offset, value);
        }

        @Override
        public NodeCost getCost() {
            return NodeCost.POLYMORPHIC;
        }

        @Override
        public boolean isAdoptable() {
            return false;
        }
    }

    static final class UnsafeWriteGenericNode extends WriteGenericNode {

        static final UnsafeWriteGenericNode UNCACHED = new UnsafeWriteGenericNode();

        private UnsafeWriteGenericNode() {
        }

        @Override
        protected void execute(GraalHPyContext ctx, Object pointer, long offset, HPyContextSignatureType type, long value) {
            long addr = coerceToPointer(pointer) + offset;
            int size;
            switch (type) {
                case Int8_t, Uint8_t, Bool:
                    size = 1;
                    break;
                case Int16_t, Uint16_t:
                    size = 2;
                    break;
                case Int32_t, Uint32_t:
                    size = 4;
                    break;
                case Int64_t, Uint64_t:
                    size = 8;
                    break;
                default:
                    size = ctx.getCTypeSize(type);
                    break;
            }
            switch (size) {
                case 1:
                    UNSAFE.putByte(addr, (byte) value);
                    break;
                case 2:
                    UNSAFE.putShort(addr, (short) value);
                    break;
                case 4:
                    UNSAFE.putInt(addr, (int) value);
                    break;
                case 8:
                    UNSAFE.putLong(addr, value);
                    break;
                default:
                    throw CompilerDirectives.shouldNotReachHere();
            }
        }

        @Override
        protected void execute(GraalHPyContext ctx, Object pointer, long offset, HPyContextSignatureType type, Object value) {
            long addr = coerceToPointer(pointer) + offset;
            switch (type) {
                case Int8_t, Uint8_t, Bool:
                    UNSAFE.putByte(addr, (byte) value);
                    break;
                case Int16_t, Uint16_t:
                    UNSAFE.putShort(addr, (short) value);
                    break;
                case Int32_t, Uint32_t:
                    UNSAFE.putInt(addr, (int) value);
                    break;
                case Int64_t, Uint64_t:
                    UNSAFE.putLong(addr, (long) value);
                    break;
                case CFloat:
                    UNSAFE.putFloat(addr, (float) value);
                    break;
                case CDouble:
                    UNSAFE.putDouble(addr, (double) value);
                    break;
                case HPyContextPtr, VoidPtr, VoidPtrPtr, HPyPtr, ConstHPyPtr, Wchar_tPtr, ConstWchar_tPtr, CharPtr, ConstCharPtr, DataPtr, DataPtrPtr, Cpy_PyObjectPtr, HPyModuleDefPtr,
                                HPyType_SpecPtr, HPyType_SpecParamPtr, HPyDefPtr, HPyFieldPtr, HPyGlobalPtr, HPyCapsule_DestructorPtr, PyType_SlotPtr:
                    UNSAFE.putAddress(addr, (long) value);
                    break;
                default:
                    int size = ctx.getCTypeSize(type);
                    switch (size) {
                        case 1:
                            UNSAFE.putByte(addr, (byte) value);
                            break;
                        case 2:
                            UNSAFE.putShort(addr, (short) value);
                            break;
                        case 4:
                            UNSAFE.putInt(addr, (int) value);
                            break;
                        case 8:
                            UNSAFE.putLong(addr, (long) value);
                            break;
                        default:
                            throw CompilerDirectives.shouldNotReachHere();
                    }
            }
        }

        @Override
        public NodeCost getCost() {
            return NodeCost.POLYMORPHIC;
        }

        @Override
        public boolean isAdoptable() {
            return false;
        }
    }

    static final class UnsafeWriteHPyNode extends WriteHPyNode {

        static final UnsafeWriteHPyNode UNCACHED = new UnsafeWriteHPyNode();

        private UnsafeWriteHPyNode() {
        }

        @Override
        protected void execute(GraalHPyContext ctx, Object pointer, long offset, Object value) {
            long bits = ctx.pythonObjectAsBits(value);
            UNSAFE.putAddress(coerceToPointer(pointer) + offset, bits);
        }

        @Override
        public NodeCost getCost() {
            return NodeCost.POLYMORPHIC;
        }

        @Override
        public boolean isAdoptable() {
            return false;
        }
    }

    static final class UnsafeWriteHPyFieldNode extends WriteHPyFieldNode {

        static final UnsafeWriteHPyFieldNode UNCACHED = new UnsafeWriteHPyFieldNode();

        private UnsafeWriteHPyFieldNode() {
        }

        @Override
        protected void execute(GraalHPyContext ctx, PythonObject owner, Object pointer, long offset, Object value) {
            long address = coerceToPointer(pointer) + offset;
            long loldValue = UNSAFE.getAddress(address);
            if (!PInt.isIntRange(loldValue)) {
                throw CompilerDirectives.shouldNotReachHere();
            }
            int oldValue = (int) loldValue;
            // TODO: (tfel) do not actually allocate the index / free the existing one when
            // value can be stored as tagged handle
            if (value == NULL_HANDLE_DELEGATE && oldValue == 0) {
                // assigning HPy_NULL to a field that already holds HPy_NULL, nothing to do
            } else {
                int newValue = GraalHPyData.setHPyField(owner, value, oldValue);
                if (oldValue != newValue) {
                    UNSAFE.putAddress(address, newValue);
                }
            }
        }

        @Override
        public NodeCost getCost() {
            return NodeCost.POLYMORPHIC;
        }

        @Override
        public boolean isAdoptable() {
            return false;
        }
    }

    static final class UnsafeWritePointerNode extends WritePointerNode {

        static final UnsafeWritePointerNode UNCACHED = new UnsafeWritePointerNode();

        private UnsafeWritePointerNode() {
        }

        @Override
        protected void execute(GraalHPyContext ctx, Object basePointer, long offset, Object valuePointer) {
            UNSAFE.putAddress(coerceToPointer(basePointer) + offset, coerceToPointer(valuePointer));
        }

        @Override
        public NodeCost getCost() {
            return NodeCost.POLYMORPHIC;
        }

        @Override
        public boolean isAdoptable() {
            return false;
        }
    }

    static final class UnsafeWriteSizeTNode extends WriteSizeTNode {

        static final UnsafeWriteSizeTNode UNCACHED = new UnsafeWriteSizeTNode();

        private UnsafeWriteSizeTNode() {
        }

        @Override
        protected void execute(GraalHPyContext ctx, Object basePointer, long offset, long valuePointer) {
            UNSAFE.putAddress(coerceToPointer(basePointer) + offset, valuePointer);
        }

        @Override
        public NodeCost getCost() {
            return NodeCost.POLYMORPHIC;
        }

        @Override
        public boolean isAdoptable() {
            return false;
        }
    }

    @GenerateUncached
    @GenerateInline(false)
    abstract static class HPyJNIFromCharPointerNode extends HPyFromCharPointerNode {

        @Specialization
        static TruffleString doLong(@SuppressWarnings("unused") GraalHPyContext hpyContext, long charPtr, int n, Encoding encoding, boolean copy,
                        @Bind("this") Node inliningTarget,
                        @Exclusive @Cached InlinedConditionProfile lengthProfile,
                        @Exclusive @Cached TruffleString.FromNativePointerNode fromNativePointerNode,
                        @Exclusive @Cached TruffleString.SwitchEncodingNode switchEncodingNode) {
            int length = lengthProfile.profile(inliningTarget, n < 0) ? strlen(charPtr) : n;
            return read(new NativePointer(charPtr), length, encoding, copy, fromNativePointerNode, switchEncodingNode);
        }

        @Specialization
        static TruffleString doNativePointer(@SuppressWarnings("unused") GraalHPyContext hpyContext, NativePointer charPtr, int n, Encoding encoding, boolean copy,
                        @Bind("this") Node inliningTarget,
                        @Exclusive @Cached InlinedConditionProfile lengthProfile,
                        @Exclusive @Cached TruffleString.FromNativePointerNode fromNativePointerNode,
                        @Exclusive @Cached TruffleString.SwitchEncodingNode switchEncodingNode) {
            int length = lengthProfile.profile(inliningTarget, n < 0) ? strlen(charPtr.asPointer()) : n;
            return read(charPtr, length, encoding, copy, fromNativePointerNode, switchEncodingNode);
        }

        @Specialization(replaces = {"doLong", "doNativePointer"})
        static TruffleString doGeneric(@SuppressWarnings("unused") GraalHPyContext hpyContext, Object charPtr, int n, Encoding encoding, boolean copy,
                        @Bind("this") Node inliningTarget,
                        @Exclusive @CachedLibrary(limit = "1") InteropLibrary lib,
                        @Exclusive @Cached InlinedConditionProfile lengthProfile,
                        @Exclusive @Cached TruffleString.FromNativePointerNode fromNativePointerNode,
                        @Exclusive @Cached TruffleString.SwitchEncodingNode switchEncodingNode) {

            long lcharPtr;
            Object interopPtr;
            if (charPtr instanceof Long ltmp) {
                interopPtr = new NativePointer(ltmp);
                lcharPtr = ltmp;
            } else if (charPtr instanceof NativePointer nativeCharPtr) {
                interopPtr = nativeCharPtr;
                lcharPtr = nativeCharPtr.asPointer();
            } else {
                try {
                    interopPtr = charPtr;
                    lcharPtr = lib.asPointer(charPtr);
                } catch (UnsupportedMessageException e) {
                    throw CompilerDirectives.shouldNotReachHere(e);
                }
            }
            int length = lengthProfile.profile(inliningTarget, n < 0) ? strlen(lcharPtr) : n;
            return read(interopPtr, length, encoding, copy, fromNativePointerNode, switchEncodingNode);
        }

        private static TruffleString read(Object charPtr, int length, Encoding encoding, boolean copy,
                        TruffleString.FromNativePointerNode fromNativePointerNode,
                        TruffleString.SwitchEncodingNode switchEncodingNode) {
            assert length >= 0;
            TruffleString result = fromNativePointerNode.execute(charPtr, 0, length, encoding, copy);
            if (TS_ENCODING != encoding) {
                return switchEncodingNode.execute(result, TS_ENCODING);
            }
            return result;
        }

        private static int strlen(long charPtr) {
            int length = 0;
            while (UNSAFE.getByte(charPtr + length) != 0) {
                length++;
            }
            return length;
        }
    }
}
