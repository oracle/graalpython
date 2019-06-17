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
public class FloatLiteralNode extends SSTNode {
    protected final String value;
    protected final boolean imaginary;

    public FloatLiteralNode(String value, boolean imaginary, int startOffset, int endOffset) {
        super(startOffset, endOffset);
        this.value = value;
        this.imaginary = imaginary;
    }

    @Override
    public <T>T accept(SSTreeVisitor<T> visitor) {
        return visitor.visit(this);
    }
    
}
