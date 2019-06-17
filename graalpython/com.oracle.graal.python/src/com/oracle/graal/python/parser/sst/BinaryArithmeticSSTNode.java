/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.oracle.graal.python.parser.sst;

import com.oracle.graal.python.nodes.expression.BinaryArithmetic;
import com.oracle.graal.python.nodes.expression.ExpressionNode;

/**
 *
 * @author petr
 */
public class BinaryArithmeticSSTNode extends SSTNode {
    protected final BinaryArithmetic operation;
    protected final SSTNode left;
    protected final SSTNode right;

    public BinaryArithmeticSSTNode(BinaryArithmetic operation, SSTNode left, SSTNode right, int startOffset, int endOffset) {
        super(startOffset, endOffset);
        this.operation = operation;
        this.left = left;
        this.right = right;
    }
    
    @Override
    public <T>T accept(SSTreeVisitor<T> visitor) {
        return visitor.visit(this);
    }
    
}
