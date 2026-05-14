/*
 * Copyright (c) 2024, 2026, Oracle and/or its affiliates. All rights reserved.
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

package com.oracle.graal.python.builtins.objects.cext.copying;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;

import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.truffle.api.TruffleFile;

final class ElfFile extends SharedObject {
    private static final String PATCHELF_INSTALL_INSTRUCTION = "IsolateNativeModules option needs `patchelf` tool to copy libraries. Make sure you have it available " +
                    "on PATH or installed in your venv.";

    private final PythonContext context;
    private final TruffleFile tempfile;

    private String getPatchelf() throws NativeLibraryToolException {
        TruffleFile patchelf = which(context, "patchelf");
        if (!patchelf.exists()) {
            throw new NativeLibraryToolException("Could not find `patchelf`. " + PATCHELF_INSTALL_INSTRUCTION);
        }
        return patchelf.toString();
    }

    private void runPatchelf(String action, String... arguments) throws NativeLibraryToolException {
        var command = new String[arguments.length + 1];
        command[0] = getPatchelf();
        System.arraycopy(arguments, 0, command, 1, arguments.length);
        var pb = newProcessBuilder(context);
        var stderr = new ByteArrayOutputStream();
        pb.redirectError(pb.createRedirectToStream(stderr));
        pb.command(command);
        Process proc;
        try {
            proc = pb.start();
        } catch (IOException e) {
            throw new NativeLibraryToolException("Failed to start `patchelf` to " + action + ": " + e.getMessage() + ". " + PATCHELF_INSTALL_INSTRUCTION, e);
        }
        try {
            if (proc.waitFor() != 0) {
                throw new NativeLibraryToolException("Failed to run `patchelf` to " + action + " (exit code " + proc.exitValue() + "). " + PATCHELF_INSTALL_INSTRUCTION +
                                " Stderr: " + getStderr(stderr));
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new NativeLibraryToolException("Interrupted while waiting for `patchelf` to " + action + ". " + PATCHELF_INSTALL_INSTRUCTION, e);
        }
    }

    private static String getStderr(ByteArrayOutputStream stderr) {
        String output = stderr.toString(StandardCharsets.UTF_8).strip();
        return output.isEmpty() ? "<empty>" : output;
    }

    private static void deleteTempfile(TruffleFile tempfile) throws NativeLibraryToolException {
        try {
            tempfile.delete();
        } catch (IOException e) {
            throw new NativeLibraryToolException("Failed to delete temporary ELF library copy '" + tempfile + "': " + e.getMessage(), e);
        }
    }

    private static void deleteTempfileAfterFailedInit(TruffleFile tempfile, IOException failure) throws NativeLibraryToolException {
        var exception = new NativeLibraryToolException("Failed to write temporary ELF library copy '" + tempfile + "' for IsolateNativeModules relocation: " + failure.getMessage(), failure);
        try {
            deleteTempfile(tempfile);
        } catch (NativeLibraryToolException e) {
            exception.addSuppressed(e);
        }
        throw exception;
    }

    private static void writeTempfile(TruffleFile tempfile, byte[] b) throws NativeLibraryToolException {
        try (var os = tempfile.newOutputStream(StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE)) {
            os.write(b);
        } catch (IOException e) {
            deleteTempfileAfterFailedInit(tempfile, e);
        }
    }

    private static TruffleFile createTempfile(PythonContext context) throws NativeLibraryToolException {
        try {
            return context.getEnv().createTempFile(null, null, ".so");
        } catch (IOException e) {
            throw new NativeLibraryToolException("Failed to create temporary ELF library copy for IsolateNativeModules relocation: " + e.getMessage(), e);
        }
    }

    ElfFile(byte[] b, PythonContext context) throws NativeLibraryToolException {
        this.context = context;
        this.tempfile = createTempfile(context);
        writeTempfile(tempfile, b);
    }

    @Override
    public void setId(String newId) throws NativeLibraryToolException {
        runPatchelf("set SONAME", "--debug", "--set-soname", newId, tempfile.toString());
    }

    @Override
    public void changeOrAddDependency(String oldName, String newName) throws NativeLibraryToolException {
        runPatchelf("remove dependency", "--debug", "--remove-needed", oldName, tempfile.toString());
        runPatchelf("add dependency", "--debug", "--add-needed", newName, tempfile.toString());
    }

    @Override
    public void write(TruffleFile copy) throws NativeLibraryToolException {
        try {
            tempfile.copy(copy, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.COPY_ATTRIBUTES);
        } catch (IOException e) {
            throw new NativeLibraryToolException("Failed to write relocated ELF library copy '" + copy + "': " + e.getMessage(), e);
        }
    }

    @Override
    public void close() throws NativeLibraryToolException {
        deleteTempfile(tempfile);
    }
}
