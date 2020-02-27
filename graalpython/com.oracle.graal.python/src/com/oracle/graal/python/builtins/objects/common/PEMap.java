/*
 * Copyright (c) 2020, Oracle and/or its affiliates. All rights reserved.
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
import java.util.Objects;
import java.util.function.BiFunction;

import org.graalvm.collections.MapCursor;

import com.oracle.graal.python.builtins.objects.common.EconomicMapStorage.DictKey;
import com.oracle.graal.python.builtins.objects.object.PythonObjectLibrary;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;

@SuppressWarnings("javadoc")
/**
 * Based on @see org.graalvm.collections.EconomicMapImpl
 */

final class PEMap implements Iterable<DictKey> {

    /**
     * Initial number of key/value pair entries that is allocated in the first entries array.
     */
    private static final int INITIAL_CAPACITY = 4;

    /**
     * Maximum number of entries that are moved linearly forward if a key is removed.
     */
    private static final int COMPRESS_IMMEDIATE_CAPACITY = 8;

    /**
     * Minimum number of key/value pair entries added when the entries array is increased in size.
     */
    private static final int MIN_CAPACITY_INCREASE = 8;

    /**
     * Number of entries above which a hash table is created.
     */
    private static final int HASH_THRESHOLD = 4;

    /**
     * Maximum number of entries allowed in the map.
     */
    private static final int MAX_ELEMENT_COUNT = Integer.MAX_VALUE >> 1;

    /**
     * Number of entries above which more than 1 byte is necessary for the hash index.
     */
    private static final int LARGE_HASH_THRESHOLD = ((1 << Byte.SIZE) << 1);

    /**
     * Number of entries above which more than 2 bytes are are necessary for the hash index.
     */
    private static final int VERY_LARGE_HASH_THRESHOLD = (LARGE_HASH_THRESHOLD << Byte.SIZE);

    /**
     * Total number of entries (actual entries plus deleted entries).
     */
    private int totalEntries;

    /**
     * Number of deleted entries.
     */
    private int deletedEntries;

    /**
     * Entries array with even indices storing keys and odd indices storing values.
     */
    private Object[] entries;

    /**
     * Hash array that is interpreted either as byte or short or int array depending on number of
     * map entries.
     */
    private byte[] hashArray;

    /**
     * Intercept method for debugging purposes.
     */
    private static PEMap intercept(PEMap map) {
        return map;
    }

    @TruffleBoundary
    public static PEMap create(boolean isSet) {
        return intercept(new PEMap(isSet));
    }

    @TruffleBoundary
    public static PEMap create(int initialCapacity, boolean isSet) {
        return intercept(new PEMap(initialCapacity, isSet));
    }

    private PEMap(boolean isSet) {
        this.isSet = isSet;
    }

    private PEMap(int initialCapacity, boolean isSet) {
        this(isSet);
        init(initialCapacity);
    }

    private void init(int size) {
        if (size > INITIAL_CAPACITY) {
            entries = new Object[size << 1];
        }
    }

    /**
     * Links the collisions. Needs to be immutable class for allowing efficient shallow copy from
     * other map on construction.
     */
    private static final class CollisionLink {

        CollisionLink(Object value, int next) {
            this.value = value;
            this.next = next;
        }

        final Object value;

        /**
         * Index plus one of the next entry in the collision link chain.
         */
        final int next;
    }

    @TruffleBoundary
    public Object get(DictKey key, PythonObjectLibrary keylib, PythonObjectLibrary otherlib) {
        Objects.requireNonNull(key);

        int index = find(key, keylib, otherlib);
        if (index != -1) {
            return getValue(index);
        }
        return null;
    }

    private int find(DictKey key, PythonObjectLibrary keylib, PythonObjectLibrary otherlib) {
        if (hasHashArray()) {
            return findHash(key, keylib, otherlib);
        } else {
            return findLinear(key, keylib, otherlib);
        }
    }

    private int findLinear(DictKey key, PythonObjectLibrary keylib, PythonObjectLibrary otherlib) {
        for (int i = 0; i < totalEntries; i++) {
            DictKey entryKey = getKey(i);
            if (entryKey != null && compareKeys(key, entryKey, keylib, otherlib)) {
                return i;
            }
        }
        return -1;
    }

