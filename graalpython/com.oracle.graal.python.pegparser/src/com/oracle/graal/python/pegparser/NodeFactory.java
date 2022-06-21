/*
 * Copyright (c) 2021, 2022, Oracle and/or its affiliates. All rights reserved.
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

import com.oracle.graal.python.pegparser.AbstractParser.NameDefaultPair;
import com.oracle.graal.python.pegparser.AbstractParser.SlashWithDefault;
import com.oracle.graal.python.pegparser.AbstractParser.StarEtc;
import com.oracle.graal.python.pegparser.sst.AliasTy;
import com.oracle.graal.python.pegparser.sst.ArgTy;
import com.oracle.graal.python.pegparser.sst.ArgumentsTy;
import com.oracle.graal.python.pegparser.sst.ComprehensionTy;
import com.oracle.graal.python.pegparser.sst.ExprTy;
import com.oracle.graal.python.pegparser.sst.KeywordTy;
import com.oracle.graal.python.pegparser.sst.ModTy;
import com.oracle.graal.python.pegparser.sst.StmtTy;
import com.oracle.graal.python.pegparser.tokenizer.SourceRange;

public interface NodeFactory {
    public StmtTy createAnnAssignment(ExprTy target, ExprTy annotation, ExprTy rhs, boolean isSimple, SourceRange sourceRange);

    public StmtTy createAssert(ExprTy test, ExprTy msg, SourceRange sourceRange);

    public StmtTy createAssignment(ExprTy[] lhs, ExprTy rhs, String typeComment, SourceRange sourceRange);

    public StmtTy createAugAssignment(ExprTy lhs, ExprTy.BinOp.Operator operation, ExprTy rhs, SourceRange sourceRange);

    public ExprTy createBinaryOp(ExprTy.BinOp.Operator op, ExprTy left, ExprTy right, SourceRange sourceRange);

    public ExprTy createComparison(ExprTy left, AbstractParser.CmpopExprPair[] rights, SourceRange sourceRange);

    public ModTy createModule(StmtTy[] statements, SourceRange sourceRange);

    public ModTy createExpressionModule(ExprTy expression, SourceRange sourceRange);

    public ModTy createInteractiveModule(StmtTy[] body, SourceRange sourceRange);

    public ExprTy createBooleanLiteral(boolean value, SourceRange sourceRange);

    public ExprTy createNone(SourceRange sourceRange);

    public ExprTy createEllipsis(SourceRange sourceRange);

    default ExprTy createGetAttribute(ExprTy receiver, String name, SourceRange sourceRange) {
        return createGetAttribute(receiver, name, null, sourceRange);
    }

    public ExprTy createGetAttribute(ExprTy receiver, String name, ExprContext context, SourceRange sourceRange);

    public StmtTy createPass(SourceRange sourceRange);

    public StmtTy createBreak(SourceRange sourceRange);

    public StmtTy createExpression(ExprTy expr);

    public ExprTy createCall(ExprTy func, ExprTy[] args, KeywordTy[] kwargs, SourceRange sourceRange);

    public StmtTy createContinue(SourceRange sourceRange);

    public StmtTy createDelete(ExprTy[] targets, SourceRange sourceRange);

    public ExprTy createYield(ExprTy value, boolean isFrom, SourceRange sourceRange);

    public ExprTy createNumber(String number, SourceRange sourceRange);

    public StmtTy createWhile(ExprTy condition, StmtTy[] block, StmtTy[] elseBlock, SourceRange sourceRange);

    public StmtTy createFor(ExprTy target, ExprTy iter, StmtTy[] block, StmtTy[] elseBlock, String typeComment, SourceRange sourceRange);

    public ExprTy createString(String[] values, SourceRange[] sourceRanges, FExprParser exprParser, ErrorCallback errorCb, PythonStringFactory<?> stringFactory);

    public ExprTy createSubscript(ExprTy receiver, ExprTy slice, ExprContext context, SourceRange sourceRange);

    public ExprTy createUnaryOp(ExprTy.UnaryOp.Operator op, ExprTy value, SourceRange sourceRange);

    default ExprTy.Name createVariable(String name, SourceRange sourceRange) {
        return createVariable(name, sourceRange, ExprContext.Load);
    }

    public ExprTy.Name createVariable(String name, SourceRange sourceRange, ExprContext context);

    public ExprTy createTuple(ExprTy[] values, ExprContext context, SourceRange sourceRange);

    public ExprTy createList(ExprTy[] values, ExprContext context, SourceRange sourceRange);

    public ExprTy createDict(ExprTy[] keys, ExprTy[] values, SourceRange sourceRange);

    public ExprTy createSet(ExprTy[] values, SourceRange sourceRange);

    public ExprTy createStarred(ExprTy value, ExprContext context, SourceRange sourceRange);

    public KeywordTy createKeyword(String arg, ExprTy value, SourceRange sourceRange);

    public ArgTy createArgument(String argument, ExprTy annotation, String typeComment, SourceRange sourceRange);

    public ArgumentsTy createArguments(ArgTy[] slashWithoutDefault, SlashWithDefault slashWithDefault, ArgTy[] paramWithoutDefault, NameDefaultPair[] paramWithDefault, StarEtc starEtc);

    public ComprehensionTy createComprehension(ExprTy target, ExprTy iter, ExprTy[] ifs, boolean isAsync, SourceRange sourceRange);

    public ExprTy createListComprehension(ExprTy name, ComprehensionTy[] generators, SourceRange sourceRange);

    public ExprTy createDictComprehension(AbstractParser.KeyValuePair name, ComprehensionTy[] generators, SourceRange sourceRange);

    public ExprTy createSetComprehension(ExprTy name, ComprehensionTy[] generators, SourceRange sourceRange);

    public ExprTy createGenerator(ExprTy name, ComprehensionTy[] generators, SourceRange sourceRange);

    public StmtTy createFunctionDef(String name, ArgumentsTy args, StmtTy[] body, ExprTy returns, String typeComment, SourceRange sourceRange);

    public StmtTy createFunctionDefWithDecorators(StmtTy funcDef, ExprTy[] decorators);

    public StmtTy createAsyncFunctionDef(String name, ArgumentsTy args, StmtTy[] body, ExprTy returns, String typeComment, SourceRange sourceRange);

    public StmtTy createReturn(ExprTy value, SourceRange sourceRange);

    public ExprTy createSlice(ExprTy start, ExprTy stop, ExprTy step, SourceRange sourceRange);

    public StmtTy createIf(ExprTy condition, StmtTy[] block, StmtTy[] orElse, SourceRange sourceRange);

    public ExprTy createIfExpression(ExprTy condition, ExprTy then, ExprTy orElse, SourceRange sourceRange);

    public ExprTy createLambda(ArgumentsTy args, ExprTy body, SourceRange sourceRange);

    public StmtTy createClassDef(ExprTy name, ExprTy call, StmtTy[] body, SourceRange sourceRange);

    public StmtTy createClassDef(StmtTy classDef, ExprTy[] decorators, SourceRange sourceRange);

    public StmtTy createNonLocal(String[] names, SourceRange sourceRange);

    public StmtTy createGlobal(String[] names, SourceRange sourceRange);

    public ExprTy createAnd(ExprTy[] values, SourceRange sourceRange);

    public ExprTy createOr(ExprTy[] values, SourceRange sourceRange);

    public StmtTy createRaise(ExprTy object, ExprTy from, SourceRange sourceRange);

    public StmtTy createImport(AliasTy[] names, SourceRange sourceRange);

    public StmtTy createImportFrom(String fromName, AliasTy[] names, int level, SourceRange sourceRange);

    public AliasTy createAlias(String name, String asName, SourceRange sourceRange);

    public StmtTy createTry(StmtTy[] body, StmtTy.Try.ExceptHandler[] handlers, StmtTy[] orElse, StmtTy[] finalBody, SourceRange sourceRange);

    public StmtTy.Try.ExceptHandler createExceptHandler(ExprTy type, String name, StmtTy[] body, SourceRange sourceRange);

    public StmtTy createWith(StmtTy.With.Item[] items, StmtTy[] body, String typeComment, SourceRange sourceRange);

    public StmtTy.With.Item createWithItem(ExprTy contextExpr, ExprTy optionalVars, SourceRange sourceRange);

    public ExprTy createAwait(ExprTy value, SourceRange sourceRange);

    public ExprTy.NamedExpr createNamedExp(ExprTy target, ExprTy value, SourceRange sourceRange);
}
