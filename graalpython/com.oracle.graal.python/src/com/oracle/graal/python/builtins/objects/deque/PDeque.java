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
package com.oracle.graal.python.builtins.objects.deque;

import java.util.ArrayDeque;
import java.util.Iterator;

import com.oracle.graal.python.builtins.objects.object.PythonBuiltinObject;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.object.Shape;

/**
 * A simple wrapper around Java's {@link ArrayDeque}.
 */
public final class PDeque extends PythonBuiltinObject {
    final ArrayDeque<Object> data = createArrayDeque();
    private int maxLength = -1;

    /**
     * This is a modification counter and used to produce exceptions if the deque is modified during
     * iteration. We need it because {@link ArrayDeque}'s iterators have a slightly different
     * behavior. E.g. if you iterate the deque to the end and the last element comparison would
     * modify the deque, then {@link ArrayDeque} doesn't complain while CPython's implementation
     * still complains. The main difference is that CPython will always check if the deque was
     * modifed right after it called out for {@code __eq__}.
     */
    private int state;

    public PDeque(Object cls, Shape instanceShape) {
        super(cls, instanceShape);
    }

    @TruffleBoundary
    private static ArrayDeque<Object> createArrayDeque() {
        return new ArrayDeque<>();
    }

    int getSize() {
        return data.size();
    }

    int getMaxLength() {
        return maxLength;
    }

    void setMaxLength(int maxLength) {
        this.maxLength = maxLength;
    }

    @TruffleBoundary
    void append(Object value) {
        assert maxLength == -1 || data.size() <= maxLength;
        data.addLast(value);
        if (maxLength != -1 && data.size() > maxLength) {
            popLeft();
        } else {
            state++;
        }

        assert maxLength == -1 || data.size() <= maxLength;
    }

    @TruffleBoundary
    void appendLeft(Object value) {
        assert maxLength == -1 || data.size() <= maxLength;
        data.addFirst(value);
        if (maxLength != -1 && data.size() > maxLength) {
            pop();
        } else {
            state++;
        }
        assert maxLength == -1 || data.size() <= maxLength;
    }

    /**
     * Returns {@code null} if empty.
     */
    @TruffleBoundary
    Object pop() {
        state++;
        return data.pollLast();
    }

    /**
     * Returns {@code null} if empty.
     */
    @TruffleBoundary
    Object popLeft() {
        state++;
        return data.pollFirst();
    }

    /**
     * Returns {@code null} if empty.
     */
    @TruffleBoundary
    Object peekLeft() {
        return data.peekFirst();
    }

    @TruffleBoundary
    void addAll(Object[] c) {
        for (Object e : c) {
            append(e);
        }
    }

    @TruffleBoundary
    void addAll(PDeque other) {
        for (Object e : other.data) {
            append(e);
        }
    }

    @TruffleBoundary
    public Iterator<Object> iterator() {
        return data.iterator();
    }

    @TruffleBoundary
    public Iterator<Object> reverseIterator() {
        return data.descendingIterator();
    }

    @TruffleBoundary
    public void clear() {
        data.clear();
        state++;
    }

    @TruffleBoundary
    public void setItem(int idx, Object value) {
        assert 0 <= idx && idx < data.size();
        int n = data.size() - idx - 1;
        Object[] savedItems = new Object[n];
        for (int i = 0; i < savedItems.length; i++) {
            savedItems[i] = data.pollLast();
        }
        // this removes the item we want to replace
        data.pollLast();
        assert data.size() == idx;
        if (value != null) {
            data.addLast(value);
        } else {
            // removal case: this alters the number of elements, so modify the state
            state++;
        }

        // re-add saved items
        for (int i = savedItems.length - 1; i >= 0; i--) {
            data.addLast(savedItems[i]);
        }
        assert maxLength == -1 || data.size() <= maxLength;
        assert value != null && data.size() == n + idx + 1 || value == null && data.size() == n + idx;
    }

    public int getState() {
        return state;
    }
}
