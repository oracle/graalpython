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
    private static final String HOME_PREFIX = VirtualFileSystem.VFS_PREFIX + "/home";
    private static final String PROJ_PREFIX = VirtualFileSystem.VFS_PREFIX + "/proj";
    private static final String VENV_PREFIX = VirtualFileSystem.VFS_PREFIX + "/venv";

    private static String PYTHON = "python";
    
    public static void main(String[] args) throws IOException {
        Builder builder = Context.newBuilder()
            .allowExperimentalOptions(true)
            .allowAllAccess(true)
            .allowIO(true)
            .fileSystem(new VirtualFileSystem())
            .option("python.PosixModuleBackend", "java")
            .option("python.DontWriteBytecodeFlag", "true")
            .option("python.VerboseFlag", System.getenv("PYTHONVERBOSE") != null ? "true" : "false")
            .option("log.python.level", System.getenv("PYTHONVERBOSE") != null ? "FINE" : "SEVERE")
            .option("python.WarnOptions", System.getenv("PYTHONWARNINGS") == null ? "" : System.getenv("PYTHONWARNINGS"))
            .option("python.AlwaysRunExcepthook", "false")
            .option("python.ForceImportSite", "true")
            .option("python.RunViaLauncher", "false")
            .option("python.Executable", VENV_PREFIX + "/bin/python")
            .option("python.InputFilePath", PROJ_PREFIX)            
            .option("python.CheckHashPycsMode", "never");
        if(ImageInfo.inImageRuntimeCode()) {
            builder.option("engine.WarnInterpreterOnly", "false")
                   .option("python.PythonHome", HOME_PREFIX);
        }
        Context context = builder.build();
                
        try {
            Source source;
            try {
                source = Source.newBuilder(PYTHON, "__graalpython__.run_path()", "<internal>").internal(true).build();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            context.eval(source);
            Value pyHelloClass = context.getPolyglotBindings().getMember("PyHello");
            Value pyHello = pyHelloClass.newInstance();
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
