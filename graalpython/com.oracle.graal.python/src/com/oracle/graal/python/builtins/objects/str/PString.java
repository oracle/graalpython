/*
 * Copyright (c) 2017, 2025, Oracle and/or its affiliates.
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
package com.oracle.graal.python.builtins.objects.str;

import static com.oracle.graal.python.nodes.PGuards.isBuiltinPString;
import static com.oracle.graal.python.util.PythonUtils.TS_ENCODING;
import static com.oracle.graal.python.util.PythonUtils.builtinClassToType;

import com.oracle.graal.python.builtins.objects.bytes.PBytes;
import com.oracle.graal.python.builtins.objects.object.PythonBuiltinObject;
import com.oracle.graal.python.builtins.objects.str.StringNodes.StringMaterializeNode;
import com.oracle.graal.python.nodes.HiddenAttr;
import com.oracle.graal.python.nodes.util.CannotCastException;
import com.oracle.graal.python.nodes.util.CastToTruffleStringNode;
import com.oracle.graal.python.runtime.GilNode;
import com.oracle.graal.python.util.PythonUtils;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Exclusive;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.api.library.ExportMessage.Ignore;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.object.Shape;
import com.oracle.truffle.api.strings.TruffleString;

@SuppressWarnings("truffle-abstract-export")
@ExportLibrary(InteropLibrary.class)
public final class PString extends PythonBuiltinObject {
    private TruffleString materializedValue;

    public PString(Object clazz, Shape instanceShape, TruffleString value) {
        super(builtinClassToType(clazz), instanceShape);
        this.materializedValue = value;
    }

    @TruffleBoundary
    public TruffleString getValueUncached() {
        return isMaterialized() ? getMaterialized() : StringMaterializeNode.executeUncached(this);
    }

    public NativeStringData getNativeStringData(Node inliningTarget, HiddenAttr.ReadNode readNode) {
        return (NativeStringData) readNode.execute(inliningTarget, this, HiddenAttr.PSTRING_NATIVE_DATA, null);
    }

    public void setNativeStringData(Node inliningTarget, HiddenAttr.WriteNode writeNode, NativeStringData value) {
        writeNode.execute(inliningTarget, this, HiddenAttr.PSTRING_NATIVE_DATA, value);
    }

    public PBytes getUtf8Bytes(Node inliningTarget, HiddenAttr.ReadNode readNode) {
        return (PBytes) readNode.execute(inliningTarget, this, HiddenAttr.PSTRING_UTF8, null);
    }

    public void setUtf8Bytes(Node inliningTarget, HiddenAttr.WriteNode writeNode, PBytes value) {
        writeNode.execute(inliningTarget, this, HiddenAttr.PSTRING_UTF8, value);
    }

    public PBytes getWCharBytes(Node inliningTarget, HiddenAttr.ReadNode readNode) {
        return (PBytes) readNode.execute(inliningTarget, this, HiddenAttr.PSTRING_WCHAR, null);
    }

    public void setWCharBytes(Node inliningTarget, HiddenAttr.WriteNode writeNode, PBytes value) {
        writeNode.execute(inliningTarget, this, HiddenAttr.PSTRING_WCHAR, value);
    }

    public boolean isMaterialized() {
        return materializedValue != null;
    }

    public TruffleString getMaterialized() {
        assert isMaterialized();
        return materializedValue;
    }

    public void setMaterialized(TruffleString materialized) {
        assert !isMaterialized();
        materializedValue = materialized;
    }

    @TruffleBoundary
    public void intern() {
        assert isBuiltinPString(this);
        TruffleString ts = getValueUncached();
        TruffleString interned = PythonUtils.internString(ts);
        materializedValue = interned;
    }

    @Override
    public String toString() {
        return isMaterialized() ? materializedValue.toJavaStringUncached() : "<unmaterialized native string>";
    }

    @Override
    public int hashCode() {
        return CastToTruffleStringNode.executeUncached(this).hashCode();
    }

    // equals is used from DynamicObject lookups
    @Ignore
    @Override
    public boolean equals(Object obj) {
        try {
            return CastToTruffleStringNode.executeUncached(this).equalsUncached(CastToTruffleStringNode.executeUncached(obj), TS_ENCODING);
        } catch (CannotCastException e) {
            return false;
        }
    }

    @ExportMessage
    @SuppressWarnings("static-method")
    public boolean isString() {
        return true;
    }

    @ExportMessage
    String asString(
                    @Bind Node inliningTarget,
                    @Shared("materialize") @Cached StringMaterializeNode stringMaterializeNode,
                    @Shared("gil") @Cached GilNode gil,
                    @Cached TruffleString.ToJavaStringNode toJavaStringNode) {
        return toJavaStringNode.execute(asTruffleString(inliningTarget, stringMaterializeNode, gil));
    }

    @ExportMessage
    TruffleString asTruffleString(
                    @Bind Node inliningTarget,
                    @Shared("materialize") @Cached StringMaterializeNode stringMaterializeNode,
                    @Shared("gil") @Cached GilNode gil) {
        boolean mustRelease = gil.acquire();
        try {
            return stringMaterializeNode.execute(inliningTarget, this);
        } finally {
            gil.release(mustRelease);
        }
    }

    @ExportMessage
    Object readArrayElement(long index,
                    @Bind Node inliningTarget,
                    @Cached CastToTruffleStringNode cast,
                    @Cached TruffleString.CodePointAtIndexNode codePointAtIndexNode,
                    @Shared("gil") @Cached GilNode gil) {
        boolean mustRelease = gil.acquire();
        try {
            try {
                return codePointAtIndexNode.execute(cast.execute(inliningTarget, this), (int) index, TS_ENCODING);
            } catch (CannotCastException e) {
                throw CompilerDirectives.shouldNotReachHere("A PString should always have an underlying CharSequence");
            }
        } finally {
            gil.release(mustRelease);
        }
    }

    @ExportMessage
    long getArraySize(
                    @Exclusive @Cached StringNodes.StringLenNode lenNode,
                    @Exclusive @Cached GilNode gil) {
        boolean mustRelease = gil.acquire();
        try {
            return lenNode.execute(this);
        } finally {
            gil.release(mustRelease);
        }
    }

    @ExportMessage
    @SuppressWarnings("unused")
    static boolean isArrayElementModifiable(PString self, long index) {
        return false;
    }

    @ExportMessage
    @SuppressWarnings("unused")
    static boolean isArrayElementInsertable(PString self, long index) {
        return false;
    }

    @ExportMessage
    @SuppressWarnings("unused")
    static boolean isArrayElementRemovable(PString self, long index) {
        return false;
    }
}
