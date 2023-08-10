/*
 * Copyright (c) 2018, 2023, Oracle and/or its affiliates. All rights reserved.
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

import static com.oracle.graal.python.util.PythonUtils.TS_ENCODING;

import java.util.LinkedHashMap;
import java.util.Map.Entry;

import com.oracle.graal.python.builtins.objects.common.HashingStorageNodes.SpecializedSetStringKey;
import com.oracle.graal.python.builtins.objects.common.ObjectHashMap.DictKey;
import com.oracle.graal.python.builtins.objects.common.ObjectHashMap.MapCursor;
import com.oracle.graal.python.builtins.objects.common.ObjectHashMap.PutNode;
import com.oracle.graal.python.lib.PyObjectHashNode;
import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateCached;
import com.oracle.truffle.api.dsl.GenerateInline;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.LoopNode;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.profiles.InlinedLoopConditionProfile;
import com.oracle.truffle.api.strings.TruffleString;
import com.oracle.truffle.api.strings.TruffleString.HashCodeNode;

public class EconomicMapStorage extends HashingStorage {

    public static EconomicMapStorage create() {
        return new EconomicMapStorage();
    }

    public static EconomicMapStorage createWithSideEffects() {
        return new EconomicMapStorage(4, true);
    }

    public static EconomicMapStorage create(int initialCapacity) {
        return new EconomicMapStorage(initialCapacity, false);
    }

    final ObjectHashMap map;

    private EconomicMapStorage(int initialCapacity, boolean hasSideEffects) {
        this.map = new ObjectHashMap(initialCapacity, hasSideEffects);
    }

    private EconomicMapStorage() {
        this(4, false);
    }

    public EconomicMapStorage(ObjectHashMap original, boolean copy) {
        this.map = copy ? original.copy() : original;
    }

    @TruffleBoundary
    public static EconomicMapStorage create(LinkedHashMap<String, Object> map) {
        EconomicMapStorage result = new EconomicMapStorage(map.size(), false);
        putAllUncached(map, result);
        return result;
    }

    public static EconomicMapStorage createGeneric(LinkedHashMap<Object, Object> map) {
        CompilerAsserts.neverPartOfCompilation();
        EconomicMapStorage result = new EconomicMapStorage(map.size(), false);
        putAllUncachedGeneric(map, result);
        return result;
    }

    public int length() {
        return map.size();
    }

    static boolean advance(MapCursor cursor) {
        return cursor.advance();
    }

    static DictKey getDictKey(MapCursor cursor) {
        return cursor.getKey();
    }

    static Object getKey(MapCursor cursor) {
        return getDictKey(cursor).getValue();
    }

    static Object getValue(MapCursor cursor) {
        return cursor.getValue();
    }

    void clear() {
        map.clear();
    }

    public HashingStorage copy() {
        return new EconomicMapStorage(this.map, true);
    }

    protected void setValueForAllKeys(VirtualFrame frame, Node inliningTarget, Object value, PutNode putNode, InlinedLoopConditionProfile loopProfile) {
        MapCursor cursor = map.getEntries();
        final int size = map.size();
        loopProfile.profileCounted(inliningTarget, size);
        LoopNode.reportLoopCount(putNode, size);
        while (loopProfile.inject(inliningTarget, advance(cursor))) {
            putNode.put(frame, inliningTarget, map, getDictKey(cursor), value);
        }
    }

    @TruffleBoundary
    public void putUncached(TruffleString key, Object value) {
        ObjectHashMap.PutNode.putUncached(this.map, key, PyObjectHashNode.hash(key, HashCodeNode.getUncached()), value);
    }

    private void putUncached(Object key, Object value) {
        ObjectHashMap.PutNode.putUncached(this.map, key, PyObjectHashNode.executeUncached(key), value);
    }

    private static void putAllUncached(LinkedHashMap<String, Object> map, EconomicMapStorage result) {
        CompilerAsserts.neverPartOfCompilation();
        for (Entry<String, Object> entry : map.entrySet()) {
            result.putUncached(TruffleString.fromJavaStringUncached(entry.getKey(), TS_ENCODING), entry.getValue());
        }
    }

    private static void putAllUncachedGeneric(LinkedHashMap<Object, Object> map, EconomicMapStorage result) {
        CompilerAsserts.neverPartOfCompilation();
        for (Entry<Object, Object> entry : map.entrySet()) {
            result.putUncached(entry.getKey(), entry.getValue());
        }
    }

    @Override
    public String toString() {
        CompilerAsserts.neverPartOfCompilation();
        StringBuilder builder = new StringBuilder();
        builder.append("map(size=").append(length()).append(", {");
        String sep = "";
        MapCursor cursor = map.getEntries();
        int i = 0;
        while (advance(cursor)) {
            i++;
            if (i >= 100) {
                builder.append("...");
                break;
            }
            builder.append(sep);
            builder.append("(").append(getKey(cursor)).append(",").append(getValue(cursor)).append(")");
            sep = ",";
        }
        builder.append("})");
        return builder.toString();
    }

    @GenerateUncached
    @GenerateInline
    @GenerateCached(false)
    public abstract static class EconomicMapSetStringKey extends SpecializedSetStringKey {
        @Specialization
        static void doIt(Node inliningTarget, HashingStorage self, TruffleString key, Object value,
                        @Cached PyObjectHashNode hashNode,
                        @Cached PutNode putNode) {
            putNode.put(null, inliningTarget, ((EconomicMapStorage) self).map, key, hashNode.execute(null, inliningTarget, key), value);
        }
    }
}
