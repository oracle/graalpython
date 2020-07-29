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

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;

import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.common.HashingStorageLibrary.ForEachNode;
import com.oracle.graal.python.builtins.objects.common.HashingStorageLibrary.HashingStorageIterable;
import com.oracle.graal.python.builtins.objects.function.PArguments.ThreadState;
import com.oracle.graal.python.builtins.objects.getsetdescriptor.HiddenPythonKey;
import com.oracle.graal.python.builtins.objects.object.PythonObject;
import com.oracle.graal.python.builtins.objects.object.PythonObjectLibrary;
import com.oracle.graal.python.builtins.objects.str.PString;
import com.oracle.graal.python.nodes.PGuards;
import com.oracle.graal.python.nodes.attributes.ReadAttributeFromDynamicObjectNode;
import com.oracle.graal.python.nodes.attributes.WriteAttributeToDynamicObjectNode;
import com.oracle.graal.python.nodes.object.IsBuiltinClassProfile;
import com.oracle.graal.python.nodes.util.CastToJavaStringNode;
import com.oracle.graal.python.runtime.sequence.storage.MroSequenceStorage;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Exclusive;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.nodes.ExplodeLoop.LoopExplosionKind;
import com.oracle.truffle.api.object.DynamicObject;
import com.oracle.truffle.api.object.DynamicObjectLibrary;
import com.oracle.truffle.api.object.Shape;
import com.oracle.truffle.api.profiles.BranchProfile;
import com.oracle.truffle.api.profiles.ConditionProfile;

/**
 * This storage keeps a reference to the MRO when used for a type dict. Writing to this storage will
 * cause the appropriate <it>attribute final</it> assumptions to be invalidated.
 */
@ExportLibrary(HashingStorageLibrary.class)
public final class DynamicObjectStorage extends HashingStorage {
    public static final int SIZE_THRESHOLD = 100;

    private static final Shape EMPTY_SHAPE = PythonObject.freshShape();

    final DynamicObject store;
    private final MroSequenceStorage mro;

    static final class Store extends DynamicObject {
        public Store(Shape shape) {
            super(shape);
        }
    }

    public DynamicObjectStorage() {
        this(new Store(EMPTY_SHAPE), null);
    }

    public DynamicObjectStorage(DynamicObject store) {
        this(store, null);
    }

    public DynamicObjectStorage(DynamicObject store, MroSequenceStorage mro) {
        this.store = store;
        this.mro = mro;
    }

    protected static Object[] keyArray(DynamicObjectStorage self) {
        return DynamicObjectStorage.keyArray(self.store.getShape());
    }

    protected static Object[] keyArray(Shape shape) {
        List<Object> keyList = keyList(shape);
        return keyList.toArray(new Object[keyList.size()]);
    }

    protected static List<Object> keyList(Shape shape) {
        return filter(shape.getKeyList());
    }

    @TruffleBoundary
    private static List<Object> filter(List<Object> l) {
        ArrayList<Object> keyList = new ArrayList<>(l.size());
        Iterator<Object> it = l.iterator();
        while (it.hasNext()) {
            Object n = it.next();
            if (!(n instanceof HiddenPythonKey)) {
                keyList.add(n);
            }
        }
        return keyList;
    }

    @ExportMessage
    static class Length {

        @Specialization(guards = "cachedShape == self.store.getShape()", limit = "3")
        @ExplodeLoop
        static int cachedLen(DynamicObjectStorage self,
                        @SuppressWarnings("unused") @Cached("self.store.getShape()") Shape cachedShape,
                        @Cached(value = "keyArray(self)", dimensions = 1) Object[] keys,
                        @Cached ReadAttributeFromDynamicObjectNode readNode) {
            int len = 0;
            for (int i = 0; i < keys.length; i++) {
                Object key = keys[i];
                len = incrementLen(self, readNode, len, key);
            }
            return len;
        }

        @TruffleBoundary
        @Specialization(replaces = "cachedLen")
        static int length(DynamicObjectStorage self,
                        @Exclusive @Cached ReadAttributeFromDynamicObjectNode readNode) {
            return cachedLen(self, self.store.getShape(), keyArray(self), readNode);
        }

        private static boolean hasStringKey(DynamicObjectStorage self, String key, ReadAttributeFromDynamicObjectNode readNode) {
            return readNode.execute(self.store, key) != PNone.NO_VALUE;
        }

        private static int incrementLen(DynamicObjectStorage self, ReadAttributeFromDynamicObjectNode readNode, int len, Object key) {
            if (key instanceof String) {
                if (hasStringKey(self, (String) key, readNode)) {
                    return len + 1;
                }
            }
            return len;
        }
    }

