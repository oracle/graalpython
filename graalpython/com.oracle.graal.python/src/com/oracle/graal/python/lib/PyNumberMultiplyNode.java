/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.lib;

import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.objects.PNotImplemented;
import com.oracle.graal.python.builtins.objects.type.TpSlots;
import com.oracle.graal.python.builtins.objects.type.TpSlots.GetCachedTpSlotsNode;
import com.oracle.graal.python.builtins.objects.type.slots.TpSlot;
import com.oracle.graal.python.builtins.objects.type.slots.TpSlotBinaryOp.BinaryOpSlot;
import com.oracle.graal.python.builtins.objects.type.slots.TpSlotSizeArgFun.CallSlotSizeArgFun;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.PRaiseNode.Lazy;
import com.oracle.graal.python.nodes.expression.BinaryOpNode;
import com.oracle.graal.python.nodes.object.GetClassNode;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.truffle.api.HostCompilerDirectives.InliningCutoff;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Exclusive;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.GenerateInline;
import com.oracle.truffle.api.dsl.NeverDefault;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.UnexpectedResultException;
import com.oracle.truffle.api.profiles.InlinedBranchProfile;

@GenerateInline(inlineByDefault = true)
public abstract class PyNumberMultiplyNode extends BinaryOpNode {
    public abstract Object execute(VirtualFrame frame, Node inliningTarget, Object v, Object w);

    @Override
    public final Object executeObject(VirtualFrame frame, Object left, Object right) {
        return executeCached(frame, left, right);
    }

    public final Object executeCached(VirtualFrame frame, Object v, Object w) {
        return execute(frame, this, v, w);
    }

    public abstract int executeInt(VirtualFrame frame, Node inliningTarget, int left, int right) throws UnexpectedResultException;

    public abstract double executeDouble(VirtualFrame frame, Node inliningTarget, double left, double right) throws UnexpectedResultException;

    /*
     * All the following fast paths need to be kept in sync with the corresponding builtin functions
     * in IntBuiltins, FloatBuiltins, ListBuiltins, ...
     */

    @Specialization(rewriteOn = ArithmeticException.class)
    public static int doII(int x, int y) throws ArithmeticException {
        return Math.multiplyExact(x, y);
    }

    @Specialization(replaces = "doII")
    public static long doIIL(int x, int y) {
        return x * (long) y;
    }

    @Specialization(rewriteOn = ArithmeticException.class)
    public static long doLL(long x, long y) {
        return Math.multiplyExact(x, y);
    }

    @Specialization
    public static double doDL(double left, long right) {
        return left * right;
    }

    @Specialization
    public static double doLD(long left, double right) {
        return left * right;
    }

    @Specialization
    public static double doDD(double left, double right) {
        return left * right;
    }

    @Fallback
    static Object doIt(VirtualFrame frame, Node inliningTarget, Object v, Object w,
                    @Exclusive @Cached GetClassNode getVClass,
                    @Cached GetCachedTpSlotsNode getVSlots,
                    @Cached GetCachedTpSlotsNode getWSlots,
                    @Exclusive @Cached GetClassNode getWClass,
                    @Cached CallBinaryOp1Node callBinaryOp1Node,
                    @Cached InlinedBranchProfile hasNbMulResult,
                    @Cached InlinedBranchProfile vHasSqRepeat,
                    @Cached InlinedBranchProfile wHasSqRepeat,
                    @Cached PyIndexCheckNode indexCheckNode,
                    @Cached PyNumberAsSizeNode asSizeNode,
                    @Cached CallSlotSizeArgFun callSlotNode,
                    @Cached PRaiseNode.Lazy raiseNode) {
        Object classV = getVClass.execute(inliningTarget, v);
        Object classW = getWClass.execute(inliningTarget, w);
        TpSlots slotsV = getVSlots.execute(inliningTarget, classV);
        TpSlots slotsW = getWSlots.execute(inliningTarget, classW);
        TpSlot slotV = slotsV.nb_multiply();
        TpSlot slotW = slotsW.nb_multiply();
        if (slotV != null || slotW != null) {
            Object result = callBinaryOp1Node.execute(frame, inliningTarget, v, classV, slotV, w, classW, slotW, BinaryOpSlot.NB_MULTIPLY);
            if (result != PNotImplemented.NOT_IMPLEMENTED) {
                hasNbMulResult.enter(inliningTarget);
                return result;
            }
        }
        if (slotsV.sq_repeat() != null) {
            vHasSqRepeat.enter(inliningTarget);
            return sequenceRepeat(frame, inliningTarget, slotsV.sq_repeat(), v, w,
                            indexCheckNode, asSizeNode, callSlotNode, raiseNode);
        } else if (slotsW.sq_repeat() != null) {
            wHasSqRepeat.enter(inliningTarget);
            return sequenceRepeat(frame, inliningTarget, slotsW.sq_repeat(), w, v,
                            indexCheckNode, asSizeNode, callSlotNode, raiseNode);
        }
        return raiseNotSupported(inliningTarget, v, w, raiseNode);
    }

    @InliningCutoff
    private static PException raiseNotSupported(Node inliningTarget, Object v, Object w, Lazy raiseNode) {
        return raiseNode.get(inliningTarget).raise(PythonBuiltinClassType.TypeError, ErrorMessages.UNSUPPORTED_OPERAND_TYPES_FOR_S_P_AND_P, "+", v, w);
    }

    private static Object sequenceRepeat(VirtualFrame frame, Node inliningTarget, TpSlot slot, Object seq, Object n,
                    PyIndexCheckNode indexCheckNode,
                    PyNumberAsSizeNode asSizeNode,
                    CallSlotSizeArgFun callSlotNode,
                    PRaiseNode.Lazy raiseNode) {
        if (indexCheckNode.execute(inliningTarget, n)) {
            int count = asSizeNode.execute(frame, inliningTarget, n, PythonBuiltinClassType.OverflowError);
            return callSlotNode.execute(frame, inliningTarget, slot, seq, count);
        } else {
            throw raiseNonIntSqMul(inliningTarget, n, raiseNode);
        }
    }

    @InliningCutoff
    private static PException raiseNonIntSqMul(Node inliningTarget, Object n, Lazy raiseNode) {
        throw raiseNode.get(inliningTarget).raise(PythonBuiltinClassType.TypeError, ErrorMessages.CANT_MULTIPLY_SEQ_BY_NON_INT, n);
    }

    @NeverDefault
    public static PyNumberMultiplyNode create() {
        return PyNumberMultiplyNodeGen.create();
    }
}
