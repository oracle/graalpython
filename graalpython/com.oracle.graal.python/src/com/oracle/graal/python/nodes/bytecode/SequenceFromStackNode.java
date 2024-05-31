/*
 * Copyright (c) 2022, 2024, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.nodes.bytecode;

import java.lang.reflect.Array;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.builtins.objects.list.PList.ListOrigin;
import com.oracle.graal.python.nodes.PNodeWithContext;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.graal.python.runtime.PythonOptions;
import com.oracle.graal.python.runtime.sequence.storage.ArrayBasedSequenceStorage;
import com.oracle.graal.python.runtime.sequence.storage.BoolSequenceStorage;
import com.oracle.graal.python.runtime.sequence.storage.ByteSequenceStorage;
import com.oracle.graal.python.runtime.sequence.storage.DoubleSequenceStorage;
import com.oracle.graal.python.runtime.sequence.storage.EmptySequenceStorage;
import com.oracle.graal.python.runtime.sequence.storage.IntSequenceStorage;
import com.oracle.graal.python.runtime.sequence.storage.LongSequenceStorage;
import com.oracle.graal.python.runtime.sequence.storage.ObjectSequenceStorage;
import com.oracle.graal.python.runtime.sequence.storage.SequenceStorage;
import com.oracle.graal.python.runtime.sequence.storage.SequenceStorageFactory;
import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.TruffleLogger;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.nodes.UnexpectedResultException;
import com.oracle.truffle.api.source.SourceSection;

abstract class SequenceFromStackNode extends PNodeWithContext {
    @CompilationFinal protected final int length;
    @CompilationFinal protected SequenceStorage.StorageType type = SequenceStorage.StorageType.Uninitialized;

    SequenceFromStackNode(int length) {
        this.length = length;
    }

    @ExplodeLoop
    protected SequenceStorage createSequenceStorageForDirect(VirtualFrame frame, int start, int stop) {
        CompilerAsserts.partialEvaluationConstant(start);
        CompilerAsserts.partialEvaluationConstant(stop);

        SequenceStorage storage;
        if (type == SequenceStorage.StorageType.Uninitialized) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            try {
                Object[] elements = new Object[length];
                for (int j = 0, i = start; i < stop; i++, j++) {
                    elements[j] = frame.getObject(i);
                    frame.setObject(i, null);
                }
                storage = SequenceStorageFactory.createStorage(elements);
                type = storage.getElementType();
            } catch (Throwable t) {
                // we do not want to repeatedly deopt if a value execution
                // always raises, for example
                type = SequenceStorage.StorageType.Generic;
                throw t;
            }
        } else {
            int j = 0;
            Object array = null;
            try {
                switch (type) {
                    // Ugh. We want to use primitive arrays during unpacking, so
                    // we cannot dispatch generically here.
                    case Empty: {
                        assert length == 0;
                        storage = EmptySequenceStorage.INSTANCE;
                        break;
                    }
                    case Boolean: {
                        boolean[] elements = new boolean[getCapacityEstimate()];
                        array = elements;
                        for (int i = start; i < stop; i++, j++) {
                            elements[j] = castBoolean(frame.getObject(i));
                            frame.setObject(i, null);
                        }
                        storage = new BoolSequenceStorage(elements, length);
                        break;
                    }
                    case Byte: {
                        byte[] elements = new byte[getCapacityEstimate()];
                        array = elements;
                        for (int i = start; i < stop; i++, j++) {
                            int element = castInt(frame.getObject(i));
                            if (element <= Byte.MAX_VALUE && element >= Byte.MIN_VALUE) {
                                elements[j] = (byte) element;
                                frame.setObject(i, null);
                            } else {
                                CompilerDirectives.transferToInterpreterAndInvalidate();
                                throw new UnexpectedResultException(element);
                            }
                        }
                        storage = new ByteSequenceStorage(elements, length);
                        break;
                    }
                    case Int: {
                        int[] elements = new int[getCapacityEstimate()];
                        array = elements;
                        for (int i = start; i < stop; i++, j++) {
                            elements[j] = castInt(frame.getObject(i));
                            frame.setObject(i, null);
                        }
                        storage = new IntSequenceStorage(elements, length);
                        break;
                    }
                    case Long: {
                        long[] elements = new long[getCapacityEstimate()];
                        array = elements;
                        for (int i = start; i < stop; i++, j++) {
                            elements[j] = castLong(frame.getObject(i));
                            frame.setObject(i, null);
                        }
                        storage = new LongSequenceStorage(elements, length);
                        break;
                    }
                    case Double: {
                        double[] elements = new double[getCapacityEstimate()];
                        array = elements;
                        for (int i = start; i < stop; i++, j++) {
                            elements[j] = castDouble(frame.getObject(i));
                            frame.setObject(i, null);
                        }
                        storage = new DoubleSequenceStorage(elements, length);
                        break;
                    }
                    case Generic: {
                        Object[] elements = new Object[getCapacityEstimate()];
                        for (int i = start; i < stop; i++, j++) {
                            elements[j] = frame.getObject(i);
                            frame.setObject(i, null);
                        }
                        storage = new ObjectSequenceStorage(elements, length);
                        break;
                    }
                    default:
                        CompilerDirectives.transferToInterpreterAndInvalidate();
                        throw new RuntimeException("unexpected state");
                }
            } catch (UnexpectedResultException e) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                storage = genericFallback(frame, array, start, stop, j, e.getResult());
            }
        }
        return storage;
    }

    private SequenceStorage genericFallback(VirtualFrame frame, Object array, int start, int stop, int count, Object result) {
        type = SequenceStorage.StorageType.Generic;
        Object[] elements = new Object[getCapacityEstimate()];
        int j = 0;
        for (; j < count; j++) {
            elements[j] = Array.get(array, j);
        }
        elements[j++] = result;
        for (int i = start + count + 1; i < stop; i++, j++) {
            elements[j] = frame.getObject(i);
            frame.setObject(i, null);
        }
        return new ObjectSequenceStorage(elements, length);
    }

    private static int castInt(Object o) throws UnexpectedResultException {
        if (o instanceof Integer) {
            return (int) o;
        }
        throw new UnexpectedResultException(o);
    }

    private static long castLong(Object o) throws UnexpectedResultException {
        if (o instanceof Long) {
            return (long) o;
        }
        throw new UnexpectedResultException(o);
    }

    private static double castDouble(Object o) throws UnexpectedResultException {
        if (o instanceof Double) {
            return (double) o;
        }
        throw new UnexpectedResultException(o);
    }

    private static boolean castBoolean(Object o) throws UnexpectedResultException {
        if (o instanceof Boolean) {
            return (boolean) o;
        }
        throw new UnexpectedResultException(o);
    }

    protected abstract int getCapacityEstimate();

    public abstract static class ListFromStackNode extends SequenceFromStackNode implements ListOrigin {

        private static final TruffleLogger LOGGER = PythonLanguage.getLogger(ListFromStackNode.class);

        public ListFromStackNode(int length) {
            super(length);
            this.initialCapacity = new SizeEstimate(length);
        }

        public abstract SequenceStorage execute(Frame virtualFrame, int start, int stop);

        @Specialization
        SequenceStorage doIt(VirtualFrame virtualFrame, int start, int stop) {
            return createSequenceStorageForDirect(virtualFrame, start, stop);
        }

        private final SizeEstimate initialCapacity;

        @Override
        protected int getCapacityEstimate() {
            return initialCapacity.estimate();
        }

        @Override
        public SourceSection getSourceSection() {
            return null;
        }

        @Override
        public void reportUpdatedCapacity(ArrayBasedSequenceStorage newStore) {
            if (CompilerDirectives.inInterpreter()) {
                if (PythonContext.get(this).getOption(PythonOptions.OverallocateLiteralLists)) {
                    if (newStore.getCapacity() > initialCapacity.estimate()) {
                        initialCapacity.updateFrom(newStore.getCapacity());
                        LOGGER.finest(() -> {
                            SourceSection encapsulatingSourceSection = getEncapsulatingSourceSection();
                            String sourceSection = encapsulatingSourceSection == null ? "<unavailable source>" : encapsulatingSourceSection.toString();
                            return String.format("Updating list size estimate at %s. Observed capacity: %d, new estimate: %d", sourceSection, newStore.getCapacity(),
                                            initialCapacity.estimate());
                        });
                    }
                    if (newStore.getElementType().generalizesFrom(type)) {
                        type = newStore.getElementType();
                        LOGGER.finest(() -> {
                            SourceSection encapsulatingSourceSection = getEncapsulatingSourceSection();
                            String sourceSection = encapsulatingSourceSection == null ? "<unavailable source>" : encapsulatingSourceSection.toString();
                            return String.format("Updating list type estimate at %s. New type: %s", sourceSection, type.name());
                        });
                    }
                }
            }
            // n.b.: it's ok that this races when the code is already being compiled
            // or if we're running on multiple threads. if the update isn't seen, we
            // are not incorrect, we just don't benefit from the optimization
        }
    }

    public abstract static class TupleFromStackNode extends SequenceFromStackNode {

        public TupleFromStackNode(int length) {
            super(length);
        }

        public abstract SequenceStorage execute(Frame virtualFrame, int start, int stop);

        @Specialization
        SequenceStorage doIt(VirtualFrame virtualFrame, int start, int stop) {
            return createSequenceStorageForDirect(virtualFrame, start, stop);
        }

        @Override
        protected int getCapacityEstimate() {
            return length;
        }
    }
}
