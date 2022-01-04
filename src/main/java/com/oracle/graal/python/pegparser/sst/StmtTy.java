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

public abstract class StmtTy extends SSTNode {
    StmtTy(int startOffset, int endOffset) {
        super(startOffset, endOffset);
    }

    public static class FunctionDef extends StmtTy {
        final String name;
        final ArgumentsTy args;
        final StmtTy[] body;
        final ExprTy[] decoratorList;
        final ExprTy returns;
        final String typeComment;

        public FunctionDef(String name, ArgumentsTy args, StmtTy[] body, ExprTy[] decoratorList, ExprTy returns, String typeComment, int startOffset, int endOffset) {
            super(startOffset, endOffset);
            this.name = name;
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

    public static final class AsyncFunctionDef extends FunctionDef {
        public AsyncFunctionDef(String name, ArgumentsTy args, StmtTy[] body, ExprTy[] decoratorList, ExprTy returns, String typeComment, int startOffset, int endOffset) {
            super(name, args, body, decoratorList, returns, typeComment, startOffset, endOffset);
        }

        @Override
        public <T> T accept(SSTreeVisitor<T> visitor) {
            return visitor.visit(this);
        }
    }

    public static final class ClassDef extends StmtTy {
        final String name;
        final ExprTy[] bases;
        final KeywordTy[] keywords;
        final StmtTy[] body;
        final ExprTy[] decoratorList;

        public ClassDef(String name, ExprTy[] bases, KeywordTy[] keywords, StmtTy[] body, ExprTy[] decoratorList, int startOffset, int endOffset) {
            super(startOffset, endOffset);
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
        final ExprTy value;

        public Return(ExprTy value, int startOffset, int endOffset) {
            super(startOffset, endOffset);
            this.value = value;
        }

        @Override
        public <T> T accept(SSTreeVisitor<T> visitor) {
            return visitor.visit(this);
        }
    }

    public static final class Delete extends StmtTy {
        final ExprTy[] targets;

        public Delete(ExprTy[] targets, int startOffset, int endOffset) {
            super(startOffset, endOffset);
            this.targets = targets;
        }

        @Override
        public <T> T accept(SSTreeVisitor<T> visitor) {
            return visitor.visit(this);
        }
    }

    public static final class Assign extends StmtTy {
        final ExprTy[] targets;
        final ExprTy value;
        final String typeComment;

        public Assign(ExprTy[] targets, ExprTy value, String typeComment, int startOffset, int endOffset) {
            super(startOffset, endOffset);
            this.targets = targets;
            this.value = value;
            this.typeComment = typeComment;
        }

        @Override
        public <T> T accept(SSTreeVisitor<T> visitor) {
            return visitor.visit(this);
        }
    }

    public static final class AugAssign extends StmtTy {
        final ExprTy target;
        final ExprTy.BinOp.Operator op;
        final ExprTy value;

        public AugAssign(ExprTy target, ExprTy.BinOp.Operator op, ExprTy value, int startOffset, int endOffset) {
            super(startOffset, endOffset);
            this.target = target;
            this.op = op;
            this.value = value;
        }

        @Override
        public <T> T accept(SSTreeVisitor<T> visitor) {
            return visitor.visit(this);
        }
    }

    public static final class AnnAssign extends StmtTy {
        final ExprTy target;
        final ExprTy annotation;
        final ExprTy value;
        final boolean isSimple;

        public AnnAssign(ExprTy target, ExprTy annotation, ExprTy value, boolean isSimple, int startOffset, int endOffset) {
            super(startOffset, endOffset);
            this.target = target;
            this.annotation = annotation;
            this.value = value;
            this.isSimple = isSimple;
        }

        @Override
        public <T> T accept(SSTreeVisitor<T> visitor) {
            return visitor.visit(this);
        }
    }

    public static class For extends StmtTy {
        final ExprTy target;
        final ExprTy iter;
        final StmtTy[] body;
        final StmtTy[] orElse;
        final String typeComment;

        public For(ExprTy target, ExprTy iter, StmtTy[] body, StmtTy[] orElse, String typeComment, int startOffset, int endOffset) {
            super(startOffset, endOffset);
            this.target = target;
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

    public static final class AsyncFor extends For {
        public AsyncFor(ExprTy target, ExprTy iter, StmtTy[] body, StmtTy[] orElse, String typeComment, int startOffset, int endOffset) {
            super(target, iter, body, orElse, typeComment, startOffset, endOffset);
        }

        @Override
        public <T> T accept(SSTreeVisitor<T> visitor) {
            return visitor.visit(this);
        }
    }

    public static final class While extends StmtTy {
        final ExprTy test;
        final StmtTy[] body;
        final StmtTy[] orElse;

        public While(ExprTy test, StmtTy[] body, StmtTy[] orElse, int startOffset, int endOffset) {
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

    public static final class If extends StmtTy {
        final ExprTy test;
        final StmtTy[] body;
        final StmtTy[] orElse;

        public If(ExprTy test, StmtTy[] body, StmtTy[] orElse, int startOffset, int endOffset) {
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

    public static class With extends StmtTy {
        public static final class Item extends SSTNode {
            final ExprTy contextExpr;
            final ExprTy optionalVars;

            public Item(ExprTy contextExpr, ExprTy optionalVars, int startOffset, int endOffset) {
                super(startOffset, endOffset);
                this.contextExpr = contextExpr;
                this.optionalVars = optionalVars;
            }

            @Override
            public <T> T accept(SSTreeVisitor<T> visitor) {
                return visitor.visit(this);
            }
        }

        final Item[] items;
        final StmtTy[] body;
        final String typeComment;

        public With(Item[] items, StmtTy[] body, String typeComment, int startOffset, int endOffset) {
            super(startOffset, endOffset);
            this.items = items;
            this.body = body;
            this.typeComment = typeComment;
        }

        @Override
        public <T> T accept(SSTreeVisitor<T> visitor) {
            return visitor.visit(this);
        }
    }

    public static final class AsyncWith extends With {
        public AsyncWith(Item[] items, StmtTy[] body, String typeComment, int startOffset, int endOffset) {
            super(items, body, typeComment, startOffset, endOffset);
        }

        @Override
        public <T> T accept(SSTreeVisitor<T> visitor) {
            return visitor.visit(this);
        }
    }

    public static final class Match extends StmtTy {
        public static abstract class Pattern extends SSTNode {
            Pattern(int startOffset, int endOffset) {
                super(startOffset, endOffset);
            }

            public static final class MatchValue extends Pattern {
                final ExprTy value;

                public MatchValue(ExprTy value, int startOffset, int endOffset) {
                    super(startOffset, endOffset);
                    this.value = value;
                }

                @Override
                public <T> T accept(SSTreeVisitor<T> visitor) {
                    return visitor.visit(this);
                }
            }

            public static final class MatchSingleton extends Pattern {
                final ExprTy.Constant value;

                public MatchSingleton(ExprTy.Constant value, int startOffset, int endOffset) {
                    super(startOffset, endOffset);
                    this.value = value;
                }

                @Override
                public <T> T accept(SSTreeVisitor<T> visitor) {
                    return visitor.visit(this);
                }
            }

            public static final class MatchSequence extends Pattern {
                final Pattern[] patterns;

                public MatchSequence(Pattern[] patterns, int startOffset, int endOffset) {
                    super(startOffset, endOffset);
                    this.patterns = patterns;
                }

                @Override
                public <T> T accept(SSTreeVisitor<T> visitor) {
                    return visitor.visit(this);
                }
            }

            public static final class MatchMapping extends Pattern {
                final ExprTy[] keys;
                final Pattern[] patterns;
                final String rest;

                public MatchMapping(ExprTy[] keys, Pattern[] patterns, String rest, int startOffset, int endOffset) {
                    super(startOffset, endOffset);
                    this.keys = keys;
                    this.patterns = patterns;
                    this.rest = rest;
                }

                @Override
                public <T> T accept(SSTreeVisitor<T> visitor) {
                    return visitor.visit(this);
                }
            }

            public static final class MatchClass extends Pattern {
                final ExprTy cls;
                final Pattern[] patterns;
                final String[] kwdAttrs;
                final Pattern[] kwdPatters;

                public MatchClass(ExprTy cls, Pattern[] patterns, String[] kwdAttrs, Pattern[] kwdPatters, int startOffset, int endOffset) {
                    super(startOffset, endOffset);
                    this.cls = cls;
                    this.patterns = patterns;
                    this.kwdAttrs = kwdAttrs;
                    this.kwdPatters = kwdPatters;
                }

                @Override
                public <T> T accept(SSTreeVisitor<T> visitor) {
                    return visitor.visit(this);
                }
            }

            public static final class MatchStar extends Pattern {
                final String name;

                public MatchStar(String name, int startOffset, int endOffset) {
                    super(startOffset, endOffset);
                    this.name = name;
                }

                @Override
                public <T> T accept(SSTreeVisitor<T> visitor) {
                    return visitor.visit(this);
                }
            }

            public static final class MatchAs extends Pattern {
                final Pattern pattern;
                final String name;

                public MatchAs(Pattern pattern, String name, int startOffset, int endOffset) {
                    super(startOffset, endOffset);
                    this.pattern = pattern;
                    this.name = name;
                }

                @Override
                public <T> T accept(SSTreeVisitor<T> visitor) {
                    return visitor.visit(this);
                }
            }

            public static final class MatchOr extends Pattern {
                final Pattern[] patterns;

                public MatchOr(Pattern[] patterns, int startOffset, int endOffset) {
                    super(startOffset, endOffset);
                    this.patterns = patterns;
                }

                @Override
                public <T> T accept(SSTreeVisitor<T> visitor) {
                    return visitor.visit(this);
                }
            }
        }

        public static final class Case extends SSTNode {
            final Pattern pattern;
            final ExprTy guard;
            final StmtTy[] body;

            @Override
            public <T> T accept(SSTreeVisitor<T> visitor) {
                return visitor.visit(this);
            }
        }

        final ExprTy subject;
        final Case[] cases;

        public Match(ExprTy subject, Case[] cases, int startOffset, int endOffset) {
            super(startOffset, endOffset);
            this.subject = subject;
            this.cases = cases;
        }

        @Override
        public <T> T accept(SSTreeVisitor<T> visitor) {
            return visitor.visit(this);
        }
    }

    public static final class Raise extends StmtTy {
        final ExprTy exc;
        final ExprTy cause;

        public Raise(ExprTy exc, ExprTy cause, int startOffset, int endOffset) {
            super(startOffset, endOffset);
            this.exc = exc;
            this.cause = cause;
        }

        @Override
        public <T> T accept(SSTreeVisitor<T> visitor) {
            return visitor.visit(this);
        }
    }

    public static final class Try extends StmtTy {
        public static final class ExceptHandler extends SSTNode {
            final ExprTy type;
            final String name;
            final StmtTy[] body;

            public ExceptHandler(ExprTy type, String name, StmtTy[] body, int startOffset, int endOffset) {
                super(startOffset, endOffset);
                this.type = type;
                this.name = name;
                this.body = body;
            }

            @Override
            public <T> T accept(SSTreeVisitor<T> visitor) {
                return visitor.visit(this);
            }
        }

        final StmtTy[] body;
        final ExceptHandler[] handlers;
        final StmtTy[] orElse;
        final StmtTy[] finalBody;

        public Try(StmtTy[] body, ExceptHandler[] handlers, StmtTy[] orElse, StmtTy[] finalBody, int startOffset, int endOffset) {
            super(startOffset, endOffset);
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
        final ExprTy test;
        final ExprTy msg;

        public Assert(ExprTy test, ExprTy msg, int startOffset, int endOffset) {
            super(startOffset, endOffset);
            this.test = test;
            this.msg = msg;
        }

        @Override
        public <T> T accept(SSTreeVisitor<T> visitor) {
            return visitor.visit(this);
        }
    }

    public static final class Import extends StmtTy {
        final AliasTy[] names;

        public Import(AliasTy[] names, int startOffset, int endOffset) {
            super(startOffset, endOffset);
            this.names = names;
        }

        @Override
        public <T> T accept(SSTreeVisitor<T> visitor) {
            return visitor.visit(this);
        }
    }

    public static final class ImportFrom extends StmtTy {
        final String module;
        final AliasTy[] names;
        final int level;

        public ImportFrom(String module, AliasTy[] names, int level, int startOffset, int endOffset) {
            super(startOffset, endOffset);
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
        final String[] names;

        public Global(String[] names, int startOffset, int endOffset) {
            super(startOffset, endOffset);
            this.names = names;
        }

        @Override
        public <T> T accept(SSTreeVisitor<T> visitor) {
            return visitor.visit(this);
        }
    }

    public static final class NonLocal extends StmtTy {
        final String[] names;

        public NonLocal(String[] names, int startOffset, int endOffset) {
            super(startOffset, endOffset);
            this.names = names;
        }

        @Override
        public <T> T accept(SSTreeVisitor<T> visitor) {
            return visitor.visit(this);
        }
    }

    public static final class Expr extends StmtTy {
        final ExprTy value;

        public Expr(ExprTy value, int startOffset, int endOffset) {
            super(startOffset, endOffset);
            this.value = value;
        }

        @Override
        public <T> T accept(SSTreeVisitor<T> visitor) {
            return visitor.visit(this);
        }
    }
}
