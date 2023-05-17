package com.oracle.graal.python.compiler.bytecode_dsl;

import static com.oracle.graal.python.compiler.CompilationScope.Class;
import static com.oracle.graal.python.util.PythonUtils.toTruffleStringUncached;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;

import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.code.PCode;
import com.oracle.graal.python.builtins.objects.ellipsis.PEllipsis;
import com.oracle.graal.python.builtins.objects.function.PArguments;
import com.oracle.graal.python.builtins.objects.function.PKeyword;
import com.oracle.graal.python.compiler.CompilationScope;
import com.oracle.graal.python.compiler.Compiler;
import com.oracle.graal.python.compiler.Compiler.ConstantCollection;
import com.oracle.graal.python.compiler.OpCodes.CollectionBits;
import com.oracle.graal.python.compiler.bytecode_dsl.BytecodeDSLCompiler.BytecodeDSLCompilerContext;
import com.oracle.graal.python.compiler.bytecode_dsl.BytecodeDSLCompiler.BytecodeDSLCompilerResult;
import com.oracle.graal.python.compiler.RaisePythonExceptionErrorCallback;
import com.oracle.graal.python.compiler.Unparser;
import com.oracle.graal.python.nodes.StringLiterals;
import com.oracle.graal.python.nodes.bytecode_dsl.BytecodeDSLCodeUnit;
import com.oracle.graal.python.nodes.bytecode_dsl.PBytecodeDSLRootNode;
import com.oracle.graal.python.nodes.bytecode_dsl.PBytecodeDSLRootNodeGen;
import com.oracle.graal.python.nodes.bytecode_dsl.PBytecodeDSLRootNodeGen.Builder;
import com.oracle.graal.python.pegparser.ErrorCallback;
import com.oracle.graal.python.pegparser.FutureFeature;
import com.oracle.graal.python.pegparser.ErrorCallback.ErrorType;
import com.oracle.graal.python.pegparser.scope.Scope;
import com.oracle.graal.python.pegparser.scope.Scope.DefUse;
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
import com.oracle.graal.python.pegparser.sst.KeywordTy;
import com.oracle.graal.python.pegparser.sst.MatchCaseTy;
import com.oracle.graal.python.pegparser.sst.ModTy;
import com.oracle.graal.python.pegparser.sst.OperatorTy;
import com.oracle.graal.python.pegparser.sst.PatternTy;
import com.oracle.graal.python.pegparser.sst.SSTNode;
import com.oracle.graal.python.pegparser.sst.StmtTy;
import com.oracle.graal.python.pegparser.sst.UnaryOpTy;
import com.oracle.graal.python.pegparser.sst.WithItemTy;
import com.oracle.graal.python.pegparser.sst.ExprTy.DictComp;
import com.oracle.graal.python.pegparser.sst.ExprTy.GeneratorExp;
import com.oracle.graal.python.pegparser.sst.ExprTy.Lambda;
import com.oracle.graal.python.pegparser.sst.ExprTy.ListComp;
import com.oracle.graal.python.pegparser.sst.ExprTy.SetComp;
import com.oracle.graal.python.pegparser.sst.StmtTy.AsyncFunctionDef;
import com.oracle.graal.python.pegparser.tokenizer.SourceRange;
import com.oracle.graal.python.util.PythonUtils;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.bytecode.BytecodeConfig;
import com.oracle.truffle.api.bytecode.BytecodeLabel;
import com.oracle.truffle.api.bytecode.BytecodeLocal;
import com.oracle.truffle.api.bytecode.BytecodeRootNodes;
import com.oracle.truffle.api.bytecode.BytecodeParser;
import com.oracle.truffle.api.strings.TruffleString;

public class RootNodeCompiler implements BaseBytecodeDSLVisitor<BytecodeDSLCompilerResult> {
    private final BytecodeDSLCompilerContext ctx;
    private final SSTNode startNode;
    private final Map<String, BytecodeLocal> locals;
    private final Map<String, BytecodeLocal> cellLocals;
    private final Map<String, BytecodeLocal> freeLocals;
    private final Scope scope;
    private final CompilationScope scopeType;
    private final GeneratorOrCoroutineKind generatorOrCoroutineKind;

    private final boolean isInteractive;
    private final EnumSet<FutureFeature> futureFeatures;

    private final HashMap<String, Integer> varnames;
    private final HashMap<String, Integer> cellvars;
    private final HashMap<String, Integer> freevars;
    private final int[] cell2arg;
    private final HashMap<Object, Integer> constants = new HashMap<>();
    private final HashMap<String, Integer> names = new HashMap<>();

    private final ErrorCallback errorCallback;
    private SourceRange currentLocation;

    public RootNodeCompiler(BytecodeDSLCompilerContext ctx, SSTNode rootNode, EnumSet<FutureFeature> futureFeatures) {
        this.ctx = ctx;
        this.startNode = rootNode;
        this.locals = new HashMap<>();
        this.cellLocals = new HashMap<>();
        this.freeLocals = new HashMap<>();
        this.scope = ctx.scopeEnvironment.lookupScope(rootNode);
        this.scopeType = getScopeType(scope, rootNode);
        this.generatorOrCoroutineKind = GeneratorOrCoroutineKind.getKind(scope);

        this.isInteractive = rootNode instanceof ModTy.Interactive;
        this.futureFeatures = futureFeatures;

        varnames = new HashMap<>();
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

        cellvars = scope.getSymbolsByType(EnumSet.of(Scope.DefUse.Cell), 0);
        if (scope.needsClassClosure()) {
            assert scopeType == Class;
            assert cellvars.isEmpty();
            cellvars.put("__class__", 0);
        }

        int[] cell2argValue = new int[cellvars.size()];
        boolean hasArgCell = false;
        Arrays.fill(cell2argValue, -1);
        for (String cellvar : cellvars.keySet()) {
            if (varnames.containsKey(cellvar)) {
                cell2argValue[cellvars.get(cellvar)] = varnames.get(cellvar);
                hasArgCell = true;
            }
        }
        this.cell2arg = hasArgCell ? cell2argValue : null;
        freevars = scope.getSymbolsByType(EnumSet.of(Scope.DefUse.Free, Scope.DefUse.DefFreeClass), 0);

        errorCallback = new RaisePythonExceptionErrorCallback(ctx.source, false);
    }

