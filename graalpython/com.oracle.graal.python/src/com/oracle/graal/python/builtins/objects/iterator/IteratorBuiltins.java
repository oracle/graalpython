/*
 * Copyright (c) 2017, 2023, Oracle and/or its affiliates.
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
package com.oracle.graal.python.builtins.objects.iterator;

import static com.oracle.graal.python.builtins.PythonBuiltinClassType.RuntimeError;
import static com.oracle.graal.python.nodes.BuiltinNames.T_ITER;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___ITER__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___LENGTH_HINT__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___NEXT__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___REDUCE__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___SETSTATE__;
import static com.oracle.graal.python.nodes.StringLiterals.T_EMPTY_STRING;
import static com.oracle.graal.python.util.PythonUtils.TS_ENCODING;

import java.math.BigInteger;
import java.util.List;

import com.oracle.graal.python.builtins.Builtin;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.array.ArrayNodes;
import com.oracle.graal.python.builtins.objects.array.PArray;
import com.oracle.graal.python.builtins.objects.common.HashingStorage;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodes.HashingStorageIterator;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodes.HashingStorageIteratorKey;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodes.HashingStorageIteratorNext;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodes.HashingStorageIteratorValue;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodes.HashingStorageLen;
import com.oracle.graal.python.builtins.objects.common.SequenceNodes;
import com.oracle.graal.python.builtins.objects.common.SequenceStorageNodes;
import com.oracle.graal.python.builtins.objects.dict.PDictView;
import com.oracle.graal.python.builtins.objects.dict.PHashingStorageIterator;
import com.oracle.graal.python.builtins.objects.ints.PInt;
import com.oracle.graal.python.builtins.objects.list.PList;
import com.oracle.graal.python.builtins.objects.module.PythonModule;
import com.oracle.graal.python.builtins.objects.tuple.PTuple;
import com.oracle.graal.python.lib.PyNumberAsSizeNode;
import com.oracle.graal.python.lib.PyObjectGetAttr;
import com.oracle.graal.python.lib.PyObjectSizeNode;
import com.oracle.graal.python.lib.PySequenceGetItemNode;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.builtins.PythonBinaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonUnaryBuiltinNode;
import com.oracle.graal.python.nodes.object.BuiltinClassProfiles.IsBuiltinObjectProfile;
import com.oracle.graal.python.nodes.util.CastToJavaBigIntegerNode;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.graal.python.runtime.object.PythonObjectFactory;
import com.oracle.graal.python.runtime.sequence.storage.SequenceStorage;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Exclusive;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.GenerateCached;
import com.oracle.truffle.api.dsl.GenerateInline;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.profiles.InlinedConditionProfile;
import com.oracle.truffle.api.profiles.InlinedExactClassProfile;
import com.oracle.truffle.api.strings.TruffleString;

@CoreFunctions(extendClasses = {PythonBuiltinClassType.PIterator, PythonBuiltinClassType.PArrayIterator,
                PythonBuiltinClassType.PDictItemIterator, PythonBuiltinClassType.PDictReverseItemIterator,
                PythonBuiltinClassType.PDictKeyIterator, PythonBuiltinClassType.PDictReverseKeyIterator,
                PythonBuiltinClassType.PDictValueIterator, PythonBuiltinClassType.PDictReverseValueIterator})
public final class IteratorBuiltins extends PythonBuiltins {

    /*
     * "extendClasses" only needs one of the set of Java classes that are mapped to the Python
     * class.
     */

    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return IteratorBuiltinsFactory.getFactories();
    }

    @Builtin(name = J___NEXT__, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class NextNode extends PythonUnaryBuiltinNode {

        public static final Object STOP_MARKER = new Object();
        private final boolean throwStopIteration;

        NextNode() {
            this.throwStopIteration = true;
        }

        NextNode(boolean throwStopIteration) {
            this.throwStopIteration = throwStopIteration;
        }

        public abstract Object execute(VirtualFrame frame, PBuiltinIterator iterator);

        private Object stopIteration(PBuiltinIterator self) {
            self.setExhausted();
            if (throwStopIteration) {
                throw raiseStopIteration();
            } else {
                return STOP_MARKER;
            }
        }

        @Specialization(guards = "self.isExhausted()")
        Object exhausted(@SuppressWarnings("unused") PBuiltinIterator self) {
            if (throwStopIteration) {
                throw raiseStopIteration();
            } else {
                return STOP_MARKER;
            }
        }

        @Specialization(guards = "!self.isExhausted()")
        @SuppressWarnings("truffle-static-method")
        Object next(PArrayIterator self,
                        @Bind("this") Node inliningTarget,
                        @Cached InlinedExactClassProfile itemTypeProfile,
                        @Cached ArrayNodes.GetValueNode getValueNode) {
            PArray array = self.array;
            if (self.getIndex() < array.getLength()) {
                return itemTypeProfile.profile(inliningTarget, getValueNode.execute(inliningTarget, array, self.index++));
            }
            return stopIteration(self);
        }

        @Specialization(guards = "!self.isExhausted()")
        Object next(PIntegerSequenceIterator self) {
            if (self.getIndex() < self.sequence.length()) {
                return self.sequence.getIntItemNormalized(self.index++);
            }
            return stopIteration(self);
        }

        @Specialization(guards = "!self.isExhausted()")
        Object next(PObjectSequenceIterator self) {
            if (self.getIndex() < self.sequence.length()) {
                return self.sequence.getItemNormalized(self.index++);
            }
            return stopIteration(self);
        }

        @Specialization(guards = "!self.isExhausted()")
        Object next(PIntRangeIterator self,
                        @Bind("this") Node inliningTarget,
                        @Shared("next") @Cached InlinedConditionProfile profile) {
            if (profile.profile(inliningTarget, self.hasNextInt())) {
                return self.nextInt();
            }
            return stopIteration(self);
        }

        @Specialization(guards = "!self.isExhausted()")
        Object next(PBigRangeIterator self) {
            if (self.hasNextBigInt()) {
                return factory().createInt(self.nextBigInt());
            }
            return stopIteration(self);
        }

        @Specialization(guards = "!self.isExhausted()")
        Object next(PDoubleSequenceIterator self) {
            if (self.getIndex() < self.sequence.length()) {
                return self.sequence.getDoubleItemNormalized(self.index++);
            }
            return stopIteration(self);
        }

        @Specialization(guards = "!self.isExhausted()")
        Object next(PLongSequenceIterator self) {
            if (self.getIndex() < self.sequence.length()) {
                return self.sequence.getLongItemNormalized(self.index++);
            }
            return stopIteration(self);
        }

        @Specialization(guards = "!self.isExhausted()")
        Object next(PStringIterator self,
                        @Cached TruffleString.CodePointLengthNode codePointLengthNode,
                        @Cached TruffleString.SubstringNode substringNode) {
            if (self.getIndex() < codePointLengthNode.execute(self.value, TS_ENCODING)) {
                return substringNode.execute(self.value, self.index++, 1, TS_ENCODING, false);
            }
            return stopIteration(self);
        }

        @Specialization(guards = "!self.isExhausted()")
        @SuppressWarnings("truffle-static-method")
        Object nextHashingStorageIter(PHashingStorageIterator self,
                        @Bind("this") Node inliningTarget,
                        @Exclusive @Cached InlinedConditionProfile sizeChanged,
                        @Cached HashingStorageLen lenNode,
                        @Cached HashingStorageIteratorNext nextNode,
                        @Cached PHashingStorageIteratorNextValue itValueNode,
                        @Shared("next") @Cached InlinedConditionProfile profile) {
            HashingStorage storage = self.getHashingStorage();
            final HashingStorageIterator it = self.getIterator();
            if (profile.profile(inliningTarget, nextNode.execute(inliningTarget, storage, it))) {
                if (sizeChanged.profile(inliningTarget, self.checkSizeChanged(inliningTarget, lenNode))) {
                    String name = PBaseSetIterator.isInstance(self) ? "Set" : "dictionary";
                    throw raise(RuntimeError, ErrorMessages.CHANGED_SIZE_DURING_ITERATION, name);
                }
                self.index++;
                return itValueNode.execute(inliningTarget, self, storage, it);
            }
            return stopIteration(self);
        }

        @Specialization(guards = {"!self.isExhausted()", "self.isPSequence()"})
        @SuppressWarnings("truffle-static-method")
        Object next(PSequenceIterator self,
                        @Bind("this") Node inliningTarget,
                        @Cached SequenceNodes.GetSequenceStorageNode getStorage,
                        @Cached("createNotNormalized()") SequenceStorageNodes.GetItemNode getItemNode) {
            SequenceStorage s = getStorage.execute(inliningTarget, self.getPSequence());
            if (self.getIndex() < s.length()) {
                return getItemNode.execute(s, self.index++);
            }
            return stopIteration(self);
        }

        @Specialization(guards = {"!self.isExhausted()", "!self.isPSequence()"})
        @SuppressWarnings("truffle-static-method")
        Object next(VirtualFrame frame, PSequenceIterator self,
                        @Bind("this") Node inliningTarget,
                        @Cached PySequenceGetItemNode getItem,
                        @Cached IsBuiltinObjectProfile profile) {
            try {
                /*
                 * This must use PySequence_GetItem and not any other get item nodes. The reason is
                 * that other get item nodes call mp_subscript. Some extensions iterate self in
                 * their mp_getitem which could result in infinite recursion.
                 *
                 * Example psycopg2:column_type.c:column_subscript
                 */
                return getItem.execute(frame, self.getObject(), self.index++);
            } catch (PException e) {
                e.expectIndexError(inliningTarget, profile);
                return stopIteration(self);
            }
        }

        @GenerateInline
        @GenerateCached(false)
        abstract static class PHashingStorageIteratorNextValue extends Node {
            abstract Object execute(Node inliningTarget, PHashingStorageIterator pyIter, HashingStorage storage, HashingStorageIterator it);

            @Specialization
            static Object doDictValue(Node inliningTarget, @SuppressWarnings("unused") PDictView.PDictValueIterator self, HashingStorage storage, HashingStorageIterator it,
                            @Shared("val") @Cached HashingStorageIteratorValue itValueNode) {
                return itValueNode.execute(inliningTarget, storage, it);
            }

            @Specialization
            static Object doDictKey(Node inliningTarget, @SuppressWarnings("unused") PDictView.PDictKeyIterator self, HashingStorage storage, HashingStorageIterator it,
                            @Shared("key") @Cached HashingStorageIteratorKey itKeyNode) {
                return itKeyNode.execute(inliningTarget, storage, it);
            }

            @Specialization
            static PTuple doDictItem(Node inliningTarget, @SuppressWarnings("unused") PDictView.PDictItemIterator self, HashingStorage storage, HashingStorageIterator it,
                            @Shared("val") @Cached HashingStorageIteratorValue itValueNode,
                            @Shared("key") @Cached HashingStorageIteratorKey itKeyNode,
                            @Cached(inline = false) PythonObjectFactory factory) {
                return factory.createTuple(new Object[]{itKeyNode.execute(inliningTarget, storage, it), itValueNode.execute(inliningTarget, storage, it)});
            }

            @Specialization
            static Object doSetKey(Node inliningTarget, @SuppressWarnings("unused") PBaseSetIterator self, HashingStorage storage, HashingStorageIterator it,
                            @Shared("key") @Cached HashingStorageIteratorKey itKeyNode) {
                return itKeyNode.execute(inliningTarget, storage, it);
            }
        }
    }

    @Builtin(name = J___ITER__, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class IterNode extends PythonUnaryBuiltinNode {
        @Specialization
        static Object doGeneric(Object self) {
            return self;
        }
    }

    @Builtin(name = J___LENGTH_HINT__, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class LengthHintNode extends PythonUnaryBuiltinNode {

        @Specialization(guards = "self.isExhausted()")
        public static int exhausted(@SuppressWarnings("unused") PBuiltinIterator self) {
            return 0;
        }

        @Specialization
        public static int lengthHint(PArrayIterator self) {
            return self.array.getLength() - self.getIndex();
        }

        @Specialization(guards = "!self.isExhausted()")
        public static int lengthHint(@SuppressWarnings({"unused"}) VirtualFrame frame, PDictView.PBaseDictIterator self,
                        @Bind("this") Node inliningTarget,
                        @Shared @Cached HashingStorageLen lenNode,
                        @Shared @Cached InlinedConditionProfile profile) {
            if (profile.profile(inliningTarget, self.checkSizeChanged(inliningTarget, lenNode))) {
                return 0;
            }
            return self.getSize() - self.getIndex();
        }

        @Specialization(guards = "!self.isExhausted()")
        public static int lengthHint(PIntegerSequenceIterator self) {
            int len = self.sequence.length() - self.getIndex();
            return len < 0 ? 0 : len;
        }

        @Specialization(guards = "!self.isExhausted()")
        public static int lengthHint(PObjectSequenceIterator self) {
            int len = self.sequence.length() - self.getIndex();
            return len < 0 ? 0 : len;
        }

        @Specialization(guards = "!self.isExhausted()")
        public static int lengthHint(PIntRangeIterator self) {
            return self.getRemainingLength();
        }

        @Specialization(guards = "!self.isExhausted()")
        public Object lengthHint(PBigRangeIterator self) {
            return factory().createInt(self.getRemainingLength());
        }

        @Specialization(guards = "!self.isExhausted()")
        public static int lengthHint(PDoubleSequenceIterator self) {
            int len = self.sequence.length() - self.getIndex();
            return len < 0 ? 0 : len;
        }

        @Specialization(guards = "!self.isExhausted()")
        public static int lengthHint(PLongSequenceIterator self) {
            int len = self.sequence.length() - self.getIndex();
            return len < 0 ? 0 : len;
        }

        @Specialization(guards = "!self.isExhausted()")
        public static int lengthHint(PBaseSetIterator self,
                        @Bind("this") Node inliningTarget,
                        @Shared @Cached HashingStorageLen lenNode,
                        @Shared @Cached InlinedConditionProfile profile) {
            int size = self.getSize();
            final int lenSet = lenNode.execute(inliningTarget, self.getHashingStorage());
            if (profile.profile(inliningTarget, lenSet != size)) {
                return 0;
            }
            int len = size - self.getIndex();
            return len < 0 ? 0 : len;
        }

        @Specialization(guards = "!self.isExhausted()")
        public static int lengthHint(PStringIterator self,
                        @Cached TruffleString.CodePointLengthNode codePointLengthNode) {
            int len = codePointLengthNode.execute(self.value, TS_ENCODING) - self.getIndex();
            return len < 0 ? 0 : len;
        }

        @Specialization(guards = {"!self.isExhausted()", "self.isPSequence()"})
        public static int lengthHint(PSequenceIterator self,
                        @Bind("this") Node inliningTarget,
                        @Cached SequenceNodes.LenNode lenNode) {
            int len = lenNode.execute(inliningTarget, self.getPSequence()) - self.getIndex();
            return len < 0 ? 0 : len;
        }

        @Specialization(guards = {"!self.isExhausted()", "!self.isPSequence()"})
        public static int lengthHint(VirtualFrame frame, PSequenceIterator self,
                        @Bind("this") Node inliningTarget,
                        @Cached PyObjectSizeNode sizeNode) {
            int len = sizeNode.execute(frame, inliningTarget, self.getObject()) - self.getIndex();
            return len < 0 ? 0 : len;
        }
    }

    @Builtin(name = J___REDUCE__, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class ReduceNode extends PythonUnaryBuiltinNode {
        @Child PyObjectGetAttr getAttrNode;

        @Specialization
        public Object reduce(VirtualFrame frame, PArrayIterator self,
                        @Bind("this") Node inliningTarget,
                        @Cached InlinedConditionProfile exhaustedProfile) {
            PythonContext context = PythonContext.get(this);
            if (!exhaustedProfile.profile(inliningTarget, self.isExhausted())) {
                return reduceInternal(frame, self.array, self.getIndex(), context);
            } else {
                return reduceInternal(frame, factory().createEmptyTuple(), context);
            }
        }

        @Specialization
        public Object reduce(VirtualFrame frame, PHashingStorageIterator self,
                        @Cached SequenceStorageNodes.CreateStorageFromIteratorNode storageNode) {
            int index = self.index;
            boolean isExhausted = self.isExhausted();
            int state = self.getIterator().getState();
            PList list = factory().createList(storageNode.execute(frame, self));
            self.getIterator().setState(state);
            self.setExhausted(isExhausted);
            self.index = index;
            return reduceInternal(frame, list, PythonContext.get(this));
        }

        @Specialization
        public Object reduce(VirtualFrame frame, PIntegerSequenceIterator self) {
            PythonContext context = PythonContext.get(this);
            if (self.isExhausted()) {
                return reduceInternal(frame, factory().createList(), null, context);
            }
            return reduceInternal(frame, self.getObject(), self.getIndex(), context);
        }

        @Specialization
        public Object reduce(VirtualFrame frame, PPrimitiveIterator self) {
            PythonContext context = PythonContext.get(this);
            if (self.isExhausted()) {
                return reduceInternal(frame, factory().createList(), null, context);
            }
            return reduceInternal(frame, self.getObject(), self.getIndex(), context);
        }

        @Specialization
        public Object reduce(VirtualFrame frame, PStringIterator self) {
            PythonContext context = PythonContext.get(this);
            if (self.isExhausted()) {
                return reduceInternal(frame, T_EMPTY_STRING, null, context);
            }
            return reduceInternal(frame, self.value, self.getIndex(), context);
        }

        @Specialization
        public Object reduce(VirtualFrame frame, PIntRangeIterator self) {
            int start = self.getStart();
            int stop = self.getStop();
            int step = self.getStep();
            int len = self.getLen();
            return reduceInternal(frame, factory().createIntRange(start, stop, step, len), self.getIndex(), PythonContext.get(this));
        }

        @Specialization
        public Object reduce(VirtualFrame frame, PBigRangeIterator self) {
            PInt start = self.getStart();
            PInt stop = self.getStop();
            PInt step = self.getStep();
            PInt len = self.getLen();
            return reduceInternal(frame, factory().createBigRange(start, stop, step, len), self.getLongIndex(factory()), PythonContext.get(this));
        }

        @Specialization(guards = "self.isPSequence()")
        public Object reduce(VirtualFrame frame, PSequenceIterator self) {
            PythonContext context = PythonContext.get(this);
            if (self.isExhausted()) {
                return reduceInternal(frame, factory().createTuple(new Object[0]), null, context);
            }
            return reduceInternal(frame, self.getPSequence(), self.getIndex(), context);
        }

        @Specialization(guards = "!self.isPSequence()")
        public Object reduceNonSeq(@SuppressWarnings({"unused"}) VirtualFrame frame, PSequenceIterator self) {
            PythonContext context = PythonContext.get(this);
            if (!self.isExhausted()) {
                return reduceInternal(frame, self.getObject(), self.getIndex(), context);
            } else {
                return reduceInternal(frame, factory().createTuple(new Object[0]), null, context);
            }
        }

        private PTuple reduceInternal(VirtualFrame frame, Object arg, PythonContext context) {
            return reduceInternal(frame, arg, null, context);
        }

        private PTuple reduceInternal(VirtualFrame frame, Object arg, Object state, PythonContext context) {
            PythonModule builtins = context.getBuiltins();
            Object iter = getGetAttrNode().executeCached(frame, builtins, T_ITER);
            PTuple args = factory().createTuple(new Object[]{arg});
            // callable, args, state (optional)
            if (state != null) {
                return factory().createTuple(new Object[]{iter, args, state});
            } else {
                return factory().createTuple(new Object[]{iter, args});
            }
        }

        private PyObjectGetAttr getGetAttrNode() {
            if (getAttrNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                getAttrNode = insert(PyObjectGetAttr.create());
            }
            return getAttrNode;
        }
    }

    @Builtin(name = J___SETSTATE__, minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    public abstract static class SetStateNode extends PythonBinaryBuiltinNode {
        @Specialization
        @TruffleBoundary
        public static Object reduce(PBigRangeIterator self, Object index,
                        @Bind("this") Node inliningTarget,
                        @Cached CastToJavaBigIntegerNode castToJavaBigIntegerNode) {
            BigInteger idx = castToJavaBigIntegerNode.execute(inliningTarget, index);
            if (idx.compareTo(BigInteger.ZERO) < 0) {
                idx = BigInteger.ZERO;
            }
            self.setLongIndex(idx);
            return PNone.NONE;
        }

        @Specialization(guards = "!isPBigRangeIterator(self)")
        public static Object reduce(VirtualFrame frame, PBuiltinIterator self, Object index,
                        @Bind("this") Node inliningTarget,
                        @Cached PyNumberAsSizeNode asSizeNode) {
            int idx = asSizeNode.executeExact(frame, inliningTarget, index);
            if (idx < 0) {
                idx = 0;
            }
            self.index = idx;
            return PNone.NONE;
        }

        protected static boolean isPBigRangeIterator(Object obj) {
            return obj instanceof PBigRangeIterator;
        }
    }
}
