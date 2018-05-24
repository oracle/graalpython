/*
 * Copyright (c) 2018, Oracle and/or its affiliates.
 *
 * The Universal Permissive License (UPL), Version 1.0
 *
 * Subject to the condition set forth below, permission is hereby granted to any
 * person obtaining a copy of this software, associated documentation and/or data
 * (collectively the "Software"), free of charge and under any and all copyright
 * rights in the Software, and any and all patent rights owned or freely
 * licensable by each licensor hereunder covering either (i) the unmodified
 * Software as contributed to or provided by such licensor, or (ii) the Larger
 * Works (as defined below), to deal in both
 *
 * (a) the Software, and
 * (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
 *     one is included with the Software (each a "Larger Work" to which the
 *     Software is contributed by such licensors),
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
package com.oracle.graal.python.test.python;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.graalvm.polyglot.PolyglotException;
import org.junit.Assert;
import org.junit.Test;

import com.oracle.graal.python.test.GraalPythonEnvVars;
import com.oracle.graal.python.test.PythonTests;

public class PyTests {
    @Test
    public void runPyTests() {
        Path script = Paths.get(GraalPythonEnvVars.graalpythonHome(), "com.oracle.graal.python.test", "src", "graalpytest.py");
        Path folder = Paths.get(GraalPythonEnvVars.graalpythonHome(), "com.oracle.graal.python.test", "src", "tests");
        final ByteArrayOutputStream byteArray = new ByteArrayOutputStream();
        final PrintStream printStream = new PrintStream(byteArray);
        try {
            String[] cmd = {script.toString(), "-v", folder.toString()};
            PythonTests.runScript(cmd, script.toFile(), printStream, printStream);
        } catch (PolyglotException e) {
            System.out.println(byteArray.toString());
            if (e.isExit()) {
                Assert.fail("unexpected exit from PyTests with exit code " + e.getExitStatus());
            } else {
                e.printStackTrace();
                Assert.fail("unexpected exception in PyTests");
            }
        }
        String output = byteArray.toString();
        System.out.println(output);
        Pattern statsPattern = Pattern.compile("Ran (?<numTests>\\d+) tests \\((?<passes>\\d+) passes, (?<failures>\\d+) failures\\)\\s*");
        Matcher matcher = statsPattern.matcher(output);
        if (matcher.find()) {
            Integer passes = Integer.valueOf(matcher.group("passes"));
            Integer failures = Integer.valueOf(matcher.group("failures"));

            Assert.assertTrue(passes > 0);
            Assert.assertTrue(failures == 0);
        } else {
            Assert.fail(String.format("could not find graalpytest test stats line in output: %s", output));
        }
    }
}
