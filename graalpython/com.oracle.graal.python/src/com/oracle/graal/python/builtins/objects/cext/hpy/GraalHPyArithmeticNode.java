/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyNodes.HPyAsHandleNode;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyNodes.HPyAsPythonObjectNode;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyNodes.HPyTransformExceptionToNativeNode;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyNodesFactory.HPyAsHandleNodeGen;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyNodesFactory.HPyAsPythonObjectNodeGen;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyNodesFactory.HPyTransformExceptionToNativeNodeGen;
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
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.dsl.NeverDefault;
import com.oracle.truffle.api.interop.ArityException;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.NodeCost;

public abstract class GraalHPyArithmeticNode extends Node {

    // 1 HPy context + 1 operand
    static final int EXPECTED_ARITY_UNARY = 2;

    // 1 HPy context + 2 operands
    static final int EXPECTED_ARITY_BINARY = 3;

    // 1 HPy context + 3 operands
    static final int EXPECTED_ARITY_TERNARY = 4;

    public abstract Object execute(Object[] arguments) throws ArityException;

    abstract static class GraalHPyArithmeticCachedNode extends GraalHPyArithmeticNode {
        @Child HPyAsPythonObjectNode asPythonObjectNode;
        @Child private HPyAsHandleNode asHandleNode;
        @Child private HPyTransformExceptionToNativeNode transformExceptionToNativeNode;

        protected GraalHPyArithmeticCachedNode() {
            asPythonObjectNode = HPyAsPythonObjectNodeGen.create();
            asHandleNode = HPyAsHandleNodeGen.create();
            transformExceptionToNativeNode = HPyTransformExceptionToNativeNodeGen.create();
        }

        @Override
        public final Object execute(Object[] arguments) throws ArityException {
            /*
             * We need to do argument checking at this position because our helper root node won't
             * do it.
             */
            checkArity(arguments);
            try {
                Object result = doOperator(arguments);
                return asHandleNode.execute(result);
            } catch (PException e) {
                transformExceptionToNativeNode.execute(e);
                return GraalHPyHandle.NULL_HANDLE;
            }
        }

        abstract void checkArity(Object[] arguments) throws ArityException;

        abstract Object doOperator(Object[] arguments);

        @Override
        public NodeCost getCost() {
            return NodeCost.NONE;
        }
    }

    abstract static class GraalHPyArithmeticUncachedNode extends GraalHPyArithmeticNode {

        @TruffleBoundary
        @Override
        public final Object execute(Object[] arguments) throws ArityException {
            /*
             * We need to do argument checking at this position because our helper root node won't
             * do it.
             */
            checkArity(arguments);

            Object[] pythonArguments = PArguments.create(arguments.length - 1);
            for (int i = 0; i < PArguments.getUserArgumentLength(pythonArguments); i++) {
                PArguments.setArgument(pythonArguments, i, HPyAsPythonObjectNodeGen.getUncached().execute(arguments[i + 1]));
            }

            try {
                Object result = GenericInvokeNode.invokeUncached(ensureCallTarget(), pythonArguments);
                return HPyAsHandleNodeGen.getUncached().execute(result);
            } catch (PException e) {
                HPyTransformExceptionToNativeNodeGen.executeUncached(e);
                return GraalHPyHandle.NULL_HANDLE;
            }
        }

        abstract void checkArity(Object[] arguments) throws ArityException;

        abstract RootCallTarget ensureCallTarget();

        @Override
        public NodeCost getCost() {
            return NodeCost.MEGAMORPHIC;
        }

        @Override
        public boolean isAdoptable() {
            return false;
        }
    }

    @NeverDefault
    public static GraalHPyArithmeticNode create(UnaryArithmetic operator) {
        return new GraalHPyUnaryArithmeticCached(operator);
    }

    public static GraalHPyArithmeticNode getUncached(UnaryArithmetic operator) {
        return GraalHPyUnaryArithmeticUncached.UNCACHEDS[operator.ordinal()];
    }

    static final class GraalHPyUnaryArithmeticCached extends GraalHPyArithmeticCachedNode {
        @Child private UnaryOpNode opNode;

