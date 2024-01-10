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

public abstract class PatternTy extends SSTNode {

    PatternTy(SourceRange sourceRange) {
        super(sourceRange);
    }

    public static final class MatchValue extends PatternTy {
        public final ExprTy value;

        public MatchValue(ExprTy value, SourceRange sourceRange) {
            super(sourceRange);
            assert value != null;
            this.value = value;
        }

        @Override
        public <T> T accept(SSTreeVisitor<T> visitor) {
            return visitor.visit(this);
        }
    }

    public static final class MatchSingleton extends PatternTy {
        public final ConstantValue value;

        public MatchSingleton(ConstantValue value, SourceRange sourceRange) {
            super(sourceRange);
            assert value != null;
            this.value = value;
        }

        @Override
        public <T> T accept(SSTreeVisitor<T> visitor) {
            return visitor.visit(this);
        }
    }

    public static final class MatchSequence extends PatternTy {
        public final PatternTy[] patterns;   // nullable

        public MatchSequence(PatternTy[] patterns, SourceRange sourceRange) {
            super(sourceRange);
            this.patterns = patterns;
        }

        @Override
        public <T> T accept(SSTreeVisitor<T> visitor) {
            return visitor.visit(this);
        }
    }

    public static final class MatchMapping extends PatternTy {
        public final ExprTy[] keys;   // nullable
        public final PatternTy[] patterns;   // nullable
        public final String rest;   // nullable

        public MatchMapping(ExprTy[] keys, PatternTy[] patterns, String rest, SourceRange sourceRange) {
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

    public static final class MatchClass extends PatternTy {
        public final ExprTy cls;
        public final PatternTy[] patterns;   // nullable
        public final String[] kwdAttrs;   // nullable
        public final PatternTy[] kwdPatterns;   // nullable

        public MatchClass(ExprTy cls, PatternTy[] patterns, String[] kwdAttrs, PatternTy[] kwdPatterns, SourceRange sourceRange) {
            super(sourceRange);
            assert cls != null;
            this.cls = cls;
            this.patterns = patterns;
            this.kwdAttrs = kwdAttrs;
            this.kwdPatterns = kwdPatterns;
        }

        @Override
        public <T> T accept(SSTreeVisitor<T> visitor) {
            return visitor.visit(this);
        }
    }

    public static final class MatchStar extends PatternTy {
        public final String name;   // nullable

        public MatchStar(String name, SourceRange sourceRange) {
            super(sourceRange);
            this.name = name;
        }

        @Override
        public <T> T accept(SSTreeVisitor<T> visitor) {
            return visitor.visit(this);
        }
    }

    public static final class MatchAs extends PatternTy {
        public final PatternTy pattern;   // nullable
        public final String name;   // nullable

        public MatchAs(PatternTy pattern, String name, SourceRange sourceRange) {
            super(sourceRange);
            this.pattern = pattern;
            this.name = name;
        }

        @Override
        public <T> T accept(SSTreeVisitor<T> visitor) {
            return visitor.visit(this);
        }
    }

    public static final class MatchOr extends PatternTy {
        public final PatternTy[] patterns;   // nullable

        public MatchOr(PatternTy[] patterns, SourceRange sourceRange) {
            super(sourceRange);
            this.patterns = patterns;
        }

        @Override
        public <T> T accept(SSTreeVisitor<T> visitor) {
            return visitor.visit(this);
        }
    }
}
