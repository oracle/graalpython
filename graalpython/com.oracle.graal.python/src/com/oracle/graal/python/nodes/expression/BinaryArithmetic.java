/*
 * Copyright (c) 2018, 2020, Oracle and/or its affiliates. All rights reserved.
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

import static com.oracle.graal.python.runtime.exception.PythonErrorType.TypeError;

import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.SpecialMethodNames;
import com.oracle.graal.python.nodes.call.special.LookupAndCallBinaryNode;
import com.oracle.graal.python.nodes.call.special.LookupAndCallBinaryNode.NotImplementedHandler;
import com.oracle.graal.python.util.Supplier;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.NodeCost;

public enum BinaryArithmetic {
    Add(SpecialMethodNames.__ADD__, "+"),
    Sub(SpecialMethodNames.__SUB__, "-"),
    Mul(SpecialMethodNames.__MUL__, "*"),
    TrueDiv(SpecialMethodNames.__TRUEDIV__, "/"),
    FloorDiv(SpecialMethodNames.__FLOORDIV__, "//"),
    Mod(SpecialMethodNames.__MOD__, "%"),
    LShift(SpecialMethodNames.__LSHIFT__, "<<"),
    RShift(SpecialMethodNames.__RSHIFT__, ">>"),
    And(SpecialMethodNames.__AND__, "&"),
    Or(SpecialMethodNames.__OR__, "|"),
    Xor(SpecialMethodNames.__XOR__, "^"),
    MatMul(SpecialMethodNames.__MATMUL__, "@"),
    Pow(SpecialMethodNames.__POW__, "**");

    private final String methodName;
    private final String operator;
    private final Supplier<NotImplementedHandler> notImplementedHandler;

    BinaryArithmetic(String methodName, String operator) {
        this.methodName = methodName;
        this.operator = operator;
        this.notImplementedHandler = () -> new NotImplementedHandler() {
            @Override
            public Object execute(Object arg, Object arg2) {
                throw PRaiseNode.getUncached().raise(TypeError, "unsupported operand type(s) for %s: '%p' and '%p'", operator, arg, arg2);
            }
        };
    }

    public String getMethodName() {
        return methodName;
    }

    public String getOperator() {
        return operator;
    }

    public static final class BinaryArithmeticExpression extends ExpressionNode {
        @Child private LookupAndCallBinaryNode callNode;
        @Child private ExpressionNode left;
        @Child private ExpressionNode right;

        private BinaryArithmeticExpression(LookupAndCallBinaryNode callNode, ExpressionNode left, ExpressionNode right) {
            this.callNode = callNode;
            this.left = left;
            this.right = right;
        }

        @Override
        public Object execute(VirtualFrame frame) {
            return callNode.executeObject(frame, left.execute(frame), right.execute(frame));
        }

        @Override
        public NodeCost getCost() {
            return NodeCost.NONE;
        }
    }

    public ExpressionNode create(ExpressionNode left, ExpressionNode right) {
        return new BinaryArithmeticExpression(LookupAndCallBinaryNode.createReversible(methodName, notImplementedHandler), left, right);
    }

    public LookupAndCallBinaryNode create() {
        return LookupAndCallBinaryNode.createReversible(methodName, notImplementedHandler);
    }
}
