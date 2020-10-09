/*
 * Copyright (c) 2017, 2020, Oracle and/or its affiliates.
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

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.graalvm.options.OptionCategory;
import org.graalvm.options.OptionDescriptor;
import org.graalvm.options.OptionDescriptors;
import org.graalvm.options.OptionKey;
import org.graalvm.options.OptionStability;
import org.graalvm.options.OptionValues;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.Option;
import com.oracle.truffle.api.TruffleLanguage.Env;
import com.oracle.truffle.api.nodes.ExplodeLoop;

/**
 * The options for Python. Note that some options have an effect on the AST structure, and thus must
 * be the same for all contexts in an engine. We annotate these with {@link EngineOption} and the
 * PythonLanguage will ensure that these are matched across contexts.
 */
@Option.Group(PythonLanguage.ID)
public final class PythonOptions {
    private static final String EXECUTABLE_LIST_SEPARATOR = "🏆";

    private PythonOptions() {
        // no instances
    }

    @Option(category = OptionCategory.USER, help = "Set the location of sys.prefix. Overrides any environment variables or Java options.", stability = OptionStability.STABLE) //
    public static final OptionKey<String> SysPrefix = new OptionKey<>("");

    @Option(category = OptionCategory.EXPERT, help = "Set the location of sys.base_prefix. Overrides any environment variables or Java options.", stability = OptionStability.STABLE) //
    public static final OptionKey<String> SysBasePrefix = new OptionKey<>("");

    @Option(category = OptionCategory.USER, help = "Set the location of lib-graalpython. Overrides any environment variables or Java options.", stability = OptionStability.STABLE) //
    public static final OptionKey<String> CoreHome = new OptionKey<>("");

    @Option(category = OptionCategory.USER, help = "Set the location of lib-python/3. Overrides any environment variables or Java options.", stability = OptionStability.STABLE) //
    public static final OptionKey<String> StdLibHome = new OptionKey<>("");

    @Option(category = OptionCategory.USER, help = "Equivalent to the Python -i flag. Inspect interactively after running a script.", stability = OptionStability.STABLE) //
    public static final OptionKey<Boolean> InspectFlag = new OptionKey<>(false);

    @Option(category = OptionCategory.USER, help = "Equivalent to the Python -q flag. Don't  print version and copyright messages on interactive startup.", stability = OptionStability.STABLE) //
    public static final OptionKey<Boolean> QuietFlag = new OptionKey<>(false);

    @Option(category = OptionCategory.USER, help = "Equivalent to the Python -S flag. Don't imply 'import site' on initialization.", stability = OptionStability.STABLE) //
    public static final OptionKey<Boolean> NoSiteFlag = new OptionKey<>(false);

    @Option(category = OptionCategory.USER, help = "Equivalent to the Python -s flag. Don't add user site directory to sys.path.", stability = OptionStability.STABLE) //
    public static final OptionKey<Boolean> NoUserSiteFlag = new OptionKey<>(false);

    @Option(category = OptionCategory.USER, help = "Equivalent to the Python -E flag. Ignore PYTHON* environment variables.", stability = OptionStability.STABLE) //
    public static final OptionKey<Boolean> IgnoreEnvironmentFlag = new OptionKey<>(false);

    @Option(category = OptionCategory.USER, help = "Equivalent to setting the PYTHONPATH environment variable for the standard launcher. ':'-separated list of directories prefixed to the default module search path.", stability = OptionStability.STABLE) //
    public static final OptionKey<String> PythonPath = new OptionKey<>("");

    @EngineOption @Option(category = OptionCategory.USER, help = "Equivalent to setting the PYTHONIOENCODING environment variable for the standard launcher. Format: Encoding[:errors]", stability = OptionStability.STABLE) //
    public static final OptionKey<String> StandardStreamEncoding = new OptionKey<>("");

    @Option(category = OptionCategory.USER, help = "Remove assert statements and any code conditional on the value of __debug__.", stability = OptionStability.STABLE) //
    public static final OptionKey<Boolean> PythonOptimizeFlag = new OptionKey<>(false);

    @Option(category = OptionCategory.USER, help = "Equivalent to the Python -v flag. Turn on verbose mode.", stability = OptionStability.STABLE) //
    public static final OptionKey<Boolean> VerboseFlag = new OptionKey<>(false);

    @Option(category = OptionCategory.USER, help = "Equivalent to the Python -u flag. Force stdout and stderr to be unbuffered.", stability = OptionStability.STABLE) //
    public static final OptionKey<Boolean> UnbufferedIO = new OptionKey<>(false);

    @Option(category = OptionCategory.USER, help = "Equivalent to the Python -I flag. Isolate from the users environment by not adding the cwd to the path", stability = OptionStability.STABLE) //
    public static final OptionKey<Boolean> IsolateFlag = new OptionKey<>(false);

    @Option(category = OptionCategory.USER, help = "Equivalent to the Python -B flag. Don't write bytecode files.", stability = OptionStability.STABLE) //
    public static final OptionKey<Boolean> DontWriteBytecodeFlag = new OptionKey<>(true);

