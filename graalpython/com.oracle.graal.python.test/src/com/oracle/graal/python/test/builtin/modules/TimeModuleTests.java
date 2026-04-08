/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.test.builtin.modules;

import static org.junit.Assert.assertEquals;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TimeZone;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Source;
import org.junit.Test;

public class TimeModuleTests {

    @Test
    public void strftimeTimezoneMatchesTzsetState() {
        TimeZone previousDefault = TimeZone.getDefault();
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("Europe/Berlin"));
            String result;
            try (Context context = Context.newBuilder("python").allowAllAccess(true).build()) {
                result = context.eval(Source.create("python", """
                                import os
                                import time

                                os.environ["TZ"] = "UTC"
                                time.tzset()
                                tt = time.localtime()

                                "\\n".join((
                                    f"tm_zone={tt.tm_zone}",
                                    f"tzname={time.tzname[tt.tm_isdst > 0]}",
                                    f"strftime_tuple={time.strftime('%Z', tt)}",
                                    f"strftime_now={time.strftime('%Z')}",
                                ))
                                """)).asString();
            }

            Map<String, String> values = parseKeyValueLines(result);
            String details = values.toString();
            assertEquals(details, values.get("tm_zone"), values.get("tzname"));
            assertEquals(details, values.get("tm_zone"), values.get("strftime_tuple"));
            assertEquals(details, values.get("tm_zone"), values.get("strftime_now"));
        } finally {
            TimeZone.setDefault(previousDefault);
        }
    }

    private static Map<String, String> parseKeyValueLines(String output) {
        Map<String, String> values = new LinkedHashMap<>();
        for (String line : output.split("\\R")) {
            int separator = line.indexOf('=');
            values.put(line.substring(0, separator), line.substring(separator + 1));
        }
        return values;
    }
}
