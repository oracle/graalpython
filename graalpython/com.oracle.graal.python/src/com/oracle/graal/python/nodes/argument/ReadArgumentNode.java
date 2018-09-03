package com.oracle.graal.python.nodes.argument;

import com.oracle.graal.python.nodes.PNodeWithContext;
import com.oracle.graal.python.nodes.expression.ExpressionNode;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.NodeCost;

public abstract class ReadArgumentNode extends PNodeWithContext {
    public abstract Object execute(VirtualFrame frame);

    private static final class ArgumentExpressionNode extends ExpressionNode {
        @Child private ReadArgumentNode argNode;

        public ArgumentExpressionNode(ReadArgumentNode argNode) {
            this.argNode = argNode;
        }

        @Override
        public Object execute(VirtualFrame frame) {
            return argNode.execute(frame);
        }

        @Override
        public NodeCost getCost() {
            return NodeCost.NONE;
        }
    }

    public final ExpressionNode asExpression() {
        return new ArgumentExpressionNode(this);
    }
}
