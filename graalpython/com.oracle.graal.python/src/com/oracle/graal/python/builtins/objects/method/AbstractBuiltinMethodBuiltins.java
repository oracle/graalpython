/*
 * Copyright (c) 2017, 2023, Oracle and/or its affiliates.
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

import static com.oracle.graal.python.nodes.SpecialAttributeNames.J___NAME__;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.J___QUALNAME__;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.J___TEXT_SIGNATURE__;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.T___NAME__;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.T___QUALNAME__;

import java.util.List;

import com.oracle.graal.python.builtins.Builtin;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.function.AbstractFunctionBuiltins;
import com.oracle.graal.python.builtins.objects.module.PythonModule;
import com.oracle.graal.python.builtins.objects.str.StringUtils.SimpleTruffleStringFormatNode;
import com.oracle.graal.python.builtins.objects.type.TypeNodes;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.attributes.GetAttributeNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.builtins.PythonBinaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonUnaryBuiltinNode;
import com.oracle.graal.python.nodes.object.GetClassNode;
import com.oracle.graal.python.nodes.util.CannotCastException;
import com.oracle.graal.python.nodes.util.CastToTruffleStringNode;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.profiles.InlinedConditionProfile;
import com.oracle.truffle.api.strings.TruffleString;

@CoreFunctions(extendClasses = {PythonBuiltinClassType.PBuiltinFunctionOrMethod, PythonBuiltinClassType.MethodWrapper})
public final class AbstractBuiltinMethodBuiltins extends PythonBuiltins {

    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return AbstractBuiltinMethodBuiltinsFactory.getFactories();
    }

    @Builtin(name = J___TEXT_SIGNATURE__, minNumOfPositionalArgs = 1, maxNumOfPositionalArgs = 2, isGetter = true, isSetter = true)
    @GenerateNodeFactory
    public abstract static class TextSignatureNode extends PythonBinaryBuiltinNode {
        @Child AbstractFunctionBuiltins.TextSignatureNode subNode = AbstractFunctionBuiltins.TextSignatureNode.create();

        @Specialization
        Object getTextSignature(VirtualFrame frame, PBuiltinMethod self, Object value) {
            return subNode.execute(frame, self.getFunction(), value);
        }

        @Specialization
        Object getTextSignature(VirtualFrame frame, PMethod self, Object value) {
            return subNode.execute(frame, self.getFunction(), value);
        }
    }

    @Builtin(name = J___NAME__, minNumOfPositionalArgs = 1, isGetter = true)
    @GenerateNodeFactory
    public abstract static class MethodName extends PythonUnaryBuiltinNode {
        @Specialization
        static Object getName(VirtualFrame frame, PBuiltinMethod method,
                        @Shared @Cached("create(T___NAME__)") GetAttributeNode getNameAttrNode) {
            return getNameAttrNode.executeObject(frame, method.getFunction());
        }

        @Specialization
        static Object getName(VirtualFrame frame, PMethod method,
                        @Shared @Cached("create(T___NAME__)") GetAttributeNode getNameAttrNode) {
            return getNameAttrNode.executeObject(frame, method.getFunction());
        }
    }

    @Builtin(name = J___QUALNAME__, minNumOfPositionalArgs = 1, isGetter = true)
    @GenerateNodeFactory
    public abstract static class MethodQualName extends PythonUnaryBuiltinNode {
        @Specialization
        static TruffleString getQualName(VirtualFrame frame, PMethod method,
                        @Bind("this") Node inliningTarget,
                        @Shared @Cached("create(T___NAME__)") GetAttributeNode getNameAttrNode,
                        @Shared @Cached("create(T___QUALNAME__)") GetAttributeNode getQualNameAttrNode,
                        @Shared @Cached TypeNodes.IsTypeNode isTypeNode,
                        @Shared @Cached CastToTruffleStringNode castToStringNode,
                        @Shared @Cached InlinedConditionProfile isGlobalProfile,
                        @Shared @Cached GetClassNode getClassNode,
                        @Shared @Cached SimpleTruffleStringFormatNode simpleTruffleStringFormatNode,
                        @Shared @Cached PRaiseNode.Lazy raiseNode) {
            return makeQualname(frame, inliningTarget, method, method.getSelf(), getQualNameAttrNode, getNameAttrNode, castToStringNode, getClassNode, isTypeNode, isGlobalProfile,
                            simpleTruffleStringFormatNode, raiseNode);
        }

        @Specialization
        static TruffleString getQualName(VirtualFrame frame, PBuiltinMethod method,
                        @Bind("this") Node inliningTarget,
                        @Shared @Cached("create(T___NAME__)") GetAttributeNode getNameAttrNode,
                        @Shared @Cached("create(T___QUALNAME__)") GetAttributeNode getQualNameAttrNode,
                        @Shared @Cached TypeNodes.IsTypeNode isTypeNode,
                        @Shared @Cached CastToTruffleStringNode castToStringNode,
                        @Shared @Cached InlinedConditionProfile isGlobalProfile,
                        @Shared @Cached GetClassNode getClassNode,
                        @Shared @Cached SimpleTruffleStringFormatNode simpleTruffleStringFormatNode,
                        @Shared @Cached PRaiseNode.Lazy raiseNode) {
            return makeQualname(frame, inliningTarget, method, method.getSelf(), getQualNameAttrNode, getNameAttrNode, castToStringNode, getClassNode, isTypeNode, isGlobalProfile,
                            simpleTruffleStringFormatNode, raiseNode);
        }

        private static TruffleString makeQualname(VirtualFrame frame, Node inliningTarget, Object method, Object self, GetAttributeNode getQualNameAttrNode, GetAttributeNode getNameAttrNode,
                        CastToTruffleStringNode castToStringNode, GetClassNode getClassNode, TypeNodes.IsTypeNode isTypeNode, InlinedConditionProfile isGlobalProfile,
                        SimpleTruffleStringFormatNode simpleTruffleStringFormatNode, PRaiseNode.Lazy raiseNode) {
            TruffleString methodName;
            try {
                methodName = castToStringNode.execute(inliningTarget, getNameAttrNode.executeObject(frame, method));
            } catch (CannotCastException e) {
                throw raiseNode.get(inliningTarget).raise(PythonBuiltinClassType.TypeError, ErrorMessages.IS_NOT_A_UNICODE_OBJECT, T___NAME__);
            }
            if (isGlobalProfile.profile(inliningTarget, self == PNone.NO_VALUE || self instanceof PythonModule)) {
                return methodName;
            }

            Object type = isTypeNode.execute(inliningTarget, self) ? self : getClassNode.execute(inliningTarget, self);
            TruffleString typeQualName;
            try {
                typeQualName = castToStringNode.execute(inliningTarget, getQualNameAttrNode.executeObject(frame, type));
            } catch (CannotCastException e) {
                throw raiseNode.get(inliningTarget).raise(PythonBuiltinClassType.TypeError, ErrorMessages.IS_NOT_A_UNICODE_OBJECT, T___QUALNAME__);
            }

            return simpleTruffleStringFormatNode.format("%s.%s", typeQualName, methodName);
        }
    }
}
