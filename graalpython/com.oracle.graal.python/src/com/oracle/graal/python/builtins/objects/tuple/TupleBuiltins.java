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
package com.oracle.graal.python.builtins.objects.tuple;

import static com.oracle.graal.python.nodes.SpecialMethodNames.J___ADD__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___CLASS_GETITEM__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___CONTAINS__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___EQ__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___GETNEWARGS__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___GE__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___GT__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___HASH__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___ITER__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___LE__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___LT__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___MUL__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___NE__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___REPR__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___RMUL__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___TRUFFLE_RICHCOMPARE__;
import static com.oracle.graal.python.nodes.StringLiterals.T_COMMA;
import static com.oracle.graal.python.nodes.StringLiterals.T_COMMA_SPACE;
import static com.oracle.graal.python.nodes.StringLiterals.T_ELLIPSIS_IN_PARENS;
import static com.oracle.graal.python.nodes.StringLiterals.T_EMPTY_PARENS;
import static com.oracle.graal.python.nodes.StringLiterals.T_LPAREN;
import static com.oracle.graal.python.nodes.StringLiterals.T_RPAREN;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.TypeError;
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
import com.oracle.graal.python.builtins.modules.MathGuards;
import com.oracle.graal.python.builtins.objects.PNotImplemented;
import com.oracle.graal.python.builtins.objects.cext.PythonAbstractNativeObject;
import com.oracle.graal.python.builtins.objects.common.SequenceStorageNodes;
import com.oracle.graal.python.builtins.objects.common.SequenceStorageNodes.CmpNode;
import com.oracle.graal.python.builtins.objects.common.SequenceStorageNodes.ConcatNode;
import com.oracle.graal.python.builtins.objects.common.SequenceStorageNodes.ListGeneralizationNode;
import com.oracle.graal.python.builtins.objects.common.SequenceStorageNodes.SequenceStorageMpSubscriptNode;
import com.oracle.graal.python.builtins.objects.iterator.PDoubleSequenceIterator;
import com.oracle.graal.python.builtins.objects.iterator.PIntegerSequenceIterator;
import com.oracle.graal.python.builtins.objects.iterator.PLongSequenceIterator;
import com.oracle.graal.python.builtins.objects.iterator.PObjectSequenceIterator;
import com.oracle.graal.python.builtins.objects.iterator.PSequenceIterator;
import com.oracle.graal.python.builtins.objects.tuple.TupleBuiltinsClinicProviders.IndexNodeClinicProviderGen;
import com.oracle.graal.python.builtins.objects.type.TpSlots;
import com.oracle.graal.python.builtins.objects.type.slots.TpSlotBinaryFunc.MpSubscriptBuiltinNode;
import com.oracle.graal.python.builtins.objects.type.slots.TpSlotLen.LenBuiltinNode;
import com.oracle.graal.python.builtins.objects.type.slots.TpSlotSizeArgFun.SqItemBuiltinNode;
import com.oracle.graal.python.lib.PyIndexCheckNode;
import com.oracle.graal.python.lib.PyNumberAsSizeNode;
import com.oracle.graal.python.lib.PyObjectHashNode;
import com.oracle.graal.python.lib.PyObjectReprAsTruffleStringNode;
import com.oracle.graal.python.lib.PyObjectRichCompareBool;
import com.oracle.graal.python.lib.PyTupleCheckExactNode;
import com.oracle.graal.python.lib.PyTupleCheckNode;
import com.oracle.graal.python.lib.PyTupleGetItem;
import com.oracle.graal.python.lib.PyTupleSizeNode;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.builtins.TupleNodes.GetNativeTupleStorage;
import com.oracle.graal.python.nodes.builtins.TupleNodes.GetTupleStorage;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.builtins.PythonBinaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonQuaternaryClinicBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonTernaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonUnaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.clinic.ArgumentClinicProvider;
import com.oracle.graal.python.nodes.truffle.PythonArithmeticTypes;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.graal.python.runtime.exception.PythonErrorType;
import com.oracle.graal.python.runtime.object.PythonObjectFactory;
import com.oracle.graal.python.runtime.sequence.storage.DoubleSequenceStorage;
import com.oracle.graal.python.runtime.sequence.storage.IntSequenceStorage;
import com.oracle.graal.python.runtime.sequence.storage.LongSequenceStorage;
import com.oracle.graal.python.runtime.sequence.storage.ObjectSequenceStorage;
import com.oracle.graal.python.runtime.sequence.storage.SequenceStorage;
import com.oracle.graal.python.util.ComparisonOp;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.HostCompilerDirectives.InliningCutoff;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Exclusive;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.GenerateCached;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.NeverDefault;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.dsl.TypeSystemReference;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.profiles.InlinedBranchProfile;
import com.oracle.truffle.api.profiles.InlinedConditionProfile;
import com.oracle.truffle.api.strings.TruffleString;
import com.oracle.truffle.api.strings.TruffleStringBuilder;

