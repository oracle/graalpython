/*
 * Copyright (c) 2017, 2020, Oracle and/or its affiliates.
 * Copyright (c) 2014, Regents of the University of California
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
package com.oracle.graal.python.builtins.objects.set;

import static com.oracle.graal.python.nodes.SpecialMethodNames.__HASH__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__OR__;

import java.util.Iterator;
import java.util.List;

import com.oracle.graal.python.builtins.Builtin;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.common.HashingCollectionNodes;
import com.oracle.graal.python.builtins.objects.common.HashingStorageLibrary;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodes;
import com.oracle.graal.python.nodes.call.special.LookupAndCallBinaryNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.builtins.PythonBinaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonUnaryBuiltinNode;
import com.oracle.graal.python.runtime.PythonCore;
import com.oracle.graal.python.runtime.exception.PythonErrorType;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.library.CachedLibrary;

@CoreFunctions(extendClasses = PythonBuiltinClassType.PSet)
public final class SetBuiltins extends PythonBuiltins {

    @Override
    public void initialize(PythonCore core) {
        super.initialize(core);
        builtinConstants.put(__HASH__, PNone.NONE);
    }

    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return SetBuiltinsFactory.getFactories();
    }

    @Builtin(name = "clear", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class ClearNode extends PythonUnaryBuiltinNode {

        @Specialization
        public Object clear(PSet self, @CachedLibrary(limit = "1") HashingStorageLibrary lib) {
            lib.clear(self.getDictStorage());
            return PNone.NONE;
        }
    }

    @Builtin(name = "add", minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    public abstract static class AddNode extends PythonBinaryBuiltinNode {

        @Specialization
        public Object add(VirtualFrame frame, PSet self, Object o,
                        @Cached("create()") HashingCollectionNodes.SetItemNode setItemNode) {
            setItemNode.execute(frame, self, o, PNone.NONE);
            return PNone.NONE;
        }
    }

    @Builtin(name = __OR__, minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    public abstract static class OrNode extends PythonBinaryBuiltinNode {
        @Specialization
        Object doSet(VirtualFrame frame, PBaseSet self, PBaseSet other,
                        @Cached("create()") HashingStorageNodes.UnionNode unionNode) {
            return factory().createSet(unionNode.execute(frame, self.getDictStorage(), other.getDictStorage()));
        }

        @Specialization
        Object doReverse(VirtualFrame frame, PBaseSet self, Object other,
                        @Cached("create(__OR__)") LookupAndCallBinaryNode callOr) {
            return callOr.executeObject(frame, other, self);
        }
    }

    @Builtin(name = "remove", minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    abstract static class RemoveNode extends PythonBinaryBuiltinNode {
        @Specialization
        Object remove(VirtualFrame frame, PBaseSet self, Object other,
                        @Cached("create()") HashingStorageNodes.DelItemNode delItemNode) {

            if (!delItemNode.execute(frame, self, self.getDictStorage(), other)) {
                throw raise(PythonErrorType.KeyError, "%s", other);
            }
            return PNone.NONE;
        }
    }

    @Builtin(name = "discard", minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    abstract static class DiscardNode extends PythonBinaryBuiltinNode {
        @Specialization
        Object discard(VirtualFrame frame, PBaseSet self, Object other,
                        @Cached("create()") HashingStorageNodes.DelItemNode delItemNode) {

            delItemNode.execute(frame, self, self.getDictStorage(), other);
            return PNone.NONE;
        }
    }

    @Builtin(name = "pop", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class PopNode extends PythonUnaryBuiltinNode {
        @Specialization
        Object remove(VirtualFrame frame, PBaseSet self,
                        @CachedLibrary(limit = "1") HashingStorageLibrary lib,
                        @Cached("create()") HashingStorageNodes.DelItemNode delItemNode) {
            Iterator<Object> iterator = lib.keys(self.getDictStorage());
            if (iterator.hasNext()) {
                Object next = iterator.next();
                delItemNode.execute(frame, self, self.getDictStorage(), next);
                return next;
            }
            throw raise(PythonErrorType.KeyError, "pop from an emtpy set");
        }
    }

}
