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

import static com.oracle.graal.python.builtins.objects.bytes.BytesUtils.append;
import static com.oracle.graal.python.builtins.objects.bytes.BytesUtils.createOutputStream;
import static com.oracle.graal.python.builtins.objects.bytes.BytesUtils.toByteArray;
import static com.oracle.graal.python.nodes.StringLiterals.T_EMPTY_STRING;
import static com.oracle.graal.python.util.PythonUtils.TS_ENCODING;

import java.io.ByteArrayOutputStream;
import java.math.BigInteger;

import com.oracle.graal.python.builtins.objects.ints.IntBuiltins;
import com.oracle.graal.python.builtins.objects.ints.PInt;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.runtime.object.PythonObjectFactory;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.memory.ByteArraySupport;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.object.Shape;
import com.oracle.truffle.api.profiles.InlinedConditionProfile;
import com.oracle.truffle.api.strings.TruffleString;

public final class PTextIO extends PTextIOBase {

    private boolean detached;
    private int chunkSize;
    private Object buffer;
    private TruffleString encoding;
    private Object encoder;
    private TruffleString errors;
    private boolean lineBuffering;
    private boolean writeThrough;
    private boolean writetranslate;
    private boolean seekable;
    private boolean hasRead1;
    private boolean telling;
    private boolean finalizing;
    /* Specialized encoding func (see below) */
    private Object encodefunc;
    /* Whether or not it's the start of the stream */
    private boolean encodingStartOfStream;

    /*
     * Reads and writes are internally buffered in order to speed things up. However, any read will
     * first flush the write buffer if itsn't empty.
     *
     * Please also note that text to be written is first encoded before being buffered. This is
     * necessary so that encoding errors are immediately reported to the caller, but it
     * unfortunately means that the IncrementalEncoder (whose encode() method is always written in
     * Python) becomes a bottleneck for small writes.
     */
    private TruffleString decodedChars; /* buffer for text returned from decoder */
    private int decodedCharsUsed; /* offset (in code points) into _decoded_chars for read() */
    private int decodedCharsLen; /* code point length of decodedChars */

    private ByteArrayOutputStream pendingBytes;       // data waiting to be written.

    /*
     * snapshot is either NULL, or a tuple (dec_flags, next_input) where dec_flags is the second
     * (integer) item of the decoder state and next_input is the chunk of input bytes that comes
     * next after the snapshot point. We use this to reconstruct decoder states in tell().
     */
    private int snapshotDecFlags;
    private byte[] snapshotNextInput;
    /*
     * Bytes-to-characters ratio for the current chunk. Serves as input for the heuristic in tell().
     */
    private double b2cratio;

    /* Cache raw object if it's a FileIO object */
    private PFileIO raw;

    public PTextIO(Object cls, Shape instanceShape) {
        super(cls, instanceShape);
        pendingBytes = createOutputStream();
    }

    @Override
    public void clearAll() {
        super.clearAll();
        detached = false;
        buffer = null;
        encoding = null;
        encoder = null;
        errors = null;
        raw = null;
        clearDecodedChars();
        clearPendingBytes();
        clearSnapshot();
        encodefunc = null;
        b2cratio = 0.0;
    }

    public boolean isDetached() {
        return detached;
    }

    public void setDetached(boolean detached) {
        this.detached = detached;
    }

    public int getChunkSize() {
        return chunkSize;
    }

    public void setChunkSize(int chunkSize) {
        this.chunkSize = chunkSize;
    }

    public Object getBuffer() {
        return buffer;
    }

    public void setBuffer(Object buffer) {
        this.buffer = buffer;
    }

    public TruffleString getEncoding() {
        return encoding;
    }

    public void setEncoding(TruffleString encoding) {
        this.encoding = encoding;
    }

    public boolean hasEncoding() {
        return encoding != null;
    }

    public Object getEncoder() {
        return encoder;
    }

    public void setEncoder(Object encoder) {
        this.encoder = encoder;
    }

    public boolean hasEncoder() {
        return encoder != null;
    }

    public TruffleString getErrors() {
        return errors;
    }

