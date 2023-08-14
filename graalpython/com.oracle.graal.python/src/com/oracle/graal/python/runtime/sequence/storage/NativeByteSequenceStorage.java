/*
 * Copyright (c) 2018, 2023, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.runtime.sequence.storage;

import com.oracle.graal.python.builtins.objects.buffer.PythonBufferAccessLibrary;
import com.oracle.graal.python.builtins.objects.cext.capi.transitions.CApiTransitions;
import com.oracle.graal.python.builtins.objects.cext.structs.CStructAccess;
import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;

@ExportLibrary(PythonBufferAccessLibrary.class)
public final class NativeByteSequenceStorage extends NativeSequenceStorage implements BufferStorage {

    private NativeByteSequenceStorage(Object ptr, int length, int capacity) {
        super(ptr, length, capacity);
    }

    /**
     * @param ownsMemory whether the memory should be freed when this object dies. Should be true
     *            when actually used as a sequence storage
     */
    public static NativeByteSequenceStorage create(Object ptr, int length, int capacity, boolean ownsMemory) {
        NativeByteSequenceStorage storage = new NativeByteSequenceStorage(ptr, length, capacity);
        if (ownsMemory) {
            CApiTransitions.registerNativeSequenceStorage(storage);
        }
        return storage;
    }

    @Override
    public ListStorageType getElementType() {
        return ListStorageType.Byte;
    }

    @Override
    public String toString(boolean isList) {
        CompilerAsserts.neverPartOfCompilation();
        return String.format("%sNativeByteSequenceStorage(len=%d, cap=%d) at %s%s", isList ? "[" : "(", length, capacity, getPtr(), isList ? "]" : ")");
    }

    @ExportMessage
    @SuppressWarnings("static-method")
    boolean isBuffer() {
        return true;
    }

    @ExportMessage
    @SuppressWarnings("static-method")
    boolean isReadonly() {
        return false;
    }

    @ExportMessage
    int getBufferLength() {
        return length;
    }

    @ExportMessage
    byte readByte(int byteOffset,
                    @Cached CStructAccess.ReadByteNode readNode) {
        return readNode.readArrayElement(getPtr(), byteOffset);
    }

    @ExportMessage
    void writeByte(int byteOffset, byte value,
                    @Cached CStructAccess.WriteByteNode writeNode) {
        writeNode.writeArrayElement(getPtr(), byteOffset, value);
    }
}
