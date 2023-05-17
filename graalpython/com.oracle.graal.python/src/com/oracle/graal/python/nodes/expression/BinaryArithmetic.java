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

import static com.oracle.graal.python.nodes.BuiltinNames.T_PRINT;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.TypeError;
import static com.oracle.graal.python.util.PythonUtils.TS_ENCODING;
import static com.oracle.graal.python.util.PythonUtils.tsArray;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.builtins.objects.floats.FloatBuiltins;
import com.oracle.graal.python.builtins.objects.function.PArguments;
import com.oracle.graal.python.builtins.objects.function.Signature;
import com.oracle.graal.python.builtins.objects.method.PBuiltinMethod;
import com.oracle.graal.python.builtins.objects.tuple.PTuple;
import com.oracle.graal.python.builtins.objects.type.SpecialMethodSlot;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.SpecialMethodNames;
import com.oracle.graal.python.nodes.call.special.LookupAndCallBinaryNode;
import com.oracle.graal.python.nodes.call.special.LookupAndCallBinaryNode.NotImplementedHandler;
import com.oracle.graal.python.runtime.exception.PythonErrorType;
import com.oracle.graal.python.runtime.object.PythonObjectFactory;
import com.oracle.graal.python.util.OverflowException;
import com.oracle.graal.python.util.Supplier;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.bytecode.OperationProxy;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.NeverDefault;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.nodes.UnexpectedResultException;
import com.oracle.truffle.api.strings.TruffleString;

@SuppressWarnings("truffle-inlining")
public enum BinaryArithmetic {
    Add(BinaryArithmeticFactory.AddNodeGen::create),
    Sub(BinaryArithmeticFactory.SubNodeGen::create),
    Mul(BinaryArithmeticFactory.MulNodeGen::create),
    TrueDiv(BinaryArithmeticFactory.TrueDivNodeGen::create),
    FloorDiv(BinaryArithmeticFactory.FloorDivNodeGen::create),
    Mod(BinaryArithmeticFactory.ModNodeGen::create),
    LShift(BinaryArithmeticFactory.LShiftNodeGen::create),
    RShift(BinaryArithmeticFactory.RShiftNodeGen::create),
    And(BinaryArithmeticFactory.BitAndNodeGen::create),
    Or(BinaryArithmeticFactory.BitOrNodeGen::create),
    Xor(BinaryArithmeticFactory.BitXorNodeGen::create),
    MatMul(BinaryArithmeticFactory.MatMulNodeGen::create),
    Pow(BinaryArithmeticFactory.PowNodeGen::create),
    DivMod(BinaryArithmeticFactory.DivModNodeGen::create);

    interface CreateBinaryOp {
        BinaryOpNode create();
    }

    private final CreateBinaryOp create;

    BinaryArithmetic(CreateBinaryOp create) {
        this.create = create;
    }

    /**
     * A helper root node that dispatches to {@link LookupAndCallBinaryNode} to execute the provided
     * ternary operator. Note: this is just a root node and won't do any signature checking.
     */
    static final class CallBinaryArithmeticRootNode extends CallArithmeticRootNode {
        static final Signature SIGNATURE_BINARY = new Signature(2, false, -1, false, tsArray("$self", "other"), null);

        @Child private BinaryOpNode callBinaryNode;

        private final BinaryArithmetic binaryOperator;

        CallBinaryArithmeticRootNode(PythonLanguage language, BinaryArithmetic binaryOperator) {
            super(language);
            this.binaryOperator = binaryOperator;
        }

        @Override
        public Signature getSignature() {
            return SIGNATURE_BINARY;
        }

        @Override
        protected Object doCall(VirtualFrame frame) {
            if (callBinaryNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                callBinaryNode = insert(binaryOperator.create());
            }
            return callBinaryNode.executeObject(frame, PArguments.getArgument(frame, 0), PArguments.getArgument(frame, 1));
        }
    }

    @NeverDefault
    public BinaryOpNode create() {
        return create.create();
    }

    /**
     * Creates a root node for this binary operator such that the operator can be executed via a
     * full call.
     */
    public RootNode createRootNode(PythonLanguage language) {
        return new CallBinaryArithmeticRootNode(language, this);
    }

    @ImportStatic(SpecialMethodNames.class)
    public abstract static class BinaryArithmeticNode extends BinaryOpNode {

