/*
 * Copyright (c) 2020, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.builtins.objects.cext.capi;

import java.util.Arrays;
import java.util.Iterator;

import com.oracle.graal.python.builtins.objects.cext.capi.CApiContext.NativeObjectReference;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;

public final class NativeReferenceStack implements Iterable<NativeObjectReference> {
    private static final int INITIAL_CAPACITY = 64;

    private final IntegerStack freeList;
    private NativeObjectReference[] nativeObjectWrapperList;

    public NativeReferenceStack() {
        nativeObjectWrapperList = new NativeObjectReference[INITIAL_CAPACITY];
        IntegerStack freeList = new IntegerStack(INITIAL_CAPACITY);
        freeList.addToFreeList(0, INITIAL_CAPACITY);
        this.freeList = freeList;
    }

    @TruffleBoundary
    private void enlargeNativeReferenceList() {
        int oldSize = nativeObjectWrapperList.length;
        int newSize = oldSize * 2;
        nativeObjectWrapperList = Arrays.copyOf(nativeObjectWrapperList, newSize);
        freeList.addToFreeList(oldSize, newSize);
    }

    public NativeObjectReference get(int idx) {
        assert 0 <= idx && idx < nativeObjectWrapperList.length;
        return nativeObjectWrapperList[idx];
    }

    public NativeObjectReference remove(int idx) {
        assert 0 <= idx && idx < nativeObjectWrapperList.length;
        NativeObjectReference ref = nativeObjectWrapperList[idx];
        nativeObjectWrapperList[idx] = null;
        freeList.push(idx);
        return ref;
    }

    public int reserve() {
        int nativeRefID = freeList.pop();
        if (nativeRefID == -1) {
            enlargeNativeReferenceList();
            nativeRefID = freeList.pop();
        }
        assert nativeRefID != -1;
        return nativeRefID;
    }

    public void commit(int idx, NativeObjectReference nativeObjectReference) {
        assert 0 <= idx && idx < nativeObjectWrapperList.length;
        assert nativeObjectWrapperList[idx] == null;
        nativeObjectWrapperList[idx] = nativeObjectReference;
    }

    @Override
    public Iterator<NativeObjectReference> iterator() {
        return Arrays.asList(nativeObjectWrapperList).iterator();
    }

    static final class IntegerStack {
        private int[] stack;
        private int top = 0;

        public IntegerStack(int initialCapacity) {
            stack = new int[initialCapacity];
        }

        void push(int i) {
            if (top >= stack.length) {
                stack = Arrays.copyOf(stack, stack.length * 2);
            }
            stack[top++] = i;
        }

        int pop() {
            if (top <= 0) {
                return -1;
            }
            return stack[--top];
        }

        void addToFreeList(int start, int end) {
            for (int i = end-1; i >= start; i--) {
                push(i);
            }
        }
    }
}
