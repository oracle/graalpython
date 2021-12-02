/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.builtins.modules.csv;

import static com.oracle.graal.python.builtins.modules.csv.CSVModuleBuiltins.NOT_SET;
import static com.oracle.graal.python.builtins.modules.csv.CSVModuleBuiltins.NOT_SET_CODEPOINT;

import com.oracle.graal.python.builtins.objects.object.PythonBuiltinObject;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.object.Shape;

public final class CSVDialect extends PythonBuiltinObject {
    String delimiter; /* field separator */
    boolean doubleQuote; /* is " represented by ""? */
    String escapeChar; /* escape character */
    String lineTerminator; /* string to write between records */
    QuoteStyle quoting; /* style of quoting to write */
    String quoteChar; /* quote character */
    boolean skipInitialSpace; /* ignore spaces following delimiter? */
    boolean strict; /* raise exception on bad CSV */

    int delimiterCodePoint; /* code point representation for handling utf-32 delimiters */
    int escapeCharCodePoint; /* code point representation for handling utf-32 escape chars */
    int quoteCharCodePoint; /* code point representation for handling utf-32 quote chars */
    int[] lineTerminatorCodePoints; /*
                                     * code point representation for handling utf-32 chars in line
                                     * terminator
                                     */

    public CSVDialect(Object cls, Shape instanceShape) {
        super(cls, instanceShape);
    }

    public CSVDialect(Object cls, Shape instanceShape, String delimiter, boolean doubleQuote, String escapeChar,
                    String lineTerminator, String quoteChar, QuoteStyle quoting, boolean skipInitialSpace,
                    boolean strict) {
        super(cls, instanceShape);
        this.delimiter = delimiter;
        this.doubleQuote = doubleQuote;
        this.escapeChar = escapeChar;
        this.lineTerminator = lineTerminator;
        this.quoteChar = quoteChar;
        this.quoting = quoting;
        this.skipInitialSpace = skipInitialSpace;
        this.strict = strict;

        this.delimiterCodePoint = this.delimiter.codePointAt(0); // delimiter cannot be NOT_SET
        this.escapeCharCodePoint = this.escapeChar.equals(NOT_SET) ? NOT_SET_CODEPOINT : this.escapeChar.codePointAt(0);
        this.quoteCharCodePoint = this.quoteChar.equals(NOT_SET) ? NOT_SET_CODEPOINT : this.quoteChar.codePointAt(0);
        this.lineTerminatorCodePoints = strToCodePointArray(this.lineTerminator);
    }

    @TruffleBoundary
    private static int[] strToCodePointArray(String str) {
        final int strLen = str.length();
        final int codePointCount = str.codePointCount(0, strLen);
        int[] codePoints = new int[codePointCount];

        for (int offset = 0, index = 0; offset < strLen; index++) {
            final int c = str.codePointAt(offset);
            codePoints[index] = c;
            offset += Character.charCount(c);
        }
        return codePoints;
    }
}
