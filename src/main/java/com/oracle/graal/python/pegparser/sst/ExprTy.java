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

import com.oracle.graal.python.pegparser.ExprContext;

public abstract class ExprTy extends SSTNode {
    ExprTy(int startOffset, int endOffset) {
        super(startOffset, endOffset);
    }

    public static final class BoolOp extends ExprTy {
        public BoolOp(Type op, ExprTy[] values, int startOffset, int endOffset) {
            super(startOffset, endOffset);
            this.op = op;
            this.values = values;
        }

        public static enum Type {
            And, Or;
        }

        public final Type op;
        public final ExprTy[] values;

        @Override
        public <T> T accept(SSTreeVisitor<T> visitor) {
            return visitor.visit(this);
        }
    }

    public static final class NamedExpr extends ExprTy {
        public final ExprTy target;
        public final ExprTy value;

        public NamedExpr(ExprTy target, ExprTy value, int startOffset, int endOffset) {
            super(startOffset, endOffset);
            this.target = target;
            this.value = value;
        }

        @Override
        public <T> T accept(SSTreeVisitor<T> visitor) {
            return visitor.visit(this);
        }
    }

    public static final class BinOp extends ExprTy {
        public static enum Operator {
            ADD, SUB, MULT, MATMULT, DIV, MOD, POW,
            LSHIFT, RSHIFT, BITOR, BITXOR, BITAND,
            FLOORDIV;
        }

        public final ExprTy left;
        public final Operator op;
        public final ExprTy right;

        public BinOp(ExprTy left, Operator op, ExprTy right, int startOffset, int endOffset) {
            super(startOffset, endOffset);
            this.left = left;
            this.op = op;
            this.right = right;
        }

        @Override
        public <T> T accept(SSTreeVisitor<T> visitor) {
            return visitor.visit(this);
        }
    }

    public static final class UnaryOp extends ExprTy {
        public static enum Operator {
            INVERT, NOT, ADD, SUB;
        }

        public final Operator op;
        public final ExprTy operand;

        public UnaryOp(Operator op, ExprTy operand, int startOffset, int endOffset) {
            super(startOffset, endOffset);
            this.op = op;
            this.operand = operand;
        }

        @Override
        public <T> T accept(SSTreeVisitor<T> visitor) {
            return visitor.visit(this);
        }
    }

    public static final class Lambda extends ExprTy {
        public final ArgumentsTy args;
        public final ExprTy body;

        public Lambda(ArgumentsTy args, ExprTy body, int startOffset, int endOffset) {
            super(startOffset, endOffset);
            this.args = args;
            this.body = body;
        }

        @Override
        public <T> T accept(SSTreeVisitor<T> visitor) {
            return visitor.visit(this);
        }
    }

    public static final class IfExp extends ExprTy {
        public final ExprTy test;
        public final ExprTy body;
        public final ExprTy orElse;

        public IfExp(ExprTy test, ExprTy body, ExprTy orElse, int startOffset, int endOffset) {
            super(startOffset, endOffset);
            this.test = test;
            this.body = body;
            this.orElse = orElse;
        }

        @Override
        public <T> T accept(SSTreeVisitor<T> visitor) {
            return visitor.visit(this);
        }
    }

    public static final class Dict extends ExprTy {
        public final ExprTy[] keys;
        public final ExprTy[] values;

        public Dict(ExprTy[] keys, ExprTy[] values, int startOffset, int endOffset) {
            super(startOffset, endOffset);
            this.keys = keys;
            this.values = values;
        }

        @Override
        public <T> T accept(SSTreeVisitor<T> visitor) {
            return visitor.visit(this);
        }
    }

    public static final class Set extends ExprTy {
        public final ExprTy[] elements;

        public Set(ExprTy[] elements, int startOffset, int endOffset) {
            super(startOffset, endOffset);
            this.elements = elements;
        }

        @Override
        public <T> T accept(SSTreeVisitor<T> visitor) {
            return visitor.visit(this);
        }
    }

    public static final class ListComp extends ExprTy {
        public final ExprTy element;
        public final ComprehensionTy[] generators;

        public ListComp(ExprTy element, ComprehensionTy[] generators, int startOffset, int endOffset) {
            super(startOffset, endOffset);
            this.element = element;
            this.generators = generators;
        }

        @Override
        public <T> T accept(SSTreeVisitor<T> visitor) {
            return visitor.visit(this);
        }
    }

