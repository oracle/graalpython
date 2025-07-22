/*
 * Copyright (c) 2017, 2025, Oracle and/or its affiliates.
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

import static com.oracle.graal.python.nodes.BuiltinNames.T_LAMBDA_NAME;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.J___CODE__;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.J___DEFAULTS__;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.J___DOC__;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.J___KWDEFAULTS__;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.J___NAME__;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.J___QUALNAME__;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.J___TYPE_PARAMS__;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.T___DEFAULTS__;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.T___NAME__;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.T___QUALNAME__;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.T___TYPE_PARAMS__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J_TRUFFLE_SOURCE;
import static com.oracle.graal.python.nodes.truffle.TruffleStringMigrationHelpers.assertNoJavaString;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.TypeError;
import static com.oracle.graal.python.util.PythonUtils.TS_ENCODING;

import java.util.ArrayList;
import java.util.List;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.annotations.Slot;
import com.oracle.graal.python.annotations.Slot.SlotKind;
import com.oracle.graal.python.annotations.Slot.SlotSignature;
import com.oracle.graal.python.builtins.Builtin;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.PythonAbstractObject;
import com.oracle.graal.python.builtins.objects.cell.PCell;
import com.oracle.graal.python.builtins.objects.code.PCode;
import com.oracle.graal.python.builtins.objects.common.HashingStorage;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodes.HashingStorageGetIterator;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodes.HashingStorageIterator;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodes.HashingStorageIteratorKey;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodes.HashingStorageIteratorNext;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodes.HashingStorageIteratorValue;
import com.oracle.graal.python.builtins.objects.common.SequenceNodes.GetObjectArrayNode;
import com.oracle.graal.python.builtins.objects.dict.PDict;
import com.oracle.graal.python.builtins.objects.getsetdescriptor.DescriptorDeleteMarker;
import com.oracle.graal.python.builtins.objects.method.PMethod;
import com.oracle.graal.python.builtins.objects.str.PString;
import com.oracle.graal.python.builtins.objects.str.StringNodes;
import com.oracle.graal.python.builtins.objects.str.StringUtils.SimpleTruffleStringFormatNode;
import com.oracle.graal.python.builtins.objects.tuple.PTuple;
import com.oracle.graal.python.builtins.objects.type.TpSlots;
import com.oracle.graal.python.builtins.objects.type.slots.TpSlotDescrGet.DescrGetBuiltinNode;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.attributes.ReadAttributeFromObjectNode;
import com.oracle.graal.python.nodes.attributes.WriteAttributeToObjectNode;
import com.oracle.graal.python.nodes.builtins.FunctionNodes.GetFunctionCodeNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonBinaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonUnaryBuiltinNode;
import com.oracle.graal.python.runtime.object.PFactory;
import com.oracle.graal.python.util.PythonUtils;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.strings.TruffleString;

@CoreFunctions(extendClasses = PythonBuiltinClassType.PFunction)
public final class FunctionBuiltins extends PythonBuiltins {
    public static final TpSlots SLOTS = FunctionBuiltinsSlotsGen.SLOTS;

    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return FunctionBuiltinsFactory.getFactories();
    }

    @Slot(value = SlotKind.tp_new, isComplex = true)
    @SlotSignature(name = "function", minNumOfPositionalArgs = 3, parameterNames = {"$cls", "code", "globals", "name", "argdefs", "closure"})
    @GenerateNodeFactory
    public abstract static class FunctionNode extends PythonBuiltinNode {

        @Specialization
        static PFunction function(@SuppressWarnings("unused") Object cls, PCode code, PDict globals, TruffleString name, @SuppressWarnings("unused") PNone defaultArgs,
                        @SuppressWarnings("unused") PNone closure,
                        @Bind PythonLanguage language) {
            return PFactory.createFunction(language, name, code, globals, null);
        }

        @Specialization
        static PFunction function(@SuppressWarnings("unused") Object cls, PCode code, PDict globals, @SuppressWarnings("unused") PNone name, @SuppressWarnings("unused") PNone defaultArgs,
                        PTuple closure,
                        @Bind Node inliningTarget,
                        @Shared("getObjectArrayNode") @Cached GetObjectArrayNode getObjectArrayNode,
                        @Bind PythonLanguage language) {
            return PFactory.createFunction(language, T_LAMBDA_NAME, code, globals, PCell.toCellArray(getObjectArrayNode.execute(inliningTarget, closure)));
        }

        @Specialization
        static PFunction function(@SuppressWarnings("unused") Object cls, PCode code, PDict globals, @SuppressWarnings("unused") PNone name, @SuppressWarnings("unused") PNone defaultArgs,
                        @SuppressWarnings("unused") PNone closure,
                        @SuppressWarnings("unused") @Shared("getObjectArrayNode") @Cached GetObjectArrayNode getObjectArrayNode,
                        @Bind PythonLanguage language) {
            return PFactory.createFunction(language, T_LAMBDA_NAME, code, globals, null);
        }

        @Specialization
        static PFunction function(@SuppressWarnings("unused") Object cls, PCode code, PDict globals, TruffleString name, @SuppressWarnings("unused") PNone defaultArgs, PTuple closure,
                        @Bind Node inliningTarget,
                        @Shared("getObjectArrayNode") @Cached GetObjectArrayNode getObjectArrayNode,
                        @Bind PythonLanguage language) {
            return PFactory.createFunction(language, name, code, globals, PCell.toCellArray(getObjectArrayNode.execute(inliningTarget, closure)));
        }

        @Specialization
        static PFunction function(@SuppressWarnings("unused") Object cls, PCode code, PDict globals, @SuppressWarnings("unused") PNone name, PTuple defaultArgs,
                        @SuppressWarnings("unused") PNone closure,
                        @Bind Node inliningTarget,
                        @Shared("getObjectArrayNode") @Cached GetObjectArrayNode getObjectArrayNode,
                        @Bind PythonLanguage language) {
            // TODO split defaults of positional args from kwDefaults
            return PFactory.createFunction(language, code.getName(), code, globals, getObjectArrayNode.execute(inliningTarget, defaultArgs), null, null);
        }

        @Specialization
        static PFunction function(@SuppressWarnings("unused") Object cls, PCode code, PDict globals, TruffleString name, PTuple defaultArgs, @SuppressWarnings("unused") PNone closure,
                        @Bind Node inliningTarget,
                        @Shared("getObjectArrayNode") @Cached GetObjectArrayNode getObjectArrayNode,
                        @Bind PythonLanguage language) {
            // TODO split defaults of positional args from kwDefaults
            return PFactory.createFunction(language, name, code, globals, getObjectArrayNode.execute(inliningTarget, defaultArgs), null, null);
        }

        @Specialization
        static PFunction function(@SuppressWarnings("unused") Object cls, PCode code, PDict globals, TruffleString name, PTuple defaultArgs, PTuple closure,
                        @Bind Node inliningTarget,
                        @Shared("getObjectArrayNode") @Cached GetObjectArrayNode getObjectArrayNode,
                        @Bind PythonLanguage language) {
            // TODO split defaults of positional args from kwDefaults
            return PFactory.createFunction(language, name, code, globals, getObjectArrayNode.execute(inliningTarget, defaultArgs), null,
                            PCell.toCellArray(getObjectArrayNode.execute(inliningTarget, closure)));
        }

        @Fallback
        @SuppressWarnings("unused")
        static PFunction function(@SuppressWarnings("unused") Object cls, Object code, Object globals, Object name, Object defaultArgs, Object closure,
                        @Bind Node inliningTarget) {
            throw PRaiseNode.raiseStatic(inliningTarget, TypeError, ErrorMessages.FUNC_CONSTRUCTION_NOT_SUPPORTED, cls, code, globals, name, defaultArgs, closure);
        }
    }

    @Slot(SlotKind.tp_descr_get)
    @GenerateUncached
    @GenerateNodeFactory
    public abstract static class GetNode extends DescrGetBuiltinNode {
        @Specialization(guards = {"!isPNone(instance)"})
        static PMethod doMethod(PFunction self, Object instance, @SuppressWarnings("unused") Object klass,
                        @Bind PythonLanguage language) {
            return PFactory.createMethod(language, instance, self);
        }

        @Specialization
        static Object doFunction(PFunction self, @SuppressWarnings("unused") PNone instance, @SuppressWarnings("unused") Object klass) {
            return self;
        }
    }

    @Slot(value = SlotKind.tp_repr, isComplex = true)
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
                        @Bind Node inliningTarget,
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
                        @Bind Node inliningTarget,
                        @Cached StringNodes.CastToTruffleStringCheckedNode cast) {
            return setQualname(self, cast.cast(inliningTarget, value, ErrorMessages.MUST_BE_SET_TO_S_OBJ, T___QUALNAME__, "string"));
        }
    }

    @Builtin(name = J___DEFAULTS__, minNumOfPositionalArgs = 1, maxNumOfPositionalArgs = 2, isGetter = true, isSetter = true, allowsDelete = true)
    @GenerateNodeFactory
    public abstract static class GetDefaultsNode extends PythonBinaryBuiltinNode {
        @Specialization(guards = "isNoValue(defaults)")
        static Object defaults(PFunction self, @SuppressWarnings("unused") PNone defaults,
                        @Bind PythonLanguage language) {
            Object[] argDefaults = self.getDefaults();
            assert argDefaults != null;
            return (argDefaults.length == 0) ? PNone.NONE : PFactory.createTuple(language, argDefaults);
        }

        @Specialization
        static Object setDefaults(PFunction self, PTuple defaults,
                        @Bind Node inliningTarget,
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
        static Object setDefaults(Object self, Object defaults,
                        @Bind Node inliningTarget) {
            throw PRaiseNode.raiseStatic(inliningTarget, TypeError, ErrorMessages.MUST_BE_SET_TO_S_NOT_P, T___DEFAULTS__, "tuple", defaults);
        }
    }

    @Builtin(name = J___KWDEFAULTS__, minNumOfPositionalArgs = 1, maxNumOfPositionalArgs = 2, isGetter = true, isSetter = true)
    @GenerateNodeFactory
    public abstract static class GetKeywordDefaultsNode extends PythonBinaryBuiltinNode {
        @Specialization(guards = "isNoValue(arg)")
        static Object get(PFunction self, @SuppressWarnings("unused") PNone arg,
                        @Bind PythonLanguage language) {
            PKeyword[] kwdefaults = self.getKwDefaults();
            return (kwdefaults.length > 0) ? PFactory.createDict(language, kwdefaults) : PNone.NONE;
        }

        @Specialization(guards = "!isNoValue(arg)")
        static Object set(PFunction self, @SuppressWarnings("unused") PNone arg) {
            self.setKwDefaults(PKeyword.EMPTY_KEYWORDS);
            return PNone.NONE;
        }

        @Specialization(guards = "!isNoValue(arg)")
        @TruffleBoundary
        Object set(PFunction self, PDict arg) {
            ArrayList<PKeyword> keywords = new ArrayList<>();
            final HashingStorage storage = arg.getDictStorage();
            HashingStorageIterator it = HashingStorageGetIterator.executeUncached(storage);
            while (HashingStorageIteratorNext.executeUncached(storage, it)) {
                Object key = assertNoJavaString(HashingStorageIteratorKey.executeUncached(storage, it));
                if (key instanceof PString) {
                    key = ((PString) key).getValueUncached();
                } else if (!(key instanceof TruffleString)) {
                    throw PRaiseNode.raiseStatic(this, PythonBuiltinClassType.TypeError, ErrorMessages.KEYWORD_NAMES_MUST_BE_STR_GOT_P, key);
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
        static Object doGeneric(Object object,
                        @Bind Node inliningTarget) {
            throw PRaiseNode.raiseStatic(inliningTarget, TypeError, ErrorMessages.GETTING_THER_SOURCE_NOT_SUPPORTED_FOR_P, object);
        }
    }

    @Builtin(name = J___CODE__, minNumOfPositionalArgs = 1, maxNumOfPositionalArgs = 2, isGetter = true, isSetter = true)
    @GenerateNodeFactory
    public abstract static class GetCodeNode extends PythonBinaryBuiltinNode {
        @Specialization(guards = {"isNoValue(none)"})
        static Object getCodeU(PFunction self, @SuppressWarnings("unused") PNone none,
                        @Bind Node inliningTarget,
                        @Cached GetFunctionCodeNode getFunctionCodeNode) {
            return getFunctionCodeNode.execute(inliningTarget, self);
        }

        @SuppressWarnings("unused")
        @Specialization
        static Object setCode(PFunction self, PCode code,
                        @Bind Node inliningTarget,
                        @Cached PRaiseNode raiseNode) {
            int closureLength = self.getClosure() == null ? 0 : self.getClosure().length;
            int freeVarsLength = code.getFreeVars().length;
            if (closureLength != freeVarsLength) {
                throw raiseNode.raise(inliningTarget, PythonBuiltinClassType.ValueError, ErrorMessages.REQUIRES_CODE_OBJ, self.getName(), closureLength, freeVarsLength);
            }
            self.setCode(code);
            return PNone.NONE;
        }
    }

    @Builtin(name = J___DOC__, minNumOfPositionalArgs = 1, maxNumOfPositionalArgs = 2, isGetter = true, isSetter = true, allowsDelete = true)
    @GenerateNodeFactory
    abstract static class DocNode extends PythonBinaryBuiltinNode {
        @Specialization(guards = "isNoValue(none)")
        Object get(PFunction self, @SuppressWarnings("unused") Object none) {
            return self.getDoc();
        }

        @Specialization(guards = {"!isNoValue(value)", "!isDeleteMarker(value)"})
        Object set(PFunction self, Object value) {
            self.setDoc(value);
            return PNone.NONE;
        }

        @Specialization
        Object delete(PFunction self, @SuppressWarnings("unused") DescriptorDeleteMarker marker) {
            self.setDoc(PNone.NONE);
            return PNone.NONE;
        }
    }

    @Builtin(name = J___TYPE_PARAMS__, minNumOfPositionalArgs = 1, maxNumOfPositionalArgs = 2, isGetter = true, isSetter = true)
    @GenerateNodeFactory
    public abstract static class GetTypeParamsNode extends PythonBinaryBuiltinNode {
        @Specialization(guards = "isNoValue(value)")
        static Object get(PFunction self, @SuppressWarnings("unused") PNone value,
                        @Bind PythonLanguage language,
                        @Cached ReadAttributeFromObjectNode readObject) {
            Object typeParams = readObject.execute(self, T___TYPE_PARAMS__);
            if (typeParams == PNone.NO_VALUE) {
                return PFactory.createEmptyTuple(language);
            }
            return typeParams;
        }

        @Specialization
        static Object set(PFunction self, PTuple typeParams,
                        @Cached WriteAttributeToObjectNode writeObject) {
            writeObject.execute(self, T___TYPE_PARAMS__, typeParams);
            return PNone.NONE;
        }

        @Fallback
        @SuppressWarnings("unused")
        static Object error(Object self, Object value,
                        @Bind Node inliningTarget) {
            throw PRaiseNode.raiseStatic(inliningTarget, TypeError, ErrorMessages.MUST_BE_SET_TO_S_NOT_P, T___TYPE_PARAMS__, "tuple", value);
        }
    }
}
