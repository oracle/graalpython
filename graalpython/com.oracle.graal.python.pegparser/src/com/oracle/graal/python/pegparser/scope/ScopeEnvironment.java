/*
 * Copyright (c) 2021, 2025, Oracle and/or its affiliates. All rights reserved.
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

import java.util.ArrayDeque;
import java.util.Collections;
import java.util.Deque;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map.Entry;

import com.oracle.graal.python.pegparser.FutureFeature;
import com.oracle.graal.python.pegparser.ParserCallbacks;
import com.oracle.graal.python.pegparser.ParserCallbacks.ErrorType;
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
import com.oracle.graal.python.pegparser.sst.StmtTy.TypeAlias;
import com.oracle.graal.python.pegparser.sst.TypeIgnoreTy;
import com.oracle.graal.python.pegparser.sst.TypeParamTy;
import com.oracle.graal.python.pegparser.sst.TypeParamTy.ParamSpec;
import com.oracle.graal.python.pegparser.sst.TypeParamTy.TypeVar;
import com.oracle.graal.python.pegparser.sst.TypeParamTy.TypeVarTuple;
import com.oracle.graal.python.pegparser.sst.WithItemTy;
import com.oracle.graal.python.pegparser.tokenizer.SourceRange;

/**
 * Roughly plays the role of CPython's {@code symtable}.
 *
 * Just like in CPython, the scope analysis uses two passes. The first simply visits everything and
 * creates {@link Scope} objects with some facts about the names. The second pass determines based
 * on the scope nestings which names are free, cells etc.
 */
public class ScopeEnvironment {
    // error strings used for warnings
    private static final String GLOBAL_PARAM = "name '%s' is parameter and global";
    private static final String NONLOCAL_PARAM = "name '%s' is parameter and nonlocal";
    private static final String GLOBAL_AFTER_ASSIGN = "name '%s' is assigned to before global declaration";
    private static final String NONLOCAL_AFTER_ASSIGN = "name '%s' is assigned to before nonlocal declaration";
    private static final String GLOBAL_AFTER_USE = "name '%s' is used prior to global declaration";
    private static final String NONLOCAL_AFTER_USE = "name '%s' is used prior to nonlocal declaration";
    private static final String GLOBAL_ANNOT = "annotated name '%s' can't be global";
    private static final String NONLOCAL_ANNOT = "annotated name '%s' can't be nonlocal";
    private static final String IMPORT_STAR_WARNING = "import * only allowed at module level";
    private static final String NAMED_EXPR_COMP_IN_CLASS = "assignment expression within a comprehension cannot be used in a class body";
    private static final String NAMED_EXPR_COMP_IN_TYPEPARAM = "assignment expression within a comprehension cannot be used within the definition of a generic";
    private static final String NAMED_EXPR_COMP_IN_TYPEALIAS = "assignment expression within a comprehension cannot be used in a type alias";
    private static final String NAMED_EXPR_COMP_IN_TYPEVAR_BOUND = "assignment expression within a comprehension cannot be used in a TypeVar bound";
    private static final String NAMED_EXPR_COMP_CONFLICT = "assignment expression cannot rebind comprehension iteration variable '%s'";
    private static final String NAMED_EXPR_COMP_INNER_LOOP_CONFLICT = "comprehension inner loop cannot rebind assignment expression target '%s'";
    private static final String NAMED_EXPR_COMP_ITER_EXPR = "assignment expression cannot be used in a comprehension iterable expression";
    private static final String DUPLICATE_ARGUMENT = "duplicate argument '%s' in function definition";
    private static final String DUPLICATE_TYPE_PARAM = "duplicate type parameter '%s'";

    final Scope topScope;
    // Keys to the `blocks` map are either SSTNode objects or TypeParamTy[] in case of functions
    // synthesized for implementation of Type Parameter Syntax.
    final HashMap<Object, Scope> blocks = new HashMap<>();
    final ParserCallbacks parserCallbacks;
    final EnumSet<FutureFeature> futureFeatures;

    public static ScopeEnvironment analyze(ModTy moduleNode, ParserCallbacks parserCallbacks, EnumSet<FutureFeature> futureFeatures) {
        return new ScopeEnvironment(moduleNode, parserCallbacks, futureFeatures);
    }

