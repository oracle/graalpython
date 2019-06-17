/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.oracle.graal.python.parser.sst;

import com.oracle.graal.python.nodes.expression.BinaryArithmetic;

/**
 *
 * @author petr
 */
public class TernaryArithmeticSSTNode extends SSTNode {
    protected final SSTNode left;
    protected final SSTNode right;

    public TernaryArithmeticSSTNode(SSTNode left, SSTNode right, int startOffset, int endOffset) {
        super(startOffset, endOffset);
        this.left = left;
        this.right = right;
    }
    
    @Override
    public <T>T accept(SSTreeVisitor<T> visitor) {
        return visitor.visit(this);
    }
}
