/*
 * Copyright (c) 2018, Oracle and/or its affiliates. All rights reserved.
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

import com.oracle.graal.python.builtins.objects.cext.NativeWrappers.PythonNativeWrapper;
import com.oracle.graal.python.builtins.objects.ints.PInt;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.interop.ForeignAccess;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnknownIdentifierException;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.api.nodes.Node;

/**
 * Native wrappers for managed objects such that they can be used as a C array by native code. The
 * major difference to other native wrappers is that they are copied to native memory if it receives
 * {@code TO_NATIVE}. This is primarily necessary for {@code char*} arrays.
 */
public abstract class CArrayWrappers {

    @ExportLibrary(InteropLibrary.class)
    public abstract static class CArrayWrapper extends PythonNativeWrapper {

        public CArrayWrapper(Object delegate) {
            super(delegate);
        }

        static boolean isInstance(TruffleObject o) {
            return o instanceof CArrayWrapper;
        }

        @Override
        public ForeignAccess getForeignAccess() {
            return CArrayWrapperMRForeign.ACCESS;
        }
    }

    /**
     * Unlike a
     * {@link com.oracle.graal.python.builtins.objects.cext.NativeWrappers.PythonObjectNativeWrapper}
     * object that wraps a Python unicode object, this wrapper let's a Java String look like a
     * {@code char*}.
     */
    @ExportLibrary(InteropLibrary.class)
    public static class CStringWrapper extends CArrayWrapper {

        public CStringWrapper(String delegate) {
            super(delegate);
        }

        public String getString() {
            return (String) getDelegate();
        }

        @ExportMessage
        final long getArraySize() {
            return this.getString().length();
        }

        @ExportMessage
        final boolean hasArrayElements() {
            return true;
        }

        @ExportMessage
        final Object readArrayElement(long index,
                                      @Cached.Exclusive  @Cached(allowUncached = true) ReadNode readNode) {
            return readNode.execute(this, index);
        }

        @ExportMessage
        final boolean isArrayElementReadable(long identifier) {
            return 0 <= identifier && identifier < getArraySize();
        }

        abstract static class ReadNode extends Node {
            public abstract char execute(CStringWrapper object, Object idx);

            @Specialization
            public char executeInt(CStringWrapper object, int idx) {
                String s = object.getString();
                if (idx >= 0 && idx < s.length()) {
                    return s.charAt(idx);
                } else if (idx == s.length()) {
                    return '\0';
                }
                CompilerDirectives.transferToInterpreter();
                throw UnknownIdentifierException.raise(Integer.toString(idx));
            }

            @Specialization
            public char executeLong(CStringWrapper object, long idx) {
                try {
                    return executeInt(object, PInt.intValueExact(idx));
                } catch (ArithmeticException e) {
                    CompilerDirectives.transferToInterpreter();
                    throw UnknownIdentifierException.raise(Long.toString(idx));
                }
            }
        }
    }

    /**
     * A native wrapper for arbitrary byte arrays (i.e. the store of a Python Bytes object) to be
     * used like a {@code char*} pointer.
     */
    @ExportLibrary(InteropLibrary.class)
    public static class CByteArrayWrapper extends CArrayWrapper {

        public CByteArrayWrapper(byte[] delegate) {
            super(delegate);
        }

        public byte[] getByteArray() {
            return (byte[]) getDelegate();
        }

        @ExportMessage
        final long getArraySize() {
            return this.getByteArray().length;
        }

        @ExportMessage
        final boolean hasArrayElements() {
            return true;
        }

        @ExportMessage
        final Object readArrayElement(long index,
                                      @Cached.Exclusive  @Cached(allowUncached = true) ReadNode readNode) {
            return readNode.execute(this, index);
        }

        @ExportMessage
        final boolean isArrayElementReadable(long identifier) {
            return 0 <= identifier && identifier < getArraySize();
        }

        abstract static class ReadNode extends Node {
            public abstract byte execute(CByteArrayWrapper object, Object idx);

            @Specialization
            public byte executeInt(CByteArrayWrapper object, int idx) {
                byte[] arr = object.getByteArray();
                if (idx >= 0 && idx < arr.length) {
                    return arr[idx];
                } else if (idx == arr.length) {
                    return (byte) 0;
                }
                CompilerDirectives.transferToInterpreter();
                throw UnknownIdentifierException.raise(Integer.toString(idx));
            }

            @Specialization
            public byte executeLong(CByteArrayWrapper object, long idx) {
                try {
                    return executeInt(object, PInt.intValueExact(idx));
                } catch (ArithmeticException e) {
                    CompilerDirectives.transferToInterpreter();
                    throw UnknownIdentifierException.raise(Long.toString(idx));
                }
            }
        }
    }
}
