/*
 * Copyright (c) 2017, 2023, Oracle and/or its affiliates.
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
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Locale;

public class GraalPythonEnvVars {
    public static final boolean IS_WINDOWS = System.getProperty("os.name").toLowerCase(Locale.ROOT).contains("windows");

    private static final String PROP_TESTS_HOME = "test.graalpython.home";
    private static final String NO_TESTS_HOME = "Fatal: You need to set Java property '%s' to a directory that " +
                    "contains additional test files, e.g., to the root of the GraalPython source tree. If the tests are " +
                    "executed via mx, then mx should set this property automatically as specified in suite.py";

    public static String graalPythonTestsHome() {
        try {
            return getGraalPythonTestsHome();
        } catch (IOException e) {
            throw new RuntimeException(String.format(NO_TESTS_HOME, PROP_TESTS_HOME));
        }
    }

    private static String getGraalPythonTestsHome() throws IOException {
        /*
         * There should be a Java property telling us where to find additional files needed for the
         * tests. If the tests are executed via MX then the Java property should be set
         * automatically (see the test project definition in suite.py)
         */
        String homeProperty = System.getProperty(PROP_TESTS_HOME);
        if (homeProperty != null) {
            final Path candidate = Paths.get(homeProperty);
            return candidate.toRealPath().toString();
        }
        throw new IOException();
    }
}
