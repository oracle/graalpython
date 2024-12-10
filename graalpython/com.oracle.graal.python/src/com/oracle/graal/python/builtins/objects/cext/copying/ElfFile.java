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

package com.oracle.graal.python.builtins.objects.cext.copying;

import java.io.IOException;
import java.nio.file.StandardOpenOption;

import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.graal.python.runtime.PythonOptions;
import com.oracle.truffle.api.TruffleFile;
import com.oracle.truffle.api.TruffleLanguage.Env;

final class ElfFile extends SharedObject {
    private final PythonContext context;
    private final TruffleFile tempfile;

    private String getPatchelf() {
        Env env = context.getEnv();
        var path = env.getEnvironment().getOrDefault("PATH", "").split(env.getPathSeparator());
        var executable = env.getPublicTruffleFile(context.getOption(PythonOptions.Executable).toJavaStringUncached());
        var patchelf = executable.resolveSibling("patchelf");
        var i = 0;
        while (!patchelf.isExecutable() && i < path.length) {
            patchelf = env.getPublicTruffleFile(path[i++]).resolve("patchelf");
        }
        return patchelf.toString();
    }

    ElfFile(byte[] b, PythonContext context) throws IOException {
        this.context = context;
        this.tempfile = context.getEnv().createTempFile(null, null, ".so");
        this.context.registerAtexitHook((ctx) -> {
            try {
                this.tempfile.delete();
            } catch (IOException e) {
                // ignore
            }
        });
        try (var os = this.tempfile.newOutputStream(StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE)) {
            os.write(b);
        }
    }

    @Override
    public void setId(String newId) throws IOException, InterruptedException {
        var pb = newProcessBuilder(context);
        pb.command(getPatchelf(), "--debug", "--set-soname", newId, tempfile.toString());
        var proc = pb.start();
        if (proc.waitFor() != 0) {
            throw new IOException("Failed to run `patchelf` command. Make sure you have it on your PATH or installed in your venv.");
        }
    }

    @Override
    public void changeOrAddDependency(String oldName, String newName) throws IOException, InterruptedException {
        var pb = newProcessBuilder(context);
        pb.command(getPatchelf(), "--debug", "--remove-needed", oldName, tempfile.toString());
        var proc = pb.start();
        if (proc.waitFor() != 0) {
            throw new IOException("Failed to run `patchelf` command. Make sure you have it on your PATH or installed in your venv.");
        }
        pb.command(getPatchelf(), "--debug", "--add-needed", newName, tempfile.toString());
        proc = pb.start();
        if (proc.waitFor() != 0) {
            throw new IOException("Failed to run `patchelf` command. Make sure you have it on your PATH or installed in your venv.");
        }
    }

    @Override
    public byte[] write() throws IOException {
        return tempfile.readAllBytes();
    }
}
