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
public class BooleanLiteralNode extends SSTNode{
    protected final boolean value;

    public BooleanLiteralNode(boolean value, int startOffset, int endOffset) {
        super(startOffset, endOffset);
        this.value = value;
    }

    @Override
    public <T>T accept(SSTreeVisitor<T> visitor) {
        return visitor.visit(this);
    }
}
