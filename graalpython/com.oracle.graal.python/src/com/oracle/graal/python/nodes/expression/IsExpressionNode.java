/*
 * Copyright (c) 2018, 2019, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.nodes.expression;

import static com.oracle.graal.python.nodes.SpecialMethodNames.__EQ__;

import com.oracle.graal.python.builtins.objects.cext.CExtNodes;
import com.oracle.graal.python.builtins.objects.cext.PythonAbstractNativeObject;
import com.oracle.graal.python.builtins.objects.code.PCode;
import com.oracle.graal.python.builtins.objects.ints.PInt;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.nodes.Node;

public abstract class IsExpressionNode extends BinaryOpNode {
    public static IsExpressionNode create(ExpressionNode left, ExpressionNode right) {
        return IsExpressionNodeGen.create(left, right);
    }

    @Specialization
    boolean doIt(Object left, Object right,
                 @Cached IsNode isNode) {
        return isNode.execute(left, right);
    }

    public abstract static class IsNode extends Node {

        public abstract boolean execute(Object left, Object right);

        @Specialization
        boolean doBB(boolean left, boolean right) {
            return left == right;
        }

        @SuppressWarnings("unused")
        @Specialization
        boolean doBI(boolean left, int right) {
            return false;
        }

        @SuppressWarnings("unused")
        @Specialization
        boolean doBL(boolean left, long right) {
            return false;
        }

        @SuppressWarnings("unused")
        @Specialization
        boolean doBD(boolean left, double right) {
            return false;
        }

        @SuppressWarnings("unused")
        @Specialization
        boolean doIB(int left, boolean right) {
            return false;
        }

        @Specialization
        boolean doII(int left, int right) {
            return left == right;
        }

        @Specialization
        boolean doIL(int left, long right) {
            return left == right;
        }

        @SuppressWarnings("unused")
        @Specialization
        boolean doID(int left, double right) {
            return false;
        }

        @Specialization
        boolean doIP(int left, PInt right) {
            try {
                return right.intValueExact() == left;
            } catch (ArithmeticException e) {
                return false;
            }
        }

        @SuppressWarnings("unused")
        @Specialization
        boolean doLB(long left, boolean right) {
            return false;
        }

        @Specialization
        boolean doLI(long left, int right) {
            return left == right;
        }

        @Specialization
        boolean doLL(long left, long right) {
            return left == right;
        }

        @SuppressWarnings("unused")
        @Specialization
        boolean doLD(long left, double right) {
            return false;
        }

        @Specialization
        boolean doLP(long left, PInt right) {
            try {
                return left == right.longValueExact();
            } catch (ArithmeticException e) {
                return false;
            }
        }

        @SuppressWarnings("unused")
        @Specialization
        boolean doDB(double left, boolean right) {
            return false;
        }

        @SuppressWarnings("unused")
        @Specialization
        boolean doDI(double left, int right) {
            return false;
        }

        @SuppressWarnings("unused")
        @Specialization
        boolean doDL(double left, long right) {
            return false;
        }

        @Specialization
        boolean doDD(double left, double right) {
            return left == right;
        }

        @Specialization
        boolean doNative(PythonAbstractNativeObject left, PythonAbstractNativeObject right,
                        @Cached CExtNodes.PointerCompareNode isNode) {
            return isNode.execute(__EQ__, left, right);
        }

        @Specialization
        boolean doCode(PCode left, PCode right) {
            // Special case for code objects: Frames create them on-demand even if they refer to the
            // same function. So we need to compare the root nodes.
            RootCallTarget leftCt = left.getRootCallTarget();
            RootCallTarget rightCt = right.getRootCallTarget();
            if (leftCt != null && rightCt != null) {
                // TODO: handle splitting, i.e., cloned root nodes
                return leftCt.getRootNode() == rightCt.getRootNode();
            }
            return left == right;
        }

        @Fallback
        boolean doGeneric(Object left, Object right) {
            return left == right;
        }
    }
}