        static Supplier<NotImplementedHandler> createHandler(String operator) {
            return () -> new NotImplementedHandler() {
                @Child private PRaiseNode raiseNode = PRaiseNode.create();

                @Override
                public Object execute(VirtualFrame frame, Object arg, Object arg2) {
                    throw raiseNode.raise(TypeError, getErrorMessage(arg), operator, arg, arg2);
                }

                @CompilerDirectives.TruffleBoundary
                private TruffleString getErrorMessage(Object arg) {
                    if (operator.equals(">>") && arg instanceof PBuiltinMethod && ((PBuiltinMethod) arg).getBuiltinFunction().getName().equalsUncached(T_PRINT, TS_ENCODING)) {
                        return ErrorMessages.UNSUPPORTED_OPERAND_TYPES_FOR_S_P_AND_P_PRINT;
                    }
                    return ErrorMessages.UNSUPPORTED_OPERAND_TYPES_FOR_S_P_AND_P;
                }
            };
        }

        @NeverDefault
        public static LookupAndCallBinaryNode createCallNode(SpecialMethodSlot slot, Supplier<NotImplementedHandler> handler) {
            assert slot.getReverse() != null;
            return LookupAndCallBinaryNode.createReversible(slot, slot.getReverse(), handler);
        }

        @NeverDefault
        public static LookupAndCallBinaryNode createBinaryOp(SpecialMethodSlot slot, Supplier<NotImplementedHandler> handler) {
            return LookupAndCallBinaryNode.createBinaryOp(slot, slot.getReverse(), handler);
        }

        @NeverDefault
        static LookupAndCallBinaryNode createPyNumberAdd(Supplier<NotImplementedHandler> handler) {
            return LookupAndCallBinaryNode.createPyNumberAdd(handler);
        }

        @NeverDefault
        static LookupAndCallBinaryNode createPyNumberMultiply(Supplier<NotImplementedHandler> handler) {
            return LookupAndCallBinaryNode.createPyNumberMultiply(handler);
        }

    }
    /*
     *
     * All the following fast paths need to be kept in sync with the corresponding builtin functions
     * in IntBuiltins and FloatBuiltins.
     *
     */

    @OperationProxy.Proxyable
    public abstract static class AddNode extends BinaryArithmeticNode {

        public static final Supplier<NotImplementedHandler> NOT_IMPLEMENTED = createHandler("+");

        public abstract int executeInt(VirtualFrame frame, int left, int right) throws UnexpectedResultException;

        public abstract double executeDouble(VirtualFrame frame, double left, double right) throws UnexpectedResultException;

        @Specialization(rewriteOn = ArithmeticException.class)
        public static int add(int left, int right) {
            return Math.addExact(left, right);
        }

        @Specialization
        public static long doIIOvf(int x, int y) {
            return x + (long) y;
        }

        @Specialization(rewriteOn = ArithmeticException.class)
        public static long addLong(long left, long right) {
            return Math.addExact(left, right);
        }

        @Specialization
        public static double doDD(double left, double right) {
            return left + right;
        }

        @Specialization
        public static double doDL(double left, long right) {
            return left + right;
        }

        @Specialization
        public static double doLD(long left, double right) {
            return left + right;
        }

        @Specialization
        public static Object doGeneric(VirtualFrame frame, Object left, Object right,
                        @Cached("createPyNumberAdd(NOT_IMPLEMENTED)") LookupAndCallBinaryNode callNode) {
            return callNode.executeObject(frame, left, right);
        }

        @NeverDefault
        public static AddNode create() {
            return BinaryArithmeticFactory.AddNodeGen.create();
        }
    }

    @OperationProxy.Proxyable
    public abstract static class SubNode extends BinaryArithmeticNode {

        public static final Supplier<NotImplementedHandler> NOT_IMPLEMENTED = createHandler("-");

        @Specialization(rewriteOn = ArithmeticException.class)
        public static int doII(int x, int y) throws ArithmeticException {
            return Math.subtractExact(x, y);
        }

        @Specialization
        public static long doIIOvf(int x, int y) {
            return x - (long) y;
        }

        @Specialization
        public static double doDD(double left, double right) {
            return left - right;
        }

        @Specialization
        public static double doDL(double left, long right) {
            return left - right;
        }

