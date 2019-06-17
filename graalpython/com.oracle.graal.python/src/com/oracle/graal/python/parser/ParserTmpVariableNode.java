/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.oracle.graal.python.parser;

import com.oracle.graal.python.nodes.PNode;
import com.oracle.graal.python.nodes.expression.ExpressionNode;
import com.oracle.graal.python.nodes.frame.ReadNode;
import com.oracle.graal.python.nodes.statement.StatementNode;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;


public class ParserTmpVariableNode extends ExpressionNode implements ReadNode, ParserNode{

    private final String name;
    private final ScopeInfo scope;
    private Node parent;
    private boolean wasCopied = false;

    public ParserTmpVariableNode(String name, ScopeInfo scope) {
        this.name = name;
        this.scope = scope;
    }

    public String getName() {
        return name;
    }

    @Override
    public Object execute(VirtualFrame frame) {
        throw new UnsupportedOperationException("Never ParserTmpVariableNode should be executed.");
    }

    @Override
    protected void onReplace(Node newNode, CharSequence reason) {
        super.onReplace(newNode, reason);
    }

    @Override
    public Node deepCopy() {
        Node result =  super.deepCopy();
//        if (!wasCopied) {
//            scope.tmpVariableNodes.put(name, (ParserTmpVariableNode)result);
//            wasCopied = true;
//        }
        return result;
    }

    @Override
    public StatementNode makeWriteNode(ExpressionNode rhs) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }
 
    public void setParent(Node node) {
        this.parent = parent;
    }

    public ScopeInfo getScope() {
        return scope;
    }

    @Override
    public PNode createPythonNode(PythonNodeFactory scopeEnvironment) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }
        
}
