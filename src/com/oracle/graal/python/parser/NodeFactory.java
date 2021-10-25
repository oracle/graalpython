/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.oracle.graal.python.parser;

import com.oracle.graal.python.parser.sst.*;

public interface NodeFactory {
    
    public VarLookupSSTNode createVariable(String name, int startOffset, int endOffset);
    public SSTNode createNumber(String number, int startOffset, int endOffset);
    
}
