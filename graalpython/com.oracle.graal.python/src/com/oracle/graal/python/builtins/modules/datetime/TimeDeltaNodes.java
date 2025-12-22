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

import static com.oracle.graal.python.builtins.PythonBuiltinClassType.TypeError;

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
import com.oracle.graal.python.builtins.objects.type.TypeNodes;
import com.oracle.graal.python.lib.PyLongAsLongNode;
import com.oracle.graal.python.lib.PyLongFromDoubleNode;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PRaiseNode;
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
import com.oracle.truffle.api.nodes.EncapsulatingNodeReference;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.object.Shape;

public class TimeDeltaNodes {
    @GenerateUncached
    @GenerateInline
    @GenerateCached(false)
    public abstract static class NewNode extends Node {

        private static final int MAX_DAYS = 999_999_999;
        private static final int MIN_DAYS = -999_999_999;

        public abstract Object execute(Node inliningTarget, Object cls, Object days, Object seconds, Object microseconds, Object milliseconds, Object minutes, Object hours, Object weeks);

        public final PTimeDelta executeBuiltin(Node inliningTarget, Object days, Object seconds, Object microseconds, Object milliseconds, Object minutes, Object hours, Object weeks) {
            return (PTimeDelta) execute(inliningTarget, PythonBuiltinClassType.PTimeDelta, days, seconds, microseconds, milliseconds, minutes, hours, weeks);
        }

        public static TimeDeltaNodes.NewNode getUncached() {
            return TimeDeltaNodesFactory.NewNodeGen.getUncached();
        }

        @Specialization
        static Object newTimeDelta(Node inliningTarget, Object cls, Object days, Object seconds, Object microseconds, Object milliseconds, Object minutes, Object hours, Object weeks,
                        @Cached PRaiseNode raiseNode,
                        @Cached TypeNodes.GetInstanceShape getInstanceShape) {
            if (days == PNone.NO_VALUE) {
                days = 0;
            }
            if (seconds == PNone.NO_VALUE) {
                seconds = 0;
            }
            if (microseconds == PNone.NO_VALUE) {
                microseconds = 0;
            }
            if (milliseconds == PNone.NO_VALUE) {
                milliseconds = 0;
            }
            if (minutes == PNone.NO_VALUE) {
                minutes = 0;
            }
            if (hours == PNone.NO_VALUE) {
                hours = 0;
            }
            if (weeks == PNone.NO_VALUE) {
                weeks = 0;
            }

            validateNumericParameter(days, "days", inliningTarget, raiseNode);
            validateNumericParameter(seconds, "seconds", inliningTarget, raiseNode);
            validateNumericParameter(microseconds, "microseconds", inliningTarget, raiseNode);
            validateNumericParameter(milliseconds, "milliseconds", inliningTarget, raiseNode);
            validateNumericParameter(minutes, "minutes", inliningTarget, raiseNode);
            validateNumericParameter(hours, "hours", inliningTarget, raiseNode);
            validateNumericParameter(weeks, "weeks", inliningTarget, raiseNode);

            Shape shape = getInstanceShape.execute(cls);

            EncapsulatingNodeReference encapsulating = EncapsulatingNodeReference.getCurrent();
            Node encapsulatingNode = encapsulating.set(inliningTarget);
            try {
                return createTimeDelta(inliningTarget, cls, shape, days, seconds, microseconds, milliseconds, minutes, hours, weeks);
            } finally {
                // Some uncached nodes (e.g. PyLongFromDoubleNode and PyLongAsLongNode in
                // Accumulator) may raise exceptions that are not connected to a current
                // node. Set the current node manually.
                encapsulating.set(encapsulatingNode);
            }
        }

        private static void validateNumericParameter(Object value, String name, Node inliningTarget, PRaiseNode raiseNode) {
            if (!(value instanceof Integer) && !(value instanceof Long) && !(value instanceof Double)) {
                throw raiseNode.raise(inliningTarget, TypeError, ErrorMessages.UNSUPPORTED_TYPE_FOR_TIMEDELTA_S_COMPONENT_P, name, value);
            }
        }

