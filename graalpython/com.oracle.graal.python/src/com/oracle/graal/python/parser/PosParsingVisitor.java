/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.oracle.graal.python.parser;

import com.oracle.graal.python.nodes.PNode;
import com.oracle.graal.python.nodes.expression.ExpressionNode;
import com.oracle.graal.python.nodes.frame.ReadNode;
import com.oracle.graal.python.nodes.function.FunctionDefinitionNode;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.NodeUtil;
import com.oracle.truffle.api.nodes.NodeVisitor;

public class PosParsingVisitor implements NodeVisitor {
    
    final private PythonNodeFactory factory;
    private Node previousVisited;
    
    public PosParsingVisitor(PythonNodeFactory factory) {
        this.factory = factory;
    }

    @Override
    public boolean visit(Node node) {
//        System.out.println(node.getClass().getName());
        node.adoptChildren();
//        if (node instanceof FunctionDefinitionNode) {
//            FunctionDefinitionNode fdn = (FunctionDefinitionNode) node;
//            fdn.getFunctionRoot().accept(this);
//        }

//        if (node instanceof ParserCallNode) {
//            Node parent = node.getParent();
//            ParserCallNode pcn =  (ParserCallNode)node;
//            pcn.
//        }
        if (node instanceof ParserReadNodeForFuncDef) {
//            ParserReadNodeForFuncDef parserNode = (ParserReadNodeForFuncDef)node;
//            ParserFunctionDefinitionNode pfdn = (ParserFunctionDefinitionNode)parserNode.getFuncDef();
//            Node parent = node.getParent();
//            //if (parent == null)
//            pfdn.getBody().accept(this);
//            ScopeInfo oldScope = factory.getScopeEnvironment().getCurrentScope();
//            factory.getScopeEnvironment().setCurrentScope(pfdn.getScope());
//            ExpressionNode funcDef = factory.createFunction(pfdn.getName(), pfdn.getEnclosingClassName(), pfdn.getArgBuilder(), pfdn.getBody(), pfdn.getStartIndex(), pfdn.getStopIndex());
//            factory.getScopeEnvironment().setCurrentScope(parserNode.getCurrentScope());
//            Node replacement = factory.createReadNodeForFuncDef(funcDef, pfdn.getName());
//            NodeUtil.replaceChild(parent, node, replacement);
        }
        if (node instanceof ParserFunctionDefinitionNode) {
            ParserFunctionDefinitionNode pfdn = (ParserFunctionDefinitionNode)node;
            pfdn.getBody().accept(this);
            Node parent = node.getParent();
            PNode replacement = pfdn.createPythonNode(factory);
            NodeUtil.replaceChild(parent, node, replacement);
//        } else if (node instanceof ParserCallNode) {
//            System.out.println(" PosProcess");
//            Node parent = node.getParent();
//            System.out.println("parent: " + parent);
//            PNode replacement = ((ParserCallNode)node).createPythonNode(factory);
//            ((ParserCallNode)node).getScope().tmpNodeCount--;
//            NodeUtil.replaceChild(parent, node, replacement);
//        } else if (node instanceof ParserTmpVariableNode) {
//            ParserTmpVariableNode tmpNode = (ParserTmpVariableNode) node;
//            Node parent = node.getParent();
//            factory.getScopeEnvironment().setCurrentScope(tmpNode.getScope());
//            PNode replacement = (ExpressionNode)factory.getScopeEnvironment().findVariable(tmpNode.getName());
//            tmpNode.getScope().tmpNodeCount--;
//            NodeUtil.replaceChild(parent, node, replacement);
        }
        return true;
    }

}
