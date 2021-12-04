/*
 * Copyright (c) 2018, 2021, Oracle and/or its affiliates. All rights reserved.
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

import static com.oracle.graal.python.nodes.frame.FrameSlotIDs.isUserFrameSlot;

import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;

import com.oracle.graal.python.builtins.objects.cell.PCell;
import com.oracle.graal.python.builtins.objects.common.HashingStorageLibrary.ForEachNode;
import com.oracle.graal.python.builtins.objects.common.HashingStorageLibrary.HashingStorageIterable;
import com.oracle.graal.python.builtins.objects.dict.PDict;
import com.oracle.graal.python.builtins.objects.function.PArguments;
import com.oracle.graal.python.builtins.objects.function.PArguments.ThreadState;
import com.oracle.graal.python.builtins.objects.str.PString;
import com.oracle.graal.python.lib.PyObjectHashNode;
import com.oracle.graal.python.lib.PyObjectRichCompareBool;
import com.oracle.graal.python.nodes.PGuards;
import com.oracle.graal.python.nodes.object.IsBuiltinClassProfile;
import com.oracle.graal.python.util.PythonUtils;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Exclusive;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.MaterializedFrame;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.profiles.ConditionProfile;

@ExportLibrary(HashingStorageLibrary.class)
@SuppressWarnings("deprecation")    // new Frame API
public final class LocalsStorage extends HashingStorage {
    /* This won't be the real (materialized) frame but a clone of it. */
    protected final MaterializedFrame frame;
    private int len = -1;

    public LocalsStorage(FrameDescriptor fd) {
        this.frame = Truffle.getRuntime().createMaterializedFrame(PythonUtils.EMPTY_OBJECT_ARRAY, fd);
    }

    public LocalsStorage(MaterializedFrame frame) {
        this.frame = frame;
    }

    public MaterializedFrame getFrame() {
        return this.frame;
    }

    private Object getValue(com.oracle.truffle.api.frame.FrameSlot slot) {
        return getValue(this.frame, slot);
    }

    private static Object getValue(MaterializedFrame frame, com.oracle.truffle.api.frame.FrameSlot slot) {
        if (slot != null) {
            Object value = frame.getValue(slot);
            if (value instanceof PCell) {
                return ((PCell) value).getRef();
            }
            return value;
        }
        return null;
    }

    @ExportMessage
    @Override
    public int length() {
        if (this.len == -1) {
            CompilerDirectives.transferToInterpreter();
            calculateLength();
        }
        return this.len;
    }

    @TruffleBoundary
    private void calculateLength() {
        this.len = this.frame.getFrameDescriptor().getSize();
        for (com.oracle.truffle.api.frame.FrameSlot slot : this.frame.getFrameDescriptor().getSlots()) {
            Object identifier = slot.getIdentifier();
            if (!isUserFrameSlot(identifier) || getValue(frame, slot) == null) {
                this.len--;
            }
        }
    }

    @SuppressWarnings({"unused", "deprecation"})    // new frame API
    @ExportMessage
    @ImportStatic(PGuards.class)
    static class GetItemWithState {
        @Specialization(guards = {"key == cachedKey", "desc == self.frame.getFrameDescriptor()"}, limit = "3", assumptions = "desc.getVersion()")
        @SuppressWarnings("deprecation")    // new Frame API
        static Object getItemCached(LocalsStorage self, String key, ThreadState state,
                        @Cached("key") String cachedKey,
                        @Cached("self.frame.getFrameDescriptor()") FrameDescriptor desc,
                        @Cached("desc.findFrameSlot(key)") com.oracle.truffle.api.frame.FrameSlot slot) {
            return self.getValue(slot);
        }

        @Specialization(replaces = "getItemCached")
        @SuppressWarnings("deprecation")    // new Frame API
        static Object string(LocalsStorage self, String key, ThreadState state) {
            if (!isUserFrameSlot(key)) {
                return null;
            }
            com.oracle.truffle.api.frame.FrameSlot slot = findSlot(self, key);
            return self.getValue(slot);
        }

        @Specialization(guards = "isBuiltinString(key, profile)", limit = "1")
        static Object pstring(LocalsStorage self, PString key, ThreadState state,
                        @Shared("builtinProfile") @Cached IsBuiltinClassProfile profile) {
            return string(self, key.getValue(), state);
        }

        @Specialization(guards = "!isBuiltinString(key, profile)", limit = "1")
        @SuppressWarnings("deprecation")    // new Frame API
        static Object notString(LocalsStorage self, Object key, ThreadState state,
                        @Shared("builtinProfile") @Cached IsBuiltinClassProfile profile,
                        @Cached PyObjectRichCompareBool.EqNode eqNode,
                        @Cached PyObjectHashNode hashNode,
                        @Shared("gotState") @Cached ConditionProfile gotState) {
            CompilerDirectives.bailout("accessing locals storage with non-string keys is slow");
            VirtualFrame frame = gotState.profile(state == null) ? null : PArguments.frameForCall(state);
            long hash = hashNode.execute(frame, key);
            for (com.oracle.truffle.api.frame.FrameSlot slot : self.frame.getFrameDescriptor().getSlots()) {
                Object currentKey = slot.getIdentifier();
                if (currentKey instanceof String) {
                    long keyHash = hashNode.execute(frame, currentKey);
                    if (keyHash == hash && eqNode.execute(frame, key, currentKey)) {
                        return self.getValue(slot);
                    }
                }
            }
            return null;
        }

        @TruffleBoundary
        @SuppressWarnings("deprecation")    // new Frame API
        private static com.oracle.truffle.api.frame.FrameSlot findSlot(LocalsStorage self, Object key) {
            return self.frame.getFrameDescriptor().findFrameSlot(key);
        }
    }

    @ExportMessage
    HashingStorage setItemWithState(Object key, Object value, ThreadState state,
                    @Shared("hlib") @CachedLibrary(limit = "2") HashingStorageLibrary lib,
                    @Shared("gotState") @Cached ConditionProfile gotState) {
        HashingStorage result = generalize(lib, key instanceof String, length() + 1);
        if (gotState.profile(state != null)) {
            return lib.setItemWithState(result, key, value, state);
        } else {
            return lib.setItem(result, key, value);
        }
    }

    @ExportMessage
    HashingStorage delItemWithState(Object key, ThreadState state,
                    @Shared("hlib") @CachedLibrary(limit = "2") HashingStorageLibrary lib,
                    @Shared("gotState") @Cached ConditionProfile gotState) {
        HashingStorage result = generalize(lib, true, length() - 1);
        if (gotState.profile(state != null)) {
            return lib.delItemWithState(result, key, state);
        } else {
            return lib.delItem(result, key);
        }
    }

    private HashingStorage generalize(HashingStorageLibrary lib, boolean isStringKey, int expectedLength) {
        HashingStorage newStore = PDict.createNewStorage(isStringKey, expectedLength);
        newStore = lib.addAllToOther(this, newStore);
        return newStore;
    }

    @ExportMessage
    @TruffleBoundary
    @Override
    @SuppressWarnings("deprecation")    // new Frame API
    public Object forEachUntyped(ForEachNode<Object> node, Object arg) {
        Object result = arg;
        for (com.oracle.truffle.api.frame.FrameSlot slot : this.frame.getFrameDescriptor().getSlots()) {
            Object identifier = slot.getIdentifier();
            if (identifier instanceof String) {
                if (isUserFrameSlot(identifier)) {
                    Object value = getValue(slot);
                    if (value != null) {
                        result = node.execute(identifier, result);
                    }
                }
            }
        }
        return result;
    }

    @ExportMessage
    static class AddAllToOther {
        @SuppressWarnings("deprecation")    // new Frame API
        protected static com.oracle.truffle.api.frame.FrameSlot[] getSlots(FrameDescriptor desc) {
            return desc.getSlots().toArray(new com.oracle.truffle.api.frame.FrameSlot[0]);
        }

        @Specialization(guards = {"desc == self.frame.getFrameDescriptor()"}, limit = "1", assumptions = "desc.getVersion()")
        @ExplodeLoop
        @SuppressWarnings("deprecation")    // new Frame API
        static HashingStorage cached(LocalsStorage self, HashingStorage other,
                        @CachedLibrary(limit = "2") HashingStorageLibrary lib,
                        @Exclusive @SuppressWarnings("unused") @Cached("self.frame.getFrameDescriptor()") FrameDescriptor desc,
                        @Exclusive @Cached(value = "getSlots(desc)", dimensions = 1) com.oracle.truffle.api.frame.FrameSlot[] slots) {
            HashingStorage result = other;
            for (int i = 0; i < slots.length; i++) {
                com.oracle.truffle.api.frame.FrameSlot slot = slots[i];
                Object value = self.getValue(slot);
                if (value != null) {
                    result = lib.setItem(result, slot.getIdentifier(), value);
                }
            }
            return result;
        }

        @Specialization(replaces = "cached")
        @TruffleBoundary
        @SuppressWarnings("deprecation")    // new Frame API
        static HashingStorage generic(LocalsStorage self, HashingStorage other,
                        @CachedLibrary(limit = "2") HashingStorageLibrary lib) {
            HashingStorage result = other;
            com.oracle.truffle.api.frame.FrameSlot[] slots = getSlots(self.frame.getFrameDescriptor());
            for (int i = 0; i < slots.length; i++) {
                com.oracle.truffle.api.frame.FrameSlot slot = slots[i];
                Object value = self.getValue(slot);
                if (value != null) {
                    result = lib.setItem(result, slot.getIdentifier(), value);
                }
            }
            return result;
        }
    }

    @ExportMessage
    public static HashingStorage clear(@SuppressWarnings("unused") LocalsStorage self) {
        return EmptyStorage.INSTANCE;
    }

    @ExportMessage
    @Override
    public HashingStorage copy() {
        return new LocalsStorage(this.frame);
    }

    @ExportMessage
    @Override
    public HashingStorageIterable<Object> keys() {
        return new HashingStorageIterable<>(new LocalsIterator(this.frame));
    }

    @ExportMessage
    @Override
    public HashingStorageIterable<Object> reverseKeys() {
        return new HashingStorageIterable<>(new ReverseLocalsIterator(this.frame));
    }

    @SuppressWarnings("deprecation")    // new Frame API
    protected abstract static class AbstractLocalsIterator implements Iterator<Object> {
        protected List<? extends com.oracle.truffle.api.frame.FrameSlot> slots;
        protected final int size;
        protected int index;
        protected final MaterializedFrame frame;
        protected com.oracle.truffle.api.frame.FrameSlot nextFrameSlot = null;

        AbstractLocalsIterator(MaterializedFrame frame) {
            this.frame = frame;
            this.slots = getSlots(frame);
            this.size = getSize(frame);
            this.index = 0;
        }

        @TruffleBoundary
        private static List<? extends com.oracle.truffle.api.frame.FrameSlot> getSlots(MaterializedFrame frame) {
            return frame.getFrameDescriptor().getSlots();
        }

        @TruffleBoundary
        private static int getSize(MaterializedFrame frame) {
            return frame.getFrameDescriptor().getSize();
        }

        public int getState() {
            return this.index;
        }

        public void setState(int state) {
            this.index = state;
        }

        protected abstract boolean loadNext();

        @Override
        public boolean hasNext() {
            if (this.size == 0) {
                return false;
            }
            if (this.nextFrameSlot == null) {
                return loadNext();
            }
            return true;
        }

        @Override
        @TruffleBoundary
        public Object next() {
            return nextSlot().getIdentifier();
        }

        @TruffleBoundary
        public com.oracle.truffle.api.frame.FrameSlot nextSlot() {
            if (hasNext()) {
                assert this.nextFrameSlot != null;
                com.oracle.truffle.api.frame.FrameSlot value = this.nextFrameSlot;
                this.nextFrameSlot = null;
                return value;
            }
            throw new NoSuchElementException();
        }

    }

    private static final class LocalsIterator extends AbstractLocalsIterator {

        LocalsIterator(MaterializedFrame frame) {
            super(frame);
        }

        @TruffleBoundary
        @Override
        @SuppressWarnings("deprecation")    // new Frame API
        protected boolean loadNext() {
            while (this.index < this.size) {
                com.oracle.truffle.api.frame.FrameSlot nextCandidate = this.slots.get(this.index++);
                Object identifier = nextCandidate.getIdentifier();
                if (identifier instanceof String) {
                    if (isUserFrameSlot(identifier)) {
                        Object nextValue = getValue(this.frame, nextCandidate);
                        if (nextValue != null) {
                            this.nextFrameSlot = nextCandidate;
                            return true;
                        }
                    }
                }
            }
            return false;
        }
    }

    private static final class ReverseLocalsIterator extends AbstractLocalsIterator {

        ReverseLocalsIterator(MaterializedFrame frame) {
            super(frame);
            this.index = this.size - 1;
        }

        @TruffleBoundary
        @Override
        @SuppressWarnings("deprecation")    // new Frame API
        protected boolean loadNext() {
            while (this.index >= 0) {
                com.oracle.truffle.api.frame.FrameSlot nextCandidate = this.slots.get(this.index--);
                Object identifier = nextCandidate.getIdentifier();
                if (identifier instanceof String) {
                    if (isUserFrameSlot(identifier)) {
                        Object nextValue = getValue(this.frame, nextCandidate);
                        if (nextValue != null) {
                            this.nextFrameSlot = nextCandidate;
                            return true;
                        }
                    }
                }
            }
            return false;
        }
    }
}
