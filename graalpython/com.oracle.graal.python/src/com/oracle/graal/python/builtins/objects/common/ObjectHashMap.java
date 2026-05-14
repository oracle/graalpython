/*
 * Copyright (c) 2020, 2026, Oracle and/or its affiliates. All rights reserved.
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

import static com.oracle.truffle.api.CompilerDirectives.SLOWPATH_PROBABILITY;

import com.oracle.graal.python.builtins.objects.common.ObjectHashMapFactory.PutNodeGen;
import com.oracle.graal.python.builtins.objects.common.ObjectHashMapFactory.RemoveNodeGen;
import com.oracle.graal.python.lib.PyObjectRichCompareBool;
import com.oracle.graal.python.util.PythonUtils;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.HostCompilerDirectives.InliningCutoff;
import com.oracle.truffle.api.TruffleSafepoint;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateCached;
import com.oracle.truffle.api.dsl.GenerateInline;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.nodes.LoopNode;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.profiles.InlinedBranchProfile;
import com.oracle.truffle.api.profiles.InlinedConditionProfile;
import com.oracle.truffle.api.profiles.InlinedCountingConditionProfile;

/**
 * Generic dictionary/set backing storage implementation.
 * <p>
 * The basic algorithm is hash table with open addressing for collision resolution. For that we need
 * to have a fixed order of indexes to probe if collision happens. Simple implementations use linear
 * search (+/-1) from the bucket where the collision happened. We use the same more advanced scheme
 * as PyPy/CPython. It relies on the fact that the recurrence
 *
 * <code>
 * j = ((5*j) + 1) mod N^2
 * </code>
 * <p>
 * generates all numbers from 0 to (N^2)-1, but not in linear order, i.e., for j=1, N=3, we get: 1 6
 * 7 4 5 2 3 0. In our case we set j to the index of the bucket that has the collision. To make this
 * scheme also dependent on the higher bits of the hash, we use this formula (also like CPython):
 *
 * <code>
 * perturb >>= PERTURB_SHIFT
 * j = ((5*j) + perturb + 1) mod N^2
 * </code>
 * <p>
 * Which is not guaranteed to generate numbers from 0 to (N^2)-1 in general, but when the perturb
 * value is shifted often enough, it becomes 0, and then we're effectively using the original
 * version of the recurrence that does guarantee that.
 * <p>
 * Additionally, we use the same trick as PyPy/CPython: there is one sparse array, which is the
 * actual hash table, but it does not contain the entries, it contains indices into compact arrays
 * with hashes and keys and values. This not only should be memory efficient and cache friendly, but
 * it preserves the insertion order, which is a requirement.
 * <p>
 * On top of the PyPy/CPython design: we use the highest bit in the indices stored in the sparse
 * array to indicate whether given bucket participates in some collisions chain. When searching
 * through a collision chain, we can stop at items that do not have this bit set. The practical
 * implications of this is that for close to full maps, lookups of items that are not present in the
 * map are faster, because we can terminate the collisions chain chasing earlier.
 * <p>
 * Notable use case that does not (yet) work well with this approach: repeated insertion and removal
 * of the same key. This keeps on adding dummy entries when removing the entry and creating long
 * collisions chains that the insertion needs to follow to find a free slot. This all repeats until
 * the insertion concludes that the map is too full and rehashes it, in this case actually not
 * growing it, but just removing the dummy entries. The same seems to happen on CPython also, but
 * can be improved.
 * <p>
 * Areas for future improvements:
 * <ul>
 * <li>Use another bit from the index in the sparse indices array to remember index of removed
 * items, i.e., dummy items would carry the old index and collision mask. Such dummy items can be
 * reused when inserting new items. This will help with the insert/remove of the same key
 * scenario.</li>
 * <li>New strategy for long keys where the hashes array is used to store the keys, and the
 * keysAndValues array will store just values. Can be implemented by extending this class and
 * overriding few methods.</li>
 * <li>Flag that indicates that the hash-map is used as a storage for a set, so all values are
 * {@code None} and there is no need to allocate space for values in the keysAndValues array.</li>
 * </ul>
 */
public class ObjectHashMap extends HashingStorage {
    private static final int TIGHT_ENTRY_CAPACITY_LIMIT = 8;

    static final int INDEX_BYTE_SIZE_CACHE_LIMIT = 3;

    /**
     * Every hash map will preallocate at least this many buckets (and corresponding # of slots for
     * the real items).
     */
    private static final int INITIAL_INDICES_SIZE = 4;

    /**
     * We limit the max size of preallocated hash maps. See the comment in the ctor.
     */
    private static final int MAX_PREALLOCATED_INDICES_SIZE = 1 << 20;

    /**
     * Indices that participate in a collision chain are marked with the sign bit in the logical
     * representation. The physical storage may use a narrower primitive array.
     */
    private static final int COLLISION_MASK = 1 << 31;
    private static final int BYTE_COLLISION_MASK = 1 << 7;
    private static final int SHORT_COLLISION_MASK = 1 << 15;
    private static final int BYTE_COLLISION_SHIFT = 24;
    private static final int SHORT_COLLISION_SHIFT = 16;
    private static final int INT_COLLISION_SHIFT = 0;

    /**
     * Sparse table indices use 0 and 1 as reserved markers and store real compact-array indices as
     * {@code index + INDEX_OFFSET}. The collision bit is kept separately.
     */
    private static final int EMPTY_INDEX = 0;
    private static final int DUMMY_INDEX = 1;
    private static final int INDEX_OFFSET = 2;
    private static final int MAX_BYTE_INDEX = (BYTE_COLLISION_MASK - 1) - INDEX_OFFSET;
    private static final int MAX_SHORT_INDEX = (SHORT_COLLISION_MASK - 1) - INDEX_OFFSET;

    private static void markCollision(byte[] metadata, int indicesOffset, int indexByteSize, int physicalCollisionMask, int compactIndex) {
        int index = getIndex(metadata, indicesOffset, indexByteSize, compactIndex);
        assert index != EMPTY_INDEX;
        if (index != DUMMY_INDEX) {
            setIndex(metadata, indicesOffset, indexByteSize, physicalCollisionMask, compactIndex, index | COLLISION_MASK);
        }
    }

    private static boolean isCollision(int index) {
        return (index & COLLISION_MASK) != 0;
    }

    private static int unwrapIndex(int value) {
        return (value & ~COLLISION_MASK) - INDEX_OFFSET;
    }

    private static final long PERTURB_SHIFT = 5;
    // It takes at most this many >>> shifts to turn any long into 0
    private static final int PERTURB_SHIFTS_COUT = 13;

    // Packed metadata: hashes first, then sparse indices.
    private byte[] metadata;

    // Compact array with the actual dict items:
    Object[] keysAndValues;

