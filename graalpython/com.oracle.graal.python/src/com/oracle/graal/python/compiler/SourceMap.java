/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.compiler;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.Arrays;

/**
 * Helper for encapsulating the encoding and decoding of source line and column information for
 * bytecode.
 */
public class SourceMap {
    public final int[] startLineMap;
    public final int[] endLineMap;
    public final int[] startColumnMap;
    public final int[] endColumnMap;

    private static final byte EXTENDED_NUM = -128;
    private static final byte NEXT_LINE = -127;
    private static final byte NEXT_LINES = -126;
    private static final byte MIN_NUM = -125;
    private static final byte MULTIPLIER_NEGATIVE = MIN_NUM + 1;
    private static final byte MAX_NUM = 127;
    private static final byte MULTIPLIER_POSITIVE = MAX_NUM;

    public SourceMap(byte[] code, byte[] srcTable, int startLine, int startColumn) {
        startLineMap = new int[code.length];
        endLineMap = new int[code.length];
        startColumnMap = new int[code.length];
        endColumnMap = new int[code.length];
        ByteArrayInputStream stream = new ByteArrayInputStream(srcTable);
        int[] startLineAndColumn = new int[]{startLine, startColumn};
        for (int bci = 0; bci < code.length;) {
            OpCodes op = OpCodes.fromOpCode(code[bci]);
            readLineAndColumn(stream, startLineAndColumn);
            int[] endLineAndColumn = Arrays.copyOf(startLineAndColumn, 2);
            readLineAndColumn(stream, endLineAndColumn);
            Arrays.fill(startLineMap, bci, bci + op.length(), startLineAndColumn[0]);
            Arrays.fill(startColumnMap, bci, bci + op.length(), startLineAndColumn[1]);
            Arrays.fill(endLineMap, bci, bci + op.length(), endLineAndColumn[0]);
            Arrays.fill(endColumnMap, bci, bci + op.length(), endLineAndColumn[1]);
            bci += op.length();
        }
    }

    private void readLineAndColumn(ByteArrayInputStream stream, int[] pair) {
        stream.mark(1);
        byte value = (byte) stream.read();
        if (value == NEXT_LINE) {
            pair[0]++;
            pair[1] = 0;
        } else if (value == NEXT_LINES) {
            pair[0] += readNum(stream);
            pair[1] = 0;
        } else {
            stream.reset();
        }
        pair[1] += readNum(stream);
    }

    private static int readNum(ByteArrayInputStream offsets) {
        int extensions = 0;
        while (true) {
            byte value = (byte) offsets.read();
            if (value == EXTENDED_NUM) {
                extensions++;
            } else if (value < 0) {
                return extensions * MULTIPLIER_NEGATIVE + value;
            } else {
                return extensions * MULTIPLIER_POSITIVE + value;
            }
        }
    }

    /**
     * Encoding: startLine=[NEXT_LINE|NEXT_LINES num] startColumn=num endLine=[NEXT_LINE|NEXT_LINES
     * num] endColumn=num
     */
    public static class Builder {
        private final ByteArrayOutputStream stream = new ByteArrayOutputStream();
        private int lastLine;
        private int lastColumn;

        public Builder(int startLine, int startColumn) {
            this.lastLine = startLine;
            this.lastColumn = startColumn;
        }

        private void writeNum(int offset) {
            while (offset > MAX_NUM) {
                stream.write(EXTENDED_NUM);
                offset -= MULTIPLIER_POSITIVE;
            }
            while (offset < MIN_NUM) {
                stream.write(EXTENDED_NUM);
                offset -= MULTIPLIER_NEGATIVE;
            }
            stream.write(offset);
        }

        private void writeLine(int lineDelta) {
            if (lineDelta == 1) {
                stream.write(NEXT_LINE);
            } else {
                stream.write(NEXT_LINES);
                writeNum(lineDelta);
            }
        }

        private void writeDeltas(int baseColumn, int column, int lineDelta) {
            if (lineDelta != 0) {
                writeLine(lineDelta);
                writeNum(column);
            } else {
                writeNum(column - baseColumn);
            }
        }

        public void appendLocation(int startLine, int startColumn, int endLine, int endColumn) {
            // assert startLine >= 0 && startColumn >= 0 && endLine >= startLine && endColumn >=
            // startColumn;
            int lineDelta = startLine - lastLine;
            int lineSpan = endLine - startLine;
            writeDeltas(lastColumn, startColumn, lineDelta);
            writeDeltas(startColumn, endColumn, lineSpan);
            lastLine = startLine;
            lastColumn = startColumn;
        }

        public byte[] build() {
            return stream.toByteArray();
        }
    }
}
