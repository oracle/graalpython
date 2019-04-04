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

package com.oracle.graal.python.builtins.objects.function;

import static com.oracle.graal.python.nodes.SpecialAttributeNames.__CODE__;
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
import com.oracle.graal.python.builtins.objects.code.PCode;
import com.oracle.graal.python.builtins.objects.common.HashingStorage;
import com.oracle.graal.python.builtins.objects.dict.PDict;
import com.oracle.graal.python.builtins.objects.method.PMethod;
import com.oracle.graal.python.builtins.objects.str.PString;
import com.oracle.graal.python.builtins.objects.tuple.PTuple;
import com.oracle.graal.python.nodes.attributes.ReadAttributeFromObjectNode;
import com.oracle.graal.python.nodes.attributes.WriteAttributeToObjectNode;
import com.oracle.graal.python.nodes.builtins.FunctionNodes.GetFunctionCodeNode;
import com.oracle.graal.python.nodes.builtins.FunctionNodes.GetFunctionDefaultsNode;
import com.oracle.graal.python.nodes.builtins.FunctionNodes.GetFunctionKeywordDefaultsNode;
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

@CoreFunctions(extendClasses = PythonBuiltinClassType.PFunction)
public class FunctionBuiltins extends PythonBuiltins {

    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return FunctionBuiltinsFactory.getFactories();
    }

    @Builtin(name = __REPR__, minNumOfPositionalArgs = 1)
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
        @Specialization(guards = "isNoValue(noValue)")
        Object getName(PFunction self, @SuppressWarnings("unused") PNone noValue,
                        @Cached("create()") ReadAttributeFromObjectNode readName) {
            Object name = readName.execute(self, __NAME__);
            if (name == PNone.NO_VALUE) {
                return self.getName();
            } else {
                return name;
            }
        }

        @Specialization(guards = "isNoValue(noValue)")
        Object getName(PBuiltinFunction self, @SuppressWarnings("unused") PNone noValue) {
            return self.getName();
        }

        @Specialization
        Object setName(PFunction self, String value,
                        @Cached("create()") WriteAttributeToObjectNode writeName) {
            writeName.execute(self, __NAME__, value);
            return PNone.NONE;
        }

        @Specialization
        Object setName(PFunction self, PString value,
                        @Cached("create()") WriteAttributeToObjectNode writeName) {
            writeName.execute(self, __NAME__, value.getValue());
            return PNone.NONE;
        }

        @Specialization
        Object setName(@SuppressWarnings("unused") PBuiltinFunction self, @SuppressWarnings("unused") Object value) {
            throw raise(PythonErrorType.AttributeError, "attribute '__name__' of builtin function is not writable");
        }

        @Fallback
        Object setName(@SuppressWarnings("unused") Object self, @SuppressWarnings("unused") Object value) {
            throw raise(PythonErrorType.TypeError, "__name__ must be set to a string object");
        }
    }

    @Builtin(name = __DEFAULTS__, minNumOfPositionalArgs = 1, maxNumOfPositionalArgs = 2, isGetter = true, isSetter = true)
    @GenerateNodeFactory
    public abstract static class GetDefaultsNode extends PythonBinaryBuiltinNode {
        @Specialization(guards = "isNoValue(defaults)")
        Object defaults(PFunction self, @SuppressWarnings("unused") PNone defaults,
                        @Cached("create()") GetFunctionDefaultsNode getFunctionDefaultsNode) {
            Object[] argDefaults = getFunctionDefaultsNode.execute(self);
            assert argDefaults != null;
            return (argDefaults.length == 0) ? PNone.NONE : factory().createTuple(argDefaults);
        }

        @Specialization
        Object setDefaults(PFunction self, PTuple defaults) {
            self.setDefaults(defaults.getArray());
            return PNone.NONE;
        }

        @Specialization(guards = "!isNoValue(defaults)")
        Object setDefaults(PFunction self, @SuppressWarnings("unused") PNone defaults) {
            self.setDefaults(new Object[0]);
            return PNone.NONE;
        }
    }

    @Builtin(name = __KWDEFAULTS__, minNumOfPositionalArgs = 1, maxNumOfPositionalArgs = 2, isGetter = true, isSetter = true)
    @GenerateNodeFactory
    public abstract static class GetKeywordDefaultsNode extends PythonBinaryBuiltinNode {
        @Specialization(guards = "isNoValue(arg)")
        Object get(PFunction self, @SuppressWarnings("unused") PNone arg,
                        @Cached("create()") GetFunctionKeywordDefaultsNode getFunctionKeywordDefaultsNode) {
            PKeyword[] kwdefaults = getFunctionKeywordDefaultsNode.execute(self);
            return (kwdefaults.length > 0) ? factory().createDict(kwdefaults) : PNone.NONE;
        }

        @Specialization(guards = "!isNoValue(arg)")
        Object set(PFunction self, @SuppressWarnings("unused") PNone arg) {
            self.setKwDefaults(PKeyword.EMPTY_KEYWORDS);
            return PNone.NONE;
        }

        @Specialization(guards = "!isNoValue(arg)")
        @TruffleBoundary
        Object set(PFunction self, PDict arg) {
            CompilerDirectives.transferToInterpreter();
            ArrayList<PKeyword> keywords = new ArrayList<>();
            for (Object k : arg.getDictStorage().keys()) {
                if (!(k instanceof String)) {
                    throw raise(PythonBuiltinClassType.TypeError, "keyword names must be str, get %p", k);
                }
                keywords.add(new PKeyword((String) k, arg.getDictStorage().getItem(k, HashingStorage.getSlowPathEquivalence(k))));
            }
            self.setKwDefaults(keywords.toArray(new PKeyword[keywords.size()]));
            return PNone.NONE;
        }
    }

    @Builtin(name = __REDUCE__, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class ReduceNode extends PythonUnaryBuiltinNode {
        @Fallback
        Object doGeneric(@SuppressWarnings("unused") Object obj) {
            throw raise(TypeError, "can't pickle function objects");
        }
    }

    @Builtin(name = TRUFFLE_SOURCE, minNumOfPositionalArgs = 1, isGetter = true)
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

    @Builtin(name = __CODE__, minNumOfPositionalArgs = 1, maxNumOfPositionalArgs = 2, isGetter = true, isSetter = true)
    @GenerateNodeFactory
    public abstract static class GetCodeNode extends PythonBinaryBuiltinNode {
        @Specialization(guards = {"isNoValue(none)"})
        Object getCodeU(PFunction self, @SuppressWarnings("unused") PNone none,
                        @Cached("create()") GetFunctionCodeNode getFunctionCodeNode) {
            return getFunctionCodeNode.execute(self);
        }

        @SuppressWarnings("unused")
        @Specialization
        Object setCode(PFunction self, PCode code) {
            int closureLength = self.getClosure() == null ? 0 : self.getClosure().length;
            int freeVarsLength = code.getFreeVars().length;
            if (closureLength != freeVarsLength) {
                throw raise(PythonBuiltinClassType.ValueError, "%s() requires a code object with %d free vars, not %d", self.getName(), closureLength, freeVarsLength);
            }
            self.setCode(code);
            return PNone.NONE;
        }
    }
}
