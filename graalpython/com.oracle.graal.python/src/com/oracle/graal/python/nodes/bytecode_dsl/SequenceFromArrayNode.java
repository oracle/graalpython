/*
 * Copyright (c) 2022, 2026, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.nodes.bytecode_dsl;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.builtins.objects.list.PList;
import com.oracle.graal.python.builtins.objects.list.PList.ListOrigin;
import com.oracle.graal.python.builtins.objects.tuple.PTuple;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.graal.python.runtime.PythonOptions;
import com.oracle.graal.python.runtime.object.PFactory;
import com.oracle.graal.python.runtime.sequence.storage.ArrayBasedSequenceStorage;
import com.oracle.graal.python.runtime.sequence.storage.BoolSequenceStorage;
import com.oracle.graal.python.runtime.sequence.storage.DoubleSequenceStorage;
import com.oracle.graal.python.runtime.sequence.storage.EmptySequenceStorage;
import com.oracle.graal.python.runtime.sequence.storage.IntSequenceStorage;
import com.oracle.graal.python.runtime.sequence.storage.LongSequenceStorage;
import com.oracle.graal.python.runtime.sequence.storage.ObjectSequenceStorage;
import com.oracle.graal.python.runtime.sequence.storage.SequenceStorage;
import com.oracle.graal.python.runtime.sequence.storage.SequenceStorage.StorageType;
import com.oracle.graal.python.runtime.sequence.storage.SequenceStorageFactory;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.HostCompilerDirectives.InliningCutoff;
import com.oracle.truffle.api.TruffleLogger;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.SlowPathException;
import com.oracle.truffle.api.profiles.InlinedIntValueProfile;
import com.oracle.truffle.api.source.SourceSection;

abstract class SequenceFromArrayNode extends Node {
    private static final SlowPathException SLOW_PATH_EXCEPTION = new SlowPathException();
    @CompilationFinal protected SequenceStorage.StorageType type = StorageType.Uninitialized;

    SequenceStorage createSequenceStorage(Object[] objectElements, int length) {
        SequenceStorage storage;
        if (type == SequenceStorage.StorageType.Uninitialized) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            storage = initialize(objectElements);
        } else {
            try {
                switch (type) {
                    // Ugh. We want to use primitive arrays during unpacking, so
                    // we cannot dispatch generically here.
                    case Empty: {
                        if (length != 0) {
                            throw SLOW_PATH_EXCEPTION;
                        }
                        storage = EmptySequenceStorage.INSTANCE;
                        break;
                    }
                    case Boolean: {
                        boolean[] elements = new boolean[getCapacityEstimate(length)];
                        for (int i = 0; i < length; i++) {
                            elements[i] = castBoolean(objectElements[i]);
                        }
                        storage = new BoolSequenceStorage(elements, length);
                        break;
                    }
                    case Int: {
                        int[] elements = new int[getCapacityEstimate(length)];
                        for (int i = 0; i < length; i++) {
                            elements[i] = castInt(objectElements[i]);
                        }
                        storage = new IntSequenceStorage(elements, length);
                        break;
                    }
                    case Long: {
                        long[] elements = new long[getCapacityEstimate(length)];
                        for (int i = 0; i < length; i++) {
                            elements[i] = castLong(objectElements[i]);
                        }
                        storage = new LongSequenceStorage(elements, length);
                        break;
                    }
                    case Double: {
                        double[] elements = new double[getCapacityEstimate(length)];
                        for (int i = 0; i < length; i++) {
                            elements[i] = castDouble(objectElements[i]);
                        }
                        storage = new DoubleSequenceStorage(elements, length);
                        break;
                    }
                    case Generic: {
                        storage = new ObjectSequenceStorage(objectElements, length);
                        break;
                    }
                    default:
                        CompilerDirectives.transferToInterpreterAndInvalidate();
                        throw new RuntimeException("unexpected state");
                }
            } catch (SlowPathException e) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                type = SequenceStorage.StorageType.Generic;
                storage = new ObjectSequenceStorage(objectElements, length);
            }
        }
        return storage;
    }

    @InliningCutoff
    private SequenceStorage initialize(Object[] objectElements) {
        SequenceStorage storage;
        try {
            storage = SequenceStorageFactory.createStorage(objectElements);
            type = storage.getElementType();
        } catch (Throwable t) {
            // we do not want to repeatedly deopt if a value execution
            // always raises, for example
            type = SequenceStorage.StorageType.Generic;
            throw t;
        }
        return storage;
    }

    private static int castInt(Object o) throws SlowPathException {
        if (o instanceof Integer) {
            return (int) o;
        }
        throw SLOW_PATH_EXCEPTION;
    }

    private static long castLong(Object o) throws SlowPathException {
        if (o instanceof Long) {
            return (long) o;
        }
        throw SLOW_PATH_EXCEPTION;
    }

    private static double castDouble(Object o) throws SlowPathException {
        if (o instanceof Double) {
            return (double) o;
        }
        throw SLOW_PATH_EXCEPTION;
    }

    private static boolean castBoolean(Object o) throws SlowPathException {
        if (o instanceof Boolean) {
            return (boolean) o;
        }
        throw SLOW_PATH_EXCEPTION;
    }

    protected abstract int getCapacityEstimate(int length);

    public abstract static class ListFromArrayNode extends SequenceFromArrayNode implements ListOrigin {
        private static final TruffleLogger LOGGER = PythonLanguage.getLogger(ListFromArrayNode.class);
        private static final ListFromArrayNode UNCACHED = new ListFromArrayNode() {
            @Override
            public PList execute(PythonLanguage language, Object[] elements) {
                return PFactory.createList(language, elements);
            }
        };

        public static ListFromArrayNode getUncached() {
            return UNCACHED;
        }

        @CompilationFinal private SizeEstimate initialCapacity;

        public abstract PList execute(PythonLanguage language, Object[] elements);

        @Specialization
        PList doIt(PythonLanguage language, Object[] elements,
                        @Bind Node inliningTarget,
                        @Cached InlinedIntValueProfile lengthProfile) {
            SequenceStorage storage = createSequenceStorage(elements, lengthProfile.profile(inliningTarget, elements.length));
            return PFactory.createList(language, storage, initialCapacity != null ? this : null);
        }

        @Override
        protected int getCapacityEstimate(int length) {
            assert isAdoptable();
            if (initialCapacity == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                initialCapacity = new SizeEstimate(length);
            }
            return Math.max(initialCapacity.estimate(), length);
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

    public abstract static class TupleFromArrayNode extends SequenceFromArrayNode {
        private static final TupleFromArrayNode UNCACHED = new TupleFromArrayNode() {
            @Override
            public PTuple execute(PythonLanguage language, Object[] elements) {
                return PFactory.createTuple(language, elements);
            }
        };

        public static TupleFromArrayNode getUncached() {
            return UNCACHED;
        }

        public abstract PTuple execute(PythonLanguage language, Object[] elements);

        @Specialization
        PTuple doIt(PythonLanguage language, Object[] elements,
                        @Bind Node inliningTarget,
                        @Cached InlinedIntValueProfile lengthProfile) {
            return PFactory.createTuple(language, createSequenceStorage(elements, lengthProfile.profile(inliningTarget, elements.length)));
        }

        @Override
        protected int getCapacityEstimate(int length) {
            return length;
        }
    }
}
