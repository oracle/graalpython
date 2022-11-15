/*
 * Copyright (c) 2021, 2022, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.builtins.objects.ssl;

import java.nio.ByteBuffer;

import com.oracle.graal.python.builtins.objects.object.PythonObject;
import com.oracle.graal.python.util.OverflowException;
import com.oracle.graal.python.util.PythonUtils;
import com.oracle.truffle.api.object.Shape;

/**
 * Our rough equivalent of OpenSSL memory BIO objects used as buffers when performing SSL
 * wrapping/unwrapping. They are similar to Java {@link ByteBuffer}, but they have a separate
 * position for reading and writing.
 */
public class PMemoryBIO extends PythonObject {

    private byte[] bytes = PythonUtils.EMPTY_BYTE_ARRAY;
    private int readPosition;
    private int writePosition;
    private boolean eofWritten;

    public PMemoryBIO(Object pythonClass, Shape instanceShape) {
        super(pythonClass, instanceShape);
    }

    /**
     * Get number of bytes that can be read from this BIO.
     */
    public int getPending() {
        return writePosition - readPosition;
    }

    /**
     * Get the current position in the internal array that is used for reading.
     */
    public int getReadPosition() {
        return readPosition;
    }

    /**
     * Get the current position in the internal array that is used for writing.
     */
    public int getWritePosition() {
        return writePosition;
    }

    /**
     * Advance the read cursor by given number of bytes.
     */
    public void advanceReadPosition(int by) throws OverflowException {
        assert by >= 0;
        readPosition = PythonUtils.addExact(readPosition, by);
        assert readPosition <= writePosition;
    }

    /**
     * Advance the read cursor by given number of bytes.
     */
    public void advanceWritePosition(int by) throws OverflowException {
        assert by >= 0;
        writePosition = PythonUtils.addExact(writePosition, by);
        assert writePosition <= bytes.length;
    }

    /**
     * Get internal byte array. The caller needs to make sure they use they use correct offsets.
     */
    public byte[] getInternalBytes() {
        return bytes;
    }

    /**
     * Wrap into a {@link ByteBuffer} meant for reading the data of this BIO. It is necessary to
     * call {@link #applyRead(ByteBuffer)} after performing the read to propagate the updated
     * positions.
     */
    public ByteBuffer getBufferForReading() {
        return ByteBuffer.wrap(bytes, readPosition, getPending());
    }

    /**
     * Update read position from a buffer previously obtained using {@link #getBufferForReading()}
     * The caller is responsible for making sure the buffer was obtained for this BIO and that the
     * position was not moved backwards. If the BIO was concurrently modified, the contents are
     * undefined, but the structure stays valid.
     */
    public void applyRead(ByteBuffer buffer) {
        if (buffer.array() == bytes) {
            readPosition = buffer.position();
            assert readPosition <= writePosition;
        }
    }

    /**
     * Wrap into a {@link ByteBuffer} meant for writing the data into this BIO. It is necessary to
     * call {@link #applyWrite(ByteBuffer)} after performing the read to propagate the updated
     * positions.
     *
     * @see #ensureWriteCapacity(int)
     */
    public ByteBuffer getBufferForWriting() {
        return ByteBuffer.wrap(bytes, writePosition, bytes.length - writePosition);
    }

    /**
     * Update write position from a buffer previously obtained using {@link #getBufferForWriting()}.
     * The caller is responsible for making sure the buffer was obtained for this BIO and that the
     * position was not moved backwards. If the BIO was concurrently modified, the contents are
     * undefined, but the structure stays valid.
     */
    public void applyWrite(ByteBuffer buffer) {
        if (buffer.array() == bytes) {
            writePosition = buffer.position();
            assert readPosition <= writePosition;
            assert writePosition <= bytes.length;
        }
    }

    /**
     * Make sure that at least {@code capacity} bytes can be written into this BIO. Disposes of the
     * already read part of the buffer when applicable.
     *
     * @param capacity Required capacity in bytes
     */
    public void ensureWriteCapacity(int capacity) throws OverflowException {
        if (bytes.length - writePosition < capacity) {
            int pending = getPending();
            if (bytes.length - pending < capacity) {
                byte[] newBytes = new byte[PythonUtils.addExact(capacity, pending)];
                PythonUtils.arraycopy(bytes, readPosition, newBytes, 0, pending);
                bytes = newBytes;
            } else {
                PythonUtils.arraycopy(bytes, readPosition, bytes, 0, pending);
            }
            readPosition = 0;
            writePosition = pending;
        }
    }

    /**
     * Read at most {@code length} bytes from this BIO into a byte array.
     *
     * @param length Maximum number of bytes to be read. Can be more than the actual size
     * @return A new byte array with the read content. Can be empty if there is no data to be read.
     */
    public byte[] read(int length) {
        int len = Math.min(length, getPending());
        byte[] to = new byte[len];
        PythonUtils.arraycopy(bytes, readPosition, to, 0, len);
        readPosition += len;
        return to;
    }

    /**
     * Write entire bytearray into this BIO.
     *
     * @param from Data to be written
     * @param length Length of data to be written
     */
    public void write(byte[] from, int length) throws OverflowException {
        ensureWriteCapacity(length);
        PythonUtils.arraycopy(from, 0, bytes, writePosition, Math.min(length, from.length));
        writePosition += length;
    }

    public boolean didWriteEOF() {
        return eofWritten;
    }

    /**
     * Return if we reached an EOF marker.
     */
    public boolean isEOF() {
        return eofWritten && getPending() == 0;
    }

    /**
     * Write an EOF marker.
     */
    public void writeEOF() {
        this.eofWritten = true;
    }

    public byte getByte(int offset) {
        return bytes[readPosition + offset];
    }
}
