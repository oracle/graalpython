/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.oracle.graal.python.parser.sst;

public class ForSSTNode extends SSTNode {
    protected final SSTNode[] targets;
    protected final SSTNode iterator;
    protected final SSTNode body;
    protected SSTNode elseStatement;
    protected final boolean containsContinue;
    protected boolean containsBreak;

    public ForSSTNode(SSTNode[] targets, SSTNode iterator, SSTNode body, boolean containsContinue, int startOffset, int endOffset) {
        super(startOffset, endOffset);
        this.targets = targets;
        this.iterator = iterator;
        this.body = body;
        this.elseStatement = null;
        this.containsContinue = containsContinue;
    }
    
    @Override
    public <T>T accept(SSTreeVisitor<T> visitor) {
        return visitor.visit(this);
    }

    public void setElse(SSTNode elseStatement) {
        this.elseStatement = elseStatement;
    }

    public void setContainsBreak(boolean containsBreak) {
        this.containsBreak = containsBreak;
    }
    
    
}
