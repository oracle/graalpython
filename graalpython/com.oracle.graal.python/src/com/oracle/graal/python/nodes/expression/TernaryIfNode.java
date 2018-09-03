package com.oracle.graal.python.nodes.expression;

import com.oracle.truffle.api.frame.VirtualFrame;

public class TernaryIfNode extends ExpressionNode {
    @Child private CastToBooleanNode condition;
    @Child private ExpressionNode then;
    @Child private ExpressionNode orelse;

    public TernaryIfNode(CastToBooleanNode condition, ExpressionNode then, ExpressionNode orelse) {
        this.condition = condition;
        this.then = then;
        this.orelse = orelse;
    }

    public CastToBooleanNode getCondition() {
        return condition;
    }

    @Override
    public Object execute(VirtualFrame frame) {
        if (condition.executeBoolean(frame)) {
            return then.execute(frame);
        } else {
            return orelse.execute(frame);
        }
    }
}
