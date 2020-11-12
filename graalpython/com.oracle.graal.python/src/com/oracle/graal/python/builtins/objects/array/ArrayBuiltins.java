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

import static com.oracle.graal.python.builtins.PythonBuiltinClassType.TypeError;
import static com.oracle.graal.python.builtins.PythonBuiltinClassType.ValueError;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__ADD__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__EQ__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__GETITEM__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__ITER__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__LEN__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__REPR__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__SETITEM__;

import java.util.List;

import com.oracle.graal.python.annotations.ArgumentClinic;
import com.oracle.graal.python.builtins.Builtin;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.PNotImplemented;
import com.oracle.graal.python.builtins.objects.common.BufferStorageNodes;
import com.oracle.graal.python.builtins.objects.common.IndexNodes.NormalizeIndexNode;
import com.oracle.graal.python.builtins.objects.object.PythonObjectLibrary;
import com.oracle.graal.python.builtins.objects.slice.PSlice;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.builtins.PythonBinaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonTernaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonTernaryClinicBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonUnaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.clinic.ArgumentClinicProvider;
import com.oracle.graal.python.nodes.subscript.SliceLiteralNode;
import com.oracle.graal.python.nodes.util.CastToJavaStringNode;
import com.oracle.graal.python.util.BufferFormat;
import com.oracle.graal.python.util.PythonUtils;
import com.oracle.truffle.api.dsl.Cached;
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

        @Specialization
        Object error(@SuppressWarnings("unused") PArray left, Object right) {
            throw raise(TypeError, "can only append array (not \"%p\") to array", right);
        }
    }

    // TODO richcompare

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

    @Builtin(name = __REPR__, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class ReprNode extends PythonUnaryBuiltinNode {
        @Specialization
        static String repr(PArray self,
                        @CachedLibrary(limit = "3") PythonObjectLibrary lib,
                        @Cached CastToJavaStringNode cast,
                        @Cached BufferStorageNodes.UnpackValueNode unpackValueNode) {
            int itemsize = self.getFormat().bytesize;
            StringBuilder sb = PythonUtils.newStringBuilder();
            PythonUtils.append(sb, "array('");
            PythonUtils.append(sb, self.getFormatStr());
            PythonUtils.append(sb, "', [");
            for (int i = 0; i < self.getLength(); i++) {
                if (i > 0) {
                    PythonUtils.append(sb, ", ");
                }
                Object value = unpackValueNode.execute(self.getFormat(), self.getBuffer(), i * itemsize);
                PythonUtils.append(sb, cast.execute(lib.asPString(value)));
            }
            PythonUtils.append(sb, "])");
            return PythonUtils.sbToString(sb);
        }
    }

    @Builtin(name = __GETITEM__, minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    public abstract static class GetItemNode extends PythonBinaryBuiltinNode {

        @Specialization(guards = "!isPSlice(idx)")
        static Object getitem(PArray self, Object idx,
                        @Cached("forArray()") NormalizeIndexNode normalizeIndexNode,
                        @Cached BufferStorageNodes.UnpackValueNode unpackValueNode) {
            int index = normalizeIndexNode.execute(idx, self.getLength());
            int itemsize = self.getFormat().bytesize;
            return unpackValueNode.execute(self.getFormat(), self.getBuffer(), index * itemsize);
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
                PythonUtils.arraycopy(self.getBuffer(), sliceInfo.start * itemsize, newArray.getBuffer(), 0, newArray.getBuffer().length);
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

        @Specialization(guards = "!isPSlice(idx)")
        static Object setitem(VirtualFrame frame, PArray self, Object idx, Object value,
                        @Cached("forArrayAssign()") NormalizeIndexNode normalizeIndexNode,
                        @Cached BufferStorageNodes.PackValueNode packValueNode) {
            int index = normalizeIndexNode.execute(idx, self.getLength());
            int itemsize = self.getFormat().bytesize;
            packValueNode.execute(frame, self.getFormat(), value, self.getBuffer(), index * itemsize);
            return PNone.NONE;
        }

        @Specialization(guards = "self.getFormat() == other.getFormat()")
        Object setitem(PArray self, PSlice slice, PArray other,
                        @Cached ConditionProfile sameArrayProfile,
                        @Cached ConditionProfile simpleStepProfile,
                        @Cached ConditionProfile differentLenghtProfile,
                        @Cached SliceLiteralNode.SliceUnpack sliceUnpack,
                        @Cached SliceLiteralNode.AdjustIndices adjustIndices) {
            PSlice.SliceInfo sliceInfo = adjustIndices.execute(self.getLength(), sliceUnpack.execute(slice));
            int itemsize = self.getFormat().bytesize;
            byte[] targetBuffer = self.getBuffer();
            byte[] sourceBuffer = other.getBuffer();
            int needed = other.getLength();
            if (sameArrayProfile.profile(sourceBuffer == targetBuffer)) {
                sourceBuffer = new byte[needed * itemsize];
                PythonUtils.arraycopy(targetBuffer, 0, sourceBuffer, 0, sourceBuffer.length);
            }
            if (simpleStepProfile.profile(sliceInfo.step == 1)) {
                int start = sliceInfo.start;
                int stop = sliceInfo.stop;
                if (differentLenghtProfile.profile(sliceInfo.sliceLength != needed)) {
                    int newLength = self.getLength() - sliceInfo.sliceLength + needed;
                    // TODO shrink when much smaller
                    self.ensureCapacityNoCopy(newLength);
                    byte[] newBuffer = self.getBuffer();
                    PythonUtils.arraycopy(targetBuffer, 0, newBuffer, 0, start * itemsize);
                    PythonUtils.arraycopy(targetBuffer, stop * itemsize, newBuffer, (start + needed) * itemsize, (self.getLength() - stop) * itemsize);
                    self.setLenght(newLength);
                    targetBuffer = newBuffer;
                }
                PythonUtils.arraycopy(sourceBuffer, 0, targetBuffer, start * itemsize, needed * itemsize);
            } else {
                // TODO allow empty other (delete)
                throw raise(ValueError, "attempt to assign array of size %d to extended slice of size %d", needed, sliceInfo.sliceLength);
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

    @Builtin(name = __ITER__, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class IterNode extends PythonUnaryBuiltinNode {

        @Specialization
        Object getitem(PArray self) {
            return factory().createArrayIterator(self);
        }
    }

    @Builtin(name = "itemsize", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class ItemSizeNode extends PythonUnaryBuiltinNode {

        @Specialization
        static int getItemSize(PArray self) {
            return self.getFormat().bytesize;
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

    @Builtin(name = "append", minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    public abstract static class ArrayAppendNode extends PythonBinaryBuiltinNode {
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

    @Builtin(name = "insert", minNumOfPositionalArgs = 3, numOfPositionalOnlyArgs = 3, parameterNames = {"$self", "index", "value"})
    @ArgumentClinic(name = "index", conversion = ArgumentClinic.ClinicConversion.Index, defaultValue = "0")
    @GenerateNodeFactory
    public abstract static class ArrayInsertNode extends PythonTernaryClinicBuiltinNode {
        @Specialization
        static Object insert(VirtualFrame frame, PArray self, int index, Object value,
                        @Cached ArrayNodes.PutValueNode putValueNode) {
            int itemsize = self.getFormat().bytesize;
            int newLength = self.getLength() + 1;
            byte[] oldBuffer = self.getBuffer();
            self.ensureCapacityNoCopy(newLength);
            byte[] newBuffer = self.getBuffer();
            if (oldBuffer != newBuffer) {
                PythonUtils.arraycopy(oldBuffer, 0, newBuffer, 0, index * itemsize);
            }
            PythonUtils.arraycopy(oldBuffer, index * itemsize, newBuffer, (index + 1) * itemsize, (newLength - index) * itemsize);
            self.setLenght(newLength);
            putValueNode.execute(frame, self, index, value);
            return PNone.NONE;
        }

        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return ArrayBuiltinsClinicProviders.ArrayInsertNodeClinicProviderGen.INSTANCE;
        }
    }
}
