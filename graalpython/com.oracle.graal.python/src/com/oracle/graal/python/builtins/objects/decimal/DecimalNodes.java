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
package com.oracle.graal.python.builtins.objects.decimal;

import static com.oracle.graal.python.builtins.PythonBuiltinClassType.MemoryError;
import static com.oracle.graal.python.builtins.PythonBuiltinClassType.TypeError;
import static com.oracle.graal.python.builtins.PythonBuiltinClassType.ValueError;
import static com.oracle.graal.python.util.PythonUtils.TS_ENCODING;

import java.math.BigInteger;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.PNotImplemented;
import com.oracle.graal.python.builtins.objects.cext.PythonAbstractNativeObject;
import com.oracle.graal.python.builtins.objects.common.SequenceStorageNodes;
import com.oracle.graal.python.builtins.objects.decimal.DecimalArithmetic.Result;
import com.oracle.graal.python.builtins.objects.decimal.DecimalUtils.ConversionException;
import com.oracle.graal.python.builtins.objects.decimal.DecimalUtils.Value;
import com.oracle.graal.python.builtins.objects.decimal.PDecimal.Kind;
import com.oracle.graal.python.builtins.objects.floats.PFloat;
import com.oracle.graal.python.builtins.objects.ints.PInt;
import com.oracle.graal.python.builtins.objects.list.PList;
import com.oracle.graal.python.builtins.objects.type.TypeNodes;
import com.oracle.graal.python.lib.PyFloatAsDoubleNode;
import com.oracle.graal.python.lib.PyFloatCheckNode;
import com.oracle.graal.python.lib.PyListCheckNode;
import com.oracle.graal.python.lib.PyLongAsLongNode;
import com.oracle.graal.python.lib.PyLongCheckNode;
import com.oracle.graal.python.lib.PyTupleCheckNode;
import com.oracle.graal.python.lib.PyUnicodeCheckNode;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PGuards;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.builtins.ListNodes.GetListStorageNode;
import com.oracle.graal.python.nodes.builtins.TupleNodes.GetTupleStorage;
import com.oracle.graal.python.nodes.call.CallNode;
import com.oracle.graal.python.nodes.object.BuiltinClassProfiles.IsBuiltinClassExactProfile;
import com.oracle.graal.python.nodes.object.GetClassNode;
import com.oracle.graal.python.nodes.util.CastToJavaBigIntegerNode;
import com.oracle.graal.python.nodes.util.CastToJavaStringNode;
import com.oracle.graal.python.runtime.ExecutionContext.BoundaryCallContext;
import com.oracle.graal.python.runtime.IndirectCallData.BoundaryCallData;
import com.oracle.graal.python.runtime.object.PFactory;
import com.oracle.graal.python.runtime.sequence.storage.SequenceStorage;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.GenerateCached;
import com.oracle.truffle.api.dsl.GenerateInline;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.profiles.InlinedBranchProfile;
import com.oracle.truffle.api.profiles.InlinedConditionProfile;
import com.oracle.truffle.api.strings.TruffleString;

public final class DecimalNodes {
    private static final int INVALID = DecimalContextUtils.conditionMask(DecimalExceptionTypes.InvalidOperation);
    private static final int INEXACT = DecimalContextUtils.conditionMask(DecimalExceptionTypes.Inexact);
    private static final int ROUNDED = DecimalContextUtils.conditionMask(DecimalExceptionTypes.Rounded);
    private static final int SUBNORMAL = DecimalContextUtils.conditionMask(DecimalExceptionTypes.Subnormal);
    private static final int CLAMPED = DecimalContextUtils.conditionMask(DecimalExceptionTypes.Clamped);
    private static final long HALF_TEN_TO_NINETEEN = 5000000000000000000L;

    private DecimalNodes() {
    }

    /** Resolve an optional context argument, matching CPython's CONTEXT_CHECK_VA. */
    @GenerateInline
    @GenerateCached(false)
    @GenerateUncached
    public abstract static class GetContextNode extends Node {
        public abstract PDecimalContext execute(Node inliningTarget, Object context);

        @Specialization
        static PDecimalContext doContext(@SuppressWarnings("unused") Node inliningTarget, PDecimalContext context) {
            return context;
        }

        @Specialization
        static PDecimalContext doDefault(Node inliningTarget, @SuppressWarnings("unused") PNone context) {
            return DecimalContextUtils.getState(inliningTarget).getCurrentContext(inliningTarget);
        }

        @Fallback
        static PDecimalContext doInvalid(Node inliningTarget, @SuppressWarnings("unused") Object context,
                        @Cached PRaiseNode raiseNode) {
            throw raiseNode.raise(inliningTarget, TypeError, ErrorMessages.OPTIONAL_DECIMAL_CONTEXT);
        }
    }

    /** Numeric slots accept Decimal and int operands, without invoking conversion protocols. */
    @GenerateInline
    @GenerateCached(false)
    @GenerateUncached
    public abstract static class ToDecimalNode extends Node {
        public abstract PDecimal execute(Node inliningTarget, Object value);

        @Specialization
        static PDecimal decimal(@SuppressWarnings("unused") Node inliningTarget, PDecimal value) {
            return value;
        }

        @Specialization
        static PDecimal smallInteger(Node inliningTarget, int value) {
            return integer(inliningTarget, value);
        }

        @Specialization
        static PDecimal integer(Node inliningTarget, long value) {
            PythonLanguage language = PythonLanguage.get(inliningTarget);
            return PFactory.createDecimal(language, value);
        }

        @Specialization
        static PDecimal bool(Node inliningTarget, boolean value) {
            return integer(inliningTarget, value ? 1L : 0L);
        }

        @Specialization
        static PDecimal bigInteger(Node inliningTarget, PInt value) {
            DecimalUtils.Value integer = DecimalUtils.fromInteger(value.getValue());
            PythonLanguage language = PythonLanguage.get(inliningTarget);
            return create(language, integer);
        }

        @Specialization
        @TruffleBoundary
        static PDecimal nativeInteger(Node inliningTarget, PythonAbstractNativeObject value) {
            if (!PyLongCheckNode.executeUncached(value)) {
                return null;
            }
            return create(PythonLanguage.get(inliningTarget), DecimalUtils.fromInteger(CastToJavaBigIntegerNode.executeUncached(value)));
        }

        @Fallback
        static PDecimal unsupported(@SuppressWarnings("unused") Node inliningTarget, @SuppressWarnings("unused") Object value) {
            return null;
        }
    }

    static PDecimal create(PythonLanguage language, Value value) {
        return PFactory.createDecimal(language, value.negative(), value.coefficient(), value.exponent(), value.kind());
    }

    /** Decimal methods accept the same exact operands as slots, but reject unsupported types. */
    @GenerateInline
    @GenerateCached(false)
    public abstract static class RequireDecimalNode extends Node {
        public abstract PDecimal execute(Node inliningTarget, Object value);

