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
import static com.oracle.graal.python.nodes.SpecialMethodNames.__REPR__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__SETITEM__;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.KeyError;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.TypeError;

import java.util.List;

import com.oracle.graal.python.builtins.Builtin;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.PNotImplemented;
import com.oracle.graal.python.builtins.objects.common.HashingCollectionNodes;
import com.oracle.graal.python.builtins.objects.common.HashingStorage;
import com.oracle.graal.python.builtins.objects.common.HashingStorage.DictEntry;
import com.oracle.graal.python.builtins.objects.common.HashingStorageLibrary;
import com.oracle.graal.python.builtins.objects.function.PArguments;
import com.oracle.graal.python.builtins.objects.function.PKeyword;
import com.oracle.graal.python.builtins.objects.mappingproxy.PMappingproxy;
import com.oracle.graal.python.builtins.objects.str.PString;
import com.oracle.graal.python.nodes.PGuards;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.call.special.LookupAndCallBinaryNode;
import com.oracle.graal.python.nodes.call.special.LookupAndCallUnaryNode;
import com.oracle.graal.python.nodes.call.special.LookupAndCallUnaryNode.LookupAndCallUnaryDynamicNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonBinaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonTernaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonUnaryBuiltinNode;
import com.oracle.graal.python.nodes.util.CannotCastException;
import com.oracle.graal.python.nodes.util.CastToJavaStringNode;
import com.oracle.graal.python.runtime.PythonCore;
import com.oracle.graal.python.runtime.exception.PythonErrorType;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.CompilerDirectives.ValueType;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Exclusive;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.library.CachedLibrary;
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

        @Specialization(guards = "args.length == 1")
        Object doVarargs(VirtualFrame frame, PDict self, Object[] args, PKeyword[] kwargs) {
            self.setDictStorage(getInitNode().execute(frame, args[0], kwargs));
            return PNone.NONE;
        }

        @Specialization(guards = "args.length == 0")
        Object doKeywords(VirtualFrame frame, PDict self, @SuppressWarnings("unused") Object[] args, PKeyword[] kwargs) {
            self.setDictStorage(getInitNode().execute(frame, NO_VALUE, kwargs));
            return PNone.NONE;
        }

        @Specialization(guards = "args.length > 1")
        Object doGeneric(@SuppressWarnings("unused") PDict self, Object[] args, @SuppressWarnings("unused") PKeyword[] kwargs) {
            throw raise(TypeError, "dict expected at most 1 arguments, got %d", args.length);
        }
    }

    // setdefault(key[, default])
    @Builtin(name = "setdefault", minNumOfPositionalArgs = 2, parameterNames = {"self", "key", "default"})
    @GenerateNodeFactory
    public abstract static class SetDefaultNode extends PythonBuiltinNode {

        @Specialization(guards = "lib.hasKeyWithFrame(dict.getDictStorage(), key, hasFrame, frame)", limit = "3")
        public Object setDefault(VirtualFrame frame, PDict dict, Object key, @SuppressWarnings("unused") Object defaultValue,
                        @SuppressWarnings("unused") @Cached("createBinaryProfile()") ConditionProfile hasFrame,
                        @CachedLibrary("dict.getDictStorage()") HashingStorageLibrary lib) {
            return lib.getItemWithFrame(dict.getDictStorage(), key, hasFrame, frame);
        }

        @Specialization(guards = "!lib.hasKeyWithFrame(dict.getDictStorage(), key, hasFrame, frame)", limit = "3")
        public Object setDefault(VirtualFrame frame, PDict dict, Object key, Object defaultValue,
                        @Cached("create()") HashingCollectionNodes.SetItemNode setItemNode,
                        @SuppressWarnings("unused") @CachedLibrary("dict.getDictStorage()") HashingStorageLibrary lib,
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

        protected void removeItem(VirtualFrame frame, PDict dict, Object key,
                        HashingStorageLibrary lib, ConditionProfile hasFrame, BranchProfile updatedStorage) {
            HashingStorage storage = dict.getDictStorage();
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

        @SuppressWarnings("unused")
        @Specialization(guards = "isNoValue(arg1)", limit = "3")
        public Object pop(VirtualFrame frame, PDict dict, Object arg0, PNone arg1,
                        @Cached BranchProfile updatedStorage,
                        @Cached("createBinaryProfile()") ConditionProfile hasFrame,
                        @CachedLibrary("dict.getDictStorage()") HashingStorageLibrary lib) {
            return popDefault(frame, dict, arg0, PNone.NONE, updatedStorage, hasFrame, lib);
        }

        @Specialization(limit = "3")
        public Object popDefault(VirtualFrame frame, PDict dict, Object key, Object defaultValue,
                        @Cached BranchProfile updatedStorage,
                        @Cached("createBinaryProfile()") ConditionProfile hasFrame,
                        @CachedLibrary("dict.getDictStorage()") HashingStorageLibrary lib) {
            Object retVal = lib.getItemWithFrame(dict.getDictStorage(), key, hasFrame, frame);
            if (retVal != null) {
                removeItem(frame, dict, key, lib, hasFrame, updatedStorage);
                return retVal;
            } else {
                return defaultValue;
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
            for (DictEntry entry : lib.entries(dict.getDictStorage())) {
                return factory().createTuple(new Object[]{entry.getKey(), entry.getValue()});
            }
            throw raise(KeyError, "popitem(): dictionary is empty");
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

        @Specialization(guards = "!isNoValue(defaultValue)", limit = "1")
        public Object doWithDefault(VirtualFrame frame, PDict self, Object key, Object defaultValue,
                        @CachedLibrary(value = "self.getDictStorage()") HashingStorageLibrary hlib,
                        @Exclusive @Cached("createBinaryProfile()") ConditionProfile profile) {
            final Object value = hlib.getItemWithFrame(self.getDictStorage(), key, profile, frame);
            return value != null ? value : defaultValue;
        }

        @Specialization(limit = "1")
        public Object doNoDefault(VirtualFrame frame, PDict self, Object key, @SuppressWarnings("unused") PNone defaultValue,
                        @CachedLibrary(value = "self.getDictStorage()") HashingStorageLibrary hlib,
                        @Exclusive @Cached("createBinaryProfile()") ConditionProfile profile) {
            final Object value = hlib.getItemWithFrame(self.getDictStorage(), key, profile, frame);
            return value != null ? value : PNone.NONE;
        }
    }

    @Builtin(name = __GETITEM__, minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    public abstract static class GetItemNode extends PythonBinaryBuiltinNode {
        @Specialization(limit = "getCallSiteInlineCacheMaxDepth()")
        Object getItem(VirtualFrame frame, PDict self, Object key,
                        @CachedLibrary(value = "self.getDictStorage()") HashingStorageLibrary hlib,
                        @Exclusive @Cached("createBinaryProfile()") ConditionProfile profile,
                        @Cached("create(__MISSING__)") LookupAndCallBinaryNode specialNode) {
            final Object result = hlib.getItemWithFrame(self.getDictStorage(), key, profile, frame);
            if (result == null) {
                return specialNode.executeObject(frame, self, key);
            }
            return result;
        }
    }

    @Builtin(name = __MISSING__, minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    public abstract static class MissingNode extends PythonBinaryBuiltinNode {
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
        Object run(VirtualFrame frame, Object self, Object key,
                        @Cached("create(__REPR__)") LookupAndCallUnaryNode specialNode) {
            Object name = specialNode.executeObject(frame, key);
            if (!PGuards.isString(name)) {
                throw raise(TypeError, "__repr__ returned non-string (type %p)", name);
            }
            throw raise(KeyError, "%s", name);
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
                        @Cached("createBinaryProfile()") ConditionProfile hasFrame,
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
        @Specialization
        Object run(PDict self) {
            return factory().createDictKeysIterator(self);
        }
    }

    @Builtin(name = __EQ__, minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    public abstract static class EqNode extends PythonBinaryBuiltinNode {

        @Specialization(limit = "1")
        Object doDictDict(VirtualFrame frame, PDict self, PDict other,
                        @Cached("createBinaryProfile()") ConditionProfile hasFrame,
                        @CachedLibrary("self.getDictStorage()") HashingStorageLibrary lib) {
            if (hasFrame.profile(frame != null)) {
                return lib.equalsWithState(self.getDictStorage(), other.getDictStorage(), PArguments.getThreadState(frame));
            } else {
                return lib.equals(self.getDictStorage(), other.getDictStorage());
            }
        }

        @Specialization(limit = "1")
        Object doDictProxy(VirtualFrame frame, PDict self, PMappingproxy other,
                        @Cached("createBinaryProfile()") ConditionProfile hasFrame,
                        @CachedLibrary("self.getDictStorage()") HashingStorageLibrary lib) {
            if (hasFrame.profile(frame != null)) {
                return lib.equalsWithState(self.getDictStorage(), other.getDictStorage(), PArguments.getThreadState(frame));
            } else {
                return lib.equals(self.getDictStorage(), other.getDictStorage());
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
                        @CachedLibrary("self.getDictStorage()") HashingStorageLibrary lib) {
            return lib.hasKeyWithFrame(self.getDictStorage(), key, hasFrame, frame);
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
        public PDict clear(PDict dict,
                        @CachedLibrary("dict.getDictStorage()") HashingStorageLibrary lib) {
            lib.clear(dict.getDictStorage());
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

    @Builtin(name = __REPR__, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class ReprNode extends PythonUnaryBuiltinNode {
        @ValueType
        protected static final class ReprState {
            private final PDict self;
            private final HashingStorage dictStorage;
            private final StringBuilder result;

            ReprState(PDict self, HashingStorage dictStorage, StringBuilder result) {
                this.self = self;
                this.dictStorage = dictStorage;
                this.result = result;
            }
        }

        abstract static class EachRepr extends HashingStorageLibrary.ForEachNode<ReprState> {
            private final int limit;

            EachRepr(int limit) {
                this.limit = limit;
            }

            protected final int getLimit() {
                return limit;
            }

            public abstract ReprState executeReprState(Object key, ReprState arg);

            @Override
            public final ReprState execute(Object key, ReprState arg) {
                return executeReprState(key, arg);
            }

            @Specialization
            public ReprState dict(Object key, ReprState s,
                            @Cached LookupAndCallUnaryDynamicNode reprKeyNode,
                            @Cached LookupAndCallUnaryDynamicNode reprValueNode,
                            @Cached CastToJavaStringNode castStr,
                            @Cached PRaiseNode raiseNode,
                            @Cached("createBinaryProfile()") ConditionProfile lengthCheck,
                            @Cached BranchProfile nullKey,
                            @Cached BranchProfile nullValue,
                            @CachedLibrary(limit = "getLimit()") HashingStorageLibrary lib) {
                Object keyRepr = reprKeyNode.executeObject(key, __REPR__);
                String keyReprString;
                try {
                    keyReprString = castStr.execute(keyRepr);
                } catch (CannotCastException e) {
                    nullKey.enter();
                    throw raiseNode.raise(PythonErrorType.TypeError, "__repr__ returned non-string (type %s)", keyRepr);
                }

                Object value = lib.getItem(s.dictStorage, key);
                Object valueRepr = value != s.self ? reprValueNode.executeObject(value, __REPR__) : "{...}";
                String valueReprString;
                try {
                    valueReprString = castStr.execute(valueRepr);
                } catch (CannotCastException e) {
                    nullValue.enter();
                    throw raiseNode.raise(PythonErrorType.TypeError, "__repr__ returned non-string (type %s)", valueRepr);
                }

                // assuming '{' is inserted already
                if (lengthCheck.profile(s.result.length() > 1)) {
                    sbAppend(s.result, ", ");
                }
                sbAppend(s.result, keyReprString);
                sbAppend(s.result, ": ");
                sbAppend(s.result, valueReprString);
                return s;
            }

        }

        @Specialization(limit = "3") // use same limit as for EachRepr nodes library
        public Object repr(PDict self,
                        @Cached("create(3)") EachRepr consumerNode,
                        @CachedLibrary("self.getDictStorage()") HashingStorageLibrary lib) {
            StringBuilder keyValue = new StringBuilder("{");
            HashingStorage dictStorage = self.getDictStorage();
            lib.forEach(dictStorage, consumerNode, new ReprState(self, dictStorage, keyValue));
            return sbAppend(keyValue, "}").toString();
        }

        @TruffleBoundary
        private static StringBuilder sbAppend(StringBuilder sb, String s) {
            return sb.append(s);
        }
    }

}
