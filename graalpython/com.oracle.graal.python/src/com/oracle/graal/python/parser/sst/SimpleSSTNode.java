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
public class SimpleSSTNode extends SSTNode {
    public enum Type {BREAK, CONTINUE,  PASS, NONE, ELLIPSIS, EMPTY };
    
    protected final Type type;

    public SimpleSSTNode(Type type, int startOffset, int endOffset) {
        super(startOffset, endOffset);
        this.type = type;
    }
    
    @Override
    public <T>T accept(SSTreeVisitor<T> visitor) {
        return visitor.visit(this);
    }
    
}
