/*
 * Copyright (c) 2017, 2024, Oracle and/or its affiliates.
 * Copyright (c) 2013, Regents of the University of California
 *
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification, are
 * permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this list of
 * conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice, this list of
 * conditions and the following disclaimer in the documentation and/or other materials provided
 * with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS
 * OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
 * MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE
 * COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE
 * GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED
 * AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED
 * OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.oracle.graal.python.runtime;

import static com.oracle.graal.python.builtins.modules.SysModuleBuiltins.INT_MAX_STR_DIGITS_THRESHOLD;
import static com.oracle.graal.python.nodes.StringLiterals.T_DEFAULT;
import static com.oracle.graal.python.nodes.StringLiterals.T_EMPTY_STRING;
import static com.oracle.graal.python.nodes.StringLiterals.T_JAVA;
import static com.oracle.graal.python.nodes.StringLiterals.T_SPACE;
import static com.oracle.graal.python.util.PythonUtils.tsLiteral;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.graalvm.options.OptionCategory;
import org.graalvm.options.OptionDescriptor;
import org.graalvm.options.OptionDescriptors;
import org.graalvm.options.OptionKey;
import org.graalvm.options.OptionStability;
import org.graalvm.options.OptionType;
import org.graalvm.options.OptionValues;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.builtins.modules.SysModuleBuiltins;
import com.oracle.graal.python.builtins.objects.str.StringUtils;
import com.oracle.graal.python.util.PythonUtils;
import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.Option;
import com.oracle.truffle.api.TruffleLanguage.Env;
import com.oracle.truffle.api.dsl.Idempotent;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.strings.TruffleString;

/**
 * The options for Python. Note that some options have an effect on the AST structure, and thus must
 * be the same for all contexts in an engine. We annotate these with {@link EngineOption} and the
 * PythonLanguage will ensure that these are matched across contexts.
 */
@Option.Group(PythonLanguage.ID)
public final class PythonOptions {
    private static final String J_STRING_LIST_SEPARATOR = "🏆";
    private static final TruffleString T_STRING_LIST_SEPARATOR = tsLiteral(J_STRING_LIST_SEPARATOR);

    /**
     * Whether Java classes are included that implement the SSL module. These come from packages
     * including (but not limited to): javax.net.ssl, org.bouncycastle, java.security, javax.crypto,
     * sun.security
     */
    public static final boolean WITHOUT_SSL = Boolean.getBoolean("python.WithoutSSL");

    /**
     * Whether cryptographic hashing functions are implemented via java.security.MessageDigest,
     * javax.crypto.Mac and related functions.
     */
    public static final boolean WITHOUT_DIGEST = Boolean.getBoolean("python.WithoutDigest");

    /**
     * Whether Java classes are included that relate to Unix-specific access, modify process
     * properties such as the default timezone, access the platform's Runtime MXBean, or spawn
     * subprocesses are available.
     */
    public static final boolean WITHOUT_PLATFORM_ACCESS = Boolean.getBoolean("python.WithoutPlatformAccess");

    /**
     * This property can be used to exclude zip, zlib, lzma, and bzip2 support from the Python core.
     */
    public static final boolean WITHOUT_COMPRESSION_LIBRARIES = Boolean.getBoolean("python.WithoutCompressionLibraries");

    /**
     * This property can be used to exclude native posix support from the build. Only Java emulation
     * will be available.
     */
    public static final boolean WITHOUT_NATIVE_POSIX = Boolean.getBoolean("python.WithoutNativePosix");

    /**
     * This property can be used to exclude socket and inet support from the Java posix backend.
     */
    public static final boolean WITHOUT_JAVA_INET = Boolean.getBoolean("python.WithoutJavaInet");

    /**
     * This property can be used to disable any usage of JNI.
     */
    public static final boolean WITHOUT_JNI = Boolean.getBoolean("python.WithoutJNI");

    /**
     * This property can be used to control if async actions are automatically scheduled using
     * daemon threads or via embedder calling a polling API on the main thread.
     */
    public static final boolean AUTOMATIC_ASYNC_ACTIONS = !"false".equalsIgnoreCase(System.getProperty("python.AutomaticAsyncActions"));

    /**
     * Whether to use the experimental Bytecode DSL interpreter instead of the manually-written
     * bytecode interpreter.
     */
    public static final boolean ENABLE_BYTECODE_DSL_INTERPRETER = Boolean.getBoolean("python.EnableBytecodeDSLInterpreter");

    public enum HPyBackendMode {
        NFI,
        JNI,
        LLVM
    }

