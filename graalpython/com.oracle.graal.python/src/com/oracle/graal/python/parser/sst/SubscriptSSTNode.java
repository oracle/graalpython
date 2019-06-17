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
public class SubscriptSSTNode extends SSTNode {
    
    protected final SSTNode receiver; 
    protected final SSTNode subscript;

    public SubscriptSSTNode(SSTNode receiver, SSTNode subscript, int startOffset, int endOffset) {
        super(startOffset, endOffset);
        this.receiver = receiver;
        this.subscript = subscript;
    }
    
    @Override
    public <T>T accept(SSTreeVisitor<T> visitor) {
        return visitor.visit(this);
    }
}
