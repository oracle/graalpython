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

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.annotations.Slot;
import com.oracle.graal.python.annotations.Builtin;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.Python3Core;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.PNotImplemented;
import com.oracle.graal.python.builtins.objects.module.PythonModule;
import com.oracle.graal.python.builtins.objects.str.PString;
import com.oracle.graal.python.builtins.objects.type.PythonBuiltinClass;
import com.oracle.graal.python.builtins.objects.type.TpSlots;
import com.oracle.graal.python.builtins.objects.type.slots.TpSlotHashFun;
import com.oracle.graal.python.builtins.objects.type.slots.TpSlotRichCompare.RichCmpBuiltinNode;
import com.oracle.graal.python.lib.PyObjectCallMethodObjArgs;
import com.oracle.graal.python.lib.PyObjectHashNode;
import com.oracle.graal.python.lib.PyObjectReprAsObjectNode;
import com.oracle.graal.python.lib.RichCmpOp;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonBinaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonUnaryBuiltinNode;
import com.oracle.graal.python.nodes.util.CastToJavaStringNode;
import com.oracle.graal.python.runtime.ExecutionContext;
import com.oracle.graal.python.runtime.IndirectCallData;
import com.oracle.graal.python.runtime.object.PFactory;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.strings.AbstractTruffleString;
import com.oracle.truffle.api.strings.TruffleString;

import java.util.List;

import static com.oracle.graal.python.builtins.PythonBuiltinClassType.TypeError;
import static com.oracle.graal.python.builtins.PythonBuiltinClassType.ValueError;
import static com.oracle.graal.python.nodes.BuiltinNames.T_MIN;
import static com.oracle.graal.python.nodes.BuiltinNames.T_TIMEZONE;
import static com.oracle.graal.python.nodes.BuiltinNames.T_UTC;
import static com.oracle.graal.python.nodes.BuiltinNames.T__DATETIME;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___GETINITARGS__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___REPR__;
import static com.oracle.graal.python.nodes.BuiltinNames.T_MAX;
import static com.oracle.graal.python.util.PythonUtils.TS_ENCODING;
import static com.oracle.graal.python.util.PythonUtils.tsLiteral;

@CoreFunctions(extendClasses = PythonBuiltinClassType.PTimezone)
public final class TimeZoneBuiltins extends PythonBuiltins {
    public static final TpSlots SLOTS = TimeZoneBuiltinsSlotsGen.SLOTS;

