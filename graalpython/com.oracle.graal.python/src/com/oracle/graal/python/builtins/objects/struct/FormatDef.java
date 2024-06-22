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

import static com.oracle.graal.python.builtins.objects.struct.FormatCode.FMT_INT;
import static com.oracle.graal.python.builtins.objects.struct.FormatCode.FMT_LONG;
import static com.oracle.graal.python.builtins.objects.struct.FormatCode.FMT_LONG_LONG;
import static com.oracle.graal.python.builtins.objects.struct.FormatCode.FMT_SHORT;
import static com.oracle.graal.python.builtins.objects.struct.FormatCode.FMT_SIGNED_CHAR;
import static com.oracle.graal.python.builtins.objects.struct.FormatCode.FMT_SIZE_T;
import static com.oracle.graal.python.builtins.objects.struct.FormatCode.FMT_UNSIGNED_CHAR;
import static com.oracle.graal.python.builtins.objects.struct.FormatCode.FMT_UNSIGNED_INT;
import static com.oracle.graal.python.builtins.objects.struct.FormatCode.FMT_UNSIGNED_LONG;
import static com.oracle.graal.python.builtins.objects.struct.FormatCode.FMT_UNSIGNED_LONG_LONG;
import static com.oracle.graal.python.builtins.objects.struct.FormatCode.FMT_UNSIGNED_SHORT;
import static com.oracle.graal.python.builtins.objects.struct.FormatCode.FMT_UNSIGNED_SIZE_T;

import com.oracle.graal.python.util.PythonUtils;
import com.oracle.truffle.api.strings.TruffleString;

public final class FormatDef {
    public final char format;
    public final int size;
    public final int alignment;
    // assigned internally
    public final boolean unsigned;
    public final long min;
    public final long max;
    public final TruffleString name;
    public final boolean integer;
    private final TruffleString label;

    private static final TruffleString T_UBYTE = tsLiteral("ubyte");
    private static final TruffleString T_BYTE = tsLiteral("byte");
    private static final TruffleString T_USHORT = tsLiteral("ushort");
    private static final TruffleString T_SHORT = tsLiteral("short");
    private static final TruffleString T_UINT = tsLiteral("uint");
    private static final TruffleString T_INT = tsLiteral("int");
    private static final TruffleString T_ULONG = tsLiteral("ulong");
    private static final TruffleString T_LONG = tsLiteral("long");

    public FormatDef(char format, TruffleString label, int size, int alignment) {
        this.format = format;
        this.label = label;
        this.size = size;
        this.alignment = alignment;
        this.unsigned = (this.format == FMT_UNSIGNED_CHAR ||
                        this.format == FMT_UNSIGNED_SHORT ||
                        this.format == FMT_UNSIGNED_INT ||
                        this.format == FMT_UNSIGNED_LONG ||
                        this.format == FMT_UNSIGNED_LONG_LONG ||
                        this.format == FMT_UNSIGNED_SIZE_T);
        this.integer = (this.format == FMT_SIGNED_CHAR || this.format == FMT_UNSIGNED_CHAR ||
                        this.format == FMT_SHORT || this.format == FMT_UNSIGNED_SHORT ||
                        this.format == FMT_INT || this.format == FMT_UNSIGNED_INT ||
                        this.format == FMT_LONG || this.format == FMT_UNSIGNED_LONG ||
                        this.format == FMT_LONG_LONG || this.format == FMT_UNSIGNED_LONG_LONG ||
                        this.format == FMT_SIZE_T || this.format == FMT_UNSIGNED_SIZE_T);
        if (this.size == 1) {
            this.name = (this.unsigned) ? T_UBYTE : T_BYTE;
            this.min = (this.unsigned) ? 0L : Byte.MIN_VALUE;
            this.max = (this.unsigned) ? 0xffL : Byte.MAX_VALUE;
        } else if (this.size == 2) {
            this.name = (this.unsigned) ? T_USHORT : T_SHORT;
            this.min = (this.unsigned) ? 0L : Short.MIN_VALUE;
            this.max = (this.unsigned) ? 0xffffL : Short.MAX_VALUE;
        } else if (this.size == 4) {
            this.name = (this.unsigned) ? T_UINT : T_INT;
            this.min = (this.unsigned) ? 0L : Integer.MIN_VALUE;
            this.max = (this.unsigned) ? 0xffffffffL : Integer.MAX_VALUE;
        } else if (this.size == 8) {
            this.name = (this.unsigned) ? T_ULONG : T_LONG;
            this.min = (this.unsigned) ? 0L : Long.MIN_VALUE;
            this.max = (this.unsigned) ? 0xffffffffffffffffL : Long.MAX_VALUE;
        } else {
            this.name = null;
            this.min = -1;
            this.max = -1;
        }
    }

    @Override
    public String toString() {
        return PythonUtils.formatJString("%s(%c,%d,%d)", this.label, this.format, this.size, this.alignment);
    }
}
