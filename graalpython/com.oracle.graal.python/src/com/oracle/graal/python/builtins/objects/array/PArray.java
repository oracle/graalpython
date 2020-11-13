/*
 * Copyright (c) 2017, 2020, Oracle and/or its affiliates.
 * Copyright (c) 2013, Regents of the University of California
 *
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification, are
 * permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this list of
 * conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice, this list of
 * conditions and the following disclaimer in the documentation and/or other materials provided
 * with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS
 * OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
 * MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE
 * COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE
 * GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED
 * AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED
 * OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.oracle.graal.python.builtins.objects.array;

import java.util.Arrays;

import com.oracle.graal.python.builtins.objects.object.PythonBuiltinObject;
import com.oracle.graal.python.builtins.objects.object.PythonObjectLibrary;
import com.oracle.graal.python.util.BufferFormat;
import com.oracle.graal.python.util.PythonUtils;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.api.object.Shape;

// TODO interop library
@ExportLibrary(PythonObjectLibrary.class)
public final class PArray extends PythonBuiltinObject {
    private BufferFormat format;
    private String formatStr;
    private int lenght;
    private byte[] buffer;

    public PArray(Object clazz, Shape instanceShape, String formatStr, BufferFormat format, int length) {
        super(clazz, instanceShape);
        this.formatStr = formatStr;
        this.format = format;
        this.lenght = length;
        this.buffer = new byte[Math.max(32, length * format.bytesize)];
    }

    public BufferFormat getFormat() {
        return format;
    }

    public String getFormatStr() {
        return formatStr;
    }

    public byte[] getBuffer() {
        return buffer;
    }

    public int getLength() {
        return lenght;
    }

    public void setLenght(int lenght) {
        assert lenght >= 0;
        this.lenght = lenght;
    }

    public void ensureCapacity(int newLenght) {
        if (newLenght * format.bytesize > buffer.length) {
            byte[] newBuffer = new byte[Math.max(32, Math.multiplyExact(newLenght * format.bytesize, 2))];
            PythonUtils.arraycopy(buffer, 0, newBuffer, 0, buffer.length);
            buffer = newBuffer;
        }
    }

    public void ensureCapacityNoCopy(int newLenght) {
        if (newLenght * format.bytesize > buffer.length) {
            buffer = new byte[Math.max(32, Math.multiplyExact(newLenght * format.bytesize, 2))];
        }
    }

    @ExportMessage
    static boolean isBuffer(@SuppressWarnings("unused") PArray self) {
        return true;
    }

    @ExportMessage
    byte[] getBufferBytes() {
        try {
            return Arrays.copyOf(buffer, getBufferLength());
        } catch (Throwable t) {
            // Break exception edges
            CompilerDirectives.transferToInterpreterAndInvalidate();
            throw t;
        }
    }

    @ExportMessage
    int getBufferLength() {
        return lenght * format.bytesize;
    }
}
