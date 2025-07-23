/*
 * Copyright (c) 2024, 2025, Oracle and/or its affiliates. All rights reserved.
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
import java.io.OutputStream;

import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.graal.python.runtime.PythonOptions;
import com.oracle.truffle.api.TruffleFile;
import com.oracle.truffle.api.TruffleLanguage.Env;
import com.oracle.truffle.api.TruffleLogger;
import com.oracle.truffle.api.io.TruffleProcessBuilder;

abstract class SharedObject implements AutoCloseable {
    abstract void setId(String newId) throws IOException, InterruptedException;

    abstract void changeOrAddDependency(String oldName, String newName) throws IOException, InterruptedException;

    abstract void write(TruffleFile copy) throws IOException, InterruptedException;

    public abstract void close() throws IOException;

    static SharedObject open(TruffleFile file, PythonContext context) throws IOException {
        var f = file.readAllBytes();
        switch (f[0]) {
            case 0x7f:
                return new ElfFile(f, context);
            case 0x4d, 0x5a:
                return new PEFile(f, context);
            case (byte) 0xca, (byte) 0xfe, (byte) 0xce, (byte) 0xcf:
                throw new IOException("Modifying Mach-O files is not yet supported");
            default:
                throw new IOException("Unknown shared object format");
        }
    }

    protected static final TruffleLogger LOGGER = NativeLibraryLocator.LOGGER;

    protected static final class LoggingOutputStream extends OutputStream {
        private final StringBuilder sb = new StringBuilder();

        @Override
        public void write(int b) throws IOException {
            sb.append((char) b);
        }

        @Override
        public void write(byte[] b, int off, int len) throws IOException {
            sb.append(new String(b, off, len));
        }

        @Override
        public void flush() {
            LOGGER.fine(sb::toString);
            sb.setLength(0);
        }
    }

    protected static TruffleProcessBuilder newProcessBuilder(PythonContext context) {
        var pb = context.getEnv().newProcessBuilder();
        pb.redirectOutput(pb.createRedirectToStream(new LoggingOutputStream()));
        pb.redirectError(pb.createRedirectToStream(new LoggingOutputStream()));
        return pb;
    }

    private static boolean isExecutable(TruffleFile executable) {
        try {
            return executable.isExecutable();
        } catch (SecurityException e) {
            return false;
        }
    }

    protected static TruffleFile which(PythonContext context, String command) {
        Env env = context.getEnv();
        var path = env.getEnvironment().getOrDefault("PATH", "").split(env.getPathSeparator());
        var executable = env.getPublicTruffleFile(context.getOption(PythonOptions.Executable).toJavaStringUncached());
        var candidate = executable.resolveSibling(command);
        var i = 0;
        while (!isExecutable(candidate) && i < path.length) {
            candidate = env.getPublicTruffleFile(path[i++]).resolve(command);
        }
        return candidate;
    }
}
