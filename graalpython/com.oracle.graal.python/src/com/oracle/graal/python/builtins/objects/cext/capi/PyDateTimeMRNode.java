/*
 * Copyright (c) 2018, 2022, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.builtins.objects.cext.capi;

import static com.oracle.graal.python.nodes.StringLiterals.T_DATE;
import static com.oracle.graal.python.nodes.StringLiterals.T_DATETIME;
import static com.oracle.graal.python.nodes.StringLiterals.T_TIME;
import static com.oracle.graal.python.util.PythonUtils.TS_ENCODING;
import static com.oracle.graal.python.util.PythonUtils.tsLiteral;

import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.PythonAbstractObject.PInteropGetAttributeNode;
import com.oracle.graal.python.builtins.objects.cext.capi.CExtNodes.ToSulongNode;
import com.oracle.graal.python.builtins.objects.cext.common.CArrayWrappers.CByteArrayWrapper;
import com.oracle.graal.python.builtins.objects.object.PythonObject;
import com.oracle.graal.python.lib.PyNumberAsSizeNode;
import com.oracle.graal.python.nodes.attributes.ReadAttributeFromObjectNode;
import com.oracle.graal.python.runtime.PythonOptions;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.strings.TruffleString;

@GenerateUncached
@ImportStatic({NativeMember.class, PythonOptions.class})
public abstract class PyDateTimeMRNode extends Node {

    public enum DateTimeMode {
        DATE,
        TIME,
        DATE_TIME
    }

    static DateTimeMode getModeFromTypeName(TruffleString typeName, TruffleString.EqualNode eqNode) {
        if (eqNode.execute(T_DATE, typeName, TS_ENCODING)) {
            return DateTimeMode.DATE;
        } else if (eqNode.execute(T_DATETIME, typeName, TS_ENCODING)) {
            return DateTimeMode.DATE_TIME;
        } else if (eqNode.execute(T_TIME, typeName, TS_ENCODING)) {
            return DateTimeMode.TIME;
        }
        return null;
    }

    public abstract Object execute(PythonObject object, String key, DateTimeMode mode);

    public static final TruffleString T_YEAR = tsLiteral("year");
    public static final TruffleString T_MONTH = tsLiteral("month");
    public static final TruffleString T_DAY = tsLiteral("day");
    public static final TruffleString T_HOUR = tsLiteral("hour");
    public static final TruffleString T_MIN = tsLiteral("minute");
    public static final TruffleString T_SEC = tsLiteral("second");
    public static final TruffleString T_USEC = tsLiteral("microsecond");
    public static final TruffleString T_TZINFO = tsLiteral("tzinfo");

    /**
     * Fields are packed into successive bytes, each viewed as unsigned and big-endian, unless
     * otherwise noted:
     *
     * PyDateTime_DateTime:
     *
     * <pre>
     * byte offset
     *  0           year     2 bytes, 1-9999
     *  2           month    1 byte, 1-12
     *  3           day      1 byte, 1-31
     *  4           hour     1 byte, 0-23
     *  5           minute   1 byte, 0-59
     *  6           second   1 byte, 0-59
     *  7           usecond  3 bytes, 0-999999
     * 10
     * </pre>
     *
     * PyDateTime_Date:
     *
     * <pre>
     * byte offset
     *  0           year     2 bytes, 1-9999
     *  2           month    1 byte, 1-12
     *  3           day      1 byte, 1-31
     * 10
     * </pre>
     *
     * PyDateTime_Time:
     *
     * <pre>
     * byte offset
     *  4           hour     1 byte, 0-23
     *  5           minute   1 byte, 0-59
     *  6           second   1 byte, 0-59
     *  7           usecond  3 bytes, 0-999999
     * 10
     * </pre>
     */
    @Specialization(guards = {"eq(DATETIME_DATA, key)", "cachedMode == mode", "cachedMode != null"}, limit = "1")
    static Object doData(PythonObject object, @SuppressWarnings("unused") String key, @SuppressWarnings("unused") DateTimeMode mode,
                    @Cached("mode") DateTimeMode cachedMode,
                    @Cached PInteropGetAttributeNode getYearNode,
                    @Cached PInteropGetAttributeNode getMonthNode,
                    @Cached PInteropGetAttributeNode getDayNode,
                    @Cached PInteropGetAttributeNode getHourNode,
                    @Cached PInteropGetAttributeNode getMinNode,
                    @Cached PInteropGetAttributeNode getSecNode,
                    @Cached PInteropGetAttributeNode getUSecNode,
                    @Cached PyNumberAsSizeNode asSizeNode) {

        // passing null here should be ok, since we should be in an interop situation
        int year = -1;
        int month = -1;
        int day = -1;
        int hour = -1;
        int min = -1;
        int sec = -1;
        int usec = -1;
        if (cachedMode == DateTimeMode.DATE || cachedMode == DateTimeMode.DATE_TIME) {
            year = asSizeNode.executeExact(null, getYearNode.execute(object, T_YEAR));
            month = asSizeNode.executeExact(null, getMonthNode.execute(object, T_MONTH));
            day = asSizeNode.executeExact(null, getDayNode.execute(object, T_DAY));
            assert year >= 0 && year < 0x10000;
            assert month >= 0 && month < 0x100;
            assert day >= 0 && day < 0x100;
        }
        if (cachedMode == DateTimeMode.TIME || cachedMode == DateTimeMode.DATE_TIME) {
            hour = asSizeNode.executeExact(null, getHourNode.execute(object, T_HOUR));
            min = asSizeNode.executeExact(null, getMinNode.execute(object, T_MIN));
            sec = asSizeNode.executeExact(null, getSecNode.execute(object, T_SEC));
            usec = asSizeNode.executeExact(null, getUSecNode.execute(object, T_USEC));
            assert hour >= 0 && hour < 0x100;
            assert min >= 0 && min < 0x100;
            assert sec >= 0 && sec < 0x100;
            assert usec >= 0 && sec < 0x1000000;
        }

        byte[] data;
        switch (cachedMode) {
            case DATE:
                data = new byte[]{(byte) (year >> 8), (byte) year, (byte) month, (byte) day};
                break;
            case TIME:
                data = new byte[]{(byte) hour, (byte) min, (byte) sec, (byte) (usec >> 16), (byte) (usec >> 8), (byte) usec};
                break;
            case DATE_TIME:
                data = new byte[]{(byte) (year >> 8), (byte) year, (byte) month, (byte) day, (byte) hour, (byte) min, (byte) sec, (byte) (usec >> 16), (byte) (usec >> 8), (byte) usec};
                break;
            default:
                throw CompilerDirectives.shouldNotReachHere();
        }
        return new CByteArrayWrapper(data);
    }

    @Specialization(guards = "eq(DATETIME_TZINFO, key)")
    static Object doTzinfo(PythonObject object, @SuppressWarnings("unused") String key, @SuppressWarnings("unused") DateTimeMode mode,
                    @Cached ReadAttributeFromObjectNode getTzinfoNode,
                    @Cached ToSulongNode toSulongNode) {
        Object value = getTzinfoNode.execute(object, T_TZINFO);
        if (value != PNone.NO_VALUE) {
            return toSulongNode.execute(value);
        }
        throw CompilerDirectives.shouldNotReachHere();
    }

    protected static boolean eq(NativeMember expected, String actual) {
        return expected.getMemberNameJavaString().equals(actual);
    }
}