        @Specialization
        static PDecimal convert(Node inliningTarget, Object value,
                        @Cached ToDecimalNode toDecimal,
                        @Cached GetClassNode getClass,
                        @Cached TypeNodes.GetNameNode getName,
                        @Cached PRaiseNode raiseNode) {
            PDecimal result = toDecimal.execute(inliningTarget, value);
            if (result == null) {
                throw raiseNode.raise(inliningTarget, TypeError, ErrorMessages.CONVERSION_FROM_S_TO_DECIMAL_NOT_SUPPORTED,
                                getName.execute(inliningTarget, getClass.execute(inliningTarget, value)));
            }
            return result;
        }
    }

    @GenerateInline
    @GenerateCached(false)
    @GenerateUncached
    @ImportStatic(PGuards.class)
    public abstract static class CreateDecimalNode extends Node {
        public abstract PDecimal execute(VirtualFrame frame, Node inliningTarget, Object cls, Object value) throws ConversionException;

        @Specialization(guards = "isNoValue(value)")
        static PDecimal doDefault(@SuppressWarnings("unused") Node inliningTarget, Object cls, @SuppressWarnings("unused") PNone value,
                        @Shared @Cached TypeNodes.GetInstanceShape getShape) {
            return PFactory.createDecimal(cls, getShape.execute(cls), 0L);
        }

        @Specialization
        static PDecimal doDecimal(Node inliningTarget, Object cls, PDecimal value,
                        @Shared @Cached GetClassNode getClass,
                        @Shared @Cached IsBuiltinClassExactProfile clsProfile,
                        @Shared @Cached IsBuiltinClassExactProfile valueProfile,
                        @Shared @Cached TypeNodes.GetInstanceShape getShape) {
            if (clsProfile.profileClass(inliningTarget, cls, PythonBuiltinClassType.PDecimal) &&
                            valueProfile.profileClass(inliningTarget, getClass.execute(inliningTarget, value), PythonBuiltinClassType.PDecimal)) {
                return value;
            }
            return PFactory.createDecimal(cls, getShape.execute(cls), value);
        }

        @Specialization
        static PDecimal doTruffleString(@SuppressWarnings("unused") Node inliningTarget, Object cls, TruffleString value,
                        @Cached TruffleString.CodePointAtIndexUTF32Node read,
                        @Cached TruffleString.ToJavaStringNode toJavaString,
                        @Shared @Cached TypeNodes.GetInstanceShape getShape) throws ConversionException {
            PDecimal result = fromCompactString(cls, value, read, getShape);
            if (result != null) {
                return result;
            }
            return createDecimal(cls, DecimalUtils.parse(toJavaString.execute(value)), getShape);
        }

        @Specialization(guards = "unicodeCheck.execute(inliningTarget, value)")
        static PDecimal doString(Node inliningTarget, Object cls, Object value,
                        @SuppressWarnings("unused") @Shared @Cached PyUnicodeCheckNode unicodeCheck,
                        @Cached CastToJavaStringNode asString,
                        @Shared @Cached TypeNodes.GetInstanceShape getShape) throws ConversionException {
            return createDecimal(cls, DecimalUtils.parse(asString.execute(value)), getShape);
        }

        @Specialization
        static PDecimal doInt(@SuppressWarnings("unused") Node inliningTarget, Object cls, int value,
                        @Shared @Cached TypeNodes.GetInstanceShape getShape) {
            return PFactory.createDecimal(cls, getShape.execute(cls), value);
        }

        @Specialization
        static PDecimal doLong(@SuppressWarnings("unused") Node inliningTarget, Object cls, long value,
                        @Shared @Cached TypeNodes.GetInstanceShape getShape) {
            return PFactory.createDecimal(cls, getShape.execute(cls), value);
        }

        @Specialization
        static PDecimal doPInt(@SuppressWarnings("unused") Node inliningTarget, Object cls, PInt value,
                        @Shared @Cached TypeNodes.GetInstanceShape getShape) {
            return createDecimal(cls, DecimalUtils.fromInteger(value.getValue()), getShape);
        }

        @Specialization(guards = "longCheck.execute(inliningTarget, value)")
        static PDecimal doIntGeneric(Node inliningTarget, Object cls, Object value,
                        @SuppressWarnings("unused") @Shared @Cached PyLongCheckNode longCheck,
                        @Shared @Cached CastToJavaBigIntegerNode asBigInteger,
                        @Shared @Cached TypeNodes.GetInstanceShape getShape) {
            return createDecimal(cls, DecimalUtils.fromInteger(asBigInteger.execute(inliningTarget, value)), getShape);
        }

        @Specialization
        static PDecimal doDouble(Node inliningTarget, Object cls, double value,
                        @Shared @Cached InlinedBranchProfile nonCompactProfile,
                        @Shared @Cached TypeNodes.GetInstanceShape getShape) {
            return fromDouble(inliningTarget, cls, value, getShape, nonCompactProfile);
        }

        @Specialization
        static PDecimal doPFloat(Node inliningTarget, Object cls, PFloat value,
                        @Shared @Cached InlinedBranchProfile nonCompactProfile,
                        @Shared @Cached TypeNodes.GetInstanceShape getShape) {
            return fromDouble(inliningTarget, cls, value.getValue(), getShape, nonCompactProfile);
        }

        @Specialization(guards = "floatCheck.execute(inliningTarget, value)")
        static PDecimal doFloatGeneric(VirtualFrame frame, Node inliningTarget, Object cls, Object value,
                        @SuppressWarnings("unused") @Shared @Cached PyFloatCheckNode floatCheck,
                        @Shared @Cached PyFloatAsDoubleNode asDouble,
                        @Shared @Cached InlinedBranchProfile nonCompactProfile,
                        @Shared @Cached TypeNodes.GetInstanceShape getShape) {
            return fromDouble(inliningTarget, cls, asDouble.execute(frame, inliningTarget, value), getShape, nonCompactProfile);
        }

        @Specialization(guards = "tupleCheck.execute(inliningTarget, value)")
        static PDecimal doTuple(VirtualFrame frame, Node inliningTarget, Object cls, Object value,
                        @SuppressWarnings("unused") @Shared @Cached PyTupleCheckNode tupleCheck,
                        @Shared @Cached GetTupleStorage getTupleStorage,
                        @Shared @Cached("createFor($node)") BoundaryCallData boundaryCallData,
                        @Shared @Cached TypeNodes.GetInstanceShape getShape) throws ConversionException {
            SequenceStorage storage = getTupleStorage.execute(inliningTarget, value);
            Object state = BoundaryCallContext.enter(frame, boundaryCallData);
            try {
                return createDecimal(cls, fromSequence(storage), getShape);
            } finally {
                BoundaryCallContext.exit(frame, boundaryCallData, state);
            }
        }

