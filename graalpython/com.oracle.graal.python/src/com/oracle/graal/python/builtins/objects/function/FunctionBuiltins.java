/*
 * Copyright (c) 2017, 2018, Oracle and/or its affiliates.
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

import static com.oracle.graal.python.nodes.SpecialAttributeNames.__DEFAULTS__;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.__KWDEFAULTS__;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.__NAME__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.TRUFFLE_SOURCE;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__REDUCE__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__REPR__;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.TypeError;

import java.util.ArrayList;
import java.util.List;

import com.oracle.graal.python.builtins.Builtin;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.function.FunctionBuiltinsFactory.GetFunctionDefaultsNodeFactory;
import com.oracle.graal.python.builtins.objects.function.FunctionBuiltinsFactory.GetFunctionKeywordDefaultsNodeFactory;
import com.oracle.graal.python.builtins.objects.method.PMethod;
import com.oracle.graal.python.builtins.objects.str.PString;
import com.oracle.graal.python.builtins.objects.tuple.PTuple;
import com.oracle.graal.python.nodes.argument.ReadKeywordNode;
import com.oracle.graal.python.nodes.attributes.ReadAttributeFromObjectNode;
import com.oracle.graal.python.nodes.attributes.WriteAttributeToObjectNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.builtins.PythonBinaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonUnaryBuiltinNode;
import com.oracle.graal.python.nodes.truffle.PythonArithmeticTypes;
import com.oracle.graal.python.runtime.exception.PythonErrorType;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.dsl.TypeSystemReference;
import com.oracle.truffle.api.nodes.NodeUtil;
import com.oracle.truffle.api.profiles.ConditionProfile;

@CoreFunctions(extendClasses = PythonBuiltinClassType.PFunction)
public class FunctionBuiltins extends PythonBuiltins {

    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return FunctionBuiltinsFactory.getFactories();
    }

    @Builtin(name = __REPR__, fixedNumOfPositionalArgs = 1)
    @TypeSystemReference(PythonArithmeticTypes.class)
    @GenerateNodeFactory
    public abstract static class ReprNode extends PythonUnaryBuiltinNode {
        @Specialization(guards = "self.getEnclosingClassName() == null")
        @TruffleBoundary
        Object reprModuleFunction(PFunction self) {
            return String.format("<function %s at 0x%x>", self.getName(), self.hashCode());
        }

        @Specialization(guards = "self.getEnclosingClassName() != null")
        @TruffleBoundary
        Object reprClassFunction(PFunction self) {
            return String.format("<function %s.%s at 0x%x>", self.getEnclosingClassName(), self.getName(), self.hashCode());
        }
    }

    @Builtin(name = __NAME__, minNumOfPositionalArgs = 1, maxNumOfPositionalArgs = 2, isGetter = true, isSetter = true)
    @GenerateNodeFactory
    abstract static class NameNode extends PythonBinaryBuiltinNode {
        @Child WriteAttributeToObjectNode writeNode;

        @Specialization(guards = "isNoValue(noValue)")
        Object getName(PFunction self, @SuppressWarnings("unused") PNone noValue,
                        @Cached("create()") ReadAttributeFromObjectNode readNode) {
            Object storedName = readNode.execute(self, __NAME__);
            if (storedName == PNone.NO_VALUE) {
                return self.getName();
            } else {
                return storedName;
            }
        }

        @Specialization(guards = "isNoValue(noValue)")
        Object getName(PBuiltinFunction self, @SuppressWarnings("unused") PNone noValue) {
            return self.getName();
        }

        @Specialization
        Object setName(PFunction self, String value) {
            if (writeNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                writeNode = insert(WriteAttributeToObjectNode.create());
            }
            writeNode.execute(self, __NAME__, value);
            self.getArity().setFunctionName(value);
            return PNone.NONE;
        }

        @Specialization
        Object setName(PFunction self, PString value) {
            return setName(self, value.getValue());
        }

        @SuppressWarnings("unused")
        @Specialization
        Object setName(PBuiltinFunction self, Object value) {
            throw raise(PythonErrorType.AttributeError, "attribute '__name__' of builtin function is not writable");
        }

        @SuppressWarnings("unused")
        @Fallback
        Object setName(Object self, Object value) {
            throw raise(PythonErrorType.TypeError, "__name__ must be set to a string object");
        }
    }

    @Builtin(name = __DEFAULTS__, minNumOfPositionalArgs = 1, maxNumOfPositionalArgs = 2, isGetter = true, isSetter = true)
    @GenerateNodeFactory
    public abstract static class GetFunctionDefaultsNode extends PythonBinaryBuiltinNode {
        private final ConditionProfile nullDefaultsProfile = ConditionProfile.createBinaryProfile();

        @TruffleBoundary
        private static Object[] extractDefaults(PFunction function) {
            List<Object> defaultValues = new ArrayList<>();
            List<ReadKeywordNode> readKeywordNodes = NodeUtil.findAllNodeInstances(function.getFunctionRootNode(), ReadKeywordNode.class);
            for (ReadKeywordNode readKeywordNode : readKeywordNodes) {
                Object defaultValue = readKeywordNode.getDefaultValue();
                if (defaultValue != null) {
                    defaultValues.add(defaultValue);
                }
            }
            return defaultValues.toArray();
        }

        private Object getDefaults(PFunction function) {
            Object[] defaults = function.getDefaults();
            if (nullDefaultsProfile.profile(defaults == null)) {
                defaults = extractDefaults(function);
            }

            assert defaults != null;
            return (defaults.length == 0) ? PNone.NONE : factory().createTuple(defaults);
        }

        @Specialization
        Object defaults(PFunction self, @SuppressWarnings("unused") PNone defaults) {
            return getDefaults(self);
        }

        @Specialization
        Object defaults(PFunction self, PTuple defaults) {
            self.setDefaults(defaults.getArray());
            return PNone.NONE;
        }

        public static GetFunctionDefaultsNode create() {
            return GetFunctionDefaultsNodeFactory.create();
        }
    }

    @Builtin(name = __KWDEFAULTS__, fixedNumOfPositionalArgs = 1, isGetter = true)
    @GenerateNodeFactory
    public abstract static class GetFunctionKeywordDefaultsNode extends PythonUnaryBuiltinNode {
        @TruffleBoundary
        private static PKeyword[] extractDefaults(PFunction function) {
            ArrayList<PKeyword> kwdefaults = new ArrayList<>();
            List<ReadKeywordNode> readKeywordNodes = NodeUtil.findAllNodeInstances(function.getFunctionRootNode(), ReadKeywordNode.class);
            for (ReadKeywordNode readKeywordNode : readKeywordNodes) {
                if (!readKeywordNode.canBePositional()) {
                    Object defaultValue = readKeywordNode.getDefaultValue();
                    if (defaultValue != null) {
                        kwdefaults.add(new PKeyword(readKeywordNode.getName(), defaultValue));
                    }
                }
            }

            return kwdefaults.toArray(new PKeyword[0]);
        }

        @Specialization(guards = "!takesVarargs(self)")
        Object doNoKeywordOnlyArgs(@SuppressWarnings("unused") PFunction self) {
            return PNone.NONE;
        }

        @Specialization(guards = "takesVarargs(self)")
        Object doGeneric(PFunction self) {
            PKeyword[] kwdefaults = extractDefaults(self);
            if (kwdefaults.length > 0) {
                return factory().createDict(kwdefaults);
            }
            return PNone.NONE;
        }

        protected static boolean takesVarargs(PFunction self) {
            return self.getArity().takesVarArgs();
        }

        public static GetFunctionKeywordDefaultsNode create() {
            return GetFunctionKeywordDefaultsNodeFactory.create();
        }
    }

    @Builtin(name = __REDUCE__, fixedNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class ReduceNode extends PythonUnaryBuiltinNode {
        @Fallback
        Object doGeneric(@SuppressWarnings("unused") Object obj) {
            throw raise(TypeError, "can't pickle function objects");
        }
    }

    @Builtin(name = TRUFFLE_SOURCE, fixedNumOfPositionalArgs = 1, isGetter = true)
    @GenerateNodeFactory
    public abstract static class GetFunctionSourceNode extends PythonUnaryBuiltinNode {
        @Specialization
        Object doFunction(PFunction function) {
            String sourceCode = function.getSourceCode();
            if (sourceCode != null) {
                return sourceCode;
            }
            return PNone.NONE;
        }

        @Specialization
        Object doMethod(PMethod method) {
            Object function = method.getFunction();
            if (function instanceof PFunction) {
                String sourceCode = ((PFunction) function).getSourceCode();
                if (sourceCode != null) {
                    return sourceCode;
                }
            }
            return PNone.NONE;
        }

        @Fallback
        Object doGeneric(Object object) {
            throw raise(TypeError, "getting the source is not supported for '%p'", object);
        }
    }

}