    static final OptionType<HPyBackendMode> HPY_BACKEND_TYPE = new OptionType<>("HPyBackend", s -> {
        try {
            return HPyBackendMode.valueOf(s.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Backend can be one of: " + Arrays.toString(HPyBackendMode.values()));
        }
    });

    private static final OptionType<TruffleString> TS_OPTION_TYPE = new OptionType<>("graal.python.TruffleString", PythonUtils::toTruffleStringUncached);

    private PythonOptions() {
        // no instances
    }

    @Option(category = OptionCategory.EXPERT, help = "Set the home of Python. Equivalent of GRAAL_PYTHONHOME env variable. " +
                    "Determines default values for the CoreHome, StdLibHome, SysBasePrefix, SysPrefix.", usageSyntax = "<path>", stability = OptionStability.STABLE) //
    public static final OptionKey<String> PythonHome = new OptionKey<>("");

    @Option(category = OptionCategory.USER, help = "Set the location of sys.prefix. Overrides any environment variables or Java options.", usageSyntax = "<path>", stability = OptionStability.STABLE) //
    public static final OptionKey<TruffleString> SysPrefix = new OptionKey<>(T_EMPTY_STRING, TS_OPTION_TYPE);

    @Option(category = OptionCategory.EXPERT, help = "Set the location of sys.base_prefix. Overrides any environment variables or Java options.", usageSyntax = "<path>", stability = OptionStability.STABLE) //
    public static final OptionKey<TruffleString> SysBasePrefix = new OptionKey<>(T_EMPTY_STRING, TS_OPTION_TYPE);

    @Option(category = OptionCategory.USER, help = "Set the location of lib/graalpy" + PythonLanguage.GRAALVM_MAJOR + "." + //
                    PythonLanguage.GRAALVM_MINOR + ". Overrides any environment variables or Java options.", //
                    usageSyntax = "<path>", stability = OptionStability.STABLE) //
    public static final OptionKey<TruffleString> CoreHome = new OptionKey<>(T_EMPTY_STRING, TS_OPTION_TYPE);

    @Option(category = OptionCategory.USER, help = "Set the location of lib/python" + PythonLanguage.MAJOR + "." + PythonLanguage.MINOR +
                    ". Overrides any environment variables or Java options.", usageSyntax = "<path>", stability = OptionStability.STABLE) //
    public static final OptionKey<TruffleString> StdLibHome = new OptionKey<>(T_EMPTY_STRING, TS_OPTION_TYPE);

    @Option(category = OptionCategory.USER, help = "Equivalent to the Python -i flag. Inspect interactively after running a script.", usageSyntax = "true|false", stability = OptionStability.STABLE) //
    public static final OptionKey<Boolean> InspectFlag = new OptionKey<>(false);

    @Option(category = OptionCategory.USER, help = "Equivalent to the Python -q flag. Don't  print version and copyright messages on interactive startup.", usageSyntax = "true|false", stability = OptionStability.STABLE) //
    public static final OptionKey<Boolean> QuietFlag = new OptionKey<>(false);

    @Option(category = OptionCategory.USER, help = "Equivalent to the Python -S flag. Don't imply 'import site' on initialization.", usageSyntax = "true|false", stability = OptionStability.STABLE) //
    public static final OptionKey<Boolean> NoSiteFlag = new OptionKey<>(false);

    @Option(category = OptionCategory.USER, help = "Equivalent to the Python -s flag. Don't add user site directory to sys.path.", usageSyntax = "true|false", stability = OptionStability.STABLE) //
    public static final OptionKey<Boolean> NoUserSiteFlag = new OptionKey<>(false);

    @Option(category = OptionCategory.USER, help = "Equivalent to the Python -E flag. Ignore PYTHON* environment variables.", usageSyntax = "true|false", stability = OptionStability.STABLE) //
    public static final OptionKey<Boolean> IgnoreEnvironmentFlag = new OptionKey<>(false);

    @Option(category = OptionCategory.USER, help = "Equivalent to setting the PYTHONPATH environment variable for the standard launcher. ':'-separated list of directories prefixed to the default module search path.", usageSyntax = "<path>[:<path>]", stability = OptionStability.STABLE) //
    public static final OptionKey<TruffleString> PythonPath = new OptionKey<>(T_EMPTY_STRING, TS_OPTION_TYPE);

    @EngineOption @Option(category = OptionCategory.USER, help = "Equivalent to setting the PYTHONIOENCODING environment variable for the standard launcher.", usageSyntax = "<Encoding>[:<errors>]", stability = OptionStability.STABLE) //
    public static final OptionKey<TruffleString> StandardStreamEncoding = new OptionKey<>(T_EMPTY_STRING, TS_OPTION_TYPE);

    @Option(category = OptionCategory.USER, help = "Remove assert statements and any code conditional on the value of __debug__.", usageSyntax = "true|false", stability = OptionStability.STABLE) //
    public static final OptionKey<Boolean> PythonOptimizeFlag = new OptionKey<>(false);

    @Option(category = OptionCategory.USER, help = "Equivalent to the Python -v flag. Turn on verbose mode.", usageSyntax = "true|false", stability = OptionStability.STABLE) //
    public static final OptionKey<Boolean> VerboseFlag = new OptionKey<>(false);

    @Option(category = OptionCategory.USER, help = "Equivalent to the Python -u flag. Force stdout and stderr to be unbuffered.", usageSyntax = "true|false", stability = OptionStability.STABLE) //
    public static final OptionKey<Boolean> UnbufferedIO = new OptionKey<>(false);

    @Option(category = OptionCategory.USER, help = "Equivalent to the Python -I flag. Isolate from the users environment by not adding the cwd to the path", usageSyntax = "true|false", stability = OptionStability.STABLE) //
    public static final OptionKey<Boolean> IsolateFlag = new OptionKey<>(false);

    @Option(category = OptionCategory.USER, help = "Equivalent to the Python -P flag. Don't prepend a potentially unsafe path to sys.path", usageSyntax = "true|false", stability = OptionStability.STABLE) //
    public static final OptionKey<Boolean> SafePathFlag = new OptionKey<>(false);

    @Option(category = OptionCategory.USER, help = "Equivalent to the Python -X warn_default_encoding flag. Enable opt-in EncodingWarning for 'encoding=None'", usageSyntax = "true|false", stability = OptionStability.STABLE) //
    public static final OptionKey<Boolean> WarnDefaultEncodingFlag = new OptionKey<>(false);

    @Option(category = OptionCategory.USER, help = "Equivalent to the Python -X int_max_str_digits option.", stability = OptionStability.STABLE) //
    public static final OptionKey<Integer> IntMaxStrDigits = new OptionKey<>(SysModuleBuiltins.INT_DEFAULT_MAX_STR_DIGITS,
                    new OptionType<>("IntMaxStrDigits", (input) -> {
                        try {
                            int value = Integer.parseInt(input);
                            if (value == 0 || value >= INT_MAX_STR_DIGITS_THRESHOLD) {
                                return value;
                            }
                        } catch (NumberFormatException e) {
                            // fallthrough
                        }
                        throw new IllegalArgumentException(String.format("IntMaxStrDigits: invalid limit; must be >= %d or 0 for unlimited.", INT_MAX_STR_DIGITS_THRESHOLD));
                    }));

    @Option(category = OptionCategory.USER, help = "Equivalent to the Python -B flag. Don't write bytecode files.", usageSyntax = "true|false", stability = OptionStability.STABLE) //
    public static final OptionKey<Boolean> DontWriteBytecodeFlag = new OptionKey<>(true);

    @Option(category = OptionCategory.USER, help = "If this is set, GraalPython will write .pyc files in a mirror directory tree at this path, " +
                    "instead of in __pycache__ directories within the source tree. " +
                    "Equivalent to setting the PYTHONPYCACHEPREFIX environment variable for the standard launcher.", usageSyntax = "<path>", stability = OptionStability.STABLE) //
    public static final OptionKey<TruffleString> PyCachePrefix = new OptionKey<>(T_EMPTY_STRING, TS_OPTION_TYPE);

    @Option(category = OptionCategory.USER, help = "Equivalent to setting the PYTHONWARNINGS environment variable for the standard launcher.", //
                    usageSyntax = "<action>[:<message>[:<category>[:<module>[:<line>]]]][,<action>[:<message>[:<category>[:<module>[:<line>]]]]]", stability = OptionStability.STABLE) //
    public static final OptionKey<TruffleString> WarnOptions = new OptionKey<>(T_EMPTY_STRING, TS_OPTION_TYPE);

    @Option(category = OptionCategory.USER, help = "Equivalent to setting PYTHONHASHSEED environment variable", usageSyntax = "random|[0,4294967295]", stability = OptionStability.STABLE) //
    public static final OptionKey<Optional<Integer>> HashSeed = new OptionKey<>(Optional.empty(),
                    new OptionType<>("HashSeed", input -> {
                        if ("random".equals(input)) {
                            return Optional.empty();
                        }
                        try {
                            return Optional.of(Integer.parseUnsignedInt(input));
                        } catch (NumberFormatException e) {
                            throw new IllegalArgumentException("PYTHONHASHSEED must be \"random\" or an integer in range [0; 4294967295]");
                        }
                    }));

    @EngineOption @Option(category = OptionCategory.USER, help = "Choose the backend for the POSIX module.", usageSyntax = "java|native|llvm", stability = OptionStability.STABLE) //
    public static final OptionKey<TruffleString> PosixModuleBackend = new OptionKey<>(T_JAVA, TS_OPTION_TYPE);

    @EngineOption @Option(category = OptionCategory.USER, help = "Choose the backend for the Sha3 module.", usageSyntax = "java|native", stability = OptionStability.STABLE) //
    public static final OptionKey<TruffleString> Sha3ModuleBackend = new OptionKey<>(T_JAVA, TS_OPTION_TYPE);

    @Option(category = OptionCategory.USER, help = "Install default signal handlers on startup", usageSyntax = "true|false", stability = OptionStability.STABLE) //
    public static final OptionKey<Boolean> InstallSignalHandlers = new OptionKey<>(false);

    @Option(category = OptionCategory.EXPERT, help = "Sets the language and territory, which will be used for initial locale. Format: 'language[_territory]', e.g., 'en_GB'. Leave empty to use the JVM default locale.", stability = OptionStability.STABLE) //
    public static final OptionKey<String> InitialLocale = new OptionKey<>("");

    @Option(category = OptionCategory.USER, help = "Value of the --check-hash-based-pycs command line option" +
                    "- 'default' means the 'check_source' flag in hash-based pycs" +
                    "  determines invalidation" +
                    "- 'always' causes the interpreter to hash the source file for" +
                    "  invalidation regardless of value of 'check_source' bit" +
                    "- 'never' causes the interpreter to always assume hash-based pycs are" +
                    "  valid" +
                    "The default value is 'default'." +
                    "See PEP 552 'Deterministic pycs' for more details.", usageSyntax = "default|always|never", stability = OptionStability.STABLE) //
    public static final OptionKey<TruffleString> CheckHashPycsMode = new OptionKey<>(T_DEFAULT, TS_OPTION_TYPE);

    @Option(category = OptionCategory.INTERNAL, help = "Set the location of C API home. Overrides any environment variables or Java options.", usageSyntax = "<path>", stability = OptionStability.STABLE) //
    public static final OptionKey<TruffleString> CAPI = new OptionKey<>(T_EMPTY_STRING, TS_OPTION_TYPE);

    @EngineOption @Option(category = OptionCategory.INTERNAL, help = "Expose internal sources as normal sources, so they will show up in the debugger and stacks", usageSyntax = "true|false") //
    public static final OptionKey<Boolean> ExposeInternalSources = new OptionKey<>(false);

    @EngineOption @Option(category = OptionCategory.INTERNAL, help = "Eagerly initialize source sections.", usageSyntax = "true|false") //
    public static final OptionKey<Boolean> ForceInitializeSourceSections = new OptionKey<>(false);

    @EngineOption @Option(category = OptionCategory.INTERNAL, help = "Print the java stacktrace. Possible modes:" +
                    "    1   Print Java stacktrace for Java exceptions only." +
                    "    2   Print Java stacktrace for Python exceptions only (ATTENTION: this will have a notable performance impact)." +
                    "    3   Combines 1 and 2.", usageSyntax = "1|2|3") //
    public static final OptionKey<Integer> WithJavaStacktrace = new OptionKey<>(0);

    @Option(category = OptionCategory.INTERNAL, usageSyntax = "true|false", help = "") //
    public static final OptionKey<Boolean> CatchGraalPythonExceptionForUnitTesting = new OptionKey<>(false);

    @EngineOption @Option(category = OptionCategory.INTERNAL, usageSyntax = "true|false", help = "Enable catching all Exceptions in generic try-catch statements.") //
    public static final OptionKey<Boolean> CatchAllExceptions = new OptionKey<>(false);

    @EngineOption @Option(category = OptionCategory.INTERNAL, help = "Choose the backend for HPy binary mode.", usageSyntax = "jni|nfi|llvm", stability = OptionStability.EXPERIMENTAL) //
    public static final OptionKey<HPyBackendMode> HPyBackend = new OptionKey<>(HPyBackendMode.JNI, HPY_BACKEND_TYPE);

    @EngineOption @Option(category = OptionCategory.INTERNAL, usageSyntax = "true|false", help = "If {@code true}, code is enabled that tries to reduce expensive upcalls into the runtime" +
                    "when HPy API functions are used. This is achieved by mirroring data in native memory.", stability = OptionStability.EXPERIMENTAL) //
    public static final OptionKey<Boolean> HPyEnableJNIFastPaths = new OptionKey<>(true);

    @EngineOption @Option(category = OptionCategory.INTERNAL, usageSyntax = "<time>", help = "Specifies the interval (ms) for periodically printing HPy upcall statistics. If {@code 0}" +
                    "or not specified, nothing will be printed (default).", stability = OptionStability.EXPERIMENTAL) //
    public static final OptionKey<Integer> HPyTraceUpcalls = new OptionKey<>(0);

    @Option(category = OptionCategory.INTERNAL, usageSyntax = "<path>", help = "Specify the directory where the JNI library is located.", stability = OptionStability.EXPERIMENTAL) //
    public static final OptionKey<TruffleString> JNIHome = new OptionKey<>(T_EMPTY_STRING, TS_OPTION_TYPE);

    @Option(category = OptionCategory.EXPERT, usageSyntax = "true|false", help = "Prints path to parsed files") //
    public static final OptionKey<Boolean> ParserLogFiles = new OptionKey<>(false);

    @Option(category = OptionCategory.EXPERT, usageSyntax = "<numFiles>", help = "Prints parser time statistics after number of parsed files, set by this option. 0 or <0 means no statistics are printed.") //
    public static final OptionKey<Integer> ParserStatistics = new OptionKey<>(0);

    @EngineOption @Option(category = OptionCategory.EXPERT, usageSyntax = "<maxDepth>", help = "") //
    public static final OptionKey<Integer> AttributeAccessInlineCacheMaxDepth = new OptionKey<>(5);

    @EngineOption @Option(category = OptionCategory.EXPERT, usageSyntax = "<maxDepth>", help = "") //
    public static final OptionKey<Integer> CallSiteInlineCacheMaxDepth = new OptionKey<>(4);

    @EngineOption @Option(category = OptionCategory.EXPERT, usageSyntax = "<limit>", help = "") //
    public static final OptionKey<Integer> VariableArgumentReadUnrollingLimit = new OptionKey<>(5);

    @EngineOption @Option(category = OptionCategory.EXPERT, usageSyntax = "<limit>", help = "") //
    public static final OptionKey<Integer> VariableArgumentInlineCacheLimit = new OptionKey<>(3);

    @EngineOption @Option(category = OptionCategory.EXPERT, usageSyntax = "<limit>", help = "") //
    public static final OptionKey<Integer> NodeRecursionLimit = new OptionKey<>(1);

    @EngineOption @Option(category = OptionCategory.EXPERT, usageSyntax = "true|false", help = "") //
    public static final OptionKey<Boolean> ForceInlineGeneratorCalls = new OptionKey<>(false);

    @Option(category = OptionCategory.EXPERT, usageSyntax = "true|false", help = "Force to automatically import site.py module.", stability = OptionStability.STABLE) //
    public static final OptionKey<Boolean> ForceImportSite = new OptionKey<>(false);

    @Option(category = OptionCategory.EXPERT, usageSyntax = "true|false", help = "Set-up library search paths to include GraalPy's LLVM toolchain library directories.") //
    public static final OptionKey<Boolean> SetupLLVMLibraryPaths = new OptionKey<>(false);

    @Option(category = OptionCategory.EXPERT, usageSyntax = "true|false", help = "This option is set by the Python launcher to tell the language it can print exceptions directly", stability = OptionStability.STABLE) //
    public static final OptionKey<Boolean> AlwaysRunExcepthook = new OptionKey<>(false);

    @Option(category = OptionCategory.INTERNAL, usageSyntax = "<path>", help = "Used by the launcher to pass the path to be executed", stability = OptionStability.STABLE) //
    public static final OptionKey<TruffleString> InputFilePath = new OptionKey<>(T_EMPTY_STRING, TS_OPTION_TYPE);

    // disabling TRegex has an effect on the _sre Python functions that are
    // dynamically created, so we cannot change that option again.
    @EngineOption @Option(category = OptionCategory.EXPERT, usageSyntax = "true|false", help = "Use the optimized TRegex engine. Default true") //
    public static final OptionKey<Boolean> WithTRegex = new OptionKey<>(true);

    @EngineOption @Option(category = OptionCategory.EXPERT, usageSyntax = "true|false", help = "Use the CPython sre engine as a fallback to the TRegex engine.") //
    public static final OptionKey<Boolean> TRegexUsesSREFallback = new OptionKey<>(true);

    @Option(category = OptionCategory.EXPERT, usageSyntax = "true|false", help = "Switch on/off using lazy strings for performance reasons. Default true.") //
    public static final OptionKey<Boolean> LazyStrings = new OptionKey<>(true);

    @EngineOption @Option(category = OptionCategory.EXPERT, usageSyntax = "true|false", help = "Enable forced splitting (of builtins). Default false.") //
    public static final OptionKey<Boolean> EnableForcedSplits = new OptionKey<>(false);

    @Option(category = OptionCategory.EXPERT, usageSyntax = "true|false", help = "Set by the launcher if an interactive console is used to run Python.") //
    public static final OptionKey<Boolean> TerminalIsInteractive = new OptionKey<>(false);

    @Option(category = OptionCategory.EXPERT, usageSyntax = "<width>", help = "Set by the launcher to the terminal width.") //
    public static final OptionKey<Integer> TerminalWidth = new OptionKey<>(80);

    @Option(category = OptionCategory.EXPERT, usageSyntax = "<height>", help = "Set by the launcher to the terminal height.") //
    public static final OptionKey<Integer> TerminalHeight = new OptionKey<>(25);

    @Option(category = OptionCategory.EXPERT, usageSyntax = "<path>", help = "The sys.executable path. Set by the launcher, but can may need to be overridden in certain special situations.", stability = OptionStability.STABLE) //
    public static final OptionKey<TruffleString> Executable = new OptionKey<>(T_EMPTY_STRING, TS_OPTION_TYPE);

    @Option(category = OptionCategory.EXPERT, usageSyntax = "<cmdPart>[" + J_STRING_LIST_SEPARATOR +
                    "<cmdPart>]", help = "The executed command list as string joined by the executable list separator char. This must always correspond to the real, valid command list used to run GraalPython.") //
    public static final OptionKey<TruffleString> ExecutableList = new OptionKey<>(T_EMPTY_STRING, TS_OPTION_TYPE);

    @Option(category = OptionCategory.EXPERT, usageSyntax = "", help = "Option used by the venvlauncher to pass on the launcher target command", stability = OptionStability.STABLE) //
    public static final OptionKey<TruffleString> VenvlauncherCommand = new OptionKey<>(T_EMPTY_STRING, TS_OPTION_TYPE);

    @Option(category = OptionCategory.EXPERT, usageSyntax = "true|false", help = "Determines wether context startup tries to re-use previously cached sources of the core library.") //
    public static final OptionKey<Boolean> WithCachedSources = new OptionKey<>(true);

    @Option(category = OptionCategory.EXPERT, usageSyntax = "true|false", help = "Embedder option: what to print in response to PythonLanguage#toString.") //
    public static final OptionKey<Boolean> UseReprForPrintString = new OptionKey<>(true);

    @EngineOption @Option(category = OptionCategory.EXPERT, usageSyntax = "<limit>", help = "Stop inlining of builtins if caller's cumulative tree size would exceed this limit") //
    public static final OptionKey<Integer> BuiltinsInliningMaxCallerSize = new OptionKey<>(2500);

    @Option(category = OptionCategory.EXPERT, usageSyntax = "true|false", help = "Disable weakref callback processing, signal handling, and other periodic async actions.") //
    public static final OptionKey<Boolean> NoAsyncActions = new OptionKey<>(false);

    @Option(category = OptionCategory.EXPERT, usageSyntax = "true|false", help = "Propagate append operations to lists created as literals back to where they were created, to inform overallocation to avoid having to grow them later.") //
    public static final OptionKey<Boolean> OverallocateLiteralLists = new OptionKey<>(true);

    @Option(category = OptionCategory.EXPERT, usageSyntax = "true|false", help = "Forces AST sharing for inner contexts.") //
    public static final OptionKey<Boolean> ForceSharingForInnerContexts = new OptionKey<>(true);

    @Option(category = OptionCategory.EXPERT, help = "Whether C extension modules should be loaded as native code (as opposed to Sulong bitcode execution).") //
    public static final OptionKey<Boolean> NativeModules = new OptionKey<>(true);

    @EngineOption @Option(category = OptionCategory.USER, usageSyntax = "true|false", help = "Emulate some Jython features that can cause performance degradation") //
    public static final OptionKey<Boolean> EmulateJython = new OptionKey<>(false);

    @Option(category = OptionCategory.EXPERT, usageSyntax = "true|false", help = "Enable tracing of native memory (ATTENTION: this will have significant impact on CExt execution performance).") //
    public static final OptionKey<Boolean> TraceNativeMemory = new OptionKey<>(false);

    @Option(category = OptionCategory.EXPERT, usageSyntax = "true|false", help = "If native memory tracing is enabled, also capture stack.") //
    public static final OptionKey<Boolean> TraceNativeMemoryCalls = new OptionKey<>(false);

    @Option(category = OptionCategory.EXPERT, usageSyntax = "<bytes>", help = "Max native memory heap size (default: 2 GB).") //
    public static final OptionKey<Long> MaxNativeMemory = new OptionKey<>(1L << 31);

    @Option(category = OptionCategory.EXPERT, usageSyntax = "<bytes>", help = "Initial native memory heap size that triggers a GC (default: 256 MB).") //
    public static final OptionKey<Long> InitialNativeMemory = new OptionKey<>(1L << 28);

    @Option(category = OptionCategory.EXPERT, usageSyntax = "true|false", help = "Use the experimental panama backend for NFI.", stability = OptionStability.EXPERIMENTAL) //
    public static final OptionKey<Boolean> UsePanama = new OptionKey<>(false);

    @Option(category = OptionCategory.EXPERT, usageSyntax = "true|false", help = "Set by the launcher to true (false means that GraalPython is being embedded in an application).") //
    public static final OptionKey<Boolean> RunViaLauncher = new OptionKey<>(false);

    @Option(category = OptionCategory.EXPERT, usageSyntax = "true|false", help = "Enable built-in functions on the __graalpython__ module that are useful for debugging.") //
    public static final OptionKey<Boolean> EnableDebuggingBuiltins = new OptionKey<>(false);

    @Option(category = OptionCategory.EXPERT, help = "Disables using frozen modules.") //
    public static final OptionKey<Boolean> DisableFrozenModules = new OptionKey<>(false);

    @Option(category = OptionCategory.EXPERT, help = "Makes bytecode instrumentation node materialization eager instead of lazy.") //
    public static final OptionKey<Boolean> EagerlyMaterializeInstrumentationNodes = new OptionKey<>(false);

    @Option(category = OptionCategory.INTERNAL, help = "The list of the original command line arguments passed to the Python executable.") //
    public static final OptionKey<TruffleString> OrigArgv = new OptionKey<>(T_EMPTY_STRING, TS_OPTION_TYPE);

    @Option(category = OptionCategory.EXPERT, help = "If true, use the system's toolchain for native extension compilation. Otherwise, use the LLVM Toolchain included with GraalVM.") //
    public static final OptionKey<Boolean> UseSystemToolchain = new OptionKey<>(true);

    public static final OptionDescriptors DESCRIPTORS = new PythonOptionsOptionDescriptors();

    @CompilationFinal(dimensions = 1) private static final OptionKey<?>[] ENGINE_OPTION_KEYS;
    @CompilationFinal(dimensions = 1) private static final OptionKey<?>[] OPTION_KEYS;
    static {
        List<OptionKey<?>> options = new ArrayList<>();
        for (OptionDescriptor desc : DESCRIPTORS) {
            options.add(desc.getKey());
        }
        OPTION_KEYS = options.toArray(new OptionKey<?>[options.size()]);

        List<OptionKey<?>> engineOptions = new ArrayList<>();
        for (Field f : PythonOptions.class.getDeclaredFields()) {
            if (f.getAnnotation(EngineOption.class) != null) {
                for (OptionDescriptor desc : DESCRIPTORS) {
                    if (desc.getName().endsWith(f.getName())) {
                        engineOptions.add(desc.getKey());
                    }
                }
            }
        }
        ENGINE_OPTION_KEYS = engineOptions.toArray(new OptionKey<?>[engineOptions.size()]);
    }

    /**
     * A CompilationFinal array of option keys defined here. Do not modify!
     */
    public static OptionKey<?>[] getOptionKeys() {
        return OPTION_KEYS;
    }

    /**
     * A CompilationFinal array of engine option keys defined here. Do not modify!
     */
    public static OptionKey<?>[] getEngineOptionKeys() {
        return ENGINE_OPTION_KEYS;
    }

    /**
     * Copy values into an array for compilation final storage and unrolling lookup.
     */
    public static Object[] createOptionValuesStorage(Env env) {
        Object[] values = new Object[OPTION_KEYS.length];
        for (int i = 0; i < OPTION_KEYS.length; i++) {
            values[i] = env.getOptions().get(OPTION_KEYS[i]);
        }
        return values;
    }

    public static Object[] createEngineOptionValuesStorage(Env env) {
        Object[] values = new Object[ENGINE_OPTION_KEYS.length];
        for (int i = 0; i < ENGINE_OPTION_KEYS.length; i++) {
            values[i] = env.getOptions().get(ENGINE_OPTION_KEYS[i]);
        }
        return values;
    }

    public static OptionValues createEngineOptions(Env env) {
        return new EngineOptionValues(env.getOptions());
    }

    @ExplodeLoop
    @SuppressWarnings("unchecked")
    public static <T> T getOptionUnrolling(Object[] optionValuesStorage, OptionKey<?>[] optionKeys, OptionKey<T> key) {
        assert optionValuesStorage.length == optionKeys.length;
        CompilerAsserts.partialEvaluationConstant(optionKeys);
        for (int i = 0; i < optionKeys.length; i++) {
            CompilerAsserts.partialEvaluationConstant(optionKeys[i]);
            if (optionKeys[i] == key) {
                return (T) optionValuesStorage[i];
            }
        }
        CompilerDirectives.transferToInterpreterAndInvalidate();
        throw new IllegalStateException("Using Python options with a non-Python option key");
    }

    @ExplodeLoop
    public static boolean isEngineOption(OptionKey<?> key) {
        CompilerAsserts.partialEvaluationConstant(ENGINE_OPTION_KEYS);
        for (int i = 0; i < ENGINE_OPTION_KEYS.length; i++) {
            CompilerAsserts.partialEvaluationConstant(ENGINE_OPTION_KEYS[i]);
            if (ENGINE_OPTION_KEYS[i] == key) {
                return true;
            }
        }
        return false;
    }

    /**
     * Check if the options set in the {@code first} and {@code second} set are compatible, i.e,
     * there are no Python per-engine options in these sets that differ.
     */
    public static boolean areOptionsCompatible(OptionValues first, OptionValues second) {
        for (OptionKey<?> key : ENGINE_OPTION_KEYS) {
            if (!first.get(key).equals(second.get(key))) {
                return false;
            }
        }
        return true;
    }

    @Idempotent
    public static int getAttributeAccessInlineCacheMaxDepth() {
        CompilerAsserts.neverPartOfCompilation();
        return PythonLanguage.get(null).getEngineOption(AttributeAccessInlineCacheMaxDepth);
    }

    @Idempotent
    public static int getCallSiteInlineCacheMaxDepth() {
        CompilerAsserts.neverPartOfCompilation();
        return PythonLanguage.get(null).getEngineOption(CallSiteInlineCacheMaxDepth);
    }

    @Idempotent
    public static int getVariableArgumentInlineCacheLimit() {
        CompilerAsserts.neverPartOfCompilation();
        return PythonLanguage.get(null).getEngineOption(VariableArgumentInlineCacheLimit);
    }

    @Idempotent
    public static int getNodeRecursionLimit() {
        CompilerAsserts.neverPartOfCompilation();
        int result = PythonLanguage.get(null).getEngineOption(NodeRecursionLimit);
        // So that we can use byte counters and also Byte.MAX_VALUE as special placeholder
        assert result < Byte.MAX_VALUE;
        return result;
    }

    public static boolean isWithJavaStacktrace(PythonLanguage language) {
        return language.getEngineOption(WithJavaStacktrace) > 0;
    }

    public static boolean isPExceptionWithJavaStacktrace(PythonLanguage language) {
        return language.getEngineOption(WithJavaStacktrace) > 1;
    }

    @TruffleBoundary
    public static TruffleString[] getExecutableList(PythonContext context) {
        TruffleString execListOption = context.getOption(ExecutableList);
        if (execListOption.isEmpty()) {
            return StringUtils.split(context.getOption(Executable), T_SPACE, TruffleString.CodePointLengthNode.getUncached(), TruffleString.IndexOfStringNode.getUncached(),
                            TruffleString.SubstringNode.getUncached(), TruffleString.EqualNode.getUncached());
        } else {
            return StringUtils.split(execListOption, T_STRING_LIST_SEPARATOR, TruffleString.CodePointLengthNode.getUncached(), TruffleString.IndexOfStringNode.getUncached(),
                            TruffleString.SubstringNode.getUncached(), TruffleString.EqualNode.getUncached());
        }
    }

    @TruffleBoundary
    public static TruffleString[] getOrigArgv(PythonContext context) {
        return StringUtils.split(context.getOption(OrigArgv), T_STRING_LIST_SEPARATOR, TruffleString.CodePointLengthNode.getUncached(), TruffleString.IndexOfStringNode.getUncached(),
                        TruffleString.SubstringNode.getUncached(), TruffleString.EqualNode.getUncached());
    }

    /**
     * Marks an @Option as being per-engine rather than per-context
     */
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.FIELD)
    @interface EngineOption {
    }

