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

import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.objects.common.HashingStorageLibrary.InjectIntoNode;
import com.oracle.graal.python.builtins.objects.function.PArguments.ThreadState;
import com.oracle.graal.python.builtins.objects.object.PythonObjectLibrary;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.truffle.api.CompilerDirectives.ValueType;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Exclusive;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.api.nodes.ControlFlowException;
import com.oracle.truffle.api.profiles.ConditionProfile;

@ExportLibrary(HashingStorageLibrary.class)
public abstract class HashingStorage {
    @ValueType
    public static final class DictEntry {
        public final Object key;
        public final Object value;

        protected DictEntry(Object key, Object value) {
            this.key = key;
            this.value = value;
        }

        public Object getKey() {
            return key;
        }

        public Object getValue() {
            return value;
        }
    }

    @ExportMessage
    int length() {
        throw new AbstractMethodError("HashingStorage.length");
    }

    @SuppressWarnings({"unused", "static-method"})
    @ExportMessage
    Object getItemWithState(Object key, ThreadState state) {
        throw new AbstractMethodError("HashingStorage.getItem");
    }

    @SuppressWarnings({"unused", "static-method"})
    @ExportMessage
    HashingStorage setItemWithState(Object key, Object value, ThreadState state) {
        throw new AbstractMethodError("HashingStorage.setItemWithState");
    }

    @SuppressWarnings({"unused", "static-method"})
    @ExportMessage
    HashingStorage delItemWithState(Object key, ThreadState state) {
        throw new AbstractMethodError("HashingStorage.delItemWithState");
    }

    @SuppressWarnings({"unused", "static-method"})
    @ExportMessage
    HashingStorage[] injectInto(HashingStorage[] firstValue, InjectIntoNode node) {
        throw new AbstractMethodError("HashingStorage.injectInto");
    }

    @SuppressWarnings({"unused", "static-method"})
    @ExportMessage
    HashingStorage clear() {
        throw new AbstractMethodError("HashingStorage.clear");
    }

    @SuppressWarnings({"unused", "static-method"})
    @ExportMessage
    HashingStorage copy() {
        throw new AbstractMethodError("HashingStorage.copy");
    }

    @SuppressWarnings({"unused", "static-method"})
    @ExportMessage
    Iterator<Object> keys() {
        throw new AbstractMethodError("HashingStorage.keys");
    }

    @GenerateUncached
    protected abstract static class AddToOtherInjectNode extends InjectIntoNode {
        @Specialization
        HashingStorage[] doit(HashingStorage[] accumulator, Object key,
                        @CachedLibrary(limit = "2") HashingStorageLibrary lib) {
            HashingStorage self = accumulator[0];
            HashingStorage other = accumulator[1];
            return new HashingStorage[]{self, lib.setItem(other, key, lib.getItem(self, key))};
        }
    }

    @ExportMessage
    public HashingStorage addAllToOther(HashingStorage other,
                    @CachedLibrary(limit = "1") HashingStorageLibrary libSelf,
                    @Cached AddToOtherInjectNode injectNode) {
        return libSelf.injectInto(this, new HashingStorage[]{this, other}, injectNode)[1];
    }

    @ExportMessage
    public boolean equalsWithState(HashingStorage other, ThreadState state,
                    @CachedLibrary(limit = "2") HashingStorageLibrary lib,
                    @Exclusive @Cached("createBinaryProfile()") ConditionProfile gotState) {
        if (this == other) {
            return true;
        }
        if (gotState.profile(state != null)) {
            if (lib.lengthWithState(this, state) == lib.lengthWithState(other, state)) {
                return lib.compareEntriesWithState(this, other, state) == 0;
            }
        } else {
            if (lib.length(this) == lib.length(other)) {
                return lib.compareEntries(this, other) == 0;
            }
        }
        return false;

    }

    @GenerateUncached
    protected abstract static class HasKeyNodeForSubsetKeys extends InjectIntoNode {
        @Specialization
        HashingStorage[] doit(HashingStorage[] other, Object key,
                        @CachedLibrary(limit = "1") HashingStorageLibrary lib) {
            if (!lib.hasKey(other[0], key)) {
                throw AbortIteration.INSTANCE;
            }
            return other;
        }
    }

    private static final class AbortIteration extends ControlFlowException {
        private static final long serialVersionUID = 1L;
        private static final AbortIteration INSTANCE = new AbortIteration();
    }

    @ExportMessage
    public int compareKeys(HashingStorage other,
                    @CachedLibrary(limit = "2") HashingStorageLibrary lib,
                    @Cached HasKeyNodeForSubsetKeys hasKeyNode) {
        if (this == other) {
            return 0;
        }
        int otherLen = lib.length(other);
        int selfLen = lib.length(this);
        if (selfLen > otherLen) {
            return 1;
        }
        try {
            lib.injectInto(this, new HashingStorage[]{other}, hasKeyNode);
        } catch (AbortIteration e) {
            return 1;
        }
        if (selfLen == otherLen) {
            return 0;
        } else {
            return -1;
        }
    }

    @GenerateUncached
    protected abstract static class TestKeyValueEqual extends InjectIntoNode {
        @Specialization
        HashingStorage[] doit(HashingStorage[] accumulator, Object key,
                        @Cached PRaiseNode raise,
                        @CachedLibrary(limit = "3") PythonObjectLibrary hashLib,
                        @CachedLibrary(limit = "2") HashingStorageLibrary lib) {
            HashingStorage self = accumulator[0];
            HashingStorage other = accumulator[1];
            Object otherValue = lib.getItem(other, key);
            if (otherValue == null) {
                throw AbortIteration.INSTANCE;
            }
            Object selfValue = lib.getItem(self, key);
            if (selfValue == null) {
                throw raise.raise(PythonBuiltinClassType.RuntimeError, "dictionary changed during comparison operation");
            }
            if (hashLib.equals(selfValue, otherValue, hashLib)) {
                return new HashingStorage[]{self, other};
            } else {
                throw AbortIteration.INSTANCE;
            }
        }
    }

