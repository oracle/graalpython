/*
 * Copyright (c) 2020, 2024, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.benchmarks.jmh;

import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.HostAccess;
import org.graalvm.polyglot.Value;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.annotations.Warmup;

/**
 * Set of micro benchmarks exercising Python and Java interop.
 */
@State(Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 5, time = 5)
@Measurement(iterations = 5, time = 5)
public class HostInteropMicroBenchmarks {
    private Context context;
    private Value positionalArgsFun;
    private Value object;
    private MyType objectAsIface;
    private Value sumFun;

    public interface MyType {
        int foo(int a, int b);
    }

    @Setup
    public void setup() {
        this.context = Context.newBuilder().allowHostAccess(HostAccess.ALL).build();
        this.positionalArgsFun = context.eval("python", "def pos_args_fun(a, b): return a + b\npos_args_fun");
        this.object = context.eval("python", """
                        class MyType:
                            def foo(self, a, b):
                                return a + b

                        MyType()
                        """);
        this.objectAsIface = object.as(MyType.class);
        this.sumFun = context.eval("python", """
                        def my_sum(args):
                            r = 0
                            for i in args:
                                r += i
                            return r

                        my_sum
                        """);
    }

    @TearDown
    public void tearDown() {
        context.close(true);
        context = null;
    }

    /** Just to avoid constant folding of the arguments to various operations */
    @State(Scope.Thread)
    public static class Arguments {
        public int num1 = 42;
        public int num2 = 24;
    }

    @State(Scope.Thread)
    public static class IntArrayArgument {
        public int[] data = IntStream.range(0, 1000).toArray();
    }

    @Benchmark
    public Value callPythonPositionalArgs(Arguments args) {
        return positionalArgsFun.execute(args.num1, args.num2);
    }

    @Benchmark
    public Value invokePythonMemberPositionalArgs(Arguments args) {
        return object.invokeMember("foo", args.num1, args.num2);
    }

    @Benchmark
    public int ifaceInvokePythonMethodPositionalArgs(Arguments args) {
        return objectAsIface.foo(args.num1, args.num2);
    }

    @Benchmark
    public Object sumJavaArrayInPython(IntArrayArgument arg) {
        return sumFun.execute((Object) arg.data);
    }
}
