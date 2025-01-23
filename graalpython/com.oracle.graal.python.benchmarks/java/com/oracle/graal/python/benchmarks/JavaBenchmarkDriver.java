/*
 * Copyright (c) 2021, 2024, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.benchmarks;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.graalvm.launcher.AbstractLanguageLauncher;
import org.graalvm.options.OptionCategory;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Context.Builder;
import org.graalvm.polyglot.Engine;
import org.graalvm.polyglot.Source;
import org.graalvm.polyglot.Value;

/**
 * Driver that can execute our Python benchmarks via Java embedding. Java version of the Python
 * benchmark harness. The benchmarks must define Python function entry point named
 * {@code java_embedded_bench_entrypoint} and this driver executes that as one benchmark iteration.
 */
public class JavaBenchmarkDriver extends AbstractLanguageLauncher {
    private static final String LINE = "-------------------------------------------------------------------------------";
    private static final String BENCHMARK_ENTRY_POINT = "java_embedded_bench_entrypoint";

    public static class BenchmarkOptions {
        public boolean sharedEngine;
        public boolean multiContext;
        public int iterations = 10;
        public int warmupIterations = 10;
        public String benchmarksPath;
        private String benchmarkName;
        private String[] benchmarkArgs = new String[0];
    }

    private final Map<String, String> engineOptions = new HashMap<>();
    private final BenchmarkOptions options = new BenchmarkOptions();

    public static void main(String[] args) {
        new JavaBenchmarkDriver().launch(args);
    }

    @Override
    protected List<String> preprocessArguments(List<String> arguments, Map<String, String> polyglotOptions) {
        ArrayList<String> unrecognized = new ArrayList<>();
        int i = 0;
        optionsLoop: while (i < arguments.size()) {
            String arg = arguments.get(i);
            switch (arg) {
                case "-interpreter":
                    engineOptions.put("engine.Compilation", "false");
                    break;
                case "-shared-engine":
                    options.sharedEngine = true;
                    break;
                case "-multi-context":
                    options.multiContext = true;
                    break;
                case "-path":
                    if (i + 1 >= arguments.size()) {
                        System.err.println("Option -path is not followed by anything");
                        System.exit(1);
                    }
                    options.benchmarksPath = arguments.get(i + 1);
                    i += 1;
                    break;
                case "-i":
                    options.iterations = getIntOption(arguments, i);
                    i += 1;
                    break;
                case "-w":
                    options.warmupIterations = getIntOption(arguments, i);
                    i += 1;
                    break;
                case "--":
                    options.benchmarkArgs = arguments.subList(i + 1, arguments.size()).toArray(new String[0]);
                    break optionsLoop;
                default:
                    if (!arg.startsWith("-") && options.benchmarkName == null) {
                        options.benchmarkName = arg;
                        break;
                    }
                    unrecognized.add(arg);
            }
            i++;
        }
        return unrecognized;
    }

    private static int getIntOption(List<String> arguments, int index) {
        if (index + 1 >= arguments.size()) {
            System.err.println("Option -i is not followed by anything");
            System.exit(1);
        }
        try {
            return Integer.parseInt(arguments.get(index + 1));
        } catch (NumberFormatException ex) {
            System.err.println("Option -i is not followed by a number");
            System.exit(1);
        }
        throw new AssertionError();
    }

    @Override
    protected void launch(Builder contextBuilder) {
        contextBuilder.allowExperimentalOptions(true);
        contextBuilder.arguments("python", new String[]{"java_embedding_bench"});
        if (options.sharedEngine) {
            contextBuilder.engine(Engine.newBuilder().allowExperimentalOptions(true).options(engineOptions).build());
        } else {
            contextBuilder.options(engineOptions);
        }

        System.out.println(LINE);
        System.out.printf("### %s, %d warmup iterations, %d bench iterations%n", options.benchmarkName, options.warmupIterations, options.iterations);
        System.out.printf("### args = %s%n", Arrays.toString(options.benchmarkArgs));

        System.out.println(LINE);
        System.out.println("### setup ... ");
        Source source;
        Path path = Paths.get(options.benchmarksPath, options.benchmarkName + ".py");
        try {
            source = Source.newBuilder("python", path.toFile()).build();
        } catch (IOException e) {
            System.err.println("Cannot open the file: " + path);
            System.exit(1);
            return;
        }

        System.out.println("### start benchmark ... ");
        if (options.multiContext) {
            runBenchmarkMultiContext(contextBuilder, source);
        } else {
            runBenchmarkSingleContext(contextBuilder, source);
        }
    }

