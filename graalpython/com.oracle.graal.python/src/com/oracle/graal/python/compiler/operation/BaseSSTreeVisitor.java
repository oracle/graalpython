package com.oracle.graal.python.compiler.operation;

import com.oracle.graal.python.pegparser.sst.AliasTy;
import com.oracle.graal.python.pegparser.sst.ArgTy;
import com.oracle.graal.python.pegparser.sst.ArgumentsTy;
import com.oracle.graal.python.pegparser.sst.ComprehensionTy;
import com.oracle.graal.python.pegparser.sst.ExceptHandlerTy;
import com.oracle.graal.python.pegparser.sst.ExprTy;
import com.oracle.graal.python.pegparser.sst.KeywordTy;
import com.oracle.graal.python.pegparser.sst.MatchCaseTy;
import com.oracle.graal.python.pegparser.sst.ModTy;
import com.oracle.graal.python.pegparser.sst.PatternTy;
import com.oracle.graal.python.pegparser.sst.SSTNode;
import com.oracle.graal.python.pegparser.sst.SSTreeVisitor;
import com.oracle.graal.python.pegparser.sst.StmtTy;
import com.oracle.graal.python.pegparser.sst.TypeIgnoreTy.TypeIgnore;
import com.oracle.graal.python.pegparser.sst.WithItemTy;

public class BaseSSTreeVisitor<T> implements SSTreeVisitor<T> {

    public T defaultValue(SSTNode node) {
        throw new UnsupportedOperationException(getClass().getSimpleName() + ": " + node.getClass().getSimpleName());
    }

    protected void visitNode(SSTNode node) {
        if (node != null) {
            node.accept(this);
        }
    }

    public T visit(AliasTy node) {
        return defaultValue(node);
    }

    public T visit(ArgTy node) {
        return defaultValue(node);
    }

    public T visit(ArgumentsTy node) {
        visitSequence(node.defaults);
        visitSequence(node.kwDefaults);
        return defaultValue(node);
    }

    public T visit(ComprehensionTy node) {
        visitNode(node.iter);
        visitSequence(node.ifs);
        visitNode(node.target);
        return defaultValue(node);
    }

    public T visit(ExprTy.Attribute node) {
        visitNode(node.value);
        return defaultValue(node);
    }

    public T visit(ExprTy.Await node) {
        visitNode(node.value);
        return defaultValue(node);
    }

    public T visit(ExprTy.BinOp node) {
        visitNode(node.left);
        visitNode(node.right);
        return defaultValue(node);
    }

    public T visit(ExprTy.BoolOp node) {
        visitSequence(node.values);
        return defaultValue(node);
    }

    public T visit(ExprTy.Call node) {
        visitNode(node.func);
        visitSequence(node.args);
        visitSequence(node.keywords);
        return defaultValue(node);
    }

    public T visit(ExprTy.Compare node) {
        visitNode(node.left);
        visitSequence(node.comparators);
        return defaultValue(node);
    }

    public T visit(ExprTy.Constant node) {
        return defaultValue(node);
    }

    public T visit(ExprTy.Dict node) {
        visitSequence(node.keys);
        visitSequence(node.values);
        return defaultValue(node);
    }

    public T visit(ExprTy.DictComp node) {
        visitSequence(node.generators);
        visitNode(node.key);
        visitNode(node.value);
        return defaultValue(node);
    }

    public T visit(ExprTy.FormattedValue node) {
        visitNode(node.formatSpec);
        visitNode(node.value);
        return defaultValue(node);
    }

    public T visit(ExprTy.GeneratorExp node) {
        visitNode(node.element);
        visitSequence(node.generators);
        return defaultValue(node);
    }

    public T visit(ExprTy.IfExp node) {
        visitNode(node.test);
        visitNode(node.body);
        visitNode(node.orElse);
        return defaultValue(node);
    }