    private static CompilationScope getScopeType(Scope scope, SSTNode rootNode) {
        if (scope.isModule()) {
            return CompilationScope.Module;
        } else if (scope.isClass()) {
            return CompilationScope.Class;
        } else if (scope.isFunction()) {
            if (rootNode instanceof Lambda) {
                return CompilationScope.Lambda;
            } else if (rootNode instanceof AsyncFunctionDef) {
                return CompilationScope.AsyncFunction;
            } else {
                return CompilationScope.Function;
            }
        } else {
            assert rootNode instanceof DictComp || rootNode instanceof ListComp || rootNode instanceof SetComp || rootNode instanceof GeneratorExp;
            return CompilationScope.Comprehension;
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

    private static <T> T addObject(HashMap<T, Integer> dict, T o) {
        Integer v = dict.get(o);
        if (v == null) {
            v = dict.size();
            dict.put(o, v);
        }
        return o;
    }

    private static TruffleString[] orderedTruffleStringArray(HashMap<String, Integer> map) {
        return orderedKeys(map, new TruffleString[0], PythonUtils::toTruffleStringUncached);
    }

    private BytecodeDSLCompilerResult compileRootNode(String name, ArgumentInfo argumentInfo, SourceRange sourceRange, BytecodeParser<Builder> parser) {
        BytecodeRootNodes<PBytecodeDSLRootNode> nodes = PBytecodeDSLRootNodeGen.create(BytecodeConfig.WITH_SOURCE, parser);
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

        BytecodeDSLCodeUnit codeUnit = new BytecodeDSLCodeUnit(toTruffleStringUncached(name), toTruffleStringUncached(ctx.getQualifiedName(scope)),
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
                        null,
                        nodes);
        rootNode.setMetadata(codeUnit);
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
    }

    protected final void checkForbiddenName(String id, NameOperation context) {
        if (context == NameOperation.BeginWrite) {
            if (id.equals("__debug__")) {
                errorCallback.onError(ErrorType.Syntax, currentLocation, "cannot assign to __debug__");
            }
        }
        if (context == NameOperation.Delete) {
            if (id.equals("__debug__")) {
                errorCallback.onError(ErrorType.Syntax, currentLocation, "cannot delete __debug__");
            }
        }
    }

    private void checkForbiddenArgs(ArgumentsTy args) {
        if (args != null) {
            if (args.posOnlyArgs != null) {
                for (ArgTy arg : args.posOnlyArgs) {
                    checkForbiddenName(arg.arg, NameOperation.BeginWrite);
                }
            }
            if (args.args != null) {
                for (ArgTy arg : args.args) {
                    checkForbiddenName(arg.arg, NameOperation.BeginWrite);
                }
            }
            if (args.kwOnlyArgs != null) {
                for (ArgTy arg : args.kwOnlyArgs) {
                    checkForbiddenName(arg.arg, NameOperation.BeginWrite);
                }
            }
            if (args.varArg != null) {
                checkForbiddenName(args.varArg.arg, NameOperation.BeginWrite);
            }
            if (args.kwArg != null) {
                checkForbiddenName(args.kwArg.arg, NameOperation.BeginWrite);
            }
        }
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

    // -------------- sources --------------

    void beginNode(SSTNode node, Builder b) {
        SourceRange sourceRange = node.getSourceRange();
        this.currentLocation = sourceRange;

        if (ctx.source.hasCharacters()) {
            int startOffset = ctx.source.getLineStartOffset(sourceRange.startLine) + sourceRange.startColumn;
            int endOffset = ctx.source.getLineStartOffset(sourceRange.endLine) + sourceRange.endColumn;
            int length = endOffset - startOffset;
            if (length == 0) {
                startOffset = 0;
            }
            b.beginSourceSection(startOffset, length);
        }
        b.beginBlock();
    }

    @SuppressWarnings("unused")
    void endNode(SSTNode node, Builder b) {
        b.endBlock();
        if (ctx.source.hasCharacters()) {
            b.endSourceSection();
        }
    }

    // --------------------- visitor ---------------------------

    @Override
    public BytecodeDSLCompilerResult visit(ModTy.Module node) {
        return compileRootNode("<module>", ArgumentInfo.NO_ARGS, node.getSourceRange(), b -> {
            b.beginRoot(ctx.language);
            b.beginSource(ctx.source);
            beginNode(node, b);

            setUpFrame(null, b);

            visitModuleBody(node.body, b);

            endNode(node, b);
            b.endSource();
            b.endRoot();
        });
    }

    @Override
    public BytecodeDSLCompilerResult visit(ModTy.Expression node) {
        return compileRootNode("<module>", ArgumentInfo.NO_ARGS, node.getSourceRange(), b -> {
            b.beginRoot(ctx.language);
            b.beginSource(ctx.source);
            beginNode(node, b);

            setUpFrame(null, b);

            b.beginReturn();
            new StatementCompiler(b).visitNode(node.body);
            b.endReturn();

            endNode(node, b);
            b.endSource();
            b.endRoot();
        });
    }

    @Override
    public BytecodeDSLCompilerResult visit(ModTy.Interactive node) {
        return compileRootNode("<interactive>", ArgumentInfo.NO_ARGS, node.getSourceRange(), b -> {
            b.beginRoot(ctx.language);
            b.beginSource(ctx.source);
            beginNode(node, b);

            setUpFrame(null, b);

            visitModuleBody(node.body, b);

            endNode(node, b);
            b.endSource();
            b.endRoot();
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
                        b.beginReturn();
                        b.emitLoadConstant(PNone.NONE);
                        b.endReturn();
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
                    b.beginReturn();
                    b.emitLoadConstant(PNone.NONE);
                    b.endReturn();
                    return;
                }

                for (; i < body.length; i++) {
                    StmtTy bodyNode = body[i];
                    if (i == body.length - 1) {
                        if (bodyNode instanceof StmtTy.Expr expr) {
                            // Return the value of the last statement for interop eval.
                            b.beginReturn();
                            expr.value.accept(statementCompiler);
                            b.endReturn();
                        } else {
                            bodyNode.accept(statementCompiler);
                            b.beginReturn();
                            b.emitLoadConstant(PNone.NONE);
                            b.endReturn();
                        }
                    } else {
                        bodyNode.accept(statementCompiler);
                    }
                }
            }
        } else {
            b.beginReturn();
            b.emitLoadConstant(PNone.NONE);
            b.endReturn();
        }
    }

    private static TruffleString getDocstring(StmtTy[] body) {
        if (body != null && body.length > 0) {
            StmtTy stmt = body[0];
            if (stmt instanceof StmtTy.Expr expr //
                            && expr.value instanceof ExprTy.Constant constant //
                            && constant.value.kind == ConstantValue.Kind.RAW) {
                return constant.value.getRaw(TruffleString.class);
            }
        }
        return null;
    }

    /**
     * To compile a generator/coroutine function f, we build a separate root node f' that executes
     * the body of f. We compile f itself to code that creates and returns a generator/coroutine
     * that uses f'.
     *
     * All of the locals are allocated and stored in f'. Since the generator/coroutine is created
     * using f's argument array, arguments are transparently forwarded to f' (there's no need for
     * closures).
     */
    private enum GeneratorOrCoroutineKind {
        NONE,
        GENERATOR,
        COROUTINE,
        ASYNC_GENERATOR;

        static GeneratorOrCoroutineKind getKind(Scope scope) {
            if (scope.isGenerator() && scope.isCoroutine()) {
                return ASYNC_GENERATOR;
            } else if (scope.isGenerator()) {
                return GENERATOR;
            } else if (scope.isCoroutine()) {
                return COROUTINE;
            } else {
                return NONE;
            }
        }
    }

    @Override
    public BytecodeDSLCompilerResult visit(StmtTy.FunctionDef node) {
        return compileRootNode(node.name, ArgumentInfo.fromArguments(node.args), node.getSourceRange(),
                        b -> emitFunctionDef(node, node.args, node.body, b, getDocstring(node.body), false));
    }

    @Override
    public BytecodeDSLCompilerResult visit(StmtTy.AsyncFunctionDef node) {
        return compileRootNode(node.name, ArgumentInfo.fromArguments(node.args), node.getSourceRange(),
                        b -> emitFunctionDef(node, node.args, node.body, b, getDocstring(node.body), false));
    }

    @Override
    public BytecodeDSLCompilerResult visit(ExprTy.Lambda node) {
        return compileRootNode("<lambda>", ArgumentInfo.fromArguments(node.args), node.getSourceRange(),
                        b -> emitFunctionDef(node, node.args, new SSTNode[]{node.body}, b, null, !scope.isGenerator()));
    }

    private void emitFunctionDef(SSTNode node, ArgumentsTy args, SSTNode[] body, Builder b, Object docstring, boolean isRegularLambda) {
        b.beginRoot(ctx.language);
        b.beginSource(ctx.source);
        beginNode(node, b);
        checkForbiddenArgs(args);
        setUpFrame(args, b);

        int i = 0;
        if (docstring != null) {
            i++;
            if (ctx.optimizationLevel < 2) {
                addObject(constants, docstring);
            } else {
                addObject(constants, PNone.NONE);
            }
        } else {
            addObject(constants, PNone.NONE);
        }

        StatementCompiler statementCompiler = new StatementCompiler(b);

        if (isRegularLambda) {
            assert i == 0;
            assert body[0] instanceof ExprTy;
            b.beginReturn();
            body[0].accept(statementCompiler);
            b.endReturn();
        } else {
            for (; i < body.length; i++) {
                body[i].accept(statementCompiler);
            }
            b.beginReturn();
            emitPythonConstant(PNone.NONE, b);
            b.endReturn();
        }

        endNode(node, b);
        b.endSource();
        b.endRoot();
    }

    @Override
    public BytecodeDSLCompilerResult visit(StmtTy.ClassDef node) {
        return compileRootNode(node.name, ArgumentInfo.NO_ARGS, node.getSourceRange(), b -> {
            b.beginRoot(ctx.language);
            b.beginSource(ctx.source);
            beginNode(node, b);

            if (containsAnnotations(node.body)) {
                b.emitSetupAnnotations();
            }

            setUpFrame(null, b);

            beginStoreLocal("__module__", b);
            emitReadLocal("__name__", b);
            endStoreLocal("__module__", b);

            beginStoreLocal("__qualname__", b);
            emitPythonConstant(toTruffleStringUncached(ctx.getQualifiedName(scope)), b);
            endStoreLocal("__qualname__", b);

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

            if (scope.needsClassClosure()) {
                beginStoreLocal("__classcell__", b);
                b.emitLoadLocal(cellLocals.get("__class__"));
                endStoreLocal("__classcell__", b);

                b.beginReturn();
                b.emitLoadLocal(cellLocals.get("__class__"));
                b.endReturn();
            } else {
                b.beginReturn();
                b.emitLoadConstant(PNone.NONE);
                b.endReturn();
            }

            endNode(node, b);
            b.endSource();
            b.endRoot();
        });
    }

    private void beginComprehension(ComprehensionTy comp, int index, Builder b) {
        BytecodeLocal localIter = b.createLocal();
        BytecodeLocal localValue = b.createLocal();
        StatementCompiler statementCompiler = new StatementCompiler(b);

        b.beginStoreLocal(localIter);
        b.beginGetIter();
        if (index == 0) {
            b.emitLoadArgument(PArguments.USER_ARGUMENTS_OFFSET);
        } else {
            comp.iter.accept(statementCompiler);
        }
        b.endGetIter();
        b.endStoreLocal();

        b.beginWhile();

        b.beginForIterate(localValue);
        b.emitLoadLocal(localIter);
        b.endForIterate();

        b.beginBlock();

        comp.target.accept(statementCompiler.new StoreVisitor(() -> b.emitLoadLocal(localValue)));

        if (comp.ifs != null) {
            for (int i = 0; i < comp.ifs.length; i++) {
                b.beginIfThen();
                statementCompiler.visitCondition(comp.ifs[i]);
                b.beginBlock();
            }
        }
    }

    private static void endComprehension(ComprehensionTy comp, Builder b) {
        if (comp.ifs != null) {
            for (int i = 0; i < len(comp.ifs); i++) {
                b.endBlock();
                b.endIfThen();
            }
        }

        b.endBlock();
        b.endWhile();
    }

    private BytecodeDSLCompilerResult buildComprehensionCodeUnit(SSTNode node, ComprehensionTy[] generators, String name,
                    Consumer<StatementCompiler> emptyCollectionProducer,
                    BiConsumer<StatementCompiler, BytecodeLocal> accumulateProducer) {
        return compileRootNode(name, new ArgumentInfo(1, 0, 0, false, false), node.getSourceRange(), b -> {
            StatementCompiler statementCompiler = new StatementCompiler(b);

            b.beginRoot(ctx.language);
            b.beginSource(ctx.source);
            beginNode(node, b);

            setUpFrame(null, b);

            boolean isGenerator = emptyCollectionProducer == null;

            BytecodeLocal collectionLocal = null;
            if (!isGenerator) {
                collectionLocal = b.createLocal();
                b.beginStoreLocal(collectionLocal);
                emptyCollectionProducer.accept(statementCompiler);
                b.endStoreLocal();
            }

            for (int i = 0; i < generators.length; i++) {
                beginComprehension(generators[i], i, b);
            }
            accumulateProducer.accept(statementCompiler, collectionLocal);
            for (int i = generators.length - 1; i >= 0; i--) {
                endComprehension(generators[i], b);
            }

            b.beginReturn();
            if (isGenerator) {
                b.emitLoadConstant(PNone.NONE);
            } else {
                b.emitLoadLocal(collectionLocal);
            }
            b.endReturn();

            endNode(node, b);
            b.endSource();
            b.endRoot();
        });
    }

    @Override
    public BytecodeDSLCompilerResult visit(ExprTy.ListComp node) {
        return buildComprehensionCodeUnit(node, node.generators, "<listcomp>",
                        (statementCompiler) -> statementCompiler.b.emitMakeEmptyList(),
                        (statementCompiler, collection) -> {
                            statementCompiler.b.beginListAppend();
                            statementCompiler.b.emitLoadLocal(collection);
                            node.element.accept(statementCompiler);
                            statementCompiler.b.endListAppend();
                        });
    }

    @Override
    public BytecodeDSLCompilerResult visit(ExprTy.DictComp node) {
        return buildComprehensionCodeUnit(node, node.generators, "<dictcomp>",
                        (statementCompiler) -> statementCompiler.b.emitMakeEmptyDict(),
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
        return buildComprehensionCodeUnit(node, node.generators, "<setcomp>",
                        (statementCompiler) -> statementCompiler.b.emitMakeEmptySet(),
                        (statementCompiler, collection) -> {
                            statementCompiler.b.beginSetAdd();
                            statementCompiler.b.emitLoadLocal(collection);
                            node.element.accept(statementCompiler);
                            statementCompiler.b.endSetAdd();
                        });
    }

    @Override
    public BytecodeDSLCompilerResult visit(ExprTy.GeneratorExp node) {
        return buildComprehensionCodeUnit(node, node.generators, "<generator>",
                        null,
                        (statementCompiler, collection) -> emitYield((statementCompiler_) -> node.element.accept(statementCompiler_), statementCompiler));
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

    private void emitNotImplemented(String what, Builder b) {
        b.beginRaiseNotImplementedError();
        emitPythonConstant(toTruffleStringUncached(what), b);
        b.endRaiseNotImplementedError();
    }

    /*
     * Use this method for values that should show up in co_consts.
     */
    private void emitPythonConstant(Object constant, Builder b) {
        b.emitLoadConstant(addObject(constants, constant));
    }

    /*
     * Use this to emit a yield operation. We have to perform a pre-yield operation that stores the
     * current exception before actually yielding.
     */
    private static void emitYield(Consumer<StatementCompiler> yieldValueProducer, StatementCompiler statementCompiler) {
        statementCompiler.b.beginYield();
        statementCompiler.b.beginPreYield();
        yieldValueProducer.accept(statementCompiler);
        statementCompiler.b.endPreYield();
        statementCompiler.b.endYield();
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
                    b.beginClassLoadCell();
                    b.emitLoadConstant(index);
                    b.emitLoadLocal(local);
                    b.endClassLoadCell();
                } else {
                    b.beginLoadCell();
                    b.emitLoadConstant(index);
                    b.emitLoadLocal(local);
                    b.endLoadCell();
                }
                break;
            case Delete:
                b.beginClearCell();
                b.emitLoadConstant(index);
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
                b.beginCheckUnboundLocal();
                b.emitLoadConstant(varnames.get(mangled));
                b.emitLoadLocal(local);
                b.endCheckUnboundLocal();
                break;
            case Delete:
                b.beginBlock();
                b.beginCheckUnboundLocal();
                b.emitLoadConstant(varnames.get(mangled));
                b.emitLoadLocal(local);
                b.endCheckUnboundLocal();

                b.beginStoreLocal(local);
                b.emitLoadConstant(null);
                b.endStoreLocal();
                b.endBlock();
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

    private void emitNameGlobalOperation(String name, NameOperation op, Builder b) {
        assert locals.get(name) == null;
        names.putIfAbsent(name, names.size());
        TruffleString tsName = toTruffleStringUncached(name);
        switch (op) {
            case Read:
                b.beginReadGlobal();
                b.emitLoadConstant(tsName);
                b.endReadGlobal();
                break;
            case Delete:
                b.beginDeleteGlobal();
                b.emitLoadConstant(tsName);
                b.endDeleteGlobal();
                break;
            case BeginWrite:
                b.beginWriteGlobal();
                break;
            case EndWrite:
                b.emitLoadConstant(tsName);
                b.endWriteGlobal();
                break;
            default:
                throw new UnsupportedOperationException("unknown value: " + op);
        }
    }

    private void emitNameSlowOperation(String name, NameOperation op, Builder b) {
        assert locals.get(name) == null;
        names.putIfAbsent(name, names.size());
        TruffleString tsName = toTruffleStringUncached(name);
        switch (op) {
            case Read:
                b.beginReadName();
                b.emitLoadConstant(tsName);
                b.endReadName();
                break;
            case Delete:
                b.beginDeleteName();
                b.emitLoadConstant(tsName);
                b.endDeleteName();
                break;
            case BeginWrite:
                b.beginWriteName();
                break;
            case EndWrite:
                b.emitLoadConstant(tsName);
                b.endWriteName();
                break;
            default:
                throw new UnsupportedOperationException("unknown value: " + op);
        }
    }

    private void emitNameOperation(String name, NameOperation op, Builder b) {
        checkForbiddenName(name, op);

        String mangled = mangle(name);
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
                    emitNameGlobalOperation(mangled, op, b);
                    return;
                }
            } else if (uses.contains(DefUse.GlobalExplicit)) {
                emitNameGlobalOperation(mangled, op, b);
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
        return locals.get(mangle(name));
    }

    public void setUpFrame(ArgumentsTy args, Builder b) {
        /**
         * This method does two things:
         *
         * 1. It allocates a contiguous region in the frame for Python variables. Some nodes in the
         * GraalPy AST expect locals to be allocated contiguously starting at slot 0. The resultant
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

        String[] cellVariables = orderedKeys(cellvars, new String[0]);
        BytecodeLocal[] cellVariableLocals = new BytecodeLocal[cellVariables.length];
        for (int i = 0; i < cellVariables.length; i++) {
            BytecodeLocal local = b.createLocal();
            cellLocals.put(cellVariables[i], local);
            cellVariableLocals[i] = local;
        }

        String[] freeVariables = orderedKeys(freevars, new String[0]);
        BytecodeLocal[] freeVariableLocals = new BytecodeLocal[freeVariables.length];
        for (int i = 0; i < freeVariables.length; i++) {
            BytecodeLocal local = b.createLocal();
            freeLocals.put(freeVariables[i], local);
            freeVariableLocals[i] = local;
        }

        // 2. Copy arguments, initialize cells, and copy free variables.
        copyArguments(args, b);

        if (cellVariableLocals.length > 0) {
            List<BytecodeLocal> toClear = new ArrayList<>();

            b.beginStoreRange(cellVariableLocals);
            b.beginMakeVariadic();
            for (int i = 0; i < cellVariableLocals.length; i++) {
                b.beginCreateCell();
                if (scope.getUseOfName(cellVariables[i]).contains(DefUse.DefParam)) {
                    /*
                     * To simplify the argument copying performed above, we copy cell params into
                     * regular locals just like all other arguments. Then, here we move the value
                     * into a cell and clear the regular local.
                     */
                    BytecodeLocal param = getLocal(cellVariables[i]);
                    b.emitLoadLocal(param);
                    toClear.add(param);
                } else {
                    b.emitLoadConstant(null);
                }
                b.endCreateCell();
            }
            b.endMakeVariadic();
            b.endStoreRange();

            for (BytecodeLocal local : toClear) {
                b.emitClearLocal(local);
            }
        }

        if (freeVariableLocals.length > 0) {
            b.beginStoreRange(freeVariableLocals);
            b.emitLoadClosure();
            b.endStoreRange();
        }
    }

