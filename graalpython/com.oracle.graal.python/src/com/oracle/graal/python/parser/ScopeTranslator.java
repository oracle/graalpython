/*
 * Copyright (c) 2017, Oracle and/or its affiliates.
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

import java.util.ArrayList;
import java.util.List;
import java.util.Stack;
import java.util.function.Consumer;
import java.util.function.Function;

import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.tree.TerminalNode;

import com.oracle.graal.python.parser.ScopeInfo.ScopeKind;
import com.oracle.graal.python.parser.antlr.Python3BaseVisitor;
import com.oracle.graal.python.parser.antlr.Python3Parser;
import com.oracle.graal.python.parser.antlr.Python3Parser.Single_inputContext;
import com.oracle.graal.python.runtime.PythonCore;

public class ScopeTranslator<T> extends Python3BaseVisitor<T> {

    private final TranslationEnvironment environment;
    private Stack<ArgListCompiler<T>> argListCompilers;
    private final PythonCore core;
    private final boolean interactive;
    private final boolean trackCells;

    public ScopeTranslator(PythonCore core, TranslationEnvironment environment, boolean interactive, boolean trackCells) {
        this.core = core;
        this.environment = environment.reset();
        this.argListCompilers = new Stack<>();
        this.interactive = interactive;
        this.trackCells = trackCells;
    }

    public interface ScopeTranslatorFactory {
        ScopeTranslator<Object> create(TranslationEnvironment environment, boolean trackCells);
    }

    public static void accept(ParserRuleContext input, TranslationEnvironment environment, ScopeTranslatorFactory factory) {
        accept(input, environment, factory, null);
    }

    public static void accept(ParserRuleContext input, TranslationEnvironment environment, ScopeTranslatorFactory factory, Consumer<TranslationEnvironment> environmentConsumer) {
        // first pass of the scope translator -> define the scopes
        ScopeTranslator<Object> scopeTranslator = factory.create(environment, false);
        input.accept(scopeTranslator);

        if (environmentConsumer != null) {
            environmentConsumer.accept(environment);
        }

        // second pass of the scope translator -> identify free vars (wrapped by cells)
        ScopeTranslator<Object> cellsTranslator = factory.create(environment, true);
        input.accept(cellsTranslator);
        // create frame slots for cell and free vars
        environment.createFrameSlotsForCellAndFreeVars();
    }

    @Override
    public T visitFile_input(Python3Parser.File_inputContext ctx) {
        environment.beginScope(ctx, ScopeInfo.ScopeKind.Module);
        T node = super.visitFile_input(ctx);
        environment.endScope(ctx);
        return node;
    }

    @Override
    public T visitSingle_input(Single_inputContext ctx) {
        if (interactive) {
            environment.beginScope(ctx, ScopeInfo.ScopeKind.Module);
        }
        T node = super.visitSingle_input(ctx);
        if (interactive) {
            environment.endScope(ctx);
        }

        return node;
    }

    @Override
    public T visitEval_input(Python3Parser.Eval_inputContext ctx) {
        environment.beginScope(ctx, ScopeInfo.ScopeKind.Module);
        T node = super.visitEval_input(ctx);
        environment.endScope(ctx);
        return node;
    }

    @Override
    public T visitFuncdef(Python3Parser.FuncdefContext ctx) {
        if (ctx.parent instanceof Python3Parser.DecoratedContext) {
            // TODO: get the decorators
        }
        environment.createLocal(ctx.NAME().getText());
        argListCompilers.push(new ArgListCompiler<>(core));
        ctx.parameters().accept(argListCompilers.peek());
        ctx.parameters().accept(this);
        environment.beginScope(ctx, ScopeKind.Function);
        try {
            ArgListCompiler<T> argListCompiler = argListCompilers.pop();
            for (String name : argListCompiler.names) {
                environment.createLocal(name);
            }
            return ctx.suite().accept(this);
        } finally {
            environment.endScope(ctx);
        }
    }

    @Override
    public T visitLambdef_nocond(Python3Parser.Lambdef_nocondContext ctx) {
        argListCompilers.push(new ArgListCompiler<>(core));
        ctx.accept(argListCompilers.peek());
        environment.beginScope(ctx, ScopeKind.Function);
        try {
            return super.visitLambdef_nocond(ctx);
        } finally {
            environment.endScope(ctx);
        }
    }

    @Override
    public T visitLambdef_nocond_body(Python3Parser.Lambdef_nocond_bodyContext ctx) {
        ArgListCompiler<T> argListCompiler = argListCompilers.pop();
        for (String name : argListCompiler.names) {
            environment.createLocal(name);
        }
        // TODO: insert init code
        return super.visitLambdef_nocond_body(ctx);
    }

    @Override
    public T visitLambdef(Python3Parser.LambdefContext ctx) {
        argListCompilers.push(new ArgListCompiler<>(core));
        ctx.accept(argListCompilers.peek());
        environment.beginScope(ctx, ScopeKind.Function);
        try {
            return super.visitLambdef(ctx);
        } finally {
            environment.endScope(ctx);
        }
    }

    @Override
    public T visitLambdef_body(Python3Parser.Lambdef_bodyContext ctx) {
        ArgListCompiler<T> argListCompiler = argListCompilers.pop();
        for (String name : argListCompiler.names) {
            environment.createLocal(name);
        }
        // TODO: insert init code
        return super.visitLambdef_body(ctx);
    }

    @Override
    public T visitImport_as_name(Python3Parser.Import_as_nameContext ctx) {
        List<TerminalNode> name = ctx.NAME();
        if (name.size() == 1) {
            environment.createLocal(name.get(0).getText());
        } else if (name.size() == 2) {
            environment.createLocal(name.get(1).getText());
        } else {
            throw new RuntimeException("Impossible case");
        }
        return super.visitImport_as_name(ctx);
    }

    @Override
    public T visitDotted_as_name(Python3Parser.Dotted_as_nameContext ctx) {
        TerminalNode asName = ctx.NAME();
        if (asName != null) {
            environment.createLocal(asName.getText());
        } else {
            environment.createLocal(ctx.dotted_name().NAME().get(0).getText());
        }
        return super.visitDotted_as_name(ctx);
    }

    @Override
    public T visitImport_from(Python3Parser.Import_fromContext ctx) {
        // TODO: right now, no future imports are supported
        return super.visitImport_from(ctx);
    }

    @Override
    public T visitGlobal_stmt(Python3Parser.Global_stmtContext ctx) {
        for (TerminalNode name : ctx.NAME()) {
            environment.addLocalGlobals(name.getText());
        }
        return super.visitGlobal_stmt(ctx);
    }

    @Override
    public T visitNonlocal_stmt(Python3Parser.Nonlocal_stmtContext ctx) {
        for (TerminalNode name : ctx.NAME()) {
            environment.addNonlocal(name.getText());
        }
        return super.visitNonlocal_stmt(ctx);
    }

    @Override
    public T visitClassdef(Python3Parser.ClassdefContext ctx) {
        environment.createLocal(ctx.NAME().getText());
        environment.beginScope(ctx, ScopeKind.Class);
        try {
            return super.visitClassdef(ctx);
        } finally {
            environment.endScope(ctx);
        }
    }

    @Override
    public T visitWith_item(Python3Parser.With_itemContext ctx) {
        if (ctx.expr() != null) {
            for (String name : ctx.expr().accept(new ExtractNameVisitor())) {
                environment.createLocal(name);
            }
        }
        return super.visitWith_item(ctx);
    }

    private void declareNames(ParserRuleContext ctx) {
        for (String name : ctx.accept(new ExtractNameVisitor())) {
            environment.createLocal(name);
        }
    }

    @Override
    public T visitExpr_stmt(Python3Parser.Expr_stmtContext ctx) {
        if (ctx.annassign() != null) {
            // TODO ...
        } else if (ctx.augassign() != null) {
            declareNames(ctx.testlist_star_expr());
        } else if (!ctx.normassign().isEmpty()) {
            declareNames(ctx.testlist_star_expr());
            // last rhs is not assigned to
            for (int i = 0; i < ctx.normassign().size() - 1; i++) {
                Python3Parser.NormassignContext normassign = ctx.normassign(i);
                if (normassign.testlist_star_expr() != null) {
                    declareNames(normassign.testlist_star_expr());
                }
            }
        }
        return super.visitExpr_stmt(ctx);
    }

    @Override
    public T visitFor_stmt(Python3Parser.For_stmtContext ctx) {
        declareNames(ctx.exprlist());
        return super.visitFor_stmt(ctx);
    }

    private static class ExtractNameVisitor extends Python3BaseVisitor<ArrayList<String>> {
        @Override
        protected ArrayList<String> aggregateResult(ArrayList<String> aggregate, ArrayList<String> nextResult) {
            if (aggregate == null) {
                return nextResult;
            } else if (nextResult == null) {
                return aggregate;
            } else {
                aggregate.addAll(nextResult);
                return aggregate;
            }
        }

        @Override
        public ArrayList<String> visitAtom_expr(Python3Parser.Atom_exprContext ctx) {
            if (ctx.trailer().isEmpty()) {
                return super.visitAtom_expr(ctx);
            }
            return new ArrayList<>();
        }

        @Override
        public ArrayList<String> visitAtom(Python3Parser.AtomContext ctx) {
            if (ctx.NAME() != null) {
                ArrayList<String> arrayList = new ArrayList<>(1);
                arrayList.add(ctx.NAME().getText());
                return arrayList;
            } else {
                return super.visitAtom(ctx);
            }
        }
    }

    @Override
    public T visitSetmaker(Python3Parser.SetmakerContext ctx) {
        if (ctx.comp_for() != null) {
            return visitGenerator(ctx, c -> super.visitSetmaker(ctx));
        } else {
            return super.visitSetmaker(ctx);
        }
    }

    @Override
    public T visitDictmaker(Python3Parser.DictmakerContext ctx) {
        if (ctx.comp_for() != null) {
            return visitGenerator(ctx, c -> super.visitDictmaker(ctx));
        } else {
            return super.visitDictmaker(ctx);
        }
    }

    @Override
    public T visitTestlist_comp(Python3Parser.Testlist_compContext ctx) {
        if (ctx.comp_for() != null) {
            return visitGenerator(ctx, c -> super.visitTestlist_comp(ctx));
        } else {
            return super.visitTestlist_comp(ctx);
        }
    }

    @Override
    public T visitArgument(Python3Parser.ArgumentContext ctx) {
        if (ctx.comp_for() == null) {
            return super.visitArgument(ctx);
        } else {
            return visitGenerator(ctx, c -> super.visitArgument(ctx));
        }
    }

    @Override
    public T visitComp_for(Python3Parser.Comp_forContext ctx) {
        declareNames(ctx.exprlist());
        T or_test = ctx.or_test().accept(this);
        if (ctx.comp_iter() != null) {
            return aggregateResult(or_test, ctx.comp_iter().accept(this));
        } else {
            return or_test;
        }
    }

    private T visitGenerator(ParserRuleContext ctx, Function<ParserRuleContext, T> block) {
        environment.beginScope(ctx, ScopeKind.Generator);
        try {
            return block.apply(ctx);
        } finally {
            environment.endScope(ctx);
        }
    }

    @Override
    public T visitDefparameter(Python3Parser.DefparameterContext ctx) {
        if (trackCells) {
            if (ctx.test() != null) {
                String identifier = ctx.test().getText();
                environment.registerCellVariable(identifier);
                return null;
            }
        }
        return super.visitDefparameter(ctx);
    }

    @Override
    public T visitAtom(Python3Parser.AtomContext ctx) {
        if (trackCells) {
            TerminalNode name = ctx.NAME();
            if (name != null) {
                environment.registerCellVariable(name.getText());
            }
        }
        return super.visitAtom(ctx);
    }
}
