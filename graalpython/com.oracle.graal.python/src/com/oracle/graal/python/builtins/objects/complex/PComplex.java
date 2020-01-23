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
package com.oracle.graal.python.builtins.objects.complex;

import com.oracle.graal.python.builtins.objects.object.PythonBuiltinObject;
import com.oracle.graal.python.builtins.objects.type.LazyPythonClass;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;

public final class PComplex extends PythonBuiltinObject {
    /* Prime multiplier used in string and various other hashes in CPython. */
    public static final int IMAG_MULTIPLIER = 1000003; /* 0xf4243 */

    private final double real;
    private final double imag;

    public PComplex(LazyPythonClass clazz, double real, double imaginary) {
        super(clazz);
        this.real = real;
        this.imag = imaginary;
    }

    @Override
    public boolean equals(Object c) {
        if (c instanceof PComplex) {
            return (real == ((PComplex) c).real && imag == ((PComplex) c).imag);
        } else {
            return false;
        }
    }

    @Override
    public int hashCode() {
        return super.hashCode();
    }

    public boolean notEqual(PComplex c) {
        return (real != c.real || imag != c.imag);
    }

    @SuppressWarnings({"unused", "static-method"})
    public boolean greaterEqual(PComplex c) {
        throw new RuntimeException("cannot compare complex numbers using <, <=, >, >=");
    }

    @SuppressWarnings({"unused", "static-method"})
    public boolean greaterThan(PComplex c) {
        throw new RuntimeException("cannot compare complex numbers using <, <=, >, >=");
    }

    @SuppressWarnings({"unused", "static-method"})
    public boolean lessEqual(PComplex c) {
        throw new RuntimeException("cannot compare complex numbers using <, <=, >, >=");
    }

    @SuppressWarnings({"unused", "static-method"})
    public boolean lessThan(PComplex c) {
        throw new RuntimeException("cannot compare complex numbers using <, <=, >, >=");
    }

    public double getReal() {
        return real;
    }

    public double getImag() {
        return imag;
    }

    @Override
    @TruffleBoundary
    public String toString() {
        if (Double.compare(real, 0.0) == 0) {
            return toString(imag) + "j";
        } else {
            String realString = toString(real);
            if (real == 0.0) {
                // special case where real is actually -0.0
                realString = "-0";
            }
            if (Double.compare(imag, 0.0) >= 0) {
                return String.format("(%s+%sj)", realString, toString(imag));
            } else {
                return String.format("(%s-%sj)", realString, toString(-imag));
            }
        }
    }

    private static String toString(double value) {
        if (value == Math.floor(value) && value <= Long.MAX_VALUE && value >= Long.MIN_VALUE) {
            return Long.toString((long) value);
        } else {
            if (Double.isInfinite(value)) {
                if (Double.NEGATIVE_INFINITY == value) {
                    return "-inf";
                }
                return "inf";
            } else if (Double.isNaN(value)) {
                return "nan";
            }
            return Double.toString(value);
        }
    }

}
