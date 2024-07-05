/* Copyright (c) 2020, 2024, Oracle and/or its affiliates.
 * Copyright (C) 1996-2020 Python Software Foundation
 *
 * Licensed under the PYTHON SOFTWARE FOUNDATION LICENSE VERSION 2
 */
package com.oracle.graal.python.builtins.objects.struct;

import static com.oracle.graal.python.util.NumericSupport.asUnsigned;

import com.oracle.graal.python.builtins.objects.object.PythonBuiltinObject;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerDirectives.ValueType;
import com.oracle.truffle.api.object.Shape;

public final class PStruct extends PythonBuiltinObject {
    @CompilationFinal(dimensions = 1) private final byte[] format;
    private final int size;
    private final int len;
    public final FormatAlignment formatAlignment;
    @CompilationFinal(dimensions = 1) private final FormatCode[] codes;

    public PStruct(Object clazz, Shape instanceShape, StructInfo structInfo) {
        this(clazz, instanceShape, structInfo.format, structInfo.size, structInfo.len, structInfo.formatAlignment, structInfo.codes);
    }

    public PStruct(Object clazz, Shape instanceShape, byte[] format, int size, int len, FormatAlignment formatAlignment, FormatCode[] codes) {
        super(clazz, instanceShape);
        this.format = format;
        this.size = size;
        this.len = len;
        this.formatAlignment = formatAlignment;
        this.codes = codes;
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

    public boolean isNative() {
        return formatAlignment.nativeSizing;
    }

    @ValueType
    public record StructInfo(byte[] format, int size, int len, FormatAlignment formatAlignment, FormatCode[] codes) {
    }
}
