/*
 * Copyright (c) 2017, 2023, Oracle and/or its affiliates.
 * Copyright (c) 2015, Regents of the University of California
 *
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification, are
 * permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this list of
 * conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice, this list of
 * conditions and the following disclaimer in the documentation and/or other materials provided
 * with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS
 * OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
 * MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE
 * COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE
 * GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED
 * AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED
 * OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.oracle.graal.python;

import static com.oracle.graal.python.builtins.PythonOS.PLATFORM_WIN32;
import static com.oracle.graal.python.builtins.PythonOS.getPythonOS;
import static com.oracle.graal.python.nodes.StringLiterals.J_PY_EXTENSION;
import static com.oracle.graal.python.nodes.StringLiterals.T_PY_EXTENSION;
import static com.oracle.graal.python.nodes.truffle.TruffleStringMigrationHelpers.isJavaString;
import static com.oracle.graal.python.util.PythonUtils.TS_ENCODING;
import static com.oracle.graal.python.util.PythonUtils.tsLiteral;

import java.io.IOException;
import java.nio.file.InvalidPathException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;
import java.util.logging.Level;

import org.graalvm.home.Version;
import org.graalvm.nativeimage.ImageInfo;
import org.graalvm.options.OptionDescriptors;
import org.graalvm.options.OptionKey;
import org.graalvm.options.OptionValues;

import com.oracle.graal.python.builtins.Python3Core;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.modules.MarshalModuleBuiltins;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.PNotImplemented;
import com.oracle.graal.python.builtins.objects.PythonAbstractObject;
import com.oracle.graal.python.builtins.objects.ellipsis.PEllipsis;
import com.oracle.graal.python.builtins.objects.frame.PFrame;
import com.oracle.graal.python.builtins.objects.function.BuiltinMethodDescriptor;
import com.oracle.graal.python.builtins.objects.function.PArguments;
import com.oracle.graal.python.builtins.objects.object.PythonObject;
import com.oracle.graal.python.builtins.objects.type.MroShape;
import com.oracle.graal.python.builtins.objects.type.PythonAbstractClass;
import com.oracle.graal.python.builtins.objects.type.PythonManagedClass;
import com.oracle.graal.python.builtins.objects.type.TypeBuiltins;
import com.oracle.graal.python.compiler.CodeUnit;
import com.oracle.graal.python.compiler.CompilationUnit;
import com.oracle.graal.python.compiler.Compiler;
import com.oracle.graal.python.compiler.RaisePythonExceptionErrorCallback;
import com.oracle.graal.python.nodes.HiddenAttributes;
import com.oracle.graal.python.nodes.bytecode.PBytecodeRootNode;
import com.oracle.graal.python.nodes.call.CallNode;
import com.oracle.graal.python.nodes.call.GenericInvokeNode;
import com.oracle.graal.python.nodes.exception.TopLevelExceptionHandler;
import com.oracle.graal.python.nodes.frame.GetFrameLocalsNode;
import com.oracle.graal.python.nodes.frame.MaterializeFrameNode;
import com.oracle.graal.python.pegparser.FutureFeature;
import com.oracle.graal.python.pegparser.InputType;
import com.oracle.graal.python.pegparser.NodeFactory;
import com.oracle.graal.python.pegparser.Parser;
import com.oracle.graal.python.pegparser.sst.ArgTy;
import com.oracle.graal.python.pegparser.sst.ArgumentsTy;
import com.oracle.graal.python.pegparser.sst.ModTy;
import com.oracle.graal.python.pegparser.sst.StmtTy;
import com.oracle.graal.python.pegparser.tokenizer.SourceRange;
import com.oracle.graal.python.runtime.GilNode;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.graal.python.runtime.PythonContext.PythonThreadState;
import com.oracle.graal.python.runtime.PythonOptions;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.graal.python.runtime.object.PythonObjectFactory;
import com.oracle.graal.python.util.Function;
import com.oracle.graal.python.util.PythonUtils;
import com.oracle.graal.python.util.Supplier;
import com.oracle.truffle.api.Assumption;
import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.ContextThreadLocal;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.TruffleFile;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.TruffleLogger;
import com.oracle.truffle.api.debug.DebuggerTags;
import com.oracle.truffle.api.dsl.Idempotent;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.instrumentation.ProvidedTags;
import com.oracle.truffle.api.instrumentation.StandardTags;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.api.nodes.DirectCallNode;
import com.oracle.truffle.api.nodes.ExecutableNode;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.nodes.ExplodeLoop.LoopExplosionKind;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.object.HiddenKey;
import com.oracle.truffle.api.object.Shape;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.api.source.Source.SourceBuilder;
import com.oracle.truffle.api.source.SourceSection;
import com.oracle.truffle.api.strings.TruffleString;

@TruffleLanguage.Registration(id = PythonLanguage.ID, //
                name = PythonLanguage.NAME, //
                implementationName = PythonLanguage.IMPLEMENTATION_NAME, //
                version = PythonLanguage.VERSION, //
                characterMimeTypes = {PythonLanguage.MIME_TYPE,
                                "text/x-python-\0\u0000-eval", "text/x-python-\0\u0000-compile", "text/x-python-\1\u0000-eval", "text/x-python-\1\u0000-compile", "text/x-python-\2\u0000-eval",
                                "text/x-python-\2\u0000-compile", "text/x-python-\0\u0100-eval", "text/x-python-\0\u0100-compile", "text/x-python-\1\u0100-eval", "text/x-python-\1\u0100-compile",
                                "text/x-python-\2\u0100-eval", "text/x-python-\2\u0100-compile", "text/x-python-\0\u0040-eval", "text/x-python-\0\u0040-compile", "text/x-python-\1\u0040-eval",
                                "text/x-python-\1\u0040-compile", "text/x-python-\2\u0040-eval", "text/x-python-\2\u0040-compile", "text/x-python-\0\u0140-eval", "text/x-python-\0\u0140-compile",
                                "text/x-python-\1\u0140-eval", "text/x-python-\1\u0140-compile", "text/x-python-\2\u0140-eval", "text/x-python-\2\u0140-compile"}, //
                byteMimeTypes = {PythonLanguage.MIME_TYPE_BYTECODE}, //
                defaultMimeType = PythonLanguage.MIME_TYPE, //
                dependentLanguages = {"nfi", "llvm"}, //
                interactive = true, internal = false, //
                contextPolicy = TruffleLanguage.ContextPolicy.SHARED, //
                fileTypeDetectors = PythonFileDetector.class, //
                internalResources = PythonResource.class, //
                website = "https://www.graalvm.org/python/")
@ProvidedTags({
                StandardTags.CallTag.class,
                StandardTags.StatementTag.class,
                StandardTags.RootTag.class,
                StandardTags.RootBodyTag.class,
                StandardTags.TryBlockTag.class,
                StandardTags.ExpressionTag.class,
                StandardTags.ReadVariableTag.class,
                StandardTags.WriteVariableTag.class,
                DebuggerTags.AlwaysHalt.class
})
public final class PythonLanguage extends TruffleLanguage<PythonContext> {
    public static final String J_GRAALPYTHON_ID = "graalpy";
    public static final TruffleString T_GRAALPYTHON_ID = tsLiteral(J_GRAALPYTHON_ID);
    public static final String ID = "python";
    public static final String NAME = "Python";
    public static final String IMPLEMENTATION_NAME = "GraalPy";
    public static final int MAJOR = 3;
    public static final int MINOR = 10;
    public static final int MICRO = 8;
    public static final int RELEASE_LEVEL_ALPHA = 0xA;
    public static final int RELEASE_LEVEL_BETA = 0xB;
    public static final int RELEASE_LEVEL_GAMMA = 0xC;
    public static final int RELEASE_LEVEL_FINAL = 0xF;
    public static final int RELEASE_LEVEL = RELEASE_LEVEL_ALPHA;
    public static final TruffleString RELEASE_LEVEL_STRING;
    public static final String FROZEN_FILENAME_PREFIX = "<frozen ";
    public static final String FROZEN_FILENAME_SUFFIX = ">";

    /**
     * GraalVM version. Unfortunately, we cannot just use {@link Version#getCurrent} as it relies on
     * a GraalVM build, but we may run from Jar files directly during development. So we hardcode
     * the version here. It will fail at runtime if it doesn't match the distribution layout that is
     * derived from the actual version.
     */
    public static final int GRAALVM_MAJOR = 23;
    public static final int GRAALVM_MINOR = 1;

    static {
        switch (RELEASE_LEVEL) {
            case RELEASE_LEVEL_ALPHA:
                RELEASE_LEVEL_STRING = tsLiteral("alpha");
                break;
            case RELEASE_LEVEL_BETA:
                RELEASE_LEVEL_STRING = tsLiteral("beta");
                break;
            case RELEASE_LEVEL_GAMMA:
                RELEASE_LEVEL_STRING = tsLiteral("rc");
                break;
            case RELEASE_LEVEL_FINAL:
            default:
                RELEASE_LEVEL_STRING = tsLiteral("final");
        }
    }
    public static final int RELEASE_SERIAL = 0;
    public static final int VERSION_HEX = MAJOR << 24 |
                    MINOR << 16 |
                    MICRO << 8 |
                    RELEASE_LEVEL_ALPHA << 4 |
                    RELEASE_SERIAL;
    public static final String VERSION = MAJOR + "." + MINOR + "." + MICRO;
    // Rarely updated version of the C API, we should take it from the imported CPython version
    public static final int API_VERSION = 1013;

    public static final String MIME_TYPE = "text/x-python";

    // the syntax for mime types is as follows
    // <mime> ::= "text/x-python-" <optlevel> <flags> "-" kind
    // <kind> ::= "compile" | "eval"
    // <optlevel> ::= "\0" | "\1" | "\2"
    // <flags> ::= "\u0040" | "\u0100" | "\u0140" | "\u0000"
    // where 0100 implies annotations, and 0040 implies barry_as_flufl
    static final String MIME_PREFIX = MIME_TYPE + "-";
    static final int OPT_FLAGS_LEN = 2; // 1 char is optlevel, 1 char is flags
    static final String MIME_KIND_COMPILE = "compile";
    static final String MIME_KIND_EVAL = "eval";
    // Since flags are greater than the highest unicode codepoint, we shift them into more
    // reasonable values in the mime type. 4 hex digits
    static final int MIME_FLAG_SHIFTBY = 4 * 4;
    // a dash follows after the opt flag pair
    static final int MIME_KIND_START = MIME_PREFIX.length() + OPT_FLAGS_LEN + 1;

    private static boolean mimeTypesComplete(ArrayList<String> mimeJavaStrings) {
        ArrayList<String> mimeTypes = new ArrayList<>();
        FutureFeature[] all = FutureFeature.values();
        for (int flagset = 0; flagset < (1 << all.length); ++flagset) {
            int flags = 0;
            for (int i = 0; i < all.length; ++i) {
                if ((flagset & (1 << i)) != 0) {
                    flags |= all[i].flagValue;
                }
            }
            for (int opt = 0; opt <= 2; opt++) {
                for (String typ : new String[]{MIME_KIND_EVAL, MIME_KIND_COMPILE}) {
                    mimeTypes.add(MIME_PREFIX + optFlagsToMime(opt, flags) + "-" + typ);
                    mimeJavaStrings.add(String.format("\"%s\\%d\\u%04x-%s\"", MIME_PREFIX, opt, flags >> MIME_FLAG_SHIFTBY, typ));
                }
            }
        }
        HashSet<String> currentMimeTypes = new HashSet<>(List.of(PythonLanguage.class.getAnnotation(Registration.class).characterMimeTypes()));
        return currentMimeTypes.containsAll(mimeTypes);
    }

    static {
        ArrayList<String> mimeJavaStrings = new ArrayList<>();
        assert mimeTypesComplete(mimeJavaStrings) : "Expected all of {" + String.join(", ", mimeJavaStrings) + "} in the PythonLanguage characterMimeTypes";
    }

    public static final String MIME_TYPE_BYTECODE = "application/x-python-bytecode";

    public static final TruffleString[] T_DEFAULT_PYTHON_EXTENSIONS = new TruffleString[]{T_PY_EXTENSION, tsLiteral(".pyc")};

    private static final TruffleLogger LOGGER = TruffleLogger.getLogger(ID, PythonLanguage.class);

    private static final LanguageReference<PythonLanguage> REFERENCE = LanguageReference.create(PythonLanguage.class);

    /**
     * This assumption will be valid if no context set a trace or profile function at any point.
     * Calling sys.settrace(None) or sys.setprofile(None) will not invalidate it
     */
    public final Assumption noTracingOrProfilingAssumption = Assumption.create("No tracing function was set");

    @CompilationFinal private boolean singleContext = true;

    @Idempotent
    public boolean isSingleContext() {
        return singleContext;
    }

    /**
     * This assumption will be valid if all contexts are single-threaded. Hence, it will be
     * invalidated as soon as at least one context has been initialized for multi-threading.
     */
    public final Assumption singleThreadedAssumption = Truffle.getRuntime().createAssumption("Only a single thread is active");

    /**
     * A thread-safe map to retrieve (and cache) singleton instances of call targets, e.g., for
     * Arithmetic operations, wrappers, named cext functions, etc. This reduces the number of call
     * targets and allows AST sharing across contexts. The key in this map is either a single value
     * or a list of values.
     */
    private final ConcurrentHashMap<Object, RootCallTarget> cachedCallTargets = new ConcurrentHashMap<>();

    /**
     * A map to retrieve call targets of special slot methods for a given BuiltinMethodDescriptor.
     * Used to perform uncached calls to slots. The call targets are not directly part of
     * descriptors because that would make them specific to a language instance. We want to have
     * them global in order to be able to efficiently compare them in guards.
     */
    private final ConcurrentHashMap<BuiltinMethodDescriptor, RootCallTarget> descriptorCallTargets = new ConcurrentHashMap<>();

    private final Shape emptyShape = Shape.newBuilder().allowImplicitCastIntToDouble(false).allowImplicitCastIntToLong(true).shapeFlags(0).propertyAssumptions(true).build();
    @CompilationFinal(dimensions = 1) private final Shape[] builtinTypeInstanceShapes = new Shape[PythonBuiltinClassType.VALUES.length];

    @CompilationFinal(dimensions = 1) private static final Object[] CONTEXT_INSENSITIVE_SINGLETONS = new Object[]{PNone.NONE, PNone.NO_VALUE, PEllipsis.INSTANCE, PNotImplemented.NOT_IMPLEMENTED};

    /**
     * Named semaphores are shared between all processes in a system, and they persist until the
     * system is shut down, unless explicitly removed. We interpret this as meaning they all exist
     * globally per language instance, that is, they are shared between different Contexts in the
     * same engine.
     *
     * Top level contexts use this map to initialize their shared multiprocessing data. Inner
     * children contexts created for the multiprocessing module ignore this map in
     * {@link PythonLanguage} and instead inherit it in the shared multiprocessing data from their
     * parent context. This way, the child inner contexts do not have to run in the same engine
     * (have the same language instance), but can still share the named semaphores.
     */
    public final ConcurrentHashMap<TruffleString, Semaphore> namedSemaphores = new ConcurrentHashMap<>();

    @CompilationFinal(dimensions = 1) private volatile Object[] engineOptionsStorage;
    @CompilationFinal private volatile OptionValues engineOptions;

    /** A shared shape for the C symbol cache (lazily initialized). */
    private Shape cApiSymbolCache;
    private Shape hpySymbolCache;

    /** For fast access to the PythonThreadState object by the owning thread. */
    private final ContextThreadLocal<PythonThreadState> threadState = locals.createContextThreadLocal(PythonContext.PythonThreadState::new);

    public final ConcurrentHashMap<String, HiddenKey> typeHiddenKeys = new ConcurrentHashMap<>(TypeBuiltins.INITIAL_HIDDEN_TYPE_KEYS);

    private final MroShape mroShapeRoot = MroShape.createRoot();

    public static PythonLanguage get(Node node) {
        return REFERENCE.get(node);
    }

    public static int getNumberOfSpecialSingletons() {
        return CONTEXT_INSENSITIVE_SINGLETONS.length;
    }

    @ExplodeLoop(kind = LoopExplosionKind.FULL_UNROLL_UNTIL_RETURN)
    public static int getSingletonNativeWrapperIdx(Object obj) {
        for (int i = 0; i < CONTEXT_INSENSITIVE_SINGLETONS.length; i++) {
            if (CONTEXT_INSENSITIVE_SINGLETONS[i] == obj) {
                return i;
            }
        }
        return -1;
    }

    /**
     * <b>DO NOT DIRECTLY USE THIS METHOD !!!</b> Instead, use
     * {@link PythonContext#getThreadState(PythonLanguage)}}.
     */
    public ContextThreadLocal<PythonThreadState> getThreadStateLocal() {
        return threadState;
    }

    public MroShape getMroShapeRoot() {
        return mroShapeRoot;
    }

    @Override
    protected void finalizeContext(PythonContext context) {
        context.finalizeContext();
        super.finalizeContext(context);
    }

    @Override
    protected boolean areOptionsCompatible(OptionValues firstOptions, OptionValues newOptions) {
        return PythonOptions.areOptionsCompatible(firstOptions, newOptions);
    }

    @Override
    protected boolean patchContext(PythonContext context, Env newEnv) {
        if (!areOptionsCompatible(context.getEnv().getOptions(), newEnv.getOptions())) {
            Python3Core.writeInfo("Cannot use preinitialized context.");
            return false;
        }
        context.initializeHomeAndPrefixPaths(newEnv, getLanguageHome());
        Python3Core.writeInfo("Using preinitialized context.");
        context.patch(newEnv);
        return true;
    }

    @Override
    protected PythonContext createContext(Env env) {
        final PythonContext context = new PythonContext(this, env);
        context.initializeHomeAndPrefixPaths(env, getLanguageHome());

        Object[] engineOptionsUnroll = this.engineOptionsStorage;
        if (engineOptionsUnroll == null) {
            this.engineOptionsStorage = PythonOptions.createEngineOptionValuesStorage(env);
        } else {
            assert Arrays.equals(engineOptionsUnroll, PythonOptions.createEngineOptionValuesStorage(env)) : "invalid engine options";
        }

        OptionValues options = this.engineOptions;
        if (options == null) {
            this.engineOptions = PythonOptions.createEngineOptions(env);
        } else {
            assert areOptionsCompatible(options, PythonOptions.createEngineOptions(env)) : "invalid engine options";
        }

        return context;
    }

    @Idempotent
    public <T> T getEngineOption(OptionKey<T> key) {
        assert engineOptions != null;
        if (CompilerDirectives.inInterpreter()) {
            return engineOptions.get(key);
        } else {
            return PythonOptions.getOptionUnrolling(this.engineOptionsStorage, PythonOptions.getEngineOptionKeys(), key);
        }
    }

    @Override
    protected OptionDescriptors getOptionDescriptors() {
        return PythonOptions.DESCRIPTORS;
    }

    @Override
    protected void initializeContext(PythonContext context) {
        context.initialize();
    }

    private static String optFlagsToMime(int optimize, int flags) {
        if (optimize < 0) {
            optimize = 0;
        } else if (optimize > 2) {
            optimize = 2;
        }
        String optField = new String(new byte[]{(byte) optimize});
        String flagField = new String(new int[]{(flags & FutureFeature.ALL_FLAGS) >> MIME_FLAG_SHIFTBY}, 0, 1);
        assert flagField.length() == 1 : "flags in mime type ended up a surrogate";
        return optField + flagField;
    }

    public static String getCompileMimeType(int optimize, int flags) {
        String optFlags = optFlagsToMime(optimize, flags);
        return MIME_PREFIX + optFlags + "-compile";
    }

    public static String getEvalMimeType(int optimize, int flags) {
        String optFlags = optFlagsToMime(optimize, flags);
        return MIME_PREFIX + optFlags + "-eval";
    }

    @Override
    protected CallTarget parse(ParsingRequest request) {
        PythonContext context = PythonContext.get(null);
        Source source = request.getSource();
        if (source.getMimeType() == null || MIME_TYPE.equals(source.getMimeType())) {
            if (!request.getArgumentNames().isEmpty() && source.isInteractive()) {
                throw new IllegalStateException("parse with arguments not allowed for interactive sources");
            }
            InputType inputType = source.isInteractive() ? InputType.SINGLE : InputType.FILE;
            return parse(context, source, inputType, true, 0, source.isInteractive(), request.getArgumentNames(), EnumSet.noneOf(FutureFeature.class));
        }
        if (!request.getArgumentNames().isEmpty()) {
            throw new IllegalStateException("parse with arguments is only allowed for " + MIME_TYPE + " mime type");
        }
        if (MIME_TYPE_BYTECODE.equals(source.getMimeType())) {
            byte[] bytes = source.getBytes().toByteArray();
            CodeUnit code = MarshalModuleBuiltins.deserializeCodeUnit(bytes);
            boolean internal = shouldMarkSourceInternal(context);
            // The original file path should be passed as the name
            String name = source.getName();
            if (name != null && !name.isEmpty()) {
                Source textSource = tryLoadSource(context, code, internal, name);
                if (textSource == null) {
                    if (name.startsWith(FROZEN_FILENAME_PREFIX) && name.endsWith(FROZEN_FILENAME_SUFFIX)) {
                        String id = name.substring(FROZEN_FILENAME_PREFIX.length(), name.length() - FROZEN_FILENAME_SUFFIX.length());
                        String fs = context.getEnv().getFileNameSeparator();
                        String path = context.getStdlibHome() + fs + id.replace(".", fs) + J_PY_EXTENSION;
                        textSource = tryLoadSource(context, code, internal, path);
                        if (textSource == null) {
                            path = context.getStdlibHome() + fs + id.replace(".", fs) + fs + "__init__.py";
                            textSource = tryLoadSource(context, code, internal, path);
                        }
                    }
                }
                if (textSource != null) {
                    source = textSource;
                }
            }
            if (internal && !source.isInternal()) {
                source = Source.newBuilder(source).internal(true).build();
            }
            PBytecodeRootNode rootNode = PBytecodeRootNode.create(this, code, source);
            return PythonUtils.getOrCreateCallTarget(rootNode);
        }

        String mime = source.getMimeType();
        String prefix = mime.substring(0, MIME_PREFIX.length());
        if (!prefix.equals(MIME_PREFIX)) {
            throw CompilerDirectives.shouldNotReachHere("unknown mime type: " + mime);
        }
        String kind = mime.substring(MIME_KIND_START);
        InputType type;
        if (kind.equals(MIME_KIND_COMPILE)) {
            type = InputType.FILE;
        } else if (kind.equals(MIME_KIND_EVAL)) {
            type = InputType.EVAL;
        } else {
            throw CompilerDirectives.shouldNotReachHere("unknown compilation kind: " + kind + " from mime type: " + mime);
        }
        int optimize = mime.codePointAt(MIME_PREFIX.length());
        int flags = mime.codePointAt(MIME_PREFIX.length() + 1) << MIME_FLAG_SHIFTBY;
        if (0 > optimize || optimize > 2 || (flags & ~FutureFeature.ALL_FLAGS) != 0) {
            throw CompilerDirectives.shouldNotReachHere("Invalid value for optlevel or flags: " + optimize + "," + flags + " from mime type: " + mime);
        }
        assert !source.isInteractive();
        return parse(context, source, type, false, optimize, false, null, FutureFeature.fromFlags(flags));
    }

    private static Source tryLoadSource(PythonContext context, CodeUnit code, boolean internal, String path) {
        try {
            return Source.newBuilder(PythonLanguage.ID, context.getEnv().getPublicTruffleFile(path)).name(code.name.toJavaStringUncached()).internal(internal).build();
        } catch (IOException | SecurityException | UnsupportedOperationException | InvalidPathException e) {
            return null;
        }
    }

    public RootCallTarget parse(PythonContext context, Source source, InputType type, boolean topLevel, int optimize, boolean interactiveTerminal, List<String> argumentNames,
                    EnumSet<FutureFeature> futureFeatures) {
        RaisePythonExceptionErrorCallback errorCb = new RaisePythonExceptionErrorCallback(source, PythonOptions.isPExceptionWithJavaStacktrace(this));
        try {
            Parser parser = Compiler.createParser(source.getCharacters().toString(), errorCb, type, interactiveTerminal);
            ModTy mod = (ModTy) parser.parse();
            assert mod != null;
            return compileForBytecodeInterpreter(context, mod, source, topLevel, optimize, argumentNames, errorCb, futureFeatures);
        } catch (PException e) {
            if (topLevel) {
                PythonUtils.getOrCreateCallTarget(new TopLevelExceptionHandler(this, e)).call();
            }
            throw e;
        }
    }

    @TruffleBoundary
    public RootCallTarget compileForBytecodeInterpreter(PythonContext context, ModTy mod, Source source, boolean topLevel, int optimize, List<String> argumentNames,
                    RaisePythonExceptionErrorCallback errorCallback, int flags) {
        return compileForBytecodeInterpreter(context, mod, source, topLevel, optimize, argumentNames, errorCallback, FutureFeature.fromFlags(flags));
    }

    @TruffleBoundary
    public RootCallTarget compileForBytecodeInterpreter(PythonContext context, ModTy modIn, Source source, boolean topLevel, int optimize, List<String> argumentNames,
                    RaisePythonExceptionErrorCallback errorCallback, EnumSet<FutureFeature> futureFeatures) {
        RaisePythonExceptionErrorCallback errorCb = errorCallback;
        if (errorCb == null) {
            errorCb = new RaisePythonExceptionErrorCallback(source, PythonOptions.isPExceptionWithJavaStacktrace(this));
        }
        try {
            Compiler compiler = new Compiler(errorCb);
            boolean hasArguments = argumentNames != null && !argumentNames.isEmpty();
            final ModTy mod;
            if (hasArguments) {
                mod = transformASTForExecutionWithArguments(argumentNames, modIn);
            } else {
                mod = modIn;
            }
            CompilationUnit cu = compiler.compile(mod, EnumSet.noneOf(Compiler.Flags.class), optimize, futureFeatures);
            CodeUnit co = cu.assemble();
            RootNode rootNode = PBytecodeRootNode.create(this, co, source, errorCb);
            if (topLevel) {
                GilNode gil = GilNode.getUncached();
                boolean wasAcquired = gil.acquire(context, rootNode);
                try {
                    errorCb.triggerDeprecationWarnings();
                } finally {
                    gil.release(context, wasAcquired);
                }
            }
            if (hasArguments) {
                rootNode = new RootNodeWithArguments(this, rootNode);
            }
            if (topLevel && context.isCoreInitialized()) {
                rootNode = new TopLevelExceptionHandler(this, rootNode, source);
            }
            return PythonUtils.getOrCreateCallTarget(rootNode);
        } catch (PException e) {
            if (topLevel) {
                PythonUtils.getOrCreateCallTarget(new TopLevelExceptionHandler(this, e)).call();
            }
            throw e;
        }
    }

    private static ModTy transformASTForExecutionWithArguments(List<String> argumentNames, ModTy mod) {
        NodeFactory nodeFactory = new NodeFactory();
        ArgTy[] astArgArray = new ArgTy[argumentNames.size()];
        for (int i = 0; i < astArgArray.length; i++) {
            astArgArray[i] = nodeFactory.createArgument(argumentNames.get(i), null, null, SourceRange.ARTIFICIAL_RANGE);
        }
        ArgumentsTy astArgs = nodeFactory.createArguments(null, null, astArgArray, null, null);
        StmtTy[] body = ((ModTy.Module) mod).body;
        if (body != null && body.length > 0) {
            body = Arrays.copyOf(body, body.length);
            StmtTy lastStmt = body[body.length - 1];
            if (lastStmt instanceof StmtTy.Expr) {
                body[body.length - 1] = nodeFactory.createReturn(((StmtTy.Expr) lastStmt).value, lastStmt.getSourceRange());
            }
        }
        String fnName = "execute";
        StmtTy astFunction = nodeFactory.createFunctionDef(fnName, astArgs, body, null, null, SourceRange.ARTIFICIAL_RANGE);
        /*
         * We cannot use a return in a module, but we piggy-back on the fact that we return the last
         * expression in a module (see Compiler)
         */
        StmtTy astGetFunction = nodeFactory.createExpression(nodeFactory.createVariable(fnName, SourceRange.ARTIFICIAL_RANGE), SourceRange.ARTIFICIAL_RANGE);
        return nodeFactory.createModule(new StmtTy[]{astFunction, astGetFunction}, ((ModTy.Module) mod).typeIgnores, SourceRange.ARTIFICIAL_RANGE);
    }

    private static class RootNodeWithArguments extends RootNode {
        private final RootNode innerRootNode;
        @Child private DirectCallNode callModuleNode;
        @Child private CallNode callFunctionNode;

        public RootNodeWithArguments(PythonLanguage language, RootNode innerRootNode) {
            super(language);
            this.innerRootNode = innerRootNode;
            callModuleNode = DirectCallNode.create(innerRootNode.getCallTarget());
            callFunctionNode = CallNode.create();
        }

        @Override
        public Object execute(VirtualFrame frame) {
            Object function = callModuleNode.call(frame.getArguments());
            Object[] arguments = PythonUtils.arrayCopyOfRange(frame.getArguments(), PArguments.USER_ARGUMENTS_OFFSET, frame.getArguments().length);
            return callFunctionNode.execute(frame, function, arguments);
        }

        @Override
        public SourceSection getSourceSection() {
            return innerRootNode.getSourceSection();
        }

        @Override
        public String getName() {
            return innerRootNode.getName();
        }
    }

    @Override
    public ExecutableNode parse(InlineParsingRequest request) {
        PythonContext context = PythonContext.get(null);
        RootCallTarget callTarget = parse(context, request.getSource(), InputType.EVAL, false, 0, false, null, EnumSet.noneOf(FutureFeature.class));
        return new ExecutableNode(this) {
            @Child private GilNode gilNode = GilNode.create();
            @Child private GenericInvokeNode invokeNode = GenericInvokeNode.create();
            @Child private MaterializeFrameNode materializeFrameNode = MaterializeFrameNode.create();
            @Child private GetFrameLocalsNode getFrameLocalsNode = GetFrameLocalsNode.create();

            @Override
            public Object execute(VirtualFrame frame) {
                Object[] arguments = PArguments.create();
                // escape?
                PFrame pFrame = materializeFrameNode.execute(this, false, true, frame);
                Object pLocals = getFrameLocalsNode.execute(pFrame);
                PArguments.setSpecialArgument(arguments, pLocals);
                PArguments.setGlobals(arguments, PArguments.getGlobals(frame));
                boolean wasAcquired = gilNode.acquire();
                try {
                    return invokeNode.execute(callTarget, arguments);
                } finally {
                    gilNode.release(wasAcquired);
                }
            }
        };
    }

    @Override
    protected Object getLanguageView(PythonContext context, Object value) {
        assert !(value instanceof PythonAbstractObject);
        PythonObjectFactory factory = PythonObjectFactory.getUncached();
        InteropLibrary interopLib = InteropLibrary.getFactory().getUncached(value);
        try {
            if (interopLib.isBoolean(value)) {
                if (interopLib.asBoolean(value)) {
                    return context.getTrue();
                } else {
                    return context.getFalse();
                }
            } else if (interopLib.isString(value)) {
                return factory.createString(interopLib.asTruffleString(value).switchEncodingUncached(TS_ENCODING));
            } else if (value instanceof Byte || value instanceof Short || value instanceof Integer || value instanceof Long) {
                // TODO: (tfel) once the interop protocol allows us to
                // distinguish fixed point from floating point reliably, we can
                // remove this branch
                return factory.createInt(interopLib.asLong(value));
            } else if (value instanceof Float || value instanceof Double) {
                // TODO: (tfel) once the interop protocol allows us to
                // distinguish fixed point from floating point reliably, we can
                // remove this branch
                return factory.createFloat(interopLib.asDouble(value));
            } else if (interopLib.fitsInLong(value)) {
                return factory.createInt(interopLib.asLong(value));
            } else if (interopLib.fitsInDouble(value)) {
                return factory.createFloat(interopLib.asDouble(value));
            } else {
                return new ForeignLanguageView(value);
            }
        } catch (UnsupportedMessageException e) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            throw new IllegalStateException(e);
        }
    }

    @ExportLibrary(value = InteropLibrary.class, delegateTo = "delegate")
    static class ForeignLanguageView implements TruffleObject {
        final Object delegate;

        ForeignLanguageView(Object delegate) {
            this.delegate = delegate;
        }

        @ExportMessage
        @TruffleBoundary
        String toDisplayString(boolean allowSideEffects,
                        @CachedLibrary("this.delegate") InteropLibrary lib) {
            return "<foreign '" + lib.toDisplayString(delegate, allowSideEffects) + "'>";
        }

        @ExportMessage
        @SuppressWarnings("static-method")
        boolean hasLanguage() {
            return true;
        }

        @ExportMessage
        Class<? extends TruffleLanguage<?>> getLanguage() {
            return PythonLanguage.class;
        }
    }

    public String getHome() {
        return getLanguageHome();
    }

    /**
     * If this object can be cached in the AST.
     */
    @Idempotent
    public static boolean canCache(Object value) {
        CompilerAsserts.neverPartOfCompilation();
        // We cache strings with at most 16 characters. This corresponds to byte length of 64 for
        // TruffleStrings since we are using UTF-32 encoding. Note that if the string contains only
        // ASCII characters, which is the usual case, the actual amount of memory taken by the
        // string is just 16 bytes - TruffleString#byteLength() reports the length as if the string
        // was not compacted.
        return value instanceof Long ||
                        value instanceof Integer ||
                        value instanceof Boolean ||
                        value instanceof Double ||
                        (isJavaString(value) && ((String) value).length() <= 16) ||
                        (value instanceof TruffleString && ((TruffleString) value).byteLength(TS_ENCODING) <= 64) ||
                        isContextInsensitiveSingleton(value);
    }

    private static boolean isContextInsensitiveSingleton(Object value) {
        for (Object singleton : CONTEXT_INSENSITIVE_SINGLETONS) {
            if (value == singleton) {
                return true;
            }
        }
        return false;
    }

    @Override
    protected boolean isVisible(PythonContext context, Object value) {
        return value != PNone.NONE && value != PNone.NO_VALUE;
    }

    @Override
    protected Object getScope(PythonContext context) {
        return context.getTopScopeObject();
    }

    @TruffleBoundary
    public static TruffleLogger getLogger(Class<?> clazz) {
        return TruffleLogger.getLogger(ID, clazz);
    }

    @TruffleBoundary
    public static TruffleLogger getLogger(String name) {
        return TruffleLogger.getLogger(ID, name);
    }

    /**
     * Loggers that should report any known incompatibility with CPython, which is silently ignored
     * in order to be able to continue the execution. Example is setting the stack size limit: it
     * would be too drastic measure to raise error, because the program may continue and work
     * correctly even if it is ignored.
     *
     * The logger name is prefixed with "compatibility" such that
     * {@code --log.python.compatibility.level=LEVEL} can turn on compatibility related logging for
     * all classes.
     */
    @TruffleBoundary
    public static TruffleLogger getCompatibilityLogger(Class<?> clazz) {
        return TruffleLogger.getLogger(ID, "compatibility." + clazz.getName());
    }

    public static Source newSource(PythonContext ctxt, TruffleString tsrc, TruffleString name, boolean mayBeFile, String mime) {
        try {
            SourceBuilder sourceBuilder = null;
            String src = tsrc.toJavaStringUncached();
            if (mayBeFile) {
                try {
                    TruffleFile truffleFile = ctxt.getPublicTruffleFileRelaxed(name, PythonLanguage.T_DEFAULT_PYTHON_EXTENSIONS);
                    if (truffleFile.exists()) {
                        // XXX: (tfel): We don't know if the expression has anything to do with the
                        // filename that's given. We would really have to compare the entire
                        // contents, but as a first approximation, we compare the content lengths.
                        // We override the contents of the source builder with the given source
                        // regardless.
                        if (src.length() == truffleFile.size() || src.getBytes().length == truffleFile.size()) {
                            sourceBuilder = Source.newBuilder(ID, truffleFile);
                            sourceBuilder.content(src);
                        }
                    }
                } catch (SecurityException | IOException | InvalidPathException e) {
                    sourceBuilder = null;
                }
            }
            if (sourceBuilder == null) {
                sourceBuilder = Source.newBuilder(ID, src, name.toJavaStringUncached());
            }
            if (mime != null) {
                sourceBuilder.mimeType(mime);
            }
            return newSource(ctxt, sourceBuilder);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    public static Source newSource(PythonContext ctxt, TruffleFile src, String name) throws IOException {
        return newSource(ctxt, Source.newBuilder(ID, src).name(name));
    }

    private static Source newSource(PythonContext context, SourceBuilder srcBuilder) throws IOException {
        if (getPythonOS() == PLATFORM_WIN32 && ImageInfo.inImageBuildtimeCode()) {
            // canonicalization on windows means something else than on linux and causes issues
            // with paths
            srcBuilder.canonicalizePath(false);
        }
        if (shouldMarkSourceInternal(context)) {
            srcBuilder.internal(true);
        }
        return srcBuilder.build();
    }

    private static boolean shouldMarkSourceInternal(PythonContext ctxt) {
        return !ctxt.isCoreInitialized() && !ctxt.getLanguage().getEngineOption(PythonOptions.ExposeInternalSources);
    }

    @Override
    protected void initializeMultipleContexts() {
        super.initializeMultipleContexts();
        singleContext = false;
    }

    private final ConcurrentHashMap<TruffleString, CallTarget> cachedCode = new ConcurrentHashMap<>();

    @TruffleBoundary
    public CallTarget cacheCode(TruffleString filename, Supplier<CallTarget> createCode) {
        if (!singleContext) {
            return cachedCode.computeIfAbsent(filename, f -> {
                LOGGER.log(Level.FINEST, () -> "Caching CallTarget for " + filename);
                return createCode.get();
            });
        } else {
            return createCode.get();
        }
    }

    @Override
    protected boolean isThreadAccessAllowed(Thread thread, boolean singleThreaded) {
        if (singleThreaded) {
            return super.isThreadAccessAllowed(thread, singleThreaded);
        }
        return true;
    }

    @Override
    protected void initializeMultiThreading(PythonContext context) {
        if (singleThreadedAssumption.isValid()) {
            singleThreadedAssumption.invalidate();
            context.initializeMultiThreading();
        }
    }

    @Override
    protected void initializeThread(PythonContext context, Thread thread) {
        context.attachThread(thread, threadState);
    }

    @Override
    protected void disposeThread(PythonContext context, Thread thread) {
        context.disposeThread(thread);
    }

    public Shape getEmptyShape() {
        return emptyShape;
    }

    public Shape getShapeForClass(PythonAbstractClass klass) {
        if (isSingleContext()) {
            return Shape.newBuilder(getEmptyShape()).addConstantProperty(HiddenAttributes.CLASS, klass, 0).build();
        } else {
            return getEmptyShape();
        }
    }

    public static Shape getShapeForClassWithoutDict(PythonManagedClass klass) {
        return Shape.newBuilder(klass.getInstanceShape()).shapeFlags(PythonObject.HAS_SLOTS_BUT_NO_DICT_FLAG).build();
    }

    public Shape getBuiltinTypeInstanceShape(PythonBuiltinClassType type) {
        int ordinal = type.ordinal();
        Shape shape = builtinTypeInstanceShapes[ordinal];
        if (shape == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            Shape.DerivedBuilder shapeBuilder = Shape.newBuilder(getEmptyShape()).addConstantProperty(HiddenAttributes.CLASS, type, 0);
            if (!type.isBuiltinWithDict()) {
                shapeBuilder.shapeFlags(PythonObject.HAS_SLOTS_BUT_NO_DICT_FLAG);
            }
            shape = shapeBuilder.build();
            builtinTypeInstanceShapes[ordinal] = shape;
        }
        return shape;
    }

    /**
     * Returns the shape used for the C API symbol cache.
     */
    @TruffleBoundary
    public synchronized Shape getCApiSymbolCacheShape() {
        if (cApiSymbolCache == null) {
            cApiSymbolCache = Shape.newBuilder().build();
        }
        return cApiSymbolCache;
    }

    /**
     * Returns the shape used for the HPy API symbol cache.
     */
    @TruffleBoundary
    public synchronized Shape getHPySymbolCacheShape() {
        if (hpySymbolCache == null) {
            hpySymbolCache = Shape.newBuilder().build();
        }
        return hpySymbolCache;
    }

    /**
     * Cache call targets that are created for every new context, based on a single key.
     */
    public RootCallTarget createCachedCallTarget(Function<PythonLanguage, RootNode> rootNodeFunction, Object key) {
        CompilerAsserts.neverPartOfCompilation();
        if (!singleContext) {
            return cachedCallTargets.computeIfAbsent(key, k -> PythonUtils.getOrCreateCallTarget(rootNodeFunction.apply(this)));
        } else {
            return PythonUtils.getOrCreateCallTarget(rootNodeFunction.apply(this));
        }
    }

    /**
     * Cache call targets that are created for every new context, based on a list of keys.
     */
    public RootCallTarget createCachedCallTarget(Function<PythonLanguage, RootNode> rootNodeFunction, Object... cacheKeys) {
        return createCachedCallTarget(rootNodeFunction, Arrays.asList(cacheKeys));
    }

    public void registerBuiltinDescriptorCallTarget(BuiltinMethodDescriptor descriptor, RootCallTarget callTarget) {
        descriptorCallTargets.put(descriptor, callTarget);
    }

    /**
     * Gets a {@link CallTarget} for given {@link BuiltinMethodDescriptor}. The
     * {@link BuiltinMethodDescriptor} must have been inserted into the slots of some Python class
     * in the current context, otherwise its {@link CallTarget} will not be cached here.
     */
    @TruffleBoundary
    public RootCallTarget getDescriptorCallTarget(BuiltinMethodDescriptor descriptor) {
        RootCallTarget callTarget = descriptorCallTargets.get(descriptor);
        assert callTarget != null : "Missing call target for builtin slot descriptor " + descriptor;
        return callTarget;
    }

    @Override
    protected void exitContext(PythonContext context, ExitMode exitMode, int exitCode) {
        if (context.getCApiContext() != null) {
            context.getCApiContext().finalizeCapi();
        }
    }
}
