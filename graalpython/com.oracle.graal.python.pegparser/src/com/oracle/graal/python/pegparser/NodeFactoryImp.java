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

// TODO this class has to be moved to impl package and from this package we need to do api.

import java.math.BigInteger;
import java.util.Arrays;

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
import com.oracle.graal.python.pegparser.sst.StringLiteralUtils;
import com.oracle.graal.python.pegparser.tokenizer.SourceRange;

public class NodeFactoryImp implements NodeFactory {
    @Override
    public StmtTy createAnnAssignment(ExprTy target, ExprTy annotation, ExprTy rhs, boolean isSimple, SourceRange sourceRange) {
        return new StmtTy.AnnAssign(target, annotation, rhs, isSimple, sourceRange);
    }

    @Override
    public StmtTy createAssert(ExprTy test, ExprTy msg, SourceRange sourceRange) {
        if (test == null) {
            // TODO Handle error if the field is null.
        }
        return new StmtTy.Assert(test, msg, sourceRange);
    }

    @Override
    public StmtTy createAssignment(ExprTy[] lhs, ExprTy rhs, String typeComment, SourceRange sourceRange) {
        return new StmtTy.Assign(lhs, rhs, typeComment, sourceRange);
    }

    @Override
    public StmtTy createAugAssignment(ExprTy lhs, ExprTy.BinOp.Operator operation, ExprTy rhs, SourceRange sourceRange) {
        return new StmtTy.AugAssign(lhs, operation, rhs, sourceRange);
    }

    @Override
    public ExprTy createBinaryOp(ExprTy.BinOp.Operator op, ExprTy left, ExprTy right, SourceRange sourceRange) {
        return new ExprTy.BinOp(left, op, right, sourceRange);
    }

    @Override
    public ModTy createModule(StmtTy[] statements, SourceRange sourceRange) {
        return new ModTy.Module(statements, null, sourceRange);
    }

    @Override
    public ModTy createExpressionModule(ExprTy expression, SourceRange sourceRange) {
        return new ModTy.Expression(expression, sourceRange);
    }

    @Override
    public ModTy createInteractiveModule(StmtTy[] body, SourceRange sourceRange) {
        return new ModTy.Interactive(body, sourceRange);
    }

    @Override
    public ExprTy createBooleanLiteral(boolean value, SourceRange sourceRange) {
        return new ExprTy.Constant(value, ExprTy.Constant.Kind.BOOLEAN, sourceRange);
    }

    @Override
    public ExprTy createNone(SourceRange sourceRange) {
        return new ExprTy.Constant(null, ExprTy.Constant.Kind.NONE, sourceRange);
    }

    @Override
    public ExprTy createEllipsis(SourceRange sourceRange) {
        return new ExprTy.Constant(null, ExprTy.Constant.Kind.ELLIPSIS, sourceRange);
    }

    @Override
    public ExprTy createGetAttribute(ExprTy receiver, String name, ExprContext context, SourceRange sourceRange) {
        return new ExprTy.Attribute(receiver, name, context, sourceRange);
    }

    @Override
    public StmtTy createPass(SourceRange sourceRange) {
        return new StmtTy.Pass(sourceRange);
    }

    @Override
    public StmtTy createBreak(SourceRange sourceRange) {
        return new StmtTy.Break(sourceRange);
    }

    @Override
    public StmtTy createExpression(ExprTy expr) {
        return new StmtTy.Expr(expr);
    }

    @Override
    public ExprTy createCall(ExprTy target, ExprTy[] args, KeywordTy[] kwargs, SourceRange sourceRange) {
        return new ExprTy.Call(target, args, kwargs, sourceRange);
    }

    @Override
    public StmtTy createContinue(SourceRange sourceRange) {
        return new StmtTy.Continue(sourceRange);
    }

    @Override
    public StmtTy createDelete(ExprTy[] targets, SourceRange sourceRange) {
        return new StmtTy.Delete(targets, sourceRange);
    }

    @Override
    public ExprTy createYield(ExprTy value, boolean isFrom, SourceRange sourceRange) {
        if (isFrom) {
            return new ExprTy.YieldFrom(value, sourceRange);
        } else {
            return new ExprTy.Yield(value, sourceRange);
        }
    }

