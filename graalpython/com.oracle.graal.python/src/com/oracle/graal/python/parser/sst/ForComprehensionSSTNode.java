/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.oracle.graal.python.parser.sst;

import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.nodes.expression.ExpressionNode;

/**
 *
 * @author petr
 */
public class ForComprehensionSSTNode extends SSTNode {
    protected final boolean async;
    protected final SSTNode target;
    protected final SSTNode name; 
    protected final SSTNode value;
    protected final SSTNode[] conditions;
    protected final PythonBuiltinClassType resultType;

    public ForComprehensionSSTNode(boolean async, SSTNode target, SSTNode name, SSTNode value, SSTNode[] conditions, PythonBuiltinClassType resultType, int startOffset, int endOffset) {
        super(startOffset, endOffset);
        this.async = async;
        this.target = target;
        this.name = name;
        this.value = value;
        this.conditions = conditions;
        this.resultType = resultType;
    }
    
    @Override
    public <T>T accept(SSTreeVisitor<T> visitor) {
        return visitor.visit(this);
    }
    
}
