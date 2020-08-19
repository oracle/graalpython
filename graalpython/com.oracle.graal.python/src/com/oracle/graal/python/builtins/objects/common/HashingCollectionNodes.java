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

import static com.oracle.graal.python.runtime.exception.PythonErrorType.TypeError;

import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.common.HashingCollectionNodesFactory.GetDictStorageNodeGen;
import com.oracle.graal.python.builtins.objects.common.HashingCollectionNodesFactory.LenNodeGen;
import com.oracle.graal.python.builtins.objects.common.HashingCollectionNodesFactory.SetDictStorageNodeGen;
import com.oracle.graal.python.builtins.objects.common.HashingCollectionNodesFactory.SetItemNodeGen;
import com.oracle.graal.python.builtins.objects.dict.PDict;
import com.oracle.graal.python.builtins.objects.dict.PDictView;
import com.oracle.graal.python.builtins.objects.function.PArguments;
import com.oracle.graal.python.builtins.objects.mappingproxy.PMappingproxy;
import com.oracle.graal.python.builtins.objects.object.PythonObjectLibrary;
import com.oracle.graal.python.builtins.objects.set.PBaseSet;
import com.oracle.graal.python.builtins.objects.str.PString;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PGuards;
import com.oracle.graal.python.nodes.PNodeWithContext;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.SpecialMethodNames;
import com.oracle.graal.python.nodes.control.GetIteratorExpressionNode;
import com.oracle.graal.python.nodes.control.GetNextNode;
import com.oracle.graal.python.nodes.object.IsBuiltinClassProfile;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.profiles.ConditionProfile;

public abstract class HashingCollectionNodes {

    @GenerateUncached
    @ImportStatic(PGuards.class)
    public abstract static class LenNode extends PNodeWithContext {
        public abstract int execute(PHashingCollection c);

        @Specialization(limit = "4")
        static int getLen(PHashingCollection c,
                        @Cached GetDictStorageNode getStorage,
                        @CachedLibrary("getStorage.execute(c)") HashingStorageLibrary lib) {
            return lib.length(getStorage.execute(c));
        }

        public static LenNode create() {
            return LenNodeGen.create();
        }
    }

    @ImportStatic(PGuards.class)
    public abstract static class SetItemNode extends PNodeWithContext {
        public abstract void execute(VirtualFrame frame, PHashingCollection c, Object key, Object value);

        @Specialization(limit = "4")
        static void doSetItem(VirtualFrame frame, PHashingCollection c, Object key, Object value,
                        @Cached("createBinaryProfile()") ConditionProfile hasFrame,
                        @Cached GetDictStorageNode getStorage,
                        @Cached SetDictStorageNode setStorage,
                        @CachedLibrary("c.getDictStorage()") HashingStorageLibrary lib) {
            HashingStorage storage = getStorage.execute(c);
            storage = lib.setItemWithFrame(storage, key, value, hasFrame, frame);
            setStorage.execute(c, storage);
        }

        public static SetItemNode create() {
            return SetItemNodeGen.create();
        }
    }

    @ImportStatic({PGuards.class})
    @GenerateUncached
    public abstract static class GetDictStorageNode extends PNodeWithContext {

        public abstract HashingStorage execute(PHashingCollection c);

        @Specialization
        static HashingStorage get(PBaseSet c) {
            return c.getDictStorage();
        }

        @Specialization
        static HashingStorage get(PDict c) {
            return c.getDictStorage();
        }

        @Specialization
        static HashingStorage get(PMappingproxy c) {
            return c.getDictStorage();
        }

        public static GetDictStorageNode create() {
            return GetDictStorageNodeGen.create();
        }

        public static GetDictStorageNode getUncached() {
            return GetDictStorageNodeGen.getUncached();
        }
    }

    @ImportStatic({PGuards.class})
    @GenerateUncached
    public abstract static class SetDictStorageNode extends PNodeWithContext {

        public abstract void execute(PHashingCollection c, HashingStorage storage);

        @Specialization
        static void set(PBaseSet c, HashingStorage storage) {
            c.setDictStorage(storage);
        }

