/*
 * Copyright (c) 2022, 2026, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.test.builtin.objects.dict;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.common.DynamicObjectStorage;
import com.oracle.graal.python.builtins.objects.common.EconomicMapStorage;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodes.HashingStorageDelItem;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodes.HashingStorageLen;
import com.oracle.graal.python.builtins.objects.dict.PDict;
import com.oracle.graal.python.builtins.objects.object.PythonObject;
import com.oracle.graal.python.nodes.attributes.WriteAttributeToObjectNode;
import com.oracle.graal.python.nodes.attributes.WriteAttributeToPythonObjectNode;
import com.oracle.graal.python.nodes.object.GetOrCreateDictNode;
import com.oracle.graal.python.runtime.object.PFactory;
import com.oracle.graal.python.test.PythonTests;
import com.oracle.truffle.api.strings.TruffleString;

public class PDictTest {

    @Before
    public void setUp() {
        PythonTests.enterContext();
    }

    @After
    public void tearDown() {
        PythonTests.closeContext();
    }

    static TruffleString ts(String s) {
        return TruffleString.fromJavaStringUncached(s, TruffleString.Encoding.UTF_8);
    }

    static void delItem(PDict dict, Object key) {
        HashingStorageDelItem.executeUncached(dict.getDictStorage(), key, dict);
    }

    static int length(PDict dict) {
        return HashingStorageLen.executeUncached(dict.getDictStorage());
    }

    @Test
    public void dynamicStorageAttributeWrites() {
        PythonObject object = PFactory.createSimpleNamespace(PythonLanguage.get(null));
        WriteAttributeToPythonObjectNode.executeUncached(object, ts("key"), 1);
        PDict dict = GetOrCreateDictNode.executeUncached(object);
        assertEquals(1, length(dict));
        WriteAttributeToObjectNode.getUncached().execute(object, ts("key"), PNone.NO_VALUE);
        assertEquals(0, length(dict));
        WriteAttributeToObjectNode.getUncached().execute(object, ts("key"), 2);
        assertEquals(1, length(dict));
        WriteAttributeToObjectNode.getUncached().execute(object, ts("key"), PNone.NO_VALUE);
        assertEquals(0, length(dict));
    }

    @Test(expected = AssertionError.class)
    public void directAttributeWriteRejectsBackingDict() {
        PythonObject object = PFactory.createSimpleNamespace(PythonLanguage.get(null));
        GetOrCreateDictNode.executeUncached(object);
        WriteAttributeToPythonObjectNode.executeUncached(object, ts("key"), 1);
    }

    @Test(expected = AssertionError.class)
    public void dynamicStorageDetectsStaleTemporaryWrapper() {
        PythonObject object = PFactory.createSimpleNamespace(PythonLanguage.get(null));
        DynamicObjectStorage storage = new DynamicObjectStorage(object);
        assertEquals(0, HashingStorageLen.executeUncached(storage));
        WriteAttributeToObjectNode.getUncached().execute(object, ts("key"), 1);
        HashingStorageLen.executeUncached(storage);
    }

    @Test(expected = AssertionError.class)
    public void dynamicStorageDetectsStaleTemporaryWrapperWithDict() {
        PythonObject object = PFactory.createSimpleNamespace(PythonLanguage.get(null));
        PDict dict = GetOrCreateDictNode.executeUncached(object);
        DynamicObjectStorage storage = new DynamicObjectStorage(object);
        assertEquals(0, HashingStorageLen.executeUncached(storage));
        assertEquals(0, length(dict));
        WriteAttributeToObjectNode.getUncached().execute(object, ts("key"), 1);
        assertEquals(1, length(dict));
        HashingStorageLen.executeUncached(storage);
    }

    @Test
    public void economicMapStorageTransition() {
        PDict dict = PFactory.createDict(PythonLanguage.get(null));
        dict.setItem(ts("key1"), 42);
        dict.setItem(11, ts("abc"));
        assertTrue(dict.getDictStorage() instanceof EconomicMapStorage);
        assertEquals(2, length(dict));
    }

    @Test
    public void economicMapStorageSet() {
        PDict dict = PFactory.createDict(PythonLanguage.get(null));
        dict.setItem(11, ts("abc"));
        assertTrue(dict.getDictStorage() instanceof EconomicMapStorage);

        dict.setItem(ts("key1"), 42);
        assertEquals(2, length(dict));
        assertTrue(dict.getDictStorage() instanceof EconomicMapStorage);

        assertEquals(42, dict.getItem(ts("key1")));
    }

    @Test
    public void economicMapStorageDel() {
        PDict dict = PFactory.createDict(PythonLanguage.get(null));
        dict.setItem(11, ts("abc"));
        dict.setItem(ts("key1"), 42);
        dict.setItem(ts("key2"), 24);
        assertTrue(dict.getDictStorage() instanceof EconomicMapStorage);
        assertEquals(3, length(dict));

        delItem(dict, ts("key2"));
        assertEquals(2, length(dict));
        assertTrue(dict.getDictStorage() instanceof EconomicMapStorage);

        assertEquals(42, dict.getItem(ts("key1")));

        assertNull(dict.getItem(ts("key2")));
    }

    @Test
    public void economicMapStorageEightEntries() {
        PDict dict = PFactory.createDict(PythonLanguage.get(null));
        for (int i = 0; i < 8; i++) {
            dict.setItem(i, i);
        }
        assertTrue(dict.getDictStorage() instanceof EconomicMapStorage);
        assertEquals(8, length(dict));
    }
}
