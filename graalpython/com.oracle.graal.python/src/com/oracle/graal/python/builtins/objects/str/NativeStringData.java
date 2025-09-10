/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.builtins.objects.str;

import com.oracle.graal.python.runtime.sequence.storage.NativeByteSequenceStorage;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.strings.TruffleString;

public final class NativeStringData {
    private static final byte KIND_ASCII = 0;
    private static final byte KIND_1BYTE = 1;
    private static final byte KIND_2BYTE = 2;
    private static final byte KIND_4BYTE = 4;
    private final byte kind;
    // We need the storage object for memory management, don't inline its fields here
    private final NativeByteSequenceStorage storage;

    private NativeStringData(int charSize, boolean isAscii, NativeByteSequenceStorage storage) {
        assert charSize == 1 || charSize == 2 || charSize == 4;
        assert !isAscii || charSize == 1;
        this.kind = isAscii ? KIND_ASCII : (byte) charSize;
        this.storage = storage;
    }

    public static NativeStringData create(int charSize, boolean isAscii, Object ptr, int length) {
        return new NativeStringData(charSize, isAscii, NativeByteSequenceStorage.create(ptr, length, length, true));
    }

    public boolean isAscii() {
        return kind == KIND_ASCII;
    }

    public int getCharSize() {
        return kind != 0 ? kind : KIND_1BYTE;
    }

    public Object getPtr() {
        return storage.getPtr();
    }

    public int length() {
        return storage.length();
    }

    public TruffleString toTruffleString(TruffleString.FromNativePointerNode fromNativePointerNode) {
        TruffleString.Encoding encoding = switch (kind) {
            case KIND_ASCII -> TruffleString.Encoding.US_ASCII;
            case KIND_1BYTE -> TruffleString.Encoding.ISO_8859_1;
            case KIND_2BYTE -> TruffleString.Encoding.UTF_16;
            case KIND_4BYTE -> TruffleString.Encoding.UTF_32;
            default -> throw CompilerDirectives.shouldNotReachHere();
        };
        // NativeByteSequenceStorage implements asPointer
        return fromNativePointerNode.execute(storage, 0, storage.length(), encoding, false);
    }
}
