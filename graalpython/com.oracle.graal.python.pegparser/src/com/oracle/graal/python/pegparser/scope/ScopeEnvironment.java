/*
 * Copyright (c) 2021, 2024, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.pegparser.scope;

import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map.Entry;
import java.util.Stack;

import com.oracle.graal.python.pegparser.ErrorCallback;
import com.oracle.graal.python.pegparser.ErrorCallback.ErrorType;
import com.oracle.graal.python.pegparser.FutureFeature;
import com.oracle.graal.python.pegparser.scope.Scope.DefUse;
import com.oracle.graal.python.pegparser.scope.Scope.ScopeFlags;
import com.oracle.graal.python.pegparser.scope.Scope.ScopeType;
import com.oracle.graal.python.pegparser.sst.AliasTy;
import com.oracle.graal.python.pegparser.sst.ArgTy;
import com.oracle.graal.python.pegparser.sst.ArgumentsTy;
import com.oracle.graal.python.pegparser.sst.ComprehensionTy;
import com.oracle.graal.python.pegparser.sst.ExceptHandlerTy;
import com.oracle.graal.python.pegparser.sst.ExprContextTy;
import com.oracle.graal.python.pegparser.sst.ExprTy;
import com.oracle.graal.python.pegparser.sst.KeywordTy;
import com.oracle.graal.python.pegparser.sst.MatchCaseTy;
import com.oracle.graal.python.pegparser.sst.ModTy;
import com.oracle.graal.python.pegparser.sst.PatternTy;
import com.oracle.graal.python.pegparser.sst.SSTNode;
import com.oracle.graal.python.pegparser.sst.SSTreeVisitor;
import com.oracle.graal.python.pegparser.sst.StmtTy;
import com.oracle.graal.python.pegparser.sst.TypeIgnoreTy;
import com.oracle.graal.python.pegparser.sst.WithItemTy;

/**
 * Roughly plays the role of CPython's {@code symtable}.
 *
 * Just like in CPython, the scope analysis uses two passes. The first simply visits everything and
 * creates {@link Scope} objects with some facts about the names. The second pass determines based
 * on the scope nestings which names are free, cells etc.
 */
public class ScopeEnvironment {
    // error strings used for warnings
    private final static String GLOBAL_PARAM = "name '%s' is parameter and global";
    private final static String NONLOCAL_PARAM = "name '%s' is parameter and nonlocal";
    private final static String GLOBAL_AFTER_ASSIGN = "name '%s' is assigned to before global declaration";
    private final static String NONLOCAL_AFTER_ASSIGN = "name '%s' is assigned to before nonlocal declaration";
    private final static String GLOBAL_AFTER_USE = "name '%s' is used prior to global declaration";
    private final static String NONLOCAL_AFTER_USE = "name '%s' is used prior to nonlocal declaration";
    private final static String GLOBAL_ANNOT = "annotated name '%s' can't be global";
    private final static String NONLOCAL_ANNOT = "annotated name '%s' can't be nonlocal";
    private final static String IMPORT_STAR_WARNING = "import * only allowed at module level";
    private final static String NAMED_EXPR_COMP_IN_CLASS = "assignment expression within a comprehension cannot be used in a class body";
    private final static String NAMED_EXPR_COMP_CONFLICT = "assignment expression cannot rebind comprehension iteration variable '%s'";
    private final static String NAMED_EXPR_COMP_INNER_LOOP_CONFLICT = "comprehension inner loop cannot rebind assignment expression target '%s'";
    private final static String NAMED_EXPR_COMP_ITER_EXPR = "assignment expression cannot be used in a comprehension iterable expression";
    private final static String DUPLICATE_ARGUMENT = "duplicate argument '%s' in function definition";

    final Scope topScope;
    final HashMap<SSTNode, Scope> blocks = new HashMap<>();
    final ErrorCallback errorCallback;
    final EnumSet<FutureFeature> futureFeatures;
    final HashMap<Scope, Scope> parents = new HashMap<>();

    public static ScopeEnvironment analyze(ModTy moduleNode, ErrorCallback errorCallback, EnumSet<FutureFeature> futureFeatures) {
        return new ScopeEnvironment(moduleNode, errorCallback, futureFeatures);
    }

    private ScopeEnvironment(ModTy moduleNode, ErrorCallback errorCallback, EnumSet<FutureFeature> futureFeatures) {
        // First pass, similar to the entry point `symtable_enter_block' on CPython
        this.errorCallback = errorCallback;
        this.futureFeatures = futureFeatures;
        FirstPassVisitor visitor = new FirstPassVisitor(moduleNode, this);
        topScope = visitor.currentScope;
        moduleNode.accept(visitor);

        // Second pass
        analyzeBlock(topScope, null, null, null);
    }

    @Override
    public String toString() {
        return "ScopeEnvironment\n" + topScope.toString(1);
    }

