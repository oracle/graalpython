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

import static com.oracle.graal.python.PythonLanguage.J_GRAALPYTHON_ID;
import static com.oracle.graal.python.nodes.BuiltinNames.J_EXTEND;
import static com.oracle.graal.python.nodes.BuiltinNames.J___GRAALPYTHON__;
import static com.oracle.graal.python.nodes.BuiltinNames.T___GRAALPYTHON__;
import static com.oracle.graal.python.nodes.BuiltinNames.T___MAIN__;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.T___NAME__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T_INSERT;
import static com.oracle.graal.python.nodes.StringLiterals.J_LLVM_LANGUAGE;
import static com.oracle.graal.python.nodes.StringLiterals.T_COLON;
import static com.oracle.graal.python.nodes.StringLiterals.T_PATH;
import static com.oracle.graal.python.nodes.StringLiterals.T_STRICT;
import static com.oracle.graal.python.nodes.StringLiterals.T_SURROGATEESCAPE;
import static com.oracle.graal.python.nodes.StringLiterals.T_UTF8;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.ImportError;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.SystemError;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.TypeError;
import static com.oracle.graal.python.util.PythonUtils.TS_ENCODING;
import static com.oracle.graal.python.util.PythonUtils.toTruffleStringUncached;
import static com.oracle.graal.python.util.PythonUtils.tsLiteral;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.logging.Level;

import org.graalvm.nativeimage.ImageInfo;
import org.graalvm.polyglot.io.ByteSequence;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.annotations.ArgumentClinic;
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
import com.oracle.graal.python.builtins.objects.common.HashingStorage;
import com.oracle.graal.python.builtins.objects.common.HashingStorageLibrary;
import com.oracle.graal.python.builtins.objects.dict.PDict;
import com.oracle.graal.python.builtins.objects.exception.OSErrorEnum;
import com.oracle.graal.python.builtins.objects.exception.OSErrorEnum.ErrorAndMessagePair;
import com.oracle.graal.python.builtins.objects.function.PFunction;
import com.oracle.graal.python.builtins.objects.generator.PGenerator;
import com.oracle.graal.python.builtins.objects.list.PList;
import com.oracle.graal.python.builtins.objects.method.PMethod;
import com.oracle.graal.python.builtins.objects.module.PythonModule;
import com.oracle.graal.python.builtins.objects.object.PythonObject;
import com.oracle.graal.python.builtins.objects.set.PSet;
import com.oracle.graal.python.builtins.objects.str.StringUtils;
import com.oracle.graal.python.lib.PyObjectCallMethodObjArgs;
import com.oracle.graal.python.lib.PyObjectTypeCheck;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.builtins.FunctionNodes.GetCallTargetNode;
import com.oracle.graal.python.nodes.bytecode.PBytecodeRootNode;
import com.oracle.graal.python.nodes.call.CallNode;
import com.oracle.graal.python.nodes.classes.IsSubtypeNode;
import com.oracle.graal.python.nodes.function.FunctionRootNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonBinaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonBinaryClinicBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonTernaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonUnaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.clinic.ArgumentClinicProvider;
import com.oracle.graal.python.nodes.object.GetClassNode;
import com.oracle.graal.python.nodes.statement.AbstractImportNode;
import com.oracle.graal.python.nodes.subscript.GetItemNode;
import com.oracle.graal.python.nodes.truffle.PythonArithmeticTypes;
import com.oracle.graal.python.nodes.util.CannotCastException;
import com.oracle.graal.python.nodes.util.CastToTruffleStringNode;
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
import com.oracle.truffle.api.nodes.NodeUtil;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.api.strings.TruffleString;
import com.oracle.truffle.llvm.api.Toolchain;

@CoreFunctions(defineModule = J___GRAALPYTHON__, isEager = true)
public class GraalPythonModuleBuiltins extends PythonBuiltins {
    private static final TruffleLogger LOGGER = PythonLanguage.getLogger(GraalPythonModuleBuiltins.class);