        @TruffleBoundary
        private static Object createTimeDelta(Node inliningTarget, Object cls, Shape shape, Object days, Object seconds, Object microseconds, Object milliseconds, Object minutes, Object hours,
                        Object weeks) {
            Accumulator accumulator = new Accumulator();
            accumulator.addDays(days);
            accumulator.addSeconds(seconds);
            accumulator.addMicroSeconds(microseconds);
            accumulator.addMilliSeconds(milliseconds);
            accumulator.addMinutes(minutes);
            accumulator.addHours(hours);
            accumulator.addWeeks(weeks);

            accumulator.roundMicroseconds();

            long daysNormalized = accumulator.getDays();
            long secondsNormalized = accumulator.getSeconds();
            long microsecondsNormalized = accumulator.getMicroseconds();

            // handle negative values - only days may be negative
            if (microsecondsNormalized < 0) {
                secondsNormalized -= 1;
                microsecondsNormalized += 1_000_000;
            }
            if (secondsNormalized < 0) {
                daysNormalized -= 1;
                secondsNormalized += 24 * 3600;
            }

            if (daysNormalized < MIN_DAYS || daysNormalized > MAX_DAYS) {
                throw PRaiseNode.raiseStatic(inliningTarget,
                                PythonBuiltinClassType.OverflowError,
                                ErrorMessages.DAYS_D_MUST_HAVE_MAGNITUDE_LESS_THAN_D,
                                daysNormalized,
                                MAX_DAYS);
            }

            if (!TypeNodes.NeedsNativeAllocationNode.executeUncached(cls)) {
                return new PTimeDelta(cls, shape,
                                (int) daysNormalized,
                                (int) secondsNormalized,
                                (int) microsecondsNormalized);
            } else {
                Object nativeResult = CExtNodes.PCallCapiFunction.callUncached(NativeCAPISymbol.FUN_TIMEDELTA_SUBTYPE_NEW,
                                CApiTransitions.PythonToNativeNode.executeUncached(cls), (int) daysNormalized, (int) secondsNormalized, (int) microsecondsNormalized);
                ExternalFunctionNodes.DefaultCheckFunctionResultNode.getUncached().execute(PythonContext.get(null), NativeCAPISymbol.FUN_TIMEDELTA_SUBTYPE_NEW.getTsName(), nativeResult);
                return CApiTransitions.NativeToPythonTransferNode.executeUncached(nativeResult);
            }
        }

        private static class Accumulator {
            private long days = 0;
            private long seconds = 0;
            private double microseconds = 0;

            long getDays() {
                return days;
            }

            long getSeconds() {
                return seconds;
            }

            long getMicroseconds() {
                return (long) microseconds;
            }

            @TruffleBoundary
            void addDays(Object value) {
                if (value instanceof Long l) {
                    days += l;
                } else if (value instanceof Integer i) {
                    days += i;
                } else if (value instanceof Double d) {
                    double valueAsDouble = d;

                    // handle Infinity and NaN
                    Object valueAsLongObject = PyLongFromDoubleNode.executeUncached(d);
                    long valueAsLong = PyLongAsLongNode.executeUncached(valueAsLongObject);

                    days += valueAsLong;
                    seconds += (long) ((valueAsDouble - valueAsLong) * 24 * 3600);
                    microseconds += ((valueAsDouble - valueAsLong) * 24 * 3600 * 1_000_000) % 1_000_000;
                }

                normalize();
            }

            void addSeconds(Object value) {
                if (value instanceof Long l) {
                    days += l / (24 * 3600);
                    seconds += l % (24 * 3600);
                } else if (value instanceof Integer i) {
                    days += i / (24 * 3600L);
                    seconds += i % (24 * 3600L);
                } else if (value instanceof Double d) {
                    double valueAsDouble = d;

                    // handle Infinity and NaN
                    Object valueAsLongObject = PyLongFromDoubleNode.executeUncached(d);
                    long valueAsLong = PyLongAsLongNode.executeUncached(valueAsLongObject);

                    days += valueAsLong / (24 * 3600);
                    seconds += valueAsLong % (24 * 3600);
                    microseconds += ((valueAsDouble - valueAsLong) * 1_000_000) % 1_000_000;
                }

                normalize();
            }