    private void addScope(SSTNode node, Scope scope) {
        blocks.put(node, scope);
    }

    public Scope lookupScope(SSTNode node) {
        return blocks.get(node);
    }

    public Scope lookupParent(Scope scope) {
        return parents.get(scope);
    }

    public Scope getTopScope() {
        return topScope;
    }

    private void analyzeBlock(Scope scope, HashSet<String> bound, HashSet<String> free, HashSet<String> global) {
        HashSet<String> local = new HashSet<>();
        HashMap<String, DefUse> scopes = new HashMap<>();
        HashSet<String> newGlobal = new HashSet<>();
        HashSet<String> newFree = new HashSet<>();
        HashSet<String> newBound = new HashSet<>();

        if (scope.type == ScopeType.Class) {
            if (global != null) {
                newGlobal.addAll(global);
            }
            if (bound != null) {
                newBound.addAll(bound);
            }
        }

        for (Entry<String, EnumSet<DefUse>> e : scope.symbols.entrySet()) {
            analyzeName(scope, scopes, e.getKey(), e.getValue(), bound, local, free, global);
        }

        if (scope.type != ScopeType.Class) {
            if (scope.type == ScopeType.Function) {
                newBound.addAll(local);
            }
            if (bound != null) {
                newBound.addAll(bound);
            }
            if (global != null) {
                newGlobal.addAll(global);
            }
        } else {
            newBound.add("__class__");
        }

        HashSet<String> allFree = new HashSet<>();
        for (Scope s : scope.children) {
            // inline the logic from CPython's analyze_child_block
            HashSet<String> tempBound = new HashSet<>(newBound);
            HashSet<String> tempFree = new HashSet<>(newFree);
            HashSet<String> tempGlobal = new HashSet<>(newGlobal);
            analyzeBlock(s, tempBound, tempFree, tempGlobal);
            allFree.addAll(tempFree);
            if (s.flags.contains(ScopeFlags.HasFreeVars) || s.flags.contains(ScopeFlags.HasChildWithFreeVars)) {
                scope.flags.add(ScopeFlags.HasChildWithFreeVars);
            }
        }

        newFree.addAll(allFree);

        switch (scope.type) {
            case Function:
                analyzeCells(scopes, newFree);
                break;
            case Class:
                dropClassFree(scope, newFree);
                break;
            default:
                break;
        }

        updateSymbols(scope.symbols, scopes, bound, newFree, scope.type == ScopeType.Class);

        if (free != null) {
            free.addAll(newFree);
        }
    }

    private void analyzeName(Scope scope, HashMap<String, DefUse> scopes, String name, EnumSet<DefUse> flags, HashSet<String> bound, HashSet<String> local, HashSet<String> free,
                    HashSet<String> global) {
        if (flags.contains(DefUse.DefGlobal)) {
            if (flags.contains(DefUse.DefNonLocal)) {
                errorCallback.onError(ErrorType.Syntax, scope.getDirective(name), "name '%s' is nonlocal and global", name);
            }
            scopes.put(name, DefUse.GlobalExplicit);
            if (global != null) {
                global.add(name);
            }
            if (bound != null) {
                bound.remove(name);
            }
        } else if (flags.contains(DefUse.DefNonLocal)) {
            if (bound == null) {
                errorCallback.onError(ErrorCallback.ErrorType.Syntax, scope.getDirective(name), "nonlocal declaration not allowed at module level");
            } else if (!bound.contains(name)) {
                errorCallback.onError(ErrorType.Syntax, scope.getDirective(name), "no binding for nonlocal '%s' found", name);
            }
            scopes.put(name, DefUse.Free);
            scope.flags.add(ScopeFlags.HasFreeVars);
            if (free != null) {
                // free is null in the module scope in which case we already reported an error above
                free.add(name);
            }
        } else if (!Collections.disjoint(flags, DefUse.DefBound)) {
            scopes.put(name, DefUse.Local);
            local.add(name);
            if (global != null) {
                global.remove(name);
            }
        } else if (bound != null && bound.contains(name)) {
            scopes.put(name, DefUse.Free);
            scope.flags.add(ScopeFlags.HasFreeVars);
            free.add(name);
        } else if (global != null && global.contains(name)) {
            scopes.put(name, DefUse.GlobalImplicit);
        } else {
            if (scope.flags.contains(ScopeFlags.IsNested)) {
                scope.flags.add(ScopeFlags.HasFreeVars);
            }
            scopes.put(name, DefUse.GlobalImplicit);
        }
    }

    private static void analyzeCells(HashMap<String, DefUse> scopes, HashSet<String> free) {
        for (Entry<String, DefUse> e : scopes.entrySet()) {
            if (e.getValue() != DefUse.Local) {
                continue;
            }
            String name = e.getKey();
            if (!free.contains(name)) {
                continue;
            }
            scopes.put(name, DefUse.Cell);
            free.remove(name);
        }
    }

