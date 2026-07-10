/*
 * Copyright (c) 2023, 2026, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 *
 * Subject to the condition set forth below, permission is hereby granted to any
 * person obtaining a copy of this software, associated documentation and/or
 * data (collectively the "Software"), free of charge and under any and all
 * copyright rights in the Software, and any and all patent rights owned or
 * freely licensable by each licensor hereunder covering either (i) the
 * unmodified Software as contributed to or provided by such licensor, or (ii)
 * the Larger Works (as defined below), to deal in both
 *
 * (a) the Software, and
 *
 * (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
 * one is included with the Software each a "Larger Work" to which the Software
 * is contributed by such licensors),
 *
 * without restriction, including without limitation the rights to copy, create
 * derivative works of, display, perform, and distribute the Software and make,
 * use, sell, offer for sale, import, export, have made, and have sold the
 * Software and the Larger Work(s), and to sublicense the foregoing rights on
 * either these or other terms.
 *
 * This license is subject to the following condition:
 *
 * The above copyright notice and either this complete permission notice or at a
 * minimum a reference to the UPL must be included in all copies or substantial
 * portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package com.oracle.graal.python.compiler.bytecode_dsl;

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
import com.oracle.graal.python.pegparser.sst.TypeParamTy;
import com.oracle.graal.python.pegparser.sst.WithItemTy;

/**
 * This interface provides default throwing implementations of all {@code SSTreeVisitor} methods.
 * It is useful for visitors that only support a subset of {@code SSTNode}s.
 */
public interface BaseBytecodeDSLVisitor<T> extends SSTreeVisitor<T> {

    default T defaultValue(SSTNode node) {
        throw new UnsupportedOperationException(getClass().getSimpleName() + ": " + node.getClass().getSimpleName());
    }

    default void visitNode(SSTNode node) {
        if (node != null) {
            node.accept(this);
        }
    }

    default ExprTy visitExpr(ExprTy node) {
        visitNode(node);
        return node;
    }

    @Override
    default <U> U visitSequence(SSTNode[] sequence) {
        if (sequence != null) {
            for (int i = 0; i < sequence.length; i++) {
                if (sequence[i] instanceof ExprTy expression) {
                    sequence[i] = visitExpr(expression);
                } else if (sequence[i] != null) {
                    sequence[i].accept(this);
                }
            }
        }
        return null;
    }

    default T visit(AliasTy node) {
        return defaultValue(node);
    }

    default T visit(ArgTy node) {
        return defaultValue(node);
    }

    default T visit(ArgumentsTy node) {
        visitSequence(node.defaults);
        visitSequence(node.kwDefaults);
        return defaultValue(node);
    }

    default T visit(ComprehensionTy node) {
        node.iter = visitExpr(node.iter);
        visitSequence(node.ifs);
        node.target = visitExpr(node.target);
        return defaultValue(node);
    }

    default T visit(ExprTy.Attribute node) {
        node.value = visitExpr(node.value);
        return defaultValue(node);
    }

    default T visit(ExprTy.Await node) {
        node.value = visitExpr(node.value);
        return defaultValue(node);
    }

    default T visit(ExprTy.BinOp node) {
        node.left = visitExpr(node.left);
        node.right = visitExpr(node.right);
        return defaultValue(node);
    }

    default T visit(ExprTy.BoolOp node) {
        visitSequence(node.values);
        return defaultValue(node);
    }

    default T visit(ExprTy.Call node) {
        node.func = visitExpr(node.func);
        visitSequence(node.args);
        visitSequence(node.keywords);
        return defaultValue(node);
    }

    default T visit(ExprTy.Compare node) {
        node.left = visitExpr(node.left);
        visitSequence(node.comparators);
        return defaultValue(node);
    }

    default T visit(ExprTy.Constant node) {
        return defaultValue(node);
    }

    default T visit(ExprTy.Dict node) {
        visitSequence(node.keys);
        visitSequence(node.values);
        return defaultValue(node);
    }

    default T visit(ExprTy.DictComp node) {
        visitSequence(node.generators);
        node.key = visitExpr(node.key);
        node.value = visitExpr(node.value);
        return defaultValue(node);
    }

    default T visit(ExprTy.FormattedValue node) {
        node.formatSpec = visitExpr(node.formatSpec);
        node.value = visitExpr(node.value);
        return defaultValue(node);
    }

    default T visit(ExprTy.GeneratorExp node) {
        node.element = visitExpr(node.element);
        visitSequence(node.generators);
        return defaultValue(node);
    }

