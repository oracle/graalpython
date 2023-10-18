/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.nodes;

import com.oracle.truffle.api.strings.TruffleString;

import static com.oracle.graal.python.util.PythonUtils.tsLiteral;

public abstract class HostInteropMethodNames {
    // -----------------------------------------------------------------------------------------------------------------
    // IS_XXX messages
    public static final String J_IS_BOOLEAN = "is_boolean";
    public static final TruffleString T_IS_BOOLEAN = tsLiteral(J_IS_BOOLEAN);
    public static final String J_IS_DATE = "is_date";
    public static final TruffleString T_IS_DATE = tsLiteral(J_IS_DATE);
    public static final String J_IS_DURATION = "is_duration";
    public static final TruffleString T_IS_DURATION = tsLiteral(J_IS_DURATION);
    public static final String J_IS_EXCEPTION = "is_exception";
    public static final TruffleString T_IS_EXCEPTION = tsLiteral(J_IS_EXCEPTION);
    public static final String J_IS_INSTANT = "is_instant";
    public static final TruffleString T_IS_INSTANT = tsLiteral(J_IS_INSTANT);
    public static final String J_IS_ITERATOR = "is_iterator";
    public static final TruffleString T_IS_ITERATOR = tsLiteral(J_IS_ITERATOR);
    public static final String J_IS_NULL = "is_null";
    public static final TruffleString T_IS_NULL = tsLiteral(J_IS_NULL);
    public static final String J_IS_NUMBER = "is_number";
    public static final TruffleString T_IS_NUMBER = tsLiteral(J_IS_NUMBER);
    public static final String J_IS_STRING = "is_string";
    public static final TruffleString T_IS_STRING = tsLiteral(J_IS_STRING);
    public static final String J_IS_TIME = "is_time";
    public static final TruffleString T_IS_TIME = tsLiteral(J_IS_TIME);
    public static final String J_IS_TIME_ZONE = "is_time_zone";
    public static final TruffleString T_IS_TIME_ZONE = tsLiteral(J_IS_TIME_ZONE);

    // -----------------------------------------------------------------------------------------------------------------
    // FITS_IN_XXX messages
    public static final String J_FITS_IN_BIG_INTEGER = "fits_in_big_integer";
    public static final TruffleString T_FITS_IN_BIG_INTEGER = tsLiteral(J_FITS_IN_BIG_INTEGER);
    public static final String J_FITS_IN_BYTE = "fits_in_byte";
    public static final TruffleString T_FITS_IN_BYTE = tsLiteral(J_FITS_IN_BYTE);
    public static final String J_FITS_IN_DOUBLE = "fits_in_double";
    public static final TruffleString T_FITS_IN_DOUBLE = tsLiteral(J_FITS_IN_DOUBLE);
    public static final String J_FITS_IN_FLOAT = "fits_in_float";
    public static final TruffleString T_FITS_IN_FLOAT = tsLiteral(J_FITS_IN_FLOAT);
    public static final String J_FITS_IN_INT = "fits_in_int";
    public static final TruffleString T_FITS_IN_INT = tsLiteral(J_FITS_IN_INT);
    public static final String J_FITS_IN_LONG = "fits_in_long";
    public static final TruffleString T_FITS_IN_LONG = tsLiteral(J_FITS_IN_LONG);
    public static final String J_FITS_IN_SHORT = "fits_in_short";
    public static final TruffleString T_FITS_IN_SHORT = tsLiteral(J_FITS_IN_SHORT);

    // -----------------------------------------------------------------------------------------------------------------
    // AS_XXX messages
    public static final String J_AS_BIG_INTEGER = "as_big_integer";
    public static final TruffleString T_AS_BIG_INTEGER = tsLiteral(J_AS_BIG_INTEGER);
    public static final String J_AS_BOOLEAN = "as_boolean";
    public static final TruffleString T_AS_BOOLEAN = tsLiteral(J_AS_BOOLEAN);
    public static final String J_AS_BYTE = "as_byte";
    public static final TruffleString T_AS_BYTE = tsLiteral(J_AS_BYTE);
    public static final String J_AS_DATE = "as_date";
    public static final TruffleString T_AS_DATE = tsLiteral(J_AS_DATE);
    public static final String J_AS_DOUBLE = "as_double";
    public static final TruffleString T_AS_DOUBLE = tsLiteral(J_AS_DOUBLE);
    public static final String J_AS_DURATION = "as_duration";
    public static final TruffleString T_AS_DURATION = tsLiteral(J_AS_DURATION);
    public static final String J_AS_FLOAT = "as_float";
    public static final TruffleString T_AS_FLOAT = tsLiteral(J_AS_FLOAT);
    public static final String J_AS_INSTANT = "as_instant";
    public static final TruffleString T_AS_INSTANT = tsLiteral(J_AS_INSTANT);
    public static final String J_AS_INT = "as_int";
    public static final TruffleString T_AS_INT = tsLiteral(J_AS_INT);
    public static final String J_AS_LONG = "as_long";
    public static final TruffleString T_AS_LONG = tsLiteral(J_AS_LONG);
    public static final String J_AS_SHORT = "as_short";
    public static final TruffleString T_AS_SHORT = tsLiteral(J_AS_SHORT);
    public static final String J_AS_STRING = "as_string";
    public static final TruffleString T_AS_STRING = tsLiteral(J_AS_STRING);
    public static final String J_AS_TIME = "as_time";
    public static final TruffleString T_AS_TIME = tsLiteral(J_AS_TIME);
    public static final String J_AS_TIME_ZONE = "as_time_zone";
    public static final TruffleString T_AS_TIME_ZONE = tsLiteral(J_AS_TIME_ZONE);
}
