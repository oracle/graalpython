/*
 * Copyright (c) 2020, 2022, Oracle and/or its affiliates. All rights reserved.
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

import static com.oracle.graal.python.nodes.SpecialAttributeNames.T___DICT__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___CONTAINS__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___EQ__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___GE__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___GT__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___ITER__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___LEN__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___LE__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___LT__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___REDUCE__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___REPR__;
import static com.oracle.graal.python.nodes.StringLiterals.T_COMMA_SPACE;
import static com.oracle.graal.python.nodes.StringLiterals.T_ELLIPSIS_IN_PARENS;
import static com.oracle.graal.python.nodes.StringLiterals.T_EMPTY_PARENS;
import static com.oracle.graal.python.nodes.StringLiterals.T_LBRACE;
import static com.oracle.graal.python.nodes.StringLiterals.T_LPAREN;
import static com.oracle.graal.python.nodes.StringLiterals.T_RBRACE;
import static com.oracle.graal.python.nodes.StringLiterals.T_RPAREN;
import static com.oracle.graal.python.util.PythonUtils.TS_ENCODING;

import java.util.Iterator;
import java.util.List;

import com.oracle.graal.python.builtins.Builtin;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.PNotImplemented;
import com.oracle.graal.python.builtins.objects.common.HashingCollectionNodes;
import com.oracle.graal.python.builtins.objects.common.HashingStorage;
import com.oracle.graal.python.builtins.objects.common.HashingStorageLibrary;
import com.oracle.graal.python.builtins.objects.function.PArguments;
import com.oracle.graal.python.builtins.objects.function.PArguments.ThreadState;
import com.oracle.graal.python.builtins.objects.tuple.PTuple;
import com.oracle.graal.python.builtins.objects.type.SpecialMethodSlot;
import com.oracle.graal.python.builtins.objects.type.TypeNodes;
import com.oracle.graal.python.lib.PyObjectGetIter;
import com.oracle.graal.python.lib.PyObjectLookupAttr;
import com.oracle.graal.python.lib.PyObjectReprAsTruffleStringNode;
import com.oracle.graal.python.nodes.PGuards;
import com.oracle.graal.python.nodes.PNodeWithContext;
import com.oracle.graal.python.nodes.attributes.LookupCallableSlotInMRONode;
import com.oracle.graal.python.nodes.control.GetNextNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.builtins.PythonBinaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonUnaryBuiltinNode;
import com.oracle.graal.python.nodes.object.GetClassNode;
import com.oracle.graal.python.nodes.object.IsBuiltinClassProfile;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.graal.python.runtime.object.PythonObjectFactory;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.profiles.ConditionProfile;
import com.oracle.truffle.api.strings.TruffleStringBuilder;

@CoreFunctions(extendClasses = {PythonBuiltinClassType.PSet, PythonBuiltinClassType.PFrozenSet})
public final class BaseSetBuiltins extends PythonBuiltins {

    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return BaseSetBuiltinsFactory.getFactories();
    }

    @Builtin(name = J___REPR__, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class BaseReprNode extends PythonUnaryBuiltinNode {
        private static void fillItems(VirtualFrame frame, TruffleStringBuilder sb, PyObjectReprAsTruffleStringNode repr, HashingStorageLibrary.HashingStorageIterator<Object> iter,
                        TruffleStringBuilder.AppendStringNode appendStringNode) {
            boolean first = true;
            appendStringNode.execute(sb, T_LBRACE);
            while (iter.hasNext()) {
                if (first) {
                    first = false;
                } else {
                    appendStringNode.execute(sb, T_COMMA_SPACE);
                }
                appendStringNode.execute(sb, repr.execute(frame, iter.next()));
            }
            appendStringNode.execute(sb, T_RBRACE);
        }

        @Specialization(limit = "3")
        public static Object repr(VirtualFrame frame, PBaseSet self,
                        @Cached PyObjectReprAsTruffleStringNode repr,
                        @Cached TypeNodes.GetNameNode getNameNode,
                        @Cached GetClassNode getClassNode,
                        @CachedLibrary("self.getDictStorage()") HashingStorageLibrary hlib,
                        @Cached TruffleStringBuilder.AppendStringNode appendStringNode,
                        @Cached TruffleStringBuilder.ToStringNode toStringNode) {
            TruffleStringBuilder sb = TruffleStringBuilder.create(TS_ENCODING);
            Object clazz = getClassNode.execute(self);
            PythonContext ctxt = PythonContext.get(getNameNode);
            int len = hlib.length(self.getDictStorage());
            if (len == 0) {
                appendStringNode.execute(sb, getNameNode.execute(clazz));
                appendStringNode.execute(sb, T_EMPTY_PARENS);
                return toStringNode.execute(sb);
            }
            if (!ctxt.reprEnter(self)) {
                appendStringNode.execute(sb, getNameNode.execute(clazz));
                appendStringNode.execute(sb, T_ELLIPSIS_IN_PARENS);
                return toStringNode.execute(sb);
            }
            try {
                HashingStorageLibrary.HashingStorageIterator<Object> iter = hlib.keys(self.getDictStorage()).iterator();
                boolean showType = clazz != PythonBuiltinClassType.PSet;
                if (showType) {
                    appendStringNode.execute(sb, getNameNode.execute(clazz));
                    appendStringNode.execute(sb, T_LPAREN);
                }
                fillItems(frame, sb, repr, iter, appendStringNode);
                if (showType) {
                    appendStringNode.execute(sb, T_RPAREN);
                }
                return toStringNode.execute(sb);
            } finally {
                ctxt.reprLeave(self);
            }
        }
    }

    @Builtin(name = J___ITER__, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    protected abstract static class BaseIterNode extends PythonUnaryBuiltinNode {
        @Specialization(limit = "1")
        Object doBaseSet(PBaseSet self,
                        @CachedLibrary("self.getDictStorage()") HashingStorageLibrary lib) {
            return factory().createBaseSetIterator(self, lib.keys(self.getDictStorage()).iterator(), lib.length(self.getDictStorage()));
        }
    }

    @Builtin(name = J___LEN__, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    protected abstract static class BaseLenNode extends PythonUnaryBuiltinNode {
        @Specialization(limit = "3")
        public static int len(PBaseSet self,
                        @CachedLibrary("self.getDictStorage()") HashingStorageLibrary lib) {
            return lib.length(self.getDictStorage());
        }
    }

    @Builtin(name = J___REDUCE__, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    protected abstract static class BaseReduceNode extends PythonUnaryBuiltinNode {

        @Specialization(limit = "3")
        public Object reduce(VirtualFrame frame, PBaseSet self,
                        @CachedLibrary("self.getDictStorage()") HashingStorageLibrary lib,
                        @Cached GetClassNode getClassNode,
                        @Cached PyObjectLookupAttr lookup) {
            HashingStorage storage = self.getDictStorage();
            int len = lib.length(storage);
            Iterator<Object> keys = lib.keys(storage).iterator();
            Object[] keysArray = new Object[len];
            for (int i = 0; i < len; i++) {
                keysArray[i] = keys.next();
            }
            PTuple contents = factory().createTuple(new Object[]{factory().createList(keysArray)});
            Object dict = lookup.execute(frame, self, T___DICT__);
            if (dict == PNone.NO_VALUE) {
                dict = PNone.NONE;
            }
            return factory().createTuple(new Object[]{getClassNode.execute(self), contents, dict});
        }
    }

    @Builtin(name = J___EQ__, minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    protected abstract static class BaseEqNode extends PythonBinaryBuiltinNode {
        @Specialization(limit = "3")
        static boolean doSetSameType(VirtualFrame frame, PBaseSet self, PBaseSet other,
                        @Cached ConditionProfile hasFrame,
                        @CachedLibrary("self.getDictStorage()") HashingStorageLibrary lib) {
            return lib.compareKeysWithFrame(self.getDictStorage(), other.getDictStorage(), hasFrame, frame) == 0;
        }

        @Fallback
        @SuppressWarnings("unused")
        static PNotImplemented doGeneric(Object self, Object other) {
            return PNotImplemented.NOT_IMPLEMENTED;
        }
    }

    @Builtin(name = J___CONTAINS__, minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    protected abstract static class BaseContainsNode extends PythonBinaryBuiltinNode {

        @Specialization(limit = "3")
        static boolean contains(VirtualFrame frame, PBaseSet self, Object key,
                        @Cached ConditionProfile hasFrame,
                        @Cached ConvertKeyNode conv,
                        @CachedLibrary("self.getDictStorage()") HashingStorageLibrary lib) {
            return lib.hasKeyWithFrame(self.getDictStorage(), conv.execute(key), hasFrame, frame);
        }
    }

    @Builtin(name = "issubset", minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    protected abstract static class BaseIsSubsetNode extends PythonBinaryBuiltinNode {
        @Specialization(limit = "3")
        static boolean isSubSet(VirtualFrame frame, PBaseSet self, PBaseSet other,
                        @Cached ConditionProfile hasFrame,
                        @CachedLibrary("self.getDictStorage()") HashingStorageLibrary lib) {
            return lib.compareKeysWithFrame(self.getDictStorage(), other.getDictStorage(), hasFrame, frame) <= 0;
        }

        @Specialization(replaces = "isSubSet", limit = "3")
        static boolean isSubSetGeneric(VirtualFrame frame, PBaseSet self, Object other,
                        @Cached HashingCollectionNodes.GetHashingStorageNode getHashingStorageNode,
                        @Cached ConditionProfile hasFrame,
                        @CachedLibrary("self.getDictStorage()") HashingStorageLibrary lib) {
            HashingStorage otherSet = getHashingStorageNode.execute(frame, other);
            return lib.compareKeysWithFrame(self.getDictStorage(), otherSet, hasFrame, frame) <= 0;
        }
    }

    @Builtin(name = "issuperset", minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    protected abstract static class BaseIsSupersetNode extends PythonBinaryBuiltinNode {
        @Specialization(limit = "3")
        static boolean isSuperSet(VirtualFrame frame, PBaseSet self, PBaseSet other,
                        @Cached ConditionProfile hasFrame,
                        @CachedLibrary("other.getDictStorage()") HashingStorageLibrary lib) {
            return lib.compareKeysWithFrame(other.getDictStorage(), self.getDictStorage(), hasFrame, frame) <= 0;
        }

        @Specialization(replaces = "isSuperSet")
        static boolean isSuperSetGeneric(VirtualFrame frame, PBaseSet self, Object other,
                        @Cached HashingCollectionNodes.GetHashingStorageNode getHashingStorageNode,
                        @Cached ConditionProfile hasFrame,
                        @CachedLibrary(limit = "3") HashingStorageLibrary lib) {
            HashingStorage otherSet = getHashingStorageNode.execute(frame, other);
            return lib.compareKeysWithFrame(otherSet, self.getDictStorage(), hasFrame, frame) <= 0;
        }

    }

    @Builtin(name = "isdisjoint", minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    protected abstract static class BaseIsDisjointNode extends PythonBinaryBuiltinNode {

        @Specialization(guards = "self == other", limit = "3")
        static boolean isDisjointSameObject(PBaseSet self, @SuppressWarnings("unused") PBaseSet other,
                        @CachedLibrary("self.getDictStorage()") HashingStorageLibrary lib) {
            return lib.length(self.getDictStorage()) == 0;
        }

        @Specialization(guards = {"self != other", "cannotBeOverridden(other, getClassNode)"}, limit = "3")
        static boolean isDisjointFastPath(VirtualFrame frame, PBaseSet self, PBaseSet other,
                        @Cached ConditionProfile hasFrame,
                        @CachedLibrary("self.getDictStorage()") HashingStorageLibrary selfLib,
                        @SuppressWarnings("unused") @Cached GetClassNode getClassNode) {
            ThreadState state = PArguments.getThreadStateOrNull(frame, hasFrame);
            return selfLib.isDisjointWithState(self.getDictStorage(), other.getDictStorage(), state);
        }

        @Specialization(guards = {"self != other", "!cannotBeOverridden(other, getClassNode)"}, limit = "3")
        static boolean isDisjointWithOtherSet(VirtualFrame frame, PBaseSet self, PBaseSet other,
                        @Cached ConditionProfile hasFrame,
                        @CachedLibrary("self.getDictStorage()") HashingStorageLibrary selfLib,
                        @SuppressWarnings("unused") @Cached GetClassNode getClassNode,
                        @Cached PyObjectGetIter getIter,
                        @Cached GetNextNode getNextNode,
                        @Cached IsBuiltinClassProfile errorProfile) {
            return isDisjointGeneric(frame, self, other, hasFrame, selfLib, getIter, getNextNode, errorProfile);
        }

        @Specialization(guards = {"!isAnySet(other)"}, limit = "3")
        static boolean isDisjointGeneric(VirtualFrame frame, PBaseSet self, Object other,
                        @Cached ConditionProfile hasFrame,
                        @CachedLibrary("self.getDictStorage()") HashingStorageLibrary selfLib,
                        @Cached PyObjectGetIter getIter,
                        @Cached GetNextNode getNextNode,
                        @Cached IsBuiltinClassProfile errorProfile) {
            ThreadState state = PArguments.getThreadStateOrNull(frame, hasFrame);
            HashingStorage selfStorage = self.getDictStorage();
            Object iterator = getIter.execute(frame, other);
            while (true) {
                try {
                    Object nextValue = getNextNode.execute(frame, iterator);
                    if (selfLib.hasKeyWithState(selfStorage, nextValue, state)) {
                        return false;
                    }
                } catch (PException e) {
                    e.expectStopIteration(errorProfile);
                    return true;
                }
            }
        }

    }

    @Builtin(name = J___LE__, minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    protected abstract static class BaseLessEqualNode extends PythonBinaryBuiltinNode {
        @Specialization(limit = "3")
        static boolean doLE(VirtualFrame frame, PBaseSet self, PBaseSet other,
                        @Cached ConditionProfile hasFrame,
                        @CachedLibrary("self.getDictStorage()") HashingStorageLibrary lib) {
            return lib.compareKeysWithFrame(self.getDictStorage(), other.getDictStorage(), hasFrame, frame) <= 0;
        }

        @Fallback
        @SuppressWarnings("unused")
        static PNotImplemented doNotImplemented(Object self, Object other) {
            return PNotImplemented.NOT_IMPLEMENTED;
        }
    }

    @Builtin(name = J___GE__, minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    protected abstract static class BaseGreaterEqualNode extends PythonBinaryBuiltinNode {
        @Specialization(limit = "3")
        static boolean doGE(VirtualFrame frame, PBaseSet self, PBaseSet other,
                        @Cached ConditionProfile hasFrame,
                        @CachedLibrary("other.getDictStorage()") HashingStorageLibrary lib) {
            return lib.compareKeysWithFrame(other.getDictStorage(), self.getDictStorage(), hasFrame, frame) <= 0;
        }

        @Fallback
        @SuppressWarnings("unused")
        static PNotImplemented doNotImplemented(Object self, Object other) {
            return PNotImplemented.NOT_IMPLEMENTED;
        }
    }

    @Builtin(name = J___LT__, minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    protected abstract static class BaseLessThanNode extends PythonBinaryBuiltinNode {

        @Specialization
        static boolean isLessThan(VirtualFrame frame, PBaseSet self, PBaseSet other,
                        @CachedLibrary(limit = "3") HashingStorageLibrary hlib,
                        @Cached ConditionProfile hasFrameProfile,
                        @Cached ConditionProfile sizeProfile) {
            final int len1 = hlib.length(self.getDictStorage());
            final int len2 = hlib.length(other.getDictStorage());
            if (sizeProfile.profile(len1 >= len2)) {
                return false;
            }
            return BaseLessEqualNode.doLE(frame, self, other, hasFrameProfile, hlib);
        }

        @Fallback
        @SuppressWarnings("unused")
        static PNotImplemented doNotImplemented(Object self, Object other) {
            return PNotImplemented.NOT_IMPLEMENTED;
        }
    }

    @Builtin(name = J___GT__, minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    protected abstract static class BaseGreaterThanNode extends PythonBinaryBuiltinNode {

        @Specialization
        static boolean isGreaterThan(VirtualFrame frame, PBaseSet self, PBaseSet other,
                        @CachedLibrary(limit = "3") HashingStorageLibrary hlib,
                        @Cached ConditionProfile hasFrameProfile,
                        @Cached ConditionProfile sizeProfile) {
            final int len1 = hlib.length(self.getDictStorage());
            final int len2 = hlib.length(other.getDictStorage());
            if (sizeProfile.profile(len1 <= len2)) {
                return false;
            }
            return BaseGreaterEqualNode.doGE(frame, self, other, hasFrameProfile, hlib);
        }

        @Fallback
        @SuppressWarnings("unused")
        static PNotImplemented doNotImplemented(Object self, Object other) {
            return PNotImplemented.NOT_IMPLEMENTED;
        }
    }

    @ImportStatic({PGuards.class, SpecialMethodSlot.class})
    protected abstract static class ConvertKeyNode extends PNodeWithContext {
        public abstract Object execute(Object key);

        @Specialization(guards = "!isPSet(key)")
        static Object doNotPSet(Object key) {
            return key;
        }

        @Specialization
        static Object doPSet(PSet key,
                        @CachedLibrary(limit = "2") HashingStorageLibrary hlib,
                        @Cached GetClassNode getClassNode,
                        @Cached(parameters = "Hash") LookupCallableSlotInMRONode lookupHash,
                        @Cached PythonObjectFactory factory) {
            Object hashDescr = lookupHash.execute(getClassNode.execute(key));
            if (hashDescr instanceof PNone) {
                return factory.createFrozenSet(hlib.copy(key.getDictStorage()));
            } else {
                return key;
            }
        }
    }

    @Builtin(name = "__class_getitem__", minNumOfPositionalArgs = 2, isClassmethod = true)
    @GenerateNodeFactory
    public abstract static class ClassGetItemNode extends PythonBinaryBuiltinNode {
        @Specialization
        Object classGetItem(Object cls, Object key) {
            return factory().createGenericAlias(cls, key);
        }
    }
}
