/*
 * Copyright (c) 2017, Oracle and/or its affiliates.
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
import java.nio.file.InvalidPathException;
import java.util.ArrayList;

import org.graalvm.options.OptionDescriptors;

import com.oracle.graal.python.builtins.Python3Core;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.PythonAbstractObject;
import com.oracle.graal.python.builtins.objects.function.PArguments;
import com.oracle.graal.python.builtins.objects.function.PBuiltinFunction;
import com.oracle.graal.python.builtins.objects.module.PythonModule;
import com.oracle.graal.python.builtins.objects.object.PythonObject;
import com.oracle.graal.python.nodes.BuiltinNames;
import com.oracle.graal.python.nodes.NodeFactory;
import com.oracle.graal.python.nodes.PNode;
import com.oracle.graal.python.nodes.call.InvokeNode;
import com.oracle.graal.python.nodes.control.TopLevelExceptionHandler;
import com.oracle.graal.python.nodes.statement.ImportNode;
import com.oracle.graal.python.parser.PythonParserImpl;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.graal.python.runtime.PythonCore;
import com.oracle.graal.python.runtime.PythonOptions;
import com.oracle.graal.python.runtime.PythonParseResult;
import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.Scope;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.TruffleFile;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.debug.DebuggerTags;
import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.frame.MaterializedFrame;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.instrumentation.ProvidedTags;
import com.oracle.truffle.api.instrumentation.StandardTags;
import com.oracle.truffle.api.nodes.ExecutableNode;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.source.Source;

@TruffleLanguage.Registration(id = PythonLanguage.ID, name = PythonLanguage.NAME, version = PythonLanguage.VERSION, mimeType = PythonLanguage.MIME_TYPE, interactive = true, internal = false)
@ProvidedTags({StandardTags.CallTag.class, StandardTags.StatementTag.class, StandardTags.RootTag.class, DebuggerTags.AlwaysHalt.class})
public final class PythonLanguage extends TruffleLanguage<PythonContext> {

    public static final String ID = "python";
    public static final String NAME = "Python";
    public static final int MAJOR = 3;
    public static final int MINOR = 7;
    public static final int MICRO = 0;
    public static final String VERSION = MAJOR + "." + MINOR + "." + MICRO;

    public static final String MIME_TYPE = "application/x-python";
    public static final String EXTENSION = ".py";

    @CompilationFinal private PythonCore sharedCore;
    private final NodeFactory nodeFactory;

    public PythonLanguage() {
        this.nodeFactory = NodeFactory.create(this);
    }

    public NodeFactory getNodeFactory() {
        return nodeFactory;
    }

    @Override
    protected boolean patchContext(PythonContext context, Env newEnv) {
        ensureHomeInOptions(newEnv);
        if (!optionsAllowPreInitializedContext(context, newEnv)) {
            // Incompatible options - cannot use pre-initialized context
            return false;
        }
        context.setEnv(newEnv);
        context.setOut(newEnv.out());
        context.setErr(newEnv.err());
        context.initialize();
        return true;
    }

    private static boolean optionsAllowPreInitializedContext(PythonContext context, Env newEnv) {
        // Verify that the option for using a shared core is the same as
        // at image building time
        final boolean useSharedCore = newEnv.getOptions().get(PythonOptions.SharedCore);
        return context.getCore().hasSingletonContext() == !useSharedCore;
    }

    @Override
    protected PythonContext createContext(Env env) {
        ensureHomeInOptions(env);
        if (env.getOptions().get(PythonOptions.SharedCore) && sharedCore != null) {
            return new PythonContext(this, env, sharedCore);
        } else {
            Python3Core newCore = new Python3Core(this, new PythonParserImpl());
            return new PythonContext(this, env, newCore);
        }
    }

    private void ensureHomeInOptions(Env env) {
        String languageHome = getLanguageHome();
        String sysPrefix = env.getOptions().get(PythonOptions.SysPrefix);
        String coreHome = env.getOptions().get(PythonOptions.CoreHome);
        String stdLibHome = env.getOptions().get(PythonOptions.StdLibHome);

        TruffleFile home = null;
        if (languageHome != null) {
            home = env.getTruffleFile(languageHome);
        }

        if (home != null) {
            if (sysPrefix.isEmpty()) {
                env.getOptions().set(PythonOptions.SysPrefix, home.getAbsoluteFile().getPath());
            }

            if (coreHome.isEmpty()) {
                try {
                    for (TruffleFile f : home.list()) {
                        if (f.getName().equals("lib-graalpython") && f.isDirectory()) {
                            coreHome = f.getPath();
                            break;
                        }
                    }
                } catch (SecurityException | IOException e) {
                }
                env.getOptions().set(PythonOptions.CoreHome, coreHome);
            }

            if (stdLibHome.isEmpty()) {
                try {
                    outer: for (TruffleFile f : home.list()) {
                        if (f.getName().equals("lib-python") && f.isDirectory()) {
                            for (TruffleFile f2 : f.list()) {
                                if (f2.getName().equals("3") && f.isDirectory()) {
                                    stdLibHome = f2.getPath();
                                    break outer;
                                }
                            }
                        }
                    }
                } catch (SecurityException | IOException e) {
                }
                env.getOptions().set(PythonOptions.StdLibHome, stdLibHome);
            }
        }
    }

    @Override
    protected OptionDescriptors getOptionDescriptors() {
        return PythonOptions.createDescriptors();
    }

    @Override
    protected void initializeContext(PythonContext context) throws Exception {
        Python3Core core = (Python3Core) getCore();
        boolean fullInit = !PythonOptions.getOption(context, PythonOptions.LazyInit);
        if (context.getOptions().get(PythonOptions.SharedCore)) {
            if (sharedCore == null) {
                sharedCore = context.getCore();
                core.bootstrap();
            } else {
                fullInit = false;
            }
        } else {
            core.bootstrap();
        }
        context.initialize();
        if (fullInit) {
            core.initialize();
        }
    }

    @Override
    protected CallTarget parse(ParsingRequest request) throws Exception {
        PythonContext context = this.getContextReference().get();
        if (!context.getCore().isInitialized()) {
            context.getCore().initialize();
        }
        context.getOrCreateMainModule(request.getSource().getPath());

        // if we are running the interpreter, module 'site' is automatically imported
        if (request.getSource().isInteractive()) {
            CompilerAsserts.neverPartOfCompilation();
            // no frame required
            new ImportNode("site").execute(null);
        }
        PythonParseResult parseResult = context.getCore().getParser().parse(context.getCore(), request.getSource());
        RootNode root = parseResult.getRootNode();
        root = new TopLevelExceptionHandler(this, root);
        return Truffle.getRuntime().createCallTarget(root);
    }

    @Override
    protected ExecutableNode parse(InlineParsingRequest request) throws Exception {
        CompilerDirectives.transferToInterpreter();
        final Source source = request.getSource();
        final MaterializedFrame requestFrame = request.getFrame();
        final ExecutableNode executableNode = new ExecutableNode(this) {
            private final ContextReference<PythonContext> contextRef = getContextReference();
            @CompilationFinal private volatile PythonContext cachedContext;
            @Child private PNode expression;

            @Override
            public Object execute(VirtualFrame frame) {
                PythonContext context = contextRef.get();
                assert context == null || context.isInitialized();
                PythonContext cachedCtx = cachedContext;
                if (cachedCtx == null) {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    parseAndCache(context);
                    cachedCtx = context;
                }
                assert context != null;
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
                PNode fragment = parseInline(source, context, frame);
                return fragment.execute(frame);
            }
        };
        return executableNode;
    }

    @TruffleBoundary
    protected static PNode parseInline(Source code, PythonContext context, MaterializedFrame lexicalContextFrame) {
        return context.getCore().getParser().parseInline(context.getCore(), code, lexicalContextFrame);
    }

    @Override
    protected boolean isObjectOfLanguage(Object object) {
        return object instanceof PNode || object instanceof PythonObject;
    }

    @Override
    protected Object findMetaObject(PythonContext context, Object value) {
        if (value != null) {
            if (value instanceof PythonObject) {
                return ((PythonObject) value).asPythonClass().getName();
            } else if (value instanceof PythonAbstractObject ||
                            value instanceof Number ||
                            value instanceof String ||
                            value instanceof Boolean) {
                return getCore().lookupType(value.getClass());
            }
        }
        return null;
    }

    public static PythonContext getContext() {
        return getCurrentContext(PythonLanguage.class);
    }

    @Override
    protected boolean isVisible(PythonContext context, Object value) {
        return value != PNone.NONE && value != PNone.NO_VALUE;
    }

    @Override
    protected Iterable<Scope> findLocalScopes(PythonContext context, Node node, Frame frame) {
        ArrayList<Scope> scopes = new ArrayList<>();
        for (Scope s : super.findLocalScopes(context, node, frame)) {
            scopes.add(s);
        }
        PythonObject globals = PArguments.getGlobals(frame);
        if (globals != null) {
            scopes.add(Scope.newBuilder("globals()", globals).build());
        }
        Frame generatorFrame = PArguments.getGeneratorFrame(frame);
        if (generatorFrame != null) {
            for (Scope s : super.findLocalScopes(context, node, generatorFrame)) {
                scopes.add(s);
            }
        }
        return scopes;
    }

    @Override
    protected Iterable<Scope> findTopScopes(PythonContext context) {
        ArrayList<Scope> scopes = new ArrayList<>();
        scopes.add(Scope.newBuilder("__main__", context.getMainModule()).build());
        return scopes;
    }

    @Override
    protected String toString(PythonContext context, Object value) {
        final PythonModule builtins = context.getBuiltins();
        PBuiltinFunction intClass = (PBuiltinFunction) builtins.getAttribute(BuiltinNames.REPR);
        Object[] userArgs = PArguments.create(1);
        PArguments.setArgument(userArgs, 0, value);
        Object res = InvokeNode.create(intClass).invoke(userArgs);
        return res.toString();
    }

    public static PythonCore getCore() {
        PythonCore core = getCurrentLanguage(PythonLanguage.class).sharedCore;
        return core != null ? core : getContext().getCore();
    }
}
