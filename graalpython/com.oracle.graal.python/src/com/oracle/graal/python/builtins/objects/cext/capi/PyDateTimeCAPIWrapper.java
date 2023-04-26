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
package com.oracle.graal.python.builtins.objects.cext.capi;

import static com.oracle.graal.python.builtins.objects.cext.capi.NativeCAPISymbol.FUN_SET_PY_DATETIME_TYPES;
import static com.oracle.graal.python.nodes.StringLiterals.T_DATE;
import static com.oracle.graal.python.nodes.StringLiterals.T_DATETIME;
import static com.oracle.graal.python.nodes.StringLiterals.T_TIME;
import static com.oracle.graal.python.util.PythonUtils.tsLiteral;

import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.modules.cext.PythonCextBuiltinRegistry;
import com.oracle.graal.python.builtins.objects.cext.capi.CExtNodes.PCallCapiFunction;
import com.oracle.graal.python.builtins.objects.cext.capi.CExtNodes.ToSulongNode;
import com.oracle.graal.python.builtins.objects.cext.capi.transitions.CApiTransitions;
import com.oracle.graal.python.builtins.objects.cext.structs.CFields;
import com.oracle.graal.python.builtins.objects.cext.structs.CStructAccess;
import com.oracle.graal.python.builtins.objects.cext.structs.CStructs;
import com.oracle.graal.python.builtins.objects.type.PythonManagedClass;
import com.oracle.graal.python.builtins.objects.type.TypeNodes.SetBasicSizeNode;
import com.oracle.graal.python.lib.PyObjectGetAttr;
import com.oracle.graal.python.lib.PyObjectSetAttr;
import com.oracle.graal.python.nodes.statement.AbstractImportNode;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.graal.python.runtime.object.PythonObjectFactory;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.api.strings.TruffleString;

