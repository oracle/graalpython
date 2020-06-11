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

import java.math.BigInteger;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import static com.oracle.graal.python.builtins.PythonBuiltinClassType.TypeError;
import com.oracle.graal.python.builtins.objects.cext.PythonNativeWrapperLibrary;
import com.oracle.graal.python.builtins.objects.function.PArguments.ThreadState;
import com.oracle.graal.python.builtins.objects.object.PythonBuiltinObject;
import com.oracle.graal.python.builtins.objects.object.PythonObjectLibrary;
import com.oracle.graal.python.builtins.objects.type.LazyPythonClass;
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
import com.oracle.truffle.api.object.DynamicObject;
import com.oracle.truffle.api.profiles.ConditionProfile;

@ExportLibrary(InteropLibrary.class)
public final class PInt extends PythonBuiltinObject {

    private final BigInteger value;

    public PInt(LazyPythonClass clazz, DynamicObject storage, BigInteger value) {
        super(clazz, storage);
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

    @TruffleBoundary(allowInlining = true)
    public boolean isZero() {
        return value.equals(BigInteger.ZERO);
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
        try {
            byteValueExact();
            return true;
        } catch (ArithmeticException e) {
            return false;
        }
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
    boolean fitsInShort(@CachedLibrary("this.intValue()") InteropLibrary interop) {
        try {
            return interop.fitsInShort(intValueExact());
        } catch (ArithmeticException e) {
            return false;
        }
    }

    @ExportMessage(limit = "1")
    short asShort(@CachedLibrary("this.intValue()") InteropLibrary interop) throws UnsupportedMessageException {
        try {
            return interop.asShort(intValueExact());
        } catch (ArithmeticException e) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            throw UnsupportedMessageException.create();
        }
    }

    @ExportMessage
    public boolean fitsInInt() {
        try {
            intValueExact();
            return true;
        } catch (ArithmeticException e) {
            return false;
        }
    }

    @ExportMessage
    public int asInt() {
        return this.intValueExact();
    }

    @ExportMessage
    public boolean fitsInLong() {
        try {
            this.longValueExact();
            return true;
        } catch (ArithmeticException e) {
            return false;
        }
    }

    @ExportMessage
    public long asLong() {
        return this.longValueExact();
    }

    @ExportMessage(limit = "1")
    boolean fitsInFloat(@CachedLibrary("this.longValue()") InteropLibrary interop) {
        try {
            return interop.fitsInFloat(longValueExact());
        } catch (ArithmeticException e) {
            return false;
        }
    }

    @ExportMessage(limit = "1")
    float asFloat(@CachedLibrary("this.longValue()") InteropLibrary interop) throws UnsupportedMessageException {
        try {
            return interop.asFloat(longValueExact());
        } catch (ArithmeticException e) {
            throw UnsupportedMessageException.create();
        }
    }

    @ExportMessage(limit = "1")
    boolean fitsInDouble(@CachedLibrary("this.longValue()") InteropLibrary interop) {
        try {
            return interop.fitsInDouble(longValueExact());
        } catch (ArithmeticException e) {
            return false;
        }
    }

    @ExportMessage(limit = "1")
    double asDouble(@CachedLibrary("this.longValue()") InteropLibrary interop) throws UnsupportedMessageException {
        try {
            return interop.asDouble(longValueExact());
        } catch (ArithmeticException e) {
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
    public int asFileDescriptor(
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
                    @Exclusive @Cached CastToJavaDoubleNode castToDouble,
                    @Exclusive @Cached() ConditionProfile hasIndexFunc,
                    @Exclusive @Cached PRaiseNode raise) {
        if (hasIndexFunc.profile(lib.canBeIndex(this))) {
            return castToDouble.execute(lib.asIndex(this));
        }
        throw raise.raise(TypeError, ErrorMessages.MUST_BE_REAL_NUMBER, this);
    }

    @SuppressWarnings("static-method")
    @ExportMessage
    public boolean canBeJavaLong() {
        return true;
    }

    @ExportMessage
    public long asJavaLong(
                    @Cached CastToJavaLongLossyNode castToLong) {
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

    @Override
    public int hashCode() {
        return value.hashCode();
    }

    public int compareTo(PInt right) {
        return compareTo(value, right.getValue());
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

    public int intValue() {
        return intValue(value);
    }

    @TruffleBoundary
    private static int intValue(BigInteger value) {
        return value.intValue();
    }

    public int intValueExact() {
        return intValueExact(value);
    }

    @TruffleBoundary
    private static int intValueExact(BigInteger value) {
        return value.intValueExact();
    }

    public long longValue() {
        return longValue(value);
    }

    @TruffleBoundary
    public static long longValue(BigInteger integer) {
        return integer.longValue();
    }

    public long longValueExact() throws ArithmeticException {
        return longValueExact(value);
    }

    @TruffleBoundary
    static long longValueExact(BigInteger value) throws ArithmeticException {
        return value.longValueExact();
    }

    public PInt max(PInt val) {
        return (compareTo(val) > 0 ? this : val);
    }

    public PInt min(PInt val) {
        return (compareTo(val) < 0 ? this : val);
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

    @TruffleBoundary
    private static byte byteValueExact(BigInteger value) {
        return value.byteValueExact();
    }

    public byte[] toByteArray() {
        return toByteArray(value);
    }

    @TruffleBoundary
    private static byte[] toByteArray(BigInteger delegate) {
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
}
