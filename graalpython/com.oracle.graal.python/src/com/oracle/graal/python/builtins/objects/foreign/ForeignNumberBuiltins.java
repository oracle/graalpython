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

package com.oracle.graal.python.builtins.objects.foreign;

import static com.oracle.graal.python.builtins.objects.str.StringUtils.simpleTruffleStringFormatUncached;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___CEIL__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___FLOOR__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___ROUND__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___TRUNC__;
import static com.oracle.graal.python.util.PythonUtils.TS_ENCODING;

import java.util.List;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.annotations.Slot;
import com.oracle.graal.python.annotations.Slot.SlotKind;
import com.oracle.graal.python.builtins.Builtin;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.PNotImplemented;
import com.oracle.graal.python.builtins.objects.PythonAbstractObject;
import com.oracle.graal.python.builtins.objects.foreign.ForeignObjectBuiltins.ForeignGetattrNode;
import com.oracle.graal.python.builtins.objects.ints.PInt;
import com.oracle.graal.python.builtins.objects.object.ObjectBuiltins;
import com.oracle.graal.python.builtins.objects.object.ObjectNodes;
import com.oracle.graal.python.builtins.objects.type.TpSlots;
import com.oracle.graal.python.builtins.objects.type.slots.TpSlotBinaryOp.BinaryOpBuiltinNode;
import com.oracle.graal.python.builtins.objects.type.slots.TpSlotGetAttr.GetAttrBuiltinNode;
import com.oracle.graal.python.builtins.objects.type.slots.TpSlotInquiry.NbBoolBuiltinNode;
import com.oracle.graal.python.builtins.objects.type.slots.TpSlotRichCompare;
import com.oracle.graal.python.lib.PyNumberAbsoluteNode;
import com.oracle.graal.python.lib.PyNumberAddNode;
import com.oracle.graal.python.lib.PyNumberAndNode;
import com.oracle.graal.python.lib.PyNumberDivmodNode;
import com.oracle.graal.python.lib.PyNumberFloatNode;
import com.oracle.graal.python.lib.PyNumberFloorDivideNode;
import com.oracle.graal.python.lib.PyNumberInvertNode;
import com.oracle.graal.python.lib.PyNumberLongNode;
import com.oracle.graal.python.lib.PyNumberLshiftNode;
import com.oracle.graal.python.lib.PyNumberMultiplyNode;
import com.oracle.graal.python.lib.PyNumberNegativeNode;
import com.oracle.graal.python.lib.PyNumberOrNode;
import com.oracle.graal.python.lib.PyNumberPositiveNode;
import com.oracle.graal.python.lib.PyNumberPowerNode;
import com.oracle.graal.python.lib.PyNumberRemainderNode;
import com.oracle.graal.python.lib.PyNumberRshiftNode;
import com.oracle.graal.python.lib.PyNumberSubtractNode;
import com.oracle.graal.python.lib.PyNumberTrueDivideNode;
import com.oracle.graal.python.lib.PyNumberXorNode;
import com.oracle.graal.python.lib.PyObjectRichCompare;
import com.oracle.graal.python.lib.PyObjectStrAsTruffleStringNode;
import com.oracle.graal.python.lib.RichCmpOp;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.SpecialMethodNames;
import com.oracle.graal.python.nodes.call.special.LookupAndCallBinaryNode;
import com.oracle.graal.python.nodes.call.special.LookupAndCallUnaryNode;
import com.oracle.graal.python.nodes.call.special.SpecialMethodNotFound;
import com.oracle.graal.python.nodes.expression.BinaryOpNode;
import com.oracle.graal.python.nodes.expression.UnaryOpNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.builtins.PythonBinaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonTernaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonUnaryBuiltinNode;
import com.oracle.graal.python.nodes.object.BuiltinClassProfiles.IsBuiltinObjectProfile;
import com.oracle.graal.python.nodes.object.IsForeignObjectNode;
import com.oracle.graal.python.runtime.GilNode;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.graal.python.runtime.object.PFactory;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.GenerateCached;
import com.oracle.truffle.api.dsl.GenerateInline;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.profiles.InlinedBranchProfile;
import com.oracle.truffle.api.strings.TruffleString;

