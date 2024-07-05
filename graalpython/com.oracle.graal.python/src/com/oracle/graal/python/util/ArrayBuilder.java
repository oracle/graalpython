/*
 * Copyright (c) 2020, 2024, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.util;

import java.util.Arrays;

import com.oracle.truffle.api.CompilerDirectives;

/**
 * Simple array list implementation that is meant to be used for array initialization when the size
 * is not known upfront.
 */
public final class ArrayBuilder<T> {
    private Object[] data;
    private int size;

    public ArrayBuilder() {
        this(8);
    }

    public ArrayBuilder(int capacity) {
        this.data = new Object[capacity];
    }

    public void add(Object item) {
        if (size == data.length) {
            try {
                data = PythonUtils.arrayCopyOf(data, PythonUtils.multiplyExact(size, 2));
            } catch (OverflowException e) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                throw new OutOfMemoryError();
            }
        }
        this.data[size++] = item;
    }

    @SuppressWarnings("unchecked")
    public T get(int index) {
        assert index >= 0 && index < size;
        return (T) data[index];
    }

    @SuppressWarnings("unchecked")
    public T[] toArray(T[] newArray) {
        return (T[]) arrayCopyOf(data, size, newArray.getClass());
    }

    @SuppressWarnings("unchecked")
    public Object[] toObjectArray(Object[] newArray) {
        return arrayCopyOf(data, size, newArray.getClass());
    }

    public int size() {
        return size;
    }

    private static <T> T[] arrayCopyOf(T[] original, int newLength, Class<? extends T[]> newType) {
        try {
            return Arrays.copyOf(original, newLength, newType);
        } catch (Throwable t) {
            // this is really unexpected and we want to break exception edges in compiled code
            CompilerDirectives.transferToInterpreterAndInvalidate();
            throw t;
        }
    }
}
