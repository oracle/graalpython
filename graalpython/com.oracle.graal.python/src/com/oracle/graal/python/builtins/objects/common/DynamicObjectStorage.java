/*
 * Copyright (c) 2018, 2024, Oracle and/or its affiliates. All rights reserved.
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

import java.util.Iterator;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodes.SpecializedSetStringKey;
import com.oracle.graal.python.builtins.objects.dict.PDict;
import com.oracle.graal.python.builtins.objects.object.PythonObject;
import com.oracle.graal.python.builtins.objects.str.PString;
import com.oracle.graal.python.lib.PyObjectHashNode;
import com.oracle.graal.python.lib.PyObjectRichCompareBool;
import com.oracle.graal.python.lib.PyUnicodeCheckExactNode;
import com.oracle.graal.python.nodes.HiddenAttr;
import com.oracle.graal.python.nodes.PGuards;
import com.oracle.graal.python.nodes.attributes.ReadAttributeFromDynamicObjectNode;
import com.oracle.graal.python.nodes.util.CastToTruffleStringNode;
import com.oracle.graal.python.runtime.sequence.storage.MroSequenceStorage;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.HostCompilerDirectives.InliningCutoff;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Exclusive;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.GenerateCached;
import com.oracle.truffle.api.dsl.GenerateInline;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.NeverDefault;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.nodes.ExplodeLoop.LoopExplosionKind;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.object.DynamicObject;
import com.oracle.truffle.api.object.DynamicObjectLibrary;
import com.oracle.truffle.api.object.Shape;
import com.oracle.truffle.api.profiles.InlinedBranchProfile;
import com.oracle.truffle.api.profiles.InlinedConditionProfile;
import com.oracle.truffle.api.strings.TruffleString;

/**
 * This storage keeps a reference to the MRO when used for a type dict. Writing to this storage will
 * cause the appropriate <it>attribute final</it> assumptions to be invalidated.
 */
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
        return shape.getKeyList().toArray();
    }

    @GenerateUncached
    // This is only used in HashingStorageNodes and there it is better to have an indirection to
    // save memory if the storage is not DOM
    @GenerateInline(false)
    @GenerateCached
    @ImportStatic(DynamicObjectStorage.class)
    public abstract static class LengthNode extends Node {

        public abstract int execute(DynamicObjectStorage storage);

        @Specialization(guards = {"cachedShape == self.store.getShape()", "keys.length < EXPLODE_LOOP_SIZE_LIMIT"}, limit = "2")
        @ExplodeLoop
        static int cachedLen(DynamicObjectStorage self,
                        @SuppressWarnings("unused") @Cached("self.store.getShape()") Shape cachedShape,
                        @Cached(value = "keyArray(self)", dimensions = 1) Object[] keys,
                        @Shared @Cached ReadAttributeFromDynamicObjectNode readNode) {
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
                        @Shared @Cached ReadAttributeFromDynamicObjectNode readNode) {
            int len = 0;
            for (Object key : keys) {
                len = incrementLen(self, readNode, len, key);
            }
            return len;
        }

        @Specialization(replaces = "cachedKeys")
        static int length(DynamicObjectStorage self,
                        @Shared @Cached ReadAttributeFromDynamicObjectNode readNode) {
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
    @GenerateInline
    @GenerateCached(false)
    abstract static class GetItemNode extends Node {
        /**
         * For builtin strings the {@code keyHash} value is ignored and can be garbage. If the
         * {@code keyHash} is equal to {@code -1} it will be computed for non-string keys.
         */
        public abstract Object execute(Frame frame, Node inliningTarget, DynamicObjectStorage self, Object key, long keyHash);

        @Specialization
        static Object string(Node inliningTarget, DynamicObjectStorage self, TruffleString key, @SuppressWarnings("unused") long keyHash,
                        @Shared("readKey") @Cached(inline = false) ReadAttributeFromDynamicObjectNode readKey,
                        @Exclusive @Cached InlinedConditionProfile noValueProfile) {
            Object result = readKey.execute(self.store, key);
            return noValueProfile.profile(inliningTarget, result == PNone.NO_VALUE) ? null : result;
        }

        @Specialization(guards = "isBuiltinString.execute(inliningTarget, key)", limit = "1")
        @InliningCutoff
        static Object pstring(Node inliningTarget, DynamicObjectStorage self, PString key, @SuppressWarnings("unused") long keyHash,
                        @SuppressWarnings("unused") @Cached PyUnicodeCheckExactNode isBuiltinString,
                        @Cached CastToTruffleStringNode castStr,
                        @Shared("readKey") @Cached(inline = false) ReadAttributeFromDynamicObjectNode readKey,
                        @Exclusive @Cached InlinedConditionProfile noValueProfile) {
            return string(inliningTarget, self, castStr.execute(inliningTarget, key), -1, readKey, noValueProfile);
        }

        @Fallback
        @InliningCutoff
        static Object nonStringKey(Frame frame, DynamicObjectStorage self, Object key, @SuppressWarnings("unused") long keyHash,
                        @Cached(inline = false) GetItemNoStringKeyNode getItemNoStringKeyNode) {
            return getItemNoStringKeyNode.execute(frame, self, key, keyHash);
        }

        @GenerateInline(false)
        @GenerateUncached
        @ImportStatic({PGuards.class, DynamicObjectStorage.class})
        abstract static class GetItemNoStringKeyNode extends Node {
            public abstract Object execute(Frame frame, DynamicObjectStorage self, Object key, long keyHash);

            @Specialization(guards = {"cachedShape == self.store.getShape()", "keyList.length < EXPLODE_LOOP_SIZE_LIMIT"}, limit = "1")
            @ExplodeLoop(kind = LoopExplosionKind.FULL_UNROLL_UNTIL_RETURN)
            static Object notString(Frame frame, DynamicObjectStorage self, Object key, long hashIn,
                            @Bind("this") Node inliningTarget,
                            @Shared("readKey") @Cached ReadAttributeFromDynamicObjectNode readKey,
                            @Exclusive @Cached("self.store.getShape()") Shape cachedShape,
                            @Exclusive @Cached(value = "keyArray(cachedShape)", dimensions = 1) Object[] keyList,
                            @Shared("eqNode") @Cached PyObjectRichCompareBool.EqNode eqNode,
                            @Shared("hashNode") @Cached PyObjectHashNode hashNode,
                            @Shared("noValueProfile") @Cached InlinedConditionProfile noValueProfile) {
                long hash = hashIn == -1 ? hashNode.execute(frame, inliningTarget, key) : hashIn;
                for (Object currentKey : keyList) {
                    if (currentKey instanceof TruffleString) {
                        long keyHash = hashNode.execute(frame, inliningTarget, currentKey);
                        if (keyHash == hash && eqNode.compare(frame, inliningTarget, key, currentKey)) {
                            return string(inliningTarget, self, (TruffleString) currentKey, -1, readKey, noValueProfile);
                        }
                    }
                }
                return null;
            }

            @Specialization(replaces = "notString")
            static Object notStringLoop(Frame frame, DynamicObjectStorage self, Object key, long hashIn,
                            @Bind("this") Node inliningTarget,
                            @Shared("readKey") @Cached ReadAttributeFromDynamicObjectNode readKey,
                            @Shared("eqNode") @Cached PyObjectRichCompareBool.EqNode eqNode,
                            @Shared("hashNode") @Cached PyObjectHashNode hashNode,
                            @Shared("noValueProfile") @Cached InlinedConditionProfile noValueProfile) {
                long hash = hashIn == -1 ? hashNode.execute(frame, inliningTarget, key) : hashIn;
                Iterator<Object> keys = getKeysIterator(self.store.getShape());
                while (hasNext(keys)) {
                    Object currentKey = getNext(keys);
                    if (currentKey instanceof TruffleString) {
                        long keyHash = hashNode.execute(frame, inliningTarget, currentKey);
                        if (keyHash == hash && eqNode.compare(frame, inliningTarget, key, currentKey)) {
                            return string(inliningTarget, self, (TruffleString) currentKey, -1, readKey, noValueProfile);
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
    }

    private static void invalidateAttributeInMROFinalAssumptions(MroSequenceStorage mro, TruffleString name, Node inliningTarget, InlinedBranchProfile profile) {
        if (mro != null) {
            profile.enter(inliningTarget);
            mro.invalidateAttributeInMROFinalAssumptions(name);
        }
    }

    void setStringKey(TruffleString key, Object value, DynamicObjectLibrary dylib, Node inliningTarget, InlinedBranchProfile invalidateMroProfile) {
        dylib.put(store, key, assertNoJavaString(value));
        invalidateAttributeInMROFinalAssumption(key, inliningTarget, invalidateMroProfile);
    }

    void invalidateAttributeInMROFinalAssumption(TruffleString key, Node inliningTarget, InlinedBranchProfile invalidateMroProfile) {
        invalidateAttributeInMROFinalAssumptions(mro, key, inliningTarget, invalidateMroProfile);
    }

    boolean shouldTransitionOnPut() {
        // For now we do not use SIZE_THRESHOLD condition to transition storages that wrap
        // dictionaries retrieved via object's __dict__
        boolean notDunderDict = store instanceof Store;
        int propertyCount = store.getShape().getPropertyCount();
        return notDunderDict && propertyCount > SIZE_THRESHOLD;
    }

    @ImportStatic(PGuards.class)
    @GenerateUncached
    @GenerateInline
    @GenerateCached(false)
    abstract static class ClearNode extends Node {
        public abstract HashingStorage execute(Node node, HashingStorage receiver);

        @Specialization(guards = "!isPythonObject(receiver.getStore())")
        static HashingStorage clearPlain(DynamicObjectStorage receiver,
                        @CachedLibrary(limit = "3") DynamicObjectLibrary dylib) {
            dylib.resetShape(receiver.getStore(), PythonLanguage.get(dylib).getEmptyShape());
            return receiver;
        }

        @Specialization(guards = "isPythonObject(receiver.getStore())")
        static HashingStorage clearObjectBacked(Node inliningTarget, DynamicObjectStorage receiver,
                        @Cached HiddenAttr.ReadNode readHiddenAttrNode) {
            /*
             * We cannot use resetShape as that would lose hidden keys, such as CLASS or OBJ_ID.
             * Construct a new storage instead and set it as the object's __dict__'s storage.
             */
            DynamicObjectStorage newStorage = new DynamicObjectStorage(new Store(PythonLanguage.get(inliningTarget).getEmptyShape()));
            PythonObject owner = (PythonObject) receiver.getStore();
            PDict dict = (PDict) readHiddenAttrNode.execute(inliningTarget, owner, HiddenAttr.DICT, null);
            if (dict != null && dict.getDictStorage() == receiver) {
                dict.setDictStorage(newStorage);
            }
            return newStorage;
        }
    }

    @ImportStatic({PGuards.class, DynamicObjectStorage.class})
    @GenerateUncached
    @GenerateInline
    @GenerateCached(false)
    abstract static class Copy extends Node {
        abstract DynamicObjectStorage execute(Node node, DynamicObjectStorage receiver);

        @NeverDefault
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
                        @Cached(value = "keys.length") int cachedLength,
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

    @GenerateUncached
    @GenerateInline
    @GenerateCached(false)
    public abstract static class DynamicObjectStorageSetStringKey extends SpecializedSetStringKey {
        @Specialization
        static void doIt(Node inliningTarget, HashingStorage self, TruffleString key, Object value,
                        @CachedLibrary(limit = "3") DynamicObjectLibrary dylib,
                        @Cached InlinedBranchProfile invalidateMro) {
            ((DynamicObjectStorage) self).setStringKey(key, value, dylib, inliningTarget, invalidateMro);
        }
    }
}
