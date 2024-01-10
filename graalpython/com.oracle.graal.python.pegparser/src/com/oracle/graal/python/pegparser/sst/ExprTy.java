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
package com.oracle.graal.python.pegparser.sst;

import com.oracle.graal.python.pegparser.tokenizer.SourceRange;

public abstract class ExprTy extends SSTNode {

    ExprTy(SourceRange sourceRange) {
        super(sourceRange);
    }

    public static final class BoolOp extends ExprTy {
        public final BoolOpTy op;
        public final ExprTy[] values;   // nullable

        public BoolOp(BoolOpTy op, ExprTy[] values, SourceRange sourceRange) {
            super(sourceRange);
            assert op != null;
            this.op = op;
            this.values = values;
        }

        @Override
        public <T> T accept(SSTreeVisitor<T> visitor) {
            return visitor.visit(this);
        }
    }

    public static final class NamedExpr extends ExprTy {
        public final ExprTy target;
        public final ExprTy value;

        public NamedExpr(ExprTy target, ExprTy value, SourceRange sourceRange) {
            super(sourceRange);
            assert target != null;
            this.target = target;
            assert value != null;
            this.value = value;
        }

        @Override
        public <T> T accept(SSTreeVisitor<T> visitor) {
            return visitor.visit(this);
        }
    }

    public static final class BinOp extends ExprTy {
        public final ExprTy left;
        public final OperatorTy op;
        public final ExprTy right;

        public BinOp(ExprTy left, OperatorTy op, ExprTy right, SourceRange sourceRange) {
            super(sourceRange);
            assert left != null;
            this.left = left;
            assert op != null;
            this.op = op;
            assert right != null;
            this.right = right;
        }

        @Override
        public <T> T accept(SSTreeVisitor<T> visitor) {
            return visitor.visit(this);
        }
    }

    public static final class UnaryOp extends ExprTy {
        public final UnaryOpTy op;
        public final ExprTy operand;