    public static final class SetComp extends ExprTy {
        public final ExprTy element;
        public final ComprehensionTy[] generators;

        public SetComp(ExprTy element, ComprehensionTy[] generators, int startOffset, int endOffset) {
            super(startOffset, endOffset);
            this.element = element;
            this.generators = generators;
        }

        @Override
        public <T> T accept(SSTreeVisitor<T> visitor) {
            return visitor.visit(this);
        }
    }

    public static final class DictComp extends ExprTy {
        public final ExprTy key;
        public final ExprTy value;
        public final ComprehensionTy[] generators;

        public DictComp(ExprTy key, ExprTy value, ComprehensionTy[] generators, int startOffset, int endOffset) {
            super(startOffset, endOffset);
            this.key = key;
            this.value = value;
            this.generators = generators;
        }

        @Override
        public <T> T accept(SSTreeVisitor<T> visitor) {
            return visitor.visit(this);
        }
    }

    public static final class GeneratorExp extends ExprTy {
        public final ExprTy element;
        public final ComprehensionTy[] generators;

        public GeneratorExp(ExprTy element, ComprehensionTy[] generators, int startOffset, int endOffset) {
            super(startOffset, endOffset);
            this.element = element;
            this.generators = generators;
        }

        @Override
        public <T> T accept(SSTreeVisitor<T> visitor) {
            return visitor.visit(this);
        }
    }

    public static final class Await extends ExprTy {
        public final ExprTy value;

        public Await(ExprTy value, int startOffset, int endOffset) {
            super(startOffset, endOffset);
            this.value = value;
        }

        @Override
        public <T> T accept(SSTreeVisitor<T> visitor) {
            return visitor.visit(this);
        }
    }

    public static final class Yield extends ExprTy {
        public final ExprTy value;

        public Yield(ExprTy value, int startOffset, int endOffset) {
            super(startOffset, endOffset);
            this.value = value;
        }

        @Override
        public <T> T accept(SSTreeVisitor<T> visitor) {
            return visitor.visit(this);
        }
    }

    public static final class YieldFrom extends ExprTy {
        public final ExprTy value;

        public YieldFrom(ExprTy value, int startOffset, int endOffset) {
            super(startOffset, endOffset);
            this.value = value;
        }

        @Override
        public <T> T accept(SSTreeVisitor<T> visitor) {
            return visitor.visit(this);
        }
    }

    public static final class Compare extends ExprTy {
        public static enum Operator {
            EQ, NOTEQ, LT, LTE, GT, GTE, IS, ISNOT,
            IN, NOTIN;
        }

        public final ExprTy left;
        public final Operator[] ops;
        public final ExprTy[] comparators;

        public Compare(ExprTy left, Operator[] ops, ExprTy[] comparators, int startOffset, int endOffset) {
            super(startOffset, endOffset);
            this.left = left;
            this.ops = ops;
            this.comparators = comparators;
        }

        @Override
        public <T> T accept(SSTreeVisitor<T> visitor) {
            return visitor.visit(this);
        }
    }

    public static final class Call extends ExprTy {
        public final ExprTy func;
        public final ExprTy[] args;
        public final KeywordTy[] keywords;

        public Call(ExprTy func, ExprTy[] args, KeywordTy[] keywords, int startOffset, int endOffset) {
            super(startOffset, endOffset);
            this.func = func;
            this.args = args;
            this.keywords = keywords;
        }

        @Override
        public <T> T accept(SSTreeVisitor<T> visitor) {
            return visitor.visit(this);
        }
    }

    public static final class FormattedValue extends ExprTy {
        public enum ConversionType {
            STR,
            REPR,
            ASCII,
            NONE;
        }

        public final ExprTy value;
        public final ConversionType conversion;
        public final ExprTy formatSpec;

        public FormattedValue(ExprTy value, ConversionType conversion, ExprTy formatSpec, int startOffset, int endOffset) {
            super(startOffset, endOffset);
            this.value = value;
            this.conversion = conversion;
            this.formatSpec = formatSpec;
        }

        @Override
        public <T> T accept(SSTreeVisitor<T> visitor) {
            return visitor.visit(this);
        }
    }

    public static final class JoinedStr extends ExprTy {
        public final ExprTy[] values;

        public JoinedStr(ExprTy[] values, int startOffset, int endOffset) {
            super(startOffset, endOffset);
            this.values = values;
        }

