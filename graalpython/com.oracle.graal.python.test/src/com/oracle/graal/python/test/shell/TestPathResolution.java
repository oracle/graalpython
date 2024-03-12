/*
 * Copyright (c) 2024, 2024, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.test.shell;

import java.lang.reflect.Method;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.function.Function;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import com.oracle.graal.python.shell.GraalPythonMain;

public class TestPathResolution {
    private Method calculateProgramFullPathMethod;

    @Before
    public void setup() throws NoSuchMethodException {
        // Use reflection to avoid exposing the method in public API
        calculateProgramFullPathMethod = GraalPythonMain.class.getDeclaredMethod("calculateProgramFullPath", String.class, Function.class, String.class);
        calculateProgramFullPathMethod.setAccessible(true);
    }

    public String calculateProgramFullPath(String executable, Function<Path, Boolean> isExecutable, String path) {
        try {
            return (String) calculateProgramFullPathMethod.invoke(new GraalPythonMain(), executable, isExecutable, path);
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    public void testProgramNameResolutionFullPath() {
        Assert.assertEquals("/absolute/path/graalpy",
                        calculateProgramFullPath("/absolute/path/graalpy", path -> true, null));

        Assert.assertEquals(Paths.get("./blah/graalpy").toAbsolutePath().normalize().toString(),
                        calculateProgramFullPath("./blah/graalpy", path -> true, null));
    }

    @Test
    public void testProgramNameResolutionEndOfPath() {
        Assert.assertEquals("/last/in/path/graalpy",
                        calculateProgramFullPath("graalpy", path -> path.startsWith("/last"), "/first/in/path:/last/in/path/"));

        // trailing slash
        Assert.assertEquals("/last/in/path/graalpy",
                        calculateProgramFullPath("graalpy", path -> path.startsWith("/last"), "/first/in/path:/last/in/path/"));

        // trailing colon
        Assert.assertEquals("/last/in/path/graalpy",
                        calculateProgramFullPath("graalpy", path -> path.startsWith("/last"), "/first/in/path:/last/in/path:"));

        // leading colon
        Assert.assertEquals("/last/in/path/graalpy",
                        calculateProgramFullPath("graalpy", path -> path.startsWith("/last"), ":/first/in/path:/last/in/path"));
    }

    @Test
    public void testProgramNameResolutionStartOfPath() {
        Assert.assertEquals("/first/in/path/graalpy",
                        calculateProgramFullPath("graalpy", path -> path.startsWith("/first"), "/first/in/path:/last/in/path/"));

        // trailing slash
        Assert.assertEquals("/first/in/path/graalpy",
                        calculateProgramFullPath("graalpy", path -> path.startsWith("/first"), "/first/in/path/:/last/in/path/"));

        // leading colon
        Assert.assertEquals("/first/in/path/graalpy",
                        calculateProgramFullPath("graalpy", path -> path.startsWith("/first"), ":/first/in/path:/last/in/path"));
    }

    @Test
    public void testProgramNameResolutionInPath() {
        Assert.assertEquals("/second/in/path/graalpy",
                        calculateProgramFullPath("graalpy", path -> path.startsWith("/second"), "/first/in/path:/second/in/path/:/last/in/path"));

        // with spaces
        Assert.assertEquals("/next/in/path/graalpy",
                        calculateProgramFullPath("graalpy", path -> path.startsWith("/next"), "/first/in/path:/path with spaces:/next/in/path/:/last/in/path"));

        // with spaces as the result
        Assert.assertEquals("/path with spaces/graalpy",
                        calculateProgramFullPath("graalpy", path -> path.startsWith("/path with spaces"), "/first/in/path:/path with spaces:/next/in/path/:/last/in/path"));

        // single path element
        Assert.assertEquals("/single/in/path/graalpy",
                        calculateProgramFullPath("graalpy", path -> path.startsWith("/single"), "/single/in/path"));
    }
}
