/*
 * Copyright (c) 2019, 2022, Oracle and/or its affiliates. All rights reserved.
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

import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.pegparser.sst.ConstantValue;
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
import com.oracle.graal.python.pegparser.tokenizer.SourceRange;

final class Obj2Sst extends Obj2SstBase {

    Obj2Sst(AstState state) {
        super(state);
    }

    ModTy obj2ModTy(Object obj) {
        if (obj == PNone.NONE) {
            return null;
        }
        if (isInstanceOf(obj, state.clsModule)) {
            return obj2Module(obj);
        }
        if (isInstanceOf(obj, state.clsInteractive)) {
            return obj2Interactive(obj);
        }
        if (isInstanceOf(obj, state.clsExpression)) {
            return obj2Expression(obj);
        }
        if (isInstanceOf(obj, state.clsFunctionType)) {
            return obj2FunctionType(obj);
        }
        throw unexpectedNodeType(AstState.T_T_MOD, obj);
    }

    ModTy.Module obj2Module(Object obj) {
        StmtTy[] body = lookupAndConvertSequence(obj, AstState.T_F_BODY, AstState.T_C_MODULE, this::obj2StmtTy, StmtTy[]::new);
        TypeIgnoreTy[] typeIgnores = lookupAndConvertSequence(obj, AstState.T_F_TYPE_IGNORES, AstState.T_C_MODULE, this::obj2TypeIgnoreTy, TypeIgnoreTy[]::new);
        SourceRange sourceRange = SourceRange.ARTIFICIAL_RANGE;
        return new ModTy.Module(body, typeIgnores, sourceRange);
    }

    ModTy.Interactive obj2Interactive(Object obj) {
        StmtTy[] body = lookupAndConvertSequence(obj, AstState.T_F_BODY, AstState.T_C_INTERACTIVE, this::obj2StmtTy, StmtTy[]::new);
        SourceRange sourceRange = SourceRange.ARTIFICIAL_RANGE;
        return new ModTy.Interactive(body, sourceRange);
    }

    ModTy.Expression obj2Expression(Object obj) {
        ExprTy body = lookupAndConvert(obj, AstState.T_F_BODY, AstState.T_C_EXPRESSION, this::obj2ExprTy, true);
        SourceRange sourceRange = SourceRange.ARTIFICIAL_RANGE;
        return new ModTy.Expression(body, sourceRange);
    }

    ModTy.FunctionType obj2FunctionType(Object obj) {
        ExprTy[] argTypes = lookupAndConvertSequence(obj, AstState.T_F_ARGTYPES, AstState.T_C_FUNCTIONTYPE, this::obj2ExprTy, ExprTy[]::new);
        ExprTy returns = lookupAndConvert(obj, AstState.T_F_RETURNS, AstState.T_C_FUNCTIONTYPE, this::obj2ExprTy, true);
        SourceRange sourceRange = SourceRange.ARTIFICIAL_RANGE;
        return new ModTy.FunctionType(argTypes, returns, sourceRange);
    }

    StmtTy obj2StmtTy(Object obj) {
        if (obj == PNone.NONE) {
            return null;
        }
        int lineNo = lookupAndConvertInt(obj, AstState.T_F_LINENO, AstState.T_T_STMT, true);
        int colOffset = lookupAndConvertInt(obj, AstState.T_F_COL_OFFSET, AstState.T_T_STMT, true);
        int endLineno = lookupAndConvertInt(obj, AstState.T_F_END_LINENO, AstState.T_T_STMT, false);
        int endColOffset = lookupAndConvertInt(obj, AstState.T_F_END_COL_OFFSET, AstState.T_T_STMT, false);
        SourceRange sourceRange = new SourceRange(lineNo, colOffset, endLineno, endColOffset);
        if (isInstanceOf(obj, state.clsFunctionDef)) {
            return obj2FunctionDef(obj, sourceRange);
        }
        if (isInstanceOf(obj, state.clsAsyncFunctionDef)) {
            return obj2AsyncFunctionDef(obj, sourceRange);
        }
        if (isInstanceOf(obj, state.clsClassDef)) {
            return obj2ClassDef(obj, sourceRange);
        }
        if (isInstanceOf(obj, state.clsReturn)) {
            return obj2Return(obj, sourceRange);
        }
        if (isInstanceOf(obj, state.clsDelete)) {
            return obj2Delete(obj, sourceRange);
        }
        if (isInstanceOf(obj, state.clsAssign)) {
            return obj2Assign(obj, sourceRange);
        }
        if (isInstanceOf(obj, state.clsAugAssign)) {
            return obj2AugAssign(obj, sourceRange);
        }
        if (isInstanceOf(obj, state.clsAnnAssign)) {
            return obj2AnnAssign(obj, sourceRange);
        }
        if (isInstanceOf(obj, state.clsFor)) {
            return obj2For(obj, sourceRange);
        }
        if (isInstanceOf(obj, state.clsAsyncFor)) {
            return obj2AsyncFor(obj, sourceRange);
        }
        if (isInstanceOf(obj, state.clsWhile)) {
            return obj2While(obj, sourceRange);
        }
        if (isInstanceOf(obj, state.clsIf)) {
            return obj2If(obj, sourceRange);
        }
        if (isInstanceOf(obj, state.clsWith)) {
            return obj2With(obj, sourceRange);
        }
        if (isInstanceOf(obj, state.clsAsyncWith)) {
            return obj2AsyncWith(obj, sourceRange);
        }
        if (isInstanceOf(obj, state.clsMatch)) {
            return obj2Match(obj, sourceRange);
        }
        if (isInstanceOf(obj, state.clsRaise)) {
            return obj2Raise(obj, sourceRange);
        }
        if (isInstanceOf(obj, state.clsTry)) {
            return obj2Try(obj, sourceRange);
        }
        if (isInstanceOf(obj, state.clsTryStar)) {
            return obj2TryStar(obj, sourceRange);
        }
        if (isInstanceOf(obj, state.clsAssert)) {
            return obj2Assert(obj, sourceRange);
        }
        if (isInstanceOf(obj, state.clsImport)) {
            return obj2Import(obj, sourceRange);
        }
        if (isInstanceOf(obj, state.clsImportFrom)) {
            return obj2ImportFrom(obj, sourceRange);
        }
        if (isInstanceOf(obj, state.clsGlobal)) {
            return obj2Global(obj, sourceRange);
        }
        if (isInstanceOf(obj, state.clsNonlocal)) {
            return obj2Nonlocal(obj, sourceRange);
        }
        if (isInstanceOf(obj, state.clsExpr)) {
            return obj2Expr(obj, sourceRange);
        }
        if (isInstanceOf(obj, state.clsPass)) {
            return obj2Pass(obj, sourceRange);
        }
        if (isInstanceOf(obj, state.clsBreak)) {
            return obj2Break(obj, sourceRange);
        }
        if (isInstanceOf(obj, state.clsContinue)) {
            return obj2Continue(obj, sourceRange);
        }
        throw unexpectedNodeType(AstState.T_T_STMT, obj);
    }

    StmtTy.FunctionDef obj2FunctionDef(Object obj, SourceRange sourceRange) {
        String name = lookupAndConvert(obj, AstState.T_F_NAME, AstState.T_C_FUNCTIONDEF, this::obj2identifier, true);
        ArgumentsTy args = lookupAndConvert(obj, AstState.T_F_ARGS, AstState.T_C_FUNCTIONDEF, this::obj2ArgumentsTy, true);
        StmtTy[] body = lookupAndConvertSequence(obj, AstState.T_F_BODY, AstState.T_C_FUNCTIONDEF, this::obj2StmtTy, StmtTy[]::new);
        ExprTy[] decoratorList = lookupAndConvertSequence(obj, AstState.T_F_DECORATOR_LIST, AstState.T_C_FUNCTIONDEF, this::obj2ExprTy, ExprTy[]::new);
        ExprTy returns = lookupAndConvert(obj, AstState.T_F_RETURNS, AstState.T_C_FUNCTIONDEF, this::obj2ExprTy, false);
        Object typeComment = lookupAndConvert(obj, AstState.T_F_TYPE_COMMENT, AstState.T_C_FUNCTIONDEF, this::obj2string, false);
        return new StmtTy.FunctionDef(name, args, body, decoratorList, returns, typeComment, sourceRange);
    }

    StmtTy.AsyncFunctionDef obj2AsyncFunctionDef(Object obj, SourceRange sourceRange) {
        String name = lookupAndConvert(obj, AstState.T_F_NAME, AstState.T_C_ASYNCFUNCTIONDEF, this::obj2identifier, true);
        ArgumentsTy args = lookupAndConvert(obj, AstState.T_F_ARGS, AstState.T_C_ASYNCFUNCTIONDEF, this::obj2ArgumentsTy, true);
        StmtTy[] body = lookupAndConvertSequence(obj, AstState.T_F_BODY, AstState.T_C_ASYNCFUNCTIONDEF, this::obj2StmtTy, StmtTy[]::new);
        ExprTy[] decoratorList = lookupAndConvertSequence(obj, AstState.T_F_DECORATOR_LIST, AstState.T_C_ASYNCFUNCTIONDEF, this::obj2ExprTy, ExprTy[]::new);
        ExprTy returns = lookupAndConvert(obj, AstState.T_F_RETURNS, AstState.T_C_ASYNCFUNCTIONDEF, this::obj2ExprTy, false);
        Object typeComment = lookupAndConvert(obj, AstState.T_F_TYPE_COMMENT, AstState.T_C_ASYNCFUNCTIONDEF, this::obj2string, false);
        return new StmtTy.AsyncFunctionDef(name, args, body, decoratorList, returns, typeComment, sourceRange);
    }

    StmtTy.ClassDef obj2ClassDef(Object obj, SourceRange sourceRange) {
        String name = lookupAndConvert(obj, AstState.T_F_NAME, AstState.T_C_CLASSDEF, this::obj2identifier, true);
        ExprTy[] bases = lookupAndConvertSequence(obj, AstState.T_F_BASES, AstState.T_C_CLASSDEF, this::obj2ExprTy, ExprTy[]::new);
        KeywordTy[] keywords = lookupAndConvertSequence(obj, AstState.T_F_KEYWORDS, AstState.T_C_CLASSDEF, this::obj2KeywordTy, KeywordTy[]::new);
        StmtTy[] body = lookupAndConvertSequence(obj, AstState.T_F_BODY, AstState.T_C_CLASSDEF, this::obj2StmtTy, StmtTy[]::new);
        ExprTy[] decoratorList = lookupAndConvertSequence(obj, AstState.T_F_DECORATOR_LIST, AstState.T_C_CLASSDEF, this::obj2ExprTy, ExprTy[]::new);
        return new StmtTy.ClassDef(name, bases, keywords, body, decoratorList, sourceRange);
    }

    StmtTy.Return obj2Return(Object obj, SourceRange sourceRange) {
        ExprTy value = lookupAndConvert(obj, AstState.T_F_VALUE, AstState.T_C_RETURN, this::obj2ExprTy, false);
        return new StmtTy.Return(value, sourceRange);
    }

    StmtTy.Delete obj2Delete(Object obj, SourceRange sourceRange) {
        ExprTy[] targets = lookupAndConvertSequence(obj, AstState.T_F_TARGETS, AstState.T_C_DELETE, this::obj2ExprTy, ExprTy[]::new);
        return new StmtTy.Delete(targets, sourceRange);
    }

    StmtTy.Assign obj2Assign(Object obj, SourceRange sourceRange) {
        ExprTy[] targets = lookupAndConvertSequence(obj, AstState.T_F_TARGETS, AstState.T_C_ASSIGN, this::obj2ExprTy, ExprTy[]::new);
        ExprTy value = lookupAndConvert(obj, AstState.T_F_VALUE, AstState.T_C_ASSIGN, this::obj2ExprTy, true);
        Object typeComment = lookupAndConvert(obj, AstState.T_F_TYPE_COMMENT, AstState.T_C_ASSIGN, this::obj2string, false);
        return new StmtTy.Assign(targets, value, typeComment, sourceRange);
    }

    StmtTy.AugAssign obj2AugAssign(Object obj, SourceRange sourceRange) {
        ExprTy target = lookupAndConvert(obj, AstState.T_F_TARGET, AstState.T_C_AUGASSIGN, this::obj2ExprTy, true);
        OperatorTy op = lookupAndConvert(obj, AstState.T_F_OP, AstState.T_C_AUGASSIGN, this::obj2OperatorTy, true);
        ExprTy value = lookupAndConvert(obj, AstState.T_F_VALUE, AstState.T_C_AUGASSIGN, this::obj2ExprTy, true);
        return new StmtTy.AugAssign(target, op, value, sourceRange);
    }

    StmtTy.AnnAssign obj2AnnAssign(Object obj, SourceRange sourceRange) {
        ExprTy target = lookupAndConvert(obj, AstState.T_F_TARGET, AstState.T_C_ANNASSIGN, this::obj2ExprTy, true);
        ExprTy annotation = lookupAndConvert(obj, AstState.T_F_ANNOTATION, AstState.T_C_ANNASSIGN, this::obj2ExprTy, true);
        ExprTy value = lookupAndConvert(obj, AstState.T_F_VALUE, AstState.T_C_ANNASSIGN, this::obj2ExprTy, false);
        boolean isSimple = lookupAndConvertBoolean(obj, AstState.T_F_SIMPLE, AstState.T_C_ANNASSIGN, true);
        return new StmtTy.AnnAssign(target, annotation, value, isSimple, sourceRange);
    }

    StmtTy.For obj2For(Object obj, SourceRange sourceRange) {
        ExprTy target = lookupAndConvert(obj, AstState.T_F_TARGET, AstState.T_C_FOR, this::obj2ExprTy, true);
        ExprTy iter = lookupAndConvert(obj, AstState.T_F_ITER, AstState.T_C_FOR, this::obj2ExprTy, true);
        StmtTy[] body = lookupAndConvertSequence(obj, AstState.T_F_BODY, AstState.T_C_FOR, this::obj2StmtTy, StmtTy[]::new);
        StmtTy[] orElse = lookupAndConvertSequence(obj, AstState.T_F_ORELSE, AstState.T_C_FOR, this::obj2StmtTy, StmtTy[]::new);
        Object typeComment = lookupAndConvert(obj, AstState.T_F_TYPE_COMMENT, AstState.T_C_FOR, this::obj2string, false);
        return new StmtTy.For(target, iter, body, orElse, typeComment, sourceRange);
    }

    StmtTy.AsyncFor obj2AsyncFor(Object obj, SourceRange sourceRange) {
        ExprTy target = lookupAndConvert(obj, AstState.T_F_TARGET, AstState.T_C_ASYNCFOR, this::obj2ExprTy, true);
        ExprTy iter = lookupAndConvert(obj, AstState.T_F_ITER, AstState.T_C_ASYNCFOR, this::obj2ExprTy, true);
        StmtTy[] body = lookupAndConvertSequence(obj, AstState.T_F_BODY, AstState.T_C_ASYNCFOR, this::obj2StmtTy, StmtTy[]::new);
        StmtTy[] orElse = lookupAndConvertSequence(obj, AstState.T_F_ORELSE, AstState.T_C_ASYNCFOR, this::obj2StmtTy, StmtTy[]::new);
        Object typeComment = lookupAndConvert(obj, AstState.T_F_TYPE_COMMENT, AstState.T_C_ASYNCFOR, this::obj2string, false);
        return new StmtTy.AsyncFor(target, iter, body, orElse, typeComment, sourceRange);
    }

    StmtTy.While obj2While(Object obj, SourceRange sourceRange) {
        ExprTy test = lookupAndConvert(obj, AstState.T_F_TEST, AstState.T_C_WHILE, this::obj2ExprTy, true);
        StmtTy[] body = lookupAndConvertSequence(obj, AstState.T_F_BODY, AstState.T_C_WHILE, this::obj2StmtTy, StmtTy[]::new);
        StmtTy[] orElse = lookupAndConvertSequence(obj, AstState.T_F_ORELSE, AstState.T_C_WHILE, this::obj2StmtTy, StmtTy[]::new);
        return new StmtTy.While(test, body, orElse, sourceRange);
    }

    StmtTy.If obj2If(Object obj, SourceRange sourceRange) {
        ExprTy test = lookupAndConvert(obj, AstState.T_F_TEST, AstState.T_C_IF, this::obj2ExprTy, true);
        StmtTy[] body = lookupAndConvertSequence(obj, AstState.T_F_BODY, AstState.T_C_IF, this::obj2StmtTy, StmtTy[]::new);
        StmtTy[] orElse = lookupAndConvertSequence(obj, AstState.T_F_ORELSE, AstState.T_C_IF, this::obj2StmtTy, StmtTy[]::new);
        return new StmtTy.If(test, body, orElse, sourceRange);
    }

    StmtTy.With obj2With(Object obj, SourceRange sourceRange) {
        WithItemTy[] items = lookupAndConvertSequence(obj, AstState.T_F_ITEMS, AstState.T_C_WITH, this::obj2WithItemTy, WithItemTy[]::new);
        StmtTy[] body = lookupAndConvertSequence(obj, AstState.T_F_BODY, AstState.T_C_WITH, this::obj2StmtTy, StmtTy[]::new);
        Object typeComment = lookupAndConvert(obj, AstState.T_F_TYPE_COMMENT, AstState.T_C_WITH, this::obj2string, false);
        return new StmtTy.With(items, body, typeComment, sourceRange);
    }

    StmtTy.AsyncWith obj2AsyncWith(Object obj, SourceRange sourceRange) {
        WithItemTy[] items = lookupAndConvertSequence(obj, AstState.T_F_ITEMS, AstState.T_C_ASYNCWITH, this::obj2WithItemTy, WithItemTy[]::new);
        StmtTy[] body = lookupAndConvertSequence(obj, AstState.T_F_BODY, AstState.T_C_ASYNCWITH, this::obj2StmtTy, StmtTy[]::new);
        Object typeComment = lookupAndConvert(obj, AstState.T_F_TYPE_COMMENT, AstState.T_C_ASYNCWITH, this::obj2string, false);
        return new StmtTy.AsyncWith(items, body, typeComment, sourceRange);
    }

    StmtTy.Match obj2Match(Object obj, SourceRange sourceRange) {
        ExprTy subject = lookupAndConvert(obj, AstState.T_F_SUBJECT, AstState.T_C_MATCH, this::obj2ExprTy, true);
        MatchCaseTy[] cases = lookupAndConvertSequence(obj, AstState.T_F_CASES, AstState.T_C_MATCH, this::obj2MatchCaseTy, MatchCaseTy[]::new);
        return new StmtTy.Match(subject, cases, sourceRange);
    }

    StmtTy.Raise obj2Raise(Object obj, SourceRange sourceRange) {
        ExprTy exc = lookupAndConvert(obj, AstState.T_F_EXC, AstState.T_C_RAISE, this::obj2ExprTy, false);
        ExprTy cause = lookupAndConvert(obj, AstState.T_F_CAUSE, AstState.T_C_RAISE, this::obj2ExprTy, false);
        return new StmtTy.Raise(exc, cause, sourceRange);
    }

    StmtTy.Try obj2Try(Object obj, SourceRange sourceRange) {
        StmtTy[] body = lookupAndConvertSequence(obj, AstState.T_F_BODY, AstState.T_C_TRY, this::obj2StmtTy, StmtTy[]::new);
        ExceptHandlerTy[] handlers = lookupAndConvertSequence(obj, AstState.T_F_HANDLERS, AstState.T_C_TRY, this::obj2ExceptHandlerTy, ExceptHandlerTy[]::new);
        StmtTy[] orElse = lookupAndConvertSequence(obj, AstState.T_F_ORELSE, AstState.T_C_TRY, this::obj2StmtTy, StmtTy[]::new);
        StmtTy[] finalBody = lookupAndConvertSequence(obj, AstState.T_F_FINALBODY, AstState.T_C_TRY, this::obj2StmtTy, StmtTy[]::new);
        return new StmtTy.Try(body, handlers, orElse, finalBody, sourceRange);
    }

    StmtTy.TryStar obj2TryStar(Object obj, SourceRange sourceRange) {
        StmtTy[] body = lookupAndConvertSequence(obj, AstState.T_F_BODY, AstState.T_C_TRYSTAR, this::obj2StmtTy, StmtTy[]::new);
        ExceptHandlerTy[] handlers = lookupAndConvertSequence(obj, AstState.T_F_HANDLERS, AstState.T_C_TRYSTAR, this::obj2ExceptHandlerTy, ExceptHandlerTy[]::new);
        StmtTy[] orElse = lookupAndConvertSequence(obj, AstState.T_F_ORELSE, AstState.T_C_TRYSTAR, this::obj2StmtTy, StmtTy[]::new);
        StmtTy[] finalBody = lookupAndConvertSequence(obj, AstState.T_F_FINALBODY, AstState.T_C_TRYSTAR, this::obj2StmtTy, StmtTy[]::new);
        return new StmtTy.TryStar(body, handlers, orElse, finalBody, sourceRange);
    }

    StmtTy.Assert obj2Assert(Object obj, SourceRange sourceRange) {
        ExprTy test = lookupAndConvert(obj, AstState.T_F_TEST, AstState.T_C_ASSERT, this::obj2ExprTy, true);
        ExprTy msg = lookupAndConvert(obj, AstState.T_F_MSG, AstState.T_C_ASSERT, this::obj2ExprTy, false);
        return new StmtTy.Assert(test, msg, sourceRange);
    }

    StmtTy.Import obj2Import(Object obj, SourceRange sourceRange) {
        AliasTy[] names = lookupAndConvertSequence(obj, AstState.T_F_NAMES, AstState.T_C_IMPORT, this::obj2AliasTy, AliasTy[]::new);
        return new StmtTy.Import(names, sourceRange);
    }

    StmtTy.ImportFrom obj2ImportFrom(Object obj, SourceRange sourceRange) {
        String module = lookupAndConvert(obj, AstState.T_F_MODULE, AstState.T_C_IMPORTFROM, this::obj2identifier, false);
        AliasTy[] names = lookupAndConvertSequence(obj, AstState.T_F_NAMES, AstState.T_C_IMPORTFROM, this::obj2AliasTy, AliasTy[]::new);
        int level = lookupAndConvertInt(obj, AstState.T_F_LEVEL, AstState.T_C_IMPORTFROM, false);
        return new StmtTy.ImportFrom(module, names, level, sourceRange);
    }

    StmtTy.Global obj2Global(Object obj, SourceRange sourceRange) {
        String[] names = lookupAndConvertSequence(obj, AstState.T_F_NAMES, AstState.T_C_GLOBAL, this::obj2identifier, String[]::new);
        return new StmtTy.Global(names, sourceRange);
    }

    StmtTy.Nonlocal obj2Nonlocal(Object obj, SourceRange sourceRange) {
        String[] names = lookupAndConvertSequence(obj, AstState.T_F_NAMES, AstState.T_C_NONLOCAL, this::obj2identifier, String[]::new);
        return new StmtTy.Nonlocal(names, sourceRange);
    }

    StmtTy.Expr obj2Expr(Object obj, SourceRange sourceRange) {
        ExprTy value = lookupAndConvert(obj, AstState.T_F_VALUE, AstState.T_C_EXPR, this::obj2ExprTy, true);
        return new StmtTy.Expr(value, sourceRange);
    }

    StmtTy.Pass obj2Pass(Object obj, SourceRange sourceRange) {
        return new StmtTy.Pass(sourceRange);
    }

    StmtTy.Break obj2Break(Object obj, SourceRange sourceRange) {
        return new StmtTy.Break(sourceRange);
    }

    StmtTy.Continue obj2Continue(Object obj, SourceRange sourceRange) {
        return new StmtTy.Continue(sourceRange);
    }

    ExprTy obj2ExprTy(Object obj) {
        if (obj == PNone.NONE) {
            return null;
        }
        int lineNo = lookupAndConvertInt(obj, AstState.T_F_LINENO, AstState.T_T_EXPR, true);
        int colOffset = lookupAndConvertInt(obj, AstState.T_F_COL_OFFSET, AstState.T_T_EXPR, true);
        int endLineno = lookupAndConvertInt(obj, AstState.T_F_END_LINENO, AstState.T_T_EXPR, false);
        int endColOffset = lookupAndConvertInt(obj, AstState.T_F_END_COL_OFFSET, AstState.T_T_EXPR, false);
        SourceRange sourceRange = new SourceRange(lineNo, colOffset, endLineno, endColOffset);
        if (isInstanceOf(obj, state.clsBoolOp)) {
            return obj2BoolOp(obj, sourceRange);
        }
        if (isInstanceOf(obj, state.clsNamedExpr)) {
            return obj2NamedExpr(obj, sourceRange);
        }
        if (isInstanceOf(obj, state.clsBinOp)) {
            return obj2BinOp(obj, sourceRange);
        }
        if (isInstanceOf(obj, state.clsUnaryOp)) {
            return obj2UnaryOp(obj, sourceRange);
        }
        if (isInstanceOf(obj, state.clsLambda)) {
            return obj2Lambda(obj, sourceRange);
        }
        if (isInstanceOf(obj, state.clsIfExp)) {
            return obj2IfExp(obj, sourceRange);
        }
        if (isInstanceOf(obj, state.clsDict)) {
            return obj2Dict(obj, sourceRange);
        }
        if (isInstanceOf(obj, state.clsSet)) {
            return obj2Set(obj, sourceRange);
        }
        if (isInstanceOf(obj, state.clsListComp)) {
            return obj2ListComp(obj, sourceRange);
        }
        if (isInstanceOf(obj, state.clsSetComp)) {
            return obj2SetComp(obj, sourceRange);
        }
        if (isInstanceOf(obj, state.clsDictComp)) {
            return obj2DictComp(obj, sourceRange);
        }
        if (isInstanceOf(obj, state.clsGeneratorExp)) {
            return obj2GeneratorExp(obj, sourceRange);
        }
        if (isInstanceOf(obj, state.clsAwait)) {
            return obj2Await(obj, sourceRange);
        }
        if (isInstanceOf(obj, state.clsYield)) {
            return obj2Yield(obj, sourceRange);
        }
        if (isInstanceOf(obj, state.clsYieldFrom)) {
            return obj2YieldFrom(obj, sourceRange);
        }
        if (isInstanceOf(obj, state.clsCompare)) {
            return obj2Compare(obj, sourceRange);
        }
        if (isInstanceOf(obj, state.clsCall)) {
            return obj2Call(obj, sourceRange);
        }
        if (isInstanceOf(obj, state.clsFormattedValue)) {
            return obj2FormattedValue(obj, sourceRange);
        }
        if (isInstanceOf(obj, state.clsJoinedStr)) {
            return obj2JoinedStr(obj, sourceRange);
        }
        if (isInstanceOf(obj, state.clsConstant)) {
            return obj2Constant(obj, sourceRange);
        }
        if (isInstanceOf(obj, state.clsAttribute)) {
            return obj2Attribute(obj, sourceRange);
        }
        if (isInstanceOf(obj, state.clsSubscript)) {
            return obj2Subscript(obj, sourceRange);
        }
        if (isInstanceOf(obj, state.clsStarred)) {
            return obj2Starred(obj, sourceRange);
        }
        if (isInstanceOf(obj, state.clsName)) {
            return obj2Name(obj, sourceRange);
        }
        if (isInstanceOf(obj, state.clsList)) {
            return obj2List(obj, sourceRange);
        }
        if (isInstanceOf(obj, state.clsTuple)) {
            return obj2Tuple(obj, sourceRange);
        }
        if (isInstanceOf(obj, state.clsSlice)) {
            return obj2Slice(obj, sourceRange);
        }
        throw unexpectedNodeType(AstState.T_T_EXPR, obj);
    }

    ExprTy.BoolOp obj2BoolOp(Object obj, SourceRange sourceRange) {
        BoolOpTy op = lookupAndConvert(obj, AstState.T_F_OP, AstState.T_C_BOOLOP, this::obj2BoolOpTy, true);
        ExprTy[] values = lookupAndConvertSequence(obj, AstState.T_F_VALUES, AstState.T_C_BOOLOP, this::obj2ExprTy, ExprTy[]::new);
        return new ExprTy.BoolOp(op, values, sourceRange);
    }

    ExprTy.NamedExpr obj2NamedExpr(Object obj, SourceRange sourceRange) {
        ExprTy target = lookupAndConvert(obj, AstState.T_F_TARGET, AstState.T_C_NAMEDEXPR, this::obj2ExprTy, true);
        ExprTy value = lookupAndConvert(obj, AstState.T_F_VALUE, AstState.T_C_NAMEDEXPR, this::obj2ExprTy, true);
        return new ExprTy.NamedExpr(target, value, sourceRange);
    }

    ExprTy.BinOp obj2BinOp(Object obj, SourceRange sourceRange) {
        ExprTy left = lookupAndConvert(obj, AstState.T_F_LEFT, AstState.T_C_BINOP, this::obj2ExprTy, true);
        OperatorTy op = lookupAndConvert(obj, AstState.T_F_OP, AstState.T_C_BINOP, this::obj2OperatorTy, true);
        ExprTy right = lookupAndConvert(obj, AstState.T_F_RIGHT, AstState.T_C_BINOP, this::obj2ExprTy, true);
        return new ExprTy.BinOp(left, op, right, sourceRange);
    }

    ExprTy.UnaryOp obj2UnaryOp(Object obj, SourceRange sourceRange) {
        UnaryOpTy op = lookupAndConvert(obj, AstState.T_F_OP, AstState.T_C_UNARYOP, this::obj2UnaryOpTy, true);
        ExprTy operand = lookupAndConvert(obj, AstState.T_F_OPERAND, AstState.T_C_UNARYOP, this::obj2ExprTy, true);
        return new ExprTy.UnaryOp(op, operand, sourceRange);
    }

    ExprTy.Lambda obj2Lambda(Object obj, SourceRange sourceRange) {
        ArgumentsTy args = lookupAndConvert(obj, AstState.T_F_ARGS, AstState.T_C_LAMBDA, this::obj2ArgumentsTy, true);
        ExprTy body = lookupAndConvert(obj, AstState.T_F_BODY, AstState.T_C_LAMBDA, this::obj2ExprTy, true);
        return new ExprTy.Lambda(args, body, sourceRange);
    }

    ExprTy.IfExp obj2IfExp(Object obj, SourceRange sourceRange) {
        ExprTy test = lookupAndConvert(obj, AstState.T_F_TEST, AstState.T_C_IFEXP, this::obj2ExprTy, true);
        ExprTy body = lookupAndConvert(obj, AstState.T_F_BODY, AstState.T_C_IFEXP, this::obj2ExprTy, true);
        ExprTy orElse = lookupAndConvert(obj, AstState.T_F_ORELSE, AstState.T_C_IFEXP, this::obj2ExprTy, true);
        return new ExprTy.IfExp(test, body, orElse, sourceRange);
    }

    ExprTy.Dict obj2Dict(Object obj, SourceRange sourceRange) {
        ExprTy[] keys = lookupAndConvertSequence(obj, AstState.T_F_KEYS, AstState.T_C_DICT, this::obj2ExprTy, ExprTy[]::new);
        ExprTy[] values = lookupAndConvertSequence(obj, AstState.T_F_VALUES, AstState.T_C_DICT, this::obj2ExprTy, ExprTy[]::new);
        return new ExprTy.Dict(keys, values, sourceRange);
    }

    ExprTy.Set obj2Set(Object obj, SourceRange sourceRange) {
        ExprTy[] elements = lookupAndConvertSequence(obj, AstState.T_F_ELTS, AstState.T_C_SET, this::obj2ExprTy, ExprTy[]::new);
        return new ExprTy.Set(elements, sourceRange);
    }

    ExprTy.ListComp obj2ListComp(Object obj, SourceRange sourceRange) {
        ExprTy element = lookupAndConvert(obj, AstState.T_F_ELT, AstState.T_C_LISTCOMP, this::obj2ExprTy, true);
        ComprehensionTy[] generators = lookupAndConvertSequence(obj, AstState.T_F_GENERATORS, AstState.T_C_LISTCOMP, this::obj2ComprehensionTy, ComprehensionTy[]::new);
        return new ExprTy.ListComp(element, generators, sourceRange);
    }

    ExprTy.SetComp obj2SetComp(Object obj, SourceRange sourceRange) {
        ExprTy element = lookupAndConvert(obj, AstState.T_F_ELT, AstState.T_C_SETCOMP, this::obj2ExprTy, true);
        ComprehensionTy[] generators = lookupAndConvertSequence(obj, AstState.T_F_GENERATORS, AstState.T_C_SETCOMP, this::obj2ComprehensionTy, ComprehensionTy[]::new);
        return new ExprTy.SetComp(element, generators, sourceRange);
    }

    ExprTy.DictComp obj2DictComp(Object obj, SourceRange sourceRange) {
        ExprTy key = lookupAndConvert(obj, AstState.T_F_KEY, AstState.T_C_DICTCOMP, this::obj2ExprTy, true);
        ExprTy value = lookupAndConvert(obj, AstState.T_F_VALUE, AstState.T_C_DICTCOMP, this::obj2ExprTy, true);
        ComprehensionTy[] generators = lookupAndConvertSequence(obj, AstState.T_F_GENERATORS, AstState.T_C_DICTCOMP, this::obj2ComprehensionTy, ComprehensionTy[]::new);
        return new ExprTy.DictComp(key, value, generators, sourceRange);
    }

    ExprTy.GeneratorExp obj2GeneratorExp(Object obj, SourceRange sourceRange) {
        ExprTy element = lookupAndConvert(obj, AstState.T_F_ELT, AstState.T_C_GENERATOREXP, this::obj2ExprTy, true);
        ComprehensionTy[] generators = lookupAndConvertSequence(obj, AstState.T_F_GENERATORS, AstState.T_C_GENERATOREXP, this::obj2ComprehensionTy, ComprehensionTy[]::new);
        return new ExprTy.GeneratorExp(element, generators, sourceRange);
    }

    ExprTy.Await obj2Await(Object obj, SourceRange sourceRange) {
        ExprTy value = lookupAndConvert(obj, AstState.T_F_VALUE, AstState.T_C_AWAIT, this::obj2ExprTy, true);
        return new ExprTy.Await(value, sourceRange);
    }

    ExprTy.Yield obj2Yield(Object obj, SourceRange sourceRange) {
        ExprTy value = lookupAndConvert(obj, AstState.T_F_VALUE, AstState.T_C_YIELD, this::obj2ExprTy, false);
        return new ExprTy.Yield(value, sourceRange);
    }

    ExprTy.YieldFrom obj2YieldFrom(Object obj, SourceRange sourceRange) {
        ExprTy value = lookupAndConvert(obj, AstState.T_F_VALUE, AstState.T_C_YIELDFROM, this::obj2ExprTy, true);
        return new ExprTy.YieldFrom(value, sourceRange);
    }

    ExprTy.Compare obj2Compare(Object obj, SourceRange sourceRange) {
        ExprTy left = lookupAndConvert(obj, AstState.T_F_LEFT, AstState.T_C_COMPARE, this::obj2ExprTy, true);
        CmpOpTy[] ops = lookupAndConvertSequence(obj, AstState.T_F_OPS, AstState.T_C_COMPARE, this::obj2CmpOpTy, CmpOpTy[]::new);
        ExprTy[] comparators = lookupAndConvertSequence(obj, AstState.T_F_COMPARATORS, AstState.T_C_COMPARE, this::obj2ExprTy, ExprTy[]::new);
        return new ExprTy.Compare(left, ops, comparators, sourceRange);
    }

    ExprTy.Call obj2Call(Object obj, SourceRange sourceRange) {
        ExprTy func = lookupAndConvert(obj, AstState.T_F_FUNC, AstState.T_C_CALL, this::obj2ExprTy, true);
        ExprTy[] args = lookupAndConvertSequence(obj, AstState.T_F_ARGS, AstState.T_C_CALL, this::obj2ExprTy, ExprTy[]::new);
        KeywordTy[] keywords = lookupAndConvertSequence(obj, AstState.T_F_KEYWORDS, AstState.T_C_CALL, this::obj2KeywordTy, KeywordTy[]::new);
        return new ExprTy.Call(func, args, keywords, sourceRange);
    }

    ExprTy.FormattedValue obj2FormattedValue(Object obj, SourceRange sourceRange) {
        ExprTy value = lookupAndConvert(obj, AstState.T_F_VALUE, AstState.T_C_FORMATTEDVALUE, this::obj2ExprTy, true);
        int conversion = lookupAndConvertInt(obj, AstState.T_F_CONVERSION, AstState.T_C_FORMATTEDVALUE, true);
        ExprTy formatSpec = lookupAndConvert(obj, AstState.T_F_FORMAT_SPEC, AstState.T_C_FORMATTEDVALUE, this::obj2ExprTy, false);
        return new ExprTy.FormattedValue(value, conversion, formatSpec, sourceRange);
    }

    ExprTy.JoinedStr obj2JoinedStr(Object obj, SourceRange sourceRange) {
        ExprTy[] values = lookupAndConvertSequence(obj, AstState.T_F_VALUES, AstState.T_C_JOINEDSTR, this::obj2ExprTy, ExprTy[]::new);
        return new ExprTy.JoinedStr(values, sourceRange);
    }

    ExprTy.Constant obj2Constant(Object obj, SourceRange sourceRange) {
        ConstantValue value = lookupAndConvert(obj, AstState.T_F_VALUE, AstState.T_C_CONSTANT, this::obj2ConstantValue, true);
        Object kind = lookupAndConvert(obj, AstState.T_F_KIND, AstState.T_C_CONSTANT, this::obj2string, false);
        return new ExprTy.Constant(value, kind, sourceRange);
    }

    ExprTy.Attribute obj2Attribute(Object obj, SourceRange sourceRange) {
        ExprTy value = lookupAndConvert(obj, AstState.T_F_VALUE, AstState.T_C_ATTRIBUTE, this::obj2ExprTy, true);
        String attr = lookupAndConvert(obj, AstState.T_F_ATTR, AstState.T_C_ATTRIBUTE, this::obj2identifier, true);
        ExprContextTy context = lookupAndConvert(obj, AstState.T_F_CTX, AstState.T_C_ATTRIBUTE, this::obj2ExprContextTy, true);
        return new ExprTy.Attribute(value, attr, context, sourceRange);
    }

    ExprTy.Subscript obj2Subscript(Object obj, SourceRange sourceRange) {
        ExprTy value = lookupAndConvert(obj, AstState.T_F_VALUE, AstState.T_C_SUBSCRIPT, this::obj2ExprTy, true);
        ExprTy slice = lookupAndConvert(obj, AstState.T_F_SLICE, AstState.T_C_SUBSCRIPT, this::obj2ExprTy, true);
        ExprContextTy context = lookupAndConvert(obj, AstState.T_F_CTX, AstState.T_C_SUBSCRIPT, this::obj2ExprContextTy, true);
        return new ExprTy.Subscript(value, slice, context, sourceRange);
    }

    ExprTy.Starred obj2Starred(Object obj, SourceRange sourceRange) {
        ExprTy value = lookupAndConvert(obj, AstState.T_F_VALUE, AstState.T_C_STARRED, this::obj2ExprTy, true);
        ExprContextTy context = lookupAndConvert(obj, AstState.T_F_CTX, AstState.T_C_STARRED, this::obj2ExprContextTy, true);
        return new ExprTy.Starred(value, context, sourceRange);
    }

    ExprTy.Name obj2Name(Object obj, SourceRange sourceRange) {
        String id = lookupAndConvert(obj, AstState.T_F_ID, AstState.T_C_NAME, this::obj2identifier, true);
        ExprContextTy context = lookupAndConvert(obj, AstState.T_F_CTX, AstState.T_C_NAME, this::obj2ExprContextTy, true);
        return new ExprTy.Name(id, context, sourceRange);
    }

    ExprTy.List obj2List(Object obj, SourceRange sourceRange) {
        ExprTy[] elements = lookupAndConvertSequence(obj, AstState.T_F_ELTS, AstState.T_C_LIST, this::obj2ExprTy, ExprTy[]::new);
        ExprContextTy context = lookupAndConvert(obj, AstState.T_F_CTX, AstState.T_C_LIST, this::obj2ExprContextTy, true);
        return new ExprTy.List(elements, context, sourceRange);
    }

    ExprTy.Tuple obj2Tuple(Object obj, SourceRange sourceRange) {
        ExprTy[] elements = lookupAndConvertSequence(obj, AstState.T_F_ELTS, AstState.T_C_TUPLE, this::obj2ExprTy, ExprTy[]::new);
        ExprContextTy context = lookupAndConvert(obj, AstState.T_F_CTX, AstState.T_C_TUPLE, this::obj2ExprContextTy, true);
        return new ExprTy.Tuple(elements, context, sourceRange);
    }

    ExprTy.Slice obj2Slice(Object obj, SourceRange sourceRange) {
        ExprTy lower = lookupAndConvert(obj, AstState.T_F_LOWER, AstState.T_C_SLICE, this::obj2ExprTy, false);
        ExprTy upper = lookupAndConvert(obj, AstState.T_F_UPPER, AstState.T_C_SLICE, this::obj2ExprTy, false);
        ExprTy step = lookupAndConvert(obj, AstState.T_F_STEP, AstState.T_C_SLICE, this::obj2ExprTy, false);
        return new ExprTy.Slice(lower, upper, step, sourceRange);
    }

    ExprContextTy obj2ExprContextTy(Object obj) {
        if (isInstanceOf(obj, state.clsLoad)) {
            return ExprContextTy.Load;
        }
        if (isInstanceOf(obj, state.clsStore)) {
            return ExprContextTy.Store;
        }
        if (isInstanceOf(obj, state.clsDel)) {
            return ExprContextTy.Del;
        }
        throw unexpectedNodeType(AstState.T_T_EXPR_CONTEXT, obj);
    }

    BoolOpTy obj2BoolOpTy(Object obj) {
        if (isInstanceOf(obj, state.clsAnd)) {
            return BoolOpTy.And;
        }
        if (isInstanceOf(obj, state.clsOr)) {
            return BoolOpTy.Or;
        }
        throw unexpectedNodeType(AstState.T_T_BOOLOP, obj);
    }

    OperatorTy obj2OperatorTy(Object obj) {
        if (isInstanceOf(obj, state.clsAdd)) {
            return OperatorTy.Add;
        }
        if (isInstanceOf(obj, state.clsSub)) {
            return OperatorTy.Sub;
        }
        if (isInstanceOf(obj, state.clsMult)) {
            return OperatorTy.Mult;
        }
        if (isInstanceOf(obj, state.clsMatMult)) {
            return OperatorTy.MatMult;
        }
        if (isInstanceOf(obj, state.clsDiv)) {
            return OperatorTy.Div;
        }
        if (isInstanceOf(obj, state.clsMod)) {
            return OperatorTy.Mod;
        }
        if (isInstanceOf(obj, state.clsPow)) {
            return OperatorTy.Pow;
        }
        if (isInstanceOf(obj, state.clsLShift)) {
            return OperatorTy.LShift;
        }
        if (isInstanceOf(obj, state.clsRShift)) {
            return OperatorTy.RShift;
        }
        if (isInstanceOf(obj, state.clsBitOr)) {
            return OperatorTy.BitOr;
        }
        if (isInstanceOf(obj, state.clsBitXor)) {
            return OperatorTy.BitXor;
        }
        if (isInstanceOf(obj, state.clsBitAnd)) {
            return OperatorTy.BitAnd;
        }
        if (isInstanceOf(obj, state.clsFloorDiv)) {
            return OperatorTy.FloorDiv;
        }
        throw unexpectedNodeType(AstState.T_T_OPERATOR, obj);
    }

    UnaryOpTy obj2UnaryOpTy(Object obj) {
        if (isInstanceOf(obj, state.clsInvert)) {
            return UnaryOpTy.Invert;
        }
        if (isInstanceOf(obj, state.clsNot)) {
            return UnaryOpTy.Not;
        }
        if (isInstanceOf(obj, state.clsUAdd)) {
            return UnaryOpTy.UAdd;
        }
        if (isInstanceOf(obj, state.clsUSub)) {
            return UnaryOpTy.USub;
        }
        throw unexpectedNodeType(AstState.T_T_UNARYOP, obj);
    }

    CmpOpTy obj2CmpOpTy(Object obj) {
        if (isInstanceOf(obj, state.clsEq)) {
            return CmpOpTy.Eq;
        }
        if (isInstanceOf(obj, state.clsNotEq)) {
            return CmpOpTy.NotEq;
        }
        if (isInstanceOf(obj, state.clsLt)) {
            return CmpOpTy.Lt;
        }
        if (isInstanceOf(obj, state.clsLtE)) {
            return CmpOpTy.LtE;
        }
        if (isInstanceOf(obj, state.clsGt)) {
            return CmpOpTy.Gt;
        }
        if (isInstanceOf(obj, state.clsGtE)) {
            return CmpOpTy.GtE;
        }
        if (isInstanceOf(obj, state.clsIs)) {
            return CmpOpTy.Is;
        }
        if (isInstanceOf(obj, state.clsIsNot)) {
            return CmpOpTy.IsNot;
        }
        if (isInstanceOf(obj, state.clsIn)) {
            return CmpOpTy.In;
        }
        if (isInstanceOf(obj, state.clsNotIn)) {
            return CmpOpTy.NotIn;
        }
        throw unexpectedNodeType(AstState.T_T_CMPOP, obj);
    }

    ComprehensionTy obj2ComprehensionTy(Object obj) {
        ExprTy target = lookupAndConvert(obj, AstState.T_F_TARGET, AstState.T_T_COMPREHENSION, this::obj2ExprTy, true);
        ExprTy iter = lookupAndConvert(obj, AstState.T_F_ITER, AstState.T_T_COMPREHENSION, this::obj2ExprTy, true);
        ExprTy[] ifs = lookupAndConvertSequence(obj, AstState.T_F_IFS, AstState.T_T_COMPREHENSION, this::obj2ExprTy, ExprTy[]::new);
        boolean isAsync = lookupAndConvertBoolean(obj, AstState.T_F_IS_ASYNC, AstState.T_T_COMPREHENSION, true);
        SourceRange sourceRange = SourceRange.ARTIFICIAL_RANGE;
        return new ComprehensionTy(target, iter, ifs, isAsync, sourceRange);
    }

    ExceptHandlerTy obj2ExceptHandlerTy(Object obj) {
        if (obj == PNone.NONE) {
            return null;
        }
        int lineNo = lookupAndConvertInt(obj, AstState.T_F_LINENO, AstState.T_T_EXCEPTHANDLER, true);
        int colOffset = lookupAndConvertInt(obj, AstState.T_F_COL_OFFSET, AstState.T_T_EXCEPTHANDLER, true);
        int endLineno = lookupAndConvertInt(obj, AstState.T_F_END_LINENO, AstState.T_T_EXCEPTHANDLER, false);
        int endColOffset = lookupAndConvertInt(obj, AstState.T_F_END_COL_OFFSET, AstState.T_T_EXCEPTHANDLER, false);
        SourceRange sourceRange = new SourceRange(lineNo, colOffset, endLineno, endColOffset);
        if (isInstanceOf(obj, state.clsExceptHandler)) {
            return obj2ExceptHandler(obj, sourceRange);
        }
        throw unexpectedNodeType(AstState.T_T_EXCEPTHANDLER, obj);
    }

    ExceptHandlerTy.ExceptHandler obj2ExceptHandler(Object obj, SourceRange sourceRange) {
        ExprTy type = lookupAndConvert(obj, AstState.T_F_TYPE, AstState.T_C_EXCEPTHANDLER, this::obj2ExprTy, false);
        String name = lookupAndConvert(obj, AstState.T_F_NAME, AstState.T_C_EXCEPTHANDLER, this::obj2identifier, false);
        StmtTy[] body = lookupAndConvertSequence(obj, AstState.T_F_BODY, AstState.T_C_EXCEPTHANDLER, this::obj2StmtTy, StmtTy[]::new);
        return new ExceptHandlerTy.ExceptHandler(type, name, body, sourceRange);
    }

    ArgumentsTy obj2ArgumentsTy(Object obj) {
        ArgTy[] posOnlyArgs = lookupAndConvertSequence(obj, AstState.T_F_POSONLYARGS, AstState.T_T_ARGUMENTS, this::obj2ArgTy, ArgTy[]::new);
        ArgTy[] args = lookupAndConvertSequence(obj, AstState.T_F_ARGS, AstState.T_T_ARGUMENTS, this::obj2ArgTy, ArgTy[]::new);
        ArgTy varArg = lookupAndConvert(obj, AstState.T_F_VARARG, AstState.T_T_ARGUMENTS, this::obj2ArgTy, false);
        ArgTy[] kwOnlyArgs = lookupAndConvertSequence(obj, AstState.T_F_KWONLYARGS, AstState.T_T_ARGUMENTS, this::obj2ArgTy, ArgTy[]::new);
        ExprTy[] kwDefaults = lookupAndConvertSequence(obj, AstState.T_F_KW_DEFAULTS, AstState.T_T_ARGUMENTS, this::obj2ExprTy, ExprTy[]::new);
        ArgTy kwArg = lookupAndConvert(obj, AstState.T_F_KWARG, AstState.T_T_ARGUMENTS, this::obj2ArgTy, false);
        ExprTy[] defaults = lookupAndConvertSequence(obj, AstState.T_F_DEFAULTS, AstState.T_T_ARGUMENTS, this::obj2ExprTy, ExprTy[]::new);
        SourceRange sourceRange = SourceRange.ARTIFICIAL_RANGE;
        return new ArgumentsTy(posOnlyArgs, args, varArg, kwOnlyArgs, kwDefaults, kwArg, defaults, sourceRange);
    }

    ArgTy obj2ArgTy(Object obj) {
        String arg = lookupAndConvert(obj, AstState.T_F_ARG, AstState.T_T_ARG, this::obj2identifier, true);
        ExprTy annotation = lookupAndConvert(obj, AstState.T_F_ANNOTATION, AstState.T_T_ARG, this::obj2ExprTy, false);
        Object typeComment = lookupAndConvert(obj, AstState.T_F_TYPE_COMMENT, AstState.T_T_ARG, this::obj2string, false);
        int lineNo = lookupAndConvertInt(obj, AstState.T_F_LINENO, AstState.T_T_ARG, true);
        int colOffset = lookupAndConvertInt(obj, AstState.T_F_COL_OFFSET, AstState.T_T_ARG, true);
        int endLineno = lookupAndConvertInt(obj, AstState.T_F_END_LINENO, AstState.T_T_ARG, false);
        int endColOffset = lookupAndConvertInt(obj, AstState.T_F_END_COL_OFFSET, AstState.T_T_ARG, false);
        SourceRange sourceRange = new SourceRange(lineNo, colOffset, endLineno, endColOffset);
        return new ArgTy(arg, annotation, typeComment, sourceRange);
    }

    KeywordTy obj2KeywordTy(Object obj) {
        String arg = lookupAndConvert(obj, AstState.T_F_ARG, AstState.T_T_KEYWORD, this::obj2identifier, false);
        ExprTy value = lookupAndConvert(obj, AstState.T_F_VALUE, AstState.T_T_KEYWORD, this::obj2ExprTy, true);
        int lineNo = lookupAndConvertInt(obj, AstState.T_F_LINENO, AstState.T_T_KEYWORD, true);
        int colOffset = lookupAndConvertInt(obj, AstState.T_F_COL_OFFSET, AstState.T_T_KEYWORD, true);
        int endLineno = lookupAndConvertInt(obj, AstState.T_F_END_LINENO, AstState.T_T_KEYWORD, false);
        int endColOffset = lookupAndConvertInt(obj, AstState.T_F_END_COL_OFFSET, AstState.T_T_KEYWORD, false);
        SourceRange sourceRange = new SourceRange(lineNo, colOffset, endLineno, endColOffset);
        return new KeywordTy(arg, value, sourceRange);
    }

    AliasTy obj2AliasTy(Object obj) {
        String name = lookupAndConvert(obj, AstState.T_F_NAME, AstState.T_T_ALIAS, this::obj2identifier, true);
        String asName = lookupAndConvert(obj, AstState.T_F_ASNAME, AstState.T_T_ALIAS, this::obj2identifier, false);
        int lineNo = lookupAndConvertInt(obj, AstState.T_F_LINENO, AstState.T_T_ALIAS, true);
        int colOffset = lookupAndConvertInt(obj, AstState.T_F_COL_OFFSET, AstState.T_T_ALIAS, true);
        int endLineno = lookupAndConvertInt(obj, AstState.T_F_END_LINENO, AstState.T_T_ALIAS, false);
        int endColOffset = lookupAndConvertInt(obj, AstState.T_F_END_COL_OFFSET, AstState.T_T_ALIAS, false);
        SourceRange sourceRange = new SourceRange(lineNo, colOffset, endLineno, endColOffset);
        return new AliasTy(name, asName, sourceRange);
    }

    WithItemTy obj2WithItemTy(Object obj) {
        ExprTy contextExpr = lookupAndConvert(obj, AstState.T_F_CONTEXT_EXPR, AstState.T_T_WITHITEM, this::obj2ExprTy, true);
        ExprTy optionalVars = lookupAndConvert(obj, AstState.T_F_OPTIONAL_VARS, AstState.T_T_WITHITEM, this::obj2ExprTy, false);
        SourceRange sourceRange = SourceRange.ARTIFICIAL_RANGE;
        return new WithItemTy(contextExpr, optionalVars, sourceRange);
    }

    MatchCaseTy obj2MatchCaseTy(Object obj) {
        PatternTy pattern = lookupAndConvert(obj, AstState.T_F_PATTERN, AstState.T_T_MATCH_CASE, this::obj2PatternTy, true);
        ExprTy guard = lookupAndConvert(obj, AstState.T_F_GUARD, AstState.T_T_MATCH_CASE, this::obj2ExprTy, false);
        StmtTy[] body = lookupAndConvertSequence(obj, AstState.T_F_BODY, AstState.T_T_MATCH_CASE, this::obj2StmtTy, StmtTy[]::new);
        SourceRange sourceRange = SourceRange.ARTIFICIAL_RANGE;
        return new MatchCaseTy(pattern, guard, body, sourceRange);
    }

    PatternTy obj2PatternTy(Object obj) {
        if (obj == PNone.NONE) {
            return null;
        }
        int lineNo = lookupAndConvertInt(obj, AstState.T_F_LINENO, AstState.T_T_PATTERN, true);
        int colOffset = lookupAndConvertInt(obj, AstState.T_F_COL_OFFSET, AstState.T_T_PATTERN, true);
        int endLineno = lookupAndConvertInt(obj, AstState.T_F_END_LINENO, AstState.T_T_PATTERN, true);
        int endColOffset = lookupAndConvertInt(obj, AstState.T_F_END_COL_OFFSET, AstState.T_T_PATTERN, true);
        SourceRange sourceRange = new SourceRange(lineNo, colOffset, endLineno, endColOffset);
        if (isInstanceOf(obj, state.clsMatchValue)) {
            return obj2MatchValue(obj, sourceRange);
        }
        if (isInstanceOf(obj, state.clsMatchSingleton)) {
            return obj2MatchSingleton(obj, sourceRange);
        }
        if (isInstanceOf(obj, state.clsMatchSequence)) {
            return obj2MatchSequence(obj, sourceRange);
        }
        if (isInstanceOf(obj, state.clsMatchMapping)) {
            return obj2MatchMapping(obj, sourceRange);
        }
        if (isInstanceOf(obj, state.clsMatchClass)) {
            return obj2MatchClass(obj, sourceRange);
        }
        if (isInstanceOf(obj, state.clsMatchStar)) {
            return obj2MatchStar(obj, sourceRange);
        }
        if (isInstanceOf(obj, state.clsMatchAs)) {
            return obj2MatchAs(obj, sourceRange);
        }
        if (isInstanceOf(obj, state.clsMatchOr)) {
            return obj2MatchOr(obj, sourceRange);
        }
        throw unexpectedNodeType(AstState.T_T_PATTERN, obj);
    }

    PatternTy.MatchValue obj2MatchValue(Object obj, SourceRange sourceRange) {
        ExprTy value = lookupAndConvert(obj, AstState.T_F_VALUE, AstState.T_C_MATCHVALUE, this::obj2ExprTy, true);
        return new PatternTy.MatchValue(value, sourceRange);
    }

    PatternTy.MatchSingleton obj2MatchSingleton(Object obj, SourceRange sourceRange) {
        ConstantValue value = lookupAndConvert(obj, AstState.T_F_VALUE, AstState.T_C_MATCHSINGLETON, this::obj2ConstantValue, true);
        return new PatternTy.MatchSingleton(value, sourceRange);
    }

    PatternTy.MatchSequence obj2MatchSequence(Object obj, SourceRange sourceRange) {
        PatternTy[] patterns = lookupAndConvertSequence(obj, AstState.T_F_PATTERNS, AstState.T_C_MATCHSEQUENCE, this::obj2PatternTy, PatternTy[]::new);
        return new PatternTy.MatchSequence(patterns, sourceRange);
    }

    PatternTy.MatchMapping obj2MatchMapping(Object obj, SourceRange sourceRange) {
        ExprTy[] keys = lookupAndConvertSequence(obj, AstState.T_F_KEYS, AstState.T_C_MATCHMAPPING, this::obj2ExprTy, ExprTy[]::new);
        PatternTy[] patterns = lookupAndConvertSequence(obj, AstState.T_F_PATTERNS, AstState.T_C_MATCHMAPPING, this::obj2PatternTy, PatternTy[]::new);
        String rest = lookupAndConvert(obj, AstState.T_F_REST, AstState.T_C_MATCHMAPPING, this::obj2identifier, false);
        return new PatternTy.MatchMapping(keys, patterns, rest, sourceRange);
    }

    PatternTy.MatchClass obj2MatchClass(Object obj, SourceRange sourceRange) {
        ExprTy cls = lookupAndConvert(obj, AstState.T_F_CLS, AstState.T_C_MATCHCLASS, this::obj2ExprTy, true);
        PatternTy[] patterns = lookupAndConvertSequence(obj, AstState.T_F_PATTERNS, AstState.T_C_MATCHCLASS, this::obj2PatternTy, PatternTy[]::new);
        String[] kwdAttrs = lookupAndConvertSequence(obj, AstState.T_F_KWD_ATTRS, AstState.T_C_MATCHCLASS, this::obj2identifier, String[]::new);
        PatternTy[] kwdPatterns = lookupAndConvertSequence(obj, AstState.T_F_KWD_PATTERNS, AstState.T_C_MATCHCLASS, this::obj2PatternTy, PatternTy[]::new);
        return new PatternTy.MatchClass(cls, patterns, kwdAttrs, kwdPatterns, sourceRange);
    }

    PatternTy.MatchStar obj2MatchStar(Object obj, SourceRange sourceRange) {
        String name = lookupAndConvert(obj, AstState.T_F_NAME, AstState.T_C_MATCHSTAR, this::obj2identifier, false);
        return new PatternTy.MatchStar(name, sourceRange);
    }

    PatternTy.MatchAs obj2MatchAs(Object obj, SourceRange sourceRange) {
        PatternTy pattern = lookupAndConvert(obj, AstState.T_F_PATTERN, AstState.T_C_MATCHAS, this::obj2PatternTy, false);
        String name = lookupAndConvert(obj, AstState.T_F_NAME, AstState.T_C_MATCHAS, this::obj2identifier, false);
        return new PatternTy.MatchAs(pattern, name, sourceRange);
    }

    PatternTy.MatchOr obj2MatchOr(Object obj, SourceRange sourceRange) {
        PatternTy[] patterns = lookupAndConvertSequence(obj, AstState.T_F_PATTERNS, AstState.T_C_MATCHOR, this::obj2PatternTy, PatternTy[]::new);
        return new PatternTy.MatchOr(patterns, sourceRange);
    }

    TypeIgnoreTy obj2TypeIgnoreTy(Object obj) {
        if (obj == PNone.NONE) {
            return null;
        }
        if (isInstanceOf(obj, state.clsTypeIgnore)) {
            return obj2TypeIgnore(obj);
        }
        throw unexpectedNodeType(AstState.T_T_TYPE_IGNORE, obj);
    }

    TypeIgnoreTy.TypeIgnore obj2TypeIgnore(Object obj) {
        int lineNo = lookupAndConvertInt(obj, AstState.T_F_LINENO, AstState.T_C_TYPEIGNORE, true);
        Object tag = lookupAndConvert(obj, AstState.T_F_TAG, AstState.T_C_TYPEIGNORE, this::obj2string, true);
        SourceRange sourceRange = SourceRange.ARTIFICIAL_RANGE;
        return new TypeIgnoreTy.TypeIgnore(lineNo, tag, sourceRange);
    }
}
