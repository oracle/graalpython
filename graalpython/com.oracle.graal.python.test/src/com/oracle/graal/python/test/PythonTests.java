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

import static org.hamcrest.CoreMatchers.containsString;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.lang.management.ManagementFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.Map;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Engine;
import org.graalvm.polyglot.PolyglotException;
import org.graalvm.polyglot.Source;
import org.graalvm.polyglot.Value;
import org.junit.Assert;

import com.oracle.truffle.api.strings.TruffleString;

public class PythonTests {
    private static final Source PRINT_EXC_TO_STDERR = Source.create("python", "import traceback; traceback.print_exception");

    static final ByteArrayOutputStream errArray = new ByteArrayOutputStream();
    static final ByteArrayOutputStream outArray = new ByteArrayOutputStream();
    static final PrintStream errStream = new PrintStream(errArray);
    static final PrintStream outStream = new PrintStream(outArray);

    private static final Engine engine = Engine.newBuilder().out(PythonTests.outStream).err(PythonTests.errStream).build();
    private static Context context = null;

    protected static final String executable;
    static {
        StringBuilder sb = new StringBuilder();
        sb.append(System.getProperty("java.home")).append(File.separator).append("bin").append(File.separator).append("java");
        for (String arg : ManagementFactory.getRuntimeMXBean().getInputArguments()) {
            sb.append(' ').append(arg);
        }
        sb.append(" -classpath ");
        sb.append(System.getProperty("java.class.path"));
        sb.append(" com.oracle.graal.python.shell.GraalPythonMain");
        executable = sb.toString();
    }

    public static Context enterContext(String... newArgs) {
        return enterContext(Collections.emptyMap(), newArgs);
    }

    public static Context enterContext(Map<String, String> options, String[] args) {
        PythonTests.outArray.reset();
        PythonTests.errArray.reset();
        Context prevContext = context;
        context = Context.newBuilder().engine(engine).allowExperimentalOptions(true).allowAllAccess(true).options(options).arguments("python", args).option("python.Executable", executable).build();
        context.initialize("python");
        assert prevContext == null;
        context.enter();
        return context;
    }

    private static void closeContext(Context ctxt) {
        try {
            ctxt.leave();
        } catch (RuntimeException e) {
        }
        ctxt.close();
    }

    public static void closeContext() {
        if (context != null) {
            closeContext(context);
            context = null;
        }
    }

    public static void assertBenchNoError(Path scriptName, String[] args) {
        final ByteArrayOutputStream byteArrayErr = new ByteArrayOutputStream();
        final ByteArrayOutputStream byteArrayOut = new ByteArrayOutputStream();
        final PrintStream printErrStream = new PrintStream(byteArrayErr);
        final PrintStream printOutStream = new PrintStream(byteArrayOut);
        File source = getBenchFile(scriptName);
        if (args == null) {
            PythonTests.runScript(new String[]{source.toString()}, source, printOutStream, printErrStream);
        } else {
            args[0] = source.toString();
            PythonTests.runScript(args, source, printOutStream, printErrStream);
        }

        String err = byteArrayErr.toString().replaceAll("\r\n", "\n");
        String result = byteArrayOut.toString().replaceAll("\r\n", "\n");
        System.out.println(scriptName.toString() + "\n" + result + "\n");
        assertEquals("", err);
        assertNotEquals("", result);
    }

    public static void assertLastLineError(String expected, String code) {
        final ByteArrayOutputStream byteArray = new ByteArrayOutputStream();
        final PrintStream printStream = new PrintStream(byteArray);
        String source = code;
        PythonTests.runThrowableScript(new String[0], source, System.out, printStream);
        String[] output = byteArray.toString().split("\n");
        // ignore the traceback
        assertEquals(expected.trim(), output[output.length - 1].trim());
    }

    public static void assertLastLineErrorContains(String expected, String code) {
        final ByteArrayOutputStream byteArray = new ByteArrayOutputStream();
        final PrintStream printStream = new PrintStream(byteArray);
        String source = code;
        PythonTests.runThrowableScript(new String[0], source, System.out, printStream);
        String[] output = byteArray.toString().split("\n");
        // ignore the traceback
        Assert.assertThat(output[output.length - 1], containsString(expected.trim()));
    }

    public static void assertPrintContains(String expected, String code) {
        final ByteArrayOutputStream byteArray = new ByteArrayOutputStream();
        final PrintStream printStream = new PrintStream(byteArray);
        String source = code;
        PythonTests.runScript(new String[0], source, printStream, System.err);
        String result = byteArray.toString().replaceAll("\r\n", "\n");
        assertTrue(result.contains(expected));
    }

    public static void assertPrints(String expected, Path scriptName) {
        final ByteArrayOutputStream byteArray = new ByteArrayOutputStream();
        final PrintStream printStream = new PrintStream(byteArray);

        File scriptFile = getTestFile(scriptName);
        PythonTests.runScript(new String[]{scriptFile.toString()}, scriptFile, printStream, System.err);
        String result = byteArray.toString().replaceAll("\r\n", "\n");
        assertEquals(expected, result);
    }

