/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.oracle.graal.python.parser.sst;


public class StarSSTNode extends SSTNode {
    protected final SSTNode value;

    public StarSSTNode(SSTNode value, int startOffset, int endOffset) {
        super(startOffset, endOffset);
        this.value = value;
    }
    
    @Override
    public <T>T accept(SSTreeVisitor<T> visitor) {
        return visitor.visit(this);
    }
}