    private void copyArguments(ArgumentsTy args, Builder b) {
        if (args == null) {
            return;
        }

        int argIdx = PArguments.USER_ARGUMENTS_OFFSET;
        // TODO default handling
        if (args.posOnlyArgs != null) {
            for (int i = 0; i < args.posOnlyArgs.length; i++) {
                BytecodeLocal local = getLocal(args.posOnlyArgs[i].arg);
                assert local != null;
                b.beginStoreLocal(local);
                b.emitLoadArgument(argIdx++);
                b.endStoreLocal();
            }
        }

        if (args.args != null) {
            for (int i = 0; i < args.args.length; i++) {
                BytecodeLocal local = getLocal(args.args[i].arg);
                assert local != null;
                b.beginStoreLocal(local);
                b.emitLoadArgument(argIdx++);
                b.endStoreLocal();
            }
        }

        if (args.kwOnlyArgs != null) {
            for (int i = 0; i < args.kwOnlyArgs.length; i++) {
                BytecodeLocal local = getLocal(args.kwOnlyArgs[i].arg);
                assert local != null;
                b.beginStoreLocal(local);
                b.emitLoadArgument(argIdx++);
                b.endStoreLocal();
            }
        }

        if (args.varArg != null) {
            BytecodeLocal local = getLocal(args.varArg.arg);
            assert local != null;
            b.beginStoreLocal(local);
            b.emitLoadVariableArguments();
            b.endStoreLocal();
        }

        if (args.kwArg != null) {
            BytecodeLocal local = getLocal(args.kwArg.arg);
            assert local != null;
            b.beginStoreLocal(local);
            b.emitLoadKeywordArguments();
            b.endStoreLocal();
        }
    }

    private static <T> int len(T[] arr) {
        return arr == null ? 0 : arr.length;
    }

    /* ---------------- StatementCompiler -------------------- */

    public class StatementCompiler implements BaseBytecodeDSLVisitor<Void> {
        private final Builder b;

        private BytecodeLabel breakLabel;
        private BytecodeLabel continueLabel;

        public StatementCompiler(Builder b) {
            this.b = b;
        }

        public void beginNode(SSTNode node) {
            RootNodeCompiler.this.beginNode(node, b);
        }

        public void endNode(SSTNode node) {
            RootNodeCompiler.this.endNode(node, b);
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
            beginNode(node);

            b.beginGetAttribute();
            node.value.accept(this);
            b.emitLoadConstant(toTruffleStringUncached(mangle(node.attr)));
            b.endGetAttribute();

            endNode(node);

            return null;
        }

        @Override
        public Void visit(ExprTy.Await node) {
            if (!scope.isFunction()) {
                errorCallback.onError(ErrorType.Syntax, currentLocation, "'await' outside function");
            }
            if (scopeType != CompilationScope.AsyncFunction && scopeType != CompilationScope.Comprehension) {
                errorCallback.onError(ErrorType.Syntax, currentLocation, "'await' outside async function");
            }

            beginNode(node);
            emitAwait(() -> node.value.accept(this));
            endNode(node);
            return null;
        }

        @Override
        public Void visit(ExprTy.BinOp node) {
            beginNode(node);
            switch (node.op) {
                case Add:
                    b.beginAdd();
                    node.left.accept(this);
                    node.right.accept(this);
                    b.endAdd();
                    break;
                case BitAnd:
                    b.beginBitAnd();
                    node.left.accept(this);
                    node.right.accept(this);
                    b.endBitAnd();
                    break;
                case BitOr:
                    b.beginBitOr();
                    node.left.accept(this);
                    node.right.accept(this);
                    b.endBitOr();
                    break;
                case BitXor:
                    b.beginBitXor();
                    node.left.accept(this);
                    node.right.accept(this);
                    b.endBitXor();
                    break;
                case Div:
                    b.beginTrueDiv();
                    node.left.accept(this);
                    node.right.accept(this);
                    b.endTrueDiv();
                    break;
                case FloorDiv:
                    b.beginFloorDiv();
                    node.left.accept(this);
                    node.right.accept(this);
                    b.endFloorDiv();
                    break;
                case LShift:
                    b.beginLShift();
                    node.left.accept(this);
                    node.right.accept(this);
                    b.endLShift();
                    break;
                case MatMult:
                    b.beginMatMul();
                    node.left.accept(this);
                    node.right.accept(this);
                    b.endMatMul();
                    break;
                case Mod:
                    b.beginMod();
                    node.left.accept(this);
                    node.right.accept(this);
                    b.endMod();
                    break;
                case Mult:
                    b.beginMul();
                    node.left.accept(this);
                    node.right.accept(this);
                    b.endMul();
                    break;
                case Pow:
                    b.beginPow();
                    node.left.accept(this);
                    node.right.accept(this);
                    b.endPow();
                    break;
                case RShift:
                    b.beginRShift();
                    node.left.accept(this);
                    node.right.accept(this);
                    b.endRShift();
                    break;
                case Sub:
                    b.beginSub();
                    node.left.accept(this);
                    node.right.accept(this);
                    b.endSub();
                    break;
                default:
                    throw new UnsupportedOperationException("" + node.getClass());
            }

            endNode(node);
            return null;
        }

