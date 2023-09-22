/*
 * Copyright (c) 2023, 2023, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.builtins.objects.cext.hpy;

import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContext.GraalHPyHandleReference;
import com.oracle.graal.python.builtins.objects.object.PythonObject;
import com.oracle.truffle.api.dsl.NeverDefault;
import com.oracle.truffle.api.nodes.Node;

public abstract class GraalHPyCAccess {

    private GraalHPyCAccess() {
    }

    private abstract static class CStructAccessNode extends Node {

        abstract boolean accepts(HPyContextSignatureType type);

        final boolean accepts(GraalHPyCField field) {
            return accepts(field.getType());
        }

        /**
         * Use this method to compute the address of a struct field where the struct is one element
         * in an array. For example:
         * 
         * <pre>
         *     HPyType_SpecParam params[] = {
         *         {HPyType_SpecParam_Base, ctx->h_LongType},
         *         {HPyType_SpecParam_Base, ctx->h_UnicodeType},
         *         {0}
         *     }
         * </pre>
         * 
         * Assume you want to read the field {@code object} (the second field of type {@code HPy})
         * of the second array element. The address is then computed by:
         *
         * <pre>
         *     long arrElemOffset = getElementPtr(1, GraalHPyCStruct.HPyType_SpecParam.getSize(), GraalHPyCField.HPyType_SpecParam_object)
         * </pre>
         * 
         * You may later read the value using {@code ReadHPyNode.execute(ctx, base, arrElemOffset)}.
         *
         * @param idx Index of the element (e.g. a struct type).
         * @param elementSize Size of each element.
         * @param field Field of the element (e.g. a struct field).
         * @return
         */
        public static long getElementPtr(GraalHPyContext ctx, long idx, long elementSize, GraalHPyCField field) {
            return idx * elementSize + ctx.getCFieldOffset(field);
        }

        public static long getElementPtr(GraalHPyContext ctx, long idx, HPyContextSignatureType elementType, GraalHPyCField field) {
            return idx * ctx.getCTypeSize(elementType) + ctx.getCFieldOffset(field);
        }

    }

    public abstract static class AllocateNode extends Node {

        protected abstract Object execute(GraalHPyContext ctx, long size, boolean zero);

        public final Object malloc(GraalHPyContext ctx, long size) {
            return execute(ctx, size, false);
        }

        public final Object malloc(GraalHPyContext ctx, HPyContextSignatureType ctype) {
            return execute(ctx, ctx.getCTypeSize(ctype), false);
        }

        public final Object calloc(GraalHPyContext ctx, long count, long size) {
            return execute(ctx, count * size, true);
        }

        @NeverDefault
        public static AllocateNode create(GraalHPyContext hpyContext) {
            return hpyContext.getBackend().createAllocateNode();
        }

        public static AllocateNode getUncached(GraalHPyContext hpyContext) {
            return hpyContext.getBackend().getUncachedAllocateNode();
        }
    }

    public abstract static class FreeNode extends Node {

        protected abstract void execute(GraalHPyContext ctx, Object pointer);

        public final void free(GraalHPyContext ctx, Object pointer) {
            execute(ctx, pointer);
        }

        @NeverDefault
        public static FreeNode create(GraalHPyContext hpyContext) {
            return hpyContext.getBackend().createFreeNode();
        }

        public static FreeNode getUncached(GraalHPyContext hpyContext) {
            return hpyContext.getBackend().getUncachedFreeNode();
        }
    }

    public abstract static class BulkFreeHandleReferencesNode extends Node {

        protected abstract void execute(GraalHPyContext ctx, GraalHPyHandleReference[] references);

        @NeverDefault
        public static BulkFreeHandleReferencesNode create(GraalHPyContext hpyContext) {
            return hpyContext.getBackend().createBulkFreeHandleReferencesNode();
        }
    }

    public abstract static class IsNullNode extends Node {

        protected abstract boolean execute(GraalHPyContext ctx, Object pointer);

        public static boolean executeUncached(GraalHPyContext ctx, Object pointer) {
            return IsNullNode.getUncached(ctx).execute(ctx, pointer);
        }

        @NeverDefault
        public static IsNullNode create(GraalHPyContext hpyContext) {
            return hpyContext.getBackend().createIsNullNode();
        }

        public static IsNullNode getUncached(GraalHPyContext hpyContext) {
            return hpyContext.getBackend().getUncachedIsNullNode();
        }
    }

    public abstract static class GetElementPtrNode extends CStructAccessNode {

        public abstract Object execute(GraalHPyContext ctx, Object pointer, long offset);

        public final Object getElementPtr(GraalHPyContext ctx, Object pointer, GraalHPyCField field) {
            assert accepts(field);
            return execute(ctx, pointer, ctx.getCFieldOffset(field));
        }

        public final boolean accepts(HPyContextSignatureType desc) {
            return true;
        }

        @NeverDefault
        public static GetElementPtrNode create(GraalHPyContext hpyContext) {
            return hpyContext.getBackend().createGetElementPtrNode();
        }

        public static GetElementPtrNode getUncached(GraalHPyContext hpyContext) {
            return hpyContext.getBackend().getUncachedGetElementPtrNode();
        }
    }

    public abstract static class ReadGenericNode extends CStructAccessNode {

        protected abstract Object execute(GraalHPyContext ctx, Object pointer, long offset, HPyContextSignatureType size);

        protected abstract int executeInt(GraalHPyContext ctx, Object pointer, long offset, HPyContextSignatureType size);

        protected abstract long executeLong(GraalHPyContext ctx, Object pointer, long offset, HPyContextSignatureType size);

        @Override
        boolean accepts(HPyContextSignatureType type) {
            return true;
        }

        public final Object read(GraalHPyContext ctx, Object pointer, GraalHPyCField field) {
            return execute(ctx, pointer, ctx.getCFieldOffset(field), field.getType());
        }

        public final int readInt(GraalHPyContext ctx, Object pointer, GraalHPyCField field) {
            return executeInt(ctx, pointer, ctx.getCFieldOffset(field), field.getType());
        }

        public final long readLong(GraalHPyContext ctx, Object pointer, GraalHPyCField field) {
            return executeLong(ctx, pointer, ctx.getCFieldOffset(field), field.getType());
        }

        @NeverDefault
        public static ReadGenericNode create(GraalHPyContext hpyContext) {
            return hpyContext.getBackend().createReadGenericNode();
        }

        public static ReadGenericNode getUncached(GraalHPyContext hpyContext) {
            return hpyContext.getBackend().getUncachedReadGenericNode();
        }
    }

    public abstract static class ReadI8ArrayNode extends CStructAccessNode {

        protected abstract byte[] execute(GraalHPyContext ctx, Object pointer, long offset, long n);

        public final boolean accepts(HPyContextSignatureType desc) {
            return false;
        }

        @NeverDefault
        public static ReadI8ArrayNode create(GraalHPyContext hpyContext) {
            return hpyContext.getBackend().createReadI8ArrayNode();
        }

        public static ReadI8ArrayNode getUncached(GraalHPyContext hpyContext) {
            return hpyContext.getBackend().getUncachedReadI8ArrayNode();
        }
    }

    public abstract static class ReadHPyNode extends CStructAccessNode {

        protected abstract Object execute(GraalHPyContext ctx, Object pointer, long offset, boolean close);

        public final boolean accepts(HPyContextSignatureType desc) {
            return false;
        }

        public final Object read(GraalHPyContext ctx, Object pointer, long offset) {
            return execute(ctx, pointer, offset, false);
        }

        /**
         * Read an {@code HPy} handle and return the referred object.
         */
        public final Object read(GraalHPyContext ctx, Object pointer, GraalHPyCField field) {
            assert accepts(field);
            return execute(ctx, pointer, ctx.getCFieldOffset(field), false);
        }

        /**
         * Read and close an {@code HPy} handle and return the referred object. This method is
         * mostly useful if some C function <it>returns</it> a handle via an out param. For example,
         * any {@code HPyFunc_getbufferproc} function returns a handle in the {@code HPy_buffer}
         * struct.
         */
        public final Object readAndClose(GraalHPyContext ctx, Object pointer, GraalHPyCField field) {
            assert accepts(field);
            return execute(ctx, pointer, ctx.getCFieldOffset(field), true);
        }

        @NeverDefault
        public static ReadHPyNode create(GraalHPyContext hpyContext) {
            return hpyContext.getBackend().createReadHPyNode();
        }

        public static ReadHPyNode getUncached(GraalHPyContext hpyContext) {
            return hpyContext.getBackend().getUncachedReadHPyNode();
        }
    }

    public abstract static class ReadHPyFieldNode extends CStructAccessNode {

        protected abstract Object execute(GraalHPyContext ctx, PythonObject owner, Object pointer, long offset, boolean close);

        public final boolean accepts(HPyContextSignatureType desc) {
            return false;
        }

        public final Object read(GraalHPyContext ctx, PythonObject owner, Object pointer, long offset) {
            return execute(ctx, owner, pointer, offset, false);
        }

        /**
         * Read an {@code HPy} handle and return the referred object.
         */
        public final Object read(GraalHPyContext ctx, PythonObject owner, Object pointer, GraalHPyCField field) {
            assert accepts(field);
            return execute(ctx, owner, pointer, ctx.getCFieldOffset(field), false);
        }

        @NeverDefault
        public static ReadHPyFieldNode create(GraalHPyContext hpyContext) {
            return hpyContext.getBackend().createReadHPyFieldNode();
        }

        public static ReadHPyFieldNode getUncached(GraalHPyContext hpyContext) {
            return hpyContext.getBackend().getUncachedReadFieldHPyNode();
        }
    }

    public abstract static class ReadHPyArrayNode extends CStructAccessNode {

        protected abstract Object[] execute(GraalHPyContext ctx, Object pointer, long offset, long n);

        public final boolean accepts(HPyContextSignatureType desc) {
            return false;
        }

        @NeverDefault
        public static ReadHPyArrayNode create(GraalHPyContext hpyContext) {
            return hpyContext.getBackend().createReadHPyArrayNode();
        }

        public static ReadHPyArrayNode getUncached(GraalHPyContext hpyContext) {
            return hpyContext.getBackend().getUncachedReadHPyArrayNode();
        }
    }

    public abstract static class ReadI32Node extends CStructAccessNode {

        protected abstract int execute(GraalHPyContext ctx, Object pointer, long offset);

        public final int read(GraalHPyContext ctx, Object pointer, GraalHPyCField field) {
            assert accepts(field);
            return execute(ctx, pointer, ctx.getCFieldOffset(field));
        }

        public final long readUnsigned(GraalHPyContext ctx, Object pointer, GraalHPyCField field) {
            assert field.getType() == HPyContextSignatureType.Uint32_t;
            return execute(ctx, pointer, ctx.getCFieldOffset(field)) & 0xFFFFFFFFL;
        }

        public final boolean accepts(HPyContextSignatureType desc) {
            return desc.jniType == int.class;
        }

        public final int readOffset(GraalHPyContext ctx, Object pointer, long offset) {
            return execute(ctx, pointer, offset);
        }

        public final int readArrayElement(GraalHPyContext ctx, Object pointer, long element) {
            return execute(ctx, pointer, element * Integer.BYTES);
        }

        @NeverDefault
        public static ReadI32Node create(GraalHPyContext hpyContext) {
            return hpyContext.getBackend().createReadI32Node();
        }

        public static ReadI32Node getUncached(GraalHPyContext hpyContext) {
            return hpyContext.getBackend().getUncachedReadI32Node();
        }
    }

    public abstract static class ReadI64Node extends CStructAccessNode {

        protected abstract long execute(GraalHPyContext ctx, Object pointer, long offset);

        public final long read(GraalHPyContext ctx, Object pointer, GraalHPyCField field) {
            assert accepts(field);
            return execute(ctx, pointer, ctx.getCFieldOffset(field));
        }

        public final boolean accepts(HPyContextSignatureType desc) {
            return desc.jniType == long.class;
        }

        @NeverDefault
        public static ReadI64Node create(GraalHPyContext hpyContext) {
            return hpyContext.getBackend().createReadI64Node();
        }

        public static ReadI64Node getUncached(GraalHPyContext hpyContext) {
            return hpyContext.getBackend().getUncachedReadI64Node();
        }
    }

    /**
     * Note that this node returns a double, not a float, even though it reads only 32 bits.
     */
    public abstract static class ReadFloatNode extends CStructAccessNode {

        protected abstract double execute(GraalHPyContext ctx, Object pointer, long offset);

        public final double read(GraalHPyContext ctx, Object pointer, GraalHPyCField field) {
            assert accepts(field);
            return execute(ctx, pointer, ctx.getCFieldOffset(field));
        }

        public final boolean accepts(HPyContextSignatureType desc) {
            return desc.jniType == double.class;
        }

        public final double readArrayElement(GraalHPyContext ctx, Object pointer, int element) {
            return execute(ctx, pointer, (long) element * Float.BYTES);
        }

        @NeverDefault
        public static ReadFloatNode create(GraalHPyContext hpyContext) {
            return hpyContext.getBackend().createReadFloatNode();
        }

        public static ReadFloatNode getUncached(GraalHPyContext hpyContext) {
            return hpyContext.getBackend().getUncachedReadFloatNode();
        }
    }

    public abstract static class ReadDoubleNode extends CStructAccessNode {

        protected abstract double execute(GraalHPyContext ctx, Object pointer, long offset);

        public final double read(GraalHPyContext ctx, Object pointer, GraalHPyCField field) {
            assert accepts(field);
            return execute(ctx, pointer, ctx.getCFieldOffset(field));
        }

        public final boolean accepts(HPyContextSignatureType desc) {
            return desc.jniType == double.class;
        }

        public final double readArrayElement(GraalHPyContext ctx, Object pointer, int element) {
            return execute(ctx, pointer, (long) element * Double.BYTES);
        }

        @NeverDefault
        public static ReadDoubleNode create(GraalHPyContext hpyContext) {
            return hpyContext.getBackend().createReadDoubleNode();
        }

        public static ReadDoubleNode getUncached(GraalHPyContext hpyContext) {
            return hpyContext.getBackend().getUncachedReadDoubleNode();
        }
    }

    public abstract static class ReadPointerNode extends CStructAccessNode {

        protected abstract Object execute(GraalHPyContext ctx, Object pointer, long offset);

        public final Object read(GraalHPyContext ctx, Object pointer, GraalHPyCField field) {
            assert accepts(field);
            return execute(ctx, pointer, ctx.getCFieldOffset(field));
        }

        public final boolean accepts(HPyContextSignatureType desc) {
            return "POINTER".equals(desc.nfiType);
        }

        public final Object readArrayElement(GraalHPyContext ctx, Object pointer, long element) {
            return execute(ctx, pointer, element * ctx.getCTypeSize(HPyContextSignatureType.VoidPtr));
        }

        @NeverDefault
        public static ReadPointerNode create(GraalHPyContext hpyContext) {
            return hpyContext.getBackend().createReadPointerNode();
        }

        public static ReadPointerNode getUncached(GraalHPyContext hpyContext) {
            return hpyContext.getBackend().getUncachedReadPointerNode();
        }
    }

    public abstract static class WriteDoubleNode extends CStructAccessNode {

        protected abstract void execute(GraalHPyContext ctx, Object pointer, long offset, double value);

        public final void write(GraalHPyContext ctx, Object pointer, GraalHPyCField field, double value) {
            assert accepts(field);
            execute(ctx, pointer, ctx.getCFieldOffset(field), value);
        }

        public final void write(GraalHPyContext ctx, Object pointer, double value) {
            execute(ctx, pointer, 0, value);
        }

        public final void writeArrayElement(GraalHPyContext ctx, Object pointer, long element, double value) {
            execute(ctx, pointer, element * Double.BYTES, value);
        }

        public final boolean accepts(HPyContextSignatureType desc) {
            return desc == HPyContextSignatureType.CDouble;
        }

        @NeverDefault
        public static WriteDoubleNode create(GraalHPyContext hpyContext) {
            return hpyContext.getBackend().createWriteDoubleNode();
        }

        public static WriteDoubleNode getUncached(GraalHPyContext hpyContext) {
            return hpyContext.getBackend().getUncachedWriteDoubleNode();
        }
    }

    public abstract static class WriteI32Node extends CStructAccessNode {

        protected abstract void execute(GraalHPyContext ctx, Object pointer, long offset, int value);

        public final void write(GraalHPyContext ctx, Object pointer, GraalHPyCField field, int value) {
            assert accepts(field);
            execute(ctx, pointer, ctx.getCFieldOffset(field), value);
        }

        public final void write(GraalHPyContext ctx, Object pointer, int value) {
            execute(ctx, pointer, 0, value);
        }

        public final boolean accepts(HPyContextSignatureType desc) {
            return desc.jniType == int.class;
        }

        public final void writeArrayElement(GraalHPyContext ctx, Object pointer, long element, int value) {
            execute(ctx, pointer, element * Integer.BYTES, value);
        }

        @NeverDefault
        public static WriteI32Node create(GraalHPyContext hpyContext) {
            return hpyContext.getBackend().createWriteI32Node();
        }

        public static WriteI32Node getUncached(GraalHPyContext hpyContext) {
            return hpyContext.getBackend().getUncachedWriteI32Node();
        }
    }

    public abstract static class WriteI64Node extends CStructAccessNode {

        protected abstract void execute(GraalHPyContext ctx, Object pointer, long offset, long value);

        public final void write(GraalHPyContext ctx, Object pointer, GraalHPyCField field, long value) {
            assert accepts(field);
            execute(ctx, pointer, ctx.getCFieldOffset(field), value);
        }

        public final void write(GraalHPyContext ctx, Object pointer, long value) {
            execute(ctx, pointer, 0, value);
        }

        public final boolean accepts(HPyContextSignatureType desc) {
            return desc.jniType == long.class;
        }

        @NeverDefault
        public static WriteI64Node create(GraalHPyContext hpyContext) {
            return hpyContext.getBackend().createWriteI64Node();
        }

        public static WriteI64Node getUncached(GraalHPyContext hpyContext) {
            return hpyContext.getBackend().getUncachedWriteI64Node();
        }
    }

    public abstract static class WriteGenericNode extends CStructAccessNode {

        protected abstract void execute(GraalHPyContext ctx, Object pointer, long offset, HPyContextSignatureType type, Object value);

        protected abstract void execute(GraalHPyContext ctx, Object pointer, long offset, HPyContextSignatureType type, long value);

        public final boolean accepts(HPyContextSignatureType desc) {
            return true;
        }

        @NeverDefault
        public static WriteGenericNode create(GraalHPyContext hpyContext) {
            return hpyContext.getBackend().createWriteGenericNode();
        }

        public static WriteGenericNode getUncached(GraalHPyContext hpyContext) {
            return hpyContext.getBackend().getUncachedWriteGenericNode();
        }
    }

    public abstract static class WriteHPyNode extends CStructAccessNode {

        protected abstract void execute(GraalHPyContext ctx, Object pointer, long offset, Object value);

        public final void write(GraalHPyContext ctx, Object pointer, GraalHPyCField field, Object value) {
            assert accepts(field);
            execute(ctx, pointer, ctx.getCFieldOffset(field), value);
        }

        public final void write(GraalHPyContext ctx, Object pointer, Object value) {
            execute(ctx, pointer, 0, value);
        }

        public final boolean accepts(HPyContextSignatureType desc) {
            return desc == HPyContextSignatureType.HPy;
        }

        @NeverDefault
        public static WriteHPyNode create(GraalHPyContext hpyContext) {
            return hpyContext.getBackend().createWriteHPyNode();
        }

        public static WriteHPyNode getUncached(GraalHPyContext hpyContext) {
            return hpyContext.getBackend().getUncachedWriteHPyNode();
        }
    }

    public abstract static class WriteHPyFieldNode extends CStructAccessNode {

        protected abstract void execute(GraalHPyContext ctx, PythonObject owner, Object pointer, long offset, Object value);

        public final void write(GraalHPyContext ctx, PythonObject owner, Object pointer, GraalHPyCField field, Object value) {
            assert accepts(field);
            execute(ctx, owner, pointer, ctx.getCFieldOffset(field), value);
        }

        public final void write(GraalHPyContext ctx, PythonObject owner, Object pointer, Object value) {
            execute(ctx, owner, pointer, 0, value);
        }

        public final boolean accepts(HPyContextSignatureType desc) {
            return desc == HPyContextSignatureType.HPyField;
        }

        @NeverDefault
        public static WriteHPyFieldNode create(GraalHPyContext hpyContext) {
            return hpyContext.getBackend().createWriteHPyFieldNode();
        }

        public static WriteHPyFieldNode getUncached(GraalHPyContext hpyContext) {
            return hpyContext.getBackend().getUncachedWriteHPyFieldNode();
        }
    }

    public abstract static class WritePointerNode extends CStructAccessNode {

        protected abstract void execute(GraalHPyContext ctx, Object basePointer, long offset, Object valuePointer);

        public final void write(GraalHPyContext ctx, Object basePointer, GraalHPyCField field, Object valuePointer) {
            assert accepts(field);
            execute(ctx, basePointer, ctx.getCFieldOffset(field), valuePointer);
        }

        public final void write(GraalHPyContext ctx, Object basePointer, Object valuePointer) {
            execute(ctx, basePointer, 0, valuePointer);
        }

        public final boolean accepts(HPyContextSignatureType desc) {
            return "POINTER".equals(desc.nfiType);
        }

        @NeverDefault
        public static WritePointerNode create(GraalHPyContext hpyContext) {
            return hpyContext.getBackend().createWritePointerNode();
        }

        public static WritePointerNode getUncached(GraalHPyContext hpyContext) {
            return hpyContext.getBackend().getUncachedWritePointerNode();
        }
    }

    public abstract static class WriteSizeTNode extends CStructAccessNode {

        protected abstract void execute(GraalHPyContext ctx, Object basePointer, long offset, long value);

        public final void write(GraalHPyContext ctx, Object basePointer, GraalHPyCField field, long value) {
            assert accepts(field);
            execute(ctx, basePointer, ctx.getCFieldOffset(field), value);
        }

        public final void write(GraalHPyContext ctx, Object basePointer, long value) {
            execute(ctx, basePointer, 0, value);
        }

        public final boolean accepts(HPyContextSignatureType desc) {
            return desc == HPyContextSignatureType.Size_t || desc == HPyContextSignatureType.HPy_ssize_t;
        }

        @NeverDefault
        public static WriteSizeTNode create(GraalHPyContext hpyContext) {
            return hpyContext.getBackend().createWriteSizeTNode();
        }

        public static WriteSizeTNode getUncached(GraalHPyContext hpyContext) {
            return hpyContext.getBackend().getUncachedWriteSizeTNode();
        }
    }
}
