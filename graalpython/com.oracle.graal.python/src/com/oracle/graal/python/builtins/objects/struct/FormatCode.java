/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.builtins.objects.struct;

import static com.oracle.graal.python.util.PythonUtils.tsLiteral;

import com.oracle.truffle.api.strings.TruffleString;

public final class FormatCode {
    public static final char FMT_PAD_BYTE = 'x';
    public static final char FMT_CHAR = 'c';
    public static final char FMT_SIGNED_CHAR = 'b';
    public static final char FMT_UNSIGNED_CHAR = 'B';
    public static final char FMT_BOOL = '?';
    public static final char FMT_SHORT = 'h';
    public static final char FMT_UNSIGNED_SHORT = 'H';
    public static final char FMT_INT = 'i';
    public static final char FMT_UNSIGNED_INT = 'I';
    public static final char FMT_LONG = 'l';
    public static final char FMT_UNSIGNED_LONG = 'L';
    public static final char FMT_LONG_LONG = 'q';
    public static final char FMT_UNSIGNED_LONG_LONG = 'Q';
    public static final char FMT_SIZE_T = 'n';
    public static final char FMT_UNSIGNED_SIZE_T = 'N';
    public static final char FMT_HALF_FLOAT = 'e';
    public static final char FMT_FLOAT = 'f';
    public static final char FMT_DOUBLE = 'd';
    public static final char FMT_STRING = 's';
    public static final char FMT_PASCAL_STRING = 'p';
    public static final char FMT_VOID_PTR = 'P';

    public static final TruffleString T_LBL_PAD_BYTE = tsLiteral("BYTE");
    public static final TruffleString T_LBL_CHAR = tsLiteral("CHAR");
    public static final TruffleString T_LBL_SIGNED_CHAR = tsLiteral("SIGNED CHAR");
    public static final TruffleString T_LBL_UNSIGNED_CHAR = tsLiteral("UNSIGNED CHAR");
    public static final TruffleString T_LBL_BOOL = tsLiteral("BOOLEAN");
    public static final TruffleString T_LBL_SHORT = tsLiteral("SHORT");
    public static final TruffleString T_LBL_UNSIGNED_SHORT = tsLiteral("UNSIGNED SHORT");
    public static final TruffleString T_LBL_INT = tsLiteral("INT");
    public static final TruffleString T_LBL_UNSIGNED_INT = tsLiteral("UNSIGNED INT");
    public static final TruffleString T_LBL_LONG = tsLiteral("LONG");
    public static final TruffleString T_LBL_UNSIGNED_LONG = tsLiteral("UNSIGNED LONG");
    public static final TruffleString T_LBL_LONG_LONG = tsLiteral("LONG LONG");
    public static final TruffleString T_LBL_UNSIGNED_LONG_LONG = tsLiteral("UNSIGNED LONG LONG");
    public static final TruffleString T_LBL_SIZE_T = tsLiteral("SIZE T");
    public static final TruffleString T_LBL_UNSIGNED_SIZE_T = tsLiteral("UNSIGNED SIZE T");
    public static final TruffleString T_LBL_HALF_FLOAT = tsLiteral("HALF FLOAT");
    public static final TruffleString T_LBL_FLOAT = tsLiteral("FLOAT");
    public static final TruffleString T_LBL_DOUBLE = tsLiteral("DOUBLE");
    public static final TruffleString T_LBL_STRING = tsLiteral("STRING");
    public static final TruffleString T_LBL_PASCAL_STRING = tsLiteral("PASCAL STRING");
    public static final TruffleString T_LBL_VOID_PTR = tsLiteral("VOID PTR");

    public final FormatDef formatDef;
    public final int offset;
    public final int size;
    public final int repeat;

    public FormatCode(FormatDef formatDef, int offset, int size, int repeat) {
        this.formatDef = formatDef;
        this.offset = offset;
        this.size = size;
        this.repeat = repeat;
    }

    public int numBytes() {
        return formatDef.size;
    }

    public boolean isUnsigned() {
        return formatDef.unsigned;
    }

    @Override
    public String toString() {
        return String.format("FormatCode(%s, offset: %d, size: %s, repeat: %d)", this.formatDef, this.offset, this.size, this.repeat);
    }
}