        @Specialization(guards = "listCheck.execute(inliningTarget, value)")
        static PDecimal doList(VirtualFrame frame, Node inliningTarget, Object cls, Object value,
                        @SuppressWarnings("unused") @Shared @Cached PyListCheckNode listCheck,
                        @Shared @Cached GetListStorageNode getListStorage,
                        @Shared @Cached("createFor($node)") BoundaryCallData boundaryCallData,
                        @Shared @Cached TypeNodes.GetInstanceShape getShape) throws ConversionException {
            SequenceStorage storage = getListStorage.execute(inliningTarget, value);
            Object state = BoundaryCallContext.enter(frame, boundaryCallData);
            try {
                return createDecimal(cls, fromSequence(storage), getShape);
            } finally {
                BoundaryCallContext.exit(frame, boundaryCallData, state);
            }
        }

        @Fallback
        static PDecimal doUnsupported(Node inliningTarget, @SuppressWarnings("unused") Object cls, Object value,
                        @Shared @Cached GetClassNode getClass,
                        @Shared @Cached TypeNodes.GetNameNode getName,
                        @Shared @Cached PRaiseNode raiseNode) {
            throw raiseNode.raise(inliningTarget, TypeError, ErrorMessages.CONVERSION_FROM_S_TO_DECIMAL_NOT_SUPPORTED,
                            getName.execute(inliningTarget, getClass.execute(inliningTarget, value)));
        }

        private static PDecimal createDecimal(Object cls, Value value, TypeNodes.GetInstanceShape getShape) {
            return PFactory.createDecimal(cls, getShape.execute(cls), value.negative(), value.coefficient(), value.exponent(), value.kind());
        }

        private static PDecimal fromCompactString(Object cls, TruffleString text, TruffleString.CodePointAtIndexUTF32Node read, TypeNodes.GetInstanceShape getShape) {
            int length = text.byteLength(TS_ENCODING) / Integer.BYTES;
            // At most 18 characters guarantee a coefficient below 10^18 and an explicit
            // exponent below 10^16 (the coefficient and 'e' take at least two characters).
            // Neither long overflow nor libmpdec's exponent limits are possible here.
            if (length == 0 || length > 18) {
                return null;
            }
            int pos = 0;
            int ch = read.execute(text, pos);
            boolean negative = ch == '-';
            if (negative || ch == '+') {
                pos++;
            }
            long coefficient = 0;
            int digits = 0;
            int fractionalDigits = 0;
            boolean point = false;
            while (pos < length) {
                ch = read.execute(text, pos);
                if (ch >= '0' && ch <= '9') {
                    coefficient = coefficient * 10 + ch - '0';
                    digits++;
                    if (point) {
                        fractionalDigits++;
                    }
                } else if (ch == '.' && !point) {
                    point = true;
                } else {
                    break;
                }
                pos++;
            }
            if (digits == 0) {
                return null;
            }
            long exponent = 0;
            if (pos < length && (ch == 'e' || ch == 'E')) {
                pos++;
                boolean negativeExponent = false;
                if (pos < length) {
                    ch = read.execute(text, pos);
                    negativeExponent = ch == '-';
                    if (negativeExponent || ch == '+') {
                        pos++;
                    }
                }
                int exponentStart = pos;
                while (pos < length) {
                    ch = read.execute(text, pos);
                    if (ch < '0' || ch > '9') {
                        return null;
                    }
                    exponent = exponent * 10 + ch - '0';
                    pos++;
                }
                if (pos == exponentStart) {
                    return null;
                }
                if (negativeExponent) {
                    exponent = -exponent;
                }
            }
            if (pos != length) {
                return null;
            }
            return PFactory.createDecimal(cls, getShape.execute(cls), negative, coefficient, exponent - fractionalDigits, Kind.FINITE);
        }

        private static PDecimal fromDouble(Node inliningTarget, Object cls, double value, TypeNodes.GetInstanceShape getShape, InlinedBranchProfile nonCompactProfile) {
            // IEEE 754 binary64 stores the sign in bit 63, an 11-bit biased exponent
            // in bits 62..52, and a 52-bit fraction in bits 51..0. The masks select
            long bits = Double.doubleToRawLongBits(value);
            boolean negative = bits < 0;
            int exponentBits = (int) ((bits >>> 52) & 0x7ff);
            long coefficient = bits & 0x000fffffffffffffL;
            // An all-ones exponent encodes infinity if the fraction is zero, else NaN.
            if (exponentBits == 0x7ff) {
                Kind kind = coefficient == 0 ? Kind.INFINITY : Kind.NAN;
                return PFactory.createDecimal(cls, getShape.execute(cls), kind == Kind.INFINITY && negative, 0L, 0, kind);
            }
            if (exponentBits == 0 && coefficient == 0) {
                return PFactory.createDecimal(cls, getShape.execute(cls), negative, 0L, 0, Kind.FINITE);
            }
            int binaryExponent;
            if (exponentBits == 0) {
                // Subnormals have no implicit leading 1. Their integer fraction is
                // scaled by 2^(1 - 1023 - 52) = 2^-1074 (bias 1023, 52 fraction bits).
                binaryExponent = -1074;
            } else {
                // Normal values have an implicit leading 1 at bit 52. Subtract the
                // exponent bias (1023) and fraction width (52) to scale this integer.
                coefficient |= 1L << 52;
                binaryExponent = exponentBits - 1023 - 52;
            }
            // Reduce coefficient * 2^binaryExponent to an odd coefficient.
            int trailingZeros = Long.numberOfTrailingZeros(coefficient);
            coefficient >>>= trailingZeros;
            binaryExponent += trailingZeros;
            if (binaryExponent >= 0) {
                // A compact positive coefficient has 63 usable bits. Check the shift
                // count and remaining capacity before multiplying by 2^binaryExponent.
                if (binaryExponent < Long.SIZE - 1 && coefficient <= (Long.MAX_VALUE >>> binaryExponent)) {
                    return PFactory.createDecimal(cls, getShape.execute(cls), negative, coefficient << binaryExponent, 0, Kind.FINITE);
                }
            } else {
                // n / 2^k = (n * 5^k) * 10^-k because 2 * 5 = 10, with no decimal
                // rounding. Long.MAX_VALUE / 5 is the largest coefficient that can
                // survive the next multiplication by 5 without signed-long overflow.
                for (int i = 0; i < -binaryExponent; i++) {
                    if (coefficient > Long.MAX_VALUE / 5) {
                        nonCompactProfile.enter(inliningTarget);
                        return createDecimal(cls, DecimalUtils.fromFloat(value), getShape);
                    }
                    coefficient *= 5;
                }
                return PFactory.createDecimal(cls, getShape.execute(cls), negative, coefficient, binaryExponent, Kind.FINITE);
            }
            nonCompactProfile.enter(inliningTarget);
            return createDecimal(cls, DecimalUtils.fromFloat(value), getShape);
        }

