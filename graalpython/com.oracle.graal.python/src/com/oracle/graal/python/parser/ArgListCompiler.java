/*
 * Copyright (c) 2017, 2019, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.parser;

import static com.oracle.graal.python.runtime.exception.PythonErrorType.SyntaxError;

import java.util.ArrayList;
import java.util.List;

import com.oracle.graal.python.parser.antlr.Python3BaseVisitor;
import com.oracle.graal.python.parser.antlr.Python3Parser.DefparameterContext;
import com.oracle.graal.python.parser.antlr.Python3Parser.KwargsparameterContext;
import com.oracle.graal.python.parser.antlr.Python3Parser.SplatparameterContext;
import com.oracle.graal.python.parser.antlr.Python3Parser.VdefparameterContext;
import com.oracle.graal.python.parser.antlr.Python3Parser.VkwargsparameterContext;
import com.oracle.graal.python.parser.antlr.Python3Parser.VsplatparameterContext;
import com.oracle.graal.python.runtime.PythonParser.ParserErrorCallback;

import org.antlr.v4.runtime.ParserRuleContext;

public class ArgListCompiler<T> extends Python3BaseVisitor<T> {
    private boolean arglist;
    public final List<String> names;
    public final List<String> fpnames;
    public final List<ParserRuleContext> init_code;
    private final ParserErrorCallback errors;

    public ArgListCompiler(ParserErrorCallback errors) {
        this.errors = errors;
        arglist = false;
        // defaults = null;
        names = new ArrayList<>();
        fpnames = new ArrayList<>();
        init_code = new ArrayList<>();
    }

    public void reset() {
        arglist = false;
        names.clear();
        init_code.clear();
    }

    private void addName(String name) {
        if (names.contains(name)) {
            throw errors.raise(SyntaxError, "duplicate argument name found");
        }
        names.add(name);
    }

    @Override
    public T visitVdefparameter(VdefparameterContext ctx) {
        String name = ctx.vfpdef().NAME().getText();
        addName(name);
        if (ctx.test() != null) {
            init_code.add(ctx);
        } else if (!init_code.isEmpty() && !arglist) {
            throw errors.raise(SyntaxError, "non-default argument follows default argument. Line %d column %d", ctx.start.getLine(), ctx.start.getCharPositionInLine());
        }
        return null;
    }

    @Override
    public T visitVsplatparameter(VsplatparameterContext ctx) {
        arglist = true;
        if (ctx.vfpdef() != null) {
            addName(ctx.vfpdef().NAME().getText());
        } else {
            names.add("*");
        }
        return null;
    }

    @Override
    public T visitVkwargsparameter(VkwargsparameterContext ctx) {
        if (ctx.vfpdef() != null) {
            addName(ctx.vfpdef().NAME().getText());
        } else {
            names.add("**");
        }
        return null;
    }

    @Override
    public T visitDefparameter(DefparameterContext ctx) {
        String name = ctx.tfpdef().NAME().getText();
        addName(name);
        if (ctx.tfpdef().test() != null) {
            // TODO: deal with annotations
        }
        if (ctx.test() != null) {
            init_code.add(ctx);
        } else if (!init_code.isEmpty() && !arglist) {
            throw errors.raise(SyntaxError, "non-default argument follows default argument. Line %d column %d", ctx.start.getLine(), ctx.start.getCharPositionInLine());
        }
        return null;
    }

    @Override
    public T visitSplatparameter(SplatparameterContext ctx) {
        arglist = true;
        if (ctx.tfpdef() != null) {
            addName(ctx.tfpdef().NAME().getText());
        } else {
            names.add("*");
        }
        return null;
    }

    @Override
    public T visitKwargsparameter(KwargsparameterContext ctx) {
        if (ctx.tfpdef() != null) {
            addName(ctx.tfpdef().NAME().getText());
        } else {
            names.add("**");
        }
        return null;
    }
}