    private ScopeEnvironment(ModTy moduleNode, ParserCallbacks parserCallbacks, EnumSet<FutureFeature> futureFeatures) {
        // First pass, similar to the entry point `symtable_enter_block' on CPython
        this.parserCallbacks = parserCallbacks;
        this.futureFeatures = futureFeatures;
        FirstPassVisitor visitor = new FirstPassVisitor(moduleNode, this);
        topScope = visitor.currentScope;
        moduleNode.accept(visitor);

        // Second pass
        analyzeBlock(topScope, null, null, null, null, null);
    }

    @Override
    public String toString() {
        return "ScopeEnvironment\n" + topScope.toString(1);
    }

    private void addScope(Object key, Scope scope) {
        assert key instanceof SSTNode || key instanceof TypeParamTy[];
        blocks.put(key, scope);
    }

    public Scope lookupScope(Object key) {
        assert key instanceof SSTNode || key instanceof TypeParamTy[];
        return blocks.get(key);
    }

    private void analyzeBlock(Scope scope, HashSet<String> bound, HashSet<String> free, HashSet<String> global, HashSet<String> typeParams, Scope classEntry) {
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
            analyzeName(scope, scopes, e.getKey(), e.getValue(), bound, local, free, global, typeParams, classEntry);
        }

        if (scope.type != ScopeType.Class) {
            if (scope.type.isFunctionLike()) {
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
            newBound.add("__classdict__");
        }

        HashSet<String> allFree = new HashSet<>();
        for (Scope s : scope.children) {
            Scope newClassEntry = null;
            if (s.canSeeClassScope()) {
                if (scope.type == ScopeType.Class) {
                    newClassEntry = scope;
                } else if (classEntry != null) {
                    newClassEntry = classEntry;
                }
            }
            HashSet<String> tempBound = new HashSet<>(newBound);
            HashSet<String> tempFree = new HashSet<>(newFree);
            HashSet<String> tempGlobal = new HashSet<>(newGlobal);
            HashSet<String> tempTypeParams = new HashSet<>();
            if (typeParams != null) {
                tempTypeParams.addAll(typeParams);
            }
            analyzeBlock(s, tempBound, tempFree, tempGlobal, tempTypeParams, newClassEntry);
            allFree.addAll(tempFree);
            if (s.flags.contains(ScopeFlags.HasFreeVars) || s.flags.contains(ScopeFlags.HasChildWithFreeVars)) {
                scope.flags.add(ScopeFlags.HasChildWithFreeVars);
            }
        }

        newFree.addAll(allFree);

        if (scope.type.isFunctionLike()) {
            analyzeCells(scopes, newFree);
        } else if (scope.type == ScopeType.Class) {
            dropClassFree(scope, newFree);
        }

        updateSymbols(scope.symbols, scopes, bound, newFree, scope.type == ScopeType.Class || scope.canSeeClassScope());

        if (free != null) {
            free.addAll(newFree);
        }
    }

