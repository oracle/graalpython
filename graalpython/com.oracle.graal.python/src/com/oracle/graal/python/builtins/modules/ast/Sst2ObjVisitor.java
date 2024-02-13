/*
 * Copyright (c) 2019, 2024, Oracle and/or its affiliates. All rights reserved.
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

// Checkstyle: stop
// JaCoCo Exclude
//@formatter:off
// Generated from Python.asdl by main_asdl_gen.py
package com.oracle.graal.python.builtins.modules.ast;

import static com.oracle.truffle.api.CompilerDirectives.shouldNotReachHere;

import com.oracle.graal.python.builtins.objects.object.PythonObject;
import com.oracle.graal.python.pegparser.sst.ModTy;
import com.oracle.graal.python.pegparser.sst.StmtTy;
import com.oracle.graal.python.pegparser.sst.ExprTy;
import com.oracle.graal.python.pegparser.sst.ExprContextTy;
import com.oracle.graal.python.pegparser.sst.BoolOpTy;
import com.oracle.graal.python.pegparser.sst.OperatorTy;
import com.oracle.graal.python.pegparser.sst.UnaryOpTy;
import com.oracle.graal.python.pegparser.sst.CmpOpTy;
import com.oracle.graal.python.pegparser.sst.ComprehensionTy;
import com.oracle.graal.python.pegparser.sst.ExceptHandlerTy;
import com.oracle.graal.python.pegparser.sst.ArgumentsTy;
import com.oracle.graal.python.pegparser.sst.ArgTy;
import com.oracle.graal.python.pegparser.sst.KeywordTy;
import com.oracle.graal.python.pegparser.sst.AliasTy;
import com.oracle.graal.python.pegparser.sst.WithItemTy;
import com.oracle.graal.python.pegparser.sst.MatchCaseTy;
import com.oracle.graal.python.pegparser.sst.PatternTy;
import com.oracle.graal.python.pegparser.sst.TypeIgnoreTy;

final class Sst2ObjVisitor extends Sst2ObjVisitorBase {

    private final AstState state;

    Sst2ObjVisitor(AstState state) {
        this.state = state;
    }

    @Override
    public Object visit(ModTy.Module node) {
        PythonObject o = factory.createPythonObject(state.clsModule);
        o.setAttribute(AstState.T_F_BODY, seq2List(node.body));
        o.setAttribute(AstState.T_F_TYPE_IGNORES, seq2List(node.typeIgnores));
        return o;
    }

    @Override
    public Object visit(ModTy.Interactive node) {
        PythonObject o = factory.createPythonObject(state.clsInteractive);
        o.setAttribute(AstState.T_F_BODY, seq2List(node.body));
        return o;
    }

    @Override
    public Object visit(ModTy.Expression node) {
        PythonObject o = factory.createPythonObject(state.clsExpression);
        o.setAttribute(AstState.T_F_BODY, visitNonNull(node.body));
        return o;
    }

    @Override
    public Object visit(ModTy.FunctionType node) {
        PythonObject o = factory.createPythonObject(state.clsFunctionType);
        o.setAttribute(AstState.T_F_ARGTYPES, seq2List(node.argTypes));
        o.setAttribute(AstState.T_F_RETURNS, visitNonNull(node.returns));
        return o;
    }

    @Override
    public Object visit(StmtTy.FunctionDef node) {
        PythonObject o = factory.createPythonObject(state.clsFunctionDef);
        o.setAttribute(AstState.T_F_NAME, visitNonNull(node.name));
        o.setAttribute(AstState.T_F_ARGS, visitNonNull(node.args));
        o.setAttribute(AstState.T_F_BODY, seq2List(node.body));
        o.setAttribute(AstState.T_F_DECORATOR_LIST, seq2List(node.decoratorList));
        o.setAttribute(AstState.T_F_RETURNS, visitNullable(node.returns));
        o.setAttribute(AstState.T_F_TYPE_COMMENT, visitNullableStringOrByteArray(node.typeComment));
        fillSourceRangeAttributes(o, node.getSourceRange());
        return o;
    }

    @Override
    public Object visit(StmtTy.AsyncFunctionDef node) {
        PythonObject o = factory.createPythonObject(state.clsAsyncFunctionDef);
        o.setAttribute(AstState.T_F_NAME, visitNonNull(node.name));
        o.setAttribute(AstState.T_F_ARGS, visitNonNull(node.args));
        o.setAttribute(AstState.T_F_BODY, seq2List(node.body));
        o.setAttribute(AstState.T_F_DECORATOR_LIST, seq2List(node.decoratorList));
        o.setAttribute(AstState.T_F_RETURNS, visitNullable(node.returns));
        o.setAttribute(AstState.T_F_TYPE_COMMENT, visitNullableStringOrByteArray(node.typeComment));
        fillSourceRangeAttributes(o, node.getSourceRange());
        return o;
    }

    @Override
    public Object visit(StmtTy.ClassDef node) {
        PythonObject o = factory.createPythonObject(state.clsClassDef);
        o.setAttribute(AstState.T_F_NAME, visitNonNull(node.name));
        o.setAttribute(AstState.T_F_BASES, seq2List(node.bases));
        o.setAttribute(AstState.T_F_KEYWORDS, seq2List(node.keywords));
        o.setAttribute(AstState.T_F_BODY, seq2List(node.body));
        o.setAttribute(AstState.T_F_DECORATOR_LIST, seq2List(node.decoratorList));
        fillSourceRangeAttributes(o, node.getSourceRange());
        return o;
    }

    @Override
    public Object visit(StmtTy.Return node) {
        PythonObject o = factory.createPythonObject(state.clsReturn);
        o.setAttribute(AstState.T_F_VALUE, visitNullable(node.value));
        fillSourceRangeAttributes(o, node.getSourceRange());
        return o;
    }

    @Override
    public Object visit(StmtTy.Delete node) {
        PythonObject o = factory.createPythonObject(state.clsDelete);
        o.setAttribute(AstState.T_F_TARGETS, seq2List(node.targets));
        fillSourceRangeAttributes(o, node.getSourceRange());
        return o;
    }

    @Override
    public Object visit(StmtTy.Assign node) {
        PythonObject o = factory.createPythonObject(state.clsAssign);
        o.setAttribute(AstState.T_F_TARGETS, seq2List(node.targets));
        o.setAttribute(AstState.T_F_VALUE, visitNonNull(node.value));
        o.setAttribute(AstState.T_F_TYPE_COMMENT, visitNullableStringOrByteArray(node.typeComment));
        fillSourceRangeAttributes(o, node.getSourceRange());
        return o;
    }

    @Override
    public Object visit(StmtTy.AugAssign node) {
        PythonObject o = factory.createPythonObject(state.clsAugAssign);
        o.setAttribute(AstState.T_F_TARGET, visitNonNull(node.target));
        o.setAttribute(AstState.T_F_OP, visitNonNull(node.op));
        o.setAttribute(AstState.T_F_VALUE, visitNonNull(node.value));
        fillSourceRangeAttributes(o, node.getSourceRange());
        return o;
    }

    @Override
    public Object visit(StmtTy.AnnAssign node) {
        PythonObject o = factory.createPythonObject(state.clsAnnAssign);
        o.setAttribute(AstState.T_F_TARGET, visitNonNull(node.target));
        o.setAttribute(AstState.T_F_ANNOTATION, visitNonNull(node.annotation));
        o.setAttribute(AstState.T_F_VALUE, visitNullable(node.value));
        o.setAttribute(AstState.T_F_SIMPLE, visitNonNull(node.isSimple));
        fillSourceRangeAttributes(o, node.getSourceRange());
        return o;
    }

    @Override
    public Object visit(StmtTy.For node) {
        PythonObject o = factory.createPythonObject(state.clsFor);
        o.setAttribute(AstState.T_F_TARGET, visitNonNull(node.target));
        o.setAttribute(AstState.T_F_ITER, visitNonNull(node.iter));
        o.setAttribute(AstState.T_F_BODY, seq2List(node.body));
        o.setAttribute(AstState.T_F_ORELSE, seq2List(node.orElse));
        o.setAttribute(AstState.T_F_TYPE_COMMENT, visitNullableStringOrByteArray(node.typeComment));
        fillSourceRangeAttributes(o, node.getSourceRange());
        return o;
    }

    @Override
    public Object visit(StmtTy.AsyncFor node) {
        PythonObject o = factory.createPythonObject(state.clsAsyncFor);
        o.setAttribute(AstState.T_F_TARGET, visitNonNull(node.target));
        o.setAttribute(AstState.T_F_ITER, visitNonNull(node.iter));
        o.setAttribute(AstState.T_F_BODY, seq2List(node.body));
        o.setAttribute(AstState.T_F_ORELSE, seq2List(node.orElse));
        o.setAttribute(AstState.T_F_TYPE_COMMENT, visitNullableStringOrByteArray(node.typeComment));
        fillSourceRangeAttributes(o, node.getSourceRange());
        return o;
    }

    @Override
    public Object visit(StmtTy.While node) {
        PythonObject o = factory.createPythonObject(state.clsWhile);
        o.setAttribute(AstState.T_F_TEST, visitNonNull(node.test));
        o.setAttribute(AstState.T_F_BODY, seq2List(node.body));
        o.setAttribute(AstState.T_F_ORELSE, seq2List(node.orElse));
        fillSourceRangeAttributes(o, node.getSourceRange());
        return o;
    }

    @Override
    public Object visit(StmtTy.If node) {
        PythonObject o = factory.createPythonObject(state.clsIf);
        o.setAttribute(AstState.T_F_TEST, visitNonNull(node.test));
        o.setAttribute(AstState.T_F_BODY, seq2List(node.body));
        o.setAttribute(AstState.T_F_ORELSE, seq2List(node.orElse));
        fillSourceRangeAttributes(o, node.getSourceRange());
        return o;
    }

    @Override
    public Object visit(StmtTy.With node) {
        PythonObject o = factory.createPythonObject(state.clsWith);
        o.setAttribute(AstState.T_F_ITEMS, seq2List(node.items));
        o.setAttribute(AstState.T_F_BODY, seq2List(node.body));
        o.setAttribute(AstState.T_F_TYPE_COMMENT, visitNullableStringOrByteArray(node.typeComment));
        fillSourceRangeAttributes(o, node.getSourceRange());
        return o;
    }

    @Override
    public Object visit(StmtTy.AsyncWith node) {
        PythonObject o = factory.createPythonObject(state.clsAsyncWith);
        o.setAttribute(AstState.T_F_ITEMS, seq2List(node.items));
        o.setAttribute(AstState.T_F_BODY, seq2List(node.body));
        o.setAttribute(AstState.T_F_TYPE_COMMENT, visitNullableStringOrByteArray(node.typeComment));
        fillSourceRangeAttributes(o, node.getSourceRange());
        return o;
    }

    @Override
    public Object visit(StmtTy.Match node) {
        PythonObject o = factory.createPythonObject(state.clsMatch);
        o.setAttribute(AstState.T_F_SUBJECT, visitNonNull(node.subject));
        o.setAttribute(AstState.T_F_CASES, seq2List(node.cases));
        fillSourceRangeAttributes(o, node.getSourceRange());
        return o;
    }

    @Override
    public Object visit(StmtTy.Raise node) {
        PythonObject o = factory.createPythonObject(state.clsRaise);
        o.setAttribute(AstState.T_F_EXC, visitNullable(node.exc));
        o.setAttribute(AstState.T_F_CAUSE, visitNullable(node.cause));
        fillSourceRangeAttributes(o, node.getSourceRange());
        return o;
    }

    @Override
    public Object visit(StmtTy.Try node) {
        PythonObject o = factory.createPythonObject(state.clsTry);
        o.setAttribute(AstState.T_F_BODY, seq2List(node.body));
        o.setAttribute(AstState.T_F_HANDLERS, seq2List(node.handlers));
        o.setAttribute(AstState.T_F_ORELSE, seq2List(node.orElse));
        o.setAttribute(AstState.T_F_FINALBODY, seq2List(node.finalBody));
        fillSourceRangeAttributes(o, node.getSourceRange());
        return o;
    }

    @Override
    public Object visit(StmtTy.TryStar node) {
        PythonObject o = factory.createPythonObject(state.clsTryStar);
        o.setAttribute(AstState.T_F_BODY, seq2List(node.body));
        o.setAttribute(AstState.T_F_HANDLERS, seq2List(node.handlers));
        o.setAttribute(AstState.T_F_ORELSE, seq2List(node.orElse));
        o.setAttribute(AstState.T_F_FINALBODY, seq2List(node.finalBody));
        fillSourceRangeAttributes(o, node.getSourceRange());
        return o;
    }

    @Override
    public Object visit(StmtTy.Assert node) {
        PythonObject o = factory.createPythonObject(state.clsAssert);
        o.setAttribute(AstState.T_F_TEST, visitNonNull(node.test));
        o.setAttribute(AstState.T_F_MSG, visitNullable(node.msg));
        fillSourceRangeAttributes(o, node.getSourceRange());
        return o;
    }

    @Override
    public Object visit(StmtTy.Import node) {
        PythonObject o = factory.createPythonObject(state.clsImport);
        o.setAttribute(AstState.T_F_NAMES, seq2List(node.names));
        fillSourceRangeAttributes(o, node.getSourceRange());
        return o;
    }

    @Override
    public Object visit(StmtTy.ImportFrom node) {
        PythonObject o = factory.createPythonObject(state.clsImportFrom);
        o.setAttribute(AstState.T_F_MODULE, visitNullable(node.module));
        o.setAttribute(AstState.T_F_NAMES, seq2List(node.names));
        o.setAttribute(AstState.T_F_LEVEL, visitNullable(node.level));
        fillSourceRangeAttributes(o, node.getSourceRange());
        return o;
    }

    @Override
    public Object visit(StmtTy.Global node) {
        PythonObject o = factory.createPythonObject(state.clsGlobal);
        o.setAttribute(AstState.T_F_NAMES, seq2List(node.names));
        fillSourceRangeAttributes(o, node.getSourceRange());
        return o;
    }

    @Override
    public Object visit(StmtTy.Nonlocal node) {
        PythonObject o = factory.createPythonObject(state.clsNonlocal);
        o.setAttribute(AstState.T_F_NAMES, seq2List(node.names));
        fillSourceRangeAttributes(o, node.getSourceRange());
        return o;
    }

    @Override
    public Object visit(StmtTy.Expr node) {
        PythonObject o = factory.createPythonObject(state.clsExpr);
        o.setAttribute(AstState.T_F_VALUE, visitNonNull(node.value));
        fillSourceRangeAttributes(o, node.getSourceRange());
        return o;
    }

    @Override
    public Object visit(StmtTy.Pass node) {
        PythonObject o = factory.createPythonObject(state.clsPass);
        fillSourceRangeAttributes(o, node.getSourceRange());
        return o;
    }

    @Override
    public Object visit(StmtTy.Break node) {
        PythonObject o = factory.createPythonObject(state.clsBreak);
        fillSourceRangeAttributes(o, node.getSourceRange());
        return o;
    }

    @Override
    public Object visit(StmtTy.Continue node) {
        PythonObject o = factory.createPythonObject(state.clsContinue);
        fillSourceRangeAttributes(o, node.getSourceRange());
        return o;
    }

    @Override
    public Object visit(ExprTy.BoolOp node) {
        PythonObject o = factory.createPythonObject(state.clsBoolOp);
        o.setAttribute(AstState.T_F_OP, visitNonNull(node.op));
        o.setAttribute(AstState.T_F_VALUES, seq2List(node.values));
        fillSourceRangeAttributes(o, node.getSourceRange());
        return o;
    }

    @Override
    public Object visit(ExprTy.NamedExpr node) {
        PythonObject o = factory.createPythonObject(state.clsNamedExpr);
        o.setAttribute(AstState.T_F_TARGET, visitNonNull(node.target));
        o.setAttribute(AstState.T_F_VALUE, visitNonNull(node.value));
        fillSourceRangeAttributes(o, node.getSourceRange());
        return o;
    }

    @Override
    public Object visit(ExprTy.BinOp node) {
        PythonObject o = factory.createPythonObject(state.clsBinOp);
        o.setAttribute(AstState.T_F_LEFT, visitNonNull(node.left));
        o.setAttribute(AstState.T_F_OP, visitNonNull(node.op));
        o.setAttribute(AstState.T_F_RIGHT, visitNonNull(node.right));
        fillSourceRangeAttributes(o, node.getSourceRange());
        return o;
    }

    @Override
    public Object visit(ExprTy.UnaryOp node) {
        PythonObject o = factory.createPythonObject(state.clsUnaryOp);
        o.setAttribute(AstState.T_F_OP, visitNonNull(node.op));
        o.setAttribute(AstState.T_F_OPERAND, visitNonNull(node.operand));
        fillSourceRangeAttributes(o, node.getSourceRange());
        return o;
    }

    @Override
    public Object visit(ExprTy.Lambda node) {
        PythonObject o = factory.createPythonObject(state.clsLambda);
        o.setAttribute(AstState.T_F_ARGS, visitNonNull(node.args));
        o.setAttribute(AstState.T_F_BODY, visitNonNull(node.body));
        fillSourceRangeAttributes(o, node.getSourceRange());
        return o;
    }

    @Override
    public Object visit(ExprTy.IfExp node) {
        PythonObject o = factory.createPythonObject(state.clsIfExp);
        o.setAttribute(AstState.T_F_TEST, visitNonNull(node.test));
        o.setAttribute(AstState.T_F_BODY, visitNonNull(node.body));
        o.setAttribute(AstState.T_F_ORELSE, visitNonNull(node.orElse));
        fillSourceRangeAttributes(o, node.getSourceRange());
        return o;
    }

    @Override
    public Object visit(ExprTy.Dict node) {
        PythonObject o = factory.createPythonObject(state.clsDict);
        o.setAttribute(AstState.T_F_KEYS, seq2List(node.keys));
        o.setAttribute(AstState.T_F_VALUES, seq2List(node.values));
        fillSourceRangeAttributes(o, node.getSourceRange());
        return o;
    }

    @Override
    public Object visit(ExprTy.Set node) {
        PythonObject o = factory.createPythonObject(state.clsSet);
        o.setAttribute(AstState.T_F_ELTS, seq2List(node.elements));
        fillSourceRangeAttributes(o, node.getSourceRange());
        return o;
    }

    @Override
    public Object visit(ExprTy.ListComp node) {
        PythonObject o = factory.createPythonObject(state.clsListComp);
        o.setAttribute(AstState.T_F_ELT, visitNonNull(node.element));
        o.setAttribute(AstState.T_F_GENERATORS, seq2List(node.generators));
        fillSourceRangeAttributes(o, node.getSourceRange());
        return o;
    }

    @Override
    public Object visit(ExprTy.SetComp node) {
        PythonObject o = factory.createPythonObject(state.clsSetComp);
        o.setAttribute(AstState.T_F_ELT, visitNonNull(node.element));
        o.setAttribute(AstState.T_F_GENERATORS, seq2List(node.generators));
        fillSourceRangeAttributes(o, node.getSourceRange());
        return o;
    }

    @Override
    public Object visit(ExprTy.DictComp node) {
        PythonObject o = factory.createPythonObject(state.clsDictComp);
        o.setAttribute(AstState.T_F_KEY, visitNonNull(node.key));
        o.setAttribute(AstState.T_F_VALUE, visitNonNull(node.value));
        o.setAttribute(AstState.T_F_GENERATORS, seq2List(node.generators));
        fillSourceRangeAttributes(o, node.getSourceRange());
        return o;
    }

    @Override
    public Object visit(ExprTy.GeneratorExp node) {
        PythonObject o = factory.createPythonObject(state.clsGeneratorExp);
        o.setAttribute(AstState.T_F_ELT, visitNonNull(node.element));
        o.setAttribute(AstState.T_F_GENERATORS, seq2List(node.generators));
        fillSourceRangeAttributes(o, node.getSourceRange());
        return o;
    }

    @Override
    public Object visit(ExprTy.Await node) {
        PythonObject o = factory.createPythonObject(state.clsAwait);
        o.setAttribute(AstState.T_F_VALUE, visitNonNull(node.value));
        fillSourceRangeAttributes(o, node.getSourceRange());
        return o;
    }

    @Override
    public Object visit(ExprTy.Yield node) {
        PythonObject o = factory.createPythonObject(state.clsYield);
        o.setAttribute(AstState.T_F_VALUE, visitNullable(node.value));
        fillSourceRangeAttributes(o, node.getSourceRange());
        return o;
    }

    @Override
    public Object visit(ExprTy.YieldFrom node) {
        PythonObject o = factory.createPythonObject(state.clsYieldFrom);
        o.setAttribute(AstState.T_F_VALUE, visitNonNull(node.value));
        fillSourceRangeAttributes(o, node.getSourceRange());
        return o;
    }

    @Override
    public Object visit(ExprTy.Compare node) {
        PythonObject o = factory.createPythonObject(state.clsCompare);
        o.setAttribute(AstState.T_F_LEFT, visitNonNull(node.left));
        o.setAttribute(AstState.T_F_OPS, seq2List(node.ops));
        o.setAttribute(AstState.T_F_COMPARATORS, seq2List(node.comparators));
        fillSourceRangeAttributes(o, node.getSourceRange());
        return o;
    }

    @Override
    public Object visit(ExprTy.Call node) {
        PythonObject o = factory.createPythonObject(state.clsCall);
        o.setAttribute(AstState.T_F_FUNC, visitNonNull(node.func));
        o.setAttribute(AstState.T_F_ARGS, seq2List(node.args));
        o.setAttribute(AstState.T_F_KEYWORDS, seq2List(node.keywords));
        fillSourceRangeAttributes(o, node.getSourceRange());
        return o;
    }

    @Override
    public Object visit(ExprTy.FormattedValue node) {
        PythonObject o = factory.createPythonObject(state.clsFormattedValue);
        o.setAttribute(AstState.T_F_VALUE, visitNonNull(node.value));
        o.setAttribute(AstState.T_F_CONVERSION, visitNonNull(node.conversion));
        o.setAttribute(AstState.T_F_FORMAT_SPEC, visitNullable(node.formatSpec));
        fillSourceRangeAttributes(o, node.getSourceRange());
        return o;
    }

    @Override
    public Object visit(ExprTy.JoinedStr node) {
        PythonObject o = factory.createPythonObject(state.clsJoinedStr);
        o.setAttribute(AstState.T_F_VALUES, seq2List(node.values));
        fillSourceRangeAttributes(o, node.getSourceRange());
        return o;
    }

    @Override
    public Object visit(ExprTy.Constant node) {
        PythonObject o = factory.createPythonObject(state.clsConstant);
        o.setAttribute(AstState.T_F_VALUE, visitNonNull(node.value));
        o.setAttribute(AstState.T_F_KIND, visitNullableStringOrByteArray(node.kind));
        fillSourceRangeAttributes(o, node.getSourceRange());
        return o;
    }

    @Override
    public Object visit(ExprTy.Attribute node) {
        PythonObject o = factory.createPythonObject(state.clsAttribute);
        o.setAttribute(AstState.T_F_VALUE, visitNonNull(node.value));
        o.setAttribute(AstState.T_F_ATTR, visitNonNull(node.attr));
        o.setAttribute(AstState.T_F_CTX, visitNonNull(node.context));
        fillSourceRangeAttributes(o, node.getSourceRange());
        return o;
    }

    @Override
    public Object visit(ExprTy.Subscript node) {
        PythonObject o = factory.createPythonObject(state.clsSubscript);
        o.setAttribute(AstState.T_F_VALUE, visitNonNull(node.value));
        o.setAttribute(AstState.T_F_SLICE, visitNonNull(node.slice));
        o.setAttribute(AstState.T_F_CTX, visitNonNull(node.context));
        fillSourceRangeAttributes(o, node.getSourceRange());
        return o;
    }

    @Override
    public Object visit(ExprTy.Starred node) {
        PythonObject o = factory.createPythonObject(state.clsStarred);
        o.setAttribute(AstState.T_F_VALUE, visitNonNull(node.value));
        o.setAttribute(AstState.T_F_CTX, visitNonNull(node.context));
        fillSourceRangeAttributes(o, node.getSourceRange());
        return o;
    }

    @Override
    public Object visit(ExprTy.Name node) {
        PythonObject o = factory.createPythonObject(state.clsName);
        o.setAttribute(AstState.T_F_ID, visitNonNull(node.id));
        o.setAttribute(AstState.T_F_CTX, visitNonNull(node.context));
        fillSourceRangeAttributes(o, node.getSourceRange());
        return o;
    }

    @Override
    public Object visit(ExprTy.List node) {
        PythonObject o = factory.createPythonObject(state.clsList);
        o.setAttribute(AstState.T_F_ELTS, seq2List(node.elements));
        o.setAttribute(AstState.T_F_CTX, visitNonNull(node.context));
        fillSourceRangeAttributes(o, node.getSourceRange());
        return o;
    }

    @Override
    public Object visit(ExprTy.Tuple node) {
        PythonObject o = factory.createPythonObject(state.clsTuple);
        o.setAttribute(AstState.T_F_ELTS, seq2List(node.elements));
        o.setAttribute(AstState.T_F_CTX, visitNonNull(node.context));
        fillSourceRangeAttributes(o, node.getSourceRange());
        return o;
    }

    @Override
    public Object visit(ExprTy.Slice node) {
        PythonObject o = factory.createPythonObject(state.clsSlice);
        o.setAttribute(AstState.T_F_LOWER, visitNullable(node.lower));
        o.setAttribute(AstState.T_F_UPPER, visitNullable(node.upper));
        o.setAttribute(AstState.T_F_STEP, visitNullable(node.step));
        fillSourceRangeAttributes(o, node.getSourceRange());
        return o;
    }

    public Object visitNonNull(ExprContextTy v) {
        switch (v) {
            case Load:
                return state.singletonLoad;
            case Store:
                return state.singletonStore;
            case Del:
                return state.singletonDel;
            default:
                throw shouldNotReachHere();
        }
    }

    public Object visitNonNull(BoolOpTy v) {
        switch (v) {
            case And:
                return state.singletonAnd;
            case Or:
                return state.singletonOr;
            default:
                throw shouldNotReachHere();
        }
    }

    public Object visitNonNull(OperatorTy v) {
        switch (v) {
            case Add:
                return state.singletonAdd;
            case Sub:
                return state.singletonSub;
            case Mult:
                return state.singletonMult;
            case MatMult:
                return state.singletonMatMult;
            case Div:
                return state.singletonDiv;
            case Mod:
                return state.singletonMod;
            case Pow:
                return state.singletonPow;
            case LShift:
                return state.singletonLShift;
            case RShift:
                return state.singletonRShift;
            case BitOr:
                return state.singletonBitOr;
            case BitXor:
                return state.singletonBitXor;
            case BitAnd:
                return state.singletonBitAnd;
            case FloorDiv:
                return state.singletonFloorDiv;
            default:
                throw shouldNotReachHere();
        }
    }

    public Object visitNonNull(UnaryOpTy v) {
        switch (v) {
            case Invert:
                return state.singletonInvert;
            case Not:
                return state.singletonNot;
            case UAdd:
                return state.singletonUAdd;
            case USub:
                return state.singletonUSub;
            default:
                throw shouldNotReachHere();
        }
    }

    @Override
    public Object visitNonNull(CmpOpTy v) {
        switch (v) {
            case Eq:
                return state.singletonEq;
            case NotEq:
                return state.singletonNotEq;
            case Lt:
                return state.singletonLt;
            case LtE:
                return state.singletonLtE;
            case Gt:
                return state.singletonGt;
            case GtE:
                return state.singletonGtE;
            case Is:
                return state.singletonIs;
            case IsNot:
                return state.singletonIsNot;
            case In:
                return state.singletonIn;
            case NotIn:
                return state.singletonNotIn;
            default:
                throw shouldNotReachHere();
        }
    }

    @Override
    public Object visit(ComprehensionTy node) {
        PythonObject o = factory.createPythonObject(state.clsComprehensionTy);
        o.setAttribute(AstState.T_F_TARGET, visitNonNull(node.target));
        o.setAttribute(AstState.T_F_ITER, visitNonNull(node.iter));
        o.setAttribute(AstState.T_F_IFS, seq2List(node.ifs));
        o.setAttribute(AstState.T_F_IS_ASYNC, visitNonNull(node.isAsync));
        return o;
    }

    @Override
    public Object visit(ExceptHandlerTy.ExceptHandler node) {
        PythonObject o = factory.createPythonObject(state.clsExceptHandler);
        o.setAttribute(AstState.T_F_TYPE, visitNullable(node.type));
        o.setAttribute(AstState.T_F_NAME, visitNullable(node.name));
        o.setAttribute(AstState.T_F_BODY, seq2List(node.body));
        fillSourceRangeAttributes(o, node.getSourceRange());
        return o;
    }

    @Override
    public Object visit(ArgumentsTy node) {
        PythonObject o = factory.createPythonObject(state.clsArgumentsTy);
        o.setAttribute(AstState.T_F_POSONLYARGS, seq2List(node.posOnlyArgs));
        o.setAttribute(AstState.T_F_ARGS, seq2List(node.args));
        o.setAttribute(AstState.T_F_VARARG, visitNullable(node.varArg));
        o.setAttribute(AstState.T_F_KWONLYARGS, seq2List(node.kwOnlyArgs));
        o.setAttribute(AstState.T_F_KW_DEFAULTS, seq2List(node.kwDefaults));
        o.setAttribute(AstState.T_F_KWARG, visitNullable(node.kwArg));
        o.setAttribute(AstState.T_F_DEFAULTS, seq2List(node.defaults));
        return o;
    }

    @Override
    public Object visit(ArgTy node) {
        PythonObject o = factory.createPythonObject(state.clsArgTy);
        o.setAttribute(AstState.T_F_ARG, visitNonNull(node.arg));
        o.setAttribute(AstState.T_F_ANNOTATION, visitNullable(node.annotation));
        o.setAttribute(AstState.T_F_TYPE_COMMENT, visitNullableStringOrByteArray(node.typeComment));
        fillSourceRangeAttributes(o, node.getSourceRange());
        return o;
    }

    @Override
    public Object visit(KeywordTy node) {
        PythonObject o = factory.createPythonObject(state.clsKeywordTy);
        o.setAttribute(AstState.T_F_ARG, visitNullable(node.arg));
        o.setAttribute(AstState.T_F_VALUE, visitNonNull(node.value));
        fillSourceRangeAttributes(o, node.getSourceRange());
        return o;
    }

    @Override
    public Object visit(AliasTy node) {
        PythonObject o = factory.createPythonObject(state.clsAliasTy);
        o.setAttribute(AstState.T_F_NAME, visitNonNull(node.name));
        o.setAttribute(AstState.T_F_ASNAME, visitNullable(node.asName));
        fillSourceRangeAttributes(o, node.getSourceRange());
        return o;
    }

    @Override
    public Object visit(WithItemTy node) {
        PythonObject o = factory.createPythonObject(state.clsWithItemTy);
        o.setAttribute(AstState.T_F_CONTEXT_EXPR, visitNonNull(node.contextExpr));
        o.setAttribute(AstState.T_F_OPTIONAL_VARS, visitNullable(node.optionalVars));
        return o;
    }

    @Override
    public Object visit(MatchCaseTy node) {
        PythonObject o = factory.createPythonObject(state.clsMatchCaseTy);
        o.setAttribute(AstState.T_F_PATTERN, visitNonNull(node.pattern));
        o.setAttribute(AstState.T_F_GUARD, visitNullable(node.guard));
        o.setAttribute(AstState.T_F_BODY, seq2List(node.body));
        return o;
    }

    @Override
    public Object visit(PatternTy.MatchValue node) {
        PythonObject o = factory.createPythonObject(state.clsMatchValue);
        o.setAttribute(AstState.T_F_VALUE, visitNonNull(node.value));
        fillSourceRangeAttributes(o, node.getSourceRange());
        return o;
    }

    @Override
    public Object visit(PatternTy.MatchSingleton node) {
        PythonObject o = factory.createPythonObject(state.clsMatchSingleton);
        o.setAttribute(AstState.T_F_VALUE, visitNonNull(node.value));
        fillSourceRangeAttributes(o, node.getSourceRange());
        return o;
    }

    @Override
    public Object visit(PatternTy.MatchSequence node) {
        PythonObject o = factory.createPythonObject(state.clsMatchSequence);
        o.setAttribute(AstState.T_F_PATTERNS, seq2List(node.patterns));
        fillSourceRangeAttributes(o, node.getSourceRange());
        return o;
    }

    @Override
    public Object visit(PatternTy.MatchMapping node) {
        PythonObject o = factory.createPythonObject(state.clsMatchMapping);
        o.setAttribute(AstState.T_F_KEYS, seq2List(node.keys));
        o.setAttribute(AstState.T_F_PATTERNS, seq2List(node.patterns));
        o.setAttribute(AstState.T_F_REST, visitNullable(node.rest));
        fillSourceRangeAttributes(o, node.getSourceRange());
        return o;
    }

    @Override
    public Object visit(PatternTy.MatchClass node) {
        PythonObject o = factory.createPythonObject(state.clsMatchClass);
        o.setAttribute(AstState.T_F_CLS, visitNonNull(node.cls));
        o.setAttribute(AstState.T_F_PATTERNS, seq2List(node.patterns));
        o.setAttribute(AstState.T_F_KWD_ATTRS, seq2List(node.kwdAttrs));
        o.setAttribute(AstState.T_F_KWD_PATTERNS, seq2List(node.kwdPatterns));
        fillSourceRangeAttributes(o, node.getSourceRange());
        return o;
    }

    @Override
    public Object visit(PatternTy.MatchStar node) {
        PythonObject o = factory.createPythonObject(state.clsMatchStar);
        o.setAttribute(AstState.T_F_NAME, visitNullable(node.name));
        fillSourceRangeAttributes(o, node.getSourceRange());
        return o;
    }

    @Override
    public Object visit(PatternTy.MatchAs node) {
        PythonObject o = factory.createPythonObject(state.clsMatchAs);
        o.setAttribute(AstState.T_F_PATTERN, visitNullable(node.pattern));
        o.setAttribute(AstState.T_F_NAME, visitNullable(node.name));
        fillSourceRangeAttributes(o, node.getSourceRange());
        return o;
    }

    @Override
    public Object visit(PatternTy.MatchOr node) {
        PythonObject o = factory.createPythonObject(state.clsMatchOr);
        o.setAttribute(AstState.T_F_PATTERNS, seq2List(node.patterns));
        fillSourceRangeAttributes(o, node.getSourceRange());
        return o;
    }

    @Override
    public Object visit(TypeIgnoreTy.TypeIgnore node) {
        PythonObject o = factory.createPythonObject(state.clsTypeIgnore);
        o.setAttribute(AstState.T_F_LINENO, visitNonNull(node.lineNo));
        o.setAttribute(AstState.T_F_TAG, visitNonNullStringOrByteArray(node.tag));
        return o;
    }
}