        GraalHPyUnaryArithmeticCached(UnaryArithmetic operator) {
            opNode = operator.create();
        }

        @Override
        void checkArity(Object[] arguments) throws ArityException {
            GraalHPyContextFunctions.checkArity(arguments, EXPECTED_ARITY_UNARY);
        }

        @Override
        Object doOperator(Object[] arguments) {
            return opNode.execute(null, asPythonObjectNode.execute(arguments[1]));
        }
    }

    private static final class GraalHPyUnaryArithmeticUncached extends GraalHPyArithmeticUncachedNode {
        final UnaryArithmetic operator;

        public GraalHPyUnaryArithmeticUncached(UnaryArithmetic operator) {
            this.operator = operator;
        }

        @Override
        void checkArity(Object[] arguments) throws ArityException {
            GraalHPyContextFunctions.checkArity(arguments, EXPECTED_ARITY_UNARY);
        }

        @Override
        RootCallTarget ensureCallTarget() {
            return PythonLanguage.get(null).createCachedCallTarget(operator::createRootNode, operator);
        }

        private static final GraalHPyUnaryArithmeticUncached[] UNCACHEDS;
        static {
            UnaryArithmetic[] values = UnaryArithmetic.values();
            UNCACHEDS = new GraalHPyUnaryArithmeticUncached[values.length];
            for (int i = 0; i < values.length; i++) {
                UNCACHEDS[i] = new GraalHPyUnaryArithmeticUncached(values[i]);
            }
        }
    }

    @NeverDefault
    public static GraalHPyBinaryArithmeticCached create(BinaryArithmetic operator) {
        return new GraalHPyBinaryArithmeticCached(operator);
    }

    public static GraalHPyBinaryArithmeticUncached getUncached(BinaryArithmetic operator) {
        return GraalHPyBinaryArithmeticUncached.UNCACHEDS[operator.ordinal()];
    }

    static final class GraalHPyBinaryArithmeticCached extends GraalHPyArithmeticCachedNode {
        @Child private BinaryOpNode opNode;

        GraalHPyBinaryArithmeticCached(BinaryArithmetic operator) {
            opNode = operator.create();
        }

        @Override
        void checkArity(Object[] arguments) throws ArityException {
            GraalHPyContextFunctions.checkArity(arguments, EXPECTED_ARITY_BINARY);
        }

        @Override
        Object doOperator(Object[] arguments) {
            return opNode.executeObject(null, asPythonObjectNode.execute(arguments[1]), asPythonObjectNode.execute(arguments[2]));
        }
    }

    private static final class GraalHPyBinaryArithmeticUncached extends GraalHPyArithmeticUncachedNode {
        final BinaryArithmetic operator;

        public GraalHPyBinaryArithmeticUncached(BinaryArithmetic operator) {
            this.operator = operator;
        }

        @Override
        void checkArity(Object[] arguments) throws ArityException {
            GraalHPyContextFunctions.checkArity(arguments, EXPECTED_ARITY_BINARY);
        }

        @Override
        RootCallTarget ensureCallTarget() {
            return PythonLanguage.get(null).createCachedCallTarget(operator::createRootNode, operator);
        }

        private static final GraalHPyBinaryArithmeticUncached[] UNCACHEDS;
        static {
            BinaryArithmetic[] values = BinaryArithmetic.values();
            UNCACHEDS = new GraalHPyBinaryArithmeticUncached[values.length];
            for (int i = 0; i < values.length; i++) {
                UNCACHEDS[i] = new GraalHPyBinaryArithmeticUncached(values[i]);
            }
        }
    }

    @NeverDefault
    public static GraalHPyTernaryArithmeticCached create(TernaryArithmetic operator) {
        return new GraalHPyTernaryArithmeticCached(operator);
    }

    public static GraalHPyTernaryArithmeticUncached getUncached(TernaryArithmetic operator) {
        return GraalHPyTernaryArithmeticUncached.UNCACHEDS[operator.ordinal()];
    }

    static final class GraalHPyTernaryArithmeticCached extends GraalHPyArithmeticCachedNode {
        @Child private LookupAndCallTernaryNode opNode;

        GraalHPyTernaryArithmeticCached(TernaryArithmetic operator) {
            opNode = operator.create();
        }

