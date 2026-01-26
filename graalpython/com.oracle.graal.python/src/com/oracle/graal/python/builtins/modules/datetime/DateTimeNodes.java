/*
 * Copyright (c) 2025, 2026, Oracle and/or its affiliates. All rights reserved.
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

import static com.oracle.graal.python.builtins.PythonBuiltinClassType.TypeError;
import static com.oracle.graal.python.builtins.PythonBuiltinClassType.ValueError;
import static com.oracle.graal.python.builtins.modules.datetime.DatetimeModuleBuiltins.MAX_YEAR;
import static com.oracle.graal.python.builtins.modules.datetime.DatetimeModuleBuiltins.MIN_YEAR;
import static com.oracle.graal.python.util.PythonUtils.tsLiteral;

import java.time.YearMonth;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.cext.PythonAbstractNativeObject;
import com.oracle.graal.python.builtins.objects.cext.capi.CExtNodes;
import com.oracle.graal.python.builtins.objects.cext.capi.ExternalFunctionNodes;
import com.oracle.graal.python.builtins.objects.cext.capi.NativeCAPISymbol;
import com.oracle.graal.python.builtins.objects.cext.capi.transitions.CApiTransitions;
import com.oracle.graal.python.builtins.objects.cext.structs.CFields;
import com.oracle.graal.python.builtins.objects.cext.structs.CStructAccess;
import com.oracle.graal.python.builtins.objects.function.PKeyword;
import com.oracle.graal.python.builtins.objects.type.TypeNodes;
import com.oracle.graal.python.lib.PyLongAsIntNode;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PGuards;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.call.CallNode;
import com.oracle.graal.python.nodes.object.BuiltinClassProfiles;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.GenerateCached;
import com.oracle.truffle.api.dsl.GenerateInline;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.object.Shape;
import com.oracle.truffle.api.strings.TruffleString;

public class DateTimeNodes {

    /** Create a new datetime instance */
    @GenerateUncached
    @GenerateInline
    @GenerateCached(false)
    public abstract static class NewNode extends Node {

        public abstract Object execute(Node inliningTarget, Object cls, Object yearObject, Object monthObject, Object dayObject, Object hour, Object minute, Object second, Object microsecond,
                        Object tzInfo, Object fold);

        @Specialization
        static Object newDateTime(VirtualFrame frame, Node inliningTarget, Object cls, Object yearObject, Object monthObject, Object dayObject, Object hourObject, Object minuteObject,
                        Object secondObject, Object microsecondObject, Object tzInfoObject, Object foldObject,
                        @Cached PyLongAsIntNode asIntNode,
                        @Cached DateTimeNodes.NewUnsafeNode newUnsafeNode,
                        @Cached TzInfoNodes.TzInfoCheckNode tzInfoCheckNode,
                        @Cached PRaiseNode raiseNode) {
            int year = asIntNode.execute(frame, inliningTarget, yearObject);
            int month = asIntNode.execute(frame, inliningTarget, monthObject);
            int day = asIntNode.execute(frame, inliningTarget, dayObject);

            validateDateComponents(inliningTarget, raiseNode, year, month, day);

            final int hour, minute, second, microsecond, fold;
            final Object tzInfo;

            if (hourObject == PNone.NO_VALUE) {
                hour = 0;
            } else {
                hour = asIntNode.execute(frame, inliningTarget, hourObject);
            }

            if (minuteObject == PNone.NO_VALUE) {
                minute = 0;
            } else {
                minute = asIntNode.execute(frame, inliningTarget, minuteObject);
            }

            if (secondObject == PNone.NO_VALUE) {
                second = 0;
            } else {
                second = asIntNode.execute(frame, inliningTarget, secondObject);
            }

            if (microsecondObject == PNone.NO_VALUE) {
                microsecond = 0;
            } else {
                microsecond = asIntNode.execute(frame, inliningTarget, microsecondObject);
            }

            // both PNone.NO_VALUE and PNone.NONE are acceptable
            if (tzInfoObject instanceof PNone) {
                tzInfo = null;
            } else {
                tzInfo = tzInfoObject;
            }

            if (foldObject == PNone.NO_VALUE) {
                fold = 0;
            } else {
                fold = asIntNode.execute(frame, inliningTarget, foldObject);
            }

            validateTimeComponents(inliningTarget, raiseNode, hour, minute, second, microsecond, tzInfo, fold, tzInfoCheckNode);

            return newUnsafeNode.execute(inliningTarget,
                            cls,
                            year,
                            month,
                            day,
                            hour,
                            minute,
                            second,
                            microsecond,
                            tzInfo,
                            fold);
        }

        private static void validateDateComponents(Node inliningTarget, PRaiseNode raiseNode, long year, long month, long day) {
            if (year < MIN_YEAR || year > MAX_YEAR) {
                throw raiseNode.raise(inliningTarget, ValueError, ErrorMessages.YEAR_D_IS_OUT_OF_RANGE, year);
            }

            if (month <= 0 || month >= 13) {
                throw raiseNode.raise(inliningTarget, ValueError, ErrorMessages.MONTH_MUST_BE_IN);
            }

            if (day <= 0 || day > getMaxDayOfMonth((int) year, (int) month)) {
                throw raiseNode.raise(inliningTarget, ValueError, ErrorMessages.DAY_IS_OUT_OF_RANGE_FOR_MONTH);
            }
        }

        private static void validateTimeComponents(Node inliningTarget, PRaiseNode raiseNode, long hour, long minute, long second, long microsecond, Object tzInfo, long fold,
                        TzInfoNodes.TzInfoCheckNode tzInfoCheckNode) {
            if (hour < 0 || hour >= 24) {
                throw raiseNode.raise(inliningTarget, ValueError, ErrorMessages.HOUR_MUST_BE_IN);
            }

            if (minute < 0 || minute >= 60) {
                throw raiseNode.raise(inliningTarget, ValueError, ErrorMessages.MINUTE_MUST_BE_IN);
            }

            if (second < 0 || second >= 60) {
                throw raiseNode.raise(inliningTarget, ValueError, ErrorMessages.SECOND_MUST_BE_IN);
            }

            if (microsecond < 0 || microsecond >= 1_000_000) {
                throw raiseNode.raise(inliningTarget, ValueError, ErrorMessages.MICROSECOND_MUST_BE_IN);
            }

            if (tzInfo != null && !tzInfoCheckNode.execute(inliningTarget, tzInfo)) {
                throw raiseNode.raise(inliningTarget, TypeError, ErrorMessages.TZINFO_ARGUMENT_MUST_BE_NONE_OR_OF_A_TZINFO_SUBCLASS_NOT_TYPE_P, tzInfo);
            }

            if (fold != 0 && fold != 1) {
                throw raiseNode.raise(inliningTarget, ValueError, ErrorMessages.FOLD_MUST_BE_EITHER_0_OR_1);
            }
        }

        @TruffleBoundary
        private static int getMaxDayOfMonth(int year, int month) {
            var date = YearMonth.of(year, month).atEndOfMonth();
            return date.getDayOfMonth();
        }
    }

    @GenerateUncached
    @GenerateInline
    @GenerateCached(false)
    public abstract static class NewUnsafeNode extends Node {

        public abstract Object execute(Node inliningTarget, Object cls, int year, int month, int day, int hour, int minute, int second, int microsecond, Object tzInfoObject, int fold);

        public static NewUnsafeNode getUncached() {
            return DateTimeNodesFactory.NewUnsafeNodeGen.getUncached();
        }

        @Specialization
        static Object newDateTime(Node inliningTarget, Object cls, int year, int month, int day, int hour, int minute, int second, int microsecond, Object tzInfoObject, int fold,
                        @Cached PRaiseNode raiseNode,
                        @Cached TzInfoNodes.TzInfoCheckNode tzInfoCheckNode,
                        @Cached TypeNodes.GetInstanceShape getInstanceShape,
                        @Cached TypeNodes.NeedsNativeAllocationNode needsNativeAllocationNode,
                        @Cached CExtNodes.PCallCapiFunction callCapiFunction,
                        @Cached ExternalFunctionNodes.DefaultCheckFunctionResultNode checkFunctionResultNode,
                        @Cached CApiTransitions.PythonToNativeNode toNativeNode,
                        @Cached CApiTransitions.NativeToPythonTransferNode fromNativeNode) {
            // create DateTime without thorough validation

            final Object tzInfo;
            if (tzInfoObject instanceof PNone) {
                // both PNone.NO_VALUE and PNone.NONE are acceptable
                tzInfo = null;
            } else {
                tzInfo = tzInfoObject;
            }

            if (tzInfo != null && !tzInfoCheckNode.execute(inliningTarget, tzInfo)) {
                throw raiseNode.raise(inliningTarget, TypeError, ErrorMessages.TZINFO_ARGUMENT_MUST_BE_NONE_OR_OF_A_TZINFO_SUBCLASS_NOT_TYPE_P, tzInfo);
            }

            if (!needsNativeAllocationNode.execute(inliningTarget, cls)) {
                Shape shape = getInstanceShape.execute(cls);
                return new PDateTime(cls, shape, year, month, day, hour, minute, second, microsecond, tzInfo, fold);
            } else {
                Object nativeResult = callCapiFunction.call(NativeCAPISymbol.FUN_DATETIME_SUBTYPE_NEW,
                                toNativeNode.execute(cls), year, month, day, hour, minute, second, microsecond, toNativeNode.execute(tzInfo != null ? tzInfo : PNone.NO_VALUE), fold);
                checkFunctionResultNode.execute(PythonContext.get(inliningTarget), NativeCAPISymbol.FUN_DATETIME_SUBTYPE_NEW.getTsName(), nativeResult);
                return fromNativeNode.execute(nativeResult);
            }
        }
    }

    /** Create a new instance of datetime class or its subclass */
    @GenerateUncached
    @GenerateInline
    @GenerateCached(false)
    public abstract static class SubclassNewNode extends Node {

        private static final TruffleString T_FOLD = tsLiteral("fold");

        public abstract Object execute(Node inliningTarget, Object cls, Object yearObject, Object monthObject, Object dayObject, Object hour, Object minute, Object second, Object microsecond,
                        Object tzInfo, Object fold);

        public static SubclassNewNode getUncached() {
            return DateTimeNodesFactory.SubclassNewNodeGen.getUncached();
        }

        @Specialization(guards = {"isBuiltinClass(cls)"})
        static Object newDateTimeBuiltIn(Node inliningTarget, Object cls, Object yearObject, Object monthObject, Object dayObject, Object hourObject, Object minuteObject, Object secondObject,
                        Object microsecondObject, Object tzInfoObject, Object foldObject,
                        @Cached NewNode newNode) {
            return newNode.execute(inliningTarget, cls, yearObject, monthObject, dayObject, hourObject, minuteObject, secondObject, microsecondObject, tzInfoObject, foldObject);
        }

        @Fallback
        @TruffleBoundary
        static Object newDateTimeGeneric(Object cls, Object yearObject, Object monthObject, Object dayObject, Object hourObject, Object minuteObject, Object secondObject,
                        Object microsecondObject, Object tzInfoObject, Object foldObject) {
            Object[] arguments = new Object[]{yearObject, monthObject, dayObject, hourObject, minuteObject, secondObject, microsecondObject, tzInfoObject};
            PKeyword foldKeyword = new PKeyword(T_FOLD, foldObject);
            PKeyword[] keywords = new PKeyword[]{foldKeyword};

            return CallNode.executeUncached(cls, arguments, keywords);
        }

        static boolean isBuiltinClass(Object cls) {
            return PGuards.isBuiltinClass(cls, PythonBuiltinClassType.PDateTime);
        }
    }

    @GenerateUncached
    @GenerateInline
    @GenerateCached(false)
    public abstract static class AsManagedDateTimeNode extends Node {

        public abstract PDateTime execute(Node inliningTarget, Object obj);

        public static PDateTime executeUncached(Object obj) {
            return DateTimeNodesFactory.AsManagedDateTimeNodeGen.getUncached().execute(null, obj);
        }

        @Specialization
        static PDateTime asManaged(PDateTime obj) {
            return obj;
        }

        @Specialization(guards = "checkNode.execute(inliningTarget, obj)", limit = "1")
        static PDateTime asManagedNative(@SuppressWarnings("unused") Node inliningTarget, PythonAbstractNativeObject obj,
                        @Bind PythonLanguage language,
                        @SuppressWarnings("unused") @Cached DateTimeCheckNode checkNode,
                        @Cached CStructAccess.ReadByteNode readByteNode,
                        @Cached CStructAccess.ReadObjectNode readObjectNode) {
            int year = getYear(obj, readByteNode);
            int month = getMonth(obj, readByteNode);
            int day = getDay(obj, readByteNode);

            int hour = getHour(obj, readByteNode);
            int minute = getMinute(obj, readByteNode);
            int second = getSecond(obj, readByteNode);
            int microsecond = getMicrosecond(obj, readByteNode);

            Object tzInfo = getTzInfo(obj, readByteNode, readObjectNode);
            int fold = getFold(obj, readByteNode);

            PythonBuiltinClassType cls = PythonBuiltinClassType.PDateTime;
            return new PDateTime(cls, cls.getInstanceShape(language), year, month, day, hour, minute, second, microsecond, tzInfo, fold);
        }

        @Fallback
        static PDateTime error(Object obj,
                        @Bind Node inliningTarget) {
            throw PRaiseNode.raiseStatic(inliningTarget, TypeError, ErrorMessages.S_EXPECTED_GOT_P, "datetime", obj);
        }

        static int getYear(PythonAbstractNativeObject self, CStructAccess.ReadByteNode readNode) {
            int b0 = readNode.readFromObjUnsigned(self, CFields.PyDateTime_DateTime__data, 0);
            int b1 = readNode.readFromObjUnsigned(self, CFields.PyDateTime_DateTime__data, 1);
            return (b0 << 8) | b1;
        }

        static int getMonth(PythonAbstractNativeObject self, CStructAccess.ReadByteNode readNode) {
            return readNode.readFromObjUnsigned(self, CFields.PyDateTime_DateTime__data, 2);
        }

        static int getDay(PythonAbstractNativeObject self, CStructAccess.ReadByteNode readNode) {
            return readNode.readFromObjUnsigned(self, CFields.PyDateTime_DateTime__data, 3);
        }

        static int getHour(PythonAbstractNativeObject self, CStructAccess.ReadByteNode readNode) {
            return readNode.readFromObjUnsigned(self, CFields.PyDateTime_DateTime__data, 4);
        }

        static int getMinute(PythonAbstractNativeObject self, CStructAccess.ReadByteNode readNode) {
            return readNode.readFromObjUnsigned(self, CFields.PyDateTime_DateTime__data, 5);
        }

        static int getSecond(PythonAbstractNativeObject self, CStructAccess.ReadByteNode readNode) {
            return readNode.readFromObjUnsigned(self, CFields.PyDateTime_DateTime__data, 6);
        }

        static int getMicrosecond(PythonAbstractNativeObject self, CStructAccess.ReadByteNode readNode) {
            int b3 = readNode.readFromObjUnsigned(self, CFields.PyDateTime_DateTime__data, 7);
            int b4 = readNode.readFromObjUnsigned(self, CFields.PyDateTime_DateTime__data, 8);
            int b5 = readNode.readFromObjUnsigned(self, CFields.PyDateTime_DateTime__data, 9);
            return (b3 << 16) | (b4 << 8) | b5;
        }

        private static Object getTzInfo(PythonAbstractNativeObject obj, CStructAccess.ReadByteNode readByteNode, CStructAccess.ReadObjectNode readObjectNode) {
            Object tzInfo = null;
            if (readByteNode.readFromObj(obj, CFields.PyDateTime_DateTime__hastzinfo) != 0) {
                Object tzinfoObj = readObjectNode.readFromObj(obj, CFields.PyDateTime_DateTime__tzinfo);
                if (tzinfoObj != PNone.NO_VALUE) {
                    tzInfo = tzinfoObj;
                }
            }
            return tzInfo;
        }

        static int getFold(PythonAbstractNativeObject self, CStructAccess.ReadByteNode readNode) {
            return readNode.readFromObjUnsigned(self, CFields.PyDateTime_DateTime__fold);
        }
    }

    @GenerateInline
    @GenerateCached(false)
    @GenerateUncached
    abstract static class TzInfoNode extends Node {
        public abstract Object execute(Node inliningTarget, Object obj);

        @Specialization
        static Object getTzInfo(PDateTime self) {
            return self.tzInfo;

        }

        @Specialization
        static Object getTzInfo(PythonAbstractNativeObject self,
                        @Cached CStructAccess.ReadByteNode readByteNode,
                        @Cached CStructAccess.ReadObjectNode readObjectNode) {
            return AsManagedDateTimeNode.getTzInfo(self, readByteNode, readObjectNode);
        }
    }

    @GenerateUncached
    @GenerateInline
    @GenerateCached(false)
    public abstract static class DateTimeCheckNode extends Node {
        public abstract boolean execute(Node inliningTarget, Object obj);

        public static boolean executeUncached(Object obj) {
            return DateTimeNodesFactory.DateTimeCheckNodeGen.getUncached().execute(null, obj);
        }

        @Specialization
        static boolean doManaged(@SuppressWarnings("unused") PDateTime value) {
            return true;
        }

        @Specialization
        static boolean doNative(Node inliningTarget, PythonAbstractNativeObject value,
                        @Cached BuiltinClassProfiles.IsBuiltinObjectProfile profile) {
            return profile.profileObject(inliningTarget, value, PythonBuiltinClassType.PDateTime);
        }

        @Fallback
        static boolean doOther(@SuppressWarnings("unused") Object value) {
            return false;
        }
    }
}
