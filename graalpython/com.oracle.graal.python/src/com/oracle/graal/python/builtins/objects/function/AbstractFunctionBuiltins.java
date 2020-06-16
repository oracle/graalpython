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

package com.oracle.graal.python.builtins.objects.function;

import static com.oracle.graal.python.nodes.SpecialAttributeNames.__ANNOTATIONS__;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.__CLOSURE__;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.__DICT__;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.__GLOBALS__;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.__MODULE__;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.__NAME__;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.__TEXT_SIGNATURE__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__CALL__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__GET__;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.AttributeError;

import java.util.List;

import com.oracle.graal.python.builtins.Builtin;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.cell.PCell;
import com.oracle.graal.python.builtins.objects.common.PHashingCollection;
import com.oracle.graal.python.builtins.objects.method.PBuiltinMethod;
import com.oracle.graal.python.builtins.objects.method.PMethod;
import com.oracle.graal.python.builtins.objects.module.PythonModule;
import com.oracle.graal.python.builtins.objects.object.PythonObject;
import com.oracle.graal.python.builtins.objects.object.PythonObjectLibrary;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.argument.CreateArgumentsNode;
import com.oracle.graal.python.nodes.attributes.ReadAttributeFromObjectNode;
import com.oracle.graal.python.nodes.attributes.WriteAttributeToObjectNode;
import com.oracle.graal.python.nodes.call.CallDispatchNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonBinaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonTernaryBuiltinNode;
import com.oracle.graal.python.nodes.subscript.GetItemNode;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.profiles.ConditionProfile;

