/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.oracle.graal.python.parser.sst;


public class WithSSTNode extends SSTNode {
    
    protected final SSTNode expression;
    protected final SSTNode target;
    protected final SSTNode body;

    public WithSSTNode(SSTNode expression, SSTNode target, SSTNode body, int startOffset, int endOffset) {
        super(startOffset, endOffset);
        this.expression = expression;
        this.target = target;
        this.body = body;
    }
    
    @Override
    public <T>T accept(SSTreeVisitor<T> visitor) {
        return visitor.visit(this);
    }
}
