/*
 * Copyright (c) 2017, 2018, Oracle and/or its affiliates.
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
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.PNotImplemented;
import com.oracle.graal.python.builtins.objects.common.HashingStorage;
import com.oracle.graal.python.builtins.objects.common.HashingStorage.DictEntry;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodes;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodes.ContainsKeyNode;
import com.oracle.graal.python.builtins.objects.function.PKeyword;
import com.oracle.graal.python.builtins.objects.str.PString;
import com.oracle.graal.python.nodes.PGuards;
import com.oracle.graal.python.nodes.call.special.LookupAndCallBinaryNode;
import com.oracle.graal.python.nodes.call.special.LookupAndCallUnaryNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonUnaryBuiltinNode;
import com.oracle.graal.python.runtime.exception.PythonErrorType;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.Specialization;

@CoreFunctions(extendClasses = PDict.class)
public final class DictBuiltins extends PythonBuiltins {

    @Override
    protected List<com.oracle.truffle.api.dsl.NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return DictBuiltinsFactory.getFactories();
    }

    @Builtin(name = __INIT__, minNumOfArguments = 1, takesVariableArguments = true, takesVariableKeywords = true)
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
        public Object doVarargs(PDict self, Object[] args, PKeyword[] kwargs) {
            getInitNode().execute(self, args[0], kwargs);
            return PNone.NONE;
        }

        @Specialization(guards = "args.length == 0")
        public Object doKeywords(PDict self, @SuppressWarnings("unused") Object[] args, PKeyword[] kwargs) {
            getInitNode().execute(self, NO_VALUE, kwargs);
            return PNone.NONE;
        }

        @Specialization(guards = "args.length > 1")
        public Object doGeneric(@SuppressWarnings("unused") PDict self, Object[] args, @SuppressWarnings("unused") PKeyword[] kwargs) {
            throw raise(TypeError, "dict expected at most 1 arguments, got %d", args.length);
        }
    }

    // setdefault(key[, default])
    @Builtin(name = "setdefault", fixedNumOfArguments = 3)
    @GenerateNodeFactory
    public abstract static class SetDefaultNode extends PythonBuiltinNode {
        @Child private HashingStorageNodes.ContainsKeyNode containsKeyNode;

        protected boolean containsKey(HashingStorage storage, Object key) {
            if (containsKeyNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                containsKeyNode = insert(ContainsKeyNode.create());
            }
            return containsKeyNode.execute(storage, key);
        }

        @Specialization(guards = "containsKey(dict.getDictStorage(), key)")
        public Object setDefault(PDict dict, Object key, @SuppressWarnings("unused") Object defaultValue,
                        @Cached("create()") HashingStorageNodes.GetItemNode getItemNode) {
            return getItemNode.execute(dict.getDictStorage(), key);
        }

        @Specialization(guards = "!containsKey(dict.getDictStorage(), key)")
        public Object setDefault(PDict dict, Object key, Object defaultValue,
                        @Cached("create()") HashingStorageNodes.SetItemNode setItemNode) {

            setItemNode.execute(dict, dict.getDictStorage(), key, defaultValue);
            return defaultValue;
        }
    }

    // pop(key[, default])
    @Builtin(name = "pop", minNumOfArguments = 2, maxNumOfArguments = 3)
    @GenerateNodeFactory
    public abstract static class PopNode extends PythonBuiltinNode {
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
        public Object pop(PDict dict, Object arg0, PNone arg1) {
            return popDefault(dict, arg0, PNone.NONE);
        }

        @Specialization
        public Object popDefault(PDict dict, Object key, Object defaultValue) {
            Object retVal = getGetItemNode().execute(dict.getDictStorage(), key);
            if (retVal != null) {
                getDelItemNode().execute(dict, dict.getDictStorage(), key);
                return retVal;
            } else {
                return defaultValue;
            }
        }
    }

    // popitem()
    @Builtin(name = "popitem", fixedNumOfArguments = 1)
    @GenerateNodeFactory
    public abstract static class PopItemNode extends PythonBuiltinNode {

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
    @Builtin(name = "keys", fixedNumOfArguments = 1)
    @GenerateNodeFactory
    public abstract static class KeysNode extends PythonUnaryBuiltinNode {

        @Specialization
        public PDictView keys(PDict self) {
            return factory().createDictKeysView(self);
        }
    }

    // items()
    @Builtin(name = "items", fixedNumOfArguments = 1)
    @GenerateNodeFactory
    public abstract static class ItemsNode extends PythonUnaryBuiltinNode {

        @Specialization
        public PDictView items(PDict self) {
            return factory().createDictItemsView(self);
        }
    }

    // get(key[, default])
    @Builtin(name = "get", minNumOfArguments = 2, maxNumOfArguments = 3)
    @GenerateNodeFactory
    public abstract static class GetNode extends PythonBuiltinNode {
        @Child private HashingStorageNodes.GetItemNode getItemNode;

        @Specialization(guards = "!isNoValue(defaultValue)")
        public Object doWithDefault(PDict self, Object key, Object defaultValue) {
            final Object value = getGetItemNode().execute(self.getDictStorage(), key);
            return value != null ? value : defaultValue;
        }

        @Specialization
        public Object doNoDefault(PDict self, Object key, @SuppressWarnings("unused") PNone defaultValue) {
            final Object value = getGetItemNode().execute(self.getDictStorage(), key);
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

    @Builtin(name = __GETITEM__, fixedNumOfArguments = 2)
    @GenerateNodeFactory
    public abstract static class GetItemNode extends PythonBuiltinNode {
        @Specialization
        Object getItem(PDict self, Object key,
                        @Cached("create()") HashingStorageNodes.GetItemNode getItemNode,
                        @Cached("create(__MISSING__)") LookupAndCallBinaryNode specialNode) {
            final Object result = getItemNode.execute(self.getDictStorage(), key);
            if (result == null) {
                return specialNode.executeObject(self, key);
            }
            return result;
        }
    }

    @Builtin(name = __MISSING__, fixedNumOfArguments = 2)
    @GenerateNodeFactory
    public abstract static class MissingNode extends PythonBuiltinNode {
        @SuppressWarnings("unused")
        @Specialization
        Object run(Object self, String key) {
            throw raise(KeyError, "%s", key);
        }

        @SuppressWarnings("unused")
        @Specialization(guards = "!isString(key)")
        Object run(Object self, Object key,
                        @Cached("create(__REPR__)") LookupAndCallUnaryNode specialNode) {
            Object name = specialNode.executeObject(key);
            if (!PGuards.isString(name)) {
                throw raise(TypeError, "__repr__ returned non-string (type %p)", name);
            }
            throw raise(KeyError, "%s", name);
        }
    }

    @Builtin(name = __SETITEM__, fixedNumOfArguments = 3)
    @GenerateNodeFactory
    public abstract static class SetItemNode extends PythonBuiltinNode {
        @Specialization
        Object run(PDict self, Object key, Object value,
                        @Cached("create()") HashingStorageNodes.SetItemNode setItemNode) {
            setItemNode.execute(self, self.getDictStorage(), key, value);
            return PNone.NONE;
        }
    }

    @Builtin(name = __DELITEM__, fixedNumOfArguments = 2)
    @GenerateNodeFactory
    public abstract static class DelItemNode extends PythonBuiltinNode {
        @Specialization
        Object run(PDict self, Object key,
                        @Cached("create()") HashingStorageNodes.DelItemNode delItemNode) {
            if (delItemNode.execute(self, self.getDictStorage(), key)) {
                return PNone.NONE;
            }
            throw getCore().raise(KeyError, this, "%s", key);
        }
    }

    @Builtin(name = __ITER__, fixedNumOfArguments = 1)
    @GenerateNodeFactory
    public abstract static class IterNode extends PythonUnaryBuiltinNode {
        @Specialization
        Object run(PDict self) {
            return factory().createDictKeysIterator(self);
        }
    }

    @Builtin(name = __EQ__, fixedNumOfArguments = 2)
    @GenerateNodeFactory
    public abstract static class EqNode extends PythonBuiltinNode {
        @Specialization
        Object doDictDict(PDict self, PDict other,
                        @Cached("create()") HashingStorageNodes.EqualsNode equalsNode) {
            return equalsNode.execute(self.getDictStorage(), other.getDictStorage());
        }

        @Fallback
        @SuppressWarnings("unused")
        Object doGeneric(Object self, Object other) {
            return PNotImplemented.NOT_IMPLEMENTED;
        }
    }

    @Builtin(name = __CONTAINS__, fixedNumOfArguments = 2)
    @GenerateNodeFactory
    public abstract static class ContainsNode extends PythonBuiltinNode {
        @Child private HashingStorageNodes.ContainsKeyNode containsKeyNode;

        @SuppressWarnings("unused")
        @Specialization(guards = "self.size() == 0")
        boolean runEmpty(PDict self, Object key) {
            return false;
        }

        @Specialization
        boolean run(PDict self, Object key) {
            if (containsKeyNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                containsKeyNode = insert(ContainsKeyNode.create());
            }
            return containsKeyNode.execute(self.getDictStorage(), key);
        }
    }

    @Builtin(name = __BOOL__, fixedNumOfArguments = 1)
    @GenerateNodeFactory
    public abstract static class BoolNode extends PythonUnaryBuiltinNode {
        @Specialization
        public boolean repr(PDict self) {
            return self.size() > 0;
        }
    }

    @Builtin(name = __LEN__, fixedNumOfArguments = 1)
    @GenerateNodeFactory
    public abstract static class LenNode extends PythonUnaryBuiltinNode {
        @Specialization
        public int len(PDict self) {
            return self.size();
        }
    }

    // copy()
    @Builtin(name = "copy", fixedNumOfArguments = 1)
    @GenerateNodeFactory
    public abstract static class CopyNode extends PythonBuiltinNode {

        @Specialization
        public PDict copy(PDict dict,
                        @Cached("create()") HashingStorageNodes.CopyNode copyNode) {
            return factory().createDict(copyNode.execute(dict.getDictStorage()));
        }
    }

    // clear()
    @Builtin(name = "clear", fixedNumOfArguments = 1)
    @GenerateNodeFactory
    public abstract static class ClearNode extends PythonBuiltinNode {

        @Specialization
        public PDict copy(PDict dict) {
            dict.getDictStorage().clear();
            return dict;
        }
    }

    // values()
    @Builtin(name = "values", fixedNumOfArguments = 1)
    @GenerateNodeFactory
    public abstract static class ValuesNode extends PythonUnaryBuiltinNode {

        @Specialization
        public PDictView values(PDict self) {
            return factory().createDictValuesView(self);
        }
    }

    @Builtin(name = __HASH__, fixedNumOfArguments = 1)
    @GenerateNodeFactory
    public abstract static class HashNode extends PythonBuiltinNode {
        @Specialization
        Object doGeneric(Object self) {
            throw raise(TypeError, "unhashable type: '%p'", self);
        }
    }

    @Builtin(name = __REPR__, fixedNumOfArguments = 1)
    @GenerateNodeFactory
    public abstract static class ReprNode extends PythonUnaryBuiltinNode {

        @Specialization
        @TruffleBoundary
        public Object repr(PDict self,
                        @Cached("create(__REPR__)") LookupAndCallUnaryNode reprKeyNode,
                        @Cached("create(__REPR__)") LookupAndCallUnaryNode reprValueNode,
                        @Cached("create()") HashingStorageNodes.GetItemNode next) {

            StringBuilder result = new StringBuilder("{");
            boolean initial = true;
            for (Object key : self.keys()) {
                Object value = next.execute(self.getDictStorage(), key);
                Object keyReprString = unwrap(reprKeyNode.executeObject(key));
                Object valueReprString = value != self ? unwrap(reprValueNode.executeObject(value)) : "{...}";

                checkString(keyReprString);
                checkString(valueReprString);

                if (initial) {
                    initial = false;
                } else {
                    result.append(", ");
                }
                result.append((String) keyReprString).append(": ").append((String) valueReprString);
            }
            return result.append('}').toString();
        }

        private void checkString(Object strObj) {
            if (!(strObj instanceof String)) {
                throw raise(PythonErrorType.TypeError, "__repr__ returned non-string (type %s)", strObj);
            }
        }

        private static Object unwrap(Object valueReprString) {
            if (valueReprString instanceof PString) {
                return ((PString) valueReprString).getValue();
            }
            return valueReprString;
        }
    }

}
