/*
 * Copyright (c) 2017, 2018, Oracle and/or its affiliates.
 * Copyright (c) 2013, Regents of the University of California
 *
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification, are
 * permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this list of
 * conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice, this list of
 * conditions and the following disclaimer in the documentation and/or other materials provided
 * with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS
 * OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
 * MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE
 * COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE
 * GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED
 * AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED
 * OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.oracle.graal.python.parser;

import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.misc.Interval;

import com.oracle.graal.python.parser.antlr.Python3Parser.ClassdefContext;
import com.oracle.graal.python.parser.antlr.Python3Parser.FuncdefContext;
import com.oracle.graal.python.parser.antlr.Python3Parser.LambdefContext;
import com.oracle.graal.python.parser.antlr.Python3Parser.Lambdef_nocondContext;

public class TranslationUtil {
    public static String getScopeId(ParserRuleContext ctx, ScopeInfo.ScopeKind kind) {
        String scopeId = "unknown scope";

        if (kind == ScopeInfo.ScopeKind.Module) {
            scopeId = ctx.toString();
        } else if (kind == ScopeInfo.ScopeKind.Function) {
            if (ctx instanceof FuncdefContext) {
                scopeId = ((FuncdefContext) ctx).NAME().toString();
            } else if (ctx instanceof Lambdef_nocondContext || ctx instanceof LambdefContext) {
                scopeId = "lambda";
            }
        } else if (kind == ScopeInfo.ScopeKind.Class) {
            scopeId = ((ClassdefContext) ctx).NAME().toString();
        } else if (kind == ScopeInfo.ScopeKind.Generator) {
            scopeId = ctx.toString();
        } else if (kind == ScopeInfo.ScopeKind.ListComp) {
            scopeId = ctx.toString();
        }

        return scopeId;
    }

    public static String getText(ParserRuleContext ctx) {
        return getText(ctx, 0);
    }

    public static String getText(ParserRuleContext ctx, int level) {
        ParserRuleContext context = ctx;
        int lvl = level;
        while (lvl > 0) {
            lvl--;
            ParserRuleContext parent = ctx.getParent();
            if (parent == null) {
                break;
            }
            context = parent;
        }
        CharStream inputStream = ctx.getStart().getInputStream();
        return inputStream.getText(new Interval(context.getStart().getStartIndex(), context.getStop().getStopIndex()));
    }

    public static NotCovered notCovered() {
        throw new NotCovered();
    }

    public static NotCovered notCovered(String message) {
        throw new NotCovered(message);
    }

    private static class NotCovered extends RuntimeException {

        private static final long serialVersionUID = 2485134940559018951L;

        public NotCovered() {
            super("This case is not covered!");
        }

        public NotCovered(String message) {
            super(message);
        }
    }
}
