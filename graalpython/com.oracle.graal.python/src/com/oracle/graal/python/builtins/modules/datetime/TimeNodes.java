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
import static com.oracle.graal.python.builtins.objects.cext.structs.CStructAccess.readByteField;
import static com.oracle.graal.python.util.PythonUtils.tsLiteral;

import java.lang.ref.Reference;

import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.cext.PythonAbstractNativeObject;
import com.oracle.graal.python.builtins.objects.cext.capi.CApiContext;
import com.oracle.graal.python.builtins.objects.cext.capi.ExternalFunctionInvoker;
import com.oracle.graal.python.builtins.objects.cext.capi.ExternalFunctionNodes.PyObjectCheckFunctionResultNode;
import com.oracle.graal.python.builtins.objects.cext.capi.NativeCAPISymbol;
import com.oracle.graal.python.builtins.objects.cext.capi.transitions.CApiTiming;
import com.oracle.graal.python.builtins.objects.cext.capi.transitions.CApiTransitions;
import com.oracle.graal.python.builtins.objects.cext.capi.transitions.CApiTransitions.NativeToPythonTransferNode;
import com.oracle.graal.python.builtins.objects.cext.structs.CFields;
import com.oracle.graal.python.builtins.objects.cext.structs.CStructAccess;
import com.oracle.graal.python.builtins.objects.function.PKeyword;
import com.oracle.graal.python.builtins.objects.type.TypeNodes;
import com.oracle.graal.python.builtins.objects.type.TypeNodes.GetInstanceShape;
import com.oracle.graal.python.lib.PyLongAsIntNode;
import com.oracle.graal.python.lib.PyTZInfoCheckNode;
import com.oracle.graal.python.runtime.nativeaccess.NativeMemory;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PGuards;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.call.CallNode;
import com.oracle.graal.python.runtime.IndirectCallData.BoundaryCallData;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.GenerateCached;
import com.oracle.truffle.api.dsl.GenerateInline;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.nodes.EncapsulatingNodeReference;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.object.Shape;
import com.oracle.truffle.api.strings.TruffleString;

public class TimeNodes {

    @GenerateUncached
    @GenerateInline
    @GenerateCached(false)
    public abstract static class NewNode extends Node {

        public abstract Object execute(Node inliningTarget, Object cls, Object hour, Object minute, Object second, Object microsecond, Object tzInfo, Object fold);

        @Specialization
        static Object newTime(Node inliningTarget, Object cls, Object hourObject, Object minuteObject, Object secondObject, Object microsecondObject, Object tzInfoObject, Object foldObject) {
            EncapsulatingNodeReference encapsulating = EncapsulatingNodeReference.getCurrent();
            Node encapsulatingNode = encapsulating.set(inliningTarget);
            try {
                return newTimeBoundary(inliningTarget, cls, hourObject, minuteObject, secondObject, microsecondObject, tzInfoObject, foldObject);
            } finally {
                // Some uncached nodes (e.g. PyLongAsLongNode) may raise exceptions
                // that are not connected to a current node. Set the current node
                // manually.
                encapsulating.set(encapsulatingNode);
            }
        }

        @TruffleBoundary
        static Object newTimeBoundary(Node inliningTarget, Object cls, Object hourObject, Object minuteObject, Object secondObject, Object microsecondObject, Object tzInfoObject, Object foldObject) {
            final int hour, minute, second, microsecond, fold;
            final Object tzInfo;

            if (hourObject == PNone.NO_VALUE) {
                hour = 0;
            } else {
                hour = PyLongAsIntNode.executeUncached(hourObject);
            }

            if (minuteObject == PNone.NO_VALUE) {
                minute = 0;
            } else {
                minute = PyLongAsIntNode.executeUncached(minuteObject);
            }

            if (secondObject == PNone.NO_VALUE) {
                second = 0;
            } else {
                second = PyLongAsIntNode.executeUncached(secondObject);
            }

            if (microsecondObject == PNone.NO_VALUE) {
                microsecond = 0;
            } else {
                microsecond = PyLongAsIntNode.executeUncached(microsecondObject);
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
                fold = PyLongAsIntNode.executeUncached(foldObject);
            }

            validateTimeComponents(inliningTarget, hour, minute, second, microsecond, tzInfo, fold);
            return newTimeUnchecked(cls, hour, minute, second, microsecond, tzInfo, fold);
        }