        @Specialization
        public static double doLD(long left, double right) {
            return left - right;
        }

        @Specialization(rewriteOn = ArithmeticException.class)
        public static long doLL(long x, long y) throws ArithmeticException {
            return Math.subtractExact(x, y);
        }

        @Specialization
        public static Object doGeneric(VirtualFrame frame, Object left, Object right,
                        // TODO: replace with 'createBinaryOp' once (GR-<1????>) is fixed
                        @Cached("createCallNode(Sub, NOT_IMPLEMENTED)") LookupAndCallBinaryNode callNode) {
            return callNode.executeObject(frame, left, right);
        }
    }

    @OperationProxy.Proxyable
    public abstract static class MulNode extends BinaryArithmeticNode {

        public static final Supplier<NotImplementedHandler> NOT_IMPLEMENTED = createHandler("*");

        @Specialization(rewriteOn = ArithmeticException.class)
        public static int doII(int x, int y) throws ArithmeticException {
            return Math.multiplyExact(x, y);
        }

        @Specialization(replaces = "doII")
        public static long doIIL(int x, int y) {
            return x * (long) y;
        }

        @Specialization(rewriteOn = ArithmeticException.class)
        public static long doLL(long x, long y) {
            return Math.multiplyExact(x, y);
        }

        @Specialization
        public static double doDL(double left, long right) {
            return left * right;
        }

        @Specialization
        public static double doLD(long left, double right) {
            return left * right;
        }

        @Specialization
        public static double doDD(double left, double right) {
            return left * right;
        }

        @Specialization
        public static Object doGeneric(VirtualFrame frame, Object left, Object right,
                        @Cached("createPyNumberMultiply(NOT_IMPLEMENTED)") LookupAndCallBinaryNode callNode) {
            return callNode.executeObject(frame, left, right);
        }
    }

    public abstract static class BinaryArithmeticRaiseNode extends BinaryArithmeticNode {

        protected static void raiseIntDivisionByZero(boolean cond, Node inliningTarget, PRaiseNode.Lazy raiseNode) {
            if (cond) {
                throw raiseNode.get(inliningTarget).raise(PythonErrorType.ZeroDivisionError, ErrorMessages.S_DIVISION_OR_MODULO_BY_ZERO, "integer");
            }
        }

        protected static void raiseDivisionByZero(boolean cond, Node inliningTarget, PRaiseNode.Lazy raiseNode) {
            if (cond) {
                throw raiseNode.get(inliningTarget).raise(PythonErrorType.ZeroDivisionError, ErrorMessages.DIVISION_BY_ZERO);
            }
        }
    }

    @OperationProxy.Proxyable
    public abstract static class TrueDivNode extends BinaryArithmeticRaiseNode {

        public static final Supplier<NotImplementedHandler> NOT_IMPLEMENTED = createHandler("/");

        @Specialization
        public static double doII(int x, int y,
                        @Bind("this") Node inliningTarget,
                        @Shared("raiseNode") @Cached PRaiseNode.Lazy raiseNode) {
            return doDD(x, y, inliningTarget, raiseNode);
        }

        @Specialization
        public static double doLD(long x, double y,
                        @Bind("this") Node inliningTarget,
                        @Shared("raiseNode") @Cached PRaiseNode.Lazy raiseNode) {
            return doDD(x, y, inliningTarget, raiseNode);
        }

        @Specialization
        public static double doDL(double x, long y,
                        @Bind("this") Node inliningTarget,
                        @Shared("raiseNode") @Cached PRaiseNode.Lazy raiseNode) {
            return doDD(x, y, inliningTarget, raiseNode);
        }

        @Specialization
        public static double doDD(double x, double y,
                        @Bind("this") Node inliningTarget,
                        @Shared("raiseNode") @Cached PRaiseNode.Lazy raiseNode) {
            raiseDivisionByZero(y == 0.0, inliningTarget, raiseNode);
            return x / y;
        }

        @Specialization
        public static Object doGeneric(VirtualFrame frame, Object left, Object right,
                        // TODO: replace with 'createBinaryOp' once (GR-<1????>) is fixed
                        @Cached("createCallNode(TrueDiv, NOT_IMPLEMENTED)") LookupAndCallBinaryNode callNode) {
            return callNode.executeObject(frame, left, right);
        }
    }

