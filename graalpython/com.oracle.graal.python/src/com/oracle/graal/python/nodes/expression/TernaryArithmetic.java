/*
 * Copyright (c) 2018, 2022, Oracle and/or its affiliates. All rights reserved.
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
import static com.oracle.graal.python.util.PythonUtils.tsArray;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.function.PArguments;
import com.oracle.graal.python.builtins.objects.function.Signature;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.SpecialMethodNames;
import com.oracle.graal.python.nodes.call.special.LookupAndCallTernaryNode;
import com.oracle.graal.python.nodes.call.special.LookupAndCallTernaryNode.NotImplementedHandler;
import com.oracle.graal.python.util.Supplier;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.strings.TruffleString;

public enum TernaryArithmetic {
    Pow(SpecialMethodNames.T___POW__, "**", "pow");

    private final TruffleString methodName;
    private final Supplier<NotImplementedHandler> notImplementedHandler;

    TernaryArithmetic(TruffleString methodName, @SuppressWarnings("unused") String operator, String operatorFunction) {
        this.methodName = methodName;
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

    public TruffleString getMethodName() {
        return methodName;
    }

    /**
     * A helper root node that dispatches to {@link LookupAndCallTernaryNode} to execute the
     * provided ternary operator. This node is mostly useful to use such operators from a location
     * without a frame (e.g. from interop). Note: this is just a root node and won't do any
     * signature checking.
     */
    static final class CallTernaryArithmeticRootNode extends CallArithmeticRootNode {
        static final Signature SIGNATURE_TERNARY = new Signature(3, false, -1, false, tsArray("x", "y", "z"), null);

        @Child private LookupAndCallTernaryNode callTernaryNode;

        private final TernaryArithmetic ternaryOperator;

        private CallTernaryArithmeticRootNode(PythonLanguage language, TernaryArithmetic ternaryOperator) {
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

    public LookupAndCallTernaryNode create() {
        return LookupAndCallTernaryNode.createReversible(methodName, notImplementedHandler);
    }

    /**
     * Creates a root node for this ternary operator such that the operator can be executed via a
     * full call.
     */
    public RootNode createRootNode(PythonLanguage language) {
        return new CallTernaryArithmeticRootNode(language, this);
    }
}
