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
package com.oracle.graal.python.runtime.interop;

import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.InvalidArrayIndexException;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;

@ExportLibrary(InteropLibrary.class)
final class MergedPropertyNames implements TruffleObject {

    private final Object[] keys;
    private final long[] size;

    protected MergedPropertyNames(Object[] keys) throws UnsupportedMessageException {
        this.keys = keys;
        size = new long[keys.length];
        long s = 0L;
        InteropLibrary interop = InteropLibrary.getUncached();
        for (int i = 0; i < keys.length; i++) {
            s += interop.getArraySize(keys[i]);
            size[i] = s;
        }
    }

    @ExportMessage
    @SuppressWarnings("static-method")
    boolean hasArrayElements() {
        return true;
    }

    @ExportMessage
    long getArraySize() {
        return size[size.length - 1];
    }

    @ExportMessage
    boolean isArrayElementReadable(long index,
                    @Shared("interop") @CachedLibrary(limit = "5") InteropLibrary interop) {
        if (index >= 0) {
            for (int i = 0; i < keys.length; i++) {
                if (index < size[i]) {
                    long start = (i == 0) ? 0 : size[i - 1];
                    return interop.isArrayElementReadable(keys[i], index - start);
                }
            }
        }
        return false;
    }

    @ExportMessage
    Object readArrayElement(long index,
                    @Shared("interop") @CachedLibrary(limit = "5") InteropLibrary interop)
                    throws InvalidArrayIndexException, UnsupportedMessageException {
        if (index >= 0) {
            for (int i = 0; i < keys.length; i++) {
                if (index < size[i]) {
                    long start = (i == 0) ? 0 : size[i - 1];
                    return interop.readArrayElement(keys[i], index - start);
                }
            }
        }
        throw InvalidArrayIndexException.create(index);
    }

}
