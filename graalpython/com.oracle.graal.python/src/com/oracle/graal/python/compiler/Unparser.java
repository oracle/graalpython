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
package com.oracle.graal.python.compiler;

import static com.oracle.graal.python.util.PythonUtils.TS_ENCODING;
import static com.oracle.graal.python.util.PythonUtils.tsLiteral;

import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.objects.bytes.BytesUtils;
import com.oracle.graal.python.builtins.objects.floats.FloatBuiltins;
import com.oracle.graal.python.builtins.objects.str.StringNodes;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.pegparser.sst.AliasTy;
import com.oracle.graal.python.pegparser.sst.ArgTy;
import com.oracle.graal.python.pegparser.sst.ArgumentsTy;
import com.oracle.graal.python.pegparser.sst.BoolOpTy;
import com.oracle.graal.python.pegparser.sst.ComprehensionTy;
import com.oracle.graal.python.pegparser.sst.ConstantValue;
import com.oracle.graal.python.pegparser.sst.ConstantValue.Kind;
import com.oracle.graal.python.pegparser.sst.ExceptHandlerTy;
import com.oracle.graal.python.pegparser.sst.ExprTy;
import com.oracle.graal.python.pegparser.sst.KeywordTy;
import com.oracle.graal.python.pegparser.sst.MatchCaseTy;
import com.oracle.graal.python.pegparser.sst.ModTy;
import com.oracle.graal.python.pegparser.sst.PatternTy;
import com.oracle.graal.python.pegparser.sst.SSTNode;
import com.oracle.graal.python.pegparser.sst.SSTreeVisitor;
import com.oracle.graal.python.pegparser.sst.StmtTy;
import com.oracle.graal.python.pegparser.sst.TypeIgnoreTy;
import com.oracle.graal.python.pegparser.sst.WithItemTy;
import com.oracle.graal.python.runtime.formatting.ComplexFormatter;
import com.oracle.graal.python.runtime.formatting.FloatFormatter;
import com.oracle.graal.python.runtime.formatting.InternalFormat.Spec;
import com.oracle.truffle.api.strings.TruffleString;
import com.oracle.truffle.api.strings.TruffleStringBuilder;

public class Unparser implements SSTreeVisitor<Void> {
    public static TruffleString unparse(SSTNode node) {
        return unparse(node, PR_TEST);
    }

    private static TruffleString unparse(SSTNode node, int level) {
        TruffleStringBuilder builder = TruffleStringBuilder.create(TS_ENCODING);
        node.accept(new Unparser(builder, level));
        return builder.toStringUncached();
    }

    private Unparser(TruffleStringBuilder builder, int level) {
        this.builder = builder;
        this.level = level;
    }

    private TruffleStringBuilder builder;
    private int level;

    private static final int PR_TUPLE = 0;
    private static final int PR_TEST = 1; /* 'if'-'else', 'lambda' */
    private static final int PR_OR = 2; /* 'or' */
    private static final int PR_AND = 3; /* 'and' */
    private static final int PR_NOT = 4; /* 'not' */
    private static final int PR_CMP = 5; /*
                                          * '<', '>', '==', '>=', '<=', '!=', 'in', 'not in', 'is',
                                          * 'is not'
                                          */
    private static final int PR_EXPR = 6;
    private static final int PR_BOR = PR_EXPR; /* '|' */
    private static final int PR_BXOR = 7; /* '^' */
    private static final int PR_BAND = 8; /* '&' */
    private static final int PR_SHIFT = 9; /* '<<', '>>' */
    private static final int PR_ARITH = 10; /* '+', '-' */
    private static final int PR_TERM = 11; /* '*', '@', '/', '%', '//' */
    private static final int PR_FACTOR = 12; /* unary '+', '-', '~' */
    private static final int PR_POWER = 13; /* '**' */
    private static final int PR_AWAIT = 14; /* 'await' */
    private static final int PR_ATOM = 15;

    private void appendStr(TruffleString temp_fv_str) {
        builder.appendStringUncached(temp_fv_str);
    }

    private void appendStr(String s) {
        appendStr(TruffleString.fromJavaStringUncached(s, TS_ENCODING));
    }

    private void appendStrIf(boolean cond, String s) {
        if (cond) {
            appendStr(s);
        }
    }

