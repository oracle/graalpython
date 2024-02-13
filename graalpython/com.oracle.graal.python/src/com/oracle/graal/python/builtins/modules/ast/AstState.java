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

import static com.oracle.graal.python.util.PythonUtils.toTruffleStringUncached;
import static com.oracle.graal.python.util.PythonUtils.tsLiteral;

import com.oracle.graal.python.builtins.objects.object.PythonObject;
import com.oracle.graal.python.builtins.objects.type.PythonBuiltinClass;
import com.oracle.graal.python.builtins.objects.type.PythonClass;
import com.oracle.truffle.api.strings.TruffleString;

final class AstState {

    static final TruffleString T_F_ANNOTATION = tsLiteral("annotation");
    static final TruffleString T_F_ARG = tsLiteral("arg");
    static final TruffleString T_F_ARGS = tsLiteral("args");
    static final TruffleString T_F_ARGTYPES = tsLiteral("argtypes");
    static final TruffleString T_F_ASNAME = tsLiteral("asname");
    static final TruffleString T_F_ATTR = tsLiteral("attr");
    static final TruffleString T_F_BASES = tsLiteral("bases");
    static final TruffleString T_F_BODY = tsLiteral("body");
    static final TruffleString T_F_CASES = tsLiteral("cases");
    static final TruffleString T_F_CAUSE = tsLiteral("cause");
    static final TruffleString T_F_CLS = tsLiteral("cls");
    static final TruffleString T_F_COL_OFFSET = tsLiteral("col_offset");
    static final TruffleString T_F_COMPARATORS = tsLiteral("comparators");
    static final TruffleString T_F_CONTEXT_EXPR = tsLiteral("context_expr");
    static final TruffleString T_F_CONVERSION = tsLiteral("conversion");
    static final TruffleString T_F_CTX = tsLiteral("ctx");
    static final TruffleString T_F_DECORATOR_LIST = tsLiteral("decorator_list");
    static final TruffleString T_F_DEFAULTS = tsLiteral("defaults");
    static final TruffleString T_F_ELT = tsLiteral("elt");
    static final TruffleString T_F_ELTS = tsLiteral("elts");
    static final TruffleString T_F_END_COL_OFFSET = tsLiteral("end_col_offset");
    static final TruffleString T_F_END_LINENO = tsLiteral("end_lineno");
    static final TruffleString T_F_EXC = tsLiteral("exc");
    static final TruffleString T_F_FINALBODY = tsLiteral("finalbody");
    static final TruffleString T_F_FORMAT_SPEC = tsLiteral("format_spec");
    static final TruffleString T_F_FUNC = tsLiteral("func");
    static final TruffleString T_F_GENERATORS = tsLiteral("generators");
    static final TruffleString T_F_GUARD = tsLiteral("guard");
    static final TruffleString T_F_HANDLERS = tsLiteral("handlers");
    static final TruffleString T_F_ID = tsLiteral("id");
    static final TruffleString T_F_IFS = tsLiteral("ifs");
    static final TruffleString T_F_IS_ASYNC = tsLiteral("is_async");
    static final TruffleString T_F_ITEMS = tsLiteral("items");
    static final TruffleString T_F_ITER = tsLiteral("iter");
    static final TruffleString T_F_KEY = tsLiteral("key");
    static final TruffleString T_F_KEYS = tsLiteral("keys");
    static final TruffleString T_F_KEYWORDS = tsLiteral("keywords");
    static final TruffleString T_F_KIND = tsLiteral("kind");
    static final TruffleString T_F_KW_DEFAULTS = tsLiteral("kw_defaults");
    static final TruffleString T_F_KWARG = tsLiteral("kwarg");
    static final TruffleString T_F_KWD_ATTRS = tsLiteral("kwd_attrs");
    static final TruffleString T_F_KWD_PATTERNS = tsLiteral("kwd_patterns");
    static final TruffleString T_F_KWONLYARGS = tsLiteral("kwonlyargs");
    static final TruffleString T_F_LEFT = tsLiteral("left");
    static final TruffleString T_F_LEVEL = tsLiteral("level");
    static final TruffleString T_F_LINENO = tsLiteral("lineno");
    static final TruffleString T_F_LOWER = tsLiteral("lower");
    static final TruffleString T_F_MODULE = tsLiteral("module");
    static final TruffleString T_F_MSG = tsLiteral("msg");
    static final TruffleString T_F_NAME = tsLiteral("name");
    static final TruffleString T_F_NAMES = tsLiteral("names");
    static final TruffleString T_F_OP = tsLiteral("op");
    static final TruffleString T_F_OPERAND = tsLiteral("operand");
    static final TruffleString T_F_OPS = tsLiteral("ops");
    static final TruffleString T_F_OPTIONAL_VARS = tsLiteral("optional_vars");
    static final TruffleString T_F_ORELSE = tsLiteral("orelse");
    static final TruffleString T_F_PATTERN = tsLiteral("pattern");
    static final TruffleString T_F_PATTERNS = tsLiteral("patterns");
    static final TruffleString T_F_POSONLYARGS = tsLiteral("posonlyargs");
    static final TruffleString T_F_REST = tsLiteral("rest");
    static final TruffleString T_F_RETURNS = tsLiteral("returns");
    static final TruffleString T_F_RIGHT = tsLiteral("right");
    static final TruffleString T_F_SIMPLE = tsLiteral("simple");
    static final TruffleString T_F_SLICE = tsLiteral("slice");
    static final TruffleString T_F_STEP = tsLiteral("step");
    static final TruffleString T_F_SUBJECT = tsLiteral("subject");
    static final TruffleString T_F_TAG = tsLiteral("tag");
    static final TruffleString T_F_TARGET = tsLiteral("target");
    static final TruffleString T_F_TARGETS = tsLiteral("targets");
    static final TruffleString T_F_TEST = tsLiteral("test");
    static final TruffleString T_F_TYPE = tsLiteral("type");
    static final TruffleString T_F_TYPE_COMMENT = tsLiteral("type_comment");
    static final TruffleString T_F_TYPE_IGNORES = tsLiteral("type_ignores");
    static final TruffleString T_F_UPPER = tsLiteral("upper");
    static final TruffleString T_F_VALUE = tsLiteral("value");
    static final TruffleString T_F_VALUES = tsLiteral("values");
    static final TruffleString T_F_VARARG = tsLiteral("vararg");
    static final TruffleString T_C_ADD = tsLiteral("Add");
    static final TruffleString T_C_AND = tsLiteral("And");
    static final TruffleString T_C_ANNASSIGN = tsLiteral("AnnAssign");
    static final TruffleString T_C_ASSERT = tsLiteral("Assert");
    static final TruffleString T_C_ASSIGN = tsLiteral("Assign");
    static final TruffleString T_C_ASYNCFOR = tsLiteral("AsyncFor");
    static final TruffleString T_C_ASYNCFUNCTIONDEF = tsLiteral("AsyncFunctionDef");
    static final TruffleString T_C_ASYNCWITH = tsLiteral("AsyncWith");
    static final TruffleString T_C_ATTRIBUTE = tsLiteral("Attribute");
    static final TruffleString T_C_AUGASSIGN = tsLiteral("AugAssign");
    static final TruffleString T_C_AWAIT = tsLiteral("Await");
    static final TruffleString T_C_BINOP = tsLiteral("BinOp");
    static final TruffleString T_C_BITAND = tsLiteral("BitAnd");
    static final TruffleString T_C_BITOR = tsLiteral("BitOr");
    static final TruffleString T_C_BITXOR = tsLiteral("BitXor");
    static final TruffleString T_C_BOOLOP = tsLiteral("BoolOp");
    static final TruffleString T_C_BREAK = tsLiteral("Break");
    static final TruffleString T_C_CALL = tsLiteral("Call");
    static final TruffleString T_C_CLASSDEF = tsLiteral("ClassDef");
    static final TruffleString T_C_COMPARE = tsLiteral("Compare");
    static final TruffleString T_C_CONSTANT = tsLiteral("Constant");
    static final TruffleString T_C_CONTINUE = tsLiteral("Continue");
    static final TruffleString T_C_DEL = tsLiteral("Del");
    static final TruffleString T_C_DELETE = tsLiteral("Delete");
    static final TruffleString T_C_DICT = tsLiteral("Dict");
    static final TruffleString T_C_DICTCOMP = tsLiteral("DictComp");
    static final TruffleString T_C_DIV = tsLiteral("Div");
    static final TruffleString T_C_EQ = tsLiteral("Eq");
    static final TruffleString T_C_EXCEPTHANDLER = tsLiteral("ExceptHandler");
    static final TruffleString T_C_EXPR = tsLiteral("Expr");
    static final TruffleString T_C_EXPRESSION = tsLiteral("Expression");
    static final TruffleString T_C_FLOORDIV = tsLiteral("FloorDiv");
    static final TruffleString T_C_FOR = tsLiteral("For");
    static final TruffleString T_C_FORMATTEDVALUE = tsLiteral("FormattedValue");
    static final TruffleString T_C_FUNCTIONDEF = tsLiteral("FunctionDef");
    static final TruffleString T_C_FUNCTIONTYPE = tsLiteral("FunctionType");
    static final TruffleString T_C_GENERATOREXP = tsLiteral("GeneratorExp");
    static final TruffleString T_C_GLOBAL = tsLiteral("Global");
    static final TruffleString T_C_GT = tsLiteral("Gt");
    static final TruffleString T_C_GTE = tsLiteral("GtE");
    static final TruffleString T_C_IF = tsLiteral("If");
    static final TruffleString T_C_IFEXP = tsLiteral("IfExp");
    static final TruffleString T_C_IMPORT = tsLiteral("Import");
    static final TruffleString T_C_IMPORTFROM = tsLiteral("ImportFrom");
    static final TruffleString T_C_IN = tsLiteral("In");
    static final TruffleString T_C_INTERACTIVE = tsLiteral("Interactive");
    static final TruffleString T_C_INVERT = tsLiteral("Invert");
    static final TruffleString T_C_IS = tsLiteral("Is");
    static final TruffleString T_C_ISNOT = tsLiteral("IsNot");
    static final TruffleString T_C_JOINEDSTR = tsLiteral("JoinedStr");
    static final TruffleString T_C_LSHIFT = tsLiteral("LShift");
    static final TruffleString T_C_LAMBDA = tsLiteral("Lambda");
    static final TruffleString T_C_LIST = tsLiteral("List");
    static final TruffleString T_C_LISTCOMP = tsLiteral("ListComp");
    static final TruffleString T_C_LOAD = tsLiteral("Load");
    static final TruffleString T_C_LT = tsLiteral("Lt");
    static final TruffleString T_C_LTE = tsLiteral("LtE");
    static final TruffleString T_C_MATMULT = tsLiteral("MatMult");
    static final TruffleString T_C_MATCH = tsLiteral("Match");
    static final TruffleString T_C_MATCHAS = tsLiteral("MatchAs");
    static final TruffleString T_C_MATCHCLASS = tsLiteral("MatchClass");
    static final TruffleString T_C_MATCHMAPPING = tsLiteral("MatchMapping");
    static final TruffleString T_C_MATCHOR = tsLiteral("MatchOr");
    static final TruffleString T_C_MATCHSEQUENCE = tsLiteral("MatchSequence");
    static final TruffleString T_C_MATCHSINGLETON = tsLiteral("MatchSingleton");
    static final TruffleString T_C_MATCHSTAR = tsLiteral("MatchStar");
    static final TruffleString T_C_MATCHVALUE = tsLiteral("MatchValue");
    static final TruffleString T_C_MOD = tsLiteral("Mod");
    static final TruffleString T_C_MODULE = tsLiteral("Module");
    static final TruffleString T_C_MULT = tsLiteral("Mult");
    static final TruffleString T_C_NAME = tsLiteral("Name");
    static final TruffleString T_C_NAMEDEXPR = tsLiteral("NamedExpr");
    static final TruffleString T_C_NONLOCAL = tsLiteral("Nonlocal");
    static final TruffleString T_C_NOT = tsLiteral("Not");
    static final TruffleString T_C_NOTEQ = tsLiteral("NotEq");
    static final TruffleString T_C_NOTIN = tsLiteral("NotIn");
    static final TruffleString T_C_OR = tsLiteral("Or");
    static final TruffleString T_C_PASS = tsLiteral("Pass");
    static final TruffleString T_C_POW = tsLiteral("Pow");
    static final TruffleString T_C_RSHIFT = tsLiteral("RShift");
    static final TruffleString T_C_RAISE = tsLiteral("Raise");
    static final TruffleString T_C_RETURN = tsLiteral("Return");
    static final TruffleString T_C_SET = tsLiteral("Set");
    static final TruffleString T_C_SETCOMP = tsLiteral("SetComp");
    static final TruffleString T_C_SLICE = tsLiteral("Slice");
    static final TruffleString T_C_STARRED = tsLiteral("Starred");
    static final TruffleString T_C_STORE = tsLiteral("Store");
    static final TruffleString T_C_SUB = tsLiteral("Sub");
    static final TruffleString T_C_SUBSCRIPT = tsLiteral("Subscript");
    static final TruffleString T_C_TRY = tsLiteral("Try");
    static final TruffleString T_C_TRYSTAR = tsLiteral("TryStar");
    static final TruffleString T_C_TUPLE = tsLiteral("Tuple");
    static final TruffleString T_C_TYPEIGNORE = tsLiteral("TypeIgnore");
    static final TruffleString T_C_UADD = tsLiteral("UAdd");
    static final TruffleString T_C_USUB = tsLiteral("USub");
    static final TruffleString T_C_UNARYOP = tsLiteral("UnaryOp");
    static final TruffleString T_C_WHILE = tsLiteral("While");
    static final TruffleString T_C_WITH = tsLiteral("With");
    static final TruffleString T_C_YIELD = tsLiteral("Yield");
    static final TruffleString T_C_YIELDFROM = tsLiteral("YieldFrom");
    static final TruffleString T_T_ALIAS = tsLiteral("alias");
    static final TruffleString T_T_ARG = tsLiteral("arg");
    static final TruffleString T_T_ARGUMENTS = tsLiteral("arguments");
    static final TruffleString T_T_BOOLOP = tsLiteral("boolop");
    static final TruffleString T_T_CMPOP = tsLiteral("cmpop");
    static final TruffleString T_T_COMPREHENSION = tsLiteral("comprehension");
    static final TruffleString T_T_EXCEPTHANDLER = tsLiteral("excepthandler");
    static final TruffleString T_T_EXPR = tsLiteral("expr");
    static final TruffleString T_T_EXPR_CONTEXT = tsLiteral("expr_context");
    static final TruffleString T_T_KEYWORD = tsLiteral("keyword");
    static final TruffleString T_T_MATCH_CASE = tsLiteral("match_case");
    static final TruffleString T_T_MOD = tsLiteral("mod");
    static final TruffleString T_T_OPERATOR = tsLiteral("operator");
    static final TruffleString T_T_PATTERN = tsLiteral("pattern");
    static final TruffleString T_T_STMT = tsLiteral("stmt");
    static final TruffleString T_T_TYPE_IGNORE = tsLiteral("type_ignore");
    static final TruffleString T_T_UNARYOP = tsLiteral("unaryop");
    static final TruffleString T_T_WITHITEM = tsLiteral("withitem");

