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

import com.oracle.graal.python.builtins.modules.cext.PythonCextBuiltinRegistry;
import com.oracle.graal.python.builtins.objects.capsule.PyCapsule;
import com.oracle.graal.python.builtins.objects.cext.capi.CExtNodes.PCallCapiFunction;
import com.oracle.graal.python.builtins.objects.cext.capi.transitions.CApiTransitions.PythonToNativeNewRefNode;
import com.oracle.graal.python.builtins.objects.cext.capi.transitions.CApiTransitionsFactory.PythonToNativeNewRefNodeGen;
import com.oracle.graal.python.builtins.objects.cext.structs.CFields;
import com.oracle.graal.python.builtins.objects.cext.structs.CStructAccess;
import com.oracle.graal.python.builtins.objects.cext.structs.CStructAccessFactory;
import com.oracle.graal.python.builtins.objects.cext.structs.CStructs;
import com.oracle.graal.python.builtins.objects.type.PythonManagedClass;
import com.oracle.graal.python.builtins.objects.type.TypeNodes.SetBasicSizeNode;
import com.oracle.graal.python.lib.PyObjectGetAttr;
import com.oracle.graal.python.lib.PyObjectSetAttr;
import com.oracle.graal.python.nodes.statement.AbstractImportNode;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.strings.TruffleString;

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
    static final TruffleString T_PYDATETIME_CAPSULE_NAME = tsLiteral("datetime.datetime_CAPI");

    private static final TruffleString T_TIMEDELTA = tsLiteral("timedelta");
    public static final TruffleString T_TZINFO = tsLiteral("tzinfo");
    private static final TruffleString T_TIMEZONE = tsLiteral("timezone");
    private static final TruffleString T_UTC = tsLiteral("utc");
    public static final TruffleString T_FROMTIMESTAMP = tsLiteral("fromtimestamp");
    public static final TruffleString T_FOLD = tsLiteral("fold");

    private PyDateTimeCAPIWrapper() {
    }

    public static PyCapsule initWrapper(PythonContext context, CApiContext capiContext) {
        CompilerAsserts.neverPartOfCompilation();

        PCallCapiFunction callCapiFunction = PCallCapiFunction.getUncached();
        callCapiFunction.call(FUN_SET_PY_DATETIME_TYPES);

        Object datetimeModule = AbstractImportNode.importModule(T_DATETIME);
        capiContext.timezoneType = PyObjectGetAttr.executeUncached(datetimeModule, T_TIMEZONE);

        Object pointerObject = allocatePyDatetimeCAPI(datetimeModule);

        PyCapsule capsule = context.factory().createCapsule(pointerObject, T_PYDATETIME_CAPSULE_NAME, null);
        PyObjectSetAttr.executeUncached(datetimeModule, T_DATETIME_CAPI, capsule);
        assert PyObjectGetAttr.executeUncached(datetimeModule, T_DATETIME_CAPI) != context.getNativeNull();
        return capsule;
    }

    /**
     * Deallocates the allocated resources for the {@code PyDateTime_CAPI} structure. Currently,
     * this will only free struct and nothing else. The used objects should be free'd when cleaning
     * all the immortal objects.
     */
    public static void destroyWrapper(PyCapsule capsule) {
        CompilerAsserts.neverPartOfCompilation();
        CStructAccess.FreeNode.executeUncached(capsule.getPointer());
    }

    private static Object allocatePyDatetimeCAPI(Object datetimeModule) {
        CStructAccess.AllocateNode allocNode = CStructAccessFactory.AllocateNodeGen.getUncached();
        CStructAccess.WritePointerNode writePointerNode = CStructAccessFactory.WritePointerNodeGen.getUncached();

        PyObjectGetAttr getAttr = PyObjectGetAttr.getUncached();
        PythonToNativeNewRefNode toNativeNode = PythonToNativeNewRefNodeGen.getUncached();

        PythonManagedClass date = (PythonManagedClass) getAttr.execute(null, datetimeModule, T_DATE);
        SetBasicSizeNode.executeUncached(date, CStructs.PyDateTime_Date.size());
        Object dateType = toNativeNode.execute(date);

        PythonManagedClass dt = (PythonManagedClass) getAttr.execute(null, datetimeModule, T_DATETIME);
        SetBasicSizeNode.executeUncached(dt, CStructs.PyDateTime_DateTime.size());
        Object datetimeType = toNativeNode.execute(dt);

        PythonManagedClass time = (PythonManagedClass) getAttr.execute(null, datetimeModule, T_TIME);
        SetBasicSizeNode.executeUncached(time, CStructs.PyDateTime_Time.size());
        Object timeType = toNativeNode.execute(time);

        PythonManagedClass delta = (PythonManagedClass) getAttr.execute(null, datetimeModule, T_TIMEDELTA);
        SetBasicSizeNode.executeUncached(delta, CStructs.PyDateTime_Delta.size());
        Object deltaType = toNativeNode.execute(delta);

        Object tzInfoType = toNativeNode.execute(getAttr.execute(null, datetimeModule, T_TZINFO));
        Object timezoneType = getAttr.execute(null, datetimeModule, T_TIMEZONE);
        Object timezoneUTC = toNativeNode.execute(getAttr.execute(null, timezoneType, T_UTC));

        Object mem = allocNode.alloc(CStructs.PyDateTime_CAPI);
        writePointerNode.write(mem, CFields.PyDateTime_CAPI__DateType, dateType);
        writePointerNode.write(mem, CFields.PyDateTime_CAPI__DateTimeType, datetimeType);
        writePointerNode.write(mem, CFields.PyDateTime_CAPI__TimeType, timeType);
        writePointerNode.write(mem, CFields.PyDateTime_CAPI__DeltaType, deltaType);
        writePointerNode.write(mem, CFields.PyDateTime_CAPI__TZInfoType, tzInfoType);
        writePointerNode.write(mem, CFields.PyDateTime_CAPI__TimeZone_UTC, timezoneUTC);
        writePointerNode.write(mem, CFields.PyDateTime_CAPI__Date_FromDate, PythonCextBuiltinRegistry.PyTruffleDateTimeCAPI_Date_FromDate);
        writePointerNode.write(mem, CFields.PyDateTime_CAPI__DateTime_FromDateAndTime, PythonCextBuiltinRegistry.PyTruffleDateTimeCAPI_DateTime_FromDateAndTime);
        writePointerNode.write(mem, CFields.PyDateTime_CAPI__Time_FromTime, PythonCextBuiltinRegistry.PyTruffleDateTimeCAPI_Time_FromTime);
        writePointerNode.write(mem, CFields.PyDateTime_CAPI__Delta_FromDelta, PythonCextBuiltinRegistry.PyTruffleDateTimeCAPI_Delta_FromDelta);
        writePointerNode.write(mem, CFields.PyDateTime_CAPI__TimeZone_FromTimeZone, PythonCextBuiltinRegistry.PyTruffleDateTimeCAPI_TimeZone_FromTimeZone);
        writePointerNode.write(mem, CFields.PyDateTime_CAPI__DateTime_FromTimestamp, PythonCextBuiltinRegistry.PyTruffleDateTimeCAPI_DateTime_FromTimestamp);
        writePointerNode.write(mem, CFields.PyDateTime_CAPI__Date_FromTimestamp, PythonCextBuiltinRegistry.PyTruffleDateTimeCAPI_Date_FromTimestamp);
        writePointerNode.write(mem, CFields.PyDateTime_CAPI__DateTime_FromDateAndTimeAndFold, PythonCextBuiltinRegistry.PyTruffleDateTimeCAPI_DateTime_FromDateAndTimeAndFold);
        writePointerNode.write(mem, CFields.PyDateTime_CAPI__Time_FromTimeAndFold, PythonCextBuiltinRegistry.PyTruffleDateTimeCAPI_Time_FromTimeAndFold);

        return mem;
    }
}