    @OperationProxy.Proxyable
    public abstract static class FloorDivNode extends BinaryArithmeticRaiseNode {

        public static final Supplier<NotImplementedHandler> NOT_IMPLEMENTED = createHandler("//");

        @Specialization
        public static int doII(int left, int right,
                        @Bind("this") Node inliningTarget,
                        @Shared("raiseNode") @Cached PRaiseNode.Lazy raiseNode) {
            raiseIntDivisionByZero(right == 0, inliningTarget, raiseNode);
            return Math.floorDiv(left, right);
        }

        @Specialization(rewriteOn = OverflowException.class)
        public static long doLL(long left, long right,
                        @Bind("this") Node inliningTarget,
                        @Shared("raiseNode") @Cached PRaiseNode.Lazy raiseNode) throws OverflowException {
            if (left == Long.MIN_VALUE && right == -1) {
                throw OverflowException.INSTANCE;
            }
            raiseIntDivisionByZero(right == 0, inliningTarget, raiseNode);
            return Math.floorDiv(left, right);
        }

        @Specialization
        public static double doDL(double left, long right,
                        @Bind("this") Node inliningTarget,
                        @Shared("raiseNode") @Cached PRaiseNode.Lazy raiseNode) {
            raiseDivisionByZero(right == 0, inliningTarget, raiseNode);
            return Math.floor(left / right);
        }

        @Specialization
        public static double doDD(double left, double right,
                        @Bind("this") Node inliningTarget,
                        @Shared("raiseNode") @Cached PRaiseNode.Lazy raiseNode) {
            raiseDivisionByZero(right == 0.0, inliningTarget, raiseNode);
            return Math.floor(left / right);
        }

        @Specialization
        public static double doLD(long left, double right,
                        @Bind("this") Node inliningTarget,
                        @Shared("raiseNode") @Cached PRaiseNode.Lazy raiseNode) {
            raiseDivisionByZero(right == 0.0, inliningTarget, raiseNode);
            return Math.floor(left / right);
        }

        @Specialization
        public static Object doGeneric(VirtualFrame frame, Object left, Object right,
                        // TODO: replace with 'createBinaryOp' once (GR-<1????>) is fixed
                        @Cached("createCallNode(FloorDiv, NOT_IMPLEMENTED)") LookupAndCallBinaryNode callNode) {
            return callNode.executeObject(frame, left, right);
        }
    }

    @OperationProxy.Proxyable
    public abstract static class ModNode extends BinaryArithmeticRaiseNode {

        public static final Supplier<NotImplementedHandler> NOT_IMPLEMENTED = createHandler("%");

        @Specialization
        public static int doII(int left, int right,
                        @Bind("this") Node inliningTarget,
                        @Shared("raiseNode") @Cached PRaiseNode.Lazy raiseNode) {
            raiseIntDivisionByZero(right == 0, inliningTarget, raiseNode);
            return Math.floorMod(left, right);
        }

        @Specialization
        public static long doLL(long left, long right,
                        @Bind("this") Node inliningTarget,
                        @Shared("raiseNode") @Cached PRaiseNode.Lazy raiseNode) {
            raiseIntDivisionByZero(right == 0, inliningTarget, raiseNode);
            return Math.floorMod(left, right);
        }

        @Specialization
        public static double doDL(double left, long right,
                        @Bind("this") Node inliningTarget,
                        @Shared("raiseNode") @Cached PRaiseNode.Lazy raiseNode) {
            raiseDivisionByZero(right == 0, inliningTarget, raiseNode);
            return FloatBuiltins.ModNode.mod(left, right);
        }

        @Specialization
        public static double doDD(double left, double right,
                        @Bind("this") Node inliningTarget,
                        @Shared("raiseNode") @Cached PRaiseNode.Lazy raiseNode) {
            raiseDivisionByZero(right == 0.0, inliningTarget, raiseNode);
            return FloatBuiltins.ModNode.mod(left, right);
        }

        @Specialization
        public static double doLD(long left, double right,
                        @Bind("this") Node inliningTarget,
                        @Shared("raiseNode") @Cached PRaiseNode.Lazy raiseNode) {
            raiseDivisionByZero(right == 0.0, inliningTarget, raiseNode);
            return FloatBuiltins.ModNode.mod(left, right);
        }

