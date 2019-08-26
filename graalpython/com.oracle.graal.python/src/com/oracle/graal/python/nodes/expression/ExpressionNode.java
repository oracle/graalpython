/*
 * Copyright (c) 2017, 2019, Oracle and/or its affiliates. All rights reserved.
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

import com.oracle.graal.python.nodes.PNode;
import com.oracle.graal.python.nodes.control.BlockNode;
import com.oracle.graal.python.nodes.statement.StatementNode;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.instrumentation.GenerateWrapper;
import com.oracle.truffle.api.instrumentation.ProbeNode;
import com.oracle.truffle.api.instrumentation.StandardTags.ExpressionTag;
import com.oracle.truffle.api.instrumentation.StandardTags.StatementTag;
import com.oracle.truffle.api.instrumentation.Tag;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.NodeCost;
import com.oracle.truffle.api.nodes.UnexpectedResultException;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.api.source.SourceSection;

/**
 * Base class for all expressions. Expressions always return a value.
 */
@GenerateWrapper
public abstract class ExpressionNode extends PNode {

    public static final ExpressionNode[] EMPTY_ARRAY = new ExpressionNode[0];

    public abstract Object execute(VirtualFrame frame);

    public int executeInt(VirtualFrame frame) throws UnexpectedResultException {
        Object value = execute(frame);
        if (value instanceof Integer) {
            return (int) value;
        }
        throw new UnexpectedResultException(value);
    }

    public long executeLong(VirtualFrame frame) throws UnexpectedResultException {
        Object value = execute(frame);
        if (value instanceof Long) {
            return (long) value;
        }
        throw new UnexpectedResultException(value);
    }

    public double executeDouble(VirtualFrame frame) throws UnexpectedResultException {
        Object o = execute(frame);
        if (o instanceof Double) {
            return (double) o;
        }
        throw new UnexpectedResultException(o);
    }

    public boolean executeBoolean(VirtualFrame frame) throws UnexpectedResultException {
        Object o = execute(frame);
        if (o instanceof Boolean) {
            return (boolean) o;
        }
        throw new UnexpectedResultException(o);
    }

    public boolean hasSideEffectAsAnExpression() {
        return false;
    }

    public WrapperNode createWrapper(ProbeNode probe) {
        return new ExpressionNodeWrapper(this, probe);
    }

    @Override
    public boolean hasTag(Class<? extends Tag> tag) {
        return tag == ExpressionTag.class || (tag == StatementTag.class && isStatement()) || super.hasTag(tag);
    }

    private boolean isStatement() {
        Node parent = getParent();
        if (parent instanceof WrapperNode) {
            parent = parent.getParent();
        }
        // expressions with these parents are considered to be statements
        return parent instanceof ExpressionWithSideEffect || parent instanceof ExpressionWithSideEffects;
    }

    public static final class ExpressionStatementNode extends StatementNode {
        @Child private ExpressionNode node;

        private ExpressionStatementNode(ExpressionNode node) {
            this.node = node;
            this.assignSourceSection(node.getSourceSection());
        }

        @Override
        public void executeVoid(VirtualFrame frame) {
            node.execute(frame);
        }

        @Override
        public NodeCost getCost() {
            return NodeCost.NONE;
        }

        public ExpressionNode getExpression() {
            return node;
        }
    }

    /**
     * If expressions appear in a block of statements, they are wrapped in a {@link StatementNode}
     * that simply drops the result.
     */
    public final StatementNode asStatement() {
        return new ExpressionStatementNode(this);
    }

    public static final class ExpressionWithSideEffect extends ExpressionNode {
        @Child private StatementNode sideEffect;
        @Child private ExpressionNode node;

        private ExpressionWithSideEffect(ExpressionNode node, StatementNode sideEffect) {
            this.node = node;
            this.sideEffect = sideEffect;
            this.assignSourceSection(node.getSourceSection());
        }

        @Override
        public Object execute(VirtualFrame frame) {
            sideEffect.executeVoid(frame);
            return node.execute(frame);
        }

        @Override
        public boolean hasSideEffectAsAnExpression() {
            return true;
        }

        public ExpressionNode getExpression() {
            return node;
        }

        public StatementNode getSideEffect() {
            return sideEffect;
        }
    }

    public static final class ExpressionWithSideEffects extends ExpressionNode {
        @Children private final StatementNode[] sideEffects;
        @Child private ExpressionNode node;

        private ExpressionWithSideEffects(ExpressionNode node, StatementNode[] sideEffects) {
            this.node = node;
            this.sideEffects = sideEffects;
            SourceSection sourceSection = node.getSourceSection();
            if (sourceSection != null) {
                Source source = sourceSection.getSource();
                this.assignSourceSection(source.createSection(0, source.getLength()));
            }
        }

        @Override
        @ExplodeLoop
        public Object execute(VirtualFrame frame) {
            for (int i = 0; i < sideEffects.length; i++) {
                sideEffects[i].executeVoid(frame);
            }
            return node.execute(frame);
        }

        @Override
        public boolean hasSideEffectAsAnExpression() {
            return true;
        }

        public StatementNode[] getSideEffects() {
            return this.sideEffects;
        }
    }

    /**
     * Some expressions can have hidden side-effects such as writing to a temporary variable. These
     * can be wrapped together with their side effecting {@link StatementNode}.
     */
    public final ExpressionNode withSideEffect(StatementNode sideEffect) {
        if (sideEffect instanceof BlockNode) {
            return new ExpressionWithSideEffects(this, ((BlockNode) sideEffect).getStatements());
        } else {
            return new ExpressionWithSideEffect(this, sideEffect);
        }
    }

    public final ExpressionNode withSideEffect(StatementNode[] sideEffects) {
        return new ExpressionWithSideEffects(this, sideEffects);
    }
}