    private static final class EngineOptionValues implements OptionValues {

        private final Map<OptionKey<?>, Object> engineOptions = new HashMap<>();

        EngineOptionValues(OptionValues contextOptions) {
            for (OptionKey<?> engineKey : ENGINE_OPTION_KEYS) {
                if (contextOptions.hasBeenSet(engineKey)) {
                    engineOptions.put(engineKey, contextOptions.get(engineKey));
                }
            }
        }

        @Override
        public OptionDescriptors getDescriptors() {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean equals(Object obj) {
            if (!(obj instanceof EngineOptionValues)) {
                return false;
            }
            EngineOptionValues other = (EngineOptionValues) obj;
            return engineOptions.equals(other.engineOptions);
        }

        @Override
        public int hashCode() {
            return engineOptions.hashCode();
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T> T get(OptionKey<T> optionKey) {
            if (engineOptions.containsKey(optionKey)) {
                return (T) engineOptions.get(optionKey);
            } else {
                return optionKey.getDefaultValue();
            }
        }

        @Override
        public boolean hasBeenSet(OptionKey<?> optionKey) {
            return engineOptions.containsKey(optionKey);
        }

        @Override
        @SuppressWarnings("deprecation")
        public <T> void set(OptionKey<T> optionKey, T value) {
            throw new UnsupportedOperationException();
        }
    }
}
