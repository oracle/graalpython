/*
 * Copyright (c) 2017, 2018, Oracle and/or its affiliates.
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

import com.oracle.graal.python.builtins.objects.object.PythonBuiltinObject;
import com.oracle.graal.python.builtins.objects.type.LazyPythonClass;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;

public final class PInt extends PythonBuiltinObject {

    private final BigInteger value;

    public PInt(LazyPythonClass clazz, BigInteger value) {
        super(clazz);
        assert value != null;
        this.value = value;
    }

    public BigInteger getValue() {
        return value;
    }

    public boolean isOne() {
        return value.equals(BigInteger.ONE);
    }

    public boolean isZero() {
        return value.equals(BigInteger.ZERO);
    }

    @Override
    public int hashCode() {
        return value.hashCode();
    }

    @Override
    @TruffleBoundary
    public boolean equals(Object obj) {
        if (obj instanceof PInt) {
            return value.equals(((PInt) obj).getValue());
        }
        return false;
    }

    @Override
    public int compareTo(Object o) {
        if (o != null && PInt.class == o.getClass()) {
            return compareTo((PInt) o);
        }
        return super.compareTo(o);
    }

    @TruffleBoundary
    private int compareTo(PInt o) {
        return value.compareTo(o.value);
    }

    @Override
    @TruffleBoundary
    public String toString() {
        return value.toString();
    }

    @TruffleBoundary
    public double doubleValue() {
        return value.doubleValue();
    }

    @TruffleBoundary
    public int intValue() {
        return value.intValue();
    }

    @TruffleBoundary(transferToInterpreterOnException = false)
    public int intValueExact() {
        return value.intValueExact();
    }

    @TruffleBoundary
    public long longValue() {
        return value.longValue();
    }

    @TruffleBoundary(transferToInterpreterOnException = false)
    public long longValueExact() {
        return value.longValueExact();
    }

    public PInt max(PInt val) {
        return (compareTo(val) > 0 ? this : val);
    }

    public PInt min(PInt val) {
        return (compareTo(val) < 0 ? this : val);
    }

    @TruffleBoundary
    public int bitCount() {
        return value.bitCount();
    }

    @TruffleBoundary
    public boolean isZeroOrPositive() {
        return value.compareTo(BigInteger.ZERO) >= 0;
    }

    @TruffleBoundary
    public boolean isZeroOrNegative() {
        return value.compareTo(BigInteger.ZERO) <= 0;
    }

    public static int intValue(boolean bool) {
        return bool ? 1 : 0;
    }

    public static double doubleValue(boolean right) {
        return right ? 1.0 : 0.0;
    }

    public static int intValueExact(long val) {
        if (!isIntRange(val)) {
            throw new ArithmeticException();
        }
        return (int) val;
    }

    public static boolean isByteRange(int val) {
        return val >= 0 && val < 256;
    }

    public static boolean isByteRange(long val) {
        return val >= 0 && val < 256;
    }

    public static byte byteValueExact(int val) {
        if (!isByteRange(val)) {
            throw new ArithmeticException();
        }
        return (byte) val;
    }

    public static byte byteValueExact(long val) {
        if (!isByteRange(val)) {
            throw new ArithmeticException();
        }
        return (byte) val;
    }

    @TruffleBoundary(transferToInterpreterOnException = false)
    public byte byteValueExact() {
        return value.byteValueExact();
    }

    public static boolean isIntRange(long val) {
        return val == (int) val;
    }

    public boolean isNative() {
        return getNativeWrapper() != null && getNativeWrapper().isNative();
    }

}
