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

package com.oracle.graal.python.builtins.objects.function;

import static com.oracle.graal.python.nodes.SpecialAttributeNames.J___ANNOTATIONS__;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.J___CLOSURE__;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.J___DICT__;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.J___GLOBALS__;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.J___MODULE__;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.J___TEXT_SIGNATURE__;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.T___ANNOTATIONS__;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.T___MODULE__;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.T___NAME__;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.T___TEXT_SIGNATURE__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___CALL__;
import static com.oracle.graal.python.nodes.StringLiterals.T_COMMA_SPACE;
import static com.oracle.graal.python.nodes.StringLiterals.T_EQ;
import static com.oracle.graal.python.nodes.StringLiterals.T_LPAREN;
import static com.oracle.graal.python.nodes.StringLiterals.T_RPAREN;
import static com.oracle.graal.python.nodes.function.BuiltinFunctionRootNode.T_DOLLAR_DECL_TYPE;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.AttributeError;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.TypeError;
import static com.oracle.graal.python.util.PythonUtils.TS_ENCODING;
import static com.oracle.graal.python.util.PythonUtils.tsLiteral;

import java.util.List;

import com.oracle.graal.python.builtins.Builtin;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.cell.PCell;
import com.oracle.graal.python.builtins.objects.dict.PDict;
import com.oracle.graal.python.builtins.objects.module.PythonModule;
import com.oracle.graal.python.builtins.objects.object.PythonObject;
import com.oracle.graal.python.lib.PyObjectGetItem;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.argument.CreateArgumentsNode;
import com.oracle.graal.python.nodes.attributes.ReadAttributeFromObjectNode;
import com.oracle.graal.python.nodes.attributes.WriteAttributeToObjectNode;
import com.oracle.graal.python.nodes.call.CallDispatchNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonBinaryBuiltinNode;
import com.oracle.graal.python.nodes.object.GetOrCreateDictNode;
import com.oracle.graal.python.nodes.object.SetDictNode;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.profiles.InlinedConditionProfile;
import com.oracle.truffle.api.strings.TruffleString;
import com.oracle.truffle.api.strings.TruffleStringBuilder;

