/*
 * Copyright (c) 2018, 2022, Oracle and/or its affiliates. All rights reserved.
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

import static com.oracle.graal.python.util.PythonUtils.TS_ENCODING;

import java.util.Iterator;
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
import com.oracle.graal.python.nodes.util.CastToTruffleStringNode;
import com.oracle.graal.python.util.PythonUtils;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.dsl.Cached;
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
import com.oracle.truffle.api.strings.TruffleString;

@ExportLibrary(HashingStorageLibrary.class)
public final class LocalsStorage extends HashingStorage {
    /* This won't be the real (materialized) frame but a clone of it. */
    protected final MaterializedFrame frame;

    public LocalsStorage(FrameDescriptor fd) {
        this.frame = Truffle.getRuntime().createMaterializedFrame(PythonUtils.EMPTY_OBJECT_ARRAY, fd);
    }

    public LocalsStorage(MaterializedFrame frame) {
        this.frame = frame;
    }

    public MaterializedFrame getFrame() {
        return this.frame;
    }

    private Object getValue(int slot) {
        return getValue(this.frame, slot);
    }

    private static Object getValue(MaterializedFrame frame, int slot) {
        if (slot >= 0) {
            Object value = frame.getValue(slot);
            if (value instanceof PCell) {
                return ((PCell) value).getRef();
            }
            return value;
        }
        return null;
    }

    @ExportMessage
    @ImportStatic(PGuards.class)
    static class Length {
        @Specialization(guards = "desc == self.frame.getFrameDescriptor()", limit = "1")
        @ExplodeLoop
        static int getLengthCached(LocalsStorage self,
                        @Shared("desc") @Cached("self.frame.getFrameDescriptor()") FrameDescriptor desc) {
            int size = desc.getNumberOfSlots();
            for (int slot = 0; slot < desc.getNumberOfSlots(); slot++) {
                Object identifier = desc.getSlotName(slot);
                if (identifier != null || self.getValue(slot) == null) {
                    size--;
                }
            }
            return size;
        }

        @Specialization(replaces = "getLengthCached")
        static int getLength(LocalsStorage self) {
            FrameDescriptor desc = self.frame.getFrameDescriptor();
            int size = desc.getNumberOfSlots();
            for (int slot = 0; slot < desc.getNumberOfSlots(); slot++) {
                Object identifier = desc.getSlotName(slot);
                if (identifier != null || self.getValue(slot) == null) {
                    size--;
                }
            }
            return size;
        }

    }

    @SuppressWarnings("unused")
    @ExportMessage
    @ImportStatic(PGuards.class)
    static class GetItemWithState {
        @Specialization(guards = {"key == cachedKey", "desc == self.frame.getFrameDescriptor()"}, limit = "3")
        static Object getItemCached(LocalsStorage self, TruffleString key, ThreadState state,
                        @Cached("key") TruffleString cachedKey,
                        @Cached("self.frame.getFrameDescriptor()") FrameDescriptor desc,
                        @Cached("findSlot(desc, key)") int slot) {
            return self.getValue(slot);
        }

        @Specialization(replaces = "getItemCached")
        static Object string(LocalsStorage self, TruffleString key, ThreadState state) {
            int slot = findSlot(self.frame.getFrameDescriptor(), key);
            return self.getValue(slot);
        }

        @Specialization(guards = "isBuiltinString(key, profile)", limit = "1")
        static Object pstring(LocalsStorage self, PString key, ThreadState state,
                        @Shared("builtinProfile") @Cached IsBuiltinClassProfile profile,
                        @Cached CastToTruffleStringNode castToStringNode) {
            return string(self, castToStringNode.execute(key), state);
        }

        @Specialization(guards = "!isBuiltinString(key, profile)", limit = "1")
        static Object notString(LocalsStorage self, Object key, ThreadState state,
                        @Shared("builtinProfile") @Cached IsBuiltinClassProfile profile,
                        @Cached PyObjectRichCompareBool.EqNode eqNode,
                        @Cached PyObjectHashNode hashNode,
                        @Shared("gotState") @Cached ConditionProfile gotState) {
            CompilerDirectives.bailout("accessing locals storage with non-string keys is slow");
            VirtualFrame frame = gotState.profile(state == null) ? null : PArguments.frameForCall(state);
            long hash = hashNode.execute(frame, key);
            FrameDescriptor descriptor = self.frame.getFrameDescriptor();
            for (int slot = 0; slot < descriptor.getNumberOfSlots(); slot++) {
                Object currentKey = descriptor.getSlotName(slot);
                if (currentKey instanceof TruffleString) {
                    long keyHash = hashNode.execute(frame, currentKey);
                    if (keyHash == hash && eqNode.execute(frame, key, currentKey)) {
                        return self.getValue(slot);
                    }
                }
            }
            return null;
        }

        @TruffleBoundary
        static int findSlot(FrameDescriptor descriptor, TruffleString key) {
            for (int slot = 0; slot < descriptor.getNumberOfSlots(); slot++) {
                Object slotName = descriptor.getSlotName(slot);
                if (slotName instanceof TruffleString && key.equalsUncached((TruffleString) slotName, TS_ENCODING)) {
                    return slot;
                }
            }
            return -1;
        }
    }

    @ExportMessage
    HashingStorage setItemWithState(Object key, Object value, ThreadState state,
                    @CachedLibrary("this") HashingStorageLibrary thisLib,
                    @Shared("hlib") @CachedLibrary(limit = "2") HashingStorageLibrary lib,
                    @Shared("gotState") @Cached ConditionProfile gotState) {
        HashingStorage result = generalize(thisLib, key instanceof TruffleString, thisLib.length(this) + 1);
        if (gotState.profile(state != null)) {
            return lib.setItemWithState(result, key, value, state);
        } else {
            return lib.setItem(result, key, value);
        }
    }

    @ExportMessage
    HashingStorage delItemWithState(Object key, ThreadState state,
                    @CachedLibrary("this") HashingStorageLibrary thisLib,
                    @Shared("hlib") @CachedLibrary(limit = "2") HashingStorageLibrary lib,
                    @Shared("gotState") @Cached ConditionProfile gotState) {
        HashingStorage result = generalize(thisLib, true, thisLib.length(this) - 1);
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
    public Object forEachUntyped(ForEachNode<Object> node, Object arg) {
        Object result = arg;
        FrameDescriptor fd = this.frame.getFrameDescriptor();
        for (int slot = 0; slot < fd.getNumberOfSlots(); slot++) {
            Object identifier = fd.getSlotName(slot);
            if (identifier != null) {
                Object value = getValue(slot);
                if (value != null) {
                    result = node.execute(identifier, result);
                }
            }
        }
        return result;
    }

    @ExportMessage
    static class AddAllToOther {

        @Specialization(guards = {"desc == self.frame.getFrameDescriptor()"}, limit = "1")
        @ExplodeLoop
        static HashingStorage cached(LocalsStorage self, HashingStorage other,
                        @CachedLibrary(limit = "2") HashingStorageLibrary lib,
                        @Shared("desc") @SuppressWarnings("unused") @Cached("self.frame.getFrameDescriptor()") FrameDescriptor desc) {
            HashingStorage result = other;
            for (int slot = 0; slot < desc.getNumberOfSlots(); slot++) {
                Object identifier = desc.getSlotName(slot);
                if (identifier != null) {
                    Object value = self.getValue(slot);
                    if (value != null) {
                        result = lib.setItem(result, desc.getSlotName(slot), value);
                    }
                }
            }
            return result;
        }

        @Specialization(replaces = "cached")
        @TruffleBoundary
        static HashingStorage generic(LocalsStorage self, HashingStorage other,
                        @CachedLibrary(limit = "2") HashingStorageLibrary lib) {
            return cached(self, other, lib, self.frame.getFrameDescriptor());
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

    protected abstract static class AbstractLocalsIterator implements Iterator<Object> {
        protected final MaterializedFrame frame;
        protected final int size;
        protected int index;

        AbstractLocalsIterator(MaterializedFrame frame) {
            this.frame = frame;
            this.size = frame.getFrameDescriptor().getNumberOfSlots();
        }

        public final int getState() {
            return index;
        }

        public final void setState(int state) {
            index = state;
        }

        @Override
        @TruffleBoundary
        public final Object next() {
            if (hasNext()) {
                int slot = index;
                loadNext();
                return frame.getFrameDescriptor().getSlotName(slot);
            }
            throw new NoSuchElementException();
        }

        @TruffleBoundary
        protected final boolean loadNext() {
            while (nextIndex()) {
                Object identifier = frame.getFrameDescriptor().getSlotName(index);
                if (identifier != null) {
                    Object nextValue = getValue(this.frame, index);
                    if (nextValue != null) {
                        return true;
                    }
                }
            }
            return false;
        }

        protected abstract boolean nextIndex();
    }

    private static final class LocalsIterator extends AbstractLocalsIterator {

        LocalsIterator(MaterializedFrame frame) {
            super(frame);
            index = -1;
        }

        @Override
        public boolean hasNext() {
            if (index == -1) {
                return loadNext();
            }
            return index < size;
        }

        @Override
        protected boolean nextIndex() {
            index++;
            return index < size;
        }

    }

    private static final class ReverseLocalsIterator extends AbstractLocalsIterator {

        ReverseLocalsIterator(MaterializedFrame frame) {
            super(frame);
            index = size;
        }

        @Override
        public boolean hasNext() {
            if (index == size) {
                return loadNext();
            }
            return index >= 0;
        }

        @Override
        protected boolean nextIndex() {
            index--;
            return index >= 0;
        }

    }
}
