/*
 * Copyright (c) 2019, 2022, Oracle and/or its affiliates. All rights reserved.
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

import static com.oracle.graal.python.PythonLanguage.GRAALPYTHON_ID;
import static com.oracle.graal.python.nodes.BuiltinNames.__GRAALPYTHON__;
import static com.oracle.graal.python.nodes.BuiltinNames.__MAIN__;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.__NAME__;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.ImportError;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.SystemError;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.TypeError;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.logging.Level;

import org.graalvm.nativeimage.ImageInfo;
import org.graalvm.polyglot.io.ByteSequence;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.builtins.Builtin;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.Python3Core;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.modules.GraalPythonModuleBuiltinsFactory.DebugNodeFactory;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.bytes.BytesNodes;
import com.oracle.graal.python.builtins.objects.bytes.PBytes;
import com.oracle.graal.python.builtins.objects.bytes.PBytesLike;
import com.oracle.graal.python.builtins.objects.code.CodeNodes;
import com.oracle.graal.python.builtins.objects.code.PCode;
import com.oracle.graal.python.builtins.objects.common.DynamicObjectStorage;
import com.oracle.graal.python.builtins.objects.common.EconomicMapStorage;
import com.oracle.graal.python.builtins.objects.common.EmptyStorage;
import com.oracle.graal.python.builtins.objects.common.HashMapStorage;
import com.oracle.graal.python.builtins.objects.common.HashingStorage;
import com.oracle.graal.python.builtins.objects.common.HashingStorageLibrary;
import com.oracle.graal.python.builtins.objects.dict.PDict;
import com.oracle.graal.python.builtins.objects.exception.OSErrorEnum;
import com.oracle.graal.python.builtins.objects.exception.OSErrorEnum.ErrorAndMessagePair;
import com.oracle.graal.python.builtins.objects.function.PFunction;
import com.oracle.graal.python.builtins.objects.function.Signature;
import com.oracle.graal.python.builtins.objects.generator.PGenerator;
import com.oracle.graal.python.builtins.objects.list.PList;
import com.oracle.graal.python.builtins.objects.method.PMethod;
import com.oracle.graal.python.builtins.objects.module.PythonModule;
import com.oracle.graal.python.builtins.objects.object.PythonObject;
import com.oracle.graal.python.builtins.objects.set.PSet;
import com.oracle.graal.python.lib.PyObjectCallMethodObjArgs;
import com.oracle.graal.python.lib.PyObjectTypeCheck;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.argument.ReadIndexedArgumentNode;
import com.oracle.graal.python.nodes.argument.ReadVarArgsNode;
import com.oracle.graal.python.nodes.builtins.FunctionNodes.GetCallTargetNode;
import com.oracle.graal.python.nodes.builtins.FunctionNodes.GetSignatureNode;
import com.oracle.graal.python.nodes.call.CallNode;
import com.oracle.graal.python.nodes.classes.IsSubtypeNode;
import com.oracle.graal.python.nodes.function.FunctionRootNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonBinaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonTernaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonUnaryBuiltinNode;
import com.oracle.graal.python.nodes.object.GetClassNode;
import com.oracle.graal.python.nodes.statement.AbstractImportNode;
import com.oracle.graal.python.nodes.subscript.GetItemNode;
import com.oracle.graal.python.nodes.truffle.PythonArithmeticTypes;
import com.oracle.graal.python.nodes.util.CannotCastException;
import com.oracle.graal.python.nodes.util.CastToJavaStringNode;
import com.oracle.graal.python.runtime.PosixSupportLibrary;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.graal.python.runtime.PythonOptions;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.graal.python.runtime.exception.PythonExitException;
import com.oracle.graal.python.runtime.object.PythonObjectFactory;
import com.oracle.graal.python.runtime.sequence.storage.SequenceStorage;
import com.oracle.graal.python.util.PythonUtils;
import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.TruffleFile;
import com.oracle.truffle.api.TruffleLanguage.Env;
import com.oracle.truffle.api.TruffleLogger;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.dsl.TypeSystemReference;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.UnknownIdentifierException;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.nodes.LanguageInfo;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.NodeUtil;
import com.oracle.truffle.api.nodes.NodeVisitor;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.llvm.api.Toolchain;

@CoreFunctions(defineModule = __GRAALPYTHON__, isEager = true)
public class GraalPythonModuleBuiltins extends PythonBuiltins {
    private static final TruffleLogger LOGGER = PythonLanguage.getLogger(GraalPythonModuleBuiltins.class);

    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return GraalPythonModuleBuiltinsFactory.getFactories();
    }

    @Override
    public void initialize(Python3Core core) {
        super.initialize(core);
        builtinConstants.put("is_native", ImageInfo.inImageCode());
        PythonContext ctx = core.getContext();
        String encodingOpt = ctx.getLanguage().getEngineOption(PythonOptions.StandardStreamEncoding);
        String standardStreamEncoding = null;
        String standardStreamError = null;
        if (encodingOpt != null && !encodingOpt.isEmpty()) {
            String[] parts = encodingOpt.split(":");
            if (parts.length > 0) {
                standardStreamEncoding = parts[0].isEmpty() ? "utf-8" : parts[0];
                standardStreamError = parts.length > 1 && !parts[1].isEmpty() ? parts[1] : "strict";
            }
        }
        if (standardStreamEncoding == null) {
            standardStreamEncoding = "utf-8";
            standardStreamError = "surrogateescape";
        }

        if (LOGGER.isLoggable(Level.FINE)) {
            LOGGER.fine(String.format("Setting default stdio encoding to %s:%s", standardStreamEncoding, standardStreamError));
        }
        this.builtinConstants.put("stdio_encoding", standardStreamEncoding);
        this.builtinConstants.put("stdio_error", standardStreamError);
        this.builtinConstants.put("startup_wall_clock_ts", -1L);
        this.builtinConstants.put("startup_nano", -1L);
        // we need these during core initialization, they are re-set in postInitialize
        postInitialize(core);
    }

    @Override
    public void postInitialize(Python3Core core) {
        super.postInitialize(core);
        PythonContext context = core.getContext();
        PythonModule mod = core.lookupBuiltinModule(__GRAALPYTHON__);
        PythonLanguage language = context.getLanguage();
        if (!ImageInfo.inImageBuildtimeCode()) {
            mod.setAttribute("home", language.getHome());
        }
        mod.setAttribute("in_image_buildtime", ImageInfo.inImageBuildtimeCode());
        mod.setAttribute("in_image", ImageInfo.inImageCode());
        String coreHome = context.getCoreHome();
        String stdlibHome = context.getStdlibHome();
        String capiHome = context.getCAPIHome();
        Env env = context.getEnv();
        LanguageInfo llvmInfo = env.getInternalLanguages().get(PythonLanguage.LLVM_LANGUAGE);
        Toolchain toolchain = env.lookup(llvmInfo, Toolchain.class);
        mod.setAttribute("jython_emulation_enabled", language.getEngineOption(PythonOptions.EmulateJython));
        mod.setAttribute("host_import_enabled", context.getEnv().isHostLookupAllowed());
        mod.setAttribute("core_home", coreHome);
        mod.setAttribute("stdlib_home", stdlibHome);
        mod.setAttribute("capi_home", capiHome);
        mod.setAttribute("jni_home", context.getJNIHome());
        mod.setAttribute("platform_id", toolchain.getIdentifier());
        Object[] arr = convertToObjectArray(PythonOptions.getExecutableList(context));
        PList executableList = PythonObjectFactory.getUncached().createList(arr);
        mod.setAttribute("executable_list", executableList);
        mod.setAttribute("ForeignType", core.lookupType(PythonBuiltinClassType.ForeignObject));

        if (!context.getOption(PythonOptions.EnableDebuggingBuiltins)) {
            mod.setAttribute("dump_truffle_ast", PNone.NO_VALUE);
            mod.setAttribute("tdebug", PNone.NO_VALUE);
            mod.setAttribute("set_storage_strategy", PNone.NO_VALUE);
            mod.setAttribute("dump_heap", PNone.NO_VALUE);
        }
    }

    @TruffleBoundary
    String getStdIOEncoding() {
        return (String) builtinConstants.get("stdio_encoding");
    }

    @TruffleBoundary
    String getStdIOError() {
        return (String) builtinConstants.get("stdio_error");
    }

    /**
     * Entry point for executing a path using the launcher, e.g. {@code python foo.py}
     */
    @Builtin(name = "run_path")
    @GenerateNodeFactory
    abstract static class RunPathNode extends PythonBuiltinNode {
        @Specialization
        @TruffleBoundary
        PNone run() {
            /*
             * This node handles the part of pymain_run_python where the filename is not null. The
             * other paths through pymain_run_python are handled in GraalPythonMain and the path
             * prepending is done in PythonLanguage in those other cases
             */
            assert !ImageInfo.inImageBuildtimeCode();
            PythonContext context = getContext();
            String inputFilePath = context.getOption(PythonOptions.InputFilePath);
            PythonModule sysModule = context.getSysModule();
            boolean needsMainImporter = !inputFilePath.isEmpty() && getImporter(sysModule, inputFilePath);
            if (needsMainImporter) {
                Object sysPath = sysModule.getAttribute("path");
                PyObjectCallMethodObjArgs.getUncached().execute(null, sysPath, "insert", 0, inputFilePath);
            } else {
                // This is normally done by PythonLanguage, but is suppressed when we have a path
                // argument
                context.addSysPath0();
            }

            if (needsMainImporter) {
                runModule(__MAIN__, false);
            } else {
                runFile(context, inputFilePath);
            }
            return PNone.NONE;
        }

        // Equivalent of CPython's pymain_run_file and pymain_run_stdin
        private void runFile(PythonContext context, String inputFilePath) {
            Source source;
            try {
                Source.SourceBuilder builder;
                if (inputFilePath.isEmpty()) {
                    // Reading from stdin
                    builder = Source.newBuilder(PythonLanguage.ID, new InputStreamReader(context.getStandardIn()), "<stdin>");
                } else {
                    TruffleFile file = context.getPublicTruffleFileRelaxed(inputFilePath);
                    builder = Source.newBuilder(PythonLanguage.ID, file);
                }
                source = builder.mimeType(PythonLanguage.MIME_TYPE).build();
                // TODO we should handle non-IO errors better
            } catch (IOException e) {
                ErrorAndMessagePair error = OSErrorEnum.fromException(e);
                String msg = String.format("%s: can't open file '%s': [Errno %d] %s\n", context.getOption(PythonOptions.Executable), inputFilePath, error.oserror.getNumber(), error.message);
                // CPython uses fprintf(stderr, ...)
                try {
                    context.getStandardErr().write(msg.getBytes(StandardCharsets.UTF_8));
                } catch (IOException ioException) {
                    // Ignore
                }
                // The exit value is hardcoded in CPython too
                throw new PythonExitException(this, 2);
            }
            CallTarget callTarget = context.getEnv().parsePublic(source);
            callTarget.call(PythonUtils.EMPTY_OBJECT_ARRAY);
        }

        // Equivalent of CPython's pymain_run_module
        private static void runModule(String module, boolean setArgv0) {
            Object runpy = AbstractImportNode.importModule("runpy");
            PyObjectCallMethodObjArgs.getUncached().execute(null, runpy, "_run_module_as_main", module, setArgv0);
        }

        // Equivalent of CPython's pymain_get_importer, but returns a boolean
        private static boolean getImporter(PythonModule sysModule, String inputFilePath) {
            Object importer = null;
            Object pathHooks = sysModule.getAttribute("path_hooks");
            Object pathImporterCache = sysModule.getAttribute("path_importer_cache");
            if (pathHooks instanceof PList && pathImporterCache instanceof PDict) {
                PDict pathImporterCacheDict = (PDict) pathImporterCache;
                importer = pathImporterCacheDict.getItem(inputFilePath);
                if (importer == null) {
                    /* set path_importer_cache[p] to None to avoid recursion */
                    pathImporterCacheDict.setItem(inputFilePath, PNone.NONE);
                    SequenceStorage storage = ((PList) pathHooks).getSequenceStorage();
                    Object[] hooks = storage.getInternalArray();
                    int numHooks = storage.length();
                    for (int i = 0; i < numHooks; i++) {
                        try {
                            importer = CallNode.getUncached().execute(hooks[i], inputFilePath);
                            break;
                        } catch (PException e) {
                            if (!IsSubtypeNode.getUncached().execute(GetClassNode.getUncached().execute(e.getUnreifiedException()), ImportError)) {
                                throw e;
                            }
                        }
                    }
                    if (importer != null) {
                        pathImporterCacheDict.setItem(inputFilePath, importer);
                    }
                }
            }
            return importer != null && importer != PNone.NONE;
        }
    }

    private static Object[] convertToObjectArray(String[] arr) {
        Object[] objectArr = new Object[arr.length];
        System.arraycopy(arr, 0, objectArr, 0, arr.length);
        return objectArr;
    }

    @Builtin(name = "read_file", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class ReadFileNode extends PythonUnaryBuiltinNode {
        @Specialization
        public PBytes doString(VirtualFrame frame, String filename) {
            try {
                TruffleFile file = getContext().getPublicTruffleFileRelaxed(filename, PythonLanguage.DEFAULT_PYTHON_EXTENSIONS);
                byte[] bytes = file.readAllBytes();
                return factory().createBytes(bytes);
            } catch (Exception ex) {
                ErrorAndMessagePair errAndMsg = OSErrorEnum.fromException(ex);
                throw raiseOSError(frame, errAndMsg.oserror.getNumber(), errAndMsg.message);
            }
        }

        @Specialization
        public Object doGeneric(VirtualFrame frame, Object filename,
                        @Cached CastToJavaStringNode castToJavaStringNode) {
            return doString(frame, castToJavaStringNode.execute(filename));
        }
    }

    @Builtin(name = "dump_truffle_ast", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class DumpTruffleAstNode extends PythonUnaryBuiltinNode {
        @Specialization
        @TruffleBoundary
        public String doIt(PFunction func) {
            return NodeUtil.printTreeToString(GetCallTargetNode.getUncached().execute(func).getRootNode());
        }

        @Specialization(guards = "isFunction(method.getFunction())")
        @TruffleBoundary
        public String doIt(PMethod method) {
            return NodeUtil.printTreeToString(GetCallTargetNode.getUncached().execute(method).getRootNode());
        }

        @Specialization
        @TruffleBoundary
        public String doIt(PGenerator gen) {
            return NodeUtil.printTreeToString(gen.getCurrentCallTarget().getRootNode());
        }

        @Specialization
        @TruffleBoundary
        public String doIt(PCode code) {
            return NodeUtil.printTreeToString(CodeNodes.GetCodeRootNode.getUncached().execute(code));
        }

        @Fallback
        @TruffleBoundary
        public Object doit(Object object) {
            return "truffle ast dump not supported for " + object.toString();
        }

        protected static boolean isFunction(Object callee) {
            return callee instanceof PFunction;
        }
    }

    @Builtin(name = "current_import", minNumOfPositionalArgs = 0)
    @GenerateNodeFactory
    public abstract static class CurrentImport extends PythonBuiltinNode {
        @Specialization
        String doIt() {
            return getContext().getCurrentImport();
        }
    }

    @Builtin(name = "tdebug", takesVarArgs = true)
    @GenerateNodeFactory
    public abstract static class DebugNode extends PythonBuiltinNode {

        public abstract Object execute(Object[] args);

        @Specialization
        @TruffleBoundary
        public Object doIt(Object[] args) {
            PrintWriter stdout = new PrintWriter(getContext().getStandardOut());
            for (int i = 0; i < args.length; i++) {
                stdout.println(args[i]);
            }
            stdout.flush();
            return PNone.NONE;
        }

        public static DebugNode create() {
            return DebugNodeFactory.create(null);
        }
    }

    @Builtin(name = "builtin", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class BuiltinNode extends PythonUnaryBuiltinNode {
        @Child private GetItemNode getNameNode = GetItemNode.create();

        @Specialization
        public Object doIt(VirtualFrame frame, PFunction func) {
            PFunction builtinFunc = convertToBuiltin(func);
            PythonObject globals = func.getGlobals();
            PythonModule builtinModule;
            if (globals instanceof PythonModule) {
                builtinModule = (PythonModule) globals;
            } else {
                String moduleName = (String) getNameNode.execute(frame, globals, __NAME__);
                builtinModule = getCore().lookupBuiltinModule(moduleName);
                assert builtinModule != null;
            }
            return factory().createBuiltinMethod(builtinModule, builtinFunc);
        }

        @TruffleBoundary
        public synchronized PFunction convertToBuiltin(PFunction func) {
            /*
             * (tfel): To be compatible with CPython, builtin module functions must be bound to
             * their respective builtin module. We ignore that builtin functions should really be
             * builtin methods here - it does not hurt if they are normal methods. What does hurt,
             * however, is if they are not bound, because then using these functions in class field
             * won't work when they are called from an instance of that class due to the implicit
             * currying with "self".
             */
            Signature signature = GetSignatureNode.getUncached().execute(func);
            PFunction builtinFunc;
            FunctionRootNode functionRootNode = (FunctionRootNode) CodeNodes.GetCodeRootNode.getUncached().execute(func.getCode());
            if (signature.getParameterIds().length > 0 && signature.getParameterIds()[0].equals("self")) {
                /*
                 * If the first parameter is called self, we assume the function does explicitly
                 * declare the module argument
                 */
                builtinFunc = func;
                functionRootNode.setPythonInternal(true);
            } else {
                RootCallTarget callTarget = PythonLanguage.get(functionRootNode).createCachedCallTarget(
                                r -> {
                                    /*
                                     * Otherwise, we create a new function with a signature that
                                     * requires one extra argument in front. We actually modify the
                                     * function's AST here, so the original PFunction cannot be used
                                     * anymore (its signature won't agree with it's indexed
                                     * parameter reads).
                                     */
                                    assert !functionRootNode.isPythonInternal() : "a function cannot be rewritten as builtin twice";
                                    return functionRootNode.rewriteWithNewSignature(signature.createWithSelf(), new NodeVisitor() {

                                        @Override
                                        public boolean visit(Node node) {
                                            if (node instanceof ReadVarArgsNode) {
                                                node.replace(ReadVarArgsNode.create(((ReadVarArgsNode) node).isBuiltin()));
                                            } else if (node instanceof ReadIndexedArgumentNode) {
                                                node.replace(ReadIndexedArgumentNode.create(((ReadIndexedArgumentNode) node).getIndex() + 1));
                                            }
                                            return true;
                                        }
                                    }, x -> x);
                                }, functionRootNode);

                String name = func.getName();
                builtinFunc = factory().createFunction(name, func.getEnclosingClassName(),
                                factory().createCode(callTarget),
                                func.getGlobals(), func.getDefaults(), func.getKwDefaults(), func.getClosure());
            }

            return builtinFunc;
        }
    }

    @Builtin(name = "builtin_method", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class BuiltinMethodNode extends PythonUnaryBuiltinNode {
        @Specialization
        public Object doIt(PFunction func,
                        @Cached CodeNodes.GetCodeRootNode getRootNode) {
            FunctionRootNode functionRootNode = (FunctionRootNode) getRootNode.execute(func.getCode());
            functionRootNode.setPythonInternal(true);
            return func;
        }
    }

    @Builtin(name = "get_toolchain_tool_path", minNumOfPositionalArgs = 1)
    @TypeSystemReference(PythonArithmeticTypes.class)
    @GenerateNodeFactory
    public abstract static class GetToolPathNode extends PythonUnaryBuiltinNode {
        @Specialization
        @TruffleBoundary
        protected Object getToolPath(String tool) {
            Env env = getContext().getEnv();
            LanguageInfo llvmInfo = env.getInternalLanguages().get(PythonLanguage.LLVM_LANGUAGE);
            Toolchain toolchain = env.lookup(llvmInfo, Toolchain.class);
            TruffleFile toolPath = toolchain.getToolPath(tool);
            if (toolPath == null) {
                return PNone.NONE;
            }
            return toolPath.toString();
        }
    }

    @Builtin(name = "get_toolchain_paths", minNumOfPositionalArgs = 1)
    @TypeSystemReference(PythonArithmeticTypes.class)
    @GenerateNodeFactory
    public abstract static class GetToolchainPathsNode extends PythonUnaryBuiltinNode {
        @Specialization
        @TruffleBoundary
        protected Object getPaths(String key) {
            Env env = getContext().getEnv();
            LanguageInfo llvmInfo = env.getInternalLanguages().get(PythonLanguage.LLVM_LANGUAGE);
            Toolchain toolchain = env.lookup(llvmInfo, Toolchain.class);
            List<TruffleFile> pathsList = toolchain.getPaths(key);
            if (pathsList == null) {
                return factory().createTuple(PythonUtils.EMPTY_OBJECT_ARRAY);
            }
            Object[] paths = new Object[pathsList.size()];
            int i = 0;
            for (TruffleFile f : pathsList) {
                paths[i++] = f.toString();
            }
            return factory().createTuple(paths);
        }
    }

    // Equivalent of PyObject_TypeCheck
    @Builtin(name = "type_check", minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    public abstract static class TypeCheckNode extends PythonBinaryBuiltinNode {
        @Specialization
        boolean typeCheck(Object instance, Object cls,
                        @Cached PyObjectTypeCheck typeCheckNode) {
            return typeCheckNode.execute(instance, cls);
        }
    }

    @Builtin(name = "posix_module_backend", minNumOfPositionalArgs = 0)
    @GenerateNodeFactory
    public abstract static class PosixModuleBackendNode extends PythonBuiltinNode {
        @Specialization
        String posixModuleBackend(
                        @CachedLibrary("getPosixSupport()") PosixSupportLibrary posixLib) {
            return posixLib.getBackend(getPosixSupport());
        }
    }

    @Builtin(name = "time_millis", minNumOfPositionalArgs = 0, maxNumOfPositionalArgs = 1, doc = "Like time.time() but in milliseconds resolution.")
    @GenerateNodeFactory
    public abstract static class TimeMillis extends PythonUnaryBuiltinNode {
        @Specialization
        @TruffleBoundary
        static long doIt(@SuppressWarnings("unused") Object dummy) {
            return System.currentTimeMillis();
        }
    }

    // Internal builtin used for testing: changes strategy of newly allocated set or map
    @Builtin(name = "set_storage_strategy", minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    public abstract static class SetStorageStrategyNode extends PythonBinaryBuiltinNode {
        @Specialization
        Object doSet(PSet set, String strategyName) {
            validate(set.getDictStorage());
            set.setDictStorage(getStrategy(strategyName, getLanguage()));
            return set;
        }

        @Specialization
        Object doDict(PDict dict, String strategyName) {
            validate(dict.getDictStorage());
            dict.setDictStorage(getStrategy(strategyName, getLanguage()));
            return dict;
        }

        private HashingStorage getStrategy(String name, PythonLanguage lang) {
            switch (name) {
                case "empty":
                    return EmptyStorage.INSTANCE;
                case "hashmap":
                    return new HashMapStorage();
                case "dynamicobject":
                    return new DynamicObjectStorage(lang);
                case "economicmap":
                    return EconomicMapStorage.create();
                default:
                    throw raise(PythonBuiltinClassType.ValueError, "Unknown storage strategy name");
            }
        }

        private void validate(HashingStorage dictStorage) {
            if (HashingStorageLibrary.getUncached().length(dictStorage) != 0) {
                throw raise(PythonBuiltinClassType.ValueError, "Should be used only on newly allocated empty sets");
            }
        }
    }

    @Builtin(name = "extend", minNumOfPositionalArgs = 1, doc = "Extends Java class and return HostAdapterCLass")
    @GenerateNodeFactory
    public abstract static class JavaExtendNode extends PythonUnaryBuiltinNode {
        @Specialization
        Object doIt(Object value,
                        @CachedLibrary(limit = "3") InteropLibrary lib) {
            if (ImageInfo.inImageBuildtimeCode()) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                throw new UnsupportedOperationException(ErrorMessages.CANT_EXTEND_JAVA_CLASS_NOT_JVM);
            }
            if (ImageInfo.inImageRuntimeCode()) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                throw raise(SystemError, ErrorMessages.CANT_EXTEND_JAVA_CLASS_NOT_JVM);
            }

            Env env = getContext().getEnv();
            if (!isType(value, env, lib)) {
                throw raise(TypeError, ErrorMessages.CANT_EXTEND_JAVA_CLASS_NOT_TYPE, value);
            }

            try {
                return env.createHostAdapter(new Object[]{value});
            } catch (Exception ex) {
                throw raise(TypeError, ex.getMessage(), ex);
            }
        }

        protected static boolean isType(Object obj, Env env, InteropLibrary lib) {
            return env.isHostObject(obj) && (env.isHostSymbol(obj) || lib.isMetaObject(obj));
        }

    }

    @Builtin(name = "super", minNumOfPositionalArgs = 1, doc = "Returns HostAdapter instance of the object or None")
    @GenerateNodeFactory
    public abstract static class JavaSuperNode extends PythonUnaryBuiltinNode {
        @Specialization
        @TruffleBoundary
        Object doIt(Object value) {
            try {
                return InteropLibrary.getUncached().readMember(value, "super");
            } catch (UnsupportedMessageException | UnknownIdentifierException e) {
                return PNone.NONE;
            }
        }
    }

    @Builtin(name = "dump_heap", minNumOfPositionalArgs = 0)
    @GenerateNodeFactory
    abstract static class DumpHeapNode extends PythonBuiltinNode {
        @Specialization
        String doit(VirtualFrame frame) {
            TruffleFile tempFile;
            try {
                PythonContext context = getContext();
                tempFile = context.getEnv().createTempFile(context.getEnv().getCurrentWorkingDirectory(), GRAALPYTHON_ID, ".hprof");
                tempFile.delete();
            } catch (IOException e) {
                throw raiseOSError(frame, e);
            }
            PythonUtils.dumpHeap(tempFile.getPath());
            return tempFile.getPath();
        }
    }

    @Builtin(name = "compile", parameterNames = {"codestr", "path", "mode"})
    @GenerateNodeFactory
    abstract static class CompileNode extends PythonTernaryBuiltinNode {
        @Specialization
        PCode compile(VirtualFrame frame, Object codestr, String path, String mode,
                        @Cached PRaiseNode raise,
                        @Cached BytesNodes.ToBytesNode toBytes,
                        @Cached CastToJavaStringNode castStr,
                        @Cached("create(false)") BuiltinFunctions.CompileNode compileNode) {
            if (mode.equals("pyc")) {
                Source source;
                if (codestr instanceof PBytesLike) {
                    try {
                        byte[] c = toBytes.execute((PBytesLike) codestr);
                        TruffleFile truffleFile = getContext().getPublicTruffleFileRelaxed(path, PythonLanguage.DEFAULT_PYTHON_EXTENSIONS);
                        if (truffleFile.exists() && c.length == truffleFile.size()) {
                            source = Source.newBuilder(PythonLanguage.ID, truffleFile).mimeType(PythonLanguage.MIME_TYPE_SOURCE_FOR_BYTECODE_COMPILE).build();
                        } else {
                            ByteSequence bs = ByteSequence.create(c);
                            source = Source.newBuilder(PythonLanguage.ID, bs, path).mimeType(PythonLanguage.MIME_TYPE_SOURCE_FOR_BYTECODE_COMPILE).build();
                        }
                    } catch (SecurityException | IOException ex) {
                        throw raise.raise(SystemError, ex);
                    }
                } else {
                    try {
                        String c = castStr.execute(codestr);
                        source = PythonLanguage.newSource(getContext(), c, path, true, PythonLanguage.MIME_TYPE_SOURCE_FOR_BYTECODE_COMPILE);
                    } catch (CannotCastException e) {
                        throw raise.raise(TypeError, "expected str or bytes, got '%p'", codestr);
                    }
                }
                CallTarget callTarget = getContext().getEnv().parsePublic(source);
                return factory().createCode((RootCallTarget) callTarget);
            } else {
                return compileNode.execute(frame, codestr, path, mode, 0, false, 2);
            }
        }
    }
}