        @TruffleBoundary
        private static Value fromSequence(SequenceStorage triple) throws ConversionException {
            PRaiseNode raiseNode = PRaiseNode.getUncached();
            // Decimal triples are (sign, digits, exponent), at indices 0, 1, and 2.
            if (triple.length() != 3) {
                throw raiseNode.raise(null, ValueError, ErrorMessages.DECIMAL_SEQUENCE_LENGTH);
            }
            Object sign = SequenceStorageNodes.GetItemScalarNode.executeUncached(triple, 0);
            if (!PyLongCheckNode.executeUncached(sign)) {
                throw raiseNode.raise(null, ValueError, ErrorMessages.DECIMAL_SIGN);
            }
            long signValue = PyLongAsLongNode.executeUncached(sign);
            // The tuple format uses 0 for positive and 1 for negative, including zero.
            if (signValue != 0 && signValue != 1) {
                throw raiseNode.raise(null, ValueError, ErrorMessages.DECIMAL_SIGN);
            }
            Object exp = SequenceStorageNodes.GetItemScalarNode.executeUncached(triple, 2);
            Kind kind = Kind.FINITE;
            long exponent = 0;
            if (PyUnicodeCheckNode.executeUncached(exp)) {
                kind = switch (CastToJavaStringNode.getUncached().execute(exp)) {
                    case "F" -> Kind.INFINITY;
                    case "n" -> Kind.NAN;
                    case "N" -> Kind.SNAN;
                    default -> throw raiseNode.raise(null, ValueError, ErrorMessages.DECIMAL_SPECIAL_EXPONENT);
                };
            } else {
                if (!PyLongCheckNode.executeUncached(exp)) {
                    throw raiseNode.raise(null, ValueError, ErrorMessages.DECIMAL_EXPONENT);
                }
                exponent = PyLongAsLongNode.executeUncached(exp);
            }
            Object digits = SequenceStorageNodes.GetItemScalarNode.executeUncached(triple, 1);
            SequenceStorage storage;
            if (PyTupleCheckNode.executeUncached(digits)) {
                storage = GetTupleStorage.executeUncached(digits);
            } else if (PyListCheckNode.executeUncached(digits)) {
                storage = GetListStorageNode.executeUncached(digits);
            } else {
                throw raiseNode.raise(null, ValueError, ErrorMessages.DECIMAL_COEFFICIENT);
            }
            StringBuilder coefficient = new StringBuilder(storage.length());
            for (int i = 0; i < storage.length(); i++) {
                Object digit = SequenceStorageNodes.GetItemScalarNode.executeUncached(storage, i);
                if (!PyLongCheckNode.executeUncached(digit)) {
                    throw raiseNode.raise(null, ValueError, ErrorMessages.DECIMAL_COEFFICIENT);
                }
                long n = PyLongAsLongNode.executeUncached(digit);
                // Each coefficient element is one base-10 digit, in the range 0..9.
                if (n < 0 || n > 9) {
                    throw raiseNode.raise(null, ValueError, ErrorMessages.DECIMAL_COEFFICIENT);
                }
                if (kind != Kind.INFINITY) {
                    coefficient.append((char) ('0' + n));
                }
            }
            BigInteger integer;
            if (coefficient.isEmpty()) {
                integer = BigInteger.ZERO;
            } else {
                integer = new BigInteger(coefficient.toString());
            }
            if (kind == Kind.FINITE) {
                return DecimalUtils.finite(signValue != 0, integer, exponent);
            }
            return new Value(signValue != 0, integer, 0, kind);
        }
    }

    private static PDecimal compact(PythonLanguage language, boolean negative, long coefficient, long exponent) {
        return PFactory.createDecimal(language, negative, coefficient, exponent, Kind.FINITE);
    }

    @GenerateInline
    @GenerateCached(false)
    public abstract static class PlusDecimalNode extends Node {
        public abstract PDecimal execute(VirtualFrame frame, Node inliningTarget, PDecimal self);

        @Specialization
        static PDecimal plus(VirtualFrame frame, Node inliningTarget, PDecimal self,
                        @Cached InlinedBranchProfile nonCompactProfile,
                        @Cached RaiseSignalNode raiseSignal,
                        @Cached PRaiseNode raiseNode) {
            PDecimalContext context = DecimalContextUtils.getState(inliningTarget).getCurrentContext(inliningTarget);
            return doPlus(frame, inliningTarget, self, context, nonCompactProfile, raiseSignal, raiseNode);
        }

        static PDecimal doPlus(VirtualFrame frame, Node inliningTarget, PDecimal self, PDecimalContext context, InlinedBranchProfile nonCompactProfile, RaiseSignalNode raiseSignal,
                        PRaiseNode raiseNode) {
            PythonLanguage language = PythonLanguage.get(inliningTarget);
            PDecimal fast = compactUnary(language, self, context, self.isNegative());
            if (fast != null) {
                return fast;
            }
            nonCompactProfile.enter(inliningTarget);
            DecimalArithmetic.Result result;
            try {
                result = DecimalArithmetic.plus(self, context);
            } catch (ArithmeticException e) {
                // BigInteger cannot allocate coefficients beyond its int-sized representation.
                throw raiseNode.raise(inliningTarget, MemoryError);
            }
            return finishResult(frame, PythonLanguage.get(inliningTarget), context, result, raiseSignal);
        }
    }

    @GenerateInline
    @GenerateCached(false)
    public abstract static class MinusDecimalNode extends Node {
        public abstract PDecimal execute(VirtualFrame frame, Node inliningTarget, PDecimal self);

        @Specialization
        static PDecimal minus(VirtualFrame frame, Node inliningTarget, PDecimal self,
                        @Cached InlinedBranchProfile nonCompactProfile,
                        @Cached RaiseSignalNode raiseSignal,
                        @Cached PRaiseNode raiseNode) {
            PDecimalContext context = DecimalContextUtils.getState(inliningTarget).getCurrentContext(inliningTarget);
            return doMinus(frame, inliningTarget, self, context, nonCompactProfile, raiseSignal, raiseNode);
        }

        static PDecimal doMinus(VirtualFrame frame, Node inliningTarget, PDecimal self, PDecimalContext context, InlinedBranchProfile nonCompactProfile, RaiseSignalNode raiseSignal,
                        PRaiseNode raiseNode) {
            PythonLanguage language = PythonLanguage.get(inliningTarget);
            PDecimal fast = compactUnary(language, self, context, !self.isNegative());
            if (fast != null) {
                return fast;
            }
            nonCompactProfile.enter(inliningTarget);
            DecimalArithmetic.Result result;
            try {
                result = DecimalArithmetic.minus(self, context);
            } catch (ArithmeticException e) {
                // BigInteger cannot allocate coefficients beyond its int-sized representation.
                throw raiseNode.raise(inliningTarget, MemoryError);
            }
            return finishResult(frame, PythonLanguage.get(inliningTarget), context, result, raiseSignal);
        }
    }

    @GenerateInline
    @GenerateCached(false)
    public abstract static class AbsDecimalNode extends Node {
        public abstract PDecimal execute(VirtualFrame frame, Node inliningTarget, PDecimal self);

