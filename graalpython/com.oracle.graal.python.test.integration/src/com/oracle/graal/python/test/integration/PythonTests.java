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
package com.oracle.graal.python.test.integration;

import static org.hamcrest.CoreMatchers.containsString;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintStream;
import java.util.Collections;
import java.util.Map;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Engine;
import org.graalvm.polyglot.PolyglotException;
import org.graalvm.polyglot.Source;
import org.graalvm.polyglot.Value;
import org.junit.Assert;

import com.oracle.graal.python.test.integration.advanced.BenchmarkTests;

public class PythonTests {
    private static final Source PRINT_EXC_TO_STDERR = Source.create("python", "import traceback; traceback.print_exception");

    static final ByteArrayOutputStream errArray = new ByteArrayOutputStream();
    static final ByteArrayOutputStream outArray = new ByteArrayOutputStream();
    static final PrintStream errStream = new PrintStream(errArray);
    static final PrintStream outStream = new PrintStream(outArray);

    private static final Engine engine = Engine.newBuilder().out(PythonTests.outStream).err(PythonTests.errStream).build();
    private static Context context = null;

    public static Context enterContext(String... newArgs) {
        return enterContext(Collections.emptyMap(), newArgs);
    }

    public static Context enterContext(Map<String, String> options, String[] args) {
        PythonTests.outArray.reset();
        PythonTests.errArray.reset();
        Context prevContext = context;
        context = Context.newBuilder().engine(engine).allowExperimentalOptions(true).allowAllAccess(true).options(options).arguments("python", args).build();
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

    public static void assertPrints(String expected, String code) {
        final ByteArrayOutputStream byteArray = new ByteArrayOutputStream();
        final PrintStream printStream = new PrintStream(byteArray);
        PythonTests.runScript(new String[0], code, printStream, System.err);
        String result = byteArray.toString().replaceAll("\r\n", "\n");
        assertEquals(expected.replaceAll(" at 0x[0-9a-f]*>", " at 0xabcd>"), result.replaceAll(" at 0x[0-9a-f]*>", " at 0xabcd>"));
    }

    public static void assertPrints(String expected, Source code) {
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

    public static Source createSource(String source) {
        return Source.newBuilder("python", source, "Unnamed").buildLiteral();
    }

    public static Source createSource(File path) throws IOException {
        return Source.newBuilder("python", path).build();
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

    public static Value runScript(String[] args, Source source, OutputStream out, OutputStream err) {
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

    public static Source getScriptSource(String name) {
        InputStream is = BenchmarkTests.class.getClassLoader().getResourceAsStream("com/oracle/graal/python/test/integration/scripts/" + name);
        try {
            return Source.newBuilder("python", new InputStreamReader(is), name).build();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