@CoreFunctions(extendClasses = {PythonBuiltinClassType.PFunction, PythonBuiltinClassType.PBuiltinFunction})
public class AbstractFunctionBuiltins extends PythonBuiltins {

    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return AbstractFunctionBuiltinsFactory.getFactories();
    }

    @Builtin(name = __GET__, minNumOfPositionalArgs = 2, maxNumOfPositionalArgs = 3)
    @GenerateNodeFactory
    @SuppressWarnings("unused")
    public abstract static class GetNode extends PythonTernaryBuiltinNode {
        @Specialization(guards = {"!isPNone(instance)"})
        protected PMethod doMethod(PFunction self, Object instance, Object klass) {
            return factory().createMethod(instance, self);
        }

        @Specialization
        protected Object doFunction(PFunction self, PNone instance, Object klass) {
            return self;
        }

        @Specialization(guards = {"!isPNone(instance)"})
        protected PBuiltinMethod doBuiltinMethod(PBuiltinFunction self, Object instance, Object klass) {
            return factory().createBuiltinMethod(instance, self);
        }

        @Specialization
        protected Object doBuiltinFunction(PBuiltinFunction self, PNone instance, Object klass) {
            return self;
        }
    }

    @Builtin(name = __CALL__, minNumOfPositionalArgs = 1, takesVarArgs = true, takesVarKeywordArgs = true)
    @GenerateNodeFactory
    public abstract static class CallNode extends PythonBuiltinNode {
        @Child private CallDispatchNode dispatch = CallDispatchNode.create();
        @Child private CreateArgumentsNode createArgs = CreateArgumentsNode.create();

        @Specialization
        protected Object doIt(VirtualFrame frame, PFunction self, Object[] arguments, PKeyword[] keywords) {
            return dispatch.executeCall(frame, self, createArgs.execute(self, arguments, keywords));
        }

        @Specialization
        protected Object doIt(VirtualFrame frame, PBuiltinFunction self, Object[] arguments, PKeyword[] keywords) {
            return dispatch.executeCall(frame, self, createArgs.execute(self, arguments, keywords));
        }
    }

    @Builtin(name = __CLOSURE__, minNumOfPositionalArgs = 1, isGetter = true)
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
            throw raise(AttributeError, ErrorMessages.OBJ_P_HAS_NO_ATTR_S, "builtin_function_or_method", "__closure__");
        }
    }

    @Builtin(name = __GLOBALS__, minNumOfPositionalArgs = 1, isGetter = true)
    @GenerateNodeFactory
    public abstract static class GetGlobalsNode extends PythonBuiltinNode {
        @Specialization(guards = "!isBuiltinFunction(self)")
        Object getGlobals(PFunction self,
                        @CachedLibrary(limit = "1") PythonObjectLibrary lib,
                        @Cached("createBinaryProfile()") ConditionProfile moduleGlobals,
                        @Cached("createBinaryProfile()") ConditionProfile moduleHasNoDict) {
            // see the make_globals_function from lib-graalpython/functions.py
            PythonObject globals = self.getGlobals();
            if (moduleGlobals.profile(globals instanceof PythonModule)) {
                PHashingCollection dict = lib.getDict(globals);
                if (moduleHasNoDict.profile(dict == null)) {
                    dict = factory().createDictFixedStorage(globals);
                    try {
                        lib.setDict(globals, dict);
                    } catch (UnsupportedMessageException e) {
                        CompilerDirectives.transferToInterpreterAndInvalidate();
                        throw new IllegalStateException(e);
                    }
                }
                return dict;
            } else {
                return globals;
            }
        }

        @SuppressWarnings("unused")
        @Fallback
        Object getGlobals(Object self) {
            throw raise(AttributeError, ErrorMessages.OBJ_P_HAS_NO_ATTR_S, "builtin_function_or_method", "__globals__");
        }
    }

    @Builtin(name = __MODULE__, minNumOfPositionalArgs = 1, maxNumOfPositionalArgs = 2, isGetter = true, isSetter = true)
    @GenerateNodeFactory
    abstract static class GetModuleNode extends PythonBuiltinNode {
        @Specialization(guards = {"!isBuiltinFunction(self)", "isNoValue(none)"})
        Object getModule(VirtualFrame frame, PFunction self, @SuppressWarnings("unused") PNone none,
                        @Cached("create()") ReadAttributeFromObjectNode readObject,
                        @Cached("create()") GetItemNode getItem,
                        @Cached("create()") WriteAttributeToObjectNode writeObject) {
            Object module = readObject.execute(self, __MODULE__);
            if (module == PNone.NO_VALUE) {
                CompilerDirectives.transferToInterpreter();
                PythonObject globals = self.getGlobals();
                if (globals instanceof PythonModule) {
                    module = globals.getAttribute(__NAME__);
                } else {
                    module = getItem.execute(frame, globals, __NAME__);
                }
                writeObject.execute(self, __MODULE__, module);
            }
            return module;
        }

        @Specialization(guards = {"!isBuiltinFunction(self)", "!isNoValue(value)"})
        Object getModule(PFunction self, Object value,
                        @Cached("create()") WriteAttributeToObjectNode writeObject) {
            writeObject.execute(self, __MODULE__, value);
            return PNone.NONE;
        }

        @SuppressWarnings("unused")
        @Specialization
        Object getModule(PBuiltinFunction self, Object value) {
            throw raise(AttributeError, ErrorMessages.OBJ_P_HAS_NO_ATTR_S, "builtin_function_or_method", "__module__");
        }
    }

    @Builtin(name = __ANNOTATIONS__, minNumOfPositionalArgs = 1, maxNumOfPositionalArgs = 2, isGetter = true, isSetter = true)
    @GenerateNodeFactory
    abstract static class GetAnnotationsNode extends PythonBuiltinNode {
        @Specialization(guards = {"!isBuiltinFunction(self)", "isNoValue(none)"})
        Object getModule(PFunction self, @SuppressWarnings("unused") PNone none,
                        @Cached("create()") ReadAttributeFromObjectNode readObject,
                        @Cached("create()") WriteAttributeToObjectNode writeObject) {
            Object annotations = readObject.execute(self, __ANNOTATIONS__);
            if (annotations == PNone.NO_VALUE) {
                annotations = factory().createDict();
                writeObject.execute(self, __ANNOTATIONS__, annotations);
            }
            return annotations;
        }

        @Specialization(guards = {"!isBuiltinFunction(self)", "!isNoValue(value)"})
        Object getModule(PFunction self, Object value,
                        @Cached("create()") WriteAttributeToObjectNode writeObject) {
            writeObject.execute(self, __ANNOTATIONS__, value);
            return PNone.NONE;
        }

        @SuppressWarnings("unused")
        @Specialization
        Object getModule(PBuiltinFunction self, Object value) {
            throw raise(AttributeError, ErrorMessages.OBJ_P_HAS_NO_ATTR_S, "builtin_function_or_method", "__annotations__");
        }
    }

    @Builtin(name = __DICT__, minNumOfPositionalArgs = 1, maxNumOfPositionalArgs = 2, isGetter = true, isSetter = true)
    @GenerateNodeFactory
    abstract static class DictNode extends PythonBinaryBuiltinNode {
        @Specialization(limit = "1")
        PNone dict(PFunction self, PHashingCollection mapping,
                        @CachedLibrary("self") PythonObjectLibrary lib) {
            try {
                lib.setDict(self, mapping);
            } catch (UnsupportedMessageException e) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                throw new IllegalStateException(e);
            }
            return PNone.NONE;
        }

        @Specialization(guards = "isNoValue(mapping)", limit = "1")
        Object dict(PFunction self, @SuppressWarnings("unused") PNone mapping,
                        @CachedLibrary("self") PythonObjectLibrary lib) {
            PHashingCollection dict = lib.getDict(self);
            if (dict == null) {
                dict = factory().createDictFixedStorage(self);
                try {
                    lib.setDict(self, dict);
                } catch (UnsupportedMessageException e) {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    throw new IllegalStateException(e);
                }
            }
            return dict;
        }

        @Specialization
        @SuppressWarnings("unused")
        Object builtinCode(PBuiltinFunction self, Object mapping) {
            throw raise(AttributeError, ErrorMessages.OBJ_P_HAS_NO_ATTR_S, "builtin_function_or_method", "__dict__");
        }
    }

    @Builtin(name = __TEXT_SIGNATURE__, minNumOfPositionalArgs = 1, maxNumOfPositionalArgs = 2, isGetter = true, isSetter = true)
    @GenerateNodeFactory
    public abstract static class TextSignatureNode extends PythonBinaryBuiltinNode {
        @Specialization(guards = {"!isBuiltinFunction(self)", "isNoValue(none)"})
        Object getFunction(PFunction self, @SuppressWarnings("unused") PNone none,
                        @Cached("create()") ReadAttributeFromObjectNode readNode) {
            Object signature = readNode.execute(self, __TEXT_SIGNATURE__);
            if (signature == PNone.NO_VALUE) {
                throw raise(AttributeError, ErrorMessages.OBJ_P_HAS_NO_ATTR_S, "function", "__text_signature__");
            }
            return signature;
        }

        @Specialization(guards = {"!isBuiltinFunction(self)", "!isNoValue(value)"})
        Object setFunction(PFunction self, Object value,
                        @Cached("create()") WriteAttributeToObjectNode writeNode) {
            writeNode.execute(self, __TEXT_SIGNATURE__, value);
            return PNone.NONE;
        }

        @Specialization(guards = "isNoValue(none)")
        protected Object getBuiltin(PBuiltinFunction self, @SuppressWarnings("unused") PNone none) {
            return getSignature(self.getSignature());
        }

        @Specialization(guards = "!isNoValue(value)")
        protected Object setBuiltin(@SuppressWarnings("unused") PBuiltinFunction self,
                        @SuppressWarnings("unused") Object value) {
            throw raise(AttributeError, ErrorMessages.ATTR_S_OF_S_IS_NOT_WRITABLE, "__text_signature__", "builtin_function_or_method");
        }

        @TruffleBoundary
        private static Object getSignature(Signature signature) {
            String[] keywordNames = signature.getKeywordNames();
            boolean takesVarArgs = signature.takesVarArgs();
            boolean takesVarKeywordArgs = signature.takesVarKeywordArgs();

            String[] parameterNames = signature.getParameterIds();
            int paramIdx = 0;

            StringBuilder sb = new StringBuilder();
            char argName = 'a';
            sb.append('(');
            for (int i = 0; i < parameterNames.length; i++) {
                if (paramIdx >= parameterNames.length) {
                    sb.append(", ").append(argName++);
                } else {
                    sb.append(", ").append(parameterNames[paramIdx++]);
                }
            }
            if (parameterNames.length > 0) {
                sb.append(", /");
            }
            if (takesVarArgs) {
                sb.append(", *args");
            }
            if (keywordNames.length > 0) {
                if (!takesVarArgs) {
                    sb.append(", *");
                }
                for (int i = 0; i < keywordNames.length; i++) {
                    sb.append(", ").append(keywordNames[i]).append("=?");
                }
            }
            if (takesVarKeywordArgs) {
                sb.append(", **kwargs");
            }
            sb.append(')');
            return sb.toString();
        }

        public static TextSignatureNode create() {
            return AbstractFunctionBuiltinsFactory.TextSignatureNodeFactory.create();
        }
    }
}
