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
package com.oracle.graal.python.nodes.interop;

import com.oracle.truffle.api.strings.TruffleString;

import static com.oracle.graal.python.nodes.HostInteropMethodNames.J_AS_BIG_INTEGER;
import static com.oracle.graal.python.nodes.HostInteropMethodNames.J_AS_BOOLEAN;
import static com.oracle.graal.python.nodes.HostInteropMethodNames.J_AS_BYTE;
import static com.oracle.graal.python.nodes.HostInteropMethodNames.J_AS_DATE;
import static com.oracle.graal.python.nodes.HostInteropMethodNames.J_AS_DOUBLE;
import static com.oracle.graal.python.nodes.HostInteropMethodNames.J_AS_DURATION;
import static com.oracle.graal.python.nodes.HostInteropMethodNames.J_AS_FLOAT;
import static com.oracle.graal.python.nodes.HostInteropMethodNames.J_AS_INSTANT;
import static com.oracle.graal.python.nodes.HostInteropMethodNames.J_AS_INT;
import static com.oracle.graal.python.nodes.HostInteropMethodNames.J_AS_LONG;
import static com.oracle.graal.python.nodes.HostInteropMethodNames.J_AS_SHORT;
import static com.oracle.graal.python.nodes.HostInteropMethodNames.J_AS_STRING;
import static com.oracle.graal.python.nodes.HostInteropMethodNames.J_AS_TIME;
import static com.oracle.graal.python.nodes.HostInteropMethodNames.J_AS_TIME_ZONE;
import static com.oracle.graal.python.nodes.HostInteropMethodNames.J_FITS_IN_BIG_INTEGER;
import static com.oracle.graal.python.nodes.HostInteropMethodNames.J_FITS_IN_BYTE;
import static com.oracle.graal.python.nodes.HostInteropMethodNames.J_FITS_IN_DOUBLE;
import static com.oracle.graal.python.nodes.HostInteropMethodNames.J_FITS_IN_FLOAT;
import static com.oracle.graal.python.nodes.HostInteropMethodNames.J_FITS_IN_INT;
import static com.oracle.graal.python.nodes.HostInteropMethodNames.J_FITS_IN_LONG;
import static com.oracle.graal.python.nodes.HostInteropMethodNames.J_FITS_IN_SHORT;
import static com.oracle.graal.python.nodes.HostInteropMethodNames.J_IS_BOOLEAN;
import static com.oracle.graal.python.nodes.HostInteropMethodNames.J_IS_DATE;
import static com.oracle.graal.python.nodes.HostInteropMethodNames.J_IS_DURATION;
import static com.oracle.graal.python.nodes.HostInteropMethodNames.J_IS_INSTANT;
import static com.oracle.graal.python.nodes.HostInteropMethodNames.J_IS_ITERATOR;
import static com.oracle.graal.python.nodes.HostInteropMethodNames.J_IS_NULL;
import static com.oracle.graal.python.nodes.HostInteropMethodNames.J_IS_NUMBER;
import static com.oracle.graal.python.nodes.HostInteropMethodNames.J_IS_STRING;
import static com.oracle.graal.python.nodes.HostInteropMethodNames.J_IS_TIME;
import static com.oracle.graal.python.nodes.HostInteropMethodNames.J_IS_TIME_ZONE;
import static com.oracle.graal.python.nodes.HostInteropMethodNames.T_AS_BIG_INTEGER;
import static com.oracle.graal.python.nodes.HostInteropMethodNames.T_AS_BOOLEAN;
import static com.oracle.graal.python.nodes.HostInteropMethodNames.T_AS_BYTE;
import static com.oracle.graal.python.nodes.HostInteropMethodNames.T_AS_DATE;
import static com.oracle.graal.python.nodes.HostInteropMethodNames.T_AS_DOUBLE;
import static com.oracle.graal.python.nodes.HostInteropMethodNames.T_AS_DURATION;
import static com.oracle.graal.python.nodes.HostInteropMethodNames.T_AS_FLOAT;
import static com.oracle.graal.python.nodes.HostInteropMethodNames.T_AS_INSTANT;
import static com.oracle.graal.python.nodes.HostInteropMethodNames.T_AS_INT;
import static com.oracle.graal.python.nodes.HostInteropMethodNames.T_AS_LONG;
import static com.oracle.graal.python.nodes.HostInteropMethodNames.T_AS_SHORT;
import static com.oracle.graal.python.nodes.HostInteropMethodNames.T_AS_STRING;
import static com.oracle.graal.python.nodes.HostInteropMethodNames.T_AS_TIME;
import static com.oracle.graal.python.nodes.HostInteropMethodNames.T_AS_TIME_ZONE;
import static com.oracle.graal.python.nodes.HostInteropMethodNames.T_FITS_IN_BIG_INTEGER;
import static com.oracle.graal.python.nodes.HostInteropMethodNames.T_FITS_IN_BYTE;
import static com.oracle.graal.python.nodes.HostInteropMethodNames.T_FITS_IN_DOUBLE;
import static com.oracle.graal.python.nodes.HostInteropMethodNames.T_FITS_IN_FLOAT;
import static com.oracle.graal.python.nodes.HostInteropMethodNames.T_FITS_IN_INT;
import static com.oracle.graal.python.nodes.HostInteropMethodNames.T_FITS_IN_LONG;
import static com.oracle.graal.python.nodes.HostInteropMethodNames.T_FITS_IN_SHORT;
import static com.oracle.graal.python.nodes.HostInteropMethodNames.T_IS_BOOLEAN;
import static com.oracle.graal.python.nodes.HostInteropMethodNames.T_IS_DATE;
import static com.oracle.graal.python.nodes.HostInteropMethodNames.T_IS_DURATION;
import static com.oracle.graal.python.nodes.HostInteropMethodNames.T_IS_INSTANT;
import static com.oracle.graal.python.nodes.HostInteropMethodNames.T_IS_ITERATOR;
import static com.oracle.graal.python.nodes.HostInteropMethodNames.T_IS_NULL;
import static com.oracle.graal.python.nodes.HostInteropMethodNames.T_IS_NUMBER;
import static com.oracle.graal.python.nodes.HostInteropMethodNames.T_IS_STRING;
import static com.oracle.graal.python.nodes.HostInteropMethodNames.T_IS_TIME;
import static com.oracle.graal.python.nodes.HostInteropMethodNames.T_IS_TIME_ZONE;

