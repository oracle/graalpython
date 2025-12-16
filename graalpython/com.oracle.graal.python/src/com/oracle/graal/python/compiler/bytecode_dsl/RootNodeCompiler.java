/*
 * Copyright (c) 2024, 2025, Oracle and/or its affiliates. All rights reserved.
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

import static com.oracle.graal.python.compiler.CompilationScope.AsyncFunction;
import static com.oracle.graal.python.compiler.CompilationScope.Class;
import static com.oracle.graal.python.compiler.CompilationScope.TypeParams;
import static com.oracle.graal.python.compiler.SSTUtils.checkCaller;
import static com.oracle.graal.python.compiler.SSTUtils.checkCompare;
import static com.oracle.graal.python.compiler.SSTUtils.checkForbiddenArgs;
import static com.oracle.graal.python.compiler.SSTUtils.checkIndex;
import static com.oracle.graal.python.compiler.SSTUtils.checkSubscripter;
import static com.oracle.graal.python.compiler.bytecode_dsl.BytecodeDSLCompilerUtils.COMPREHENSION_ARGS;
import static com.oracle.graal.python.compiler.bytecode_dsl.BytecodeDSLCompilerUtils.NO_ARGS;
import static com.oracle.graal.python.compiler.bytecode_dsl.BytecodeDSLCompilerUtils.TYPE_PARAMS_DEFAULTS;
import static com.oracle.graal.python.compiler.bytecode_dsl.BytecodeDSLCompilerUtils.TYPE_PARAMS_DEFAULTS_KWDEFAULTS;
import static com.oracle.graal.python.compiler.bytecode_dsl.BytecodeDSLCompilerUtils.TYPE_PARAMS_KWDEFAULTS;
import static com.oracle.graal.python.compiler.bytecode_dsl.BytecodeDSLCompilerUtils.addObject;
import static com.oracle.graal.python.compiler.bytecode_dsl.BytecodeDSLCompilerUtils.hasDefaultArgs;
import static com.oracle.graal.python.compiler.bytecode_dsl.BytecodeDSLCompilerUtils.hasDefaultKwargs;
import static com.oracle.graal.python.compiler.bytecode_dsl.BytecodeDSLCompilerUtils.len;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.J___CLASS__;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.J___TYPE_PARAMS__;
import static com.oracle.graal.python.util.PythonUtils.codePointsToInternedTruffleString;
import static com.oracle.graal.python.util.PythonUtils.codePointsToTruffleString;
import static com.oracle.graal.python.util.PythonUtils.toInternedTruffleStringUncached;
import static com.oracle.graal.python.util.PythonUtils.toTruffleStringUncached;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.code.PCode;
import com.oracle.graal.python.builtins.objects.ellipsis.PEllipsis;
import com.oracle.graal.python.builtins.objects.function.PArguments;
import com.oracle.graal.python.builtins.objects.function.PKeyword;
import com.oracle.graal.python.builtins.objects.type.TypeFlags;
import com.oracle.graal.python.compiler.CompilationScope;
import com.oracle.graal.python.compiler.Compiler;
import com.oracle.graal.python.compiler.Compiler.ConstantCollection;
import com.oracle.graal.python.compiler.OpCodes.CollectionBits;
import com.oracle.graal.python.compiler.OpCodes.MakeTypeParamKind;
import com.oracle.graal.python.compiler.SSTUtils;
import com.oracle.graal.python.compiler.Unparser;
import com.oracle.graal.python.compiler.bytecode_dsl.BytecodeDSLCompiler.BytecodeDSLCompilerContext;
import com.oracle.graal.python.compiler.bytecode_dsl.BytecodeDSLCompiler.BytecodeDSLCompilerResult;
import com.oracle.graal.python.lib.PyObjectRichCompareBool;
import com.oracle.graal.python.nodes.StringLiterals;
import com.oracle.graal.python.nodes.bytecode_dsl.BytecodeDSLCodeUnit;
import com.oracle.graal.python.nodes.bytecode_dsl.BytecodeDSLCodeUnitAndRoot;
import com.oracle.graal.python.nodes.bytecode_dsl.PBytecodeDSLRootNode;
import com.oracle.graal.python.nodes.bytecode_dsl.PBytecodeDSLRootNodeGen;
import com.oracle.graal.python.nodes.bytecode_dsl.PBytecodeDSLRootNodeGen.Builder;
import com.oracle.graal.python.pegparser.FutureFeature;
import com.oracle.graal.python.pegparser.ParserCallbacks.ErrorType;
import com.oracle.graal.python.pegparser.ParserCallbacks.WarningType;
import com.oracle.graal.python.pegparser.scope.Scope;
import com.oracle.graal.python.pegparser.scope.Scope.DefUse;
import com.oracle.graal.python.pegparser.scope.ScopeEnvironment;
import com.oracle.graal.python.pegparser.sst.AliasTy;
import com.oracle.graal.python.pegparser.sst.ArgTy;
import com.oracle.graal.python.pegparser.sst.ArgumentsTy;
import com.oracle.graal.python.pegparser.sst.BoolOpTy;
import com.oracle.graal.python.pegparser.sst.CmpOpTy;
import com.oracle.graal.python.pegparser.sst.ComprehensionTy;
import com.oracle.graal.python.pegparser.sst.ConstantValue;
import com.oracle.graal.python.pegparser.sst.ConstantValue.Kind;
import com.oracle.graal.python.pegparser.sst.ExceptHandlerTy;
import com.oracle.graal.python.pegparser.sst.ExprContextTy;
import com.oracle.graal.python.pegparser.sst.ExprTy;
import com.oracle.graal.python.pegparser.sst.ExprTy.DictComp;
import com.oracle.graal.python.pegparser.sst.ExprTy.GeneratorExp;
import com.oracle.graal.python.pegparser.sst.ExprTy.Lambda;
import com.oracle.graal.python.pegparser.sst.ExprTy.ListComp;
import com.oracle.graal.python.pegparser.sst.ExprTy.SetComp;
import com.oracle.graal.python.pegparser.sst.ExprTy.Tuple;
import com.oracle.graal.python.pegparser.sst.KeywordTy;
import com.oracle.graal.python.pegparser.sst.MatchCaseTy;
import com.oracle.graal.python.pegparser.sst.ModTy;
import com.oracle.graal.python.pegparser.sst.OperatorTy;
import com.oracle.graal.python.pegparser.sst.PatternTy;
import com.oracle.graal.python.pegparser.sst.SSTNode;
import com.oracle.graal.python.pegparser.sst.SSTreeVisitor;
import com.oracle.graal.python.pegparser.sst.StmtTy;
import com.oracle.graal.python.pegparser.sst.StmtTy.AsyncFunctionDef;
import com.oracle.graal.python.pegparser.sst.StmtTy.ClassDef;
import com.oracle.graal.python.pegparser.sst.StmtTy.FunctionDef;
import com.oracle.graal.python.pegparser.sst.StmtTy.TypeAlias;
import com.oracle.graal.python.pegparser.sst.TypeParamTy;
import com.oracle.graal.python.pegparser.sst.TypeParamTy.ParamSpec;
import com.oracle.graal.python.pegparser.sst.TypeParamTy.TypeVar;
import com.oracle.graal.python.pegparser.sst.TypeParamTy.TypeVarTuple;
import com.oracle.graal.python.pegparser.sst.UnaryOpTy;
import com.oracle.graal.python.pegparser.sst.WithItemTy;
import com.oracle.graal.python.pegparser.tokenizer.SourceRange;
import com.oracle.graal.python.util.PythonUtils;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.bytecode.BytecodeConfig;
import com.oracle.truffle.api.bytecode.BytecodeLabel;
import com.oracle.truffle.api.bytecode.BytecodeLocal;
import com.oracle.truffle.api.bytecode.BytecodeParser;
import com.oracle.truffle.api.bytecode.BytecodeRootNodes;
import com.oracle.truffle.api.instrumentation.StandardTags.StatementTag;
import com.oracle.truffle.api.strings.TruffleString;

/**
 * Compiles a top-level AST (modules, functions, classes, etc.) to a root node. Produces a
 * {@link BytecodeDSLCompilerResult}. Every instance is associated with corresponding
 * {@link SSTNode} that represents the compiled top level AST.
 * <p>
 * The class implements SST visitor, so that it can have a separate handler for each top-level AST
 * node type, the handler (one of the {@code visit} methods) then creates a lambda of type
 * {@link BytecodeParser}, which captures the node being compiled and the instance of
 * {@link RootNodeCompiler}, and it uses the {@link RootNodeCompiler} to do the parsing itself. The
 * {@link BytecodeParser} instance is passed to Truffle API
 * {@link PBytecodeDSLRootNodeGen#create(PythonLanguage, BytecodeConfig, BytecodeParser)} to trigger
 * the parsing. Truffle keeps the lambda, and it may invoke it again when it needs to perform the
 * parsing of the given node again.
 * <p>
 * The parsing must happen within the {@link BytecodeParser} lambda invocation.
 * <p>
 * This visitor also captures compilation unit state, such as the map of local variables, and serves
 * the same purpose as the {@code compiler_unit} struct in the CPython compiler. Instead of explicit
 * stack of compiler units, we use implicitly Java stack and new instances of
 * {@link RootNodeCompiler}.
 * <p>
 * For the parsing of the body of the top level AST element, this visitor delegates to the
 * {@link StatementCompiler}, which does all the heavy lifting.
 */
public final class RootNodeCompiler implements BaseBytecodeDSLVisitor<BytecodeDSLCompilerResult> {
    /**
     * Because a {@link RootNodeCompiler} instance gets reused on reparse, it should be idempotent.
     * Consequently, most of its fields are final and immutable/not mutated after construction. For
     * some tables updated during parsing (e.g., the constants map), we ensure these updates are
     * idempotent. Any remaining fields must be {@link #reset()} at the beginning of the parse.
     */
    // Immutable
    private final BytecodeDSLCompilerContext ctx;
    private final SSTNode startNode;
    private final Scope scope;
    private final CompilationScope scopeType;
    private final boolean isInteractive;
    private final EnumSet<FutureFeature> futureFeatures;

    /**
     * Used for name mangling in the context of type parameters. See
     * {@link com.oracle.graal.python.pegparser.scope.ScopeEnvironment#maybeMangle(String, Scope, String)}.
     */
    private final String privateName;
    private final RootNodeCompiler parent;
    private String qualName;

    // Immutable after construction
    private final HashMap<String, Integer> varnames;
    private final HashMap<String, Integer> cellvars;
    private final HashMap<String, Integer> freevars;
    private final int[] cell2arg;
    private final String selfCellName;

    // Updated idempotently: the keys are filled during first parsing, on subsequent parsings the
    // values will be just overridden, but no new keys should be added.
    private final Map<String, BytecodeLocal> locals = new HashMap<>();
    private final Map<String, BytecodeLocal> cellLocals = new HashMap<>();
    private final Map<String, BytecodeLocal> freeLocals = new HashMap<>();
    private final HashMap<Object, Integer> constants = new HashMap<>();
    private final HashMap<String, Integer> names = new HashMap<>();

    /**
     * Initialized lazily only for generator functions. Internal variable used to store the
     * generator's exception state. Cleared if there is no exception, otherwise set to the
     * {@link com.oracle.graal.python.runtime.exception.PException} object. This stores only
     * exception raised inside the generator (not caller passed exception state).
     * <p>
     * We need to distinguish between the caller exception state passed in the generator's frame's
     * arguments array (could be null => meaning: stack-walk is needed to fetch it) and the
     * exception state of the generator itself. Example:
     *
     * <pre>
     *     def gen():
     *          try:
     *              3/0
     *          except:
     *              yeild sys.exc_info()
     *     g = gen()
     *     try:
     *         raise AttributeError()
     *     except:
     *         print(gen.send(None)) # gives division by zero error, not AttributeError
     * </pre>
     */
    private BytecodeLocal generatorExceptionStateLocal;

    // Mutable (must be reset)
    private SourceRange currentLocation;
    BytecodeLocal yieldFromGenerator;
    private int lastTracedLine;
    boolean inExceptStar;

    /**
     * Applicable only for generators: we need to know if we are in a code block that saved the
     * exception state from caller ("outer exception") in order to restore it later, because if
     * there is a yield in that block, on the resume we must update the saved exception state
     * according to the exception state of the new caller. Any nested except block is
     * saving/restoring exception that was raised by the generator and that should stay across
     * resumes.
     * <p>
     * This field holds the local used to save the exception if we are in except or any other kind
     * of block that saves and restores current exception.
     */
    private BytecodeLocal currentSaveExceptionLocal;
    private BytecodeLocal prevSaveExceptionLocal;

    public RootNodeCompiler(BytecodeDSLCompilerContext ctx, RootNodeCompiler parent, SSTNode rootNode, EnumSet<FutureFeature> futureFeatures) {
        this(ctx, parent, null, rootNode, rootNode, futureFeatures);
    }

    public RootNodeCompiler(BytecodeDSLCompilerContext ctx, RootNodeCompiler parent, String privateName, SSTNode rootNode, Object scopeKey, EnumSet<FutureFeature> futureFeatures) {
        this.ctx = ctx;
        this.startNode = rootNode;
        this.scope = ctx.scopeEnvironment.lookupScope(scopeKey);
        this.scopeType = getScopeType(scope, scopeKey);
        this.parent = parent;
        if (privateName != null) {
            this.privateName = privateName;
        } else if (scopeType == Class) {
            this.privateName = ((ClassDef) rootNode).name;
        } else if (parent != null) {
            this.privateName = parent.privateName;
        } else {
            this.privateName = null;
        }
        this.isInteractive = rootNode instanceof ModTy.Interactive;
        this.futureFeatures = futureFeatures;

        this.varnames = new HashMap<>();
        if (scope.isFunction()) {
            /*
             * scope.getVarnames only returns parameters. We use the scope to collect the rest of
             * the regular variables.
             */
            for (int i = 0; i < scope.getVarnames().size(); i++) {
                varnames.put(scope.getVarnames().get(i), i);
            }
            varnames.putAll(scope.getSymbolsByType(EnumSet.of(DefUse.Local), EnumSet.of(DefUse.DefParam, DefUse.Cell, DefUse.Free), varnames.size()));
        }

        this.cellvars = scope.getSymbolsByType(EnumSet.of(Scope.DefUse.Cell), 0);
        if (scope.needsClassClosure()) {
            assert scopeType == Class;
            assert cellvars.isEmpty();
            cellvars.put("__class__", 0);
        }

        if (scope.needsClassDict()) {
            assert scopeType == Class;
            cellvars.put("__classdict__", cellvars.size());
        }

        this.freevars = scope.getSymbolsByType(EnumSet.of(Scope.DefUse.Free, Scope.DefUse.DefFreeClass), 0);

        int[] cell2argValue = new int[cellvars.size()];
        boolean hasArgCell = false;
        Arrays.fill(cell2argValue, -1);
        String selfCellNameValue = null;
        for (String cellvar : cellvars.keySet()) {
            if (varnames.containsKey(cellvar)) {
                int argIndex = varnames.get(cellvar);
                cell2argValue[cellvars.get(cellvar)] = argIndex;
                hasArgCell = true;
                if (argIndex == 0) {
                    assert selfCellNameValue == null;
                    selfCellNameValue = cellvar;
                }
            }
        }
        this.cell2arg = hasArgCell ? cell2argValue : null;
        this.selfCellName = selfCellNameValue;
    }

    private static CompilationScope getScopeType(Scope scope, Object scopeKey) {
        if (scope.isModule()) {
            return CompilationScope.Module;
        } else if (scope.isClass()) {
            return CompilationScope.Class;
        } else if (scope.isFunction()) {
            if (scopeKey instanceof Lambda) {
                return CompilationScope.Lambda;
            } else if (scopeKey instanceof AsyncFunctionDef) {
                return CompilationScope.AsyncFunction;
            } else if (scopeKey instanceof DictComp || scopeKey instanceof ListComp || scopeKey instanceof SetComp || scopeKey instanceof GeneratorExp) {
                return CompilationScope.Comprehension;
            } else if (scopeKey instanceof TypeParamTy[]) {
                return CompilationScope.TypeParams;
            } else {
                return CompilationScope.Function;
            }
        } else {
            throw new IllegalStateException("Unexpected scope: " + scope);
        }
    }

    private static <T, U> U[] orderedKeys(HashMap<T, Integer> map, U[] base, Function<T, U> converter) {
        U[] result = Arrays.copyOf(base, map.size());
        for (Map.Entry<T, Integer> e : map.entrySet()) {
            result[e.getValue()] = converter.apply(e.getKey());
        }
        return result;
    }

    private static <T> T[] orderedKeys(HashMap<T, Integer> map, T[] base) {
        return orderedKeys(map, base, x -> x);
    }

    private Object addConstant(Object c) {
        Integer v = constants.get(c);
        if (v == null) {
            v = constants.size();
            constants.put(c, v);
        }
        return c;
    }

    private static TruffleString[] orderedTruffleStringArray(HashMap<String, Integer> map) {
        return orderedKeys(map, PythonUtils.EMPTY_TRUFFLESTRING_ARRAY, PythonUtils::toInternedTruffleStringUncached);
    }

    private String getNewScopeQualName(String name, CompilationScope scopeType) {
        RootNodeCompiler parent = this.parent;
        if (parent != null && parent.parent != null) {
            if (parent.scopeType == TypeParams && parent.parent != null && parent.parent.parent != null) {
                parent = parent.parent;
                if (parent.parent != null && parent.parent.parent == null) {
                    // if there are exactly two parents/ancestros, then return the name
                    return name;
                }
            }
            if (!(EnumSet.of(CompilationScope.Function, AsyncFunction, Class).contains(scopeType) &&
                            parent.scope.getUseOfName(ScopeEnvironment.mangle(parent.privateName, name)).contains(Scope.DefUse.GlobalExplicit))) {
                String base;
                if (EnumSet.of(CompilationScope.Function, AsyncFunction, CompilationScope.Lambda).contains(parent.scopeType)) {
                    base = parent.qualName + ".<locals>";
                } else {
                    base = parent.qualName;
                }
                return base + "." + name;
            }
        }
        return name;
    }

    private BytecodeDSLCompilerResult compileRootNode(String name, ArgumentInfo argumentInfo, SourceRange sourceRange, BytecodeParser<Builder> parser) {
        qualName = getNewScopeQualName(name, scopeType);

        BytecodeRootNodes<PBytecodeDSLRootNode> nodes = PBytecodeDSLRootNodeGen.create(ctx.language, BytecodeConfig.WITH_SOURCE, parser);
        List<PBytecodeDSLRootNode> nodeList = nodes.getNodes();
        assert nodeList.size() == 1;
        PBytecodeDSLRootNode rootNode = nodeList.get(0);

        int flags = PCode.CO_OPTIMIZED | PCode.CO_NEWLOCALS;
        flags |= argumentInfo.takesVarArgs ? PCode.CO_VARARGS : 0;
        flags |= argumentInfo.takesVarKeywordArgs ? PCode.CO_VARKEYWORDS : 0;
        if (scope.isNested()) {
            flags |= PCode.CO_NESTED;
        }
        if (scope.isModule()) {
            flags |= PCode.CO_GRAALPYHON_MODULE;
        }
        if (scope.isGenerator() && scope.isCoroutine()) {
            flags |= PCode.CO_ASYNC_GENERATOR;
        } else if (scope.isGenerator()) {
            flags |= PCode.CO_GENERATOR;
        } else if (scope.isCoroutine()) {
            flags |= PCode.CO_COROUTINE;
        }
        for (FutureFeature flag : futureFeatures) {
            flags |= flag.flagValue;
        }

        int classcellIndex = -1;
        if (freeLocals.containsKey(J___CLASS__)) {
            classcellIndex = freeLocals.get(J___CLASS__).getLocalOffset();
        }

        int selfIndex = -1;
        if (argumentInfo.nonEmpty()) {
            selfIndex = 0;
            if (selfCellName != null) {
                selfIndex = cellLocals.get(selfCellName).getLocalOffset();
            }
        }

        BytecodeDSLCodeUnit codeUnit = new BytecodeDSLCodeUnit(toInternedTruffleStringUncached(name), toInternedTruffleStringUncached(qualName),
                        argumentInfo.argCount, argumentInfo.kwOnlyArgCount, argumentInfo.positionalOnlyArgCount,
                        flags, orderedTruffleStringArray(names),
                        orderedTruffleStringArray(varnames),
                        orderedTruffleStringArray(cellvars),
                        orderedTruffleStringArray(freevars),
                        cell2arg,
                        orderedKeys(constants, new Object[0]),
                        sourceRange.startLine,
                        sourceRange.startColumn,
                        sourceRange.endLine,
                        sourceRange.endColumn,
                        classcellIndex,
                        selfIndex,
                        null,
                        nodes);
        rootNode.setMetadata(codeUnit, ctx.errorCallback);
        if (codeUnit.isCoroutine() || codeUnit.isAsyncGenerator() || scope.isGeneratorWithYieldFrom()) {
            rootNode.yieldFromGeneratorIndex = yieldFromGenerator.getLocalIndex();
        }

        return new BytecodeDSLCompilerResult(rootNode, codeUnit);
    }

    private static class ArgumentInfo {
        static final ArgumentInfo NO_ARGS = new ArgumentInfo(0, 0, 0, false, false);

        final int argCount;
        final int positionalOnlyArgCount;
        final int kwOnlyArgCount;
        final boolean takesVarArgs;
        final boolean takesVarKeywordArgs;

        ArgumentInfo(int argCount, int positionalOnlyArgCount, int kwOnlyArgCount, boolean takesVarArgs, boolean takesVarKeywordArgs) {
            this.argCount = argCount;
            this.positionalOnlyArgCount = positionalOnlyArgCount;
            this.kwOnlyArgCount = kwOnlyArgCount;
            this.takesVarArgs = takesVarArgs;
            this.takesVarKeywordArgs = takesVarKeywordArgs;
        }

        static ArgumentInfo fromArguments(ArgumentsTy args) {
            int argc, pargc, kwargc;
            boolean splat, kwSplat;
            if (args == null) {
                argc = pargc = kwargc = 0;
                splat = kwSplat = false;
            } else {
                argc = args.args == null ? 0 : args.args.length;
                pargc = args.posOnlyArgs == null ? 0 : args.posOnlyArgs.length;
                kwargc = args.kwOnlyArgs == null ? 0 : args.kwOnlyArgs.length;
                splat = args.varArg != null;
                kwSplat = args.kwArg != null;
            }
            return new ArgumentInfo(argc, pargc, kwargc, splat, kwSplat);
        }

        private boolean nonEmpty() {
            return argCount + positionalOnlyArgCount + kwOnlyArgCount > 0 || takesVarArgs || takesVarKeywordArgs;
        }
    }

    private void checkForbiddenName(String id, NameOperation context) {
        checkForbiddenName(id, context, currentLocation);
    }

    private void checkForbiddenName(String id, NameOperation context, SourceRange location) {
        ExprContextTy exprContext = switch (context) {
            case BeginWrite, EndWrite -> ExprContextTy.Store;
            case Read -> ExprContextTy.Load;
            case Delete -> ExprContextTy.Del;
        };
        SSTUtils.checkForbiddenName(ctx.errorCallback, location, id, exprContext);
    }

    private boolean containsAnnotations(StmtTy[] stmts) {
        if (stmts == null) {
            return false;
        }
        for (StmtTy stmt : stmts) {
            if (containsAnnotations(stmt)) {
                return true;
            }
        }
        return false;
    }