    public void setErrors(TruffleString errors) {
        this.errors = errors;
    }

    public boolean isLineBuffering() {
        return lineBuffering;
    }

    public void setLineBuffering(boolean lineBuffering) {
        this.lineBuffering = lineBuffering;
    }

    public boolean isWriteThrough() {
        return writeThrough;
    }

    public void setWriteThrough(boolean writeThrough) {
        this.writeThrough = writeThrough;
    }

    public boolean isWriteTranslate() {
        return writetranslate;
    }

    public void setWriteTranslate(boolean writetranslate) {
        this.writetranslate = writetranslate;
    }

    public boolean isSeekable() {
        return seekable;
    }

    public void setSeekable(boolean seekable) {
        this.seekable = seekable;
    }

    public boolean isHasRead1() {
        return hasRead1;
    }

    public void setHasRead1(boolean hasRead1) {
        this.hasRead1 = hasRead1;
    }

    public boolean isTelling() {
        return telling;
    }

    public void setTelling(boolean telling) {
        this.telling = telling;
    }

    public boolean isFinalizing() {
        return finalizing;
    }

    public void setFinalizing(boolean finalizing) {
        this.finalizing = finalizing;
    }

    // TODO: do a direct call to the encode function
    public Object getEncodefunc() {
        return encodefunc;
    }

    public void setEncodefunc(Object encodefunc) {
        this.encodefunc = encodefunc;
    }

    public boolean isEncodingStartOfStream() {
        return encodingStartOfStream;
    }

    public void setEncodingStartOfStream(boolean encodingStartOfStream) {
        this.encodingStartOfStream = encodingStartOfStream;
    }

    public TruffleString getDecodedChars() {
        return decodedChars;
    }

    public boolean hasDecodedChars() {
        return decodedChars != null;
    }

    public boolean hasDecodedCharsAvailable() {
        return decodedChars != null && decodedCharsUsed < decodedCharsLen;
    }

    public int getDecodedCharsUsed() {
        return decodedCharsUsed;
    }

    public int setDecodedChars(TruffleString decodedChars, TruffleString.CodePointLengthNode codePointLengthNode) {
        assert !hasDecodedCharsAvailable();
        this.decodedChars = decodedChars;
        decodedCharsLen = codePointLengthNode.execute(decodedChars, TS_ENCODING);
        decodedCharsUsed = 0;
        return decodedCharsLen;
    }

    public void incDecodedCharsUsed(int n) {
        assert decodedCharsUsed + n <= decodedCharsLen;
        this.decodedCharsUsed += n;
    }

    public void clearDecodedChars() {
        this.decodedChars = null;
        this.decodedCharsUsed = 0;
        this.decodedCharsLen = 0;
    }

    TruffleString consumeDecodedChars(int n, TruffleString.SubstringNode substringNode, boolean lazy) {
        assert n >= 0;
        if (decodedChars == null || n == 0) {
            return T_EMPTY_STRING;
        }
        int avail = decodedCharsLen - decodedCharsUsed;
        if (n >= avail) {
            return consumeAllDecodedChars(substringNode, lazy);
        }
        TruffleString chars = substringNode.execute(decodedChars, decodedCharsUsed, n, TS_ENCODING, lazy);
        decodedCharsUsed += n;
        return chars;
    }

    TruffleString consumeAllDecodedChars(TruffleString.SubstringNode substringNode, boolean lazy) {
        if (decodedChars == null || decodedCharsUsed == decodedCharsLen) {
            return T_EMPTY_STRING;
        }
        TruffleString chars;
        if (decodedCharsUsed > 0) {
            chars = substringNode.execute(decodedChars, decodedCharsUsed, decodedCharsLen - decodedCharsUsed, TS_ENCODING, lazy);
        } else {
            chars = decodedChars;
        }
        decodedCharsUsed = decodedCharsLen;
        return chars;
    }

    public void clearPendingBytes() {
        pendingBytes = createOutputStream();
    }

    public byte[] getAndClearPendingBytes() {
        byte[] b = toByteArray(pendingBytes);
        clearPendingBytes();
        return b;
    }

