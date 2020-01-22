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

import static com.oracle.graal.python.builtins.PythonBuiltinClassType.TypeError;
import static com.oracle.graal.python.builtins.PythonBuiltinClassType.ValueError;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__SIZEOF__;

import java.io.IOException;
import java.nio.ByteOrder;
import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.builtins.Builtin;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.exception.GetTracebackNode;
import com.oracle.graal.python.builtins.objects.exception.PBaseException;
import com.oracle.graal.python.builtins.objects.frame.PFrame;
import com.oracle.graal.python.builtins.objects.frame.PFrame.Reference;
import com.oracle.graal.python.builtins.objects.function.PArguments;
import com.oracle.graal.python.builtins.objects.ints.PInt;
import com.oracle.graal.python.builtins.objects.list.PList;
import com.oracle.graal.python.builtins.objects.module.PythonModule;
import com.oracle.graal.python.builtins.objects.str.PString;
import com.oracle.graal.python.builtins.objects.traceback.PTraceback;
import com.oracle.graal.python.nodes.call.special.LookupAndCallUnaryNode;
import com.oracle.graal.python.nodes.call.special.LookupAndCallUnaryNode.NoAttributeHandler;
import com.oracle.graal.python.nodes.frame.ReadCallerFrameNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonBinaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonUnaryBuiltinNode;
import com.oracle.graal.python.nodes.object.GetClassNode;
import com.oracle.graal.python.nodes.truffle.PythonArithmeticTypes;
import com.oracle.graal.python.nodes.util.CastToIntegerFromIntNode;
import com.oracle.graal.python.nodes.util.ExceptionStateNodes.GetCaughtExceptionNode;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.graal.python.runtime.PythonCore;
import com.oracle.graal.python.runtime.PythonOptions;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.TruffleFile;
import com.oracle.truffle.api.TruffleLanguage.Env;
import com.oracle.truffle.api.TruffleOptions;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.dsl.TypeSystemReference;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.LanguageInfo;
import com.oracle.truffle.api.profiles.ConditionProfile;
import com.oracle.truffle.llvm.api.Toolchain;

import org.graalvm.nativeimage.ImageInfo;

@CoreFunctions(defineModule = "sys")
public class SysModuleBuiltins extends PythonBuiltins {
    public static final String LLVM_LANGUAGE = "llvm";
    private static final String LICENSE = "Copyright (c) Oracle and/or its affiliates. Licensed under the Universal Permissive License v 1.0 as shown at http://oss.oracle.com/licenses/upl.";
    private static final String COMPILE_TIME;
    public static final String PLATFORM_DARWIN = "darwin";
    public static final String PLATFORM_WIN32 = "win32";
    public static final PNone FRAMEWORK = PNone.NONE;

    static {
        String compile_time;
        try {
            compile_time = new Date(PythonBuiltins.class.getResource("PythonBuiltins.class").openConnection().getLastModified()).toString();
        } catch (IOException e) {
            compile_time = "";
        }
        COMPILE_TIME = compile_time;
    }

