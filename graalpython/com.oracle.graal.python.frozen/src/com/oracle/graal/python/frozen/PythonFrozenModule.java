package com.oracle.graal.python.frozen;

public final class PythonFrozenModule {
    String name;
    byte[] code;
    int size;

    public PythonFrozenModule(String name, byte[] code, int size) {
        this.name = name;
        this.code = code;
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
}