    public boolean hasPendingBytes() {
        return pendingBytes.size() != 0;
    }

    public void appendPendingBytes(byte[] bytes, int len) {
        append(pendingBytes, bytes, len);
    }

    public int getPendingBytesCount() {
        return pendingBytes.size();
    }

    public int getSnapshotDecFlags() {
        return snapshotDecFlags;
    }

    public void setSnapshotDecFlags(int snapshotDecFlags) {
        this.snapshotDecFlags = snapshotDecFlags;
    }

    public byte[] getSnapshotNextInput() {
        return snapshotNextInput;
    }

    public boolean hasSnapshotNextInput() {
        return snapshotNextInput != null;
    }

    public void setSnapshotNextInput(byte[] snapshotNextInput) {
        this.snapshotNextInput = snapshotNextInput;
    }

    public void clearSnapshot() {
        this.snapshotNextInput = null;
        this.snapshotDecFlags = 0;
    }

    public double getB2cratio() {
        return b2cratio;
    }

    public void setB2cratio(double b2cratio) {
        this.b2cratio = b2cratio;
    }

    public PFileIO getFileIO() {
        return raw;
    }

    public boolean isFileIO() {
        return raw != null;
    }

    public void setFileIO(PFileIO raw) {
        this.raw = raw;
    }

    public static PTextIO createTextIO(Object cls, Shape instanceShape) {
        return new PTextIO(cls, instanceShape);
    }

    @CompilerDirectives.ValueType
    protected static class CookieType {
        private static final ByteArraySupport SERIALIZE = ByteArraySupport.littleEndian();
        private static final int COOKIE_BUF_LEN = Long.BYTES + Integer.BYTES * 3 + 1;

        protected long startPos;
        protected int decFlags;
        protected int bytesToFeed;
        protected int charsToSkip;
        protected byte needEOF;

        CookieType() {
            this.startPos = 0;
            this.decFlags = 0;
            this.bytesToFeed = 0;
            this.charsToSkip = 0;
            this.needEOF = 0;
        }

        public static PInt build(CookieType cookie, PythonObjectFactory factory) {
            byte[] buffer = new byte[COOKIE_BUF_LEN];
            SERIALIZE.putLong(buffer, 0, cookie.startPos);
            SERIALIZE.putInt(buffer, Long.BYTES, cookie.decFlags);
            SERIALIZE.putInt(buffer, Long.BYTES + Integer.BYTES, cookie.bytesToFeed);
            SERIALIZE.putInt(buffer, Long.BYTES + Integer.BYTES * 2, cookie.charsToSkip);
            SERIALIZE.putByte(buffer, Long.BYTES + Integer.BYTES * 3, cookie.needEOF);
            BigInteger v = IntBuiltins.FromBytesNode.createBigInteger(buffer, false, false);
            return factory.createInt(v);
        }

        public static CookieType parse(long v, Node inliningTarget, InlinedConditionProfile overflow, PRaiseNode.Lazy raise) {
            byte[] buffer = IntBuiltins.ToBytesNode.fromLong(v, COOKIE_BUF_LEN, false, false, inliningTarget, overflow, raise);
            return parse(buffer);
        }

        public static CookieType parse(PInt v, Node inliningTarget, InlinedConditionProfile overflow, PRaiseNode.Lazy raise) {
            byte[] buffer = IntBuiltins.ToBytesNode.fromBigInteger(v, COOKIE_BUF_LEN, false, false, inliningTarget, overflow, raise);
            return parse(buffer);
        }

        public static CookieType parse(byte[] buffer) {
            CookieType cookie = new CookieType();
            cookie.startPos = SERIALIZE.getLong(buffer, 0);
            cookie.decFlags = SERIALIZE.getInt(buffer, Long.BYTES);
            cookie.bytesToFeed = SERIALIZE.getInt(buffer, Long.BYTES + Integer.BYTES);
            cookie.charsToSkip = SERIALIZE.getInt(buffer, Long.BYTES + Integer.BYTES * 2);
            cookie.needEOF = SERIALIZE.getByte(buffer, Long.BYTES + Integer.BYTES * 3);
            return cookie;
        }
    }
}