    private void analyzeName(Scope scope, HashMap<String, DefUse> scopes, String name, EnumSet<DefUse> flags, HashSet<String> bound, HashSet<String> local, HashSet<String> free,
                    HashSet<String> global, HashSet<String> typeParams, Scope classEntry) {
        if (flags.contains(DefUse.DefGlobal)) {
            if (flags.contains(DefUse.DefNonLocal)) {
                throw parserCallbacks.onError(ErrorType.Syntax, scope.getDirective(name), "name '%s' is nonlocal and global", name);
            }
            scopes.put(name, DefUse.GlobalExplicit);
            if (global != null) {
                global.add(name);
            }
            if (bound != null) {
                bound.remove(name);
            }
            return;
        }
        if (flags.contains(DefUse.DefNonLocal)) {
            if (bound == null) {
                throw parserCallbacks.onError(ParserCallbacks.ErrorType.Syntax, scope.getDirective(name), "nonlocal declaration not allowed at module level");
            } else if (!bound.contains(name)) {
                throw parserCallbacks.onError(ErrorType.Syntax, scope.getDirective(name), "no binding for nonlocal '%s' found", name);
            } else if (typeParams != null && typeParams.contains(name)) {
                throw parserCallbacks.onError(ErrorType.Syntax, scope.getDirective(name), "nonlocal binding not allowed for type parameter '%s'", name);
            }
            scopes.put(name, DefUse.Free);
            scope.flags.add(ScopeFlags.HasFreeVars);
            if (free != null) {
                // free is null in the module scope in which case we already reported an error above
                free.add(name);
            }
            return;
        }
        if (!Collections.disjoint(flags, DefUse.DefBound)) {
            scopes.put(name, DefUse.Local);
            local.add(name);
            if (global != null) {
                global.remove(name);
            }
            if (flags.contains(DefUse.DefTypeParam)) {
                // typeParams is null in the module scope, but in that scope it is not possible to
                // declare a type parameter
                assert typeParams != null;
                typeParams.add(name);
            } else if (typeParams != null) {
                typeParams.remove(name);
            }
            return;
        }
        // If we were passed classEntry (i.e., we're in an CanSeeClassScope scope)
        // and the bound name is in that set, then the name is potentially bound both by
        // the immediately enclosing class namespace, and also by an outer function namespace.
        // In that case, we want the runtime name resolution to look at only the class
        // namespace and the globals (not the namespace providing the bound).
        // Similarly, if the name is explicitly global in the class namespace (through the
        // global statement), we want to also treat it as a global in this scope.
        if (classEntry != null) {
            EnumSet<DefUse> classFlags = classEntry.getUseOfName(name);
            if (classFlags.contains(DefUse.DefGlobal)) {
                scopes.put(name, DefUse.GlobalExplicit);
                return;
            } else if (!Collections.disjoint(classFlags, DefUse.DefBound) && !classFlags.contains(DefUse.DefNonLocal)) {
                scopes.put(name, DefUse.GlobalImplicit);
                return;
            }
        }

        if (bound != null && bound.contains(name)) {
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
        if (free.remove("__classdict__")) {
            scope.flags.add(ScopeFlags.NeedsClassDict);
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
                if (isClass) {
                    v.add(DefUse.DefFreeClass);
                }
            } else if (bound == null || bound.contains(name)) {
                symbols.put(name, EnumSet.of(DefUse.Free));
            }
        }
    }