        @Specialization
        static PDecimal abs(VirtualFrame frame, Node inliningTarget, PDecimal self,
                        @Cached InlinedBranchProfile nonCompactProfile,
                        @Cached RaiseSignalNode raiseSignal,
                        @Cached PRaiseNode raiseNode) {
            PDecimalContext context = DecimalContextUtils.getState(inliningTarget).getCurrentContext(inliningTarget);
            return doAbs(frame, inliningTarget, self, context, nonCompactProfile, raiseSignal, raiseNode);
        }

        static PDecimal doAbs(VirtualFrame frame, Node inliningTarget, PDecimal self, PDecimalContext context, InlinedBranchProfile nonCompactProfile, RaiseSignalNode raiseSignal,
                        PRaiseNode raiseNode) {
            PythonLanguage language = PythonLanguage.get(inliningTarget);
            PDecimal fast = compactUnary(language, self, context, false);
            if (fast != null) {
                return fast;
            }
            nonCompactProfile.enter(inliningTarget);
            DecimalArithmetic.Result result;
            try {
                result = DecimalArithmetic.abs(self, context);
            } catch (ArithmeticException e) {
                // BigInteger cannot allocate coefficients beyond its int-sized representation.
                throw raiseNode.raise(inliningTarget, MemoryError);
            }
            return finishResult(frame, PythonLanguage.get(inliningTarget), context, result, raiseSignal);
        }
    }

    @GenerateInline
    @GenerateCached(false)
    public abstract static class AddDecimalNode extends Node {
        public abstract Object execute(VirtualFrame frame, Node inliningTarget, Object left, Object right);

        @Specialization
        static Object add(VirtualFrame frame, Node inliningTarget, Object left, Object right,
                        @Cached ToDecimalNode toLeft,
                        @Cached ToDecimalNode toRight,
                        @Cached InlinedConditionProfile notImplementedProfile,
                        @Cached InlinedBranchProfile nonCompactProfile,
                        @Cached RaiseSignalNode raiseSignal,
                        @Cached PRaiseNode raiseNode) {
            PDecimal a = toLeft.execute(inliningTarget, left);
            PDecimal b = toRight.execute(inliningTarget, right);
            if (notImplementedProfile.profile(inliningTarget, a == null || b == null)) {
                return PNotImplemented.NOT_IMPLEMENTED;
            }
            PDecimalContext context = DecimalContextUtils.getState(inliningTarget).getCurrentContext(inliningTarget);
            return doAdd(frame, inliningTarget, a, b, context, nonCompactProfile, raiseSignal, raiseNode);
        }

        static PDecimal doAdd(VirtualFrame frame, Node inliningTarget, PDecimal a, PDecimal b, PDecimalContext context, InlinedBranchProfile nonCompactProfile, RaiseSignalNode raiseSignal,
                        PRaiseNode raiseNode) {
            PythonLanguage language = PythonLanguage.get(inliningTarget);
            PDecimal fast = compactAdd(language, a, b, context, b.isNegative());
            if (fast != null) {
                return fast;
            }
            nonCompactProfile.enter(inliningTarget);
            DecimalArithmetic.Result result;
            try {
                result = DecimalArithmetic.add(a, b, context);
            } catch (ArithmeticException e) {
                throw raiseNode.raise(inliningTarget, MemoryError);
            }
            return finishResult(frame, PythonLanguage.get(inliningTarget), context, result, raiseSignal);
        }
    }

    @GenerateInline
    @GenerateCached(false)
    public abstract static class SubtractDecimalNode extends Node {
        public abstract Object execute(VirtualFrame frame, Node inliningTarget, Object left, Object right);

        @Specialization
        static Object subtract(VirtualFrame frame, Node inliningTarget, Object left, Object right,
                        @Cached ToDecimalNode toLeft,
                        @Cached ToDecimalNode toRight,
                        @Cached InlinedConditionProfile notImplementedProfile,
                        @Cached InlinedBranchProfile nonCompactProfile,
                        @Cached RaiseSignalNode raiseSignal,
                        @Cached PRaiseNode raiseNode) {
            PDecimal a = toLeft.execute(inliningTarget, left);
            PDecimal b = toRight.execute(inliningTarget, right);
            if (notImplementedProfile.profile(inliningTarget, a == null || b == null)) {
                return PNotImplemented.NOT_IMPLEMENTED;
            }
            PDecimalContext context = DecimalContextUtils.getState(inliningTarget).getCurrentContext(inliningTarget);
            return doSubtract(frame, inliningTarget, a, b, context, nonCompactProfile, raiseSignal, raiseNode);
        }

        static PDecimal doSubtract(VirtualFrame frame, Node inliningTarget, PDecimal a, PDecimal b, PDecimalContext context, InlinedBranchProfile nonCompactProfile, RaiseSignalNode raiseSignal,
                        PRaiseNode raiseNode) {
            PythonLanguage language = PythonLanguage.get(inliningTarget);
            PDecimal fast = compactAdd(language, a, b, context, !b.isNegative());
            if (fast != null) {
                return fast;
            }
            nonCompactProfile.enter(inliningTarget);
            DecimalArithmetic.Result result;
            try {
                result = DecimalArithmetic.subtract(a, b, context);
            } catch (ArithmeticException e) {
                throw raiseNode.raise(inliningTarget, MemoryError);
            }
            return finishResult(frame, PythonLanguage.get(inliningTarget), context, result, raiseSignal);
        }
    }

    @GenerateInline
    @GenerateCached(false)
    public abstract static class MultiplyDecimalNode extends Node {
        public abstract Object execute(VirtualFrame frame, Node inliningTarget, Object left, Object right);

        @Specialization
        static Object multiply(VirtualFrame frame, Node inliningTarget, Object left, Object right,
                        @Cached ToDecimalNode toLeft,
                        @Cached ToDecimalNode toRight,
                        @Cached InlinedConditionProfile notImplementedProfile,
                        @Cached InlinedBranchProfile nonCompactProfile,
                        @Cached RaiseSignalNode raiseSignal,
                        @Cached PRaiseNode raiseNode) {
            PDecimal a = toLeft.execute(inliningTarget, left);
            PDecimal b = toRight.execute(inliningTarget, right);
            if (notImplementedProfile.profile(inliningTarget, a == null || b == null)) {
                return PNotImplemented.NOT_IMPLEMENTED;
            }
            PDecimalContext context = DecimalContextUtils.getState(inliningTarget).getCurrentContext(inliningTarget);
            return doMultiply(frame, inliningTarget, a, b, context, nonCompactProfile, raiseSignal, raiseNode);
        }

        static PDecimal doMultiply(VirtualFrame frame, Node inliningTarget, PDecimal a, PDecimal b, PDecimalContext context, InlinedBranchProfile nonCompactProfile, RaiseSignalNode raiseSignal,
                        PRaiseNode raiseNode) {
            PythonLanguage language = PythonLanguage.get(inliningTarget);
            PDecimal fast = compactMultiply(language, a, b, context);
            if (fast != null) {
                return fast;
            }
            nonCompactProfile.enter(inliningTarget);
            DecimalArithmetic.Result result;
            try {
                result = DecimalArithmetic.multiply(a, b, context);
            } catch (ArithmeticException e) {
                throw raiseNode.raise(inliningTarget, MemoryError);
            }
            return finishResult(frame, PythonLanguage.get(inliningTarget), context, result, raiseSignal);
        }
    }

