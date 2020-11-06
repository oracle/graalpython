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

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.function.PArguments;
import com.oracle.graal.python.builtins.objects.function.Signature;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.SpecialMethodNames;
import com.oracle.graal.python.nodes.call.special.LookupAndCallTernaryNode;
import com.oracle.graal.python.nodes.call.special.LookupAndCallTernaryNode.NotImplementedHandler;
import com.oracle.graal.python.util.PythonUtils;
import com.oracle.graal.python.util.Supplier;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.NodeCost;

public enum TernaryArithmetic {
    Pow(SpecialMethodNames.__POW__, "**", "pow");

    private final String methodName;
    private final String operator;
    private final Supplier<NotImplementedHandler> notImplementedHandler;

    TernaryArithmetic(String methodName, String operator, String operatorFunction) {
        this.methodName = methodName;
        this.operator = operator;
        this.notImplementedHandler = () -> new NotImplementedHandler() {
            @Child private PRaiseNode raiseNode = PRaiseNode.create();

            @Override
            public Object execute(Object arg, Object arg2, Object arg3) {
                if (arg3 instanceof PNone) {
                    throw raiseNode.raise(TypeError, ErrorMessages.UNSUPPORTED_OPERAND_TYPES_FOR_S_PR_S_P_AND_P, operator, operatorFunction, arg, arg2);
                } else {
                    throw raiseNode.raise(TypeError, ErrorMessages.UNSUPPORTED_OPERAND_TYPES_FOR_S_P_P_P, operatorFunction, arg, arg2, arg3);
                }
            }
        };
    }

    public String getMethodName() {
        return methodName;
    }

    public String getOperator() {
        return operator;
    }

    public static final class TernaryArithmeticExpression extends ExpressionNode {
        @Child private LookupAndCallTernaryNode callNode;
        @Child private ExpressionNode left;
        @Child private ExpressionNode right;

        private TernaryArithmeticExpression(LookupAndCallTernaryNode callNode, ExpressionNode left, ExpressionNode right) {
            this.callNode = callNode;
            this.left = left;
            this.right = right;
        }

        @Override
        public Object execute(VirtualFrame frame) {
            return callNode.execute(frame, left.execute(frame), right.execute(frame), PNone.NONE);
        }

        @Override
        public NodeCost getCost() {
            return NodeCost.NONE;
        }
    }

    /**
     * A helper root node that dispatches to {@link LookupAndCallTernaryNode} to execute the
     * provided ternary operator. This node is mostly useful to use such operators from a location
     * without a frame (e.g. from interop). Note: this is just a root node and won't do any
     * signature checking.
     */
    static final class CallTernaryArithmeticRootNode extends CallArithmeticRootNode {
        static final Signature SIGNATURE_TERNARY = new Signature(3, false, -1, false, new String[]{"x", "y", "z"}, null);

        @Child private LookupAndCallTernaryNode callTernaryNode;

        private final TernaryArithmetic ternaryOperator;

        CallTernaryArithmeticRootNode(PythonLanguage language, TernaryArithmetic ternaryOperator) {
            super(language);
            this.ternaryOperator = ternaryOperator;
        }

        @Override
        public Signature getSignature() {
            return SIGNATURE_TERNARY;
        }

        @Override
        protected Object doCall(VirtualFrame frame) {
            if (callTernaryNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                callTernaryNode = insert(ternaryOperator.create());
            }
            return callTernaryNode.execute(frame, PArguments.getArgument(frame, 0), PArguments.getArgument(frame, 1), PArguments.getArgument(frame, 2));
        }
    }

    public ExpressionNode create(ExpressionNode x, ExpressionNode y) {
        return new TernaryArithmeticExpression(LookupAndCallTernaryNode.createReversible(methodName, notImplementedHandler), x, y);
    }

    public LookupAndCallTernaryNode create() {
        return LookupAndCallTernaryNode.createReversible(methodName, notImplementedHandler);
    }

    /**
     * Creates a call target with a specific root node for this ternary operator such that the
     * operator can be executed via a full call. This is in particular useful, if you want to
     * execute an operator without a frame (e.g. from interop). It is not recommended to use this
     * method directly. In order to enable AST sharing, you should use
     * {@link PythonLanguage#getOrCreateTernaryArithmeticCallTarget(TernaryArithmetic)}.
     */
    public RootCallTarget createCallTarget(PythonLanguage language) {
        return PythonUtils.getOrCreateCallTarget(new CallTernaryArithmeticRootNode(language, this));
    }
}
