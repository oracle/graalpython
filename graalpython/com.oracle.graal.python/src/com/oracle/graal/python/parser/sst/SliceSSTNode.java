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
public class SliceSSTNode extends SSTNode {
    protected final SSTNode start;
    protected final SSTNode stop;
    protected final SSTNode step;

    public SliceSSTNode(SSTNode start, SSTNode stop, SSTNode step, int startOffset, int endOffset) {
        super(startOffset, endOffset);
        this.start = start;
        this.stop = stop;
        this.step = step;
    }
    
    @Override
    public <T>T accept(SSTreeVisitor<T> visitor) {
        return visitor.visit(this);
    }
    
}
