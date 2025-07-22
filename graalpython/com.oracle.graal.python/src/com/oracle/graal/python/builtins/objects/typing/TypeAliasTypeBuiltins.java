/*
 * Copyright (c) 2024, 2025, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.builtins.objects.typing;

import static com.oracle.graal.python.nodes.BuiltinNames.J_TYPE_ALIAS_TYPE;
import static com.oracle.graal.python.nodes.ErrorMessages.ONLY_GENERIC_TYPE_ALIASES_ARE_SUBSCRIPTABLE;
import static com.oracle.graal.python.nodes.ErrorMessages.TYPE_PARAMS_MUST_BE_A_TUPLE;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.J___MODULE__;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.J___NAME__;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.J___PARAMETERS__;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.J___TYPE_PARAMS__;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.T___NAME__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___REDUCE__;

import java.util.List;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.annotations.ArgumentClinic;
import com.oracle.graal.python.annotations.ArgumentClinic.ClinicConversion;
import com.oracle.graal.python.annotations.Slot;
import com.oracle.graal.python.annotations.Slot.SlotKind;
import com.oracle.graal.python.annotations.Slot.SlotSignature;
import com.oracle.graal.python.builtins.Builtin;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.modules.TypingModuleBuiltins.CallerNode;
import com.oracle.graal.python.builtins.modules.TypingModuleBuiltins.UnpackTypeVarTuplesNode;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.function.PFunction;
import com.oracle.graal.python.builtins.objects.tuple.PTuple;
import com.oracle.graal.python.builtins.objects.type.TpSlots;
import com.oracle.graal.python.builtins.objects.type.TypeNodes;
import com.oracle.graal.python.builtins.objects.type.slots.TpSlotBinaryFunc.MpSubscriptBuiltinNode;
import com.oracle.graal.python.builtins.objects.type.slots.TpSlotBinaryOp.BinaryOpBuiltinNode;
import com.oracle.graal.python.builtins.objects.types.GenericTypeNodes.UnionTypeOrNode;
import com.oracle.graal.python.builtins.objects.typing.TypeAliasTypeBuiltinsClinicProviders.TypeAliasTypeNodeClinicProviderGen;
import com.oracle.graal.python.lib.PyObjectGetItem;
import com.oracle.graal.python.nodes.PGuards;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.call.CallNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.builtins.PythonClinicBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonUnaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.clinic.ArgumentClinicProvider;
import com.oracle.graal.python.runtime.object.PFactory;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.GenerateCached;
import com.oracle.truffle.api.dsl.GenerateInline;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.strings.TruffleString;

@CoreFunctions(extendClasses = PythonBuiltinClassType.PTypeAliasType)
public final class TypeAliasTypeBuiltins extends PythonBuiltins {

    public static final TpSlots SLOTS = TypeAliasTypeBuiltinsSlotsGen.SLOTS;

    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return TypeAliasTypeBuiltinsFactory.getFactories();
    }

    @Slot(value = SlotKind.tp_new, isComplex = true)
    @SlotSignature(name = J_TYPE_ALIAS_TYPE, minNumOfPositionalArgs = 3, parameterNames = {"$cls", "name", "value"}, keywordOnlyNames = {
                    "type_params"}, needsFrame = true, alwaysNeedsCallerFrame = true)
    @ArgumentClinic(name = "name", conversion = ClinicConversion.TString)
    @GenerateNodeFactory
    abstract static class TypeAliasTypeNode extends PythonClinicBuiltinNode {

        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return TypeAliasTypeNodeClinicProviderGen.INSTANCE;
        }

        @Specialization
        static PTypeAliasType newTypeAliasType(VirtualFrame frame, Object cls, TruffleString name, Object value, Object typeParams,
                        @Bind Node inliningTarget,
                        @Bind PythonLanguage language,
                        @Cached CheckTypeParamsNode checkNode,
                        @Cached CallerNode callerNode,
                        @Cached TypeNodes.GetInstanceShape getInstanceShape) {
            PTuple typeParamsTuple = checkNode.execute(inliningTarget, typeParams);
            Object module = callerNode.execute(frame, inliningTarget);
            return PFactory.createTypeAliasType(language, cls, getInstanceShape.execute(cls), name, typeParamsTuple, null, value, module);
        }

        @GenerateInline
        @GenerateCached(false)
        @ImportStatic(PGuards.class)
        abstract static class CheckTypeParamsNode extends Node {
            abstract PTuple execute(Node inliningTarget, Object o);

            @Specialization(guards = "isNoValue(o)")
            static PTuple doDefault(@SuppressWarnings("unused") Object o) {
                return null;
            }

            @Specialization
            static PTuple doTuple(PTuple o) {
                return o.getSequenceStorage().length() == 0 ? null : o;
            }

            @Fallback
            static PTuple doError(@SuppressWarnings("unused") Object o,
                            @Bind Node inliningTarget) {
                throw PRaiseNode.raiseStatic(inliningTarget, PythonBuiltinClassType.TypeError, TYPE_PARAMS_MUST_BE_A_TUPLE);
            }
        }
    }

    @Builtin(name = J___NAME__, minNumOfPositionalArgs = 1, isGetter = true)
    @GenerateNodeFactory
    public abstract static class GetNameNode extends PythonUnaryBuiltinNode {
        @Specialization
        static TruffleString doName(PTypeAliasType self) {
            return self.name;
        }
    }

    @Builtin(name = J___TYPE_PARAMS__, minNumOfPositionalArgs = 1, isGetter = true)
    @GenerateNodeFactory
    public abstract static class GetTypeParamsNode extends PythonUnaryBuiltinNode {
        @Specialization(guards = "self.typeParams != null")
        static PTuple doTypeParams(PTypeAliasType self) {
            return self.typeParams;
        }

        @Specialization(guards = "self.typeParams == null")
        static PTuple doEmpty(@SuppressWarnings("unused") PTypeAliasType self,
                        @Bind PythonLanguage language) {
            return PFactory.createEmptyTuple(language);
        }
    }

    @Builtin(name = "__value__", minNumOfPositionalArgs = 1, isGetter = true)
    @GenerateNodeFactory
    public abstract static class GetValueNode extends PythonUnaryBuiltinNode {
        @Specialization(guards = "self.value != null")
        static Object doEvaluated(PTypeAliasType self) {
            return self.value;
        }

        @Specialization(guards = "self.value == null")
        static Object doEvaluate(VirtualFrame frame, PTypeAliasType self,
                        @Cached CallNode callNode) {
            assert self.computeValue != null;
            self.value = callNode.execute(frame, self.computeValue);
            self.computeValue = null;
            return self.value;
        }
    }

    @Builtin(name = J___MODULE__, minNumOfPositionalArgs = 1, isGetter = true)
    @GenerateNodeFactory
    public abstract static class GetModuleNode extends PythonUnaryBuiltinNode {
        @Specialization(guards = "self.module != null")
        static Object doModule(PTypeAliasType self) {
            return self.module;
        }

        @Specialization(guards = {"self.module == null", "self.computeValue != null"})
        static Object doComputed(VirtualFrame frame, PTypeAliasType self,
                        @Bind Node inliningTarget,
                        @Cached PyObjectGetItem getItemNode) {
            if (self.computeValue instanceof PFunction fun && fun.getGlobals() != null) {
                return getItemNode.execute(frame, inliningTarget, fun.getGlobals(), T___NAME__);
            }
            return PNone.NONE;
        }

        @Specialization(guards = {"self.module == null", "self.computeValue == null"})
        static Object doNone(@SuppressWarnings("unused") PTypeAliasType self) {
            return PNone.NONE;
        }
    }

    @Builtin(name = J___PARAMETERS__, minNumOfPositionalArgs = 1, isGetter = true)
    @GenerateNodeFactory
    public abstract static class GetParametersNode extends PythonUnaryBuiltinNode {
        @Specialization(guards = "self.typeParams != null")
        static PTuple doTypeParams(VirtualFrame frame, PTypeAliasType self,
                        @Bind Node inliningTarget,
                        @Cached UnpackTypeVarTuplesNode unpackTypeVarTuplesNode) {
            return unpackTypeVarTuplesNode.execute(frame, inliningTarget, self.typeParams);
        }

        @Specialization(guards = "self.typeParams == null")
        static PTuple doEmpty(@SuppressWarnings("unused") PTypeAliasType self,
                        @Bind PythonLanguage language) {
            return PFactory.createEmptyTuple(language);
        }
    }

    @Slot(value = SlotKind.tp_repr, isComplex = true)
    @GenerateNodeFactory
    abstract static class ReprNode extends PythonUnaryBuiltinNode {

        @Specialization
        static TruffleString repr(PTypeAliasType self) {
            return self.name;
        }
    }

    @Builtin(name = J___REDUCE__, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class ReduceNode extends PythonUnaryBuiltinNode {
        @Specialization
        static Object reduce(PTypeAliasType self) {
            return self.name;
        }
    }

    @Slot(value = SlotKind.nb_or, isComplex = true)
    @GenerateNodeFactory
    abstract static class OrNode extends BinaryOpBuiltinNode {
        @Specialization
        static Object union(Object self, Object other,
                        @Cached UnionTypeOrNode unionTypeOrNode) {
            return unionTypeOrNode.execute(self, other);
        }
    }

    @Slot(value = SlotKind.mp_subscript, isComplex = true)
    @GenerateNodeFactory
    public abstract static class GetItemNode extends MpSubscriptBuiltinNode {
        @Specialization(guards = "self.typeParams != null")
        static Object doGenericAlias(PTypeAliasType self, Object args,
                        @Bind PythonLanguage language) {
            return PFactory.createGenericAlias(language, self, args);
        }

        @Specialization(guards = "self.typeParams == null")
        static Object doError(@SuppressWarnings("unused") PTypeAliasType self, @SuppressWarnings("unused") Object args,
                        @Bind Node inliningTarget) {
            throw PRaiseNode.raiseStatic(inliningTarget, PythonBuiltinClassType.TypeError, ONLY_GENERIC_TYPE_ALIASES_ARE_SUBSCRIPTABLE);
        }
    }
}
