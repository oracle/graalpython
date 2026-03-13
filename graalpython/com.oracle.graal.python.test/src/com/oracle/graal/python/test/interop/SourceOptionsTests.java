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
package com.oracle.graal.python.test.interop;

import static org.junit.Assert.assertEquals;

import java.io.IOException;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Source;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.oracle.graal.python.test.PythonTests;

public class SourceOptionsTests extends PythonTests {
    private Context context;

    @Before
    public void setUpTest() {
        Context.Builder builder = Context.newBuilder();
        builder.allowExperimentalOptions(true);
        builder.allowAllAccess(true);
        context = builder.build();
    }

    @After
    public void tearDown() {
        context.close();
    }

    @Test
    public void testDefaultUsesMainModuleGlobals() {
        context.eval("python", "x = 41");
        assertEquals(42, context.eval("python", "x + 1").asInt());
    }

    @Test
    public void testNewGlobalsIsolatedFromMainModule() throws IOException {
        context.eval("python", "x = 41");

        Source source = Source.newBuilder("python", "x = 100\nx + 1", "new-globals.py").option("python.NewGlobals", "true").build();

        assertEquals(101, context.eval(source).asInt());
        assertEquals(42, context.eval("python", "x + 1").asInt());
    }

    @Test
    public void testSeparateNewGlobalsExecutionsDoNotShareState() throws IOException {
        Source writeSource = Source.newBuilder("python", "x = 100", "new-globals-write.py").option("python.NewGlobals", "true").build();
        Source readSource = Source.newBuilder("python", "x = globals().get('x', 0)\nx + 1", "new-globals-read.py").option("python.NewGlobals", "true").build();

        context.eval(writeSource);
        assertEquals(1, context.eval(readSource).asInt());
    }
}
