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

import static com.oracle.graal.python.builtins.objects.cext.capi.NativeCAPISymbol.FUN_GET_DATETIME_DATETIME_BASICSIZE;
import static com.oracle.graal.python.builtins.objects.cext.capi.NativeCAPISymbol.FUN_GET_DATETIME_DATE_BASICSIZE;
import static com.oracle.graal.python.builtins.objects.cext.capi.NativeCAPISymbol.FUN_GET_DATETIME_DELTA_BASICSIZE;
import static com.oracle.graal.python.builtins.objects.cext.capi.NativeCAPISymbol.FUN_GET_DATETIME_TIME_BASICSIZE;
import static com.oracle.graal.python.builtins.objects.cext.capi.NativeCAPISymbol.FUN_SET_PY_DATETIME_IDS;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.T___BASICSIZE__;
import static com.oracle.graal.python.nodes.StringLiterals.T_DATE;
import static com.oracle.graal.python.nodes.StringLiterals.T_DATETIME;
import static com.oracle.graal.python.nodes.StringLiterals.T_TIME;
import static com.oracle.graal.python.util.PythonUtils.tsLiteral;

import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.objects.cext.capi.CExtNodes.AllToJavaNode;
import com.oracle.graal.python.builtins.objects.cext.capi.CExtNodes.PCallCapiFunction;
import com.oracle.graal.python.builtins.objects.cext.capi.CExtNodes.ToJavaNode;
import com.oracle.graal.python.builtins.objects.cext.capi.CExtNodes.ToNewRefNode;
import com.oracle.graal.python.builtins.objects.cext.capi.CExtNodes.ToSulongNode;
import com.oracle.graal.python.builtins.objects.cext.capi.CExtNodes.TransformExceptionToNativeNode;
import com.oracle.graal.python.builtins.objects.cext.capi.DynamicObjectNativeWrapper.ToNativeOtherNode;
import com.oracle.graal.python.builtins.objects.function.PKeyword;
import com.oracle.graal.python.lib.PyObjectGetAttr;
import com.oracle.graal.python.lib.PyObjectLookupAttr;
import com.oracle.graal.python.lib.PyObjectSetAttr;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.argument.keywords.ExpandKeywordStarargsNodeGen;
import com.oracle.graal.python.nodes.argument.positional.ExecutePositionalStarargsNodeGen;
import com.oracle.graal.python.nodes.call.special.CallVarargsMethodNode;
import com.oracle.graal.python.nodes.statement.AbstractImportNode;
import com.oracle.graal.python.runtime.GilNode;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.graal.python.runtime.interop.InteropArray;
import com.oracle.graal.python.runtime.object.PythonObjectFactory;
import com.oracle.graal.python.util.PythonUtils;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Exclusive;
import com.oracle.truffle.api.interop.ArityException;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.UnknownIdentifierException;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.interop.UnsupportedTypeException;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.profiles.ConditionProfile;
import com.oracle.truffle.api.strings.TruffleString;
import com.oracle.truffle.llvm.spi.NativeTypeLibrary;

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
@ExportLibrary(value = NativeTypeLibrary.class, useForAOT = false)
public final class PyDateTimeCAPIWrapper extends PythonNativeWrapper {

    static final TruffleString T_DATETIME_CAPI = tsLiteral("datetime_CAPI");
    static final TruffleString T_PYDATETIME_CAPSULE_NAME = tsLiteral("datetime.datetime_CAPI");

    private static final String J_DATE_TYPE = "DateType";
    private static final String J_DATETIME_TYPE = "DateTimeType";
    private static final String J_TIME_TYPE = "TimeType";
    private static final String J_DELTA_TYPE = "DeltaType";
    private static final String J_TZ_INFO_TYPE = "TZInfoType";

    private static final String J_TIMEZONE_UTC = "TimeZone_UTC";

