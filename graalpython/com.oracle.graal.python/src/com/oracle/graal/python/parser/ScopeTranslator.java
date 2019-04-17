/*
 * Copyright (c) 2017, 2019, Oracle and/or its affiliates.
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
import java.util.HashSet;
import java.util.List;
import java.util.function.Function;

import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.tree.TerminalNode;

import com.oracle.graal.python.builtins.objects.cell.PCell;
import com.oracle.graal.python.parser.ScopeInfo.ScopeKind;
import com.oracle.graal.python.parser.antlr.Python3BaseVisitor;
import com.oracle.graal.python.parser.antlr.Python3Parser;
import com.oracle.graal.python.parser.antlr.Python3Parser.Except_clauseContext;
import com.oracle.graal.python.parser.antlr.Python3Parser.Single_inputContext;
import com.oracle.graal.python.runtime.PythonParser.ParserErrorCallback;
import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.FrameSlot;
import com.oracle.truffle.api.frame.FrameUtil;

public final class ScopeTranslator<T> extends Python3BaseVisitor<T> {

    private final TranslationEnvironment environment;
    private final ArrayList<ArgListCompiler<T>> argListCompilers;
    private final ParserErrorCallback errors;
    private final boolean interactive;
    private final FrameDescriptor curInlineLocals; // used for inline parsing (when != null)

    private final ArrayList<String> possibleCellIdentifiers = new ArrayList<>();
    private final ArrayList<ScopeInfo> possibleCellScopes = new ArrayList<>();

    public ScopeTranslator(ParserErrorCallback errors, TranslationEnvironment environment, boolean interactive, FrameDescriptor curInlineLocals) {
        this.errors = errors;
        this.environment = environment;
        this.argListCompilers = new ArrayList<>();
        this.interactive = interactive;
        this.curInlineLocals = curInlineLocals;
        assert curInlineLocals == null || !interactive;
    }

    @Override
    public T visitFile_input(Python3Parser.File_inputContext ctx) {
        ctx.scope = environment.pushScope(ctx, ScopeInfo.ScopeKind.Module);
        try {
            return super.visitFile_input(ctx);
        } finally {
            environment.popScope();
        }
    }

    @Override
    public T visitSingle_input(Single_inputContext ctx) {
        if (!interactive && curInlineLocals != null) {
            ctx.scope = environment.pushScope(ctx, ScopeInfo.ScopeKind.Function, curInlineLocals);
        } else {
            ctx.scope = environment.pushScope(ctx, ScopeInfo.ScopeKind.Module);
        }
        try {
            return super.visitSingle_input(ctx);
        } finally {
            environment.popScope();
        }
    }

    @Override
    public T visitEval_input(Python3Parser.Eval_inputContext ctx) {
        ctx.scope = environment.pushScope(ctx, ScopeInfo.ScopeKind.Module);
        try {
            return super.visitEval_input(ctx);
        } finally {
            environment.popScope();
        }
    }

    @Override
    public T visitDecorator(Python3Parser.DecoratorContext ctx) {
        registerPossibleCell(ctx.dotted_name().NAME(0).getText());
        return super.visitDecorator(ctx);
    }

    @Override
    public T visitFuncdef(Python3Parser.FuncdefContext ctx) {
        environment.createLocal(ctx.NAME().getText());
        ArgListCompiler<T> argListCompiler = new ArgListCompiler<>(errors);
        argListCompilers.add(argListCompiler);
        ctx.parameters().accept(argListCompiler);
        ctx.parameters().accept(this);
        ctx.scope = environment.pushScope(ctx, ScopeKind.Function);
        try {
            ArgListCompiler<T> argListCompiler2 = argListCompilers.remove(argListCompilers.size() - 1);
            assert argListCompiler2 == argListCompiler;
            for (String name : argListCompiler.names) {
                environment.createLocal(name);
            }
            return ctx.suite().accept(this);
        } finally {
            environment.popScope();
        }
    }

    @Override
    public T visitLambdef_nocond(Python3Parser.Lambdef_nocondContext ctx) {
        ArgListCompiler<T> argListCompiler = new ArgListCompiler<>(errors);
        argListCompilers.add(argListCompiler);
        ctx.accept(argListCompiler);
        ctx.scope = environment.pushScope(ctx, ScopeKind.Function);
        try {
            return super.visitLambdef_nocond(ctx);
        } finally {
            environment.popScope();
        }
    }

    @Override
    public T visitLambdef_nocond_body(Python3Parser.Lambdef_nocond_bodyContext ctx) {
        ArgListCompiler<T> argListCompiler = argListCompilers.remove(argListCompilers.size() - 1);
        for (String name : argListCompiler.names) {
            environment.createLocal(name);
        }
        // TODO: insert init code
        return super.visitLambdef_nocond_body(ctx);
    }

    @Override
    public T visitLambdef(Python3Parser.LambdefContext ctx) {
        ArgListCompiler<T> argListCompiler = new ArgListCompiler<>(errors);
        argListCompilers.add(argListCompiler);
        if (ctx.varargslist() != null) {
            ctx.accept(argListCompiler);
        }
        ctx.scope = environment.pushScope(ctx, ScopeKind.Function);
        try {
            return super.visitLambdef(ctx);
        } finally {
            environment.popScope();
        }
    }

    @Override
    public T visitLambdef_body(Python3Parser.Lambdef_bodyContext ctx) {
        ArgListCompiler<T> argListCompiler = argListCompilers.remove(argListCompilers.size() - 1);
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
            String asName = name.get(0).getText();
            environment.createLocal(asName);
        } else if (name.size() == 2) {
            String asName = name.get(1).getText();
            environment.createLocal(asName);
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
            String identifier = name.getText();
            environment.addNonlocal(identifier);
            registerPossibleCell(identifier);
        }
        return super.visitNonlocal_stmt(ctx);
    }

    @Override
    public T visitClassdef(Python3Parser.ClassdefContext ctx) {
        environment.createLocal(ctx.NAME().getText());
        ctx.scope = environment.pushScope(ctx, ScopeKind.Class);
        try {
            return super.visitClassdef(ctx);
        } finally {
            environment.popScope();
        }
    }

    @Override
    public T visitWith_item(Python3Parser.With_itemContext ctx) {
        if (ctx.expr() != null) {
            ExtractNameVisitor visitor = new ExtractNameVisitor();
            ctx.expr().accept(visitor);
            for (String name : visitor.names) {
                environment.createLocal(name);
            }
        }
        return super.visitWith_item(ctx);
    }

    private void declareNames(ParserRuleContext ctx) {
        ExtractNameVisitor visitor = new ExtractNameVisitor();
        ctx.accept(visitor);
        for (String name : visitor.names) {
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

    private static class ExtractNameVisitor extends Python3BaseVisitor<Object> {

        private final ArrayList<String> names = new ArrayList<>();

        @Override
        protected Object aggregateResult(Object aggregate, Object nextResult) {
            return null;
        }

        @Override
        public Object visitAtom_expr(Python3Parser.Atom_exprContext ctx) {
            if (ctx.trailer().isEmpty()) {
                super.visitAtom_expr(ctx);
            }
            return null;
        }

        @Override
        public Object visitAtom(Python3Parser.AtomContext ctx) {
            if (ctx.NAME() != null) {
                names.add(ctx.NAME().getText());
            } else {
                super.visitAtom(ctx);
            }
            return null;
        }
    }

    @Override
    public T visitSetmaker(Python3Parser.SetmakerContext ctx) {
        if (ctx.comp_for() != null) {
            return visitGenerator(ctx, ctx.comp_for(), c -> super.visitSetmaker(ctx));
        } else {
            return super.visitSetmaker(ctx);
        }
    }

    @Override
    public T visitDictmaker(Python3Parser.DictmakerContext ctx) {
        if (ctx.comp_for() != null) {
            return visitGenerator(ctx, ctx.comp_for(), c -> super.visitDictmaker(ctx));
        } else {
            return super.visitDictmaker(ctx);
        }
    }

    @Override
    public T visitTestlist_comp(Python3Parser.Testlist_compContext ctx) {
        if (ctx.comp_for() != null) {
            return visitGenerator(ctx, ctx.comp_for(), c -> super.visitTestlist_comp(ctx));
        } else {
            return super.visitTestlist_comp(ctx);
        }
    }

    @Override
    public T visitArgument(Python3Parser.ArgumentContext ctx) {
        if (ctx.comp_for() == null) {
            return super.visitArgument(ctx);
        } else {
            return visitGenerator(ctx, ctx.comp_for(), c -> super.visitArgument(ctx));
        }
    }

    @Override
    public T visitComp_for(Python3Parser.Comp_forContext ctx) {
        declareNames(ctx.exprlist());
        visitExprlist(ctx.exprlist());

        if (!(ctx.getParent() instanceof Python3Parser.Comp_iterContext)) {
            // the first iterator is eagerly evaluated in the outside scope, but any iterator under
            // the comp_iter is not
            ScopeInfo currentGeneratorScope = environment.popScope();
            visitOr_test(ctx.or_test());
            environment.pushScope(currentGeneratorScope);
        } else {
            visitOr_test(ctx.or_test());
        }

        if (ctx.comp_iter() != null) {
            visitComp_iter(ctx.comp_iter());
        }
        return null;
    }

    private T visitGenerator(ParserRuleContext ctx, Python3Parser.Comp_forContext compctx, Function<ParserRuleContext, T> block) {
        compctx.scope = environment.pushScope(ctx, ScopeKind.Generator);
        try {
            return block.apply(ctx);
        } finally {
            environment.popScope();
        }
    }

    public void registerPossibleCell(String identifier) {
        if (!environment.isInModuleScope() && environment.findFrameSlot(identifier) == null) {
            possibleCellIdentifiers.add(identifier);
            possibleCellScopes.add(environment.getCurrentScope());
        }
    }

    public void setFreeVarsInRootScope(Frame frame) {
        if (frame != null) {
            for (Object identifier : frame.getFrameDescriptor().getIdentifiers()) {
                FrameSlot frameSlot = frame.getFrameDescriptor().findFrameSlot(identifier);
                if (frameSlot != null && frame.isObject(frameSlot)) {
                    Object value = FrameUtil.getObjectSafe(frame, frameSlot);
                    if (value instanceof PCell) {
                        environment.getGlobalScope().addFreeVar((String) frameSlot.getIdentifier(), false);
                    }
                }
            }
        }
    }

    public void createFrameSlotsForCellAndFreeVars() {
        assert possibleCellIdentifiers.size() == possibleCellScopes.size();

        HashSet<ScopeInfo> scopes = new HashSet<>();
        for (int i = 0; i < possibleCellIdentifiers.size(); i++) {
            String identifier = possibleCellIdentifiers.get(i);
            ScopeInfo cellScope = possibleCellScopes.get(i);
            if (cellScope != environment.getGlobalScope() && cellScope.findFrameSlot(identifier) == null) {
                // symbol frameslot not found in current scope => free variable in current scope
                ScopeInfo definitionScope = TranslationEnvironment.findVariableScope(cellScope, identifier);
                if (definitionScope != null && definitionScope != environment.getGlobalScope()) {
                    definitionScope.addCellVar(identifier);
                    // register it as a free variable in all parent scopes up until the defining
                    // scope (except it)
                    scopes.add(definitionScope);
                    ScopeInfo scope = cellScope;
                    while (scope != definitionScope) {
                        scopes.add(scope);
                        scope.addFreeVar(identifier);
                        scope = scope.getParent();
                    }
                }
            }
        }
        for (ScopeInfo scope : scopes) {
            scope.createFrameSlotsForCellAndFreeVars();
        }
        environment.getGlobalScope().createFrameSlotsForCellAndFreeVars();
    }

    @Override
    public T visitDefparameter(Python3Parser.DefparameterContext ctx) {
        if (ctx.test() != null) {
            String identifier = ctx.test().getText();
            registerPossibleCell(identifier);
        }
        return super.visitDefparameter(ctx);
    }

    @Override
    public T visitAtom(Python3Parser.AtomContext ctx) {
        TerminalNode name = ctx.NAME();
        if (name != null) {
            String identifier = name.getText();
            registerPossibleCell(identifier);
        }
        return super.visitAtom(ctx);
    }

    @Override
    public T visitExcept_clause(Except_clauseContext ctx) {
        TerminalNode name = ctx.NAME();
        if (name != null) {
            String identifier = name.getText();
            environment.createLocal(identifier);
        }
        return super.visitExcept_clause(ctx);
    }
}
