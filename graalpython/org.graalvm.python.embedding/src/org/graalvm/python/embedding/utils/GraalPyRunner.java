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

    public static List<String> run(Set<String> classpath, Log log, String... args) throws IOException, InterruptedException {
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

        List<String> output = new ArrayList<>();
        pb.redirectError();
        pb.redirectOutput();
        Process p = pb.start();
        p.waitFor();
        try (InputStream is = p.getInputStream(); BufferedReader reader = new BufferedReader(new InputStreamReader(is))) {
            String line;
            log.debug("=================== graalpy subprocess output start =================== ");
            while ((line = reader.readLine()) != null) {
                log.debug(line);
                output.add(line);
            }
            log.debug("=================== graalpy subprocess output end ===================== ");
        } catch (IOException e) {
            // Do nothing for now. Probably is not good idea to stop the
            // execution at this moment
        }

        if (p.exitValue() != 0) {
            // if there are some errors, print the error output
            BufferedReader errorBufferedReader = new BufferedReader(new InputStreamReader(p.getErrorStream()));
            String line;
            log.error("=================== graalpy subprocess error output start =================== ");
            while ((line = errorBufferedReader.readLine()) != null) {
                log.error(line);
            }
            log.error("=================== graalpy subprocess error output end ===================== ");
            // and terminate the build process
            throw new RuntimeException(String.format("Running command: '%s' ended with code %d.See the error output above.", String.join(" ", pb.command()), p.exitValue()));
        }
        return output;
    }

    public static interface Log {
        void debug(CharSequence var1);

        void debug(CharSequence var1, Throwable var2);

        void debug(Throwable var1);

        void info(CharSequence var1);

        void info(CharSequence var1, Throwable var2);

        void info(Throwable var1);

        void warn(CharSequence var1);

        void warn(CharSequence var1, Throwable var2);

        void warn(Throwable var1);

        void error(CharSequence var1);

        void error(CharSequence var1, Throwable var2);

        void error(Throwable var1);
    }

}