    final PythonBuiltinClass clsAst;
    final PythonClass clsModTy;
    final PythonClass clsModule;
    final PythonClass clsInteractive;
    final PythonClass clsExpression;
    final PythonClass clsFunctionType;
    final PythonClass clsStmtTy;
    final PythonClass clsFunctionDef;
    final PythonClass clsAsyncFunctionDef;
    final PythonClass clsClassDef;
    final PythonClass clsReturn;
    final PythonClass clsDelete;
    final PythonClass clsAssign;
    final PythonClass clsAugAssign;
    final PythonClass clsAnnAssign;
    final PythonClass clsFor;
    final PythonClass clsAsyncFor;
    final PythonClass clsWhile;
    final PythonClass clsIf;
    final PythonClass clsWith;
    final PythonClass clsAsyncWith;
    final PythonClass clsMatch;
    final PythonClass clsRaise;
    final PythonClass clsTry;
    final PythonClass clsTryStar;
    final PythonClass clsAssert;
    final PythonClass clsImport;
    final PythonClass clsImportFrom;
    final PythonClass clsGlobal;
    final PythonClass clsNonlocal;
    final PythonClass clsExpr;
    final PythonClass clsPass;
    final PythonClass clsBreak;
    final PythonClass clsContinue;
    final PythonClass clsExprTy;
    final PythonClass clsBoolOp;
    final PythonClass clsNamedExpr;
    final PythonClass clsBinOp;
    final PythonClass clsUnaryOp;
    final PythonClass clsLambda;
    final PythonClass clsIfExp;
    final PythonClass clsDict;
    final PythonClass clsSet;
    final PythonClass clsListComp;
    final PythonClass clsSetComp;
    final PythonClass clsDictComp;
    final PythonClass clsGeneratorExp;
    final PythonClass clsAwait;
    final PythonClass clsYield;
    final PythonClass clsYieldFrom;
    final PythonClass clsCompare;
    final PythonClass clsCall;
    final PythonClass clsFormattedValue;
    final PythonClass clsJoinedStr;
    final PythonClass clsConstant;
    final PythonClass clsAttribute;
    final PythonClass clsSubscript;
    final PythonClass clsStarred;
    final PythonClass clsName;
    final PythonClass clsList;
    final PythonClass clsTuple;
    final PythonClass clsSlice;
    final PythonClass clsExprContextTy;
    final PythonClass clsBoolOpTy;
    final PythonClass clsOperatorTy;
    final PythonClass clsUnaryOpTy;
    final PythonClass clsCmpOpTy;
    final PythonClass clsComprehensionTy;
    final PythonClass clsExceptHandlerTy;
    final PythonClass clsExceptHandler;
    final PythonClass clsArgumentsTy;
    final PythonClass clsArgTy;
    final PythonClass clsKeywordTy;
    final PythonClass clsAliasTy;
    final PythonClass clsWithItemTy;
    final PythonClass clsMatchCaseTy;
    final PythonClass clsPatternTy;
    final PythonClass clsMatchValue;
    final PythonClass clsMatchSingleton;
    final PythonClass clsMatchSequence;
    final PythonClass clsMatchMapping;
    final PythonClass clsMatchClass;
    final PythonClass clsMatchStar;
    final PythonClass clsMatchAs;
    final PythonClass clsMatchOr;
    final PythonClass clsTypeIgnoreTy;
    final PythonClass clsTypeIgnore;