/*
 * This class handles foreign numbers, whether they are integral or floating-point,
 * since interop has no message to know which one, and it would be impractical to handle
 * foreign integers in IntBuiltins for instance.
 * We are also handling part of foreign booleans here since Python ForeignBoolean inherits from ForeignNumber,
 * to avoid duplication and to be similar to Python bool/int.
 *
 * NOTE: We are not using IndirectCallContext here in this file
 * because it seems unlikely that these interop messages would call back to Python
 * and that we would also need precise frame info for that case.
 * Adding it shouldn't hurt peak, but might be a non-trivial overhead in interpreter.
 */
@CoreFunctions(extendClasses = PythonBuiltinClassType.ForeignNumber)
public final class ForeignNumberBuiltins extends PythonBuiltins {
    public static TpSlots SLOTS = ForeignNumberBuiltinsSlotsGen.SLOTS;

    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return ForeignNumberBuiltinsFactory.getFactories();
    }

    @GenerateInline
    @GenerateCached(false)
    abstract static class UnboxForeignObjectNode extends Node {
        public abstract Object execute(Node inliningTarget, Object value);

        @Specialization(guards = "lib.isBoolean(obj)")
        boolean doBool(Object obj,
                        @Shared @CachedLibrary(limit = "3") InteropLibrary lib,
                        @Shared @Cached(inline = false) GilNode gil) {
            gil.release(true);
            try {
                return lib.asBoolean(obj);
            } catch (UnsupportedMessageException e) {
                throw CompilerDirectives.shouldNotReachHere(e);
            } finally {
                gil.acquire();
            }
        }

        @Specialization(guards = "lib.fitsInLong(obj)")
        long doLong(Object obj,
                        @Shared @CachedLibrary(limit = "3") InteropLibrary lib,
                        @Shared @Cached(inline = false) GilNode gil) {
            assert !lib.isBoolean(obj);
            gil.release(true);
            try {
                return lib.asLong(obj);
            } catch (UnsupportedMessageException e) {
                throw CompilerDirectives.shouldNotReachHere(e);
            } finally {
                gil.acquire();
            }
        }

        @Specialization(guards = {"!lib.fitsInLong(obj)", "lib.fitsInBigInteger(obj)"})
        PInt doBigInt(Object obj,
                        @Bind PythonLanguage language,
                        @Shared @CachedLibrary(limit = "3") InteropLibrary lib,
                        @Shared @Cached(inline = false) GilNode gil) {
            assert !lib.isBoolean(obj);
            gil.release(true);
            try {
                return PFactory.createInt(language, lib.asBigInteger(obj));
            } catch (UnsupportedMessageException e) {
                throw CompilerDirectives.shouldNotReachHere(e);
            } finally {
                gil.acquire();
            }
        }

        @Specialization(guards = {"!lib.fitsInLong(obj)", "!lib.fitsInBigInteger(obj)", "lib.fitsInDouble(obj)"})
        double doDouble(Object obj,
                        @Shared @CachedLibrary(limit = "3") InteropLibrary lib,
                        @Shared @Cached(inline = false) GilNode gil) {
            assert !lib.isBoolean(obj);
            gil.release(true);
            try {
                return lib.asDouble(obj);
            } catch (UnsupportedMessageException e) {
                throw CompilerDirectives.shouldNotReachHere(e);
            } finally {
                gil.acquire();
            }
        }

        @Fallback
        @SuppressWarnings("unused")
        public static Object doGeneric(Object left) {
            return null;
        }
    }

    @GenerateInline
    @GenerateCached(false)
    abstract static class UnboxNode extends Node {
        public final Object execute(Node inliningTarget, Object value) {
            Object unboxed = executeInternal(inliningTarget, value);
            assert unboxed == null || !IsForeignObjectNode.executeUncached(unboxed) : unboxed;
            return unboxed;
        }

        protected abstract Object executeInternal(Node inliningTarget, Object value);

        @Specialization
        static boolean unbox(boolean value) {
            return value;
        }

        @Specialization
        static int unbox(int value) {
            return value;
        }

        @Specialization
        static long unbox(long value) {
            return value;
        }

        @Specialization
        static PInt unbox(PInt value) {
            return value;
        }

        @Specialization
        static double unbox(double value) {
            return value;
        }

        @Fallback
        static Object unbox(Node inliningTarget, Object value,
                        @Cached IsForeignObjectNode isForeignObjectNode,
                        @Cached UnboxForeignObjectNode unboxForeignObjectNode) {
            if (isForeignObjectNode.execute(inliningTarget, value)) {
                return unboxForeignObjectNode.execute(inliningTarget, value);
            } else {
                return null;
            }
        }
    }

    abstract static class ForeignUnaryNode extends PythonUnaryBuiltinNode {
        @Child private UnaryOpNode op;

        protected ForeignUnaryNode(UnaryOpNode op) {
            this.op = op;
        }

        @Specialization
        Object doGeneric(VirtualFrame frame, Object value,
                        @Bind Node inliningTarget,
                        @Cached UnboxNode unboxNode) {
            Object unboxed = unboxNode.execute(inliningTarget, value);
            assert unboxed != null;
            return op.execute(frame, unboxed);
        }
    }

    abstract static class ForeignBinaryNode extends BinaryOpBuiltinNode {
        @Child private BinaryOpNode op;
        protected final boolean reverse;

        protected ForeignBinaryNode(BinaryOpNode op, boolean reverse) {
            this.op = op;
            this.reverse = reverse;
        }

        @Specialization
        Object doGeneric(VirtualFrame frame, Object left, Object right,
                        @Bind Node inliningTarget,
                        @Cached UnboxNode unboxNode) {
            Object unboxed = unboxNode.execute(inliningTarget, left);
            if (unboxed != null) {
                if (!reverse) {
                    return op.execute(frame, unboxed, right);
                } else {
                    return op.execute(frame, right, unboxed);
                }
            } else {
                return PNotImplemented.NOT_IMPLEMENTED;
            }
        }
    }

    @GenerateInline
    @GenerateCached(false)
    abstract static class ForeignBinarySlotNode extends Node {
        abstract Object execute(VirtualFrame frame, Node inliningTarget, Object left, Object right, BinaryOpNode binaryOpNode);

        @Specialization
        static Object doIt(VirtualFrame frame, Node inliningTarget, Object left, Object right, BinaryOpNode op,
                        @Cached IsForeignObjectNode isForeignLeft,
                        @Cached IsForeignObjectNode isForeignRight,
                        @Cached UnboxNode unboxLeft,
                        @Cached UnboxNode unboxRight) {
            boolean leftIsForeign = isForeignLeft.execute(inliningTarget, left);
            boolean rightIsForeign = isForeignRight.execute(inliningTarget, right);
            if (!leftIsForeign && !rightIsForeign) {
                return PNotImplemented.NOT_IMPLEMENTED;
            }

            Object newLeft = unboxLeft.execute(inliningTarget, left);
            Object newRight = unboxRight.execute(inliningTarget, right);
            if (newLeft == null || newRight == null) {
                return PNotImplemented.NOT_IMPLEMENTED;
            }
            return op.execute(frame, newLeft, newRight);
        }
    }

    @Slot(SlotKind.nb_bool)
    @GenerateUncached
    @GenerateNodeFactory
    abstract static class BoolNode extends NbBoolBuiltinNode {
        @Specialization(limit = "getCallSiteInlineCacheMaxDepth()")
        static boolean bool(Object receiver,
                        @CachedLibrary("receiver") InteropLibrary lib,
                        @Cached GilNode gil) {
            assert !lib.isBoolean(receiver);
            gil.release(true);
            try {
                if (lib.fitsInLong(receiver)) {
                    return lib.asLong(receiver) != 0;
                }
                if (lib.fitsInBigInteger(receiver)) {
                    return lib.asBigInteger(receiver).signum() != 0;
                }
                if (lib.fitsInDouble(receiver)) {
                    return lib.asDouble(receiver) != 0.0;
                }
                return false;
            } catch (UnsupportedMessageException e) {
                throw CompilerDirectives.shouldNotReachHere(e);
            } finally {
                gil.acquire();
            }
        }
    }

    @Slot(value = SlotKind.nb_positive, isComplex = true)
    @GenerateNodeFactory
    abstract static class PosNode extends ForeignUnaryNode {
        PosNode() {
            super(PyNumberPositiveNode.create());
        }
    }

    @Slot(value = SlotKind.nb_negative, isComplex = true)
    @GenerateNodeFactory
    abstract static class NegNode extends ForeignUnaryNode {
        NegNode() {
            super(PyNumberNegativeNode.create());
        }
    }

    @Slot(value = SlotKind.nb_invert, isComplex = true)
    @GenerateNodeFactory
    abstract static class InvertNode extends ForeignUnaryNode {
        InvertNode() {
            super(PyNumberInvertNode.create());
        }
    }

    @Slot(value = SlotKind.nb_absolute, isComplex = true)
    @GenerateNodeFactory
    abstract static class AbsNode extends ForeignUnaryNode {
        AbsNode() {
            super(PyNumberAbsoluteNode.create());
        }
    }

    @Builtin(name = J___CEIL__, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class CeilNode extends ForeignUnaryNode {
        CeilNode() {
            super(LookupAndCallUnaryNode.create(SpecialMethodNames.T___CEIL__));
        }
    }

    @Builtin(name = J___FLOOR__, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class FloorNode extends ForeignUnaryNode {
        FloorNode() {
            super(LookupAndCallUnaryNode.create(SpecialMethodNames.T___FLOOR__));
        }
    }

    @Builtin(name = J___TRUNC__, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class TruncNode extends ForeignUnaryNode {
        TruncNode() {
            super(LookupAndCallUnaryNode.create(SpecialMethodNames.T___TRUNC__));
        }
    }

    @Slot(value = SlotKind.nb_int, isComplex = true)
    @GenerateNodeFactory
    abstract static class IntNode extends PythonUnaryBuiltinNode {
        @Specialization
        Object doGeneric(VirtualFrame frame, Object self,
                        @Bind Node inliningTarget,
                        @Cached UnboxNode unboxNode,
                        @Cached PyNumberLongNode longNode) {
            Object unboxed = unboxNode.execute(inliningTarget, self);
            return longNode.execute(frame, inliningTarget, unboxed);
        }
    }

    @Slot(value = SlotKind.nb_float, isComplex = true)
    @GenerateNodeFactory
    abstract static class FloatNode extends PythonUnaryBuiltinNode {
        @Specialization
        Object doGeneric(VirtualFrame frame, Object self,
                        @Bind Node inliningTarget,
                        @Cached UnboxNode unboxNode,
                        @Cached PyNumberFloatNode floatNode) {
            Object unboxed = unboxNode.execute(inliningTarget, self);
            return floatNode.execute(frame, inliningTarget, unboxed);
        }
    }

    @Slot(value = SlotKind.nb_add, isComplex = true)
    @GenerateNodeFactory
    abstract static class AddNode extends BinaryOpBuiltinNode {
        @Specialization
        static Object doIt(VirtualFrame frame, Object left, Object right,
                        @Bind Node inliningTarget,
                        @Cached ForeignBinarySlotNode binarySlotNode,
                        @Cached PyNumberAddNode opNode) {
            return binarySlotNode.execute(frame, inliningTarget, left, right, opNode);
        }
    }

    @Slot(value = SlotKind.nb_subtract, isComplex = true)
    @GenerateNodeFactory
    abstract static class SubNode extends BinaryOpBuiltinNode {
        @Specialization
        static Object doIt(VirtualFrame frame, Object left, Object right,
                        @Bind Node inliningTarget,
                        @Cached ForeignBinarySlotNode binarySlotNode,
                        @Cached PyNumberSubtractNode opNode) {
            return binarySlotNode.execute(frame, inliningTarget, left, right, opNode);
        }
    }

    @Slot(value = SlotKind.nb_multiply, isComplex = true)
    @GenerateNodeFactory
    abstract static class MulNode extends BinaryOpBuiltinNode {
        @Specialization
        static Object doIt(VirtualFrame frame, Object left, Object right,
                        @Bind Node inliningTarget,
                        @Cached ForeignBinarySlotNode binarySlotNode,
                        @Cached PyNumberMultiplyNode opNode) {
            return binarySlotNode.execute(frame, inliningTarget, left, right, opNode);
        }
    }

    @Slot(value = SlotKind.nb_remainder, isComplex = true)
    @GenerateNodeFactory
    abstract static class ModNode extends BinaryOpBuiltinNode {
        @Specialization
        static Object doIt(VirtualFrame frame, Object left, Object right,
                        @Bind Node inliningTarget,
                        @Cached ForeignBinarySlotNode binarySlotNode,
                        @Cached PyNumberRemainderNode opNode) {
            return binarySlotNode.execute(frame, inliningTarget, left, right, opNode);
        }
    }

    @Slot(value = SlotKind.nb_lshift, isComplex = true)
    @GenerateNodeFactory
    abstract static class LShiftNode extends BinaryOpBuiltinNode {
        @Specialization
        static Object doIt(VirtualFrame frame, Object left, Object right,
                        @Bind Node inliningTarget,
                        @Cached ForeignBinarySlotNode binarySlotNode,
                        @Cached PyNumberLshiftNode opNode) {
            return binarySlotNode.execute(frame, inliningTarget, left, right, opNode);
        }
    }

    @Slot(value = SlotKind.nb_rshift, isComplex = true)
    @GenerateNodeFactory
    abstract static class RShiftNode extends BinaryOpBuiltinNode {
        @Specialization
        static Object doIt(VirtualFrame frame, Object left, Object right,
                        @Bind Node inliningTarget,
                        @Cached ForeignBinarySlotNode binarySlotNode,
                        @Cached PyNumberRshiftNode opNode) {
            return binarySlotNode.execute(frame, inliningTarget, left, right, opNode);
        }
    }

    @Slot(value = SlotKind.nb_and, isComplex = true)
    @GenerateNodeFactory
    abstract static class AndNode extends BinaryOpBuiltinNode {
        @Specialization
        static Object doIt(VirtualFrame frame, Object left, Object right,
                        @Bind Node inliningTarget,
                        @Cached ForeignBinarySlotNode binarySlotNode,
                        @Cached PyNumberAndNode opNode) {
            return binarySlotNode.execute(frame, inliningTarget, left, right, opNode);
        }
    }

    @Slot(value = SlotKind.nb_xor, isComplex = true)
    @GenerateNodeFactory
    abstract static class XorNode extends BinaryOpBuiltinNode {
        @Specialization
        static Object doIt(VirtualFrame frame, Object left, Object right,
                        @Bind Node inliningTarget,
                        @Cached ForeignBinarySlotNode binarySlotNode,
                        @Cached PyNumberXorNode opNode) {
            return binarySlotNode.execute(frame, inliningTarget, left, right, opNode);
        }
    }

    @Slot(value = SlotKind.nb_or, isComplex = true)
    @GenerateNodeFactory
    abstract static class OrNode extends BinaryOpBuiltinNode {
        @Specialization
        static Object doIt(VirtualFrame frame, Object left, Object right,
                        @Bind Node inliningTarget,
                        @Cached ForeignBinarySlotNode binarySlotNode,
                        @Cached PyNumberOrNode opNode) {
            return binarySlotNode.execute(frame, inliningTarget, left, right, opNode);
        }
    }

    @Slot(value = SlotKind.nb_floor_divide, isComplex = true)
    @GenerateNodeFactory
    abstract static class FloorDivNode extends BinaryOpBuiltinNode {
        @Specialization
        static Object doIt(VirtualFrame frame, Object left, Object right,
                        @Bind Node inliningTarget,
                        @Cached ForeignBinarySlotNode binarySlotNode,
                        @Cached PyNumberFloorDivideNode opNode) {
            return binarySlotNode.execute(frame, inliningTarget, left, right, opNode);
        }
    }

    @Slot(value = SlotKind.nb_true_divide, isComplex = true)
    @GenerateNodeFactory
    abstract static class TrueDivNode extends BinaryOpBuiltinNode {
        @Specialization
        static Object doIt(VirtualFrame frame, Object left, Object right,
                        @Bind Node inliningTarget,
                        @Cached ForeignBinarySlotNode binarySlotNode,
                        @Cached PyNumberTrueDivideNode opNode) {
            return binarySlotNode.execute(frame, inliningTarget, left, right, opNode);
        }
    }

    @Slot(value = SlotKind.nb_divmod, isComplex = true)
    @GenerateNodeFactory
    abstract static class DivModNode extends BinaryOpBuiltinNode {
        @Specialization
        static Object doIt(VirtualFrame frame, Object left, Object right,
                        @Bind Node inliningTarget,
                        @Cached ForeignBinarySlotNode binarySlotNode,
                        @Cached PyNumberDivmodNode opNode) {
            return binarySlotNode.execute(frame, inliningTarget, left, right, opNode);
        }
    }

    @Slot(value = SlotKind.nb_power, isComplex = true)
    @GenerateNodeFactory
    abstract static class PowNode extends PythonTernaryBuiltinNode {

        @Specialization
        static Object doIt(VirtualFrame frame, Object v, Object w, Object z,
                        @Bind Node inliningTarget,
                        @Cached UnboxNode unboxV,
                        @Cached UnboxNode unboxW,
                        @Cached UnboxNode unboxZ,
                        @Cached PyNumberPowerNode power) {
            v = unboxV.execute(inliningTarget, v);
            w = unboxW.execute(inliningTarget, w);
            if (!(z instanceof PNone)) {
                z = unboxZ.execute(inliningTarget, z);
            }
            if (v == null || w == null || z == null) {
                return PNotImplemented.NOT_IMPLEMENTED;
            }
            return power.execute(frame, v, w, z);
        }
    }

    @Builtin(name = J___ROUND__, minNumOfPositionalArgs = 1, maxNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    abstract static class RoundNode extends PythonBinaryBuiltinNode {

        @Specialization
        Object doGeneric(VirtualFrame frame, Object self, Object n,
                        @Bind Node inliningTarget,
                        @Cached UnboxNode unboxNode,
                        @Cached("create(T___ROUND__)") LookupAndCallBinaryNode callRound) {
            Object unboxed = unboxNode.execute(inliningTarget, self);
            try {
                return callRound.executeObject(frame, unboxed, n);
            } catch (SpecialMethodNotFound ignore) {
                throw CompilerDirectives.shouldNotReachHere();
            }
        }
    }

    @Slot(value = SlotKind.tp_richcompare, isComplex = true)
    @GenerateNodeFactory
    public abstract static class ForeignBinaryComparisonNode extends TpSlotRichCompare.RichCmpBuiltinNode {
        @Specialization
        Object doComparison(VirtualFrame frame, Object left, Object right, RichCmpOp op,
                        @Bind Node inliningTarget,
                        @Cached UnboxNode unboxNode,
                        @Cached IsBuiltinObjectProfile exProfile,
                        @Cached PyObjectRichCompare richCompareNode) {
            Object unboxed = unboxNode.execute(inliningTarget, left);
            if (unboxed != null) {
                try {
                    return richCompareNode.execute(frame, inliningTarget, unboxed, right, op);
                } catch (PException ex) {
                    ex.expect(inliningTarget, PythonBuiltinClassType.TypeError, exProfile);
                }
            }
            return PNotImplemented.NOT_IMPLEMENTED;
        }
    }

    @Slot(value = SlotKind.nb_index, isComplex = true)
    @GenerateNodeFactory
    abstract static class IndexNode extends PythonUnaryBuiltinNode {
        @Specialization(limit = "3")
        protected static Object doIt(Object object,
                        @Bind Node inliningTarget,
                        @Cached PRaiseNode raiseNode,
                        @CachedLibrary("object") InteropLibrary lib,
                        @Cached GilNode gil) {
            assert !lib.isBoolean(object);
            gil.release(true);
            try {
                if (lib.fitsInInt(object)) {
                    try {
                        return lib.asInt(object);
                    } catch (UnsupportedMessageException e) {
                        CompilerDirectives.transferToInterpreterAndInvalidate();
                        throw new IllegalStateException("foreign value claims it fits into index-sized int, but doesn't");
                    }
                }
                if (lib.fitsInLong(object)) {
                    try {
                        return lib.asLong(object);
                    } catch (UnsupportedMessageException e) {
                        CompilerDirectives.transferToInterpreterAndInvalidate();
                        throw new IllegalStateException("foreign value claims it fits into index-sized long, but doesn't");
                    }
                }
                if (lib.fitsInBigInteger(object)) {
                    try {
                        var big = lib.asBigInteger(object);
                        return PFactory.createInt(PythonLanguage.get(inliningTarget), big);
                    } catch (UnsupportedMessageException e) {
                        CompilerDirectives.transferToInterpreterAndInvalidate();
                        throw new IllegalStateException("foreign value claims to be a big integer but isn't");
                    }
                }
                throw raiseNode.raise(inliningTarget, PythonBuiltinClassType.TypeError, ErrorMessages.OBJ_CANNOT_BE_INTERPRETED_AS_INTEGER, object);
            } finally {
                gil.acquire();
            }
        }
    }

    @Slot(value = SlotKind.tp_str, isComplex = true)
    @GenerateNodeFactory
    abstract static class StrNode extends PythonUnaryBuiltinNode {
        @Child private TruffleString.SwitchEncodingNode switchEncodingNode;

        @Specialization
        Object str(VirtualFrame frame, Object object,
                        @Bind Node inliningTarget,
                        @Cached UnboxNode unboxNode,
                        @CachedLibrary(limit = "3") InteropLibrary lib,
                        @Cached PyObjectStrAsTruffleStringNode strNode,
                        @Cached InlinedBranchProfile defaultCase) {
            final Object value = unboxNode.execute(inliningTarget, object);
            if (value == null) {
                defaultCase.enter(inliningTarget);
                return defaultConversion(frame, lib, object);
            }
            return strNode.execute(frame, inliningTarget, value);
        }

        protected TruffleString.SwitchEncodingNode getSwitchEncodingNode() {
            if (switchEncodingNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                switchEncodingNode = insert(TruffleString.SwitchEncodingNode.create());
            }
            return switchEncodingNode;
        }

        protected TruffleString defaultConversion(VirtualFrame frame, InteropLibrary lib, Object object) {
            try {
                return getSwitchEncodingNode().execute(lib.asTruffleString(lib.toDisplayString(object)), TS_ENCODING);
            } catch (UnsupportedMessageException e) {
                throw CompilerDirectives.shouldNotReachHere("toDisplayString result not convertible to String");
            }
        }
    }

    @Slot(value = SlotKind.tp_repr, isComplex = true)
    @GenerateNodeFactory
    abstract static class ReprNode extends StrNode {
        @Child private ObjectNodes.DefaultObjectReprNode defaultReprNode;

        @Override
        protected TruffleString defaultConversion(VirtualFrame frame, InteropLibrary lib, Object object) {
            try {
                if (getContext().getEnv().isHostObject(object)) {
                    boolean isMetaObject = lib.isMetaObject(object);
                    Object metaObject = null;
                    if (isMetaObject) {
                        metaObject = object;
                    } else if (lib.hasMetaObject(object)) {
                        metaObject = lib.getMetaObject(object);
                    }
                    if (metaObject != null) {
                        TruffleString displayName = getSwitchEncodingNode().execute(lib.asTruffleString(lib.toDisplayString(metaObject)), TS_ENCODING);
                        return simpleTruffleStringFormatUncached("<%s[%s] at 0x%s>", isMetaObject ? "JavaClass" : "JavaObject", displayName,
                                        PythonAbstractObject.systemHashCodeAsHexString(object));
                    }
                }
            } catch (UnsupportedMessageException e) {
                // fallthrough to default
            }
            if (defaultReprNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                defaultReprNode = insert(ObjectNodes.DefaultObjectReprNode.create());
            }
            return defaultReprNode.executeCached(frame, object);
        }
    }

    // NOTE: This does not get called e.g. for `~foreign`, only for non-__dunder__ methods or
    // explicit calls like `foreign.__invert__()`.
    @Slot(value = SlotKind.tp_getattro, isComplex = true)
    @GenerateNodeFactory
    abstract static class GetAttributeNode extends GetAttrBuiltinNode {
        @Specialization
        static Object doIt(VirtualFrame frame, Object self, Object name,
                        @Bind Node inliningTarget,
                        @Cached UnboxNode unboxNode,
                        @Cached ObjectBuiltins.GetAttributeNode objectGetattrNode,
                        @Cached IsBuiltinObjectProfile isAttrError,
                        @Cached ForeignGetattrNode foreignGetattrNode) {
            /*
             * We unbox so we automatically get all methods from bool, int or float. We want the
             * default Python attribute lookup first and try foreign members last. Because method
             * calls in a Python source should prioritize Python methods over foreign methods.
             */
            Object unboxed = unboxNode.execute(inliningTarget, self);
            if (unboxed != null) {
                try {
                    return objectGetattrNode.execute(frame, unboxed, name);
                } catch (PException e) {
                    e.expect(inliningTarget, PythonBuiltinClassType.AttributeError, isAttrError);
                }
            }

            return foreignGetattrNode.execute(inliningTarget, self, name);
        }
    }
}