        @Override
        public <T> T accept(SSTreeVisitor<T> visitor) {
            return visitor.visit(this);
        }
    }

    public static final class Constant extends ExprTy {
        public static enum Kind {
            OBJECT,
            NONE,
            ELLIPSIS,
            BOOLEAN,
            LONG,
            DOUBLE,
            COMPLEX,
            BIGINTEGER,
            RAW,
            BYTES;
        }

        public final Object value;
        public final long longValue;
        public final Kind kind;

        public Constant(Object value, Kind kind, int startOffset, int endOffset) {
            super(startOffset, endOffset);
            this.value = value;
            this.longValue = 0;
            this.kind = kind == null ? Kind.OBJECT : kind;
        }

        public Constant(double value, Kind kind, int startOffset, int endOffset) {
            super(startOffset, endOffset);
            this.value = null;
            this.longValue = Double.doubleToLongBits(value);
            this.kind = kind;
        }

        public Constant(long longValue, int startOffset, int endOffset) {
            super(startOffset, endOffset);
            this.value = null;
            this.longValue = longValue;
            this.kind = Kind.LONG;
        }

        public Constant(double real, double imag, int startOffset, int endOffset) {
            super(startOffset, endOffset);
            this.value = real;
            this.longValue = Double.doubleToLongBits(imag);
            this.kind = Kind.COMPLEX;
        }

        @Override
        public <T> T accept(SSTreeVisitor<T> visitor) {
            return visitor.visit(this);
        }
    }

    public static final class Attribute extends ExprTy {
        public final ExprTy value;
        public final String attr;
        public final ExprContext context;

        public Attribute(ExprTy value, String attr, ExprContext context, int startOffset, int endOffset) {
            super(startOffset, endOffset);
            this.value = value;
            this.attr = attr;
            this.context = context;
        }

        @Override
        public <T> T accept(SSTreeVisitor<T> visitor) {
            return visitor.visit(this);
        }
    }

    public static final class Subscript extends ExprTy {
        public final ExprTy value;
        public final ExprTy slice;
        public final ExprContext context;

        public Subscript(ExprTy value, ExprTy slice, ExprContext context, int startOffset, int endOffset) {
            super(startOffset, endOffset);
            this.value = value;
            this.slice = slice;
            this.context = context;
        }

        @Override
        public <T> T accept(SSTreeVisitor<T> visitor) {
            return visitor.visit(this);
        }
    }

    public static final class Starred extends ExprTy {
        public final ExprTy value;
        public final ExprContext context;

        public Starred(ExprTy value, ExprContext context, int startOffset, int endOffset) {
            super(startOffset, endOffset);
            this.value = value;
            this.context = context;
        }

        @Override
        public <T> T accept(SSTreeVisitor<T> visitor) {
            return visitor.visit(this);
        }
    }

    public static final class Name extends ExprTy {
        public final String id;
        public final ExprContext context;

        public Name(String id, ExprContext context, int startOffset, int endOffset) {
            super(startOffset, endOffset);
            this.id = id;
            this.context = context;
        }

        @Override
        public <T> T accept(SSTreeVisitor<T> visitor) {
            return visitor.visit(this);
        }
    }

    public static final class List extends ExprTy {
        public final ExprTy[] elements;
        public final ExprContext context;

        public List(ExprTy[] elements, ExprContext context, int startOffset, int endOffset) {
            super(startOffset, endOffset);
            this.elements = elements;
            this.context = context;
        }

        @Override
        public <T> T accept(SSTreeVisitor<T> visitor) {
            return visitor.visit(this);
        }
    }

    public static final class Tuple extends ExprTy {
        public final ExprTy[] elements;
        public final ExprContext context;

        public Tuple(ExprTy[] elements, ExprContext context, int startOffset, int endOffset) {
            super(startOffset, endOffset);
            this.elements = elements;
            this.context = context;
        }

        @Override
        public <T> T accept(SSTreeVisitor<T> visitor) {
            return visitor.visit(this);
        }
    }

    public static final class Slice extends ExprTy {
        public final ExprTy lower;
        public final ExprTy upper;
        public final ExprTy step;

        public Slice(ExprTy lower, ExprTy upper, ExprTy step, int startOffset, int endOffset) {
            super(startOffset, endOffset);
            this.lower = lower;
            this.upper = upper;
            this.step = step;
        }

        @Override
        public <T> T accept(SSTreeVisitor<T> visitor) {
            return visitor.visit(this);
        }
    }
}
