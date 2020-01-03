/*
 * Copyright (c) 2017, 2019, Oracle and/or its affiliates.
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
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import java.util.logging.Level;

import com.oracle.graal.python.builtins.Python3Core;
import com.oracle.graal.python.builtins.objects.PEllipsis;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.PNotImplemented;
import com.oracle.graal.python.builtins.objects.PythonAbstractObject;
import com.oracle.graal.python.builtins.objects.code.PCode;
import com.oracle.graal.python.builtins.objects.dict.PDict;
import com.oracle.graal.python.builtins.objects.function.PArguments;
import com.oracle.graal.python.builtins.objects.function.PBuiltinFunction;
import com.oracle.graal.python.builtins.objects.function.PFunction;
import com.oracle.graal.python.builtins.objects.list.PList;
import com.oracle.graal.python.builtins.objects.method.PBuiltinMethod;
import com.oracle.graal.python.builtins.objects.method.PMethod;
import com.oracle.graal.python.builtins.objects.module.PythonModule;
import com.oracle.graal.python.builtins.objects.object.PythonObject;
import com.oracle.graal.python.builtins.objects.str.PString;
import com.oracle.graal.python.builtins.objects.type.PythonManagedClass;
import com.oracle.graal.python.nodes.BuiltinNames;
import com.oracle.graal.python.nodes.NodeFactory;
import com.oracle.graal.python.nodes.PGuards;
import com.oracle.graal.python.nodes.attributes.ReadAttributeFromDynamicObjectNode;
import com.oracle.graal.python.nodes.call.InvokeNode;
import com.oracle.graal.python.nodes.control.TopLevelExceptionHandler;
import com.oracle.graal.python.nodes.expression.ExpressionNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.literal.ListLiteralNode;
import com.oracle.graal.python.nodes.object.GetClassNode;
import com.oracle.graal.python.parser.PythonParserImpl;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.graal.python.runtime.PythonCore;
import com.oracle.graal.python.runtime.PythonOptions;
import com.oracle.graal.python.runtime.PythonParser.ParserMode;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.graal.python.runtime.interop.InteropMap;
import com.oracle.graal.python.util.PFunctionArgsFinder;
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
import com.oracle.truffle.api.nodes.ExecutableNode;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.nodes.ExplodeLoop.LoopExplosionKind;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.api.source.Source.SourceBuilder;
import com.oracle.truffle.api.source.SourceSection;

import org.graalvm.options.OptionDescriptors;
import org.graalvm.options.OptionValues;

@TruffleLanguage.Registration(id = PythonLanguage.ID, //
                name = PythonLanguage.NAME, //
                version = PythonLanguage.VERSION, //
                characterMimeTypes = PythonLanguage.MIME_TYPE, //
                dependentLanguages = "llvm", //
                interactive = true, internal = false, //
                contextPolicy = TruffleLanguage.ContextPolicy.SHARED, //
                fileTypeDetectors = PythonFileDetector.class)
@ProvidedTags({StandardTags.CallTag.class, StandardTags.StatementTag.class, StandardTags.RootTag.class, StandardTags.TryBlockTag.class, StandardTags.ExpressionTag.class,
                DebuggerTags.AlwaysHalt.class})
public final class PythonLanguage extends TruffleLanguage<PythonContext> {
    public static final String ID = "python";
    public static final String NAME = "Python";
    public static final int MAJOR = 3;
    public static final int MINOR = 7;
    public static final int MICRO = 4;
    public static final String VERSION = MAJOR + "." + MINOR + "." + MICRO;

    public static final String MIME_TYPE = "text/x-python";
    public static final String EXTENSION = ".py";
    public static final String[] DEFAULT_PYTHON_EXTENSIONS = new String[]{EXTENSION, ".pyc"};

    public final Assumption singleContextAssumption = Truffle.getRuntime().createAssumption("Only a single context is active");

    private final NodeFactory nodeFactory;
    public final ConcurrentHashMap<Class<? extends PythonBuiltinBaseNode>, RootCallTarget> builtinCallTargetCache = new ConcurrentHashMap<>();

    @CompilationFinal(dimensions = 1) private static final Object[] CONTEXT_INSENSITIVE_SINGLETONS = new Object[]{PNone.NONE, PNone.NO_VALUE, PEllipsis.INSTANCE, PNotImplemented.NOT_IMPLEMENTED};

    /*
     * We need to store this here, because the check is on the language and can come from a thread
     * that has no context, but we enable or disable threads with a context option. So we store this
     * here when a context is created.
     */
    private Boolean isWithThread = null;

    public static int getNumberOfSpecialSingletons() {
        return CONTEXT_INSENSITIVE_SINGLETONS.length;
    }

    @ExplodeLoop(kind = LoopExplosionKind.FULL_UNROLL_UNTIL_RETURN)
    public static int getSingletonNativePtrIdx(Object obj) {
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
        // internal sources were marked during context initialization
        return (firstOptions.get(PythonOptions.ExposeInternalSources).equals(newOptions.get(PythonOptions.ExposeInternalSources)) &&
                        // we cache WithThread on the language
                        firstOptions.get(PythonOptions.WithThread).equals(newOptions.get(PythonOptions.WithThread)) &&
                        // we cache JythonEmulation on nodes
                        firstOptions.get(PythonOptions.EmulateJython).equals(newOptions.get(PythonOptions.EmulateJython)) &&
                        // we cache CatchAllExceptions hard on TryExceptNode
                        firstOptions.get(PythonOptions.CatchAllExceptions).equals(newOptions.get(PythonOptions.CatchAllExceptions)) &&
                        // we cache BuiltinsInliningMaxCallerSize on the language
                        firstOptions.get(PythonOptions.BuiltinsInliningMaxCallerSize).equals(newOptions.get(PythonOptions.BuiltinsInliningMaxCallerSize)));
    }

    private boolean areOptionsCompatibleWithPreinitializedContext(OptionValues firstOptions, OptionValues newOptions) {
        return (areOptionsCompatible(firstOptions, newOptions) &&
                        // disabling TRegex has an effect on the _sre Python functions that are
                        // dynamically created
                        firstOptions.get(PythonOptions.WithTRegex).equals(newOptions.get(PythonOptions.WithTRegex)));
    }

    @Override
    protected boolean patchContext(PythonContext context, Env newEnv) {
        if (!areOptionsCompatibleWithPreinitializedContext(context.getEnv().getOptions(), newEnv.getOptions())) {
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
        return context;
    }

    @Override
    protected OptionDescriptors getOptionDescriptors() {
        return PythonOptions.createDescriptors();
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
        RootNode root = doParse(context, source);
        if (core.isInitialized()) {
            return Truffle.getRuntime().createCallTarget(new TopLevelExceptionHandler(this, root));
        } else {
            return Truffle.getRuntime().createCallTarget(root);
        }
    }

    private RootNode doParse(PythonContext context, Source source) {
        ParserMode mode = null;
        if (source.isInteractive()) {
            if (PythonOptions.getOption(context, PythonOptions.TerminalIsInteractive)) {
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
            return (RootNode) pythonCore.getParser().parse(mode, pythonCore, source, null);
        } catch (PException e) {
            // handle PException during parsing (PIncompleteSourceException will propagate through)
            Truffle.getRuntime().createCallTarget(new TopLevelExceptionHandler(this, e)).call();
            throw e;
        }
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
        return (ExpressionNode) pythonCore.getParser().parse(ParserMode.InlineEvaluation, pythonCore, code, lexicalContextFrame);
    }

    @Override
    protected boolean isObjectOfLanguage(Object object) {
        return object instanceof PythonAbstractObject;
    }

    @Override
    protected Object findMetaObject(PythonContext context, Object value) {
        if (value != null) {
            if (value instanceof PythonObject) {
                return ((PythonObject) value).asPythonClass();
            } else if (PGuards.isNativeObject(value)) {
                // TODO(fa): we could also use 'GetClassNode.getItSlowPath(value)' here
                return null;
            } else if (value instanceof PythonAbstractObject ||
                            value instanceof Number ||
                            value instanceof String ||
                            value instanceof Boolean) {
                return GetClassNode.getUncached().execute(value);
            }
        }
        return null;
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

                Scope.Builder scopeBuilder = Scope.newBuilder(s.getName(), s.getVariables())
                        .node(s.getNode())
                        .receiver(s.getReceiverName(), s.getReceiver())
                        .rootInstance(s.getRootInstance())
                        .arguments(argsFinder.collectArgs());

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

    @Override
    @TruffleBoundary
    protected SourceSection findSourceLocation(PythonContext context, Object value) {
        if (value instanceof PFunction) {
            PFunction callable = (PFunction) value;
            return callable.getCallTarget().getRootNode().getSourceSection();
        } else if (value instanceof PMethod && ((PMethod) value).getFunction() instanceof PFunction) {
            PFunction callable = (PFunction) ((PMethod) value).getFunction();
            return callable.getCallTarget().getRootNode().getSourceSection();
        } else if (value instanceof PCode) {
            return ((PCode) value).getRootNode().getSourceSection();
        } else if (value instanceof PythonManagedClass) {
            for (String k : ((PythonManagedClass) value).getAttributeNames()) {
                Object attrValue = ReadAttributeFromDynamicObjectNode.getUncached().execute(((PythonManagedClass) value).getStorage(), k);
                SourceSection attrSourceLocation = findSourceLocation(context, attrValue);
                if (attrSourceLocation != null) {
                    return attrSourceLocation;
                }
            }
        } else if (value instanceof PList) {
            ListLiteralNode node = ((PList) value).getOrigin();
            if (node != null) {
                return node.getSourceSection();
            }
        }
        return null;
    }

    @Override
    protected String toString(PythonContext context, Object value) {
        if (PythonOptions.getFlag(context, PythonOptions.UseReprForPrintString)) {
            final PythonModule builtins = context.getBuiltins();
            if (builtins != null) {
                // may be null during initialization
                Object reprAttribute = builtins.getAttribute(BuiltinNames.REPR);
                if (reprAttribute instanceof PBuiltinMethod) {
                    // may be false if e.g. someone accessed our builtins reflectively
                    Object reprFunction = ((PBuiltinMethod) reprAttribute).getFunction();
                    if (reprFunction instanceof PBuiltinFunction) {
                        // may be false if our builtins were tampered with
                        Object[] userArgs = PArguments.create(2);
                        PArguments.setArgument(userArgs, 0, PNone.NONE);
                        PArguments.setArgument(userArgs, 1, value);
                        try {
                            Object result = InvokeNode.invokeUncached((PBuiltinFunction) reprFunction, userArgs);
                            if (result instanceof String) {
                                return (String) result;
                            } else if (result instanceof PString) {
                                return ((PString) result).getValue();
                            } else {
                                // This is illegal for a repr implementation, we ignore the result.
                                // At this point it's probably difficult to report this properly.
                            }
                        } catch (PException e) {
                            // Fall through to default
                        }
                    }
                }
            }
        }
        // This is not a good place to report inconsistencies in any of the above conditions. Just
        // return a String
        if (value instanceof PythonAbstractObject) {
            return ((PythonAbstractObject) value).toString();
        } else if (value instanceof String) {
            return (String) value;
        } else if (value instanceof Number) {
            return ((Number) value).toString();
        } else {
            return "not a Python object";
        }
    }

    @TruffleBoundary
    public static TruffleLogger getLogger() {
        return TruffleLogger.getLogger(ID);
    }

    public static Source newSource(PythonContext ctxt, String src, String name, boolean mayBeFile) {
        try {
            SourceBuilder sourceBuilder = null;
            if (mayBeFile) {
                try {
                    TruffleFile truffleFile = ctxt.getEnv().getInternalTruffleFile(name);
                    if (truffleFile.exists()) {
                        // XXX: (tfel): We don't know if the expression has anything to do with the
                        // filename that's given. We would really have to compare the entire
                        // contents, but as a first approximation, we compare the content lengths.
                        // We override the contents of the source builder with the given source
                        // regardless.
                        if (src.length() == truffleFile.size()) {
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
            throw new AssertionError();
        }
    }

    public static Source newSource(PythonContext ctxt, TruffleFile src, String name) throws IOException {
        return newSource(ctxt, Source.newBuilder(ID, src).name(name));
    }

    private static Source newSource(PythonContext ctxt, SourceBuilder srcBuilder) throws IOException {
        boolean coreIsInitialized = ctxt.getCore().isInitialized();
        boolean internal = !coreIsInitialized && !PythonOptions.getOption(ctxt, PythonOptions.ExposeInternalSources);
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
            PythonLanguage.getLogger().log(Level.FINEST, () -> "Caching CallTarget for " + filename);
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
}