    private static void dropClassFree(Scope scope, HashSet<String> free) {
        if (free.remove("__class__")) {
            scope.flags.add(ScopeFlags.NeedsClassClosure);
        }
    }

    private static void updateSymbols(HashMap<String, EnumSet<DefUse>> symbols, HashMap<String, DefUse> scopes, HashSet<String> bound, HashSet<String> free, boolean isClass) {
        for (Entry<String, EnumSet<DefUse>> e : symbols.entrySet()) {
            String name = e.getKey();
            DefUse vScope = scopes.get(name);
            assert !vScope.toString().startsWith("Def");
            // CPython now stores the VariableScope into the DefUse flags at a shifted offset
            e.getValue().add(vScope);
        }

        for (String name : free) {
            EnumSet<DefUse> v = symbols.get(name);
            if (v != null) {
                if (isClass && (v.contains(DefUse.DefGlobal) || !Collections.disjoint(v, DefUse.DefBound))) {
                    v.add(DefUse.DefFreeClass);
                }
            } else if (bound != null && !bound.contains(name)) {
            } else {
                symbols.put(name, EnumSet.of(DefUse.Free));
            }
        }
    }

    public static String mangle(String className, String name) {
        if (className == null || !name.startsWith("__")) {
            return name;
        }
        if (name.endsWith("__") || name.contains(".")) {
            return name;
        }
        int offset = 0;
        while (className.charAt(offset) == '_') {
            offset++;
            if (offset >= className.length()) {
                return name;
            }
        }
        return "_" + className.substring(offset) + name;
    }

    private static final class FirstPassVisitor implements SSTreeVisitor<Void> {
        private final Stack<Scope> stack;
        private final HashMap<String, EnumSet<DefUse>> globals;
        private final ScopeEnvironment env;
        private Scope currentScope;
        private String currentClassName;

        private FirstPassVisitor(ModTy moduleNode, ScopeEnvironment env) {
            this.stack = new Stack<>();
            this.env = env;
            enterBlock(null, Scope.ScopeType.Module, moduleNode);
            this.globals = this.currentScope.symbols;
        }

        private void enterBlock(String name, Scope.ScopeType type, SSTNode ast) {
            Scope scope = new Scope(name, type, ast);
            env.addScope(ast, scope);
            stack.add(scope);
            Scope prev = currentScope;
            if (prev != null) {
                scope.comprehensionIterExpression = prev.comprehensionIterExpression;
                if (prev.type == ScopeType.Function || prev.isNested()) {
                    scope.flags.add(ScopeFlags.IsNested);
                }
            }
            currentScope = scope;
            if (type == Scope.ScopeType.Annotation) {
                return;
            }
            env.parents.put(scope, prev);
            if (prev != null) {
                prev.children.add(scope);
            }
        }

        private void exitBlock() {
            stack.pop();
            currentScope = stack.peek();
        }

        private String mangle(String name) {
            return ScopeEnvironment.mangle(currentClassName, name);
        }

        private void addDef(String name, DefUse flag, SSTNode node) {
            addDef(name, flag, currentScope, node);
        }

        private void addDef(String name, DefUse flag, Scope scope, SSTNode node) {
            String mangled = mangle(name);
            EnumSet<DefUse> flags = scope.getUseOfName(mangled);
            if (flags != null) {
                if (flag == DefUse.DefParam && flags.contains(DefUse.DefParam)) {
                    env.errorCallback.onError(ErrorCallback.ErrorType.Syntax, node.getSourceRange(), DUPLICATE_ARGUMENT, mangled);
                }
                flags.add(flag);
            } else {
                flags = EnumSet.of(flag);
            }
            if (scope.flags.contains(ScopeFlags.IsVisitingIterTarget)) {
                if (flags.contains(DefUse.DefGlobal) || flags.contains(DefUse.DefNonLocal)) {
                    env.errorCallback.onError(ErrorCallback.ErrorType.Syntax, node.getSourceRange(), NAMED_EXPR_COMP_INNER_LOOP_CONFLICT, mangled);
                }
                flags.add(DefUse.DefCompIter);
            }
            scope.symbols.put(mangled, flags);
            switch (flag) {
                case DefParam:
                    if (scope.varnames.contains(mangled)) {
                        env.errorCallback.onError(ErrorCallback.ErrorType.Syntax, node.getSourceRange(), "duplicate argument '%s' in function definition", mangled);
                    }
                    scope.varnames.add(mangled);

                    break;
                case DefGlobal:
                    EnumSet<DefUse> globalFlags = globals.get(mangled);
                    if (globalFlags != null) {
                        globalFlags.add(flag);
                    } else {
                        globalFlags = EnumSet.of(flag);
                    }
                    globals.put(mangled, globalFlags);
                    break;
                default:
                    break;
            }
        }

