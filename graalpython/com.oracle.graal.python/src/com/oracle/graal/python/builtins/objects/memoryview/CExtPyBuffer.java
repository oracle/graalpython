/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.builtins.objects.memoryview;

import com.oracle.truffle.api.CompilerDirectives.ValueType;
import com.oracle.truffle.api.interop.TruffleObject;

/**
 * This class implements the equivalent of {@code Py_buffer} on a Java level. This class basically
 * has the same fields as {@code Py_buffer} but all values are already converted to Java values if
 * appropriate.<br>
 * This class is intended to be used to store the converted result values created by a call to
 * {@code PyBufferProcs.bf_getbuffer} and can then be used to release the buffer.
 * 
 * <pre>
 *     typedef struct {
 *         void *buf;
 *         PyObject *obj;
 *         Py_ssize_t len;
 *         Py_ssize_t itemsize;
 *         int readonly;
 *         int ndim;
 *         char *format;
 *         Py_ssize_t *shape;
 *         Py_ssize_t *strides;
 *         Py_ssize_t *suboffsets;
 *         void *internal;
 * } Py_buffer;
 * </pre>
 * 
 */
@ValueType
public final class CExtPyBuffer implements TruffleObject {
    /* Flags for getting buffers */
    public static final int PyBUF_SIMPLE = 0;
    public static final int PyBUF_WRITABLE = 0x0001;
    public static final int PyBUF_FORMAT = 0x0004;
    public static final int PyBUF_ND = 0x0008;
    public static final int PyBUF_STRIDES = (0x0010 | PyBUF_ND);
    public static final int PyBUF_C_CONTIGUOUS = (0x0020 | PyBUF_STRIDES);
    public static final int PyBUF_F_CONTIGUOUS = (0x0040 | PyBUF_STRIDES);
    public static final int PyBUF_ANY_CONTIGUOUS = (0x0080 | PyBUF_STRIDES);
    public static final int PyBUF_INDIRECT = (0x0100 | PyBUF_STRIDES);

    public static final int PyBUF_CONTIG = (PyBUF_ND | PyBUF_WRITABLE);
    public static final int PyBUF_CONTIG_RO = (PyBUF_ND);

    public static final int PyBUF_STRIDED = (PyBUF_STRIDES | PyBUF_WRITABLE);
    public static final int PyBUF_STRIDED_RO = (PyBUF_STRIDES);

    public static final int PyBUF_RECORDS = (PyBUF_STRIDES | PyBUF_WRITABLE | PyBUF_FORMAT);
    public static final int PyBUF_RECORDS_RO = (PyBUF_STRIDES | PyBUF_FORMAT);

    public static final int PyBUF_FULL = (PyBUF_INDIRECT | PyBUF_WRITABLE | PyBUF_FORMAT);
    public static final int PyBUF_FULL_RO = (PyBUF_INDIRECT | PyBUF_FORMAT);

    /** An object behaving like a {@code void*} pointer. */
    private final Object buf;
    private final Object obj;
    private final int len;
    private final int itemSize;
    private final boolean readOnly;
    private final int dims;
    private final String format;
    private final int[] shape;
    private final int[] strides;
    private final int[] suboffsets;
    private final Object internal;

    public CExtPyBuffer(Object buf, Object obj, int len, int itemSize, boolean readOnly, int dims, String format, int[] shape, int[] strides, int[] suboffsets, Object internal) {
        this.buf = buf;
        this.obj = obj;
        this.len = len;
        this.itemSize = itemSize;
        this.readOnly = readOnly;
        this.dims = dims;
        this.format = format;
        this.shape = shape;
        this.strides = strides;
        this.suboffsets = suboffsets;
        this.internal = internal;
    }

    public Object getBuf() {
        return buf;
    }

    public Object getObj() {
        return obj;
    }

    public int getLen() {
        return len;
    }

    public int getItemSize() {
        return itemSize;
    }

    public boolean isReadOnly() {
        return readOnly;
    }

    public int getDims() {
        return dims;
    }

    public String getFormat() {
        return format;
    }

    public int[] getShape() {
        return shape;
    }

    public int[] getStrides() {
        return strides;
    }

    public int[] getSuboffsets() {
        return suboffsets;
    }

    public Object getInternal() {
        return internal;
    }
}