    default T visit(ExprTy.IfExp node) {
        node.test = visitExpr(node.test);
        node.body = visitExpr(node.body);
        node.orElse = visitExpr(node.orElse);
        return defaultValue(node);
    }

    default T visit(ExprTy.JoinedStr node) {
        visitSequence(node.values);
        return defaultValue(node);
    }

    default T visit(ExprTy.Lambda node) {
        visitNode(node.args);
        node.body = visitExpr(node.body);
        return defaultValue(node);
    }

    default T visit(ExprTy.List node) {
        visitSequence(node.elements);
        return defaultValue(node);
    }

    default T visit(ExprTy.ListComp node) {
        visitSequence(node.generators);
        node.element = visitExpr(node.element);
        return defaultValue(node);
    }

    default T visit(ExprTy.Name node) {
        return defaultValue(node);
    }

    default T visit(ExprTy.NamedExpr node) {
        node.target = visitExpr(node.target);
        node.value = visitExpr(node.value);
        return defaultValue(node);
    }

    default T visit(ExprTy.Set node) {
        visitSequence(node.elements);
        return defaultValue(node);
    }

    default T visit(ExprTy.SetComp node) {
        visitSequence(node.generators);
        node.element = visitExpr(node.element);
        return defaultValue(node);
    }

    default T visit(ExprTy.Slice node) {
        node.lower = visitExpr(node.lower);
        node.upper = visitExpr(node.upper);
        node.step = visitExpr(node.step);
        return defaultValue(node);
    }

    default T visit(ExprTy.Starred node) {
        node.value = visitExpr(node.value);
        return defaultValue(node);
    }

    default T visit(ExprTy.Subscript node) {
        node.value = visitExpr(node.value);
        node.slice = visitExpr(node.slice);
        return defaultValue(node);
    }

    default T visit(ExprTy.Tuple node) {
        visitSequence(node.elements);
        return defaultValue(node);
    }

    default T visit(ExprTy.UnaryOp node) {
        node.operand = visitExpr(node.operand);
        return defaultValue(node);
    }

    default T visit(ExprTy.Yield node) {
        node.value = visitExpr(node.value);
        return defaultValue(node);
    }

    default T visit(ExprTy.YieldFrom node) {
        node.value = visitExpr(node.value);
        return defaultValue(node);
    }

    default T visit(KeywordTy node) {
        node.value = visitExpr(node.value);
        return defaultValue(node);
    }

    default T visit(ModTy.Expression node) {
        node.body = visitExpr(node.body);
        return defaultValue(node);
    }

    default T visit(ModTy.FunctionType node) {
        node.returns = visitExpr(node.returns);
        return defaultValue(node);
    }

    default T visit(ModTy.Interactive node) {
        visitSequence(node.body);
        return defaultValue(node);
    }

    default T visit(ModTy.Module node) {
        visitSequence(node.body);
        return defaultValue(node);
    }

    default T visit(StmtTy.AnnAssign node) {
        node.target = visitExpr(node.target);
        node.annotation = visitExpr(node.annotation);
        node.value = visitExpr(node.value);
        return defaultValue(node);
    }

    default T visit(StmtTy.Assert node) {
        node.test = visitExpr(node.test);
        node.msg = visitExpr(node.msg);
        return defaultValue(node);
    }

    default T visit(StmtTy.Assign node) {
        node.value = visitExpr(node.value);
        visitSequence(node.targets);
        return defaultValue(node);
    }

    default T visit(StmtTy.AsyncFor node) {
        node.target = visitExpr(node.target);
        node.iter = visitExpr(node.iter);
        visitSequence(node.body);
        visitSequence(node.orElse);
        return defaultValue(node);
    }

    default T visit(StmtTy.AsyncFunctionDef node) {
        visitSequence(node.decoratorList);
        visitNode(node.args);
        node.returns = visitExpr(node.returns);
        visitSequence(node.body);
        return defaultValue(node);
    }

    default T visit(StmtTy.AsyncWith node) {
        visitSequence(node.items);
        visitSequence(node.body);
        return defaultValue(node);
    }

    default T visit(StmtTy.AugAssign node) {
        node.target = visitExpr(node.target);
        node.value = visitExpr(node.value);
        return defaultValue(node);
    }

    default T visit(StmtTy.ClassDef node) {
        visitSequence(node.decoratorList);
        visitSequence(node.bases);
        visitSequence(node.keywords);
        visitSequence(node.body);
        return defaultValue(node);
    }

    default T visit(StmtTy.Delete node) {
        visitSequence(node.targets);
        return defaultValue(node);
    }

