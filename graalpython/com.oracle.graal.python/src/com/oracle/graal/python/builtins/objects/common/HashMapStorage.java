/*
 * Copyright (c) 2018, Oracle and/or its affiliates.
 *
 * The Universal Permissive License (UPL), Version 1.0
 *
 * Subject to the condition set forth below, permission is hereby granted to any
 * person obtaining a copy of this software, associated documentation and/or data
 * (collectively the "Software"), free of charge and under any and all copyright
 * rights in the Software, and any and all patent rights owned or freely
 * licensable by each licensor hereunder covering either (i) the unmodified
 * Software as contributed to or provided by such licensor, or (ii) the Larger
 * Works (as defined below), to deal in both
 *
 * (a) the Software, and
 * (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
 *     one is included with the Software (each a "Larger Work" to which the
 *     Software is contributed by such licensors),
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

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;

import com.oracle.graal.python.builtins.objects.object.PythonObject;
import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;

public class HashMapStorage extends HashingStorage {

    private final HashMap<Object, Object> map;

    @TruffleBoundary
    public HashMapStorage(Map<? extends Object, ? extends Object> map) {
        this.map = new HashMap<>(map);
    }

    @TruffleBoundary
    public HashMapStorage() {
        map = new HashMap<>();
    }

    @Override
    @TruffleBoundary
    public void addAll(HashingStorage other, Equivalence eq) {
        for (DictEntry e : other.entries()) {
            map.put(wrap(e.getKey(), eq), e.getValue());
        }
    }

    @Override
    @TruffleBoundary
    public Object getItem(Object key, Equivalence eq) {
        return map.get(wrap(key, eq));
    }

    private static Object wrap(Object key, Equivalence eq) {
        if (key instanceof PythonObject) {
            return new KeyWrapper(eq, key);
        }
        return key;
    }

    private static Object unwrap(Object key) {
        if (key instanceof KeyWrapper) {
            return ((KeyWrapper) key).key;
        }
        return key;
    }

    @Override
    @TruffleBoundary
    public void setItem(Object key, Object value, Equivalence eq) {
        map.put(wrap(key, eq), value);
    }

    @TruffleBoundary
    public Iterable<Object> items() {
        return wrapJavaIterable(map.values());
    }

    @Override
    public Iterable<Object> keys() {
        ArrayList<Object> keys = new ArrayList<>(map.size());
        for (Object key : map.keySet()) {
            keys.add(unwrap(key));
        }
        return wrapJavaIterable(keys);
    }

    public Map<Object, Object> getStore() {
        return map;
    }

    @Override
    public String toString() {
        CompilerAsserts.neverPartOfCompilation();
        StringBuilder buf = new StringBuilder("{");
        int length = map.size();
        int i = 0;

        for (Entry<?, ?> entry : map.entrySet()) {
            buf.append(entry.getKey() + ": " + entry.getValue());

            if (i < length - 1) {
                buf.append(", ");
            }

            i++;
        }

        buf.append("}");
        return buf.toString();
    }

    @Override
    public int length() {
        return map.size();
    }

    @Override
    @TruffleBoundary
    public boolean remove(Object key, Equivalence eq) {
        return map.remove(wrap(key, eq)) != null;
    }

    @Override
    @TruffleBoundary
    public Iterable<Object> values() {
        return wrapJavaIterable(map.values());
    }

    @Override
    @TruffleBoundary
    public Collection<DictEntry> entries() {
        ArrayList<DictEntry> entries = new ArrayList<>(map.size());
        for (Map.Entry<Object, Object> entry : map.entrySet()) {
            entries.add(new DictEntry(entry.getKey(), entry.getValue()));
        }
        return entries;
    }

    private static class KeyWrapper {
        private final Equivalence eq;
        private final Object key;
        private final long hash;

        public KeyWrapper(Equivalence eq, Object key2) {
            this.eq = eq;
            this.key = key2;
            this.hash = eq.hashCode(key2);
        }

        @Override
        public int hashCode() {
            return (int) hash;
        }

        @Override
        public boolean equals(Object o) {
            if (o instanceof KeyWrapper) {
                KeyWrapper other = (KeyWrapper) o;
                return eq.equals(key, other.key);
            }
            return false;
        }

    }

    @Override
    @TruffleBoundary
    public void clear() {
        map.clear();
    }

    @Override
    @TruffleBoundary
    public HashingStorage copy(Equivalence eq) {
        return new HashMapStorage(map);
    }

    @Override
    @TruffleBoundary
    public boolean hasKey(Object key, Equivalence eq) {
        return map.containsKey(wrap(key, eq));
    }

}
