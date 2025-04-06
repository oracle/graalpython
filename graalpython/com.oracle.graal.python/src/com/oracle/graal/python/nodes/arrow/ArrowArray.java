/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.nodes.arrow;

import com.oracle.graal.python.builtins.objects.capsule.PyCapsule;
import com.oracle.graal.python.util.PythonUtils;
import sun.misc.Unsafe;

import static com.oracle.graal.python.builtins.objects.cext.structs.CStructAccess.POINTER_SIZE;

/**
 * C Data Interface ArrowArray.
 * <p>
 * Represents a wrapper for the following C structure:
 *
 * <pre>
 * struct ArrowArray {
 *     // Array data description
 *     int64_t length;                          0
 *     int64_t null_count;                      8
 *     int64_t offset;                          16
 *     int64_t n_buffers;                       24
 *     int64_t n_children;                      32
 *     const void** buffers;                    40
 *     struct ArrowArray** children;            48
 *     struct ArrowArray* dictionary;           56
 *
 *     // Release callback
 *     void (*release)(struct ArrowArray*);     64
 *     // Opaque producer-specific data
 *     void* private_data;                      72
 * };
 * </pre>
 */
public class ArrowArray {

    private static final Unsafe unsafe = PythonUtils.initUnsafe();
    public static final byte[] CAPSULE_NAME = PyCapsule.capsuleName("arrow_array");

    public static final byte NULL = 0;
    private static final byte SIZE_OF = 80;

    private static final long LENGTH_INDEX = 0;
    private static final long NULL_COUNT_INDEX = POINTER_SIZE;
    private static final long OFFSET_INDEX = 2 * POINTER_SIZE;
    private static final long N_BUFFERS_INDEX = 3 * POINTER_SIZE;
    private static final long N_CHILDREN_INDEX = 4 * POINTER_SIZE;
    private static final long BUFFERS_INDEX = 5 * POINTER_SIZE;
    private static final long CHILDREN_INDEX = 6 * POINTER_SIZE;
    private static final long DICTIONARY_INDEX = 7 * POINTER_SIZE;
    private static final long RELEASE_CALLBACK_INDEX = 8 * POINTER_SIZE;
    private static final long PRIVATE_DATA_INDEX = 9 * POINTER_SIZE;

    private final long memoryAddr;

    private ArrowArray(long memoryAddr) {
        this.memoryAddr = memoryAddr;
    }

    public static ArrowArray allocate(long length, long nullCount, long offset, long nBuffers, long nChildren, long buffers, long children, long dictionary, long release, long privateData) {
        var memoryAddr = unsafe.allocateMemory(SIZE_OF);
        unsafe.putLong(memoryAddr + LENGTH_INDEX, length);
        unsafe.putLong(memoryAddr + NULL_COUNT_INDEX, nullCount);
        unsafe.putLong(memoryAddr + OFFSET_INDEX, offset);
        unsafe.putLong(memoryAddr + N_BUFFERS_INDEX, nBuffers);
        unsafe.putLong(memoryAddr + N_CHILDREN_INDEX, nChildren);
        unsafe.putLong(memoryAddr + BUFFERS_INDEX, buffers);
        unsafe.putLong(memoryAddr + CHILDREN_INDEX, children);
        unsafe.putLong(memoryAddr + DICTIONARY_INDEX, dictionary);
        unsafe.putLong(memoryAddr + RELEASE_CALLBACK_INDEX, release);
        unsafe.putLong(memoryAddr + PRIVATE_DATA_INDEX, privateData);

        return new ArrowArray(memoryAddr);
    }

    public long memoryAddress() {
        return memoryAddr;
    }

    public long releaseCallback() {
        return unsafe.getLong(memoryAddr + RELEASE_CALLBACK_INDEX);
    }

    public static ArrowArray wrap(long arrowArrayPointer) {
        return new ArrowArray(arrowArrayPointer);
    }

    public boolean isReleased() {
        return releaseCallback() == NULL;
    }
}
