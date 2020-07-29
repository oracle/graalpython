/*
 * Copyright (c) 2020, Oracle and/or its affiliates. All rights reserved.
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

import com.oracle.graal.python.nodes.PNodeWithContext;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.Specialization;

public abstract class MapNodes {

    @GenerateNodeFactory
    @GenerateUncached
    public abstract static class GetIteratorState extends PNodeWithContext {

        public abstract int execute(Object iterator);

        @Specialization
        int iterState(HashingStorageLibrary.HashingStorageIterator<?> iterator,
                        @Cached GetIteratorState rec) {
            return rec.execute(iterator.getIterator());
        }

        @Specialization
        int iterState(HashingStorage.ValuesIterator iterator,
                        @Cached GetIteratorState rec) {
            return rec.execute(iterator.getIterator());
        }

        @Specialization
        int iterState(EconomicMapStorage.KeysIterator iterator,
                        @Cached GetIteratorState rec) {
            return rec.execute(iterator.getKeysIterator());
        }

        @Specialization
        int iterState(HashingStorage.EntriesIterator iterator,
                        @Cached GetIteratorState rec) {
            return rec.execute(iterator.getKeysIterator());
        }

        @Specialization
        int iterState(HashingStorageLibrary.HashingStorageIterable<?> iterator,
                        @Cached GetIteratorState rec) {
            return rec.execute(iterator.getIterator());
        }

        @Specialization
        int iterState(LocalsStorage.AbstractLocalsIterator iterator) {
            return iterator.getState();
        }

        @Specialization
        int iterState(PEMap.AbstractSparseMapIterator<?> iterator) {
            return iterator.getState();
        }

        @Specialization
        int iterState(DynamicObjectStorage.EntriesIterator iterator) {
            return iterator.getState();
        }
    }

    @GenerateNodeFactory
    @GenerateUncached
    public abstract static class SetIteratorState extends PNodeWithContext {

        public abstract void execute(Object iterator, int state);

        @Specialization
        void iterState(HashingStorageLibrary.HashingStorageIterator<?> iterator, int state,
                        @Cached SetIteratorState rec) {
            rec.execute(iterator.getIterator(), state);
        }

        @Specialization
        void iterState(HashingStorage.ValuesIterator iterator, int state,
                        @Cached SetIteratorState rec) {
            rec.execute(iterator.getIterator(), state);
        }

        @Specialization
        void iterState(EconomicMapStorage.KeysIterator iterator, int state,
                        @Cached SetIteratorState rec) {
            rec.execute(iterator.getKeysIterator(), state);
        }

        @Specialization
        void iterState(HashingStorage.EntriesIterator iterator, int state,
                        @Cached SetIteratorState rec) {
            rec.execute(iterator.getKeysIterator(), state);
        }

        @Specialization
        void iterState(HashingStorageLibrary.HashingStorageIterable<?> iterator, int state,
                        @Cached SetIteratorState rec) {
            rec.execute(iterator.getIterator(), state);
        }

        @Specialization
        void iterState(LocalsStorage.AbstractLocalsIterator iterator, int state) {
            iterator.setState(state);
        }

        @Specialization
        void iterState(PEMap.AbstractSparseMapIterator<?> iterator, int state) {
            iterator.setState(state);
        }

        @Specialization
        void iterState(DynamicObjectStorage.EntriesIterator iterator, int state) {
            iterator.setState(state);
        }
    }

}
