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

import static com.oracle.graal.python.nodes.SpecialAttributeNames.J___CODE__;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.J___DEFAULTS__;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.J___KWDEFAULTS__;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.J___NAME__;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.J___QUALNAME__;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.T___DEFAULTS__;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.T___NAME__;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.T___QUALNAME__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J_TRUFFLE_SOURCE;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___GET__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___REPR__;
import static com.oracle.graal.python.nodes.truffle.TruffleStringMigrationHelpers.assertNoJavaString;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.TypeError;
import static com.oracle.graal.python.util.PythonUtils.TS_ENCODING;

import java.util.ArrayList;
import java.util.List;

import com.oracle.graal.python.builtins.Builtin;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.PythonAbstractObject;
import com.oracle.graal.python.builtins.objects.code.PCode;
import com.oracle.graal.python.builtins.objects.common.HashingStorage;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodes.HashingStorageGetIterator;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodes.HashingStorageIterator;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodes.HashingStorageIteratorKey;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodes.HashingStorageIteratorNext;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodes.HashingStorageIteratorValue;
import com.oracle.graal.python.builtins.objects.common.SequenceNodes.GetObjectArrayNode;
import com.oracle.graal.python.builtins.objects.dict.PDict;
import com.oracle.graal.python.builtins.objects.method.PMethod;
import com.oracle.graal.python.builtins.objects.str.PString;
import com.oracle.graal.python.builtins.objects.str.StringNodes;
import com.oracle.graal.python.builtins.objects.str.StringUtils.SimpleTruffleStringFormatNode;
import com.oracle.graal.python.builtins.objects.tuple.PTuple;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.builtins.FunctionNodes.GetFunctionCodeNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.builtins.PythonBinaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonTernaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonUnaryBuiltinNode;
import com.oracle.graal.python.nodes.truffle.PythonArithmeticTypes;
import com.oracle.graal.python.runtime.object.PythonObjectFactory;
import com.oracle.graal.python.util.PythonUtils;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.dsl.TypeSystemReference;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.strings.TruffleString;

