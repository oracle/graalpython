/* Copyright (c) 2020, 2026, Oracle and/or its affiliates.
 * Copyright (C) 1996-2020 Python Software Foundation
 *
 * Licensed under the PYTHON SOFTWARE FOUNDATION LICENSE VERSION 2
 */
package com.oracle.graal.python.builtins.objects.struct;

import static com.oracle.graal.python.util.NumericSupport.asUnsigned;

import com.oracle.graal.python.builtins.objects.object.PythonBuiltinObject;
import com.oracle.truffle.api.CompilerDirectives.ValueType;
import com.oracle.truffle.api.object.Shape;

public final class PStruct extends PythonBuiltinObject {
    private byte[] format;
    private int size;
    private int len;
    public FormatAlignment formatAlignment;
    private FormatCode[] codes;

    public PStruct(Object clazz, Shape instanceShape, StructInfo structInfo) {
        super(clazz, instanceShape);
        setStructInfo(structInfo);
    }

    public void setStructInfo(StructInfo structInfo) {
        this.format = structInfo.format;
        this.size = structInfo.size;
        this.len = structInfo.len;
        this.formatAlignment = structInfo.formatAlignment;
        this.codes = structInfo.codes;
    }

    public StructInfo getStructInfo() {
        return new StructInfo(format, size, len, formatAlignment, codes);
    }

    public int getSize() {
        return size;
    }

    public long getUnsignedSize() {
        return asUnsigned(size);
    }

    public FormatCode[] getCodes() {
        return codes;
    }

    public byte[] getFormat() {
        return format;
    }

    public int getLen() {
        return len;
    }

    public boolean isBigEndian() {
        return formatAlignment.bigEndian;
    }

    @ValueType
    public record StructInfo(byte[] format, int size, int len, FormatAlignment formatAlignment, FormatCode[] codes) {
    }
}