    default T visit(StmtTy.Expr node) {
        node.value = visitExpr(node.value);
        return defaultValue(node);
    }

    default T visit(StmtTy.For node) {
        node.iter = visitExpr(node.iter);
        node.target = visitExpr(node.target);
        visitSequence(node.body);
        visitSequence(node.orElse);
        return defaultValue(node);
    }

    default T visit(StmtTy.FunctionDef node) {
        visitSequence(node.decoratorList);
        visitNode(node.args);
        node.returns = visitExpr(node.returns);
        visitSequence(node.body);
        return defaultValue(node);
    }

    default T visit(StmtTy.Global node) {
        return defaultValue(node);
    }

    default T visit(StmtTy.If node) {
        node.test = visitExpr(node.test);
        visitSequence(node.body);
        visitSequence(node.orElse);
        return defaultValue(node);
    }

    default T visit(StmtTy.Import node) {
        return defaultValue(node);
    }

    default T visit(StmtTy.ImportFrom node) {
        return defaultValue(node);
    }

    default T visit(StmtTy.Match node) {
        node.subject = visitExpr(node.subject);
        visitSequence(node.cases);
        return defaultValue(node);
    }

    default T visit(MatchCaseTy node) {
        visitNode(node.pattern);
        node.guard = visitExpr(node.guard);
        visitSequence(node.body);
        return defaultValue(node);
    }

    default T visit(PatternTy.MatchAs node) {
        visitNode(node.pattern);
        return defaultValue(node);
    }

    default T visit(PatternTy.MatchClass node) {
        visitSequence(node.patterns);
        visitSequence(node.kwdPatterns);
        node.cls = visitExpr(node.cls);
        return defaultValue(node);
    }

    default T visit(PatternTy.MatchMapping node) {
        visitSequence(node.keys);
        visitSequence(node.patterns);
        return defaultValue(node);
    }

    default T visit(PatternTy.MatchOr node) {
        visitSequence(node.patterns);
        return defaultValue(node);
    }

    default T visit(PatternTy.MatchSequence node) {
        visitSequence(node.patterns);
        return defaultValue(node);
    }

    default T visit(PatternTy.MatchSingleton node) {
        return defaultValue(node);
    }

    default T visit(PatternTy.MatchStar node) {
        return defaultValue(node);
    }

    default T visit(PatternTy.MatchValue node) {
        node.value = visitExpr(node.value);
        return defaultValue(node);
    }

    default T visit(StmtTy.Nonlocal node) {
        return defaultValue(node);
    }

    default T visit(StmtTy.Raise node) {
        node.exc = visitExpr(node.exc);
        node.cause = visitExpr(node.cause);
        return defaultValue(node);
    }

    default T visit(StmtTy.Return node) {
        node.value = visitExpr(node.value);
        return defaultValue(node);
    }

    default T visit(StmtTy.Try node) {
        visitSequence(node.body);
        visitSequence(node.orElse);
        visitSequence(node.finalBody);
        visitSequence(node.handlers);
        return defaultValue(node);
    }

    default T visit(StmtTy.TryStar node) {
        return defaultValue(node);
    }

    default T visit(ExceptHandlerTy.ExceptHandler node) {
        node.type = visitExpr(node.type);
        visitSequence(node.body);
        return defaultValue(node);
    }

    default T visit(StmtTy.While node) {
        node.test = visitExpr(node.test);
        visitSequence(node.body);
        visitSequence(node.orElse);
        return defaultValue(node);
    }

    default T visit(StmtTy.With node) {
        visitSequence(node.items);
        visitSequence(node.body);
        return defaultValue(node);
    }

    default T visit(WithItemTy node) {
        node.contextExpr = visitExpr(node.contextExpr);
        node.optionalVars = visitExpr(node.optionalVars);
        return defaultValue(node);
    }

    default T visit(StmtTy.Break node) {
        return defaultValue(node);
    }

    default T visit(StmtTy.Continue node) {
        return defaultValue(node);
    }

    default T visit(StmtTy.Pass node) {
        return defaultValue(node);
    }

    default T visit(TypeIgnore node) {
        return defaultValue(node);
    }

    default T visit(StmtTy.TypeAlias node) {
        return defaultValue(node);
    }

    default T visit(TypeParamTy.TypeVar node) {
        return defaultValue(node);
    }

    default T visit(TypeParamTy.ParamSpec node) {
        return defaultValue(node);
    }

    default T visit(TypeParamTy.TypeVarTuple node) {
        return defaultValue(node);
    }
}
