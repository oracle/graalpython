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

import static com.oracle.graal.python.builtins.PythonBuiltinClassType.OverflowError;
import static com.oracle.graal.python.builtins.PythonBuiltinClassType.TypeError;

import java.math.BigInteger;

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
import com.oracle.graal.python.lib.PyFloatAsDoubleNode;
import com.oracle.graal.python.lib.PyFloatCheckNode;
import com.oracle.graal.python.lib.PyLongAsDoubleNode;
import com.oracle.graal.python.lib.PyLongCheckNode;
import com.oracle.graal.python.lib.PyLongFromDoubleNode;
import com.oracle.graal.python.lib.PyNumberMultiplyNode;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.object.BuiltinClassProfiles;
import com.oracle.graal.python.nodes.util.CastToJavaBigIntegerNode;
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
                        @Cached TypeNodes.GetInstanceShape getInstanceShape) {
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

        public static final long US_PER_MS = 1000;
        public static final long US_PER_SECOND = 1000_000;
        public static final long US_PER_MINUTE = US_PER_SECOND * 60;
        public static final long US_PER_HOUR = US_PER_MINUTE * 60;
        public static final long US_PER_DAY = US_PER_HOUR * 24;
        public static final long US_PER_WEEK = US_PER_DAY * 7;

        public static final BigInteger BIG_US_PER_SECOND = BigInteger.valueOf(US_PER_SECOND);
        public static final BigInteger BIG_SECONDS_PER_DAY = BigInteger.valueOf(3600 * 24);

        @TruffleBoundary
        private static Object createTimeDelta(Node inliningTarget, Object cls, Shape shape, Object days, Object seconds, Object microseconds, Object milliseconds, Object minutes, Object hours,
                        Object weeks) {
            Accumulator accumulator = new Accumulator();
            accumulator.add("microseconds", microseconds, 1);
            accumulator.add("milliseconds", milliseconds, US_PER_MS);
            accumulator.add("seconds", seconds, US_PER_SECOND);
            accumulator.add("minutes", minutes, US_PER_MINUTE);
            accumulator.add("hours", hours, US_PER_HOUR);
            accumulator.add("days", days, US_PER_DAY);
            accumulator.add("weeks", weeks, US_PER_WEEK);
            accumulator.roundMicroseconds();
            return createTimeDeltaFromMicroseconds(inliningTarget, cls, shape, accumulator.getTotalMicroseconds());
        }

        @TruffleBoundary
        private static Object createTimeDeltaFromMicroseconds(Node inliningTarget, Object cls, Shape shape, BigInteger microseconds) {
            BigInteger[] res = microseconds.divideAndRemainder(BIG_US_PER_SECOND);
            int microsecondsNormalized = res[1].intValue();
            res = res[0].divideAndRemainder(BIG_SECONDS_PER_DAY);
            int daysNormalized;
            try {
                daysNormalized = res[0].intValueExact();
            } catch (ArithmeticException e) {
                throw PRaiseNode.raiseStatic(inliningTarget, OverflowError, ErrorMessages.PYTHON_INT_TOO_LARGE_TO_CONV_TO, "Java int");
            }
            int secondsNormalized = res[1].intValue();

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
                                daysNormalized,
                                secondsNormalized,
                                microsecondsNormalized);
            } else {
                Object nativeResult = CExtNodes.PCallCapiFunction.callUncached(NativeCAPISymbol.FUN_TIMEDELTA_SUBTYPE_NEW,
                                CApiTransitions.PythonToNativeNode.executeUncached(cls), daysNormalized, secondsNormalized, microsecondsNormalized);
                ExternalFunctionNodes.DefaultCheckFunctionResultNode.getUncached().execute(PythonContext.get(null), NativeCAPISymbol.FUN_TIMEDELTA_SUBTYPE_NEW.getTsName(), nativeResult);
                return CApiTransitions.NativeToPythonTransferNode.executeUncached(nativeResult);
            }
        }

        private static class Accumulator {
            private BigInteger microseconds = BigInteger.ZERO;
            private double leftover;

            public void add(String field, Object num, long factor) {
                if (num instanceof PNone) {
                    return;
                }
                if (PyLongCheckNode.executeUncached(num)) {
                    Object prod = PyNumberMultiplyNode.getUncached().execute(null, num, factor);
                    microseconds = microseconds.add(CastToJavaBigIntegerNode.executeUncached(prod));
                } else if (PyFloatCheckNode.executeUncached(num)) {
                    double dnum = PyFloatAsDoubleNode.executeUncached(num);
                    double intpart = dnum >= 0 ? Math.floor(dnum) : Math.ceil(dnum);
                    double fracpart = dnum - intpart;
                    Object x = PyLongFromDoubleNode.executeUncached(intpart);
                    Object prod = PyNumberMultiplyNode.getUncached().execute(null, x, factor);
                    microseconds = microseconds.add(CastToJavaBigIntegerNode.executeUncached(prod));
                    if (fracpart != 0) {
                        dnum = PyLongAsDoubleNode.executeUncached(factor);
                        dnum *= fracpart;
                        intpart = dnum >= 0 ? Math.floor(dnum) : Math.ceil(dnum);
                        fracpart = dnum - intpart;
                        x = PyLongFromDoubleNode.executeUncached(intpart);
                        microseconds = microseconds.add(CastToJavaBigIntegerNode.executeUncached(x));
                        leftover += fracpart;
                    }
                } else {
                    throw PRaiseNode.raiseStatic(null, TypeError, ErrorMessages.UNSUPPORTED_TYPE_FOR_TIMEDELTA_S_COMPONENT_P, field, num);
                }
            }

            public void roundMicroseconds() {
                if (leftover != 0) {
                    /* Round to nearest whole # of us, and add into microseconds. */
                    double wholeUs = Math.round(leftover);
                    if (Math.abs(wholeUs - leftover) == 0.5) {
                        /*
                         * We're exactly halfway between two integers. In order to do
                         * round-half-to-even, we must determine whether microseconds is odd. Note
                         * that microseconds is odd when it's last bit is 1. The code below uses
                         * bitwise and operation to check the last bit.
                         */
                        int xIsOdd = microseconds.testBit(0) ? 1 : 0;
                        wholeUs = 2.0 * Math.round((leftover + xIsOdd) * 0.5) - xIsOdd;
                    }
                    microseconds = microseconds.add(BigInteger.valueOf((long) wholeUs));
                }
            }

            public BigInteger getTotalMicroseconds() {
                return microseconds;
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

        @Specialization(guards = "checkNode.execute(inliningTarget, nativeDelta)", limit = "1")
        static PTimeDelta doNative(@SuppressWarnings("unused") Node inliningTarget, PythonAbstractNativeObject nativeDelta,
                        @Bind PythonLanguage language,
                        @SuppressWarnings("unused") @Cached TimeDeltaCheckNode checkNode,
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

        @Fallback
        static PTimeDelta error(Object obj,
                        @Bind Node inliningTarget) {
            throw PRaiseNode.raiseStatic(inliningTarget, TypeError, ErrorMessages.S_EXPECTED_GOT_P, "timedelta", obj);
        }
    }
}
