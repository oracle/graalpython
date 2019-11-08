/*
 * Copyright (c) 2017, 2019, Oracle and/or its affiliates.
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

import com.oracle.graal.python.PythonLanguage;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.Option;
import com.oracle.truffle.api.TruffleLanguage.Env;

import org.graalvm.options.OptionCategory;
import org.graalvm.options.OptionDescriptors;
import org.graalvm.options.OptionKey;
import org.graalvm.options.OptionStability;

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

    @Option(category = OptionCategory.USER, help = "Remove assert statements and any code conditional on the value of __debug__.", stability = OptionStability.STABLE) //
    public static final OptionKey<Boolean> PythonOptimizeFlag = new OptionKey<>(false);

    @Option(category = OptionCategory.USER, help = "Equivalent to the Python -v flag. Turn on verbose mode.", stability = OptionStability.STABLE) //
    public static final OptionKey<Boolean> VerboseFlag = new OptionKey<>(false);

    @Option(category = OptionCategory.USER, help = "Equivalent to the Python -u flag. Force stdout and stderr to be unbuffered.", stability = OptionStability.STABLE) //
    public static final OptionKey<Boolean> UnbufferedIO = new OptionKey<>(false);

    @Option(category = OptionCategory.USER, help = "Equivalent to the Python -I flag. Isolate from the users environment by not adding the cwd to the path", stability = OptionStability.STABLE) //
    public static final OptionKey<Boolean> IsolateFlag = new OptionKey<>(false);

    @Option(category = OptionCategory.INTERNAL, help = "Set the location of C API home. Overrides any environment variables or Java options.") //
    public static final OptionKey<String> CAPI = new OptionKey<>("");

    @Option(category = OptionCategory.INTERNAL, help = "Expose internal sources as normal sources, so they will show up in the debugger and stacks") //
    public static final OptionKey<Boolean> ExposeInternalSources = new OptionKey<>(false);

    @Option(category = OptionCategory.INTERNAL, help = "Print the java stacktrace if enabled") //
    public static final OptionKey<Boolean> WithJavaStacktrace = new OptionKey<>(false);

    @Option(category = OptionCategory.INTERNAL, help = "") //
    public static final OptionKey<Boolean> CatchGraalPythonExceptionForUnitTesting = new OptionKey<>(false);

    @Option(category = OptionCategory.INTERNAL, help = "Enable catching all Exceptions in generic try-catch statements.") //
    public static final OptionKey<Boolean> CatchAllExceptions = new OptionKey<>(false);

    @Option(category = OptionCategory.EXPERT, help = "Prints path to parsed files") //
    public static final OptionKey<Boolean> ParserLogFiles = new OptionKey<>(false);

    @Option(category = OptionCategory.EXPERT, help = "Prints parser time statistis after number of parsed files, set by this option. 0 or <0 means no statistics are printed.") //
    public static final OptionKey<Integer> ParserStatistics = new OptionKey<>(0);

    @Option(category = OptionCategory.EXPERT, help = "") //
    public static final OptionKey<Boolean> IntrinsifyBuiltinCalls = new OptionKey<>(true);

    @Option(category = OptionCategory.EXPERT, help = "") //
    public static final OptionKey<Integer> AttributeAccessInlineCacheMaxDepth = new OptionKey<>(5);

    @Option(category = OptionCategory.EXPERT, help = "") //
    public static final OptionKey<Integer> CallSiteInlineCacheMaxDepth = new OptionKey<>(4);

    @Option(category = OptionCategory.EXPERT, help = "") //
    public static final OptionKey<Integer> VariableArgumentReadUnrollingLimit = new OptionKey<>(5);

    @Option(category = OptionCategory.EXPERT, help = "") //
    public static final OptionKey<Integer> VariableArgumentInlineCacheLimit = new OptionKey<>(3);

    @Option(category = OptionCategory.EXPERT, help = "") //
    public static final OptionKey<Boolean> ForceInlineGeneratorCalls = new OptionKey<>(false);

    @Option(category = OptionCategory.EXPERT, help = "Minimal size of string, when lazy strings are used. Default 20") //
    public static final OptionKey<Integer> MinLazyStringLength = new OptionKey<>(20);

    @Option(category = OptionCategory.EXPERT, help = "This option is set by the Python launcher to tell the language it can print exceptions directly") //
    public static final OptionKey<Boolean> AlwaysRunExcepthook = new OptionKey<>(false);

    @Option(category = OptionCategory.EXPERT, help = "This option control builtin _thread module support") //
    public static final OptionKey<Boolean> WithThread = new OptionKey<>(false);

    @Option(category = OptionCategory.EXPERT, help = "Use the optimized TRegex engine and call the CPython sre engine only as a fallback. Default true") //
    public static final OptionKey<Boolean> WithTRegex = new OptionKey<>(true);

    @Option(category = OptionCategory.EXPERT, help = "Switch on/off using lazy strings for performance reasons. Default true.") //
    public static final OptionKey<Boolean> LazyStrings = new OptionKey<>(true);

    @Option(category = OptionCategory.EXPERT, help = "Enable forced splitting (of builtins). Default false.") //
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

    @Option(category = OptionCategory.EXPERT, help = "Stop inlining of builtins if caller's cumulative tree size would exceed this limit") //
    public static final OptionKey<Integer> BuiltinsInliningMaxCallerSize = new OptionKey<>(2250);

    @Option(category = OptionCategory.USER, help = "Emulate some Jython features that can cause performance degradation") //
    public static final OptionKey<Boolean> EmulateJython = new OptionKey<>(false);

    public static OptionDescriptors createDescriptors() {
        return new PythonOptionsOptionDescriptors();
    }

    @TruffleBoundary
    public static <T> T getOption(PythonContext context, OptionKey<T> key) {
        if (context == null) {
            return key.getDefaultValue();
        }
        return context.getOptions().get(key);
    }

    @TruffleBoundary
    public static <T> T getOption(Env env, OptionKey<T> key) {
        return env.getOptions().get(key);
    }

    @TruffleBoundary
    public static int getIntOption(PythonContext context, OptionKey<Integer> key) {
        if (context == null) {
            return key.getDefaultValue();
        }
        return context.getOptions().get(key);
    }

    @TruffleBoundary
    public static boolean getFlag(PythonContext context, OptionKey<Boolean> key) {
        if (context == null) {
            return key.getDefaultValue();
        }
        return context.getOptions().get(key);
    }

    public static int getAttributeAccessInlineCacheMaxDepth() {
        return getOption(PythonLanguage.getContext(), AttributeAccessInlineCacheMaxDepth);
    }

    public static int getCallSiteInlineCacheMaxDepth() {
        return getOption(PythonLanguage.getContext(), CallSiteInlineCacheMaxDepth);
    }

    public static int getVariableArgumentInlineCacheLimit() {
        return getOption(PythonLanguage.getContext(), VariableArgumentInlineCacheLimit);
    }

    public static boolean useLazyString() {
        return getOption(PythonLanguage.getContext(), LazyStrings);
    }

    public static int getMinLazyStringLength() {
        return getOption(PythonLanguage.getContext(), MinLazyStringLength);
    }

    public static boolean isWithThread(Env env) {
        return getOption(env, WithThread);
    }

    public static boolean getEnableForcedSplits() {
        return getOption(PythonLanguage.getContext(), EnableForcedSplits);
    }

    public static int getTerminalHeight() {
        return getOption(PythonLanguage.getContext(), TerminalHeight);
    }

    public static int getTerminalWidth() {
        return getOption(PythonLanguage.getContext(), TerminalWidth);
    }

    @TruffleBoundary
    public static String[] getExecutableList() {
        String option = getOption(PythonLanguage.getContext(), ExecutableList);
        if (option.isEmpty()) {
            return getOption(PythonLanguage.getContext(), Executable).split(" ");
        } else {
            return getOption(PythonLanguage.getContext(), ExecutableList).split(EXECUTABLE_LIST_SEPARATOR);
        }
    }
}
