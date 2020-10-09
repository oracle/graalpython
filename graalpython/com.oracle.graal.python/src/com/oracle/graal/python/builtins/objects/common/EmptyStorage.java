/*
 * Copyright (c) 2019, 2020, Oracle and/or its affiliates. All rights reserved.
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

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.builtins.objects.common.HashingStorageLibrary.ForEachNode;
import com.oracle.graal.python.builtins.objects.common.HashingStorageLibrary.HashingStorageIterable;
import com.oracle.graal.python.builtins.objects.function.PArguments.ThreadState;
import com.oracle.graal.python.builtins.objects.object.PythonObjectLibrary;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Exclusive;
import com.oracle.truffle.api.dsl.CachedLanguage;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.api.profiles.ConditionProfile;

@ExportLibrary(HashingStorageLibrary.class)
public class EmptyStorage extends HashingStorage {

    @Override
    @ExportMessage
    public int length() {
        return 0;
    }

    @ExportMessage(limit = "1")
    public Object getItemWithState(Object key, ThreadState state,
                    @CachedLibrary("key") PythonObjectLibrary lib,
                    @Exclusive @Cached("createBinaryProfile()") ConditionProfile gotState) {
        // must call __hash__ for potential side-effect
        getHashWithState(key, lib, state, gotState);
        return null;
    }

    @ExportMessage
    public HashingStorage setItemWithState(Object key, Object value, ThreadState state,
                    @CachedLanguage PythonLanguage lang,
                    @CachedLibrary(limit = "2") HashingStorageLibrary lib,
                    @Exclusive @Cached("createBinaryProfile()") ConditionProfile gotState) {
        HashingStorage newStore;
        if (key instanceof String) {
            newStore = new DynamicObjectStorage(lang);
        } else {
            newStore = EconomicMapStorage.create();
        }
        if (gotState.profile(state != null)) {
            lib.setItemWithState(newStore, key, value, state);
        } else {
            lib.setItem(newStore, key, value);
        }
        return newStore;
    }

    @ExportMessage(limit = "1")
    public HashingStorage delItemWithState(Object key, ThreadState state,
                    @CachedLibrary("key") PythonObjectLibrary lib,
                    @Exclusive @Cached("createBinaryProfile()") ConditionProfile gotState) {
        // must call __hash__ for potential side-effect
        getHashWithState(key, lib, state, gotState);
        return this;
    }

    @Override
    @ExportMessage
    Object forEachUntyped(@SuppressWarnings("unused") ForEachNode<Object> node, Object arg) {
        return arg;
    }

    @Override
    @ExportMessage
    public HashingStorage clear() {
        return this;
    }

    @Override
    @ExportMessage
    public HashingStorage copy() {
        return this;
    }

    private static final Iterator<Object> KEYS_ITERATOR = new Iterator<Object>() {
        @Override
        public boolean hasNext() {
            return false;
        }

        @Override
        public Object next() {
            throw new NoSuchElementException();
        }
    };

    @Override
    @ExportMessage
    public HashingStorageIterable<Object> keys() {
        return new HashingStorageIterable<>(KEYS_ITERATOR);
    }

    @ExportMessage
    @Override
    public HashingStorageIterable<Object> reverseKeys() {
        return new HashingStorageIterable<>(KEYS_ITERATOR);
    }

}