        private void handleComprehension(ExprTy e, String scopeName, ComprehensionTy[] generators, ExprTy element, ExprTy value, Scope.ComprehensionType comprehensionType) {
            boolean isGenerator = e instanceof ExprTy.GeneratorExp;
            ComprehensionTy outermost = generators[0];
            currentScope.comprehensionIterExpression++;
            outermost.iter.accept(this);
            currentScope.comprehensionIterExpression--;
            enterBlock(scopeName, Scope.ScopeType.Function, e);
            boolean isAsync;
            try {
                currentScope.comprehensionType = comprehensionType;
                if (outermost.isAsync) {
                    currentScope.flags.add(ScopeFlags.IsCoroutine);
                }
                currentScope.flags.add(ScopeFlags.IsComprehension);
                addDef(".0", DefUse.DefParam, value);
                currentScope.flags.add(ScopeFlags.IsVisitingIterTarget);
                outermost.target.accept(this);
                currentScope.flags.remove(ScopeFlags.IsVisitingIterTarget);
                visitSequence(outermost.ifs);
                for (int i = 1; i < generators.length; i++) {
                    generators[i].accept(this);
                }
                if (value != null) {
                    value.accept(this);
                }
                element.accept(this);
                if (isGenerator) {
                    currentScope.flags.add(ScopeFlags.IsGenerator);
                }
                isAsync = currentScope.isCoroutine() && !isGenerator;
            } finally {
                exitBlock();
            }
            if (isAsync) {
                currentScope.flags.add(ScopeFlags.IsCoroutine);
            }
        }

        private void raiseIfComprehensionBlock(ExprTy node) {
            String msg;
            switch (currentScope.comprehensionType) {
                case ListComprehension:
                    msg = "'yield' inside list comprehension";
                    break;
                case SetComprehension:
                    msg = "'yield' inside set comprehension";
                    break;
                case DictComprehension:
                    msg = "'yield' inside dict comprehension";
                    break;
                case GeneratorExpression:
                default:
                    msg = "'yield' inside generator expression";
                    break;
            }
            env.errorCallback.onError(ErrorCallback.ErrorType.Syntax, node.getSourceRange(), msg);
        }

        private void raiseIfAnnotationBlock(String name, ExprTy node) {
            if (currentScope.type == ScopeType.Annotation) {
                env.errorCallback.onError(ErrorType.Syntax, node.getSourceRange(), "'%s' can not be used within an annotation", name);
            }
        }

        private void visitAnnotation(ExprTy expr) {
            boolean futureAnnotations = env.futureFeatures.contains(FutureFeature.ANNOTATIONS);
            if (futureAnnotations) {
                enterBlock("_annotation", ScopeType.Annotation, expr);
            }
            try {
                expr.accept(this);
            } finally {
                if (futureAnnotations) {
                    exitBlock();
                }
            }
        }

        private void visitAnnotations(ArgTy[] args) {
            if (args != null) {
                for (ArgTy arg : args) {
                    if (arg.annotation != null) {
                        arg.annotation.accept(this);
                    }
                }
            }
        }

        private void visitAnnotations(StmtTy node, ArgumentsTy args, ExprTy returns) {
            boolean futureAnnotations = env.futureFeatures.contains(FutureFeature.ANNOTATIONS);
            if (args != null) {
                if (futureAnnotations) {
                    enterBlock("_annotation", ScopeType.Annotation, node);
                }
                try {
                    visitAnnotations(args.posOnlyArgs);
                    visitAnnotations(args.args);
                    if (args.varArg != null && args.varArg.annotation != null) {
                        args.varArg.annotation.accept(this);
                    }
                    if (args.kwArg != null && args.kwArg.annotation != null) {
                        args.kwArg.annotation.accept(this);
                    }
                    visitAnnotations(args.kwOnlyArgs);
                } finally {
                    if (futureAnnotations) {
                        exitBlock();
                    }
                }
            }
            if (returns != null) {
                visitAnnotation(returns);
            }
        }

        @Override
        public Void visit(AliasTy node) {
            String importedName = node.asName == null ? node.name : node.asName;
            int dotIndex = importedName.indexOf('.');
            if (dotIndex >= 0) {
                importedName = importedName.substring(0, dotIndex);
            }
            if ("*".equals(importedName)) {
                if (!currentScope.isModule()) {
                    env.errorCallback.onError(ErrorType.Syntax, node.getSourceRange(), IMPORT_STAR_WARNING);
                }
            } else {
                addDef(importedName, DefUse.DefImport, node);
            }
            return null;
        }

        @Override
        public Void visit(ArgTy node) {
            addDef(node.arg, DefUse.DefParam, node);
            return null;
        }

