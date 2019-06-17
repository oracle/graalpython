/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.oracle.graal.python.parser;

import com.oracle.graal.python.nodes.PNode;
import com.oracle.graal.python.nodes.call.PythonCallNode;
import com.oracle.graal.python.nodes.expression.ExpressionNode;
import com.oracle.truffle.api.frame.VirtualFrame;

public class ParserCallNode extends ExpressionNode implements ParserNode {
    private ExpressionNode calleeNode;
    private final ExpressionNode[] argumentNodes;
    private final ExpressionNode[] keywords;
    private final ExpressionNode starArgs;
    private final ExpressionNode kwArgs;
    private final ScopeInfo scope;

    public ParserCallNode(ScopeInfo scope, ExpressionNode calleeNode, ExpressionNode[] argumentNodes, ExpressionNode[] keywords, ExpressionNode starArgs, ExpressionNode kwArgs) {
        this.calleeNode = calleeNode;
        this.argumentNodes = argumentNodes;
        this.keywords = keywords;
        this.starArgs = starArgs;
        this.kwArgs = kwArgs;
        this.scope = scope;
    }

    @Override
    public Object execute(VirtualFrame frame) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public PNode createPythonNode(PythonNodeFactory factory) {
        if (calleeNode instanceof ParserTmpVariableNode) {
            String name = ((ParserTmpVariableNode)calleeNode).getName();
            calleeNode = (ExpressionNode)factory.getScopeEnvironment().findVariable(name, scope);
        }
        ExpressionNode callNode = PythonCallNode.create(calleeNode, argumentNodes, keywords, starArgs, kwArgs);
        callNode.assignSourceSection(this.getSourceSection());
        return callNode;
    }

    public ScopeInfo getScope() {
        return scope;
    }
    
    
}
