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
package com.oracle.graal.python.builtins.modules.lzma;

import static com.oracle.graal.python.builtins.modules.lzma.LZMAModuleBuiltins.FORMAT_ALONE;
import static com.oracle.graal.python.builtins.modules.lzma.LZMAModuleBuiltins.FORMAT_AUTO;
import static com.oracle.graal.python.builtins.modules.lzma.LZMAModuleBuiltins.FORMAT_XZ;
import static com.oracle.graal.python.builtins.modules.lzma.LZMAModuleBuiltins.INITIAL_BUFFER_SIZE;
import static com.oracle.graal.python.builtins.objects.bytes.BytesUtils.createOutputStream;
import static com.oracle.graal.python.builtins.objects.bytes.BytesUtils.toByteArray;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;

import org.tukaani.xz.FilterOptions;
import org.tukaani.xz.FinishableOutputStream;
import org.tukaani.xz.LZMA2Options;
import org.tukaani.xz.LZMAInputStream;
import org.tukaani.xz.LZMAOutputStream;
import org.tukaani.xz.XZInputStream;
import org.tukaani.xz.XZOutputStream;
import org.tukaani.xz.common.DecoderUtil;

import com.oracle.graal.python.builtins.objects.ints.PInt;
import com.oracle.graal.python.builtins.objects.object.PythonBuiltinObject;
import com.oracle.graal.python.runtime.NFILZMASupport;
import com.oracle.graal.python.util.OverflowException;
import com.oracle.graal.python.util.PythonUtils;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.object.Shape;

public abstract class LZMAObject extends PythonBuiltinObject {

    protected int check;

    public LZMAObject(Object cls, Shape instanceShape) {
        super(cls, instanceShape);
    }

    public void setCheck(int check) {
        this.check = check;
    }

    public int getCheck() {
        return check;
    }

    public abstract static class LZMACompressor extends LZMAObject {

        private boolean flushed;

        public LZMACompressor(Object cls, Shape instanceShape) {
            super(cls, instanceShape);
            this.flushed = false;
        }

        public boolean isFlushed() {
            return flushed;
        }

        public void setFlushed() {
            this.flushed = true;
        }

        public static class Java extends LZMACompressor {
            private FinishableOutputStream lzmaStream;
            private final ByteArrayOutputStream bos;

            public Java(Object cls, Shape instanceShape) {
                super(cls, instanceShape);
                this.bos = createOutputStream(INITIAL_BUFFER_SIZE);
            }

            @TruffleBoundary
            public void lzmaStreamEncoder(FilterOptions[] optionsChain) throws IOException {
                lzmaStream = new XZOutputStream(bos, optionsChain, check);
            }

            @TruffleBoundary
            public void lzmaEasyEncoder(LZMA2Options lzmaOptions) throws IOException {
                lzmaStream = new XZOutputStream(bos, lzmaOptions, check);
            }

            @TruffleBoundary
            public void lzmaRawEncoder(FilterOptions[] optionsChain) throws IOException {
                lzmaStream = new LZMAOutputStream(bos, (LZMA2Options) optionsChain[0], true);
            }

            @TruffleBoundary
            public void lzmaAloneEncoder(LZMA2Options lzmaOptions) throws IOException {
                lzmaStream = new LZMAOutputStream(bos, lzmaOptions, -1);
            }

            @TruffleBoundary
            public void write(byte[] bytes) throws IOException {
                lzmaStream.write(bytes);
            }

            @TruffleBoundary
            public void finish() throws IOException {
                lzmaStream.finish();
            }

            public byte[] getByteArray() {
                return toByteArray(bos);
            }

            @TruffleBoundary
            public void resetBuffer() {
                bos.reset();
            }
        }

        public static class Native extends LZMACompressor {

            private NFILZMASupport.Pointer pointer;

            public Native(Object cls, Shape instanceShape) {
                super(cls, instanceShape);
            }

            public final void init(Object lzmast, NFILZMASupport lib) {
                this.pointer = new NFILZMASupport.Pointer(this, lzmast, lib);
            }

            public final Object getLzs() {
                assert pointer != null;
                return pointer.getReference();
            }

            @TruffleBoundary
            public final void markReleased() {
                if (pointer != null) {
                    synchronized (this) {
                        pointer.markReleased();
                        pointer = null;
                    }
                }
            }
        }
    }

    public abstract static class LZMADecompressor extends LZMAObject {

        protected int memlimit;
        protected int format;
        private boolean eof;
        private byte[] unusedData;
        private boolean needsInput;
        private byte[] inputBuffer;
        private int inputBufferSize;
        private int lzsAvailIn;
        private int lzsAvailOut;

        protected byte[] nextIn;
        private int nextInIndex;

        public LZMADecompressor(Object cls, Shape instanceShape) {
            super(cls, instanceShape);
            this.memlimit = Integer.MAX_VALUE;
            this.eof = false;
            this.needsInput = true;
            this.lzsAvailIn = 0;
            this.lzsAvailOut = 0;
            this.inputBuffer = null;
            this.inputBufferSize = 0;
            this.unusedData = PythonUtils.EMPTY_BYTE_ARRAY;

            this.nextIn = null;
            this.nextInIndex = 0;
        }

        public void setMemlimit(int memlimit) {
            this.memlimit = memlimit;
        }

        public int getMemlimit() {
            return memlimit;
        }

        public void setFormat(int format) {
            this.format = format;
        }

        public boolean isEOF() {
            return eof;
        }

        public void setEOF() {
            this.eof = true;
        }

        public void setEOF(boolean b) {
            this.eof = b;
        }

        public byte[] getUnusedData() {
            return unusedData;
        }