        @Override
        public Void visit(ArgumentsTy node) {
            visitSequence(node.posOnlyArgs);
            visitSequence(node.args);
            visitSequence(node.kwOnlyArgs);
            if (node.varArg != null) {
                node.varArg.accept(this);
                currentScope.flags.add(ScopeFlags.HasVarArgs);
            }
            if (node.kwArg != null) {
                node.kwArg.accept(this);
                currentScope.flags.add(ScopeFlags.HasVarKeywords);
            }
            return null;
        }

        @Override
        public Void visit(ExprTy.Attribute node) {
            node.value.accept(this);
            return null;
        }

        @Override
        public Void visit(ExprTy.Await node) {
            raiseIfAnnotationBlock("await expression", node);
            node.value.accept(this);
            currentScope.flags.add(ScopeFlags.IsCoroutine);
            return null;
        }

        @Override
        public Void visit(ExprTy.BinOp node) {
            node.left.accept(this);
            node.right.accept(this);
            return null;
        }

        @Override
        public Void visit(ExprTy.BoolOp node) {
            visitSequence(node.values);
            return null;
        }

        @Override
        public Void visit(ExprTy.Call node) {
            node.func.accept(this);
            visitSequence(node.args);
            visitSequence(node.keywords);
            return null;
        }

        @Override
        public Void visit(ExprTy.Compare node) {
            node.left.accept(this);
            visitSequence(node.comparators);
            return null;
        }

        @Override
        public Void visit(ExprTy.Constant node) {
            return null;
        }

        @Override
        public Void visit(ExprTy.Dict node) {
            visitSequence(node.keys);
            visitSequence(node.values);
            return null;
        }

        @Override
        public Void visit(ExprTy.DictComp node) {
            handleComprehension(node, "dictcomp", node.generators, node.key, node.value, Scope.ComprehensionType.DictComprehension);
            return null;
        }

        @Override
        public Void visit(ExprTy.FormattedValue node) {
            node.value.accept(this);
            if (node.formatSpec != null) {
                node.formatSpec.accept(this);
            }
            return null;
        }

        @Override
        public Void visit(ExprTy.GeneratorExp node) {
            handleComprehension(node, "genexp", node.generators, node.element, null, Scope.ComprehensionType.GeneratorExpression);
            return null;
        }

        @Override
        public Void visit(ExprTy.IfExp node) {
            node.test.accept(this);
            node.body.accept(this);
            node.orElse.accept(this);
            return null;
        }

        @Override
        public Void visit(ExprTy.JoinedStr node) {
            visitSequence(node.values);
            return null;
        }

        @Override
        public Void visit(ExprTy.Lambda node) {
            if (node.args != null) {
                visitSequence(node.args.defaults);
                visitSequence(node.args.kwDefaults);
            }
            enterBlock("lambda", ScopeType.Function, node);
            try {
                if (node.args != null) {
                    node.args.accept(this);
                }
                node.body.accept(this);
            } finally {
                exitBlock();
            }
            return null;
        }

        @Override
        public Void visit(ExprTy.List node) {
            visitSequence(node.elements);
            return null;
        }

        @Override
        public Void visit(ExprTy.ListComp node) {
            handleComprehension(node, "listcomp", node.generators, node.element, null, Scope.ComprehensionType.ListComprehension);
            return null;
        }

        @Override
        public Void visit(ExprTy.Name node) {
            addDef(node.id, node.context == ExprContextTy.Load ? DefUse.Use : DefUse.DefLocal, node);
            // Special-case super: it counts as a use of __class__
            if (node.context == ExprContextTy.Load && currentScope.type == ScopeType.Function &&
                            node.id.equals("super")) {
                addDef("__class__", DefUse.Use, node);
            }
            return null;
        }

        @Override
        public Void visit(ExprTy.NamedExpr node) {
            raiseIfAnnotationBlock("named expression", node);
            if (currentScope.comprehensionIterExpression > 0) {
                env.errorCallback.onError(ErrorCallback.ErrorType.Syntax, node.getSourceRange(), NAMED_EXPR_COMP_ITER_EXPR);
            }
            if (currentScope.flags.contains(ScopeFlags.IsComprehension)) {
                // symtable_extend_namedexpr_scope
                String targetName = ((ExprTy.Name) node.target).id;
                for (int i = stack.size() - 1; i >= 0; i--) {
                    Scope s = stack.get(i);
                    // If we find a comprehension scope, check for conflict
                    if (s.flags.contains(ScopeFlags.IsComprehension)) {
                        if (s.getUseOfName(targetName).contains(DefUse.DefCompIter)) {
                            env.errorCallback.onError(ErrorCallback.ErrorType.Syntax, node.getSourceRange(), NAMED_EXPR_COMP_CONFLICT);
                        }
                        continue;
                    }
                    // If we find a FunctionBlock entry, add as GLOBAL/LOCAL or NONLOCAL/LOCAL
                    if (s.type == ScopeType.Function) {
                        EnumSet<DefUse> uses = s.getUseOfName(targetName);
                        if (uses.contains(DefUse.DefGlobal)) {
                            addDef(targetName, DefUse.DefGlobal, node);
                        } else {
                            addDef(targetName, DefUse.DefNonLocal, node);
                        }
                        currentScope.recordDirective(mangle(targetName), node.getSourceRange());
                        addDef(targetName, DefUse.DefLocal, s, node);
                        break;
                    }
                    // If we find a ModuleBlock entry, add as GLOBAL
                    if (s.type == ScopeType.Module) {
                        addDef(targetName, DefUse.DefGlobal, node);
                        currentScope.recordDirective(mangle(targetName), node.getSourceRange());
                        addDef(targetName, DefUse.DefGlobal, s, node);
                        break;
                    }
                    // Disallow usage in ClassBlock
                    if (s.type == ScopeType.Class) {
                        env.errorCallback.onError(ErrorCallback.ErrorType.Syntax, node.getSourceRange(), NAMED_EXPR_COMP_IN_CLASS);
                    }
                }
            }
            node.value.accept(this);
            node.target.accept(this);
            return null;
        }

