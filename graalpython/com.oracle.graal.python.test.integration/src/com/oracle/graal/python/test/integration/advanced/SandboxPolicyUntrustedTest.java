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

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.SandboxPolicy;
import org.graalvm.polyglot.Value;
import org.junit.Assume;
import org.junit.BeforeClass;
import org.junit.Test;

public class SandboxPolicyUntrustedTest {
    @BeforeClass
    public static void setupClass() {
        String requestedTest = System.getProperty("test");
        Assume.assumeTrue(requestedTest != null && requestedTest.equals(SandboxPolicyUntrustedTest.class.getSimpleName()));
    }

    private static Value run(String source) {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ByteArrayOutputStream errorOutput = new ByteArrayOutputStream();
        try (Context context = Context.newBuilder("python") //
                        .sandbox(SandboxPolicy.UNTRUSTED) //
                        .out(output) //
                        .err(errorOutput) //
                        .option("engine.MaxIsolateMemory", "1GB") //
                        .option("sandbox.MaxHeapMemory", "800MB") //
                        .option("sandbox.MaxCPUTime", "10s") //
                        .option("sandbox.MaxASTDepth", "100") //
                        .option("sandbox.MaxStackFrames", "10") //
                        .option("sandbox.MaxThreads", "1") //
                        .option("sandbox.MaxOutputStreamSize", "1MB") //
                        .option("sandbox.MaxErrorStreamSize", "1MB") //
                        .build()) {
            return context.eval("python", source);
        }
    }

    @Test
    public void helloworld() {
        assertEquals("hello world", run("'hello world'").asString());
    }

    @Test
    public void canImportBuiltinModules() {
        assertEquals("graalpy", run("import sys; sys.implementation.name").asString());
    }

    @Test
    public void canImportNonBuiltinModules() {
        assertEquals("email", run("import email; email.__name__").asString());
    }

    @Test
    public void doesNotLeakEnvironmentVariables() {
        assertEquals("<empty>", run("import os; os.environ.get('JAVA_HOME', '<empty>')").asString());
    }
}