    private void runBenchmarkMultiContext(Builder contextBuilder, Source source) {
        System.out.println();
        System.out.printf("### (pre)warming up for %d iterations ... %n", options.warmupIterations);
        repeatBenchmarkMultiContext(contextBuilder, source, options.warmupIterations, true);

        System.out.println();
        System.out.printf("### measure phase for %d iterations ... %n", options.iterations);
        long[] durations = repeatBenchmarkMultiContext(contextBuilder, source, options.iterations, false);

        System.out.println(LINE);
        System.out.println("### benchmark complete");

        System.out.println(LINE);
        System.out.printf("### BEST                duration: %.4f%n", Arrays.stream(durations).min().getAsLong() / 1000_000_000.0);
        System.out.printf("### WORST               duration: %.4f%n", Arrays.stream(durations).max().getAsLong() / 1000_000_000.0);
        System.out.printf("### AVG (no warmup)     duration: %.4f%n", Arrays.stream(durations).average().getAsDouble() / 1000_000_000.0);

        System.out.println(LINE);
        System.out.printf("### RAW DURATIONS: [%s]%n", Arrays.stream(durations).mapToObj(x -> String.format("%.4f", x / 1000_000_000.0)).collect(Collectors.joining(",")));
    }

    private long[] repeatBenchmarkMultiContext(Builder contextBuilder, Source source, int iterations, boolean warmup) {
        long[] durations = new long[iterations];
        for (int i = 0; i < iterations; i++) {
            Context context = contextBuilder.build();
            Value benchmark = getBenchmark(source, context);

            long start = System.nanoTime();
            benchmark.executeVoid((Object[]) options.benchmarkArgs);
            long end = System.nanoTime();
            durations[i] = end - start;
            reportIteration(i, durations[i], warmup);
        }
        return durations;
    }

    private void runBenchmarkSingleContext(Builder contextBuilder, Source source) {
        Context context = contextBuilder.build();
        Value benchmark = getBenchmark(source, context);

        System.out.println();
        System.out.printf("### (pre)warming up for %d iterations ... %n", options.warmupIterations);
        repeatBenchmarkSingleContext(benchmark, options.warmupIterations, true);

        System.out.println();
        System.out.printf("### measure phase for %d iterations ... %n", options.iterations);
        repeatBenchmarkSingleContext(benchmark, options.iterations, false);
    }

    private void repeatBenchmarkSingleContext(Value benchmark, int iterations, boolean warmup) {
        for (int i = 0; i < iterations; i++) {
            long start = System.nanoTime();
            benchmark.executeVoid((Object[]) options.benchmarkArgs);
            long end = System.nanoTime();
            long time = end - start;
            reportIteration(i, time, warmup);
        }
    }

    private Value getBenchmark(Source source, Context context) {
        try {
            context.eval(source);
            Value result = context.getBindings("python").getMember(BENCHMARK_ENTRY_POINT);
            if (result == null) {
                throw new UnsupportedOperationException();
            }
            return result;
        } catch (UnsupportedOperationException ex) {
            System.err.printf("Benchmark in file '%s' does not define method named '%s'.%n", options.benchmarkName, BENCHMARK_ENTRY_POINT);
            System.exit(1);
            return null;
        }
    }

    private void reportIteration(int iter, long nanoTime, boolean warmup) {
        System.out.printf("### %siteration=%d, name=%s, duration=%.4f%n", warmup ? "warmup " : "", iter, options.benchmarkName, nanoTime / 1_000_000_000.0);
    }

    @Override
    protected String getLanguageId() {
        return "python";
    }

    @Override
    protected void printHelp(OptionCategory maxCategory) {
        System.out.println("Python Java benchmarks driver");
        System.out.println();
        System.out.println("usage: java ... JavaBenchmarkDriver [options] benchmarkName -- [benchmark arguments]");
        System.out.println();
        System.out.println("Supported options:");
        System.out.println("-i                Number of benchmark iterations");
        System.out.println("-w                Number of warmup iterations");
        System.out.println("-interpreter      Turn off Truffle compilations to benchmark interpreter performance");
        System.out.println("-shared-engine    Use shared engine");
        System.out.println("-multi-context    Run each iteration in a new context");
        System.out.println("-path /some/path  Where to look for the Python scripts with the benchmarks");
        System.out.println();
        System.out.printf("The Python benchmark script must define function named '%s'.%n", BENCHMARK_ENTRY_POINT);
        System.out.println("The values from [benchmark arguments] will be passed to it as arguments, always as strings.");
    }
}
