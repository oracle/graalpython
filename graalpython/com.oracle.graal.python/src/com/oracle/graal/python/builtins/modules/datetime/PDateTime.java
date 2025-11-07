/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.builtins.modules.datetime;

import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.lib.PyObjectCallMethodObjArgs;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.api.object.Shape;

import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;

@ExportLibrary(InteropLibrary.class)
public class PDateTime extends PDate {
    public final int hour;
    public final int minute;
    public final int second;
    public final int microsecond;
    public final Object tzInfo;
    public final int fold;

    public PDateTime(Object cls, Shape instanceShape, int year, int month, int day, int hour, int minute, int second, int microsecond, Object tzInfo, int fold) {
        super(cls, instanceShape, year, month, day);

        this.hour = hour;
        this.minute = minute;
        this.second = second;
        this.microsecond = microsecond;
        this.tzInfo = tzInfo instanceof PNone ? null : tzInfo;
        this.fold = fold;
    }

    // region Date and Time messages

    // Don't implement asInstant as far is in general case it's a complex operation.
    // See {@code DateTimeBuiltins.TimestampNode}.

    @ExportMessage
    public boolean isTime() {
        return true;
    }

    @TruffleBoundary
    @ExportMessage
    public LocalTime asTime() {
        return LocalTime.of(hour, minute, second, microsecond * 1_000);
    }

    @ExportMessage
    public boolean isTimeZone() {
        return tzInfo != null;
    }

    @TruffleBoundary
    @ExportMessage
    public ZoneId asTimeZone() throws UnsupportedMessageException {
        if (tzInfo == null) {
            throw UnsupportedMessageException.create();
        }

        Object offsetObject = PyObjectCallMethodObjArgs.executeUncached(tzInfo, DatetimeModuleBuiltins.T_UTCOFFSET, PNone.NONE);
        if (offsetObject instanceof PNone) {
            throw UnsupportedMessageException.create();
        }
        if (!(offsetObject instanceof PTimeDelta offset)) {
            throw UnsupportedMessageException.create();
        }

        long microseconds = DatetimeModuleBuiltins.utcOffsetToMicroseconds(offset);

        // ignore seconds fraction
        ZoneOffset zoneOffset = ZoneOffset.ofTotalSeconds((int) (microseconds / 1_000_000));
        return ZoneId.ofOffset("UTC", zoneOffset);
    }
    // endregion
}