    private void appendExpr(SSTNode node, int newLevel) {
        int savedLevel = this.level;
        this.level = newLevel;
        node.accept(this);
        this.level = savedLevel;
    }

    int len(SSTNode[] a) {
        return a != null ? a.length : 0;
    }

    private static final TruffleString OPEN_BR = tsLiteral("{");
    private static final TruffleString DBL_OPEN_BR = tsLiteral("{{");
    private static final TruffleString CLOSE_BR = tsLiteral("}");

    private static final TruffleString DBL_CLOSE_BR = tsLiteral("}}");

    private static TruffleString escapeBraces(TruffleString s) {
        StringNodes.StringReplaceNode replace = StringNodes.StringReplaceNode.getUncached();
        TruffleString t = replace.execute(s, OPEN_BR, DBL_OPEN_BR, -1);
        return replace.execute(t, CLOSE_BR, DBL_CLOSE_BR, -1);
    }

    private void appendFString(TruffleString string) {
        appendStr(escapeBraces(string));
    }

    private void appendFStringElement(ExprTy e, boolean isFormatSpec) {
        if (e instanceof ExprTy.Constant) {
            appendFString(((ExprTy.Constant) e).value.getRaw(TruffleString.class));
        } else if (e instanceof ExprTy.JoinedStr) {
            appendJoinedStr((ExprTy.JoinedStr) e, isFormatSpec);
        } else if (e instanceof ExprTy.FormattedValue) {
            visit((ExprTy.FormattedValue) e);
        } else {
            throw new IllegalStateException("unknown expression kind inside f-string");
        }
    }

    public TruffleString buildFStringBody(ExprTy[] values, boolean isFormatSpec) {
        TruffleStringBuilder savedBuilder = builder;
        builder = TruffleStringBuilder.create(TS_ENCODING);
        for (int i = 0; i < values.length; i++) {
            appendFStringElement(values[i], isFormatSpec);
        }
        TruffleString result = builder.toStringUncached();
        builder = savedBuilder;
        return result;
    }

    private void appendJoinedStr(ExprTy.JoinedStr node, boolean isFormatSpec) {
        TruffleString body = buildFStringBody(node.values, isFormatSpec);
        if (!isFormatSpec) {
            appendStr("f");
            appendStr(StringNodes.StringReprNode.getUncached().execute(body));
        } else {
            appendStr(body);
        }
    }

    @Override
    public Void visit(AliasTy node) {
        throw new IllegalStateException("unknown expression kind");
    }

    @Override
    public Void visit(ArgTy node) {
        appendStr(node.arg);
        if (node.annotation != null) {
            appendStr(": ");
            appendExpr(node.annotation, PR_TEST);
        }
        return null;
    }

    @Override
    public Void visit(ArgumentsTy args) {
        boolean first = true;
        int argCount, posonlyargCount, defaultCount;

        /* positional-only and positional arguments with defaults */
        posonlyargCount = len(args.posOnlyArgs);
        argCount = len(args.args);
        defaultCount = len(args.defaults);
        for (int i = 0; i < posonlyargCount + argCount; i++) {
            if (first) {
                first = false;
            } else {
                appendStr(", ");
            }
            if (i < posonlyargCount) {
                visit(args.posOnlyArgs[i]);
            } else {
                visit(args.args[i - posonlyargCount]);
            }

            int di = i - posonlyargCount - argCount + defaultCount;
            if (di >= 0) {
                appendStr("=");
                appendExpr(args.defaults[di], PR_TEST);
            }
            if (posonlyargCount > 0 && i + 1 == posonlyargCount) {
                appendStr(", /");
            }
        }

        /* vararg, or bare '*' if no varargs but keyword-only arguments present */
        if (args.varArg != null || len(args.kwOnlyArgs) > 0) {
            if (first) {
                first = false;
            } else {
                appendStr(", ");
            }
            appendStr("*");
            if (args.varArg != null) {
                visit(args.varArg);
            }
        }

        /* keyword-only arguments */
        argCount = len(args.kwOnlyArgs);
        defaultCount = len(args.kwDefaults);
        for (int i = 0; i < argCount; i++) {
            if (first) {
                first = false;
            } else {
                appendStr(", ");
            }
            visit(args.kwOnlyArgs[i]);

            int di = i - argCount + defaultCount;
            if (di >= 0) {
                ExprTy kwDefault = args.kwDefaults[di];
                if (kwDefault != null) {
                    appendStr("=");
                    appendExpr(kwDefault, PR_TEST);
                }
            }
        }

        /* **kwargs */
        if (args.kwArg != null) {
            if (!first) {
                appendStr(", ");
            }
            appendStr("**");
            visit(args.kwArg);
        }
        return null;
    }

