/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.test.advanced;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Engine;
import org.junit.Assume;
import org.junit.Test;

public class CompilerFailureExitTest {
    private static final String ENABLE_FLAG_ENV_NAME = "GRAALPYTHON_JUNIT_COMPILER_FAILURE_EXIT_TEST";

    @Test
    public void compilerBailoutExitsVM() {
        Assume.assumeTrue(ENABLE_FLAG_ENV_NAME + " is not set", "true".equals(System.getenv(ENABLE_FLAG_ENV_NAME)));
        try (Engine engine = Engine.newBuilder("python").allowExperimentalOptions(true).//
                        option("engine.BackgroundCompilation", "false").//
                        option("engine.FirstTierCompilationThreshold", "1").//
                        option("engine.LastTierCompilationThreshold", "10").build();
                        Context context = Context.newBuilder("python").engine(engine).allowExperimentalOptions(true).allowAllAccess(true).//
                                        option("python.EnableDebuggingBuiltins", "true").build()) {
            context.eval("python", """
                            import __graalpython__

                            def trigger_compiler_bailout():
                                for _ in range(100):
                                    __graalpython__.compiler_bailout_for_tests()

                            trigger_compiler_bailout()
                            """);
        }
    }
}