    @GenerateInline
    @GenerateCached(false)
    public abstract static class DivideDecimalNode extends Node {
        public abstract Object execute(VirtualFrame frame, Node inliningTarget, Object left, Object right);

        @Specialization
        static Object divide(VirtualFrame frame, Node inliningTarget, Object left, Object right,
                        @Cached ToDecimalNode toLeft,
                        @Cached ToDecimalNode toRight,
                        @Cached InlinedConditionProfile notImplementedProfile,
                        @Cached RaiseSignalNode raiseSignal,
                        @Cached PRaiseNode raiseNode) {
            PDecimal a = toLeft.execute(inliningTarget, left);
            PDecimal b = toRight.execute(inliningTarget, right);
            if (notImplementedProfile.profile(inliningTarget, a == null || b == null)) {
                return PNotImplemented.NOT_IMPLEMENTED;
            }
            PDecimalContext context = DecimalContextUtils.getState(inliningTarget).getCurrentContext(inliningTarget);
            return doDivide(frame, inliningTarget, a, b, context, raiseSignal, raiseNode);
        }

        static PDecimal doDivide(VirtualFrame frame, Node inliningTarget, PDecimal a, PDecimal b, PDecimalContext context, RaiseSignalNode raiseSignal, PRaiseNode raiseNode) {
            DecimalArithmetic.Result result;
            try {
                result = DecimalArithmetic.divide(a, b, context);
            } catch (ArithmeticException e) {
                throw raiseNode.raise(inliningTarget, MemoryError);
            }
            return finishResult(frame, PythonLanguage.get(inliningTarget), context, result, raiseSignal);
        }
    }

    @GenerateInline
    @GenerateCached(false)
    public abstract static class DivideIntDecimalNode extends Node {
        public abstract Object execute(VirtualFrame frame, Node inliningTarget, Object left, Object right);

        @Specialization
        static Object divideInt(VirtualFrame frame, Node inliningTarget, Object left, Object right,
                        @Cached ToDecimalNode toLeft,
                        @Cached ToDecimalNode toRight,
                        @Cached InlinedConditionProfile notImplementedProfile,
                        @Cached RaiseSignalNode raiseSignal,
                        @Cached PRaiseNode raiseNode) {
            PDecimal a = toLeft.execute(inliningTarget, left);
            PDecimal b = toRight.execute(inliningTarget, right);
            if (notImplementedProfile.profile(inliningTarget, a == null || b == null)) {
                return PNotImplemented.NOT_IMPLEMENTED;
            }
            PDecimalContext context = DecimalContextUtils.getState(inliningTarget).getCurrentContext(inliningTarget);
            return doDivideInt(frame, inliningTarget, a, b, context, raiseSignal, raiseNode);
        }

        static PDecimal doDivideInt(VirtualFrame frame, Node inliningTarget, PDecimal a, PDecimal b, PDecimalContext context, RaiseSignalNode raiseSignal, PRaiseNode raiseNode) {
            DecimalArithmetic.Result result;
            try {
                result = DecimalArithmetic.divideInt(a, b, context);
            } catch (ArithmeticException e) {
                throw raiseNode.raise(inliningTarget, MemoryError);
            }
            return finishResult(frame, PythonLanguage.get(inliningTarget), context, result, raiseSignal);
        }
    }

    @GenerateInline
    @GenerateCached(false)
    public abstract static class RemainderDecimalNode extends Node {
        public abstract Object execute(VirtualFrame frame, Node inliningTarget, Object left, Object right);

        @Specialization
        static Object remainder(VirtualFrame frame, Node inliningTarget, Object left, Object right,
                        @Cached ToDecimalNode toLeft,
                        @Cached ToDecimalNode toRight,
                        @Cached InlinedConditionProfile notImplementedProfile,
                        @Cached RaiseSignalNode raiseSignal,
                        @Cached PRaiseNode raiseNode) {
            PDecimal a = toLeft.execute(inliningTarget, left);
            PDecimal b = toRight.execute(inliningTarget, right);
            if (notImplementedProfile.profile(inliningTarget, a == null || b == null)) {
                return PNotImplemented.NOT_IMPLEMENTED;
            }
            PDecimalContext context = DecimalContextUtils.getState(inliningTarget).getCurrentContext(inliningTarget);
            return doRemainder(frame, inliningTarget, a, b, context, raiseSignal, raiseNode);
        }

        static PDecimal doRemainder(VirtualFrame frame, Node inliningTarget, PDecimal a, PDecimal b, PDecimalContext context, RaiseSignalNode raiseSignal, PRaiseNode raiseNode) {
            DecimalArithmetic.Result result;
            try {
                result = DecimalArithmetic.remainder(a, b, context);
            } catch (ArithmeticException e) {
                throw raiseNode.raise(inliningTarget, MemoryError);
            }
            return finishResult(frame, PythonLanguage.get(inliningTarget), context, result, raiseSignal);
        }
    }

    @GenerateInline
    @GenerateCached(false)
    public abstract static class DivmodDecimalNode extends Node {
        public abstract Object execute(VirtualFrame frame, Node inliningTarget, Object left, Object right);

        @Specialization
        static Object divmod(VirtualFrame frame, Node inliningTarget, Object left, Object right,
                        @Cached ToDecimalNode toLeft,
                        @Cached ToDecimalNode toRight,
                        @Cached InlinedConditionProfile notImplementedProfile,
                        @Cached RaiseSignalNode raiseSignal,
                        @Cached PRaiseNode raiseNode) {
            PDecimal a = toLeft.execute(inliningTarget, left);
            PDecimal b = toRight.execute(inliningTarget, right);
            if (notImplementedProfile.profile(inliningTarget, a == null || b == null)) {
                return PNotImplemented.NOT_IMPLEMENTED;
            }
            PDecimalContext context = DecimalContextUtils.getState(inliningTarget).getCurrentContext(inliningTarget);
            return doDivmod(frame, inliningTarget, a, b, context, raiseSignal, raiseNode);
        }

        static Object doDivmod(VirtualFrame frame, Node inliningTarget, PDecimal a, PDecimal b, PDecimalContext context, RaiseSignalNode raiseSignal, PRaiseNode raiseNode) {
            DecimalArithmetic.ResultWithRemainder result;
            try {
                result = DecimalArithmetic.divmod(a, b, context);
            } catch (ArithmeticException e) {
                throw raiseNode.raise(inliningTarget, MemoryError);
            }
            DecimalContextUtils.addStatus(frame, context, result.status(), raiseSignal);
            PythonLanguage language = PythonLanguage.get(inliningTarget);
            PDecimal value = create(language, result.value());
            PDecimal remainder = create(language, result.remainder());
            return PFactory.createTuple(language, new Object[]{value, remainder});
        }
    }