    final PythonClass clsLoad;
    final PythonObject singletonLoad;
    final PythonClass clsStore;
    final PythonObject singletonStore;
    final PythonClass clsDel;
    final PythonObject singletonDel;
    final PythonClass clsAnd;
    final PythonObject singletonAnd;
    final PythonClass clsOr;
    final PythonObject singletonOr;
    final PythonClass clsAdd;
    final PythonObject singletonAdd;
    final PythonClass clsSub;
    final PythonObject singletonSub;
    final PythonClass clsMult;
    final PythonObject singletonMult;
    final PythonClass clsMatMult;
    final PythonObject singletonMatMult;
    final PythonClass clsDiv;
    final PythonObject singletonDiv;
    final PythonClass clsMod;
    final PythonObject singletonMod;
    final PythonClass clsPow;
    final PythonObject singletonPow;
    final PythonClass clsLShift;
    final PythonObject singletonLShift;
    final PythonClass clsRShift;
    final PythonObject singletonRShift;
    final PythonClass clsBitOr;
    final PythonObject singletonBitOr;
    final PythonClass clsBitXor;
    final PythonObject singletonBitXor;
    final PythonClass clsBitAnd;
    final PythonObject singletonBitAnd;
    final PythonClass clsFloorDiv;
    final PythonObject singletonFloorDiv;
    final PythonClass clsInvert;
    final PythonObject singletonInvert;
    final PythonClass clsNot;
    final PythonObject singletonNot;
    final PythonClass clsUAdd;
    final PythonObject singletonUAdd;
    final PythonClass clsUSub;
    final PythonObject singletonUSub;
    final PythonClass clsEq;
    final PythonObject singletonEq;
    final PythonClass clsNotEq;
    final PythonObject singletonNotEq;
    final PythonClass clsLt;
    final PythonObject singletonLt;
    final PythonClass clsLtE;
    final PythonObject singletonLtE;
    final PythonClass clsGt;
    final PythonObject singletonGt;
    final PythonClass clsGtE;
    final PythonObject singletonGtE;
    final PythonClass clsIs;
    final PythonObject singletonIs;
    final PythonClass clsIsNot;
    final PythonObject singletonIsNot;
    final PythonClass clsIn;
    final PythonObject singletonIn;
    final PythonClass clsNotIn;
    final PythonObject singletonNotIn;