    public static String maybeMangle(String className, Scope scope, String name) {
        if (scope.mangledNames != null && !scope.mangledNames.contains(name)) {
            return name;
        }
        return mangle(className, name);
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
        private final Deque<Scope> stack;
        private final HashMap<String, EnumSet<DefUse>> globals;
        private final ScopeEnvironment env;
        private Scope currentScope;
        private String currentClassName;

        private FirstPassVisitor(ModTy moduleNode, ScopeEnvironment env) {
            this.stack = new ArrayDeque<>();
            this.env = env;
            enterBlock(null, Scope.ScopeType.Module, moduleNode);
            this.globals = this.currentScope.symbols;
        }

        private void enterBlock(String name, Scope.ScopeType type, SSTNode ast) {
            enterBlock(name, type, ast.getSourceRange(), ast);
        }

        private void enterBlock(String name, Scope.ScopeType type, SourceRange sourceRange, Object key) {
            Scope scope = new Scope(name, type, sourceRange);
            env.addScope(key, scope);
            stack.push(scope);
            Scope prev = currentScope;
            if (prev != null) {
                scope.comprehensionIterExpression = prev.comprehensionIterExpression;
                if (prev.type.isFunctionLike() || prev.isNested()) {
                    scope.flags.add(ScopeFlags.IsNested);
                }
            }
            currentScope = scope;
            if (type == Scope.ScopeType.Annotation) {
                return;
            }
            if (prev != null) {
                prev.children.add(scope);
            }
        }

        private void exitBlock() {
            stack.pop();
            currentScope = stack.peek();
        }

        private String maybeMangle(String name) {
            return ScopeEnvironment.maybeMangle(currentClassName, currentScope, name);
        }

        private void addDef(String name, DefUse flag, SSTNode node) {
            addDef(name, EnumSet.of(flag), node);
        }

        private void addDef(String name, EnumSet<DefUse> flags, SSTNode node) {
            if (flags.contains(DefUse.DefTypeParam) && currentScope.mangledNames != null) {
                currentScope.mangledNames.add(name);
            }
            addDefHelper(name, flags, currentScope, node);
        }

        private void addDef(String name, DefUse flag, Scope scope, SSTNode node) {
            addDefHelper(name, EnumSet.of(flag), scope, node);
        }

        private void addDefHelper(String name, EnumSet<DefUse> newFlags, Scope scope, SSTNode node) {
            String mangled = maybeMangle(name);
            EnumSet<DefUse> flags = scope.getUseOfName(mangled);
            if (newFlags.contains(DefUse.DefParam) && flags.contains(DefUse.DefParam)) {
                throw env.parserCallbacks.onError(ParserCallbacks.ErrorType.Syntax, node.getSourceRange(), DUPLICATE_ARGUMENT, name);
            }
            if (newFlags.contains(DefUse.DefTypeParam) && flags.contains(DefUse.DefTypeParam)) {
                throw env.parserCallbacks.onError(ParserCallbacks.ErrorType.Syntax, node.getSourceRange(), DUPLICATE_TYPE_PARAM, name);
            }
            flags.addAll(newFlags);
            if (scope.flags.contains(ScopeFlags.IsVisitingIterTarget)) {
                if (flags.contains(DefUse.DefGlobal) || flags.contains(DefUse.DefNonLocal)) {
                    throw env.parserCallbacks.onError(ParserCallbacks.ErrorType.Syntax, node.getSourceRange(), NAMED_EXPR_COMP_INNER_LOOP_CONFLICT, mangled);
                }
                flags.add(DefUse.DefCompIter);
            }
            scope.symbols.put(mangled, flags);
            if (newFlags.contains(DefUse.DefParam)) {
                if (scope.varnames.contains(mangled)) {
                    throw env.parserCallbacks.onError(ParserCallbacks.ErrorType.Syntax, node.getSourceRange(), "duplicate argument '%s' in function definition", mangled);
                }
                scope.varnames.add(mangled);
            } else if (newFlags.contains(DefUse.DefGlobal)) {
                EnumSet<DefUse> globalFlags = globals.get(mangled);
                if (globalFlags != null) {
                    globalFlags.addAll(newFlags);
                } else {
                    globalFlags = newFlags;
                }
                globals.put(mangled, globalFlags);
            }
        }

        private void enterTypeParamBlock(String name, boolean hasDefaults, boolean hasKwDefaults, SSTNode ast, TypeParamTy[] key) {
            ScopeType currentType = currentScope.type;
            enterBlock(name, ScopeType.TypeParam, ast.getSourceRange(), key);
            if (currentType == ScopeType.Class) {
                currentScope.flags.add(ScopeFlags.CanSeeClassScope);
                addDef("__classdict__", DefUse.Use, ast);
            }
            if (ast instanceof StmtTy.ClassDef) {
                addDef(".type_params", DefUse.DefLocal, ast);
                addDef(".type_params", DefUse.Use, ast);
                addDef(".generic_base", DefUse.DefLocal, ast);
                addDef(".generic_base", DefUse.Use, ast);
            }
            if (hasDefaults) {
                addDef(".defaults", DefUse.DefParam, ast);
            }
            if (hasKwDefaults) {
                addDef(".kwdefaults", DefUse.DefParam, ast);
            }
        }

        private static boolean hasKwOnlyDefaults(ArgTy[] kwOnlyArgs, ExprTy[] kwDefaults) {
            for (int i = 0; i < kwOnlyArgs.length; i++) {
                if (kwDefaults[i] != null) {
                    return true;
                }
            }
            return false;
        }

        private void handleComprehension(ExprTy e, String scopeName, ComprehensionTy[] generators, ExprTy element, ExprTy value, Scope.ComprehensionType comprehensionType) {
            if (currentScope.canSeeClassScope()) {
                throw env.parserCallbacks.onError(ParserCallbacks.ErrorType.Syntax, e.getSourceRange(), "Cannot use comprehension in annotation scope within class scope");
            }
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

        private RuntimeException raiseIfComprehensionBlock(ExprTy node) {
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
            throw env.parserCallbacks.onError(ParserCallbacks.ErrorType.Syntax, node.getSourceRange(), msg);
        }

        private void raiseIfAnnotationBlock(String name, ExprTy node) {
            switch (currentScope.type) {
                case Annotation:
                    throw env.parserCallbacks.onError(ErrorType.Syntax, node.getSourceRange(), "'%s' can not be used within an annotation", name);
                case TypeVarBound:
                    throw env.parserCallbacks.onError(ErrorType.Syntax, node.getSourceRange(), "'%s' cannot be used within a TypeVar bound", name);
                case TypeAlias:
                    throw env.parserCallbacks.onError(ErrorType.Syntax, node.getSourceRange(), "'%s' cannot be used within a type alias", name);
                case TypeParam:
                    throw env.parserCallbacks.onError(ErrorType.Syntax, node.getSourceRange(), "'%s' cannot be used within the definition of a generic", name);
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
                    throw env.parserCallbacks.onError(ErrorType.Syntax, node.getSourceRange(), IMPORT_STAR_WARNING);
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
            if (node.context == ExprContextTy.Load && currentScope.type.isFunctionLike() &&
                            node.id.equals("super")) {
                addDef("__class__", DefUse.Use, node);
            }
            return null;
        }

        @Override
        public Void visit(ExprTy.NamedExpr node) {
            raiseIfAnnotationBlock("named expression", node);
            if (currentScope.comprehensionIterExpression > 0) {
                throw env.parserCallbacks.onError(ParserCallbacks.ErrorType.Syntax, node.getSourceRange(), NAMED_EXPR_COMP_ITER_EXPR);
            }
            if (currentScope.flags.contains(ScopeFlags.IsComprehension)) {
                // symtable_extend_namedexpr_scope
                String targetName = ((ExprTy.Name) node.target).id;
                for (Scope s : stack) {
                    // If we find a comprehension scope, check for conflict
                    if (s.flags.contains(ScopeFlags.IsComprehension)) {
                        if (s.getUseOfName(maybeMangle(targetName)).contains(DefUse.DefCompIter)) {
                            throw env.parserCallbacks.onError(ParserCallbacks.ErrorType.Syntax, node.getSourceRange(), NAMED_EXPR_COMP_CONFLICT);
                        }
                        continue;
                    }
                    // If we find a FunctionBlock entry, add as GLOBAL/LOCAL or NONLOCAL/LOCAL
                    if (s.type == ScopeType.Function) {
                        EnumSet<DefUse> uses = s.getUseOfName(maybeMangle(targetName));
                        if (uses.contains(DefUse.DefGlobal)) {
                            addDef(targetName, DefUse.DefGlobal, node);
                        } else {
                            addDef(targetName, DefUse.DefNonLocal, node);
                        }
                        currentScope.recordDirective(maybeMangle(targetName), node.getSourceRange());
                        addDef(targetName, DefUse.DefLocal, s, node);
                        break;
                    }
                    // If we find a ModuleBlock entry, add as GLOBAL
                    if (s.type == ScopeType.Module) {
                        addDef(targetName, DefUse.DefGlobal, node);
                        currentScope.recordDirective(maybeMangle(targetName), node.getSourceRange());
                        addDef(targetName, DefUse.DefGlobal, s, node);
                        break;
                    }
                    // Disallow usage in ClassBlock and type scopes
                    if (s.type == ScopeType.Class) {
                        throw env.parserCallbacks.onError(ParserCallbacks.ErrorType.Syntax, node.getSourceRange(), NAMED_EXPR_COMP_IN_CLASS);
                    }
                    if (s.type == ScopeType.TypeParam) {
                        throw env.parserCallbacks.onError(ParserCallbacks.ErrorType.Syntax, node.getSourceRange(), NAMED_EXPR_COMP_IN_TYPEPARAM);
                    }
                    if (s.type == ScopeType.TypeAlias) {
                        throw env.parserCallbacks.onError(ParserCallbacks.ErrorType.Syntax, node.getSourceRange(), NAMED_EXPR_COMP_IN_TYPEALIAS);
                    }
                    if (s.type == ScopeType.TypeVarBound) {
                        throw env.parserCallbacks.onError(ParserCallbacks.ErrorType.Syntax, node.getSourceRange(), NAMED_EXPR_COMP_IN_TYPEVAR_BOUND);
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
                throw raiseIfComprehensionBlock(node);
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
                throw raiseIfComprehensionBlock(node);
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
                EnumSet<DefUse> cur = currentScope.getUseOfName(maybeMangle(name.id));
                if (cur != null && (cur.contains(DefUse.DefGlobal) || cur.contains(DefUse.DefNonLocal)) &&
                                currentScope.symbols != globals &&
                                node.isSimple) {
                    String msg = cur.contains(DefUse.DefGlobal) ? "annotated name '%s' can't be global" : "annotated name '%s' can't be nonlocal";
                    throw env.parserCallbacks.onError(ErrorType.Syntax, node.getSourceRange(), msg, name.id);
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
            visitFunctionDef(node.name, node.args, node.body, node.decoratorList, node.returns, node.typeParams, true, node);
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
            visitSequence(node.decoratorList);
            String tmp = currentClassName;
            boolean isGeneric = node.typeParams != null && node.typeParams.length > 0;
            if (isGeneric) {
                enterTypeParamBlock(node.name, false, false, node, node.typeParams);
            }
            try {
                if (isGeneric) {
                    currentClassName = node.name;
                    currentScope.mangledNames = new HashSet<>();
                    visitSequence(node.typeParams);
                }
                visitSequence(node.bases);
                visitSequence(node.keywords);
                enterBlock(node.name, ScopeType.Class, node);
                try {
                    currentClassName = node.name;
                    if (isGeneric) {
                        addDef("__type_params__", DefUse.DefLocal, node);
                        addDef(".type_params", DefUse.Use, node);
                    }
                    visitSequence(node.body);
                } finally {
                    exitBlock();
                }
            } finally {
                if (isGeneric) {
                    exitBlock();
                }
                currentClassName = tmp;
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
            visitFunctionDef(node.name, node.args, node.body, node.decoratorList, node.returns, node.typeParams, false, node);
            return null;
        }

        private void visitFunctionDef(String name, ArgumentsTy args, StmtTy[] body, ExprTy[] decoratorList, ExprTy returns, TypeParamTy[] typeParams, boolean isAsync, StmtTy node) {
            addDef(name, DefUse.DefLocal, node);
            visitSequence(args.defaults);
            visitSequence(args.kwDefaults);
            visitSequence(decoratorList);
            if (typeParams != null) {
                enterTypeParamBlock(
                                name,
                                args.defaults.length > 0,
                                hasKwOnlyDefaults(args.kwOnlyArgs, args.kwDefaults),
                                node, typeParams);
            }
            try {
                if (typeParams != null) {
                    visitSequence(typeParams);
                }
                visitAnnotations(node, args, returns);
                enterBlock(name, ScopeType.Function, node);
                try {
                    if (isAsync) {
                        currentScope.flags.add(ScopeFlags.IsCoroutine);
                    }
                    args.accept(this);
                    visitSequence(body);
                } finally {
                    exitBlock();
                }
            } finally {
                if (typeParams != null) {
                    exitBlock();
                }
            }
        }

        @Override
        public Void visit(StmtTy.Global node) {
            for (String n : node.names) {
                String mangled = maybeMangle(n);
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
                        throw env.parserCallbacks.onError(ParserCallbacks.ErrorType.Syntax, node.getSourceRange(), msg, n);
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
                String mangled = maybeMangle(n);
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
                        throw env.parserCallbacks.onError(ParserCallbacks.ErrorType.Syntax, node.getSourceRange(), msg, n);
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

        @Override
        public Void visit(TypeAlias node) {
            node.name.accept(this);
            String name = ((ExprTy.Name) node.name).id;
            boolean isInClass = currentScope.type == ScopeType.Class;
            boolean isGeneric = node.typeParams != null && node.typeParams.length > 0;
            if (isGeneric) {
                enterTypeParamBlock(name, false, false, node, node.typeParams);
            }
            try {
                if (isGeneric) {
                    visitSequence(node.typeParams);
                }
                enterBlock(name, ScopeType.TypeAlias, node);
                try {
                    if (isInClass) {
                        currentScope.flags.add(ScopeFlags.CanSeeClassScope);
                        addDef("__classdict__", DefUse.Use, node.value);
                    }
                    node.value.accept(this);
                } finally {
                    exitBlock();
                }
            } finally {
                if (isGeneric) {
                    exitBlock();
                }
            }
            return null;
        }

        @Override
        public Void visit(TypeVar node) {
            addDef(node.name, EnumSet.of(DefUse.DefTypeParam, DefUse.DefLocal), node);
            if (node.bound != null) {
                boolean isInClass = currentScope.canSeeClassScope();
                enterBlock(node.name, ScopeType.TypeVarBound, node);
                try {
                    if (isInClass) {
                        currentScope.flags.add(ScopeFlags.CanSeeClassScope);
                        addDef("__classdict__", DefUse.Use, node.bound);
                    }
                    node.bound.accept(this);
                } finally {
                    exitBlock();
                }
            }
            return null;
        }

        @Override
        public Void visit(ParamSpec node) {
            addDef(node.name, EnumSet.of(DefUse.DefTypeParam, DefUse.DefLocal), node);
            return null;
        }

        @Override
        public Void visit(TypeVarTuple node) {
            addDef(node.name, EnumSet.of(DefUse.DefTypeParam, DefUse.DefLocal), node);
            return null;
        }
    }
}
