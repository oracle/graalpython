/*
 * Copyright (c) 2020, 2023, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.builtins.modules.zlib;

import static com.oracle.graal.python.builtins.PythonBuiltinClassType.ZlibCompress;
import static com.oracle.graal.python.builtins.PythonBuiltinClassType.ZlibDecompress;
import static com.oracle.graal.python.builtins.modules.zlib.ZLibModuleBuiltins.MAX_WBITS;
import static com.oracle.graal.python.builtins.objects.bytes.BytesUtils.mask;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.ZLibError;

import java.util.zip.CRC32;
import java.util.zip.DataFormatException;
import java.util.zip.Deflater;
import java.util.zip.Inflater;

import com.oracle.graal.python.builtins.objects.bytes.PBytes;
import com.oracle.graal.python.builtins.objects.object.PythonBuiltinObject;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.runtime.NFIZlibSupport;
import com.oracle.graal.python.runtime.object.PythonObjectFactory;
import com.oracle.graal.python.util.PythonUtils;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.object.Shape;

public abstract class ZLibCompObject extends PythonBuiltinObject {

    protected volatile boolean isInitialized;
    private boolean eof;
    private PBytes unusedData;
    private PBytes unconsumedTail;

    public ZLibCompObject(Object cls, Shape instanceShape) {
        super(cls, instanceShape);
        this.isInitialized = true;
        this.eof = false;
        this.unusedData = null;
        this.unconsumedTail = null;
    }

    // Note: some IDEs mark this class as inaccessible in PythonObjectFactory, but changing this to
    // public will cause a warning: [this-escape] possible 'this' escape before subclass is fully
    // initialized
    protected static class NativeZlibCompObject extends ZLibCompObject {

        private NFIZlibSupport.Pointer pointer;
        Object lastInput;

        public NativeZlibCompObject(Object cls, Shape instanceShape, Object zst, NFIZlibSupport zlibSupport) {
            super(cls, instanceShape);
            this.pointer = new NFIZlibSupport.Pointer(this, zst, zlibSupport);
            this.lastInput = null;
        }

        public Object getZst() {
            assert pointer != null;
            return pointer.getReference();
        }

        @TruffleBoundary
        public void markReleased() {
            if (isInitialized) {
                synchronized (this) {
                    isInitialized = false;
                    pointer.markReleased();
                    pointer = null;
                }
            }
        }
    }

    protected static class JavaZlibCompObject extends ZLibCompObject {

        final Object stream;
        final byte[] zdict;

        final int level;
        final int wbits;
        final int strategy;

        private byte[] inputData; // helper for copy operation
        private int inputLen;
        private boolean canCopy; // to assist if copying is allowed
        private boolean readHeader;

        public JavaZlibCompObject(Object cls, Shape instanceShape, Object stream, int level, int wbits, int strategy, byte[] zdict) {
            super(cls, instanceShape);
            this.stream = stream;
            this.zdict = zdict;
            this.level = level;
            this.wbits = wbits;
            this.strategy = strategy;
            this.inputData = null;
            this.canCopy = true;
            this.readHeader = wbits >= 25 && wbits <= 31;
        }

        public JavaZlibCompObject(Object cls, Shape instanceShape, Object stream, int wbits, byte[] zdict) {
            this(cls, instanceShape, stream, 0, wbits, 0, zdict);
        }

        public void setUninitialized() {
            isInitialized = false;
        }

        public byte[] getZdict() {
            return zdict;
        }

        public boolean canCopy() {
            return canCopy;
        }

        @TruffleBoundary
        public void setDeflaterInput(byte[] data, int length) {
            assert stream instanceof Deflater;
            canCopy = inputData == null;
            inputData = data;
            inputLen = length;
            ((Deflater) stream).setInput(data, 0, length);
        }

        @TruffleBoundary
        public void setInflaterInput(byte[] data, int length, Node node) {
            assert stream instanceof Inflater;
            byte[] bytes = data;
            if (readHeader) {
                readHeader = false;
                int h = gzipHeader(data, node);
                bytes = PythonUtils.arrayCopyOfRange(bytes, h, length);
                length = bytes.length;
            }
            canCopy = inputData == null;
            inputData = bytes;
            inputLen = length;
            ((Inflater) stream).setInput(bytes);
        }

        @TruffleBoundary
        public ZLibCompObject copyCompressObj(PythonObjectFactory factory) {
            assert canCopy;
            Deflater deflater = new Deflater(level, wbits < 0 || wbits > (MAX_WBITS + 9));

            deflater.setStrategy(strategy);
            if (zdict.length > 0) {
                deflater.setDictionary(zdict);
            }
            ZLibCompObject obj = factory.createJavaZLibCompObject(ZlibCompress, deflater, level, wbits, strategy, zdict);
            if (inputData != null) {
                // feed the new copy of deflater the same input data
                ((JavaZlibCompObject) obj).setDeflaterInput(inputData, inputLen);
                deflater.deflate(new byte[inputLen]);
            }
            return obj;
        }

        @TruffleBoundary
        public ZLibCompObject copyDecompressObj(PythonObjectFactory factory, Node node) {
            assert canCopy;
            boolean isRAW = wbits < 0;
            Inflater inflater = new Inflater(isRAW || wbits > (MAX_WBITS + 9));
            if (isRAW && zdict.length > 0) {
                inflater.setDictionary(zdict);
            }
            ZLibCompObject obj = factory.createJavaZLibCompObject(ZlibDecompress, inflater, wbits, zdict);
            if (inputData != null) {
                try {
                    ((JavaZlibCompObject) obj).setInflaterInput(inputData, inputLen, node);
                    inflater.setInput(inputData);
                    int n = inflater.inflate(new byte[ZLibModuleBuiltins.DEF_BUF_SIZE]);
                    if (!isRAW && n == 0 && inflater.needsDictionary() && zdict.length > 0) {
                        inflater.setDictionary(zdict);
                        inflater.inflate(new byte[ZLibModuleBuiltins.DEF_BUF_SIZE]);
                    }
                } catch (DataFormatException e) {
                    // pass
                }
            }
            obj.setUnconsumedTail(getUnconsumedTail());
            obj.setUnusedData(getUnusedData());
            return obj;
        }

        public static final int GZIP_MAGIC = 0x8b1f;
        private static final int FHCRC = 2;    // Header CRC
        private static final int FEXTRA = 4;    // Extra field
        private static final int FNAME = 8;    // File name
        private static final int FCOMMENT = 16;   // File comment

        private static int getValue(byte b, CRC32 crc) {
            int v = mask(b);
            crc.update(v);
            return v;
        }

        private static int readShort(byte[] bytes, int off, CRC32 crc) {
            return getValue(bytes[off + 1], crc) << 8 | getValue(bytes[off], crc);
        }

        // logic is from GZIPInputStream.readHeader()
        @TruffleBoundary
        private static int gzipHeader(byte[] bytes, Node node) {
            CRC32 crc = new CRC32();
            int idx = 0;
            // Check header magic
            if (readShort(bytes, idx, crc) != GZIP_MAGIC) {
                throw PRaiseNode.raiseUncached(node, ZLibError, ErrorMessages.NOT_IN_GZIP_FORMAT);
            }
            idx += 2;
            // Check compression method
            if (getValue(bytes[idx++], crc) != 8) {
                throw PRaiseNode.raiseUncached(node, ZLibError, ErrorMessages.UNSUPPORTED_COMPRESSION_METHOD);
            }
            // Read flags
            int flg = getValue(bytes[idx++], crc);
            // Skip MTIME, XFL, and OS fields
            idx += 6;
            @SuppressWarnings("unused")
            int n = 2 + 2 + 6;
            // Skip optional extra field
            if ((flg & FEXTRA) == FEXTRA) {
                int m = getValue(bytes[idx++], crc);
                idx += m;
                n += m + 2;
            }
            // Skip optional file name
            if ((flg & FNAME) == FNAME) {
                do {
                    n++;
                } while (getValue(bytes[idx++], crc) != 0);
            }
            // Skip optional file comment
            if ((flg & FCOMMENT) == FCOMMENT) {
                do {
                    n++;
                } while (getValue(bytes[idx++], crc) != 0);
            }
            // Check optional header CRC
            crc.reset();
            if ((flg & FHCRC) == FHCRC) {
                int v = (int) crc.getValue() & 0xffff;
                if (readShort(bytes, idx, crc) != v) {
                    throw PRaiseNode.raiseUncached(node, ZLibError, ErrorMessages.CORRUPT_GZIP_HEADER);
                }
                idx += 2;
                n += 2;
            }
            crc.reset();
            return idx;
        }

    }

    public boolean isInitialized() {
        return isInitialized;
    }

    public boolean isEof() {
        return eof;
    }

    public void setEof(boolean eof) {
        this.eof = eof;
    }

    public PBytes getUnusedData() {
        return unusedData;
    }

    public void setUnusedData(PBytes unusedData) {
        this.unusedData = unusedData;
    }

    public PBytes getUnconsumedTail() {
        return unconsumedTail;
    }

    public void setUnconsumedTail(PBytes unconsumedTail) {
        this.unconsumedTail = unconsumedTail;
    }

    public static NativeZlibCompObject createNative(Object cls, Shape instanceShape, Object zst, NFIZlibSupport zlibSupport) {
        return new NativeZlibCompObject(cls, instanceShape, zst, zlibSupport);
    }

    public static ZLibCompObject createJava(Object cls, Shape instanceShape, Object stream, int level, int wbits, int strategy, byte[] zdict) {
        return new JavaZlibCompObject(cls, instanceShape, stream, level, wbits, strategy, zdict);
    }

    public static ZLibCompObject createJava(Object cls, Shape instanceShape, Object stream, int wbits, byte[] zdict) {
        return new JavaZlibCompObject(cls, instanceShape, stream, wbits, zdict);
    }
}
