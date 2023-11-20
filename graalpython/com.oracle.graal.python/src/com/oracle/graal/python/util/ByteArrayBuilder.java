/*
 * Copyright (c) 2023, 2023, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.util;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.builtins.objects.buffer.PythonBufferAccessLibrary;
import com.oracle.graal.python.builtins.objects.buffer.PythonBufferAcquireLibrary;
import com.oracle.graal.python.nodes.PNodeWithContext;
import com.oracle.graal.python.runtime.IndirectCallData;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.GenerateCached;
import com.oracle.truffle.api.dsl.GenerateInline;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.nodes.Node;

/**
 * @see ArrayBuilder
 */
public final class ByteArrayBuilder {
    private byte[] data;
    private int size;

    public ByteArrayBuilder() {
        this(8);
    }

    public ByteArrayBuilder(int capacity) {
        this.data = new byte[capacity];
    }

    public void add(byte item) {
        ensureCanAppend(1);
        this.data[size++] = item;
    }

    public void add(byte[] bytes, int len) {
        ensureCanAppend(len);
        PythonUtils.arraycopy(bytes, 0, data, size, len);
        size += len;
    }

    public int get(int index) {
        assert index >= 0 && index < size;
        return data[index];
    }

    public byte[] toArray() {
        return arrayCopyOf(data, size);
    }

    public int size() {
        return size;
    }

    private static byte[] arrayCopyOf(byte[] original, int newLength) {
        byte[] copy = new byte[newLength];
        PythonUtils.arraycopy(original, 0, copy, 0, Math.min(newLength, original.length));
        return copy;
    }

    private void ensureCanAppend(int bytesToAdd) {
        try {
            int newSize = PythonUtils.addExact(size, bytesToAdd);
            if (newSize > data.length) {
                int sizeTimes2 = PythonUtils.multiplyExact(size, 2);
                if (newSize < sizeTimes2) {
                    newSize = sizeTimes2;
                }
            }
            data = arrayCopyOf(data, newSize);
        } catch (OverflowException e) {
            CompilerDirectives.transferToInterpreter();
            throw new OutOfMemoryError();
        }
    }

    @GenerateInline
    @GenerateCached(false)
    public abstract static class AppendBytesNode extends PNodeWithContext {
        public abstract void execute(VirtualFrame frame, Node inliningTarget, ByteArrayBuilder builder, Object bytes);

        @Specialization(limit = "3")
        static void appendBytes(VirtualFrame frame, Node inliningTarget, ByteArrayBuilder builder, Object data,
                        @Cached("createFor(this)") IndirectCallData indirectCallData,
                        @CachedLibrary("data") PythonBufferAcquireLibrary bufferAcquireLib,
                        @CachedLibrary(limit = "3") @Shared PythonBufferAccessLibrary bufferLib) {
            Object dataBuffer = bufferAcquireLib.acquireReadonly(data, frame, PythonContext.get(inliningTarget), PythonLanguage.get(inliningTarget), indirectCallData);
            try {
                int len = bufferLib.getBufferLength(dataBuffer);
                byte[] src = bufferLib.getInternalOrCopiedByteArray(dataBuffer);
                builder.add(src, len);
            } finally {
                bufferLib.release(dataBuffer, frame, indirectCallData);
            }
        }

    }
}
