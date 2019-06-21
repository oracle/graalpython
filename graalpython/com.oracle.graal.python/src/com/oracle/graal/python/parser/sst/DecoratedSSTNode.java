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
public class DecoratedSSTNode extends SSTNode {
    
    protected final DecoratorSSTNode[] decorators;
    protected final SSTNode decorated;

    public DecoratedSSTNode(DecoratorSSTNode[] decorators, SSTNode decorated, int startOffset, int endOffset) {
        super(startOffset, endOffset);
        this.decorators = decorators;
        this.decorated = decorated;
    }
    
    @Override
    public <T>T accept(SSTreeVisitor<T> visitor) {
        return visitor.visit(this);
    }
    
}
