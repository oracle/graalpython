/*
 * Copyright (c) 2017, 2020, Oracle and/or its affiliates.
 * Copyright (c) 2013, Regents of the University of California
 *
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification, are
 * permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this list of
 * conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice, this list of
 * conditions and the following disclaimer in the documentation and/or other materials provided
 * with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS
 * OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
 * MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE
 * COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE
 * GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED
 * AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED
 * OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.oracle.graal.python.builtins.objects.ints;

import static com.oracle.graal.python.runtime.exception.PythonErrorType.OverflowError;

import java.math.BigInteger;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.modules.SysModuleBuiltins;
import com.oracle.graal.python.builtins.objects.cext.PythonNativeWrapperLibrary;
import com.oracle.graal.python.builtins.objects.function.PArguments.ThreadState;
import com.oracle.graal.python.builtins.objects.object.PythonBuiltinObject;
import com.oracle.graal.python.builtins.objects.object.PythonObjectLibrary;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.util.CastToJavaDoubleNode;
import com.oracle.graal.python.nodes.util.CastToJavaIntExactNode;
import com.oracle.graal.python.nodes.util.CastToJavaLongLossyNode;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.graal.python.util.OverflowException;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Exclusive;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.CachedContext;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.api.library.ExportMessage.Ignore;
import com.oracle.truffle.api.object.Shape;

@ExportLibrary(InteropLibrary.class)
public final class PInt extends PythonBuiltinObject {

    public static final BigInteger MAX_INT = BigInteger.valueOf(Integer.MAX_VALUE);
    public static final BigInteger MIN_INT = BigInteger.valueOf(Integer.MIN_VALUE);
    public static final BigInteger MAX_LONG = BigInteger.valueOf(Long.MAX_VALUE);
    public static final BigInteger MIN_LONG = BigInteger.valueOf(Long.MIN_VALUE);
    private static final BigInteger MAX_BYTE = BigInteger.valueOf(Byte.MAX_VALUE);
    private static final BigInteger MIN_BYTE = BigInteger.valueOf(Byte.MIN_VALUE);
    private static final BigInteger MAX_SHORT = BigInteger.valueOf(Short.MAX_VALUE);
    private static final BigInteger MIN_SHORT = BigInteger.valueOf(Short.MIN_VALUE);

    private final BigInteger value;

    public PInt(Object clazz, Shape instanceShape, BigInteger value) {
        super(clazz, instanceShape);
        assert value != null;
        this.value = value;
    }

    public static long abs(long a) {
        return (a < 0) ? -a : a;
    }

    public BigInteger getValue() {
        return value;
    }

    @TruffleBoundary(allowInlining = true)
    public boolean isOne() {
        return value.equals(BigInteger.ONE);
    }

    public boolean isZero() {
        return value.signum() == 0;
    }

    @ExportMessage
    public boolean isBoolean(
                    @Shared("context") @CachedContext(PythonLanguage.class) PythonContext context) {
        return this == context.getCore().getTrue() || this == context.getCore().getFalse();
    }

    @ExportMessage
    public boolean asBoolean(
                    @Shared("context") @CachedContext(PythonLanguage.class) PythonContext context) throws UnsupportedMessageException {
        if (this == context.getCore().getTrue()) {
            return true;
        } else if (this == context.getCore().getFalse()) {
            return false;
        }
        throw UnsupportedMessageException.create();
    }

    @ExportMessage
    @SuppressWarnings("static-method")
    public boolean isNumber() {
        return true;
    }

    @ExportMessage
    public boolean fitsInByte() {
        return fitsIn(MIN_BYTE, MAX_BYTE);
    }

    @ExportMessage
    public byte asByte() throws UnsupportedMessageException {
        try {
            return byteValueExact();
        } catch (ArithmeticException e) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            throw UnsupportedMessageException.create();
        }
    }

    @ExportMessage(limit = "1")
    boolean fitsInShort() {
        return fitsIn(MIN_SHORT, MAX_SHORT);
    }

    @ExportMessage(limit = "1")
    short asShort(@CachedLibrary("this.intValue()") InteropLibrary interop) throws UnsupportedMessageException {
        try {
            return interop.asShort(intValueExact());
        } catch (OverflowException e) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            throw UnsupportedMessageException.create();
        }
    }

    @ExportMessage
    @TruffleBoundary
    public boolean fitsInInt() {
        return fitsIn(MIN_INT, MAX_INT);
    }

    @ExportMessage
    public int asInt() throws UnsupportedMessageException {
        try {
            return this.intValueExact();
        } catch (OverflowException e) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            throw UnsupportedMessageException.create();
        }
    }

    @ExportMessage
    public boolean fitsInLong() {
        return fitsIn(MIN_LONG, MAX_LONG);
    }

    @ExportMessage
    public long asLong() throws UnsupportedMessageException {
        try {
            return this.longValueExact();
        } catch (OverflowException e) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            throw UnsupportedMessageException.create();
        }
    }

    @ExportMessage(limit = "1")
    boolean fitsInFloat(@CachedLibrary("this.longValue()") InteropLibrary interop) {
        try {
            return fitsInLong() && interop.fitsInFloat(longValueExact());
        } catch (OverflowException e) {
            throw CompilerDirectives.shouldNotReachHere();
        }
    }

    @ExportMessage(limit = "1")
    float asFloat(@CachedLibrary("this.longValue()") InteropLibrary interop) throws UnsupportedMessageException {
        try {
            return interop.asFloat(longValueExact());
        } catch (OverflowException e) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            throw UnsupportedMessageException.create();
        }
    }

    @ExportMessage(limit = "1")
    boolean fitsInDouble(@CachedLibrary("this.longValue()") InteropLibrary interop) {
        try {
            return fitsInLong() && interop.fitsInDouble(longValueExact());
        } catch (OverflowException e) {
            throw CompilerDirectives.shouldNotReachHere();
        }
    }

    @ExportMessage(limit = "1")
    double asDouble(@CachedLibrary("this.longValue()") InteropLibrary interop) throws UnsupportedMessageException {
        try {
            return interop.asDouble(longValueExact());
        } catch (OverflowException e) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            throw UnsupportedMessageException.create();
        }
    }

    @ExportMessage
    @SuppressWarnings("static-method")
    public boolean canBeIndex() {
        return true;
    }

    @ExportMessage
    public Object asIndexWithState(@SuppressWarnings("unused") ThreadState threadState) {
        return this;
    }

    @ExportMessage
    public int asFileDescriptorWithState(@SuppressWarnings("unused") ThreadState state,
                    @Exclusive @Cached PRaiseNode raiseNode,
                    @Exclusive @Cached CastToJavaIntExactNode castToJavaIntNode) {
        try {
            return castToJavaIntNode.execute(this);
        } catch (PException e) {
            throw raiseNode.raise(PythonBuiltinClassType.OverflowError, ErrorMessages.PYTHON_INT_TOO_LARGE_TO_CONV_TO, "int");
        }
    }

    @SuppressWarnings("static-method")
    @ExportMessage
    public boolean canBeJavaDouble() {
        return true;
    }

    @ExportMessage
    public double asJavaDouble(
                    @CachedLibrary("this") PythonObjectLibrary lib,
                    @Shared("castToDouble") @Cached CastToJavaDoubleNode castToDouble) {
        return castToDouble.execute(lib.asIndex(this));
    }

    @ExportMessage
    public double asJavaDoubleWithState(ThreadState threadState,
                    @CachedLibrary("this") PythonObjectLibrary lib,
                    @Shared("castToDouble") @Cached CastToJavaDoubleNode castToDouble) {
        return castToDouble.execute(lib.asIndexWithState(this, threadState));
    }

    @SuppressWarnings("static-method")
    @ExportMessage
    public boolean canBeJavaLong() {
        return true;
    }

    @ExportMessage
    public long asJavaLong(
                    @Cached @Shared("asJavaLong") CastToJavaLongLossyNode castToLong) {
        return castToLong.execute(this);
    }

    @ExportMessage
    public long asJavaLongWithState(@SuppressWarnings("unused") ThreadState threadState,
                    @Cached @Shared("asJavaLong") CastToJavaLongLossyNode castToLong) {
        return castToLong.execute(this);
    }

    @SuppressWarnings("static-method")
    @ExportMessage
    public boolean canBePInt() {
        return true;
    }

    @ExportMessage
    public PInt asPInt() {
        return this;
    }

    @ExportMessage
    public PInt asPIntWithState(@SuppressWarnings("unused") ThreadState state) {
        return this;
    }

    @Override
    public int hashCode() {
        return value.hashCode();
    }

    public int compareTo(PInt right) {
        return compareTo(value, right.getValue());
    }

    public int compareTo(BigInteger right) {
        return compareTo(value, right);
    }

    @TruffleBoundary
    private static int compareTo(BigInteger left, BigInteger right) {
        return left.compareTo(right);
    }

    public int compareTo(long i) {
        return compareTo(value, i);
    }

    @TruffleBoundary
    private static int compareTo(BigInteger left, long right) {
        return left.compareTo(longToBigInteger(right));
    }

    @Override
    public String toString() {
        return toString(value);
    }

    @TruffleBoundary
    private static String toString(BigInteger value) {
        return value.toString();
    }

    @TruffleBoundary
    public static String toHexString(long value) {
        return Long.toHexString(value);
    }

    @TruffleBoundary
    public static BigInteger longToBigInteger(long value) {
        return BigInteger.valueOf(value);
    }

    public double doubleValue() {
        return doubleValue(value);
    }

    @TruffleBoundary
    public static double doubleValue(BigInteger value) {
        return value.doubleValue();
    }

    public double doubleValueWithOverflow(PRaiseNode raise) {
        return doubleValueWithOverflow(value, raise);
    }

    @TruffleBoundary
    public static double doubleValueWithOverflow(BigInteger value, PRaiseNode raise) {
        double d = value.doubleValue();
        if (Double.isInfinite(d)) {
            throw raise.raise(OverflowError, ErrorMessages.INT_TOO_LARGE_TO_CONVERT_TO_FLOAT);
        }
        return d;
    }

    public int intValue() {
        return intValue(value);
    }

    @TruffleBoundary(allowInlining = true)
    public static int intValue(BigInteger value) {
        return value.intValue();
    }

    public int intValueExact() throws OverflowException {
        return intValueExact(value);
    }

    public long longValue() {
        return longValue(value);
    }

    @TruffleBoundary(allowInlining = true)
    public static long longValue(BigInteger integer) {
        return integer.longValue();
    }

    public long longValueExact() throws OverflowException {
        return longValueExact(value);
    }

    public static long longValueExact(BigInteger x) throws OverflowException {
        if (!fitsIn(x, MIN_LONG, MAX_LONG)) {
            throw OverflowException.INSTANCE;
        }
        return longValue(x);
    }

    public PInt max(PInt val) {
        return (compareTo(val) > 0 ? this : val);
    }

    public PInt min(PInt val) {
        return (compareTo(val) < 0 ? this : val);
    }

    @TruffleBoundary
    public BigInteger inc() {
        return value.add(BigInteger.ONE);
    }

    public int bitLength() {
        return bitLength(value);
    }

    @TruffleBoundary
    public static int bitLength(BigInteger value) {
        return value.bitLength();
    }

    public int bitCount() {
        return bitCount(value);
    }

    @TruffleBoundary
    private static int bitCount(BigInteger value) {
        return value.bitCount();
    }

    public boolean isZeroOrPositive() {
        return value.signum() >= 0;
    }

    public boolean isZeroOrNegative() {
        return value.signum() <= 0;
    }

    public boolean isNegative() {
        return value.signum() < 0;
    }

    public static int intValue(boolean bool) {
        return bool ? 1 : 0;
    }

    public static double doubleValue(boolean right) {
        return right ? 1.0 : 0.0;
    }

    public static int intValueExact(long val) throws OverflowException {
        if (!isIntRange(val)) {
            throw OverflowException.INSTANCE;
        }
        return (int) val;
    }

    public static int intValueExact(BigInteger x) throws OverflowException {
        if (!fitsIn(x, MIN_INT, MAX_INT)) {
            throw OverflowException.INSTANCE;
        }
        return intValue(x);
    }

    public static char charValueExact(int val) throws OverflowException {
        char t = (char) val;
        if (t != val) {
            throw OverflowException.INSTANCE;
        }
        return t;
    }

    public static char charValueExact(long val) throws OverflowException {
        char t = (char) val;
        if (t != val) {
            throw OverflowException.INSTANCE;
        }
        return t;
    }

    public static boolean isByteRange(int val) {
        return val >= 0 && val < 256;
    }

    public static boolean isByteRange(long val) {
        return val >= 0 && val < 256;
    }

    public static byte byteValueExact(int val) throws OverflowException {
        if (!isByteRange(val)) {
            throw OverflowException.INSTANCE;
        }
        return (byte) val;
    }

    public static byte byteValueExact(long val) throws OverflowException {
        if (!isByteRange(val)) {
            throw OverflowException.INSTANCE;
        }
        return (byte) val;
    }

    public byte byteValueExact() {
        return byteValueExact(value);
    }

    @TruffleBoundary(transferToInterpreterOnException = false)
    private static byte byteValueExact(BigInteger value) {
        return value.byteValueExact();
    }

    public byte[] toByteArray() {
        return toByteArray(value);
    }

    @TruffleBoundary
    public static byte[] toByteArray(BigInteger delegate) {
        return delegate.toByteArray();
    }

    public static boolean isIntRange(long val) {
        return val == (int) val;
    }

    public BigInteger abs() {
        if (value.signum() < 0) {
            return abs(value);
        } else {
            return value;
        }
    }

    @TruffleBoundary
    private static BigInteger abs(BigInteger value) {
        return value.abs();
    }

    public boolean isNative() {
        return getNativeWrapper() != null && PythonNativeWrapperLibrary.getUncached().isNative(getNativeWrapper());
    }

    @TruffleBoundary
    public BigInteger multiply(BigInteger other) {
        return this.value.multiply(other);
    }

    public BigInteger multiply(PInt other) {
        return multiply(other.value);
    }

    @TruffleBoundary
    public BigInteger add(BigInteger other) {
        return this.value.add(other);
    }

    @TruffleBoundary
    public BigInteger subtract(BigInteger other) {
        return this.value.subtract(other);
    }

    public BigInteger subtract(PInt other) {
        return subtract(other.getValue());
    }

    public BigInteger add(PInt other) {
        return add(other.value);
    }

    // We cannot export it as a message, because it can be overridden!
    @Ignore
    public long hash() {
        return hashBigInteger(value);
    }

    @TruffleBoundary
    public static long hashBigInteger(BigInteger i) {
        long h = i.remainder(BigInteger.valueOf(SysModuleBuiltins.HASH_MODULUS)).longValue();
        return h == -1 ? -2 : h;
    }

    @TruffleBoundary(allowInlining = true)
    public static boolean fitsIn(BigInteger value, BigInteger left, BigInteger right) {
        return value.compareTo(left) >= 0 && value.compareTo(right) <= 0;
    }

    private boolean fitsIn(BigInteger left, BigInteger right) {
        return fitsIn(value, left, right);
    }
}
