/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.oracle.graal.python.parser.sst;

public class DecoratorSSTNode extends SSTNode {

    protected final String name;
    protected final ArgListBuilder arg;

    public DecoratorSSTNode(String name, ArgListBuilder arg, int startOffset, int endOffset) {
        super(startOffset, endOffset);
        this.name = name;
        this.arg = arg;
    }
    
    @Override
    public <T>T accept(SSTreeVisitor<T> visitor) {
        return visitor.visit(this);
    }
    
}
