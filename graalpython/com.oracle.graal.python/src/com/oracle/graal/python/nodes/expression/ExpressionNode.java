package com.oracle.graal.python.nodes.expression;

import com.oracle.graal.python.nodes.PNode;
import com.oracle.graal.python.nodes.statement.StatementNode;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.instrumentation.GenerateWrapper;
import com.oracle.truffle.api.instrumentation.ProbeNode;
import com.oracle.truffle.api.instrumentation.StandardTags.ExpressionTag;
import com.oracle.truffle.api.instrumentation.Tag;
import com.oracle.truffle.api.nodes.NodeCost;
import com.oracle.truffle.api.nodes.UnexpectedResultException;

@GenerateWrapper
public abstract class ExpressionNode extends PNode {

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
        return tag == ExpressionTag.class || super.hasTag(tag);
    }

    public static final class ExpressionStatementNode extends StatementNode {
        @Child private ExpressionNode node;

        private ExpressionStatementNode(ExpressionNode node) {
            this.node = node;
        }

        @Override
        public void executeVoid(VirtualFrame frame) {
            node.execute(frame);
        }

        @Override
        public NodeCost getCost() {
            return NodeCost.NONE;
        }
    }

    public final StatementNode asStatement() {
        return new ExpressionStatementNode(this);
    }

    public static final class ExpressionWithSideEffects extends ExpressionNode {
        @Child private StatementNode sideEffect;
        @Child private ExpressionNode node;

        private ExpressionWithSideEffects(ExpressionNode node, StatementNode sideEffect) {
            this.node = node;
            this.sideEffect = sideEffect;
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
    }

    public final ExpressionNode withSideEffects(StatementNode sideEffect) {
        return new ExpressionWithSideEffects(this, sideEffect);
    }
}
