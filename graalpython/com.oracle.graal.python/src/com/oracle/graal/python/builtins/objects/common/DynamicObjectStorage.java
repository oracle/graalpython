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

import static com.oracle.graal.python.nodes.truffle.TruffleStringMigrationHelpers.assertNoJavaString;
import static com.oracle.graal.python.nodes.truffle.TruffleStringMigrationHelpers.isJavaString;
import static com.oracle.graal.python.util.PythonUtils.toTruffleStringUncached;

import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.common.HashingStorageLibrary.ForEachNode;
import com.oracle.graal.python.builtins.objects.common.HashingStorageLibrary.HashingStorageIterable;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodes.SpecializedSetStringKey;
import com.oracle.graal.python.builtins.objects.dict.PDict;
import com.oracle.graal.python.builtins.objects.object.PythonObject;
import com.oracle.graal.python.builtins.objects.str.PString;
import com.oracle.graal.python.lib.PyObjectHashNode;
import com.oracle.graal.python.lib.PyObjectRichCompareBool;
import com.oracle.graal.python.nodes.PGuards;
import com.oracle.graal.python.nodes.attributes.ReadAttributeFromDynamicObjectNode;
import com.oracle.graal.python.nodes.object.IsBuiltinClassProfile;
import com.oracle.graal.python.nodes.util.CastToTruffleStringNode;
import com.oracle.graal.python.runtime.sequence.storage.MroSequenceStorage;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Exclusive;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.nodes.ExplodeLoop.LoopExplosionKind;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.object.DynamicObject;
import com.oracle.truffle.api.object.DynamicObjectLibrary;
import com.oracle.truffle.api.object.Shape;
import com.oracle.truffle.api.profiles.BranchProfile;
import com.oracle.truffle.api.profiles.ConditionProfile;
import com.oracle.truffle.api.strings.TruffleString;
import com.oracle.truffle.object.ShapeImpl;

/**
 * This storage keeps a reference to the MRO when used for a type dict. Writing to this storage will
 * cause the appropriate <it>attribute final</it> assumptions to be invalidated.
 */
@ExportLibrary(HashingStorageLibrary.class)
public final class DynamicObjectStorage extends HashingStorage {
    public static final int SIZE_THRESHOLD = 100;
    public static final int EXPLODE_LOOP_SIZE_LIMIT = 16;

    final DynamicObject store;
    private final MroSequenceStorage mro;

    static final class Store extends DynamicObject {
        public Store(Shape shape) {
            super(shape);
        }
    }

    public DynamicObjectStorage(PythonLanguage lang) {
        this(new Store(lang.getEmptyShape()), null);
    }

    public DynamicObjectStorage(DynamicObject store) {
        this(store, null);
    }

    public DynamicObjectStorage(DynamicObject store, MroSequenceStorage mro) {
        this.store = store;
        this.mro = mro;
    }

    public Shape getStoreShape() {
        return store.getShape();
    }

    public DynamicObject getStore() {
        return store;
    }

    protected static Object[] keyArray(DynamicObjectStorage self) {
        return DynamicObjectStorage.keyArray(self.store.getShape());
    }

    protected static Object[] keyArray(Shape shape) {
        return ((ShapeImpl) shape).getKeyArray();
    }

    protected static List<Object> keyList(Shape shape) {
        return shape.getKeyList();
    }

    @GenerateUncached
    @ImportStatic(DynamicObjectStorage.class)
    public static abstract class LengthNode extends Node {

        public abstract int execute(DynamicObjectStorage storage);

        @Specialization(guards = {"cachedShape == self.store.getShape()", "keys.length < EXPLODE_LOOP_SIZE_LIMIT"}, limit = "2")
        @ExplodeLoop
        static int cachedLen(DynamicObjectStorage self,
                        @SuppressWarnings("unused") @Cached("self.store.getShape()") Shape cachedShape,
                        @Cached(value = "keyArray(self)", dimensions = 1) Object[] keys,
                        @Exclusive @Cached ReadAttributeFromDynamicObjectNode readNode) {
            int len = 0;
            for (Object key : keys) {
                len = incrementLen(self, readNode, len, key);
            }
            return len;
        }

        @Specialization(replaces = "cachedLen", guards = {"cachedShape == self.store.getShape()"}, limit = "3")
        static int cachedKeys(DynamicObjectStorage self,
                        @SuppressWarnings("unused") @Cached("self.store.getShape()") Shape cachedShape,
                        @Cached(value = "keyArray(self)", dimensions = 1) Object[] keys,
                        @Exclusive @Cached ReadAttributeFromDynamicObjectNode readNode) {
            int len = 0;
            for (Object key : keys) {
                len = incrementLen(self, readNode, len, key);
            }
            return len;
        }

