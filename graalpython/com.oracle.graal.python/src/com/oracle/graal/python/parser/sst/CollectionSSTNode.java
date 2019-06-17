/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.oracle.graal.python.parser.sst;

import com.oracle.graal.python.builtins.PythonBuiltinClassType;


public class CollectionSSTNode extends SSTNode {
    protected final SSTNode[] values;
    protected final PythonBuiltinClassType type;

    public CollectionSSTNode(SSTNode[] values, PythonBuiltinClassType type, int startOffset, int endOffset) {
        super(startOffset, endOffset);
        this.values = values;
        this.type = type;
    }
    
    @Override
    public <T>T accept(SSTreeVisitor<T> visitor) {
        return visitor.visit(this);
    }

    public SSTNode[] getValues() {
        return values;
    }
    
}
