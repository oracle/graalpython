/*
 * Copyright (c) 2017, 2024, Oracle and/or its affiliates.
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

import static com.oracle.graal.python.builtins.PythonBuiltinClassType.EOFError;
import static com.oracle.graal.python.builtins.PythonBuiltinClassType.IndexError;
import static com.oracle.graal.python.builtins.PythonBuiltinClassType.MemoryError;
import static com.oracle.graal.python.builtins.PythonBuiltinClassType.NotImplementedError;
import static com.oracle.graal.python.builtins.PythonBuiltinClassType.TypeError;
import static com.oracle.graal.python.builtins.PythonBuiltinClassType.ValueError;
import static com.oracle.graal.python.builtins.modules.io.IONodes.T_READ;
import static com.oracle.graal.python.builtins.modules.io.IONodes.T_WRITE;
import static com.oracle.graal.python.builtins.objects.common.IndexNodes.checkBounds;
import static com.oracle.graal.python.nodes.BuiltinNames.J_APPEND;
import static com.oracle.graal.python.nodes.BuiltinNames.J_ARRAY;
import static com.oracle.graal.python.nodes.BuiltinNames.J_EXTEND;
import static com.oracle.graal.python.nodes.BuiltinNames.T_ARRAY;
import static com.oracle.graal.python.nodes.ErrorMessages.BAD_ARG_TYPE_FOR_BUILTIN_OP;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.T___DICT__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___ADD__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___CONTAINS__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___DELITEM__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___EQ__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___GE__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___GT__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___IADD__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___IMUL__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___ITER__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___LE__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___LT__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___MUL__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___NE__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___REDUCE_EX__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___REPR__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___RMUL__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___SETITEM__;
import static com.oracle.graal.python.nodes.StringLiterals.T_COMMA_SPACE;
import static com.oracle.graal.python.nodes.StringLiterals.T_LBRACKET;
import static com.oracle.graal.python.nodes.StringLiterals.T_LPAREN;
import static com.oracle.graal.python.nodes.StringLiterals.T_RBRACKET;
import static com.oracle.graal.python.nodes.StringLiterals.T_RPAREN;
import static com.oracle.graal.python.nodes.StringLiterals.T_SINGLE_QUOTE;
import static com.oracle.graal.python.util.PythonUtils.TS_ENCODING;
import static com.oracle.graal.python.util.PythonUtils.tsLiteral;

import java.util.List;

import com.oracle.graal.python.annotations.ArgumentClinic;
import com.oracle.graal.python.annotations.Slot;
import com.oracle.graal.python.annotations.Slot.SlotKind;
import com.oracle.graal.python.builtins.Builtin;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.PNotImplemented;
import com.oracle.graal.python.builtins.objects.array.ArrayBuiltinsClinicProviders.ReduceExNodeClinicProviderGen;
import com.oracle.graal.python.builtins.objects.array.ArrayNodes.DeleteArraySliceNode;
import com.oracle.graal.python.builtins.objects.array.ArrayNodes.GetValueNode;
import com.oracle.graal.python.builtins.objects.buffer.PythonBufferAccessLibrary;
import com.oracle.graal.python.builtins.objects.bytes.PBytes;
import com.oracle.graal.python.builtins.objects.common.IndexNodes.NormalizeIndexNode;
import com.oracle.graal.python.builtins.objects.common.SequenceNodes;
import com.oracle.graal.python.builtins.objects.common.SequenceStorageNodes;
import com.oracle.graal.python.builtins.objects.list.PList;
import com.oracle.graal.python.builtins.objects.module.PythonModule;
import com.oracle.graal.python.builtins.objects.slice.PSlice;
import com.oracle.graal.python.builtins.objects.slice.SliceNodes;
import com.oracle.graal.python.builtins.objects.tuple.PTuple;
import com.oracle.graal.python.builtins.objects.type.TpSlots;
import com.oracle.graal.python.builtins.objects.type.slots.TpSlotBinaryFunc.MpSubscriptBuiltinNode;
import com.oracle.graal.python.builtins.objects.type.slots.TpSlotLen.LenBuiltinNode;
import com.oracle.graal.python.builtins.objects.type.slots.TpSlotSizeArgFun.SqItemBuiltinNode;
import com.oracle.graal.python.lib.GetNextNode;
import com.oracle.graal.python.lib.PyIndexCheckNode;
import com.oracle.graal.python.lib.PyNumberAsSizeNode;
import com.oracle.graal.python.lib.PyNumberIndexNode;
import com.oracle.graal.python.lib.PyObjectCallMethodObjArgs;
import com.oracle.graal.python.lib.PyObjectGetAttr;
import com.oracle.graal.python.lib.PyObjectGetIter;
import com.oracle.graal.python.lib.PyObjectLookupAttr;
import com.oracle.graal.python.lib.PyObjectRichCompareBool;
import com.oracle.graal.python.lib.PyObjectSizeNode;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PGuards;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.PRaiseNode.Lazy;
import com.oracle.graal.python.nodes.builtins.ListNodes;
import com.oracle.graal.python.nodes.call.special.LookupAndCallUnaryNode;
import com.oracle.graal.python.nodes.expression.BinaryComparisonNode;
import com.oracle.graal.python.nodes.expression.CoerceToBooleanNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.builtins.PythonBinaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonBinaryClinicBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonQuaternaryClinicBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonTernaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonTernaryClinicBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonUnaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.clinic.ArgumentClinicProvider;
import com.oracle.graal.python.nodes.object.BuiltinClassProfiles.IsBuiltinObjectProfile;
import com.oracle.graal.python.nodes.object.GetClassNode;
import com.oracle.graal.python.nodes.util.CastToTruffleStringNode;
import com.oracle.graal.python.runtime.IndirectCallData;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.graal.python.runtime.exception.PythonErrorType;
import com.oracle.graal.python.runtime.object.PythonObjectFactory;
import com.oracle.graal.python.runtime.sequence.PSequence;
import com.oracle.graal.python.runtime.sequence.storage.ByteSequenceStorage;
import com.oracle.graal.python.runtime.sequence.storage.SequenceStorage;
import com.oracle.graal.python.util.BufferFormat;
import com.oracle.graal.python.util.ComparisonOp;
import com.oracle.graal.python.util.OverflowException;
import com.oracle.graal.python.util.PythonUtils;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.HostCompilerDirectives.InliningCutoff;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Exclusive;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.GenerateCached;
import com.oracle.truffle.api.dsl.GenerateInline;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.profiles.InlinedByteValueProfile;
import com.oracle.truffle.api.profiles.InlinedConditionProfile;
import com.oracle.truffle.api.strings.TruffleString;
import com.oracle.truffle.api.strings.TruffleStringBuilder;
import com.oracle.truffle.api.strings.TruffleStringIterator;

@CoreFunctions(extendClasses = PythonBuiltinClassType.PArray)
public final class ArrayBuiltins extends PythonBuiltins {
    public static final TpSlots SLOTS = ArrayBuiltinsSlotsGen.SLOTS;

    private static final TruffleString T_ARRAY_RECONSTRUCTOR = tsLiteral("_array_reconstructor");

    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return ArrayBuiltinsFactory.getFactories();
    }

    @Builtin(name = J___ADD__, minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    abstract static class AddNode extends PythonBinaryBuiltinNode {
        @Specialization(guards = "left.getFormat() == right.getFormat()")
        Object concat(PArray left, PArray right,
                        @CachedLibrary(limit = "2") PythonBufferAccessLibrary bufferLib,
                        @Cached PythonObjectFactory factory) {
            try {
                int newLength = PythonUtils.addExact(left.getLength(), right.getLength());
                int itemShift = left.getItemSizeShift();
                PArray newArray = factory.createArray(left.getFormatString(), left.getFormat(), newLength);
                bufferLib.readIntoBuffer(left.getBuffer(), 0, newArray.getBuffer(), 0, left.getLength() << itemShift, bufferLib);
                bufferLib.readIntoBuffer(right.getBuffer(), 0, newArray.getBuffer(), left.getLength() << itemShift, right.getLength() << itemShift, bufferLib);
                return newArray;
            } catch (OverflowException e) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                throw PRaiseNode.raiseUncached(this, MemoryError);
            }
        }

        @Specialization(guards = "left.getFormat() != right.getFormat()")
        @SuppressWarnings("unused")
        static Object error(PArray left, PArray right,
                        @Shared @Cached PRaiseNode raiseNode) {
            throw raiseNode.raise(TypeError, BAD_ARG_TYPE_FOR_BUILTIN_OP);
        }

        @Fallback
        static Object error(@SuppressWarnings("unused") Object left, Object right,
                        @Shared @Cached PRaiseNode raiseNode) {
            throw raiseNode.raise(TypeError, ErrorMessages.CAN_ONLY_APPEND_ARRAY_TO_ARRAY, right);
        }
    }

    @Builtin(name = J___IADD__, minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    abstract static class IAddNode extends PythonBinaryBuiltinNode {
        @Specialization
        static Object concat(VirtualFrame frame, PArray left, PArray right,
                        @Cached ExtendNode extendNode) {
            extendNode.execute(frame, left, right);
            return left;
        }

        @Fallback
        static Object error(@SuppressWarnings("unused") Object left, Object right,
                        @Cached PRaiseNode raiseNode) {
            throw raiseNode.raise(TypeError, ErrorMessages.CAN_ONLY_EXTEND_ARRAY_WITH_ARRAY, right);
        }
    }

    @Builtin(name = J___MUL__, minNumOfPositionalArgs = 2, numOfPositionalOnlyArgs = 2, parameterNames = {"$self", "value"})
    @ArgumentClinic(name = "value", conversion = ArgumentClinic.ClinicConversion.Index)
    @GenerateNodeFactory
    abstract static class MulNode extends PythonBinaryClinicBuiltinNode {
        @Specialization
        Object concat(PArray self, int value,
                        @CachedLibrary(limit = "2") PythonBufferAccessLibrary bufferLib,
                        @Cached PythonObjectFactory factory) {
            try {
                int newLength = Math.max(PythonUtils.multiplyExact(self.getLength(), value), 0);
                PArray newArray = factory.createArray(self.getFormatString(), self.getFormat(), newLength);
                int segmentLength = self.getBytesLength();
                for (int i = 0; i < value; i++) {
                    bufferLib.readIntoBuffer(self.getBuffer(), 0, newArray.getBuffer(), segmentLength * i, segmentLength, bufferLib);
                }
                return newArray;
            } catch (OverflowException e) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                throw PRaiseNode.raiseUncached(this, MemoryError);
            }
        }

        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return ArrayBuiltinsClinicProviders.MulNodeClinicProviderGen.INSTANCE;
        }
    }

    @Builtin(name = J___RMUL__, minNumOfPositionalArgs = 2, numOfPositionalOnlyArgs = 2, parameterNames = {"$self", "value"})
    abstract static class RMulNode extends MulNode {
    }

    @Builtin(name = J___IMUL__, minNumOfPositionalArgs = 2, numOfPositionalOnlyArgs = 2, parameterNames = {"$self", "value"})
    @ArgumentClinic(name = "value", conversion = ArgumentClinic.ClinicConversion.Index)
    @GenerateNodeFactory
    abstract static class IMulNode extends PythonBinaryClinicBuiltinNode {
        @Specialization
        static Object concat(PArray self, int value,
                        @Bind("this") Node inliningTarget,
                        @CachedLibrary(limit = "2") PythonBufferAccessLibrary bufferLib,
                        @Cached ArrayNodes.EnsureCapacityNode ensureCapacityNode,
                        @Cached ArrayNodes.SetLengthNode setLengthNode,
                        @Cached PRaiseNode.Lazy raiseNode) {
            try {
                int newLength = Math.max(PythonUtils.multiplyExact(self.getLength(), value), 0);
                if (newLength != self.getLength()) {
                    self.checkCanResize(inliningTarget, raiseNode);
                }
                int segmentLength = self.getBytesLength();
                ensureCapacityNode.execute(inliningTarget, self, newLength);
                setLengthNode.execute(inliningTarget, self, newLength);
                for (int i = 0; i < value; i++) {
                    bufferLib.readIntoBuffer(self.getBuffer(), 0, self.getBuffer(), segmentLength * i, segmentLength, bufferLib);
                }
                return self;
            } catch (OverflowException e) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                throw PRaiseNode.raiseUncached(inliningTarget, MemoryError);
            }
        }

        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return ArrayBuiltinsClinicProviders.IMulNodeClinicProviderGen.INSTANCE;
        }
    }

    @GenerateInline
    @GenerateCached(false)
    @ImportStatic({BufferFormat.class, PGuards.class})
    abstract static class EqNeHelperNode extends Node {

        abstract Object execute(VirtualFrame frame, Node inliningTarget, Object left, Object right, ComparisonOp op);

        @Specialization(guards = {"left.getFormat() == right.getFormat()", "!isFloatingPoint(left.getFormat())"})
        static boolean eqBytes(PArray left, PArray right, ComparisonOp op,
                        @CachedLibrary(limit = "2") PythonBufferAccessLibrary bufferLib) {
            if (left.getBytesLength() != right.getBytesLength()) {
                return op == ComparisonOp.NE;
            }
            for (int i = 0; i < left.getBytesLength(); i++) {
                if (bufferLib.readByte(left.getBuffer(), i) != bufferLib.readByte(right.getBuffer(), i)) {
                    return op == ComparisonOp.NE;
                }
            }
            return op == ComparisonOp.EQ;
        }

        @Specialization(guards = "left.getFormat() != right.getFormat()")
        static boolean eqItems(VirtualFrame frame, Node inliningTarget, PArray left, PArray right, ComparisonOp op,
                        @Cached PyObjectRichCompareBool.EqNode eqNode,
                        @Exclusive @Cached ArrayNodes.GetValueNode getLeft,
                        @Exclusive @Cached ArrayNodes.GetValueNode getRight) {
            if (left.getLength() != right.getLength()) {
                return op == ComparisonOp.NE;
            }
            for (int i = 0; i < left.getLength(); i++) {
                if (!eqNode.compare(frame, inliningTarget, getLeft.execute(inliningTarget, left, i), getRight.execute(inliningTarget, right, i))) {
                    return op == ComparisonOp.NE;
                }
            }
            return op == ComparisonOp.EQ;
        }

        // Separate specialization for float/double is needed because of NaN comparisons
        @Specialization(guards = {"left.getFormat() == right.getFormat()", "isFloatingPoint(left.getFormat())"})
        static boolean eqDoubles(Node inliningTarget, PArray left, PArray right, ComparisonOp op,
                        @Exclusive @Cached ArrayNodes.GetValueNode getLeft,
                        @Exclusive @Cached ArrayNodes.GetValueNode getRight) {
            if (left.getLength() != right.getLength()) {
                return op == ComparisonOp.NE;
            }
            for (int i = 0; i < left.getLength(); i++) {
                double leftValue = (Double) getLeft.execute(inliningTarget, left, i);
                double rightValue = (Double) getRight.execute(inliningTarget, right, i);
                if (leftValue != rightValue) {
                    return op == ComparisonOp.NE;
                }
            }
            return op == ComparisonOp.EQ;
        }

        @Specialization(guards = "!isArray(right)")
        @SuppressWarnings("unused")
        static Object eq(PArray left, Object right, ComparisonOp op) {
            return PNotImplemented.NOT_IMPLEMENTED;
        }

        @Specialization(guards = "!isArray(left)")
        @SuppressWarnings("unused")
        static Object error(Object left, Object right, ComparisonOp op,
                        @Cached(inline = false) PRaiseNode raiseNode) {
            throw raiseNode.raise(PythonErrorType.TypeError, ErrorMessages.DESCRIPTOR_S_REQUIRES_S_OBJ_RECEIVED_P, op.builtinName, J_ARRAY + "." + J_ARRAY, left);
        }
    }

    @Builtin(name = J___EQ__, minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    abstract static class EqNode extends PythonBinaryBuiltinNode {

        @Specialization
        static Object eqBytes(VirtualFrame frame, Object left, Object right,
                        @Bind("this") Node inliningTarget,
                        @Cached EqNeHelperNode helperNode) {
            return helperNode.execute(frame, inliningTarget, left, right, ComparisonOp.EQ);
        }
    }

    @Builtin(name = J___NE__, minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    abstract static class NeNode extends PythonBinaryBuiltinNode {

        @Specialization
        static Object eqBytes(VirtualFrame frame, Object left, Object right,
                        @Bind("this") Node inliningTarget,
                        @Cached EqNeHelperNode helperNode) {
            return helperNode.execute(frame, inliningTarget, left, right, ComparisonOp.NE);
        }
    }

    @GenerateInline
    @GenerateCached(false)
    @ImportStatic({BufferFormat.class, PGuards.class})
    abstract static class ComparisonHelperNode extends Node {

        abstract Object execute(VirtualFrame frame, Node inliningTarget, Object left, Object right, ComparisonOp op, BinaryComparisonNode compareNode);

        @Specialization(guards = "!isFloatingPoint(left.getFormat()) || (left.getFormat() != right.getFormat())")
        static boolean cmpItems(VirtualFrame frame, Node inliningTarget, PArray left, PArray right, ComparisonOp op, BinaryComparisonNode compareNode,
                        @Cached PyObjectRichCompareBool.EqNode eqNode,
                        @Exclusive @Cached CoerceToBooleanNode.YesNode coerceToBooleanNode,
                        @Exclusive @Cached ArrayNodes.GetValueNode getLeft,
                        @Exclusive @Cached ArrayNodes.GetValueNode getRight) {
            int commonLength = Math.min(left.getLength(), right.getLength());
            for (int i = 0; i < commonLength; i++) {
                Object leftValue = getLeft.execute(inliningTarget, left, i);
                Object rightValue = getRight.execute(inliningTarget, right, i);
                if (!eqNode.compare(frame, inliningTarget, leftValue, rightValue)) {
                    return coerceToBooleanNode.executeBoolean(frame, inliningTarget, compareNode.executeObject(frame, leftValue, rightValue));
                }
            }
            return op.cmpResultToBool(left.getLength() - right.getLength());
        }

        // Separate specialization for float/double is needed because of NaN comparisons
        @Specialization(guards = {"isFloatingPoint(left.getFormat())", "left.getFormat() == right.getFormat()"})
        static boolean cmpDoubles(VirtualFrame frame, Node inliningTarget, PArray left, PArray right, ComparisonOp op, BinaryComparisonNode compareNode,
                        @Exclusive @Cached CoerceToBooleanNode.YesNode coerceToBooleanNode,
                        @Exclusive @Cached ArrayNodes.GetValueNode getLeft,
                        @Exclusive @Cached ArrayNodes.GetValueNode getRight) {
            int commonLength = Math.min(left.getLength(), right.getLength());
            for (int i = 0; i < commonLength; i++) {
                double leftValue = (Double) getLeft.execute(inliningTarget, left, i);
                double rightValue = (Double) getRight.execute(inliningTarget, right, i);
                if (leftValue != rightValue) {
                    return coerceToBooleanNode.executeBoolean(frame, inliningTarget, compareNode.executeObject(frame, leftValue, rightValue));
                }
            }
            return op.cmpResultToBool(left.getLength() - right.getLength());
        }

        @Specialization(guards = "!isArray(right)")
        @SuppressWarnings("unused")
        static Object cmp(PArray left, Object right, ComparisonOp op, BinaryComparisonNode compareNode) {
            return PNotImplemented.NOT_IMPLEMENTED;
        }

        @Specialization(guards = "!isArray(left)")
        @SuppressWarnings("unused")
        static Object error(Object left, Object right, ComparisonOp op, BinaryComparisonNode compareNode,
                        @Cached(inline = false) PRaiseNode raiseNode) {
            throw raiseNode.raise(PythonErrorType.TypeError, ErrorMessages.DESCRIPTOR_S_REQUIRES_S_OBJ_RECEIVED_P, op.builtinName, J_ARRAY + "." + J_ARRAY, left);
        }

    }

    @Builtin(name = J___LT__, minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    abstract static class LtNode extends PythonBinaryBuiltinNode {

        @Specialization
        static Object cmp(VirtualFrame frame, Object left, Object right,
                        @Bind("this") Node inliningTarget,
                        @Cached ComparisonHelperNode helperNode,
                        @Cached BinaryComparisonNode.LtNode cmpNode) {
            return helperNode.execute(frame, inliningTarget, left, right, ComparisonOp.LT, cmpNode);
        }
    }

    @Builtin(name = J___GT__, minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    abstract static class GtNode extends PythonBinaryBuiltinNode {

        @Specialization
        static Object cmp(VirtualFrame frame, Object left, Object right,
                        @Bind("this") Node inliningTarget,
                        @Cached ComparisonHelperNode helperNode,
                        @Cached BinaryComparisonNode.GtNode cmpNode) {
            return helperNode.execute(frame, inliningTarget, left, right, ComparisonOp.GT, cmpNode);
        }
    }

    @Builtin(name = J___LE__, minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    abstract static class LeNode extends PythonBinaryBuiltinNode {

        @Specialization
        static Object cmp(VirtualFrame frame, Object left, Object right,
                        @Bind("this") Node inliningTarget,
                        @Cached ComparisonHelperNode helperNode,
                        @Cached BinaryComparisonNode.LeNode cmpNode) {
            return helperNode.execute(frame, inliningTarget, left, right, ComparisonOp.LE, cmpNode);
        }
    }

    @Builtin(name = J___GE__, minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    abstract static class GeNode extends PythonBinaryBuiltinNode {

        @Specialization
        static Object cmp(VirtualFrame frame, Object left, Object right,
                        @Bind("this") Node inliningTarget,
                        @Cached ComparisonHelperNode helperNode,
                        @Cached BinaryComparisonNode.GeNode cmpNode) {
            return helperNode.execute(frame, inliningTarget, left, right, ComparisonOp.GE, cmpNode);
        }
    }

    @Builtin(name = J___CONTAINS__, minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    abstract static class ContainsNode extends PythonBinaryBuiltinNode {
        @Specialization
        static boolean contains(VirtualFrame frame, PArray self, Object value,
                        @Bind("this") Node inliningTarget,
                        @Cached PyObjectRichCompareBool.EqNode eqNode,
                        @Cached ArrayNodes.GetValueNode getValueNode) {
            for (int i = 0; i < self.getLength(); i++) {
                if (eqNode.compare(frame, inliningTarget, getValueNode.execute(inliningTarget, self, i), value)) {
                    return true;
                }
            }
            return false;
        }
    }

    @Builtin(name = J___REPR__, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class ReprNode extends PythonUnaryBuiltinNode {
        @Specialization
        static TruffleString repr(VirtualFrame frame, PArray self,
                        @Bind("this") Node inliningTarget,
                        @Cached("create(Repr)") LookupAndCallUnaryNode reprNode,
                        @Cached InlinedConditionProfile isEmptyProfile,
                        @Cached InlinedConditionProfile isUnicodeProfile,
                        @Cached CastToTruffleStringNode cast,
                        @Cached ToUnicodeNode toUnicodeNode,
                        @Cached ArrayNodes.GetValueNode getValueNode,
                        @Cached TruffleStringBuilder.AppendStringNode appendStringNode,
                        @Cached TruffleStringBuilder.ToStringNode toStringNode) {
            TruffleStringBuilder sb = TruffleStringBuilder.create(TS_ENCODING);
            appendStringNode.execute(sb, T_ARRAY);
            appendStringNode.execute(sb, T_LPAREN);
            appendStringNode.execute(sb, T_SINGLE_QUOTE);
            appendStringNode.execute(sb, self.getFormatString());
            appendStringNode.execute(sb, T_SINGLE_QUOTE);
            int length = self.getLength();
            if (isEmptyProfile.profile(inliningTarget, length != 0)) {
                if (isUnicodeProfile.profile(inliningTarget, self.getFormat() == BufferFormat.UNICODE)) {
                    appendStringNode.execute(sb, T_COMMA_SPACE);
                    appendStringNode.execute(sb, cast.execute(inliningTarget, reprNode.executeObject(frame, toUnicodeNode.execute(frame, self))));
                } else {
                    appendStringNode.execute(sb, T_COMMA_SPACE);
                    appendStringNode.execute(sb, T_LBRACKET);
                    for (int i = 0; i < length; i++) {
                        if (i > 0) {
                            appendStringNode.execute(sb, T_COMMA_SPACE);
                        }
                        Object value = getValueNode.execute(inliningTarget, self, i);
                        appendStringNode.execute(sb, cast.execute(inliningTarget, reprNode.executeObject(frame, value)));
                    }
                    appendStringNode.execute(sb, T_RBRACKET);
                }
            }
            appendStringNode.execute(sb, T_RPAREN);
            return toStringNode.execute(sb);
        }
    }

    @Slot(value = SlotKind.sq_item, isComplex = true)
    @GenerateNodeFactory
    abstract static class SqItemNode extends SqItemBuiltinNode {
        @Specialization
        static Object doIt(VirtualFrame frame, PArray self, int index,
                        @Bind("this") Node inliningTarget,
                        @Cached PRaiseNode.Lazy raiseNode,
                        @Cached ArrayNodes.GetValueNode getValueNode) {
            return getItem(inliningTarget, self, index, raiseNode, getValueNode);
        }

        private static Object getItem(Node inliningTarget, PArray self, int index, PRaiseNode.Lazy raiseNode, GetValueNode getValueNode) {
            checkBounds(inliningTarget, raiseNode, ErrorMessages.ARRAY_OUT_OF_BOUNDS, index, self.getLength());
            return getValueNode.execute(inliningTarget, self, index);
        }
    }

    @Slot(value = SlotKind.mp_subscript, isComplex = true)
    @GenerateNodeFactory
    abstract static class MpSubscriptNode extends MpSubscriptBuiltinNode {
        @Specialization(guards = "!isPSlice(idx)")
        static Object doIndex(VirtualFrame frame, PArray self, Object idx,
                        @Bind("this") Node inliningTarget,
                        @Cached PyIndexCheckNode indexCheckNode,
                        @Cached PRaiseNode.Lazy raiseNode,
                        @Cached PyNumberAsSizeNode numberAsSizeNode,
                        @Exclusive @Cached InlinedConditionProfile negativeIndexProfile,
                        @Cached ArrayNodes.GetValueNode getValueNode) {
            if (!indexCheckNode.execute(inliningTarget, idx)) {
                throw raiseNonIntIndex(inliningTarget, raiseNode);
            }
            int index = numberAsSizeNode.executeExact(frame, inliningTarget, idx, IndexError);
            if (negativeIndexProfile.profile(inliningTarget, index < 0)) {
                index += self.getLength();
            }
            return SqItemNode.getItem(inliningTarget, self, index, raiseNode, getValueNode);
        }

        @Specialization
        static Object doSlice(PArray self, PSlice slice,
                        @Bind("this") Node inliningTarget,
                        @CachedLibrary(limit = "2") PythonBufferAccessLibrary bufferLib,
                        @Cached InlinedByteValueProfile itemShiftProfile,
                        @Exclusive @Cached InlinedConditionProfile simpleStepProfile,
                        @Cached SliceNodes.SliceUnpack sliceUnpack,
                        @Cached SliceNodes.AdjustIndices adjustIndices,
                        @Cached PythonObjectFactory factory) {
            PSlice.SliceInfo sliceInfo = adjustIndices.execute(inliningTarget, self.getLength(), sliceUnpack.execute(inliningTarget, slice));
            int itemShift = itemShiftProfile.profile(inliningTarget, (byte) self.getItemSizeShift());
            int itemsize = self.getItemSize();
            PArray newArray;
            try {
                newArray = factory.createArray(self.getFormatString(), self.getFormat(), sliceInfo.sliceLength);
            } catch (OverflowException e) {
                // It's a slice of existing array, the length cannot overflow
                throw CompilerDirectives.shouldNotReachHere();
            }

            if (simpleStepProfile.profile(inliningTarget, sliceInfo.step == 1)) {
                bufferLib.readIntoBuffer(self.getBuffer(), sliceInfo.start << itemShift, newArray.getBuffer(), 0, sliceInfo.sliceLength << itemShift, bufferLib);
            } else {
                for (int i = sliceInfo.start, j = 0; j < sliceInfo.sliceLength; i += sliceInfo.step, j++) {
                    bufferLib.readIntoBuffer(self.getBuffer(), i << itemShift, newArray.getBuffer(), j << itemShift, itemsize, bufferLib);
                }
            }
            return newArray;
        }

        @InliningCutoff
        private static PException raiseNonIntIndex(Node inliningTarget, Lazy raiseNode) {
            throw raiseNode.get(inliningTarget).raise(PythonBuiltinClassType.TypeError, ErrorMessages.ARRAY_INDICES_MUST_BE_INTS);
        }
    }

    @Builtin(name = J___SETITEM__, minNumOfPositionalArgs = 3)
    @GenerateNodeFactory
    abstract static class SetItemNode extends PythonTernaryBuiltinNode {

        @Specialization(guards = "!isPSlice(idx)")
        static Object setitem(VirtualFrame frame, PArray self, Object idx, Object value,
                        @Bind("this") Node inliningTarget,
                        @Cached PyNumberIndexNode indexNode,
                        @Cached("forArrayAssign()") NormalizeIndexNode normalizeIndexNode,
                        @Cached ArrayNodes.PutValueNode putValueNode) {
            int index = normalizeIndexNode.execute(indexNode.execute(frame, inliningTarget, idx), self.getLength());
            putValueNode.execute(frame, inliningTarget, self, index, value);
            return PNone.NONE;
        }

        @Specialization(guards = "self.getFormat() == other.getFormat()")
        static Object setitem(VirtualFrame frame, PArray self, PSlice slice, PArray other,
                        @Bind("this") Node inliningTarget,
                        @CachedLibrary(limit = "2") PythonBufferAccessLibrary bufferLib,
                        @Cached InlinedConditionProfile sameArrayProfile,
                        @Cached InlinedConditionProfile simpleStepProfile,
                        @Cached InlinedConditionProfile complexDeleteProfile,
                        @Cached InlinedConditionProfile differentLengthProfile,
                        @Cached InlinedConditionProfile growProfile,
                        @Cached InlinedConditionProfile stepAssignProfile,
                        @Cached InlinedByteValueProfile itemShiftProfile,
                        @Cached SliceNodes.SliceUnpack sliceUnpack,
                        @Cached SliceNodes.AdjustIndices adjustIndices,
                        @Cached DeleteArraySliceNode deleteSliceNode,
                        @Cached ArrayNodes.ShiftNode shiftNode,
                        @Cached DelItemNode delItemNode,
                        @Cached PRaiseNode.Lazy raiseNode) {
            PSlice.SliceInfo sliceInfo = adjustIndices.execute(inliningTarget, self.getLength(), sliceUnpack.execute(inliningTarget, slice));
            int start = sliceInfo.start;
            int stop = sliceInfo.stop;
            int step = sliceInfo.step;
            int sliceLength = sliceInfo.sliceLength;
            int itemShift = itemShiftProfile.profile(inliningTarget, (byte) self.getItemSizeShift());
            int itemsize = self.getItemSize();
            Object sourceBuffer = other.getBuffer();
            int needed = other.getLength();
            if (sameArrayProfile.profile(inliningTarget, sourceBuffer == self.getBuffer())) {
                byte[] tmp = new byte[needed * itemsize];
                bufferLib.readIntoByteArray(other.getBuffer(), 0, tmp, 0, tmp.length);
                sourceBuffer = new ByteSequenceStorage(tmp);
            }
            if (simpleStepProfile.profile(inliningTarget, step == 1)) {
                if (differentLengthProfile.profile(inliningTarget, sliceLength != needed)) {
                    self.checkCanResize(inliningTarget, raiseNode);
                    if (growProfile.profile(inliningTarget, sliceLength < needed)) {
                        if (stop < start) {
                            stop = start;
                        }
                        shiftNode.execute(inliningTarget, self, stop, needed - sliceLength);
                    } else {
                        deleteSliceNode.execute(inliningTarget, self, start, sliceLength - needed);
                    }
                }
                bufferLib.readIntoBuffer(sourceBuffer, 0, self.getBuffer(), start << itemShift, needed << itemShift, bufferLib);
            } else if (complexDeleteProfile.profile(inliningTarget, needed == 0)) {
                delItemNode.executeSlice(frame, self, slice);
            } else if (stepAssignProfile.profile(inliningTarget, needed == sliceLength)) {
                for (int cur = start, i = 0; i < sliceLength; cur += step, i++) {
                    bufferLib.readIntoBuffer(sourceBuffer, i << itemShift, self.getBuffer(), cur << itemShift, itemsize, bufferLib);
                }
            } else {
                throw raiseNode.get(inliningTarget).raise(ValueError, ErrorMessages.ATTEMPT_ASSIGN_ARRAY_OF_SIZE, needed, sliceLength);
            }
            return PNone.NONE;
        }

        @Specialization(guards = "self.getFormat() != other.getFormat()")
        @SuppressWarnings("unused")
        static Object setitemWrongFormat(PArray self, PSlice slice, PArray other,
                        @Shared @Cached PRaiseNode raiseNode) {
            throw raiseNode.raise(TypeError, BAD_ARG_TYPE_FOR_BUILTIN_OP);
        }

        @Specialization(guards = "!isArray(other)")
        @SuppressWarnings("unused")
        static Object setitemWrongType(PArray self, PSlice slice, Object other,
                        @Shared @Cached PRaiseNode raiseNode) {
            throw raiseNode.raise(TypeError, ErrorMessages.CAN_ONLY_ASSIGN_ARRAY, other);
        }
    }

    @Builtin(name = J___DELITEM__, minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    abstract static class DelItemNode extends PythonBinaryBuiltinNode {
        public abstract Object executeSlice(VirtualFrame frame, PArray self, PSlice slice);

        @Specialization(guards = "!isPSlice(idx)")
        static Object delitem(VirtualFrame frame, PArray self, Object idx,
                        @Bind("this") Node inliningTarget,
                        @Cached PyNumberIndexNode indexNode,
                        @Cached("forArrayAssign()") NormalizeIndexNode normalizeIndexNode,
                        @Exclusive @Cached DeleteArraySliceNode deleteSliceNode,
                        @Exclusive @Cached PRaiseNode.Lazy raiseNode) {
            self.checkCanResize(inliningTarget, raiseNode);
            int index = normalizeIndexNode.execute(indexNode.execute(frame, inliningTarget, idx), self.getLength());
            deleteSliceNode.execute(inliningTarget, self, index, 1);
            return PNone.NONE;
        }

        @Specialization
        static Object delitem(PArray self, PSlice slice,
                        @Bind("this") Node inliningTarget,
                        @CachedLibrary(limit = "2") PythonBufferAccessLibrary bufferLib,
                        @Exclusive @Cached DeleteArraySliceNode deleteSliceNode,
                        @Cached InlinedByteValueProfile itemShiftProfile,
                        @Cached ArrayNodes.SetLengthNode setLengthNode,
                        @Cached InlinedConditionProfile simpleStepProfile,
                        @Cached SliceNodes.SliceUnpack sliceUnpack,
                        @Cached SliceNodes.AdjustIndices adjustIndices,
                        @Exclusive @Cached PRaiseNode.Lazy raiseNode) {
            self.checkCanResize(inliningTarget, raiseNode);
            int length = self.getLength();
            PSlice.SliceInfo sliceInfo = adjustIndices.execute(inliningTarget, length, sliceUnpack.execute(inliningTarget, slice));
            int start = sliceInfo.start;
            int step = sliceInfo.step;
            int sliceLength = sliceInfo.sliceLength;
            int itemShift = itemShiftProfile.profile(inliningTarget, (byte) self.getItemSizeShift());
            if (sliceLength > 0) {
                if (simpleStepProfile.profile(inliningTarget, step == 1)) {
                    deleteSliceNode.execute(inliningTarget, self, start, sliceLength);
                } else {
                    if (step < 0) {
                        start += 1 + step * (sliceLength - 1) - 1;
                        step = -step;
                    }
                    int cur, offset;
                    for (cur = start, offset = 0; offset < sliceLength - 1; cur += step, offset++) {
                        bufferLib.readIntoBuffer(self.getBuffer(), (cur + 1) << itemShift, self.getBuffer(), (cur - offset) << itemShift, (step - 1) << itemShift, bufferLib);
                    }
                    bufferLib.readIntoBuffer(self.getBuffer(), (cur + 1) << itemShift, self.getBuffer(), (cur - offset) << itemShift, (length - cur - 1) << itemShift, bufferLib);
                    setLengthNode.execute(inliningTarget, self, length - sliceLength);
                }
            }
            return PNone.NONE;
        }
    }

    @Builtin(name = J___ITER__, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class IterNode extends PythonUnaryBuiltinNode {

        @Specialization
        static Object getitem(PArray self,
                        @Cached PythonObjectFactory factory) {
            return factory.createArrayIterator(self);
        }
    }

    @Slot(SlotKind.sq_length)
    @Slot(SlotKind.mp_length)
    @GenerateUncached
    @GenerateNodeFactory
    abstract static class LenNode extends LenBuiltinNode {

        @Specialization
        static int len(PArray self) {
            return self.getLength();
        }
    }

    @Builtin(name = J___REDUCE_EX__, minNumOfPositionalArgs = 2, numOfPositionalOnlyArgs = 2, parameterNames = {"$self", "protocol"})
    @ArgumentClinic(name = "protocol", conversion = ArgumentClinic.ClinicConversion.Int)
    @GenerateNodeFactory
    abstract static class ReduceExNode extends PythonBinaryClinicBuiltinNode {
        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return ReduceExNodeClinicProviderGen.INSTANCE;
        }

        @Specialization(guards = "protocol < 3")
        static Object reduceLegacy(VirtualFrame frame, PArray self, @SuppressWarnings("unused") int protocol,
                        @Bind("this") Node inliningTarget,
                        @Cached @Exclusive GetClassNode getClassNode,
                        @Cached @Exclusive PyObjectLookupAttr lookupDict,
                        @Cached ToListNode toListNode,
                        @Shared @Cached PythonObjectFactory factory) {
            Object cls = getClassNode.execute(inliningTarget, self);
            Object dict = lookupDict.execute(frame, inliningTarget, self, T___DICT__);
            if (dict == PNone.NO_VALUE) {
                dict = PNone.NONE;
            }
            PTuple args = factory.createTuple(new Object[]{self.getFormatString(), toListNode.execute(frame, self)});
            return factory.createTuple(new Object[]{cls, args, dict});
        }

        @Specialization(guards = "protocol >= 3")
        static Object reduce(VirtualFrame frame, PArray self, @SuppressWarnings("unused") int protocol,
                        @Bind("this") Node inliningTarget,
                        @Cached @Exclusive GetClassNode getClassNode,
                        @Cached @Exclusive PyObjectLookupAttr lookupDict,
                        @Cached PyObjectGetAttr getReconstructor,
                        @Cached ToBytesNode toBytesNode,
                        @Shared @Cached PythonObjectFactory factory) {
            PythonModule arrayModule = PythonContext.get(inliningTarget).lookupBuiltinModule(T_ARRAY);
            PArray.MachineFormat mformat = PArray.MachineFormat.forFormat(self.getFormat());
            assert mformat != null;
            Object cls = getClassNode.execute(inliningTarget, self);
            Object dict = lookupDict.execute(frame, inliningTarget, self, T___DICT__);
            if (dict == PNone.NO_VALUE) {
                dict = PNone.NONE;
            }
            Object reconstructor = getReconstructor.execute(frame, inliningTarget, arrayModule, T_ARRAY_RECONSTRUCTOR);
            PTuple args = factory.createTuple(new Object[]{cls, self.getFormatString(), mformat.code, toBytesNode.execute(frame, self)});
            return factory.createTuple(new Object[]{reconstructor, args, dict});
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
        static TruffleString getTypeCode(PArray self) {
            return self.getFormatString();
        }
    }

    @Builtin(name = "buffer_info", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class BufferInfoNode extends PythonUnaryBuiltinNode {

        @Specialization
        static Object bufferinfo(PArray self,
                        @Bind("this") Node inliningTarget,
                        @Cached ArrayNodes.EnsureNativeStorageNode ensureNativeStorageNode,
                        @Cached PythonObjectFactory factory,
                        @CachedLibrary(limit = "1") InteropLibrary lib) {
            Object nativePointer = ensureNativeStorageNode.execute(inliningTarget, self).getPtr();
            if (!(nativePointer instanceof Long)) {
                try {
                    nativePointer = lib.asPointer(nativePointer);
                } catch (UnsupportedMessageException e) {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    throw PRaiseNode.raiseUncached(inliningTarget, NotImplementedError);
                }
            }
            return factory.createTuple(new Object[]{nativePointer, self.getLength()});
        }
    }

    @Builtin(name = J_APPEND, minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    abstract static class AppendNode extends PythonBinaryBuiltinNode {
        @Specialization
        static Object append(VirtualFrame frame, PArray self, Object value,
                        @Bind("this") Node inliningTarget,
                        @Cached ArrayNodes.EnsureCapacityNode ensureCapacityNode,
                        @Cached ArrayNodes.SetLengthNode setLengthNode,
                        @Cached ArrayNodes.PutValueNode putValueNode,
                        @Cached PRaiseNode.Lazy raiseNode) {
            try {
                int index = self.getLength();
                int newLength = PythonUtils.addExact(index, 1);
                self.checkCanResize(inliningTarget, raiseNode);
                ensureCapacityNode.execute(inliningTarget, self, newLength);
                setLengthNode.execute(inliningTarget, self, newLength);
                putValueNode.execute(frame, inliningTarget, self, index, value);
                return PNone.NONE;
            } catch (OverflowException e) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                throw PRaiseNode.raiseUncached(inliningTarget, MemoryError);
            }
        }
    }

    @Builtin(name = J_EXTEND, minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    abstract static class ExtendNode extends PythonBinaryBuiltinNode {
        @Specialization(guards = "self.getFormat() == value.getFormat()")
        static Object extend(PArray self, PArray value,
                        @Bind("this") Node inliningTarget,
                        @CachedLibrary(limit = "2") PythonBufferAccessLibrary bufferLib,
                        @Exclusive @Cached ArrayNodes.EnsureCapacityNode ensureCapacityNode,
                        @Exclusive @Cached ArrayNodes.SetLengthNode setLengthNode,
                        @Exclusive @Cached PRaiseNode.Lazy raiseNode) {
            try {
                int newLength = PythonUtils.addExact(self.getLength(), value.getLength());
                if (newLength != self.getLength()) {
                    self.checkCanResize(inliningTarget, raiseNode);
                }
                int itemShift = self.getItemSizeShift();
                ensureCapacityNode.execute(inliningTarget, self, newLength);
                bufferLib.readIntoBuffer(value.getBuffer(), 0, self.getBuffer(), self.getLength() << itemShift, value.getLength() << itemShift, bufferLib);
                setLengthNode.execute(inliningTarget, self, newLength);
                return PNone.NONE;
            } catch (OverflowException e) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                throw PRaiseNode.raiseUncached(inliningTarget, MemoryError);
            }
        }

        @Specialization
        static Object extend(VirtualFrame frame, PArray self, PSequence value,
                        @Bind("this") Node inliningTarget,
                        @Exclusive @Cached ArrayNodes.PutValueNode putValueNode,
                        @Cached SequenceNodes.GetSequenceStorageNode getSequenceStorageNode,
                        @Cached SequenceStorageNodes.GetItemScalarNode getItemNode,
                        @Exclusive @Cached ArrayNodes.EnsureCapacityNode ensureCapacityNode,
                        @Exclusive @Cached ArrayNodes.SetLengthNode setLengthNode,
                        @Exclusive @Cached PRaiseNode.Lazy raiseNode) {
            SequenceStorage storage = getSequenceStorageNode.execute(inliningTarget, value);
            int storageLength = storage.length();
            try {
                int newLength = PythonUtils.addExact(self.getLength(), storageLength);
                if (newLength != self.getLength()) {
                    self.checkCanResize(inliningTarget, raiseNode);
                    ensureCapacityNode.execute(inliningTarget, self, newLength);
                }
            } catch (OverflowException e) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                throw PRaiseNode.raiseUncached(inliningTarget, MemoryError);
            }
            int length = self.getLength();
            for (int i = 0; i < storageLength; i++) {
                // The whole extend is not atomic, just individual inserts are. That's the same as
                // in CPython
                putValueNode.execute(frame, inliningTarget, self, length, getItemNode.execute(inliningTarget, storage, i));
                setLengthNode.execute(inliningTarget, self, ++length);
            }

            return PNone.NONE;
        }

        @Specialization(guards = "!isArray(value)")
        static Object extend(VirtualFrame frame, PArray self, Object value,
                        @Bind("this") Node inliningTarget,
                        @Cached PyObjectGetIter getIter,
                        @Exclusive @Cached ArrayNodes.PutValueNode putValueNode,
                        @Cached GetNextNode nextNode,
                        @Cached IsBuiltinObjectProfile errorProfile,
                        @Exclusive @Cached ArrayNodes.EnsureCapacityNode ensureCapacityNode,
                        @Exclusive @Cached ArrayNodes.SetLengthNode setLengthNode,
                        @Exclusive @Cached PRaiseNode.Lazy raiseNode) {
            Object iter = getIter.execute(frame, inliningTarget, value);
            int length = self.getLength();
            while (true) {
                Object nextValue;
                try {
                    nextValue = nextNode.execute(frame, iter);
                } catch (PException e) {
                    e.expectStopIteration(inliningTarget, errorProfile);
                    break;
                }
                // The whole extend is not atomic, just individual inserts are. That's the same as
                // in CPython
                try {
                    length = PythonUtils.addExact(length, 1);
                    self.checkCanResize(inliningTarget, raiseNode);
                    ensureCapacityNode.execute(inliningTarget, self, length);
                } catch (OverflowException e) {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    throw PRaiseNode.raiseUncached(inliningTarget, MemoryError);
                }
                putValueNode.execute(frame, inliningTarget, self, length - 1, nextValue);
                setLengthNode.execute(inliningTarget, self, length);
            }

            return PNone.NONE;
        }

        @Specialization(guards = "self.getFormat() != value.getFormat()")
        @SuppressWarnings("unused")
        static Object error(PArray self, PArray value,
                        @Cached PRaiseNode raiseNode) {
            // CPython allows extending an array with an arbitrary iterable. Except a differently
            // formatted array. Weird
            throw raiseNode.raise(TypeError, ErrorMessages.CAN_ONLY_EXTEND_WITH_ARRAY_OF_SAME_KIND);
        }
    }

    @Builtin(name = "insert", minNumOfPositionalArgs = 3, numOfPositionalOnlyArgs = 3, parameterNames = {"$self", "index", "value"})
    @ArgumentClinic(name = "index", conversion = ArgumentClinic.ClinicConversion.Index)
    @GenerateNodeFactory
    abstract static class InsertNode extends PythonTernaryClinicBuiltinNode {
        @Specialization
        static Object insert(VirtualFrame frame, PArray self, int inputIndex, Object value,
                        @Bind("this") Node inliningTarget,
                        @Cached("create(false)") NormalizeIndexNode normalizeIndexNode,
                        @Cached ArrayNodes.CheckValueNode checkValueNode,
                        @Cached ArrayNodes.PutValueNode putValueNode,
                        @Cached ArrayNodes.ShiftNode shiftNode,
                        @Cached PRaiseNode.Lazy raiseNode) {
            int index = normalizeIndexNode.execute(inputIndex, self.getLength());
            if (index > self.getLength()) {
                index = self.getLength();
            } else if (index < 0) {
                index = 0;
            }
            // Need to check the validity of the value before moving the memory around to ensure the
            // operation can fail atomically
            checkValueNode.execute(frame, inliningTarget, self, value);
            self.checkCanResize(inliningTarget, raiseNode);
            shiftNode.execute(inliningTarget, self, index, 1);
            putValueNode.execute(frame, inliningTarget, self, index, value);
            return PNone.NONE;
        }

        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return ArrayBuiltinsClinicProviders.InsertNodeClinicProviderGen.INSTANCE;
        }
    }

    @Builtin(name = "remove", minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    abstract static class RemoveNode extends PythonBinaryBuiltinNode {
        @Specialization
        static Object remove(VirtualFrame frame, PArray self, Object value,
                        @Bind("this") Node inliningTarget,
                        @Cached PyObjectRichCompareBool.EqNode eqNode,
                        @Cached ArrayNodes.GetValueNode getValueNode,
                        @Cached DeleteArraySliceNode deleteSliceNode,
                        @Cached PRaiseNode.Lazy raiseNode) {
            for (int i = 0; i < self.getLength(); i++) {
                Object item = getValueNode.execute(inliningTarget, self, i);
                if (eqNode.compare(frame, inliningTarget, item, value)) {
                    self.checkCanResize(inliningTarget, raiseNode);
                    deleteSliceNode.execute(inliningTarget, self, i, 1);
                    return PNone.NONE;
                }
            }
            throw raiseNode.get(inliningTarget).raise(ValueError, ErrorMessages.ARRAY_REMOVE_X_NOT_IN_ARRAY);
        }
    }

    @Builtin(name = "pop", minNumOfPositionalArgs = 1, numOfPositionalOnlyArgs = 2, parameterNames = {"$self", "index"})
    @ArgumentClinic(name = "index", conversion = ArgumentClinic.ClinicConversion.Index, defaultValue = "-1")
    @GenerateNodeFactory
    abstract static class PopNode extends PythonBinaryClinicBuiltinNode {
        @Specialization
        static Object pop(PArray self, int inputIndex,
                        @Bind("this") Node inliningTarget,
                        @Cached("forPop()") NormalizeIndexNode normalizeIndexNode,
                        @Cached ArrayNodes.GetValueNode getValueNode,
                        @Cached DeleteArraySliceNode deleteSliceNode,
                        @Cached PRaiseNode.Lazy raiseNode) {
            if (self.getLength() == 0) {
                throw raiseNode.get(inliningTarget).raise(IndexError, ErrorMessages.POP_FROM_EMPTY_ARRAY);
            }
            int index = normalizeIndexNode.execute(inputIndex, self.getLength());
            Object value = getValueNode.execute(inliningTarget, self, index);
            self.checkCanResize(inliningTarget, raiseNode);
            deleteSliceNode.execute(inliningTarget, self, index, 1);
            return value;
        }

        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return ArrayBuiltinsClinicProviders.PopNodeClinicProviderGen.INSTANCE;
        }
    }

    @Builtin(name = "frombytes", minNumOfPositionalArgs = 2, numOfPositionalOnlyArgs = 2, parameterNames = {"$self", "buffer"})
    @ArgumentClinic(name = "buffer", conversion = ArgumentClinic.ClinicConversion.ReadableBuffer)
    @GenerateNodeFactory
    public abstract static class FromBytesNode extends PythonBinaryClinicBuiltinNode {

        // make this method accessible
        @Override
        public abstract Object executeWithoutClinic(VirtualFrame frame, Object arg, Object arg2);

        @Specialization
        static Object frombytes(VirtualFrame frame, PArray self, Object buffer,
                        @Bind("this") Node inliningTarget,
                        @Cached("createFor(this)") IndirectCallData indirectCallData,
                        @CachedLibrary(limit = "3") PythonBufferAccessLibrary bufferLib,
                        @Cached ArrayNodes.EnsureCapacityNode ensureCapacityNode,
                        @Cached ArrayNodes.SetLengthNode setLengthNode,
                        @Cached PRaiseNode.Lazy raiseNode) {
            try {
                int itemShift = self.getItemSizeShift();
                int oldSize = self.getLength();
                try {
                    int bufferLength = bufferLib.getBufferLength(buffer);
                    if (!PythonUtils.isDivisible(bufferLength, itemShift)) {
                        throw raiseNode.get(inliningTarget).raise(ValueError, ErrorMessages.BYTES_ARRAY_NOT_MULTIPLE_OF_ARRAY_SIZE);
                    }
                    int newLength = PythonUtils.addExact(oldSize, bufferLength >> itemShift);
                    self.checkCanResize(inliningTarget, raiseNode);
                    ensureCapacityNode.execute(inliningTarget, self, newLength);
                    setLengthNode.execute(inliningTarget, self, newLength);
                    bufferLib.readIntoBuffer(buffer, 0, self.getBuffer(), oldSize << itemShift, bufferLength, bufferLib);
                } catch (OverflowException e) {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    throw PRaiseNode.raiseUncached(inliningTarget, MemoryError);
                }
                return PNone.NONE;
            } finally {
                bufferLib.release(buffer, frame, indirectCallData);
            }
        }

        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return ArrayBuiltinsClinicProviders.FromBytesNodeClinicProviderGen.INSTANCE;
        }
    }

    @Builtin(name = "fromfile", minNumOfPositionalArgs = 3, numOfPositionalOnlyArgs = 3, parameterNames = {"$self", "file", "n"})
    @ArgumentClinic(name = "n", conversion = ArgumentClinic.ClinicConversion.Index)
    @GenerateNodeFactory
    public abstract static class FromFileNode extends PythonTernaryClinicBuiltinNode {
        @Specialization
        static Object fromfile(VirtualFrame frame, PArray self, Object file, int n,
                        @Bind("this") Node inliningTarget,
                        @Cached PyObjectCallMethodObjArgs callMethod,
                        @Cached PyObjectSizeNode sizeNode,
                        @Cached InlinedConditionProfile nNegativeProfile,
                        @Cached FromBytesNode fromBytesNode,
                        @Cached PRaiseNode.Lazy raiseNode) {
            if (nNegativeProfile.profile(inliningTarget, n < 0)) {
                throw raiseNode.get(inliningTarget).raise(ValueError, ErrorMessages.NEGATIVE_COUNT);
            }
            int nbytes = n << self.getItemSizeShift();
            Object readResult = callMethod.execute(frame, inliningTarget, file, T_READ, nbytes);
            if (readResult instanceof PBytes) {
                int readLength = sizeNode.execute(frame, inliningTarget, readResult);
                fromBytesNode.executeWithoutClinic(frame, self, readResult);
                // It would make more sense to check this before the frombytes call, but CPython
                // does it this way
                if (readLength != nbytes) {
                    throw raiseNode.get(inliningTarget).raise(EOFError, ErrorMessages.READ_DIDNT_RETURN_ENOUGH_BYTES);
                }
            } else {
                throw raiseNode.get(inliningTarget).raise(TypeError, ErrorMessages.READ_DIDNT_RETURN_BYTES);
            }
            return PNone.NONE;
        }

        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return ArrayBuiltinsClinicProviders.FromFileNodeClinicProviderGen.INSTANCE;
        }
    }

    @Builtin(name = "fromlist", minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    abstract static class FromListNode extends PythonBinaryBuiltinNode {
        @Specialization
        static Object fromlist(VirtualFrame frame, PArray self, PList list,
                        @Bind("this") Node inliningTarget,
                        @Cached SequenceNodes.GetSequenceStorageNode getSequenceStorageNode,
                        @Cached SequenceStorageNodes.GetItemScalarNode getItemScalarNode,
                        @Cached ArrayNodes.EnsureCapacityNode ensureCapacityNode,
                        @Cached ArrayNodes.SetLengthNode setLengthNode,
                        @Cached ArrayNodes.PutValueNode putValueNode,
                        @Cached PRaiseNode.Lazy raiseNode) {
            try {
                SequenceStorage storage = getSequenceStorageNode.execute(inliningTarget, list);
                int length = storage.length();
                int newLength = PythonUtils.addExact(self.getLength(), length);
                self.checkCanResize(inliningTarget, raiseNode);
                ensureCapacityNode.execute(inliningTarget, self, newLength);
                for (int i = 0; i < length; i++) {
                    putValueNode.execute(frame, inliningTarget, self, self.getLength() + i, getItemScalarNode.execute(inliningTarget, storage, i));
                }
                setLengthNode.execute(inliningTarget, self, newLength);
                return PNone.NONE;
            } catch (OverflowException e) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                throw PRaiseNode.raiseUncached(inliningTarget, MemoryError);
            }
        }

        @Fallback
        @SuppressWarnings("unused")
        static Object error(Object self, Object arg,
                        @Cached PRaiseNode raiseNode) {
            throw raiseNode.raise(TypeError, ErrorMessages.ARG_MUST_BE_LIST);
        }
    }

    @Builtin(name = "fromunicode", minNumOfPositionalArgs = 2, numOfPositionalOnlyArgs = 2, parameterNames = {"$self", "str"})
    @ArgumentClinic(name = "str", conversion = ArgumentClinic.ClinicConversion.TString)
    @GenerateNodeFactory
    public abstract static class FromUnicodeNode extends PythonBinaryClinicBuiltinNode {
        @Specialization
        static Object fromunicode(VirtualFrame frame, PArray self, TruffleString str,
                        @Bind("this") Node inliningTarget,
                        @Cached ArrayNodes.PutValueNode putValueNode,
                        @Cached ArrayNodes.EnsureCapacityNode ensureCapacityNode,
                        @Cached ArrayNodes.SetLengthNode setLengthNode,
                        @Cached TruffleString.CodePointLengthNode codePointLengthNode,
                        @Cached TruffleString.CreateCodePointIteratorNode createCodePointIteratorNode,
                        @Cached TruffleStringIterator.NextNode nextNode,
                        @Cached TruffleString.FromCodePointNode fromCodePointNode,
                        @Cached PRaiseNode.Lazy raiseNode) {
            try {
                int length = codePointLengthNode.execute(str, TS_ENCODING);
                int newLength = PythonUtils.addExact(self.getLength(), length);
                self.checkCanResize(inliningTarget, raiseNode);
                ensureCapacityNode.execute(inliningTarget, self, newLength);
                TruffleStringIterator it = createCodePointIteratorNode.execute(str, TS_ENCODING);
                int codePointIndex = 0;
                while (it.hasNext()) {
                    TruffleString value = fromCodePointNode.execute(nextNode.execute(it), TS_ENCODING, true);
                    putValueNode.execute(frame, inliningTarget, self, self.getLength() + codePointIndex++, value);
                }
                setLengthNode.execute(inliningTarget, self, newLength);
                return PNone.NONE;
            } catch (OverflowException e) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                throw PRaiseNode.raiseUncached(inliningTarget, MemoryError);
            }
        }

        @Fallback
        @SuppressWarnings("unused")
        static Object error(Object self, Object arg,
                        @Cached PRaiseNode raiseNode) {
            throw raiseNode.raise(TypeError, ErrorMessages.FROMUNICODE_ARG_MUST_BE_STR_NOT_P, arg);
        }

        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return ArrayBuiltinsClinicProviders.FromUnicodeNodeClinicProviderGen.INSTANCE;
        }
    }

    @Builtin(name = "tobytes", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class ToBytesNode extends PythonUnaryBuiltinNode {
        @Specialization
        Object tobytes(PArray self,
                        @CachedLibrary(limit = "2") PythonBufferAccessLibrary bufferLib,
                        @Cached PythonObjectFactory factory) {
            byte[] bytes = new byte[self.getBytesLength()];
            bufferLib.readIntoByteArray(self.getBuffer(), 0, bytes, 0, bytes.length);
            return factory.createBytes(bytes);
        }
    }

    @Builtin(name = "tolist", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class ToListNode extends PythonUnaryBuiltinNode {
        @Specialization
        static Object tolist(VirtualFrame frame, PArray self,
                        @Cached ListNodes.ConstructListNode constructListNode) {
            return constructListNode.execute(frame, self);
        }
    }

    @Builtin(name = "tounicode", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class ToUnicodeNode extends PythonUnaryBuiltinNode {
        @Specialization
        static TruffleString tounicode(PArray self,
                        @Bind("this") Node inliningTarget,
                        @Cached InlinedConditionProfile formatProfile,
                        @Cached ArrayNodes.GetValueNode getValueNode,
                        @Cached TruffleStringBuilder.AppendStringNode appendStringNode,
                        @Cached TruffleStringBuilder.ToStringNode toStringNode,
                        @Cached PRaiseNode.Lazy raiseNode) {
            if (formatProfile.profile(inliningTarget, self.getFormat() != BufferFormat.UNICODE)) {
                throw raiseNode.get(inliningTarget).raise(ValueError, ErrorMessages.MAY_ONLY_BE_CALLED_ON_UNICODE_TYPE_ARRAYS);
            }
            TruffleStringBuilder sb = TruffleStringBuilder.create(TS_ENCODING);
            int length = self.getLength();
            for (int i = 0; i < length; i++) {
                appendStringNode.execute(sb, (TruffleString) getValueNode.execute(inliningTarget, self, i));
            }
            return toStringNode.execute(sb);
        }
    }

    @Builtin(name = "tofile", minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    abstract static class ToFileNode extends PythonBinaryBuiltinNode {
        @Specialization
        static Object tofile(VirtualFrame frame, PArray self, Object file,
                        @Bind("this") Node inliningTarget,
                        @CachedLibrary(limit = "2") PythonBufferAccessLibrary bufferLib,
                        @Cached PyObjectCallMethodObjArgs callMethod,
                        @Cached PythonObjectFactory factory) {
            if (self.getLength() > 0) {
                int remaining = self.getBytesLength();
                int blocksize = 64 * 1024;
                int nblocks = (remaining + blocksize - 1) / blocksize;
                byte[] buffer = null;
                for (int i = 0; i < nblocks; i++) {
                    if (remaining < blocksize) {
                        buffer = new byte[remaining];
                    } else if (buffer == null) {
                        buffer = new byte[blocksize];
                    }
                    bufferLib.readIntoByteArray(self.getBuffer(), i * blocksize, buffer, 0, buffer.length);
                    callMethod.execute(frame, inliningTarget, file, T_WRITE, factory.createBytes(buffer));
                    remaining -= blocksize;
                }
            }
            return PNone.NONE;
        }
    }

    @Builtin(name = "byteswap", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class ByteSwapNode extends PythonUnaryBuiltinNode {

        @Specialization(guards = "self.getFormat().bytesize == 1")
        static Object byteswap1(@SuppressWarnings("unused") PArray self) {
            return PNone.NONE;
        }

        @Specialization(guards = "self.getFormat().bytesize == 2")
        static Object byteswap2(PArray self,
                        @Shared @CachedLibrary(limit = "3") PythonBufferAccessLibrary bufferLib) {
            doByteSwapExploded(self, 2, self.getBuffer(), bufferLib);
            return PNone.NONE;
        }

        @Specialization(guards = "self.getFormat().bytesize == 4")
        static Object byteswap4(PArray self,
                        @Shared @CachedLibrary(limit = "3") PythonBufferAccessLibrary bufferLib) {
            doByteSwapExploded(self, 4, self.getBuffer(), bufferLib);
            return PNone.NONE;
        }

        @Specialization(guards = "self.getFormat().bytesize == 8")
        static Object byteswap8(PArray self,
                        @Shared @CachedLibrary(limit = "3") PythonBufferAccessLibrary bufferLib) {
            doByteSwapExploded(self, 8, self.getBuffer(), bufferLib);
            return PNone.NONE;
        }

        private static void doByteSwapExploded(PArray self, int itemsize, Object buffer, PythonBufferAccessLibrary bufferLib) {
            for (int i = 0; i < self.getBytesLength(); i += itemsize) {
                doByteSwapExplodedInnerLoop(buffer, itemsize, i, bufferLib);
            }
        }

        @ExplodeLoop
        private static void doByteSwapExplodedInnerLoop(Object buffer, int itemsize, int i, PythonBufferAccessLibrary bufferLib) {
            for (int j = 0; j < itemsize / 2; j++) {
                byte b = bufferLib.readByte(buffer, i + j);
                bufferLib.writeByte(buffer, i + j, bufferLib.readByte(buffer, i + itemsize - j - 1));
                bufferLib.writeByte(buffer, i + itemsize - j - 1, b);
            }
        }
    }

    @Builtin(name = "index", minNumOfPositionalArgs = 2, parameterNames = {"$self", "sub", "start", "end"})
    @ArgumentClinic(name = "start", conversion = ArgumentClinic.ClinicConversion.SliceIndex, defaultValue = "0", useDefaultForNone = true)
    @ArgumentClinic(name = "end", conversion = ArgumentClinic.ClinicConversion.SliceIndex, defaultValue = "Integer.MAX_VALUE", useDefaultForNone = true)
    @GenerateNodeFactory
    abstract static class IndexNode extends PythonQuaternaryClinicBuiltinNode {
        @Specialization
        static int index(VirtualFrame frame, PArray self, Object value, int start, int stop,
                        @Bind("this") Node inliningTarget,
                        @Cached PyObjectRichCompareBool.EqNode eqNode,
                        @Cached ArrayNodes.GetValueNode getValueNode,
                        @Cached PRaiseNode.Lazy raiseNode) {
            int length = self.getLength();
            if (start < 0) {
                start += length;
                if (start < 0) {
                    start = 0;
                }
            }
            if (stop < 0) {
                stop += length;
            }
            for (int i = start; i < stop && i < length; i++) {
                if (eqNode.compare(frame, inliningTarget, getValueNode.execute(inliningTarget, self, i), value)) {
                    return i;
                }
            }
            throw raiseNode.get(inliningTarget).raise(ValueError, ErrorMessages.ARRAY_INDEX_X_NOT_IN_ARRAY);
        }

        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return ArrayBuiltinsClinicProviders.IndexNodeClinicProviderGen.INSTANCE;
        }
    }

    @Builtin(name = "count", minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    abstract static class CountNode extends PythonBinaryBuiltinNode {
        @Specialization
        static int count(VirtualFrame frame, PArray self, Object value,
                        @Bind("this") Node inliningTarget,
                        @Cached PyObjectRichCompareBool.EqNode eqNode,
                        @Cached ArrayNodes.GetValueNode getValueNode) {
            int count = 0;
            for (int i = 0; i < self.getLength(); i++) {
                if (eqNode.compare(frame, inliningTarget, getValueNode.execute(inliningTarget, self, i), value)) {
                    count++;
                }
            }
            return count;
        }
    }

    @Builtin(name = "reverse", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class ReverseNode extends PythonUnaryBuiltinNode {

        @Specialization
        static Object reverse(PArray self,
                        @CachedLibrary(limit = "2") PythonBufferAccessLibrary bufferLib) {
            int itemsize = self.getFormat().bytesize;
            int itemShift = self.getItemSizeShift();
            byte[] tmp = new byte[itemsize];
            int length = self.getLength();
            for (int i = 0; i < length / 2; i++) {
                bufferLib.readIntoByteArray(self.getBuffer(), i << itemShift, tmp, 0, itemsize);
                bufferLib.readIntoBuffer(self.getBuffer(), (length - i - 1) << itemShift, self.getBuffer(), i << itemShift, itemsize, bufferLib);
                bufferLib.writeFromByteArray(self.getBuffer(), (length - i - 1) << itemShift, tmp, 0, itemsize);
            }
            return PNone.NONE;
        }
    }
}
