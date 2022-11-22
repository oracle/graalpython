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

import static com.oracle.graal.python.nodes.SpecialMethodNames.T___ADD__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___AND__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___FLOORDIV__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___IAND__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___IFLOORDIV__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___ILSHIFT__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___IMATMUL__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___IMOD__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___IOR__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___IPOW__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___IRSHIFT__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___ISUB__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___ITRUEDIV__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___IXOR__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___LSHIFT__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___MATMUL__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___MOD__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___MUL__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___OR__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___POW__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___RSHIFT__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___SUB__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___TRUEDIV__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___XOR__;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.TypeError;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.builtins.objects.function.PArguments;
import com.oracle.graal.python.builtins.objects.function.Signature;
import com.oracle.graal.python.builtins.objects.type.SpecialMethodSlot;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.expression.BinaryArithmetic.CallBinaryArithmeticRootNode;
import com.oracle.graal.python.nodes.expression.TernaryArithmetic.CallTernaryArithmeticRootNode;
import com.oracle.graal.python.util.Supplier;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.strings.TruffleString;

public enum InplaceArithmetic {
    IAdd(SpecialMethodSlot.IAdd, T___ADD__, "+=", BinaryArithmetic.Add),
    ISub(T___ISUB__, T___SUB__, "-=", BinaryArithmetic.Sub),
    IMul(SpecialMethodSlot.IMul, T___MUL__, "*=", BinaryArithmetic.Mul),
    ITrueDiv(T___ITRUEDIV__, T___TRUEDIV__, "/=", BinaryArithmetic.TrueDiv),
    IFloorDiv(T___IFLOORDIV__, T___FLOORDIV__, "//=", BinaryArithmetic.FloorDiv),
    IMod(T___IMOD__, T___MOD__, "%=", BinaryArithmetic.Mod),
    IPow(T___IPOW__, T___POW__, "**=", BinaryArithmetic.Pow, true),
    ILShift(T___ILSHIFT__, T___LSHIFT__, "<<=", BinaryArithmetic.LShift),
    IRShift(T___IRSHIFT__, T___RSHIFT__, ">>=", BinaryArithmetic.RShift),
    IAnd(T___IAND__, T___AND__, "&=", BinaryArithmetic.And),
    IOr(T___IOR__, T___OR__, "|=", BinaryArithmetic.Or),
    IXor(T___IXOR__, T___XOR__, "^=", BinaryArithmetic.Xor),
    IMatMul(T___IMATMUL__, T___MATMUL__, "@", BinaryArithmetic.MatMul);

    final TruffleString methodName;
    final SpecialMethodSlot slot;
    final boolean isTernary;
    final Supplier<LookupAndCallInplaceNode.NotImplementedHandler> notImplementedHandler;
    final BinaryArithmetic binary;
    final TruffleString binaryOpName;

    InplaceArithmetic(TruffleString methodName, TruffleString binaryOpName, String operator, BinaryArithmetic binary) {
        this(methodName, binaryOpName, operator, binary, false);
    }

    InplaceArithmetic(SpecialMethodSlot slot, TruffleString binaryOpName, String operator, BinaryArithmetic binary) {
        this(slot.getName(), binaryOpName, operator, binary, false, slot);
    }

    InplaceArithmetic(TruffleString methodName, TruffleString binaryOpName, String operator, BinaryArithmetic binary, boolean isTernary) {
        this(methodName, binaryOpName, operator, binary, isTernary, null);
    }

    InplaceArithmetic(TruffleString methodName, TruffleString binaryOpName, @SuppressWarnings("unused") String operator, BinaryArithmetic binary, boolean isTernary, SpecialMethodSlot slot) {
        assert methodName.toJavaStringUncached().startsWith("__i") && methodName.toJavaStringUncached().substring(3).equals(binaryOpName.toJavaStringUncached().substring(2));
        this.methodName = methodName;
        this.binary = binary;
        this.isTernary = isTernary;
        this.binaryOpName = binaryOpName;
        this.slot = slot;

        this.notImplementedHandler = () -> new LookupAndCallInplaceNode.NotImplementedHandler() {
            @Child private PRaiseNode raiseNode = PRaiseNode.create();

            @Override
            public Object execute(Object arg, Object arg2) {
                throw raiseNode.raise(TypeError, ErrorMessages.UNSUPPORTED_OPERAND_TYPES_FOR_S_P_AND_P, operator, arg, arg2);
            }
        };
    }

    public TruffleString getMethodName() {
        return methodName;
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

    public LookupAndCallInplaceNode create() {
        return LookupAndCallInplaceNode.create(this);
    }

    /**
     * Creates a root node for this in-place operator such that the operator can be executed via a
     * full call.
     */
    public RootNode createRootNode(PythonLanguage language) {
        return new CallInplaceArithmeticRootNode(language, this);
    }
}
