/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.oracle.graal.python.parser.sst;

import com.oracle.graal.python.nodes.expression.UnaryArithmetic;

/**
 *
 * @author petr
 */
public class UnarySSTNode extends SSTNode {
    protected final UnaryArithmetic arithmetic;
    protected final SSTNode value;

    public UnarySSTNode(UnaryArithmetic arithmetic, SSTNode value, int startOffset, int endOffset) {
        super(startOffset, endOffset);
        this.arithmetic = arithmetic;
        this.value = value;
    }
    
    @Override
    public <T>T accept(SSTreeVisitor<T> visitor) {
        return visitor.visit(this);
    }
}
