/*
 * Copyright (c) 2017, 2021, Oracle and/or its affiliates. All rights reserved.
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
import static com.oracle.graal.python.nodes.BuiltinNames.BUILTINS;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.STDERR;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.STDIN;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.STDOUT;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.__EXCEPTHOOK__;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.__MODULE__;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.__STDERR__;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.__STDIN__;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.__STDOUT__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__SIZEOF__;

import java.io.IOException;
import java.nio.ByteOrder;
import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Map;

import org.graalvm.collections.EconomicSet;
import org.graalvm.nativeimage.ImageInfo;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.annotations.ArgumentClinic;
import com.oracle.graal.python.annotations.ArgumentClinic.ClinicConversion;
import com.oracle.graal.python.builtins.Builtin;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.Python3Core;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.modules.SysModuleBuiltinsClinicProviders.GetFrameNodeClinicProviderGen;
import com.oracle.graal.python.builtins.modules.io.BufferedReaderBuiltins;
import com.oracle.graal.python.builtins.modules.io.BufferedWriterBuiltins;
import com.oracle.graal.python.builtins.modules.io.FileIOBuiltins;
import com.oracle.graal.python.builtins.modules.io.PBuffered;
import com.oracle.graal.python.builtins.modules.io.PFileIO;
import com.oracle.graal.python.builtins.modules.io.PTextIO;
import com.oracle.graal.python.builtins.modules.io.StringIOBuiltins;
import com.oracle.graal.python.builtins.modules.io.TextIOWrapperNodes.TextIOWrapperInitNode;
import com.oracle.graal.python.builtins.modules.io.TextIOWrapperNodesFactory.TextIOWrapperInitNodeGen;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.code.PCode;
import com.oracle.graal.python.builtins.objects.exception.GetExceptionTracebackNode;
import com.oracle.graal.python.builtins.objects.exception.PBaseException;
import com.oracle.graal.python.builtins.objects.frame.PFrame;
import com.oracle.graal.python.builtins.objects.frame.PFrame.Reference;
import com.oracle.graal.python.builtins.objects.function.PArguments;
import com.oracle.graal.python.builtins.objects.ints.PInt;
import com.oracle.graal.python.builtins.objects.list.PList;
import com.oracle.graal.python.builtins.objects.module.PythonModule;
import com.oracle.graal.python.builtins.objects.namespace.PSimpleNamespace;
import com.oracle.graal.python.builtins.objects.object.PythonObject;
import com.oracle.graal.python.builtins.objects.str.PString;
import com.oracle.graal.python.builtins.objects.str.StringNodes;
import com.oracle.graal.python.builtins.objects.traceback.GetTracebackNode;
import com.oracle.graal.python.builtins.objects.traceback.LazyTraceback;
import com.oracle.graal.python.builtins.objects.traceback.PTraceback;
import com.oracle.graal.python.builtins.objects.traceback.TracebackBuiltins;
import com.oracle.graal.python.builtins.objects.tuple.PTuple;
import com.oracle.graal.python.builtins.objects.tuple.StructSequence;
import com.oracle.graal.python.builtins.objects.type.TypeNodes;
import com.oracle.graal.python.lib.PyLongAsIntNode;
import com.oracle.graal.python.lib.PyLongAsIntNodeGen;
import com.oracle.graal.python.lib.PyLongAsLongAndOverflowNode;
import com.oracle.graal.python.lib.PyLongAsLongAndOverflowNodeGen;
import com.oracle.graal.python.lib.PyLongCheckNode;
import com.oracle.graal.python.lib.PyNumberAsSizeNode;
import com.oracle.graal.python.lib.PyObjectCallMethodObjArgs;
import com.oracle.graal.python.lib.PyObjectLookupAttr;
import com.oracle.graal.python.lib.PyObjectStrAsObjectNode;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PGuards;
import com.oracle.graal.python.nodes.attributes.ReadAttributeFromObjectNode;
import com.oracle.graal.python.nodes.call.special.LookupAndCallUnaryNode;
import com.oracle.graal.python.nodes.call.special.LookupAndCallUnaryNode.NoAttributeHandler;
import com.oracle.graal.python.nodes.frame.ReadCallerFrameNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonBinaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonClinicBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.clinic.ArgumentClinicProvider;
import com.oracle.graal.python.nodes.object.GetClassNode;
import com.oracle.graal.python.nodes.util.CannotCastException;
import com.oracle.graal.python.nodes.util.CastToJavaStringNode;
import com.oracle.graal.python.nodes.util.ExceptionStateNodes.GetCaughtExceptionNode;
import com.oracle.graal.python.runtime.PosixSupportLibrary;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.graal.python.runtime.PythonOptions;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.graal.python.runtime.formatting.IntegerFormatter;
import com.oracle.graal.python.runtime.object.PythonObjectFactory;
import com.oracle.graal.python.util.CharsetMapping;
import com.oracle.graal.python.util.OverflowException;
import com.oracle.graal.python.util.PythonUtils;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.TruffleFile;
import com.oracle.truffle.api.TruffleLanguage.Env;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.profiles.ConditionProfile;
import com.oracle.truffle.api.source.Source;

@CoreFunctions(defineModule = "sys", isEager = true)
public class SysModuleBuiltins extends PythonBuiltins {
    private static final String LICENSE = "Copyright (c) Oracle and/or its affiliates. Licensed under the Universal Permissive License v 1.0 as shown at http://oss.oracle.com/licenses/upl.";
    private static final String COMPILE_TIME;
    public static final String PLATFORM_DARWIN = "darwin";
    public static final String PLATFORM_WIN32 = "win32";
    public static final PNone FRAMEWORK = PNone.NONE;
    public static final int MAXSIZE = Integer.MAX_VALUE;
    public static final long HASH_MULTIPLIER = 1000003L;
    public static final int HASH_BITS = 61;
    public static final long HASH_MODULUS = (1L << HASH_BITS) - 1;
    public static final long HASH_INF = 314159;
    public static final long HASH_NAN = 0;
    public static final long HASH_IMAG = HASH_MULTIPLIER;

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

    static final StructSequence.BuiltinTypeDescriptor VERSION_INFO_DESC = new StructSequence.BuiltinTypeDescriptor(
                    PythonBuiltinClassType.PVersionInfo,
                    // @formatter:off The formatter joins these lines making it less readable
                    "sys.version_info\n" +
                    "\n" +
                    "Version information as a named tuple.",
                    // @formatter:on
                    5,
                    new String[]{
                                    "major", "minor", "micro",
                                    "releaselevel", "serial",
                    },
                    new String[]{
                                    "Major release number", "Minor release number", "Patch release number",
                                    "'alpha', 'beta', 'candidate', or 'final'", "Serial release number"
                    },
                    false);

    static final StructSequence.BuiltinTypeDescriptor FLAGS_DESC = new StructSequence.BuiltinTypeDescriptor(
                    PythonBuiltinClassType.PFlags,
                    // @formatter:off The formatter joins these lines making it less readable
                    "sys.flags\n" +
                    "\n" +
                    "Flags provided through command line arguments or environment vars.",
                    // @formatter:on
                    15,
                    new String[]{
                                    "debug", "inspect", "interactive", "optimize", "dont_write_bytecode",
                                    "no_user_site", "no_site", "ignore_environment", "verbose",
                                    "bytes_warning", "quiet", "hash_randomization", "isolated",
                                    "dev_mode", "utf8_mode"
                    },
                    new String[]{
                                    "-d", "-i", "-i", "-O or -OO", "-B",
                                    "-s", "-S", "-E", "-v",
                                    "-b", "-q", "-R", "-I",
                                    "-X dev", "-X utf8"
                    },
                    false);

    static final StructSequence.BuiltinTypeDescriptor FLOAT_INFO_DESC = new StructSequence.BuiltinTypeDescriptor(
                    PythonBuiltinClassType.PFloatInfo,
                    // @formatter:off The formatter joins these lines making it less readable
                    "sys.float_info\n" +
                    "\n" +
                    "A named tuple holding information about the float type. It contains low level\n" +
                    "information about the precision and internal representation. Please study\n" +
                    "your system's :file:`float.h` for more information.",
                    // @formatter:on
                    11,
                    new String[]{
                                    "max",
                                    "max_exp",
                                    "max_10_exp",
                                    "min",
                                    "min_exp",
                                    "min_10_exp",
                                    "dig",
                                    "mant_dig",
                                    "epsilon",
                                    "radix",
                                    "rounds"
                    },
                    new String[]{
                                    "DBL_MAX -- maximum representable finite float",
                                    "DBL_MAX_EXP -- maximum int e such that radix**(e-1) is representable",
                                    "DBL_MAX_10_EXP -- maximum int e such that 10**e is representable",
                                    "DBL_MIN -- Minimum positive normalized float",
                                    "DBL_MIN_EXP -- minimum int e such that radix**(e-1) is a normalized float",
                                    "DBL_MIN_10_EXP -- minimum int e such that 10**e is a normalized",
                                    "DBL_DIG -- digits",
                                    "DBL_MANT_DIG -- mantissa digits",
                                    "DBL_EPSILON -- Difference between 1 and the next representable float",
                                    "FLT_RADIX -- radix of exponent",
                                    "FLT_ROUNDS -- rounding mode"
                    });

    static final StructSequence.BuiltinTypeDescriptor INT_INFO_DESC = new StructSequence.BuiltinTypeDescriptor(
                    PythonBuiltinClassType.PIntInfo,
                    // @formatter:off The formatter joins these lines making it less readable
                    "sys.int_info\n" +
                    "\n" +
                    "A named tuple that holds information about Python's\n" +
                    "internal representation of integers.  The attributes are read only.",
                    // @formatter:on
                    2,
                    new String[]{
                                    "bits_per_digit", "sizeof_digit"
                    },
                    new String[]{
                                    "size of a digit in bits", "size in bytes of the C type used to represent a digit"
                    });

    static final StructSequence.BuiltinTypeDescriptor HASH_INFO_DESC = new StructSequence.BuiltinTypeDescriptor(
                    PythonBuiltinClassType.PHashInfo,
                    // @formatter:off The formatter joins these lines making it less readable
                    "hash_info\n" +
                    "\n" +
                    "A named tuple providing parameters used for computing\n" +
                    "hashes. The attributes are read only.",
                    // @formatter:on
                    9,
                    new String[]{
                                    "width", "modulus", "inf", "nan", "imag", "algorithm", "hash_bits",
                                    "seed_bits", "cutoff"
                    },
                    new String[]{
                                    "width of the type used for hashing, in bits",
                                    "prime number giving the modulus on which the hash function is based",
                                    "value to be used for hash of a positive infinity",
                                    "value to be used for hash of a nan",
                                    "multiplier used for the imaginary part of a complex number",
                                    "name of the algorithm for hashing of str, bytes and memoryviews",
                                    "internal output size of hash algorithm",
                                    "seed size of hash algorithm",
                                    "small string optimization cutoff"
                    });

    static final StructSequence.BuiltinTypeDescriptor THREAD_INFO_DESC = new StructSequence.BuiltinTypeDescriptor(
                    PythonBuiltinClassType.PThreadInfo,
                    // @formatter:off The formatter joins these lines making it less readable
                    "sys.thread_info\n" +
                    "\n" +
                    "A named tuple holding information about the thread implementation.",
                    // @formatter:on
                    3,
                    new String[]{
                                    "name", "lock", "version"
                    },
                    new String[]{
                                    "name of the thread implementation", "name of the lock implementation",
                                    "name and version of the thread library"
                    });

    public static final StructSequence.BuiltinTypeDescriptor UNRAISABLE_HOOK_ARGS_DESC = new StructSequence.BuiltinTypeDescriptor(
                    PythonBuiltinClassType.PUnraisableHookArgs,
                    // @formatter:off The formatter joins these lines making it less readable
                    "UnraisableHookArgs\n" +
                    "\n" +
                    "Type used to pass arguments to sys.unraisablehook.",
                    // @formatter:on
                    5,
                    new String[]{
                                    "exc_type", "exc_value", "exc_traceback", "err_msg", "object"
                    },
                    new String[]{
                                    "Exception type", "Exception value", "Exception traceback", "Error message", "Object causing the exception"
                    });

    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return SysModuleBuiltinsFactory.getFactories();
    }

    protected static PSimpleNamespace makeImplementation(PythonObjectFactory factory, PTuple versionInfo, String gmultiarch) {
        final PSimpleNamespace ns = factory.createSimpleNamespace();
        ns.setAttribute("name", "graalpython");
        ns.setAttribute("cache_tag", "graalpython-" + PythonLanguage.MAJOR + PythonLanguage.MINOR);
        ns.setAttribute("version", versionInfo);
        ns.setAttribute("_multiarch", gmultiarch);
        ns.setAttribute("hexversion", PythonLanguage.VERSION_HEX);
        return ns;
    }

    @Override
    public void initialize(Python3Core core) {
        StructSequence.initType(core, VERSION_INFO_DESC);
        StructSequence.initType(core, FLAGS_DESC);
        StructSequence.initType(core, FLOAT_INFO_DESC);
        StructSequence.initType(core, INT_INFO_DESC);
        StructSequence.initType(core, HASH_INFO_DESC);
        StructSequence.initType(core, THREAD_INFO_DESC);
        StructSequence.initType(core, UNRAISABLE_HOOK_ARGS_DESC);

        builtinConstants.put("abiflags", "");
        builtinConstants.put("byteorder", ByteOrder.nativeOrder() == ByteOrder.LITTLE_ENDIAN ? "little" : "big");
        builtinConstants.put("copyright", LICENSE);
        final PythonObjectFactory factory = PythonObjectFactory.getUncached();
        builtinConstants.put("modules", factory.createDict());
        builtinConstants.put("path", factory.createList());
        builtinConstants.put("builtin_module_names", factory.createTuple(core.builtinModuleNames()));
        builtinConstants.put("maxsize", MAXSIZE);
        final PTuple versionInfo = factory.createStructSeq(VERSION_INFO_DESC, PythonLanguage.MAJOR, PythonLanguage.MINOR, PythonLanguage.MICRO, PythonLanguage.RELEASE_LEVEL_STRING,
                        PythonLanguage.RELEASE_SERIAL);
        builtinConstants.put("version_info", versionInfo);
        builtinConstants.put("api_version", PythonLanguage.API_VERSION);
        builtinConstants.put("version", PythonLanguage.VERSION +
                        " (" + COMPILE_TIME + ")" +
                        "\n[Graal, " + Truffle.getRuntime().getName() + ", Java " + System.getProperty("java.version") + "]");
        // the default values taken from JPython
        builtinConstants.put("float_info", factory.createStructSeq(FLOAT_INFO_DESC,
                        Double.MAX_VALUE,           // DBL_MAX
                        Double.MAX_EXPONENT + 1,    // DBL_MAX_EXP
                        308,                        // DBL_MIN_10_EXP
                        Double.MIN_VALUE,           // DBL_MIN
                        Double.MIN_EXPONENT,        // DBL_MIN_EXP
                        -307,                       // DBL_MIN_10_EXP
                        10,                         // DBL_DIG
                        53,                         // DBL_MANT_DIG
                        2.2204460492503131e-16,     // DBL_EPSILON
                        2,                          // FLT_RADIX
                        1                           // FLT_ROUNDS
        ));
        builtinConstants.put("int_info", factory.createStructSeq(INT_INFO_DESC, 32, 4));
        builtinConstants.put("hash_info", factory.createStructSeq(HASH_INFO_DESC,
                        64,                         // width
                        HASH_MODULUS,               // modulus
                        HASH_INF,                   // inf
                        HASH_NAN,                   // nan
                        HASH_IMAG,                  // imag
                        "java",                     // algorithm
                        64,                         // hash_bits
                        0,                          // seed_bits
                        0                           // cutoff
        ));
        builtinConstants.put("thread_info", factory.createStructSeq(THREAD_INFO_DESC, PNone.NONE, PNone.NONE, PNone.NONE));
        builtinConstants.put("maxunicode", IntegerFormatter.LIMIT_UNICODE.intValue() - 1);

        String os = PythonUtils.getPythonOSName();
        builtinConstants.put("platform", os);
        if (os.equals(PLATFORM_DARWIN)) {
            builtinConstants.put("_framework", FRAMEWORK);
        }
        final String gmultiarch = PythonUtils.getPythonArch() + "-" + os;
        builtinConstants.put("__gmultiarch", gmultiarch);

        PFileIO stdin = factory.createFileIO(PythonBuiltinClassType.PFileIO);
        FileIOBuiltins.FileIOInit.internalInit(stdin, "<stdin>", 0, "r");
        builtinConstants.put(STDIN, stdin);
        builtinConstants.put(__STDIN__, stdin);

        PFileIO stdout = factory.createFileIO(PythonBuiltinClassType.PFileIO);
        FileIOBuiltins.FileIOInit.internalInit(stdout, "<stdout>", 1, "w");
        builtinConstants.put(STDOUT, stdout);
        builtinConstants.put(__STDOUT__, stdout);

        PFileIO stderr = factory.createFileIO(PythonBuiltinClassType.PFileIO);
        stderr.setUTF8Write(true);
        FileIOBuiltins.FileIOInit.internalInit(stderr, "<stderr>", 2, "w");
        builtinConstants.put(STDERR, stderr);
        builtinConstants.put(__STDERR__, stderr);
        builtinConstants.put("implementation", makeImplementation(factory, versionInfo, gmultiarch));
        builtinConstants.put("hexversion", PythonLanguage.VERSION_HEX);

        builtinConstants.put("float_repr_style", "short");
        builtinConstants.put("meta_path", factory.createList());
        builtinConstants.put("path_hooks", factory.createList());
        builtinConstants.put("path_importer_cache", factory.createDict());

        // default prompt for interactive shell
        builtinConstants.put("ps1", ">>> ");
        // continue prompt for interactive shell
        builtinConstants.put("ps2", "... ");

        super.initialize(core);

        // we need these during core initialization, they are re-set in postInitialize
        postInitialize0(core);
    }

    public void postInitialize0(Python3Core core) {
        super.postInitialize(core);
        PythonModule sys = core.lookupBuiltinModule("sys");
        PythonContext context = core.getContext();
        String[] args = context.getEnv().getApplicationArguments();
        final PythonObjectFactory factory = PythonObjectFactory.getUncached();
        sys.setAttribute("argv", factory.createList(Arrays.copyOf(args, args.length, Object[].class)));

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
            sys.setAttribute("executable", context.getOption(PythonOptions.Executable));
            sys.setAttribute("_base_executable", context.getOption(PythonOptions.Executable));
        }
        sys.setAttribute("dont_write_bytecode", context.getOption(PythonOptions.DontWriteBytecodeFlag));
        String pycachePrefix = context.getOption(PythonOptions.PyCachePrefix);
        sys.setAttribute("pycache_prefix", pycachePrefix.isEmpty() ? PNone.NONE : pycachePrefix);

        String strWarnoption = context.getOption(PythonOptions.WarnOptions);
        Object[] warnoptions;
        if (strWarnoption.length() > 0) {
            String[] strWarnoptions = context.getOption(PythonOptions.WarnOptions).split(",");
            warnoptions = new Object[strWarnoptions.length];
            System.arraycopy(strWarnoptions, 0, warnoptions, 0, strWarnoptions.length);
        } else {
            warnoptions = PythonUtils.EMPTY_OBJECT_ARRAY;
        }
        sys.setAttribute("warnoptions", factory.createList(warnoptions));

        Env env = context.getEnv();
        String option = context.getOption(PythonOptions.PythonPath);

        boolean capiSeparate = !capiHome.equals(coreHome);

        Object[] path;
        int pathIdx = 0;
        int defaultPathsLen = 2;
        if (capiSeparate) {
            defaultPathsLen++;
        }
        if (option.length() > 0) {
            String[] split = option.split(context.getEnv().getPathSeparator());
            path = new Object[split.length + defaultPathsLen];
            PythonUtils.arraycopy(split, 0, path, 0, split.length);
            pathIdx = split.length;
        } else {
            path = new Object[defaultPathsLen];
        }
        path[pathIdx++] = stdlibHome;
        path[pathIdx++] = coreHome + env.getFileNameSeparator() + "modules";
        if (capiSeparate) {
            // include our native modules on the path
            path[pathIdx++] = capiHome + env.getFileNameSeparator() + "modules";
        }
        PList sysPaths = factory.createList(path);
        sys.setAttribute("path", sysPaths);
        sys.setAttribute("flags", factory.createStructSeq(SysModuleBuiltins.FLAGS_DESC,
                        PInt.intValue(!context.getOption(PythonOptions.PythonOptimizeFlag)), // debug
                        PInt.intValue(context.getOption(PythonOptions.InspectFlag)), // inspect
                        PInt.intValue(context.getOption(PythonOptions.TerminalIsInteractive)), // interactive
                        PInt.intValue(context.getOption(PythonOptions.PythonOptimizeFlag)), // optimize
                        PInt.intValue(context.getOption(PythonOptions.DontWriteBytecodeFlag)),  // dont_write_bytecode
                        PInt.intValue(context.getOption(PythonOptions.NoUserSiteFlag)), // no_user_site
                        PInt.intValue(context.getOption(PythonOptions.NoSiteFlag)), // no_site
                        PInt.intValue(context.getOption(PythonOptions.IgnoreEnvironmentFlag)), // ignore_environment
                        PInt.intValue(context.getOption(PythonOptions.VerboseFlag)), // verbose
                        0, // bytes_warning
                        PInt.intValue(context.getOption(PythonOptions.QuietFlag)), // quiet
                        0, // hash_randomization
                        PInt.intValue(context.getOption(PythonOptions.IsolateFlag)), // isolated
                        false, // dev_mode
                        0 // utf8_mode
        ));
        sys.setAttribute(__EXCEPTHOOK__, sys.getAttribute("excepthook"));
    }

    @Override
    public void postInitialize(Python3Core core) {
        postInitialize0(core);
        initStd(core);
    }

    @TruffleBoundary
    public void initStd(Python3Core core) {
        TextIOWrapperInitNode textIOWrapperInitNode = TextIOWrapperInitNodeGen.getUncached();
        PythonObjectFactory factory = PythonObjectFactory.getUncached();

        // wrap std in/out/err
        GraalPythonModuleBuiltins gp = (GraalPythonModuleBuiltins) core.lookupBuiltinModule("__graalpython__").getBuiltins();
        String stdioEncoding = gp.getStdIOEncoding();
        String stdioError = gp.getStdIOError();
        Object posixSupport = core.getContext().getPosixSupport();
        PosixSupportLibrary posixLib = PosixSupportLibrary.getUncached();
        PythonModule sysModule = core.lookupBuiltinModule("sys");

        PBuffered reader = factory.createBufferedReader(PythonBuiltinClassType.PBufferedReader);
        BufferedReaderBuiltins.BufferedReaderInit.internalInit(reader, (PFileIO) get(builtinConstants, "stdin"), BufferedReaderBuiltins.DEFAULT_BUFFER_SIZE, factory, posixSupport,
                        posixLib);
        setWrapper("stdin", "__stdin__", "r", stdioEncoding, stdioError, reader, sysModule, textIOWrapperInitNode, core.factory());

        PBuffered writer = factory.createBufferedWriter(PythonBuiltinClassType.PBufferedWriter);
        BufferedWriterBuiltins.BufferedWriterInit.internalInit(writer, (PFileIO) get(builtinConstants, "stdout"), BufferedReaderBuiltins.DEFAULT_BUFFER_SIZE, factory, posixSupport,
                        posixLib);
        PTextIO stdout = setWrapper("stdout", "__stdout__", "w", stdioEncoding, stdioError, writer, sysModule, textIOWrapperInitNode, core.factory());

        writer = factory.createBufferedWriter(PythonBuiltinClassType.PBufferedWriter);
        BufferedWriterBuiltins.BufferedWriterInit.internalInit(writer, (PFileIO) get(builtinConstants, "stderr"), BufferedReaderBuiltins.DEFAULT_BUFFER_SIZE, factory, posixSupport,
                        posixLib);
        PTextIO stderr = setWrapper("stderr", "__stderr__", "w", stdioEncoding, "backslashreplace", writer, sysModule, textIOWrapperInitNode, core.factory());

        // register atexit close std out/err
        core.getContext().registerAtexitHook((ctx) -> {
            callClose(stdout);
            callClose(stderr);
        });
    }

    private static Object get(Map<Object, Object> builtinConstants, Object key) {
        return builtinConstants.get(key);
    }

    private static PTextIO setWrapper(String name, String specialName, String mode, String encoding, String error, PBuffered buffered, PythonModule sysModule,
                    TextIOWrapperInitNode textIOWrapperInitNode, PythonObjectFactory factory) {
        PTextIO textIOWrapper = factory.createTextIO(PythonBuiltinClassType.PTextIOWrapper);
        textIOWrapperInitNode.execute(null, textIOWrapper, buffered, encoding, error, PNone.NONE, true, true);

        setAttribute(textIOWrapper, "mode", mode);
        setAttribute(sysModule, name, textIOWrapper);
        setAttribute(sysModule, specialName, textIOWrapper);

        return textIOWrapper;
    }

    private static void setAttribute(PythonObject obj, String key, Object value) {
        obj.setAttribute(key, value);
    }

    private static void callClose(Object obj) {
        try {
            PyObjectCallMethodObjArgs.getUncached().execute(null, obj, "close");
        } catch (PException e) {
        }
    }

    @Builtin(name = "exc_info", needsFrame = true)
    @GenerateNodeFactory
    public abstract static class ExcInfoNode extends PythonBuiltinNode {

        public static Object fast(VirtualFrame frame, GetClassNode getClassNode, GetCaughtExceptionNode getCaughtExceptionNode, PythonObjectFactory factory) {
            final PException currentException = getCaughtExceptionNode.execute(frame);
            if (currentException == null) {
                return factory.createTuple(new PNone[]{PNone.NONE});
            }
            return factory.createTuple(new Object[]{getClassNode.execute(currentException.getUnreifiedException())});
        }

        @Specialization
        public Object run(VirtualFrame frame,
                        @Cached GetClassNode getClassNode,
                        @Cached GetCaughtExceptionNode getCaughtExceptionNode,
                        @Cached GetTracebackNode getTracebackNode) {
            PException currentException = getCaughtExceptionNode.execute(frame);
            assert currentException != PException.NO_EXCEPTION;
            if (currentException == null) {
                return factory().createTuple(new PNone[]{PNone.NONE, PNone.NONE, PNone.NONE});
            } else {
                PBaseException exception = currentException.getEscapedException();
                LazyTraceback lazyTraceback = currentException.getTraceback();
                PTraceback traceback = null;
                if (lazyTraceback != null) {
                    traceback = getTracebackNode.execute(lazyTraceback);
                }
                return factory().createTuple(new Object[]{getClassNode.execute(exception), exception, traceback == null ? PNone.NONE : traceback});
            }
        }

    }

    // ATTENTION: this is intentionally a PythonBuiltinNode and not PythonUnaryBuiltinNode,
    // because we need a guarantee that this builtin will get its own stack frame in order to
    // be able to count how many frames down the call stack we need to walk
    @Builtin(name = "_getframe", parameterNames = "depth", minNumOfPositionalArgs = 0, needsFrame = true, alwaysNeedsCallerFrame = true)
    @ArgumentClinic(name = "depth", defaultValue = "0", conversion = ClinicConversion.Int)
    @GenerateNodeFactory
    public abstract static class GetFrameNode extends PythonClinicBuiltinNode {
        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return GetFrameNodeClinicProviderGen.INSTANCE;
        }

        @Specialization
        PFrame counted(VirtualFrame frame, int num,
                        @Cached ReadCallerFrameNode readCallerNode,
                        @Cached ConditionProfile callStackDepthProfile) {
            PFrame requested = escapeFrame(frame, num, readCallerNode);
            if (callStackDepthProfile.profile(requested == null)) {
                throw raiseCallStackDepth();
            }
            return requested;
        }

        private static PFrame escapeFrame(VirtualFrame frame, int num, ReadCallerFrameNode readCallerNode) {
            Reference currentFrameInfo = PArguments.getCurrentFrameInfo(frame);
            currentFrameInfo.markAsEscaped();
            return readCallerNode.executeWith(frame, currentFrameInfo, num);
        }

        private PException raiseCallStackDepth() {
            return raise(ValueError, ErrorMessages.CALL_STACK_NOT_DEEP_ENOUGH);
        }
    }

    @Builtin(name = "getfilesystemencoding", minNumOfPositionalArgs = 0)
    @GenerateNodeFactory
    public abstract static class GetFileSystemEncodingNode extends PythonBuiltinNode {
        @Specialization
        @TruffleBoundary
        public static String getFileSystemEncoding() {
            String javaEncoding = System.getProperty("file.encoding");
            String pythonEncoding = CharsetMapping.getPythonEncodingNameFromJavaName(javaEncoding);
            // Fallback on returning the property value if no mapping found
            return pythonEncoding != null ? pythonEncoding : javaEncoding;
        }
    }

    @Builtin(name = "getfilesystemencodeerrors", minNumOfPositionalArgs = 0)
    @GenerateNodeFactory
    public abstract static class GetFileSystemEncodeErrorsNode extends PythonBuiltinNode {
        @Specialization
        protected static String getFileSystemEncoding() {
            return "surrogateescape";
        }
    }

    @Builtin(name = "intern", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class InternNode extends PythonBuiltinNode {
        private PString doIntern(Object str, StringNodes.InternStringNode internNode) {
            final PString interned = internNode.execute(str);
            if (interned == null) {
                throw raise(TypeError, ErrorMessages.CANNOT_INTERN_P, str);
            }
            return interned;
        }

        @Specialization
        Object doString(String s,
                        @Shared("internNode") @Cached StringNodes.InternStringNode internNode) {
            return doIntern(s, internNode);
        }

        @Specialization
        Object doPString(PString s,
                        @Shared("internNode") @Cached StringNodes.InternStringNode internNode) {
            return doIntern(s, internNode);
        }

        @Fallback
        Object doOthers(Object obj) {
            throw raise(TypeError, ErrorMessages.ARG_MUST_BE_S_NOT_P, "intern()", "str", obj);
        }
    }

    @Builtin(name = "getdefaultencoding", minNumOfPositionalArgs = 0)
    @GenerateNodeFactory
    public abstract static class GetDefaultEncodingNode extends PythonBuiltinNode {
        @Specialization
        @TruffleBoundary
        protected static String getFileSystemEncoding() {
            return Charset.defaultCharset().name();
        }
    }

    @Builtin(name = "getsizeof", minNumOfPositionalArgs = 1, maxNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    public abstract static class GetsizeofNode extends PythonBinaryBuiltinNode {
        @Child PyNumberAsSizeNode asSizeNode;

        @Specialization(guards = "isNoValue(dflt)")
        protected Object doGeneric(VirtualFrame frame, Object object, @SuppressWarnings("unused") PNone dflt,
                        @Cached("createWithError()") LookupAndCallUnaryNode callSizeofNode) {
            return checkResult(frame, callSizeofNode.executeObject(frame, object));
        }

        @Specialization(guards = "!isNoValue(dflt)")
        protected Object doGeneric(VirtualFrame frame, Object object, Object dflt,
                        @Cached("createWithoutError()") LookupAndCallUnaryNode callSizeofNode) {
            Object result = callSizeofNode.executeObject(frame, object);
            if (result == PNone.NO_VALUE) {
                return dflt;
            }
            return checkResult(frame, result);
        }

        private Object checkResult(VirtualFrame frame, Object result) {
            int value = getAsSizeNode().executeExact(frame, result);
            if (value < 0) {
                throw raise(ValueError, ErrorMessages.SHOULD_RETURN, "__sizeof__()", ">= 0");
            }
            return value;
        }

        private PyNumberAsSizeNode getAsSizeNode() {
            if (asSizeNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                asSizeNode = insert(PyNumberAsSizeNode.create());
            }
            return asSizeNode;
        }

        protected LookupAndCallUnaryNode createWithError() {
            return LookupAndCallUnaryNode.create(__SIZEOF__, () -> new NoAttributeHandler() {
                @Override
                public Object execute(Object receiver) {
                    throw raise(TypeError, ErrorMessages.TYPE_DOESNT_DEFINE_METHOD, receiver, __SIZEOF__);
                }
            });
        }

        protected static LookupAndCallUnaryNode createWithoutError() {
            return LookupAndCallUnaryNode.create(__SIZEOF__);
        }
    }

    // TODO implement support for audit events
    @GenerateUncached
    public abstract static class AuditNode extends Node {
        public abstract void execute(String event, Object[] arguments);

        public void audit(String event, Object... arguments) {
            execute(event, arguments);
        }

        @Specialization
        void doAudit(@SuppressWarnings("unused") String event, @SuppressWarnings("unused") Object[] arguments) {
        }

        public static AuditNode create() {
            return SysModuleBuiltinsFactory.AuditNodeGen.create();
        }
    }

    @Builtin(name = "is_finalizing")
    @GenerateNodeFactory
    public abstract static class IsFinalizingNode extends PythonBuiltinNode {
        @Specialization
        boolean doGeneric() {
            return getContext().isFinalizing();
        }
    }

    @Builtin(name = "gettrace")
    @GenerateNodeFactory
    abstract static class GetTrace extends PythonBuiltinNode {
        @Specialization
        static Object gettrace() {
            return PNone.NONE;
        }
    }

    @Builtin(name = "excepthook", minNumOfPositionalArgs = 4, maxNumOfPositionalArgs = 4, declaresExplicitSelf = true, doc = "excepthook($module, exctype, value, traceback, /)\n" +
                    "--\n" +
                    "\n" +
                    "Handle an exception by displaying it with a traceback on sys.stderr.")
    @GenerateNodeFactory
    abstract static class ExceptHookNode extends PythonBuiltinNode {
        static final String CAUSE_MESSAGE = "\nThe above exception was the direct cause of the following exception:\n\n";
        static final String CONTEXT_MESSAGE = "\nDuring handling of the above exception, another exception occurred:\n\n";
        static final int TRACEBACK_LIMIT = 1000;
        static final int TB_RECURSIVE_CUTOFF = 3;
        static final String ATTR_TB_LIMIT = "tracebacklimit";
        static final String ATTR_PRINT_FILE_AND_LINE = "print_file_and_line";
        static final String ATTR_MSG = "msg";
        static final String ATTR_FILENAME = "filename";
        static final String ATTR_LINENO = "lineno";
        static final String ATTR_OFFSET = "offset";
        static final String ATTR_TEXT = "text";
        static final String VALUE_STRING = "<string>";
        static final String VALUE_UNKNOWN = "<unknown>";

        @Child private ReadAttributeFromObjectNode readAttributeFromObjectNode;
        @Child private PyLongAsLongAndOverflowNode pyLongAsLongAndOverflowNode;
        @Child private PyLongAsIntNode pyLongAsIntNode;
        @Child private PyLongCheckNode pyLongCheckNode;
        @Child private GetExceptionTracebackNode getExceptionTracebackNode;
        @Child private GetClassNode getClassNode;
        @Child private TypeNodes.GetNameNode getNameNode;
        @Child private PyObjectLookupAttr objectLookupAttr;
        @Child private CastToJavaStringNode castToJavaStringNode;
        @Child private PyObjectStrAsObjectNode strAsObjNode;
        @Child private TracebackBuiltins.GetTracebackFrameNode getTracebackFrameNode;
        @Child private StringIOBuiltins.WriteNode writeNode;

        @CompilerDirectives.ValueType
        static final class SyntaxErrData {
            final Object message;
            final Object fileName;
            final int lineNo;
            final int offset;
            final Object text;

            SyntaxErrData(Object message, Object fileName, int lineNo, int offset, Object text) {
                this.message = message;
                this.fileName = fileName;
                this.lineNo = lineNo;
                this.offset = offset;
                this.text = text;
            }
        }

        private void print(VirtualFrame frame, Object file, String data) {
            if (writeNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                writeNode = insert(StringIOBuiltins.WriteNode.create());
            }
            writeNode.execute(frame, file, data);
        }

        private boolean isEmptyString(Object value) {
            final String s = tryCastToString(value);
            return s != null && s.isEmpty();
        }

        private PFrame getFrame(VirtualFrame frame, PTraceback tb) {
            if (getTracebackFrameNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                getTracebackFrameNode = insert(TracebackBuiltins.GetTracebackFrameNode.create());
            }
            return getTracebackFrameNode.execute(frame, tb);
        }

        private PCode getCode(VirtualFrame frame, PTraceback tb) {
            return factory().createCode(getFrame(frame, tb).getTarget());
        }

        private Object str(VirtualFrame frame, Object value) {
            if (strAsObjNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                strAsObjNode = insert(PyObjectStrAsObjectNode.create());
            }

            try {
                return strAsObjNode.execute(frame, value);
            } catch (PException pe) {
                return null;
            }
        }

        private String castToString(Object value) {
            if (castToJavaStringNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                castToJavaStringNode = insert(CastToJavaStringNode.create());
            }
            return castToJavaStringNode.execute(value);
        }

        private String tryCastToString(Object value) {
            try {
                return castToString(value);
            } catch (CannotCastException e) {
                return null;
            }
        }

        private Object lookupAttr(VirtualFrame frame, Object object, String attr) {
            if (objectLookupAttr == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                objectLookupAttr = insert(PyObjectLookupAttr.create());
            }
            return objectLookupAttr.execute(frame, object, attr);
        }

        private String lookupStrAttr(VirtualFrame frame, Object object, String attr) {
            final Object value = lookupAttr(frame, object, attr);
            return value != PNone.NO_VALUE ? castToString(value) : null;
        }

        private boolean hasAttr(VirtualFrame frame, Object object, String attr) {
            return lookupAttr(frame, object, attr) != PNone.NO_VALUE;
        }

        private String getName(Object type) {
            if (getNameNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                getNameNode = insert(TypeNodes.GetNameNode.create());
            }
            return getNameNode.execute(type);
        }

        private Object getClass(Object object) {
            if (getClassNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                getClassNode = insert(GetClassNode.create());
            }
            return getClassNode.execute(object);
        }

        private PTraceback getExceptionTraceback(PBaseException e) {
            if (getExceptionTracebackNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                getExceptionTracebackNode = insert(GetExceptionTracebackNode.create());
            }
            return getExceptionTracebackNode.execute(e);
        }

        private boolean checkLong(Object object) {
            if (pyLongCheckNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                pyLongCheckNode = insert(PyLongCheckNode.create());
            }
            return pyLongCheckNode.execute(object);
        }

        private int asInt(VirtualFrame frame, Object object) {
            if (pyLongAsIntNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                pyLongAsIntNode = insert(PyLongAsIntNodeGen.create());
            }
            return pyLongAsIntNode.execute(frame, object);
        }

        private long asLongAndOverflow(VirtualFrame frame, Object object, long overflowValue) {
            if (pyLongAsLongAndOverflowNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                pyLongAsLongAndOverflowNode = insert(PyLongAsLongAndOverflowNodeGen.create());
            }
            try {
                final long val = pyLongAsLongAndOverflowNode.execute(frame, object);
                return Math.min(val, overflowValue);
            } catch (OverflowException e) {
                return overflowValue;
            }
        }

        private Object readAttr(Object object, String attribute) {
            if (readAttributeFromObjectNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                readAttributeFromObjectNode = insert(ReadAttributeFromObjectNode.create());
            }
            return readAttributeFromObjectNode.execute(object, attribute);
        }

        void printTraceBack(VirtualFrame frame, Object out, PTraceback tb) {
            long limit = TRACEBACK_LIMIT;
            final PythonModule sys = getContext().getCore().lookupBuiltinModule("sys");
            final Object limitv = readAttr(sys, ATTR_TB_LIMIT);
            if (checkLong(limitv)) {
                limit = asLongAndOverflow(frame, limitv, MAXSIZE);
                if (limit <= 0) {
                    return;
                }
            }
            print(frame, out, "Traceback (most recent call last):\n");
            printInternal(frame, out, tb, limit);
        }

        void printLineRepeated(VirtualFrame frame, Object out, int count) {
            int cnt = count;
            cnt -= TB_RECURSIVE_CUTOFF;
            final StringBuilder sb = PythonUtils.newStringBuilder("  [Previous line repeated ");
            PythonUtils.append(sb, cnt, (cnt > 1) ? " more times]\n" : " more time]\n");
            print(frame, out, PythonUtils.sbToString(sb));
        }

        void displayLine(VirtualFrame frame, Object out, String fileName, int lineNo, String name) {
            if (fileName == null || name == null) {
                return;
            }

            final StringBuilder sb = PythonUtils.newStringBuilder("  File \"");
            PythonUtils.append(sb, fileName, "\", line ", lineNo, ", in ", name, "\n");
            print(frame, out, PythonUtils.sbToString(sb));
            // ignore errors since we can't report them, can we?
            displaySourceLine(frame, out, fileName, lineNo, 4);
        }

        private static String getIndent(int indent) {
            StringBuilder sb = PythonUtils.newStringBuilder();
            for (int i = 0; i < indent; i++) {
                PythonUtils.append(sb, ' ');
            }
            return PythonUtils.sbToString(sb);
        }

        @TruffleBoundary
        CharSequence getSourceLine(String fileName, int lineNo) {
            final PythonContext context = getContext();
            TruffleFile file = context.getEnv().getInternalTruffleFile(fileName);
            final Source source;
            try {
                source = PythonLanguage.newSource(context, file, fileName);
                return source.getCharacters(lineNo);
            } catch (IOException ioe) {
                return null;
            }
        }

        void displaySourceLine(VirtualFrame frame, Object out, String fileName, int lineNo, int indent) {
            final CharSequence line = getSourceLine(fileName, lineNo);
            print(frame, out, getIndent(indent));
            print(frame, out, PythonUtils.trimLeft(line));
            print(frame, out, "\n");
        }

        void printInternal(VirtualFrame frame, Object out, PTraceback traceback, long limit) {
            int depth = 0;
            String lastFile = null;
            int lastLine = -1;
            String lastName = null;
            int cnt = 0;
            PTraceback tb1 = traceback;
            PTraceback tb = traceback;
            while (tb1 != null) {
                depth++;
                tb1 = tb1.getNext();
            }
            while (tb != null && depth > limit) {
                depth--;
                tb = tb.getNext();
            }
            while (tb != null) {
                final PCode code = getCode(frame, tb);
                if (lastFile == null ||
                                !code.getFilename().equals(lastName) ||
                                lastLine == -1 || tb.getLineno() != lastLine ||
                                lastName == null || !code.getName().equals(lastName)) {
                    if (cnt > TB_RECURSIVE_CUTOFF) {
                        printLineRepeated(frame, out, cnt);
                    }
                    lastFile = code.getFilename();
                    lastLine = tb.getLineno();
                    lastName = code.getName();
                    cnt = 0;
                }
                cnt++;
                if (cnt <= TB_RECURSIVE_CUTOFF) {
                    displayLine(frame, out, code.getFilename(), tb.getLineno(), code.getName());
                }
                tb = tb.getNext();
            }
            if (cnt > TB_RECURSIVE_CUTOFF) {
                printLineRepeated(frame, out, cnt);
            }
        }

        SyntaxErrData parseSyntaxError(VirtualFrame frame, Object err) {
            String msg, fileName = null, text = null;
            int lineNo = 0, offset = 0, hold = 0;

            // new style errors. `err' is an instance
            msg = lookupStrAttr(frame, err, ATTR_MSG);
            if (msg == null) {
                return new SyntaxErrData(msg, fileName, lineNo, offset, text);
            }

            Object v = lookupAttr(frame, err, ATTR_FILENAME);
            if (v == PNone.NO_VALUE) {
                return new SyntaxErrData(msg, fileName, lineNo, offset, text);
            }
            if (v == PNone.NONE) {
                fileName = VALUE_STRING;
            } else {
                fileName = castToString(str(frame, v));
            }

            v = lookupAttr(frame, err, ATTR_LINENO);
            if (v == PNone.NO_VALUE) {
                return new SyntaxErrData(msg, fileName, lineNo, offset, text);
            }
            try {
                hold = asInt(frame, v);
            } catch (PException pe) {
                return new SyntaxErrData(msg, fileName, lineNo, offset, text);
            }

            lineNo = hold;

            v = lookupAttr(frame, err, ATTR_OFFSET);
            if (v == PNone.NO_VALUE) {
                return new SyntaxErrData(msg, fileName, lineNo, offset, text);
            }
            if (v == PNone.NONE) {
                offset = -1;
            } else {
                try {
                    hold = asInt(frame, v);
                } catch (PException pe) {
                    return new SyntaxErrData(msg, fileName, lineNo, offset, text);
                }
                offset = hold;
            }

            v = lookupAttr(frame, err, ATTR_TEXT);
            if (v == PNone.NO_VALUE) {
                return new SyntaxErrData(msg, fileName, lineNo, offset, text);
            }
            if (v == PNone.NONE) {
                text = null;
            } else {
                text = castToString(v);
            }

            return new SyntaxErrData(msg, fileName, lineNo, offset, text);
        }

        void printErrorText(VirtualFrame frame, Object out, SyntaxErrData syntaxErrData) {
            String text = castToString(str(frame, syntaxErrData.text));
            int offset = syntaxErrData.offset;

            if (offset >= 0) {
                if (offset > 0 && offset == text.length() && text.charAt(offset - 1) == '\n') {
                    offset--;
                }
                int nl;
                while (true) {
                    nl = PythonUtils.lastIndexOf(text, '\n');
                    if (nl == -1 || nl >= offset) {
                        break;
                    }
                    offset -= nl + 1;
                    text = PythonUtils.substring(text, nl + 1);
                }
                int idx = 0;
                while (text.charAt(idx) == ' ' || text.charAt(idx) == '\t' || text.charAt(idx) == '\f') {
                    idx++;
                    offset--;
                }
                text = PythonUtils.substring(text, idx);
            }

            print(frame, out, "    ");
            print(frame, out, text);
            if (text.charAt(0) == '\0' || text.charAt(text.length() - 1) != '\n') {
                print(frame, out, "\n");
            }
            if (offset == -1) {
                return;
            }
            print(frame, out, "    ");
            while (--offset > 0) {
                print(frame, out, " ");
            }
            print(frame, out, "^\n");
        }

        private String classNameNoDot(String name) {
            final int i = PythonUtils.lastIndexOf(name, '.');
            return (i > 0) ? PythonUtils.substring(name, i + 1) : name;
        }

        void printException(VirtualFrame frame, Object out, Object excValue) {
            Object value = excValue;
            final Object type = getClass(value);
            if (!PGuards.isPBaseException(value)) {
                print(frame, out, "TypeError: print_exception(): Exception expected for value, ");
                print(frame, out, getName(type));
                print(frame, out, " found\n");
                return;
            }

            final PBaseException exc = (PBaseException) value;
            final PTraceback tb = getExceptionTraceback(exc);
            if (tb != null) {
                printTraceBack(frame, out, tb);
            }

            if (hasAttr(frame, value, ATTR_PRINT_FILE_AND_LINE)) {
                // SyntaxError case
                final SyntaxErrData syntaxErrData = parseSyntaxError(frame, value);
                value = syntaxErrData.message;
                StringBuilder sb = PythonUtils.newStringBuilder("  File \"");
                PythonUtils.append(sb, castToString(str(frame, syntaxErrData.fileName)), "\", line ", syntaxErrData.lineNo, "\n");
                print(frame, out, PythonUtils.sbToString(sb));

                // Can't be bothered to check all those PyFile_WriteString() calls
                if (syntaxErrData.text != null) {
                    printErrorText(frame, out, syntaxErrData);
                }
            }

            {
                // TODO: assert(PyExceptionClass_Check(type));
                String className;
                try {
                    className = getName(type);
                    className = classNameNoDot(className);
                } catch (PException pe) {
                    className = null;
                }
                String moduleName;
                Object v = lookupAttr(frame, type, __MODULE__);
                if (v == PNone.NO_VALUE || !PGuards.isString(v)) {
                    print(frame, out, VALUE_UNKNOWN);
                } else {
                    moduleName = castToString(v);
                    if (!moduleName.equals(BUILTINS)) {
                        print(frame, out, moduleName);
                        print(frame, out, ".");
                    }
                }
                if (className == null) {
                    print(frame, out, VALUE_UNKNOWN);
                } else {
                    print(frame, out, className);
                }
            }

            if (value != PNone.NONE) {
                // only print colon if the str() of the object is not the empty string
                Object v = str(frame, value);
                String s = tryCastToString(v);
                if (v == null) {
                    print(frame, out, ": <exception str() failed>");
                } else if (!PGuards.isString(v) || (s != null && !s.isEmpty())) {
                    print(frame, out, ": ");
                }
                if (s != null) {
                    print(frame, out, s);
                }
            }

            print(frame, out, "\n");
        }

        void printExceptionRecursive(VirtualFrame frame, Object out, Object value, EconomicSet<Object> seen) {
            if (seen != null) {
                // Exception chaining
                seen.add(value);
                if (PGuards.isPBaseException(value)) {
                    final PBaseException exc = (PBaseException) value;
                    final PBaseException cause = exc.getCause();
                    final PBaseException context = exc.getContext();

                    if (cause != null) {
                        if (!seen.contains(cause)) {
                            printExceptionRecursive(frame, out, cause, seen);
                            print(frame, out, CAUSE_MESSAGE);
                        }
                    } else if (context != null && !exc.getSuppressContext()) {
                        if (!seen.contains(context)) {
                            printExceptionRecursive(frame, out, context, seen);
                            print(frame, out, CONTEXT_MESSAGE);
                        }
                    }
                }
            }
            printException(frame, out, value);
        }

        @Specialization
        Object doIt(VirtualFrame frame, PythonModule sys, @SuppressWarnings("unused") Object excType, Object value, PTraceback traceBack) {
            if (PGuards.isPBaseException(value) && traceBack != null) {
                final PBaseException exc = (PBaseException) value;
                final PTraceback currTb = getExceptionTraceback(exc);
                if (currTb == null) {
                    exc.setTraceback(traceBack);
                }
            }

            Object stdErr = lookupAttr(frame, sys, STDERR);
            printExceptionRecursive(frame, stdErr, value, EconomicSet.create());

            return PNone.NONE;
        }
    }
}