/**
 * This wrapper emulates the following native API:
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
@ExportLibrary(InteropLibrary.class)
public final class PyDateTimeCAPIWrapper extends PythonNativeWrapper {

    static final TruffleString T_DATETIME_CAPI = tsLiteral("datetime_CAPI");
    static final TruffleString T_PYDATETIME_CAPSULE_NAME = tsLiteral("datetime.datetime_CAPI");

    private static final TruffleString T_TIMEDELTA = tsLiteral("timedelta");
    public static final TruffleString T_TZINFO = tsLiteral("tzinfo");
    private static final TruffleString T_TIMEZONE = tsLiteral("timezone");
    private static final TruffleString T_UTC = tsLiteral("utc");
    public static final TruffleString T_FROMTIMESTAMP = tsLiteral("fromtimestamp");
    public static final TruffleString T_FOLD = tsLiteral("fold");

    private Object dateType;
    private Object datetimeType;
    private Object timeType;
    private Object deltaType;
    private Object tzInfoType;
    private Object timezoneUTC;

    private Object dateFromDateWrapper;
    private Object datetimeFromDateAndTimeWrapper;
    private Object timeFromTimeWrapper;
    private Object deltaFromDeltaWrapper;

    private Object datetimeFromDateAndTimeAdFoldWrapper;
    private Object timeFromTimeAndFold;
    private Object timezoneFromTimezoneWrapper;
    private Object datetimeFromTimestamp;
    private Object dateFromTimestamp;

    public PyDateTimeCAPIWrapper() {
        super(PythonObjectFactory.getUncached().createPythonObject(PythonBuiltinClassType.PythonObject));
    }

    public static void initWrapper(CApiContext capiContext) {
        Object datetime = AbstractImportNode.importModule(T_DATETIME);
        PyDateTimeCAPIWrapper wrapper = new PyDateTimeCAPIWrapper();

        PyObjectGetAttr getAttr = PyObjectGetAttr.getUncached();
        ToSulongNode toSulongNode = ToSulongNode.getUncached();
        PyObjectSetAttr setAttr = PyObjectSetAttr.getUncached();
        PCallCapiFunction callCapiFunction = PCallCapiFunction.getUncached();

        PythonManagedClass date = (PythonManagedClass) getAttr.execute(null, datetime, T_DATE);
        SetBasicSizeNode.executeUncached(date, CStructs.PyDateTime_Date.size());
        wrapper.dateType = toSulongNode.execute(date);

        PythonManagedClass dt = (PythonManagedClass) getAttr.execute(null, datetime, T_DATETIME);
        SetBasicSizeNode.executeUncached(dt, CStructs.PyDateTime_DateTime.size());
        wrapper.datetimeType = toSulongNode.execute(dt);

        PythonManagedClass time = (PythonManagedClass) getAttr.execute(null, datetime, T_TIME);
        SetBasicSizeNode.executeUncached(time, CStructs.PyDateTime_Time.size());
        wrapper.timeType = toSulongNode.execute(time);

        PythonManagedClass delta = (PythonManagedClass) getAttr.execute(null, datetime, T_TIMEDELTA);
        SetBasicSizeNode.executeUncached(delta, CStructs.PyDateTime_Delta.size());
        wrapper.deltaType = toSulongNode.execute(delta);

        wrapper.tzInfoType = toSulongNode.execute(getAttr.execute(null, datetime, T_TZINFO));
        Object timezoneType = capiContext.timezoneType = getAttr.execute(null, datetime, T_TIMEZONE);
        wrapper.timezoneUTC = toSulongNode.execute(getAttr.execute(null, timezoneType, T_UTC));

        wrapper.dateFromDateWrapper = PythonCextBuiltinRegistry.PyTruffleDateTimeCAPI_Date_FromDate;
        wrapper.datetimeFromDateAndTimeWrapper = PythonCextBuiltinRegistry.PyTruffleDateTimeCAPI_DateTime_FromDateAndTime;
        wrapper.timeFromTimeWrapper = PythonCextBuiltinRegistry.PyTruffleDateTimeCAPI_Time_FromTime;
        wrapper.deltaFromDeltaWrapper = PythonCextBuiltinRegistry.PyTruffleDateTimeCAPI_Delta_FromDelta;
        wrapper.timezoneFromTimezoneWrapper = PythonCextBuiltinRegistry.PyTruffleDateTimeCAPI_TimeZone_FromTimeZone;
        wrapper.datetimeFromTimestamp = PythonCextBuiltinRegistry.PyTruffleDateTimeCAPI_DateTime_FromTimestamp;
        wrapper.dateFromTimestamp = PythonCextBuiltinRegistry.PyTruffleDateTimeCAPI_Date_FromTimestamp;
        wrapper.datetimeFromDateAndTimeAdFoldWrapper = PythonCextBuiltinRegistry.PyTruffleDateTimeCAPI_DateTime_FromDateAndTimeAndFold;
        wrapper.timeFromTimeAndFold = PythonCextBuiltinRegistry.PyTruffleDateTimeCAPI_Time_FromTimeAndFold;

        callCapiFunction.call(FUN_SET_PY_DATETIME_TYPES);

        setAttr.execute(null, datetime, T_DATETIME_CAPI, PythonObjectFactory.getUncached().createCapsule(wrapper, T_PYDATETIME_CAPSULE_NAME, null));
        assert getAttr.execute(null, datetime, T_DATETIME_CAPI) != PythonContext.get(null).getNativeNull();
    }

    @ExportMessage
    boolean isPointer() {
        return isNative();
    }

    @ExportMessage
    long asPointer() {
        return getNativePointer();
    }

    @ExportMessage
    @TruffleBoundary
    protected void toNative(
                    @Cached CStructAccess.AllocateNode allocNode,
                    @Cached CStructAccess.WritePointerNode writePointerNode,
                    @CachedLibrary(limit = "3") InteropLibrary lib) {
        if (!isNative()) {
            setRefCount(Long.MAX_VALUE / 2); // make this object immortal

            Object mem = allocNode.alloc(CStructs.PyDateTime_CAPI);
            writePointerNode.write(mem, CFields.PyDateTime_CAPI__DateType, dateType);
            writePointerNode.write(mem, CFields.PyDateTime_CAPI__DateTimeType, datetimeType);
            writePointerNode.write(mem, CFields.PyDateTime_CAPI__TimeType, timeType);
            writePointerNode.write(mem, CFields.PyDateTime_CAPI__DeltaType, deltaType);
            writePointerNode.write(mem, CFields.PyDateTime_CAPI__TZInfoType, tzInfoType);
            writePointerNode.write(mem, CFields.PyDateTime_CAPI__TimeZone_UTC, timezoneUTC);
            writePointerNode.write(mem, CFields.PyDateTime_CAPI__Date_FromDate, dateFromDateWrapper);
            writePointerNode.write(mem, CFields.PyDateTime_CAPI__DateTime_FromDateAndTime, datetimeFromDateAndTimeWrapper);
            writePointerNode.write(mem, CFields.PyDateTime_CAPI__Time_FromTime, timeFromTimeWrapper);
            writePointerNode.write(mem, CFields.PyDateTime_CAPI__Delta_FromDelta, deltaFromDeltaWrapper);
            writePointerNode.write(mem, CFields.PyDateTime_CAPI__TimeZone_FromTimeZone, timezoneFromTimezoneWrapper);
            writePointerNode.write(mem, CFields.PyDateTime_CAPI__DateTime_FromTimestamp, datetimeFromTimestamp);
            writePointerNode.write(mem, CFields.PyDateTime_CAPI__Date_FromTimestamp, dateFromTimestamp);
            writePointerNode.write(mem, CFields.PyDateTime_CAPI__DateTime_FromDateAndTimeAndFold, datetimeFromDateAndTimeAdFoldWrapper);
            writePointerNode.write(mem, CFields.PyDateTime_CAPI__Time_FromTimeAndFold, timeFromTimeAndFold);

            long ptr = coerceToLong(mem, lib);
            CApiTransitions.firstToNative(this, ptr);
        }
    }
}