    @SuppressWarnings("unused")
    @ExportMessage
    @ImportStatic(PGuards.class)
    static class GetItemWithState {
        @Specialization
        static Object string(DynamicObjectStorage self, String key, ThreadState state,
                        @Shared("readKey") @Cached ReadAttributeFromDynamicObjectNode readKey,
                        @Exclusive @Cached("createBinaryProfile()") ConditionProfile noValueProfile) {
            Object result = readKey.execute(self.store, key);
            return noValueProfile.profile(result == PNone.NO_VALUE) ? null : result;
        }

        @Specialization(guards = "isBuiltinString(key, profile)", limit = "1")
        static Object pstring(DynamicObjectStorage self, PString key, ThreadState state,
                        @Shared("castStr") @Cached CastToJavaStringNode castStr,
                        @Shared("readKey") @Cached ReadAttributeFromDynamicObjectNode readKey,
                        @Shared("builtinStringProfile") @Cached IsBuiltinClassProfile profile,
                        @Exclusive @Cached("createBinaryProfile()") ConditionProfile noValueProfile) {
            return string(self, castStr.execute(key), state, readKey, noValueProfile);
        }

        @Specialization(guards = {"cachedShape == self.store.getShape()", "!isBuiltinString(key, profile)"}, limit = "1")
        @ExplodeLoop(kind = LoopExplosionKind.FULL_UNROLL_UNTIL_RETURN)
        static Object notString(DynamicObjectStorage self, Object key, ThreadState state,
                        @Shared("readKey") @Cached ReadAttributeFromDynamicObjectNode readKey,
                        @Exclusive @Cached("self.store.getShape()") Shape cachedShape,
                        @Exclusive @Cached(value = "keyArray(cachedShape)", dimensions = 1) Object[] keyList,
                        @Shared("builtinStringProfile") @Cached IsBuiltinClassProfile profile,
                        @CachedLibrary(limit = "2") PythonObjectLibrary lib,
                        @Exclusive @Cached("createBinaryProfile()") ConditionProfile gotState,
                        @Exclusive @Cached("createBinaryProfile()") ConditionProfile noValueProfile) {
            long hash = self.getHashWithState(key, lib, state, gotState);
            for (int i = 0; i < keyList.length; i++) {
                Object currentKey = keyList[i];
                if (currentKey instanceof String) {
                    if (gotState.profile(state != null)) {
                        long keyHash = lib.hashWithState(currentKey, state);
                        if (keyHash == hash && lib.equalsWithState(key, currentKey, lib, state)) {
                            return string(self, (String) currentKey, state, readKey, noValueProfile);
                        }
                    } else {
                        long keyHash = lib.hash(currentKey);
                        if (keyHash == hash && lib.equals(key, currentKey, lib)) {
                            return string(self, (String) currentKey, null, readKey, noValueProfile);
                        }
                    }
                }
            }
            return null;
        }

        @Specialization(guards = "!isBuiltinString(key, profile)", replaces = "notString", limit = "1")
        static Object notStringLoop(DynamicObjectStorage self, Object key, ThreadState state,
                        @Shared("readKey") @Cached ReadAttributeFromDynamicObjectNode readKey,
                        @Shared("builtinStringProfile") @Cached IsBuiltinClassProfile profile,
                        @CachedLibrary(limit = "2") PythonObjectLibrary lib,
                        @Exclusive @Cached("createBinaryProfile()") ConditionProfile gotState,
                        @Exclusive @Cached("createBinaryProfile()") ConditionProfile noValueProfile) {
            long hash = self.getHashWithState(key, lib, state, gotState);
            Iterator<Object> keys = self.store.getShape().getKeys().iterator();
            while (hasNext(keys)) {
                Object currentKey = getNext(keys);
                if (currentKey instanceof String) {
                    long keyHash;
                    if (gotState.profile(state != null)) {
                        keyHash = lib.hashWithState(currentKey, state);
                        if (keyHash == hash && lib.equalsWithState(key, currentKey, lib, state)) {
                            return string(self, (String) currentKey, state, readKey, noValueProfile);
                        }
                    } else {
                        keyHash = lib.hash(currentKey);
                        if (keyHash == hash && lib.equals(key, currentKey, lib)) {
                            return string(self, (String) currentKey, null, readKey, noValueProfile);
                        }
                    }
                }
            }
            return null;
        }

        @TruffleBoundary
        private static Object getNext(Iterator<Object> keys) {
            return keys.next();
        }

        @TruffleBoundary
        private static boolean hasNext(Iterator<Object> keys) {
            return keys.hasNext();
        }
    }