    private static int digitValue(char ch) {
        if (ch >= '0' && ch <= '9') {
            return ch - '0';
        } else if (ch >= 'a' && ch <= 'f') {
            return ch - 'a' + 10;
        } else {
            assert ch >= 'A' && ch <= 'f';
            return ch - 'A' + 10;
        }
    }

    @Override
    public ExprTy createNumber(String numberWithUnderscores, SourceRange sourceRange) {
        String number = numberWithUnderscores;
        if (number.contains("_")) {
            number = number.replace("_", "");
        }
        int base = 10;
        int start = 0;
        boolean isFloat = false;
        boolean isComplex = false;

        if (number.startsWith("0")) {
            if (number.startsWith("0x") || number.startsWith("0X")) {
                base = 16;
                start = 2;
            } else if (number.startsWith("0o") || number.startsWith("0O")) {
                base = 8;
                start = 2;
            } else if (number.startsWith("0b") || number.startsWith("0B")) {
                base = 2;
                start = 2;
            }
        }
        if (base == 10) {
            isComplex = number.endsWith("j") || number.endsWith("J");
            if (!isComplex) {
                isFloat = number.contains(".") || number.contains("e") || number.contains("E");
            }
        }

        if (isComplex) {
            double imag = Double.parseDouble(number.substring(0, number.length() - 1));
            return new ExprTy.Constant(new double[]{0.0, imag},
                            ExprTy.Constant.Kind.COMPLEX,
                            sourceRange);
        } else if (isFloat) {
            return new ExprTy.Constant(Double.parseDouble(number),
                            ExprTy.Constant.Kind.DOUBLE,
                            sourceRange);
        } else {
            final long max = Long.MAX_VALUE;
            final long moltmax = max / base;
            int i = start;
            long result = 0;
            int lastD;
            boolean overunder = false;
            while (i < number.length()) {
                lastD = digitValue(number.charAt(i));

                long next = result;
                if (next > moltmax) {
                    overunder = true;
                } else {
                    next *= base;
                    if (next > (max - lastD)) {
                        overunder = true;
                    } else {
                        next += lastD;
                    }
                }
                if (overunder) {
                    // overflow
                    BigInteger bigResult = BigInteger.valueOf(result);
                    BigInteger bigBase = BigInteger.valueOf(base);
                    while (i < number.length()) {
                        bigResult = bigResult.multiply(bigBase).add(BigInteger.valueOf(digitValue(number.charAt(i))));
                        i++;
                    }
                    return new ExprTy.Constant(bigResult, ExprTy.Constant.Kind.BIGINTEGER, sourceRange);
                }
                result = next;
                i++;
            }
            return new ExprTy.Constant(result, ExprTy.Constant.Kind.LONG, sourceRange);
        }
    }

    @Override
    public ExprTy createString(String[] values, SourceRange[] sourceRanges, FExprParser exprParser, ParserErrorCallback errorCb) {
        return StringLiteralUtils.createStringLiteral(values, sourceRanges, exprParser, errorCb);
    }

    @Override
    public ExprTy createUnaryOp(ExprTy.UnaryOp.Operator op, ExprTy value, SourceRange sourceRange) {
        return new ExprTy.UnaryOp(op, value, sourceRange);
    }

    @Override
    public ExprTy.Name createVariable(String name, SourceRange sourceRange, ExprContext context) {
        return new ExprTy.Name(name, context, sourceRange);
    }

    @Override
    public ExprTy createStarred(ExprTy value, ExprContext context, SourceRange sourceRange) {
        return new ExprTy.Starred(value, context, sourceRange);
    }

    @Override
    public KeywordTy createKeyword(String arg, ExprTy value, SourceRange sourceRange) {
        return new KeywordTy(arg, value, sourceRange);
    }

    @Override
    public ArgTy createArgument(String argument, ExprTy annotation, String typeComment, SourceRange sourceRange) {
        return new ArgTy(argument, annotation, typeComment, sourceRange);
    }

