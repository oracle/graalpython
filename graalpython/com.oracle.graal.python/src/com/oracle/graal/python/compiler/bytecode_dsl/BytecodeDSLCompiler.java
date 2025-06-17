/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.compiler.bytecode_dsl;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.compiler.Compiler;
import com.oracle.graal.python.compiler.ParserCallbacksImpl;
import com.oracle.graal.python.nodes.bytecode_dsl.BytecodeDSLCodeUnit;
import com.oracle.graal.python.nodes.bytecode_dsl.PBytecodeDSLRootNode;
import com.oracle.graal.python.pegparser.FutureFeature;
import com.oracle.graal.python.pegparser.scope.Scope;
import com.oracle.graal.python.pegparser.scope.ScopeEnvironment;
import com.oracle.graal.python.pegparser.sst.ModTy;
import com.oracle.graal.python.pegparser.sst.StmtTy;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.truffle.api.source.Source;

public class BytecodeDSLCompiler {

    public static final record BytecodeDSLCompilerResult(PBytecodeDSLRootNode rootNode, BytecodeDSLCodeUnit codeUnit) {
    }

    public static BytecodeDSLCompilerResult compile(PythonLanguage language, PythonContext context, ModTy mod, Source source, int optimize, ParserCallbacksImpl parserCallbacks,
                    EnumSet<FutureFeature> futureFeatures) {
        /**
         * Parse __future__ annotations before the analysis step. The analysis does extra validation
         * when __future__.annotations is imported.
         */
        int futureLineNumber = parseFuture(mod, futureFeatures, parserCallbacks);
        ScopeEnvironment scopeEnvironment = ScopeEnvironment.analyze(mod, parserCallbacks, futureFeatures);
        BytecodeDSLCompilerContext ctx = new BytecodeDSLCompilerContext(language, context, mod, source, optimize, futureFeatures, futureLineNumber, parserCallbacks, scopeEnvironment);
        RootNodeCompiler compiler = new RootNodeCompiler(ctx, null, mod, futureFeatures);
        return compiler.compile();
    }

    private static int parseFuture(ModTy mod, EnumSet<FutureFeature> futureFeatures, ParserCallbacksImpl parserCallbacks) {
        StmtTy[] stmts = null;
        if (mod instanceof ModTy.Module module) {
            stmts = module.body;
        } else if (mod instanceof ModTy.Interactive interactive) {
            stmts = interactive.body;
        } else {
            return -1;
        }
        return Compiler.parseFuture(stmts, futureFeatures, parserCallbacks);
    }

    public static class BytecodeDSLCompilerContext {

        public final PythonLanguage language;
        public final PythonContext pythonContext;
        public final ModTy mod;
        public final Source source;
        public final int optimizationLevel;
        public final EnumSet<FutureFeature> futureFeatures;
        public final int futureLineNumber;
        public final ParserCallbacksImpl errorCallback;
        public final ScopeEnvironment scopeEnvironment;
        public final Map<Scope, String> qualifiedNames;

        public BytecodeDSLCompilerContext(PythonLanguage language, PythonContext context, ModTy mod, Source source, int optimizationLevel,
                        EnumSet<FutureFeature> futureFeatures, int futureLineNumber, ParserCallbacksImpl errorCallback, ScopeEnvironment scopeEnvironment) {
            this.language = language;
            this.pythonContext = context;
            this.mod = mod;
            this.source = source;
            this.optimizationLevel = optimizationLevel;
            this.futureFeatures = futureFeatures;
            this.futureLineNumber = futureLineNumber;
            this.errorCallback = errorCallback;
            this.scopeEnvironment = scopeEnvironment;
            this.qualifiedNames = new HashMap<>();
        }

        public String maybeMangle(String privateName, Scope scope, String name) {
            return ScopeEnvironment.maybeMangle(privateName, scope, name);
        }

        String mangle(Scope scope, String name) {
            return ScopeEnvironment.mangle(getClassName(scope), name);
        }

        String getClassName(Scope s) {
            Scope cur = s;
            while (cur != null) {
                if (cur.isClass()) {
                    return cur.getName();
                }
                cur = scopeEnvironment.lookupParent(cur);
            }
            return null;
        }

        String getQualifiedName(Scope scope) {
            if (qualifiedNames.containsKey(scope)) {
                return qualifiedNames.get(scope);
            } else {
                String qualifiedName = computeQualifiedName(scope);
                qualifiedNames.put(scope, qualifiedName);
                return qualifiedName;
            }
        }

        String getQualifiedName(String name, Scope scope) {
            if (qualifiedNames.containsKey(scope)) {
                return qualifiedNames.get(scope);
            } else {
                String qualifiedName = computeQualifiedName(name, scope);
                qualifiedNames.put(scope, qualifiedName);
                return qualifiedName;
            }
        }

        private String computeQualifiedName(Scope scope) {
            return computeQualifiedName(scope.getName(), scope);
        }

        private String computeQualifiedName(String qualifiedName, Scope scope) {
            Scope parentScope = scopeEnvironment.lookupParent(scope);
            if (parentScope != null && parentScope != scopeEnvironment.getTopScope()) {
                if (parentScope.isTypeParam()) {
                    parentScope = scopeEnvironment.lookupParent(parentScope);
                    if (parentScope == null || scopeEnvironment.lookupParent(parentScope) == null) {
                        return qualifiedName;
                    }
                }
                if (!((scope.isFunction() || scope.isClass()) && parentScope.getUseOfName(mangle(scope, qualifiedName)).contains(Scope.DefUse.GlobalExplicit))) {
                    // Qualify the name, unless it's a function/class and the parent declared the
                    // name as a global (in which case the function/class doesn't belong to the
                    // parent).
                    if (parentScope.isFunction()) {
                        qualifiedName = getQualifiedName(parentScope) + ".<locals>." + qualifiedName;
                    } else {
                        qualifiedName = getQualifiedName(parentScope) + "." + qualifiedName;
                    }
                }
            }

            return qualifiedName;
        }
    }
}
