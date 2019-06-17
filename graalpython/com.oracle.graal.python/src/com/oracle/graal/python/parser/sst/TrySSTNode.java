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
public class TrySSTNode extends SSTNode {
    protected final SSTNode body; 
    protected final ExceptSSTNode[] exceptNodes;
    protected final SSTNode elseStatement;
    protected final SSTNode finallyStatement;

    public TrySSTNode(SSTNode body, ExceptSSTNode[] exceptNodes, SSTNode elseStatement, SSTNode finallyStatement, int startOffset, int endOffset) {
        super(startOffset, endOffset);
        this.body = body;
        this.exceptNodes = exceptNodes;
        this.elseStatement = elseStatement;
        this.finallyStatement = finallyStatement;
    }
    
    @Override
    public <T>T accept(SSTreeVisitor<T> visitor) {
        return visitor.visit(this);
    }
}