    @ExportMessage
    public int compareEntriesWithState(HashingStorage other, ThreadState state,
                    @CachedLibrary(limit = "2") HashingStorageLibrary lib,
                    @Cached TestKeyValueEqual testNode,
                    @Exclusive @Cached("createBinaryProfile()") ConditionProfile gotState) {
        if (this == other) {
            return 0;
        }
        int otherLen, selfLen;
        if (gotState.profile(state != null)) {
            otherLen = lib.lengthWithState(other, state);
            selfLen = lib.lengthWithState(this, state);
        } else {
            otherLen = lib.length(other);
            selfLen = lib.length(this);
        }
        if (selfLen > otherLen) {
            return 1;
        }
        try {
            lib.injectInto(this, new HashingStorage[]{this, other}, testNode);
        } catch (AbortIteration e) {
            return 1;
        }
        if (selfLen == otherLen) {
            return 0;
        } else {
            return -1;
        }
    }

    @GenerateUncached
    protected abstract static class IntersectInjectionNode extends InjectIntoNode {
        @Specialization
        HashingStorage[] doit(HashingStorage[] accumulator, Object key,
                        @CachedLibrary(limit = "2") HashingStorageLibrary lib) {
            HashingStorage other = accumulator[0];
            HashingStorage output = accumulator[1];
            Object value = lib.getItem(other, key);
            if (value != null) {
                output = lib.setItem(output, key, value);
            }
            return new HashingStorage[]{other, output};
        }
    }

    @ExportMessage
    public HashingStorage intersect(HashingStorage other,
                    @CachedLibrary(limit = "1") HashingStorageLibrary libSelf,
                    @Cached IntersectInjectionNode injectNode) {
        HashingStorage newStore = EconomicMapStorage.create();
        return libSelf.injectInto(this, new HashingStorage[]{other, newStore}, injectNode)[1];
    }

    @GenerateUncached
    protected abstract static class DiffInjectNode extends InjectIntoNode {
        @Specialization
        HashingStorage[] doit(HashingStorage[] accumulator, Object key,
                        @CachedLibrary(limit = "3") HashingStorageLibrary lib) {
            HashingStorage self = accumulator[0];
            HashingStorage other = accumulator[1];
            HashingStorage output = accumulator[2];
            if (!lib.hasKey(other, key)) {
                output = lib.setItem(output, key, lib.getItem(self, key));
            }
            return new HashingStorage[]{self, other, output};
        }
    }

    @ExportMessage
    public HashingStorage xor(HashingStorage other,
                    @CachedLibrary(limit = "2") HashingStorageLibrary lib,
                    @Exclusive @Cached DiffInjectNode injectNode) {
        // could also be done with lib.union(lib.diff(self, other),
        // lib.diff(other, self)), but that uses one more iteration.
        HashingStorage newStore = EconomicMapStorage.create();
        // add all keys in self that are not in other
        newStore = lib.injectInto(this, new HashingStorage[]{this, other, newStore}, injectNode)[2];
        // add all keys in other that are not in self
        return lib.injectInto(other, new HashingStorage[]{other, this, newStore}, injectNode)[2];
    }

    @ExportMessage
    public HashingStorage union(HashingStorage other,
                    @CachedLibrary(limit = "2") HashingStorageLibrary lib) {
        HashingStorage newStore = lib.copy(this);
        return lib.addAllToOther(other, newStore);
    }

    @ExportMessage
    public HashingStorage diff(HashingStorage other,
                    @CachedLibrary(limit = "1") HashingStorageLibrary libSelf,
                    @Exclusive @Cached DiffInjectNode diffNode) {
        HashingStorage newStore = EconomicMapStorage.create();
        return libSelf.injectInto(this, new HashingStorage[]{this, other, newStore}, diffNode)[2];
    }

    @ExportMessage
    public Iterator<Object> values(@CachedLibrary(limit = "1") HashingStorageLibrary lib) {
        return new ValuesIterator(this, lib);
    }

    private static final class ValuesIterator implements Iterator<Object> {
        private final Iterator<DictEntry> entriesIterator;

        ValuesIterator(HashingStorage self, HashingStorageLibrary lib) {
            this.entriesIterator = new EntriesIterator(self, lib);
        }

        public boolean hasNext() {
            return entriesIterator.hasNext();
        }

        public Object next() {
            return entriesIterator.next().getValue();
        }
    }

    @ExportMessage
    public Iterator<DictEntry> entries(@CachedLibrary(limit = "1") HashingStorageLibrary lib) {
        return new EntriesIterator(this, lib);
    }

    private static final class EntriesIterator implements Iterator<DictEntry> {
        private final Iterator<Object> keysIterator;
        private final HashingStorage self;
        private final HashingStorageLibrary lib;

        EntriesIterator(HashingStorage self, HashingStorageLibrary lib) {
            this.self = self;
            this.lib = lib;
            this.keysIterator = lib.keys(self);
        }

        public boolean hasNext() {
            return keysIterator.hasNext();
        }

        public DictEntry next() {
            Object key = keysIterator.next();
            Object value = lib.getItem(self, key);
            return new DictEntry(key, value);
        }
    }

    protected long getHash(Object key, PythonObjectLibrary lib) {
        return lib.hash(key);
    }

    protected long getHashWithState(Object key, PythonObjectLibrary lib, ThreadState state, ConditionProfile gotState) {
        if (gotState.profile(state == null)) {
            return getHash(key, lib);
        }
        return lib.hashWithState(key, state);
    }
}
