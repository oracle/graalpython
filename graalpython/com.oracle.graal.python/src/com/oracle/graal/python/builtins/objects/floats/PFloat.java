/*
 * Copyright (c) 2017, 2020, Oracle and/or its affiliates.
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

import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.objects.cext.PythonNativeWrapperLibrary;
import com.oracle.graal.python.builtins.objects.object.PythonBuiltinObject;
import com.oracle.graal.python.builtins.objects.object.PythonObjectLibrary;
import com.oracle.graal.python.builtins.objects.type.LazyPythonClass;
import com.oracle.graal.python.nodes.util.CastToJavaDoubleNode;
import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.api.library.ExportMessage.Ignore;
import com.oracle.truffle.api.object.DynamicObject;

@ExportLibrary(InteropLibrary.class)
@ExportLibrary(PythonObjectLibrary.class)
public class PFloat extends PythonBuiltinObject {

    protected final double value;

    public PFloat(LazyPythonClass clazz, DynamicObject storage, double value) {
        super(clazz, storage);
        this.value = value;
    }

    public double getValue() {
        return value;
    }

    @Override
    public int hashCode() {
        return Double.valueOf(value).hashCode();
    }

    @Ignore
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
        return getNativeWrapper() != null && PythonNativeWrapperLibrary.getUncached().isNative(getNativeWrapper());
    }

    public static PFloat create(double value) {
        return create(PythonBuiltinClassType.PFloat, PythonBuiltinClassType.PFloat.newInstance(), value);
    }

    public static PFloat create(LazyPythonClass cls, DynamicObject storage, double value) {
        return new PFloat(cls, storage, value);
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

    @ExportMessage
    public boolean isNumber() {
        return true;
    }

    @ExportMessage
    boolean fitsInFloat(@CachedLibrary("this.value") InteropLibrary interop) {
        return interop.fitsInFloat(value);
    }

    @ExportMessage
    float asFloat(@CachedLibrary("this.value") InteropLibrary interop) throws UnsupportedMessageException {
        return interop.asFloat(value);
    }

    @ExportMessage
    @SuppressWarnings("static-method")
    boolean fitsInDouble() {
        return true;
    }

    @ExportMessage
    public double asDouble() {
        return this.getValue();
    }

    @ExportMessage
    boolean fitsInByte(@CachedLibrary("this.value") InteropLibrary interop) {
        return interop.fitsInByte(value);
    }

    @ExportMessage
    byte asByte(@CachedLibrary("this.value") InteropLibrary interop) throws UnsupportedMessageException {
        return interop.asByte(value);
    }

    @ExportMessage
    boolean fitsInShort(@CachedLibrary("this.value") InteropLibrary interop) {
        return interop.fitsInShort(value);
    }

    @ExportMessage
    short asShort(@CachedLibrary("this.value") InteropLibrary interop) throws UnsupportedMessageException {
        return interop.asShort(value);
    }

    @ExportMessage
    boolean fitsInInt(@CachedLibrary("this.value") InteropLibrary interop) {
        return interop.fitsInInt(value);
    }

    @ExportMessage
    int asInt(@CachedLibrary("this.value") InteropLibrary interop) throws UnsupportedMessageException {
        return interop.asInt(value);
    }

    @ExportMessage
    boolean fitsInLong(@CachedLibrary("this.value") InteropLibrary interop) {
        return interop.fitsInLong(value);
    }

    @ExportMessage
    long asLong(@CachedLibrary("this.value") InteropLibrary interop) throws UnsupportedMessageException {
        return interop.asLong(value);
    }

    @ExportMessage
    boolean isHashable() {
        return true;
    }

    @ExportMessage
    public double asJavaDouble(
                    @Cached CastToJavaDoubleNode cast) {
        return cast.execute(this);
    }
}
