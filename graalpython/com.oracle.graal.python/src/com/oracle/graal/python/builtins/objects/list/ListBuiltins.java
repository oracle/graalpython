/*
 * Copyright (c) 2017, 2025, Oracle and/or its affiliates.
 * Copyright (c) 2013, Regents of the University of California
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
package com.oracle.graal.python.builtins.objects.list;

import static com.oracle.graal.python.nodes.BuiltinNames.J_APPEND;
import static com.oracle.graal.python.nodes.BuiltinNames.J_EXTEND;
import static com.oracle.graal.python.nodes.BuiltinNames.J_LIST;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J_SORT;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___CLASS_GETITEM__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___REVERSED__;
import static com.oracle.graal.python.nodes.StringLiterals.T_COMMA_SPACE;
import static com.oracle.graal.python.nodes.StringLiterals.T_ELLIPSIS_IN_BRACKETS;
import static com.oracle.graal.python.nodes.StringLiterals.T_EMPTY_BRACKETS;
import static com.oracle.graal.python.nodes.StringLiterals.T_LBRACKET;
import static com.oracle.graal.python.nodes.StringLiterals.T_RBRACKET;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.MemoryError;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.TypeError;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.ValueError;
import static com.oracle.graal.python.util.PythonUtils.TS_ENCODING;

import java.util.List;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.annotations.ArgumentClinic;
import com.oracle.graal.python.annotations.HashNotImplemented;
import com.oracle.graal.python.annotations.Slot;
import com.oracle.graal.python.annotations.Slot.SlotKind;
import com.oracle.graal.python.annotations.Slot.SlotSignature;
import com.oracle.graal.python.builtins.Builtin;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.Python3Core;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.PNotImplemented;
import com.oracle.graal.python.builtins.objects.common.IndexNodes.NormalizeIndexNode;
import com.oracle.graal.python.builtins.objects.common.SequenceStorageNodes;
import com.oracle.graal.python.builtins.objects.common.SequenceStorageNodes.CreateStorageFromIteratorNode;
import com.oracle.graal.python.builtins.objects.common.SequenceStorageNodes.ListGeneralizationNode;
import com.oracle.graal.python.builtins.objects.common.SequenceStorageNodes.SequenceStorageMpSubscriptNode;
import com.oracle.graal.python.builtins.objects.common.SequenceStorageNodes.SequenceStorageSqItemNode;
import com.oracle.graal.python.builtins.objects.common.SortNodes.SortSequenceStorageNode;
import com.oracle.graal.python.builtins.objects.function.PKeyword;
import com.oracle.graal.python.builtins.objects.iterator.IteratorNodes;
import com.oracle.graal.python.builtins.objects.iterator.PDoubleSequenceIterator;
import com.oracle.graal.python.builtins.objects.iterator.PIntegerSequenceIterator;
import com.oracle.graal.python.builtins.objects.iterator.PLongSequenceIterator;
import com.oracle.graal.python.builtins.objects.iterator.PSequenceIterator;
import com.oracle.graal.python.builtins.objects.range.PIntRange;
import com.oracle.graal.python.builtins.objects.str.PString;
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
import com.oracle.graal.python.lib.PyIndexCheckNode;
import com.oracle.graal.python.lib.PyListCheckNode;
import com.oracle.graal.python.lib.PyObjectGetIter;
import com.oracle.graal.python.lib.PyObjectReprAsTruffleStringNode;
import com.oracle.graal.python.lib.PyObjectRichCompareBool;
import com.oracle.graal.python.lib.RichCmpOp;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PGuards;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.builtins.ListNodes;
import com.oracle.graal.python.nodes.builtins.ListNodes.AppendNode;
import com.oracle.graal.python.nodes.builtins.ListNodes.ClearListStorageNode;
import com.oracle.graal.python.nodes.builtins.ListNodes.GetClassForNewListNode;
import com.oracle.graal.python.nodes.builtins.ListNodes.GetListStorageNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonBinaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonClinicBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonQuaternaryClinicBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonTernaryClinicBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonUnaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonVarargsBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.clinic.ArgumentClinicProvider;
import com.oracle.graal.python.nodes.util.CastToTruffleStringNode;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.graal.python.runtime.exception.PythonErrorType;
import com.oracle.graal.python.runtime.object.PFactory;
import com.oracle.graal.python.runtime.sequence.storage.DoubleSequenceStorage;
import com.oracle.graal.python.runtime.sequence.storage.EmptySequenceStorage;
import com.oracle.graal.python.runtime.sequence.storage.IntSequenceStorage;
import com.oracle.graal.python.runtime.sequence.storage.LongSequenceStorage;
import com.oracle.graal.python.runtime.sequence.storage.SequenceStorage;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.HostCompilerDirectives.InliningCutoff;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Exclusive;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.NeverDefault;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.nodes.LoopNode;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.profiles.InlinedBranchProfile;
import com.oracle.truffle.api.profiles.InlinedConditionProfile;
import com.oracle.truffle.api.profiles.InlinedLoopConditionProfile;
import com.oracle.truffle.api.strings.TruffleString;
import com.oracle.truffle.api.strings.TruffleStringBuilder;
import com.oracle.truffle.api.strings.TruffleStringIterator;

/**
 * NOTE: self can either be a PList or a foreign list (hasArrayElements()).
 * {@link GetListStorageNode} should be used instead of
 * {@link com.oracle.graal.python.builtins.objects.common.SequenceNodes.GetSequenceStorageNode} to
 * get a proper error and not allow other sequences, just PList or foreign list.
 */
