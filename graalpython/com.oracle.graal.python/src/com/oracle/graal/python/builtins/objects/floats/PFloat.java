/*
 * Copyright (c) 2017, 2019, Oracle and/or its affiliates.
 * Copyright (c) 2014, Regents of the University of California
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
package com.oracle.graal.python.builtins.objects.floats;

import com.oracle.graal.python.builtins.objects.object.PythonBuiltinObject;
import com.oracle.graal.python.builtins.objects.type.LazyPythonClass;
import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;

public class PFloat extends PythonBuiltinObject {

    private final double value;

    public PFloat(LazyPythonClass clazz, double value) {
        super(clazz);
        this.value = value;
    }

    public double getValue() {
        return value;
    }

    @Override
    public int hashCode() {
        return Double.valueOf(value).hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        return obj != null && PFloat.class == obj.getClass() && value == (((PFloat) obj).getValue());
    }

    @Override
    public String toString() {
        CompilerAsserts.neverPartOfCompilation();
        return Double.toString(value);
    }

    public boolean isNative() {
        return getNativeWrapper() != null && getNativeWrapper().isNative();
    }

    public static PFloat create(double value) {
        return create(null, value);
    }

    public static PFloat create(LazyPythonClass cls, double value) {
        return new PFloat(cls, value);
    }

    @TruffleBoundary
    public static String doubleToString(double item) {
        String d = Double.toString(item);
        int exp = d.indexOf("E");
        if (exp != -1) {
            int l = d.length() - 1;
            if (exp == (l - 2)) {
                if (d.charAt(exp + 1) == '-') {
                    if (Integer.valueOf(d.charAt(l) + "") == 4) {
                        /*- Java convert double when 0.000###... while Python does it when 0.0000####... */
                        d = Double.toString((item * 10)).replace(".", ".0");
                    } else {
                        d = d.substring(0, l) + "0" + d.substring(l);
                    }

                    exp = d.indexOf("E");
                }
            }
            if (exp != -1 && d.charAt(exp + 1) != '-') {
                d = d.substring(0, exp + 1) + "+" + d.substring(exp + 1, l + 1);
            }
            d = d.toLowerCase();
        }
        return d;
    }
}