    @Override
    public Void visit(ComprehensionTy node) {
        appendStr(node.isAsync ? " async for " : " for ");
        appendExpr(node.target, PR_TUPLE);
        appendStr(" in ");
        appendExpr(node.iter, PR_TEST + 1);

        int ifCount = len(node.ifs);
        for (int i = 0; i < ifCount; i++) {
            appendStr(" if ");
            appendExpr(node.ifs[i], PR_TEST + 1);
        }
        return null;
    }

    private void appendComprehensions(ComprehensionTy[] comprehensions) {
        for (int i = 0; i < comprehensions.length; i++) {
            visit(comprehensions[i]);
        }
    }

    @Override
    public Void visit(ExprTy.Attribute node) {
        String period;
        ExprTy v = node.value;
        appendExpr(v, PR_ATOM);

        /*
         * Special case: integers require a space for attribute access to be unambiguous.
         */
        if (v instanceof ExprTy.Constant && (((ExprTy.Constant) v).value.kind == ConstantValue.Kind.LONG || ((ExprTy.Constant) v).value.kind == ConstantValue.Kind.BIGINTEGER)) {
            period = " .";
        } else {
            period = ".";
        }
        appendStr(period);

        appendStr(node.attr);
        return null;
    }

    @Override
    public Void visit(ExprTy.Await node) {
        appendStrIf(level > PR_AWAIT, "(");
        appendStr("await ");
        appendExpr(node.value, PR_ATOM);
        appendStrIf(level > PR_AWAIT, ")");
        return null;
    }

    @Override
    public Void visit(ExprTy.BinOp node) {
        int pr;
        String op;
        boolean rassoc = false;
        switch (node.op) {
            case Add:
                op = " + ";
                pr = PR_ARITH;
                break;
            case Sub:
                op = " - ";
                pr = PR_ARITH;
                break;
            case Mult:
                op = " * ";
                pr = PR_TERM;
                break;
            case MatMult:
                op = " @ ";
                pr = PR_TERM;
                break;
            case Div:
                op = " / ";
                pr = PR_TERM;
                break;
            case Mod:
                op = " % ";
                pr = PR_TERM;
                break;
            case LShift:
                op = " << ";
                pr = PR_SHIFT;
                break;
            case RShift:
                op = " >> ";
                pr = PR_SHIFT;
                break;
            case BitOr:
                op = " | ";
                pr = PR_BOR;
                break;
            case BitXor:
                op = " ^ ";
                pr = PR_BXOR;
                break;
            case BitAnd:
                op = " & ";
                pr = PR_BAND;
                break;
            case FloorDiv:
                op = " // ";
                pr = PR_TERM;
                break;
            case Pow:
                op = " ** ";
                pr = PR_POWER;
                rassoc = true;
                break;
            default:
                throw new IllegalStateException("unknown binary operator");
        }
        appendStrIf(level > pr, "(");
        appendExpr(node.left, pr + (rassoc ? 1 : 0));
        appendStr(op);
        appendExpr(node.right, pr + (rassoc ? 0 : 1));
        appendStrIf(level > pr, ")");
        return null;
    }

    @Override
    public Void visit(ExprTy.BoolOp node) {
        String op = node.op == BoolOpTy.And ? " and " : " or ";
        int pr = node.op == BoolOpTy.And ? PR_AND : PR_OR;
        appendStrIf(level > pr, "(");
        for (int i = 0; i < node.values.length; i++) {
            appendStrIf(i > 0, op);
            appendExpr(node.values[i], pr + 1);
        }
        appendStrIf(level > pr, ")");
        return null;
    }

