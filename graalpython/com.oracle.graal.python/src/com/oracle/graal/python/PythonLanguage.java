/*
 * Copyright (c) 2017, 2020, Oracle and/or its affiliates.
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

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;
import java.util.logging.Level;

import org.graalvm.options.OptionDescriptors;
import org.graalvm.options.OptionKey;
import org.graalvm.options.OptionValues;

import com.oracle.graal.python.builtins.Builtin;
import com.oracle.graal.python.builtins.Python3Core;
import com.oracle.graal.python.builtins.objects.PEllipsis;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.PNotImplemented;
import com.oracle.graal.python.builtins.objects.PythonAbstractObject;
import com.oracle.graal.python.builtins.objects.dict.PDict;
import com.oracle.graal.python.builtins.objects.function.PArguments;
import com.oracle.graal.python.builtins.objects.object.PythonObject;
import com.oracle.graal.python.nodes.BuiltinNames;
import com.oracle.graal.python.nodes.NodeFactory;
import com.oracle.graal.python.nodes.call.InvokeNode;
import com.oracle.graal.python.nodes.control.TopLevelExceptionHandler;
import com.oracle.graal.python.nodes.expression.ExpressionNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.parser.PythonParserImpl;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.graal.python.runtime.PythonCore;
import com.oracle.graal.python.runtime.PythonOptions;
import com.oracle.graal.python.runtime.PythonParser.ParserMode;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.graal.python.runtime.interop.InteropMap;
import com.oracle.graal.python.runtime.object.PythonObjectFactory;
import com.oracle.graal.python.util.Function;
import com.oracle.graal.python.util.PFunctionArgsFinder;
import com.oracle.graal.python.util.PythonUtils;
import com.oracle.graal.python.util.Supplier;
import com.oracle.truffle.api.Assumption;
import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.Scope;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.TruffleFile;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.TruffleLogger;
import com.oracle.truffle.api.debug.DebuggerTags;
import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.frame.MaterializedFrame;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.instrumentation.ProvidedTags;
import com.oracle.truffle.api.instrumentation.StandardTags;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.api.nodes.ExecutableNode;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.nodes.ExplodeLoop.LoopExplosionKind;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.api.source.Source.SourceBuilder;

@TruffleLanguage.Registration(id = PythonLanguage.ID, //
                name = PythonLanguage.NAME, //
                version = PythonLanguage.VERSION, //
                characterMimeTypes = PythonLanguage.MIME_TYPE, //
                dependentLanguages = "llvm", //
                interactive = true, internal = false, //
                contextPolicy = TruffleLanguage.ContextPolicy.SHARED, //
                fileTypeDetectors = PythonFileDetector.class)
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
    public static final String ID = "python";
    public static final String NAME = "Python";
    public static final int MAJOR = 3;
    public static final int MINOR = 8;
    public static final int MICRO = 2;
    public static final String VERSION = MAJOR + "." + MINOR + "." + MICRO;

    public static final String MIME_TYPE = "text/x-python";
    public static final String EXTENSION = ".py";
    public static final String[] DEFAULT_PYTHON_EXTENSIONS = new String[]{EXTENSION, ".pyc"};

    private static final TruffleLogger LOGGER = TruffleLogger.getLogger(ID, PythonLanguage.class);

    public final Assumption singleContextAssumption = Truffle.getRuntime().createAssumption("Only a single context is active");

    private final NodeFactory nodeFactory;
    private final ConcurrentHashMap<String, RootCallTarget> builtinCallTargetCache = new ConcurrentHashMap<>();

    @CompilationFinal(dimensions = 1) private static final Object[] CONTEXT_INSENSITIVE_SINGLETONS = new Object[]{PNone.NONE, PNone.NO_VALUE, PEllipsis.INSTANCE, PNotImplemented.NOT_IMPLEMENTED};

    /**
     * Named semaphores are shared between all processes in a system, and they persist until the
     * system is shut down, unless explicitly removed. We interpret this as meaning they all exist
     * globally per language instance, that is, they are shared between different Contexts in the
     * same engine.
     */
    public final ConcurrentHashMap<String, Semaphore> namedSemaphores = new ConcurrentHashMap<>();

    /*
     * We need to store this here, because the check is on the language and can come from a thread
     * that has no context, but we enable or disable threads with a context option. So we store this
     * here when a context is created.
     */
    private Boolean isWithThread = null;

    @CompilationFinal(dimensions = 1) private volatile Object[] engineOptionsStorage;
    @CompilationFinal private volatile OptionValues engineOptions;

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

    public PythonLanguage() {
        this.nodeFactory = NodeFactory.create(this);
    }

    public NodeFactory getNodeFactory() {
        return nodeFactory;
    }

    @Override
    protected void finalizeContext(PythonContext context) {
        context.shutdownThreads();
        context.runShutdownHooks();
        super.finalizeContext(context);
    }

    @Override
    protected boolean areOptionsCompatible(OptionValues firstOptions, OptionValues newOptions) {
        return PythonOptions.areOptionsCompatible(firstOptions, newOptions);
    }

    @Override
    protected boolean patchContext(PythonContext context, Env newEnv) {
        if (!areOptionsCompatible(context.getEnv().getOptions(), newEnv.getOptions())) {
            PythonCore.writeInfo("Cannot use preinitialized context.");
            return false;
        }
        context.initializeHomeAndPrefixPaths(newEnv, getLanguageHome());
        PythonCore.writeInfo("Using preinitialized context.");
        context.patch(newEnv);
        return true;
    }

    @Override
    protected PythonContext createContext(Env env) {
        assert this.isWithThread == null || this.isWithThread == PythonOptions.isWithThread(env) : "conflicting thread options in the same language!";
        this.isWithThread = PythonOptions.isWithThread(env);
        Python3Core newCore = new Python3Core(new PythonParserImpl(env));
        final PythonContext context = new PythonContext(this, env, newCore);
        context.initializeHomeAndPrefixPaths(env, getLanguageHome());

        Object[] engineOptionsUnroll = this.engineOptionsStorage;
        if (engineOptionsUnroll == null) {
            this.engineOptionsStorage = engineOptionsUnroll = PythonOptions.createEngineOptionValuesStorage(env);
        } else {
            assert Arrays.equals(engineOptionsUnroll, PythonOptions.createEngineOptionValuesStorage(env)) : "invalid engine options";
        }

        OptionValues options = this.engineOptions;
        if (options == null) {
            this.engineOptions = options = PythonOptions.createEngineOptions(env);
        } else {
            assert options.equals(PythonOptions.createEngineOptions(env)) : "invalid engine options";
        }
        return context;
    }

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

    @Override
    protected CallTarget parse(ParsingRequest request) {
        PythonContext context = getCurrentContext(PythonLanguage.class);
        PythonCore core = context.getCore();
        Source source = request.getSource();
        CompilerDirectives.transferToInterpreter();
        if (core.isInitialized()) {
            context.initializeMainModule(source.getPath());
        }
        if (!request.getArgumentNames().isEmpty()) {
            return Truffle.getRuntime().createCallTarget(parseWithArguments(request));
        }
        RootNode root = doParse(context, source);
        if (core.isInitialized()) {
            return Truffle.getRuntime().createCallTarget(new TopLevelExceptionHandler(this, root));
        } else {
            return Truffle.getRuntime().createCallTarget(root);
        }
    }

    private RootNode doParse(PythonContext context, Source source) {
        ParserMode mode;
        if (source.isInteractive()) {
            if (context.getOption(PythonOptions.TerminalIsInteractive)) {
                // if we run through our own launcher, the sys.__displayhook__ would provide the
                // printing
                mode = ParserMode.Statement;
            } else {
                // if we're not run through our own launcher, the embedder will expect the normal
                // Truffle printing
                mode = ParserMode.InteractiveStatement;
            }
        } else {
            // by default we assume a module
            mode = ParserMode.File;
        }
        PythonCore pythonCore = context.getCore();
        try {
            return (RootNode) pythonCore.getParser().parse(mode, pythonCore, source, null, null);
        } catch (PException e) {
            // handle PException during parsing (PIncompleteSourceException will propagate through)
            Truffle.getRuntime().createCallTarget(new TopLevelExceptionHandler(this, e)).call();
            throw e;
        }
    }

    private RootNode parseWithArguments(ParsingRequest request) {
        final String[] argumentNames = request.getArgumentNames().toArray(new String[request.getArgumentNames().size()]);
        final Source source = request.getSource();
        CompilerDirectives.transferToInterpreter();
        final RootNode executableNode = new RootNode(this) {
            @Node.Child private RootNode rootNode;

            protected Object[] preparePArguments(VirtualFrame frame) {
                int argumentsLength = frame.getArguments().length;
                Object[] arguments = PArguments.create(argumentsLength);
                PArguments.setGlobals(arguments, new PDict());
                PythonUtils.arraycopy(frame.getArguments(), 0, arguments, PArguments.USER_ARGUMENTS_OFFSET, argumentsLength);
                return arguments;
            }

            @Override
            @TruffleBoundary
            public Object execute(VirtualFrame frame) {
                PythonContext context = lookupContextReference(PythonLanguage.class).get();
                assert context != null;
                if (!context.isInitialized()) {
                    context.initialize();
                }
                if (rootNode == null) {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    parse(context, frame);
                }
                Object[] args = preparePArguments(frame);
                Object result = InvokeNode.invokeUncached(rootNode.getCallTarget(), args);
                return result;
            }

            private void parse(PythonContext context, VirtualFrame frame) {
                CompilerAsserts.neverPartOfCompilation();
                rootNode = (RootNode) context.getCore().getParser().parse(ParserMode.WithArguments, context.getCore(), source, frame, argumentNames);
            }
        };
        return executableNode;
    }

    @Override
    protected ExecutableNode parse(InlineParsingRequest request) {
        CompilerDirectives.transferToInterpreter();
        final Source source = request.getSource();
        final MaterializedFrame requestFrame = request.getFrame();
        final ExecutableNode executableNode = new ExecutableNode(this) {
            @CompilationFinal private ContextReference<PythonContext> contextRef;
            @CompilationFinal private volatile PythonContext cachedContext;
            @Child private ExpressionNode expression;

            @Override
            public Object execute(VirtualFrame frame) {
                if (contextRef == null) {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    contextRef = lookupContextReference(PythonLanguage.class);
                }
                PythonContext context = contextRef.get();
                assert context != null && context.isInitialized();
                PythonContext cachedCtx = cachedContext;
                if (cachedCtx == null) {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    parseAndCache(context);
                    cachedCtx = context;
                }
                Object result;
                if (context == cachedCtx) {
                    result = expression.execute(frame);
                } else {
                    result = parseAndEval(context, frame.materialize());
                }
                return result;
            }

            private void parseAndCache(PythonContext context) {
                CompilerAsserts.neverPartOfCompilation();
                expression = insert(parseInline(source, context, requestFrame));
                cachedContext = context;
            }

            @TruffleBoundary
            private Object parseAndEval(PythonContext context, MaterializedFrame frame) {
                ExpressionNode fragment = parseInline(source, context, frame);
                return fragment.execute(frame);
            }
        };
        return executableNode;
    }

    @TruffleBoundary
    protected static ExpressionNode parseInline(Source code, PythonContext context, MaterializedFrame lexicalContextFrame) {
        PythonCore pythonCore = context.getCore();
        return (ExpressionNode) pythonCore.getParser().parse(ParserMode.InlineEvaluation, pythonCore, code, lexicalContextFrame, null);
    }

    @Override
    protected Object getLanguageView(PythonContext context, Object value) {
        assert !(value instanceof PythonAbstractObject);
        PythonObjectFactory factory = PythonObjectFactory.getUncached();
        InteropLibrary interopLib = InteropLibrary.getFactory().getUncached(value);
        try {
            if (interopLib.isBoolean(value)) {
                if (interopLib.asBoolean(value)) {
                    return context.getCore().getTrue();
                } else {
                    return context.getCore().getFalse();
                }
            } else if (interopLib.isString(value)) {
                return factory.createString(interopLib.asString(value));
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

    public static PythonLanguage getCurrent() {
        return getCurrentLanguage(PythonLanguage.class);
    }

    public static PythonContext getContext() {
        return getCurrentContext(PythonLanguage.class);
    }

    public static PythonCore getCore() {
        return getCurrentContext(PythonLanguage.class).getCore();
    }

    @Override
    protected boolean isVisible(PythonContext context, Object value) {
        return value != PNone.NONE && value != PNone.NO_VALUE;
    }

    @Override
    @TruffleBoundary
    protected Iterable<Scope> findLocalScopes(PythonContext context, Node node, Frame frame) {
        ArrayList<Scope> scopes = new ArrayList<>();
        for (Scope s : super.findLocalScopes(context, node, frame)) {
            if (frame == null) {
                PFunctionArgsFinder argsFinder = new PFunctionArgsFinder(node);

                Scope.Builder scopeBuilder = Scope.newBuilder(s.getName(), s.getVariables()).node(s.getNode()).receiver(s.getReceiverName(), s.getReceiver()).rootInstance(
                                s.getRootInstance()).arguments(argsFinder.collectArgs());

                scopes.add(scopeBuilder.build());
            } else {
                scopes.add(s);
            }
        }

        if (frame != null) {
            PythonObject globals = PArguments.getGlobalsSafe(frame);
            if (globals != null) {
                scopes.add(Scope.newBuilder("globals()", scopeFromObject(globals)).build());
            }
            Frame generatorFrame = PArguments.getGeneratorFrameSafe(frame);
            if (generatorFrame != null) {
                for (Scope s : super.findLocalScopes(context, node, generatorFrame)) {
                    scopes.add(s);
                }
            }
        }
        return scopes;
    }

    private static InteropMap scopeFromObject(PythonObject globals) {
        if (globals instanceof PDict) {
            return InteropMap.fromPDict((PDict) globals);
        } else {
            return InteropMap.fromPythonObject(globals);
        }
    }

    @Override
    protected Iterable<Scope> findTopScopes(PythonContext context) {
        ArrayList<Scope> scopes = new ArrayList<>();
        if (context.getBuiltins() != null) {
            // false during initialization
            scopes.add(Scope.newBuilder(BuiltinNames.__MAIN__, context.getMainModule()).build());
            scopes.add(Scope.newBuilder(BuiltinNames.BUILTINS, scopeFromObject(context.getBuiltins())).build());
        }
        return scopes;
    }

    @TruffleBoundary
    public static TruffleLogger getLogger(Class<?> clazz) {
        return TruffleLogger.getLogger(ID, clazz);
    }

    public static Source newSource(PythonContext ctxt, String src, String name, boolean mayBeFile) {
        try {
            SourceBuilder sourceBuilder = null;
            if (mayBeFile) {
                try {
                    TruffleFile truffleFile = ctxt.getPublicTruffleFileRelaxed(name, PythonLanguage.DEFAULT_PYTHON_EXTENSIONS);
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
                } catch (SecurityException | IOException e) {
                    sourceBuilder = null;
                }
            }
            if (sourceBuilder == null) {
                sourceBuilder = Source.newBuilder(ID, src, name);
            }
            return newSource(ctxt, sourceBuilder);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    public static Source newSource(PythonContext ctxt, TruffleFile src, String name) throws IOException {
        return newSource(ctxt, Source.newBuilder(ID, src).name(name));
    }

    private static Source newSource(PythonContext ctxt, SourceBuilder srcBuilder) throws IOException {
        boolean coreIsInitialized = ctxt.getCore().isInitialized();
        boolean internal = !coreIsInitialized && !ctxt.getOption(PythonOptions.ExposeInternalSources);
        if (internal) {
            srcBuilder.internal(true);
        }
        return srcBuilder.build();
    }

    @Override
    protected void initializeMultipleContexts() {
        super.initializeMultipleContexts();
        singleContextAssumption.invalidate();
    }

    private final ConcurrentHashMap<String, CallTarget> cachedCode = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, String[]> cachedCodeModulePath = new ConcurrentHashMap<>();

    @TruffleBoundary
    public CallTarget cacheCode(String filename, Supplier<CallTarget> createCode) {
        return cachedCode.computeIfAbsent(filename, f -> {
            LOGGER.log(Level.FINEST, () -> "Caching CallTarget for " + filename);
            return createCode.get();
        });
    }

    @TruffleBoundary
    public String[] cachedCodeModulePath(String name) {
        return cachedCodeModulePath.get(name);
    }

    @TruffleBoundary
    public boolean hasCachedCode(String name) {
        return cachedCode.get(name) != null;
    }

    @TruffleBoundary
    public CallTarget cacheCode(String filename, Supplier<CallTarget> createCode, String[] modulepath) {
        CallTarget ct = cacheCode(filename, createCode);
        cachedCodeModulePath.computeIfAbsent(filename, t -> modulepath);
        return ct;
    }

    @Override
    protected boolean isThreadAccessAllowed(Thread thread, boolean singleThreaded) {
        if (singleThreaded) {
            return super.isThreadAccessAllowed(thread, singleThreaded);
        }
        if (isWithThread == null) {
            isWithThread = false;
        }
        return isWithThread;
    }

    @Override
    protected void initializeMultiThreading(PythonContext context) {
        context.initializeMultiThreading();
    }

    @Override
    protected void initializeThread(PythonContext context, Thread thread) {
        context.attachThread(thread);
    }

    @Override
    protected void disposeThread(PythonContext context, Thread thread) {
        context.disposeThread(thread);
    }

    public RootCallTarget getOrComputeBuiltinCallTarget(Builtin builtin, Class<? extends PythonBuiltinBaseNode> nodeClass, Function<Builtin, RootCallTarget> supplier) {
        String key = builtin.name() + nodeClass.getName();
        return builtinCallTargetCache.computeIfAbsent(key, (k) -> supplier.apply(builtin));
    }
}
