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
package com.oracle.graal.python.builtins.modules.zlib;

import static com.oracle.graal.python.builtins.modules.zlib.ZLibModuleBuiltins.EMPTY_BYTE_ARRAY;
import static com.oracle.graal.python.builtins.modules.zlib.ZLibModuleBuiltins.MAX_WBITS;
import static com.oracle.graal.python.nodes.ErrorMessages.WHILE_SETTING_ZDICT;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.ZLibError;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.zip.DataFormatException;
import java.util.zip.GZIPInputStream;
import java.util.zip.Inflater;
import java.util.zip.ZipException;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.builtins.objects.bytes.BytesNodes;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.runtime.object.PFactory;
import com.oracle.graal.python.util.PythonUtils;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.object.Shape;

public class JavaDecompress extends JavaZlibCompObject {

    private static final int HEADER_TRAILER_SIZE = 10;

    private static class ZLibByteInputStream extends ByteArrayInputStream {

        public ZLibByteInputStream(byte[] buf, int offset, int length) {
            super(buf, offset, length);
        }

        public void setBuffer(byte[] bytes, int off, int length) {
            this.buf = bytes;
            this.pos = off;
            this.mark = off;
            this.count = length;
        }

        public void append(byte[] bytes, int off, int length) {
            if (buf.length == 0) {
                setBuffer(bytes, off, length);
                return;
            }
            if ((buf.length - count) < length) {
                buf = PythonUtils.arrayCopyOf(buf, buf.length + length);
            }
            if (length >= 0) {
                PythonUtils.arraycopy(bytes, off, buf, count, length);
            }
            count += length;
        }

        public int length() {
            return count - pos;
        }
    }

    private static class GZIPDecompressStream extends GZIPInputStream {

        public GZIPDecompressStream(InputStream in) throws IOException {
            super(in);
        }

        public Inflater getInflater() {
            return inf;
        }

        public void setInput() throws IOException {
            fill();
        }
    }

    private static class DecompressStream {

        Inflater inflater;
        GZIPDecompressStream stream;

        protected final ZLibByteInputStream in;

        DecompressStream(Inflater inflater) {
            this.inflater = inflater;
            this.stream = null;
            if (inflater == null) {
                this.in = new ZLibByteInputStream(new byte[HEADER_TRAILER_SIZE + 1], 0, 0);
            } else {
                this.in = null;
            }
        }
    }

    @TruffleBoundary
    private static DecompressStream createStream(int wbits) {
        if (wbits > (MAX_WBITS + 9) && wbits <= (MAX_WBITS + 16)) {
            // We delay the creation of a GZIP stream until we get an input; GZIPInputStream will
            // check the data header during initialization.
            return new DecompressStream(null);
        } else {
            Inflater inf = new Inflater(wbits < 0);
            return new DecompressStream(inf);
        }
    }

    private final DecompressStream stream;

    public JavaDecompress(Object cls, Shape instanceShape, int wbits, byte[] zdict) {
        super(cls, instanceShape, wbits, zdict);
        this.stream = createStream(wbits);
    }

    private static boolean isGZIPStreamReady(DecompressStream stream, byte[] data, int length, boolean force, Node node) {
        assert !isReady(stream);
        stream.in.append(data, 0, length);
        try {
            if (stream.in.length() > HEADER_TRAILER_SIZE || force) {
                // GZIPInputStream will read the header during initialization
                stream.stream = new GZIPDecompressStream(stream.in);
                stream.inflater = stream.stream.getInflater();
                stream.stream.setInput();
                return true;
            }
        } catch (ZipException ze) {
            throw PRaiseNode.raiseStatic(node, ZLibError, ze);
        } catch (IOException e) {
            throw CompilerDirectives.shouldNotReachHere(e);
        }
        return false;
    }

    private static boolean isGZIPStreamFinishing(DecompressStream stream, byte[] data, int length, Node node) {
        if (stream.stream != null && stream.inflater.finished()) {
            stream.in.append(data, 0, length);
            try {
                if (stream.in.length() >= HEADER_TRAILER_SIZE) {
                    stream.stream.setInput();
                    // this should trigger reading trailer
                    stream.stream.read();
                    stream.stream = null;
                }
                return true;
            } catch (ZipException ze) {
                throw PRaiseNode.raiseStatic(node, ZLibError, ze);
            } catch (IOException e) {
                throw CompilerDirectives.shouldNotReachHere(e);
            }
        }
        return false;
    }

    @TruffleBoundary
    private boolean setInflaterInput(byte[] data, int length, Node node) {
        canCopy = inputData == null;
        inputData = data;
        inputLen = length;
        if (!isReady()) {
            return isGZIPStreamReady(stream, data, length, false, node);
        } else {
            if (isGZIPStreamFinishing(stream, data, length, node)) {
                return false;
            }
        }
        stream.inflater.setInput(data, 0, length);
        return true;
    }

    @TruffleBoundary
    protected JavaDecompress copy(Node node) {
        assert canCopy();
        boolean isRAW = wbits < 0;
        JavaDecompress obj = PFactory.createJavaZLibCompObjectDecompress(PythonLanguage.get(node), wbits, zdict);
        if (isRAW) {
            obj.setDictionary();
        }
        if (inputData != null) {
            try {
                if (!obj.setInflaterInput(inputData, inputLen, node)) {
                    return obj;
                }
                int n = obj.stream.inflater.inflate(new byte[ZLibModuleBuiltins.DEF_BUF_SIZE]);
                if (!isRAW && n == 0 && obj.stream.inflater.needsDictionary() && getZdict().length > 0) {
                    obj.setDictionary();
                    obj.stream.inflater.inflate(new byte[ZLibModuleBuiltins.DEF_BUF_SIZE]);
                }
            } catch (DataFormatException e) {
                // pass
            }
        }
        obj.setUnconsumedTail(getUnconsumedTail());
        obj.setUnusedData(getUnusedData());
        return obj;
    }