        @Override
        public Void visit(ExprTy.BoolOp node) {
            beginNode(node);

            if (node.op == BoolOpTy.And) {
                b.beginBoolAnd();
            } else {
                b.beginBoolOr();
            }

            visitSequence(node.values);

            if (node.op == BoolOpTy.And) {
                b.endBoolAnd();
            } else {
                b.endBoolOr();
            }

            endNode(node);
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
                            errorCallback.onError(ErrorType.Syntax, currentLocation, "keyword argument repeated: " + keywords[i].arg);
                        }
                    }
                }
            }
        }

        private static boolean isAttributeLoad(ExprTy node) {
            return node instanceof ExprTy.Attribute && ((ExprTy.Attribute) node).context == ExprContextTy.Load;
        }

        private static final int NUM_ARGS_MAX_FIXED = 4;

        private void emitCall(ExprTy func, ExprTy[] args, KeywordTy[] keywords) {
            validateKeywords(keywords);

            boolean isMethodCall = isAttributeLoad(func) && keywords.length == 0;
            int numArgs = len(args) + (isMethodCall ? 1 : 0);
            boolean useVariadic = anyIsStarred(args) || len(keywords) > 0 || numArgs > NUM_ARGS_MAX_FIXED;

            // @formatter:off
            if (useVariadic) {
                b.beginCallVarargsMethod();
            } else {
                switch (numArgs) {
                    case 0:  b.beginCallNilaryMethod();     break;
                    case 1:  b.beginCallUnaryMethod();      break;
                    case 2:  b.beginCallBinaryMethod();     break;
                    case 3:  b.beginCallTernaryMethod();    break;
                    case 4:  b.beginCallQuaternaryMethod(); break;
                }
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

                    emitUnstar(args, receiver);
                    emitKeywords(keywords, function);
                } else {
                    assert len(keywords) == 0;

                    emitGetMethod(func, receiver);
                    b.emitLoadLocal(receiver);
                    visitSequence(args);
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

                    emitUnstar(args);
                    emitKeywords(keywords, function);
                } else {
                    assert len(keywords) == 0;

                    func.accept(this);
                    visitSequence(args);
                }
            }

            // @formatter:off
            if (useVariadic) {
                b.endCallVarargsMethod();
            } else {
                switch (numArgs) {
                    case 0:  b.endCallNilaryMethod();     break;
                    case 1:  b.endCallUnaryMethod();      break;
                    case 2:  b.endCallBinaryMethod();     break;
                    case 3:  b.endCallTernaryMethod();    break;
                    case 4:  b.endCallQuaternaryMethod(); break;
                }
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

            b.beginGetMethod();
            b.emitLoadLocal(receiver);
            String mangled = mangle(attrAccess.attr);
            b.emitLoadConstant(toTruffleStringUncached(mangled));
            b.endGetMethod();
            b.endBlock();
        }

        @Override
        public Void visit(ExprTy.Call node) {
            beginNode(node);
            emitCall(node.func, node.args, node.keywords);
            endNode(node);
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
            beginNode(node);

            b.beginBoolAnd();

            BytecodeLocal tmp = b.createLocal();

            for (int i = 0; i < node.comparators.length; i++) {
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
            }

            b.endBoolAnd();

            endNode(node);
            return null;
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
                    b.beginLoadComplex();
                    emitPythonConstant(value.getComplex(), b);
                    b.endLoadComplex();
                    break;
                }
                case BIGINTEGER:
                    b.beginLoadBigInt();
                    emitPythonConstant(value.getBigInteger(), b);
                    b.endLoadBigInt();
                    break;
                case RAW:
                    emitPythonConstant(value.getRaw(TruffleString.class), b);
                    break;
                case BYTES:
                    b.beginLoadBytes();
                    emitPythonConstant(value.getBytes(), b);
                    b.endLoadBytes();
                    break;
                case TUPLE:
                    b.beginMakeTuple();
                    for (ConstantValue cv : value.getFrozensetElements()) {
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
            beginNode(node);
            createConstant(node.value);
            endNode(node);
            return null;
        }

        @Override
        public Void visit(ExprTy.Dict node) {
            beginNode(node);

            b.beginMakeDict();
            for (int i = 0; i < node.keys.length; i++) {
                if (node.keys[i] == null) {
                    b.emitLoadConstant(PNone.NO_VALUE);
                } else {
                    node.keys[i].accept(this);
                }
                node.values[i].accept(this);
            }
            b.endMakeDict();

            endNode(node);
            return null;
        }

        @Override
        public Void visit(ExprTy.DictComp node) {
            beginNode(node);

            b.beginCallUnaryMethod();
            emitMakeFunction(node, "<dictcomp>", COMPREHENSION_ARGS, null);
            node.generators[0].iter.accept(this);
            b.endCallUnaryMethod();

            endNode(node);
            return null;
        }

        @Override
        public Void visit(ExprTy.FormattedValue node) {
            beginNode(node);
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
            endNode(node);

            return null;
        }

        @Override
        public Void visit(ExprTy.GeneratorExp node) {
            beginNode(node);

            b.beginCallUnaryMethod();
            emitMakeFunction(node, "<generator>", COMPREHENSION_ARGS, null);
            node.generators[0].iter.accept(this);
            b.endCallUnaryMethod();

            endNode(node);
            return null;
        }

        @Override
        public Void visit(ExprTy.IfExp node) {
            beginNode(node);
            b.beginConditional();

            visitCondition(node.test);

            node.body.accept(this);

            node.orElse.accept(this);

            b.endConditional();
            endNode(node);
            return null;
        }

        @Override
        public Void visit(ExprTy.JoinedStr node) {
            beginNode(node);

            if (node.values.length == 1) {
                node.values[0].accept(this);
            } else {
                b.beginBuildString();
                visitSequence(node.values);
                b.endBuildString();
            }

            endNode(node);
            return null;
        }

        @Override
        public Void visit(ExprTy.Lambda node) {
            beginNode(node);
            emitMakeFunction(node, "<lambda>", node.args, null);
            endNode(node);
            return null;
        }

        @Override
        public Void visit(ExprTy.List node) {
            beginNode(node);

            ConstantCollection constantCollection = Compiler.tryCollectConstantCollection(node.elements);
            if (constantCollection != null) {
                emitConstantList(constantCollection);
            } else {
                b.beginMakeList();
                emitUnstar(node.elements);
                b.endMakeList();
            }

            endNode(node);
            return null;
        }

        private static final String COMPREHENSION_ARGUMENT_NAME = ".0";
        private static final ArgumentsTy COMPREHENSION_ARGS = new ArgumentsTy(new ArgTy[]{new ArgTy(COMPREHENSION_ARGUMENT_NAME, null, null, null)}, null, null, null, null, null, null, null);

        @Override
        public Void visit(ExprTy.ListComp node) {
            beginNode(node);

            b.beginCallUnaryMethod();
            emitMakeFunction(node, "<listcomp>", COMPREHENSION_ARGS, null);
            node.generators[0].iter.accept(this);
            b.endCallUnaryMethod();

            endNode(node);
            return null;
        }

        @Override
        public Void visit(ExprTy.Name node) {
            beginNode(node);
            emitReadLocal(node.id, b);
            endNode(node);
            return null;
        }

        @Override
        public Void visit(ExprTy.NamedExpr node) {
            beginNode(node);

            // save expr result to "tmp"
            BytecodeLocal tmp = b.createLocal();
            b.beginStoreLocal(tmp);
            node.value.accept(this);
            b.endStoreLocal();

            node.target.accept(new StoreVisitor(() -> {
                b.emitLoadLocal(tmp);
            }));

            b.emitLoadLocal(tmp);
            endNode(node);
            return null;
        }

        private void emitConstantList(ConstantCollection constantCollection) {
            switch (constantCollection.elementType) {
                case CollectionBits.ELEMENT_INT:
                    b.beginMakeConstantIntList();
                    emitPythonConstant(constantCollection.collection, b);
                    b.endMakeConstantIntList();
                    break;
                case CollectionBits.ELEMENT_LONG:
                    b.beginMakeConstantLongList();
                    emitPythonConstant(constantCollection.collection, b);
                    b.endMakeConstantLongList();
                    break;
                case CollectionBits.ELEMENT_BOOLEAN:
                    b.beginMakeConstantBooleanList();
                    emitPythonConstant(constantCollection.collection, b);
                    b.endMakeConstantBooleanList();
                    break;
                case CollectionBits.ELEMENT_DOUBLE:
                    b.beginMakeConstantDoubleList();
                    emitPythonConstant(constantCollection.collection, b);
                    b.endMakeConstantDoubleList();
                    break;
                case CollectionBits.ELEMENT_OBJECT:
                    b.beginMakeConstantObjectList();
                    emitPythonConstant(constantCollection.collection, b);
                    b.endMakeConstantObjectList();
                    break;
                default:
                    throw CompilerDirectives.shouldNotReachHere();
            }
        }

        private void emitConstantTuple(ConstantCollection constantCollection) {
            switch (constantCollection.elementType) {
                case CollectionBits.ELEMENT_INT:
                    b.beginMakeConstantIntTuple();
                    emitPythonConstant(constantCollection.collection, b);
                    b.endMakeConstantIntTuple();
                    break;
                case CollectionBits.ELEMENT_LONG:
                    b.beginMakeConstantLongTuple();
                    emitPythonConstant(constantCollection.collection, b);
                    b.endMakeConstantLongTuple();
                    break;
                case CollectionBits.ELEMENT_BOOLEAN:
                    b.beginMakeConstantBooleanTuple();
                    emitPythonConstant(constantCollection.collection, b);
                    b.endMakeConstantBooleanTuple();
                    break;
                case CollectionBits.ELEMENT_DOUBLE:
                    b.beginMakeConstantDoubleTuple();
                    emitPythonConstant(constantCollection.collection, b);
                    b.endMakeConstantDoubleTuple();
                    break;
                case CollectionBits.ELEMENT_OBJECT:
                    b.beginMakeConstantObjectTuple();
                    emitPythonConstant(constantCollection.collection, b);
                    b.endMakeConstantObjectTuple();
                    break;
                default:
                    throw CompilerDirectives.shouldNotReachHere();
            }
        }

        /**
         * Converts a sequence of expressions of which some may be started into just an Object[].
         *
         * @param args the sequence of expressions
         */
        private void emitUnstar(ExprTy[] args) {
            emitUnstar(args, null);
        }

        /**
         * Same as above, but takes an optional receiver local. This is only used for method calls
         * where the receiver needs to be included in the positional arguments.
         *
         * @param args the sequence of expressions to unstar
         * @param receiver an optional local storing the receiver
         */
        private void emitUnstar(ExprTy[] args, BytecodeLocal receiver) {
            if (len(args) == 0 && receiver == null) {
                b.emitLoadConstant(PythonUtils.EMPTY_OBJECT_ARRAY);
            } else if (anyIsStarred(args)) {
                /**
                 * We emit one or more arrays and concatenate them using Unstar. Each array
                 * corresponds to a contiguous sequence of arguments or the result of unpacking a
                 * single starred argument.
                 *
                 * For example, for the argument list a, b, *c, d, e, *f, g we would emit:
                 *
                 * @formatter:off
                 * Unstar(
                 *   MakeVariadic(a, b),
                 *   UnpackStarred(c),
                 *   MakeVariadic(d, e),
                 *   MakeVariadic(f),
                 *   UnpackStarred(g)
                 * )
                 * @formatter:on
                 */
                b.beginUnstar();
                boolean inVariadic = false;

                if (receiver != null) {
                    b.beginMakeVariadic();
                    inVariadic = true;
                    b.emitLoadLocal(receiver);
                }

                for (int i = 0; i < args.length; i++) {
                    if (args[i] instanceof ExprTy.Starred) {
                        if (inVariadic) {
                            b.endMakeVariadic();
                            inVariadic = false;
                        }

                        b.beginUnpackStarred();
                        ((ExprTy.Starred) args[i]).value.accept(this);
                        b.endUnpackStarred();
                    } else {
                        if (!inVariadic) {
                            b.beginMakeVariadic();
                            inVariadic = true;
                        }

                        args[i].accept(this);
                    }
                }

                if (inVariadic) {
                    b.endMakeVariadic();
                }

                b.endUnstar();
            } else {
                b.beginMakeVariadic();
                if (receiver != null) {
                    b.emitLoadLocal(receiver);
                }
                visitSequence(args);
                b.endMakeVariadic();
            }
        }

        @Override
        public Void visit(ExprTy.Set node) {
            beginNode(node);

            b.beginMakeSet();
            emitUnstar(node.elements);
            b.endMakeSet();

            endNode(node);
            return null;
        }

        @Override
        public Void visit(ExprTy.SetComp node) {
            beginNode(node);

            b.beginCallUnaryMethod();
            emitMakeFunction(node, "<setcomp>", COMPREHENSION_ARGS, null);
            node.generators[0].iter.accept(this);
            b.endCallUnaryMethod();

            endNode(node);
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
            beginNode(node);

            b.beginMakeSlice();

            visitNoneable(node.lower);
            visitNoneable(node.upper);
            visitNoneable(node.step);

            b.endMakeSlice();

            endNode(node);
            return null;
        }

        @Override
        public Void visit(ExprTy.Starred node) {
            throw new UnsupportedOperationException("" + node.getClass());
        }

        @Override
        public Void visit(ExprTy.Subscript node) {
            beginNode(node);

            b.beginGetItem();
            node.value.accept(this);
            node.slice.accept(this);
            b.endGetItem();

            endNode(node);
            return null;
        }

        @Override
        public Void visit(ExprTy.Tuple node) {
            beginNode(node);

            ConstantCollection constantCollection = Compiler.tryCollectConstantCollection(node.elements);
            if (constantCollection != null) {
                emitConstantTuple(constantCollection);
            } else {
                b.beginMakeTuple();
                emitUnstar(node.elements);
                b.endMakeTuple();
            }

            endNode(node);
            return null;
        }

        @Override
        public Void visit(ExprTy.UnaryOp node) {
            // Basic constant folding for unary negation
            if (node.op == UnaryOpTy.USub && node.operand instanceof ExprTy.Constant c) {
                if (c.value.kind == ConstantValue.Kind.BIGINTEGER || c.value.kind == ConstantValue.Kind.DOUBLE || c.value.kind == ConstantValue.Kind.LONG ||
                                c.value.kind == ConstantValue.Kind.COMPLEX) {
                    ConstantValue cv = c.value.negate();

                    beginNode(node);
                    visit(new ExprTy.Constant(cv, null, c.getSourceRange()));
                    endNode(node);
                    return null;
                }
            }

            beginNode(node);
            switch (node.op) {
                case UAdd:
                    b.beginPos();
                    node.operand.accept(this);
                    b.endPos();
                    break;
                case Invert:
                    b.beginInvert();
                    node.operand.accept(this);
                    b.endInvert();
                    break;
                case USub:
                    b.beginNeg();
                    node.operand.accept(this);
                    b.endNeg();
                    break;
                case Not:
                    b.beginNot();
                    node.operand.accept(this);
                    b.endNot();
                    break;
                default:
                    throw new UnsupportedOperationException("" + node.getClass());
            }

            endNode(node);
            return null;
        }

        @Override
        public Void visit(ExprTy.Yield node) {
            beginNode(node);

            b.beginResumeYield();
            emitYield((statementCompiler) -> {
                if (node.value != null) {
                    node.value.accept(this);
                } else {
                    statementCompiler.b.emitLoadConstant(PNone.NONE);
                }
            }, this);
            b.endResumeYield();

            endNode(node);
            return null;
        }

        @Override
        public Void visit(ExprTy.YieldFrom node) {
            beginNode(node);
            emitYieldFrom(() -> {
                b.beginGetYieldFromIter();
                node.value.accept(this);
                b.endGetYieldFromIter();
            });
            endNode(node);
            return null;
        }

        public void emitYieldFrom(Runnable generatorOrCoroutineProducer) {
            /**
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
             *   # throw/close generator
             *   if generator returned a value:
             *     goto end
             *   else:
             *     continue (generator yielded a value)
             *
             *   # Step 3: send sentValue into the generator
             *   try:
             *     yieldValue = generator.send(sentValue)
             *   except StopIteration as e:
             *     returnValue = e.value
             *     goto end
             *
             * end:
             * # Step 4: return returnValue
             * returnValue (result)
             * @formatter:on
             */

            b.beginBlock();

            BytecodeLocal generator = b.createLocal();
            BytecodeLocal returnValue = b.createLocal();
            BytecodeLocal sentValue = b.createLocal();
            BytecodeLocal yieldValue = b.createLocal();
            BytecodeLabel end = b.createLabel();

            b.beginStoreLocal(generator);
            generatorOrCoroutineProducer.run();
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
            BytecodeLocal thrownException = b.createLocal();
            b.beginTryCatch(thrownException);

            // try clause: yield
            b.beginStoreLocal(sentValue);
            b.beginResumeYield();
            emitYield((statementCompiler) -> statementCompiler.b.emitLoadLocal(yieldValue), this);
            b.endResumeYield();
            b.endStoreLocal();

            // catch clause: handle throw/close exceptions.
            b.beginIfThenElse();
            b.beginYieldFromThrow(yieldValue, returnValue);
            b.emitLoadLocal(generator);
            b.emitLoadLocal(thrownException);
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

            // Step 4: return returnValue
            b.emitLabel(end);
            b.emitLoadLocal(returnValue);

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
            beginNode(node);
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
                    b.beginSetDictItem();
                    emitNameOperation("__annotations__", NameOperation.Read, b);

                    String mangled = mangle(name);
                    emitPythonConstant(toTruffleStringUncached(mangled), b);

                    if (futureFeatures.contains(FutureFeature.ANNOTATIONS)) {
                        emitPythonConstant(Unparser.unparse(node.annotation), b);
                    } else {
                        node.annotation.accept(this);
                    }

                    b.endSetDictItem();
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
                errorCallback.onError(ErrorType.Syntax, node.getSourceRange(), "invalid node type for annotated assignment");
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
            endNode(node);
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
                beginNode(node);
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
                endNode(node);
            }
            return null;
        }

        // --------------------- assign ------------------------

        public class StoreVisitor implements BaseBytecodeDSLVisitor<Void> {
            private final Builder b = StatementCompiler.this.b;
            private final Runnable generateValue;

            StoreVisitor(Runnable generateValue) {
                this.generateValue = generateValue;
            }

            @Override
            public Void visit(ExprTy.Name node) {
                beginNode(node);
                beginStoreLocal(node.id, b);
                generateValue.run();
                endStoreLocal(node.id, b);
                endNode(node);
                return null;
            }

            @Override
            public Void visit(ExprTy.Attribute node) {
                beginNode(node);
                checkForbiddenName(node.attr, NameOperation.BeginWrite);
                b.beginSetAttribute();
                generateValue.run();
                node.value.accept(StatementCompiler.this);
                b.emitLoadConstant(toTruffleStringUncached(mangle(node.attr)));
                b.endSetAttribute();
                endNode(node);
                return null;
            }

            @Override
            public Void visit(ExprTy.Subscript node) {
                beginNode(node);
                b.beginSetItem();
                generateValue.run();
                node.value.accept(StatementCompiler.this);
                node.slice.accept(StatementCompiler.this);
                b.endSetItem();
                endNode(node);
                return null;
            }

            private void visitIterableAssign(ExprTy[] nodes) {
                b.beginBlock();

                // TODO if it is a variable directly, use that variable instead of going to a temp
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
                    b.beginUnpackStarredToLocals(targets);
                }

                generateValue.run();

                if (indexOfStarred == -1) {
                    b.endUnpackToLocals();
                } else {
                    b.emitLoadConstant(indexOfStarred);
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
                beginNode(node);
                visitIterableAssign(node.elements);
                endNode(node);
                return null;
            }

            @Override
            public Void visit(ExprTy.List node) {
                beginNode(node);
                visitIterableAssign(node.elements);
                endNode(node);
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
                    case Add:
                        b.beginIAdd();
                        break;
                    case Sub:
                        b.beginISub();
                        break;
                    case Mult:
                        b.beginIMult();
                        break;
                    case FloorDiv:
                        b.beginIFloorDiv();
                        break;
                    case BitAnd:
                        b.beginIAnd();
                        break;
                    case BitOr:
                        b.beginIOr();
                        break;
                    case BitXor:
                        b.beginIXor();
                        break;
                    case RShift:
                        b.beginIRShift();
                        break;
                    case LShift:
                        b.beginILShift();
                        break;
                    case Div:
                        b.beginITrueDiv();
                        break;
                    case Mod:
                        b.beginIMod();
                        break;
                    case MatMult:
                        b.beginIMatMul();
                        break;
                    case Pow:
                        b.beginIPow();
                        break;
                    default:
                        throw new UnsupportedOperationException("aug ass: " + op);
                }
            }

            private void endAugAssign() {
                switch (op) {
                    case Add:
                        b.endIAdd();
                        break;
                    case Sub:
                        b.endISub();
                        break;
                    case Mult:
                        b.endIMult();
                        break;
                    case FloorDiv:
                        b.endIFloorDiv();
                        break;
                    case BitAnd:
                        b.endIAnd();
                        break;
                    case BitOr:
                        b.endIOr();
                        break;
                    case BitXor:
                        b.endIXor();
                        break;
                    case RShift:
                        b.endIRShift();
                        break;
                    case LShift:
                        b.endILShift();
                        break;
                    case Div:
                        b.endITrueDiv();
                        break;
                    case Mod:
                        b.endIMod();
                        break;
                    case MatMult:
                        b.endIMatMul();
                        break;
                    case Pow:
                        b.endIPow();
                        break;
                    default:
                        throw new UnsupportedOperationException("aug ass: " + op);
                }
            }

            @Override
            public Void visit(ExprTy.Name node) {
                beginNode(node);

                beginStoreLocal(node.id, b);
                beginAugAssign();
                emitReadLocal(node.id, b);
                value.accept(StatementCompiler.this);
                endAugAssign();
                endStoreLocal(node.id, b);

                endNode(node);
                return null;
            }

            @Override
            public Void visit(ExprTy.Attribute node) {
                beginNode(node);
                checkForbiddenName(node.attr, NameOperation.BeginWrite);
                b.beginBlock();
                // {
                BytecodeLocal target = b.createLocal();

                b.beginStoreLocal(target);
                node.value.accept(StatementCompiler.this);
                b.endStoreLocal();

                b.beginSetAttribute();
                beginAugAssign();

                b.beginGetAttribute();
                b.emitLoadLocal(target);
                b.emitLoadConstant(toTruffleStringUncached(mangle(node.attr)));
                b.endGetAttribute();

                value.accept(StatementCompiler.this);

                endAugAssign();

                b.emitLoadLocal(target);

                b.emitLoadConstant(toTruffleStringUncached(mangle(node.attr)));
                b.endSetAttribute();
                // }
                b.endBlock();
                endNode(node);
                return null;
            }

            @Override
            public Void visit(ExprTy.Subscript node) {
                beginNode(node);
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

                b.beginGetItem();
                b.emitLoadLocal(target);
                b.emitLoadLocal(slice);
                b.endGetItem();

                value.accept(StatementCompiler.this);

                endAugAssign();

                b.emitLoadLocal(target);
                b.emitLoadLocal(slice);
                b.endSetItem();
                // }
                b.endBlock();
                endNode(node);
                return null;
            }
        }

        @Override
        public Void visit(StmtTy.Assign node) {
            beginNode(node);
            emitAssignment(node.targets, node.value);
            endNode(node);
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
            emitNotImplemented("async for", b);
            return null;
        }

        @Override
        public Void visit(StmtTy.AsyncWith node) {
            if (!scope.isFunction()) {
                errorCallback.onError(ErrorType.Syntax, currentLocation, "'async with' outside function");
            }
            if (scopeType != CompilationScope.AsyncFunction && scopeType != CompilationScope.Comprehension) {
                errorCallback.onError(ErrorType.Syntax, currentLocation, "'async with' outside async function");
            }
            beginNode(node);
            visitWithRecurse(node.items, 0, node.body, true);
            endNode(node);
            return null;
        }

        @Override
        public Void visit(StmtTy.AugAssign node) {
            beginNode(node);
            node.target.accept(new AugStoreVisitor(node.op, node.value));
            endNode(node);
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
                b.beginKwargsMerge();
                emitKeywordsRecursive(groups, i - 1, function);
                emitKeywordGroup(groups[i], false, function);
                b.emitLoadLocal(function);
                b.endKwargsMerge();
            }
        }

        private void emitKeywordGroup(KeywordGroup group, boolean copy, BytecodeLocal function) {
            if (group instanceof NamedKeywords namedKeywords) {
                b.beginMakeDict();
                for (int i = 0; i < namedKeywords.names.size(); i++) {
                    emitPythonConstant(namedKeywords.names.get(i), b);
                    namedKeywords.values.get(i).accept(this);
                }
                b.endMakeDict();
            } else {
                SplatKeywords splatKeywords = (SplatKeywords) group;

                if (copy) {
                    b.beginKwargsMerge();
                    b.emitMakeEmptyDict();
                    splatKeywords.expr.accept(this);
                    b.emitLoadLocal(function);
                    b.endKwargsMerge();
                } else {
                    splatKeywords.expr.accept(this);
                }
            }
        }

        @Override
        public Void visit(StmtTy.ClassDef node) {
            beginNode(node);
            BytecodeLocal buildClassFunction = b.createLocal();

            // compute __build_class__ (we need it in multiple places, so store it)
            b.beginStoreLocal(buildClassFunction);
            b.emitBuildClass();
            b.endStoreLocal();

            // ClassName = __build_class__(<code>, "ClassName", bases, keywords)
            beginStoreLocal(node.name, b);

            int numDeco = len(node.decoratorList);
            for (int i = 0; i < numDeco; i++) {
                b.beginCallUnaryMethod();
                node.decoratorList[i].accept(this);
            }

            b.beginCallVarargsMethod();

            b.emitLoadLocal(buildClassFunction);

            // positional args
            b.beginMakeVariadic();
            emitMakeFunction(node, node.name, null, null);
            emitPythonConstant(toTruffleStringUncached(node.name), b);
            visitSequence(node.bases);
            b.endMakeVariadic();

            // keyword args
            emitKeywords(node.keywords, buildClassFunction);

            b.endCallVarargsMethod();

            for (int i = 0; i < numDeco; i++) {
                b.endCallUnaryMethod();
            }

            endStoreLocal(node.name, b);

            endNode(node);

            return null;
        }

        private class DeleteVisitor implements BaseBytecodeDSLVisitor<Void> {

            @Override
            public Void visit(ExprTy.Subscript node) {
                beginNode(node);

                b.beginDeleteItem();
                node.value.accept(StatementCompiler.this);
                node.slice.accept(StatementCompiler.this);
                b.endDeleteItem();

                endNode(node);
                return null;
            }

            @Override
            public Void visit(ExprTy.Attribute node) {
                beginNode(node);
                checkForbiddenName(node.attr, NameOperation.BeginWrite);
                b.beginDeleteAttribute();
                node.value.accept(StatementCompiler.this);
                b.emitLoadConstant(toTruffleStringUncached(node.attr));
                b.endDeleteAttribute();

                endNode(node);
                return null;
            }

            @Override
            public Void visit(ExprTy.Name node) {
                beginNode(node);
                emitNameOperation(node.id, NameOperation.Delete, b);
                endNode(node);
                return null;
            }

            @Override
            public Void visit(ExprTy.Tuple node) {
                beginNode(node);
                visitSequence(node.elements);
                endNode(node);
                return null;
            }

            @Override
            public Void visit(ExprTy.List node) {
                beginNode(node);
                visitSequence(node.elements);
                endNode(node);
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
            beginNode(node);
            if (isInteractive) {
                b.beginPrintExpr();
                node.value.accept(this);
                b.endPrintExpr();
            } else {
                node.value.accept(this);
            }
            endNode(node);

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
            beginNode(node);

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
            b.beginForIterate(value);
            b.emitLoadLocal(iter);
            b.endForIterate();

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

            endNode(node);
            return null;
        }

        @Override
        public Void visit(StmtTy.FunctionDef node) {
            beginNode(node);

            beginStoreLocal(node.name, b);

            int numDeco = len(node.decoratorList);
            for (int i = 0; i < numDeco; i++) {
                b.beginCallUnaryMethod();
                node.decoratorList[i].accept(this);
            }

            List<ParamAnnotation> annotations = collectParamAnnotations(node.args, node.returns);
            emitMakeFunction(node, node.name, node.args, annotations);

            for (int i = 0; i < numDeco; i++) {
                b.endCallUnaryMethod();
            }

            endStoreLocal(node.name, b);

            endNode(node);
            return null;
        }

        @Override
        public Void visit(StmtTy.AsyncFunctionDef node) {
            beginNode(node);

            beginStoreLocal(node.name, b);

            int numDeco = len(node.decoratorList);
            for (int i = 0; i < numDeco; i++) {
                b.beginCallUnaryMethod();
                node.decoratorList[i].accept(this);
            }

            List<ParamAnnotation> annotations = collectParamAnnotations(node.args, node.returns);
            emitMakeFunction(node, node.name, node.args, annotations);

            for (int i = 0; i < numDeco; i++) {
                b.endCallUnaryMethod();
            }

            endStoreLocal(node.name, b);

            endNode(node);
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

        private void emitMakeFunction(SSTNode node, String name, ArgumentsTy args, List<ParamAnnotation> annotations) {
            BytecodeDSLCompilerResult compilerResult = compileNode(node);
            BytecodeDSLCodeUnit codeUnit = compilerResult.codeUnit();

            b.beginMakeFunction();
            b.emitLoadConstant(toTruffleStringUncached(name));

            Scope targetScope = ctx.scopeEnvironment.lookupScope(node);
            emitPythonConstant(toTruffleStringUncached(ctx.getQualifiedName(targetScope)), b);
            emitPythonConstant(codeUnit, b);

            if (args == null || len(args.defaults) == 0) {
                b.emitLoadConstant(PythonUtils.EMPTY_OBJECT_ARRAY);
            } else {
                b.beginMakeVariadic();
                for (int i = 0; i < args.defaults.length; i++) {
                    args.defaults[i].accept(this);
                }
                b.endMakeVariadic();
            }

            if (args == null || len(args.kwDefaults) == 0) {
                b.emitLoadConstant(PKeyword.EMPTY_KEYWORDS);
            } else {
                ArgTy[] kwOnlyArgs = args.kwOnlyArgs;

                b.beginMakeKeywords();
                for (int i = 0; i < args.kwDefaults.length; i++) {
                    // Only emit keywords with default values.
                    if (args.kwDefaults[i] != null) {
                        b.emitLoadConstant(toTruffleStringUncached(mangle(kwOnlyArgs[i].arg)));
                        args.kwDefaults[i].accept(this);
                    }
                }
                b.endMakeKeywords();
            }

            if (codeUnit.freevars.length == 0) {
                b.emitLoadConstant(null);
            } else {
                b.beginMakeCellArray();
                for (int i = 0; i < codeUnit.freevars.length; i++) {
                    String fv = codeUnit.freevars[i].toJavaStringUncached();
                    BytecodeLocal local;
                    if (scopeType == CompilationScope.Class && "__class__".equals(fv) || scope.getUseOfName(fv).contains(Scope.DefUse.Cell)) {
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
                b.beginMakeDict();
                for (ParamAnnotation annotation : annotations) {
                    emitParamAnnotation(annotation);
                }
                b.endMakeDict();
            } else {
                b.emitLoadConstant(null);
            }

            // __doc__
            Object[] functionConstants = codeUnit.constants;
            if (functionConstants.length > 0 && functionConstants[0] != null && functionConstants[0] instanceof TruffleString) {
                b.emitLoadConstant(functionConstants[0]);
            } else {
                b.emitLoadConstant(null);
            }

            b.endMakeFunction();
        }

        private BytecodeDSLCompilerResult compileNode(SSTNode node) {
            return (new RootNodeCompiler(ctx, node, futureFeatures)).compile();
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
            beginNode(node);

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

            endNode(node);
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
            beginNode(node);

            for (AliasTy name : node.names) {
                addObject(constants, PythonUtils.EMPTY_TRUFFLESTRING_ARRAY);
                if (name.asName == null) {
                    // import a.b.c
                    // --> a = (Import "a.b.c" [] 0)
                    // import a
                    // --> a = (Import "a" [] 0)
                    String resName = name.name.contains(".")
                                    ? name.name.substring(0, name.name.indexOf('.'))
                                    : name.name;

                    beginStoreLocal(resName, b);

                    b.beginImport();
                    b.emitLoadConstant(toTruffleStringUncached(name.name));
                    b.emitLoadConstant(PythonUtils.EMPTY_TRUFFLESTRING_ARRAY);
                    b.emitLoadConstant(0);
                    b.endImport();

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
                            b.beginImportFrom();
                        } else {
                            b.beginImport();
                            b.emitLoadConstant(toTruffleStringUncached(name.name));
                            b.emitLoadConstant(PythonUtils.EMPTY_TRUFFLESTRING_ARRAY);
                            b.emitLoadConstant(0);
                            b.endImport();
                        }
                    }

                    for (int i = 1; i < parts.length; i++) {
                        b.emitLoadConstant(toTruffleStringUncached(parts[i]));
                        b.endImportFrom();
                    }

                    endStoreLocal(name.asName, b);
                }
            }

            endNode(node);
            return null;
        }

        @Override
        public Void visit(StmtTy.ImportFrom node) {
            beginNode(node);
            if (node.getSourceRange().startLine > ctx.futureLineNumber && "__future__".equals(node.module)) {
                errorCallback.onError(ErrorType.Syntax, node.getSourceRange(), "from __future__ imports must occur at the beginning of the file");
            }

            TruffleString tsModuleName = toTruffleStringUncached(node.module == null ? "" : node.module);

            if (node.names[0].name.equals("*")) {
                b.beginImportStar();
                b.emitLoadConstant(tsModuleName);
                b.emitLoadConstant(node.level);
                b.endImportStar();
            } else {
                b.beginBlock();

                BytecodeLocal module = b.createLocal();

                TruffleString[] fromList = new TruffleString[node.names.length];
                for (int i = 0; i < fromList.length; i++) {
                    fromList[i] = toTruffleStringUncached(node.names[i].name);
                }

                b.beginStoreLocal(module);
                b.beginImport();
                b.emitLoadConstant(tsModuleName);
                b.emitLoadConstant(fromList);
                b.emitLoadConstant(node.level);
                b.endImport();
                b.endStoreLocal();

                TruffleString[] importedNames = new TruffleString[node.names.length];
                for (int i = 0; i < node.names.length; i++) {
                    AliasTy alias = node.names[i];
                    String asName = alias.asName == null ? alias.name : alias.asName;
                    beginStoreLocal(asName, b);

                    b.beginImportFrom();
                    b.emitLoadLocal(module);
                    TruffleString name = toTruffleStringUncached(alias.name);
                    importedNames[i] = name;
                    b.emitLoadConstant(name);
                    b.endImportFrom();

                    endStoreLocal(asName, b);
                }
                addObject(constants, importedNames);

                b.endBlock();
            }

            endNode(node);
            return null;
        }

        @Override
        public Void visit(StmtTy.Match node) {
            beginNode(node);
            b.beginBlock();
            // Compute and store the subject in a local.
            BytecodeLocal subject = b.createLocal();
            b.beginStoreLocal(subject);
            node.subject.accept(this);
            b.endStoreLocal();

            visitMatchCaseRecursively(node.cases, 0, new PatternContext(subject));

            b.endBlock();
            endNode(node);
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
                errorCallback.onError(ErrorType.Syntax, currentLocation, "multiple assignments to name '%s' in pattern", name);
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
            beginNode(c);

            if (index != cases.length - 1) {
                b.beginIfThenElse();

                // A case that isn't last can be irrefutable only if it is guarded.
                pc.allowIrrefutable = c.guard != null;

                emitPatternCondition(c, pc);
                visitStatements(c.body);
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

            endNode(c);
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
                guard.accept(this);
            }
            b.endPrimitiveBoolAnd();
        }

        /**
         * Generates code to test a {@code pattern} against the value stored in {@code subject}.
         *
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
            beginNode(pattern);
            if (pattern instanceof PatternTy.MatchAs matchAs) {
                doVisitPattern(matchAs, pc);
            } else if (pattern instanceof PatternTy.MatchClass matchClass) {
                doVisitPattern(matchClass);
            } else if (pattern instanceof PatternTy.MatchMapping matchMapping) {
                doVisitPattern(matchMapping);
            } else if (pattern instanceof PatternTy.MatchOr matchOr) {
                doVisitPattern(matchOr);
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
            endNode(pattern);
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
                        errorCallback.onError(ErrorType.Syntax, currentLocation, "name capture '%s' makes remaining patterns unreachable", node.name);
                    }
                    errorCallback.onError(ErrorType.Syntax, currentLocation, "wildcard makes remaining patterns unreachable");
                }
                b.emitLoadConstant(true);
            } else {
                assert node.name != null : "name should only be null for the empty wildcard pattern '_'";
                visitPattern(node.pattern, pc);
            }

            b.endBlock();
        }

        private void emitPatternNotImplemented(String kind) {
            b.beginBlock();
            emitNotImplemented(kind + " pattern matching", b);
            // we need a value producing operation
            b.emitLoadConstant(false);
            b.endBlock();
        }

        private void doVisitPattern(PatternTy.MatchClass node) {
            emitPatternNotImplemented("class");
        }

        private void doVisitPattern(PatternTy.MatchMapping node) {
            emitPatternNotImplemented("mapping");
        }

        private void doVisitPattern(PatternTy.MatchOr node) {
            emitPatternNotImplemented("OR");
        }

        private static int lengthOrZero(Object[] p) {
            return p == null ? 0 : p.length;
        }

        private void patternHelperSequenceUnpack(PatternTy[] patterns, PatternContext pc) {
            int n = lengthOrZero(patterns);

            b.beginBlock();
            // We need to remember the unpacked array, since subject will be overwritten in
            // recursive calls.
            BytecodeLocal unpacked = b.createLocal();
            b.beginStoreLocal(unpacked);
            patternUnpackHelper(patterns, pc);
            b.endStoreLocal();

            for (int i = 0; i < n; i++) {
                b.beginStoreLocal(pc.subject);
                b.beginArrayIndex();
                b.emitLoadLocal(unpacked);
                b.emitLoadConstant(i);
                b.endArrayIndex();
                b.endStoreLocal();

                visitSubpattern(patterns[i], pc);
            }
            b.endBlock();
        }

        private void patternUnpackHelper(PatternTy[] patterns, PatternContext pc) {
            int n = lengthOrZero(patterns);

            boolean seenStar = false;
            for (int i = 0; i < n; i++) {
                PatternTy pattern = patterns[i];
                if (pattern instanceof PatternTy.MatchStar) {
                    if (seenStar) {
                        errorCallback.onError(ErrorType.Syntax, currentLocation, "multiple starred expressions in sequence pattern");
                    }
                    seenStar = true;
                    int countAfter = n - i - 1;
                    if (countAfter != (byte) countAfter) {
                        errorCallback.onError(ErrorType.Syntax, currentLocation, "too many expressions in star-unpacking sequence pattern");
                    }
                    // If there's a star pattern, emit UnpackEx.
                    b.beginUnpackEx();
                    b.emitLoadLocal(pc.subject);
                    b.emitLoadConstant(i);
                    b.emitLoadConstant(countAfter);
                    b.endUnpackEx();
                    // Continue in the loop to ensure there are no additional starred patterns.
                }
            }
            // If there were no star patterns, emit UnpackSequence.
            if (!seenStar) {
                b.beginUnpackSequence();
                b.emitLoadLocal(pc.subject);
                b.emitLoadConstant(n);
                b.endUnpackSequence();
            }
        }

        /**
         * Like patternHelperSequenceUnpack, but uses subscripting, which is (likely) more efficient
         * for patterns with a starred wildcard like [first, *_], [first, *_, last], [*_, last],
         * etc.
         */
        private void patternHelperSequenceSubscr(PatternTy[] patterns, int star, PatternContext pc) {
            int n = lengthOrZero(patterns);

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
                    b.beginSub();

                    b.beginGetLen();
                    b.emitLoadLocal(sequence);
                    b.endGetLen();

                    b.emitLoadConstant(n - i);

                    b.endSub();
                }
                b.endBinarySubscript();
                b.endStoreLocal();

                visitSubpattern(pattern, pc);
            }
            b.endBlock();
        }

        private void doVisitPattern(PatternTy.MatchSequence node, PatternContext pc) {
            int size = lengthOrZero(node.patterns);
            int star = -1;
            boolean onlyWildcard = true;
            boolean starWildcard = false;

            // Find a starred name, if it exists. There may be at most one:
            for (int i = 0; i < size; i++) {
                PatternTy pattern = node.patterns[i];
                if (pattern instanceof PatternTy.MatchStar) {
                    if (star >= 0) {
                        errorCallback.onError(ErrorType.Syntax, node.getSourceRange(), "multiple starred names in sequence pattern");
                    }
                    starWildcard = wildcardStarCheck(pattern);
                    onlyWildcard &= starWildcard;
                    star = i;
                    continue;
                }
                onlyWildcard &= wildcardCheck(pattern);
            }

            b.beginPrimitiveBoolAnd();

            b.beginIsSequence();
            b.emitLoadLocal(pc.subject);
            b.endIsSequence();

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
                errorCallback.onError(ErrorType.Syntax, currentLocation, "patterns may only match literals and attribute lookups");
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
            // TODO: why does the version in Compiler.java save and restore locations?
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
            // TODO: why does the version in Compiler.java save and restore locations?
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
            beginNode(node);
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
            endNode(node);
            return null;
        }

        @Override
        public Void visit(StmtTy.Return node) {
            if (!scope.isFunction()) {
                errorCallback.onError(ErrorType.Syntax, currentLocation, "'return' outside function");
            }

            beginNode(node);

            b.beginReturn();
            if (node.value != null) {
                node.value.accept(this);
            } else {
                b.emitLoadConstant(PNone.NONE);
            }
            b.endReturn();

            endNode(node);
            return null;
        }

        @Override
        public Void visit(StmtTy.Try node) {
            beginNode(node);
            if (node.finalBody != null && node.finalBody.length != 0) {
                /**
                 * In Python, an uncaught exception becomes the "current" exception inside a finally
                 * block. The finally body can itself throw, in which case it replaces the exception
                 * being thrown. For such a scenario, we have to be careful to restore the "current"
                 * exception using a try-finally.
                 *
                 * In pseudocode, this looks like:
                 * @formatter:off
                 * try {
                 *   try_catch_else
                 * } finally uncaught_ex {
                 *   if (uncaught_ex != null) {
                 *     save current exception
                 *     set the current exception to uncaught_ex
                 *   }
                 *   try {
                 *     finally_body
                 *   } finally {
                 *     if (uncaught_ex != null) {
                 *       restore current exception
                 *     }
                 *   }
                 * }
                 */
                BytecodeLocal uncaughtException = b.createLocal();
                b.beginFinallyTry(uncaughtException);
                    b.beginBlock(); // finally
                        BytecodeLocal savedException = b.createLocal();

                        b.beginIfThen();
                            b.beginNonNull();
                                b.emitLoadLocal(uncaughtException);
                            b.endNonNull();

                            b.beginBlock();
                                emitSaveCurrentException(savedException);
                                emitSetCurrentException(uncaughtException);
                                // Mark this location for the stack trace.
                                b.beginSetCatchingFrameReference();
                                    b.emitLoadLocal(uncaughtException);
                                b.endSetCatchingFrameReference();
                            b.endBlock();
                        b.endIfThen();

                        b.beginFinallyTry(b.createLocal());
                            b.beginBlock(); // implicit finally block
                                b.beginIfThen();
                                    b.beginNonNull();
                                        b.emitLoadLocal(uncaughtException);
                                    b.endNonNull();

                                    emitSetCurrentException(savedException);
                                b.endIfThen();
                            b.endBlock(); // implicit finally block

                            b.beginBlock(); // implicit try block
                                visitSequence(node.finalBody);
                            b.endBlock(); // implicit try block
                        b.endFinallyTry();
                    b.endBlock(); // finally

                    emitTryExceptElse(node); // try
                b.endFinallyTry();
                // @formatter:on
            } else {
                emitTryExceptElse(node);
            }

            endNode(node);
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
                 *   try {
                 *     if (handler_1_matches(ex)) {
                 *       assign ex to handler_1_name
                 *       try {
                 *         handler_1_body
                 *       } finally {
                 *         unbind handler_1_name
                 *       }
                 *       goto afterElse
                 *     }
                 *     if (handler_2_matches(ex)) {
                 *       assign ex to handler_2_name
                 *       try {
                 *         handler_2_body
                 *       } finally {
                 *         unbind handler_2_name
                 *       }
                 *       goto afterElse
                 *     }
                 *     ... // more cases
                 *     rethrow ex // only if a handler doesn't match
                 *   } finally {
                 *     restore current exception
                 *   }
                 * }
                 * else_body
                 * afterElse:
                 */
                b.beginBlock(); // outermost block

                BytecodeLocal exception = b.createLocal();
                BytecodeLocal savedException = b.createLocal();
                BytecodeLabel afterElse = b.createLabel();

                b.beginTryCatch(exception);

                    b.beginBlock(); // try
                        visitSequence(node.body);
                    b.endBlock(); // try

                    b.beginBlock(); // catch
                        emitSaveCurrentException(savedException);
                        emitSetCurrentException(exception);
                        // Mark this location for the stack trace.
                        b.beginSetCatchingFrameReference();
                            b.emitLoadLocal(exception);
                        b.endSetCatchingFrameReference();

                        b.beginFinallyTry(b.createLocal());
                            emitSetCurrentException(savedException); // finally

                            b.beginBlock(); // try
                                boolean hasBareExcept = false;
                                for (ExceptHandlerTy h : node.handlers) {

                                    if (hasBareExcept) {
                                        // TODO: improve source location
                                        errorCallback.onError(ErrorType.Syntax, currentLocation, "default 'except:' must be last");
                                    }

                                    ExceptHandlerTy.ExceptHandler handler = (ExceptHandlerTy.ExceptHandler) h;
                                    if (handler.type != null) {
                                        b.beginIfThen();
                                            b.beginExceptMatch();
                                                b.emitLoadLocal(exception);
                                                handler.type.accept(this);
                                            b.endExceptMatch();
                                    } else {
                                        hasBareExcept = true;
                                    }

                                    b.beginBlock(); // handler body

                                    if (handler.name != null) {
                                        // Assign exception to handler name.
                                        beginStoreLocal(handler.name, b);
                                            b.beginUnwrapException();
                                                b.emitLoadLocal(exception);
                                            b.endUnwrapException();
                                        endStoreLocal(handler.name, b);

                                        b.beginFinallyTry(b.createLocal());
                                            b.beginBlock(); // finally
                                                // Store None to the variable just in case the handler deleted it.
                                                beginStoreLocal(handler.name, b);
                                                    b.emitLoadConstant(PNone.NONE);
                                                endStoreLocal(handler.name, b);
                                                emitDelLocal(handler.name, b);
                                            b.endBlock(); // finally

                                            b.beginBlock(); // try
                                                visitSequence(handler.body);
                                            b.endBlock(); // try
                                        b.endFinallyTry();
                                    } else {
                                        visitSequence(handler.body);
                                    }

                                    b.emitBranch(afterElse);

                                    b.endBlock(); // handler body

                                    if (handler.type != null) {
                                        b.endIfThen();
                                    }
                                }
                            b.endBlock(); // try

                        b.endFinallyTry();

                        // Each handler branches to afterElse. If we reach this point and there was not a
                        // bare exception, none of the handlers matched, and we should rethrow.
                        // Optimization: If there's a bare except clause, control will never fall through
                        // and we can omit the rethrow.
                        if (!hasBareExcept) {
                            b.beginThrow();
                                b.emitLoadLocal(exception);
                            b.endThrow();
                        }

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

        private void emitSaveCurrentException(BytecodeLocal savedException) {
            b.beginStoreLocal(savedException);
            b.emitGetCurrentException();
            b.endStoreLocal();
        }

        private void emitSetCurrentException(BytecodeLocal newException) {
            b.beginSetCurrentException();
            b.emitLoadLocal(newException);
            b.endSetCurrentException();
        }

        @Override
        public Void visit(ExceptHandlerTy.ExceptHandler node) {
            throw new UnsupportedOperationException("" + node.getClass());
        }

        @Override
        public Void visit(StmtTy.While node) {
            beginNode(node);

            BytecodeLabel oldBreakLabel = breakLabel;
            BytecodeLabel oldContinueLabel = continueLabel;

            BytecodeLabel currentBreakLabel = b.createLabel();
            breakLabel = currentBreakLabel;

            b.beginWhile();
            // {
            visitCondition(node.test);

            b.beginBlock();
            continueLabel = b.createLabel();
            visitStatements(node.body);
            b.emitLabel(continueLabel);
            b.endBlock();
            // }
            b.endWhile();

            breakLabel = oldBreakLabel;
            continueLabel = oldContinueLabel;
            visitStatements(node.orElse);
            b.emitLabel(currentBreakLabel);

            endNode(node);
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
             *   exceptionHit = False
             *   try:
             *     try:
             *       x = value
             *       bar
             *     except ex:
             *       exceptionHit = True
             *       if not __exit__(...):
             *         raise
             *   finally_noexcept:
             *     if not exceptionHit:
             *       call __exit__(None, None, None)

             * @formatter:on
             *
             * When there are multiple context managers, they are recursively generated (where "bar"
             * is). Once we have entered all of the context managers, we emit the body.
             */
            WithItemTy item = items[index];
            beginNode(item);

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
                emitAwait(() -> b.emitLoadLocal(value));
            } else {
                // call __enter__
                b.beginContextManagerEnter(exit, value);
                b.emitLoadLocal(contextManager);
                b.endContextManagerEnter();
            }

            BytecodeLocal exceptionHit = b.createLocal();
            b.beginStoreLocal(exceptionHit);
            b.emitLoadConstant(false);
            b.endStoreLocal();

            b.beginFinallyTryNoExcept();
            b.beginBlock(); // finally
            b.beginIfThen();

            b.beginNot();
            b.emitLoadLocal(exceptionHit);
            b.endNot();

            // regular exit
            if (async) {
                // call and await __aexit__
                emitAwait(() -> {
                    b.beginAsyncContextManagerCallExit();
                    b.emitLoadConstant(PNone.NONE);
                    b.emitLoadLocal(exit);
                    b.emitLoadLocal(contextManager);
                    b.endAsyncContextManagerCallExit();
                });
            } else {
                // call __exit__
                b.beginContextManagerExit();
                b.emitLoadConstant(PNone.NONE);
                b.emitLoadLocal(exit);
                b.emitLoadLocal(contextManager);
                b.endContextManagerExit();
            }

            b.endIfThen();
            b.endBlock(); // finally

            b.beginBlock(); // try
            BytecodeLocal ex = b.createLocal();
            b.beginTryCatch(ex);

            b.beginBlock(); // try-catch body
            if (item.optionalVars != null) {
                item.optionalVars.accept(new StoreVisitor(() -> b.emitLoadLocal(value)));
            }

            if (index < items.length - 1) {
                visitWithRecurse(items, index + 1, body, async);
            } else {
                visitSequence(body);
            }
            b.endBlock(); // try-catch body

            b.beginBlock(); // try-catch handler
            b.beginStoreLocal(exceptionHit);
            b.emitLoadConstant(true);
            b.endStoreLocal();

            // Mark this location for the stack trace.
            b.beginSetCatchingFrameReference();
            b.emitLoadLocal(ex);
            b.endSetCatchingFrameReference();

            // exceptional exit
            if (async) {
                // call, await, and handle result of __aexit__
                b.beginAsyncContextManagerExit();
                b.emitLoadLocal(ex);
                emitAwait(() -> {
                    b.beginAsyncContextManagerCallExit();
                    b.emitLoadLocal(ex);
                    b.emitLoadLocal(exit);
                    b.emitLoadLocal(contextManager);
                    b.endAsyncContextManagerCallExit();
                });
                b.endAsyncContextManagerExit();
            } else {
                // call __exit__
                b.beginContextManagerExit();
                b.emitLoadLocal(ex);
                b.emitLoadLocal(exit);
                b.emitLoadLocal(contextManager);
                b.endContextManagerExit();
            }
            b.endBlock(); // try-catch handler

            b.endTryCatch();
            b.endBlock(); // try

            b.endFinallyTryNoExcept();
            endNode(item);
        }

        @Override
        public Void visit(StmtTy.With node) {
            beginNode(node);
            visitWithRecurse(node.items, 0, node.body, false);
            endNode(node);
            return null;
        }

        @Override
        public Void visit(WithItemTy node) {
            throw new UnsupportedOperationException("" + node.getClass());
        }

        @Override
        public Void visit(StmtTy.Break aThis) {
            beginNode(aThis);

            assert breakLabel != null;
            b.emitBranch(breakLabel);

            endNode(aThis);
            return null;
        }

        @Override
        public Void visit(StmtTy.Continue aThis) {
            beginNode(aThis);

            assert continueLabel != null;
            b.emitBranch(continueLabel);

            endNode(aThis);
            return null;
        }

        @Override
        public Void visit(StmtTy.Pass aThis) {
            return null;
        }
    }
}
