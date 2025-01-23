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

package org.graalvm.python.embedding.cext.test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Set;
import java.util.logging.Handler;
import java.util.logging.LogRecord;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Engine;
import org.graalvm.polyglot.PolyglotException;
import org.graalvm.polyglot.Source;
import org.graalvm.python.embedding.tools.exec.SubprocessLog;
import org.graalvm.python.embedding.tools.vfs.VFSUtils;
import org.graalvm.python.embedding.tools.vfs.VFSUtils.Log;
import org.junit.Test;

public class MultiContextCExtTest {
    static final class TestLog extends Handler implements SubprocessLog, Log {
        final StringBuilder logCharSequence = new StringBuilder();
        final StringBuilder logThrowable = new StringBuilder();
        final StringBuilder stderr = new StringBuilder();
        final StringBuilder stdout = new StringBuilder();
        final StringBuilder info = new StringBuilder();
        final StringBuilder truffleLog = new StringBuilder();

        static void println(CharSequence... args) {
            if (isVerbose()) {
                System.out.println(String.join(" ", args));
            }
        }

        public void log(CharSequence txt) {
            println("[log]", txt);
            logCharSequence.append(txt);
        }

        public void log(CharSequence txt, Throwable t) {
            println("[log]", txt);
            println("[throwable]", t.getMessage());
            logThrowable.append(txt).append(t.getMessage());
        }

        public void subProcessErr(CharSequence err) {
            println("[err]", err);
            stderr.append(err);
        }

        public void subProcessOut(CharSequence out) {
            println("[out]", out);
            stdout.append(out);
        }

        public void info(String s) {
            println("[info]", s);
            info.append(s);
        }

        @Override
        public void publish(LogRecord record) {
            var msg = String.format("[%s] %s: %s", record.getLoggerName(), record.getLevel().getName(), String.format(record.getMessage(), record.getParameters()));
            println(msg);
            truffleLog.append(msg);
        }

        @Override
        public void flush() {
        }

        @Override
        public void close() {
        }
    }

    private static Path createVenv(TestLog log, String... packages) throws IOException {
        var tmpdir = Files.createTempDirectory("graalpytest");
        deleteDirOnShutdown(tmpdir);
        var venvdir = tmpdir.resolve("venv");
        try {
            VFSUtils.createVenv(venvdir, Arrays.asList(packages), tmpdir.resolve("graalpy.exe"), () -> getClasspath(), "", log, log);
        } catch (RuntimeException e) {
            System.err.println(getClasspath());
            throw e;
        }
        return venvdir;
    }

