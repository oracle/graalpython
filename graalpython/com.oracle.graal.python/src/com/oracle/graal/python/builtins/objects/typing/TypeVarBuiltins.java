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

import static com.oracle.graal.python.nodes.BuiltinNames.J_TYPE_VAR;
import static com.oracle.graal.python.nodes.ErrorMessages.A_SINGLE_CONSTRAINT_IS_NOT_ALLOWED;
import static com.oracle.graal.python.nodes.ErrorMessages.BIVARIANT_TYPES_ARE_NOT_SUPPORTED;
import static com.oracle.graal.python.nodes.ErrorMessages.CANNOT_SUBCLASS_AN_INSTANCE_OF_TYPEVAR;
import static com.oracle.graal.python.nodes.ErrorMessages.CONSTRAINTS_CANNOT_BE_COMBINED_WITH_BOUND;
import static com.oracle.graal.python.nodes.ErrorMessages.VARIANCE_CANNOT_BE_SPECIFIED_WITH_INFER_VARIANCE;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.J___NAME__;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.T___MODULE__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___MRO_ENTRIES__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___REDUCE__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___TYPING_SUBST__;
import static com.oracle.graal.python.util.PythonUtils.tsLiteral;

import java.util.List;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.annotations.ArgumentClinic;
import com.oracle.graal.python.annotations.ArgumentClinic.ClinicConversion;
import com.oracle.graal.python.annotations.Slot;
import com.oracle.graal.python.annotations.Slot.SlotKind;
import com.oracle.graal.python.annotations.Slot.SlotSignature;
import com.oracle.graal.python.annotations.Builtin;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.modules.TypingModuleBuiltins.CallTypingFuncObjectNode;
import com.oracle.graal.python.builtins.modules.TypingModuleBuiltins.CallerNode;
import com.oracle.graal.python.builtins.modules.TypingModuleBuiltins.CheckBoundNode;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.str.StringUtils.SimpleTruffleStringFormatNode;
import com.oracle.graal.python.builtins.objects.type.TpSlots;
import com.oracle.graal.python.builtins.objects.type.TypeNodes;
import com.oracle.graal.python.builtins.objects.type.slots.TpSlotBinaryOp.BinaryOpBuiltinNode;
import com.oracle.graal.python.builtins.objects.typing.TypeVarBuiltinsClinicProviders.TypeVarNodeClinicProviderGen;
import com.oracle.graal.python.lib.PyObjectSetAttr;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.call.CallNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.builtins.PythonBinaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonClinicBuiltinNode;
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

@CoreFunctions(extendClasses = PythonBuiltinClassType.PTypeVar)
public final class TypeVarBuiltins extends PythonBuiltins {

