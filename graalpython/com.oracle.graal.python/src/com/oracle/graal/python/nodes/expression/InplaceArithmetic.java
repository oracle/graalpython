/*
 * Copyright (c) 2018, 2021, Oracle and/or its affiliates. All rights reserved.
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
import com.oracle.graal.python.builtins.objects.type.SpecialMethodSlot;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.SpecialMethodNames;
import com.oracle.graal.python.nodes.expression.BinaryArithmetic.CallBinaryArithmeticRootNode;
import com.oracle.graal.python.nodes.expression.TernaryArithmetic.CallTernaryArithmeticRootNode;
import com.oracle.graal.python.nodes.literal.ObjectLiteralNode;
import com.oracle.graal.python.util.Supplier;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.RootNode;

public enum InplaceArithmetic {
    IAdd(SpecialMethodSlot.IAdd, "+=", BinaryArithmetic.Add),
    ISub(SpecialMethodNames.__ISUB__, "-=", BinaryArithmetic.Sub),
    IMul(SpecialMethodSlot.IMul, "*=", BinaryArithmetic.Mul),
    ITrueDiv(SpecialMethodNames.__ITRUEDIV__, "/=", BinaryArithmetic.TrueDiv),
    IFloorDiv(SpecialMethodNames.__IFLOORDIV__, "//=", BinaryArithmetic.FloorDiv),
    IMod(SpecialMethodNames.__IMOD__, "%=", BinaryArithmetic.Mod),
    IPow(SpecialMethodNames.__IPOW__, "**=", BinaryArithmetic.Pow, true),
    ILShift(SpecialMethodNames.__ILSHIFT__, "<<=", BinaryArithmetic.LShift),
    IRShift(SpecialMethodNames.__IRSHIFT__, ">>=", BinaryArithmetic.RShift),
    IAnd(SpecialMethodNames.__IAND__, "&=", BinaryArithmetic.And),
    IOr(SpecialMethodNames.__IOR__, "|=", BinaryArithmetic.Or),
    IXor(SpecialMethodNames.__IXOR__, "^=", BinaryArithmetic.Xor),
    IMatMul(SpecialMethodNames.__IMATMUL__, "@", BinaryArithmetic.MatMul);

    final String methodName;
    final SpecialMethodSlot slot;
    final String operator;
    final boolean isTernary;
    final Supplier<LookupAndCallInplaceNode.NotImplementedHandler> notImplementedHandler;
    final BinaryArithmetic binary;
    final String binaryOpName;

    InplaceArithmetic(String methodName, String operator, BinaryArithmetic binary) {
        this(methodName, operator, binary, false);
    }

    InplaceArithmetic(SpecialMethodSlot slot, String operator, BinaryArithmetic binary) {
        this(slot.getName(), operator, binary, false, slot);
    }

    InplaceArithmetic(String methodName, String operator, BinaryArithmetic binary, boolean isTernary) {
        this(methodName, operator, binary, isTernary, null);
    }

    InplaceArithmetic(String methodName, String operator, BinaryArithmetic binary, boolean isTernary, SpecialMethodSlot slot) {
        this.methodName = methodName;
        this.operator = operator;
        this.binary = binary;
        this.isTernary = isTernary;
        this.binaryOpName = methodName.replaceFirst("__i", "__");
        this.slot = slot;

        this.notImplementedHandler = () -> new LookupAndCallInplaceNode.NotImplementedHandler() {
            @Child private PRaiseNode raiseNode = PRaiseNode.create();

            @Override
            public Object execute(Object arg, Object arg2) {
                throw raiseNode.raise(TypeError, ErrorMessages.UNSUPPORTED_OPERAND_TYPES_FOR_S_P_AND_P, operator, arg, arg2);
            }
        };
    }

    public static ExpressionNode createInplaceOperation(String string, ExpressionNode left, ExpressionNode right) {
        switch (string) {
            case "+=":
                return IAdd.create(left, right);
            case "-=":
                return ISub.create(left, right);
            case "*=":
                return IMul.create(left, right);
            case "/=":
                return ITrueDiv.create(left, right);
            case "//=":
                return IFloorDiv.create(left, right);
            case "%=":
                return IMod.create(left, right);
            case "**=":
                return IPow.create(left, right);
            case "<<=":
                return ILShift.create(left, right);
            case ">>=":
                return IRShift.create(left, right);
            case "&=":
                return IAnd.create(left, right);
            case "|=":
                return IOr.create(left, right);
            case "^=":
                return IXor.create(left, right);
            case "@=":
                return IMatMul.create(left, right);
            default:
                throw new RuntimeException("unexpected operation: " + string);
        }
    }

    public String getMethodName() {
        return methodName;
    }

    public String getOperator() {
        return operator;
    }

    public boolean isTernary() {
        return isTernary;
    }

    /**
     * A helper root node that dispatches to {@link LookupAndCallInplaceNode} to execute the
     * provided in-place operator. This node is mostly useful to use such operators from a location
     * without a frame (e.g. from interop). Note: this is just a root node and won't do any
     * signature checking.
     */
    static final class CallInplaceArithmeticRootNode extends CallArithmeticRootNode {

        @Child private LookupAndCallInplaceNode callInplaceNode;

        private final InplaceArithmetic inplaceOperator;

        CallInplaceArithmeticRootNode(PythonLanguage language, InplaceArithmetic inplaceOperator) {
            super(language);
            this.inplaceOperator = inplaceOperator;
        }

        @Override
        public Signature getSignature() {
            if (inplaceOperator.isTernary()) {
                return CallTernaryArithmeticRootNode.SIGNATURE_TERNARY;
            }
            return CallBinaryArithmeticRootNode.SIGNATURE_BINARY;
        }

        @Override
        protected Object doCall(VirtualFrame frame) {
            if (callInplaceNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                callInplaceNode = insert(inplaceOperator.create());
            }
            // most of the in-place operators are binary but there can also be ternary
            if (inplaceOperator.isTernary()) {
                return callInplaceNode.executeTernary(frame, PArguments.getArgument(frame, 0), PArguments.getArgument(frame, 1), PArguments.getArgument(frame, 2));
            }
            return callInplaceNode.execute(frame, PArguments.getArgument(frame, 0), PArguments.getArgument(frame, 1));
        }
    }

    public LookupAndCallInplaceNode create(ExpressionNode left, ExpressionNode right) {
        return LookupAndCallInplaceNode.create(this, left, right, new ObjectLiteralNode(PNone.NO_VALUE));
    }

    public LookupAndCallInplaceNode create() {
        return LookupAndCallInplaceNode.create(this, null, null, null);
    }

    /**
     * Creates a root node for this in-place operator such that the operator can be executed via a
     * full call.
     */
    public RootNode createRootNode(PythonLanguage language) {
        return new CallInplaceArithmeticRootNode(language, this);
    }
}
