/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.oracle.graal.python.parser.sst;

import com.oracle.graal.python.nodes.expression.ExpressionNode;
import com.oracle.graal.python.parser.PythonNodeFactory;

/**
 *
 * @author petr
 */
public class CallSSTNode extends SSTNode {
    protected final SSTNode target;
    protected final ArgListBuilder parameters;

    public CallSSTNode(SSTNode target, ArgListBuilder parameters, int startOffset, int endOffset) {
        super(startOffset, endOffset);
        this.target = target;
        this.parameters = parameters;
    }
    
    @Override
    public <T>T accept(SSTreeVisitor<T> visitor) {
        return visitor.visit(this);
    }
}