@CoreFunctions(extendClasses = PythonBuiltinClassType.PTuple)
public final class TupleBuiltins extends PythonBuiltins {
    public static final TpSlots SLOTS = TupleBuiltinsSlotsGen.SLOTS;

    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return TupleBuiltinsFactory.getFactories();
    }

    // index(element)
    @Builtin(name = "index", minNumOfPositionalArgs = 2, parameterNames = {"$self", "value", "start", "stop"})
    @ArgumentClinic(name = "start", conversion = ArgumentClinic.ClinicConversion.SliceIndex, defaultValue = "0")
    @ArgumentClinic(name = "stop", conversion = ArgumentClinic.ClinicConversion.SliceIndex, defaultValue = "Integer.MAX_VALUE")
    @TypeSystemReference(PythonArithmeticTypes.class)
    @ImportStatic(MathGuards.class)
    @GenerateNodeFactory
    public abstract static class IndexNode extends PythonQuaternaryClinicBuiltinNode {

        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return IndexNodeClinicProviderGen.INSTANCE;
        }

        @Specialization
        int index(VirtualFrame frame, Object self, Object value, int startIn, int endIn,
                        @Bind("this") Node inliningTarget,
                        @Cached GetTupleStorage getTupleStorage,
                        @Cached InlinedBranchProfile startLe0Profile,
                        @Cached InlinedBranchProfile endLe0Profile,
                        @Cached SequenceStorageNodes.ItemIndexNode itemIndexNode,
                        @Cached PRaiseNode.Lazy raiseNode) {
            SequenceStorage storage = getTupleStorage.execute(inliningTarget, self);
            int start = startIn;
            if (start < 0) {
                startLe0Profile.enter(inliningTarget);
                start += storage.length();
                if (start < 0) {
                    start = 0;
                }
            }

            int end = endIn;
            if (end < 0) {
                endLe0Profile.enter(inliningTarget);
                end += storage.length();
            }

            // Note: ItemIndexNode normalizes the end to min(end, length(storage))
            int idx = itemIndexNode.execute(frame, inliningTarget, storage, value, start, end);
            if (idx != -1) {
                return idx;
            }
            throw raiseNode.get(inliningTarget).raise(PythonErrorType.ValueError, ErrorMessages.X_NOT_IN_TUPLE);
        }
    }

    @Builtin(name = "count", minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    public abstract static class CountNode extends PythonBinaryBuiltinNode {

        @Specialization
        long count(VirtualFrame frame, Object self, Object value,
                        @Bind("this") Node inliningTarget,
                        @Cached GetTupleStorage getTupleStorage,
                        @Cached("createNotNormalized()") SequenceStorageNodes.GetItemNode getItemNode,
                        @Cached PyObjectRichCompareBool.EqNode eqNode) {
            long count = 0;
            SequenceStorage tupleStore = getTupleStorage.execute(inliningTarget, self);
            for (int i = 0; i < tupleStore.length(); i++) {
                Object seqItem = getItemNode.execute(tupleStore, i);
                if (eqNode.compare(frame, inliningTarget, seqItem, value)) {
                    count++;
                }
            }
            return count;
        }
    }

    @Slot(SlotKind.sq_length)
    @Slot(SlotKind.mp_length)
    @GenerateUncached
    @GenerateNodeFactory
    public abstract static class LenNode extends LenBuiltinNode {
        @Specialization
        public int len(Object self,
                        @Bind("this") Node inliningTarget,
                        @Cached PyTupleSizeNode pyTupleSizeNode) {
            return pyTupleSizeNode.execute(inliningTarget, self);
        }
    }

    @Builtin(name = J___REPR__, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class ReprNode extends PythonUnaryBuiltinNode {
        @Override
        public abstract TruffleString execute(VirtualFrame VirtualFrame, Object arg);

        private static final TruffleString NULL = tsLiteral("(null)");

        public static TruffleString toString(VirtualFrame frame, Node inliningTarget, Object item, PyObjectReprAsTruffleStringNode reprNode) {
            if (item != null) {
                return reprNode.execute(frame, inliningTarget, item);
            }
            return NULL;
        }

        @Specialization
        public static TruffleString repr(VirtualFrame frame, Object self,
                        @Bind("this") Node inliningTarget,
                        @Cached GetTupleStorage getTupleStorage,
                        @Cached("createNotNormalized()") SequenceStorageNodes.GetItemNode getItemNode,
                        @Cached PyObjectReprAsTruffleStringNode reprNode,
                        @Cached TruffleStringBuilder.AppendStringNode appendStringNode,
                        @Cached TruffleStringBuilder.ToStringNode toStringNode) {
            SequenceStorage tupleStore = getTupleStorage.execute(inliningTarget, self);
            int len = tupleStore.length();
            if (len == 0) {
                return T_EMPTY_PARENS;
            }
            if (!PythonContext.get(reprNode).reprEnter(self)) {
                return T_ELLIPSIS_IN_PARENS;
            }
            try {
                TruffleStringBuilder buf = TruffleStringBuilder.create(TS_ENCODING);
                appendStringNode.execute(buf, T_LPAREN);
                for (int i = 0; i < len - 1; i++) {
                    appendStringNode.execute(buf, toString(frame, inliningTarget, getItemNode.execute(tupleStore, i), reprNode));
                    appendStringNode.execute(buf, T_COMMA_SPACE);
                }

                if (len > 0) {
                    appendStringNode.execute(buf, toString(frame, inliningTarget, getItemNode.execute(tupleStore, len - 1), reprNode));
                }

                if (len == 1) {
                    appendStringNode.execute(buf, T_COMMA);
                }

                appendStringNode.execute(buf, T_RPAREN);
                return toStringNode.execute(buf);
            } finally {
                PythonContext.get(reprNode).reprLeave(self);
            }
        }
    }

    @Slot(value = SlotKind.sq_item, isComplex = true)
    @GenerateNodeFactory
    public abstract static class TupleSqItem extends SqItemBuiltinNode {
        @Specialization
        static Object doIt(Object self, int index,
                        @Bind("this") Node inliningTarget,
                        @Cached PyTupleGetItem getItem) {
            return getItem.execute(inliningTarget, self, index);
        }
    }

    // TODO: replace calls to GetItemNode with TupleSqItem where appropriate
    @Slot(value = SlotKind.mp_subscript, isComplex = true)
    @GenerateNodeFactory
    public abstract static class GetItemNode extends MpSubscriptBuiltinNode {
        @Specialization
        static Object doIt(VirtualFrame frame, Object self, Object idx,
                        @Bind("this") Node inliningTarget,
                        @Cached InlinedConditionProfile validProfile,
                        @Cached PyIndexCheckNode indexCheckNode,
                        @Cached PRaiseNode.Lazy raiseNode,
                        @Cached GetTupleStorage getTupleStorage,
                        @Cached SequenceStorageMpSubscriptNode subscriptNode) {
            if (!validProfile.profile(inliningTarget, SequenceStorageMpSubscriptNode.isValidIndex(inliningTarget, idx, indexCheckNode))) {
                raiseNonIntIndex(inliningTarget, raiseNode, idx);
            }
            return subscriptNode.execute(frame, inliningTarget, getTupleStorage.execute(inliningTarget, self), idx,
                            ErrorMessages.TUPLE_OUT_OF_BOUNDS, PythonObjectFactory::createTuple);
        }

        @InliningCutoff
        private static void raiseNonIntIndex(Node inliningTarget, PRaiseNode.Lazy raiseNode, Object index) {
            raiseNode.get(inliningTarget).raise(PythonBuiltinClassType.TypeError, ErrorMessages.OBJ_INDEX_MUST_BE_INT_OR_SLICES, "tuple", index);
        }
    }

    @GenerateCached(false)
    abstract static class AbstractCmpNode extends PythonBinaryBuiltinNode {
        @Specialization
        static boolean doPTuple(VirtualFrame frame, PTuple left, PTuple right,
                        @Shared("cmp") @Cached("createCmp()") SequenceStorageNodes.CmpNode cmp) {
            return cmp.execute(frame, left.getSequenceStorage(), right.getSequenceStorage());
        }

        @Specialization(guards = {"checkRight.execute(inliningTarget, right)"}, limit = "1", replaces = "doPTuple")
        static boolean doTuple(VirtualFrame frame, Object left, Object right,
                        @Bind("this") Node inliningTarget,
                        @SuppressWarnings("unused") @Cached PyTupleCheckNode checkRight,
                        @Cached GetTupleStorage getLeft,
                        @Cached GetTupleStorage getRight,
                        @Shared("cmp") @Cached("createCmp()") SequenceStorageNodes.CmpNode cmp) {
            return cmp.execute(frame, getLeft.execute(inliningTarget, left), getRight.execute(inliningTarget, right));
        }

        @Fallback
        @SuppressWarnings("unused")
        static Object doOther(Object left, Object right) {
            return PNotImplemented.NOT_IMPLEMENTED;
        }

        @NeverDefault
        protected abstract SequenceStorageNodes.CmpNode createCmp();
    }

    @Builtin(name = J___EQ__, minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    abstract static class EqNode extends AbstractCmpNode {

        @NeverDefault
        @Override
        protected CmpNode createCmp() {
            return SequenceStorageNodes.CmpNode.createEq();
        }
    }

    @Builtin(name = J___NE__, minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    abstract static class NeNode extends AbstractCmpNode {

        @NeverDefault
        @Override
        protected CmpNode createCmp() {
            return SequenceStorageNodes.CmpNode.createNe();
        }
    }

    @Builtin(name = J___GE__, minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    abstract static class GeNode extends AbstractCmpNode {

        @NeverDefault
        @Override
        protected CmpNode createCmp() {
            return SequenceStorageNodes.CmpNode.createGe();
        }
    }

    @Builtin(name = J___LE__, minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    abstract static class LeNode extends AbstractCmpNode {

        @NeverDefault
        @Override
        protected CmpNode createCmp() {
            return SequenceStorageNodes.CmpNode.createLe();
        }
    }

    @Builtin(name = J___GT__, minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    abstract static class GtNode extends AbstractCmpNode {

        @NeverDefault
        @Override
        protected CmpNode createCmp() {
            return SequenceStorageNodes.CmpNode.createGt();
        }
    }

    @Builtin(name = J___LT__, minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    abstract static class LtNode extends AbstractCmpNode {

        @NeverDefault
        @Override
        protected CmpNode createCmp() {
            return SequenceStorageNodes.CmpNode.createLt();
        }
    }

    @Builtin(name = J___TRUFFLE_RICHCOMPARE__, minNumOfPositionalArgs = 3)
    @GenerateNodeFactory
    @ImportStatic(ComparisonOp.class)
    abstract static class RichCompareNode extends PythonTernaryBuiltinNode {

        @Specialization(guards = {"opCode == cachedOp.opCode"}, limit = "6")
        static Object doPTuple(VirtualFrame frame, PTuple left, PTuple right, @SuppressWarnings("unused") int opCode,
                        @SuppressWarnings("unused") @Cached("fromOpCode(opCode)") ComparisonOp cachedOp,
                        @Exclusive @Cached("createCmpNode(cachedOp)") SequenceStorageNodes.CmpNode cmpNode) {
            return cmpNode.execute(frame, left.getSequenceStorage(), right.getSequenceStorage());
        }

        @Specialization(guards = {"opCode == cachedOp.opCode"}, limit = "6", replaces = "doPTuple")
        static Object doGeneric(VirtualFrame frame, Object left, Object right, @SuppressWarnings("unused") int opCode,
                        @Bind("this") Node inliningTarget,
                        @SuppressWarnings("unused") @Cached("fromOpCode(opCode)") ComparisonOp cachedOp,
                        @Cached PyTupleCheckNode checkLeft,
                        @Cached PyTupleCheckNode checkRight,
                        @Cached GetTupleStorage getLeft,
                        @Cached GetTupleStorage getRight,
                        @Exclusive @Cached("createCmpNode(cachedOp)") SequenceStorageNodes.CmpNode cmpNode) {
            if (!checkLeft.execute(inliningTarget, left) || !checkRight.execute(inliningTarget, right)) {
                return PNotImplemented.NOT_IMPLEMENTED;
            }
            return cmpNode.execute(frame, getLeft.execute(inliningTarget, left), getRight.execute(inliningTarget, right));
        }

        @NeverDefault
        static SequenceStorageNodes.CmpNode createCmpNode(ComparisonOp op) {
            switch (op) {
                case LE:
                    return SequenceStorageNodes.CmpNode.createLe();
                case LT:
                    return SequenceStorageNodes.CmpNode.createLt();
                case EQ:
                    return SequenceStorageNodes.CmpNode.createEq();
                case NE:
                    return SequenceStorageNodes.CmpNode.createNe();
                case GT:
                    return SequenceStorageNodes.CmpNode.createGt();
                case GE:
                    return SequenceStorageNodes.CmpNode.createGe();
            }
            throw CompilerDirectives.shouldNotReachHere();
        }
    }

    @Builtin(name = J___ADD__, minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    abstract static class AddNode extends PythonBinaryBuiltinNode {

        @Specialization(guards = {"checkRight.execute(inliningTarget, right)"}, limit = "1")
        static PTuple doTuple(Object left, Object right,
                        @Bind("this") Node inliningTarget,
                        @SuppressWarnings("unused") @Cached PyTupleCheckNode checkRight,
                        @Cached GetTupleStorage getLeft,
                        @Cached GetTupleStorage getRight,
                        @Cached("createConcat()") ConcatNode concatNode,
                        @Cached PythonObjectFactory factory) {
            SequenceStorage concatenated = concatNode.execute(getLeft.execute(inliningTarget, left), getRight.execute(inliningTarget, right));
            return factory.createTuple(concatenated);
        }

        @NeverDefault
        protected static SequenceStorageNodes.ConcatNode createConcat() {
            return SequenceStorageNodes.ConcatNode.create(ListGeneralizationNode::create);
        }

        @Fallback
        static Object doGeneric(@SuppressWarnings("unused") Object left, Object right,
                        @Cached PRaiseNode raiseNode) {
            throw raiseNode.raise(TypeError, ErrorMessages.CAN_ONLY_CONCAT_S_NOT_P_TO_S, "tuple", right, "tuple");
        }
    }

    @Builtin(name = J___RMUL__, minNumOfPositionalArgs = 2)
    @Builtin(name = J___MUL__, minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    abstract static class MulNode extends PythonBinaryBuiltinNode {

        @Specialization
        static Object doTuple(VirtualFrame frame, Object left, Object right,
                        @Bind("this") Node inliningTarget,
                        @Cached PyTupleCheckExactNode checkTuple,
                        @Cached GetTupleStorage getLeft,
                        @Cached InlinedConditionProfile isSingleRepeat,
                        @Cached PyNumberAsSizeNode asSizeNode,
                        @Cached SequenceStorageNodes.RepeatNode repeatNode,
                        @Cached PythonObjectFactory.Lazy factory) {
            int repeats = asSizeNode.executeExact(frame, inliningTarget, right);
            if (isSingleRepeat.profile(inliningTarget, repeats == 1 && checkTuple.execute(inliningTarget, left))) {
                return left;
            } else {
                return factory.get(inliningTarget).createTuple(repeatNode.execute(frame, getLeft.execute(inliningTarget, left), repeats));
            }
        }
    }

    @Builtin(name = J___CONTAINS__, minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    abstract static class ContainsNode extends PythonBinaryBuiltinNode {
        @Specialization
        boolean contains(VirtualFrame frame, Object self, Object other,
                        @Bind("this") Node inliningTarget,
                        @Cached GetTupleStorage getTupleStorage,
                        @Cached SequenceStorageNodes.ContainsNode containsNode) {
            return containsNode.execute(frame, inliningTarget, getTupleStorage.execute(inliningTarget, self), other);
        }

    }

    @Builtin(name = J___ITER__, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class IterNode extends PythonUnaryBuiltinNode {
        @Specialization(guards = {"isIntStorage(primary)"})
        static PIntegerSequenceIterator doPTupleInt(PTuple primary,
                        @Shared @Cached PythonObjectFactory factory) {
            return factory.createIntegerSequenceIterator((IntSequenceStorage) primary.getSequenceStorage(), primary);
        }

        @Specialization(guards = {"isObjectStorage(primary)"})
        static PObjectSequenceIterator doPTupleObject(PTuple primary,
                        @Shared @Cached PythonObjectFactory factory) {
            return factory.createObjectSequenceIterator((ObjectSequenceStorage) primary.getSequenceStorage(), primary);
        }

        @Specialization(guards = {"isLongStorage(primary)"})
        static PLongSequenceIterator doPTupleLong(PTuple primary,
                        @Shared @Cached PythonObjectFactory factory) {
            return factory.createLongSequenceIterator((LongSequenceStorage) primary.getSequenceStorage(), primary);
        }

        @Specialization(guards = {"isDoubleStorage(primary)"})
        static PDoubleSequenceIterator doPTupleDouble(PTuple primary,
                        @Shared @Cached PythonObjectFactory factory) {
            return factory.createDoubleSequenceIterator((DoubleSequenceStorage) primary.getSequenceStorage(), primary);
        }

        @Specialization(guards = {"!isIntStorage(primary)", "!isLongStorage(primary)", "!isDoubleStorage(primary)"})
        static PSequenceIterator doPTuple(PTuple primary,
                        @Shared @Cached PythonObjectFactory factory) {
            return factory.createSequenceIterator(primary);
        }

        @Specialization
        static PSequenceIterator doNativeTuple(PythonAbstractNativeObject primary,
                        @Shared @Cached PythonObjectFactory factory) {
            return factory.createSequenceIterator(primary);
        }
    }

    @Builtin(name = J___HASH__, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class HashNode extends PythonUnaryBuiltinNode {
        protected static long HASH_UNSET = -1;

        @Specialization(guards = {"self.getHash() != HASH_UNSET"})
        long getHash(PTuple self) {
            return self.getHash();
        }

        @Specialization(guards = {"self.getHash() == HASH_UNSET"})
        long computeHash(VirtualFrame frame, PTuple self,
                        @Bind("this") Node inliningTarget,
                        @Shared("getItem") @Cached("createNotNormalized()") SequenceStorageNodes.GetItemNode getItemNode,
                        @Shared("hash") @Cached PyObjectHashNode hashNode) {
            // XXX CPython claims that caching the hash is not worth the space overhead:
            // https://bugs.python.org/issue9685
            long hash = doComputeHash(frame, inliningTarget, getItemNode, hashNode, self.getSequenceStorage());
            self.setHash(hash);
            return hash;
        }

        @Specialization
        long computeHash(VirtualFrame frame, PythonAbstractNativeObject self,
                        @Bind("this") Node inliningTarget,
                        @Shared("getItem") @Cached("createNotNormalized()") SequenceStorageNodes.GetItemNode getItemNode,
                        @Shared("hash") @Cached PyObjectHashNode hashNode,
                        @Cached GetNativeTupleStorage getStorage) {
            return doComputeHash(frame, inliningTarget, getItemNode, hashNode, getStorage.execute(self));
        }

        private static long doComputeHash(VirtualFrame frame, Node inliningTarget, SequenceStorageNodes.GetItemNode getItemNode, PyObjectHashNode hashNode, SequenceStorage tupleStore) {
            // adapted from https://github.com/python/cpython/blob/v3.6.5/Objects/tupleobject.c#L345
            int len = tupleStore.length();
            long multiplier = 0xf4243;
            long hash = 0x345678;
            for (int i = 0; i < len; i++) {
                Object item = getItemNode.execute(tupleStore, i);
                long tmp = hashNode.execute(frame, inliningTarget, item);
                hash = (hash ^ tmp) * multiplier;
                multiplier += 82520 + len + len;
            }

            hash += 97531;

            if (hash == Long.MAX_VALUE) {
                hash = -2;
            }
            return hash;
        }
    }

    @Builtin(name = J___GETNEWARGS__, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class GetNewargsNode extends PythonUnaryBuiltinNode {
        @Specialization
        static PTuple doIt(Object self,
                        @Bind("this") Node inliningTarget,
                        @Cached GetTupleStorage getTupleStorage,
                        @Cached PythonObjectFactory factory) {
            return factory.createTuple(new Object[]{factory.createTuple(getTupleStorage.execute(inliningTarget, self))});
        }
    }

    @Builtin(name = J___CLASS_GETITEM__, minNumOfPositionalArgs = 2, isClassmethod = true)
    @GenerateNodeFactory
    public abstract static class ClassGetItemNode extends PythonBinaryBuiltinNode {
        @Specialization
        static Object classGetItem(Object cls, Object key,
                        @Cached PythonObjectFactory factory) {
            return factory.createGenericAlias(cls, key);
        }
    }
}