        @Override
        public Void visit(ExprTy.Set node) {
            visitSequence(node.elements);
            return null;
        }

        @Override
        public Void visit(ExprTy.SetComp node) {
            handleComprehension(node, "setcomp", node.generators, node.element, null, Scope.ComprehensionType.SetComprehension);
            return null;
        }

        @Override
        public Void visit(ExprTy.Slice node) {
            if (node.lower != null) {
                node.lower.accept(this);
            }
            if (node.upper != null) {
                node.upper.accept(this);
            }
            if (node.step != null) {
                node.step.accept(this);
            }
            return null;
        }

        @Override
        public Void visit(ExprTy.Starred node) {
            node.value.accept(this);
            return null;
        }

        @Override
        public Void visit(ExprTy.Subscript node) {
            node.value.accept(this);
            node.slice.accept(this);
            return null;
        }

        @Override
        public Void visit(ExprTy.Tuple node) {
            visitSequence(node.elements);
            return null;
        }

        @Override
        public Void visit(ExprTy.UnaryOp node) {
            node.operand.accept(this);
            return null;
        }

        @Override
        public Void visit(ExprTy.Yield node) {
            raiseIfAnnotationBlock("yield expression", node);
            if (node.value != null) {
                node.value.accept(this);
            }
            currentScope.flags.add(ScopeFlags.IsGenerator);
            if (currentScope.flags.contains(ScopeFlags.IsComprehension)) {
                raiseIfComprehensionBlock(node);
            }
            return null;
        }

        @Override
        public Void visit(ExprTy.YieldFrom node) {
            raiseIfAnnotationBlock("yield expression", node);
            if (node.value != null) {
                node.value.accept(this);
            }
            currentScope.flags.add(ScopeFlags.IsGenerator);
            if (currentScope.flags.contains(ScopeFlags.IsComprehension)) {
                raiseIfComprehensionBlock(node);
            }
            return null;
        }

        @Override
        public Void visit(KeywordTy node) {
            node.value.accept(this);
            return null;
        }

        @Override
        public Void visit(ModTy.Expression node) {
            node.body.accept(this);
            return null;
        }

        @Override
        public Void visit(ModTy.FunctionType node) {
            throw new UnsupportedOperationException("Not supported yet.");
        }

        @Override
        public Void visit(ModTy.Interactive node) {
            visitSequence(node.body);
            return null;
        }

        @Override
        public Void visit(ModTy.Module node) {
            visitSequence(node.body);
            return null;
        }

        @Override
        public Void visit(TypeIgnoreTy.TypeIgnore node) {
            throw new UnsupportedOperationException("Not supported yet.");
        }

        @Override
        public Void visit(StmtTy.AnnAssign node) {
            if (node.target instanceof ExprTy.Name) {
                ExprTy.Name name = (ExprTy.Name) node.target;
                EnumSet<DefUse> cur = currentScope.getUseOfName(mangle(name.id));
                if (cur != null && (cur.contains(DefUse.DefGlobal) || cur.contains(DefUse.DefNonLocal)) &&
                                currentScope.symbols != globals &&
                                node.isSimple) {
                    String msg = cur.contains(DefUse.DefGlobal) ? "annotated name '%s' can't be global" : "annotated name '%s' can't be nonlocal";
                    env.errorCallback.onError(ErrorType.Syntax, node.getSourceRange(), msg, name.id);
                    return null;
                }
                if (node.isSimple) {
                    addDef(name.id, DefUse.DefAnnot, node);
                    addDef(name.id, DefUse.DefLocal, node);
                } else {
                    if (node.value != null) {
                        addDef(name.id, DefUse.DefLocal, node);
                    }
                }
            } else {
                node.target.accept(this);
            }
            visitAnnotation(node.annotation);
            if (node.value != null) {
                node.value.accept(this);
            }
            return null;
        }