    private static final String J_DATE_FROM_DATE = "Date_FromDate";
    private static final String J_DATETIME_FROM_DATE_AND_TIME = "DateTime_FromDateAndTime";
    private static final String J_TIME_FROM_TIME = "Time_FromTime";
    private static final String J_DELTA_FROM_DELTA = "Delta_FromDelta";
    private static final String J_TIMEZONE_FROM_TIMEZONE = "TimeZone_FromTimeZone";
    private static final String J_DATETIME_FROM_TIMESTAMP = "DateTime_FromTimestamp";
    private static final String J_DATE_FROM_TIMESTAMP = "Date_FromTimestamp";
    private static final String J_TIME_FROM_TIME_AND_FOLD = "Time_FromTimeAndFold";
    private static final String J_DATETIME_FROM_DATE_AND_TIME_AND_FOLD = "DateTime_FromDateAndTimeAndFold";

    private static final TruffleString T_TIMEDELTA = tsLiteral("timedelta");
    private static final TruffleString T_TZINFO = tsLiteral("tzinfo");
    private static final TruffleString T_TIMEZONE = tsLiteral("timezone");
    private static final TruffleString T_UTC = tsLiteral("utc");
    private static final TruffleString T_FROMTIMESTAMP = tsLiteral("fromtimestamp");
    private static final TruffleString T_FOLD = tsLiteral("fold");

    // IMPORTANT: if you modify this array, also adopt INVOCABLE_MEMBER_START_IDX
    @CompilationFinal(dimensions = 1) private static final String[] MEMBERS = {J_DATE_TYPE, J_DATETIME_TYPE, J_TIME_TYPE, J_DELTA_TYPE, J_TZ_INFO_TYPE, J_TIMEZONE_UTC,
                    J_DATE_FROM_DATE, J_DATETIME_FROM_DATE_AND_TIME, J_TIME_FROM_TIME, J_DELTA_FROM_DELTA, J_TIMEZONE_FROM_TIMEZONE,
                    J_DATETIME_FROM_TIMESTAMP, J_DATE_FROM_TIMESTAMP, J_DATETIME_FROM_DATE_AND_TIME_AND_FOLD, J_TIME_FROM_TIME_AND_FOLD};

    // IMPORTANT: this is the index of the first function; keep it in sync with MEMBERS !!
    private static final int INVOCABLE_MEMBER_START_IDX = 6;

    @ExplodeLoop
    private static int indexOf(String member) {
        for (int i = 0; i < MEMBERS.length; i++) {
            if (MEMBERS[i].equals(member)) {
                return i;
            }
        }
        return -1;
    }

    private Object dateType;
    private Object datetimeType;
    private Object timeType;
    private Object deltaType;
    private Object tzInfoType;
    private Object timezoneUTC;

    private Object nativeType;
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

        Object date = getAttr.execute(null, datetime, T_DATE);
        setAttr.execute(null, date, T___BASICSIZE__, callCapiFunction.call(FUN_GET_DATETIME_DATE_BASICSIZE, capiContext.getLLVMLibrary()));
        wrapper.dateType = toSulongNode.execute(date);

        Object dt = getAttr.execute(null, datetime, T_DATETIME);
        setAttr.execute(null, dt, T___BASICSIZE__, callCapiFunction.call(FUN_GET_DATETIME_DATETIME_BASICSIZE, capiContext.getLLVMLibrary()));
        wrapper.datetimeType = toSulongNode.execute(dt);

        Object time = getAttr.execute(null, datetime, T_TIME);
        setAttr.execute(null, time, T___BASICSIZE__, callCapiFunction.call(FUN_GET_DATETIME_TIME_BASICSIZE, capiContext.getLLVMLibrary()));
        wrapper.timeType = toSulongNode.execute(time);

        Object delta = getAttr.execute(null, datetime, T_TIMEDELTA);
        setAttr.execute(null, delta, T___BASICSIZE__, callCapiFunction.call(FUN_GET_DATETIME_DELTA_BASICSIZE, capiContext.getLLVMLibrary()));
        wrapper.deltaType = toSulongNode.execute(delta);

