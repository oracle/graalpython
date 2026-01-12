/*
 * Copyright (c) 2020, 2026, Oracle and/or its affiliates. All rights reserved.
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

import static com.oracle.graal.python.builtins.objects.cext.capi.NativeCAPISymbol.FUN_INIT_NATIVE_DATETIME;
import static com.oracle.graal.python.builtins.objects.cext.structs.CStructAccess.allocate;
import static com.oracle.graal.python.builtins.objects.cext.structs.CStructAccess.writePtrField;
import static com.oracle.graal.python.nfi2.NativeMemory.free;
import static com.oracle.graal.python.nodes.StringLiterals.T_DATE;
import static com.oracle.graal.python.nodes.StringLiterals.T_DATETIME;
import static com.oracle.graal.python.nodes.StringLiterals.T_TIME;
import static com.oracle.graal.python.runtime.PythonContext.NATIVE_NULL;
import static com.oracle.graal.python.util.PythonUtils.tsLiteral;

import com.oracle.graal.python.builtins.modules.cext.PythonCextBuiltinRegistry;
import com.oracle.graal.python.builtins.objects.capsule.PyCapsule;
import com.oracle.graal.python.builtins.objects.cext.capi.CExtNodes.PCallCapiFunction;
import com.oracle.graal.python.builtins.objects.cext.capi.transitions.CApiTransitions.PythonToNativeNewRefNode;
import com.oracle.graal.python.builtins.objects.cext.structs.CFields;
import com.oracle.graal.python.builtins.objects.cext.structs.CStructs;
import com.oracle.graal.python.builtins.objects.type.PythonManagedClass;
import com.oracle.graal.python.builtins.objects.type.TypeNodes.SetBasicSizeNode;
import com.oracle.graal.python.lib.PyObjectGetAttr;
import com.oracle.graal.python.lib.PyObjectSetAttr;
import com.oracle.graal.python.nodes.statement.AbstractImportNode;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.graal.python.runtime.object.PFactory;
import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.strings.TruffleString;
import com.oracle.truffle.api.strings.TruffleString.Encoding;

/**
 * A class to allocate and initialize C structure {@code PyDateTime_CAPI}.
 *
 * <pre>
 * typedef struct {
 *     PyTypeObject *DateType;
 *     PyTypeObject *DateTimeType;
 *     PyTypeObject *TimeType;
 *     PyTypeObject *DeltaType;
 *     PyTypeObject *TZInfoType;
 *
 *     PyObject *TimeZone_UTC;
 *
 *     PyObject *(*Date_FromDate)(int, int, int, PyTypeObject*);
 *     PyObject *(*DateTime_FromDateAndTime)(int, int, int, int, int, int, int, PyObject*, PyTypeObject*);
 *     PyObject *(*Time_FromTime)(int, int, int, int, PyObject*, PyTypeObject*);
 *     PyObject *(*Delta_FromDelta)(int, int, int, int, PyTypeObject*);
 *     PyObject *(*TimeZone_FromTimeZone)(PyObject *offset, PyObject *name);
 *
 *     PyObject *(*DateTime_FromTimestamp)(PyObject*, PyObject*, PyObject*);
 *     PyObject *(*Date_FromTimestamp)(PyObject*, PyObject*);
 *
 *     PyObject *(*DateTime_FromDateAndTimeAndFold)(int, int, int, int, int, int, int, PyObject*, int, PyTypeObject*);
 *     PyObject *(*Time_FromTimeAndFold)(int, int, int, int, PyObject*, int, PyTypeObject*);
 *
 * } PyDateTime_CAPI
 * </pre>
 */
public abstract class PyDateTimeCAPIWrapper {

    static final TruffleString T_DATETIME_CAPI = tsLiteral("datetime_CAPI");
    static final String J_PYDATETIME_CAPSULE_NAME = "datetime.datetime_CAPI";

    private static final TruffleString T_TIMEDELTA = tsLiteral("timedelta");
    public static final TruffleString T_TZINFO = tsLiteral("tzinfo");
    public static final TruffleString T_DAY = tsLiteral("day");
    public static final TruffleString T_MONTH = tsLiteral("month");
    public static final TruffleString T_YEAR = tsLiteral("year");
    public static final TruffleString T_HOUR = tsLiteral("hour");
    public static final TruffleString T_MINUTE = tsLiteral("minute");
    public static final TruffleString T_SECOND = tsLiteral("second");
    public static final TruffleString T_MICROSECOND = tsLiteral("microsecond");
    public static final TruffleString T_DAYS = tsLiteral("days");
    public static final TruffleString T_SECONDS = tsLiteral("seconds");
    public static final TruffleString T_MICROSECONDS = tsLiteral("microseconds");
    private static final TruffleString T_TIMEZONE = tsLiteral("timezone");
    private static final TruffleString T_UTC = tsLiteral("utc");
    public static final TruffleString T_FROMTIMESTAMP = tsLiteral("fromtimestamp");
    public static final TruffleString T_FOLD = tsLiteral("fold");

    private PyDateTimeCAPIWrapper() {
    }

