/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.oracle.graal.python.parser.sst;


public class AnnAssignmentSSTNode extends AssignmentNode {
    protected final SSTNode type;

    public AnnAssignmentSSTNode(SSTNode lhs, SSTNode type, SSTNode rhs, int start, int end) {
        super(new SSTNode[]{lhs}, rhs, start, end);
        this.type = type;
    }
    
    @Override
    public <T>T accept(SSTreeVisitor<T> visitor) {
        return visitor.visit(this);
    }
}