        public void setUnusedData() {
            this.unusedData = Arrays.copyOfRange(nextIn, nextInIndex, nextInIndex + lzsAvailIn);
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
            if (inputBuffer != null && lzsAvailIn != 0) {
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
        }

        public void clearNextIn() {
            this.nextIn = null;
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

        public int getLzsAvailIn() {
            return lzsAvailIn;
        }

        public int getLzsAvailOut() {
            return lzsAvailOut;
        }

        public void incLzsAvailIn(int size) {
            this.lzsAvailIn += size;
        }

        public void setLzsAvailIn(int lzsAvailIn) {
            this.lzsAvailIn = lzsAvailIn;
        }

        public void setLzsAvailIn(long lzsAvailIn) throws OverflowException {
            this.lzsAvailIn = PInt.intValueExact(lzsAvailIn);
        }

        public void setLzsAvailOut(int lzsAvailOut) {
            this.lzsAvailOut = lzsAvailOut;
        }

        public void setLzsAvailOut(long lzsAvailOut) throws OverflowException {
            this.lzsAvailOut = PInt.intValueExact(lzsAvailOut);
        }

        public static class Java extends LZMADecompressor {
            private LZMANodes.LZMAByteInputStream input;
            private InputStream lzs;

            private int currentAutoFormat;
            private int currentDataSize;
            private boolean decompressed;

            public Java(Object cls, Shape instanceShape) {
                super(cls, instanceShape);
            }

            public boolean isFormatAuto() {
                return format == FORMAT_AUTO;
            }

            @TruffleBoundary
            public void initialize() throws IOException {
                if (format == FORMAT_AUTO) {
                    switchStream();
                } else if (format == FORMAT_XZ) {
                    createXZ();
                } else if (format == FORMAT_ALONE) {
                    createLZMA();
                } else {
                    // XXX: #FORMAT_RAW not supported.
                    throw new IllegalStateException();
                }
            }

            public boolean isInitialized() {
                return lzs != null;
            }

            @TruffleBoundary
            public void setInput() {
                if (input == null) {
                    input = createLZMAByteInputStream(getNextIn(), getNextInIndex(), getLzsAvailIn());
                } else {
                    input.setBuffer(getNextIn(), getNextInIndex());
                }
            }

            @TruffleBoundary
            public void adjustNextIn() {
                int pos = input.getNextInIndex();
                byte[] tmp = Arrays.copyOfRange(getNextIn(), pos, getNextIn().length);
                setNextIn(tmp);
                setNextInIndex(0);
            }

            @TruffleBoundary
            public int read(byte[] result) throws IOException {
                return lzs.read(result);
            }

            @TruffleBoundary
            public void update(int availOut) {
                setNextInIndex(input.getNextInIndex());
                setLzsAvailIn(input.getAvailIn());
                setLzsAvailOut(availOut);
            }

            @TruffleBoundary
            protected static LZMANodes.LZMAByteInputStream createLZMAByteInputStream(byte[] buf, int offset, int length) {
                return new LZMANodes.LZMAByteInputStream(buf, offset, length);
            }

            @TruffleBoundary
            public boolean sameData() {
                return currentDataSize == getNextIn().length;
            }

            @TruffleBoundary
            public void decompressedData(int size) {
                decompressed = decompressed || size > 0;
                if (!decompressed && !sameData()) {
                    input.setBuffer(getNextIn(), getNextInIndex());
                    setLzsAvailIn(getNextIn().length);
                    currentDataSize = getNextIn().length;
                    currentAutoFormat = 0;
                    lzs = null;
                }
            }

            @TruffleBoundary
            public void switchStream() throws IOException {
                setNextInIndex(input.getNextInIndex());
                if (currentAutoFormat == FORMAT_XZ) {
                    currentAutoFormat = FORMAT_ALONE;
                    if (getNextInIndex() >= DecoderUtil.STREAM_HEADER_SIZE) {
                        setNextInIndex(getNextInIndex() - DecoderUtil.STREAM_HEADER_SIZE);
                    }
                    input.setBuffer(getNextIn(), getNextInIndex());
                    createLZMA();
                } else {
                    currentAutoFormat = FORMAT_XZ;
                    input.setBuffer(getNextIn(), getNextInIndex());
                    createXZ();
                }
                setLzsAvailIn(input.getAvailIn());
            }

            @TruffleBoundary
            public void createXZ() throws IOException {
                lzs = new XZInputStream(input, memlimit);
            }

            @TruffleBoundary
            public void createLZMA() throws IOException {
                lzs = new LZMAInputStream(input, memlimit);
            }
        }

        public static class Native extends LZMADecompressor {

            private NFILZMASupport.Pointer pointer;

            public Native(Object cls, Shape instanceShape) {
                super(cls, instanceShape);
            }

            public final void init(Object lzmast, NFILZMASupport lib) {
                this.pointer = new NFILZMASupport.Pointer(this, lzmast, lib);
            }

            public final Object getLzs() {
                assert pointer != null;
                return pointer.getReference();
            }

            @TruffleBoundary
            public final void markReleased() {
                if (pointer != null) {
                    synchronized (this) {
                        pointer.markReleased();
                        pointer = null;
                    }
                }
            }
        }
    }

    public static LZMACompressor createCompressor(Object cls, Shape instanceShape, boolean isNative) {
        if (!isNative) {
            return new LZMACompressor.Java(cls, instanceShape);
        } else {
            return new LZMACompressor.Native(cls, instanceShape);
        }
    }

    public static LZMADecompressor createDecompressor(Object cls, Shape instanceShape, boolean isNative) {
        if (!isNative) {
            return new LZMADecompressor.Java(cls, instanceShape);
        } else {
            return new LZMADecompressor.Native(cls, instanceShape);
        }
    }
}
