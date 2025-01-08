/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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
import com.oracle.graal.python.builtins.objects.type.TpSlots.GetCachedTpSlotsNode;
import com.oracle.graal.python.builtins.objects.type.slots.TpSlot;
import com.oracle.graal.python.builtins.objects.type.slots.TpSlotBinaryOp.BinaryOpSlot;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PRaiseNode.Lazy;
import com.oracle.graal.python.nodes.expression.BinaryOpNode;
import com.oracle.graal.python.nodes.object.GetClassNode;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.graal.python.util.OverflowException;
import com.oracle.truffle.api.HostCompilerDirectives.InliningCutoff;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.GenerateInline;
import com.oracle.truffle.api.dsl.NeverDefault;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.UnexpectedResultException;

@GenerateInline(inlineByDefault = true)
public abstract class PyNumberFloorDivideNode extends BinaryOpNode {
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
     * in IntBuiltins, FloatBuiltins, ...
     */
    @Specialization(rewriteOn = OverflowException.class)
    static int doII(int left, int right) throws OverflowException {
        if (right == 0 || (left == Integer.MIN_VALUE && right == -1)) {
            throw OverflowException.INSTANCE;
        }
        return Math.floorDiv(left, right);
    }

    @Specialization(rewriteOn = OverflowException.class)
    static long doLL(long left, long right) throws OverflowException {
        if (right == 0 || (left == Long.MIN_VALUE && right == -1)) {
            throw OverflowException.INSTANCE;
        }
        return Math.floorDiv(left, right);
    }

    @Specialization(rewriteOn = OverflowException.class)
    static double doID(int left, double right) throws OverflowException {
        return doDD(left, right);
    }

    @Specialization(rewriteOn = OverflowException.class)
    static double doDI(double left, int right) throws OverflowException {
        return doDD(left, right);
    }

    @Specialization(rewriteOn = OverflowException.class)
    static double doDD(double left, double right) throws OverflowException {
        if (right == 0.0) {
            throw OverflowException.INSTANCE;
        }
        return Math.floor(left / right);
    }

    @Fallback
    static Object doIt(VirtualFrame frame, Node inliningTarget, Object v, Object w,
                    @Cached GetClassNode getVClass,
                    @Cached GetCachedTpSlotsNode getVSlots,
                    @Cached GetCachedTpSlotsNode getWSlots,
                    @Cached GetClassNode getWClass,
                    @Cached CallBinaryOp1Node callBinaryOp1Node,
                    @Cached Lazy raiseNode) {
        Object classV = getVClass.execute(inliningTarget, v);
        Object classW = getWClass.execute(inliningTarget, w);
        TpSlot slotV = getVSlots.execute(inliningTarget, classV).nb_floor_divide();
        TpSlot slotW = getWSlots.execute(inliningTarget, classW).nb_floor_divide();
        if (slotV != null || slotW != null) {
            Object result = callBinaryOp1Node.execute(frame, inliningTarget, v, classV, slotV, w, classW, slotW, BinaryOpSlot.NB_FLOOR_DIVIDE);
            if (result != PNotImplemented.NOT_IMPLEMENTED) {
                return result;
            }
        }
        return raiseNotSupported(inliningTarget, v, w, raiseNode);
    }

    @InliningCutoff
    private static PException raiseNotSupported(Node inliningTarget, Object v, Object w, Lazy raiseNode) {
        return raiseNode.get(inliningTarget).raise(PythonBuiltinClassType.TypeError, ErrorMessages.UNSUPPORTED_OPERAND_TYPES_FOR_S_P_AND_P, "//", v, w);
    }

    @NeverDefault
    public static PyNumberFloorDivideNode create() {
        return PyNumberFloorDivideNodeGen.create();
    }
}
