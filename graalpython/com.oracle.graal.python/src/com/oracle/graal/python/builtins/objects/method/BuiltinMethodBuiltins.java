/*
 * Copyright (c) 2017, 2019, Oracle and/or its affiliates.
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

package com.oracle.graal.python.builtins.objects.method;

import static com.oracle.graal.python.nodes.SpecialMethodNames.__REDUCE__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__REPR__;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.TypeError;

import java.util.List;

import com.oracle.graal.python.builtins.Builtin;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.function.AbstractFunctionBuiltins;
import com.oracle.graal.python.builtins.objects.function.PFunction;
import com.oracle.graal.python.builtins.objects.module.PythonModule;
import com.oracle.graal.python.builtins.objects.type.TypeNodes.GetNameNode;
import com.oracle.graal.python.nodes.SpecialAttributeNames;
import com.oracle.graal.python.nodes.attributes.GetAttributeNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.builtins.PythonUnaryBuiltinNode;
import com.oracle.graal.python.nodes.object.GetLazyClassNode;
import com.oracle.graal.python.nodes.util.CastToStringNode;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;

@CoreFunctions(extendClasses = {PythonBuiltinClassType.PBuiltinMethod})
public class BuiltinMethodBuiltins extends PythonBuiltins {

    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return BuiltinMethodBuiltinsFactory.getFactories();
    }

    @Builtin(name = __REPR__, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class ReprNode extends PythonUnaryBuiltinNode {
        boolean isBuiltinFunction(PBuiltinMethod self) {
            return self.getSelf() instanceof PythonModule;
        }

        boolean isBuiltinFunction(PMethod self) {
            return self.getSelf() instanceof PythonModule && self.getFunction() instanceof PFunction && ((PFunction) self.getFunction()).getEnclosingClassName() == null;
        }

        @Specialization(guards = "isBuiltinFunction(self)")
        Object reprBuiltinFunction(VirtualFrame frame, PMethod self,
                        @Cached("createGetAttributeNode()") GetAttributeNode getNameNode) {
            // (tfel): this only happens for builtin modules ... I think
            return strFormat("<built-in function %s>", getNameNode.executeObject(frame, self.getFunction()));
        }

        @Specialization(guards = "isBuiltinFunction(self)")
        String reprBuiltinFunction(VirtualFrame frame, PBuiltinMethod self,
                        @Cached("createGetAttributeNode()") GetAttributeNode getNameNode) {
            return strFormat("<built-in function %s>", getNameNode.executeObject(frame, self.getFunction()));
        }

        @Specialization(guards = "!isBuiltinFunction(self)")
        Object reprBuiltinMethod(VirtualFrame frame, PBuiltinMethod self,
                        @Cached("create()") GetLazyClassNode getClassNode,
                        @Cached("createGetAttributeNode()") GetAttributeNode getNameNode,
                        @Cached("create()") GetNameNode getTypeNameNode) {
            String typeName = getTypeNameNode.execute(getClassNode.execute(self.getSelf()));
            return strFormat("<built-in method %s of %s object at 0x%x>", getNameNode.executeObject(frame, self.getFunction()), typeName, hashCode(self));
        }

        @Specialization(guards = "!isBuiltinFunction(self)")
        Object reprBuiltinMethod(VirtualFrame frame, PMethod self,
                        @Cached("create()") GetLazyClassNode getClassNode,
                        @Cached("createGetAttributeNode()") GetAttributeNode getNameNode,
                        @Cached("create()") GetNameNode getTypeNameNode) {
            String typeName = getTypeNameNode.execute(getClassNode.execute(self.getSelf()));
            return strFormat("<built-in method %s of %s object at 0x%x>", getNameNode.executeObject(frame, self.getFunction()), typeName, hashCode(self));
        }

        @TruffleBoundary(allowInlining = true)
        private static int hashCode(Object self) {
            return self.hashCode();
        }

        @TruffleBoundary
        private static String strFormat(String fmt, Object... objects) {
            return String.format(fmt, objects);
        }

        protected static GetAttributeNode createGetAttributeNode() {
            return GetAttributeNode.create(SpecialAttributeNames.__NAME__, null);
        }
    }

    @Builtin(name = __REDUCE__, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class ReduceNode extends PythonUnaryBuiltinNode {
        @Specialization
        String doBuiltinMethod(VirtualFrame frame, PBuiltinMethod self,
                        @Cached("createGetAttributeNode()") GetAttributeNode getNameNode,
                        @Cached("create()") CastToStringNode castToStringNode) {
            String name = castToStringNode.execute(frame, getNameNode.executeObject(frame, self.getFunction()));
            return doMethod(name, self.getSelf());
        }

        @Specialization
        String doBuiltinMethod(VirtualFrame frame, PMethod self,
                        @Cached("createGetAttributeNode()") GetAttributeNode getNameNode,
                        @Cached("create()") CastToStringNode castToStringNode) {
            return doMethod(castToStringNode.execute(frame, getNameNode.executeObject(frame, self.getFunction())), self.getSelf());
        }

        private String doMethod(String name, Object owner) {
            if (owner == null || owner == PNone.NONE || owner instanceof PythonModule) {
                return name;
            }
            throw raiseCannotPickle();
        }

        @Fallback
        Object doGeneric(@SuppressWarnings("unused") Object obj) {
            throw raiseCannotPickle();
        }

        protected static GetAttributeNode createGetAttributeNode() {
            return GetAttributeNode.create(SpecialAttributeNames.__NAME__, null);
        }

        private PException raiseCannotPickle() {
            throw raise(TypeError, "can't pickle function objects");
        }
    }

    @Builtin(name = "__text_signature__", minNumOfPositionalArgs = 1, isGetter = true)
    @GenerateNodeFactory
    public abstract static class TextSignatureNode extends PythonUnaryBuiltinNode {
        @Child AbstractFunctionBuiltins.TextSignatureNode subNode = AbstractFunctionBuiltins.TextSignatureNode.create();

        @Specialization
        Object getTextSignature(VirtualFrame frame, PBuiltinMethod self) {
            return subNode.execute(frame, self.getFunction());
        }

        @Specialization
        Object getTextSignature(VirtualFrame frame, PMethod self) {
            return subNode.execute(frame, self.getFunction());
        }
    }
}
