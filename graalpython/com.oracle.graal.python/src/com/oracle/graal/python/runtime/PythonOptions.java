/*
 * Copyright (c) 2017, 2018, Oracle and/or its affiliates.
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

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import org.graalvm.options.OptionCategory;
import org.graalvm.options.OptionDescriptors;
import org.graalvm.options.OptionKey;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.truffle.api.Option;

@Option.Group(PythonLanguage.ID)
public final class PythonOptions {

    private PythonOptions() {
        // no instances
    }

    @Option(category = OptionCategory.DEBUG, help = "Expose internal sources as normal sources, so they will show up in the debugger and stacks") //
    public static final OptionKey<Boolean> ExposeInternalSources = new OptionKey<>(false);

    @Option(category = OptionCategory.DEBUG, help = "Print the java stacktrace if enabled") //
    public static final OptionKey<Boolean> WithJavaStacktrace = new OptionKey<>(false);

    @Option(category = OptionCategory.DEBUG, help = "") //
    public static final OptionKey<Boolean> IntrinsifyBuiltinCalls = new OptionKey<>(true);

    @Option(category = OptionCategory.DEBUG, help = "") //
    public static final OptionKey<Integer> AttributeAccessInlineCacheMaxDepth = new OptionKey<>(4);

    @Option(category = OptionCategory.DEBUG, help = "") //
    public static final OptionKey<Integer> CallSiteInlineCacheMaxDepth = new OptionKey<>(4);

    @Option(category = OptionCategory.DEBUG, help = "") //
    public static final OptionKey<Integer> VariableArgumentReadUnrollingLimit = new OptionKey<>(5);

    @Option(category = OptionCategory.DEBUG, help = "") //
    public static final OptionKey<Integer> VariableArgumentInlineCacheLimit = new OptionKey<>(3);

    @Option(category = OptionCategory.DEBUG, help = "") //
    public static final OptionKey<Boolean> InlineGeneratorCalls = new OptionKey<>(true);

    @Option(category = OptionCategory.DEBUG, help = "") //
    public static final OptionKey<Boolean> CatchGraalPythonExceptionForUnitTesting = new OptionKey<>(false);

    @Option(category = OptionCategory.DEBUG, help = "") //
    public static final OptionKey<Boolean> CatchAllExceptions = new OptionKey<>(false);

    @Option(category = OptionCategory.USER, help = "Set the location of sys.prefix. Overrides any environment variables or Java options.") //
    public static final OptionKey<String> SysPrefix = new OptionKey<>("");

    @Option(category = OptionCategory.USER, help = "Set the location of lib-graalpython. Overrides any environment variables or Java options.") //
    public static final OptionKey<String> CoreHome = new OptionKey<>("");

    @Option(category = OptionCategory.USER, help = "Set the location of lib-python/3. Overrides any environment variables or Java options.") //
    public static final OptionKey<String> StdLibHome = new OptionKey<>("");

    @Option(category = OptionCategory.EXPERT, help = "This option is set by the Python launcher to tell the language it can print exceptions directly") //
    public static final OptionKey<Boolean> AlwaysRunExcepthook = new OptionKey<>(false);

    @Option(category = OptionCategory.USER, help = "") //
    public static final OptionKey<Boolean> InspectFlag = new OptionKey<>(false);

    @Option(category = OptionCategory.USER, help = "Remove assert statements and any code conditional on the value of __debug__.") //
    public static final OptionKey<Boolean> PythonOptimizeFlag = new OptionKey<>(false);

    @Option(category = OptionCategory.DEBUG, help = "Turn on verbose mode") //
    public static final OptionKey<Boolean> VerboseFlag = new OptionKey<>(false);

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
    public static int getIntOption(PythonContext context, OptionKey<Integer> key) {
        if (context == null) {
            return key.getDefaultValue();
        }
        return context.getOptions().get(key);
    }

    public static int getCallSiteInlineCacheMaxDepth() {
        return getOption(PythonLanguage.getContextRef().get(), CallSiteInlineCacheMaxDepth);
    }

    public static int getVariableArgumentInlineCacheLimit() {
        return getOption(PythonLanguage.getContextRef().get(), VariableArgumentInlineCacheLimit);
    }
}
