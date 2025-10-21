/*
 * Copyright (c) 2017, 2025, Oracle and/or its affiliates.
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
import static com.oracle.graal.python.nodes.ErrorMessages.S_TAKES_AT_LEAST_D_ARGUMENTS_D_GIVEN;
import static com.oracle.graal.python.nodes.ErrorMessages.S_TAKES_AT_MOST_D_ARGUMENTS_D_GIVEN;
import static com.oracle.graal.python.nodes.ErrorMessages.S_TAKES_NO_KEYWORD_ARGS;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.T___DICT__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___REDUCE_EX__;
import static com.oracle.graal.python.nodes.StringLiterals.T_COMMA_SPACE;
import static com.oracle.graal.python.nodes.StringLiterals.T_LBRACKET;
import static com.oracle.graal.python.nodes.StringLiterals.T_LPAREN;
import static com.oracle.graal.python.nodes.StringLiterals.T_RBRACKET;
import static com.oracle.graal.python.nodes.StringLiterals.T_RPAREN;
import static com.oracle.graal.python.nodes.StringLiterals.T_SINGLE_QUOTE;
import static com.oracle.graal.python.util.PythonUtils.TS_ENCODING;
import static com.oracle.graal.python.util.PythonUtils.tsLiteral;

import java.util.List;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.annotations.ArgumentClinic;
import com.oracle.graal.python.annotations.Builtin;
import com.oracle.graal.python.annotations.Slot;
import com.oracle.graal.python.annotations.Slot.SlotKind;
import com.oracle.graal.python.annotations.Slot.SlotSignature;
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
import com.oracle.graal.python.builtins.objects.bytes.PBytesLike;
import com.oracle.graal.python.builtins.objects.common.IndexNodes.NormalizeIndexWithBoundsCheckNode;
import com.oracle.graal.python.builtins.objects.common.IndexNodes.NormalizeIndexWithoutBoundsCheckNode;
import com.oracle.graal.python.builtins.objects.common.SequenceNodes;
import com.oracle.graal.python.builtins.objects.common.SequenceStorageNodes;
import com.oracle.graal.python.builtins.objects.function.PKeyword;
import com.oracle.graal.python.builtins.objects.list.PList;
import com.oracle.graal.python.builtins.objects.module.PythonModule;
import com.oracle.graal.python.builtins.objects.range.PIntRange;
import com.oracle.graal.python.builtins.objects.slice.PSlice;
import com.oracle.graal.python.builtins.objects.slice.SliceNodes;
import com.oracle.graal.python.builtins.objects.str.StringNodes.CastToTruffleStringChecked1Node;
import com.oracle.graal.python.builtins.objects.tuple.PTuple;
import com.oracle.graal.python.builtins.objects.type.TpSlots;
import com.oracle.graal.python.builtins.objects.type.TypeNodes;
import com.oracle.graal.python.builtins.objects.type.slots.TpSlotBinaryFunc.MpSubscriptBuiltinNode;
import com.oracle.graal.python.builtins.objects.type.slots.TpSlotBinaryFunc.SqConcatBuiltinNode;
import com.oracle.graal.python.builtins.objects.type.slots.TpSlotLen.LenBuiltinNode;
import com.oracle.graal.python.builtins.objects.type.slots.TpSlotMpAssSubscript.MpAssSubscriptBuiltinNode;
import com.oracle.graal.python.builtins.objects.type.slots.TpSlotRichCompare;
import com.oracle.graal.python.builtins.objects.type.slots.TpSlotSizeArgFun.SqItemBuiltinNode;
import com.oracle.graal.python.builtins.objects.type.slots.TpSlotSizeArgFun.SqRepeatBuiltinNode;
import com.oracle.graal.python.builtins.objects.type.slots.TpSlotSqAssItem.SqAssItemBuiltinNode;
import com.oracle.graal.python.builtins.objects.type.slots.TpSlotSqContains.SqContainsBuiltinNode;
import com.oracle.graal.python.lib.IteratorExhausted;
import com.oracle.graal.python.lib.PyIndexCheckNode;
import com.oracle.graal.python.lib.PyIterNextNode;
import com.oracle.graal.python.lib.PyNumberAsSizeNode;
import com.oracle.graal.python.lib.PyNumberIndexNode;
import com.oracle.graal.python.lib.PyObjectCallMethodObjArgs;
import com.oracle.graal.python.lib.PyObjectGetAttr;
import com.oracle.graal.python.lib.PyObjectGetIter;
import com.oracle.graal.python.lib.PyObjectLookupAttr;
import com.oracle.graal.python.lib.PyObjectReprAsTruffleStringNode;
import com.oracle.graal.python.lib.PyObjectRichCompare;
import com.oracle.graal.python.lib.PyObjectRichCompareBool;
import com.oracle.graal.python.lib.PyObjectSizeNode;
import com.oracle.graal.python.lib.RichCmpOp;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PGuards;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.builtins.ListNodes;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.builtins.PythonBinaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonBinaryClinicBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonQuaternaryClinicBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonTernaryClinicBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonUnaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonVarargsBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.clinic.ArgumentClinicProvider;
import com.oracle.graal.python.nodes.object.BuiltinClassProfiles.IsBuiltinClassExactProfile;
import com.oracle.graal.python.nodes.object.GetClassNode;
import com.oracle.graal.python.nodes.util.CastToTruffleStringNode;
import com.oracle.graal.python.runtime.IndirectCallData;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.graal.python.runtime.exception.PythonErrorType;
import com.oracle.graal.python.runtime.object.PFactory;
import com.oracle.graal.python.runtime.sequence.PSequence;
import com.oracle.graal.python.runtime.sequence.storage.ByteSequenceStorage;
import com.oracle.graal.python.runtime.sequence.storage.SequenceStorage;
import com.oracle.graal.python.util.BufferFormat;
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
import com.oracle.truffle.api.profiles.InlinedBranchProfile;
import com.oracle.truffle.api.profiles.InlinedByteValueProfile;
import com.oracle.truffle.api.profiles.InlinedConditionProfile;
import com.oracle.truffle.api.profiles.InlinedLoopConditionProfile;
import com.oracle.truffle.api.profiles.ValueProfile;
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

    // array.array(typecode[, initializer])
    @Slot(value = SlotKind.tp_new, isComplex = true)
    @SlotSignature(name = J_ARRAY, minNumOfPositionalArgs = 1, takesVarArgs = true, takesVarKeywordArgs = true)
    @GenerateNodeFactory
    abstract static class ArrayNode extends PythonVarargsBuiltinNode {

        @Specialization(guards = "args.length == 1 || args.length == 2")
        static Object array2(VirtualFrame frame, Object cls, Object[] args, PKeyword[] kwargs,
                        @Bind Node inliningTarget,
                        @Cached InlinedConditionProfile hasInitializerProfile,
                        @Cached IsBuiltinClassExactProfile isNotSubtypeProfile,
                        @Cached CastToTruffleStringChecked1Node cast,
                        @Cached ArrayNodeInternal arrayNodeInternal,
                        @Cached PRaiseNode raise) {
            if (isNotSubtypeProfile.profileClass(inliningTarget, cls, PythonBuiltinClassType.PArray)) {
                if (kwargs.length != 0) {
                    throw raise.raise(inliningTarget, TypeError, S_TAKES_NO_KEYWORD_ARGS, "array.array()");
                }
            }
            Object initializer = hasInitializerProfile.profile(inliningTarget, args.length == 2) ? args[1] : PNone.NO_VALUE;
            return arrayNodeInternal.execute(frame, inliningTarget, cls, cast.cast(inliningTarget, args[0], ErrorMessages.ARG_1_MUST_BE_UNICODE_NOT_P, args[0]), initializer);
        }

        @Fallback
        @SuppressWarnings("unused")
        Object error(Object cls, Object[] args, PKeyword[] kwargs) {
            if (args.length < 2) {
                throw PRaiseNode.raiseStatic(this, TypeError, S_TAKES_AT_LEAST_D_ARGUMENTS_D_GIVEN, T_ARRAY, 2, args.length);
            } else {
                throw PRaiseNode.raiseStatic(this, TypeError, S_TAKES_AT_MOST_D_ARGUMENTS_D_GIVEN, T_ARRAY, 3, args.length);
            }
        }

        // multiple non-inlined specializations share nodes
        @SuppressWarnings("truffle-interpreted-performance")
        @ImportStatic(PGuards.class)
        @GenerateInline
        @GenerateCached(false)
        abstract static class ArrayNodeInternal extends Node {

            public abstract PArray execute(VirtualFrame frame, Node inliningTarget, Object cls, TruffleString typeCode, Object initializer);

            @Specialization(guards = "isNoValue(initializer)")
            static PArray array(Node inliningTarget, Object cls, TruffleString typeCode, @SuppressWarnings("unused") PNone initializer,
                            @Shared @Cached GetFormatCheckedNode getFormatCheckedNode,
                            @Shared @Cached TypeNodes.GetInstanceShape getInstanceShape) {
                BufferFormat format = getFormatCheckedNode.execute(inliningTarget, typeCode);
                return PFactory.createArray(cls, getInstanceShape.execute(cls), typeCode, format);
            }

            @Specialization
            @InliningCutoff
            static PArray arrayWithRangeInitializer(Node inliningTarget, Object cls, TruffleString typeCode, PIntRange range,
                            @Shared @Cached GetFormatCheckedNode getFormatCheckedNode,
                            @Shared @Cached TypeNodes.GetInstanceShape getInstanceShape,
                            @Exclusive @Cached ArrayNodes.PutValueNode putValueNode) {
                BufferFormat format = getFormatCheckedNode.execute(inliningTarget, typeCode);
                PArray array;
                try {
                    array = PFactory.createArray(cls, getInstanceShape.execute(cls), typeCode, format, range.getIntLength());
                } catch (OverflowException e) {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    throw PRaiseNode.raiseStatic(inliningTarget, MemoryError);
                }

                int start = range.getIntStart();
                int step = range.getIntStep();
                int len = range.getIntLength();

                for (int index = 0, value = start; index < len; index++, value += step) {
                    putValueNode.execute(null, inliningTarget, array, index, value);
                }

                return array;
            }

            @Specialization
            static PArray arrayWithBytesInitializer(VirtualFrame frame, Node inliningTarget, Object cls, TruffleString typeCode, PBytesLike bytes,
                            @Shared @Cached GetFormatCheckedNode getFormatCheckedNode,
                            @Shared @Cached TypeNodes.GetInstanceShape getInstanceShape,
                            @Cached(inline = false) ArrayBuiltins.FromBytesNode fromBytesNode) {
                BufferFormat format = getFormatCheckedNode.execute(inliningTarget, typeCode);
                PArray array = PFactory.createArray(cls, getInstanceShape.execute(cls), typeCode, format);
                fromBytesNode.executeWithoutClinic(frame, array, bytes);
                return array;
            }

            @Specialization(guards = "isString(initializer)")
            @InliningCutoff
            static PArray arrayWithStringInitializer(VirtualFrame frame, Node inliningTarget, Object cls, TruffleString typeCode, Object initializer,
                            @Shared @Cached GetFormatCheckedNode getFormatCheckedNode,
                            @Shared @Cached TypeNodes.GetInstanceShape getInstanceShape,
                            @Cached(inline = false) ArrayBuiltins.FromUnicodeNode fromUnicodeNode,
                            @Cached PRaiseNode raise) {
                BufferFormat format = getFormatCheckedNode.execute(inliningTarget, typeCode);
                if (format != BufferFormat.UNICODE) {
                    throw raise.raise(inliningTarget, TypeError, ErrorMessages.CANNOT_USE_STR_TO_INITIALIZE_ARRAY, typeCode);
                }
                PArray array = PFactory.createArray(cls, getInstanceShape.execute(cls), typeCode, format);
                fromUnicodeNode.execute(frame, array, initializer);
                return array;
            }

            @Specialization
            @InliningCutoff
            static PArray arrayArrayInitializer(VirtualFrame frame, Node inliningTarget, Object cls, TruffleString typeCode, PArray initializer,
                            @Shared @Cached GetFormatCheckedNode getFormatCheckedNode,
                            @Shared @Cached TypeNodes.GetInstanceShape getInstanceShape,
                            @Exclusive @Cached ArrayNodes.PutValueNode putValueNode,
                            @Cached ArrayNodes.GetValueNode getValueNode) {
                BufferFormat format = getFormatCheckedNode.execute(inliningTarget, typeCode);
                try {
                    int length = initializer.getLength();
                    PArray array = PFactory.createArray(cls, getInstanceShape.execute(cls), typeCode, format, length);
                    for (int i = 0; i < length; i++) {
                        putValueNode.execute(frame, inliningTarget, array, i, getValueNode.execute(inliningTarget, initializer, i));
                    }
                    return array;
                } catch (OverflowException e) {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    throw PRaiseNode.raiseStatic(inliningTarget, MemoryError);
                }
            }

            @Specialization(guards = "!isBytes(initializer)")
            @InliningCutoff
            static PArray arraySequenceInitializer(VirtualFrame frame, Node inliningTarget, Object cls, TruffleString typeCode, PSequence initializer,
                            @Shared @Cached GetFormatCheckedNode getFormatCheckedNode,
                            @Shared @Cached TypeNodes.GetInstanceShape getInstanceShape,
                            @Exclusive @Cached ArrayNodes.PutValueNode putValueNode,
                            @Cached SequenceNodes.GetSequenceStorageNode getSequenceStorageNode,
                            @Cached SequenceStorageNodes.GetItemScalarNode getItemNode) {
                BufferFormat format = getFormatCheckedNode.execute(inliningTarget, typeCode);
                SequenceStorage storage = getSequenceStorageNode.execute(inliningTarget, initializer);
                int length = storage.length();
                try {
                    PArray array = PFactory.createArray(cls, getInstanceShape.execute(cls), typeCode, format, length);
                    for (int i = 0; i < length; i++) {
                        putValueNode.execute(frame, inliningTarget, array, i, getItemNode.execute(inliningTarget, storage, i));
                    }
                    return array;
                } catch (OverflowException e) {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    throw PRaiseNode.raiseStatic(inliningTarget, MemoryError);
                }
            }

            @Specialization(guards = {"!isBytes(initializer)", "!isString(initializer)", "!isPSequence(initializer)"})
            @InliningCutoff
            static PArray arrayIteratorInitializer(VirtualFrame frame, Node inliningTarget, Object cls, TruffleString typeCode, Object initializer,
                            @Cached PyObjectGetIter getIter,
                            @Shared @Cached GetFormatCheckedNode getFormatCheckedNode,
                            @Shared @Cached TypeNodes.GetInstanceShape getInstanceShape,
                            @Exclusive @Cached ArrayNodes.PutValueNode putValueNode,
                            @Cached PyIterNextNode nextNode,
                            @Cached ArrayNodes.SetLengthNode setLengthNode,
                            @Cached ArrayNodes.EnsureCapacityNode ensureCapacityNode) {
                Object iter = getIter.execute(frame, inliningTarget, initializer);

                BufferFormat format = getFormatCheckedNode.execute(inliningTarget, typeCode);
                PArray array = PFactory.createArray(cls, getInstanceShape.execute(cls), typeCode, format);

                int length = 0;
                while (true) {
                    try {
                        Object nextValue = nextNode.execute(frame, inliningTarget, iter);
                        try {
                            length = PythonUtils.addExact(length, 1);
                            ensureCapacityNode.execute(inliningTarget, array, length);
                        } catch (OverflowException e) {
                            CompilerDirectives.transferToInterpreterAndInvalidate();
                            throw PRaiseNode.raiseStatic(inliningTarget, MemoryError);
                        }
                        putValueNode.execute(frame, inliningTarget, array, length - 1, nextValue);
                    } catch (IteratorExhausted e) {
                        break;
                    }
                }

                setLengthNode.execute(inliningTarget, array, length);
                return array;
            }

            @GenerateInline
            @GenerateCached(false)
            abstract static class GetFormatCheckedNode extends Node {
                abstract BufferFormat execute(Node inliningTarget, TruffleString typeCode);

                @Specialization
                static BufferFormat get(Node inliningTarget, TruffleString typeCode,
                                @Cached TruffleString.CodePointLengthNode lengthNode,
                                @Cached TruffleString.CodePointAtIndexNode atIndexNode,
                                @Cached PRaiseNode raise,
                                @Cached(value = "createIdentityProfile()", inline = false) ValueProfile valueProfile) {
                    if (lengthNode.execute(typeCode, TS_ENCODING) != 1) {
                        throw raise.raise(inliningTarget, TypeError, ErrorMessages.ARRAY_ARG_1_MUST_BE_UNICODE);
                    }
                    BufferFormat format = BufferFormat.forArray(typeCode, lengthNode, atIndexNode);
                    if (format == null) {
                        throw raise.raise(inliningTarget, ValueError, ErrorMessages.BAD_TYPECODE);
                    }
                    return valueProfile.profile(format);
                }
            }
        }
    }

    @Slot(value = SlotKind.sq_concat, isComplex = true)
    @GenerateNodeFactory
    abstract static class ConcatNode extends SqConcatBuiltinNode {
        @Specialization(guards = "left.getFormat() == right.getFormat()")
        Object concat(PArray left, PArray right,
                        @CachedLibrary(limit = "2") PythonBufferAccessLibrary bufferLib,
                        @Bind PythonLanguage language) {
            try {
                int newLength = PythonUtils.addExact(left.getLength(), right.getLength());
                int itemShift = left.getItemSizeShift();
                PArray newArray = PFactory.createArray(language, left.getFormatString(), left.getFormat(), newLength);
                bufferLib.readIntoBuffer(left.getBuffer(), 0, newArray.getBuffer(), 0, left.getLength() << itemShift, bufferLib);
                bufferLib.readIntoBuffer(right.getBuffer(), 0, newArray.getBuffer(), left.getLength() << itemShift, right.getLength() << itemShift, bufferLib);
                return newArray;
            } catch (OverflowException e) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                throw PRaiseNode.raiseStatic(this, MemoryError);
            }
        }

        @Specialization(guards = "left.getFormat() != right.getFormat()")
        @SuppressWarnings("unused")
        static Object error(PArray left, PArray right,
                        @Bind Node inliningTarget) {
            throw PRaiseNode.raiseStatic(inliningTarget, TypeError, BAD_ARG_TYPE_FOR_BUILTIN_OP);
        }

        @Fallback
        static Object error(@SuppressWarnings("unused") Object left, Object right,
                        @Bind Node inliningTarget) {
            throw PRaiseNode.raiseStatic(inliningTarget, TypeError, ErrorMessages.CAN_ONLY_APPEND_ARRAY_TO_ARRAY, right);
        }
    }

    @Slot(value = SlotKind.sq_inplace_concat, isComplex = true)
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
                        @Bind Node inliningTarget) {
            throw PRaiseNode.raiseStatic(inliningTarget, TypeError, ErrorMessages.CAN_ONLY_EXTEND_ARRAY_WITH_ARRAY, right);
        }
    }

    @Slot(value = SlotKind.sq_repeat, isComplex = true)
    @GenerateNodeFactory
    abstract static class MulNode extends SqRepeatBuiltinNode {
        @Specialization(guards = "self.getLength() > 0")
        static PArray concat(PArray self, int valueIn,
                        @Bind Node inliningTarget,
                        @CachedLibrary(limit = "2") PythonBufferAccessLibrary bufferLib,
                        @Cached InlinedBranchProfile negativeSize,
                        @Cached InlinedLoopConditionProfile loopProfile,
                        @Bind PythonLanguage language) {
            int value = valueIn;
            if (value < 0) {
                negativeSize.enter(inliningTarget);
                value = 0;
            }
            try {
                int newLength = Math.max(PythonUtils.multiplyExact(self.getLength(), value), 0);
                PArray newArray = PFactory.createArray(language, self.getFormatString(), self.getFormat(), newLength);
                int segmentLength = self.getBytesLength();
                loopProfile.profileCounted(inliningTarget, value);
                for (int i = 0; loopProfile.inject(inliningTarget, i < value); i++) {
                    bufferLib.readIntoBuffer(self.getBuffer(), 0, newArray.getBuffer(), segmentLength * i, segmentLength, bufferLib);
                }
                return newArray;
            } catch (OverflowException e) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                throw PRaiseNode.raiseStatic(inliningTarget, MemoryError);
            }
        }

        @Fallback
        static PArray doZeroSize(Object self, @SuppressWarnings("unused") int value) {
            return (PArray) self;
        }
    }

    @Slot(value = SlotKind.sq_inplace_repeat, isComplex = true)
    @GenerateNodeFactory
    abstract static class IMulNode extends SqRepeatBuiltinNode {
        @Specialization
        static Object concat(PArray self, int value,
                        @Bind Node inliningTarget,
                        @CachedLibrary(limit = "2") PythonBufferAccessLibrary bufferLib,
                        @Cached ArrayNodes.EnsureCapacityNode ensureCapacityNode,
                        @Cached ArrayNodes.SetLengthNode setLengthNode,
                        @Cached PRaiseNode raiseNode) {
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
                throw PRaiseNode.raiseStatic(inliningTarget, MemoryError);
            }
        }
    }

    @Slot(value = SlotKind.tp_richcompare, isComplex = true)
    @GenerateNodeFactory
    @ImportStatic({BufferFormat.class, PGuards.class})
    abstract static class ArrayRichCmpNode extends TpSlotRichCompare.RichCmpBuiltinNode {

        @Specialization(guards = "!isFloatingPoint(left.getFormat()) || (left.getFormat() != right.getFormat())")
        static Object cmpItems(VirtualFrame frame, PArray left, PArray right, RichCmpOp op,
                        @Bind Node inliningTarget,
                        @Exclusive @Cached InlinedBranchProfile fullCmpProfile,
                        @Exclusive @Cached PyObjectRichCompareBool richCmpEqNode,
                        @Exclusive @Cached PyObjectRichCompare richCmpOpNode,
                        @Exclusive @Cached ArrayNodes.GetValueNode getLeft,
                        @Exclusive @Cached ArrayNodes.GetValueNode getRight,
                        @Exclusive @Cached InlinedLoopConditionProfile loopProfile) {
            if (left.getLength() != right.getLength() && op.isEqOrNe()) {
                // the same fast-path as CPython
                return op == RichCmpOp.Py_NE;
            }
            fullCmpProfile.enter(inliningTarget);
            int commonLength = Math.min(left.getLength(), right.getLength());
            loopProfile.profileCounted(inliningTarget, commonLength); // ignoring the early exit
            for (int i = 0; loopProfile.inject(inliningTarget, i < commonLength); i++) {
                Object leftValue = getLeft.execute(inliningTarget, left, i);
                Object rightValue = getRight.execute(inliningTarget, right, i);
                if (!richCmpEqNode.execute(frame, inliningTarget, leftValue, rightValue, RichCmpOp.Py_EQ)) {
                    if (op == RichCmpOp.Py_EQ) {
                        return false;
                    } else if (op == RichCmpOp.Py_NE) {
                        return true;
                    }
                    return richCmpOpNode.execute(frame, inliningTarget, leftValue, rightValue, op);
                }
            }
            if (op.isEqOrNe()) {
                return op.isEq();
            }
            return op.compareResultToBool(left.getLength() - right.getLength());
        }

        // Separate specialization for float/double is needed because, normally in
        // PyObjectRichCompareBool we treat NaNs as equals, this is because CPython does identity
        // check in PyObjectRichCompareBool. We do not really have identity for doubles, so we
        // cannot say if NaNs, which are by definition not equal (PyObjectRichCompare always returns
        // false for NaN and Nan), are identical or not. So we choose that all NaNs with equal bit
        // patterns are identical. This is however different for arrays, where the identity cannot
        // be preserved, so here we know for sure that if we see two NaNs, PyObjectRichCompareBool
        // would return false on CPython, and so we do the same. This is tested in CPython tests.
        @Specialization(guards = {"isFloatingPoint(left.getFormat())", "left.getFormat() == right.getFormat()"})
        static boolean cmpDoubles(VirtualFrame frame, PArray left, PArray right, RichCmpOp op,
                        @Bind Node inliningTarget,
                        @Exclusive @Cached InlinedBranchProfile fullCmpProfile,
                        @Exclusive @Cached ArrayNodes.GetValueNode getLeft,
                        @Exclusive @Cached ArrayNodes.GetValueNode getRight,
                        @Exclusive @Cached InlinedLoopConditionProfile loopProfile) {
            if (left.getLength() != right.getLength() && op.isEqOrNe()) {
                // the same fast-path as CPython
                return op == RichCmpOp.Py_NE;
            }
            fullCmpProfile.enter(inliningTarget);
            int commonLength = Math.min(left.getLength(), right.getLength());
            loopProfile.profileCounted(inliningTarget, commonLength); // ignoring the early exit
            for (int i = 0; loopProfile.inject(inliningTarget, i < commonLength); i++) {
                double leftValue = (Double) getLeft.execute(inliningTarget, left, i);
                double rightValue = (Double) getRight.execute(inliningTarget, right, i);
                if (leftValue != rightValue) {
                    return op.compare(leftValue, rightValue);
                }
            }
            if (op.isEqOrNe()) {
                return op.isEq();
            }
            return op.compareResultToBool(left.getLength() - right.getLength());
        }

        @Specialization(guards = "!isArray(right)")
        @SuppressWarnings("unused")
        static Object cmp(PArray left, Object right, RichCmpOp op) {
            return PNotImplemented.NOT_IMPLEMENTED;
        }

        @Specialization(guards = "!isArray(left)")
        @SuppressWarnings("unused")
        static Object error(Object left, Object right, RichCmpOp op,
                        @Bind Node inliningTarget) {
            throw PRaiseNode.raiseStatic(inliningTarget, PythonErrorType.TypeError, ErrorMessages.DESCRIPTOR_S_REQUIRES_S_OBJ_RECEIVED_P, op.getPythonName(), J_ARRAY + "." + J_ARRAY, left);
        }
    }

    @Slot(value = SlotKind.sq_contains, isComplex = true)
    @GenerateNodeFactory
    abstract static class ContainsNode extends SqContainsBuiltinNode {
        @Specialization
        static boolean contains(VirtualFrame frame, PArray self, Object value,
                        @Bind Node inliningTarget,
                        @Cached PyObjectRichCompareBool eqNode,
                        @Cached ArrayNodes.GetValueNode getValueNode) {
            for (int i = 0; i < self.getLength(); i++) {
                if (eqNode.execute(frame, inliningTarget, getValueNode.execute(inliningTarget, self, i), value, RichCmpOp.Py_EQ)) {
                    return true;
                }
            }
            return false;
        }
    }

    @Slot(value = SlotKind.tp_repr, isComplex = true)
    @GenerateNodeFactory
    abstract static class ReprNode extends PythonUnaryBuiltinNode {
        @Specialization
        static TruffleString repr(VirtualFrame frame, PArray self,
                        @Bind Node inliningTarget,
                        @Cached PyObjectReprAsTruffleStringNode repr,
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
                    appendStringNode.execute(sb, repr.execute(frame, inliningTarget, toUnicodeNode.execute(frame, self)));
                } else {
                    appendStringNode.execute(sb, T_COMMA_SPACE);
                    appendStringNode.execute(sb, T_LBRACKET);
                    for (int i = 0; i < length; i++) {
                        if (i > 0) {
                            appendStringNode.execute(sb, T_COMMA_SPACE);
                        }
                        Object value = getValueNode.execute(inliningTarget, self, i);
                        appendStringNode.execute(sb, cast.execute(inliningTarget, repr.execute(frame, inliningTarget, value)));
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
        static Object doIt(PArray self, int index,
                        @Bind Node inliningTarget,
                        @Cached PRaiseNode raiseNode,
                        @Cached ArrayNodes.GetValueNode getValueNode) {
            return getItem(inliningTarget, self, index, raiseNode, getValueNode);
        }

        private static Object getItem(Node inliningTarget, PArray self, int index, PRaiseNode raiseNode, GetValueNode getValueNode) {
            checkBounds(inliningTarget, raiseNode, ErrorMessages.ARRAY_OUT_OF_BOUNDS, index, self.getLength());
            return getValueNode.execute(inliningTarget, self, index);
        }
    }

    @Slot(value = SlotKind.mp_subscript, isComplex = true)
    @GenerateNodeFactory
    abstract static class MpSubscriptNode extends MpSubscriptBuiltinNode {
        @Specialization(guards = "!isPSlice(idx)")
        static Object doIndex(VirtualFrame frame, PArray self, Object idx,
                        @Bind Node inliningTarget,
                        @Cached PyIndexCheckNode indexCheckNode,
                        @Cached PRaiseNode raiseNode,
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
                        @Bind Node inliningTarget,
                        @CachedLibrary(limit = "2") PythonBufferAccessLibrary bufferLib,
                        @Cached InlinedByteValueProfile itemShiftProfile,
                        @Exclusive @Cached InlinedConditionProfile simpleStepProfile,
                        @Cached SliceNodes.SliceUnpack sliceUnpack,
                        @Cached SliceNodes.AdjustIndices adjustIndices,
                        @Bind PythonLanguage language) {
            PSlice.SliceInfo sliceInfo = adjustIndices.execute(inliningTarget, self.getLength(), sliceUnpack.execute(inliningTarget, slice));
            int itemShift = itemShiftProfile.profile(inliningTarget, (byte) self.getItemSizeShift());
            int itemsize = self.getItemSize();
            PArray newArray;
            try {
                newArray = PFactory.createArray(language, self.getFormatString(), self.getFormat(), sliceInfo.sliceLength);
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
        private static PException raiseNonIntIndex(Node inliningTarget, PRaiseNode raiseNode) {
            throw raiseNode.raise(inliningTarget, PythonBuiltinClassType.TypeError, ErrorMessages.ARRAY_INDICES_MUST_BE_INTS);
        }
    }

    @Slot(value = SlotKind.sq_ass_item, isComplex = true)
    @GenerateNodeFactory
    abstract static class SetItemNode extends SqAssItemBuiltinNode {

        @Specialization(guards = "!isNoValue(value)")
        static void setitem(VirtualFrame frame, PArray self, int index, Object value,
                        @Bind Node inliningTarget,
                        @Cached ArrayNodes.PutValueNode putValueNode,
                        @Exclusive @Cached PRaiseNode raiseNode) {
            checkBounds(inliningTarget, raiseNode, ErrorMessages.ARRAY_ASSIGN_OUT_OF_BOUNDS, index, self.getLength());
            putValueNode.execute(frame, inliningTarget, self, index, value);
        }

        // @Exclusive for truffle-interpreted-performance
        @Specialization(guards = "isNoValue(value)")
        static void delitem(PArray self, int index, @SuppressWarnings("unused") Object value,
                        @Bind Node inliningTarget,
                        @Cached DeleteArraySliceNode deleteSliceNode,
                        @Exclusive @Cached PRaiseNode raiseNode) {
            checkBounds(inliningTarget, raiseNode, ErrorMessages.ARRAY_ASSIGN_OUT_OF_BOUNDS, index, self.getLength());
            self.checkCanResize(inliningTarget, raiseNode);
            deleteSliceNode.execute(inliningTarget, self, index, 1);
        }
    }

    @Slot(value = SlotKind.mp_ass_subscript, isComplex = true)
    @GenerateNodeFactory
    abstract static class SetSubscriptNode extends MpAssSubscriptBuiltinNode {

        // @Exclusive for truffle-interpreted-performance
        @Specialization(guards = {"!isPSlice(idx)", "!isNoValue(value)"})
        static void setitem(VirtualFrame frame, PArray self, Object idx, Object value,
                        @Bind Node inliningTarget,
                        @Exclusive @Cached PyNumberIndexNode indexNode,
                        @Shared @Cached NormalizeIndexWithBoundsCheckNode normalizeIndexNode,
                        @Cached ArrayNodes.PutValueNode putValueNode) {
            int index = normalizeIndexNode.execute(indexNode.execute(frame, inliningTarget, idx), self.getLength(), ErrorMessages.ARRAY_ASSIGN_OUT_OF_BOUNDS);
            putValueNode.execute(frame, inliningTarget, self, index, value);
        }

        @Specialization(guards = {"!isPSlice(idx)", "isNoValue(value)"})
        static void delitem(VirtualFrame frame, PArray self, Object idx, @SuppressWarnings("unused") Object value,
                        @Bind Node inliningTarget,
                        @Exclusive @Cached PyNumberIndexNode indexNode,
                        @Shared @Cached NormalizeIndexWithBoundsCheckNode normalizeIndexNode,
                        @Exclusive @Cached DeleteArraySliceNode deleteSliceNode,
                        @Exclusive @Cached PRaiseNode raiseNode) {
            self.checkCanResize(inliningTarget, raiseNode);
            int index = normalizeIndexNode.execute(indexNode.execute(frame, inliningTarget, idx), self.getLength(), ErrorMessages.ARRAY_ASSIGN_OUT_OF_BOUNDS);
            deleteSliceNode.execute(inliningTarget, self, index, 1);
        }

        // @Exclusive for truffle-interpreted-performance
        @Specialization
        static void setitem(PArray self, PSlice slice, Object other,
                        @Bind Node inliningTarget,
                        @CachedLibrary(limit = "2") PythonBufferAccessLibrary bufferLib,
                        @Cached InlinedBranchProfile hasOtherProfile,
                        @Cached InlinedBranchProfile isDelItemProfile,
                        @Cached InlinedBranchProfile otherTypeErrorProfile,
                        @Cached InlinedBranchProfile sameArrayProfile,
                        @Cached InlinedBranchProfile simpleStepProfile,
                        @Cached InlinedBranchProfile complexDeleteProfile,
                        @Cached InlinedBranchProfile differentLengthProfile,
                        @Cached InlinedBranchProfile copyProfile,
                        @Cached InlinedBranchProfile wrongLengthProfile,
                        @Cached InlinedConditionProfile growProfile,
                        @Cached InlinedBranchProfile stepAssignProfile,
                        @Cached InlinedByteValueProfile itemShiftProfile,
                        @Cached SliceNodes.SliceUnpack sliceUnpack,
                        @Cached SliceNodes.AdjustIndices adjustIndices,
                        @Exclusive @Cached DeleteArraySliceNode deleteSliceNode,
                        @Cached ArrayNodes.ShiftNode shiftNode,
                        @Cached ArrayNodes.SetLengthNode setLengthNode,
                        @Exclusive @Cached PRaiseNode raiseNode) {
            int length = self.getLength();
            PSlice.SliceInfo sliceInfo = adjustIndices.execute(inliningTarget, length, sliceUnpack.execute(inliningTarget, slice));
            int start = sliceInfo.start;
            int stop = sliceInfo.stop;
            int step = sliceInfo.step;
            int sliceLength = sliceInfo.sliceLength;
            int itemShift = itemShiftProfile.profile(inliningTarget, (byte) self.getItemSizeShift());
            int itemsize = self.getItemSize();
            int needed;
            Object sourceBuffer;
            if (other instanceof PArray otherArray) {
                hasOtherProfile.enter(inliningTarget);
                if (self.getFormat() != otherArray.getFormat()) {
                    throw raiseNode.raise(inliningTarget, TypeError, BAD_ARG_TYPE_FOR_BUILTIN_OP);
                }
                sourceBuffer = otherArray.getBuffer();
                needed = otherArray.getLength();
                if (sourceBuffer == self.getBuffer()) {
                    sameArrayProfile.enter(inliningTarget);
                    byte[] tmp = new byte[needed * itemsize];
                    bufferLib.readIntoByteArray(sourceBuffer, 0, tmp, 0, tmp.length);
                    sourceBuffer = new ByteSequenceStorage(tmp);
                }
            } else if (other == PNone.NO_VALUE) {
                isDelItemProfile.enter(inliningTarget);
                sourceBuffer = null;
                needed = 0;
            } else {
                otherTypeErrorProfile.enter(inliningTarget);
                throw raiseNode.raise(inliningTarget, TypeError, ErrorMessages.CAN_ONLY_ASSIGN_ARRAY, other);
            }
            if (step == 1) {
                simpleStepProfile.enter(inliningTarget);
                if (sliceLength != needed) {
                    differentLengthProfile.enter(inliningTarget);
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
                if (needed > 0) {
                    copyProfile.enter(inliningTarget);
                    bufferLib.readIntoBuffer(sourceBuffer, 0, self.getBuffer(), start << itemShift, needed << itemShift, bufferLib);
                }
            } else if (needed == 0) {
                complexDeleteProfile.enter(inliningTarget);
                if (sliceLength > 0) {
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
            } else if (needed == sliceLength) {
                stepAssignProfile.enter(inliningTarget);
                for (int cur = start, i = 0; i < sliceLength; cur += step, i++) {
                    bufferLib.readIntoBuffer(sourceBuffer, i << itemShift, self.getBuffer(), cur << itemShift, itemsize, bufferLib);
                }
            } else {
                wrongLengthProfile.enter(inliningTarget);
                throw raiseNode.raise(inliningTarget, ValueError, ErrorMessages.ATTEMPT_ASSIGN_ARRAY_OF_SIZE, needed, sliceLength);
            }
        }
    }

    @Slot(value = SlotKind.tp_iter, isComplex = true)
    @GenerateNodeFactory
    abstract static class IterNode extends PythonUnaryBuiltinNode {

        @Specialization
        static Object getitem(PArray self,
                        @Bind PythonLanguage language) {
            return PFactory.createArrayIterator(language, self);
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
                        @Bind Node inliningTarget,
                        @Cached @Exclusive GetClassNode getClassNode,
                        @Cached @Exclusive PyObjectLookupAttr lookupDict,
                        @Cached ToListNode toListNode,
                        @Bind PythonLanguage language) {
            Object cls = getClassNode.execute(inliningTarget, self);
            Object dict = lookupDict.execute(frame, inliningTarget, self, T___DICT__);
            if (dict == PNone.NO_VALUE) {
                dict = PNone.NONE;
            }
            PTuple args = PFactory.createTuple(language, new Object[]{self.getFormatString(), toListNode.execute(frame, self)});
            return PFactory.createTuple(language, new Object[]{cls, args, dict});
        }

        @Specialization(guards = "protocol >= 3")
        static Object reduce(VirtualFrame frame, PArray self, @SuppressWarnings("unused") int protocol,
                        @Bind Node inliningTarget,
                        @Cached @Exclusive GetClassNode getClassNode,
                        @Cached @Exclusive PyObjectLookupAttr lookupDict,
                        @Cached PyObjectGetAttr getReconstructor,
                        @Cached ToBytesNode toBytesNode,
                        @Bind PythonLanguage language) {
            PythonModule arrayModule = PythonContext.get(inliningTarget).lookupBuiltinModule(T_ARRAY);
            PArray.MachineFormat mformat = PArray.MachineFormat.forFormat(self.getFormat());
            assert mformat != null;
            Object cls = getClassNode.execute(inliningTarget, self);
            Object dict = lookupDict.execute(frame, inliningTarget, self, T___DICT__);
            if (dict == PNone.NO_VALUE) {
                dict = PNone.NONE;
            }
            Object reconstructor = getReconstructor.execute(frame, inliningTarget, arrayModule, T_ARRAY_RECONSTRUCTOR);
            PTuple args = PFactory.createTuple(language, new Object[]{cls, self.getFormatString(), mformat.code, toBytesNode.execute(frame, self)});
            return PFactory.createTuple(language, new Object[]{reconstructor, args, dict});
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
                        @Bind Node inliningTarget,
                        @Bind PythonLanguage language,
                        @Cached ArrayNodes.EnsureNativeStorageNode ensureNativeStorageNode,
                        @CachedLibrary(limit = "1") InteropLibrary lib) {
            Object nativePointer = ensureNativeStorageNode.execute(inliningTarget, self).getPtr();
            if (!(nativePointer instanceof Long)) {
                try {
                    nativePointer = lib.asPointer(nativePointer);
                } catch (UnsupportedMessageException e) {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    throw PRaiseNode.raiseStatic(inliningTarget, NotImplementedError);
                }
            }
            return PFactory.createTuple(language, new Object[]{nativePointer, self.getLength()});
        }
    }

    @Builtin(name = J_APPEND, minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    abstract static class AppendNode extends PythonBinaryBuiltinNode {
        @Specialization
        static Object append(VirtualFrame frame, PArray self, Object value,
                        @Bind Node inliningTarget,
                        @Cached ArrayNodes.EnsureCapacityNode ensureCapacityNode,
                        @Cached ArrayNodes.SetLengthNode setLengthNode,
                        @Cached ArrayNodes.PutValueNode putValueNode,
                        @Cached PRaiseNode raiseNode) {
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
                throw PRaiseNode.raiseStatic(inliningTarget, MemoryError);
            }
        }
    }

    @Builtin(name = J_EXTEND, minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    abstract static class ExtendNode extends PythonBinaryBuiltinNode {
        @Specialization(guards = "self.getFormat() == value.getFormat()")
        static Object extend(PArray self, PArray value,
                        @Bind Node inliningTarget,
                        @CachedLibrary(limit = "2") PythonBufferAccessLibrary bufferLib,
                        @Exclusive @Cached ArrayNodes.EnsureCapacityNode ensureCapacityNode,
                        @Exclusive @Cached ArrayNodes.SetLengthNode setLengthNode,
                        @Exclusive @Cached PRaiseNode raiseNode) {
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
                throw PRaiseNode.raiseStatic(inliningTarget, MemoryError);
            }
        }

        @Specialization
        static Object extend(VirtualFrame frame, PArray self, PSequence value,
                        @Bind Node inliningTarget,
                        @Exclusive @Cached ArrayNodes.PutValueNode putValueNode,
                        @Cached SequenceNodes.GetSequenceStorageNode getSequenceStorageNode,
                        @Cached SequenceStorageNodes.GetItemScalarNode getItemNode,
                        @Exclusive @Cached ArrayNodes.EnsureCapacityNode ensureCapacityNode,
                        @Exclusive @Cached ArrayNodes.SetLengthNode setLengthNode,
                        @Exclusive @Cached PRaiseNode raiseNode) {
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
                throw PRaiseNode.raiseStatic(inliningTarget, MemoryError);
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
                        @Bind Node inliningTarget,
                        @Cached PyObjectGetIter getIter,
                        @Cached PyIterNextNode nextNode,
                        @Exclusive @Cached ArrayNodes.PutValueNode putValueNode,
                        @Exclusive @Cached ArrayNodes.EnsureCapacityNode ensureCapacityNode,
                        @Exclusive @Cached ArrayNodes.SetLengthNode setLengthNode,
                        @Exclusive @Cached PRaiseNode raiseNode) {
            Object iter = getIter.execute(frame, inliningTarget, value);
            int length = self.getLength();
            while (true) {
                Object nextValue;
                try {
                    nextValue = nextNode.execute(frame, inliningTarget, iter);
                } catch (IteratorExhausted e) {
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
                    throw PRaiseNode.raiseStatic(inliningTarget, MemoryError);
                }
                putValueNode.execute(frame, inliningTarget, self, length - 1, nextValue);
                setLengthNode.execute(inliningTarget, self, length);
            }

            return PNone.NONE;
        }

        @Specialization(guards = "self.getFormat() != value.getFormat()")
        @SuppressWarnings("unused")
        static Object error(PArray self, PArray value,
                        @Bind Node inliningTarget) {
            // CPython allows extending an array with an arbitrary iterable. Except a differently
            // formatted array. Weird
            throw PRaiseNode.raiseStatic(inliningTarget, TypeError, ErrorMessages.CAN_ONLY_EXTEND_WITH_ARRAY_OF_SAME_KIND);
        }
    }

    @Builtin(name = "insert", minNumOfPositionalArgs = 3, numOfPositionalOnlyArgs = 3, parameterNames = {"$self", "index", "value"})
    @ArgumentClinic(name = "index", conversion = ArgumentClinic.ClinicConversion.Index)
    @GenerateNodeFactory
    abstract static class InsertNode extends PythonTernaryClinicBuiltinNode {
        @Specialization
        static Object insert(VirtualFrame frame, PArray self, int inputIndex, Object value,
                        @Bind Node inliningTarget,
                        @Cached NormalizeIndexWithoutBoundsCheckNode normalizeIndexNode,
                        @Cached ArrayNodes.CheckValueNode checkValueNode,
                        @Cached ArrayNodes.PutValueNode putValueNode,
                        @Cached ArrayNodes.ShiftNode shiftNode,
                        @Cached PRaiseNode raiseNode) {
            int index = normalizeIndexNode.execute(inputIndex, self.getLength(), ErrorMessages.INDEX_OUT_OF_RANGE);
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
                        @Bind Node inliningTarget,
                        @Cached PyObjectRichCompareBool eqNode,
                        @Cached ArrayNodes.GetValueNode getValueNode,
                        @Cached DeleteArraySliceNode deleteSliceNode,
                        @Cached PRaiseNode raiseNode) {
            for (int i = 0; i < self.getLength(); i++) {
                Object item = getValueNode.execute(inliningTarget, self, i);
                if (eqNode.execute(frame, inliningTarget, item, value, RichCmpOp.Py_EQ)) {
                    self.checkCanResize(inliningTarget, raiseNode);
                    deleteSliceNode.execute(inliningTarget, self, i, 1);
                    return PNone.NONE;
                }
            }
            throw raiseNode.raise(inliningTarget, ValueError, ErrorMessages.ARRAY_REMOVE_X_NOT_IN_ARRAY);
        }
    }

    @Builtin(name = "pop", minNumOfPositionalArgs = 1, numOfPositionalOnlyArgs = 2, parameterNames = {"$self", "index"})
    @ArgumentClinic(name = "index", conversion = ArgumentClinic.ClinicConversion.Index, defaultValue = "-1")
    @GenerateNodeFactory
    abstract static class PopNode extends PythonBinaryClinicBuiltinNode {
        @Specialization
        static Object pop(PArray self, int inputIndex,
                        @Bind Node inliningTarget,
                        @Cached NormalizeIndexWithBoundsCheckNode normalizeIndexNode,
                        @Cached ArrayNodes.GetValueNode getValueNode,
                        @Cached DeleteArraySliceNode deleteSliceNode,
                        @Cached PRaiseNode raiseNode) {
            if (self.getLength() == 0) {
                throw raiseNode.raise(inliningTarget, IndexError, ErrorMessages.POP_FROM_EMPTY_ARRAY);
            }
            int index = normalizeIndexNode.execute(inputIndex, self.getLength(), ErrorMessages.POP_INDEX_OUT_OF_RANGE);
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
                        @Bind Node inliningTarget,
                        @Cached("createFor($node)") IndirectCallData indirectCallData,
                        @CachedLibrary(limit = "3") PythonBufferAccessLibrary bufferLib,
                        @Cached ArrayNodes.EnsureCapacityNode ensureCapacityNode,
                        @Cached ArrayNodes.SetLengthNode setLengthNode,
                        @Cached PRaiseNode raiseNode) {
            try {
                int itemShift = self.getItemSizeShift();
                int oldSize = self.getLength();
                try {
                    int bufferLength = bufferLib.getBufferLength(buffer);
                    if (!PythonUtils.isDivisible(bufferLength, itemShift)) {
                        throw raiseNode.raise(inliningTarget, ValueError, ErrorMessages.BYTES_ARRAY_NOT_MULTIPLE_OF_ARRAY_SIZE);
                    }
                    int newLength = PythonUtils.addExact(oldSize, bufferLength >> itemShift);
                    self.checkCanResize(inliningTarget, raiseNode);
                    ensureCapacityNode.execute(inliningTarget, self, newLength);
                    setLengthNode.execute(inliningTarget, self, newLength);
                    bufferLib.readIntoBuffer(buffer, 0, self.getBuffer(), oldSize << itemShift, bufferLength, bufferLib);
                } catch (OverflowException e) {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    throw PRaiseNode.raiseStatic(inliningTarget, MemoryError);
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
                        @Bind Node inliningTarget,
                        @Cached PyObjectCallMethodObjArgs callMethod,
                        @Cached PyObjectSizeNode sizeNode,
                        @Cached InlinedConditionProfile nNegativeProfile,
                        @Cached FromBytesNode fromBytesNode,
                        @Cached PRaiseNode raiseNode) {
            if (nNegativeProfile.profile(inliningTarget, n < 0)) {
                throw raiseNode.raise(inliningTarget, ValueError, ErrorMessages.NEGATIVE_COUNT);
            }
            int nbytes = n << self.getItemSizeShift();
            Object readResult = callMethod.execute(frame, inliningTarget, file, T_READ, nbytes);
            if (readResult instanceof PBytes) {
                int readLength = sizeNode.execute(frame, inliningTarget, readResult);
                fromBytesNode.executeWithoutClinic(frame, self, readResult);
                // It would make more sense to check this before the frombytes call, but CPython
                // does it this way
                if (readLength != nbytes) {
                    throw raiseNode.raise(inliningTarget, EOFError, ErrorMessages.READ_DIDNT_RETURN_ENOUGH_BYTES);
                }
            } else {
                throw raiseNode.raise(inliningTarget, TypeError, ErrorMessages.READ_DIDNT_RETURN_BYTES);
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
                        @Bind Node inliningTarget,
                        @Cached SequenceNodes.GetSequenceStorageNode getSequenceStorageNode,
                        @Cached SequenceStorageNodes.GetItemScalarNode getItemScalarNode,
                        @Cached ArrayNodes.EnsureCapacityNode ensureCapacityNode,
                        @Cached ArrayNodes.SetLengthNode setLengthNode,
                        @Cached ArrayNodes.PutValueNode putValueNode,
                        @Cached PRaiseNode raiseNode) {
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
                throw PRaiseNode.raiseStatic(inliningTarget, MemoryError);
            }
        }

        @Fallback
        @SuppressWarnings("unused")
        static Object error(Object self, Object arg,
                        @Bind Node inliningTarget) {
            throw PRaiseNode.raiseStatic(inliningTarget, TypeError, ErrorMessages.ARG_MUST_BE_LIST);
        }
    }

    @Builtin(name = "fromunicode", minNumOfPositionalArgs = 2, numOfPositionalOnlyArgs = 2, parameterNames = {"$self", "str"})
    @ArgumentClinic(name = "str", conversion = ArgumentClinic.ClinicConversion.TString)
    @GenerateNodeFactory
    public abstract static class FromUnicodeNode extends PythonBinaryClinicBuiltinNode {
        @Specialization
        static Object fromunicode(VirtualFrame frame, PArray self, TruffleString str,
                        @Bind Node inliningTarget,
                        @Cached ArrayNodes.PutValueNode putValueNode,
                        @Cached ArrayNodes.EnsureCapacityNode ensureCapacityNode,
                        @Cached ArrayNodes.SetLengthNode setLengthNode,
                        @Cached TruffleString.CodePointLengthNode codePointLengthNode,
                        @Cached TruffleString.CreateCodePointIteratorNode createCodePointIteratorNode,
                        @Cached TruffleStringIterator.NextNode nextNode,
                        @Cached TruffleString.FromCodePointNode fromCodePointNode,
                        @Cached PRaiseNode raiseNode) {
            try {
                int length = codePointLengthNode.execute(str, TS_ENCODING);
                int newLength = PythonUtils.addExact(self.getLength(), length);
                self.checkCanResize(inliningTarget, raiseNode);
                ensureCapacityNode.execute(inliningTarget, self, newLength);
                TruffleStringIterator it = createCodePointIteratorNode.execute(str, TS_ENCODING);
                int codePointIndex = 0;
                while (it.hasNext()) {
                    TruffleString value = fromCodePointNode.execute(nextNode.execute(it, TS_ENCODING), TS_ENCODING, true);
                    putValueNode.execute(frame, inliningTarget, self, self.getLength() + codePointIndex++, value);
                }
                setLengthNode.execute(inliningTarget, self, newLength);
                return PNone.NONE;
            } catch (OverflowException e) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                throw PRaiseNode.raiseStatic(inliningTarget, MemoryError);
            }
        }

        @Fallback
        @SuppressWarnings("unused")
        static Object error(Object self, Object arg,
                        @Bind Node inliningTarget) {
            throw PRaiseNode.raiseStatic(inliningTarget, TypeError, ErrorMessages.FROMUNICODE_ARG_MUST_BE_STR_NOT_P, arg);
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
                        @Bind PythonLanguage language) {
            byte[] bytes = new byte[self.getBytesLength()];
            bufferLib.readIntoByteArray(self.getBuffer(), 0, bytes, 0, bytes.length);
            return PFactory.createBytes(language, bytes);
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
                        @Bind Node inliningTarget,
                        @Cached InlinedConditionProfile formatProfile,
                        @Cached ArrayNodes.GetValueNode getValueNode,
                        @Cached TruffleStringBuilder.AppendStringNode appendStringNode,
                        @Cached TruffleStringBuilder.ToStringNode toStringNode,
                        @Cached PRaiseNode raiseNode) {
            if (formatProfile.profile(inliningTarget, self.getFormat() != BufferFormat.UNICODE)) {
                throw raiseNode.raise(inliningTarget, ValueError, ErrorMessages.MAY_ONLY_BE_CALLED_ON_UNICODE_TYPE_ARRAYS);
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
                        @Bind Node inliningTarget,
                        @Bind PythonLanguage language,
                        @CachedLibrary(limit = "2") PythonBufferAccessLibrary bufferLib,
                        @Cached PyObjectCallMethodObjArgs callMethod) {
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
                    callMethod.execute(frame, inliningTarget, file, T_WRITE, PFactory.createBytes(language, buffer));
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
    @ArgumentClinic(name = "start", conversion = ArgumentClinic.ClinicConversion.SliceIndex, defaultValue = "0")
    @ArgumentClinic(name = "end", conversion = ArgumentClinic.ClinicConversion.SliceIndex, defaultValue = "Integer.MAX_VALUE")
    @GenerateNodeFactory
    abstract static class IndexNode extends PythonQuaternaryClinicBuiltinNode {
        @Specialization
        static int index(VirtualFrame frame, PArray self, Object value, int start, int stop,
                        @Bind Node inliningTarget,
                        @Cached PyObjectRichCompareBool eqNode,
                        @Cached ArrayNodes.GetValueNode getValueNode,
                        @Cached PRaiseNode raiseNode) {
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
                if (eqNode.execute(frame, inliningTarget, getValueNode.execute(inliningTarget, self, i), value, RichCmpOp.Py_EQ)) {
                    return i;
                }
            }
            throw raiseNode.raise(inliningTarget, ValueError, ErrorMessages.ARRAY_INDEX_X_NOT_IN_ARRAY);
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
                        @Bind Node inliningTarget,
                        @Cached PyObjectRichCompareBool eqNode,
                        @Cached ArrayNodes.GetValueNode getValueNode) {
            int count = 0;
            for (int i = 0; i < self.getLength(); i++) {
                if (eqNode.execute(frame, inliningTarget, getValueNode.execute(inliningTarget, self, i), value, RichCmpOp.Py_EQ)) {
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
