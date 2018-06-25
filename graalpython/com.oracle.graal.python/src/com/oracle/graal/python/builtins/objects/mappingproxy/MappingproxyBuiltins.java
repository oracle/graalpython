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
package com.oracle.graal.python.builtins.objects.mappingproxy;

import static com.oracle.graal.python.nodes.SpecialMethodNames.KEYS;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__BOOL__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__CONTAINS__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__DELITEM__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__GETITEM__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__INIT__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__LEN__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__SETITEM__;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.KeyError;

import java.util.List;

import com.oracle.graal.python.builtins.Builtin;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.dict.PDict;
import com.oracle.graal.python.builtins.objects.list.PList;
import com.oracle.graal.python.builtins.objects.object.PythonObject;
import com.oracle.graal.python.builtins.objects.tuple.PTuple;
import com.oracle.graal.python.nodes.attributes.DeleteAttributeNode;
import com.oracle.graal.python.nodes.attributes.ReadAttributeFromObjectNode;
import com.oracle.graal.python.nodes.attributes.WriteAttributeToObjectNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonBinaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonUnaryBuiltinNode;
import com.oracle.graal.python.runtime.exception.PythonErrorType;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.profiles.ConditionProfile;

@CoreFunctions(extendClasses = PMappingproxy.class)
public final class MappingproxyBuiltins extends PythonBuiltins {

    @Override
    protected List<com.oracle.truffle.api.dsl.NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return MappingproxyBuiltinsFactory.getFactories();
    }

    @Builtin(name = __INIT__, fixedNumOfArguments = 2)
    @GenerateNodeFactory
    public abstract static class InitNode extends PythonBinaryBuiltinNode {

        @Specialization
        @SuppressWarnings("unused")
        Object doPDict(PMappingproxy self, PDict mapping) {
            // nothing to do
            return PNone.NONE;
        }

        @Specialization
        @SuppressWarnings("unused")
        Object doPTuple(PMappingproxy self, PTuple mapping) {
            // nothing to do
            return PNone.NONE;
        }

        @Specialization
        @SuppressWarnings("unused")
        Object doPList(PMappingproxy self, PList mapping) {
            // nothing to do
            return PNone.NONE;
        }

        @Fallback
        Object doGeneric(@SuppressWarnings("unused") Object self, Object o) {
            throw raise(PythonErrorType.TypeError, "mappingproxy() argument must be a mapping, not %p", o);
        }
    }

    // keys()
    @Builtin(name = KEYS, fixedNumOfArguments = 1)
    @GenerateNodeFactory
    public abstract static class KeysNode extends PythonBuiltinNode {
        @Specialization
        public Object keys(PMappingproxy self) {
            PythonObject object = self.getObject();
            return factory().createList(createKeys(object));
        }

        @TruffleBoundary
        private static Object[] createKeys(PythonObject object) {
            return object.getAttributeNames().toArray();
        }
    }

    // get(key[, default])
    @Builtin(name = "get", minNumOfArguments = 2, maxNumOfArguments = 3)
    @GenerateNodeFactory
    public abstract static class GetNode extends PythonBuiltinNode {
        @Specialization
        public Object get(PMappingproxy self, Object key, Object defaultValue,
                        @Cached("create()") ReadAttributeFromObjectNode readNode,
                        @Cached("createBinaryProfile()") ConditionProfile profile) {
            Object value = readNode.execute(self.getObject(), key);
            if (profile.profile(value == PNone.NO_VALUE)) {
                return defaultValue;
            } else {
                return value;
            }
        }
    }

    @Builtin(name = __GETITEM__, fixedNumOfArguments = 2)
    @GenerateNodeFactory
    public abstract static class GetItemNode extends PythonBuiltinNode {
        @Specialization
        Object getItem(PMappingproxy self, Object key,
                        @Cached("create()") ReadAttributeFromObjectNode readNode,
                        @Cached("createBinaryProfile()") ConditionProfile profile) {
            Object result = readNode.execute(self.getObject(), key);
            if (profile.profile(result == PNone.NO_VALUE)) {
                throw raise(KeyError, "%s", key);
            }
            return result;
        }
    }

    @Builtin(name = __SETITEM__, fixedNumOfArguments = 3)
    @GenerateNodeFactory
    public abstract static class SetItemNode extends PythonBuiltinNode {
        @Specialization
        Object run(PMappingproxy self, Object key, Object value,
                        @Cached("create()") WriteAttributeToObjectNode writeNode) {
            writeNode.execute(self.getObject(), key, value);
            return PNone.NONE;
        }
    }

    @Builtin(name = __DELITEM__, fixedNumOfArguments = 2)
    @GenerateNodeFactory
    public abstract static class DelItemNode extends PythonBuiltinNode {
        @Specialization
        Object run(PMappingproxy self, Object key,
                        @Cached("create()") DeleteAttributeNode delAttributeNode) {
            delAttributeNode.execute(self.getObject(), key);
            return PNone.NONE;
        }
    }

    @Builtin(name = __CONTAINS__, fixedNumOfArguments = 2)
    @GenerateNodeFactory
    public abstract static class ContainsNode extends PythonBuiltinNode {
        @Specialization
        boolean contains(PMappingproxy self, Object key,
                        @Cached("create()") ReadAttributeFromObjectNode readNode) {
            return readNode.execute(self.getObject(), key) != PNone.NO_VALUE;
        }
    }

    @Builtin(name = __BOOL__, fixedNumOfArguments = 1)
    @GenerateNodeFactory
    public abstract static class BoolNode extends PythonUnaryBuiltinNode {
        @Specialization
        public boolean repr(PMappingproxy self) {
            return self.getStorage().size() > 0;
        }
    }

    @Builtin(name = __LEN__, fixedNumOfArguments = 1)
    @GenerateNodeFactory
    public abstract static class LenNode extends PythonUnaryBuiltinNode {
        @Specialization
        public int len(PMappingproxy self) {
            return self.getStorage().size();
        }
    }

    // copy()
    @Builtin(name = "copy", fixedNumOfArguments = 1)
    @GenerateNodeFactory
    public abstract static class CopyNode extends PythonBuiltinNode {
        @Specialization
        public PMappingproxy copy(PMappingproxy proxy) {
            return factory().createMappingproxy(proxy.getPythonClass(), proxy.getObject());
        }
    }

}