public enum HostInteropBehaviorArg {
    is_boolean(J_IS_BOOLEAN, T_IS_BOOLEAN, true),
    is_date(J_IS_DATE, T_IS_DATE, true),
    is_duration(J_IS_DURATION, T_IS_DURATION, true),
    is_exception(J_IS_DATE, T_IS_DATE, true),
    is_instant(J_IS_INSTANT, T_IS_INSTANT, true),
    is_iterator(J_IS_ITERATOR, T_IS_ITERATOR, true),
    is_null(J_IS_NULL, T_IS_NULL, true),
    is_number(J_IS_NUMBER, T_IS_NUMBER, true),
    is_string(J_IS_STRING, T_IS_STRING, true),
    is_time(J_IS_TIME, T_IS_TIME, true),
    is_time_zone(J_IS_TIME_ZONE, T_IS_TIME_ZONE, true),

    fits_in_big_integer(J_FITS_IN_BIG_INTEGER, T_FITS_IN_BIG_INTEGER, false),
    fits_in_byte(J_FITS_IN_BYTE, T_FITS_IN_BYTE, false),
    fits_in_double(J_FITS_IN_DOUBLE, T_FITS_IN_DOUBLE, false),
    fits_in_float(J_FITS_IN_FLOAT, T_FITS_IN_FLOAT, false),
    fits_in_int(J_FITS_IN_INT, T_FITS_IN_INT, false),
    fits_in_long(J_FITS_IN_LONG, T_FITS_IN_LONG, false),
    fits_in_short(J_FITS_IN_SHORT, T_FITS_IN_SHORT, false),
    as_big_integer(J_AS_BIG_INTEGER, T_AS_BIG_INTEGER, false),
    as_boolean(J_AS_BOOLEAN, T_AS_BOOLEAN, false),
    as_byte(J_AS_BYTE, T_AS_BYTE, false),
    as_date(J_AS_DATE, T_AS_DATE, false),
    as_double(J_AS_DOUBLE, T_AS_DOUBLE, false),
    as_duration(J_AS_DURATION, T_AS_DURATION, false),
    as_float(J_AS_FLOAT, T_AS_FLOAT, false),
    as_instant(J_AS_INSTANT, T_AS_INSTANT, false),
    as_int(J_AS_INT, T_AS_INT, false),
    as_long(J_AS_LONG, T_AS_LONG, false),
    as_short(J_AS_SHORT, T_AS_SHORT, false),
    as_string(J_AS_STRING, T_AS_STRING, false),
    as_time(J_AS_TIME, T_AS_TIME, false),
    as_time_zone(J_AS_TIME_ZONE, T_AS_TIME_ZONE, false);

    public final String name;
    public final TruffleString tsName;
    public final boolean isConstant;

    HostInteropBehaviorArg(String name, TruffleString tsName, boolean isConstant) {
        this.name = name;
        this.tsName = tsName;
        this.isConstant = isConstant;
    }
}
