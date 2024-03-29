/*
 * Copyright (c) 2017, 2024, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.shell;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.IntConsumer;
import java.util.function.IntFunction;
import java.util.function.IntSupplier;

import org.graalvm.polyglot.Context;

/**
 * The interface to a source of input/output for the context, which may have different
 * implementations for different contexts.
 */
abstract class ConsoleHandler {

    /**
     * Read a line of input, newline is <b>NOT</b> included in result.
     */
    public final String readLine() {
        return readLine(true);
    }

    public abstract String readLine(boolean prompt);

    public abstract void setPrompt(String prompt);

    public void setContext(@SuppressWarnings("unused") Context context) {
        // ignore by default
    }

    @SuppressWarnings("unused")
    public void setupReader(BooleanSupplier shouldRecord, IntSupplier getSize, Consumer<String> addItem, IntFunction<String> getItem, BiConsumer<Integer, String> setItem, IntConsumer removeItem,
                    Runnable clear, Function<String, List<String>> completer) {
        // ignore by default
    }

    public InputStream createInputStream() {
        return new InputStream() {
            byte[] buffer = null;
            int pos = 0;

            @Override
            public int read() throws IOException {
                if (pos < 0) {
                    pos = 0;
                    return -1;
                } else if (buffer == null) {
                    assert pos == 0;
                    String line = readLine(false);
                    if (line == null) {
                        return -1;
                    }
                    buffer = line.getBytes(StandardCharsets.UTF_8);
                }
                if (pos == buffer.length) {
                    buffer = null;
                    pos = -1;
                    return '\n';
                } else {
                    return buffer[pos++];
                }
            }
        };
    }

    public int getTerminalWidth() {
        // default value
        return 80;
    }

    public int getTerminalHeight() {
        // default value
        return 25;
    }
}
