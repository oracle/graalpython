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
package com.oracle.graal.python.builtins.objects.tuple;

import static com.oracle.graal.python.nodes.BuiltinNames.J_TUPLE;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___CLASS_GETITEM__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___GETNEWARGS__;
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

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.annotations.ArgumentClinic;
import com.oracle.graal.python.annotations.Slot;
import com.oracle.graal.python.annotations.Slot.SlotKind;
import com.oracle.graal.python.annotations.Slot.SlotSignature;
import com.oracle.graal.python.builtins.Builtin;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.objects.PNotImplemented;
import com.oracle.graal.python.builtins.objects.cext.PythonAbstractNativeObject;
import com.oracle.graal.python.builtins.objects.cext.capi.CExtNodes;
import com.oracle.graal.python.builtins.objects.common.SequenceStorageNodes;
import com.oracle.graal.python.builtins.objects.common.SequenceStorageNodes.SequenceStorageMpSubscriptNode;
import com.oracle.graal.python.builtins.objects.iterator.PDoubleSequenceIterator;
import com.oracle.graal.python.builtins.objects.iterator.PIntegerSequenceIterator;
import com.oracle.graal.python.builtins.objects.iterator.PLongSequenceIterator;
import com.oracle.graal.python.builtins.objects.iterator.PObjectSequenceIterator;
import com.oracle.graal.python.builtins.objects.iterator.PSequenceIterator;
import com.oracle.graal.python.builtins.objects.tuple.TupleBuiltinsClinicProviders.IndexNodeClinicProviderGen;
import com.oracle.graal.python.builtins.objects.type.TpSlots;
import com.oracle.graal.python.builtins.objects.type.TypeNodes;
import com.oracle.graal.python.builtins.objects.type.slots.TpSlotBinaryFunc.MpSubscriptBuiltinNode;
import com.oracle.graal.python.builtins.objects.type.slots.TpSlotBinaryFunc.SqConcatBuiltinNode;
import com.oracle.graal.python.builtins.objects.type.slots.TpSlotHashFun.HashBuiltinNode;
import com.oracle.graal.python.builtins.objects.type.slots.TpSlotLen.LenBuiltinNode;
import com.oracle.graal.python.builtins.objects.type.slots.TpSlotRichCompare;
import com.oracle.graal.python.builtins.objects.type.slots.TpSlotSizeArgFun.SqItemBuiltinNode;
import com.oracle.graal.python.builtins.objects.type.slots.TpSlotSizeArgFun.SqRepeatBuiltinNode;
import com.oracle.graal.python.builtins.objects.type.slots.TpSlotSqContains.SqContainsBuiltinNode;
import com.oracle.graal.python.lib.PyIndexCheckNode;
import com.oracle.graal.python.lib.PyObjectHashNode;
import com.oracle.graal.python.lib.PyObjectReprAsTruffleStringNode;
import com.oracle.graal.python.lib.PyObjectRichCompareBool;
import com.oracle.graal.python.lib.PyTupleCheckExactNode;
import com.oracle.graal.python.lib.PyTupleCheckNode;
import com.oracle.graal.python.lib.PyTupleGetItem;
import com.oracle.graal.python.lib.PyTupleSizeNode;
import com.oracle.graal.python.lib.RichCmpOp;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.builtins.TupleNodes;
import com.oracle.graal.python.nodes.builtins.TupleNodes.GetNativeTupleStorage;
import com.oracle.graal.python.nodes.builtins.TupleNodes.GetTupleStorage;
import com.oracle.graal.python.nodes.classes.IsSubtypeNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.builtins.PythonBinaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonQuaternaryClinicBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonUnaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.clinic.ArgumentClinicProvider;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.graal.python.runtime.exception.PythonErrorType;
import com.oracle.graal.python.runtime.object.PFactory;
import com.oracle.graal.python.runtime.sequence.storage.DoubleSequenceStorage;
import com.oracle.graal.python.runtime.sequence.storage.IntSequenceStorage;
import com.oracle.graal.python.runtime.sequence.storage.LongSequenceStorage;
import com.oracle.graal.python.runtime.sequence.storage.ObjectSequenceStorage;
import com.oracle.graal.python.runtime.sequence.storage.SequenceStorage;
import com.oracle.truffle.api.HostCompilerDirectives.InliningCutoff;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
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

    // tuple([iterable])
    @Slot(value = SlotKind.tp_new, isComplex = true)
    @SlotSignature(name = J_TUPLE, minNumOfPositionalArgs = 1, maxNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    public abstract static class TupleNode extends PythonBinaryBuiltinNode {

        @Specialization(guards = "isBuiltinTupleType(cls)")
        static Object doBuiltin(VirtualFrame frame, @SuppressWarnings("unused") Object cls, Object iterable,
                        @Shared @Cached TupleNodes.ConstructTupleNode constructTupleNode) {
            return constructTupleNode.execute(frame, iterable);
        }

        @Specialization(guards = "!needsNativeAllocationNode.execute(inliningTarget, cls)", replaces = "doBuiltin")
        static PTuple constructTuple(VirtualFrame frame, Object cls, Object iterable,
                        @SuppressWarnings("unused") @Bind Node inliningTarget,
                        @SuppressWarnings("unused") @Shared @Cached TypeNodes.NeedsNativeAllocationNode needsNativeAllocationNode,
                        @Shared @Cached TupleNodes.ConstructTupleNode constructTupleNode,
                        @Cached TypeNodes.IsSameTypeNode isSameTypeNode,
                        @Bind PythonLanguage language,
                        @Cached TypeNodes.GetInstanceShape getInstanceShape) {
            PTuple tuple = constructTupleNode.execute(frame, iterable);
            if (isSameTypeNode.execute(inliningTarget, cls, PythonBuiltinClassType.PTuple)) {
                return tuple;
            } else {
                return PFactory.createTuple(language, cls, getInstanceShape.execute(cls), tuple.getSequenceStorage());
            }
        }

        // delegate to tuple_subtype_new(PyTypeObject *type, PyObject *x)
        @Specialization(guards = {"needsNativeAllocationNode.execute(inliningTarget, cls)", "isSubtypeOfTuple( isSubtype, cls)"}, limit = "1")
        @InliningCutoff
        static Object doNative(@SuppressWarnings("unused") VirtualFrame frame, Object cls, Object iterable,
                        @SuppressWarnings("unused") @Bind Node inliningTarget,
                        @SuppressWarnings("unused") @Shared @Cached TypeNodes.NeedsNativeAllocationNode needsNativeAllocationNode,
                        @Cached @SuppressWarnings("unused") IsSubtypeNode isSubtype,
                        @Cached CExtNodes.TupleSubtypeNew subtypeNew) {
            return subtypeNew.call(cls, iterable);
        }

        protected static boolean isBuiltinTupleType(Object cls) {
            return cls == PythonBuiltinClassType.PTuple;
        }

        protected static boolean isSubtypeOfTuple(IsSubtypeNode isSubtypeNode, Object cls) {
            return isSubtypeNode.execute(cls, PythonBuiltinClassType.PTuple);
        }

        @Fallback
        static PTuple tupleObject(Object cls, @SuppressWarnings("unused") Object arg,
                        @Bind Node inliningTarget) {
            throw PRaiseNode.raiseStatic(inliningTarget, TypeError, ErrorMessages.IS_NOT_TYPE_OBJ, "'cls'", cls);
        }
    }

    // index(element)
    @Builtin(name = "index", minNumOfPositionalArgs = 2, parameterNames = {"$self", "value", "start", "stop"})
    @ArgumentClinic(name = "start", conversion = ArgumentClinic.ClinicConversion.SliceIndex, defaultValue = "0")
    @ArgumentClinic(name = "stop", conversion = ArgumentClinic.ClinicConversion.SliceIndex, defaultValue = "Integer.MAX_VALUE")
    @GenerateNodeFactory
    public abstract static class IndexNode extends PythonQuaternaryClinicBuiltinNode {

        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return IndexNodeClinicProviderGen.INSTANCE;
        }

        @Specialization
        int index(VirtualFrame frame, Object self, Object value, int startIn, int endIn,
                        @Bind Node inliningTarget,
                        @Cached GetTupleStorage getTupleStorage,
                        @Cached InlinedBranchProfile startLe0Profile,
                        @Cached InlinedBranchProfile endLe0Profile,
                        @Cached SequenceStorageNodes.ItemIndexNode itemIndexNode,
                        @Cached PRaiseNode raiseNode) {
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
            throw raiseNode.raise(inliningTarget, PythonErrorType.ValueError, ErrorMessages.X_NOT_IN_TUPLE);
        }
    }

    @Builtin(name = "count", minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    public abstract static class CountNode extends PythonBinaryBuiltinNode {

        @Specialization
        static long count(VirtualFrame frame, Object self, Object value,
                        @Bind Node inliningTarget,
                        @Cached GetTupleStorage getTupleStorage,
                        @Cached("createNotNormalized()") SequenceStorageNodes.GetItemNode getItemNode,
                        @Cached PyObjectRichCompareBool eqNode) {
            long count = 0;
            SequenceStorage tupleStore = getTupleStorage.execute(inliningTarget, self);
            for (int i = 0; i < tupleStore.length(); i++) {
                Object seqItem = getItemNode.execute(tupleStore, i);
                if (eqNode.execute(frame, inliningTarget, seqItem, value, RichCmpOp.Py_EQ)) {
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
                        @Bind Node inliningTarget,
                        @Cached PyTupleSizeNode pyTupleSizeNode) {
            return pyTupleSizeNode.execute(inliningTarget, self);
        }
    }

    @Slot(value = SlotKind.tp_repr, isComplex = true)
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
                        @Bind Node inliningTarget,
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
                        @Bind Node inliningTarget,
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
                        @Bind Node inliningTarget,
                        @Cached InlinedConditionProfile validProfile,
                        @Cached PyIndexCheckNode indexCheckNode,
                        @Cached PRaiseNode raiseNode,
                        @Cached GetTupleStorage getTupleStorage,
                        @Cached SequenceStorageMpSubscriptNode subscriptNode) {
            if (!validProfile.profile(inliningTarget, SequenceStorageMpSubscriptNode.isValidIndex(inliningTarget, idx, indexCheckNode))) {
                raiseNonIntIndex(inliningTarget, raiseNode, idx);
            }
            return subscriptNode.execute(frame, inliningTarget, getTupleStorage.execute(inliningTarget, self), idx,
                            ErrorMessages.TUPLE_OUT_OF_BOUNDS, PFactory::createTuple);
        }

        @InliningCutoff
        private static void raiseNonIntIndex(Node inliningTarget, PRaiseNode raiseNode, Object index) {
            raiseNode.raise(inliningTarget, PythonBuiltinClassType.TypeError, ErrorMessages.OBJ_INDEX_MUST_BE_INT_OR_SLICES, "tuple", index);
        }
    }

    @Slot(value = SlotKind.tp_richcompare, isComplex = true)
    @GenerateNodeFactory
    abstract static class TupleRichCmpNode extends TpSlotRichCompare.RichCmpBuiltinNode {
        @Specialization
        static Object doTuple(VirtualFrame frame, Object left, Object right, RichCmpOp op,
                        @Bind Node inliningTarget,
                        @Cached PyTupleCheckNode checkLeft,
                        @Cached PyTupleCheckNode checkRight,
                        @Cached InlinedConditionProfile tupleCheckProfile,
                        @Cached GetTupleStorage getLeft,
                        @Cached GetTupleStorage getRight,
                        @Cached SequenceStorageNodes.CmpNode cmp) {
            if (tupleCheckProfile.profile(inliningTarget, !checkLeft.execute(inliningTarget, left) || !checkRight.execute(inliningTarget, right))) {
                return PNotImplemented.NOT_IMPLEMENTED;
            }
            return cmp.execute(frame, inliningTarget, getLeft.execute(inliningTarget, left), getRight.execute(inliningTarget, right), false, null, null, op);
        }
    }

    @Slot(value = SlotKind.sq_concat, isComplex = true)
    @GenerateNodeFactory
    abstract static class TupleConcatNode extends SqConcatBuiltinNode {

        @Specialization(guards = {"checkRight.execute(inliningTarget, right)"}, limit = "1")
        static PTuple doTuple(Object left, Object right,
                        @Bind Node inliningTarget,
                        @SuppressWarnings("unused") @Cached PyTupleCheckNode checkRight,
                        @Cached GetTupleStorage getLeft,
                        @Cached GetTupleStorage getRight,
                        @Cached SequenceStorageNodes.ConcatListOrTupleNode concatNode,
                        @Bind PythonLanguage language) {
            SequenceStorage leftStorage = getLeft.execute(inliningTarget, left);
            SequenceStorage rightStorage = getRight.execute(inliningTarget, right);
            SequenceStorage concatenated = concatNode.execute(inliningTarget, leftStorage, rightStorage);
            return PFactory.createTuple(language, concatenated);
        }

        @Fallback
        static Object doGeneric(@SuppressWarnings("unused") Object left, Object right,
                        @Bind Node inliningTarget) {
            throw PRaiseNode.raiseStatic(inliningTarget, TypeError, ErrorMessages.CAN_ONLY_CONCAT_S_NOT_P_TO_S, "tuple", right, "tuple");
        }
    }

    @Slot(value = SlotKind.sq_repeat, isComplex = true)
    @GenerateNodeFactory
    abstract static class MulNode extends SqRepeatBuiltinNode {

        @Specialization
        static Object doTuple(VirtualFrame frame, Object left, int repeats,
                        @Bind Node inliningTarget,
                        @Cached PyTupleCheckExactNode checkTuple,
                        @Cached GetTupleStorage getLeft,
                        @Cached InlinedConditionProfile isSingleRepeat,
                        @Cached SequenceStorageNodes.RepeatNode repeatNode) {
            if (isSingleRepeat.profile(inliningTarget, repeats == 1 && checkTuple.execute(inliningTarget, left))) {
                return left;
            } else {
                return PFactory.createTuple(PythonLanguage.get(inliningTarget), repeatNode.execute(frame, getLeft.execute(inliningTarget, left), repeats));
            }
        }
    }

    @Slot(value = SlotKind.sq_contains, isComplex = true)
    @GenerateNodeFactory
    abstract static class ContainsNode extends SqContainsBuiltinNode {
        @Specialization
        boolean contains(VirtualFrame frame, Object self, Object other,
                        @Bind Node inliningTarget,
                        @Cached GetTupleStorage getTupleStorage,
                        @Cached SequenceStorageNodes.ContainsNode containsNode) {
            return containsNode.execute(frame, inliningTarget, getTupleStorage.execute(inliningTarget, self), other);
        }
    }

    @Slot(value = SlotKind.tp_iter, isComplex = true)
    @GenerateNodeFactory
    public abstract static class IterNode extends PythonUnaryBuiltinNode {
        @Specialization(guards = {"isIntStorage(primary)"})
        static PIntegerSequenceIterator doPTupleInt(PTuple primary,
                        @Bind PythonLanguage language) {
            return PFactory.createIntegerSequenceIterator(language, (IntSequenceStorage) primary.getSequenceStorage(), primary);
        }

        @Specialization(guards = {"isObjectStorage(primary)"})
        static PObjectSequenceIterator doPTupleObject(PTuple primary,
                        @Bind PythonLanguage language) {
            return PFactory.createObjectSequenceIterator(language, (ObjectSequenceStorage) primary.getSequenceStorage(), primary);
        }

        @Specialization(guards = {"isLongStorage(primary)"})
        static PLongSequenceIterator doPTupleLong(PTuple primary,
                        @Bind PythonLanguage language) {
            return PFactory.createLongSequenceIterator(language, (LongSequenceStorage) primary.getSequenceStorage(), primary);
        }

        @Specialization(guards = {"isDoubleStorage(primary)"})
        static PDoubleSequenceIterator doPTupleDouble(PTuple primary,
                        @Bind PythonLanguage language) {
            return PFactory.createDoubleSequenceIterator(language, (DoubleSequenceStorage) primary.getSequenceStorage(), primary);
        }

        @Specialization(guards = {"!isIntStorage(primary)", "!isLongStorage(primary)", "!isDoubleStorage(primary)"})
        static PSequenceIterator doPTuple(PTuple primary,
                        @Bind PythonLanguage language) {
            return PFactory.createSequenceIterator(language, primary);
        }

        @Specialization
        static PSequenceIterator doNativeTuple(PythonAbstractNativeObject primary,
                        @Bind PythonLanguage language) {
            return PFactory.createSequenceIterator(language, primary);
        }
    }

    @Slot(value = SlotKind.tp_hash, isComplex = true)
    @GenerateNodeFactory
    public abstract static class HashNode extends HashBuiltinNode {
        protected static long HASH_UNSET = -1;

        @Specialization(guards = {"self.getHash() != HASH_UNSET"})
        long getHash(PTuple self) {
            return self.getHash();
        }

        @Specialization(guards = {"self.getHash() == HASH_UNSET"})
        long computeHash(VirtualFrame frame, PTuple self,
                        @Bind Node inliningTarget,
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
                        @Bind Node inliningTarget,
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

            if (hash == -1) {
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
                        @Bind Node inliningTarget,
                        @Cached GetTupleStorage getTupleStorage,
                        @Bind PythonLanguage language) {
            return PFactory.createTuple(language, new Object[]{PFactory.createTuple(language, getTupleStorage.execute(inliningTarget, self))});
        }
    }

    @Builtin(name = J___CLASS_GETITEM__, minNumOfPositionalArgs = 2, isClassmethod = true)
    @GenerateNodeFactory
    public abstract static class ClassGetItemNode extends PythonBinaryBuiltinNode {
        @Specialization
        static Object classGetItem(Object cls, Object key,
                        @Bind PythonLanguage language) {
            return PFactory.createGenericAlias(language, cls, key);
        }
    }
}
