/*
 * Copyright (c) 2019, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.test.interop;

import com.oracle.graal.python.test.PythonTests;
import java.io.ByteArrayOutputStream;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Source;
import org.graalvm.polyglot.Value;
import org.junit.After;
import static org.junit.Assert.*;
import org.junit.Before;
import org.junit.Test;

public class TimeDateTest extends PythonTests {

    private ByteArrayOutputStream out;
    private Context context;
    private ByteArrayOutputStream err;

    @Before
    public void setUpTest() {
        out = new ByteArrayOutputStream();
        err = new ByteArrayOutputStream();
        Context.Builder builder = Context.newBuilder();
        builder.allowExperimentalOptions(true);
        builder.allowAllAccess(true);
        builder.out(out);
        builder.err(err);
        context = builder.build();
    }

    @After
    public void tearDown() {
        context.close();
    }

    @Test
    public void testDateTimeDate01() {
        String source = "import datetime as dt; " +
                        "dt.date.fromisoformat('2019-12-02')";
        Value value = getValue(source, ZoneId.of("America/Los_Angeles"));
        checkValueDateTimeType(value, true, false, false, false);
        checkDate(value, 2019, 12, 02);
        try {
            value.asTime();
            assertFalse("From datetime.date can not be obtained time", true);
        } catch (Exception ex) {
        }
    }

    @Test
    public void testDateTimeTime01() {
        String source = "import datetime as dt; " +
                        "dt.time.fromisoformat('04:23:01')";
        Value value = getValue(source, ZoneId.of("America/Los_Angeles"));
        checkValueDateTimeType(value, false, true, false, false);
        checkTime(value, 4, 23, 1, 0);
        try {
            value.asDate();
            assertFalse("From datetime.time can not be obtained date", true);
        } catch (Exception ex) {
        }
    }

    @Test
    public void testDateTimeTime02() {
        String source = "import datetime as dt; " +
                        "dt.time.fromisoformat('04:23:01.000384')";
        Value value = getValue(source, ZoneId.of("America/Los_Angeles"));
        checkValueDateTimeType(value, false, true, false, false);
        checkTime(value, 4, 23, 1, 384);
        try {
            value.asDate();
            assertFalse("From datetime.time can not be obtained date", true);
        } catch (Exception ex) {
        }
    }

    // TODO this test is desabled due the issue GR-19849
    public void testDateTimeTime03() {
        String source = "import datetime as dt; " +
                        "dt.time.fromisoformat('04:23:01+04:00')";
        Value value = getValue(source, ZoneId.of("America/Los_Angeles"));
        checkValueDateTimeType(value, false, true, false, true);
        checkTime(value, 4, 23, 1, 0);
        try {
            value.asDate();
            assertFalse("From datetime.time can not be obtained date", true);
        } catch (Exception ex) {
        }
        assertEquals(ZoneId.of("UTC+4"), value.asTimeZone());
    }

    @Test
    public void testDateTimeDateTime01() {
        String source = "import datetime as dt; " +
                        "dt.datetime.now()";
        Value value = getValue(source, ZoneId.of("America/Los_Angeles"));
        checkValueDateTimeType(value, true, true, false, false);
    }

    @Test
    public void testDateTimeDateTime02() {
        String source = "import datetime as dt\n" +
                        "dt.datetime.now(dt.timezone.utc)";
        Value value = getValue(source, ZoneId.of("America/Los_Angeles"));
        checkValueDateTimeType(value, true, true, true, true);
        assertEquals(ZoneId.of("UTC"), value.asTimeZone());
    }

    @Test
    public void testDateTimeDateTime03() {
        String source = "from datetime import datetime\n" +
                        "datetime.fromisoformat('2011-11-04 00:05:23.283+04:00')";
        Value value = getValue(source, ZoneId.of("America/Los_Angeles"));
        checkValueDateTimeType(value, true, true, true, true);
        checkDate(value, 2011, 11, 4);
        checkTime(value, 0, 5, 23, 283000);
        assertEquals(ZoneId.of("UTC+4"), value.asTimeZone());
    }

    @Test
    public void testStructTime01() {
        String source = "import time\n" +
                        "time.gmtime()";
        Value value = getValue(source, ZoneId.of("UTC+1"));
        checkValueDateTimeType(value, true, true, true, true);
        assertEquals(ZoneId.of("UTC"), value.asTimeZone());
    }

    @Test
    public void testStructTime02() {
        String source = "import time\n" +
                        "time.localtime()";
        Value value = getValue(source, ZoneId.of("UTC+1"));
        checkValueDateTimeType(value, true, true, true, true);
        assertEquals(ZoneId.of("UTC+1"), value.asTimeZone());
    }

    @Test
    public void testStructTime03() {
        String source = "import time\n" +
                        "tup = (2019, 12, 24, 18, 56, 26, 0, 0, 0)\n" +
                        "tt = time.mktime(tup)\n" +
                        "time.localtime(tt)";
        Value value = getValue(source, ZoneId.of("UTC+8"));
        checkValueDateTimeType(value, true, true, true, true);
        checkDate(value, 2019, 12, 24);
        checkTime(value, 18, 56, 26, 0);
        assertEquals(ZoneId.of("UTC+8"), value.asTimeZone());
    }

    private Value getValue(String source, ZoneId timeZoneId) {
        return Context.newBuilder("python").allowAllAccess(true).timeZone(timeZoneId).build().eval(Source.create("python", source));
    }

    private void checkValueDateTimeType(Value value, boolean isDate, boolean isTime, boolean isInstant, boolean isTimeZone) {
        assertEquals(isDate, value.isDate());
        assertEquals(isTime, value.isTime());
        assertEquals(isTimeZone, value.isTimeZone());
        assertEquals(isInstant, value.isInstant());
    }

    private void checkDate(Value value, int expectedYear, int expectedMonth, int expectedDay) {
        LocalDate ld = value.asDate();
        assertEquals(expectedYear, ld.getYear());
        assertEquals(expectedMonth, ld.getMonthValue());
        assertEquals(expectedDay, ld.getDayOfMonth());
    }

    private void checkTime(Value value, int expectedHour, int expectedMinute, int expectedSecond, int expectedNano) {
        LocalTime ld = value.asTime();
        assertEquals(expectedHour, ld.getHour());
        assertEquals(expectedMinute, ld.getMinute());
        assertEquals(expectedSecond, ld.getSecond());
        assertEquals(expectedNano, ld.getNano());
    }
}
