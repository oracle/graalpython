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
public class ComparisonNode extends SSTNode {
    protected final SSTNode firstValue;
    protected final String[] operations;
    protected final SSTNode[] otherValues;

    public ComparisonNode(SSTNode firstValue, String[] operations, SSTNode[] otherValues, int startOffset, int endOffset) {
        super(startOffset, endOffset);
        this.firstValue = firstValue;
        this.operations = operations;
        this.otherValues = otherValues;
    }
    
    @Override
    public <T>T accept(SSTreeVisitor<T> visitor) {
        return visitor.visit(this);
    }
}