    @Override
    public Void visit(ExprTy.Call node) {
        appendExpr(node.func, PR_ATOM);

        int argCount = len(node.args);
        int kwCount = len(node.keywords);
        if (argCount == 1 && kwCount == 0) {
            ExprTy expr = node.args[0];
            if (expr instanceof ExprTy.GeneratorExp) {
                /* Special case: a single generator expression. */
                return visit((ExprTy.GeneratorExp) expr);
            }
        }

        appendStr("(");

        boolean first = true;
        for (int i = 0; i < argCount; i++) {
            if (first) {
                first = false;
            } else {
                appendStr(", ");
            }
            appendExpr(node.args[i], PR_TEST);
        }

        for (int i = 0; i < kwCount; i++) {
            if (first) {
                first = false;
            } else {
                appendStr(", ");
            }
            visit(node.keywords[i]);
        }

        appendStr(")");
        return null;
    }

    @Override
    public Void visit(ExprTy.Compare node) {
        String op;
        appendStrIf(level > PR_CMP, "(");
        int comparatorCount = len(node.comparators);
        assert (comparatorCount > 0);
        assert (comparatorCount == node.ops.length);

        appendExpr(node.left, PR_CMP + 1);

        for (int i = 0; i < comparatorCount; i++) {
            switch (node.ops[i]) {
                case Eq:
                    op = " == ";
                    break;
                case NotEq:
                    op = " != ";
                    break;
                case Lt:
                    op = " < ";
                    break;
                case LtE:
                    op = " <= ";
                    break;
                case Gt:
                    op = " > ";
                    break;
                case GtE:
                    op = " >= ";
                    break;
                case Is:
                    op = " is ";
                    break;
                case IsNot:
                    op = " is not ";
                    break;
                case In:
                    op = " in ";
                    break;
                case NotIn:
                    op = " not in ";
                    break;
                default:
                    throw new IllegalStateException("unexpected comparison kind");
            }

            appendStr(op);
            appendExpr(node.comparators[i], PR_CMP + 1);
        }

        appendStrIf(level > PR_CMP, ")");
        return null;
    }

    @Override
    public Void visit(ExprTy.Constant node) {
        // equivalent of case Constant_kind in append_ast_expr
        if (node.value.kind == Kind.ELLIPSIS) {
            appendStr("...");
            return null;
        }
        if (node.kind instanceof String) {
            appendStr((String) node.kind);
        } else if (node.kind instanceof byte[]) {
            // This conversion of byte[] -> String might not be correct, but CPython crashes in this
            // case so nobody cares and also "kind" should only be 'u' or b'u' if present.
            appendStr(new String((byte[]) node.kind));
        }
        appendConstantValue(node.value);
        return null;
    }

    private void appendConstantValue(ConstantValue value) {
        switch (value.kind) {
            case LONG:
                builder.appendLongNumberUncached(value.getLong());
                break;
            case DOUBLE:
                FloatFormatter f = new FloatFormatter(null, FloatBuiltins.StrNode.spec);
                f.setMinFracDigits(1);
                TruffleString result = f.format(value.getDouble()).getResult();
                appendStr(result);
                break;
            case BOOLEAN:
                appendStr(value.getBoolean() ? "True" : "False");
                break;
            case RAW:
                appendStr(StringNodes.StringReprNode.getUncached().execute(value.getRaw(TruffleString.class)));
                break;
            case BIGINTEGER:
                appendStr(value.getBigInteger().toString());
                break;
            case NONE:
                appendStr("None");
                break;
            case BYTES:
                byte[] bytes = value.getBytes();
                BytesUtils.reprLoop(builder, bytes, bytes.length, TruffleStringBuilder.AppendCodePointNode.getUncached());
                break;
            case COMPLEX:
                double[] num = value.getComplex();
                ComplexFormatter formatter = new ComplexFormatter(null, new Spec(-1, Spec.NONE));
                formatter.format(num[0], num[1]);
                appendStr(formatter.pad().getResult());
                break;
            case ELLIPSIS:
                appendStr("Ellipsis");
                break;
            case TUPLE:
                appendTuple(value.getTupleElements());
                break;
            case FROZENSET:
                appendFrozenset(value.getFrozensetElements());
                break;
            default:
                throw new IllegalStateException("unknown constant kind");
        }
    }

    private void appendTuple(ConstantValue[] values) {
        appendStr("(");
        for (int i = 0; i < values.length; ++i) {
            appendStrIf(i > 0, ", ");
            appendConstantValue(values[i]);
        }
        appendStrIf(values.length == 1, ",");
        appendStr(")");
    }

    private void appendFrozenset(ConstantValue[] values) {
        appendStr("frozenset(");
        if (values.length > 0) {
            appendStr("{");
            for (int i = 0; i < values.length; ++i) {
                appendStrIf(i > 0, ", ");
                appendConstantValue(values[i]);
            }
            appendStr("}");
        }
        appendStr(")");
    }