        @Override
        public Void visit(StmtTy.Assert node) {
            node.test.accept(this);
            if (node.msg != null) {
                node.msg.accept(this);
            }
            return null;
        }

        @Override
        public Void visit(StmtTy.Assign node) {
            visitSequence(node.targets);
            node.value.accept(this);
            return null;
        }

        @Override
        public Void visit(StmtTy.AsyncFor node) {
            node.target.accept(this);
            node.iter.accept(this);
            visitSequence(node.body);
            if (node.orElse != null) {
                visitSequence(node.orElse);
            }
            return null;
        }

        @Override
        public Void visit(StmtTy.AsyncFunctionDef node) {
            addDef(node.name, DefUse.DefLocal, node);
            if (node.args != null) {
                visitSequence(node.args.defaults);
                visitSequence(node.args.kwDefaults);
            }
            visitAnnotations(node, node.args, node.returns);
            visitSequence(node.decoratorList);
            enterBlock(node.name, ScopeType.Function, node);
            try {
                currentScope.flags.add(ScopeFlags.IsCoroutine);
                if (node.args != null) {
                    node.args.accept(this);
                }
                visitSequence(node.body);
            } finally {
                exitBlock();
            }
            return null;
        }

        @Override
        public Void visit(StmtTy.AsyncWith node) {
            visitSequence(node.items);
            visitSequence(node.body);
            return null;
        }

        @Override
        public Void visit(StmtTy.AugAssign node) {
            node.target.accept(this);
            node.value.accept(this);
            return null;
        }

        @Override
        public Void visit(StmtTy.ClassDef node) {
            addDef(node.name, DefUse.DefLocal, node);
            visitSequence(node.bases);
            visitSequence(node.keywords);
            visitSequence(node.decoratorList);
            String tmp = currentClassName;
            enterBlock(node.name, ScopeType.Class, node);
            try {
                currentClassName = node.name;
                visitSequence(node.body);
            } finally {
                currentClassName = tmp;
                exitBlock();
            }
            return null;
        }

        @Override
        public Void visit(StmtTy.Delete node) {
            visitSequence(node.targets);
            return null;
        }

        @Override
        public Void visit(StmtTy.Expr node) {
            node.value.accept(this);
            return null;
        }

        @Override
        public Void visit(StmtTy.For node) {
            node.target.accept(this);
            node.iter.accept(this);
            visitSequence(node.body);
            if (node.orElse != null) {
                visitSequence(node.orElse);
            }
            return null;
        }

        @Override
        public Void visit(StmtTy.FunctionDef node) {
            addDef(node.name, DefUse.DefLocal, node);
            if (node.args != null) {
                visitSequence(node.args.defaults);
                visitSequence(node.args.kwDefaults);
            }
            visitAnnotations(node, node.args, node.returns);
            visitSequence(node.decoratorList);
            enterBlock(node.name, ScopeType.Function, node);
            try {
                if (node.args != null) {
                    node.args.accept(this);
                }
                visitSequence(node.body);
            } finally {
                exitBlock();
            }
            return null;
        }

        @Override
        public Void visit(StmtTy.Global node) {
            for (String n : node.names) {
                String mangled = mangle(n);
                EnumSet<DefUse> cur = currentScope.getUseOfName(mangled);
                if (cur != null) {
                    String msg = null;
                    if (cur.contains(DefUse.DefParam)) {
                        msg = GLOBAL_PARAM;
                    } else if (cur.contains(DefUse.Use)) {
                        msg = GLOBAL_AFTER_USE;
                    } else if (cur.contains(DefUse.DefAnnot)) {
                        msg = GLOBAL_ANNOT;
                    } else if (cur.contains(DefUse.DefLocal)) {
                        msg = GLOBAL_AFTER_ASSIGN;
                    }
                    if (msg != null) {
                        env.errorCallback.onError(ErrorCallback.ErrorType.Syntax, node.getSourceRange(), msg, n);
                        continue;
                    }
                }
                addDef(n, DefUse.DefGlobal, node);
                currentScope.recordDirective(mangled, node.getSourceRange());
            }
            return null;
        }

        @Override
        public Void visit(StmtTy.If node) {
            node.test.accept(this);
            visitSequence(node.body);
            visitSequence(node.orElse);
            return null;
        }

        @Override
        public Void visit(StmtTy.Import node) {
            visitSequence(node.names);
            return null;
        }

        @Override
        public Void visit(StmtTy.ImportFrom node) {
            visitSequence(node.names);
            return null;
        }

        @Override
        public Void visit(StmtTy.Match node) {
            node.subject.accept(this);
            visitSequence(node.cases);
            return null;
        }

        @Override
        public Void visit(MatchCaseTy node) {
            node.pattern.accept(this);
            if (node.guard != null) {
                node.guard.accept(this);
            }
            visitSequence(node.body);
            return null;
        }

