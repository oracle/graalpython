/*
 * Copyright (c) 2020, Oracle and/or its affiliates. All rights reserved.
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

import static com.oracle.graal.python.nodes.SpecialAttributeNames.__DICT__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__CONTAINS__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__EQ__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__GE__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__GT__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__ITER__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__LEN__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__LE__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__LT__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__REDUCE__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__REPR__;

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
import com.oracle.graal.python.builtins.objects.object.PythonObjectLibrary;
import com.oracle.graal.python.builtins.objects.str.PString;
import com.oracle.graal.python.builtins.objects.tuple.PTuple;
import com.oracle.graal.python.builtins.objects.type.TypeNodes;
import com.oracle.graal.python.nodes.PGuards;
import com.oracle.graal.python.nodes.PNodeWithContext;
import com.oracle.graal.python.nodes.call.special.LookupAndCallUnaryNode;
import com.oracle.graal.python.nodes.control.GetIteratorExpressionNode.GetIteratorNode;
import com.oracle.graal.python.nodes.control.GetNextNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.builtins.PythonBinaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonUnaryBuiltinNode;
import com.oracle.graal.python.nodes.object.IsBuiltinClassProfile;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.graal.python.runtime.object.PythonObjectFactory;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.profiles.ConditionProfile;

@CoreFunctions(extendClasses = {PythonBuiltinClassType.PSet, PythonBuiltinClassType.PFrozenSet})
public final class BaseSetBuiltins extends PythonBuiltins {

    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return BaseSetBuiltinsFactory.getFactories();
    }

    @Builtin(name = __REPR__, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class BaseReprNode extends PythonUnaryBuiltinNode {
        @CompilerDirectives.TruffleBoundary
        private static StringBuilder newStringBuilder() {
            return new StringBuilder();
        }

        @CompilerDirectives.TruffleBoundary
        private static String sbToString(StringBuilder sb) {
            return sb.toString();
        }

        @CompilerDirectives.TruffleBoundary
        private static void sbAppend(StringBuilder sb, String s) {
            sb.append(s);
        }

        private static void fillItems(VirtualFrame frame, StringBuilder sb, LookupAndCallUnaryNode repr, HashingStorageLibrary.HashingStorageIterator<Object> iter) {
            boolean first = true;
            sbAppend(sb, "{");
            while (iter.hasNext()) {
                Object reprString = repr.executeObject(frame, iter.next());
                if (reprString instanceof PString) {
                    reprString = ((PString) reprString).getValue();
                }
                if (first) {
                    first = false;
                } else {
                    sbAppend(sb, ", ");
                }
                sbAppend(sb, (String) reprString);
            }
            sbAppend(sb, "}");
        }

        @Specialization(limit = "2")
        public static Object repr(VirtualFrame frame, PBaseSet self,
                        @Cached IsBuiltinClassProfile isBuiltinClass,
                        @Cached("create(__REPR__)") LookupAndCallUnaryNode repr,
                        @Cached("create()") TypeNodes.GetNameNode getNameNode,
                        @CachedLibrary("self") PythonObjectLibrary lib,
                        @CachedLibrary("self.getDictStorage()") HashingStorageLibrary hlib) {
            StringBuilder sb = newStringBuilder();
            int len = hlib.length(self.getDictStorage());
            HashingStorageLibrary.HashingStorageIterator<Object> iter = hlib.keys(self.getDictStorage()).iterator();
            Object clazz = lib.getLazyPythonClass(self);
            if (len > 0 && clazz == PythonBuiltinClassType.PSet && isBuiltinClass.profileIsAnyBuiltinClass(clazz)) {
                fillItems(frame, sb, repr, iter);
                return sbToString(sb);
            }
            String typeName = getNameNode.execute(clazz);
            sbAppend(sb, typeName);
            sbAppend(sb, "(");
            if (len > 0) {
                fillItems(frame, sb, repr, iter);
            }
            sbAppend(sb, ")");
            return sbToString(sb);
        }
    }

    @Builtin(name = __ITER__, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    protected abstract static class BaseIterNode extends PythonUnaryBuiltinNode {
        @Specialization(limit = "1")
        public Object iter(PBaseSet self,
                        @CachedLibrary("self.getDictStorage()") HashingStorageLibrary lib) {
            return factory().createBaseSetIterator(self, lib.keys(self.getDictStorage()).iterator(), lib.length(self.getDictStorage()));
        }
    }

    @Builtin(name = __LEN__, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    protected abstract static class BaseLenNode extends PythonUnaryBuiltinNode {
        @Specialization(limit = "1")
        public static int len(PBaseSet self,
                        @CachedLibrary("self.getDictStorage()") HashingStorageLibrary lib) {
            return lib.length(self.getDictStorage());
        }
    }

    @Builtin(name = __REDUCE__, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    protected abstract static class BaseReduceNode extends PythonUnaryBuiltinNode {

        @Specialization(limit = "1")
        public Object reduce(VirtualFrame frame, PBaseSet self,
                        @CachedLibrary("self.getDictStorage()") HashingStorageLibrary lib,
                        @CachedLibrary("self") PythonObjectLibrary plib) {
            HashingStorage storage = self.getDictStorage();
            int len = lib.length(storage);
            Iterator<Object> keys = lib.keys(storage).iterator();
            Object[] keysArray = new Object[len];
            for (int i = 0; i < len; i++) {
                keysArray[i] = keys.next();
            }
            PTuple contents = factory().createTuple(new Object[]{factory().createList(keysArray)});
            Object dict = plib.lookupAttribute(self, frame, __DICT__);
            if (dict == PNone.NO_VALUE) {
                dict = PNone.NONE;
            }
            return factory().createTuple(new Object[]{plib.getLazyPythonClass(self), contents, dict});
        }
    }

    @Builtin(name = __EQ__, minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    protected abstract static class BaseEqNode extends PythonBinaryBuiltinNode {
        @Specialization(limit = "1")
        static boolean doSetSameType(VirtualFrame frame, PBaseSet self, PBaseSet other,
                        @Cached("createBinaryProfile()") ConditionProfile hasFrame,
                        @CachedLibrary("self.getDictStorage()") HashingStorageLibrary lib) {
            return lib.compareKeysWithFrame(self.getDictStorage(), other.getDictStorage(), hasFrame, frame) == 0;
        }

        @Fallback
        @SuppressWarnings("unused")
        static PNotImplemented doGeneric(Object self, Object other) {
            return PNotImplemented.NOT_IMPLEMENTED;
        }
    }

    @Builtin(name = __CONTAINS__, minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    protected abstract static class BaseContainsNode extends PythonBinaryBuiltinNode {

        @Specialization(limit = "1")
        boolean contains(VirtualFrame frame, PBaseSet self, Object key,
                        @Cached("createBinaryProfile()") ConditionProfile hasFrame,
                        @Cached ConvertKeyNode conv,
                        @CachedLibrary("self.getDictStorage()") HashingStorageLibrary lib) {
            return lib.hasKeyWithFrame(self.getDictStorage(), conv.execute(key, factory()), hasFrame, frame);
        }
    }

    @Builtin(name = "issubset", minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    protected abstract static class BaseIsSubsetNode extends PythonBinaryBuiltinNode {
        @Specialization(limit = "1")
        static boolean isSubSet(VirtualFrame frame, PBaseSet self, PBaseSet other,
                        @Cached("createBinaryProfile()") ConditionProfile hasFrame,
                        @CachedLibrary("self.getDictStorage()") HashingStorageLibrary lib) {
            return lib.compareKeysWithFrame(self.getDictStorage(), other.getDictStorage(), hasFrame, frame) <= 0;
        }

        @Specialization(replaces = "isSubSet", limit = "1")
        static boolean isSubSetGeneric(VirtualFrame frame, PBaseSet self, Object other,
                        @Cached HashingCollectionNodes.GetHashingStorageNode getHashingStorageNode,
                        @Cached("createBinaryProfile()") ConditionProfile hasFrame,
                        @CachedLibrary("self.getDictStorage()") HashingStorageLibrary lib) {
            HashingStorage otherSet = getHashingStorageNode.execute(frame, other);
            return lib.compareKeysWithFrame(self.getDictStorage(), otherSet, hasFrame, frame) <= 0;
        }
    }

    @Builtin(name = "issuperset", minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    protected abstract static class BaseIsSupersetNode extends PythonBinaryBuiltinNode {
        @Specialization(limit = "1")
        static boolean isSuperSet(VirtualFrame frame, PBaseSet self, PBaseSet other,
                        @Cached("createBinaryProfile()") ConditionProfile hasFrame,
                        @CachedLibrary("other.getDictStorage()") HashingStorageLibrary lib) {
            return lib.compareKeysWithFrame(other.getDictStorage(), self.getDictStorage(), hasFrame, frame) <= 0;
        }

        @Specialization(replaces = "isSuperSet")
        static boolean isSuperSetGeneric(VirtualFrame frame, PBaseSet self, Object other,
                        @Cached HashingCollectionNodes.GetHashingStorageNode getHashingStorageNode,
                        @Cached("createBinaryProfile()") ConditionProfile hasFrame,
                        @CachedLibrary(limit = "3") HashingStorageLibrary lib) {
            HashingStorage otherSet = getHashingStorageNode.execute(frame, other);
            return lib.compareKeysWithFrame(otherSet, self.getDictStorage(), hasFrame, frame) <= 0;
        }

    }

    @Builtin(name = "isdisjoint", minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    protected abstract static class BaseIsDisjointNode extends PythonBinaryBuiltinNode {

        @Specialization(guards = "self == other", limit = "2")
        static boolean isDisjointSameObject(VirtualFrame frame, PBaseSet self, @SuppressWarnings("unused") PBaseSet other,
                        @Cached("createBinaryProfile()") ConditionProfile hasFrame,
                        @CachedLibrary("self.getDictStorage()") HashingStorageLibrary lib) {
            ThreadState state = PArguments.getThreadStateOrNull(frame, hasFrame);
            return lib.lengthWithState(self.getDictStorage(), state) == 0;
        }

        @Specialization(guards = {"self != other", "cannotBeOverridden(pLib.getLazyPythonClass(other))"}, limit = "2")
        static boolean isDisjointFastPath(VirtualFrame frame, PBaseSet self, PBaseSet other,
                        @Cached("createBinaryProfile()") ConditionProfile hasFrame,
                        @CachedLibrary("self.getDictStorage()") HashingStorageLibrary selfLib,
                        @SuppressWarnings("unused") @CachedLibrary("other") PythonObjectLibrary pLib) {
            ThreadState state = PArguments.getThreadStateOrNull(frame, hasFrame);
            return selfLib.isDisjointWithState(self.getDictStorage(), other.getDictStorage(), state);
        }

        @Specialization(guards = {"self != other", "!cannotBeOverridden(pLib.getLazyPythonClass(other))"}, limit = "2")
        static boolean isDisjointWithOtherSet(VirtualFrame frame, PBaseSet self, PBaseSet other,
                        @Cached("createBinaryProfile()") ConditionProfile hasFrame,
                        @CachedLibrary("self.getDictStorage()") HashingStorageLibrary selfLib,
                        @SuppressWarnings("unused") @CachedLibrary("other") PythonObjectLibrary pLib,
                        @Cached GetIteratorNode getIteratorNode,
                        @Cached GetNextNode getNextNode,
                        @Cached IsBuiltinClassProfile errorProfile) {
            return isDisjointGeneric(frame, self, other, hasFrame, selfLib, getIteratorNode, getNextNode, errorProfile);
        }

        @Specialization(guards = {"!isAnySet(other)"}, limit = "3")
        static boolean isDisjointGeneric(VirtualFrame frame, PBaseSet self, Object other,
                        @Cached("createBinaryProfile()") ConditionProfile hasFrame,
                        @CachedLibrary("self.getDictStorage()") HashingStorageLibrary selfLib,
                        @Cached GetIteratorNode getIteratorNode,
                        @Cached GetNextNode getNextNode,
                        @Cached IsBuiltinClassProfile errorProfile) {
            ThreadState state = PArguments.getThreadStateOrNull(frame, hasFrame);
            HashingStorage selfStorage = self.getDictStorage();
            Object iterator = getIteratorNode.executeWith(frame, other);
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

    @Builtin(name = __LE__, minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    protected abstract static class BaseLessEqualNode extends PythonBinaryBuiltinNode {
        @Specialization(limit = "1")
        static boolean doLE(VirtualFrame frame, PBaseSet self, PBaseSet other,
                        @Cached("createBinaryProfile()") ConditionProfile hasFrame,
                        @CachedLibrary("self.getDictStorage()") HashingStorageLibrary lib) {
            return lib.compareKeysWithFrame(self.getDictStorage(), other.getDictStorage(), hasFrame, frame) <= 0;
        }

        @Fallback
        @SuppressWarnings("unused")
        static PNotImplemented doNotImplemented(Object self, Object other) {
            return PNotImplemented.NOT_IMPLEMENTED;
        }
    }

    @Builtin(name = __GE__, minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    protected abstract static class BaseGreaterEqualNode extends PythonBinaryBuiltinNode {
        @Specialization(limit = "1")
        static boolean doGE(VirtualFrame frame, PBaseSet self, PBaseSet other,
                        @Cached("createBinaryProfile()") ConditionProfile hasFrame,
                        @CachedLibrary("other.getDictStorage()") HashingStorageLibrary lib) {
            return lib.compareKeysWithFrame(other.getDictStorage(), self.getDictStorage(), hasFrame, frame) <= 0;
        }

        @Fallback
        @SuppressWarnings("unused")
        static PNotImplemented doNotImplemented(Object self, Object other) {
            return PNotImplemented.NOT_IMPLEMENTED;
        }
    }

    @Builtin(name = __LT__, minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    protected abstract static class BaseLessThanNode extends PythonBinaryBuiltinNode {

        @Specialization
        static boolean isLessThan(VirtualFrame frame, PBaseSet self, PBaseSet other,
                        @CachedLibrary(limit = "2") HashingStorageLibrary hlib,
                        @Cached ConditionProfile hasFrameProfile,
                        @Cached ConditionProfile sizeProfile) {
            final int len1 = hlib.lengthWithFrame(self.getDictStorage(), hasFrameProfile, frame);
            final int len2 = hlib.lengthWithFrame(other.getDictStorage(), hasFrameProfile, frame);
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

    @Builtin(name = __GT__, minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    protected abstract static class BaseGreaterThanNode extends PythonBinaryBuiltinNode {

        @Specialization
        static boolean isGreaterThan(VirtualFrame frame, PBaseSet self, PBaseSet other,
                        @CachedLibrary(limit = "2") HashingStorageLibrary hlib,
                        @Cached ConditionProfile hasFrameProfile,
                        @Cached ConditionProfile sizeProfile) {
            final int len1 = hlib.lengthWithFrame(self.getDictStorage(), hasFrameProfile, frame);
            final int len2 = hlib.lengthWithFrame(other.getDictStorage(), hasFrameProfile, frame);
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

    @ImportStatic(PGuards.class)
    protected abstract static class ConvertKeyNode extends PNodeWithContext {
        public abstract Object execute(Object key, PythonObjectFactory factory);

        @Specialization(guards = "!isPSet(key)", limit = "2")
        static Object doHashingCollection(Object key, @SuppressWarnings("unused") PythonObjectFactory factory,
                        @SuppressWarnings("unused") @CachedLibrary("key") PythonObjectLibrary lib) {
            return key;
        }

        @Specialization(guards = "!lib.isHashable(key)", limit = "2")
        static Object doPSet(PSet key, PythonObjectFactory factory,
                        @SuppressWarnings("unused") @CachedLibrary("key") PythonObjectLibrary lib,
                        @CachedLibrary("key.getDictStorage()") HashingStorageLibrary hlib) {
            return factory.createFrozenSet(hlib.copy(key.getDictStorage()));
        }

        @Specialization(guards = "lib.isHashable(key)", limit = "2")
        static Object doHashable(PSet key, @SuppressWarnings("unused") PythonObjectFactory factory,
                        @SuppressWarnings("unused") @CachedLibrary("key") PythonObjectLibrary lib) {
            return key;
        }
    }
}