    private static void invalidateAttributeInMROFinalAssumptions(MroSequenceStorage mro, String name, BranchProfile profile) {
        if (mro != null) {
            profile.enter();
            mro.invalidateAttributeInMROFinalAssumptions(name);
        }
    }

    @SuppressWarnings("unused")
    @ExportMessage
    @ImportStatic(PGuards.class)
    static class SetItemWithState {

        @Specialization
        static HashingStorage string(DynamicObjectStorage self, String key, Object value, ThreadState state,
                        @Shared("hasMroprofile") @Cached BranchProfile profile,
                        @Shared("setitemWrite") @Cached WriteAttributeToDynamicObjectNode writeNode) {
            writeNode.execute(self.store, key, value);
            invalidateAttributeInMROFinalAssumptions(self.mro, key, profile);
            return self;
        }

        @Specialization(guards = "isBuiltinString(key, profile)", limit = "1")
        static HashingStorage pstring(DynamicObjectStorage self, PString key, Object value, ThreadState state,
                        @Shared("castStr") @Cached CastToJavaStringNode castStr,
                        @Shared("hasMroprofile") @Cached BranchProfile hasMro,
                        @Shared("setitemWrite") @Cached WriteAttributeToDynamicObjectNode writeNode,
                        @Shared("builtinStringProfile") @Cached IsBuiltinClassProfile profile) {
            return string(self, castStr.execute(key), value, state, hasMro, writeNode);
        }

        // n.b: do not replace the other two specializations here, because that would make the
        // uncached version pretty useless
        @Specialization
        static HashingStorage generalize(DynamicObjectStorage self, Object key, Object value, ThreadState state,
                        @CachedLibrary(limit = "2") HashingStorageLibrary lib,
                        @Exclusive @Cached("createBinaryProfile()") ConditionProfile gotState) {
            if (gotState.profile(state != null)) {
                HashingStorage newStore = EconomicMapStorage.create(lib.lengthWithState(self, state));
                newStore = lib.addAllToOther(self, newStore);
                return lib.setItemWithState(newStore, key, value, state);
            } else {
                HashingStorage newStore = EconomicMapStorage.create(lib.length(self));
                newStore = lib.addAllToOther(self, newStore);
                return lib.setItem(newStore, key, value);
            }
        }
    }

    @ExportMessage
    public HashingStorage delItemWithState(Object key, ThreadState state,
                    @CachedLibrary("this") HashingStorageLibrary lib,
                    @Shared("hasMroprofile") @Cached BranchProfile hasMro,
                    @Exclusive @Cached WriteAttributeToDynamicObjectNode writeNode,
                    @Exclusive @Cached("createBinaryProfile()") ConditionProfile gotState) {
        // __hash__ call is done through hasKey, if necessary
        boolean hasKey;
        if (gotState.profile(state != null)) {
            hasKey = lib.hasKeyWithState(this, key, state);
        } else {
            hasKey = lib.hasKey(this, key);
        }
        if (hasKey) {
            // if we're here, key is either a String or a built-in PString
            String strKey = key instanceof String ? (String) key : ((PString) key).getValue();
            writeNode.execute(store, strKey, PNone.NO_VALUE);
            invalidateAttributeInMROFinalAssumptions(mro, strKey, hasMro);
        }
        return this;
    }

    @ExportMessage
    static class ForEachUntyped {
        @Specialization(guards = "cachedShape == self.store.getShape()", limit = "1")
        @ExplodeLoop
        static Object cachedLen(DynamicObjectStorage self, ForEachNode<Object> node, Object firstValue,
                        @Exclusive @SuppressWarnings("unused") @Cached("self.store.getShape()") Shape cachedShape,
                        @Cached(value = "keyArray(self)", dimensions = 1) Object[] keys,
                        @Shared("readNodeInject") @Cached ReadAttributeFromDynamicObjectNode readNode) {
            Object result = firstValue;
            for (int i = 0; i < keys.length; i++) {
                Object key = keys[i];
                result = runNode(self, key, result, readNode, node);
            }
            return result;
        }

        @Specialization(replaces = "cachedLen")
        static Object addAll(DynamicObjectStorage self, ForEachNode<Object> node, Object firstValue,
                        @Shared("readNodeInject") @Cached ReadAttributeFromDynamicObjectNode readNode) {
            return cachedLen(self, node, firstValue, self.store.getShape(), keyArray(self), readNode);
        }

        private static Object runNode(DynamicObjectStorage self, Object key, Object acc, ReadAttributeFromDynamicObjectNode readNode, ForEachNode<Object> node) {
            if (key instanceof String) {
                Object value = readNode.execute(self.store, key);
                if (value != PNone.NO_VALUE) {
                    return node.execute(key, acc);
                }
            }
            return acc;
        }
    }

