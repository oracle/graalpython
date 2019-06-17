/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.oracle.graal.python.parser.sst;

import com.oracle.graal.python.nodes.NodeFactory;
import com.oracle.graal.python.nodes.PNode;
import com.oracle.graal.python.nodes.expression.BinaryArithmetic;
import com.oracle.graal.python.nodes.expression.ExpressionNode;
import com.oracle.graal.python.nodes.generator.DictConcatNode;
import com.oracle.graal.python.nodes.literal.KeywordLiteralNode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public final class ArgListBuilder {

    private final SSTNode[] EMPTY_SSTN = new SSTNode[0];
    private final ExpressionNode[] EMPTY = new ExpressionNode[0];

    private List<SSTNode> args;
    private List<SSTNode> nameArgNodes;
    private List<String> nameArgNames;
    private List<SSTNode> starArg;
    private List<SSTNode> kwArg;

    public void addArg(SSTNode value) {
        if (args == null) {
            args = new ArrayList<>();
        }
        args.add(value);
    }

    public ExpressionNode[] getArgs(SSTreeVisitor<PNode> visitor) {
        ExpressionNode[] result;
        if (args == null || args.isEmpty()) {
            result = EMPTY;
        } else {
            int len = args.size();
            result = new ExpressionNode[len];
            for (int i = 0; i < len; i++) {
                result[i] = (ExpressionNode)args.get(i).accept(visitor);
            }
        }
        return result;
    }

    public SSTNode[] getArgs() {
        return args == null ? EMPTY_SSTN : args.toArray(new SSTNode[args.size()]);
    }
    
    public void addNamedArg(String name, SSTNode value) {
        if (nameArgNodes == null) {
            nameArgNodes = new ArrayList<>();
            nameArgNames = new ArrayList<>();
        }
        nameArgNodes.add(value);
        nameArgNames.add(name);
    }

    public ExpressionNode[] getNameArgs(SSTreeVisitor<PNode> visitor) {
        ExpressionNode[] result;
        if (nameArgNodes == null || nameArgNodes.isEmpty()) {
            result = EMPTY;
        } else {
            int len = nameArgNodes.size();
            result = new ExpressionNode[len];
            for (int i = 0; i < len; i++) {
                result[i] = new KeywordLiteralNode((ExpressionNode)nameArgNodes.get(i).accept(visitor), nameArgNames.get(i));
            }
        }
        return result;
    }

    public boolean hasNameArg() {
        return !(nameArgNodes == null || nameArgNodes.isEmpty());
    }

    public void addKwArg(SSTNode value) {
        if (kwArg == null) {
            kwArg = new ArrayList<>();
        }
        kwArg.add(value);
    }
    
    public boolean hasKwArg() {
        return kwArg != null;
    }

    public ExpressionNode getKwArgs(SSTreeVisitor<PNode> visitor) {
        ExpressionNode result = null;
        if (kwArg != null && !kwArg.isEmpty()) {
            int len = kwArg.size();
            if (len == 1) {
                result = (ExpressionNode)kwArg.get(0).accept(visitor);
            } else {
                ExpressionNode[] expressions = new ExpressionNode[len];
                for (int i = 0; i < len; i++) {
                    expressions[i] =(ExpressionNode)kwArg.get(i).accept(visitor);
                }
                result = DictConcatNode.create(expressions);
            }
        }
        return result;
    }

    public void addStarArg(SSTNode value) {
        if (starArg == null) {
            starArg = new ArrayList<>();
        }
        starArg.add(value);
    }

    public ExpressionNode getStarArgs(SSTreeVisitor<PNode> visitor) {
        ExpressionNode result = null;
        if (starArg != null && !starArg.isEmpty()) {
            result = (ExpressionNode)starArg.get(0).accept(visitor);
            for (int i = 1; i < starArg.size(); i++) {
                result = BinaryArithmetic.Add.create(result, (ExpressionNode)starArg.get(i).accept(visitor));
            }
        }
        return result;
    }

}
