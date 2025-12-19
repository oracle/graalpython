/*
 * Copyright (c) 2019, 2025, Oracle and/or its affiliates. All rights reserved.
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

import static com.oracle.graal.python.PythonLanguage.GRAALVM_MAJOR;
import static com.oracle.graal.python.PythonLanguage.GRAALVM_MICRO;
import static com.oracle.graal.python.PythonLanguage.GRAALVM_MINOR;
import static com.oracle.graal.python.PythonLanguage.J_GRAALPYTHON_ID;
import static com.oracle.graal.python.PythonLanguage.RELEASE_LEVEL;
import static com.oracle.graal.python.PythonLanguage.RELEASE_LEVEL_FINAL;
import static com.oracle.graal.python.nodes.BuiltinNames.J_EXTEND;
import static com.oracle.graal.python.nodes.BuiltinNames.J___GRAALPYTHON__;
import static com.oracle.graal.python.nodes.BuiltinNames.T_SHA3;
import static com.oracle.graal.python.nodes.BuiltinNames.T___GRAALPYTHON__;
import static com.oracle.graal.python.nodes.BuiltinNames.T___MAIN__;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.T___NAME__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T_INSERT;
import static com.oracle.graal.python.nodes.StringLiterals.T_COLON;
import static com.oracle.graal.python.nodes.StringLiterals.T_JAVA;
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

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.lang.management.ManagementFactory;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.logging.Level;

import org.graalvm.polyglot.Value;
import org.graalvm.polyglot.proxy.ProxyExecutable;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.annotations.ArgumentClinic;
import com.oracle.graal.python.annotations.Builtin;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.Python3Core;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.modules.GraalPythonModuleBuiltinsFactory.DebugNodeFactory;
import com.oracle.graal.python.builtins.modules.cext.PythonCextCapsuleBuiltins;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.array.PArray;
import com.oracle.graal.python.builtins.objects.bytes.PBytes;
import com.oracle.graal.python.builtins.objects.bytes.PBytesLike;
import com.oracle.graal.python.builtins.objects.capsule.PyCapsule;
import com.oracle.graal.python.builtins.objects.cext.PythonAbstractNativeObject;
import com.oracle.graal.python.builtins.objects.cext.capi.CApiContext;
import com.oracle.graal.python.builtins.objects.cext.capi.PySequenceArrayWrapper.ToNativeStorageNode;
import com.oracle.graal.python.builtins.objects.cext.capi.PythonNativeWrapper;
import com.oracle.graal.python.builtins.objects.cext.capi.transitions.CApiTransitions;
import com.oracle.graal.python.builtins.objects.cext.capi.transitions.CApiTransitions.PythonObjectReference;
import com.oracle.graal.python.builtins.objects.cext.capi.transitions.GetNativeWrapperNode;
import com.oracle.graal.python.builtins.objects.cext.common.CArrayWrappers;
import com.oracle.graal.python.builtins.objects.cext.copying.NativeLibraryLocator;
import com.oracle.graal.python.builtins.objects.code.CodeNodes;
import com.oracle.graal.python.builtins.objects.code.PCode;
import com.oracle.graal.python.builtins.objects.common.DynamicObjectStorage;
import com.oracle.graal.python.builtins.objects.common.EconomicMapStorage;
import com.oracle.graal.python.builtins.objects.common.EmptyStorage;
import com.oracle.graal.python.builtins.objects.common.HashingStorage;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodes.HashingStorageLen;
import com.oracle.graal.python.builtins.objects.common.SequenceStorageNodes;
import com.oracle.graal.python.builtins.objects.dict.PDict;
import com.oracle.graal.python.builtins.objects.exception.OSErrorEnum;
import com.oracle.graal.python.builtins.objects.exception.OSErrorEnum.ErrorAndMessagePair;
import com.oracle.graal.python.builtins.objects.function.PArguments;
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
import com.oracle.graal.python.lib.OsEnvironGetNode;
import com.oracle.graal.python.lib.PyObjectCallMethodObjArgs;
import com.oracle.graal.python.lib.PyObjectGetItem;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PConstructAndRaiseNode;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.arrow.ArrowArray;
import com.oracle.graal.python.nodes.arrow.ArrowSchema;
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
import com.oracle.graal.python.nodes.function.builtins.PythonUnaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.clinic.ArgumentClinicProvider;
import com.oracle.graal.python.nodes.object.GetClassNode;
import com.oracle.graal.python.nodes.object.GetOrCreateDictNode;
import com.oracle.graal.python.nodes.statement.AbstractImportNode;
import com.oracle.graal.python.nodes.util.CastToTruffleStringNode;
import com.oracle.graal.python.nodes.util.ToNativePrimitiveStorageNode;
import com.oracle.graal.python.runtime.ExecutionContext;
import com.oracle.graal.python.runtime.ExecutionContext.BoundaryCallContext;
import com.oracle.graal.python.runtime.ExecutionContext.InteropCallContext;
import com.oracle.graal.python.runtime.IndirectCallData.BoundaryCallData;
import com.oracle.graal.python.runtime.IndirectCallData.InteropCallData;
import com.oracle.graal.python.runtime.PosixSupportLibrary;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.graal.python.runtime.PythonImageBuildOptions;
import com.oracle.graal.python.runtime.PythonOptions;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.graal.python.runtime.exception.PythonExitException;
import com.oracle.graal.python.runtime.object.PFactory;
import com.oracle.graal.python.runtime.sequence.PSequence;
import com.oracle.graal.python.runtime.sequence.storage.NativePrimitiveSequenceStorage;
import com.oracle.graal.python.runtime.sequence.storage.NativeSequenceStorage;
import com.oracle.graal.python.runtime.sequence.storage.SequenceStorage;
import com.oracle.graal.python.util.PythonUtils;
import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.TruffleFile;
import com.oracle.truffle.api.TruffleLanguage.Env;
import com.oracle.truffle.api.TruffleLogger;
import com.oracle.truffle.api.TruffleOptions;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Exclusive;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.NeverDefault;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.interop.ArityException;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.InvalidArrayIndexException;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnknownIdentifierException;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.interop.UnsupportedTypeException;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.NodeUtil;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.api.strings.TruffleString;

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
        addBuiltinConstant("is_native", TruffleOptions.AOT);
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
        if (!context.getEnv().isPreInitialization()) {
            mod.setAttribute(tsLiteral("home"), context.getLanguageHome());
        }
        mod.setAttribute(tsLiteral("in_preinitialization"), context.getEnv().isPreInitialization());
        TruffleString coreHome = context.getCoreHome();
        TruffleString stdlibHome = context.getStdlibHome();
        TruffleString capiHome = context.getCAPIHome();
        mod.setAttribute(tsLiteral("jython_emulation_enabled"), language.getEngineOption(PythonOptions.EmulateJython));
        mod.setAttribute(tsLiteral("host_import_enabled"), context.getEnv().isHostLookupAllowed());
        mod.setAttribute(tsLiteral("core_home"), coreHome);
        mod.setAttribute(tsLiteral("stdlib_home"), stdlibHome);
        mod.setAttribute(tsLiteral("capi_home"), capiHome);
        Object[] arr = convertToObjectArray(PythonOptions.getExecutableList(context));
        PList executableList = PFactory.createList(language, arr);
        mod.setAttribute(tsLiteral("executable_list"), executableList);
        mod.setAttribute(tsLiteral("venvlauncher_command"), context.getOption(PythonOptions.VenvlauncherCommand));
        mod.setAttribute(tsLiteral("ForeignType"), core.lookupType(PythonBuiltinClassType.ForeignObject));

        if (!context.getOption(PythonOptions.EnableDebuggingBuiltins)) {
            mod.setAttribute(tsLiteral("dump_truffle_ast"), PNone.NO_VALUE);
            mod.setAttribute(tsLiteral("tdebug"), PNone.NO_VALUE);
            mod.setAttribute(tsLiteral("set_storage_strategy"), PNone.NO_VALUE);
            mod.setAttribute(tsLiteral("get_storage_strategy"), PNone.NO_VALUE);
            mod.setAttribute(tsLiteral("storage_to_native"), PNone.NO_VALUE);
            mod.setAttribute(tsLiteral("dump_heap"), PNone.NO_VALUE);
            mod.setAttribute(tsLiteral("is_native_object"), PNone.NO_VALUE);
            mod.setAttribute(tsLiteral("get_handle_table_id"), PNone.NO_VALUE);
            mod.setAttribute(tsLiteral("is_strong_handle_table_ref"), PNone.NO_VALUE);
            mod.setAttribute(tsLiteral("clear_interop_type_registry"), PNone.NO_VALUE);
            mod.setAttribute(tsLiteral("foreign_number_list"), PNone.NO_VALUE);
            mod.setAttribute(tsLiteral("foreign_wrapper"), PNone.NO_VALUE);
        } else {
            addBuiltinConstant("using_native_primitive_storage_strategy", context.getLanguage().getEngineOption(PythonOptions.UseNativePrimitiveStorageStrategy));
        }
        if (PythonImageBuildOptions.WITHOUT_PLATFORM_ACCESS || !context.getOption(PythonOptions.RunViaLauncher)) {
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
        PNone run(VirtualFrame frame,
                        @Cached("createFor($node)") BoundaryCallData boundaryCallData) {
            Object saved = BoundaryCallContext.enter(frame, boundaryCallData);
            try {
                return runBoundary();
            } finally {
                BoundaryCallContext.exit(frame, boundaryCallData, saved);
            }
        }

        @TruffleBoundary
        PNone runBoundary() {
            /*
             * This node handles the part of pymain_run_python where the filename is not null. The
             * other paths through pymain_run_python are handled in GraalPythonMain and the path
             * prepending is done in PythonLanguage in those other cases
             */
            PythonContext context = getContext();
            assert !context.getEnv().isPreInitialization();
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
                source = builder.mimeType(PythonLanguage.getCompileMimeType(0, 0)).build();
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
            PythonLanguage language = context.getLanguage();
            RootCallTarget callTarget = (RootCallTarget) context.getEnv().parsePublic(source);
            Object[] arguments = PArguments.create();
            PythonModule mainModule = context.getMainModule();
            PDict mainDict = GetOrCreateDictNode.executeUncached(mainModule);
            PArguments.setGlobals(arguments, mainModule);
            PArguments.setSpecialArgument(arguments, mainDict);
            PArguments.setException(arguments, PException.NO_EXCEPTION);
            context.initializeMainModule(inputFilePath);
            Object state = ExecutionContext.IndirectCalleeContext.enter(context.getThreadState(language), arguments);
            try {
                callTarget.call(arguments);
            } finally {
                ExecutionContext.IndirectCalleeContext.exit(language, context, state);
            }
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
            if (pathHooks instanceof PList pathHooksList && pathImporterCache instanceof PDict pathImporterCacheDict) {
                importer = pathImporterCacheDict.getItem(inputFilePath);
                if (importer == null) {
                    /* set path_importer_cache[p] to None to avoid recursion */
                    pathImporterCacheDict.setItem(inputFilePath, PNone.NONE);
                    SequenceStorage storage = pathHooksList.getSequenceStorage();
                    Object[] hooks = SequenceStorageNodes.GetInternalObjectArrayNode.executeUncached(storage);
                    int numHooks = storage.length();
                    for (int i = 0; i < numHooks; i++) {
                        try {
                            importer = CallNode.executeUncached(hooks[i], inputFilePath);
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
                        @Bind Node inliningTarget,
                        @Bind PythonContext context,
                        @Cached CastToTruffleStringNode castToTruffleStringNode,
                        @Cached TruffleString.EqualNode eqNode,
                        @Cached PConstructAndRaiseNode.Lazy constructAndRaiseNode) {
            try {
                TruffleString filename = castToTruffleStringNode.execute(inliningTarget, filenameObj);
                TruffleFile file = context.getPublicTruffleFileRelaxed(filename, PythonLanguage.T_DEFAULT_PYTHON_EXTENSIONS);
                byte[] bytes = file.readAllBytes();
                return PFactory.createBytes(context.getLanguage(inliningTarget), bytes);
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

    @Builtin(name = "indirect_call_tester", minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    public abstract static class IndirectCallTesterNode extends PythonBinaryBuiltinNode {
        // Synchronize with constants of the same name in Python tests
        static final int TYPE_INDIRECT_BOUNDARY = 1;
        static final int TYPE_INDIRECT_INTEROP_CALL = 2;
        static final int TYPE_INDIRECT_INTEROP = 3;
        static final int TYPE_INDIRECT_BOUNDARY_UNCACHED_INTEROP = 4;

        @Specialization(guards = "type == TYPE_INDIRECT_BOUNDARY")
        public Object doBoundary(VirtualFrame frame, Object arg, int type,
                        @Exclusive @Cached("createFor($node)") BoundaryCallData boundaryCallData) {
            Object state = BoundaryCallContext.enter(frame, boundaryCallData);
            try {
                return truffleBoundaryCall(arg);
            } finally {
                BoundaryCallContext.exit(frame, boundaryCallData, state);
            }
        }

        @Specialization(guards = "type == TYPE_INDIRECT_INTEROP_CALL")
        public Object doInteropCall(VirtualFrame frame, Object arg, int type,
                        @Cached CallNode callNode) {
            Object hostFun = ForwardingExecutable.asGuestObject(this);
            return callNode.execute(frame, hostFun, arg);
        }

        @Specialization(guards = "type == TYPE_INDIRECT_INTEROP")
        public Object doInterop(VirtualFrame frame, Object arg, int type,
                        @Exclusive @Cached("createFor($node)") InteropCallData callData,
                        @CachedLibrary(limit = "1") InteropLibrary interop) {
            Object hostFun = ForwardingExecutable.asGuestObject(this);
            Object state = InteropCallContext.enter(frame, this, callData);
            try {
                return interop.execute(hostFun, arg);
            } catch (UnsupportedTypeException | ArityException | UnsupportedMessageException e) {
                throw CompilerDirectives.shouldNotReachHere(e);
            } finally {
                InteropCallContext.exit(frame, this, callData, state);
            }
        }

        @Specialization(guards = "type == TYPE_INDIRECT_BOUNDARY_UNCACHED_INTEROP")
        public Object doBoundaryInterop(VirtualFrame frame, Object arg, int type,
                        @Exclusive @Cached("createFor($node)") BoundaryCallData boundaryCallData) {
            Object hostFun = ForwardingExecutable.asGuestObject(this);
            Object state = BoundaryCallContext.enter(frame, boundaryCallData);
            try {
                return truffleBoundaryInteropCall(hostFun, arg);
            } finally {
                BoundaryCallContext.exit(frame, boundaryCallData, state);
            }
        }

        @TruffleBoundary
        private Object truffleBoundaryCall(Object arg) {
            return CallNode.executeUncached(arg);
        }

        @TruffleBoundary
        private Object truffleBoundaryInteropCall(Object callable, Object arg) {
            try {
                return InteropLibrary.getUncached().execute(callable, arg);
            } catch (UnsupportedTypeException | ArityException | UnsupportedMessageException e) {
                throw CompilerDirectives.shouldNotReachHere(e);
            }
        }

        public static final class ForwardingExecutable implements ProxyExecutable {
            static Object asGuestObject(Node n) {
                return PythonContext.get(n).getEnv().asGuestValue(new ForwardingExecutable());
            }

            @Override
            public Object execute(Value... arguments) {
                return arguments[0].execute();
            }
        }
    }

    @Builtin(name = "was_stack_walk", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class WasStackWalkNode extends PythonUnaryBuiltinNode {
        @TruffleBoundary
        @Specialization
        @SuppressWarnings("AssertWithSideEffects")
        public Object doBoundary(Object value) {
            boolean assertionsEnabled = false;
            assert assertionsEnabled = true;
            if (!assertionsEnabled) {
                // None indicates that assertions are disabled and this functionality is not
                // available
                return PNone.NONE;
            }

            boolean prev = PythonContext.get(this).wasStackWalk;
            if (value instanceof Boolean b) {
                PythonContext.get(this).wasStackWalk = b;
            }
            return prev;
        }
    }

    @Builtin(name = "builtin", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class BuiltinNode extends PythonUnaryBuiltinNode {
        @Specialization
        public Object doIt(VirtualFrame frame, PFunction func,
                        @Bind Node inliningTarget,
                        @Bind PythonContext context,
                        @Cached PyObjectGetItem getItem) {
            PFunction builtinFunc = convertToBuiltin(func);
            PythonObject globals = func.getGlobals();
            PythonModule builtinModule;
            if (globals instanceof PythonModule) {
                builtinModule = (PythonModule) globals;
            } else {
                TruffleString moduleName = (TruffleString) getItem.execute(frame, inliningTarget, globals, T___NAME__);
                builtinModule = context.lookupBuiltinModule(moduleName);
                assert builtinModule != null;
            }
            return PFactory.createBuiltinMethod(context.getLanguage(inliningTarget), builtinModule, builtinFunc);
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
                        @Bind Node inliningTarget,
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
        static PDict doGeneric(VirtualFrame frame, @SuppressWarnings("unused") Object unused,
                        @Bind PythonLanguage language,
                        @Cached("createFor($node)") BoundaryCallData boundaryCallData) {
            return PFactory.createDict(language, fromToolchain(frame, boundaryCallData));
        }

        private static PKeyword[] fromToolchain(VirtualFrame frame, BoundaryCallData boundaryCallData) {
            Object saved = BoundaryCallContext.enter(frame, boundaryCallData);
            try {
                return fromToolchainBoundary();
            } finally {
                BoundaryCallContext.exit(frame, boundaryCallData, saved);
            }
        }

        @TruffleBoundary
        private static PKeyword[] fromToolchainBoundary() {
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
            Env env = PythonContext.get(null).getEnv();
            TruffleString tspath = OsEnvironGetNode.lookupUncached(T_PATH);
            if (tspath == null) {
                return -1;
            }
            String path = tspath.toJavaStringUncached();
            if (path != null) {
                for (int i = 0; i < C_COMPILER_PRECEDENCE.length; i++) {
                    int last = 0;
                    for (int j = path.indexOf(env.getPathSeparator()); j != -1; j = path.indexOf(env.getPathSeparator(), last)) {
                        try {
                            if (env.getPublicTruffleFile(path.substring(last, j)).resolve(C_COMPILER_PRECEDENCE[i]).isExecutable()) {
                                return i;
                            }
                        } catch (UnsupportedOperationException | IllegalArgumentException e) {
                            // skip
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

    @Builtin(name = "zlib_module_backend", minNumOfPositionalArgs = 0)
    @GenerateNodeFactory
    public abstract static class ZlibModuleBackendNode extends PythonBuiltinNode {
        @Specialization
        TruffleString zlibModuleBackend() {
            return getContext().getNFIZlibSupport().isAvailable() ? T_NATIVE : T_JAVA;
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
                    throw PRaiseNode.raiseStatic(this, PythonBuiltinClassType.ValueError, ErrorMessages.UNKNOWN_STORAGE_STRATEGY);
            }
        }

        private void validate(HashingStorage dictStorage) {
            if (HashingStorageLen.executeUncached(dictStorage) != 0) {
                throw PRaiseNode.raiseStatic(this, PythonBuiltinClassType.ValueError, ErrorMessages.SHOULD_BE_USED_ONLY_NEW_SETS);
            }
        }
    }

    @Builtin(name = "get_storage_strategy", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class GetStorageStrategyNode extends PythonUnaryBuiltinNode {
        @Specialization
        @TruffleBoundary
        TruffleString doSet(PSequence seq) {
            return PythonUtils.toTruffleStringUncached(seq.getSequenceStorage().getClass().getSimpleName());
        }
    }

    @Builtin(name = "storage_to_native", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class StorageToNative extends PythonUnaryBuiltinNode {

        @Specialization
        @TruffleBoundary
        Object toNative(PArray array) {
            CApiContext.ensureCapiWasLoaded("internal API");
            NativeSequenceStorage newStorage = ToNativeStorageNode.executeUncached(array.getSequenceStorage(), true);
            array.setSequenceStorage(newStorage);
            return array;
        }

        @Specialization
        @TruffleBoundary
        Object toNative(PSequence sequence) {
            CApiContext.ensureCapiWasLoaded("internal API");
            NativeSequenceStorage newStorage = ToNativeStorageNode.executeUncached(sequence.getSequenceStorage(), sequence instanceof PBytesLike);
            sequence.setSequenceStorage(newStorage);
            return sequence;
        }
    }

    @Builtin(name = J_EXTEND, minNumOfPositionalArgs = 1, doc = "Extends Java class and return HostAdapterCLass")
    @GenerateNodeFactory
    public abstract static class JavaExtendNode extends PythonUnaryBuiltinNode {
        @Specialization
        static Object doIt(Object value,
                        @Bind Node inliningTarget,
                        @CachedLibrary(limit = "3") InteropLibrary lib,
                        @Cached PRaiseNode raiseNode) {
            if (getContext(inliningTarget).getEnv().isPreInitialization()) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                throw new UnsupportedOperationException(ErrorMessages.CANT_EXTEND_JAVA_CLASS_NOT_JVM.toJavaStringUncached());
            }
            if (TruffleOptions.AOT) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                throw raiseNode.raise(inliningTarget, SystemError, ErrorMessages.CANT_EXTEND_JAVA_CLASS_NOT_JVM);
            }

            if (!isType(value, lib)) {
                throw raiseNode.raise(inliningTarget, TypeError, ErrorMessages.CANT_EXTEND_JAVA_CLASS_NOT_TYPE, value);
            }

            try {
                return PythonContext.get(inliningTarget).getEnv().createHostAdapter(new Object[]{value});
            } catch (Exception ex) {
                throw raiseNode.raise(inliningTarget, TypeError, PythonUtils.getMessage(ex), ex);
            }
        }

        protected static boolean isType(Object obj, InteropLibrary lib) {
            return lib.isHostObject(obj) && lib.isMetaObject(obj);
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
        // This is a builtin for debugging, so it also includes things that should never end up in
        // python value space
        static Object which(Object object) {
            if (object == null) {
                return "null";
            }
            String name = object.getClass().getName();
            Object detail = null;
            try {
                if (object instanceof PNone) {
                    detail = "NO_VALUE";
                } else if (object instanceof PBuiltinFunction fn) {
                    detail = whichCallTarget(fn.getCallTarget());
                } else if (object instanceof PBuiltinMethod fn) {
                    detail = whichCallTarget(fn.getBuiltinFunction().getCallTarget());
                } else if (object instanceof PSequence sequence) {
                    detail = sequence.getSequenceStorage();
                } else if (object instanceof PArray array) {
                    detail = array.getSequenceStorage();
                } else if (object instanceof PythonAbstractNativeObject nativeObject) {
                    detail = PythonUtils.formatPointer(nativeObject.getPtr());
                }
            } catch (Throwable t) {
                detail = "Detail computation threw exception: " + t;
            }
            String which = detail != null ? String.format("%s(%s)", name, detail) : name;
            return toTruffleStringUncached(which);
        }
    }

    @Builtin(name = "dump_heap", minNumOfPositionalArgs = 0)
    @GenerateNodeFactory
    abstract static class DumpHeapNode extends PythonBuiltinNode {
        @Specialization
        TruffleString doit(VirtualFrame frame,
                        @Bind Node inliningTarget,
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
        @SuppressWarnings("all")
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

    @Builtin(name = "get_handle_table_id", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class GetHandleTableID extends PythonUnaryBuiltinNode {
        @Specialization
        @TruffleBoundary
        static int doManaged(Object object) {
            if (object instanceof PythonAbstractNativeObject) {
                return -1;
            }
            Object nativeWrapper = GetNativeWrapperNode.executeUncached(object);
            if (nativeWrapper instanceof PythonNativeWrapper pn) {
                return pn.ref.getHandleTableIndex();
            } else {
                return -1;
            }
        }
    }

    @Builtin(name = "is_strong_handle_table_ref", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class IsWeakHandleTableRef extends PythonUnaryBuiltinNode {
        @Specialization
        @TruffleBoundary
        static boolean doGeneric(int id) {
            PythonObjectReference ref = CApiTransitions.nativeStubLookupGet(PythonContext.get(null).nativeContext, 0, id);
            return ref != null && ref.isStrongReference();
        }
    }

    @Builtin(name = "get_graalvm_version", minNumOfPositionalArgs = 0)
    @GenerateNodeFactory
    abstract static class GetGraalVmVersion extends PythonBuiltinNode {
        private static final TruffleString VERSION_STRING;
        static {
            String version = String.format("%d.%d.%d", GRAALVM_MAJOR, GRAALVM_MINOR, GRAALVM_MICRO);
            if (RELEASE_LEVEL != RELEASE_LEVEL_FINAL) {
                version += "-dev";
            }
            VERSION_STRING = TruffleString.fromJavaStringUncached(version, TS_ENCODING);
        }

        @Specialization
        TruffleString get() {
            return VERSION_STRING;
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
            TruffleString sep = TruffleString.fromJavaStringUncached(context.getEnv().getPathSeparator(), TS_ENCODING);
            return context.getStdlibHome().concatUncached(sep, TS_ENCODING, false).concatUncached(context.getCoreHome(), TS_ENCODING, false);
        }
    }

    @Builtin(name = "storage_to_native_primitive", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class StorageToNativePrimitive extends PythonUnaryBuiltinNode {

        @Specialization
        static Object doArray(PArray array,
                        @Shared @Cached ToNativePrimitiveStorageNode toNativePrimitiveNode,
                        @Bind Node inliningTarget) {
            NativePrimitiveSequenceStorage newStorage = toNativePrimitiveNode.execute(inliningTarget, array.getSequenceStorage());
            array.setSequenceStorage(newStorage);
            return array;
        }

        @Specialization
        static Object doSequence(PSequence sequence,
                        @Shared @Cached ToNativePrimitiveStorageNode toNativePrimitiveNode,
                        @Bind Node inliningTarget) {
            NativePrimitiveSequenceStorage newStorage = toNativePrimitiveNode.execute(inliningTarget, sequence.getSequenceStorage());
            sequence.setSequenceStorage(newStorage);
            return sequence;
        }
    }

    @Builtin(name = "clear_interop_type_registry", maxNumOfPositionalArgs = 0)
    @GenerateNodeFactory
    public abstract static class ClearInteropTypeRegistry extends PythonBuiltinNode {

        @Specialization
        @TruffleBoundary
        Object doClear() {
            getContext().interopTypeRegistry.clear();
            PolyglotModuleBuiltins.clearInteropTypeRegistryCache(getContext());
            return PNone.NONE;
        }
    }

    @Builtin(name = "get_current_rss", maxNumOfPositionalArgs = 0)
    @GenerateNodeFactory
    public abstract static class GetCurrentRSS extends PythonBuiltinNode {

        @Specialization
        @TruffleBoundary
        Object currentRSS() {
            return getContext().getCApiContext().getCurrentRSS();
        }
    }

    @Builtin(name = "replicate_extensions_in_venv", minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    public abstract static class ReplicateExtNode extends PythonBuiltinNode {
        @Specialization
        @TruffleBoundary
        static Object replicate(TruffleString venvPath, int count,
                        @Bind Node node,
                        @Bind PythonContext context) {
            try {
                NativeLibraryLocator.replicate(context.getEnv().getPublicTruffleFile(venvPath.toJavaStringUncached()), context, count);
            } catch (IOException | InterruptedException e) {
                throw PRaiseNode.raiseStatic(node, PythonBuiltinClassType.ValueError, e);
            }
            return PNone.NONE;
        }
    }

    @Builtin(name = "foreign_number_list", maxNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class ForeignNumberListNode extends PythonBuiltinNode {

        @Specialization
        @TruffleBoundary
        Object foreignNumberList(Object number) {
            return new ForeignNumberList(number);
        }

        @SuppressWarnings("static-method")
        @ExportLibrary(value = InteropLibrary.class, delegateTo = "number")
        static final class ForeignNumberList implements TruffleObject {
            final Object number;

            ForeignNumberList(Object number) {
                this.number = number;
            }

            @ExportMessage
            boolean hasArrayElements() {
                return true;
            }

            @ExportMessage
            boolean isArrayElementReadable(long index) {
                return index == 0;
            }

            @TruffleBoundary
            @ExportMessage
            Object readArrayElement(long index) throws InvalidArrayIndexException {
                if (!isArrayElementReadable(index)) {
                    throw InvalidArrayIndexException.create(index);
                }
                return number;
            }

            @ExportMessage
            long getArraySize() {
                return 1;
            }
        }
    }

    @Builtin(name = "foreign_wrapper", maxNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class ForeignWrapperNode extends PythonBuiltinNode {

        @Specialization
        @TruffleBoundary
        Object foreignWrapper(Object object) {
            return new ForeignWrapper(object);
        }

        @SuppressWarnings({"unused", "static-method"})
        @ExportLibrary(value = InteropLibrary.class, delegateTo = "object")
        static final class ForeignWrapper implements TruffleObject {
            final Object object;

            ForeignWrapper(Object object) {
                this.object = object;
            }

            /*
             * Hide members as we want to treat the object solely by using its interop traits &
             * trait-specific messages and not unintentionally read or invoke one of its members
             * (methods or fields).
             */

            @ExportMessage
            boolean hasMembers() {
                return false;
            }

            @ExportMessage
            boolean isMemberReadable(String member) {
                return false;
            }

            @ExportMessage
            boolean isMemberModifiable(String member) {
                return false;
            }

            @ExportMessage
            boolean isMemberInsertable(String member) {
                return false;
            }

            @ExportMessage
            boolean isMemberRemovable(String member) {
                return false;
            }

            @ExportMessage
            boolean isMemberInvocable(String member) {
                return false;
            }

            @ExportMessage
            Object getMembers(boolean includeInternal) throws UnsupportedMessageException {
                throw UnsupportedMessageException.create();
            }

            @ExportMessage
            Object readMember(String member) throws UnsupportedMessageException {
                throw UnsupportedMessageException.create();
            }

            @ExportMessage
            void writeMember(String member, Object value) throws UnsupportedMessageException {
                throw UnsupportedMessageException.create();
            }

            @ExportMessage
            void removeMember(String member) throws UnsupportedMessageException {
                throw UnsupportedMessageException.create();
            }

            @ExportMessage
            Object invokeMember(String member, Object[] arguments) throws UnsupportedMessageException {
                throw UnsupportedMessageException.create();
            }
        }
    }

    @Builtin(name = "create_arrow_py_capsule", minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    public abstract static class CreateArrowPyCapsule extends PythonBinaryBuiltinNode {

        @Specialization
        static PTuple doCreate(long arrowArrayAddr, long arrowSchemaAddr,
                        @Bind Node inliningTarget,
                        @Cached PythonCextCapsuleBuiltins.PyCapsuleNewNode pyCapsuleNewNode) {
            var ctx = getContext(inliningTarget);

            long arrayDestructor = ctx.arrowSupport.getArrowArrayDestructor();
            var arrayCapsuleName = new CArrayWrappers.CByteArrayWrapper(ArrowArray.CAPSULE_NAME);
            PyCapsule arrowArrayCapsule = pyCapsuleNewNode.execute(inliningTarget, arrowArrayAddr, arrayCapsuleName, arrayDestructor);

            long schemaDestructor = ctx.arrowSupport.getArrowSchemaDestructor();
            var schemaCapsuleName = new CArrayWrappers.CByteArrayWrapper(ArrowSchema.CAPSULE_NAME);
            PyCapsule arrowSchemaCapsule = pyCapsuleNewNode.execute(inliningTarget, arrowSchemaAddr, schemaCapsuleName, schemaDestructor);
            return PFactory.createTuple(ctx.getLanguage(inliningTarget), new Object[]{arrowSchemaCapsule, arrowArrayCapsule});
        }
    }
}