        @Specialization
        public static Object doGeneric(VirtualFrame frame, Object left, Object right,
                        @Cached("createCallNode(Mod, NOT_IMPLEMENTED)") LookupAndCallBinaryNode callNode) {
            return callNode.executeObject(frame, left, right);
        }
    }

    @OperationProxy.Proxyable
    public abstract static class LShiftNode extends BinaryArithmeticNode {

        public static final Supplier<NotImplementedHandler> NOT_IMPLEMENTED = createHandler("<<");

        @Specialization(guards = {"right < 32", "right >= 0"}, rewriteOn = OverflowException.class)
        public static int doII(int left, int right) throws OverflowException {
            int result = left << right;
            if (left != result >> right) {
                throw OverflowException.INSTANCE;
            }
            return result;
        }

        @Specialization(guards = {"right < 64", "right >= 0"}, rewriteOn = OverflowException.class)
        public static long doLL(long left, long right) throws OverflowException {
            long result = left << right;
            if (left != result >> right) {
                throw OverflowException.INSTANCE;
            }
            return result;
        }

        @Specialization
        public static Object doGeneric(VirtualFrame frame, Object left, Object right,
                        // TODO: replace with 'createBinaryOp' once (GR-<1????>) is fixed
                        @Cached("createCallNode(LShift, NOT_IMPLEMENTED)") LookupAndCallBinaryNode callNode) {
            return callNode.executeObject(frame, left, right);
        }
    }

    @OperationProxy.Proxyable
    public abstract static class RShiftNode extends BinaryArithmeticNode {

        public static final Supplier<NotImplementedHandler> NOT_IMPLEMENTED = createHandler(">>");

        @Specialization(guards = {"right < 32", "right >= 0"})
        public static int doIISmall(int left, int right) {
            return left >> right;
        }

        @Specialization(guards = {"right < 64", "right >= 0"})
        public static long doIISmall(long left, long right) {
            return left >> right;
        }

        @Specialization
        public static Object doGeneric(VirtualFrame frame, Object left, Object right,
                        // TODO: replace with 'createBinaryOp' once (GR-<1????>) is fixed
                        @Cached("createCallNode(RShift, NOT_IMPLEMENTED)") LookupAndCallBinaryNode callNode) {
            return callNode.executeObject(frame, left, right);
        }
    }

    @OperationProxy.Proxyable
    public abstract static class BitAndNode extends BinaryArithmeticNode {

        public static final Supplier<NotImplementedHandler> NOT_IMPLEMENTED = createHandler("&");

        @Specialization
        public static int op(int left, int right) {
            return left & right;
        }

        @Specialization
        public static long op(long left, long right) {
            return left & right;
        }

        @Specialization
        public static Object doGeneric(VirtualFrame frame, Object left, Object right,
                        @Cached("createBinaryOp(And, NOT_IMPLEMENTED)") LookupAndCallBinaryNode callNode) {
            return callNode.executeObject(frame, left, right);
        }

    }

    @OperationProxy.Proxyable
    public abstract static class BitOrNode extends BinaryArithmeticNode {

        public static final Supplier<NotImplementedHandler> NOT_IMPLEMENTED = createHandler("|");

        @Specialization
        public static int op(int left, int right) {
            return left | right;
        }

        @Specialization
        public static long op(long left, long right) {
            return left | right;
        }

        @Specialization
        public static Object doGeneric(VirtualFrame frame, Object left, Object right,
                        // (mq) TODO: use 'createBinaryOp' once 'type(int | str)' is avoided
                        // during forzen module build.
                        @Cached("createCallNode(Or, NOT_IMPLEMENTED)") LookupAndCallBinaryNode callNode) {
            return callNode.executeObject(frame, left, right);
        }

    }

    @OperationProxy.Proxyable
    public abstract static class BitXorNode extends BinaryArithmeticNode {

        public static final Supplier<NotImplementedHandler> NOT_IMPLEMENTED = createHandler("^");

        @Specialization
        public static int op(int left, int right) {
            return left ^ right;
        }

        @Specialization
        public static long op(long left, long right) {
            return left ^ right;
        }

        @Specialization
        public static Object doGeneric(VirtualFrame frame, Object left, Object right,
                        @Cached("createBinaryOp(Xor, NOT_IMPLEMENTED)") LookupAndCallBinaryNode callNode) {
            return callNode.executeObject(frame, left, right);
        }

    }

