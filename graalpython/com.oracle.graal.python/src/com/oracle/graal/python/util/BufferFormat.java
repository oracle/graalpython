/*
 * Copyright (c) 2020, 2023, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.util;

import static com.oracle.graal.python.util.PythonUtils.TS_ENCODING;
import static com.oracle.graal.python.util.PythonUtils.toTruffleStringUncached;
import static com.oracle.graal.python.util.PythonUtils.tsLiteral;

import com.oracle.truffle.api.strings.TruffleString;

/**
 * This enum represents formats used by {@code array} and {@code memoryview}. The correspondence
 * between the type specifier string and {@link BufferFormat} is not 1 to 1. Multiple specifiers may
 * represent the same format (e.g. both {@code L} and {@code Q} on 64 bit platforms both map to
 * {@link BufferFormat#UINT_64}) format. Therefore it is necessary to keep the original specifier
 * string around for error messages.
 */
public enum BufferFormat {
    UINT_8(1, 0, "B"),
    INT_8(1, 0, "b"),
    UINT_16(2, 1, "H"),
    INT_16(2, 1, "h"),
    UINT_32(4, 2, "I"),
    INT_32(4, 2, "i"),
    UINT_64(8, 3, "L"),
    INT_64(8, 3, "l"),
    FLOAT(4, 2, "f"),
    DOUBLE(8, 3, "d"),
    // Unicode is array-only and deprecated
    UNICODE(4, 2, "u"),
    // The following are memoryview-only
    CHAR(1, 0, "c"),
    BOOLEAN(1, 0, "?"),
    OTHER(-1, 0, null);

    public static final TruffleString T_UINT_8_TYPE_CODE = tsLiteral("B");
    public static final TruffleString T_UNICODE_TYPE_CODE_U = tsLiteral("u");
    public static final TruffleString T_UNICODE_TYPE_CODE_W = tsLiteral("w");

    public final int bytesize;
    public final int shift;
    public final TruffleString baseTypeCode;

    BufferFormat(int bytesize, int shift, String baseTypeCode) {
        assert bytesize == -1 || bytesize == 1 << shift;
        this.bytesize = bytesize;
        this.shift = shift;
        this.baseTypeCode = toTruffleStringUncached(baseTypeCode);
    }

    public static BufferFormat forMemoryView(TruffleString formatString, TruffleString.CodePointLengthNode lengthNode, TruffleString.CodePointAtIndexNode atIndexNode) {
        char fmtchar;
        int length = lengthNode.execute(formatString, TS_ENCODING);
        if (length == 1) {
            fmtchar = (char) atIndexNode.execute(formatString, 0, TS_ENCODING);
        } else if (length == 2 && atIndexNode.execute(formatString, 0, TS_ENCODING) == '@') {
            fmtchar = (char) atIndexNode.execute(formatString, 1, TS_ENCODING);
        } else {
            return OTHER;
        }
        switch (fmtchar) {
            case 'N':
            case 'P':
                return UINT_64;
            case 'n':
                return INT_64;
            case '?':
                return BOOLEAN;
            case 'c':
                return CHAR;
        }
        BufferFormat format = fromCharCommon(fmtchar);
        return format != null ? format : OTHER;
    }

    public static BufferFormat forArray(TruffleString formatString, TruffleString.CodePointLengthNode lengthNode, TruffleString.CodePointAtIndexNode atIndexNode) {
        int length = lengthNode.execute(formatString, TS_ENCODING);
        if (length == 1) {
            char fmtchar = (char) atIndexNode.execute(formatString, 0, TS_ENCODING);
            if (fmtchar == 'u') {
                return UNICODE;
            }
            return fromCharCommon(fmtchar);
        }
        return null;
    }

    private static BufferFormat fromCharCommon(char fmtchar) {
        // TODO fetch the right byte size from sulong during build
        switch (fmtchar) {
            case 'B':
                return UINT_8;
            case 'b':
                return INT_8;
            case 'H':
                return UINT_16;
            case 'h':
                return INT_16;
            case 'I':
                return UINT_32;
            case 'i':
                return INT_32;
            case 'L':
            case 'Q':
                return UINT_64;
            case 'l':
            case 'q':
                return INT_64;
            case 'f':
                return FLOAT;
            case 'd':
                return DOUBLE;
        }
        return null;
    }

    public static boolean isFloatingPoint(BufferFormat format) {
        return format == FLOAT || format == DOUBLE;
    }

}
