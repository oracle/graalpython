/*
 * Copyright (c) 2017, 2021, Oracle and/or its affiliates.
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
package com.oracle.graal.python.builtins.objects.dict;

import static com.oracle.graal.python.builtins.objects.PNone.NO_VALUE;
import static com.oracle.graal.python.nodes.SpecialMethodNames.ITEMS;
import static com.oracle.graal.python.nodes.SpecialMethodNames.KEYS;
import static com.oracle.graal.python.nodes.SpecialMethodNames.VALUES;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__CONTAINS__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__DELITEM__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__EQ__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__GETITEM__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__HASH__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__INIT__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__ITER__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__LEN__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__REVERSED__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__SETITEM__;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.KeyError;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.TypeError;

import java.util.List;

import com.oracle.graal.python.builtins.Builtin;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.Python3Core;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.PNotImplemented;
import com.oracle.graal.python.builtins.objects.common.HashingCollectionNodes;
import com.oracle.graal.python.builtins.objects.common.HashingStorage;
import com.oracle.graal.python.builtins.objects.common.HashingStorage.DictEntry;
import com.oracle.graal.python.builtins.objects.common.HashingStorageLibrary;
import com.oracle.graal.python.builtins.objects.dict.DictBuiltinsFactory.DispatchMissingNodeGen;
import com.oracle.graal.python.builtins.objects.function.PArguments;
import com.oracle.graal.python.builtins.objects.function.PKeyword;
import com.oracle.graal.python.builtins.objects.tuple.PTuple;
import com.oracle.graal.python.builtins.objects.type.SpecialMethodSlot;
import com.oracle.graal.python.builtins.objects.type.TypeNodes;
import com.oracle.graal.python.lib.PyObjectGetIter;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PNodeWithRaise;
import com.oracle.graal.python.nodes.call.CallNode;
import com.oracle.graal.python.nodes.call.special.CallTernaryMethodNode;
import com.oracle.graal.python.nodes.call.special.LookupAndCallBinaryNode;
import com.oracle.graal.python.nodes.call.special.LookupSpecialMethodSlotNode;
import com.oracle.graal.python.nodes.control.GetNextNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonBinaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonTernaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonUnaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonVarargsBuiltinNode;
import com.oracle.graal.python.nodes.object.GetClassNode;
import com.oracle.graal.python.nodes.object.IsBuiltinClassProfile;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Exclusive;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.profiles.BranchProfile;
import com.oracle.truffle.api.profiles.ConditionProfile;

@CoreFunctions(extendClasses = {PythonBuiltinClassType.PDict, PythonBuiltinClassType.PDefaultDict})
public final class DictBuiltins extends PythonBuiltins {

    @Override
    public void initialize(Python3Core core) {
        super.initialize(core);
        builtinConstants.put(__HASH__, PNone.NONE);
    }

    @Override
    protected List<com.oracle.truffle.api.dsl.NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return DictBuiltinsFactory.getFactories();
    }

    @Builtin(name = __INIT__, minNumOfPositionalArgs = 1, takesVarArgs = true, takesVarKeywordArgs = true)
    @GenerateNodeFactory
    public abstract static class InitNode extends PythonVarargsBuiltinNode {
        @Child private HashingStorage.InitNode initNode;

        private HashingStorage.InitNode getInitNode() {
            if (initNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                initNode = insert(HashingStorage.InitNode.create());
            }
            return initNode;
        }

        @Specialization(guards = {"args.length == 1"})
        Object doVarargs(VirtualFrame frame, PDict self, Object[] args, PKeyword[] kwargs,
                        @CachedLibrary(limit = "1") HashingStorageLibrary storageLib) {
            self.setDictStorage(storageLib.addAllToOther(getInitNode().execute(frame, args[0], kwargs), self.getDictStorage()));
            return PNone.NONE;
        }

        @Specialization(guards = {"args.length == 0", "kwargs.length > 0"})
        Object doKeywords(VirtualFrame frame, PDict self, @SuppressWarnings("unused") Object[] args, PKeyword[] kwargs,
                        @CachedLibrary(limit = "1") HashingStorageLibrary storageLib) {
            self.setDictStorage(storageLib.addAllToOther(getInitNode().execute(frame, NO_VALUE, kwargs), self.getDictStorage()));
            return PNone.NONE;
        }

        @SuppressWarnings("unused")
        @Specialization(guards = {"args.length == 0", "kwargs.length == 0"})
        static Object doEmpty(PDict self, Object[] args, PKeyword[] kwargs) {
            return PNone.NONE;
        }

        @Specialization(guards = "args.length > 1")
        Object doGeneric(@SuppressWarnings("unused") PDict self, Object[] args, @SuppressWarnings("unused") PKeyword[] kwargs) {
            throw raise(TypeError, ErrorMessages.EXPECTED_AT_MOST_D_ARGS_GOT_D, "dict", 1, args.length);
        }
    }

    // setdefault(key[, default])
    @Builtin(name = "setdefault", minNumOfPositionalArgs = 2, parameterNames = {"self", "key", "default"})
    @GenerateNodeFactory
    public abstract static class SetDefaultNode extends PythonBuiltinNode {

        @Specialization(guards = "lib.hasKeyWithFrame(dict.getDictStorage(), key, hasFrame, frame)", limit = "3")
        public static Object setDefault(VirtualFrame frame, PDict dict, Object key, @SuppressWarnings("unused") Object defaultValue,
                        @SuppressWarnings("unused") @Cached ConditionProfile hasFrame,
                        @CachedLibrary("dict.getDictStorage()") HashingStorageLibrary lib) {
            return lib.getItemWithFrame(dict.getDictStorage(), key, hasFrame, frame);
        }

        @Specialization(guards = "!lib.hasKeyWithFrame(dict.getDictStorage(), key, hasFrame, frame)", limit = "3")
        public static Object setDefault(VirtualFrame frame, PDict dict, Object key, Object defaultValue,
                        @Cached HashingCollectionNodes.SetItemNode setItemNode,
                        @SuppressWarnings("unused") @CachedLibrary("dict.getDictStorage()") HashingStorageLibrary lib,
                        @SuppressWarnings("unused") @Cached ConditionProfile hasFrame,
                        @Cached ConditionProfile defaultValProfile) {
            Object value = defaultValue;
            if (defaultValProfile.profile(defaultValue == PNone.NO_VALUE)) {
                value = PNone.NONE;
            }
            setItemNode.execute(frame, dict, key, value);
            return value;
        }
    }

    // pop(key[, default])
    @Builtin(name = "pop", minNumOfPositionalArgs = 2, maxNumOfPositionalArgs = 3)
    @GenerateNodeFactory
    public abstract static class PopNode extends PythonTernaryBuiltinNode {

        protected static void removeItem(VirtualFrame frame, PDict dict, Object key, HashingStorage storage,
                        HashingStorageLibrary lib, ConditionProfile hasFrame, BranchProfile updatedStorage) {
            HashingStorage newStore = null;
            // TODO: FIXME: this might call __hash__ twice
            boolean hasKey = lib.hasKeyWithFrame(storage, key, hasFrame, frame);
            if (hasKey) {
                newStore = lib.delItemWithFrame(storage, key, hasFrame, frame);
            }

            if (hasKey) {
                if (newStore != storage) {
                    updatedStorage.enter();
                    dict.setDictStorage(newStore);
                }
            }
        }

        @Specialization(limit = "3")
        public Object popDefault(VirtualFrame frame, PDict dict, Object key, Object defaultValue,
                        @Cached BranchProfile updatedStorage,
                        @Cached ConditionProfile hasKey,
                        @Cached ConditionProfile hasDefault,
                        @Cached ConditionProfile hasFrame,
                        @CachedLibrary("dict.getDictStorage()") HashingStorageLibrary lib) {
            HashingStorage dictStorage = dict.getDictStorage();
            Object retVal = lib.getItemWithFrame(dictStorage, key, hasFrame, frame);
            if (hasKey.profile(retVal != null)) {
                removeItem(frame, dict, key, dictStorage, lib, hasFrame, updatedStorage);
                return retVal;
            } else if (hasDefault.profile(defaultValue != PNone.NO_VALUE)) {
                return defaultValue;
            } else {
                throw raise(PythonBuiltinClassType.KeyError, "%s", key);
            }
        }
    }

    // popitem()
    @Builtin(name = "popitem", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class PopItemNode extends PythonUnaryBuiltinNode {

        @Specialization(limit = "3")
        public Object popItem(PDict dict,
                        @CachedLibrary("dict.getDictStorage()") HashingStorageLibrary lib) {
            HashingStorage storage = dict.getDictStorage();
            for (DictEntry entry : lib.reverseEntries(storage)) {
                PTuple result = factory().createTuple(new Object[]{entry.getKey(), entry.getValue()});
                lib.delItem(storage, entry.getKey());
                return result;
            }
            throw raise(KeyError, ErrorMessages.IS_EMPTY, "popitem(): dictionary");
        }
    }

    // keys()
    @Builtin(name = KEYS, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class KeysNode extends PythonUnaryBuiltinNode {

        @Specialization
        public PDictView keys(PDict self) {
            return factory().createDictKeysView(self);
        }
    }

    // items()
    @Builtin(name = ITEMS, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class ItemsNode extends PythonUnaryBuiltinNode {

        @Specialization
        public PDictView items(PDict self) {
            return factory().createDictItemsView(self);
        }
    }

    // get(key[, default])
    @Builtin(name = "get", minNumOfPositionalArgs = 2, maxNumOfPositionalArgs = 3)
    @GenerateNodeFactory
    public abstract static class GetNode extends PythonTernaryBuiltinNode {
        @Specialization(limit = "getCallSiteInlineCacheMaxDepth()")
        public static Object doWithDefault(VirtualFrame frame, PDict self, Object key, Object defaultValue,
                        @CachedLibrary(value = "self.getDictStorage()") HashingStorageLibrary hlib,
                        @Cached ConditionProfile profile) {
            final Object value = hlib.getItemWithFrame(self.getDictStorage(), key, profile, frame);
            return value != null ? value : (defaultValue == PNone.NO_VALUE ? PNone.NONE : defaultValue);
        }
    }

    @Builtin(name = __GETITEM__, minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    public abstract static class GetItemNode extends PythonBinaryBuiltinNode {
        @Child private DispatchMissingNode missing;

        @Specialization(limit = "getCallSiteInlineCacheMaxDepth()")
        Object getItem(VirtualFrame frame, PDict self, Object key,
                        @CachedLibrary(value = "self.getDictStorage()") HashingStorageLibrary hlib,
                        @Exclusive @Cached ConditionProfile profile) {
            final Object result = hlib.getItemWithFrame(self.getDictStorage(), key, profile, frame);
            if (result == null) {
                if (missing == null) {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    missing = insert(DispatchMissingNodeGen.create());
                }
                return missing.execute(frame, self, key);
            }
            return result;
        }
    }

    @ImportStatic(SpecialMethodSlot.class)
    protected abstract static class DispatchMissingNode extends Node {

        protected abstract Object execute(VirtualFrame frame, Object self, Object key);

        @Specialization
        protected static Object misssing(VirtualFrame frame, Object self, Object key,
                        @Cached("create(Missing)") LookupAndCallBinaryNode callMissing,
                        @Cached DefaultMissingNode defaultMissing) {
            Object result = callMissing.executeObject(frame, self, key);
            if (result == PNotImplemented.NOT_IMPLEMENTED) {
                return defaultMissing.execute(key);
            }
            return result;
        }
    }

    protected abstract static class DefaultMissingNode extends PNodeWithRaise {
        public abstract Object execute(Object key);

        @Specialization
        Object run(String key) {
            throw raise(KeyError, "%s", key);
        }

        @Specialization(guards = "!isJavaString(key)")
        Object run(Object key) {
            throw raise(KeyError, key);
        }
    }

    @Builtin(name = __SETITEM__, minNumOfPositionalArgs = 3)
    @GenerateNodeFactory
    public abstract static class SetItemNode extends PythonTernaryBuiltinNode {
        @Specialization
        static Object run(VirtualFrame frame, PDict self, Object key, Object value,
                        @Cached HashingCollectionNodes.SetItemNode setItemNode) {
            setItemNode.execute(frame, self, key, value);
            return PNone.NONE;
        }
    }

    @Builtin(name = __DELITEM__, minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    public abstract static class DelItemNode extends PythonBinaryBuiltinNode {
        @Specialization
        Object run(VirtualFrame frame, PDict self, Object key,
                        @Cached BranchProfile updatedStorage,
                        @Cached ConditionProfile hasFrame,
                        @CachedLibrary(limit = "3") HashingStorageLibrary lib) {
            HashingStorage storage = self.getDictStorage();
            HashingStorage newStore = null;
            // TODO: FIXME: this might call __hash__ twice
            boolean hasKey = lib.hasKeyWithFrame(storage, key, hasFrame, frame);
            if (hasKey) {
                newStore = lib.delItemWithFrame(storage, key, hasFrame, frame);
            }

            if (hasKey) {
                if (newStore != storage) {
                    updatedStorage.enter();
                    self.setDictStorage(newStore);
                }
                return PNone.NONE;
            }
            throw raise(KeyError, "%s", key);
        }
    }

    @Builtin(name = __ITER__, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class IterNode extends PythonUnaryBuiltinNode {
        @Specialization(limit = "getCallSiteInlineCacheMaxDepth()")
        Object run(@SuppressWarnings("unused") PDict self,
                        @Bind("self.getDictStorage()") HashingStorage dictStorage,
                        @CachedLibrary("dictStorage") HashingStorageLibrary lib) {
            return factory().createDictKeyIterator(lib.keys(dictStorage).iterator(), dictStorage, lib.length(dictStorage));
        }
    }

    @Builtin(name = __REVERSED__, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class ReversedNode extends PythonUnaryBuiltinNode {
        @Specialization(limit = "getCallSiteInlineCacheMaxDepth()")
        Object run(PDict self,
                        @CachedLibrary("self.getDictStorage()") HashingStorageLibrary lib) {
            HashingStorage storage = self.getDictStorage();
            return factory().createDictKeyIterator(lib.reverseKeys(storage).iterator(), storage, lib.length(storage));
        }
    }

    @Builtin(name = __EQ__, minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    public abstract static class EqNode extends PythonBinaryBuiltinNode {

        @Specialization(limit = "3")
        static Object doDictDict(VirtualFrame frame, PDict self, PDict other,
                        @Cached ConditionProfile hasFrame,
                        @CachedLibrary("self.getDictStorage()") HashingStorageLibrary lib) {
            if (hasFrame.profile(frame != null)) {
                return lib.equalsWithState(self.getDictStorage(), other.getDictStorage(), PArguments.getThreadState(frame));
            } else {
                return lib.equals(self.getDictStorage(), other.getDictStorage());
            }
        }

        @Fallback
        @SuppressWarnings("unused")
        static PNotImplemented doGeneric(Object self, Object other) {
            return PNotImplemented.NOT_IMPLEMENTED;
        }
    }

    @Builtin(name = __CONTAINS__, minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    public abstract static class ContainsNode extends PythonBinaryBuiltinNode {

        @Specialization(limit = "getCallSiteInlineCacheMaxDepth()")
        static boolean run(VirtualFrame frame, PDict self, Object key,
                        @Cached ConditionProfile hasFrame,
                        @CachedLibrary("self.getDictStorage()") HashingStorageLibrary lib) {
            return lib.hasKeyWithFrame(self.getDictStorage(), key, hasFrame, frame);
        }
    }

    @Builtin(name = __LEN__, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class LenNode extends PythonUnaryBuiltinNode {
        @Specialization(limit = "1")
        public static int len(PDict self,
                        @CachedLibrary("self.getDictStorage()") HashingStorageLibrary lib) {
            return lib.length(self.getDictStorage());
        }
    }

    // copy()
    @Builtin(name = "copy", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class CopyNode extends PythonUnaryBuiltinNode {

        @Specialization(limit = "1")
        public PDict copy(@SuppressWarnings("unused") VirtualFrame frame, PDict dict,
                        @CachedLibrary("dict.getDictStorage()") HashingStorageLibrary lib) {
            return factory().createDict(lib.copy(dict.getDictStorage()));
        }
    }

    // clear()
    @Builtin(name = "clear", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class ClearNode extends PythonUnaryBuiltinNode {

        @Specialization(limit = "3")
        public static PDict clear(PDict dict,
                        @CachedLibrary("dict.getDictStorage()") HashingStorageLibrary lib) {
            HashingStorage newStorage = lib.clear(dict.getDictStorage());
            dict.setDictStorage(newStorage);
            return dict;
        }
    }

    // values()
    @Builtin(name = VALUES, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class ValuesNode extends PythonUnaryBuiltinNode {

        @Specialization
        public PDictView values(PDict self) {
            return factory().createDictValuesView(self);
        }
    }

    // update()
    @Builtin(name = "update", minNumOfPositionalArgs = 1, takesVarArgs = true, takesVarKeywordArgs = true)
    @GenerateNodeFactory
    public abstract static class UpdateNode extends PythonBuiltinNode {

        @SuppressWarnings("unused")
        @Specialization(guards = {"args.length == 0", "kwargs.length == 0"})
        static Object updateEmpy(VirtualFrame frame, PDict self, Object[] args, PKeyword[] kwargs) {
            return PNone.NONE;
        }

        @Specialization(guards = {"args.length == 1", "kwargs.length == 0"})
        static Object update(VirtualFrame frame, PDict self, Object[] args, @SuppressWarnings("unused") PKeyword[] kwargs,
                        @Shared("updateNode") @Cached DictNodes.UpdateNode updateNode) {
            updateNode.execute(frame, self, args[0]);
            return PNone.NONE;
        }

        @Specialization(guards = {"args.length == 0", "kwargs.length > 0"})
        static Object update(VirtualFrame frame, PDict self, @SuppressWarnings("unused") Object[] args, PKeyword[] kwargs,
                        @Shared("initNode") @Cached HashingStorage.InitNode initNode,
                        @Shared("hlib") @CachedLibrary(limit = "3") HashingStorageLibrary lib) {
            updateKwargs(frame, self, kwargs, initNode, lib);
            return PNone.NONE;
        }

        @Specialization(guards = {"args.length == 1", "kwargs.length > 0"})
        static Object update(VirtualFrame frame, PDict self, Object[] args, PKeyword[] kwargs,
                        @Shared("updateNode") @Cached DictNodes.UpdateNode updateNode,
                        @Shared("initNode") @Cached HashingStorage.InitNode initNode,
                        @Shared("hlib") @CachedLibrary(limit = "3") HashingStorageLibrary lib) {
            updateNode.execute(frame, self, args[0]);
            updateKwargs(frame, self, kwargs, initNode, lib);
            return PNone.NONE;
        }

        @Specialization(guards = "args.length > 1")
        @SuppressWarnings("unused")
        Object error(PDict self, Object[] args, PKeyword[] kwargs) {
            throw raise(TypeError, ErrorMessages.EXPECTED_AT_MOST_D_ARGS_GOT_D, "update", 1, args.length);
        }

        private static void updateKwargs(VirtualFrame frame, PDict self, PKeyword[] kwargs, HashingStorage.InitNode initNode, HashingStorageLibrary lib) {
            HashingStorage storage = self.getDictStorage();
            storage = lib.addAllToOther(initNode.execute(frame, PNone.NO_VALUE, kwargs), storage);
            self.setDictStorage(storage);
        }
    }

    // fromkeys()
    @Builtin(name = "fromkeys", minNumOfPositionalArgs = 2, parameterNames = {"$cls", "iterable", "value"}, isClassmethod = true)
    @ImportStatic(SpecialMethodSlot.class)
    @GenerateNodeFactory
    public abstract static class FromKeysNode extends PythonTernaryBuiltinNode {

        @Specialization(guards = "isBuiltinDict(cls, isSameTypeNode)", limit = "1")
        public Object doKeys(VirtualFrame frame, Object cls, Object iterable, Object value,
                        @SuppressWarnings("unused") @Cached TypeNodes.IsSameTypeNode isSameTypeNode,
                        @Cached HashingCollectionNodes.GetClonedHashingStorageNode getHashingStorageNode) {
            HashingStorage s = getHashingStorageNode.execute(frame, iterable, value);
            return factory().createDict(cls, s);
        }

        @Fallback
        public Object doKeys(VirtualFrame frame, Object cls, Object iterable, Object value,
                        @Cached PyObjectGetIter getIter,
                        @Cached CallNode callCtor,
                        @Cached GetClassNode getClassNode,
                        @Cached(parameters = "SetItem") LookupSpecialMethodSlotNode lookupSetItem,
                        @Cached CallTernaryMethodNode callSetItem,
                        @Cached GetNextNode nextNode,
                        @Cached IsBuiltinClassProfile errorProfile) {
            Object dict = callCtor.execute(frame, cls);
            Object val = value == PNone.NO_VALUE ? PNone.NONE : value;
            Object it = getIter.execute(frame, iterable);
            Object setitemMethod = lookupSetItem.execute(frame, getClassNode.execute(dict), dict);
            if (setitemMethod != PNone.NO_VALUE) {
                while (true) {
                    try {
                        Object key = nextNode.execute(frame, it);
                        callSetItem.execute(frame, setitemMethod, dict, key, val);
                    } catch (PException e) {
                        e.expectStopIteration(errorProfile);
                        break;
                    }
                }
                return dict;
            } else {
                throw raise(TypeError, ErrorMessages.P_OBJ_DOES_NOT_SUPPORT_ITEM_ASSIGMENT, iterable);
            }
        }

        protected static boolean isBuiltinDict(Object cls, TypeNodes.IsSameTypeNode isSameTypeNode) {
            return isSameTypeNode.execute(PythonBuiltinClassType.PDict, cls);
        }
    }
}