    public static final TruffleString T_UTC_ATTRIBUTE = tsLiteral("utc");

    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return TimeZoneBuiltinsFactory.getFactories();
    }

    @Override
    public void postInitialize(Python3Core core) {
        super.postInitialize(core);

        PythonLanguage language = core.getLanguage();

        PythonModule datetimeModule = core.lookupBuiltinModule(T__DATETIME);
        PythonBuiltinClass self = (PythonBuiltinClass) datetimeModule.getAttribute(T_TIMEZONE);
        var timeDeltaType = PythonBuiltinClassType.PTimeDelta;
        var timeDeltaShape = timeDeltaType.getInstanceShape(language);
        var timezoneType = PythonBuiltinClassType.PTimezone;
        var timezoneShape = timezoneType.getInstanceShape(language);

        // -(23 hours, 59 minutes)
        var timeDeltaMin = new PTimeDelta(timeDeltaType, timeDeltaShape, -1, 60, 0);
        var min = new PTimeZone(timezoneType, timezoneShape, timeDeltaMin, null);
        self.setAttribute(T_MIN, min);

        // (23 hours, 59 minutes)
        var timeDeltaMax = new PTimeDelta(timeDeltaType, timeDeltaShape, 0, 86340, 0);
        var max = new PTimeZone(timezoneType, timezoneShape, timeDeltaMax, null);
        self.setAttribute(T_MAX, max);

        var timeDeltaUtc = new PTimeDelta(timeDeltaType, timeDeltaShape, 0, 0, 0);
        var utc = new PTimeZone(timezoneType, timezoneShape, timeDeltaUtc, null);
        self.setAttribute(T_UTC_ATTRIBUTE, utc);
    }

    @Slot(value = Slot.SlotKind.tp_new, isComplex = true)
    @Slot.SlotSignature(name = "datetime.timezone", minNumOfPositionalArgs = 2, parameterNames = {"$cls", "offset", "name"})
    @GenerateNodeFactory
    public abstract static class NewNode extends PythonBuiltinNode {

        @Specialization
        static PTimeZone newTimezone(Object cls, Object offsetObject, Object nameObject,
                        @Bind Node inliningTarget,
                        @Cached TimeZoneNodes.NewNode newTimezoneNode) {
            return newTimezoneNode.execute(inliningTarget, getContext(inliningTarget), cls, offsetObject, nameObject);
        }
    }

    @Slot(value = Slot.SlotKind.tp_str, isComplex = true)
    @GenerateNodeFactory
    public abstract static class StrNode extends PythonUnaryBuiltinNode {

        @Specialization
        static TruffleString str(PTimeZone self) {
            // equivalent to tzname()
            return getTzName(self);
        }
    }

    @Slot(value = Slot.SlotKind.tp_repr, isComplex = true)
    @GenerateNodeFactory
    public abstract static class ReprNode extends PythonUnaryBuiltinNode {

        @Specialization
        static TruffleString repr(VirtualFrame frame, PTimeZone self,
                        @Bind Node inliningTarget,
                        @Cached("createFor($node)") IndirectCallData.BoundaryCallData boundaryCallData) {
            Object saved = ExecutionContext.BoundaryCallContext.enter(frame, boundaryCallData);
            try {
                return reprBoundary(self, inliningTarget);
            } finally {
                // A Python method call (using PyObjectReprAsObjectNode and
                // PyObjectCallMethodObjArgs) should be connected to a current node.
                ExecutionContext.BoundaryCallContext.exit(frame, boundaryCallData, saved);
            }
        }

        @TruffleBoundary
        private static TruffleString reprBoundary(PTimeZone self, Node inliningTarget) {
            PTimeZone utc = DatetimeModuleBuiltins.getUtcTimeZone(getContext(inliningTarget));
            if (self == utc) {
                var string = "datetime.timezone.utc";
                return TruffleString.FromJavaStringNode.getUncached().execute(string, TS_ENCODING);
            }

            var builder = new StringBuilder();
            builder.append("datetime.timezone(");

            Object offsetReprObject = PyObjectReprAsObjectNode.executeUncached(self.offset);
            String offsetRepr = CastToJavaStringNode.getUncached().execute(offsetReprObject);
            builder.append(offsetRepr);

            if (self.name != null) {
                builder.append(", ");

                Object nameReprObject = PyObjectCallMethodObjArgs.executeUncached(self.name, T___REPR__);
                assert nameReprObject instanceof TruffleString;
                String nameReprString = TruffleString.ToJavaStringNode.getUncached().execute((AbstractTruffleString) nameReprObject);

                builder.append(nameReprString);
            }

            builder.append(")");

            return TruffleString.FromJavaStringNode.getUncached().execute(builder.toString(), TS_ENCODING);
        }
    }

    @Slot(value = Slot.SlotKind.tp_richcompare, isComplex = true)
    @GenerateNodeFactory
    abstract static class RichCmpNode extends RichCmpBuiltinNode {

        @Specialization
        static Object richCmp(PTimeZone self, PTimeZone other, RichCmpOp op) {
            if (op != RichCmpOp.Py_EQ && op != RichCmpOp.Py_NE) {
                return PNotImplemented.NOT_IMPLEMENTED;
            }

            int result = self.offset.compareTo(other.offset);
            return op.compareResultToBool(result);
        }

        @Fallback
        static PNotImplemented doGeneric(Object self, Object other, RichCmpOp op) {
            return PNotImplemented.NOT_IMPLEMENTED;
        }
    }

    @Slot(value = Slot.SlotKind.tp_hash, isComplex = true)
    @GenerateNodeFactory
    abstract static class HashNode extends TpSlotHashFun.HashBuiltinNode {

        @Specialization
        static long hash(VirtualFrame frame, PTimeZone self,
                        @Bind Node inliningTarget,
                        @Bind PythonLanguage language,
                        @Cached PyObjectHashNode hashNode) {
            var content = new int[]{self.offset.days, self.offset.seconds, self.offset.microseconds};
            return hashNode.execute(frame, inliningTarget, PFactory.createTuple(language, content));
        }
    }

    @Builtin(name = "utcoffset", minNumOfPositionalArgs = 2, parameterNames = {"$self", "dt"})
    @GenerateNodeFactory
    public abstract static class UtcOffsetNode extends PythonBinaryBuiltinNode {

        @Specialization
        static PTimeDelta utcOffset(PTimeZone self, PDateTime dt) {
            return self.offset;
        }

        @Specialization(guards = {"isNone(dt)"})
        static PTimeDelta utcOffsetForNone(PTimeZone self, PNone dt) {
            return self.offset;
        }

        @Fallback
        static void doGeneric(Object self, Object dt,
                        @Bind Node inliningTarget,
                        @Cached PRaiseNode raiseNode) {
            throw raiseNode.raise(inliningTarget, TypeError,
                            ErrorMessages.S_ARGUMENT_MUST_BE_A_S_INSTANCE_OR_NONE_NOT_P,
                            "utcoffset(dt)",
                            "datetime",
                            dt);
        }
    }

    @Builtin(name = "dst", minNumOfPositionalArgs = 1, parameterNames = {"$self", "dt"})
    @GenerateNodeFactory
    public abstract static class DstNode extends PythonBinaryBuiltinNode {

        @Specialization
        static Object dst(PTimeZone self, PDateTime dt) {
            return PNone.NONE;
        }

        @Specialization(guards = {"isNone(dt)"})
        static Object dst(PTimeZone self, PNone dt) {
            return PNone.NONE;
        }

        @Fallback
        static void doGeneric(Object self, Object dt,
                        @Bind Node inliningTarget,
                        @Cached PRaiseNode raiseNode) {
            throw raiseNode.raise(inliningTarget,
                            TypeError,
                            ErrorMessages.S_ARGUMENT_MUST_BE_A_S_INSTANCE_OR_NONE_NOT_P,
                            "dst(dt)",
                            "datetime",
                            dt);
        }
    }

    @Builtin(name = "tzname", minNumOfPositionalArgs = 1, parameterNames = {"$self", "dt"})
    @GenerateNodeFactory
    public abstract static class TzNameNode extends PythonBinaryBuiltinNode {

        @Specialization
        static TruffleString tzName(PTimeZone self, PDateTime dt) {
            return getTzName(self);
        }

        @Specialization(guards = {"isNone(dt)"})
        static TruffleString tzName(PTimeZone self, PNone dt) {
            return getTzName(self);
        }

        @Fallback
        static void doGeneric(Object self, Object dt,
                        @Bind Node inliningTarget,
                        @Cached PRaiseNode raiseNode) {
            throw raiseNode.raise(inliningTarget,
                            TypeError,
                            ErrorMessages.S_ARGUMENT_MUST_BE_A_S_INSTANCE_OR_NONE_NOT_P,
                            "tzname(dt)",
                            "datetime",
                            dt);
        }
    }

    @Builtin(name = J___GETINITARGS__, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class GetInitArgsNode extends PythonUnaryBuiltinNode {

        @Specialization
        static Object getInitArgs(PTimeZone self,
                        @Bind PythonLanguage language) {
            final Object[] arguments;

            if (self.name == null) {
                arguments = new Object[]{self.offset};
            } else {
                PString name = PFactory.createString(language, self.name);
                arguments = new Object[]{self.offset, name};
            }

            return PFactory.createTuple(language, arguments);
        }
    }

    @Builtin(name = "fromutc", minNumOfPositionalArgs = 1, parameterNames = {"$self", "dt"})
    @GenerateNodeFactory
    public abstract static class FromUtcNode extends PythonBinaryBuiltinNode {

        @Specialization
        static Object fromUtc(PTimeZone self, PDateTime dateTime,
                        @Bind Node inliningTarget,
                        @Cached @Shared PRaiseNode raiseNode,
                        @Cached DateTimeNodes.SubclassNewNode dateTimeSubclassNewNode) {
            if (dateTime.tzInfo != self) {
                throw raiseNode.raise(inliningTarget, ValueError, ErrorMessages.FROMUTC_DT_TZINFO_IS_NOT_SELF);
            }

            return DatetimeModuleBuiltins.addOffsetToDateTime(dateTime, self.offset, dateTimeSubclassNewNode, inliningTarget);
        }

        @Fallback
        static void doGeneric(Object self, Object dateTime,
                        @Bind Node inliningTarget,
                        @Cached @Shared PRaiseNode raiseNode) {
            throw raiseNode.raise(inliningTarget, TypeError, ErrorMessages.FROMUTC_ARGUMENT_MUST_BE_A_DATETIME);
        }
    }

    @TruffleBoundary
    static TruffleString getTzName(PTimeZone timezone) {
        if (timezone.name != null) {
            return timezone.name;
        }

        if (timezone.offset.isZero()) {
            return T_UTC;
        }

        PTimeDelta utcOffset = timezone.offset;
        long microsecondsTotal = DatetimeModuleBuiltins.utcOffsetToMicroseconds(utcOffset);
        int sign = Long.signum(microsecondsTotal);
        microsecondsTotal = Math.abs(microsecondsTotal);
        long hours = microsecondsTotal / 1_000_000 / 3_600;
        long minutes = microsecondsTotal / 1_000_000 / 60 % 60;
        long seconds = microsecondsTotal / 1_000_000 % 60;
        long microseconds = microsecondsTotal % 1_000_000;

        var builder = new StringBuilder();
        builder.append("UTC");
        builder.append((sign >= 0) ? '+' : '-');
        builder.append(String.format("%02d:%02d", hours, minutes));

        if (seconds != 0 || microseconds != 0) {
            builder.append(String.format(":%02d", seconds));

            if (microseconds != 0) {
                builder.append(String.format(".%06d", microseconds));
            }
        }

        return TruffleString.FromJavaStringNode.getUncached().execute(builder.toString(), TS_ENCODING);
    }
}
