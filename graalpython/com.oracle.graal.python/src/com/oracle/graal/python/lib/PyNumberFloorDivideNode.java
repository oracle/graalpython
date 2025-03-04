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

import com.oracle.graal.python.builtins.objects.type.slots.TpSlotBinaryOp.ReversibleSlot;
import com.oracle.graal.python.nodes.expression.BinaryOpNode;
import com.oracle.graal.python.nodes.truffle.PythonIntegerTypes;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateCached;
import com.oracle.truffle.api.dsl.GenerateInline;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.NeverDefault;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.dsl.TypeSystemReference;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;

@GenerateCached(false)
@TypeSystemReference(PythonIntegerTypes.class)
abstract class PyNumberFloorDivideBaseNode extends BinaryOpNode {

    /*
     * All the following fast paths need to be kept in sync with the corresponding builtin functions
     * in IntBuiltins, FloatBuiltins, ...
     */
    @Specialization(guards = "!specialCase(left, right)")
    public static int doII(int left, int right) {
        return Math.floorDiv(left, right);
    }

    @Specialization(guards = "!specialCase(left, right)")
    public static long doLL(long left, long right) {
        return Math.floorDiv(left, right);
    }

    @Specialization(guards = "!isZero(right)")
    public static double doLD(long left, double right) {
        return doDD(left, right);
    }

    @Specialization(guards = "right != 0")
    public static double doDL(double left, long right) {
        return doDD(left, right);
    }

    @Specialization(guards = "!isZero(right)")
    public static double doDD(double left, double right) {
        return Math.floor(left / right);
    }

    protected static boolean specialCase(int left, int right) {
        return right == 0 || left == Integer.MIN_VALUE && right == -1;
    }

    protected static boolean specialCase(long left, long right) {
        return right == 0 || left == Long.MIN_VALUE && right == -1;
    }

    protected static boolean isZero(double right) {
        return right == 0.0;
    }
}

@GenerateInline(false)
@GenerateUncached
public abstract class PyNumberFloorDivideNode extends PyNumberFloorDivideBaseNode {

    @Specialization
    public static Object doIt(VirtualFrame frame, Object v, Object w,
                    @Bind Node inliningTarget,
                    @Cached CallBinaryOpNode callBinaryOpNode) {
        return callBinaryOpNode.execute(frame, inliningTarget, v, w, ReversibleSlot.NB_FLOOR_DIVIDE, "//");
    }

    @NeverDefault
    public static PyNumberFloorDivideNode create() {
        return PyNumberFloorDivideNodeGen.create();
    }

    public static PyNumberFloorDivideNode getUncached() {
        return PyNumberFloorDivideNodeGen.getUncached();
    }
}
