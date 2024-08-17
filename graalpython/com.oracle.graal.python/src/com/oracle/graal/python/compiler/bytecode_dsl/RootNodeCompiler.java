package com.oracle.graal.python.compiler.bytecode_dsl;

import static com.oracle.graal.python.compiler.CompilationScope.Class;
import static com.oracle.graal.python.util.PythonUtils.toTruffleStringUncached;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.J___CLASS__;

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
import com.oracle.graal.python.compiler.Unparser;
import com.oracle.graal.python.nodes.StringLiterals;
import com.oracle.graal.python.nodes.bytecode_dsl.BytecodeDSLCodeUnit;
import com.oracle.graal.python.nodes.bytecode_dsl.PBytecodeDSLRootNode;
import com.oracle.graal.python.nodes.bytecode_dsl.PBytecodeDSLRootNodeGen;
import com.oracle.graal.python.nodes.bytecode_dsl.PBytecodeDSLRootNodeGen.Builder;
import com.oracle.graal.python.pegparser.FutureFeature;
import com.oracle.graal.python.pegparser.ErrorCallback.ErrorType;
import com.oracle.graal.python.pegparser.ErrorCallback.WarningType;
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
import com.oracle.graal.python.pegparser.sst.ExprTy.Constant;
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
import com.oracle.truffle.api.instrumentation.StandardTags.StatementTag;
import com.oracle.truffle.api.bytecode.BytecodeParser;
import com.oracle.truffle.api.strings.TruffleString;

/**
 * Visitor that compiles a top-level AST (modules, functions, classes, etc.) to a root node.
 * Produces a {@link BytecodeDSLCompilerResult}.
 * <p>
 * This visitor is a small wrapper that calls into another visitor, {@link StatementCompiler}, to
 * produce bytecode for the various statements/expressions within the AST.
 */
public class RootNodeCompiler implements BaseBytecodeDSLVisitor<BytecodeDSLCompilerResult> {
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

    // Immutable after construction
    private final HashMap<String, Integer> varnames;
    private final HashMap<String, Integer> cellvars;
    private final HashMap<String, Integer> freevars;
    private final int[] cell2arg;
    private final String selfCellName;

    // Updated idempotently
    private final Map<String, BytecodeLocal> locals = new HashMap<>();
    private final Map<String, BytecodeLocal> cellLocals = new HashMap<>();
    private final Map<String, BytecodeLocal> freeLocals = new HashMap<>();
    private final HashMap<Object, Integer> constants = new HashMap<>();
    private final HashMap<String, Integer> names = new HashMap<>();

    // Mutable (must be reset)
    private SourceRange currentLocation;

