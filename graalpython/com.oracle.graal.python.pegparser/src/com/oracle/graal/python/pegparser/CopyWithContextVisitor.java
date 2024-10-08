/*
 * Copyright (c) 2022, 2024, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.pegparser;

import com.oracle.graal.python.pegparser.sst.AliasTy;
import com.oracle.graal.python.pegparser.sst.ArgTy;
import com.oracle.graal.python.pegparser.sst.ArgumentsTy;
import com.oracle.graal.python.pegparser.sst.ComprehensionTy;
import com.oracle.graal.python.pegparser.sst.ExceptHandlerTy;
import com.oracle.graal.python.pegparser.sst.ExprContextTy;
import com.oracle.graal.python.pegparser.sst.ExprTy;
import com.oracle.graal.python.pegparser.sst.KeywordTy;
import com.oracle.graal.python.pegparser.sst.MatchCaseTy;
import com.oracle.graal.python.pegparser.sst.ModTy;
import com.oracle.graal.python.pegparser.sst.PatternTy;
import com.oracle.graal.python.pegparser.sst.SSTreeVisitor;
import com.oracle.graal.python.pegparser.sst.StmtTy;
import com.oracle.graal.python.pegparser.sst.StmtTy.TypeAlias;
import com.oracle.graal.python.pegparser.sst.TypeIgnoreTy;
import com.oracle.graal.python.pegparser.sst.TypeParamTy.ParamSpec;
import com.oracle.graal.python.pegparser.sst.TypeParamTy.TypeVar;
import com.oracle.graal.python.pegparser.sst.TypeParamTy.TypeVarTuple;
import com.oracle.graal.python.pegparser.sst.WithItemTy;

public final class CopyWithContextVisitor implements SSTreeVisitor<ExprTy> {

    private final ExprContextTy newContext;

    public CopyWithContextVisitor(ExprContextTy newContext) {
        this.newContext = newContext;
    }

    @Override
    public ExprTy visit(ModTy.Module node) {
        throw new IllegalStateException();
    }

    @Override
    public ExprTy visit(ModTy.Interactive node) {
        throw new IllegalStateException();
    }

    @Override
    public ExprTy visit(ModTy.Expression node) {
        throw new IllegalStateException();
    }

    @Override
    public ExprTy visit(ModTy.FunctionType node) {
        throw new IllegalStateException();
    }

    @Override
    public ExprTy visit(StmtTy.FunctionDef node) {
        throw new IllegalStateException();
    }

    @Override
    public ExprTy visit(StmtTy.AsyncFunctionDef node) {
        throw new IllegalStateException();
    }

    @Override
    public ExprTy visit(StmtTy.ClassDef node) {
        throw new IllegalStateException();
    }

    @Override
    public ExprTy visit(StmtTy.Return node) {
        throw new IllegalStateException();
    }

    @Override
    public ExprTy visit(StmtTy.Delete node) {
        throw new IllegalStateException();
    }

    @Override
    public ExprTy visit(StmtTy.Assign node) {
        throw new IllegalStateException();
    }

    @Override
    public ExprTy visit(StmtTy.AugAssign node) {
        throw new IllegalStateException();
    }

    @Override
    public ExprTy visit(StmtTy.AnnAssign node) {
        throw new IllegalStateException();
    }

    @Override
    public ExprTy visit(StmtTy.For node) {
        throw new IllegalStateException();
    }

    @Override
    public ExprTy visit(StmtTy.AsyncFor node) {
        throw new IllegalStateException();
    }

    @Override
    public ExprTy visit(StmtTy.While node) {
        throw new IllegalStateException();
    }

    @Override
    public ExprTy visit(StmtTy.If node) {
        throw new IllegalStateException();
    }

    @Override
    public ExprTy visit(StmtTy.With node) {
        throw new IllegalStateException();
    }

    @Override
    public ExprTy visit(StmtTy.AsyncWith node) {
        throw new IllegalStateException();
    }

    @Override
    public ExprTy visit(StmtTy.Match node) {
        throw new IllegalStateException();
    }

    @Override
    public ExprTy visit(StmtTy.Raise node) {
        throw new IllegalStateException();
    }

    @Override
    public ExprTy visit(StmtTy.Try node) {
        throw new IllegalStateException();
    }

    @Override
    public ExprTy visit(StmtTy.TryStar node) {
        throw new IllegalStateException();
    }

    @Override
    public ExprTy visit(StmtTy.Assert node) {
        throw new IllegalStateException();
    }

    @Override
    public ExprTy visit(StmtTy.Import node) {
        throw new IllegalStateException();
    }

    @Override
    public ExprTy visit(StmtTy.ImportFrom node) {
        throw new IllegalStateException();
    }

    @Override
    public ExprTy visit(StmtTy.Global node) {
        throw new IllegalStateException();
    }

    @Override
    public ExprTy visit(StmtTy.Nonlocal node) {
        throw new IllegalStateException();
    }

    @Override
    public ExprTy visit(StmtTy.Expr node) {
        throw new IllegalStateException();
    }

    @Override
    public ExprTy visit(StmtTy.Pass node) {
        throw new IllegalStateException();
    }

    @Override
    public ExprTy visit(StmtTy.Break node) {
        throw new IllegalStateException();
    }

    @Override
    public ExprTy visit(StmtTy.Continue node) {
        throw new IllegalStateException();
    }

    @Override
    public ExprTy visit(ExprTy.BoolOp node) {
        return node;
    }

    @Override
    public ExprTy visit(ExprTy.NamedExpr node) {
        return node;
    }

    @Override
    public ExprTy visit(ExprTy.BinOp node) {
        return node;
    }

    @Override
    public ExprTy visit(ExprTy.UnaryOp node) {
        return node;
    }

    @Override
    public ExprTy visit(ExprTy.Lambda node) {
        return node;
    }

    @Override
    public ExprTy visit(ExprTy.IfExp node) {
        return node;
    }

    @Override
    public ExprTy visit(ExprTy.Dict node) {
        return node;
    }

    @Override
    public ExprTy visit(ExprTy.Set node) {
        return node;
    }

    @Override
    public ExprTy visit(ExprTy.ListComp node) {
        return node;
    }

    @Override
    public ExprTy visit(ExprTy.SetComp node) {
        return node;
    }

    @Override
    public ExprTy visit(ExprTy.DictComp node) {
        return node;
    }

    @Override
    public ExprTy visit(ExprTy.GeneratorExp node) {
        return node;
    }

    @Override
    public ExprTy visit(ExprTy.Await node) {
        return node;
    }

    @Override
    public ExprTy visit(ExprTy.Yield node) {
        return node;
    }

    @Override
    public ExprTy visit(ExprTy.YieldFrom node) {
        return node;
    }

    @Override
    public ExprTy visit(ExprTy.Compare node) {
        return node;
    }

    @Override
    public ExprTy visit(ExprTy.Call node) {
        return node;
    }

    @Override
    public ExprTy visit(ExprTy.FormattedValue node) {
        return node;
    }

    @Override
    public ExprTy visit(ExprTy.JoinedStr node) {
        return node;
    }

    @Override
    public ExprTy visit(ExprTy.Constant node) {
        return node;
    }

    @Override
    public ExprTy visit(ExprTy.Attribute node) {
        return new ExprTy.Attribute(node.value, node.attr, newContext, node.getSourceRange());
    }

    @Override
    public ExprTy visit(ExprTy.Subscript node) {
        return new ExprTy.Subscript(node.value, node.slice, newContext, node.getSourceRange());
    }

    @Override
    public ExprTy visit(ExprTy.Starred node) {
        return new ExprTy.Starred(node.value.accept(this), newContext, node.getSourceRange());
    }

    @Override
    public ExprTy visit(ExprTy.Name node) {
        return new ExprTy.Name(node.id, newContext, node.getSourceRange());
    }

    @Override
    public ExprTy visit(ExprTy.List node) {
        return new ExprTy.List(copySequence(node.elements), newContext, node.getSourceRange());
    }

    @Override
    public ExprTy visit(ExprTy.Tuple node) {
        return new ExprTy.Tuple(copySequence(node.elements), newContext, node.getSourceRange());
    }

    @Override
    public ExprTy visit(ExprTy.Slice node) {
        return node;
    }

    @Override
    public ExprTy visit(ComprehensionTy node) {
        throw new IllegalStateException();
    }

    @Override
    public ExprTy visit(ExceptHandlerTy.ExceptHandler node) {
        throw new IllegalStateException();
    }

    @Override
    public ExprTy visit(ArgumentsTy node) {
        throw new IllegalStateException();
    }

    @Override
    public ExprTy visit(ArgTy node) {
        throw new IllegalStateException();
    }

    @Override
    public ExprTy visit(KeywordTy node) {
        throw new IllegalStateException();
    }

    @Override
    public ExprTy visit(AliasTy node) {
        throw new IllegalStateException();
    }

    @Override
    public ExprTy visit(WithItemTy node) {
        throw new IllegalStateException();
    }

    @Override
    public ExprTy visit(MatchCaseTy node) {
        throw new IllegalStateException();
    }

    @Override
    public ExprTy visit(PatternTy.MatchValue node) {
        throw new IllegalStateException();
    }

    @Override
    public ExprTy visit(PatternTy.MatchSingleton node) {
        throw new IllegalStateException();
    }

    @Override
    public ExprTy visit(PatternTy.MatchSequence node) {
        throw new IllegalStateException();
    }

    @Override
    public ExprTy visit(PatternTy.MatchMapping node) {
        throw new IllegalStateException();
    }

    @Override
    public ExprTy visit(PatternTy.MatchClass node) {
        throw new IllegalStateException();
    }

    @Override
    public ExprTy visit(PatternTy.MatchStar node) {
        throw new IllegalStateException();
    }

    @Override
    public ExprTy visit(PatternTy.MatchAs node) {
        throw new IllegalStateException();
    }

    @Override
    public ExprTy visit(PatternTy.MatchOr node) {
        throw new IllegalStateException();
    }

    @Override
    public ExprTy visit(TypeIgnoreTy.TypeIgnore node) {
        throw new IllegalStateException();
    }

    @Override
    public ExprTy visit(TypeAlias node) {
        throw new IllegalStateException();
    }

    @Override
    public ExprTy visit(TypeVar node) {
        throw new IllegalStateException();
    }

    @Override
    public ExprTy visit(ParamSpec node) {
        throw new IllegalStateException();
    }

    @Override
    public ExprTy visit(TypeVarTuple node) {
        throw new IllegalStateException();
    }

    private ExprTy[] copySequence(ExprTy[] elements) {
        if (elements == null) {
            return null;
        }
        ExprTy[] newElements = new ExprTy[elements.length];
        for (int i = 0; i < newElements.length; i++) {
            newElements[i] = elements[i].accept(this);
        }
        return newElements;
    }
}
