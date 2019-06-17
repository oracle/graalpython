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
public class StringLiteralNode extends SSTNode{
    protected final String[] values;

    public StringLiteralNode(String[] values, int start, int end) {
        super(start, end);
        this.values = values;
    }

    @Override
    public <T>T accept(SSTreeVisitor<T> visitor) {
        return visitor.visit(this);
    }
    
}
