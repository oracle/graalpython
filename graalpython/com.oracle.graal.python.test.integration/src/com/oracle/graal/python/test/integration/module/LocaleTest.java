/*
 * Copyright (c) 2023, 2025, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.test.integration.module;

import static com.oracle.graal.python.test.integration.PythonTests.assertPrints;
import static org.junit.Assert.assertEquals;

import java.nio.charset.Charset;
import java.util.Locale;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Engine;
import org.graalvm.polyglot.Value;
import org.junit.Assume;
import org.junit.Test;

public class LocaleTest {
    @Test
    public void getlocaleWithJvmLocale() {
        Assume.assumeFalse("Setting host locale is not propagated to isolate", Boolean.getBoolean("polyglot.engine.SpawnIsolate"));
        String expectedEncoding = Charset.defaultCharset().displayName();
        String expectedOutput = String.format("('it_IT', '%s')\n", expectedEncoding);
        Locale currentDefault = Locale.getDefault();
        try {
            Locale.setDefault(Locale.ITALY);
            assertPrints(expectedOutput, "import locale; print(locale.getlocale())");
        } finally {
            Locale.setDefault(currentDefault);
        }
    }

    @Test
    public void localeconvWithJvmLocale() {
        Assume.assumeFalse("Setting host locale is not propagated to isolate", Boolean.getBoolean("polyglot.engine.SpawnIsolate"));
        Locale currentDefault = Locale.getDefault();
        try {
            Locale.setDefault(Locale.ITALY);
            assertPrints("EUR\n", "import locale; print(locale.localeconv()['int_curr_symbol'])");
        } finally {
            Locale.setDefault(currentDefault);
        }
    }

    @Test
    public void getlocaleWithOption() {
        String expectedEncoding = Charset.defaultCharset().displayName();
        try (Engine engine = Engine.create("python"); Context context = Context.newBuilder("python").engine(engine).option("python.InitialLocale", "en_GB").build()) {
            Value tuple = context.eval("python", "import locale; locale.getlocale()");
            assertEquals("en_GB", tuple.getArrayElement(0).asString());
            assertEquals(expectedEncoding, tuple.getArrayElement(1).asString());

            Value currency = context.eval("python", "import locale; locale.localeconv()['int_curr_symbol']");
            assertEquals("GBP", currency.asString());
        }
    }
}
