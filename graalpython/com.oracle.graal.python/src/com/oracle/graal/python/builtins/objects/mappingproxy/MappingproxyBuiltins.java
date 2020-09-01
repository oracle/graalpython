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
package com.oracle.graal.python.builtins.objects.mappingproxy;

import static com.oracle.graal.python.nodes.SpecialMethodNames.ITEMS;
import static com.oracle.graal.python.nodes.SpecialMethodNames.KEYS;
import static com.oracle.graal.python.nodes.SpecialMethodNames.VALUES;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__CONTAINS__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__EQ__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__GETITEM__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__INIT__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__ITER__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__LEN__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__REPR__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__STR__;

import java.util.List;

import com.oracle.graal.python.builtins.Builtin;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.modules.BuiltinConstructors;
import com.oracle.graal.python.builtins.modules.BuiltinFunctions;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.function.PArguments;
import com.oracle.graal.python.builtins.objects.object.PythonObjectLibrary;
import com.oracle.graal.python.builtins.objects.str.PString;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonBinaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonUnaryBuiltinNode;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.library.CachedLibrary;

@CoreFunctions(extendClasses = PythonBuiltinClassType.PMappingproxy)
public final class MappingproxyBuiltins extends PythonBuiltins {

    @Override
    protected List<com.oracle.truffle.api.dsl.NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return MappingproxyBuiltinsFactory.getFactories();
    }

    @Builtin(name = __INIT__, minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    public abstract static class InitNode extends PythonBinaryBuiltinNode {

        @Specialization
        @SuppressWarnings("unused")
        Object doPMappingproxy(PMappingproxy self, Object mapping) {
            // nothing to do
            return PNone.NONE;
        }
    }

    @Builtin(name = __ITER__, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class IterNode extends PythonUnaryBuiltinNode {
        @Specialization(limit = "getCallSiteInlineCacheMaxDepth()")
        static Object iter(VirtualFrame frame, @SuppressWarnings("unused") PMappingproxy self,
                        @Bind("self.getMapping()") Object mapping,
                        @CachedLibrary("mapping") PythonObjectLibrary lib) {
            return lib.getIteratorWithState(mapping, PArguments.getThreadState(frame));
        }
    }

    // keys()
    @Builtin(name = KEYS, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class KeysNode extends PythonUnaryBuiltinNode {
        @Specialization(limit = "1")
        public Object items(VirtualFrame frame, PMappingproxy self,
                        @CachedLibrary("self.getMapping()") PythonObjectLibrary lib) {
            return lib.lookupAndCallRegularMethod(self.getMapping(), frame, "keys");
        }
    }

    // items()
    @Builtin(name = ITEMS, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class ItemsNode extends PythonUnaryBuiltinNode {
        @Specialization(limit = "1")
        public Object items(VirtualFrame frame, PMappingproxy self,
                        @CachedLibrary("self.getMapping()") PythonObjectLibrary lib) {
            return lib.lookupAndCallRegularMethod(self.getMapping(), frame, ITEMS);
        }
    }

    // values()
    @Builtin(name = VALUES, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class ValuesNode extends PythonUnaryBuiltinNode {
        @Specialization(limit = "1")
        public Object values(VirtualFrame frame, PMappingproxy self,
                        @CachedLibrary("self.getMapping()") PythonObjectLibrary lib) {
            return lib.lookupAndCallRegularMethod(self.getMapping(), frame, VALUES);
        }
    }

    // get(key[, default])
    @Builtin(name = "get", minNumOfPositionalArgs = 2, maxNumOfPositionalArgs = 3)
    @GenerateNodeFactory
    public abstract static class GetNode extends PythonBuiltinNode {
        @Specialization(guards = "isNoValue(defaultValue)", limit = "1")
        public Object get(VirtualFrame frame, PMappingproxy self, Object key, @SuppressWarnings("unused") PNone defaultValue,
                        @CachedLibrary("self.getMapping()") PythonObjectLibrary lib) {
            return lib.lookupAndCallRegularMethod(self.getMapping(), frame, "get", key);
        }

        @Specialization(guards = "!isNoValue(defaultValue)", limit = "1")
        public Object get(VirtualFrame frame, PMappingproxy self, Object key, Object defaultValue,
                        @CachedLibrary("self.getMapping()") PythonObjectLibrary lib) {
            return lib.lookupAndCallRegularMethod(self.getMapping(), frame, "get", key, defaultValue);
        }
    }

    @Builtin(name = __GETITEM__, minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    public abstract static class GetItemNode extends PythonBinaryBuiltinNode {
        @Specialization
        Object getItem(VirtualFrame frame, PMappingproxy self, Object key,
                        @Cached com.oracle.graal.python.nodes.subscript.GetItemNode getItemNode) {
            return getItemNode.execute(frame, self.getMapping(), key);
        }
    }

    @Builtin(name = __CONTAINS__, minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    public abstract static class ContainsNode extends PythonBuiltinNode {
        @Specialization
        Object run(VirtualFrame frame, PMappingproxy self, Object key,
                        @Cached com.oracle.graal.python.nodes.expression.ContainsNode containsNode) {
            return containsNode.executeWith(frame, key, self.getMapping());
        }
    }

    @Builtin(name = __LEN__, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class LenNode extends PythonUnaryBuiltinNode {
        @Specialization(limit = "1")
        public int len(VirtualFrame frame, PMappingproxy self,
                        @CachedLibrary("self.getMapping()") PythonObjectLibrary lib) {
            return lib.lengthWithFrame(self.getMapping(), frame);
        }
    }

    // copy()
    @Builtin(name = "copy", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class CopyNode extends PythonUnaryBuiltinNode {
        @Specialization(limit = "1")
        public Object copy(VirtualFrame frame, PMappingproxy self,
                        @CachedLibrary("self.getMapping()") PythonObjectLibrary lib) {
            return lib.lookupAndCallRegularMethod(self.getMapping(), frame, "copy");
        }
    }

    @Builtin(name = __EQ__, minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    public abstract static class EqNode extends PythonBinaryBuiltinNode {
        @Specialization(limit = "3")
        Object eq(VirtualFrame frame, PMappingproxy self, Object other,
                        @CachedLibrary("self.getMapping()") PythonObjectLibrary lib,
                        @CachedLibrary("other") PythonObjectLibrary otherLib) {
            return lib.equalsWithFrame(self.getMapping(), other, otherLib, frame);
        }
    }

    @Builtin(name = __STR__, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class StrNode extends PythonUnaryBuiltinNode {
        @Specialization
        public Object str(VirtualFrame frame, PMappingproxy self,
                        @Cached BuiltinConstructors.StrNode strNode) {
            return strNode.executeWith(frame, self.getMapping());
        }
    }

    @Builtin(name = __REPR__, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class ReprNode extends PythonUnaryBuiltinNode {
        @Specialization
        public String repr(VirtualFrame frame, PMappingproxy self,
                        @Cached BuiltinFunctions.ReprNode reprNode) {
            Object mappingRepr = reprNode.call(frame, self.getMapping());
            return PString.cat("mappingproxy(", mappingRepr, ")");
        }
    }
}
