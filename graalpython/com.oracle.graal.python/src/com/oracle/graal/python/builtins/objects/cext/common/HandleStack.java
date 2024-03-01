/*
 * Copyright (c) 2023, 2024, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.builtins.objects.cext.common;

import java.util.Arrays;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;

public final class HandleStack {
    private int[] handles;
    private int top = 0;

    public HandleStack(int initialCapacity) {
        this(initialCapacity, false);
    }

    public HandleStack(int initialCapacity, boolean fill) {
        handles = new int[initialCapacity];
        if (fill) {
            pushRange(0, initialCapacity);
        }
    }

    public void push(int i) {
        if (top >= handles.length) {
            handles = Arrays.copyOf(handles, handles.length * 2);
        }
        handles[top++] = i;
    }

    /**
     * Push a range of values to the stack.
     *
     * @param start The first value to push (inclusive).
     * @param end The last value to push (exclusive).
     */
    @TruffleBoundary
    public void pushRange(int start, int end) {
        int n = end - start;
        if (top + n > handles.length) {
            handles = Arrays.copyOf(handles, top + n);
        }
        for (int i = 0; i < n; i++) {
            handles[top + i] = end - i - 1;
        }
        top += n;
    }

    public int pop() {
        if (top <= 0) {
            return -1;
        }
        return handles[--top];
    }

    public int getTop() {
        return top;
    }
}