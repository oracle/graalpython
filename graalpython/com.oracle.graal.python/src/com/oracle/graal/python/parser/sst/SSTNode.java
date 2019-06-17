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
public abstract class SSTNode {
    
    protected int startOffset;
    protected int endOffset;

    public SSTNode(int startOffset, int endOffset) {
        this.startOffset = startOffset;
        this.endOffset = endOffset;
    }

    public int getStartOffset() {
        return startOffset;
    }

    public int getEndOffset() {
        return endOffset;
    }

    public void setStartOffset(int startOffset) {
        this.startOffset = startOffset;
    }

    public void setEndOffset(int endOffset) {
        this.endOffset = endOffset;
    }
    
    public abstract <T>T accept(SSTreeVisitor<T> visitor);
}
