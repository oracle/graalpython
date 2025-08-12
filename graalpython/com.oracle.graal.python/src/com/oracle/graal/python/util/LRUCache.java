/*
 * Copyright (c) 2024, 2025, Oracle and/or its affiliates. All rights reserved.
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

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.TruffleSafepoint;
import com.oracle.truffle.api.nodes.Node;

public class LRUCache<K, V> {
    private final LRUHashMap<K, V> lruHashMap;
    private final ReadWriteLock readWriteLock = new ReentrantReadWriteLock();
    private final Lock read = readWriteLock.readLock();
    private final Lock write = readWriteLock.writeLock();

    public LRUCache(int maxSize) {
        this.lruHashMap = new LRUHashMap<>(maxSize);
    }

    static class LRUHashMap<K, V> extends LinkedHashMap<K, V> {
        private static final long serialVersionUID = -2396748959641741279L;
        private final int maxSize;

        public LRUHashMap(int maxSize) {
            super(maxSize);
            this.maxSize = maxSize;
        }

        @Override
        protected boolean removeEldestEntry(Map.Entry<K, V> eldest) {
            return size() > maxSize;
        }
    }

    @TruffleBoundary
    public V get(Node location, K key) {
        try {
            TruffleSafepoint.setBlockedThreadInterruptible(location, Lock::lockInterruptibly, read);
            return lruHashMap.get(key);
        } finally {
            read.unlock();
        }
    }

    @TruffleBoundary
    public V put(Node location, K key, V value) {
        try {
            TruffleSafepoint.setBlockedThreadInterruptible(location, Lock::lockInterruptibly, write);
            return lruHashMap.putIfAbsent(key, value);
        } finally {
            write.unlock();
        }
    }

    @TruffleBoundary
    public void clear(Node location) {
        try {
            TruffleSafepoint.setBlockedThreadInterruptible(location, Lock::lockInterruptibly, write);
            lruHashMap.clear();
        } finally {
            write.unlock();
        }
    }
}
