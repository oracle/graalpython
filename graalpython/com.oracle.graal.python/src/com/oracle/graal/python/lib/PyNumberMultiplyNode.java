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
package com.oracle.graal.python.lib;

import static com.oracle.graal.python.builtins.objects.ints.IntBuiltins.MulNode.mul;
import static com.oracle.graal.python.lib.CallBinaryOpNode.raiseNotSupported;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.builtins.objects.PNotImplemented;
import com.oracle.graal.python.builtins.objects.ints.PInt;
import com.oracle.graal.python.builtins.objects.type.TpSlots;
import com.oracle.graal.python.builtins.objects.type.TpSlots.GetCachedTpSlotsNode;
import com.oracle.graal.python.builtins.objects.type.slots.TpSlotBinaryOp.ReversibleSlot;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.expression.BinaryOpNode;
import com.oracle.graal.python.nodes.object.GetClassNode;
import com.oracle.graal.python.nodes.truffle.PythonIntegerTypes;
import com.oracle.graal.python.runtime.object.PFactory;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Exclusive;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.GenerateCached;
import com.oracle.truffle.api.dsl.GenerateInline;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.NeverDefault;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.dsl.TypeSystemReference;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.UnexpectedResultException;
import com.oracle.truffle.api.profiles.InlinedBranchProfile;

@GenerateCached(false)
@TypeSystemReference(PythonIntegerTypes.class)
abstract class PyNumberMultiplyBaseNode extends BinaryOpNode {

    /*
     * All the following fast paths need to be kept in sync with the corresponding builtin functions
     * in IntBuiltins
     */
    @Specialization(rewriteOn = ArithmeticException.class)
    static int doII(int x, int y) throws ArithmeticException {
        return Math.multiplyExact(x, y);
    }

    @Specialization(replaces = "doII")
    static long doIIL(int x, int y) {
        return x * (long) y;
    }

    @Specialization(rewriteOn = ArithmeticException.class)
    static long doLL(long x, long y) {
        return Math.multiplyExact(x, y);
    }

    @Specialization(replaces = "doLL")
    static Object doLongWithOverflow(long x, long y,
                    @Bind("this") Node inliningTarget) {
        /* Inlined version of Math.multiplyExact(x, y) with BigInteger fallback. */
        long r = x * y;
        long ax = Math.abs(x);
        long ay = Math.abs(y);
        if (((ax | ay) >>> 31 != 0)) {
            // Some bits greater than 2^31 that might cause overflow
            // Check the result using the divide operator
            // and check for the special case of Long.MIN_VALUE * -1
            if (((y != 0) && (r / y != x)) ||
                            (x == Long.MIN_VALUE && y == -1)) {
                return PFactory.createInt(PythonLanguage.get(inliningTarget), mul(PInt.longToBigInteger(x), PInt.longToBigInteger(y)));
            }
        }
        return r;
    }

    /*
     * All the following fast paths need to be kept in sync with the corresponding builtin functions
     * in FloatBuiltins
     */
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
}

@GenerateInline(inlineByDefault = true)
@GenerateUncached
public abstract class PyNumberMultiplyNode extends PyNumberMultiplyBaseNode {
    public abstract Object execute(VirtualFrame frame, Node inliningTarget, Object v, Object w);

    @Override
    public final Object execute(VirtualFrame frame, Object left, Object right) {
        return executeCached(frame, left, right);
    }

    public final Object executeCached(VirtualFrame frame, Object v, Object w) {
        return execute(frame, this, v, w);
    }

    public abstract int executeInt(VirtualFrame frame, Node inliningTarget, int left, int right) throws UnexpectedResultException;

    public abstract double executeDouble(VirtualFrame frame, Node inliningTarget, double left, double right) throws UnexpectedResultException;

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
                    @Cached SequenceRepeatHelperNode sequenceRepeatNode,
                    @Cached PRaiseNode raiseNode) {
        Object classV = getVClass.execute(inliningTarget, v);
        Object classW = getWClass.execute(inliningTarget, w);
        TpSlots slotsV = getVSlots.execute(inliningTarget, classV);
        TpSlots slotsW = getWSlots.execute(inliningTarget, classW);
        Object result = callBinaryOp1Node.execute(frame, inliningTarget, v, classV, slotsV, w, classW, slotsW, ReversibleSlot.NB_MULTIPLY);
        if (result != PNotImplemented.NOT_IMPLEMENTED) {
            hasNbMulResult.enter(inliningTarget);
            return result;
        }
        if (slotsV.sq_repeat() != null) {
            vHasSqRepeat.enter(inliningTarget);
            return sequenceRepeatNode.execute(frame, slotsV.sq_repeat(), v, w);
        } else if (slotsW.sq_repeat() != null) {
            wHasSqRepeat.enter(inliningTarget);
            return sequenceRepeatNode.execute(frame, slotsW.sq_repeat(), w, v);
        }
        return raiseNotSupported(inliningTarget, v, w, "*", raiseNode);
    }

    @NeverDefault
    public static PyNumberMultiplyNode create() {
        return PyNumberMultiplyNodeGen.create();
    }

    public static PyNumberMultiplyNode getUncached() {
        return PyNumberMultiplyNodeGen.getUncached();
    }
}