@CoreFunctions(extendClasses = PythonBuiltinClassType.PList)
@HashNotImplemented
public final class ListBuiltins extends PythonBuiltins {
    public static final TpSlots SLOTS = ListBuiltinsSlotsGen.SLOTS;

    @Override
    public void initialize(Python3Core core) {
        super.initialize(core);
    }

    @Override
    protected List<com.oracle.truffle.api.dsl.NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return ListBuiltinsFactory.getFactories();
    }

    @Slot(value = SlotKind.tp_new, isComplex = true)
    @SlotSignature(name = J_LIST, minNumOfPositionalArgs = 1, takesVarArgs = true, takesVarKeywordArgs = true)
    @GenerateNodeFactory
    public abstract static class ListNode extends PythonVarargsBuiltinNode {
        @Specialization(guards = "isBuiltinList(cls)")
        PList doBuiltin(@SuppressWarnings("unused") Object cls, @SuppressWarnings("unused") Object[] arguments, @SuppressWarnings("unused") PKeyword[] keywords,
                        @Bind PythonLanguage language) {
            return PFactory.createList(language);
        }

        @Fallback
        protected PList constructList(Object cls, @SuppressWarnings("unused") Object[] arguments, @SuppressWarnings("unused") PKeyword[] keywords,
                        @Bind PythonLanguage language,
                        @Cached TypeNodes.GetInstanceShape getInstanceShape) {
            return PFactory.createList(language, cls, getInstanceShape.execute(cls));
        }

        protected static boolean isBuiltinList(Object cls) {
            return cls == PythonBuiltinClassType.PList;
        }
    }

    @Slot(value = SlotKind.tp_repr, isComplex = true)
    @GenerateNodeFactory
    abstract static class ReprNode extends PythonUnaryBuiltinNode {

        @Specialization
        public TruffleString repr(VirtualFrame frame, Object self,
                        @Bind Node inliningTarget,
                        @Cached GetListStorageNode getStorageNode,
                        @CachedLibrary(limit = "2") InteropLibrary interopLib,
                        @Cached SequenceStorageNodes.GetItemNode getItem,
                        @Cached PyObjectReprAsTruffleStringNode reprNode,
                        @Cached TruffleStringBuilder.AppendStringNode appendStringNode,
                        @Cached TruffleStringBuilder.ToStringNode toStringNode) {
            SequenceStorage storage = getStorageNode.execute(inliningTarget, self);
            int length = storage.length();
            if (length == 0) {
                return T_EMPTY_BRACKETS;
            }
            Object reprIdentity = self;
            if (!PGuards.isAnyPythonObject(self)) {
                // The interop library dispatch initialization acts as branch profile. Hash codes
                // may clash, but in this case the only downside is that we print an ellipsis
                // instead of expanding more.
                if (interopLib.hasIdentity(self)) {
                    try {
                        reprIdentity = interopLib.identityHashCode(self);
                    } catch (UnsupportedMessageException e) {
                        throw CompilerDirectives.shouldNotReachHere(e);
                    }
                }
            }
            PythonContext context = PythonContext.get(this);
            if (!context.reprEnter(reprIdentity)) {
                return T_ELLIPSIS_IN_BRACKETS;
            }
            try {
                TruffleStringBuilder buf = TruffleStringBuilder.create(TS_ENCODING);
                appendStringNode.execute(buf, T_LBRACKET);
                boolean initial = true;
                for (int index = 0; index < length; index++) {
                    if (initial) {
                        initial = false;
                    } else {
                        appendStringNode.execute(buf, T_COMMA_SPACE);
                    }
                    Object value = getItem.execute(storage, index);
                    appendStringNode.execute(buf, reprNode.execute(frame, inliningTarget, value));
                }
                appendStringNode.execute(buf, T_RBRACKET);
                return toStringNode.execute(buf);
            } finally {
                context.reprLeave(reprIdentity);
            }
        }
    }

    @Slot(value = SlotKind.tp_init, isComplex = true)
    @SlotSignature(name = "list", minNumOfPositionalArgs = 1, maxNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    public abstract static class ListInitNode extends PythonBinaryBuiltinNode {

        @Specialization
        static PNone initTruffleString(PList list, TruffleString value,
                        @Bind Node inliningTarget,
                        @Shared @Cached ClearListStorageNode clearStorageNode,
                        @Shared("cpIt") @Cached TruffleString.CreateCodePointIteratorNode createCodePointIteratorNode,
                        @Shared("cpItNext") @Cached TruffleStringIterator.NextNode nextNode,
                        @Shared("fromCp") @Cached TruffleString.FromCodePointNode fromCodePointNode,
                        @Shared("appendNode") @Cached AppendNode appendNode) {
            clearStorageNode.execute(inliningTarget, list);
            TruffleStringIterator iterator = createCodePointIteratorNode.execute(value, TS_ENCODING);
            while (iterator.hasNext()) {
                // TODO: GR-37219: use SubstringNode with lazy=true?
                int cp = nextNode.execute(iterator, TS_ENCODING);
                appendNode.execute(list, fromCodePointNode.execute(cp, TS_ENCODING, true));
            }
            return PNone.NONE;
        }

        // @Exclusive to address warning
        @Specialization
        static PNone initPString(PList list, PString value,
                        @Bind Node inliningTarget,
                        @Cached CastToTruffleStringNode castStr,
                        @Exclusive @Cached ClearListStorageNode clearStorageNode,
                        @Shared("cpIt") @Cached TruffleString.CreateCodePointIteratorNode createCodePointIteratorNode,
                        @Shared("cpItNext") @Cached TruffleStringIterator.NextNode nextNode,
                        @Shared("fromCp") @Cached TruffleString.FromCodePointNode fromCodePointNode,
                        @Shared("appendNode") @Cached AppendNode appendNode) {
            return initTruffleString(list, castStr.execute(inliningTarget, value), inliningTarget, clearStorageNode, createCodePointIteratorNode, nextNode, fromCodePointNode, appendNode);
        }

        @Specialization(guards = "isNoValue(none)")
        static PNone init(Object list, PNone none,
                        @Bind Node inliningTarget,
                        @Shared @Cached ClearListStorageNode clearStorageNode) {
            clearStorageNode.execute(inliningTarget, list);
            return PNone.NONE;
        }

        @Specialization
        static PNone listRange(PList list, PIntRange range,
                        @Bind Node inliningTarget,
                        @Shared @Cached ClearListStorageNode clearStorageNode) {
            clearStorageNode.execute(inliningTarget, list);
            int start = range.getIntStart();
            int step = range.getIntStep();
            int len = range.getIntLength();
            int[] array = new int[len];
            int value = start;
            for (int i = 0; i < len; i++) {
                array[i] = value;
                value += step;
            }
            list.setSequenceStorage(new IntSequenceStorage(array));
            return PNone.NONE;
        }

        @Specialization(guards = {"!isNoValue(iterable)", "!isString(iterable)", "!isPIntRange(iterable)"})
        static PNone listIterable(VirtualFrame frame, PList list, Object iterable,
                        @Bind Node inliningTarget,
                        // exclusive for truffle-interpreted-performance
                        @Exclusive @Cached ClearListStorageNode clearStorageNode,
                        @Cached IteratorNodes.GetLength lenNode,
                        @Cached PyObjectGetIter getIter,
                        @Cached CreateStorageFromIteratorNode storageNode) {
            clearStorageNode.execute(inliningTarget, list);
            int len = lenNode.execute(frame, inliningTarget, iterable);
            Object iterObj = getIter.execute(frame, inliningTarget, iterable);
            list.setSequenceStorage(storageNode.execute(frame, iterObj, len));
            return PNone.NONE;
        }

        @Specialization(guards = {"!isList(list)", "!isNoValue(iterable)"})
        static PNone foreignListIterable(VirtualFrame frame, Object list, Object iterable,
                        @Bind Node inliningTarget,
                        @Shared @Cached ClearListStorageNode clearStorageNode,
                        @Cached ListExtendNode extendNode) {
            clearStorageNode.execute(inliningTarget, list);
            extendNode.execute(frame, list, iterable);
            return PNone.NONE;
        }

        protected static boolean isPIntRange(Object object) {
            return object instanceof PIntRange;
        }
    }

    @Slot(value = SlotKind.sq_item, isComplex = true)
    @GenerateNodeFactory
    public abstract static class ListSqItemNode extends SqItemBuiltinNode {
        @Specialization
        static Object doIt(Object self, int index,
                        @Bind Node inliningTarget,
                        @Cached GetListStorageNode getStorageNode,
                        @Cached SequenceStorageSqItemNode sqItemNode) {
            var sequenceStorage = getStorageNode.execute(inliningTarget, self);
            return sqItemNode.execute(inliningTarget, sequenceStorage, index, ErrorMessages.LIST_INDEX_OUT_OF_RANGE);
        }
    }

    @Slot(value = SlotKind.mp_subscript, isComplex = true)
    @GenerateNodeFactory
    public abstract static class GetItemNode extends MpSubscriptBuiltinNode {
        @Specialization
        static Object doIt(VirtualFrame frame, Object self, Object idx,
                        @Bind Node inliningTarget,
                        @Cached GetListStorageNode getStorageNode,
                        @Cached InlinedConditionProfile validProfile,
                        @Cached PyIndexCheckNode indexCheckNode,
                        @Cached PRaiseNode raiseNode,
                        @Cached SequenceStorageMpSubscriptNode subscriptNode) {
            if (!validProfile.profile(inliningTarget, SequenceStorageMpSubscriptNode.isValidIndex(inliningTarget, idx, indexCheckNode))) {
                raiseNonIntIndex(inliningTarget, raiseNode, idx);
            }
            var sequenceStorage = getStorageNode.execute(inliningTarget, self);
            return subscriptNode.execute(frame, inliningTarget, sequenceStorage, idx,
                            ErrorMessages.LIST_INDEX_OUT_OF_RANGE, PFactory::createList);
        }

        @InliningCutoff
        private static void raiseNonIntIndex(Node inliningTarget, PRaiseNode raiseNode, Object index) {
            raiseNode.raise(inliningTarget, PythonBuiltinClassType.TypeError, ErrorMessages.OBJ_INDEX_MUST_BE_INT_OR_SLICES, "list", index);
        }
    }

    @Slot(value = SlotKind.sq_ass_item, isComplex = true)
    @GenerateNodeFactory
    public abstract static class SetItemNode extends SqAssItemBuiltinNode {

        @Specialization(guards = "!isNoValue(value)")
        static void doInt(Object self, int index, Object value,
                        @Bind Node inliningTarget,
                        @Exclusive @Cached GetListStorageNode getStorageNode,
                        @Cached ListNodes.UpdateListStorageNode updateStorageNode,
                        @Cached("createForList()") SequenceStorageNodes.SetItemNode setItemNode) {
            var sequenceStorage = getStorageNode.execute(inliningTarget, self);
            var newStorage = setItemNode.execute(sequenceStorage, index, value);
            updateStorageNode.execute(inliningTarget, self, sequenceStorage, newStorage);
        }

        // @Exclusive for truffle-interpreted-performance
        @Specialization(guards = "isNoValue(value)")
        static void doGeneric(Object list, int index, @SuppressWarnings("unused") Object value,
                        @Bind Node inliningTarget,
                        @Exclusive @Cached GetListStorageNode getStorageNode,
                        @Cached NormalizeIndexNode normalizeIndexNode,
                        @Cached SequenceStorageNodes.DeleteItemNode deleteItemNode) {
            var sequenceStorage = getStorageNode.execute(inliningTarget, list);
            index = normalizeIndexNode.execute(index, sequenceStorage.length());
            deleteItemNode.execute(inliningTarget, sequenceStorage, index);
        }
    }

    @Slot(value = SlotKind.mp_ass_subscript, isComplex = true)
    @GenerateNodeFactory
    public abstract static class SetSubscriptNode extends MpAssSubscriptBuiltinNode {

        @Specialization(guards = "!isNoValue(value)")
        static void doIntSet(Object self, int index, Object value,
                        @Bind Node inliningTarget,
                        @Shared @Cached GetListStorageNode getStorageNode,
                        @Shared @Cached ListNodes.UpdateListStorageNode updateStorageNode,
                        @Shared("setItem") @Cached("createForList()") SequenceStorageNodes.SetItemNode setItemNode) {
            var sequenceStorage = getStorageNode.execute(inliningTarget, self);
            var newStorage = setItemNode.execute(sequenceStorage, index, value);
            updateStorageNode.execute(inliningTarget, self, sequenceStorage, newStorage);
        }

        @InliningCutoff
        @Specialization(guards = {"!isNoValue(value)", "isIndexOrSlice(this, indexCheckNode, key)"})
        static void doGenericSet(VirtualFrame frame, Object self, Object key, Object value,
                        @Bind Node inliningTarget,
                        @Shared @Cached GetListStorageNode getStorageNode,
                        @Shared @Cached ListNodes.UpdateListStorageNode updateStorageNode,
                        @Shared("indexCheckNode") @SuppressWarnings("unused") @Cached PyIndexCheckNode indexCheckNode,
                        @Shared("setItem") @Cached("createForList()") SequenceStorageNodes.SetItemNode setItemNode) {
            var sequenceStorage = getStorageNode.execute(inliningTarget, self);
            var newStorage = setItemNode.execute(frame, sequenceStorage, key, value);
            updateStorageNode.execute(inliningTarget, self, sequenceStorage, newStorage);
        }

        @Specialization(guards = {"isNoValue(value)", "isIndexOrSlice(this, indexCheckNode, key)"})
        static void doGenericDel(VirtualFrame frame, Object list, Object key, @SuppressWarnings("unused") Object value,
                        @Bind Node inliningTarget,
                        @Shared("indexCheckNode") @SuppressWarnings("unused") @Cached PyIndexCheckNode indexCheckNode,
                        @Shared @Cached GetListStorageNode getStorageNode,
                        @Cached SequenceStorageNodes.DeleteNode deleteNode) {
            var sequenceStorage = getStorageNode.execute(inliningTarget, list);
            deleteNode.execute(frame, sequenceStorage, key);
        }

        @InliningCutoff
        @SuppressWarnings("unused")
        @Specialization(guards = "!isIndexOrSlice(this, indexCheckNode, key)")
        static void doError(Object self, Object key, Object value,
                        @Shared("indexCheckNode") @SuppressWarnings("unused") @Cached PyIndexCheckNode indexCheckNode,
                        @Bind Node inliningTarget) {
            throw PRaiseNode.raiseStatic(inliningTarget, TypeError, ErrorMessages.OBJ_INDEX_MUST_BE_INT_OR_SLICES, "list", key);
        }

        @NeverDefault
        protected static SetSubscriptNode create() {
            return ListBuiltinsFactory.SetSubscriptNodeFactory.create();
        }
    }

    // list.append(object)
    @Builtin(name = J_APPEND, minNumOfPositionalArgs = 2, numOfPositionalOnlyArgs = 2, parameterNames = {"$self", "object"})
    @GenerateNodeFactory
    public abstract static class ListAppendNode extends PythonBinaryBuiltinNode {

        @Specialization
        public PNone appendObjectGeneric(Object list, Object arg,
                        @Cached ListNodes.AppendNode appendNode) {
            appendNode.execute(list, arg);
            return PNone.NONE;
        }
    }

    // list.extend(L)
    @Builtin(name = J_EXTEND, minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    public abstract static class ListExtendNode extends PythonBinaryBuiltinNode {

        public abstract PNone execute(VirtualFrame frame, Object list, Object source);

        @Specialization
        public static PNone extendSequence(VirtualFrame frame, Object list, Object iterable,
                        @Bind Node inliningTarget,
                        @Cached GetListStorageNode getStorageNode,
                        @Cached ListNodes.UpdateListStorageNode updateStorageNode,
                        @Cached IteratorNodes.GetLength lenNode,
                        @Cached("createExtend()") SequenceStorageNodes.ExtendNode extendNode) {
            var sequenceStorage = getStorageNode.execute(inliningTarget, list);
            int len = lenNode.execute(frame, inliningTarget, iterable);
            var newStorage = extendNode.execute(frame, sequenceStorage, iterable, len);
            updateStorageNode.execute(inliningTarget, list, sequenceStorage, newStorage);
            return PNone.NONE;
        }

        @NeverDefault
        protected static SequenceStorageNodes.ExtendNode createExtend() {
            return SequenceStorageNodes.ExtendNode.create(ListGeneralizationNode.SUPPLIER);
        }

        @NeverDefault
        public static ListExtendNode create() {
            return ListBuiltinsFactory.ListExtendNodeFactory.create();
        }
    }

    // list.copy()
    @Builtin(name = "copy", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class ListCopyNode extends PythonUnaryBuiltinNode {

        @Specialization
        static PList copySequence(Object list,
                        @Bind Node inliningTarget,
                        @Cached GetListStorageNode getStorageNode,
                        @Cached SequenceStorageNodes.CopyNode copy,
                        @Cached GetClassForNewListNode getClassForNewListNode,
                        @Bind PythonLanguage language,
                        @Cached TypeNodes.GetInstanceShape getInstanceShape) {
            var sequenceStorage = getStorageNode.execute(inliningTarget, list);
            Object newClass = getClassForNewListNode.execute(inliningTarget, list);
            return PFactory.createList(language, newClass, getInstanceShape.execute(newClass), copy.execute(inliningTarget, sequenceStorage));
        }

    }

    // list.insert(i, x)
    @Builtin(name = "insert", minNumOfPositionalArgs = 3, numOfPositionalOnlyArgs = 3, parameterNames = {"$self", "index", "object"})
    @ArgumentClinic(name = "index", conversion = ArgumentClinic.ClinicConversion.Index)
    @GenerateNodeFactory
    public abstract static class ListInsertNode extends PythonTernaryClinicBuiltinNode {

        @Specialization
        static PNone insert(Object list, int index, Object value,
                        @Bind Node inliningTarget,
                        @Cached GetListStorageNode getStorageNode,
                        @Cached InlinedBranchProfile negativeProfile,
                        @Cached InlinedBranchProfile tooFarProfile,
                        @Cached ListNodes.UpdateListStorageNode updateStorageNode,
                        @Cached SequenceStorageNodes.InsertItemNode insertItem) {
            SequenceStorage store = getStorageNode.execute(inliningTarget, list);
            int len = store.length();
            if (index < 0) {
                negativeProfile.enter(inliningTarget);
                index += len;
                if (index < 0) {
                    index = 0;
                }
            }
            if (index > len) {
                tooFarProfile.enter(inliningTarget);
                index = len;
            }
            SequenceStorage newStorage = insertItem.execute(inliningTarget, store, index, value);
            updateStorageNode.execute(inliningTarget, list, store, newStorage);
            return PNone.NONE;
        }

        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return ListBuiltinsClinicProviders.ListInsertNodeClinicProviderGen.INSTANCE;
        }
    }

    // list.remove(x)
    @Builtin(name = "remove", minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    public abstract static class ListRemoveNode extends PythonBinaryBuiltinNode {

        @Specialization
        static PNone remove(VirtualFrame frame, Object list, Object value,
                        @Bind Node inliningTarget,
                        @Cached GetListStorageNode getStorageNode,
                        @Cached("createNotNormalized()") SequenceStorageNodes.GetItemNode getItemNode,
                        @Cached InlinedLoopConditionProfile loopProfile,
                        @Cached SequenceStorageNodes.DeleteNode deleteNode,
                        @Cached PyObjectRichCompareBool eqNode,
                        @Cached PRaiseNode raiseNode) {
            SequenceStorage listStore = getStorageNode.execute(inliningTarget, list);
            int len = listStore.length();
            loopProfile.profileCounted(inliningTarget, len);
            for (int i = 0; i < len; i++) {
                Object object = getItemNode.execute(listStore, i);
                if (eqNode.execute(frame, inliningTarget, object, value, RichCmpOp.Py_EQ)) {
                    deleteNode.execute(frame, listStore, i);
                    LoopNode.reportLoopCount(inliningTarget, i);
                    return PNone.NONE;
                }
            }
            LoopNode.reportLoopCount(inliningTarget, len);
            throw raiseNode.raise(inliningTarget, PythonErrorType.ValueError, ErrorMessages.NOT_IN_LIST_MESSAGE);
        }
    }

    // list.pop([i])
    @Builtin(name = "pop", minNumOfPositionalArgs = 1, maxNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    public abstract static class ListPopNode extends PythonBinaryBuiltinNode {

        @Specialization
        static Object popLast(VirtualFrame frame, Object list, @SuppressWarnings("unused") PNone none,
                        @Bind Node inliningTarget,
                        @Shared @Cached GetListStorageNode getStorageNode,
                        @Shared @Cached("createDelete()") SequenceStorageNodes.DeleteNode deleteNode,
                        @Shared @Cached SequenceStorageNodes.GetItemNode getItemNode) {
            SequenceStorage store = getStorageNode.execute(inliningTarget, list);
            Object ret = getItemNode.execute(store, -1);
            deleteNode.execute(frame, store, -1);
            return ret;
        }

        @Specialization(guards = {"!isNoValue(idx)", "!isPSlice(idx)"})
        static Object doIndex(VirtualFrame frame, Object list, Object idx,
                        @Bind Node inliningTarget,
                        @Shared @Cached GetListStorageNode getStorageNode,
                        @Shared @Cached("createDelete()") SequenceStorageNodes.DeleteNode deleteNode,
                        @Shared @Cached SequenceStorageNodes.GetItemNode getItemNode) {
            SequenceStorage store = getStorageNode.execute(inliningTarget, list);
            Object ret = getItemNode.execute(frame, store, idx);
            deleteNode.execute(frame, store, idx);
            return ret;
        }

        @Fallback
        static Object doError(@SuppressWarnings("unused") Object list, Object arg,
                        @Bind Node inliningTarget) {
            throw PRaiseNode.raiseStatic(inliningTarget, TypeError, ErrorMessages.OBJ_CANNOT_BE_INTERPRETED_AS_INTEGER, arg);
        }

        @NeverDefault
        protected static SequenceStorageNodes.DeleteNode createDelete() {
            return SequenceStorageNodes.DeleteNode.create(createNormalize());
        }

        @NeverDefault
        private static NormalizeIndexNode createNormalize() {
            return NormalizeIndexNode.create(ErrorMessages.POP_INDEX_OUT_OF_RANGE);
        }
    }

    // list.index(x)
    @Builtin(name = "index", minNumOfPositionalArgs = 2, numOfPositionalOnlyArgs = 4, parameterNames = {"$self", "value", "start", "stop"})
    @ArgumentClinic(name = "start", conversion = ArgumentClinic.ClinicConversion.SliceIndex, defaultValue = "0")
    @ArgumentClinic(name = "stop", conversion = ArgumentClinic.ClinicConversion.SliceIndex, defaultValue = "Integer.MAX_VALUE")
    @GenerateNodeFactory
    public abstract static class ListIndexNode extends PythonQuaternaryClinicBuiltinNode {

        @Specialization
        static Object index(VirtualFrame frame, Object self, Object value, int start, int stop,
                        @Bind Node inliningTarget,
                        @Cached GetListStorageNode getListStorageNode,
                        @Cached InlinedBranchProfile startAdjust,
                        @Cached InlinedBranchProfile stopAdjust,
                        @Cached SequenceStorageNodes.ItemIndexNode indexNode,
                        @Cached PRaiseNode raiseNode) {
            SequenceStorage s = getListStorageNode.execute(inliningTarget, self);
            start = adjustIndex(inliningTarget, s, start, startAdjust);
            stop = adjustIndex(inliningTarget, s, stop, stopAdjust);
            int idx = indexNode.execute(frame, inliningTarget, s, value, start, stop);
            if (idx != -1) {
                return idx;
            }
            throw raiseNode.raise(inliningTarget, PythonErrorType.ValueError, ErrorMessages.X_NOT_IN_LIST);
        }

        private static int adjustIndex(Node inliningTarget, SequenceStorage s, int index, InlinedBranchProfile profile) {
            if (index < 0) {
                profile.enter(inliningTarget);
                index += s.length();
                if (index < 0) {
                    return 0;
                }
            }
            return index;
        }

        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return ListBuiltinsClinicProviders.ListIndexNodeClinicProviderGen.INSTANCE;
        }
    }

    // list.count(x)
    @Builtin(name = "count", minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    public abstract static class ListCountNode extends PythonBuiltinNode {

        @Specialization
        long count(VirtualFrame frame, Object list, Object value,
                        @Bind Node inliningTarget,
                        @Cached InlinedLoopConditionProfile loopProfile,
                        @Cached GetListStorageNode getStorageNode,
                        @Cached("createNotNormalized()") SequenceStorageNodes.GetItemNode getItemNode,
                        @Cached PyObjectRichCompareBool eqNode) {
            long count = 0;
            SequenceStorage s = getStorageNode.execute(inliningTarget, list);
            loopProfile.profileCounted(inliningTarget, s.length());
            for (int i = 0; loopProfile.inject(inliningTarget, i < s.length()); i++) {
                Object seqItem = getItemNode.execute(s, i);
                if (eqNode.execute(frame, inliningTarget, seqItem, value, RichCmpOp.Py_EQ)) {
                    LoopNode.reportLoopCount(inliningTarget, i);
                    count++;
                }
            }
            LoopNode.reportLoopCount(inliningTarget, s.length());
            return count;
        }

    }

    // list.clear()
    @Builtin(name = "clear", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class ListClearNode extends PythonUnaryBuiltinNode {

        @Specialization
        static PNone clear(Object list,
                        @Bind Node inliningTarget,
                        @Cached ClearListStorageNode clearSequenceNode) {
            clearSequenceNode.execute(inliningTarget, list);
            return PNone.NONE;
        }

    }

    // list.reverse()
    @Builtin(name = "reverse", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class ListReverseNode extends PythonUnaryBuiltinNode {

        @Specialization
        static PNone reverse(Object list,
                        @Bind Node inliningTarget,
                        @Cached GetListStorageNode getStorageNode,
                        @Cached SequenceStorageNodes.ReverseNode reverseNode) {
            var sequenceStorage = getStorageNode.execute(inliningTarget, list);
            reverseNode.execute(inliningTarget, sequenceStorage);
            return PNone.NONE;
        }

    }

    // list.sort(key=None, reverse=False)
    @Builtin(name = J_SORT, minNumOfPositionalArgs = 1, parameterNames = {"$self"}, keywordOnlyNames = {"key", "reverse"})
    @ArgumentClinic(name = "reverse", conversion = ArgumentClinic.ClinicConversion.IntToBoolean, defaultValue = "false")
    @GenerateNodeFactory
    public abstract static class ListSortNode extends PythonClinicBuiltinNode {
        public final Object execute(VirtualFrame frame, Object list) {
            return execute(frame, list, PNone.NO_VALUE, false);
        }

        public abstract Object execute(VirtualFrame frame, Object list, Object keyfunc, boolean reverse);

        @Specialization
        static Object doPList(VirtualFrame frame, PList list, Object keyfunc, boolean reverse,
                        @Bind Node inliningTarget,
                        @Shared @Cached SortSequenceStorageNode sortSequenceStorageNode,
                        @Cached PRaiseNode raiseNode) {
            SequenceStorage storage = list.getSequenceStorage();
            // Make the list temporarily empty to prevent concurrent modification
            list.setSequenceStorage(EmptySequenceStorage.INSTANCE);
            try {
                sortSequenceStorageNode.execute(frame, storage, keyfunc, reverse);
                if (list.getSequenceStorage() != EmptySequenceStorage.INSTANCE) {
                    throw raiseNode.raise(inliningTarget, ValueError, ErrorMessages.LIST_MODIFIED_DURING_SORT);
                }
            } finally {
                list.setSequenceStorage(storage);
            }
            return PNone.NONE;
        }

        @Specialization(guards = "!isList(list)")
        static Object doForeign(VirtualFrame frame, Object list, Object keyfunc, boolean reverse,
                        @Bind Node inliningTarget,
                        @Cached GetListStorageNode getStorageNode,
                        @Shared @Cached SortSequenceStorageNode sortSequenceStorageNode) {
            SequenceStorage storage = getStorageNode.execute(inliningTarget, list);
            sortSequenceStorageNode.execute(frame, storage, keyfunc, reverse);
            return PNone.NONE;
        }

        @NeverDefault
        public static ListSortNode create() {
            return ListBuiltinsFactory.ListSortNodeFactory.create(null);
        }

        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return ListBuiltinsClinicProviders.ListSortNodeClinicProviderGen.INSTANCE;
        }
    }

    @Slot(SlotKind.sq_length)
    @Slot(SlotKind.mp_length)
    @GenerateUncached
    @GenerateNodeFactory
    public abstract static class LenNode extends LenBuiltinNode {

        @Specialization
        int doGeneric(Object list,
                        @Bind Node inliningTarget,
                        @Cached GetListStorageNode getStorageNode) {
            return getStorageNode.execute(inliningTarget, list).length();
        }
    }

    @Slot(value = SlotKind.sq_concat, isComplex = true)
    @GenerateNodeFactory
    abstract static class ConcatNode extends SqConcatBuiltinNode {
        @Specialization
        static PList doPList(Object left, Object right,
                        @Bind Node inliningTarget,
                        @Cached PyListCheckNode isListNode,
                        @Cached GetListStorageNode getStorageNode,
                        @Cached GetClassForNewListNode getClassForNewListNode,
                        @Cached SequenceStorageNodes.ConcatListOrTupleNode concatNode,
                        @Bind PythonLanguage language,
                        @Cached TypeNodes.GetInstanceShape getInstanceShape,
                        @Cached PRaiseNode raiseNode) {
            if (!isListNode.execute(inliningTarget, right)) {
                throw raiseNode.raise(inliningTarget, TypeError, ErrorMessages.CAN_ONLY_CONCAT_S_NOT_P_TO_S, "list", right, "list");
            }

            var leftStorage = getStorageNode.execute(inliningTarget, left);
            var rightStorage = getStorageNode.execute(inliningTarget, right);
            SequenceStorage newStore = concatNode.execute(inliningTarget, leftStorage, rightStorage);
            Object newClass = getClassForNewListNode.execute(inliningTarget, left);
            return PFactory.createList(language, newClass, getInstanceShape.execute(newClass), newStore);
        }
    }

    @Slot(value = SlotKind.sq_inplace_concat, isComplex = true)
    @GenerateNodeFactory
    abstract static class IAddNode extends PythonBinaryBuiltinNode {
        @Specialization
        Object extendSequence(VirtualFrame frame, Object list, Object iterable,
                        @Bind Node inliningTarget,
                        @Cached GetListStorageNode getStorageNode,
                        @Cached ListNodes.UpdateListStorageNode updateStorageNode,
                        @Cached IteratorNodes.GetLength lenNode,
                        @Cached("createExtend()") SequenceStorageNodes.ExtendNode extendNode) {
            var sequenceStorage = getStorageNode.execute(inliningTarget, list);
            int len = lenNode.execute(frame, inliningTarget, iterable);
            var newStorage = extendNode.execute(frame, sequenceStorage, iterable, len);
            updateStorageNode.execute(inliningTarget, list, sequenceStorage, newStorage);
            return list;
        }

        @NeverDefault
        protected static SequenceStorageNodes.ExtendNode createExtend() {
            return SequenceStorageNodes.ExtendNode.create(ListGeneralizationNode.SUPPLIER);
        }
    }

    @Slot(value = SlotKind.sq_repeat, isComplex = true)
    @GenerateNodeFactory
    abstract static class MulNode extends SqRepeatBuiltinNode {
        @Specialization
        static Object doPListInt(VirtualFrame frame, Object left, int right,
                        @Bind Node inliningTarget,
                        @Cached PyListCheckNode isListNode,
                        @Cached GetListStorageNode getStorageNode,
                        @Cached SequenceStorageNodes.RepeatNode repeatNode,
                        @Bind PythonLanguage language,
                        @Cached PRaiseNode raiseNode) {
            if (!isListNode.execute(inliningTarget, left)) {
                return PNotImplemented.NOT_IMPLEMENTED;
            }

            var sequenceStorage = getStorageNode.execute(inliningTarget, left);
            try {
                SequenceStorage repeated = repeatNode.execute(frame, sequenceStorage, right);
                return PFactory.createList(language, repeated);
            } catch (ArithmeticException | OutOfMemoryError e) {
                throw raiseNode.raise(inliningTarget, MemoryError);
            }
        }
    }

    @Slot(value = SlotKind.sq_inplace_repeat, isComplex = true)
    @GenerateNodeFactory
    abstract static class IMulNode extends SqRepeatBuiltinNode {
        @Specialization
        static Object doGeneric(VirtualFrame frame, Object list, int right,
                        @Bind Node inliningTarget,
                        @Cached GetListStorageNode getStorageNode,
                        @Cached ListNodes.UpdateListStorageNode updateStorageNode,
                        @Cached SequenceStorageNodes.RepeatNode repeatNode) {
            SequenceStorage store = getStorageNode.execute(inliningTarget, list);
            SequenceStorage updated = repeatNode.execute(frame, store, right);
            updateStorageNode.execute(inliningTarget, list, store, updated);
            return list;
        }
    }

    @Slot(value = SlotKind.tp_richcompare, isComplex = true)
    @GenerateNodeFactory
    abstract static class ListRichCmpNode extends TpSlotRichCompare.RichCmpBuiltinNode {
        @Specialization
        static Object doIt(VirtualFrame frame, Object left, Object right, RichCmpOp op,
                        @Bind Node inliningTarget,
                        @Cached PyListCheckNode isLeftList,
                        @Cached PyListCheckNode isRightList,
                        @Cached InlinedConditionProfile listCheckProfile,
                        @Cached SequenceStorageNodes.CmpNode cmpNode,
                        @Cached GetListStorageNode getStorageNode) {
            if (listCheckProfile.profile(inliningTarget, isLeftList.execute(inliningTarget, left) && isRightList.execute(inliningTarget, right))) {
                var leftStorage = getStorageNode.execute(inliningTarget, left);
                var rightStorage = getStorageNode.execute(inliningTarget, right);
                return cmpNode.execute(frame, inliningTarget, leftStorage, rightStorage, true, left, right, op);
            } else {
                return PNotImplemented.NOT_IMPLEMENTED;
            }
        }
    }

    @Slot(value = SlotKind.sq_contains, isComplex = true)
    @GenerateNodeFactory
    abstract static class ContainsNode extends SqContainsBuiltinNode {
        @Specialization
        boolean contains(VirtualFrame frame, Object self, Object other,
                        @Bind Node inliningTarget,
                        @Cached GetListStorageNode getStorage,
                        @Cached SequenceStorageNodes.ContainsNode containsNode) {
            return containsNode.execute(frame, inliningTarget, getStorage.execute(inliningTarget, self), other);
        }
    }

    @Slot(value = SlotKind.tp_iter, isComplex = true)
    @GenerateNodeFactory
    public abstract static class IterNode extends PythonUnaryBuiltinNode {

        /*
         * Don't create PObjectSequenceIterators here - otherwise list.clear will not reflect in the
         * iterator.
         */
        @Specialization(guards = {"isIntStorage(primary)"})
        static PIntegerSequenceIterator doPListInt(PList primary,
                        @Bind PythonLanguage language) {
            return PFactory.createIntegerSequenceIterator(language, (IntSequenceStorage) primary.getSequenceStorage(), primary);
        }

        @Specialization(guards = {"isLongStorage(primary)"})
        static PLongSequenceIterator doPListLong(PList primary,
                        @Bind PythonLanguage language) {
            return PFactory.createLongSequenceIterator(language, (LongSequenceStorage) primary.getSequenceStorage(), primary);
        }

        @Specialization(guards = {"isDoubleStorage(primary)"})
        static PDoubleSequenceIterator doPListDouble(PList primary,
                        @Bind PythonLanguage language) {
            return PFactory.createDoubleSequenceIterator(language, (DoubleSequenceStorage) primary.getSequenceStorage(), primary);
        }

        @Fallback
        static PSequenceIterator doOther(Object primary,
                        @Bind PythonLanguage language) {
            return PFactory.createSequenceIterator(language, primary);
        }
    }

    @Builtin(name = J___REVERSED__, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class ReverseNode extends PythonUnaryBuiltinNode {
        @Specialization
        static Object reverse(Object self,
                        @Bind Node inliningTarget,
                        @Cached GetListStorageNode getStorage,
                        @Bind PythonLanguage language) {
            int len = getStorage.execute(inliningTarget, self).length();
            return PFactory.createSequenceReverseIterator(language, self, len);
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
