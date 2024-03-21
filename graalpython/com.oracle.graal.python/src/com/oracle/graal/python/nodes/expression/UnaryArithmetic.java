/*
 * Copyright (c) 2018, 2024, Oracle and/or its affiliates. All rights reserved.
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
import com.oracle.graal.python.builtins.objects.function.PArguments;
import com.oracle.graal.python.builtins.objects.function.Signature;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.SpecialMethodNames;
import com.oracle.graal.python.nodes.call.special.LookupAndCallUnaryNode;
import com.oracle.graal.python.nodes.call.special.LookupAndCallUnaryNode.NoAttributeHandler;
import com.oracle.graal.python.util.Supplier;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.bytecode.OperationProxy;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateCached;
import com.oracle.truffle.api.dsl.GenerateInline;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.NeverDefault;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.strings.TruffleString;

public enum UnaryArithmetic {
    Pos(UnaryArithmeticFactory.PosNodeGen::create),
    Neg(UnaryArithmeticFactory.NegNodeGen::create),
    Invert(UnaryArithmeticFactory.InvertNodeGen::create);

    interface CreateUnaryOp {
        UnaryOpNode create();
    }

    private final CreateUnaryOp create;

    UnaryArithmetic(CreateUnaryOp create) {
        this.create = create;
    }

    /**
     * A helper root node that dispatches to {@link LookupAndCallUnaryNode} to execute the provided
     * unary operator. This node is mostly useful to use such operators from a location without a
     * frame (e.g. from interop). Note: this is just a root node and won't do any signature
     * checking.
     */
    static final class CallUnaryArithmeticRootNode extends CallArithmeticRootNode {
        private static final Signature SIGNATURE_UNARY = new Signature(1, false, -1, false, tsArray("$self"), null);

        @Child private UnaryOpNode callUnaryNode;

        private final UnaryArithmetic unaryOperator;

        CallUnaryArithmeticRootNode(PythonLanguage language, UnaryArithmetic unaryOperator) {
            super(language);
            this.unaryOperator = unaryOperator;
        }

        @Override
        public Signature getSignature() {
            return SIGNATURE_UNARY;
        }

        @Override
        protected Object doCall(VirtualFrame frame) {
            if (callUnaryNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                callUnaryNode = insert(unaryOperator.create());
            }
            return callUnaryNode.executeCached(frame, PArguments.getArgument(frame, 0));
        }
    }

    public UnaryOpNode create() {
        return create.create();
    }

    /**
     * Creates a root node for this unary operator such that the operator can be executed via a full
     * call.
     */
    public RootNode createRootNode(PythonLanguage language) {
        return new CallUnaryArithmeticRootNode(language, this);
    }

    @ImportStatic(SpecialMethodNames.class)
    public abstract static class UnaryArithmeticNode extends UnaryOpNode {

        static Supplier<NoAttributeHandler> createHandler(String operator) {

            return () -> new NoAttributeHandler() {
                @Child private PRaiseNode raiseNode = PRaiseNode.create();

                @Override
                public Object execute(Object receiver) {
                    throw raiseNode.raise(TypeError, ErrorMessages.BAD_OPERAND_FOR, "unary ", operator, receiver);
                }
            };
        }

        @NeverDefault
        public static LookupAndCallUnaryNode createCallNode(TruffleString name, Supplier<NoAttributeHandler> handler) {
            return LookupAndCallUnaryNode.create(name, handler);
        }
    }

    /*
     *
     * All the following fast paths need to be kept in sync with the corresponding builtin functions
     * in IntBuiltins and FloatBuiltins.
     *
     */

    @GenerateInline(inlineByDefault = true)
    @GenerateCached
    @OperationProxy.Proxyable
    public abstract static class PosNode extends UnaryArithmeticNode {

        public static final Supplier<NoAttributeHandler> NOT_IMPLEMENTED = createHandler("+");

        @Specialization
        public static int pos(int arg) {
            return arg;
        }

        @Specialization
        public static long pos(long arg) {
            return arg;
        }

        @Specialization
        public static double pos(double arg) {
            return arg;
        }

        @Specialization
        public static Object doGeneric(VirtualFrame frame, Object arg,
                        @Cached(value = "createCallNode(T___POS__, NOT_IMPLEMENTED)") LookupAndCallUnaryNode callNode) {
            return callNode.executeObject(frame, arg);
        }

        @NeverDefault
        public static PosNode create() {
            return UnaryArithmeticFactory.PosNodeGen.create();
        }
    }

    @GenerateInline(inlineByDefault = true)
    @GenerateCached
    @OperationProxy.Proxyable
    public abstract static class NegNode extends UnaryArithmeticNode {

        public static final Supplier<NoAttributeHandler> NOT_IMPLEMENTED = createHandler("-");

        @Specialization(rewriteOn = ArithmeticException.class)
        public static int neg(int arg) {
            return Math.negateExact(arg);
        }

        @Specialization
        public static long negOvf(int arg) {
            return -((long) arg);
        }

        @Specialization(rewriteOn = ArithmeticException.class)
        public static long neg(long arg) {
            return Math.negateExact(arg);
        }

        @Specialization
        public static double neg(double arg) {
            return -arg;
        }

        @Specialization
        public static Object doGeneric(VirtualFrame frame, Object arg,
                        @Cached(value = "createCallNode(T___NEG__, NOT_IMPLEMENTED)") LookupAndCallUnaryNode callNode) {
            return callNode.executeObject(frame, arg);
        }

        @NeverDefault
        public static NegNode create() {
            return UnaryArithmeticFactory.NegNodeGen.create();
        }
    }

    @GenerateInline(inlineByDefault = true)
    @GenerateCached
    @OperationProxy.Proxyable
    public abstract static class InvertNode extends UnaryArithmeticNode {

        public static final Supplier<NoAttributeHandler> NOT_IMPLEMENTED = createHandler("~");

        @Specialization
        public static int invert(boolean arg) {
            return ~(arg ? 1 : 0);
        }

        @Specialization
        public static int invert(int arg) {
            return ~arg;
        }

        @Specialization
        public static long invert(long arg) {
            return ~arg;
        }

        @Specialization
        public static Object doGeneric(VirtualFrame frame, Object arg,
                        @Cached(value = "createCallNode(T___INVERT__, NOT_IMPLEMENTED)") LookupAndCallUnaryNode callNode) {
            return callNode.executeObject(frame, arg);
        }

        @NeverDefault
        public static InvertNode create() {
            return UnaryArithmeticFactory.InvertNodeGen.create();
        }
    }
}