@CoreFunctions(extendClasses = {PythonBuiltinClassType.PFunction, PythonBuiltinClassType.PBuiltinFunction, PythonBuiltinClassType.WrapperDescriptor})
public final class AbstractFunctionBuiltins extends PythonBuiltins {

    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return AbstractFunctionBuiltinsFactory.getFactories();
    }

    @Builtin(name = J___CALL__, minNumOfPositionalArgs = 1, takesVarArgs = true, takesVarKeywordArgs = true)
    @GenerateNodeFactory
    public abstract static class CallNode extends PythonBuiltinNode {
        @Specialization
        protected Object doIt(VirtualFrame frame, PFunction self, Object[] arguments, PKeyword[] keywords,
                        @Shared @Cached CreateArgumentsNode createArgs,
                        @Shared @Cached CallDispatchNode dispatch) {
            return dispatch.executeCall(frame, self, createArgs.execute(self, arguments, keywords));
        }

        @Specialization
        protected Object doIt(VirtualFrame frame, PBuiltinFunction self, Object[] arguments, PKeyword[] keywords,
                        @Shared @Cached CreateArgumentsNode createArgs,
                        @Shared @Cached CallDispatchNode dispatch) {
            return dispatch.executeCall(frame, self, createArgs.execute(self, arguments, keywords));
        }
    }

    @Builtin(name = J___CLOSURE__, minNumOfPositionalArgs = 1, isGetter = true)
    @GenerateNodeFactory
    public abstract static class GetClosureNode extends PythonBuiltinNode {
        @Specialization(guards = "!isBuiltinFunction(self)")
        Object getClosure(PFunction self) {
            PCell[] closure = self.getClosure();
            if (closure == null) {
                return PNone.NONE;
            }
            return factory().createTuple(closure);
        }

        @SuppressWarnings("unused")
        @Fallback
        Object getClosure(Object self) {
            throw raise(AttributeError, ErrorMessages.OBJ_S_HAS_NO_ATTR_S, "builtin_function_or_method", "__closure__");
        }
    }

    @Builtin(name = J___GLOBALS__, minNumOfPositionalArgs = 1, isGetter = true)
    @GenerateNodeFactory
    public abstract static class GetGlobalsNode extends PythonBuiltinNode {
        @Specialization(guards = "!isBuiltinFunction(self)")
        Object getGlobals(PFunction self,
                        @Bind("this") Node inliningTarget,
                        @Cached GetOrCreateDictNode getDict,
                        @Cached InlinedConditionProfile moduleGlobals) {
            // see the make_globals_function from lib-graalpython/functions.py
            PythonObject globals = self.getGlobals();
            if (moduleGlobals.profile(inliningTarget, globals instanceof PythonModule)) {
                return getDict.execute(globals);
            } else {
                return globals;
            }
        }

        @SuppressWarnings("unused")
        @Fallback
        Object getGlobals(Object self) {
            throw raise(AttributeError, ErrorMessages.OBJ_S_HAS_NO_ATTR_S, "builtin_function_or_method", "__globals__");
        }
    }

    @Builtin(name = J___MODULE__, minNumOfPositionalArgs = 1, maxNumOfPositionalArgs = 2, isGetter = true, isSetter = true, allowsDelete = true)
    @GenerateNodeFactory
    abstract static class GetModuleNode extends PythonBuiltinNode {
        @Specialization(guards = {"!isBuiltinFunction(self)", "isNoValue(none)"})
        static Object getModule(VirtualFrame frame, PFunction self, @SuppressWarnings("unused") PNone none,
                        @Cached ReadAttributeFromObjectNode readObject,
                        @Cached ReadAttributeFromObjectNode readGlobals,
                        @Cached PyObjectGetItem getItem,
                        @Cached.Shared("writeObject") @Cached WriteAttributeToObjectNode writeObject) {
            Object module = readObject.execute(self, T___MODULE__);
            if (module == PNone.NO_VALUE) {
                PythonObject globals = self.getGlobals();
                // __module__: If module name is in globals, use it. Otherwise, use None.
                if (globals instanceof PythonModule) {
                    module = readGlobals.execute(globals, T___NAME__);
                    if (module == PNone.NO_VALUE) {
                        module = PNone.NONE;
                    }
                } else {
                    try {
                        module = getItem.execute(frame, globals, T___NAME__);
                    } catch (PException pe) {
                        module = PNone.NONE;
                    }
                }
                writeObject.execute(self, T___MODULE__, module);
            }
            return module;
        }

        @Specialization(guards = {"!isBuiltinFunction(self)", "isDeleteMarker(value)"})
        static Object delModule(PFunction self, @SuppressWarnings("unused") Object value,
                        @Cached.Shared("writeObject") @Cached WriteAttributeToObjectNode writeObject) {
            writeObject.execute(self, T___MODULE__, PNone.NONE);
            return PNone.NONE;
        }

        @Specialization(guards = {"!isBuiltinFunction(self)", "!isNoValue(value)", "!isDeleteMarker(value)"})
        static Object setModule(PFunction self, Object value,
                        @Cached.Shared("writeObject") @Cached WriteAttributeToObjectNode writeObject) {
            writeObject.execute(self, T___MODULE__, value);
            return PNone.NONE;
        }

        @SuppressWarnings("unused")
        @Specialization
        Object getModule(PBuiltinFunction self, Object value) {
            throw raise(AttributeError, ErrorMessages.OBJ_S_HAS_NO_ATTR_S, "builtin_function_or_method", "__module__");
        }
    }

    @Builtin(name = J___ANNOTATIONS__, minNumOfPositionalArgs = 1, maxNumOfPositionalArgs = 2, isGetter = true, isSetter = true)
    @GenerateNodeFactory
    abstract static class GetAnnotationsNode extends PythonBuiltinNode {
        @Specialization(guards = {"!isBuiltinFunction(self)", "isNoValue(none)"})
        Object getModule(PFunction self, @SuppressWarnings("unused") PNone none,
                        @Cached ReadAttributeFromObjectNode readObject,
                        @Cached WriteAttributeToObjectNode writeObject) {
            Object annotations = readObject.execute(self, T___ANNOTATIONS__);
            if (annotations == PNone.NO_VALUE) {
                annotations = factory().createDict();
                writeObject.execute(self, T___ANNOTATIONS__, annotations);
            }
            return annotations;
        }

        @Specialization(guards = {"!isBuiltinFunction(self)", "!isNoValue(value)"})
        static Object getModule(PFunction self, Object value,
                        @Cached WriteAttributeToObjectNode writeObject) {
            writeObject.execute(self, T___ANNOTATIONS__, value);
            return PNone.NONE;
        }

        @SuppressWarnings("unused")
        @Specialization
        Object getModule(PBuiltinFunction self, Object value) {
            throw raise(AttributeError, ErrorMessages.OBJ_S_HAS_NO_ATTR_S, "builtin_function_or_method", "__annotations__");
        }
    }

    @Builtin(name = J___DICT__, minNumOfPositionalArgs = 1, maxNumOfPositionalArgs = 2, isGetter = true, isSetter = true)
    @GenerateNodeFactory
    abstract static class DictNode extends PythonBinaryBuiltinNode {
        @Specialization
        static PNone dict(PFunction self, PDict mapping,
                        @Cached SetDictNode setDict) {
            setDict.execute(self, mapping);
            return PNone.NONE;
        }

        @Specialization(guards = "isNoValue(mapping)")
        Object dict(PFunction self, @SuppressWarnings("unused") PNone mapping,
                        @Cached GetOrCreateDictNode getDict) {
            return getDict.execute(self);
        }

        @Specialization(guards = {"!isNoValue(mapping)", "!isDict(mapping)"})
        PNone dict(@SuppressWarnings("unused") PFunction self, Object mapping) {
            throw raise(TypeError, ErrorMessages.DICT_MUST_BE_SET_TO_DICT, mapping);
        }

        @Specialization
        @SuppressWarnings("unused")
        Object builtinCode(PBuiltinFunction self, Object mapping) {
            throw raise(AttributeError, ErrorMessages.OBJ_S_HAS_NO_ATTR_S, "builtin_function_or_method", "__dict__");
        }
    }

    @Builtin(name = J___TEXT_SIGNATURE__, minNumOfPositionalArgs = 1, maxNumOfPositionalArgs = 2, isGetter = true, isSetter = true)
    @GenerateNodeFactory
    public abstract static class TextSignatureNode extends PythonBinaryBuiltinNode {

        private static final TruffleString ARGS = tsLiteral("*args");
        private static final TruffleString KWARGS = tsLiteral("**kwargs");

        @Specialization(guards = {"!isBuiltinFunction(self)", "isNoValue(none)"})
        Object getFunction(PFunction self, @SuppressWarnings("unused") PNone none,
                        @Cached ReadAttributeFromObjectNode readNode) {
            Object signature = readNode.execute(self, T___TEXT_SIGNATURE__);
            if (signature == PNone.NO_VALUE) {
                throw raise(AttributeError, ErrorMessages.OBJ_S_HAS_NO_ATTR_S, "function", "__text_signature__");
            }
            return signature;
        }

        @Specialization(guards = {"!isBuiltinFunction(self)", "!isNoValue(value)"})
        static Object setFunction(PFunction self, Object value,
                        @Cached WriteAttributeToObjectNode writeNode) {
            writeNode.execute(self, T___TEXT_SIGNATURE__, value);
            return PNone.NONE;
        }

        @Specialization(guards = "isNoValue(none)")
        protected static TruffleString getBuiltin(PBuiltinFunction self, @SuppressWarnings("unused") PNone none) {
            Signature signature = self.getSignature();
            return signatureToText(signature, false);
        }

        @TruffleBoundary
        public static TruffleString signatureToText(Signature signature, boolean skipSelf) {
            TruffleString[] keywordNames = signature.getKeywordNames();
            boolean takesVarArgs = signature.takesVarArgs();
            boolean takesVarKeywordArgs = signature.takesVarKeywordArgs();

            TruffleString[] parameterNames = signature.getParameterIds();

            TruffleStringBuilder sb = TruffleStringBuilder.create(TS_ENCODING);
            sb.appendStringUncached(T_LPAREN);
            boolean first = true;
            for (int i = 0; i < parameterNames.length; i++) {
                if (i == 0 && T_DOLLAR_DECL_TYPE.equalsUncached(parameterNames[i], TS_ENCODING)) {
                    continue;
                }
                if (skipSelf) {
                    skipSelf = false;
                    continue;
                }
                if (!first && signature.getPositionalOnlyArgIndex() == i) {
                    first = appendCommaIfNeeded(sb, first);
                    sb.appendCodePointUncached('/', 1, true);
                }
                first = appendCommaIfNeeded(sb, first);
                sb.appendStringUncached(parameterNames[i]);
            }
            if (signature.getPositionalOnlyArgIndex() == parameterNames.length) {
                first = appendCommaIfNeeded(sb, first);
                sb.appendCodePointUncached('/', 1, true);
            }
            if (takesVarArgs) {
                first = appendCommaIfNeeded(sb, first);
                sb.appendStringUncached(ARGS);
            }
            if (keywordNames.length > 0) {
                if (!takesVarArgs) {
                    first = appendCommaIfNeeded(sb, first);
                    sb.appendCodePointUncached('*', 1, true);
                }
                for (TruffleString keywordName : keywordNames) {
                    first = appendCommaIfNeeded(sb, first);
                    sb.appendStringUncached(keywordName);
                    sb.appendStringUncached(T_EQ);
                    sb.appendCodePointUncached('?', 1, true);
                }
            }
            if (takesVarKeywordArgs) {
                appendCommaIfNeeded(sb, first);
                sb.appendStringUncached(KWARGS);
            }
            sb.appendStringUncached(T_RPAREN);
            return sb.toStringUncached();
        }

        private static boolean appendCommaIfNeeded(TruffleStringBuilder sb, boolean first) {
            if (!first) {
                sb.appendStringUncached(T_COMMA_SPACE);
            }
            return false;
        }

        @Specialization(guards = "!isNoValue(value)")
        protected Object setBuiltin(@SuppressWarnings("unused") PBuiltinFunction self,
                        @SuppressWarnings("unused") Object value) {
            throw raise(AttributeError, ErrorMessages.ATTR_S_OF_S_IS_NOT_WRITABLE, "__text_signature__", "builtin_function_or_method");
        }

        public static TextSignatureNode create() {
            return AbstractFunctionBuiltinsFactory.TextSignatureNodeFactory.create();
        }
    }
}
