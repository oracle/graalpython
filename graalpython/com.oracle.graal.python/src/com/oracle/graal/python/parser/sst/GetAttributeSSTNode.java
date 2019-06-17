/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.oracle.graal.python.parser.sst;


public class GetAttributeSSTNode extends SSTNode {
    
    protected final SSTNode receiver;
    protected final String name;

    public GetAttributeSSTNode(SSTNode receiver, String name, int startOffset, int endOffset) {
        super(startOffset, endOffset);
        this.receiver = receiver;
        this.name = name;
    }
    
    @Override
    public <T>T accept(SSTreeVisitor<T> visitor) {
        return visitor.visit(this);
    }
}