        wrapper.tzInfoType = toSulongNode.execute(getAttr.execute(null, datetime, T_TZINFO));
        Object timezoneType = getAttr.execute(null, datetime, T_TIMEZONE);
        wrapper.timezoneUTC = toSulongNode.execute(getAttr.execute(null, timezoneType, T_UTC));

        wrapper.dateFromDateWrapper = new ConstructorWrapper("(SINT32,SINT32,SINT32,POINTER):POINTER");
        wrapper.datetimeFromDateAndTimeWrapper = new ConstructorWrapper("(SINT32,SINT32,SINT32,SINT32,SINT32,SINT32,SINT32,POINTER,POINTER):POINTER");
        wrapper.timeFromTimeWrapper = new ConstructorWrapper("(SINT32,SINT32,SINT32,SINT32,POINTER,POINTER):POINTER");
        wrapper.deltaFromDeltaWrapper = new DeltaConstructorWrapper("(SINT32,SINT32,SINT32,SINT32,POINTER):POINTER");
        wrapper.timezoneFromTimezoneWrapper = new TimeZoneWrapper(timezoneType, "(POINTER,POINTER):POINTER");
        wrapper.datetimeFromTimestamp = new FromTimestampWrapper("(POINTER,POINTER,POINTER):POINTER");
        wrapper.dateFromTimestamp = new FromTimestampWrapper("(POINTER,POINTER):POINTER");
        wrapper.datetimeFromDateAndTimeAdFoldWrapper = new FoldConstructorWrapper("(SINT32,SINT32,SINT32,SINT32,SINT32,SINT32,SINT32,POINTER,SINT32,POINTER):POINTER");
        wrapper.timeFromTimeAndFold = new FoldConstructorWrapper("(SINT32,SINT32,SINT32,SINT32,POINTER,SINT32,POINTER):POINTER");

        wrapper.nativeType = callCapiFunction.call(FUN_SET_PY_DATETIME_IDS, toSulongNode.execute(wrapper.dateType), toSulongNode.execute(wrapper.datetimeType),
                        toSulongNode.execute(wrapper.timeType), toSulongNode.execute(wrapper.deltaType), toSulongNode.execute(wrapper.tzInfoType));

