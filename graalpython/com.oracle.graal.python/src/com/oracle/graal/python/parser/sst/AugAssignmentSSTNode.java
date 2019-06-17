/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.oracle.graal.python.parser.sst;


public class AugAssignmentSSTNode extends SSTNode {
    protected final SSTNode lhs;
    protected final SSTNode rhs;
    protected final String operation;

    public AugAssignmentSSTNode(SSTNode lhs, String operation, SSTNode rhs, int startOffset, int endOffset) {
        super(startOffset, endOffset);
        this.lhs = lhs;
        this.rhs = rhs;
        this.operation = operation;
    }
    
    @Override
    public <T>T accept(SSTreeVisitor<T> visitor) {
        return visitor.visit(this);
    }
}
