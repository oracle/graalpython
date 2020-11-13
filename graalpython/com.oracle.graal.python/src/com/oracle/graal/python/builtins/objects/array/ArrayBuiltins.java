/*
 * Copyright (c) 2017, 2020, Oracle and/or its affiliates.
 * Copyright (c) 2014, Regents of the University of California
 *
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification, are
 * permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this list of
 * conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice, this list of
 * conditions and the following disclaimer in the documentation and/or other materials provided
 * with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS
 * OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
 * MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE
 * COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE
 * GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED
 * AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED
 * OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.oracle.graal.python.builtins.objects.array;

import static com.oracle.graal.python.builtins.PythonBuiltinClassType.IndexError;
import static com.oracle.graal.python.builtins.PythonBuiltinClassType.TypeError;
import static com.oracle.graal.python.builtins.PythonBuiltinClassType.ValueError;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__ADD__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__CONTAINS__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__DELITEM__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__EQ__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__GETITEM__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__GE__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__GT__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__IADD__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__IMUL__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__ITER__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__LEN__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__LE__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__LT__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__MUL__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__REPR__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__RMUL__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__SETITEM__;

import java.util.List;

import com.oracle.graal.python.annotations.ArgumentClinic;
import com.oracle.graal.python.builtins.Builtin;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.PNotImplemented;
import com.oracle.graal.python.builtins.objects.common.IndexNodes.NormalizeIndexNode;
import com.oracle.graal.python.builtins.objects.object.PythonObjectLibrary;
import com.oracle.graal.python.builtins.objects.slice.PSlice;
import com.oracle.graal.python.nodes.call.special.LookupAndCallUnaryNode;
import com.oracle.graal.python.nodes.control.GetNextNode;
import com.oracle.graal.python.nodes.expression.BinaryComparisonNode;
import com.oracle.graal.python.nodes.expression.CoerceToBooleanNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.builtins.PythonBinaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonBinaryClinicBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonTernaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonTernaryClinicBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonUnaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.clinic.ArgumentClinicProvider;
import com.oracle.graal.python.nodes.object.IsBuiltinClassProfile;
import com.oracle.graal.python.nodes.subscript.SliceLiteralNode;
import com.oracle.graal.python.nodes.util.CastToJavaStringNode;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.graal.python.util.BufferFormat;
import com.oracle.graal.python.util.PythonUtils;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.profiles.ConditionProfile;

@CoreFunctions(extendClasses = PythonBuiltinClassType.PArray)
public class ArrayBuiltins extends PythonBuiltins {

    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return ArrayBuiltinsFactory.getFactories();
    }

    @Builtin(name = __ADD__, minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    abstract static class AddNode extends PythonBinaryBuiltinNode {
        @Specialization(guards = "left.getFormat() == right.getFormat()")
        Object concat(PArray left, PArray right) {
            // TODO check overflow
            int newLength = left.getLength() + right.getLength();
            int itemsize = left.getFormat().bytesize;
            PArray newArray = factory().createArray(left.getFormatStr(), left.getFormat(), newLength);
            PythonUtils.arraycopy(left.getBuffer(), 0, newArray.getBuffer(), 0, left.getLength() * itemsize);
            PythonUtils.arraycopy(right.getBuffer(), 0, newArray.getBuffer(), left.getLength() * itemsize, right.getLength() * itemsize);
            return newArray;
        }

        @Specialization(guards = "left.getFormat() != right.getFormat()")
        @SuppressWarnings("unused")
        Object error(PArray left, PArray right) {
            throw raise(TypeError, "bad argument type for built-in operation");
        }

        @Fallback
        Object error(@SuppressWarnings("unused") Object left, Object right) {
            throw raise(TypeError, "can only append array (not \"%p\") to array", right);
        }
    }

    @Builtin(name = __IADD__, minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    abstract static class IAddNode extends PythonBinaryBuiltinNode {
        @Specialization
        static Object concat(VirtualFrame frame, PArray left, PArray right,
                        @Cached ExtendNode extendNode) {
            extendNode.execute(frame, left, right);
            return left;
        }

        @Fallback
        Object error(@SuppressWarnings("unused") Object left, Object right) {
            throw raise(TypeError, "can only extend array (not \"%p\") with array", right);
        }
    }

    @Builtin(name = __MUL__, minNumOfPositionalArgs = 2, numOfPositionalOnlyArgs = 2, parameterNames = {"$self", "value"})
    @ArgumentClinic(name = "value", conversion = ArgumentClinic.ClinicConversion.Index, defaultValue = "0")
    @GenerateNodeFactory
    abstract static class MulNode extends PythonBinaryClinicBuiltinNode {
        @Specialization
        Object concat(PArray self, int value) {
            // TODO check overflow
            int newLength = Math.max(self.getLength() * value, 0);
            int itemsize = self.getFormat().bytesize;
            PArray newArray = factory().createArray(self.getFormatStr(), self.getFormat(), newLength);
            int segmentLenght = self.getLength() * itemsize;
            for (int i = 0; i < value; i++) {
                PythonUtils.arraycopy(self.getBuffer(), 0, newArray.getBuffer(), segmentLenght * i, segmentLenght);
            }
            return newArray;
        }

        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return ArrayBuiltinsClinicProviders.MulNodeClinicProviderGen.INSTANCE;
        }
    }

    @Builtin(name = __RMUL__, minNumOfPositionalArgs = 2, numOfPositionalOnlyArgs = 2, parameterNames = {"$self", "value"})
    abstract static class RMulNode extends MulNode {
    }

    @Builtin(name = __IMUL__, minNumOfPositionalArgs = 2, numOfPositionalOnlyArgs = 2, parameterNames = {"$self", "value"})
    @ArgumentClinic(name = "value", conversion = ArgumentClinic.ClinicConversion.Index, defaultValue = "0")
    @GenerateNodeFactory
    abstract static class IMulNode extends PythonBinaryClinicBuiltinNode {
        @Specialization
        static Object concat(PArray self, int value) {
            // TODO check overflow
            int newLength = Math.max(self.getLength() * value, 0);
            int itemsize = self.getFormat().bytesize;
            int segmentLenght = self.getLength() * itemsize;
            self.ensureCapacity(newLength);
            for (int i = 0; i < value; i++) {
                PythonUtils.arraycopy(self.getBuffer(), 0, self.getBuffer(), segmentLenght * i, segmentLenght);
            }
            self.setLenght(newLength);
            return self;
        }

        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return ArrayBuiltinsClinicProviders.IMulNodeClinicProviderGen.INSTANCE;
        }
    }

    @Builtin(name = __EQ__, minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    abstract static class EqNode extends PythonBinaryBuiltinNode {

        @Specialization(guards = "canCompareBytes(left, right)")
        static boolean eqFastPath(PArray left, PArray right) {
            if (left.getLength() != right.getLength()) {
                return false;
            }
            int itemsize = left.getFormat().bytesize;
            for (int i = 0; i < left.getLength() * itemsize; i++) {
                if (left.getBuffer()[i] != right.getBuffer()[i]) {
                    return false;
                }
            }
            return true;
        }

        @Specialization(replaces = "eqFastPath")
        static boolean eq(PArray left, PArray right,
                        @CachedLibrary(limit = "4") PythonObjectLibrary lib,
                        @Cached ArrayNodes.GetValueNode getLeft,
                        @Cached ArrayNodes.GetValueNode getRight) {
            if (left.getLength() != right.getLength()) {
                return false;
            }
            for (int i = 0; i < left.getLength(); i++) {
                if (!lib.equals(getLeft.execute(left, i), getRight.execute(right, i), lib)) {
                    return false;
                }
            }
            return true;
        }

        @Specialization(guards = "!isArray(right)")
        @SuppressWarnings("unused")
        static Object eq(PArray left, Object right) {
            return PNotImplemented.NOT_IMPLEMENTED;
        }

        protected static boolean canCompareBytes(PArray left, PArray right) {
            return left.getFormat() == right.getFormat() && left.getFormat() != BufferFormat.DOUBLE && left.getFormat() != BufferFormat.FLOAT;
        }
    }

    abstract static class AbstractComparisonNode extends PythonBinaryBuiltinNode {

        @Specialization
        boolean cmp(VirtualFrame frame, PArray left, PArray right,
                        @CachedLibrary(limit = "4") PythonObjectLibrary lib,
                        @Cached("createComparison()") BinaryComparisonNode compareNode,
                        @Cached("createIfTrueNode()") CoerceToBooleanNode coerceToBooleanNode,
                        @Cached ArrayNodes.GetValueNode getLeft,
                        @Cached ArrayNodes.GetValueNode getRight) {
            int commonLength = Math.min(left.getLength(), right.getLength());
            for (int i = 0; i < commonLength; i++) {
                Object leftValue = getLeft.execute(left, i);
                Object rightValue = getRight.execute(right, i);
                if (!lib.equals(leftValue, rightValue, lib)) {
                    return coerceToBooleanNode.executeBoolean(frame, compareNode.executeWith(frame, leftValue, rightValue));
                }
            }
            return compareLengths(left.getLength(), right.getLength());
        }

        @Specialization(guards = "!isArray(right)")
        @SuppressWarnings("unused")
        static Object cmp(PArray left, Object right) {
            return PNotImplemented.NOT_IMPLEMENTED;
        }

        @SuppressWarnings("unused")
        protected boolean compareLengths(int a, int b) {
            throw new AbstractMethodError("compareLengths");
        }

        protected BinaryComparisonNode createComparison() {
            throw new AbstractMethodError("createComparison");
        }
    }

    @Builtin(name = __LT__, minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    abstract static class LtNode extends AbstractComparisonNode {

        @Override
        protected BinaryComparisonNode createComparison() {
            return BinaryComparisonNode.create(__LT__, __GT__, "<");
        }

        @Override
        protected boolean compareLengths(int a, int b) {
            return a < b;
        }
    }

    @Builtin(name = __GT__, minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    abstract static class GtNode extends AbstractComparisonNode {

        @Override
        protected BinaryComparisonNode createComparison() {
            return BinaryComparisonNode.create(__GT__, __LT__, ">");
        }

        @Override
        protected boolean compareLengths(int a, int b) {
            return a > b;
        }
    }

    @Builtin(name = __LE__, minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    abstract static class LeNode extends AbstractComparisonNode {

        @Override
        protected BinaryComparisonNode createComparison() {
            return BinaryComparisonNode.create(__LE__, __GE__, "<=");
        }

        @Override
        protected boolean compareLengths(int a, int b) {
            return a <= b;
        }
    }

    @Builtin(name = __GE__, minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    abstract static class GeNode extends AbstractComparisonNode {

        @Override
        protected BinaryComparisonNode createComparison() {
            return BinaryComparisonNode.create(__GE__, __LE__, ">=");
        }

        @Override
        protected boolean compareLengths(int a, int b) {
            return a >= b;
        }
    }

    @Builtin(name = __CONTAINS__, minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    abstract static class ContainsNode extends PythonBinaryBuiltinNode {
        @Specialization
        static boolean contains(VirtualFrame frame, PArray self, Object value,
                        @CachedLibrary(limit = "3") PythonObjectLibrary lib,
                        @Cached ArrayNodes.GetValueNode getValueNode) {
            for (int i = 0; i < self.getLength(); i++) {
                if (lib.equalsWithFrame(getValueNode.execute(self, i), value, lib, frame)) {
                    return true;
                }
            }
            return false;
        }
    }

    @Builtin(name = __REPR__, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class ReprNode extends PythonUnaryBuiltinNode {
        @Specialization
        static String repr(VirtualFrame frame, PArray self,
                        @Cached("create(__REPR__)") LookupAndCallUnaryNode reprNode,
                        @Cached ConditionProfile isEmptyProfile,
                        @Cached CastToJavaStringNode cast,
                        @Cached ArrayNodes.GetValueNode getValueNode) {
            StringBuilder sb = PythonUtils.newStringBuilder();
            PythonUtils.append(sb, "array('");
            PythonUtils.append(sb, self.getFormatStr());
            PythonUtils.append(sb, '\'');
            if (isEmptyProfile.profile(self.getLength() != 0)) {
                PythonUtils.append(sb, ", [");
                for (int i = 0; i < self.getLength(); i++) {
                    if (i > 0) {
                        PythonUtils.append(sb, ", ");
                    }
                    Object value = getValueNode.execute(self, i);
                    PythonUtils.append(sb, cast.execute(reprNode.executeObject(frame, value)));
                }
                PythonUtils.append(sb, ']');
            }
            PythonUtils.append(sb, ')');
            return PythonUtils.sbToString(sb);
        }
    }

    @Builtin(name = __GETITEM__, minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    public abstract static class GetItemNode extends PythonBinaryBuiltinNode {

        @Specialization(guards = "!isPSlice(idx)", limit = "3")
        static Object getitem(PArray self, Object idx,
                        @CachedLibrary("idx") PythonObjectLibrary lib,
                        @Cached("forArray()") NormalizeIndexNode normalizeIndexNode,
                        @Cached ArrayNodes.GetValueNode getValueNode) {
            int index = normalizeIndexNode.execute(lib.asIndex(idx), self.getLength());
            return getValueNode.execute(self, index);
        }

        @Specialization
        Object getitem(PArray self, PSlice slice,
                        @Cached ConditionProfile simpleStepProfile,
                        @Cached SliceLiteralNode.SliceUnpack sliceUnpack,
                        @Cached SliceLiteralNode.AdjustIndices adjustIndices) {
            PSlice.SliceInfo sliceInfo = adjustIndices.execute(self.getLength(), sliceUnpack.execute(slice));
            int itemsize = self.getFormat().bytesize;
            PArray newArray = factory().createArray(self.getFormatStr(), self.getFormat(), sliceInfo.sliceLength);

            if (simpleStepProfile.profile(sliceInfo.step == 1)) {
                PythonUtils.arraycopy(self.getBuffer(), sliceInfo.start * itemsize, newArray.getBuffer(), 0, sliceInfo.sliceLength * itemsize);
            } else {
                for (int i = sliceInfo.start, j = 0; j < sliceInfo.sliceLength; i += sliceInfo.step, j++) {
                    PythonUtils.arraycopy(self.getBuffer(), i * itemsize, newArray.getBuffer(), j * itemsize, itemsize);
                }
            }
            return newArray;
        }
    }

    @Builtin(name = __SETITEM__, minNumOfPositionalArgs = 3)
    @GenerateNodeFactory
    abstract static class SetItemNode extends PythonTernaryBuiltinNode {

        @Specialization(guards = "!isPSlice(idx)", limit = "3")
        static Object setitem(VirtualFrame frame, PArray self, Object idx, Object value,
                        @CachedLibrary("idx") PythonObjectLibrary lib,
                        @Cached("forArrayAssign()") NormalizeIndexNode normalizeIndexNode,
                        @Cached ArrayNodes.PutValueNode putValueNode) {
            int index = normalizeIndexNode.execute(lib.asIndex(idx), self.getLength());
            putValueNode.execute(frame, self, index, value);
            return PNone.NONE;
        }

        @Specialization(guards = "self.getFormat() == other.getFormat()")
        Object setitem(PArray self, PSlice slice, PArray other,
                        @Cached ConditionProfile sameArrayProfile,
                        @Cached ConditionProfile simpleStepProfile,
                        @Cached ConditionProfile complexDeleteProfile,
                        @Cached ConditionProfile differentLenghtProfile,
                        @Cached ConditionProfile growProfile,
                        @Cached SliceLiteralNode.SliceUnpack sliceUnpack,
                        @Cached SliceLiteralNode.AdjustIndices adjustIndices) {
            PSlice.SliceInfo sliceInfo = adjustIndices.execute(self.getLength(), sliceUnpack.execute(slice));
            int start = sliceInfo.start;
            int stop = sliceInfo.stop;
            int step = sliceInfo.step;
            int sliceLength = sliceInfo.sliceLength;
            int itemsize = self.getFormat().bytesize;
            byte[] sourceBuffer = other.getBuffer();
            int needed = other.getLength();
            if (sameArrayProfile.profile(sourceBuffer == self.getBuffer())) {
                sourceBuffer = new byte[needed * itemsize];
                PythonUtils.arraycopy(other.getBuffer(), 0, sourceBuffer, 0, sourceBuffer.length);
            }
            if (simpleStepProfile.profile(step == 1)) {
                if (differentLenghtProfile.profile(sliceLength != needed)) {
                    if (growProfile.profile(sliceLength < needed)) {
                        if (stop < start) {
                            stop = start;
                        }
                        self.shift(stop, needed - sliceLength);
                    } else {
                        self.delSlice(start, sliceLength - needed);
                    }
                }
                PythonUtils.arraycopy(sourceBuffer, 0, self.getBuffer(), start * itemsize, needed * itemsize);
            } else if (complexDeleteProfile.profile(needed == 0)) {
                if ((step > 0 && stop < start) || (step < 0 && stop > start)) {
                    stop = start;
                }
                if (step < 0) {
                    stop = start + 1;
                    start = stop + step * (sliceLength - 1) - 1;
                    step = -step;
                }
                for (int i = start; i < stop; i += step - 1) {
                    self.delSlice(i, 1);
                }
            } else {
                throw raise(ValueError, "attempt to assign array of size %d to extended slice of size %d", needed, sliceLength);
            }
            return PNone.NONE;
        }

        @Specialization(guards = "self.getFormat() != other.getFormat()")
        @SuppressWarnings("unused")
        Object setitemWrongFormat(PArray self, PSlice slice, PArray other) {
            throw raise(TypeError, "bad argument type for built-in operation");
        }

        @Specialization(guards = "!isArray(other)")
        @SuppressWarnings("unused")
        Object setitemWrongType(PArray self, PSlice slice, Object other) {
            throw raise(TypeError, "can only assign array (not \"%p\") to array slice", other);
        }
    }

    @Builtin(name = __DELITEM__, minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    public abstract static class DelItemNode extends PythonBinaryBuiltinNode {
        @Specialization(guards = "!isPSlice(idx)", limit = "3")
        static Object delitem(PArray self, Object idx,
                        @CachedLibrary("idx") PythonObjectLibrary lib,
                        @Cached("forArrayAssign()") NormalizeIndexNode normalizeIndexNode) {
            int index = normalizeIndexNode.execute(lib.asIndex(idx), self.getLength());
            self.delSlice(index, 1);
            return PNone.NONE;
        }

        @Specialization
        static Object delitem(PArray self, PSlice slice,
                        @Cached ConditionProfile simpleStepProfile,
                        @Cached SliceLiteralNode.SliceUnpack sliceUnpack,
                        @Cached SliceLiteralNode.AdjustIndices adjustIndices) {
            PSlice.SliceInfo sliceInfo = adjustIndices.execute(self.getLength(), sliceUnpack.execute(slice));
            int start = sliceInfo.start;
            int stop = sliceInfo.stop;
            int step = sliceInfo.step;
            int sliceLength = sliceInfo.sliceLength;
            if (simpleStepProfile.profile(step == 1)) {
                self.delSlice(start, sliceLength);
            } else {
                if ((step > 0 && stop < start) || (step < 0 && stop > start)) {
                    stop = start;
                }
                if (step < 0) {
                    stop = start + 1;
                    start = stop + step * (sliceLength - 1) - 1;
                    step = -step;
                }
                for (int i = start; i < stop; i += step - 1) {
                    self.delSlice(i, 1);
                }
            }
            return PNone.NONE;
        }
    }

    @Builtin(name = __ITER__, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class IterNode extends PythonUnaryBuiltinNode {

        @Specialization
        Object getitem(PArray self) {
            return factory().createArrayIterator(self);
        }
    }

    @Builtin(name = __LEN__, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class LenNode extends PythonUnaryBuiltinNode {

        @Specialization
        static int len(PArray self) {
            return self.getLength();
        }
    }

    @Builtin(name = "itemsize", minNumOfPositionalArgs = 1, isGetter = true)
    @GenerateNodeFactory
    abstract static class ItemSizeNode extends PythonUnaryBuiltinNode {

        @Specialization
        static int getItemSize(PArray self) {
            return self.getFormat().bytesize;
        }
    }

    @Builtin(name = "typecode", minNumOfPositionalArgs = 1, isGetter = true)
    @GenerateNodeFactory
    abstract static class TypeCodeNode extends PythonUnaryBuiltinNode {

        @Specialization
        static String getTypeCode(PArray self) {
            return self.getFormatStr();
        }
    }

    @Builtin(name = "append", minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    public abstract static class AppendNode extends PythonBinaryBuiltinNode {
        @Specialization
        static Object append(VirtualFrame frame, PArray self, Object value,
                        @Cached ArrayNodes.PutValueNode putValueNode) {
            int index = self.getLength();
            int newLength = index + 1;
            self.ensureCapacity(newLength);
            self.setLenght(newLength);
            putValueNode.execute(frame, self, index, value);
            return PNone.NONE;
        }
    }

    @Builtin(name = "extend", minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    public abstract static class ExtendNode extends PythonBinaryBuiltinNode {
        @Specialization(guards = "self.getFormat() == value.getFormat()")
        static Object extend(PArray self, PArray value) {
            // TODO check overflow
            int newLength = self.getLength() + value.getLength();
            int itemsize = self.getFormat().bytesize;
            self.ensureCapacity(newLength);
            PythonUtils.arraycopy(value.getBuffer(), 0, self.getBuffer(), self.getLength() * itemsize, value.getLength() * itemsize);
            self.setLenght(newLength);
            return PNone.NONE;
        }

        @Specialization(guards = "self.getFormat() != value.getFormat()")
        @SuppressWarnings("unused")
        Object error(PArray self, PArray value) {
            // CPython allows extending an array with an arbitrary iterable. Except a differently
            // formatted array. Weird
            throw raise(TypeError, "can only extend with array of same kind");
        }

        @Specialization(guards = "!isArray(value)", limit = "3")
        static Object extend(VirtualFrame frame, PArray self, Object value,
                        @CachedLibrary("value") PythonObjectLibrary lib,
                        @Cached ArrayNodes.PutValueNode putValueNode,
                        @Cached GetNextNode nextNode,
                        @Cached IsBuiltinClassProfile errorProfile) {
            Object iter = lib.getIteratorWithFrame(value, frame);
            int lenght = self.getLength();
            while (true) {
                Object nextValue;
                try {
                    nextValue = nextNode.execute(frame, iter);
                } catch (PException e) {
                    e.expectStopIteration(errorProfile);
                    break;
                }
                self.ensureCapacity(++lenght);
                putValueNode.execute(frame, self, lenght - 1, nextValue);
            }

            self.setLenght(lenght);
            return PNone.NONE;
        }
    }

    @Builtin(name = "insert", minNumOfPositionalArgs = 3, numOfPositionalOnlyArgs = 3, parameterNames = {"$self", "index", "value"})
    @ArgumentClinic(name = "index", conversion = ArgumentClinic.ClinicConversion.Index, defaultValue = "0")
    @GenerateNodeFactory
    public abstract static class InsertNode extends PythonTernaryClinicBuiltinNode {
        @Specialization
        static Object insert(VirtualFrame frame, PArray self, int inputIndex, Object value,
                        @Cached("create(false)") NormalizeIndexNode normalizeIndexNode,
                        @Cached ArrayNodes.PutValueNode putValueNode) {
            int index = normalizeIndexNode.execute(inputIndex, self.getLength());
            if (index > self.getLength()) {
                index = self.getLength();
            } else if (index < 0) {
                index = 0;
            }
            self.shift(index, 1);
            putValueNode.execute(frame, self, index, value);
            return PNone.NONE;
        }

        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return ArrayBuiltinsClinicProviders.InsertNodeClinicProviderGen.INSTANCE;
        }
    }

    @Builtin(name = "remove", minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    public abstract static class RemoveNode extends PythonBinaryBuiltinNode {
        @Specialization
        Object remove(VirtualFrame frame, PArray self, Object value,
                        @CachedLibrary(limit = "3") PythonObjectLibrary lib,
                        @Cached ArrayNodes.GetValueNode getValueNode) {
            for (int i = 0; i < self.getLength(); i++) {
                Object item = getValueNode.execute(self, i);
                if (lib.equalsWithFrame(item, value, lib, frame)) {
                    self.delSlice(i, 1);
                    return PNone.NONE;
                }
            }
            throw raise(ValueError, "array.remove(x): x not in array");
        }
    }

    @Builtin(name = "pop", minNumOfPositionalArgs = 1, numOfPositionalOnlyArgs = 2, parameterNames = {"$self", "index"})
    @ArgumentClinic(name = "index", conversion = ArgumentClinic.ClinicConversion.Index, defaultValue = "-1")
    @GenerateNodeFactory
    public abstract static class PopNode extends PythonBinaryClinicBuiltinNode {
        @Specialization
        Object pop(PArray self, int inputIndex,
                        @Cached("forPop()") NormalizeIndexNode normalizeIndexNode,
                        @Cached ArrayNodes.GetValueNode getValueNode) {
            if (self.getLength() == 0) {
                throw raise(IndexError, "pop from empty array");
            }
            int index = normalizeIndexNode.execute(inputIndex, self.getLength());
            Object value = getValueNode.execute(self, index);
            self.delSlice(index, 1);
            return value;
        }

        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return ArrayBuiltinsClinicProviders.PopNodeClinicProviderGen.INSTANCE;
        }
    }
}
