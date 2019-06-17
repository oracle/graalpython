/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.oracle.graal.python.parser.sst;

/**
 *
 * @author petr
 */
public class ExpressionStatementNode extends SSTNode {
    protected final SSTNode expression;

    public ExpressionStatementNode(SSTNode expression) {
        super(-1, -1);
        this.expression = expression;
    }

    @Override
    public <T>T accept(SSTreeVisitor<T> visitor) {
        return visitor.visit(this);
    }
    
}