    private static boolean compareKeys(DictKey key, DictKey other, PythonObjectLibrary keylib, PythonObjectLibrary otherlib) { //
        // Comparison as per CPython's dictobject.c#lookdict function. First
        // check if the keys are identical, then check if the hashes are the
        // same, and only if they are, also call the comparison function.
        if (key.value == other.value) {
            return true;
        }
        if (key.hash == other.hash) {
            if (keylib != null && otherlib != null) {
                return keylib.equals(key.value, other.value, otherlib);
            } else {
                return key.value.equals(other.value);
            }
        }
        return false;
    }

    private int findHash(DictKey key, PythonObjectLibrary keylib, PythonObjectLibrary otherlib) {
        int index = getHashArray(getHashIndex(key)) - 1;
        if (index != -1) {
            DictKey entryKey = getKey(index);
            if (compareKeys(key, entryKey, keylib, otherlib)) {
                return index;
            } else {
                Object entryValue = getRawValue(index);
                if (entryValue instanceof CollisionLink) {
                    return findWithCollision(key, (CollisionLink) entryValue, keylib, otherlib);
                }
            }
        }

        return -1;
    }

    private int findWithCollision(DictKey key, CollisionLink initialEntryValue, PythonObjectLibrary keylib, PythonObjectLibrary otherlib) {
        int index;
        DictKey entryKey;
        CollisionLink entryValue = initialEntryValue;
        while (true) {
            CollisionLink collisionLink = entryValue;
            index = collisionLink.next;
            entryKey = getKey(index);
            if (compareKeys(key, entryKey, keylib, otherlib)) {
                return index;
            } else {
                Object value = getRawValue(index);
                if (value instanceof CollisionLink) {
                    entryValue = (CollisionLink) getRawValue(index);
                } else {
                    return -1;
                }
            }
        }
    }

    private int getHashArray(int index) {
        if (entries.length < LARGE_HASH_THRESHOLD) {
            return (hashArray[index] & 0xFF);
        } else if (entries.length < VERY_LARGE_HASH_THRESHOLD) {
            int adjustedIndex = index << 1;
            return (hashArray[adjustedIndex] & 0xFF) | ((hashArray[adjustedIndex + 1] & 0xFF) << 8);
        } else {
            int adjustedIndex = index << 2;
            return (hashArray[adjustedIndex] & 0xFF) | ((hashArray[adjustedIndex + 1] & 0xFF) << 8) | ((hashArray[adjustedIndex + 2] & 0xFF) << 16) | ((hashArray[adjustedIndex + 3] & 0xFF) << 24);
        }
    }

    private void setHashArray(int index, int value) {
        if (entries.length < LARGE_HASH_THRESHOLD) {
            hashArray[index] = (byte) value;
        } else if (entries.length < VERY_LARGE_HASH_THRESHOLD) {
            int adjustedIndex = index << 1;
            hashArray[adjustedIndex] = (byte) value;
            hashArray[adjustedIndex + 1] = (byte) (value >> 8);
        } else {
            int adjustedIndex = index << 2;
            hashArray[adjustedIndex] = (byte) value;
            hashArray[adjustedIndex + 1] = (byte) (value >> 8);
            hashArray[adjustedIndex + 2] = (byte) (value >> 16);
            hashArray[adjustedIndex + 3] = (byte) (value >> 24);
        }
    }

    private int findAndRemoveHash(DictKey key, PythonObjectLibrary keylib, PythonObjectLibrary otherlib) {
        int hashIndex = getHashIndex(key);
        int index = getHashArray(hashIndex) - 1;
        if (index != -1) {
            DictKey entryKey = getKey(index);
            if (compareKeys(key, entryKey, keylib, otherlib)) {
                Object value = getRawValue(index);
                int nextIndex = -1;
                if (value instanceof CollisionLink) {
                    CollisionLink collisionLink = (CollisionLink) value;
                    nextIndex = collisionLink.next;
                }
                setHashArray(hashIndex, nextIndex + 1);
                return index;
            } else {
                Object entryValue = getRawValue(index);
                if (entryValue instanceof CollisionLink) {
                    return findAndRemoveWithCollision(key, (CollisionLink) entryValue, index, keylib, otherlib);
                }
            }
        }

        return -1;
    }

