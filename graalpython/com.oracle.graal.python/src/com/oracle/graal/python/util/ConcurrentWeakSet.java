/*
 * Copyright (c) 2025, 2025, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.util;

import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;

/**
 * A concurrent thread-safe set with weak elements. Elements cannot be null. This is notably used as
 * a way to intern/deduplicate elements with {@link #intern(Object, Function)}
 */
public final class ConcurrentWeakSet<E> implements Iterable<E> {

    private final ConcurrentHashMap<WeakReference<E>, Boolean> map = new ConcurrentHashMap<>();
    private final ReferenceQueue<E> referenceQueue = new ReferenceQueue<>();

    public ConcurrentWeakSet() {
    }

    /**
     * Returns an element already in the set which is {@link Object#equals(Object)} to
     * {@code element} if there is one. Otherwise, add this element to the set after calling
     * {@code transformForAdding} and returns that.
     */
    @TruffleBoundary
    public E intern(E element, Function<E, E> transformForAdding) {
        removeStaleElements();
        var lookupRef = new RecordingWeakReferenceWithHashCode<>(element);

        if (map.containsKey(lookupRef)) {
            return lookupRef.getElementInMap();
        }

        E elementToAdd = transformForAdding.apply(element);
        var refToAdd = new WeakReferenceWithHashCode<>(elementToAdd, referenceQueue);
        while (true) {
            if (map.putIfAbsent(refToAdd, Boolean.TRUE) == null) {
                return elementToAdd;
            } else {
                if (map.containsKey(lookupRef)) {
                    return lookupRef.getElementInMap();
                }
            }
        }
    }

    @TruffleBoundary
    public boolean isInterned(E element) {
        removeStaleElements();
        var lookupRef = new RecordingWeakReferenceWithHashCode<>(element);

        if (map.containsKey(lookupRef)) {
            return element == lookupRef.getElementInMap();
        } else {
            return false;
        }
    }

    /** Same as {@link Set#add(Object)} */
    @TruffleBoundary
    public boolean add(E element) {
        removeStaleElements();
        var ref = (WeakReference<E>) new WeakReferenceWithHashCode<>(element, referenceQueue);
        return map.put(ref, Boolean.TRUE) == null;
    }

    /** Same as {@link Set#contains(Object)} */
    @TruffleBoundary
    public boolean contains(E element) {
        removeStaleElements();
        return map.containsKey(new WeakReferenceWithHashCode<E>(element));
    }

    /** Same as {@link Set#toArray()} */
    @TruffleBoundary
    public Object[] toArray() {
        return elements().toArray();
    }

    /** Same as {@link Set#iterator()} */
    @TruffleBoundary
    public WeakSetIterator<E> iterator() {
        return new WeakSetIterator<>(map.keySet().iterator());
    }

    /** Same as {@link Set#remove(Object)} */
    @TruffleBoundary
    public Boolean remove(E e) {
        removeStaleElements();
        return map.remove(new WeakReferenceWithHashCode<>(e));
    }

    /** Same as {@link Set#clear()} */
    @TruffleBoundary
    public void clear() {
        map.clear();
    }

    /** Same as {@link Set#size()} */
    @TruffleBoundary
    public int size() {
        removeStaleElements();
        int size = 0;

        // Filter out null entries.
        for (var e : map.keySet()) {
            final E element = e.get();
            if (element != null) {
                ++size;
            }
        }

        return size;
    }

    @TruffleBoundary
    public Collection<E> elements() {
        removeStaleElements();
        final Collection<E> elements = new ArrayList<>(map.size());

        // Filter out null entries.
        for (var e : map.keySet()) {
            final E element = e.get();
            if (element != null) {
                elements.add(element);
            }
        }

        return elements;
    }

    /**
     * Attempts to remove map entries whose values have been made unreachable by the GC.
     * <p>
     * This relies on the underlying {@link WeakReference} instance being enqueued to the
     * {@link #referenceQueue} queue. It is possible that the map still contains
     * {@link WeakReference} instances whose element has been nulled out after a call to this method
     * (the reference not having been enqueued yet)!
     */
    private void removeStaleElements() {
        WeakReference<?> ref;
        while ((ref = (WeakReference<?>) referenceQueue.poll()) != null) {
            // Here ref.get() is null, so it will not remove a new key-value pair with the same key
            // as that is a different WeakReference instance.
            map.remove(ref);
        }
    }

    public static final class WeakSetIterator<E> implements Iterator<E> {
        private final Iterator<WeakReference<E>> keysIterator;
        private E nextElement;

        private WeakSetIterator(Iterator<WeakReference<E>> keysIterator) {
            this.keysIterator = keysIterator;
            computeNext();
        }

        private void computeNext() {
            // hasNext()+next() is safe because the ConcurrentHashMap keySet iterator saves the next
            // value
            while (keysIterator.hasNext()) {
                E element = keysIterator.next().get();
                if (element != null) {
                    this.nextElement = element;
                    return;
                }
            }
            this.nextElement = null;
        }

        @Override
        public boolean hasNext() {
            return nextElement != null;
        }

        @TruffleBoundary
        @Override
        public E next() {
            final E element = nextElement;
            if (element == null) {
                throw new NoSuchElementException();
            }
            computeNext();
            return element;
        }
    }

    static class WeakReferenceWithHashCode<E> extends WeakReference<E> {
        private final int hashCode;

        public WeakReferenceWithHashCode(E element) {
            super(element);
            Objects.requireNonNull(element);
            this.hashCode = element.hashCode();
        }

        public WeakReferenceWithHashCode(E element, ReferenceQueue<? super E> queue) {
            super(element, queue);
            Objects.requireNonNull(element);
            this.hashCode = element.hashCode();
        }

        /**
         * It's important that a WeakReference returns true for {@code ref.equals(ref)} even if the
         * reference is cleared so it can still be removed after it's cleared.
         */
        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }

            if (other instanceof WeakReferenceWithHashCode<?> ref) {
                E element = get();
                Object otherElement = ref.get();
                return element != null && otherElement != null && element.equals(otherElement);
            } else {
                return false;
            }
        }

        @Override
        public int hashCode() {
            return hashCode;
        }
    }

    static final class RecordingWeakReferenceWithHashCode<E> extends WeakReferenceWithHashCode<E> {

        private E elementInMap;

        public RecordingWeakReferenceWithHashCode(E element) {
            super(element);
        }

        /**
         * This relies on {@link ConcurrentHashMap#get(Object)} to call equals() on the argument and
         * not on the key in the map. That's checked in {@link #getElementInMap()} that it works as
         * expected. If this no longer holds we would need to change
         * {@link WeakReferenceWithHashCode#equals(Object)} to handle
         * {@link RecordingWeakReferenceWithHashCode} specially.
         */
        @SuppressWarnings("unchecked")
        @Override
        public boolean equals(Object other) {
            if (!(other instanceof WeakReferenceWithHashCode<?>)) {
                return false;
            }

            // Keep the other element alive while comparing
            var elementInMap = ((WeakReferenceWithHashCode<E>) other).get();

            if (super.equals(other)) {
                this.elementInMap = elementInMap;
                return true;
            } else {
                return false;
            }
        }

        public E getElementInMap() {
            return Objects.requireNonNull(elementInMap);
        }
    }
}
