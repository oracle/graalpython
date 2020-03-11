/*
 * Copyright (c) 2020, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.benchmarks.interop;

import java.util.HashMap;
import java.util.concurrent.TimeUnit;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Value;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.ChainedOptionsBuilder;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;
import org.openjdk.jmh.runner.options.VerboseMode;

@State(Scope.Benchmark)
@BenchmarkMode(Mode.SingleShotTime)
@OutputTimeUnit(TimeUnit.SECONDS)
@Warmup(iterations = BenchRunner.WARMUP_ITERATIONS)
@Measurement(iterations = BenchRunner.MEASUREMENT_ITERATIONS, time = 1)
public class BenchRunner {

    public static final int MEASUREMENT_ITERATIONS = 5;
    public static final int WARMUP_ITERATIONS = 0;

    protected Context context;

    protected BenchRunner() {
        this.context = Context.newBuilder().allowIO(true).build();
    }

    public static void main(String[] args) throws RunnerException {
        assert args.length > 1;
        final HashMap<String, String> rest = new HashMap<>();
        String benchName = args[0];
        String benchClass = args[1];
        int iter = MEASUREMENT_ITERATIONS;
        int warmup = WARMUP_ITERATIONS;
        int restCount = 0;
        for (int i = 2; i < args.length; i++) {
            final String arg = args[i];
            if (arg.contentEquals("-i")) {
                i++;
                iter = Integer.valueOf(args[i]);
            } else if (arg.contentEquals("-w")) {
                i++;
                warmup = Integer.valueOf(args[i]);
            } else {
                restCount++;
                rest.put("arg" + restCount, arg);
            }
        }
        ChainedOptionsBuilder options = new OptionsBuilder();
        options = options.include(benchClass);
        options = options.warmupIterations(warmup).measurementIterations(iter);
        for (String arg : rest.keySet()) {
            options = options.param(arg, rest.get(arg));
        }
        Options opt = options.forks(1).build();
        new Runner(opt, new BenchOutputFormat(System.out, VerboseMode.SILENT, benchName)).run();
    }

    protected static Value get(Value a, int i) {
        return a.getArrayElement(i);
    }

    protected static void set(Value a, int i, Object v) {
        a.setArrayElement(i, v);
    }

    protected static double getd(Value a, int i) {
        return a.getArrayElement(i).asDouble();
    }

    protected static void setd(Value a, int i, double v) {
        a.setArrayElement(i, v);
    }

    protected static int geti(Value a, int i) {
        return a.getArrayElement(i).asInt();
    }

    protected static void seti(Value a, int i, int v) {
        a.setArrayElement(i, v);
    }

}
