/*
 * Copyright (c) 2025, 2026, Oracle and/or its affiliates. All rights reserved.
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
import static com.oracle.graal.python.builtins.modules.zlib.ZlibNodes.Z_DATA_ERROR;
import static com.oracle.graal.python.nodes.ErrorMessages.ERROR_D_S_S;
import static com.oracle.graal.python.nodes.ErrorMessages.WHILE_SETTING_ZDICT;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.ZLibError;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.zip.CRC32;
import java.util.zip.DataFormatException;
import java.util.zip.Inflater;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.builtins.objects.bytes.BytesNodes;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.runtime.object.PFactory;
import com.oracle.graal.python.util.PythonUtils;
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

        public int count() {
            return count;
        }

        public int byteAt(int index) {
            return buf[index] & 0xff;
        }

        public void updateCrc(CRC32 crc, int offset, int length) {
            crc.update(buf, offset, length);
        }
    }

    private static class DecompressStream {

        Inflater inflater;
        final boolean gzip;
        int inputOffset;
        final byte[] gzipTrailer;
        final CRC32 gzipCrc;
        int gzipTrailerLength;
        int gzipUnusedLength;
        boolean gzipTrailerComplete;

        protected final ZLibByteInputStream in;

        DecompressStream(Inflater inflater, boolean gzip) {
            this.inflater = inflater;
            this.gzip = gzip;
            this.inputOffset = 0;
            this.gzipTrailer = gzip ? new byte[8] : null;
            this.gzipCrc = gzip ? new CRC32() : null;
            this.gzipTrailerLength = 0;
            this.gzipUnusedLength = 0;
            this.gzipTrailerComplete = !gzip;
            if (inflater == null) {
                this.in = new ZLibByteInputStream(new byte[HEADER_TRAILER_SIZE + 1], 0, 0);
            } else {
                this.in = null;
            }
        }
    }

    private static boolean isGZIP(int wbits) {
        return wbits > (MAX_WBITS + 9) && wbits <= (MAX_WBITS + 16);
    }

    @TruffleBoundary
    private static DecompressStream createStream(int wbits) {
        if (isGZIP(wbits)) {
            // Delay creating the raw inflater until the gzip header is complete.
            return new DecompressStream(null, true);
        } else {
            Inflater inf = new Inflater(wbits < 0);
            return new DecompressStream(inf, false);
        }
    }

    private final DecompressStream stream;

    public JavaDecompress(Object cls, Shape instanceShape, int wbits, byte[] zdict) {
        super(cls, instanceShape, wbits, zdict);
        this.stream = createStream(wbits);
    }

    private static int gzipHeaderLength(ZLibByteInputStream in, boolean force, Node node) {
        if (in.count() < HEADER_TRAILER_SIZE) {
            return incompleteGZIPHeader(force, node);
        }
        if (in.byteAt(0) != 0x1f || in.byteAt(1) != 0x8b || in.byteAt(2) != 8 || (in.byteAt(3) & 0xe0) != 0) {
            throw PRaiseNode.raiseStatic(node, ZLibError, ErrorMessages.WHILE_PREPARING_TO_S_DATA, "decompress");
        }
        int flags = in.byteAt(3);
        int offset = HEADER_TRAILER_SIZE;
        if ((flags & 0x04) != 0) {
            if (in.count() < offset + 2) {
                return incompleteGZIPHeader(force, node);
            }
            int extraLength = in.byteAt(offset) | (in.byteAt(offset + 1) << 8);
            offset += 2 + extraLength;
            if (in.count() < offset) {
                return incompleteGZIPHeader(force, node);
            }
        }
        if ((flags & 0x08) != 0) {
            offset = skipGZIPHeaderString(in, offset, force, node);
            if (offset < 0) {
                return offset;
            }
        }
        if ((flags & 0x10) != 0) {
            offset = skipGZIPHeaderString(in, offset, force, node);
            if (offset < 0) {
                return offset;
            }
        }
        if ((flags & 0x02) != 0) {
            int crcOffset = offset;
            offset += 2;
            if (in.count() < offset) {
                return incompleteGZIPHeader(force, node);
            }
            validateGZIPHeaderCrc(in, crcOffset, node);
        }
        return offset;
    }

    private static int skipGZIPHeaderString(ZLibByteInputStream in, int offset, boolean force, Node node) {
        while (offset < in.count()) {
            if (in.byteAt(offset++) == 0) {
                return offset;
            }
        }
        return incompleteGZIPHeader(force, node);
    }

    private static int incompleteGZIPHeader(boolean force, Node node) {
        if (force) {
            throw PRaiseNode.raiseStatic(node, ZLibError, ErrorMessages.ERROR_5_WHILE_DECOMPRESSING);
        }
        return -1;
    }

    private static void validateGZIPHeaderCrc(ZLibByteInputStream in, int crcOffset, Node node) {
        CRC32 crc = new CRC32();
        in.updateCrc(crc, 0, crcOffset);
        int expectedCrc = in.byteAt(crcOffset) | (in.byteAt(crcOffset + 1) << 8);
        if (((int) crc.getValue() & 0xffff) != expectedCrc) {
            throw PRaiseNode.raiseStatic(node, ZLibError, ERROR_D_S_S, Z_DATA_ERROR,
                            "while decompressing data", "header crc mismatch");
        }
    }

    private static boolean isGZIPStreamReady(DecompressStream stream, byte[] data, int length, boolean force, Node node) {
        assert !isReady(stream);
        int oldCount = stream.in.count();
        stream.in.append(data, 0, length);
        int headerLength = gzipHeaderLength(stream.in, force, node);
        if (headerLength >= 0) {
            stream.inflater = new Inflater(true);
            stream.inputOffset = Math.max(0, headerLength - oldCount);
            stream.inflater.setInput(data, stream.inputOffset, length - stream.inputOffset);
            return true;
        }
        return false;
    }

    private static long getLittleEndianUnsignedInt(byte[] data, int offset) {
        return (data[offset] & 0xffL) |
                        ((data[offset + 1] & 0xffL) << 8) |
                        ((data[offset + 2] & 0xffL) << 16) |
                        ((data[offset + 3] & 0xffL) << 24);
    }

    private static void validateGZIPTrailer(DecompressStream stream, Node node) {
        assert stream.gzip;
        long expectedCrc = getLittleEndianUnsignedInt(stream.gzipTrailer, 0);
        long expectedSize = getLittleEndianUnsignedInt(stream.gzipTrailer, 4);
        if (stream.gzipCrc.getValue() != expectedCrc) {
            throw PRaiseNode.raiseStatic(node, ZLibError, ERROR_D_S_S, Z_DATA_ERROR,
                            "while decompressing data", "incorrect data check");
        }
        if ((stream.inflater.getBytesWritten() & 0xffffffffL) != expectedSize) {
            throw PRaiseNode.raiseStatic(node, ZLibError, ERROR_D_S_S, Z_DATA_ERROR,
                            "while decompressing data", "incorrect length check");
        }
    }

    private static boolean consumeGZIPTrailer(DecompressStream stream, byte[] data, int offset, int length, Node node) {
        if (!stream.gzip || (!stream.inflater.finished() && !stream.gzipTrailerComplete)) {
            return false;
        }
        if (stream.gzipTrailerComplete) {
            stream.gzipUnusedLength = length;
            return true;
        }
        int trailerBytes = Math.min(stream.gzipTrailer.length - stream.gzipTrailerLength, length);
        PythonUtils.arraycopy(data, offset, stream.gzipTrailer, stream.gzipTrailerLength, trailerBytes);
        stream.gzipTrailerLength += trailerBytes;
        stream.gzipUnusedLength = 0;
        if (stream.gzipTrailerLength == stream.gzipTrailer.length) {
            validateGZIPTrailer(stream, node);
            stream.gzipTrailerComplete = true;
            stream.gzipUnusedLength = length - trailerBytes;
        }
        return true;
    }

    private static boolean isGZIPStreamFinishing(DecompressStream stream, byte[] data, int length, Node node) {
        if (stream.gzip && (stream.inflater.finished() || stream.gzipTrailerComplete)) {
            consumeGZIPTrailer(stream, data, 0, length, node);
            stream.inputOffset = 0;
            return true;
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
        stream.inputOffset = 0;
        stream.inflater.setInput(data, 0, length);
        return true;
    }

    private static boolean needsReplay(DecompressStream stream, long bytesRead, long bytesWritten, boolean finishInflater) {
        return stream.inflater.getBytesRead() < bytesRead || stream.inflater.getBytesWritten() < bytesWritten ||
                        (finishInflater && !stream.inflater.finished());
    }

    private static void replayInflaterInput(JavaDecompress obj, long bytesRead, long bytesWritten, boolean finishInflater, boolean isRAW)
                    throws DataFormatException {
        byte[] result = new byte[ZLibModuleBuiltins.DEF_BUF_SIZE];
        boolean zdictIsSet = isRAW;
        while (needsReplay(obj.stream, bytesRead, bytesWritten, finishInflater) && !obj.stream.inflater.finished()) {
            long remainingBytes = bytesWritten - obj.stream.inflater.getBytesWritten();
            int len = remainingBytes > 0 ? (int) Math.min(result.length, remainingBytes) : result.length;
            int n = obj.stream.inflater.inflate(result, 0, len);
            if (n == 0) {
                if (!zdictIsSet && obj.stream.inflater.needsDictionary() && obj.getZdict().length > 0) {
                    obj.setDictionary();
                    zdictIsSet = true;
                    continue;
                }
                break;
            }
            updateGZIPCrc(obj.stream, result, n);
        }
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
                replayInflaterInput(obj, stream.inflater.getBytesRead(), stream.inflater.getBytesWritten(), stream.inflater.finished(),
                                isRAW);
                if (stream.gzip && stream.inflater.finished() && obj.stream.inflater.finished()) {
                    int remaining = obj.stream.inflater.getRemaining();
                    int remainingOffset = obj.stream.inputOffset + (inputLen - obj.stream.inputOffset - remaining);
                    consumeGZIPTrailer(obj.stream, inputData, remainingOffset, remaining, node);
                }
            } catch (DataFormatException e) {
                // pass
            }
        }
        obj.setUnconsumedTail(getUnconsumedTail());
        obj.setUnusedData(getUnusedData());
        obj.setEof(isEof());
        return obj;
    }

    @TruffleBoundary
    private byte[] createByteArray(byte[] bytes, int length, int maxLength, int bufSize, Node nodeForRaise) {

        if (!setInflaterInput(bytes, length, nodeForRaise)) {
            return EMPTY_BYTE_ARRAY;
        }

        int maxLen = maxLength <= 0 ? Integer.MAX_VALUE : maxLength;
        byte[] result = new byte[Math.min(maxLen, bufSize)];

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        boolean zdictIsSet = false;
        while (baos.size() < maxLen && !stream.inflater.finished()) {
            if (stream.inflater.needsInput()) {
                break;
            }
            int bytesWritten;
            try {
                int len = Math.min(maxLen - baos.size(), result.length);
                bytesWritten = stream.inflater.inflate(result, 0, len);
                if (bytesWritten == 0 && !zdictIsSet && stream.inflater.needsDictionary()) {
                    if (getZdict().length > 0) {
                        setDictionary();
                        zdictIsSet = true;
                        continue;
                    } else {
                        throw PRaiseNode.raiseStatic(nodeForRaise, ZLibError, WHILE_SETTING_ZDICT);
                    }
                }
            } catch (DataFormatException e) {
                throw PRaiseNode.raiseStatic(nodeForRaise, ZLibError, e);
            }
            updateGZIPCrc(stream, result, bytesWritten);
            baos.write(result, 0, bytesWritten);
        }
        if (stream.gzip && stream.inflater.finished()) {
            int remaining = stream.inflater.getRemaining();
            int remainingOffset = stream.inputOffset + (length - stream.inputOffset - remaining);
            consumeGZIPTrailer(stream, bytes, remainingOffset, remaining, nodeForRaise);
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
            } else {
                stream.inflater.setInput(bytes, 0, length);
            }
            while (!stream.inflater.finished()) {
                int howmany = stream.inflater.inflate(resultArray);
                if (howmany == 0 && stream.inflater.needsInput()) {
                    throw PRaiseNode.raiseStatic(node, ZLibError, ErrorMessages.ERROR_5_WHILE_DECOMPRESSING);
                }
                updateGZIPCrc(stream, resultArray, howmany);
                baos.write(resultArray, 0, howmany);
            }
            if (stream.gzip) {
                int remaining = stream.inflater.getRemaining();
                int remainingOffset = stream.inputOffset + (length - stream.inputOffset - remaining);
                consumeGZIPTrailer(stream, bytes, remainingOffset, remaining, node);
                if (!stream.gzipTrailerComplete) {
                    throw PRaiseNode.raiseStatic(node, ZLibError, ErrorMessages.ERROR_5_WHILE_DECOMPRESSING);
                }
            }
            stream.inflater.end();
        } catch (DataFormatException e) {
            throw PRaiseNode.raiseStatic(node, ZLibError, ErrorMessages.WHILE_PREPARING_TO_S_DATA, "decompress");
        }

        return baos.toByteArray();
    }

    private void saveUnconsumedInput(byte[] data, int length,
                    byte[] unusedDataBytes, int unconsumedTailLen, Node inliningTarget) {
        int unusedLen = getRemaining();
        byte[] tail = PythonUtils.arrayCopyOfRange(data, Math.max(0, length - unusedLen), length);
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

    private static void updateGZIPCrc(DecompressStream stream, byte[] data, int length) {
        if (stream.gzip && length > 0) {
            stream.gzipCrc.update(data, 0, length);
        }
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
        if (stream.gzip) {
            if (stream.gzipTrailerComplete) {
                return stream.gzipUnusedLength;
            }
            if (stream.inflater.finished()) {
                return 0;
            }
        }
        return stream.inflater.getRemaining();
    }

    @TruffleBoundary
    private boolean isFinished() {
        if (!isReady()) {
            return false;
        }
        return stream.gzip ? stream.gzipTrailerComplete : stream.inflater.finished();
    }
}
