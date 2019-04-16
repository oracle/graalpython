/*
 * Copyright (c) 2017, 2019, Oracle and/or its affiliates. All rights reserved.
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

import static com.oracle.graal.python.nodes.SpecialAttributeNames.__CAUSE__;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.__FILE__;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.ImportError;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.NotImplementedError;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.locks.ReentrantLock;

import com.oracle.graal.python.builtins.Builtin;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.modules.PythonCextBuiltins.CheckFunctionResultNode;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.cext.CExtNodes.AsPythonObjectNode;
import com.oracle.graal.python.builtins.objects.code.PCode;
import com.oracle.graal.python.builtins.objects.common.HashingCollectionNodes.SetItemNode;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodes;
import com.oracle.graal.python.builtins.objects.dict.PDict;
import com.oracle.graal.python.builtins.objects.exception.PBaseException;
import com.oracle.graal.python.builtins.objects.module.PythonModule;
import com.oracle.graal.python.builtins.objects.object.PythonObject;
import com.oracle.graal.python.builtins.objects.str.PString;
import com.oracle.graal.python.nodes.SpecialMethodNames;
import com.oracle.graal.python.nodes.attributes.ReadAttributeFromObjectNode;
import com.oracle.graal.python.nodes.call.special.CallUnaryMethodNode;
import com.oracle.graal.python.nodes.call.special.LookupAndCallUnaryNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonBinaryBuiltinNode;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.graal.python.runtime.PythonCore;
import com.oracle.graal.python.runtime.PythonOptions;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.graal.python.runtime.exception.PythonErrorType;
import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.TruffleFile;
import com.oracle.truffle.api.TruffleLanguage.Env;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.interop.ArityException;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnknownIdentifierException;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.interop.UnsupportedTypeException;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.api.source.Source.SourceBuilder;

@CoreFunctions(defineModule = "_imp")
public class ImpModuleBuiltins extends PythonBuiltins {
    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return ImpModuleBuiltinsFactory.getFactories();
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

    @Builtin(name = "__create_dynamic__", minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    public abstract static class CreateDynamic extends PythonBuiltinNode {
        protected static final String INITIALIZE_CAPI = "initialize_capi";
        protected static final String IMPORT_NATIVE_MEMORYVIEW = "import_native_memoryview";
        protected static final String RUN_CAPI_LOADED_HOOKS = "run_capi_loaded_hooks";
        private static final String LLVM_LANGUAGE = "llvm";
        @Child private SetItemNode setItemNode;
        @Child private CheckFunctionResultNode checkResultNode;

        @Specialization
        @TruffleBoundary
        public Object run(PythonObject moduleSpec, @SuppressWarnings("unused") Object filename,
                        @CachedLibrary(limit = "1") InteropLibrary interop) {
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
        private Object loadDynamicModuleWithSpec(String name, String path, InteropLibrary interop) {
            ensureCapiWasLoaded();
            Env env = getContext().getEnv();
            String basename = name.substring(name.lastIndexOf('.') + 1);
            TruffleObject sulongLibrary;
            try {
                CallTarget callTarget = env.parse(Source.newBuilder(LLVM_LANGUAGE, env.getTruffleFile(path)).build());
                sulongLibrary = (TruffleObject) callTarget.call();
            } catch (SecurityException | IOException e) {
                throw raise(ImportError, "cannot load %s: %m", path, e);
            } catch (RuntimeException e) {
                throw reportImportError(e, path);
            }
            TruffleObject pyinitFunc;
            try {
                pyinitFunc = (TruffleObject) interop.readMember(sulongLibrary, "PyInit_" + basename);
            } catch (UnknownIdentifierException | UnsupportedMessageException e1) {
                throw raise(ImportError, "no function PyInit_%s found in %s", basename, path);
            }
            try {
                // save current exception state
                PException exceptionState = getContext().getCaughtException();
                // clear current exception such that native code has clean environment
                getContext().setCaughtException(null);

                Object nativeResult = interop.execute(pyinitFunc);
                getCheckResultNode().execute("PyInit_" + basename, nativeResult);

                // restore previous exception state
                getContext().setCaughtException(exceptionState);

                Object result = AsPythonObjectNode.doSlowPath(nativeResult, false);
                if (!(result instanceof PythonModule)) {
                    // PyModuleDef_Init(pyModuleDef)
                    // TODO: PyModule_FromDefAndSpec((PyModuleDef*)m, spec);
                    throw raise(PythonErrorType.NotImplementedError, "multi-phase init of extension module %s", name);
                } else {
                    ((PythonObject) result).setAttribute(__FILE__, path);
                    // TODO: _PyImport_FixupExtensionObject(result, name, path, sys.modules)
                    PDict sysModules = getContext().getSysModules();
                    getSetItemNode().execute(sysModules, name, result);
                    return result;
                }
            } catch (UnsupportedTypeException | ArityException | UnsupportedMessageException e) {
                e.printStackTrace();
                throw raise(ImportError, "cannot initialize %s with PyInit_%s", path, basename);
            } catch (RuntimeException e) {
                throw reportImportError(e, path);
            }
        }

        @TruffleBoundary
        private void ensureCapiWasLoaded() {
            PythonContext ctxt = getContext();
            if (!ctxt.capiWasLoaded()) {
                Env env = ctxt.getEnv();
                CompilerDirectives.transferToInterpreterAndInvalidate();
                TruffleFile capiFile = env.getTruffleFile(PythonCore.getCoreHome(env) + env.getFileNameSeparator() + "capi.bc");
                Object capi = null;
                try {
                    SourceBuilder capiSrcBuilder = Source.newBuilder(LLVM_LANGUAGE, capiFile);
                    if (!PythonOptions.getOption(ctxt, PythonOptions.ExposeInternalSources)) {
                        capiSrcBuilder.internal(true);
                    }
                    capi = ctxt.getEnv().parse(capiSrcBuilder.build()).call();
                } catch (SecurityException | IOException e) {
                    throw raise(PythonErrorType.ImportError, "cannot load capi from " + capiFile.getAbsoluteFile().getPath());
                }
                // call into Python to initialize python_cext module globals
                ReadAttributeFromObjectNode readNode = insert(ReadAttributeFromObjectNode.create());
                CallUnaryMethodNode callNode = insert(CallUnaryMethodNode.create());
                callNode.executeObject(readNode.execute(ctxt.getCore().lookupBuiltinModule(PythonCextBuiltins.PYTHON_CEXT), INITIALIZE_CAPI), capi);
                ctxt.setCapiWasLoaded(capi);
                callNode.executeObject(readNode.execute(ctxt.getCore().lookupBuiltinModule(PythonCextBuiltins.PYTHON_CEXT), RUN_CAPI_LOADED_HOOKS), capi);

                // initialization needs to be finished already but load memoryview implemenation
                // immediately
                callNode.executeObject(readNode.execute(ctxt.getCore().lookupBuiltinModule(PythonCextBuiltins.PYTHON_CEXT), IMPORT_NATIVE_MEMORYVIEW), capi);
            }
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
                checkResultNode = insert(CheckFunctionResultNode.create());
            }
            return checkResultNode;
        }

        @Child private LookupAndCallUnaryNode callReprNode = LookupAndCallUnaryNode.create(SpecialMethodNames.__REPR__);

        @TruffleBoundary
        private PException reportImportError(RuntimeException e, String path) {
            StringBuilder sb = new StringBuilder();
            Object pythonCause = PNone.NONE;
            if (e instanceof PException) {
                PBaseException excObj = ((PException) e).getExceptionObject();
                pythonCause = excObj;
                sb.append(callReprNode.executeObject(excObj));
            } else {
                // that call will cause problems if the format string contains '%p'
                sb.append(e.getMessage());
            }
            Throwable cause = e;
            while ((cause = cause.getCause()) != null) {
                if (e instanceof PException) {
                    if (pythonCause != PNone.NONE) {
                        ((PythonObject) pythonCause).setAttribute(__CAUSE__, ((PException) e).getExceptionObject());
                    }
                    pythonCause = ((PException) e).getExceptionObject();
                }
                if (cause.getMessage() != null) {
                    sb.append(", ");
                    sb.append(cause.getMessage());
                }
            }
            Object[] args = new Object[]{path, sb.toString()};
            PBaseException importExc = factory().createBaseException(ImportError, "cannot load %s: %s", args);
            importExc.setAttribute(__CAUSE__, pythonCause);
            throw raise(importExc);
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
        @TruffleBoundary
        public int run(String name,
                        @Cached("create()") HashingStorageNodes.ContainsKeyNode hasKey) {
            if (getCore().lookupBuiltinModule(name) != null) {
                return 1;
            } else if (getContext() != null && getContext().isInitialized() && hasKey.execute(getContext().getImportedModules().getDictStorage(), name)) {
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
        @SuppressWarnings("unused")
        @Specialization
        @TruffleBoundary
        public Object run(PythonObject moduleSpec) {
            Object origin = moduleSpec.getAttribute("origin");
            Object name = moduleSpec.getAttribute("name");
            if ("built-in".equals(origin)) {
                for (String bm : getCore().builtinModuleNames()) {
                    if (bm.equals(name)) {
                        return getCore().lookupBuiltinModule(bm);
                    }
                }
            }
            throw raise(NotImplementedError, "_imp.create_builtin");
        }
    }

    @Builtin(name = "_fix_co_filename", minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    public abstract static class FixCoFilename extends PythonBinaryBuiltinNode {
        @Specialization
        public Object run(PCode code, PString path) {
            code.setFilename(path.getValue());
            return PNone.NONE;
        }

        @Specialization
        public Object run(PCode code, String path) {
            code.setFilename(path);
            return PNone.NONE;
        }
    }
}
