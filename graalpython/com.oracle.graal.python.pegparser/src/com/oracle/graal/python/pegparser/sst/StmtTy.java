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

package com.oracle.graal.python.pegparser.sst;

import com.oracle.graal.python.pegparser.tokenizer.SourceRange;

public abstract class StmtTy extends SSTNode {
    StmtTy(SourceRange sourceRange) {
        super(sourceRange);
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

    public static class FunctionDef extends StmtTy {
        public final String name;
        public final ArgumentsTy args;
        public final StmtTy[] body;
        public final ExprTy[] decoratorList;
        public final ExprTy returns;
        public final String typeComment;

        public FunctionDef(String name, ArgumentsTy args, StmtTy[] body, ExprTy[] decoratorList, ExprTy returns, String typeComment, SourceRange sourceRange) {
            super(sourceRange);
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

        public FunctionDef copyWithDecorators(ExprTy[] newDecoratorList) {
            return new FunctionDef(name, args, body, newDecoratorList, returns, typeComment, sourceRange);
        }
    }

    public static final class AsyncFunctionDef extends FunctionDef {
        public AsyncFunctionDef(String name, ArgumentsTy args, StmtTy[] body, ExprTy[] decoratorList, ExprTy returns, String typeComment, SourceRange sourceRange) {
            super(name, args, body, decoratorList, returns, typeComment, sourceRange);
        }

        @Override
        public <T> T accept(SSTreeVisitor<T> visitor) {
            return visitor.visit(this);
        }

        @Override
        public AsyncFunctionDef copyWithDecorators(ExprTy[] newDecoratorList) {
            return new AsyncFunctionDef(name, args, body, newDecoratorList, returns, typeComment, sourceRange);
        }
    }

    public static final class ClassDef extends StmtTy {
        public final String name;
        public final ExprTy[] bases;
        public final KeywordTy[] keywords;
        public final StmtTy[] body;
        public final ExprTy[] decoratorList;

        public ClassDef(String name, ExprTy[] bases, KeywordTy[] keywords, StmtTy[] body, ExprTy[] decoratorList, SourceRange sourceRange) {
            super(sourceRange);
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
        public final ExprTy value;

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
        public final ExprTy[] targets;

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
        public final ExprTy[] targets;
        public final ExprTy value;
        public final String typeComment;

        public Assign(ExprTy[] targets, ExprTy value, String typeComment, SourceRange sourceRange) {
            super(sourceRange);
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
        public final ExprTy target;
        public final OperatorTy op;
        public final ExprTy value;

        public AugAssign(ExprTy target, OperatorTy op, ExprTy value, SourceRange sourceRange) {
            super(sourceRange);
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
        public final ExprTy target;
        public final ExprTy annotation;
        public final ExprTy value;
        public final boolean isSimple;

        public AnnAssign(ExprTy target, ExprTy annotation, ExprTy value, boolean isSimple, SourceRange sourceRange) {
            super(sourceRange);
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
        public final ExprTy target;
        public final ExprTy iter;
        public final StmtTy[] body;
        public final StmtTy[] orElse;
        public final String typeComment;

        public For(ExprTy target, ExprTy iter, StmtTy[] body, StmtTy[] orElse, String typeComment, SourceRange sourceRange) {
            super(sourceRange);
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
        public AsyncFor(ExprTy target, ExprTy iter, StmtTy[] body, StmtTy[] orElse, String typeComment, SourceRange sourceRange) {
            super(target, iter, body, orElse, typeComment, sourceRange);
        }

        @Override
        public <T> T accept(SSTreeVisitor<T> visitor) {
            return visitor.visit(this);
        }
    }

    public static final class While extends StmtTy {
        public final ExprTy test;
        public final StmtTy[] body;
        public final StmtTy[] orElse;

        public While(ExprTy test, StmtTy[] body, StmtTy[] orElse, SourceRange sourceRange) {
            super(sourceRange);
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
        public final StmtTy[] body;
        public final StmtTy[] orElse;

        public If(ExprTy test, StmtTy[] body, StmtTy[] orElse, SourceRange sourceRange) {
            super(sourceRange);
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
            public final ExprTy contextExpr;
            public final ExprTy optionalVars;

            public Item(ExprTy contextExpr, ExprTy optionalVars, SourceRange sourceRange) {
                super(sourceRange);
                this.contextExpr = contextExpr;
                this.optionalVars = optionalVars;
            }

            @Override
            public <T> T accept(SSTreeVisitor<T> visitor) {
                return visitor.visit(this);
            }
        }

        public final Item[] items;
        public final StmtTy[] body;
        public final String typeComment;

        public With(Item[] items, StmtTy[] body, String typeComment, SourceRange sourceRange) {
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

    public static final class AsyncWith extends With {
        public AsyncWith(Item[] items, StmtTy[] body, String typeComment, SourceRange sourceRange) {
            super(items, body, typeComment, sourceRange);
        }

        @Override
        public <T> T accept(SSTreeVisitor<T> visitor) {
            return visitor.visit(this);
        }
    }

    public static final class Match extends StmtTy {
        public static abstract class Pattern extends SSTNode {
            Pattern(SourceRange sourceRange) {
                super(sourceRange);
            }

            public static final class MatchValue extends Pattern {
                public final ExprTy value;

                public MatchValue(ExprTy value, SourceRange sourceRange) {
                    super(sourceRange);
                    this.value = value;
                }

                @Override
                public <T> T accept(SSTreeVisitor<T> visitor) {
                    return visitor.visit(this);
                }
            }

            public static final class MatchSingleton extends Pattern {
                public final ExprTy.Constant value;

                public MatchSingleton(ExprTy.Constant value, SourceRange sourceRange) {
                    super(sourceRange);
                    this.value = value;
                }

                @Override
                public <T> T accept(SSTreeVisitor<T> visitor) {
                    return visitor.visit(this);
                }
            }

            public static final class MatchSequence extends Pattern {
                public final Pattern[] patterns;

                public MatchSequence(Pattern[] patterns, SourceRange sourceRange) {
                    super(sourceRange);
                    this.patterns = patterns;
                }

                @Override
                public <T> T accept(SSTreeVisitor<T> visitor) {
                    return visitor.visit(this);
                }
            }

            public static final class MatchMapping extends Pattern {
                public final ExprTy[] keys;
                public final Pattern[] patterns;
                public final String rest;

                public MatchMapping(ExprTy[] keys, Pattern[] patterns, String rest, SourceRange sourceRange) {
                    super(sourceRange);
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
                public final ExprTy cls;
                public final Pattern[] patterns;
                public final String[] kwdAttrs;
                public final Pattern[] kwdPatters;

                public MatchClass(ExprTy cls, Pattern[] patterns, String[] kwdAttrs, Pattern[] kwdPatters, SourceRange sourceRange) {
                    super(sourceRange);
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
                public final String name;

                public MatchStar(String name, SourceRange sourceRange) {
                    super(sourceRange);
                    this.name = name;
                }

                @Override
                public <T> T accept(SSTreeVisitor<T> visitor) {
                    return visitor.visit(this);
                }
            }

            public static final class MatchAs extends Pattern {
                public final Pattern pattern;
                public final String name;

                public MatchAs(Pattern pattern, String name, SourceRange sourceRange) {
                    super(sourceRange);
                    this.pattern = pattern;
                    this.name = name;
                }

                @Override
                public <T> T accept(SSTreeVisitor<T> visitor) {
                    return visitor.visit(this);
                }
            }

            public static final class MatchOr extends Pattern {
                public final Pattern[] patterns;

                public MatchOr(Pattern[] patterns, SourceRange sourceRange) {
                    super(sourceRange);
                    this.patterns = patterns;
                }

                @Override
                public <T> T accept(SSTreeVisitor<T> visitor) {
                    return visitor.visit(this);
                }
            }
        }

        public static final class Case extends SSTNode {
            public final Pattern pattern;
            public final ExprTy guard;
            public final StmtTy[] body;

            public Case(Pattern pattern, ExprTy guard, StmtTy[] body, SourceRange sourceRange) {
                super(sourceRange);
                this.pattern = pattern;
                this.guard = guard;
                this.body = body;
            }

            @Override
            public <T> T accept(SSTreeVisitor<T> visitor) {
                return visitor.visit(this);
            }
        }

        public final ExprTy subject;
        public final Case[] cases;

        public Match(ExprTy subject, Case[] cases, SourceRange sourceRange) {
            super(sourceRange);
            this.subject = subject;
            this.cases = cases;
        }

        @Override
        public <T> T accept(SSTreeVisitor<T> visitor) {
            return visitor.visit(this);
        }
    }

    public static final class Raise extends StmtTy {
        public final ExprTy exc;
        public final ExprTy cause;

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

        public final StmtTy[] body;
        public final ExceptHandlerTy[] handlers;
        public final StmtTy[] orElse;
        public final StmtTy[] finalBody;

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

    public static final class Assert extends StmtTy {
        public final ExprTy test;
        public final ExprTy msg;

        public Assert(ExprTy test, ExprTy msg, SourceRange sourceRange) {
            super(sourceRange);
            this.test = test;
            this.msg = msg;
        }

        @Override
        public <T> T accept(SSTreeVisitor<T> visitor) {
            return visitor.visit(this);
        }
    }

    public static final class Import extends StmtTy {
        public final AliasTy[] names;

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
        public final String module;
        public final AliasTy[] names;
        public final int level;

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
        public final String[] names;

        public Global(String[] names, SourceRange sourceRange) {
            super(sourceRange);
            this.names = names;
        }

        @Override
        public <T> T accept(SSTreeVisitor<T> visitor) {
            return visitor.visit(this);
        }
    }

    public static final class NonLocal extends StmtTy {
        public final String[] names;

        public NonLocal(String[] names, SourceRange sourceRange) {
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

        public Expr(ExprTy value) {
            super(value.sourceRange);
            this.value = value;
        }

        @Override
        public <T> T accept(SSTreeVisitor<T> visitor) {
            return visitor.visit(this);
        }
    }
}
