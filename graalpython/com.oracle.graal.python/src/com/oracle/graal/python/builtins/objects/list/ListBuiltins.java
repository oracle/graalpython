/*
 * Copyright (c) 2017, 2023, Oracle and/or its affiliates.
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
import static com.oracle.graal.python.nodes.SpecialAttributeNames.T___DOC__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J_SORT;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___ADD__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___CLASS_GETITEM__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___CONTAINS__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___DELITEM__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___EQ__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___GETITEM__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___GE__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___GT__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___IADD__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___IMUL__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___INIT__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___ITER__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___LEN__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___LE__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___LT__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___MUL__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___NE__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___REPR__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___REVERSED__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___RMUL__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___SETITEM__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___HASH__;
import static com.oracle.graal.python.nodes.StringLiterals.T_COMMA_SPACE;
import static com.oracle.graal.python.nodes.StringLiterals.T_ELLIPSIS_IN_BRACKETS;
import static com.oracle.graal.python.nodes.StringLiterals.T_EMPTY_BRACKETS;
import static com.oracle.graal.python.nodes.StringLiterals.T_LBRACKET;
import static com.oracle.graal.python.nodes.StringLiterals.T_RBRACKET;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.MemoryError;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.TypeError;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.ValueError;
import static com.oracle.graal.python.util.PythonUtils.TS_ENCODING;

import java.math.BigInteger;
import java.util.List;

import com.oracle.graal.python.annotations.ArgumentClinic;
import com.oracle.graal.python.builtins.Builtin;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.Python3Core;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.modules.MathGuards;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.PNotImplemented;
import com.oracle.graal.python.builtins.objects.common.IndexNodes.NormalizeIndexNode;
import com.oracle.graal.python.builtins.objects.common.SequenceNodes;
import com.oracle.graal.python.builtins.objects.common.SequenceStorageNodes;
import com.oracle.graal.python.builtins.objects.common.SequenceStorageNodes.CreateStorageFromIteratorNode;
import com.oracle.graal.python.builtins.objects.common.SequenceStorageNodes.ListGeneralizationNode;
import com.oracle.graal.python.builtins.objects.common.SortNodes.SortSequenceStorageNode;
import com.oracle.graal.python.builtins.objects.ints.PInt;
import com.oracle.graal.python.builtins.objects.iterator.IteratorNodes;
import com.oracle.graal.python.builtins.objects.iterator.PDoubleSequenceIterator;
import com.oracle.graal.python.builtins.objects.iterator.PIntegerSequenceIterator;
import com.oracle.graal.python.builtins.objects.iterator.PLongSequenceIterator;
import com.oracle.graal.python.builtins.objects.iterator.PSequenceIterator;
import com.oracle.graal.python.builtins.objects.range.PIntRange;
import com.oracle.graal.python.builtins.objects.str.PString;
import com.oracle.graal.python.builtins.objects.tuple.PTuple;
import com.oracle.graal.python.lib.PyIndexCheckNode;
import com.oracle.graal.python.lib.PyObjectGetIter;
import com.oracle.graal.python.lib.PyObjectReprAsTruffleStringNode;
import com.oracle.graal.python.lib.PyObjectRichCompareBool;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PGuards;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.builtins.ListNodes;
import com.oracle.graal.python.nodes.builtins.ListNodes.AppendNode;
import com.oracle.graal.python.nodes.builtins.ListNodes.IndexNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonBinaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonClinicBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonTernaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonUnaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.clinic.ArgumentClinicProvider;
import com.oracle.graal.python.nodes.object.GetClassNode;
import com.oracle.graal.python.nodes.truffle.PythonArithmeticTypes;
import com.oracle.graal.python.nodes.util.CastToTruffleStringNode;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.graal.python.runtime.exception.PythonErrorType;
import com.oracle.graal.python.runtime.object.PythonObjectFactory;
import com.oracle.graal.python.runtime.sequence.PSequence;
import com.oracle.graal.python.runtime.sequence.storage.DoubleSequenceStorage;
import com.oracle.graal.python.runtime.sequence.storage.EmptySequenceStorage;
import com.oracle.graal.python.runtime.sequence.storage.IntSequenceStorage;
import com.oracle.graal.python.runtime.sequence.storage.LongSequenceStorage;
import com.oracle.graal.python.runtime.sequence.storage.SequenceStorage;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.HostCompilerDirectives.InliningCutoff;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.NeverDefault;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.dsl.TypeSystemReference;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.profiles.InlinedConditionProfile;
import com.oracle.truffle.api.strings.TruffleString;
import com.oracle.truffle.api.strings.TruffleStringBuilder;
import com.oracle.truffle.api.strings.TruffleStringIterator;

@CoreFunctions(extendClasses = PythonBuiltinClassType.PList)
public final class ListBuiltins extends PythonBuiltins {

    @Override
    public void initialize(Python3Core core) {
        super.initialize(core);
        addBuiltinConstant(T___DOC__, //
                        "Built-in mutable sequence.\n" + //
                                        "\n" + //
                                        "If no argument is given, the constructor creates a new empty list.\n" + //
                                        "The argument must be an iterable if specified.");
        this.addBuiltinConstant(T___HASH__, PNone.NONE);
    }

    @Override
    protected List<com.oracle.truffle.api.dsl.NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return ListBuiltinsFactory.getFactories();
    }

    @Builtin(name = J___REPR__, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class ReprNode extends PythonUnaryBuiltinNode {

        @Specialization
        public TruffleString repr(VirtualFrame frame, PList self,
                        @Bind("this") Node inliningTarget,
                        @Cached SequenceStorageNodes.GetItemNode getItem,
                        @Cached PyObjectReprAsTruffleStringNode reprNode,
                        @Cached TruffleStringBuilder.AppendStringNode appendStringNode,
                        @Cached TruffleStringBuilder.ToStringNode toStringNode) {
            SequenceStorage storage = self.getSequenceStorage();
            int length = storage.length();
            if (length == 0) {
                return T_EMPTY_BRACKETS;
            }
            if (!PythonContext.get(this).reprEnter(self)) {
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
                PythonContext.get(this).reprLeave(self);
            }
        }
    }

    @Builtin(name = J___INIT__, minNumOfPositionalArgs = 1, maxNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    public abstract static class ListInitNode extends PythonBinaryBuiltinNode {

        public abstract PNone execute(VirtualFrame frame, PList list, Object source);

        @Specialization
        static PNone initTruffleString(PList list, TruffleString value,
                        @Shared("cpIt") @Cached TruffleString.CreateCodePointIteratorNode createCodePointIteratorNode,
                        @Shared("cpItNext") @Cached TruffleStringIterator.NextNode nextNode,
                        @Shared("fromCp") @Cached TruffleString.FromCodePointNode fromCodePointNode,
                        @Shared("appendNode") @Cached AppendNode appendNode) {
            clearStorage(list);
            TruffleStringIterator iterator = createCodePointIteratorNode.execute(value, TS_ENCODING);
            while (iterator.hasNext()) {
                // TODO: GR-37219: use SubstringNode with lazy=true?
                int cp = nextNode.execute(iterator);
                appendNode.execute(list, fromCodePointNode.execute(cp, TS_ENCODING, true));
            }
            return PNone.NONE;
        }

        @Specialization
        static PNone initPString(PList list, PString value,
                        @Bind("this") Node inliningTarget,
                        @Cached CastToTruffleStringNode castStr,
                        @Shared("cpIt") @Cached TruffleString.CreateCodePointIteratorNode createCodePointIteratorNode,
                        @Shared("cpItNext") @Cached TruffleStringIterator.NextNode nextNode,
                        @Shared("fromCp") @Cached TruffleString.FromCodePointNode fromCodePointNode,
                        @Shared("appendNode") @Cached AppendNode appendNode) {
            return initTruffleString(list, castStr.execute(inliningTarget, value), createCodePointIteratorNode, nextNode, fromCodePointNode, appendNode);
        }

        @Specialization(guards = "isNoValue(none)")
        static PNone init(PList list, @SuppressWarnings("unused") PNone none) {
            clearStorage(list);
            return PNone.NONE;
        }

        @Specialization
        static PNone listRange(PList list, PIntRange range) {
            clearStorage(list);
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

        @Specialization(guards = {"!isNoValue(iterable)", "!isString(iterable)"})
        static PNone listIterable(VirtualFrame frame, PList list, Object iterable,
                        @Bind("this") Node inliningTarget,
                        @Cached IteratorNodes.GetLength lenNode,
                        @Cached PyObjectGetIter getIter,
                        @Cached CreateStorageFromIteratorNode storageNode) {
            clearStorage(list);
            int len = lenNode.execute(frame, inliningTarget, iterable);
            Object iterObj = getIter.execute(frame, inliningTarget, iterable);
            list.setSequenceStorage(storageNode.execute(frame, iterObj, len));
            return PNone.NONE;
        }

        private static void clearStorage(PList list) {
            if (EmptySequenceStorage.INSTANCE != list.getSequenceStorage()) {
                list.setSequenceStorage(EmptySequenceStorage.INSTANCE);
            }
        }
    }

    @Builtin(name = J___DELITEM__, minNumOfPositionalArgs = 2)
    @TypeSystemReference(PythonArithmeticTypes.class)
    @GenerateNodeFactory
    public abstract static class DelItemNode extends PythonBinaryBuiltinNode {

        @Specialization
        protected Object doGeneric(VirtualFrame frame, PList self, Object key,
                        @Cached SequenceStorageNodes.DeleteNode deleteNode) {
            deleteNode.execute(frame, self.getSequenceStorage(), key);
            return PNone.NONE;
        }

        @Fallback
        protected Object doGeneric(Object self, @SuppressWarnings("unused") Object objectIdx) {
            throw raise(TypeError, ErrorMessages.DESCRIPTOR_S_REQUIRES_S_OBJ_RECEIVED_P, "__delitem__", "list", self);
        }
    }

    @Builtin(name = J___GETITEM__, minNumOfPositionalArgs = 2)
    @TypeSystemReference(PythonArithmeticTypes.class)
    @GenerateNodeFactory
    public abstract static class GetItemNode extends PythonBinaryBuiltinNode {

        @Specialization
        protected static Object doInBounds(PList self, int index,
                        @Shared("getItem") @Cached("createForList()") SequenceStorageNodes.GetItemNode getItemNode) {
            return getItemNode.execute(self.getSequenceStorage(), index);
        }

        @InliningCutoff
        @Specialization(guards = "isIndexOrSlice(this, indexCheckNode, key)")
        protected static Object doScalar(VirtualFrame frame, PList self, Object key,
                        @SuppressWarnings("unused") @Bind("this") Node inliningTarget,
                        @SuppressWarnings("unused") @Shared @Cached PyIndexCheckNode indexCheckNode,
                        @Shared("getItem") @Cached("createForList()") SequenceStorageNodes.GetItemNode getItemNode) {
            return getItemNode.execute(frame, self.getSequenceStorage(), key);
        }

        @InliningCutoff
        @SuppressWarnings({"unused", "truffle-static-method"})
        @Specialization(guards = "!isIndexOrSlice(this, indexCheckNode, key)")
        protected Object doListError(VirtualFrame frame, Object self, Object key,
                        @SuppressWarnings("unused") @Bind("this") Node inliningTarget,
                        @SuppressWarnings("unused") @Shared @Cached PyIndexCheckNode indexCheckNode) {
            throw raise(TypeError, ErrorMessages.OBJ_INDEX_MUST_BE_INT_OR_SLICES, "list", key);
        }

        @NeverDefault
        protected static GetItemNode create() {
            return ListBuiltinsFactory.GetItemNodeFactory.create();
        }
    }

    @Builtin(name = J___SETITEM__, minNumOfPositionalArgs = 3)
    @GenerateNodeFactory
    public abstract static class SetItemNode extends PythonTernaryBuiltinNode {

        @Specialization
        static Object doInt(PList self, int index, Object value,
                        @Bind("this") Node inliningTarget,
                        @Shared @Cached InlinedConditionProfile generalizedProfile,
                        @Shared("setItem") @Cached("createForList()") SequenceStorageNodes.SetItemNode setItemNode) {
            updateStorage(inliningTarget, self, setItemNode.execute(self.getSequenceStorage(), index, value), generalizedProfile);
            return PNone.NONE;
        }

        @InliningCutoff
        @Specialization(guards = "isIndexOrSlice(this, indexCheckNode, key)")
        static Object doGeneric(VirtualFrame frame, PList primary, Object key, Object value,
                        @Bind("this") Node inliningTarget,
                        @Shared @Cached InlinedConditionProfile generalizedProfile,
                        @Shared("indexCheckNode") @SuppressWarnings("unused") @Cached PyIndexCheckNode indexCheckNode,
                        @Shared("setItem") @Cached("createForList()") SequenceStorageNodes.SetItemNode setItemNode) {
            updateStorage(inliningTarget, primary, setItemNode.execute(frame, primary.getSequenceStorage(), key, value), generalizedProfile);
            return PNone.NONE;
        }

        @InliningCutoff
        @SuppressWarnings("unused")
        @Specialization(guards = "!isIndexOrSlice(this, indexCheckNode, key)")
        static Object doError(Object self, Object key, Object value,
                        @Shared("indexCheckNode") @SuppressWarnings("unused") @Cached PyIndexCheckNode indexCheckNode,
                        @Cached PRaiseNode raiseNode) {
            throw raiseNode.raise(TypeError, ErrorMessages.OBJ_INDEX_MUST_BE_INT_OR_SLICES, "list", key);
        }

        private static void updateStorage(Node inliningTarget, PList primary, SequenceStorage newStorage, InlinedConditionProfile generalizedProfile) {
            if (generalizedProfile.profile(inliningTarget, primary.getSequenceStorage() != newStorage)) {
                primary.setSequenceStorage(newStorage);
            }
        }

        @NeverDefault
        protected static SetItemNode create() {
            return ListBuiltinsFactory.SetItemNodeFactory.create();
        }
    }

    // list.append(x)
    @Builtin(name = J_APPEND, minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    public abstract static class ListAppendNode extends PythonBinaryBuiltinNode {

        @Specialization
        public PNone appendObjectGeneric(PList list, Object arg,
                        @Cached ListNodes.AppendNode appendNode) {
            appendNode.execute(list, arg);
            return PNone.NONE;
        }
    }

    // list.extend(L)
    @Builtin(name = J_EXTEND, minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    public abstract static class ListExtendNode extends PythonBinaryBuiltinNode {

        public abstract PNone execute(VirtualFrame frame, PList list, Object source);

        @Specialization
        PNone extendSequence(VirtualFrame frame, PList list, Object iterable,
                        @Bind("this") Node inliningTarget,
                        @Cached IteratorNodes.GetLength lenNode,
                        @Cached("createExtend()") SequenceStorageNodes.ExtendNode extendNode) {
            int len = lenNode.execute(frame, inliningTarget, iterable);
            updateSequenceStorage(list, extendNode.execute(frame, list.getSequenceStorage(), iterable, len));
            return PNone.NONE;
        }

        private static void updateSequenceStorage(PList list, SequenceStorage s) {
            if (list.getSequenceStorage() != s) {
                list.setSequenceStorage(s);
            }
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
        static PList copySequence(PList self,
                        @Bind("this") Node inliningTarget,
                        @Cached SequenceStorageNodes.CopyNode copy,
                        @Cached GetClassNode getClassNode,
                        @Cached PythonObjectFactory factory) {
            return factory.createList(getClassNode.execute(inliningTarget, self), copy.execute(inliningTarget, self.getSequenceStorage()));
        }

    }

    // list.insert(i, x)
    @Builtin(name = "insert", minNumOfPositionalArgs = 3)
    @GenerateNodeFactory
    public abstract static class ListInsertNode extends PythonTernaryBuiltinNode {
        protected static final TruffleString ERROR_MSG = ErrorMessages.OBJ_CANNOT_BE_INTERPRETED_AS_INTEGER;

        public abstract PNone execute(VirtualFrame frame, PList list, Object index, Object value);

        @Specialization(guards = "isIntStorage(list)")
        static PNone insertIntInt(PList list, int index, int value) {
            IntSequenceStorage target = (IntSequenceStorage) list.getSequenceStorage();
            target.insertIntItem(normalizeIndex(index, target.length()), value);
            return PNone.NONE;
        }

        @Specialization(guards = "isLongStorage(list)")
        static PNone insertLongLong(PList list, int index, int value) {
            LongSequenceStorage target = (LongSequenceStorage) list.getSequenceStorage();
            target.insertLongItem(normalizeIndex(index, target.length()), value);
            return PNone.NONE;
        }

        @Specialization(guards = "isLongStorage(list)")
        static PNone insertLongLong(PList list, int index, long value) {
            LongSequenceStorage target = (LongSequenceStorage) list.getSequenceStorage();
            target.insertLongItem(normalizeIndex(index, target.length()), value);
            return PNone.NONE;
        }

        @Specialization(guards = "isDoubleStorage(list)")
        static PNone insertDoubleDouble(PList list, int index, double value) {
            DoubleSequenceStorage target = (DoubleSequenceStorage) list.getSequenceStorage();
            target.insertDoubleItem(normalizeIndex(index, target.length()), value);
            return PNone.NONE;
        }

        @Specialization(guards = "isNotSpecialCase(list, value)")
        static PNone insert(PList list, int index, Object value,
                        @Bind("this") Node inliningTarget,
                        @Cached SequenceStorageNodes.InsertItemNode insertItem) {
            SequenceStorage store = list.getSequenceStorage();
            list.setSequenceStorage(insertItem.execute(inliningTarget, store, normalizeIndex(index, store.length()), value));
            return PNone.NONE;
        }

        @Specialization
        static PNone insertLongIndex(VirtualFrame frame, PList list, long index, Object value,
                        @Shared @Cached ListInsertNode insertNode) {
            int where = index < Integer.MIN_VALUE ? 0 : index > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) index;
            where = normalizeIndex(where, list.getSequenceStorage().length());
            return insertNode.execute(frame, list, where, value);
        }

        @Specialization
        static PNone insertPIntIndex(VirtualFrame frame, PList list, PInt index, Object value,
                        @Shared @Cached ListInsertNode insertNode) {
            int where = normalizePIntForIndex(index);
            where = normalizeIndex(where, list.getSequenceStorage().length());
            return insertNode.execute(frame, list, where, value);
        }

        @Specialization(guards = {"!isIntegerOrPInt(i)"})
        static PNone insert(VirtualFrame frame, PList list, Object i, Object value,
                        @Cached("createInteger(ERROR_MSG)") IndexNode indexNode,
                        @Shared @Cached ListInsertNode insertNode) {
            Object indexValue = indexNode.execute(frame, i);
            return insertNode.execute(frame, list, indexValue, value);
        }

        @TruffleBoundary
        private static int normalizePIntForIndex(PInt index) {
            int where = 0;
            BigInteger bigIndex = index.getValue();
            if (bigIndex.compareTo(BigInteger.valueOf(Integer.MIN_VALUE)) < 0) {
                where = 0;
            } else if (bigIndex.compareTo(BigInteger.valueOf(Integer.MAX_VALUE)) > 0) {
                where = Integer.MAX_VALUE;
            } else {
                where = bigIndex.intValue();
            }
            return where;
        }

        private static int normalizeIndex(int index, int len) {
            int idx = index;
            if (idx < 0) {
                idx += len;
                if (idx < 0) {
                    idx = 0;
                }
            }
            if (idx > len) {
                idx = len;
            }
            return idx;
        }

        protected static boolean isNotSpecialCase(PList list, Object value) {
            return !((PGuards.isIntStorage(list) && value instanceof Integer) || (PGuards.isLongStorage(list) && PGuards.isInteger(value)) ||
                            (PGuards.isDoubleStorage(list) && value instanceof Double));
        }

        protected static boolean isIntegerOrPInt(Object index) {
            return index instanceof Integer || index instanceof PInt;
        }

    }

    // list.remove(x)
    @Builtin(name = "remove", minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    public abstract static class ListRemoveNode extends PythonBinaryBuiltinNode {

        @Specialization
        PNone remove(VirtualFrame frame, PList list, Object value,
                        @Bind("this") Node inliningTarget,
                        @Cached("createNotNormalized()") SequenceStorageNodes.GetItemNode getItemNode,
                        @Cached SequenceStorageNodes.DeleteNode deleteNode,
                        @Cached PyObjectRichCompareBool.EqNode eqNode) {
            SequenceStorage listStore = list.getSequenceStorage();
            int len = listStore.length();
            for (int i = 0; i < len; i++) {
                Object object = getItemNode.execute(listStore, i);
                if (eqNode.compare(frame, inliningTarget, object, value)) {
                    deleteNode.execute(frame, listStore, i);
                    return PNone.NONE;
                }
            }
            throw raise(PythonErrorType.ValueError, ErrorMessages.NOT_IN_LIST_MESSAGE);
        }
    }

    // list.pop([i])
    @Builtin(name = "pop", minNumOfPositionalArgs = 1, maxNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    public abstract static class ListPopNode extends PythonBinaryBuiltinNode {

        @Child private SequenceStorageNodes.GetItemNode getItemNode;

        @Specialization
        public Object popLast(VirtualFrame frame, PList list, @SuppressWarnings("unused") PNone none,
                        @Shared @Cached("createDelete()") SequenceStorageNodes.DeleteNode deleteNode) {
            SequenceStorage store = list.getSequenceStorage();
            Object ret = getGetItemNode().execute(store, -1);
            deleteNode.execute(frame, store, -1);
            return ret;
        }

        @Specialization(guards = {"!isNoValue(idx)", "!isPSlice(idx)"})
        public Object doIndex(VirtualFrame frame, PList list, Object idx,
                        @Shared @Cached("createDelete()") SequenceStorageNodes.DeleteNode deleteNode) {
            SequenceStorage store = list.getSequenceStorage();
            Object ret = getGetItemNode().execute(frame, store, idx);
            deleteNode.execute(frame, store, idx);
            return ret;
        }

        @Fallback
        public Object doError(@SuppressWarnings("unused") Object list, Object arg) {
            throw raise(TypeError, ErrorMessages.OBJ_CANNOT_BE_INTERPRETED_AS_INTEGER, arg);
        }

        private SequenceStorageNodes.GetItemNode getGetItemNode() {
            if (this.getItemNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                this.getItemNode = insert(SequenceStorageNodes.GetItemNode.create(createNormalize()));
            }
            return this.getItemNode;
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
    @Builtin(name = "index", minNumOfPositionalArgs = 2, maxNumOfPositionalArgs = 4)
    @TypeSystemReference(PythonArithmeticTypes.class)
    @ImportStatic(MathGuards.class)
    @GenerateNodeFactory
    public abstract static class ListIndexNode extends PythonBuiltinNode {
        protected static final TruffleString ERROR_TYPE_MESSAGE = ErrorMessages.SLICE_INDICES_TYPE_ERROR;

        public abstract int execute(VirtualFrame frame, Object arg1, Object arg2, Object arg3, Object arg4);

        private static int correctIndex(SequenceStorage s, long index) {
            long resultIndex = index;
            if (resultIndex < 0) {
                resultIndex += s.length();
                if (resultIndex < 0) {
                    return 0;
                }
            }
            return (int) Math.min(resultIndex, Integer.MAX_VALUE);
        }

        @TruffleBoundary(transferToInterpreterOnException = false)
        private static int correctIndex(SequenceStorage s, PInt index) {
            BigInteger value = index.getValue();
            if (value.compareTo(BigInteger.ZERO) < 0) {
                BigInteger resultAdd = value.add(BigInteger.valueOf(s.length()));
                if (resultAdd.compareTo(BigInteger.ZERO) < 0) {
                    return 0;
                }
                return resultAdd.intValue();
            }
            return value.min(BigInteger.valueOf(Integer.MAX_VALUE)).intValue();
        }

        private int findIndex(VirtualFrame frame, Node inliningTarget, SequenceStorageNodes.ItemIndexNode itemIndexNode, SequenceStorage s, Object value, int start, int end) {
            int idx = itemIndexNode.execute(frame, inliningTarget, s, value, start, end);
            if (idx != -1) {
                return idx;
            }
            throw raise(PythonErrorType.ValueError, ErrorMessages.X_NOT_IN_LIST);
        }

        @Specialization
        int index(VirtualFrame frame, PList self, Object value, @SuppressWarnings("unused") PNone start, @SuppressWarnings("unused") PNone end,
                        @Bind("this") Node inliningTarget,
                        @Shared @Cached SequenceStorageNodes.ItemIndexNode itemIndexNode) {
            SequenceStorage s = self.getSequenceStorage();
            return findIndex(frame, inliningTarget, itemIndexNode, s, value, 0, s.length());
        }

        @Specialization
        int index(VirtualFrame frame, PList self, Object value, long start, @SuppressWarnings("unused") PNone end,
                        @Bind("this") Node inliningTarget,
                        @Shared @Cached SequenceStorageNodes.ItemIndexNode itemIndexNode) {
            SequenceStorage s = self.getSequenceStorage();
            return findIndex(frame, inliningTarget, itemIndexNode, s, value, correctIndex(s, start), s.length());
        }

        @Specialization
        int index(VirtualFrame frame, PList self, Object value, long start, long end,
                        @Bind("this") Node inliningTarget,
                        @Shared @Cached SequenceStorageNodes.ItemIndexNode itemIndexNode) {
            SequenceStorage s = self.getSequenceStorage();
            return findIndex(frame, inliningTarget, itemIndexNode, s, value, correctIndex(s, start), correctIndex(s, end));
        }

        @Specialization
        int indexPI(VirtualFrame frame, PList self, Object value, PInt start, @SuppressWarnings("unused") PNone end,
                        @Bind("this") Node inliningTarget,
                        @Shared @Cached SequenceStorageNodes.ItemIndexNode itemIndexNode) {
            SequenceStorage s = self.getSequenceStorage();
            return findIndex(frame, inliningTarget, itemIndexNode, s, value, correctIndex(s, start), s.length());
        }

        @Specialization
        int indexPIPI(VirtualFrame frame, PList self, Object value, PInt start, PInt end,
                        @Bind("this") Node inliningTarget,
                        @Shared @Cached SequenceStorageNodes.ItemIndexNode itemIndexNode) {
            SequenceStorage s = self.getSequenceStorage();
            return findIndex(frame, inliningTarget, itemIndexNode, s, value, correctIndex(s, start), correctIndex(s, end));
        }

        @Specialization
        int indexLPI(VirtualFrame frame, PList self, Object value, long start, PInt end,
                        @Bind("this") Node inliningTarget,
                        @Shared @Cached SequenceStorageNodes.ItemIndexNode itemIndexNode) {
            SequenceStorage s = self.getSequenceStorage();
            return findIndex(frame, inliningTarget, itemIndexNode, s, value, correctIndex(s, start), correctIndex(s, end));
        }

        @Specialization
        int indexPIL(VirtualFrame frame, PList self, Object value, PInt start, Long end,
                        @Bind("this") Node inliningTarget,
                        @Shared @Cached SequenceStorageNodes.ItemIndexNode itemIndexNode) {
            SequenceStorage s = self.getSequenceStorage();
            return findIndex(frame, inliningTarget, itemIndexNode, s, value, correctIndex(s, start), correctIndex(s, end));
        }

        @Specialization
        @SuppressWarnings("unused")
        int indexDO(PTuple self, Object value, double start, Object end) {
            throw raise(TypeError, ERROR_TYPE_MESSAGE);
        }

        @Specialization
        @SuppressWarnings("unused")
        int indexOD(PTuple self, Object value, Object start, double end) {
            throw raise(TypeError, ERROR_TYPE_MESSAGE);
        }

        @Specialization(guards = "!isNumber(start)")
        int indexO(VirtualFrame frame, PTuple self, Object value, Object start, PNone end,
                        @Shared @Cached("createNumber(ERROR_TYPE_MESSAGE)") IndexNode startNode,
                        @Shared @Cached("createIndexNode()") ListIndexNode indexNode) {
            Object startValue = startNode.execute(frame, start);
            return indexNode.execute(frame, self, value, startValue, end);
        }

        @Specialization(guards = {"!isNumber(end)"})
        int indexLO(VirtualFrame frame, PTuple self, Object value, long start, Object end,
                        @Shared @Cached("createNumber(ERROR_TYPE_MESSAGE)") IndexNode endNode,
                        @Shared @Cached("createIndexNode()") ListIndexNode indexNode) {
            Object endValue = endNode.execute(frame, end);
            return indexNode.execute(frame, self, value, start, endValue);
        }

        @Specialization(guards = {"!isNumber(start) || !isNumber(end)"})
        int indexOO(VirtualFrame frame, PTuple self, Object value, Object start, Object end,
                        @Shared @Cached("createNumber(ERROR_TYPE_MESSAGE)") IndexNode startNode,
                        @Shared @Cached("createNumber(ERROR_TYPE_MESSAGE)") IndexNode endNode,
                        @Shared @Cached("createIndexNode()") ListIndexNode indexNode) {
            Object startValue = startNode.execute(frame, start);
            Object endValue = endNode.execute(frame, end);
            return indexNode.execute(frame, self, value, startValue, endValue);
        }

        @NeverDefault
        protected ListIndexNode createIndexNode() {
            return ListBuiltinsFactory.ListIndexNodeFactory.create(null);
        }
    }

    // list.count(x)
    @Builtin(name = "count", minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    public abstract static class ListCountNode extends PythonBuiltinNode {

        @Specialization
        long count(VirtualFrame frame, PList self, Object value,
                        @Bind("this") Node inliningTarget,
                        @Cached("createNotNormalized()") SequenceStorageNodes.GetItemNode getItemNode,
                        @Cached PyObjectRichCompareBool.EqNode eqNode) {
            long count = 0;
            SequenceStorage s = self.getSequenceStorage();
            for (int i = 0; i < s.length(); i++) {
                Object seqItem = getItemNode.execute(s, i);
                if (eqNode.compare(frame, inliningTarget, seqItem, value)) {
                    count++;
                }
            }
            return count;
        }

    }

    // list.clear()
    @Builtin(name = "clear", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class ListClearNode extends PythonUnaryBuiltinNode {

        @Specialization
        static PNone clear(PList list) {
            list.setSequenceStorage(EmptySequenceStorage.INSTANCE);
            return PNone.NONE;
        }
    }

    // list.reverse()
    @Builtin(name = "reverse", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class ListReverseNode extends PythonUnaryBuiltinNode {

        @Specialization
        static PList reverse(PList list) {
            list.reverse();
            return list;
        }

    }

    // list.sort(key=None, reverse=False)
    @Builtin(name = J_SORT, minNumOfPositionalArgs = 1, parameterNames = {"$self"}, keywordOnlyNames = {"key", "reverse"})
    @ArgumentClinic(name = "reverse", conversion = ArgumentClinic.ClinicConversion.IntToBoolean, defaultValue = "false")
    @GenerateNodeFactory
    public abstract static class ListSortNode extends PythonClinicBuiltinNode {
        public final Object execute(VirtualFrame frame, PList list) {
            return execute(frame, list, PNone.NO_VALUE, false);
        }

        public abstract Object execute(VirtualFrame frame, PList list, Object keyfunc, boolean reverse);

        @Specialization
        Object doGeneric(VirtualFrame frame, PList list, Object keyfunc, boolean reverse,
                        @Cached SortSequenceStorageNode sortSequenceStorageNode) {
            SequenceStorage storage = list.getSequenceStorage();
            // Make the list temporarily empty to prevent concurrent modification
            list.setSequenceStorage(EmptySequenceStorage.INSTANCE);
            try {
                sortSequenceStorageNode.execute(frame, storage, keyfunc, reverse);
                if (list.getSequenceStorage() != EmptySequenceStorage.INSTANCE) {
                    throw raise(ValueError, ErrorMessages.LIST_MODIFIED_DURING_SOFT);
                }
            } finally {
                list.setSequenceStorage(storage);
            }
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

    @Builtin(name = J___LEN__, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class LenNode extends PythonUnaryBuiltinNode {

        @Specialization
        int doGeneric(PList list) {
            return list.getSequenceStorage().length();
        }
    }

    @Builtin(name = J___ADD__, minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    abstract static class AddNode extends PythonBinaryBuiltinNode {
        @Specialization
        static PList doPList(PList left, PList other,
                        @Bind("this") Node inliningTarget,
                        @Cached GetClassNode getClassNode,
                        @Cached("createConcat()") SequenceStorageNodes.ConcatNode concatNode,
                        @Cached PythonObjectFactory factory) {
            SequenceStorage newStore = concatNode.execute(left.getSequenceStorage(), other.getSequenceStorage());
            return factory.createList(getClassNode.execute(inliningTarget, left), newStore);
        }

        @Specialization(guards = "!isList(right)")
        Object doGeneric(@SuppressWarnings("unused") Object left, Object right) {
            throw raise(TypeError, ErrorMessages.CAN_ONLY_CONCAT_S_NOT_P_TO_S, "list", right, "list");
        }

        @NeverDefault
        protected static SequenceStorageNodes.ConcatNode createConcat() {
            return SequenceStorageNodes.ConcatNode.create(() -> SequenceStorageNodes.ListGeneralizationNode.create());
        }
    }

    @Builtin(name = J___IADD__, minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    abstract static class IAddNode extends PythonBinaryBuiltinNode {
        @Specialization
        PList extendSequence(VirtualFrame frame, PList list, Object iterable,
                        @Bind("this") Node inliningTarget,
                        @Cached IteratorNodes.GetLength lenNode,
                        @Cached("createExtend()") SequenceStorageNodes.ExtendNode extendNode) {
            int len = lenNode.execute(frame, inliningTarget, iterable);
            updateSequenceStorage(list, extendNode.execute(frame, list.getSequenceStorage(), iterable, len));
            return list;
        }

        private static void updateSequenceStorage(PList list, SequenceStorage s) {
            if (list.getSequenceStorage() != s) {
                list.setSequenceStorage(s);
            }
        }

        @NeverDefault
        protected static SequenceStorageNodes.ExtendNode createExtend() {
            return SequenceStorageNodes.ExtendNode.create(ListGeneralizationNode.SUPPLIER);
        }
    }

    @Builtin(name = J___RMUL__, minNumOfPositionalArgs = 2)
    @Builtin(name = J___MUL__, minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    abstract static class MulNode extends PythonBinaryBuiltinNode {

        @Specialization
        PList doPListInt(VirtualFrame frame, PList left, Object right,
                        @Cached SequenceStorageNodes.RepeatNode repeatNode,
                        @Cached PythonObjectFactory factory) {
            try {
                SequenceStorage repeated = repeatNode.execute(frame, left.getSequenceStorage(), right);
                return factory.createList(repeated);
            } catch (ArithmeticException | OutOfMemoryError e) {
                throw raise(MemoryError);
            }
        }

        @SuppressWarnings("unused")
        @Fallback
        PNotImplemented doGeneric(Object left, Object right) {
            return PNotImplemented.NOT_IMPLEMENTED;
        }
    }

    @Builtin(name = J___IMUL__, minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    abstract static class IMulNode extends PythonBinaryBuiltinNode {
        @Specialization
        Object doGeneric(VirtualFrame frame, PList list, Object right,
                        @Bind("this") Node inliningTarget,
                        @Cached InlinedConditionProfile updatedProfile,
                        @Cached SequenceStorageNodes.RepeatNode repeatNode) {

            SequenceStorage store = list.getSequenceStorage();
            SequenceStorage updated = repeatNode.execute(frame, store, right);
            if (updatedProfile.profile(inliningTarget, store != updated)) {
                list.setSequenceStorage(updated);
            }
            return list;
        }

        protected IMulNode createIMulNode() {
            return ListBuiltinsFactory.IMulNodeFactory.create();
        }
    }

    @Builtin(name = J___EQ__, minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    abstract static class EqNode extends PythonBinaryBuiltinNode {

        protected static boolean isObjectStorage(PList left, PList right) {
            return PGuards.isObjectStorage(left) || PGuards.isObjectStorage(right);
        }

        @Specialization(guards = "!isObjectStorage(left, right)")
        boolean doPList(VirtualFrame frame, PList left, PList right,
                        @Shared @Cached("createEq()") SequenceStorageNodes.CmpNode neNode) {
            return neNode.execute(frame, left.getSequenceStorage(), right.getSequenceStorage());
        }

        /**
         * This is a fix for the bpo-38588 bug. See
         * {@code test_list.py: ListTest.test_equal_operator_modifying_operand}
         */
        @Specialization(guards = "isObjectStorage(left, right)")
        boolean doPListObjectStorage(VirtualFrame frame, PList left, PList right,
                        @Shared @Cached("createEq()") SequenceStorageNodes.CmpNode neNode) {
            final SequenceStorage leftStorage = left.getSequenceStorage();
            final SequenceStorage rightStorage = right.getSequenceStorage();
            final boolean result = neNode.execute(frame, leftStorage, rightStorage);
            /**
             * This will check if the underlying storage has been modified and if so, we do the
             * check again.
             */
            if (leftStorage == left.getSequenceStorage() && rightStorage == right.getSequenceStorage()) {
                return result;
            }
            /**
             * To avoid possible infinite recursion case, we call the default specialization.
             */
            return doPList(frame, left, right, neNode);
        }

        @Fallback
        @SuppressWarnings("unused")
        Object doOther(Object left, Object right) {
            return PNotImplemented.NOT_IMPLEMENTED;
        }
    }

    @Builtin(name = J___NE__, minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    abstract static class NeNode extends PythonBinaryBuiltinNode {

        @Specialization
        boolean doPList(VirtualFrame frame, PList left, PList right,
                        @Cached("createEq()") SequenceStorageNodes.CmpNode eqNode) {
            return !eqNode.execute(frame, left.getSequenceStorage(), right.getSequenceStorage());
        }

        @Fallback
        @SuppressWarnings("unused")
        PNotImplemented doOther(Object left, Object right) {
            return PNotImplemented.NOT_IMPLEMENTED;
        }
    }

    @Builtin(name = J___GE__, minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    abstract static class GeNode extends PythonBinaryBuiltinNode {

        @Specialization
        boolean doPList(VirtualFrame frame, PList left, PList right,
                        @Cached("createGe()") SequenceStorageNodes.CmpNode neNode) {
            return neNode.execute(frame, left.getSequenceStorage(), right.getSequenceStorage());
        }

        @Fallback
        @SuppressWarnings("unused")
        PNotImplemented doOther(Object left, Object right) {
            return PNotImplemented.NOT_IMPLEMENTED;
        }

    }

    @Builtin(name = J___LE__, minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    abstract static class LeNode extends PythonBinaryBuiltinNode {

        @Specialization
        boolean doPList(VirtualFrame frame, PList left, PList right,
                        @Cached("createLe()") SequenceStorageNodes.CmpNode neNode) {
            return neNode.execute(frame, left.getSequenceStorage(), right.getSequenceStorage());
        }

        @Fallback
        @SuppressWarnings("unused")
        PNotImplemented doOther(Object left, Object right) {
            return PNotImplemented.NOT_IMPLEMENTED;
        }

    }

    @Builtin(name = J___GT__, minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    abstract static class GtNode extends PythonBinaryBuiltinNode {

        @Specialization
        boolean doPList(VirtualFrame frame, PList left, PList right,
                        @Cached("createGt()") SequenceStorageNodes.CmpNode neNode) {
            return neNode.execute(frame, left.getSequenceStorage(), right.getSequenceStorage());
        }

        @Fallback
        PNotImplemented doOther(@SuppressWarnings("unused") Object left, @SuppressWarnings("unused") Object right) {
            return PNotImplemented.NOT_IMPLEMENTED;
        }
    }

    @Builtin(name = J___LT__, minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    abstract static class LtNode extends PythonBinaryBuiltinNode {
        @Specialization
        boolean doPList(VirtualFrame frame, PList left, PList right,
                        @Cached("createLt()") SequenceStorageNodes.CmpNode neNode) {
            return neNode.execute(frame, left.getSequenceStorage(), right.getSequenceStorage());
        }

        @Fallback
        PNotImplemented contains(@SuppressWarnings("unused") Object self, @SuppressWarnings("unused") Object other) {
            return PNotImplemented.NOT_IMPLEMENTED;
        }
    }

    @Builtin(name = J___CONTAINS__, minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    abstract static class ContainsNode extends PythonBinaryBuiltinNode {
        @Specialization
        boolean contains(VirtualFrame frame, PSequence self, Object other,
                        @Bind("this") Node inliningTarget,
                        @Cached SequenceNodes.GetSequenceStorageNode getStorage,
                        @Cached SequenceStorageNodes.ContainsNode containsNode) {
            return containsNode.execute(frame, inliningTarget, getStorage.execute(inliningTarget, self), other);
        }
    }

    @Builtin(name = J___ITER__, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class IterNode extends PythonUnaryBuiltinNode {

        /*
         * Don't create PObjectSequenceIterators here - otherwise list.clear will not reflect in the
         * iterator.
         */
        @Specialization(guards = {"isIntStorage(primary)"})
        static PIntegerSequenceIterator doPListInt(PList primary,
                        @Shared @Cached PythonObjectFactory factory) {
            return factory.createIntegerSequenceIterator((IntSequenceStorage) primary.getSequenceStorage(), primary);
        }

        @Specialization(guards = {"isLongStorage(primary)"})
        static PLongSequenceIterator doPListLong(PList primary,
                        @Shared @Cached PythonObjectFactory factory) {
            return factory.createLongSequenceIterator((LongSequenceStorage) primary.getSequenceStorage(), primary);
        }

        @Specialization(guards = {"isDoubleStorage(primary)"})
        static PDoubleSequenceIterator doPListDouble(PList primary,
                        @Shared @Cached PythonObjectFactory factory) {
            return factory.createDoubleSequenceIterator((DoubleSequenceStorage) primary.getSequenceStorage(), primary);
        }

        @Specialization(guards = {"!isIntStorage(primary)", "!isLongStorage(primary)", "!isDoubleStorage(primary)"})
        static PSequenceIterator doPList(PList primary,
                        @Shared @Cached PythonObjectFactory factory) {
            return factory.createSequenceIterator(primary);
        }

        @Fallback
        static Object doGeneric(@SuppressWarnings("unused") Object self) {
            return PNone.NONE;
        }
    }

    @Builtin(name = J___REVERSED__, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class ReverseNode extends PythonUnaryBuiltinNode {
        @Specialization
        static Object reverse(PList self,
                        @Bind("this") Node inliningTarget,
                        @Cached SequenceNodes.GetSequenceStorageNode getSequenceStorageNode,
                        @Cached PythonObjectFactory factory) {
            int len = getSequenceStorageNode.execute(inliningTarget, self).length();
            return factory.createSequenceReverseIterator(PythonBuiltinClassType.PReverseIterator, self, len);
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
