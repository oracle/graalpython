/*
 * Copyright (c) 2018, 2020, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.builtins.objects.dict;

import com.oracle.graal.python.builtins.objects.common.HashingStorage;
import com.oracle.graal.python.builtins.objects.common.HashingStorage.DictEntry;
import com.oracle.graal.python.builtins.objects.common.HashingStorageLibrary.HashingStorageIterator;
import com.oracle.graal.python.builtins.objects.common.PHashingCollection;
import com.oracle.graal.python.builtins.objects.object.PythonBuiltinObject;
import com.oracle.truffle.api.object.DynamicObject;

public abstract class PDictView extends PythonBuiltinObject {
    private final PHashingCollection dict;
    private final String name;

    public PDictView(Object clazz, DynamicObject storage, String name, PHashingCollection dict) {
        super(clazz, storage);
        this.name = name;
        assert dict != null;
        this.dict = dict;
    }

    public final PHashingCollection getWrappedDict() {
        return dict;
    }

    public int size() {
        return dict.size();
    }

    // -----------------------------------------------------------------------------------------------------------------
    //
    // the keys
    //
    // -----------------------------------------------------------------------------------------------------------------
    public static final class PDictKeyIterator extends PHashingStorageIterator<Object> {
        public PDictKeyIterator(Object clazz, DynamicObject storage, HashingStorageIterator<Object> iterator, HashingStorage hashingStorage, int initialSize) {
            super(clazz, storage, iterator, hashingStorage, initialSize);
        }
    }

    public static final class PDictKeysView extends PDictView {

        public PDictKeysView(Object clazz, DynamicObject storage, PHashingCollection dict) {
            super(clazz, storage, "dict_keys", dict);
        }
    }

    // -----------------------------------------------------------------------------------------------------------------
    //
    // the values
    //
    // -----------------------------------------------------------------------------------------------------------------
    public static final class PDictValueIterator extends PHashingStorageIterator<Object> {
        public PDictValueIterator(Object clazz, DynamicObject storage, HashingStorageIterator<Object> iterator, HashingStorage hashingStorage, int initialSize) {
            super(clazz, storage, iterator, hashingStorage, initialSize);
        }
    }

    public static final class PDictValuesView extends PDictView {

        public PDictValuesView(Object clazz, DynamicObject storage, PHashingCollection dict) {
            super(clazz, storage, "dict_values", dict);
        }
    }

    // -----------------------------------------------------------------------------------------------------------------
    //
    // the items
    //
    // -----------------------------------------------------------------------------------------------------------------
    public static final class PDictItemIterator extends PHashingStorageIterator<DictEntry> {
        public PDictItemIterator(Object clazz, DynamicObject storage, HashingStorageIterator<DictEntry> iterator, HashingStorage hashingStorage, int initialSize) {
            super(clazz, storage, iterator, hashingStorage, initialSize);
        }
    }

    public static final class PDictItemsView extends PDictView {

        public PDictItemsView(Object clazz, DynamicObject storage, PHashingCollection dict) {
            super(clazz, storage, "dict_items", dict);
        }
    }

    public String getName() {
        return name;
    }

}
