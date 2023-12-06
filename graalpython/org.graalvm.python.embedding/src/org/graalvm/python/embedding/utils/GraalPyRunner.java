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
package org.graalvm.python.embedding.utils;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class GraalPyRunner {

    public static void run(Set<String> classpath, Log log, String... args) throws IOException, InterruptedException {
        String workdir = System.getProperty("exec.workingdir");
        Path java = Paths.get(System.getProperty("java.home"), "bin", "java");
        List<String> cmd = new ArrayList<>();
        cmd.add(java.toString());
        cmd.add("-classpath");
        cmd.add(String.join(File.pathSeparator, classpath));
        cmd.add("com.oracle.graal.python.shell.GraalPythonMain");
        cmd.addAll(List.of(args));
        var pb = new ProcessBuilder(cmd);
        if (workdir != null) {
            pb.directory(new File(workdir));
        }

        log.debug(String.format("Running GraalPy: %s", String.join(" ", cmd)));

        pb.redirectError();
        pb.redirectOutput();
        Process process = pb.start();
        Thread outputReader = new Thread(() -> {
            try (InputStream is = process.getInputStream(); BufferedReader reader = new BufferedReader(new InputStreamReader(is))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    log.subProcessOut(line);
                }
            } catch (IOException e) {
                // Do nothing for now. Probably is not good idea to stop the
                // execution at this moment
                log.subProcessErr(e);
            }
        });
        outputReader.start();

        Thread errorReader = new Thread(() -> {
            try {
                BufferedReader errorBufferedReader = new BufferedReader(new InputStreamReader(process.getErrorStream()));
                String line;
                while ((line = errorBufferedReader.readLine()) != null) {
                    log.subProcessErr(line);
                }
            } catch (IOException e) {
                // Do nothing for now. Probably is not good idea to stop the
                // execution at this moment
                log.subProcessErr(e);
            }
        });
        errorReader.start();

        process.waitFor();
        outputReader.join();
        errorReader.join();

        if (process.exitValue() != 0) {
            throw new RuntimeException(String.format("Running command: '%s' ended with code %d.See the error output above.", String.join(" ", pb.command()), process.exitValue()));
        }
    }

    public static interface Log {

        void subProcessOut(CharSequence var1);

        void subProcessErr(CharSequence var1);

        void subProcessOut(Throwable var1);

        void subProcessErr(Throwable var1);

        void debug(CharSequence var1);

    }
}