    @Override
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
            posOnlyArgs = new ArgTy[0];
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
            posArgs = new ArgTy[0];
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
            kwOnlyArgs = new ArgTy[0];
            kwDefaults = AbstractParser.EMPTY_EXPR;
        }

        return new ArgumentsTy(posOnlyArgs, posArgs, starEtc != null ? starEtc.varArg : null, kwOnlyArgs, kwDefaults, starEtc != null ? starEtc.kwArg : null, posDefaults,
                        new SourceRange(0, 0, 0, 0, 0, 0));
    }

    @Override
    public ExprTy createComparison(ExprTy left, AbstractParser.CmpopExprPair[] pairs, SourceRange sourceRange) {
        ExprTy.Compare.Operator[] ops = new ExprTy.Compare.Operator[pairs.length];
        ExprTy[] rights = new ExprTy[pairs.length];
        for (int i = 0; i < pairs.length; i++) {
            ops[i] = pairs[i].op;
            rights[i] = pairs[i].expr;
        }
        return new ExprTy.Compare(left, ops, rights, sourceRange);
    }

    @Override
    public ExprTy createSubscript(ExprTy receiver, ExprTy subscript, ExprContext context, SourceRange sourceRange) {
        return new ExprTy.Subscript(receiver, subscript, context, sourceRange);
    }

    @Override
    public ExprTy createTuple(ExprTy[] values, ExprContext context, SourceRange sourceRange) {
        return new ExprTy.Tuple(values != null ? values : AbstractParser.EMPTY_EXPR, context, sourceRange);
    }

    @Override
    public ExprTy createList(ExprTy[] values, ExprContext context, SourceRange sourceRange) {
        return new ExprTy.List(values != null ? values : AbstractParser.EMPTY_EXPR, context, sourceRange);
    }

    @Override
    public ExprTy createDict(ExprTy[] keys, ExprTy[] values, SourceRange sourceRange) {
        return new ExprTy.Dict(keys, values, sourceRange);
    }

    @Override
    public ExprTy createSet(ExprTy[] values, SourceRange sourceRange) {
        return new ExprTy.Set(values, sourceRange);
    }

    @Override
    public ComprehensionTy createComprehension(ExprTy target, ExprTy iter, ExprTy[] ifs, boolean isAsync, SourceRange sourceRange) {
        return new ComprehensionTy(target, iter, ifs, isAsync, sourceRange);
    }

    @Override
    public ExprTy createListComprehension(ExprTy name, ComprehensionTy[] generators, SourceRange sourceRange) {
        return new ExprTy.ListComp(name, generators, sourceRange);
    }

    @Override
    public ExprTy createDictComprehension(AbstractParser.KeyValuePair name, ComprehensionTy[] generators, SourceRange sourceRange) {
        return new ExprTy.DictComp(name.key, name.value, generators, sourceRange);
    }

    @Override
    public ExprTy createSetComprehension(ExprTy name, ComprehensionTy[] generators, SourceRange sourceRange) {
        return new ExprTy.SetComp(name, generators, sourceRange);
    }

    @Override
    public ExprTy createGenerator(ExprTy name, ComprehensionTy[] generators, SourceRange sourceRange) {
        return new ExprTy.GeneratorExp(name, generators, sourceRange);
    }

    @Override
    public StmtTy createFunctionDef(String name, ArgumentsTy args, StmtTy[] body, ExprTy returns, String typeComment, SourceRange sourceRange) {
        return new StmtTy.FunctionDef(name, args, body, null, returns, typeComment, sourceRange);
    }

    @Override
    public StmtTy createFunctionDefWithDecorators(StmtTy funcDef, ExprTy[] decorators) {
        return ((StmtTy.FunctionDef) funcDef).copyWithDecorators(decorators);
    }

    @Override
    public StmtTy createAsyncFunctionDef(String name, ArgumentsTy args, StmtTy[] body, ExprTy returns, String typeComment, SourceRange sourceRange) {
        return new StmtTy.AsyncFunctionDef(name, args, body, null, returns, typeComment, sourceRange);
    }

    @Override
    public StmtTy createWhile(ExprTy condition, StmtTy[] block, StmtTy[] elseBlock, SourceRange sourceRange) {
        return new StmtTy.While(condition, block, elseBlock, sourceRange);
    }

    @Override
    public StmtTy createFor(ExprTy target, ExprTy iter, StmtTy[] block, StmtTy[] elseBlock, String typeComment, SourceRange sourceRange) {
        return new StmtTy.For(target, iter, block, elseBlock, typeComment, sourceRange);
    }

    @Override
    public StmtTy createReturn(ExprTy value, SourceRange sourceRange) {
        return new StmtTy.Return(value, sourceRange);
    }

    @Override
    public ExprTy createSlice(ExprTy start, ExprTy stop, ExprTy step, SourceRange sourceRange) {
        return new ExprTy.Slice(start, stop, step, sourceRange);
    }

    @Override
    public StmtTy createIf(ExprTy condition, StmtTy[] block, StmtTy[] orElse, SourceRange sourceRange) {
        return new StmtTy.If(condition, block, orElse, sourceRange);
    }

    @Override
    public ExprTy createIfExpression(ExprTy condition, ExprTy then, ExprTy orElse, SourceRange sourceRange) {
        return new ExprTy.IfExp(condition, then, orElse, sourceRange);
    }

    @Override
    public ExprTy createLambda(ArgumentsTy args, ExprTy body, SourceRange sourceRange) {
        return new ExprTy.Lambda(args, body, sourceRange);
    }

    @Override
    public StmtTy createClassDef(ExprTy name, ExprTy call, StmtTy[] body, SourceRange sourceRange) {
        return new StmtTy.ClassDef(((ExprTy.Name) name).id,
                        call == null ? AbstractParser.EMPTY_EXPR : ((ExprTy.Call) call).args,
                        call == null ? AbstractParser.EMPTY_KWDS : ((ExprTy.Call) call).keywords,
                        body, null, sourceRange);
    }

    @Override
    public StmtTy createClassDef(StmtTy proto, ExprTy[] decorators, SourceRange sourceRange) {
        StmtTy.ClassDef classdef = (StmtTy.ClassDef) proto;
        return new StmtTy.ClassDef(classdef.name, classdef.bases, classdef.keywords, classdef.body, decorators, sourceRange);
    }

    @Override
    public StmtTy createNonLocal(String[] names, SourceRange sourceRange) {
        return new StmtTy.NonLocal(names, sourceRange);
    }

    @Override
    public StmtTy createGlobal(String[] names, SourceRange sourceRange) {
        return new StmtTy.Global(names, sourceRange);
    }

    @Override
    public ExprTy createAnd(ExprTy[] values, SourceRange sourceRange) {
        return new ExprTy.BoolOp(ExprTy.BoolOp.Type.And, values, sourceRange);
    }

    @Override
    public ExprTy createOr(ExprTy[] values, SourceRange sourceRange) {
        return new ExprTy.BoolOp(ExprTy.BoolOp.Type.Or, values, sourceRange);
    }

    @Override
    public StmtTy createRaise(ExprTy object, ExprTy from, SourceRange sourceRange) {
        return new StmtTy.Raise(object, from, sourceRange);
    }

    @Override
    public StmtTy createImport(AliasTy[] names, SourceRange sourceRange) {
        return new StmtTy.Import(names, sourceRange);
    }

    @Override
    public StmtTy createImportFrom(String fromName, AliasTy[] names, int level, SourceRange sourceRange) {
        return new StmtTy.ImportFrom(fromName, names, level, sourceRange);
    }

    @Override
    public AliasTy createAlias(String name, String asName, SourceRange sourceRange) {
        return new AliasTy(name, asName, sourceRange);
    }

    @Override
    public StmtTy createTry(StmtTy[] body, StmtTy.Try.ExceptHandler[] handlers, StmtTy[] orElse, StmtTy[] finalBody, SourceRange sourceRange) {
        return new StmtTy.Try(body, handlers, orElse, finalBody, sourceRange);
    }

    @Override
    public StmtTy.Try.ExceptHandler createExceptHandler(ExprTy type, String name, StmtTy[] body, SourceRange sourceRange) {
        return new StmtTy.Try.ExceptHandler(type, name, body, sourceRange);
    }

    @Override
    public StmtTy.With.Item createWithItem(ExprTy contextExpr, ExprTy optionalVars, SourceRange sourceRange) {
        // TODO check if context expr is not null -> throw error
        return new StmtTy.With.Item(contextExpr, optionalVars, sourceRange);
    }

    @Override
    public StmtTy.With createWith(StmtTy.With.Item[] items, StmtTy[] body, String typeComment, SourceRange sourceRange) {
        return new StmtTy.With(items, body, typeComment, sourceRange);
    }

    @Override
    public ExprTy createAwait(ExprTy value, SourceRange sourceRange) {
        return new ExprTy.Await(value, sourceRange);
    }

    @Override
    public ExprTy.NamedExpr createNamedExp(ExprTy target, ExprTy value, SourceRange sourceRange) {
        return new ExprTy.NamedExpr(target, value, sourceRange);
    }
}
