/*
 * Copyright (c) 2017, 2018, Oracle and/or its affiliates.
 *
 * The Universal Permissive License (UPL), Version 1.0
 *
 * Subject to the condition set forth below, permission is hereby granted to any
 * person obtaining a copy of this software, associated documentation and/or data
 * (collectively the "Software"), free of charge and under any and all copyright
 * rights in the Software, and any and all patent rights owned or freely
 * licensable by each licensor hereunder covering either (i) the unmodified
 * Software as contributed to or provided by such licensor, or (ii) the Larger
 * Works (as defined below), to deal in both
 *
 * (a) the Software, and
 * (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
 *     one is included with the Software (each a "Larger Work" to which the
 *     Software is contributed by such licensors),
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

import static com.oracle.graal.python.nodes.SpecialAttributeNames.__FILE__;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.ImportError;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.NotImplementedError;

import java.io.IOException;
import java.net.URI;
import java.util.List;
import java.util.concurrent.locks.ReentrantLock;
import java.util.regex.Pattern;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.builtins.Builtin;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.modules.TruffleCextBuiltins.AsPythonObjectNode;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodes.SetItemNode;
import com.oracle.graal.python.builtins.objects.dict.PDict;
import com.oracle.graal.python.builtins.objects.function.PArguments;
import com.oracle.graal.python.builtins.objects.module.PythonModule;
import com.oracle.graal.python.builtins.objects.object.PythonObject;
import com.oracle.graal.python.nodes.attributes.ReadAttributeFromObjectNode;
import com.oracle.graal.python.nodes.call.special.CallUnaryMethodNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinNode;
import com.oracle.graal.python.runtime.PythonCore;
import com.oracle.graal.python.runtime.PythonParseResult;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.graal.python.runtime.exception.PythonErrorType;
import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.TruffleFile;
import com.oracle.truffle.api.TruffleLanguage.Env;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.interop.ArityException;
import com.oracle.truffle.api.interop.ForeignAccess;
import com.oracle.truffle.api.interop.Message;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnknownIdentifierException;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.interop.UnsupportedTypeException;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.source.Source;

@CoreFunctions(defineModule = "_imp")
public class ImpModuleBuiltins extends PythonBuiltins {
    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinNode>> getNodeFactories() {
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

    @Builtin(name = "__create_dynamic__", fixedNumOfArguments = 2)
    @GenerateNodeFactory
    @ImportStatic(Message.class)
    public abstract static class CreateDynamic extends PythonBuiltinNode {
        protected static final String INITIALIZE_CAPI = "initialize_capi";
        private static final String LLVM_LANGUAGE = "llvm";
        @Child private SetItemNode setItemNode;

        @Specialization
        @TruffleBoundary
        public Object run(PythonObject moduleSpec, @SuppressWarnings("unused") Object filename,
                        @Cached("createExecute(0).createNode()") Node executeNode,
                        @Cached("READ.createNode()") Node readNode) {
            String name = moduleSpec.getAttribute("name").toString();
            String path = moduleSpec.getAttribute("origin").toString();

            Object existingModule = findExtensionObject(name, path);
            if (existingModule != null) {
                return existingModule;
            }

            return loadDynamicModuleWithSpec(name, path, readNode, executeNode);
        }

        @SuppressWarnings({"static-method", "unused"})
        private Object findExtensionObject(String name, String path) {
            // TODO: to avoid initializing an extension module twice, keep an internal dict
            // and possibly return from there, i.e., _PyImport_FindExtensionObject(name, path)
            return null;
        }

        @TruffleBoundary
        private Object loadDynamicModuleWithSpec(String name, String path, Node readNode, Node executeNode) {
            ensureCapiWasLoaded();
            Env env = getContext().getEnv();
            String basename = name.substring(name.lastIndexOf('.') + 1);
            TruffleObject sulongLibrary;
            try {
                CallTarget callTarget = env.parse(env.newSourceBuilder(env.getTruffleFile(path)).language(LLVM_LANGUAGE).build());
                sulongLibrary = (TruffleObject) callTarget.call();
            } catch (SecurityException | IOException e) {
                throw raise(ImportError, "cannot load %s");
            } catch (RuntimeException e) {
                Throwable rootCaus = getRootCause(e);
                throw raise(ImportError, "cannot load %s: %s", path, rootCaus.getMessage());
            }
            TruffleObject pyinitFunc;
            try {
                pyinitFunc = (TruffleObject) ForeignAccess.sendRead(readNode, sulongLibrary, "PyInit_" + basename);
            } catch (UnknownIdentifierException | UnsupportedMessageException e1) {
                throw raise(ImportError, "no function PyInit_%s found in %s", basename, path);
            }
            try {
                Object result = AsPythonObjectNode.doSlowPath(ForeignAccess.sendExecute(executeNode, pyinitFunc));
                if (!(result instanceof PythonModule)) {
                    // PyModuleDef_Init(pyModuleDef)
                    // TODO: PyModule_FromDefAndSpec((PyModuleDef*)m, spec);
                    throw raise(PythonErrorType.NotImplementedError, "multi-phase init of extension module %s", name);
                } else {
                    ((PythonObject) result).setAttribute(__FILE__, path);
                    // TODO: _PyImport_FixupExtensionObject(result, name, path, sys.modules)
                    PDict sysModules = getContext().getSysModules();
                    getSetItemNode().execute(sysModules, sysModules.getDictStorage(), name, result);
                    return result;
                }
            } catch (UnsupportedTypeException | ArityException | UnsupportedMessageException e) {
                e.printStackTrace();
                throw raise(ImportError, "cannot initialize %s with PyInit_%s", path, basename);
            }
        }

        private static Throwable getRootCause(Exception e) {
            Throwable cause = e;
            while (cause.getCause() != null) {
                cause = cause.getCause();
            }
            return cause;
        }

        @TruffleBoundary
        private void ensureCapiWasLoaded() {
            if (!getContext().capiWasLoaded()) {
                Env env = getContext().getEnv();
                CompilerDirectives.transferToInterpreterAndInvalidate();
                TruffleFile capiFile = env.getTruffleFile(PythonCore.getCoreHome(env) + PythonCore.FILE_SEPARATOR + "capi.bc");
                Object capi = null;
                try {
                    capi = getContext().getEnv().parse(env.newSourceBuilder(capiFile).language(LLVM_LANGUAGE).build()).call();
                } catch (SecurityException | IOException e) {
                    throw raise(PythonErrorType.ImportError, "cannot load capi from " + capiFile.getAbsoluteFile().getPath());
                }
                // call into Python to initialize python_cext module globals
                ReadAttributeFromObjectNode readNode = ReadAttributeFromObjectNode.create();
                CallUnaryMethodNode callNode = CallUnaryMethodNode.create();
                callNode.executeObject(readNode.execute(getContext().getCore().lookupBuiltinModule("python_cext"), INITIALIZE_CAPI), capi);
                getContext().setCapiWasLoaded();
            }
        }

        private SetItemNode getSetItemNode() {
            if (setItemNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                setItemNode = insert(SetItemNode.create());
            }
            return setItemNode;
        }

    }

    @Builtin(name = "exec_dynamic", fixedNumOfArguments = 1)
    @GenerateNodeFactory
    public abstract static class ExecDynamicNode extends PythonBuiltinNode {
        @Specialization
        public Object run(PythonModule extensionModule) {
            // TODO: implement PyModule_ExecDef
            return extensionModule;
        }
    }

    @Builtin(name = "is_builtin", fixedNumOfArguments = 1)
    @GenerateNodeFactory
    public abstract static class IsBuiltin extends PythonBuiltinNode {
        @Specialization
        @TruffleBoundary
        public int run(String name) {
            if (getCore().lookupBuiltinModule(name) != null) {
                return 1;
            } else if (getContext() != null && getContext().isInitialized() && getContext().getImportedModules().hasKey(name)) {
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

    @Builtin(name = "create_builtin", fixedNumOfArguments = 1)
    @GenerateNodeFactory
    public abstract static class CreateBuiltin extends PythonBuiltinNode {
        @SuppressWarnings("unused")
        @Specialization
        public Object run(VirtualFrame frame, PythonObject moduleSpec) {
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

    @Builtin(name = "_truffle_bootstrap_file_into_module", fixedNumOfArguments = 2)
    @GenerateNodeFactory
    public abstract static class TruffleImportStar extends PythonBuiltinNode {
        @Specialization
        @TruffleBoundary
        public Object run(String path, String modulename) {
            return run(path, getCore().lookupBuiltinModule(modulename));
        }

        @Specialization
        @TruffleBoundary
        public Object run(String path, PythonModule mod) {
            Env env = getContext().getEnv();
            try {
                PythonParseResult parsedModule;
                String[] pathParts = path.split(Pattern.quote(PythonCore.FILE_SEPARATOR));
                String fileName = pathParts[pathParts.length - 1];
                TruffleFile file = env.getTruffleFile(path);
                Source src = null;
                try {
                    if (file.exists()) {
                        src = env.newSourceBuilder(file).mimeType(PythonLanguage.MIME_TYPE).build();
                    }
                } catch (SecurityException e) {
                }
                if (src == null) {
                    src = Source.newBuilder("").uri(URI.create(path)).mimeType(PythonLanguage.MIME_TYPE).name(fileName).build();
                }
                parsedModule = getCore().getParser().parse(getCore(), src);
                if (parsedModule != null) {
                    CallTarget callTarget = Truffle.getRuntime().createCallTarget(parsedModule.getRootNode());
                    callTarget.call(PArguments.withGlobals(mod));
                }
            } catch (PException e) {
                throw e;
            } catch (IOException e) {
                throw raise(ImportError, e.getMessage());
            }
            return PNone.NONE;
        }
    }

}
