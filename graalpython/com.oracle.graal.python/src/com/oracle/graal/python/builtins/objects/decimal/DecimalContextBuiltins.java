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

import static com.oracle.graal.python.builtins.PythonBuiltinClassType.AttributeError;
import static com.oracle.graal.python.builtins.PythonBuiltinClassType.NotImplementedError;
import static com.oracle.graal.python.builtins.PythonBuiltinClassType.TypeError;
import static com.oracle.graal.python.builtins.objects.decimal.DecimalContextUtils.T_FLAGS;
import static com.oracle.graal.python.builtins.objects.decimal.DecimalContextUtils.T_TRAPS;
import static com.oracle.graal.python.nodes.ErrorMessages.ATTR_NAME_MUST_BE_STRING;
import static com.oracle.graal.python.util.PythonUtils.TS_ENCODING;

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
import com.oracle.graal.python.builtins.objects.function.PKeyword;
import com.oracle.graal.python.builtins.objects.object.ObjectBuiltins;
import com.oracle.graal.python.builtins.objects.str.StringNodes.CastToTruffleStringChecked1Node;
import com.oracle.graal.python.builtins.objects.type.TpSlots;
import com.oracle.graal.python.builtins.objects.type.TypeNodes;
import com.oracle.graal.python.builtins.objects.type.slots.TpSlotGetAttr.GetAttrBuiltinNode;
import com.oracle.graal.python.builtins.objects.type.slots.TpSlotSetAttr.SetAttrBuiltinNode;
import com.oracle.graal.python.lib.PyLongAsLongNode;
import com.oracle.graal.python.lib.PyLongCheckNode;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonBinaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonQuaternaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonTernaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonUnaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonVarargsBuiltinNode;
import com.oracle.graal.python.nodes.object.GetClassNode;
import com.oracle.graal.python.runtime.object.PFactory;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.strings.TruffleString;

