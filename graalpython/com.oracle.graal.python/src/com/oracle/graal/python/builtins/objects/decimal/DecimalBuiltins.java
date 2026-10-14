/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.builtins.objects.decimal;

import static com.oracle.graal.python.builtins.PythonBuiltinClassType.DeprecationWarning;
import static com.oracle.graal.python.builtins.PythonBuiltinClassType.MemoryError;
import static com.oracle.graal.python.builtins.PythonBuiltinClassType.OverflowError;
import static com.oracle.graal.python.builtins.PythonBuiltinClassType.TypeError;
import static com.oracle.graal.python.builtins.PythonBuiltinClassType.ValueError;
import static com.oracle.graal.python.util.PythonUtils.TS_ENCODING;
import static com.oracle.graal.python.util.PythonUtils.tsLiteral;

import java.math.BigInteger;
import java.util.List;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.annotations.Builtin;
import com.oracle.graal.python.annotations.Slot;
import com.oracle.graal.python.annotations.Slot.SlotKind;
import com.oracle.graal.python.annotations.Slot.SlotSignature;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.modules.SysModuleBuiltins;
import com.oracle.graal.python.builtins.modules.WarningsModuleBuiltins.WarnNode;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.PNotImplemented;
import com.oracle.graal.python.builtins.objects.complex.ComplexBuiltins.ComplexValue;
import com.oracle.graal.python.builtins.objects.complex.ComplexBuiltins.ToComplexValueNode;
import com.oracle.graal.python.builtins.objects.decimal.DecimalNodes.AbsDecimalNode;
import com.oracle.graal.python.builtins.objects.decimal.DecimalNodes.AddDecimalNode;
import com.oracle.graal.python.builtins.objects.decimal.DecimalNodes.CreateDecimalNode;
import com.oracle.graal.python.builtins.objects.decimal.DecimalNodes.DecimalToStringNode;
import com.oracle.graal.python.builtins.objects.decimal.DecimalNodes.DivideDecimalNode;
import com.oracle.graal.python.builtins.objects.decimal.DecimalNodes.DivideIntDecimalNode;
import com.oracle.graal.python.builtins.objects.decimal.DecimalNodes.DivmodDecimalNode;
import com.oracle.graal.python.builtins.objects.decimal.DecimalNodes.GetContextNode;
import com.oracle.graal.python.builtins.objects.decimal.DecimalNodes.GetCurrentContextNode;
import com.oracle.graal.python.builtins.objects.decimal.DecimalNodes.MinusDecimalNode;
import com.oracle.graal.python.builtins.objects.decimal.DecimalNodes.MultiplyDecimalNode;
import com.oracle.graal.python.builtins.objects.decimal.DecimalNodes.PlusDecimalNode;
import com.oracle.graal.python.builtins.objects.decimal.DecimalNodes.QuantizeDecimalNode;
import com.oracle.graal.python.builtins.objects.decimal.DecimalNodes.RaiseSignalNode;
import com.oracle.graal.python.builtins.objects.decimal.DecimalNodes.RemainderDecimalNode;
import com.oracle.graal.python.builtins.objects.decimal.DecimalNodes.RequireDecimalNode;
import com.oracle.graal.python.builtins.objects.decimal.DecimalNodes.SubtractDecimalNode;
import com.oracle.graal.python.builtins.objects.decimal.DecimalNodes.ToDecimalNode;
import com.oracle.graal.python.builtins.objects.decimal.DecimalUtils.ConversionException;
import com.oracle.graal.python.builtins.objects.decimal.DecimalUtils.Value;
import com.oracle.graal.python.builtins.objects.decimal.PDecimal.Kind;
import com.oracle.graal.python.builtins.objects.function.PKeyword;
import com.oracle.graal.python.builtins.objects.object.ObjectBuiltins;
import com.oracle.graal.python.builtins.objects.type.TpSlots;
import com.oracle.graal.python.builtins.objects.type.TypeNodes;
import com.oracle.graal.python.builtins.objects.type.slots.TpSlotBinaryOp.BinaryOpBuiltinNode;
import com.oracle.graal.python.builtins.objects.type.slots.TpSlotHashFun.HashBuiltinNode;
import com.oracle.graal.python.builtins.objects.type.slots.TpSlotInquiry.NbBoolBuiltinNode;
import com.oracle.graal.python.builtins.objects.type.slots.TpSlotRichCompare.RichCmpBuiltinNode;
import com.oracle.graal.python.lib.PyComplexCheckNode;
import com.oracle.graal.python.lib.PyFloatAsDoubleNode;
import com.oracle.graal.python.lib.PyFloatCheckNode;
import com.oracle.graal.python.lib.PyLongAsLongNode;
import com.oracle.graal.python.lib.PyLongCheckNode;
import com.oracle.graal.python.lib.PyObjectGetAttr;
import com.oracle.graal.python.lib.PyObjectIsInstanceNode;
import com.oracle.graal.python.lib.PyUnicodeCheckNode;
import com.oracle.graal.python.lib.RichCmpOp;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.call.CallNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonBinaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonTernaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonUnaryBuiltinNode;
import com.oracle.graal.python.nodes.object.BuiltinClassProfiles.IsBuiltinClassExactProfile;
import com.oracle.graal.python.nodes.object.GetClassNode;
import com.oracle.graal.python.nodes.util.CastToJavaBigIntegerNode;
import com.oracle.graal.python.nodes.util.CastToJavaStringNode;
import com.oracle.graal.python.runtime.object.PFactory;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.profiles.InlinedBranchProfile;
import com.oracle.truffle.api.profiles.InlinedConditionProfile;
import com.oracle.truffle.api.strings.TruffleString;

