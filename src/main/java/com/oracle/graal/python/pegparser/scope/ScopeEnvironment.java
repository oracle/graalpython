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
import com.oracle.graal.python.pegparser.sst.AndSSTNode;
import com.oracle.graal.python.pegparser.sst.AnnAssignmentSSTNode;
import com.oracle.graal.python.pegparser.sst.AnnotationSSTNode;
import com.oracle.graal.python.pegparser.sst.AssertSSTNode;
import com.oracle.graal.python.pegparser.sst.AssignmentSSTNode;
import com.oracle.graal.python.pegparser.sst.AugAssignmentSSTNode;
import com.oracle.graal.python.pegparser.sst.BinaryArithmeticSSTNode;
import com.oracle.graal.python.pegparser.sst.BlockSSTNode;
import com.oracle.graal.python.pegparser.sst.BooleanLiteralSSTNode;
import com.oracle.graal.python.pegparser.sst.CallSSTNode;
import com.oracle.graal.python.pegparser.sst.ClassSSTNode;
import com.oracle.graal.python.pegparser.sst.CollectionSSTNode;
import com.oracle.graal.python.pegparser.sst.ComparisonSSTNode;
import com.oracle.graal.python.pegparser.sst.ComprehensionSSTNode;
import com.oracle.graal.python.pegparser.sst.DecoratedSSTNode;
import com.oracle.graal.python.pegparser.sst.DecoratorSSTNode;
import com.oracle.graal.python.pegparser.sst.DelSSTNode;
import com.oracle.graal.python.pegparser.sst.ExceptSSTNode;
import com.oracle.graal.python.pegparser.sst.ExpressionStatementSSTNode;
import com.oracle.graal.python.pegparser.sst.FloatLiteralSSTNode;
import com.oracle.graal.python.pegparser.sst.ForComprehensionSSTNode;
import com.oracle.graal.python.pegparser.sst.ForSSTNode;
import com.oracle.graal.python.pegparser.sst.FunctionDefSSTNode;
import com.oracle.graal.python.pegparser.sst.GetAttributeSSTNode;
import com.oracle.graal.python.pegparser.sst.IfSSTNode;
import com.oracle.graal.python.pegparser.sst.ImportFromSSTNode;
import com.oracle.graal.python.pegparser.sst.ImportSSTNode;
import com.oracle.graal.python.pegparser.sst.KeyValueSSTNode;
import com.oracle.graal.python.pegparser.sst.LambdaSSTNode;
import com.oracle.graal.python.pegparser.sst.NotSSTNode;
import com.oracle.graal.python.pegparser.sst.NumberLiteralSSTNode;
import com.oracle.graal.python.pegparser.sst.OrSSTNode;
import com.oracle.graal.python.pegparser.sst.RaiseSSTNode;
import com.oracle.graal.python.pegparser.sst.ReturnSSTNode;
import com.oracle.graal.python.pegparser.sst.SSTNode;
import com.oracle.graal.python.pegparser.sst.SSTreeVisitor;
import com.oracle.graal.python.pegparser.sst.SimpleSSTNode;
import com.oracle.graal.python.pegparser.sst.SliceSSTNode;
import com.oracle.graal.python.pegparser.sst.StarSSTNode;
import com.oracle.graal.python.pegparser.sst.StringLiteralSSTNode;
import com.oracle.graal.python.pegparser.sst.SubscriptSSTNode;
import com.oracle.graal.python.pegparser.sst.TernaryIfSSTNode;
import com.oracle.graal.python.pegparser.sst.TrySSTNode;
import com.oracle.graal.python.pegparser.sst.UnarySSTNode;
import com.oracle.graal.python.pegparser.sst.VarLookupSSTNode;
import com.oracle.graal.python.pegparser.sst.WhileSSTNode;
import com.oracle.graal.python.pegparser.sst.WithSSTNode;
import com.oracle.graal.python.pegparser.sst.YieldExpressionSSTNode;
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

    // TODO: accept only a module sst node
    public ScopeEnvironment(BlockSSTNode moduleNode) {
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
        private Stack<Scope> stack;
        private final HashMap<String, EnumSet<DefUse>> globals;
        private Scope currentScope;
        private String currentClassName;

        private FirstPassVisitor(SSTNode moduleNode) {
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
        public Void visit(AndSSTNode node) {
            for (SSTNode n : node.getValues()) {
                n.accept(this);
            }
            return null;
        }

        @Override
        public Void visit(AnnAssignmentSSTNode node) {
            throw new UnsupportedOperationException("Not supported yet.");
        }

        @Override
        public Void visit(AnnotationSSTNode node) {
            throw new UnsupportedOperationException("Not supported yet.");
        }

        @Override
        public Void visit(AssertSSTNode node) {
            throw new UnsupportedOperationException("Not supported yet.");
        }

        @Override
        public Void visit(AssignmentSSTNode node) {
            throw new UnsupportedOperationException("Not supported yet.");
        }

        @Override
        public Void visit(AugAssignmentSSTNode node) {
            throw new UnsupportedOperationException("Not supported yet.");
        }

        @Override
        public Void visit(BinaryArithmeticSSTNode node) {
            throw new UnsupportedOperationException("Not supported yet.");
        }

        @Override
        public Void visit(BlockSSTNode node) {
            throw new UnsupportedOperationException("Not supported yet.");
        }

        @Override
        public Void visit(BooleanLiteralSSTNode node) {
            throw new UnsupportedOperationException("Not supported yet.");
        }

        @Override
        public Void visit(CallSSTNode node) {
            throw new UnsupportedOperationException("Not supported yet.");
        }

        @Override
        public Void visit(ClassSSTNode node) {
            addDef(node.getName(), DefUse.DefLocal);
            for (SSTNode n : node.getBaseClasses().getArgs()) {
                n.accept(this);
            }
            for (SSTNode n : node.getBaseClasses().getKwArg()) {
                n.accept(this);
            }
            String tmp = currentClassName;
            try {
                enterBlock(ScopeType.Class, node);
                currentClassName = node.getName();
                node.getBody().accept(this);
            } finally {
                currentClassName = tmp;
                exitBlock();
            }
            return null;
        }

        @Override
        public Void visit(CollectionSSTNode node) {
            throw new UnsupportedOperationException("Not supported yet.");
        }

        @Override
        public Void visit(ComparisonSSTNode node) {
            throw new UnsupportedOperationException("Not supported yet.");
        }

        @Override
        public Void visit(DecoratedSSTNode node) {
            throw new UnsupportedOperationException("Not supported yet.");
        }

        @Override
        public Void visit(DecoratorSSTNode node) {
            throw new UnsupportedOperationException("Not supported yet.");
        }

        @Override
        public Void visit(DelSSTNode node) {
            throw new UnsupportedOperationException("Not supported yet.");
        }

        @Override
        public Void visit(ExceptSSTNode node) {
            throw new UnsupportedOperationException("Not supported yet.");
        }

        @Override
        public Void visit(ExpressionStatementSSTNode node) {
            throw new UnsupportedOperationException("Not supported yet.");
        }

        @Override
        public Void visit(FloatLiteralSSTNode node) {
            throw new UnsupportedOperationException("Not supported yet.");
        }

        @Override
        public Void visit(ForComprehensionSSTNode node) {
            throw new UnsupportedOperationException("Not supported yet.");
        }

        @Override
        public Void visit(ComprehensionSSTNode node) {
            throw new UnsupportedOperationException("Not supported yet.");
        }

        @Override
        public Void visit(ForSSTNode node) {
            throw new UnsupportedOperationException("Not supported yet.");
        }

        @Override
        public Void visit(FunctionDefSSTNode node) {
            addDef(node.getName(), DefUse.DefLocal);
            SSTNode[] defaults = node.getArgBuilder().getDefaults();
            if (defaults != null) {
                for (SSTNode n : defaults) {
                    n.accept(this);
                }
            }
            SSTNode[] kwDefaults = node.getArgBuilder().getKwDefaults();
            if (kwDefaults != null) {
                for (SSTNode n : kwDefaults) {
                    n.accept(this);
                }
            }
            // TODO: visit annotations
            SSTNode[] decorators = node.getDecorators();
            if (decorators != null) {
                for (SSTNode n : decorators) {
                    n.accept(this);
                }
            }
            try {
                enterBlock(ScopeType.Function, node);
                for (String n : node.getArgBuilder().getParameterNames()) {
                    addDef(n, DefUse.DefParam);
                }
                if (node.getArgBuilder().hasSplat()) {
                    currentScope.flags.add(ScopeFlags.HasVarArgs);
                }
                if (node.getArgBuilder().hasKwSplat()) {
                    currentScope.flags.add(ScopeFlags.HasVarKeywords);
                }
                for (SSTNode n : node.getBody()) {
                    n.accept(this);
                }
            } finally {
                exitBlock();
            }
            return null;
        }

        @Override
        public Void visit(GetAttributeSSTNode node) {
            throw new UnsupportedOperationException("Not supported yet.");
        }

        @Override
        public Void visit(IfSSTNode node) {
            throw new UnsupportedOperationException("Not supported yet.");
        }

        @Override
        public Void visit(ImportFromSSTNode node) {
            throw new UnsupportedOperationException("Not supported yet.");
        }

        @Override
        public Void visit(ImportSSTNode node) {
            throw new UnsupportedOperationException("Not supported yet.");
        }

        @Override
        public Void visit(LambdaSSTNode node) {
            throw new UnsupportedOperationException("Not supported yet.");
        }

        @Override
        public Void visit(NotSSTNode node) {
            throw new UnsupportedOperationException("Not supported yet.");
        }

        @Override
        public Void visit(NumberLiteralSSTNode.IntegerLiteralSSTNode node) {
            throw new UnsupportedOperationException("Not supported yet.");
        }

        @Override
        public Void visit(NumberLiteralSSTNode.BigIntegerLiteralSSTNode node) {
            throw new UnsupportedOperationException("Not supported yet.");
        }

        @Override
        public Void visit(OrSSTNode node) {
            throw new UnsupportedOperationException("Not supported yet.");
        }

        @Override
        public Void visit(RaiseSSTNode node) {
            throw new UnsupportedOperationException("Not supported yet.");
        }

        @Override
        public Void visit(ReturnSSTNode node) {
            throw new UnsupportedOperationException("Not supported yet.");
        }

        @Override
        public Void visit(SimpleSSTNode node) {
            throw new UnsupportedOperationException("Not supported yet.");
        }

        @Override
        public Void visit(SliceSSTNode node) {
            throw new UnsupportedOperationException("Not supported yet.");
        }

        @Override
        public Void visit(StarSSTNode node) {
            throw new UnsupportedOperationException("Not supported yet.");
        }

        @Override
        public Void visit(StringLiteralSSTNode.RawStringLiteralSSTNode node) {
            throw new UnsupportedOperationException("Not supported yet.");
        }

        @Override
        public Void visit(StringLiteralSSTNode.BytesLiteralSSTNode node) {
            throw new UnsupportedOperationException("Not supported yet.");
        }

        @Override
        public Void visit(StringLiteralSSTNode.FormatExpressionSSTNode node) {
            throw new UnsupportedOperationException("Not supported yet.");
        }

        @Override
        public Void visit(StringLiteralSSTNode.FormatStringLiteralSSTNode node) {
            throw new UnsupportedOperationException("Not supported yet.");
        }

        @Override
        public Void visit(SubscriptSSTNode node) {
            throw new UnsupportedOperationException("Not supported yet.");
        }

        @Override
        public Void visit(TernaryIfSSTNode node) {
            throw new UnsupportedOperationException("Not supported yet.");
        }

        @Override
        public Void visit(TrySSTNode node) {
            throw new UnsupportedOperationException("Not supported yet.");
        }

        @Override
        public Void visit(UnarySSTNode node) {
            throw new UnsupportedOperationException("Not supported yet.");
        }

        @Override
        public Void visit(VarLookupSSTNode node) {
            throw new UnsupportedOperationException("Not supported yet.");
        }

        @Override
        public Void visit(WhileSSTNode node) {
            throw new UnsupportedOperationException("Not supported yet.");
        }

        @Override
        public Void visit(WithSSTNode node) {
            throw new UnsupportedOperationException("Not supported yet.");
        }

        @Override
        public Void visit(YieldExpressionSSTNode node) {
            throw new UnsupportedOperationException("Not supported yet.");
        }

        @Override
        public Void visit(KeyValueSSTNode node) {
            throw new UnsupportedOperationException("Not supported yet.");
        }
    }
}
