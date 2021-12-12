package com.oracle.graal.python.frozen;

import java.util.Arrays;

public final class PythonFrozenModule {
    String name;
    byte[] code;
    int size;

    public PythonFrozenModule(String name, byte[] code, int size) {
        this.name = name;
        this.code = code;
        this.size = size;
    }

    // Some modules are too big to be stored within the 2^16 bytes size limit of code artifacts
    // and need to be passed in in multiple portions
    public PythonFrozenModule(String name, byte[][] code, int size) {
        this.name = name;
        this.code = flattenByteArray(code);
        this.size = size;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public byte[] getCode() {
        return code;
    }

    public void setCode(byte[] code) {
        this.code = code;
    }

    public int getSize() {
        return size;
    }

    public void setSize(int size) {
        this.size = size;
    }

    //@TruffleBoundary How to include TruffleBoundary here? / Necessary?
    private static byte[] flattenByteArray(byte[][] byteArrays) {
        int totalLength = 0;
        for (byte[] array : byteArrays) {
            totalLength += array.length;
        }

        if (totalLength == 0) return new byte[0];
        byte[] first = byteArrays[0];

        byte[] result = new byte[totalLength];
        int offset = 0;

        for (byte[] array : byteArrays) {
            System.arraycopy(array, 0, result, offset, array.length);
            offset += array.length;
        }

        return result;
    }
}