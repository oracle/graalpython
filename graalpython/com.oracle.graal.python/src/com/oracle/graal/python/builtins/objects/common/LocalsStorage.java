/*
 * Copyright (c) 2018, 2019, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.builtins.objects.common;

import java.util.Iterator;
import java.util.NoSuchElementException;

import com.oracle.graal.python.builtins.objects.cell.PCell;
import com.oracle.graal.python.nodes.frame.FrameSlotIDs;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.FrameSlot;
import com.oracle.truffle.api.frame.MaterializedFrame;

public class LocalsStorage extends HashingStorage {

    /* This won't be the real (materialized) frame but a clone of it. */
    private final MaterializedFrame frame;
    private int len = -1;

    public LocalsStorage(FrameDescriptor fd) {
        this.frame = Truffle.getRuntime().createMaterializedFrame(new Object[0], fd);
    }

    public LocalsStorage(MaterializedFrame frame) {
        this.frame = frame;
    }

    private Object getValue(FrameSlot slot) {
        if (slot != null) {
            Object value = frame.getValue(slot);
            if (value instanceof PCell) {
                return ((PCell) value).getRef();
            }
            return value;
        }
        return null;
    }

    public MaterializedFrame getFrame() {
        return frame;
    }

    @Override
    public void addAll(HashingStorage other, Equivalence eq) {
        throw UnmodifiableStorageException.INSTANCE;
    }

    @Override
    @TruffleBoundary
    public Object getItem(Object key, Equivalence eq) {
        assert eq == DEFAULT_EQIVALENCE;
        if (!FrameSlotIDs.isUserFrameSlot(key)) {
            return null;
        }
        FrameSlot slot = frame.getFrameDescriptor().findFrameSlot(key);
        return getValue(slot);
    }

    @Override
    public void setItem(Object key, Object value, Equivalence eq) {
        throw UnmodifiableStorageException.INSTANCE;
    }

    @Override
    public Iterable<Object> keys() {
        return new Iterable<Object>() {

            public Iterator<Object> iterator() {
                return new KeysIterator();
            }
        };
    }

    @Override
    @TruffleBoundary
    public boolean hasKey(Object key, Equivalence eq) {
        assert eq == DEFAULT_EQIVALENCE;
        if (!FrameSlotIDs.isUserFrameSlot(key)) {
            return false;
        }
        // Deleting variables from a frame means to write 'null' into the slot. So we also need to
        // check the value.
        return frame.getFrameDescriptor().findFrameSlot(key) != null && getItem(key, eq) != null;
    }

    @Override
    @TruffleBoundary
    public int length() {
        if (len == -1) {
            len = frame.getFrameDescriptor().getSize();
            for (FrameSlot slot : frame.getFrameDescriptor().getSlots()) {
                Object identifier = slot.getIdentifier();
                if (!FrameSlotIDs.isUserFrameSlot(identifier) || frame.getValue(slot) == null) {
                    len--;
                }
            }
        }
        return len;
    }

    @Override
    public boolean remove(Object key, Equivalence eq) {
        throw UnmodifiableStorageException.INSTANCE;
    }

    @Override
    @TruffleBoundary
    public Iterable<Object> values() {
        return new Iterable<Object>() {

            public Iterator<Object> iterator() {
                return new ValuesIterator();
            }
        };
    }

    @Override
    @TruffleBoundary
    public Iterable<DictEntry> entries() {
        return new Iterable<DictEntry>() {

            public Iterator<DictEntry> iterator() {
                return new ItemsIterator();
            }
        };
    }

    @Override
    @TruffleBoundary
    public void clear() {
        throw UnmodifiableStorageException.INSTANCE;
    }

    @Override
    public HashingStorage copy(Equivalence eq) {
        assert eq == DEFAULT_EQIVALENCE;
        return new LocalsStorage(frame);
    }

    private abstract class LocalsIterator<T> implements Iterator<T> {

        private Iterator<? extends FrameSlot> keys;
        private FrameSlot nextFrameSlot = null;

        @Override
        public boolean hasNext() {
            if (frame.getFrameDescriptor().getSize() == 0) {
                return false;
            }
            if (nextFrameSlot == null) {
                return loadNext();
            }
            return true;
        }

        @TruffleBoundary
        public FrameSlot nextSlot() {
            if (hasNext()) {
                assert nextFrameSlot != null;
                FrameSlot value = nextFrameSlot;
                nextFrameSlot = null;
                return value;
            }
            throw new NoSuchElementException();
        }

        @TruffleBoundary
        private boolean loadNext() {
            while (keysIterator().hasNext()) {
                FrameSlot nextCandidate = keysIterator().next();
                Object identifier = nextCandidate.getIdentifier();
                if (identifier instanceof String) {
                    if (FrameSlotIDs.isUserFrameSlot(identifier)) {
                        Object nextValue = frame.getValue(nextCandidate);
                        if (nextValue != null) {
                            nextFrameSlot = nextCandidate;
                            return true;
                        }
                    }
                }
            }
            return false;
        }

        protected final Iterator<? extends FrameSlot> keysIterator() {
            if (keys == null) {
                keys = frame.getFrameDescriptor().getSlots().iterator();
            }
            return keys;
        }
    }

    private class KeysIterator extends LocalsIterator<Object> {
        @Override
        @TruffleBoundary
        public Object next() {
            return nextSlot().getIdentifier();
        }
    }

    private class ValuesIterator extends LocalsIterator<Object> {
        @Override
        @TruffleBoundary
        public Object next() {
            return getValue(nextSlot());
        }
    }

    private class ItemsIterator extends LocalsIterator<DictEntry> {
        @Override
        @TruffleBoundary
        public DictEntry next() {
            FrameSlot slot = nextSlot();
            return new DictEntry(slot.getIdentifier(), getValue(slot));
        }
    }
}
