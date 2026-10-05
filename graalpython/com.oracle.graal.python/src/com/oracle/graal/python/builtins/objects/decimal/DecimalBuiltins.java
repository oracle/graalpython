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

import static com.oracle.graal.python.builtins.PythonBuiltinClassType.NotImplementedError;

import java.util.List;

import com.oracle.graal.python.annotations.Builtin;
import com.oracle.graal.python.annotations.Slot.SlotKind;
import com.oracle.graal.python.annotations.Slot.SlotSignature;
import com.oracle.graal.python.annotations.Slot;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.objects.function.PKeyword;
import com.oracle.graal.python.builtins.objects.type.TpSlots;
import com.oracle.graal.python.builtins.objects.type.slots.TpSlotBinaryOp.BinaryOpBuiltinNode;
import com.oracle.graal.python.builtins.objects.type.slots.TpSlotGetAttr.GetAttrBuiltinNode;
import com.oracle.graal.python.builtins.objects.type.slots.TpSlotHashFun.HashBuiltinNode;
import com.oracle.graal.python.builtins.objects.type.slots.TpSlotInquiry.NbBoolBuiltinNode;
import com.oracle.graal.python.builtins.objects.type.slots.TpSlotRichCompare.RichCmpBuiltinNode;
import com.oracle.graal.python.lib.RichCmpOp;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonBinaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonTernaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonUnaryBuiltinNode;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;

