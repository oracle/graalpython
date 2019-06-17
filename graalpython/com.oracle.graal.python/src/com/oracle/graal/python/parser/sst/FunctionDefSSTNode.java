/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.oracle.graal.python.parser.sst;

import com.oracle.graal.python.parser.ScopeInfo;


public class FunctionDefSSTNode extends SSTNode{
    protected final String name;
    protected final String enclosingClassName;
    protected final ArgDefListBuilder argBuilder;
    protected final SSTNode body;
    protected final ScopeInfo functionScope;

    public FunctionDefSSTNode(ScopeInfo functionScope, String name, String enclosingClassName, ArgDefListBuilder argBuilder, SSTNode body, int startOffset, int endOffset) {
        super(startOffset, endOffset);
        this.name = name;
        this.enclosingClassName = enclosingClassName;
        this.argBuilder = argBuilder;
        this.body = body;
        this.functionScope = functionScope;
    }
    
    @Override
    public <T>T accept(SSTreeVisitor<T> visitor) {
        return visitor.visit(this);
    }
    
}
