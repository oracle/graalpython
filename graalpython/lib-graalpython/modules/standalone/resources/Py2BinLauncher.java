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
import java.io.IOException;
import java.util.stream.Stream;

import org.graalvm.nativeimage.ImageInfo;
import org.graalvm.nativeimage.ProcessProperties;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.PolyglotException;
import org.graalvm.polyglot.Source;
import org.graalvm.polyglot.io.IOAccess;

/**
 * A simple launcher for Python. The launcher sets the filesystem up to read the Python core,
 * standard library, a Python virtual environment, and the module to launch from an embedded
 * resource. Any other file system accesses are passed through to the underlying filesystem. The
 * options are set such that most access is allowed and the interpreter works mostly as if run via
 * the launcher. To support the virtual filesystem, however, the POSIX and C API backends are set
 * up to use Java instead of native execution.
 *
 * This class can serve as a skeleton for more elaborate embeddings, an example of a virtual
 * filesystem, as well as showing how to embed Python code into a single native image binary.
 */
public class Py2BinLauncher {
    private static final String HOME_PREFIX = "/{vfs-home-prefix}";
    private static final String VENV_PREFIX = "/{vfs-venv-prefix}";
    private static final String PROJ_PREFIX = "/{vfs-proj-prefix}";

    public static void main(String[] args) throws IOException {
        VirtualFileSystem vfs = new VirtualFileSystem(p -> {
            String s = p.toString();
            return s.endsWith(".so") || s.endsWith(".dylib") || s.endsWith(".pyd") || s.endsWith(".dll");
        });
        IOAccess ioAccess = IOAccess.newBuilder().fileSystem(vfs).allowHostSocketAccess(true).build();
        var builder = Context.newBuilder()
                .allowExperimentalOptions(true)
                .allowAllAccess(true)
                .allowIO(ioAccess)
                .arguments("python", Stream.concat(Stream.of(getProgramName()), Stream.of(args)).toArray(String[]::new))
                .option("python.PosixModuleBackend", "java")
                .option("python.DontWriteBytecodeFlag", "true")
                .option("python.VerboseFlag", System.getenv("PYTHONVERBOSE") != null ? "true" : "false")
                .option("log.python.level", System.getenv("PYTHONVERBOSE") != null ? "FINE" : "SEVERE")
                .option("python.WarnOptions", System.getenv("PYTHONWARNINGS") == null ? "" : System.getenv("PYTHONWARNINGS"))
                .option("python.AlwaysRunExcepthook", "true")
                .option("python.ForceImportSite", "true")
                .option("python.RunViaLauncher", "true")
                .option("python.Executable", vfs.resourcePathToPlatformPath(VENV_PREFIX) + (VirtualFileSystem.isWindows() ? "\\Scripts\\python.cmd" : "/bin/python"))
                .option("python.InputFilePath", vfs.resourcePathToPlatformPath(PROJ_PREFIX))
                .option("python.PythonHome", vfs.resourcePathToPlatformPath(HOME_PREFIX))
                .option("python.CheckHashPycsMode", "never");
        if(ImageInfo.inImageRuntimeCode()) {
            builder.option("engine.WarnInterpreterOnly", "false");
        }
        try (var context = builder.build()) {
            try {
                var src = Source.newBuilder("python", "__graalpython__.run_path()", "<internal>").internal(true).build();
                context.eval(src);
            } catch (PolyglotException e) {
                if (e.isExit()) {
                    System.exit(e.getExitStatus());
                } else {
                    throw e;
                }
            }
        } finally {
            vfs.close();
        }
    }

    private static String getProgramName() {
        if (ImageInfo.inImageRuntimeCode()) {
            if (ProcessProperties.getArgumentVectorBlockSize() > 0) {
                return ProcessProperties.getArgumentVectorProgramName();
            } else {
                return ProcessProperties.getExecutableName();
            }
        }
        return "";
    }
}