    private int findAndRemoveWithCollision(DictKey key, CollisionLink initialEntryValue, int initialIndexValue, PythonObjectLibrary keylib, PythonObjectLibrary otherlib) {
        int index;
        DictKey entryKey;
        CollisionLink entryValue = initialEntryValue;
        int lastIndex = initialIndexValue;
        while (true) {
            CollisionLink collisionLink = entryValue;
            index = collisionLink.next;
            entryKey = getKey(index);
            if (compareKeys(key, entryKey, keylib, otherlib)) {
                Object value = getRawValue(index);
                if (value instanceof CollisionLink) {
                    CollisionLink thisCollisionLink = (CollisionLink) value;
                    setRawValue(lastIndex, new CollisionLink(collisionLink.value, thisCollisionLink.next));
                } else {
                    setRawValue(lastIndex, collisionLink.value);
                }
                return index;
            } else {
                Object value = getRawValue(index);
                if (value instanceof CollisionLink) {
                    entryValue = (CollisionLink) getRawValue(index);
                    lastIndex = index;
                } else {
                    return -1;
                }
            }
        }
    }

    private int getHashIndex(DictKey key) {
        int hash = key.hashCode();
        hash = hash ^ (hash >>> 16);
        return hash & (getHashTableSize() - 1);
    }

    @TruffleBoundary
    public Object put(DictKey key, Object value, PythonObjectLibrary keylib, PythonObjectLibrary otherlib) {
        if (key == null) {
            throw new UnsupportedOperationException("null not supported as key!");
        }
        int index = find(key, keylib, otherlib);
        if (index != -1) {
            Object oldValue = getValue(index);
            setValue(index, value);
            return oldValue;
        }

        int nextEntryIndex = totalEntries;
        if (entries == null) {
            entries = new Object[INITIAL_CAPACITY << 1];
        } else if (entries.length == nextEntryIndex << 1) {
            grow();

            assert entries.length > totalEntries << 1;
            // Can change if grow is actually compressing.
            nextEntryIndex = totalEntries;
        }

        setKey(nextEntryIndex, key);
        setValue(nextEntryIndex, value);
        totalEntries++;

        if (hasHashArray()) {
            // Rehash on collision if hash table is more than three quarters full.
            boolean rehashOnCollision = (getHashTableSize() < (size() + (size() >> 1)));
            putHashEntry(key, nextEntryIndex, rehashOnCollision);
        } else if (totalEntries > getHashThreshold()) {
            createHash();
        }

        return null;
    }

    @TruffleBoundary
    public void putAll(PEMap other) {
        final PythonObjectLibrary lib = PythonObjectLibrary.getUncached();
        MapCursor<DictKey, Object> e = other.getEntries();
        while (e.advance()) {
            put(e.getKey(), e.getValue(), lib, lib);
        }
    }

    /**
     * Number of entries above which a hash table should be constructed.
     */
    private static int getHashThreshold() {
        return HASH_THRESHOLD;
    }

    private void grow() {
        int entriesLength = entries.length;
        int newSize = (entriesLength >> 1) + Math.max(MIN_CAPACITY_INCREASE, entriesLength >> 2);
        if (newSize > MAX_ELEMENT_COUNT) {
            throw new UnsupportedOperationException("map grown too large!");
        }
        Object[] newEntries = new Object[newSize << 1];
        System.arraycopy(entries, 0, newEntries, 0, entriesLength);
        entries = newEntries;
        if ((entriesLength < LARGE_HASH_THRESHOLD && newEntries.length >= LARGE_HASH_THRESHOLD) ||
                        (entriesLength < VERY_LARGE_HASH_THRESHOLD && newEntries.length > VERY_LARGE_HASH_THRESHOLD)) {
            // Rehash in order to change number of bits reserved for hash indices.
            createHash();
        }
    }

    /**
     * Compresses the graph if there is a large number of deleted entries and returns the translated
     * new next index.
     */
    private int maybeCompress(int nextIndex) {
        if (entries.length != INITIAL_CAPACITY << 1 && deletedEntries >= (totalEntries >> 1) + (totalEntries >> 2)) {
            return compressLarge(nextIndex);
        }
        return nextIndex;
    }

    /**
     * Compresses the graph and returns the translated new next index.
     */
    private int compressLarge(int nextIndex) {
        int size = INITIAL_CAPACITY;
        int remaining = totalEntries - deletedEntries;

        while (size <= remaining) {
            size += Math.max(MIN_CAPACITY_INCREASE, size >> 1);
        }

        Object[] newEntries = new Object[size << 1];
        int z = 0;
        int newNextIndex = remaining;
        for (int i = 0; i < totalEntries; ++i) {
            DictKey key = getKey(i);
            if (i == nextIndex) {
                newNextIndex = z;
            }
            if (key != null) {
                newEntries[z << 1] = key;
                newEntries[(z << 1) + 1] = getValue(i);
                z++;
            }
        }

        this.entries = newEntries;
        totalEntries = z;
        deletedEntries = 0;
        if (z <= getHashThreshold()) {
            this.hashArray = null;
        } else {
            createHash();
        }
        return newNextIndex;
    }

