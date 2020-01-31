/*
 * Copyright (c) 2018, 2020, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.builtins.objects.common;

import static com.oracle.graal.python.nodes.SpecialMethodNames.KEYS;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.TypeError;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.ValueError;

import java.util.ArrayList;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodesFactory.ContainsKeyNodeGen;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodesFactory.CopyNodeGen;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodesFactory.DelItemNodeGen;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodesFactory.DiffNodeGen;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodesFactory.DynamicObjectSetItemNodeGen;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodesFactory.EqualsNodeGen;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodesFactory.ExclusiveOrNodeGen;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodesFactory.GetItemInteropNodeGen;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodesFactory.GetItemNodeGen;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodesFactory.InitNodeGen;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodesFactory.IntersectNodeGen;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodesFactory.KeysEqualsNodeGen;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodesFactory.KeysIsSubsetNodeGen;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodesFactory.KeysIsSupersetNodeGen;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodesFactory.LenNodeGen;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodesFactory.SetItemNodeGen;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodesFactory.UnionNodeGen;
import com.oracle.graal.python.builtins.objects.dict.PDict;
import com.oracle.graal.python.builtins.objects.function.PArguments;
import com.oracle.graal.python.builtins.objects.function.PKeyword;
import com.oracle.graal.python.builtins.objects.object.PythonObject;
import com.oracle.graal.python.nodes.IndirectCallNode;
import com.oracle.graal.python.nodes.PGuards;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.SpecialMethodNames;
import com.oracle.graal.python.nodes.attributes.LookupInheritedAttributeNode;
import com.oracle.graal.python.nodes.builtins.ListNodes.FastConstructListNode;
import com.oracle.graal.python.nodes.call.special.LookupAndCallBinaryNode;
import com.oracle.graal.python.nodes.call.special.LookupAndCallUnaryNode;
import com.oracle.graal.python.nodes.control.GetIteratorExpressionNode.GetIteratorNode;
import com.oracle.graal.python.nodes.control.GetNextNode;
import com.oracle.graal.python.nodes.object.IsBuiltinClassProfile;
import com.oracle.graal.python.runtime.ExecutionContext.IndirectCallContext;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.graal.python.runtime.sequence.PSequence;
import com.oracle.truffle.api.Assumption;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.CachedContext;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.profiles.BranchProfile;
import com.oracle.truffle.api.profiles.ConditionProfile;

@GenerateNodeFactory
public abstract class HashingStorageNodes {
    private static final int MAX_STORAGES = 8;

    abstract static class DictStorageBaseNode extends Node implements IndirectCallNode {
        private final Assumption dontNeedExceptionState = Truffle.getRuntime().createAssumption();
        private final Assumption dontNeedCallerFrame = Truffle.getRuntime().createAssumption();

        @Override
        public Assumption needNotPassFrameAssumption() {
            return dontNeedCallerFrame;
        }

        @Override
        public Assumption needNotPassExceptionAssumption() {
            return dontNeedExceptionState;
        }
    }

    @ImportStatic({SpecialMethodNames.class, PGuards.class})
    public abstract static class InitNode extends DictStorageBaseNode {
        public abstract HashingStorage execute(VirtualFrame frame, Object mapping, PKeyword[] kwargs);

        @Child private GetNextNode nextNode;
        @Child private SetItemNode setItemNode;
        @Child private LookupInheritedAttributeNode lookupKeysAttributeNode;

        protected boolean isEmpty(PKeyword[] kwargs) {
            return kwargs.length == 0;
        }

        @Specialization(guards = {"isNoValue(iterable)", "isEmpty(kwargs)"})
        HashingStorage doEmpty(@SuppressWarnings("unused") PNone iterable, @SuppressWarnings("unused") PKeyword[] kwargs) {
            return new EmptyStorage();
        }

        @Specialization(guards = {"isNoValue(iterable)", "!isEmpty(kwargs)"})
        HashingStorage doKeywords(@SuppressWarnings("unused") PNone iterable, PKeyword[] kwargs) {
            return new KeywordsStorage(kwargs);
        }

        protected static boolean isPDict(Object o) {
            return o instanceof PHashingCollection;
        }

        protected boolean hasKeysAttribute(Object o) {
            if (lookupKeysAttributeNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                lookupKeysAttributeNode = insert(LookupInheritedAttributeNode.create(KEYS));
            }
            return lookupKeysAttributeNode.execute(o) != PNone.NO_VALUE;
        }

        @Specialization(guards = "isEmpty(kwargs)")
        HashingStorage doPDict(PHashingCollection dictLike, @SuppressWarnings("unused") PKeyword[] kwargs,
                        @CachedLibrary(limit = "3") HashingStorageLibrary lib,
                        @Cached HashingCollectionNodes.GetDictStorageNode getDictStorageNode) {
            return lib.copy(getDictStorageNode.execute(dictLike));
        }

        @Specialization(guards = "!isEmpty(kwargs)")
        HashingStorage doPDictKwargs(VirtualFrame frame, PHashingCollection iterable, PKeyword[] kwargs,
                        @CachedContext(PythonLanguage.class) PythonContext context,
                        @CachedLibrary(limit = "2") HashingStorageLibrary lib,
                        @Cached("create()") HashingCollectionNodes.GetDictStorageNode getDictStorageNode) {
            Object state = IndirectCallContext.enter(frame, context, this);
            try {
                HashingStorage iterableDictStorage = getDictStorageNode.execute(iterable);
                HashingStorage dictStorage = lib.copy(iterableDictStorage);
                return lib.addAllToOther(new KeywordsStorage(kwargs), dictStorage);
            } finally {
                IndirectCallContext.exit(frame, context, state);
            }
        }

        @Specialization(guards = {"!isPDict(mapping)", "hasKeysAttribute(mapping)"})
        HashingStorage doMapping(VirtualFrame frame, Object mapping, @SuppressWarnings("unused") PKeyword[] kwargs,
                        @CachedLibrary(limit = "3") HashingStorageLibrary lib,
                        @Cached("create(KEYS)") LookupAndCallUnaryNode callKeysNode,
                        @Cached("create(__GETITEM__)") LookupAndCallBinaryNode callGetItemNode,
                        @Cached("create()") GetIteratorNode getIteratorNode,
                        @Cached("create()") IsBuiltinClassProfile errorProfile) {
            HashingStorage curStorage = PDict.createNewStorage(false, 0);
            // That call must work since 'hasKeysAttribute' checks if it has the 'keys' attribute
            // before.
            Object keysIterable = callKeysNode.executeObject(frame, mapping);
            Object keysIt = getIteratorNode.executeWith(frame, keysIterable);
            while (true) {
                try {
                    Object keyObj = getNextNode().execute(frame, keysIt);
                    Object valueObj = callGetItemNode.executeObject(frame, mapping, keyObj);

                    curStorage = getSetItemNode().execute(frame, curStorage, keyObj, valueObj);
                } catch (PException e) {
                    e.expectStopIteration(errorProfile);
                    break;
                }
            }
            if (kwargs.length > 0) {
                curStorage = lib.addAllToOther(new KeywordsStorage(kwargs), curStorage);
            }
            return curStorage;
        }

        private GetNextNode getNextNode() {
            if (nextNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                nextNode = insert(GetNextNode.create());
            }
            return nextNode;
        }

        private SetItemNode getSetItemNode() {
            if (setItemNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                setItemNode = insert(SetItemNode.create());
            }
            return setItemNode;
        }

        @Specialization(guards = {"!isNoValue(iterable)", "!isPDict(iterable)", "!hasKeysAttribute(iterable)"})
        HashingStorage doSequence(VirtualFrame frame, PythonObject iterable, PKeyword[] kwargs,
                        @CachedLibrary(limit = "3") HashingStorageLibrary lib,
                        @Cached PRaiseNode raise,
                        @Cached("create()") GetIteratorNode getIterator,
                        @Cached("create()") FastConstructListNode createListNode,
                        @Cached("create(__GETITEM__)") LookupAndCallBinaryNode getItemNode,
                        @Cached("create()") SequenceNodes.LenNode seqLenNode,
                        @Cached("createBinaryProfile()") ConditionProfile lengthTwoProfile,
                        @Cached("create()") IsBuiltinClassProfile errorProfile,
                        @Cached("create()") IsBuiltinClassProfile isTypeErrorProfile) {

            Object it = getIterator.executeWith(frame, iterable);

            ArrayList<PSequence> elements = new ArrayList<>();
            boolean isStringKey = false;
            try {
                while (true) {
                    Object next = getNextNode().execute(frame, it);
                    PSequence element = null;
                    int len = 1;
                    element = createListNode.execute(next);
                    assert element != null;
                    // This constructs a new list using the builtin type. So, the object cannot
                    // be subclassed and we can directly call 'len()'.
                    len = seqLenNode.execute(element);

                    if (lengthTwoProfile.profile(len != 2)) {
                        throw raise.raise(ValueError, "dictionary update sequence element #%d has length %d; 2 is required", arrayListSize(elements), len);
                    }

                    // really check for Java String since PString can be subclassed
                    isStringKey = isStringKey || getItemNode.executeObject(frame, element, 0) instanceof String;

                    arrayListAdd(elements, element);
                }
            } catch (PException e) {
                if (isTypeErrorProfile.profileException(e, TypeError)) {
                    throw raise.raise(TypeError, "cannot convert dictionary update sequence element #%d to a sequence", arrayListSize(elements));
                } else {
                    e.expectStopIteration(errorProfile);
                }
            }

            HashingStorage storage = PDict.createNewStorage(isStringKey, arrayListSize(elements) + kwargs.length);
            for (int j = 0; j < arrayListSize(elements); j++) {
                PSequence element = arrayListGet(elements, j);
                storage = getSetItemNode().execute(frame, storage, getItemNode.executeObject(frame, element, 0), getItemNode.executeObject(frame, element, 1));
            }
            if (kwargs.length > 0) {
                storage = lib.addAllToOther(new KeywordsStorage(kwargs), storage);
            }
            return storage;
        }

        @TruffleBoundary(allowInlining = true)
        private static PSequence arrayListGet(ArrayList<PSequence> elements, int j) {
            return elements.get(j);
        }

        @TruffleBoundary(allowInlining = true)
        private static boolean arrayListAdd(ArrayList<PSequence> elements, PSequence element) {
            return elements.add(element);
        }

        @TruffleBoundary(allowInlining = true)
        private static int arrayListSize(ArrayList<PSequence> elements) {
            return elements.size();
        }

        public static InitNode create() {
            return InitNodeGen.create();
        }
    }

    public abstract static class ContainsKeyNode extends DictStorageBaseNode {

        public abstract boolean execute(VirtualFrame frame, HashingStorage storage, Object key);

        @Specialization
        protected boolean readUncached(VirtualFrame frame, HashingStorage storage, Object name,
                        @CachedContext(PythonLanguage.class) PythonContext context,
                        @Cached("createBinaryProfile()") ConditionProfile hasFrame,
                        @CachedLibrary(limit = "3") HashingStorageLibrary lib) {
            Object state = IndirectCallContext.enter(frame, context, this);
            try {
                if (hasFrame.profile(frame != null)) {
                    return lib.hasKeyWithState(storage, name, PArguments.getThreadState(frame));
                } else {
                    return lib.hasKey(storage, name);
                }
            } finally {
                IndirectCallContext.exit(frame, context, state);
            }
        }

        public static ContainsKeyNode create() {
            return ContainsKeyNodeGen.create();
        }
    }

    @GenerateUncached
    public abstract static class DynamicObjectSetItemNode extends Node {
        public static DynamicObjectSetItemNode create() {
            return DynamicObjectSetItemNodeGen.create();
        }

        public static DynamicObjectSetItemNode getUncached() {
            return DynamicObjectSetItemNodeGen.getUncached();
        }

        public abstract HashingStorage execute(DynamicObjectStorage s, Object key, Object val);

        @Specialization(limit = "1")
        HashingStorage doit(DynamicObjectStorage s, Object key, Object val,
                        @CachedLibrary("s") HashingStorageLibrary lib) {
            return lib.setItem(s, key, val);
        }
    }

    public abstract static class SetItemNode extends DictStorageBaseNode {
        public abstract HashingStorage execute(VirtualFrame frame, HashingStorage storage, Object key, Object value);

        @Specialization
        protected HashingStorage doGeneric(VirtualFrame frame, HashingStorage storage, Object key, Object value,
                        @CachedContext(PythonLanguage.class) PythonContext context,
                        @CachedLibrary(limit = "3") HashingStorageLibrary lib) {
            Object state = IndirectCallContext.enter(frame, context, this);
            try {
                return lib.setItem(storage, key, value);
            } finally {
                IndirectCallContext.exit(frame, context, state);
            }
        }

        public static SetItemNode create() {
            return SetItemNodeGen.create();
        }
    }

    @ImportStatic(SpecialMethodNames.class)
    public abstract static class GetItemNode extends DictStorageBaseNode {
        public static GetItemNode create() {
            return GetItemNodeGen.create();
        }

        public abstract Object execute(VirtualFrame frame, HashingStorage storage, Object key);

        @Specialization
        Object doGeneric(@SuppressWarnings("unused") VirtualFrame frame, HashingStorage storage, Object key,
                        @CachedContext(PythonLanguage.class) PythonContext context,
                        @Cached("createBinaryProfile()") ConditionProfile hasFrame,
                        @CachedLibrary(limit = "3") HashingStorageLibrary lib) {
            Object state = IndirectCallContext.enter(frame, context, this);
            try {
                if (hasFrame.profile(frame != null)) {
                    return lib.getItemWithState(storage, key, PArguments.getThreadState(frame));
                } else {
                    return lib.getItem(storage, key);
                }
            } finally {
                IndirectCallContext.exit(frame, context, state);
            }
        }
    }

    @GenerateUncached
    public abstract static class GetItemInteropNode extends Node {
        public static GetItemInteropNode create() {
            return GetItemInteropNodeGen.create();
        }

        public static GetItemInteropNode getUncached() {
            return GetItemInteropNodeGen.getUncached();
        }

        public abstract Object executeWithGlobalState(HashingStorage storage, Object key);

        @Specialization
        public Object doit(HashingStorage storage, Object key,
                        @CachedLibrary(limit = "1") HashingStorageLibrary lib) {
            return lib.getItem(storage, key);
        }
    }

    public abstract static class EqualsNode extends DictStorageBaseNode {
        public abstract boolean execute(VirtualFrame frame, HashingStorage selfStorage, HashingStorage other);

        @Specialization
        boolean doGeneric(VirtualFrame frame, HashingStorage self, HashingStorage other,
                        @CachedContext(PythonLanguage.class) PythonContext context,
                        @Cached("createBinaryProfile()") ConditionProfile hasFrame,
                        @CachedLibrary(limit = "3") HashingStorageLibrary lib) {
            Object state = IndirectCallContext.enter(frame, context, this);
            try {
                if (hasFrame.profile(frame != null)) {
                    return lib.equalsWithState(self, other, PArguments.getThreadState(frame));
                } else {
                    return lib.equals(self, other);
                }
            } finally {
                IndirectCallContext.exit(frame, context, state);
            }
        }

        public static EqualsNode create() {
            return EqualsNodeGen.create();
        }
    }

    public abstract static class KeysEqualsNode extends DictStorageBaseNode {
        public abstract boolean execute(VirtualFrame frame, HashingStorage selfStorage, HashingStorage other);

        @Specialization
        boolean doGeneric(VirtualFrame frame, HashingStorage selfStorage, HashingStorage other,
                        @CachedContext(PythonLanguage.class) PythonContext context,
                        @CachedLibrary(limit = "3") HashingStorageLibrary lib) {
            Object state = IndirectCallContext.enter(frame, context, this);
            try {
                return lib.compareKeys(selfStorage, other) == 0;
            } finally {
                IndirectCallContext.exit(frame, context, state);
            }
        }

        public static KeysEqualsNode create() {
            return KeysEqualsNodeGen.create();
        }
    }

    public abstract static class KeysIsSubsetNode extends DictStorageBaseNode {
        public abstract boolean execute(VirtualFrame frame, HashingStorage selfStorage, HashingStorage other);

        @Specialization
        boolean doGeneric(VirtualFrame frame, HashingStorage selfStorage, HashingStorage other,
                        @CachedContext(PythonLanguage.class) PythonContext context,
                        @CachedLibrary(limit = "3") HashingStorageLibrary lib) {
            Object state = IndirectCallContext.enter(frame, context, this);
            try {
                return lib.compareKeys(selfStorage, other) <= 0;
            } finally {
                IndirectCallContext.exit(frame, context, state);
            }
        }

        public static KeysIsSubsetNode create() {
            return KeysIsSubsetNodeGen.create();
        }
    }

    public abstract static class KeysIsSupersetNode extends DictStorageBaseNode {
        public abstract boolean execute(VirtualFrame frame, HashingStorage selfStorage, HashingStorage other);

        @Specialization
        boolean doGeneric(VirtualFrame frame, HashingStorage selfStorage, HashingStorage other,
                        @CachedContext(PythonLanguage.class) PythonContext context,
                        @CachedLibrary(limit = "3") HashingStorageLibrary lib) {
            Object state = IndirectCallContext.enter(frame, context, this);
            try {
                return lib.compareKeys(other, selfStorage) <= 0;
            } finally {
                IndirectCallContext.exit(frame, context, state);
            }
        }

        public static KeysIsSupersetNode create() {
            return KeysIsSupersetNodeGen.create();
        }
    }

    public abstract static class DelItemNode extends DictStorageBaseNode {
        public abstract boolean execute(VirtualFrame frame, PHashingCollection dict, HashingStorage dictStorage, Object key);

        @Specialization
        protected boolean doGeneric(VirtualFrame frame, PHashingCollection container, HashingStorage storage, Object key,
                        @CachedContext(PythonLanguage.class) PythonContext context,
                        @Cached BranchProfile updatedStorage,
                        @Cached("createBinaryProfile()") ConditionProfile hasFrame,
                        @CachedLibrary(limit = "3") HashingStorageLibrary lib) {
            Object state = IndirectCallContext.enter(frame, context, this);
            try {
                boolean hasKey; // TODO: FIXME: this might call __hash__ twice
                if (hasFrame.profile(frame != null)) {
                    hasKey = lib.hasKeyWithState(storage, key, PArguments.getThreadState(frame));
                } else {
                    hasKey = lib.hasKey(storage, key);
                }
                if (hasKey) {
                    HashingStorage newStore = lib.delItem(storage, key);
                    if (newStore != storage) {
                        updatedStorage.enter();
                        container.setDictStorage(newStore);
                    }
                }
                return hasKey;
            } finally {
                IndirectCallContext.exit(frame, context, state);
            }
        }

        public static DelItemNode create() {
            return DelItemNodeGen.create();
        }
    }

    public abstract static class CopyNode extends Node {
        public abstract HashingStorage execute(VirtualFrame frame, HashingStorage storage);

        @Specialization(limit = "3")
        HashingStorage doGeneric(HashingStorage storage,
                        @CachedLibrary("storage") HashingStorageLibrary lib) {
            return lib.copy(storage);
        }

        public static CopyNode create() {
            return CopyNodeGen.create();
        }
    }

    public abstract static class IntersectNode extends DictStorageBaseNode {

        public abstract HashingStorage execute(VirtualFrame frame, HashingStorage left, HashingStorage right);

        @Specialization
        HashingStorage doGeneric(VirtualFrame frame, HashingStorage left, HashingStorage right,
                        @CachedContext(PythonLanguage.class) PythonContext context,
                        @CachedLibrary(limit = "3") HashingStorageLibrary lib) {
            Object state = IndirectCallContext.enter(frame, context, this);
            try {
                return lib.intersect(left, right);
            } finally {
                IndirectCallContext.exit(frame, context, state);
            }
        }

        public static IntersectNode create() {
            return IntersectNodeGen.create();
        }
    }

    public abstract static class UnionNode extends DictStorageBaseNode {
        public abstract HashingStorage execute(VirtualFrame frame, HashingStorage left, HashingStorage right);

        @Specialization
        HashingStorage doGeneric(VirtualFrame frame, HashingStorage left, HashingStorage right,
                        @CachedContext(PythonLanguage.class) PythonContext context,
                        @CachedLibrary(limit = "3") HashingStorageLibrary lib) {
            Object state = IndirectCallContext.enter(frame, context, this);
            try {
                return lib.union(left, right);
            } finally {
                IndirectCallContext.exit(frame, context, state);
            }
        }

        public static UnionNode create() {
            return UnionNodeGen.create();
        }
    }

    public abstract static class ExclusiveOrNode extends DictStorageBaseNode {
        public abstract HashingStorage execute(VirtualFrame frame, HashingStorage left, HashingStorage right);

        @Specialization
        HashingStorage doGeneric(VirtualFrame frame, HashingStorage left, HashingStorage right,
                        @CachedContext(PythonLanguage.class) PythonContext context,
                        @CachedLibrary(limit = "3") HashingStorageLibrary lib) {
            Object state = IndirectCallContext.enter(frame, context, this);
            try {
                return lib.xor(left, right);
            } finally {
                IndirectCallContext.exit(frame, context, state);
            }
        }

        public static ExclusiveOrNode create() {
            return ExclusiveOrNodeGen.create();
        }
    }

    public abstract static class DiffNode extends DictStorageBaseNode {
        public abstract HashingStorage execute(VirtualFrame frame, HashingStorage left, HashingStorage right);

        @Specialization
        public HashingStorage doGeneric(VirtualFrame frame, HashingStorage left, HashingStorage right,
                        @CachedContext(PythonLanguage.class) PythonContext context,
                        @CachedLibrary(limit = "3") HashingStorageLibrary lib) {
            Object state = IndirectCallContext.enter(frame, context, this);
            try {
                return lib.diff(left, right);
            } finally {
                IndirectCallContext.exit(frame, context, state);
            }
        }

        public static DiffNode create() {
            return DiffNodeGen.create();
        }
    }

    @GenerateUncached
    public abstract static class LenNode extends Node {
        protected static final int MAX_STORAGES = HashingStorageNodes.MAX_STORAGES;

        public abstract int execute(HashingStorage s);

        @Specialization(limit = "MAX_STORAGES")
        static int doCached(HashingStorage s,
                        @CachedLibrary("s") HashingStorageLibrary lib) {
            return lib.length(s);
        }

        public static LenNode create() {
            return LenNodeGen.create();
        }

        public static LenNode getUncached() {
            return LenNodeGen.getUncached();
        }
    }
}
