/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.oracle.graal.python.parser.sst;

import com.oracle.graal.python.parser.ScopeInfo;

/**
 *
 * @author petr
 */
public class LambdaSSTNode extends SSTNode {
    protected final ArgDefListBuilder args;
    protected final SSTNode body;
    protected final ScopeInfo functionScope;

    public LambdaSSTNode(ScopeInfo functionScope, ArgDefListBuilder args, SSTNode body, int startOffset, int endOffset) {
        super(startOffset, endOffset);
        this.args = args;
        this.body = body;
        this.functionScope = functionScope;
    }
    
    @Override
    public <T>T accept(SSTreeVisitor<T> visitor) {
        return visitor.visit(this);
    }
}