    private boolean containsAnnotations(StmtTy stmt) {
        if (stmt instanceof StmtTy.AnnAssign) {
            return true;
        } else if (stmt instanceof StmtTy.For) {
            return containsAnnotations(((StmtTy.For) stmt).body) || containsAnnotations(((StmtTy.For) stmt).orElse);
        } else if (stmt instanceof StmtTy.While) {
            return containsAnnotations(((StmtTy.While) stmt).body) || containsAnnotations(((StmtTy.While) stmt).orElse);
        } else if (stmt instanceof StmtTy.If) {
            return containsAnnotations(((StmtTy.If) stmt).body) || containsAnnotations(((StmtTy.If) stmt).orElse);
        } else if (stmt instanceof StmtTy.With) {
            return containsAnnotations(((StmtTy.With) stmt).body);
        } else if (stmt instanceof StmtTy.Try) {
            StmtTy.Try tryStmt = (StmtTy.Try) stmt;
            if (tryStmt.handlers != null) {
                for (ExceptHandlerTy h : tryStmt.handlers) {
                    if (containsAnnotations(((ExceptHandlerTy.ExceptHandler) h).body)) {
                        return true;
                    }
                }
            }
            return containsAnnotations(tryStmt.body) || containsAnnotations(tryStmt.finalBody) || containsAnnotations(tryStmt.orElse);
        } else if (stmt instanceof StmtTy.Match) {
            StmtTy.Match matchStmt = (StmtTy.Match) stmt;
            for (MatchCaseTy _case : matchStmt.cases) {
                if (containsAnnotations(_case.body)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static final class ParamAnnotation {
        final TruffleString name;
        final ExprTy annotation;

        ParamAnnotation(TruffleString name, ExprTy annotation) {
            this.name = name;
            this.annotation = annotation;
        }
    }

    private List<ParamAnnotation> collectParamAnnotations(ArgumentsTy args, ExprTy returns) {
        List<ParamAnnotation> result = new ArrayList<>();
        if (args != null) {
            visitParamAnnotations(result, args.args);
            visitParamAnnotations(result, args.posOnlyArgs);
            if (args.varArg != null) {
                visitParamAnnotation(result, args.varArg.arg, args.varArg.annotation);
            }
            visitParamAnnotations(result, args.kwOnlyArgs);
            if (args.kwArg != null) {
                visitParamAnnotation(result, args.kwArg.arg, args.kwArg.annotation);
            }
        }
        visitParamAnnotation(result, "return", returns);
        return result;
    }

    private void visitParamAnnotations(List<ParamAnnotation> result, ArgTy[] args) {
        for (int i = 0; i < args.length; i++) {
            visitParamAnnotation(result, args[i].arg, args[i].annotation);
        }
    }

    private void visitParamAnnotation(List<ParamAnnotation> result, String name, ExprTy annotation) {
        if (annotation != null) {
            String mangled = mangle(name);
            result.add(new ParamAnnotation(toTruffleStringUncached(mangled), annotation));
        }
    }

    public BytecodeDSLCompilerResult compile() {
        return startNode.accept(this);
    }

    public void reset() {
        currentLocation = null;
        currentSaveExceptionLocal = null;
        prevSaveExceptionLocal = null;
        lastTracedLine = -1;
        this.inExceptStar = false;
    }

    // -------------- helpers --------------

    void beginRootNode(SSTNode node, ArgumentsTy args, Builder b) {
        reset();
        b.beginSource(ctx.source);
        beginRootSourceSection(node, b);

        b.beginRoot();

        checkForbiddenArgs(ctx.errorCallback, node.getSourceRange(), args);
        setUpFrame(args, b);

        b.emitTraceOrProfileCall();
        if (node instanceof ClassDef cls) {
            if (cls.decoratorList != null && cls.decoratorList.length > 0) {
                b.emitTraceLine(cls.decoratorList[0].getSourceRange().startLine);
            } else {
                b.emitTraceLine(node.getSourceRange().startLine);
            }
        } else if (node instanceof FunctionDef fn) {
            if (fn.decoratorList != null && fn.decoratorList.length > 0) {
                b.emitTraceLine(fn.decoratorList[0].getSourceRange().startLine);
            }
        }
    }

    void endRootNode(Builder b) {
        b.endRoot();
        endRootSourceSection(b);
        b.endSource();
    }

    void emitTraceLineChecked(SSTNode node, Builder b) {
        if (lastTracedLine == -1 || node.getSourceRange().startLine != lastTracedLine) {
            b.emitTraceLine(node.getSourceRange().startLine);
            lastTracedLine = node.getSourceRange().startLine;
        }
    }

    void beginTraceLineChecked(Builder b) {
        b.beginBlock();
        b.beginTraceLineWithArgument();
    }

    /**
     * Emits a "line" tracing if either no tracing was emitted before, or if line number was
     * updated.
     * 
     * @param b Builder for line tracing.
     */
    void endTraceLineChecked(SSTNode node, Builder b) {
        if (lastTracedLine == -1 || node.getSourceRange().startLine != lastTracedLine) {
            b.endTraceLineWithArgument(node.getSourceRange().startLine);
            lastTracedLine = node.getSourceRange().startLine;
        } else {
            b.endTraceLineWithArgument(-1);
        }
        b.endBlock();
    }

    /**
     * Opens a new SourceSection operation. Emits TraceLine and starts a new Tag(Statement) if this
     * location has a different line from the previous location.
     * <p>
     * Returns whether this call opened a new Tag(Statement). The result should be passed to the
     * corresponding {@link #endSourceSection} call to ensure the Tag is closed.
     */
    boolean beginSourceSection(SSTNode node, Builder b) {
        SourceRange sourceRange = node.getSourceRange();
        SourceRange oldSourceRange = this.currentLocation;
        this.currentLocation = sourceRange;

        if (ctx.source.hasCharacters()) {
            int startOffset = getStartOffset(sourceRange);
            int endOffset = getEndOffset(sourceRange);
            int length = endOffset - startOffset;
            if (length == 0) {
                startOffset = 0;
            }
            b.beginSourceSection(startOffset, length);

            if (oldSourceRange == null || oldSourceRange.startLine != sourceRange.startLine) {
                b.beginTag(StatementTag.class);
                b.beginBlock();
                return true;
            }
        }
        return false;
    }

    /**
     * Same as {@link #beginSourceSection(SSTNode, Builder)}, but does not emit tags or trace events
     * (since the root has not been started yet). Avoids setting {@link #currentLocation} so that
     * {{@link #beginSourceSection(SSTNode, Builder)} will emit a TraceLine for a statement on the
     * first line.
     */
    void beginRootSourceSection(SSTNode node, Builder b) {
        SourceRange sourceRange;
        if (node instanceof ClassDef cls && cls.decoratorList != null && cls.decoratorList.length > 0) {
            sourceRange = cls.decoratorList[0].getSourceRange().withEnd(node.getSourceRange().endLine, node.getSourceRange().endColumn);
        } else {
            sourceRange = node.getSourceRange();
        }

        if (ctx.source.hasCharacters()) {
            int startOffset = getStartOffset(sourceRange);
            int endOffset = getEndOffset(sourceRange);
            int length = endOffset - startOffset;
            if (length == 0) {
                startOffset = 0;
            }
            b.beginSourceSection(startOffset, length);
        }
    }

    void endSourceSection(Builder b, boolean closeTag) {
        if (ctx.source.hasCharacters()) {
            if (closeTag) {
                b.endBlock();
                b.endTag(StatementTag.class);
            }
            b.endSourceSection();
        }
    }

    void endRootSourceSection(Builder b) {
        if (ctx.source.hasCharacters()) {
            b.endSourceSection();
        }
    }

    int getStartOffset(SourceRange sourceRange) {
        return ctx.source.getLineStartOffset(sourceRange.startLine) + sourceRange.startColumn;
    }

    int getEndOffset(SourceRange sourceRange) {
        return ctx.source.getLineStartOffset(sourceRange.endLine) + sourceRange.endColumn;
    }

    void beginReturn(Builder b) {
        b.beginReturn();
        b.beginTraceOrProfileReturn();
    }

    void endReturn(Builder b) {
        b.endTraceOrProfileReturn();
        b.endReturn();
    }

    String maybeMangle(String name) {
        return ctx.maybeMangle(this.privateName, scope, name);
    }

    String maybeMangleAndAddName(String name) {
        String mangled = ctx.maybeMangle(this.privateName, scope, name);
        return addName(mangled);
    }

    // --------------------- visitor ---------------------------

    @Override
    public BytecodeDSLCompilerResult visit(ModTy.Module node) {
        return compileRootNode("<module>", ArgumentInfo.NO_ARGS, node.getSourceRange(), b -> {
            beginRootNode(node, null, b);
            visitModuleBody(node.body, b);
            endRootNode(b);
        });
    }

    @Override
    public BytecodeDSLCompilerResult visit(ModTy.Expression node) {
        return compileRootNode("<module>", ArgumentInfo.NO_ARGS, node.getSourceRange(), b -> {
            beginRootNode(node, null, b);
            beginReturn(b);
            new StatementCompiler(b).visitNode(node.body);
            endReturn(b);
            endRootNode(b);
        });
    }

    @Override
    public BytecodeDSLCompilerResult visit(ModTy.Interactive node) {
        return compileRootNode("<module>", ArgumentInfo.NO_ARGS, node.getSourceRange(), b -> {
            beginRootNode(node, null, b);
            visitModuleBody(node.body, b);
            endRootNode(b);
        });
    }

    private void visitModuleBody(StmtTy[] body, Builder b) {
        if (body != null) {
            if (containsAnnotations(body)) {
                b.emitSetupAnnotations();
            }

            StatementCompiler statementCompiler = new StatementCompiler(b);
            if (isInteractive) {
                for (int i = 0; i < body.length; i++) {
                    StmtTy bodyNode = body[i];
                    if (i == body.length - 1) {
                        bodyNode.accept(statementCompiler);

                        // For interactive code, always return None.
                        beginReturn(b);
                        b.emitLoadConstant(PNone.NONE);
                        endReturn(b);
                    } else {
                        bodyNode.accept(statementCompiler);
                    }
                }
            } else {
                int i = 0;
                TruffleString docstring = getDocstring(body);
                if (docstring != null) {
                    /*
                     * Skip over the docstring so it does not get evaluated (and registered as a
                     * constant) for higher optimization levels. We manually add it as a constant
                     * for lower levels.
                     */
                    i++;
                    if (ctx.optimizationLevel < 2) {
                        beginStoreLocal("__doc__", b);
                        emitPythonConstant(docstring, b);
                        endStoreLocal("__doc__", b);
                    }
                }
                if (i == body.length) {
                    // Special case: module body just consists of a docstring.
                    beginReturn(b);
                    b.emitLoadConstant(PNone.NONE);
                    endReturn(b);
                    return;
                }

                for (; i < body.length; i++) {
                    StmtTy bodyNode = body[i];
                    if (i == body.length - 1) {
                        if (bodyNode instanceof StmtTy.Expr expr) {
                            // Return the value of the last statement for interop eval.
                            beginReturn(b);
                            boolean closeTag = beginSourceSection(expr, b);
                            expr.value.accept(statementCompiler);
                            endSourceSection(b, closeTag);
                            endReturn(b);
                        } else {
                            bodyNode.accept(statementCompiler);
                            beginReturn(b);
                            b.emitLoadConstant(PNone.NONE);
                            endReturn(b);
                        }
                    } else {
                        bodyNode.accept(statementCompiler);
                    }
                }
            }
        } else {
            beginReturn(b);
            b.emitLoadConstant(PNone.NONE);
            endReturn(b);
        }
    }

    private static TruffleString getDocstring(StmtTy[] body) {
        if (body != null && body.length > 0) {
            StmtTy stmt = body[0];
            if (stmt instanceof StmtTy.Expr expr //
                            && expr.value instanceof ExprTy.Constant constant //
                            && constant.value.kind == ConstantValue.Kind.CODEPOINTS) {
                return codePointsToTruffleString(constant.value.getCodePoints());
            }
        }
        return null;
    }

    public BytecodeDSLCompilerResult compileFunctionDef(StmtTy node, String name, ArgumentsTy args, StmtTy[] body) {
        return compileRootNode(name, ArgumentInfo.fromArguments(args), node.getSourceRange(),
                        b -> emitFunctionDefBody(node, args, body, b, getDocstring(body), false));
    }

    /**
     * Creates a code unit that will create the type parameters and invoke "make function" with
     * given code unit to create the function for the
     * {@link com.oracle.graal.python.pegparser.sst.StmtTy.FunctionDef} or
     * {@link com.oracle.graal.python.pegparser.sst.StmtTy.AsyncFunctionDef} that we are processing.
     * <p/>
     * The resulting code unit will take the values of the default arguments and default keyword
     * arguments of the function we are processing as its own arguments. The values of those
     * arguments are plain Java object arrays.
     */
    private BytecodeDSLCompilerResult compileFunctionTypeParams(BytecodeDSLCodeUnit codeUnit, StmtTy node, String name, ArgumentsTy args, ExprTy returns, TypeParamTy[] typeParams) {
        assert this.scopeType == CompilationScope.TypeParams;
        // arguments info for the code unit that we are creating
        ArgumentsTy typeParamsUnitArgs;
        if (hasDefaultArgs(args) && hasDefaultKwargs(args)) {
            typeParamsUnitArgs = TYPE_PARAMS_DEFAULTS_KWDEFAULTS;
        } else if (hasDefaultKwargs(args)) {
            typeParamsUnitArgs = TYPE_PARAMS_KWDEFAULTS;
        } else if (hasDefaultArgs(args)) {
            typeParamsUnitArgs = TYPE_PARAMS_DEFAULTS;
        } else {
            typeParamsUnitArgs = NO_ARGS;
        }
        ArgumentInfo argInfo = ArgumentInfo.fromArguments(typeParamsUnitArgs);
        return compileRootNode(name, argInfo, node.getSourceRange(), b -> {
            beginRootNode(node, typeParamsUnitArgs, b);
            StatementCompiler statementCompiler = new StatementCompiler(b);

            // typeParamsLocal = {type parameters}
            BytecodeLocal typeParamsLocal = b.createLocal();
            b.beginStoreLocal(typeParamsLocal);
            statementCompiler.visitTypeParams(typeParams);
            b.endStoreLocal();

            // funLocal = {make function}
            BytecodeLocal funLocal = b.createLocal();
            b.beginStoreLocal(funLocal);
            List<ParamAnnotation> annotations = collectParamAnnotations(args, returns);
            BytecodeLocal defaultArgsLocal = null;
            BytecodeLocal defaultKwargsLocal = null;
            if (hasDefaultArgs(args)) {
                defaultArgsLocal = locals.get(".defaults");
                assert defaultArgsLocal != null;
            }
            if (hasDefaultKwargs(args)) {
                defaultKwargsLocal = locals.get(".kwdefaults");
                assert defaultKwargsLocal != null;
            }
            statementCompiler.emitMakeFunction(codeUnit, node, name, defaultArgsLocal, defaultKwargsLocal, null, annotations);
            b.endStoreLocal();

            // funLocal.__type_params__ = typeParamsLocal
            beginSetAttribute(J___TYPE_PARAMS__, b);
            b.emitLoadLocal(typeParamsLocal);
            b.emitLoadLocal(funLocal);
            b.endSetAttribute();

            // return funLocal
            b.beginReturn();
            b.emitLoadLocal(funLocal);
            b.endReturn();

            endRootNode(b);
        });
    }

    private BytecodeDSLCompilerResult compileBoundTypeVar(TypeVar node) {
        assert node.bound != null;
        return compileRootNode(node.name, ArgumentInfo.NO_ARGS, node.getSourceRange(), b -> {
            beginRootNode(node, null, b);
            b.beginReturn();
            node.bound.accept(new StatementCompiler(b));
            b.endReturn();
            endRootNode(b);
        });
    }

    private BytecodeDSLCompilerResult compileTypeAliasBody(TypeAlias node) {
        String name = ((ExprTy.Name) node.name).id;
        return compileRootNode(name, ArgumentInfo.NO_ARGS, node.getSourceRange(), b -> {
            // Make None the first constant, so the evaluate function can't have a docstring.
            addObject(constants, PNone.NONE);
            beginRootNode(node, null, b);
            b.beginReturn();
            node.value.accept(new StatementCompiler(b));
            b.endReturn();
            endRootNode(b);
        });
    }

    private BytecodeDSLCompilerResult compileTypeAliasTypeParameters(String name, BytecodeDSLCodeUnit codeUnit, TypeAlias node) {
        assert this.scopeType == CompilationScope.TypeParams;
        String typeParamsName = "<generic parameters of " + name + ">";
        return compileRootNode(typeParamsName, ArgumentInfo.NO_ARGS, node.getSourceRange(), b -> {
            beginRootNode(node, null, b);
            StatementCompiler statementCompiler = new StatementCompiler(b);
            statementCompiler.emitBuildTypeAlias(codeUnit, node);
            endRootNode(b);
        });
    }

    @Override
    public BytecodeDSLCompilerResult visit(ExprTy.Lambda node) {
        return compileRootNode("<lambda>", ArgumentInfo.fromArguments(node.args), node.getSourceRange(),
                        b -> emitFunctionDefBody(node, node.args, new SSTNode[]{node.body}, b, null, true));
    }

    private void emitFunctionDefBody(SSTNode node, ArgumentsTy args, SSTNode[] body, Builder b, Object docstring, boolean isLambda) {
        beginRootNode(node, args, b);

        int i = 0;
        if (docstring != null) {
            i++;
            if (ctx.optimizationLevel < 2) {
                addConstant(docstring);
            } else {
                addConstant(PNone.NONE);
            }
        } else {
            addConstant(PNone.NONE);
        }

        if (scope.isGenerator() || scope.isCoroutine()) {
            b.beginResumeYieldGenerator();
            b.emitYieldGenerator();
            b.endResumeYieldGenerator();
        }

        StatementCompiler statementCompiler = new StatementCompiler(b);

        if (isLambda) {
            assert i == 0;
            assert body[0] instanceof ExprTy;
            beginReturn(b);
            body[0].accept(statementCompiler);
            endReturn(b);
        } else {
            for (; i < body.length; i++) {
                body[i].accept(statementCompiler);
            }
            beginReturn(b);
            emitPythonConstant(PNone.NONE, b);
            endReturn(b);
        }

        endRootNode(b);
    }

    public BytecodeDSLCompilerResult compileClassDefBody(StmtTy.ClassDef node) {
        return compileRootNode(node.name, ArgumentInfo.NO_ARGS, node.getSourceRange(), b -> {
            beginRootNode(node, null, b);

            beginStoreLocal("__module__", b);
            emitReadLocal("__name__", b);
            endStoreLocal("__module__", b);

            beginStoreLocal("__qualname__", b);
            emitPythonConstant(toTruffleStringUncached(this.qualName), b);
            endStoreLocal("__qualname__", b);

            if (node.isGeneric()) {
                beginStoreLocal(J___TYPE_PARAMS__, b);
                emitReadLocal(".type_params", b);
                endStoreLocal(J___TYPE_PARAMS__, b);
            }

            if (scope.needsClassDict()) {
                assert "__classdict__".equals(mangle("__classdict__"));
                emitNameCellOperation("__classdict__", NameOperation.BeginWrite, b);
                b.emitLoadSpecialArgument();
                emitNameCellOperation("__classdict__", NameOperation.EndWrite, b);
            }

            if (containsAnnotations(node.body)) {
                b.emitSetupAnnotations();
            }

            int i = 0;
            TruffleString docstring = getDocstring(node.body);
            if (docstring != null) {
                i++;
                if (ctx.optimizationLevel < 2) {
                    beginStoreLocal("__doc__", b);
                    emitPythonConstant(docstring, b);
                    endStoreLocal("__doc__", b);
                }
            }

            StatementCompiler statementCompiler = new StatementCompiler(b);
            for (; i < node.body.length; i++) {
                node.body[i].accept(statementCompiler);
            }

            if (scope.needsClassDict()) {
                emitNameOperation("__classdictcell__", NameOperation.BeginWrite, b);
                assert "__classdict__".equals(mangle("__classdict__"));
                BytecodeLocal classDictCell = cellLocals.get("__classdict__");
                b.emitLoadLocal(classDictCell);
                emitNameOperation("__classdictcell__", NameOperation.EndWrite, b);
            }

            if (scope.needsClassClosure()) {
                beginStoreLocal("__classcell__", b);
                b.emitLoadLocal(cellLocals.get("__class__"));
                endStoreLocal("__classcell__", b);

                beginReturn(b);
                b.emitLoadLocal(cellLocals.get("__class__"));
                endReturn(b);
            } else {
                beginReturn(b);
                b.emitLoadConstant(PNone.NONE);
                endReturn(b);
            }

            endRootNode(b);
        });
    }

    public BytecodeDSLCompilerResult compileClassTypeParams(StmtTy.ClassDef node, BytecodeDSLCodeUnit classBody) {
        assert this.scopeType == CompilationScope.TypeParams;
        return compileRootNode(node.name, ArgumentInfo.NO_ARGS, node.getSourceRange(), b -> {
            beginRootNode(node, null, b);
            StatementCompiler statementCompiler = new StatementCompiler(b);
            statementCompiler.emitBuildClass(classBody, node);
            endRootNode(b);
        });
    }

    private void emitComprehension(ComprehensionTy[] generators, int index, Builder b,
                    BytecodeLocal collectionLocal,
                    BiConsumer<StatementCompiler, BytecodeLocal> accumulateProducer) {
        ComprehensionTy comp = generators[index];
        boolean newStatement = beginSourceSection(comp, b);
        StatementCompiler statementCompiler = new StatementCompiler(b);

        if (comp.isAsync) {
            ExprTy iter = null;
            if (index > 0) {
                iter = comp.iter;
            }
            statementCompiler.emitAsyncFor(iter, comp.target, null, true, index,
                            (stmtComp, idx) -> emitComprehensionBody(generators, idx, collectionLocal, accumulateProducer, stmtComp));
        } else {
            BytecodeLocal localIter = b.createLocal();
            BytecodeLocal localValue = b.createLocal();

            b.beginStoreLocal(localIter);
            if (index == 0) {
                // The iterator is the function argument for the outermost generator
                b.emitLoadArgument(PArguments.USER_ARGUMENTS_OFFSET);
            } else {
                b.beginGetIter();
                comp.iter.accept(statementCompiler);
                b.endGetIter();
            }
            b.endStoreLocal();

            b.beginWhile();

            b.beginBlock();
            b.emitTraceLineAtLoopHeader(currentLocation.startLine);
            b.beginForIterate(localValue);
            b.emitLoadLocal(localIter);
            b.endForIterate();
            b.endBlock();

            b.beginBlock();

            comp.target.accept(statementCompiler.new StoreVisitor(() -> b.emitLoadLocal(localValue)));
            emitComprehensionBody(generators, index, collectionLocal, accumulateProducer, statementCompiler);

            b.endBlock();
            b.endWhile();
        }

        endSourceSection(b, newStatement);
    }

    private void emitComprehensionBody(ComprehensionTy[] generators, int index,
                    BytecodeLocal collectionLocal, BiConsumer<StatementCompiler, BytecodeLocal> accumulateProducer,
                    StatementCompiler statementCompiler) {
        ComprehensionTy comp = generators[index];
        Builder b = statementCompiler.b;
        if (comp.ifs != null) {
            for (int i = 0; i < comp.ifs.length; i++) {
                b.beginIfThen();
                statementCompiler.visitCondition(comp.ifs[i]);
                b.beginBlock();
            }
        }

        if (index == generators.length - 1) {
            accumulateProducer.accept(statementCompiler, collectionLocal);
        } else {
            emitComprehension(generators, index + 1, b, collectionLocal, accumulateProducer);
        }

        if (comp.ifs != null) {
            for (int i = 0; i < len(comp.ifs); i++) {
                b.endBlock();
                b.endIfThen();
            }
        }
    }

    private enum ComprehensionType {
        LIST("<listcomp>"),
        SET("<setcomp>"),
        DICT("<dictcomp>"),
        GENEXPR("<genexpr>");

        private final String name;

        ComprehensionType(String name) {
            this.name = name;
        }
    }

    private BytecodeDSLCompilerResult buildComprehensionCodeUnit(SSTNode node, ComprehensionTy[] generators, ComprehensionType type,
                    Consumer<StatementCompiler> emptyCollectionProducer,
                    BiConsumer<StatementCompiler, BytecodeLocal> accumulateProducer) {
        if (scope.isCoroutine() && type != ComprehensionType.GENEXPR && scopeType != CompilationScope.AsyncFunction && scopeType != CompilationScope.Comprehension) {
            throw ctx.errorCallback.onError(ErrorType.Syntax, currentLocation, "asynchronous comprehension outside of an asynchronous function");
        }
        return compileRootNode(type.name, new ArgumentInfo(1, 0, 0, false, false), node.getSourceRange(), b -> {
            beginRootNode(node, null, b);

            assert scope.isGenerator() == (type == ComprehensionType.GENEXPR);
            if (scope.isCoroutine() || scope.isGenerator()) {
                b.beginResumeYieldGenerator();
                b.emitYieldGenerator();
                b.endResumeYieldGenerator();
            }

            StatementCompiler statementCompiler = new StatementCompiler(b);
            BytecodeLocal collectionLocal = null;
            if (!scope.isGenerator()) {
                collectionLocal = b.createLocal();
                b.beginStoreLocal(collectionLocal);
                emptyCollectionProducer.accept(statementCompiler);
                b.endStoreLocal();
            }

            emitComprehension(generators, 0, b, collectionLocal, accumulateProducer);

            beginReturn(b);
            if (scope.isGenerator()) {
                // TODO: what if someone sends us some value?
                b.emitLoadConstant(PNone.NONE);
            } else {
                b.emitLoadLocal(collectionLocal);
            }
            endReturn(b);

            endRootNode(b);
        });
    }

    @Override
    public BytecodeDSLCompilerResult visit(ExprTy.ListComp node) {
        return buildComprehensionCodeUnit(node, node.generators, ComprehensionType.LIST,
                        (statementCompiler) -> {
                            statementCompiler.b.beginMakeList();
                            statementCompiler.b.endMakeList();
                        },
                        (statementCompiler, collection) -> {
                            statementCompiler.b.beginListAppend();
                            statementCompiler.b.emitLoadLocal(collection);
                            node.element.accept(statementCompiler);
                            statementCompiler.b.endListAppend();
                        });
    }

    @Override
    public BytecodeDSLCompilerResult visit(ExprTy.DictComp node) {
        return buildComprehensionCodeUnit(node, node.generators, ComprehensionType.DICT,
                        (statementCompiler) -> {
                            statementCompiler.b.beginMakeDict(0);
                            statementCompiler.b.endMakeDict();
                        },
                        (statementCompiler, collection) -> {
                            statementCompiler.b.beginSetDictItem();
                            statementCompiler.b.emitLoadLocal(collection);
                            node.key.accept(statementCompiler);
                            node.value.accept(statementCompiler);
                            statementCompiler.b.endSetDictItem();
                        });
    }

    @Override
    public BytecodeDSLCompilerResult visit(ExprTy.SetComp node) {
        return buildComprehensionCodeUnit(node, node.generators, ComprehensionType.SET,
                        (statementCompiler) -> {
                            statementCompiler.b.beginMakeSet();
                            statementCompiler.b.endMakeSet();
                        },
                        (statementCompiler, collection) -> {
                            statementCompiler.b.beginSetAdd();
                            statementCompiler.b.emitLoadLocal(collection);
                            node.element.accept(statementCompiler);
                            statementCompiler.b.endSetAdd();
                        });
    }

    @Override
    public BytecodeDSLCompilerResult visit(ExprTy.GeneratorExp node) {
        return buildComprehensionCodeUnit(node, node.generators, ComprehensionType.GENEXPR,
                        null,
                        (statementCompiler, collection) -> {
                            emitYield((statementCompiler_) -> {
                                boolean isAsync = node.generators[node.generators.length - 1].isAsync;
                                if (isAsync) {
                                    statementCompiler_.b.beginAsyncGenWrap();
                                }
                                node.element.accept(statementCompiler_);
                                if (isAsync) {
                                    statementCompiler_.b.endAsyncGenWrap();
                                }
                            }, statementCompiler);
                        });
    }

    @Override
    public BytecodeDSLCompilerResult visit(TypeAlias node) {
        return null;
    }

    @Override
    public BytecodeDSLCompilerResult visit(TypeVar node) {
        return null;
    }

    @Override
    public BytecodeDSLCompilerResult visit(ParamSpec node) {
        return null;
    }

    @Override
    public BytecodeDSLCompilerResult visit(TypeVarTuple node) {
        return null;
    }

    enum NameOperation {
        Read,
        BeginWrite,
        EndWrite,
        Delete
    }

    private String mangle(String name) {
        return ctx.mangle(scope, name);
    }

    private String mangleAndAddName(String name) {
        String mangled = ctx.mangle(scope, name);
        return addName(mangled);
    }

    /**
     * Use this method for values that should show up in co_consts.
     */
    private void emitPythonConstant(Object constant, Builder b) {
        b.emitLoadConstant(addConstant(constant));
    }

    private boolean inTopMostSaveExceptionBlock() {
        return currentSaveExceptionLocal != null && prevSaveExceptionLocal == null;
    }

    private BytecodeLocal enterSaveExceptionBlock(BytecodeLocal saveExceptionLocal) {
        BytecodeLocal prevPrev = prevSaveExceptionLocal;
        prevSaveExceptionLocal = currentSaveExceptionLocal;
        currentSaveExceptionLocal = saveExceptionLocal;
        return prevPrev;
    }

    private void exitSaveExceptionBlock(BytecodeLocal prevPrev) {
        currentSaveExceptionLocal = prevSaveExceptionLocal;
        prevSaveExceptionLocal = prevPrev;
    }

    /**
     * This helper encapsulates all of the logic needed to yield and resume. Yields should not be
     * emitted directly.
     */
    private void emitYield(Consumer<StatementCompiler> yieldValueProducer, StatementCompiler statementCompiler) {
        // We are doing this dance, because we cannot pass `null` local, so if boths locals are the
        // same, it means by convention that the second one is in fact "null".
        BytecodeLocal savedExLocal = generatorExceptionStateLocal;
        if (inTopMostSaveExceptionBlock()) {
            // If we are in a top most except block, what we saved is caller exception state, so we
            // will need to refresh it on next resume according to the new caller
            savedExLocal = currentSaveExceptionLocal;
        }

        statementCompiler.b.beginResumeYield(generatorExceptionStateLocal, savedExLocal);
        statementCompiler.b.beginYieldValue();
        yieldValueProducer.accept(statementCompiler);
        statementCompiler.b.endYieldValue();
        statementCompiler.b.endResumeYield();
    }

    private void beginSetAttribute(String name, Builder b) {
        String mangled = maybeMangleAndAddName(name);
        b.beginSetAttribute(toTruffleStringUncached(mangled));
    }

    private void beginGetAttribute(String name, Builder b) {
        String mangled = maybeMangleAndAddName(name);
        b.beginGetAttribute(toTruffleStringUncached(mangled));
    }

    private void emitNameCellOperation(String mangled, NameOperation op, Builder b) {
        int index;
        BytecodeLocal local;
        if (freevars.containsKey(mangled)) {
            index = freevars.get(mangled) + cellvars.size();
            local = freeLocals.get(mangled);
        } else {
            index = cellvars.get(mangled);
            local = cellLocals.get(mangled);
        }

        switch (op) {
            case Read:
                if (scope.isClass()) {
                    b.beginLoadFromDictOrCell(index);
                    b.emitLoadSpecialArgument();
                    b.emitLoadLocal(local);
                    b.endLoadFromDictOrCell();
                } else if (scope.canSeeClassScope()) {
                    // __classdict__ should have been added during RootNodeCompiler initialization
                    int classDictIndex = freevars.get("__classdict__");
                    BytecodeLocal classDictLocal = freeLocals.get("__classdict__");
                    // @formatter:off
                    b.beginLoadFromDictOrCell(index);
                        b.beginLoadCell(classDictIndex);
                            b.emitLoadLocal(classDictLocal);
                        b.endLoadCell();
                        b.emitLoadLocal(local);
                    b.endLoadFromDictOrCell();
                    // @formatter:on
                } else {
                    b.beginLoadCell(index);
                    b.emitLoadLocal(local);
                    b.endLoadCell();
                }
                break;
            case Delete:
                b.beginClearCell(index);
                b.emitLoadLocal(local);
                b.endClearCell();
                break;
            case BeginWrite:
                b.beginStoreCell();
                b.emitLoadLocal(local);
                break;
            case EndWrite:
                b.endStoreCell();
                break;
            default:
                throw new UnsupportedOperationException("unknown value: " + op);
        }

    }

    private void emitNameFastOperation(String mangled, NameOperation op, Builder b) {
        BytecodeLocal local = locals.get(mangled);
        switch (op) {
            case Read:
                b.emitCheckAndLoadLocal(local, varnames.get(mangled));
                break;
            case Delete:
                b.emitDeleteLocal(local, varnames.get(mangled));
                break;
            case BeginWrite:
                if (local == null) {
                    throw new NullPointerException("local " + mangled + " not defined");
                }
                b.beginStoreLocal(local);
                break;
            case EndWrite:
                b.endStoreLocal();
                break;
            default:
                throw new UnsupportedOperationException("unknown value: " + op);
        }
    }

    private void emitNameGlobalOperation(String name, NameOperation op, Builder b, boolean isImplicitScope) {
        assert locals.get(name) == null;
        addName(name);
        TruffleString tsName = toTruffleStringUncached(name);
        switch (op) {
            case Read:
                if (scope.canSeeClassScope() && isImplicitScope) {
                    // __classdict__ should have been added during RootNodeCompiler initialization
                    int classDictIndex = freevars.get("__classdict__");
                    BytecodeLocal classDictLocal = freeLocals.get("__classdict__");
                    // @formatter:off
                    b.beginLoadFromDictOrGlobals(tsName);
                        b.beginLoadCell(classDictIndex);
                            b.emitLoadLocal(classDictLocal);
                        b.endLoadCell();
                    b.endLoadFromDictOrGlobals();
                    // @formatter:on
                } else {
                    b.emitReadGlobal(tsName);
                }
                break;
            case Delete:
                b.emitDeleteGlobal(tsName);
                break;
            case BeginWrite:
                b.beginWriteGlobal(tsName);
                break;
            case EndWrite:
                b.endWriteGlobal();
                break;
            default:
                throw new UnsupportedOperationException("unknown value: " + op);
        }
    }

    private String addName(String name) {
        names.putIfAbsent(name, names.size());
        return name;
    }

    private void emitNameSlowOperation(String name, NameOperation op, Builder b) {
        assert locals.get(name) == null;
        addName(name);
        TruffleString tsName = toTruffleStringUncached(name);
        switch (op) {
            case Read:
                b.emitReadName(tsName);
                break;
            case Delete:
                b.emitDeleteName(tsName);
                break;
            case BeginWrite:
                b.beginWriteName(tsName);
                break;
            case EndWrite:
                b.endWriteName();
                break;
            default:
                throw new UnsupportedOperationException("unknown value: " + op);
        }
    }

    private void emitNameOperation(String name, NameOperation op, Builder b) {
        checkForbiddenName(name, op);

        String mangled = maybeMangle(name);
        EnumSet<DefUse> uses = scope.getUseOfName(mangled);

        if (uses != null) {
            if (uses.contains(DefUse.Free)) {
                assert freevars.containsKey(mangled) : String.format("scope analysis did not mark %s as a free variable", mangled);
                emitNameCellOperation(mangled, op, b);
                return;
            } else if (uses.contains(DefUse.Cell)) {
                assert cellvars.containsKey(mangled) : String.format("scope analysis did not mark %s as a cell variable", mangled);
                emitNameCellOperation(mangled, op, b);
                return;
            } else if (uses.contains(DefUse.Local)) {
                if (scope.isFunction()) {
                    assert varnames.containsKey(mangled) : String.format("scope analysis did not mark %s as a regular variable", mangled);
                    emitNameFastOperation(mangled, op, b);
                    return;
                }
            } else if (uses.contains(DefUse.GlobalImplicit)) {
                if (scope.isFunction()) {
                    emitNameGlobalOperation(mangled, op, b, true);
                    return;
                }
            } else if (uses.contains(DefUse.GlobalExplicit)) {
                emitNameGlobalOperation(mangled, op, b, false);
                return;
            }
        }
        emitNameSlowOperation(mangled, op, b);
    }

    private void emitReadLocal(String name, Builder b) {
        emitNameOperation(name, NameOperation.Read, b);
    }

    private void emitDelLocal(String name, Builder b) {
        emitNameOperation(name, NameOperation.Delete, b);
    }

    private void beginStoreLocal(String name, Builder b) {
        emitNameOperation(name, NameOperation.BeginWrite, b);
    }

    private void endStoreLocal(String name, Builder b) {
        emitNameOperation(name, NameOperation.EndWrite, b);
    }

    private BytecodeLocal getLocal(String name) {
        return locals.get(maybeMangle(name));
    }

    public void setUpFrame(ArgumentsTy args, Builder b) {
        /**
         * This method does two things:
         *
         * 1. It allocates a contiguous region in the frame for Python variables. Some nodes in the
         * GraalPy AST expect locals to be allocated contiguously starting at index 0. The resultant
         * frame has the following layout:
         *
         * [var1, var2, ..., cell1, cell2, ..., free1, free2, ..., temp1, temp2, ..., stack]
         *
         * The temp variables are allocated elsewhere during compilation (e.g., to store an
         * intermediate computation) and the stack space is automatically reserved by the DSL.
         *
         * 2. It emits code to copy arguments, initialize cells, and copy free variables.
         */

        // 1. Allocate space in the frame.
        if (scope.isFunction()) {
            String[] regularVariables = orderedKeys(varnames, new String[0]);
            for (int i = 0; i < regularVariables.length; i++) {
                locals.put(regularVariables[i], b.createLocal());
            }
        }

        // 2. Copy arguments, initialize cells, and copy free variables.
        copyArguments(args, b);

        if (!cellvars.isEmpty()) {
            String[] cellVariables = orderedKeys(cellvars, new String[0]);
            BytecodeLocal[] cellVariableLocals = new BytecodeLocal[cellVariables.length];
            for (int i = 0; i < cellVariables.length; i++) {
                BytecodeLocal local = b.createLocal();
                cellLocals.put(cellVariables[i], local);
                cellVariableLocals[i] = local;
            }
            b.emitCreateCells(cellVariableLocals);
            for (int i = 0; i < cellVariables.length; i++) {
                if (scope.getUseOfName(cellVariables[i]).contains(DefUse.DefParam)) {
                    /*
                     * To simplify the argument copying performed above, we copy cell params into
                     * regular locals just like all other arguments. Then, here we move the value
                     * into a cell and clear the regular local.
                     */
                    BytecodeLocal param = getLocal(cellVariables[i]);
                    b.beginStoreCell();
                    b.emitLoadLocal(cellVariableLocals[i]);
                    b.emitLoadLocal(param);
                    b.endStoreCell();
                    b.emitClearLocal(param);
                }
            }
        }

        if (!freevars.isEmpty()) {
            String[] freeVariables = orderedKeys(freevars, new String[0]);
            BytecodeLocal[] freeVariableLocals = new BytecodeLocal[freeVariables.length];
            for (int i = 0; i < freeVariables.length; i++) {
                BytecodeLocal local = b.createLocal();
                freeLocals.put(freeVariables[i], local);
                freeVariableLocals[i] = local;
            }
            b.emitInitFreeVars(freeVariableLocals);
        }

        if (scope.isCoroutine() || scope.isGenerator()) {
            generatorExceptionStateLocal = b.createLocal();
        }

        if (scope.isGeneratorWithYieldFrom() || scope.isCoroutine()) {
            yieldFromGenerator = b.createLocal();
        }
    }

    private void copyArguments(ArgumentsTy args, Builder b) {
        if (args == null) {
            return;
        }

        int idx = 0;
        int posOnlyArgsCount = args.posOnlyArgs != null ? args.posOnlyArgs.length : 0;
        int argsCount = args.args != null ? args.args.length : 0;
        int kwOnlyArgsLength = args.kwOnlyArgs != null ? args.kwOnlyArgs.length : 0;
        int totalLocals = posOnlyArgsCount + argsCount + kwOnlyArgsLength;
        if (totalLocals > 0) {
            BytecodeLocal[] locals = new BytecodeLocal[totalLocals];

            for (int i = 0; i < posOnlyArgsCount; i++) {
                locals[idx++] = getLocal(args.posOnlyArgs[i].arg);
            }

            for (int i = 0; i < argsCount; i++) {
                locals[idx++] = getLocal(args.args[i].arg);
            }

            for (int i = 0; i < kwOnlyArgsLength; i++) {
                locals[idx++] = getLocal(args.kwOnlyArgs[i].arg);
            }

            b.emitCopyArguments(locals);
        }

        if (args.varArg != null) {
            BytecodeLocal local = getLocal(args.varArg.arg);
            assert local != null;
            b.beginStoreLocal(local);
            b.emitLoadVariableArguments(idx++);
            b.endStoreLocal();
        }

        if (args.kwArg != null) {
            BytecodeLocal local = getLocal(args.kwArg.arg);
            assert local != null;
            b.beginStoreLocal(local);
            b.emitLoadKeywordArguments(idx++);
            b.endStoreLocal();
        }

        if (scope.isCoroutine() || scope.isGenerator()) {
            b.emitClearArguments(idx);
        }
    }

    /* ---------------- StatementCompiler -------------------- */

    public class StatementCompiler implements BaseBytecodeDSLVisitor<Void> {
        private final Builder b;

        private BytecodeLabel breakLabel;
        private BytecodeLabel continueLabel;

        public StatementCompiler(Builder b) {
            this.b = b;
        }

        // --------------------- visitor ---------------------------

        @Override
        public Void visit(AliasTy node) {
            throw new UnsupportedOperationException("" + node.getClass());
        }

        @Override
        public Void visit(ArgTy node) {
            throw new UnsupportedOperationException("" + node.getClass());
        }

        @Override
        public Void visit(ArgumentsTy node) {
            throw new UnsupportedOperationException("" + node.getClass());
        }

        @Override
        public Void visit(ComprehensionTy node) {
            throw new UnsupportedOperationException("" + node.getClass());
        }

        @Override
        public Void visit(ExprTy.Attribute node) {
            boolean newStatement = beginSourceSection(node, b);
            beginTraceLineChecked(b);

            beginGetAttribute(node.attr, b);
            node.value.accept(this);
            b.endGetAttribute();

            endTraceLineChecked(node, b);
            endSourceSection(b, newStatement);

            return null;
        }

        @Override
        public Void visit(ExprTy.Await node) {
            // TODO if !IS_TOP_LEVEL_AWAIT
            // TODO handle await in comprehension correctly (currently, it is always allowed)
            if (!scope.isFunction()) {
                ctx.errorCallback.onError(ErrorType.Syntax, currentLocation, "'await' outside function");
            }
            if (scopeType != CompilationScope.AsyncFunction && scopeType != CompilationScope.Comprehension) {
                ctx.errorCallback.onError(ErrorType.Syntax, currentLocation, "'await' outside async function");
            }
            boolean newStatement = beginSourceSection(node, b);
            beginTraceLineChecked(b);
            emitAwait(() -> node.value.accept(this));
            endTraceLineChecked(node, b);
            endSourceSection(b, newStatement);
            return null;
        }

        /**
         * Accepts provided visitor for both left and right subexpression of the provided BinOp
         * node.
         */
        private void acceptBinOpExpressions(ExprTy.BinOp node, SSTreeVisitor<Void> visitor) {
            node.left.accept(visitor);
            beginTraceLineChecked(b);
            node.right.accept(visitor);
            endTraceLineChecked(node, b);
        }

        @Override
        public Void visit(ExprTy.BinOp node) {
            boolean newStatement = beginSourceSection(node, b);
            beginTraceLineChecked(b);
            int startLine = node.getSourceRange().startLine;
            switch (node.op) {
                case Add:
                    b.beginPyNumberAdd();
                    acceptBinOpExpressions(node, this);
                    b.endPyNumberAdd();
                    break;
                case BitAnd:
                    b.beginPyNumberAnd();
                    acceptBinOpExpressions(node, this);
                    b.endPyNumberAnd();
                    break;
                case BitOr:
                    b.beginPyNumberOr();
                    acceptBinOpExpressions(node, this);
                    b.endPyNumberOr();
                    break;
                case BitXor:
                    b.beginPyNumberXor();
                    acceptBinOpExpressions(node, this);
                    b.endPyNumberXor();
                    break;
                case Div:
                    b.beginPyNumberTrueDivide();
                    acceptBinOpExpressions(node, this);
                    b.endPyNumberTrueDivide();
                    break;
                case FloorDiv:
                    b.beginPyNumberFloorDivide();
                    acceptBinOpExpressions(node, this);
                    b.endPyNumberFloorDivide();
                    break;
                case LShift:
                    b.beginPyNumberLshift();
                    acceptBinOpExpressions(node, this);
                    b.endPyNumberLshift();
                    break;
                case MatMult:
                    b.beginPyNumberMatrixMultiply();
                    acceptBinOpExpressions(node, this);
                    b.endPyNumberMatrixMultiply();
                    break;
                case Mod:
                    b.beginPyNumberRemainder();
                    acceptBinOpExpressions(node, this);
                    b.endPyNumberRemainder();
                    break;
                case Mult:
                    b.beginPyNumberMultiply();
                    acceptBinOpExpressions(node, this);
                    b.endPyNumberMultiply();
                    break;
                case Pow:
                    b.beginPow();
                    acceptBinOpExpressions(node, this);
                    b.endPow();
                    break;
                case RShift:
                    b.beginPyNumberRshift();
                    acceptBinOpExpressions(node, this);
                    b.endPyNumberRshift();
                    break;
                case Sub:
                    b.beginPyNumberSubtract();
                    acceptBinOpExpressions(node, this);
                    b.endPyNumberSubtract();
                    break;
                default:
                    throw new UnsupportedOperationException("" + node.getClass());
            }
            endTraceLineChecked(node, b);
            endSourceSection(b, newStatement);
            return null;
        }

        public void acceptBoolOpArgs(ExprTy.BoolOp node) {
            int valueCount = node.values.length;
            ExprTy value = null;
            int i = 0;
            for (; i < valueCount - 1; i++) {
                value = node.values[i];
                beginTraceLineChecked(b);
                value.accept(this);
                endTraceLineChecked(node, b);
            }
            node.values[i].accept(this);
        }

        @Override
        public Void visit(ExprTy.BoolOp node) {
            boolean newStatement = beginSourceSection(node, b);
            emitTraceLineChecked(node, b);

            if (node.op == BoolOpTy.And) {
                b.beginBoolAnd();
            } else {
                b.beginBoolOr();
            }

            acceptBoolOpArgs(node);

            if (node.op == BoolOpTy.And) {
                b.endBoolAnd();
            } else {
                b.endBoolOr();
            }

            endSourceSection(b, newStatement);
            return null;
        }

        private static boolean anyIsStarred(SSTNode[] nodes) {
            for (int i = 0; i < nodes.length; i++) {
                if (nodes[i] instanceof ExprTy.Starred) {
                    return true;
                }
            }

            return false;
        }

        protected final void validateKeywords(KeywordTy[] keywords) {
            for (int i = 0; i < keywords.length; i++) {
                if (keywords[i].arg != null) {
                    checkForbiddenName(keywords[i].arg, NameOperation.BeginWrite);
                    for (int j = i + 1; j < keywords.length; j++) {
                        if (keywords[i].arg.equals(keywords[j].arg)) {
                            ctx.errorCallback.onError(ErrorType.Syntax, currentLocation, "keyword argument repeated: " + keywords[i].arg);
                        }
                    }
                }
            }
        }

        private static boolean isAttributeLoad(ExprTy node) {
            return node instanceof ExprTy.Attribute && ((ExprTy.Attribute) node).context == ExprContextTy.Load;
        }

        private static final int NUM_ARGS_MAX_FIXED = 4;

        private void beginCallNAry(int numArgs) {
            assert numArgs <= NUM_ARGS_MAX_FIXED;
            switch (numArgs) {
                case 0 -> b.beginCallNilaryMethod();
                case 1 -> b.beginCallUnaryMethod();
                case 2 -> b.beginCallBinaryMethod();
                case 3 -> b.beginCallTernaryMethod();
                case 4 -> b.beginCallQuaternaryMethod();
            }
        }

        private void endCallNAry(int numArgs) {
            assert numArgs <= NUM_ARGS_MAX_FIXED;
            switch (numArgs) {
                case 0 -> b.endCallNilaryMethod();
                case 1 -> b.endCallUnaryMethod();
                case 2 -> b.endCallBinaryMethod();
                case 3 -> b.endCallTernaryMethod();
                case 4 -> b.endCallQuaternaryMethod();
            }
        }

        private void visitArguments(ExprTy func, ExprTy[] args, int numArgs) {
            if (numArgs > 0) {
                for (int i = 0; i < numArgs - 1; i++) {
                    args[i].accept(this);
                }
                b.beginTraceLineWithArgument();
                args[numArgs - 1].accept(this);
                b.endTraceLineWithArgument(func.getSourceRange().startLine);
            }
        }

        private void emitCall(ExprTy func, ExprTy[] args, KeywordTy[] keywords) {
            validateKeywords(keywords);

            boolean isMethodCall = isAttributeLoad(func) && keywords.length == 0;
            int numArgs = len(args) + (isMethodCall ? 1 : 0);
            boolean useVariadic = anyIsStarred(args) || len(keywords) > 0 || numArgs > NUM_ARGS_MAX_FIXED;

            // @formatter:off
            if (useVariadic) {
                b.beginCallVarargsMethod();
            } else {
                beginCallNAry(numArgs);
            }

            // @formatter:on

            if (isMethodCall) {
                // The receiver is needed for method lookup and for the first argument.
                BytecodeLocal receiver = b.createLocal();

                if (useVariadic) {
                    BytecodeLocal function = b.createLocal();
                    b.beginBlock();
                    b.beginStoreLocal(function);
                    emitGetMethod(func, receiver);
                    b.endStoreLocal();
                    b.emitLoadLocal(function);
                    b.endBlock();

                    b.beginCollectToObjectArray();
                    emitUnstar(() -> b.emitLoadLocal(receiver), args, null, func);
                    b.endCollectToObjectArray();
                    emitKeywords(keywords, function);
                } else {
                    assert len(keywords) == 0;

                    emitGetMethod(func, receiver);
                    b.emitLoadLocal(receiver); // callable
                    visitArguments(func, args, numArgs - 1);
                }
            } else {
                if (useVariadic) {
                    BytecodeLocal function = b.createLocal();

                    b.beginBlock();
                    b.beginStoreLocal(function);
                    func.accept(this);
                    b.endStoreLocal();
                    b.emitLoadLocal(function);
                    b.endBlock();

                    b.beginCollectToObjectArray();
                    emitUnstar(null, args, null, func);
                    b.endCollectToObjectArray();
                    emitKeywords(keywords, function);
                } else {
                    assert len(keywords) == 0;

                    func.accept(this); // callable
                    visitArguments(func, args, numArgs);
                }
            }

            // @formatter:off
            if (useVariadic) {
                b.endCallVarargsMethod();
            } else {
                endCallNAry(numArgs);
            }
            // @formatter:on
        }

        private void emitGetMethod(ExprTy func, BytecodeLocal receiver) {
            assert isAttributeLoad(func);
            ExprTy.Attribute attrAccess = (ExprTy.Attribute) func;
            b.beginBlock();
            b.beginStoreLocal(receiver);
            attrAccess.value.accept(this);
            b.endStoreLocal();

            String mangled = maybeMangle(attrAccess.attr);
            b.beginGetMethod(toTruffleStringUncached(mangled));
            b.emitLoadLocal(receiver);
            b.endGetMethod();
            b.endBlock();
        }

        @Override
        public Void visit(ExprTy.Call node) {
            boolean newStatement = beginSourceSection(node, b);
            emitTraceLineChecked(node, b);
            checkCaller(ctx.errorCallback, node.func);
            emitCall(node.func, node.args, node.keywords);
            endSourceSection(b, newStatement);
            return null;
        }

        private void beginComparison(CmpOpTy op) {
            switch (op) {
                case Eq:
                    b.beginEq();
                    break;
                case NotEq:
                    b.beginNe();
                    break;
                case Lt:
                    b.beginLt();
                    break;
                case LtE:
                    b.beginLe();
                    break;
                case Gt:
                    b.beginGt();
                    break;
                case GtE:
                    b.beginGe();
                    break;
                case Is:
                    b.beginIs();
                    break;
                case IsNot:
                    b.beginNot();
                    b.beginIs();
                    break;
                case In:
                    b.beginContains();
                    break;
                case NotIn:
                    b.beginNot();
                    b.beginContains();
                    break;
                default:
                    throw new UnsupportedOperationException("" + op);
            }
        }

        private void endComparison(CmpOpTy op) {
            switch (op) {
                case Eq:
                    b.endEq();
                    break;
                case NotEq:
                    b.endNe();
                    break;
                case Lt:
                    b.endLt();
                    break;
                case LtE:
                    b.endLe();
                    break;
                case Gt:
                    b.endGt();
                    break;
                case GtE:
                    b.endGe();
                    break;
                case Is:
                    b.endIs();
                    break;
                case IsNot:
                    b.endIs();
                    b.endNot();
                    break;
                case In:
                    b.endContains();
                    break;
                case NotIn:
                    b.endContains();
                    b.endNot();
                    break;
                default:
                    throw new UnsupportedOperationException("" + op);
            }
        }

        @Override
        public Void visit(ExprTy.Compare node) {
            boolean newStatement = beginSourceSection(node, b);
            checkCompare(ctx.errorCallback, node);

            boolean multipleComparisons = node.comparators.length > 1;

            if (multipleComparisons) {
                b.beginBoolAnd();
            }

            BytecodeLocal tmp = b.createLocal();

            for (int i = 0; i < node.comparators.length; i++) {
                beginTraceLineChecked(b);
                beginComparison(node.ops[i]);

                if (i == 0) {
                    node.left.accept(this);
                } else {
                    b.emitLoadLocal(tmp);
                }

                if (i != node.comparators.length - 1) {
                    b.beginTeeLocal(tmp);
                }
                node.comparators[i].accept(this);
                if (i != node.comparators.length - 1) {
                    b.endTeeLocal();
                }

                endComparison(node.ops[i]);
                endTraceLineChecked(node, b);
            }

            if (multipleComparisons) {
                b.endBoolAnd();
            }

            endSourceSection(b, newStatement);
            return null;
        }

        private void warn(SSTNode node, String message, Object... arguments) {
            ctx.errorCallback.onWarning(WarningType.Syntax, node.getSourceRange(), message, arguments);
        }

        private void createConstant(ConstantValue value) {
            switch (value.kind) {
                case NONE:
                    b.emitLoadConstant(PNone.NONE);
                    break;
                case ELLIPSIS:
                    b.emitLoadConstant(PEllipsis.INSTANCE);
                    break;
                case BOOLEAN:
                    emitPythonConstant(value.getBoolean(), b);
                    break;
                case LONG:
                    emitPythonConstant(getConstantNumber(value.getLong()), b);
                    break;
                case DOUBLE:
                    emitPythonConstant(value.getDouble(), b);
                    break;
                case COMPLEX: {
                    double[] complex = value.getComplex();
                    addConstant(complex);
                    b.emitLoadComplex(complex[0], complex[1]);
                    break;
                }
                case BIGINTEGER:
                    addConstant(value.getBigInteger());
                    b.emitLoadBigInt(value.getBigInteger());
                    break;
                case CODEPOINTS:
                    emitPythonConstant(codePointsToInternedTruffleString(value.getCodePoints()), b);
                    break;
                case BYTES:
                    addConstant(value.getBytes());
                    b.emitLoadBytes(value.getBytes());
                    break;
                case TUPLE:
                    b.beginMakeTuple();
                    for (ConstantValue cv : value.getTupleElements()) {
                        createConstant(cv);
                    }
                    b.endMakeTuple();
                    break;
                case FROZENSET:
                    b.beginMakeFrozenSet();
                    for (ConstantValue cv : value.getFrozensetElements()) {
                        createConstant(cv);
                    }
                    b.endMakeFrozenSet();
                    break;

                default:
                    throw new UnsupportedOperationException("not supported: " + value.kind);
            }
        }

        /**
         * Some AST nodes have type guards expecting ints rather than long. When the actual constant
         * fits into something smaller, convert it accordingly.
         */
        private Object getConstantNumber(long value) {
            if (value == (int) value) {
                return (int) value;
            } else {
                return value;
            }
        }

        @Override
        public Void visit(ExprTy.Constant node) {
            boolean newStatement = beginSourceSection(node, b);
            beginTraceLineChecked(b);
            createConstant(node.value);
            endTraceLineChecked(node, b);
            endSourceSection(b, newStatement);
            return null;
        }

        @Override
        public Void visit(ExprTy.Dict node) {
            boolean newStatement = beginSourceSection(node, b);
            beginTraceLineChecked(b);

            if (len(node.keys) == 0) {
                b.beginMakeDict(0);
                b.endMakeDict();
            } else {
                b.beginMakeDict(node.keys.length);
                for (int i = 0; i < node.keys.length; i++) {
                    if (node.keys[i] == null) {
                        b.emitLoadConstant(PNone.NO_VALUE);
                    } else {
                        node.keys[i].accept(this);
                    }
                    node.values[i].accept(this);
                }
                b.endMakeDict();
            }

            endTraceLineChecked(node, b);
            endSourceSection(b, newStatement);
            return null;
        }

        @Override
        public Void visit(ExprTy.DictComp node) {
            emitMakeAndCallComprehension(node, node.generators, ComprehensionType.DICT);
            return null;
        }

        @Override
        public Void visit(ExprTy.FormattedValue node) {
            boolean newStatement = beginSourceSection(node, b);
            beginTraceLineChecked(b);
            b.beginFormat();

            // @formatter:off
            switch (node.conversion) {
                case 's': b.beginFormatStr(); break;
                case 'r': b.beginFormatRepr(); break;
                case 'a':  b.beginFormatAscii(); break;
                case -1:  break;
                default: throw new UnsupportedOperationException("unknown conversion: " + node.conversion);
            }
            // @formatter:on

            node.value.accept(this);

            // @formatter:off
            switch (node.conversion) {
                case 's': b.endFormatStr(); break;
                case 'r': b.endFormatRepr(); break;
                case 'a':  b.endFormatAscii(); break;
                case -1:  break;
                default: throw new UnsupportedOperationException("unknown conversion: " + node.conversion);
            }
            // @formatter:on

            if (node.formatSpec != null) {
                node.formatSpec.accept(this);
            } else {
                b.emitLoadConstant(StringLiterals.T_EMPTY_STRING);
            }

            b.endFormat();
            endTraceLineChecked(node, b);
            endSourceSection(b, newStatement);

            return null;
        }

        @Override
        public Void visit(ExprTy.GeneratorExp node) {
            emitMakeAndCallComprehension(node, node.generators, ComprehensionType.GENEXPR);
            return null;
        }

        @Override
        public Void visit(ExprTy.IfExp node) {
            boolean newStatement = beginSourceSection(node, b);
            beginTraceLineChecked(b);

            b.beginConditional();
            visitCondition(node.test);
            node.body.accept(this);
            node.orElse.accept(this);
            b.endConditional();

            endTraceLineChecked(node, b);
            endSourceSection(b, newStatement);
            return null;
        }

        @Override
        public Void visit(ExprTy.JoinedStr node) {
            boolean newStatement = beginSourceSection(node, b);
            beginTraceLineChecked(b);

            if (node.values.length == 1) {
                node.values[0].accept(this);
            } else {
                b.beginBuildString(node.values.length);
                visitSequence(node.values);
                b.endBuildString();
            }

            endTraceLineChecked(node, b);
            endSourceSection(b, newStatement);
            return null;
        }

        @Override
        public Void visit(ExprTy.Lambda node) {
            boolean newStatement = beginSourceSection(node, b);
            beginTraceLineChecked(b);
            emitMakeFunction(node, "<lambda>", node.args);
            endTraceLineChecked(node, b);
            endSourceSection(b, newStatement);
            return null;
        }

        @Override
        public Void visit(ExprTy.List node) {
            boolean newStatement = beginSourceSection(node, b);
            beginTraceLineChecked(b);

            ConstantCollection constantCollection = Compiler.tryCollectConstantCollection(node.elements);
            if (constantCollection != null) {
                emitConstantList(constantCollection);
            } else {
                b.beginMakeList();
                emitUnstar(node.elements);
                b.endMakeList();
            }

            endTraceLineChecked(node, b);
            endSourceSection(b, newStatement);
            return null;
        }

        @Override
        public Void visit(ExprTy.ListComp node) {
            emitMakeAndCallComprehension(node, node.generators, ComprehensionType.LIST);
            return null;
        }

        private void emitMakeAndCallComprehension(ExprTy node, ComprehensionTy[] generators, ComprehensionType type) {
            boolean newStatement = beginSourceSection(node, b);
            beginTraceLineChecked(b);
            Scope comprehensionScope = ctx.scopeEnvironment.lookupScope(node);
            if (comprehensionScope.isCoroutine() && type != ComprehensionType.GENEXPR) {
                emitYieldFrom(() -> {
                    // @formatter:off
                    b.beginGetAwaitable();
                        b.beginCallComprehension();
                            emitMakeFunction(node, type.name, COMPREHENSION_ARGS);
                            emitGetIter(generators);
                        b.endCallComprehension();
                    b.endGetAwaitable();
                    // @formatter:on
                });
            } else {
                // @formatter:off
                b.beginCallComprehension();
                    emitMakeFunction(node, type.name, COMPREHENSION_ARGS);
                    emitGetIter(generators);
                b.endCallComprehension();
                // @formatter:on
            }
            endTraceLineChecked(node, b);
            endSourceSection(b, newStatement);
        }

        private void emitGetIter(ComprehensionTy[] generators) {
            if (generators[0].isAsync) {
                b.beginGetAIter();
                generators[0].iter.accept(this);
                b.endGetAIter();
            } else {
                b.beginGetIter();
                generators[0].iter.accept(this);
                b.endGetIter();
            }
        }

        @Override
        public Void visit(ExprTy.Name node) {
            boolean newStatement = beginSourceSection(node, b);
            beginTraceLineChecked(b);
            emitReadLocal(node.id, b);
            endTraceLineChecked(node, b);
            endSourceSection(b, newStatement);
            return null;
        }

        @Override
        public Void visit(ExprTy.NamedExpr node) {
            boolean newStatement = beginSourceSection(node, b);
            beginTraceLineChecked(b);
            b.beginBlock();

            // save expr result to "tmp"
            BytecodeLocal tmp = b.createLocal();
            b.beginStoreLocal(tmp);
            node.value.accept(this);
            b.endStoreLocal();

            node.target.accept(new StoreVisitor(() -> {
                b.emitLoadLocal(tmp);
            }));

            b.emitLoadLocal(tmp);

            b.endBlock();
            endTraceLineChecked(node, b);
            endSourceSection(b, newStatement);
            return null;
        }

        private void emitConstantList(ConstantCollection constantCollection) {
            addConstant(constantCollection.collection);
            switch (constantCollection.elementType) {
                case CollectionBits.ELEMENT_INT:
                    b.emitMakeConstantIntList((int[]) constantCollection.collection);
                    break;
                case CollectionBits.ELEMENT_LONG:
                    b.emitMakeConstantLongList((long[]) constantCollection.collection);
                    break;
                case CollectionBits.ELEMENT_BOOLEAN:
                    b.emitMakeConstantBooleanList((boolean[]) constantCollection.collection);
                    break;
                case CollectionBits.ELEMENT_DOUBLE:
                    b.emitMakeConstantDoubleList((double[]) constantCollection.collection);
                    break;
                case CollectionBits.ELEMENT_OBJECT:
                    b.emitMakeConstantObjectList((Object[]) constantCollection.collection);
                    break;
                default:
                    throw CompilerDirectives.shouldNotReachHere();
            }
        }

        private void emitConstantTuple(ConstantCollection constantCollection) {
            addConstant(constantCollection.collection);
            switch (constantCollection.elementType) {
                case CollectionBits.ELEMENT_INT:
                    b.emitMakeConstantIntTuple((int[]) constantCollection.collection);
                    break;
                case CollectionBits.ELEMENT_LONG:
                    b.emitMakeConstantLongTuple((long[]) constantCollection.collection);
                    break;
                case CollectionBits.ELEMENT_BOOLEAN:
                    b.emitMakeConstantBooleanTuple((boolean[]) constantCollection.collection);
                    break;
                case CollectionBits.ELEMENT_DOUBLE:
                    b.emitMakeConstantDoubleTuple((double[]) constantCollection.collection);
                    break;
                case CollectionBits.ELEMENT_OBJECT:
                    b.emitMakeConstantObjectTuple((Object[]) constantCollection.collection);
                    break;
                default:
                    throw CompilerDirectives.shouldNotReachHere();
            }
        }

        /**
         * Converts a sequence of expressions of which some may be starred into just an Object[].
         *
         * @param args the sequence of expressions
         */
        private void emitUnstar(ExprTy[] args) {
            emitUnstar(null, args);
        }

        private void emitUnstar(Runnable initialElementsProducer, ExprTy[] args) {
            emitUnstar(initialElementsProducer, args, null);
        }

        private void emitUnstar(Runnable initialElementsProducer, ExprTy[] args, Runnable finalElementsProducer) {
            emitUnstar(initialElementsProducer, args, finalElementsProducer, null);
        }

        /**
         * Same as above, but takes an optional Runnable to produce elements at the beginning of the
         * sequence.
         *
         * @param initialElementsProducer a runnable to produce the first element(s) of the
         *            sequence.
         * @param args the sequence of expressions to unstar
         */
        private void emitUnstar(Runnable initialElementsProducer, ExprTy[] args, Runnable finalElementsProducer, ExprTy func) {
            boolean noExtraElements = initialElementsProducer == null && finalElementsProducer == null;
            if (noExtraElements && len(args) == 0) {
                /**
                 * We don't need to emit anything for an empty array.
                 */
            } else if (noExtraElements && len(args) == 1 && args[0] instanceof ExprTy.Starred) {
                // Optimization for single starred argument: we can just upack it. For generic
                // algorithm see the next branch
                b.beginUnpackStarredVariadic();
                ((ExprTy.Starred) args[0]).value.accept(this);
                b.endUnpackStarredVariadic();
            } else if (anyIsStarred(args)) {
                /**
                 * We emit one or more arrays. These are not concatenated directly, but rather
                 * expect that the caller is receiving them into @Variadic annotated argument, as that handles
                 * the concatenation. Each array corresponds to a contiguous sequence of arguments or the result
                 * of unpacking a single starred argument.
                 *
                 * For example, for the argument list a, b, *c, d, e, *f, g we would emit:
                 *
                 * @formatter:off
                 *   a,
                 *   b,
                 *   UnpackStarredVariadic(c),
                 *   d,
                 *   e,
                 *   UnpackStarredVariadic(f),
                 *   g
                 * @formatter:on
                 *
                 * CollectObjectToArray is no longer necessary, as the UnpackStarredVariadic return @Variadic.
                 */
                if (initialElementsProducer != null) {
                    initialElementsProducer.run();
                }

                for (int i = 0; i < args.length; i++) {
                    if (args[i] instanceof ExprTy.Starred) {
                        b.beginUnpackStarredVariadic();
                        ((ExprTy.Starred) args[i]).value.accept(this);
                        b.endUnpackStarredVariadic();
                    } else {
                        args[i].accept(this);
                    }
                }

                if (finalElementsProducer != null) {
                    finalElementsProducer.run();
                }
            } else {
                if (initialElementsProducer != null) {
                    initialElementsProducer.run();
                }
                if (func != null) {
                    visitArguments(func, args, args.length);
                } else {
                    visitSequence(args);
                }
                if (finalElementsProducer != null) {
                    finalElementsProducer.run();
                }
            }
        }

        @Override
        public Void visit(ExprTy.Set node) {
            boolean newStatement = beginSourceSection(node, b);
            beginTraceLineChecked(b);
            b.beginMakeSet();
            if (len(node.elements) != 0) {
                emitUnstar(node.elements);
            }
            b.endMakeSet();
            endTraceLineChecked(node, b);
            endSourceSection(b, newStatement);
            return null;
        }

        @Override
        public Void visit(ExprTy.SetComp node) {
            emitMakeAndCallComprehension(node, node.generators, ComprehensionType.SET);
            return null;
        }

        private void visitNoneable(ExprTy node) {
            if (node == null) {
                b.emitLoadConstant(PNone.NONE);
            } else {
                node.accept(this);
            }
        }

        @Override
        public Void visit(ExprTy.Slice node) {
            boolean newStatement = beginSourceSection(node, b);
            beginTraceLineChecked(b);

            b.beginMakeSlice();

            visitNoneable(node.lower);
            visitNoneable(node.upper);
            visitNoneable(node.step);

            b.endMakeSlice();

            endTraceLineChecked(node, b);
            endSourceSection(b, newStatement);
            return null;
        }

        @Override
        public Void visit(ExprTy.Starred node) {
            throw ctx.errorCallback.onError(ErrorType.Syntax, currentLocation, "can't use starred expression here");
        }

        @Override
        public Void visit(ExprTy.Subscript node) {
            boolean newStatement = beginSourceSection(node, b);
            beginTraceLineChecked(b);
            if (node.context == ExprContextTy.Load) {
                checkSubscripter(ctx.errorCallback, node.value);
                checkIndex(ctx.errorCallback, node.value, node.slice);
            }
            b.beginBinarySubscript();
            node.value.accept(this);
            node.slice.accept(this);
            b.endBinarySubscript();

            endTraceLineChecked(node, b);
            endSourceSection(b, newStatement);
            return null;
        }

        @Override
        public Void visit(ExprTy.Tuple node) {
            boolean newStatement = beginSourceSection(node, b);
            beginTraceLineChecked(b);

            ConstantCollection constantCollection = Compiler.tryCollectConstantCollection(node.elements);
            if (constantCollection != null) {
                emitConstantTuple(constantCollection);
            } else {
                b.beginMakeTuple();
                emitUnstar(node.elements);
                b.endMakeTuple();
            }

            endTraceLineChecked(node, b);
            endSourceSection(b, newStatement);
            return null;
        }

        @Override
        public Void visit(ExprTy.UnaryOp node) {
            // Basic constant folding for unary negation
            if (node.op == UnaryOpTy.USub && node.operand instanceof ExprTy.Constant c) {
                if (c.value.kind == ConstantValue.Kind.BIGINTEGER || c.value.kind == ConstantValue.Kind.DOUBLE || c.value.kind == ConstantValue.Kind.LONG ||
                                c.value.kind == ConstantValue.Kind.COMPLEX) {
                    ConstantValue cv = c.value.negate();
                    boolean newStatement = beginSourceSection(node, b);
                    beginTraceLineChecked(b);
                    visit(new ExprTy.Constant(cv, null, c.getSourceRange()));
                    endTraceLineChecked(node, b);
                    endSourceSection(b, newStatement);
                    return null;
                }
            }
            boolean newStatement = beginSourceSection(node, b);
            beginTraceLineChecked(b);
            switch (node.op) {
                case UAdd:
                    b.beginPyNumberPositive();
                    node.operand.accept(this);
                    b.endPyNumberPositive();
                    break;
                case Invert:
                    b.beginPyNumberInvert();
                    node.operand.accept(this);
                    b.endPyNumberInvert();
                    break;
                case USub:
                    b.beginPyNumberNegative();
                    node.operand.accept(this);
                    b.endPyNumberNegative();
                    break;
                case Not:
                    b.beginNot();
                    node.operand.accept(this);
                    b.endNot();
                    break;
                default:
                    throw new UnsupportedOperationException("" + node.getClass());
            }

            endTraceLineChecked(node, b);
            endSourceSection(b, newStatement);
            return null;
        }

        @Override
        public Void visit(ExprTy.Yield node) {
            boolean newStatement = beginSourceSection(node, b);
            beginTraceLineChecked(b);
            if (!scope.isFunction()) {
                ctx.errorCallback.onError(ErrorType.Syntax, currentLocation, "'yield' outside function");
            }
            emitYield((statementCompiler) -> {
                if (scopeType == CompilationScope.AsyncFunction) {
                    b.beginAsyncGenWrap();
                }
                if (node.value != null) {
                    node.value.accept(this);
                } else {
                    statementCompiler.b.emitLoadConstant(PNone.NONE);
                }
                if (scopeType == CompilationScope.AsyncFunction) {
                    b.endAsyncGenWrap();
                }
            }, this);

            endTraceLineChecked(node, b);
            endSourceSection(b, newStatement);
            return null;
        }

        @Override
        public Void visit(ExprTy.YieldFrom node) {
            if (!scope.isFunction()) {
                ctx.errorCallback.onError(ErrorType.Syntax, currentLocation, "'yield from' outside function");
            }
            if (scopeType == CompilationScope.AsyncFunction) {
                ctx.errorCallback.onError(ErrorType.Syntax, currentLocation, "'yield from' inside async function");
            }
            boolean newStatement = beginSourceSection(node, b);
            beginTraceLineChecked(b);
            emitYieldFrom(() -> {
                b.beginGetYieldFromIter();
                node.value.accept(this);
                b.endGetYieldFromIter();
            });
            endTraceLineChecked(node, b);
            endSourceSection(b, newStatement);
            return null;
        }

        public void emitYieldFrom(Runnable generatorOrCoroutineProducer) {
            b.beginBlock();
            BytecodeLocal returnValue = b.createLocal();
            emitYieldFrom(generatorOrCoroutineProducer, returnValue);
            b.emitLoadLocal(returnValue);
            b.endBlock();
        }

        public void emitYieldFrom(Runnable generatorOrCoroutineProducer, BytecodeLocal returnValue) {
            /**
             * Runs a yield from loop - getting values from a generator and yielding them until
             * the generator ends by throwing StopIteration. The return value (value field in
             * the StopIteration exception) is stored into the given {@code BytecodeLocal}.
             *
             * @formatter:off
             * generator = <value>
             * returnValue = None
             * sentValue = None
             *
             * # Step 1: prime the generator
             * try:
             *   yieldValue = generator.send(sentValue)
             * except StopIteration as e:
             *   returnValue = e.value
             *   goto end
             *
             * while True:
             *   # Step 2: yield yieldValue to the caller
             *   try:
             *     sentValue = yield yieldValue
             *   except Exception as e:
             *     # throw/close generator
             *     if generator returned a value:
             *       goto end
             *     else:
             *       continue (generator yielded a value)
             *
             *   # Step 3: send sentValue into the generator
             *   try:
             *     yieldValue = generator.send(sentValue)
             *   except StopIteration as e:
             *     returnValue = e.value
             *     goto end
             *
             * end: # Step 4: resultValue local is assigned
             * @formatter:on
             */
            BytecodeLocal generator = b.createLocal();
            BytecodeLocal sentValue = b.createLocal();
            BytecodeLocal yieldValue = b.createLocal();
            b.beginBlock();
            BytecodeLabel end = b.createLabel();

            b.beginStoreLocal(generator);
            generatorOrCoroutineProducer.run();
            b.endStoreLocal();

            assert yieldFromGenerator != null;
            b.beginStoreLocal(yieldFromGenerator);
            b.emitLoadLocal(generator);
            b.endStoreLocal();

            b.beginStoreLocal(returnValue);
            b.emitLoadConstant(PNone.NONE);
            b.endStoreLocal();

            b.beginStoreLocal(sentValue);
            b.emitLoadConstant(PNone.NONE);
            b.endStoreLocal();

            // Step 1: prime the generator
            emitSend(generator, sentValue, yieldValue, returnValue, end);

            b.beginWhile();
            b.emitLoadConstant(true);

            b.beginBlock();
            BytecodeLabel loopEnd = b.createLabel();
            // Step 2: yield yieldValue to the caller
            b.beginTryCatch();

            // try clause: yield
            b.beginStoreLocal(sentValue);
            emitYield((statementCompiler) -> statementCompiler.b.emitLoadLocal(yieldValue), this);
            b.endStoreLocal();

            // catch clause: handle throw/close exceptions.
            b.beginIfThenElse();
            b.beginYieldFromThrow(yieldValue, returnValue);
            b.emitLoadLocal(generator);
            b.emitLoadException();
            b.endYieldFromThrow();

            // StopIteration was raised; go to the end.
            b.emitBranch(end);

            // The generator yielded a value; go to top of the loop.
            b.emitBranch(loopEnd);

            b.endIfThenElse();

            b.endTryCatch();

            // Step 3: send sentValue into the generator
            emitSend(generator, sentValue, yieldValue, returnValue, end);

            b.emitLabel(loopEnd);
            b.endBlock();
            b.endWhile();

            // Step 4: the returnValue local is assigned when branching to "end" label
            b.emitLabel(end);
            b.beginStoreLocal(yieldFromGenerator);
            b.emitLoadNull();
            b.endStoreLocal();
            b.endBlock();
        }

        private void emitSend(BytecodeLocal generator, BytecodeLocal sentValue, BytecodeLocal yieldValue, BytecodeLocal returnValue, BytecodeLabel end) {
            b.beginIfThen();
            // When the generator raises StopIteration, send evaluates to true; branch to the end.
            b.beginYieldFromSend(yieldValue, returnValue);
            b.emitLoadLocal(generator);
            b.emitLoadLocal(sentValue);
            b.endYieldFromSend();

            b.emitBranch(end);

            b.endIfThen();
        }

        private void emitAwait(Runnable producer) {
            emitYieldFrom(() -> {
                b.beginGetAwaitable();
                producer.run();
                b.endGetAwaitable();
            });
        }

        @Override
        public Void visit(KeywordTy node) {
            throw new UnsupportedOperationException("" + node.getClass());
        }

        @Override
        public Void visit(StmtTy.AnnAssign node) {
            boolean newStatement = beginSourceSection(node, b);
            b.beginBlock();
            if (node.value != null) {
                // Emit the assignment if there's an RHS.
                emitAssignment(new ExprTy[]{node.target}, node.value);
            }
            if (node.target instanceof ExprTy.Name) {
                String name = ((ExprTy.Name) node.target).id;
                checkForbiddenName(name, NameOperation.BeginWrite);
                /* If we have a simple name in a module or class, store annotation. */
                if (node.isSimple &&
                                (scopeType == CompilationScope.Module || scopeType == CompilationScope.Class)) {
                    b.beginSetItem();

                    if (futureFeatures.contains(FutureFeature.ANNOTATIONS)) {
                        emitPythonConstant(Unparser.unparse(node.annotation), b);
                    } else {
                        node.annotation.accept(this);
                    }

                    emitNameOperation("__annotations__", NameOperation.Read, b);

                    String mangled = maybeMangle(name);
                    emitPythonConstant(toTruffleStringUncached(mangled), b);

                    b.endSetItem();
                }
            } else if (node.target instanceof ExprTy.Attribute) {
                if (node.value == null) {
                    ExprTy.Attribute attr = (ExprTy.Attribute) node.target;
                    checkForbiddenName(attr.attr, NameOperation.BeginWrite);
                    if (attr.value != null) {
                        checkAnnExpr(attr.value);
                    }
                }
            } else if (node.target instanceof ExprTy.Subscript) {
                if (node.value == null) {
                    ExprTy.Subscript subscript = (ExprTy.Subscript) node.target;
                    if (subscript.value != null) {
                        checkAnnExpr(subscript.value);
                    }
                    checkAnnSubscr(subscript.slice);
                }
            } else {
                ctx.errorCallback.onError(ErrorType.Syntax, node.getSourceRange(), "invalid node type for annotated assignment");
            }
            if (!node.isSimple) {
                /*
                 * Annotations of complex targets does not produce anything under annotations
                 * future. Annotations are only evaluated in a module or class.
                 */
                if (!futureFeatures.contains(FutureFeature.ANNOTATIONS) && (scopeType == CompilationScope.Module || scopeType == CompilationScope.Class)) {
                    checkAnnExpr(node.annotation);
                }
            }
            b.endBlock();
            emitTraceLineChecked(node, b);
            endSourceSection(b, newStatement);
            return null;
        }

        private void checkAnnExpr(ExprTy expr) {
            expr.accept(this);
        }

        private void checkAnnSubscr(ExprTy expr) {
            if (expr instanceof ExprTy.Slice) {
                ExprTy.Slice slice = (ExprTy.Slice) expr;
                if (slice.lower != null) {
                    checkAnnExpr(slice.lower);
                }
                if (slice.upper != null) {
                    checkAnnExpr(slice.upper);
                }
                if (slice.step != null) {
                    checkAnnExpr(slice.step);
                }
            } else if (expr instanceof ExprTy.Tuple) {
                ExprTy.Tuple tuple = (ExprTy.Tuple) expr;
                for (int i = 0; i < tuple.elements.length; i++) {
                    checkAnnSubscr(tuple.elements[i]);
                }
            } else {
                checkAnnExpr(expr);
            }
        }

        @Override
        public Void visit(StmtTy.Assert node) {
            if (ctx.optimizationLevel <= 0) {
                boolean newStatement = beginSourceSection(node, b);
                b.beginIfThen();

                b.beginNot();
                node.test.accept(this);
                b.endNot();

                b.beginAssertFailed();
                if (node.msg == null) {
                    b.emitLoadConstant(PNone.NO_VALUE);
                } else {
                    node.msg.accept(this);
                }
                b.endAssertFailed();

                b.endIfThen();
                emitTraceLineChecked(node, b);
                endSourceSection(b, newStatement);
            }
            return null;
        }

        /**
         * Produces a list or tuple containing the type parameters. Each type parameter may also
         * store to some local variables/cells depending on its semantics.
         */
        public void visitTypeParams(TypeParamTy[] typeParams) {
            boolean useList = typeParams.length > CollectionBits.KIND_MASK;
            if (useList) {
                b.beginMakeList();
            } else {
                b.beginMakeTuple();
            }
            for (TypeParamTy typeParam : typeParams) {
                typeParam.accept(this);
            }
            if (useList) {
                b.endMakeList();
            } else {
                b.endMakeTuple();
            }
        }

        // --------------------- assign ------------------------

        /**
         * Generates code to store the value produced by {@link #generateValue} into the visited
         * expression.
         */
        public class StoreVisitor implements BaseBytecodeDSLVisitor<Void> {
            private final Builder b = StatementCompiler.this.b;
            private final Runnable generateValue;

            StoreVisitor(Runnable generateValue) {
                this.generateValue = generateValue;
            }

            @Override
            public Void visit(ExprTy.Name node) {
                boolean newStatement = beginSourceSection(node, b);
                emitTraceLineChecked(node, b);
                beginStoreLocal(node.id, b);
                generateValue.run();
                endStoreLocal(node.id, b);
                endSourceSection(b, newStatement);
                return null;
            }

            @Override
            public Void visit(ExprTy.Attribute node) {
                boolean newStatement = beginSourceSection(node, b);
                emitTraceLineChecked(node, b);
                checkForbiddenName(node.attr, NameOperation.BeginWrite);
                beginSetAttribute(node.attr, b);
                generateValue.run();
                node.value.accept(StatementCompiler.this);
                b.endSetAttribute();
                endSourceSection(b, newStatement);
                return null;
            }

            @Override
            public Void visit(ExprTy.Subscript node) {
                boolean newStatement = beginSourceSection(node, b);
                emitTraceLineChecked(node, b);
                b.beginSetItem();
                generateValue.run();
                node.value.accept(StatementCompiler.this);
                node.slice.accept(StatementCompiler.this);
                b.endSetItem();
                endSourceSection(b, newStatement);
                return null;
            }

            /**
             * This method unpacks the rhs (a sequence/iterable) to the elements on the lhs
             * (specified by {@code nodes}.
             */
            private void visitIterableAssign(ExprTy[] nodes) {
                b.beginBlock();

                /**
                 * The rhs should be fully evaluated and unpacked into the expected number of
                 * elements before storing values into the lhs (e.g., if an lhs element is f().attr,
                 * but computing or unpacking rhs throws, f() is not computed). Thus, the unpacking
                 * step stores the unpacked values into intermediate variables, and then those
                 * variables are copied into the lhs elements afterward.
                 */
                BytecodeLocal[] targets = new BytecodeLocal[nodes.length];
                for (int i = 0; i < targets.length; i++) {
                    targets[i] = b.createLocal();
                }

                int indexOfStarred = -1;
                for (int i = 0; i < nodes.length; i++) {
                    if (nodes[i] instanceof ExprTy.Starred) {
                        indexOfStarred = i;
                        break;
                    }
                }

                if (indexOfStarred == -1) {
                    b.beginUnpackToLocals(targets);
                } else {
                    b.beginUnpackStarredToLocals(indexOfStarred, targets);
                }

                generateValue.run();

                if (indexOfStarred == -1) {
                    b.endUnpackToLocals();
                } else {
                    b.endUnpackStarredToLocals();
                }

                for (int i = 0; i < nodes.length; i++) {
                    final int index = i;

                    ExprTy target = nodes[i];
                    if (nodes[i] instanceof ExprTy.Starred) {
                        target = ((ExprTy.Starred) target).value;
                    }

                    target.accept(new StoreVisitor(() -> {
                        b.emitLoadLocal(targets[index]);
                    }));
                }

                b.endBlock();
            }

            @Override
            public Void visit(ExprTy.Tuple node) {
                boolean newStatement = beginSourceSection(node, b);
                emitTraceLineChecked(node, b);
                visitIterableAssign(node.elements);
                endSourceSection(b, newStatement);
                return null;
            }

            @Override
            public Void visit(TypeAlias node) {
                return null;
            }

            @Override
            public Void visit(TypeVar node) {
                return null;
            }

            @Override
            public Void visit(ParamSpec node) {
                return null;
            }

            @Override
            public Void visit(TypeVarTuple node) {
                return null;
            }

            @Override
            public Void visit(ExprTy.List node) {
                boolean newStatement = beginSourceSection(node, b);
                emitTraceLineChecked(node, b);
                visitIterableAssign(node.elements);
                endSourceSection(b, newStatement);
                return null;
            }
        }

        private class AugStoreVisitor implements BaseBytecodeDSLVisitor<Void> {
            private final Builder b = StatementCompiler.this.b;
            private final ExprTy value;
            private final OperatorTy op;

            AugStoreVisitor(OperatorTy op, ExprTy value) {
                this.op = op;
                this.value = value;
            }

            private void beginAugAssign() {
                switch (op) {
                    case Add -> b.beginPyNumberInPlaceAdd();
                    case Sub -> b.beginPyNumberInPlaceSubtract();
                    case Mult -> b.beginPyNumberInPlaceMultiply();
                    case FloorDiv -> b.beginPyNumberInPlaceFloorDivide();
                    case BitAnd -> b.beginPyNumberInPlaceAnd();
                    case BitOr -> b.beginPyNumberInPlaceOr();
                    case BitXor -> b.beginPyNumberInPlaceXor();
                    case RShift -> b.beginPyNumberInPlaceRshift();
                    case LShift -> b.beginPyNumberInPlaceLshift();
                    case Div -> b.beginPyNumberInPlaceTrueDivide();
                    case Mod -> b.beginPyNumberInPlaceRemainder();
                    case MatMult -> b.beginPyNumberInPlaceMatrixMultiply();
                    case Pow -> b.beginInPlacePow();
                    default -> throw new UnsupportedOperationException("aug ass: " + op);
                }
            }

            private void endAugAssign() {
                switch (op) {
                    case Add -> b.endPyNumberInPlaceAdd();
                    case Sub -> b.endPyNumberInPlaceSubtract();
                    case Mult -> b.endPyNumberInPlaceMultiply();
                    case FloorDiv -> b.endPyNumberInPlaceFloorDivide();
                    case BitAnd -> b.endPyNumberInPlaceAnd();
                    case BitOr -> b.endPyNumberInPlaceOr();
                    case BitXor -> b.endPyNumberInPlaceXor();
                    case RShift -> b.endPyNumberInPlaceRshift();
                    case LShift -> b.endPyNumberInPlaceLshift();
                    case Div -> b.endPyNumberInPlaceTrueDivide();
                    case Mod -> b.endPyNumberInPlaceRemainder();
                    case MatMult -> b.endPyNumberInPlaceMatrixMultiply();
                    case Pow -> b.endInPlacePow();
                    default -> throw new UnsupportedOperationException("aug ass: " + op);
                }
            }

            @Override
            public Void visit(ExprTy.Name node) {
                boolean newStatement = beginSourceSection(node, b);
                emitTraceLineChecked(node, b);

                beginStoreLocal(node.id, b);
                beginAugAssign();
                emitReadLocal(node.id, b);
                value.accept(StatementCompiler.this);
                endAugAssign();
                endStoreLocal(node.id, b);

                endSourceSection(b, newStatement);
                return null;
            }

            @Override
            public Void visit(TypeVar node) {
                return null;
            }

            @Override
            public Void visit(ParamSpec node) {
                return null;
            }

            @Override
            public Void visit(TypeVarTuple node) {
                return null;
            }

            @Override
            public Void visit(TypeAlias node) {
                return null;
            }

            @Override
            public Void visit(ExprTy.Attribute node) {
                boolean newStatement = beginSourceSection(node, b);
                emitTraceLineChecked(node, b);
                b.beginBlock();
                // {
                BytecodeLocal target = b.createLocal();

                b.beginStoreLocal(target);
                node.value.accept(StatementCompiler.this);
                b.endStoreLocal();

                beginSetAttribute(node.attr, b);
                beginAugAssign();

                beginGetAttribute(node.attr, b);
                b.emitLoadLocal(target);
                b.endGetAttribute();

                value.accept(StatementCompiler.this);

                endAugAssign();

                b.emitLoadLocal(target);
                b.endSetAttribute();
                // }
                b.endBlock();
                endSourceSection(b, newStatement);
                return null;
            }

            @Override
            public Void visit(ExprTy.Subscript node) {
                boolean newStatement = beginSourceSection(node, b);
                emitTraceLineChecked(node, b);
                b.beginBlock();
                // {
                BytecodeLocal target = b.createLocal();
                BytecodeLocal slice = b.createLocal();

                b.beginStoreLocal(target);
                node.value.accept(StatementCompiler.this);
                b.endStoreLocal();

                b.beginStoreLocal(slice);
                node.slice.accept(StatementCompiler.this);
                b.endStoreLocal();

                b.beginSetItem();
                beginAugAssign();

                b.beginBinarySubscript();
                b.emitLoadLocal(target);
                b.emitLoadLocal(slice);
                b.endBinarySubscript();

                value.accept(StatementCompiler.this);

                endAugAssign();

                b.emitLoadLocal(target);
                b.emitLoadLocal(slice);
                b.endSetItem();
                // }
                b.endBlock();
                endSourceSection(b, newStatement);
                return null;
            }
        }

        @Override
        public Void visit(StmtTy.Assign node) {
            boolean newStatement = beginSourceSection(node, b);
            b.beginBlock();
            emitAssignment(node.targets, node.value);
            b.endBlock();
            emitTraceLineChecked(node, b);
            endSourceSection(b, newStatement);
            return null;
        }

        private void emitAssignment(ExprTy[] targets, ExprTy value) {
            if (targets.length == 1) {
                targets[0].accept(new StoreVisitor(() -> {
                    value.accept(this);
                }));
            } else {
                BytecodeLocal tmp = b.createLocal();
                b.beginStoreLocal(tmp);
                value.accept(this);
                b.endStoreLocal();

                for (ExprTy target : targets) {
                    target.accept(new StoreVisitor(() -> {
                        b.emitLoadLocal(tmp);
                    }));
                }
            }
        }

        @Override
        public Void visit(StmtTy.AsyncFor node) {
            if (!scope.isFunction()) {
                ctx.errorCallback.onError(ErrorType.Syntax, currentLocation, "'async for' outside function");
            }
            if (scopeType != CompilationScope.AsyncFunction) {
                ctx.errorCallback.onError(ErrorType.Syntax, currentLocation, "'async for' outside async function");
            }
            boolean newStatement = beginSourceSection(node, b);
            emitAsyncFor(node.iter, node.target, node.orElse, false, node, (stmtCompiler, n) -> {
                stmtCompiler.visitSequence(n.body);
            });
            endSourceSection(b, newStatement);
            return null;
        }

        /**
         * @param iterOrNull If {@code null}, then it assumes that the first argument holds the
         *            iterator, i.e., it won't call {@code __aiter__} on it and just use it as is.
         *            This is the calling convention for async comprehensions.
         */
        private <T> void emitAsyncFor(ExprTy iterOrNull, ExprTy target, StmtTy[] orElse, boolean isComprehension,
                        T arg, BiConsumer<StatementCompiler, T> body) {
            assert !isComprehension || orElse == null;
            BytecodeLocal iterLocal = b.createLocal();
            b.beginStoreLocal(iterLocal);
            if (iterOrNull == null) {
                b.emitLoadArgument(PArguments.USER_ARGUMENTS_OFFSET);
            } else {
                b.beginGetAIter();
                iterOrNull.accept(this);
                b.endGetAIter();
            }
            b.endStoreLocal();

            b.beginBlock();
            BytecodeLocal result = b.createLocal();
            BytecodeLabel loopEnd = b.createLabel();
            BytecodeLabel currentBreakLabel = null;
            BytecodeLabel oldContinueLabel = continueLabel;
            BytecodeLabel oldBreakLabel = breakLabel;
            if (!isComprehension) {
                currentBreakLabel = b.createLabel();
                breakLabel = currentBreakLabel;
            }
            // @formatter:off
            b.beginWhile();
                // infinite loop, we break out of it explicitly by jump to "loopEnd"
                b.emitLoadConstant(true);
                // body:
                b.beginBlock();
                    if (!isComprehension) {
                        continueLabel = b.createLabel();
                    }
                    target.accept(new StoreVisitor(() -> {
                        b.beginBlock();
                            b.beginTryCatch();
                                // try:
                                emitYieldFrom(() -> {
                                    b.beginGetANext();
                                        b.emitLoadLocal(iterLocal);
                                    b.endGetANext();
                                }, result);
                                // catch:
                                b.beginBlock();
                                    // rethrows the exception unless its StopAsyncIteration
                                    b.beginExpectStopAsyncIteration();
                                        b.emitLoadException();
                                    b.endExpectStopAsyncIteration();
                                    b.emitBranch(loopEnd);
                                b.endBlock();
                            b.endTryCatch();
                            b.emitLoadLocal(result);
                        b.endBlock();
                    }));
                    body.accept(this, arg);
                    if (!isComprehension) {
                        b.emitLabel(continueLabel);
                    }
                b.endBlock();
            b.endWhile();
            b.emitLabel(loopEnd);
            if (!isComprehension) {
                visitSequence(orElse);
                b.emitLabel(currentBreakLabel);
                breakLabel = oldBreakLabel;
                continueLabel = oldContinueLabel;
            }
            b.endBlock();
            // @formatter:on
        }

        @Override
        public Void visit(StmtTy.AsyncWith node) {
            if (!scope.isFunction()) {
                ctx.errorCallback.onError(ErrorType.Syntax, currentLocation, "'async with' outside function");
            }
            if (scopeType != CompilationScope.AsyncFunction && scopeType != CompilationScope.Comprehension) {
                ctx.errorCallback.onError(ErrorType.Syntax, currentLocation, "'async with' outside async function");
            }
            boolean newStatement = beginSourceSection(node, b);
            visitWithRecurse(node.items, 0, node.body, true);
            emitTraceLineChecked(node, b);
            endSourceSection(b, newStatement);
            return null;
        }

        @Override
        public Void visit(StmtTy.AugAssign node) {
            boolean newStatement = beginSourceSection(node, b);
            node.target.accept(new AugStoreVisitor(node.op, node.value));
            emitTraceLineChecked(node, b);
            endSourceSection(b, newStatement);
            return null;
        }

        private abstract static sealed class KeywordGroup permits NamedKeywords, SplatKeywords {
        }

        private static final class NamedKeywords extends KeywordGroup {
            final ArrayList<TruffleString> names;
            final ArrayList<ExprTy> values;

            NamedKeywords(ArrayList<TruffleString> names, ArrayList<ExprTy> values) {
                this.names = names;
                this.values = values;
            }
        }

        private static final class SplatKeywords extends KeywordGroup {
            final ExprTy expr;

            SplatKeywords(ExprTy expr) {
                this.expr = expr;
            }
        }

        private void emitKeywords(KeywordTy[] kws, BytecodeLocal function) {
            if (len(kws) == 0) {
                b.emitLoadConstant(PKeyword.EMPTY_KEYWORDS);
            } else {
                KeywordGroup[] groups = partitionKeywords(kws);
                // The nodes that validate keyword arguments operate on PDicts, so we convert into
                // a list of PKeywords after validation.
                b.beginMappingToKeywords();
                emitKeywordsRecursive(groups, groups.length - 1, function);
                b.endMappingToKeywords();
            }
        }

        private KeywordGroup[] partitionKeywords(KeywordTy[] kws) {
            ArrayList<KeywordGroup> groups = new ArrayList<>();

            int i = 0;
            while (i < kws.length) {
                if (kws[i].arg == null) {
                    // splat
                    groups.add(new SplatKeywords(kws[i].value));
                    i++;
                } else {
                    // named keyword
                    ArrayList<TruffleString> kwNames = new ArrayList<>();
                    ArrayList<ExprTy> kwValues = new ArrayList<>();
                    while (i < kws.length && kws[i].arg != null) {
                        kwNames.add(toTruffleStringUncached(kws[i].arg));
                        kwValues.add(kws[i].value);
                        i++;
                    }
                    groups.add(new NamedKeywords(kwNames, kwValues));
                }
            }

            return groups.toArray(KeywordGroup[]::new);
        }

        private void emitKeywordsRecursive(KeywordGroup[] groups, int i, BytecodeLocal function) {
            /*
             * Keyword groups should be merged left-to-right. For example, for groups [A, B, C] we
             * should emit KwArgsMerge(KwArgsMerge(A, B), C).
             */
            if (i == 0) {
                emitKeywordGroup(groups[i], true, function);
            } else {
                b.beginKwargsMerge(function);
                emitKeywordsRecursive(groups, i - 1, function);
                emitKeywordGroup(groups[i], false, function);
                b.endKwargsMerge();
            }
        }

        private void emitKeywordGroup(KeywordGroup group, boolean copy, BytecodeLocal function) {
            if (group instanceof NamedKeywords namedKeywords) {
                b.beginMakeDict(namedKeywords.names.size());
                for (int i = 0; i < namedKeywords.names.size(); i++) {
                    emitPythonConstant(namedKeywords.names.get(i), b);
                    namedKeywords.values.get(i).accept(this);
                }
                b.endMakeDict();
            } else {
                SplatKeywords splatKeywords = (SplatKeywords) group;

                if (copy) {
                    b.beginKwargsMerge(function);
                    b.beginMakeDict(0);
                    b.endMakeDict();
                    splatKeywords.expr.accept(this);
                    b.endKwargsMerge();
                } else {
                    splatKeywords.expr.accept(this);
                }
            }
        }

        @Override
        public Void visit(StmtTy.ClassDef node) {
            // We need to differentiate between building a plain class or class with type parameters
            // For type parameters the root node compiler produces intermediate code unit that will
            // assemble the generic parameters and then call __build_class__ and we just need to
            // call that code unit
            BytecodeLocal[] decoratorsLocals = evaluateDecorators(node.decoratorList);
            boolean newStatement = beginSourceSection(node, b);
            emitTraceLineChecked(node, b);

            beginStoreLocal(node.name, b);

            if (node.decoratorList != null && node.decoratorList.length > 0) {
                // needs to emit line before return (that will also move the return)
                beginTraceLineChecked(b);
            }

            beginWrapWithDecorators(decoratorsLocals);
            if (node.isGeneric()) {
                RootNodeCompiler typeParamsCompiler = new RootNodeCompiler(ctx, RootNodeCompiler.this, node.name, node, node.typeParams, futureFeatures);
                RootNodeCompiler classBodyCompiler = createRootNodeCompilerFor(node, typeParamsCompiler);
                BytecodeDSLCompilerResult classBody = classBodyCompiler.compileClassDefBody(node);
                BytecodeDSLCompilerResult typeParamsFun = typeParamsCompiler.compileClassTypeParams(node, classBody.codeUnit());

                b.beginCallNilaryMethod();
                String typeParamsName = "<generic parameters of " + node.name + ">";
                emitMakeFunction(typeParamsFun.codeUnit(), node.typeParams, typeParamsName, null, null);
                b.endCallNilaryMethod();
            } else {
                BytecodeDSLCompilerResult classBody = createRootNodeCompilerFor(node).compileClassDefBody(node);
                emitBuildClass(classBody.codeUnit(), node);
            }
            endWrapWithDecorators(decoratorsLocals, node.decoratorList);

            if (node.decoratorList != null && node.decoratorList.length > 0) {
                // needs to emit line before return (that will also move the return)
                endTraceLineChecked(node, b);
            }

            endStoreLocal(node.name, b);
            endSourceSection(b, newStatement);
            return null;
        }

        /**
         * Produces the result of {@code __build_class__} builtin.
         */
        private void emitBuildClass(BytecodeDSLCodeUnit body, ClassDef node) {
            b.beginBlock();

            if (node.isGeneric()) {
                beginStoreLocal(".type_params", b);
                visitTypeParams(node.typeParams);
                endStoreLocal(".type_params", b);
            }

            BytecodeLocal buildClassFunction = b.createLocal();

            // compute __build_class__ (we need it in multiple places, so store it)
            b.beginStoreLocal(buildClassFunction);
            b.emitLoadBuildClass();
            b.endStoreLocal();

            b.beginCallVarargsMethod();
            b.emitLoadLocal(buildClassFunction);

            Runnable finalElements = null;
            if (node.isGeneric()) {
                finalElements = () -> {
                    // call "make generic" operation, store the result to .generic_base and also
                    // emit it as one of the unstarred arguments
                    // @formatter:off
                    b.beginBlock();
                        beginStoreLocal(".generic_base", b);
                            b.beginMakeGeneric();
                                emitReadLocal(".type_params", b);
                            b.endMakeGeneric();
                        endStoreLocal(".generic_base", b);
                        emitReadLocal(".generic_base", b);
                    b.endBlock();
                    // @formatter:on
                };
            }

            // positional args
            b.beginCollectToObjectArray();
            emitUnstar(() -> {
                emitMakeFunction(body, node, node.name, null, null);
                emitPythonConstant(toTruffleStringUncached(node.name), b);
            }, node.bases, finalElements);
            b.endCollectToObjectArray();

            // keyword args
            validateKeywords(node.keywords);
            emitKeywords(node.keywords, buildClassFunction);

            b.endCallVarargsMethod();
            b.endBlock();
        }

        private class DeleteVisitor implements BaseBytecodeDSLVisitor<Void> {

            @Override
            public Void visit(ExprTy.Subscript node) {
                boolean newStatement = beginSourceSection(node, b);
                emitTraceLineChecked(node, b);

                b.beginDeleteItem();
                node.value.accept(StatementCompiler.this);
                node.slice.accept(StatementCompiler.this);
                b.endDeleteItem();

                endSourceSection(b, newStatement);
                return null;
            }

            @Override
            public Void visit(ExprTy.Attribute node) {
                boolean newStatement = beginSourceSection(node, b);
                emitTraceLineChecked(node, b);
                b.beginDeleteAttribute(toTruffleStringUncached(maybeMangleAndAddName(node.attr)));
                node.value.accept(StatementCompiler.this);
                b.endDeleteAttribute();

                endSourceSection(b, newStatement);
                return null;
            }

            @Override
            public Void visit(ExprTy.Name node) {
                boolean newStatement = beginSourceSection(node, b);
                emitTraceLineChecked(node, b);
                emitNameOperation(node.id, NameOperation.Delete, b);
                endSourceSection(b, newStatement);
                return null;
            }

            @Override
            public Void visit(ExprTy.Tuple node) {
                boolean newStatement = beginSourceSection(node, b);
                emitTraceLineChecked(node, b);
                b.beginBlock();
                visitSequence(node.elements);
                b.endBlock();
                endSourceSection(b, newStatement);
                return null;
            }

            @Override
            public Void visit(TypeAlias node) {
                return null;
            }

            @Override
            public Void visit(TypeVar node) {
                return null;
            }

            @Override
            public Void visit(ParamSpec node) {
                return null;
            }

            @Override
            public Void visit(TypeVarTuple node) {
                return null;
            }

            @Override
            public Void visit(ExprTy.List node) {
                boolean newStatement = beginSourceSection(node, b);
                emitTraceLineChecked(node, b);
                b.beginBlock();
                visitSequence(node.elements);
                b.endBlock();
                endSourceSection(b, newStatement);
                return null;
            }
        }

        @Override
        public Void visit(StmtTy.Delete node) {
            new DeleteVisitor().visitSequence(node.targets);
            return null;
        }

        @Override
        public Void visit(StmtTy.Expr node) {
            boolean newStatement = beginSourceSection(node, b);
            if (isInteractive) {
                b.beginPrintExpr();
                node.value.accept(this);
                b.endPrintExpr();
            } else if (!(node.value instanceof ExprTy.Constant)) {
                node.value.accept(this);
            }
            emitTraceLineChecked(node, b);
            endSourceSection(b, newStatement);

            return null;
        }

        @Override
        public Void visit(StmtTy.For node) {
            // @formatter:off
            // iter = GetIter(<<iter>>); value;
            // while (ForIterate(iter, &value)) {
            //   store value
            //   <<body>>
            //   continueLabel:
            // }
            // <<elses>
            // breakLabel:
            // @formatter:on
            boolean newStatement = beginSourceSection(node, b);
            emitTraceLineChecked(node, b);
            boolean saveInExceptStar = inExceptStar;
            inExceptStar = false;
            b.beginBlock();

            BytecodeLocal iter = b.createLocal();

            b.beginStoreLocal(iter);
            b.beginGetIter();
            node.iter.accept(this);
            b.endGetIter();
            b.endStoreLocal();

            BytecodeLabel oldBreakLabel = breakLabel;
            BytecodeLabel oldContinueLabel = continueLabel;

            BytecodeLabel currentBreakLabel = b.createLabel();
            breakLabel = currentBreakLabel;

            b.beginWhile();
            BytecodeLocal value = b.createLocal();

            // condition
            b.beginBlock();
            b.emitTraceLineAtLoopHeader(currentLocation.startLine);
            b.beginForIterate(value);
            b.emitLoadLocal(iter);
            b.endForIterate();
            b.endBlock();

            // body
            b.beginBlock();
            continueLabel = b.createLabel();
            node.target.accept(new StoreVisitor(() -> {
                b.emitLoadLocal(value);
            }));

            visitSequence(node.body);
            b.emitLabel(continueLabel);
            b.endBlock();

            b.endWhile();

            breakLabel = oldBreakLabel;
            continueLabel = oldContinueLabel;
            visitSequence(node.orElse);
            b.emitLabel(currentBreakLabel);

            b.endBlock();
            endSourceSection(b, newStatement);
            inExceptStar = saveInExceptStar;
            return null;
        }

        @Override
        public Void visit(StmtTy.FunctionDef node) {
            emitFunctionDef(node, node.name, node.args, node.body, node.decoratorList, node.returns, node.typeParams);
            return null;
        }

        public void emitFunctionDef(StmtTy node, String name, ArgumentsTy args, StmtTy[] body, ExprTy[] decoratorList, ExprTy returns, TypeParamTy[] typeParams) {
            BytecodeLocal[] decoratorLocals = evaluateDecorators(decoratorList);
            boolean newStatement = beginSourceSection(node, b);
            emitTraceLineChecked(node, b);

            beginStoreLocal(name, b);
            if (decoratorList != null && decoratorList.length > 0) {
                // needs to emit line before return (that will also move the return)
                b.beginTraceLineWithArgument();
            }
            beginWrapWithDecorators(decoratorLocals);

            boolean isGeneric = typeParams != null && typeParams.length > 0;
            if (isGeneric) {
                // The values of default positional and keyword arguments must be passed as
                // arguments to the "type parameters" code unit, because we must eveluate them
                // already here
                int argsCount = 0;
                if (hasDefaultArgs(args)) {
                    argsCount++;
                }
                if (hasDefaultKwargs(args)) {
                    argsCount++;
                }
                beginCallNAry(argsCount);

                RootNodeCompiler typeParamsCompiler = new RootNodeCompiler(ctx, RootNodeCompiler.this, null, node, typeParams, futureFeatures);
                RootNodeCompiler funBodyCompiler = createRootNodeCompilerFor(node, typeParamsCompiler);
                BytecodeDSLCompilerResult funBodyUnit = funBodyCompiler.compileFunctionDef(node, name, args, body);
                BytecodeDSLCompilerResult typeParamsFunUnit = typeParamsCompiler.compileFunctionTypeParams(funBodyUnit.codeUnit(), node, name, args, returns, typeParams);

                String typeParamsName = "<generic parameters of " + name + ">";
                emitMakeFunction(typeParamsFunUnit.codeUnit(), typeParams, typeParamsName, null, null);

                if (hasDefaultArgs(args)) {
                    emitDefaultArgsArray(args);
                }
                if (hasDefaultKwargs(args)) {
                    emitDefaultKwargsArray(args);
                }

                endCallNAry(argsCount);
            } else {
                BytecodeDSLCompilerResult funBodyCodeUnit = createRootNodeCompilerFor(node).compileFunctionDef(node, name, args, body);
                emitBuildFunction(funBodyCodeUnit.codeUnit(), node, name, args, decoratorList, returns);
            }

            endWrapWithDecorators(decoratorLocals, decoratorList);
            if (decoratorList != null && decoratorList.length > 0) {
                // needs to emit line before return (that will also move the return)
                b.endTraceLineWithArgument(node.getSourceRange().startLine);
            }
            endStoreLocal(name, b);
            endSourceSection(b, newStatement);
        }

        private void emitDefaultArgsArray(ArgumentsTy args) {
            if (hasDefaultArgs(args)) {
                b.beginCollectToObjectArray();
                for (int i = 0; i < args.defaults.length; i++) {
                    args.defaults[i].accept(this);
                }
                b.endCollectToObjectArray();
            } else {
                b.emitLoadConstant(PythonUtils.EMPTY_OBJECT_ARRAY);
            }
        }

        private void emitDefaultKwargsArray(ArgumentsTy args) {
            // We only emit keywords with default values. Check if any exist.
            if (!hasDefaultKwargs(args)) {
                b.emitLoadConstant(PKeyword.EMPTY_KEYWORDS);
            } else {
                ArgTy[] kwOnlyArgs = args.kwOnlyArgs;

                List<TruffleString> keys = new ArrayList<>();
                b.beginMakeKeywords();
                for (int i = 0; i < args.kwDefaults.length; i++) {
                    // Only emit keywords with default values.
                    if (args.kwDefaults[i] != null) {
                        keys.add(toTruffleStringUncached(mangle(kwOnlyArgs[i].arg)));
                        args.kwDefaults[i].accept(this);
                    }
                }
                b.endMakeKeywords(keys.toArray(new TruffleString[0]));
            }
        }

        /**
         * Produces the function object.
         */
        public void emitBuildFunction(BytecodeDSLCodeUnit codeUnit, StmtTy node, String name, ArgumentsTy args, ExprTy[] decoratorList, ExprTy returns) {
            List<ParamAnnotation> annotations = collectParamAnnotations(args, returns);
            emitMakeFunction(codeUnit, node, name, args, annotations);
        }

        /**
         * Evaluates the decorator expressions and stores them in bytecode locals that are returned.
         */
        public BytecodeLocal[] evaluateDecorators(ExprTy[] decorators) {
            int numDeco = len(decorators);
            BytecodeLocal[] locals = new BytecodeLocal[numDeco];
            for (int i = 0; i < locals.length; i++) {
                BytecodeLocal local = locals[i] = b.createLocal();
                b.beginStoreLocal(local);
                decorators[i].accept(this);
                b.endStoreLocal();
            }
            return locals;
        }

        /**
         * Emits the "opening parentheses" of expression {@code decorator1( decoractor2( ... (
         * {value} )) ... )}.
         */
        public void beginWrapWithDecorators(BytecodeLocal[] locals) {
            for (int i = 0; i < locals.length; i++) {
                b.beginCallUnaryMethod();
                b.emitLoadLocal(locals[i]);
                beginTraceLineChecked(b);
            }
        }

        /**
         * "Closing parentheses" for {@link #beginWrapWithDecorators(BytecodeLocal[])}.
         */
        public void endWrapWithDecorators(BytecodeLocal[] locals, ExprTy[] decorators) {
            for (int i = 0; i < locals.length; i++) {
                // we need to trace line in opposite direction -> decorator calls are nested and so
                // they will "flip"
                // w.r.t. original decorator ordering, but tracings won't, so we need to flip them
                // manually
                endTraceLineChecked(decorators[locals.length - 1 - i], b);
                b.endCallUnaryMethod();
            }
        }

        @Override
        public Void visit(StmtTy.AsyncFunctionDef node) {
            emitFunctionDef(node, node.name, node.args, node.body, node.decoratorList, node.returns, node.typeParams);
            return null;
        }

        private void emitParamAnnotation(ParamAnnotation paramAnnotation) {
            emitPythonConstant(paramAnnotation.name, b);

            if (futureFeatures.contains(FutureFeature.ANNOTATIONS)) {
                emitPythonConstant(Unparser.unparse(paramAnnotation.annotation), b);
            } else {
                if (paramAnnotation.annotation instanceof ExprTy.Starred starred) {
                    // *args: *Ts (where Ts is a TypeVarTuple).
                    // Do [annotation_value] = [*Ts].
                    b.beginBlock();
                    BytecodeLocal local = b.createLocal();
                    b.beginUnpackToLocals(new BytecodeLocal[]{local});
                    starred.value.accept(this);
                    b.endUnpackToLocals();
                    b.emitLoadLocal(local);
                    b.endBlock();
                } else {
                    paramAnnotation.annotation.accept(this);
                }
            }
        }

        private void emitMakeFunction(SSTNode node, String name, ArgumentsTy args) {
            BytecodeDSLCompilerResult compilerResult = compileNode(node);
            BytecodeDSLCodeUnit codeUnit = compilerResult.codeUnit();
            emitMakeFunction(codeUnit, node, name, args, null);
        }

        private void emitMakeFunction(BytecodeDSLCodeUnit codeUnit, Object scopeKey, String name,
                        ArgumentsTy args, List<ParamAnnotation> annotations) {
            emitMakeFunction(codeUnit, scopeKey, name, null, null, args, annotations);
        }

        /**
         * Emits "make function" operation, which takes:
         * <ul>
         * <li>Array of default arguments' values. The value is loaded from {@code defaultArgsLocal}
         * if not {@code null}, otherwise the value is generated inline.</li>
         * <li>Array of default keyword arguments' values. The value is loaded from
         * {@code defaultArgsLocal} if not {@code null}, otherwise the value is generated
         * inline.</li>
         * <li>Array of cells created from freevars. This method emits the array inline.</li>
         * <li>Dictionary with annotations. This method emits the dictionary creation from the
         * values passed in the {@code annotations} argument.</li>
         * </ul>
         */
        private void emitMakeFunction(BytecodeDSLCodeUnit codeUnit, Object scopeKey, String name,
                        BytecodeLocal defaultArgsLocal, BytecodeLocal defaultKwargsLocal,
                        ArgumentsTy argsForDefaults, List<ParamAnnotation> annotations) {
            TruffleString functionName = toTruffleStringUncached(name);
            Scope targetScope = ctx.scopeEnvironment.lookupScope(scopeKey);
            TruffleString qualifiedName = codeUnit.qualname;

            // Register these in the Python constants list.
            addConstant(codeUnit);

            b.beginMakeFunction(functionName, qualifiedName, new BytecodeDSLCodeUnitAndRoot(codeUnit));

            if (defaultArgsLocal != null) {
                assert argsForDefaults == null;
                b.emitLoadLocal(defaultArgsLocal);
            } else {
                emitDefaultArgsArray(argsForDefaults);
            }

            if (defaultKwargsLocal != null) {
                assert argsForDefaults == null;
                b.emitLoadLocal(defaultKwargsLocal);
            } else {
                emitDefaultKwargsArray(argsForDefaults);
            }

            if (codeUnit.freevars.length == 0) {
                b.emitLoadNull();
            } else {
                b.beginMakeCellArray();
                for (int i = 0; i < codeUnit.freevars.length; i++) {
                    String fv = codeUnit.freevars[i].toJavaStringUncached();
                    BytecodeLocal local;
                    if ((scopeType == CompilationScope.Class && ("__class__".equals(fv) || "__classdict__".equals(fv))) || scope.getUseOfName(fv).contains(Scope.DefUse.Cell)) {
                        local = cellLocals.get(fv);
                    } else {
                        local = freeLocals.get(fv);
                    }
                    b.emitLoadLocal(local);
                }
                b.endMakeCellArray();
            }

            // __annotations__
            if (annotations != null && annotations.size() > 0) {
                b.beginMakeDict(annotations.size());
                for (ParamAnnotation annotation : annotations) {
                    emitParamAnnotation(annotation);
                }
                b.endMakeDict();
            } else {
                b.emitLoadNull();
            }

            b.endMakeFunction();
        }

        private BytecodeDSLCompilerResult compileNode(SSTNode node) {
            return createRootNodeCompilerFor(node).compile();
        }

        private RootNodeCompiler createRootNodeCompilerFor(SSTNode node) {
            return new RootNodeCompiler(ctx, RootNodeCompiler.this, node, futureFeatures);
        }

        private RootNodeCompiler createRootNodeCompilerFor(SSTNode node, RootNodeCompiler parent) {
            return new RootNodeCompiler(ctx, RootNodeCompiler.this, node, futureFeatures);
        }

        @Override
        public Void visit(StmtTy.Global node) {
            return null;
        }

        private void visitStatements(StmtTy[] stmts) {
            b.beginBlock();
            if (stmts != null) {
                for (StmtTy stmt : stmts) {
                    stmt.accept(this);
                }
            }
            b.endBlock();
        }

        @Override
        public Void visit(StmtTy.If node) {
            boolean newStatement = beginSourceSection(node, b);
            emitTraceLineChecked(node, b);
            if (node.orElse == null || node.orElse.length == 0) {
                b.beginIfThen();
                visitCondition(node.test);
                visitStatements(node.body);
                b.endIfThen();
            } else {
                b.beginIfThenElse();
                visitCondition(node.test);
                visitStatements(node.body);
                visitStatements(node.orElse);
                b.endIfThenElse();
            }

            endSourceSection(b, newStatement);
            return null;
        }

        private boolean producesBoolean(ExprTy node) {
            // NB: Binary and/or operations evaluate to their operands, which are not necessarily
            // booleans.
            return node instanceof ExprTy.UnaryOp unOp && unOp.op == UnaryOpTy.Not ||
                            node instanceof ExprTy.Constant c && c.value.kind == Kind.BOOLEAN;
        }

        private void visitCondition(ExprTy node) {
            boolean mayNeedCoercion = !producesBoolean(node);
            if (mayNeedCoercion) {
                b.beginYes();
            }

            node.accept(this);

            if (mayNeedCoercion) {
                b.endYes();
            }
        }

        @Override
        public Void visit(StmtTy.Import node) {
            boolean newStatement = beginSourceSection(node, b);
            emitTraceLineChecked(node, b);
            b.beginBlock();

            for (AliasTy name : node.names) {
                addConstant(PythonUtils.EMPTY_TRUFFLESTRING_ARRAY);
                if (name.asName == null) {
                    // import a.b.c
                    // --> a = (Import "a.b.c" [] 0)
                    // import a
                    // --> a = (Import "a" [] 0)
                    String resName = name.name.contains(".")
                                    ? name.name.substring(0, name.name.indexOf('.'))
                                    : name.name;

                    beginStoreLocal(resName, b);
                    b.emitImport(toTruffleStringUncached(name.name), PythonUtils.EMPTY_TRUFFLESTRING_ARRAY, 0);
                    endStoreLocal(resName, b);
                } else {
                    // import a.b.c as x
                    // --> x = (ImportFrom (ImportFrom (Import "a.b.c" [] 0) "b") "c")
                    // import a as x
                    // --> x = (Import "a" [] 0)
                    String[] parts = name.name.split("\\.");

                    beginStoreLocal(name.asName, b);

                    for (int i = parts.length - 1; i >= 0; i--) {
                        if (i != 0) {
                            b.beginImportFrom(toTruffleStringUncached(parts[i]));
                        } else {
                            b.emitImport(toTruffleStringUncached(name.name), PythonUtils.EMPTY_TRUFFLESTRING_ARRAY, 0);
                        }
                    }

                    for (int i = 1; i < parts.length; i++) {
                        b.endImportFrom();
                    }

                    endStoreLocal(name.asName, b);
                }
            }

            b.endBlock();
            endSourceSection(b, newStatement);
            return null;
        }

        @Override
        public Void visit(StmtTy.ImportFrom node) {
            boolean newStatement = beginSourceSection(node, b);
            emitTraceLineChecked(node, b);
            if (node.getSourceRange().startLine > ctx.futureLineNumber && "__future__".equals(node.module)) {
                ctx.errorCallback.onError(ErrorType.Syntax, node.getSourceRange(), "from __future__ imports must occur at the beginning of the file");
            }

            String moduleName = addName(node.module == null ? "" : node.module);
            TruffleString tsModuleName = toTruffleStringUncached(moduleName);

            if (node.names[0].name.equals("*")) {
                b.emitImportStar(tsModuleName, node.level);
            } else {
                b.beginBlock();

                BytecodeLocal module = b.createLocal();

                TruffleString[] fromList = new TruffleString[node.names.length];
                for (int i = 0; i < fromList.length; i++) {
                    fromList[i] = toTruffleStringUncached(node.names[i].name);
                }

                b.beginStoreLocal(module);
                b.emitImport(tsModuleName, fromList, node.level);
                b.endStoreLocal();

                TruffleString[] importedNames = new TruffleString[node.names.length];
                for (int i = 0; i < node.names.length; i++) {
                    AliasTy alias = node.names[i];
                    addName(alias.name);
                    String asName = alias.asName == null ? alias.name : alias.asName;
                    beginStoreLocal(asName, b);

                    TruffleString name = toTruffleStringUncached(alias.name);
                    importedNames[i] = name;
                    b.beginImportFrom(name);
                    b.emitLoadLocal(module);
                    b.endImportFrom();

                    endStoreLocal(asName, b);
                }
                addConstant(importedNames);

                b.endBlock();
            }

            endSourceSection(b, newStatement);
            return null;
        }

        @Override
        public Void visit(StmtTy.Match node) {
            boolean newStatement = beginSourceSection(node, b);
            emitTraceLineChecked(node, b);
            b.beginBlock();
            // Compute and store the subject in a local.
            BytecodeLocal subject = b.createLocal();
            b.beginStoreLocal(subject);
            node.subject.accept(this);
            b.endStoreLocal();

            visitMatchCaseRecursively(node.cases, 0, new PatternContext(subject));

            b.endBlock();
            endSourceSection(b, newStatement);
            return null;
        }

        private final class PatternContext {
            private final Map<String, BytecodeLocal> bindVariables = new HashMap<>();
            private final BytecodeLocal subject;
            private boolean allowIrrefutable = false;

            PatternContext(BytecodeLocal subject) {
                this.subject = subject;
            }

            public void copySubjectToTemporary(String name) {
                BytecodeLocal temporary = allocateBindVariable(name);
                b.beginStoreLocal(temporary);
                b.emitLoadLocal(subject);
                b.endStoreLocal();
            }

            private BytecodeLocal allocateBindVariable(String name) {
                checkForbiddenName(name, NameOperation.BeginWrite);
                if (bindVariables.containsKey(name)) {
                    duplicateStoreError(name);
                }
                BytecodeLocal result = b.createLocal();
                bindVariables.put(name, result);
                return result;
            }

            private void duplicateStoreError(String name) {
                ctx.errorCallback.onError(ErrorType.Syntax, currentLocation, "multiple assignments to name '%s' in pattern", name);
            }

        }

        private void visitMatchCaseRecursively(MatchCaseTy[] cases, int index, PatternContext pc) {
            /**
             * Cases are chained as a sequence of if-then-else clauses, as in:
             *
             * @formatter:off
             * IfThenElse(
             *   <case 1 condition>,
             *   <case 1 body>,
             *   IfThenElse(
             *     <case 2 condition>,
             *     <case 2 body>,
             *     ...
             *   )
             * )
             * @formatter:on
             */
            MatchCaseTy c = cases[index];
            boolean newStatement = beginSourceSection(c, b);
            emitTraceLineChecked(cases[index], b);

            if (index != cases.length - 1) {
                b.beginIfThenElse();

                // A case that isn't last can be irrefutable only if it is guarded.
                pc.allowIrrefutable = c.guard != null;

                emitPatternCondition(c, pc);
                visitStatements(c.body);
                pc.bindVariables.clear();
                visitMatchCaseRecursively(cases, index + 1, pc);
                b.endIfThenElse();
            } else {
                /**
                 * For the last pattern: if it's an unguarded wildcard _, just emit the body.
                 * Otherwise, emit an IfThen (no else).
                 */
                if (wildcardCheck(c.pattern) && c.guard == null) {
                    visitStatements(c.body);
                } else {
                    b.beginIfThen();

                    // The last case can be irrefutable.
                    pc.allowIrrefutable = true;

                    emitPatternCondition(c, pc);
                    visitStatements(c.body);
                    b.endIfThen();
                }
            }

            endSourceSection(b, newStatement);
        }

        private void emitPatternCondition(MatchCaseTy currentCase, PatternContext pc) {
            PatternTy pattern = currentCase.pattern;
            ExprTy guard = currentCase.guard;

            /**
             * We evaluate conditions using a sequence of boolean computations chained with
             * short-circuiting ANDs. If a condition fails at any point, we abort and continue with
             * the next pattern.
             *
             * Patterns can bind variables, but a variable is only bound if the full pattern
             * matches. We accumulate the bound values into temporary variables and copy them all
             * over only if the pattern matches. For example:
             *
             * @formatter:off
             * IfThenElse(
             *   And(
             *     <pattern checks, which bind temp_1, ..., temp_n>,
             *     Block(
             *       <copy temp_1 into var_1>
             *       ...
             *       <copy temp_n into var_n>,
             *       true   // continue unconditionally
             *     ),
             *     <guard, if exists>
             *   ),
             *   <case body>,
             *   ...
             * )
             * @formatter:on
             */
            b.beginPrimitiveBoolAnd();

            visitPattern(pattern, pc);

            if (!pc.bindVariables.isEmpty()) {
                b.beginBlock();

                for (Map.Entry<String, BytecodeLocal> entry : pc.bindVariables.entrySet()) {
                    beginStoreLocal(entry.getKey(), b);
                    b.emitLoadLocal(entry.getValue());
                    endStoreLocal(entry.getKey(), b);
                }

                b.emitLoadConstant(true);
                b.endBlock();
            }
            if (guard != null) {
                visitCondition(guard);
            }
            b.endPrimitiveBoolAnd();
        }

        /**
         * Generates code to test a {@code pattern} against the value stored in {@code subject}.
         * <p>
         * Invariants:
         * <ul>
         * <li>The code for each pattern produces a boolean value.
         * <li>When the pattern has a variable binding, the code will use the {@code pc} to allocate
         * a new temporary variable to store the value of the binding. If the pattern match
         * succeeds, only then will we copy the temporaries into Python-level variables.
         * <li>The {@code pc.subject} variable always contains the value to match against a pattern.
         * When performing structural recursion on a value, the original value will be overwritten
         * unless saved in a new local.
         * </ul>
         */
        private void visitPattern(PatternTy pattern, PatternContext pc) {
            boolean newStatement = beginSourceSection(pattern, b);
            emitTraceLineChecked(pattern, b);
            if (pattern instanceof PatternTy.MatchAs matchAs) {
                doVisitPattern(matchAs, pc);
            } else if (pattern instanceof PatternTy.MatchClass matchClass) {
                doVisitPattern(matchClass, pc);
            } else if (pattern instanceof PatternTy.MatchMapping matchMapping) {
                doVisitPattern(matchMapping, pc);
            } else if (pattern instanceof PatternTy.MatchOr matchOr) {
                doVisitPattern(matchOr, pc);
            } else if (pattern instanceof PatternTy.MatchSequence matchSequence) {
                doVisitPattern(matchSequence, pc);
            } else if (pattern instanceof PatternTy.MatchSingleton matchSingleton) {
                doVisitPattern(matchSingleton, pc);
            } else if (pattern instanceof PatternTy.MatchStar matchStar) {
                doVisitPattern(matchStar, pc);
            } else if (pattern instanceof PatternTy.MatchValue matchValue) {
                doVisitPattern(matchValue, pc);
            } else {
                throw CompilerDirectives.shouldNotReachHere();
            }
            endSourceSection(b, newStatement);
        }

        // In a subpattern, irrefutable patterns are OK.
        private void visitSubpattern(PatternTy pattern, PatternContext pc) {
            boolean allowIrrefutable = pc.allowIrrefutable;
            pc.allowIrrefutable = true;
            visitPattern(pattern, pc);
            pc.allowIrrefutable = allowIrrefutable;
        }

        private void doVisitPattern(PatternTy.MatchAs node, PatternContext pc) {
            b.beginBlock();
            if (node.name != null) {
                pc.copySubjectToTemporary(node.name);
            }

            if (node.pattern == null) {
                // If there's no pattern (e.g., _), it trivially matches. Ensure this is permitted.
                if (!pc.allowIrrefutable) {
                    if (node.name != null) {
                        ctx.errorCallback.onError(ErrorType.Syntax, currentLocation, "name capture '%s' makes remaining patterns unreachable", node.name);
                    }
                    ctx.errorCallback.onError(ErrorType.Syntax, currentLocation, "wildcard makes remaining patterns unreachable");
                }
                b.emitLoadConstant(true);
            } else {
                assert node.name != null : "name should only be null for the empty wildcard pattern '_'";
                visitPattern(node.pattern, pc);
            }

            b.endBlock();
        }

        /**
         * Saves subject of the pattern context into BytecodeLocal variable, to be restored
         * eventually.
         *
         * @param pc Pattern context, which subject needs to be saved.
         * @return Subject saved in local variable.
         */
        private BytecodeLocal patternContextSubjectSave(PatternContext pc) {
            BytecodeLocal pcSave = b.createLocal();
            b.beginStoreLocal(pcSave);
            b.emitLoadLocal(pc.subject);
            b.endStoreLocal();
            return pcSave;
        }

        /**
         * Loads pattern context subject back into pattern context.
         *
         * @param pcSave Variable to restore pattern context subject from.
         * @param pc Pattern context into which the subject should be restored.
         */
        private void patternContextSubjectLoad(BytecodeLocal pcSave, PatternContext pc) {
            b.beginStoreLocal(pc.subject);
            b.emitLoadLocal(pcSave);
            b.endStoreLocal();
        }

        /**
         * Check if attribute and keyword attribute lengths match, or if there isn't too much
         * patterns or attributes. Throws error on fail.
         *
         * @param patLen Patterns count
         * @param attrsLen Attributes count
         * @param kwdPatLen Keyword attributes count
         * @param node MatchClass node for errors
         */
        private void classMatchLengthChecks(int patLen, int attrsLen, int kwdPatLen, PatternTy.MatchClass node) {
            if (attrsLen != kwdPatLen) {
                ctx.errorCallback.onError(ErrorType.Syntax, node.getSourceRange(), "kwd_attrs (%d) / kwd_patterns (%d) length mismatch in class pattern", attrsLen, kwdPatLen);
            }
            if (Integer.MAX_VALUE < patLen + attrsLen - 1) {
                String id = node.cls instanceof ExprTy.Name ? ((ExprTy.Name) node.cls).id : node.cls.toString();
                ctx.errorCallback.onError(ErrorType.Syntax, node.getSourceRange(), "too many sub-patterns in class pattern %s", id);
            }

        }

        /**
         * Visits sub-patterns for class pattern matching. Regular, positional patterns are handled
         * first, then the keyword patterns (e.g. the "class.attribute = [keyword] pattern").
         * Generates boolean value based on results of the subpatterns; values are evaluated using
         * the AND operator.
         *
         * @param patterns Patterns to check as subpatterns.
         * @param kwdPatterns Keyword patterns to check as subpatterns.
         * @param attrsValueUnpacked Values to use as `pc.subject` in sub-pattern check.
         * @param pc Pattern context (subject is saved then restored).
         * @param patLen Number of patterns.
         * @param attrsLen Number of attributes (also keyword patterns).
         */
        private void classMatchVisitSubpatterns(PatternTy[] patterns, PatternTy[] kwdPatterns, BytecodeLocal attrsValueUnpacked, PatternContext pc, int patLen, int attrsLen) {
            BytecodeLocal pcSave = patternContextSubjectSave(pc);

            if (patLen + attrsLen == 0) {
                b.emitLoadConstant(true);
            } else {
                BytecodeLocal temp = b.createLocal();
                b.beginStoreLocal(temp);
                b.beginPrimitiveBoolAnd();
                for (int i = 0; i < patLen; i++) {
                    b.beginBlock();
                    b.beginStoreLocal(pc.subject);
                    b.beginArrayIndex(i);
                    b.emitLoadLocal(attrsValueUnpacked);
                    b.endArrayIndex();
                    b.endStoreLocal();

                    visitSubpattern(patterns[i], pc);
                    b.endBlock();
                }

                for (int i = 0, j = patLen; i < attrsLen; i++, j++) {
                    b.beginBlock();
                    b.beginStoreLocal(pc.subject);
                    b.beginArrayIndex(j);
                    b.emitLoadLocal(attrsValueUnpacked);
                    b.endArrayIndex();
                    b.endStoreLocal();

                    visitSubpattern(kwdPatterns[i], pc);
                    b.endBlock();
                }
                b.endPrimitiveBoolAnd();
                b.endStoreLocal();

                patternContextSubjectLoad(pcSave, pc);

                b.emitLoadLocal(temp);
            }
        }

        private void doVisitPattern(PatternTy.MatchClass node, PatternContext pc) {
            /**
             * Class pattern matching consists of subject and pattern. Pattern is split into:
             * <ul>
             * <li> patterns: These are positional and match the {@code __match_args__} arguments of the class, and are
             * evaluated as sub-patterns with respective positional class attributes as subjects.
             * <li> keyword attributes (kwdAttrs): These are non-positional, named class attributes that need to match
             * the accompanying keyword patterns.
             * <li> keyword patterns (kwdPatterns): Patterns that accompany keyword attributes, these are evaluated as
             * sub-patterns with provided class attributes as subjects. Note that the number of keyword attributes
             * and keyword patterns do need to match.
             * </ul>
             *
             * Example:
             * @formatter:off
             *     x = <some class>
             *     match x:
             *         case <class>(x, 42 as y, a = ("test1" | "test2") as z):
             *             ...
             * @formatter:on
             * Here, {@code x} and {@code 42 as y} are "patterns" (positional), {@code a} is "keyword attribute" and
             * {@code ... as z} is its accompanying "keyword pattern".
             */

            b.beginBlock();

            PatternTy[] patterns = node.patterns;
            String[] kwdAttrs = node.kwdAttrs;
            PatternTy[] kwdPatterns = node.kwdPatterns;
            int patLen = lengthOrZero(patterns);
            int attrsLen = lengthOrZero(kwdAttrs);
            int kwdPatLen = lengthOrZero(kwdPatterns);

            classMatchLengthChecks(patLen, attrsLen, kwdPatLen, node);
            if (attrsLen > 0) {
                validateKwdAttrs(kwdAttrs, kwdPatterns);
            }

            //@formatter:off
            // attributes needs to be converted into truffle strings
            TruffleString[] tsAttrs = new TruffleString[attrsLen];
            for (int i = 0; i < attrsLen; i++) {
                tsAttrs[i] = toTruffleStringUncached(kwdAttrs[i]);
            }

            b.beginPrimitiveBoolAnd();
                BytecodeLocal attrsValue = b.createLocal();
                // match class that's in the subject
                b.beginMatchClass(attrsValue);
                    b.emitLoadLocal(pc.subject);
                    node.cls.accept(this); // get class type
                    b.emitLoadConstant(patLen);
                    b.emitLoadConstant(tsAttrs);
                b.endMatchClass();

                b.beginBlock();
                    // attributes from match class needs to be unpacked first
                    BytecodeLocal attrsValueUnpacked = b.createLocal();
                    b.beginStoreLocal(attrsValueUnpacked);
                        b.beginUnpackSequence(patLen + attrsLen);
                            b.emitLoadLocal(attrsValue);
                        b.endUnpackSequence();
                    b.endStoreLocal();

                    classMatchVisitSubpatterns(patterns, kwdPatterns, attrsValueUnpacked, pc, patLen, attrsLen);
                b.endBlock();
            b.endPrimitiveBoolAnd();

            b.endBlock();
            //@formatter:on
        }

        /**
         * Checks if keyword argument names aren't the same or if their name isn't forbidden. Raises
         * error at fail.
         *
         * @param attrs Attributes to check.
         * @param patterns Patterns for error source range.
         */
        private void validateKwdAttrs(String[] attrs, PatternTy[] patterns) {
            // Any errors will point to the pattern rather than the arg name as the
            // parser is only supplying identifiers rather than Name or keyword nodes
            int attrsLen = lengthOrZero(attrs);
            for (int i = 0; i < attrsLen; i++) {
                String attr = attrs[i];
                checkForbiddenName(attr, NameOperation.BeginWrite, patterns[i].getSourceRange());
                for (int j = i + 1; j < attrsLen; j++) {
                    String other = attrs[j];
                    if (attr.equals(other)) {
                        ctx.errorCallback.onError(ErrorType.Syntax, patterns[j].getSourceRange(), "attribute name repeated in class pattern: `%s`", attr);
                    }
                }
            }
        }

        private static int lengthOrZero(Object[] p) {
            return p == null ? 0 : p.length;
        }

        /**
         * Checks if keys in pattern are, if present, longer than keys in subject. If yes, pattern
         * should fail, otherwise, we should continue with evaluation.
         * <p>
         * Generates result of the comparison (boolean).
         *
         * @param keyLen Number of keys in pattern.
         * @param pc Pattern context.
         */
        private void checkPatternKeysLength(int keyLen, PatternContext pc) {
            b.beginGe();
            b.beginGetLen();
            b.emitLoadLocal(pc.subject);
            b.endGetLen();
            b.emitLoadConstant(keyLen);
            b.endGe();
        }

        /**
         * Will process pattern keys: Attributes evaluation and constant folding. Checks for
         * duplicate keys and that only literals and attributes lookups are being matched.
         * <p>
         * Generates array.
         *
         * @param keys Pattern keys.
         * @param keyLen Length of pattern keys.
         * @param node Pattern matching node, for source range in errors.
         */
        private void processPatternKeys(ExprTy[] keys, int keyLen, PatternTy.MatchMapping node) {
            b.beginCollectToObjectArray(); // keys (from pattern)
            List<Object> seen = new ArrayList<>();
            for (int i = 0; i < keyLen; i++) {
                ExprTy key = keys[i];
                if (key instanceof ExprTy.Attribute) {
                    key.accept(this);
                } else {
                    ConstantValue constantValue = null;
                    if (key instanceof ExprTy.UnaryOp || key instanceof ExprTy.BinOp) {
                        constantValue = foldConstantOp(key);
                    } else if (key instanceof ExprTy.Constant) {
                        constantValue = ((ExprTy.Constant) key).value;
                    } else {
                        ctx.errorCallback.onError(ErrorType.Syntax, node.getSourceRange(), "mapping pattern keys may only match literals and attribute lookups");
                    }
                    assert constantValue != null;
                    Object pythonValue = PythonUtils.pythonObjectFromConstantValue(constantValue);
                    for (Object o : seen) {
                        // need python like equal - e.g. 1 equals True
                        if (PyObjectRichCompareBool.executeEqUncached(o, pythonValue)) {
                            ctx.errorCallback.onError(ErrorType.Syntax, node.getSourceRange(), "mapping pattern checks duplicate key (%s)", pythonValue);
                        }
                    }
                    seen.add(pythonValue);
                    createConstant(constantValue);
                }
            }
            b.endCollectToObjectArray();
        }

        /**
         * Visit all sub-patterns for mapping in pattern (not subject).
         * <p>
         * Generates boolean value (AND of result of all sub-patterns).
         *
         * @param patterns Sub-patterns to iterate through.
         * @param values Patterns from subject to set as subject for evaluated sub-patterns.
         * @param pc Pattern context.
         */
        private void mappingVisitSubpatterns(PatternTy[] patterns, BytecodeLocal values, PatternContext pc) {
            int patLen = patterns.length;

            b.beginBlock();
            // unpack values from pc.subject
            BytecodeLocal valuesUnpacked = b.createLocal();
            b.beginStoreLocal(valuesUnpacked);
            b.beginUnpackSequence(patLen);
            b.emitLoadLocal(values);
            b.endUnpackSequence();
            b.endStoreLocal();

            // backup pc.subject, it will get replaced for sub-patterns
            BytecodeLocal pcSave = patternContextSubjectSave(pc);

            BytecodeLocal temp = b.createLocal();
            b.beginStoreLocal(temp);
            b.beginPrimitiveBoolAnd();
            boolean hadNonWildcardPattern = false;
            for (int i = 0; i < patLen; i++) {
                if (wildcardCheck(patterns[i])) {
                    continue;
                }
                hadNonWildcardPattern = true;
                b.beginBlock();
                b.beginStoreLocal(pc.subject);
                b.beginArrayIndex(i);
                b.emitLoadLocal(valuesUnpacked);
                b.endArrayIndex();
                b.endStoreLocal();

                visitSubpattern(patterns[i], pc);
                b.endBlock();
            }
            if (!hadNonWildcardPattern) {
                b.emitLoadConstant(true);
            }
            b.endPrimitiveBoolAnd();
            b.endStoreLocal();

            patternContextSubjectLoad(pcSave, pc);

            b.emitLoadLocal(temp);
            b.endBlock();
        }

        private void doVisitPattern(PatternTy.MatchMapping node, PatternContext pc) {
            /**
             * Mapping pattern match will take the keys and check, whether the keys in the pattern
             * are present in the subject. This is good enough, since the pattern needs only to be a
             * subset of the subject. Keys aren't evaluated as subpatterns.
             *
             * After the key check, the values of the pattern are patterns as well and are evaluated
             * as sub-patterns with values in the subject used as separate respective subjects.
             */
            ExprTy[] keys = node.keys;
            PatternTy[] patterns = node.patterns;

            int keyLen = lengthOrZero(keys);
            int patLen = lengthOrZero(patterns);

            if (keyLen != patLen) {
                ctx.errorCallback.onError(ErrorType.Syntax, node.getSourceRange(), "keys (%d) / patterns (%d) length mismatch in mapping pattern", keyLen, patLen);
            }
            // @formatter:off

            b.beginPrimitiveBoolAnd(); // AND for type, trivial and key length matching
                // check that type matches
                b.beginCheckTypeFlags(TypeFlags.MAPPING);
                    b.emitLoadLocal(pc.subject);
                b.endCheckTypeFlags();

                String starTarget = node.rest;
                if (keyLen == 0 && starTarget == null) {
                    b.emitLoadConstant(true);
                    b.endPrimitiveBoolAnd();
                    return;
                }
                if (Integer.MAX_VALUE < keyLen - 1) {
                    ctx.errorCallback.onError(ErrorType.Syntax, node.getSourceRange(), "too many sub-patterns in mapping pattern");
                }

                // If the pattern has any keys in it, perform a length check:
                if (keyLen > 0) {
                    checkPatternKeysLength(keyLen, pc);
                }

                b.beginBlock();
                    BytecodeLocal subjectPatterns = b.createLocal();
                    BytecodeLocal temp = b.createLocal();
                    BytecodeLocal keysChecked = b.createLocal();

                    b.beginStoreLocal(temp);
                        b.beginPrimitiveBoolAnd(); // AND process keys and sub-patterns
                            b.beginBlock();
                                b.beginStoreLocal(keysChecked);
                                    processPatternKeys(keys, keyLen, node);
                                b.endStoreLocal();

                                // save match result together with values
                                b.beginMatchKeys(subjectPatterns);
                                    b.emitLoadLocal(pc.subject);
                                    b.emitLoadLocal(keysChecked);
                                b.endMatchKeys();
                            b.endBlock();

                            if (patLen > 0) {
                                mappingVisitSubpatterns(patterns, subjectPatterns, pc);
                            }
                        b.endPrimitiveBoolAnd(); // AND process keys and sub-patterns
                    b.endStoreLocal(); // temp

                    if (starTarget != null) {
                        BytecodeLocal starVariable = pc.allocateBindVariable(starTarget);
                        b.beginStoreLocal(starVariable);
                            b.beginCopyDictWithoutKeys();
                                b.emitLoadLocal(pc.subject);
                                b.emitLoadLocal(keysChecked);
                            b.endCopyDictWithoutKeys();
                        b.endStoreLocal();
                    }

                    b.emitLoadLocal(temp);
                b.endBlock();
            b.endPrimitiveBoolAnd(); // AND for key length matching

            // @formatter:on
        }

        private void checkAlternativePatternDifferentNames(Set<String> control, Map<String, BytecodeLocal> bindVariables) {
            if (!control.equals(bindVariables.keySet())) {
                ctx.errorCallback.onError(ErrorType.Syntax, currentLocation, "alternative patterns bind different names");
            }
        }

        private void fromPatternContextToLocal(PatternContext pc, BytecodeLocal local_temp) {
            b.beginIfThen();

            // condition
            b.emitLoadLocal(local_temp);

            // if-then
            b.beginBlock();

            if (!pc.bindVariables.isEmpty()) {
                for (Map.Entry<String, BytecodeLocal> entry : pc.bindVariables.entrySet()) {
                    beginStoreLocal(entry.getKey(), b);
                    b.emitLoadLocal(entry.getValue());
                    endStoreLocal(entry.getKey(), b);
                }
            }

            b.endBlock();
            b.endIfThen();
        }

        private void visitMatchOrRecursively(PatternTy[] patterns, int index, PatternContext pc, Set<String> control, boolean allowIrrefutable) {
            /**
             * Case patterns joined by OR operator are chained as a sequence of binary OR operators, as in:
             *
             * @formatter:off
             * case pattern1 | (pattern2 | (pattern3 | ... (patternN-1 | patternN))):
             *  ...
             * @formatter:on
             */
            b.beginBoolOr();
            b.beginBlock();

            pc = new PatternContext(pc.subject);

            // store the (boolean) result of the sub-pattern
            BytecodeLocal local_temp = b.createLocal();
            b.beginStoreLocal(local_temp);
            visitPattern(patterns[index], pc);
            b.endStoreLocal();

            if (index == 0) {
                control = new HashSet<>(pc.bindVariables.keySet());
            }
            checkAlternativePatternDifferentNames(control, pc.bindVariables);
            fromPatternContextToLocal(pc, local_temp);

            b.emitLoadLocal(local_temp);
            b.endBlock();

            if (index + 2 < patterns.length) {
                visitMatchOrRecursively(patterns, index + 1, pc, control, allowIrrefutable);
                b.endBoolOr();
            } else {
                // Only last sub-pattern can be irrefutable -- if it was allowed in the first place
                pc = new PatternContext(pc.subject);
                pc.allowIrrefutable = allowIrrefutable;

                b.beginBlock();

                // store the (boolean) result of the sub-pattern
                local_temp = b.createLocal();
                b.beginStoreLocal(local_temp);
                visitPattern(patterns[index + 1], pc);
                b.endStoreLocal();

                checkAlternativePatternDifferentNames(control, pc.bindVariables);
                fromPatternContextToLocal(pc, local_temp);

                b.emitLoadLocal(local_temp);
                b.endBlock();
                b.endBoolOr();
            }
        }

        private void doVisitPattern(PatternTy.MatchOr node, PatternContext pc) {
            boolean saveIrrefutable = pc.allowIrrefutable;
            // sub-patterns are not irrefutable by default, only last one is
            // this needs to be restored before last sub-pattern is visited
            pc.allowIrrefutable = false;
            visitMatchOrRecursively(node.patterns, 0, pc, null, saveIrrefutable);
        }

        private void patternHelperSequenceUnpack(PatternTy[] patterns, PatternContext pc) {
            int n = len(patterns);

            b.beginBlock();
            // We need to remember the unpacked array, since subject will be overwritten in
            // recursive calls.
            BytecodeLocal unpacked = b.createLocal();
            b.beginStoreLocal(unpacked);
            patternUnpackHelper(patterns, pc);
            b.endStoreLocal();

            b.beginPrimitiveBoolAnd();
            for (int i = 0; i < n; i++) {
                b.beginBlock();
                b.beginStoreLocal(pc.subject);
                b.beginArrayIndex(i);
                b.emitLoadLocal(unpacked);
                b.endArrayIndex();
                b.endStoreLocal();

                visitSubpattern(patterns[i], pc);
                b.endBlock();
            }

            b.endPrimitiveBoolAnd();
            b.endBlock();
        }

        private void patternUnpackHelper(PatternTy[] patterns, PatternContext pc) {
            int n = len(patterns);

            boolean seenStar = false;
            for (int i = 0; i < n; i++) {
                PatternTy pattern = patterns[i];
                if (pattern instanceof PatternTy.MatchStar) {
                    if (seenStar) {
                        ctx.errorCallback.onError(ErrorType.Syntax, currentLocation, "multiple starred expressions in sequence pattern");
                    }
                    seenStar = true;
                    int countAfter = n - i - 1;
                    if (countAfter != (byte) countAfter) {
                        ctx.errorCallback.onError(ErrorType.Syntax, currentLocation, "too many expressions in star-unpacking sequence pattern");
                    }
                    // If there's a star pattern, emit UnpackEx.
                    b.beginUnpackEx(i, countAfter);
                    b.emitLoadLocal(pc.subject);
                    b.endUnpackEx();
                    // Continue in the loop to ensure there are no additional starred patterns.
                }
            }
            // If there were no star patterns, emit UnpackSequence.
            if (!seenStar) {
                b.beginUnpackSequence(n);
                b.emitLoadLocal(pc.subject);
                b.endUnpackSequence();
            }
        }

        /**
         * Like patternHelperSequenceUnpack, but uses subscripting, which is (likely) more efficient
         * for patterns with a starred wildcard like [first, *_], [first, *_, last], [*_, last],
         * etc.
         */
        private void patternHelperSequenceSubscr(PatternTy[] patterns, int star, PatternContext pc) {
            int n = len(patterns);

            b.beginBlock();
            // We need to remember the sequence, since subject will be overwritten in recursive
            // calls.
            BytecodeLocal sequence = b.createLocal();
            b.beginStoreLocal(sequence);
            b.emitLoadLocal(pc.subject);
            b.endStoreLocal();

            for (int i = 0; i < n; i++) {
                PatternTy pattern = patterns[i];
                if (wildcardCheck(pattern)) {
                    // nothing to check
                    continue;
                } else if (i == star) {
                    // nothing to check
                    assert wildcardStarCheck(pattern);
                    continue;
                }

                b.beginStoreLocal(pc.subject);
                b.beginBinarySubscript();
                b.emitLoadLocal(sequence);
                if (i < star) {
                    b.emitLoadConstant(i);
                } else {
                    // The subject may not support negative indexing! Compute a
                    // nonnegative index:
                    b.beginPyNumberSubtract();

                    b.beginGetLen();
                    b.emitLoadLocal(sequence);
                    b.endGetLen();

                    b.emitLoadConstant(n - i);

                    b.endPyNumberSubtract();
                }
                b.endBinarySubscript();
                b.endStoreLocal();

                visitSubpattern(pattern, pc);
            }
            b.endBlock();
        }

        private void doVisitPattern(PatternTy.MatchSequence node, PatternContext pc) {
            int size = len(node.patterns);
            int star = -1;
            boolean onlyWildcard = true;
            boolean starWildcard = false;

            // Find a starred name, if it exists. There may be at most one:
            for (int i = 0; i < size; i++) {
                PatternTy pattern = node.patterns[i];
                if (pattern instanceof PatternTy.MatchStar) {
                    if (star >= 0) {
                        ctx.errorCallback.onError(ErrorType.Syntax, node.getSourceRange(), "multiple starred names in sequence pattern");
                    }
                    starWildcard = wildcardStarCheck(pattern);
                    onlyWildcard &= starWildcard;
                    star = i;
                    continue;
                }
                onlyWildcard &= wildcardCheck(pattern);
            }

            b.beginBlock();
            BytecodeLocal resultOfAnd = b.createLocal();

            // oldSubject <- pc.subject
            // store pc.subject for eventual return from sub-pattern
            BytecodeLocal oldSubject = b.createLocal();
            b.beginStoreLocal(oldSubject);
            b.emitLoadLocal(pc.subject);
            b.endStoreLocal();

            b.beginStoreLocal(resultOfAnd);
            b.beginPrimitiveBoolAnd();

            b.beginCheckTypeFlags(TypeFlags.SEQUENCE);
            b.emitLoadLocal(pc.subject);
            b.endCheckTypeFlags();

            if (star < 0) {
                // No star: len(subject) == size
                b.beginEq();
                b.beginGetLen();
                b.emitLoadLocal(pc.subject);
                b.endGetLen();
                b.emitLoadConstant(size);
                b.endEq();
            } else if (size > 1) {
                // Star: len(subject) >= size - 1
                b.beginGe();
                b.beginGetLen();
                b.emitLoadLocal(pc.subject);
                b.endGetLen();
                b.emitLoadConstant(size - 1);
                b.endGe();
            }

            if (onlyWildcard) {
                /**
                 * For patterns like: [] / [_] / [_, _] / [*_] / [_, *_] / [_, _, *_] / etc., there
                 * is nothing more to check.
                 */
            } else if (starWildcard) {
                /**
                 * For sequences with a *_ pattern, it is (likely) more efficient to extract the
                 * bound elements with subscripting rather than iterating the entire collection.
                 */
                patternHelperSequenceSubscr(node.patterns, star, pc);
            } else {
                /**
                 * Otherwise, unpack the sequence element-by-element. If there's a named * pattern,
                 * collect the rest into it.
                 */
                patternHelperSequenceUnpack(node.patterns, pc);
            }

            b.endPrimitiveBoolAnd();
            b.endStoreLocal();

            // pc.subject <- oldSubject
            // load old subject when returning from sub-pattern
            b.beginStoreLocal(pc.subject);
            b.emitLoadLocal(oldSubject);
            b.endStoreLocal();

            b.emitLoadLocal(resultOfAnd);
            b.endBlock();

        }

        private void doVisitPattern(PatternTy.MatchSingleton node, PatternContext pc) {
            b.beginIs();
            b.emitLoadLocal(pc.subject);

            switch (node.value.kind) {
                case BOOLEAN:
                    b.emitLoadConstant(node.value.getBoolean());
                    break;
                case NONE:
                    b.emitLoadConstant(PNone.NONE);
                    break;
                default:
                    throw new IllegalStateException("wrong MatchSingleton value kind " + node.value.kind);
            }
            b.endIs();
        }

        private void doVisitPattern(PatternTy.MatchStar node, PatternContext pc) {
            if (node.name != null) {
                b.beginBlock();
                pc.copySubjectToTemporary(node.name);
                b.emitLoadConstant(true);
                b.endBlock();
            }
            /**
             * If there's no name, no need to emit anything. A MatchStar can only appear as a
             * subpattern of a mapping/sequence pattern, at which point in code generation we will
             * be in the middle of a short-circuiting AND (that already has at least one operand)
             */
        }

        private void doVisitPattern(PatternTy.MatchValue node, PatternContext pc) {
            b.beginEq();
            b.emitLoadLocal(pc.subject);

            if (node.value instanceof ExprTy.UnaryOp || node.value instanceof ExprTy.BinOp) {
                createConstant(foldConstantOp(node.value));
            } else if (node.value instanceof ExprTy.Constant || node.value instanceof ExprTy.Attribute) {
                node.value.accept(this);
            } else {
                ctx.errorCallback.onError(ErrorType.Syntax, currentLocation, "patterns may only match literals and attribute lookups");
            }
            b.endEq();
        }

        private static boolean wildcardCheck(PatternTy pattern) {
            return pattern instanceof PatternTy.MatchAs && ((PatternTy.MatchAs) pattern).name == null;
        }

        private static boolean wildcardStarCheck(PatternTy pattern) {
            return pattern instanceof PatternTy.MatchStar && ((PatternTy.MatchStar) pattern).name == null;
        }

        /**
         * handles only particular cases when a constant comes either as a unary or binary op
         */
        private ConstantValue foldConstantOp(ExprTy value) {
            if (value instanceof ExprTy.UnaryOp unaryOp) {
                return foldUnaryOpConstant(unaryOp);
            } else if (value instanceof ExprTy.BinOp binOp) {
                return foldBinOpComplexConstant(binOp);
            }
            throw new IllegalStateException("should not reach here");
        }

        /**
         * handles only unary sub and a numeric constant
         */
        private ConstantValue foldUnaryOpConstant(ExprTy.UnaryOp unaryOp) {
            assert unaryOp.op == UnaryOpTy.USub;
            assert unaryOp.operand instanceof ExprTy.Constant : unaryOp.operand;
            ExprTy.Constant c = (ExprTy.Constant) unaryOp.operand;
            ConstantValue ret = c.value.negate();
            assert ret != null;
            return ret;
        }

        /**
         * handles only complex which comes as a BinOp
         */
        private ConstantValue foldBinOpComplexConstant(ExprTy.BinOp binOp) {
            assert (binOp.left instanceof ExprTy.UnaryOp || binOp.left instanceof ExprTy.Constant) && binOp.right instanceof ExprTy.Constant : binOp.left + " " + binOp.right;
            assert binOp.op == OperatorTy.Sub || binOp.op == OperatorTy.Add;
            ConstantValue left;
            if (binOp.left instanceof ExprTy.UnaryOp) {
                left = foldUnaryOpConstant((ExprTy.UnaryOp) binOp.left);
            } else {
                left = ((ExprTy.Constant) binOp.left).value;
            }
            ExprTy.Constant right = (ExprTy.Constant) binOp.right;
            switch (binOp.op) {
                case Add:
                    return left.addComplex(right.value);
                case Sub:
                    return left.subComplex(right.value);
                default:
                    throw new IllegalStateException("wrong constant BinOp operator " + binOp.op);
            }
        }

        @Override
        public Void visit(MatchCaseTy node) {
            throw new UnsupportedOperationException("" + node.getClass());
        }

        @Override
        public Void visit(PatternTy.MatchAs node) {
            throw new UnsupportedOperationException("" + node.getClass());
        }

        @Override
        public Void visit(PatternTy.MatchClass node) {
            throw new UnsupportedOperationException("" + node.getClass());
        }

        @Override
        public Void visit(PatternTy.MatchMapping node) {
            throw new UnsupportedOperationException("" + node.getClass());
        }

        @Override
        public Void visit(PatternTy.MatchOr node) {
            throw new UnsupportedOperationException("" + node.getClass());
        }

        @Override
        public Void visit(PatternTy.MatchSequence node) {
            throw new UnsupportedOperationException("" + node.getClass());
        }

        @Override
        public Void visit(PatternTy.MatchSingleton node) {
            throw new UnsupportedOperationException("" + node.getClass());
        }

        @Override
        public Void visit(PatternTy.MatchStar node) {
            throw new UnsupportedOperationException("" + node.getClass());
        }

        @Override
        public Void visit(PatternTy.MatchValue node) {
            throw new UnsupportedOperationException("" + node.getClass());
        }

        @Override
        public Void visit(StmtTy.Nonlocal node) {
            return null;
        }

        @Override
        public Void visit(StmtTy.Raise node) {
            boolean newStatement = beginSourceSection(node, b);
            emitTraceLineChecked(node, b);
            b.beginRaise();

            if (node.exc != null) {
                node.exc.accept(this);
            } else {
                b.emitLoadConstant(PNone.NO_VALUE);
            }

            if (node.cause != null) {
                node.cause.accept(this);
            } else {
                b.emitLoadConstant(PNone.NO_VALUE);
            }

            b.endRaise();
            endSourceSection(b, newStatement);
            return null;
        }

        @Override
        public Void visit(StmtTy.Return node) {
            boolean newStatement = beginSourceSection(node, b);
            if (!scope.isFunction()) {
                ctx.errorCallback.onError(ErrorType.Syntax, currentLocation, "'return' outside function");
            }
            if (inExceptStar) {
                ctx.errorCallback.onError(ErrorType.Syntax, currentLocation, "'break', 'continue' and 'return' cannot appear in an except* block");
            }
            if (node.value != null && scope.isGenerator() && scope.isCoroutine()) {
                ctx.errorCallback.onError(ErrorType.Syntax, currentLocation, "'return' with value in async generator");
            }
            beginReturn(b);
            b.beginBlock();
            beginTraceLineChecked(b);
            if (node.value != null) {
                node.value.accept(this);
            } else {
                b.emitLoadConstant(PNone.NONE);
            }
            endTraceLineChecked(node, b);
            b.endBlock();
            endReturn(b);
            endSourceSection(b, newStatement);
            return null;
        }

        @Override
        public Void visit(StmtTy.Try node) {
            boolean newStatement = beginSourceSection(node, b);
            emitTraceLineChecked(node, b);
            if (node.finalBody != null && node.finalBody.length != 0) {
                /**
                 * In Python, an uncaught exception becomes the "current" exception inside a finally
                 * block. The finally body can itself throw, in which case it replaces the exception
                 * being thrown. For such a scenario, we have to be careful to restore the "current"
                 * exception using a try-finally.
                 *
                 * In pseudocode, the implementation looks like:
                 * @formatter:off
                 * try {
                 *   try_catch_else
                 * } catch uncaught_ex {
                 *   # this all is finally in case of exceptional exit
                 *   # user defined handlers already run in try_catch_else above
                 *   save current exception
                 *   set the current exception to uncaught_ex
                 *   markCaught(uncaught_ex)
                 *   try {
                 *     finally_body
                 *   } catch handler_ex {
                 *     restore current exception
                 *     markCaught(handler_ex)
                 *     reraise handler_ex
                 *   } otherwise {
                 *     restore current exception
                 *   }
                 *   reraise uncaught_ex
                 * } otherwise {
                 *   finally_body
                 * }
                 */
                b.beginTryCatchOtherwise(() -> {
                    b.beginBlock(); // finally
                        visitSequence(node.finalBody);
                    b.endBlock();
                });

                    emitTryExceptElse(node); // try-except-else

                    b.beginBlock(); // catch uncaught exceptions
                        BytecodeLocal savedException = b.createLocal();
                        BytecodeLocal prevPrevSaved = enterSaveExceptionBlock(savedException);

                        emitSaveCurrentException(savedException);
                        emitSetCurrentException();
                        // Mark this location for the stack trace.
                        b.beginMarkExceptionAsCaught();
                            b.emitLoadException();
                        b.endMarkExceptionAsCaught();

                        b.beginTryCatchOtherwise(() -> emitRestoreCurrentException(savedException));
                            b.beginBlock(); // try finally body
                                visitSequence(node.finalBody);
                            b.endBlock(); // try finally body

                            b.beginBlock(); // catch exception in finally
                                emitRestoreCurrentException(savedException);

                                b.beginMarkExceptionAsCaught();
                                    b.emitLoadException();
                                b.endMarkExceptionAsCaught();

                                b.beginReraise();
                                    b.emitLoadException();
                                b.endReraise();
                            b.endBlock(); // catch exception in finally
                        b.endTryCatchOtherwise();

                        b.beginReraise();
                            b.emitLoadException();
                        b.endReraise();

                        exitSaveExceptionBlock(prevPrevSaved);
                    b.endBlock(); // catch uncaught exceptions
                b.endTryCatchOtherwise();
                // @formatter:on
            } else {
                emitTryExceptElse(node);
            }

            endSourceSection(b, newStatement);
            return null;
        }

        /**
         * Emit the "try-except-else" part of a Try node. The "finally" part, if it exists, should
         * be handled by the caller of this method.
         */
        private void emitTryExceptElse(StmtTy.Try node) {
            if (node.handlers != null && node.handlers.length != 0) {
                /**
                 * There are two orthogonal issues that complicate Python try-except clauses.
                 *
                 * First, when in an exception handler, the "current" exception (accessible via, e.g.,
                 * sys.exc_info) gets set to the caught exception. After leaving the handler, this
                 * "current" exception must be restored to the one previously stored. Since except
                 * clauses can themselves raise exceptions, the restoring process must happen inside
                 * a finally block.
                 *
                 * Second, when an exception is bound to an identifier (e.g., except BaseException as
                 * ex), the identifier must be deleted after leaving the except clause. Again, since
                 * the except clause may raise an exception, the deletion must happen inside a finally
                 * block. Since the bound name is different in each clause, this block is specific to
                 * each handler.
                 *
                 * @formatter:off
                 * try {
                 *   try_body
                 *   # fall through to else_body
                 * } catch ex {
                 *   save current exception
                 *   set current exception to ex
                 *   markCaught(ex)
                 *   try {
                 *     if (handler_1_matches(ex)) {
                 *       assign ex to handler_1_name
                 *       try {
                 *         handler_1_body
                 *       } catch handler_1_ex {
                 *         unbind handler_1_name
                 *         // Freeze the bci before it gets rethrown.
                 *         markCaught(handler_ex)
                 *         throw handler_1_ex
                 *       } otherwise {
                 *         unbind handler_1_name
                 *       }
                 *       goto afterElse
                 *     }
                 *     ... // more handlers
                 *
                 *     // case 1: bare except
                 *     bare_except_body
                 *     goto afterElse
                 *   } catch handler_ex {
                 *     // A handler raised or no handler was found. Restore exception state and reraise.
                 *     restore current exception
                 *     markCaught(handler_ex) // (no-op if handler_ex is the original exception)
                 *     reraise handler_ex
                 *   } otherwise {
                 *     // Exception handled. Restore the exception state.
                 *     restore current exception
                 *   }
                 *   // case 2: no bare except (we only reach this point if no handler matched/threw)
                 *   reraise ex
                 * }
                 * else_body
                 * afterElse:
                 */
                b.beginBlock(); // outermost block

                BytecodeLabel afterElse = b.createLabel();

                b.beginTryCatch();

                    b.beginBlock(); // try
                        visitSequence(node.body);
                    b.endBlock(); // try

                    b.beginBlock(); // catch
                        BytecodeLocal savedException = b.createLocal();
                        BytecodeLocal prevPrevEx = enterSaveExceptionBlock(savedException);

                        emitSaveCurrentException(savedException);
                        emitSetCurrentException();
                        // Mark this location for the stack trace.
                        b.beginMarkExceptionAsCaught();
                            b.emitLoadException(); // ex
                        b.endMarkExceptionAsCaught();

                        b.beginTryCatchOtherwise(() -> emitRestoreCurrentException(savedException));
                            b.beginBlock(); // try
                                SourceRange bareExceptRange = null;
                                for (ExceptHandlerTy h : node.handlers) {
                                    boolean newStatement = beginSourceSection(h, b);
                                    emitTraceLineChecked(h, b);
                                    if (bareExceptRange != null) {
                                        ctx.errorCallback.onError(ErrorType.Syntax, currentLocation, "default 'except:' must be last");
                                    }

                                    ExceptHandlerTy.ExceptHandler handler = (ExceptHandlerTy.ExceptHandler) h;
                                    if (handler.type != null) {
                                        b.beginIfThen();
                                            b.beginExceptMatch();
                                                b.emitLoadException(); // ex
                                                handler.type.accept(this);
                                            b.endExceptMatch();
                                    } else {
                                        bareExceptRange = handler.getSourceRange();
                                    }

                                    b.beginBlock(); // handler body

                                    if (handler.name != null) {
                                        // Assign exception to handler name.
                                        beginStoreLocal(handler.name, b);
                                            b.beginUnwrapException();
                                                b.emitLoadException(); // ex
                                            b.endUnwrapException();
                                        endStoreLocal(handler.name, b);

                                        b.beginTryCatchOtherwise(() -> emitUnbindHandlerVariable(handler));
                                            b.beginBlock(); // try
                                                visitSequence(handler.body);
                                            b.endBlock(); // try

                                            b.beginBlock(); // catch
                                                emitUnbindHandlerVariable(handler);

                                                b.beginMarkExceptionAsCaught();
                                                    b.emitLoadException(); // handler_i_ex
                                                b.endMarkExceptionAsCaught();

                                                b.beginThrow();
                                                    b.emitLoadException(); // handler_i_ex
                                                b.endThrow();
                                            b.endBlock(); // catch
                                        b.endTryCatchOtherwise();
                                    } else { // bare except
                                        b.beginBlock();
                                            visitSequence(handler.body);
                                        b.endBlock();
                                    }

                                    b.emitBranch(afterElse);

                                    b.endBlock(); // handler body

                                    if (handler.type != null) {
                                        b.endIfThen();
                                    }

                                    endSourceSection(b, newStatement);
                                }
                            b.endBlock(); // try

                            b.beginBlock(); // catch
                                emitRestoreCurrentException(savedException);

                                b.beginMarkExceptionAsCaught();
                                    b.emitLoadException(); // handler_ex
                                b.endMarkExceptionAsCaught();

                                b.beginReraise();
                                    b.emitLoadException(); // handler_ex
                                b.endReraise();
                            b.endBlock(); // catch
                        b.endTryCatchOtherwise();

                        /**
                         * Each handler branches to afterElse. If we reach this point and there was not a
                         * bare exception, none of the handlers matched, and we should reraise.
                         * Optimization: If there's a bare except clause, control will never fall through
                         * and we can omit the rethrow.
                         */
                        if (bareExceptRange == null) {
                            b.beginReraise();
                                b.emitLoadException(); // ex
                            b.endReraise();
                        }

                        exitSaveExceptionBlock(prevPrevEx);
                    b.endBlock(); // catch

                b.endTryCatch();

                if (node.orElse != null) {
                    visitSequence(node.orElse);
                }
                b.emitLabel(afterElse);

                b.endBlock(); // outermost block
                // @formatter:on
            } else {
                // Optimization: If there's no except clauses, there's no point in generating a
                // TryCatch with a catch that just rethrows the caught exception.
                b.beginBlock();
                visitSequence(node.body);
                b.endBlock();
            }
        }

        /**
         * Emit the "try-except-else" part of a TryStar node. The "finally" part, if it exists,
         * should be handled by the caller of this method.
         */
        private void emitTryExceptElse(StmtTy.TryStar node) {
            /**
             * See the overload for StmtTy.Try node for general overview.
             *
             * Some exception groups and try-except* related notes and differences w.r.t. regular try-except block:
             *
             * - In except* scenario, all handlers will try to match its exceptions from exception group.
             *   This means, that possibly more than one handler bodies can be executed, and also all handler clauses
             *   will be checked with caught exception group.
             * - If handler raises a new exception, does explicit raise of a caught exception or does a reraise, all
             *   these needs to be collected into one big exception group that gets reraised at the end of the
             *   try-except* block, should it not be empty. Unmatched exceptions will end up in this final exception
             *   group as well. We use the exception accumulator `exceptionAcc` during the
             *   course of this function for this purpose.
             * - In regular try-except the exceptions raised in `try` and caught in `except` were the same. However,
             *   in try-except* the exception caught in `except*` is an exception group created ad-hoc, containing
             *   only those exceptions, that matched the handler clause.
             *
             * @formatter:off
             * try {
             *   try_body
             *   // fall through to else_body
             * } catch eg {
             *   save current exception
             *   set current exception to eg
             *   save eg to exceptionOrig
             *   create exception_acc  # accumulator for final, all-encompassing exception group
             *   markCaught(ex)
             *   try {
             *     if (handler_1_matches_eg(eg)) {
             *       matched_ex = exceptions from eg that did match clause from handler 1
             *       unmatched_ex = exceptions from eg that didn't match clause from handler 1
             *       assign matched_ex to handler_1_name
             *       try {
             *         handler_1_body
             *       } catch handler_1_ex {
             *         add_exception_to_exception_acc(handler_1_ex)
             *         unbind handler_1_name
             *         // Freeze the bci before it gets rethrown.
             *         markCaught(handler_ex)
             *       } otherwise {
             *         unbind handler_1_name
             *       }
             *     }
             *     if (handler_2_matches_eg(unmatched_ex)) {
             *       // here, the matched_ex and unmatched_ex from handler 1 are repurposed
             *       ...
             *     }
             *     // similarly for all other handlers
             *     ...
             *
             *     add_exception_to_exception_acc(unmatched_ex)
             *     reraise exception_acc  # we need to raise, so that "otherwise" will not run
             *   } catch final_eg {
             *     // A handler for the final exception group, restore exception state and reraise it.
             *     restore current exception
             *     reraise final_eg
             *     goto afterElse
             *   } otherwise {
             *     // Exception handled. Restore the exception state.
             *     restore current exception
             *   }
             * }
             * else_body
             * afterElse:
             */
            b.beginBlock(); // outermost block

            BytecodeLabel afterElse = b.createLabel();

            b.beginTryCatch();

                b.beginBlock(); // try
                    visitSequence(node.body);
                b.endBlock(); // try

                b.beginBlock(); // catch
                    BytecodeLocal exceptionOrig = b.createLocal();
                    BytecodeLocal savedException = b.createLocal();
                    BytecodeLocal prevPrevEx = enterSaveExceptionBlock(savedException);

                    emitSaveCurrentException(savedException);
                    emitSetCurrentException();

                    b.beginStoreLocal(exceptionOrig);
                        b.emitGetCaughtException();
                    b.endStoreLocal();
                    // Mark this location for the stack trace.
                    b.beginMarkExceptionAsCaught();
                        b.emitLoadException(); // ex
                    b.endMarkExceptionAsCaught();

                    b.beginTryCatchOtherwise(() -> emitRestoreCurrentException(savedException));
                        b.beginBlock(); // try (all handlers)
                            BytecodeLocal matchedExceptions = b.createLocal();
                            BytecodeLocal unmatchedExceptions = b.createLocal();
                            b.beginStoreLocal(unmatchedExceptions);
                                b.emitLoadException();
                            b.endStoreLocal();

                            BytecodeLocal exceptionAcc = b.createLocal();
                            b.beginStoreLocal(exceptionAcc);
                                b.emitLoadConstant(PNone.NONE);
                            b.endStoreLocal();

                            for (ExceptHandlerTy h : node.handlers) {
                                boolean newStatement = beginSourceSection(h, b);
                                emitTraceLineChecked(h, b);

                                ExceptHandlerTy.ExceptHandler handler = (ExceptHandlerTy.ExceptHandler) h;
                                if (handler.type == null) {
                                    ctx.errorCallback.onError(ErrorType.Syntax, currentLocation, "cannot have bare 'except' in 'try' containing 'except*' clauses.");
                                }

                                BytecodeLocal handlerType = b.createLocal();
                                b.beginStoreLocal(handlerType);
                                    handler.type.accept(this);
                                b.endStoreLocal();

                                b.beginIfThen();
                                    b.beginSplitExceptionGroups(matchedExceptions, unmatchedExceptions);
                                        b.emitLoadLocal(unmatchedExceptions); // ex
                                        b.emitLoadLocal(handlerType);
                                        b.emitLoadLocal(exceptionOrig);
                                    b.endSplitExceptionGroups();

                                    b.beginBlock(); // then; handler body
                                        boolean saveInExceptStarState;
                                        if (handler.name != null) {
                                            // Assign exception to handler name.
                                            beginStoreLocal(handler.name, b);
                                                b.beginUnwrapException();
                                                    b.emitLoadLocal(matchedExceptions);
                                                b.endUnwrapException();
                                            endStoreLocal(handler.name, b);

                                            b.beginTryCatchOtherwise(() -> emitUnbindHandlerVariable(handler));
                                                b.beginBlock(); // try (this handler only)
                                                    b.beginSetCurrentException();
                                                        b.emitLoadLocal(matchedExceptions);
                                                    b.endSetCurrentException();

                                                    saveInExceptStarState = inExceptStar;
                                                    inExceptStar = true;

                                                    visitSequence(handler.body);

                                                    inExceptStar = saveInExceptStarState;

                                                    b.beginSetCurrentException();
                                                        b.emitLoadLocal(exceptionOrig);
                                                    b.endSetCurrentException();
                                                b.endBlock(); // try (this handler only)

                                                b.beginBlock(); // catch (exception thrown in this handler)
                                                    emitUnbindHandlerVariable(handler);

                                                    b.beginMarkExceptionAsCaught();
                                                        b.emitLoadException(); // handler_i_ex (exception thrown in this handler)
                                                    b.endMarkExceptionAsCaught();

                                                    b.beginIfThenElse();
                                                        b.beginIsExceptionGroup(); // if
                                                            b.emitLoadException();
                                                            b.emitLoadLocal(exceptionOrig);
                                                        b.endIsExceptionGroup();

                                                        b.beginBlock(); // then (explicit raises and reraises)
                                                            b.beginStoreLocal(exceptionAcc);
                                                                b.beginHandleExceptionsInHandler();
                                                                    b.emitLoadException(); // handler_i_ex (exception thrown in this handler)
                                                                    b.emitLoadLocal(exceptionAcc);
                                                                    b.emitLoadLocal(exceptionOrig);
                                                                    b.emitLoadLocal(handlerType);
                                                                b.endHandleExceptionsInHandler();
                                                            b.endStoreLocal();
                                                        b.endBlock();

                                                        b.beginBlock(); // else (new exceptions raised)
                                                            b.beginSetCurrentException();
                                                                b.emitLoadLocal(matchedExceptions);
                                                            b.endSetCurrentException();

                                                            b.beginTryCatch();
                                                                b.beginThrow(); // "try"
                                                                    b.emitLoadException(); // handler_i_ex (exception thrown in this handler)
                                                                b.endThrow();

                                                                b.beginBlock(); // catch and insert into exception group
                                                                    b.beginStoreLocal(exceptionAcc);
                                                                        b.beginHandleExceptionsInHandler();
                                                                            b.emitLoadException();
                                                                            b.emitLoadLocal(exceptionAcc);
                                                                            b.emitLoadLocal(exceptionOrig);
                                                                            b.emitLoadConstant(PNone.NONE);
                                                                        b.endHandleExceptionsInHandler();
                                                                    b.endStoreLocal();
                                                                b.endBlock();
                                                            b.endTryCatch();

                                                            b.beginSetCurrentException();
                                                                b.emitLoadLocal(exceptionOrig);
                                                            b.endSetCurrentException();
                                                        b.endBlock();
                                                    b.endIfThenElse();
                                                b.endBlock(); // catch (exception thrown in this handler)
                                            b.endTryCatchOtherwise();
                                        } else { // bare except
                                            b.beginBlock();
                                                b.beginTryCatch();
                                                    b.beginBlock(); // try
                                                        b.beginSetCurrentException();
                                                            b.emitLoadLocal(matchedExceptions);
                                                        b.endSetCurrentException();

                                                        saveInExceptStarState = inExceptStar;
                                                        inExceptStar = true;

                                                        visitSequence(handler.body);

                                                        inExceptStar = saveInExceptStarState;
                                                        b.beginSetCurrentException();
                                                            b.emitLoadLocal(exceptionOrig);
                                                        b.endSetCurrentException();
                                                    b.endBlock();

                                                    b.beginBlock(); // catch (exception thrown in bare handler)
                                                        b.beginStoreLocal(exceptionAcc);
                                                            b.beginHandleExceptionsInHandler();
                                                                b.emitLoadException(); // handler_i_ex (exception thrown in bare handler)
                                                                b.emitLoadLocal(exceptionAcc);
                                                                b.emitLoadLocal(exceptionOrig);
                                                                b.emitLoadLocal(handlerType);
                                                            b.endHandleExceptionsInHandler();
                                                        b.endStoreLocal();
                                                    b.endBlock();
                                                b.endTryCatch();
                                            b.endBlock();
                                        }
                                    b.endBlock(); // handler body
                                b.endIfThen();

                                endSourceSection(b, newStatement);
                            } // end handler loop

                            b.beginBlock(); // bundle up unmatched exceptions into exceptionAcc and throw them
                                b.beginStoreLocal(exceptionAcc);
                                    b.beginHandleExceptionsInHandler();
                                        b.emitLoadLocal(unmatchedExceptions);
                                        b.emitLoadLocal(exceptionAcc);
                                        b.emitLoadLocal(exceptionOrig);
                                        b.emitLoadConstant(PNone.NONE);
                                    b.endHandleExceptionsInHandler();
                                b.endStoreLocal();
                            b.endBlock();

                            b.beginIfThen();
                                b.beginIsNotNone();
                                    b.emitLoadLocal(exceptionAcc);
                                b.endIsNotNone();
                                b.beginReraise();
                                    // exceptionAcc is a PBaseExceptionGroup and
                                    // needs to be converted into PException
                                    b.beginEncapsulateExceptionGroup();
                                        b.emitLoadLocal(exceptionAcc);
                                        b.emitLoadLocal(exceptionOrig);
                                    b.endEncapsulateExceptionGroup();
                                b.endReraise();
                            b.endIfThen();
                        b.endBlock(); // try (all handlers)

                        b.beginBlock(); // catch (final, all-encompassing exception group)
                            emitRestoreCurrentException(savedException);

                            b.beginReraise();
                                b.emitLoadException(); // handler_ex (final, all-encompassing exception group)
                            b.endReraise();
                        b.endBlock(); // catch (final, all-encompassing exception group)
                    b.endTryCatchOtherwise();

                    exitSaveExceptionBlock(prevPrevEx);

                    b.emitBranch(afterElse);

                b.endBlock(); // catch

            b.endTryCatch();

            if (node.orElse != null) {
                visitSequence(node.orElse);
            }
            b.emitLabel(afterElse);

            b.endBlock(); // outermost block
            // @formatter:on
        }

        private void emitSaveCurrentException(BytecodeLocal savedException) {
            b.beginStoreLocal(savedException);
            b.emitGetCurrentException();
            b.endStoreLocal();
        }

        private void beginSetCurrentException(boolean clearGeneratorException) {
            if (generatorExceptionStateLocal != null) {
                b.beginSetCurrentGeneratorException(generatorExceptionStateLocal, clearGeneratorException);
            } else {
                b.beginSetCurrentException();
            }
        }

        private void endSetCurrentException() {
            if (generatorExceptionStateLocal != null) {
                b.endSetCurrentGeneratorException();
            } else {
                b.endSetCurrentException();
            }
        }

        private void emitSetCurrentException() {
            beginSetCurrentException(false);
            b.emitLoadException();
            endSetCurrentException();
        }

        private void emitRestoreCurrentException(BytecodeLocal savedException) {
            // in top most except block we are restoring either to NO_EXCEPTION or to caller
            // exception, so we clear the generator exception
            beginSetCurrentException(inTopMostSaveExceptionBlock());
            b.emitLoadLocal(savedException);
            endSetCurrentException();
        }

        private void emitUnbindHandlerVariable(ExceptHandlerTy.ExceptHandler handler) {
            b.beginBlock();
            // Store None to the variable just in case the handler deleted it.
            beginStoreLocal(handler.name, b);
            b.emitLoadConstant(PNone.NONE);
            endStoreLocal(handler.name, b);
            emitDelLocal(handler.name, b);
            b.endBlock();
        }

        @Override
        public Void visit(StmtTy.TryStar node) {
            boolean newStatement = beginSourceSection(node, b);
            emitTraceLineChecked(node, b);
            if (node.finalBody != null && node.finalBody.length != 0) {
                /**
                 * In Python, an uncaught exception becomes the "current" exception inside a finally
                 * block. The finally body can itself throw, in which case it replaces the exception
                 * being thrown. For such a scenario, we have to be careful to restore the "current"
                 * exception using a try-finally.
                 *
                 * In pseudocode, the implementation looks like:
                 * @formatter:off
                 * try {
                 *   try_catch_else
                 * } catch uncaught_ex {
                 *   save current exception
                 *   set the current exception to uncaught_ex
                 *   markCaught(uncaught_ex)
                 *   try {
                 *     finally_body
                 *   } catch handler_ex {
                 *     restore current exception
                 *     markCaught(handler_ex)
                 *     reraise handler_ex
                 *   } otherwise {
                 *     restore current exception
                 *   }
                 *   reraise uncaught_ex
                 * } otherwise {
                 *   finally_body
                 * }
                 */
                b.beginTryCatchOtherwise(() -> {
                    b.beginBlock(); // finally
                        visitSequence(node.finalBody);
                    b.endBlock();
                });

                    emitTryExceptElse(node); // try-except-else

                    b.beginBlock(); // catch uncaught exceptions
                        BytecodeLocal savedException = b.createLocal();
                        BytecodeLocal prevPrevSaved = enterSaveExceptionBlock(savedException);

                        emitSaveCurrentException(savedException);
                        emitSetCurrentException();
                        // Mark this location for the stack trace.
                        b.beginMarkExceptionAsCaught();
                            b.emitLoadException();
                        b.endMarkExceptionAsCaught();

                        b.beginTryCatchOtherwise(() -> emitRestoreCurrentException(savedException));
                            b.beginBlock(); // try finally body
                                visitSequence(node.finalBody);
                            b.endBlock(); // try finally body

                            b.beginBlock(); // catch exception in finally
                                emitRestoreCurrentException(savedException);

                                b.beginMarkExceptionAsCaught();
                                    b.emitLoadException();
                                b.endMarkExceptionAsCaught();

                                b.beginReraise();
                                    b.emitLoadException();
                                b.endReraise();
                            b.endBlock(); // catch exception in finally
                        b.endTryCatchOtherwise();

                        b.beginReraise();
                            b.emitLoadException();
                        b.endReraise();

                        exitSaveExceptionBlock(prevPrevSaved);
                    b.endBlock(); // catch uncaught exceptions
                b.endTryCatchOtherwise();
                // @formatter:on
            } else {
                emitTryExceptElse(node);
            }

            endSourceSection(b, newStatement);
            return null;
        }

        @Override
        public Void visit(ExceptHandlerTy.ExceptHandler node) {
            throw new UnsupportedOperationException("" + node.getClass());
        }

        @Override
        public Void visit(StmtTy.While node) {
            boolean newStatement = beginSourceSection(node, b);
            emitTraceLineChecked(node, b);
            boolean saveInExceptStar = inExceptStar;
            inExceptStar = false;
            b.beginBlock();

            BytecodeLabel oldBreakLabel = breakLabel;
            BytecodeLabel oldContinueLabel = continueLabel;

            BytecodeLabel currentBreakLabel = b.createLabel();
            breakLabel = currentBreakLabel;

            b.beginWhile();

            b.beginBlock();
            b.emitTraceLineAtLoopHeader(currentLocation.startLine);
            visitCondition(node.test);
            b.endBlock();

            b.beginBlock();
            continueLabel = b.createLabel();
            visitStatements(node.body);
            b.emitLabel(continueLabel);
            b.endBlock();

            b.endWhile();

            breakLabel = oldBreakLabel;
            continueLabel = oldContinueLabel;
            visitStatements(node.orElse);
            b.emitLabel(currentBreakLabel);

            b.endBlock();
            inExceptStar = saveInExceptStar;
            endSourceSection(b, newStatement);
            return null;
        }

        private void visitWithRecurse(WithItemTy[] items, int index, StmtTy[] body, boolean async) {
            /**
             * For a with-statement like
             *
             *   with foo as x:
             *     bar
             *
             * we generate code that performs (roughly)
             *
             * @formatter:off
             *   contextManager = foo
             *   resolve __enter__ and __exit__
             *   value = __enter__()
             *   try {
             *     x = value
             *     bar
             *   } catch ex {
             *     if not __exit__(...):
             *       raise
             *   } otherwise {
             *     call __exit__(None, None, None)
             *   }
             * @formatter:on
             *
             * When there are multiple context managers, they are recursively generated (where "bar"
             * is). Once we have entered all of the context managers, we emit the body.
             */
            WithItemTy item = items[index];
            boolean newStatement = beginSourceSection(item, b);
            emitTraceLineChecked(item, b);
            b.beginBlock();

            BytecodeLocal contextManager = b.createLocal();
            b.beginStoreLocal(contextManager);
            item.contextExpr.accept(this);
            b.endStoreLocal();

            BytecodeLocal exit = b.createLocal();
            BytecodeLocal value = b.createLocal();
            if (async) {
                // call __aenter__
                b.beginAsyncContextManagerEnter(exit, value);
                b.emitLoadLocal(contextManager);
                b.endAsyncContextManagerEnter();
                // await the result
                b.beginStoreLocal(value);
                emitAwait(() -> b.emitLoadLocal(value));
                b.endStoreLocal();
            } else {
                // call __enter__
                b.beginContextManagerEnter(exit, value);
                b.emitLoadLocal(contextManager);
                b.endContextManagerEnter();
            }

            Runnable finallyHandler;
            if (async) {
                finallyHandler = () -> emitAwait(() -> {
                    b.beginBlock();
                    emitTraceLineChecked(items[index], b);
                    b.beginAsyncContextManagerCallExit();
                    b.emitLoadConstant(PNone.NONE);
                    b.emitLoadLocal(exit);
                    b.emitLoadLocal(contextManager);
                    b.endAsyncContextManagerCallExit();
                    b.endBlock();
                });
            } else {
                finallyHandler = () -> {
                    // call __exit__
                    emitTraceLineChecked(items[index], b);
                    b.beginContextManagerExit();
                    b.emitLoadConstant(PNone.NONE);
                    b.emitLoadLocal(exit);
                    b.emitLoadLocal(contextManager);
                    b.endContextManagerExit();
                };
            }
            b.beginTryCatchOtherwise(finallyHandler);
            b.beginBlock(); // try
            if (item.optionalVars != null) {
                item.optionalVars.accept(new StoreVisitor(() -> b.emitLoadLocal(value)));
            }
            if (index < items.length - 1) {
                visitWithRecurse(items, index + 1, body, async);
            } else {
                visitSequence(body);
                emitTraceLineChecked(item, b);
            }
            b.endBlock(); // try

            b.beginBlock(); // catch

            // Mark this location for the stack trace.
            b.beginMarkExceptionAsCaught();
            b.emitLoadException();
            b.endMarkExceptionAsCaught();

            // exceptional exit
            if (async) {
                // call, await, and handle result of __aexit__
                BytecodeLocal savedException = b.createLocal();
                BytecodeLocal prevPrevSaved = enterSaveExceptionBlock(savedException);
                emitSaveCurrentException(savedException);
                emitSetCurrentException();

                // @formatter:off
                b.beginAsyncContextManagerExit();
                    b.emitLoadException();
                    b.beginBlock();
                        BytecodeLocal tmp = b.createLocal();
                        b.beginStoreLocal(tmp);
                        emitAwait(() -> {
                            b.beginAsyncContextManagerCallExit();
                            b.emitLoadException();
                            b.emitLoadLocal(exit);
                            b.emitLoadLocal(contextManager);
                            b.endAsyncContextManagerCallExit();
                        });
                        b.endStoreLocal();
                        // restore the exception just before invoking the AsyncContextManagerExit operation
                        emitRestoreCurrentException(savedException);
                        b.emitLoadLocal(tmp);
                    b.endBlock();
                b.endAsyncContextManagerExit();
                // @formatter:on

                exitSaveExceptionBlock(prevPrevSaved);
            } else {
                // call __exit__
                b.beginContextManagerExit();
                b.emitLoadException();
                b.emitLoadLocal(exit);
                b.emitLoadLocal(contextManager);
                b.endContextManagerExit();
            }
            b.endBlock(); // catch

            b.endTryCatchOtherwise();

            b.endBlock();
            endSourceSection(b, newStatement);
        }

        @Override
        public Void visit(StmtTy.With node) {
            boolean newStatement = beginSourceSection(node, b);
            visitWithRecurse(node.items, 0, node.body, false);
            emitTraceLineChecked(node, b);
            endSourceSection(b, newStatement);
            return null;
        }

        @Override
        public Void visit(WithItemTy node) {
            throw new UnsupportedOperationException("" + node.getClass());
        }

        @Override
        public Void visit(StmtTy.Break aThis) {
            boolean newStatement = beginSourceSection(aThis, b);
            emitTraceLineChecked(aThis, b);
            if (inExceptStar) {
                ctx.errorCallback.onError(ErrorType.Syntax, currentLocation, "'break', 'continue' and 'return' cannot appear in an except* block");
            }
            if (breakLabel == null) {
                ctx.errorCallback.onError(ErrorType.Syntax, currentLocation, "'break' outside loop");
            }
            b.emitBranch(breakLabel);
            endSourceSection(b, newStatement);
            return null;
        }

        @Override
        public Void visit(StmtTy.Continue aThis) {
            boolean newStatement = beginSourceSection(aThis, b);
            emitTraceLineChecked(aThis, b);
            if (inExceptStar) {
                ctx.errorCallback.onError(ErrorType.Syntax, currentLocation, "'break', 'continue' and 'return' cannot appear in an except* block");
            }
            if (continueLabel == null) {
                ctx.errorCallback.onError(ErrorType.Syntax, currentLocation, "'continue' not properly in loop");
            }
            b.emitBranch(continueLabel);
            endSourceSection(b, newStatement);
            return null;
        }

        @Override
        public Void visit(TypeAlias node) {
            // store the value to a variable and also produce it as the result of this block
            b.beginBlock();
            String name = ((ExprTy.Name) node.name).id;
            beginStoreLocal(name, b);

            if (node.isGeneric()) {
                RootNodeCompiler typeParamsCompiler = new RootNodeCompiler(ctx, RootNodeCompiler.this, null, node, node.typeParams, futureFeatures);
                BytecodeDSLCompilerResult body = createRootNodeCompilerFor(node, typeParamsCompiler).compileTypeAliasBody(node);
                BytecodeDSLCompilerResult typeParamsFun = typeParamsCompiler.compileTypeAliasTypeParameters(name, body.codeUnit(), node);

                String typeParamsName = "<generic parameters of " + name + ">";
                b.beginCallNilaryMethod();
                emitMakeFunction(typeParamsFun.codeUnit(), node.typeParams, typeParamsName, null, null);
                b.endCallNilaryMethod();
            } else {
                BytecodeDSLCompilerResult body = createRootNodeCompilerFor(node).compileTypeAliasBody(node);
                emitBuildTypeAlias(body.codeUnit(), node);
            }

            endStoreLocal(name, b);
            emitReadLocal(name, b);
            b.endBlock();
            return null;
        }

        public void emitBuildTypeAlias(BytecodeDSLCodeUnit body, TypeAlias node) {
            String name = ((ExprTy.Name) node.name).id;
            // @formatter:off
            b.beginMakeTypeAliasType();
                emitPythonConstant(toTruffleStringUncached(name), b);
                if (node.isGeneric()) {
                    visitTypeParams(node.typeParams);
                } else {
                    b.emitLoadNull();
                }
                emitMakeFunction(body, node, name, null, null);
            b.endMakeTypeAliasType();
            // @formatter:on
        }

        @Override
        public Void visit(TypeVar node) {
            b.beginBlock();

            // store the value to the variable
            beginStoreLocal(node.name, b);
            if (node.bound != null) {
                BytecodeDSLCompilerResult code = createRootNodeCompilerFor(node).compileBoundTypeVar(node);
                int kind = node.bound instanceof Tuple ? MakeTypeParamKind.TYPE_VAR_WITH_CONSTRAINTS : MakeTypeParamKind.TYPE_VAR_WITH_BOUND;
                // @formatter:off
                b.beginMakeTypeParam(kind);
                    emitPythonConstant(toTruffleStringUncached(node.name), b);
                    emitMakeFunction(code.codeUnit(), node, node.name, null, null);
                b.endMakeTypeParam();
                // @formatter:on
            } else {
                // @formatter:off
                b.beginMakeTypeParam(MakeTypeParamKind.TYPE_VAR);
                    emitPythonConstant(toTruffleStringUncached(node.name), b);
                    b.emitLoadNull(); // boundOrConstraints
                b.endMakeTypeParam();
                // @formatter:on
            }
            endStoreLocal(node.name, b);

            // produce the value stored to the variable as the result of this block
            emitReadLocal(node.name, b);

            b.endBlock();
            return null;
        }

        @Override
        public Void visit(ParamSpec node) {
            b.beginBlock();

            // store the value to the variable
            // @formatter:off
            beginStoreLocal(node.name, b);
                b.beginMakeTypeParam(MakeTypeParamKind.PARAM_SPEC);
                    emitPythonConstant(toTruffleStringUncached(node.name), b);
                    b.emitLoadNull();
                b.endMakeTypeParam();
            endStoreLocal(node.name, b);
            // @formatter:on

            // produce the value stored to the variable as the result of this block
            emitReadLocal(node.name, b);

            b.endBlock();
            return null;
        }

        @Override
        public Void visit(TypeVarTuple node) {
            b.beginBlock();

            // store the value to the variable
            // @formatter:off
            beginStoreLocal(node.name, b);
                b.beginMakeTypeParam(MakeTypeParamKind.TYPE_VAR_TUPLE);
                    emitPythonConstant(toTruffleStringUncached(node.name), b);
                    b.emitLoadNull(); // boundOrConstraints
                b.endMakeTypeParam();
            endStoreLocal(node.name, b);
            // formatter:@on

            // produce the value stored to the variable as the result of this block
            emitReadLocal(node.name, b);

            b.endBlock();
            return null;
        }

        @Override
        public Void visit(StmtTy.Pass node) {
            emitTraceLineChecked(node, b);
            return null;
        }
    }
}