    private static PDecimal finishResult(VirtualFrame frame, PythonLanguage language, PDecimalContext context, Result result, RaiseSignalNode raiseSignal) {
        DecimalContextUtils.addStatus(frame, context, result.status(), raiseSignal);
        return create(language, result.value());
    }

    static boolean isCompactFinite(PDecimal value) {
        return value.getKind() == Kind.FINITE && value.isCompact();
    }

    private static PDecimal compactUnary(PythonLanguage language, PDecimal self, PDecimalContext context, boolean negative) {
        if (!isCompactFinite(self) || !DecimalArithmetic.fitsContext(self.getCompactCoefficient() == 0, self.getExponent(), DecimalArithmetic.digits(self.getCompactCoefficient()), context)) {
            return null;
        }
        if (self.getCompactCoefficient() == 0 && context.getRounding() != DecimalRounding.ROUND_FLOOR) {
            negative = false;
        }
        return compact(language, negative, self.getCompactCoefficient(), self.getExponent());
    }

    /** Scale a nonnegative coefficient, or return -1 if it no longer fits a signed long. */
    static long scaleCompactCoefficient(long coefficient, long shift) {
        assert coefficient >= 0 && shift >= 0;
        if (coefficient == 0) {
            return 0;
        }
        // Even a one-digit coefficient cannot fit after a shift of 19 decimal places.
        if (shift > 18) {
            return -1;
        }
        for (int i = 0; i < shift; i++) {
            if (coefficient > Long.MAX_VALUE / 10) {
                return -1;
            }
            coefficient *= 10;
        }
        return coefficient;
    }

    private static PDecimal compactAdd(PythonLanguage language, PDecimal a, PDecimal b, PDecimalContext context, boolean rightNegative) {
        if (!isCompactFinite(a) || !isCompactFinite(b)) {
            return null;
        }
        // Preserve the finer quantum by scaling the operand with the larger exponent.
        long left = a.getCompactCoefficient();
        long right = b.getCompactCoefficient();
        long exponent = a.getExponent();
        if (exponent > b.getExponent()) {
            left = scaleCompactCoefficient(left, exponent - b.getExponent());
            exponent = b.getExponent();
        } else if (exponent < b.getExponent()) {
            right = scaleCompactCoefficient(right, b.getExponent() - exponent);
        }
        if (left < 0 || right < 0) {
            return null;
        }
        if (a.isNegative()) {
            left = -left;
        }
        if (rightNegative) {
            right = -right;
        }
        long sum;
        try {
            sum = Math.addExact(left, right);
        } catch (ArithmeticException e) {
            return null;
        }
        if (sum == Long.MIN_VALUE) {
            return null;
        }
        long coefficient = Math.abs(sum);
        boolean negative;
        if (sum == 0) {
            if (a.isNegative() == rightNegative) {
                negative = a.isNegative();
            } else {
                negative = context.getRounding() == DecimalRounding.ROUND_FLOOR;
            }
        } else {
            negative = sum < 0;
        }
        if (DecimalArithmetic.fitsContext(coefficient == 0, exponent, DecimalArithmetic.digits(coefficient), context)) {
            return compact(language, negative, coefficient, exponent);
        }
        return null;
    }

    private static PDecimal compactMultiply(PythonLanguage language, PDecimal a, PDecimal b, PDecimalContext context) {
        if (!isCompactFinite(a) || !isCompactFinite(b)) {
            return null;
        }
        long coefficient;
        try {
            coefficient = Math.multiplyExact(a.getCompactCoefficient(), b.getCompactCoefficient());
        } catch (ArithmeticException e) {
            return null;
        }
        long exponent = a.getExponent() + b.getExponent();
        boolean negative = a.isNegative() ^ b.isNegative();
        if (DecimalArithmetic.fitsContext(coefficient == 0, exponent, DecimalArithmetic.digits(coefficient), context)) {
            return compact(language, negative, coefficient, exponent);
        }
        return null;
    }

    /** Apply quantize's precision, exponent limits and flags without BigInteger arithmetic. */
    private static PDecimal compactQuantize(VirtualFrame frame, PythonLanguage language, PDecimal self, long exponent,
                    PDecimalContext context, DecimalRounding rounding, RaiseSignalNode raiseSignal) {
        if (!isCompactFinite(self)) {
            return null;
        }
        long coefficient = self.getCompactCoefficient();
        long etiny = context.getEmin() - context.getPrecision() + 1;
        long adjusted = self.getExponent() + DecimalArithmetic.digits(coefficient) - 1;
        if (exponent < etiny || exponent > context.getEmax() || coefficient != 0 &&
                        (adjusted > context.getEmax() || adjusted - exponent + 1 > context.getPrecision())) {
            return invalidQuantize(frame, language, context, raiseSignal);
        }
        int status = 0;
        long shift = self.getExponent() - exponent;
        if (coefficient != 0) {
            if (shift >= 0) {
                coefficient = scaleCompactCoefficient(coefficient, shift);
                if (coefficient < 0) {
                    return null;
                }
            } else {
                long places = -shift;
                long divisor = compactDivisor(places);
                long remainder = coefficient;
                long quotient = 0;
                if (divisor != 0) {
                    quotient = coefficient / divisor;
                    remainder = coefficient % divisor;
                }
                if (incrementCompact(self.isNegative(), quotient, remainder, divisor, places, rounding)) {
                    quotient++;
                }
                coefficient = quotient;
                status |= ROUNDED;
                if (remainder != 0) {
                    status |= INEXACT;
                }
            }
        }
        int digits = DecimalArithmetic.digits(coefficient);
        adjusted = exponent + digits - 1;
        if (digits > context.getPrecision() || adjusted > context.getEmax()) {
            return invalidQuantize(frame, language, context, raiseSignal);
        }
        if (coefficient != 0 && adjusted < context.getEmin()) {
            status |= SUBNORMAL;
        }
        long etop = context.getEmax() - context.getPrecision() + 1;
        if (context.getClamp() != 0 && exponent > etop) {
            coefficient = scaleCompactCoefficient(coefficient, exponent - etop);
            if (coefficient < 0) {
                return null;
            }
            exponent = etop;
            status |= CLAMPED;
        }
        DecimalContextUtils.addStatus(frame, context, status, raiseSignal);
        return compact(language, self.isNegative(), coefficient, exponent);
    }

    private static PDecimal invalidQuantize(VirtualFrame frame, PythonLanguage language, PDecimalContext context, RaiseSignalNode raiseSignal) {
        DecimalContextUtils.addStatus(frame, context, INVALID, raiseSignal);
        return PFactory.createDecimal(language, false, 0L, 0, Kind.NAN);
    }

