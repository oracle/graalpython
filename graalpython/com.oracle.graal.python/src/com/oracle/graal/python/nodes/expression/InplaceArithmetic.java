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

import static com.oracle.graal.python.runtime.exception.PythonErrorType.TypeError;

import java.util.function.Supplier;

import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.SpecialMethodNames;

public enum InplaceArithmetic {
    IAdd(SpecialMethodNames.__IADD__, "+="),
    ISub(SpecialMethodNames.__ISUB__, "-="),
    IMul(SpecialMethodNames.__IMUL__, "*="),
    ITrueDiv(SpecialMethodNames.__ITRUEDIV__, "/="),
    IFloorDiv(SpecialMethodNames.__IFLOORDIV__, "//="),
    IMod(SpecialMethodNames.__IMOD__, "%="),
    IPow(SpecialMethodNames.__IPOW__, "**="),
    ILShift(SpecialMethodNames.__ILSHIFT__, "<<="),
    IRShift(SpecialMethodNames.__IRSHIFT__, ">>="),
    IAnd(SpecialMethodNames.__IAND__, "&="),
    IOr(SpecialMethodNames.__IOR__, "|="),
    IXor(SpecialMethodNames.__IXOR__, "^="),
    IMatMul(SpecialMethodNames.__IMATMUL__, "@");

    private final String methodName;
    private final String operator;
    private final Supplier<LookupAndCallInplaceNode.NotImplementedHandler> notImplementedHandler;

    InplaceArithmetic(String methodName, String operator) {
        this.methodName = methodName;
        this.operator = operator;
        this.notImplementedHandler = () -> new LookupAndCallInplaceNode.NotImplementedHandler() {
            @Child private PRaiseNode raiseNode = PRaiseNode.create();

            @Override
            public Object execute(Object arg, Object arg2) {
                throw raiseNode.raise(TypeError, "unsupported operand type(s) for %s: '%p' and '%p'", operator, arg, arg2);
            }
        };
    }

    public String getMethodName() {
        return methodName;
    }

    public String getOperator() {
        return operator;
    }

    public LookupAndCallInplaceNode create(ExpressionNode left, ExpressionNode right) {
        return LookupAndCallInplaceNode.createWithBinary(methodName, left, right, notImplementedHandler);
    }

    public LookupAndCallInplaceNode create() {
        return LookupAndCallInplaceNode.createWithBinary(methodName, null, null, notImplementedHandler);
    }
}