        @Specialization
        static void set(PDict c, HashingStorage storage) {
            c.setDictStorage(storage);
        }

        @Specialization
        static void set(PMappingproxy c, HashingStorage storage) {
            c.setDictStorage(storage);
        }

        public static SetDictStorageNode create() {
            return SetDictStorageNodeGen.create();
        }

        public static SetDictStorageNode getUncached() {
            return SetDictStorageNodeGen.getUncached();
        }
    }

    @ImportStatic({PGuards.class})
    public abstract static class SetValueHashingStorageNode extends PNodeWithContext {
        public abstract void execute(VirtualFrame frame, HashingStorage iterator, Object value);

        @Specialization
        static void doEconomicStorage(VirtualFrame frame, EconomicMapStorage map, Object value,
                        @CachedLibrary(limit = "1") PythonObjectLibrary lib,
                        @Cached.Exclusive @Cached("createBinaryProfile()") ConditionProfile findProfile,
                        @Cached.Exclusive @Cached("createBinaryProfile()") ConditionProfile gotState) {
            PArguments.ThreadState state = frame == null ? null : PArguments.getThreadState(frame);
            // We want to avoid calling __hash__() during map.put
            HashingStorageLibrary.HashingStorageIterable<EconomicMapStorage.DictKey> iter = map.dictKeys();
            for (EconomicMapStorage.DictKey key : iter) {
                map.setValue(key, value, lib, findProfile, gotState, state);
            }
        }

        @Specialization(guards = "!isEconomicMapStorage(map)", limit = "2")
        static void doGeneric(VirtualFrame frame, HashingStorage map, Object value,
                        @Cached("createBinaryProfile()") ConditionProfile hasFrame,
                        @CachedLibrary("map") HashingStorageLibrary lib) {
            HashingStorageLibrary.HashingStorageIterable<Object> iter = lib.keys(map);
            for (Object key : iter) {
                lib.setItemWithFrame(map, key, value, hasFrame, frame);
            }
        }

        protected static boolean isEconomicMapStorage(Object o) {
            return o instanceof EconomicMapStorage;
        }
    }

    @ImportStatic({PGuards.class})
    public abstract static class GetClonedHashingStorageNode extends PNodeWithContext {
        @Child private PRaiseNode raise;

        public abstract HashingStorage execute(VirtualFrame frame, Object iterator, Object value);

        public final HashingStorage doNoValue(VirtualFrame frame, Object iterator) {
            return execute(frame, iterator, PNone.NO_VALUE);
        }

        @Specialization(guards = "isNoValue(value)", limit = "1")
        static HashingStorage doHashingCollectionNoValue(@SuppressWarnings("unused") VirtualFrame frame, PHashingCollection other, @SuppressWarnings("unused") Object value,
                        @Cached.Shared("getStorage") @Cached GetDictStorageNode getStorage,
                        @CachedLibrary("getStorage.execute(other)") HashingStorageLibrary lib) {
            return lib.copy(getStorage.execute(other));
        }

        @Specialization(guards = "isNoValue(value)", limit = "1")
        static HashingStorage doPDictKeyViewNoValue(@SuppressWarnings("unused") VirtualFrame frame, PDictView.PDictKeysView other, Object value,
                        @Cached.Shared("getStorage") @Cached GetDictStorageNode getStorage,
                        @CachedLibrary("getStorage.execute(other.getWrappedDict())") HashingStorageLibrary lib) {
            return doHashingCollectionNoValue(frame, other.getWrappedDict(), value, getStorage, lib);
        }

        @Specialization(guards = "!isNoValue(value)", limit = "1")
        static HashingStorage doHashingCollection(@SuppressWarnings("unused") VirtualFrame frame, PHashingCollection other, Object value,
                        @Cached.Shared("getStorage") @Cached GetDictStorageNode getStorage,
                        @Cached SetValueHashingStorageNode setValue,
                        @CachedLibrary("getStorage.execute(other)") HashingStorageLibrary lib) {
            HashingStorage storage = lib.copy(getStorage.execute(other));
            setValue.execute(frame, storage, value);
            return storage;
        }