    public T visit(ExprTy.JoinedStr node) {
        visitSequence(node.values);
        return defaultValue(node);
    }

    public T visit(ExprTy.Lambda node) {
        visitNode(node.body);
        return defaultValue(node);
    }

    public T visit(ExprTy.List node) {
        visitSequence(node.elements);
        return defaultValue(node);
    }

    public T visit(ExprTy.ListComp node) {
        visitSequence(node.generators);
        visitNode(node.element);
        return defaultValue(node);
    }

    public T visit(ExprTy.Name node) {
        return defaultValue(node);
    }

    public T visit(ExprTy.NamedExpr node) {
        visitNode(node.target);
        visitNode(node.value);
        return defaultValue(node);
    }

    public T visit(ExprTy.Set node) {
        visitSequence(node.elements);
        return defaultValue(node);
    }

    public T visit(ExprTy.SetComp node) {
        visitSequence(node.generators);
        visitNode(node.element);
        return defaultValue(node);
    }

    public T visit(ExprTy.Slice node) {
        visitNode(node.lower);
        visitNode(node.upper);
        visitNode(node.step);
        return defaultValue(node);
    }

    public T visit(ExprTy.Starred node) {
        visitNode(node.value);
        return defaultValue(node);
    }

    public T visit(ExprTy.Subscript node) {
        visitNode(node.value);
        visitNode(node.slice);
        return defaultValue(node);
    }

    public T visit(ExprTy.Tuple node) {
        visitSequence(node.elements);
        return defaultValue(node);
    }

    public T visit(ExprTy.UnaryOp node) {
        visitNode(node.operand);
        return defaultValue(node);
    }

    public T visit(ExprTy.Yield node) {
        visitNode(node.value);
        return defaultValue(node);
    }

    public T visit(ExprTy.YieldFrom node) {
        visitNode(node.value);
        return defaultValue(node);
    }

    public T visit(KeywordTy node) {
        visitNode(node.value);
        return defaultValue(node);
    }

    public T visit(ModTy.Expression node) {
        visitNode(node.body);
        return defaultValue(node);
    }

    public T visit(ModTy.FunctionType node) {
        visitNode(node.returns);
        return defaultValue(node);
    }

    public T visit(ModTy.Interactive node) {
        visitSequence(node.body);
        return defaultValue(node);
    }

    public T visit(ModTy.Module node) {
        visitSequence(node.body);
        return defaultValue(node);
    }

    public T visit(StmtTy.AnnAssign node) {
        visitNode(node.target);
        visitNode(node.annotation);
        visitNode(node.value);
        return defaultValue(node);
    }

    public T visit(StmtTy.Assert node) {
        visitNode(node.test);
        visitNode(node.msg);
        return defaultValue(node);
    }

    public T visit(StmtTy.Assign node) {
        visitNode(node.value);
        visitSequence(node.targets);
        return defaultValue(node);
    }

    public T visit(StmtTy.AsyncFor node) {
        visitNode(node.target);
        visitNode(node.iter);
        visitSequence(node.body);
        visitSequence(node.orElse);
        return defaultValue(node);
    }

    public T visit(StmtTy.AsyncFunctionDef node) {
        visitSequence(node.decoratorList);
        visitNode(node.args);
        visitNode(node.returns);
        visitSequence(node.body);
        return defaultValue(node);
    }

    public T visit(StmtTy.AsyncWith node) {
        visitSequence(node.items);
        visitSequence(node.body);
        return defaultValue(node);
    }

    public T visit(StmtTy.AugAssign node) {
        visitNode(node.target);
        visitNode(node.value);
        return defaultValue(node);
    }

    public T visit(StmtTy.ClassDef node) {
        visitSequence(node.decoratorList);
        visitSequence(node.bases);
        visitSequence(node.keywords);
        visitSequence(node.body);
        return defaultValue(node);
    }

