/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.builtins.objects.buffer;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.library.GenerateLibrary;
import com.oracle.truffle.api.library.GenerateLibrary.Abstract;
import com.oracle.truffle.api.library.Library;
import com.oracle.truffle.api.library.LibraryFactory;

@GenerateLibrary
public abstract class PythonBufferAccessLibrary extends Library {
    public abstract int getBufferLength(Object receiver);

    public boolean hasInternalByteArray(@SuppressWarnings("unused") Object receiver) {
        return false;
    }

    @Abstract(ifExported = "hasInternalByteArray")
    public byte[] getInternalByteArray(@SuppressWarnings("unused") Object receiver) {
        throw CompilerDirectives.shouldNotReachHere("getInternalByteArray");
    }

    public abstract void copyFrom(Object receiver, int srcOffset, byte[] dest, int destOffset, int length);

    public abstract void copyTo(Object receiver, int destOffset, byte[] src, int srcOffset, int length);

    public final byte[] getCopiedByteArray(Object receiver) {
        int len = getBufferLength(receiver);
        byte[] bytes = new byte[len];
        copyFrom(receiver, 0, bytes, 0, len);
        return bytes;
    }

    public final byte[] getInternalOrCopiedByteArray(Object receiver) {
        if (hasInternalByteArray(receiver)) {
            return getInternalByteArray(receiver);
        } else {
            return getCopiedByteArray(receiver);
        }
    }

    public void release(@SuppressWarnings("unused") Object receiver) {
    }

    static final LibraryFactory<PythonBufferAccessLibrary> FACTORY = LibraryFactory.resolve(PythonBufferAccessLibrary.class);

    public static LibraryFactory<PythonBufferAccessLibrary> getFactory() {
        return FACTORY;
    }

    public static PythonBufferAccessLibrary getUncached() {
        return FACTORY.getUncached();
    }
}
