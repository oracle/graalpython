package com.oracle.graal.python.nodes.expression;

import com.oracle.graal.python.nodes.PNode;
import com.oracle.graal.python.nodes.statement.StatementNode;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.NodeCost;

public abstract class ExpressionNode extends PNode {
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
