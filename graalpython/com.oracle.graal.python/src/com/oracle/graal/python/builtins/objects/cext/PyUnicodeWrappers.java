/*
 * Copyright (c) 2018, 2020, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 *
 * Subject to the condition set forth below, permission is hereby granted to any
 * person obtaining a copy of this software, associated documentation and/or
 * data (collectively the "Software"), free of charge and under any and all
 * copyright rights in the Software, and any and all patent rights owned or
 * freely licensable by each licensor hereunder covering either (i) the
 * unmodified Software as contributed to or provided by such licensor, or (ii)
 * the Larger Works (as defined below), to deal in both
 *
 * (a) the Software, and
 *
 * (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
 * one is included with the Software each a "Larger Work" to which the Software
 * is contributed by such licensors),
 *
 * without restriction, including without limitation the rights to copy, create
 * derivative works of, display, perform, and distribute the Software and make,
 * use, sell, offer for sale, import, export, have made, and have sold the
 * Software and the Larger Work(s), and to sublicense the foregoing rights on
 * either these or other terms.
 *
 * This license is subject to the following condition:
 *
 * The above copyright notice and either this complete permission notice or at a
 * minimum a reference to the UPL must be included in all copies or substantial
 * portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package com.oracle.graal.python.builtins.objects.cext;

import static com.oracle.graal.python.builtins.objects.cext.NativeMemberNames.UNICODE_DATA_ANY;
import static com.oracle.graal.python.builtins.objects.cext.NativeMemberNames.UNICODE_DATA_LATIN1;
import static com.oracle.graal.python.builtins.objects.cext.NativeMemberNames.UNICODE_DATA_UCS2;
import static com.oracle.graal.python.builtins.objects.cext.NativeMemberNames.UNICODE_DATA_UCS4;
import static com.oracle.graal.python.builtins.objects.cext.NativeMemberNames.UNICODE_STATE_ASCII;
import static com.oracle.graal.python.builtins.objects.cext.NativeMemberNames.UNICODE_STATE_COMPACT;
import static com.oracle.graal.python.builtins.objects.cext.NativeMemberNames.UNICODE_STATE_INTERNED;
import static com.oracle.graal.python.builtins.objects.cext.NativeMemberNames.UNICODE_STATE_KIND;
import static com.oracle.graal.python.builtins.objects.cext.NativeMemberNames.UNICODE_STATE_READY;

import java.nio.charset.CharsetEncoder;
import java.nio.charset.StandardCharsets;

import com.oracle.graal.python.builtins.objects.cext.DynamicObjectNativeWrapper.PAsPointerNode;
import com.oracle.graal.python.builtins.objects.cext.DynamicObjectNativeWrapper.ToPyObjectNode;
import com.oracle.graal.python.builtins.objects.cext.UnicodeObjectNodes.UnicodeAsWideCharNode;
import com.oracle.graal.python.builtins.objects.str.PString;
import com.oracle.graal.python.builtins.objects.str.StringNodes.StringLenNode;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Exclusive;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.UnknownIdentifierException;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.llvm.spi.NativeTypeLibrary;

public abstract class PyUnicodeWrappers {
    @ExportLibrary(InteropLibrary.class)
    @ExportLibrary(NativeTypeLibrary.class)
    abstract static class PyUnicodeWrapper extends PythonNativeWrapper {

        public PyUnicodeWrapper(PString delegate) {
            super(delegate);
        }

        public PString getPString(PythonNativeWrapperLibrary lib) {
            return (PString) lib.getDelegate(this);
        }

        @ExportMessage
        boolean isPointer(
                        @Cached CExtNodes.IsPointerNode pIsPointerNode) {
            return pIsPointerNode.execute(this);
        }

        @ExportMessage
        long asPointer(
                        @Cached PAsPointerNode pAsPointerNode) {
            return pAsPointerNode.execute(this);
        }

        @ExportMessage
        void toNative(
                        @CachedLibrary("this") PythonNativeWrapperLibrary lib,
                        @Cached ToPyObjectNode toPyObjectNode,
                        @Cached InvalidateNativeObjectsAllManagedNode invalidateNode) {
            invalidateNode.execute();
            if (!lib.isNative(this)) {
                setNativePointer(toPyObjectNode.execute(this));
            }
        }

        @ExportMessage
        @SuppressWarnings("static-method")
        boolean hasNativeType() {
            // TODO implement native type
            return false;
        }

        @ExportMessage
        @SuppressWarnings("static-method")
        Object getNativeType() {
            // TODO implement native type
            return null;
        }
    }

    /**
     * A native wrapper for the {@code data} member of {@code PyUnicodeObject}.
     */
    @ExportLibrary(InteropLibrary.class)
    @ExportLibrary(NativeTypeLibrary.class)
    public static class PyUnicodeData extends PyUnicodeWrapper {
        public PyUnicodeData(PString delegate) {
            super(delegate);
        }

        @ExportMessage
        boolean hasMembers() {
            return true;
        }

        @ExportMessage
        String[] getMembers(@SuppressWarnings("unused") boolean includeInternal) {
            return new String[]{
                            UNICODE_DATA_ANY.getMemberName(),
                            UNICODE_DATA_LATIN1.getMemberName(),
                            UNICODE_DATA_UCS2.getMemberName(),
                            UNICODE_DATA_UCS4.getMemberName()
            };
        }

        @ExportMessage
        boolean isMemberReadable(String member) {
            return UNICODE_DATA_ANY.getMemberName().equals(member) ||
                            UNICODE_DATA_LATIN1.getMemberName().equals(member) ||
                            UNICODE_DATA_UCS2.getMemberName().equals(member) ||
                            UNICODE_DATA_UCS4.getMemberName().equals(member);
        }

        @ExportMessage
        Object readMember(String member,
                        @CachedLibrary("this") PythonNativeWrapperLibrary lib,
                        @Cached(value = "createNativeOrder()", uncached = "getUncachedNativeOrder()") UnicodeAsWideCharNode asWideCharNode,
                        @Cached CExtNodes.SizeofWCharNode sizeofWcharNode,
                        @Exclusive @Cached StringLenNode stringLenNode) throws UnknownIdentifierException {

            if (isMemberReadable(member)) {
                int elementSize = (int) sizeofWcharNode.execute();
                PString s = getPString(lib);
                return new PySequenceArrayWrapper(asWideCharNode.execute(s, elementSize, stringLenNode.execute(s)), elementSize);
            }
            throw UnknownIdentifierException.create(member);
        }
    }

    /**
     * A native wrapper for the {@code state} member of {@code PyASCIIObject}.
     */
    @ExportLibrary(InteropLibrary.class)
    @ExportLibrary(NativeTypeLibrary.class)
    public static class PyUnicodeState extends PyUnicodeWrapper {
        @CompilationFinal private CharsetEncoder asciiEncoder;

        public PyUnicodeState(PString delegate) {
            super(delegate);
        }

        @ExportMessage
        boolean hasMembers() {
            return true;
        }

        @ExportMessage
        String[] getMembers(@SuppressWarnings("unused") boolean includeInternal) {
            return new String[]{
                            UNICODE_STATE_INTERNED.getMemberName(),
                            UNICODE_STATE_KIND.getMemberName(),
                            UNICODE_STATE_COMPACT.getMemberName(),
                            UNICODE_STATE_ASCII.getMemberName(),
                            UNICODE_STATE_READY.getMemberName()
            };
        }

        @ExportMessage
        boolean isMemberReadable(String member) {
            return UNICODE_STATE_INTERNED.getMemberName().equals(member) ||
                            UNICODE_STATE_KIND.getMemberName().equals(member) ||
                            UNICODE_STATE_COMPACT.getMemberName().equals(member) ||
                            UNICODE_STATE_ASCII.getMemberName().equals(member) ||
                            UNICODE_STATE_READY.getMemberName().equals(member);
        }

        @ExportMessage
        Object readMember(String member,
                        @CachedLibrary("this") PythonNativeWrapperLibrary lib,
                        @Cached CExtNodes.SizeofWCharNode sizeofWcharNode) throws UnknownIdentifierException {
            // padding(24), ready(1), ascii(1), compact(1), kind(3), interned(2)
            int value = 0b000000000000000000000000_1_0_0_000_00;
            if (onlyAscii(getPString(lib).getValue())) {
                value |= 0b1_0_000_00;
            }
            value |= ((int) sizeofWcharNode.execute() << 2) & 0b11100;
            if (isMemberReadable(member)) {
                // it's a bit field; so we need to return the whole 32-bit word
                return value;
            }
            throw UnknownIdentifierException.create(member);
        }

        private boolean onlyAscii(String value) {
            if (asciiEncoder == null) {
                asciiEncoder = newAsciiEncoder();
            }
            return doCheck(value, asciiEncoder);
        }

        @TruffleBoundary
        private static CharsetEncoder newAsciiEncoder() {
            return StandardCharsets.US_ASCII.newEncoder();
        }

        @TruffleBoundary
        private static boolean doCheck(String value, CharsetEncoder asciiEncoder) {
            return asciiEncoder.canEncode(value);
        }

    }
}
