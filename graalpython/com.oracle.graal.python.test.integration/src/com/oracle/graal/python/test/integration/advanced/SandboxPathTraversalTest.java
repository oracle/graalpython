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
package com.oracle.graal.python.test.integration.advanced;

import static com.oracle.graal.python.test.integration.Utils.IS_WINDOWS;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeFalse;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.io.IOAccess;
import org.junit.Test;

public class SandboxPathTraversalTest {
    private static final String MARKER = "sandbox-path-traversal-marker";
    private static final String LEAKED_PREFIX = "LEAKED:";
    private static final String POSIX_BACKEND = "java";

    @Test
    public void cannotListHostDirectoryViaLanguageHomeDotDotPath() throws IOException {
        assumeFalse(IS_WINDOWS);
        Path directory = Files.createTempDirectory("graalpy-sandbox-list");
        Path markerFile = Files.writeString(directory.resolve("marker.txt"), MARKER, StandardCharsets.UTF_8);
        try {
            String source = listDirectoryScript(pythonStringLiteral(directory.toRealPath().toString()));
            String unrestrictedResult = eval(source, IOAccess.ALL);
            assertTrue(unrestrictedResult, unrestrictedResult.contains(markerFile.getFileName().toString()));

            String result = eval(source, IOAccess.NONE);
            assertFalse(result, result.startsWith(LEAKED_PREFIX));
        } finally {
            Files.deleteIfExists(markerFile);
            Files.deleteIfExists(directory);
        }
    }

    @Test
    public void cannotReadHostPyFileViaLanguageHomeDotDotPath() throws IOException {
        assumeFalse(IS_WINDOWS);
        Path directory = Files.createTempDirectory("graalpy-sandbox-read");
        Path secretFile = Files.writeString(directory.resolve("secret.py"), MARKER, StandardCharsets.UTF_8);
        try {
            String source = readFileScript(pythonStringLiteral(secretFile.toRealPath().toString()));
            assertEquals(LEAKED_PREFIX + MARKER, eval(source, IOAccess.ALL));

            String result = eval(source, IOAccess.NONE);
            assertFalse(result, result.startsWith(LEAKED_PREFIX));
        } finally {
            Files.deleteIfExists(secretFile);
            Files.deleteIfExists(directory);
        }
    }

    @Test
    public void cannotReadHostFileViaTracebackSourceLineRendering() throws IOException {
        assumeFalse(IS_WINDOWS);
        Path directory = Files.createTempDirectory("graalpy-sandbox-traceback");
        String contents = MARKER + "\n" + MARKER + "-second\n";
        Path secretFile = Files.writeString(directory.resolve("secret"), contents, StandardCharsets.UTF_8);
        try {
            String source = tracebackSourceLineScript(pythonStringLiteral(secretFile.toRealPath().toString()));
            assertEquals(LEAKED_PREFIX + MARKER + "\n" + MARKER + "-second", eval(source, IOAccess.ALL));

            String result = eval(source, IOAccess.NONE);
            assertFalse(result, result.startsWith(LEAKED_PREFIX));
            assertFalse(result, result.contains(MARKER));
        } finally {
            Files.deleteIfExists(secretFile);
            Files.deleteIfExists(directory);
        }
    }

    private static String eval(String source, IOAccess ioAccess) {
        try (Context context = Context.newBuilder("python").allowIO(ioAccess).option("python.PosixModuleBackend", POSIX_BACKEND).build()) {
            assertEquals(POSIX_BACKEND, context.eval("python", "__graalpython__.posix_module_backend()").asString());
            return context.eval("python", source).asString();
        }
    }

    private static String listDirectoryScript(String directory) {
        return """
                        %s
                        import os

                        # Build a host directory path prefixed with the language home and followed
                        # by enough '..' components to escape it again lexically.
                        escaped = escaped_path(%s)
                        try:
                            # A successful result would mean os.listdir reached the host directory
                            # through getPublicTruffleFileRelaxed despite restricted IO.
                            result = "LEAKED:" + ",".join(sorted(os.listdir(escaped)))
                        except BaseException as e:
                            result = "BLOCKED:" + type(e).__name__
                        result
                        """.formatted(escapedPathFunction(), directory);
    }

    private static String readFileScript(String file) {
        return """
                        %s
                        # The target file is deliberately named secret.py to exercise the relaxed
                        # guard's allowed-suffix branch.
                        escaped = escaped_path(%s)
                        try:
                            # A successful result would mean open() reached the host file through
                            # the internal TruffleFile returned by getPublicTruffleFileRelaxed.
                            with open(escaped, "r", encoding="utf-8") as f:
                                result = "LEAKED:" + f.read()
                        except BaseException as e:
                            result = "BLOCKED:" + type(e).__name__
                        result
                        """.formatted(escapedPathFunction(), file);
    }

    private static String tracebackSourceLineScript(String file) {
        return """
                        import io
                        import traceback

                        def read_file(host_path, max_lines=100):
                            # Ask traceback rendering for source lines associated with a compiled
                            # code object's host filename. The filename is the raw host path, not a
                            # language-home-prefixed traversal path.
                            out = []
                            for n in range(1, max_lines + 1):
                                src = "\\n" * (n - 1) + "1/0\\n"
                                buf = io.StringIO()
                                try:
                                    exec(compile(src, host_path, "exec"))
                                except ZeroDivisionError:
                                    traceback.print_exc(file=buf)
                                line = None
                                for ln in buf.getvalue().splitlines():
                                    s = ln.strip()
                                    if s and not s.startswith(("Traceback", "File ", "ZeroDivisionError", "~", "1/0")):
                                        line = s
                                        break
                                if line is None:
                                    break
                                out.append(line)
                            return out

                        try:
                            lines = read_file(%s)
                            result = "LEAKED:" + "\\n".join(lines) if lines else "BLOCKED:no-source-line"
                        except BaseException as e:
                            result = "BLOCKED:" + type(e).__name__
                        result
                        """.formatted(file);
    }

    private static String escapedPathFunction() {
        return """
                        def escaped_path(target):
                            import os
                            # __graalpython__.home is PythonContext.langHome, which is what
                            # isPyFileInLanguageHome compares against.
                            home = __graalpython__.home
                            # Count non-empty path elements so the generated path climbs from the
                            # absolute language home back to the filesystem root. The raw path still
                            # starts with home, but its normalized form is the supplied host target.
                            parts = [p for p in home.split(os.sep) if p]
                            return home + (os.sep + "..") * len(parts) + target
                        """;
    }

    private static String pythonStringLiteral(String value) {
        return "'" + value.replace("\\", "\\\\").replace("'", "\\'") + "'";
    }
}
