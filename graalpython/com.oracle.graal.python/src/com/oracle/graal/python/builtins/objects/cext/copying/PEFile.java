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

final class PEFile extends SharedObject {
    private static final String DELVEWHEEL_VERSION = "1.9.0";
    private static final String DELVEWHEEL_INSTALL_INSTRUCTION = "IsolateNativeModules option needs `delvewheel` tool to copy libraries. Make sure you have `delvewheel==" + DELVEWHEEL_VERSION +
                    "` available in the virtualenv or on PATH (needs environment access).";

    private final PythonContext context;
    private final TruffleFile tempfile;

    PEFile(byte[] b, PythonContext context) throws NativeLibraryToolException {
        this.context = context;
        TruffleFile temp;
        try {
            temp = context.getEnv().createTempFile(null, null, ".dll");
        } catch (IOException e) {
            throw new NativeLibraryToolException("Failed to create temporary PE library copy for IsolateNativeModules relocation: " + e.getMessage(), e);
        }
        this.tempfile = temp;
        try (var os = tempfile.newOutputStream(StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE)) {
            os.write(b);
        } catch (IOException e) {
            throw new NativeLibraryToolException("Failed to write temporary PE library copy '" + tempfile + "' for IsolateNativeModules relocation: " + e.getMessage(), e);
        }
    }

    @Override
    public void setId(String newId) {
        // TODO
    }

    private String getDelvewheelPython() throws NativeLibraryToolException {
        TruffleFile delvewheel = which(context, "delvewheel.exe");
        if (!delvewheel.exists()) {
            delvewheel = which(context, "delvewheel.bat");
        }
        if (!delvewheel.exists()) {
            delvewheel = which(context, "delvewheel.cmd");
        }
        if (!delvewheel.exists()) {
            throw new NativeLibraryToolException("Could not find `delvewheel`. " + DELVEWHEEL_INSTALL_INSTRUCTION);
        }
        TruffleFile python = delvewheel.resolveSibling("python.exe");
        if (!python.exists()) {
            python = delvewheel.resolveSibling("python.bat");
        }
        if (!python.exists()) {
            python = delvewheel.resolveSibling("python.cmd");
        }
        if (!python.exists()) {
            python = delvewheel.getParent().resolveSibling("python.exe");
        }
        if (!python.exists()) {
            python = delvewheel.getParent().resolveSibling("python.bat");
        }
        if (!python.exists()) {
            python = delvewheel.getParent().resolveSibling("python.cmd");
        }
        if (!python.exists()) {
            throw new NativeLibraryToolException("Could not find Python executable next to `delvewheel` at '" + delvewheel + "'. " + DELVEWHEEL_INSTALL_INSTRUCTION);
        }
        return python.toString();
    }

    @Override
    public void changeOrAddDependency(String oldName, String newName) throws NativeLibraryToolException {
        var pb = newProcessBuilder(context);
        var stderr = new ByteArrayOutputStream();
        pb.redirectError(pb.createRedirectToStream(stderr));
        var tempfileWithForwardSlashes = tempfile.toString().replace('\\', '/');
        String pythonExe = getDelvewheelPython();
        pb.command(pythonExe, "-c",
                        String.format("from delvewheel import _dll_utils; _dll_utils.replace_needed('%s', ['%s'], {'%s': '%s'}, strip=True, verbose=2, test=[])",
                                        tempfileWithForwardSlashes, oldName, oldName, newName));
        Process proc;
        try {
            proc = pb.start();
        } catch (IOException e) {
            throw new NativeLibraryToolException("Failed to start `delvewheel` to copy required DLL: " + e.getMessage() + ". " + DELVEWHEEL_INSTALL_INSTRUCTION, e);
        }
        try {
            if (proc.waitFor() != 0) {
                throw new NativeLibraryToolException("Failed to run `delvewheel` to copy required DLL (exit code " + proc.exitValue() + "). " + DELVEWHEEL_INSTALL_INSTRUCTION +
                                " Stderr: " + getStderr(stderr));
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new NativeLibraryToolException("Interrupted while waiting for `delvewheel` to copy required DLL. " + DELVEWHEEL_INSTALL_INSTRUCTION, e);
        }
    }

    private static String getStderr(ByteArrayOutputStream stderr) {
        String output = stderr.toString(StandardCharsets.UTF_8).strip();
        return output.isEmpty() ? "<empty>" : output;
    }

    @Override
    public void write(TruffleFile copy) throws NativeLibraryToolException {
        try {
            tempfile.copy(copy, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.COPY_ATTRIBUTES);
        } catch (IOException e) {
            throw new NativeLibraryToolException("Failed to write relocated PE library copy '" + copy + "': " + e.getMessage(), e);
        }
    }

    @Override
    public void close() throws NativeLibraryToolException {
        try {
            tempfile.delete();
        } catch (IOException e) {
            throw new NativeLibraryToolException("Failed to delete temporary PE library copy '" + tempfile + "': " + e.getMessage(), e);
        }
    }
}