@CoreFunctions(extendClasses = PythonBuiltinClassType.PDecimal)
public final class DecimalBuiltins extends PythonBuiltins {
    public static final TpSlots SLOTS = DecimalBuiltinsSlotsGen.SLOTS;
    private static final TruffleString T_NUMERATOR = tsLiteral("numerator");
    private static final TruffleString T_DENOMINATOR = tsLiteral("denominator");
    private static final TruffleString T_SNAN = tsLiteral("sNaN");
    private static final TruffleString T_NAN = tsLiteral("NaN");
    private static final TruffleString T_NEGATIVE_INFINITY = tsLiteral("-Infinity");
    private static final TruffleString T_POSITIVE_INFINITY = tsLiteral("+Infinity");
    private static final TruffleString T_NEGATIVE_ZERO = tsLiteral("-Zero");
    private static final TruffleString T_POSITIVE_ZERO = tsLiteral("+Zero");
    private static final TruffleString T_NEGATIVE_SUBNORMAL = tsLiteral("-Subnormal");
    private static final TruffleString T_POSITIVE_SUBNORMAL = tsLiteral("+Subnormal");
    private static final TruffleString T_NEGATIVE_NORMAL = tsLiteral("-Normal");
    private static final TruffleString T_POSITIVE_NORMAL = tsLiteral("+Normal");

    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return DecimalBuiltinsFactory.getFactories();
    }

    static PDecimal finishResult(VirtualFrame frame, Node inliningTarget, PDecimalContext context, DecimalArithmetic.Result result, RaiseSignalNode raiseSignal) {
        DecimalContextUtils.addStatus(frame, context, result.status(), raiseSignal);
        return DecimalNodes.create(PythonLanguage.get(inliningTarget), result.value());
    }

    @Builtin(name = "exp", minNumOfPositionalArgs = 1, numOfPositionalOnlyArgs = 1, parameterNames = {"self", "context"})
    @GenerateNodeFactory
    public abstract static class ExpNode extends PythonBuiltinNode {
        @Specialization
        static PDecimal exp(VirtualFrame frame, PDecimal self, Object context,
                        @Bind Node inliningTarget,
                        @Cached GetContextNode getContext,
                        @Cached RaiseSignalNode raiseSignal,
                        @Cached PRaiseNode raiseNode) {
            PDecimalContext actualContext = getContext.execute(inliningTarget, context);
            return doExp(frame, inliningTarget, self, actualContext, raiseSignal, raiseNode);
        }

        static PDecimal doExp(VirtualFrame frame, Node inliningTarget, PDecimal self, PDecimalContext context, RaiseSignalNode raiseSignal, PRaiseNode raiseNode) {
            DecimalArithmetic.Result result;
            try {
                result = DecimalArithmetic.exp(self, context);
            } catch (ArithmeticException e) {
                throw raiseNode.raise(inliningTarget, MemoryError);
            }
            return finishResult(frame, inliningTarget, context, result, raiseSignal);
        }
    }

    @Builtin(name = "ln", minNumOfPositionalArgs = 1, numOfPositionalOnlyArgs = 1, parameterNames = {"self", "context"})
    @GenerateNodeFactory
    public abstract static class LnNode extends PythonBuiltinNode {
        @Specialization
        static PDecimal ln(VirtualFrame frame, PDecimal self, Object context,
                        @Bind Node inliningTarget,
                        @Cached GetContextNode getContext,
                        @Cached RaiseSignalNode raiseSignal,
                        @Cached PRaiseNode raiseNode) {
            PDecimalContext actualContext = getContext.execute(inliningTarget, context);
            return doLn(frame, inliningTarget, self, actualContext, raiseSignal, raiseNode);
        }

        static PDecimal doLn(VirtualFrame frame, Node inliningTarget, PDecimal self, PDecimalContext context, RaiseSignalNode raiseSignal, PRaiseNode raiseNode) {
            DecimalArithmetic.Result result;
            try {
                result = DecimalArithmetic.ln(self, context);
            } catch (ArithmeticException e) {
                throw raiseNode.raise(inliningTarget, MemoryError);
            }
            return finishResult(frame, inliningTarget, context, result, raiseSignal);
        }
    }

    @Builtin(name = "log10", minNumOfPositionalArgs = 1, numOfPositionalOnlyArgs = 1, parameterNames = {"self", "context"})
    @GenerateNodeFactory
    public abstract static class Log10Node extends PythonBuiltinNode {
        @Specialization
        static PDecimal log10(VirtualFrame frame, PDecimal self, Object context,
                        @Bind Node inliningTarget,
                        @Cached GetContextNode getContext,
                        @Cached RaiseSignalNode raiseSignal,
                        @Cached PRaiseNode raiseNode) {
            PDecimalContext actualContext = getContext.execute(inliningTarget, context);
            return doLog10(frame, inliningTarget, self, actualContext, raiseSignal, raiseNode);
        }

        static PDecimal doLog10(VirtualFrame frame, Node inliningTarget, PDecimal self, PDecimalContext context, RaiseSignalNode raiseSignal, PRaiseNode raiseNode) {
            DecimalArithmetic.Result result;
            try {
                result = DecimalArithmetic.log10(self, context);
            } catch (ArithmeticException e) {
                throw raiseNode.raise(inliningTarget, MemoryError);
            }
            return finishResult(frame, inliningTarget, context, result, raiseSignal);
        }
    }

    @Builtin(name = "next_minus", minNumOfPositionalArgs = 1, numOfPositionalOnlyArgs = 1, parameterNames = {"self", "context"})
    @GenerateNodeFactory
    public abstract static class NextMinusNode extends PythonBuiltinNode {
        @Specialization
        static PDecimal nextMinus(VirtualFrame frame, PDecimal self, Object context,
                        @Bind Node inliningTarget,
                        @Cached GetContextNode getContext,
                        @Cached RaiseSignalNode raiseSignal,
                        @Cached PRaiseNode raiseNode) {
            PDecimalContext actualContext = getContext.execute(inliningTarget, context);
            return doNextMinus(frame, inliningTarget, self, actualContext, raiseSignal, raiseNode);
        }

        static PDecimal doNextMinus(VirtualFrame frame, Node inliningTarget, PDecimal self, PDecimalContext context, RaiseSignalNode raiseSignal, PRaiseNode raiseNode) {
            DecimalArithmetic.Result result;
            try {
                result = DecimalArithmetic.nextMinus(self, context);
            } catch (ArithmeticException e) {
                throw raiseNode.raise(inliningTarget, MemoryError);
            }
            return finishResult(frame, inliningTarget, context, result, raiseSignal);
        }
    }

    @Builtin(name = "next_plus", minNumOfPositionalArgs = 1, numOfPositionalOnlyArgs = 1, parameterNames = {"self", "context"})
    @GenerateNodeFactory
    public abstract static class NextPlusNode extends PythonBuiltinNode {
        @Specialization
        static PDecimal nextPlus(VirtualFrame frame, PDecimal self, Object context,
                        @Bind Node inliningTarget,
                        @Cached GetContextNode getContext,
                        @Cached RaiseSignalNode raiseSignal,
                        @Cached PRaiseNode raiseNode) {
            PDecimalContext actualContext = getContext.execute(inliningTarget, context);
            return doNextPlus(frame, inliningTarget, self, actualContext, raiseSignal, raiseNode);
        }

        static PDecimal doNextPlus(VirtualFrame frame, Node inliningTarget, PDecimal self, PDecimalContext context, RaiseSignalNode raiseSignal, PRaiseNode raiseNode) {
            DecimalArithmetic.Result result;
            try {
                result = DecimalArithmetic.nextPlus(self, context);
            } catch (ArithmeticException e) {
                throw raiseNode.raise(inliningTarget, MemoryError);
            }
            return finishResult(frame, inliningTarget, context, result, raiseSignal);
        }
    }

    @Builtin(name = "normalize", minNumOfPositionalArgs = 1, numOfPositionalOnlyArgs = 1, parameterNames = {"self", "context"})
    @GenerateNodeFactory
    public abstract static class NormalizeNode extends PythonBuiltinNode {
        @Specialization
        static PDecimal normalize(VirtualFrame frame, PDecimal self, Object context,
                        @Bind Node inliningTarget,
                        @Cached GetContextNode getContext,
                        @Cached RaiseSignalNode raiseSignal,
                        @Cached PRaiseNode raiseNode) {
            PDecimalContext actualContext = getContext.execute(inliningTarget, context);
            return doNormalize(frame, inliningTarget, self, actualContext, raiseSignal, raiseNode);
        }

        static PDecimal doNormalize(VirtualFrame frame, Node inliningTarget, PDecimal self, PDecimalContext context, RaiseSignalNode raiseSignal, PRaiseNode raiseNode) {
            DecimalArithmetic.Result result;
            try {
                result = DecimalArithmetic.normalize(self, context);
            } catch (ArithmeticException e) {
                throw raiseNode.raise(inliningTarget, MemoryError);
            }
            return finishResult(frame, inliningTarget, context, result, raiseSignal);
        }
    }

    @Builtin(name = "to_integral", minNumOfPositionalArgs = 1, numOfPositionalOnlyArgs = 1, parameterNames = {"self", "rounding", "context"})
    @GenerateNodeFactory
    public abstract static class ToIntegralNode extends PythonBuiltinNode {
        @Specialization
        static PDecimal toIntegralValue(VirtualFrame frame, PDecimal self, Object rounding, Object context,
                        @Bind Node inliningTarget,
                        @Cached GetContextNode getContext,
                        @Cached RaiseSignalNode raiseSignal,
                        @Cached PRaiseNode raiseNode) {
            PDecimalContext actualContext = getContext.execute(inliningTarget, context);
            DecimalRounding actualRounding = getRounding(rounding, inliningTarget, actualContext);
            return ToIntegralValueNode.doToIntegralValue(frame, inliningTarget, self, actualContext, actualRounding, raiseSignal, raiseNode);
        }
    }

    @Builtin(name = "to_integral_exact", minNumOfPositionalArgs = 1, numOfPositionalOnlyArgs = 1, parameterNames = {"self", "rounding", "context"})
    @GenerateNodeFactory
    public abstract static class ToIntegralExactNode extends PythonBuiltinNode {
        @Specialization
        static PDecimal toIntegralExact(VirtualFrame frame, PDecimal self, Object rounding, Object context,
                        @Bind Node inliningTarget,
                        @Cached GetContextNode getContext,
                        @Cached RaiseSignalNode raiseSignal,
                        @Cached PRaiseNode raiseNode) {
            PDecimalContext actualContext = getContext.execute(inliningTarget, context);
            DecimalRounding actualRounding = getRounding(rounding, inliningTarget, actualContext);
            return doToIntegralExact(frame, inliningTarget, self, actualContext, actualRounding, raiseSignal, raiseNode);
        }

        static PDecimal doToIntegralExact(VirtualFrame frame, Node inliningTarget, PDecimal self, PDecimalContext context, DecimalRounding rounding, RaiseSignalNode raiseSignal,
                        PRaiseNode raiseNode) {
            DecimalArithmetic.Result result;
            try {
                result = DecimalArithmetic.toIntegralExact(self, context, rounding);
            } catch (ArithmeticException e) {
                throw raiseNode.raise(inliningTarget, MemoryError);
            }
            return finishResult(frame, inliningTarget, context, result, raiseSignal);
        }
    }

    @Builtin(name = "to_integral_value", minNumOfPositionalArgs = 1, numOfPositionalOnlyArgs = 1, parameterNames = {"self", "rounding", "context"})
    @GenerateNodeFactory
    public abstract static class ToIntegralValueNode extends PythonBuiltinNode {
        @Specialization
        static PDecimal toIntegralValue(VirtualFrame frame, PDecimal self, Object rounding, Object context,
                        @Bind Node inliningTarget,
                        @Cached GetContextNode getContext,
                        @Cached RaiseSignalNode raiseSignal,
                        @Cached PRaiseNode raiseNode) {
            PDecimalContext actualContext = getContext.execute(inliningTarget, context);
            DecimalRounding actualRounding = getRounding(rounding, inliningTarget, actualContext);
            return doToIntegralValue(frame, inliningTarget, self, actualContext, actualRounding, raiseSignal, raiseNode);
        }

        static PDecimal doToIntegralValue(VirtualFrame frame, Node inliningTarget, PDecimal self, PDecimalContext context, DecimalRounding rounding, RaiseSignalNode raiseSignal,
                        PRaiseNode raiseNode) {
            DecimalArithmetic.Result result;
            try {
                result = DecimalArithmetic.toIntegralValue(self, context, rounding);
            } catch (ArithmeticException e) {
                throw raiseNode.raise(inliningTarget, MemoryError);
            }
            return finishResult(frame, inliningTarget, context, result, raiseSignal);
        }
    }

    @Builtin(name = "sqrt", minNumOfPositionalArgs = 1, numOfPositionalOnlyArgs = 1, parameterNames = {"self", "context"})
    @GenerateNodeFactory
    public abstract static class SqrtNode extends PythonBuiltinNode {
        @Specialization
        static PDecimal sqrt(VirtualFrame frame, PDecimal self, Object context,
                        @Bind Node inliningTarget,
                        @Cached GetContextNode getContext,
                        @Cached RaiseSignalNode raiseSignal,
                        @Cached PRaiseNode raiseNode) {
            PDecimalContext actualContext = getContext.execute(inliningTarget, context);
            return doSqrt(frame, inliningTarget, self, actualContext, raiseSignal, raiseNode);
        }

        static PDecimal doSqrt(VirtualFrame frame, Node inliningTarget, PDecimal self, PDecimalContext context, RaiseSignalNode raiseSignal, PRaiseNode raiseNode) {
            DecimalArithmetic.Result result;
            try {
                result = DecimalArithmetic.sqrt(self, context);
            } catch (ArithmeticException e) {
                throw raiseNode.raise(inliningTarget, MemoryError);
            }
            return finishResult(frame, inliningTarget, context, result, raiseSignal);
        }
    }

    @Builtin(name = "compare", minNumOfPositionalArgs = 2, numOfPositionalOnlyArgs = 1, parameterNames = {"self", "other", "context"})
    @GenerateNodeFactory
    public abstract static class CompareNode extends PythonBuiltinNode {
        @Specialization
        static PDecimal compare(VirtualFrame frame, PDecimal self, Object other, Object context,
                        @Bind Node inliningTarget,
                        @Cached GetContextNode getContext,
                        @Cached RaiseSignalNode raiseSignal,
                        @Cached PRaiseNode raiseNode,
                        @Cached RequireDecimalNode convert) {
            PDecimalContext actualContext = getContext.execute(inliningTarget, context);
            PDecimal operand = convert.execute(inliningTarget, other);
            return doCompare(frame, inliningTarget, self, operand, actualContext, raiseSignal, raiseNode);
        }

        static PDecimal doCompare(VirtualFrame frame, Node inliningTarget, PDecimal self, PDecimal operand, PDecimalContext context, RaiseSignalNode raiseSignal, PRaiseNode raiseNode) {
            DecimalArithmetic.Result result;
            try {
                result = DecimalArithmetic.compare(self, operand, context);
            } catch (ArithmeticException e) {
                throw raiseNode.raise(inliningTarget, MemoryError);
            }
            return finishResult(frame, inliningTarget, context, result, raiseSignal);
        }
    }

    @Builtin(name = "compare_signal", minNumOfPositionalArgs = 2, numOfPositionalOnlyArgs = 1, parameterNames = {"self", "other", "context"})
    @GenerateNodeFactory
    public abstract static class CompareSignalNode extends PythonBuiltinNode {
        @Specialization
        static PDecimal compareSignal(VirtualFrame frame, PDecimal self, Object other, Object context,
                        @Bind Node inliningTarget,
                        @Cached GetContextNode getContext,
                        @Cached RaiseSignalNode raiseSignal,
                        @Cached PRaiseNode raiseNode,
                        @Cached RequireDecimalNode convert) {
            PDecimalContext actualContext = getContext.execute(inliningTarget, context);
            PDecimal operand = convert.execute(inliningTarget, other);
            return doCompareSignal(frame, inliningTarget, self, operand, actualContext, raiseSignal, raiseNode);
        }

        static PDecimal doCompareSignal(VirtualFrame frame, Node inliningTarget, PDecimal self, PDecimal operand, PDecimalContext context, RaiseSignalNode raiseSignal, PRaiseNode raiseNode) {
            DecimalArithmetic.Result result;
            try {
                result = DecimalArithmetic.compareSignal(self, operand, context);
            } catch (ArithmeticException e) {
                throw raiseNode.raise(inliningTarget, MemoryError);
            }
            return finishResult(frame, inliningTarget, context, result, raiseSignal);
        }
    }

    @Builtin(name = "max", minNumOfPositionalArgs = 2, numOfPositionalOnlyArgs = 1, parameterNames = {"self", "other", "context"})
    @GenerateNodeFactory
    public abstract static class MaxNode extends PythonBuiltinNode {
        @Specialization
        static PDecimal max(VirtualFrame frame, PDecimal self, Object other, Object context,
                        @Bind Node inliningTarget,
                        @Cached GetContextNode getContext,
                        @Cached RaiseSignalNode raiseSignal,
                        @Cached PRaiseNode raiseNode,
                        @Cached RequireDecimalNode convert) {
            PDecimalContext actualContext = getContext.execute(inliningTarget, context);
            PDecimal operand = convert.execute(inliningTarget, other);
            return doMax(frame, inliningTarget, self, operand, actualContext, raiseSignal, raiseNode);
        }

        static PDecimal doMax(VirtualFrame frame, Node inliningTarget, PDecimal self, PDecimal operand, PDecimalContext context, RaiseSignalNode raiseSignal, PRaiseNode raiseNode) {
            DecimalArithmetic.Result result;
            try {
                result = DecimalArithmetic.max(self, operand, context);
            } catch (ArithmeticException e) {
                throw raiseNode.raise(inliningTarget, MemoryError);
            }
            return finishResult(frame, inliningTarget, context, result, raiseSignal);
        }
    }

    @Builtin(name = "max_mag", minNumOfPositionalArgs = 2, numOfPositionalOnlyArgs = 1, parameterNames = {"self", "other", "context"})
    @GenerateNodeFactory
    public abstract static class MaxMagNode extends PythonBuiltinNode {
        @Specialization
        static PDecimal maxMag(VirtualFrame frame, PDecimal self, Object other, Object context,
                        @Bind Node inliningTarget,
                        @Cached GetContextNode getContext,
                        @Cached RaiseSignalNode raiseSignal,
                        @Cached PRaiseNode raiseNode,
                        @Cached RequireDecimalNode convert) {
            PDecimalContext actualContext = getContext.execute(inliningTarget, context);
            PDecimal operand = convert.execute(inliningTarget, other);
            return doMaxMag(frame, inliningTarget, self, operand, actualContext, raiseSignal, raiseNode);
        }

        static PDecimal doMaxMag(VirtualFrame frame, Node inliningTarget, PDecimal self, PDecimal operand, PDecimalContext context, RaiseSignalNode raiseSignal, PRaiseNode raiseNode) {
            DecimalArithmetic.Result result;
            try {
                result = DecimalArithmetic.maxMag(self, operand, context);
            } catch (ArithmeticException e) {
                throw raiseNode.raise(inliningTarget, MemoryError);
            }
            return finishResult(frame, inliningTarget, context, result, raiseSignal);
        }
    }

    @Builtin(name = "min", minNumOfPositionalArgs = 2, numOfPositionalOnlyArgs = 1, parameterNames = {"self", "other", "context"})
    @GenerateNodeFactory
    public abstract static class MinNode extends PythonBuiltinNode {
        @Specialization
        static PDecimal min(VirtualFrame frame, PDecimal self, Object other, Object context,
                        @Bind Node inliningTarget,
                        @Cached GetContextNode getContext,
                        @Cached RaiseSignalNode raiseSignal,
                        @Cached PRaiseNode raiseNode,
                        @Cached RequireDecimalNode convert) {
            PDecimalContext actualContext = getContext.execute(inliningTarget, context);
            PDecimal operand = convert.execute(inliningTarget, other);
            return doMin(frame, inliningTarget, self, operand, actualContext, raiseSignal, raiseNode);
        }

        static PDecimal doMin(VirtualFrame frame, Node inliningTarget, PDecimal self, PDecimal operand, PDecimalContext context, RaiseSignalNode raiseSignal, PRaiseNode raiseNode) {
            DecimalArithmetic.Result result;
            try {
                result = DecimalArithmetic.min(self, operand, context);
            } catch (ArithmeticException e) {
                throw raiseNode.raise(inliningTarget, MemoryError);
            }
            return finishResult(frame, inliningTarget, context, result, raiseSignal);
        }
    }

    @Builtin(name = "min_mag", minNumOfPositionalArgs = 2, numOfPositionalOnlyArgs = 1, parameterNames = {"self", "other", "context"})
    @GenerateNodeFactory
    public abstract static class MinMagNode extends PythonBuiltinNode {
        @Specialization
        static PDecimal minMag(VirtualFrame frame, PDecimal self, Object other, Object context,
                        @Bind Node inliningTarget,
                        @Cached GetContextNode getContext,
                        @Cached RaiseSignalNode raiseSignal,
                        @Cached PRaiseNode raiseNode,
                        @Cached RequireDecimalNode convert) {
            PDecimalContext actualContext = getContext.execute(inliningTarget, context);
            PDecimal operand = convert.execute(inliningTarget, other);
            return doMinMag(frame, inliningTarget, self, operand, actualContext, raiseSignal, raiseNode);
        }

        static PDecimal doMinMag(VirtualFrame frame, Node inliningTarget, PDecimal self, PDecimal operand, PDecimalContext context, RaiseSignalNode raiseSignal, PRaiseNode raiseNode) {
            DecimalArithmetic.Result result;
            try {
                result = DecimalArithmetic.minMag(self, operand, context);
            } catch (ArithmeticException e) {
                throw raiseNode.raise(inliningTarget, MemoryError);
            }
            return finishResult(frame, inliningTarget, context, result, raiseSignal);
        }
    }

    @Builtin(name = "next_toward", minNumOfPositionalArgs = 2, numOfPositionalOnlyArgs = 1, parameterNames = {"self", "other", "context"})
    @GenerateNodeFactory
    public abstract static class NextTowardNode extends PythonBuiltinNode {
        @Specialization
        static PDecimal nextToward(VirtualFrame frame, PDecimal self, Object other, Object context,
                        @Bind Node inliningTarget,
                        @Cached GetContextNode getContext,
                        @Cached RaiseSignalNode raiseSignal,
                        @Cached PRaiseNode raiseNode,
                        @Cached RequireDecimalNode convert) {
            PDecimalContext actualContext = getContext.execute(inliningTarget, context);
            PDecimal operand = convert.execute(inliningTarget, other);
            return doNextToward(frame, inliningTarget, self, operand, actualContext, raiseSignal, raiseNode);
        }

        static PDecimal doNextToward(VirtualFrame frame, Node inliningTarget, PDecimal self, PDecimal operand, PDecimalContext context, RaiseSignalNode raiseSignal, PRaiseNode raiseNode) {
            DecimalArithmetic.Result result;
            try {
                result = DecimalArithmetic.nextToward(self, operand, context);
            } catch (ArithmeticException e) {
                throw raiseNode.raise(inliningTarget, MemoryError);
            }
            return finishResult(frame, inliningTarget, context, result, raiseSignal);
        }
    }

    @Builtin(name = "quantize", minNumOfPositionalArgs = 2, numOfPositionalOnlyArgs = 1, parameterNames = {"self", "exp", "rounding", "context"})
    @GenerateNodeFactory
    public abstract static class QuantizeNode extends PythonBuiltinNode {
        @Specialization
        static PDecimal quantize(VirtualFrame frame, PDecimal self, Object exp, Object rounding, Object context,
                        @Bind Node inliningTarget,
                        @Cached GetContextNode getContext,
                        @Cached ToDecimalNode toDecimal,
                        @Cached QuantizeDecimalNode operation,
                        @Cached RaiseSignalNode raiseSignal,
                        @Cached GetClassNode getClass,
                        @Cached TypeNodes.GetNameNode getName,
                        @Cached PRaiseNode raiseNode,
                        @Cached InlinedBranchProfile nonFiniteQuantumProfile) {
            PDecimalContext actualContext = getContext.execute(inliningTarget, context);
            DecimalRounding actualRounding = actualContext.getRounding();
            if (!(rounding instanceof PNone)) {
                actualRounding = DecimalContextUtils.validateRounding(inliningTarget, rounding);
            }
            PDecimal quantum = toDecimal.execute(inliningTarget, exp);
            if (quantum == null) {
                throw raiseNode.raise(inliningTarget, TypeError, ErrorMessages.CONVERSION_FROM_S_TO_DECIMAL_NOT_SUPPORTED,
                                getName.execute(inliningTarget, getClass.execute(inliningTarget, exp)));
            }
            return QuantizeDecimalNode.doQuantize(frame, inliningTarget, self, quantum, actualContext, actualRounding, operation, raiseSignal, raiseNode, nonFiniteQuantumProfile);
        }
    }

    @Builtin(name = "remainder_near", minNumOfPositionalArgs = 2, numOfPositionalOnlyArgs = 1, parameterNames = {"self", "other", "context"})
    @GenerateNodeFactory
    public abstract static class RemainderNearNode extends PythonBuiltinNode {
        @Specialization
        static PDecimal remainderNear(VirtualFrame frame, PDecimal self, Object other, Object context,
                        @Bind Node inliningTarget,
                        @Cached GetContextNode getContext,
                        @Cached RaiseSignalNode raiseSignal,
                        @Cached PRaiseNode raiseNode,
                        @Cached RequireDecimalNode convert) {
            PDecimalContext actualContext = getContext.execute(inliningTarget, context);
            PDecimal operand = convert.execute(inliningTarget, other);
            return doRemainderNear(frame, inliningTarget, self, operand, actualContext, raiseSignal, raiseNode);
        }

        static PDecimal doRemainderNear(VirtualFrame frame, Node inliningTarget, PDecimal self, PDecimal operand, PDecimalContext context, RaiseSignalNode raiseSignal, PRaiseNode raiseNode) {
            DecimalArithmetic.Result result;
            try {
                result = DecimalArithmetic.remainderNear(self, operand, context);
            } catch (ArithmeticException e) {
                throw raiseNode.raise(inliningTarget, MemoryError);
            }
            return finishResult(frame, inliningTarget, context, result, raiseSignal);
        }
    }

    @Builtin(name = "fma", minNumOfPositionalArgs = 3, numOfPositionalOnlyArgs = 1, parameterNames = {"self", "other", "third", "context"})
    @GenerateNodeFactory
    public abstract static class FmaNode extends PythonBuiltinNode {
        @Specialization
        static PDecimal fma(VirtualFrame frame, PDecimal self, Object other, Object third, Object context,
                        @Bind Node inliningTarget,
                        @Cached GetContextNode getContext,
                        @Cached RaiseSignalNode raiseSignal,
                        @Cached PRaiseNode raiseNode,
                        @Cached RequireDecimalNode convert) {
            PDecimalContext actualContext = getContext.execute(inliningTarget, context);
            PDecimal operand = convert.execute(inliningTarget, other);
            PDecimal addend = convert.execute(inliningTarget, third);
            return doFma(frame, inliningTarget, self, operand, addend, actualContext, raiseSignal, raiseNode);
        }

        static PDecimal doFma(VirtualFrame frame, Node inliningTarget, PDecimal self, PDecimal operand, PDecimal addend, PDecimalContext context, RaiseSignalNode raiseSignal, PRaiseNode raiseNode) {
            DecimalArithmetic.Result result;
            try {
                result = DecimalArithmetic.fma(self, operand, addend, context);
            } catch (ArithmeticException e) {
                throw raiseNode.raise(inliningTarget, MemoryError);
            }
            return finishResult(frame, inliningTarget, context, result, raiseSignal);
        }
    }

    @Builtin(name = "is_canonical", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class IsCanonicalNode extends PythonUnaryBuiltinNode {
        @Specialization
        static boolean check(PDecimal self) {
            // In CPython, mpd_iscanonical always returns true
            return true;
        }
    }

    @Builtin(name = "is_finite", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class IsFiniteNode extends PythonUnaryBuiltinNode {
        @Specialization
        static boolean check(PDecimal self) {
            return self.getKind() == Kind.FINITE;
        }
    }

    @Builtin(name = "is_infinite", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class IsInfiniteNode extends PythonUnaryBuiltinNode {
        @Specialization
        static boolean check(PDecimal self) {
            return self.getKind() == Kind.INFINITY;
        }
    }

    @Builtin(name = "is_nan", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class IsNanNode extends PythonUnaryBuiltinNode {
        @Specialization
        static boolean check(PDecimal self) {
            return self.getKind() == Kind.NAN || self.getKind() == Kind.SNAN;
        }
    }

    @Builtin(name = "is_qnan", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class IsQnanNode extends PythonUnaryBuiltinNode {
        @Specialization
        static boolean check(PDecimal self) {
            return self.getKind() == Kind.NAN;
        }
    }

    @Builtin(name = "is_snan", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class IsSnanNode extends PythonUnaryBuiltinNode {
        @Specialization
        static boolean check(PDecimal self) {
            return self.getKind() == Kind.SNAN;
        }
    }

    @Builtin(name = "is_signed", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class IsSignedNode extends PythonUnaryBuiltinNode {
        @Specialization
        static boolean check(PDecimal self) {
            return self.isNegative();
        }
    }

    @Builtin(name = "is_zero", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class IsZeroNode extends PythonUnaryBuiltinNode {
        @Specialization
        static boolean check(PDecimal self) {
            return isZero(self);
        }
    }

    @Builtin(name = "is_normal", minNumOfPositionalArgs = 1, numOfPositionalOnlyArgs = 1, parameterNames = {"self", "context"})
    @GenerateNodeFactory
    public abstract static class IsNormalNode extends PythonBuiltinNode {
        @Specialization
        static boolean check(PDecimal self, Object context,
                        @Bind Node inliningTarget,
                        @Cached GetContextNode getContext) {
            PDecimalContext actualContext = getContext.execute(inliningTarget, context);
            return doCheck(self, actualContext);
        }

        static boolean doCheck(PDecimal self, PDecimalContext context) {
            return isNonzeroFinite(self) && adjustedExponent(self) >= context.getEmin();
        }
    }

    @Builtin(name = "is_subnormal", minNumOfPositionalArgs = 1, numOfPositionalOnlyArgs = 1, parameterNames = {"self", "context"})
    @GenerateNodeFactory
    public abstract static class IsSubnormalNode extends PythonBuiltinNode {
        @Specialization
        static boolean check(PDecimal self, Object context,
                        @Bind Node inliningTarget,
                        @Cached GetContextNode getContext) {
            PDecimalContext actualContext = getContext.execute(inliningTarget, context);
            return doCheck(self, actualContext);
        }

        static boolean doCheck(PDecimal self, PDecimalContext context) {
            return isNonzeroFinite(self) && adjustedExponent(self) < context.getEmin();
        }
    }

    @Builtin(name = "adjusted", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class AdjustedNode extends PythonUnaryBuiltinNode {
        @Specialization
        static long adjusted(PDecimal self) {
            return adjustedExponent(self);
        }
    }

    @Builtin(name = "canonical", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class CanonicalNode extends PythonUnaryBuiltinNode {
        @Specialization
        static PDecimal identity(PDecimal self) {
            return self;
        }
    }

    @Builtin(name = "conjugate", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class ConjugateNode extends PythonUnaryBuiltinNode {
        @Specialization
        static PDecimal identity(PDecimal self) {
            return self;
        }
    }

    @Builtin(name = "radix", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class RadixNode extends PythonUnaryBuiltinNode {
        @Specialization
        static PDecimal radix(@SuppressWarnings("unused") PDecimal self,
                        @Bind PythonLanguage language) {
            return createRadix(language);
        }

        static PDecimal createRadix(PythonLanguage language) {
            return PFactory.createDecimal(language, 10);
        }
    }

    @Builtin(name = "copy_abs", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class CopyAbsNode extends PythonUnaryBuiltinNode {
        @Specialization
        static PDecimal copy(PDecimal self,
                        @Bind PythonLanguage language) {
            return copySign(language, self, false);
        }
    }

    @Builtin(name = "copy_negate", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class CopyNegateNode extends PythonUnaryBuiltinNode {
        @Specialization
        static PDecimal copy(PDecimal self,
                        @Bind PythonLanguage language) {
            return copySign(language, self, !self.isNegative());
        }
    }

    @Builtin(name = "logb", minNumOfPositionalArgs = 1, numOfPositionalOnlyArgs = 1, parameterNames = {"self", "context"})
    @GenerateNodeFactory
    public abstract static class LogbNode extends PythonBuiltinNode {
        @Specialization
        static PDecimal logb(VirtualFrame frame, PDecimal self, Object context,
                        @Bind Node inliningTarget,
                        @Cached GetContextNode getContext,
                        @Cached RaiseSignalNode raiseSignal,
                        @Cached PRaiseNode raiseNode) {
            PDecimalContext actualContext = getContext.execute(inliningTarget, context);
            return doLogb(frame, inliningTarget, self, actualContext, raiseSignal, raiseNode);
        }

        static PDecimal doLogb(VirtualFrame frame, Node inliningTarget, PDecimal self, PDecimalContext context, RaiseSignalNode raiseSignal, PRaiseNode raiseNode) {
            DecimalArithmetic.Result result;
            try {
                result = DecimalArithmetic.logb(self, context);
            } catch (ArithmeticException e) {
                throw raiseNode.raise(inliningTarget, MemoryError);
            }
            return finishResult(frame, inliningTarget, context, result, raiseSignal);
        }
    }

    @Builtin(name = "logical_invert", minNumOfPositionalArgs = 1, numOfPositionalOnlyArgs = 1, parameterNames = {"self", "context"})
    @GenerateNodeFactory
    public abstract static class LogicalInvertNode extends PythonBuiltinNode {
        @Specialization
        static PDecimal logicalInvert(VirtualFrame frame, PDecimal self, Object context,
                        @Bind Node inliningTarget,
                        @Cached GetContextNode getContext,
                        @Cached RaiseSignalNode raiseSignal,
                        @Cached PRaiseNode raiseNode) {
            PDecimalContext actualContext = getContext.execute(inliningTarget, context);
            return doLogicalInvert(frame, inliningTarget, self, actualContext, raiseSignal, raiseNode);
        }

        static PDecimal doLogicalInvert(VirtualFrame frame, Node inliningTarget, PDecimal self, PDecimalContext context, RaiseSignalNode raiseSignal, PRaiseNode raiseNode) {
            DecimalArithmetic.Result result;
            try {
                result = DecimalArithmetic.logicalInvert(self, context);
            } catch (ArithmeticException e) {
                throw raiseNode.raise(inliningTarget, MemoryError);
            }
            return finishResult(frame, inliningTarget, context, result, raiseSignal);
        }
    }

    @Builtin(name = "number_class", minNumOfPositionalArgs = 1, numOfPositionalOnlyArgs = 1, parameterNames = {"self", "context"})
    @GenerateNodeFactory
    public abstract static class NumberClassNode extends PythonBuiltinNode {
        @Specialization
        static TruffleString classify(PDecimal self, Object context,
                        @Bind Node inliningTarget,
                        @Cached GetContextNode getContext) {
            PDecimalContext actualContext = getContext.execute(inliningTarget, context);
            return numberClass(self, actualContext);
        }

        static TruffleString numberClass(PDecimal self, PDecimalContext context) {
            if (self.getKind() == Kind.SNAN) {
                return T_SNAN;
            }
            if (self.getKind() == Kind.NAN) {
                return T_NAN;
            }
            if (self.getKind() == Kind.INFINITY) {
                return self.isNegative() ? T_NEGATIVE_INFINITY : T_POSITIVE_INFINITY;
            }
            if (isZero(self)) {
                return self.isNegative() ? T_NEGATIVE_ZERO : T_POSITIVE_ZERO;
            }
            if (adjustedExponent(self) < context.getEmin()) {
                return self.isNegative() ? T_NEGATIVE_SUBNORMAL : T_POSITIVE_SUBNORMAL;
            }
            return self.isNegative() ? T_NEGATIVE_NORMAL : T_POSITIVE_NORMAL;
        }
    }

    @Builtin(name = "to_eng_string", minNumOfPositionalArgs = 1, numOfPositionalOnlyArgs = 1, parameterNames = {"self", "context"})
    @GenerateNodeFactory
    public abstract static class ToEngStringNode extends PythonBuiltinNode {
        @Specialization
        static TruffleString format(PDecimal self, Object context,
                        @Bind Node inliningTarget,
                        @Cached GetContextNode getContext,
                        @Cached TruffleString.FromJavaStringNode fromJavaString) {
            PDecimalContext actualContext = getContext.execute(inliningTarget, context);
            return doFormat(self, actualContext, fromJavaString);
        }

        static TruffleString doFormat(PDecimal self, PDecimalContext context, TruffleString.FromJavaStringNode fromJavaString) {
            return fromJavaString.execute(engineeringString(self, context.getCapitals()), TS_ENCODING);
        }

        @TruffleBoundary
        private static String engineeringString(PDecimal self, boolean capitals) {
            if (self.getKind() != Kind.FINITE || self.getExponent() <= 0 && adjustedExponent(self) >= -6) {
                return self.toScientificString(capitals);
            }
            String digits = self.getCoefficient().toString();
            long left = self.getExponent() + digits.length();
            int point;
            if (isZero(self)) {
                point = Math.floorMod(left + 1, 3) - 1;
            } else {
                point = Math.floorMod(left - 1, 3) + 1;
            }
            StringBuilder result = new StringBuilder();
            if (self.isNegative()) {
                result.append('-');
            }
            if (point <= 0) {
                result.append("0.").append("0".repeat(-point)).append(digits);
            } else if (point >= digits.length()) {
                result.append(digits).append("0".repeat(point - digits.length()));
            } else {
                result.append(digits, 0, point).append('.').append(digits, point, digits.length());
            }
            long exponent = left - point;
            if (exponent != 0) {
                result.append(capitals ? 'E' : 'e');
                if (exponent > 0) {
                    result.append('+');
                }
                result.append(exponent);
            }
            return result.toString();
        }
    }

    @Builtin(name = "compare_total", minNumOfPositionalArgs = 2, numOfPositionalOnlyArgs = 1, parameterNames = {"self", "other", "context"})
    @GenerateNodeFactory
    public abstract static class CompareTotalNode extends PythonBuiltinNode {
        @Specialization
        static PDecimal compareTotal(VirtualFrame frame, PDecimal self, Object other, Object context,
                        @Bind Node inliningTarget,
                        @Cached GetContextNode getContext,
                        @Cached RaiseSignalNode raiseSignal,
                        @Cached PRaiseNode raiseNode,
                        @Cached RequireDecimalNode convert) {
            PDecimalContext actualContext = getContext.execute(inliningTarget, context);
            PDecimal operand = convert.execute(inliningTarget, other);
            return doCompareTotal(frame, inliningTarget, self, operand, actualContext, raiseSignal, raiseNode);
        }

        static PDecimal doCompareTotal(VirtualFrame frame, Node inliningTarget, PDecimal self, PDecimal operand, PDecimalContext context, RaiseSignalNode raiseSignal, PRaiseNode raiseNode) {
            DecimalArithmetic.Result result;
            try {
                result = DecimalArithmetic.compareTotal(self, operand);
            } catch (ArithmeticException e) {
                throw raiseNode.raise(inliningTarget, MemoryError);
            }
            return finishResult(frame, inliningTarget, context, result, raiseSignal);
        }
    }

    @Builtin(name = "compare_total_mag", minNumOfPositionalArgs = 2, numOfPositionalOnlyArgs = 1, parameterNames = {"self", "other", "context"})
    @GenerateNodeFactory
    public abstract static class CompareTotalMagNode extends PythonBuiltinNode {
        @Specialization
        static PDecimal compareTotalMag(VirtualFrame frame, PDecimal self, Object other, Object context,
                        @Bind Node inliningTarget,
                        @Cached GetContextNode getContext,
                        @Cached RaiseSignalNode raiseSignal,
                        @Cached PRaiseNode raiseNode,
                        @Cached RequireDecimalNode convert) {
            PDecimalContext actualContext = getContext.execute(inliningTarget, context);
            PDecimal operand = convert.execute(inliningTarget, other);
            return doCompareTotalMag(frame, inliningTarget, self, operand, actualContext, raiseSignal, raiseNode);
        }

        static PDecimal doCompareTotalMag(VirtualFrame frame, Node inliningTarget, PDecimal self, PDecimal operand, PDecimalContext context, RaiseSignalNode raiseSignal, PRaiseNode raiseNode) {
            DecimalArithmetic.Result result;
            try {
                result = DecimalArithmetic.compareTotalMag(self, operand);
            } catch (ArithmeticException e) {
                throw raiseNode.raise(inliningTarget, MemoryError);
            }
            return finishResult(frame, inliningTarget, context, result, raiseSignal);
        }
    }

    @Builtin(name = "copy_sign", minNumOfPositionalArgs = 2, numOfPositionalOnlyArgs = 1, parameterNames = {"self", "other", "context"})
    @GenerateNodeFactory
    public abstract static class CopySignNode extends PythonBuiltinNode {
        @Specialization
        static PDecimal apply(PDecimal self, Object other, Object context,
                        @Bind Node inliningTarget,
                        @Cached GetContextNode getContext,
                        @Cached RequireDecimalNode convert) {
            getContext.execute(inliningTarget, context);
            PDecimal operand = convert.execute(inliningTarget, other);
            return doCopySign(PythonLanguage.get(inliningTarget), self, operand);
        }

        static PDecimal doCopySign(PythonLanguage language, PDecimal self, PDecimal other) {
            return copySign(language, self, other.isNegative());
        }
    }

    @Builtin(name = "same_quantum", minNumOfPositionalArgs = 2, numOfPositionalOnlyArgs = 1, parameterNames = {"self", "other", "context"})
    @GenerateNodeFactory
    public abstract static class SameQuantumNode extends PythonBuiltinNode {
        @Specialization
        static boolean apply(PDecimal self, Object other, Object context,
                        @Bind Node inliningTarget,
                        @Cached GetContextNode getContext,
                        @Cached RequireDecimalNode convert) {
            getContext.execute(inliningTarget, context);
            PDecimal operand = convert.execute(inliningTarget, other);
            return sameQuantum(self, operand);
        }

        static boolean sameQuantum(PDecimal self, PDecimal other) {
            if (self.getKind() == Kind.FINITE && other.getKind() == Kind.FINITE) {
                return self.getExponent() == other.getExponent();
            }
            if (self.getKind() == Kind.INFINITY || other.getKind() == Kind.INFINITY) {
                return self.getKind() == other.getKind();
            }
            return self.getKind() != Kind.FINITE && other.getKind() != Kind.FINITE;
        }
    }

    @Builtin(name = "logical_and", minNumOfPositionalArgs = 2, numOfPositionalOnlyArgs = 1, parameterNames = {"self", "other", "context"})
    @GenerateNodeFactory
    public abstract static class LogicalAndNode extends PythonBuiltinNode {
        @Specialization
        static PDecimal logicalAnd(VirtualFrame frame, PDecimal self, Object other, Object context,
                        @Bind Node inliningTarget,
                        @Cached GetContextNode getContext,
                        @Cached RaiseSignalNode raiseSignal,
                        @Cached PRaiseNode raiseNode,
                        @Cached RequireDecimalNode convert) {
            PDecimalContext actualContext = getContext.execute(inliningTarget, context);
            PDecimal operand = convert.execute(inliningTarget, other);
            return doLogicalAnd(frame, inliningTarget, self, operand, actualContext, raiseSignal, raiseNode);
        }

        static PDecimal doLogicalAnd(VirtualFrame frame, Node inliningTarget, PDecimal self, PDecimal operand, PDecimalContext context, RaiseSignalNode raiseSignal, PRaiseNode raiseNode) {
            DecimalArithmetic.Result result;
            try {
                result = DecimalArithmetic.logicalAnd(self, operand, context);
            } catch (ArithmeticException e) {
                throw raiseNode.raise(inliningTarget, MemoryError);
            }
            return finishResult(frame, inliningTarget, context, result, raiseSignal);
        }
    }

    @Builtin(name = "logical_or", minNumOfPositionalArgs = 2, numOfPositionalOnlyArgs = 1, parameterNames = {"self", "other", "context"})
    @GenerateNodeFactory
    public abstract static class LogicalOrNode extends PythonBuiltinNode {
        @Specialization
        static PDecimal logicalOr(VirtualFrame frame, PDecimal self, Object other, Object context,
                        @Bind Node inliningTarget,
                        @Cached GetContextNode getContext,
                        @Cached RaiseSignalNode raiseSignal,
                        @Cached PRaiseNode raiseNode,
                        @Cached RequireDecimalNode convert) {
            PDecimalContext actualContext = getContext.execute(inliningTarget, context);
            PDecimal operand = convert.execute(inliningTarget, other);
            return doLogicalOr(frame, inliningTarget, self, operand, actualContext, raiseSignal, raiseNode);
        }

        static PDecimal doLogicalOr(VirtualFrame frame, Node inliningTarget, PDecimal self, PDecimal operand, PDecimalContext context, RaiseSignalNode raiseSignal, PRaiseNode raiseNode) {
            DecimalArithmetic.Result result;
            try {
                result = DecimalArithmetic.logicalOr(self, operand, context);
            } catch (ArithmeticException e) {
                throw raiseNode.raise(inliningTarget, MemoryError);
            }
            return finishResult(frame, inliningTarget, context, result, raiseSignal);
        }
    }

    @Builtin(name = "logical_xor", minNumOfPositionalArgs = 2, numOfPositionalOnlyArgs = 1, parameterNames = {"self", "other", "context"})
    @GenerateNodeFactory
    public abstract static class LogicalXorNode extends PythonBuiltinNode {
        @Specialization
        static PDecimal logicalXor(VirtualFrame frame, PDecimal self, Object other, Object context,
                        @Bind Node inliningTarget,
                        @Cached GetContextNode getContext,
                        @Cached RaiseSignalNode raiseSignal,
                        @Cached PRaiseNode raiseNode,
                        @Cached RequireDecimalNode convert) {
            PDecimalContext actualContext = getContext.execute(inliningTarget, context);
            PDecimal operand = convert.execute(inliningTarget, other);
            return doLogicalXor(frame, inliningTarget, self, operand, actualContext, raiseSignal, raiseNode);
        }

        static PDecimal doLogicalXor(VirtualFrame frame, Node inliningTarget, PDecimal self, PDecimal operand, PDecimalContext context, RaiseSignalNode raiseSignal, PRaiseNode raiseNode) {
            DecimalArithmetic.Result result;
            try {
                result = DecimalArithmetic.logicalXor(self, operand, context);
            } catch (ArithmeticException e) {
                throw raiseNode.raise(inliningTarget, MemoryError);
            }
            return finishResult(frame, inliningTarget, context, result, raiseSignal);
        }
    }

    @Builtin(name = "rotate", minNumOfPositionalArgs = 2, numOfPositionalOnlyArgs = 1, parameterNames = {"self", "other", "context"})
    @GenerateNodeFactory
    public abstract static class RotateNode extends PythonBuiltinNode {
        @Specialization
        static PDecimal rotate(VirtualFrame frame, PDecimal self, Object other, Object context,
                        @Bind Node inliningTarget,
                        @Cached GetContextNode getContext,
                        @Cached RaiseSignalNode raiseSignal,
                        @Cached PRaiseNode raiseNode,
                        @Cached RequireDecimalNode convert) {
            PDecimalContext actualContext = getContext.execute(inliningTarget, context);
            PDecimal operand = convert.execute(inliningTarget, other);
            return doRotate(frame, inliningTarget, self, operand, actualContext, raiseSignal, raiseNode);
        }

        static PDecimal doRotate(VirtualFrame frame, Node inliningTarget, PDecimal self, PDecimal operand, PDecimalContext context, RaiseSignalNode raiseSignal, PRaiseNode raiseNode) {
            DecimalArithmetic.Result result;
            try {
                result = DecimalArithmetic.rotate(self, operand, context);
            } catch (ArithmeticException e) {
                throw raiseNode.raise(inliningTarget, MemoryError);
            }
            return finishResult(frame, inliningTarget, context, result, raiseSignal);
        }
    }

    @Builtin(name = "scaleb", minNumOfPositionalArgs = 2, numOfPositionalOnlyArgs = 1, parameterNames = {"self", "other", "context"})
    @GenerateNodeFactory
    public abstract static class ScalebNode extends PythonBuiltinNode {
        @Specialization
        static PDecimal scaleb(VirtualFrame frame, PDecimal self, Object other, Object context,
                        @Bind Node inliningTarget,
                        @Cached GetContextNode getContext,
                        @Cached RaiseSignalNode raiseSignal,
                        @Cached PRaiseNode raiseNode,
                        @Cached RequireDecimalNode convert) {
            PDecimalContext actualContext = getContext.execute(inliningTarget, context);
            PDecimal operand = convert.execute(inliningTarget, other);
            return doScaleb(frame, inliningTarget, self, operand, actualContext, raiseSignal, raiseNode);
        }

        static PDecimal doScaleb(VirtualFrame frame, Node inliningTarget, PDecimal self, PDecimal operand, PDecimalContext context, RaiseSignalNode raiseSignal, PRaiseNode raiseNode) {
            DecimalArithmetic.Result result;
            try {
                result = DecimalArithmetic.scaleb(self, operand, context);
            } catch (ArithmeticException e) {
                throw raiseNode.raise(inliningTarget, MemoryError);
            }
            return finishResult(frame, inliningTarget, context, result, raiseSignal);
        }
    }

    @Builtin(name = "shift", minNumOfPositionalArgs = 2, numOfPositionalOnlyArgs = 1, parameterNames = {"self", "other", "context"})
    @GenerateNodeFactory
    public abstract static class ShiftNode extends PythonBuiltinNode {
        @Specialization
        static PDecimal shift(VirtualFrame frame, PDecimal self, Object other, Object context,
                        @Bind Node inliningTarget,
                        @Cached GetContextNode getContext,
                        @Cached RaiseSignalNode raiseSignal,
                        @Cached PRaiseNode raiseNode,
                        @Cached RequireDecimalNode convert) {
            PDecimalContext actualContext = getContext.execute(inliningTarget, context);
            PDecimal operand = convert.execute(inliningTarget, other);
            return doShift(frame, inliningTarget, self, operand, actualContext, raiseSignal, raiseNode);
        }

        static PDecimal doShift(VirtualFrame frame, Node inliningTarget, PDecimal self, PDecimal operand, PDecimalContext context, RaiseSignalNode raiseSignal, PRaiseNode raiseNode) {
            DecimalArithmetic.Result result;
            try {
                result = DecimalArithmetic.shift(self, operand, context);
            } catch (ArithmeticException e) {
                throw raiseNode.raise(inliningTarget, MemoryError);
            }
            return finishResult(frame, inliningTarget, context, result, raiseSignal);
        }
    }

    @Builtin(name = "from_float", minNumOfPositionalArgs = 2, isClassmethod = true)
    @GenerateNodeFactory
    public abstract static class FromFloatNode extends PythonBinaryBuiltinNode {
        @Specialization
        static Object convert(VirtualFrame frame, Object cls, Object value,
                        @Bind Node inliningTarget,
                        @Cached PyLongCheckNode longCheck,
                        @Cached PyFloatCheckNode floatCheck,
                        @Cached RequireDecimalNode toDecimal,
                        @Cached PyFloatAsDoubleNode asDouble,
                        @Cached IsBuiltinClassExactProfile clsProfile,
                        @Cached CallNode callNode,
                        @Cached PRaiseNode raiseNode) {
            PDecimal result = fromFloat(frame, inliningTarget, value, longCheck, floatCheck, toDecimal, asDouble, raiseNode);
            if (clsProfile.profileClass(inliningTarget, cls, PythonBuiltinClassType.PDecimal)) {
                return result;
            }
            return callNode.execute(frame, cls, result);
        }

        static PDecimal fromFloat(VirtualFrame frame, Node inliningTarget, Object value, PyLongCheckNode longCheck,
                        PyFloatCheckNode floatCheck, RequireDecimalNode toDecimal, PyFloatAsDoubleNode asDouble, PRaiseNode raiseNode) {
            PDecimal result;
            if (longCheck.execute(inliningTarget, value)) {
                result = toDecimal.execute(inliningTarget, value);
            } else if (floatCheck.execute(inliningTarget, value)) {
                result = DecimalNodes.create(PythonLanguage.get(inliningTarget), DecimalUtils.fromFloat(asDouble.execute(frame, inliningTarget, value)));
            } else {
                throw raiseNode.raise(inliningTarget, TypeError, ErrorMessages.DECIMAL_FROM_FLOAT_ARGUMENT);
            }
            return result;
        }
    }

    @Builtin(name = "as_tuple", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class AsTupleNode extends PythonUnaryBuiltinNode {
        @Specialization
        static Object tuple(VirtualFrame frame, PDecimal self,
                        @Bind Node inliningTarget,
                        @Cached CallNode callNode) {
            Object[] args = tupleArguments(PythonLanguage.get(inliningTarget), self);
            return callNode.execute(frame, DecimalContextUtils.getState(inliningTarget).getDecimalTupleType(), args, PKeyword.EMPTY_KEYWORDS);
        }
    }

    @Builtin(name = "as_integer_ratio", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class AsIntegerRatioNode extends PythonUnaryBuiltinNode {
        @Specialization
        static Object ratio(PDecimal self,
                        @Bind Node inliningTarget,
                        @Cached PRaiseNode raiseNode) {
            if (self.getKind() == Kind.INFINITY) {
                throw raiseNode.raise(inliningTarget, OverflowError, ErrorMessages.DECIMAL_INFINITY_TO_RATIO);
            }
            if (self.getKind() != Kind.FINITE) {
                throw raiseNode.raise(inliningTarget, ValueError, ErrorMessages.DECIMAL_NAN_TO_RATIO);
            }
            try {
                return integerRatio(PythonLanguage.get(inliningTarget), self);
            } catch (ArithmeticException e) {
                throw raiseNode.raise(inliningTarget, MemoryError);
            }
        }
    }

    private static DecimalRounding getRounding(Object rounding, Node inliningTarget, PDecimalContext actualContext) {
        if (rounding instanceof PNone) {
            return actualContext.getRounding();
        } else {
            return DecimalContextUtils.validateRounding(inliningTarget, rounding);
        }
    }

    private static boolean isZero(PDecimal self) {
        return self.getKind() == Kind.FINITE && self.isCompact() && self.getCompactCoefficient() == 0;
    }

    private static boolean isNonzeroFinite(PDecimal self) {
        return self.getKind() == Kind.FINITE && !isZero(self);
    }

    private static long adjustedExponent(PDecimal self) {
        if (self.getKind() != Kind.FINITE) {
            return 0;
        }
        long exponent = self.getExponent();
        int digits;
        if (self.isCompact()) {
            digits = DecimalUtils.digits(self.getCompactCoefficient());
        } else {
            digits = DecimalUtils.digits(self.getCoefficient());
        }
        return exponent + digits - 1;
    }

    private static PDecimal copySign(PythonLanguage language, PDecimal self, boolean negative) {
        if (self.isCompact()) {
            return PFactory.createDecimal(language, negative, self.getCompactCoefficient(), self.getExponent(), self.getKind());
        }
        return PFactory.createDecimal(language, negative, self.getCoefficient(), self.getExponent(), self.getKind());
    }

    @TruffleBoundary
    private static Object[] tupleArguments(PythonLanguage language, PDecimal self) {
        String text = self.getCoefficient().toString();
        Object exponent = self.getExponent();
        if (self.getKind() == Kind.INFINITY) {
            text = "0";
            exponent = tsLiteral("F");
        } else if (self.getKind() != Kind.FINITE) {
            if (text.equals("0")) {
                text = "";
            }
            exponent = self.getKind() == Kind.NAN ? tsLiteral("n") : tsLiteral("N");
        }
        int[] digits = new int[text.length()];
        for (int i = 0; i < text.length(); i++) {
            digits[i] = text.charAt(i) - '0';
        }
        return new Object[]{self.isNegative() ? 1 : 0, PFactory.createTuple(language, digits), exponent};
    }

    @TruffleBoundary
    private static Object integerRatio(PythonLanguage language, PDecimal self) {
        BigInteger numerator = self.getCoefficient();
        BigInteger denominator = BigInteger.ONE;
        if (numerator.signum() != 0) {
            if (self.getExponent() >= 0) {
                numerator = numerator.multiply(DecimalUtils.pow10(self.getExponent()));
            } else {
                // Cancel powers of two and five before constructing the denominator.
                long fives = -self.getExponent();
                BigInteger five = BigInteger.valueOf(5);
                while (fives > 0 && numerator.remainder(five).signum() == 0) {
                    numerator = numerator.divide(five);
                    fives--;
                }
                long twos = -self.getExponent();
                int shift = (int) Math.min(numerator.getLowestSetBit(), twos);
                numerator = numerator.shiftRight(shift);
                twos -= shift;
                denominator = five.pow(Math.toIntExact(fives)).shiftLeft(Math.toIntExact(twos));
            }
        }
        if (self.isNegative()) {
            numerator = numerator.negate();
        }
        return PFactory.createTuple(language, new Object[]{PFactory.createInt(language, numerator), PFactory.createInt(language, denominator)});
    }

    private static Value decimalValue(PDecimal self) {
        return new Value(self.isNegative(), self.getCoefficient(), self.getExponent(), self.getKind());
    }

    private static Object toInteger(Node inliningTarget, PDecimal self, DecimalRounding rounding, PRaiseNode raiseNode) {
        if (self.getKind() == Kind.INFINITY) {
            throw raiseNode.raise(inliningTarget, OverflowError, ErrorMessages.DECIMAL_INFINITY_TO_INTEGER);
        }
        if (self.getKind() != Kind.FINITE) {
            throw raiseNode.raise(inliningTarget, ValueError, ErrorMessages.DECIMAL_NAN_TO_INTEGER);
        }
        long compact = DecimalNodes.compactInteger(self, rounding);
        if (compact != Long.MIN_VALUE) {
            return compact;
        }
        try {
            return PFactory.createInt(PythonLanguage.get(inliningTarget), DecimalArithmetic.toInteger(self, rounding));
        } catch (ArithmeticException e) {
            throw raiseNode.raise(inliningTarget, MemoryError);
        }
    }

    @Builtin(name = "__copy__", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class CopyNode extends PythonUnaryBuiltinNode {
        @Specialization
        static PDecimal copy(PDecimal self) {
            return self;
        }
    }

    @Builtin(name = "__deepcopy__", minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    public abstract static class DeepcopyNode extends PythonBinaryBuiltinNode {
        @Specialization
        static PDecimal copy(PDecimal self, @SuppressWarnings("unused") Object memo) {
            return self;
        }
    }

    @Builtin(name = "__format__", minNumOfPositionalArgs = 2, maxNumOfPositionalArgs = 3)
    @GenerateNodeFactory
    public abstract static class FormatNode extends PythonTernaryBuiltinNode {
        @Specialization
        static Object format(VirtualFrame frame, PDecimal self, Object specifier, Object override,
                        @Bind Node inliningTarget,
                        @Cached PyUnicodeCheckNode unicodeCheck,
                        @Cached CastToJavaStringNode asString,
                        @Cached TruffleString.FromJavaStringNode fromJavaString,
                        @Cached WarnNode warnNode,
                        @Cached PRaiseNode raiseNode,
                        @Cached GetCurrentContextNode getCurrentContext) {
            if (!unicodeCheck.execute(inliningTarget, specifier)) {
                throw raiseNode.raise(inliningTarget, TypeError, ErrorMessages.DECIMAL_FORMAT_STRING);
            }
            String format = asString.execute(specifier);
            if (endsWithN(format)) {
                warnNode.warnEx(frame, DeprecationWarning, ErrorMessages.DECIMAL_FORMAT_N_DEPRECATED, 1);
            }
            PDecimalContext context = getCurrentContext.execute(inliningTarget);
            try {
                return fromJavaString.execute(DecimalFormatting.format(self, format, override, context), TS_ENCODING);
            } catch (ArithmeticException e) {
                throw raiseNode.raise(inliningTarget, MemoryError);
            }
        }

        @TruffleBoundary
        private static boolean endsWithN(String format) {
            return format.endsWith("N");
        }

    }

    @Builtin(name = "__reduce__", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class ReduceNode extends PythonUnaryBuiltinNode {
        @Specialization
        static Object reduce(PDecimal self,
                        @Bind Node inliningTarget,
                        @Cached GetClassNode getClass,
                        @Cached TpStrNode str) {
            PythonLanguage language = PythonLanguage.get(inliningTarget);
            Object args = PFactory.createTuple(language, new Object[]{str.execute(null, self)});
            return PFactory.createTuple(language, new Object[]{getClass.execute(inliningTarget, self), args});
        }
    }

    @Builtin(name = "__round__", minNumOfPositionalArgs = 1, maxNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    public abstract static class RoundNode extends PythonBinaryBuiltinNode {
        @Specialization
        static Object round(VirtualFrame frame, PDecimal self, Object ndigits,
                        @Bind Node inliningTarget,
                        @Cached PyLongCheckNode longCheck,
                        @Cached PyLongAsLongNode asLong,
                        @Cached QuantizeDecimalNode operation,
                        @Cached PRaiseNode raiseNode,
                        @Cached GetCurrentContextNode getCurrentContext) {
            if (ndigits == PNone.NO_VALUE) {
                return toInteger(inliningTarget, self, DecimalRounding.ROUND_HALF_EVEN, raiseNode);
            }
            if (!longCheck.execute(inliningTarget, ndigits)) {
                throw raiseNode.raise(inliningTarget, TypeError, ErrorMessages.DECIMAL_ROUND_DIGITS);
            }
            long digits = asLong.execute(frame, inliningTarget, ndigits);
            long exponent;
            if (digits == Long.MIN_VALUE) {
                exponent = Long.MAX_VALUE;
            } else {
                exponent = -digits;
            }
            PDecimalContext context = getCurrentContext.execute(inliningTarget);
            return operation.execute(frame, inliningTarget, self, exponent, context, context.getRounding());
        }
    }

    @Builtin(name = "__ceil__", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class CeilNode extends PythonUnaryBuiltinNode {
        @Specialization
        static Object round(PDecimal self,
                        @Bind Node inliningTarget,
                        @Cached PRaiseNode raiseNode) {
            return toInteger(inliningTarget, self, DecimalRounding.ROUND_CEILING, raiseNode);
        }
    }

    @Builtin(name = "__floor__", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class FloorNode extends PythonUnaryBuiltinNode {
        @Specialization
        static Object round(PDecimal self,
                        @Bind Node inliningTarget,
                        @Cached PRaiseNode raiseNode) {
            return toInteger(inliningTarget, self, DecimalRounding.ROUND_FLOOR, raiseNode);
        }
    }

    @Builtin(name = "__trunc__", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class TruncNode extends PythonUnaryBuiltinNode {
        @Specialization
        static Object trunc(PDecimal self,
                        @Cached NbIntNode integer) {
            return integer.execute(null, self);
        }
    }

    @Builtin(name = "__complex__", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class ComplexNode extends PythonUnaryBuiltinNode {
        @Specialization
        static Object complex(PDecimal self,
                        @Bind Node inliningTarget,
                        @Cached NbFloatNode floating) {
            return PFactory.createComplex(PythonLanguage.get(inliningTarget), (double) floating.execute(null, self), 0.0);
        }
    }

    @Builtin(name = "__sizeof__", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class SizeofNode extends PythonUnaryBuiltinNode {
        @Specialization
        static long size(PDecimal self,
                        @Bind Node inliningTarget,
                        @Cached GetClassNode getClass,
                        @Cached TypeNodes.GetBasicSizeNode getBasicSize) {
            // Report coefficient storage in addition to the object's basic size.
            return getBasicSize.execute(inliningTarget, getClass.execute(inliningTarget, self)) + coefficientSize(self);
        }

        @TruffleBoundary
        private static long coefficientSize(PDecimal self) {
            if (self.isCompact()) {
                return Long.BYTES;
            }
            return (self.getCoefficient().bitLength() + 31L) / 32 * Integer.BYTES;
        }
    }

    @Builtin(name = "real", minNumOfPositionalArgs = 1, isGetter = true)
    @GenerateNodeFactory
    public abstract static class RealNode extends PythonUnaryBuiltinNode {
        @Specialization
        static PDecimal real(PDecimal self) {
            return self;
        }
    }

    @Builtin(name = "imag", minNumOfPositionalArgs = 1, isGetter = true)
    @GenerateNodeFactory
    public abstract static class ImagNode extends PythonUnaryBuiltinNode {
        @Specialization
        static PDecimal imag(@SuppressWarnings("unused") PDecimal self,
                        @Bind PythonLanguage language) {
            return PFactory.createDecimal(language, 0);
        }
    }

    @Slot(value = SlotKind.tp_new, isComplex = true)
    @SlotSignature(name = "Decimal", minNumOfPositionalArgs = 1, numOfPositionalOnlyArgs = 1, parameterNames = {"cls", "value", "context"})
    @GenerateNodeFactory
    public abstract static class TpNewNode extends PythonTernaryBuiltinNode {
        @Specialization
        static PDecimal construct(VirtualFrame frame, Object cls, Object value, Object context,
                        @Bind Node inliningTarget,
                        @Cached CreateDecimalNode createDecimal,
                        @Cached RaiseSignalNode raiseSignal,
                        @Cached GetContextNode getContext,
                        @Cached InlinedBranchProfile errorProfile,
                        @Cached PyFloatCheckNode floatCheck,
                        @Cached TypeNodes.GetInstanceShape getShape) {
            PDecimalContext decimalContext = getContext.execute(inliningTarget, context);
            if (floatCheck.execute(inliningTarget, value)) {
                DecimalContextUtils.addStatus(frame, decimalContext, DecimalContextUtils.conditionMask(DecimalExceptionTypes.FloatOperation), raiseSignal);
            }
            try {
                return createDecimal.execute(frame, inliningTarget, cls, value, true);
            } catch (ConversionException e) {
                errorProfile.enter(inliningTarget);
                DecimalContextUtils.addStatus(frame, decimalContext, DecimalContextUtils.conditionMask(e.condition), raiseSignal);
                return PFactory.createDecimal(cls, getShape.execute(cls), false, 0L, 0L, Kind.NAN);
            }
        }

    }

    @Slot(value = SlotKind.tp_repr, isComplex = true)
    @GenerateNodeFactory
    public abstract static class TpReprNode extends PythonUnaryBuiltinNode {
        @Specialization
        static TruffleString repr(PDecimal self,
                        @Bind Node inliningTarget,
                        @Cached TruffleString.FromJavaStringNode fromJavaString,
                        @Cached GetCurrentContextNode getCurrentContext) {
            boolean capitals = getCurrentContext.execute(inliningTarget).getCapitals();
            return fromJavaString.execute(reprString(self, capitals), TS_ENCODING);
        }

        @TruffleBoundary
        private static String reprString(PDecimal self, boolean capitals) {
            return "Decimal('" + self.toScientificString(capitals) + "')";
        }
    }

    @Slot(value = SlotKind.tp_str, isComplex = true)
    @GenerateNodeFactory
    public abstract static class TpStrNode extends PythonUnaryBuiltinNode {
        @Specialization
        static TruffleString str(PDecimal self,
                        @Bind Node inliningTarget,
                        @Cached DecimalToStringNode toString,
                        @Cached GetCurrentContextNode getCurrentContext) {
            boolean capitals = getCurrentContext.execute(inliningTarget).getCapitals();
            return toString.execute(inliningTarget, self, capitals);
        }

    }

    @Slot(value = SlotKind.nb_negative, isComplex = true)
    @GenerateNodeFactory
    public abstract static class NbNegativeNode extends PythonUnaryBuiltinNode {
        @Specialization
        static PDecimal unary(VirtualFrame frame, PDecimal self,
                        @Bind Node inliningTarget,
                        @Cached MinusDecimalNode operation) {
            return operation.execute(frame, inliningTarget, self);
        }
    }

    @Slot(value = SlotKind.nb_positive, isComplex = true)
    @GenerateNodeFactory
    public abstract static class NbPositiveNode extends PythonUnaryBuiltinNode {
        @Specialization
        static PDecimal unary(VirtualFrame frame, PDecimal self,
                        @Bind Node inliningTarget,
                        @Cached PlusDecimalNode operation) {
            return operation.execute(frame, inliningTarget, self);
        }
    }

    @Slot(value = SlotKind.nb_absolute, isComplex = true)
    @GenerateNodeFactory
    public abstract static class NbAbsoluteNode extends PythonUnaryBuiltinNode {
        @Specialization
        static PDecimal unary(VirtualFrame frame, PDecimal self,
                        @Bind Node inliningTarget,
                        @Cached AbsDecimalNode operation) {
            return operation.execute(frame, inliningTarget, self);
        }
    }

    @Slot(value = SlotKind.nb_int, isComplex = true)
    @GenerateNodeFactory
    public abstract static class NbIntNode extends PythonUnaryBuiltinNode {
        @Specialization
        static Object integer(PDecimal self,
                        @Bind Node inliningTarget,
                        @Cached InlinedBranchProfile nonCompactProfile,
                        @Cached PRaiseNode raiseNode) {
            if (self.getKind() == Kind.INFINITY) {
                throw raiseNode.raise(inliningTarget, OverflowError, ErrorMessages.DECIMAL_INFINITY_TO_INTEGER);
            }
            if (self.getKind() != Kind.FINITE) {
                throw raiseNode.raise(inliningTarget, ValueError, ErrorMessages.DECIMAL_NAN_TO_INTEGER);
            }
            if (self.isCompact()) {
                long coefficient = self.getCompactCoefficient();
                long exponent = self.getExponent();
                if (exponent > 0) {
                    coefficient = DecimalNodes.scaleCompactCoefficient(coefficient, exponent);
                } else {
                    // Truncate toward zero; any compact coefficient vanishes within 19 steps.
                    while (exponent < 0 && coefficient != 0) {
                        coefficient /= 10;
                        exponent++;
                    }
                }
                if (coefficient >= 0) {
                    if (self.isNegative()) {
                        return -coefficient;
                    }
                    return coefficient;
                }
            }
            nonCompactProfile.enter(inliningTarget);
            try {
                return PFactory.createInt(PythonLanguage.get(inliningTarget), DecimalArithmetic.toInteger(self));
            } catch (ArithmeticException e) {
                throw raiseNode.raise(inliningTarget, MemoryError);
            }
        }
    }

    @Slot(value = SlotKind.nb_float, isComplex = true)
    @GenerateNodeFactory
    public abstract static class NbFloatNode extends PythonUnaryBuiltinNode {
        @Specialization
        static double floating(PDecimal self,
                        @Bind Node inliningTarget,
                        @Cached PRaiseNode raiseNode) {
            if (self.getKind() == Kind.SNAN) {
                throw raiseNode.raise(inliningTarget, ValueError, ErrorMessages.DECIMAL_SNAN_TO_FLOAT);
            }
            return DecimalArithmetic.toFloat(self);
        }
    }

    @Slot(value = SlotKind.tp_hash, isComplex = true)
    @GenerateNodeFactory
    public abstract static class TpHashNode extends HashBuiltinNode {
        // Inverse of 10 modulo 2**61 - 1, as in CPython's _dec_hash.
        private static final long INVERSE_TEN_HASH = 2075258708292324556L;

        @Specialization
        static long hash(PDecimal self,
                        @Bind Node inliningTarget,
                        @Cached PRaiseNode raiseNode) {
            long hash = self.getHash();
            if (hash != -1) {
                return hash;
            }
            if (self.getKind() == Kind.SNAN) {
                throw raiseNode.raise(inliningTarget, TypeError, ErrorMessages.DECIMAL_SNAN_HASH);
            }
            if (self.getKind() == Kind.NAN) {
                hash = ObjectBuiltins.HashNode.hash(self);
            } else if (self.getKind() == Kind.INFINITY) {
                hash = SysModuleBuiltins.HASH_INF;
                if (self.isNegative()) {
                    hash = -hash;
                }
            } else if (self.isCompact()) {
                hash = hashCompact(self.getCompactCoefficient(), self.getExponent());
                if (self.isNegative()) {
                    hash = -hash;
                }
            } else {
                hash = hashFinite(self);
            }
            hash = hash == -1 ? -2 : hash;
            self.setHash(hash);
            return hash;
        }

        private static long hashCompact(long coefficient, long exponent) {
            long hash = coefficient % SysModuleBuiltins.HASH_MODULUS;
            if (hash == 0 || exponent == 0) {
                return hash;
            }
            long base = 10;
            if (exponent < 0) {
                base = INVERSE_TEN_HASH;
                exponent = -exponent;
            }
            while (exponent != 0) {
                if ((exponent & 1) != 0) {
                    hash = multiplyHashModulus(hash, base);
                }
                exponent >>>= 1;
                if (exponent != 0) {
                    base = multiplyHashModulus(base, base);
                }
            }
            return hash;
        }

        private static long multiplyHashModulus(long a, long b) {
            // Fold the unsigned 128-bit product at bit 61 using 2**61 == 1 (mod p).
            assert SysModuleBuiltins.HASH_BITS == 61;
            long low = a * b;
            long high = Math.multiplyHigh(a, b);
            long reduced = (low & SysModuleBuiltins.HASH_MODULUS) + ((high << 3) | (low >>> 61));
            reduced = (reduced & SysModuleBuiltins.HASH_MODULUS) + (reduced >>> 61);
            if (reduced == SysModuleBuiltins.HASH_MODULUS) {
                return 0;
            }
            return reduced;
        }

        @TruffleBoundary
        private static long hashFinite(PDecimal self) {
            BigInteger modulus = BigInteger.valueOf(SysModuleBuiltins.HASH_MODULUS);
            BigInteger base = BigInteger.TEN;
            if (self.getExponent() < 0) {
                base = base.modInverse(modulus);
            }
            BigInteger power = base.modPow(BigInteger.valueOf(self.getExponent()).abs(), modulus);
            long hash = self.getCoefficient().mod(modulus).multiply(power).mod(modulus).longValue();
            if (self.isNegative()) {
                hash = -hash;
            }
            return hash == -1 ? -2 : hash;
        }
    }

    @Slot(value = SlotKind.tp_richcompare, isComplex = true)
    @ImportStatic(DecimalNodes.class)
    @GenerateNodeFactory
    public abstract static class TpRichcompareNode extends RichCmpBuiltinNode {
        @Specialization(guards = {"isCompactFinite(self)", "isCompactFinite(other)"})
        static boolean compactDecimal(PDecimal self, PDecimal other, RichCmpOp op) {
            return op.compareResultToBool(DecimalNodes.compareCompact(self.isNegative(), self.getCompactCoefficient(), self.getExponent(),
                            other.isNegative(), other.getCompactCoefficient(), other.getExponent()));
        }

        @Specialization(guards = "isCompactFinite(self)")
        static boolean compactInt(PDecimal self, int other, RichCmpOp op) {
            return compareInteger(self, other, op);
        }

        @Specialization(guards = {"isCompactFinite(self)", "fitsCompactLong(other)"})
        static boolean compactLong(PDecimal self, long other, RichCmpOp op) {
            return compareInteger(self, other, op);
        }

        private static boolean compareInteger(PDecimal self, long other, RichCmpOp op) {
            return op.compareResultToBool(DecimalNodes.compareCompact(self.isNegative(), self.getCompactCoefficient(), self.getExponent(),
                            other < 0, Math.abs(other), 0));
        }

        static boolean fitsCompactLong(long value) {
            return value != Long.MIN_VALUE;
        }

        @Specialization
        static Object compare(VirtualFrame frame, PDecimal self, Object other, RichCmpOp op,
                        @Bind Node inliningTarget,
                        @Cached ToDecimalNode toDecimal,
                        @Cached PyFloatCheckNode floatCheck,
                        @Cached PyFloatAsDoubleNode asDouble,
                        @Cached PyComplexCheckNode complexCheck,
                        @Cached ToComplexValueNode asComplex,
                        @Cached PyObjectIsInstanceNode isInstance,
                        @Cached PyObjectGetAttr getAttr,
                        @Cached CastToJavaBigIntegerNode asInteger,
                        @Cached PyLongCheckNode longCheck,
                        @Cached RaiseSignalNode raiseSignal,
                        @Cached PRaiseNode raiseNode,
                        @Cached GetCurrentContextNode getCurrentContext) {
            PDecimalContext context = getCurrentContext.execute(inliningTarget);
            Value a = decimalValue(self);
            Value b;
            PDecimal converted = toDecimal.execute(inliningTarget, other);
            if (converted != null) {
                b = decimalValue(converted);
            } else if (floatCheck.execute(inliningTarget, other)) {
                floatOperation(frame, context, op, raiseSignal);
                b = DecimalUtils.fromFloat(asDouble.execute(frame, inliningTarget, other));
            } else if (op.isEqOrNe() && complexCheck.execute(inliningTarget, other)) {
                ComplexValue complex = asComplex.execute(inliningTarget, other);
                if (complex.getImag() != 0.0) {
                    return PNotImplemented.NOT_IMPLEMENTED;
                }
                floatOperation(frame, context, op, raiseSignal);
                b = DecimalUtils.fromFloat(complex.getReal());
            } else if (isInstance.execute(frame, other, DecimalContextUtils.getState(inliningTarget).getRationalType())) {
                Object numerator = getAttr.execute(frame, inliningTarget, other, T_NUMERATOR);
                if (!longCheck.execute(inliningTarget, numerator)) {
                    throw raiseNode.raise(inliningTarget, TypeError, ErrorMessages.DECIMAL_INTEGER_ARGUMENT);
                }
                b = DecimalUtils.fromInteger(asInteger.execute(inliningTarget, numerator));
                if (a.kind() == Kind.FINITE) {
                    Object denominator = getAttr.execute(frame, inliningTarget, other, T_DENOMINATOR);
                    if (!longCheck.execute(inliningTarget, denominator)) {
                        throw raiseNode.raise(inliningTarget, TypeError, ErrorMessages.DECIMAL_INTEGER_ARGUMENT);
                    }
                    a = multiplyDenominator(a, asInteger.execute(inliningTarget, denominator));
                }
            } else {
                return PNotImplemented.NOT_IMPLEMENTED;
            }
            if (a.kind() == Kind.NAN || a.kind() == Kind.SNAN || b.kind() == Kind.NAN || b.kind() == Kind.SNAN) {
                if (!op.isEqOrNe() || a.kind() == Kind.SNAN || b.kind() == Kind.SNAN) {
                    DecimalContextUtils.addStatus(frame, context, DecimalContextUtils.conditionMask(DecimalExceptionTypes.InvalidOperation), raiseSignal);
                }
                return op.isNe();
            }
            return op.compareResultToBool(DecimalArithmetic.compare(a, b));
        }

        private static void floatOperation(VirtualFrame frame, PDecimalContext context, RichCmpOp op, RaiseSignalNode raiseSignal) {
            int mask = DecimalContextUtils.mask(DecimalExceptionTypes.FloatOperation);
            if (op.isEqOrNe()) {
                context.setFlags(context.getFlags() | mask);
            } else {
                DecimalContextUtils.addStatus(frame, context, DecimalContextUtils.conditionMask(DecimalExceptionTypes.FloatOperation), raiseSignal);
            }
        }

        @TruffleBoundary
        private static Value multiplyDenominator(Value value, BigInteger denominator) {
            return new Value(value.negative() != (denominator.signum() < 0), value.coefficient().multiply(denominator.abs()), value.exponent(), Kind.FINITE);
        }
    }

    @Slot(value = SlotKind.nb_add, isComplex = true)
    @GenerateNodeFactory
    public abstract static class NbAddNode extends BinaryOpBuiltinNode {
        @Specialization
        static Object binary(VirtualFrame frame, Object left, Object right,
                        @Bind Node inliningTarget,
                        @Cached AddDecimalNode operation) {
            return operation.execute(frame, inliningTarget, left, right);
        }
    }

    @Slot(value = SlotKind.nb_subtract, isComplex = true)
    @GenerateNodeFactory
    public abstract static class NbSubtractNode extends BinaryOpBuiltinNode {
        @Specialization
        static Object binary(VirtualFrame frame, Object left, Object right,
                        @Bind Node inliningTarget,
                        @Cached SubtractDecimalNode operation) {
            return operation.execute(frame, inliningTarget, left, right);
        }
    }

    @Slot(value = SlotKind.nb_multiply, isComplex = true)
    @GenerateNodeFactory
    public abstract static class NbMultiplyNode extends BinaryOpBuiltinNode {
        @Specialization
        static Object binary(VirtualFrame frame, Object left, Object right,
                        @Bind Node inliningTarget,
                        @Cached MultiplyDecimalNode operation) {
            return operation.execute(frame, inliningTarget, left, right);
        }
    }

    @Slot(value = SlotKind.nb_remainder, isComplex = true)
    @GenerateNodeFactory
    public abstract static class NbRemainderNode extends BinaryOpBuiltinNode {
        @Specialization
        static Object binary(VirtualFrame frame, Object left, Object right,
                        @Bind Node inliningTarget,
                        @Cached RemainderDecimalNode operation) {
            return operation.execute(frame, inliningTarget, left, right);
        }
    }

    @Slot(value = SlotKind.nb_divmod, isComplex = true)
    @GenerateNodeFactory
    public abstract static class NbDivmodNode extends BinaryOpBuiltinNode {
        @Specialization
        static Object binary(VirtualFrame frame, Object left, Object right,
                        @Bind Node inliningTarget,
                        @Cached DivmodDecimalNode operation) {
            return operation.execute(frame, inliningTarget, left, right);
        }
    }

    @Slot(value = SlotKind.nb_floor_divide, isComplex = true)
    @GenerateNodeFactory
    public abstract static class NbFloorDivideNode extends BinaryOpBuiltinNode {
        @Specialization
        static Object binary(VirtualFrame frame, Object left, Object right,
                        @Bind Node inliningTarget,
                        @Cached DivideIntDecimalNode operation) {
            return operation.execute(frame, inliningTarget, left, right);
        }
    }

    @Slot(value = SlotKind.nb_true_divide, isComplex = true)
    @GenerateNodeFactory
    public abstract static class NbTrueDivideNode extends BinaryOpBuiltinNode {
        @Specialization
        static Object binary(VirtualFrame frame, Object left, Object right,
                        @Bind Node inliningTarget,
                        @Cached DivideDecimalNode operation) {
            return operation.execute(frame, inliningTarget, left, right);
        }
    }

    @Slot(value = SlotKind.nb_power, isComplex = true)
    @GenerateNodeFactory
    public abstract static class NbPowerNode extends PythonTernaryBuiltinNode {
        @Specialization
        static Object power(VirtualFrame frame, Object left, Object right, Object mod,
                        @Bind Node inliningTarget,
                        @Cached ToDecimalNode toLeft,
                        @Cached ToDecimalNode toRight,
                        @Cached ToDecimalNode toModulo,
                        @Cached InlinedConditionProfile notImplementedProfile,
                        @Cached RaiseSignalNode raiseSignal,
                        @Cached PRaiseNode raiseNode,
                        @Cached GetCurrentContextNode getCurrentContext) {
            PDecimal a = toLeft.execute(inliningTarget, left);
            PDecimal b = toRight.execute(inliningTarget, right);
            boolean hasModulo = !(mod instanceof PNone);
            PDecimal modulo = hasModulo ? toModulo.execute(inliningTarget, mod) : null;
            if (notImplementedProfile.profile(inliningTarget, a == null || b == null || hasModulo && modulo == null)) {
                return PNotImplemented.NOT_IMPLEMENTED;
            }
            PDecimalContext context = getCurrentContext.execute(inliningTarget);
            return doPower(frame, inliningTarget, a, b, modulo, context, raiseSignal, raiseNode);
        }

        static PDecimal doPower(VirtualFrame frame, Node inliningTarget, PDecimal a, PDecimal b, PDecimal modulo, PDecimalContext context, RaiseSignalNode raiseSignal, PRaiseNode raiseNode) {
            DecimalArithmetic.Result result;
            try {
                result = DecimalArithmetic.power(a, b, modulo, context);
            } catch (ArithmeticException e) {
                throw raiseNode.raise(inliningTarget, MemoryError);
            }
            return finishResult(frame, inliningTarget, context, result, raiseSignal);
        }
    }

    @Slot(value = SlotKind.nb_bool, isComplex = true)
    @GenerateNodeFactory
    public abstract static class NbBoolNode extends NbBoolBuiltinNode {
        @Specialization
        static boolean bool(PDecimal self) {
            return self.getKind() != Kind.FINITE || !self.isCompact() || self.getCompactCoefficient() != 0;
        }
    }
}
