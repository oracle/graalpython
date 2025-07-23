/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.test.integration;

import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.NoSuchElementException;

import org.graalvm.polyglot.Value;
import org.graalvm.polyglot.proxy.ProxyArray;
import org.graalvm.polyglot.proxy.ProxyHashMap;
import org.graalvm.polyglot.proxy.ProxyIterator;
import org.graalvm.polyglot.proxy.ProxyObject;

// This is just a mock for tests, the real API is exposed in the org.graalvm.python.embedding library
public final class KeywordArgumentsMock implements ProxyHashMap, ProxyObject {

    public static final String MEMBER_KEY = "org.graalvm.python.embedding.KeywordArguments.is_keyword_arguments";
    private final Map<String, Object> kwArgs;

    public KeywordArgumentsMock(Map<String, Object> kwArgs) {
        this.kwArgs = kwArgs;
    }

    public static KeywordArgumentsMock from(Map<String, Object> kwArgs) {
        return new KeywordArgumentsMock(kwArgs);
    }

    @Override
    public Object getMember(String key) throws UnsupportedOperationException {
        if (MEMBER_KEY.equals(key)) {
            return true;
        }
        throw new UnsupportedOperationException();
    }

    @Override
    public Object getMemberKeys() {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean hasMember(String key) {
        return MEMBER_KEY.equals(key);
    }

    @Override
    public void putMember(String key, Value value) {
        throw new UnsupportedOperationException();
    }

    @Override
    public long getHashSize() {
        return kwArgs.size();
    }

    @Override
    public boolean hasHashEntry(Value key) {
        String unboxedKey = unboxKey(key);
        return kwArgs.containsKey(unboxedKey);
    }

    private static String unboxKey(Value key) {
        return key.asString();
    }

    @Override
    public Object getHashValue(Value key) {
        Object unboxedKey = unboxKey(key);
        return kwArgs.get(unboxedKey);
    }

    @Override
    public void putHashEntry(Value key, Value value) {
        String unboxedKey = unboxKey(key);
        kwArgs.put(unboxedKey, value.isHostObject() ? value.asHostObject() : value);
    }

    @Override
    public Object getHashEntriesIterator() {
        Iterator<Entry<String, Object>> entryIterator = kwArgs.entrySet().iterator();
        return new ProxyIterator() {
            @Override
            public boolean hasNext() {
                return entryIterator.hasNext();
            }

            @Override
            public Object getNext() throws NoSuchElementException, UnsupportedOperationException {
                return new ProxyEntryImpl(entryIterator.next());
            }
        };
    }

    private class ProxyEntryImpl implements ProxyArray {

        private Entry<String, Object> mapEntry;

        ProxyEntryImpl(Entry<String, Object> mapEntry) {
            this.mapEntry = mapEntry;
        }

        @Override
        public Object get(long index) {
            if (index == 0L) {
                return mapEntry.getKey();
            } else if (index == 1L) {
                return mapEntry.getValue();
            } else {
                throw new ArrayIndexOutOfBoundsException();
            }
        }

        @Override
        public void set(long index, Value value) {
            if (index == 0L) {
                throw new UnsupportedOperationException();
            } else if (index == 1L) {
                kwArgs.put(mapEntry.getKey(), value.isHostObject() ? value.asHostObject() : value);
            } else {
                throw new ArrayIndexOutOfBoundsException();
            }
        }

        @Override
        public long getSize() {
            return 2;
        }
    }
}