    private static final String[] SYS_PREFIX_ATTRIBUTES = new String[]{"prefix", "exec_prefix"};
    private static final String[] BASE_PREFIX_ATTRIBUTES = new String[]{"base_prefix", "base_exec_prefix"};

    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return SysModuleBuiltinsFactory.getFactories();
    }

    @Override
    public void initialize(PythonCore core) {
        builtinConstants.put("abiflags", "");
        builtinConstants.put("byteorder", ByteOrder.nativeOrder() == ByteOrder.LITTLE_ENDIAN ? "little" : "big");
        builtinConstants.put("copyright", LICENSE);
        builtinConstants.put("dont_write_bytecode", true);
        builtinConstants.put("modules", core.factory().createDict());
        builtinConstants.put("path", core.factory().createList());
        builtinConstants.put("builtin_module_names", core.factory().createTuple(core.builtinModuleNames()));
        builtinConstants.put("maxsize", Integer.MAX_VALUE);
        builtinConstants.put("version_info", core.factory().createTuple(new Object[]{PythonLanguage.MAJOR, PythonLanguage.MINOR, PythonLanguage.MICRO, "dev", 0}));
        builtinConstants.put("version", PythonLanguage.VERSION +
                        " (" + COMPILE_TIME + ")" +
                        "\n[" + Truffle.getRuntime().getName() + ", Java " + System.getProperty("java.version") + "]");
        builtinConstants.put("graal_python_is_native", TruffleOptions.AOT);
        // the default values taken from JPython
        builtinConstants.put("float_info", core.factory().createTuple(new Object[]{
                        Double.MAX_VALUE,       // DBL_MAX
                        Double.MAX_EXPONENT,    // DBL_MAX_EXP
                        308,                    // DBL_MIN_10_EXP
                        Double.MIN_VALUE,       // DBL_MIN
                        Double.MIN_EXPONENT,    // DBL_MIN_EXP
                        -307,                   // DBL_MIN_10_EXP
                        10,                     // DBL_DIG
                        53,                     // DBL_MANT_DIG
                        2.2204460492503131e-16, // DBL_EPSILON
                        2,                      // FLT_RADIX
                        1                       // FLT_ROUNDS
        }));
        builtinConstants.put("maxunicode", Character.MAX_CODE_POINT);

        String os = getPythonOSName();
        builtinConstants.put("platform", os);
        if (os.equals(PLATFORM_DARWIN)) {
            builtinConstants.put("_framework", FRAMEWORK);
        }
        builtinConstants.put("__gmultiarch", getPythonArch() + "-" + os);

        super.initialize(core);

        // we need these during core initialization, they are re-set in postInitialize
        postInitialize(core);
    }

    @Override
    public void postInitialize(PythonCore core) {
        super.postInitialize(core);
        PythonModule sys = core.lookupBuiltinModule("sys");
        PythonContext context = core.getContext();
        String[] args = context.getEnv().getApplicationArguments();
        sys.setAttribute("argv", core.factory().createList(Arrays.copyOf(args, args.length, Object[].class)));

        String prefix = context.getSysPrefix();
        for (String name : SysModuleBuiltins.SYS_PREFIX_ATTRIBUTES) {
            sys.setAttribute(name, prefix);
        }

        String base_prefix = context.getSysBasePrefix();
        for (String name : SysModuleBuiltins.BASE_PREFIX_ATTRIBUTES) {
            sys.setAttribute(name, base_prefix);
        }

        String coreHome = context.getCoreHome();
        String stdlibHome = context.getStdlibHome();
        String capiHome = context.getCAPIHome();

        if (!ImageInfo.inImageBuildtimeCode()) {
            sys.setAttribute("executable", PythonOptions.getOption(context, PythonOptions.Executable));
            sys.setAttribute("graal_python_home", context.getLanguage().getHome());
        }
        sys.setAttribute("graal_python_jython_emulation_enabled", PythonOptions.getOption(context, PythonOptions.EmulateJython));
        sys.setAttribute("graal_python_host_import_enabled", context.getEnv().isHostLookupAllowed());
        sys.setAttribute("graal_python_core_home", coreHome);
        sys.setAttribute("graal_python_stdlib_home", stdlibHome);
        sys.setAttribute("graal_python_capi_home", capiHome);
        sys.setAttribute("__flags__", core.factory().createTuple(new Object[]{
                        false, // bytes_warning
                        !PythonOptions.getFlag(context, PythonOptions.PythonOptimizeFlag), // debug
                        true,  // dont_write_bytecode
                        false, // hash_randomization
                        PythonOptions.getFlag(context, PythonOptions.IgnoreEnvironmentFlag), // ignore_environment
                        PythonOptions.getFlag(context, PythonOptions.InspectFlag), // inspect
                        PythonOptions.getFlag(context, PythonOptions.TerminalIsInteractive), // interactive
                        PythonOptions.getFlag(context, PythonOptions.IsolateFlag), // isolated
                        PythonOptions.getFlag(context, PythonOptions.NoSiteFlag), // no_site
                        PythonOptions.getFlag(context, PythonOptions.NoUserSiteFlag), // no_user_site
                        PythonOptions.getFlag(context, PythonOptions.PythonOptimizeFlag), // optimize
                        PythonOptions.getFlag(context, PythonOptions.QuietFlag), // quiet
                        PythonOptions.getFlag(context, PythonOptions.VerboseFlag), // verbose
                        false, // dev_mode
                        0, // utf8_mode
        }));

        Env env = context.getEnv();
        String option = PythonOptions.getOption(context, PythonOptions.PythonPath);

        LanguageInfo llvmInfo = env.getInternalLanguages().get(LLVM_LANGUAGE);
        Toolchain toolchain = env.lookup(llvmInfo, Toolchain.class);

        boolean isIsolated = PythonOptions.getOption(context, PythonOptions.IsolateFlag);
        boolean capiSeparate = !capiHome.equals(coreHome);

        Object[] path;
        int pathIdx = 0;
        int defaultPathsLen = 2;
        if (!isIsolated) {
            defaultPathsLen++;
        }
        if (capiSeparate) {
            defaultPathsLen++;
        }
        if (option.length() > 0) {
            String[] split = option.split(context.getEnv().getPathSeparator());
            path = new Object[split.length + defaultPathsLen];
            System.arraycopy(split, 0, path, 0, split.length);
            pathIdx = split.length;
        } else {
            path = new Object[defaultPathsLen];
        }
        if (!isIsolated) {
            path[pathIdx++] = getScriptPath(env, args);
        }
        path[pathIdx++] = stdlibHome;
        path[pathIdx++] = coreHome + env.getFileNameSeparator() + "modules";
        if (capiSeparate) {
            // include our native modules on the path
            path[pathIdx++] = capiHome + env.getFileNameSeparator() + "modules";
        }
        PList sysPaths = core.factory().createList(path);
        sys.setAttribute("path", sysPaths);
        sys.setAttribute("graal_python_platform_id", toolchain.getIdentifier());
    }

    private static String getScriptPath(Env env, String[] args) {
        String scriptPath;
        if (args.length > 0) {
            String argv0 = args[0];
            if (argv0 != null && !argv0.startsWith("-") && !argv0.isEmpty()) {
                TruffleFile scriptFile = env.getPublicTruffleFile(argv0);
                try {
                    scriptPath = scriptFile.getAbsoluteFile().getParent().getPath();
                } catch (SecurityException e) {
                    scriptPath = scriptFile.getParent().getPath();
                }
                if (scriptPath == null) {
                    scriptPath = ".";
                }
            } else {
                scriptPath = "";
            }
        } else {
            scriptPath = "";
        }
        return scriptPath;
    }

    static String getPythonArch() {
        String arch = System.getProperty("os.arch", "");
        if (arch.equals("amd64")) {
            // be compatible with CPython's designation
            arch = "x86_64";
        }
        return arch;
    }

    static String getPythonOSName() {
        String property = System.getProperty("os.name");
        String os = "java";
        if (property != null) {
            if (property.toLowerCase().contains("cygwin")) {
                os = "cygwin";
            } else if (property.toLowerCase().contains("linux")) {
                os = "linux";
            } else if (property.toLowerCase().contains("mac")) {
                os = PLATFORM_DARWIN;
            } else if (property.toLowerCase().contains("windows")) {
                os = PLATFORM_WIN32;
            } else if (property.toLowerCase().contains("sunos")) {
                os = "sunos";
            } else if (property.toLowerCase().contains("freebsd")) {
                os = "freebsd";
            }
        }
        return os;
    }

    @Builtin(name = "exc_info", needsFrame = true)
    @GenerateNodeFactory
    public abstract static class ExcInfoNode extends PythonBuiltinNode {

        @Specialization
        public Object run(VirtualFrame frame,
                        @Cached GetClassNode getClassNode,
                        @Cached GetCaughtExceptionNode getCaughtExceptionNode,
                        @Cached ReadCallerFrameNode readCallerFrameNode,
                        @Cached GetTracebackNode getTracebackNode) {
            PException currentException = getCaughtExceptionNode.execute(frame);
            assert currentException != PException.NO_EXCEPTION;
            if (currentException == null) {
                return factory().createTuple(new PNone[]{PNone.NONE, PNone.NONE, PNone.NONE});
            } else {
                PBaseException exception = currentException.getExceptionObject();
                Reference currentFrameInfo = PArguments.getCurrentFrameInfo(frame);
                PFrame escapedFrame = readCallerFrameNode.executeWith(frame, currentFrameInfo, 0);
                currentFrameInfo.markAsEscaped();
                PTraceback exceptionTraceback = getTracebackNode.execute(frame, exception);
                // n.b. a call to 'sys.exc_info' always creates a new traceback with the current
                // frame and links (via 'tb_next') to the traceback of the exception
                PTraceback chainedTraceback;
                if (exceptionTraceback != null) {
                    chainedTraceback = factory().createTraceback(escapedFrame, exceptionTraceback);
                } else {
                    // it's still possible that there is no traceback if, for example, the exception
                    // has been thrown and caught and did never escape
                    chainedTraceback = factory().createTraceback(escapedFrame, currentException);
                }
                exception.setTraceback(chainedTraceback);
                return factory().createTuple(new Object[]{getClassNode.execute(exception), exception, chainedTraceback});
            }
        }

    }

    @Builtin(name = "_getframe", minNumOfPositionalArgs = 0, maxNumOfPositionalArgs = 1, needsFrame = true)
    @GenerateNodeFactory
    public abstract static class GetFrameNode extends PythonBuiltinNode {
        @Specialization
        PFrame first(VirtualFrame frame, @SuppressWarnings("unused") PNone arg,
                        @Shared("caller") @Cached ReadCallerFrameNode readCallerNode) {
            PFrame requested = escapeFrame(frame, 0, readCallerNode);
            // there must always be *the current frame*
            assert requested != null : "frame must not be null";
            return requested;
        }

        @Specialization
        PFrame counted(VirtualFrame frame, int num,
                        @Shared("caller") @Cached ReadCallerFrameNode readCallerNode,
                        @Shared("callStackDepthProfile") @Cached("createBinaryProfile()") ConditionProfile callStackDepthProfile) {
            PFrame requested = escapeFrame(frame, num, readCallerNode);
            if (callStackDepthProfile.profile(requested == null)) {
                throw raiseCallStackDepth();
            }
            return requested;
        }

        @Specialization(rewriteOn = ArithmeticException.class)
        PFrame countedLong(VirtualFrame frame, long num,
                        @Shared("caller") @Cached ReadCallerFrameNode readCallerNode,
                        @Shared("callStackDepthProfile") @Cached("createBinaryProfile()") ConditionProfile callStackDepthProfile) {
            return counted(frame, PInt.intValueExact(num), readCallerNode, callStackDepthProfile);
        }

        @Specialization
        PFrame countedLongOvf(VirtualFrame frame, long num,
                        @Shared("caller") @Cached ReadCallerFrameNode readCallerNode,
                        @Shared("callStackDepthProfile") @Cached("createBinaryProfile()") ConditionProfile callStackDepthProfile) {
            try {
                return counted(frame, PInt.intValueExact(num), readCallerNode, callStackDepthProfile);
            } catch (ArithmeticException e) {
                throw raiseCallStackDepth();
            }
        }

        @Specialization(rewriteOn = ArithmeticException.class)
        PFrame countedPInt(VirtualFrame frame, PInt num,
                        @Shared("caller") @Cached ReadCallerFrameNode readCallerNode,
                        @Shared("callStackDepthProfile") @Cached("createBinaryProfile()") ConditionProfile callStackDepthProfile) {
            return counted(frame, num.intValueExact(), readCallerNode, callStackDepthProfile);
        }

        @Specialization
        PFrame countedPIntOvf(VirtualFrame frame, PInt num,
                        @Shared("caller") @Cached ReadCallerFrameNode readCallerNode,
                        @Shared("callStackDepthProfile") @Cached("createBinaryProfile()") ConditionProfile callStackDepthProfile) {
            try {
                return counted(frame, num.intValueExact(), readCallerNode, callStackDepthProfile);
            } catch (ArithmeticException e) {
                throw raiseCallStackDepth();
            }
        }

        private static PFrame escapeFrame(VirtualFrame frame, int num, ReadCallerFrameNode readCallerNode) {
            Reference currentFrameInfo = PArguments.getCurrentFrameInfo(frame);
            currentFrameInfo.markAsEscaped();
            return readCallerNode.executeWith(frame, currentFrameInfo, num);
        }

        private PException raiseCallStackDepth() {
            return raise(ValueError, "call stack is not deep enough");
        }
    }

    @Builtin(name = "getfilesystemencoding", minNumOfPositionalArgs = 0)
    @GenerateNodeFactory
    public abstract static class GetFileSystemEncodingNode extends PythonBuiltinNode {
        @Specialization
        @TruffleBoundary
        protected String getFileSystemEncoding() {
            return System.getProperty("file.encoding");
        }
    }

    @Builtin(name = "getfilesystemencodeerrors", minNumOfPositionalArgs = 0)
    @GenerateNodeFactory
    public abstract static class GetFileSystemEncodeErrorsNode extends PythonBuiltinNode {
        @Specialization
        protected String getFileSystemEncoding() {
            return "surrogateescape";
        }
    }

    @Builtin(name = "intern", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class InternNode extends PythonBuiltinNode {
        @Specialization
        @TruffleBoundary
        String doBytes(String s) {
            return s.intern();
        }

        @Specialization
        @TruffleBoundary
        PString doBytes(PString ps) {
            String s = ps.getValue();
            return factory().createString(s.intern());
        }
    }

    @Builtin(name = "getdefaultencoding", minNumOfPositionalArgs = 0)
    @GenerateNodeFactory
    public abstract static class GetDefaultEncodingNode extends PythonBuiltinNode {
        @Specialization
        @TruffleBoundary
        protected String getFileSystemEncoding() {
            return Charset.defaultCharset().name();
        }
    }

    @Builtin(name = "getsizeof", minNumOfPositionalArgs = 1, maxNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    public abstract static class GetsizeofNode extends PythonBinaryBuiltinNode {
        @Child private CastToIntegerFromIntNode castToIntNode = CastToIntegerFromIntNode.create();

        @Specialization(guards = "isNoValue(dflt)")
        protected Object doGeneric(VirtualFrame frame, Object object, @SuppressWarnings("unused") PNone dflt,
                        @Cached("createWithError()") LookupAndCallUnaryNode callSizeofNode) {
            Object result = castToIntNode.execute(callSizeofNode.executeObject(frame, object));
            return checkResult(result);
        }

        @Specialization(guards = "!isNoValue(dflt)")
        protected Object doGeneric(VirtualFrame frame, Object object, Object dflt,
                        @Cached("createWithoutError()") LookupAndCallUnaryNode callSizeofNode) {
            Object result = castToIntNode.execute(callSizeofNode.executeObject(frame, object));
            if (result == PNone.NO_VALUE) {
                return dflt;
            }
            return checkResult(result);
        }

        private Object checkResult(Object result) {
            long value = -1;
            if (result instanceof Number) {
                value = ((Number) result).longValue();
            } else if (result instanceof PInt) {
                try {
                    value = ((PInt) result).longValueExact();
                } catch (ArithmeticException e) {
                    // fall through
                }
            }
            if (value < 0) {
                throw raise(ValueError, "__sizeof__() should return >= 0");
            }
            return value;
        }

        protected LookupAndCallUnaryNode createWithError() {
            return LookupAndCallUnaryNode.create(__SIZEOF__, () -> new NoAttributeHandler() {
                @Override
                public Object execute(Object receiver) {
                    throw raise(TypeError, "Type %p doesn't define %s", receiver, __SIZEOF__);
                }
            });
        }

        protected LookupAndCallUnaryNode createWithoutError() {
            return LookupAndCallUnaryNode.create(__SIZEOF__);
        }
    }

    @Builtin(name = "__graal_get_toolchain_path", minNumOfPositionalArgs = 1)
    @TypeSystemReference(PythonArithmeticTypes.class)
    @GenerateNodeFactory
    public abstract static class GetToolPathNode extends PythonUnaryBuiltinNode {

        @Specialization
        @TruffleBoundary
        protected Object getToolPath(String tool) {
            Env env = getContext().getEnv();
            LanguageInfo llvmInfo = env.getInternalLanguages().get(LLVM_LANGUAGE);
            Toolchain toolchain = env.lookup(llvmInfo, Toolchain.class);
            TruffleFile toolPath = toolchain.getToolPath(tool);
            if (toolPath == null) {
                return PNone.NONE;
            }
            return toolPath.toString();
        }
    }

}
