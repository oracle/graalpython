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
package com.mycompany.javapython;

import java.io.IOException;
import org.graalvm.nativeimage.ImageInfo;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Context.Builder;
import org.graalvm.polyglot.PolyglotException;
import org.graalvm.polyglot.Source;
import org.graalvm.polyglot.Value;

public class Main {
    private static final String HOME_PREFIX = "/{vfs-home-prefix}";
    private static final String VENV_PREFIX = "/{vfs-venv-prefix}";
    private static final String PROJ_PREFIX = "/{vfs-proj-prefix}";

    private static String PYTHON = "python";

    public static void main(String[] args) throws IOException {
        VirtualFileSystem vfs = new VirtualFileSystem(p -> {
            String s = p.toString();
            return s.endsWith(".so") || s.endsWith(".dylib") || s.endsWith(".pyd") || s.endsWith(".dll");
        });
        Builder builder = Context.newBuilder()
            // set true to allow experimental options
            .allowExperimentalOptions(true)
            // allow all privileges
            .allowAllAccess(true)
            // alow access to host IO
            .allowIO(true)
            // install a truffle FileSystem
            .fileSystem(vfs)
            // choose the backend for the POSIX module
            .option("python.PosixModuleBackend", "java")
            // equivalent to the Python -B flag
            .option("python.DontWriteBytecodeFlag", "true")
            // equivalent to the Python -v flag
            .option("python.VerboseFlag", System.getenv("PYTHONVERBOSE") != null ? "true" : "false")
            // log level
            .option("log.python.level", System.getenv("PYTHONVERBOSE") != null ? "FINE" : "SEVERE")
            // equivalent to setting the PYTHONWARNINGS environment variable
            .option("python.WarnOptions", System.getenv("PYTHONWARNINGS") == null ? "" : System.getenv("PYTHONWARNINGS"))
            // print exceptions directly
            .option("python.AlwaysRunExcepthook", "true")
            // Force to automatically import site.py module
            .option("python.ForceImportSite", "true")
            // The sys.executable path
            .option("python.Executable", vfs.resourcePathToPlatformPath(VENV_PREFIX) + (VirtualFileSystem.isWindows() ? "\\Scripts\\python.cmd" : "/bin/python"))
            // Used by the launcher to pass the path to be executed.
            // VirtualFilesystem will take care, that at runtime this will be
            // the python sources stored in src/main/resources/{vfs-proj-prefix}
            .option("python.InputFilePath", vfs.resourcePathToPlatformPath(PROJ_PREFIX))
            // Value of the --check-hash-based-pycs command line option
            .option("python.CheckHashPycsMode", "never")
            // Do not warn if running without JIT. This can be desirable for short running scripts
            // to reduce memory footprint.
            .option("engine.WarnInterpreterOnly", "false");
        if(ImageInfo.inImageRuntimeCode()) {
            // Set the home of Python. Equivalent of GRAAL_PYTHONHOME env variable
            builder.option("python.PythonHome", vfs.resourcePathToPlatformPath(HOME_PREFIX));
        }
        Context context = builder.build();

        try {
            Source source;
            try {
                source = Source.newBuilder(PYTHON, "__graalpython__.run_path()", "<internal>").internal(true).build();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            // eval the snipet __graalpython__.run_path() which executes what the option python.InputFilePath points to
            context.eval(source);

            // retrieve the python PyHello class
            Value pyHelloClass = context.getPolyglotBindings().getMember("PyHello");
            Value pyHello = pyHelloClass.newInstance();
            // and cast it to the Hello interface which matches PyHello
            Hello hello = pyHello.as(Hello.class);
            hello.hello("java");

        } catch (PolyglotException e) {
            if (e.isExit()) {
                System.exit(e.getExitStatus());
            } else {
                throw e;
            }
        } finally {
            vfs.close();
        }
    }

}
