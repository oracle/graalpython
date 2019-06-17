/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.oracle.graal.python.parser.sst;

public class IfSSTNode extends SSTNode {
    protected final SSTNode test;
    protected final SSTNode thenStatement;
    protected final SSTNode elseStatement;

    public IfSSTNode(SSTNode test, SSTNode thenStatement, SSTNode elseStatement, int startOffset, int endOffset) {
        super(startOffset, endOffset);
        this.test = test;
        this.thenStatement = thenStatement;
        this.elseStatement = elseStatement;
    }

    @Override
    public <T> T accept(SSTreeVisitor<T> visitor) {
        return visitor.visit(this);
    }
    
    
}
