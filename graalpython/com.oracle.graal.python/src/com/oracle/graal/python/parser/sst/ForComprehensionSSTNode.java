/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.oracle.graal.python.parser.sst;

import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.nodes.expression.ExpressionNode;
import com.oracle.graal.python.parser.ScopeInfo;

/**
 *
 * @author petr
 */
public class ForComprehensionSSTNode extends SSTNode {
    protected final boolean async;
    protected final SSTNode target;
    protected final SSTNode name; 
    protected final SSTNode[] variables;
    protected final SSTNode iterator;
    protected final SSTNode[] conditions;
    protected final PythonBuiltinClassType resultType;
    protected final ScopeInfo scope;
    protected final int line;
    /**
     * Level of inner comp_for expressions. The top expression has level 0.
     */
    protected final int level;

    public ForComprehensionSSTNode(ScopeInfo scope, boolean async, SSTNode target, SSTNode name, SSTNode[] variables, SSTNode iterator, SSTNode[] conditions, PythonBuiltinClassType resultType, int line, int level, int startOffset, int endOffset) {
        super(startOffset, endOffset);
        this.async = async;
        this.target = target;
        this.name = name;
        this.variables = variables;
        this.iterator = iterator;
        this.conditions = conditions;
        this.resultType = resultType;
        this.scope = scope;
        this.line = line;
        this.level = level;
    }
    
    @Override
    public <T>T accept(SSTreeVisitor<T> visitor) {
        return visitor.visit(this);
    }
    
}