    private int getHashTableSize() {
        if (entries.length < LARGE_HASH_THRESHOLD) {
            return hashArray.length;
        } else if (entries.length < VERY_LARGE_HASH_THRESHOLD) {
            return hashArray.length >> 1;
        } else {
            return hashArray.length >> 2;
        }
    }

    private void createHash() {
        int entryCount = size();

        // Calculate smallest 2^n that is greater number of entries.
        int size = getHashThreshold();
        while (size <= entryCount) {
            size <<= 1;
        }

        // Give extra size to avoid collisions.
        size <<= 1;

        if (this.entries.length >= VERY_LARGE_HASH_THRESHOLD) {
            // Every entry has 4 bytes.
            size <<= 2;
        } else if (this.entries.length >= LARGE_HASH_THRESHOLD) {
            // Every entry has 2 bytes.
            size <<= 1;
        } else {
            // Entries are very small => give extra size to further reduce collisions.
            size <<= 1;
        }

        hashArray = new byte[size];
        for (int i = 0; i < totalEntries; i++) {
            DictKey entryKey = getKey(i);
            if (entryKey != null) {
                putHashEntry(entryKey, i, false);
            }
        }
    }

    private void putHashEntry(DictKey key, int entryIndex, boolean rehashOnCollision) {
        int hashIndex = getHashIndex(key);
        int oldIndex = getHashArray(hashIndex) - 1;
        if (oldIndex != -1 && rehashOnCollision) {
            this.createHash();
            return;
        }
        setHashArray(hashIndex, entryIndex + 1);
        Object value = getRawValue(entryIndex);
        if (oldIndex != -1) {
            assert entryIndex != oldIndex : "this cannot happend and would create an endless collision link cycle";
            if (value instanceof CollisionLink) {
                CollisionLink collisionLink = (CollisionLink) value;
                setRawValue(entryIndex, new CollisionLink(collisionLink.value, oldIndex));
            } else {
                setRawValue(entryIndex, new CollisionLink(getRawValue(entryIndex), oldIndex));
            }
        } else {
            if (value instanceof CollisionLink) {
                CollisionLink collisionLink = (CollisionLink) value;
                setRawValue(entryIndex, collisionLink.value);
            }
        }
    }

    public int size() {
        return totalEntries - deletedEntries;
    }

    @TruffleBoundary
    public boolean containsKey(DictKey key, PythonObjectLibrary keylib, PythonObjectLibrary otherlib) {
        return find(key, keylib, otherlib) != -1;
    }

    public void clear() {
        entries = null;
        hashArray = null;
        totalEntries = deletedEntries = 0;
    }

    private boolean hasHashArray() {
        return hashArray != null;
    }

    @TruffleBoundary
    public Object removeKey(DictKey key, PythonObjectLibrary keylib, PythonObjectLibrary otherlib) {
        if (key == null) {
            throw new UnsupportedOperationException("null not supported as key!");
        }
        int index;
        if (hasHashArray()) {
            index = this.findAndRemoveHash(key, keylib, otherlib);
        } else {
            index = this.findLinear(key, keylib, otherlib);
        }

        if (index != -1) {
            Object value = getValue(index);
            remove(index);
            return value;
        }
        return null;
    }

    /**
     * Removes the element at the specific index and returns the index of the next element. This can
     * be a different value if graph compression was triggered.
     */
    private int remove(int indexToRemove) {
        int index = indexToRemove;
        int entriesAfterIndex = totalEntries - index - 1;
        int result = index + 1;

        // Without hash array, compress immediately.
        if (entriesAfterIndex <= COMPRESS_IMMEDIATE_CAPACITY && !hasHashArray()) {
            while (index < totalEntries - 1) {
                setKey(index, getKey(index + 1));
                setRawValue(index, getRawValue(index + 1));
                index++;
            }
            result--;
        }

        setKey(index, null);
        setRawValue(index, null);
        if (index == totalEntries - 1) {
            // Make sure last element is always non-null.
            totalEntries--;
            while (index > 0 && getKey(index - 1) == null) {
                totalEntries--;
                deletedEntries--;
                index--;
            }
        } else {
            deletedEntries++;
            result = maybeCompress(result);
        }

        return result;
    }

    private abstract class SparseMapIterator<E> implements Iterator<E> { //

        protected int current;

        @Override
        public boolean hasNext() {
            return current < totalEntries;
        }

