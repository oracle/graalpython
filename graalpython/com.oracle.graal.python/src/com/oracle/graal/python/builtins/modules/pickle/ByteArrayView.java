package com.oracle.graal.python.builtins.modules.pickle;

import com.oracle.graal.python.util.PythonUtils;

public class ByteArrayView {
    private final byte[] bytes;
    private int offset;

    public ByteArrayView(byte[] bytes) {
        this(bytes, 0);
    }

    public ByteArrayView(byte[] bytes, int offset) {
        this.bytes = bytes;
        this.offset = offset;
        validateOffset(0);
    }

    private void validateOffset(int n) {
        assert offset + n >= 0 && offset + n <= bytes.length;
    }

    public void add(int n) {
        validateOffset(n);
        this.offset += n;
    }

    public void sub(int n) {
        this.add(-n);
    }

    public byte[] getBytes(int len) {
        assert len >= 0 && (len + offset) >= 0 && (len + offset) <= bytes.length;
        if (offset == 0 && len == bytes.length) {
            return bytes;
        }
        byte[] arr = new byte[len];
        PythonUtils.arraycopy(this.bytes, this.offset, arr, 0, len);
        return arr;
    }

    public int getOffset() {
        return offset;
    }

    public byte get(int i) {
        return this.bytes[this.offset + i];
    }

    public int getUnsigned(int i) {
        return get(i) & 0xff;
    }

    public void put(int i, byte value) {
        this.bytes[this.offset + i] = value;
    }

    public void writeSize64(int value) {
        PickleUtils.writeSize64(bytes, offset, value);
    }

    public void memmove(int off, int num) {
        PythonUtils.arraycopy(this.bytes, this.offset + off, this.bytes, this.offset, num);
    }
}
