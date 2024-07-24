/*
 * Copyright (c) 2023, 2024, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.builtins.objects.cext.hpy;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.function.PArguments;
import com.oracle.graal.python.nodes.call.GenericInvokeNode;
import com.oracle.graal.python.nodes.call.special.LookupAndCallTernaryNode;
import com.oracle.graal.python.nodes.expression.BinaryArithmetic;
import com.oracle.graal.python.nodes.expression.BinaryOpNode;
import com.oracle.graal.python.nodes.expression.InplaceArithmetic;
import com.oracle.graal.python.nodes.expression.LookupAndCallInplaceNode;
import com.oracle.graal.python.nodes.expression.TernaryArithmetic;
import com.oracle.graal.python.nodes.expression.UnaryArithmetic;
import com.oracle.graal.python.nodes.expression.UnaryOpNode;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.dsl.NeverDefault;
import com.oracle.truffle.api.nodes.Node;

public abstract class GraalHPyArithmeticNode {

    private GraalHPyArithmeticNode() {
    }

    public abstract static class HPyUnaryArithmeticNode extends Node {

        public abstract Object execute(Object object);

        @NeverDefault
        public static HPyUnaryArithmeticNode create(UnaryArithmetic operator) {
            return new HPyUnaryArithmeticCached(operator);
        }

        public static HPyUnaryArithmeticNode getUncached(UnaryArithmetic operator) {
            return HPyUnaryArithmeticUncached.UNCACHEDS[operator.ordinal()];
        }
    }

    static final class HPyUnaryArithmeticCached extends HPyUnaryArithmeticNode {
        @Child private UnaryOpNode opNode;

        private HPyUnaryArithmeticCached(UnaryArithmetic operator) {
            opNode = operator.create();
        }

        @Override
        public Object execute(Object object) {
            return opNode.executeCached(null, object);
        }
    }

    private static final class HPyUnaryArithmeticUncached extends HPyUnaryArithmeticNode {
        final UnaryArithmetic operator;

        public HPyUnaryArithmeticUncached(UnaryArithmetic operator) {
            this.operator = operator;
        }

        @TruffleBoundary
        @Override
        public Object execute(Object object) {
            Object[] pythonArguments = PArguments.create(1);
            PArguments.setArgument(pythonArguments, 0, object);
            RootCallTarget callTarget = PythonLanguage.get(null).createCachedCallTarget(operator::createRootNode, operator);
            return GenericInvokeNode.invokeUncached(callTarget, pythonArguments);
        }

        @Override
        public boolean isAdoptable() {
            return false;
        }

        private static final HPyUnaryArithmeticUncached[] UNCACHEDS;
        static {
            UnaryArithmetic[] values = UnaryArithmetic.values();
            UNCACHEDS = new HPyUnaryArithmeticUncached[values.length];
            for (int i = 0; i < values.length; i++) {
                UNCACHEDS[i] = new HPyUnaryArithmeticUncached(values[i]);
            }
        }
    }

    public abstract static class HPyBinaryArithmeticNode extends Node {

        public abstract Object execute(Object arg0, Object arg1);

        @NeverDefault
        public static HPyBinaryArithmeticNode create(BinaryArithmetic operator) {
            return new HPyBinaryArithmeticCached(operator);
        }

        public static HPyBinaryArithmeticNode getUncached(BinaryArithmetic operator) {
            return HPyBinaryArithmeticUncached.UNCACHEDS[operator.ordinal()];
        }
    }

    private static final class HPyBinaryArithmeticCached extends HPyBinaryArithmeticNode {
        @Child private BinaryOpNode opNode;

        private HPyBinaryArithmeticCached(BinaryArithmetic operator) {
            opNode = operator.create();
        }

        @Override
        public Object execute(Object arg0, Object arg1) {
            return opNode.executeObject(null, arg0, arg1);
        }
    }

    private static final class HPyBinaryArithmeticUncached extends HPyBinaryArithmeticNode {
        final BinaryArithmetic operator;

        public HPyBinaryArithmeticUncached(BinaryArithmetic operator) {
            this.operator = operator;
        }

        @TruffleBoundary
        @Override
        public Object execute(Object arg0, Object arg1) {
            Object[] pythonArguments = PArguments.create(2);
            PArguments.setArgument(pythonArguments, 0, arg0);
            PArguments.setArgument(pythonArguments, 1, arg1);
            RootCallTarget callTarget = PythonLanguage.get(null).createCachedCallTarget(operator::createRootNode, operator);
            return GenericInvokeNode.invokeUncached(callTarget, pythonArguments);
        }

        @Override
        public boolean isAdoptable() {
            return false;
        }

        private static final HPyBinaryArithmeticUncached[] UNCACHEDS;
        static {
            BinaryArithmetic[] values = BinaryArithmetic.values();
            UNCACHEDS = new HPyBinaryArithmeticUncached[values.length];
            for (int i = 0; i < values.length; i++) {
                UNCACHEDS[i] = new HPyBinaryArithmeticUncached(values[i]);
            }
        }
    }

    public abstract static class HPyTernaryArithmeticNode extends Node {

        public abstract Object execute(Object arg0, Object arg1, Object arg2);

        @NeverDefault
        public static HPyTernaryArithmeticNode create(TernaryArithmetic operator) {
            return new HPyTernaryArithmeticCached(operator);
        }

        public static HPyTernaryArithmeticNode getUncached(TernaryArithmetic operator) {
            return HPyTernaryArithmeticUncached.UNCACHEDS[operator.ordinal()];
        }
    }

    private static final class HPyTernaryArithmeticCached extends HPyTernaryArithmeticNode {
        @Child private LookupAndCallTernaryNode opNode;

        private HPyTernaryArithmeticCached(TernaryArithmetic operator) {
            opNode = operator.create();
        }

        @Override
        public Object execute(Object arg0, Object arg1, Object arg2) {
            return opNode.execute(null, arg0, arg1, arg2);
        }
    }

    private static final class HPyTernaryArithmeticUncached extends HPyTernaryArithmeticNode {
        final TernaryArithmetic operator;

        public HPyTernaryArithmeticUncached(TernaryArithmetic operator) {
            this.operator = operator;
        }

        @TruffleBoundary
        @Override
        public Object execute(Object arg0, Object arg1, Object arg2) {
            Object[] pythonArguments = PArguments.create(3);
            PArguments.setArgument(pythonArguments, 0, arg0);
            PArguments.setArgument(pythonArguments, 1, arg1);
            PArguments.setArgument(pythonArguments, 2, arg2);
            RootCallTarget callTarget = PythonLanguage.get(null).createCachedCallTarget(operator::createRootNode, operator);
            return GenericInvokeNode.invokeUncached(callTarget, pythonArguments);
        }

        @Override
        public boolean isAdoptable() {
            return false;
        }

        private static final HPyTernaryArithmeticUncached[] UNCACHEDS;
        static {
            TernaryArithmetic[] values = TernaryArithmetic.values();
            UNCACHEDS = new HPyTernaryArithmeticUncached[values.length];
            for (int i = 0; i < values.length; i++) {
                UNCACHEDS[i] = new HPyTernaryArithmeticUncached(values[i]);
            }
        }
    }

    public abstract static class HPyInplaceArithmeticNode extends Node {

        public abstract Object execute(Object arg0, Object arg1, Object arg2);

        public final Object execute(Object arg0, Object arg1) {
            return execute(arg0, arg1, PNone.NO_VALUE);
        }

        @NeverDefault
        public static HPyInplaceArithmeticNode create(InplaceArithmetic operator) {
            return new HPyInplaceArithmeticCached(operator);
        }

        public static HPyInplaceArithmeticNode getUncached(InplaceArithmetic operator) {
            return HPyInplaceArithmeticUncached.UNCACHEDS[operator.ordinal()];
        }
    }

    private static final class HPyInplaceArithmeticCached extends HPyInplaceArithmeticNode {
        @Child private LookupAndCallInplaceNode opNode;

        private final boolean isTernary;

        private HPyInplaceArithmeticCached(InplaceArithmetic operator) {
            opNode = operator.create();
            this.isTernary = operator.isTernary();
        }

        @Override
        public Object execute(Object arg0, Object arg1, Object arg2) {
            if (isTernary) {
                return opNode.executeTernary(null, arg0, arg1, arg2);
            } else {
                return opNode.execute(null, arg0, arg1);
            }
        }
    }

    private static final class HPyInplaceArithmeticUncached extends HPyInplaceArithmeticNode {
        final InplaceArithmetic operator;

        public HPyInplaceArithmeticUncached(InplaceArithmetic operator) {
            this.operator = operator;
        }

        @TruffleBoundary
        @Override
        public Object execute(Object arg0, Object arg1, Object arg2) {
            Object[] pythonArguments = PArguments.create(3);
            PArguments.setArgument(pythonArguments, 0, arg0);
            PArguments.setArgument(pythonArguments, 1, arg1);
            PArguments.setArgument(pythonArguments, 2, arg2);
            RootCallTarget callTarget = PythonLanguage.get(null).createCachedCallTarget(operator::createRootNode, operator);
            return GenericInvokeNode.invokeUncached(callTarget, pythonArguments);
        }

        @Override
        public boolean isAdoptable() {
            return false;
        }

        private static final HPyInplaceArithmeticUncached[] UNCACHEDS;
        static {
            InplaceArithmetic[] values = InplaceArithmetic.values();
            UNCACHEDS = new HPyInplaceArithmeticUncached[values.length];
            for (int i = 0; i < values.length; i++) {
                UNCACHEDS[i] = new HPyInplaceArithmeticUncached(values[i]);
            }
        }
    }

// @NeverDefault
// public static GraalHPyBinaryArithmeticCached create(BinaryArithmetic operator) {
// return new GraalHPyBinaryArithmeticCached(operator);
// }
//
// public static GraalHPyBinaryArithmeticUncached getUncached(BinaryArithmetic operator) {
// return GraalHPyBinaryArithmeticUncached.UNCACHEDS[operator.ordinal()];
// }
//
// static final class GraalHPyBinaryArithmeticCached extends GraalHPyArithmeticCachedNode {
// @Child private BinaryOpNode opNode;
//
// GraalHPyBinaryArithmeticCached(BinaryArithmetic operator) {
// opNode = operator.create();
// }
//
// @Override
// void checkArity(Object[] arguments) throws ArityException {
// checkArity(arguments, EXPECTED_ARITY_BINARY);
// }
//
// @Override
// Object doOperator(Object[] arguments) {
// return opNode.executeObject(null, asPythonObjectNode.execute(arguments[1]),
// asPythonObjectNode.execute(arguments[2]));
// }
// }
//
// private static final class GraalHPyBinaryArithmeticUncached extends
// GraalHPyArithmeticUncachedNode {
// final BinaryArithmetic operator;
//
// public GraalHPyBinaryArithmeticUncached(BinaryArithmetic operator) {
// this.operator = operator;
// }
//
// @Override
// void checkArity(Object[] arguments) throws ArityException {
// checkArity(arguments, EXPECTED_ARITY_BINARY);
// }
//
// @Override
// RootCallTarget ensureCallTarget() {
// return PythonLanguage.get(null).createCachedCallTarget(operator::createRootNode, operator);
// }
//
// private static final GraalHPyBinaryArithmeticUncached[] UNCACHEDS;
// static {
// BinaryArithmetic[] values = BinaryArithmetic.values();
// UNCACHEDS = new GraalHPyBinaryArithmeticUncached[values.length];
// for (int i = 0; i < values.length; i++) {
// UNCACHEDS[i] = new GraalHPyBinaryArithmeticUncached(values[i]);
// }
// }
// }
//
// @NeverDefault
// public static GraalHPyTernaryArithmeticCached create(TernaryArithmetic operator) {
// return new GraalHPyTernaryArithmeticCached(operator);
// }
//
// public static GraalHPyTernaryArithmeticUncached getUncached(TernaryArithmetic operator) {
// return GraalHPyTernaryArithmeticUncached.UNCACHEDS[operator.ordinal()];
// }
//
// static final class GraalHPyTernaryArithmeticCached extends GraalHPyArithmeticCachedNode {
// @Child private LookupAndCallTernaryNode opNode;
//
// GraalHPyTernaryArithmeticCached(TernaryArithmetic operator) {
// opNode = operator.create();
// }
//
// @Override
// void checkArity(Object[] arguments) throws ArityException {
// checkArity(arguments, EXPECTED_ARITY_TERNARY);
// }
//
// @Override
// Object doOperator(Object[] arguments) {
// return opNode.execute(null, asPythonObjectNode.execute(arguments[1]),
// asPythonObjectNode.execute(arguments[2]), asPythonObjectNode.execute(arguments[3]));
// }
// }
//
// private static final class GraalHPyTernaryArithmeticUncached extends
// GraalHPyArithmeticUncachedNode {
// final TernaryArithmetic operator;
//
// public GraalHPyTernaryArithmeticUncached(TernaryArithmetic operator) {
// this.operator = operator;
// }
//
// @Override
// void checkArity(Object[] arguments) throws ArityException {
// checkArity(arguments, EXPECTED_ARITY_TERNARY);
// }
//
// @Override
// RootCallTarget ensureCallTarget() {
// return PythonLanguage.get(null).createCachedCallTarget(operator::createRootNode, operator);
// }
//
// private static final GraalHPyTernaryArithmeticUncached[] UNCACHEDS;
// static {
// TernaryArithmetic[] values = TernaryArithmetic.values();
// UNCACHEDS = new GraalHPyTernaryArithmeticUncached[values.length];
// for (int i = 0; i < values.length; i++) {
// UNCACHEDS[i] = new GraalHPyTernaryArithmeticUncached(values[i]);
// }
// }
// }
//
// @NeverDefault
// public static GraalHPyInplaceArithmeticCached create(InplaceArithmetic operator) {
// return new GraalHPyInplaceArithmeticCached(operator);
// }
//
// public static GraalHPyInplaceArithmeticUncached getUncached(InplaceArithmetic operator) {
// return GraalHPyInplaceArithmeticUncached.UNCACHEDS[operator.ordinal()];
// }
//
// static final class GraalHPyInplaceArithmeticCached extends GraalHPyArithmeticCachedNode {
// @Child private LookupAndCallInplaceNode opNode;
// private final boolean ternary;
//
// GraalHPyInplaceArithmeticCached(InplaceArithmetic operator) {
// opNode = operator.create();
// ternary = operator.isTernary();
// }
//
// @Override
// void checkArity(Object[] arguments) throws ArityException {
// GraalHPyInplaceArithmeticCached.checkInplaceArity(arguments, ternary);
// }
//
// private static void checkInplaceArity(Object[] arguments, boolean ternary) throws ArityException
// {
// // we also need to account for the HPy context
// if (ternary && arguments.length != 4) {
// CompilerDirectives.transferToInterpreterAndInvalidate();
// throw ArityException.create(EXPECTED_ARITY_TERNARY, EXPECTED_ARITY_TERNARY, arguments.length);
// }
// if (!ternary && arguments.length != 3) {
// CompilerDirectives.transferToInterpreterAndInvalidate();
// throw ArityException.create(EXPECTED_ARITY_BINARY, EXPECTED_ARITY_BINARY, arguments.length);
// }
// }
//
// @Override
// Object doOperator(Object[] arguments) {
// return opNode.execute(null, asPythonObjectNode.execute(arguments[1]),
// asPythonObjectNode.execute(arguments[2]));
// }
// }
//
// private static final class GraalHPyInplaceArithmeticUncached extends
// GraalHPyArithmeticUncachedNode {
// final InplaceArithmetic operator;
//
// public GraalHPyInplaceArithmeticUncached(InplaceArithmetic operator) {
// this.operator = operator;
// }
//
// @Override
// void checkArity(Object[] arguments) throws ArityException {
// GraalHPyInplaceArithmeticCached.checkInplaceArity(arguments, operator.isTernary());
// }
//
// @Override
// RootCallTarget ensureCallTarget() {
// return PythonLanguage.get(null).createCachedCallTarget(operator::createRootNode, operator);
// }
//
// private static final GraalHPyInplaceArithmeticUncached[] UNCACHEDS;
// static {
// InplaceArithmetic[] values = InplaceArithmetic.values();
// UNCACHEDS = new GraalHPyInplaceArithmeticUncached[values.length];
// for (int i = 0; i < values.length; i++) {
// UNCACHEDS[i] = new GraalHPyInplaceArithmeticUncached(values[i]);
// }
// }
// }
//
// static void checkArity(Object[] arguments, int expectedArity) throws ArityException {
// if (arguments.length != expectedArity) {
// CompilerDirectives.transferToInterpreterAndInvalidate();
// throw ArityException.create(expectedArity, expectedArity, arguments.length);
// }
// }

}