    @Option(category = OptionCategory.USER, help = "If this is set, GraalPython will write .pyc files in a mirror directory tree at this path, " +
                    "instead of in __pycache__ directories within the source tree. " +
                    "Equivalent to setting the PYTHONPYCACHEPREFIX environment variable for the standard launcher.", stability = OptionStability.STABLE) //
    public static final OptionKey<String> PyCachePrefix = new OptionKey<>("");

    @Option(category = OptionCategory.USER, help = "Equivalent to setting the PYTHONWARNINGS environment variable for the standard launcher.", stability = OptionStability.STABLE) //
    public static final OptionKey<String> WarnOptions = new OptionKey<>("");

    @EngineOption @Option(category = OptionCategory.USER, help = "Choose the backend for the POSIX module. Valid values are 'java', 'native', 'llvm'.") //
    public static final OptionKey<String> PosixModuleBackend = new OptionKey<>("java");

    @Option(category = OptionCategory.INTERNAL, help = "Set the location of C API home. Overrides any environment variables or Java options.", stability = OptionStability.STABLE) //
    public static final OptionKey<String> CAPI = new OptionKey<>("");

    @EngineOption @Option(category = OptionCategory.INTERNAL, help = "Expose internal sources as normal sources, so they will show up in the debugger and stacks") //
    public static final OptionKey<Boolean> ExposeInternalSources = new OptionKey<>(false);

    @EngineOption @Option(category = OptionCategory.INTERNAL, help = "Print the java stacktrace. Possible modes:" +
                    "    1   Print Java stacktrace for Java exceptions only." +
                    "    2   Print Java stacktrace for Python exceptions only (ATTENTION: this will have a notable performance impact)." +
                    "    3   Combines 1 and 2.") //
    public static final OptionKey<Integer> WithJavaStacktrace = new OptionKey<>(0);

    @Option(category = OptionCategory.INTERNAL, help = "") //
    public static final OptionKey<Boolean> CatchGraalPythonExceptionForUnitTesting = new OptionKey<>(false);

    @EngineOption @Option(category = OptionCategory.INTERNAL, help = "Enable catching all Exceptions in generic try-catch statements.") //
    public static final OptionKey<Boolean> CatchAllExceptions = new OptionKey<>(false);

    @Option(category = OptionCategory.EXPERT, help = "Prints path to parsed files") //
    public static final OptionKey<Boolean> ParserLogFiles = new OptionKey<>(false);

    @Option(category = OptionCategory.EXPERT, help = "Prints parser time statistics after number of parsed files, set by this option. 0 or <0 means no statistics are printed.") //
    public static final OptionKey<Integer> ParserStatistics = new OptionKey<>(0);

    @EngineOption @Option(category = OptionCategory.EXPERT, help = "") //
    public static final OptionKey<Integer> AttributeAccessInlineCacheMaxDepth = new OptionKey<>(5);

    @EngineOption @Option(category = OptionCategory.EXPERT, help = "") //
    public static final OptionKey<Integer> CallSiteInlineCacheMaxDepth = new OptionKey<>(4);

    @EngineOption @Option(category = OptionCategory.EXPERT, help = "") //
    public static final OptionKey<Integer> VariableArgumentReadUnrollingLimit = new OptionKey<>(5);

    @EngineOption @Option(category = OptionCategory.EXPERT, help = "") //
    public static final OptionKey<Integer> VariableArgumentInlineCacheLimit = new OptionKey<>(3);

    @EngineOption @Option(category = OptionCategory.EXPERT, help = "") //
    public static final OptionKey<Boolean> ForceInlineGeneratorCalls = new OptionKey<>(false);

    @Option(category = OptionCategory.EXPERT, help = "Force to automatically import site.py module.") //
    public static final OptionKey<Boolean> ForceImportSite = new OptionKey<>(false);

    @Option(category = OptionCategory.EXPERT, help = "Minimal size of string, when lazy strings are used. Default 20") //
    public static final OptionKey<Integer> MinLazyStringLength = new OptionKey<>(20);

    @Option(category = OptionCategory.EXPERT, help = "This option is set by the Python launcher to tell the language it can print exceptions directly") //
    public static final OptionKey<Boolean> AlwaysRunExcepthook = new OptionKey<>(false);

    @EngineOption @Option(category = OptionCategory.EXPERT, help = "This option control builtin _thread module support") //
    public static final OptionKey<Boolean> WithThread = new OptionKey<>(false);

    // disabling TRegex has an effect on the _sre Python functions that are
    // dynamically created, so we cannot change that option again.
    @EngineOption @Option(category = OptionCategory.EXPERT, help = "Use the optimized TRegex engine. Default true") //
    public static final OptionKey<Boolean> WithTRegex = new OptionKey<>(true);

    @EngineOption @Option(category = OptionCategory.EXPERT, help = "Use the CPython sre engine as a fallback to the TRegex engine.") //
    public static final OptionKey<Boolean> TRegexUsesSREFallback = new OptionKey<>(true);

