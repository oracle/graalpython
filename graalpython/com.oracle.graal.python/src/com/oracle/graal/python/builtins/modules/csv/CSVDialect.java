/*
 * Copyright (c) 2021, 2022, Oracle and/or its affiliates. All rights reserved.
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

import static com.oracle.graal.python.builtins.modules.csv.CSVModuleBuiltins.NOT_SET_CODEPOINT;
import static com.oracle.graal.python.util.PythonUtils.TS_ENCODING;

import com.oracle.graal.python.builtins.objects.object.PythonBuiltinObject;
import com.oracle.truffle.api.object.Shape;
import com.oracle.truffle.api.strings.TruffleString;

public final class CSVDialect extends PythonBuiltinObject {
    TruffleString delimiter; /* field separator */
    boolean doubleQuote; /* is " represented by ""? */
    TruffleString escapeChar; /* escape character */
    TruffleString lineTerminator; /* string to write between records */
    QuoteStyle quoting; /* style of quoting to write */
    TruffleString quoteChar; /* quote character */
    boolean skipInitialSpace; /* ignore spaces following delimiter? */
    boolean strict; /* raise exception on bad CSV */

    int delimiterCodePoint; /* code point representation for handling utf-32 delimiters */
    int escapeCharCodePoint; /* code point representation for handling utf-32 escape chars */
    int quoteCharCodePoint; /* code point representation for handling utf-32 quote chars */

    public CSVDialect(Object cls, Shape instanceShape, TruffleString delimiter, int delimiterCodePoint, boolean doubleQuote, TruffleString escapeChar, int escapeCharCodePoint,
                    TruffleString lineTerminator, TruffleString quoteChar, int quoteCharCodePoint, QuoteStyle quoting, boolean skipInitialSpace, boolean strict) {
        super(cls, instanceShape);
        this.delimiter = delimiter;
        this.doubleQuote = doubleQuote;
        this.escapeChar = escapeChar;
        this.lineTerminator = lineTerminator;
        this.quoteChar = quoteChar;
        this.quoting = quoting;
        this.skipInitialSpace = skipInitialSpace;
        this.strict = strict;

        assert delimiterCodePoint == delimiter.codePointAtIndexUncached(0, TS_ENCODING);
        assert escapeCharCodePoint == NOT_SET_CODEPOINT || escapeCharCodePoint == escapeChar.codePointAtIndexUncached(0, TS_ENCODING);
        assert quoteCharCodePoint == NOT_SET_CODEPOINT || quoteCharCodePoint == quoteChar.codePointAtIndexUncached(0, TS_ENCODING);

        this.delimiterCodePoint = delimiterCodePoint;
        this.escapeCharCodePoint = escapeCharCodePoint;
        this.quoteCharCodePoint = quoteCharCodePoint;
    }
}
