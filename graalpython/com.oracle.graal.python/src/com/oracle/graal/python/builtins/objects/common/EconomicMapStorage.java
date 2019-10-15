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
import java.util.Objects;

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;

/**
 * Implementation of a map with a memory-efficient structure that always preserves insertion order
 * when iterating over keys. Particularly efficient when number of entries is 0 or smaller equal
 * {@link #INITIAL_CAPACITY} or smaller 256.
 *
 * The key/value pairs are kept in an expanding flat object array with keys at even indices and
 * values at odd indices. If the map has smaller or equal to {@link #HASH_THRESHOLD} entries, there
 * is no additional hash data structure and comparisons are done via linear checking of the
 * key/value pairs.
 *
 * When the hash table needs to be constructed, the field {@link #hashArray} becomes a new hash
 * array where an entry of 0 means no hit and otherwise denotes the entry number in the
 * {@link #entriesArr} array. The hash array is interpreted as an actual byte array if the indices
 * fit within 8 bit, or as an array of short values if the indices fit within 16 bit, or as an array
 * of integer values in other cases.
 *
 * Hash collisions are handled by chaining a linked list of {@link CollisionLink} objects that take
 * the place of the values in the {@link #entriesArr} array.
 *
 * Removing entries will put {@code null} into the {@link #entriesArr} array. If the occupation of
 * the map falls below a specific threshold, the map will be compressed via the
 * {@link #maybeCompress(int, Equivalence)} method.
 */
public class EconomicMapStorage extends HashingStorage implements Iterable<Object> {

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
    private Object[] entriesArr;

    /**
     * Hash array that is interpreted either as byte or short or int array depending on number of
     * map entries.
     */
    private byte[] hashArray;

    public static EconomicMapStorage create(boolean isSet) {
        return new EconomicMapStorage(isSet);
    }

    public static EconomicMapStorage create(int initialCapacity, boolean isSet) {
        return new EconomicMapStorage(initialCapacity, isSet);
    }

    public static EconomicMapStorage create(EconomicMapStorage other, boolean isSet, Equivalence eq) {
        return new EconomicMapStorage(other, isSet, eq);
    }

    private EconomicMapStorage(boolean isSet) {
        this.isSet = isSet;
    }

    private EconomicMapStorage(int initialCapacity, boolean isSet) {
        this(isSet);
        init(initialCapacity);
    }

    private EconomicMapStorage(EconomicMapStorage other, boolean isSet, Equivalence eq) {
        this(isSet);
        if (!initFrom(other)) {
            init(other.length());
            putAll(other, eq);
        }
    }

    @TruffleBoundary
    private boolean initFrom(Object o) {
        if (o instanceof EconomicMapStorage) {
            EconomicMapStorage otherMap = (EconomicMapStorage) o;
            // We are only allowed to directly copy if the strategies of the two maps are the same.
            totalEntries = otherMap.totalEntries;
            deletedEntries = otherMap.deletedEntries;
            if (otherMap.entriesArr != null) {
                entriesArr = otherMap.entriesArr.clone();
            }
            if (otherMap.hashArray != null) {
                hashArray = otherMap.hashArray.clone();
            }
            return true;
        }
        return false;
    }

