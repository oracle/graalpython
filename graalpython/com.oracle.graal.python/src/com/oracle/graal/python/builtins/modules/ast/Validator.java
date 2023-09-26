/*
 * Copyright (c) 2022, 2023, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.builtins.modules.ast;

import static com.oracle.graal.python.builtins.modules.ast.AstState.T_C_ASSIGN;
import static com.oracle.graal.python.builtins.modules.ast.AstState.T_C_ASYNCFOR;
import static com.oracle.graal.python.builtins.modules.ast.AstState.T_C_ASYNCFUNCTIONDEF;
import static com.oracle.graal.python.builtins.modules.ast.AstState.T_C_ASYNCWITH;
import static com.oracle.graal.python.builtins.modules.ast.AstState.T_C_CLASSDEF;
import static com.oracle.graal.python.builtins.modules.ast.AstState.T_C_DELETE;
import static com.oracle.graal.python.builtins.modules.ast.AstState.T_C_EXCEPTHANDLER;
import static com.oracle.graal.python.builtins.modules.ast.AstState.T_C_FOR;
import static com.oracle.graal.python.builtins.modules.ast.AstState.T_C_FUNCTIONDEF;
import static com.oracle.graal.python.builtins.modules.ast.AstState.T_C_GLOBAL;
import static com.oracle.graal.python.builtins.modules.ast.AstState.T_C_IF;
import static com.oracle.graal.python.builtins.modules.ast.AstState.T_C_IMPORT;
import static com.oracle.graal.python.builtins.modules.ast.AstState.T_C_IMPORTFROM;
import static com.oracle.graal.python.builtins.modules.ast.AstState.T_C_MATCH;
import static com.oracle.graal.python.builtins.modules.ast.AstState.T_C_NONLOCAL;
import static com.oracle.graal.python.builtins.modules.ast.AstState.T_C_TRY;
import static com.oracle.graal.python.builtins.modules.ast.AstState.T_C_TRYSTAR;
import static com.oracle.graal.python.builtins.modules.ast.AstState.T_C_WHILE;
import static com.oracle.graal.python.builtins.modules.ast.AstState.T_C_WITH;
import static com.oracle.graal.python.builtins.modules.ast.AstState.T_F_BODY;
import static com.oracle.graal.python.builtins.modules.ast.AstState.T_F_CASES;
import static com.oracle.graal.python.builtins.modules.ast.AstState.T_F_ITEMS;
import static com.oracle.graal.python.builtins.modules.ast.AstState.T_F_NAMES;
import static com.oracle.graal.python.builtins.modules.ast.AstState.T_F_TARGETS;
import static com.oracle.graal.python.builtins.modules.ast.AstState.T_T_MATCH_CASE;
import static com.oracle.graal.python.pegparser.sst.ExprContextTy.Del;
import static com.oracle.graal.python.pegparser.sst.ExprContextTy.Load;
import static com.oracle.graal.python.pegparser.sst.ExprContextTy.Store;

import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.pegparser.sst.AliasTy;
import com.oracle.graal.python.pegparser.sst.ArgTy;
import com.oracle.graal.python.pegparser.sst.ArgumentsTy;
import com.oracle.graal.python.pegparser.sst.ComprehensionTy;
import com.oracle.graal.python.pegparser.sst.ConstantValue;
import com.oracle.graal.python.pegparser.sst.ConstantValue.Kind;
import com.oracle.graal.python.pegparser.sst.ExceptHandlerTy;
import com.oracle.graal.python.pegparser.sst.ExprContextTy;
import com.oracle.graal.python.pegparser.sst.ExprTy;
import com.oracle.graal.python.pegparser.sst.ExprTy.UnaryOp;
import com.oracle.graal.python.pegparser.sst.KeywordTy;
import com.oracle.graal.python.pegparser.sst.MatchCaseTy;
import com.oracle.graal.python.pegparser.sst.ModTy;
import com.oracle.graal.python.pegparser.sst.OperatorTy;
import com.oracle.graal.python.pegparser.sst.PatternTy;
import com.oracle.graal.python.pegparser.sst.SSTreeVisitor;
import com.oracle.graal.python.pegparser.sst.StmtTy;
import com.oracle.graal.python.pegparser.sst.TypeIgnoreTy;
import com.oracle.graal.python.pegparser.sst.UnaryOpTy;
import com.oracle.graal.python.pegparser.sst.WithItemTy;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.truffle.api.strings.TruffleString;

final class Validator implements SSTreeVisitor<Void> {

    private static final String[] FORBIDDEN_NAMES = {"None", "True", "False"};

    private Validator() {
    }

    //////////////////////////////
    // ModTy validation
    //////////////////////////////

    // Equivalent of _PyAST_Validate - entry point of the validation
    static void validateMod(ModTy mod) {
        // TODO recursion checks
        mod.accept(new Validator());
    }

    @Override
    public Void visit(ModTy.Module node) {
        validateStmts(node.body);
        return null;
    }

    @Override
    public Void visit(ModTy.Interactive node) {
        validateStmts(node.body);
        return null;
    }

    @Override
    public Void visit(ModTy.Expression node) {
        validateExpr(node.body, Load);
        return null;
    }

    @Override
    public Void visit(ModTy.FunctionType node) {
        validateExprs(node.argTypes, Load, false);
        validateExpr(node.returns, Load);
        return null;
    }

    //////////////////////////////
    // StmtTy validation
    //////////////////////////////

    // Equivalent of validate_stmt
    private void validateStmt(StmtTy stmt) {
        // TODO recursion checks
        stmt.accept(this);
    }

    @Override
    public Void visit(StmtTy.FunctionDef node) {
        validateBody(node.body, T_C_FUNCTIONDEF);
        visit(node.args);
        validateExprs(node.decoratorList, Load, false);
        if (node.returns != null) {
            validateExpr(node.returns, Load);
        }
        return null;
    }

    @Override
    public Void visit(StmtTy.ClassDef node) {
        validateBody(node.body, T_C_CLASSDEF);
        validateExprs(node.bases, Load, false);
        validateKeywords(node.keywords);
        validateExprs(node.decoratorList, Load, false);
        return null;
    }

    @Override
    public Void visit(StmtTy.Return node) {
        if (node.value != null) {
            validateExpr(node.value, Load);
        }
        return null;
    }

    @Override
    public Void visit(StmtTy.Delete node) {
        validateAssignList(node.targets, Del);
        return null;
    }

    @Override
    public Void visit(StmtTy.Assign node) {
        validateAssignList(node.targets, Store);
        validateExpr(node.value, Load);
        return null;
    }

    @Override
    public Void visit(StmtTy.AugAssign node) {
        validateExpr(node.target, Store);
        validateExpr(node.value, Load);
        return null;
    }

    @Override
    public Void visit(StmtTy.AnnAssign node) {
        if (!(node.target instanceof ExprTy.Name) && node.isSimple) {
            throw raiseTypeError(ErrorMessages.ANN_ASSIGN_WITH_SIMPLE_NON_NAME_TARGET);
        }
        validateExpr(node.target, Store);
        if (node.value != null) {
            validateExpr(node.value, Load);
        }
        validateExpr(node.annotation, Load);
        return null;
    }

    @Override
    public Void visit(StmtTy.For node) {
        validateExpr(node.target, Store);
        validateExpr(node.iter, Load);
        validateBody(node.body, T_C_FOR);
        validateStmts(node.orElse);
        return null;
    }

    @Override
    public Void visit(StmtTy.AsyncFor node) {
        validateExpr(node.target, Store);
        validateExpr(node.iter, Load);
        validateBody(node.body, T_C_ASYNCFOR);
        validateStmts(node.orElse);
        return null;
    }

    @Override
    public Void visit(StmtTy.While node) {
        validateExpr(node.test, Load);
        validateBody(node.body, T_C_WHILE);
        validateStmts(node.orElse);
        return null;
    }

    @Override
    public Void visit(StmtTy.If node) {
        validateExpr(node.test, Load);
        validateBody(node.body, T_C_IF);
        validateStmts(node.orElse);
        return null;
    }

    @Override
    public Void visit(StmtTy.With node) {
        validateNonEmptySeq(node.items, T_F_ITEMS, T_C_WITH);
        for (WithItemTy item : node.items) {
            visit(item);
        }
        validateBody(node.body, T_C_WITH);
        return null;
    }

    @Override
    public Void visit(StmtTy.AsyncWith node) {
        validateNonEmptySeq(node.items, T_F_ITEMS, T_C_ASYNCWITH);
        for (WithItemTy item : node.items) {
            visit(item);
        }
        validateBody(node.body, T_C_ASYNCWITH);
        return null;
    }

    @Override
    public Void visit(StmtTy.Match node) {
        validateExpr(node.subject, Load);
        validateNonEmptySeq(node.cases, T_F_CASES, T_C_MATCH);
        for (MatchCaseTy matchCase : node.cases) {
            visit(matchCase);
        }
        return null;
    }

    @Override
    public Void visit(StmtTy.Raise node) {
        if (node.exc != null) {
            validateExpr(node.exc, Load);
            if (node.cause != null) {
                validateExpr(node.cause, Load);
            }
        } else if (node.cause != null) {
            throw raiseValueError(ErrorMessages.RAISE_WITH_CAUSE_BUT_NO_EXCEPTION);
        }
        return null;
    }

    @Override
    public Void visit(StmtTy.Try node) {
        validateBody(node.body, T_C_TRY);
        if (seqLen(node.handlers) == 0 && seqLen(node.finalBody) == 0) {
            throw raiseValueError(ErrorMessages.TRY_HAS_NEITHER_EXCEPT_HANDLERS_NOR_FINALBODY);
        }
        if (seqLen(node.handlers) == 0 && seqLen(node.orElse) != 0) {
            throw raiseValueError(ErrorMessages.TRY_HAS_ORELSE_BUT_NO_EXCEPT_HANDLERS);
        }
        if (node.handlers != null) {
            for (ExceptHandlerTy handler : node.handlers) {
                handler.accept(this);
            }
        }
        if (seqLen(node.finalBody) != 0) {
            validateStmts(node.finalBody);
        }
        if (seqLen(node.orElse) != 0) {
            validateStmts(node.orElse);
        }
        return null;
    }

    @Override
    public Void visit(StmtTy.TryStar node) {
        validateBody(node.body, T_C_TRYSTAR);
        if (seqLen(node.handlers) == 0 && seqLen(node.finalBody) == 0) {
            throw raiseValueError(ErrorMessages.TRYSTAR_HAS_NEITHER_EXCEPT_HANDLERS_NOR_FINALBODY);
        }
        if (seqLen(node.handlers) == 0 && seqLen(node.orElse) != 0) {
            throw raiseValueError(ErrorMessages.TRYSTAR_HAS_ORELSE_BUT_NO_EXCEPT_HANDLERS);
        }
        if (node.handlers != null) {
            for (ExceptHandlerTy handler : node.handlers) {
                handler.accept(this);
            }
        }
        if (seqLen(node.finalBody) != 0) {
            validateStmts(node.finalBody);
        }
        if (seqLen(node.orElse) != 0) {
            validateStmts(node.orElse);
        }
        return null;
    }

    @Override
    public Void visit(StmtTy.Assert node) {
        validateExpr(node.test, Load);
        if (node.msg != null) {
            validateExpr(node.msg, Load);
        }
        return null;
    }

    @Override
    public Void visit(StmtTy.Import node) {
        validateNonEmptySeq(node.names, T_F_NAMES, T_C_IMPORT);
        return null;
    }

    @Override
    public Void visit(StmtTy.ImportFrom node) {
        if (node.level < 0) {
            throw raiseValueError(ErrorMessages.NEGATIVE_IMPORT_FROM_LEVEL);
        }
        validateNonEmptySeq(node.names, T_F_NAMES, T_C_IMPORTFROM);
        return null;
    }

    @Override
    public Void visit(StmtTy.Global node) {
        validateNonEmptySeq(node.names, T_F_NAMES, T_C_GLOBAL);
        return null;
    }

    @Override
    public Void visit(StmtTy.Nonlocal node) {
        validateNonEmptySeq(node.names, T_F_NAMES, T_C_NONLOCAL);
        return null;
    }

    @Override
    public Void visit(StmtTy.Expr node) {
        validateExpr(node.value, Load);
        return null;
    }

    @Override
    public Void visit(StmtTy.AsyncFunctionDef node) {
        validateBody(node.body, T_C_ASYNCFUNCTIONDEF);
        visit(node.args);
        validateExprs(node.decoratorList, Load, false);
        if (node.returns != null) {
            validateExpr(node.returns, Load);
        }
        return null;
    }

    @Override
    public Void visit(StmtTy.Pass node) {
        return null;
    }

    @Override
    public Void visit(StmtTy.Break node) {
        return null;
    }

    @Override
    public Void visit(StmtTy.Continue node) {
        return null;
    }

    //////////////////////////////
    // ExprTy validation
    //////////////////////////////

    // Equivalent of validate_expr
    private void validateExpr(ExprTy expr, ExprContextTy context) {
        assert context != null;

        // TODO recursion checks

        // CPython uses two switch(exp->kind) statements. We combine them in a single visitor, but
        // each ExprTy visitor must call one of the checkContext() methods exactly
        // once, and it must do so before any recursive calls to validateExpr.

        assert expectedContext == null : "recursive validateExpr() before a call to checkContext()";
        expectedContext = context;
        expr.accept(this);
        assert expectedContext == null : "checkContext() not called by " + expr.getClass().getSimpleName() + " visitor";
    }

    private ExprContextTy expectedContext;

    // used by visitors of ExprTy nodes that have a 'context' field
    private void checkContext(ExprContextTy actualContext) {
        assert expectedContext != null : "checkContext() called more than once";
        if (actualContext != expectedContext) {
            throw raiseValueError(ErrorMessages.EXPRESSION_MUST_HAVE_S_CONTEXT_BUT_HAS_S_INSTEAD, expectedContext, actualContext);
        }
        expectedContext = null;
    }

    // used by visitors of ExprTy nodes that don't have any 'context' field
    private void checkContext() {
        assert expectedContext != null : "checkContext() called more than once";
        if (expectedContext != Load) {
            throw raiseValueError(ErrorMessages.EXPRESSION_WHICH_CANT_BE_ASSIGNED_TO_IN_S_CONTEXT, expectedContext);
        }
        expectedContext = null;
    }

    @Override
    public Void visit(ExprTy.BoolOp node) {
        checkContext();
        if (seqLen(node.values) < 2) {
            throw raiseValueError(ErrorMessages.BOOL_OP_WITH_LESS_THAN_2_VALUES);
        }
        validateExprs(node.values, Load, false);
        return null;
    }

    @Override
    public Void visit(ExprTy.BinOp node) {
        checkContext();
        validateExpr(node.left, Load);
        validateExpr(node.right, Load);
        return null;
    }

    @Override
    public Void visit(ExprTy.UnaryOp node) {
        checkContext();
        validateExpr(node.operand, Load);
        return null;
    }

    @Override
    public Void visit(ExprTy.Lambda node) {
        checkContext();
        visit(node.args);
        validateExpr(node.body, Load);
        return null;
    }

    @Override
    public Void visit(ExprTy.IfExp node) {
        checkContext();
        validateExpr(node.test, Load);
        validateExpr(node.body, Load);
        validateExpr(node.orElse, Load);
        return null;
    }

    @Override
    public Void visit(ExprTy.Dict node) {
        checkContext();
        if (seqLen(node.keys) != seqLen(node.values)) {
            throw raiseValueError(ErrorMessages.DICT_DOESNT_HAVE_THE_SAME_NUMBER_OF_KEYS_AS_VALUES);
        }
        validateExprs(node.keys, Load, true);
        validateExprs(node.values, Load, false);
        return null;
    }

    @Override
    public Void visit(ExprTy.Set node) {
        checkContext();
        validateExprs(node.elements, Load, false);
        return null;
    }

    @Override
    public Void visit(ExprTy.ListComp node) {
        checkContext();
        validateComprehension(node.generators);
        validateExpr(node.element, Load);
        return null;
    }

    @Override
    public Void visit(ExprTy.SetComp node) {
        checkContext();
        validateComprehension(node.generators);
        validateExpr(node.element, Load);
        return null;
    }

    @Override
    public Void visit(ExprTy.GeneratorExp node) {
        checkContext();
        validateComprehension(node.generators);
        validateExpr(node.element, Load);
        return null;
    }

    @Override
    public Void visit(ExprTy.DictComp node) {
        checkContext();
        validateComprehension(node.generators);
        validateExpr(node.key, Load);
        validateExpr(node.value, Load);
        return null;
    }

    @Override
    public Void visit(ExprTy.Yield node) {
        checkContext();
        if (node.value != null) {
            validateExpr(node.value, Load);
        }
        return null;
    }

    @Override
    public Void visit(ExprTy.YieldFrom node) {
        checkContext();
        validateExpr(node.value, Load);
        return null;
    }

    @Override
    public Void visit(ExprTy.Await node) {
        checkContext();
        validateExpr(node.value, Load);
        return null;
    }

    @Override
    public Void visit(ExprTy.Compare node) {
        checkContext();
        if (seqLen(node.comparators) == 0) {
            throw raiseValueError(ErrorMessages.COMPARE_WITH_NO_COMPARATORS);
        }
        if (seqLen(node.comparators) != seqLen(node.ops)) {
            throw raiseValueError(ErrorMessages.COMPARE_HAS_A_DIFFERENT_NUMBER_OF_COMPARATORS_AND_OPERANDS);
        }
        validateExprs(node.comparators, Load, false);
        validateExpr(node.left, Load);
        return null;
    }

    @Override
    public Void visit(ExprTy.Call node) {
        checkContext();
        validateExpr(node.func, Load);
        validateExprs(node.args, Load, false);
        validateKeywords(node.keywords);
        return null;
    }

    @Override
    public Void visit(ExprTy.Constant node) {
        checkContext();
        validateConstant(node.value);
        return null;
    }

    @Override
    public Void visit(ExprTy.JoinedStr node) {
        checkContext();
        validateExprs(node.values, Load, false);
        return null;
    }

    @Override
    public Void visit(ExprTy.FormattedValue node) {
        checkContext();
        validateExpr(node.value, Load);
        if (node.formatSpec != null) {
            validateExpr(node.formatSpec, Load);
        }
        return null;
    }

    @Override
    public Void visit(ExprTy.Attribute node) {
        checkContext(node.context);
        validateExpr(node.value, Load);
        return null;
    }

    @Override
    public Void visit(ExprTy.Subscript node) {
        checkContext(node.context);
        validateExpr(node.slice, Load);
        validateExpr(node.value, Load);
        return null;
    }

    @Override
    public Void visit(ExprTy.Starred node) {
        checkContext(node.context);
        validateExpr(node.value, node.context);
        return null;
    }

    @Override
    public Void visit(ExprTy.Slice node) {
        checkContext();
        if (node.lower != null) {
            validateExpr(node.lower, Load);
        }
        if (node.upper != null) {
            validateExpr(node.upper, Load);
        }
        if (node.step != null) {
            validateExpr(node.step, Load);
        }
        return null;
    }

    @Override
    public Void visit(ExprTy.List node) {
        checkContext(node.context);
        validateExprs(node.elements, node.context, false);
        return null;
    }

    @Override
    public Void visit(ExprTy.Tuple node) {
        checkContext(node.context);
        validateExprs(node.elements, node.context, false);
        return null;
    }

    @Override
    public Void visit(ExprTy.NamedExpr node) {
        checkContext();
        validateExpr(node.value, Load);
        return null;
    }

    @Override
    public Void visit(ExprTy.Name node) {
        validateName(node.id);
        checkContext(node.context);
        return null;
    }

    //////////////////////////////
    // PatternTy validation
    //////////////////////////////

    boolean isStarPatternOk;

    // Equivalent of validate_pattern
    private void validatePattern(PatternTy pattern, boolean starOk) {
        boolean prevStarOk = isStarPatternOk;
        isStarPatternOk = starOk;
        pattern.accept(this);
        isStarPatternOk = prevStarOk;
    }

    // Equivalent of ensure_literal_number
    private static boolean ensureLiteralNumber(ExprTy.Constant expr, boolean allowReal, boolean allowImaginary) {
        return (allowReal && expr.value.kind == Kind.DOUBLE) || (allowReal && expr.value.kind == Kind.LONG) || (allowImaginary && expr.value.kind == Kind.COMPLEX);
    }

    // Equivalent of ensure_literal_negative
    private static boolean ensureLiteralNegative(UnaryOp expr, boolean allowImaginary) {
        if (expr.op != UnaryOpTy.USub) {
            return false;
        }
        if (!(expr.operand instanceof ExprTy.Constant)) {
            return false;
        }
        return ensureLiteralNumber((ExprTy.Constant) expr.operand, true, allowImaginary);
    }

    // Equivalent of ensure_literal_complex
    private static boolean ensureLiteralComplex(ExprTy.BinOp expr) {
        if (expr.op != OperatorTy.Add && expr.op != OperatorTy.Sub) {
            return false;
        }
        if (expr.left instanceof ExprTy.Constant) {
            if (!ensureLiteralNumber((ExprTy.Constant) expr.left, true, false)) {
                return false;
            }
        } else if (expr.left instanceof ExprTy.UnaryOp) {
            if (!ensureLiteralNegative((ExprTy.UnaryOp) expr.left, false)) {
                return false;
            }
        } else {
            return false;
        }
        if (expr.right instanceof ExprTy.Constant) {
            return ensureLiteralNumber((ExprTy.Constant) expr.right, false, true);
        } else {
            return false;
        }
    }

    // Equivalent of validate_pattern_match_value
    private void validatePatternMatchValue(ExprTy expr) {
        validateExpr(expr, Load);
        if (expr instanceof ExprTy.Constant) {
            ExprTy.Constant constantExpr = (ExprTy.Constant) expr;
            switch (constantExpr.value.kind) {
                case LONG:
                case DOUBLE:
                case BYTES:
                case COMPLEX:
                case RAW:
                    return;
            }
            throw raiseValueError(ErrorMessages.UNEXPECTED_CONSTANT_INSIDE_OF_A_LITERAL_PATTERN);
        }
        if (expr instanceof ExprTy.Attribute) {
            return;
        }
        if (expr instanceof ExprTy.UnaryOp) {
            if (ensureLiteralNegative((ExprTy.UnaryOp) expr, true)) {
                return;
            }
        }
        if (expr instanceof ExprTy.BinOp) {
            if (ensureLiteralComplex((ExprTy.BinOp) expr)) {
                return;
            }
        }
        if (expr instanceof ExprTy.JoinedStr) {
            return;
        }
        throw raiseValueError(ErrorMessages.PATTERNS_MAY_ONLY_MATCH_LITERALS_AND_ATTRIBUTE_LOOKUPS);
    }

    private static void validateCapture(String name) {
        if (name.equals("_")) {
            throw raiseValueError(ErrorMessages.CANT_CAPTURE_NAME_UNDERSCORE_IN_PATTERNS);
        }
        validateName(name);
    }

    @Override
    public Void visit(PatternTy.MatchValue node) {
        validatePatternMatchValue(node.value);
        return null;
    }

    @Override
    public Void visit(PatternTy.MatchSingleton node) {
        if (node.value.kind != Kind.NONE && node.value.kind != Kind.BOOLEAN) {
            throw raiseValueError(ErrorMessages.MATCH_SINGLETON_CAN_ONLY_CONTAIN_TRUE_FALSE_AND_NONE);
        }
        return null;
    }

    @Override
    public Void visit(PatternTy.MatchSequence node) {
        validatePatterns(node.patterns, true);
        return null;
    }

    @Override
    public Void visit(PatternTy.MatchMapping node) {
        if (seqLen(node.keys) != seqLen(node.patterns)) {
            throw raiseValueError(ErrorMessages.MATCH_MAPPING_DOESNT_HAVE_THE_SAME_NUMBER_OF_KEYS_AS_PATTERNS);
        }
        if (node.rest != null) {
            validateCapture(node.rest);
        }
        if (node.keys != null) {
            for (ExprTy key : node.keys) {
                if (key instanceof ExprTy.Constant) {
                    ConstantValue literal = ((ExprTy.Constant) key).value;
                    if (literal.kind == Kind.NONE || literal.kind == Kind.BOOLEAN) {
                        continue;
                    }
                }
                validatePatternMatchValue(key);
            }
        }
        validatePatterns(node.patterns, false);
        return null;
    }

    @Override
    public Void visit(PatternTy.MatchClass node) {
        if (seqLen(node.kwdAttrs) != seqLen(node.kwdPatterns)) {
            throw raiseValueError(ErrorMessages.MATCH_CLASS_DOESNT_HAVE_THE_SAME_NUMBER_OF_KEYWORD_ATTRIBUTES_AS_PATTERNS);
        }
        validateExpr(node.cls, Load);
        ExprTy cls = node.cls;
        while (cls instanceof ExprTy.Attribute) {
            cls = ((ExprTy.Attribute) cls).value;
        }
        if (!(cls instanceof ExprTy.Name)) {
            throw raiseValueError(ErrorMessages.MATCH_CLASS_CLS_FIELD_CAN_ONLY_CONTAIN_NAME_OR_ATTRIBUTE_NODES);
        }
        if (node.kwdAttrs != null) {
            for (String identifier : node.kwdAttrs) {
                validateName(identifier);
            }
        }
        validatePatterns(node.patterns, false);
        validatePatterns(node.kwdPatterns, false);
        return null;
    }

    @Override
    public Void visit(PatternTy.MatchStar node) {
        if (!isStarPatternOk) {
            throw raiseValueError(ErrorMessages.CANT_USE_MATCH_STAR_HERE);
        }
        if (node.name != null) {
            validateCapture(node.name);
        }
        return null;
    }

    @Override
    public Void visit(PatternTy.MatchAs node) {
        if (node.name != null) {
            validateCapture(node.name);
        }
        if (node.pattern != null) {
            if (node.name == null) {
                throw raiseValueError(ErrorMessages.MATCH_AS_MUST_SPECIFY_A_TARGET_NAME_IF_A_PATTERN_IS_GIVEN);
            }
            validatePattern(node.pattern, false);
        }
        return null;
    }

    @Override
    public Void visit(PatternTy.MatchOr node) {
        if (seqLen(node.patterns) < 2) {
            throw raiseValueError(ErrorMessages.MATCH_OR_REQUIRES_AT_LEAST_2_PATTERNS);
        }
        validatePatterns(node.patterns, false);
        return null;
    }

    @Override
    public Void visit(MatchCaseTy node) {
        validatePattern(node.pattern, false);
        if (node.guard != null) {
            validateExpr(node.guard, Load);
        }
        validateBody(node.body, T_T_MATCH_CASE);
        return null;
    }

    //////////////////////////////
    // Other visitors
    //////////////////////////////

    @Override
    public Void visit(ComprehensionTy node) {
        validateExpr(node.target, Store);
        validateExpr(node.iter, Load);
        validateExprs(node.ifs, Load, false);
        return null;
    }

    @Override
    public Void visit(ExceptHandlerTy.ExceptHandler node) {
        if (node.type != null) {
            validateExpr(node.type, Load);
        }
        validateBody(node.body, T_C_EXCEPTHANDLER);
        return null;
    }

    // Equivalent of validate_arguments
    @Override
    public Void visit(ArgumentsTy node) {
        validateArgs(node.posOnlyArgs);
        validateArgs(node.args);
        if (node.varArg != null && node.varArg.annotation != null) {
            validateExpr(node.varArg.annotation, Load);
        }
        validateArgs(node.kwOnlyArgs);
        if (node.kwArg != null && node.kwArg.annotation != null) {
            validateExpr(node.kwArg.annotation, Load);
        }
        if (seqLen(node.defaults) > seqLen(node.posOnlyArgs) + seqLen(node.args)) {
            throw raiseValueError(ErrorMessages.MORE_POSITIONAL_DEFAULTS_THAN_ARGS_ON_ARGUMENTS);
        }
        if (seqLen(node.kwDefaults) != seqLen(node.kwOnlyArgs)) {
            throw raiseValueError(ErrorMessages.LENGTH_OF_KWONLYARGS_IS_NOT_THE_SAME_AS_KW_DEFAULTS_ON_ARGUMENTS);
        }
        validateExprs(node.defaults, Load, false);
        validateExprs(node.kwDefaults, Load, true);
        return null;
    }

    @Override
    public Void visit(ArgTy node) {
        if (node.annotation != null) {
            validateExpr(node.annotation, Load);
        }
        return null;
    }

    @Override
    public Void visit(KeywordTy node) {
        validateExpr(node.value, Load);
        return null;
    }

    @Override
    public Void visit(AliasTy node) {
        return null;
    }

    @Override
    public Void visit(WithItemTy node) {
        validateExpr(node.contextExpr, Load);
        if (node.optionalVars != null) {
            validateExpr(node.optionalVars, Store);
        }
        return null;
    }

    @Override
    public Void visit(TypeIgnoreTy.TypeIgnore node) {
        return null;
    }

    //////////////////////////////
    // Validation of sequences
    //////////////////////////////

    // Equivalent of validate_stmts
    private void validateStmts(StmtTy[] stmts) {
        if (stmts == null) {
            return;
        }
        for (StmtTy stmt : stmts) {
            if (stmt != null) {
                validateStmt(stmt);
            } else {
                throw raiseValueError(ErrorMessages.NONE_DISALLOWED_IN_STATEMENT_LIST);
            }
        }
    }

    // Equivalent of validate_exprs
    private void validateExprs(ExprTy[] exprs, ExprContextTy exprContext, boolean allowNull) {
        if (exprs == null) {
            return;
        }
        for (ExprTy expr : exprs) {
            if (expr != null) {
                validateExpr(expr, exprContext);
            } else if (!allowNull) {
                throw raiseValueError(ErrorMessages.NONE_DISALLOWED_IN_EXPRESSION_LIST);
            }
        }
    }

    // Equivalent of validate_args
    private void validateArgs(ArgTy[] args) {
        if (args == null) {
            return;
        }
        for (ArgTy arg : args) {
            visit(arg);
        }
    }

    // Equivalent of validate_keywords
    private void validateKeywords(KeywordTy[] keywords) {
        if (keywords == null) {
            return;
        }
        for (KeywordTy kw : keywords) {
            visit(kw);
        }
    }

    // Equivalent of validate_patterns
    private void validatePatterns(PatternTy[] patterns, boolean starOk) {
        if (patterns == null) {
            return;
        }
        for (PatternTy pattern : patterns) {
            validatePattern(pattern, starOk);
        }
    }

    // Equivalent of validate_body
    private void validateBody(StmtTy[] body, TruffleString owner) {
        validateNonEmptySeq(body, T_F_BODY, owner);
        validateStmts(body);
    }

    // Equivalent of validate_assignlist
    private void validateAssignList(ExprTy[] targets, ExprContextTy ctx) {
        validateNonEmptySeq(targets, T_F_TARGETS, ctx == Del ? T_C_DELETE : T_C_ASSIGN);
        validateExprs(targets, ctx, false);
    }

    // Equivalent of _validate_nonempty_seq
    private static void validateNonEmptySeq(Object[] seq, TruffleString what, TruffleString owner) {
        if (seqLen(seq) == 0) {
            throw raiseValueError(ErrorMessages.EMPTY_S_ON_S, what, owner);
        }
    }

    // Equivalent of validate_comprehension
    private void validateComprehension(ComprehensionTy[] generators) {
        if (seqLen(generators) == 0) {
            throw raiseValueError(ErrorMessages.COMPREHENSION_WITH_NO_GENERATORS);
        }
        for (ComprehensionTy comp : generators) {
            visit(comp);
        }
    }

    //////////////////////////////
    // Helpers
    //////////////////////////////

    // Equivalent of asdl_seq_LEN
    private static int seqLen(Object[] seq) {
        return seq == null ? 0 : seq.length;
    }

    // Equivalent of validate_name
    private static void validateName(String id) {
        for (String f : FORBIDDEN_NAMES) {
            if (f.equals(id)) {
                throw raiseValueError(ErrorMessages.IDENTIFIER_FIELD_CANT_REPRESENT_S_CONSTANT, f);
            }
        }
    }

    // Equivalent of validate_constant
    private void validateConstant(@SuppressWarnings("unused") ConstantValue value) {
        // Already done in Obj2SstBase#obj2ConstantValue()
    }

    private static PException raiseValueError(TruffleString format, Object... args) {
        throw PRaiseNode.getUncached().raise(PythonBuiltinClassType.ValueError, format, args);
    }

    private static PException raiseTypeError(TruffleString format, Object... args) {
        throw PRaiseNode.getUncached().raise(PythonBuiltinClassType.TypeError, format, args);
    }
}
