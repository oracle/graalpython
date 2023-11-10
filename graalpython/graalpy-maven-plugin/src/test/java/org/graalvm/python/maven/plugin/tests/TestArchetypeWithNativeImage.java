/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.python.maven.plugin.tests;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStreamReader;
import java.nio.file.Path;

import org.apache.maven.shared.verifier.VerificationException;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

public class TestArchetypeWithNativeImage extends GraalPyPluginTests {
    @Test
    public void archetypeWithNativeImage() throws Exception {
        Assumptions.assumeTrue(CAN_RUN_TESTS);
        // build
        var v = getLocalVerifier("archetype_ni_test");
        v.addCliArguments("package");
        v.execute();
        v.verifyTextInLog("-m venv");
        v.verifyTextInLog("-m ensurepip");
        v.verifyTextInLog("termcolor");
        v.verifyTextInLog("BUILD SUCCESS");

        // run
        v = getLocalVerifier("archetype_ni_test");
        v.setAutoclean(false);
        v.addCliArguments("-Pnative", "-DmainClass=GraalPy", "-DimageName=graalpy", "package");
        try {
            v.execute();
        } catch (VerificationException e) {
            v.verifyTextInLog("is not a GraalVM distribution");
            Assumptions.assumeTrue(false);
        }

        Process p = new ProcessBuilder(Path.of(v.getBasedir(), "target", "graalpy").toAbsolutePath().toString()).start();
        var buffer = new char[8192];
        var sb = new StringBuilder();
        try (var in = new InputStreamReader(p.getInputStream(), "UTF-8")) {
            while (true) {
                int size = in.read(buffer, 0, buffer.length);
                if (size < 0) {
                    break;
                }
                sb.append(buffer, 0, size);
            }
        }
        p.waitFor();
        var output = sb.toString();
        assertTrue(output.contains("/graalpy_vfs/home/lib/python3.10"));
        assertTrue(output.contains("/graalpy_vfs/home/lib/graalpy23.1/modules"));
        assertTrue(output.contains("/graalpy_vfs/venv/lib/python3.10/site-packages"));
    }
}
