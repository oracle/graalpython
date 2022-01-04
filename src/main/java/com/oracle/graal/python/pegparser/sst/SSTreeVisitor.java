/*
 * Copyright (c) 2019, 2021, Oracle and/or its affiliates. All rights reserved.
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

package com.oracle.graal.python.pegparser.sst;

public interface SSTreeVisitor<T> {
    default <T> T visitSequence(SSTNode[] sequence) {
        if (sequence != null) {
            for (SSTNode n : sequence) {
                n.accept(this);
            }
        }
        return null;
    }

    T visit(AliasTy node);

    T visit(ArgTy node);

    T visit(ArgumentsTy node);

    T visit(ComprehensionTy aThis);

    T visit(ExprTy.Attribute node);

    T visit(ExprTy.Await node);

    T visit(ExprTy.BinOp node);

    T visit(ExprTy.BoolOp node);

    T visit(ExprTy.Call node);

    T visit(ExprTy.Compare node);

    T visit(ExprTy.Constant node);

    T visit(ExprTy.Dict node);

    T visit(ExprTy.DictComp node);

    T visit(ExprTy.FormattedValue node);

    T visit(ExprTy.GeneratorExp node);

    T visit(ExprTy.IfExp node);

    T visit(ExprTy.JoinedStr node);

    T visit(ExprTy.Lambda node);

    T visit(ExprTy.List node);

    T visit(ExprTy.ListComp node);

    T visit(ExprTy.Name node);

    T visit(ExprTy.NamedExpr node);

    T visit(ExprTy.Set node);

    T visit(ExprTy.SetComp node);

    T visit(ExprTy.Slice node);

    T visit(ExprTy.Starred node);

    T visit(ExprTy.Subscript node);

    T visit(ExprTy.Tuple node);

    T visit(ExprTy.UnaryOp node);

    T visit(ExprTy.Yield node);

    T visit(ExprTy.YieldFrom node);

    T visit(KeywordTy node);

    T visit(ModTy.Expression node);

    T visit(ModTy.FunctionType node);

    T visit(ModTy.Interactive node);

    T visit(ModTy.Module node);

    T visit(ModTy.TypeIgnore node);

    T visit(StmtTy.AnnAssign node);

    T visit(StmtTy.Assert node);

    T visit(StmtTy.Assign node);

    T visit(StmtTy.AsyncFor node);

    T visit(StmtTy.AsyncFunctionDef node);

    T visit(StmtTy.AsyncWith node);

    T visit(StmtTy.AugAssign node);

    T visit(StmtTy.ClassDef node);

    T visit(StmtTy.Delete node);

    T visit(StmtTy.Expr node);

    T visit(StmtTy.For node);

    T visit(StmtTy.FunctionDef node);

    T visit(StmtTy.Global node);

    T visit(StmtTy.If node);

    T visit(StmtTy.Import node);

    T visit(StmtTy.ImportFrom node);

    T visit(StmtTy.Match node);

    T visit(StmtTy.Match.Case node);

    T visit(StmtTy.Match.Pattern.MatchAs node);

    T visit(StmtTy.Match.Pattern.MatchClass node);

    T visit(StmtTy.Match.Pattern.MatchMapping node);

    T visit(StmtTy.Match.Pattern.MatchOr node);

    T visit(StmtTy.Match.Pattern.MatchSequence node);

    T visit(StmtTy.Match.Pattern.MatchSingleton node);

    T visit(StmtTy.Match.Pattern.MatchStar node);

    T visit(StmtTy.Match.Pattern.MatchValue node);

    T visit(StmtTy.NonLocal node);

    T visit(StmtTy.Raise node);

    T visit(StmtTy.Return node);

    T visit(StmtTy.Try node);

    T visit(StmtTy.Try.ExceptHandler node);

    T visit(StmtTy.While node);

    T visit(StmtTy.With node);

    T visit(StmtTy.With.Item node);

    T visit(StmtTy.Break aThis);

    T visit(StmtTy.Continue aThis);

    T visit(StmtTy.Pass aThis);
}
