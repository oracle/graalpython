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
    T visit(ExprTy.BoolOp aThis);

    T visit(ExprTy.NamedExpr aThis);

    T visit(ExprTy.BinOp aThis);

    T visit(ExprTy.UnaryOp aThis);

    T visit(ArgTy aThis);

    T visit(ArgumentsTy aThis);

    T visit(ExprTy.Lambda aThis);

    T visit(ExprTy.IfExp aThis);

    T visit(ExprTy.Dict aThis);

    T visit(ExprTy.Set aThis);

    T visit(ExprTy.ListComp aThis);

    T visit(ExprTy.SetComp aThis);

    T visit(ExprTy.DictComp aThis);

    T visit(ExprTy.GeneratorExp aThis);

    T visit(ExprTy.Await aThis);

    T visit(ExprTy.Yield aThis);

    T visit(ExprTy.YieldFrom aThis);

    T visit(ExprTy.Compare aThis);

    T visit(KeywordTy aThis);

    T visit(ExprTy.FormattedValue aThis);

    T visit(ExprTy.JoinedStr aThis);

    T visit(ExprTy.Constant aThis);

    T visit(ExprTy.Attribute aThis);

    T visit(ExprTy.Subscript aThis);

    T visit(ExprTy.Starred aThis);

    T visit(ExprTy.Name aThis);

    T visit(ExprTy.List aThis);

    T visit(ExprTy.Tuple aThis);

    T visit(ExprTy.Slice aThis);

    T visit(ExprTy.Call aThis);

    T visit(StmtTy.FunctionDef aThis);

    T visit(StmtTy.AsyncFunctionDef aThis);

    T visit(StmtTy.ClassDef aThis);

    T visit(StmtTy.Return aThis);

    T visit(StmtTy.Delete aThis);

    T visit(StmtTy.Assign aThis);

    T visit(StmtTy.AugAssign aThis);

    T visit(StmtTy.AnnAssign aThis);

    T visit(StmtTy.For aThis);

    T visit(StmtTy.AsyncFor aThis);

    T visit(StmtTy.While aThis);

    T visit(StmtTy.If aThis);

    T visit(StmtTy.With.Item aThis);

    T visit(StmtTy.With aThis);

    T visit(StmtTy.AsyncWith aThis);

    T visit(StmtTy.Match aThis);

    T visit(StmtTy.Match.Case aThis);

    T visit(StmtTy.Match.Pattern.MatchValue aThis);

    T visit(StmtTy.Match.Pattern.MatchSingleton aThis);

    T visit(StmtTy.Match.Pattern.MatchSequence aThis);

    T visit(StmtTy.Match.Pattern.MatchMapping aThis);

    T visit(StmtTy.Match.Pattern.MatchClass aThis);

    T visit(StmtTy.Match.Pattern.MatchStar aThis);

    T visit(StmtTy.Match.Pattern.MatchAs aThis);

    T visit(StmtTy.Match.Pattern.MatchOr aThis);

    T visit(StmtTy.Raise aThis);

    T visit(StmtTy.Try.ExceptHandler aThis);

    T visit(StmtTy.Try aThis);

    T visit(StmtTy.Assert aThis);

    T visit(AliasTy aThis);

    T visit(StmtTy.Import aThis);

    T visit(StmtTy.ImportFrom aThis);

    T visit(StmtTy.Global aThis);

    T visit(StmtTy.NonLocal aThis);

    T visit(StmtTy.Expr aThis);

    T visit(ModTy.TypeIgnore aThis);

    T visit(ModTy.Module aThis);

    T visit(ModTy.Interactive aThis);

    T visit(ModTy.Expression aThis);

    T visit(ModTy.FunctionType aThis);
}