@CoreFunctions(extendClasses = PythonBuiltinClassType.PFunction)
public final class FunctionBuiltins extends PythonBuiltins {

    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return FunctionBuiltinsFactory.getFactories();
    }

    @Builtin(name = J___GET__, minNumOfPositionalArgs = 2, maxNumOfPositionalArgs = 3)
    @GenerateNodeFactory
    @SuppressWarnings("unused")
    public abstract static class GetNode extends PythonTernaryBuiltinNode {
        @Specialization(guards = {"!isPNone(instance)"})
        static PMethod doMethod(PFunction self, Object instance, Object klass,
                        @Cached PythonObjectFactory factory) {
            return factory.createMethod(instance, self);
        }

        @Specialization
        static Object doFunction(PFunction self, PNone instance, Object klass) {
            return self;
        }
    }

    @Builtin(name = J___REPR__, minNumOfPositionalArgs = 1)
    @TypeSystemReference(PythonArithmeticTypes.class)
    @GenerateNodeFactory
    abstract static class ReprNode extends PythonUnaryBuiltinNode {
        @Specialization
        static TruffleString reprFunction(PFunction self,
                        @Cached SimpleTruffleStringFormatNode simpleTruffleStringFormatNode) {
            return simpleTruffleStringFormatNode.format("<function %s at 0x%s>", self.getQualname(), PythonAbstractObject.objectHashCodeAsHexString(self));
        }
    }

    @Builtin(name = J___NAME__, minNumOfPositionalArgs = 1, maxNumOfPositionalArgs = 2, isGetter = true, isSetter = true)
    @GenerateNodeFactory
    abstract static class NameNode extends PythonBinaryBuiltinNode {
        @Specialization(guards = "isNoValue(noValue)")
        static Object getName(PFunction self, @SuppressWarnings("unused") PNone noValue) {
            return self.getName();
        }

        @Specialization
        static Object setName(PFunction self, TruffleString value) {
            self.setName(value);
            return PNone.NONE;
        }

        @Specialization(guards = "!isNoValue(value)")
        static Object setName(PFunction self, Object value,
                        @Bind("this") Node inliningTarget,
                        @Cached StringNodes.CastToTruffleStringCheckedNode cast) {
            return setName(self, cast.cast(inliningTarget, value, ErrorMessages.MUST_BE_SET_TO_S_OBJ, T___NAME__, "string"));
        }
    }

    @Builtin(name = J___QUALNAME__, minNumOfPositionalArgs = 1, maxNumOfPositionalArgs = 2, isGetter = true, isSetter = true)
    @GenerateNodeFactory
    abstract static class QualnameNode extends PythonBinaryBuiltinNode {
        @Specialization(guards = "isNoValue(noValue)")
        static Object getQualname(PFunction self, @SuppressWarnings("unused") PNone noValue) {
            return self.getQualname();
        }

        @Specialization
        static Object setQualname(PFunction self, TruffleString value) {
            self.setQualname(value);
            return PNone.NONE;
        }

        @Specialization(guards = "!isNoValue(value)")
        static Object setQualname(PFunction self, Object value,
                        @Bind("this") Node inliningTarget,
                        @Cached StringNodes.CastToTruffleStringCheckedNode cast) {
            return setQualname(self, cast.cast(inliningTarget, value, ErrorMessages.MUST_BE_SET_TO_S_OBJ, T___QUALNAME__, "string"));
        }
    }

    @Builtin(name = J___DEFAULTS__, minNumOfPositionalArgs = 1, maxNumOfPositionalArgs = 2, isGetter = true, isSetter = true, allowsDelete = true)
    @GenerateNodeFactory
    public abstract static class GetDefaultsNode extends PythonBinaryBuiltinNode {
        @Specialization(guards = "isNoValue(defaults)")
        static Object defaults(PFunction self, @SuppressWarnings("unused") PNone defaults,
                        @Cached PythonObjectFactory factory) {
            Object[] argDefaults = self.getDefaults();
            assert argDefaults != null;
            return (argDefaults.length == 0) ? PNone.NONE : factory.createTuple(argDefaults);
        }

        @Specialization
        static Object setDefaults(PFunction self, PTuple defaults,
                        @Bind("this") Node inliningTarget,
                        @Cached GetObjectArrayNode getObjectArrayNode) {
            self.setDefaults(getObjectArrayNode.execute(inliningTarget, defaults));
            return PNone.NONE;
        }

        @Specialization(guards = "isDeleteMarker(defaults)")
        static Object setDefaults(PFunction self, @SuppressWarnings("unused") Object defaults) {
            self.setDefaults(PythonUtils.EMPTY_OBJECT_ARRAY);
            return PNone.NONE;
        }

        @Specialization(guards = "!isNoValue(defaults)")
        static Object setDefaults(PFunction self, @SuppressWarnings("unused") PNone defaults) {
            self.setDefaults(PythonUtils.EMPTY_OBJECT_ARRAY);
            return PNone.NONE;
        }

        @Fallback
        @SuppressWarnings("unused")
        Object setDefaults(Object self, Object defaults) {
            throw raise(TypeError, ErrorMessages.MUST_BE_SET_TO_S_NOT_P, T___DEFAULTS__, "tuple");
        }
    }

    @Builtin(name = J___KWDEFAULTS__, minNumOfPositionalArgs = 1, maxNumOfPositionalArgs = 2, isGetter = true, isSetter = true)
    @GenerateNodeFactory
    public abstract static class GetKeywordDefaultsNode extends PythonBinaryBuiltinNode {
        @Specialization(guards = "isNoValue(arg)")
        static Object get(PFunction self, @SuppressWarnings("unused") PNone arg,
                        @Cached PythonObjectFactory factory) {
            PKeyword[] kwdefaults = self.getKwDefaults();
            return (kwdefaults.length > 0) ? factory.createDict(kwdefaults) : PNone.NONE;
        }

        @Specialization(guards = "!isNoValue(arg)")
        static Object set(PFunction self, @SuppressWarnings("unused") PNone arg) {
            self.setKwDefaults(PKeyword.EMPTY_KEYWORDS);
            return PNone.NONE;
        }

        @Specialization(guards = "!isNoValue(arg)")
        @TruffleBoundary
        Object set(PFunction self, PDict arg) {
            CompilerDirectives.transferToInterpreter();
            ArrayList<PKeyword> keywords = new ArrayList<>();
            final HashingStorage storage = arg.getDictStorage();
            HashingStorageIterator it = HashingStorageGetIterator.executeUncached(storage);
            while (HashingStorageIteratorNext.executeUncached(storage, it)) {
                Object key = assertNoJavaString(HashingStorageIteratorKey.executeUncached(storage, it));
                if (key instanceof PString) {
                    key = ((PString) key).getValueUncached();
                } else if (!(key instanceof TruffleString)) {
                    throw raise(PythonBuiltinClassType.TypeError, ErrorMessages.KEYWORD_NAMES_MUST_BE_STR_GOT_P, key);
                }
                keywords.add(new PKeyword((TruffleString) key, HashingStorageIteratorValue.executeUncached(storage, it)));
            }
            self.setKwDefaults(keywords.isEmpty() ? PKeyword.EMPTY_KEYWORDS : keywords.toArray(new PKeyword[keywords.size()]));
            return PNone.NONE;
        }
    }

    @Builtin(name = J_TRUFFLE_SOURCE, minNumOfPositionalArgs = 1, isGetter = true)
    @GenerateNodeFactory
    public abstract static class GetFunctionSourceNode extends PythonUnaryBuiltinNode {
        @Specialization
        static Object doFunction(PFunction function,
                        @Shared("js2ts") @Cached TruffleString.FromJavaStringNode fromJavaStringNode) {
            String sourceCode = function.getSourceCode();
            if (sourceCode != null) {
                return fromJavaStringNode.execute(sourceCode, TS_ENCODING);
            }
            return PNone.NONE;
        }

        @Specialization
        static Object doMethod(PMethod method,
                        @Shared("js2ts") @Cached TruffleString.FromJavaStringNode fromJavaStringNode) {
            Object function = method.getFunction();
            if (function instanceof PFunction) {
                String sourceCode = ((PFunction) function).getSourceCode();
                if (sourceCode != null) {
                    return fromJavaStringNode.execute(sourceCode, TS_ENCODING);
                }
            }
            return PNone.NONE;
        }

        @Fallback
        Object doGeneric(Object object) {
            throw raise(TypeError, ErrorMessages.GETTING_THER_SOURCE_NOT_SUPPORTED_FOR_P, object);
        }
    }

    @Builtin(name = J___CODE__, minNumOfPositionalArgs = 1, maxNumOfPositionalArgs = 2, isGetter = true, isSetter = true)
    @GenerateNodeFactory
    public abstract static class GetCodeNode extends PythonBinaryBuiltinNode {
        @Specialization(guards = {"isNoValue(none)"})
        static Object getCodeU(PFunction self, @SuppressWarnings("unused") PNone none,
                        @Bind("this") Node inliningTarget,
                        @Cached GetFunctionCodeNode getFunctionCodeNode) {
            return getFunctionCodeNode.execute(inliningTarget, self);
        }

        @SuppressWarnings("unused")
        @Specialization
        Object setCode(PFunction self, PCode code) {
            int closureLength = self.getClosure() == null ? 0 : self.getClosure().length;
            int freeVarsLength = code.getFreeVars().length;
            if (closureLength != freeVarsLength) {
                throw raise(PythonBuiltinClassType.ValueError, ErrorMessages.REQUIRES_CODE_OBJ, self.getName(), closureLength, freeVarsLength);
            }
            self.setCode(code);
            return PNone.NONE;
        }
    }
}
