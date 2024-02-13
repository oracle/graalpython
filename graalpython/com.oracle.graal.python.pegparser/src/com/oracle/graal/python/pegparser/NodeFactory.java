/*
 * Copyright (c) 2021, 2023, Oracle and/or its affiliates. All rights reserved.
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

// TODO this class has to be moved to impl package and from this package we need to do api.

import static com.oracle.graal.python.pegparser.AbstractParser.EMPTY_ARG_ARRAY;
import static com.oracle.graal.python.pegparser.AbstractParser.EMPTY_EXPR_ARRAY;

import java.util.Arrays;

import com.oracle.graal.python.pegparser.AbstractParser.NameDefaultPair;
import com.oracle.graal.python.pegparser.AbstractParser.SlashWithDefault;
import com.oracle.graal.python.pegparser.AbstractParser.StarEtc;
import com.oracle.graal.python.pegparser.sst.AliasTy;
import com.oracle.graal.python.pegparser.sst.ArgTy;
import com.oracle.graal.python.pegparser.sst.ArgumentsTy;
import com.oracle.graal.python.pegparser.sst.BoolOpTy;
import com.oracle.graal.python.pegparser.sst.CmpOpTy;
import com.oracle.graal.python.pegparser.sst.ComprehensionTy;
import com.oracle.graal.python.pegparser.sst.ConstantValue;
import com.oracle.graal.python.pegparser.sst.ExceptHandlerTy;
import com.oracle.graal.python.pegparser.sst.ExprContextTy;
import com.oracle.graal.python.pegparser.sst.ExprTy;
import com.oracle.graal.python.pegparser.sst.KeywordTy;
import com.oracle.graal.python.pegparser.sst.MatchCaseTy;
import com.oracle.graal.python.pegparser.sst.ModTy;
import com.oracle.graal.python.pegparser.sst.OperatorTy;
import com.oracle.graal.python.pegparser.sst.PatternTy;
import com.oracle.graal.python.pegparser.sst.StmtTy;
import com.oracle.graal.python.pegparser.sst.StringLiteralUtils;
import com.oracle.graal.python.pegparser.sst.TypeIgnoreTy;
import com.oracle.graal.python.pegparser.sst.TypeIgnoreTy.TypeIgnore;
import com.oracle.graal.python.pegparser.sst.UnaryOpTy;
import com.oracle.graal.python.pegparser.sst.WithItemTy;
import com.oracle.graal.python.pegparser.tokenizer.SourceRange;

public class NodeFactory {
    public StmtTy createAnnAssignment(ExprTy target, ExprTy annotation, ExprTy rhs, boolean isSimple, SourceRange sourceRange) {
        return new StmtTy.AnnAssign(target, annotation, rhs, isSimple, sourceRange);
    }

    public StmtTy createAssert(ExprTy test, ExprTy msg, SourceRange sourceRange) {
        return new StmtTy.Assert(test, msg, sourceRange);
    }

    public StmtTy createAssignment(ExprTy[] lhs, ExprTy rhs, String typeComment, SourceRange sourceRange) {
        return new StmtTy.Assign(lhs, rhs, typeComment, sourceRange);
    }

    public StmtTy createAugAssignment(ExprTy lhs, OperatorTy operation, ExprTy rhs, SourceRange sourceRange) {
        return new StmtTy.AugAssign(lhs, operation, rhs, sourceRange);
    }

    public ExprTy createBinaryOp(OperatorTy op, ExprTy left, ExprTy right, SourceRange sourceRange) {
        return new ExprTy.BinOp(left, op, right, sourceRange);
    }

    public ModTy createModule(StmtTy[] statements, TypeIgnoreTy[] typeIgnores, SourceRange sourceRange) {
        return new ModTy.Module(statements, typeIgnores, sourceRange);
    }

    public ModTy createExpressionModule(ExprTy expression, SourceRange sourceRange) {
        return new ModTy.Expression(expression, sourceRange);
    }

    public ModTy createInteractiveModule(StmtTy[] body, SourceRange sourceRange) {
        return new ModTy.Interactive(body, sourceRange);
    }

    public ModTy createFunctionType(ExprTy[] argTypes, ExprTy returns, SourceRange sourceRange) {
        return new ModTy.FunctionType(argTypes, returns, sourceRange);
    }

    public TypeIgnore createTypeIgnore(int lineNo, String tag, SourceRange sourceRange) {
        return new TypeIgnoreTy.TypeIgnore(lineNo, tag, sourceRange);
    }

    public ExprTy createBooleanLiteral(boolean value, SourceRange sourceRange) {
        return new ExprTy.Constant(ConstantValue.ofBoolean(value), null, sourceRange);
    }

    public ExprTy createNone(SourceRange sourceRange) {
        return new ExprTy.Constant(ConstantValue.NONE, null, sourceRange);
    }

    public ExprTy createEllipsis(SourceRange sourceRange) {
        return new ExprTy.Constant(ConstantValue.ELLIPSIS, null, sourceRange);
    }

    public ExprTy createGetAttribute(ExprTy receiver, String name, ExprContextTy context, SourceRange sourceRange) {
        return new ExprTy.Attribute(receiver, name, context, sourceRange);
    }

    public StmtTy createPass(SourceRange sourceRange) {
        return new StmtTy.Pass(sourceRange);
    }

    public StmtTy createBreak(SourceRange sourceRange) {
        return new StmtTy.Break(sourceRange);
    }

    public StmtTy createExpression(ExprTy expr, SourceRange sourceRange) {
        return new StmtTy.Expr(expr, sourceRange);
    }

    public ExprTy createCall(ExprTy target, ExprTy[] args, KeywordTy[] kwargs, SourceRange sourceRange) {
        return new ExprTy.Call(target, args, kwargs, sourceRange);
    }

    public StmtTy createContinue(SourceRange sourceRange) {
        return new StmtTy.Continue(sourceRange);
    }

    public StmtTy createDelete(ExprTy[] targets, SourceRange sourceRange) {
        return new StmtTy.Delete(targets, sourceRange);
    }

    public ExprTy createYield(ExprTy value, boolean isFrom, SourceRange sourceRange) {
        if (isFrom) {
            return new ExprTy.YieldFrom(value, sourceRange);
        } else {
            return new ExprTy.Yield(value, sourceRange);
        }
    }

    public ExprTy createConstant(ConstantValue value, SourceRange sourceRange) {
        return new ExprTy.Constant(value, null, sourceRange);
    }

    public ExprTy createString(String[] values, SourceRange[] sourceRanges, FExprParser exprParser, ErrorCallback errorCb, PythonStringFactory<?> stringFactory, int featureVersion) {
        return StringLiteralUtils.createStringLiteral(values, sourceRanges, exprParser, errorCb, stringFactory, featureVersion);
    }

    public ExprTy createUnaryOp(UnaryOpTy op, ExprTy value, SourceRange sourceRange) {
        return new ExprTy.UnaryOp(op, value, sourceRange);
    }

    public ExprTy.Name createVariable(String name, SourceRange sourceRange, ExprContextTy context) {
        return new ExprTy.Name(name, context, sourceRange);
    }

    public ExprTy createStarred(ExprTy value, ExprContextTy context, SourceRange sourceRange) {
        return new ExprTy.Starred(value, context, sourceRange);
    }

    public KeywordTy createKeyword(String arg, ExprTy value, SourceRange sourceRange) {
        return new KeywordTy(arg, value, sourceRange);
    }

    public ArgTy createArgument(String argument, ExprTy annotation, String typeComment, SourceRange sourceRange) {
        return new ArgTy(argument, annotation, typeComment, sourceRange);
    }

    public ArgumentsTy createArguments(ArgTy[] slashWithoutDefault, SlashWithDefault slashWithDefault, ArgTy[] paramWithoutDefault, NameDefaultPair[] paramWithDefault, StarEtc starEtc) {
        ArgTy[] posOnlyArgs;
        if (slashWithoutDefault != null) {
            posOnlyArgs = slashWithoutDefault;
        } else if (slashWithDefault != null) {
            posOnlyArgs = Arrays.copyOf(slashWithDefault.plainNames,
                            slashWithDefault.plainNames.length +
                                            slashWithDefault.namesWithDefaults.length);
            int i = slashWithDefault.plainNames.length;
            for (NameDefaultPair p : slashWithDefault.namesWithDefaults) {
                posOnlyArgs[i++] = p.name;
            }
        } else {
            posOnlyArgs = EMPTY_ARG_ARRAY;
        }

        ArgTy[] posArgs;
        if (paramWithDefault != null) {
            int i;
            if (paramWithoutDefault != null) {
                posArgs = Arrays.copyOf(paramWithoutDefault,
                                paramWithoutDefault.length +
                                                paramWithDefault.length);
                i = paramWithoutDefault.length;
            } else {
                posArgs = new ArgTy[paramWithDefault.length];
                i = 0;
            }
            for (NameDefaultPair p : paramWithDefault) {
                posArgs[i++] = p.name;
            }
        } else if (paramWithoutDefault != null) {
            posArgs = paramWithoutDefault;
        } else {
            posArgs = EMPTY_ARG_ARRAY;
        }

        ExprTy[] posDefaults;
        int posDefaultsLen = 0;
        if (slashWithDefault != null) {
            posDefaultsLen = slashWithDefault.namesWithDefaults.length;
        }
        if (paramWithDefault != null) {
            posDefaultsLen += paramWithDefault.length;
        }
        posDefaults = new ExprTy[posDefaultsLen];
        int i = 0;
        if (slashWithDefault != null) {
            for (NameDefaultPair p : slashWithDefault.namesWithDefaults) {
                posDefaults[i++] = p.def;
            }
        }
        if (paramWithDefault != null) {
            for (NameDefaultPair p : paramWithDefault) {
                posDefaults[i++] = p.def;
            }
        }

        ArgTy[] kwOnlyArgs;
        ExprTy[] kwDefaults;
        if (starEtc != null && starEtc.kwOnlyArgs != null) {
            kwOnlyArgs = new ArgTy[starEtc.kwOnlyArgs.length];
            kwDefaults = new ExprTy[kwOnlyArgs.length];
            for (int j = 0; j < kwOnlyArgs.length; j++) {
                kwOnlyArgs[j] = starEtc.kwOnlyArgs[j].name;
                kwDefaults[j] = starEtc.kwOnlyArgs[j].def;
            }
        } else {
            kwOnlyArgs = EMPTY_ARG_ARRAY;
            kwDefaults = EMPTY_EXPR_ARRAY;
        }

        return new ArgumentsTy(posOnlyArgs, posArgs, starEtc != null ? starEtc.varArg : null, kwOnlyArgs, kwDefaults, starEtc != null ? starEtc.kwArg : null, posDefaults,
                        SourceRange.ARTIFICIAL_RANGE);
    }

    public ArgumentsTy emptyArguments() {
        return new ArgumentsTy(EMPTY_ARG_ARRAY, EMPTY_ARG_ARRAY, null, EMPTY_ARG_ARRAY, EMPTY_EXPR_ARRAY,
                        null, EMPTY_EXPR_ARRAY, SourceRange.ARTIFICIAL_RANGE);
    }

    public ExprTy createComparison(ExprTy left, AbstractParser.CmpopExprPair[] pairs, SourceRange sourceRange) {
        CmpOpTy[] ops = new CmpOpTy[pairs.length];
        ExprTy[] rights = new ExprTy[pairs.length];
        for (int i = 0; i < pairs.length; i++) {
            ops[i] = pairs[i].op;
            rights[i] = pairs[i].expr;
        }
        return new ExprTy.Compare(left, ops, rights, sourceRange);
    }

    public ExprTy createSubscript(ExprTy receiver, ExprTy subscript, ExprContextTy context, SourceRange sourceRange) {
        return new ExprTy.Subscript(receiver, subscript, context, sourceRange);
    }

    public ExprTy createTuple(ExprTy[] values, ExprContextTy context, SourceRange sourceRange) {
        return new ExprTy.Tuple(values != null ? values : EMPTY_EXPR_ARRAY, context, sourceRange);
    }

    public ExprTy createList(ExprTy[] values, ExprContextTy context, SourceRange sourceRange) {
        return new ExprTy.List(values != null ? values : EMPTY_EXPR_ARRAY, context, sourceRange);
    }

    public ExprTy createDict(ExprTy[] keys, ExprTy[] values, SourceRange sourceRange) {
        return new ExprTy.Dict(keys, values, sourceRange);
    }

    public ExprTy createSet(ExprTy[] values, SourceRange sourceRange) {
        return new ExprTy.Set(values, sourceRange);
    }

    public ComprehensionTy createComprehension(ExprTy target, ExprTy iter, ExprTy[] ifs, boolean isAsync, SourceRange sourceRange) {
        return new ComprehensionTy(target, iter, ifs, isAsync, sourceRange);
    }

    public ExprTy createListComprehension(ExprTy name, ComprehensionTy[] generators, SourceRange sourceRange) {
        return new ExprTy.ListComp(name, generators, sourceRange);
    }

    public ExprTy createDictComprehension(AbstractParser.KeyValuePair name, ComprehensionTy[] generators, SourceRange sourceRange) {
        return new ExprTy.DictComp(name.key, name.value, generators, sourceRange);
    }

    public ExprTy createSetComprehension(ExprTy name, ComprehensionTy[] generators, SourceRange sourceRange) {
        return new ExprTy.SetComp(name, generators, sourceRange);
    }

    public ExprTy createGenerator(ExprTy name, ComprehensionTy[] generators, SourceRange sourceRange) {
        return new ExprTy.GeneratorExp(name, generators, sourceRange);
    }

    public StmtTy createFunctionDef(String name, ArgumentsTy args, StmtTy[] body, ExprTy returns, String typeComment, SourceRange sourceRange) {
        return new StmtTy.FunctionDef(name, args, body, null, returns, typeComment, sourceRange);
    }

    public StmtTy createFunctionDefWithDecorators(StmtTy funcDef, ExprTy[] decorators) {
        if (funcDef instanceof StmtTy.AsyncFunctionDef) {
            StmtTy.AsyncFunctionDef f = (StmtTy.AsyncFunctionDef) funcDef;
            return new StmtTy.AsyncFunctionDef(f.name, f.args, f.body, decorators, f.returns, f.typeComment, f.getSourceRange());
        }
        StmtTy.FunctionDef f = (StmtTy.FunctionDef) funcDef;
        return new StmtTy.FunctionDef(f.name, f.args, f.body, decorators, f.returns, f.typeComment, f.getSourceRange());
    }

    public StmtTy createAsyncFunctionDef(String name, ArgumentsTy args, StmtTy[] body, ExprTy returns, String typeComment, SourceRange sourceRange) {
        return new StmtTy.AsyncFunctionDef(name, args, body, null, returns, typeComment, sourceRange);
    }

    public StmtTy createWhile(ExprTy condition, StmtTy[] block, StmtTy[] elseBlock, SourceRange sourceRange) {
        return new StmtTy.While(condition, block, elseBlock, sourceRange);
    }

    public StmtTy createFor(ExprTy target, ExprTy iter, StmtTy[] block, StmtTy[] elseBlock, String typeComment, SourceRange sourceRange) {
        return new StmtTy.For(target, iter, block, elseBlock, typeComment, sourceRange);
    }

    public StmtTy createAsyncFor(ExprTy target, ExprTy iter, StmtTy[] block, StmtTy[] elseBlock, String typeComment, SourceRange sourceRange) {
        return new StmtTy.AsyncFor(target, iter, block, elseBlock, typeComment, sourceRange);
    }

    public StmtTy createReturn(ExprTy value, SourceRange sourceRange) {
        return new StmtTy.Return(value, sourceRange);
    }

    public ExprTy createSlice(ExprTy start, ExprTy stop, ExprTy step, SourceRange sourceRange) {
        return new ExprTy.Slice(start, stop, step, sourceRange);
    }

    public StmtTy createIf(ExprTy condition, StmtTy[] block, StmtTy[] orElse, SourceRange sourceRange) {
        return new StmtTy.If(condition, block, orElse, sourceRange);
    }

    public ExprTy createIfExpression(ExprTy condition, ExprTy then, ExprTy orElse, SourceRange sourceRange) {
        return new ExprTy.IfExp(condition, then, orElse, sourceRange);
    }

    public ExprTy createLambda(ArgumentsTy args, ExprTy body, SourceRange sourceRange) {
        return new ExprTy.Lambda(args, body, sourceRange);
    }

    public StmtTy createClassDef(ExprTy name, ExprTy call, StmtTy[] body, SourceRange sourceRange) {
        return new StmtTy.ClassDef(((ExprTy.Name) name).id,
                        call == null ? EMPTY_EXPR_ARRAY : ((ExprTy.Call) call).args,
                        call == null ? AbstractParser.EMPTY_KEYWORD_ARRAY : ((ExprTy.Call) call).keywords,
                        body, null, sourceRange);
    }

    public StmtTy createClassDef(StmtTy proto, ExprTy[] decorators, @SuppressWarnings("unused") SourceRange sourceRange) {
        StmtTy.ClassDef classdef = (StmtTy.ClassDef) proto;
        return new StmtTy.ClassDef(classdef.name, classdef.bases, classdef.keywords, classdef.body, decorators, classdef.getSourceRange());
    }

    public StmtTy createNonLocal(String[] names, SourceRange sourceRange) {
        return new StmtTy.Nonlocal(names, sourceRange);
    }

    public StmtTy createGlobal(String[] names, SourceRange sourceRange) {
        return new StmtTy.Global(names, sourceRange);
    }

    public ExprTy createAnd(ExprTy[] values, SourceRange sourceRange) {
        return new ExprTy.BoolOp(BoolOpTy.And, values, sourceRange);
    }

    public ExprTy createOr(ExprTy[] values, SourceRange sourceRange) {
        return new ExprTy.BoolOp(BoolOpTy.Or, values, sourceRange);
    }

    public StmtTy createRaise(ExprTy object, ExprTy from, SourceRange sourceRange) {
        return new StmtTy.Raise(object, from, sourceRange);
    }

    public StmtTy createImport(AliasTy[] names, SourceRange sourceRange) {
        return new StmtTy.Import(names, sourceRange);
    }

    public StmtTy createImportFrom(String fromName, AliasTy[] names, int level, SourceRange sourceRange) {
        return new StmtTy.ImportFrom(fromName, names, level, sourceRange);
    }

    public AliasTy createAlias(String name, String asName, SourceRange sourceRange) {
        return new AliasTy(name, asName, sourceRange);
    }

    public StmtTy createTry(StmtTy[] body, ExceptHandlerTy[] handlers, StmtTy[] orElse, StmtTy[] finalBody, SourceRange sourceRange) {
        return new StmtTy.Try(body, handlers, orElse, finalBody, sourceRange);
    }

    public StmtTy.TryStar createTryStar(StmtTy[] body, ExceptHandlerTy[] handlers, StmtTy[] orElse, StmtTy[] finalBody, SourceRange sourceRange) {
        return new StmtTy.TryStar(body, handlers, orElse, finalBody, sourceRange);
    }

    public ExceptHandlerTy createExceptHandler(ExprTy type, String name, StmtTy[] body, SourceRange sourceRange) {
        return new ExceptHandlerTy.ExceptHandler(type, name, body, sourceRange);
    }

    public WithItemTy createWithItem(ExprTy contextExpr, ExprTy optionalVars, SourceRange sourceRange) {
        return new WithItemTy(contextExpr, optionalVars, sourceRange);
    }

    public StmtTy.With createWith(WithItemTy[] items, StmtTy[] body, String typeComment, SourceRange sourceRange) {
        return new StmtTy.With(items, body, typeComment, sourceRange);
    }

    public StmtTy.AsyncWith createAsyncWith(WithItemTy[] items, StmtTy[] body, String typeComment, SourceRange sourceRange) {
        return new StmtTy.AsyncWith(items, body, typeComment, sourceRange);
    }

    public ExprTy createAwait(ExprTy value, SourceRange sourceRange) {
        return new ExprTy.Await(value, sourceRange);
    }

    public ExprTy.NamedExpr createNamedExp(ExprTy target, ExprTy value, SourceRange sourceRange) {
        return new ExprTy.NamedExpr(target, value, sourceRange);
    }

    public ExprTy createGetAttribute(ExprTy receiver, String name, SourceRange sourceRange) {
        return createGetAttribute(receiver, name, null, sourceRange);
    }

    public ExprTy.Name createVariable(String name, SourceRange sourceRange) {
        return createVariable(name, sourceRange, ExprContextTy.Load);
    }

    public StmtTy createMatch(ExprTy subject, MatchCaseTy[] cases, SourceRange sourceRange) {
        return new StmtTy.Match(subject, cases, sourceRange);
    }

    public MatchCaseTy createMatchCase(PatternTy pattern, ExprTy guard, StmtTy[] body, SourceRange sourceRange) {
        return new MatchCaseTy(pattern, guard, body, sourceRange);
    }

    public PatternTy createMatchValue(ExprTy value, SourceRange sourceRange) {
        return new PatternTy.MatchValue(value, sourceRange);
    }

    public PatternTy createMatchSingleton(ConstantValue value, SourceRange sourceRange) {
        return new PatternTy.MatchSingleton(value, sourceRange);
    }

    public PatternTy createMatchSequence(PatternTy[] patterns, SourceRange sourceRange) {
        return new PatternTy.MatchSequence(patterns, sourceRange);
    }

    public PatternTy createMatchMapping(ExprTy[] keys, PatternTy[] patterns, String rest, SourceRange sourceRange) {
        return new PatternTy.MatchMapping(keys, patterns, rest, sourceRange);
    }

    public PatternTy createMatchClass(ExprTy cls, PatternTy[] patterns, String[] kwdAttrs, PatternTy[] kwdPatterns, SourceRange sourceRange) {
        return new PatternTy.MatchClass(cls, patterns, kwdAttrs, kwdPatterns, sourceRange);
    }

    public PatternTy createMatchStar(String name, SourceRange sourceRange) {
        return new PatternTy.MatchStar(name, sourceRange);
    }

    public PatternTy createMatchAs(PatternTy pattern, String name, SourceRange sourceRange) {
        return new PatternTy.MatchAs(pattern, name, sourceRange);
    }

    public PatternTy createMatchOr(PatternTy[] patterns, SourceRange sourceRange) {
        return new PatternTy.MatchOr(patterns, sourceRange);
    }
}
