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
import static com.oracle.graal.python.builtins.PythonBuiltinClassType.TypeError;
import static com.oracle.graal.python.builtins.PythonBuiltinClassType.ValueError;
import static com.oracle.graal.python.nodes.BuiltinNames.T__DECIMAL;
import static com.oracle.graal.python.nodes.PGuards.isNoValue;
import static com.oracle.graal.python.nodes.PGuards.isNone;
import static com.oracle.graal.python.util.PythonUtils.TS_ENCODING;

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
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.common.SequenceStorageNodes;
import com.oracle.graal.python.builtins.objects.decimal.DecimalUtils.ConversionException;
import com.oracle.graal.python.builtins.objects.decimal.DecimalUtils.Value;
import com.oracle.graal.python.builtins.objects.decimal.PDecimal.Kind;
import com.oracle.graal.python.builtins.objects.floats.PFloat;
import com.oracle.graal.python.builtins.objects.function.PKeyword;
import com.oracle.graal.python.builtins.objects.ints.PInt;
import com.oracle.graal.python.builtins.objects.list.PList;
import com.oracle.graal.python.builtins.objects.type.TpSlots;
import com.oracle.graal.python.builtins.objects.type.TypeNodes;
import com.oracle.graal.python.builtins.objects.type.slots.TpSlotBinaryOp.BinaryOpBuiltinNode;
import com.oracle.graal.python.builtins.objects.type.slots.TpSlotGetAttr.GetAttrBuiltinNode;
import com.oracle.graal.python.builtins.objects.type.slots.TpSlotHashFun.HashBuiltinNode;
import com.oracle.graal.python.builtins.objects.type.slots.TpSlotInquiry.NbBoolBuiltinNode;
import com.oracle.graal.python.builtins.objects.type.slots.TpSlotRichCompare.RichCmpBuiltinNode;
import com.oracle.graal.python.lib.PyFloatAsDoubleNode;
import com.oracle.graal.python.lib.PyFloatCheckNode;
import com.oracle.graal.python.lib.PyListCheckNode;
import com.oracle.graal.python.lib.PyLongAsLongNode;
import com.oracle.graal.python.lib.PyLongCheckNode;
import com.oracle.graal.python.lib.PyTupleCheckNode;
import com.oracle.graal.python.lib.PyUnicodeCheckNode;
import com.oracle.graal.python.lib.RichCmpOp;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PGuards;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.builtins.ListNodes.GetListStorageNode;
import com.oracle.graal.python.nodes.builtins.TupleNodes.GetTupleStorage;
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
import com.oracle.graal.python.runtime.ExecutionContext.BoundaryCallContext;
import com.oracle.graal.python.runtime.IndirectCallData.BoundaryCallData;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.graal.python.runtime.object.PFactory;
import com.oracle.graal.python.runtime.sequence.storage.SequenceStorage;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.GenerateCached;
import com.oracle.truffle.api.dsl.GenerateInline;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.profiles.BranchProfile;
import com.oracle.truffle.api.profiles.InlinedBranchProfile;
import com.oracle.truffle.api.strings.TruffleString;

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
        static PDecimal construct(VirtualFrame frame, Object cls, Object value, Object context,
                        @Bind Node inliningTarget,
                        @Bind PythonLanguage language,
                        @Cached(inline = true) CreateDecimalNode createDecimal,
                        @Cached CallNode callNode,
                        @Cached PRaiseNode raiseNode,
                        @Cached BranchProfile errorProfile) {
            if (!isNone(context) && !isNoValue(context) && !(context instanceof PDecimalContext)) {
                throw raiseNode.raise(inliningTarget, TypeError, ErrorMessages.OPTIONAL_DECIMAL_CONTEXT);
            }
            try {
                return createDecimal.execute(frame, inliningTarget, cls, value);
            } catch (ConversionException e) {
                errorProfile.enter();
                // Until contexts have flags/traps, use the default InvalidOperation trap.
                DecimalModuleState state = PythonContext.get(inliningTarget).lookupBuiltinModule(T__DECIMAL).getModuleState(DecimalModuleState.class);
                PList conditions = PFactory.createList(language, new Object[]{state.getExceptionType(e.condition)});
                Object exception = callNode.execute(frame, state.getExceptionType(DecimalExceptionTypes.InvalidOperation), conditions);
                throw PRaiseNode.raiseExceptionObjectStatic(inliningTarget, exception);
            }
        }

        @GenerateInline
        @GenerateCached(false)
        @GenerateUncached
        @ImportStatic(PGuards.class)
        abstract static class CreateDecimalNode extends Node {
            abstract PDecimal execute(VirtualFrame frame, Node inliningTarget, Object cls, Object value) throws ConversionException;

            @Specialization(guards = "isNoValue(value)")
            static PDecimal doDefault(@SuppressWarnings("unused") Node inliningTarget, Object cls, @SuppressWarnings("unused") PNone value,
                            @Shared @Cached TypeNodes.GetInstanceShape getShape) {
                return PFactory.createDecimal(cls, getShape.execute(cls), 0L);
            }

            @Specialization
            static PDecimal doDecimal(Node inliningTarget, Object cls, PDecimal value,
                            @Shared @Cached GetClassNode getClass,
                            @Shared @Cached IsBuiltinClassExactProfile clsProfile,
                            @Shared @Cached IsBuiltinClassExactProfile valueProfile,
                            @Shared @Cached TypeNodes.GetInstanceShape getShape) {
                if (clsProfile.profileClass(inliningTarget, cls, PythonBuiltinClassType.PDecimal) &&
                                valueProfile.profileClass(inliningTarget, getClass.execute(inliningTarget, value), PythonBuiltinClassType.PDecimal)) {
                    return value;
                }
                return PFactory.createDecimal(cls, getShape.execute(cls), value);
            }

            @Specialization(guards = "unicodeCheck.execute(inliningTarget, value)")
            static PDecimal doString(Node inliningTarget, Object cls, Object value,
                            @SuppressWarnings("unused") @Shared @Cached PyUnicodeCheckNode unicodeCheck,
                            @Cached CastToJavaStringNode asString,
                            @Shared @Cached TypeNodes.GetInstanceShape getShape) throws ConversionException {
                return create(cls, DecimalUtils.parse(asString.execute(value)), getShape);
            }

            @Specialization
            static PDecimal doInt(@SuppressWarnings("unused") Node inliningTarget, Object cls, int value,
                            @Shared @Cached TypeNodes.GetInstanceShape getShape) {
                return PFactory.createDecimal(cls, getShape.execute(cls), value);
            }

            @Specialization
            static PDecimal doLong(@SuppressWarnings("unused") Node inliningTarget, Object cls, long value,
                            @Shared @Cached TypeNodes.GetInstanceShape getShape) {
                return PFactory.createDecimal(cls, getShape.execute(cls), value);
            }

            @Specialization
            static PDecimal doPInt(@SuppressWarnings("unused") Node inliningTarget, Object cls, PInt value,
                            @Shared @Cached TypeNodes.GetInstanceShape getShape) {
                return create(cls, DecimalUtils.fromInteger(value.getValue()), getShape);
            }

            @Specialization(guards = "longCheck.execute(inliningTarget, value)")
            static PDecimal doIntGeneric(Node inliningTarget, Object cls, Object value,
                            @SuppressWarnings("unused") @Shared @Cached PyLongCheckNode longCheck,
                            @Shared @Cached CastToJavaBigIntegerNode asBigInteger,
                            @Shared @Cached TypeNodes.GetInstanceShape getShape) {
                return create(cls, DecimalUtils.fromInteger(asBigInteger.execute(inliningTarget, value)), getShape);
            }

            @Specialization
            static PDecimal doDouble(Node inliningTarget, Object cls, double value,
                            @Shared @Cached InlinedBranchProfile nonCompactProfile,
                            @Shared @Cached TypeNodes.GetInstanceShape getShape) {
                // TODO signal FloatOperation when decimal contexts and signals are implemented.
                return fromDouble(inliningTarget, cls, value, getShape, nonCompactProfile);
            }

            @Specialization
            static PDecimal doPFloat(Node inliningTarget, Object cls, PFloat value,
                            @Shared @Cached InlinedBranchProfile nonCompactProfile,
                            @Shared @Cached TypeNodes.GetInstanceShape getShape) {
                return fromDouble(inliningTarget, cls, value.getValue(), getShape, nonCompactProfile);
            }

            @Specialization(guards = "floatCheck.execute(inliningTarget, value)")
            static PDecimal doFloatGeneric(VirtualFrame frame, Node inliningTarget, Object cls, Object value,
                            @SuppressWarnings("unused") @Shared @Cached PyFloatCheckNode floatCheck,
                            @Shared @Cached PyFloatAsDoubleNode asDouble,
                            @Shared @Cached InlinedBranchProfile nonCompactProfile,
                            @Shared @Cached TypeNodes.GetInstanceShape getShape) {
                return fromDouble(inliningTarget, cls, asDouble.execute(frame, inliningTarget, value), getShape, nonCompactProfile);
            }

            @Specialization(guards = "tupleCheck.execute(inliningTarget, value)")
            static PDecimal doTuple(VirtualFrame frame, Node inliningTarget, Object cls, Object value,
                            @SuppressWarnings("unused") @Shared @Cached PyTupleCheckNode tupleCheck,
                            @Shared @Cached GetTupleStorage getTupleStorage,
                            @Shared @Cached("createFor($node)") BoundaryCallData boundaryCallData,
                            @Shared @Cached TypeNodes.GetInstanceShape getShape) throws ConversionException {
                SequenceStorage storage = getTupleStorage.execute(inliningTarget, value);
                Object state = BoundaryCallContext.enter(frame, boundaryCallData);
                try {
                    return create(cls, fromSequence(storage), getShape);
                } finally {
                    BoundaryCallContext.exit(frame, boundaryCallData, state);
                }
            }

            @Specialization(guards = "listCheck.execute(inliningTarget, value)")
            static PDecimal doList(VirtualFrame frame, Node inliningTarget, Object cls, Object value,
                            @SuppressWarnings("unused") @Shared @Cached PyListCheckNode listCheck,
                            @Shared @Cached GetListStorageNode getListStorage,
                            @Shared @Cached("createFor($node)") BoundaryCallData boundaryCallData,
                            @Shared @Cached TypeNodes.GetInstanceShape getShape) throws ConversionException {
                SequenceStorage storage = getListStorage.execute(inliningTarget, value);
                Object state = BoundaryCallContext.enter(frame, boundaryCallData);
                try {
                    return create(cls, fromSequence(storage), getShape);
                } finally {
                    BoundaryCallContext.exit(frame, boundaryCallData, state);
                }
            }

            @Fallback
            static PDecimal doUnsupported(Node inliningTarget, @SuppressWarnings("unused") Object cls, Object value,
                            @Shared @Cached GetClassNode getClass,
                            @Shared @Cached TypeNodes.GetNameNode getName,
                            @Shared @Cached PRaiseNode raiseNode) {
                throw raiseNode.raise(inliningTarget, TypeError, ErrorMessages.CONVERSION_FROM_S_TO_DECIMAL_NOT_SUPPORTED,
                                getName.execute(inliningTarget, getClass.execute(inliningTarget, value)));
            }

            private static PDecimal create(Object cls, Value value, TypeNodes.GetInstanceShape getShape) {
                return PFactory.createDecimal(cls, getShape.execute(cls), value.negative(), value.coefficient(), value.exponent(), value.kind());
            }

            private static PDecimal fromDouble(Node inliningTarget, Object cls, double value, TypeNodes.GetInstanceShape getShape, InlinedBranchProfile nonCompactProfile) {
                // IEEE 754 binary64 stores the sign in bit 63, an 11-bit biased exponent
                // in bits 62..52, and a 52-bit fraction in bits 51..0. The masks select
                long bits = Double.doubleToRawLongBits(value);
                boolean negative = bits < 0;
                int exponentBits = (int) ((bits >>> 52) & 0x7ff);
                long coefficient = bits & 0x000fffffffffffffL;
                // An all-ones exponent encodes infinity if the fraction is zero, else NaN.
                if (exponentBits == 0x7ff) {
                    Kind kind = coefficient == 0 ? Kind.INFINITY : Kind.NAN;
                    return PFactory.createDecimal(cls, getShape.execute(cls), kind == Kind.INFINITY && negative, 0L, 0, kind);
                }
                if (exponentBits == 0 && coefficient == 0) {
                    return PFactory.createDecimal(cls, getShape.execute(cls), negative, 0L, 0, Kind.FINITE);
                }
                int binaryExponent;
                if (exponentBits == 0) {
                    // Subnormals have no implicit leading 1. Their integer fraction is
                    // scaled by 2^(1 - 1023 - 52) = 2^-1074 (bias 1023, 52 fraction bits).
                    binaryExponent = -1074;
                } else {
                    // Normal values have an implicit leading 1 at bit 52. Subtract the
                    // exponent bias (1023) and fraction width (52) to scale this integer.
                    coefficient |= 1L << 52;
                    binaryExponent = exponentBits - 1023 - 52;
                }
                // Reduce coefficient * 2^binaryExponent to an odd coefficient.
                int trailingZeros = Long.numberOfTrailingZeros(coefficient);
                coefficient >>>= trailingZeros;
                binaryExponent += trailingZeros;
                if (binaryExponent >= 0) {
                    // A compact positive coefficient has 63 usable bits. Check the shift
                    // count and remaining capacity before multiplying by 2^binaryExponent.
                    if (binaryExponent < Long.SIZE - 1 && coefficient <= (Long.MAX_VALUE >>> binaryExponent)) {
                        return PFactory.createDecimal(cls, getShape.execute(cls), negative, coefficient << binaryExponent, 0, Kind.FINITE);
                    }
                } else {
                    // n / 2^k = (n * 5^k) * 10^-k because 2 * 5 = 10, with no decimal
                    // rounding. Long.MAX_VALUE / 5 is the largest coefficient that can
                    // survive the next multiplication by 5 without signed-long overflow.
                    for (int i = 0; i < -binaryExponent; i++) {
                        if (coefficient > Long.MAX_VALUE / 5) {
                            nonCompactProfile.enter(inliningTarget);
                            return create(cls, DecimalUtils.fromFloat(value), getShape);
                        }
                        coefficient *= 5;
                    }
                    return PFactory.createDecimal(cls, getShape.execute(cls), negative, coefficient, binaryExponent, Kind.FINITE);
                }
                nonCompactProfile.enter(inliningTarget);
                return create(cls, DecimalUtils.fromFloat(value), getShape);
            }

            @TruffleBoundary
            private static Value fromSequence(SequenceStorage triple) throws ConversionException {
                PRaiseNode raiseNode = PRaiseNode.getUncached();
                // Decimal triples are (sign, digits, exponent), at indices 0, 1, and 2.
                if (triple.length() != 3) {
                    throw raiseNode.raise(null, ValueError, ErrorMessages.DECIMAL_SEQUENCE_LENGTH);
                }
                Object sign = SequenceStorageNodes.GetItemScalarNode.executeUncached(triple, 0);
                if (!PyLongCheckNode.executeUncached(sign)) {
                    throw raiseNode.raise(null, ValueError, ErrorMessages.DECIMAL_SIGN);
                }
                long signValue = PyLongAsLongNode.executeUncached(sign);
                // The tuple format uses 0 for positive and 1 for negative, including zero.
                if (signValue != 0 && signValue != 1) {
                    throw raiseNode.raise(null, ValueError, ErrorMessages.DECIMAL_SIGN);
                }
                Object exp = SequenceStorageNodes.GetItemScalarNode.executeUncached(triple, 2);
                Kind kind = Kind.FINITE;
                long exponent = 0;
                if (PyUnicodeCheckNode.executeUncached(exp)) {
                    kind = switch (CastToJavaStringNode.getUncached().execute(exp)) {
                        case "F" -> Kind.INFINITY;
                        case "n" -> Kind.NAN;
                        case "N" -> Kind.SNAN;
                        default -> throw raiseNode.raise(null, ValueError, ErrorMessages.DECIMAL_SPECIAL_EXPONENT);
                    };
                } else {
                    if (!PyLongCheckNode.executeUncached(exp)) {
                        throw raiseNode.raise(null, ValueError, ErrorMessages.DECIMAL_EXPONENT);
                    }
                    exponent = PyLongAsLongNode.executeUncached(exp);
                }
                Object digits = SequenceStorageNodes.GetItemScalarNode.executeUncached(triple, 1);
                SequenceStorage storage;
                if (PyTupleCheckNode.executeUncached(digits)) {
                    storage = GetTupleStorage.executeUncached(digits);
                } else if (PyListCheckNode.executeUncached(digits)) {
                    storage = GetListStorageNode.executeUncached(digits);
                } else {
                    throw raiseNode.raise(null, ValueError, ErrorMessages.DECIMAL_COEFFICIENT);
                }
                StringBuilder coefficient = new StringBuilder(storage.length());
                for (int i = 0; i < storage.length(); i++) {
                    Object digit = SequenceStorageNodes.GetItemScalarNode.executeUncached(storage, i);
                    if (!PyLongCheckNode.executeUncached(digit)) {
                        throw raiseNode.raise(null, ValueError, ErrorMessages.DECIMAL_COEFFICIENT);
                    }
                    long n = PyLongAsLongNode.executeUncached(digit);
                    // Each coefficient element is one base-10 digit, in the range 0..9.
                    if (n < 0 || n > 9) {
                        throw raiseNode.raise(null, ValueError, ErrorMessages.DECIMAL_COEFFICIENT);
                    }
                    if (kind != Kind.INFINITY) {
                        coefficient.append((char) ('0' + n));
                    }
                }
                BigInteger integer = coefficient.isEmpty() ? BigInteger.ZERO : new BigInteger(coefficient.toString());
                return kind == Kind.FINITE ? DecimalUtils.finite(signValue != 0, integer, exponent) : new Value(signValue != 0, integer, 0, kind);
            }
        }
    }

    @Slot(value = SlotKind.tp_repr, isComplex = true)
    @GenerateNodeFactory
    public abstract static class TpReprNode extends PythonUnaryBuiltinNode {
        @Specialization
        static TruffleString repr(PDecimal self,
                        @Cached TruffleString.FromJavaStringNode fromJavaString) {
            return fromJavaString.execute(reprString(self), TS_ENCODING);
        }

        @TruffleBoundary
        private static String reprString(PDecimal self) {
            // TODO read capitals from the current context once contexts are implemented.
            return "Decimal('" + self.toScientificString(true) + "')";
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
