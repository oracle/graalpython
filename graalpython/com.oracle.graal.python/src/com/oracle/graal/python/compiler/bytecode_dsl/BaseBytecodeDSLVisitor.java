/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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
import com.oracle.graal.python.pegparser.sst.WithItemTy;

/**
 * This interface provides default implementations of all {@code SSTreeVisitor} methods, which makes
 * it easier to incrementally add support to the Bytecode DSL compiler. Once the compiler is stable,
 * this interface should be removed.
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
        visitNode(node.iter);
        visitSequence(node.ifs);
        visitNode(node.target);
        return defaultValue(node);
    }

    default T visit(ExprTy.Attribute node) {
        visitNode(node.value);
        return defaultValue(node);
    }

    default T visit(ExprTy.Await node) {
        visitNode(node.value);
        return defaultValue(node);
    }

    default T visit(ExprTy.BinOp node) {
        visitNode(node.left);
        visitNode(node.right);
        return defaultValue(node);
    }

    default T visit(ExprTy.BoolOp node) {
        visitSequence(node.values);
        return defaultValue(node);
    }

    default T visit(ExprTy.Call node) {
        visitNode(node.func);
        visitSequence(node.args);
        visitSequence(node.keywords);
        return defaultValue(node);
    }

    default T visit(ExprTy.Compare node) {
        visitNode(node.left);
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
        visitNode(node.key);
        visitNode(node.value);
        return defaultValue(node);
    }

    default T visit(ExprTy.FormattedValue node) {
        visitNode(node.formatSpec);
        visitNode(node.value);
        return defaultValue(node);
    }

    default T visit(ExprTy.GeneratorExp node) {
        visitNode(node.element);
        visitSequence(node.generators);
        return defaultValue(node);
    }

    default T visit(ExprTy.IfExp node) {
        visitNode(node.test);
        visitNode(node.body);
        visitNode(node.orElse);
        return defaultValue(node);
    }

    default T visit(ExprTy.JoinedStr node) {
        visitSequence(node.values);
        return defaultValue(node);
    }

    default T visit(ExprTy.Lambda node) {
        visitNode(node.body);
        return defaultValue(node);
    }

    default T visit(ExprTy.List node) {
        visitSequence(node.elements);
        return defaultValue(node);
    }

    default T visit(ExprTy.ListComp node) {
        visitSequence(node.generators);
        visitNode(node.element);
        return defaultValue(node);
    }

    default T visit(ExprTy.Name node) {
        return defaultValue(node);
    }

    default T visit(ExprTy.NamedExpr node) {
        visitNode(node.target);
        visitNode(node.value);
        return defaultValue(node);
    }

    default T visit(ExprTy.Set node) {
        visitSequence(node.elements);
        return defaultValue(node);
    }

    default T visit(ExprTy.SetComp node) {
        visitSequence(node.generators);
        visitNode(node.element);
        return defaultValue(node);
    }

    default T visit(ExprTy.Slice node) {
        visitNode(node.lower);
        visitNode(node.upper);
        visitNode(node.step);
        return defaultValue(node);
    }

    default T visit(ExprTy.Starred node) {
        visitNode(node.value);
        return defaultValue(node);
    }

    default T visit(ExprTy.Subscript node) {
        visitNode(node.value);
        visitNode(node.slice);
        return defaultValue(node);
    }

    default T visit(ExprTy.Tuple node) {
        visitSequence(node.elements);
        return defaultValue(node);
    }

    default T visit(ExprTy.UnaryOp node) {
        visitNode(node.operand);
        return defaultValue(node);
    }

    default T visit(ExprTy.Yield node) {
        visitNode(node.value);
        return defaultValue(node);
    }

    default T visit(ExprTy.YieldFrom node) {
        visitNode(node.value);
        return defaultValue(node);
    }

    default T visit(KeywordTy node) {
        visitNode(node.value);
        return defaultValue(node);
    }

    default T visit(ModTy.Expression node) {
        visitNode(node.body);
        return defaultValue(node);
    }

    default T visit(ModTy.FunctionType node) {
        visitNode(node.returns);
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
        visitNode(node.target);
        visitNode(node.annotation);
        visitNode(node.value);
        return defaultValue(node);
    }

    default T visit(StmtTy.Assert node) {
        visitNode(node.test);
        visitNode(node.msg);
        return defaultValue(node);
    }

    default T visit(StmtTy.Assign node) {
        visitNode(node.value);
        visitSequence(node.targets);
        return defaultValue(node);
    }

    default T visit(StmtTy.AsyncFor node) {
        visitNode(node.target);
        visitNode(node.iter);
        visitSequence(node.body);
        visitSequence(node.orElse);
        return defaultValue(node);
    }

    default T visit(StmtTy.AsyncFunctionDef node) {
        visitSequence(node.decoratorList);
        visitNode(node.args);
        visitNode(node.returns);
        visitSequence(node.body);
        return defaultValue(node);
    }

    default T visit(StmtTy.AsyncWith node) {
        visitSequence(node.items);
        visitSequence(node.body);
        return defaultValue(node);
    }

    default T visit(StmtTy.AugAssign node) {
        visitNode(node.target);
        visitNode(node.value);
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
        visitNode(node.value);
        return defaultValue(node);
    }

    default T visit(StmtTy.For node) {
        visitNode(node.iter);
        visitNode(node.target);
        visitSequence(node.body);
        visitSequence(node.orElse);
        return defaultValue(node);
    }

    default T visit(StmtTy.FunctionDef node) {
        visitSequence(node.decoratorList);
        visitNode(node.args);
        visitNode(node.returns);
        visitSequence(node.body);
        return defaultValue(node);
    }

    default T visit(StmtTy.Global node) {
        return defaultValue(node);
    }

    default T visit(StmtTy.If node) {
        visitNode(node.test);
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
        visitNode(node.subject);
        visitSequence(node.cases);
        return defaultValue(node);
    }

    default T visit(MatchCaseTy node) {
        visitNode(node.pattern);
        visitNode(node.guard);
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
        visitNode(node.cls);
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
        visitNode(node.value);
        return defaultValue(node);
    }

    default T visit(StmtTy.Nonlocal node) {
        return defaultValue(node);
    }

    default T visit(StmtTy.Raise node) {
        visitNode(node.exc);
        visitNode(node.cause);
        return defaultValue(node);
    }

    default T visit(StmtTy.Return node) {
        visitNode(node.value);
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
        visitNode(node.type);
        visitSequence(node.body);
        return defaultValue(node);
    }

    default T visit(StmtTy.While node) {
        visitNode(node.test);
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
        visitNode(node.contextExpr);
        visitNode(node.optionalVars);
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
}
