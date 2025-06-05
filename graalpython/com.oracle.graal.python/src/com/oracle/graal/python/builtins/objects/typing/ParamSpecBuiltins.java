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

import static com.oracle.graal.python.nodes.BuiltinNames.J_PARAM_SPEC;
import static com.oracle.graal.python.nodes.ErrorMessages.BIVARIANT_TYPES_ARE_NOT_SUPPORTED;
import static com.oracle.graal.python.nodes.ErrorMessages.CANNOT_SUBCLASS_AN_INSTANCE_OF_PARAMSPEC;
import static com.oracle.graal.python.nodes.ErrorMessages.VARIANCE_CANNOT_BE_SPECIFIED_WITH_INFER_VARIANCE;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.J___NAME__;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.T___MODULE__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___MRO_ENTRIES__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___REDUCE__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___TYPING_PREPARE_SUBST__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___TYPING_SUBST__;
import static com.oracle.graal.python.util.PythonUtils.tsLiteral;

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
import com.oracle.graal.python.builtins.modules.TypingModuleBuiltins.CallTypingFuncObjectNode;
import com.oracle.graal.python.builtins.modules.TypingModuleBuiltins.CallerNode;
import com.oracle.graal.python.builtins.modules.TypingModuleBuiltins.CheckBoundNode;
import com.oracle.graal.python.builtins.objects.str.StringUtils.SimpleTruffleStringFormatNode;
import com.oracle.graal.python.builtins.objects.type.TpSlots;
import com.oracle.graal.python.builtins.objects.type.TypeNodes;
import com.oracle.graal.python.builtins.objects.type.slots.TpSlotBinaryOp.BinaryOpBuiltinNode;
import com.oracle.graal.python.builtins.objects.typing.ParamSpecBuiltinsClinicProviders.ParamSpecNodeClinicProviderGen;
import com.oracle.graal.python.lib.PyObjectSetAttr;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.builtins.PythonBinaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonClinicBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonTernaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonUnaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.clinic.ArgumentClinicProvider;
import com.oracle.graal.python.runtime.object.PFactory;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.strings.TruffleString;