    public static void assertPrints(String expected, String code) {
        final ByteArrayOutputStream byteArray = new ByteArrayOutputStream();
        final PrintStream printStream = new PrintStream(byteArray);
        PythonTests.runScript(new String[0], code, printStream, System.err);
        String result = byteArray.toString().replaceAll("\r\n", "\n");
        assertEquals(expected.replaceAll(" at 0x[0-9a-f]*>", " at 0xabcd>"), result.replaceAll(" at 0x[0-9a-f]*>", " at 0xabcd>"));
    }

    public static void assertPrints(String expected, org.graalvm.polyglot.Source code) {
        final ByteArrayOutputStream byteArray = new ByteArrayOutputStream();
        final PrintStream printStream = new PrintStream(byteArray);
        PythonTests.runScript(new String[0], code, printStream, System.err);
        String result = byteArray.toString().replaceAll("\r\n", "\n");
        assertEquals(expected.replaceAll(" at 0x[0-9a-f]*>", " at 0xabcd>"), result.replaceAll(" at 0x[0-9a-f]*>", " at 0xabcd>"));
    }

    public static Value eval(String code) {
        final ByteArrayOutputStream byteArray = new ByteArrayOutputStream();
        final PrintStream printStream = new PrintStream(byteArray);
        return PythonTests.runScript(new String[0], code, printStream, System.err);
    }

    static void flush(OutputStream out, OutputStream err) {
        PythonTests.outStream.flush();
        PythonTests.errStream.flush();
        try {
            out.write(PythonTests.outArray.toByteArray());
            err.write(PythonTests.errArray.toByteArray());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static File getBenchFile(Path filename) {
        Path path = Paths.get(GraalPythonEnvVars.graalPythonTestsHome(), "com.oracle.graal.python.benchmarks", "python");
        if (!Files.isDirectory(path)) {
            throw new RuntimeException("Unable to locate com.oracle.graal.python.benchmarks/python/");
        }

        Path fullPath = Paths.get(path.toString(), filename.toString());
        if (!Files.isReadable(fullPath)) {
            throw new IllegalStateException("Unable to locate " + path + " (benchmarks or micro) " + filename.toString());
        }

        File file = new File(fullPath.toString());
        return file;
    }

    public static File getTestFile(Path filename) {
        Path path = Paths.get(GraalPythonEnvVars.graalPythonTestsHome(), "com.oracle.graal.python.test", "src", "tests", filename.toString());
        if (Files.isReadable(path)) {
            return new File(path.toString());
        } else {
            throw new RuntimeException("Unable to locate " + path);
        }
    }

    public static org.graalvm.polyglot.Source createSource(String source) {
        return org.graalvm.polyglot.Source.newBuilder("python", source, "Unnamed").buildLiteral();
    }

    public static org.graalvm.polyglot.Source createSource(File path) throws IOException {
        return org.graalvm.polyglot.Source.newBuilder("python", path).build();
    }

    public static Value runScript(String[] args, File path, OutputStream out, OutputStream err) {
        try {
            enterContext(args);
            return context.eval(createSource(path));
        } catch (IOException e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        } finally {
            flush(out, err);
            closeContext();
        }
    }

    public static Value runScript(Map<String, String> options, String[] args, String source, OutputStream out, OutputStream err) {
        try {
            enterContext(options, args);
            return context.eval(createSource(source));
        } finally {
            flush(out, err);
            closeContext();
        }
    }

    public static Value runScript(String[] args, String source, OutputStream out, OutputStream err) {
        try {
            enterContext(args);
            return context.eval(createSource(source));
        } finally {
            flush(out, err);
            closeContext();
        }
    }

    public static Value runScript(String[] args, org.graalvm.polyglot.Source source, OutputStream out, OutputStream err) {
        try {
            enterContext(args);
            return context.eval(source);
        } finally {
            flush(out, err);
            closeContext();
        }
    }

    public static Value runScript(String[] args, String source, OutputStream out, OutputStream err, Runnable cb) {
        return runScript(Collections.emptyMap(), args, source, out, err, cb);
    }

    public static Value runScript(Map<String, String> options, String[] args, String source, OutputStream out, OutputStream err, Runnable cb) {
        try {
            enterContext(options, args);
            return context.eval(createSource(source));
        } finally {
            cb.run();
            flush(out, err);
            closeContext();
        }
    }

    public static void runThrowableScript(String[] args, String source, OutputStream out, OutputStream err) {
        try {
            enterContext(args);
            context.eval(createSource(source));
        } catch (PolyglotException t) {
            try {
                Value printExc = context.eval(PRINT_EXC_TO_STDERR);
                printExc.execute(t.getGuestObject());
            } catch (Throwable ex) {
                throw new RuntimeException("Error while printing the PolyglotException message to stderr.", ex);
            }
        } finally {
            flush(out, err);
            closeContext();
        }
    }

    public static TruffleString ts(String s) {
        return TruffleString.fromJavaStringUncached(s, TruffleString.Encoding.UTF_8);
    }
}
