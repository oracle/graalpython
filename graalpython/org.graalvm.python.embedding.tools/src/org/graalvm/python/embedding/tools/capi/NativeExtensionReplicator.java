/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.python.embedding.tools.capi;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.BiFunction;
import java.util.function.Consumer;

import org.graalvm.python.embedding.tools.exec.GraalPyRunner;
import org.graalvm.python.embedding.tools.exec.SubprocessLog;

/**
 * Given a GraalPy virtual environment, this class helps prepare that environment so that multiple
 * GraalPy contexts in the same process can use its native extensions at the same time.
 *
 * The mechanism for this is tied to the implementation of GraalPy and the resulting venv is only
 * guaranteed to work with the matching GraalPy version.
 */
public final class NativeExtensionReplicator {
    public static void replicate(Path venvDirectory, SubprocessLog log, int count) throws IOException, InterruptedException {
        final StringBuilder sb = new StringBuilder();
        final SubprocessLog suffixLog = new SubprocessLog() {
            @Override
            public void subProcessOut(CharSequence out) {
                sb.append(out);
            }
        };

        GraalPyRunner.runVenvBin(venvDirectory, "graalpy", suffixLog, "-c", "print(__graalpython__.soabi, flush=True)");
        String suffix = sb.toString().trim();
        sb.delete(0, sb.length());
        GraalPyRunner.runVenvBin(venvDirectory, "graalpy", suffixLog, "-c", "print(__graalpython__.core_home, flush=True)");
        Path graalpyHome = Path.of(sb.toString().trim());
        sb.delete(0, sb.length());
        GraalPyRunner.runVenvBin(venvDirectory, "graalpy", suffixLog, "-c", "print(__graalpython__.capi_library, flush=True)");
        String capiLibrary = sb.toString().trim();

        // Relocate the C API library
        Files.walk(graalpyHome).forEach(fileWalkConsumer(venvDirectory, capiLibrary, count, null, log, (originalPath, newName) -> venvDirectory.resolve(newName)));

        // Relocate the core C extensions
        Files.walk(graalpyHome).forEach(fileWalkConsumer(venvDirectory, suffix, count, capiLibrary, log, (originalPath, newName) -> venvDirectory.resolve(newName)));

        // Relocate C extensions in the venv
        Files.walk(venvDirectory).forEach(fileWalkConsumer(venvDirectory, suffix, count, capiLibrary, log, (originalPath, newName) -> originalPath.resolveSibling(newName)));
    }

    private static Consumer<Path> fileWalkConsumer(Path venvDirectory, String extSuffix, int replicationCount, String capiLibraryName,
                    SubprocessLog log, BiFunction<Path, String, Path> getTargetFile) {
        return (f) -> {
            var filename = f.getFileName().toString();
            if (filename.endsWith(extSuffix) && f.toFile().isFile()) {
                try {
                    var o = SharedObject.open(venvDirectory, f, log);
                    for (int i = 0; i < replicationCount; i++) {
                        if (capiLibraryName != null) {
                            var newCapiLibraryName = capiLibraryName + "." + Integer.toHexString(i);
                            o.changeOrAddDependency(capiLibraryName, newCapiLibraryName);
                        }
                        var newName = filename + "." + Integer.toHexString(i);
                        o.setId(newName);
                        Files.write(getTargetFile.apply(f, newName), o.write());
                    }
                } catch (IOException | InterruptedException e) {
                    throw new RuntimeException(e);
                }
            }
        };
    }

    public static void main(String[] args) throws IOException, InterruptedException {
        try {
            String venvDir = args[0];
            int count = Byte.parseByte(args[1]);
            replicate(Path.of(venvDir), new SubprocessLog() {}, count);
        } catch (ArrayIndexOutOfBoundsException | NumberFormatException e) {
            System.err.println("Usage: PATH-TO-VENV MAX-CONTEXT-COUNT");
            System.exit(1);
        }
    }
}