    AstState(AstTypeFactory factory, PythonBuiltinClass clsAst) {
        this.clsAst = clsAst;

        // ModTy
        clsModTy = factory.makeType(T_T_MOD, clsAst,
                        tsa(),
                        null,
                        tsa(),
                        ts("mod = Module(stmt* body, type_ignore* type_ignores)\n" +
                        "    | Interactive(stmt* body)\n" +
                        "    | Expression(expr body)\n" +
                        "    | FunctionType(expr* argtypes, expr returns)")
        );

        // ModTy.Module
        clsModule = factory.makeType(T_C_MODULE, clsModTy,
                        tsa(T_F_BODY, T_F_TYPE_IGNORES),
                        null,
                        tsa(),
                        ts("Module(stmt* body, type_ignore* type_ignores)")
        );

        // ModTy.Interactive
        clsInteractive = factory.makeType(T_C_INTERACTIVE, clsModTy,
                        tsa(T_F_BODY),
                        null,
                        tsa(),
                        ts("Interactive(stmt* body)")
        );

        // ModTy.Expression
        clsExpression = factory.makeType(T_C_EXPRESSION, clsModTy,
                        tsa(T_F_BODY),
                        null,
                        tsa(),
                        ts("Expression(expr body)")
        );

        // ModTy.FunctionType
        clsFunctionType = factory.makeType(T_C_FUNCTIONTYPE, clsModTy,
                        tsa(T_F_ARGTYPES, T_F_RETURNS),
                        null,
                        tsa(),
                        ts("FunctionType(expr* argtypes, expr returns)")
        );

        // StmtTy
        clsStmtTy = factory.makeType(T_T_STMT, clsAst,
                        tsa(),
                        tsa(T_F_LINENO, T_F_COL_OFFSET, T_F_END_LINENO, T_F_END_COL_OFFSET),
                        tsa(T_F_END_LINENO, T_F_END_COL_OFFSET),
                        ts("stmt = FunctionDef(identifier name, arguments args, stmt* body, expr* decorator_list, expr? returns, string? type_comment)\n" +
                        "     | AsyncFunctionDef(identifier name, arguments args, stmt* body, expr* decorator_list, expr? returns, string? type_comment)\n" +
                        "     | ClassDef(identifier name, expr* bases, keyword* keywords, stmt* body, expr* decorator_list)\n" +
                        "     | Return(expr? value)\n" +
                        "     | Delete(expr* targets)\n" +
                        "     | Assign(expr* targets, expr value, string? type_comment)\n" +
                        "     | AugAssign(expr target, operator op, expr value)\n" +
                        "     | AnnAssign(expr target, expr annotation, expr? value, int simple)\n" +
                        "     | For(expr target, expr iter, stmt* body, stmt* orelse, string? type_comment)\n" +
                        "     | AsyncFor(expr target, expr iter, stmt* body, stmt* orelse, string? type_comment)\n" +
                        "     | While(expr test, stmt* body, stmt* orelse)\n" +
                        "     | If(expr test, stmt* body, stmt* orelse)\n" +
                        "     | With(withitem* items, stmt* body, string? type_comment)\n" +
                        "     | AsyncWith(withitem* items, stmt* body, string? type_comment)\n" +
                        "     | Match(expr subject, match_case* cases)\n" +
                        "     | Raise(expr? exc, expr? cause)\n" +
                        "     | Try(stmt* body, excepthandler* handlers, stmt* orelse, stmt* finalbody)\n" +
                        "     | TryStar(stmt* body, excepthandler* handlers, stmt* orelse, stmt* finalbody)\n" +
                        "     | Assert(expr test, expr? msg)\n" +
                        "     | Import(alias* names)\n" +
                        "     | ImportFrom(identifier? module, alias* names, int? level)\n" +
                        "     | Global(identifier* names)\n" +
                        "     | Nonlocal(identifier* names)\n" +
                        "     | Expr(expr value)\n" +
                        "     | Pass\n" +
                        "     | Break\n" +
                        "     | Continue")
        );

        // StmtTy.FunctionDef
        clsFunctionDef = factory.makeType(T_C_FUNCTIONDEF, clsStmtTy,
                        tsa(T_F_NAME, T_F_ARGS, T_F_BODY, T_F_DECORATOR_LIST, T_F_RETURNS, T_F_TYPE_COMMENT),
                        null,
                        tsa(T_F_RETURNS, T_F_TYPE_COMMENT),
                        ts("FunctionDef(identifier name, arguments args, stmt* body, expr* decorator_list, expr? returns, string? type_comment)")
        );

        // StmtTy.AsyncFunctionDef
        clsAsyncFunctionDef = factory.makeType(T_C_ASYNCFUNCTIONDEF, clsStmtTy,
                        tsa(T_F_NAME, T_F_ARGS, T_F_BODY, T_F_DECORATOR_LIST, T_F_RETURNS, T_F_TYPE_COMMENT),
                        null,
                        tsa(T_F_RETURNS, T_F_TYPE_COMMENT),
                        ts("AsyncFunctionDef(identifier name, arguments args, stmt* body, expr* decorator_list, expr? returns, string? type_comment)")
        );

        // StmtTy.ClassDef
        clsClassDef = factory.makeType(T_C_CLASSDEF, clsStmtTy,
                        tsa(T_F_NAME, T_F_BASES, T_F_KEYWORDS, T_F_BODY, T_F_DECORATOR_LIST),
                        null,
                        tsa(),
                        ts("ClassDef(identifier name, expr* bases, keyword* keywords, stmt* body, expr* decorator_list)")
        );

        // StmtTy.Return
        clsReturn = factory.makeType(T_C_RETURN, clsStmtTy,
                        tsa(T_F_VALUE),
                        null,
                        tsa(T_F_VALUE),
                        ts("Return(expr? value)")
        );

        // StmtTy.Delete
        clsDelete = factory.makeType(T_C_DELETE, clsStmtTy,
                        tsa(T_F_TARGETS),
                        null,
                        tsa(),
                        ts("Delete(expr* targets)")
        );

        // StmtTy.Assign
        clsAssign = factory.makeType(T_C_ASSIGN, clsStmtTy,
                        tsa(T_F_TARGETS, T_F_VALUE, T_F_TYPE_COMMENT),
                        null,
                        tsa(T_F_TYPE_COMMENT),
                        ts("Assign(expr* targets, expr value, string? type_comment)")
        );

        // StmtTy.AugAssign
        clsAugAssign = factory.makeType(T_C_AUGASSIGN, clsStmtTy,
                        tsa(T_F_TARGET, T_F_OP, T_F_VALUE),
                        null,
                        tsa(),
                        ts("AugAssign(expr target, operator op, expr value)")
        );

        // StmtTy.AnnAssign
        clsAnnAssign = factory.makeType(T_C_ANNASSIGN, clsStmtTy,
                        tsa(T_F_TARGET, T_F_ANNOTATION, T_F_VALUE, T_F_SIMPLE),
                        null,
                        tsa(T_F_VALUE),
                        ts("AnnAssign(expr target, expr annotation, expr? value, int simple)")
        );

        // StmtTy.For
        clsFor = factory.makeType(T_C_FOR, clsStmtTy,
                        tsa(T_F_TARGET, T_F_ITER, T_F_BODY, T_F_ORELSE, T_F_TYPE_COMMENT),
                        null,
                        tsa(T_F_TYPE_COMMENT),
                        ts("For(expr target, expr iter, stmt* body, stmt* orelse, string? type_comment)")
        );

        // StmtTy.AsyncFor
        clsAsyncFor = factory.makeType(T_C_ASYNCFOR, clsStmtTy,
                        tsa(T_F_TARGET, T_F_ITER, T_F_BODY, T_F_ORELSE, T_F_TYPE_COMMENT),
                        null,
                        tsa(T_F_TYPE_COMMENT),
                        ts("AsyncFor(expr target, expr iter, stmt* body, stmt* orelse, string? type_comment)")
        );

        // StmtTy.While
        clsWhile = factory.makeType(T_C_WHILE, clsStmtTy,
                        tsa(T_F_TEST, T_F_BODY, T_F_ORELSE),
                        null,
                        tsa(),
                        ts("While(expr test, stmt* body, stmt* orelse)")
        );

        // StmtTy.If
        clsIf = factory.makeType(T_C_IF, clsStmtTy,
                        tsa(T_F_TEST, T_F_BODY, T_F_ORELSE),
                        null,
                        tsa(),
                        ts("If(expr test, stmt* body, stmt* orelse)")
        );

        // StmtTy.With
        clsWith = factory.makeType(T_C_WITH, clsStmtTy,
                        tsa(T_F_ITEMS, T_F_BODY, T_F_TYPE_COMMENT),
                        null,
                        tsa(T_F_TYPE_COMMENT),
                        ts("With(withitem* items, stmt* body, string? type_comment)")
        );

        // StmtTy.AsyncWith
        clsAsyncWith = factory.makeType(T_C_ASYNCWITH, clsStmtTy,
                        tsa(T_F_ITEMS, T_F_BODY, T_F_TYPE_COMMENT),
                        null,
                        tsa(T_F_TYPE_COMMENT),
                        ts("AsyncWith(withitem* items, stmt* body, string? type_comment)")
        );

        // StmtTy.Match
        clsMatch = factory.makeType(T_C_MATCH, clsStmtTy,
                        tsa(T_F_SUBJECT, T_F_CASES),
                        null,
                        tsa(),
                        ts("Match(expr subject, match_case* cases)")
        );

        // StmtTy.Raise
        clsRaise = factory.makeType(T_C_RAISE, clsStmtTy,
                        tsa(T_F_EXC, T_F_CAUSE),
                        null,
                        tsa(T_F_EXC, T_F_CAUSE),
                        ts("Raise(expr? exc, expr? cause)")
        );

        // StmtTy.Try
        clsTry = factory.makeType(T_C_TRY, clsStmtTy,
                        tsa(T_F_BODY, T_F_HANDLERS, T_F_ORELSE, T_F_FINALBODY),
                        null,
                        tsa(),
                        ts("Try(stmt* body, excepthandler* handlers, stmt* orelse, stmt* finalbody)")
        );

        // StmtTy.TryStar
        clsTryStar = factory.makeType(T_C_TRYSTAR, clsStmtTy,
                        tsa(T_F_BODY, T_F_HANDLERS, T_F_ORELSE, T_F_FINALBODY),
                        null,
                        tsa(),
                        ts("TryStar(stmt* body, excepthandler* handlers, stmt* orelse, stmt* finalbody)")
        );

        // StmtTy.Assert
        clsAssert = factory.makeType(T_C_ASSERT, clsStmtTy,
                        tsa(T_F_TEST, T_F_MSG),
                        null,
                        tsa(T_F_MSG),
                        ts("Assert(expr test, expr? msg)")
        );

        // StmtTy.Import
        clsImport = factory.makeType(T_C_IMPORT, clsStmtTy,
                        tsa(T_F_NAMES),
                        null,
                        tsa(),
                        ts("Import(alias* names)")
        );

        // StmtTy.ImportFrom
        clsImportFrom = factory.makeType(T_C_IMPORTFROM, clsStmtTy,
                        tsa(T_F_MODULE, T_F_NAMES, T_F_LEVEL),
                        null,
                        tsa(T_F_MODULE, T_F_LEVEL),
                        ts("ImportFrom(identifier? module, alias* names, int? level)")
        );

        // StmtTy.Global
        clsGlobal = factory.makeType(T_C_GLOBAL, clsStmtTy,
                        tsa(T_F_NAMES),
                        null,
                        tsa(),
                        ts("Global(identifier* names)")
        );

        // StmtTy.Nonlocal
        clsNonlocal = factory.makeType(T_C_NONLOCAL, clsStmtTy,
                        tsa(T_F_NAMES),
                        null,
                        tsa(),
                        ts("Nonlocal(identifier* names)")
        );

        // StmtTy.Expr
        clsExpr = factory.makeType(T_C_EXPR, clsStmtTy,
                        tsa(T_F_VALUE),
                        null,
                        tsa(),
                        ts("Expr(expr value)")
        );

        // StmtTy.Pass
        clsPass = factory.makeType(T_C_PASS, clsStmtTy,
                        tsa(),
                        null,
                        tsa(),
                        ts("Pass")
        );

        // StmtTy.Break
        clsBreak = factory.makeType(T_C_BREAK, clsStmtTy,
                        tsa(),
                        null,
                        tsa(),
                        ts("Break")
        );

        // StmtTy.Continue
        clsContinue = factory.makeType(T_C_CONTINUE, clsStmtTy,
                        tsa(),
                        null,
                        tsa(),
                        ts("Continue")
        );

        // ExprTy
        clsExprTy = factory.makeType(T_T_EXPR, clsAst,
                        tsa(),
                        tsa(T_F_LINENO, T_F_COL_OFFSET, T_F_END_LINENO, T_F_END_COL_OFFSET),
                        tsa(T_F_END_LINENO, T_F_END_COL_OFFSET),
                        ts("expr = BoolOp(boolop op, expr* values)\n" +
                        "     | NamedExpr(expr target, expr value)\n" +
                        "     | BinOp(expr left, operator op, expr right)\n" +
                        "     | UnaryOp(unaryop op, expr operand)\n" +
                        "     | Lambda(arguments args, expr body)\n" +
                        "     | IfExp(expr test, expr body, expr orelse)\n" +
                        "     | Dict(expr* keys, expr* values)\n" +
                        "     | Set(expr* elts)\n" +
                        "     | ListComp(expr elt, comprehension* generators)\n" +
                        "     | SetComp(expr elt, comprehension* generators)\n" +
                        "     | DictComp(expr key, expr value, comprehension* generators)\n" +
                        "     | GeneratorExp(expr elt, comprehension* generators)\n" +
                        "     | Await(expr value)\n" +
                        "     | Yield(expr? value)\n" +
                        "     | YieldFrom(expr value)\n" +
                        "     | Compare(expr left, cmpop* ops, expr* comparators)\n" +
                        "     | Call(expr func, expr* args, keyword* keywords)\n" +
                        "     | FormattedValue(expr value, int conversion, expr? format_spec)\n" +
                        "     | JoinedStr(expr* values)\n" +
                        "     | Constant(constant value, string? kind)\n" +
                        "     | Attribute(expr value, identifier attr, expr_context ctx)\n" +
                        "     | Subscript(expr value, expr slice, expr_context ctx)\n" +
                        "     | Starred(expr value, expr_context ctx)\n" +
                        "     | Name(identifier id, expr_context ctx)\n" +
                        "     | List(expr* elts, expr_context ctx)\n" +
                        "     | Tuple(expr* elts, expr_context ctx)\n" +
                        "     | Slice(expr? lower, expr? upper, expr? step)")
        );

        // ExprTy.BoolOp
        clsBoolOp = factory.makeType(T_C_BOOLOP, clsExprTy,
                        tsa(T_F_OP, T_F_VALUES),
                        null,
                        tsa(),
                        ts("BoolOp(boolop op, expr* values)")
        );

        // ExprTy.NamedExpr
        clsNamedExpr = factory.makeType(T_C_NAMEDEXPR, clsExprTy,
                        tsa(T_F_TARGET, T_F_VALUE),
                        null,
                        tsa(),
                        ts("NamedExpr(expr target, expr value)")
        );

        // ExprTy.BinOp
        clsBinOp = factory.makeType(T_C_BINOP, clsExprTy,
                        tsa(T_F_LEFT, T_F_OP, T_F_RIGHT),
                        null,
                        tsa(),
                        ts("BinOp(expr left, operator op, expr right)")
        );

        // ExprTy.UnaryOp
        clsUnaryOp = factory.makeType(T_C_UNARYOP, clsExprTy,
                        tsa(T_F_OP, T_F_OPERAND),
                        null,
                        tsa(),
                        ts("UnaryOp(unaryop op, expr operand)")
        );

        // ExprTy.Lambda
        clsLambda = factory.makeType(T_C_LAMBDA, clsExprTy,
                        tsa(T_F_ARGS, T_F_BODY),
                        null,
                        tsa(),
                        ts("Lambda(arguments args, expr body)")
        );

        // ExprTy.IfExp
        clsIfExp = factory.makeType(T_C_IFEXP, clsExprTy,
                        tsa(T_F_TEST, T_F_BODY, T_F_ORELSE),
                        null,
                        tsa(),
                        ts("IfExp(expr test, expr body, expr orelse)")
        );

        // ExprTy.Dict
        clsDict = factory.makeType(T_C_DICT, clsExprTy,
                        tsa(T_F_KEYS, T_F_VALUES),
                        null,
                        tsa(),
                        ts("Dict(expr* keys, expr* values)")
        );

        // ExprTy.Set
        clsSet = factory.makeType(T_C_SET, clsExprTy,
                        tsa(T_F_ELTS),
                        null,
                        tsa(),
                        ts("Set(expr* elts)")
        );

        // ExprTy.ListComp
        clsListComp = factory.makeType(T_C_LISTCOMP, clsExprTy,
                        tsa(T_F_ELT, T_F_GENERATORS),
                        null,
                        tsa(),
                        ts("ListComp(expr elt, comprehension* generators)")
        );

        // ExprTy.SetComp
        clsSetComp = factory.makeType(T_C_SETCOMP, clsExprTy,
                        tsa(T_F_ELT, T_F_GENERATORS),
                        null,
                        tsa(),
                        ts("SetComp(expr elt, comprehension* generators)")
        );

        // ExprTy.DictComp
        clsDictComp = factory.makeType(T_C_DICTCOMP, clsExprTy,
                        tsa(T_F_KEY, T_F_VALUE, T_F_GENERATORS),
                        null,
                        tsa(),
                        ts("DictComp(expr key, expr value, comprehension* generators)")
        );

        // ExprTy.GeneratorExp
        clsGeneratorExp = factory.makeType(T_C_GENERATOREXP, clsExprTy,
                        tsa(T_F_ELT, T_F_GENERATORS),
                        null,
                        tsa(),
                        ts("GeneratorExp(expr elt, comprehension* generators)")
        );

        // ExprTy.Await
        clsAwait = factory.makeType(T_C_AWAIT, clsExprTy,
                        tsa(T_F_VALUE),
                        null,
                        tsa(),
                        ts("Await(expr value)")
        );

        // ExprTy.Yield
        clsYield = factory.makeType(T_C_YIELD, clsExprTy,
                        tsa(T_F_VALUE),
                        null,
                        tsa(T_F_VALUE),
                        ts("Yield(expr? value)")
        );

        // ExprTy.YieldFrom
        clsYieldFrom = factory.makeType(T_C_YIELDFROM, clsExprTy,
                        tsa(T_F_VALUE),
                        null,
                        tsa(),
                        ts("YieldFrom(expr value)")
        );

        // ExprTy.Compare
        clsCompare = factory.makeType(T_C_COMPARE, clsExprTy,
                        tsa(T_F_LEFT, T_F_OPS, T_F_COMPARATORS),
                        null,
                        tsa(),
                        ts("Compare(expr left, cmpop* ops, expr* comparators)")
        );

        // ExprTy.Call
        clsCall = factory.makeType(T_C_CALL, clsExprTy,
                        tsa(T_F_FUNC, T_F_ARGS, T_F_KEYWORDS),
                        null,
                        tsa(),
                        ts("Call(expr func, expr* args, keyword* keywords)")
        );

        // ExprTy.FormattedValue
        clsFormattedValue = factory.makeType(T_C_FORMATTEDVALUE, clsExprTy,
                        tsa(T_F_VALUE, T_F_CONVERSION, T_F_FORMAT_SPEC),
                        null,
                        tsa(T_F_FORMAT_SPEC),
                        ts("FormattedValue(expr value, int conversion, expr? format_spec)")
        );

        // ExprTy.JoinedStr
        clsJoinedStr = factory.makeType(T_C_JOINEDSTR, clsExprTy,
                        tsa(T_F_VALUES),
                        null,
                        tsa(),
                        ts("JoinedStr(expr* values)")
        );

        // ExprTy.Constant
        clsConstant = factory.makeType(T_C_CONSTANT, clsExprTy,
                        tsa(T_F_VALUE, T_F_KIND),
                        null,
                        tsa(T_F_KIND),
                        ts("Constant(constant value, string? kind)")
        );

        // ExprTy.Attribute
        clsAttribute = factory.makeType(T_C_ATTRIBUTE, clsExprTy,
                        tsa(T_F_VALUE, T_F_ATTR, T_F_CTX),
                        null,
                        tsa(),
                        ts("Attribute(expr value, identifier attr, expr_context ctx)")
        );

        // ExprTy.Subscript
        clsSubscript = factory.makeType(T_C_SUBSCRIPT, clsExprTy,
                        tsa(T_F_VALUE, T_F_SLICE, T_F_CTX),
                        null,
                        tsa(),
                        ts("Subscript(expr value, expr slice, expr_context ctx)")
        );

        // ExprTy.Starred
        clsStarred = factory.makeType(T_C_STARRED, clsExprTy,
                        tsa(T_F_VALUE, T_F_CTX),
                        null,
                        tsa(),
                        ts("Starred(expr value, expr_context ctx)")
        );

        // ExprTy.Name
        clsName = factory.makeType(T_C_NAME, clsExprTy,
                        tsa(T_F_ID, T_F_CTX),
                        null,
                        tsa(),
                        ts("Name(identifier id, expr_context ctx)")
        );

        // ExprTy.List
        clsList = factory.makeType(T_C_LIST, clsExprTy,
                        tsa(T_F_ELTS, T_F_CTX),
                        null,
                        tsa(),
                        ts("List(expr* elts, expr_context ctx)")
        );

        // ExprTy.Tuple
        clsTuple = factory.makeType(T_C_TUPLE, clsExprTy,
                        tsa(T_F_ELTS, T_F_CTX),
                        null,
                        tsa(),
                        ts("Tuple(expr* elts, expr_context ctx)")
        );

        // ExprTy.Slice
        clsSlice = factory.makeType(T_C_SLICE, clsExprTy,
                        tsa(T_F_LOWER, T_F_UPPER, T_F_STEP),
                        null,
                        tsa(T_F_LOWER, T_F_UPPER, T_F_STEP),
                        ts("Slice(expr? lower, expr? upper, expr? step)")
        );

        // ExprContextTy
        clsExprContextTy = factory.makeType(T_T_EXPR_CONTEXT, clsAst,
                        tsa(),
                        null,
                        tsa(),
                        ts("expr_context = Load | Store | Del")
        );

        // ExprContextTy.Load
        clsLoad = factory.makeType(T_C_LOAD, clsExprContextTy,
                        tsa(),
                        null,
                        tsa(),
                        ts("Load")
        );
        singletonLoad = factory.createSingleton(clsLoad);

        // ExprContextTy.Store
        clsStore = factory.makeType(T_C_STORE, clsExprContextTy,
                        tsa(),
                        null,
                        tsa(),
                        ts("Store")
        );
        singletonStore = factory.createSingleton(clsStore);

        // ExprContextTy.Del
        clsDel = factory.makeType(T_C_DEL, clsExprContextTy,
                        tsa(),
                        null,
                        tsa(),
                        ts("Del")
        );
        singletonDel = factory.createSingleton(clsDel);

        // BoolOpTy
        clsBoolOpTy = factory.makeType(T_T_BOOLOP, clsAst,
                        tsa(),
                        null,
                        tsa(),
                        ts("boolop = And | Or")
        );

        // BoolOpTy.And
        clsAnd = factory.makeType(T_C_AND, clsBoolOpTy,
                        tsa(),
                        null,
                        tsa(),
                        ts("And")
        );
        singletonAnd = factory.createSingleton(clsAnd);

        // BoolOpTy.Or
        clsOr = factory.makeType(T_C_OR, clsBoolOpTy,
                        tsa(),
                        null,
                        tsa(),
                        ts("Or")
        );
        singletonOr = factory.createSingleton(clsOr);

        // OperatorTy
        clsOperatorTy = factory.makeType(T_T_OPERATOR, clsAst,
                        tsa(),
                        null,
                        tsa(),
                        ts("operator = Add | Sub | Mult | MatMult | Div | Mod | Pow | LShift | RShift | BitOr | BitXor | BitAnd | FloorDiv")
        );

        // OperatorTy.Add
        clsAdd = factory.makeType(T_C_ADD, clsOperatorTy,
                        tsa(),
                        null,
                        tsa(),
                        ts("Add")
        );
        singletonAdd = factory.createSingleton(clsAdd);

        // OperatorTy.Sub
        clsSub = factory.makeType(T_C_SUB, clsOperatorTy,
                        tsa(),
                        null,
                        tsa(),
                        ts("Sub")
        );
        singletonSub = factory.createSingleton(clsSub);

        // OperatorTy.Mult
        clsMult = factory.makeType(T_C_MULT, clsOperatorTy,
                        tsa(),
                        null,
                        tsa(),
                        ts("Mult")
        );
        singletonMult = factory.createSingleton(clsMult);

        // OperatorTy.MatMult
        clsMatMult = factory.makeType(T_C_MATMULT, clsOperatorTy,
                        tsa(),
                        null,
                        tsa(),
                        ts("MatMult")
        );
        singletonMatMult = factory.createSingleton(clsMatMult);

        // OperatorTy.Div
        clsDiv = factory.makeType(T_C_DIV, clsOperatorTy,
                        tsa(),
                        null,
                        tsa(),
                        ts("Div")
        );
        singletonDiv = factory.createSingleton(clsDiv);

        // OperatorTy.Mod
        clsMod = factory.makeType(T_C_MOD, clsOperatorTy,
                        tsa(),
                        null,
                        tsa(),
                        ts("Mod")
        );
        singletonMod = factory.createSingleton(clsMod);

        // OperatorTy.Pow
        clsPow = factory.makeType(T_C_POW, clsOperatorTy,
                        tsa(),
                        null,
                        tsa(),
                        ts("Pow")
        );
        singletonPow = factory.createSingleton(clsPow);

        // OperatorTy.LShift
        clsLShift = factory.makeType(T_C_LSHIFT, clsOperatorTy,
                        tsa(),
                        null,
                        tsa(),
                        ts("LShift")
        );
        singletonLShift = factory.createSingleton(clsLShift);

        // OperatorTy.RShift
        clsRShift = factory.makeType(T_C_RSHIFT, clsOperatorTy,
                        tsa(),
                        null,
                        tsa(),
                        ts("RShift")
        );
        singletonRShift = factory.createSingleton(clsRShift);

        // OperatorTy.BitOr
        clsBitOr = factory.makeType(T_C_BITOR, clsOperatorTy,
                        tsa(),
                        null,
                        tsa(),
                        ts("BitOr")
        );
        singletonBitOr = factory.createSingleton(clsBitOr);

        // OperatorTy.BitXor
        clsBitXor = factory.makeType(T_C_BITXOR, clsOperatorTy,
                        tsa(),
                        null,
                        tsa(),
                        ts("BitXor")
        );
        singletonBitXor = factory.createSingleton(clsBitXor);

        // OperatorTy.BitAnd
        clsBitAnd = factory.makeType(T_C_BITAND, clsOperatorTy,
                        tsa(),
                        null,
                        tsa(),
                        ts("BitAnd")
        );
        singletonBitAnd = factory.createSingleton(clsBitAnd);

        // OperatorTy.FloorDiv
        clsFloorDiv = factory.makeType(T_C_FLOORDIV, clsOperatorTy,
                        tsa(),
                        null,
                        tsa(),
                        ts("FloorDiv")
        );
        singletonFloorDiv = factory.createSingleton(clsFloorDiv);

        // UnaryOpTy
        clsUnaryOpTy = factory.makeType(T_T_UNARYOP, clsAst,
                        tsa(),
                        null,
                        tsa(),
                        ts("unaryop = Invert | Not | UAdd | USub")
        );

        // UnaryOpTy.Invert
        clsInvert = factory.makeType(T_C_INVERT, clsUnaryOpTy,
                        tsa(),
                        null,
                        tsa(),
                        ts("Invert")
        );
        singletonInvert = factory.createSingleton(clsInvert);

        // UnaryOpTy.Not
        clsNot = factory.makeType(T_C_NOT, clsUnaryOpTy,
                        tsa(),
                        null,
                        tsa(),
                        ts("Not")
        );
        singletonNot = factory.createSingleton(clsNot);

        // UnaryOpTy.UAdd
        clsUAdd = factory.makeType(T_C_UADD, clsUnaryOpTy,
                        tsa(),
                        null,
                        tsa(),
                        ts("UAdd")
        );
        singletonUAdd = factory.createSingleton(clsUAdd);

        // UnaryOpTy.USub
        clsUSub = factory.makeType(T_C_USUB, clsUnaryOpTy,
                        tsa(),
                        null,
                        tsa(),
                        ts("USub")
        );
        singletonUSub = factory.createSingleton(clsUSub);

        // CmpOpTy
        clsCmpOpTy = factory.makeType(T_T_CMPOP, clsAst,
                        tsa(),
                        null,
                        tsa(),
                        ts("cmpop = Eq | NotEq | Lt | LtE | Gt | GtE | Is | IsNot | In | NotIn")
        );

        // CmpOpTy.Eq
        clsEq = factory.makeType(T_C_EQ, clsCmpOpTy,
                        tsa(),
                        null,
                        tsa(),
                        ts("Eq")
        );
        singletonEq = factory.createSingleton(clsEq);

        // CmpOpTy.NotEq
        clsNotEq = factory.makeType(T_C_NOTEQ, clsCmpOpTy,
                        tsa(),
                        null,
                        tsa(),
                        ts("NotEq")
        );
        singletonNotEq = factory.createSingleton(clsNotEq);

        // CmpOpTy.Lt
        clsLt = factory.makeType(T_C_LT, clsCmpOpTy,
                        tsa(),
                        null,
                        tsa(),
                        ts("Lt")
        );
        singletonLt = factory.createSingleton(clsLt);

        // CmpOpTy.LtE
        clsLtE = factory.makeType(T_C_LTE, clsCmpOpTy,
                        tsa(),
                        null,
                        tsa(),
                        ts("LtE")
        );
        singletonLtE = factory.createSingleton(clsLtE);

        // CmpOpTy.Gt
        clsGt = factory.makeType(T_C_GT, clsCmpOpTy,
                        tsa(),
                        null,
                        tsa(),
                        ts("Gt")
        );
        singletonGt = factory.createSingleton(clsGt);

        // CmpOpTy.GtE
        clsGtE = factory.makeType(T_C_GTE, clsCmpOpTy,
                        tsa(),
                        null,
                        tsa(),
                        ts("GtE")
        );
        singletonGtE = factory.createSingleton(clsGtE);

        // CmpOpTy.Is
        clsIs = factory.makeType(T_C_IS, clsCmpOpTy,
                        tsa(),
                        null,
                        tsa(),
                        ts("Is")
        );
        singletonIs = factory.createSingleton(clsIs);

        // CmpOpTy.IsNot
        clsIsNot = factory.makeType(T_C_ISNOT, clsCmpOpTy,
                        tsa(),
                        null,
                        tsa(),
                        ts("IsNot")
        );
        singletonIsNot = factory.createSingleton(clsIsNot);

        // CmpOpTy.In
        clsIn = factory.makeType(T_C_IN, clsCmpOpTy,
                        tsa(),
                        null,
                        tsa(),
                        ts("In")
        );
        singletonIn = factory.createSingleton(clsIn);

        // CmpOpTy.NotIn
        clsNotIn = factory.makeType(T_C_NOTIN, clsCmpOpTy,
                        tsa(),
                        null,
                        tsa(),
                        ts("NotIn")
        );
        singletonNotIn = factory.createSingleton(clsNotIn);

        // ComprehensionTy
        clsComprehensionTy = factory.makeType(T_T_COMPREHENSION, clsAst,
                        tsa(T_F_TARGET, T_F_ITER, T_F_IFS, T_F_IS_ASYNC),
                        null,
                        tsa(),
                        ts("comprehension(expr target, expr iter, expr* ifs, int is_async)")
        );

        // ExceptHandlerTy
        clsExceptHandlerTy = factory.makeType(T_T_EXCEPTHANDLER, clsAst,
                        tsa(),
                        tsa(T_F_LINENO, T_F_COL_OFFSET, T_F_END_LINENO, T_F_END_COL_OFFSET),
                        tsa(T_F_END_LINENO, T_F_END_COL_OFFSET),
                        ts("excepthandler = ExceptHandler(expr? type, identifier? name, stmt* body)")
        );

        // ExceptHandlerTy.ExceptHandler
        clsExceptHandler = factory.makeType(T_C_EXCEPTHANDLER, clsExceptHandlerTy,
                        tsa(T_F_TYPE, T_F_NAME, T_F_BODY),
                        null,
                        tsa(T_F_TYPE, T_F_NAME),
                        ts("ExceptHandler(expr? type, identifier? name, stmt* body)")
        );

        // ArgumentsTy
        clsArgumentsTy = factory.makeType(T_T_ARGUMENTS, clsAst,
                        tsa(T_F_POSONLYARGS, T_F_ARGS, T_F_VARARG, T_F_KWONLYARGS, T_F_KW_DEFAULTS, T_F_KWARG, T_F_DEFAULTS),
                        null,
                        tsa(T_F_VARARG, T_F_KWARG),
                        ts("arguments(arg* posonlyargs, arg* args, arg? vararg, arg* kwonlyargs, expr* kw_defaults, arg? kwarg, expr* defaults)")
        );

        // ArgTy
        clsArgTy = factory.makeType(T_T_ARG, clsAst,
                        tsa(T_F_ARG, T_F_ANNOTATION, T_F_TYPE_COMMENT),
                        tsa(T_F_LINENO, T_F_COL_OFFSET, T_F_END_LINENO, T_F_END_COL_OFFSET),
                        tsa(T_F_ANNOTATION, T_F_TYPE_COMMENT, T_F_END_LINENO, T_F_END_COL_OFFSET),
                        ts("arg(identifier arg, expr? annotation, string? type_comment)")
        );

        // KeywordTy
        clsKeywordTy = factory.makeType(T_T_KEYWORD, clsAst,
                        tsa(T_F_ARG, T_F_VALUE),
                        tsa(T_F_LINENO, T_F_COL_OFFSET, T_F_END_LINENO, T_F_END_COL_OFFSET),
                        tsa(T_F_ARG, T_F_END_LINENO, T_F_END_COL_OFFSET),
                        ts("keyword(identifier? arg, expr value)")
        );

        // AliasTy
        clsAliasTy = factory.makeType(T_T_ALIAS, clsAst,
                        tsa(T_F_NAME, T_F_ASNAME),
                        tsa(T_F_LINENO, T_F_COL_OFFSET, T_F_END_LINENO, T_F_END_COL_OFFSET),
                        tsa(T_F_ASNAME, T_F_END_LINENO, T_F_END_COL_OFFSET),
                        ts("alias(identifier name, identifier? asname)")
        );

        // WithItemTy
        clsWithItemTy = factory.makeType(T_T_WITHITEM, clsAst,
                        tsa(T_F_CONTEXT_EXPR, T_F_OPTIONAL_VARS),
                        null,
                        tsa(T_F_OPTIONAL_VARS),
                        ts("withitem(expr context_expr, expr? optional_vars)")
        );

        // MatchCaseTy
        clsMatchCaseTy = factory.makeType(T_T_MATCH_CASE, clsAst,
                        tsa(T_F_PATTERN, T_F_GUARD, T_F_BODY),
                        null,
                        tsa(T_F_GUARD),
                        ts("match_case(pattern pattern, expr? guard, stmt* body)")
        );

        // PatternTy
        clsPatternTy = factory.makeType(T_T_PATTERN, clsAst,
                        tsa(),
                        tsa(T_F_LINENO, T_F_COL_OFFSET, T_F_END_LINENO, T_F_END_COL_OFFSET),
                        tsa(),
                        ts("pattern = MatchValue(expr value)\n" +
                        "        | MatchSingleton(constant value)\n" +
                        "        | MatchSequence(pattern* patterns)\n" +
                        "        | MatchMapping(expr* keys, pattern* patterns, identifier? rest)\n" +
                        "        | MatchClass(expr cls, pattern* patterns, identifier* kwd_attrs, pattern* kwd_patterns)\n" +
                        "        | MatchStar(identifier? name)\n" +
                        "        | MatchAs(pattern? pattern, identifier? name)\n" +
                        "        | MatchOr(pattern* patterns)")
        );

        // PatternTy.MatchValue
        clsMatchValue = factory.makeType(T_C_MATCHVALUE, clsPatternTy,
                        tsa(T_F_VALUE),
                        null,
                        tsa(),
                        ts("MatchValue(expr value)")
        );

        // PatternTy.MatchSingleton
        clsMatchSingleton = factory.makeType(T_C_MATCHSINGLETON, clsPatternTy,
                        tsa(T_F_VALUE),
                        null,
                        tsa(),
                        ts("MatchSingleton(constant value)")
        );

        // PatternTy.MatchSequence
        clsMatchSequence = factory.makeType(T_C_MATCHSEQUENCE, clsPatternTy,
                        tsa(T_F_PATTERNS),
                        null,
                        tsa(),
                        ts("MatchSequence(pattern* patterns)")
        );

        // PatternTy.MatchMapping
        clsMatchMapping = factory.makeType(T_C_MATCHMAPPING, clsPatternTy,
                        tsa(T_F_KEYS, T_F_PATTERNS, T_F_REST),
                        null,
                        tsa(T_F_REST),
                        ts("MatchMapping(expr* keys, pattern* patterns, identifier? rest)")
        );

        // PatternTy.MatchClass
        clsMatchClass = factory.makeType(T_C_MATCHCLASS, clsPatternTy,
                        tsa(T_F_CLS, T_F_PATTERNS, T_F_KWD_ATTRS, T_F_KWD_PATTERNS),
                        null,
                        tsa(),
                        ts("MatchClass(expr cls, pattern* patterns, identifier* kwd_attrs, pattern* kwd_patterns)")
        );

        // PatternTy.MatchStar
        clsMatchStar = factory.makeType(T_C_MATCHSTAR, clsPatternTy,
                        tsa(T_F_NAME),
                        null,
                        tsa(T_F_NAME),
                        ts("MatchStar(identifier? name)")
        );

        // PatternTy.MatchAs
        clsMatchAs = factory.makeType(T_C_MATCHAS, clsPatternTy,
                        tsa(T_F_PATTERN, T_F_NAME),
                        null,
                        tsa(T_F_PATTERN, T_F_NAME),
                        ts("MatchAs(pattern? pattern, identifier? name)")
        );

        // PatternTy.MatchOr
        clsMatchOr = factory.makeType(T_C_MATCHOR, clsPatternTy,
                        tsa(T_F_PATTERNS),
                        null,
                        tsa(),
                        ts("MatchOr(pattern* patterns)")
        );

        // TypeIgnoreTy
        clsTypeIgnoreTy = factory.makeType(T_T_TYPE_IGNORE, clsAst,
                        tsa(),
                        null,
                        tsa(),
                        ts("type_ignore = TypeIgnore(int lineno, string tag)")
        );

        // TypeIgnoreTy.TypeIgnore
        clsTypeIgnore = factory.makeType(T_C_TYPEIGNORE, clsTypeIgnoreTy,
                        tsa(T_F_LINENO, T_F_TAG),
                        null,
                        tsa(),
                        ts("TypeIgnore(int lineno, string tag)")
        );
    }

    private static TruffleString ts(String s) {
        return toTruffleStringUncached(s);
    }

    private static TruffleString[] tsa(TruffleString... names) {
        return names;
    }
}
