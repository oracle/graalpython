/*
 * Copyright (c) 2020, 2020, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.processor;

import java.io.IOException;
import java.io.Writer;
import java.util.Arrays;

public class CodeWriter implements AutoCloseable {
    private final Writer writer;
    private int indent;

    public CodeWriter(Writer writer) {
        this.writer = writer;
    }

    public void writeLn(String fmt, Object... args) throws IOException {
        writeIndent();
        writer.write(String.format(fmt, args));
        writer.write('\n');
    }

    public void writeLn() throws IOException {
        writeIndent();
        writer.write('\n');
    }

    public LineBuilder startLn() throws IOException {
        writeIndent();
        return new LineBuilder();
    }

    public Block newIndent() {
        return new Block();
    }

    private void writeIndent() throws IOException {
        for (int i = 0; i < indent; i++) {
            writer.write("    ");
        }
    }

    public final class LineBuilder {
        public LineBuilder write(String fmt, Object... args) throws IOException {
            writer.write(String.format(fmt, args));
            return this;
        }

        public LineBuilder writeEach(int[] items, String delimiter, String fmt) throws IOException {
            String[] strs = Arrays.stream(items).mapToObj(x -> String.format(fmt, x)).toArray(String[]::new);
            return write(String.join(delimiter, strs));
        }

        public void endLn(String code) throws IOException {
            write(code).write("\n");
        }
    }

    public final class Block implements AutoCloseable {
        public Block() {
            indent++;
        }

        @Override
        public void close() {
            indent--;
        }
    }

    @Override
    public void close() throws IOException {
        writer.close();
    }
}