        @Override
        public void remove() {
            if (hasHashArray()) {
                final PythonObjectLibrary lib = PythonObjectLibrary.getUncached();
                PEMap.this.findAndRemoveHash(getKey(current - 1), lib, lib);
            }
            current = PEMap.this.remove(current - 1);
        }
    }

    @TruffleBoundary
    public Iterable<Object> getValues() {
        return new Iterable<Object>() {
            @Override
            public Iterator<Object> iterator() {
                return new SparseMapIterator<Object>() {
                    @SuppressWarnings("unchecked")
                    @Override
                    public Object next() {
                        Object result;
                        while (true) {
                            result = getValue(current);
                            if (result == null && getKey(current) == null) {
                                // values can be null, double-check if key is also null
                                current++;
                            } else {
                                current++;
                                break;
                            }
                        }
                        return result;
                    }
                };
            }
        };
    }

    public Iterable<DictKey> getKeys() {
        return this;
    }

    public boolean isEmpty() {
        return this.size() == 0;
    }

    @TruffleBoundary
    public MapCursor<DictKey, Object> getEntries() {
        return new MapCursor<DictKey, Object>() {
            int current = -1;

            @Override
            public boolean advance() {
                current++;
                if (current >= totalEntries) {
                    return false;
                } else {
                    while (PEMap.this.getKey(current) == null) {
                        // Skip over null entries
                        current++;
                    }
                    return true;
                }
            }

            @Override
            public DictKey getKey() {
                return PEMap.this.getKey(current);
            }

            @Override
            public Object getValue() {
                return PEMap.this.getValue(current);
            }

            @Override
            public void remove() {
                if (hasHashArray()) {
                    final PythonObjectLibrary lib = PythonObjectLibrary.getUncached();
                    PEMap.this.findAndRemoveHash(PEMap.this.getKey(current), lib, lib);
                }
                current = PEMap.this.remove(current) - 1;
            }
        };
    }

    public void replaceAll(BiFunction<? super DictKey, ? super Object, ? extends Object> function) {
        for (int i = 0; i < totalEntries; i++) {
            DictKey entryKey = getKey(i);
            if (entryKey != null) {
                Object newValue = function.apply(entryKey, getValue(i));
                setValue(i, newValue);
            }
        }
    }

    private DictKey getKey(int index) {
        return (DictKey) entries[index << 1];
    }

    private void setKey(int index, DictKey newValue) {
        entries[index << 1] = newValue;
    }

    private void setValue(int index, Object newValue) {
        Object oldValue = getRawValue(index);
        if (oldValue instanceof CollisionLink) {
            CollisionLink collisionLink = (CollisionLink) oldValue;
            setRawValue(index, new CollisionLink(newValue, collisionLink.next));
        } else {
            setRawValue(index, newValue);
        }
    }

    private void setRawValue(int index, Object newValue) {
        entries[(index << 1) + 1] = newValue;
    }

    private Object getRawValue(int index) {
        return entries[(index << 1) + 1];
    }

    private Object getValue(int index) {
        Object object = getRawValue(index);
        if (object instanceof CollisionLink) {
            return ((CollisionLink) object).value;
        }
        return object;
    }

    private final boolean isSet;

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();
        builder.append(isSet ? "set(size=" : "map(size=").append(size()).append(", {");
        String sep = "";
        MapCursor<DictKey, Object> cursor = getEntries();
        while (cursor.advance()) {
            builder.append(sep);
            if (isSet) {
                builder.append(cursor.getKey());
            } else {
                builder.append("(").append(cursor.getKey()).append(",").append(cursor.getValue()).append(")");
            }
            sep = ",";
        }
        builder.append("})");
        return builder.toString();
    }

    @Override
    public Iterator<DictKey> iterator() {
        return new SparseMapIterator<DictKey>() {
            @Override
            public DictKey next() {
                DictKey result;
                while ((result = getKey(current++)) == null) {
                    // skip null entries
                }
                return result;
            }
        };
    }

    public boolean contains(DictKey element, PythonObjectLibrary keylib, PythonObjectLibrary otherlib) {
        return containsKey(element, keylib, otherlib);
    }

    public boolean add(DictKey element, PythonObjectLibrary keylib, PythonObjectLibrary otherlib) {
        return put(element, element, keylib, otherlib) == null;
    }

    public void remove(DictKey element, PythonObjectLibrary keylib, PythonObjectLibrary otherlib) {
        removeKey(element, keylib, otherlib);
    }

}