    public T visit(StmtTy.Delete node) {
        visitSequence(node.targets);
        return defaultValue(node);
    }

    public T visit(StmtTy.Expr node) {
        visitNode(node.value);
        return defaultValue(node);
    }

    public T visit(StmtTy.For node) {
        visitNode(node.iter);
        visitNode(node.target);
        visitSequence(node.body);
        visitSequence(node.orElse);
        return defaultValue(node);
    }

    public T visit(StmtTy.FunctionDef node) {
        visitSequence(node.decoratorList);
        visitNode(node.args);
        visitNode(node.returns);
        visitSequence(node.body);
        return defaultValue(node);
    }

    public T visit(StmtTy.Global node) {
        return defaultValue(node);
    }

    public T visit(StmtTy.If node) {
        visitNode(node.test);
        visitSequence(node.body);
        visitSequence(node.orElse);
        return defaultValue(node);
    }

    public T visit(StmtTy.Import node) {
        return defaultValue(node);
    }

    public T visit(StmtTy.ImportFrom node) {
        return defaultValue(node);
    }

    public T visit(StmtTy.Match node) {
        visitNode(node.subject);
        visitSequence(node.cases);
        return defaultValue(node);
    }

    public T visit(MatchCaseTy node) {
        visitNode(node.pattern);
        visitNode(node.guard);
        visitSequence(node.body);
        return defaultValue(node);
    }

    public T visit(PatternTy.MatchAs node) {
        visitNode(node.pattern);
        return defaultValue(node);
    }

    public T visit(PatternTy.MatchClass node) {
        visitSequence(node.patterns);
        visitSequence(node.kwdPatterns);
        visitNode(node.cls);
        return defaultValue(node);
    }

    public T visit(PatternTy.MatchMapping node) {
        visitSequence(node.keys);
        visitSequence(node.patterns);
        return defaultValue(node);
    }

    public T visit(PatternTy.MatchOr node) {
        visitSequence(node.patterns);
        return defaultValue(node);
    }

    public T visit(PatternTy.MatchSequence node) {
        visitSequence(node.patterns);
        return defaultValue(node);
    }

    public T visit(PatternTy.MatchSingleton node) {
        return defaultValue(node);
    }

    public T visit(PatternTy.MatchStar node) {
        return defaultValue(node);
    }

    public T visit(PatternTy.MatchValue node) {
        visitNode(node.value);
        return defaultValue(node);
    }

    public T visit(StmtTy.Nonlocal node) {
        return defaultValue(node);
    }

    public T visit(StmtTy.Raise node) {
        visitNode(node.exc);
        visitNode(node.cause);
        return defaultValue(node);
    }

    public T visit(StmtTy.Return node) {
        visitNode(node.value);
        return defaultValue(node);
    }

    public T visit(StmtTy.Try node) {
        visitSequence(node.body);
        visitSequence(node.orElse);
        visitSequence(node.finalBody);
        visitSequence(node.handlers);
        return defaultValue(node);
    }

    public T visit(ExceptHandlerTy.ExceptHandler node) {
        visitNode(node.type);
        visitSequence(node.body);
        return defaultValue(node);
    }

    public T visit(StmtTy.While node) {
        visitNode(node.test);
        visitSequence(node.body);
        visitSequence(node.orElse);
        return defaultValue(node);
    }

    public T visit(StmtTy.With node) {
        visitSequence(node.items);
        visitSequence(node.body);
        return defaultValue(node);
    }

    public T visit(WithItemTy node) {
        visitNode(node.contextExpr);
        visitNode(node.optionalVars);
        return defaultValue(node);
    }

    public T visit(StmtTy.Break node) {
        return defaultValue(node);
    }

    public T visit(StmtTy.Continue node) {
        return defaultValue(node);
    }

    public T visit(StmtTy.Pass node) {
        return defaultValue(node);
    }

    public T visit(TypeIgnore node) {
        return defaultValue(node);
    }
}