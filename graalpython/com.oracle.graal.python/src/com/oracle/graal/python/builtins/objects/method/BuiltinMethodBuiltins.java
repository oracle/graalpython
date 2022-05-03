/*
 * Copyright (c) 2017, 2022, Oracle and/or its affiliates.
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

import static com.oracle.graal.python.nodes.SpecialAttributeNames.__NAME__;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.__QUALNAME__;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.__TEXT_SIGNATURE__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__OBJCLASS__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__REPR__;

import java.util.List;

import com.oracle.graal.python.builtins.Builtin;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.objects.PythonAbstractObject;
import com.oracle.graal.python.builtins.objects.function.AbstractFunctionBuiltins;
import com.oracle.graal.python.builtins.objects.function.PFunction;
import com.oracle.graal.python.builtins.objects.module.PythonModule;
import com.oracle.graal.python.builtins.objects.type.TypeNodes;
import com.oracle.graal.python.builtins.objects.type.TypeNodes.GetNameNode;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.SpecialAttributeNames;
import com.oracle.graal.python.nodes.attributes.GetAttributeNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.builtins.PythonBinaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonUnaryBuiltinNode;
import com.oracle.graal.python.nodes.object.GetClassNode;
import com.oracle.graal.python.nodes.truffle.PythonArithmeticTypes;
import com.oracle.graal.python.nodes.util.CannotCastException;
import com.oracle.graal.python.nodes.util.CastToJavaStringNode;
import com.oracle.graal.python.runtime.exception.PythonErrorType;
import com.oracle.graal.python.util.PythonUtils;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.dsl.TypeSystemReference;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.profiles.ConditionProfile;

@CoreFunctions(extendClasses = {PythonBuiltinClassType.PBuiltinMethod})
public class BuiltinMethodBuiltins extends PythonBuiltins {

    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return BuiltinMethodBuiltinsFactory.getFactories();
    }

    @Builtin(name = __REPR__, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class ReprNode extends PythonUnaryBuiltinNode {
        static boolean isBuiltinFunction(PBuiltinMethod self) {
            return self.getSelf() instanceof PythonModule;
        }

        static boolean isBuiltinFunction(PMethod self) {
            return self.getSelf() instanceof PythonModule && self.getFunction() instanceof PFunction && ((PFunction) self.getFunction()).isBuiltin();
        }

        @Specialization(guards = "isBuiltinFunction(self)")
        static Object reprBuiltinFunction(VirtualFrame frame, PMethod self,
                        @Cached("createGetAttributeNode()") GetAttributeNode getNameNode) {
            // (tfel): this only happens for builtin modules ... I think
            return PythonUtils.format("<built-in function %s>", getNameNode.executeObject(frame, self.getFunction()));
        }

        @Specialization(guards = "isBuiltinFunction(self)")
        static String reprBuiltinFunction(VirtualFrame frame, PBuiltinMethod self,
                        @Cached("createGetAttributeNode()") GetAttributeNode getNameNode) {
            return PythonUtils.format("<built-in function %s>", getNameNode.executeObject(frame, self.getFunction()));
        }

        @Specialization(guards = "!isBuiltinFunction(self)")
        static Object reprBuiltinMethod(VirtualFrame frame, PBuiltinMethod self,
                        @Cached GetClassNode getClassNode,
                        @Cached("createGetAttributeNode()") GetAttributeNode getNameNode,
                        @Cached GetNameNode getTypeNameNode) {
            String typeName = getTypeNameNode.execute(getClassNode.execute(self.getSelf()));
            return PythonUtils.format("<built-in method %s of %s object at 0x%x>", getNameNode.executeObject(frame, self.getFunction()), typeName, PythonAbstractObject.systemHashCode(self.getSelf()));
        }

        @Specialization(guards = "!isBuiltinFunction(self)")
        static Object reprBuiltinMethod(VirtualFrame frame, PMethod self,
                        @Cached GetClassNode getClassNode,
                        @Cached("createGetAttributeNode()") GetAttributeNode getNameNode,
                        @Cached GetNameNode getTypeNameNode) {
            String typeName = getTypeNameNode.execute(getClassNode.execute(self.getSelf()));
            return PythonUtils.format("<built-in method %s of %s object at 0x%x>", getNameNode.executeObject(frame, self.getFunction()), typeName, PythonAbstractObject.systemHashCode(self.getSelf()));
        }

        protected static GetAttributeNode createGetAttributeNode() {
            return GetAttributeNode.create(SpecialAttributeNames.__NAME__, null);
        }
    }

    @Builtin(name = __TEXT_SIGNATURE__, minNumOfPositionalArgs = 1, maxNumOfPositionalArgs = 2, isGetter = true, isSetter = true)
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

    @Builtin(name = __OBJCLASS__, minNumOfPositionalArgs = 1, isGetter = true)
    @TypeSystemReference(PythonArithmeticTypes.class)
    @GenerateNodeFactory
    public abstract static class ObjclassNode extends PythonUnaryBuiltinNode {
        @Specialization(guards = "self.getFunction().getEnclosingType() == null")
        Object objclassMissing(@SuppressWarnings("unused") PBuiltinMethod self) {
            throw raise(PythonErrorType.AttributeError, ErrorMessages.OBJ_S_HAS_NO_ATTR_S, "builtin_function_or_method", "__objclass__");
        }

        @Specialization(guards = "self.getFunction().getEnclosingType() != null")
        @TruffleBoundary
        Object objclass(PBuiltinMethod self,
                        @Cached ConditionProfile profile) {
            return getPythonClass(self.getFunction().getEnclosingType(), profile);
        }
    }

    @Builtin(name = __NAME__, minNumOfPositionalArgs = 1, isGetter = true)
    @GenerateNodeFactory
    public abstract static class MethodName extends PythonUnaryBuiltinNode {
        @Specialization
        static Object getName(VirtualFrame frame, PBuiltinMethod method,
                        @Cached("create(__NAME__)") GetAttributeNode getNameAttrNode) {
            return getNameAttrNode.executeObject(frame, method.getFunction());
        }

        @Specialization
        static Object getName(VirtualFrame frame, PMethod method,
                        @Cached("create(__NAME__)") GetAttributeNode getNameAttrNode) {
            return getNameAttrNode.executeObject(frame, method.getFunction());
        }
    }

    @Builtin(name = __QUALNAME__, minNumOfPositionalArgs = 1, isGetter = true)
    @GenerateNodeFactory
    public abstract static class MethodQualName extends PythonUnaryBuiltinNode {
        @Specialization
        Object getQualName(VirtualFrame frame, PMethod method,
                        @Cached("create(__NAME__)") GetAttributeNode getNameAttrNode,
                        @Cached("create(__QUALNAME__)") GetAttributeNode getQualNameAttrNode,
                        @Cached TypeNodes.IsTypeNode isTypeNode,
                        @Cached CastToJavaStringNode castToJavaStringNode,
                        @Cached ConditionProfile isGlobalProfile,
                        @Cached GetClassNode getClassNode) {
            return makeQualname(frame, method, method.getSelf(), getQualNameAttrNode, getNameAttrNode, castToJavaStringNode, getClassNode, isTypeNode, isGlobalProfile);
        }

        @Specialization
        Object getQualName(VirtualFrame frame, PBuiltinMethod method,
                        @Cached("create(__NAME__)") GetAttributeNode getNameAttrNode,
                        @Cached("create(__QUALNAME__)") GetAttributeNode getQualNameAttrNode,
                        @Cached TypeNodes.IsTypeNode isTypeNode,
                        @Cached CastToJavaStringNode castToJavaStringNode,
                        @Cached ConditionProfile isGlobalProfile,
                        @Cached GetClassNode getClassNode) {
            return makeQualname(frame, method, method.getSelf(), getQualNameAttrNode, getNameAttrNode, castToJavaStringNode, getClassNode, isTypeNode, isGlobalProfile);
        }

        private Object makeQualname(VirtualFrame frame, Object method, Object self, GetAttributeNode getQualNameAttrNode, GetAttributeNode getNameAttrNode, CastToJavaStringNode castToJavaStringNode,
                        GetClassNode getClassNode, TypeNodes.IsTypeNode isTypeNode, ConditionProfile isGlobalProfile) {
            String methodName;
            try {
                methodName = castToJavaStringNode.execute(getNameAttrNode.executeObject(frame, method));
            } catch (CannotCastException e) {
                throw raise(PythonBuiltinClassType.TypeError, ErrorMessages.IS_NOT_A_UNICODE_OBJECT, __NAME__);
            }
            if (isGlobalProfile.profile(self == null || self instanceof PythonModule)) {
                return methodName;
            }

            Object type = isTypeNode.execute(self) ? self : getClassNode.execute(self);
            String typeQualName;
            try {
                typeQualName = castToJavaStringNode.execute(getQualNameAttrNode.executeObject(frame, type));
            } catch (CannotCastException e) {
                throw raise(PythonBuiltinClassType.TypeError, ErrorMessages.IS_NOT_A_UNICODE_OBJECT, __QUALNAME__);
            }

            return PythonUtils.format("%s.%s", typeQualName, methodName);
        }
    }
}
