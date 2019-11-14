/*
 * Copyright (c) 2017, 2019, Oracle and/or its affiliates.
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
import com.oracle.graal.python.builtins.objects.common.HashingStorage.DictEntry;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodes;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodes.ContainsKeyNode;
import com.oracle.graal.python.builtins.objects.function.PKeyword;
import com.oracle.graal.python.builtins.objects.mappingproxy.PMappingproxy;
import com.oracle.graal.python.builtins.objects.str.PString;
import com.oracle.graal.python.nodes.PGuards;
import com.oracle.graal.python.nodes.call.special.LookupAndCallBinaryNode;
import com.oracle.graal.python.nodes.call.special.LookupAndCallUnaryNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonBinaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonTernaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonUnaryBuiltinNode;
import com.oracle.graal.python.runtime.PythonCore;
import com.oracle.graal.python.runtime.exception.PythonErrorType;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
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

        @Child private HashingStorageNodes.InitNode initNode;

        private HashingStorageNodes.InitNode getInitNode() {
            if (initNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                initNode = insert(HashingStorageNodes.InitNode.create());
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
        @Child private HashingStorageNodes.ContainsKeyNode containsKeyNode;

        protected boolean containsKey(VirtualFrame frame, HashingStorage storage, Object key) {
            if (containsKeyNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                containsKeyNode = insert(ContainsKeyNode.create());
            }
            return containsKeyNode.execute(frame, storage, key);
        }

        @Specialization(guards = "containsKey(frame, dict.getDictStorage(), key)")
        public Object setDefault(VirtualFrame frame, PDict dict, Object key, @SuppressWarnings("unused") Object defaultValue,
                        @Cached("create()") HashingStorageNodes.GetItemNode getItemNode) {
            return getItemNode.execute(frame, dict.getDictStorage(), key);
        }

        @Specialization(guards = "!containsKey(frame, dict.getDictStorage(), key)")
        public Object setDefault(VirtualFrame frame, PDict dict, Object key, Object defaultValue,
                        @Cached("create()") HashingCollectionNodes.SetItemNode setItemNode,
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
        @Child private HashingStorageNodes.GetItemNode getItemNode;
        @Child private HashingStorageNodes.DelItemNode delItemNode;

        private HashingStorageNodes.GetItemNode getGetItemNode() {
            if (getItemNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                getItemNode = insert(HashingStorageNodes.GetItemNode.create());
            }
            return getItemNode;
        }

        private HashingStorageNodes.DelItemNode getDelItemNode() {
            if (delItemNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                delItemNode = insert(HashingStorageNodes.DelItemNode.create());
            }
            return delItemNode;
        }

        @SuppressWarnings("unused")
        @Specialization(guards = "isNoValue(arg1)")
        public Object pop(VirtualFrame frame, PDict dict, Object arg0, PNone arg1) {
            return popDefault(frame, dict, arg0, PNone.NONE);
        }

        @Specialization
        public Object popDefault(VirtualFrame frame, PDict dict, Object key, Object defaultValue) {
            Object retVal = getGetItemNode().execute(frame, dict.getDictStorage(), key);
            if (retVal != null) {
                getDelItemNode().execute(frame, dict, dict.getDictStorage(), key);
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

        @Specialization
        @TruffleBoundary
        public Object popItem(PDict dict) {
            Iterator<DictEntry> iterator = dict.getDictStorage().entries().iterator();
            if (iterator.hasNext()) {
                DictEntry entry = iterator.next();
                return factory().createTuple(new Object[]{entry.getKey(), entry.getValue()});
            } else {
                throw raise(KeyError, "popitem(): dictionary is empty");
            }
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
        @Child private HashingStorageNodes.GetItemNode getItemNode;

        @Specialization(guards = "!isNoValue(defaultValue)")
        public Object doWithDefault(VirtualFrame frame, PDict self, Object key, Object defaultValue) {
            final Object value = getGetItemNode().execute(frame, self.getDictStorage(), key);
            return value != null ? value : defaultValue;
        }

        @Specialization
        public Object doNoDefault(VirtualFrame frame, PDict self, Object key, @SuppressWarnings("unused") PNone defaultValue) {
            final Object value = getGetItemNode().execute(frame, self.getDictStorage(), key);
            return value != null ? value : PNone.NONE;
        }

        private HashingStorageNodes.GetItemNode getGetItemNode() {
            if (getItemNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                getItemNode = insert(HashingStorageNodes.GetItemNode.create());
            }
            return getItemNode;
        }
    }

    @Builtin(name = __GETITEM__, minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    public abstract static class GetItemNode extends PythonBinaryBuiltinNode {
        @Specialization
        Object getItem(VirtualFrame frame, PDict self, Object key,
                        @Cached("create()") HashingStorageNodes.GetItemNode getItemNode,
                        @Cached("create(__MISSING__)") LookupAndCallBinaryNode specialNode) {
            final Object result = getItemNode.execute(frame, self.getDictStorage(), key);
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
        Object run(Object self, PString key) {
            throw raise(KeyError, "%s", key.getValue());
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
                        @Cached("create()") HashingStorageNodes.DelItemNode delItemNode) {
            if (delItemNode.execute(frame, self, self.getDictStorage(), key)) {
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
        @Specialization
        Object doDictDict(VirtualFrame frame, PDict self, PDict other,
                        @Cached("create()") HashingStorageNodes.EqualsNode equalsNode) {
            return equalsNode.execute(frame, self.getDictStorage(), other.getDictStorage());
        }

        @Specialization
        Object doDictProxy(VirtualFrame frame, PDict self, PMappingproxy other,
                        @Cached("create()") HashingStorageNodes.EqualsNode equalsNode) {
            return equalsNode.execute(frame, self.getDictStorage(), other.getDictStorage());
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
        @Child private HashingStorageNodes.ContainsKeyNode containsKeyNode;

        @Specialization
        boolean run(VirtualFrame frame, PDict self, Object key) {
            if (containsKeyNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                containsKeyNode = insert(ContainsKeyNode.create());
            }
            return containsKeyNode.execute(frame, self.getDictStorage(), key);
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
        @Specialization
        public int len(PDict self,
                        @Cached("create()") HashingStorageNodes.LenNode lenNode) {
            return lenNode.execute(self.getDictStorage());
        }
    }

    // copy()
    @Builtin(name = "copy", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class CopyNode extends PythonUnaryBuiltinNode {

        @Specialization
        public PDict copy(VirtualFrame frame, PDict dict,
                        @Cached("create()") HashingStorageNodes.CopyNode copyNode) {
            return factory().createDict(copyNode.execute(frame, dict.getDictStorage()));
        }
    }

    // clear()
    @Builtin(name = "clear", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class ClearNode extends PythonUnaryBuiltinNode {

        @Specialization
        public PDict copy(PDict dict) {
            dict.getDictStorage().clear();
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

        @Specialization
        public Object repr(VirtualFrame frame, PDict self,
                        @Cached("create(__REPR__)") LookupAndCallUnaryNode reprKeyNode,
                        @Cached("create(__REPR__)") LookupAndCallUnaryNode reprValueNode,
                        @Cached("create()") HashingStorageNodes.GetItemNode next) {

            StringBuilder result = new StringBuilder();
            sbAppend(result, "{");
            boolean initial = true;
            for (Object key : self.keys()) {
                Object value = next.execute(frame, self.getDictStorage(), key);
                Object keyReprString = unwrap(reprKeyNode.executeObject(frame, key));
                Object valueReprString = value != self ? unwrap(reprValueNode.executeObject(frame, value)) : "{...}";

                checkString(keyReprString);
                checkString(valueReprString);

                if (initial) {
                    initial = false;
                } else {
                    sbAppend(result, ", ");
                }
                result.append((String) keyReprString).append(": ").append((String) valueReprString);
            }
            return sbAppend(result, "}").toString();
        }

        private void checkString(Object strObj) {
            if (!(strObj instanceof String)) {
                throw raise(PythonErrorType.TypeError, "__repr__ returned non-string (type %s)", strObj);
            }
        }

        @TruffleBoundary
        private static StringBuilder sbAppend(StringBuilder sb, String s) {
            return sb.append(s);
        }

        private static Object unwrap(Object valueReprString) {
            if (valueReprString instanceof PString) {
                return ((PString) valueReprString).getValue();
            }
            return valueReprString;
        }
    }

}
