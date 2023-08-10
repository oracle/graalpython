/*
 * Copyright (c) 2020, 2023, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.test.objects;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Random;
import java.util.stream.Collectors;

import org.junit.Assert;
import org.junit.Test;

import com.oracle.graal.python.builtins.objects.common.EconomicMapStorage;
import com.oracle.graal.python.builtins.objects.common.HashingStorage;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodes.HashingStorageForEach;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodes.HashingStorageForEachCallback;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodes.HashingStorageGetIterator;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodes.HashingStorageGetReverseIterator;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodes.HashingStorageIterator;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodes.HashingStorageIteratorKey;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodes.HashingStorageIteratorNext;
import com.oracle.graal.python.builtins.objects.common.ObjectHashMap;
import com.oracle.graal.python.builtins.objects.common.ObjectHashMap.MapCursor;
import com.oracle.graal.python.lib.PyObjectHashNode;
import com.oracle.graal.python.lib.PyObjectRichCompareBool;
import com.oracle.graal.python.lib.PyObjectRichCompareBool.Comparison;
import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.profiles.InlinedBranchProfile;
import com.oracle.truffle.api.profiles.InlinedCountingConditionProfile;

public class ObjectHashMapTests {
    public static final class DictKey implements TruffleObject {
        final long hash;

        DictKey(long hash) {
            this.hash = hash;
        }
    }

    private static final class EqNodeStub extends PyObjectRichCompareBool.EqNode {
        @Override
        protected boolean execute(Frame frame, Node inliningTarget, Object a, Object b, Comparison cmp) {
            // Sanity check: we do not use any other keys in the tests
            assert a instanceof Long || a instanceof DictKey;
            assert b instanceof Long || b instanceof DictKey;
            // the hashmap should never call __eq__ unless the hashes match
            assertEquals("keys: " + a + ", " + b, getKeyHash(a), getKeyHash(b));
            return a.equals(b);
        }
    }

    @Test
    public void testCollisionsByPuttingManyKeysWithSameHash() {
        ObjectHashMap map = new ObjectHashMap();
        LinkedHashMap<DictKey, Object> expected = new LinkedHashMap<>();
        for (int i = 0; i < 100; i++) {
            DictKey key = new DictKey(42);
            Object value = newValue();
            expected.put(key, value);
            put(map, key, 42, value);
            assertEqual(i, expected, map);
        }
        for (int i = 0; i < 55; i++) {
            DictKey key = expected.keySet().stream().skip(22).findFirst().get();
            expected.remove(key);
            remove(map, key, 42);
            assertEqual(i, expected, map);
        }
        for (int i = 0; i < 10; i++) {
            DictKey key = expected.keySet().stream().skip(10 + i).findFirst().get();
            Object value = newValue();
            expected.put(key, value);
            put(map, key, 42, value);
            assertEqual(i, expected, map);
        }
    }

    @Test
    public void testCollisionsByPuttingAndRemovingTheSameKey() {
        ObjectHashMap map = new ObjectHashMap();
        LinkedHashMap<DictKey, Object> expected = new LinkedHashMap<>();
        DictKey key = new DictKey(42);
        for (int i = 0; i < 100; i++) {
            Object value = newValue();
            put(map, key, 42, value);
            expected.put(key, value);
            assertEqual(i, expected, map);

            remove(map, key, 42);
            expected.remove(key);
            assertEqual(i, expected, map);
        }
    }

    @Test
    public void testCollisionsByPuttingAndRemovingTheSameKeys() {
        ObjectHashMap map = new ObjectHashMap();
        LinkedHashMap<DictKey, Object> expected = new LinkedHashMap<>();
        DictKey[] keys = new DictKey[]{new DictKey(42), new DictKey(1)};
        for (int i = 0; i < 100; i++) {
            Object value = newValue();
            final DictKey toPut = keys[i % keys.length];
            put(map, toPut, toPut.hash, value);
            expected.put(toPut, value);
            assertEqual(i, expected, map);

            final DictKey toRemove = keys[(i + 1) % keys.length];
            remove(map, toRemove, toRemove.hash);
            expected.remove(toRemove);
            assertEqual(i, expected, map);
        }
    }

    @Test
    public void testLongHashMapStressTest() {
        ObjectHashMap map = new ObjectHashMap();

        // put/remove many random (with fixed seed) keys, check consistency against LinkedHashMap
        testBasics(map);
        removeAll(map);
        testBasics(map);

        // Basic tests of other methods
        Object[] oldKeys = keysToArray(map);

        ObjectHashMap copy = map.copy();
        assertEquals(map.size(), copy.size());
        for (Object key : oldKeys) {
            assertEquals(key.toString(), //
                            get(map, key, getKeyHash(key)), //
                            get(copy, key, getKeyHash(key)));
        }

        map.clear();
        assertEquals(0, map.size());
        for (Object key : oldKeys) {
            assertNull(key.toString(), get(map, key, getKeyHash(key)));
        }
    }

    private static void testBasics(ObjectHashMap map) {
        LinkedHashMap<Long, Object> expected = new LinkedHashMap<>();
        Random rand = new Random(42);

        putValues(map, expected, rand, 100);
        removeValues(map, expected, rand, 33);
        putValues(map, expected, rand, 44);
        overrideValues(map, expected, rand, 55);
        removeValues(map, expected, rand, 66);
        overrideValues(map, expected, rand, 11);
        putValues(map, expected, rand, 22);

        removeAll(map, expected);

        putValues(map, expected, rand, 50);
        removeValues(map, expected, rand, 33);
        overrideValues(map, expected, rand, 10);

        putValues(map, expected, rand, 300);
        removeValues(map, expected, rand, 10);
        overrideValues(map, expected, rand, 10);
    }

    private static void removeAll(ObjectHashMap map) {
        ArrayList<Long> keys = new ArrayList<>();
        for (Object key : keysToArray(map)) {
            keys.add((Long) key);
        }
        for (Long key : keys) {
            remove(map, key, PyObjectHashNode.hash(key));
            assertNull(get(map, key, PyObjectHashNode.hash(key)));
        }
    }

    private static void removeAll(ObjectHashMap map, LinkedHashMap<Long, Object> expected) {
        for (Long key : expected.keySet().toArray(new Long[0])) {
            remove(map, key, PyObjectHashNode.hash(key));
            expected.remove(key);
            assertEqual(Long.toString(key), expected, map);
        }
    }

    private static void removeValues(ObjectHashMap map, LinkedHashMap<Long, Object> expected, Random rand, int count) {
        for (int i = 0; i < count; i++) {
            int index = rand.nextInt(expected.size() - 1);
            long key = expected.keySet().stream().skip(index).findFirst().get();
            remove(map, key, PyObjectHashNode.hash(key));
            expected.remove(key);
            assertEqual(i, expected, map);
        }
    }

    private static void overrideValues(ObjectHashMap map, LinkedHashMap<Long, Object> expected, Random rand, int count) {
        for (int i = 0; i < count; i++) {
            Object value = newValue();
            int index = rand.nextInt(expected.size() - 1);
            long key = expected.keySet().stream().skip(index).findFirst().get();
            put(map, key, PyObjectHashNode.hash(key), value);
            expected.put(key, value);
            assertEqual(i, expected, map);
        }
    }

    private static void putValues(ObjectHashMap map, LinkedHashMap<Long, Object> expected, Random rand, int count) {
        for (int i = 0; i < count; i++) {
            Object value = newValue();
            long key = rand.nextLong();
            put(map, key, PyObjectHashNode.hash(key), value);
            expected.put(key, value);
            assertEqual(i, expected, map);
        }
    }

    static <T> void assertEqual(int iter, LinkedHashMap<T, Object> expected, ObjectHashMap actual) {
        assertEqual(Integer.toString(iter), expected, actual);
    }

    static <T> void assertEqual(String message, LinkedHashMap<T, Object> expected, ObjectHashMap actual) {
        assertEquals(message, expected.size(), actual.size());

        // Check getEntries and build array of keys/values
        MapCursor it = actual.getEntries();
        ArrayList<ObjectHashMap.DictKey> keys = new ArrayList<>();
        ArrayList<Object> valuesList = new ArrayList<>();
        for (T key : expected.keySet()) {
            Assert.assertTrue(message + "; the actual is shorter ", it.advance());

            assertEquals(message, key, it.getKey().getValue());
            long hash = getKeyHash(key);
            assertEquals(message + "; hash in DictKey: " + key, hash, it.getKey().getPythonHash());

            Object expectedVal = expected.get(key);
            Object actualVal = get(actual, key, hash);
            assertEquals(message + "; value under key: " + key, expectedVal, actualVal);
            assertEquals(message + "; value in DictKey: " + key, expectedVal, it.getValue());

            keys.add(it.getKey());
            valuesList.add(it.getValue());
        }
        Assert.assertFalse(message + "; the actual is longer", it.advance());

        // Using the array of keys/values, check other methods
        List<Object> keysValues = keys.stream().map(ObjectHashMap.DictKey::getValue).collect(Collectors.toList());
        assertArrayEquals(message, keysValues.toArray(), keysToArray(actual));

        List<Object> keysValuesReversed = new ArrayList<>(keysValues);
        Collections.reverse(keysValuesReversed);
        assertArrayEquals(message, keysValuesReversed.toArray(), reverseKeysToArray(actual));

        EconomicMapStorage storage = new EconomicMapStorage(actual, false);
        int[] size = new int[]{0};
        HashingStorageForEach.executeUncached(storage, new HashingStorageForEachCallback<>() {
            @Override
            public Object execute(Frame frame, Node inliningTarget, HashingStorage s, HashingStorageIterator cbIt, Object accumulator) {
                Object key = HashingStorageIteratorKey.executeUncached(s, cbIt);
                assertTrue(key.toString(), expected.containsKey(key));
                size[0]++;
                return null;
            }
        }, null);
        assertEquals(expected.size(), size[0]);
    }

    private static Object[] keysToArray(ObjectHashMap m) {
        EconomicMapStorage s = new EconomicMapStorage(m, false);
        return iteratorToArray(s, HashingStorageGetIterator.executeUncached(s));
    }

    private static Object[] reverseKeysToArray(ObjectHashMap m) {
        EconomicMapStorage s = new EconomicMapStorage(m, false);
        return iteratorToArray(s, HashingStorageGetReverseIterator.executeUncached(s));
    }

    private static Object[] iteratorToArray(HashingStorage s, HashingStorageIterator it) {
        ArrayList<Object> result = new ArrayList<>();
        while (HashingStorageIteratorNext.executeUncached(s, it)) {
            result.add(HashingStorageIteratorKey.executeUncached(s, it));
        }
        return result.toArray();
    }

    private static int valueCounter = 0;

    public static Object newValue() {
        return "Val: " + (valueCounter++);
    }

    private static long getKeyHash(Object key) {
        return key instanceof Long ? PyObjectHashNode.hash((Long) key) : ((DictKey) key).hash;
    }

    private static Object get(ObjectHashMap map, Object key, long hash) {
        InlinedCountingConditionProfile uncachedCounting = InlinedCountingConditionProfile.getUncached();
        return ObjectHashMap.GetNode.doGetWithRestart(null, null, map, key, hash,
                        InlinedBranchProfile.getUncached(), uncachedCounting, uncachedCounting, uncachedCounting,
                        uncachedCounting, uncachedCounting,
                        new EqNodeStub());
    }

    private static void remove(ObjectHashMap map, Object key, long hash) {
        InlinedCountingConditionProfile uncachedCounting = InlinedCountingConditionProfile.getUncached();
        ObjectHashMap.RemoveNode.doRemoveWithRestart(null, null, map, key, hash,
                        InlinedBranchProfile.getUncached(), uncachedCounting, uncachedCounting, uncachedCounting,
                        uncachedCounting, InlinedBranchProfile.getUncached(), new EqNodeStub());
    }

    private static void put(ObjectHashMap map, Object key, long hash, Object value) {
        InlinedCountingConditionProfile uncachedCounting = InlinedCountingConditionProfile.getUncached();
        ObjectHashMap.PutNode.doPutWithRestart(null, null, map, key, hash, value,
                        InlinedBranchProfile.getUncached(), uncachedCounting, uncachedCounting, uncachedCounting,
                        uncachedCounting, InlinedBranchProfile.getUncached(), InlinedBranchProfile.getUncached(),
                        new EqNodeStub());
    }
}