    private static void deleteDirOnShutdown(Path tmpdir) {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try (var fs = Files.walk(tmpdir)) {
                fs.sorted(Comparator.reverseOrder()).map(Path::toFile).forEach(File::delete);
            } catch (IOException e) {
            }
        }));
    }

    private static Set<String> getClasspath() {
        var sb = new ArrayList<String>();
        var modPath = System.getProperty("jdk.module.path");
        if (modPath != null) {
            sb.add(modPath);
        }
        var classPath = System.getProperty("java.class.path");
        if (classPath != null) {
            sb.add(classPath);
        }
        var cp = String.join(File.pathSeparator, sb);
        return Set.copyOf(Arrays.stream(cp.split(File.pathSeparator)).toList());
    }

    @Test
    public void testCreatingVenvForMulticontext() throws IOException {
        var log = new TestLog();
        Path venv;

        String pythonNative;
        String exe;
        if (System.getProperty("os.name").toLowerCase().contains("win")) {
            pythonNative = "python-native.dll";
            venv = createVenv(log, "delvewheel==1.9.0");
            exe = venv.resolve("Scripts").resolve("python.exe").toString().replace('\\', '/');
        } else if (System.getProperty("os.name").toLowerCase().contains("mac")) {
            pythonNative = "libpython-native.dylib";
            venv = createVenv(log);
            exe = venv.resolve("bin").resolve("python").toString();
        } else {
            pythonNative = "libpython-native.so";
            venv = createVenv(log, "patchelf");
            exe = venv.resolve("bin").resolve("python").toString();
        }

        var engine = Engine.newBuilder("python").logHandler(log).build();
        var builder = Context.newBuilder().engine(engine).allowAllAccess(true).option("python.Sha3ModuleBackend", "native").option("python.ForceImportSite", "true").option("python.Executable",
                        exe).option("log.python.level", "CONFIG");
        var contexts = new ArrayList<Context>();
        try {
            Context c0, c1, c2, c3, c4, c5;
            contexts.add(c0 = builder.build());
            c0.initialize("python");
            c0.eval("python", String.format("__graalpython__.replicate_extensions_in_venv('%s', 2)", venv.toString().replace('\\', '/')));

            assertTrue("created a copy of the capi", Files.list(venv).anyMatch((p) -> p.getFileName().toString().startsWith(pythonNative) && p.getFileName().toString().endsWith(".dup0")));
            assertTrue("created another copy of the capi", Files.list(venv).anyMatch((p) -> p.getFileName().toString().startsWith(pythonNative) && p.getFileName().toString().endsWith(".dup1")));
            assertFalse("created no more copies of the capi", Files.list(venv).anyMatch((p) -> p.getFileName().toString().startsWith(pythonNative) && p.getFileName().toString().endsWith(".dup2")));

            builder.option("python.IsolateNativeModules", "true");
            contexts.add(c1 = builder.build());
            contexts.add(c2 = builder.build());
            contexts.add(c3 = builder.build());
            contexts.add(c4 = builder.build());
            builder.option("python.Executable", "");
            contexts.add(c5 = builder.build());
            c0.initialize("python");
            c1.initialize("python");
            c2.initialize("python");
            c3.initialize("python");
            c4.initialize("python");
            c5.initialize("python");
            var code = Source.create("python", "import _sha3; _sha3.implementation");
            // First one works
            var r1 = c1.eval(code);
            assertEquals("tiny_sha3", r1.asString());
            assertFalse("created no more copies of the capi", Files.list(venv).anyMatch((p) -> p.getFileName().toString().startsWith(pythonNative) && p.getFileName().toString().endsWith(".dup2")));
            // Second one works because of isolation
            var r2 = c2.eval(code);
            assertEquals("tiny_sha3", r2.asString());
            c2.eval("python", "import _sha3; _sha3.implementation = '12'");
            r2 = c2.eval(code);
            assertEquals("12", r2.asString());
            // first context is unaffected
            r1 = c1.eval(code);
            assertEquals("tiny_sha3", r1.asString());
            assertFalse("created no more copies of the capi", Files.list(venv).anyMatch((p) -> p.getFileName().toString().startsWith(pythonNative) && p.getFileName().toString().endsWith(".dup2")));
            // Third one works and triggers a dynamic relocation
            c3.eval(code);
            assertTrue("created another copy of the capi", Files.list(venv).anyMatch((p) -> p.getFileName().toString().startsWith(pythonNative) && p.getFileName().toString().endsWith(".dup2")));
            // Fourth one does not work because we changed the sys.prefix
            c4.eval("python", "import sys; sys.prefix = 12");
            try {
                c4.eval(code);
                fail("should not reach here");
            } catch (PolyglotException e) {
                assertTrue("We rely on sys.prefix", e.getMessage().contains("sys.prefix must be a str"));
            }
            // Fifth one does not work because we don't have the venv configured
            try {
                c5.eval(code);
                fail("should not reach here");
            } catch (PolyglotException e) {
                assertTrue("We need a venv", e.getMessage().contains("sys.prefix must point to a venv"));
            }
            // Using a context without isolation in the same process needs to use LLVM
            assertFalse("have not had a context use LLVM, yet", log.truffleLog.toString().contains("as bitcode"));
            try {
                c0.eval(code);
                fail("should not reach here");
            } catch (PolyglotException e) {
                assertTrue("needs LLVM", e.getMessage().contains("LLVM"));
            }
        } finally {
            for (var c : contexts) {
                c.close(true);
            }
        }
    }

    private static boolean isVerbose() {
        return Boolean.getBoolean("com.oracle.graal.python.test.verbose");
    }
}