            void addMicroSeconds(Object value) {
                if (value instanceof Long l) {
                    days += l / (24 * 3600 * 1_000_000L);
                    seconds += l % (24 * 3600 * 1_000_000L) / 1_000_000;
                    microseconds += l % 1_000_000;
                } else if (value instanceof Integer i) {
                    // Java int cannot represent microseconds that exceed microseconds in a day
                    seconds += i / 1_000_000L;
                    microseconds += i % 1_000_000L;
                } else if (value instanceof Double d) {
                    double valueAsDouble = d;

                    // handle Infinity and NaN
                    Object valueAsLongObject = PyLongFromDoubleNode.executeUncached(d);
                    long valueAsLong = PyLongAsLongNode.executeUncached(valueAsLongObject);

                    days += valueAsLong / (24 * 3600 * 1_000_000L);
                    seconds += valueAsLong % (24 * 3600 * 1_000_000L) / 1_000_000;
                    microseconds += valueAsDouble % 1_000_000;
                }

                normalize();
            }

            void addMilliSeconds(Object value) {
                if (value instanceof Long l) {
                    days += l / (24 * 3600 * 1_000);
                    seconds += l % (24 * 3600 * 1_000) / 1_000;
                    microseconds += (l % 1_000) * 1_000;
                } else if (value instanceof Integer i) {
                    days += i / (24 * 3600 * 1_000L);
                    seconds += i % (24 * 3600 * 1_000L) / 1_000;
                    microseconds += (i % 1_000L) * 1_000;
                } else if (value instanceof Double d) {
                    double valueAsDouble = d;

                    // handle Infinity and NaN
                    Object valueAsLongObject = PyLongFromDoubleNode.executeUncached(d);
                    long valueAsLong = PyLongAsLongNode.executeUncached(valueAsLongObject);

                    days += valueAsLong / (24 * 3600 * 1_000);
                    seconds += valueAsLong % (24 * 3600 * 1_000) / 1_000;
                    microseconds += (valueAsDouble * 1_000) % 1_000;
                }

                normalize();
            }

            void addMinutes(Object value) {
                if (value instanceof Long l) {
                    days += l / (24 * 60);
                    seconds += l % (24 * 60) * 60;
                } else if (value instanceof Integer i) {
                    days += i / (24 * 60L);
                    seconds += i % (24 * 60L) * 60;
                } else if (value instanceof Double d) {
                    double valueAsDouble = d;

                    // handle Infinity and NaN
                    Object valueAsLongObject = PyLongFromDoubleNode.executeUncached(d);
                    long valueAsLong = PyLongAsLongNode.executeUncached(valueAsLongObject);

                    days += valueAsLong / (24 * 60);
                    seconds += (long) (valueAsDouble % (24 * 60) * 60);
                    microseconds += ((valueAsDouble - valueAsLong) * 60 * 1_000_000) % 1_000_000;
                }

                normalize();
            }

            void addHours(Object value) {
                if (value instanceof Long l) {
                    days += l / 24;
                    seconds += (l % 24) * 3600;
                } else if (value instanceof Integer i) {
                    days += i / 24L;
                    seconds += (i % 24L) * 3600;
                } else if (value instanceof Double d) {
                    double valueAsDouble = d;

                    // handle Infinity and NaN
                    Object valueAsLongObject = PyLongFromDoubleNode.executeUncached(d);
                    long valueAsLong = PyLongAsLongNode.executeUncached(valueAsLongObject);

                    days += valueAsLong / 24;
                    seconds += (long) ((valueAsDouble % 24) * 3600);
                    microseconds += ((valueAsDouble - valueAsLong) * 3600 * 1_000_000) % 1_000_000;
                }

                normalize();
            }

