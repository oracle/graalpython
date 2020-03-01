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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.NoSuchElementException;

import com.oracle.graal.python.builtins.objects.cext.capi.CApiContext.NativeObjectReference;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;

public final class EscapedReferencesHeap implements Iterable<NativeObjectReference> {

    private static final int CHUNK_SIZE = 512;

    private final ArrayList<Chunk> chunks = new ArrayList<>();

    private int[] freeList = new int[0];
    private int top = 0;

    private void addToFreeList(int i) {
        if (top >= freeList.length) {
            freeList = Arrays.copyOf(freeList, freeList.length + CHUNK_SIZE);
        }
        freeList[top++] = i;
    }

    private int pop() {
        if (top <= 0) {
            return -1;
        }
        return freeList[--top];
    }

    private static final class Chunk {
        private final NativeObjectReference[] chunkArray = new NativeObjectReference[CHUNK_SIZE];
    }

    @TruffleBoundary
    public int reserve() {
        int freeId = pop();
        if (freeId == -1) {
            freeId = addChunk();
        }
        assert freeId != -1;
        return freeId;
    }

    private int addChunk() {
        Chunk chunk = new Chunk();
        int chunkId = chunks.size();
        chunks.add(chunk);
        assert chunks.get(chunkId) == chunk;

        for (int i = 0; i < CHUNK_SIZE; i++) {
            addToFreeList(chunkId * CHUNK_SIZE + i);
        }
        return pop();
    }

    @TruffleBoundary
    public void set(NativeObjectReference ref) {
        int id = ref.id;
        int chunkIdx = id / CHUNK_SIZE;
        int offset = id % CHUNK_SIZE;

        assert chunks.get(chunkIdx).chunkArray[offset] == null;
        chunks.get(chunkIdx).chunkArray[offset] = ref;
    }

    @TruffleBoundary
    public boolean remove(NativeObjectReference ref) {
        int id = ref.id;
        int chunkIdx = id / CHUNK_SIZE;
        int offset = id % CHUNK_SIZE;

        Chunk chunk = chunks.get(chunkIdx);
        NativeObjectReference element = chunk.chunkArray[offset];
        if (element == ref) {
            chunk.chunkArray[offset] = null;
            addToFreeList(id);
            return true;
        }
        return false;
    }

    @Override
    @TruffleBoundary
    public Iterator<NativeObjectReference> iterator() {
        return new EscapedReferencesHeapIterator();
    }

    private final class EscapedReferencesHeapIterator implements Iterator<NativeObjectReference> {

        private int chunkIdx = 0;
        private int offset = 0;

        @Override
        @TruffleBoundary
        public boolean hasNext() {
            if (chunkIdx < chunks.size() && offset < CHUNK_SIZE) {
                Chunk chunk = chunks.get(chunkIdx);
                return chunk.chunkArray[offset] != null;
            }
            return false;
        }

        @Override
        @TruffleBoundary
        public NativeObjectReference next() {
            if (chunkIdx < chunks.size()) {
                Chunk chunk = chunks.get(chunkIdx);
                NativeObjectReference ref = chunk.chunkArray[offset];
                if (offset < CHUNK_SIZE - 1) {
                    offset++;
                } else {
                    offset = 0;
                    chunkIdx++;
                }
                return ref;
            }
            throw new NoSuchElementException();
        }
    }

}