        public UnaryOp(UnaryOpTy op, ExprTy operand, SourceRange sourceRange) {
            super(sourceRange);
            assert op != null;
            this.op = op;
            assert operand != null;
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

        public Lambda(ArgumentsTy args, ExprTy body, SourceRange sourceRange) {
            super(sourceRange);
            assert args != null;
            this.args = args;
            assert body != null;
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

        public IfExp(ExprTy test, ExprTy body, ExprTy orElse, SourceRange sourceRange) {
            super(sourceRange);
            assert test != null;
            this.test = test;
            assert body != null;
            this.body = body;
            assert orElse != null;
            this.orElse = orElse;
        }

        @Override
        public <T> T accept(SSTreeVisitor<T> visitor) {
            return visitor.visit(this);
        }
    }

    public static final class Dict extends ExprTy {
        public final ExprTy[] keys;   // nullable
        public final ExprTy[] values;   // nullable

        public Dict(ExprTy[] keys, ExprTy[] values, SourceRange sourceRange) {
            super(sourceRange);
            this.keys = keys;
            this.values = values;
        }

        @Override
        public <T> T accept(SSTreeVisitor<T> visitor) {
            return visitor.visit(this);
        }
    }

    public static final class Set extends ExprTy {
        public final ExprTy[] elements;   // nullable

        public Set(ExprTy[] elements, SourceRange sourceRange) {
            super(sourceRange);
            this.elements = elements;
        }

        @Override
        public <T> T accept(SSTreeVisitor<T> visitor) {
            return visitor.visit(this);
        }
    }

    public static final class ListComp extends ExprTy {
        public final ExprTy element;
        public final ComprehensionTy[] generators;   // nullable

        public ListComp(ExprTy element, ComprehensionTy[] generators, SourceRange sourceRange) {
            super(sourceRange);
            assert element != null;
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
        public final ComprehensionTy[] generators;   // nullable

        public SetComp(ExprTy element, ComprehensionTy[] generators, SourceRange sourceRange) {
            super(sourceRange);
            assert element != null;
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
        public final ComprehensionTy[] generators;   // nullable

        public DictComp(ExprTy key, ExprTy value, ComprehensionTy[] generators, SourceRange sourceRange) {
            super(sourceRange);
            assert key != null;
            this.key = key;
            assert value != null;
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
        public final ComprehensionTy[] generators;   // nullable

        public GeneratorExp(ExprTy element, ComprehensionTy[] generators, SourceRange sourceRange) {
            super(sourceRange);
            assert element != null;
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

        public Await(ExprTy value, SourceRange sourceRange) {
            super(sourceRange);
            assert value != null;
            this.value = value;
        }

        @Override
        public <T> T accept(SSTreeVisitor<T> visitor) {
            return visitor.visit(this);
        }
    }

    public static final class Yield extends ExprTy {
        public final ExprTy value;   // nullable

        public Yield(ExprTy value, SourceRange sourceRange) {
            super(sourceRange);
            this.value = value;
        }

        @Override
        public <T> T accept(SSTreeVisitor<T> visitor) {
            return visitor.visit(this);
        }
    }

    public static final class YieldFrom extends ExprTy {
        public final ExprTy value;

        public YieldFrom(ExprTy value, SourceRange sourceRange) {
            super(sourceRange);
            assert value != null;
            this.value = value;
        }

        @Override
        public <T> T accept(SSTreeVisitor<T> visitor) {
            return visitor.visit(this);
        }
    }

    public static final class Compare extends ExprTy {
        public final ExprTy left;
        public final CmpOpTy[] ops;   // nullable
        public final ExprTy[] comparators;   // nullable

        public Compare(ExprTy left, CmpOpTy[] ops, ExprTy[] comparators, SourceRange sourceRange) {
            super(sourceRange);
            assert left != null;
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
        public final ExprTy[] args;   // nullable
        public final KeywordTy[] keywords;   // nullable

        public Call(ExprTy func, ExprTy[] args, KeywordTy[] keywords, SourceRange sourceRange) {
            super(sourceRange);
            assert func != null;
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
        public final ExprTy value;
        public final int conversion;
        public final ExprTy formatSpec;   // nullable

        public FormattedValue(ExprTy value, int conversion, ExprTy formatSpec, SourceRange sourceRange) {
            super(sourceRange);
            assert value != null;
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
        public final ExprTy[] values;   // nullable

        public JoinedStr(ExprTy[] values, SourceRange sourceRange) {
            super(sourceRange);
            this.values = values;
        }

        @Override
        public <T> T accept(SSTreeVisitor<T> visitor) {
            return visitor.visit(this);
        }
    }

    public static final class Constant extends ExprTy {
        public final ConstantValue value;
        public final Object kind;   // nullable

        public Constant(ConstantValue value, Object kind, SourceRange sourceRange) {
            super(sourceRange);
            assert value != null;
            this.value = value;
            this.kind = kind;
        }

        @Override
        public <T> T accept(SSTreeVisitor<T> visitor) {
            return visitor.visit(this);
        }
    }

    public static final class Attribute extends ExprTy {
        public final ExprTy value;
        public final String attr;
        public final ExprContextTy context;

        public Attribute(ExprTy value, String attr, ExprContextTy context, SourceRange sourceRange) {
            super(sourceRange);
            assert value != null;
            this.value = value;
            assert attr != null;
            this.attr = attr;
            assert context != null;
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
        public final ExprContextTy context;

        public Subscript(ExprTy value, ExprTy slice, ExprContextTy context, SourceRange sourceRange) {
            super(sourceRange);
            assert value != null;
            this.value = value;
            assert slice != null;
            this.slice = slice;
            assert context != null;
            this.context = context;
        }

        @Override
        public <T> T accept(SSTreeVisitor<T> visitor) {
            return visitor.visit(this);
        }
    }

    public static final class Starred extends ExprTy {
        public final ExprTy value;
        public final ExprContextTy context;

        public Starred(ExprTy value, ExprContextTy context, SourceRange sourceRange) {
            super(sourceRange);
            assert value != null;
            this.value = value;
            assert context != null;
            this.context = context;
        }

        @Override
        public <T> T accept(SSTreeVisitor<T> visitor) {
            return visitor.visit(this);
        }
    }

    public static final class Name extends ExprTy {
        public final String id;
        public final ExprContextTy context;

        public Name(String id, ExprContextTy context, SourceRange sourceRange) {
            super(sourceRange);
            assert id != null;
            this.id = id;
            assert context != null;
            this.context = context;
        }

        @Override
        public <T> T accept(SSTreeVisitor<T> visitor) {
            return visitor.visit(this);
        }
    }

    public static final class List extends ExprTy {
        public final ExprTy[] elements;   // nullable
        public final ExprContextTy context;

        public List(ExprTy[] elements, ExprContextTy context, SourceRange sourceRange) {
            super(sourceRange);
            this.elements = elements;
            assert context != null;
            this.context = context;
        }

        @Override
        public <T> T accept(SSTreeVisitor<T> visitor) {
            return visitor.visit(this);
        }
    }

    public static final class Tuple extends ExprTy {
        public final ExprTy[] elements;   // nullable
        public final ExprContextTy context;

        public Tuple(ExprTy[] elements, ExprContextTy context, SourceRange sourceRange) {
            super(sourceRange);
            this.elements = elements;
            assert context != null;
            this.context = context;
        }

        @Override
        public <T> T accept(SSTreeVisitor<T> visitor) {
            return visitor.visit(this);
        }
    }

    public static final class Slice extends ExprTy {
        public final ExprTy lower;   // nullable
        public final ExprTy upper;   // nullable
        public final ExprTy step;   // nullable

        public Slice(ExprTy lower, ExprTy upper, ExprTy step, SourceRange sourceRange) {
            super(sourceRange);
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
