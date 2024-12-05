/*
 * Copyright (c) 2023, 2024, Oracle and/or its affiliates. All rights reserved.
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

package org.graalvm.python.embedding.utils.test.integration;

import static org.junit.Assert.assertEquals;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

import org.graalvm.python.embedding.tools.capi.NativeExtensionReplicator;
import org.graalvm.python.embedding.tools.exec.SubprocessLog;
import org.graalvm.python.embedding.tools.vfs.VFSUtils;
import org.graalvm.python.embedding.tools.vfs.VFSUtils.Log;
import org.junit.Test;

public class MultiContextCExtTest {
    static final class TestLog implements SubprocessLog, Log {
        final StringBuilder logCharSequence = new StringBuilder();
        final StringBuilder logThrowable = new StringBuilder();
        final StringBuilder stderr = new StringBuilder();
        final StringBuilder stdout = new StringBuilder();
        final StringBuilder info = new StringBuilder();

        public void log(CharSequence txt) {
            System.out.print("[log] ");
            System.out.println(txt);
            logCharSequence.append(txt);
        }

        public void log(CharSequence txt, Throwable t) {
            System.out.print("[log] ");
            System.out.println(txt);
            System.out.print("[throwable] ");
            System.out.println(t.getMessage());
            logThrowable.append(txt).append(t.getMessage());
        }

        public void subProcessErr(CharSequence err) {
            System.out.print("[err] ");
            System.out.println(err);
            stderr.append(err);
        }

        public void subProcessOut(CharSequence out) {
            System.out.print("[out] ");
            System.out.println(out);
            stdout.append(out);
        }

        public void info(String s) {
            System.out.print("[info] ");
            System.out.println(s);
            info.append(s);
        }
    }

    private static Path createVenv(TestLog log) throws IOException {
        var tmpdir = Files.createTempDirectory("graalpytest");
        tmpdir.toFile().deleteOnExit();
        var venvdir = tmpdir.resolve("venv");
        VFSUtils.createVenv(venvdir, List.of(), tmpdir.resolve("graalpy.exe"), () -> getClasspath(), "", log, log);
        return venvdir;
    }

    private static Set<String> getClasspath() {
        return Set.copyOf(Arrays.stream((System.getProperty("jdk.module.path") + File.pathSeparator + System.getProperty("java.class.path")).split(File.pathSeparator)).toList());
    }

    @Test
    public void testCreatingVenv() throws IOException {
        var log = new TestLog();
        createVenv(log);
        assertEquals("", log.stderr.toString());
    }

    @Test
    public void testCreatingVenvForMulticontext() throws IOException, InterruptedException {
        var log = new TestLog();
        var venv = createVenv(log);
        NativeExtensionReplicator.replicate(venv, log, 2);
    }
}
