/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.oracle.graal.python.parser.sst;

import com.oracle.graal.python.parser.ScopeInfo;

public abstract class SSTNodeWithScope extends SSTNode {
    protected final ScopeInfo scope;

    public SSTNodeWithScope(int startOffset, int endOffset, ScopeInfo scope) {
        super(startOffset, endOffset);
        this.scope = scope;
    }

    public ScopeInfo getScope() {
        return scope;
    }
}
