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
package com.oracle.graal.python.builtins.objects.buffer;

/**
 * Flags for getting buffers. Extracted from CPython. Used by {@link PythonBufferAcquireLibrary}.
 */
public interface BufferFlags {
    int PyBUF_SIMPLE = 0;
    int PyBUF_WRITABLE = 0x0001;
    int PyBUF_FORMAT = 0x0004;
    int PyBUF_ND = 0x0008;
    int PyBUF_CONTIG_RO = (PyBUF_ND);
    int PyBUF_CONTIG = (PyBUF_ND | PyBUF_WRITABLE);
    int PyBUF_STRIDES = (0x0010 | PyBUF_ND);
    int PyBUF_RECORDS_RO = (PyBUF_STRIDES | PyBUF_FORMAT);
    int PyBUF_RECORDS = (PyBUF_STRIDES | PyBUF_WRITABLE | PyBUF_FORMAT);
    int PyBUF_STRIDED_RO = (PyBUF_STRIDES);
    int PyBUF_STRIDED = (PyBUF_STRIDES | PyBUF_WRITABLE);
    int PyBUF_INDIRECT = (0x0100 | PyBUF_STRIDES);
    int PyBUF_FULL_RO = (PyBUF_INDIRECT | PyBUF_FORMAT);
    int PyBUF_FULL = (PyBUF_INDIRECT | PyBUF_WRITABLE | PyBUF_FORMAT);
    int PyBUF_ANY_CONTIGUOUS = (0x0080 | PyBUF_STRIDES);
    int PyBUF_F_CONTIGUOUS = (0x0040 | PyBUF_STRIDES);
    int PyBUF_C_CONTIGUOUS = (0x0020 | PyBUF_STRIDES);

    static boolean requestsWritable(int flags) {
        return (flags & PyBUF_WRITABLE) != 0;
    }

    static boolean requestsFormat(int flags) {
        return (flags & PyBUF_FORMAT) != 0;
    }

    static boolean requestsShape(int flags) {
        return (flags & PyBUF_ND) == PyBUF_ND;
    }

    static boolean requestsStrides(int flags) {
        return (flags & PyBUF_STRIDES) == PyBUF_STRIDES;
    }

    static boolean requestsIndirect(int flags) {
        return (flags & PyBUF_INDIRECT) == PyBUF_INDIRECT;
    }

    static boolean requestsAnyContiguous(int flags) {
        return (flags & PyBUF_ANY_CONTIGUOUS) == PyBUF_ANY_CONTIGUOUS;
    }

    static boolean requestsCContiguous(int flags) {
        return (flags & PyBUF_C_CONTIGUOUS) == PyBUF_C_CONTIGUOUS;
    }

    static boolean requestsFContiguous(int flags) {
        return (flags & PyBUF_F_CONTIGUOUS) == PyBUF_F_CONTIGUOUS;
    }
}
