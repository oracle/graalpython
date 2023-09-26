/*
 * Copyright (c) 2019, 2023, Oracle and/or its affiliates. All rights reserved.
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

public abstract class StmtTy extends SSTNode {

    StmtTy(SourceRange sourceRange) {
        super(sourceRange);
    }

    public static final class FunctionDef extends StmtTy {
        public final String name;
        public final ArgumentsTy args;
        public final StmtTy[] body;   // nullable
        public final ExprTy[] decoratorList;   // nullable
        public final ExprTy returns;   // nullable
        public final Object typeComment;   // nullable

        public FunctionDef(String name, ArgumentsTy args, StmtTy[] body, ExprTy[] decoratorList, ExprTy returns, Object typeComment, SourceRange sourceRange) {
            super(sourceRange);
            assert name != null;
            this.name = name;
            assert args != null;
            this.args = args;
            this.body = body;
            this.decoratorList = decoratorList;
            this.returns = returns;
            this.typeComment = typeComment;
        }

        @Override
        public <T> T accept(SSTreeVisitor<T> visitor) {
            return visitor.visit(this);
        }
    }

    public static final class AsyncFunctionDef extends StmtTy {
        public final String name;
        public final ArgumentsTy args;
        public final StmtTy[] body;   // nullable
        public final ExprTy[] decoratorList;   // nullable
        public final ExprTy returns;   // nullable
        public final Object typeComment;   // nullable

        public AsyncFunctionDef(String name, ArgumentsTy args, StmtTy[] body, ExprTy[] decoratorList, ExprTy returns, Object typeComment, SourceRange sourceRange) {
            super(sourceRange);
            assert name != null;
            this.name = name;
            assert args != null;
            this.args = args;
            this.body = body;
            this.decoratorList = decoratorList;
            this.returns = returns;
            this.typeComment = typeComment;
        }

        @Override
        public <T> T accept(SSTreeVisitor<T> visitor) {
            return visitor.visit(this);
        }
    }

    public static final class ClassDef extends StmtTy {
        public final String name;
        public final ExprTy[] bases;   // nullable
        public final KeywordTy[] keywords;   // nullable
        public final StmtTy[] body;   // nullable
        public final ExprTy[] decoratorList;   // nullable

        public ClassDef(String name, ExprTy[] bases, KeywordTy[] keywords, StmtTy[] body, ExprTy[] decoratorList, SourceRange sourceRange) {
            super(sourceRange);
            assert name != null;
            this.name = name;
            this.bases = bases;
            this.keywords = keywords;
            this.body = body;
            this.decoratorList = decoratorList;
        }

        @Override
        public <T> T accept(SSTreeVisitor<T> visitor) {
            return visitor.visit(this);
        }
    }

    public static final class Return extends StmtTy {
        public final ExprTy value;   // nullable

        public Return(ExprTy value, SourceRange sourceRange) {
            super(sourceRange);
            this.value = value;
        }

        @Override
        public <T> T accept(SSTreeVisitor<T> visitor) {
            return visitor.visit(this);
        }
    }

    public static final class Delete extends StmtTy {
        public final ExprTy[] targets;   // nullable

        public Delete(ExprTy[] targets, SourceRange sourceRange) {
            super(sourceRange);
            this.targets = targets;
        }

        @Override
        public <T> T accept(SSTreeVisitor<T> visitor) {
            return visitor.visit(this);
        }
    }

    public static final class Assign extends StmtTy {
        public final ExprTy[] targets;   // nullable
        public final ExprTy value;
        public final Object typeComment;   // nullable

        public Assign(ExprTy[] targets, ExprTy value, Object typeComment, SourceRange sourceRange) {
            super(sourceRange);
            this.targets = targets;
            assert value != null;
            this.value = value;
            this.typeComment = typeComment;
        }

        @Override
        public <T> T accept(SSTreeVisitor<T> visitor) {
            return visitor.visit(this);
        }
    }

    public static final class AugAssign extends StmtTy {
        public final ExprTy target;
        public final OperatorTy op;
        public final ExprTy value;

        public AugAssign(ExprTy target, OperatorTy op, ExprTy value, SourceRange sourceRange) {
            super(sourceRange);
            assert target != null;
            this.target = target;
            assert op != null;
            this.op = op;
            assert value != null;
            this.value = value;
        }

        @Override
        public <T> T accept(SSTreeVisitor<T> visitor) {
            return visitor.visit(this);
        }
    }

    public static final class AnnAssign extends StmtTy {
        public final ExprTy target;
        public final ExprTy annotation;
        public final ExprTy value;   // nullable
        public final boolean isSimple;

        public AnnAssign(ExprTy target, ExprTy annotation, ExprTy value, boolean isSimple, SourceRange sourceRange) {
            super(sourceRange);
            assert target != null;
            this.target = target;
            assert annotation != null;
            this.annotation = annotation;
            this.value = value;
            this.isSimple = isSimple;
        }

        @Override
        public <T> T accept(SSTreeVisitor<T> visitor) {
            return visitor.visit(this);
        }
    }

    public static final class For extends StmtTy {
        public final ExprTy target;
        public final ExprTy iter;
        public final StmtTy[] body;   // nullable
        public final StmtTy[] orElse;   // nullable
        public final Object typeComment;   // nullable

        public For(ExprTy target, ExprTy iter, StmtTy[] body, StmtTy[] orElse, Object typeComment, SourceRange sourceRange) {
            super(sourceRange);
            assert target != null;
            this.target = target;
            assert iter != null;
            this.iter = iter;
            this.body = body;
            this.orElse = orElse;
            this.typeComment = typeComment;
        }

        @Override
        public <T> T accept(SSTreeVisitor<T> visitor) {
            return visitor.visit(this);
        }
    }

    public static final class AsyncFor extends StmtTy {
        public final ExprTy target;
        public final ExprTy iter;
        public final StmtTy[] body;   // nullable
        public final StmtTy[] orElse;   // nullable
        public final Object typeComment;   // nullable

        public AsyncFor(ExprTy target, ExprTy iter, StmtTy[] body, StmtTy[] orElse, Object typeComment, SourceRange sourceRange) {
            super(sourceRange);
            assert target != null;
            this.target = target;
            assert iter != null;
            this.iter = iter;
            this.body = body;
            this.orElse = orElse;
            this.typeComment = typeComment;
        }

        @Override
        public <T> T accept(SSTreeVisitor<T> visitor) {
            return visitor.visit(this);
        }
    }

    public static final class While extends StmtTy {
        public final ExprTy test;
        public final StmtTy[] body;   // nullable
        public final StmtTy[] orElse;   // nullable

        public While(ExprTy test, StmtTy[] body, StmtTy[] orElse, SourceRange sourceRange) {
            super(sourceRange);
            assert test != null;
            this.test = test;
            this.body = body;
            this.orElse = orElse;
        }

        @Override
        public <T> T accept(SSTreeVisitor<T> visitor) {
            return visitor.visit(this);
        }
    }

    public static final class If extends StmtTy {
        public final ExprTy test;
        public final StmtTy[] body;   // nullable
        public final StmtTy[] orElse;   // nullable

        public If(ExprTy test, StmtTy[] body, StmtTy[] orElse, SourceRange sourceRange) {
            super(sourceRange);
            assert test != null;
            this.test = test;
            this.body = body;
            this.orElse = orElse;
        }

        @Override
        public <T> T accept(SSTreeVisitor<T> visitor) {
            return visitor.visit(this);
        }
    }

    public static final class With extends StmtTy {
        public final WithItemTy[] items;   // nullable
        public final StmtTy[] body;   // nullable
        public final Object typeComment;   // nullable

        public With(WithItemTy[] items, StmtTy[] body, Object typeComment, SourceRange sourceRange) {
            super(sourceRange);
            this.items = items;
            this.body = body;
            this.typeComment = typeComment;
        }

        @Override
        public <T> T accept(SSTreeVisitor<T> visitor) {
            return visitor.visit(this);
        }
    }

    public static final class AsyncWith extends StmtTy {
        public final WithItemTy[] items;   // nullable
        public final StmtTy[] body;   // nullable
        public final Object typeComment;   // nullable

        public AsyncWith(WithItemTy[] items, StmtTy[] body, Object typeComment, SourceRange sourceRange) {
            super(sourceRange);
            this.items = items;
            this.body = body;
            this.typeComment = typeComment;
        }

        @Override
        public <T> T accept(SSTreeVisitor<T> visitor) {
            return visitor.visit(this);
        }
    }

    public static final class Match extends StmtTy {
        public final ExprTy subject;
        public final MatchCaseTy[] cases;   // nullable

        public Match(ExprTy subject, MatchCaseTy[] cases, SourceRange sourceRange) {
            super(sourceRange);
            assert subject != null;
            this.subject = subject;
            this.cases = cases;
        }

        @Override
        public <T> T accept(SSTreeVisitor<T> visitor) {
            return visitor.visit(this);
        }
    }

    public static final class Raise extends StmtTy {
        public final ExprTy exc;   // nullable
        public final ExprTy cause;   // nullable

        public Raise(ExprTy exc, ExprTy cause, SourceRange sourceRange) {
            super(sourceRange);
            this.exc = exc;
            this.cause = cause;
        }

        @Override
        public <T> T accept(SSTreeVisitor<T> visitor) {
            return visitor.visit(this);
        }
    }

    public static final class Try extends StmtTy {
        public final StmtTy[] body;   // nullable
        public final ExceptHandlerTy[] handlers;   // nullable
        public final StmtTy[] orElse;   // nullable
        public final StmtTy[] finalBody;   // nullable

        public Try(StmtTy[] body, ExceptHandlerTy[] handlers, StmtTy[] orElse, StmtTy[] finalBody, SourceRange sourceRange) {
            super(sourceRange);
            this.body = body;
            this.handlers = handlers;
            this.orElse = orElse;
            this.finalBody = finalBody;
        }

        @Override
        public <T> T accept(SSTreeVisitor<T> visitor) {
            return visitor.visit(this);
        }
    }

    public static final class TryStar extends StmtTy {
        public final StmtTy[] body;   // nullable
        public final ExceptHandlerTy[] handlers;   // nullable
        public final StmtTy[] orElse;   // nullable
        public final StmtTy[] finalBody;   // nullable

        public TryStar(StmtTy[] body, ExceptHandlerTy[] handlers, StmtTy[] orElse, StmtTy[] finalBody, SourceRange sourceRange) {
            super(sourceRange);
            this.body = body;
            this.handlers = handlers;
            this.orElse = orElse;
            this.finalBody = finalBody;
        }

        @Override
        public <T> T accept(SSTreeVisitor<T> visitor) {
            return visitor.visit(this);
        }
    }

    public static final class Assert extends StmtTy {
        public final ExprTy test;
        public final ExprTy msg;   // nullable

        public Assert(ExprTy test, ExprTy msg, SourceRange sourceRange) {
            super(sourceRange);
            assert test != null;
            this.test = test;
            this.msg = msg;
        }

        @Override
        public <T> T accept(SSTreeVisitor<T> visitor) {
            return visitor.visit(this);
        }
    }

    public static final class Import extends StmtTy {
        public final AliasTy[] names;   // nullable

        public Import(AliasTy[] names, SourceRange sourceRange) {
            super(sourceRange);
            this.names = names;
        }

        @Override
        public <T> T accept(SSTreeVisitor<T> visitor) {
            return visitor.visit(this);
        }
    }

    public static final class ImportFrom extends StmtTy {
        public final String module;   // nullable
        public final AliasTy[] names;   // nullable
        public final int level;   // nullable

        public ImportFrom(String module, AliasTy[] names, int level, SourceRange sourceRange) {
            super(sourceRange);
            this.module = module;
            this.names = names;
            this.level = level;
        }

        @Override
        public <T> T accept(SSTreeVisitor<T> visitor) {
            return visitor.visit(this);
        }
    }

    public static final class Global extends StmtTy {
        public final String[] names;   // nullable

        public Global(String[] names, SourceRange sourceRange) {
            super(sourceRange);
            this.names = names;
        }

        @Override
        public <T> T accept(SSTreeVisitor<T> visitor) {
            return visitor.visit(this);
        }
    }

    public static final class Nonlocal extends StmtTy {
        public final String[] names;   // nullable

        public Nonlocal(String[] names, SourceRange sourceRange) {
            super(sourceRange);
            this.names = names;
        }

        @Override
        public <T> T accept(SSTreeVisitor<T> visitor) {
            return visitor.visit(this);
        }
    }

    public static final class Expr extends StmtTy {
        public final ExprTy value;

        public Expr(ExprTy value, SourceRange sourceRange) {
            super(sourceRange);
            assert value != null;
            this.value = value;
        }

        @Override
        public <T> T accept(SSTreeVisitor<T> visitor) {
            return visitor.visit(this);
        }
    }

    public static final class Pass extends StmtTy {

        public Pass(SourceRange sourceRange) {
            super(sourceRange);
        }

        @Override
        public <T> T accept(SSTreeVisitor<T> visitor) {
            return visitor.visit(this);
        }
    }

    public static final class Break extends StmtTy {

        public Break(SourceRange sourceRange) {
            super(sourceRange);
        }

        @Override
        public <T> T accept(SSTreeVisitor<T> visitor) {
            return visitor.visit(this);
        }
    }

    public static final class Continue extends StmtTy {

        public Continue(SourceRange sourceRange) {
            super(sourceRange);
        }

        @Override
        public <T> T accept(SSTreeVisitor<T> visitor) {
            return visitor.visit(this);
        }
    }
}
