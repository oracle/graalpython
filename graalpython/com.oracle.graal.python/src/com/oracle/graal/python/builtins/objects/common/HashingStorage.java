/*
 * Copyright (c) 2018, 2019, Oracle and/or its affiliates. All rights reserved.
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

import static com.oracle.graal.python.nodes.SpecialMethodNames.__EQ__;

import java.util.Iterator;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.builtins.objects.object.PythonObjectLibrary;
import com.oracle.graal.python.nodes.PNodeWithContext;
import com.oracle.graal.python.nodes.expression.BinaryComparisonNode;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.CompilerDirectives.ValueType;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.ControlFlowException;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.source.SourceSection;

public abstract class HashingStorage {

    public static class UnmodifiableStorageException extends ControlFlowException {
        private static final long serialVersionUID = 9102544480293222401L;

        protected static final UnmodifiableStorageException INSTANCE = new UnmodifiableStorageException();
    }

    @ValueType
    public static class DictEntry {
        public final Object key;
        public final Object value;

        protected DictEntry(Object key, Object value) {
            this.key = key;
            this.value = value;
        }

        public Object getKey() {
            return key;
        }

        public Object getValue() {
            return value;
        }

    }

    public abstract static class Equivalence extends PNodeWithContext {
        public abstract int hashCode(Object o);

        public abstract boolean equals(Object left, Object right);

    }

    static final Equivalence DEFAULT_EQIVALENCE = new Equivalence() {

        @Override
        public int hashCode(Object o) {
            return Long.hashCode(o.hashCode());
        }

        @Override
        public boolean equals(Object a, Object b) {
            return (a == b) || (a != null && objectEquals(a, b));
        }

        @TruffleBoundary
        private boolean objectEquals(Object a, Object b) {
            return a.equals(b);
        }
    };

    private static class EqualsRootNode extends RootNode {
        @Child private BinaryComparisonNode callEqNode = BinaryComparisonNode.create(__EQ__, __EQ__, "==");

        protected EqualsRootNode() {
            super(PythonLanguage.getCurrent());
            Truffle.getRuntime().createCallTarget(this);
        }

        @Override
        public Object execute(VirtualFrame frame) {
            Object[] args = frame.getArguments();
            return callEqNode.executeWith(frame, args[0], args[1]);
        }

        @Override
        public SourceSection getSourceSection() {
            return null;
        }

        @Override
        public boolean isCloningAllowed() {
            return true;
        }
    }

    public static class SlowPathEquivalence extends Equivalence {
        private final EqualsRootNode eqRootNode = new EqualsRootNode();

        @Override
        public int hashCode(Object o) {
            long hash = PythonObjectLibrary.getUncached().hash(o);
            return Long.hashCode(hash);
        }

        @Override
        public boolean equals(Object a, Object b) {
            return (boolean) eqRootNode.getCallTarget().call(a, b);
        }

    }

    static SlowPathEquivalence slowPathEquivalence = null;

    protected static <T> Iterable<T> wrapJavaIterable(Iterable<T> values) {
        return new Iterable<T>() {

            @TruffleBoundary
            public Iterator<T> iterator() {
                return new WrappedIterator<>(values.iterator());
            }
        };
    }

    private static class WrappedIterator<T> implements Iterator<T> {
        private final Iterator<T> delegate;

        protected WrappedIterator(Iterator<T> delegate) {
            this.delegate = delegate;
        }

        @TruffleBoundary
        public boolean hasNext() {
            return delegate.hasNext();
        }

        @TruffleBoundary
        public T next() {
            return delegate.next();
        }

    }

    public static Equivalence getSlowPathEquivalence(Object key) {
        if (key instanceof String) {
            return DEFAULT_EQIVALENCE;
        }
        return PythonLanguage.getContext().getSlowPathEquivalence();
    }

    public abstract int length();

    public void addAll(HashingStorage other) {
        addAll(other, DEFAULT_EQIVALENCE);
    }

    @TruffleBoundary
    public void addAll(HashingStorage other, Equivalence eq) {
        for (DictEntry e : other.entries()) {
            setItem(e.getKey(), e.getValue(), eq);
        }
    }

    public abstract boolean hasKey(Object key, Equivalence eq);

    public abstract Object getItem(Object key, Equivalence eq);

    public abstract void setItem(Object key, Object value, Equivalence eq);

    public abstract boolean remove(Object key, Equivalence eq);

    public abstract Iterable<Object> keys();

    @TruffleBoundary
    private Object[] iteratorAsArray(Iterable<Object> iterable) {
        Object[] items = new Object[this.length()];
        int i = 0;
        for (Object item : iterable) {
            items[i++] = item;
        }
        return items;
    }

    public Object[] keysAsArray() {
        return iteratorAsArray(keys());
    }

    public abstract Iterable<Object> values();

    public Object[] valuesAsArray() {
        return iteratorAsArray(values());
    }

    public abstract Iterable<DictEntry> entries();

    public abstract void clear();

    public abstract HashingStorage copy(Equivalence eq);
}
