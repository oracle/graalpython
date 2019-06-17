/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.oracle.graal.python.parser.sst;


public class AssignmentNode extends SSTNode{
    protected final SSTNode[] lhs;
    protected final SSTNode rhs;

    public AssignmentNode(SSTNode[] lhs, SSTNode rhs, int start, int end) {
        super(start, end);
        this.lhs = lhs;
        this.rhs = rhs;
    }

    @Override
    public <T>T accept(SSTreeVisitor<T> visitor) {
        return visitor.visit(this);
    }
}
