package com.oracle.graal.python.compiler.bytecode_dsl;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.compiler.Compiler;
import com.oracle.graal.python.compiler.RaisePythonExceptionErrorCallback;
import com.oracle.graal.python.nodes.bytecode_dsl.BytecodeDSLCodeUnit;
import com.oracle.graal.python.nodes.bytecode_dsl.PBytecodeDSLRootNode;
import com.oracle.graal.python.pegparser.ErrorCallback;
import com.oracle.graal.python.pegparser.FutureFeature;
import com.oracle.graal.python.pegparser.scope.Scope;
import com.oracle.graal.python.pegparser.scope.ScopeEnvironment;
import com.oracle.graal.python.pegparser.sst.ModTy;
import com.oracle.graal.python.pegparser.sst.StmtTy;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.graal.python.runtime.object.PythonObjectSlowPathFactory;
import com.oracle.truffle.api.source.Source;

public class BytecodeDSLCompiler {

    public static final record BytecodeDSLCompilerResult(PBytecodeDSLRootNode rootNode, BytecodeDSLCodeUnit codeUnit) {
    }

    public static BytecodeDSLCompilerResult compile(PythonLanguage language, PythonContext context, ModTy mod, Source source, int optimize, EnumSet<FutureFeature> futureFeatures) {
        ErrorCallback errorCallback = new RaisePythonExceptionErrorCallback(source, false);
        /**
         * Parse __future__ annotations before the analysis step. The analysis does extra validation
         * when __future__.annotations is imported.
         */
        int futureLineNumber = parseFuture(mod, futureFeatures, errorCallback);
        ScopeEnvironment scopeEnvironment = ScopeEnvironment.analyze(mod, errorCallback, futureFeatures);
        BytecodeDSLCompilerContext ctx = new BytecodeDSLCompilerContext(language, context, mod, source, optimize, futureFeatures, futureLineNumber, errorCallback, scopeEnvironment);
        RootNodeCompiler compiler = new RootNodeCompiler(ctx, mod, futureFeatures);
        return compiler.compile();
    }

    private static int parseFuture(ModTy mod, EnumSet<FutureFeature> futureFeatures, ErrorCallback errorCallback) {
        StmtTy[] stmts = null;
        if (mod instanceof ModTy.Module module) {
            stmts = module.body;
        } else if (mod instanceof ModTy.Interactive interactive) {
            stmts = interactive.body;
        } else {
            return -1;
        }
        return Compiler.parseFuture(stmts, futureFeatures, errorCallback);
    }

    public static class BytecodeDSLCompilerContext {

        public final PythonLanguage language;
        public final PythonContext pythonContext;
        public final PythonObjectSlowPathFactory factory;
        public final ModTy mod;
        public final Source source;
        public final int optimizationLevel;
        public final EnumSet<FutureFeature> futureFeatures;
        public final int futureLineNumber;
        public final ErrorCallback errorCallback;
        public final ScopeEnvironment scopeEnvironment;
        public final Map<Scope, String> qualifiedNames;

        public BytecodeDSLCompilerContext(PythonLanguage language, PythonContext context, ModTy mod, Source source, int optimizationLevel,
                        EnumSet<FutureFeature> futureFeatures, int futureLineNumber, ErrorCallback errorCallback, ScopeEnvironment scopeEnvironment) {
            this.language = language;
            this.pythonContext = context;
            this.factory = context.factory();
            this.mod = mod;
            this.source = source;
            this.optimizationLevel = optimizationLevel;
            this.futureFeatures = futureFeatures;
            this.futureLineNumber = futureLineNumber;
            this.errorCallback = errorCallback;
            this.scopeEnvironment = scopeEnvironment;
            this.qualifiedNames = new HashMap<>();
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

        private String computeQualifiedName(Scope scope) {
            String qualifiedName = scope.getName();
            Scope parentScope = scopeEnvironment.lookupParent(scope);
            if (parentScope != null && parentScope != scopeEnvironment.getTopScope()) {
                if (!((scope.isFunction() || scope.isClass()) && parentScope.getUseOfName(mangle(scope, scope.getName())).contains(Scope.DefUse.GlobalExplicit))) {
                    // Qualify the name, unless it's a function/class and the parent declared the
                    // name as a global (in which case the function/class doesn't belong to the
                    // parent).
                    if (parentScope.isFunction()) {
                        qualifiedName = getQualifiedName(parentScope) + ".<locals>." + scope.getName();
                    } else {
                        qualifiedName = getQualifiedName(parentScope) + "." + scope.getName();
                    }
                }
            }

            return qualifiedName;
        }
    }
}