    // How many real items are in the dict
    int size;
    // How many of the slots in the hashes/keysAndValues arrays are occupied either with real item
    // or dummy item. Note: we compact those arrays on deletion if there are too many dummy entries,
    // but we do not compact the indices array to retain the collision sequences. On rehashing,
    // triggered from insertion, we do remove dummy entries and rearrange the collision sequences
    // (as a side effect of reinserting all the items again).
    int usedHashes;
    // How many of the buckets in indices array are used. This may be larger by usedHashes if
    // we compacted on deletion.
    int usedIndices;

    public ObjectHashMap(int capacity) {
        int allocateSize;
        int entryCapacity;
        if (capacity <= 0) {
            allocateSize = INITIAL_INDICES_SIZE;
            entryCapacity = getUsableSize(allocateSize);
        } else {
            if (capacity > getUsableSize(MAX_PREALLOCATED_INDICES_SIZE)) {
                // This oddity is here because in some cases we are asked to allocate very large
                // dict in a situation where CPython (probably) does not preallocate at all and
                // fails later during the actual insertion on something unrelated before it can
                // reach the memory limit. We'd fail on the memory limit earlier -> difference in
                // behavior, so we take it easy if the requested size is too large. Maybe we should
                // rather revisit all such callsites instead of fixing this here...
                allocateSize = MAX_PREALLOCATED_INDICES_SIZE;
                entryCapacity = getUsableSize(allocateSize);
            } else {
                allocateSize = getMinBucketsCount(capacity);
                entryCapacity = getRequestedEntryCapacity(capacity, allocateSize);
            }
        }
        allocateData(allocateSize, entryCapacity);
    }

    public ObjectHashMap() {
        // Note: there is no point in null values/keys, we should use empty storage for such cases,
        // this map will almost certainly have some actual elements
        allocateData(INITIAL_INDICES_SIZE);
    }

    protected ObjectHashMap(ObjectHashMap original) {
        size = original.size;
        usedHashes = original.usedHashes;
        usedIndices = original.usedIndices;
        metadata = PythonUtils.arrayCopyOf(original.metadata, original.metadata.length);
        keysAndValues = PythonUtils.arrayCopyOf(original.keysAndValues, original.keysAndValues.length);
    }

    private void allocateData(int bucketsCount) {
        allocateData(bucketsCount, getUsableSize(bucketsCount));
    }

    private void allocateData(int bucketsCount, int entryCapacity) {
        assert isPow2(bucketsCount);
        assert entryCapacity > 0 && entryCapacity <= getUsableSize(bucketsCount);
        ensureArraySizesFit(bucketsCount, entryCapacity);
        metadata = createMetadata(bucketsCount, entryCapacity);
        keysAndValues = new Object[entryCapacity * 2];
    }

    public void clear() {
        size = 0;
        usedHashes = 0;
        usedIndices = 0;
        allocateData(INITIAL_INDICES_SIZE);
    }

    public MapCursor getEntries() {
        return new MapCursor();
    }

    @CompilerDirectives.ValueType
    public static final class DictKey {
        private final Object value;
        private final long hash;

        DictKey(Object value, long hash) {
            this.value = value;
            this.hash = hash;
        }

        public Object getValue() {
            return value;
        }

        public long getPythonHash() {
            return hash;
        }
    }

    public final class MapCursor {
        private int index = -1;

        private void moveToNextValue() {
            while (index < usedHashes && ObjectHashMap.this.getValue(index) == null) {
                index++;
            }
        }

        public boolean advance() {
            index++;
            moveToNextValue();
            return index < usedHashes;
        }

        public DictKey getKey() {
            return new DictKey(ObjectHashMap.this.getKey(index), ObjectHashMap.this.getHash(index));
        }

        public Object getValue() {
            return ObjectHashMap.this.getValue(index);
        }
    }

    int getEntryCapacity() {
        return keysAndValues.length >> 1;
    }

    private static byte[] createMetadata(int bucketsCount, int usableSize) {
        return new byte[getMetadataLength(bucketsCount, usableSize)];
    }

    private static int getBucketsCount(byte[] metadata, int entryCapacity, int indexByteSize) {
        return (metadata.length - getIndicesOffset(entryCapacity)) / indexByteSize;
    }

    static int getIndexByteSize(int entryCapacity) {
        if (entryCapacity - 1 <= MAX_BYTE_INDEX) {
            return Byte.BYTES;
        } else if (entryCapacity - 1 <= MAX_SHORT_INDEX) {
            return Short.BYTES;
        } else {
            return Integer.BYTES;
        }
    }

    @TruffleBoundary
    private static int getIndexByteSizeAfterRestart(int entryCapacity) {
        return getIndexByteSize(entryCapacity);
    }

    private static int getIndicesOffset(int entryCapacity) {
        return castMetadataInt(getIndicesOffsetLong(entryCapacity));
    }

    private static int getMetadataLength(int bucketsCount, int entryCapacity) {
        return castMetadataInt(getMetadataLengthLong(bucketsCount, entryCapacity));
    }

    private static int getHashOffset(int index) {
        return castMetadataInt((long) index * Long.BYTES);
    }

    private static int getIndexOffset(int indicesOffset, int indexByteSize, int compactIndex) {
        return indicesOffset + compactIndex * indexByteSize;
    }

    private static long getIndicesOffsetLong(int entryCapacity) {
        return (long) entryCapacity * Long.BYTES;
    }

    private static long getMetadataLengthLong(int bucketsCount, int entryCapacity) {
        return getIndicesOffsetLong(entryCapacity) + ((long) bucketsCount * getIndexByteSize(entryCapacity));
    }