        @Override
        void checkArity(Object[] arguments) throws ArityException {
            GraalHPyContextFunctions.checkArity(arguments, EXPECTED_ARITY_TERNARY);
        }

        @Override
        Object doOperator(Object[] arguments) {
            return opNode.execute(null, asPythonObjectNode.execute(arguments[1]), asPythonObjectNode.execute(arguments[2]), asPythonObjectNode.execute(arguments[3]));
        }
    }

    private static final class GraalHPyTernaryArithmeticUncached extends GraalHPyArithmeticUncachedNode {
        final TernaryArithmetic operator;

        public GraalHPyTernaryArithmeticUncached(TernaryArithmetic operator) {
            this.operator = operator;
        }

        @Override
        void checkArity(Object[] arguments) throws ArityException {
            GraalHPyContextFunctions.checkArity(arguments, EXPECTED_ARITY_TERNARY);
        }

        @Override
        RootCallTarget ensureCallTarget() {
            return PythonLanguage.get(null).createCachedCallTarget(operator::createRootNode, operator);
        }

        private static final GraalHPyTernaryArithmeticUncached[] UNCACHEDS;
        static {
            TernaryArithmetic[] values = TernaryArithmetic.values();
            UNCACHEDS = new GraalHPyTernaryArithmeticUncached[values.length];
            for (int i = 0; i < values.length; i++) {
                UNCACHEDS[i] = new GraalHPyTernaryArithmeticUncached(values[i]);
            }
        }
    }

    @NeverDefault
    public static GraalHPyInplaceArithmeticCached create(InplaceArithmetic operator) {
        return new GraalHPyInplaceArithmeticCached(operator);
    }

    public static GraalHPyInplaceArithmeticUncached getUncached(InplaceArithmetic operator) {
        return GraalHPyInplaceArithmeticUncached.UNCACHEDS[operator.ordinal()];
    }

    static final class GraalHPyInplaceArithmeticCached extends GraalHPyArithmeticCachedNode {
        @Child private LookupAndCallInplaceNode opNode;
        private final boolean ternary;

        GraalHPyInplaceArithmeticCached(InplaceArithmetic operator) {
            opNode = operator.create();
            ternary = operator.isTernary();
        }

        @Override
        void checkArity(Object[] arguments) throws ArityException {
            GraalHPyInplaceArithmeticCached.checkInplaceArity(arguments, ternary);
        }

        private static void checkInplaceArity(Object[] arguments, boolean ternary) throws ArityException {
            // we also need to account for the HPy context
            if (ternary && arguments.length != 4) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                throw ArityException.create(EXPECTED_ARITY_TERNARY, EXPECTED_ARITY_TERNARY, arguments.length);
            }
            if (!ternary && arguments.length != 3) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                throw ArityException.create(EXPECTED_ARITY_BINARY, EXPECTED_ARITY_BINARY, arguments.length);
            }
        }

        @Override
        Object doOperator(Object[] arguments) {
            return opNode.execute(null, asPythonObjectNode.execute(arguments[1]), asPythonObjectNode.execute(arguments[2]));
        }
    }

    private static final class GraalHPyInplaceArithmeticUncached extends GraalHPyArithmeticUncachedNode {
        final InplaceArithmetic operator;

        public GraalHPyInplaceArithmeticUncached(InplaceArithmetic operator) {
            this.operator = operator;
        }

        @Override
        void checkArity(Object[] arguments) throws ArityException {
            GraalHPyInplaceArithmeticCached.checkInplaceArity(arguments, operator.isTernary());
        }

        @Override
        RootCallTarget ensureCallTarget() {
            return PythonLanguage.get(null).createCachedCallTarget(operator::createRootNode, operator);
        }

        private static final GraalHPyInplaceArithmeticUncached[] UNCACHEDS;
        static {
            InplaceArithmetic[] values = InplaceArithmetic.values();
            UNCACHEDS = new GraalHPyInplaceArithmeticUncached[values.length];
            for (int i = 0; i < values.length; i++) {
                UNCACHEDS[i] = new GraalHPyInplaceArithmeticUncached(values[i]);
            }
        }
    }
}
