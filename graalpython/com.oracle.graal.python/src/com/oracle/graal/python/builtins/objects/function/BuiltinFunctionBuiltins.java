/*
 * Copyright (c) 2017, 2024, Oracle and/or its affiliates.
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

package com.oracle.graal.python.builtins.objects.function;

import static com.oracle.graal.python.nodes.BuiltinNames.T_GETATTR;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.J___NAME__;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.J___QUALNAME__;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.J___SIGNATURE__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___OBJCLASS__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___REDUCE__;
import static com.oracle.graal.python.nodes.function.BuiltinFunctionRootNode.T_DOLLAR_DECL_TYPE;
import static com.oracle.graal.python.util.PythonUtils.TS_ENCODING;
import static com.oracle.graal.python.util.PythonUtils.tsLiteral;

import java.util.ArrayList;
import java.util.List;

import com.oracle.graal.python.builtins.Builtin;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.module.PythonModule;
import com.oracle.graal.python.builtins.objects.tuple.PTuple;
import com.oracle.graal.python.lib.PyObjectGetAttr;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.StringLiterals;
import com.oracle.graal.python.nodes.bytecode.ImportNode;
import com.oracle.graal.python.nodes.call.CallNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.builtins.PythonBinaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonUnaryBuiltinNode;
import com.oracle.graal.python.nodes.truffle.PythonArithmeticTypes;
import com.oracle.graal.python.runtime.exception.PythonErrorType;
import com.oracle.graal.python.runtime.object.PythonObjectFactory;
import com.oracle.graal.python.util.PythonUtils;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.dsl.TypeSystemReference;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.strings.TruffleString;

@CoreFunctions(extendClasses = {PythonBuiltinClassType.PBuiltinFunction, PythonBuiltinClassType.WrapperDescriptor})
public final class BuiltinFunctionBuiltins extends PythonBuiltins {

    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return BuiltinFunctionBuiltinsFactory.getFactories();
    }

    @Builtin(name = J___NAME__, minNumOfPositionalArgs = 1, maxNumOfPositionalArgs = 2, isGetter = true, isSetter = true)
    @GenerateNodeFactory
    abstract static class NameNode extends PythonBinaryBuiltinNode {
        @Specialization(guards = "isNoValue(noValue)")
        static TruffleString getName(PBuiltinFunction self, @SuppressWarnings("unused") PNone noValue) {
            return self.getName();
        }

        @Specialization(guards = "!isNoValue(value)")
        static TruffleString setName(@SuppressWarnings("unused") PBuiltinFunction self, @SuppressWarnings("unused") Object value,
                        @Cached PRaiseNode raiseNode) {
            throw raiseNode.raise(PythonErrorType.AttributeError, ErrorMessages.ATTR_S_OF_S_IS_NOT_WRITABLE, "__name__", "builtin function");
        }
    }

    @Builtin(name = J___QUALNAME__, minNumOfPositionalArgs = 1, maxNumOfPositionalArgs = 2, isGetter = true, isSetter = true)
    @GenerateNodeFactory
    abstract static class QualnameNode extends PythonBinaryBuiltinNode {
        @Specialization(guards = "isNoValue(noValue)")
        static TruffleString getQualname(PBuiltinFunction self, @SuppressWarnings("unused") PNone noValue) {
            return self.getQualname();
        }

        @Specialization(guards = "!isNoValue(value)")
        static TruffleString setQualname(@SuppressWarnings("unused") PBuiltinFunction self, @SuppressWarnings("unused") Object value,
                        @Cached PRaiseNode raiseNode) {
            throw raiseNode.raise(PythonErrorType.AttributeError, ErrorMessages.ATTR_S_OF_S_IS_NOT_WRITABLE, "__qualname__", "builtin function");
        }
    }

    @Builtin(name = J___OBJCLASS__, minNumOfPositionalArgs = 1, isGetter = true)
    @TypeSystemReference(PythonArithmeticTypes.class)
    @GenerateNodeFactory
    public abstract static class ObjclassNode extends PythonUnaryBuiltinNode {
        @Specialization(guards = "self.getEnclosingType() == null")
        static Object objclassMissing(@SuppressWarnings("unused") PBuiltinFunction self,
                        @Cached PRaiseNode raiseNode) {
            throw raiseNode.raise(PythonErrorType.AttributeError, ErrorMessages.OBJ_S_HAS_NO_ATTR_S, "builtin_function_or_method", "__objclass__");
        }

        @Specialization(guards = "self.getEnclosingType() != null")
        static Object objclass(PBuiltinFunction self) {
            return self.getEnclosingType();
        }
    }

    @Builtin(name = J___REDUCE__, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class ReduceNode extends PythonUnaryBuiltinNode {
        @Specialization
        Object doBuiltinFunc(VirtualFrame frame, PBuiltinFunction func,
                        @Bind("this") Node inliningTarget,
                        @Cached PyObjectGetAttr getAttr,
                        @Cached PythonObjectFactory factory) {
            PythonModule builtins = getContext().getBuiltins();
            Object getattr = getAttr.execute(frame, inliningTarget, builtins, T_GETATTR);
            PTuple args = factory.createTuple(new Object[]{func.getEnclosingType(), func.getName()});
            return factory.createTuple(new Object[]{getattr, args});
        }
    }

    @Builtin(name = J___SIGNATURE__, minNumOfPositionalArgs = 1, isGetter = true)
    @GenerateNodeFactory
    public abstract static class SignatureNode extends PythonUnaryBuiltinNode {

        @Specialization
        public Object doIt(PBuiltinFunction fun) {
            return createInspectSignagure(fun.getSignature(), false);
        }

        private enum ParameterKinds {
            POSITIONAL_ONLY,
            POSITIONAL_OR_KEYWORD,
            VAR_POSITIONAL,
            KEYWORD_ONLY,
            VAR_KEYWORD;

            static final ParameterKinds[] VALUES = values();

            Object get(Object[] kinds, Object inspectParameter) {
                if (kinds[ordinal()] == null) {
                    kinds[ordinal()] = PyObjectGetAttr.executeUncached(inspectParameter, PythonUtils.toTruffleStringUncached(name()));
                }
                return kinds[ordinal()];
            }
        }

        @TruffleBoundary
        public static Object createInspectSignagure(Signature signature, boolean skipSelf) {
            PythonModule inspect = ImportNode.importModule(tsLiteral("inspect"));
            Object inspectSignature = PyObjectGetAttr.executeUncached(inspect, tsLiteral("Signature"));
            Object inspectParameter = PyObjectGetAttr.executeUncached(inspect, tsLiteral("Parameter"));
            Object[] parameterKinds = new Object[ParameterKinds.VALUES.length];

            TruffleString[] keywordNames = signature.getKeywordNames();
            boolean takesVarArgs = signature.takesVarArgs();
            boolean takesVarKeywordArgs = signature.takesVarKeywordArgs();
            TruffleString[] parameterNames = signature.getParameterIds();

            Object kind = ParameterKinds.POSITIONAL_ONLY.get(parameterKinds, inspectParameter);
            ArrayList<Object> parameters = new ArrayList<>();
            CallNode callNode = CallNode.getUncached();
            for (int i = 0; i < parameterNames.length; i++) {
                if (i == 0 && T_DOLLAR_DECL_TYPE.equalsUncached(parameterNames[i], TS_ENCODING)) {
                    continue;
                }
                if (skipSelf) {
                    skipSelf = false;
                    continue;
                }
                if (signature.getPositionalOnlyArgIndex() == i) {
                    kind = ParameterKinds.POSITIONAL_OR_KEYWORD.get(parameterKinds, inspectParameter);
                }
                TruffleString name = parameterNames[i];
                if (name.codePointAtIndexUncached(0, TS_ENCODING) == '$') {
                    name = name.substringUncached(1, name.codePointLengthUncached(TS_ENCODING) - 1, TS_ENCODING, true);
                }
                parameters.add(callNode.executeWithoutFrame(inspectParameter, name, kind));
            }
            if (takesVarArgs) {
                parameters.add(callNode.executeWithoutFrame(inspectParameter, StringLiterals.T_ARGS, ParameterKinds.VAR_POSITIONAL.get(parameterKinds, inspectParameter)));
            }
            for (TruffleString keywordName : keywordNames) {
                parameters.add(callNode.executeWithoutFrame(inspectParameter, keywordName, ParameterKinds.KEYWORD_ONLY.get(parameterKinds, inspectParameter)));
            }
            if (takesVarKeywordArgs) {
                parameters.add(callNode.executeWithoutFrame(inspectParameter, StringLiterals.T_KWARGS, ParameterKinds.VAR_KEYWORD.get(parameterKinds, inspectParameter)));
            }

            return callNode.executeWithoutFrame(inspectSignature, PythonObjectFactory.getUncached().createTuple(parameters.toArray()));
        }
    }
}
