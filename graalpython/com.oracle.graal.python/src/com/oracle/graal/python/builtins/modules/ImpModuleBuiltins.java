/*
 * Copyright (c) 2017, 2020, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.builtins.modules;

import static com.oracle.graal.python.builtins.PythonBuiltinClassType.SystemError;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.__FILE__;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.NotImplementedError;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.locks.ReentrantLock;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.builtins.Builtin;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.modules.PythonCextBuiltins.CheckFunctionResultNode;
import com.oracle.graal.python.builtins.modules.PythonCextBuiltinsFactory.DefaultCheckFunctionResultNodeGen;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.PythonAbstractObjectFactory.PInteropGetAttributeNodeGen;
import com.oracle.graal.python.builtins.objects.bytes.PBytes;
import com.oracle.graal.python.builtins.objects.bytes.PBytesLike;
import com.oracle.graal.python.builtins.objects.cext.capi.CExtNodesFactory.AsPythonObjectNodeGen;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContext;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyInitObject;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyNodesFactory.HPyAsPythonObjectNodeGen;
import com.oracle.graal.python.builtins.objects.code.PCode;
import com.oracle.graal.python.builtins.objects.common.HashingCollectionNodes.SetItemNode;
import com.oracle.graal.python.builtins.objects.dict.PDict;
import com.oracle.graal.python.builtins.objects.exception.PBaseException;
import com.oracle.graal.python.builtins.objects.ints.IntBuiltins;
import com.oracle.graal.python.builtins.objects.ints.PInt;
import com.oracle.graal.python.builtins.objects.module.PythonModule;
import com.oracle.graal.python.builtins.objects.object.PythonObject;
import com.oracle.graal.python.builtins.objects.object.PythonObjectLibrary;
import com.oracle.graal.python.builtins.objects.str.PString;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PConstructAndRaiseNode;
import com.oracle.graal.python.nodes.SpecialMethodNames;
import com.oracle.graal.python.nodes.attributes.ReadAttributeFromObjectNode;
import com.oracle.graal.python.nodes.attributes.SetAttributeNode;
import com.oracle.graal.python.nodes.call.special.CallUnaryMethodNode;
import com.oracle.graal.python.nodes.call.special.LookupAndCallUnaryNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonBinaryBuiltinNode;
import com.oracle.graal.python.nodes.statement.ExceptionHandlingStatementNode;
import com.oracle.graal.python.nodes.util.CastToJavaStringNode;
import com.oracle.graal.python.runtime.ExecutionContext.ForeignCallContext;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.graal.python.runtime.PythonCore;
import com.oracle.graal.python.runtime.PythonOptions;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.graal.python.util.PythonUtils;
import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.TruffleFile;
import com.oracle.truffle.api.TruffleLanguage.Env;
import com.oracle.truffle.api.TruffleLogger;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.CachedContext;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.interop.ArityException;
import com.oracle.truffle.api.interop.InteropException;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnknownIdentifierException;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.interop.UnsupportedTypeException;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.nodes.LanguageInfo;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.api.source.Source.SourceBuilder;
import com.oracle.truffle.llvm.api.Toolchain;

@CoreFunctions(defineModule = "_imp")
public class ImpModuleBuiltins extends PythonBuiltins {

    static final String HPY_SUFFIX = ".hpy.so";

    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return ImpModuleBuiltinsFactory.getFactories();
    }

    @Override
    public void postInitialize(PythonCore core) {
        super.postInitialize(core);
        PythonContext context = core.getContext();
        PythonModule mod = core.lookupBuiltinModule("_imp");
        mod.setAttribute("check_hash_based_pycs", context.getOption(PythonOptions.CheckHashPycsMode));
    }

    @Builtin(name = "acquire_lock")
    @GenerateNodeFactory
    public abstract static class AcquireLock extends PythonBuiltinNode {
        @Specialization
        @TruffleBoundary
        public Object run() {
            getContext().getImportLock().lock();
            return PNone.NONE;
        }
    }

    @Builtin(name = "release_lock")
    @GenerateNodeFactory
    public abstract static class ReleaseLockNode extends PythonBuiltinNode {
        @Specialization
        @TruffleBoundary
        public Object run() {
            ReentrantLock importLock = getContext().getImportLock();
            if (importLock.isHeldByCurrentThread()) {
                importLock.unlock();
            }
            return PNone.NONE;
        }
    }

    @Builtin(name = "lock_held")
    @GenerateNodeFactory
    public abstract static class LockHeld extends PythonBuiltinNode {
        @Specialization
        @TruffleBoundary
        public boolean run() {
            ReentrantLock importLock = getContext().getImportLock();
            return importLock.isHeldByCurrentThread();
        }
    }

    @Builtin(name = "get_magic")
    @GenerateNodeFactory
    public abstract static class GetMagic extends PythonBuiltinNode {
        static final int MAGIC_NUMBER = 3413;

        @Child private IntBuiltins.ToBytesNode toBytesNode = IntBuiltins.ToBytesNode.create();
        @Child private PythonObjectLibrary pol = PythonObjectLibrary.getFactory().createDispatched(1);

        @Specialization(assumptions = "singleContextAssumption()")
        public PBytes runCachedSingleContext(@SuppressWarnings("unused") VirtualFrame frame,
                        @Cached(value = "getMagicNumberPBytes(frame)", weak = true) PBytes magicBytes) {
            return magicBytes;
        }

        @Specialization(replaces = "runCachedSingleContext")
        public PBytes run(@SuppressWarnings("unused") VirtualFrame frame,
                        @Cached(value = "getMagicNumberBytes(frame)", dimensions = 1) byte[] magicBytes) {
            return factory().createBytes(magicBytes);
        }

        protected PBytes getMagicNumberPBytes(VirtualFrame frame) {
            return factory().createBytes(getMagicNumberBytes(frame));
        }

        protected byte[] getMagicNumberBytes(VirtualFrame frame) {
            try {
                PBytes magic = toBytesNode.execute(frame, MAGIC_NUMBER, 2, "little", false);
                byte[] magicBytes = pol.getBufferBytes(magic);
                return new byte[]{magicBytes[0], magicBytes[1], '\r', '\n'};
            } catch (UnsupportedMessageException e) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                throw new IllegalStateException("magicBytes does not support getBufferBytes()");
            }
        }
    }

    @Builtin(name = "__create_dynamic__", minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    public abstract static class CreateDynamic extends PythonBuiltinNode {
        private static final TruffleLogger LOGGER = PythonLanguage.getLogger(CreateDynamic.class);

        protected static final String INITIALIZE_CAPI = "initialize_capi";
        protected static final String IMPORT_NATIVE_MEMORYVIEW = "import_native_memoryview";
        protected static final String RUN_CAPI_LOADED_HOOKS = "run_capi_loaded_hooks";
        private static final String LLVM_LANGUAGE = "llvm";

        @Child private SetItemNode setItemNode;
        @Child private CheckFunctionResultNode checkResultNode;
        @Child private LookupAndCallUnaryNode callReprNode = LookupAndCallUnaryNode.create(SpecialMethodNames.__REPR__);
        @Child private PConstructAndRaiseNode constructAndRaiseNode;

        static class ImportException extends Exception {
            private static final long serialVersionUID = 3517291912314595890L;
            public final PBaseException cause;
            public final Object name;
            public final Object path;
            public final String formatString;
            public final Object[] formatArgs;

            ImportException(PBaseException cause, Object name, Object path, String formatString, Object... formatArgs) {
                this.cause = cause;
                this.name = name;
                this.path = path;
                this.formatString = formatString;
                this.formatArgs = formatArgs;
            }
        }

        @Specialization
        public Object run(VirtualFrame frame, PythonObject moduleSpec, @SuppressWarnings("unused") Object filename,
                        @Cached ForeignCallContext foreignCallContext,
                        @CachedLibrary(limit = "1") InteropLibrary interop) {
            PythonContext context = getContextRef().get();
            Object state = foreignCallContext.enter(frame, context, this);
            try {
                return run(moduleSpec, interop);
            } catch (ImportException ie) {
                throw getConstructAndRaiseNode().raiseImportError(frame, ie.cause, ie.name, ie.path, ie.formatString, ie.formatArgs);
            } finally {
                foreignCallContext.exit(frame, context, state);
            }
        }

        @TruffleBoundary
        private Object run(PythonObject moduleSpec, InteropLibrary interop) throws ImportException {
            String name = moduleSpec.getAttribute("name").toString();
            String path = moduleSpec.getAttribute("origin").toString();

            Object existingModule = findExtensionObject(name, path);
            if (existingModule != null) {
                return existingModule;
            }

            return loadDynamicModuleWithSpec(name, path, interop);
        }

        @SuppressWarnings({"static-method", "unused"})
        private Object findExtensionObject(String name, String path) {
            // TODO: to avoid initializing an extension module twice, keep an internal dict
            // and possibly return from there, i.e., _PyImport_FindExtensionObject(name, path)
            return null;
        }

        @TruffleBoundary
        private Object loadDynamicModuleWithSpec(String name, String path, InteropLibrary interop) throws ImportException {
            // we always need to load the CPython C API (even for HPy modules)
            ensureCapiWasLoaded(name, path);
            PythonContext context = getContext();
            Env env = context.getEnv();
            String basename = name.substring(name.lastIndexOf('.') + 1);
            TruffleObject sulongLibrary;
            try {
                String extSuffix = ExtensionSuffixesNode.getSoAbi(context);
                CallTarget callTarget = env.parseInternal(Source.newBuilder(LLVM_LANGUAGE, context.getPublicTruffleFileRelaxed(path, extSuffix)).build());
                sulongLibrary = (TruffleObject) callTarget.call();
            } catch (SecurityException | IOException e) {
                logJavaException(e);
                throw new ImportException(wrapJavaException(e), name, path, ErrorMessages.CANNOT_LOAD_M, path, e);
            } catch (RuntimeException e) {
                throw reportImportError(e, name, path);
            }

            // Now, try to detect the C extension's API by looking for the appropriate init
            // functions.
            String hpyInitFuncName = "HPyInit_" + basename;
            String initFuncName = "PyInit_" + basename;
            try {
                if (interop.isMemberExisting(sulongLibrary, hpyInitFuncName)) {
                    return initHPyModule(sulongLibrary, hpyInitFuncName, name, path, interop);
                }
                return initCApiModule(sulongLibrary, initFuncName, name, path, interop);
            } catch (UnsupportedTypeException | ArityException | UnsupportedMessageException e) {
                logJavaException(e);
                throw new ImportException(wrapJavaException(e), name, path, ErrorMessages.CANNOT_INITIALIZE_WITH, path, basename, "");
            } catch (RuntimeException e) {
                throw reportImportError(e, name, path);
            }
        }

        @TruffleBoundary
        private Object initHPyModule(TruffleObject sulongLibrary, String initFuncName, String name, String path, InteropLibrary interop)
                        throws UnsupportedMessageException, ArityException, UnsupportedTypeException, ImportException {
            PythonContext context = getContext();
            GraalHPyContext hpyContext = ensureHPyWasLoaded(context, name, path);

            TruffleObject pyinitFunc;
            try {
                pyinitFunc = (TruffleObject) interop.readMember(sulongLibrary, initFuncName);
            } catch (UnknownIdentifierException | UnsupportedMessageException e1) {
                throw new ImportException(null, name, path, ErrorMessages.NO_FUNCTION_FOUND, "", initFuncName, path);
            }
            Object nativeResult = interop.execute(pyinitFunc, hpyContext);
            getCheckResultNode().execute(initFuncName, nativeResult);

            Object result = HPyAsPythonObjectNodeGen.getUncached().execute(hpyContext, nativeResult);
            if (!(result instanceof PythonModule)) {
                // PyModuleDef_Init(pyModuleDef)
                // TODO: PyModule_FromDefAndSpec((PyModuleDef*)m, spec);
                throw raise(NotImplementedError, "multi-phase init of extension module %s", name);
            } else {
                ((PythonObject) result).setAttribute(__FILE__, path);
                // TODO: _PyImport_FixupExtensionObject(result, name, path, sys.modules)
                PDict sysModules = context.getSysModules();
                getSetItemNode().execute(null, sysModules, name, result);
                return result;
            }
        }

        @TruffleBoundary
        private Object initCApiModule(TruffleObject sulongLibrary, String initFuncName, String name, String path, InteropLibrary interop)
                        throws UnsupportedMessageException, ArityException, UnsupportedTypeException, ImportException {
            PythonContext context = getContext();
            TruffleObject pyinitFunc;
            try {
                pyinitFunc = (TruffleObject) interop.readMember(sulongLibrary, initFuncName);
            } catch (UnknownIdentifierException | UnsupportedMessageException e1) {
                throw new ImportException(null, name, path, ErrorMessages.NO_FUNCTION_FOUND, "", initFuncName, path);
            }
            Object nativeResult;
            try {
                nativeResult = interop.execute(pyinitFunc);
            } catch (ArityException e) {
                // In case of multi-phase init, the init function may take more than one arguments.
                // However, CPython gracefully ignores that. So, we pass just NULL pointers.
                Object[] arguments = new Object[e.getExpectedArity()];
                Arrays.fill(arguments, PNone.NO_VALUE);
                nativeResult = interop.execute(pyinitFunc, arguments);
            }

            getCheckResultNode().execute(initFuncName, nativeResult);

            Object result = AsPythonObjectNodeGen.getUncached().execute(nativeResult);
            if (!(result instanceof PythonModule)) {
                // PyModuleDef_Init(pyModuleDef)
                // TODO: PyModule_FromDefAndSpec((PyModuleDef*)m, spec);
                throw raise(NotImplementedError, "multi-phase init of extension module %s", path);
            } else {
                ((PythonObject) result).setAttribute(__FILE__, path);
                // TODO: _PyImport_FixupExtensionObject(result, name, path, sys.modules)
                PDict sysModules = context.getSysModules();
                getSetItemNode().execute(null, sysModules, name, result);
                return result;
            }
        }

        @TruffleBoundary
        private void ensureCapiWasLoaded(String name, String path) throws ImportException {
            PythonContext context = getContext();
            if (!context.hasCApiContext()) {
                if (!context.getEnv().isNativeAccessAllowed()) {
                    throw new ImportException(null, name, path, ErrorMessages.NATIVE_ACCESS_NOT_ALLOWED);
                }

                Env env = context.getEnv();
                CompilerDirectives.transferToInterpreterAndInvalidate();

                String libPythonName = "libpython" + ExtensionSuffixesNode.getSoAbi(context);
                TruffleFile homePath = env.getInternalTruffleFile(context.getCAPIHome());
                TruffleFile capiFile = homePath.resolve(libPythonName);
                Object capi;
                try {
                    SourceBuilder capiSrcBuilder = Source.newBuilder(LLVM_LANGUAGE, capiFile);
                    if (!context.getLanguage().getEngineOption(PythonOptions.ExposeInternalSources)) {
                        capiSrcBuilder.internal(true);
                    }
                    capi = context.getEnv().parseInternal(capiSrcBuilder.build()).call();
                } catch (IOException | RuntimeException e) {
                    logJavaException(e);
                    throw new ImportException(wrapJavaException(e), name, path, ErrorMessages.CAPI_LOAD_ERROR, capiFile.getAbsoluteFile().getPath());
                }
                try {
                    // call into Python to initialize python_cext module globals
                    ReadAttributeFromObjectNode readNode = ReadAttributeFromObjectNode.getUncached();
                    PythonModule builtinModule = context.getCore().lookupBuiltinModule(PythonCextBuiltins.PYTHON_CEXT);

                    CallUnaryMethodNode callNode = CallUnaryMethodNode.getUncached();
                    callNode.executeObject(null, readNode.execute(builtinModule, INITIALIZE_CAPI), capi);
                    context.setCapiWasLoaded(capi);
                    callNode.executeObject(null, readNode.execute(builtinModule, RUN_CAPI_LOADED_HOOKS), capi);

                    // initialization needs to be finished already but load memoryview
                    // implementation immediately
                    callNode.executeObject(null, readNode.execute(builtinModule, IMPORT_NATIVE_MEMORYVIEW), capi);
                } catch (RuntimeException e) {
                    logJavaException(e);
                    throw new ImportException(wrapJavaException(e), name, path, ErrorMessages.CAPI_LOAD_ERROR, capiFile.getAbsoluteFile().getPath());
                }
            }
        }

        @TruffleBoundary
        private GraalHPyContext ensureHPyWasLoaded(PythonContext context, String name, String path) throws ImportException {
            if (!context.hasHPyContext()) {
                Env env = context.getEnv();
                CompilerDirectives.transferToInterpreterAndInvalidate();

                String libPythonName = "libhpy" + ExtensionSuffixesNode.getSoAbi(context);
                TruffleFile homePath = env.getInternalTruffleFile(context.getCAPIHome());
                TruffleFile capiFile = homePath.resolve(libPythonName);
                try {
                    SourceBuilder capiSrcBuilder = Source.newBuilder(LLVM_LANGUAGE, capiFile);
                    if (!context.getLanguage().getEngineOption(PythonOptions.ExposeInternalSources)) {
                        capiSrcBuilder.internal(true);
                    }
                    Object hpyLibrary = context.getEnv().parseInternal(capiSrcBuilder.build()).call();
                    context.createHPyContext(hpyLibrary);

                    InteropLibrary interopLibrary = InteropLibrary.getFactory().getUncached(hpyLibrary);
                    interopLibrary.invokeMember(hpyLibrary, "graal_hpy_init", new GraalHPyInitObject(context.getHPyContext()));
                } catch (IOException | RuntimeException | InteropException e) {
                    logJavaException(e);
                    throw new ImportException(wrapJavaException(e), name, path, ErrorMessages.HPY_LOAD_ERROR, capiFile.getAbsoluteFile().getPath());
                }
            }
            return context.getHPyContext();
        }

        private static void logJavaException(Exception e) {
            LOGGER.fine(() -> String.format("Original error was: %s\n%s", e, getJavaStacktrace(e)));
        }

        @TruffleBoundary
        private static String getJavaStacktrace(Exception e) {
            StringWriter sw = new StringWriter();
            PrintWriter pw = new PrintWriter(sw);
            e.printStackTrace(pw);
            return sw.toString();
        }

        @TruffleBoundary
        private PBaseException wrapJavaException(Throwable e) {
            PBaseException excObject = factory().createBaseException(SystemError, e.getMessage(), PythonUtils.EMPTY_OBJECT_ARRAY);
            return ExceptionHandlingStatementNode.wrapJavaException(e, this, excObject).getEscapedException();
        }

        private SetItemNode getSetItemNode() {
            if (setItemNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                setItemNode = insert(SetItemNode.create());
            }
            return setItemNode;
        }

        private CheckFunctionResultNode getCheckResultNode() {
            if (checkResultNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                checkResultNode = insert(DefaultCheckFunctionResultNodeGen.create());
            }
            return checkResultNode;
        }

        private PConstructAndRaiseNode getConstructAndRaiseNode() {
            if (constructAndRaiseNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                constructAndRaiseNode = insert(PConstructAndRaiseNode.create());
            }
            return constructAndRaiseNode;
        }

        @TruffleBoundary
        private PException reportImportError(RuntimeException e, String name, String path) throws ImportException {
            StringBuilder sb = new StringBuilder();
            PBaseException pythonCause = null;
            if (e instanceof PException) {
                PBaseException excObj = ((PException) e).getEscapedException();
                pythonCause = excObj;
                sb.append(callReprNode.executeObject(null, excObj));
            } else {
                // that call will cause problems if the format string contains '%p'
                sb.append(e.getMessage());
            }
            Throwable cause = e;
            while ((cause = cause.getCause()) != null) {
                if (e instanceof PException) {
                    PBaseException pythonException = ((PException) e).getEscapedException();
                    if (pythonCause != null) {
                        pythonCause.setCause(pythonException);
                    }
                    pythonCause = pythonException;
                } else {
                    logJavaException(e);
                }
                if (cause.getMessage() != null) {
                    sb.append(", ");
                    sb.append(cause.getMessage());
                }
            }
            Object[] args = new Object[]{path, sb.toString()};
            if (pythonCause != null) {
                throw new ImportException(pythonCause, name, path, ErrorMessages.CANNOT_LOAD, args);
            } else {
                throw new ImportException(null, name, path, ErrorMessages.CANNOT_LOAD, args);
            }
        }
    }

    @Builtin(name = "exec_dynamic", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class ExecDynamicNode extends PythonBuiltinNode {
        @Specialization
        public Object run(PythonModule extensionModule) {
            // TODO: implement PyModule_ExecDef
            return extensionModule;
        }
    }

    @Builtin(name = "is_builtin", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class IsBuiltin extends PythonBuiltinNode {

        @Specialization
        public int run(String name) {
            if (getCore().lookupBuiltinModule(name) != null) {
                // TODO: missing "1" case when the builtin module can be re-initialized
                return -1;
            } else {
                return 0;
            }
        }

        @Specialization
        public int run(@SuppressWarnings("unused") Object noName) {
            return 0;
        }
    }

    @Builtin(name = "create_builtin", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class CreateBuiltin extends PythonBuiltinNode {
        @Specialization(limit = "getCallSiteInlineCacheMaxDepth()")
        public Object run(VirtualFrame frame, PythonObject moduleSpec,
                        @Cached CastToJavaStringNode toJavaStringNode,
                        @Cached("create(__LOADER__)") SetAttributeNode setAttributeNode,
                        @CachedLibrary(value = "moduleSpec") PythonObjectLibrary pol) {
            Object name = pol.lookupAttribute(moduleSpec, frame, "name");
            PythonModule builtinModule = getBuiltinModule(toJavaStringNode.execute(name));
            if (builtinModule != null) {
                // TODO: GR-26411 builtin modules cannot be re-initialized (see is_builtin)
                // We are setting the loader to the spec loader (since this is the loader that is
                // set during bootstrap); this, however, should be handled be the builtin module
                // reinitialization (if reinit is possible)
                Object loader = pol.lookupAttribute(moduleSpec, frame, "loader");
                if (loader != PNone.NO_VALUE) {
                    setAttributeNode.executeVoid(frame, builtinModule, loader);
                }
                return builtinModule;
            }
            throw raise(NotImplementedError, "_imp.create_builtin");
        }

        @TruffleBoundary
        private PythonModule getBuiltinModule(String name) {
            return getCore().lookupBuiltinModule(name);
        }
    }

    @Builtin(name = "source_hash", minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    public abstract static class SourceHashNode extends PythonBinaryBuiltinNode {
        @Specialization
        @TruffleBoundary
        PBytes run(long magicNumber, PBytesLike source) {
            byte[] hash = new byte[Long.BYTES];
            long hashCode = magicNumber ^ source.hashCode();
            for (int i = 0; i < hash.length; i++) {
                hash[i] = (byte) (hashCode << (8 * i));
            }
            return factory().createBytes(hash);
        }

        @Specialization
        PBytes run(PInt magicNumber, PBytesLike source) {
            return run(magicNumber.longValue(), source);
        }
    }

    @Builtin(name = "_fix_co_filename", minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    public abstract static class FixCoFilename extends PythonBinaryBuiltinNode {
        @Specialization
        public Object run(PCode code, PString path,
                        @Cached CastToJavaStringNode castToJavaStringNode) {
            code.setFilename(castToJavaStringNode.execute(path));
            return PNone.NONE;
        }

        @Specialization
        public Object run(PCode code, String path) {
            code.setFilename(path);
            return PNone.NONE;
        }
    }

    @Builtin(name = "extension_suffixes")
    @GenerateNodeFactory
    public abstract static class ExtensionSuffixesNode extends PythonBuiltinNode {
        @Specialization
        Object run(
                        @CachedContext(PythonLanguage.class) PythonContext ctxt) {
            String soAbi = getSoAbi(ctxt);
            return factory().createList(new Object[]{soAbi, HPY_SUFFIX, ".so", ".dylib", ".su"});
        }

        @TruffleBoundary
        public static String getSoAbi(PythonContext ctxt) {
            PythonModule sysModule = ctxt.getCore().lookupBuiltinModule("sys");
            Object implementationObj = ReadAttributeFromObjectNode.getUncached().execute(sysModule, "implementation");
            // sys.implementation.cache_tag
            String cacheTag = (String) PInteropGetAttributeNodeGen.getUncached().execute(implementationObj, "cache_tag");
            // sys.implementation._multiarch
            String multiArch = (String) PInteropGetAttributeNodeGen.getUncached().execute(implementationObj, "_multiarch");

            Env env = ctxt.getEnv();
            LanguageInfo llvmInfo = env.getInternalLanguages().get(GraalPythonModuleBuiltins.LLVM_LANGUAGE);
            Toolchain toolchain = env.lookup(llvmInfo, Toolchain.class);
            String toolchainId = toolchain.getIdentifier();

            // only use '.dylib' if we are on 'Darwin-native'
            String soExt;
            if ("darwin".equals(SysModuleBuiltins.getPythonOSName()) && "native".equals(toolchainId)) {
                soExt = ".dylib";
            } else {
                soExt = ".so";
            }

            return "." + cacheTag + "-" + toolchainId + "-" + multiArch + soExt;
        }
    }

}
