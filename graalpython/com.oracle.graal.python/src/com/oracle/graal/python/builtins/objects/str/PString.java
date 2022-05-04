/*
 * Copyright (c) 2017, 2022, Oracle and/or its affiliates.
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

import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.objects.bytes.BytesUtils;
import com.oracle.graal.python.builtins.objects.cext.capi.PythonNativeWrapperLibrary;
import com.oracle.graal.python.builtins.objects.str.StringNodes.StringMaterializeNode;
import com.oracle.graal.python.builtins.objects.str.StringNodesFactory.StringMaterializeNodeGen;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.util.CannotCastException;
import com.oracle.graal.python.nodes.util.CastToJavaStringNode;
import com.oracle.graal.python.runtime.GilNode;
import com.oracle.graal.python.runtime.sequence.PSequence;
import com.oracle.graal.python.runtime.sequence.storage.SequenceStorage;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Exclusive;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.api.library.ExportMessage.Ignore;
import com.oracle.truffle.api.object.HiddenKey;
import com.oracle.truffle.api.object.Shape;

@ExportLibrary(InteropLibrary.class)
public final class PString extends PSequence {
    public static final HiddenKey INTERNED = new HiddenKey("_interned");

    private CharSequence value;

    public PString(Object clazz, Shape instanceShape, CharSequence value) {
        super(clazz, instanceShape);
        this.value = value;
    }

    public String getValue() {
        return StringMaterializeNodeGen.getUncached().execute(this);
    }

    public CharSequence getCharSequence() {
        return value;
    }

    void setCharSequence(String materialized) {
        this.value = materialized;
    }

    @Override
    public String toString() {
        return value.toString();
    }

    @Override
    public SequenceStorage getSequenceStorage() {
        CompilerDirectives.transferToInterpreterAndInvalidate();
        throw new UnsupportedOperationException();
    }

    @Override
    public int hashCode() {
        if (value instanceof LazyString) {
            return value.toString().hashCode();
        }
        return value.hashCode();
    }

    @Ignore
    @Override
    public boolean equals(Object obj) {
        return obj != null && obj.equals(value);
    }

    public boolean isNative() {
        return getNativeWrapper() != null && PythonNativeWrapperLibrary.getUncached().isNative(getNativeWrapper());
    }

    @Override
    @ExportMessage
    @SuppressWarnings("static-method")
    public boolean isString() {
        return true;
    }

    @ExportMessage
    String asString(
                    @Cached StringMaterializeNode stringMaterializeNode,
                    @Shared("gil") @Cached GilNode gil) {
        boolean mustRelease = gil.acquire();
        try {
            return stringMaterializeNode.execute(this);
        } finally {
            gil.release(mustRelease);
        }
    }

    @ExportMessage
    Object readArrayElement(long index,
                    @Cached CastToJavaStringNode cast,
                    @Shared("gil") @Cached GilNode gil) {
        boolean mustRelease = gil.acquire();
        try {
            try {
                return cast.execute(this).codePointAt((int) index);
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

    @ExportMessage.Ignore
    @TruffleBoundary(allowInlining = true)
    public static int length(String s) {
        return s.length();
    }

    @TruffleBoundary(allowInlining = true)
    public static String valueOf(char c) {
        return String.valueOf(c);
    }

    @TruffleBoundary(allowInlining = true)
    public static char charAt(String s, int i) {
        return s.charAt(i);
    }

    @TruffleBoundary(allowInlining = true)
    public static char[] toCharArray(String s) {
        return s.toCharArray();
    }

    @TruffleBoundary(allowInlining = true)
    public static int codePointAt(String s, int i) {
        return s.codePointAt(i);
    }

    @TruffleBoundary(allowInlining = true)
    public static int charCount(int codePoint) {
        return Character.charCount(codePoint);
    }

    @TruffleBoundary(allowInlining = true)
    public static boolean isHighSurrogate(char ch) {
        return Character.isHighSurrogate(ch);
    }

    @TruffleBoundary(allowInlining = true)
    public static boolean isLowSurrogate(char ch) {
        return Character.isLowSurrogate(ch);
    }

    @TruffleBoundary(allowInlining = true)
    public static int indexOf(String s, String sub, int fromIndex) {
        return s.indexOf(sub, fromIndex);
    }

    @TruffleBoundary(allowInlining = true)
    public static int lastIndexOf(String s, String sub, int fromIndex) {
        return s.lastIndexOf(sub, fromIndex);
    }

    @TruffleBoundary(allowInlining = true)
    public static String substring(String str, int start, int end) {
        return str.substring(start, end);
    }

    @TruffleBoundary(allowInlining = true)
    public static String substring(String str, int start) {
        return str.substring(start);
    }

    @TruffleBoundary
    public static boolean isWhitespace(char c) {
        return Character.isWhitespace(c);
    }

    @TruffleBoundary
    public static boolean isWhitespace(int codePoint) {
        return Character.isWhitespace(codePoint);
    }

    @TruffleBoundary(allowInlining = true)
    public static int codePointCount(String str, int beginIndex, int endIndex) {
        return str.codePointCount(beginIndex, endIndex);
    }

    @Ignore
    @TruffleBoundary
    public static boolean equals(String left, String other) {
        return left.equals(other);
    }

    @TruffleBoundary
    public static String cat(Object... args) {
        StringBuilder sb = new StringBuilder();
        for (Object arg : args) {
            sb.append(arg);
        }
        return sb.toString();
    }

    @TruffleBoundary
    public static String repr(String self) {
        boolean hasSingleQuote = self.contains("'");
        boolean hasDoubleQuote = self.contains("\"");
        boolean useDoubleQuotes = hasSingleQuote && !hasDoubleQuote;

        StringBuilder str = new StringBuilder(self.length() + 2);
        byte[] buffer = new byte[12];
        str.append(useDoubleQuotes ? '"' : '\'');
        int offset = 0;
        while (offset < self.length()) {
            int codepoint = self.codePointAt(offset);
            switch (codepoint) {
                case '"':
                    if (useDoubleQuotes) {
                        str.append("\\\"");
                    } else {
                        str.append('\"');
                    }
                    break;
                case '\'':
                    if (useDoubleQuotes) {
                        str.append('\'');
                    } else {
                        str.append("\\'");
                    }
                    break;
                case '\\':
                    str.append("\\\\");
                    break;
                default:
                    if (StringUtils.isPrintable(codepoint)) {
                        str.appendCodePoint(codepoint);
                    } else {
                        int len = BytesUtils.unicodeEscape(codepoint, 0, buffer);
                        str.ensureCapacity(str.length() + len);
                        for (int i = 0; i < len; i++) {
                            str.append((char) buffer[i]);
                        }
                    }
                    break;
            }
            offset += Character.charCount(codepoint);
        }
        str.append(useDoubleQuotes ? '"' : '\'');
        return str.toString();
    }

    @TruffleBoundary(allowInlining = true)
    public static boolean startsWith(String left, String prefix) {
        return left.startsWith(prefix);
    }

    @TruffleBoundary(allowInlining = true)
    public static boolean endsWith(String left, String suffix) {
        return left.endsWith(suffix);
    }

    @Override
    public void setSequenceStorage(SequenceStorage store) {
        CompilerDirectives.transferToInterpreterAndInvalidate();
        throw new UnsupportedOperationException();
    }

    @SuppressWarnings({"static-method", "unused"})
    public static void setItem(int idx, Object value) {
        CompilerDirectives.transferToInterpreterAndInvalidate();
        throw PRaiseNode.raiseUncached(null, PythonBuiltinClassType.PString, ErrorMessages.OBJ_DOES_NOT_SUPPORT_ITEM_ASSIGMENT);
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