@CoreFunctions(extendClasses = PythonBuiltinClassType.PDecimalContext)
public final class DecimalContextBuiltins extends PythonBuiltins {
    public static final TpSlots SLOTS = DecimalContextBuiltinsSlotsGen.SLOTS;

    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return DecimalContextBuiltinsFactory.getFactories();
    }

    @Builtin(name = "abs", minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    public abstract static class AbsNode extends PythonBinaryBuiltinNode {
        @Specialization
        static Object doMock(VirtualFrame frame, Object self, Object arg1,
                        @Bind Node inliningTarget,
                        @Cached PRaiseNode raiseNode) {
            // TODO implement Context.abs.
            throw raiseNode.raise(inliningTarget, NotImplementedError);
        }
    }

    @Builtin(name = "exp", minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    public abstract static class ExpNode extends PythonBinaryBuiltinNode {
        @Specialization
        static Object doMock(VirtualFrame frame, Object self, Object arg1,
                        @Bind Node inliningTarget,
                        @Cached PRaiseNode raiseNode) {
            // TODO implement Context.exp.
            throw raiseNode.raise(inliningTarget, NotImplementedError);
        }
    }

    @Builtin(name = "ln", minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    public abstract static class LnNode extends PythonBinaryBuiltinNode {
        @Specialization
        static Object doMock(VirtualFrame frame, Object self, Object arg1,
                        @Bind Node inliningTarget,
                        @Cached PRaiseNode raiseNode) {
            // TODO implement Context.ln.
            throw raiseNode.raise(inliningTarget, NotImplementedError);
        }
    }

    @Builtin(name = "log10", minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    public abstract static class Log10Node extends PythonBinaryBuiltinNode {
        @Specialization
        static Object doMock(VirtualFrame frame, Object self, Object arg1,
                        @Bind Node inliningTarget,
                        @Cached PRaiseNode raiseNode) {
            // TODO implement Context.log10.
            throw raiseNode.raise(inliningTarget, NotImplementedError);
        }
    }

    @Builtin(name = "minus", minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    public abstract static class MinusNode extends PythonBinaryBuiltinNode {
        @Specialization
        static Object doMock(VirtualFrame frame, Object self, Object arg1,
                        @Bind Node inliningTarget,
                        @Cached PRaiseNode raiseNode) {
            // TODO implement Context.minus.
            throw raiseNode.raise(inliningTarget, NotImplementedError);
        }
    }

    @Builtin(name = "next_minus", minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    public abstract static class NextMinusNode extends PythonBinaryBuiltinNode {
        @Specialization
        static Object doMock(VirtualFrame frame, Object self, Object arg1,
                        @Bind Node inliningTarget,
                        @Cached PRaiseNode raiseNode) {
            // TODO implement Context.next_minus.
            throw raiseNode.raise(inliningTarget, NotImplementedError);
        }
    }

    @Builtin(name = "next_plus", minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    public abstract static class NextPlusNode extends PythonBinaryBuiltinNode {
        @Specialization
        static Object doMock(VirtualFrame frame, Object self, Object arg1,
                        @Bind Node inliningTarget,
                        @Cached PRaiseNode raiseNode) {
            // TODO implement Context.next_plus.
            throw raiseNode.raise(inliningTarget, NotImplementedError);
        }
    }

    @Builtin(name = "normalize", minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    public abstract static class NormalizeNode extends PythonBinaryBuiltinNode {
        @Specialization
        static Object doMock(VirtualFrame frame, Object self, Object arg1,
                        @Bind Node inliningTarget,
                        @Cached PRaiseNode raiseNode) {
            // TODO implement Context.normalize.
            throw raiseNode.raise(inliningTarget, NotImplementedError);
        }
    }

    @Builtin(name = "plus", minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    public abstract static class PlusNode extends PythonBinaryBuiltinNode {
        @Specialization
        static Object doMock(VirtualFrame frame, Object self, Object arg1,
                        @Bind Node inliningTarget,
                        @Cached PRaiseNode raiseNode) {
            // TODO implement Context.plus.
            throw raiseNode.raise(inliningTarget, NotImplementedError);
        }
    }

    @Builtin(name = "to_integral", minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    public abstract static class ToIntegralNode extends PythonBinaryBuiltinNode {
        @Specialization
        static Object doMock(VirtualFrame frame, Object self, Object arg1,
                        @Bind Node inliningTarget,
                        @Cached PRaiseNode raiseNode) {
            // TODO implement Context.to_integral.
            throw raiseNode.raise(inliningTarget, NotImplementedError);
        }
    }

    @Builtin(name = "to_integral_exact", minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    public abstract static class ToIntegralExactNode extends PythonBinaryBuiltinNode {
        @Specialization
        static Object doMock(VirtualFrame frame, Object self, Object arg1,
                        @Bind Node inliningTarget,
                        @Cached PRaiseNode raiseNode) {
            // TODO implement Context.to_integral_exact.
            throw raiseNode.raise(inliningTarget, NotImplementedError);
        }
    }

    @Builtin(name = "to_integral_value", minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    public abstract static class ToIntegralValueNode extends PythonBinaryBuiltinNode {
        @Specialization
        static Object doMock(VirtualFrame frame, Object self, Object arg1,
                        @Bind Node inliningTarget,
                        @Cached PRaiseNode raiseNode) {
            // TODO implement Context.to_integral_value.
            throw raiseNode.raise(inliningTarget, NotImplementedError);
        }
    }

    @Builtin(name = "sqrt", minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    public abstract static class SqrtNode extends PythonBinaryBuiltinNode {
        @Specialization
        static Object doMock(VirtualFrame frame, Object self, Object arg1,
                        @Bind Node inliningTarget,
                        @Cached PRaiseNode raiseNode) {
            // TODO implement Context.sqrt.
            throw raiseNode.raise(inliningTarget, NotImplementedError);
        }
    }

    @Builtin(name = "add", minNumOfPositionalArgs = 3)
    @GenerateNodeFactory
    public abstract static class AddNode extends PythonTernaryBuiltinNode {
        @Specialization
        static Object doMock(VirtualFrame frame, Object self, Object arg1, Object arg2,
                        @Bind Node inliningTarget,
                        @Cached PRaiseNode raiseNode) {
            // TODO implement Context.add.
            throw raiseNode.raise(inliningTarget, NotImplementedError);
        }
    }

    @Builtin(name = "compare", minNumOfPositionalArgs = 3)
    @GenerateNodeFactory
    public abstract static class CompareNode extends PythonTernaryBuiltinNode {
        @Specialization
        static Object doMock(VirtualFrame frame, Object self, Object arg1, Object arg2,
                        @Bind Node inliningTarget,
                        @Cached PRaiseNode raiseNode) {
            // TODO implement Context.compare.
            throw raiseNode.raise(inliningTarget, NotImplementedError);
        }
    }

    @Builtin(name = "compare_signal", minNumOfPositionalArgs = 3)
    @GenerateNodeFactory
    public abstract static class CompareSignalNode extends PythonTernaryBuiltinNode {
        @Specialization
        static Object doMock(VirtualFrame frame, Object self, Object arg1, Object arg2,
                        @Bind Node inliningTarget,
                        @Cached PRaiseNode raiseNode) {
            // TODO implement Context.compare_signal.
            throw raiseNode.raise(inliningTarget, NotImplementedError);
        }
    }

    @Builtin(name = "divide", minNumOfPositionalArgs = 3)
    @GenerateNodeFactory
    public abstract static class DivideNode extends PythonTernaryBuiltinNode {
        @Specialization
        static Object doMock(VirtualFrame frame, Object self, Object arg1, Object arg2,
                        @Bind Node inliningTarget,
                        @Cached PRaiseNode raiseNode) {
            // TODO implement Context.divide.
            throw raiseNode.raise(inliningTarget, NotImplementedError);
        }
    }

    @Builtin(name = "divide_int", minNumOfPositionalArgs = 3)
    @GenerateNodeFactory
    public abstract static class DivideIntNode extends PythonTernaryBuiltinNode {
        @Specialization
        static Object doMock(VirtualFrame frame, Object self, Object arg1, Object arg2,
                        @Bind Node inliningTarget,
                        @Cached PRaiseNode raiseNode) {
            // TODO implement Context.divide_int.
            throw raiseNode.raise(inliningTarget, NotImplementedError);
        }
    }

    @Builtin(name = "divmod", minNumOfPositionalArgs = 3)
    @GenerateNodeFactory
    public abstract static class DivmodNode extends PythonTernaryBuiltinNode {
        @Specialization
        static Object doMock(VirtualFrame frame, Object self, Object arg1, Object arg2,
                        @Bind Node inliningTarget,
                        @Cached PRaiseNode raiseNode) {
            // TODO implement Context.divmod.
            throw raiseNode.raise(inliningTarget, NotImplementedError);
        }
    }

    @Builtin(name = "max", minNumOfPositionalArgs = 3)
    @GenerateNodeFactory
    public abstract static class MaxNode extends PythonTernaryBuiltinNode {
        @Specialization
        static Object doMock(VirtualFrame frame, Object self, Object arg1, Object arg2,
                        @Bind Node inliningTarget,
                        @Cached PRaiseNode raiseNode) {
            // TODO implement Context.max.
            throw raiseNode.raise(inliningTarget, NotImplementedError);
        }
    }

    @Builtin(name = "max_mag", minNumOfPositionalArgs = 3)
    @GenerateNodeFactory
    public abstract static class MaxMagNode extends PythonTernaryBuiltinNode {
        @Specialization
        static Object doMock(VirtualFrame frame, Object self, Object arg1, Object arg2,
                        @Bind Node inliningTarget,
                        @Cached PRaiseNode raiseNode) {
            // TODO implement Context.max_mag.
            throw raiseNode.raise(inliningTarget, NotImplementedError);
        }
    }

    @Builtin(name = "min", minNumOfPositionalArgs = 3)
    @GenerateNodeFactory
    public abstract static class MinNode extends PythonTernaryBuiltinNode {
        @Specialization
        static Object doMock(VirtualFrame frame, Object self, Object arg1, Object arg2,
                        @Bind Node inliningTarget,
                        @Cached PRaiseNode raiseNode) {
            // TODO implement Context.min.
            throw raiseNode.raise(inliningTarget, NotImplementedError);
        }
    }

    @Builtin(name = "min_mag", minNumOfPositionalArgs = 3)
    @GenerateNodeFactory
    public abstract static class MinMagNode extends PythonTernaryBuiltinNode {
        @Specialization
        static Object doMock(VirtualFrame frame, Object self, Object arg1, Object arg2,
                        @Bind Node inliningTarget,
                        @Cached PRaiseNode raiseNode) {
            // TODO implement Context.min_mag.
            throw raiseNode.raise(inliningTarget, NotImplementedError);
        }
    }

    @Builtin(name = "multiply", minNumOfPositionalArgs = 3)
    @GenerateNodeFactory
    public abstract static class MultiplyNode extends PythonTernaryBuiltinNode {
        @Specialization
        static Object doMock(VirtualFrame frame, Object self, Object arg1, Object arg2,
                        @Bind Node inliningTarget,
                        @Cached PRaiseNode raiseNode) {
            // TODO implement Context.multiply.
            throw raiseNode.raise(inliningTarget, NotImplementedError);
        }
    }

    @Builtin(name = "next_toward", minNumOfPositionalArgs = 3)
    @GenerateNodeFactory
    public abstract static class NextTowardNode extends PythonTernaryBuiltinNode {
        @Specialization
        static Object doMock(VirtualFrame frame, Object self, Object arg1, Object arg2,
                        @Bind Node inliningTarget,
                        @Cached PRaiseNode raiseNode) {
            // TODO implement Context.next_toward.
            throw raiseNode.raise(inliningTarget, NotImplementedError);
        }
    }

    @Builtin(name = "quantize", minNumOfPositionalArgs = 3)
    @GenerateNodeFactory
    public abstract static class QuantizeNode extends PythonTernaryBuiltinNode {
        @Specialization
        static Object doMock(VirtualFrame frame, Object self, Object arg1, Object arg2,
                        @Bind Node inliningTarget,
                        @Cached PRaiseNode raiseNode) {
            // TODO implement Context.quantize.
            throw raiseNode.raise(inliningTarget, NotImplementedError);
        }
    }

    @Builtin(name = "remainder", minNumOfPositionalArgs = 3)
    @GenerateNodeFactory
    public abstract static class RemainderNode extends PythonTernaryBuiltinNode {
        @Specialization
        static Object doMock(VirtualFrame frame, Object self, Object arg1, Object arg2,
                        @Bind Node inliningTarget,
                        @Cached PRaiseNode raiseNode) {
            // TODO implement Context.remainder.
            throw raiseNode.raise(inliningTarget, NotImplementedError);
        }
    }

    @Builtin(name = "remainder_near", minNumOfPositionalArgs = 3)
    @GenerateNodeFactory
    public abstract static class RemainderNearNode extends PythonTernaryBuiltinNode {
        @Specialization
        static Object doMock(VirtualFrame frame, Object self, Object arg1, Object arg2,
                        @Bind Node inliningTarget,
                        @Cached PRaiseNode raiseNode) {
            // TODO implement Context.remainder_near.
            throw raiseNode.raise(inliningTarget, NotImplementedError);
        }
    }

    @Builtin(name = "subtract", minNumOfPositionalArgs = 3)
    @GenerateNodeFactory
    public abstract static class SubtractNode extends PythonTernaryBuiltinNode {
        @Specialization
        static Object doMock(VirtualFrame frame, Object self, Object arg1, Object arg2,
                        @Bind Node inliningTarget,
                        @Cached PRaiseNode raiseNode) {
            // TODO implement Context.subtract.
            throw raiseNode.raise(inliningTarget, NotImplementedError);
        }
    }

    @Builtin(name = "power", minNumOfPositionalArgs = 3, numOfPositionalOnlyArgs = 1, parameterNames = {"self", "a", "b", "modulo"}, takesVarKeywordArgs = true)
    @GenerateNodeFactory
    public abstract static class PowerNode extends PythonBuiltinNode {
        @Specialization
        static Object doMock(VirtualFrame frame, Object self, Object a, Object b, Object modulo, PKeyword[] kwargs,
                        @Bind Node inliningTarget,
                        @Cached PRaiseNode raiseNode) {
            // TODO implement Context.power.
            throw raiseNode.raise(inliningTarget, NotImplementedError);
        }
    }

    @Builtin(name = "fma", minNumOfPositionalArgs = 4)
    @GenerateNodeFactory
    public abstract static class FmaNode extends PythonQuaternaryBuiltinNode {
        @Specialization
        static Object doMock(VirtualFrame frame, Object self, Object arg1, Object arg2, Object arg3,
                        @Bind Node inliningTarget,
                        @Cached PRaiseNode raiseNode) {
            // TODO implement Context.fma.
            throw raiseNode.raise(inliningTarget, NotImplementedError);
        }
    }

    @Builtin(name = "Etiny", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class EtinyNode extends PythonUnaryBuiltinNode {
        @Specialization
        static long get(PDecimalContext self) {
            return self.getEmin() - self.getPrecision() + 1;
        }
    }

    @Builtin(name = "Etop", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class EtopNode extends PythonUnaryBuiltinNode {
        @Specialization
        static long get(PDecimalContext self) {
            return self.getEmax() - self.getPrecision() + 1;
        }
    }

    @Builtin(name = "radix", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class RadixNode extends PythonUnaryBuiltinNode {
        @Specialization
        static PDecimal radix(@SuppressWarnings("unused") PDecimalContext self, @Bind PythonLanguage language) {
            return PFactory.createDecimal(PythonBuiltinClassType.PDecimal, PythonBuiltinClassType.PDecimal.getInstanceShape(language), 10L);
        }
    }

    @Builtin(name = "is_canonical", minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    public abstract static class IsCanonicalNode extends PythonBinaryBuiltinNode {
        @Specialization
        static Object doMock(VirtualFrame frame, Object self, Object arg1,
                        @Bind Node inliningTarget,
                        @Cached PRaiseNode raiseNode) {
            // TODO implement Context.is_canonical.
            throw raiseNode.raise(inliningTarget, NotImplementedError);
        }
    }

    @Builtin(name = "is_finite", minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    public abstract static class IsFiniteNode extends PythonBinaryBuiltinNode {
        @Specialization
        static Object doMock(VirtualFrame frame, Object self, Object arg1,
                        @Bind Node inliningTarget,
                        @Cached PRaiseNode raiseNode) {
            // TODO implement Context.is_finite.
            throw raiseNode.raise(inliningTarget, NotImplementedError);
        }
    }

    @Builtin(name = "is_infinite", minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    public abstract static class IsInfiniteNode extends PythonBinaryBuiltinNode {
        @Specialization
        static Object doMock(VirtualFrame frame, Object self, Object arg1,
                        @Bind Node inliningTarget,
                        @Cached PRaiseNode raiseNode) {
            // TODO implement Context.is_infinite.
            throw raiseNode.raise(inliningTarget, NotImplementedError);
        }
    }

    @Builtin(name = "is_nan", minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    public abstract static class IsNanNode extends PythonBinaryBuiltinNode {
        @Specialization
        static Object doMock(VirtualFrame frame, Object self, Object arg1,
                        @Bind Node inliningTarget,
                        @Cached PRaiseNode raiseNode) {
            // TODO implement Context.is_nan.
            throw raiseNode.raise(inliningTarget, NotImplementedError);
        }
    }

    @Builtin(name = "is_normal", minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    public abstract static class IsNormalNode extends PythonBinaryBuiltinNode {
        @Specialization
        static Object doMock(VirtualFrame frame, Object self, Object arg1,
                        @Bind Node inliningTarget,
                        @Cached PRaiseNode raiseNode) {
            // TODO implement Context.is_normal.
            throw raiseNode.raise(inliningTarget, NotImplementedError);
        }
    }

    @Builtin(name = "is_qnan", minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    public abstract static class IsQnanNode extends PythonBinaryBuiltinNode {
        @Specialization
        static Object doMock(VirtualFrame frame, Object self, Object arg1,
                        @Bind Node inliningTarget,
                        @Cached PRaiseNode raiseNode) {
            // TODO implement Context.is_qnan.
            throw raiseNode.raise(inliningTarget, NotImplementedError);
        }
    }

    @Builtin(name = "is_signed", minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    public abstract static class IsSignedNode extends PythonBinaryBuiltinNode {
        @Specialization
        static Object doMock(VirtualFrame frame, Object self, Object arg1,
                        @Bind Node inliningTarget,
                        @Cached PRaiseNode raiseNode) {
            // TODO implement Context.is_signed.
            throw raiseNode.raise(inliningTarget, NotImplementedError);
        }
    }

    @Builtin(name = "is_snan", minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    public abstract static class IsSnanNode extends PythonBinaryBuiltinNode {
        @Specialization
        static Object doMock(VirtualFrame frame, Object self, Object arg1,
                        @Bind Node inliningTarget,
                        @Cached PRaiseNode raiseNode) {
            // TODO implement Context.is_snan.
            throw raiseNode.raise(inliningTarget, NotImplementedError);
        }
    }

    @Builtin(name = "is_subnormal", minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    public abstract static class IsSubnormalNode extends PythonBinaryBuiltinNode {
        @Specialization
        static Object doMock(VirtualFrame frame, Object self, Object arg1,
                        @Bind Node inliningTarget,
                        @Cached PRaiseNode raiseNode) {
            // TODO implement Context.is_subnormal.
            throw raiseNode.raise(inliningTarget, NotImplementedError);
        }
    }

    @Builtin(name = "is_zero", minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    public abstract static class IsZeroNode extends PythonBinaryBuiltinNode {
        @Specialization
        static Object doMock(VirtualFrame frame, Object self, Object arg1,
                        @Bind Node inliningTarget,
                        @Cached PRaiseNode raiseNode) {
            // TODO implement Context.is_zero.
            throw raiseNode.raise(inliningTarget, NotImplementedError);
        }
    }

    @Builtin(name = "_apply", minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    public abstract static class ApplyNode extends PythonBinaryBuiltinNode {
        @Specialization
        static Object doMock(VirtualFrame frame, Object self, Object arg1,
                        @Bind Node inliningTarget,
                        @Cached PRaiseNode raiseNode) {
            // TODO implement Context._apply.
            throw raiseNode.raise(inliningTarget, NotImplementedError);
        }
    }

    @Builtin(name = "canonical", minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    public abstract static class CanonicalNode extends PythonBinaryBuiltinNode {
        @Specialization
        static Object doMock(VirtualFrame frame, Object self, Object arg1,
                        @Bind Node inliningTarget,
                        @Cached PRaiseNode raiseNode) {
            // TODO implement Context.canonical.
            throw raiseNode.raise(inliningTarget, NotImplementedError);
        }
    }

    @Builtin(name = "copy_abs", minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    public abstract static class CopyAbsNode extends PythonBinaryBuiltinNode {
        @Specialization
        static Object doMock(VirtualFrame frame, Object self, Object arg1,
                        @Bind Node inliningTarget,
                        @Cached PRaiseNode raiseNode) {
            // TODO implement Context.copy_abs.
            throw raiseNode.raise(inliningTarget, NotImplementedError);
        }
    }

    @Builtin(name = "copy_decimal", minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    public abstract static class CopyDecimalNode extends PythonBinaryBuiltinNode {
        @Specialization
        static Object doMock(VirtualFrame frame, Object self, Object arg1,
                        @Bind Node inliningTarget,
                        @Cached PRaiseNode raiseNode) {
            // TODO implement Context.copy_decimal.
            throw raiseNode.raise(inliningTarget, NotImplementedError);
        }
    }

    @Builtin(name = "copy_negate", minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    public abstract static class CopyNegateNode extends PythonBinaryBuiltinNode {
        @Specialization
        static Object doMock(VirtualFrame frame, Object self, Object arg1,
                        @Bind Node inliningTarget,
                        @Cached PRaiseNode raiseNode) {
            // TODO implement Context.copy_negate.
            throw raiseNode.raise(inliningTarget, NotImplementedError);
        }
    }

    @Builtin(name = "logb", minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    public abstract static class LogbNode extends PythonBinaryBuiltinNode {
        @Specialization
        static Object doMock(VirtualFrame frame, Object self, Object arg1,
                        @Bind Node inliningTarget,
                        @Cached PRaiseNode raiseNode) {
            // TODO implement Context.logb.
            throw raiseNode.raise(inliningTarget, NotImplementedError);
        }
    }

    @Builtin(name = "logical_invert", minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    public abstract static class LogicalInvertNode extends PythonBinaryBuiltinNode {
        @Specialization
        static Object doMock(VirtualFrame frame, Object self, Object arg1,
                        @Bind Node inliningTarget,
                        @Cached PRaiseNode raiseNode) {
            // TODO implement Context.logical_invert.
            throw raiseNode.raise(inliningTarget, NotImplementedError);
        }
    }

    @Builtin(name = "number_class", minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    public abstract static class NumberClassNode extends PythonBinaryBuiltinNode {
        @Specialization
        static Object doMock(VirtualFrame frame, Object self, Object arg1,
                        @Bind Node inliningTarget,
                        @Cached PRaiseNode raiseNode) {
            // TODO implement Context.number_class.
            throw raiseNode.raise(inliningTarget, NotImplementedError);
        }
    }

    @Builtin(name = "to_sci_string", minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    public abstract static class ToSciStringNode extends PythonBinaryBuiltinNode {
        @Specialization
        static Object doMock(VirtualFrame frame, Object self, Object arg1,
                        @Bind Node inliningTarget,
                        @Cached PRaiseNode raiseNode) {
            // TODO implement Context.to_sci_string.
            throw raiseNode.raise(inliningTarget, NotImplementedError);
        }
    }

    @Builtin(name = "to_eng_string", minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    public abstract static class ToEngStringNode extends PythonBinaryBuiltinNode {
        @Specialization
        static Object doMock(VirtualFrame frame, Object self, Object arg1,
                        @Bind Node inliningTarget,
                        @Cached PRaiseNode raiseNode) {
            // TODO implement Context.to_eng_string.
            throw raiseNode.raise(inliningTarget, NotImplementedError);
        }
    }

    @Builtin(name = "compare_total", minNumOfPositionalArgs = 3)
    @GenerateNodeFactory
    public abstract static class CompareTotalNode extends PythonTernaryBuiltinNode {
        @Specialization
        static Object doMock(VirtualFrame frame, Object self, Object arg1, Object arg2,
                        @Bind Node inliningTarget,
                        @Cached PRaiseNode raiseNode) {
            // TODO implement Context.compare_total.
            throw raiseNode.raise(inliningTarget, NotImplementedError);
        }
    }

    @Builtin(name = "compare_total_mag", minNumOfPositionalArgs = 3)
    @GenerateNodeFactory
    public abstract static class CompareTotalMagNode extends PythonTernaryBuiltinNode {
        @Specialization
        static Object doMock(VirtualFrame frame, Object self, Object arg1, Object arg2,
                        @Bind Node inliningTarget,
                        @Cached PRaiseNode raiseNode) {
            // TODO implement Context.compare_total_mag.
            throw raiseNode.raise(inliningTarget, NotImplementedError);
        }
    }

    @Builtin(name = "copy_sign", minNumOfPositionalArgs = 3)
    @GenerateNodeFactory
    public abstract static class CopySignNode extends PythonTernaryBuiltinNode {
        @Specialization
        static Object doMock(VirtualFrame frame, Object self, Object arg1, Object arg2,
                        @Bind Node inliningTarget,
                        @Cached PRaiseNode raiseNode) {
            // TODO implement Context.copy_sign.
            throw raiseNode.raise(inliningTarget, NotImplementedError);
        }
    }

    @Builtin(name = "logical_and", minNumOfPositionalArgs = 3)
    @GenerateNodeFactory
    public abstract static class LogicalAndNode extends PythonTernaryBuiltinNode {
        @Specialization
        static Object doMock(VirtualFrame frame, Object self, Object arg1, Object arg2,
                        @Bind Node inliningTarget,
                        @Cached PRaiseNode raiseNode) {
            // TODO implement Context.logical_and.
            throw raiseNode.raise(inliningTarget, NotImplementedError);
        }
    }

    @Builtin(name = "logical_or", minNumOfPositionalArgs = 3)
    @GenerateNodeFactory
    public abstract static class LogicalOrNode extends PythonTernaryBuiltinNode {
        @Specialization
        static Object doMock(VirtualFrame frame, Object self, Object arg1, Object arg2,
                        @Bind Node inliningTarget,
                        @Cached PRaiseNode raiseNode) {
            // TODO implement Context.logical_or.
            throw raiseNode.raise(inliningTarget, NotImplementedError);
        }
    }

    @Builtin(name = "logical_xor", minNumOfPositionalArgs = 3)
    @GenerateNodeFactory
    public abstract static class LogicalXorNode extends PythonTernaryBuiltinNode {
        @Specialization
        static Object doMock(VirtualFrame frame, Object self, Object arg1, Object arg2,
                        @Bind Node inliningTarget,
                        @Cached PRaiseNode raiseNode) {
            // TODO implement Context.logical_xor.
            throw raiseNode.raise(inliningTarget, NotImplementedError);
        }
    }

    @Builtin(name = "rotate", minNumOfPositionalArgs = 3)
    @GenerateNodeFactory
    public abstract static class RotateNode extends PythonTernaryBuiltinNode {
        @Specialization
        static Object doMock(VirtualFrame frame, Object self, Object arg1, Object arg2,
                        @Bind Node inliningTarget,
                        @Cached PRaiseNode raiseNode) {
            // TODO implement Context.rotate.
            throw raiseNode.raise(inliningTarget, NotImplementedError);
        }
    }

    @Builtin(name = "same_quantum", minNumOfPositionalArgs = 3)
    @GenerateNodeFactory
    public abstract static class SameQuantumNode extends PythonTernaryBuiltinNode {
        @Specialization
        static Object doMock(VirtualFrame frame, Object self, Object arg1, Object arg2,
                        @Bind Node inliningTarget,
                        @Cached PRaiseNode raiseNode) {
            // TODO implement Context.same_quantum.
            throw raiseNode.raise(inliningTarget, NotImplementedError);
        }
    }

    @Builtin(name = "scaleb", minNumOfPositionalArgs = 3)
    @GenerateNodeFactory
    public abstract static class ScalebNode extends PythonTernaryBuiltinNode {
        @Specialization
        static Object doMock(VirtualFrame frame, Object self, Object arg1, Object arg2,
                        @Bind Node inliningTarget,
                        @Cached PRaiseNode raiseNode) {
            // TODO implement Context.scaleb.
            throw raiseNode.raise(inliningTarget, NotImplementedError);
        }
    }

    @Builtin(name = "shift", minNumOfPositionalArgs = 3)
    @GenerateNodeFactory
    public abstract static class ShiftNode extends PythonTernaryBuiltinNode {
        @Specialization
        static Object doMock(VirtualFrame frame, Object self, Object arg1, Object arg2,
                        @Bind Node inliningTarget,
                        @Cached PRaiseNode raiseNode) {
            // TODO implement Context.shift.
            throw raiseNode.raise(inliningTarget, NotImplementedError);
        }
    }

    @Builtin(name = "clear_flags", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class ClearFlagsNode extends PythonUnaryBuiltinNode {
        @Specialization
        static Object clear(PDecimalContext self) {
            self.setFlags(0);
            return PNone.NONE;
        }
    }

    @Builtin(name = "clear_traps", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class ClearTrapsNode extends PythonUnaryBuiltinNode {
        @Specialization
        static Object clear(PDecimalContext self) {
            self.setTraps(0);
            return PNone.NONE;
        }
    }

    @Builtin(name = "__copy__", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class CopySpecialNode extends PythonUnaryBuiltinNode {
        @Specialization
        static PDecimalContext copy(PDecimalContext self,
                        @Bind Node inliningTarget,
                        @Bind PythonLanguage language) {
            return DecimalContextUtils.getState(inliningTarget).copyContext(language, self);
        }
    }

    @Builtin(name = "__reduce__", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class ReduceNode extends PythonUnaryBuiltinNode {
        @Specialization
        static Object reduce(PDecimalContext self,
                        @Bind Node inliningTarget,
                        @Bind PythonLanguage language,
                        @Cached GetClassNode getClass) {
            Object args = PFactory.createTuple(language, new Object[]{
                            self.getPrecision(), self.getRounding(), self.getEmin(), self.getEmax(), self.getCapitals(), self.getClamp(),
                            DecimalContextUtils.signalsAsList(inliningTarget, self.getFlags()), DecimalContextUtils.signalsAsList(inliningTarget, self.getTraps())
            });
            return PFactory.createTuple(language, new Object[]{getClass.execute(inliningTarget, self), args});
        }
    }

    @Builtin(name = "copy", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class CopyNode extends PythonUnaryBuiltinNode {
        @Specialization
        static PDecimalContext copy(PDecimalContext self,
                        @Bind Node inliningTarget,
                        @Bind PythonLanguage language) {
            return DecimalContextUtils.getState(inliningTarget).copyContext(language, self);
        }
    }

    @Builtin(name = "create_decimal", minNumOfPositionalArgs = 1, maxNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    public abstract static class CreateDecimalNode extends PythonBinaryBuiltinNode {
        @Specialization
        static Object doMock(VirtualFrame frame, Object self, Object arg1,
                        @Bind Node inliningTarget,
                        @Cached PRaiseNode raiseNode) {
            // TODO implement Context.create_decimal.
            throw raiseNode.raise(inliningTarget, NotImplementedError);
        }
    }

    @Builtin(name = "create_decimal_from_float", minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    public abstract static class CreateDecimalFromFloatNode extends PythonBinaryBuiltinNode {
        @Specialization
        static Object doMock(VirtualFrame frame, Object self, Object arg1,
                        @Bind Node inliningTarget,
                        @Cached PRaiseNode raiseNode) {
            // TODO implement Context.create_decimal_from_float.
            throw raiseNode.raise(inliningTarget, NotImplementedError);
        }
    }

    @Builtin(name = "prec", minNumOfPositionalArgs = 1, maxNumOfPositionalArgs = 2, isGetter = true, isSetter = true)
    @GenerateNodeFactory
    public abstract static class PrecNode extends PythonBinaryBuiltinNode {
        @Specialization(guards = "isNoValue(value)")
        static long get(PDecimalContext self, @SuppressWarnings("unused") PNone value) {
            return self.getPrecision();
        }

        @Specialization(guards = "!isNoValue(value)")
        static Object set(VirtualFrame frame, PDecimalContext self, Object value,
                        @Bind Node inliningTarget,
                        @Cached PyLongAsLongNode asLong,
                        @Cached PyLongCheckNode longCheck,
                        @Cached PRaiseNode raiseNode) {
            if (!longCheck.execute(inliningTarget, value)) {
                throw raiseNode.raise(inliningTarget, TypeError, ErrorMessages.INTEGER_REQUIRED);
            }
            long number = asLong.execute(frame, inliningTarget, value);
            self.setPrecision(DecimalContextUtils.validateLong(inliningTarget, raiseNode, number, 1, DecimalUtils.MAX_PREC, ErrorMessages.DECIMAL_PREC_RANGE));
            return PNone.NONE;
        }
    }

    @Builtin(name = "Emax", minNumOfPositionalArgs = 1, maxNumOfPositionalArgs = 2, isGetter = true, isSetter = true)
    @GenerateNodeFactory
    public abstract static class EmaxNode extends PythonBinaryBuiltinNode {
        @Specialization(guards = "isNoValue(value)")
        static long get(PDecimalContext self, @SuppressWarnings("unused") PNone value) {
            return self.getEmax();
        }

        @Specialization(guards = "!isNoValue(value)")
        static Object set(VirtualFrame frame, PDecimalContext self, Object value,
                        @Bind Node inliningTarget,
                        @Cached PyLongAsLongNode asLong,
                        @Cached PyLongCheckNode longCheck,
                        @Cached PRaiseNode raiseNode) {
            if (!longCheck.execute(inliningTarget, value)) {
                throw raiseNode.raise(inliningTarget, TypeError, ErrorMessages.INTEGER_REQUIRED);
            }
            long number = asLong.execute(frame, inliningTarget, value);
            self.setEmax(DecimalContextUtils.validateLong(inliningTarget, raiseNode, number, 0, DecimalUtils.MAX_EMAX, ErrorMessages.DECIMAL_EMAX_RANGE));
            return PNone.NONE;
        }
    }

    @Builtin(name = "Emin", minNumOfPositionalArgs = 1, maxNumOfPositionalArgs = 2, isGetter = true, isSetter = true)
    @GenerateNodeFactory
    public abstract static class EminNode extends PythonBinaryBuiltinNode {
        @Specialization(guards = "isNoValue(value)")
        static long get(PDecimalContext self, @SuppressWarnings("unused") PNone value) {
            return self.getEmin();
        }

        @Specialization(guards = "!isNoValue(value)")
        static Object set(VirtualFrame frame, PDecimalContext self, Object value,
                        @Bind Node inliningTarget,
                        @Cached PyLongAsLongNode asLong,
                        @Cached PyLongCheckNode longCheck,
                        @Cached PRaiseNode raiseNode) {
            if (!longCheck.execute(inliningTarget, value)) {
                throw raiseNode.raise(inliningTarget, TypeError, ErrorMessages.INTEGER_REQUIRED);
            }
            long number = asLong.execute(frame, inliningTarget, value);
            self.setEmin(DecimalContextUtils.validateLong(inliningTarget, raiseNode, number, DecimalUtils.MIN_EMIN, 0, ErrorMessages.DECIMAL_EMIN_RANGE));
            return PNone.NONE;
        }
    }

    @Builtin(name = "rounding", minNumOfPositionalArgs = 1, maxNumOfPositionalArgs = 2, isGetter = true, isSetter = true)
    @GenerateNodeFactory
    public abstract static class RoundingNode extends PythonBinaryBuiltinNode {
        @Specialization(guards = "isNoValue(value)")
        static TruffleString get(PDecimalContext self, @SuppressWarnings("unused") PNone value) {
            return self.getRounding();
        }

        @Specialization(guards = "!isNoValue(value)")
        static Object set(PDecimalContext self, Object value,
                        @Bind Node inliningTarget) {
            self.setRounding(DecimalContextUtils.validateRounding(inliningTarget, value));
            return PNone.NONE;
        }
    }

    @Builtin(name = "capitals", minNumOfPositionalArgs = 1, maxNumOfPositionalArgs = 2, isGetter = true, isSetter = true)
    @GenerateNodeFactory
    public abstract static class CapitalsNode extends PythonBinaryBuiltinNode {
        @Specialization(guards = "isNoValue(value)")
        static int get(PDecimalContext self, @SuppressWarnings("unused") PNone value) {
            return self.getCapitals();
        }

        @Specialization(guards = "!isNoValue(value)")
        static Object set(VirtualFrame frame, PDecimalContext self, Object value,
                        @Bind Node inliningTarget,
                        @Cached PyLongAsLongNode asLong,
                        @Cached PyLongCheckNode longCheck,
                        @Cached PRaiseNode raiseNode) {
            if (!longCheck.execute(inliningTarget, value)) {
                throw raiseNode.raise(inliningTarget, TypeError, ErrorMessages.INTEGER_REQUIRED);
            }
            long number = asLong.execute(frame, inliningTarget, value);
            self.setCapitals((int) DecimalContextUtils.validateLong(inliningTarget, raiseNode, number, 0, 1, ErrorMessages.DECIMAL_CAPITALS_RANGE));
            return PNone.NONE;
        }
    }

    @Builtin(name = "clamp", minNumOfPositionalArgs = 1, maxNumOfPositionalArgs = 2, isGetter = true, isSetter = true)
    @GenerateNodeFactory
    public abstract static class ClampNode extends PythonBinaryBuiltinNode {
        @Specialization(guards = "isNoValue(value)")
        static int get(PDecimalContext self, @SuppressWarnings("unused") PNone value) {
            return self.getClamp();
        }

        @Specialization(guards = "!isNoValue(value)")
        static Object set(VirtualFrame frame, PDecimalContext self, Object value,
                        @Bind Node inliningTarget,
                        @Cached PyLongAsLongNode asLong,
                        @Cached PyLongCheckNode longCheck,
                        @Cached PRaiseNode raiseNode) {
            if (!longCheck.execute(inliningTarget, value)) {
                throw raiseNode.raise(inliningTarget, TypeError, ErrorMessages.INTEGER_REQUIRED);
            }
            long number = asLong.execute(frame, inliningTarget, value);
            self.setClamp(DecimalContextUtils.validateClamp(inliningTarget, raiseNode, number));
            return PNone.NONE;
        }
    }

    @Slot(value = SlotKind.tp_new, isComplex = true)
    @SlotSignature(name = "Context", minNumOfPositionalArgs = 1, takesVarArgs = true, takesVarKeywordArgs = true)
    @GenerateNodeFactory
    public abstract static class TpNewNode extends PythonVarargsBuiltinNode {
        @Specialization
        static PDecimalContext construct(Object cls, @SuppressWarnings("unused") Object[] args, @SuppressWarnings("unused") PKeyword[] kwargs,
                        @Bind Node inliningTarget,
                        @Cached TypeNodes.GetInstanceShape getShape) {
            return DecimalContextUtils.getState(inliningTarget).newContext(cls, getShape.execute(cls));
        }
    }

    @Slot(value = SlotKind.tp_init, isComplex = true)
    @SlotSignature(name = "Context", minNumOfPositionalArgs = 1, numOfPositionalOnlyArgs = 1, parameterNames = {"self", "prec", "rounding", "Emin", "Emax", "capitals", "clamp", "flags", "traps"})
    @GenerateNodeFactory
    public abstract static class TpInitNode extends PythonBuiltinNode {
        @Specialization
        static Object initialize(PDecimalContext self, Object prec, Object rounding, Object emin, Object emax, Object capitals, Object clamp, Object flags, Object traps,
                        @Bind Node inliningTarget) {
            DecimalContextUtils.setAttributes(inliningTarget, self, prec, rounding, emin, emax, capitals, clamp, flags, traps);
            return PNone.NONE;
        }
    }

    @Slot(value = SlotKind.tp_repr, isComplex = true)
    @GenerateNodeFactory
    public abstract static class TpReprNode extends PythonUnaryBuiltinNode {
        @Specialization
        static TruffleString repr(PDecimalContext self,
                        @Cached TruffleString.FromJavaStringNode fromJavaString) {
            return fromJavaString.execute(DecimalContextUtils.contextRepr(self), TS_ENCODING);
        }
    }

    @Slot(value = SlotKind.tp_getattro, isComplex = true)
    @GenerateNodeFactory
    public abstract static class TpGetattroNode extends GetAttrBuiltinNode {
        @Specialization
        static Object get(VirtualFrame frame, PDecimalContext self, Object name,
                        @Bind Node inliningTarget,
                        @Cached CastToTruffleStringChecked1Node castName,
                        @Cached TruffleString.EqualNode equal,
                        @Cached ObjectBuiltins.GetAttributeNode genericGet) {
            TruffleString key = castName.cast(inliningTarget, name, ATTR_NAME_MUST_BE_STRING, name);
            if (equal.execute(key, T_FLAGS, TS_ENCODING)) {
                return self.getFlagsDict();
            }
            if (equal.execute(key, T_TRAPS, TS_ENCODING)) {
                return self.getTrapsDict();
            }
            return genericGet.execute(frame, self, key);
        }
    }

    @Slot(value = SlotKind.tp_setattro, isComplex = true)
    @GenerateNodeFactory
    public abstract static class TpSetattroNode extends SetAttrBuiltinNode {
        @Specialization
        static void set(VirtualFrame frame, PDecimalContext self, Object name, Object value,
                        @Bind Node inliningTarget,
                        @Cached CastToTruffleStringChecked1Node castName,
                        @Cached TruffleString.EqualNode equal,
                        @Cached PRaiseNode raiseNode,
                        @Cached ObjectBuiltins.SetattrNode genericSet) {
            TruffleString key = castName.cast(inliningTarget, name, ATTR_NAME_MUST_BE_STRING, name);
            if (value == PNone.NO_VALUE) {
                throw raiseNode.raise(inliningTarget, AttributeError, ErrorMessages.CANT_DELETE_ATTRIBUTE);
            } else if (equal.execute(key, T_FLAGS, TS_ENCODING)) {
                self.setFlags(DecimalContextUtils.signalsFromObject(inliningTarget, value, false));
            } else if (equal.execute(key, T_TRAPS, TS_ENCODING)) {
                self.setTraps(DecimalContextUtils.signalsFromObject(inliningTarget, value, false));
            } else {
                genericSet.execute(frame, self, key, value);
            }
        }
    }
}