    public RootNodeCompiler(BytecodeDSLCompilerContext ctx, SSTNode rootNode, EnumSet<FutureFeature> futureFeatures) {
        this.ctx = ctx;
        this.startNode = rootNode;
        this.scope = ctx.scopeEnvironment.lookupScope(rootNode);
        this.scopeType = getScopeType(scope, rootNode);
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

    private Object addConstant(Object c) {
        Integer v = constants.get(c);
        if (v == null) {
            v = constants.size();
            constants.put(c, v);
        }
        return c;
    }

    private static TruffleString[] orderedTruffleStringArray(HashMap<String, Integer> map) {
        return orderedKeys(map, new TruffleString[0], PythonUtils::toTruffleStringUncached);
    }

    private BytecodeDSLCompilerResult compileRootNode(String name, ArgumentInfo argumentInfo, SourceRange sourceRange, BytecodeParser<Builder> parser) {
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
                        classcellIndex,
                        selfIndex,
                        null,
                        nodes);
        rootNode.setMetadata(codeUnit, ctx.errorCallback);
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

    protected final void checkForbiddenName(String id, NameOperation context) {
        if (context == NameOperation.BeginWrite) {
            if (id.equals("__debug__")) {
                ctx.errorCallback.onError(ErrorType.Syntax, currentLocation, "cannot assign to __debug__");
            }
        }
        if (context == NameOperation.Delete) {
            if (id.equals("__debug__")) {
                ctx.errorCallback.onError(ErrorType.Syntax, currentLocation, "cannot delete __debug__");
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

    public void reset() {
        this.currentLocation = null;
    }

    // -------------- helpers --------------

    void beginRootNode(SSTNode node, ArgumentsTy args, Builder b) {
        reset();
        b.beginSource(ctx.source);
        beginRootSourceSection(node, b);

        b.beginRoot();

        checkForbiddenArgs(args);
        setUpFrame(args, b);

        b.emitTraceOrProfileCall();
    }

    void endRootNode(Builder b) {
        b.endRoot();
        endRootSourceSection(b);
        b.endSource();
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
                b.emitTraceLine(sourceRange.startLine);
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
        SourceRange sourceRange = node.getSourceRange();

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
        return compileRootNode("<interactive>", ArgumentInfo.NO_ARGS, node.getSourceRange(), b -> {
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
                            expr.value.accept(statementCompiler);
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
                            && constant.value.kind == ConstantValue.Kind.RAW) {
                return constant.value.getRaw(TruffleString.class);
            }
        }
        return null;
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

        StatementCompiler statementCompiler = new StatementCompiler(b);

        if (isRegularLambda) {
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

    @Override
    public BytecodeDSLCompilerResult visit(StmtTy.ClassDef node) {
        return compileRootNode(node.name, ArgumentInfo.NO_ARGS, node.getSourceRange(), b -> {
            beginRootNode(node, null, b);

            beginStoreLocal("__module__", b);
            emitReadLocal("__name__", b);
            endStoreLocal("__module__", b);

            beginStoreLocal("__qualname__", b);
            emitPythonConstant(toTruffleStringUncached(ctx.getQualifiedName(scope)), b);
            endStoreLocal("__qualname__", b);

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

    private boolean beginComprehension(ComprehensionTy comp, int index, Builder b) {
        boolean newStatement = beginSourceSection(comp, b);

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

        b.beginBlock();
        b.emitTraceLineAtLoopHeader(currentLocation.startLine);
        b.beginForIterate(localValue);
        b.emitLoadLocal(localIter);
        b.endForIterate();
        b.endBlock();

        b.beginBlock();

        comp.target.accept(statementCompiler.new StoreVisitor(() -> b.emitLoadLocal(localValue)));

        if (comp.ifs != null) {
            for (int i = 0; i < comp.ifs.length; i++) {
                b.beginIfThen();
                statementCompiler.visitCondition(comp.ifs[i]);
                b.beginBlock();
            }
        }

        return newStatement;
    }

    private void endComprehension(ComprehensionTy comp, Builder b, boolean newStatement) {
        if (comp.ifs != null) {
            for (int i = 0; i < len(comp.ifs); i++) {
                b.endBlock();
                b.endIfThen();
            }
        }

        b.endBlock();
        b.endWhile();

        endSourceSection(b, newStatement);
    }

    private BytecodeDSLCompilerResult buildComprehensionCodeUnit(SSTNode node, ComprehensionTy[] generators, String name,
                    Consumer<StatementCompiler> emptyCollectionProducer,
                    BiConsumer<StatementCompiler, BytecodeLocal> accumulateProducer) {
        return compileRootNode(name, new ArgumentInfo(1, 0, 0, false, false), node.getSourceRange(), b -> {
            beginRootNode(node, null, b);

            StatementCompiler statementCompiler = new StatementCompiler(b);
            boolean isGenerator = emptyCollectionProducer == null;
            BytecodeLocal collectionLocal = null;
            if (!isGenerator) {
                collectionLocal = b.createLocal();
                b.beginStoreLocal(collectionLocal);
                emptyCollectionProducer.accept(statementCompiler);
                b.endStoreLocal();
            }

            boolean[] newStatement = new boolean[generators.length];
            for (int i = 0; i < generators.length; i++) {
                newStatement[i] = beginComprehension(generators[i], i, b);
            }
            accumulateProducer.accept(statementCompiler, collectionLocal);
            for (int i = generators.length - 1; i >= 0; i--) {
                endComprehension(generators[i], b, newStatement[i]);
            }

            beginReturn(b);
            if (isGenerator) {
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

    /**
     * Use this method for values that should show up in co_consts.
     */
    private void emitPythonConstant(Object constant, Builder b) {
        b.emitLoadConstant(addConstant(constant));
    }

    /**
     * This helper encapsulates all of the logic needed to yield and resume. Yields should not be
     * emitted directly.
     */
    private static void emitYield(Consumer<StatementCompiler> yieldValueProducer, StatementCompiler statementCompiler) {
        statementCompiler.b.beginResumeYield();
        statementCompiler.b.beginYield();
        statementCompiler.b.beginPreYield();
        yieldValueProducer.accept(statementCompiler);
        statementCompiler.b.endPreYield();
        statementCompiler.b.endYield();
        statementCompiler.b.endResumeYield();
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
                    b.beginClassLoadCell(index);
                    b.emitLoadLocal(local);
                    b.endClassLoadCell();
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
                b.beginCheckUnboundLocal(varnames.get(mangled));
                b.emitLoadLocal(local);
                b.endCheckUnboundLocal();
                break;
            case Delete:
                b.beginBlock();
                b.beginCheckUnboundLocal(varnames.get(mangled));
                b.emitLoadLocal(local);
                b.endCheckUnboundLocal();

                b.beginStoreLocal(local);
                b.emitLoadNull();
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
                b.emitReadGlobal(tsName);
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

    private void emitNameSlowOperation(String name, NameOperation op, Builder b) {
        assert locals.get(name) == null;
        names.putIfAbsent(name, names.size());
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
                    b.emitLoadNull();
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

            b.beginGetAttribute(toTruffleStringUncached(mangle(node.attr)));
            node.value.accept(this);
            b.endGetAttribute();

            endSourceSection(b, newStatement);

            return null;
        }

        @Override
        public Void visit(ExprTy.Await node) {
            if (!scope.isFunction()) {
                ctx.errorCallback.onError(ErrorType.Syntax, currentLocation, "'await' outside function");
            }
            if (scopeType != CompilationScope.AsyncFunction && scopeType != CompilationScope.Comprehension) {
                ctx.errorCallback.onError(ErrorType.Syntax, currentLocation, "'await' outside async function");
            }
            boolean newStatement = beginSourceSection(node, b);
            emitAwait(() -> node.value.accept(this));
            endSourceSection(b, newStatement);
            return null;
        }

        @Override
        public Void visit(ExprTy.BinOp node) {
            boolean newStatement = beginSourceSection(node, b);
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

            endSourceSection(b, newStatement);
            return null;
        }

        @Override
        public Void visit(ExprTy.BoolOp node) {
            boolean newStatement = beginSourceSection(node, b);

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

                    emitUnstar(() -> b.emitLoadLocal(receiver), args);
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

            String mangled = mangle(attrAccess.attr);
            b.beginGetMethod(toTruffleStringUncached(mangled));
            b.emitLoadLocal(receiver);
            b.endGetMethod();
            b.endBlock();
        }

        @Override
        public Void visit(ExprTy.Call node) {
            boolean newStatement = beginSourceSection(node, b);
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
            checkCompare(node);

            boolean multipleComparisons = node.comparators.length > 1;

            if (multipleComparisons) {
                b.beginBoolAnd();
            }

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

            if (multipleComparisons) {
                b.endBoolAnd();
            }

            endSourceSection(b, newStatement);
            return null;
        }

        private void warn(SSTNode node, String message, Object... arguments) {
            ctx.errorCallback.onWarning(WarningType.Syntax, node.getSourceRange(), message, arguments);
        }

        private void checkCompare(ExprTy node) {
            if (!(node instanceof ExprTy.Compare compare)) {
                return;
            }
            boolean left = checkIsArg(compare.left);
            int n = compare.ops == null ? 0 : compare.ops.length;
            for (int i = 0; i < n; ++i) {
                CmpOpTy op = compare.ops[i];
                boolean right = checkIsArg(compare.comparators[i]);
                if (op == CmpOpTy.Is || op == CmpOpTy.IsNot) {
                    if (!right || !left) {
                        warn(compare, op == CmpOpTy.Is ? "\"is\" with a literal. Did you mean \"==\"?" : "\"is not\" with a literal. Did you mean \"!=\"?");
                    }
                }
                left = right;
            }
        }

        private static boolean checkIsArg(ExprTy e) {
            if (e instanceof ExprTy.Constant) {
                ConstantValue.Kind kind = ((Constant) e).value.kind;
                return kind == Kind.NONE || kind == Kind.BOOLEAN || kind == Kind.ELLIPSIS;
            }
            return true;
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
                case RAW:
                    emitPythonConstant(value.getRaw(TruffleString.class), b);
                    break;
                case BYTES:
                    addConstant(value.getBytes());
                    b.emitLoadBytes(value.getBytes());
                    break;
                case TUPLE:
                    b.beginMakeTuple();
                    b.beginMakeVariadic();
                    for (ConstantValue cv : value.getTupleElements()) {
                        createConstant(cv);
                    }
                    b.endMakeVariadic();
                    b.endMakeTuple();
                    break;
                case FROZENSET:
                    b.beginMakeFrozenSet(value.getFrozensetElements().length);
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
            createConstant(node.value);
            endSourceSection(b, newStatement);
            return null;
        }

        @Override
        public Void visit(ExprTy.Dict node) {
            boolean newStatement = beginSourceSection(node, b);

            if (len(node.keys) == 0) {
                b.emitMakeEmptyDict();
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

            endSourceSection(b, newStatement);
            return null;
        }

        @Override
        public Void visit(ExprTy.DictComp node) {
            boolean newStatement = beginSourceSection(node, b);

            b.beginCallUnaryMethod();
            emitMakeFunction(node, "<dictcomp>", COMPREHENSION_ARGS, null);
            node.generators[0].iter.accept(this);
            b.endCallUnaryMethod();

            endSourceSection(b, newStatement);
            return null;
        }

        @Override
        public Void visit(ExprTy.FormattedValue node) {
            boolean newStatement = beginSourceSection(node, b);
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
            endSourceSection(b, newStatement);

            return null;
        }

        @Override
        public Void visit(ExprTy.GeneratorExp node) {
            boolean newStatement = beginSourceSection(node, b);

            b.beginCallUnaryMethod();
            emitMakeFunction(node, "<generator>", COMPREHENSION_ARGS, null);
            node.generators[0].iter.accept(this);
            b.endCallUnaryMethod();

            endSourceSection(b, newStatement);
            return null;
        }

        @Override
        public Void visit(ExprTy.IfExp node) {
            boolean newStatement = beginSourceSection(node, b);

            b.beginConditional();
            visitCondition(node.test);
            node.body.accept(this);
            node.orElse.accept(this);
            b.endConditional();

            endSourceSection(b, newStatement);
            return null;
        }

        @Override
        public Void visit(ExprTy.JoinedStr node) {
            boolean newStatement = beginSourceSection(node, b);

            if (node.values.length == 1) {
                node.values[0].accept(this);
            } else {
                b.beginBuildString(node.values.length);
                visitSequence(node.values);
                b.endBuildString();
            }

            endSourceSection(b, newStatement);
            return null;
        }

        @Override
        public Void visit(ExprTy.Lambda node) {
            boolean newStatement = beginSourceSection(node, b);
            emitMakeFunction(node, "<lambda>", node.args, null);
            endSourceSection(b, newStatement);
            return null;
        }

        @Override
        public Void visit(ExprTy.List node) {
            boolean newStatement = beginSourceSection(node, b);

            ConstantCollection constantCollection = Compiler.tryCollectConstantCollection(node.elements);
            if (constantCollection != null) {
                emitConstantList(constantCollection);
            } else {
                b.beginMakeList();
                emitUnstar(node.elements);
                b.endMakeList();
            }

            endSourceSection(b, newStatement);
            return null;
        }

        private static final String COMPREHENSION_ARGUMENT_NAME = ".0";
        private static final ArgumentsTy COMPREHENSION_ARGS = new ArgumentsTy(new ArgTy[]{new ArgTy(COMPREHENSION_ARGUMENT_NAME, null, null, null)}, null, null, null, null, null, null, null);

        @Override
        public Void visit(ExprTy.ListComp node) {
            boolean newStatement = beginSourceSection(node, b);

            b.beginCallUnaryMethod();
            emitMakeFunction(node, "<listcomp>", COMPREHENSION_ARGS, null);
            node.generators[0].iter.accept(this);
            b.endCallUnaryMethod();

            endSourceSection(b, newStatement);
            return null;
        }

        @Override
        public Void visit(ExprTy.Name node) {
            boolean newStatement = beginSourceSection(node, b);
            emitReadLocal(node.id, b);
            endSourceSection(b, newStatement);
            return null;
        }

        @Override
        public Void visit(ExprTy.NamedExpr node) {
            boolean newStatement = beginSourceSection(node, b);
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

        /**
         * Same as above, but takes an optional Runnable to produce elements at the beginning of the
         * sequence.
         *
         * @param initialElementsProducer a runnable to produce the first element(s) of the
         *            sequence.
         * @param args the sequence of expressions to unstar
         */
        private void emitUnstar(Runnable initialElementsProducer, ExprTy[] args) {
            if (len(args) == 0 && initialElementsProducer == null) {
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
                 *   UnpackStarred(f),
                 *   MakeVariadic(g)
                 * )
                 * @formatter:on
                 */
                b.beginUnstar();
                boolean inVariadic = false;
                int numOperands = 0;

                if (initialElementsProducer != null) {
                    b.beginMakeVariadic();
                    initialElementsProducer.run();
                    inVariadic = true;
                }

                for (int i = 0; i < args.length; i++) {
                    if (args[i] instanceof ExprTy.Starred) {
                        if (inVariadic) {
                            b.endMakeVariadic();
                            inVariadic = false;
                            numOperands++;
                        }

                        b.beginUnpackStarred();
                        ((ExprTy.Starred) args[i]).value.accept(this);
                        b.endUnpackStarred();
                        numOperands++;
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
                    numOperands++;
                }

                b.endUnstar(numOperands);
            } else {
                b.beginMakeVariadic();
                if (initialElementsProducer != null) {
                    initialElementsProducer.run();
                }
                visitSequence(args);
                b.endMakeVariadic();
            }
        }

        @Override
        public Void visit(ExprTy.Set node) {
            boolean newStatement = beginSourceSection(node, b);

            if (len(node.elements) == 0) {
                b.emitMakeEmptySet();
            } else {
                b.beginMakeSet();
                emitUnstar(node.elements);
                b.endMakeSet();
            }

            endSourceSection(b, newStatement);
            return null;
        }

        @Override
        public Void visit(ExprTy.SetComp node) {
            boolean newStatement = beginSourceSection(node, b);

            b.beginCallUnaryMethod();
            emitMakeFunction(node, "<setcomp>", COMPREHENSION_ARGS, null);
            node.generators[0].iter.accept(this);
            b.endCallUnaryMethod();

            endSourceSection(b, newStatement);
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

            b.beginMakeSlice();

            visitNoneable(node.lower);
            visitNoneable(node.upper);
            visitNoneable(node.step);

            b.endMakeSlice();

            endSourceSection(b, newStatement);
            return null;
        }

        @Override
        public Void visit(ExprTy.Starred node) {
            throw new UnsupportedOperationException("" + node.getClass());
        }

        @Override
        public Void visit(ExprTy.Subscript node) {
            boolean newStatement = beginSourceSection(node, b);

            b.beginGetItem();
            node.value.accept(this);
            node.slice.accept(this);
            b.endGetItem();

            endSourceSection(b, newStatement);
            return null;
        }

        @Override
        public Void visit(ExprTy.Tuple node) {
            boolean newStatement = beginSourceSection(node, b);

            ConstantCollection constantCollection = Compiler.tryCollectConstantCollection(node.elements);
            if (constantCollection != null) {
                emitConstantTuple(constantCollection);
            } else {
                b.beginMakeTuple();
                emitUnstar(node.elements);
                b.endMakeTuple();
            }

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
                    visit(new ExprTy.Constant(cv, null, c.getSourceRange()));
                    endSourceSection(b, newStatement);
                    return null;
                }
            }
            boolean newStatement = beginSourceSection(node, b);
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

            endSourceSection(b, newStatement);
            return null;
        }

        @Override
        public Void visit(ExprTy.Yield node) {
            if (!scope.isFunction()) {
                ctx.errorCallback.onError(ErrorType.Syntax, currentLocation, "'yield' outside function");
            }
            boolean newStatement = beginSourceSection(node, b);
            emitYield((statementCompiler) -> {
                if (node.value != null) {
                    node.value.accept(this);
                } else {
                    statementCompiler.b.emitLoadConstant(PNone.NONE);
                }
            }, this);

            endSourceSection(b, newStatement);
            return null;
        }

        @Override
        public Void visit(ExprTy.YieldFrom node) {
            if (!scope.isFunction()) {
                ctx.errorCallback.onError(ErrorType.Syntax, currentLocation, "'yield' outside function");
            }
            if (scopeType == CompilationScope.AsyncFunction) {
                ctx.errorCallback.onError(ErrorType.Syntax, currentLocation, "'yield from' inside async function");
            }
            boolean newStatement = beginSourceSection(node, b);
            emitYieldFrom(() -> {
                b.beginGetYieldFromIter();
                node.value.accept(this);
                b.endGetYieldFromIter();
            });
            endSourceSection(b, newStatement);
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
                endSourceSection(b, newStatement);
            }
            return null;
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
                beginStoreLocal(node.id, b);
                generateValue.run();
                endStoreLocal(node.id, b);
                endSourceSection(b, newStatement);
                return null;
            }

            @Override
            public Void visit(ExprTy.Attribute node) {
                boolean newStatement = beginSourceSection(node, b);
                checkForbiddenName(node.attr, NameOperation.BeginWrite);
                b.beginSetAttribute(toTruffleStringUncached(mangle(node.attr)));
                generateValue.run();
                node.value.accept(StatementCompiler.this);
                b.endSetAttribute();
                endSourceSection(b, newStatement);
                return null;
            }

            @Override
            public Void visit(ExprTy.Subscript node) {
                boolean newStatement = beginSourceSection(node, b);
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
                visitIterableAssign(node.elements);
                endSourceSection(b, newStatement);
                return null;
            }

            @Override
            public Void visit(ExprTy.List node) {
                boolean newStatement = beginSourceSection(node, b);
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
                boolean newStatement = beginSourceSection(node, b);

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
            public Void visit(ExprTy.Attribute node) {
                boolean newStatement = beginSourceSection(node, b);
                checkForbiddenName(node.attr, NameOperation.BeginWrite);
                b.beginBlock();
                // {
                BytecodeLocal target = b.createLocal();

                b.beginStoreLocal(target);
                node.value.accept(StatementCompiler.this);
                b.endStoreLocal();

                TruffleString attrName = toTruffleStringUncached(mangle(node.attr));
                b.beginSetAttribute(attrName);
                beginAugAssign();

                b.beginGetAttribute(attrName);
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
            emitNotImplemented("async for", b);
            return null;
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
            endSourceSection(b, newStatement);
            return null;
        }

        @Override
        public Void visit(StmtTy.AugAssign node) {
            boolean newStatement = beginSourceSection(node, b);
            node.target.accept(new AugStoreVisitor(node.op, node.value));
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
                b.beginKwargsMerge();
                emitKeywordsRecursive(groups, i - 1, function);
                emitKeywordGroup(groups[i], false, function);
                b.emitLoadLocal(function);
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
            boolean newStatement = beginSourceSection(node, b);
            b.beginBlock();
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
            emitUnstar(() -> {
                emitMakeFunction(node, node.name, null, null);
                emitPythonConstant(toTruffleStringUncached(node.name), b);
            }, node.bases);

            // keyword args
            validateKeywords(node.keywords);
            emitKeywords(node.keywords, buildClassFunction);

            b.endCallVarargsMethod();

            for (int i = 0; i < numDeco; i++) {
                b.endCallUnaryMethod();
            }

            endStoreLocal(node.name, b);

            b.endBlock();
            endSourceSection(b, newStatement);

            return null;
        }

        private class DeleteVisitor implements BaseBytecodeDSLVisitor<Void> {

            @Override
            public Void visit(ExprTy.Subscript node) {
                boolean newStatement = beginSourceSection(node, b);

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
                checkForbiddenName(node.attr, NameOperation.BeginWrite);
                b.beginDeleteAttribute(toTruffleStringUncached(node.attr));
                node.value.accept(StatementCompiler.this);
                b.endDeleteAttribute();

                endSourceSection(b, newStatement);
                return null;
            }

            @Override
            public Void visit(ExprTy.Name node) {
                boolean newStatement = beginSourceSection(node, b);
                emitNameOperation(node.id, NameOperation.Delete, b);
                endSourceSection(b, newStatement);
                return null;
            }

            @Override
            public Void visit(ExprTy.Tuple node) {
                boolean newStatement = beginSourceSection(node, b);
                b.beginBlock();
                visitSequence(node.elements);
                b.endBlock();
                endSourceSection(b, newStatement);
                return null;
            }

            @Override
            public Void visit(ExprTy.List node) {
                boolean newStatement = beginSourceSection(node, b);
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
            } else {
                node.value.accept(this);
            }
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
            return null;
        }

        @Override
        public Void visit(StmtTy.FunctionDef node) {
            boolean newStatement = beginSourceSection(node, b);
            b.beginBlock();

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

            b.endBlock();
            endSourceSection(b, newStatement);
            return null;
        }

        @Override
        public Void visit(StmtTy.AsyncFunctionDef node) {
            boolean newStatement = beginSourceSection(node, b);
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
            endSourceSection(b, newStatement);
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

            TruffleString functionName = toTruffleStringUncached(name);
            Scope targetScope = ctx.scopeEnvironment.lookupScope(node);
            TruffleString qualifiedName = toTruffleStringUncached(ctx.getQualifiedName(targetScope));

            // Register these in the Python constants list.
            addConstant(qualifiedName);
            addConstant(codeUnit);

            b.beginMakeFunction(functionName, qualifiedName, codeUnit);

            if (args == null || len(args.defaults) == 0) {
                b.emitLoadConstant(PythonUtils.EMPTY_OBJECT_ARRAY);
            } else {
                b.beginMakeVariadic();
                for (int i = 0; i < args.defaults.length; i++) {
                    args.defaults[i].accept(this);
                }
                b.endMakeVariadic();
            }

            boolean hasKeywords = false;
            if (args != null && len(args.kwDefaults) != 0) {
                // We only emit keywords with default values. Check if any exist.
                for (int i = 0; i < args.kwDefaults.length; i++) {
                    if (args.kwDefaults[i] != null) {
                        hasKeywords = true;
                        break;
                    }
                }
            }

            if (!hasKeywords) {
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

            if (codeUnit.freevars.length == 0) {
                b.emitLoadNull();
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
            boolean newStatement = beginSourceSection(node, b);
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
            if (node.getSourceRange().startLine > ctx.futureLineNumber && "__future__".equals(node.module)) {
                ctx.errorCallback.onError(ErrorType.Syntax, node.getSourceRange(), "from __future__ imports must occur at the beginning of the file");
            }

            TruffleString tsModuleName = toTruffleStringUncached(node.module == null ? "" : node.module);

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
            boolean newStatement = beginSourceSection(pattern, b);
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

        private void patternHelperSequenceUnpack(PatternTy[] patterns, PatternContext pc) {
            int n = len(patterns);

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
            if (!scope.isFunction()) {
                ctx.errorCallback.onError(ErrorType.Syntax, currentLocation, "'return' outside function");
            }
            boolean newStatement = beginSourceSection(node, b);
            beginReturn(b);
            if (node.value != null) {
                node.value.accept(this);
            } else {
                b.emitLoadConstant(PNone.NONE);
            }
            endReturn(b);
            endSourceSection(b, newStatement);
            return null;
        }

        @Override
        public Void visit(StmtTy.Try node) {
            boolean newStatement = beginSourceSection(node, b);
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
                 * } finally {
                 *   finally_body
                 * } catch uncaught_ex {
                 *   save current exception
                 *   set the current exception to uncaught_ex
                 *   markCaught(uncaught_ex)
                 *   try {
                 *     finally_body
                 *   } finally {
                 *     restore current exception
                 *   } catch handler_ex {
                 *     restore current exception
                 *     markCaught(handler_ex)
                 *     reraise handler_ex
                 *   }
                 *   reraise uncaught_ex
                 * }
                 */
                b.beginTryFinallyCatch(() -> {
                    b.beginBlock(); // finally
                        visitSequence(node.finalBody);
                    b.endBlock();
                });

                    emitTryExceptElse(node); // try

                    b.beginBlock(); // catch
                        BytecodeLocal savedException = b.createLocal();
                        emitSaveCurrentException(savedException);
                        emitSetCurrentException();
                        // Mark this location for the stack trace.
                        b.beginMarkExceptionAsCaught();
                            b.emitLoadException();
                        b.endMarkExceptionAsCaught();

                        b.beginTryFinallyCatch(() -> emitRestoreCurrentException(savedException));
                            b.beginBlock(); // try
                                visitSequence(node.finalBody);
                            b.endBlock(); // try

                            b.beginBlock(); // catch
                                emitRestoreCurrentException(savedException);

                                b.beginMarkExceptionAsCaught();
                                    b.emitLoadException();
                                b.endMarkExceptionAsCaught();

                                b.beginReraise();
                                    b.emitLoadException();
                                b.endReraise();
                            b.endBlock(); // catch
                        b.endTryFinallyCatch();

                        b.beginReraise();
                            b.emitLoadException();
                        b.endReraise();
                    b.endBlock(); // catch
                b.endTryFinallyCatch();
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
                 *       } finally {
                 *         unbind handler_1_name
                 *       } catch handler_1_ex {
                 *         unbind handler_1_name
                 *         // Freeze the bci before it gets rethrown.
                 *         markCaught(handler_ex)
                 *         throw handler_1_ex
                 *       }
                 *       goto afterElse
                 *     }
                 *     ... // more handlers
                 *
                 *     // case 1: bare except
                 *     bare_except_body
                 *     goto afterElse
                 *   } finally {
                 *     // Exception handled. Restore the exception state.
                 *     restore current exception
                 *   } catch handler_ex {
                 *     // A handler raised or no handler was found. Restore exception state and reraise.
                 *     restore current exception
                 *     markCaught(handler_ex) // (no-op if handler_ex is the original exception)
                 *     reraise handler_ex
                 *   }
                 *   // case 2: no bare except (we only reach this point if no handler matched/threw)
                 *   reraise ex
                 * }
                 * else_body
                 * afterElse:
                 */
                b.beginBlock(); // outermost block

                BytecodeLocal savedException = b.createLocal();
                BytecodeLabel afterElse = b.createLabel();

                b.beginTryCatch();

                    b.beginBlock(); // try
                        visitSequence(node.body);
                    b.endBlock(); // try

                    b.beginBlock(); // catch
                        emitSaveCurrentException(savedException);
                        emitSetCurrentException();
                        // Mark this location for the stack trace.
                        b.beginMarkExceptionAsCaught();
                            b.emitLoadException(); // ex
                        b.endMarkExceptionAsCaught();

                        b.beginTryFinallyCatch(() -> emitRestoreCurrentException(savedException));
                            b.beginBlock(); // try
                                SourceRange bareExceptRange = null;
                                for (ExceptHandlerTy h : node.handlers) {
boolean newStatement = beginSourceSection(h, b);
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

                                        b.beginTryFinallyCatch(() -> emitUnbindHandlerVariable(handler));
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
                                        b.endTryFinallyCatch();
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
                        b.endTryFinallyCatch();

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

        private void emitSetCurrentException() {
            b.beginSetCurrentException();
            b.emitLoadException();
            b.endSetCurrentException();
        }

        private void emitRestoreCurrentException(BytecodeLocal savedException) {
            b.beginSetCurrentException();
            b.emitLoadLocal(savedException);
            b.endSetCurrentException();
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
            emitNotImplemented("try star", b);
            return null;
        }

        @Override
        public Void visit(ExceptHandlerTy.ExceptHandler node) {
            throw new UnsupportedOperationException("" + node.getClass());
        }

        @Override
        public Void visit(StmtTy.While node) {
            boolean newStatement = beginSourceSection(node, b);
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
             *   } finally {
             *     call __exit__(None, None, None)
             *   } catch ex {
             *     if not __exit__(...):
             *       raise
             *   }
             * @formatter:on
             *
             * When there are multiple context managers, they are recursively generated (where "bar"
             * is). Once we have entered all of the context managers, we emit the body.
             */
            WithItemTy item = items[index];
            boolean newStatement = beginSourceSection(item, b);
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
                emitAwait(() -> b.emitLoadLocal(value));
            } else {
                // call __enter__
                b.beginContextManagerEnter(exit, value);
                b.emitLoadLocal(contextManager);
                b.endContextManagerEnter();
            }

            Runnable finallyHandler;
            if (async) {
                finallyHandler = () -> emitAwait(() -> {
                    b.beginAsyncContextManagerCallExit();
                    b.emitLoadConstant(PNone.NONE);
                    b.emitLoadLocal(exit);
                    b.emitLoadLocal(contextManager);
                    b.endAsyncContextManagerCallExit();
                });
            } else {
                finallyHandler = () -> {
                    // call __exit__
                    b.beginContextManagerExit();
                    b.emitLoadConstant(PNone.NONE);
                    b.emitLoadLocal(exit);
                    b.emitLoadLocal(contextManager);
                    b.endContextManagerExit();
                };
            }
            b.beginTryFinallyCatch(finallyHandler);
            b.beginBlock(); // try
            if (item.optionalVars != null) {
                item.optionalVars.accept(new StoreVisitor(() -> b.emitLoadLocal(value)));
            }
            if (index < items.length - 1) {
                visitWithRecurse(items, index + 1, body, async);
            } else {
                visitSequence(body);
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
                b.beginAsyncContextManagerExit();
                b.emitLoadException();
                emitAwait(() -> {
                    b.beginAsyncContextManagerCallExit();
                    b.emitLoadException();
                    b.emitLoadLocal(exit);
                    b.emitLoadLocal(contextManager);
                    b.endAsyncContextManagerCallExit();
                });
                b.endAsyncContextManagerExit();
            } else {
                // call __exit__
                b.beginContextManagerExit();
                b.emitLoadException();
                b.emitLoadLocal(exit);
                b.emitLoadLocal(contextManager);
                b.endContextManagerExit();
            }
            b.endBlock(); // catch

            b.endTryFinallyCatch();
            b.endBlock();
            endSourceSection(b, newStatement);
        }

        @Override
        public Void visit(StmtTy.With node) {
            boolean newStatement = beginSourceSection(node, b);
            visitWithRecurse(node.items, 0, node.body, false);
            endSourceSection(b, newStatement);
            return null;
        }

        @Override
        public Void visit(WithItemTy node) {
            throw new UnsupportedOperationException("" + node.getClass());
        }

        @Override
        public Void visit(StmtTy.Break aThis) {
            if (breakLabel == null) {
                ctx.errorCallback.onError(ErrorType.Syntax, currentLocation, "'break' outside loop");
            }
            boolean newStatement = beginSourceSection(aThis, b);
            b.emitBranch(breakLabel);
            endSourceSection(b, newStatement);
            return null;
        }

        @Override
        public Void visit(StmtTy.Continue aThis) {
            if (continueLabel == null) {
                ctx.errorCallback.onError(ErrorType.Syntax, currentLocation, "'continue' not properly in loop");
            }
            boolean newStatement = beginSourceSection(aThis, b);
            b.emitBranch(continueLabel);
            endSourceSection(b, newStatement);
            return null;
        }

        @Override
        public Void visit(StmtTy.Pass aThis) {
            return null;
        }
    }
}
