package com.oracle.graal.python.nodes.expression;

import com.oracle.truffle.api.frame.VirtualFrame;

public interface BinaryOp {
    Object executeObject(VirtualFrame frame, Object left, Object right);
}
