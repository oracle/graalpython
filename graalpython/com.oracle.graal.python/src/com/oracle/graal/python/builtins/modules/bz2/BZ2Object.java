/*
 * Copyright (c) 2020, 2021, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.builtins.modules.bz2;

import java.util.Arrays;

import com.oracle.graal.python.builtins.objects.ints.PInt;
import com.oracle.graal.python.builtins.objects.object.PythonBuiltinObject;
import com.oracle.graal.python.runtime.NFIBz2Support;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.graal.python.util.OverflowException;
import com.oracle.graal.python.util.PythonUtils;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.object.Shape;

public abstract class BZ2Object extends PythonBuiltinObject {

    private NFIBz2Support.Pointer pointer;

    public BZ2Object(Object cls, Shape instanceShape) {
        super(cls, instanceShape);
    }

    public final void init(Object bzst, NFIBz2Support lib) {
        this.pointer = new NFIBz2Support.Pointer(this, bzst, lib);
    }

    public final Object getBzs() {
        assert pointer != null;
        return pointer.getReference();
    }

    @CompilerDirectives.TruffleBoundary
    public final void markReleased() {
        if (pointer != null) {
            synchronized (this) {
                pointer.markReleased();
                pointer = null;
            }
        }
    }

    public static class BZ2Compressor extends BZ2Object {

        private boolean flushed;

        public BZ2Compressor(Object cls, Shape instanceShape) {
            super(cls, instanceShape);
            this.flushed = false;
        }

        public boolean isFlushed() {
            return flushed;
        }

        public void setFlushed() {
            this.flushed = true;
        }
    }

    public static class BZ2Decompressor extends BZ2Object {

        private boolean eof;
        private byte[] unusedData;
        private boolean needsInput;
        private byte[] inputBuffer;
        private int inputBufferSize;
        private int bzsAvailInReal;

        private byte[] nextIn;
        private Object nextInGuest;
        private int nextInIndex;

        public BZ2Decompressor(Object cls, Shape instanceShape) {
            super(cls, instanceShape);
            this.eof = false;
            this.needsInput = true;
            this.bzsAvailInReal = 0;
            this.inputBuffer = null;
            this.inputBufferSize = 0;
            this.unusedData = PythonUtils.EMPTY_BYTE_ARRAY;

            this.nextIn = null;
            this.nextInGuest = null;
            this.nextInIndex = 0;
        }

        public boolean isEOF() {
            return eof;
        }

        public void setEOF() {
            this.eof = true;
        }

        public byte[] getUnusedData() {
            return unusedData;
        }

        public void setUnusedData() {
            this.unusedData = Arrays.copyOfRange(nextIn, getNextInIndex(), getNextInIndex() + getBzsAvailInReal());
        }

        public boolean needsInput() {
            return needsInput;
        }

        public void setNeedsInput(boolean needsInput) {
            this.needsInput = needsInput;
        }

        public byte[] getInputBuffer() {
            return inputBuffer;
        }

        public void setInputBuffer(byte[] inputBuffer) {
            this.inputBuffer = inputBuffer;
        }

        public void createInputBuffer(int size) {
            this.inputBuffer = new byte[size];
            this.inputBufferSize = size;
        }

        public void discardInputBuffer() {
            this.inputBuffer = null;
            this.inputBufferSize = 0;
        }

        public void resizeInputBuffer(int size) {
            assert size >= inputBufferSize;
            byte[] tmp = new byte[size];
            if (inputBuffer != null && getBzsAvailInReal() != 0) {
                PythonUtils.arraycopy(inputBuffer, 0, tmp, 0, inputBuffer.length);
            }
            this.inputBuffer = tmp;
            this.inputBufferSize = size;
        }

        public int getInputBufferSize() {
            return inputBufferSize;
        }

        public void setInputBufferSize(int inputBufferSize) {
            this.inputBufferSize = inputBufferSize;
        }

        public byte[] getNextIn() {
            return nextIn;
        }

        public void setNextIn(byte[] in) {
            assert in != null;
            this.nextIn = in;
            this.nextInGuest = null;
        }

        public void clearNextIn() {
            this.nextIn = null;
            this.nextInGuest = null;
        }

        public Object getNextInGuest(PythonContext context) {
            if (nextInGuest == null) {
                this.nextInGuest = context.getEnv().asGuestValue(nextIn);
            }
            return nextInGuest;
        }

        public int getNextInIndex() {
            return nextInIndex;
        }

        public void setNextInIndex(int nextInIndex) {
            this.nextInIndex = nextInIndex;
        }

        public void setNextInIndex(long nextInIndex) throws OverflowException {
            this.nextInIndex = PInt.intValueExact(nextInIndex);
        }

        public int getBzsAvailInReal() {
            return bzsAvailInReal;
        }

        public void incBzsAvailInReal(int size) {
            this.bzsAvailInReal += size;
        }

        public void setBzsAvailInReal(int bzsAvailInReal) {
            this.bzsAvailInReal = bzsAvailInReal;
        }

        public void setBzsAvailInReal(long bzsAvailInReal) throws OverflowException {
            this.bzsAvailInReal = PInt.intValueExact(bzsAvailInReal);
        }
    }

    public static BZ2Compressor createCompressor(Object cls, Shape instanceShape) {
        return new BZ2Compressor(cls, instanceShape);
    }

    public static BZ2Decompressor createDecompressor(Object cls, Shape instanceShape) {
        return new BZ2Decompressor(cls, instanceShape);
    }
}