        @Specialization(replaces = "cachedKeys")
        static int length(DynamicObjectStorage self,
                        @Cached ReadAttributeFromDynamicObjectNode readNode) {
            return cachedKeys(self, self.store.getShape(), keyArray(self), readNode);
        }

        private static boolean hasStringKey(DynamicObjectStorage self, TruffleString key, ReadAttributeFromDynamicObjectNode readNode) {
            return readNode.execute(self.store, key) != PNone.NO_VALUE;
        }

        private static int incrementLen(DynamicObjectStorage self, ReadAttributeFromDynamicObjectNode readNode, int len, Object key) {
            key = assertNoJavaString(key);
            if (key instanceof TruffleString) {
                if (hasStringKey(self, (TruffleString) key, readNode)) {
                    return len + 1;
                }
            }
            return len;
        }
    }

    @SuppressWarnings("unused")
    @ImportStatic({PGuards.class, DynamicObjectStorage.class})
    @GenerateUncached
    static abstract class GetItemNode extends Node {
        /**
         * For builtin strings the {@code keyHash} value is ignored and can be garbage. If the
         * {@code keyHash} is equal to {@code -1} it will be computed for non-string keys.
         */
        public abstract Object execute(Frame frame, DynamicObjectStorage self, Object key, long keyHash);

        @Specialization
        static Object string(DynamicObjectStorage self, TruffleString key, @SuppressWarnings("unused") long keyHash,
                        @Shared("readKey") @Cached ReadAttributeFromDynamicObjectNode readKey,
                        @Shared("noValueProfile") @Cached ConditionProfile noValueProfile) {
            Object result = readKey.execute(self.store, key);
            return noValueProfile.profile(result == PNone.NO_VALUE) ? null : result;
        }

        @Specialization(guards = "isBuiltinString(key, profile)", limit = "1")
        static Object pstring(DynamicObjectStorage self, PString key, @SuppressWarnings("unused") long keyHash,
                        @Cached CastToTruffleStringNode castStr,
                        @Shared("readKey") @Cached ReadAttributeFromDynamicObjectNode readKey,
                        @Shared("builtinStringProfile") @Cached IsBuiltinClassProfile profile,
                        @Shared("noValueProfile") @Cached ConditionProfile noValueProfile) {
            return string(self, castStr.execute(key), -1, readKey, noValueProfile);
        }

        @Specialization(guards = {"cachedShape == self.store.getShape()", "keyList.length < EXPLODE_LOOP_SIZE_LIMIT", "!isBuiltinString(key, profile)"}, limit = "1")
        @ExplodeLoop(kind = LoopExplosionKind.FULL_UNROLL_UNTIL_RETURN)
        static Object notString(Frame frame, DynamicObjectStorage self, Object key, long hashIn,
                        @Shared("readKey") @Cached ReadAttributeFromDynamicObjectNode readKey,
                        @Exclusive @Cached("self.store.getShape()") Shape cachedShape,
                        @Exclusive @Cached(value = "keyArray(cachedShape)", dimensions = 1) Object[] keyList,
                        @Shared("builtinStringProfile") @Cached IsBuiltinClassProfile profile,
                        @Shared("eqNode") @Cached PyObjectRichCompareBool.EqNode eqNode,
                        @Shared("hashNode") @Cached PyObjectHashNode hashNode,
                        @Shared("gotState") @Cached ConditionProfile gotState,
                        @Shared("noValueProfile") @Cached ConditionProfile noValueProfile) {
            long hash = hashIn == -1 ? hashNode.execute(frame, key) : hashIn;
            for (Object currentKey : keyList) {
                if (currentKey instanceof TruffleString) {
                    long keyHash = hashNode.execute(frame, currentKey);
                    if (keyHash == hash && eqNode.execute(frame, key, currentKey)) {
                        return string(self, (TruffleString) currentKey, -1, readKey, noValueProfile);
                    }
                }
            }
            return null;
        }

        @Specialization(guards = "!isBuiltinString(key, profile)", replaces = "notString", limit = "1")
        static Object notStringLoop(Frame frame, DynamicObjectStorage self, Object key, long hashIn,
                        @Shared("readKey") @Cached ReadAttributeFromDynamicObjectNode readKey,
                        @Shared("builtinStringProfile") @Cached IsBuiltinClassProfile profile,
                        @Shared("eqNode") @Cached PyObjectRichCompareBool.EqNode eqNode,
                        @Shared("hashNode") @Cached PyObjectHashNode hashNode,
                        @Shared("gotState") @Cached ConditionProfile gotState,
                        @Shared("noValueProfile") @Cached ConditionProfile noValueProfile) {
            long hash = hashIn == -1 ? hashNode.execute(frame, key) : hashIn;
            Iterator<Object> keys = getKeysIterator(self.store.getShape());
            while (hasNext(keys)) {
                Object currentKey = getNext(keys);
                if (currentKey instanceof TruffleString) {
                    long keyHash = hashNode.execute(frame, currentKey);
                    if (keyHash == hash && eqNode.execute(frame, key, currentKey)) {
                        return string(self, (TruffleString) currentKey, -1, readKey, noValueProfile);
                    }
                }
            }
            return null;
        }

