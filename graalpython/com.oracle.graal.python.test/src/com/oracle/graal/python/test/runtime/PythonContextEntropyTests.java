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
package com.oracle.graal.python.test.runtime;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Random;

import org.graalvm.polyglot.PolyglotException;
import org.graalvm.polyglot.Context;
import org.junit.After;
import org.junit.Test;

import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.graal.python.test.PythonTests;

public class PythonContextEntropyTests {

    @After
    public void tearDown() {
        PythonTests.closeContext();
    }

    @Test
    public void fixedInitializationEntropySourceSeedsHashSecretDeterministically() {
        long seed = 0x1234ABCDL;
        PythonTests.enterContext(Map.of("python.InitializationEntropySource", "fixed:0x1234ABCD"), new String[0]);
        PythonContext context = PythonContext.get(null);

        byte[] expected = new byte[24];
        new Random(seed).nextBytes(expected);

        assertArrayEquals(expected, context.getHashSecret());
    }

    @Test
    public void deviceInitializationEntropySourceSeedsHashSecretFromConfiguredPath() throws IOException {
        byte[] expected = new byte[24];
        for (int i = 0; i < expected.length; i++) {
            expected[i] = (byte) (i + 1);
        }
        byte[] source = new byte[expected.length + 8];
        System.arraycopy(expected, 0, source, 0, expected.length);
        for (int i = expected.length; i < source.length; i++) {
            source[i] = (byte) 0xFF;
        }
        Path tempFile = Files.createTempFile("graalpy-init-entropy-", ".bin");
        Files.write(tempFile, source);

        try {
            PythonTests.enterContext(Map.of("python.InitializationEntropySource", "device:" + tempFile), new String[0]);
            PythonContext context = PythonContext.get(null);
            assertArrayEquals(expected, context.getHashSecret());
        } finally {
            Files.deleteIfExists(tempFile);
        }
    }

    @Test
    public void deviceInitializationEntropySourceThrowsProviderExceptionWhenExhausted() throws IOException {
        Path tempFile = Files.createTempFile("graalpy-init-entropy-short-", ".bin");
        Files.write(tempFile, new byte[]{1, 2, 3, 4});

        try {
            try {
                PythonTests.enterContext(Map.of("python.InitializationEntropySource", "device:" + tempFile), new String[0]);
                fail("expected PolyglotException");
            } catch (PolyglotException e) {
                assertTrue(e.getMessage().contains("ProviderException"));
                assertTrue(e.getMessage().contains("initialization entropy device exhausted"));
            }
        } finally {
            Files.deleteIfExists(tempFile);
        }
    }

    @Test
    public void randomSeedNoneConsumesInitializationEntropyBytes() {
        long seed = 0x1234ABCDL;
        Context polyglotContext = PythonTests.enterContext(Map.of("python.InitializationEntropySource", "fixed:0x1234ABCD"), new String[]{"-S"});
        try {
            polyglotContext.eval("python", "import _random; _random.Random()");
            PythonContext context = PythonContext.get(null);
            byte[] actual = new byte[16];
            context.fillInitializationEntropyBytes(actual);

            Random expectedRandom = new Random(seed);
            expectedRandom.nextBytes(new byte[24]);
            expectedRandom.nextBytes(new byte[624 * Integer.BYTES]);
            byte[] expected = new byte[16];
            expectedRandom.nextBytes(expected);

            assertArrayEquals(expected, actual);
        } finally {
            PythonTests.closeContext();
        }
    }
}