        private static final CApiTiming C_API_TIMING = CApiTiming.create(true, NativeCAPISymbol.FUN_TIME_SUBTYPE_NEW);

        @TruffleBoundary
        public static Object newTimeUnchecked(Object cls, int hour, int minute, int second, int microsecond, Object tzInfoObject, int fold) {
            final Object tzInfo;
            if (tzInfoObject instanceof PNone) {
                tzInfo = null;
            } else {
                tzInfo = tzInfoObject;
            }

            if (!TypeNodes.NeedsNativeAllocationNode.executeUncached(cls)) {
                Shape shape = GetInstanceShape.executeUncached(cls);
                return new PTime(cls, shape, hour, minute, second, microsecond, tzInfo, fold);
            } else {
                CApiTransitions.PythonToNativeNode toNative = CApiTransitions.PythonToNativeNode.getUncached();
                long clsPointer = toNative.executeLong(cls);
                Object effectiveTzInfo = tzInfo != null ? tzInfo : PNone.NO_VALUE;
                long tzInfoPointer = toNative.executeLong(effectiveTzInfo);
                try {
                    PythonContext context = PythonContext.get(null);
                    var callable = CApiContext.getNativeSymbol(null, NativeCAPISymbol.FUN_TIME_SUBTYPE_NEW);
                    long nativeResult = ExternalFunctionInvoker.invokeTIME_SUBTYPE_NEW(null, C_API_TIMING,
                                    context.ensureNfiContext(), BoundaryCallData.getUncached(),
                                    context.getThreadState(context.getLanguage()), callable, clsPointer, hour, minute, second, microsecond, tzInfoPointer, fold);
                    return PyObjectCheckFunctionResultNode.executeUncached(NativeCAPISymbol.FUN_TIME_SUBTYPE_NEW.getTsName(), NativeToPythonTransferNode.executeRawUncached(nativeResult));
                } finally {
                    Reference.reachabilityFence(cls);
                    Reference.reachabilityFence(effectiveTzInfo);
                }
            }
        }

        @TruffleBoundary
        private static void validateTimeComponents(Node inliningTarget, long hour, long minute, long second, long microsecond, Object tzInfo, long fold) {
            if (hour < 0 || hour >= 24) {
                throw PRaiseNode.raiseStatic(inliningTarget, ValueError, ErrorMessages.HOUR_MUST_BE_IN);
            }

            if (minute < 0 || minute >= 60) {
                throw PRaiseNode.raiseStatic(inliningTarget, ValueError, ErrorMessages.MINUTE_MUST_BE_IN);
            }

            if (second < 0 || second >= 60) {
                throw PRaiseNode.raiseStatic(inliningTarget, ValueError, ErrorMessages.SECOND_MUST_BE_IN);
            }

            if (microsecond < 0 || microsecond >= 1_000_000) {
                throw PRaiseNode.raiseStatic(inliningTarget, ValueError, ErrorMessages.MICROSECOND_MUST_BE_IN);
            }

            if (tzInfo != null && !PyTZInfoCheckNode.executeUncached(tzInfo)) {
                throw PRaiseNode.raiseStatic(inliningTarget, TypeError, ErrorMessages.TZINFO_ARGUMENT_MUST_BE_NONE_OR_OF_A_TZINFO_SUBCLASS_NOT_TYPE_P, tzInfo);
            }

            if (fold != 0 && fold != 1) {
                throw PRaiseNode.raiseStatic(inliningTarget, ValueError, ErrorMessages.FOLD_MUST_BE_EITHER_0_OR_1);
            }
        }
    }

    @GenerateUncached
    @GenerateInline
    @GenerateCached(false)
    public abstract static class SubclassNewNode extends Node {

        private static final TruffleString T_FOLD = tsLiteral("fold");

        public abstract Object execute(Node inliningTarget, Object cls, Object hour, Object minute, Object second, Object microsecond, Object tzInfo, Object fold);

        public static SubclassNewNode getUncached() {
            return TimeNodesFactory.SubclassNewNodeGen.getUncached();
        }