    @Override
    public Void visit(ExprTy.Dict node) {
        appendStr("{");
        for (int i = 0; i < len(node.values); i++) {
            appendStrIf(i > 0, ", ");
            ExprTy key_node = node.keys[i];
            if (key_node != null) {
                appendExpr(key_node, PR_TEST);
                appendStr(": ");
                appendExpr(node.values[i], PR_TEST);
            } else {
                appendStr("**");
                appendExpr(node.values[i], PR_EXPR);
            }
        }

        appendStr("}");
        return null;
    }

    @Override
    public Void visit(ExprTy.DictComp node) {
        appendStr("{");
        appendExpr(node.key, PR_TEST);
        appendStr(": ");
        appendExpr(node.value, PR_TEST);
        appendComprehensions(node.generators);
        appendStr("}");
        return null;
    }

    @Override
    public Void visit(ExprTy.FormattedValue node) {
        String conversion;
        String outerBrace = "{";
        /*
         * Grammar allows PR_TUPLE, but use >PR_TEST for adding parenthesis around a lambda with ':'
         */
        TruffleString tempFvStr = unparse(node.value, PR_TEST + 1);
        if (tempFvStr.codePointAtByteIndexUncached(0, TS_ENCODING) == '{') {
            /*
             * Expression starts with a brace, split it with a space from the outer one.
             */
            outerBrace = "{ ";
        }
        appendStr(outerBrace);
        appendStr(tempFvStr);

        if (node.conversion > 0) {
            switch (node.conversion) {
                case 'a':
                    conversion = "!a";
                    break;
                case 'r':
                    conversion = "!r";
                    break;
                case 's':
                    conversion = "!s";
                    break;
                default:
                    throw PRaiseNode.getUncached().raise(PythonBuiltinClassType.SystemError, ErrorMessages.UNKNOWN_F_VALUE_CONVERSION_KIND);
            }
            appendStr(conversion);
        }
        if (node.formatSpec != null) {
            appendStr(":");
            appendFStringElement(node.formatSpec, true);
        }

        appendStr("}");
        return null;
    }

    @Override
    public Void visit(ExprTy.GeneratorExp node) {
        appendStr("(");
        appendExpr(node.element, PR_TEST);
        appendComprehensions(node.generators);
        appendStr(")");
        return null;
    }

    @Override
    public Void visit(ExprTy.IfExp node) {
        appendStrIf(level > PR_TEST, "(");
        appendExpr(node.body, PR_TEST + 1);
        appendStr(" if ");
        appendExpr(node.test, PR_TEST + 1);
        appendStr(" else ");
        appendExpr(node.orElse, PR_TEST);
        appendStrIf(level > PR_TEST, ")");
        return null;
    }

    @Override
    public Void visit(ExprTy.JoinedStr node) {
        appendJoinedStr(node, false);
        return null;
    }

    @Override
    public Void visit(ExprTy.Lambda node) {
        appendStrIf(level > PR_TEST, "(");
        int nPositional = len(node.args.args) + len(node.args.posOnlyArgs);
        appendStr(nPositional > 0 ? "lambda " : "lambda");
        visit(node.args);
        appendStr(": ");
        appendExpr(node.body, PR_TEST);
        appendStrIf(level > PR_TEST, ")");
        return null;
    }

    @Override
    public Void visit(ExprTy.List node) {
        appendStr("[");
        for (int i = 0; i < len(node.elements); i++) {
            appendStrIf(i > 0, ", ");
            appendExpr(node.elements[i], PR_TEST);
        }
        appendStr("]");
        return null;
    }

    @Override
    public Void visit(ExprTy.ListComp node) {
        appendStr("(");
        appendExpr(node.element, PR_TEST);
        appendComprehensions(node.generators);
        appendStr(")");
        return null;
    }

    @Override
    public Void visit(ExprTy.Name node) {
        appendStr(node.id);
        return null;
    }

    @Override
    public Void visit(ExprTy.NamedExpr node) {
        appendStrIf(level > PR_TUPLE, "(");
        appendExpr(node.target, PR_ATOM);
        appendStr(" := ");
        appendExpr(node.value, PR_ATOM);
        appendStrIf(level > PR_TUPLE, ")");
        return null;
    }

