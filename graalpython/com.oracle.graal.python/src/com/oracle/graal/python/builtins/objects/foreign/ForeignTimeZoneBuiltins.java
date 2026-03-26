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
package com.oracle.graal.python.builtins.objects.foreign;

import static com.oracle.graal.python.nodes.SpecialMethodNames.J___REDUCE__;
import static com.oracle.graal.python.util.PythonUtils.TS_ENCODING;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

import com.oracle.graal.python.annotations.Builtin;
import com.oracle.graal.python.annotations.Slot;
import com.oracle.graal.python.annotations.Slot.SlotKind;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.modules.datetime.DateTimeNodes;
import com.oracle.graal.python.builtins.modules.datetime.DatetimeModuleBuiltins;
import com.oracle.graal.python.builtins.modules.datetime.TemporalNodes;
import com.oracle.graal.python.builtins.modules.datetime.TimeDeltaNodes;
import com.oracle.graal.python.builtins.modules.datetime.TemporalNodes.DateTimeValue;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.type.TpSlots;
import com.oracle.graal.python.lib.PyDateTimeCheckNode;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.builtins.PythonBinaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonUnaryBuiltinNode;
import com.oracle.graal.python.nodes.object.GetClassNode;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.strings.TruffleString;

@CoreFunctions(extendClasses = PythonBuiltinClassType.ForeignTimeZone)
public final class ForeignTimeZoneBuiltins extends PythonBuiltins {
    public static final TpSlots SLOTS = ForeignTimeZoneBuiltinsSlotsGen.SLOTS;

    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return ForeignTimeZoneBuiltinsFactory.getFactories();
    }

    @Slot(value = SlotKind.tp_repr, isComplex = true)
    @GenerateNodeFactory
    abstract static class ReprNode extends PythonUnaryBuiltinNode {
        @Specialization(limit = "1")
        @TruffleBoundary
        static TruffleString repr(Object self,
                        @Bind Node inliningTarget,
                        @CachedLibrary("self") InteropLibrary interop) {
            ZoneId zoneId = asZoneId(self, interop);
            TruffleString typeName = com.oracle.graal.python.builtins.objects.type.TypeNodes.GetTpNameNode.executeUncached(GetClassNode.executeUncached(self));
            String value = String.format("%s('%s')", typeName, zoneId.getId());
            return TruffleString.FromJavaStringNode.getUncached().execute(value, TS_ENCODING);
        }
    }

    @Slot(value = SlotKind.tp_str, isComplex = true)
    @GenerateNodeFactory
    abstract static class StrNode extends PythonUnaryBuiltinNode {
        @Specialization(limit = "1")
        static TruffleString str(Object self,
                        @CachedLibrary("self") InteropLibrary interop) {
            return TruffleString.FromJavaStringNode.getUncached().execute(asZoneId(self, interop).getId(), TS_ENCODING);
        }
    }

    @Builtin(name = "utcoffset", minNumOfPositionalArgs = 1, parameterNames = {"$self", "dt"})
    @GenerateNodeFactory
    abstract static class UtcOffsetNode extends PythonBinaryBuiltinNode {
        @Specialization(limit = "1")
        @TruffleBoundary
        static Object utcoffset(Object self, Object dateTime,
                        @Bind Node inliningTarget,
                        @CachedLibrary("self") InteropLibrary interop,
                        @Cached PyDateTimeCheckNode dateTimeLikeCheckNode,
                        @Cached TemporalNodes.ReadDateTimeValueNode readDateTimeValueNode) {
            ZoneId zoneId = asZoneId(self, interop);
            if (dateTime == PNone.NONE) {
                Object fixed = TemporalNodes.toFixedOffsetTimeZone(zoneId, inliningTarget);
                if (fixed == null) {
                    return PNone.NONE;
                }
                return DatetimeModuleBuiltins.callUtcOffset(fixed, PNone.NONE, inliningTarget);
            }
            if (!dateTimeLikeCheckNode.execute(inliningTarget, dateTime)) {
                throw PRaiseNode.raiseStatic(inliningTarget, PythonBuiltinClassType.TypeError, ErrorMessages.FROMUTC_ARGUMENT_MUST_BE_A_DATETIME);
            }
            LocalDateTime localDateTime = readDateTimeValueNode.execute(inliningTarget, dateTime).toLocalDateTime();
            ZonedDateTime zonedDateTime = localDateTime.atZone(zoneId);
            return TimeDeltaNodes.NewNode.getUncached().execute(inliningTarget, PythonBuiltinClassType.PTimeDelta, 0, zonedDateTime.getOffset().getTotalSeconds(), 0, 0, 0, 0, 0);
        }
    }

    @Builtin(name = "dst", minNumOfPositionalArgs = 1, parameterNames = {"$self", "dt"})
    @GenerateNodeFactory
    abstract static class DstNode extends PythonBinaryBuiltinNode {
        @Specialization(limit = "1")
        @TruffleBoundary
        static Object dst(Object self, Object dateTime,
                        @Bind Node inliningTarget,
                        @CachedLibrary("self") InteropLibrary interop,
                        @Cached PyDateTimeCheckNode dateTimeLikeCheckNode,
                        @Cached TemporalNodes.ReadDateTimeValueNode readDateTimeValueNode) {
            ZoneId zoneId = asZoneId(self, interop);
            if (dateTime == PNone.NONE) {
                return PNone.NONE;
            }
            if (!dateTimeLikeCheckNode.execute(inliningTarget, dateTime)) {
                throw PRaiseNode.raiseStatic(inliningTarget, PythonBuiltinClassType.TypeError, ErrorMessages.FROMUTC_ARGUMENT_MUST_BE_A_DATETIME);
            }
            LocalDateTime localDateTime = readDateTimeValueNode.execute(inliningTarget, dateTime).toLocalDateTime();
            ZonedDateTime zonedDateTime = localDateTime.atZone(zoneId);
            int dstSeconds = (int) zoneId.getRules().getDaylightSavings(zonedDateTime.toInstant()).getSeconds();
            return TimeDeltaNodes.NewNode.getUncached().execute(inliningTarget, PythonBuiltinClassType.PTimeDelta, 0, dstSeconds, 0, 0, 0, 0, 0);
        }
    }

    @Builtin(name = "tzname", minNumOfPositionalArgs = 1, parameterNames = {"$self", "dt"})
    @GenerateNodeFactory
    abstract static class TzNameNode extends PythonBinaryBuiltinNode {
        @Specialization(limit = "1")
        @TruffleBoundary
        static Object tzname(Object self, Object dateTime,
                        @Bind Node inliningTarget,
                        @CachedLibrary("self") InteropLibrary interop,
                        @Cached PyDateTimeCheckNode dateTimeLikeCheckNode,
                        @Cached TemporalNodes.ReadDateTimeValueNode readDateTimeValueNode) {
            ZoneId zoneId = asZoneId(self, interop);
            if (dateTime == PNone.NONE) {
                return zoneId.getRules().isFixedOffset() ? TruffleString.FromJavaStringNode.getUncached().execute(zoneId.getId(), TS_ENCODING) : PNone.NONE;
            }
            if (!dateTimeLikeCheckNode.execute(inliningTarget, dateTime)) {
                throw PRaiseNode.raiseStatic(inliningTarget, PythonBuiltinClassType.TypeError, ErrorMessages.FROMUTC_ARGUMENT_MUST_BE_A_DATETIME);
            }
            LocalDateTime localDateTime = readDateTimeValueNode.execute(inliningTarget, dateTime).toLocalDateTime();
            String name = DateTimeFormatter.ofPattern("z", Locale.ENGLISH).format(localDateTime.atZone(zoneId));
            return TruffleString.FromJavaStringNode.getUncached().execute(name, TS_ENCODING);
        }
    }

    @Builtin(name = "fromutc", minNumOfPositionalArgs = 1, parameterNames = {"$self", "dt"})
    @GenerateNodeFactory
    abstract static class FromUtcNode extends PythonBinaryBuiltinNode {
        @Specialization(limit = "1")
        @TruffleBoundary
        static Object fromutc(Object self, Object dateTime,
                        @Bind Node inliningTarget,
                        @CachedLibrary("self") InteropLibrary interop,
                        @Cached PyDateTimeCheckNode dateTimeLikeCheckNode,
                        @Cached TemporalNodes.ReadDateTimeValueNode readDateTimeValueNode) {
            if (!dateTimeLikeCheckNode.execute(inliningTarget, dateTime)) {
                throw PRaiseNode.raiseStatic(inliningTarget, PythonBuiltinClassType.TypeError, ErrorMessages.FROMUTC_ARGUMENT_MUST_BE_A_DATETIME);
            }
            DateTimeValue asDateTime = readDateTimeValueNode.execute(inliningTarget, dateTime);
            if (asDateTime.tzInfo != self) {
                throw PRaiseNode.raiseStatic(inliningTarget, PythonBuiltinClassType.ValueError, ErrorMessages.FROMUTC_DT_TZINFO_IS_NOT_SELF);
            }
            ZoneId zoneId = asZoneId(self, interop);
            LocalDateTime utcDateTime = asDateTime.toLocalDateTime();
            ZonedDateTime zonedDateTime = utcDateTime.atOffset(java.time.ZoneOffset.UTC).atZoneSameInstant(zoneId);
            return DateTimeNodes.NewUnsafeNode.getUncached().execute(inliningTarget, PythonBuiltinClassType.PDateTime, zonedDateTime.getYear(), zonedDateTime.getMonthValue(),
                            zonedDateTime.getDayOfMonth(), zonedDateTime.getHour(), zonedDateTime.getMinute(), zonedDateTime.getSecond(), zonedDateTime.getNano() / 1_000, self, 0);
        }
    }

    @Builtin(name = J___REDUCE__, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class ReduceNode extends PythonUnaryBuiltinNode {
        @Specialization(limit = "1")
        static Object reduce(Object self,
                        @CachedLibrary("self") InteropLibrary interop) {
            return TruffleString.FromJavaStringNode.getUncached().execute(asZoneId(self, interop).getId(), TS_ENCODING);
        }
    }

    private static ZoneId asZoneId(Object self, InteropLibrary interop) {
        try {
            return interop.asTimeZone(self);
        } catch (UnsupportedMessageException e) {
            throw CompilerDirectives.shouldNotReachHere(e);
        }
    }
}
