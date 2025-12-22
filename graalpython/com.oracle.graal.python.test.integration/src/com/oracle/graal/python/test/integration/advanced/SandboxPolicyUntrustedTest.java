/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.test.integration.advanced;

import static org.junit.Assert.assertEquals;

import java.io.ByteArrayOutputStream;
import java.nio.charset.Charset;
import java.util.function.Consumer;
import java.util.function.Function;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.PolyglotException;
import org.graalvm.polyglot.SandboxPolicy;
import org.graalvm.polyglot.Value;
import org.junit.Assume;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class SandboxPolicyUntrustedTest {
    @BeforeClass
    public static void setupClass() {
        String requestedTest = System.getProperty("test");
        Assume.assumeTrue(requestedTest != null && requestedTest.equals(SandboxPolicyUntrustedTest.class.getSimpleName()));
    }

    public record ContextConfig(String name, Consumer<Context.Builder> config) {
        @Override
        public String toString() {
            return name();
        }
    }

    @Parameters(name = "{0}")
    public static ContextConfig[] configs() {
        return new ContextConfig[]{
                        new ContextConfig("UNTRUSTED", b -> b //
                                        .sandbox(SandboxPolicy.ISOLATED) //
                                        .option("engine.MaxIsolateMemory", "1GB")),
                        new ContextConfig("ISOLATED", b -> b //
                                        .sandbox(SandboxPolicy.ISOLATED) //
                                        .option("engine.MaxIsolateMemory", "1GB")),
                        new ContextConfig("CONSTRAINED", b -> b //
                                        .sandbox(SandboxPolicy.CONSTRAINED)),
        };
    }

    @Parameter public ContextConfig config;

    private <R> R run(String source, Function<Value, R> resultFun) {
        // encodings import during ctx init takes 19 frames
        return run(source, 20, resultFun);
    }

    private <R> R run(String source, int maxStackFrames, Function<Value, R> resultFun) {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ByteArrayOutputStream errorOutput = new ByteArrayOutputStream();
        try (Context context = Context.newBuilder("python") //
                        .out(output) //
                        .err(errorOutput) //
                        .apply(config.config()).option("sandbox.MaxHeapMemory", "800MB") //
                        .option("sandbox.MaxCPUTime", "10s") //
                        .option("sandbox.MaxASTDepth", "100") //
                        .option("sandbox.MaxStackFrames", Integer.toString(maxStackFrames)) //
                        .option("sandbox.MaxThreads", "1") //
                        .option("sandbox.MaxOutputStreamSize", "1MB") //
                        .option("sandbox.MaxErrorStreamSize", "1MB") //
                        .build()) {
            return resultFun.apply(context.eval("python", source));
        } catch (PolyglotException e) {
            System.out.println("stdout:");
            System.out.println(output.toString(Charset.defaultCharset()));
            System.out.println("--------");
            System.out.println("stderr:");
            System.out.println(errorOutput.toString(Charset.defaultCharset()));
            throw e;
        }
    }

    @Test
    public void helloworld() {
        assertEquals("hello world", run("'hello world'", Value::asString));
    }

    @Test
    public void canImportBuiltinModules() {
        assertEquals("graalpy", run("import sys; sys.implementation.name", Value::asString));
    }

    @Test
    public void canImportNonBuiltinModules() {
        assertEquals("email", run("import email; email.__name__", Value::asString));
    }

    @Test
    public void doesNotLeakEnvironmentVariables() {
        int maxStackFrames = 27;
        assertEquals("<empty>", run("import os; os.environ.get('JAVA_HOME', '<empty>')", maxStackFrames, Value::asString));
    }
}
