/*
 * Copyright (c) 2019, 2024, Oracle and/or its affiliates. All rights reserved.
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
import static com.oracle.graal.python.nodes.BuiltinNames.T_SHA3;
import static com.oracle.graal.python.nodes.BuiltinNames.T___GRAALPYTHON__;
import static com.oracle.graal.python.nodes.BuiltinNames.T___MAIN__;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.T___NAME__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T_INSERT;
import static com.oracle.graal.python.nodes.StringLiterals.J_LLVM_LANGUAGE;
import static com.oracle.graal.python.nodes.StringLiterals.T_COLON;
import static com.oracle.graal.python.nodes.StringLiterals.T_JAVA;
import static com.oracle.graal.python.nodes.StringLiterals.T_LLVM_LANGUAGE;
import static com.oracle.graal.python.nodes.StringLiterals.T_NATIVE;
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

import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.lang.management.ManagementFactory;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.logging.Level;

import org.graalvm.home.Version;
import org.graalvm.nativeimage.ImageInfo;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.annotations.ArgumentClinic;
import com.oracle.graal.python.builtins.Builtin;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.Python3Core;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.modules.GraalPythonModuleBuiltinsFactory.DebugNodeFactory;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.array.PArray;
import com.oracle.graal.python.builtins.objects.bytes.PBytes;
import com.oracle.graal.python.builtins.objects.bytes.PBytesLike;
import com.oracle.graal.python.builtins.objects.cext.PythonAbstractNativeObject;
import com.oracle.graal.python.builtins.objects.cext.capi.CApiContext;
import com.oracle.graal.python.builtins.objects.cext.capi.PySequenceArrayWrapper.ToNativeStorageNode;
import com.oracle.graal.python.builtins.objects.code.CodeNodes;
import com.oracle.graal.python.builtins.objects.code.PCode;
import com.oracle.graal.python.builtins.objects.common.DynamicObjectStorage;
import com.oracle.graal.python.builtins.objects.common.EconomicMapStorage;
import com.oracle.graal.python.builtins.objects.common.EmptyStorage;
import com.oracle.graal.python.builtins.objects.common.HashingStorage;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodes.HashingStorageLen;
import com.oracle.graal.python.builtins.objects.dict.PDict;
import com.oracle.graal.python.builtins.objects.exception.OSErrorEnum;
import com.oracle.graal.python.builtins.objects.exception.OSErrorEnum.ErrorAndMessagePair;
import com.oracle.graal.python.builtins.objects.function.PBuiltinFunction;
import com.oracle.graal.python.builtins.objects.function.PFunction;
import com.oracle.graal.python.builtins.objects.function.PKeyword;
import com.oracle.graal.python.builtins.objects.generator.PGenerator;
import com.oracle.graal.python.builtins.objects.list.PList;
import com.oracle.graal.python.builtins.objects.method.PBuiltinMethod;
import com.oracle.graal.python.builtins.objects.method.PMethod;
import com.oracle.graal.python.builtins.objects.module.PythonModule;
import com.oracle.graal.python.builtins.objects.object.PythonObject;
import com.oracle.graal.python.builtins.objects.set.PSet;
import com.oracle.graal.python.builtins.objects.str.StringUtils;
import com.oracle.graal.python.builtins.objects.tuple.PTuple;
import com.oracle.graal.python.builtins.objects.type.PythonClass;
import com.oracle.graal.python.builtins.objects.type.TypeNodes.CreateTypeNode;
import com.oracle.graal.python.lib.PyObjectCallMethodObjArgs;
import com.oracle.graal.python.lib.PyObjectGetItem;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PConstructAndRaiseNode;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.builtins.FunctionNodes.GetCallTargetNode;
import com.oracle.graal.python.nodes.bytecode.PBytecodeRootNode;
import com.oracle.graal.python.nodes.bytecode_dsl.PBytecodeDSLRootNode;
import com.oracle.graal.python.nodes.call.CallNode;
import com.oracle.graal.python.nodes.classes.IsSubtypeNode;
import com.oracle.graal.python.nodes.function.BuiltinFunctionRootNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonBinaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonBinaryClinicBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonQuaternaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonUnaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.clinic.ArgumentClinicProvider;
import com.oracle.graal.python.nodes.object.GetClassNode;
import com.oracle.graal.python.nodes.statement.AbstractImportNode;
import com.oracle.graal.python.nodes.truffle.PythonArithmeticTypes;
import com.oracle.graal.python.nodes.util.CastToTruffleStringNode;
import com.oracle.graal.python.runtime.PosixSupportLibrary;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.graal.python.runtime.PythonOptions;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.graal.python.runtime.exception.PythonExitException;
import com.oracle.graal.python.runtime.object.PythonObjectFactory;
import com.oracle.graal.python.runtime.sequence.PSequence;
import com.oracle.graal.python.runtime.sequence.storage.NativeSequenceStorage;
import com.oracle.graal.python.runtime.sequence.storage.SequenceStorage;
import com.oracle.graal.python.util.PythonUtils;
import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.TruffleFile;
import com.oracle.truffle.api.TruffleLanguage.Env;
import com.oracle.truffle.api.TruffleLogger;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.NeverDefault;
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
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.api.strings.TruffleString;
import com.oracle.truffle.llvm.api.Toolchain;

@CoreFunctions(defineModule = J___GRAALPYTHON__, isEager = true)
public final class GraalPythonModuleBuiltins extends PythonBuiltins {
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
        addBuiltinConstant("is_bytecode_dsl_interpreter", PythonOptions.ENABLE_BYTECODE_DSL_INTERPRETER);
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
            mod.setAttribute(tsLiteral("home"), context.getLanguageHome());
        }
        mod.setAttribute(tsLiteral("in_image_buildtime"), ImageInfo.inImageBuildtimeCode());
        mod.setAttribute(tsLiteral("in_image"), ImageInfo.inImageCode());
        TruffleString coreHome = context.getCoreHome();
        TruffleString stdlibHome = context.getStdlibHome();
        TruffleString capiHome = context.getCAPIHome();
        mod.setAttribute(tsLiteral("jython_emulation_enabled"), language.getEngineOption(PythonOptions.EmulateJython));
        mod.setAttribute(tsLiteral("host_import_enabled"), context.getEnv().isHostLookupAllowed());
        mod.setAttribute(tsLiteral("core_home"), coreHome);
        mod.setAttribute(tsLiteral("stdlib_home"), stdlibHome);
        mod.setAttribute(tsLiteral("capi_home"), capiHome);
        mod.setAttribute(tsLiteral("jni_home"), context.getJNIHome());
        Object[] arr = convertToObjectArray(PythonOptions.getExecutableList(context));
        PList executableList = PythonObjectFactory.getUncached().createList(arr);
        mod.setAttribute(tsLiteral("executable_list"), executableList);
        mod.setAttribute(tsLiteral("venvlauncher_command"), context.getOption(PythonOptions.VenvlauncherCommand));
        mod.setAttribute(tsLiteral("ForeignType"), core.lookupType(PythonBuiltinClassType.ForeignObject));
        mod.setAttribute(tsLiteral("use_system_toolchain"), context.getOption(PythonOptions.UseSystemToolchain));
        mod.setAttribute(tsLiteral("ext_mode"), context.getOption(PythonOptions.NativeModules) ? T_NATIVE : T_LLVM_LANGUAGE);

        if (!context.getOption(PythonOptions.EnableDebuggingBuiltins)) {
            mod.setAttribute(tsLiteral("dump_truffle_ast"), PNone.NO_VALUE);
            mod.setAttribute(tsLiteral("tdebug"), PNone.NO_VALUE);
            mod.setAttribute(tsLiteral("set_storage_strategy"), PNone.NO_VALUE);
            mod.setAttribute(tsLiteral("storage_to_native"), PNone.NO_VALUE);
            mod.setAttribute(tsLiteral("dump_heap"), PNone.NO_VALUE);
            mod.setAttribute(tsLiteral("is_native_object"), PNone.NO_VALUE);
        }
        if (PythonOptions.WITHOUT_PLATFORM_ACCESS || !context.getOption(PythonOptions.RunViaLauncher)) {
            mod.setAttribute(tsLiteral("list_files"), PNone.NO_VALUE);
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
                PyObjectCallMethodObjArgs.getUncached().execute(null, null, sysPath, T_INSERT, 0, inputFilePath);
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
            PyObjectCallMethodObjArgs.executeUncached(runpy, T__RUN_MODULE_AS_MAIN, module, setArgv0);
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
                            if (!IsSubtypeNode.getUncached().execute(GetClassNode.executeUncached(e.getUnreifiedException()), ImportError)) {
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
        PBytes doString(VirtualFrame frame, Object filenameObj,
                        @Bind("this") Node inliningTarget,
                        @Cached CastToTruffleStringNode castToTruffleStringNode,
                        @Cached TruffleString.EqualNode eqNode,
                        @Cached PConstructAndRaiseNode.Lazy constructAndRaiseNode,
                        @Cached PythonObjectFactory factory) {
            try {
                TruffleString filename = castToTruffleStringNode.execute(inliningTarget, filenameObj);
                TruffleFile file = getContext().getPublicTruffleFileRelaxed(filename, PythonLanguage.T_DEFAULT_PYTHON_EXTENSIONS);
                byte[] bytes = file.readAllBytes();
                return factory.createBytes(bytes);
            } catch (Exception ex) {
                ErrorAndMessagePair errAndMsg = OSErrorEnum.fromException(ex, eqNode);
                throw constructAndRaiseNode.get(inliningTarget).raiseOSError(frame, errAndMsg.oserror.getNumber(), errAndMsg.message);
            }
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
            return toTruffleStringUncached(NodeUtil.printTreeToString(CodeNodes.GetCodeRootNode.executeUncached(code)));
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

    @Builtin(name = "blackhole", minNumOfPositionalArgs = 1, maxNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class Blackhole extends PythonUnaryBuiltinNode {
        @Specialization
        Object doIt(Object value) {
            CompilerDirectives.blackhole(value);
            return PNone.NONE;
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

        @NeverDefault
        public static DebugNode create() {
            return DebugNodeFactory.create(null);
        }
    }

    @Builtin(name = "builtin", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class BuiltinNode extends PythonUnaryBuiltinNode {
        @Specialization
        public Object doIt(VirtualFrame frame, PFunction func,
                        @Bind("this") Node inliningTarget,
                        @Cached PyObjectGetItem getItem,
                        @Cached PythonObjectFactory factory) {
            PFunction builtinFunc = convertToBuiltin(func);
            PythonObject globals = func.getGlobals();
            PythonModule builtinModule;
            if (globals instanceof PythonModule) {
                builtinModule = (PythonModule) globals;
            } else {
                TruffleString moduleName = (TruffleString) getItem.execute(frame, inliningTarget, globals, T___NAME__);
                builtinModule = getContext().lookupBuiltinModule(moduleName);
                assert builtinModule != null;
            }
            return factory.createBuiltinMethod(builtinModule, builtinFunc);
        }

        @TruffleBoundary
        public synchronized PFunction convertToBuiltin(PFunction func) {
            RootNode rootNode = CodeNodes.GetCodeRootNode.executeUncached(func.getCode());
            if (PythonOptions.ENABLE_BYTECODE_DSL_INTERPRETER) {
                if (rootNode instanceof PBytecodeDSLRootNode r) {
                    r.setPythonInternal(true);
                }
            } else if (rootNode instanceof PBytecodeRootNode r) {
                r.setPythonInternal(true);
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
                        @Bind("this") Node inliningTarget,
                        @Cached CodeNodes.GetCodeRootNode getRootNode) {
            RootNode rootNode = getRootNode.execute(inliningTarget, func.getCode());
            if (PythonOptions.ENABLE_BYTECODE_DSL_INTERPRETER) {
                if (rootNode instanceof PBytecodeDSLRootNode r) {
                    r.setPythonInternal(true);
                }
            } else if (rootNode instanceof PBytecodeRootNode r) {
                r.setPythonInternal(true);
            }
            return func;
        }
    }

    @Builtin(name = "force_split_direct_calls", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class ForceSplitDirectCallsNode extends PythonUnaryBuiltinNode {
        @Specialization
        public Object doIt(PFunction func) {
            func.setForceSplitDirectCalls(true);
            return func;
        }
    }

    @Builtin(name = "get_toolchain_tools_for_venv")
    @TypeSystemReference(PythonArithmeticTypes.class)
    @GenerateNodeFactory
    public abstract static class GetToolchainToolsForVenv extends PythonBuiltinNode {
        private static final class Tool {
            final String name;
            final boolean isVariableName;
            final Object[] targets;

            public Tool(String name, boolean isVariableName, Object[] targets) {
                this.name = name;
                this.isVariableName = isVariableName;
                this.targets = targets;
            }

            static Tool forVariable(String name, Object... targets) {
                return new Tool(name, true, targets);
            }

            static Tool forBinary(String name, Object... targets) {
                return new Tool(name, true, targets);
            }
        }

        static final Tool[] tools = new Tool[]{
                        Tool.forVariable("AR", tsLiteral("ar")),
                        Tool.forVariable("RANLIB", tsLiteral("ranlib")),
                        Tool.forVariable("NM", tsLiteral("nm")),
                        Tool.forVariable("LD", tsLiteral("ld.lld"), tsLiteral("ld"), tsLiteral("lld")),
                        Tool.forVariable("CC", tsLiteral("clang"), tsLiteral("cc")),
                        Tool.forVariable("CXX", tsLiteral("clang++"), tsLiteral("c++")),
                        Tool.forVariable("FC", tsLiteral("graalvm-flang"), tsLiteral("flang-new"), tsLiteral("flang")),
                        Tool.forBinary("llvm-as", tsLiteral("as")),
                        Tool.forBinary("clang-cl", tsLiteral("cl")),
                        Tool.forBinary("clang-cpp", tsLiteral("cpp")),
        };

        @Specialization
        @TruffleBoundary
        Object getToolPath() {
            PythonObjectFactory factory = PythonObjectFactory.getUncached();
            Env env = getContext().getEnv();
            LanguageInfo llvmInfo = env.getInternalLanguages().get(J_LLVM_LANGUAGE);
            Toolchain toolchain = env.lookup(llvmInfo, Toolchain.class);
            List<TruffleFile> toolchainPaths = toolchain.getPaths("PATH");
            EconomicMapStorage storage = EconomicMapStorage.create(tools.length);
            for (Tool tool : tools) {
                String path = null;
                if (tool.isVariableName) {
                    TruffleFile toolPath = toolchain.getToolPath(tool.name);
                    if (toolPath != null) {
                        path = toolPath.getAbsoluteFile().getPath();
                    }
                } else {
                    for (TruffleFile toolchainPath : toolchainPaths) {
                        LOGGER.finest(() -> " Testing path " + toolchainPath.getPath() + " for tool " + tool.name);
                        TruffleFile pathToTest = toolchainPath.resolve(tool.name);
                        if (pathToTest.exists()) {
                            path = pathToTest.getAbsoluteFile().getPath();
                            break;
                        }
                    }
                }
                if (path != null) {
                    storage.putUncached(toTruffleStringUncached(path), factory.createTuple(tool.targets));
                } else {
                    LOGGER.fine("Could not locate tool " + tool.name);
                }
            }
            return factory.createDict(storage);
        }
    }

    /*
     * Internal check used in tests only to check that we are running through managed launcher.
     */
    @Builtin(name = "is_managed_launcher")
    @TypeSystemReference(PythonArithmeticTypes.class)
    @GenerateNodeFactory
    public abstract static class IsManagedLauncher extends PythonBuiltinNode {
        @Specialization
        @TruffleBoundary
        protected boolean isManaged() {
            // The best approximation for now
            return !getContext().getEnv().isNativeAccessAllowed() && getContext().getOption(PythonOptions.RunViaLauncher);
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
            return toTruffleStringUncached(toolPath.toString().replace("\\", "/"));
        }
    }

    @Builtin(name = "get_platform_id", minNumOfPositionalArgs = 0)
    @TypeSystemReference(PythonArithmeticTypes.class)
    @GenerateNodeFactory
    public abstract static class GetPlatformId extends PythonBuiltinNode {
        @Specialization
        @TruffleBoundary
        protected TruffleString getPlatformId() {
            return getContext().getPlatformId();
        }

    }

    @Builtin(name = "get_toolchain_paths", minNumOfPositionalArgs = 1)
    @TypeSystemReference(PythonArithmeticTypes.class)
    @GenerateNodeFactory
    public abstract static class GetToolchainPathsNode extends PythonUnaryBuiltinNode {
        @Specialization
        @TruffleBoundary
        protected Object getToolPath(TruffleString tool) {
            Env env = getContext().getEnv();
            LanguageInfo llvmInfo = env.getInternalLanguages().get(J_LLVM_LANGUAGE);
            Toolchain toolchain = env.lookup(llvmInfo, Toolchain.class);
            List<TruffleFile> toolPaths = toolchain.getPaths(tool.toJavaStringUncached());
            if (toolPaths == null) {
                return PNone.NONE;
            }
            Object[] pathNames = new Object[toolPaths.size()];
            for (int i = 0; i < pathNames.length; i++) {
                pathNames[i] = toTruffleStringUncached(toolPaths.get(i).toString().replace("\\", "/"));
            }
            return PythonObjectFactory.getUncached().createList(pathNames);
        }
    }

    @Builtin(name = "determine_system_toolchain", maxNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class DetermineSystemToolchain extends PythonUnaryBuiltinNode {
        /**
         * This is derived from {@code distutils.unixccompiler._is_gcc}
         */
        private static final String[] C_COMPILER_PRECEDENCE = {"gcc", "clang"};
        private static final String[] CXX_COMPILER_PRECEDENCE = {"g++", "clang++"};

        private static final PKeyword[] GENERIC_TOOLCHAIN = {
                        new PKeyword(tsLiteral("CC"), tsLiteral("cc")),
                        new PKeyword(tsLiteral("CXX"), tsLiteral("c++")),
                        new PKeyword(tsLiteral("AR"), tsLiteral("ar")),
                        new PKeyword(tsLiteral("RANLIB"), tsLiteral("ranlib")),
                        new PKeyword(tsLiteral("LD"), tsLiteral("ld")),
                        new PKeyword(tsLiteral("NM"), tsLiteral("nm"))
        };

        @Specialization
        static PDict doGeneric(@SuppressWarnings("unused") Object unused,
                        @Cached PythonObjectFactory factory) {
            return factory.createDict(fromToolchain());
        }

        @TruffleBoundary
        private static PKeyword[] fromToolchain() {
            PKeyword[] result = GENERIC_TOOLCHAIN;
            int id = which();
            if (id >= 0) {
                assert id < C_COMPILER_PRECEDENCE.length;
                result = Arrays.copyOf(GENERIC_TOOLCHAIN, GENERIC_TOOLCHAIN.length);
                result[0] = new PKeyword(tsLiteral("CC"), tsLiteral(C_COMPILER_PRECEDENCE[id]));
                result[1] = new PKeyword(tsLiteral("CXX"), tsLiteral(CXX_COMPILER_PRECEDENCE[id]));
            }
            return result;
        }

        private static int which() {
            CompilerAsserts.neverPartOfCompilation();
            String path = System.getenv("PATH");
            if (path != null) {
                for (int i = 0; i < C_COMPILER_PRECEDENCE.length; i++) {
                    int last = 0;
                    for (int j = path.indexOf(File.pathSeparatorChar); j != -1; j = path.indexOf(File.pathSeparatorChar, last)) {
                        Path resolvedProgramName = Paths.get(path.substring(last, j)).resolve(C_COMPILER_PRECEDENCE[i]);
                        if (Files.isExecutable(resolvedProgramName)) {
                            return i;
                        }
                        /*
                         * next start is the char after the separator because we have "path0:path1"
                         * and 'i' points to ':'
                         */
                        last = j + 1;
                    }
                }
            }
            return -1;
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

    @Builtin(name = "sha3_module_backend", minNumOfPositionalArgs = 0)
    @GenerateNodeFactory
    public abstract static class Sha3ModuleBackendNode extends PythonBuiltinNode {
        @Specialization
        TruffleString sha3ModuleBackend() {
            return getContext().lookupBuiltinModule(T_SHA3) == null ? T_NATIVE : T_JAVA;
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
        @TruffleBoundary
        Object doSet(PSet set, TruffleString strategyName) {
            validate(set.getDictStorage());
            set.setDictStorage(getStrategy(strategyName, getLanguage()));
            return set;
        }

        @Specialization
        @TruffleBoundary
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
                    throw PRaiseNode.raiseUncached(this, PythonBuiltinClassType.ValueError, ErrorMessages.UNKNOWN_STORAGE_STRATEGY);
            }
        }

        private void validate(HashingStorage dictStorage) {
            if (HashingStorageLen.executeUncached(dictStorage) != 0) {
                throw PRaiseNode.raiseUncached(this, PythonBuiltinClassType.ValueError, ErrorMessages.SHOULD_BE_USED_ONLY_NEW_SETS);
            }
        }
    }

    @Builtin(name = "storage_to_native", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class StorageToNative extends PythonUnaryBuiltinNode {
        @Specialization
        @TruffleBoundary
        Object toNative(PBytesLike bytes) {
            CApiContext.ensureCapiWasLoaded();
            NativeSequenceStorage newStorage = ToNativeStorageNode.executeUncached(bytes.getSequenceStorage(), true);
            bytes.setSequenceStorage(newStorage);
            return bytes;
        }

        @Specialization
        @TruffleBoundary
        Object toNative(PArray array) {
            CApiContext.ensureCapiWasLoaded();
            NativeSequenceStorage newStorage = ToNativeStorageNode.executeUncached(array.getSequenceStorage(), true);
            array.setSequenceStorage(newStorage);
            return array;
        }

        @Specialization
        @TruffleBoundary
        Object toNative(PSequence sequence) {
            CApiContext.ensureCapiWasLoaded();
            NativeSequenceStorage newStorage = ToNativeStorageNode.executeUncached(sequence.getSequenceStorage(), false);
            sequence.setSequenceStorage(newStorage);
            return sequence;
        }
    }

    @Builtin(name = J_EXTEND, minNumOfPositionalArgs = 1, doc = "Extends Java class and return HostAdapterCLass")
    @GenerateNodeFactory
    public abstract static class JavaExtendNode extends PythonUnaryBuiltinNode {
        @Specialization
        static Object doIt(Object value,
                        @Bind("this") Node inliningTarget,
                        @CachedLibrary(limit = "3") InteropLibrary lib,
                        @Cached PRaiseNode.Lazy raiseNode) {
            if (ImageInfo.inImageBuildtimeCode()) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                throw new UnsupportedOperationException(ErrorMessages.CANT_EXTEND_JAVA_CLASS_NOT_JVM.toJavaStringUncached());
            }
            if (ImageInfo.inImageRuntimeCode()) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                throw raiseNode.get(inliningTarget).raise(SystemError, ErrorMessages.CANT_EXTEND_JAVA_CLASS_NOT_JVM);
            }

            Env env = PythonContext.get(inliningTarget).getEnv();
            if (!isType(value, env, lib)) {
                throw raiseNode.get(inliningTarget).raise(TypeError, ErrorMessages.CANT_EXTEND_JAVA_CLASS_NOT_TYPE, value);
            }

            try {
                return env.createHostAdapter(new Object[]{value});
            } catch (Exception ex) {
                throw raiseNode.get(inliningTarget).raise(TypeError, PythonUtils.getMessage(ex), ex);
            }
        }

        protected static boolean isType(Object obj, Env env, InteropLibrary lib) {
            return env.isHostObject(obj) && (env.isHostSymbol(obj) || lib.isMetaObject(obj));
        }

    }

    @Builtin(name = "dis", minNumOfPositionalArgs = 1, parameterNames = {"obj", "quickened"}, doc = "Helper to disassemble code objects")
    @ArgumentClinic(name = "quickened", conversion = ArgumentClinic.ClinicConversion.Boolean, defaultValue = "false")
    @GenerateNodeFactory
    abstract static class DisNode extends PythonBinaryClinicBuiltinNode {
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
            return GraalPythonModuleBuiltinsClinicProviders.DisNodeClinicProviderGen.INSTANCE;
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

    @Builtin(name = "which", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class WhichNode extends PythonUnaryBuiltinNode {
        @Specialization
        @TruffleBoundary
        Object which(PBuiltinFunction object) {
            RootCallTarget callTarget = object.getCallTarget();
            return toTruffleStringUncached(String.format("%s(%s)", object.getClass().getName(), whichCallTarget(callTarget)));
        }

        @Specialization
        @TruffleBoundary
        Object which(PBuiltinMethod object) {
            return toTruffleStringUncached(String.format("%s(%s)", object.getClass().getName(), whichCallTarget(object.getBuiltinFunction().getCallTarget())));
        }

        private static String whichCallTarget(RootCallTarget callTarget) {
            RootNode rootNode = callTarget.getRootNode();
            String rootStr;
            if (rootNode instanceof BuiltinFunctionRootNode builtinFunctionRootNode) {
                rootStr = String.format("nodeClass=%s", builtinFunctionRootNode.getFactory().getNodeClass().getName());
            } else {
                rootStr = String.format("root=%s", rootNode.getClass().getName());
            }
            return rootStr;
        }

        @Specialization
        @TruffleBoundary
        Object which(Object object) {
            return toTruffleStringUncached(object.getClass().getName());
        }
    }

    @Builtin(name = "dump_heap", minNumOfPositionalArgs = 0)
    @GenerateNodeFactory
    abstract static class DumpHeapNode extends PythonBuiltinNode {
        @Specialization
        TruffleString doit(VirtualFrame frame,
                        @Bind("this") Node inliningTarget,
                        @Cached TruffleString.FromJavaStringNode fromJavaStringNode,
                        @Cached TruffleString.EqualNode eqNode,
                        @Cached PConstructAndRaiseNode.Lazy constructAndRaiseNode) {
            TruffleFile tempFile;
            try {
                PythonContext context = getContext();
                tempFile = context.getEnv().createTempFile(context.getEnv().getCurrentWorkingDirectory(), J_GRAALPYTHON_ID, ".hprof");
                tempFile.delete();
            } catch (IOException e) {
                throw constructAndRaiseNode.get(inliningTarget).raiseOSError(frame, e, eqNode);
            }
            PythonUtils.dumpHeap(tempFile.getPath());
            return fromJavaStringNode.execute(tempFile.getPath(), TS_ENCODING);
        }
    }

    @Builtin(name = "java_assert", minNumOfPositionalArgs = 0)
    @GenerateNodeFactory
    abstract static class JavaAssertNode extends PythonBuiltinNode {
        @Specialization
        Object doit() {
            boolean assertOn = false;
            assert assertOn = true;
            return assertOn;
        }
    }

    @Builtin(name = "is_native_object", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class IsNativeObject extends PythonUnaryBuiltinNode {
        @Specialization
        boolean isNative(@SuppressWarnings("unused") PythonAbstractNativeObject obj) {
            return true;
        }

        @Fallback
        boolean isNative(@SuppressWarnings("unused") Object obj) {
            return false;
        }

    }

    // This is only used from HPy
    @Builtin(name = "PyTruffle_CreateType", minNumOfPositionalArgs = 4)
    @GenerateNodeFactory
    abstract static class PyTruffle_CreateType extends PythonQuaternaryBuiltinNode {
        @Specialization
        static PythonClass createType(VirtualFrame frame, TruffleString name, PTuple bases, PDict namespaceOrig, Object metaclass,
                        @Cached CreateTypeNode createType) {
            return createType.execute(frame, namespaceOrig, name, bases, metaclass, PKeyword.EMPTY_KEYWORDS);
        }
    }

    @Builtin(name = "get_graalvm_version", minNumOfPositionalArgs = 0)
    @GenerateNodeFactory
    abstract static class GetGraalVmVersion extends PythonBuiltinNode {
        @TruffleBoundary
        @Specialization
        TruffleString get() {
            Version current = Version.getCurrent();
            return TruffleString.fromJavaStringUncached(current.toString(), TS_ENCODING);
        }
    }

    @Builtin(name = "get_jdk_version", minNumOfPositionalArgs = 0)
    @GenerateNodeFactory
    abstract static class GetJdkVersion extends PythonBuiltinNode {
        @TruffleBoundary
        @Specialization
        TruffleString get() {
            return TruffleString.fromJavaStringUncached(System.getProperty("java.version"), TS_ENCODING);
        }
    }

    @Builtin(name = "get_max_process_count", minNumOfPositionalArgs = 0)
    @GenerateNodeFactory
    abstract static class GetMaxProcessCount extends PythonBuiltinNode {

        @TruffleBoundary
        @Specialization
        int get() {
            int numCpu = Runtime.getRuntime().availableProcessors();
            if (numCpu < 2) {
                return 1;
            }
            // Try a heuristic based on total memory
            int processCount;
            try {
                // Don't think of parsing /proc/meminfo, it doesn't account for cgroups
                Class<?> beanClass = Class.forName("com.sun.management.OperatingSystemMXBean");
                Method method = beanClass.getDeclaredMethod("getTotalMemorySize");
                long totalMemory = (long) method.invoke(ManagementFactory.getOperatingSystemMXBean());
                // Let's say we don't want to use more than 50% of total memory
                long usableMemory = totalMemory / 2;
                // Conservative estimate of how much memory GraalPy might use when loading many
                // heavy modules
                long memoryPerProcess = 3L * 1024 * 1024 * 1024;
                processCount = (int) (usableMemory / memoryPerProcess) - 1;
            } catch (Exception e) {
                // Do a coarse guess
                processCount = numCpu / 5;
            }
            return Math.min(numCpu, Math.max(1, processCount));
        }
    }

    @Builtin(name = "get_python_home_paths", minNumOfPositionalArgs = 0)
    @GenerateNodeFactory
    abstract static class GetPythonHomePaths extends PythonBuiltinNode {
        @TruffleBoundary
        @Specialization
        TruffleString get() {
            PythonContext context = getContext();
            TruffleString sep = TruffleString.fromJavaStringUncached(File.pathSeparator, TS_ENCODING);
            return context.getStdlibHome().concatUncached(sep, TS_ENCODING, false).concatUncached(context.getCoreHome(), TS_ENCODING, false);
        }
    }
}