    public static PyCapsule initWrapper(PythonContext context, CApiContext capiContext) {
        CompilerAsserts.neverPartOfCompilation();

        PCallCapiFunction callCapiFunction = PCallCapiFunction.getUncached();
        callCapiFunction.call(FUN_INIT_NATIVE_DATETIME);

        Object datetimeModule = AbstractImportNode.importModule(T_DATETIME);
        capiContext.timezoneType = PyObjectGetAttr.executeUncached(datetimeModule, T_TIMEZONE);

        long pointer = allocatePyDatetimeCAPI(datetimeModule);

        long name = context.stringToNativeUtf8Bytes(TruffleString.fromJavaStringUncached(J_PYDATETIME_CAPSULE_NAME, Encoding.US_ASCII), true);
        PyCapsule capsule = PFactory.createCapsuleNativeName(context.getLanguage(), pointer, name);
        PyObjectSetAttr.executeUncached(datetimeModule, T_DATETIME_CAPI, capsule);
        assert PyObjectGetAttr.executeUncached(datetimeModule, T_DATETIME_CAPI) != NATIVE_NULL;
        return capsule;
    }

    /**
     * Deallocates the allocated resources for the {@code PyDateTime_CAPI} structure. Currently,
     * this will only free struct and nothing else. The used objects should be free'd when cleaning
     * all the immortal objects.
     */
    public static void destroyWrapper(PyCapsule capsule) {
        CompilerAsserts.neverPartOfCompilation();
        free(capsule.getPointer());
    }

    private static long allocatePyDatetimeCAPI(Object datetimeModule) {
        PyObjectGetAttr getAttr = PyObjectGetAttr.getUncached();
        PythonToNativeNewRefNode toNativeNode = PythonToNativeNewRefNode.getUncached();

        PythonManagedClass date = (PythonManagedClass) getAttr.execute(null, datetimeModule, T_DATE);
        SetBasicSizeNode.executeUncached(date, CStructs.PyDateTime_Date.size());
        long dateType = toNativeNode.executeLong(date);

        PythonManagedClass dt = (PythonManagedClass) getAttr.execute(null, datetimeModule, T_DATETIME);
        SetBasicSizeNode.executeUncached(dt, CStructs.PyDateTime_DateTime.size());
        long datetimeType = toNativeNode.executeLong(dt);

        PythonManagedClass time = (PythonManagedClass) getAttr.execute(null, datetimeModule, T_TIME);
        SetBasicSizeNode.executeUncached(time, CStructs.PyDateTime_Time.size());
        long timeType = toNativeNode.executeLong(time);

        PythonManagedClass delta = (PythonManagedClass) getAttr.execute(null, datetimeModule, T_TIMEDELTA);
        SetBasicSizeNode.executeUncached(delta, CStructs.PyDateTime_Delta.size());
        long deltaType = toNativeNode.executeLong(delta);

        long tzInfoType = toNativeNode.executeLong(getAttr.execute(null, datetimeModule, T_TZINFO));
        Object timezoneType = getAttr.execute(null, datetimeModule, T_TIMEZONE);
        long timezoneUTC = toNativeNode.executeLong(getAttr.execute(null, timezoneType, T_UTC));

        long mem = allocate(CStructs.PyDateTime_CAPI);
        writePtrField(mem, CFields.PyDateTime_CAPI__DateType, dateType);
        writePtrField(mem, CFields.PyDateTime_CAPI__DateTimeType, datetimeType);
        writePtrField(mem, CFields.PyDateTime_CAPI__TimeType, timeType);
        writePtrField(mem, CFields.PyDateTime_CAPI__DeltaType, deltaType);
        writePtrField(mem, CFields.PyDateTime_CAPI__TZInfoType, tzInfoType);
        writePtrField(mem, CFields.PyDateTime_CAPI__TimeZone_UTC, timezoneUTC);
        writePtrField(mem, CFields.PyDateTime_CAPI__Date_FromDate, PythonCextBuiltinRegistry.GraalPyPrivate_DateTimeCAPI_Date_FromDate.getNativePointer());
        writePtrField(mem, CFields.PyDateTime_CAPI__DateTime_FromDateAndTime, PythonCextBuiltinRegistry.GraalPyPrivate_DateTimeCAPI_DateTime_FromDateAndTime.getNativePointer());
        writePtrField(mem, CFields.PyDateTime_CAPI__Time_FromTime, PythonCextBuiltinRegistry.GraalPyPrivate_DateTimeCAPI_Time_FromTime.getNativePointer());
        writePtrField(mem, CFields.PyDateTime_CAPI__Delta_FromDelta, PythonCextBuiltinRegistry.GraalPyPrivate_DateTimeCAPI_Delta_FromDelta.getNativePointer());
        writePtrField(mem, CFields.PyDateTime_CAPI__TimeZone_FromTimeZone, PythonCextBuiltinRegistry.GraalPyPrivate_DateTimeCAPI_TimeZone_FromTimeZone.getNativePointer());
        writePtrField(mem, CFields.PyDateTime_CAPI__DateTime_FromTimestamp, PythonCextBuiltinRegistry.GraalPyPrivate_DateTimeCAPI_DateTime_FromTimestamp.getNativePointer());
        writePtrField(mem, CFields.PyDateTime_CAPI__Date_FromTimestamp, PythonCextBuiltinRegistry.GraalPyPrivate_DateTimeCAPI_Date_FromTimestamp.getNativePointer());
        writePtrField(mem, CFields.PyDateTime_CAPI__DateTime_FromDateAndTimeAndFold,
                        PythonCextBuiltinRegistry.GraalPyPrivate_DateTimeCAPI_DateTime_FromDateAndTimeAndFold.getNativePointer());
        writePtrField(mem, CFields.PyDateTime_CAPI__Time_FromTimeAndFold, PythonCextBuiltinRegistry.GraalPyPrivate_DateTimeCAPI_Time_FromTimeAndFold.getNativePointer());

        return mem;
    }
}
