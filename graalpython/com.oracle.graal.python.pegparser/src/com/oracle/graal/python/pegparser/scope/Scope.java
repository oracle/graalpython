/*
 * Copyright (c) 2021, 2022, Oracle and/or its affiliates. All rights reserved.
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

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;

import com.oracle.graal.python.pegparser.sst.SSTNode;
import com.oracle.graal.python.pegparser.tokenizer.SourceRange;

/**
 * Roughly equivalent to CPython's {@code symtable_entry}.
 */
public class Scope {

    Scope(String name, ScopeType type, SSTNode ast) {
        this.name = name;
        this.type = type;
        this.sourceRange = ast.getSourceRange();
    }

    enum ScopeType {
        Function,
        Class,
        Module,
        Annotation
    }

    public enum DefUse {
        DefGlobal,
        DefLocal,
        DefParam,
        DefNonLocal,
        Use,
        DefFree,
        DefFreeClass,
        DefImport,
        DefAnnot,
        DefCompIter,
        // shifted VariableScope flags
        Local,
        GlobalExplicit,
        GlobalImplicit,
        Free,
        Cell;

        static EnumSet<DefUse> DefBound = EnumSet.of(DefLocal, DefParam, DefImport);
    }

    HashMap<String, EnumSet<DefUse>> symbols = new HashMap<>();
    private List<String> sortedSymbols; // for lazy sorting

    String name;
    ArrayList<String> varnames = new ArrayList<>();
    ArrayList<Scope> children = new ArrayList<>();
    HashMap<String, SourceRange> directives = new HashMap<>();
    ScopeType type;

    enum ScopeFlags {
        IsNested,
        HasFreeVars,
        HasChildWithFreeVars,
        IsGenerator,
        IsCoroutine,
        IsComprehension,
        HasVarArgs,
        HasVarKeywords,
        ReturnsAValue,
        NeedsClassClosure,
        IsVisitingIterTarget
    }

    EnumSet<ScopeFlags> flags = EnumSet.noneOf(ScopeFlags.class);
    int comprehensionIterExpression = 0;

    enum ComprehensionType {
        NoComprehension,
        ListComprehension,
        DictComprehension,
        SetComprehension,
        GeneratorExpression
    }

    ComprehensionType comprehensionType = ComprehensionType.NoComprehension;

    SourceRange sourceRange;

    @Override
    public String toString() {
        return toString(0);
    }

    String toString(int indent) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < indent; i++) {
            sb.append("  ");
        }
        sb.append("Scope ").append(name).append(" ").append(type);
        if (!flags.isEmpty()) {
            sb.append('\n');
            for (int i = 0; i < indent; i++) {
                sb.append("    ");
            }
            sb.append("Flags: ").append(flags);
        }
        if (!varnames.isEmpty()) {
            sb.append('\n');
            for (int i = 0; i < indent; i++) {
                sb.append("    ");
            }
            sb.append("Varnames: ").append(varnames.get(0));
            for (int i = 1; i < varnames.size(); i++) {
                sb.append(", ").append(varnames.get(i));
            }
        }
        if (!symbols.isEmpty()) {
            sb.append('\n');
            for (int i = 0; i < indent; i++) {
                sb.append("    ");
            }
            sb.append("Symbols:");
            for (String k : getSortedSymbols()) {
                sb.append('\n');
                for (int i = 0; i < indent; i++) {
                    sb.append("      ");
                }
                sb.append(k).append(": ").append(symbols.get(k));
            }
        }
        for (Scope child : children) {
            sb.append("\n").append(child.toString(indent + 1));
        }
        return sb.toString();
    }

    void recordDirective(String directiveName, SourceRange directiveSourceRange) {
        directives.put(directiveName, directiveSourceRange);
    }

    SourceRange getDirective(String directiveName) {
        SourceRange range = directives.get(directiveName);
        assert range != null : "BUG: internal directive bookkeeping broken";
        return range;
    }

    public String getName() {
        return name;
    }

    public ArrayList<String> getVarnames() {
        return varnames;
    }

    private List<String> getSortedSymbols() {
        if (sortedSymbols == null) {
            sortedSymbols = new ArrayList<>(symbols.keySet());
            sortedSymbols.sort(String::compareTo);
        }
        return sortedSymbols;
    }

    public boolean needsClassClosure() {
        return flags.contains(ScopeFlags.NeedsClassClosure);
    }

    public boolean isFunction() {
        return type == ScopeType.Function;
    }

    public boolean isClass() {
        return type == ScopeType.Class;
    }

    public boolean isModule() {
        return type == ScopeType.Module;
    }

    public boolean isGenerator() {
        return flags.contains(ScopeFlags.IsGenerator);
    }

    public boolean isCoroutine() {
        return flags.contains(ScopeFlags.IsCoroutine);
    }

    public boolean isNested() {
        return flags.contains(ScopeFlags.IsNested);
    }

    public HashMap<String, Integer> getSymbolsByType(EnumSet<DefUse> expectedFlags, int start) {
        return getSymbolsByType(expectedFlags, EnumSet.noneOf(DefUse.class), start);
    }

    public HashMap<String, Integer> getSymbolsByType(EnumSet<DefUse> expectedFlags, EnumSet<DefUse> unexpectedFlags, int start) {
        int i = start;
        HashMap<String, Integer> mapping = new HashMap<>();
        for (String key : getSortedSymbols()) {
            EnumSet<DefUse> keyFlags = getUseOfName(key);
            if (!Collections.disjoint(expectedFlags, keyFlags) && Collections.disjoint(unexpectedFlags, keyFlags)) {
                mapping.put(key, i++);
            }
        }
        return mapping;
    }

    public EnumSet<DefUse> getUseOfName(String usedName) {
        return symbols.getOrDefault(usedName, EnumSet.noneOf(DefUse.class));
    }

    public ArrayList<Scope> getChildren() {
        return children;
    }
}
