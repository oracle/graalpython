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
public class VarLookupNode extends SSTNode {

    protected final String name;
    protected final ScopeInfo scope;
    
    public VarLookupNode(String name, ScopeInfo scope, int start, int end) {
        super(start, end);
        this.name = name;
        this.scope = scope;
    }

    @Override
    public <T>T accept(SSTreeVisitor<T> visitor) {
        return visitor.visit(this);
    }

    public String getName() {
        return name;
    }
    
    
}