    public static final TpSlots SLOTS = TypeVarBuiltinsSlotsGen.SLOTS;

    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return TypeVarBuiltinsFactory.getFactories();
    }

    @Slot(value = SlotKind.tp_new, isComplex = true)
    @SlotSignature(name = J_TYPE_VAR, minNumOfPositionalArgs = 2, takesVarArgs = true, parameterNames = {"$cls", "name"}, keywordOnlyNames = {"bound", "covariant",
                    "contravariant", "infer_variance"}, needsFrame = true, alwaysNeedsCallerFrame = true)
    @ArgumentClinic(name = "name", conversion = ClinicConversion.TString)
    @ArgumentClinic(name = "covariant", conversion = ClinicConversion.Boolean, defaultValue = "false")
    @ArgumentClinic(name = "contravariant", conversion = ClinicConversion.Boolean, defaultValue = "false")
    @ArgumentClinic(name = "infer_variance", conversion = ClinicConversion.Boolean, defaultValue = "false")
    @GenerateNodeFactory
    abstract static class TypeVarNode extends PythonClinicBuiltinNode {

        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return TypeVarNodeClinicProviderGen.INSTANCE;
        }

        @Specialization
        static PTypeVar newTypeVar(VirtualFrame frame, Object cls, TruffleString name, Object[] constraints, Object bound, boolean covariant, boolean contravariant, boolean inferVariance,
                        @Bind Node inliningTarget,
                        @Bind PythonLanguage language,
                        @Cached CheckBoundNode checkBoundNode,
                        @Cached CallerNode callerNode,
                        @Cached TypeNodes.GetInstanceShape getInstanceShape,
                        @Cached PRaiseNode raiseNode,
                        @Cached PyObjectSetAttr setAttrNode) {
            if (covariant && contravariant) {
                throw raiseNode.raise(inliningTarget, PythonBuiltinClassType.ValueError, BIVARIANT_TYPES_ARE_NOT_SUPPORTED);
            }

            if (inferVariance && (covariant || contravariant)) {
                throw raiseNode.raise(inliningTarget, PythonBuiltinClassType.ValueError, VARIANCE_CANNOT_BE_SPECIFIED_WITH_INFER_VARIANCE);
            }

            Object boundChecked = checkBoundNode.execute(frame, inliningTarget, bound);
            Object constraintsTuple;

            if (constraints.length == 1) {
                throw raiseNode.raise(inliningTarget, PythonBuiltinClassType.TypeError, A_SINGLE_CONSTRAINT_IS_NOT_ALLOWED);
            } else if (constraints.length == 0) {
                constraintsTuple = PFactory.createEmptyTuple(language);
            } else if (boundChecked != PNone.NONE) {
                throw raiseNode.raise(inliningTarget, PythonBuiltinClassType.TypeError, CONSTRAINTS_CANNOT_BE_COMBINED_WITH_BOUND);
            } else {
                constraintsTuple = PFactory.createTuple(language, constraints);
            }
            Object module = callerNode.execute(frame, inliningTarget);

            PTypeVar result = PFactory.createTypeVar(language, cls, getInstanceShape.execute(cls), name, boundChecked, null, constraintsTuple, null, covariant, contravariant, inferVariance);
            setAttrNode.execute(frame, inliningTarget, result, T___MODULE__, module);
            return result;
        }
    }

    @Builtin(name = J___NAME__, minNumOfPositionalArgs = 1, isGetter = true)
    @GenerateNodeFactory
    public abstract static class GetNameNode extends PythonUnaryBuiltinNode {
        @Specialization
        static TruffleString doName(PTypeVar self) {
            return self.name;
        }
    }

    @Builtin(name = "__covariant__", minNumOfPositionalArgs = 1, isGetter = true)
    @GenerateNodeFactory
    public abstract static class GetCovariantNode extends PythonUnaryBuiltinNode {
        @Specialization
        static boolean doCovariant(PTypeVar self) {
            return self.covariant;
        }
    }

    @Builtin(name = "__contravariant__", minNumOfPositionalArgs = 1, isGetter = true)
    @GenerateNodeFactory
    public abstract static class GetContravariantNode extends PythonUnaryBuiltinNode {
        @Specialization
        static boolean doContravariant(PTypeVar self) {
            return self.contravariant;
        }
    }

    @Builtin(name = "__infer_variance__", minNumOfPositionalArgs = 1, isGetter = true)
    @GenerateNodeFactory
    public abstract static class GetInferVarianceNode extends PythonUnaryBuiltinNode {
        @Specialization
        static boolean doInferVariance(PTypeVar self) {
            return self.inferVariance;
        }
    }

    @Builtin(name = "__bound__", minNumOfPositionalArgs = 1, isGetter = true)
    @GenerateNodeFactory
    public abstract static class GetBoundNode extends PythonUnaryBuiltinNode {
        @Specialization(guards = "self.bound != null")
        static Object doEvaluated(PTypeVar self) {
            return self.bound;
        }

        @Specialization(guards = "self.bound == null")
        static Object doEvaluate(VirtualFrame frame, PTypeVar self,
                        @Cached CallNode callNode) {
            assert self.evaluateBound != null;
            self.bound = callNode.execute(frame, self.evaluateBound);
            self.evaluateBound = null;
            return self.bound;
        }
    }

    @Builtin(name = "__constraints__", minNumOfPositionalArgs = 1, isGetter = true)
    @GenerateNodeFactory
    public abstract static class GetConstraintsNode extends PythonUnaryBuiltinNode {
        @Specialization(guards = "self.constraints != null")
        static Object doEvaluated(PTypeVar self) {
            return self.constraints;
        }

        @Specialization(guards = "self.constraints == null")
        static Object doEvaluate(VirtualFrame frame, PTypeVar self,
                        @Cached CallNode callNode) {
            assert self.evaluateConstraints != null;
            self.constraints = callNode.execute(frame, self.evaluateConstraints);
            self.evaluateConstraints = null;
            return self.constraints;
        }
    }

    @Slot(value = SlotKind.tp_repr, isComplex = true)
    @GenerateNodeFactory
    abstract static class ReprNode extends PythonUnaryBuiltinNode {

        @Specialization(guards = "self.inferVariance")
        static TruffleString reprInferVariance(PTypeVar self) {
            return self.name;
        }

        @Specialization(guards = "!self.inferVariance")
        static TruffleString repr(PTypeVar self,
                        @Cached SimpleTruffleStringFormatNode simpleTruffleStringFormatNode) {
            char variance = self.covariant ? '+' : self.contravariant ? '-' : '~';
            return simpleTruffleStringFormatNode.format("%c%s", variance, self.name);
        }
    }

    @Builtin(name = J___TYPING_SUBST__, minNumOfPositionalArgs = 2, parameterNames = {"$self", "arg"})
    @GenerateNodeFactory
    abstract static class TypingSubstNode extends PythonBinaryBuiltinNode {
        static final TruffleString T_TYPEVAR_SUBST = tsLiteral("_typevar_subst");

        @Specialization
        static Object doTypingSubst(VirtualFrame frame, PTypeVar self, Object arg,
                        @Bind Node inliningTarget,
                        @Cached CallTypingFuncObjectNode callTypingFuncObjectNode) {
            return callTypingFuncObjectNode.execute(frame, inliningTarget, T_TYPEVAR_SUBST, self, arg);
        }
    }

    @Builtin(name = J___MRO_ENTRIES__, minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    abstract static class MroEntriesNode extends PythonBinaryBuiltinNode {
        @Specialization
        static Object mro(@SuppressWarnings("unused") PTypeVar self, @SuppressWarnings("unused") Object bases,
                        @Bind Node inliningTarget) {
            throw PRaiseNode.raiseStatic(inliningTarget, PythonBuiltinClassType.TypeError, CANNOT_SUBCLASS_AN_INSTANCE_OF_TYPEVAR);
        }
    }

    @Builtin(name = J___REDUCE__, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class ReduceNode extends PythonUnaryBuiltinNode {
        @Specialization
        static Object reduce(PTypeVar self) {
            return self.name;
        }
    }

    @Slot(value = SlotKind.nb_or, isComplex = true)
    @GenerateNodeFactory
    abstract static class OrNode extends BinaryOpBuiltinNode {
        static final TruffleString T_MAKE_UNION = tsLiteral("_make_union");

        @Specialization
        static Object union(VirtualFrame frame, Object self, Object other,
                        @Bind Node inliningTarget,
                        @Cached CallTypingFuncObjectNode callTypingFuncObjectNode) {
            return callTypingFuncObjectNode.execute(frame, inliningTarget, T_MAKE_UNION, self, other);
        }
    }
}
