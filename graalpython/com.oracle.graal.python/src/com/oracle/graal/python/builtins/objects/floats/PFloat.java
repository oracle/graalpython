/*
 * Copyright (c) 2017, 2025, Oracle and/or its affiliates.
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
// skip GIL
package com.oracle.graal.python.builtins.objects.floats;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.objects.object.PythonBuiltinObject;
import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.api.library.ExportMessage.Ignore;
import com.oracle.truffle.api.object.Shape;

@SuppressWarnings("truffle-abstract-export")
@ExportLibrary(InteropLibrary.class)
public class PFloat extends PythonBuiltinObject {

    protected final double value;

    public PFloat(Object clazz, Shape instanceShape, double value) {
        super(clazz, instanceShape);
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
        return getNativeWrapper() != null && getNativeWrapper().isNative();
    }

    public static PFloat create(PythonLanguage lang, double value) {
        return create(PythonBuiltinClassType.PFloat, PythonBuiltinClassType.PFloat.getInstanceShape(lang), value);
    }

    public static PFloat create(Object cls, Shape instanceShape, double value) {
        return new PFloat(cls, instanceShape, value);
    }

    public static int compare(double x, double y) {
        return (x < y) ? -1 : ((x == y) ? 0 : 1);
    }

    /**
     * CPython does identity check in {@code PyObject_RichCompareBool}. We do not really have
     * identity for doubles, so we cannot say if NaNs, which are by definition not equal
     * (PyObjectRichCompare always returns false for NaN and NaN), are identical or not. So we
     * choose that all NaNs with equal bit patterns are identical. This method should be used in
     * places which use {@code PyObject_RichCompareBool} in CPython.
     */
    public static boolean areIdentical(double x, double y) {
        return x == y || Double.doubleToRawLongBits(x) == Double.doubleToRawLongBits(y);
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
    public boolean fitsInDouble() {
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

    @Ignore
    public static boolean fitsInFloat(double d) {
        float f = (float) d;
        return !Double.isFinite(d) || f == d;
    }
}
