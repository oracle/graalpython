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

package ${package};

#set( $symbol_pound = '#' )
#set( $symbol_dollar = '$' )
#set( $symbol_escape = '\' )
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.PolyglotAccess;
import org.graalvm.polyglot.PolyglotException;
import org.graalvm.polyglot.Source;
import org.graalvm.polyglot.Value;
import org.graalvm.polyglot.io.IOAccess;
import java.io.IOException;
import org.graalvm.python.embedding.utils.VirtualFileSystem;

public class GraalPy {
    private static final String VENV_PREFIX = "/vfs/venv";
    private static final String HOME_PREFIX = "/vfs/home";
    private static final String PROJ_PREFIX = "/vfs/proj";
    
    private static final String PYTHON = "python";

    public static Context getContext() {
        VirtualFileSystem vfs = VirtualFileSystem.newBuilder()
            .extractFilter(p -> {
                String s = p.toString();
                return s.endsWith(".ttf");
            })
            .build();
        Context context = Context.newBuilder()
            // set true to allow experimental options
            .allowExperimentalOptions(false)
            // setting false will deny all privileges unless configured below
            .allowAllAccess(false)
            // allows python to access the java language
            .allowHostAccess(true)
            // allow access to the virtual and the host filesystem, as well as sockets
            .allowIO(IOAccess.newBuilder()
                            .allowHostSocketAccess(true)
                            .fileSystem(vfs)
                            .build())
            // allow creating python threads
            .allowCreateThread(true)
            // allow running Python native extensions
            .allowNativeAccess(true)
            // allow exporting Python values to polyglot bindings and accessing Java from Python
            .allowPolyglotAccess(PolyglotAccess.ALL)
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
            // print Python exceptions directly
            .option("python.AlwaysRunExcepthook", "true")
            // Force to automatically import site.py module, to make Python packages available
            .option("python.ForceImportSite", "true")
            // The sys.executable path, a virtual path that is used by the interpreter to discover packages
            .option("python.Executable", vfs.resourcePathToPlatformPath(VENV_PREFIX) + (VirtualFileSystem.isWindows() ? "${symbol_escape}${symbol_escape}Scripts${symbol_escape}${symbol_escape}python.exe" : "/bin/python"))
            // Set the python home to be read from the embedded resources
            .option("python.PythonHome", vfs.resourcePathToPlatformPath(HOME_PREFIX))
            // Do not warn if running without JIT. This can be desirable for short running scripts
            // to reduce memory footprint.
            .option("engine.WarnInterpreterOnly", "false")
            // Set python path to point to sources stored in src/main/resources/vfs/proj
            .option("python.PythonPath", vfs.resourcePathToPlatformPath(PROJ_PREFIX))
            .build();
        return context;
    }

    public static void main(String[] args) {
        try (Context context = getContext()) {
            Source source;
            try {
                source = Source.newBuilder(PYTHON, "import hello", "<internal>").internal(true).build();
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
        }
    }
}