    @Override
    public Void visit(ExprTy.Set node) {
        appendStr("{");
        for (int i = 0; i < len(node.elements); i++) {
            appendStrIf(i > 0, ", ");
            appendExpr(node.elements[i], PR_TEST);
        }
        appendStr("}");
        return null;
    }

    @Override
    public Void visit(ExprTy.SetComp node) {
        appendStr("(");
        appendExpr(node.element, PR_TEST);
        appendComprehensions(node.generators);
        appendStr(")");
        return null;
    }

    @Override
    public Void visit(ExprTy.Slice node) {
        if (node.lower != null) {
            appendExpr(node.lower, PR_TEST);
        }

        appendStr(":");

        if (node.upper != null) {
            appendExpr(node.upper, PR_TEST);
        }

        if (node.step != null) {
            appendStr(":");
            appendExpr(node.step, PR_TEST);
        }
        return null;
    }

    @Override
    public Void visit(ExprTy.Starred node) {
        appendStr("*");
        appendExpr(node.value, PR_EXPR);
        return null;
    }

    @Override
    public Void visit(ExprTy.Subscript node) {
        appendExpr(node.value, PR_ATOM);
        appendStr("[");
        appendExpr(node.slice, PR_TUPLE);
        appendStr("]");
        return null;
    }

    @Override
    public Void visit(ExprTy.Tuple node) {
        int elemCount = len(node.elements);

        if (elemCount == 0) {
            appendStr("()");
            return null;
        }

        appendStrIf(level > PR_TUPLE, "(");

        for (int i = 0; i < elemCount; i++) {
            appendStrIf(i > 0, ", ");
            appendExpr(node.elements[i], PR_TEST);
        }

        appendStrIf(elemCount == 1, ",");
        appendStrIf(level > PR_TUPLE, ")");
        return null;
    }

    @Override
    public Void visit(ExprTy.UnaryOp node) {
        String op;
        int pr;
        switch (node.op) {
            case Invert:
                op = "~";
                pr = PR_FACTOR;
                break;
            case Not:
                op = "not ";
                pr = PR_NOT;
                break;
            case UAdd:
                op = "+";
                pr = PR_FACTOR;
                break;
            case USub:
                op = "-";
                pr = PR_FACTOR;
                break;
            default:
                throw new IllegalStateException("unknown unary operator");
        }
        appendStrIf(level > pr, "(");
        appendStr(op);
        appendExpr(node.operand, pr);
        appendStrIf(level > pr, ")");
        return null;
    }

    @Override
    public Void visit(ExprTy.Yield node) {
        if (node.value == null) {
            appendStr("(yield)");
            return null;
        }

        appendStr("(yield ");
        appendExpr(node.value, PR_TEST);
        appendStr(")");
        return null;
    }

    @Override
    public Void visit(ExprTy.YieldFrom node) {
        appendStr("(yield from ");
        appendExpr(node.value, PR_TEST);
        appendStr(")");
        return null;
    }

    @Override
    public Void visit(KeywordTy node) {
        if (node.arg == null) {
            appendStr("**");
        } else {
            appendStr(node.arg);
            appendStr("=");
        }

        appendExpr(node.value, PR_TEST);
        return null;
    }

    @Override
    public Void visit(ModTy.Expression node) {
        throw new IllegalStateException("unknown expression kind");
    }

    @Override
    public Void visit(ModTy.FunctionType node) {
        throw new IllegalStateException("unknown expression kind");
    }

    @Override
    public Void visit(ModTy.Interactive node) {
        throw new IllegalStateException("unknown expression kind");
    }

    @Override
    public Void visit(ModTy.Module node) {
        throw new IllegalStateException("unknown expression kind");
    }

    @Override
    public Void visit(TypeIgnoreTy.TypeIgnore node) {
        throw new IllegalStateException("unknown expression kind");
    }

    @Override
    public Void visit(StmtTy.AnnAssign node) {
        throw new IllegalStateException("unknown expression kind");
    }

    @Override
    public Void visit(StmtTy.Assert node) {
        throw new IllegalStateException("unknown expression kind");
    }

    @Override
    public Void visit(StmtTy.Assign node) {
        throw new IllegalStateException("unknown expression kind");
    }

    @Override
    public Void visit(StmtTy.AsyncFor node) {
        throw new IllegalStateException("unknown expression kind");
    }

