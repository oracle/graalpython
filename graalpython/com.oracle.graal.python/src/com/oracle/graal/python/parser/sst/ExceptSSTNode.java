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
public class ExceptSSTNode extends SSTNode {
    protected final SSTNode test;
    protected final String asName;
    protected final SSTNode body;

    public ExceptSSTNode(SSTNode test, String asName, SSTNode body, int startOffset, int endOffset) {
        super(startOffset, endOffset);
        this.test = test;
        this.asName = asName;
        this.body = body;
    }
     
    @Override
    public <T>T accept(SSTreeVisitor<T> visitor) {
        return visitor.visit(this);
    }
}
