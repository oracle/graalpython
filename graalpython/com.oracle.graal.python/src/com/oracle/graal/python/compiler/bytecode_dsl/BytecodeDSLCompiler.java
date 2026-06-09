/*
 * Copyright (c) 2025, 2026, Oracle and/or its affiliates. All rights reserved.
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

import static com.oracle.graal.python.util.PythonUtils.codePointsToTruffleString;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.compiler.ParserCallbacksImpl;
import com.oracle.graal.python.pegparser.AbstractParser;
import com.oracle.graal.python.nodes.bytecode_dsl.BytecodeDSLCodeUnit;
import com.oracle.graal.python.nodes.bytecode_dsl.PBytecodeDSLRootNode;
import com.oracle.graal.python.pegparser.FutureFeature;
import com.oracle.graal.python.pegparser.InputType;
import com.oracle.graal.python.pegparser.Parser;
import com.oracle.graal.python.pegparser.scope.Scope;
import com.oracle.graal.python.pegparser.scope.ScopeEnvironment;
import com.oracle.graal.python.pegparser.ParserCallbacks;
import com.oracle.graal.python.pegparser.ParserCallbacks.ErrorType;
import com.oracle.graal.python.pegparser.sst.AliasTy;
import com.oracle.graal.python.pegparser.sst.ConstantValue;
import com.oracle.graal.python.pegparser.sst.ExprTy;
import com.oracle.graal.python.pegparser.sst.ModTy;
import com.oracle.graal.python.pegparser.sst.StmtTy;
import com.oracle.truffle.api.source.Source;

public class BytecodeDSLCompiler {
    public static final int BYTECODE_VERSION = 32;

    public static final record BytecodeDSLCompilerResult(PBytecodeDSLRootNode rootNode, BytecodeDSLCodeUnit codeUnit) {
    }

    public static BytecodeDSLCompilerResult compile(PythonLanguage language, ModTy mod, Source source, int optimize, ParserCallbacksImpl parserCallbacks,
                    EnumSet<FutureFeature> futureFeatures) {
        /**
         * Parse __future__ annotations before the analysis step. The analysis does extra validation
         * when __future__.annotations is imported.
         */
        int futureLineNumber = parseFuture(mod, futureFeatures, parserCallbacks);
        ScopeEnvironment scopeEnvironment = ScopeEnvironment.analyze(mod, parserCallbacks, futureFeatures);
        BytecodeDSLCompilerContext ctx = new BytecodeDSLCompilerContext(language, mod, source, optimize, futureFeatures, futureLineNumber, parserCallbacks, scopeEnvironment);
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
        return parseFuture(stmts, futureFeatures, parserCallbacks);
    }

    private static int parseFuture(StmtTy[] modBody, EnumSet<FutureFeature> futureFeatures, ParserCallbacks parserCallbacks) {
        int lastFutureLine = -1;
        if (modBody == null || modBody.length == 0) {
            return lastFutureLine;
        }
        boolean done = false;
        int prevLine = 0;
        int i = 0;
        if (findDocstring(modBody) != null) {
            i++;
        }

        for (; i < modBody.length; i++) {
            StmtTy s = modBody[i];
            int line = s.getSourceRange().startLine;
            if (done && line > prevLine) {
                return lastFutureLine;
            }
            prevLine = line;
            if (s instanceof StmtTy.ImportFrom importFrom) {
                if ("__future__".equals(importFrom.module)) {
                    if (done) {
                        throw parserCallbacks.onError(ErrorType.Syntax, s.getSourceRange(), "from __future__ imports must occur at the beginning of the file");
                    }
                    parseFutureFeatures(importFrom, futureFeatures, parserCallbacks);
                    lastFutureLine = line;
                } else {
                    done = true;
                }
            } else {
                done = true;
            }
        }
        return lastFutureLine;
    }

    private static void parseFutureFeatures(StmtTy.ImportFrom node, EnumSet<FutureFeature> features, ParserCallbacks parserCallbacks) {
        for (AliasTy alias : node.names) {
            if (alias.name != null) {
                switch (alias.name) {
                    case "nested_scopes":
                    case "generators":
                    case "division":
                    case "absolute_import":
                    case "with_statement":
                    case "print_function":
                    case "unicode_literals":
                    case "generator_stop":
                        break;
                    case "barry_as_FLUFL":
                        features.add(FutureFeature.BARRY_AS_BDFL);
                        break;
                    case "annotations":
                        features.add(FutureFeature.ANNOTATIONS);
                        break;
                    case "braces":
                        throw parserCallbacks.onError(ErrorType.Syntax, node.getSourceRange(), "not a chance");
                    default:
                        throw parserCallbacks.onError(ErrorType.Syntax, node.getSourceRange(), "future feature %s is not defined", alias.name);
                }
            }
        }
    }

    private static Object findDocstring(StmtTy[] body) {
        if (body != null && body.length > 0 && body[0] instanceof StmtTy.Expr stmt && stmt.value instanceof ExprTy.Constant expr) {
            ConstantValue value = expr.value;
            if (value.kind == ConstantValue.Kind.CODEPOINTS) {
                return codePointsToTruffleString(value.getCodePoints());
            }
        }
        return null;
    }

    public static Parser createParser(String src, ParserCallbacks errorCb, InputType inputType, boolean interactiveTerminal, boolean allowIncompleteInput) {
        EnumSet<AbstractParser.Flags> flags = EnumSet.noneOf(AbstractParser.Flags.class);
        if (interactiveTerminal) {
            flags.add(AbstractParser.Flags.INTERACTIVE_TERMINAL);
        }
        if (allowIncompleteInput) {
            flags.add(AbstractParser.Flags.ALLOW_INCOMPLETE_INPUT);
        }
        return createParser(src, errorCb, inputType, flags, PythonLanguage.MINOR);
    }

    public static Parser createParser(String src, ParserCallbacks errorCb, InputType inputType, EnumSet<AbstractParser.Flags> flags, int featureVersion) {
        return new Parser(src, errorCb, inputType, flags, featureVersion);
    }

    public static class BytecodeDSLCompilerContext {

        public final PythonLanguage language;
        public final ModTy mod;
        public final Source source;
        public final int optimizationLevel;
        public final EnumSet<FutureFeature> futureFeatures;
        public final int futureLineNumber;
        public final ParserCallbacksImpl errorCallback;
        public final ScopeEnvironment scopeEnvironment;
        // Store code units for possible reparses
        public final Map<Object, BytecodeDSLCodeUnit> codeUnits = new HashMap<>();

        public BytecodeDSLCompilerContext(PythonLanguage language, ModTy mod, Source source, int optimizationLevel,
                        EnumSet<FutureFeature> futureFeatures, int futureLineNumber, ParserCallbacksImpl errorCallback, ScopeEnvironment scopeEnvironment) {
            this.language = language;
            this.mod = mod;
            this.source = source;
            this.optimizationLevel = optimizationLevel;
            this.futureFeatures = futureFeatures;
            this.futureLineNumber = futureLineNumber;
            this.errorCallback = errorCallback;
            this.scopeEnvironment = scopeEnvironment;
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
    }
}