    @TruffleBoundary
    private byte[] createByteArray(byte[] bytes, int length, int maxLength, int bufSize, Node nodeForRaise) {

        if (!setInflaterInput(bytes, length, nodeForRaise)) {
            return EMPTY_BYTE_ARRAY;
        }

        int maxLen = maxLength == 0 ? Integer.MAX_VALUE : maxLength;
        byte[] result = new byte[Math.min(maxLen, bufSize)];

        int bytesWritten = result.length;
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        boolean zdictIsSet = false;
        while (baos.size() < maxLen && bytesWritten == result.length) {
            try {
                int len = Math.min(maxLen - baos.size(), result.length);
                bytesWritten = stream.inflater.inflate(result, 0, len);
                if (bytesWritten == 0 && !zdictIsSet && stream.inflater.needsDictionary()) {
                    if (getZdict().length > 0) {
                        setDictionary();
                        zdictIsSet = true;
                        // we inflate again with a dictionary
                        bytesWritten = stream.inflater.inflate(result, 0, len);
                    } else {
                        throw PRaiseNode.raiseStatic(nodeForRaise, ZLibError, WHILE_SETTING_ZDICT);
                    }
                }
            } catch (DataFormatException e) {
                throw PRaiseNode.raiseStatic(nodeForRaise, ZLibError, e);
            }
            baos.write(result, 0, bytesWritten);
        }
        return baos.toByteArray();
    }

    protected byte[] decompress(VirtualFrame frame, byte[] bytes, int length, int maxLength, int bufSize,
                    Node inliningTarget, BytesNodes.ToBytesNode toBytesNode) {
        byte[] result = createByteArray(bytes, length, maxLength, bufSize, inliningTarget);
        if (!isReady()) {
            return result;
        }
        setEof(isFinished());
        byte[] unusedDataBytes = toBytesNode.execute(frame, getUnusedData());
        int unconsumedTailLen = getUnconsumedTail().getSequenceStorage().length();
        saveUnconsumedInput(bytes, length, unusedDataBytes, unconsumedTailLen, inliningTarget);
        return result;
    }

    protected static byte[] decompress(byte[] bytes, int length, int wbits, int bufsize, Node node) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        byte[] resultArray = new byte[bufsize];
        DecompressStream stream = createStream(wbits);
        try {
            if (!isReady(stream)) {
                isGZIPStreamReady(stream, bytes, length, true, node);
                while (stream.stream.available() > 0) {
                    int howmany = stream.stream.read(resultArray);
                    baos.write(resultArray, 0, howmany);
                }
            } else {
                stream.inflater.setInput(bytes, 0, length);
                while (!stream.inflater.finished()) {
                    int howmany = stream.inflater.inflate(resultArray);
                    if (howmany == 0 && stream.inflater.needsInput()) {
                        throw PRaiseNode.raiseStatic(node, ZLibError, ErrorMessages.ERROR_5_WHILE_DECOMPRESSING);
                    }
                    baos.write(resultArray, 0, howmany);
                }
                stream.inflater.end();
            }
        } catch (ZipException ze) {
            throw PRaiseNode.raiseStatic(node, ZLibError, ze);
        } catch (DataFormatException e) {
            throw PRaiseNode.raiseStatic(node, ZLibError, ErrorMessages.WHILE_PREPARING_TO_S_DATA, "decompress");
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        return baos.toByteArray();
    }

    private void saveUnconsumedInput(byte[] data, int length,
                    byte[] unusedDataBytes, int unconsumedTailLen, Node inliningTarget) {
        int unusedLen = getRemaining();
        byte[] tail = PythonUtils.arrayCopyOfRange(data, length - unusedLen, length);
        PythonLanguage language = PythonLanguage.get(inliningTarget);
        if (isEof()) {
            if (unconsumedTailLen > 0) {
                setUnconsumedTail(PFactory.createEmptyBytes(language));
            }
            if (unusedDataBytes.length > 0 && tail.length > 0) {
                byte[] newUnusedData = PythonUtils.arrayCopyOf(unusedDataBytes, unusedDataBytes.length + tail.length);
                PythonUtils.arraycopy(tail, 0, newUnusedData, unusedDataBytes.length, tail.length);
                setUnusedData(PFactory.createBytes(language, newUnusedData));
            } else if (tail.length > 0) {
                setUnusedData(PFactory.createBytes(language, tail));
            }
        } else {
            setUnconsumedTail(PFactory.createBytes(language, tail));
        }
    }

    private static boolean isReady(DecompressStream stream) {
        return stream.inflater != null;
    }

    private boolean isReady() {
        return isReady(stream);
    }

    @TruffleBoundary
    protected void setDictionary() {
        if (isReady() && getZdict().length > 0) {
            stream.inflater.setDictionary(getZdict());
        }
    }

    @TruffleBoundary
    private int getRemaining() {
        if (!isReady()) {
            return 0;
        }
        return stream.inflater.getRemaining();
    }

    @TruffleBoundary
    private boolean isFinished() {
        if (!isReady()) {
            return false;
        }
        return stream.inflater.finished();
    }
}
