/*
 * Copyright (c) 2017, 2021, Oracle and/or its affiliates.
 * Copyright (c) 2013, Regents of the University of California
 *
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification, are
 * permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this list of
 * conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice, this list of
 * conditions and the following disclaimer in the documentation and/or other materials provided
 * with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS
 * OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
 * MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE
 * COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE
 * GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED
 * AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED
 * OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.oracle.graal.python.test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.CodeSource;

public class GraalPythonEnvVars {
    private static final String PROP_HOME = "test.graalpython.home";
    private static final String LIB_GRAALPYTHON = "lib-graalpython";
    private static final String NO_CORE = "Fatal: You need to pass --python.CoreHome because its location could not be discovered.";

    public static String graalpythonHome() {
        try {
            return discoverHomeFromSource();
        } catch (IOException e) {
            graalpythonExit(NO_CORE);
        }
        return null;
    }

    private static void graalpythonExit(String msg) {
        System.err.println("GraalPython unexpected failure: " + msg);
        System.exit(1);
    }

    private static String discoverHomeFromSource() throws IOException {
        /*
         * If the tests are executed via MX then there should be a Java property telling us the
         * Graal Python home.
         */
        String homeProperty = System.getProperty(PROP_HOME);
        if (homeProperty != null) {
            final Path candidate = Paths.get(homeProperty);
            if (isGraalPythonHome(candidate)) {
                return candidate.toRealPath().toString();
            }
        }

        final CodeSource codeSource = GraalPythonEnvVars.class.getProtectionDomain().getCodeSource();
        if (codeSource != null && codeSource.getLocation().getProtocol().equals("file")) {
            final Path codeLocation = Paths.get(codeSource.getLocation().getFile());
            final Path codeDir = codeLocation.getParent();

            // executing from jar file in GraalVM build or distribution
            if (codeDir.getFileName().toString().equals("python")) {
                if (isGraalPythonHome(codeDir)) {
                    // GraalVM build or distribution
                    return codeDir.toFile().getCanonicalPath().toString();
                }
            }

            // executing from binary import
            if (codeDir.endsWith(Paths.get("mx.imports", "binary", "graalpython"))) {
                final Path candidate = codeDir.resolve(Paths.get("mxbuild", "graalpython-zip"));
                if (isGraalPythonHome(candidate)) {
                    return candidate.toFile().getCanonicalPath().toString();
                }
            } else if (codeDir.endsWith(Paths.get("mx.imports", "binary", "graalpython", "mxbuild", "dists"))) {
                // executing from another binary import layout
                final Path candidate = codeDir.resolveSibling(Paths.get("graalpython-zip"));
                if (isGraalPythonHome(candidate)) {
                    return candidate.toFile().getCanonicalPath().toString();
                }
            }
        }
        throw new IOException();
    }

    private static boolean isGraalPythonHome(Path src) {
        return Files.isDirectory(src.resolve(LIB_GRAALPYTHON));
    }
}
