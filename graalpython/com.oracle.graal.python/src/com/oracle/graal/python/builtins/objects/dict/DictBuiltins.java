/*
 * Copyright (c) 2017, 2020, Oracle and/or its affiliates.
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
import static com.oracle.graal.python.nodes.SpecialMethodNames.__BOOL__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__CONTAINS__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__DELITEM__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__EQ__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__GETITEM__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__HASH__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__INIT__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__ITER__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__LEN__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__MISSING__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__REVERSED__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__SETITEM__;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.KeyError;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.RuntimeError;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.TypeError;

import java.util.List;

import com.oracle.graal.python.builtins.Builtin;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.PNotImplemented;
import com.oracle.graal.python.builtins.objects.common.EconomicMapStorage;
import com.oracle.graal.python.builtins.objects.common.HashingCollectionNodes;
import com.oracle.graal.python.builtins.objects.common.HashingCollectionNodes.GetDictStorageNode;
import com.oracle.graal.python.builtins.objects.common.HashingCollectionNodes.SetDictStorageNode;
import com.oracle.graal.python.builtins.objects.common.HashingStorage;
import com.oracle.graal.python.builtins.objects.common.HashingStorage.DictEntry;
import com.oracle.graal.python.builtins.objects.common.HashingStorage.StorageSupplier;
import com.oracle.graal.python.builtins.objects.common.HashingStorageLibrary;
import com.oracle.graal.python.builtins.objects.common.HashingStorageLibrary.HashingStorageIterator;
import com.oracle.graal.python.builtins.objects.common.SequenceNodes;
import com.oracle.graal.python.builtins.objects.function.PArguments;
import com.oracle.graal.python.builtins.objects.function.PBuiltinFunction;
import com.oracle.graal.python.builtins.objects.function.PKeyword;
import com.oracle.graal.python.builtins.objects.mappingproxy.PMappingproxy;
import com.oracle.graal.python.builtins.objects.method.PBuiltinMethod;
import com.oracle.graal.python.builtins.objects.method.PMethod;
import com.oracle.graal.python.builtins.objects.object.PythonObjectLibrary;
import com.oracle.graal.python.builtins.objects.str.PString;
import com.oracle.graal.python.builtins.objects.tuple.PTuple;
import com.oracle.graal.python.builtins.objects.type.PythonBuiltinClass;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PGuards;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.SpecialMethodNames;
import com.oracle.graal.python.nodes.builtins.ListNodes;
import com.oracle.graal.python.nodes.call.CallNode;
import com.oracle.graal.python.nodes.call.special.LookupAndCallBinaryNode;
import com.oracle.graal.python.nodes.call.special.LookupAndCallUnaryNode;
import com.oracle.graal.python.nodes.call.special.LookupAndCallVarargsNode;
import com.oracle.graal.python.nodes.control.GetIteratorExpressionNode.GetIteratorNode;
import com.oracle.graal.python.nodes.control.GetNextNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonBinaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonTernaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonUnaryBuiltinNode;
import com.oracle.graal.python.nodes.object.IsBuiltinClassProfile;
import com.oracle.graal.python.nodes.util.CastToJavaStringNode;
import com.oracle.graal.python.runtime.PythonCore;
import com.oracle.graal.python.runtime.exception.PException;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.ValueError;
import com.oracle.graal.python.runtime.sequence.PSequence;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Exclusive;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.profiles.BranchProfile;
import com.oracle.truffle.api.profiles.ConditionProfile;

@CoreFunctions(extendClasses = PythonBuiltinClassType.PDict)
public final class DictBuiltins extends PythonBuiltins {

    @Override
    public void initialize(PythonCore core) {
        super.initialize(core);
        builtinConstants.put(__HASH__, PNone.NONE);
    }

    @Override
    protected List<com.oracle.truffle.api.dsl.NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return DictBuiltinsFactory.getFactories();
    }

    @Builtin(name = __INIT__, minNumOfPositionalArgs = 1, takesVarArgs = true, takesVarKeywordArgs = true)
    @GenerateNodeFactory
    public abstract static class InitNode extends PythonBuiltinNode {

        @Child private HashingStorage.InitNode initNode;

        private HashingStorage.InitNode getInitNode() {
            if (initNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                initNode = insert(HashingStorage.InitNode.create());
            }
            return initNode;
        }

        @Specialization(guards = {"args.length == 1", "firstArgIterable(args, lib)", "!firstArgString(args)"})
        Object doVarargs(VirtualFrame frame, PDict self, Object[] args, PKeyword[] kwargs,
                        @Cached SetDictStorageNode setStorage,
                        @SuppressWarnings("unused") @CachedLibrary(limit = "1") PythonObjectLibrary lib) {
            setStorage.execute(self, getInitNode().execute(frame, args[0], kwargs));
            return PNone.NONE;
        }

        @SuppressWarnings("unused")
        @Specialization(guards = {"args.length == 1", "!firstArgIterable(args, lib)"})
        Object doVarargsNotIterable(Object self, Object[] args, @SuppressWarnings("unused") PKeyword[] kwargs,
                        @CachedLibrary(limit = "1") PythonObjectLibrary lib) {
            throw raise(TypeError, ErrorMessages.OBJ_NOT_ITERABLE, args[0]);
        }

        @SuppressWarnings("unused")
        @Specialization(guards = {"args.length == 1", "firstArgString(args)"})
        Object doString(Object self, Object[] args, PKeyword[] kwargs,
                        @Cached PRaiseNode raise) {
            throw raise.raise(ValueError, ErrorMessages.DICT_UPDATE_SEQ_ELEM_HAS_LENGTH_2_REQUIRED, 0, 1);
        }

        @Specialization(guards = "args.length == 0")
        Object doKeywords(VirtualFrame frame, PDict self, @SuppressWarnings("unused") Object[] args, PKeyword[] kwargs,
                        @Cached SetDictStorageNode setStorage) {
            setStorage.execute(self, getInitNode().execute(frame, NO_VALUE, kwargs));
            return PNone.NONE;
        }

        @Specialization(guards = "args.length > 1")
        Object doGeneric(@SuppressWarnings("unused") PDict self, Object[] args, @SuppressWarnings("unused") PKeyword[] kwargs) {
            throw raise(TypeError, ErrorMessages.EXPECTED_AT_MOST_D_ARGS_GOT_D, "dict", 1, args.length);
        }

        protected boolean firstArgIterable(Object[] args, PythonObjectLibrary lib) {
            return lib.isIterable(args[0]);
        }

        protected boolean firstArgString(Object[] args) {
            return PGuards.isString(args[0]);
        }
    }

    // setdefault(key[, default])
    @Builtin(name = "setdefault", minNumOfPositionalArgs = 2, parameterNames = {"self", "key", "default"})
    @GenerateNodeFactory
    public abstract static class SetDefaultNode extends PythonBuiltinNode {

        @Specialization(guards = "lib.hasKeyWithFrame(getStorage.execute(dict), key, hasFrame, frame)", limit = "3")
        public Object setDefault(VirtualFrame frame, PDict dict, Object key, @SuppressWarnings("unused") Object defaultValue,
                        @SuppressWarnings("unused") @Cached("createBinaryProfile()") ConditionProfile hasFrame,
                        @Cached GetDictStorageNode getStorage,
                        @CachedLibrary("getStorage.execute(dict)") HashingStorageLibrary lib) {
            return lib.getItemWithFrame(getStorage.execute(dict), key, hasFrame, frame);
        }

        @Specialization(guards = "!lib.hasKeyWithFrame(getStorage.execute(dict), key, hasFrame, frame)", limit = "3")
        public Object setDefault(VirtualFrame frame, PDict dict, Object key, Object defaultValue,
                        @Cached("create()") HashingCollectionNodes.SetItemNode setItemNode,
                        @SuppressWarnings("unused") @Cached GetDictStorageNode getStorage,
                        @SuppressWarnings("unused") @CachedLibrary("getStorage.execute(dict)") HashingStorageLibrary lib,
                        @SuppressWarnings("unused") @Cached("createBinaryProfile()") ConditionProfile hasFrame,
                        @Cached("createBinaryProfile()") ConditionProfile defaultValProfile) {
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

        protected void removeItem(VirtualFrame frame, PDict dict, Object key, HashingStorage storage,
                        HashingStorageLibrary lib, ConditionProfile hasFrame, BranchProfile updatedStorage, SetDictStorageNode setStorage) {
            HashingStorage newStore = null;
            // TODO: FIXME: this might call __hash__ twice
            boolean hasKey = lib.hasKeyWithFrame(storage, key, hasFrame, frame);
            if (hasKey) {
                newStore = lib.delItemWithFrame(storage, key, hasFrame, frame);
            }

            if (hasKey) {
                if (newStore != storage) {
                    updatedStorage.enter();
                    setStorage.execute(dict, newStore);
                }
            }
        }

        @Specialization(limit = "3")
        public Object popDefault(VirtualFrame frame, PDict dict, Object key, Object defaultValue,
                        @Cached BranchProfile updatedStorage,
                        @Cached ConditionProfile hasKey,
                        @Cached ConditionProfile hasDefault,
                        @Cached ConditionProfile hasFrame,
                        @Cached GetDictStorageNode getStorage,
                        @Cached SetDictStorageNode setStorage,
                        @CachedLibrary("getStorage.execute(dict)") HashingStorageLibrary lib) {
            HashingStorage dictStorage = getStorage.execute(dict);
            Object retVal = lib.getItemWithFrame(dictStorage, key, hasFrame, frame);
            if (hasKey.profile(retVal != null)) {
                removeItem(frame, dict, key, dictStorage, lib, hasFrame, updatedStorage, setStorage);
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
                        @Cached GetDictStorageNode getStorage,
                        @CachedLibrary("getStorage.execute(dict)") HashingStorageLibrary lib) {
            HashingStorage storage = getStorage.execute(dict);
            for (DictEntry entry : lib.entries(storage)) {
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
        public Object doWithDefault(VirtualFrame frame, PDict self, Object key, Object defaultValue,
                        @Cached GetDictStorageNode getStorage,
                        @CachedLibrary(value = "getStorage.execute(self)") HashingStorageLibrary hlib,
                        @Cached ConditionProfile profile) {
            final Object value = hlib.getItemWithFrame(getStorage.execute(self), key, profile, frame);
            return value != null ? value : (defaultValue == PNone.NO_VALUE ? PNone.NONE : defaultValue);
        }
    }

    @Builtin(name = __GETITEM__, minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    public abstract static class GetItemNode extends PythonBinaryBuiltinNode {
        @Specialization(limit = "getCallSiteInlineCacheMaxDepth()")
        Object getItem(VirtualFrame frame, PDict self, Object key,
                        @Cached GetDictStorageNode getStorage,
                        @CachedLibrary(value = "getStorage.execute(self)") HashingStorageLibrary hlib,
                        @Exclusive @Cached("createBinaryProfile()") ConditionProfile profile,
                        @Cached DispatchMissingNode missing) {
            final Object result = hlib.getItemWithFrame(getStorage.execute(self), key, profile, frame);
            if (result == null) {
                return missing.execute(frame, self, key);
            }
            return result;
        }
    }

    @ImportStatic(SpecialMethodNames.class)
    protected abstract static class DispatchMissingNode extends Node {

        protected abstract Object execute(VirtualFrame frame, Object self, Object key);

        @Specialization(guards = "hasMissing(self, lib)", limit = "1")
        protected Object misssing(Object self, Object key,
                        @CachedLibrary("self") PythonObjectLibrary lib,
                        @Exclusive @Cached CallNode callNode) {
            return callNode.execute(lib.lookupAttribute(self, __MISSING__), key);
        }

        @Specialization(guards = "!hasMissing(self, lib)", limit = "1")
        protected Object misssing(VirtualFrame frame, Object self, Object key,
                        @SuppressWarnings("unused") @CachedLibrary("self") PythonObjectLibrary lib,
                        @Exclusive @Cached DefaultMissingNode missing) {
            return missing.execute(frame, self, key);
        }

        protected boolean hasMissing(Object self, PythonObjectLibrary lib) {
            Object missing = lib.lookupAttribute(self, __MISSING__);
            return missing != PNone.NO_VALUE && missing instanceof PMethod;
        }
    }

    protected abstract static class DefaultMissingNode extends PythonBinaryBuiltinNode {
        @SuppressWarnings("unused")
        @Specialization
        Object run(Object self, PString key,
                        @Cached CastToJavaStringNode castStr) {
            throw raise(KeyError, "%s", castStr.execute(key));
        }

        @SuppressWarnings("unused")
        @Specialization
        Object run(Object self, String key) {
            throw raise(KeyError, "%s", key);
        }

        @SuppressWarnings("unused")
        @Specialization(guards = "!isString(key)")
        Object run(VirtualFrame frame, Object self, Object key) {
            throw raise(KeyError, new Object[]{key});
        }
    }

    @Builtin(name = __SETITEM__, minNumOfPositionalArgs = 3)
    @GenerateNodeFactory
    public abstract static class SetItemNode extends PythonTernaryBuiltinNode {
        @Specialization
        Object run(VirtualFrame frame, PDict self, Object key, Object value,
                        @Cached("create()") HashingCollectionNodes.SetItemNode setItemNode) {
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
                        @Cached SetDictStorageNode setStorage,
                        @Cached GetDictStorageNode getStorage,
                        @Cached("createBinaryProfile()") ConditionProfile hasFrame,
                        @CachedLibrary(limit = "3") HashingStorageLibrary lib) {
            HashingStorage storage = getStorage.execute(self);
            HashingStorage newStore = null;
            // TODO: FIXME: this might call __hash__ twice
            boolean hasKey = lib.hasKeyWithFrame(storage, key, hasFrame, frame);
            if (hasKey) {
                newStore = lib.delItemWithFrame(storage, key, hasFrame, frame);
            }

            if (hasKey) {
                if (newStore != storage) {
                    updatedStorage.enter();
                    setStorage.execute(self, newStore);
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
        Object run(PDict self,
                        @Cached HashingCollectionNodes.GetDictStorageNode getStore,
                        @CachedLibrary("getStore.execute(self)") HashingStorageLibrary lib) {
            HashingStorage storage = getStore.execute(self);
            return factory().createDictKeyIterator(lib.keys(storage).iterator(), storage, lib.length(storage));
        }
    }

    @Builtin(name = __REVERSED__, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class ReversedNode extends PythonUnaryBuiltinNode {
        @Specialization(limit = "getCallSiteInlineCacheMaxDepth()")
        Object run(PDict self,
                        @Cached HashingCollectionNodes.GetDictStorageNode getStore,
                        @CachedLibrary("getStore.execute(self)") HashingStorageLibrary lib) {
            HashingStorage storage = getStore.execute(self);
            return factory().createDictKeyIterator(lib.reverseKeys(storage).iterator(), storage, lib.length(storage));
        }
    }

    @Builtin(name = __EQ__, minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    public abstract static class EqNode extends PythonBinaryBuiltinNode {

        @Specialization(limit = "1")
        Object doDictDict(VirtualFrame frame, PDict self, PDict other,
                        @Cached("createBinaryProfile()") ConditionProfile hasFrame,
                        @Cached GetDictStorageNode getStorage,
                        @CachedLibrary("getStorage.execute(self)") HashingStorageLibrary lib) {
            if (hasFrame.profile(frame != null)) {
                return lib.equalsWithState(getStorage.execute(self), getStorage.execute(other), PArguments.getThreadState(frame));
            } else {
                return lib.equals(getStorage.execute(self), getStorage.execute(other));
            }
        }

        @Specialization(limit = "1")
        Object doDictProxy(VirtualFrame frame, PDict self, PMappingproxy other,
                        @Cached("createBinaryProfile()") ConditionProfile hasFrame,
                        @Cached GetDictStorageNode getStorage,
                        @CachedLibrary("getStorage.execute(self)") HashingStorageLibrary lib) {
            if (hasFrame.profile(frame != null)) {
                return lib.equalsWithState(getStorage.execute(self), getStorage.execute(other), PArguments.getThreadState(frame));
            } else {
                return lib.equals(getStorage.execute(self), getStorage.execute(other));
            }
        }

        @Fallback
        @SuppressWarnings("unused")
        PNotImplemented doGeneric(Object self, Object other) {
            return PNotImplemented.NOT_IMPLEMENTED;
        }
    }

    @Builtin(name = __CONTAINS__, minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    public abstract static class ContainsNode extends PythonBinaryBuiltinNode {

        @Specialization(limit = "getCallSiteInlineCacheMaxDepth()")
        boolean run(VirtualFrame frame, PDict self, Object key,
                        @Cached("createBinaryProfile()") ConditionProfile hasFrame,
                        @Cached GetDictStorageNode getStorage,
                        @CachedLibrary("getStorage.execute(self)") HashingStorageLibrary lib) {
            return lib.hasKeyWithFrame(getStorage.execute(self), key, hasFrame, frame);
        }
    }

    @Builtin(name = __BOOL__, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class BoolNode extends PythonUnaryBuiltinNode {
        @Specialization
        public boolean repr(PDict self,
                        @Cached HashingCollectionNodes.LenNode lenNode) {
            return lenNode.execute(self) > 0;
        }
    }

    @Builtin(name = __LEN__, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class LenNode extends PythonUnaryBuiltinNode {
        @Specialization(limit = "1")
        public int len(PDict self,
                        @Cached GetDictStorageNode getStorage,
                        @CachedLibrary("getStorage.execute(self)") HashingStorageLibrary lib) {
            return lib.length(getStorage.execute(self));
        }
    }

    // copy()
    @Builtin(name = "copy", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class CopyNode extends PythonUnaryBuiltinNode {

        @Specialization(limit = "1")
        public PDict copy(@SuppressWarnings("unused") VirtualFrame frame, PDict dict,
                        @Cached GetDictStorageNode getStorage,
                        @CachedLibrary("getStorage.execute(dict)") HashingStorageLibrary lib) {
            return factory().createDict(lib.copy(getStorage.execute(dict)));
        }
    }

    // clear()
    @Builtin(name = "clear", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class ClearNode extends PythonUnaryBuiltinNode {

        @Specialization(limit = "3")
        public PDict clear(PDict dict,
                        @Cached GetDictStorageNode getStorage,
                        @CachedLibrary("getStorage.execute(dict)") HashingStorageLibrary lib,
                        @Cached SetDictStorageNode setStorage) {
            HashingStorage newStorage = lib.clear(getStorage.execute(dict));
            setStorage.execute(dict, newStorage);
            return dict;
        }
    }

    // values()
    @Builtin(name = "values", minNumOfPositionalArgs = 1)
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
        public Object updateEmpy(VirtualFrame frame, PDict self, Object[] args, PKeyword[] kwargs) {
            return PNone.NONE;
        }

        @SuppressWarnings("unused")
        @Specialization(guards = {"isSelf(self, args)", "kwargs.length == 0"})
        public Object updateSelf(VirtualFrame frame, PDict self, Object[] args, PKeyword[] kwargs) {
            return PNone.NONE;
        }

        @Specialization(guards = {"args.length == 0", "kwargs.length > 0"})
        public Object update(VirtualFrame frame, PDict self, @SuppressWarnings("unused") Object[] args, PKeyword[] kwargs,
                        @Cached HashingStorage.InitNode initNode,
                        @CachedLibrary(limit = "3") HashingStorageLibrary lib,
                        @Cached GetDictStorageNode getStorage,
                        @Cached SetDictStorageNode setStorage) {
            HashingStorage storage = getStorage.execute(self);
            storage = lib.addAllToOther(initNode.execute(frame, PNone.NO_VALUE, kwargs), storage);
            setStorage.execute(self, storage);
            return PNone.NONE;
        }

        @Specialization(guards = {"isDictButNotEconomicMap(args, getStorage)", "kwargs.length == 0"})
        public Object updateDict(PDict self, Object[] args, @SuppressWarnings("unused") PKeyword[] kwargs,
                        @CachedLibrary(limit = "1") HashingStorageLibrary lib,
                        @Cached GetDictStorageNode getStorage,
                        @Cached SetDictStorageNode setStorage) {
            HashingStorage storage = lib.addAllToOther(getStorage.execute((PDict) args[0]), getStorage.execute(self));
            setStorage.execute(self, storage);
            return PNone.NONE;
        }

        @Specialization(guards = {"isDictButNotEconomicMap(args, getStorage)", "kwargs.length > 0"})
        public Object updateDict(VirtualFrame frame, PDict self, Object[] args, PKeyword[] kwargs,
                        @CachedLibrary(limit = "1") HashingStorageLibrary lib,
                        @Cached HashingStorage.InitNode initNode,
                        @Cached GetDictStorageNode getStorage,
                        @Cached SetDictStorageNode setStorage) {
            HashingStorage storage = lib.addAllToOther(getStorage.execute((PDict) args[0]), getStorage.execute(self));
            storage = lib.addAllToOther(initNode.execute(frame, PNone.NO_VALUE, kwargs), storage);
            setStorage.execute(self, storage);
            return PNone.NONE;
        }

        @Specialization(guards = {"isDictEconomicMap(args, getStorage)", "kwargs.length == 0"}, limit = "1")
        public Object updateDict(PDict self, Object[] args, @SuppressWarnings("unused") PKeyword[] kwargs,
                        @Cached GetDictStorageNode getStorage,
                        @Cached SetDictStorageNode setStorage,
                        @CachedLibrary("getStorage.execute(self)") HashingStorageLibrary libSelf,
                        @CachedLibrary(limit = "1") HashingStorageLibrary libOther) {
            HashingStorage newStorage = addAll(self, (PDict) args[0], getStorage, libSelf, libOther);
            setStorage.execute(self, newStorage);
            return PNone.NONE;
        }

        @Specialization(guards = {"isDictEconomicMap(args, getStorage)", "kwargs.length > 0"}, limit = "1")
        public Object updateDict(VirtualFrame frame, PDict self, Object[] args, PKeyword[] kwargs,
                        @Cached GetDictStorageNode getStorage,
                        @Cached SetDictStorageNode setStorage,
                        @CachedLibrary("getStorage.execute(self)") HashingStorageLibrary libSelf,
                        @CachedLibrary(limit = "1") HashingStorageLibrary libOther,
                        @Cached HashingStorage.InitNode initNode) {
            HashingStorage newStorage = addAll(self, (PDict) args[0], getStorage, libSelf, libOther);
            newStorage = libOther.addAllToOther(initNode.execute(frame, PNone.NO_VALUE, kwargs), newStorage);
            setStorage.execute(self, newStorage);
            return PNone.NONE;
        }

        private HashingStorage addAll(PDict self, PDict other, GetDictStorageNode getStorage, HashingStorageLibrary libSelf, HashingStorageLibrary libOther) throws PException {
            HashingStorage selfStorage = getStorage.execute(self);
            HashingStorage otherStorage = getStorage.execute(other);
            HashingStorageIterator<DictEntry> itOther = libOther.entries(otherStorage).iterator();
            int initialSize = libOther.length(otherStorage);
            HashingStorage newStorage = selfStorage;
            while (itOther.hasNext()) {
                DictEntry next = itOther.next();
                newStorage = libSelf.setItem(selfStorage, next.key, next.value);
                if (initialSize != libOther.length(otherStorage)) {
                    throw raise(RuntimeError, ErrorMessages.MUTATED_DURING_UPDATE, "dict");
                }
            }
            return newStorage;
        }

        @Specialization(guards = {"args.length == 1", "!isDict(args)", "hasKeysAttr(args, libArg)"})
        public Object updateMapping(VirtualFrame frame, PDict self, Object[] args, PKeyword[] kwargs,
                        @SuppressWarnings("unused") @CachedLibrary(limit = "1") PythonObjectLibrary libArg,
                        @CachedLibrary(limit = "3") HashingStorageLibrary lib,
                        @Cached("create(KEYS)") LookupAndCallUnaryNode callKeysNode,
                        @Cached("create(__GETITEM__)") LookupAndCallBinaryNode callGetItemNode,
                        @Cached GetIteratorNode getIteratorNode,
                        @Cached GetNextNode nextNode,
                        @Cached IsBuiltinClassProfile errorProfile,
                        @Cached GetDictStorageNode getStorage,
                        @Cached SetDictStorageNode setStorage) {
            HashingStorage storage = HashingStorage.copyToStorage(frame, args[0], kwargs, getStorage.execute(self),
                            callKeysNode, callGetItemNode, getIteratorNode, nextNode, errorProfile, lib);
            setStorage.execute(self, storage);
            return PNone.NONE;
        }

        @Specialization(guards = {"args.length == 1", "!isDict(args)", "!hasKeysAttr(args, libArg)"})
        public Object updateSequence(VirtualFrame frame, PDict self, Object[] args, PKeyword[] kwargs,
                        @SuppressWarnings("unused") @CachedLibrary(limit = "1") PythonObjectLibrary libArg,
                        @CachedLibrary(limit = "3") HashingStorageLibrary lib,
                        @Cached PRaiseNode raise,
                        @Cached GetIteratorNode getIterator,
                        @Cached GetNextNode nextNode,
                        @Cached ListNodes.FastConstructListNode createListNode,
                        @Cached("create(__GETITEM__)") LookupAndCallBinaryNode getItemNode,
                        @Cached SequenceNodes.LenNode seqLenNode,
                        @Cached("createBinaryProfile()") ConditionProfile lengthTwoProfile,
                        @Cached IsBuiltinClassProfile errorProfile,
                        @Cached IsBuiltinClassProfile isTypeErrorProfile,
                        @Cached GetDictStorageNode getStorage,
                        @Cached SetDictStorageNode setStorage) {
            StorageSupplier storageSupplier = (boolean isStringKey, int length) -> getStorage.execute(self);
            HashingStorage storage = HashingStorage.addSequenceToStorage(frame, args[0], kwargs, storageSupplier,
                            getIterator, nextNode, createListNode, seqLenNode, lengthTwoProfile, raise, getItemNode, isTypeErrorProfile, errorProfile, lib);
            setStorage.execute(self, storage);
            return PNone.NONE;
        }

        @SuppressWarnings("unused")
        @Specialization(guards = "!isIterable(args, libOther)")
        public Object notIterable(PDict self, Object[] args, PKeyword[] kwargs,
                        @CachedLibrary(limit = "1") PythonObjectLibrary libOther) {
            throw raise(TypeError, ErrorMessages.OBJ_NOT_ITERABLE, args[0]);
        }

        protected boolean isDict(Object[] args) {
            return args.length == 1 && args[0] instanceof PDict;
        }

        protected boolean isDictEconomicMap(Object[] args, GetDictStorageNode getStorage) {
            return args.length == 1 && args[0] instanceof PDict && getStorage.execute((PDict) args[0]) instanceof EconomicMapStorage;
        }

        protected boolean isDictButNotEconomicMap(Object[] args, GetDictStorageNode getStorage) {
            return args.length == 1 && args[0] instanceof PDict && !(getStorage.execute((PDict) args[0]) instanceof EconomicMapStorage);
        }

        protected boolean isSeq(Object[] args) {
            return args.length == 1 && args[0] instanceof PSequence;
        }

        protected boolean isIterable(Object[] args, PythonObjectLibrary lib) {
            return args.length == 1 && lib.isIterable(args[0]);
        }

        protected boolean isSelf(PDict self, Object[] args) {
            return isDict(args) && args[0] == self;
        }

        protected boolean hasKeysAttr(Object[] args, PythonObjectLibrary lib) {
            return lib.lookupAttribute(args[0], KEYS) != PNone.NO_VALUE;
        }

    }

    // fromkeys()
    @Builtin(name = "fromkeys", minNumOfPositionalArgs = 2, parameterNames = {"cls", "iterable", "value"}, isClassmethod = true)
    @GenerateNodeFactory
    public abstract static class FromKeysNode extends PythonBuiltinNode {

        @Specialization(guards = {"lib.isIterable(iterable)", "isBuiltinType(cls)", "hasBuiltinSetItem(cls, lib)"})
        public Object doKeys(VirtualFrame frame, Object cls, Object iterable, Object value,
                        @SuppressWarnings("unused") @CachedLibrary(limit = "2") PythonObjectLibrary lib,
                        @CachedLibrary(limit = "2") HashingStorageLibrary libStorage,
                        @Cached GetIteratorNode getIteratorNode,
                        @Cached GetNextNode nextNode,
                        @Cached IsBuiltinClassProfile errorProfile,
                        @Cached GetDictStorageNode getStorage,
                        @Cached SetDictStorageNode setStorage) {
            PDict dict = factory().createDict(cls);
            Object val = value == PNone.NO_VALUE ? PNone.NONE : value;
            HashingStorage storage = getStorage.execute(dict);
            Object it = getIteratorNode.executeWith(frame, iterable);
            while (true) {
                try {
                    Object key = nextNode.execute(frame, it);
                    storage = libStorage.setItem(storage, key, val);
                } catch (PException e) {
                    e.expectStopIteration(errorProfile);
                    break;
                }
            }
            setStorage.execute(dict, storage);
            return dict;
        }

        @Specialization(guards = {"lib.isIterable(iterable)", "!isBuiltinType(cls) || !hasBuiltinSetItem(cls, lib)"})
        public Object doKeys(VirtualFrame frame, Object cls, Object iterable, Object value,
                        @CachedLibrary(limit = "2") PythonObjectLibrary lib,
                        @Cached("create(__CALL__)") LookupAndCallVarargsNode constructNode,
                        @Cached CallNode callSetItemNode,
                        @Cached GetIteratorNode getIteratorNode,
                        @Cached GetNextNode nextNode,
                        @Cached IsBuiltinClassProfile errorProfile,
                        @Cached ConditionProfile noSetItemProfile) {
            Object dict = constructNode.execute(null, cls, new Object[]{cls});
            Object attrSetItem = lib.lookupAttribute(dict, __SETITEM__);
            Object val = value == PNone.NO_VALUE ? PNone.NONE : value;
            if (noSetItemProfile.profile(attrSetItem != PNone.NO_VALUE)) {
                Object it = getIteratorNode.executeWith(frame, iterable);
                while (true) {
                    try {
                        Object key = nextNode.execute(frame, it);
                        callSetItemNode.execute(frame, attrSetItem, key, val);
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

        @SuppressWarnings("unused")
        @Specialization(guards = "!lib.isIterable(iterable)")
        public Object notIterable(Object cls, Object iterable, Object value,
                        @CachedLibrary(limit = "1") PythonObjectLibrary lib) {
            throw raise(TypeError, ErrorMessages.OBJ_NOT_ITERABLE, iterable);
        }

        protected boolean isBuiltinType(Object cls) {
            PythonBuiltinClassType type = null;
            if (cls instanceof PythonBuiltinClass) {
                type = ((PythonBuiltinClass) cls).getType();
            } else if (cls instanceof PythonBuiltinClassType) {
                type = (PythonBuiltinClassType) cls;
            }
            return type == PythonBuiltinClassType.PDict;
        }

        protected boolean hasBuiltinSetItem(Object cls, PythonObjectLibrary lib) {
            Object attr = lib.lookupAttribute(cls, __SETITEM__);
            return attr instanceof PBuiltinMethod || attr instanceof PBuiltinFunction;
        }
    }
}