        @TruffleBoundary
        private static Iterator<Object> getKeysIterator(Shape shape) {
            return shape.getKeys().iterator();
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

    private static void invalidateAttributeInMROFinalAssumptions(MroSequenceStorage mro, TruffleString name, BranchProfile profile) {
        if (mro != null) {
            profile.enter();
            mro.invalidateAttributeInMROFinalAssumptions(name);
        }
    }

    void setStringKey(TruffleString key, Object value, DynamicObjectLibrary dylib, BranchProfile invalidateMroProfile) {
        dylib.put(store, key, assertNoJavaString(value));
        invalidateAttributeInMROFinalAssumption(key, invalidateMroProfile);
    }

    void invalidateAttributeInMROFinalAssumption(TruffleString key, BranchProfile invalidateMroProfile) {
        invalidateAttributeInMROFinalAssumptions(mro, key, invalidateMroProfile);
    }

    boolean shouldTransitionOnPut() {
        // For now we do not use SIZE_THRESHOLD condition to transition storages that wrap
        // dictionaries retrieved via object's __dict__
        boolean notDunderDict = store instanceof Store;
        int propertyCount = store.getShape().getPropertyCount();
        return notDunderDict && propertyCount > SIZE_THRESHOLD;
    }

    @ExportMessage
    static class ForEachUntyped {
        @Specialization(guards = {"cachedShape == self.store.getShape()", "keys.length < EXPLODE_LOOP_SIZE_LIMIT"}, limit = "2")
        @ExplodeLoop
        static Object cachedLen(DynamicObjectStorage self, ForEachNode<Object> node, Object firstValue,
                        @Exclusive @SuppressWarnings("unused") @Cached("self.store.getShape()") Shape cachedShape,
                        @Cached(value = "keyArray(self)", dimensions = 1) Object[] keys,
                        @Exclusive @Cached ReadAttributeFromDynamicObjectNode readNode) {
            Object result = firstValue;
            for (int i = 0; i < keys.length; i++) {
                Object key = keys[i];
                result = runNode(self, key, result, readNode, node);
            }
            return result;
        }

        @Specialization(replaces = "cachedLen", guards = {"cachedShape == self.store.getShape()"}, limit = "3")
        static Object cachedKeys(DynamicObjectStorage self, ForEachNode<Object> node, Object firstValue,
                        @Exclusive @SuppressWarnings("unused") @Cached("self.store.getShape()") Shape cachedShape,
                        @Cached(value = "keyArray(self)", dimensions = 1) Object[] keys,
                        @Exclusive @Cached ReadAttributeFromDynamicObjectNode readNode) {
            Object result = firstValue;
            for (int i = 0; i < keys.length; i++) {
                Object key = keys[i];
                result = runNode(self, key, result, readNode, node);
            }
            return result;
        }

        @Specialization(replaces = "cachedKeys")
        static Object addAll(DynamicObjectStorage self, ForEachNode<Object> node, Object firstValue,
                        @Shared("readKey") @Cached ReadAttributeFromDynamicObjectNode readNode) {
            return cachedKeys(self, node, firstValue, self.store.getShape(), keyArray(self), readNode);
        }

        private static Object runNode(DynamicObjectStorage self, Object key, Object acc, ReadAttributeFromDynamicObjectNode readNode, ForEachNode<Object> node) {
            if (key instanceof TruffleString) {
                Object value = readNode.execute(self.store, key);
                if (value != PNone.NO_VALUE) {
                    return node.execute(key, acc);
                }
            }
            if (isJavaString(key)) {
                Object value = readNode.execute(self.store, toTruffleStringUncached((String) key));
                if (value != PNone.NO_VALUE) {
                    return node.execute(key, acc);
                }
            }
            return acc;
        }
    }

    @ImportStatic(PGuards.class)
    @GenerateUncached
    static abstract class ClearNode extends Node {
        public abstract HashingStorage execute(HashingStorage receiver);

        @Specialization(guards = "!isPythonObject(receiver.getStore())")
        static HashingStorage clearPlain(DynamicObjectStorage receiver,
                        @Shared("dylib") @CachedLibrary(limit = "3") DynamicObjectLibrary dylib) {
            dylib.resetShape(receiver.getStore(), PythonLanguage.get(dylib).getEmptyShape());
            return receiver;
        }

        @Specialization(guards = "isPythonObject(receiver.getStore())")
        static HashingStorage clearObjectBacked(DynamicObjectStorage receiver,
                        @Shared("dylib") @CachedLibrary(limit = "3") DynamicObjectLibrary dylib) {
            /*
             * We cannot use resetShape as that would lose hidden keys, such as CLASS or OBJ_ID.
             * Construct a new storage instead and set it as the object's __dict__'s storage.
             */
            DynamicObjectStorage newStorage = new DynamicObjectStorage(new Store(PythonLanguage.get(dylib).getEmptyShape()));
            PythonObject owner = (PythonObject) receiver.getStore();
            PDict dict = (PDict) dylib.getOrDefault(owner, PythonObject.DICT, null);
            if (dict != null && dict.getDictStorage() == receiver) {
                dict.setDictStorage(newStorage);
            }
            return newStorage;
        }
    }

    @ImportStatic({PGuards.class, DynamicObjectStorage.class})
    @GenerateUncached
    abstract static class Copy extends Node {
        abstract DynamicObjectStorage execute(DynamicObjectStorage receiver);

        static DynamicObjectLibrary[] createAccess(int length) {
            DynamicObjectLibrary[] result = new DynamicObjectLibrary[length];
            for (int i = 0; i < length; i++) {
                result[i] = DynamicObjectLibrary.getFactory().createDispatched(1);
            }
            return result;
        }

        @ExplodeLoop
        @Specialization(limit = "1", guards = {"cachedLength < EXPLODE_LOOP_SIZE_LIMIT", "keys.length == cachedLength"})
        public static DynamicObjectStorage copy(DynamicObjectStorage receiver,
                        @SuppressWarnings("unused") @Bind("receiver.store") DynamicObject store,
                        @SuppressWarnings("unused") @CachedLibrary("store") DynamicObjectLibrary dylib,
                        @Bind("dylib.getKeyArray(store)") Object[] keys,
                        @Cached("keys.length") int cachedLength,
                        @Cached("createAccess(cachedLength)") DynamicObjectLibrary[] readLib,
                        @Cached("createAccess(cachedLength)") DynamicObjectLibrary[] writeLib) {
            DynamicObject copy = new Store(PythonLanguage.get(dylib).getEmptyShape());
            for (int i = 0; i < cachedLength; i++) {
                writeLib[i].put(copy, keys[i], readLib[i].getOrDefault(receiver.store, keys[i], PNone.NO_VALUE));
            }
            return new DynamicObjectStorage(copy);
        }

        @Specialization(replaces = "copy")
        public static DynamicObjectStorage copyGeneric(DynamicObjectStorage receiver,
                        @CachedLibrary(limit = "3") DynamicObjectLibrary dylib) {
            DynamicObject copy = new Store(PythonLanguage.get(dylib).getEmptyShape());
            Object[] keys = dylib.getKeyArray(receiver.store);
            for (Object key : keys) {
                dylib.put(copy, key, dylib.getOrDefault(receiver.store, key, PNone.NO_VALUE));
            }
            return new DynamicObjectStorage(copy);
        }
    }

    @ExportMessage
    public HashingStorageIterable<Object> keys(
                    @Shared("readKey") @Cached ReadAttributeFromDynamicObjectNode readNode) {
        return new HashingStorageIterable<>(new KeysIterator(store, readNode));
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
            this.keyIter = getIter(keyList(store.getShape()));
        }

        @TruffleBoundary
        private static Iterator<Object> getIter(List<Object> keyList) {
            return keyList.iterator();
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
                    @Shared("readKey") @Cached ReadAttributeFromDynamicObjectNode readNode) {
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
            this.size = getListSize();
        }

        public int getState() {
            return state;
        }

        public void setState(int state) {
            this.state = state;
        }

        @TruffleBoundary
        private int getListSize() {
            return this.keyList.size();
        }

        @Override
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

        @Override
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

    @GenerateUncached
    public static abstract class DynamicObjectStorageSetStringKey extends SpecializedSetStringKey {
        @Specialization
        static void doIt(HashingStorage self, TruffleString key, Object value,
                        @CachedLibrary(limit = "3") DynamicObjectLibrary dylib,
                        @Cached BranchProfile invalidateMro) {
            ((DynamicObjectStorage) self).setStringKey(key, value, dylib, invalidateMro);
        }
    }
}
