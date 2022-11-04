/*
 * Copyright (c) 2020, 2021, Oracle and/or its affiliates. All rights reserved.
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

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.library.GenerateLibrary;
import com.oracle.truffle.api.library.Library;
import com.oracle.truffle.api.library.LibraryFactory;
import com.oracle.truffle.api.nodes.Node;

/*
 * TODO:
 *
 * - EconomicMapStorage should implement all combinatory messages specialized to
 * another EconomicMapStorage. All of these should then be able to avoid any
 * calls to __hash__ and __eq__
 */

@GenerateLibrary
public abstract class HashingStorageLibrary extends Library {

    /**
     * A node to be called in a loop with different keys to operate on {@code
     * store}. It's execute method returns the new store to use for the next iteration.
     */
    public abstract static class InjectIntoNode extends ForEachNode<HashingStorage[]> {
        public abstract HashingStorage[] execute(HashingStorage[] accumulator, Object key);

        @Override
        public final HashingStorage[] execute(Object key, HashingStorage[] accumulator) {
            return execute(accumulator, key);
        }
    }

    /**
     * A node to be used as the hook for a consumer that is called in a loop over a storage.
     */
    public abstract static class ForEachNode<T> extends Node {
        public abstract T execute(Object key, T arg);
    }

    public abstract Object forEachUntyped(HashingStorage self, ForEachNode<Object> node, Object arg);

    /**
     * Iterate over {@code self} keys and execute {@code node} with each key.
     */
    @SuppressWarnings("unchecked")
    public final <T> T forEach(HashingStorage self, ForEachNode<T> node, T arg) {
        return (T) forEachUntyped(self, (ForEachNode<Object>) node, (Object) arg);
    }

    /**
     * Determines if the storage has elements with a potential side effect on access.
     *
     * @return {@code true} if the storage has elements with a potential side effect, otherwise
     *         {@code false}.
     */
    public boolean hasSideEffect(@SuppressWarnings("unused") HashingStorage self) {
        return false;
    }

    /**
     * An iterable that does not need to be guarded separately with TruffleBoundary.
     */
    public static final class HashingStorageIterable<T> implements Iterable<T> {
        private final Iterator<T> iterator;

        HashingStorageIterable(Iterator<T> iterator) {
            this.iterator = iterator;
        }

        public Iterator<T> getIterator() {
            return this.iterator;
        }

        @Override
        public HashingStorageIterator<T> iterator() {
            return new HashingStorageIterator<>(this.iterator);
        }
    }

    /**
     * An iterator that does not need to be guarded separately with TruffleBoundary.
     */
    public static final class HashingStorageIterator<T> implements Iterator<T> {
        private final Iterator<T> iterator;

        public HashingStorageIterator(Iterator<T> iterator) {
            this.iterator = iterator;
        }

        public Iterator<T> getIterator() {
            return this.iterator;
        }

        @Override
        @TruffleBoundary
        public boolean hasNext() {
            return this.iterator.hasNext();
        }

        @Override
        @TruffleBoundary
        public T next() {
            return this.iterator.next();
        }
    }

    /**
     * This method can be used to iterate over the keys of a store. Due to the nature of Java
     * iterators being an interface and the different storage strategies, this may be slow and
     * should be used with caution.
     *
     * @return an iterator over the keys in this store.
     */
    public abstract HashingStorageIterable<Object> keys(HashingStorage self);

    /**
     * This method can be used to iterate over the values of a store in a reversed order. Due to the
     * nature of Java iterators being an interface and the different storage strategies, this may be
     * slow and should be used with caution.
     *
     * @return an iterator over the values in this store.
     */
    public abstract HashingStorageIterable<Object> values(HashingStorage self);

    /**
     * This method can be used to iterate over the key-value pairs of a store. Due to the nature of
     * Java iterators being an interface and the different storage strategies, this may be slow and
     * should be used with caution.
     *
     * @return an iterator over the keys-value pairs in this store.
     */
    public abstract HashingStorageIterable<HashingStorage.DictEntry> entries(HashingStorage self);

    static final LibraryFactory<HashingStorageLibrary> FACTORY = LibraryFactory.resolve(HashingStorageLibrary.class);

    public static LibraryFactory<HashingStorageLibrary> getFactory() {
        return FACTORY;
    }

    public static HashingStorageLibrary getUncached() {
        return FACTORY.getUncached();
    }
}