            void addWeeks(Object value) {
                if (value instanceof Long l) {
                    days += l * 7;
                } else if (value instanceof Integer i) {
                    days += i * 7L;
                } else if (value instanceof Double d) {
                    double valueAsDouble = d;

                    // handle Infinity and NaN
                    Object valueAsLongObject = PyLongFromDoubleNode.executeUncached(d);
                    long valueAsLong = PyLongAsLongNode.executeUncached(valueAsLongObject);

                    days += (long) (valueAsDouble * 7);
                    seconds += (long) (((valueAsDouble - valueAsLong) * 7 * 24 * 3600) % (24 * 3600));
                    microseconds += ((valueAsDouble - valueAsLong) * 7 * 24 * 3600 * 1_000_000) % 1_000_000;
                }

                normalize();
            }

            @TruffleBoundary
            void roundMicroseconds() {
                microseconds = Math.rint(microseconds);
                normalize();
            }

            private void normalize() {
                if (microseconds >= 1_000_000) {
                    seconds += (long) microseconds / 1_000_000;
                    microseconds = microseconds % 1_000_000;
                }

                if (seconds >= 24 * 3600) {
                    days += seconds / (24 * 3600);
                    seconds = seconds % (24 * 3600);
                }
            }
        }
    }

    @GenerateUncached
    @GenerateInline
    @GenerateCached(false)
    public abstract static class TimeDeltaCheckNode extends Node {
        public abstract boolean execute(Node inliningTarget, Object obj);

        public static boolean executeUncached(Object obj) {
            return TimeDeltaNodesFactory.TimeDeltaCheckNodeGen.getUncached().execute(null, obj);
        }

        @Specialization
        static boolean doManaged(@SuppressWarnings("unused") PTimeDelta value) {
            return true;
        }

        @Specialization
        static boolean doNative(Node inliningTarget, PythonAbstractNativeObject value,
                        @Cached BuiltinClassProfiles.IsBuiltinObjectProfile profile) {
            return profile.profileObject(inliningTarget, value, PythonBuiltinClassType.PTimeDelta);
        }

        @Fallback
        static boolean doOther(@SuppressWarnings("unused") Object value) {
            return false;
        }
    }

    @GenerateUncached
    @GenerateInline
    @GenerateCached(false)
    public abstract static class AsManagedTimeDeltaNode extends Node {
        public abstract PTimeDelta execute(Node inliningTarget, Object obj);

        public static PTimeDelta executeUncached(Object obj) {
            return TimeDeltaNodesFactory.AsManagedTimeDeltaNodeGen.getUncached().execute(null, obj);
        }

        @Specialization
        static PTimeDelta doPTimeDelta(PTimeDelta value) {
            return value;
        }

        @Specialization
        static PTimeDelta doNative(PythonAbstractNativeObject nativeDelta,
                        @Bind PythonLanguage language,
                        @Cached CStructAccess.ReadI32Node readIntNode) {
            int days = getDays(nativeDelta, readIntNode);
            int seconds = getSeconds(nativeDelta, readIntNode);
            int microseconds = getMicroseconds(nativeDelta, readIntNode);

            PythonBuiltinClassType cls = PythonBuiltinClassType.PTimeDelta;
            return new PTimeDelta(cls, cls.getInstanceShape(language), days, seconds, microseconds);
        }

        static int getDays(PythonAbstractNativeObject self, CStructAccess.ReadI32Node readNode) {
            return readNode.readFromObj(self, CFields.PyDateTime_Delta__days);
        }

        static int getSeconds(PythonAbstractNativeObject self, CStructAccess.ReadI32Node readNode) {
            return readNode.readFromObj(self, CFields.PyDateTime_Delta__seconds);
        }

        static int getMicroseconds(PythonAbstractNativeObject self, CStructAccess.ReadI32Node readNode) {
            return readNode.readFromObj(self, CFields.PyDateTime_Delta__microseconds);
        }
    }
}
