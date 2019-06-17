/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.oracle.graal.python.parser.sst;

import com.oracle.graal.python.nodes.PNode;
import com.oracle.graal.python.parser.ScopeEnvironment;

/**
 *
 * @author petr
 */
public class BlockSSTNode extends SSTNode {
    protected final SSTNode[] statements;

    public BlockSSTNode(SSTNode[] statements, int startOffset, int endOffset) {
        super(startOffset, endOffset);
        this.statements = statements;
    }
    
    public BlockSSTNode(SSTNode[] statements) {
        this(statements, -1, -1);
    }

    @Override
    public <T>T accept(SSTreeVisitor<T> visitor) {
        return visitor.visit(this);
    }
    
    
}