    @GenerateInline
    @GenerateCached(false)
    @GenerateUncached
    public abstract static class QuantizeDecimalNode extends Node {
        public abstract PDecimal execute(VirtualFrame frame, Node inliningTarget, PDecimal self, long exponent, PDecimalContext context, DecimalRounding rounding);

        @Specialization
        static PDecimal quantize(VirtualFrame frame, Node inliningTarget, PDecimal self, long exponent, PDecimalContext context, DecimalRounding rounding,
                        @Cached InlinedBranchProfile nonCompactProfile,
                        @Cached RaiseSignalNode raiseSignal,
                        @Cached PRaiseNode raiseNode) {
            PythonLanguage language = PythonLanguage.get(inliningTarget);
            PDecimal fast = compactQuantize(frame, language, self, exponent, context, rounding, raiseSignal);
            if (fast != null) {
                return fast;
            }
            nonCompactProfile.enter(inliningTarget);
            Result result;
            try {
                result = DecimalArithmetic.quantize(self, exponent, context, rounding);
            } catch (ArithmeticException e) {
                throw raiseNode.raise(inliningTarget, MemoryError);
            }
            DecimalContextUtils.addStatus(frame, context, result.status(), raiseSignal);
            return create(language, result.value());
        }

        static PDecimal doQuantize(VirtualFrame frame, Node inliningTarget, PDecimal self, PDecimal quantum, PDecimalContext context, DecimalRounding rounding, QuantizeDecimalNode operation,
                        RaiseSignalNode raiseSignal, PRaiseNode raiseNode) {
            if (quantum.getKind() == Kind.FINITE) {
                return operation.execute(frame, inliningTarget, self, quantum.getExponent(), context, rounding);
            }
            DecimalArithmetic.Result result;
            try {
                result = DecimalArithmetic.quantize(self, quantum, context, rounding);
            } catch (ArithmeticException e) {
                throw raiseNode.raise(inliningTarget, MemoryError);
            }
            return finishResult(frame, PythonLanguage.get(inliningTarget), context, result, raiseSignal);
        }
    }

    /** A divisor of zero denotes 10**places outside the compact coefficient range. */
    private static long compactDivisor(long places) {
        if (places > 18) {
            return 0;
        }
        long divisor = 1;
        for (int i = 0; i < places; i++) {
            divisor *= 10;
        }
        return divisor;
    }

    private static boolean incrementCompact(boolean negative, long quotient, long remainder, long divisor, long places, DecimalRounding rounding) {
        if (remainder == 0) {
            return false;
        }
        int half = -1;
        if (divisor != 0) {
            half = Long.compare(remainder, divisor / 2);
        } else if (places == 19) {
            half = Long.compare(remainder, HALF_TEN_TO_NINETEEN);
        }
        return switch (rounding) {
            case ROUND_DOWN -> false;
            case ROUND_UP -> true;
            case ROUND_CEILING -> !negative;
            case ROUND_FLOOR -> negative;
            case ROUND_05UP -> quotient % 5 == 0;
            case ROUND_HALF_UP -> half >= 0;
            case ROUND_HALF_DOWN -> half > 0;
            case ROUND_HALF_EVEN -> half > 0 || half == 0 && (quotient & 1) != 0;
        };
    }

    /** Long.MIN_VALUE indicates that the integer result needs the BigInteger fallback. */
    static long compactInteger(PDecimal self, DecimalRounding rounding) {
        if (!self.isCompact()) {
            return Long.MIN_VALUE;
        }
        long coefficient = self.getCompactCoefficient();
        long exponent = self.getExponent();
        if (exponent >= 0) {
            coefficient = scaleCompactCoefficient(coefficient, exponent);
            if (coefficient < 0) {
                return Long.MIN_VALUE;
            }
        } else {
            long divisor = compactDivisor(-exponent);
            long quotient = 0;
            long remainder = coefficient;
            if (divisor != 0) {
                quotient = coefficient / divisor;
                remainder = coefficient % divisor;
            }
            if (incrementCompact(self.isNegative(), quotient, remainder, divisor, -exponent, rounding)) {
                quotient++;
            }
            coefficient = quotient;
        }
        if (self.isNegative()) {
            return -coefficient;
        }
        return coefficient;
    }

    /** Compare finite compact magnitudes by truncating, so scaling cannot overflow. */
    static int compareCompact(boolean negativeA, long a, long exponentA, boolean negativeB, long b, long exponentB) {
        if (a == 0 && b == 0) {
            return 0;
        }
        if (negativeA != negativeB) {
            if (negativeA) {
                return -1;
            }
            return 1;
        }
        int result;
        if (a == 0 || b == 0) {
            result = Long.compare(a, b);
        } else {
            long adjustedA = exponentA + DecimalArithmetic.digits(a) - 1;
            long adjustedB = exponentB + DecimalArithmetic.digits(b) - 1;
            result = Long.compare(adjustedA, adjustedB);
            if (result == 0) {
                if (exponentA >= exponentB) {
                    long divisor = compactDivisor(exponentA - exponentB);
                    result = Long.compare(a, b / divisor);
                    if (result == 0 && b % divisor != 0) {
                        result = -1;
                    }
                } else {
                    long divisor = compactDivisor(exponentB - exponentA);
                    result = Long.compare(a / divisor, b);
                    if (result == 0 && a % divisor != 0) {
                        result = 1;
                    }
                }
            }
        }
        if (negativeA) {
            return -result;
        }
        return result;
    }

    @GenerateInline(false) // Error path
    @GenerateUncached
    public abstract static class RaiseSignalNode extends Node {
        public abstract void execute(VirtualFrame frame, int trapped);

        @Specialization
        static void raise(VirtualFrame frame, int trapped,
                        @Bind Node inliningTarget,
                        @Bind PythonLanguage language,
                        @Cached CallNode callNode,
                        @Cached PRaiseNode raiseNode) {
            DecimalModuleState state = DecimalContextUtils.getState(inliningTarget);
            Object[] conditions = new Object[Integer.bitCount(trapped)];
            int index = 0;
            for (DecimalExceptionTypes condition : DecimalContextUtils.INVALID_CONDITIONS) {
                if ((trapped & DecimalContextUtils.conditionMask(condition)) != 0) {
                    conditions[index++] = state.getExceptionType(condition);
                }
            }
            for (int i = 1; i < DecimalContextUtils.SIGNALS.length; i++) {
                if ((trapped & DecimalContextUtils.mask(DecimalContextUtils.SIGNALS[i])) != 0) {
                    conditions[index++] = state.getExceptionType(DecimalContextUtils.SIGNALS[i]);
                }
            }
            assert index == conditions.length;
            for (DecimalExceptionTypes signal : DecimalContextUtils.SIGNALS) {
                if ((trapped & DecimalContextUtils.mask(signal)) != 0) {
                    PList args = PFactory.createList(language, conditions);
                    Object exception = callNode.execute(frame, state.getExceptionType(signal), args);
                    throw raiseNode.raiseExceptionObject(inliningTarget, exception);
                }
            }
        }
    }
}
