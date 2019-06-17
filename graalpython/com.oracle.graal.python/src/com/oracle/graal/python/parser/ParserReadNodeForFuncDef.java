/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.oracle.graal.python.parser;

import com.oracle.graal.python.nodes.expression.ExpressionNode;
import com.oracle.graal.python.nodes.statement.StatementNode;
import com.oracle.truffle.api.frame.VirtualFrame;

public class ParserReadNodeForFuncDef extends StatementNode {
    
    private final ScopeInfo currentScope;
    private final String name;
    private final ExpressionNode funcDef;

    public ParserReadNodeForFuncDef(ScopeInfo currentScope, String name, ExpressionNode funcDef) {
        this.currentScope = currentScope;
        this.name = name;
        this.funcDef = funcDef;
    }

    @Override
    public void executeVoid(VirtualFrame frame) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    public ScopeInfo getCurrentScope() {
        return currentScope;
    }

    public String getName() {
        return name;
    }

    public ExpressionNode getFuncDef() {
        return funcDef;
    }
            
        
}
