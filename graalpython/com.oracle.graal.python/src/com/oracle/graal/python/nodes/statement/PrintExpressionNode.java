package com.oracle.graal.python.nodes.statement;

import static com.oracle.graal.python.nodes.BuiltinNames.DISPLAYHOOK;

import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.module.PythonModule;
import com.oracle.graal.python.nodes.attributes.GetAttributeNode;
import com.oracle.graal.python.nodes.call.CallNode;
import com.oracle.graal.python.nodes.expression.ExpressionNode;
import com.oracle.truffle.api.frame.VirtualFrame;

public class PrintExpressionNode extends ExpressionNode {
    @Child GetAttributeNode getAttribute = GetAttributeNode.create(DISPLAYHOOK);
    @Child CallNode callNode = CallNode.create();
    @Child ExpressionNode valueNode;

    public PrintExpressionNode(ExpressionNode valueNode) {
        this.valueNode = valueNode;
    }

    @Override
    public Object execute(VirtualFrame frame) {
        Object value = valueNode.execute(frame);
        PythonModule sysModule = getContext().getCore().lookupBuiltinModule("sys");
        Object displayhook = getAttribute.executeObject(sysModule);
        callNode.execute(frame, displayhook, value);
        return PNone.NONE;
    }

    public static PrintExpressionNode create(ExpressionNode valueNode) {
        return new PrintExpressionNode(valueNode);
    }
}