    @OperationProxy.Proxyable
    public abstract static class MatMulNode extends BinaryArithmeticNode {

        public static final Supplier<NotImplementedHandler> NOT_IMPLEMENTED = createHandler("@");

        @Specialization
        public static Object doGeneric(VirtualFrame frame, Object left, Object right,
                        // TODO: replace with 'createBinaryOp' once (GR-<1????>) is fixed
                        @Cached("createCallNode(MatMul, NOT_IMPLEMENTED)") LookupAndCallBinaryNode callNode) {
            return callNode.executeObject(frame, left, right);
        }
    }

    @OperationProxy.Proxyable
    public abstract static class PowNode extends BinaryArithmeticNode {

        public static final Supplier<NotImplementedHandler> NOT_IMPLEMENTED = createHandler("** or pow()");

        @Specialization
        public static Object doGeneric(VirtualFrame frame, Object left, Object right,
                        // TODO: ternary_op is not implemented (GR-<2????>)
                        @Cached("createCallNode(Pow, NOT_IMPLEMENTED)") LookupAndCallBinaryNode callNode) {
            return callNode.executeObject(frame, left, right);
        }
    }

    @OperationProxy.Proxyable
    public abstract static class DivModNode extends BinaryArithmeticRaiseNode {

        public static final Supplier<NotImplementedHandler> NOT_IMPLEMENTED = createHandler("divmod");

        @Specialization
        public static PTuple doLL(int left, int right,
                        @Bind("this") Node inliningTarget,
                        @Shared("raiseNode") @Cached PRaiseNode.Lazy raiseNode,
                        @Shared("factory") @Cached PythonObjectFactory factory) {
            raiseIntDivisionByZero(right == 0, inliningTarget, raiseNode);
            return factory.createTuple(new Object[]{Math.floorDiv(left, right), Math.floorMod(left, right)});
        }

        @Specialization
        public static PTuple doLL(long left, long right,
                        @Bind("this") Node inliningTarget,
                        @Shared("raiseNode") @Cached PRaiseNode.Lazy raiseNode,
                        @Shared("factory") @Cached PythonObjectFactory factory) {
            raiseIntDivisionByZero(right == 0, inliningTarget, raiseNode);
            return factory.createTuple(new Object[]{Math.floorDiv(left, right), Math.floorMod(left, right)});
        }

        @Specialization
        public static PTuple doDL(double left, long right,
                        @Bind("this") Node inliningTarget,
                        @Shared("raiseNode") @Cached PRaiseNode.Lazy raiseNode,
                        @Shared("factory") @Cached PythonObjectFactory factory) {
            raiseDivisionByZero(right == 0, inliningTarget, raiseNode);
            return factory.createTuple(new Object[]{Math.floor(left / right), FloatBuiltins.ModNode.mod(left, right)});
        }

        @Specialization
        public static PTuple doDD(double left, double right,
                        @Bind("this") Node inliningTarget,
                        @Shared("raiseNode") @Cached PRaiseNode.Lazy raiseNode,
                        @Shared("factory") @Cached PythonObjectFactory factory) {
            raiseDivisionByZero(right == 0.0, inliningTarget, raiseNode);
            return factory.createTuple(new Object[]{Math.floor(left / right), FloatBuiltins.ModNode.mod(left, right)});
        }

        @Specialization
        public static PTuple doLD(long left, double right,
                        @Bind("this") Node inliningTarget,
                        @Shared("raiseNode") @Cached PRaiseNode.Lazy raiseNode,
                        @Shared("factory") @Cached PythonObjectFactory factory) {
            raiseDivisionByZero(right == 0.0, inliningTarget, raiseNode);
            return factory.createTuple(new Object[]{Math.floor(left / right), FloatBuiltins.ModNode.mod(left, right)});
        }

        @Specialization
        public static Object doGeneric(VirtualFrame frame, Object left, Object right,
                        // TODO: replace with 'createBinaryOp' once (GR-<1????>) is fixed
                        @Cached("createCallNode(DivMod, NOT_IMPLEMENTED)") LookupAndCallBinaryNode callNode) {
            return callNode.executeObject(frame, left, right);
        }
    }
}