        setAttr.execute(null, datetime, T_DATETIME_CAPI, PythonObjectFactory.getUncached().createCapsule(wrapper, T_PYDATETIME_CAPSULE_NAME, null));
        assert getAttr.execute(null, datetime, T_DATETIME_CAPI) != PythonContext.get(null).getNativeNull();
    }

    @ExportMessage
    @SuppressWarnings("static-method")
    boolean hasMembers() {
        return true;
    }

    @ExportMessage
    @SuppressWarnings({"static-method", "unused"})
    Object getMembers(boolean includeInternal) {
        return new InteropArray(PythonUtils.EMPTY_STRING_ARRAY);
    }

    @ExportMessage
    @SuppressWarnings("static-method")
    boolean isMemberReadable(String member) {
        return indexOf(member) != -1;
    }

    @ExportMessage
    @SuppressWarnings("static-method")
    boolean isMemberInvocable(String member) {
        return indexOf(member) >= INVOCABLE_MEMBER_START_IDX;
    }

    @ExportMessage
    @SuppressWarnings("static-method")
    boolean isMemberModifiable(String member) {
        return isMemberReadable(member);
    }

    @ExportMessage
    @SuppressWarnings({"static-method", "unused"})
    boolean isMemberInsertable(String member) {
        return false;
    }

    @ExportMessage
    Object readMember(String member,
                    @Exclusive @Cached GilNode gil) throws UnknownIdentifierException {
        boolean mustRelease = gil.acquire();
        try {
            return getMember(member);
        } finally {
            gil.release(mustRelease);
        }
    }

    private Object getMember(String member) throws UnknownIdentifierException {
        switch (member) {
            case J_DATE_TYPE:
                return dateType;
            case J_DATETIME_TYPE:
                return datetimeType;
            case J_TIME_TYPE:
                return timeType;
            case J_DELTA_TYPE:
                return deltaType;
            case J_TZ_INFO_TYPE:
                return tzInfoType;
            case J_TIMEZONE_UTC:
                return timezoneUTC;
            case J_DATE_FROM_DATE:
                return dateFromDateWrapper;
            case J_DATETIME_FROM_DATE_AND_TIME:
                return datetimeFromDateAndTimeWrapper;
            case J_TIME_FROM_TIME:
                return timeFromTimeWrapper;
            case J_DELTA_FROM_DELTA:
                return deltaFromDeltaWrapper;
            case J_DATETIME_FROM_DATE_AND_TIME_AND_FOLD:
                return datetimeFromDateAndTimeAdFoldWrapper;
            case J_TIME_FROM_TIME_AND_FOLD:
                return timeFromTimeAndFold;
            case J_TIMEZONE_FROM_TIMEZONE:
                return timezoneFromTimezoneWrapper;
            case J_DATETIME_FROM_TIMESTAMP:
                return datetimeFromTimestamp;
            case J_DATE_FROM_TIMESTAMP:
                return dateFromTimestamp;
            default:
                CompilerDirectives.transferToInterpreterAndInvalidate();
                throw UnknownIdentifierException.create(member);
        }
    }

    @ExportMessage
    Object invokeMember(String member, Object[] args,
                    @Exclusive @Cached GilNode gil,
                    @Cached AllToJavaNode allToJavaNode,
                    @Cached CallVarargsMethodNode callNode,
                    @Exclusive @Cached ToNewRefNode toSulongNode,
                    @CachedLibrary(limit = "1") InteropLibrary lib,
                    @Cached CExtNodes.PRaiseNativeNode raiseNode,
                    @Cached ConditionProfile profile,
                    @Cached TransformExceptionToNativeNode transformExceptionToNativeNode) throws UnknownIdentifierException {
        boolean mustRelease = gil.acquire();
        try {
            Object m = getMember(member);
            if (profile.profile(m instanceof AbstractWrapper)) {
                return ((AbstractWrapper) m).call(args, allToJavaNode, callNode, toSulongNode);
            } else {
                return lib.execute(m, args);
            }
        } catch (UnsupportedTypeException | UnsupportedMessageException ex) {
            return raiseNode.raise(null, PythonContext.get(raiseNode).getNativeNull(), PythonBuiltinClassType.TypeError, ErrorMessages.CALLING_NATIVE_FUNC_FAILED, member, ex);
        } catch (ArityException ex) {
            return raiseNode.raise(null, PythonContext.get(raiseNode).getNativeNull(), PythonBuiltinClassType.TypeError, ErrorMessages.CALLING_NATIVE_FUNC_EXPECTED_ARGS, member,
                            ex.getExpectedMinArity(), ex.getActualArity());
        } catch (PException e) {
            transformExceptionToNativeNode.execute(null, e);
            return toSulongNode.execute(PythonContext.get(gil).getNativeNull());
        } finally {
            gil.release(mustRelease);
        }
    }

    @ExportMessage
    void writeMember(String member, Object value,
                    @Exclusive @Cached ToJavaNode toJavaNode,
                    @Exclusive @Cached GilNode gil) throws UnknownIdentifierException {
        boolean mustRelease = gil.acquire();
        try {
            switch (member) {
                case J_DATE_TYPE:
                    dateType = toJavaNode.execute(value);
                    break;
                case J_DATETIME_TYPE:
                    datetimeType = toJavaNode.execute(value);
                    break;
                case J_TIME_TYPE:
                    timeType = toJavaNode.execute(value);
                    break;
                case J_DELTA_TYPE:
                    deltaType = toJavaNode.execute(value);
                    break;
                case J_TZ_INFO_TYPE:
                    tzInfoType = toJavaNode.execute(value);
                    break;
                case J_TIMEZONE_UTC:
                    timezoneUTC = toJavaNode.execute(value);
                    break;
                case J_DATE_FROM_DATE:
                    dateFromDateWrapper = value;
                    break;
                case J_DATETIME_FROM_DATE_AND_TIME:
                    datetimeFromDateAndTimeWrapper = value;
                    break;
                case J_TIME_FROM_TIME:
                    timeFromTimeWrapper = value;
                    break;
                case J_DELTA_FROM_DELTA:
                    deltaFromDeltaWrapper = value;
                    break;
                case J_DATETIME_FROM_DATE_AND_TIME_AND_FOLD:
                    datetimeFromDateAndTimeAdFoldWrapper = value;
                    break;
                case J_TIME_FROM_TIME_AND_FOLD:
                    timeFromTimeAndFold = value;
                    break;
                case J_TIMEZONE_FROM_TIMEZONE:
                    timezoneFromTimezoneWrapper = value;
                    break;
                case J_DATETIME_FROM_TIMESTAMP:
                    datetimeFromTimestamp = value;
                    break;
                case J_DATE_FROM_TIMESTAMP:
                    dateFromTimestamp = value;
                    break;
                default:
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    throw UnknownIdentifierException.create(member);
            }
        } finally {
            gil.release(mustRelease);
        }
    }

    @ExportMessage
    @SuppressWarnings("static-method")
    boolean hasNativeType() {
        return true;
    }

    @ExportMessage
    Object getNativeType() {
        return nativeType;
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
    protected void toNative(
                    @Cached ToNativeOtherNode toNativeNode) {
        toNativeNode.execute(this, NativeCAPISymbol.FUN_PYTRUFFLE_ALLOCATE_DATETIME_API);
    }

    @ExportLibrary(InteropLibrary.class)
    abstract static class AbstractWrapper extends PyProcsWrapper {

        private final String signature;

        public AbstractWrapper(String signature) {
            super(null);
            this.signature = signature;
        }

        protected abstract Object call(Object[] args, AllToJavaNode allToJavaNode, CallVarargsMethodNode callNode, ToNewRefNode toSulongNode);

        @ExportMessage
        protected Object execute(Object[] args,
                        @Cached ToNewRefNode toSulongNode,
                        @Cached AllToJavaNode allToJavaNode,
                        @Cached CallVarargsMethodNode callNode,
                        @Cached TransformExceptionToNativeNode transformExceptionToNativeNode,
                        @Exclusive @Cached GilNode gil) {
            boolean mustRelease = gil.acquire();
            try {
                return call(args, allToJavaNode, callNode, toSulongNode);
            } catch (PException e) {
                transformExceptionToNativeNode.execute(null, e);
                return toSulongNode.execute(PythonContext.get(gil).getNativeNull());
            } finally {
                gil.release(mustRelease);
            }
        }

        @Override
        protected String getSignature() {
            return signature;
        }
    }

    static class ConstructorWrapper extends AbstractWrapper {
        public ConstructorWrapper(String signature) {
            super(signature);
        }

        @Override
        protected Object call(Object[] args, AllToJavaNode allToJavaNode, CallVarargsMethodNode callNode, ToNewRefNode toSulongNode) {
            Object[] convertedArgs = allToJavaNode.execute(args);
            Object[] callArgs = PythonUtils.arrayCopyOf(convertedArgs, convertedArgs.length - 1);
            Object type = convertedArgs[convertedArgs.length - 1];
            return toSulongNode.execute(callNode.execute(null, type, callArgs, PKeyword.EMPTY_KEYWORDS));
        }
    }

    static class DeltaConstructorWrapper extends AbstractWrapper {
        public DeltaConstructorWrapper(String signature) {
            super(signature);
        }

        @Override
        protected Object call(Object[] args, AllToJavaNode allToJavaNode, CallVarargsMethodNode callNode, ToNewRefNode toSulongNode) {
            Object[] convertedArgs = allToJavaNode.execute(args);
            // see _datetime.c/new_delta_ex() - construct delta from days, seconds, microseconds
            Object[] callArgs = PythonUtils.arrayCopyOf(convertedArgs, 3);
            Object type = convertedArgs[convertedArgs.length - 1];
            return toSulongNode.execute(callNode.execute(null, type, callArgs, PKeyword.EMPTY_KEYWORDS));
        }
    }

    static class FoldConstructorWrapper extends AbstractWrapper {
        public FoldConstructorWrapper(String signature) {
            super(signature);
        }

        @Override
        protected Object call(Object[] args, AllToJavaNode allToJavaNode, CallVarargsMethodNode callNode, ToNewRefNode toSulongNode) {
            Object[] convertedArgs = allToJavaNode.execute(args);
            Object[] callArgs = PythonUtils.arrayCopyOf(convertedArgs, convertedArgs.length - 2);
            Object type = convertedArgs[convertedArgs.length - 1];
            Object fold = convertedArgs[convertedArgs.length - 2];
            return toSulongNode.execute(callNode.execute(null, type, callArgs, new PKeyword[]{new PKeyword(T_FOLD, fold)}));
        }
    }

    static class TimeZoneWrapper extends AbstractWrapper {
        private final Object timezoneType;

        public TimeZoneWrapper(Object timezoneType, String signature) {
            super(signature);
            this.timezoneType = timezoneType;
        }

        @Override
        protected Object call(Object[] args, AllToJavaNode allToJavaNode, CallVarargsMethodNode callNode, ToNewRefNode toSulongNode) {
            Object[] convertedArgs = allToJavaNode.execute(args);
            return toSulongNode.execute(callNode.execute(null, timezoneType, convertedArgs, PKeyword.EMPTY_KEYWORDS));
        }
    }

    @ExportLibrary(InteropLibrary.class)
    static class FromTimestampWrapper extends AbstractWrapper {
        public FromTimestampWrapper(String signature) {
            super(signature);
        }

        @ExportMessage
        protected Object execute(Object[] args,
                        @Cached ToSulongNode toSulongNode,
                        @Cached CallVarargsMethodNode callNode,
                        @Cached AllToJavaNode allToJavaNode,
                        @Cached PyObjectLookupAttr lookupNode,
                        @Cached CExtNodes.AddRefCntNode incRefNode,
                        @Cached TransformExceptionToNativeNode transformExceptionToNativeNode,
                        @Exclusive @Cached GilNode gil) {
            boolean mustRelease = gil.acquire();
            try {
                Object[] convertedArgs = allToJavaNode.execute(args);
                Object type = convertedArgs[0];

                Object[] callArgs = ExecutePositionalStarargsNodeGen.getUncached().executeWith(null, convertedArgs[1]);
                PKeyword[] kwds;
                if (convertedArgs.length > 2) {
                    kwds = ExpandKeywordStarargsNodeGen.getUncached().execute(convertedArgs[2]);
                } else {
                    kwds = PKeyword.EMPTY_KEYWORDS;
                }
                Object fromTSCallable = lookupNode.execute(null, type, T_FROMTIMESTAMP);
                return incRefNode.inc(toSulongNode.execute(callNode.execute(null, fromTSCallable, callArgs, kwds)));
            } catch (PException e) {
                transformExceptionToNativeNode.execute(null, e);
                return toSulongNode.execute(PythonContext.get(gil).getNativeNull());
            } finally {
                gil.release(mustRelease);
            }
        }

        @Override
        protected Object call(Object[] args, AllToJavaNode allToJavaNode, CallVarargsMethodNode callNode, ToNewRefNode toSulongNode) {
            throw new IllegalStateException();
        }
    }
}
