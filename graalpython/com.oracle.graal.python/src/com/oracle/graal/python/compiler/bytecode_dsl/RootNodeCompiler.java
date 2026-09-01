/*
 * Copyright (c) 2024, 2026, Oracle and/or its affiliates. All rights reserved.
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
import static com.oracle.graal.python.compiler.SSTUtils.mayBeForbiddenName;
import static com.oracle.graal.python.compiler.bytecode_dsl.BytecodeDSLCompilerUtils.COMPREHENSION_ARGS;
import static com.oracle.graal.python.compiler.bytecode_dsl.BytecodeDSLCompilerUtils.NO_ARGS;
import static com.oracle.graal.python.compiler.bytecode_dsl.BytecodeDSLCompilerUtils.TYPE_PARAMS_DEFAULTS;
import static com.oracle.graal.python.compiler.bytecode_dsl.BytecodeDSLCompilerUtils.TYPE_PARAMS_DEFAULTS_KWDEFAULTS;
import static com.oracle.graal.python.compiler.bytecode_dsl.BytecodeDSLCompilerUtils.TYPE_PARAMS_KWDEFAULTS;
import static com.oracle.graal.python.compiler.bytecode_dsl.BytecodeDSLCompilerUtils.addObject;
import static com.oracle.graal.python.compiler.bytecode_dsl.BytecodeDSLCompilerUtils.hasDefaultArgs;
import static com.oracle.graal.python.compiler.bytecode_dsl.BytecodeDSLCompilerUtils.hasDefaultKwargs;
import static com.oracle.graal.python.compiler.bytecode_dsl.BytecodeDSLCompilerUtils.len;
import static com.oracle.graal.python.nodes.BuiltinNames.J_BREAKPOINT;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.J___CLASS__;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.J___FIRSTLINENO__;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.J___STATIC_ATTRIBUTES__;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.J___TYPE_PARAMS__;
import static com.oracle.graal.python.util.PythonUtils.codePointsToInternedTruffleString;
import static com.oracle.graal.python.util.PythonUtils.codePointsToTruffleString;
import static com.oracle.graal.python.util.PythonUtils.toInternedTruffleStringUncached;
import static com.oracle.graal.python.util.PythonUtils.toTruffleStringUncached;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
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
import com.oracle.graal.python.builtins.modules.MarshalModuleBuiltins;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.code.PCode;
import com.oracle.graal.python.builtins.objects.ellipsis.PEllipsis;
import com.oracle.graal.python.builtins.objects.function.PArguments;
import com.oracle.graal.python.builtins.objects.function.PKeyword;
import com.oracle.graal.python.builtins.objects.object.PythonObject;
import com.oracle.graal.python.builtins.objects.type.TypeFlags;
import com.oracle.graal.python.compiler.CompilationScope;
import com.oracle.graal.python.compiler.MakeTypeParamKind;
import com.oracle.graal.python.compiler.SSTUtils;
import com.oracle.graal.python.compiler.Unparser;
import com.oracle.graal.python.compiler.bytecode_dsl.BytecodeDSLCompiler.BytecodeDSLCompilerContext;
import com.oracle.graal.python.compiler.bytecode_dsl.BytecodeDSLCompiler.BytecodeDSLCompilerResult;
import com.oracle.graal.python.lib.PyObjectRichCompareBool;
import com.oracle.graal.python.nodes.StringLiterals;
import com.oracle.graal.python.nodes.bytecode_dsl.BytecodeDSLCodeUnit;
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
import com.oracle.truffle.api.bytecode.StackValue;
import com.oracle.truffle.api.bytecode.serialization.BytecodeSerializer;
import com.oracle.truffle.api.debug.DebuggerTags;
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
    private final String selfCellName;

    // Updated idempotently: the keys are filled during first parsing, on subsequent parsings the
    // values will be just overridden, but no new keys should be added.
    private final Map<String, BytecodeLocal> locals = new HashMap<>();
    private final Map<String, BytecodeLocal> cellLocals = new HashMap<>();
    private final Map<String, BytecodeLocal> freeLocals = new HashMap<>();
    private final HashMap<Object, Integer> constants = new HashMap<>();
    private final HashMap<String, Integer> names = new HashMap<>();
    private final Set<String> staticAttributes;

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

    private BytecodeLocal instrumentationDataLocal;
    private int profileCEventStackSize;
    private int maxProfileCEventStackSize;

    public RootNodeCompiler(BytecodeDSLCompilerContext ctx, RootNodeCompiler parent, SSTNode rootNode, EnumSet<FutureFeature> futureFeatures) {
        this(ctx, parent, null, rootNode, rootNode, futureFeatures);
    }

    public RootNodeCompiler(BytecodeDSLCompilerContext ctx, RootNodeCompiler parent, String privateName, SSTNode rootNode, Object scopeKey, EnumSet<FutureFeature> futureFeatures) {
        this.ctx = ctx;
        this.startNode = rootNode;
        this.scope = ctx.scopeEnvironment.lookupScope(scopeKey);
        this.scopeType = getScopeType(scope, scopeKey);
        this.parent = parent;
        this.staticAttributes = scopeType == Class ? new HashSet<>() : null;
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

        String selfCellNameValue = null;
        for (String cellvar : cellvars.keySet()) {
            if (varnames.containsKey(cellvar)) {
                int argIndex = varnames.get(cellvar);
                if (argIndex == 0) {
                    assert selfCellNameValue == null;
                    selfCellNameValue = cellvar;
                }
            }
        }
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
        assert !(c instanceof PythonObject) : "context-specific object in constants: " + c;
        Integer v = constants.get(c);
        if (v == null) {
            v = constants.size();
            constants.put(c, v);
        }
        return c;
    }

    private enum CollectionType {
        INT,
        LONG,
        BOOLEAN,
        DOUBLE,
        OBJECT
    }

    private static final class ConstantCollection {
        final Object collection;
        final CollectionType elementType;

        ConstantCollection(Object collection, CollectionType elementType) {
            this.collection = collection;
            this.elementType = elementType;
        }
    }

    private static ConstantCollection tryCollectConstantCollection(ExprTy[] elements) {
        if (elements == null || elements.length == 0) {
            return null;
        }
        ConstantValue[] values = new ConstantValue[elements.length];
        for (int i = 0; i < elements.length; i++) {
            if (!(elements[i] instanceof ExprTy.Constant constant)) {
                return null;
            }
            values[i] = constant.value;
        }
        return tryCollectConstantCollection(values);
    }

    private static ConstantCollection tryCollectConstantCollection(ConstantValue[] values) {
        if (values == null || values.length == 0) {
            return null;
        }

        CollectionType constantType = null;
        List<Object> constants = new ArrayList<>();

        for (ConstantValue value : values) {
            if (value.kind == ConstantValue.Kind.BOOLEAN) {
                constantType = determineConstantType(constantType, CollectionType.BOOLEAN);
                constants.add(value.getBoolean());
            } else if (value.kind == ConstantValue.Kind.LONG) {
                long val = value.getLong();
                if (val == (int) val) {
                    constantType = determineConstantType(constantType, CollectionType.INT);
                } else {
                    constantType = determineConstantType(constantType, CollectionType.LONG);
                }
                constants.add(val);
            } else if (value.kind == ConstantValue.Kind.DOUBLE) {
                constantType = determineConstantType(constantType, CollectionType.DOUBLE);
                constants.add(value.getDouble());
            } else if (value.kind == ConstantValue.Kind.CODEPOINTS) {
                constantType = determineConstantType(constantType, CollectionType.OBJECT);
                constants.add(codePointsToInternedTruffleString(value.getCodePoints()));
            } else if (value.kind == ConstantValue.Kind.NONE) {
                constantType = determineConstantType(constantType, CollectionType.OBJECT);
                constants.add(PNone.NONE);
            } else {
                return null;
            }
        }
        Object newConstant = null;
        switch (constantType) {
            case OBJECT:
                newConstant = constants.toArray(new Object[0]);
                break;
            case INT: {
                int[] a = new int[constants.size()];
                for (int i = 0; i < a.length; i++) {
                    a[i] = (int) (long) constants.get(i);
                }
                newConstant = a;
                break;
            }
            case LONG: {
                long[] a = new long[constants.size()];
                for (int i = 0; i < a.length; i++) {
                    a[i] = (long) constants.get(i);
                }
                newConstant = a;
                break;
            }
            case BOOLEAN: {
                boolean[] a = new boolean[constants.size()];
                for (int i = 0; i < a.length; i++) {
                    a[i] = (boolean) constants.get(i);
                }
                newConstant = a;
                break;
            }
            case DOUBLE: {
                double[] a = new double[constants.size()];
                for (int i = 0; i < a.length; i++) {
                    a[i] = (double) constants.get(i);
                }
                newConstant = a;
                break;
            }
        }
        return new ConstantCollection(newConstant, constantType);
    }

    private static CollectionType determineConstantType(CollectionType existing, CollectionType type) {
        if (existing == null || existing == type) {
            return type;
        }
        if (existing == CollectionType.LONG && type == CollectionType.INT || existing == CollectionType.INT && type == CollectionType.LONG) {
            return CollectionType.LONG;
        }
        return CollectionType.OBJECT;
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

    private record CodeUnitKey(SSTNode node, CompilationScope scope) {
    }

    private BytecodeDSLCompilerResult compileRootNode(String name, ArgumentInfo argumentInfo, SSTNode node, BytecodeParser<Builder> parser) {
        qualName = getNewScopeQualName(name, scopeType);

        BytecodeRootNodes<PBytecodeDSLRootNode> nodes = PBytecodeDSLRootNodeGen.create(ctx.language, BytecodeConfig.WITH_SOURCE, parser);
        List<PBytecodeDSLRootNode> nodeList = nodes.getNodes();
        assert nodeList.size() == 1;
        PBytecodeDSLRootNode rootNode = nodeList.get(0);

        CodeUnitKey key = new CodeUnitKey(node, scopeType);
        BytecodeDSLCodeUnit codeUnit = ctx.codeUnits.get(key);
        if (codeUnit == null) {
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
            SourceRange sourceRange = getRootSourceRange(node);
            codeUnit = new BytecodeDSLCodeUnit(toInternedTruffleStringUncached(name), toInternedTruffleStringUncached(qualName),
                            argumentInfo.argCount, argumentInfo.kwOnlyArgCount, argumentInfo.positionalOnlyArgCount,
                            flags, orderedTruffleStringArray(names),
                            orderedTruffleStringArray(varnames),
                            orderedTruffleStringArray(cellvars),
                            orderedTruffleStringArray(freevars),
                            orderedKeys(constants, new Object[0]),
                            sourceRange.startLine,
                            sourceRange.startColumn,
                            sourceRange.endLine,
                            sourceRange.endColumn,
                            classcellIndex,
                            selfIndex,
                            yieldFromGenerator != null ? yieldFromGenerator.getLocalIndex() : -1,
                            instrumentationDataLocal.getLocalIndex(),
                            maxProfileCEventStackSize,
                            new BytecodeSupplier(nodes));
            ctx.codeUnits.put(key, codeUnit);
        }
        rootNode.setMetadata(codeUnit, ctx.errorCallback, ctx.source.isInternal());
        return new BytecodeDSLCompilerResult(rootNode, codeUnit);
    }

    static class BytecodeSupplier extends BytecodeDSLCodeUnit.BytecodeSupplier {
        private final BytecodeRootNodes<PBytecodeDSLRootNode> nodes;

        BytecodeSupplier(BytecodeRootNodes<PBytecodeDSLRootNode> nodes) {
            this.nodes = nodes;
        }

        @Override
        public PBytecodeDSLRootNode createRootNode(PythonLanguage language) {
            return nodes.getNode(0);
        }

        @Override
        public byte[] createSerializedBytecode(PythonLanguage language) {
            try {
                BytecodeSerializer serializer = new MarshalModuleBuiltins.PBytecodeDSLSerializer(language);
                ByteArrayOutputStream bytes = new ByteArrayOutputStream();
                nodes.serialize(new DataOutputStream(bytes), serializer);
                return bytes.toByteArray();
            } catch (IOException e) {
                throw CompilerDirectives.shouldNotReachHere(e);
            }
        }
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
        temporaryLocals.clear();
        if (ASSERTIONS_ENABLED) {
            temporaryLocalsTraces.clear();
        }
    }

    // -------------- helpers --------------

    void beginRootNode(SSTNode node, ArgumentsTy args, Builder b) {
        reset();
        b.beginSource(ctx.source);
        beginRootSourceSection(node, b);

        b.beginRoot();

        checkForbiddenArgs(ctx.errorCallback, node.getSourceRange(), args);
        setUpFrame(args, b);

        if (!scope.isGenerator() && !scope.isCoroutine()) {
            b.emitTraceOrProfileCall();
            if (node instanceof ClassDef cls) {
                if (cls.decoratorList != null && cls.decoratorList.length > 0) {
                    b.emitTraceLine(cls.decoratorList[0].getSourceRange().startLine);
                } else {
                    b.emitTraceLine(node.getSourceRange().startLine);
                }
            }
        }
    }

    void endRootNode(Builder b) {
        b.endRoot();
        endRootSourceSection(b);
        b.endSource();
        if (ASSERTIONS_ENABLED && !temporaryLocalsTraces.isEmpty()) {
            throw new AssertionError(this.qualName + "\n\n" + formatTempLocalsStackTraces());
        }
    }

    private String formatTempLocalsStackTraces() {
        StringBuilder sb = new StringBuilder();
        for (Object v : temporaryLocalsTraces.values()) {
            if (v instanceof RuntimeException re) {
                sb.append("\n==================\n");
                StringWriter sw = new StringWriter();
                re.printStackTrace(new PrintWriter(sw));
                sb.append(sw);
            } else {
                return "Run with -Dorg.graalvm.python.trackTempLocals=true to get a stack trace.";
            }
        }
        return sb.toString();
    }

    private static final boolean ASSERTIONS_ENABLED = assertionsEnabled();
    private static final boolean TRACK_TEMP_LOCALS = Boolean.getBoolean("org.graalvm.python.trackTempLocals");

    @SuppressWarnings("all")
    private static boolean assertionsEnabled() {
        boolean enabled = false;
        assert (enabled = true) == true;
        return enabled;
    }

    private HashMap<BytecodeLocal, Object> temporaryLocalsTraces = ASSERTIONS_ENABLED ? new HashMap<>() : null;
    private HashSet<BytecodeLocal> temporaryLocals = new HashSet<>();

    private BytecodeLocal beginTemporaryLocal(Builder b) {
        BytecodeLocal local = b.createLocal();
        temporaryLocals.add(local);
        assert isTemporaryLocal(local);
        if (ASSERTIONS_ENABLED) {
            Object previous = temporaryLocalsTraces.put(local, TRACK_TEMP_LOCALS ? new RuntimeException() : local);
            if (previous != null) {
                throw new AssertionError();
            }
        }
        return local;
    }

    public BytecodeLocal beginTemporaryLocalOrGetLocal(ExprTy target, Builder b) {
        if (target instanceof ExprTy.Name nameExpr) {
            BytecodeLocal l = getFastLocal(nameExpr.id);
            if (l != null) {
                return l;
            }
        }
        return beginTemporaryLocal(b);
    }

    public boolean isTemporaryLocal(BytecodeLocal local) {
        return temporaryLocals.contains(local);
    }

    private void endTemporaryLocal(BytecodeLocal local, Builder b) {
        if (isTemporaryLocal(local)) {
            markTemporaryLocalCleared(local);
            b.emitClearLocal(local);
        }
    }

    private void loadAndEndTemporaryLocal(BytecodeLocal local, Builder b) {
        if (isTemporaryLocal(local)) {
            markTemporaryLocalCleared(local);
            b.emitLoadAndClearTempLocal(local);
        }
    }

    private void markTemporaryLocalCleared(BytecodeLocal local) {
        if (!temporaryLocals.remove(local)) {
            throw new AssertionError();
        }
        if (ASSERTIONS_ENABLED) {
            if (temporaryLocalsTraces.remove(local) == null) {
                throw new AssertionError();
            }
        }
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
        return beginSourceSection(node.getSourceRange(), b);
    }

    /**
     * {@link #beginSourceSection(SSTNode, Builder)}
     */
    boolean beginSourceSection(SourceRange sourceRange, Builder b) {
        SourceRange oldSourceRange = this.currentLocation;
        this.currentLocation = sourceRange;

        beginSourceSectionInner(b, sourceRange);

        if (oldSourceRange == null || oldSourceRange.startLine != sourceRange.startLine) {
            b.beginTag(StatementTag.class);
            b.beginBlock();
            return true;
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
        beginSourceSectionInner(b, getRootSourceRange(node));
    }

    /**
     * Decorated class and function roots start at their first decorator for code object metadata and root source sections,
     * while still ending at the decorated definition.
     */
    private static SourceRange getRootSourceRange(SSTNode node) {
        if (node instanceof ClassDef cls && cls.decoratorList != null && cls.decoratorList.length > 0) {
            return cls.decoratorList[0].getSourceRange().withEnd(node.getSourceRange().endLine, node.getSourceRange().endColumn);
        } else if (node instanceof FunctionDef fn && fn.decoratorList != null && fn.decoratorList.length > 0) {
            return fn.decoratorList[0].getSourceRange().withEnd(node.getSourceRange().endLine, node.getSourceRange().endColumn);
        } else if (node instanceof AsyncFunctionDef fn && fn.decoratorList != null && fn.decoratorList.length > 0) {
            return fn.decoratorList[0].getSourceRange().withEnd(node.getSourceRange().endLine, node.getSourceRange().endColumn);
        } else {
            return node.getSourceRange();
        }
    }

    private static void beginSourceSectionInner(Builder b, SourceRange sourceRange) {
        if (sourceRange.startLine >= 1 && sourceRange != SourceRange.ARTIFICIAL_RANGE) {
            if (sourceRange.startColumn >= 0 && sourceRange.endLine >= sourceRange.startLine && sourceRange.endColumn >= 0) {
                int startColumn = sourceRange.startColumn + 1;
                int endColumn = sourceRange.endColumn > 0 ? sourceRange.endColumn : 1;
                if (sourceRange.endLine == sourceRange.startLine && endColumn < startColumn) {
                    /*
                     * Truffle doesn't allow source sections with empty or inverted ranges. These are
                     * rare, but can occur for string constituents of top-level multiline format
                     * strings and for AST-created code without end-position metadata.
                     */
                    b.beginSourceSection(sourceRange.startLine);
                    return;
                }
                b.beginSourceSection(sourceRange.startLine, startColumn, sourceRange.endLine, endColumn);
            } else {
                b.beginSourceSection(sourceRange.startLine);
            }
        } else {
            b.beginSourceSectionUnavailable();
        }
    }

    void endSourceSection(Builder b, boolean closeTag) {
        if (closeTag) {
            b.endBlock();
            b.endTag(StatementTag.class);
        }
        b.endSourceSection();
    }

    void endRootSourceSection(Builder b) {
        b.endSourceSection();
    }

    void beginReturn(Builder b) {
        b.beginReturn();
    }

    void endReturn(Builder b) {
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
        return compileRootNode("<module>", ArgumentInfo.NO_ARGS, node, b -> {
            beginRootNode(node, null, b);
            visitModuleBody(node.body, b, true);
            endRootNode(b);
        });
    }

    @Override
    public BytecodeDSLCompilerResult visit(ModTy.Expression node) {
        return compileRootNode("<module>", ArgumentInfo.NO_ARGS, node, b -> {
            beginRootNode(node, null, b);
            beginReturn(b);
            new StatementCompiler(b).visitNode(node.body);
            endReturn(b);
            endRootNode(b);
        });
    }

    @Override
    public BytecodeDSLCompilerResult visit(ModTy.Interactive node) {
        return compileRootNode("<module>", ArgumentInfo.NO_ARGS, node, b -> {
            beginRootNode(node, null, b);
            visitModuleBody(node.body, b, false);
            endRootNode(b);
        });
    }

    private void visitModuleBody(StmtTy[] body, Builder b, boolean returnLastStmt) {
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

                for (; i < body.length - 1; i++) {
                    body[i].accept(statementCompiler);
                }

                /*
                 * To support interop eval we need to return the value of the last statement even if
                 * we're in file mode. Also used when parsing with arguments. Note that if there is
                 * only a doc string, although we normally skip it, here we use it as a return
                 * value.
                 */
                StmtTy lastStatement = body.length > 0 ? body[body.length - 1] : null;
                if (returnLastStmt && lastStatement instanceof StmtTy.Expr expr) {
                    // Return the value of the last statement for interop eval.
                    beginReturn(b);
                    boolean closeTag = beginSourceSection(expr, b);
                    expr.value.accept(statementCompiler);
                    endSourceSection(b, closeTag);
                    endReturn(b);
                } else {
                    if (lastStatement != null) {
                        lastStatement.accept(statementCompiler);
                    }
                    beginReturn(b);
                    b.emitLoadConstant(PNone.NONE);
                    endReturn(b);
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
        return compileRootNode(name, ArgumentInfo.fromArguments(args),
                        node, b -> emitFunctionDefBody(node, args, body, b, getDocstring(body), false));
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
        return compileRootNode(name, argInfo, node, b -> {
            beginRootNode(node, typeParamsUnitArgs, b);
            StatementCompiler statementCompiler = new StatementCompiler(b);

            b.beginBlock();

            // typeParamsStackValue = {type parameters}
            b.beginBindStackValue();
            statementCompiler.visitTypeParams(typeParams);
            StackValue typeParamsStackValue = b.endBindStackValue();

            // funStackValue = {make function}
            b.beginBindStackValue();
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
            StackValue funStackValue = b.endBindStackValue();

            // funStackValue.__type_params__ = typeParamsStackValue
            beginSetAttribute(J___TYPE_PARAMS__, b);
            b.emitLoadStackValue(typeParamsStackValue);
            b.emitLoadStackValue(funStackValue);
            b.endSetAttribute();

            // return funStackValue
            b.beginReturn();
            b.emitLoadStackValue(funStackValue);
            b.endReturn();

            b.endBlock();

            endRootNode(b);
        });
    }

    private BytecodeDSLCompilerResult compileBoundTypeVar(TypeVar node) {
        assert node.bound != null;
        return compileRootNode(node.name, ArgumentInfo.NO_ARGS, node, b -> {
            beginRootNode(node, null, b);
            b.beginReturn();
            node.bound.accept(new StatementCompiler(b));
            b.endReturn();
            endRootNode(b);
        });
    }

    private BytecodeDSLCompilerResult compileTypeAliasBody(TypeAlias node) {
        String name = ((ExprTy.Name) node.name).id;
        return compileRootNode(name, ArgumentInfo.NO_ARGS, node, b -> {
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
        return compileRootNode(typeParamsName, ArgumentInfo.NO_ARGS, node, b -> {
            beginRootNode(node, null, b);
            StatementCompiler statementCompiler = new StatementCompiler(b);
            statementCompiler.emitBuildTypeAlias(codeUnit, node);
            endRootNode(b);
        });
    }

    @Override
    public BytecodeDSLCompilerResult visit(ExprTy.Lambda node) {
        return compileRootNode("<lambda>", ArgumentInfo.fromArguments(node.args),
                        node, b -> emitFunctionDefBody(node, node.args, new SSTNode[]{node.body}, b, null, true));
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
        return compileRootNode(node.name, ArgumentInfo.NO_ARGS, node, b -> {
            beginRootNode(node, null, b);

            beginStoreLocal("__module__", b);
            emitReadLocal("__name__", b);
            endStoreLocal("__module__", b);

            beginStoreLocal("__qualname__", b);
            emitPythonConstant(toTruffleStringUncached(this.qualName), b);
            endStoreLocal("__qualname__", b);

            beginStoreLocal(J___FIRSTLINENO__, b);
            int firstLine = node.decoratorList != null && node.decoratorList.length > 0
                            ? node.decoratorList[0].getSourceRange().startLine
                            : node.getSourceRange().startLine;
            emitPythonConstant(firstLine, b);
            endStoreLocal(J___FIRSTLINENO__, b);

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

            beginStoreLocal(J___STATIC_ATTRIBUTES__, b);
            b.beginMakeTuple();
            String[] attributes = staticAttributes.toArray(String[]::new);
            Arrays.sort(attributes);
            for (String attribute : attributes) {
                emitPythonConstant(toTruffleStringUncached(attribute), b);
            }
            b.endMakeTuple();
            endStoreLocal(J___STATIC_ATTRIBUTES__, b);

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
        return compileRootNode(node.name, ArgumentInfo.NO_ARGS, node, b -> {
            beginRootNode(node, null, b);
            StatementCompiler statementCompiler = new StatementCompiler(b);
            statementCompiler.emitBuildClass(classBody, node);
            endRootNode(b);
        });
    }

    private void emitComprehension(ComprehensionTy[] generators, int index, Builder b, ComprehensionType type,
                    StackValue collection,
                    BiConsumer<StatementCompiler, StackValue> accumulateProducer) {
        ComprehensionTy comp = generators[index];
        boolean newStatement = beginSourceSection(comp, b);
        StatementCompiler statementCompiler = new StatementCompiler(b);

        if (comp.isAsync) {
            ExprTy iter = null;
            if (index > 0) {
                iter = comp.iter;
            }
            statementCompiler.emitAsyncFor(iter, comp.target, null, true, index,
                            (stmtComp, idx) -> emitComprehensionBody(generators, idx, type, collection, accumulateProducer, stmtComp));
        } else {
            BytecodeLocal localValue = beginTemporaryLocal(b);

            b.beginBlock();
            b.beginBindStackValue();
            if (index == 0) {
                // The iterator is the function argument for the outermost generator
                b.emitLoadArgument(PArguments.USER_ARGUMENTS_OFFSET);
            } else {
                b.beginGetIter();
                comp.iter.accept(statementCompiler);
                b.endGetIter();
            }
            StackValue iter = b.endBindStackValue();

            b.beginWhile();

            b.beginBlock();
            if (type == ComprehensionType.GENEXPR) {
                b.emitTraceLineAtLoopHeader(currentLocation.startLine);
            } else {
                b.emitClearTraceLine();
            }
            b.beginForIterate(localValue);
            b.emitLoadStackValue(iter);
            b.endForIterate();
            b.endBlock();

            b.beginBlock();

            comp.target.accept(statementCompiler.new StoreVisitor(() -> b.emitLoadLocal(localValue)));
            emitComprehensionBody(generators, index, type, collection, accumulateProducer, statementCompiler);

            b.endBlock();
            b.endWhile();

            b.endBlock();
            assert RootNodeCompiler.this.isTemporaryLocal(localValue);
            endTemporaryLocal(localValue, b);
        }

        endSourceSection(b, newStatement);
    }

    private void emitComprehensionBody(ComprehensionTy[] generators, int index,
                    ComprehensionType type, StackValue collection, BiConsumer<StatementCompiler, StackValue> accumulateProducer,
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
            accumulateProducer.accept(statementCompiler, collection);
        } else {
            emitComprehension(generators, index + 1, b, type, collection, accumulateProducer);
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
                    BiConsumer<StatementCompiler, StackValue> accumulateProducer) {
        if (scope.isCoroutine() && type != ComprehensionType.GENEXPR && scopeType != CompilationScope.AsyncFunction && scopeType != CompilationScope.Comprehension) {
            throw ctx.errorCallback.onError(ErrorType.Syntax, currentLocation, "asynchronous comprehension outside of an asynchronous function");
        }
        return compileRootNode(type.name, new ArgumentInfo(1, 0, 0, false, false), node, b -> {
            beginRootNode(node, null, b);

            assert scope.isGenerator() == (type == ComprehensionType.GENEXPR);
            if (scope.isCoroutine() || scope.isGenerator()) {
                b.beginResumeYieldGenerator();
                b.emitYieldGenerator();
                b.endResumeYieldGenerator();
            }

            StatementCompiler statementCompiler = new StatementCompiler(b);
            b.beginBlock();
            StackValue collection = null;
            if (!scope.isGenerator()) {
                b.beginBindStackValue();
                emptyCollectionProducer.accept(statementCompiler);
                collection = b.endBindStackValue();
            }

            emitComprehension(generators, 0, b, type, collection, accumulateProducer);

            beginReturn(b);
            if (scope.isGenerator()) {
                // TODO: what if someone sends us some value?
                b.emitLoadConstant(PNone.NONE);
            } else {
                b.emitLoadStackValue(collection);
            }
            endReturn(b);
            b.endBlock();

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
                            statementCompiler.b.emitLoadStackValue(collection);
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
                            statementCompiler.b.emitLoadStackValue(collection);
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
                            statementCompiler.b.emitLoadStackValue(collection);
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

    private void addStaticAttribute(ExprTy.Attribute node) {
        if (node.value instanceof ExprTy.Name name && name.id.equals("self")) {
            for (RootNodeCompiler compiler = this; compiler != null; compiler = compiler.parent) {
                if (compiler.staticAttributes != null) {
                    compiler.staticAttributes.add(node.attr);
                    return;
                }
            }
        }
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

        statementCompiler.b.beginResumeYield();
        statementCompiler.b.beginResumeInstrumentedYield();
        statementCompiler.b.beginPreResumeYield(generatorExceptionStateLocal, savedExLocal);

        statementCompiler.b.beginYieldValue();
        statementCompiler.b.beginTraceYieldValue();
        yieldValueProducer.accept(statementCompiler);
        statementCompiler.b.endTraceYieldValue();
        statementCompiler.b.endYieldValue();

        statementCompiler.b.endPreResumeYield();
        statementCompiler.b.endResumeInstrumentedYield();
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
                b.emitLoadLocal(local);
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

    private BytecodeLocal getFastLocal(String name) {
        if (mayBeForbiddenName(name)) {
            return null;
        }
        String mangled = maybeMangle(name);
        EnumSet<DefUse> uses = scope.getUseOfName(mangled);
        if (uses != null) {
            if (uses.contains(DefUse.Free) || uses.contains(DefUse.Cell)) {
                return null;
            } else if (uses.contains(DefUse.Local)) {
                if (scope.isFunction()) {
                    assert varnames.containsKey(mangled) : String.format("scope analysis did not mark %s as a regular variable", mangled);
                    return locals.get(mangled);
                }
            }
        }
        return null;
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
                // For user locals, store a varnames table index in the "info" field.
                int index = varnames.get(regularVariables[i]);
                locals.put(regularVariables[i], b.createLocal(null, index));
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

        // We always create this local, but it is used only by TRACE_AND_PROFILE_CONFIG
        // configuration
        instrumentationDataLocal = b.createLocal();
        b.emitEnterInstrumentedRoot();
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

        private BytecodeLocal beginTemporaryLocal() {
            return RootNodeCompiler.this.beginTemporaryLocal(b);
        }

        /**
         * If the target expression is simple local variable, we can often just directly store into it.
         * Otherwise, we create temporary local. {@link BytecodeLocal} instances returned by this should
         * be passed to {@link #storeTemporaryLocalToTarget(BytecodeLocal, ExprTy, Builder)}.
         */
        public BytecodeLocal beginTemporaryLocalOrGetLocal(ExprTy target, Builder b) {
            return RootNodeCompiler.this.beginTemporaryLocalOrGetLocal(target, b);
        }

        private void storeTemporaryLocalToTarget(BytecodeLocal temporaryLocal, ExprTy target, Builder b) {
            if (RootNodeCompiler.this.isTemporaryLocal(temporaryLocal)) {
                target.accept(new StoreVisitor(() -> {
                    b.emitLoadLocal(temporaryLocal);
                }));
            }
        }

        private void endTemporaryLocal(BytecodeLocal local) {
            RootNodeCompiler.this.endTemporaryLocal(local, b);
        }

        private void loadAndEndTemporaryLocal(BytecodeLocal local) {
            RootNodeCompiler.this.loadAndEndTemporaryLocal(local, b);
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
            b.beginBlock();
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

            b.endBlock();
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

        private void enterProfileCEventCall() {
            profileCEventStackSize++;
            maxProfileCEventStackSize = Math.max(maxProfileCEventStackSize, profileCEventStackSize);
        }

        private void exitProfileCEventCall() {
            assert profileCEventStackSize > 0;
            profileCEventStackSize--;
        }

        private void beginCallNAry(int numArgs) {
            assert numArgs <= NUM_ARGS_MAX_FIXED;
            enterProfileCEventCall();
            b.beginInstrumentCallReturn();
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
            b.endInstrumentCallReturn();
            exitProfileCEventCall();
        }

        private void beginCallNilaryMethod() {
            enterProfileCEventCall();
            b.beginInstrumentCallReturn();
            b.beginCallNilaryMethod();
        }

        private void endCallNilaryMethod() {
            b.endCallNilaryMethod();
            b.endInstrumentCallReturn();
            exitProfileCEventCall();
        }

        private void beginCallUnaryMethod() {
            enterProfileCEventCall();
            b.beginInstrumentCallReturn();
            b.beginCallUnaryMethod();
        }

        private void endCallUnaryMethod() {
            b.endCallUnaryMethod();
            b.endInstrumentCallReturn();
            exitProfileCEventCall();
        }

        private void beginCallVarargsMethod() {
            enterProfileCEventCall();
            b.beginInstrumentCallReturn();
            b.beginCallVarargsMethod();
        }

        private void endCallVarargsMethod() {
            b.endCallVarargsMethod();
            b.endInstrumentCallReturn();
            exitProfileCEventCall();
        }

        private void visitArguments(ExprTy func, ExprTy[] args, int numArgs) {
            visitArguments(func, args, numArgs, true);
        }

        private void visitArguments(ExprTy func, ExprTy[] args, int numArgs, boolean instrumentCall) {
            if (numArgs > 0) {
                for (int i = 0; i < numArgs - 1; i++) {
                    args[i].accept(this);
                }
                if (instrumentCall) {
                    b.beginInstrumentCall();
                }
                beginTraceLineChecked(b);
                args[numArgs - 1].accept(this);
                endTraceLineChecked(func, b);
                if (instrumentCall) {
                    b.endInstrumentCall();
                }
            }
        }

        private void emitCall(ExprTy func, ExprTy[] args, KeywordTy[] keywords) {
            validateKeywords(keywords);

            boolean isMethodCall = isAttributeLoad(func) && keywords.length == 0;
            int numArgs = len(args) + (isMethodCall ? 1 : 0);
            boolean hasKeywords = len(keywords) > 0;
            boolean useVariadic = anyIsStarred(args) || hasKeywords || numArgs > NUM_ARGS_MAX_FIXED;
            KeywordGroup[] keywordGroups = null;
            boolean needsKeywordsMerge = false;
            if (hasKeywords) {
                keywordGroups = partitionKeywords(keywords);
                needsKeywordsMerge = !(keywordGroups.length == 1 && keywordGroups[0] instanceof NamedKeywords);
            }

            StackValue receiver = null;
            if (isMethodCall) {
                // Reserve a stack value for the receiver.
                b.beginBlock();
                b.beginBindStackValue();
                assert isAttributeLoad(func);
                ExprTy.Attribute attrAccess = (ExprTy.Attribute) func;
                attrAccess.value.accept(this);
                receiver = b.endBindStackValue();
            }

            // @formatter:off
            if (useVariadic) {
                beginCallVarargsMethod();
            } else {
                beginCallNAry(numArgs);
            }

            // @formatter:on

            if (isMethodCall) {
                // The receiver is needed for method lookup and for the first argument.
                if (useVariadic) {
                    b.beginInstrumentCallable();
                    emitGetMethod(func, receiver);
                    b.endInstrumentCallable();

                    b.beginCollectToObjectArray();
                    StackValue finalReceiver = receiver;
                    emitUnstar(() -> b.emitLoadStackValue(finalReceiver), args, null, func);
                    b.endCollectToObjectArray();
                    b.beginInstrumentCall();
                    emitEmptyKeywords();
                    b.endInstrumentCall();
                } else {
                    assert len(keywords) == 0;

                    b.beginInstrumentCallable();
                    emitGetMethod(func, receiver);
                    b.endInstrumentCallable();
                    if (numArgs == 1) {
                        b.beginInstrumentCall();
                        b.emitLoadStackValue(receiver); // callable
                        b.endInstrumentCall();
                    } else {
                        b.emitLoadStackValue(receiver); // callable
                        visitArguments(func, args, numArgs - 1);
                    }
                }
            } else {
                if (useVariadic) {
                    StackValue function = null;
                    if (hasKeywords && needsKeywordsMerge) {
                        b.beginBindStackValue();
                        b.beginInstrumentCallable();
                        func.accept(this);
                        b.endInstrumentCallable();
                        function = b.endBindStackValue();
                    } else {
                        b.beginInstrumentCallable();
                        func.accept(this);
                        b.endInstrumentCallable();
                    }

                    b.beginCollectToObjectArray();
                    emitUnstar(null, args, null, func);
                    b.endCollectToObjectArray();
                    b.beginInstrumentCall();
                    if (hasKeywords) {
                        emitNonEmptyKeywords(keywordGroups, function);
                    } else {
                        emitEmptyKeywords();
                    }
                    b.endInstrumentCall();
                } else {
                    assert len(keywords) == 0;

                    boolean isBreakpoint = func instanceof ExprTy.Name && ((ExprTy.Name) func).id.equals(J_BREAKPOINT);

                    if (isBreakpoint) {
                        b.beginTag(DebuggerTags.AlwaysHalt.class);
                    }

                    if (numArgs == 0) {
                        b.beginInstrumentCall();
                    }
                    b.beginInstrumentCallable();
                    func.accept(this); // callable
                    b.endInstrumentCallable();
                    if (numArgs == 0) {
                        b.endInstrumentCall();
                    }
                    visitArguments(func, args, numArgs);

                    if (isBreakpoint) {
                        b.endTag(DebuggerTags.AlwaysHalt.class);
                    }
                }
            }

            // @formatter:off
            if (useVariadic) {
                endCallVarargsMethod();
            } else {
                endCallNAry(numArgs);
            }
            // @formatter:on

            if (isMethodCall) {
                // End the block owning the receiver stack value.
                b.endBlock();
            }
        }

        private void emitGetMethod(ExprTy func, StackValue receiver) {
            assert isAttributeLoad(func);
            ExprTy.Attribute attrAccess = (ExprTy.Attribute) func;
            String mangled = maybeMangle(attrAccess.attr);
            b.beginGetMethod(toTruffleStringUncached(mangled));
            b.emitLoadStackValue(receiver);
            b.endGetMethod();
        }

        @Override
        public Void visit(ExprTy.Call node) {
            boolean newStatement = beginSourceSection(node, b);
            b.beginBlock();
            emitTraceLineChecked(node, b);
            checkCaller(ctx.errorCallback, node.func);
            emitCall(node.func, node.args, node.keywords);
            b.endBlock();
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

        /**
         * Emits bytecode for a Compare node, which has one or more comparisons.
         * <b>
         * When multiple comparisons are present, all operands except the first and last participate
         * in two comparisons. For example, in {@code a <= b <= c <= d}, the value of {@code b} is
         * used in both {@code a <= b} and {@code b <= c}. To support this, we stash the second operand
         * in a temporary and read it in the subsequent comparison. For example, {@code a <= b <= c <= d}
         * is implemented using:
         * <pre>
         * BoolAnd(
         *   Le(a, TeeStackValue(tmp, b)),
         *   Le(LoadStackValue(tmp), TeeStackValue(tmp, c)),
         *   Le(LoadStackValue(tmp), d)
         * )
         * </pre>
         */
        @Override
        public Void visit(ExprTy.Compare node) {
            boolean newStatement = beginSourceSection(node, b);
            checkCompare(ctx.errorCallback, node);

            boolean multipleComparisons = node.comparators.length > 1;

            StackValue tmp = null;
            if (multipleComparisons) {
                b.beginBlock();
                // Reserve a stack value for the operands used in two comparisons.
                b.beginBindStackValue();
                b.emitLoadNull();
                tmp = b.endBindStackValue();

                b.beginBoolAnd();
            }

            for (int i = 0; i < node.comparators.length; i++) {
                beginTraceLineChecked(b);

                boolean isNoneComparison = false;
                boolean isNotNoneComparison = false;
                // Don't optimize to IsNone/IsNotNone if the rhs None must be stored to tmp for a later comparison.
                if (i == node.comparators.length - 1) {
                    boolean comparesWithNone = node.comparators[i] instanceof ExprTy.Constant constant && constant.value.kind == ConstantValue.Kind.NONE;
                    isNoneComparison = comparesWithNone && (node.ops[i] == CmpOpTy.Is);
                    isNotNoneComparison = comparesWithNone && (node.ops[i] == CmpOpTy.IsNot);
                }

                if (isNoneComparison) {
                    b.beginIsNone();
                } else if (isNotNoneComparison) {
                    b.beginIsNotNone();
                } else {
                    beginComparison(node.ops[i]);
                }

                if (i == 0) {
                    node.left.accept(this);
                } else {
                    // LHS stashed on stack from previous comparison.
                    b.emitLoadStackValue(tmp);
                }

                if (isNoneComparison) {
                    b.endIsNone();
                } else if (isNotNoneComparison) {
                    b.endIsNotNone();
                } else {
                    if (i == node.comparators.length - 1) {
                        node.comparators[i].accept(this);
                    } else {
                        // Stash RHS on stack for next comparison.
                        b.beginBlock();
                        b.beginStoreStackValue(tmp);
                        node.comparators[i].accept(this);
                        b.endStoreStackValue();
                        b.emitLoadStackValue(tmp);
                        b.endBlock();
                    }
                    endComparison(node.ops[i]);
                }

                endTraceLineChecked(node, b);
            }

            if (multipleComparisons) {
                b.endBoolAnd();
                b.endBlock();
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
                case TUPLE: {
                    ConstantCollection constantCollection = tryCollectConstantCollection(value.getTupleElements());
                    if (constantCollection != null) {
                        emitConstantTuple(constantCollection);
                    } else {
                        b.beginMakeTuple();
                        for (ConstantValue cv : value.getTupleElements()) {
                            createConstant(cv);
                        }
                        b.endMakeTuple();
                    }
                    break;
                }
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

            ConstantCollection constantCollection = tryCollectConstantCollection(node.elements);
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
            b.beginBindStackValue();
            node.value.accept(this);
            StackValue tmp = b.endBindStackValue();

            node.target.accept(new StoreVisitor(() -> {
                b.emitLoadStackValue(tmp);
            }));

            b.emitLoadStackValue(tmp);

            b.endBlock();
            endTraceLineChecked(node, b);
            endSourceSection(b, newStatement);
            return null;
        }

        private void emitConstantList(ConstantCollection constantCollection) {
            addConstant(constantCollection.collection);
            switch (constantCollection.elementType) {
                case INT:
                    b.emitMakeConstantIntList((int[]) constantCollection.collection);
                    break;
                case LONG:
                    b.emitMakeConstantLongList((long[]) constantCollection.collection);
                    break;
                case BOOLEAN:
                    b.emitMakeConstantBooleanList((boolean[]) constantCollection.collection);
                    break;
                case DOUBLE:
                    b.emitMakeConstantDoubleList((double[]) constantCollection.collection);
                    break;
                case OBJECT:
                    b.emitMakeConstantObjectList((Object[]) constantCollection.collection);
                    break;
                default:
                    throw CompilerDirectives.shouldNotReachHere();
            }
        }

        private void emitConstantTuple(ConstantCollection constantCollection) {
            addConstant(constantCollection.collection);
            switch (constantCollection.elementType) {
                case INT:
                    b.emitMakeConstantIntTuple((int[]) constantCollection.collection);
                    break;
                case LONG:
                    b.emitMakeConstantLongTuple((long[]) constantCollection.collection);
                    break;
                case BOOLEAN:
                    b.emitMakeConstantBooleanTuple((boolean[]) constantCollection.collection);
                    break;
                case DOUBLE:
                    b.emitMakeConstantDoubleTuple((double[]) constantCollection.collection);
                    break;
                case OBJECT:
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
                    visitArguments(func, args, args.length, false);
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

            b.beginMakeTuple();
            emitUnstar(node.elements);
            b.endMakeTuple();

            endTraceLineChecked(node, b);
            endSourceSection(b, newStatement);
            return null;
        }

        @Override
        public Void visit(ExprTy.UnaryOp node) {
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
            BytecodeLocal returnValue = beginTemporaryLocal();
            emitYieldFrom(generatorOrCoroutineProducer, returnValue);
            loadAndEndTemporaryLocal(returnValue);
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
             *       returnValue = e.value
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
             * end: # Step 4: returnValue local is assigned
             * @formatter:on
             */
            BytecodeLocal yieldValue = beginTemporaryLocal();
            b.beginBlock();
            BytecodeLabel end = b.createLabel();

            // @formatter:off
            b.beginBindStackValue();
                generatorOrCoroutineProducer.run();
            StackValue generator = b.endBindStackValue();

            assert yieldFromGenerator != null;
            b.beginStoreLocal(yieldFromGenerator);
                b.emitLoadStackValue(generator);
            b.endStoreLocal();

            b.beginStoreLocal(returnValue);
                b.emitLoadConstant(PNone.NONE);
            b.endStoreLocal();

            b.beginBindStackValue();
                b.emitLoadConstant(PNone.NONE);
            StackValue sentValue = b.endBindStackValue();

            // Step 1: prime the generator
            emitSend(generator, sentValue, yieldValue, returnValue, end);

            b.beginWhile();
                b.emitLoadConstant(true);
                b.beginBlock();
                    BytecodeLabel loopEnd = b.createLabel();
                    // Step 2: yield yieldValue to the caller
                    b.beginTryCatch();
                        // try clause: yield
                        b.beginStoreStackValue(sentValue);
                        emitYield((statementCompiler) -> statementCompiler.b.emitLoadLocal(yieldValue), this);
                        b.endStoreStackValue();

                        // catch clause: handle throw/close exceptions.
                        b.beginIfThenElse();
                            b.beginYieldFromThrow(yieldValue, returnValue);
                                b.emitLoadStackValue(generator);
                                b.emitLoadException();
                            b.endYieldFromThrow();

                            // Then: StopIteration was raised; go to the end.
                            b.emitBranch(end);

                            // Else: The generator yielded a value; go to top of the loop.
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
            endTemporaryLocal(yieldValue);
            b.beginStoreLocal(yieldFromGenerator);
                b.emitLoadNull();
            b.endStoreLocal();

            // @formatter:on
            b.endBlock();
        }

        private void emitSend(StackValue generator, StackValue sentValue, BytecodeLocal yieldValue, BytecodeLocal returnValue, BytecodeLabel end) {
            b.beginIfThen();
            // When the generator raises StopIteration, send evaluates to true; branch to the end.
            b.beginYieldFromSend(yieldValue, returnValue);
            b.emitLoadStackValue(generator);
            b.emitLoadStackValue(sentValue);
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
            boolean nonEmptyTuple = node.test instanceof ExprTy.Tuple tuple && tuple.elements.length > 0 ||
                            node.test instanceof ExprTy.Constant constant && constant.value.kind == Kind.TUPLE && constant.value.getTupleElements().length > 0;
            if (nonEmptyTuple) {
                warn(node, "assertion is always true, perhaps remove parentheses?");
            }
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
            b.beginMakeTuple();
            for (TypeParamTy typeParam : typeParams) {
                currentLocation = typeParam.getSourceRange();
                typeParam.accept(this);
            }
            b.endMakeTuple();
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
                addStaticAttribute(node);
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

            @Override
            public Void visit(ExprTy.Starred node) {
                throw ctx.errorCallback.onError(ErrorType.Syntax, node.getSourceRange(), "starred assignment target must be in a list or tuple");
            }

            /**
             * This method unpacks the rhs (a sequence/iterable) to the elements on the lhs
             * specified by {@code nodes}.
             */
            private void visitIterableAssign(ExprTy[] nodes) {
                b.beginBlock();

                /*
                 * The rhs should be fully evaluated and unpacked into the expected number of
                 * elements before storing values into the lhs (e.g., if an lhs element is f().attr,
                 * but computing or unpacking rhs throws, f() is not computed). Thus, the unpacking
                 * step stores the unpacked values into intermediate variables, and then those
                 * variables are copied into the lhs elements afterward.
                 *
                 * On top of that, in order to pass the target BytecodeLocal variables as
                 * LocalRangeAccessor, they must have consecutive indices.
                 */
                BytecodeLocal[] targets = new BytecodeLocal[nodes.length];
                for (int i = 0; i < targets.length; i++) {
                    targets[i] = beginTemporaryLocal();
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
                    endTemporaryLocal(targets[index]);
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

                // @formatter:off
                b.beginBindStackValue();
                    node.value.accept(StatementCompiler.this);
                StackValue target = b.endBindStackValue();

                beginSetAttribute(node.attr, b);
                    beginAugAssign();
                        beginGetAttribute(node.attr, b);
                            b.emitLoadStackValue(target);
                        b.endGetAttribute();
                        value.accept(StatementCompiler.this);
                    endAugAssign();
                    b.emitLoadStackValue(target);
                b.endSetAttribute();

                // @formatter:on
                b.endBlock();
                endSourceSection(b, newStatement);
                return null;
            }

            @Override
            public Void visit(ExprTy.Subscript node) {
                boolean newStatement = beginSourceSection(node, b);
                emitTraceLineChecked(node, b);
                b.beginBlock();
                // @formatter:off

                b.beginBindStackValue();
                    node.value.accept(StatementCompiler.this);
                StackValue target = b.endBindStackValue();

                b.beginBindStackValue();
                    node.slice.accept(StatementCompiler.this);
                StackValue slice = b.endBindStackValue();

                b.beginSetItem();
                    beginAugAssign();
                        b.beginBinarySubscript();
                            b.emitLoadStackValue(target);
                            b.emitLoadStackValue(slice);
                        b.endBinarySubscript();
                        value.accept(StatementCompiler.this);
                    endAugAssign();
                    b.emitLoadStackValue(target);
                    b.emitLoadStackValue(slice);
                b.endSetItem();

                // @formatter:on
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
                b.beginBlock();
                b.beginBindStackValue();
                value.accept(this);
                StackValue tmp = b.endBindStackValue();

                for (ExprTy target : targets) {
                    target.accept(new StoreVisitor(() -> {
                        b.emitLoadStackValue(tmp);
                    }));
                }
                b.endBlock();
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
            b.beginBlock();
            b.beginBindStackValue();
            if (iterOrNull == null) {
                b.emitLoadArgument(PArguments.USER_ARGUMENTS_OFFSET);
            } else {
                b.beginGetAIter();
                iterOrNull.accept(this);
                b.endGetAIter();
            }
            StackValue iterStackValue = b.endBindStackValue();

            BytecodeLocal result = beginTemporaryLocal();
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
                                        b.emitLoadStackValue(iterStackValue);
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
                    // TODO: GR-71890, we should clear result, or create a temporary local for each iteration
                    body.accept(this, arg);
                    if (!isComprehension) {
                        b.emitLabel(continueLabel);
                    }
                b.endBlock();
            b.endWhile();
            b.emitLabel(loopEnd);
            endTemporaryLocal(result);
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

        private void emitEmptyKeywords() {
            b.emitLoadConstant(PKeyword.EMPTY_KEYWORDS);
        }

        private void emitNonEmptyKeywords(KeywordTy[] kws, StackValue functionTempStackValue) {
            assert len(kws) > 0;
            KeywordGroup[] groups = partitionKeywords(kws);
            emitNonEmptyKeywords(groups, functionTempStackValue);
        }

        private void emitNonEmptyKeywords(KeywordGroup[] groups, StackValue functionTempStackValue) {
            assert groups.length > 0;
            // The nodes that validate keyword arguments operate on PDicts, so we convert into
            // a list of PKeywords after validation.
            b.beginMappingToKeywords();
            emitKeywordsRecursive(groups, groups.length - 1, functionTempStackValue);
            b.endMappingToKeywords();
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

        private void emitKeywordsRecursive(KeywordGroup[] groups, int i, StackValue functionTempStackValue) {
            /*
             * Keyword groups should be merged left-to-right. For example, for groups [A, B, C] we
             * should emit KwArgsMerge(KwArgsMerge(A, B), C).
             *
             * The outermost KwargsMerge clears the temporary local, because that's the last
             * KwargsMerge to be executed. The function local can be omitted only if there are no
             * keywords to be merged.
             */
            if (i == 0) {
                emitKeywordGroup(groups[i], true, functionTempStackValue);
            } else {
                assert functionTempStackValue != null;
                b.beginKwargsMerge();
                b.emitLoadStackValue(functionTempStackValue);
                emitKeywordsRecursive(groups, i - 1, functionTempStackValue);
                emitKeywordGroup(groups[i], false, functionTempStackValue);
                b.endKwargsMerge();
            }
        }

        private void emitKeywordGroup(KeywordGroup group, boolean copy, StackValue functionTempStackValue) {
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
                    b.emitLoadStackValue(functionTempStackValue);
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
            boolean newStatement = beginSourceSection(node, b);
            beginStoreLocal(node.name, b);

            if (node.decoratorList != null && node.decoratorList.length > 0) {
                // needs to emit line before return (that will also move the return)
                beginTraceLineChecked(b);
            }

            beginWrapWithDecorators(node.decoratorList);
            b.beginBlock();
            b.emitTraceLine(node.getSourceRange().startLine);
            if (node.isGeneric()) {
                RootNodeCompiler typeParamsCompiler = new RootNodeCompiler(ctx, RootNodeCompiler.this, node.name, node, node.typeParams, futureFeatures);
                RootNodeCompiler classBodyCompiler = createRootNodeCompilerFor(node, typeParamsCompiler);
                BytecodeDSLCompilerResult classBody = classBodyCompiler.compileClassDefBody(node);
                BytecodeDSLCompilerResult typeParamsFun = typeParamsCompiler.compileClassTypeParams(node, classBody.codeUnit());

                beginCallNilaryMethod();
                String typeParamsName = "<generic parameters of " + node.name + ">";
                b.beginInstrumentCall();
                b.beginInstrumentCallable();
                emitMakeFunction(typeParamsFun.codeUnit(), node.typeParams, typeParamsName, null, null);
                b.endInstrumentCallable();
                b.endInstrumentCall();
                endCallNilaryMethod();
            } else {
                BytecodeDSLCompilerResult classBody = createRootNodeCompilerFor(node).compileClassDefBody(node);
                emitBuildClass(classBody.codeUnit(), node);
            }
            b.endBlock();
            endWrapWithDecorators(node.decoratorList);

            if (node.decoratorList != null && node.decoratorList.length > 0) {
                // needs to emit line before return (that will also move the return)
                endTraceLineChecked(node, b);
            }
            // we didn't properly update lastTracedLine, force next traceline
            lastTracedLine = -1;
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

            boolean hasEmptyKeywords = len(node.keywords) == 0;

            StackValue buildClassFunction = null;
            if (!hasEmptyKeywords) {
                b.beginBindStackValue();
                // compute __build_class__ and keep it
                b.emitLoadBuildClass();
                buildClassFunction = b.endBindStackValue();
            }

            beginCallVarargsMethod();
            b.beginInstrumentCallable();
            if (hasEmptyKeywords) {
                b.emitLoadBuildClass();
            } else {
                b.emitLoadStackValue(buildClassFunction);
            }
            b.endInstrumentCallable();

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
            b.beginInstrumentCall();
            if (hasEmptyKeywords) {
                emitEmptyKeywords();
            } else {
                validateKeywords(node.keywords);
                emitNonEmptyKeywords(node.keywords, buildClassFunction);
            }
            b.endInstrumentCall();

            endCallVarargsMethod();
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

            BytecodeLocal value = beginTemporaryLocal();

            b.beginBindStackValue();
            b.beginGetIter();
            node.iter.accept(this);
            b.endGetIter();
            StackValue iter = b.endBindStackValue();

            BytecodeLabel oldBreakLabel = breakLabel;
            BytecodeLabel oldContinueLabel = continueLabel;

            BytecodeLabel currentBreakLabel = b.createLabel();
            breakLabel = currentBreakLabel;

            b.beginWhile();

            // condition
            b.beginBlock();
            b.emitTraceLineAtLoopHeader(currentLocation.startLine);
            b.beginForIterate(value);
            b.emitLoadStackValue(iter);
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

            endTemporaryLocal(value);
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
            // For instrumentation, we want to map this statement only to the declaration line, such
            // that, e.g., breakpoints inside the body fire only once the body actually executes and
            // not is declared. There is no simple way to get the exact line width here, so we just
            // approximate it with name width.
            boolean newStatement = beginSourceSection(node.getSourceRange().startLineShiftColumn(name.length()), b);
            // Note: source range of `node` excludes the source range of the decorators
            beginStoreLocal(name, b);
            if (decoratorList != null && decoratorList.length > 0) {
                // needs to emit line before return (that will also move the return)
                b.beginTraceLineWithArgument();
            }
            beginWrapWithDecorators(decoratorList);

            b.beginTraceLineWithArgument();
            boolean isGeneric = typeParams != null && typeParams.length > 0;
            if (isGeneric) {
                // The values of default positional and keyword arguments must be passed as
                // arguments to the "type parameters" code unit, because we must evaluate them
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
                if (argsCount == 0) {
                    b.beginInstrumentCall();
                }
                b.beginInstrumentCallable();
                emitMakeFunction(typeParamsFunUnit.codeUnit(), typeParams, typeParamsName, null, null);
                b.endInstrumentCallable();
                if (argsCount == 0) {
                    b.endInstrumentCall();
                }

                if (hasDefaultArgs(args)) {
                    if (!hasDefaultKwargs(args)) {
                        b.beginInstrumentCall();
                    }
                    emitDefaultArgsArray(args);
                    if (!hasDefaultKwargs(args)) {
                        b.endInstrumentCall();
                    }
                }
                if (hasDefaultKwargs(args)) {
                    b.beginInstrumentCall();
                    emitDefaultKwargsArray(args);
                    b.endInstrumentCall();
                }

                endCallNAry(argsCount);
            } else {
                BytecodeDSLCompilerResult funBodyCodeUnit = createRootNodeCompilerFor(node).compileFunctionDef(node, name, args, body);
                emitBuildFunction(funBodyCodeUnit.codeUnit(), node, name, args, decoratorList, returns);
            }
            b.endTraceLineWithArgument(node.getSourceRange().startLine);

            endWrapWithDecorators(decoratorList);
            if (decoratorList != null && decoratorList.length > 0) {
                // needs to emit line before return (that will also move the return)
                b.endTraceLineWithArgument(node.getSourceRange().startLine);
            }
            // we didn't properly update lastTracedLine, force next traceline
            lastTracedLine = -1;
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
         * Emits the "opening parentheses" of expression {@code decorator1( decoractor2( ... (
         * {value} )) ... )}.
         */
        public void beginWrapWithDecorators(ExprTy[] decorators) {
            if (decorators == null) {
                return;
            }
            for (int i = 0; i < decorators.length; i++) {
                // Attribute the eventual decorator call to the decorator expression, not the def/class line.
                beginSourceSectionInner(b, decorators[i].getSourceRange());
                beginCallUnaryMethod();
                // evaluation of the decorator expression
                b.beginTraceLineWithArgument();
                b.beginInstrumentCallable();
                decorators[i].accept(this);
                b.endInstrumentCallable();
                // trace line for the decorator expression, must be executed before the next
                // decorator expression is evaluated and before the function declaration itself
                b.endTraceLineWithArgument(decorators[i].getSourceRange().startLine);

                // trace the call to the decorator function (Python 3.12+)
                b.beginInstrumentCall();
                b.beginTraceLineWithArgument();
            }
        }

        public void endWrapWithDecorators(ExprTy[] decorators) {
            if (decorators == null) {
                return;
            }
            for (int i = 0; i < decorators.length; i++) {
                // we need to trace line in opposite direction -> decorator calls are nested and so
                // they will "flip" w.r.t. original decorator ordering, but tracings won't, so we
                // need to flip them manually
                b.endTraceLineWithArgument(decorators[decorators.length - 1 - i].getSourceRange().startLine);
                b.endInstrumentCall();
                endCallUnaryMethod();
                b.endSourceSection();
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
                    BytecodeLocal local = beginTemporaryLocal();
                    b.beginUnpackToLocals(new BytecodeLocal[]{local});
                    starred.value.accept(this);
                    b.endUnpackToLocals();
                    loadAndEndTemporaryLocal(local);
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
            int codeIndex = constants.get(codeUnit);

            b.beginMakeFunction(functionName, qualifiedName, codeIndex);

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
            if (tryVisitIsNoneCondition(node)) {
                return;
            }

            boolean mayNeedCoercion = !producesBoolean(node);
            if (mayNeedCoercion) {
                b.beginYes();
            }

            node.accept(this);

            if (mayNeedCoercion) {
                b.endYes();
            }
        }

        private boolean tryVisitIsNoneCondition(ExprTy node) {
            if (!(node instanceof ExprTy.Compare cmp) || cmp.comparators == null || cmp.comparators.length != 1 || !(cmp.comparators[0] instanceof ExprTy.Constant constant) ||
                            constant.value.kind != Kind.NONE) {
                return false;
            }

            if (cmp.ops[0] == CmpOpTy.Is) {
                b.beginIsNone();
                cmp.left.accept(this);
                b.endIsNone();
            } else if (cmp.ops[0] == CmpOpTy.IsNot) {
                b.beginIsNotNone();
                cmp.left.accept(this);
                b.endIsNotNone();
            } else {
                return false;
            }

            return true;
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

                TruffleString[] fromList = new TruffleString[node.names.length];
                for (int i = 0; i < fromList.length; i++) {
                    fromList[i] = toTruffleStringUncached(node.names[i].name);
                }

                b.beginBindStackValue();
                b.emitImport(tsModuleName, fromList, node.level);
                StackValue module = b.endBindStackValue();

                TruffleString[] importedNames = new TruffleString[node.names.length];
                for (int i = 0; i < node.names.length; i++) {
                    AliasTy alias = node.names[i];
                    addName(alias.name);
                    String asName = alias.asName == null ? alias.name : alias.asName;
                    beginStoreLocal(asName, b);

                    TruffleString name = toTruffleStringUncached(alias.name);
                    importedNames[i] = name;
                    b.beginImportFrom(name);
                    b.emitLoadStackValue(module);
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
            MatchCaseTy[] cases = node.cases;
            boolean newStatement = beginSourceSection(node, b);
            emitTraceLineChecked(node, b);

            /*
             * A match is lowered to a sequence of blocks and labels (one per case):
             * @formatter:off
             * Block(
             *   evaluate subject
             *   Block( // case 1
             *     evaluate case
             *       if any pattern/guard fails, branch to afterCase1
             *       else, evaluate body then branch to endMatch
             *   )
             *   afterCase1:
             *   Block( // case 2
             *     ...
             *   )
             *   afterCase2:
             *   ...
             *   Block( // case n
             *     ...
             *   )
             *   endMatch:
             * )
             * @formatter:on
             */
            b.beginBlock();
            BytecodeLabel[] afterCase = new BytecodeLabel[cases.length];
            for (int i = 0; i < cases.length; i++) {
                afterCase[i] = b.createLabel();
            }
            BytecodeLabel endMatch = afterCase[cases.length - 1];

            // Compute and bind the subject in a stack value.
            b.beginBindStackValue();
            node.subject.accept(this);
            StackValue subject = b.endBindStackValue();

            for (int i = 0; i < cases.length; i++) {
                emitMatchCase(cases[i], subject, afterCase[i], endMatch, i == cases.length - 1);
                b.emitLabel(afterCase[i]);
            }

            b.endBlock();
            endSourceSection(b, newStatement);
            return null;
        }

        private final class PatternContext {
            /**
             * A stack value containing the current subject of pattern matching. The value stored at
             * this location should not be overwritten; instead, a new pattern context should be
             * created.
             */
            private final StackValue subject;
            /**
             * The location in bytecode to branch to if the current pattern match fails.
             */
            private final BytecodeLabel nextCase;
            /**
             * Whether the pattern can be "irrefutable" (i.e., succeed unconditionally, like a bare
             * {@code _} or {@code x} pattern). Irrefutable patterns are only allowed in certain
             * contexts (e.g., the last case of a match).
             */
            private final boolean allowIrrefutable;
            /**
             * A mapping from bound name to the stack value reserved for its value.
             */
            private final Map<String, StackValue> bindVariables;
            /**
             * The set of names bound by the pattern. This can differ from the keyset of
             * {@link #bindVariables} in OR patterns, where stack values are eagerly reserved and
             * the names bound in each alternative must still be checked.
             */
            private final Set<String> boundNames;

            PatternContext(StackValue subject, BytecodeLabel nextCase, boolean allowIrrefutable) {
                this(subject, nextCase, allowIrrefutable, new HashMap<>(), new HashSet<>());
            }

            private PatternContext(StackValue subject, BytecodeLabel nextCase, boolean allowIrrefutable, Map<String, StackValue> bindVariables, Set<String> boundNames) {
                this.subject = subject;
                this.nextCase = nextCase;
                this.allowIrrefutable = allowIrrefutable;
                this.bindVariables = bindVariables;
                this.boundNames = boundNames;
            }

            public PatternContext forSubpattern(StackValue subpatternSubject) {
                // In a subpattern, irrefutable patterns are OK.
                return new PatternContext(subpatternSubject, nextCase, true, bindVariables, boundNames);
            }

            public PatternContext forAlternative(BytecodeLabel alternativeFailed, boolean alternativeAllowIrrefutable) {
                // When processing OR patterns, we preallocate space for the bound variables.
                // We use a fresh set of bound names to track & validate the names bound in each alternative.
                return new PatternContext(subject, alternativeFailed, alternativeAllowIrrefutable, bindVariables, new HashSet<>());
            }

            public Set<String> getBoundNames() {
                return boundNames;
            }

            private void reserveBindVariable(String name) {
                if (bindVariables.containsKey(name)) {
                    return;
                }

                b.beginBindStackValue();
                b.emitLoadNull();
                StackValue result = b.endBindStackValue();
                bindVariables.put(name, result);
            }

            private void allocateBindVariable(String name, Runnable valueProducer) {
                checkForbiddenName(name, NameOperation.BeginWrite);
                if (!boundNames.add(name)) {
                    duplicateStoreError(name);
                }
                StackValue existing = bindVariables.get(name);
                if (existing != null) {
                    b.beginStoreStackValue(existing);
                    valueProducer.run();
                    b.endStoreStackValue();
                    return;
                }

                b.beginBindStackValue();
                valueProducer.run();
                StackValue result = b.endBindStackValue();
                bindVariables.put(name, result);
            }

            private void duplicateStoreError(String name) {
                ctx.errorCallback.onError(ErrorType.Syntax, currentLocation, "multiple assignments to name '%s' in pattern", name);
            }
        }

        private void emitMatchCase(MatchCaseTy c, StackValue rootSubject, BytecodeLabel nextCase, BytecodeLabel endMatch, boolean last) {
            /*
             * We guard each case using a sequence of boolean checks to evaluate the pattern and guard.
             * If a check fails, we branch to the next case.
             *
             * Each case takes the following shape:
             * @formatter:off
             * Block(
             *   check pattern/guard, branching to nextCase on failure
             *   execute body
             *   branch/fall through to endMatch
             * )
             * @formatter:on
             */
            boolean newStatement = beginSourceSection(c, b);
            b.beginBlock();
            emitTraceLineChecked(c, b);

            if (last && wildcardCheck(c.pattern) && c.guard == null) {
                // No pattern to check. Just emit the body.
                visitStatements(c.body);
            } else {
                // The case can be irrefutable if it's last or has a guard expression.
                PatternContext pc = new PatternContext(rootSubject, nextCase, last || c.guard != null);
                if (c.pattern.getSourceRange().startLine != c.pattern.getSourceRange().endLine) {
                    // If the pattern spans multiple lines, we will create sub-blocks and be unable to bind values
                    // to this top-level block. Bind them ahead of time.
                    for (String name : collectPatternBindings(c.pattern)) {
                        pc.reserveBindVariable(name);
                    }
                }
                emitCheckPattern(c.pattern, c.guard, pc);
                visitStatements(c.body);
                if (!last) {
                    b.emitBranch(endMatch);
                }
            }

            b.endBlock();
            endSourceSection(b, newStatement);
        }

        private void emitCheckPattern(PatternTy pattern, ExprTy guard, PatternContext pc) {
            /*
             * Emits code to check a pattern and its guard:
             *
             * @formatter:off
             * if (!checkPattern) {
             *   branch nextCase
             * }
             * copy values bound by pattern into python variables
             * if (guard != null && !guard) {
             *   branch nextCase
             * }
             * @formatter:on
             *
             * Patterns can bind variables, but a variable is only bound if the full pattern
             * matches, so we accumulate the bound values into stack values and copy them all
             * over only after confirming the pattern matches.
             */
            emitCheckPattern(pattern, pc);

            if (!pc.bindVariables.isEmpty()) {
                for (Map.Entry<String, StackValue> entry : pc.bindVariables.entrySet()) {
                    beginStoreLocal(entry.getKey(), b);
                    b.emitLoadStackValue(entry.getValue());
                    endStoreLocal(entry.getKey(), b);
                }
            }
            if (guard != null) {
                emitBranchIfFalse(pc.nextCase, guard);
            }
        }

        private void emitBranchIfFalse(BytecodeLabel label, ExprTy condition) {
            b.beginIfThen();
            emitNegatedCondition(condition);
            b.emitBranch(label);
            b.endIfThen();
        }

        private void emitNegatedCondition(ExprTy condition) {
            if (condition instanceof ExprTy.UnaryOp unaryOp && unaryOp.op == UnaryOpTy.Not) {
                visitCondition(unaryOp.operand);
            } else {
                b.beginNot();
                visitCondition(condition);
                b.endNot();
            }
        }

        private void emitBranchIfFalse(BytecodeLabel label, Runnable emitCondition) {
            b.beginIfThen();
            b.beginNot();
            emitCondition.run();
            b.endNot();
            b.emitBranch(label);
            b.endIfThen();
        }

        private void emitBranchIfTrue(BytecodeLabel label, Runnable emitCondition) {
            b.beginIfThen();
            emitCondition.run();
            b.emitBranch(label);
            b.endIfThen();
        }

        /**
         * Emits code to test a {@code pattern} against the value stored in {@code subject}. The
         * generated code falls through on success and branches to the context's failure label.
         * Helpers that need a different subject should create a subpattern context and leave the
         * caller's {@code PatternContext} unchanged.
         */
        private void emitCheckPattern(PatternTy pattern, PatternContext pc) {
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

        private void doVisitPattern(PatternTy.MatchAs node, PatternContext pc) {
            if (node.name != null) {
                pc.allocateBindVariable(node.name, () -> b.emitLoadStackValue(pc.subject));
            }

            if (node.pattern == null) {
                // If there's no pattern (e.g., _), it trivially matches. Ensure this is permitted.
                if (!pc.allowIrrefutable) {
                    if (node.name != null) {
                        ctx.errorCallback.onError(ErrorType.Syntax, currentLocation, "name capture '%s' makes remaining patterns unreachable", node.name);
                    }
                    ctx.errorCallback.onError(ErrorType.Syntax, currentLocation, "wildcard makes remaining patterns unreachable");
                }
            } else {
                assert node.name != null : "name should only be null for the empty wildcard pattern '_'";
                emitCheckPattern(node.pattern, pc);
            }
        }

        /**
         * Check if attribute and keyword attribute lengths match, or if there isn't too much
         * patterns or attributes. Throws error on fail.
         *
         * @param patLen    Patterns count
         * @param attrsLen  Attributes count
         * @param kwdPatLen Keyword attributes count
         * @param node      MatchClass node for errors
         */
        private void classMatchLengthChecks(int patLen, int attrsLen, int kwdPatLen, PatternTy.MatchClass node) {
            if (attrsLen != kwdPatLen) {
                ctx.errorCallback.onError(ErrorType.Syntax, node.getSourceRange(), "kwd_attrs (%d) / kwd_patterns (%d) length mismatch in class pattern", attrsLen, kwdPatLen);
            }
            if (Integer.MAX_VALUE < (long) patLen + attrsLen - 1) {
                String id = node.cls instanceof ExprTy.Name ? ((ExprTy.Name) node.cls).id : node.cls.toString();
                ctx.errorCallback.onError(ErrorType.Syntax, node.getSourceRange(), "too many sub-patterns in class pattern %s", id);
            }

        }

        /**
         * Visits sub-patterns for class pattern matching. Regular, positional patterns are handled
         * first, then the keyword patterns (e.g. the "class.attribute = [keyword] pattern").
         *
         * @param patterns           Patterns to check as subpatterns.
         * @param kwdPatterns        Keyword patterns to check as subpatterns.
         * @param attrsValueUnpacked Values to use as subpattern subjects.
         * @param pc                 Pattern context.
         * @param patLen             Number of patterns.
         * @param attrsLen           Number of attributes (also keyword patterns).
         */
        private void classMatchVisitSubpatterns(PatternTy[] patterns, PatternTy[] kwdPatterns, StackValue attrsValueUnpacked, PatternContext pc, int patLen, int attrsLen) {
            assert patLen + attrsLen > 0;
            for (int i = 0; i < patLen; i++) {
                b.beginBindStackValue();
                b.beginArrayIndex(i);
                b.emitLoadStackValue(attrsValueUnpacked);
                b.endArrayIndex();
                StackValue subpatternSubject = b.endBindStackValue();

                emitCheckPattern(patterns[i], pc.forSubpattern(subpatternSubject));
            }

            for (int i = 0, j = patLen; i < attrsLen; i++, j++) {
                b.beginBindStackValue();
                b.beginArrayIndex(j);
                b.emitLoadStackValue(attrsValueUnpacked);
                b.endArrayIndex();
                StackValue subpatternSubject = b.endBindStackValue();

                emitCheckPattern(kwdPatterns[i], pc.forSubpattern(subpatternSubject));
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

            BytecodeLocal attrsValue = beginTemporaryLocal();
            // match class that's in the subject
            emitBranchIfFalse(pc.nextCase, () -> {
                b.beginMatchClass(attrsValue);
                    b.emitLoadStackValue(pc.subject);
                    node.cls.accept(this); // get class type
                    b.emitLoadConstant(patLen);
                    b.emitLoadConstant(tsAttrs);
                b.endMatchClass();
            });

            if (patLen + attrsLen == 0) {
                endTemporaryLocal(attrsValue);
            } else {
                // attributes from match class needs to be unpacked first
                b.beginBindStackValue();
                    b.beginUnpackSequence(patLen + attrsLen);
                        loadAndEndTemporaryLocal(attrsValue);
                    b.endUnpackSequence();
                StackValue attrsValueUnpacked = b.endBindStackValue();

                classMatchVisitSubpatterns(patterns, kwdPatterns, attrsValueUnpacked, pc, patLen, attrsLen);
            }
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
         * @param keyLen Number of keys in pattern.
         * @param pc Pattern context.
         */
        private void emitCheckPatternKeysLength(int keyLen, PatternContext pc) {
            emitBranchIfTrue(pc.nextCase, () -> {
                b.beginLt();
                b.beginGetLen();
                b.emitLoadStackValue(pc.subject);
                b.endGetLen();
                b.emitLoadConstant(keyLen);
                b.endLt();
            });
        }

        /**
         * Will process pattern keys: attribute evaluation and constant validation. Checks for
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
                    if (!(key instanceof ExprTy.Constant constant)) {
                        throw ctx.errorCallback.onError(ErrorType.Syntax, node.getSourceRange(), "mapping pattern keys may only match literals and attribute lookups");
                    }
                    Object pythonValue = PythonUtils.pythonObjectFromConstantValue(constant.value);
                    for (Object o : seen) {
                        // need python like equal - e.g. 1 equals True
                        if (PyObjectRichCompareBool.executeEqUncached(o, pythonValue)) {
                            ctx.errorCallback.onError(ErrorType.Syntax, node.getSourceRange(), "mapping pattern checks duplicate key (%s)", pythonValue);
                        }
                    }
                    seen.add(pythonValue);
                    createConstant(constant.value);
                }
            }
            b.endCollectToObjectArray();
        }

        /**
         * Visit all sub-patterns for mapping in pattern (not subject).
         *
         * @param patterns Sub-patterns to iterate through.
         * @param values Patterns from subject to set as subject for evaluated sub-patterns.
         * @param pc Pattern context.
         */
        private void mappingVisitSubpatterns(PatternTy[] patterns, BytecodeLocal values, PatternContext pc) {
            int patLen = patterns.length;

            // unpack values from pc.subject
            b.beginBindStackValue();
            b.beginUnpackSequence(patLen);
            b.emitLoadLocal(values);
            b.endUnpackSequence();
            StackValue valuesUnpacked = b.endBindStackValue();

            for (int i = 0; i < patLen; i++) {
                if (wildcardCheck(patterns[i])) {
                    continue;
                }
                b.beginBindStackValue();
                b.beginArrayIndex(i);
                b.emitLoadStackValue(valuesUnpacked);
                b.endArrayIndex();
                StackValue subpatternSubject = b.endBindStackValue();

                emitCheckPattern(patterns[i], pc.forSubpattern(subpatternSubject));
            }
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
            String starTarget = node.rest;

            int keyLen = lengthOrZero(keys);
            int patLen = lengthOrZero(patterns);

            if (keyLen != patLen) {
                ctx.errorCallback.onError(ErrorType.Syntax, node.getSourceRange(), "keys (%d) / patterns (%d) length mismatch in mapping pattern", keyLen, patLen);
            }
            // @formatter:off

            // check that type matches
            emitBranchIfFalse(pc.nextCase, () -> {
                b.beginCheckTypeFlags(TypeFlags.MAPPING);
                    b.emitLoadStackValue(pc.subject);
                b.endCheckTypeFlags();
            });

            if (keyLen == 0 && starTarget == null) {
                return;
            }
            // If the pattern has any keys in it, perform a length check:
            if (keyLen > 0) {
                emitCheckPatternKeysLength(keyLen, pc);
            }

            BytecodeLocal subjectPatterns = beginTemporaryLocal();

            b.beginBindStackValue();
                processPatternKeys(keys, keyLen, node);
            StackValue keysChecked = b.endBindStackValue();

            // save match result together with values
            emitBranchIfFalse(pc.nextCase, () -> {
                b.beginMatchKeys(subjectPatterns);
                    b.emitLoadStackValue(pc.subject);
                    b.emitLoadStackValue(keysChecked);
                b.endMatchKeys();
            });

            if (patLen > 0) {
                mappingVisitSubpatterns(patterns, subjectPatterns, pc);
            }

            endTemporaryLocal(subjectPatterns);

            if (starTarget != null) {
                pc.allocateBindVariable(starTarget, () -> {
                    b.beginCopyDictWithoutKeys();
                        b.emitLoadStackValue(pc.subject);
                        b.emitLoadStackValue(keysChecked);
                    b.endCopyDictWithoutKeys();
                });
            }

            // @formatter:on
        }

        private Set<String> collectPatternBindings(PatternTy pattern) {
            return new PatternBindingVisitor().collect(pattern);
        }

        private final class PatternBindingVisitor implements BaseBytecodeDSLVisitor<Void> {
            private final Set<String> names = new HashSet<>();

            Set<String> collect(PatternTy pattern) {
                pattern.accept(this);
                return Set.copyOf(names);
            }

            @Override
            public Void visit(PatternTy.MatchAs node) {
                if (node.name != null) {
                    names.add(node.name);
                }
                if (node.pattern != null) {
                    node.pattern.accept(this);
                }
                return null;
            }

            @Override
            public Void visit(PatternTy.MatchClass node) {
                visitPatterns(node.patterns);
                visitPatterns(node.kwdPatterns);
                return null;
            }

            @Override
            public Void visit(PatternTy.MatchMapping node) {
                visitPatterns(node.patterns);
                if (node.rest != null) {
                    names.add(node.rest);
                }
                return null;
            }

            @Override
            public Void visit(PatternTy.MatchOr node) {
                names.addAll(collectPatternBindings(node.patterns[0]));
                return null;
            }

            @Override
            public Void visit(PatternTy.MatchSequence node) {
                visitPatterns(node.patterns);
                return null;
            }

            @Override
            public Void visit(PatternTy.MatchSingleton node) {
                return null;
            }

            @Override
            public Void visit(PatternTy.MatchStar node) {
                if (node.name != null) {
                    names.add(node.name);
                }
                return null;
            }

            @Override
            public Void visit(PatternTy.MatchValue node) {
                return null;
            }

            private void visitPatterns(PatternTy[] patterns) {
                if (patterns != null) {
                    for (PatternTy pattern : patterns) {
                        pattern.accept(this);
                    }
                }
            }
        }

        private void checkAlternativePatternDifferentNames(Set<String> control, Set<String> names) {
            if (!control.equals(names)) {
                ctx.errorCallback.onError(ErrorType.Syntax, currentLocation, "alternative patterns bind different names");
            }
        }

        private void doVisitPattern(PatternTy.MatchOr node, PatternContext pc) {
            PatternTy[] patterns = node.patterns;
            Set<String> control = collectPatternBindings(patterns[0]);
            // Reserve bind variables before evaluating each alternative.
            // Each alternative will write its values into the bind variables.
            for (String name : control) {
                pc.reserveBindVariable(name);
            }

            b.beginBlock();
            BytecodeLabel success = b.createLabel();
            for (int i = 0; i < patterns.length; i++) {
                BytecodeLabel alternativeFailed = i == patterns.length - 1 ? pc.nextCase : b.createLabel();
                b.beginBlock();
                PatternContext alternative = pc.forAlternative(alternativeFailed, i == patterns.length - 1 && pc.allowIrrefutable);

                emitCheckPattern(patterns[i], alternative);
                checkAlternativePatternDifferentNames(control, alternative.getBoundNames());
                b.emitBranch(success);
                b.endBlock();

                if (i != patterns.length - 1) {
                    b.emitLabel(alternativeFailed);
                }
            }

            b.emitLabel(success);
            b.endBlock();
        }

        private void patternHelperSequenceUnpack(PatternTy[] patterns, PatternContext pc) {
            int n = len(patterns);

            b.beginBindStackValue();
            patternUnpackHelper(patterns, pc);
            StackValue unpacked = b.endBindStackValue();

            for (int i = 0; i < n; i++) {
                b.beginBindStackValue();
                b.beginArrayIndex(i);
                b.emitLoadStackValue(unpacked);
                b.endArrayIndex();
                StackValue subpatternSubject = b.endBindStackValue();

                emitCheckPattern(patterns[i], pc.forSubpattern(subpatternSubject));
            }
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
                    b.emitLoadStackValue(pc.subject);
                    b.endUnpackEx();
                    // Continue in the loop to ensure there are no additional starred patterns.
                }
            }
            // If there were no star patterns, emit UnpackSequence.
            if (!seenStar) {
                b.beginUnpackSequence(n);
                b.emitLoadStackValue(pc.subject);
                b.endUnpackSequence();
            }
        }

        /**
         * Like patternHelperSequenceUnpack, but uses subscripting, which is (likely) more efficient
         * for patterns with a starred wildcard like [first, *_], [first, *_, last], [*_, last],
         * etc.
         */
        private void patternHelperSequenceSubscr(PatternTy[] patterns, int star, PatternContext pc) {
            assert star >= 0;
            int n = len(patterns);

            int lastItem = star == n - 1 ? n - 2 : n - 1;
            for (int i = 0; i < n; i++) {
                PatternTy pattern = patterns[i];
                if (i == star) {
                    // nothing to check
                    assert wildcardStarCheck(pattern);
                    continue;
                }

                assert !wildcardStarCheck(pattern);
                b.beginBindStackValue();
                b.beginBinarySubscript();
                if (i < star) {
                    assert i != n - 1;
                    b.emitLoadStackValue(pc.subject);
                    b.emitLoadConstant(i);
                } else {
                    b.emitLoadStackValue(pc.subject);
                    // The subject may not support negative indexing! Compute a
                    // nonnegative index:
                    b.beginPyNumberSubtract();

                    b.beginGetLen();
                    b.emitLoadStackValue(pc.subject);
                    b.endGetLen();

                    b.emitLoadConstant(n - i);

                    b.endPyNumberSubtract();
                }
                b.endBinarySubscript();
                StackValue subpatternSubject = b.endBindStackValue();

                emitCheckPattern(pattern, pc.forSubpattern(subpatternSubject));
            }
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

            emitBranchIfFalse(pc.nextCase, () -> {
                b.beginCheckTypeFlags(TypeFlags.SEQUENCE);
                b.emitLoadStackValue(pc.subject);
                b.endCheckTypeFlags();
            });

            if (star < 0) {
                // No star: len(subject) == size
                emitBranchIfTrue(pc.nextCase, () -> {
                    b.beginNe();
                    b.beginGetLen();
                    b.emitLoadStackValue(pc.subject);
                    b.endGetLen();
                    b.emitLoadConstant(size);
                    b.endNe();
                });
            } else if (size > 1) {
                // Star: len(subject) >= size - 1
                emitBranchIfTrue(pc.nextCase, () -> {
                    b.beginLt();
                    b.beginGetLen();
                    b.emitLoadStackValue(pc.subject);
                    b.endGetLen();
                    b.emitLoadConstant(size - 1);
                    b.endLt();
                });
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

        }

        private void doVisitPattern(PatternTy.MatchSingleton node, PatternContext pc) {
            switch (node.value.kind) {
                case BOOLEAN:
                    emitBranchIfFalse(pc.nextCase, () -> {
                        b.beginIs();
                        b.emitLoadStackValue(pc.subject);
                        b.emitLoadConstant(node.value.getBoolean());
                        b.endIs();
                    });
                    break;
                case NONE:
                    emitBranchIfTrue(pc.nextCase, () -> {
                        b.beginIsNotNone();
                        b.emitLoadStackValue(pc.subject);
                        b.endIsNotNone();
                    });
                    break;
                default:
                    throw new IllegalStateException("wrong MatchSingleton value kind " + node.value.kind);
            }
        }

        private void doVisitPattern(PatternTy.MatchStar node, PatternContext pc) {
            if (node.name != null) {
                pc.allocateBindVariable(node.name, () -> b.emitLoadStackValue(pc.subject));
            }
            // If there's no name, no need to check anything.
        }

        private void doVisitPattern(PatternTy.MatchValue node, PatternContext pc) {
            emitBranchIfFalse(pc.nextCase, () -> {
                b.beginEq();
                b.emitLoadStackValue(pc.subject);
                if (node.value instanceof ExprTy.Constant || node.value instanceof ExprTy.Attribute) {
                    node.value.accept(this);
                } else {
                    ctx.errorCallback.onError(ErrorType.Syntax, currentLocation, "patterns may only match literals and attribute lookups");
                }
                b.endEq();
            });
        }

        private static boolean wildcardCheck(PatternTy pattern) {
            return pattern instanceof PatternTy.MatchAs && ((PatternTy.MatchAs) pattern).name == null;
        }

        private static boolean wildcardStarCheck(PatternTy pattern) {
            return pattern instanceof PatternTy.MatchStar && ((PatternTy.MatchStar) pattern).name == null;
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
                    int saveLastTracedLine = lastTracedLine;
                    lastTracedLine = -1;
                    b.beginBlock(); // finally
                        visitSequence(node.finalBody);
                    b.endBlock();
                    lastTracedLine = saveLastTracedLine;
                });

                    emitTryExceptElse(node); // try-except-else

                    b.beginBlock(); // catch uncaught exceptions
                        BytecodeLocal savedException = beginTemporaryLocal();
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
                        endTemporaryLocal(savedException);
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
                        BytecodeLocal savedException = beginTemporaryLocal();
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
                        endTemporaryLocal(savedException);
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
                    BytecodeLocal savedException = beginTemporaryLocal();
                    BytecodeLocal prevPrevEx = enterSaveExceptionBlock(savedException);

                    emitSaveCurrentException(savedException);
                    emitSetCurrentException();

                    b.beginBindStackValue();
                        b.emitGetCaughtException();
                    StackValue exceptionOrig = b.endBindStackValue();
                    // Mark this location for the stack trace.
                    b.beginMarkExceptionAsCaught();
                        b.emitLoadException(); // ex
                    b.endMarkExceptionAsCaught();

                    b.beginTryCatchOtherwise(() -> emitRestoreCurrentException(savedException));
                        b.beginBlock(); // try (all handlers)
                            BytecodeLocal matchedExceptions = beginTemporaryLocal();
                            BytecodeLocal unmatchedExceptions = beginTemporaryLocal();
                            b.beginStoreLocal(unmatchedExceptions);
                                b.emitLoadException();
                            b.endStoreLocal();

                            b.beginBindStackValue();
                                b.emitLoadConstant(PNone.NONE);
                            StackValue exceptionAcc = b.endBindStackValue();

                            for (ExceptHandlerTy h : node.handlers) {
                                boolean newStatement = beginSourceSection(h, b);
                                emitTraceLineChecked(h, b);

                                ExceptHandlerTy.ExceptHandler handler = (ExceptHandlerTy.ExceptHandler) h;
                                if (handler.type == null) {
                                    ctx.errorCallback.onError(ErrorType.Syntax, currentLocation, "cannot have bare 'except' in 'try' containing 'except*' clauses.");
                                }

                                b.beginBlock(); // handler
                                b.beginBindStackValue();
                                    handler.type.accept(this);
                                StackValue handlerType = b.endBindStackValue();

                                b.beginIfThen();
                                    b.beginSplitExceptionGroups(matchedExceptions, unmatchedExceptions);
                                        b.emitLoadLocal(unmatchedExceptions); // ex
                                        b.emitLoadStackValue(handlerType);
                                        b.emitLoadStackValue(exceptionOrig);
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
                                                        b.emitLoadStackValue(exceptionOrig);
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
                                                            b.emitLoadStackValue(exceptionOrig);
                                                        b.endIsExceptionGroup();

                                                        b.beginBlock(); // then (explicit raises and reraises)
                                                            b.beginStoreStackValue(exceptionAcc);
                                                                b.beginHandleExceptionsInHandler();
                                                                    b.emitLoadException(); // handler_i_ex (exception thrown in this handler)
                                                                    b.emitLoadStackValue(exceptionAcc);
                                                                    b.emitLoadStackValue(exceptionOrig);
                                                                    b.emitLoadStackValue(handlerType);
                                                                b.endHandleExceptionsInHandler();
                                                            b.endStoreStackValue();
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
                                                                    b.beginStoreStackValue(exceptionAcc);
                                                                        b.beginHandleExceptionsInHandler();
                                                                            b.emitLoadException();
                                                                            b.emitLoadStackValue(exceptionAcc);
                                                                            b.emitLoadStackValue(exceptionOrig);
                                                                            b.emitLoadConstant(PNone.NONE);
                                                                        b.endHandleExceptionsInHandler();
                                                                    b.endStoreStackValue();
                                                                b.endBlock();
                                                            b.endTryCatch();

                                                            b.beginSetCurrentException();
                                                                b.emitLoadStackValue(exceptionOrig);
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
                                                            b.emitLoadStackValue(exceptionOrig);
                                                        b.endSetCurrentException();
                                                    b.endBlock();

                                                    b.beginBlock(); // catch (exception thrown in bare handler)
                                                        b.beginStoreStackValue(exceptionAcc);
                                                            b.beginHandleExceptionsInHandler();
                                                                b.emitLoadException(); // handler_i_ex (exception thrown in bare handler)
                                                                b.emitLoadStackValue(exceptionAcc);
                                                                b.emitLoadStackValue(exceptionOrig);
                                                                b.emitLoadStackValue(handlerType);
                                                            b.endHandleExceptionsInHandler();
                                                        b.endStoreStackValue();
                                                    b.endBlock();
                                                b.endTryCatch();
                                            b.endBlock();
                                        }
                                    b.endBlock(); // handler body
                                b.endIfThen();

                                b.endBlock(); // handler
                                endSourceSection(b, newStatement);
                            } // end handler loop

                            b.beginBlock(); // bundle up unmatched exceptions into exceptionAcc and throw them
                                b.beginStoreStackValue(exceptionAcc);
                                    b.beginHandleExceptionsInHandler();
                                        b.emitLoadLocal(unmatchedExceptions);
                                        b.emitLoadStackValue(exceptionAcc);
                                        b.emitLoadStackValue(exceptionOrig);
                                        b.emitLoadConstant(PNone.NONE);
                                    b.endHandleExceptionsInHandler();
                                b.endStoreStackValue();
                            b.endBlock();

                            b.beginIfThen();
                                b.beginIsNotNone();
                                    b.emitLoadStackValue(exceptionAcc);
                                b.endIsNotNone();
                                b.beginReraise();
                                    // exceptionAcc is a PBaseExceptionGroup and
                                    // needs to be converted into PException
                                    b.beginEncapsulateExceptionGroup();
                                        b.emitLoadStackValue(exceptionAcc);
                                        b.emitLoadStackValue(exceptionOrig);
                                    b.endEncapsulateExceptionGroup();
                                b.endReraise();
                            b.endIfThen();

                            endTemporaryLocal(unmatchedExceptions);
                            endTemporaryLocal(matchedExceptions);
                        b.endBlock(); // try (all handlers)

                        b.beginBlock(); // catch (final, all-encompassing exception group)
                            emitRestoreCurrentException(savedException);

                            b.beginReraise();
                                b.emitLoadException(); // handler_ex (final, all-encompassing exception group)
                            b.endReraise();
                        b.endBlock(); // catch (final, all-encompassing exception group)
                    b.endTryCatchOtherwise();

                    exitSaveExceptionBlock(prevPrevEx);
                    endTemporaryLocal(savedException);

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
                    int saveLastTracedLine = lastTracedLine;
                    lastTracedLine = -1;
                    b.beginBlock(); // finally
                        visitSequence(node.finalBody);
                    b.endBlock();
                    lastTracedLine = saveLastTracedLine;
                });

                    emitTryExceptElse(node); // try-except-else

                    b.beginBlock(); // catch uncaught exceptions
                        BytecodeLocal savedException = beginTemporaryLocal();
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
                        endTemporaryLocal(savedException);
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

            BytecodeLocal exit = beginTemporaryLocal();
            BytecodeLocal value = beginTemporaryLocal();
            b.beginBindStackValue();
            item.contextExpr.accept(this);
            StackValue contextManager = b.endBindStackValue();

            if (async) {
                // call __aenter__
                b.beginAsyncContextManagerEnter(exit, value);
                b.emitLoadStackValue(contextManager);
                b.endAsyncContextManagerEnter();
                // await the result
                b.beginStoreLocal(value);
                emitAwait(() -> b.emitLoadLocal(value));
                b.endStoreLocal();
            } else {
                // call __enter__
                b.beginContextManagerEnter(exit, value);
                b.emitLoadStackValue(contextManager);
                b.endContextManagerEnter();
            }

            Runnable finallyHandler;
            if (async) {
                finallyHandler = () -> emitAwait(() -> {
                    b.beginBlock();
                    b.emitTraceLine(items[index].getSourceRange().startLine);
                    b.beginAsyncContextManagerCallExit();
                    b.emitLoadConstant(PNone.NONE);
                    b.emitLoadLocal(exit);
                    b.emitLoadStackValue(contextManager);
                    b.endAsyncContextManagerCallExit();
                    b.endBlock();
                });
            } else {
                finallyHandler = () -> {
                    // call __exit__
                    b.emitTraceLine(items[index].getSourceRange().startLine);
                    b.beginContextManagerExit();
                    b.emitLoadConstant(PNone.NONE);
                    b.emitLoadLocal(exit);
                    b.emitLoadStackValue(contextManager);
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
                BytecodeLocal savedException = beginTemporaryLocal();
                BytecodeLocal prevPrevSaved = enterSaveExceptionBlock(savedException);
                emitSaveCurrentException(savedException);
                emitSetCurrentException();

                b.beginBlock();
                b.emitTraceLine(items[index].getSourceRange().startLine);
                // @formatter:off
                b.beginAsyncContextManagerExit();
                    b.emitLoadException();
                    b.beginBlock();
                        b.beginBindStackValue();
                        emitAwait(() -> {
                            b.beginAsyncContextManagerCallExit();
                            b.emitLoadException();
                            b.emitLoadLocal(exit);
                            b.emitLoadStackValue(contextManager);
                            b.endAsyncContextManagerCallExit();
                        });
                        StackValue tmp = b.endBindStackValue();
                        // restore the exception just before invoking the AsyncContextManagerExit operation
                        emitRestoreCurrentException(savedException);
                        b.emitLoadStackValue(tmp);
                    b.endBlock();
                b.endAsyncContextManagerExit();
                b.endBlock();
                // @formatter:on

                exitSaveExceptionBlock(prevPrevSaved);
                endTemporaryLocal(savedException);
            } else {
                // call __exit__
                b.emitTraceLine(items[index].getSourceRange().startLine);
                b.beginContextManagerExit();
                b.emitLoadException();
                b.emitLoadLocal(exit);
                b.emitLoadStackValue(contextManager);
                b.endContextManagerExit();
            }
            b.endBlock(); // catch

            b.endTryCatchOtherwise();

            endTemporaryLocal(value);
            endTemporaryLocal(exit);
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
                beginCallNilaryMethod();
                b.beginInstrumentCall();
                b.beginInstrumentCallable();
                emitMakeFunction(typeParamsFun.codeUnit(), node.typeParams, typeParamsName, null, null);
                b.endInstrumentCallable();
                b.endInstrumentCall();
                endCallNilaryMethod();
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

            b.beginBindStackValue();
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
            StackValue typeParam = b.endBindStackValue();

            beginStoreLocal(node.name, b);
            b.emitLoadStackValue(typeParam);
            endStoreLocal(node.name, b);

            // Keep the newly created parameter as the result. Reading the variable again could
            // resolve to the enclosing class namespace when this scope can see it.
            b.emitLoadStackValue(typeParam);

            b.endBlock();
            return null;
        }

        @Override
        public Void visit(ParamSpec node) {
            b.beginBlock();

            b.beginBindStackValue();
            // @formatter:off
            b.beginMakeTypeParam(MakeTypeParamKind.PARAM_SPEC);
                emitPythonConstant(toTruffleStringUncached(node.name), b);
                b.emitLoadNull();
            b.endMakeTypeParam();
            StackValue typeParam = b.endBindStackValue();

            beginStoreLocal(node.name, b);
                b.emitLoadStackValue(typeParam);
            endStoreLocal(node.name, b);
            // @formatter:on

            b.emitLoadStackValue(typeParam);

            b.endBlock();
            return null;
        }

        @Override
        public Void visit(TypeVarTuple node) {
            b.beginBlock();

            b.beginBindStackValue();
            // @formatter:off
            b.beginMakeTypeParam(MakeTypeParamKind.TYPE_VAR_TUPLE);
                emitPythonConstant(toTruffleStringUncached(node.name), b);
                b.emitLoadNull(); // boundOrConstraints
            b.endMakeTypeParam();
            StackValue typeParam = b.endBindStackValue();

            beginStoreLocal(node.name, b);
                b.emitLoadStackValue(typeParam);
            endStoreLocal(node.name, b);
            // formatter:@on

            b.emitLoadStackValue(typeParam);

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
