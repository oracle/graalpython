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
public class ImportFromSSTNode  extends SSTNode {
    protected final String from;
    protected final String[][] asNames;
    protected final ScopeInfo scope;

    public ImportFromSSTNode(ScopeInfo scope, String from, String[][] asNames, int startOffset, int endOffset) {
        super(startOffset, endOffset);
        this.from = from;
        this.asNames = asNames;
        this.scope = scope;
    }
    
    @Override
    public <T> T accept(SSTreeVisitor<T> visitor) {
        return visitor.visit(this);
    }
}
