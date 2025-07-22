/*
 * Copyright (c) 2020, 2025, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.builtins.objects.set;

import static com.oracle.graal.python.nodes.SpecialMethodNames.J___CLASS_GETITEM__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___REDUCE__;
import static com.oracle.graal.python.nodes.StringLiterals.T_COMMA_SPACE;
import static com.oracle.graal.python.nodes.StringLiterals.T_ELLIPSIS_IN_PARENS;
import static com.oracle.graal.python.nodes.StringLiterals.T_EMPTY_PARENS;
import static com.oracle.graal.python.nodes.StringLiterals.T_LBRACE;
import static com.oracle.graal.python.nodes.StringLiterals.T_LPAREN;
import static com.oracle.graal.python.nodes.StringLiterals.T_RBRACE;
import static com.oracle.graal.python.nodes.StringLiterals.T_RPAREN;
import static com.oracle.graal.python.util.PythonUtils.TS_ENCODING;

import java.util.List;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.annotations.Slot;
import com.oracle.graal.python.annotations.Slot.SlotKind;
import com.oracle.graal.python.builtins.Builtin;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.objects.PNotImplemented;
import com.oracle.graal.python.builtins.objects.common.HashingCollectionNodes;
import com.oracle.graal.python.builtins.objects.common.HashingStorage;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodes;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodes.HashingStorageAreDisjoint;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodes.HashingStorageCopy;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodes.HashingStorageGetItem;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodes.HashingStorageGetIterator;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodes.HashingStorageIterator;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodes.HashingStorageIteratorKey;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodes.HashingStorageIteratorNext;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodes.HashingStorageLen;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodes.IsKeysSubset;
import com.oracle.graal.python.builtins.objects.tuple.PTuple;
import com.oracle.graal.python.builtins.objects.type.TpSlots;
import com.oracle.graal.python.builtins.objects.type.TpSlots.GetObjectSlotsNode;
import com.oracle.graal.python.builtins.objects.type.TypeNodes;
import com.oracle.graal.python.builtins.objects.type.slots.TpSlotBinaryOp.BinaryOpBuiltinNode;
import com.oracle.graal.python.builtins.objects.type.slots.TpSlotHashFun;
import com.oracle.graal.python.builtins.objects.type.slots.TpSlotLen.LenBuiltinNode;
import com.oracle.graal.python.builtins.objects.type.slots.TpSlotRichCompare;
import com.oracle.graal.python.builtins.objects.type.slots.TpSlotSqContains.SqContainsBuiltinNode;
import com.oracle.graal.python.lib.IteratorExhausted;
import com.oracle.graal.python.lib.PyIterNextNode;
import com.oracle.graal.python.lib.PyObjectGetIter;
import com.oracle.graal.python.lib.PyObjectGetStateNode;
import com.oracle.graal.python.lib.PyObjectReprAsTruffleStringNode;
import com.oracle.graal.python.lib.RichCmpOp;
import com.oracle.graal.python.nodes.PGuards;
import com.oracle.graal.python.nodes.PNodeWithContext;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.builtins.PythonBinaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonUnaryBuiltinNode;
import com.oracle.graal.python.nodes.object.GetClassNode;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.graal.python.runtime.object.PFactory;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.GenerateCached;
import com.oracle.truffle.api.dsl.GenerateInline;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.profiles.InlinedConditionProfile;
import com.oracle.truffle.api.strings.TruffleStringBuilder;

@CoreFunctions(extendClasses = {PythonBuiltinClassType.PSet, PythonBuiltinClassType.PFrozenSet})
public final class BaseSetBuiltins extends PythonBuiltins {
    public static final TpSlots SLOTS = BaseSetBuiltinsSlotsGen.SLOTS;

    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return BaseSetBuiltinsFactory.getFactories();
    }

    @Slot(value = SlotKind.tp_repr, isComplex = true)
    @GenerateNodeFactory
    abstract static class BaseReprNode extends PythonUnaryBuiltinNode {
        private static void fillItems(VirtualFrame frame, Node inliningTarget, HashingStorage storage, TruffleStringBuilder sb, PyObjectReprAsTruffleStringNode repr,
                        HashingStorageGetIterator getIter, HashingStorageIteratorNext iterNext, HashingStorageIteratorKey iterKey,
                        TruffleStringBuilder.AppendStringNode appendStringNode) {
            boolean first = true;
            appendStringNode.execute(sb, T_LBRACE);
            HashingStorageIterator it = getIter.execute(inliningTarget, storage);
            while (iterNext.execute(inliningTarget, storage, it)) {
                if (first) {
                    first = false;
                } else {
                    appendStringNode.execute(sb, T_COMMA_SPACE);
                }
                appendStringNode.execute(sb, repr.execute(frame, inliningTarget, iterKey.execute(inliningTarget, storage, it)));
            }
            appendStringNode.execute(sb, T_RBRACE);
        }

        @Specialization
        public static Object repr(VirtualFrame frame, PBaseSet self,
                        @Bind Node inliningTarget,
                        @Cached PyObjectReprAsTruffleStringNode repr,
                        @Cached TypeNodes.GetNameNode getNameNode,
                        @Cached GetClassNode getClassNode,
                        @Cached HashingStorageLen lenNode,
                        @Cached HashingStorageGetIterator getStorageIterator,
                        @Cached HashingStorageIteratorNext iteratorNext,
                        @Cached HashingStorageIteratorKey iteratorKey,
                        @Cached TruffleStringBuilder.AppendStringNode appendStringNode,
                        @Cached TruffleStringBuilder.ToStringNode toStringNode) {
            TruffleStringBuilder sb = TruffleStringBuilder.create(TS_ENCODING);
            Object clazz = getClassNode.execute(inliningTarget, self);
            PythonContext ctxt = PythonContext.get(getNameNode);
            int len = lenNode.execute(inliningTarget, self.getDictStorage());
            if (len == 0) {
                appendStringNode.execute(sb, getNameNode.execute(inliningTarget, clazz));
                appendStringNode.execute(sb, T_EMPTY_PARENS);
                return toStringNode.execute(sb);
            }
            if (!ctxt.reprEnter(self)) {
                appendStringNode.execute(sb, getNameNode.execute(inliningTarget, clazz));
                appendStringNode.execute(sb, T_ELLIPSIS_IN_PARENS);
                return toStringNode.execute(sb);
            }
            try {
                boolean showType = clazz != PythonBuiltinClassType.PSet;
                if (showType) {
                    appendStringNode.execute(sb, getNameNode.execute(inliningTarget, clazz));
                    appendStringNode.execute(sb, T_LPAREN);
                }
                fillItems(frame, inliningTarget, self.getDictStorage(), sb, repr, getStorageIterator, iteratorNext, iteratorKey, appendStringNode);
                if (showType) {
                    appendStringNode.execute(sb, T_RPAREN);
                }
                return toStringNode.execute(sb);
            } finally {
                ctxt.reprLeave(self);
            }
        }
    }

    @Slot(value = SlotKind.tp_iter, isComplex = true)
    @GenerateNodeFactory
    protected abstract static class BaseIterNode extends PythonUnaryBuiltinNode {
        @Specialization
        static Object doBaseSet(PBaseSet self,
                        @Bind Node inliningTarget,
                        @Cached HashingStorageLen lenNode,
                        @Cached HashingStorageGetIterator getIterator,
                        @Bind PythonLanguage language) {
            HashingStorage storage = self.getDictStorage();
            return PFactory.createBaseSetIterator(language, self, getIterator.execute(inliningTarget, storage), lenNode.execute(inliningTarget, storage));
        }
    }

    @Slot(SlotKind.sq_length)
    @GenerateUncached
    @GenerateNodeFactory
    protected abstract static class BaseSetLenSlotNode extends LenBuiltinNode {
        @Specialization
        public static int len(PBaseSet self,
                        @Bind Node inliningTarget,
                        @Cached HashingStorageLen lenNode) {
            return lenNode.execute(inliningTarget, self.getDictStorage());
        }
    }

    @Builtin(name = J___REDUCE__, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    protected abstract static class BaseReduceNode extends PythonUnaryBuiltinNode {

        @Specialization
        static Object reduce(VirtualFrame frame, PBaseSet self,
                        @Bind Node inliningTarget,
                        @Cached HashingStorageLen lenNode,
                        @Cached HashingStorageGetIterator getIter,
                        @Cached HashingStorageIteratorNext iterNext,
                        @Cached HashingStorageIteratorKey getIterKey,
                        @Cached GetClassNode getClassNode,
                        @Cached PyObjectGetStateNode getStateNode,
                        @Bind PythonLanguage language) {
            HashingStorage storage = self.getDictStorage();
            int len = lenNode.execute(inliningTarget, storage);
            Object[] keysArray = new Object[len];
            HashingStorageIterator it = getIter.execute(inliningTarget, storage);
            for (int i = 0; i < len; i++) {
                boolean hasNext = iterNext.execute(inliningTarget, storage, it);
                assert hasNext;
                keysArray[i] = getIterKey.execute(inliningTarget, storage, it);
            }
            PTuple contents = PFactory.createTuple(language, new Object[]{PFactory.createList(language, keysArray)});
            Object state = getStateNode.execute(frame, inliningTarget, self);
            return PFactory.createTuple(language, new Object[]{getClassNode.execute(inliningTarget, self), contents, state});
        }
    }

    @Slot(value = SlotKind.tp_richcompare, isComplex = true)
    @GenerateNodeFactory
    protected abstract static class BaseSetRichCmpNode extends TpSlotRichCompare.RichCmpBuiltinNode {
        static boolean lengthsMismatch(int len1, int len2, RichCmpOp op) {
            return switch (op) {
                case Py_EQ -> len1 != len2;
                case Py_LT -> len1 >= len2;
                case Py_GT -> len1 <= len2;
                case Py_LE -> len1 > len2;
                case Py_GE -> len1 < len2;
                case Py_NE -> throw CompilerDirectives.shouldNotReachHere();
            };
        }

        @Specialization
        static Object doIt(VirtualFrame frame, PBaseSet self, PBaseSet other, RichCmpOp opIn,
                        @Bind Node inliningTarget,
                        @Cached HashingStorageLen lenSelfNode,
                        @Cached HashingStorageLen lenOtherNode,
                        @Cached InlinedConditionProfile sizeProfile,
                        @Cached IsKeysSubset issubsetNode) {
            boolean wasNe = opIn.isNe();
            RichCmpOp op = opIn;
            if (wasNe) {
                op = RichCmpOp.Py_EQ;
            }

            final int len1 = lenSelfNode.execute(inliningTarget, self.getDictStorage());
            final int len2 = lenOtherNode.execute(inliningTarget, other.getDictStorage());
            if (sizeProfile.profile(inliningTarget, lengthsMismatch(len1, len2, op))) {
                return wasNe;
            }

            PBaseSet left, right;
            if (op.isEq() || op.isLe() || op.isLt()) {
                left = self;
                right = other;
            } else {
                left = other;
                right = self;
            }
            boolean result = issubsetNode.execute(frame, inliningTarget, left.getDictStorage(), right.getDictStorage());
            if (wasNe) {
                return !result;
            }
            return result;
        }

        @Fallback
        @SuppressWarnings("unused")
        static PNotImplemented doGeneric(Object self, Object other, RichCmpOp op) {
            return PNotImplemented.NOT_IMPLEMENTED;
        }
    }

    @Slot(value = SlotKind.sq_contains, isComplex = true)
    @GenerateNodeFactory
    protected abstract static class BaseContainsNode extends SqContainsBuiltinNode {

        @Specialization
        static boolean contains(VirtualFrame frame, PBaseSet self, Object key,
                        @Bind Node inliningTarget,
                        @Cached ConvertKeyNode conv,
                        @Cached HashingStorageGetItem getItem) {
            return getItem.hasKey(frame, inliningTarget, self.getDictStorage(), conv.execute(inliningTarget, key));
        }
    }

    @GenerateInline
    @GenerateCached(false)
    abstract static class CreateSetNode extends Node {
        public abstract PBaseSet execute(Node inliningTarget, HashingStorage storage, PBaseSet template);

        @Specialization
        static PBaseSet doSet(HashingStorage storage, @SuppressWarnings("unused") PSet template,
                        @Bind PythonLanguage language) {
            return PFactory.createSet(language, storage);
        }

        @Specialization
        static PBaseSet doFrozenSet(HashingStorage storage, @SuppressWarnings("unused") PFrozenSet template,
                        @Bind PythonLanguage language) {
            return PFactory.createFrozenSet(language, storage);
        }
    }

    @Slot(value = SlotKind.nb_subtract, isComplex = true)
    @GenerateNodeFactory
    abstract static class SubNode extends BinaryOpBuiltinNode {
        @Specialization
        static PBaseSet doPBaseSet(@SuppressWarnings("unused") VirtualFrame frame, PBaseSet left, PBaseSet right,
                        @Bind Node inliningTarget,
                        @Cached HashingStorageNodes.HashingStorageDiff diffNode,
                        @Cached CreateSetNode createSetNode) {
            HashingStorage storage = diffNode.execute(frame, inliningTarget, left.getDictStorage(), right.getDictStorage());
            return createSetNode.execute(inliningTarget, storage, left);
        }

        @Fallback
        @SuppressWarnings("unused")
        static Object doOther(Object self, Object other) {
            return PNotImplemented.NOT_IMPLEMENTED;
        }
    }

    @Slot(value = SlotKind.nb_and, isComplex = true)
    @GenerateNodeFactory
    public abstract static class AndNode extends BinaryOpBuiltinNode {

        @Specialization
        static PBaseSet doPBaseSet(VirtualFrame frame, PBaseSet self, PBaseSet other,
                        @Bind Node inliningTarget,
                        @Cached HashingStorageNodes.HashingStorageLen lenNode,
                        @Cached HashingStorageNodes.HashingStorageIntersect intersectNode,
                        @Cached InlinedConditionProfile swapProfile,
                        @Cached CreateSetNode createSetNode) {
            HashingStorage storage1 = self.getDictStorage();
            HashingStorage storage2 = other.getDictStorage();
            // Try to minimize the number of __eq__ calls
            if (swapProfile.profile(inliningTarget, lenNode.execute(inliningTarget, storage2) > lenNode.execute(inliningTarget, storage1))) {
                HashingStorage tmp = storage1;
                storage1 = storage2;
                storage2 = tmp;
            }
            HashingStorage storage = intersectNode.execute(frame, inliningTarget, storage2, storage1);
            return createSetNode.execute(inliningTarget, storage, self);
        }

        @SuppressWarnings("unused")
        @Fallback
        Object doOther(Object self, Object other) {
            return PNotImplemented.NOT_IMPLEMENTED;
        }
    }

    @Slot(value = SlotKind.nb_or, isComplex = true)
    @GenerateNodeFactory
    public abstract static class OrNode extends BinaryOpBuiltinNode {
        @Specialization
        static Object doSet(VirtualFrame frame, PBaseSet self, PBaseSet other,
                        @Bind Node inliningTarget,
                        @Cached HashingStorageCopy copyStorage,
                        @Cached HashingStorageNodes.HashingStorageAddAllToOther addAllToOther,
                        @Cached CreateSetNode createSetNode) {
            HashingStorage resultStorage = copyStorage.execute(inliningTarget, self.getDictStorage());
            resultStorage = addAllToOther.execute(frame, inliningTarget, other.getDictStorage(), resultStorage);
            return createSetNode.execute(inliningTarget, resultStorage, self);
        }

        @SuppressWarnings("unused")
        @Fallback
        Object doOther(Object self, Object other) {
            return PNotImplemented.NOT_IMPLEMENTED;
        }
    }

    @Slot(value = SlotKind.nb_xor, isComplex = true)
    @GenerateNodeFactory
    @ImportStatic(PGuards.class)
    public abstract static class XorNode extends BinaryOpBuiltinNode {

        @Specialization
        static Object doSet(VirtualFrame frame, PBaseSet self, PBaseSet other,
                        @Bind Node inliningTarget,
                        @Cached HashingStorageNodes.HashingStorageXor xorNode,
                        @Cached CreateSetNode createSetNode) {
            // TODO: calls __eq__ wrong number of times compared to CPython (GR-42240)
            HashingStorage storage = xorNode.execute(frame, inliningTarget, self.getDictStorage(), other.getDictStorage());
            return createSetNode.execute(inliningTarget, storage, self);
        }

        @SuppressWarnings("unused")
        @Fallback
        Object doOther(Object self, Object other) {
            return PNotImplemented.NOT_IMPLEMENTED;
        }
    }

    @Builtin(name = "issubset", minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    protected abstract static class BaseIsSubsetNode extends PythonBinaryBuiltinNode {
        @Specialization
        static boolean isSubSetGeneric(VirtualFrame frame, PBaseSet self, Object other,
                        @Bind Node inliningTarget,
                        @Cached HashingCollectionNodes.GetSetStorageNode getSetStorageNode,
                        @Cached IsKeysSubset compareKeys) {
            HashingStorage otherStorage = getSetStorageNode.execute(frame, inliningTarget, other);
            return compareKeys.execute(frame, inliningTarget, self.getDictStorage(), otherStorage);
        }
    }

    @Builtin(name = "issuperset", minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    protected abstract static class BaseIsSupersetNode extends PythonBinaryBuiltinNode {
        @Specialization
        static boolean isSuperSetGeneric(VirtualFrame frame, PBaseSet self, Object other,
                        @Bind Node inliningTarget,
                        @Cached HashingCollectionNodes.GetSetStorageNode getSetStorageNode,
                        @Cached HashingStorageNodes.IsKeysSubset compareKeys) {
            HashingStorage otherStorage = getSetStorageNode.execute(frame, inliningTarget, other);
            return compareKeys.execute(frame, inliningTarget, otherStorage, self.getDictStorage());
        }

    }

    @Builtin(name = "isdisjoint", minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    protected abstract static class BaseIsDisjointNode extends PythonBinaryBuiltinNode {

        @Specialization(guards = "self == other")
        static boolean isDisjointSameObject(PBaseSet self, @SuppressWarnings("unused") PBaseSet other,
                        @Bind Node inliningTarget,
                        @Cached HashingStorageLen lenNode) {
            return lenNode.execute(inliningTarget, self.getDictStorage()) == 0;
        }

        @Specialization(guards = {"self != other", "isBuiltinAnySet(other)"})
        static boolean isDisjointFastPath(VirtualFrame frame, PBaseSet self, PBaseSet other,
                        @Bind Node inliningTarget,
                        @Cached HashingStorageAreDisjoint disjointNode) {
            return disjointNode.execute(frame, inliningTarget, self.getDictStorage(), other.getDictStorage());
        }

        @Fallback
        static boolean isDisjointGeneric(VirtualFrame frame, Object self, Object other,
                        @Bind Node inliningTarget,
                        @Cached HashingStorageGetItem getHashingStorageItem,
                        @Cached PyObjectGetIter getIter,
                        @Cached PyIterNextNode nextNode) {
            HashingStorage selfStorage = ((PBaseSet) self).getDictStorage();
            Object iterator = getIter.execute(frame, inliningTarget, other);
            while (true) {
                Object nextValue;
                try {
                    nextValue = nextNode.execute(frame, inliningTarget, iterator);
                } catch (IteratorExhausted e) {
                    return true;
                }
                if (getHashingStorageItem.hasKey(frame, inliningTarget, selfStorage, nextValue)) {
                    return false;
                }
            }
        }

    }

    @GenerateInline
    @GenerateCached(false)
    @ImportStatic(PGuards.class)
    protected abstract static class ConvertKeyNode extends PNodeWithContext {
        public abstract Object execute(Node inliningTarget, Object key);

        @Specialization(guards = "!isPSet(key)")
        static Object doNotPSet(Object key) {
            return key;
        }

        @Specialization
        static Object doPSet(Node inliningTarget, PSet key,
                        @Cached HashingStorageCopy copyNode,
                        @Cached GetObjectSlotsNode getSlotsNode) {
            // This follows the logic in CPython's setobject.c:set_contains
            // TODO: CPython first tries the search and only if it gets back TypeError and the key
            // type is set, then it transforms it to frozenset. The issue is that the TypeError can
            // come from the tp_hash implementation or from the tp_richcompare also called during
            // the search
            TpSlots slots = getSlotsNode.execute(inliningTarget, key);
            if (slots.tp_hash() == null || slots.tp_hash() == TpSlotHashFun.HASH_NOT_IMPLEMENTED) {
                return PFactory.createFrozenSet(PythonLanguage.get(inliningTarget), copyNode.execute(inliningTarget, key.getDictStorage()));
            } else {
                return key;
            }
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
