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

import com.oracle.graal.python.nodes.ErrorMessages;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.TypeError;

import com.oracle.graal.python.util.Supplier;

import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.SpecialMethodNames;
import com.oracle.graal.python.nodes.call.special.LookupAndCallUnaryNode;
import com.oracle.graal.python.nodes.call.special.LookupAndCallUnaryNode.NoAttributeHandler;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.NodeCost;

public enum UnaryArithmetic {
    Pos(SpecialMethodNames.__POS__, "+"),
    Neg(SpecialMethodNames.__NEG__, "-"),
    Invert(SpecialMethodNames.__INVERT__, "~");

    private final String methodName;
    private final String operator;
    private final Supplier<NoAttributeHandler> noAttributeHandler;

    UnaryArithmetic(String methodName, String operator) {
        this.methodName = methodName;
        this.operator = operator;
        this.noAttributeHandler = () -> new NoAttributeHandler() {
            @Child private PRaiseNode raiseNode = PRaiseNode.create();

            @Override
            public Object execute(Object receiver) {
                throw raiseNode.raise(TypeError, ErrorMessages.BAD_OPERAND_FOR, "unary ", operator, receiver);
            }
        };
    }

    public String getMethodName() {
        return methodName;
    }

    public String getOperator() {
        return operator;
    }

    public static final class UnaryArithmeticExpression extends ExpressionNode {
        @Child private LookupAndCallUnaryNode callNode;
        @Child private ExpressionNode operand;

        private UnaryArithmeticExpression(LookupAndCallUnaryNode callNode, ExpressionNode operand) {
            this.callNode = callNode;
            this.operand = operand;
        }

        @Override
        public Object execute(VirtualFrame frame) {
            return callNode.executeObject(frame, operand.execute(frame));
        }

        @Override
        public NodeCost getCost() {
            return NodeCost.NONE;
        }
    }

    public ExpressionNode create(ExpressionNode receiver) {
        return new UnaryArithmeticExpression(LookupAndCallUnaryNode.create(methodName, noAttributeHandler), receiver);
    }

    public LookupAndCallUnaryNode create() {
        return LookupAndCallUnaryNode.create(methodName, noAttributeHandler);
    }
}
