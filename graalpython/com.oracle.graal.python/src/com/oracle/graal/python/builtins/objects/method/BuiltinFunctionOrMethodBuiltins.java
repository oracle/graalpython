/*
 * Copyright (c) 2022, 2024, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.builtins.objects.method;

import static com.oracle.graal.python.nodes.SpecialAttributeNames.J___SIGNATURE__;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.T___NAME__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___REPR__;

import java.util.List;

import com.oracle.graal.python.builtins.Builtin;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.PythonAbstractObject;
import com.oracle.graal.python.builtins.objects.function.BuiltinFunctionBuiltins;
import com.oracle.graal.python.builtins.objects.function.PFunction;
import com.oracle.graal.python.builtins.objects.module.PythonModule;
import com.oracle.graal.python.builtins.objects.str.StringUtils.SimpleTruffleStringFormatNode;
import com.oracle.graal.python.builtins.objects.type.TypeNodes.GetNameNode;
import com.oracle.graal.python.nodes.attributes.GetAttributeNode;
import com.oracle.graal.python.nodes.builtins.FunctionNodes.GetSignatureNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.builtins.PythonUnaryBuiltinNode;
import com.oracle.graal.python.nodes.object.GetClassNode;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.NeverDefault;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.strings.TruffleString;

@CoreFunctions(extendClasses = PythonBuiltinClassType.PBuiltinFunctionOrMethod)
public final class BuiltinFunctionOrMethodBuiltins extends PythonBuiltins {

    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return BuiltinFunctionOrMethodBuiltinsFactory.getFactories();
    }

    @Builtin(name = J___REPR__, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class ReprNode extends PythonUnaryBuiltinNode {
        static boolean isBuiltinFunction(PBuiltinMethod self) {
            return self.getSelf() == PNone.NO_VALUE || self.getSelf() instanceof PythonModule;
        }

        static boolean isBuiltinFunction(PMethod self) {
            return self.getSelf() == PNone.NO_VALUE || (self.getSelf() instanceof PythonModule && self.getFunction() instanceof PFunction && ((PFunction) self.getFunction()).isBuiltin());
        }

        @Specialization(guards = "isBuiltinFunction(self)")
        static TruffleString reprBuiltinFunction(VirtualFrame frame, PMethod self,
                        @Shared @Cached("createGetAttributeNode()") GetAttributeNode getNameNode,
                        @Shared("formatter") @Cached SimpleTruffleStringFormatNode simpleTruffleStringFormatNode) {
            // (tfel): this only happens for builtin modules ... I think
            return simpleTruffleStringFormatNode.format("<built-in function %s>", getNameNode.executeObject(frame, self.getFunction()));
        }

        @Specialization(guards = "isBuiltinFunction(self)")
        static TruffleString reprBuiltinFunction(VirtualFrame frame, PBuiltinMethod self,
                        @Shared @Cached("createGetAttributeNode()") GetAttributeNode getNameNode,
                        @Shared("formatter") @Cached SimpleTruffleStringFormatNode simpleTruffleStringFormatNode) {
            return simpleTruffleStringFormatNode.format("<built-in function %s>", getNameNode.executeObject(frame, self.getFunction()));
        }

        @Specialization(guards = "!isBuiltinFunction(self)")
        static TruffleString reprBuiltinMethod(VirtualFrame frame, PBuiltinMethod self,
                        @Bind("this") Node inliningTarget,
                        @Shared @Cached GetClassNode getClassNode,
                        @Shared @Cached("createGetAttributeNode()") GetAttributeNode getNameNode,
                        @Shared @Cached GetNameNode getTypeNameNode,
                        @Shared("formatter") @Cached SimpleTruffleStringFormatNode simpleTruffleStringFormatNode) {
            TruffleString typeName = getTypeNameNode.execute(inliningTarget, getClassNode.execute(inliningTarget, self.getSelf()));
            return simpleTruffleStringFormatNode.format("<built-in method %s of %s object at 0x%s>", getNameNode.executeObject(frame, self.getFunction()), typeName,
                            PythonAbstractObject.systemHashCodeAsHexString(self.getSelf()));
        }

        @Specialization(guards = "!isBuiltinFunction(self)")
        static TruffleString reprBuiltinMethod(VirtualFrame frame, PMethod self,
                        @Bind("this") Node inliningTarget,
                        @Shared @Cached GetClassNode getClassNode,
                        @Shared @Cached("createGetAttributeNode()") GetAttributeNode getNameNode,
                        @Shared @Cached GetNameNode getTypeNameNode,
                        @Shared("formatter") @Cached SimpleTruffleStringFormatNode simpleTruffleStringFormatNode) {
            TruffleString typeName = getTypeNameNode.execute(inliningTarget, getClassNode.execute(inliningTarget, self.getSelf()));
            return simpleTruffleStringFormatNode.format("<built-in method %s of %s object at 0x%s>", getNameNode.executeObject(frame, self.getFunction()), typeName,
                            PythonAbstractObject.systemHashCodeAsHexString(self.getSelf()));
        }

        @NeverDefault
        protected static GetAttributeNode createGetAttributeNode() {
            return GetAttributeNode.create(T___NAME__);
        }
    }

    @Builtin(name = J___SIGNATURE__, minNumOfPositionalArgs = 1, isGetter = true)
    @GenerateNodeFactory
    abstract static class SignatureNode extends PythonUnaryBuiltinNode {
        @Specialization
        @TruffleBoundary
        public Object doIt(Object fun) {
            return BuiltinFunctionBuiltins.SignatureNode.createInspectSignagure(GetSignatureNode.executeUncached(fun), true);
        }
    }
}
