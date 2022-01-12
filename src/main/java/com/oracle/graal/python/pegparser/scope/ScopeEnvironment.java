/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
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

import com.oracle.graal.python.pegparser.scope.Scope.DefUse;
import com.oracle.graal.python.pegparser.scope.Scope.ScopeFlags;
import com.oracle.graal.python.pegparser.scope.Scope.ScopeType;
import com.oracle.graal.python.pegparser.sst.AliasTy;
import com.oracle.graal.python.pegparser.sst.ArgTy;
import com.oracle.graal.python.pegparser.sst.ArgumentsTy;
import com.oracle.graal.python.pegparser.sst.ComprehensionTy;
import com.oracle.graal.python.pegparser.sst.ExprTy;
import com.oracle.graal.python.pegparser.sst.KeywordTy;
import com.oracle.graal.python.pegparser.sst.ModTy;
import com.oracle.graal.python.pegparser.sst.SSTNode;
import com.oracle.graal.python.pegparser.sst.SSTreeVisitor;
import com.oracle.graal.python.pegparser.sst.StmtTy;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map.Entry;
import java.util.Stack;

/**
 * Roughly plays the role of CPython's {@code symtable}.
 *
 * Just like in CPython, the scope analysis uses two passes. The first simply visits everything and
 * creates {@link Scope} objects with some facts about the names. The second pass determines based
 * on the scope nestings which names are free, cells etc.
 */
public class ScopeEnvironment {
    final Scope topScope;
    final HashMap<SSTNode, Scope> blocks;