    private static final TruffleString T_PATH_HOOKS = tsLiteral("path_hooks");
    private static final TruffleString T_PATH_IMPORTER_CACHE = tsLiteral("path_importer_cache");
    private static final TruffleString T__RUN_MODULE_AS_MAIN = tsLiteral("_run_module_as_main");
    private static final TruffleString T_STDIO_ENCODING = tsLiteral("stdio_encoding");
    private static final TruffleString T_STDIO_ERROR = tsLiteral("stdio_error");

    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return GraalPythonModuleBuiltinsFactory.getFactories();
    }

    @Override
    public void initialize(Python3Core core) {
        super.initialize(core);
        addBuiltinConstant("is_native", ImageInfo.inImageCode());
        PythonContext ctx = core.getContext();
        TruffleString encodingOpt = ctx.getLanguage().getEngineOption(PythonOptions.StandardStreamEncoding);
        TruffleString standardStreamEncoding = null;
        TruffleString standardStreamError = null;
        if (encodingOpt != null && !encodingOpt.isEmpty()) {
            TruffleString[] parts = StringUtils.split(encodingOpt, T_COLON, TruffleString.CodePointLengthNode.getUncached(), TruffleString.IndexOfStringNode.getUncached(),
                            TruffleString.SubstringNode.getUncached(), TruffleString.EqualNode.getUncached());
            if (parts.length > 0) {
                standardStreamEncoding = parts[0].isEmpty() ? T_UTF8 : parts[0];
                standardStreamError = parts.length > 1 && !parts[1].isEmpty() ? parts[1] : T_STRICT;
            }
        }
        if (standardStreamEncoding == null) {
            standardStreamEncoding = T_UTF8;
            standardStreamError = T_SURROGATEESCAPE;
        }

        if (LOGGER.isLoggable(Level.FINE)) {
            LOGGER.fine(String.format("Setting default stdio encoding to %s:%s", standardStreamEncoding, standardStreamError));
        }
        this.addBuiltinConstant(T_STDIO_ENCODING, standardStreamEncoding);
        this.addBuiltinConstant(T_STDIO_ERROR, standardStreamError);
        this.addBuiltinConstant("startup_wall_clock_ts", -1L);
        this.addBuiltinConstant("startup_nano", -1L);
        // we need these during core initialization, they are re-set in postInitialize
        postInitialize(core);
    }

    @Override
    public void postInitialize(Python3Core core) {
        super.postInitialize(core);
        PythonContext context = core.getContext();
        PythonModule mod = core.lookupBuiltinModule(T___GRAALPYTHON__);
        PythonLanguage language = context.getLanguage();
        if (!ImageInfo.inImageBuildtimeCode()) {
            mod.setAttribute(tsLiteral("home"), toTruffleStringUncached(language.getHome()));
        }
        mod.setAttribute(tsLiteral("in_image_buildtime"), ImageInfo.inImageBuildtimeCode());
        mod.setAttribute(tsLiteral("in_image"), ImageInfo.inImageCode());
        TruffleString coreHome = context.getCoreHome();
        TruffleString stdlibHome = context.getStdlibHome();
        TruffleString capiHome = context.getCAPIHome();
        Env env = context.getEnv();
        LanguageInfo llvmInfo = env.getInternalLanguages().get(J_LLVM_LANGUAGE);
        Toolchain toolchain = env.lookup(llvmInfo, Toolchain.class);
        mod.setAttribute(tsLiteral("jython_emulation_enabled"), language.getEngineOption(PythonOptions.EmulateJython));
        mod.setAttribute(tsLiteral("host_import_enabled"), context.getEnv().isHostLookupAllowed());
        mod.setAttribute(tsLiteral("core_home"), coreHome);
        mod.setAttribute(tsLiteral("stdlib_home"), stdlibHome);
        mod.setAttribute(tsLiteral("capi_home"), capiHome);
        mod.setAttribute(tsLiteral("jni_home"), context.getJNIHome());
        mod.setAttribute(tsLiteral("platform_id"), toTruffleStringUncached(toolchain.getIdentifier()));
        mod.setAttribute(tsLiteral("uses_bytecode_interpreter"), context.getOption(PythonOptions.EnableBytecodeInterpreter));
        Object[] arr = convertToObjectArray(PythonOptions.getExecutableList(context));
        PList executableList = PythonObjectFactory.getUncached().createList(arr);
        mod.setAttribute(tsLiteral("executable_list"), executableList);
        mod.setAttribute(tsLiteral("ForeignType"), core.lookupType(PythonBuiltinClassType.ForeignObject));

        if (!context.getOption(PythonOptions.EnableDebuggingBuiltins)) {
            mod.setAttribute(tsLiteral("dump_truffle_ast"), PNone.NO_VALUE);
            mod.setAttribute(tsLiteral("tdebug"), PNone.NO_VALUE);
            mod.setAttribute(tsLiteral("set_storage_strategy"), PNone.NO_VALUE);
            mod.setAttribute(tsLiteral("dump_heap"), PNone.NO_VALUE);
        }
    }

    @TruffleBoundary
    TruffleString getStdIOEncoding() {
        return (TruffleString) getBuiltinConstant(T_STDIO_ENCODING);
    }

    @TruffleBoundary
    TruffleString getStdIOError() {
        return (TruffleString) getBuiltinConstant(T_STDIO_ERROR);
    }

    /**
     * Entry point for executing a path using the launcher, e.g. {@code python foo.py}
     */
    @Builtin(name = "run_path")
    @GenerateNodeFactory
    abstract static class RunPathNode extends PythonBuiltinNode {

        public static final TruffleString T_RUNPY = tsLiteral("runpy");

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
            TruffleString inputFilePath = context.getOption(PythonOptions.InputFilePath);
            PythonModule sysModule = context.getSysModule();
            boolean needsMainImporter = !inputFilePath.isEmpty() && getImporter(sysModule, inputFilePath);
            if (needsMainImporter) {
                Object sysPath = sysModule.getAttribute(T_PATH);
                PyObjectCallMethodObjArgs.getUncached().execute(null, sysPath, T_INSERT, 0, inputFilePath);
            } else {
                // This is normally done by PythonLanguage, but is suppressed when we have a path
                // argument
                context.addSysPath0();
            }

            if (needsMainImporter) {
                runModule(T___MAIN__, false);
            } else {
                runFile(context, inputFilePath);
            }
            return PNone.NONE;
        }

        // Equivalent of CPython's pymain_run_file and pymain_run_stdin
        private void runFile(PythonContext context, TruffleString inputFilePath) {
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
                ErrorAndMessagePair error = OSErrorEnum.fromException(e, TruffleString.EqualNode.getUncached());
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
        private static void runModule(TruffleString module, boolean setArgv0) {
            Object runpy = AbstractImportNode.importModule(T_RUNPY);
            PyObjectCallMethodObjArgs.getUncached().execute(null, runpy, T__RUN_MODULE_AS_MAIN, module, setArgv0);
        }

        // Equivalent of CPython's pymain_get_importer, but returns a boolean
        private static boolean getImporter(PythonModule sysModule, TruffleString inputFilePath) {
            Object importer = null;
            Object pathHooks = sysModule.getAttribute(T_PATH_HOOKS);
            Object pathImporterCache = sysModule.getAttribute(T_PATH_IMPORTER_CACHE);
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

    private static Object[] convertToObjectArray(TruffleString[] arr) {
        Object[] objectArr = new Object[arr.length];
        System.arraycopy(arr, 0, objectArr, 0, arr.length);
        return objectArr;
    }

    @Builtin(name = "read_file", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class ReadFileNode extends PythonUnaryBuiltinNode {
        @Specialization
        public PBytes doString(VirtualFrame frame, TruffleString filename,
                        @Cached TruffleString.EqualNode eqNode) {
            try {
                TruffleFile file = getContext().getPublicTruffleFileRelaxed(filename, PythonLanguage.T_DEFAULT_PYTHON_EXTENSIONS);
                byte[] bytes = file.readAllBytes();
                return factory().createBytes(bytes);
            } catch (Exception ex) {
                ErrorAndMessagePair errAndMsg = OSErrorEnum.fromException(ex, eqNode);
                throw raiseOSError(frame, errAndMsg.oserror.getNumber(), errAndMsg.message);
            }
        }

        @Specialization
        public Object doGeneric(VirtualFrame frame, Object filename,
                        @Cached CastToTruffleStringNode castToTruffleStringNode,
                        @Cached TruffleString.EqualNode eqNode) {
            return doString(frame, castToTruffleStringNode.execute(filename), eqNode);
        }
    }

    @Builtin(name = "dump_truffle_ast", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class DumpTruffleAstNode extends PythonUnaryBuiltinNode {
        @Specialization
        @TruffleBoundary
        public TruffleString doIt(PFunction func) {
            return toTruffleStringUncached(NodeUtil.printTreeToString(GetCallTargetNode.getUncached().execute(func).getRootNode()));
        }

        @Specialization(guards = "isFunction(method.getFunction())")
        @TruffleBoundary
        public TruffleString doIt(PMethod method) {
            return toTruffleStringUncached(NodeUtil.printTreeToString(GetCallTargetNode.getUncached().execute(method).getRootNode()));
        }

        @Specialization
        @TruffleBoundary
        public TruffleString doIt(PGenerator gen) {
            return toTruffleStringUncached(NodeUtil.printTreeToString(gen.getCurrentCallTarget().getRootNode()));
        }

        @Specialization
        @TruffleBoundary
        public TruffleString doIt(PCode code) {
            return toTruffleStringUncached(NodeUtil.printTreeToString(CodeNodes.GetCodeRootNode.getUncached().execute(code)));
        }

        @Fallback
        @TruffleBoundary
        public TruffleString doit(Object object) {
            return toTruffleStringUncached("truffle ast dump not supported for " + object.toString());
        }

        protected static boolean isFunction(Object callee) {
            return callee instanceof PFunction;
        }
    }

    @Builtin(name = "current_import", minNumOfPositionalArgs = 0)
    @GenerateNodeFactory
    public abstract static class CurrentImport extends PythonBuiltinNode {
        @Specialization
        TruffleString doIt() {
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
                TruffleString moduleName = (TruffleString) getNameNode.execute(frame, globals, T___NAME__);
                builtinModule = getCore().lookupBuiltinModule(moduleName);
                assert builtinModule != null;
            }
            return factory().createBuiltinMethod(builtinModule, builtinFunc);
        }

        @TruffleBoundary
        public synchronized PFunction convertToBuiltin(PFunction func) {
            RootNode rootNode = CodeNodes.GetCodeRootNode.getUncached().execute(func.getCode());
            if (rootNode instanceof FunctionRootNode) {
                ((FunctionRootNode) rootNode).setPythonInternal(true);
            } else if (rootNode instanceof PBytecodeRootNode) {
                ((PBytecodeRootNode) rootNode).setPythonInternal(true);
            }
            func.setBuiltin(true);
            return func;
        }
    }

    @Builtin(name = "builtin_method", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class BuiltinMethodNode extends PythonUnaryBuiltinNode {
        @Specialization
        public Object doIt(PFunction func,
                        @Cached CodeNodes.GetCodeRootNode getRootNode) {
            RootNode rootNode = getRootNode.execute(func.getCode());
            if (rootNode instanceof FunctionRootNode) {
                ((FunctionRootNode) rootNode).setPythonInternal(true);
            } else if (rootNode instanceof PBytecodeRootNode) {
                ((PBytecodeRootNode) rootNode).setPythonInternal(true);
            }
            return func;
        }
    }

    @Builtin(name = "get_toolchain_tool_path", minNumOfPositionalArgs = 1)
    @TypeSystemReference(PythonArithmeticTypes.class)
    @GenerateNodeFactory
    public abstract static class GetToolPathNode extends PythonUnaryBuiltinNode {
        @Specialization
        @TruffleBoundary
        protected Object getToolPath(TruffleString tool) {
            Env env = getContext().getEnv();
            LanguageInfo llvmInfo = env.getInternalLanguages().get(J_LLVM_LANGUAGE);
            Toolchain toolchain = env.lookup(llvmInfo, Toolchain.class);
            TruffleFile toolPath = toolchain.getToolPath(tool.toJavaStringUncached());
            if (toolPath == null) {
                return PNone.NONE;
            }
            return toTruffleStringUncached(toolPath.toString());
        }
    }

    @Builtin(name = "get_toolchain_paths", minNumOfPositionalArgs = 1)
    @TypeSystemReference(PythonArithmeticTypes.class)
    @GenerateNodeFactory
    public abstract static class GetToolchainPathsNode extends PythonUnaryBuiltinNode {
        @Specialization
        @TruffleBoundary
        protected Object getPaths(TruffleString key) {
            Env env = getContext().getEnv();
            LanguageInfo llvmInfo = env.getInternalLanguages().get(J_LLVM_LANGUAGE);
            Toolchain toolchain = env.lookup(llvmInfo, Toolchain.class);
            List<TruffleFile> pathsList = toolchain.getPaths(key.toJavaStringUncached());
            if (pathsList == null) {
                return factory().createTuple(PythonUtils.EMPTY_OBJECT_ARRAY);
            }
            Object[] paths = new Object[pathsList.size()];
            int i = 0;
            for (TruffleFile f : pathsList) {
                paths[i++] = toTruffleStringUncached(f.toString());
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
        TruffleString posixModuleBackend(
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
        Object doSet(PSet set, TruffleString strategyName) {
            validate(set.getDictStorage());
            set.setDictStorage(getStrategy(strategyName, getLanguage()));
            return set;
        }

        @Specialization
        Object doDict(PDict dict, TruffleString strategyName) {
            validate(dict.getDictStorage());
            dict.setDictStorage(getStrategy(strategyName, getLanguage()));
            return dict;
        }

        private HashingStorage getStrategy(TruffleString tname, PythonLanguage lang) {
            String name = tname.toJavaStringUncached();
            switch (name) {
                case "empty":
                    return EmptyStorage.INSTANCE;
                case "dynamicobject":
                    return new DynamicObjectStorage(lang);
                case "economicmap":
                    return EconomicMapStorage.create();
                default:
                    throw raise(PythonBuiltinClassType.ValueError, ErrorMessages.UNKNOWN_STORAGE_STRATEGY);
            }
        }

        private void validate(HashingStorage dictStorage) {
            if (HashingStorageLibrary.getUncached().length(dictStorage) != 0) {
                throw raise(PythonBuiltinClassType.ValueError, ErrorMessages.SHOULD_BE_USED_ONLY_NEW_SETS);
            }
        }
    }

    @Builtin(name = J_EXTEND, minNumOfPositionalArgs = 1, doc = "Extends Java class and return HostAdapterCLass")
    @GenerateNodeFactory
    public abstract static class JavaExtendNode extends PythonUnaryBuiltinNode {
        @Specialization
        Object doIt(Object value,
                        @CachedLibrary(limit = "3") InteropLibrary lib) {
            if (ImageInfo.inImageBuildtimeCode()) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                throw new UnsupportedOperationException(ErrorMessages.CANT_EXTEND_JAVA_CLASS_NOT_JVM.toJavaStringUncached());
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
                throw raise(TypeError, PythonUtils.getMessage(ex), ex);
            }
        }

        protected static boolean isType(Object obj, Env env, InteropLibrary lib) {
            return env.isHostObject(obj) && (env.isHostSymbol(obj) || lib.isMetaObject(obj));
        }

    }

    @Builtin(name = "dis", minNumOfPositionalArgs = 1, parameterNames = {"obj", "quickened"}, doc = "Helper to disassemble code objects if running with the bytecode interpreter")
    @ArgumentClinic(name = "quickened", conversion = ArgumentClinic.ClinicConversion.Boolean, defaultValue = "false")
    @GenerateNodeFactory
    public abstract static class BCIDisNode extends PythonBinaryClinicBuiltinNode {
        @Specialization
        Object doMethod(PMethod method, boolean quickened) {
            final Object function = method.getFunction();
            if (function instanceof PFunction) {
                return doFunction((PFunction) function, quickened);
            }
            return PNone.NONE;
        }

        @Specialization
        Object doFunction(PFunction function, boolean quickened) {
            return doCode(function.getCode(), quickened);
        }

        @Specialization
        @TruffleBoundary
        Object doCode(PCode code, boolean quickened) {
            return toTruffleStringUncached(code.toDisassembledString(quickened));
        }

        @Fallback
        @SuppressWarnings("unused")
        Object doObject(Object value, Object quickened) {
            return PNone.NONE;
        }

        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return GraalPythonModuleBuiltinsClinicProviders.BCIDisNodeClinicProviderGen.INSTANCE;
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
        TruffleString doit(VirtualFrame frame,
                        @Cached TruffleString.FromJavaStringNode fromJavaStringNode,
                        @Cached TruffleString.EqualNode eqNode) {
            TruffleFile tempFile;
            try {
                PythonContext context = getContext();
                tempFile = context.getEnv().createTempFile(context.getEnv().getCurrentWorkingDirectory(), J_GRAALPYTHON_ID, ".hprof");
                tempFile.delete();
            } catch (IOException e) {
                throw raiseOSError(frame, e, eqNode);
            }
            PythonUtils.dumpHeap(tempFile.getPath());
            return fromJavaStringNode.execute(tempFile.getPath(), TS_ENCODING);
        }
    }

    @Builtin(name = "compile", parameterNames = {"codestr", "path", "mode"})
    @GenerateNodeFactory
    abstract static class CompileNode extends PythonTernaryBuiltinNode {
        private static final TruffleString T_PYC = tsLiteral("pyc");

        @Specialization
        PCode compile(VirtualFrame frame, Object codestr, TruffleString path, TruffleString mode,
                        @Cached PRaiseNode raise,
                        @Cached BytesNodes.ToBytesNode toBytes,
                        @Cached CastToTruffleStringNode castStr,
                        @Cached TruffleString.EqualNode equalNode,
                        @Cached("create(false)") BuiltinFunctions.CompileNode compileNode) {
            if (equalNode.execute(mode, T_PYC, TS_ENCODING)) {
                Source source;
                if (codestr instanceof PBytesLike) {
                    try {
                        source = getSource(path, toBytes.execute((PBytesLike) codestr));
                    } catch (SecurityException | IOException ex) {
                        throw raise.raise(SystemError, ex);
                    }
                } else {
                    try {
                        source = getSource(path, castStr.execute(codestr));
                    } catch (CannotCastException e) {
                        throw raise.raise(TypeError, ErrorMessages.EXPECTED_STR_OR_BYTES, codestr);
                    }
                }
                CallTarget callTarget = createCallTarget(source);
                return factory().createCode((RootCallTarget) callTarget);
            } else {
                return compileNode.compile(frame, codestr, path, mode, 2);
            }
        }

        @TruffleBoundary
        private CallTarget createCallTarget(Source source) {
            return getContext().getEnv().parsePublic(source);
        }

        @TruffleBoundary
        private Source getSource(TruffleString path, TruffleString code) {
            return PythonLanguage.newSource(getContext(), code, path, true, PythonLanguage.MIME_TYPE_SOURCE_FOR_BYTECODE_COMPILE);
        }

        @TruffleBoundary
        private Source getSource(TruffleString path, byte[] code) throws IOException {
            TruffleFile truffleFile = getContext().getPublicTruffleFileRelaxed(path, PythonLanguage.T_DEFAULT_PYTHON_EXTENSIONS);
            if (truffleFile.exists() && code.length == truffleFile.size()) {
                return Source.newBuilder(PythonLanguage.ID, truffleFile).mimeType(PythonLanguage.MIME_TYPE_SOURCE_FOR_BYTECODE_COMPILE).build();
            } else {
                ByteSequence bs = ByteSequence.create(code);
                return Source.newBuilder(PythonLanguage.ID, bs, path.toJavaStringUncached()).mimeType(PythonLanguage.MIME_TYPE_SOURCE_FOR_BYTECODE_COMPILE).build();
            }
        }
    }
}
