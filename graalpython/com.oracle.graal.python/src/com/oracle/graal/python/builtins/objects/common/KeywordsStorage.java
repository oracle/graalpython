/*
 * Copyright (c) 2018, Oracle and/or its affiliates. All rights reserved.
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

import com.oracle.graal.python.builtins.objects.function.PKeyword;

public class KeywordsStorage extends HashingStorage {

    private final PKeyword[] keywords;

    protected KeywordsStorage(PKeyword[] keywords) {
        this.keywords = keywords;
    }

    @Override
    public int length() {
        return keywords.length;
    }

    @Override
    public void addAll(HashingStorage other, Equivalence eq) {
        throw UnmodifiableStorageException.INSTANCE;
    }

    @Override
    public boolean hasKey(Object key, Equivalence eq) {
        for (int i = 0; i < keywords.length; i++) {
            if (eq.equals(keywords[i].getName(), key)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public Object getItem(Object key, Equivalence eq) {
        for (int i = 0; i < keywords.length; i++) {
            PKeyword keyword = keywords[i];
            if (eq.equals(keyword.getName(), key)) {
                return keyword.getValue();
            }
        }
        return null;
    }

    @Override
    public void setItem(Object key, Object value, Equivalence eq) {
        throw UnmodifiableStorageException.INSTANCE;
    }

    @Override
    public boolean remove(Object key, Equivalence eq) {
        throw UnmodifiableStorageException.INSTANCE;
    }

    @Override
    public Iterable<Object> keys() {
        return new Iterable<Object>() {
            public Iterator<Object> iterator() {
                return new KeysIterator();
            }
        };
    }

    @Override
    public Iterable<Object> values() {
        return new Iterable<Object>() {
            public Iterator<Object> iterator() {
                return new ValuesIterator();
            }
        };
    }

    @Override
    public Iterable<DictEntry> entries() {
        return new Iterable<DictEntry>() {
            public Iterator<DictEntry> iterator() {
                return new ItemsIterator();
            }
        };
    }

    @Override
    public void clear() {
        throw UnmodifiableStorageException.INSTANCE;
    }

    @Override
    public HashingStorage copy(Equivalence eq) {
        // this storage is unmodifiable; just reuse it
        return this;
    }

    public static KeywordsStorage create(PKeyword[] keywords) {
        return new KeywordsStorage(keywords);
    }

    public PKeyword[] getStore() {
        return keywords;
    }

    private abstract class KeywordsIterator<T> implements Iterator<T> {

        protected int current = 0;

        public boolean hasNext() {
            return current < keywords.length;
        }

    }

    private class KeysIterator extends KeywordsIterator<Object> {

        public Object next() {
            return keywords[current++].getName();
        }

    }

    private class ValuesIterator extends KeywordsIterator<Object> {

        public Object next() {
            return keywords[current++].getValue();
        }

    }

    private class ItemsIterator extends KeywordsIterator<DictEntry> {

        public DictEntry next() {
            PKeyword kwd = keywords[current++];
            return new DictEntry(kwd.getName(), kwd.getValue());
        }

    }

}
