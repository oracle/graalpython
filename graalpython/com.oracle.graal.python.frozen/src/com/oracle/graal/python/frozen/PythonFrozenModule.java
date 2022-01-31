package com.oracle.graal.python.frozen;

public final class PythonFrozenModule {
    final String name;
    final byte[] code;
    final int size;

    public PythonFrozenModule(String name, byte[] code, int size) {
        this.name = name;
        this.code = code;
        this.size = size;
    }

    public String getName() {
        return name;
    }

    public byte[] getCode() {
        return code;
    }

    public int getSize() {
        return size;
    }

}