    private static void ensureArraySizesFit(int bucketsCount, int entryCapacity) {
        long metadataLength = getMetadataLengthLong(bucketsCount, entryCapacity);
        if (metadataLength > Integer.MAX_VALUE || ((long) entryCapacity << 1) > Integer.MAX_VALUE) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            throw new OutOfMemoryError();
        }
    }

    private static int castMetadataInt(long value) {
        assert value >= 0 && value <= Integer.MAX_VALUE;
        return (int) value;
    }

    private static int getPhysicalCollisionMaskForIndexByteSize(int indexByteSize) {
        if (indexByteSize == Byte.BYTES) {
            return BYTE_COLLISION_MASK;
        } else if (indexByteSize == Short.BYTES) {
            return SHORT_COLLISION_MASK;
        } else {
            return COLLISION_MASK;
        }
    }

    private static int getIndex(byte[] metadata, int indicesOffset, int indexByteSize, int compactIndex) {
        int offset = getIndexOffset(indicesOffset, indexByteSize, compactIndex);
        if (indexByteSize == Byte.BYTES) {
            return decodeIndex(metadata[offset] & 0xFF, BYTE_COLLISION_MASK, BYTE_COLLISION_SHIFT);
        } else if (indexByteSize == Short.BYTES) {
            return decodeIndex(PythonUtils.ARRAY_ACCESSOR.getShort(metadata, offset) & 0xFFFF, SHORT_COLLISION_MASK, SHORT_COLLISION_SHIFT);
        } else {
            return decodeIndex(PythonUtils.ARRAY_ACCESSOR.getInt(metadata, offset), COLLISION_MASK, INT_COLLISION_SHIFT);
        }
    }

    private static int decodeIndex(int encodedValue, int physicalCollisionMask, int collisionShift) {
        int value = encodedValue & (physicalCollisionMask - 1);
        return value | ((encodedValue & physicalCollisionMask) << collisionShift);
    }

    private static void setIndex(byte[] metadata, int indicesOffset, int indexByteSize, int physicalCollisionMask, int compactIndex, int logicalValue) {
        int encodedValue = logicalValue & ~COLLISION_MASK;
        if ((logicalValue & COLLISION_MASK) != 0) {
            encodedValue |= physicalCollisionMask;
        }
        int offset = getIndexOffset(indicesOffset, indexByteSize, compactIndex);
        if (indexByteSize == Byte.BYTES) {
            metadata[offset] = (byte) encodedValue;
        } else if (indexByteSize == Short.BYTES) {
            PythonUtils.ARRAY_ACCESSOR.putShort(metadata, offset, (short) encodedValue);
        } else {
            PythonUtils.ARRAY_ACCESSOR.putInt(metadata, offset, encodedValue);
        }
    }

    private static long getHash(byte[] metadata, int index) {
        return PythonUtils.ARRAY_ACCESSOR.getLong(metadata, getHashOffset(index));
    }

    private static void setHash(byte[] metadata, int index, long hash) {
        PythonUtils.ARRAY_ACCESSOR.putLong(metadata, getHashOffset(index), hash);
    }

    long getHash(int index) {
        return getHash(metadata, index);
    }

    private boolean needsResize(int entryCapacity, int bucketsCount) {
        // Keep one slot empty at all times. For the smallest table, that means resizing once 2 of
        // the 4 buckets are already in use instead of allowing the table to become completely full.
        return usedHashes >= entryCapacity || usedIndices >= getUsableSize(bucketsCount);
    }

    public int size() {
        return size;
    }

    @GenerateUncached
    @GenerateInline
    @GenerateCached(false)
    @ImportStatic(ObjectHashMap.class)
    public abstract static class PopNode extends Node {
        public abstract Object[] execute(Node inliningTarget, ObjectHashMap map);

        @TruffleBoundary
        public static Object[] doPopWithRestartForTests(ObjectHashMap map) {
            // Public entry point for Java tests that bypass the generated node.
            int entryCapacity = map.getEntryCapacity();
            return doPopWithRestart(null, map, entryCapacity, getIndexByteSize(entryCapacity), InlinedConditionProfile.getUncached(),
                            InlinedCountingConditionProfile.getUncached(), InlinedCountingConditionProfile.getUncached(), InlinedBranchProfile.getUncached());
        }

        @Specialization(guards = "indexByteSize == getIndexByteSize(entryCapacity)", limit = "INDEX_BYTE_SIZE_CACHE_LIMIT")
        static Object[] doPopWithRestart(Node inliningTarget, ObjectHashMap map,
                        @Bind("map.getEntryCapacity()") int entryCapacity,
                        @Cached(value = "getIndexByteSize(entryCapacity)", allowUncached = true) int indexByteSize,
                        @Cached InlinedConditionProfile emptyMapProfile,
                        @Cached InlinedCountingConditionProfile hasValueProfile,
                        @Cached InlinedCountingConditionProfile hasCollisionProfile,
                        @Cached InlinedBranchProfile lookupRestart) {
            while (true) {
                try {
                    return doPop(inliningTarget, map, map.metadata, entryCapacity, indexByteSize, emptyMapProfile, hasValueProfile, hasCollisionProfile);
                } catch (RestartLookupException ignore) {
                    lookupRestart.enter(inliningTarget);
                    entryCapacity = map.getEntryCapacity();
                    indexByteSize = getIndexByteSizeAfterRestart(entryCapacity);
                }
            }
        }

        private static boolean isIndex(int indexInIndices, int indexToFind) {
            return indexInIndices != DUMMY_INDEX && indexInIndices != EMPTY_INDEX && indexToFind == unwrapIndex(indexInIndices);
        }

        private static Object[] doPop(Node inliningTarget, ObjectHashMap map, byte[] metadata, int entryCapacity, int indexByteSize,
                        @Cached InlinedConditionProfile emptyMapProfile,
                        @Cached InlinedCountingConditionProfile hasValueProfile,
                        @Cached InlinedCountingConditionProfile hasCollisionProfile) throws RestartLookupException {
            if (emptyMapProfile.profile(inliningTarget, map.size() == 0)) {
                return null;
            }
            Object[] localKeysAndValues = map.keysAndValues;
            int indicesLen = getBucketsCount(metadata, entryCapacity, indexByteSize);
            int indicesOffset = getIndicesOffset(entryCapacity);
            int physicalCollisionMask = getPhysicalCollisionMaskForIndexByteSize(indexByteSize);
            int usedHashes = map.usedHashes;
            for (int i = usedHashes - 1; i >= 0; i--) {
                if (metadata != map.metadata) {
                    // restart, can happen after Truffle safepoint on backedge
                    throw RestartLookupException.INSTANCE;
                }
                Object value = getValue(i, localKeysAndValues);
                if (hasValueProfile.profile(inliningTarget, value != null)) {
                    // We can remove the item from the compact arrays
                    var result = new Object[]{map.getKey(i), value};
                    // We need to find the slot in the sparse indices array
                    long hash = map.getHash(i);
                    int compactIndex = getIndex(indicesLen, hash);
                    int index = getIndex(metadata, indicesOffset, indexByteSize, compactIndex);
                    if (hasCollisionProfile.profile(inliningTarget, isIndex(index, i))) {
                        setIndex(metadata, indicesOffset, indexByteSize, physicalCollisionMask, compactIndex, DUMMY_INDEX);
                    } else {
                        removeBucketWithIndex(map, metadata, indicesOffset, indexByteSize, physicalCollisionMask, indicesLen, hash, compactIndex, i);
                    }
                    // Only remove the slot now, removeBucketWithIndex can restart the search
                    map.setValue(i, null);
                    map.setKey(i, null);
                    map.size--;
                    return result;
                }
            }
            throw CompilerDirectives.shouldNotReachHere();
        }

        private static void removeBucketWithIndex(ObjectHashMap map, byte[] metadata, int indicesOffset, int indexByteSize, int physicalCollisionMask, int indicesLen, long hash,
                        int initialCompactIndex,
                        int indexToFind)
                        throws RestartLookupException {
            int searchLimit = indicesLen + PERTURB_SHIFTS_COUT;
            long perturb = hash;
            int compactIndex = initialCompactIndex;
            for (int i = 0; i < searchLimit; i++) {
                if (metadata != map.metadata) {
                    // guards against things happening in the safepoint on the backedge
                    throw RestartLookupException.INSTANCE;
                }
                perturb >>>= PERTURB_SHIFT;
                compactIndex = nextIndex(indicesLen, compactIndex, perturb);
                int index = getIndex(metadata, indicesOffset, indexByteSize, compactIndex);
                if (isIndex(index, indexToFind)) {
                    setIndex(metadata, indicesOffset, indexByteSize, physicalCollisionMask, compactIndex, DUMMY_INDEX);
                    return;
                }
            }
            throw CompilerDirectives.shouldNotReachHere();
        }
    }

    @GenerateUncached
    @GenerateInline
    @GenerateCached(false)
    @ImportStatic(ObjectHashMap.class)
    public abstract static class GetNode extends Node {
        public abstract Object execute(Frame frame, Node inliningTarget, ObjectHashMap map, Object key, long keyHash);

        @TruffleBoundary
        public static Object doGetWithRestartForTests(ObjectHashMap map, Object key, long keyHash, PyObjectRichCompareBool eqNode) {
            // Public entry point for Java tests that bypass the generated node.
            int entryCapacity = map.getEntryCapacity();
            InlinedCountingConditionProfile uncachedCounting = InlinedCountingConditionProfile.getUncached();
            return doGetWithRestart(null, null, map, key, keyHash, entryCapacity, getIndexByteSize(entryCapacity), InlinedBranchProfile.getUncached(), uncachedCounting,
                            uncachedCounting, uncachedCounting, uncachedCounting, uncachedCounting, eqNode);
        }

        @Specialization(guards = "indexByteSize == getIndexByteSize(entryCapacity)", limit = "INDEX_BYTE_SIZE_CACHE_LIMIT")
        static Object doGetWithRestart(Frame frame, Node inliningTarget, ObjectHashMap map, Object key, long keyHash,
                        @Bind("map.getEntryCapacity()") int entryCapacity,
                        @Cached(value = "getIndexByteSize(entryCapacity)", allowUncached = true) int indexByteSize,
                        @Cached InlinedBranchProfile lookupRestart,
                        @Cached InlinedCountingConditionProfile foundNullKey,
                        @Cached InlinedCountingConditionProfile foundSameHashKey,
                        @Cached InlinedCountingConditionProfile foundEqKey,
                        @Cached InlinedCountingConditionProfile collisionFoundNoValue,
                        @Cached InlinedCountingConditionProfile collisionFoundEqKey,
                        @Cached PyObjectRichCompareBool eqNode) {
            while (true) {
                try {
                    return doGet(frame, map, key, keyHash, indexByteSize, entryCapacity, inliningTarget, foundNullKey, foundSameHashKey,
                                    foundEqKey, collisionFoundNoValue, collisionFoundEqKey, eqNode);
                } catch (RestartLookupException ignore) {
                    lookupRestart.enter(inliningTarget);
                    TruffleSafepoint.poll(inliningTarget);
                    entryCapacity = map.getEntryCapacity();
                    indexByteSize = getIndexByteSizeAfterRestart(entryCapacity);
                }
            }
        }

        static Object doGet(Frame frame, ObjectHashMap map, Object key, long keyHash, int indexByteSize, int entryCapacity,
                        Node inliningTarget,
                        InlinedCountingConditionProfile foundNullKey,
                        InlinedCountingConditionProfile foundSameHashKey,
                        InlinedCountingConditionProfile foundEqKey,
                        InlinedCountingConditionProfile collisionFoundNoValue,
                        InlinedCountingConditionProfile collisionFoundEqKey,
                        PyObjectRichCompareBool eqNode) throws RestartLookupException {
            assert map.checkInternalState(entryCapacity, indexByteSize);
            byte[] metadata = map.metadata;
            int indicesLen = getBucketsCount(metadata, entryCapacity, indexByteSize);
            int indicesOffset = getIndicesOffset(entryCapacity);

            int compactIndex = getIndex(indicesLen, keyHash);
            int index = getIndex(metadata, indicesOffset, indexByteSize, compactIndex);
            if (foundNullKey.profile(inliningTarget, index == EMPTY_INDEX)) {
                return null;
            }
            if (foundSameHashKey.profile(inliningTarget, index != DUMMY_INDEX)) {
                int unwrappedIndex = unwrapIndex(index);
                if (foundEqKey.profile(inliningTarget, map.keysEqual(metadata, frame, inliningTarget, unwrappedIndex, key, keyHash, eqNode))) {
                    return map.getValue(unwrappedIndex);
                } else if (!isCollision(getIndex(metadata, indicesOffset, indexByteSize, compactIndex))) {
                    // ^ note: we need to re-read the bucket,
                    // it may have been changed during __eq__
                    return null;
                }
            }

            return getCollision(frame, map, key, keyHash, inliningTarget, collisionFoundNoValue, collisionFoundEqKey, eqNode, metadata, indicesOffset, indexByteSize, indicesLen,
                            compactIndex);
        }

        @InliningCutoff
        private static Object getCollision(Frame frame, ObjectHashMap map, Object key, long keyHash, Node inliningTarget,
                        InlinedCountingConditionProfile collisionFoundNoValue,
                        InlinedCountingConditionProfile collisionFoundEqKey,
                        PyObjectRichCompareBool eqNode, byte[] metadata, int indicesOffset, int indexByteSize, int indicesLen, int compactIndex) throws RestartLookupException {
            int index;
            // collision: intentionally counted loop
            long perturb = keyHash;
            int searchLimit = indicesLen + PERTURB_SHIFTS_COUT;
            int i = 0;
            try {
                for (; i < searchLimit; i++) {
                    if (metadata != map.metadata) {
                        // guards against things happening in the safepoint on the backedge
                        throw RestartLookupException.INSTANCE;
                    }
                    perturb >>>= PERTURB_SHIFT;
                    compactIndex = nextIndex(indicesLen, compactIndex, perturb);
                    index = getIndex(metadata, indicesOffset, indexByteSize, compactIndex);
                    if (collisionFoundNoValue.profile(inliningTarget, index == EMPTY_INDEX)) {
                        return null;
                    }
                    if (index != DUMMY_INDEX) {
                        int unwrappedIndex = unwrapIndex(index);
                        if (collisionFoundEqKey.profile(inliningTarget, map.keysEqual(metadata, frame, inliningTarget, unwrappedIndex, key, keyHash, eqNode))) {
                            return map.getValue(unwrappedIndex);
                        } else if (!isCollision(getIndex(metadata, indicesOffset, indexByteSize, compactIndex))) {
                            // ^ note: we need to re-read the bucket,
                            // it may have been changed during __eq__
                            return null;
                        }
                    }
                    TruffleSafepoint.poll(inliningTarget);
                }
            } finally {
                LoopNode.reportLoopCount(eqNode, i);
            }
            // all values are dummies? Not possible, since we should have compacted the
            // hashes/keysAndValues arrays in "remove". We always keep some head-room, so there must
            // be at least few empty slots, and we must have hit one.
            throw CompilerDirectives.shouldNotReachHere();
        }
    }

    @GenerateUncached
    @GenerateInline
    @GenerateCached(false)
    @ImportStatic(ObjectHashMap.class)
    public abstract static class PutNode extends Node {
        public static PutNode getUncached() {
            return PutNodeGen.getUncached();
        }

        public final void put(Frame frame, Node inliningTarget, ObjectHashMap map, DictKey key, Object value) {
            execute(frame, inliningTarget, map, key.getValue(), key.getPythonHash(), value);
        }

        public final void put(Frame frame, Node inliningTarget, ObjectHashMap map, Object key, long keyHash, Object value) {
            execute(frame, inliningTarget, map, key, keyHash, value);
        }

        public static void putUncached(ObjectHashMap map, Object key, long keyHash, Object value) {
            ObjectHashMapFactory.PutNodeGen.getUncached().execute(null, null, map, key, keyHash, value);
        }

        abstract void execute(Frame frame, Node inliningTarget, ObjectHashMap map, Object key, long keyHash, Object value);

        @TruffleBoundary
        public static void doPutWithRestartForTests(ObjectHashMap map, Object key, long keyHash, Object value, PyObjectRichCompareBool eqNode) {
            // Public entry point for Java tests that bypass the generated node.
            int entryCapacity = map.getEntryCapacity();
            InlinedCountingConditionProfile uncachedCounting = InlinedCountingConditionProfile.getUncached();
            doPutWithRestart(null, null, map, key, keyHash, value, entryCapacity, getIndexByteSize(entryCapacity), InlinedBranchProfile.getUncached(), uncachedCounting,
                            uncachedCounting, uncachedCounting, uncachedCounting, InlinedBranchProfile.getUncached(), InlinedBranchProfile.getUncached(), eqNode);
        }

        @Specialization(guards = "indexByteSize == getIndexByteSize(entryCapacity)", limit = "INDEX_BYTE_SIZE_CACHE_LIMIT")
        static void doPutWithRestart(Frame frame, Node inliningTarget, ObjectHashMap map, Object key, long keyHash, Object value,
                        @Bind("map.getEntryCapacity()") int entryCapacity,
                        @Cached(value = "getIndexByteSize(entryCapacity)", allowUncached = true) int indexByteSize,
                        @Cached InlinedBranchProfile lookupRestart,
                        @Cached InlinedCountingConditionProfile foundNullKey,
                        @Cached InlinedCountingConditionProfile foundEqKey,
                        @Cached InlinedCountingConditionProfile collisionFoundNoValue,
                        @Cached InlinedCountingConditionProfile collisionFoundEqKey,
                        @Cached InlinedBranchProfile rehash1Profile,
                        @Cached InlinedBranchProfile rehash2Profile,
                        @Cached PyObjectRichCompareBool eqNode) {
            while (true) {
                try {
                    doPut(frame, map, key, keyHash, value, entryCapacity, indexByteSize, inliningTarget, foundNullKey, foundEqKey,
                                    collisionFoundNoValue, collisionFoundEqKey, rehash1Profile, rehash2Profile,
                                    eqNode);
                    return;
                } catch (RestartLookupException ignore) {
                    lookupRestart.enter(inliningTarget);
                    TruffleSafepoint.poll(inliningTarget);
                    entryCapacity = map.getEntryCapacity();
                    indexByteSize = getIndexByteSizeAfterRestart(entryCapacity);
                }
            }
        }

        static void doPut(Frame frame, ObjectHashMap map, Object key, long keyHash, Object value, int entryCapacity, int indexByteSize,
                        Node inliningTarget,
                        InlinedCountingConditionProfile foundNullKey,
                        InlinedCountingConditionProfile foundEqKey,
                        InlinedCountingConditionProfile collisionFoundNoValue,
                        InlinedCountingConditionProfile collisionFoundEqKey,
                        InlinedBranchProfile rehash1Profile,
                        InlinedBranchProfile rehash2Profile,
                        PyObjectRichCompareBool eqNode) throws RestartLookupException {
            assert map.checkInternalState(entryCapacity, indexByteSize);
            byte[] metadata = map.metadata;
            int indicesLen = getBucketsCount(metadata, entryCapacity, indexByteSize);
            int indicesOffset = getIndicesOffset(entryCapacity);
            int physicalCollisionMask = getPhysicalCollisionMaskForIndexByteSize(indexByteSize);

            int compactIndex = getIndex(indicesLen, keyHash);
            int index = getIndex(metadata, indicesOffset, indexByteSize, compactIndex);
            if (foundNullKey.profile(inliningTarget, index == EMPTY_INDEX)) {
                map.putInNewSlot(metadata, indicesOffset, indexByteSize, physicalCollisionMask, entryCapacity, indicesLen, inliningTarget, rehash1Profile, key, keyHash, value, compactIndex);
                return;
            }

            if (foundEqKey.profile(inliningTarget, index != DUMMY_INDEX && map.keysEqual(metadata, frame, inliningTarget, unwrapIndex(index), key, keyHash, eqNode))) {
                // we found the key, override the value, Python does not override the key though
                map.setValue(unwrapIndex(index), value);
                return;
            }

            putCollision(frame, map, key, keyHash, value, inliningTarget, collisionFoundNoValue, collisionFoundEqKey, rehash2Profile, eqNode, metadata, indicesOffset, indexByteSize,
                            physicalCollisionMask, entryCapacity, indicesLen, compactIndex);
        }

        @InliningCutoff
        private static void putCollision(Frame frame, ObjectHashMap map, Object key, long keyHash, Object value, Node inliningTarget,
                        InlinedCountingConditionProfile collisionFoundNoValue, InlinedCountingConditionProfile collisionFoundEqKey,
                        InlinedBranchProfile rehash2Profile, PyObjectRichCompareBool eqNode,
                        byte[] metadata, int indicesOffset, int indexByteSize, int physicalCollisionMask, int entryCapacity, int indicesLen, int compactIndex)
                        throws RestartLookupException {
            markCollision(metadata, indicesOffset, indexByteSize, physicalCollisionMask, compactIndex);
            long perturb = keyHash;
            int searchLimit = indicesLen + PERTURB_SHIFTS_COUT;
            int i = 0;
            try {
                for (; i < searchLimit; i++) {
                    if (metadata != map.metadata) {
                        // guards against things happening in the safepoint on the backedge
                        throw RestartLookupException.INSTANCE;
                    }
                    perturb >>>= PERTURB_SHIFT;
                    compactIndex = nextIndex(indicesLen, compactIndex, perturb);
                    int index = getIndex(metadata, indicesOffset, indexByteSize, compactIndex);
                    if (collisionFoundNoValue.profile(inliningTarget, index == EMPTY_INDEX)) {
                        map.putInNewSlot(metadata, indicesOffset, indexByteSize, physicalCollisionMask, entryCapacity, indicesLen, inliningTarget, rehash2Profile, key, keyHash, value, compactIndex);
                        return;
                    }
                    if (collisionFoundEqKey.profile(inliningTarget, index != DUMMY_INDEX && map.keysEqual(metadata, frame, inliningTarget, unwrapIndex(index), key, keyHash, eqNode))) {
                        // we found the key, override the value, Python does not override the key
                        // though
                        map.setValue(unwrapIndex(index), value);
                        return;
                    }
                    markCollision(metadata, indicesOffset, indexByteSize, physicalCollisionMask, compactIndex);
                    TruffleSafepoint.poll(inliningTarget);
                }
            } finally {
                LoopNode.reportLoopCount(eqNode, i);
            }
            // all values are dummies? Not possible, since we should have compacted the
            // hashes/keysAndValues arrays in "remove". Also, there must be an unused slot
            // available, because we checked at the beginning that we have at least ~1/4 of space
            // left
            throw CompilerDirectives.shouldNotReachHere();
        }
    }

    // Internal helper: it is not profiling, never rehashes, and it assumes that the hash map never
    // contains the key that we are inserting
    private void insertNewKey(byte[] localMetadata, int indicesLen, int indicesOffset, int indexByteSize, int physicalCollisionMask, Object key, long keyHash, Object value) {
        assert localMetadata == this.metadata;
        int compactIndex = getIndex(indicesLen, keyHash);
        int index = getIndex(localMetadata, indicesOffset, indexByteSize, compactIndex);
        if (index == EMPTY_INDEX) {
            putInNewSlot(localMetadata, indicesOffset, indexByteSize, physicalCollisionMask, key, keyHash, value, compactIndex);
            return;
        }

        // collision
        markCollision(localMetadata, indicesOffset, indexByteSize, physicalCollisionMask, compactIndex);
        long perturb = keyHash;
        int searchLimit = indicesLen + PERTURB_SHIFTS_COUT;
        for (int i = 0; i < searchLimit; i++) {
            perturb >>>= PERTURB_SHIFT;
            compactIndex = nextIndex(indicesLen, compactIndex, perturb);
            index = getIndex(localMetadata, indicesOffset, indexByteSize, compactIndex);
            if (index == EMPTY_INDEX) {
                putInNewSlot(localMetadata, indicesOffset, indexByteSize, physicalCollisionMask, key, keyHash, value, compactIndex);
                return;
            }
            markCollision(localMetadata, indicesOffset, indexByteSize, physicalCollisionMask, compactIndex);
        }
        // all values are dummies? Not possible, since we should have compacted the
        // hashes/keysAndValues arrays in "remove". Also, there must be an unused slot available,
        // because we checked at the beginning that we have at least ~1/4 of space left
        throw CompilerDirectives.shouldNotReachHere();
    }

    private void putInNewSlot(byte[] localMetadata, int indicesOffset, int indexByteSize, int physicalCollisionMask, int entryCapacity, int bucketsCount, Node inliningTarget,
                    InlinedBranchProfile rehashProfile, Object key, long keyHash, Object value, int compactIndex) {
        assert metadata == localMetadata;
        assert entryCapacity == getEntryCapacity();
        if (CompilerDirectives.injectBranchProbability(SLOWPATH_PROBABILITY, needsResize(entryCapacity, bucketsCount))) {
            rehashProfile.enter(inliningTarget);
            rehashAndPut(key, keyHash, value);
            return;
        }
        putInNewSlot(localMetadata, indicesOffset, indexByteSize, physicalCollisionMask, key, keyHash, value, compactIndex);
    }

    private void putInNewSlot(byte[] localMetadata, int indicesOffset, int indexByteSize, int physicalCollisionMask, Object key, long keyHash, Object value, int compactIndex) {
        size++;
        usedIndices++;
        int newIndex = usedHashes++;
        setIndex(localMetadata, indicesOffset, indexByteSize, physicalCollisionMask, compactIndex, newIndex + INDEX_OFFSET);
        setValue(newIndex, value);
        setKey(newIndex, key);
        setHash(localMetadata, newIndex, keyHash);
    }

    private boolean needsCompaction(int entryCapacity) {
        // if more than quarter of all the slots are occupied by dummy values -> compact
        int quarterOfUsable = entryCapacity >> 2;
        int dummyCnt = usedHashes - size;
        return dummyCnt > quarterOfUsable;
    }

    @GenerateUncached
    @GenerateInline
    @GenerateCached(false)
    @ImportStatic(ObjectHashMap.class)
    public abstract static class RemoveNode extends Node {
        public static Object removeUncached(ObjectHashMap map, Object key, long keyHash) {
            return RemoveNodeGen.getUncached().execute(null, null, map, key, keyHash);
        }

        public abstract Object execute(Frame frame, Node inliningTarget, ObjectHashMap map, Object key, long keyHash);

        @TruffleBoundary
        public static Object doRemoveWithRestartForTests(ObjectHashMap map, Object key, long keyHash, PyObjectRichCompareBool eqNode) {
            // Public entry point for Java tests that bypass the generated node.
            int entryCapacity = map.getEntryCapacity();
            InlinedCountingConditionProfile uncachedCounting = InlinedCountingConditionProfile.getUncached();
            return doRemoveWithRestart(null, null, map, key, keyHash, entryCapacity, getIndexByteSize(entryCapacity), InlinedBranchProfile.getUncached(), uncachedCounting,
                            uncachedCounting, uncachedCounting, uncachedCounting, InlinedBranchProfile.getUncached(), eqNode);
        }

        @Specialization(guards = "indexByteSize == getIndexByteSize(entryCapacity)", limit = "INDEX_BYTE_SIZE_CACHE_LIMIT")
        static Object doRemoveWithRestart(Frame frame, Node inliningTarget, ObjectHashMap map, Object key, long keyHash,
                        @Bind("map.getEntryCapacity()") int entryCapacity,
                        @Cached(value = "getIndexByteSize(entryCapacity)", allowUncached = true) int indexByteSize,
                        @Cached InlinedBranchProfile lookupRestart,
                        @Cached InlinedCountingConditionProfile foundNullKey,
                        @Cached InlinedCountingConditionProfile foundEqKey,
                        @Cached InlinedCountingConditionProfile collisionFoundNoValue,
                        @Cached InlinedCountingConditionProfile collisionFoundEqKey,
                        @Cached InlinedBranchProfile compactProfile,
                        @Cached PyObjectRichCompareBool eqNode) {
            while (true) {
                try {
                    return doRemove(frame, inliningTarget, map, key, keyHash, entryCapacity, indexByteSize, foundNullKey, foundEqKey,
                                    collisionFoundNoValue, collisionFoundEqKey, compactProfile,
                                    eqNode);
                } catch (RestartLookupException ignore) {
                    lookupRestart.enter(inliningTarget);
                    entryCapacity = map.getEntryCapacity();
                    indexByteSize = getIndexByteSizeAfterRestart(entryCapacity);
                }
            }
        }

        static Object doRemove(Frame frame, Node inliningTarget, ObjectHashMap map, Object key, long keyHash, int entryCapacity, int indexByteSize,
                        InlinedCountingConditionProfile foundNullKey,
                        InlinedCountingConditionProfile foundEqKey,
                        InlinedCountingConditionProfile collisionFoundNoValue,
                        InlinedCountingConditionProfile collisionFoundEqKey,
                        InlinedBranchProfile compactProfile,
                        PyObjectRichCompareBool eqNode) throws RestartLookupException {
            assert map.checkInternalState(entryCapacity, indexByteSize);
            // TODO: move this to the point after we find the value to remove?
            if (CompilerDirectives.injectBranchProbability(SLOWPATH_PROBABILITY, map.needsCompaction(entryCapacity))) {
                compactProfile.enter(inliningTarget);
                map.compact();
            }
            byte[] metadata = map.metadata;
            int indicesLen = getBucketsCount(metadata, entryCapacity, indexByteSize);
            int indicesOffset = getIndicesOffset(entryCapacity);
            int physicalCollisionMask = getPhysicalCollisionMaskForIndexByteSize(indexByteSize);

            // Note: CPython is not shrinking the capacity of the hash table on delete, we do the
            // same
            int compactIndex = getIndex(indicesLen, keyHash);
            int index = getIndex(metadata, indicesOffset, indexByteSize, compactIndex);
            if (foundNullKey.profile(inliningTarget, index == EMPTY_INDEX)) {
                return null; // not found
            }

            int unwrappedIndex = unwrapIndex(index);
            if (foundEqKey.profile(inliningTarget, index != DUMMY_INDEX && map.keysEqual(metadata, frame, inliningTarget, unwrappedIndex, key, keyHash, eqNode))) {
                Object result = map.getValue(unwrappedIndex);
                setIndex(metadata, indicesOffset, indexByteSize, physicalCollisionMask, compactIndex, DUMMY_INDEX);
                map.setValue(unwrappedIndex, null);
                map.setKey(unwrappedIndex, null);
                map.size--;
                return result;
            }

            // collision: intentionally counted loop
            return removeCollision(frame, inliningTarget, map, key, keyHash, collisionFoundNoValue, collisionFoundEqKey, eqNode, metadata, indicesOffset, indexByteSize,
                            physicalCollisionMask, indicesLen, compactIndex);
        }

        @InliningCutoff
        private static Object removeCollision(Frame frame, Node inliningTarget, ObjectHashMap map, Object key, long keyHash,
                        InlinedCountingConditionProfile collisionFoundNoValue, InlinedCountingConditionProfile collisionFoundEqKey,
                        PyObjectRichCompareBool eqNode, byte[] metadata, int indicesOffset, int indexByteSize, int physicalCollisionMask, int indicesLen, int compactIndex)
                        throws RestartLookupException {
            int unwrappedIndex;
            long perturb = keyHash;
            int searchLimit = indicesLen + PERTURB_SHIFTS_COUT;
            int i = 0;
            try {
                for (; i < searchLimit; i++) {
                    if (metadata != map.metadata) {
                        // guards against things happening in the safepoint on the backedge
                        throw RestartLookupException.INSTANCE;
                    }
                    perturb >>>= PERTURB_SHIFT;
                    compactIndex = nextIndex(indicesLen, compactIndex, perturb);
                    int index = getIndex(metadata, indicesOffset, indexByteSize, compactIndex);
                    if (collisionFoundNoValue.profile(inliningTarget, index == EMPTY_INDEX)) {
                        return null;
                    }
                    unwrappedIndex = unwrapIndex(index);
                    if (collisionFoundEqKey.profile(inliningTarget, index != DUMMY_INDEX && map.keysEqual(metadata, frame, inliningTarget, unwrappedIndex, key, keyHash, eqNode))) {
                        Object result = map.getValue(unwrappedIndex);
                        setIndex(metadata, indicesOffset, indexByteSize, physicalCollisionMask, compactIndex, DUMMY_INDEX);
                        map.setValue(unwrappedIndex, null);
                        map.setKey(unwrappedIndex, null);
                        map.size--;
                        return result;
                    }
                }
            } finally {
                LoopNode.reportLoopCount(eqNode, i);
            }
            // all values are dummies? Not possible, since we should have compacted the
            // hashes/keysAndValues arrays at the top
            throw CompilerDirectives.shouldNotReachHere();
        }
    }

    private static final class RestartLookupException extends Exception {
        private static final long serialVersionUID = -5517471989238569331L;
        private static final RestartLookupException INSTANCE = new RestartLookupException();

        public RestartLookupException() {
            super(null, null);
        }

        @SuppressWarnings("sync-override")
        @Override
        public Throwable fillInStackTrace() {
            return this;
        }
    }

    private boolean keysEqual(byte[] originalMetadata, Frame frame, Node inliningTarget, int index, Object key, long keyHash,
                    PyObjectRichCompareBool eqNode) throws RestartLookupException {
        if (getHash(index) != keyHash) {
            return false;
        }
        Object originalKey = getKey(index);
        if (originalKey == key) {
            return true;
        }
        boolean result = eqNode.executeEq(frame, inliningTarget, originalKey, key);
        if (metadata != originalMetadata || getKey(index) != originalKey) {
            // Either someone overridden the slot we are just examining, or rehasing reallocated the
            // indices array. We need to restart the lookup. Other situations are OK:
            //
            // New entry was added: if its key is different to what we look for we don't care, if
            // its key collides with what we look for, it will be put at the end of the collision
            // chain, and we'll find it.
            //
            // Entry was removed: if it was not the entry we're looking at right now, we don't care.
            // Removal could have triggered a compaction, which shuffles things around in the arrays
            // (hashes, keysAndValues), but does not reallocate the arrays or changes collision
            // sequences.
            throw RestartLookupException.INSTANCE;
        }
        return result;
    }

    /**
     * Called when we need space for new entry. It determines the new size from the number of slots
     * occupied by real values (i.e., does not count dummy entries), so the new size may be actually
     * smaller than the old size if there were many dummy entries. The rehashing also removes the
     * dummy entries.
     */
    @TruffleBoundary
    private void rehashAndPut(Object newKey, long newKeyHash, Object newValue) {
        int newSize = size + 1;
        int indicesCapacity = getMinBucketsCount(newSize);
        byte[] oldMetadata = metadata;
        Object[] oldKeysAndValues = keysAndValues;
        int oldUsedSize = usedHashes;
        int oldSize = size;
        allocateData(indicesCapacity, getRequestedEntryCapacity(newSize, indicesCapacity));
        size = 0;
        usedHashes = 0;
        usedIndices = 0;
        byte[] localMetadata = this.metadata;
        int entryCapacity = getEntryCapacity();
        int indexByteSize = getIndexByteSize(entryCapacity);
        int indicesLen = getBucketsCount(localMetadata, entryCapacity, indexByteSize);
        int indicesOffset = getIndicesOffset(entryCapacity);
        int physicalCollisionMask = getPhysicalCollisionMaskForIndexByteSize(indexByteSize);
        for (int i = 0; i < oldUsedSize; i++) {
            if (getValue(i, oldKeysAndValues) != null) {
                final Object key = getKey(i, oldKeysAndValues);
                insertNewKey(localMetadata, indicesLen, indicesOffset, indexByteSize, physicalCollisionMask, key, getHash(oldMetadata, i), getValue(i, oldKeysAndValues));
            }
        }
        assert size == oldSize : String.format("size=%d, oldSize=%d, oldUsedSize=%d, usedHashes=%d, usedIndices=%d",
                        size, oldSize, oldUsedSize, usedHashes, usedIndices);
        insertNewKey(localMetadata, indicesLen, indicesOffset, indexByteSize, physicalCollisionMask, newKey, newKeyHash, newValue);
    }

    private static int getRequestedEntryCapacity(int requestedCapacity, int bucketsCount) {
        if (requestedCapacity <= TIGHT_ENTRY_CAPACITY_LIMIT) {
            return requestedCapacity;
        }
        return getUsableSize(bucketsCount);
    }

    @TruffleBoundary
    private void compact() {
        // shuffle[X] will tell us by how much value X found in 'indices' should be shuffled to left
        int[] shuffle = new int[getEntryCapacity()];
        int currentShuffle = 0;
        int dummyCount = 0;
        for (int i = 0; i < usedHashes; i++) {
            Object value = getValue(i);
            if (value == null) {
                currentShuffle++;
                dummyCount++;
            } else if (currentShuffle > 0) {
                assert getValue(i - currentShuffle) == null;
                assert getKey(i - currentShuffle) == null;
                setValue(i - currentShuffle, value);
                setKey(i - currentShuffle, getKey(i));
                setValue(i, null);
                setKey(i, null);
                setHash(metadata, i - currentShuffle, getHash(i));
                shuffle[i] = currentShuffle;
            }
        }
        usedHashes -= dummyCount; // We've "removed" the dummy entries
        byte[] localMetadata = metadata;
        int entryCapacity = getEntryCapacity();
        int indexByteSize = getIndexByteSize(entryCapacity);
        int localIndicesLength = getBucketsCount(localMetadata, entryCapacity, indexByteSize);
        int indicesOffset = getIndicesOffset(entryCapacity);
        int physicalCollisionMask = getPhysicalCollisionMaskForIndexByteSize(indexByteSize);
        for (int i = 0; i < localIndicesLength; i++) {
            int index = getIndex(localMetadata, indicesOffset, indexByteSize, i);
            if (index != EMPTY_INDEX && index != DUMMY_INDEX) {
                boolean collision = isCollision(index);
                int unwrapped = unwrapIndex(index);
                int newIndex = unwrapped - shuffle[unwrapped];
                if (collision) {
                    setIndex(localMetadata, indicesOffset, indexByteSize, physicalCollisionMask, i, newIndex + INDEX_OFFSET | COLLISION_MASK);
                } else {
                    setIndex(localMetadata, indicesOffset, indexByteSize, physicalCollisionMask, i, newIndex + INDEX_OFFSET);
                }
            } else if (index == DUMMY_INDEX) {
                dummyCount--;
            }
        }
        // Indices may contain dummy values removed from hashes and keysAndValues arrays in some
        // previous rounds of compaction, but we should have seen at least this many dummy values
        assert dummyCount <= 0;
    }

    private static int nextIndex(int indicesLen, int i, long perturb) {
        return getIndex(indicesLen, i * 5L + perturb + 1L);
    }

    private static int getIndex(int indicesLen, long hash) {
        // since buckets count is power of 2, the & works as modulo
        return (int) (hash & (indicesLen - 1));
    }

    private static int getUsableSize(int bucketsCount) {
        int minFreeBuckets = Math.max(2, bucketsCount >> 2);
        return bucketsCount - minFreeBuckets + 1;
    }

    private static int getMinBucketsCount(int requiredEntries) {
        int bucketsCount = INITIAL_INDICES_SIZE;
        while (getUsableSize(bucketsCount) < requiredEntries) {
            if (bucketsCount > Integer.MAX_VALUE >> 1) {
                // The backing arrays cannot grow past Java array indexing limits. The exact packed
                // metadata bound is checked in allocateData.
                throw new OutOfMemoryError();
            }
            bucketsCount <<= 1;
        }
        return bucketsCount;
    }

    public static Object getKey(int index, Object[] keysAndValues) {
        return keysAndValues[index << 1];
    }

    public static Object getValue(int index, Object[] keysAndValues) {
        return keysAndValues[(index << 1) + 1];
    }

    public Object getKey(int index) {
        return getKey(index, keysAndValues);
    }

    public Object getValue(int index) {
        return getValue(index, keysAndValues);
    }

    public void setValue(int index, Object value) {
        keysAndValues[(index << 1) + 1] = value;
    }

    public void setKey(int index, Object key) {
        keysAndValues[(index << 1)] = key;
    }

    private boolean checkInternalState(int entryCapacity, int indexByteSize) {
        // We must have at least one empty slot, collision resolution relies on the fact that it is
        // always going to find an empty slot
        assert usedIndices < getBucketsCount(metadata, entryCapacity, indexByteSize) : usedIndices;
        return true;
    }

    private static boolean isPow2(int n) {
        return Integer.bitCount(n) == 1;
    }
}
