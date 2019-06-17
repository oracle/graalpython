/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.oracle.graal.python.parser;

import com.oracle.graal.python.nodes.PNode;
import com.oracle.graal.python.nodes.expression.ExpressionNode;
import com.oracle.graal.python.nodes.statement.StatementNode;
import com.oracle.truffle.api.frame.VirtualFrame;

public class ParserFunctionDefinitionNode extends ExpressionNode implements ParserNode {

    final private ScopeInfo scope;
    final private String name;
    final private String enclosingClassName;
    final private PythonNodeFactory.ArgDefListBuilder argBuilder;
    final private StatementNode body;
    final private int startIndex;
    final private int stopIndex;

    public ParserFunctionDefinitionNode(ScopeInfo scope, String name, String enclosingClassName, PythonNodeFactory.ArgDefListBuilder argBuilder, StatementNode body, int startIndex, int stopIndex) {
        this.scope = scope;
        this.name = name;
        this.enclosingClassName = enclosingClassName;
        this.argBuilder = argBuilder;
        this.body = body;
        this.startIndex = startIndex;
        this.stopIndex = stopIndex;
    }

    @Override
    public PNode createPythonNode(PythonNodeFactory factory) {
//        return factory.createFunction(name, enclosingClassName, argBuilder, body, startIndex, stopIndex);
        return null;
    }

    @Override
    public Object execute(VirtualFrame frame) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    public StatementNode getBody() {
        return body;
    }

    public ScopeInfo getScope() {
        return scope;
    }

    public String getName() {
        return name;
    }

    public String getEnclosingClassName() {
        return enclosingClassName;
    }

    public PythonNodeFactory.ArgDefListBuilder getArgBuilder() {
        return argBuilder;
    }

    public int getStartIndex() {
        return startIndex;
    }

    public int getStopIndex() {
        return stopIndex;
    }
    
    
}
