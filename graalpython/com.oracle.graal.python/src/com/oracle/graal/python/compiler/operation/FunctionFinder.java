package com.oracle.graal.python.compiler.operation;

import java.util.ArrayList;
import java.util.List;

import com.oracle.graal.python.pegparser.sst.ExprTy;
import com.oracle.graal.python.pegparser.sst.ModTy;
import com.oracle.graal.python.pegparser.sst.SSTNode;
import com.oracle.graal.python.pegparser.sst.StmtTy;

public final class FunctionFinder extends BaseSSTreeVisitor<Void> {
    private final List<SSTNode> rootNodes = new ArrayList<>();

    public List<SSTNode> getRootNodes() {
        return rootNodes;
    }

    @Override
    public Void defaultValue(SSTNode node) {
        return null;
    }

    @Override
    public Void visit(ExprTy.DictComp node) {
        super.visit(node);
        rootNodes.add(node);
        return null;
    }

    @Override
    public Void visit(ExprTy.GeneratorExp node) {
        super.visit(node);
        rootNodes.add(node);
        return null;
    }

    @Override
    public Void visit(ExprTy.Lambda node) {
        super.visit(node);
        rootNodes.add(node);
        return null;
    }

    @Override
    public Void visit(ExprTy.ListComp node) {
        super.visit(node);
        rootNodes.add(node);
        return null;
    }

    @Override
    public Void visit(ExprTy.SetComp node) {
        super.visit(node);
        rootNodes.add(node);
        return null;
    }

    @Override
    public Void visit(ModTy.Expression node) {
        super.visit(node);
        rootNodes.add(node);
        return null;
    }

    @Override
    public Void visit(ModTy.FunctionType node) {
        super.visit(node);
        rootNodes.add(node);
        return null;
    }

    @Override
    public Void visit(ModTy.Interactive node) {
        super.visit(node);
        rootNodes.add(node);
        return null;
    }

    @Override
    public Void visit(ModTy.Module node) {
        super.visit(node);
        rootNodes.add(node);
        return null;
    }

    @Override
    public Void visit(StmtTy.AnnAssign node) {
        super.visit(node);
        rootNodes.add(node);
        return null;
    }

    @Override
    public Void visit(StmtTy.AsyncFunctionDef node) {
        super.visit(node);
        rootNodes.add(node);
        return null;
    }

    @Override
    public Void visit(StmtTy.ClassDef node) {
        super.visit(node);
        rootNodes.add(node);
        return null;
    }

    @Override
    public Void visit(StmtTy.FunctionDef node) {
        super.visit(node);
        rootNodes.add(node);
        return null;
    }

}