package com.oracle.graal.python.builtins.objects.ssl;

import java.nio.ByteBuffer;

import com.oracle.graal.python.util.PythonUtils;

/**
 * Our rough equivalent of OpenSSL memory BIO objects used as buffers when performing SSL
 * wrapping/unwrapping. They are similar to Java {@link ByteBuffer}, but they have a separate
 * position for reading and writing.
 */
public class MemoryBIO {
    private byte[] bytes = new byte[0];
    private int readPosition;
    private int writePosition;
    private boolean eofWritten;

    public int getPending() {
        return writePosition - readPosition;
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
     */
    public void applyRead(ByteBuffer buffer) {
        readPosition = buffer.position();
        assert readPosition <= bytes.length;
        assert readPosition <= writePosition;
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
     * Update write position from a buffer previously obtained using {@link #getBufferForWriting()}
     */
    public void applyWrite(ByteBuffer buffer) {
        writePosition = buffer.position();
        assert writePosition <= bytes.length;
        assert readPosition <= writePosition;
    }

    /**
     * Make sure that at least {@code capacity} bytes can be written into this BIO. Disposes of the
     * already read part of the buffer when applicable.
     * 
     * @param capacity Required capacity in bytes
     */
    public void ensureWriteCapacity(int capacity) {
        if (bytes.length - writePosition < capacity) {
            int pending = getPending();
            if (bytes.length - pending < capacity) {
                byte[] newBytes = new byte[capacity + pending];
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
     * Read at most {@code lenght} bytes from this BIO into a byte array.
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
     * Write entire bytearray into this BIO. Must not be called after EOF was written.
     * 
     * @param from Data to be written
     * @param length Lenght of data to be written
     */
    public void write(byte[] from, int length) {
        assert !isEOF();
        ensureWriteCapacity(length);
        PythonUtils.arraycopy(from, 0, bytes, writePosition, Math.min(length, from.length));
        writePosition += length;
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
}