@CoreFunctions(extendClasses = PythonBuiltinClassType.PDecimal)
public final class DecimalBuiltins extends PythonBuiltins {
    public static final TpSlots SLOTS = DecimalBuiltinsSlotsGen.SLOTS;

    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return DecimalBuiltinsFactory.getFactories();
    }

    @Builtin(name = "exp", minNumOfPositionalArgs = 1, numOfPositionalOnlyArgs = 1, parameterNames = {"self", "context"}, takesVarKeywordArgs = true)
    @GenerateNodeFactory
    public abstract static class ExpNode extends PythonBuiltinNode {
        @Specialization
        static Object doMock(VirtualFrame frame, Object self, Object context, PKeyword[] kwargs,
                        @Bind Node inliningTarget,
                        @Cached PRaiseNode raiseNode) {
            // TODO implement Decimal.exp.
            throw raiseNode.raise(inliningTarget, NotImplementedError);
        }
    }

    @Builtin(name = "ln", minNumOfPositionalArgs = 1, numOfPositionalOnlyArgs = 1, parameterNames = {"self", "context"}, takesVarKeywordArgs = true)
    @GenerateNodeFactory
    public abstract static class LnNode extends PythonBuiltinNode {
        @Specialization
        static Object doMock(VirtualFrame frame, Object self, Object context, PKeyword[] kwargs,
                        @Bind Node inliningTarget,
                        @Cached PRaiseNode raiseNode) {
            // TODO implement Decimal.ln.
            throw raiseNode.raise(inliningTarget, NotImplementedError);
        }
    }

    @Builtin(name = "log10", minNumOfPositionalArgs = 1, numOfPositionalOnlyArgs = 1, parameterNames = {"self", "context"}, takesVarKeywordArgs = true)
    @GenerateNodeFactory
    public abstract static class Log10Node extends PythonBuiltinNode {
        @Specialization
        static Object doMock(VirtualFrame frame, Object self, Object context, PKeyword[] kwargs,
                        @Bind Node inliningTarget,
                        @Cached PRaiseNode raiseNode) {
            // TODO implement Decimal.log10.
            throw raiseNode.raise(inliningTarget, NotImplementedError);
        }
    }

    @Builtin(name = "next_minus", minNumOfPositionalArgs = 1, numOfPositionalOnlyArgs = 1, parameterNames = {"self", "context"}, takesVarKeywordArgs = true)
    @GenerateNodeFactory
    public abstract static class NextMinusNode extends PythonBuiltinNode {
        @Specialization
        static Object doMock(VirtualFrame frame, Object self, Object context, PKeyword[] kwargs,
                        @Bind Node inliningTarget,
                        @Cached PRaiseNode raiseNode) {
            // TODO implement Decimal.next_minus.
            throw raiseNode.raise(inliningTarget, NotImplementedError);
        }
    }

    @Builtin(name = "next_plus", minNumOfPositionalArgs = 1, numOfPositionalOnlyArgs = 1, parameterNames = {"self", "context"}, takesVarKeywordArgs = true)
    @GenerateNodeFactory
    public abstract static class NextPlusNode extends PythonBuiltinNode {
        @Specialization
        static Object doMock(VirtualFrame frame, Object self, Object context, PKeyword[] kwargs,
                        @Bind Node inliningTarget,
                        @Cached PRaiseNode raiseNode) {
            // TODO implement Decimal.next_plus.
            throw raiseNode.raise(inliningTarget, NotImplementedError);
        }
    }

    @Builtin(name = "normalize", minNumOfPositionalArgs = 1, numOfPositionalOnlyArgs = 1, parameterNames = {"self", "context"}, takesVarKeywordArgs = true)
    @GenerateNodeFactory
    public abstract static class NormalizeNode extends PythonBuiltinNode {
        @Specialization
        static Object doMock(VirtualFrame frame, Object self, Object context, PKeyword[] kwargs,
                        @Bind Node inliningTarget,
                        @Cached PRaiseNode raiseNode) {
            // TODO implement Decimal.normalize.
            throw raiseNode.raise(inliningTarget, NotImplementedError);
        }
    }

    @Builtin(name = "to_integral", minNumOfPositionalArgs = 1, numOfPositionalOnlyArgs = 1, parameterNames = {"self", "rounding", "context"}, takesVarKeywordArgs = true)
    @GenerateNodeFactory
    public abstract static class ToIntegralNode extends PythonBuiltinNode {
        @Specialization
        static Object doMock(VirtualFrame frame, Object self, Object rounding, Object context, PKeyword[] kwargs,
                        @Bind Node inliningTarget,
                        @Cached PRaiseNode raiseNode) {
            // TODO implement Decimal.to_integral.
            throw raiseNode.raise(inliningTarget, NotImplementedError);
        }
    }

    @Builtin(name = "to_integral_exact", minNumOfPositionalArgs = 1, numOfPositionalOnlyArgs = 1, parameterNames = {"self", "rounding", "context"}, takesVarKeywordArgs = true)
    @GenerateNodeFactory
    public abstract static class ToIntegralExactNode extends PythonBuiltinNode {
        @Specialization
        static Object doMock(VirtualFrame frame, Object self, Object rounding, Object context, PKeyword[] kwargs,
                        @Bind Node inliningTarget,
                        @Cached PRaiseNode raiseNode) {
            // TODO implement Decimal.to_integral_exact.
            throw raiseNode.raise(inliningTarget, NotImplementedError);
        }
    }

    @Builtin(name = "to_integral_value", minNumOfPositionalArgs = 1, numOfPositionalOnlyArgs = 1, parameterNames = {"self", "rounding", "context"}, takesVarKeywordArgs = true)
    @GenerateNodeFactory
    public abstract static class ToIntegralValueNode extends PythonBuiltinNode {
        @Specialization
        static Object doMock(VirtualFrame frame, Object self, Object rounding, Object context, PKeyword[] kwargs,
                        @Bind Node inliningTarget,
                        @Cached PRaiseNode raiseNode) {
            // TODO implement Decimal.to_integral_value.
            throw raiseNode.raise(inliningTarget, NotImplementedError);
        }
    }

    @Builtin(name = "sqrt", minNumOfPositionalArgs = 1, numOfPositionalOnlyArgs = 1, parameterNames = {"self", "context"}, takesVarKeywordArgs = true)
    @GenerateNodeFactory
    public abstract static class SqrtNode extends PythonBuiltinNode {
        @Specialization
        static Object doMock(VirtualFrame frame, Object self, Object context, PKeyword[] kwargs,
                        @Bind Node inliningTarget,
                        @Cached PRaiseNode raiseNode) {
            // TODO implement Decimal.sqrt.
            throw raiseNode.raise(inliningTarget, NotImplementedError);
        }
    }

    @Builtin(name = "compare", minNumOfPositionalArgs = 2, numOfPositionalOnlyArgs = 1, parameterNames = {"self", "other", "context"}, takesVarKeywordArgs = true)
    @GenerateNodeFactory
    public abstract static class CompareNode extends PythonBuiltinNode {
        @Specialization
        static Object doMock(VirtualFrame frame, Object self, Object other, Object context, PKeyword[] kwargs,
                        @Bind Node inliningTarget,
                        @Cached PRaiseNode raiseNode) {
            // TODO implement Decimal.compare.
            throw raiseNode.raise(inliningTarget, NotImplementedError);
        }
    }

    @Builtin(name = "compare_signal", minNumOfPositionalArgs = 2, numOfPositionalOnlyArgs = 1, parameterNames = {"self", "other", "context"}, takesVarKeywordArgs = true)
    @GenerateNodeFactory
    public abstract static class CompareSignalNode extends PythonBuiltinNode {
        @Specialization
        static Object doMock(VirtualFrame frame, Object self, Object other, Object context, PKeyword[] kwargs,
                        @Bind Node inliningTarget,
                        @Cached PRaiseNode raiseNode) {
            // TODO implement Decimal.compare_signal.
            throw raiseNode.raise(inliningTarget, NotImplementedError);
        }
    }

    @Builtin(name = "max", minNumOfPositionalArgs = 2, numOfPositionalOnlyArgs = 1, parameterNames = {"self", "other", "context"}, takesVarKeywordArgs = true)
    @GenerateNodeFactory
    public abstract static class MaxNode extends PythonBuiltinNode {
        @Specialization
        static Object doMock(VirtualFrame frame, Object self, Object other, Object context, PKeyword[] kwargs,
                        @Bind Node inliningTarget,
                        @Cached PRaiseNode raiseNode) {
            // TODO implement Decimal.max.
            throw raiseNode.raise(inliningTarget, NotImplementedError);
        }
    }

    @Builtin(name = "max_mag", minNumOfPositionalArgs = 2, numOfPositionalOnlyArgs = 1, parameterNames = {"self", "other", "context"}, takesVarKeywordArgs = true)
    @GenerateNodeFactory
    public abstract static class MaxMagNode extends PythonBuiltinNode {
        @Specialization
        static Object doMock(VirtualFrame frame, Object self, Object other, Object context, PKeyword[] kwargs,
                        @Bind Node inliningTarget,
                        @Cached PRaiseNode raiseNode) {
            // TODO implement Decimal.max_mag.
            throw raiseNode.raise(inliningTarget, NotImplementedError);
        }
    }

    @Builtin(name = "min", minNumOfPositionalArgs = 2, numOfPositionalOnlyArgs = 1, parameterNames = {"self", "other", "context"}, takesVarKeywordArgs = true)
    @GenerateNodeFactory
    public abstract static class MinNode extends PythonBuiltinNode {
        @Specialization
        static Object doMock(VirtualFrame frame, Object self, Object other, Object context, PKeyword[] kwargs,
                        @Bind Node inliningTarget,
                        @Cached PRaiseNode raiseNode) {
            // TODO implement Decimal.min.
            throw raiseNode.raise(inliningTarget, NotImplementedError);
        }
    }

    @Builtin(name = "min_mag", minNumOfPositionalArgs = 2, numOfPositionalOnlyArgs = 1, parameterNames = {"self", "other", "context"}, takesVarKeywordArgs = true)
    @GenerateNodeFactory
    public abstract static class MinMagNode extends PythonBuiltinNode {
        @Specialization
        static Object doMock(VirtualFrame frame, Object self, Object other, Object context, PKeyword[] kwargs,
                        @Bind Node inliningTarget,
                        @Cached PRaiseNode raiseNode) {
            // TODO implement Decimal.min_mag.
            throw raiseNode.raise(inliningTarget, NotImplementedError);
        }
    }

    @Builtin(name = "next_toward", minNumOfPositionalArgs = 2, numOfPositionalOnlyArgs = 1, parameterNames = {"self", "other", "context"}, takesVarKeywordArgs = true)
    @GenerateNodeFactory
    public abstract static class NextTowardNode extends PythonBuiltinNode {
        @Specialization
        static Object doMock(VirtualFrame frame, Object self, Object other, Object context, PKeyword[] kwargs,
                        @Bind Node inliningTarget,
                        @Cached PRaiseNode raiseNode) {
            // TODO implement Decimal.next_toward.
            throw raiseNode.raise(inliningTarget, NotImplementedError);
        }
    }

    @Builtin(name = "quantize", minNumOfPositionalArgs = 2, numOfPositionalOnlyArgs = 1, parameterNames = {"self", "exp", "rounding", "context"}, takesVarKeywordArgs = true)
    @GenerateNodeFactory
    public abstract static class QuantizeNode extends PythonBuiltinNode {
        @Specialization
        static Object doMock(VirtualFrame frame, Object self, Object exp, Object rounding, Object context, PKeyword[] kwargs,
                        @Bind Node inliningTarget,
                        @Cached PRaiseNode raiseNode) {
            // TODO implement Decimal.quantize.
            throw raiseNode.raise(inliningTarget, NotImplementedError);
        }
    }

    @Builtin(name = "remainder_near", minNumOfPositionalArgs = 2, numOfPositionalOnlyArgs = 1, parameterNames = {"self", "other", "context"}, takesVarKeywordArgs = true)
    @GenerateNodeFactory
    public abstract static class RemainderNearNode extends PythonBuiltinNode {
        @Specialization
        static Object doMock(VirtualFrame frame, Object self, Object other, Object context, PKeyword[] kwargs,
                        @Bind Node inliningTarget,
                        @Cached PRaiseNode raiseNode) {
            // TODO implement Decimal.remainder_near.
            throw raiseNode.raise(inliningTarget, NotImplementedError);
        }
    }

    @Builtin(name = "fma", minNumOfPositionalArgs = 3, numOfPositionalOnlyArgs = 1, parameterNames = {"self", "other", "third", "context"}, takesVarKeywordArgs = true)
    @GenerateNodeFactory
    public abstract static class FmaNode extends PythonBuiltinNode {
        @Specialization
        static Object doMock(VirtualFrame frame, Object self, Object other, Object third, Object context, PKeyword[] kwargs,
                        @Bind Node inliningTarget,
                        @Cached PRaiseNode raiseNode) {
            // TODO implement Decimal.fma.
            throw raiseNode.raise(inliningTarget, NotImplementedError);
        }
    }

    @Builtin(name = "is_canonical", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class IsCanonicalNode extends PythonUnaryBuiltinNode {
        @Specialization
        static Object doMock(VirtualFrame frame, Object self,
                        @Bind Node inliningTarget,
                        @Cached PRaiseNode raiseNode) {
            // TODO implement Decimal.is_canonical.
            throw raiseNode.raise(inliningTarget, NotImplementedError);
        }
    }

    @Builtin(name = "is_finite", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class IsFiniteNode extends PythonUnaryBuiltinNode {
        @Specialization
        static Object doMock(VirtualFrame frame, Object self,
                        @Bind Node inliningTarget,
                        @Cached PRaiseNode raiseNode) {
            // TODO implement Decimal.is_finite.
            throw raiseNode.raise(inliningTarget, NotImplementedError);
        }
    }

    @Builtin(name = "is_infinite", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class IsInfiniteNode extends PythonUnaryBuiltinNode {
        @Specialization
        static Object doMock(VirtualFrame frame, Object self,
                        @Bind Node inliningTarget,
                        @Cached PRaiseNode raiseNode) {
            // TODO implement Decimal.is_infinite.
            throw raiseNode.raise(inliningTarget, NotImplementedError);
        }
    }

    @Builtin(name = "is_nan", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class IsNanNode extends PythonUnaryBuiltinNode {
        @Specialization
        static Object doMock(VirtualFrame frame, Object self,
                        @Bind Node inliningTarget,
                        @Cached PRaiseNode raiseNode) {
            // TODO implement Decimal.is_nan.
            throw raiseNode.raise(inliningTarget, NotImplementedError);
        }
    }

    @Builtin(name = "is_qnan", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class IsQnanNode extends PythonUnaryBuiltinNode {
        @Specialization
        static Object doMock(VirtualFrame frame, Object self,
                        @Bind Node inliningTarget,
                        @Cached PRaiseNode raiseNode) {
            // TODO implement Decimal.is_qnan.
            throw raiseNode.raise(inliningTarget, NotImplementedError);
        }
    }

    @Builtin(name = "is_snan", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class IsSnanNode extends PythonUnaryBuiltinNode {
        @Specialization
        static Object doMock(VirtualFrame frame, Object self,
                        @Bind Node inliningTarget,
                        @Cached PRaiseNode raiseNode) {
            // TODO implement Decimal.is_snan.
            throw raiseNode.raise(inliningTarget, NotImplementedError);
        }
    }

    @Builtin(name = "is_signed", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class IsSignedNode extends PythonUnaryBuiltinNode {
        @Specialization
        static Object doMock(VirtualFrame frame, Object self,
                        @Bind Node inliningTarget,
                        @Cached PRaiseNode raiseNode) {
            // TODO implement Decimal.is_signed.
            throw raiseNode.raise(inliningTarget, NotImplementedError);
        }
    }

    @Builtin(name = "is_zero", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class IsZeroNode extends PythonUnaryBuiltinNode {
        @Specialization
        static Object doMock(VirtualFrame frame, Object self,
                        @Bind Node inliningTarget,
                        @Cached PRaiseNode raiseNode) {
            // TODO implement Decimal.is_zero.
            throw raiseNode.raise(inliningTarget, NotImplementedError);
        }
    }

    @Builtin(name = "is_normal", minNumOfPositionalArgs = 1, numOfPositionalOnlyArgs = 1, parameterNames = {"self", "context"}, takesVarKeywordArgs = true)
    @GenerateNodeFactory
    public abstract static class IsNormalNode extends PythonBuiltinNode {
        @Specialization
        static Object doMock(VirtualFrame frame, Object self, Object context, PKeyword[] kwargs,
                        @Bind Node inliningTarget,
                        @Cached PRaiseNode raiseNode) {
            // TODO implement Decimal.is_normal.
            throw raiseNode.raise(inliningTarget, NotImplementedError);
        }
    }

    @Builtin(name = "is_subnormal", minNumOfPositionalArgs = 1, numOfPositionalOnlyArgs = 1, parameterNames = {"self", "context"}, takesVarKeywordArgs = true)
    @GenerateNodeFactory
    public abstract static class IsSubnormalNode extends PythonBuiltinNode {
        @Specialization
        static Object doMock(VirtualFrame frame, Object self, Object context, PKeyword[] kwargs,
                        @Bind Node inliningTarget,
                        @Cached PRaiseNode raiseNode) {
            // TODO implement Decimal.is_subnormal.
            throw raiseNode.raise(inliningTarget, NotImplementedError);
        }
    }

    @Builtin(name = "adjusted", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class AdjustedNode extends PythonUnaryBuiltinNode {
        @Specialization
        static Object doMock(VirtualFrame frame, Object self,
                        @Bind Node inliningTarget,
                        @Cached PRaiseNode raiseNode) {
            // TODO implement Decimal.adjusted.
            throw raiseNode.raise(inliningTarget, NotImplementedError);
        }
    }

    @Builtin(name = "canonical", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class CanonicalNode extends PythonUnaryBuiltinNode {
        @Specialization
        static Object doMock(VirtualFrame frame, Object self,
                        @Bind Node inliningTarget,
                        @Cached PRaiseNode raiseNode) {
            // TODO implement Decimal.canonical.
            throw raiseNode.raise(inliningTarget, NotImplementedError);
        }
    }

    @Builtin(name = "conjugate", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class ConjugateNode extends PythonUnaryBuiltinNode {
        @Specialization
        static Object doMock(VirtualFrame frame, Object self,
                        @Bind Node inliningTarget,
                        @Cached PRaiseNode raiseNode) {
            // TODO implement Decimal.conjugate.
            throw raiseNode.raise(inliningTarget, NotImplementedError);
        }
    }

    @Builtin(name = "radix", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class RadixNode extends PythonUnaryBuiltinNode {
        @Specialization
        static Object doMock(VirtualFrame frame, Object self,
                        @Bind Node inliningTarget,
                        @Cached PRaiseNode raiseNode) {
            // TODO implement Decimal.radix.
            throw raiseNode.raise(inliningTarget, NotImplementedError);
        }
    }

    @Builtin(name = "copy_abs", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class CopyAbsNode extends PythonUnaryBuiltinNode {
        @Specialization
        static Object doMock(VirtualFrame frame, Object self,
                        @Bind Node inliningTarget,
                        @Cached PRaiseNode raiseNode) {
            // TODO implement Decimal.copy_abs.
            throw raiseNode.raise(inliningTarget, NotImplementedError);
        }
    }

    @Builtin(name = "copy_negate", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class CopyNegateNode extends PythonUnaryBuiltinNode {
        @Specialization
        static Object doMock(VirtualFrame frame, Object self,
                        @Bind Node inliningTarget,
                        @Cached PRaiseNode raiseNode) {
            // TODO implement Decimal.copy_negate.
            throw raiseNode.raise(inliningTarget, NotImplementedError);
        }
    }

    @Builtin(name = "logb", minNumOfPositionalArgs = 1, numOfPositionalOnlyArgs = 1, parameterNames = {"self", "context"}, takesVarKeywordArgs = true)
    @GenerateNodeFactory
    public abstract static class LogbNode extends PythonBuiltinNode {
        @Specialization
        static Object doMock(VirtualFrame frame, Object self, Object context, PKeyword[] kwargs,
                        @Bind Node inliningTarget,
                        @Cached PRaiseNode raiseNode) {
            // TODO implement Decimal.logb.
            throw raiseNode.raise(inliningTarget, NotImplementedError);
        }
    }

    @Builtin(name = "logical_invert", minNumOfPositionalArgs = 1, numOfPositionalOnlyArgs = 1, parameterNames = {"self", "context"}, takesVarKeywordArgs = true)
    @GenerateNodeFactory
    public abstract static class LogicalInvertNode extends PythonBuiltinNode {
        @Specialization
        static Object doMock(VirtualFrame frame, Object self, Object context, PKeyword[] kwargs,
                        @Bind Node inliningTarget,
                        @Cached PRaiseNode raiseNode) {
            // TODO implement Decimal.logical_invert.
            throw raiseNode.raise(inliningTarget, NotImplementedError);
        }
    }

    @Builtin(name = "number_class", minNumOfPositionalArgs = 1, numOfPositionalOnlyArgs = 1, parameterNames = {"self", "context"}, takesVarKeywordArgs = true)
    @GenerateNodeFactory
    public abstract static class NumberClassNode extends PythonBuiltinNode {
        @Specialization
        static Object doMock(VirtualFrame frame, Object self, Object context, PKeyword[] kwargs,
                        @Bind Node inliningTarget,
                        @Cached PRaiseNode raiseNode) {
            // TODO implement Decimal.number_class.
            throw raiseNode.raise(inliningTarget, NotImplementedError);
        }
    }

    @Builtin(name = "to_eng_string", minNumOfPositionalArgs = 1, numOfPositionalOnlyArgs = 1, parameterNames = {"self", "context"}, takesVarKeywordArgs = true)
    @GenerateNodeFactory
    public abstract static class ToEngStringNode extends PythonBuiltinNode {
        @Specialization
        static Object doMock(VirtualFrame frame, Object self, Object context, PKeyword[] kwargs,
                        @Bind Node inliningTarget,
                        @Cached PRaiseNode raiseNode) {
            // TODO implement Decimal.to_eng_string.
            throw raiseNode.raise(inliningTarget, NotImplementedError);
        }
    }

    @Builtin(name = "compare_total", minNumOfPositionalArgs = 2, numOfPositionalOnlyArgs = 1, parameterNames = {"self", "other", "context"}, takesVarKeywordArgs = true)
    @GenerateNodeFactory
    public abstract static class CompareTotalNode extends PythonBuiltinNode {
        @Specialization
        static Object doMock(VirtualFrame frame, Object self, Object other, Object context, PKeyword[] kwargs,
                        @Bind Node inliningTarget,
                        @Cached PRaiseNode raiseNode) {
            // TODO implement Decimal.compare_total.
            throw raiseNode.raise(inliningTarget, NotImplementedError);
        }
    }

    @Builtin(name = "compare_total_mag", minNumOfPositionalArgs = 2, numOfPositionalOnlyArgs = 1, parameterNames = {"self", "other", "context"}, takesVarKeywordArgs = true)
    @GenerateNodeFactory
    public abstract static class CompareTotalMagNode extends PythonBuiltinNode {
        @Specialization
        static Object doMock(VirtualFrame frame, Object self, Object other, Object context, PKeyword[] kwargs,
                        @Bind Node inliningTarget,
                        @Cached PRaiseNode raiseNode) {
            // TODO implement Decimal.compare_total_mag.
            throw raiseNode.raise(inliningTarget, NotImplementedError);
        }
    }

    @Builtin(name = "copy_sign", minNumOfPositionalArgs = 2, numOfPositionalOnlyArgs = 1, parameterNames = {"self", "other", "context"}, takesVarKeywordArgs = true)
    @GenerateNodeFactory
    public abstract static class CopySignNode extends PythonBuiltinNode {
        @Specialization
        static Object doMock(VirtualFrame frame, Object self, Object other, Object context, PKeyword[] kwargs,
                        @Bind Node inliningTarget,
                        @Cached PRaiseNode raiseNode) {
            // TODO implement Decimal.copy_sign.
            throw raiseNode.raise(inliningTarget, NotImplementedError);
        }
    }

    @Builtin(name = "same_quantum", minNumOfPositionalArgs = 2, numOfPositionalOnlyArgs = 1, parameterNames = {"self", "other", "context"}, takesVarKeywordArgs = true)
    @GenerateNodeFactory
    public abstract static class SameQuantumNode extends PythonBuiltinNode {
        @Specialization
        static Object doMock(VirtualFrame frame, Object self, Object other, Object context, PKeyword[] kwargs,
                        @Bind Node inliningTarget,
                        @Cached PRaiseNode raiseNode) {
            // TODO implement Decimal.same_quantum.
            throw raiseNode.raise(inliningTarget, NotImplementedError);
        }
    }

    @Builtin(name = "logical_and", minNumOfPositionalArgs = 2, numOfPositionalOnlyArgs = 1, parameterNames = {"self", "other", "context"}, takesVarKeywordArgs = true)
    @GenerateNodeFactory
    public abstract static class LogicalAndNode extends PythonBuiltinNode {
        @Specialization
        static Object doMock(VirtualFrame frame, Object self, Object other, Object context, PKeyword[] kwargs,
                        @Bind Node inliningTarget,
                        @Cached PRaiseNode raiseNode) {
            // TODO implement Decimal.logical_and.
            throw raiseNode.raise(inliningTarget, NotImplementedError);
        }
    }

    @Builtin(name = "logical_or", minNumOfPositionalArgs = 2, numOfPositionalOnlyArgs = 1, parameterNames = {"self", "other", "context"}, takesVarKeywordArgs = true)
    @GenerateNodeFactory
    public abstract static class LogicalOrNode extends PythonBuiltinNode {
        @Specialization
        static Object doMock(VirtualFrame frame, Object self, Object other, Object context, PKeyword[] kwargs,
                        @Bind Node inliningTarget,
                        @Cached PRaiseNode raiseNode) {
            // TODO implement Decimal.logical_or.
            throw raiseNode.raise(inliningTarget, NotImplementedError);
        }
    }

    @Builtin(name = "logical_xor", minNumOfPositionalArgs = 2, numOfPositionalOnlyArgs = 1, parameterNames = {"self", "other", "context"}, takesVarKeywordArgs = true)
    @GenerateNodeFactory
    public abstract static class LogicalXorNode extends PythonBuiltinNode {
        @Specialization
        static Object doMock(VirtualFrame frame, Object self, Object other, Object context, PKeyword[] kwargs,
                        @Bind Node inliningTarget,
                        @Cached PRaiseNode raiseNode) {
            // TODO implement Decimal.logical_xor.
            throw raiseNode.raise(inliningTarget, NotImplementedError);
        }
    }

    @Builtin(name = "rotate", minNumOfPositionalArgs = 2, numOfPositionalOnlyArgs = 1, parameterNames = {"self", "other", "context"}, takesVarKeywordArgs = true)
    @GenerateNodeFactory
    public abstract static class RotateNode extends PythonBuiltinNode {
        @Specialization
        static Object doMock(VirtualFrame frame, Object self, Object other, Object context, PKeyword[] kwargs,
                        @Bind Node inliningTarget,
                        @Cached PRaiseNode raiseNode) {
            // TODO implement Decimal.rotate.
            throw raiseNode.raise(inliningTarget, NotImplementedError);
        }
    }

    @Builtin(name = "scaleb", minNumOfPositionalArgs = 2, numOfPositionalOnlyArgs = 1, parameterNames = {"self", "other", "context"}, takesVarKeywordArgs = true)
    @GenerateNodeFactory
    public abstract static class ScalebNode extends PythonBuiltinNode {
        @Specialization
        static Object doMock(VirtualFrame frame, Object self, Object other, Object context, PKeyword[] kwargs,
                        @Bind Node inliningTarget,
                        @Cached PRaiseNode raiseNode) {
            // TODO implement Decimal.scaleb.
            throw raiseNode.raise(inliningTarget, NotImplementedError);
        }
    }

    @Builtin(name = "shift", minNumOfPositionalArgs = 2, numOfPositionalOnlyArgs = 1, parameterNames = {"self", "other", "context"}, takesVarKeywordArgs = true)
    @GenerateNodeFactory
    public abstract static class ShiftNode extends PythonBuiltinNode {
        @Specialization
        static Object doMock(VirtualFrame frame, Object self, Object other, Object context, PKeyword[] kwargs,
                        @Bind Node inliningTarget,
                        @Cached PRaiseNode raiseNode) {
            // TODO implement Decimal.shift.
            throw raiseNode.raise(inliningTarget, NotImplementedError);
        }
    }

    @Builtin(name = "from_float", minNumOfPositionalArgs = 2, isClassmethod = true)
    @GenerateNodeFactory
    public abstract static class FromFloatNode extends PythonBinaryBuiltinNode {
        @Specialization
        static Object doMock(VirtualFrame frame, Object self, Object arg1,
                        @Bind Node inliningTarget,
                        @Cached PRaiseNode raiseNode) {
            // TODO implement Decimal.from_float.
            throw raiseNode.raise(inliningTarget, NotImplementedError);
        }
    }

    @Builtin(name = "as_tuple", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class AsTupleNode extends PythonUnaryBuiltinNode {
        @Specialization
        static Object doMock(VirtualFrame frame, Object self,
                        @Bind Node inliningTarget,
                        @Cached PRaiseNode raiseNode) {
            // TODO implement Decimal.as_tuple.
            throw raiseNode.raise(inliningTarget, NotImplementedError);
        }
    }

    @Builtin(name = "as_integer_ratio", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class AsIntegerRatioNode extends PythonUnaryBuiltinNode {
        @Specialization
        static Object doMock(VirtualFrame frame, Object self,
                        @Bind Node inliningTarget,
                        @Cached PRaiseNode raiseNode) {
            // TODO implement Decimal.as_integer_ratio.
            throw raiseNode.raise(inliningTarget, NotImplementedError);
        }
    }

    @Builtin(name = "__copy__", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class CopyNode extends PythonUnaryBuiltinNode {
        @Specialization
        static Object doMock(VirtualFrame frame, Object self,
                        @Bind Node inliningTarget,
                        @Cached PRaiseNode raiseNode) {
            // TODO implement Decimal.__copy__.
            throw raiseNode.raise(inliningTarget, NotImplementedError);
        }
    }

    @Builtin(name = "__deepcopy__", minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    public abstract static class DeepcopyNode extends PythonBinaryBuiltinNode {
        @Specialization
        static Object doMock(VirtualFrame frame, Object self, Object arg1,
                        @Bind Node inliningTarget,
                        @Cached PRaiseNode raiseNode) {
            // TODO implement Decimal.__deepcopy__.
            throw raiseNode.raise(inliningTarget, NotImplementedError);
        }
    }

    @Builtin(name = "__format__", minNumOfPositionalArgs = 2, maxNumOfPositionalArgs = 3)
    @GenerateNodeFactory
    public abstract static class FormatNode extends PythonTernaryBuiltinNode {
        @Specialization
        static Object doMock(VirtualFrame frame, Object self, Object arg1, Object arg2,
                        @Bind Node inliningTarget,
                        @Cached PRaiseNode raiseNode) {
            // TODO implement Decimal.__format__.
            throw raiseNode.raise(inliningTarget, NotImplementedError);
        }
    }

    @Builtin(name = "__reduce__", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class ReduceNode extends PythonUnaryBuiltinNode {
        @Specialization
        static Object doMock(VirtualFrame frame, Object self,
                        @Bind Node inliningTarget,
                        @Cached PRaiseNode raiseNode) {
            // TODO implement Decimal.__reduce__.
            throw raiseNode.raise(inliningTarget, NotImplementedError);
        }
    }

    @Builtin(name = "__round__", minNumOfPositionalArgs = 1, maxNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    public abstract static class RoundNode extends PythonBinaryBuiltinNode {
        @Specialization
        static Object doMock(VirtualFrame frame, Object self, Object arg1,
                        @Bind Node inliningTarget,
                        @Cached PRaiseNode raiseNode) {
            // TODO implement Decimal.__round__.
            throw raiseNode.raise(inliningTarget, NotImplementedError);
        }
    }

    @Builtin(name = "__ceil__", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class CeilNode extends PythonUnaryBuiltinNode {
        @Specialization
        static Object doMock(VirtualFrame frame, Object self,
                        @Bind Node inliningTarget,
                        @Cached PRaiseNode raiseNode) {
            // TODO implement Decimal.__ceil__.
            throw raiseNode.raise(inliningTarget, NotImplementedError);
        }
    }

    @Builtin(name = "__floor__", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class FloorNode extends PythonUnaryBuiltinNode {
        @Specialization
        static Object doMock(VirtualFrame frame, Object self,
                        @Bind Node inliningTarget,
                        @Cached PRaiseNode raiseNode) {
            // TODO implement Decimal.__floor__.
            throw raiseNode.raise(inliningTarget, NotImplementedError);
        }
    }

    @Builtin(name = "__trunc__", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class TruncNode extends PythonUnaryBuiltinNode {
        @Specialization
        static Object doMock(VirtualFrame frame, Object self,
                        @Bind Node inliningTarget,
                        @Cached PRaiseNode raiseNode) {
            // TODO implement Decimal.__trunc__.
            throw raiseNode.raise(inliningTarget, NotImplementedError);
        }
    }

    @Builtin(name = "__complex__", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class ComplexNode extends PythonUnaryBuiltinNode {
        @Specialization
        static Object doMock(VirtualFrame frame, Object self,
                        @Bind Node inliningTarget,
                        @Cached PRaiseNode raiseNode) {
            // TODO implement Decimal.__complex__.
            throw raiseNode.raise(inliningTarget, NotImplementedError);
        }
    }

    @Builtin(name = "__sizeof__", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class SizeofNode extends PythonUnaryBuiltinNode {
        @Specialization
        static Object doMock(VirtualFrame frame, Object self,
                        @Bind Node inliningTarget,
                        @Cached PRaiseNode raiseNode) {
            // TODO implement Decimal.__sizeof__.
            throw raiseNode.raise(inliningTarget, NotImplementedError);
        }
    }

    @Builtin(name = "real", minNumOfPositionalArgs = 1, isGetter = true)
    @GenerateNodeFactory
    public abstract static class RealNode extends PythonUnaryBuiltinNode {
        @Specialization
        static Object doMock(VirtualFrame frame, Object self,
                        @Bind Node inliningTarget,
                        @Cached PRaiseNode raiseNode) {
            // TODO implement Decimal.real.
            throw raiseNode.raise(inliningTarget, NotImplementedError);
        }
    }

    @Builtin(name = "imag", minNumOfPositionalArgs = 1, isGetter = true)
    @GenerateNodeFactory
    public abstract static class ImagNode extends PythonUnaryBuiltinNode {
        @Specialization
        static Object doMock(VirtualFrame frame, Object self,
                        @Bind Node inliningTarget,
                        @Cached PRaiseNode raiseNode) {
            // TODO implement Decimal.imag.
            throw raiseNode.raise(inliningTarget, NotImplementedError);
        }
    }

    @Slot(value = SlotKind.tp_new, isComplex = true)
    @SlotSignature(name = "Decimal", minNumOfPositionalArgs = 1, numOfPositionalOnlyArgs = 1, parameterNames = {"cls", "value", "context"})
    @GenerateNodeFactory
    public abstract static class TpNewNode extends PythonTernaryBuiltinNode {
        @Specialization
        static Object doMock(VirtualFrame frame, Object self, Object arg1, Object arg2,
                        @Bind Node inliningTarget,
                        @Cached PRaiseNode raiseNode) {
            // TODO implement Decimal.tp_new.
            throw raiseNode.raise(inliningTarget, NotImplementedError);
        }
    }

    @Slot(value = SlotKind.tp_repr, isComplex = true)
    @GenerateNodeFactory
    public abstract static class TpReprNode extends PythonUnaryBuiltinNode {
        @Specialization
        static Object doMock(VirtualFrame frame, Object self,
                        @Bind Node inliningTarget,
                        @Cached PRaiseNode raiseNode) {
            // TODO implement Decimal.tp_repr.
            throw raiseNode.raise(inliningTarget, NotImplementedError);
        }
    }

    @Slot(value = SlotKind.tp_str, isComplex = true)
    @GenerateNodeFactory
    public abstract static class TpStrNode extends PythonUnaryBuiltinNode {
        @Specialization
        static Object doMock(VirtualFrame frame, Object self,
                        @Bind Node inliningTarget,
                        @Cached PRaiseNode raiseNode) {
            // TODO implement Decimal.tp_str.
            throw raiseNode.raise(inliningTarget, NotImplementedError);
        }
    }

    @Slot(value = SlotKind.nb_negative, isComplex = true)
    @GenerateNodeFactory
    public abstract static class NbNegativeNode extends PythonUnaryBuiltinNode {
        @Specialization
        static Object doMock(VirtualFrame frame, Object self,
                        @Bind Node inliningTarget,
                        @Cached PRaiseNode raiseNode) {
            // TODO implement Decimal.nb_negative.
            throw raiseNode.raise(inliningTarget, NotImplementedError);
        }
    }

    @Slot(value = SlotKind.nb_positive, isComplex = true)
    @GenerateNodeFactory
    public abstract static class NbPositiveNode extends PythonUnaryBuiltinNode {
        @Specialization
        static Object doMock(VirtualFrame frame, Object self,
                        @Bind Node inliningTarget,
                        @Cached PRaiseNode raiseNode) {
            // TODO implement Decimal.nb_positive.
            throw raiseNode.raise(inliningTarget, NotImplementedError);
        }
    }

    @Slot(value = SlotKind.nb_absolute, isComplex = true)
    @GenerateNodeFactory
    public abstract static class NbAbsoluteNode extends PythonUnaryBuiltinNode {
        @Specialization
        static Object doMock(VirtualFrame frame, Object self,
                        @Bind Node inliningTarget,
                        @Cached PRaiseNode raiseNode) {
            // TODO implement Decimal.nb_absolute.
            throw raiseNode.raise(inliningTarget, NotImplementedError);
        }
    }

    @Slot(value = SlotKind.nb_int, isComplex = true)
    @GenerateNodeFactory
    public abstract static class NbIntNode extends PythonUnaryBuiltinNode {
        @Specialization
        static Object doMock(VirtualFrame frame, Object self,
                        @Bind Node inliningTarget,
                        @Cached PRaiseNode raiseNode) {
            // TODO implement Decimal.nb_int.
            throw raiseNode.raise(inliningTarget, NotImplementedError);
        }
    }

    @Slot(value = SlotKind.nb_float, isComplex = true)
    @GenerateNodeFactory
    public abstract static class NbFloatNode extends PythonUnaryBuiltinNode {
        @Specialization
        static Object doMock(VirtualFrame frame, Object self,
                        @Bind Node inliningTarget,
                        @Cached PRaiseNode raiseNode) {
            // TODO implement Decimal.nb_float.
            throw raiseNode.raise(inliningTarget, NotImplementedError);
        }
    }

    @Slot(value = SlotKind.tp_getattro, isComplex = true)
    @GenerateNodeFactory
    public abstract static class TpGetattroNode extends GetAttrBuiltinNode {
        @Specialization
        static Object doMock(VirtualFrame frame, Object self, Object arg1,
                        @Bind Node inliningTarget,
                        @Cached PRaiseNode raiseNode) {
            // TODO implement Decimal.tp_getattro.
            throw raiseNode.raise(inliningTarget, NotImplementedError);
        }
    }

    @Slot(value = SlotKind.tp_hash, isComplex = true)
    @GenerateNodeFactory
    public abstract static class TpHashNode extends HashBuiltinNode {
        @Specialization
        static long doMock(VirtualFrame frame, Object self,
                        @Bind Node inliningTarget,
                        @Cached PRaiseNode raiseNode) {
            // TODO implement Decimal.tp_hash.
            throw raiseNode.raise(inliningTarget, NotImplementedError);
        }
    }

    @Slot(value = SlotKind.tp_richcompare, isComplex = true)
    @GenerateNodeFactory
    public abstract static class TpRichcompareNode extends RichCmpBuiltinNode {
        @Specialization
        static Object doMock(VirtualFrame frame, Object self, Object other, RichCmpOp op,
                        @Bind Node inliningTarget,
                        @Cached PRaiseNode raiseNode) {
            // TODO implement Decimal.tp_richcompare.
            throw raiseNode.raise(inliningTarget, NotImplementedError);
        }
    }

    @Slot(value = SlotKind.nb_add, isComplex = true)
    @GenerateNodeFactory
    public abstract static class NbAddNode extends BinaryOpBuiltinNode {
        @Specialization
        static Object doMock(VirtualFrame frame, Object self, Object arg1,
                        @Bind Node inliningTarget,
                        @Cached PRaiseNode raiseNode) {
            // TODO implement Decimal.nb_add.
            throw raiseNode.raise(inliningTarget, NotImplementedError);
        }
    }

    @Slot(value = SlotKind.nb_subtract, isComplex = true)
    @GenerateNodeFactory
    public abstract static class NbSubtractNode extends BinaryOpBuiltinNode {
        @Specialization
        static Object doMock(VirtualFrame frame, Object self, Object arg1,
                        @Bind Node inliningTarget,
                        @Cached PRaiseNode raiseNode) {
            // TODO implement Decimal.nb_subtract.
            throw raiseNode.raise(inliningTarget, NotImplementedError);
        }
    }

    @Slot(value = SlotKind.nb_multiply, isComplex = true)
    @GenerateNodeFactory
    public abstract static class NbMultiplyNode extends BinaryOpBuiltinNode {
        @Specialization
        static Object doMock(VirtualFrame frame, Object self, Object arg1,
                        @Bind Node inliningTarget,
                        @Cached PRaiseNode raiseNode) {
            // TODO implement Decimal.nb_multiply.
            throw raiseNode.raise(inliningTarget, NotImplementedError);
        }
    }

    @Slot(value = SlotKind.nb_remainder, isComplex = true)
    @GenerateNodeFactory
    public abstract static class NbRemainderNode extends BinaryOpBuiltinNode {
        @Specialization
        static Object doMock(VirtualFrame frame, Object self, Object arg1,
                        @Bind Node inliningTarget,
                        @Cached PRaiseNode raiseNode) {
            // TODO implement Decimal.nb_remainder.
            throw raiseNode.raise(inliningTarget, NotImplementedError);
        }
    }

    @Slot(value = SlotKind.nb_divmod, isComplex = true)
    @GenerateNodeFactory
    public abstract static class NbDivmodNode extends BinaryOpBuiltinNode {
        @Specialization
        static Object doMock(VirtualFrame frame, Object self, Object arg1,
                        @Bind Node inliningTarget,
                        @Cached PRaiseNode raiseNode) {
            // TODO implement Decimal.nb_divmod.
            throw raiseNode.raise(inliningTarget, NotImplementedError);
        }
    }

    @Slot(value = SlotKind.nb_floor_divide, isComplex = true)
    @GenerateNodeFactory
    public abstract static class NbFloorDivideNode extends BinaryOpBuiltinNode {
        @Specialization
        static Object doMock(VirtualFrame frame, Object self, Object arg1,
                        @Bind Node inliningTarget,
                        @Cached PRaiseNode raiseNode) {
            // TODO implement Decimal.nb_floor_divide.
            throw raiseNode.raise(inliningTarget, NotImplementedError);
        }
    }

    @Slot(value = SlotKind.nb_true_divide, isComplex = true)
    @GenerateNodeFactory
    public abstract static class NbTrueDivideNode extends BinaryOpBuiltinNode {
        @Specialization
        static Object doMock(VirtualFrame frame, Object self, Object arg1,
                        @Bind Node inliningTarget,
                        @Cached PRaiseNode raiseNode) {
            // TODO implement Decimal.nb_true_divide.
            throw raiseNode.raise(inliningTarget, NotImplementedError);
        }
    }

    @Slot(value = SlotKind.nb_power, isComplex = true)
    @GenerateNodeFactory
    public abstract static class NbPowerNode extends PythonTernaryBuiltinNode {
        @Specialization
        static Object doMock(VirtualFrame frame, Object self, Object arg1, Object arg2,
                        @Bind Node inliningTarget,
                        @Cached PRaiseNode raiseNode) {
            // TODO implement Decimal.nb_power.
            throw raiseNode.raise(inliningTarget, NotImplementedError);
        }
    }

    @Slot(value = SlotKind.nb_bool, isComplex = true)
    @GenerateNodeFactory
    public abstract static class NbBoolNode extends NbBoolBuiltinNode {
        @Specialization
        static boolean doMock(VirtualFrame frame, Object self,
                        @Bind Node inliningTarget,
                        @Cached PRaiseNode raiseNode) {
            // TODO implement Decimal.nb_bool.
            throw raiseNode.raise(inliningTarget, NotImplementedError);
        }
    }
}
