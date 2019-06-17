/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.oracle.graal.python.parser.sst;

public class RaiseSSTNode extends SSTNode {
    
    protected final SSTNode value; 
    protected final SSTNode from;

    public RaiseSSTNode(SSTNode value, SSTNode from, int startOffset, int endOffset) {
        super(startOffset, endOffset);
        this.value = value;
        this.from = from;
    }
    
    @Override
    public <T>T accept(SSTreeVisitor<T> visitor) {
        return visitor.visit(this);
    }
    
}