        @Specialization(guards = {"isBuiltinClass(cls)"})
        static Object newTimeBuiltinClass(Node inliningTarget, Object cls, Object hourObject, Object minuteObject, Object secondObject, Object microsecondObject, Object tzInfoObject,
                        Object foldObject,
                        @Cached NewNode newNode) {
            return newNode.execute(inliningTarget, cls, hourObject, minuteObject, secondObject, microsecondObject, tzInfoObject, foldObject);
        }

        @Fallback
        @TruffleBoundary
        static Object newTimeGeneric(Object cls, Object hourObject, Object minuteObject, Object secondObject, Object microsecondObject, Object tzInfoObject, Object foldObject) {
            Object[] arguments = new Object[]{hourObject, minuteObject, secondObject, microsecondObject, tzInfoObject};
            PKeyword foldKeyword = new PKeyword(T_FOLD, foldObject);
            PKeyword[] keywords = new PKeyword[]{foldKeyword};

            return CallNode.executeUncached(cls, arguments, keywords);
        }

        static boolean isBuiltinClass(Object cls) {
            return PGuards.isBuiltinClass(cls, PythonBuiltinClassType.PTime);
        }
    }

    public static final class FromNative {
        static int getHour(PythonAbstractNativeObject self) {
            long ptr = CStructAccess.getFieldPtr(self.getPtr(), CFields.PyDateTime_Time__data);
            return NativeMemory.readByteArrayElement(ptr, 0) & 0xFF;
        }

        static int getMinute(PythonAbstractNativeObject self) {
            long ptr = CStructAccess.getFieldPtr(self.getPtr(), CFields.PyDateTime_Time__data);
            return NativeMemory.readByteArrayElement(ptr, 1) & 0xFF;
        }

        static int getSecond(PythonAbstractNativeObject self) {
            long ptr = CStructAccess.getFieldPtr(self.getPtr(), CFields.PyDateTime_Time__data);
            return NativeMemory.readByteArrayElement(ptr, 2) & 0xFF;
        }

        static int getMicrosecond(PythonAbstractNativeObject self) {
            long ptr = CStructAccess.getFieldPtr(self.getPtr(), CFields.PyDateTime_Time__data);
            int b3 = NativeMemory.readByteArrayElement(ptr, 3) & 0xFF;
            int b4 = NativeMemory.readByteArrayElement(ptr, 4) & 0xFF;
            int b5 = NativeMemory.readByteArrayElement(ptr, 5) & 0xFF;
            return (b3 << 16) | (b4 << 8) | b5;
        }

        static Object getTzInfo(PythonAbstractNativeObject nativeTime, CStructAccess.ReadObjectNode readObjectNode) {
            Object tzinfo = null;
            if (readByteField(nativeTime.getPtr(), CFields.PyDateTime_Time__hastzinfo) != 0) {
                Object tzinfoObj = readObjectNode.readFromObj(nativeTime, CFields.PyDateTime_Time__tzinfo);
                if (tzinfoObj != PNone.NO_VALUE) {
                    tzinfo = tzinfoObj;
                }
            }
            return tzinfo;
        }

        static int getFold(PythonAbstractNativeObject self) {
            return readByteField(self.getPtr(), CFields.PyDateTime_Time__fold) & 0xFF;
        }
    }

    @GenerateInline
    @GenerateCached(false)
    @GenerateUncached
    abstract static class TzInfoNode extends Node {
        public abstract Object execute(Node inliningTarget, Object obj);

        public static Object executeUncached(Node inliningTarget, Object obj) {
            return TimeNodesFactory.TzInfoNodeGen.getUncached().execute(inliningTarget, obj);
        }

        @Specialization
        static Object getTzInfo(PTime self) {
            return self.tzInfo;

        }

        @Specialization
        static Object getTzInfo(PythonAbstractNativeObject self,
                        @Cached CStructAccess.ReadObjectNode readObjectNode) {
            return FromNative.getTzInfo(self, readObjectNode);
        }

        @Specialization
        static Object getTzInfo(Node inliningTarget, Object self,
                        @Cached TemporalValueNodes.GetTimeValue readTimeValueNode) {
            TemporalValueNodes.TimeValue timeValue = readTimeValueNode.execute(inliningTarget, self);
            return TemporalValueNodes.toPythonTzInfo(timeValue.tzInfo, timeValue.zoneId, inliningTarget);
        }
    }
}
