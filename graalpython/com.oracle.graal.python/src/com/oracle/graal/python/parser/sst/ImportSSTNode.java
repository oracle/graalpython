/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.oracle.graal.python.parser.sst;

import com.oracle.graal.python.parser.ScopeInfo;

public class ImportSSTNode extends SSTNode {
    protected final String name;
    protected final String asName;
    protected final ScopeInfo scope;

    public ImportSSTNode(ScopeInfo scope, String name, String asName, int startOffset, int endOffset) {
        super(startOffset, endOffset);
        this.name = name;
        this.asName = asName;
        this.scope = scope;
    }
    
    @Override
    public <T> T accept(SSTreeVisitor<T> visitor) {
        return visitor.visit(this);
    }
}
