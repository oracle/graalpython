/*
 * Copyright (c) 2018, 2020, Oracle and/or its affiliates. All rights reserved.
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
import com.oracle.graal.python.builtins.objects.common.HashingStorageLibrary.ForEachNode;
import com.oracle.graal.python.builtins.objects.common.HashingStorageLibrary.HashingStorageIterable;
import com.oracle.graal.python.builtins.objects.function.PArguments.ThreadState;
import com.oracle.graal.python.builtins.objects.object.PythonObjectLibrary;
import com.oracle.graal.python.builtins.objects.str.PString;
import com.oracle.graal.python.nodes.PGuards;
import com.oracle.graal.python.nodes.frame.FrameSlotIDs;
import com.oracle.graal.python.nodes.object.IsBuiltinClassProfile;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Exclusive;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.FrameSlot;
import com.oracle.truffle.api.frame.MaterializedFrame;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.profiles.ConditionProfile;
import java.util.List;

@ExportLibrary(HashingStorageLibrary.class)
public class LocalsStorage extends HashingStorage {
    /* This won't be the real (materialized) frame but a clone of it. */
    protected final MaterializedFrame frame;
    private int len = -1;

    public LocalsStorage(FrameDescriptor fd) {
        this.frame = Truffle.getRuntime().createMaterializedFrame(new Object[0], fd);
    }

    public LocalsStorage(MaterializedFrame frame) {
        this.frame = frame;
    }

    public MaterializedFrame getFrame() {
        return frame;
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

    @Override
    @ExportMessage
    public int length() {
        if (len == -1) {
            CompilerDirectives.transferToInterpreter();
            calculateLength();
        }
        return len;
    }

    @TruffleBoundary
    private void calculateLength() {
        len = frame.getFrameDescriptor().getSize();
        for (FrameSlot slot : frame.getFrameDescriptor().getSlots()) {
            Object identifier = slot.getIdentifier();
            if (!FrameSlotIDs.isUserFrameSlot(identifier) || frame.getValue(slot) == null) {
                len--;
            }
        }
    }

    @SuppressWarnings("unused")
    @ExportMessage
    @ImportStatic(PGuards.class)
    static class GetItemWithState {
        @Specialization(guards = {"key == cachedKey", "desc == self.frame.getFrameDescriptor()"}, limit = "3", assumptions = "desc.getVersion()")
        static Object getItemCached(LocalsStorage self, String key, ThreadState state,
                        @Cached("key") String cachedKey,
                        @Cached("self.frame.getFrameDescriptor()") FrameDescriptor desc,
                        @Cached("desc.findFrameSlot(key)") FrameSlot slot) {
            return self.getValue(slot);
        }

        @Specialization(replaces = "getItemCached")
        static Object string(LocalsStorage self, String key, ThreadState state) {
            if (!FrameSlotIDs.isUserFrameSlot(key)) {
                return null;
            }
            FrameSlot slot = findSlot(self, key);
            return self.getValue(slot);
        }

        @Specialization(guards = "isBuiltinString(key, profile)")
        static Object pstring(LocalsStorage self, PString key, ThreadState state,
                        @Cached IsBuiltinClassProfile profile) {
            return string(self, key.getValue(), state);
        }

        @Specialization(guards = "!isBuiltinString(key, profile)")
        static Object notString(LocalsStorage self, Object key, ThreadState state,
                        @Cached IsBuiltinClassProfile profile,
                        @CachedLibrary(limit = "2") PythonObjectLibrary lib,
                        @Exclusive @Cached("createBinaryProfile()") ConditionProfile gotState) {
            CompilerDirectives.bailout("accessing locals storage with non-string keys is slow");
            long hash = self.getHashWithState(key, lib, state, gotState);
            for (FrameSlot slot : self.frame.getFrameDescriptor().getSlots()) {
                Object currentKey = slot.getIdentifier();
                if (currentKey instanceof String) {
                    long keyHash;
                    if (gotState.profile(state != null)) {
                        keyHash = lib.hashWithState(currentKey, state);
                        if (keyHash == hash && lib.equalsWithState(key, currentKey, lib, state)) {
                            return self.getValue(slot);
                        }
                    } else {
                        keyHash = lib.hash(currentKey);
                        if (keyHash == hash && lib.equals(key, currentKey, lib)) {
                            return self.getValue(slot);
                        }
                    }
                }
            }
            return null;
        }

        @TruffleBoundary
        private static FrameSlot findSlot(LocalsStorage self, Object key) {
            return self.frame.getFrameDescriptor().findFrameSlot(key);
        }
    }

    @ExportMessage
    HashingStorage setItemWithState(Object key, Object value, ThreadState state,
                    @CachedLibrary(limit = "2") HashingStorageLibrary lib,
                    @Exclusive @Cached("createBinaryProfile()") ConditionProfile gotState) {
        HashingStorage result = generalize(lib);
        if (gotState.profile(state != null)) {
            return lib.setItemWithState(result, key, value, state);
        } else {
            return lib.setItem(result, key, value);
        }
    }

    @ExportMessage
    HashingStorage delItemWithState(Object key, ThreadState state,
                    @CachedLibrary(limit = "1") HashingStorageLibrary lib,
                    @Exclusive @Cached("createBinaryProfile()") ConditionProfile gotState) {
        HashingStorage result = generalize(lib);
        if (gotState.profile(state != null)) {
            return lib.delItemWithState(result, key, state);
        } else {
            return lib.delItem(result, key);
        }
    }

    private HashingStorage generalize(HashingStorageLibrary lib) {
        HashingStorage result = EconomicMapStorage.create(length());
        result = lib.addAllToOther(this, result);
        return result;
    }

    @Override
    @ExportMessage
    public Object forEachUntyped(ForEachNode<Object> node, Object arg) {
        bailout();
        Object result = arg;
        for (FrameSlot slot : frame.getFrameDescriptor().getSlots()) {
            Object value = getValue(slot);
            if (value != null) {
                result = node.execute(slot.getIdentifier(), result);
            }
        }
        return result;
    }

    private static void bailout() {
        CompilerDirectives.bailout("Generic loop over frame storage cannot be compiled");
    }

    @ExportMessage
    static class AddAllToOther {
        protected static FrameSlot[] getSlots(FrameDescriptor desc) {
            return desc.getSlots().toArray(new FrameSlot[0]);
        }

        @Specialization(guards = {"desc == self.frame.getFrameDescriptor()"}, limit = "1", assumptions = "desc.getVersion()")
        @ExplodeLoop
        static HashingStorage cached(LocalsStorage self, HashingStorage other,
                        @CachedLibrary(limit = "2") HashingStorageLibrary lib,
                        @Exclusive @SuppressWarnings("unused") @Cached("self.frame.getFrameDescriptor()") FrameDescriptor desc,
                        @Exclusive @Cached(value = "getSlots(desc)", dimensions = 1) FrameSlot[] slots) {
            HashingStorage result = other;
            for (int i = 0; i < slots.length; i++) {
                FrameSlot slot = slots[i];
                Object value = self.getValue(slot);
                if (value != null) {
                    result = lib.setItem(result, slot.getIdentifier(), value);
                }
            }
            return result;
        }

        @Specialization(replaces = "cached")
        static HashingStorage generic(LocalsStorage self, HashingStorage other,
                        @CachedLibrary(limit = "2") HashingStorageLibrary lib) {
            bailout();
            HashingStorage result = other;
            FrameSlot[] slots = getSlots(self.frame.getFrameDescriptor());
            for (int i = 0; i < slots.length; i++) {
                FrameSlot slot = slots[i];
                Object value = self.getValue(slot);
                if (value != null) {
                    result = lib.setItem(result, slot.getIdentifier(), value);
                }
            }
            return result;
        }
    }

    @Override
    @ExportMessage
    public HashingStorage clear() {
        return EconomicMapStorage.create();
    }

    @Override
    @ExportMessage
    public HashingStorage copy() {
        return new LocalsStorage(frame);
    }

    @Override
    @ExportMessage
    public HashingStorageIterable<Object> keys() {
        return new HashingStorageIterable<>(new LocalsIterator(frame));
    }

    @ExportMessage
    @Override
    public HashingStorageIterable<Object> reverseKeys() {
        return new HashingStorageIterable<>(new ReverseLocalsIterator(frame));
    }

    private abstract static class AbstractLocalsIterator implements Iterator<Object> {
        protected final MaterializedFrame frame;
        protected FrameSlot nextFrameSlot = null;

        AbstractLocalsIterator(MaterializedFrame frame) {
            this.frame = frame;
        }

        protected abstract boolean loadNext();

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
        public Object next() {
            return nextSlot().getIdentifier();
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

    }

    private static final class LocalsIterator extends AbstractLocalsIterator {
        private Iterator<? extends FrameSlot> keys;

        LocalsIterator(MaterializedFrame frame) {
            super(frame);
        }

        @TruffleBoundary
        @Override
        protected boolean loadNext() {
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

    private static final class ReverseLocalsIterator extends AbstractLocalsIterator {
        private List<? extends FrameSlot> slots;
        private int index;

        ReverseLocalsIterator(MaterializedFrame frame) {
            super(frame);
        }

        @TruffleBoundary
        @Override
        protected boolean loadNext() {
            if (slots == null) {
                slots = frame.getFrameDescriptor().getSlots();
                index = slots.size() - 1;
            }
            while (index >= 0) {
                FrameSlot nextCandidate = slots.get(index--);
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
    }
}