    @Override
    public Void visit(StmtTy.AsyncFunctionDef node) {
        throw new IllegalStateException("unknown expression kind");
    }

    @Override
    public Void visit(StmtTy.AsyncWith node) {
        throw new IllegalStateException("unknown expression kind");
    }

    @Override
    public Void visit(StmtTy.AugAssign node) {
        throw new IllegalStateException("unknown expression kind");
    }

    @Override
    public Void visit(StmtTy.ClassDef node) {
        throw new IllegalStateException("unknown expression kind");
    }

    @Override
    public Void visit(StmtTy.Delete node) {
        throw new IllegalStateException("unknown expression kind");
    }

    @Override
    public Void visit(StmtTy.Expr node) {
        throw new IllegalStateException("unknown expression kind");
    }

    @Override
    public Void visit(StmtTy.For node) {
        throw new IllegalStateException("unknown expression kind");
    }

    @Override
    public Void visit(StmtTy.FunctionDef node) {
        throw new IllegalStateException("unknown expression kind");
    }

    @Override
    public Void visit(StmtTy.Global node) {
        throw new IllegalStateException("unknown expression kind");
    }

    @Override
    public Void visit(StmtTy.If node) {
        throw new IllegalStateException("unknown expression kind");
    }

    @Override
    public Void visit(StmtTy.Import node) {
        throw new IllegalStateException("unknown expression kind");
    }

    @Override
    public Void visit(StmtTy.ImportFrom node) {
        throw new IllegalStateException("unknown expression kind");
    }

    @Override
    public Void visit(StmtTy.Match node) {
        throw new IllegalStateException("unknown expression kind");
    }

    @Override
    public Void visit(MatchCaseTy node) {
        throw new IllegalStateException("unknown expression kind");
    }

    @Override
    public Void visit(PatternTy.MatchAs node) {
        throw new IllegalStateException("unknown expression kind");
    }

    @Override
    public Void visit(PatternTy.MatchClass node) {
        throw new IllegalStateException("unknown expression kind");
    }

    @Override
    public Void visit(PatternTy.MatchMapping node) {
        throw new IllegalStateException("unknown expression kind");
    }

    @Override
    public Void visit(PatternTy.MatchOr node) {
        throw new IllegalStateException("unknown expression kind");
    }

    @Override
    public Void visit(PatternTy.MatchSequence node) {
        throw new IllegalStateException("unknown expression kind");
    }

    @Override
    public Void visit(PatternTy.MatchSingleton node) {
        throw new IllegalStateException("unknown expression kind");
    }

    @Override
    public Void visit(PatternTy.MatchStar node) {
        throw new IllegalStateException("unknown expression kind");
    }

    @Override
    public Void visit(PatternTy.MatchValue node) {
        throw new IllegalStateException("unknown expression kind");
    }

    @Override
    public Void visit(StmtTy.Nonlocal node) {
        throw new IllegalStateException("unknown expression kind");
    }

    @Override
    public Void visit(StmtTy.Raise node) {
        throw new IllegalStateException("unknown expression kind");
    }

    @Override
    public Void visit(StmtTy.Return node) {
        throw new IllegalStateException("unknown expression kind");
    }

    @Override
    public Void visit(StmtTy.Try node) {
        throw new IllegalStateException("unknown expression kind");
    }

    @Override
    public Void visit(StmtTy.TryStar node) {
        throw new IllegalStateException("unknown expression kind");
    }

    @Override
    public Void visit(ExceptHandlerTy.ExceptHandler node) {
        throw new IllegalStateException("unknown expression kind");
    }

    @Override
    public Void visit(StmtTy.While node) {
        throw new IllegalStateException("unknown expression kind");
    }

    @Override
    public Void visit(StmtTy.With node) {
        throw new IllegalStateException("unknown expression kind");
    }

    @Override
    public Void visit(WithItemTy node) {
        throw new IllegalStateException("unknown expression kind");
    }

    @Override
    public Void visit(StmtTy.Break aThis) {
        throw new IllegalStateException("unknown expression kind");
    }

    @Override
    public Void visit(StmtTy.Continue aThis) {
        throw new IllegalStateException("unknown expression kind");
    }

    @Override
    public Void visit(StmtTy.Pass aThis) {
        throw new IllegalStateException("unknown expression kind");
    }
}