    @Option(category = OptionCategory.EXPERT, help = "Switch on/off using lazy strings for performance reasons. Default true.") //
    public static final OptionKey<Boolean> LazyStrings = new OptionKey<>(true);

    @EngineOption @Option(category = OptionCategory.EXPERT, help = "Enable forced splitting (of builtins). Default false.") //
    public static final OptionKey<Boolean> EnableForcedSplits = new OptionKey<>(false);

    @Option(category = OptionCategory.EXPERT, help = "Set by the launcher if an interactive console is used to run Python.") //
    public static final OptionKey<Boolean> TerminalIsInteractive = new OptionKey<>(false);

    @Option(category = OptionCategory.EXPERT, help = "Set by the launcher to the terminal width.") //
    public static final OptionKey<Integer> TerminalWidth = new OptionKey<>(80);

    @Option(category = OptionCategory.EXPERT, help = "Set by the launcher to the terminal height.") //
    public static final OptionKey<Integer> TerminalHeight = new OptionKey<>(25);

    @Option(category = OptionCategory.EXPERT, help = "The sys.executable path. Set by the launcher, but can may need to be overridden in certain special situations.", stability = OptionStability.STABLE) //
    public static final OptionKey<String> Executable = new OptionKey<>("");

    @Option(category = OptionCategory.EXPERT, help = "The executed command list as string joined by the executable list separator char. This must always correspond to the real, valid command list used to run GraalPython.") //
    public static final OptionKey<String> ExecutableList = new OptionKey<>("");

    @Option(category = OptionCategory.EXPERT, help = "Determines wether context startup tries to re-use previously cached sources of the core library.") //
    public static final OptionKey<Boolean> WithCachedSources = new OptionKey<>(true);

    @Option(category = OptionCategory.EXPERT, help = "Embedder option: what to print in response to PythonLanguage#toString.") //
    public static final OptionKey<Boolean> UseReprForPrintString = new OptionKey<>(true);

    @EngineOption @Option(category = OptionCategory.EXPERT, help = "Stop inlining of builtins if caller's cumulative tree size would exceed this limit") //
    public static final OptionKey<Integer> BuiltinsInliningMaxCallerSize = new OptionKey<>(2250);

    @Option(category = OptionCategory.EXPERT, help = "Disable weakref callback processing, signal handling, and other periodic async actions.") //
    public static final OptionKey<Boolean> NoAsyncActions = new OptionKey<>(false);

    @Option(category = OptionCategory.EXPERT, help = "Propagate append operations to lists created as literals back to where they were created, to inform overallocation to avoid having to grow them later.") //
    public static final OptionKey<Boolean> OverallocateLiteralLists = new OptionKey<>(true);

    @EngineOption @Option(category = OptionCategory.USER, help = "Emulate some Jython features that can cause performance degradation") //
    public static final OptionKey<Boolean> EmulateJython = new OptionKey<>(false);

    @Option(category = OptionCategory.EXPERT, help = "Enable tracing of native memory (ATTENTION: this will have significant impact on CExt execution performance).") //
    public static final OptionKey<Boolean> TraceNativeMemory = new OptionKey<>(false);

    @Option(category = OptionCategory.EXPERT, help = "If native memory tracing is enabled, also capture stack.") //
    public static final OptionKey<Boolean> TraceNativeMemoryCalls = new OptionKey<>(false);

    @Option(category = OptionCategory.EXPERT, help = "Max native memory heap size (default: 2 GB).") //
    public static final OptionKey<Long> MaxNativeMemory = new OptionKey<>(1L << 31);

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

    public static int getAttributeAccessInlineCacheMaxDepth() {
        CompilerAsserts.neverPartOfCompilation();
        return PythonLanguage.getCurrent().getEngineOption(AttributeAccessInlineCacheMaxDepth);
    }

    public static int getCallSiteInlineCacheMaxDepth() {
        CompilerAsserts.neverPartOfCompilation();
        return PythonLanguage.getCurrent().getEngineOption(CallSiteInlineCacheMaxDepth);
    }

    public static int getVariableArgumentInlineCacheLimit() {
        CompilerAsserts.neverPartOfCompilation();
        return PythonLanguage.getCurrent().getEngineOption(VariableArgumentInlineCacheLimit);
    }

    public static boolean isWithJavaStacktrace(PythonLanguage language) {
        return language.getEngineOption(WithJavaStacktrace) > 0;
    }

    public static boolean isPExceptionWithJavaStacktrace(PythonLanguage language) {
        return language.getEngineOption(WithJavaStacktrace) > 1;
    }

    @TruffleBoundary
    public static boolean isWithThread(Env env) {
        return env.getOptions().get(WithThread);
    }

    @TruffleBoundary
    public static String[] getExecutableList(PythonContext context) {
        String option = context.getOption(ExecutableList);
        if (option.isEmpty()) {
            return splitString(context.getOption(Executable), " ");
        } else {
            return splitString(context.getOption(ExecutableList), EXECUTABLE_LIST_SEPARATOR);
        }
    }

    @TruffleBoundary
    private static String[] splitString(String str, String sep) {
        return str.split(sep);
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
