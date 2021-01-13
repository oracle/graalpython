package com.oracle.graal.python.builtins.objects.ssl;

import java.nio.ByteBuffer;

import com.oracle.graal.python.util.PythonUtils;

public class MemoryBIO {
    byte[] bytes = new byte[0];
    int readPosition;
    int writePosition;

    int getPending() {
        return writePosition - readPosition;
    }

    ByteBuffer getBufferForReading() {
        return ByteBuffer.wrap(bytes, readPosition, getPending());
    }

    void applyRead(ByteBuffer buffer) {
        readPosition = buffer.position();
        assert readPosition <= bytes.length;
        assert readPosition <= writePosition;
    }

    void applyWrite(ByteBuffer buffer) {
        writePosition = buffer.position();
        assert writePosition <= bytes.length;
        assert readPosition <= writePosition;
    }

    ByteBuffer getBufferForWriting() {
        return ByteBuffer.wrap(bytes, writePosition, bytes.length - writePosition);
    }

    void ensureWriteCapacity(int capacity) {
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
}