        @Specialization(guards = "!isNoValue(value)", limit = "1")
        static HashingStorage doPDictView(@SuppressWarnings("unused") VirtualFrame frame, PDictView.PDictKeysView other, Object value,
                        @Cached.Shared("getStorage") @Cached GetDictStorageNode getStorage,
                        @Cached SetValueHashingStorageNode setValue,
                        @CachedLibrary("getStorage.execute(other.getWrappedDict())") HashingStorageLibrary lib) {
            return doHashingCollection(frame, other.getWrappedDict(), value, getStorage, setValue, lib);
        }

        @Specialization
        static HashingStorage doString(VirtualFrame frame, String str, Object value,
                        @Cached("createBinaryProfile()") ConditionProfile hasFrame,
                        @CachedLibrary(limit = "1") HashingStorageLibrary lib) {
            HashingStorage storage = EconomicMapStorage.create(PString.length(str));
            Object val = value == PNone.NO_VALUE ? PNone.NONE : value;
            for (int i = 0; i < PString.length(str); i++) {
                String key = PString.valueOf(PString.charAt(str, i));
                lib.setItemWithFrame(storage, key, val, hasFrame, frame);
            }
            return storage;
        }

        @Specialization
        static HashingStorage doString(VirtualFrame frame, PString pstr, Object value,
                        @Cached("createBinaryProfile()") ConditionProfile hasFrame,
                        @CachedLibrary(limit = "1") HashingStorageLibrary lib) {
            return doString(frame, pstr.getValue(), value, hasFrame, lib);
        }

        @Specialization(guards = {"!isPHashingCollection(other)", "!isDictKeysView(other)", "!isString(other)"})
        static HashingStorage doIterable(VirtualFrame frame, Object other, Object value,
                        @Cached("create()") GetIteratorExpressionNode.GetIteratorNode getIteratorNode,
                        @Cached("create()") GetNextNode next,
                        @Cached("create()") IsBuiltinClassProfile errorProfile,
                        @Cached("createBinaryProfile()") ConditionProfile hasFrame,
                        @CachedLibrary(limit = "2") HashingStorageLibrary lib) {
            HashingStorage curStorage = EconomicMapStorage.create();
            Object iterator = getIteratorNode.executeWith(frame, other);
            Object val = value == PNone.NO_VALUE ? PNone.NONE : value;
            while (true) {
                Object key;
                try {
                    key = next.execute(frame, iterator);
                } catch (PException e) {
                    e.expectStopIteration(errorProfile);
                    return curStorage;
                }
                curStorage = lib.setItemWithFrame(curStorage, key, val, hasFrame, frame);
            }
        }

        @Fallback
        HashingStorage fail(@SuppressWarnings("unused") VirtualFrame frame, Object other, @SuppressWarnings("unused") Object value) {
            if (raise == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                raise = insert(PRaiseNode.create());
            }
            throw raise.raise(TypeError, ErrorMessages.OBJ_NOT_ITERABLE, other);
        }
    }

    @ImportStatic({SpecialMethodNames.class, PGuards.class})
    public abstract static class GetHashingStorageNode extends PNodeWithContext {

        public abstract HashingStorage execute(VirtualFrame frame, Object iterator);

        @Specialization
        static HashingStorage doHashingCollection(@SuppressWarnings("unused") VirtualFrame frame, PHashingCollection other,
                        @Cached GetDictStorageNode getStorage) {
            return getStorage.execute(other);
        }

        @Specialization
        static HashingStorage doPDictView(@SuppressWarnings("unused") VirtualFrame frame, PDictView.PDictKeysView other,
                        @Cached GetDictStorageNode getStorage) {
            return getStorage.execute(other.getWrappedDict());
        }

        @Specialization(guards = {"!isPHashingCollection(other)", "!isDictKeysView(other)"})
        static HashingStorage doGeneric(@SuppressWarnings("unused") VirtualFrame frame, Object other,
                        @Cached GetClonedHashingStorageNode getHashingStorageNode) {
            return getHashingStorageNode.doNoValue(frame, other);
        }
    }
}