@CoreFunctions(extendClasses = PythonBuiltinClassType.PParamSpec)
public final class ParamSpecBuiltins extends PythonBuiltins {
    public static final TpSlots SLOTS = ParamSpecBuiltinsSlotsGen.SLOTS;

    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return ParamSpecBuiltinsFactory.getFactories();
    }

    @Slot(value = SlotKind.tp_new, isComplex = true)
    @SlotSignature(name = J_PARAM_SPEC, minNumOfPositionalArgs = 2, parameterNames = {"$cls", "name"}, keywordOnlyNames = {"bound", "covariant",
                    "contravariant", "infer_variance"}, needsFrame = true, alwaysNeedsCallerFrame = true)
    @ArgumentClinic(name = "name", conversion = ClinicConversion.TString)
    @ArgumentClinic(name = "covariant", conversion = ClinicConversion.Boolean, defaultValue = "false")
    @ArgumentClinic(name = "contravariant", conversion = ClinicConversion.Boolean, defaultValue = "false")
    @ArgumentClinic(name = "infer_variance", conversion = ClinicConversion.Boolean, defaultValue = "false")
    @GenerateNodeFactory
    abstract static class ParamSpecNode extends PythonClinicBuiltinNode {

        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return ParamSpecNodeClinicProviderGen.INSTANCE;
        }

        @Specialization
        static PParamSpec newParamSpec(VirtualFrame frame, Object cls, TruffleString name, Object bound, boolean covariant, boolean contravariant, boolean inferVariance,
                        @Bind("this") Node inliningTarget,
                        @Bind PythonLanguage language,
                        @Cached CheckBoundNode checkBoundNode,
                        @Cached CallerNode callerNode,
                        @Cached PRaiseNode raiseNode,
                        @Cached TypeNodes.GetInstanceShape getInstanceShape,
                        @Cached PyObjectSetAttr setAttrNode) {
            if (covariant && contravariant) {
                throw raiseNode.raise(inliningTarget, PythonBuiltinClassType.ValueError, BIVARIANT_TYPES_ARE_NOT_SUPPORTED);
            }

            if (inferVariance && (covariant || contravariant)) {
                throw raiseNode.raise(inliningTarget, PythonBuiltinClassType.ValueError, VARIANCE_CANNOT_BE_SPECIFIED_WITH_INFER_VARIANCE);
            }

            Object boundChecked = checkBoundNode.execute(frame, inliningTarget, bound);

            Object module = callerNode.execute(frame, inliningTarget);

            PParamSpec result = PFactory.createParamSpec(language, cls, getInstanceShape.execute(cls), name, boundChecked, covariant, contravariant, inferVariance);
            setAttrNode.execute(frame, inliningTarget, result, T___MODULE__, module);
            return result;
        }
    }

    @Builtin(name = J___NAME__, minNumOfPositionalArgs = 1, isGetter = true)
    @GenerateNodeFactory
    public abstract static class GetNameNode extends PythonUnaryBuiltinNode {
        @Specialization
        static TruffleString doName(PParamSpec self) {
            return self.name;
        }
    }

    @Builtin(name = "__covariant__", minNumOfPositionalArgs = 1, isGetter = true)
    @GenerateNodeFactory
    public abstract static class GetCovariantNode extends PythonUnaryBuiltinNode {
        @Specialization
        static boolean doCovariant(PParamSpec self) {
            return self.covariant;
        }
    }

    @Builtin(name = "__contravariant__", minNumOfPositionalArgs = 1, isGetter = true)
    @GenerateNodeFactory
    public abstract static class GetContravariantNode extends PythonUnaryBuiltinNode {
        @Specialization
        static boolean doContravariant(PParamSpec self) {
            return self.contravariant;
        }
    }

    @Builtin(name = "__infer_variance__", minNumOfPositionalArgs = 1, isGetter = true)
    @GenerateNodeFactory
    public abstract static class GetInferVarianceNode extends PythonUnaryBuiltinNode {
        @Specialization
        static boolean doInferVariance(PParamSpec self) {
            return self.inferVariance;
        }
    }

    @Builtin(name = "__bound__", minNumOfPositionalArgs = 1, isGetter = true)
    @GenerateNodeFactory
    public abstract static class GetBoundNode extends PythonUnaryBuiltinNode {
        @Specialization
        static Object doBound(PParamSpec self) {
            return self.bound;
        }
    }

    @Builtin(name = "args", minNumOfPositionalArgs = 1, isGetter = true)
    @GenerateNodeFactory
    public abstract static class GetArgsNode extends PythonUnaryBuiltinNode {
        @Specialization
        static PParamSpecArgs doArgs(PParamSpec self,
                        @Bind PythonLanguage language) {
            return PFactory.createParamSpecArgs(language, self);
        }
    }

    @Builtin(name = "kwargs", minNumOfPositionalArgs = 1, isGetter = true)
    @GenerateNodeFactory
    public abstract static class GetKwargsNode extends PythonUnaryBuiltinNode {
        @Specialization
        static PParamSpecKwargs doKwargs(PParamSpec self,
                        @Bind PythonLanguage language) {
            return PFactory.createParamSpecKwargs(language, self);
        }
    }

    @Slot(value = SlotKind.tp_repr, isComplex = true)
    @GenerateNodeFactory
    abstract static class ReprNode extends PythonUnaryBuiltinNode {

        @Specialization(guards = "self.inferVariance")
        static TruffleString reprInferVariance(PParamSpec self) {
            return self.name;
        }

        @Specialization(guards = "!self.inferVariance")
        static TruffleString repr(PParamSpec self,
                        @Cached SimpleTruffleStringFormatNode simpleTruffleStringFormatNode) {
            char variance = self.covariant ? '+' : self.contravariant ? '-' : '~';
            return simpleTruffleStringFormatNode.format("%c%s", variance, self.name);
        }
    }

    @Builtin(name = J___TYPING_SUBST__, minNumOfPositionalArgs = 2, parameterNames = {"$self", "arg"})
    @GenerateNodeFactory
    abstract static class TypingSubstNode extends PythonBinaryBuiltinNode {
        static final TruffleString T_PARAMSPEC_SUBST = tsLiteral("_paramspec_subst");

        @Specialization
        static Object doTypingSubst(VirtualFrame frame, PParamSpec self, Object arg,
                        @Bind("this") Node inliningTarget,
                        @Cached CallTypingFuncObjectNode callTypingFuncObjectNode) {
            return callTypingFuncObjectNode.execute(frame, inliningTarget, T_PARAMSPEC_SUBST, self, arg);
        }
    }

    @Builtin(name = J___TYPING_PREPARE_SUBST__, minNumOfPositionalArgs = 3, parameterNames = {"$self", "alias", "args"})
    @GenerateNodeFactory
    abstract static class TypingPrepareSubstNode extends PythonTernaryBuiltinNode {
        private static final TruffleString T_PARAMSPEC_PREPARE_SUBST = tsLiteral("_paramspec_prepare_subst");

        @Specialization
        static Object doTypingPrepareSubst(VirtualFrame frame, PParamSpec self, Object alias, Object args,
                        @Bind("this") Node inliningTarget,
                        @Cached CallTypingFuncObjectNode callTypingFuncObjectNode) {
            return callTypingFuncObjectNode.execute(frame, inliningTarget, T_PARAMSPEC_PREPARE_SUBST, self, alias, args);
        }
    }

    @Builtin(name = J___MRO_ENTRIES__, minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    abstract static class MroEntriesNode extends PythonBinaryBuiltinNode {
        @Specialization
        static Object mro(@SuppressWarnings("unused") PParamSpec self, @SuppressWarnings("unused") Object bases,
                        @Bind("this") Node inliningTarget) {
            throw PRaiseNode.raiseStatic(inliningTarget, PythonBuiltinClassType.TypeError, CANNOT_SUBCLASS_AN_INSTANCE_OF_PARAMSPEC);
        }
    }

    @Builtin(name = J___REDUCE__, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class ReduceNode extends PythonUnaryBuiltinNode {
        @Specialization
        static Object reduce(PParamSpec self) {
            return self.name;
        }
    }

    @Slot(value = SlotKind.nb_or, isComplex = true)
    @GenerateNodeFactory
    abstract static class OrNode extends BinaryOpBuiltinNode {
        static final TruffleString T_MAKE_UNION = tsLiteral("_make_union");

        @Specialization
        static Object union(VirtualFrame frame, Object self, Object other,
                        @Bind("this") Node inliningTarget,
                        @Cached CallTypingFuncObjectNode callTypingFuncObjectNode) {
            return callTypingFuncObjectNode.execute(frame, inliningTarget, T_MAKE_UNION, self, other);
        }
    }
}