    private void init(int size) {
        if (size > INITIAL_CAPACITY) {
            entriesArr = new Object[size << 1];
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

    @Override
    public Object getItem(Object key, Equivalence eq) {
        Objects.requireNonNull(key);

        int index = find(key, eq);
        if (index != -1) {
            return getValue(index);
        }
        return null;
    }

    private int find(Object key, Equivalence eq) {
        if (hasHashArray()) {
            return findHash(key, eq);
        } else {
            return findLinear(key, eq);
        }
    }

    private int findLinear(Object key, Equivalence eq) {
        for (int i = 0; i < totalEntries; i++) {
            Object entryKey = entriesArr[i << 1];
            if (entryKey != null && compareKeys(key, entryKey, eq)) {
                return i;
            }
        }
        return -1;
    }

    private static boolean compareKeys(Object key, Object entryKey, Equivalence strategy) {
        if (key == entryKey) {
            return true;
        }
        if (strategy != null) {
            // both need to be called in order to capture the side-effects of implemented __hash__
            // and __eq__ methods
            // see: test_dict.py: test_errors_in_view_containment_check for more details
            boolean sameHash = strategy.hashCode(key) == strategy.hashCode(entryKey);
            boolean areEqual = strategy.equals(key, entryKey);
            return sameHash && areEqual;
        }
        return key.equals(entryKey);
    }

    private int findHash(Object key, Equivalence eq) {
        int index = getHashArray(getHashIndex(key, eq)) - 1;
        if (index != -1) {
            Object entryKey = getKey(index);
            if (compareKeys(key, entryKey, eq)) {
                return index;
            } else {
                Object entryValue = getRawValue(index);
                if (entryValue instanceof CollisionLink) {
                    return findWithCollision(key, (CollisionLink) entryValue, eq);
                }
            }
        }

        return -1;
    }

    private int findWithCollision(Object key, CollisionLink initialEntryValue, Equivalence eq) {
        int index;
        Object entryKey;
        CollisionLink entryValue = initialEntryValue;
        while (true) {
            CollisionLink collisionLink = entryValue;
            index = collisionLink.next;
            entryKey = getKey(index);
            if (compareKeys(key, entryKey, eq)) {
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
        if (entriesArr.length < LARGE_HASH_THRESHOLD) {
            return (hashArray[index] & 0xFF);
        } else if (entriesArr.length < VERY_LARGE_HASH_THRESHOLD) {
            int adjustedIndex = index << 1;
            return (hashArray[adjustedIndex] & 0xFF) | ((hashArray[adjustedIndex + 1] & 0xFF) << 8);
        } else {
            int adjustedIndex = index << 2;
            return (hashArray[adjustedIndex] & 0xFF) | ((hashArray[adjustedIndex + 1] & 0xFF) << 8) | ((hashArray[adjustedIndex + 2] & 0xFF) << 16) | ((hashArray[adjustedIndex + 3] & 0xFF) << 24);
        }
    }

    private void setHashArray(int index, int value) {
        if (entriesArr.length < LARGE_HASH_THRESHOLD) {
            hashArray[index] = (byte) value;
        } else if (entriesArr.length < VERY_LARGE_HASH_THRESHOLD) {
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

    private int findAndRemoveHash(Object key, Equivalence eq) {
        int hashIndex = getHashIndex(key, eq);
        int index = getHashArray(hashIndex) - 1;
        if (index != -1) {
            Object entryKey = getKey(index);
            if (compareKeys(key, entryKey, eq)) {
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
                    return findAndRemoveWithCollision(key, (CollisionLink) entryValue, index, eq);
                }
            }
        }

        return -1;
    }

    private int findAndRemoveWithCollision(Object key, CollisionLink initialEntryValue, int initialIndexValue, Equivalence eq) {
        int index;
        Object entryKey;
        CollisionLink entryValue = initialEntryValue;
        int lastIndex = initialIndexValue;
        while (true) {
            CollisionLink collisionLink = entryValue;
            index = collisionLink.next;
            entryKey = getKey(index);
            if (compareKeys(key, entryKey, eq)) {
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

    private int getHashIndex(Object key, Equivalence strategy) {
        int hash;
        if (strategy != null) {
            hash = strategy.hashCode(key);
        } else {
            hash = key.hashCode();
        }
        hash = hash ^ (hash >>> 16);
        return hash & (getHashTableSize() - 1);
    }

    /**
     * Copies all of the mappings from {@code other} to this map.
     *
     * @since 1.0
     */
    public void putAll(EconomicMapStorage other, Equivalence eq) {
        for (DictEntry entry : other.entries()) {
            setItem(entry.getKey(), entry.getValue(), eq);
        }
    }

    @Override
    public void setItem(Object key, Object value, Equivalence eq) {
        if (key == null) {
            throw new UnsupportedOperationException("null not supported as key!");
        }
        int index = find(key, eq);
        if (index != -1) {
            setValue(index, value);
            return;
        }

        int nextEntryIndex = totalEntries;
        if (entriesArr == null) {
            entriesArr = new Object[INITIAL_CAPACITY << 1];
        } else if (entriesArr.length == nextEntryIndex << 1) {
            grow(eq);

            assert entriesArr.length > totalEntries << 1;
            // Can change if grow is actually compressing.
            nextEntryIndex = totalEntries;
        }

        setKey(nextEntryIndex, key);
        setValue(nextEntryIndex, value);
        totalEntries++;

        if (hasHashArray()) {
            // Rehash on collision if hash table is more than three quarters full.
            boolean rehashOnCollision = (getHashTableSize() < (length() + (length() >> 1)));
            putHashEntry(key, nextEntryIndex, rehashOnCollision, eq);
        } else if (totalEntries > getHashThreshold()) {
            createHash(eq);
        }

    }

    /**
     * Number of entries above which a hash table should be constructed.
     */
    private static int getHashThreshold() {
        return HASH_THRESHOLD;
    }

    private void grow(Equivalence eq) {
        int entriesLength = entriesArr.length;
        int newSize = (entriesLength >> 1) + Math.max(MIN_CAPACITY_INCREASE, entriesLength >> 2);
        if (newSize > MAX_ELEMENT_COUNT) {
            throw new UnsupportedOperationException("map grown too large!");
        }
        Object[] newEntries = new Object[newSize << 1];
        System.arraycopy(entriesArr, 0, newEntries, 0, entriesLength);
        entriesArr = newEntries;
        if ((entriesLength < LARGE_HASH_THRESHOLD && newEntries.length >= LARGE_HASH_THRESHOLD) ||
                        (entriesLength < VERY_LARGE_HASH_THRESHOLD && newEntries.length > VERY_LARGE_HASH_THRESHOLD)) {
            // Rehash in order to change number of bits reserved for hash indices.
            createHash(eq);
        }
    }

    /**
     * Compresses the graph if there is a large number of deleted entries and returns the translated
     * new next index.
     */
    private int maybeCompress(int nextIndex, Equivalence eq) {
        if (entriesArr.length != INITIAL_CAPACITY << 1 && deletedEntries >= (totalEntries >> 1) + (totalEntries >> 2)) {
            return compressLarge(nextIndex, eq);
        }
        return nextIndex;
    }

    /**
     * Compresses the graph and returns the translated new next index.
     */
    private int compressLarge(int nextIndex, Equivalence eq) {
        int size = INITIAL_CAPACITY;
        int remaining = totalEntries - deletedEntries;

        while (size <= remaining) {
            size += Math.max(MIN_CAPACITY_INCREASE, size >> 1);
        }

        Object[] newEntries = new Object[size << 1];
        int z = 0;
        int newNextIndex = remaining;
        for (int i = 0; i < totalEntries; ++i) {
            Object key = getKey(i);
            if (i == nextIndex) {
                newNextIndex = z;
            }
            if (key != null) {
                newEntries[z << 1] = key;
                newEntries[(z << 1) + 1] = getValue(i);
                z++;
            }
        }

        this.entriesArr = newEntries;
        totalEntries = z;
        deletedEntries = 0;
        if (z <= getHashThreshold()) {
            this.hashArray = null;
        } else {
            createHash(eq);
        }
        return newNextIndex;
    }

    private int getHashTableSize() {
        if (entriesArr.length < LARGE_HASH_THRESHOLD) {
            return hashArray.length;
        } else if (entriesArr.length < VERY_LARGE_HASH_THRESHOLD) {
            return hashArray.length >> 1;
        } else {
            return hashArray.length >> 2;
        }
    }

    private void createHash(Equivalence eq) {
        int entryCount = length();

        // Calculate smallest 2^n that is greater number of entries.
        int size = getHashThreshold();
        while (size <= entryCount) {
            size <<= 1;
        }

        // Give extra size to avoid collisions.
        size <<= 1;

        if (this.entriesArr.length >= VERY_LARGE_HASH_THRESHOLD) {
            // Every entry has 4 bytes.
            size <<= 2;
        } else if (this.entriesArr.length >= LARGE_HASH_THRESHOLD) {
            // Every entry has 2 bytes.
            size <<= 1;
        } else {
            // Entries are very small => give extra size to further reduce collisions.
            size <<= 1;
        }

        hashArray = new byte[size];
        for (int i = 0; i < totalEntries; i++) {
            Object entryKey = getKey(i);
            if (entryKey != null) {
                putHashEntry(entryKey, i, false, eq);
            }
        }
    }

    private void putHashEntry(Object key, int entryIndex, boolean rehashOnCollision, Equivalence eq) {
        int hashIndex = getHashIndex(key, eq);
        int oldIndex = getHashArray(hashIndex) - 1;
        if (oldIndex != -1 && rehashOnCollision) {
            this.createHash(eq);
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

    @Override
    public int length() {
        return totalEntries - deletedEntries;
    }

    @Override
    public void clear() {
        entriesArr = null;
        hashArray = null;
        totalEntries = deletedEntries = 0;
    }

    private boolean hasHashArray() {
        return hashArray != null;
    }

    /**
     * Removes the element at the specific index and returns the index of the next element. This can
     * be a different value if graph compression was triggered.
     */
    private int remove(int indexToRemove, Equivalence eq) {
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
            result = maybeCompress(result, eq);
        }

        return result;
    }

    private abstract class SparseMapIterator implements Iterator<Object> {

        protected int current;

        public boolean hasNext() {
            return current < totalEntries;
        }
    }

    private Object getKey(int index) {
        return entriesArr[index << 1];
    }

    private void setKey(int index, Object newValue) {
        entriesArr[index << 1] = newValue;
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
        entriesArr[(index << 1) + 1] = newValue;
    }

    private Object getRawValue(int index) {
        return entriesArr[(index << 1) + 1];
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
        CompilerAsserts.neverPartOfCompilation();
        StringBuilder builder = new StringBuilder();
        builder.append(isSet ? "set(size=" : "map(size=").append(length()).append(", {");
        String sep = "";
        MapCursor cursor = new MapCursor();
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

    public Iterator<Object> iterator() {
        return new SparseMapIterator() {
            @Override
            public Object next() {
                Object result;
                while ((result = getKey(current++)) == null) {
                    // skip null entries
                }
                return result;
            }
        };
    }

    @Override
    public boolean hasKey(Object key, Equivalence eq) {
        return find(key, eq) != -1;
    }

    @Override
    public boolean remove(Object key, Equivalence eq) {
        if (key == null) {
            throw new UnsupportedOperationException("null not supported as key!");
        }
        int index;
        if (hasHashArray()) {
            index = this.findAndRemoveHash(key, eq);
        } else {
            index = this.findLinear(key, eq);
        }

        if (index != -1) {
            remove(index, eq);
            return true;
        }
        return false;
    }

    private class MapCursor {

        private int current = -1;

        protected boolean advance() {
            current++;
            if (current >= totalEntries) {
                return false;
            } else {
                while (EconomicMapStorage.this.getKey(current) == null) {
                    // Skip over null entries
                    current++;
                }
                return true;
            }
        }

        public Object getKey() {
            return EconomicMapStorage.this.getKey(current);
        }

        public Object getValue() {
            return EconomicMapStorage.this.getValue(current);
        }
    }

    private class MapIterator extends MapCursor implements Iterator<DictEntry> {

        private int consumed = 0;

        @Override
        public boolean hasNext() {
            return consumed < EconomicMapStorage.this.length();

        }

        @Override
        public DictEntry next() {
            if (!advance()) {
                throw new NoSuchElementException();
            }
            consumed++;
            return new DictEntry(getKey(), getValue());
        }
    }

    @Override
    public Iterable<Object> keys() {
        return this;
    }

    @Override
    @TruffleBoundary
    public Iterable<Object> values() {
        return new Iterable<Object>() {
            @Override
            public Iterator<Object> iterator() {
                return new SparseMapIterator() {
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

    @Override
    public Iterable<DictEntry> entries() {
        return new Iterable<HashingStorage.DictEntry>() {

            public Iterator<DictEntry> iterator() {
                return new MapIterator();
            }
        };
    }

    @Override
    @TruffleBoundary
    public HashingStorage copy(Equivalence eq) {
        return new EconomicMapStorage(this, this.isSet, eq);
    }
}
