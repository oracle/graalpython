/*
 * Copyright (c) 2020, 2025, Oracle and/or its affiliates. All rights reserved.
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

import static org.junit.Assert.assertTrue;

import java.io.File;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Engine;
import org.graalvm.polyglot.io.IOAccess;
import org.junit.Before;
import org.junit.Test;

public class ResourcesTest {
    public static final boolean RUNNING_WITH_LANGUAGE_HOME = System.getProperty("org.graalvm.language.python.home") != null;

    @Before
    public void setUp() {
        org.junit.Assume.assumeFalse(RUNNING_WITH_LANGUAGE_HOME);
    }

    @Test
    public void testResourcesAsHome() {
        try (Context context = Context.newBuilder("python").allowExperimentalOptions(true).option("python.PythonHome", "/path/that/does/not/exist").build()) {
            String foundHome = context.eval("python", "__graalpython__.home").asString();
            assertTrue(foundHome, foundHome.contains("python" + File.separator + "python-home"));
        }

        try (Context context = Context.newBuilder("python").allowExperimentalOptions(true).option("python.PythonHome", "").build()) {
            String foundHome = context.eval("python", "__graalpython__.home").asString();
            assertTrue(foundHome, !foundHome.contains("graalpython"));
        }
    }

    @Test
    public void testResourcesAlwaysAllowReading() {
        try (Engine engine = Engine.create("python");
                        Context context = Context.newBuilder("python").engine(engine).allowIO(IOAccess.NONE).option("python.PythonHome", "/path/that/does/not/exist").build()) {
            String foundHome = context.eval("python", "import email; email.__spec__.origin").asString();
            assertTrue(foundHome, foundHome.contains("python" + File.separator + "python-home"));
        }
    }
}