    public ScopeEnvironment(ModTy moduleNode) {
        blocks = new HashMap<>();
        // First pass, similar to the entry point `symtable_enter_block' on CPython
        FirstPassVisitor visitor = new FirstPassVisitor(moduleNode);
        topScope = visitor.currentScope;
        moduleNode.accept(visitor);

        // Second pass
        analyzeBlock(topScope, null, null, null);
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

    private void analyzeName(Scope scope, HashMap<String, DefUse> scopes, String name, EnumSet<DefUse> flags, HashSet<String> bound, HashSet<String> local, HashSet<String> free, HashSet<String> global) {
        if (flags.contains(DefUse.DefGlobal)) {
            if (flags.contains(DefUse.DefNonLocal)) {
                // TODO: SyntaxError:
                // "name '%s' is nonlocal and global", name
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
                // TODO: SyntaxError:
                // "nonlocal declaration not allowed at module level"
            } else if (!bound.contains(name)) {
                // TODO: SyntaxError:
                // "no binding for nonlocal '%s' found", name
            }
            scopes.put(name, DefUse.Free);
            scope.flags.add(ScopeFlags.HasFreeVars);
            free.add(name);
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

    private void analyzeCells(HashMap<String, DefUse> scopes, HashSet<String> free) {
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

    private void dropClassFree(Scope scope, HashSet<String> free) {
        if (free.remove("__class__")) {
            scope.flags.add(ScopeFlags.NeedsClassClosure);
        }
    }

    private void updateSymbols(HashMap<String, EnumSet<DefUse>> symbols, HashMap<String, DefUse> scopes, HashSet<String> bound, HashSet<String> free, boolean isClass) {
        for (Entry<String, EnumSet<DefUse>> e : symbols.entrySet()) {
            String name = e.getKey();
            DefUse vScope = scopes.get(name);
            assert vScope.toString().startsWith("V");
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

    private final class FirstPassVisitor implements SSTreeVisitor<Void> {
        private final Stack<Scope> stack;
        private final HashMap<String, EnumSet<DefUse>> globals;
        private Scope currentScope;
        private String currentClassName;

        private FirstPassVisitor(ModTy moduleNode) {
            this.stack = new Stack<>();
            enterBlock(Scope.ScopeType.Module, moduleNode);
            this.globals = this.currentScope.symbols;
        }

        private void enterBlock(Scope.ScopeType type, SSTNode ast) {
            Scope scope = new Scope(type, ast);
            stack.add(scope);
            if (type == Scope.ScopeType.Annotation) {
                return;
            }
            if (currentScope != null) {
                scope.comprehensionIterExpression = currentScope.comprehensionIterExpression;
                currentScope.children.add(scope);
            }
            currentScope = scope;
        }

        private void exitBlock() {
            stack.pop();
            currentScope = stack.peek();
        }

        private String mangle(String name) {
            if (currentClassName == null || !name.startsWith("__")) {
                return name;
            }
            if (name.endsWith("__") || name.contains(".")) {
                return name;
            }
            int offset = 0;
            while (currentClassName.charAt(offset) == '_') {
                offset++;
                if (offset >= currentClassName.length()) {
                    return name;
                }
            }
            return "_" + currentClassName.substring(offset) + name;
        }

        private void addDef(String name, DefUse flag) {
            String mangled = mangle(name);
            EnumSet<DefUse> flags = currentScope.symbols.get(mangled);
            if (flags != null) {
                if (flag == DefUse.DefParam && flags.contains(DefUse.DefParam)) {
                    // TODO: raises SyntaxError:
                    // "duplicate argument '%s' in function definition", name
                }
            } else {
                flags = EnumSet.of(flag);
            }
            if (currentScope.flags.contains(ScopeFlags.IsVisitingIterTarget)) {
                if (flags.contains(DefUse.DefGlobal) || flags.contains(DefUse.DefNonLocal)) {
                    // TODO: raises SyntaxError:
                    // "comprehension inner loop cannot rebind assignment expression target '%s'", name
                }
                flags.add(DefUse.DefCompIter);
            }
            currentScope.symbols.put(mangled, flags);
            switch (flag) {
                case DefParam:
                    currentScope.varnames.add(mangled);
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

        @Override
        public Void visit(AliasTy node) {
            throw new UnsupportedOperationException("Not supported yet.");
        }

        @Override
        public Void visit(ArgTy node) {
            addDef(node.arg, DefUse.DefParam);
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
            throw new UnsupportedOperationException("Not supported yet.");
        }

        @Override
        public Void visit(ExprTy.Await node) {
            throw new UnsupportedOperationException("Not supported yet.");
        }

        @Override
        public Void visit(ExprTy.BinOp node) {
            throw new UnsupportedOperationException("Not supported yet.");
        }

        @Override
        public Void visit(ExprTy.BoolOp node) {
            throw new UnsupportedOperationException("Not supported yet.");
        }

        @Override
        public Void visit(ExprTy.Call node) {
            throw new UnsupportedOperationException("Not supported yet.");
        }

        @Override
        public Void visit(ExprTy.Compare node) {
            throw new UnsupportedOperationException("Not supported yet.");
        }

        @Override
        public Void visit(ExprTy.Constant node) {
            throw new UnsupportedOperationException("Not supported yet.");
        }

        @Override
        public Void visit(ExprTy.Dict node) {
            throw new UnsupportedOperationException("Not supported yet.");
        }

        @Override
        public Void visit(ExprTy.DictComp node) {
            throw new UnsupportedOperationException("Not supported yet.");
        }

        @Override
        public Void visit(ExprTy.FormattedValue node) {
            throw new UnsupportedOperationException("Not supported yet.");
        }

        @Override
        public Void visit(ExprTy.GeneratorExp node) {
            throw new UnsupportedOperationException("Not supported yet.");
        }

        @Override
        public Void visit(ExprTy.IfExp node) {
            throw new UnsupportedOperationException("Not supported yet.");
        }

        @Override
        public Void visit(ExprTy.JoinedStr node) {
            throw new UnsupportedOperationException("Not supported yet.");
        }

        @Override
        public Void visit(ExprTy.Lambda node) {
            throw new UnsupportedOperationException("Not supported yet.");
        }

        @Override
        public Void visit(ExprTy.List node) {
            throw new UnsupportedOperationException("Not supported yet.");
        }

        @Override
        public Void visit(ExprTy.ListComp node) {
            throw new UnsupportedOperationException("Not supported yet.");
        }

        @Override
        public Void visit(ExprTy.Name node) {
            throw new UnsupportedOperationException("Not supported yet.");
        }

        @Override
        public Void visit(ExprTy.NamedExpr node) {
            throw new UnsupportedOperationException("Not supported yet.");
        }

        @Override
        public Void visit(ExprTy.Set node) {
            throw new UnsupportedOperationException("Not supported yet.");
        }

        @Override
        public Void visit(ExprTy.SetComp node) {
            throw new UnsupportedOperationException("Not supported yet.");
        }

        @Override
        public Void visit(ExprTy.Slice node) {
            throw new UnsupportedOperationException("Not supported yet.");
        }

        @Override
        public Void visit(ExprTy.Starred node) {
            throw new UnsupportedOperationException("Not supported yet.");
        }

        @Override
        public Void visit(ExprTy.Subscript node) {
            throw new UnsupportedOperationException("Not supported yet.");
        }

        @Override
        public Void visit(ExprTy.Tuple node) {
            throw new UnsupportedOperationException("Not supported yet.");
        }

        @Override
        public Void visit(ExprTy.UnaryOp node) {
            throw new UnsupportedOperationException("Not supported yet.");
        }

        @Override
        public Void visit(ExprTy.Yield node) {
            throw new UnsupportedOperationException("Not supported yet.");
        }

        @Override
        public Void visit(ExprTy.YieldFrom node) {
            throw new UnsupportedOperationException("Not supported yet.");
        }

        @Override
        public Void visit(KeywordTy node) {
            throw new UnsupportedOperationException("Not supported yet.");
        }

        @Override
        public Void visit(ModTy.Expression node) {
            throw new UnsupportedOperationException("Not supported yet.");
        }

        @Override
        public Void visit(ModTy.FunctionType node) {
            throw new UnsupportedOperationException("Not supported yet.");
        }

        @Override
        public Void visit(ModTy.Interactive node) {
            throw new UnsupportedOperationException("Not supported yet.");
        }

        @Override
        public Void visit(ModTy.Module node) {
            throw new UnsupportedOperationException("Not supported yet.");
        }

        @Override
        public Void visit(ModTy.TypeIgnore node) {
            throw new UnsupportedOperationException("Not supported yet.");
        }

        @Override
        public Void visit(StmtTy.AnnAssign node) {
            throw new UnsupportedOperationException("Not supported yet.");
        }

        @Override
        public Void visit(StmtTy.Assert node) {
            throw new UnsupportedOperationException("Not supported yet.");
        }

        @Override
        public Void visit(StmtTy.Assign node) {
            throw new UnsupportedOperationException("Not supported yet.");
        }

        @Override
        public Void visit(StmtTy.AsyncFor node) {
            throw new UnsupportedOperationException("Not supported yet.");
        }

        @Override
        public Void visit(StmtTy.AsyncFunctionDef node) {
            throw new UnsupportedOperationException("Not supported yet.");
        }

        @Override
        public Void visit(StmtTy.AsyncWith node) {
            throw new UnsupportedOperationException("Not supported yet.");
        }

        @Override
        public Void visit(StmtTy.AugAssign node) {
            throw new UnsupportedOperationException("Not supported yet.");
        }

        @Override
        public Void visit(StmtTy.ClassDef node) {
            addDef(node.name, DefUse.DefLocal);
            visitSequence(node.bases);
            visitSequence(node.keywords);
            visitSequence(node.decoratorList);
            String tmp = currentClassName;
            enterBlock(ScopeType.Class, node);
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
            throw new UnsupportedOperationException("Not supported yet.");
        }

        @Override
        public Void visit(StmtTy.Expr node) {
            throw new UnsupportedOperationException("Not supported yet.");
        }

        @Override
        public Void visit(StmtTy.For node) {
            throw new UnsupportedOperationException("Not supported yet.");
        }

        @Override
        public Void visit(StmtTy.FunctionDef node) {
            addDef(node.name, DefUse.DefLocal);
            visitSequence(node.args.defaults);
            visitSequence(node.args.kwDefaults);
            // TODO: visit annotations
            visitSequence(node.decoratorList);
            enterBlock(ScopeType.Function, node);
            try {
                node.args.accept(this);
                visitSequence(node.body);
            } finally {
                exitBlock();
            }
            return null;
        }

        @Override
        public Void visit(StmtTy.Global node) {
            throw new UnsupportedOperationException("Not supported yet.");
        }

        @Override
        public Void visit(StmtTy.If node) {
            throw new UnsupportedOperationException("Not supported yet.");
        }

        @Override
        public Void visit(StmtTy.Import node) {
            throw new UnsupportedOperationException("Not supported yet.");
        }

        @Override
        public Void visit(StmtTy.ImportFrom node) {
            throw new UnsupportedOperationException("Not supported yet.");
        }

        @Override
        public Void visit(StmtTy.Match node) {
            throw new UnsupportedOperationException("Not supported yet.");
        }

        @Override
        public Void visit(StmtTy.Match.Case node) {
            throw new UnsupportedOperationException("Not supported yet.");
        }

        @Override
        public Void visit(StmtTy.Match.Pattern.MatchAs node) {
            throw new UnsupportedOperationException("Not supported yet.");
        }

        @Override
        public Void visit(StmtTy.Match.Pattern.MatchClass node) {
            throw new UnsupportedOperationException("Not supported yet.");
        }

        @Override
        public Void visit(StmtTy.Match.Pattern.MatchMapping node) {
            throw new UnsupportedOperationException("Not supported yet.");
        }

        @Override
        public Void visit(StmtTy.Match.Pattern.MatchOr node) {
            throw new UnsupportedOperationException("Not supported yet.");
        }

        @Override
        public Void visit(StmtTy.Match.Pattern.MatchSequence node) {
            throw new UnsupportedOperationException("Not supported yet.");
        }

        @Override
        public Void visit(StmtTy.Match.Pattern.MatchSingleton node) {
            throw new UnsupportedOperationException("Not supported yet.");
        }

        @Override
        public Void visit(StmtTy.Match.Pattern.MatchStar node) {
            throw new UnsupportedOperationException("Not supported yet.");
        }

        @Override
        public Void visit(StmtTy.Match.Pattern.MatchValue node) {
            throw new UnsupportedOperationException("Not supported yet.");
        }

        @Override
        public Void visit(StmtTy.NonLocal node) {
            throw new UnsupportedOperationException("Not supported yet.");
        }

        @Override
        public Void visit(StmtTy.Raise node) {
            throw new UnsupportedOperationException("Not supported yet.");
        }

        @Override
        public Void visit(StmtTy.Return node) {
            throw new UnsupportedOperationException("Not supported yet.");
        }

        @Override
        public Void visit(StmtTy.Try node) {
            throw new UnsupportedOperationException("Not supported yet.");
        }

        @Override
        public Void visit(StmtTy.Try.ExceptHandler node) {
            throw new UnsupportedOperationException("Not supported yet.");
        }

        @Override
        public Void visit(StmtTy.While node) {
            throw new UnsupportedOperationException("Not supported yet.");
        }

        @Override
        public Void visit(StmtTy.With node) {
            throw new UnsupportedOperationException("Not supported yet.");
        }

        @Override
        public Void visit(StmtTy.With.Item node) {
            throw new UnsupportedOperationException("Not supported yet.");
        }

        @Override
        public Void visit(ComprehensionTy aThis) {
            throw new UnsupportedOperationException("Not supported yet.");
        }

        @Override
        public Void visit(StmtTy.Break aThis) {
            throw new UnsupportedOperationException("Not supported yet.");
        }

        @Override
        public Void visit(StmtTy.Continue aThis) {
            throw new UnsupportedOperationException("Not supported yet.");
        }

        @Override
        public Void visit(StmtTy.Pass aThis) {
            throw new UnsupportedOperationException("Not supported yet.");
        }
    }
}