    @ExportMessage
    public HashingStorage clear(@CachedLibrary(limit = "3") DynamicObjectLibrary dylib) {
        dylib.resetShape(store, EMPTY_SHAPE);
        return this;
    }

    @ExportMessage
    public HashingStorage copy(@CachedLibrary(limit = "3") DynamicObjectLibrary dylib) {
        DynamicObject copy = new Store(EMPTY_SHAPE);
        Object[] keys = dylib.getKeyArray(store);
        for (int i = 0; i < keys.length; i++) {
            dylib.put(copy, keys[i], dylib.getOrDefault(store, keys[i], PNone.NO_VALUE));
        }
        return new DynamicObjectStorage(copy);
    }

    @ExportMessage
    public HashingStorageIterable<Object> keys(
                    @Exclusive @Cached ReadAttributeFromDynamicObjectNode readNode) {
        return new HashingStorageIterable<>(new KeysIterator(store, readNode));
    }

    @ExportMessage
    public HashingStorageIterable<Object> reverseKeys(
                    @Exclusive @Cached ReadAttributeFromDynamicObjectNode readNode) {
        return new HashingStorageIterable<>(new ReverseKeysIterator(store, readNode));
    }

    private abstract static class AbstractKeysIterator implements Iterator<Object> {
        private final DynamicObject store;
        private final ReadAttributeFromDynamicObjectNode readNode;
        private Object next = null;

        public AbstractKeysIterator(DynamicObject store, ReadAttributeFromDynamicObjectNode readNode) {
            this.store = store;
            this.readNode = readNode;
        }

        protected abstract boolean hasNextKey();

        protected abstract Object nextKey();

        @TruffleBoundary
        @Override
        public boolean hasNext() {
            while (next == null && hasNextKey()) {
                Object key = nextKey();
                Object value = readNode.execute(store, key);
                if (value != PNone.NO_VALUE) {
                    next = key;
                }
            }
            return next != null;
        }

        @Override
        public Object next() {
            hasNext(); // find the next value
            if (next != null) {
                Object returnValue = next;
                next = null;
                return returnValue;
            } else {
                throw new NoSuchElementException();
            }
        }
    }

    private static final class KeysIterator extends AbstractKeysIterator {
        private final Iterator<Object> keyIter;

        public KeysIterator(DynamicObject store, ReadAttributeFromDynamicObjectNode readNode) {
            super(store, readNode);
            this.keyIter = keyList(store.getShape()).iterator();
        }

        @Override
        protected boolean hasNextKey() {
            return keyIter.hasNext();
        }

        @Override
        protected Object nextKey() {
            return keyIter.next();
        }
    }

    private static final class ReverseKeysIterator extends AbstractKeysIterator {
        private final List<Object> keyList;
        private int index;

        public ReverseKeysIterator(DynamicObject store, ReadAttributeFromDynamicObjectNode readNode) {
            super(store, readNode);
            keyList = keyList(store.getShape());
            this.index = keyList.size() - 1;
        }

        @Override
        protected boolean hasNextKey() {
            return index >= 0;
        }

        @Override
        protected Object nextKey() {
            return keyList.get(index--);
        }

    }

    @ExportMessage
    public HashingStorageIterable<DictEntry> entries(
                    @Exclusive @Cached ReadAttributeFromDynamicObjectNode readNode) {
        return new HashingStorageIterable<>(new EntriesIterator(store, readNode));
    }

    protected static final class EntriesIterator implements Iterator<DictEntry> {
        private final List<Object> keyList;
        private final DynamicObject store;
        private final ReadAttributeFromDynamicObjectNode readNode;
        private DictEntry next = null;
        private int state;
        private final int size;

        public EntriesIterator(DynamicObject store, ReadAttributeFromDynamicObjectNode readNode) {
            this.keyList = keyList(store.getShape());
            this.store = store;
            this.readNode = readNode;
            this.state = 0;
            this.size = this.keyList.size();
        }

        public int getState() {
            return state;
        }

        public void setState(int state) {
            this.state = state;
        }

        @TruffleBoundary
        private static Iterator<Object> getList(Shape shape) {
            return keyList(shape).iterator();
        }

        @TruffleBoundary
        public boolean hasNext() {
            while (next == null && state < size) {
                Object key = keyList.get(state++);
                Object value = readNode.execute(store, key);
                if (value != PNone.NO_VALUE) {
                    next = new DictEntry(key, value);
                }
            }
            return next != null;
        }

        public DictEntry next() {
            hasNext(); // find the next value
            if (next != null) {
                DictEntry returnValue = next;
                next = null;
                return returnValue;
            } else {
                throw new NoSuchElementException();
            }
        }
    }
}
