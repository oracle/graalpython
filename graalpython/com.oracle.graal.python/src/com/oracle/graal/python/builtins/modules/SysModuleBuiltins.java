/*
 * Copyright (c) 2017, 2018, Oracle and/or its affiliates. All rights reserved.
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
import java.lang.management.ManagementFactory;
import java.nio.ByteOrder;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.builtins.Builtin;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.exception.PBaseException;
import com.oracle.graal.python.builtins.objects.ints.PInt;
import com.oracle.graal.python.builtins.objects.str.PString;
import com.oracle.graal.python.nodes.SpecialAttributeNames;
import com.oracle.graal.python.nodes.call.special.LookupAndCallUnaryNode;
import com.oracle.graal.python.nodes.call.special.LookupAndCallUnaryNode.NoAttributeHandler;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonBinaryBuiltinNode;
import com.oracle.graal.python.nodes.object.GetClassNode;
import com.oracle.graal.python.nodes.util.CastToIntegerFromIntNode;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.graal.python.runtime.PythonCore;
import com.oracle.graal.python.runtime.PythonOptions;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.graal.python.runtime.exception.PythonErrorType;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.TruffleLanguage.ContextReference;
import com.oracle.truffle.api.TruffleOptions;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.DirectCallNode;
import com.oracle.truffle.api.nodes.RootNode;

@CoreFunctions(defineModule = "sys")
public class SysModuleBuiltins extends PythonBuiltins {
    private static final String LICENSE = "Copyright (c) Oracle and/or its affiliates. Licensed under the Universal Permissive License v 1.0 as shown at http://oss.oracle.com/licenses/upl.";
    private static final String COMPILE_TIME;
    static {
        String compile_time;
        try {
            compile_time = new Date(PythonBuiltins.class.getResource("PythonBuiltins.class").openConnection().getLastModified()).toString();
        } catch (IOException e) {
            compile_time = "";
        }
        COMPILE_TIME = compile_time;
    }

    public static final String[] SYS_PREFIX_ATTRIBUTES = new String[]{"prefix", "exec_prefix", "base_prefix", "base_exec_prefix"};

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

        String executable = PythonOptions.getOption(core.getContext(), PythonOptions.ExecutablePath);
        if (TruffleOptions.AOT || !core.getContext().isExecutableAccessAllowed()) {
            // cannot set the path at this time since the binary is not yet known; will be patched
            // in the context
            builtinConstants.put("executable", PNone.NONE);
            builtinConstants.put(SpecialAttributeNames.GRAAL_PYTHON_EXECUTABLE_LIST, PNone.NONE);
        } else {
            StringBuilder sb = new StringBuilder();
            ArrayList<String> exec_list = new ArrayList<>();
            sb.append(System.getProperty("java.home")).append(PythonCore.FILE_SEPARATOR).append("bin").append(PythonCore.FILE_SEPARATOR).append("java");
            exec_list.add(sb.toString());
            sb.append(' ');
            for (String arg : ManagementFactory.getRuntimeMXBean().getInputArguments()) {
                if (arg.matches("-Xrunjdwp:transport=dt_socket,server=y,address=\\d+,suspend=y")) {
                    arg = arg.replace("suspend=y", "suspend=n");
                }
                sb.append(arg).append(' ');
                exec_list.add(arg);
            }
            sb.append("-classpath ");
            exec_list.add("-classpath");
            sb.append(System.getProperty("java.class.path")).append(' ');
            exec_list.add(System.getProperty("java.class.path"));
            // we really don't care what the main class or its arguments were - this should
            // always help us launch Graal.Python
            sb.append("com.oracle.graal.python.shell.GraalPythonMain");
            exec_list.add("com.oracle.graal.python.shell.GraalPythonMain");
            builtinConstants.put("executable", sb.toString());
            builtinConstants.put(SpecialAttributeNames.GRAAL_PYTHON_EXECUTABLE_LIST, core.factory().createList(exec_list.toArray()));
        }

        String executable = PythonOptions.getOption(core.getContext(), PythonOptions.ExecutablePath);
        if (!executable.isEmpty() && core.getContext().isExecutableAccessAllowed()) {
            builtinConstants.put("executable", executable);
        }

        builtinConstants.put("modules", core.factory().createDict());
        builtinConstants.put("path", core.factory().createList());
        builtinConstants.put("builtin_module_names", core.factory().createTuple(core.builtinModuleNames()));
        builtinConstants.put("maxsize", Integer.MAX_VALUE);
        builtinConstants.put("version_info", core.factory().createTuple(new Object[]{PythonLanguage.MAJOR, PythonLanguage.MINOR, PythonLanguage.MICRO, "dev", 0}));
        builtinConstants.put("version", PythonLanguage.VERSION +
                        " (" + COMPILE_TIME + ")" +
                        "\n[" + Truffle.getRuntime().getName() + ", Java " + System.getProperty("java.version") + "]");
        builtinConstants.put("flags", core.factory().createTuple(new Object[]{
                        false, // bytes_warning
                        false, // debug
                        true,  // dont_write_bytecode
                        false, // hash_randomization
                        false, // ignore_environment
                        PythonOptions.getFlag(core.getContext(), PythonOptions.InspectFlag), // inspect
                        PythonOptions.getFlag(core.getContext(), PythonOptions.InspectFlag), // interactive
                        false, // isolated
                        PythonOptions.getFlag(core.getContext(), PythonOptions.NoSiteFlag), // no_site
                        PythonOptions.getFlag(core.getContext(), PythonOptions.NoUserSiteFlag), // no_user_site
                        false, // optimize
                        PythonOptions.getFlag(core.getContext(), PythonOptions.QuietFlag), // quiet
                        PythonOptions.getFlag(core.getContext(), PythonOptions.VerboseFlag), // verbose
        }));
        builtinConstants.put("graal_python_home", core.getContext().getLanguage().getHome());
        builtinConstants.put("graal_python_core_home", PythonOptions.getOption(core.getContext(), PythonOptions.CoreHome));
        builtinConstants.put("graal_python_stdlib_home", PythonOptions.getOption(core.getContext(), PythonOptions.StdLibHome));
        builtinConstants.put("graal_python_opaque_filesystem", PythonOptions.getOption(core.getContext(), PythonOptions.OpaqueFilesystem));
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
        builtinConstants.put("__gmultiarch", getPythonArch() + "-" + os);

        super.initialize(core);
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
        if (property.toLowerCase().contains("cygwin")) {
            os = "cygwin";
        } else if (property.toLowerCase().contains("linux")) {
            os = "linux";
        } else if (property.toLowerCase().contains("mac")) {
            os = "darwin";
        } else if (property.toLowerCase().contains("windows")) {
            os = "win32";
        } else if (property.toLowerCase().contains("sunos")) {
            os = "sunos";
        } else if (property.toLowerCase().contains("freebsd")) {
            os = "freebsd";
        }
        return os;
    }

    @Builtin(name = "exc_info", fixedNumOfPositionalArgs = 0)
    @GenerateNodeFactory
    public static abstract class ExcInfoNode extends PythonBuiltinNode {
        @Specialization
        public Object run(
                        @Cached("create()") GetClassNode getClassNode) {
            PythonContext context = getContext();
            PException currentException = context.getCaughtException();
            if (currentException == null) {
                return factory().createTuple(new PNone[]{PNone.NONE, PNone.NONE, PNone.NONE});
            } else {
                PBaseException exception = currentException.getExceptionObject();
                exception.reifyException();
                return factory().createTuple(new Object[]{getClassNode.execute(exception), exception, exception.getTraceback(factory())});
            }
        }
    }

    @Builtin(name = "_getframe", minNumOfPositionalArgs = 0, maxNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public static abstract class GetFrameNode extends PythonBuiltinNode {

        @Child private DirectCallNode call;

        @Specialization
        Object first(@SuppressWarnings("unused") PNone arg) {
            return counted(0);
        }

        /*
         * This is necessary for the time being to be compatible with the old TruffleException
         * behavior. (it only captures the frames if a CallTarget boundary is crossed)
         */
        private static final class GetStackTraceRootNode extends RootNode {
            private final ContextReference<PythonContext> contextRef;

            protected GetStackTraceRootNode(PythonLanguage language) {
                super(language);
                this.contextRef = language.getContextReference();
            }

            @Override
            public Object execute(VirtualFrame frame) {
                CompilerDirectives.transferToInterpreter();
                throw contextRef.get().getCore().raise(ValueError, null);
            }

            @Override
            public boolean isCaptureFramesForTrace() {
                return true;
            }
        }

        @Specialization
        @TruffleBoundary
        Object counted(int num) {
            if (call == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                GetStackTraceRootNode rootNode = new GetStackTraceRootNode(getRootNode().getLanguage(PythonLanguage.class));
                call = insert(Truffle.getRuntime().createDirectCallNode(Truffle.getRuntime().createCallTarget(rootNode)));
            }
            int actual = num + 1; // skip dummy frame
            try {
                call.call(new Object[0]);
                throw raise(PythonErrorType.SystemError, "should not reach here");
            } catch (PException e) {
                PBaseException exception = e.getExceptionObject();
                exception.reifyException();
                if (actual >= exception.getStackTrace().size()) {
                    throw raiseCallStackDepth();
                }
                return exception.getPFrame(factory(), Math.max(0, actual));
            }
        }

        @Specialization(rewriteOn = ArithmeticException.class)
        Object countedLong(long num) {
            return counted(PInt.intValueExact(num));
        }

        @Specialization
        Object countedLongOvf(long num) {
            try {
                return counted(PInt.intValueExact(num));
            } catch (ArithmeticException e) {
                throw raiseCallStackDepth();
            }
        }

        @Specialization(rewriteOn = ArithmeticException.class)
        Object countedPInt(PInt num) {
            return counted(num.intValueExact());
        }

        @Specialization
        Object countedPIntOvf(PInt num) {
            try {
                return counted(num.intValueExact());
            } catch (ArithmeticException e) {
                throw raiseCallStackDepth();
            }
        }

        private PException raiseCallStackDepth() {
            return raise(ValueError, "call stack is not deep enough");
        }

    }

    @Builtin(name = "getfilesystemencoding", fixedNumOfPositionalArgs = 0)
    @GenerateNodeFactory
    public static abstract class GetFileSystemEncodingNode extends PythonBuiltinNode {
        @Specialization
        protected String getFileSystemEncoding() {
            return System.getProperty("file.encoding");
        }
    }

    @Builtin(name = "getfilesystemencodeerrors", fixedNumOfPositionalArgs = 0)
    @GenerateNodeFactory
    public static abstract class GetFileSystemEncodeErrorsNode extends PythonBuiltinNode {
        @Specialization
        protected String getFileSystemEncoding() {
            return "surrogateescape";
        }
    }

    @Builtin(name = "intern", fixedNumOfPositionalArgs = 1)
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

    @Builtin(name = "getdefaultencoding", fixedNumOfPositionalArgs = 0)
    @GenerateNodeFactory
    public static abstract class GetDefaultEncodingNode extends PythonBuiltinNode {
        @Specialization
        @TruffleBoundary
        protected String getFileSystemEncoding() {
            return Charset.defaultCharset().name();
        }
    }

    @Builtin(name = "getsizeof", minNumOfPositionalArgs = 1, maxNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    public static abstract class GetsizeofNode extends PythonBinaryBuiltinNode {
        @Child private CastToIntegerFromIntNode castToIntNode = CastToIntegerFromIntNode.create();

        @Specialization(guards = "isNoValue(dflt)")
        protected Object doGeneric(Object object, @SuppressWarnings("unused") PNone dflt,
                        @Cached("createWithError()") LookupAndCallUnaryNode callSizeofNode) {
            Object result = castToIntNode.execute(callSizeofNode.executeObject(object));
            return checkResult(result);
        }

        @Specialization(guards = "!isNoValue(dflt)")
        protected Object doGeneric(Object object, Object dflt,
                        @Cached("createWithoutError()") LookupAndCallUnaryNode callSizeofNode) {
            Object result = castToIntNode.execute(callSizeofNode.executeObject(object));
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

}
