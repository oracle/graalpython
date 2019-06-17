/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.oracle.graal.python.parser.sst;


public class AssertSSTNode extends SSTNode {
    protected final SSTNode test;
    protected final SSTNode message;

    public AssertSSTNode(SSTNode test, SSTNode message, int startOffset, int endOffset) {
        super(startOffset, endOffset);
        this.test = test;
        this.message = message;
    }
    
    @Override
    public <T>T accept(SSTreeVisitor<T> visitor) {
        return visitor.visit(this);
    }
}