        @Override
        public Void visit(PatternTy.MatchAs node) {
            if (node.pattern != null) {
                node.pattern.accept(this);
            }
            if (node.name != null) {
                addDef(node.name, DefUse.DefLocal, node);
            }
            return null;
        }

        @Override
        public Void visit(PatternTy.MatchClass node) {
            node.cls.accept(this);
            visitSequence(node.patterns);
            visitSequence(node.kwdPatterns);
            return null;
        }

        @Override
        public Void visit(PatternTy.MatchMapping node) {
            visitSequence(node.keys);
            visitSequence(node.patterns);
            if (node.rest != null) {
                addDef(node.rest, DefUse.DefLocal, node);
            }
            return null;
        }

        @Override
        public Void visit(PatternTy.MatchOr node) {
            visitSequence(node.patterns);
            return null;
        }

        @Override
        public Void visit(PatternTy.MatchSequence node) {
            visitSequence(node.patterns);
            return null;
        }

        @Override
        public Void visit(PatternTy.MatchSingleton node) {
            // Nothing to do here.
            return null;
        }

        @Override
        public Void visit(PatternTy.MatchStar node) {
            if (node.name != null) {
                addDef(node.name, DefUse.DefLocal, node);
            }
            return null;
        }

        @Override
        public Void visit(PatternTy.MatchValue node) {
            node.value.accept(this);
            return null;
        }

        @Override
        public Void visit(StmtTy.Nonlocal node) {
            for (String n : node.names) {
                String mangled = mangle(n);
                EnumSet<DefUse> cur = currentScope.getUseOfName(n);
                if (cur != null) {
                    String msg = null;
                    if (cur.contains(DefUse.DefParam)) {
                        msg = NONLOCAL_PARAM;
                    } else if (cur.contains(DefUse.Use)) {
                        msg = NONLOCAL_AFTER_USE;
                    } else if (cur.contains(DefUse.DefAnnot)) {
                        msg = NONLOCAL_ANNOT;
                    } else if (cur.contains(DefUse.DefLocal)) {
                        msg = NONLOCAL_AFTER_ASSIGN;
                    }
                    if (msg != null) {
                        env.errorCallback.onError(ErrorCallback.ErrorType.Syntax, node.getSourceRange(), msg, n);
                        continue;
                    }
                }
                addDef(n, DefUse.DefNonLocal, node);
                currentScope.recordDirective(mangled, node.getSourceRange());
            }
            return null;
        }

        @Override
        public Void visit(StmtTy.Raise node) {
            if (node.exc != null) {
                node.exc.accept(this);
                if (node.cause != null) {
                    node.cause.accept(this);
                }
            }
            return null;
        }

        @Override
        public Void visit(StmtTy.Return node) {
            if (node.value != null) {
                node.value.accept(this);
            }
            return null;
        }

        @Override
        public Void visit(StmtTy.Try node) {
            visitSequence(node.body);
            visitSequence(node.orElse);
            visitSequence(node.handlers);
            visitSequence(node.finalBody);
            return null;
        }

        @Override
        public Void visit(StmtTy.TryStar node) {
            visitSequence(node.body);
            visitSequence(node.orElse);
            visitSequence(node.handlers);
            visitSequence(node.finalBody);
            return null;
        }

        @Override
        public Void visit(ExceptHandlerTy.ExceptHandler node) {
            if (node.type != null) {
                node.type.accept(this);
            }
            if (node.name != null) {
                addDef(node.name, DefUse.DefLocal, node);
            }
            visitSequence(node.body);
            return null;
        }

        @Override
        public Void visit(StmtTy.While node) {
            node.test.accept(this);
            visitSequence(node.body);
            visitSequence(node.orElse);
            return null;
        }

        @Override
        public Void visit(StmtTy.With node) {
            visitSequence(node.items);
            visitSequence(node.body);
            return null;
        }

        @Override
        public Void visit(WithItemTy node) {
            node.contextExpr.accept(this);
            if (node.optionalVars != null) {
                node.optionalVars.accept(this);
            }
            return null;
        }

        @Override
        public Void visit(ComprehensionTy node) {
            currentScope.flags.add(ScopeFlags.IsVisitingIterTarget);
            node.target.accept(this);
            currentScope.flags.remove(ScopeFlags.IsVisitingIterTarget);
            currentScope.comprehensionIterExpression++;
            node.iter.accept(this);
            currentScope.comprehensionIterExpression--;
            visitSequence(node.ifs);
            if (node.isAsync) {
                currentScope.flags.add(ScopeFlags.IsCoroutine);
            }
            return null;
        }

        @Override
        public Void visit(StmtTy.Break aThis) {
            return null;
        }

        @Override
        public Void visit(StmtTy.Continue aThis) {
            return null;
        }

        @Override
        public Void visit(StmtTy.Pass aThis) {
            return null;
        }
    }
}
