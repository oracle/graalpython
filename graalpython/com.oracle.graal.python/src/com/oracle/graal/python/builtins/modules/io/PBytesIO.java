/*
 * Copyright (c) 2021, 2023, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.builtins.modules.io;

import static com.oracle.graal.python.builtins.PythonBuiltinClassType.BufferError;

import com.oracle.graal.python.builtins.objects.buffer.PythonBufferAccessLibrary;
import com.oracle.graal.python.builtins.objects.bytes.PByteArray;
import com.oracle.graal.python.builtins.objects.memoryview.BufferLifecycleManager;
import com.oracle.graal.python.builtins.objects.object.PythonBuiltinObject;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.runtime.object.PythonObjectFactory;
import com.oracle.truffle.api.object.Shape;

public final class PBytesIO extends PythonBuiltinObject {
    private PByteArray buf;
    private int pos;
    private int stringSize;
    private boolean escaped;
    private final BufferLifecycleManager exports;

    public PBytesIO(Object cls, Shape instanceShape) {
        super(cls, instanceShape);
        exports = new BufferLifecycleManager();
    }

    public boolean hasBuf() {
        return buf != null;
    }

    public PByteArray getBuf() {
        return buf;
    }

    public void setBuf(PByteArray buf) {
        this.buf = buf;
    }

    public int getPos() {
        return pos;
    }

    public void setPos(int pos) {
        this.pos = pos;
    }

    public void incPos(int n) {
        this.pos += n;
    }

    public int getStringSize() {
        return stringSize;
    }

    public void setStringSize(int size) {
        this.stringSize = size;
    }

    public int getExports() {
        return exports.getExports().get();
    }

    public void checkExports(PRaiseNode raise) {
        if (getExports() != 0) {
            throw raise.raise(BufferError, ErrorMessages.EXISTING_EXPORTS_OF_DATA_OBJECT_CANNOT_BE_RE_SIZED);
        }
    }

    /**
     * @see #unshareIfNecessary
     */
    public void markEscaped() {
        escaped = true;
    }

    /**
     * CPython has an optimization that it mutates the internal bytes object if its refcount is 1
     * (so the mutation cannot be observed by others). We don't have refcounts, so we at least try
     * to remember if the current buffer has never been escaped this object. Then we can can return
     * it without copying the first time the program asks for the whole contents. So that a sequence
     * of writes followed by one getvalue call doesn't have to copy the whole buffer at the end.
     */
    public void unshareIfNecessary(PythonBufferAccessLibrary bufferLib, PythonObjectFactory factory) {
        if (escaped || getExports() != 0) {
            buf = factory.createByteArray(bufferLib.getCopiedByteArray(buf));
            escaped = false;
        }
    }

    public void unshareAndResize(PythonBufferAccessLibrary bufferLib, PythonObjectFactory factory, int size, boolean truncate) {
        int origLength = bufferLib.getBufferLength(getBuf());
        int alloc;
        if (truncate && size < origLength / 2) {
            /* Major downsize; resize down to exact size. */
            alloc = size;
        } else if (size < origLength) {
            /* Within allocated size; quick exit */
            unshareIfNecessary(bufferLib, factory);
            return;
        } else if (size <= origLength * 1.125) {
            /* Moderate upsize; overallocate similar to list_resize() */
            alloc = size + (size >> 3) + (size < 9 ? 3 : 6);
            // Handle overflow
            if (alloc < size) {
                alloc = size;
            }
        } else {
            /* Major upsize; resize up to exact size */
            alloc = size;
        }
        byte[] newBuf = new byte[alloc];
        bufferLib.readIntoByteArray(getBuf(), 0, newBuf, 0, Math.min(stringSize, size));
        setBuf(factory.createByteArray(newBuf));
        escaped = false;
    }
}
