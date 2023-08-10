/*
 * Copyright (c) 2017, 2023, Oracle and/or its affiliates.
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

import static com.oracle.graal.python.util.PythonUtils.TS_ENCODING;

import com.oracle.graal.python.builtins.objects.bytes.PBytes;
import com.oracle.graal.python.builtins.objects.str.StringNodes.StringMaterializeNode;
import com.oracle.graal.python.nodes.util.CannotCastException;
import com.oracle.graal.python.nodes.util.CastToTruffleStringNode;
import com.oracle.graal.python.runtime.GilNode;
import com.oracle.graal.python.runtime.sequence.PSequence;
import com.oracle.graal.python.runtime.sequence.storage.SequenceStorage;
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
import com.oracle.truffle.api.object.HiddenKey;
import com.oracle.truffle.api.object.Shape;
import com.oracle.truffle.api.strings.TruffleString;

@SuppressWarnings("truffle-abstract-export")
@ExportLibrary(InteropLibrary.class)
public final class PString extends PSequence {
    public static final HiddenKey INTERNED = new HiddenKey("_interned");

    private TruffleString materializedValue;
    private NativeCharSequence nativeCharSequence;

    /*
     * We need to keep a reference to the encoded forms for functions that return char pointers to
     * keep the underlying memory alive (NativeSequenceStorage frees memory in finalizer).
     */
    private PBytes utf8Bytes;
    private PBytes wCharBytes;

    public PString(Object clazz, Shape instanceShape, NativeCharSequence value) {
        super(clazz, instanceShape);
        this.nativeCharSequence = value;
    }

    public PString(Object clazz, Shape instanceShape, TruffleString value) {
        super(clazz, instanceShape);
        assert value != null;
        this.materializedValue = value;
    }

    @TruffleBoundary
    public TruffleString getValueUncached() {
        return isMaterialized() ? getMaterialized() : StringMaterializeNode.executeUncached(this);
    }

    public boolean isNativeCharSequence() {
        return nativeCharSequence != null;
    }

    public boolean isNativeMaterialized() {
        assert isNativeCharSequence();
        return nativeCharSequence.isMaterialized();
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

    public NativeCharSequence getNativeCharSequence() {
        assert isNativeCharSequence();
        return nativeCharSequence;
    }

    @Override
    public String toString() {
        return isMaterialized() ? materializedValue.toJavaStringUncached() : nativeCharSequence.toString();
    }

    public PBytes getUtf8Bytes() {
        return utf8Bytes;
    }

    public void setUtf8Bytes(PBytes bytes) {
        this.utf8Bytes = bytes;
    }

    public PBytes getWCharBytes() {
        return wCharBytes;
    }

    public void setWCharBytes(PBytes bytes) {
        this.wCharBytes = bytes;
    }

    @Override
    public SequenceStorage getSequenceStorage() {
        CompilerDirectives.transferToInterpreterAndInvalidate();
        throw new UnsupportedOperationException();
    }

    @Override
    public int hashCode() {
        return isMaterialized() ? materializedValue.hashCode() : nativeCharSequence.hashCode();
    }

    @Ignore
    @Override
    public boolean equals(Object obj) {
        return obj != null && obj.equals(isMaterialized() ? materializedValue : nativeCharSequence);
    }

    @Override
    @ExportMessage
    @SuppressWarnings("static-method")
    public boolean isString() {
        return true;
    }

    @ExportMessage
    String asString(
                    @Bind("$node") Node inliningTarget,
                    @Shared("materialize") @Cached StringMaterializeNode stringMaterializeNode,
                    @Shared("gil") @Cached GilNode gil,
                    @Cached TruffleString.ToJavaStringNode toJavaStringNode) {
        return toJavaStringNode.execute(asTruffleString(inliningTarget, stringMaterializeNode, gil));
    }

    @ExportMessage
    TruffleString asTruffleString(
                    @Bind("$node") Node inliningTarget,
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
                    @Bind("$node") Node inliningTarget,
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

    @Override
    public void setSequenceStorage(SequenceStorage store) {
        CompilerDirectives.transferToInterpreterAndInvalidate();
        throw new UnsupportedOperationException();
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
