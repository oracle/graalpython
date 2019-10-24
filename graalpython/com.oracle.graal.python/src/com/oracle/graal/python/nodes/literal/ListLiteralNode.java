/*
 * Copyright (c) 2017, 2019, Oracle and/or its affiliates.
 * Copyright (c) 2013, Regents of the University of California
 *
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification, are
 * permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this list of
 * conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice, this list of
 * conditions and the following disclaimer in the documentation and/or other materials provided
 * with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS
 * OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
 * MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE
 * COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE
 * GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED
 * AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED
 * OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.oracle.graal.python.nodes.literal;

import java.lang.reflect.Array;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.builtins.objects.common.SequenceStorageNodes;
import com.oracle.graal.python.builtins.objects.common.SequenceStorageNodes.ListGeneralizationNode;
import com.oracle.graal.python.builtins.objects.list.PList;
import com.oracle.graal.python.builtins.objects.tuple.PTuple;
import com.oracle.graal.python.nodes.PNode;
import com.oracle.graal.python.nodes.expression.ExpressionNode;
import com.oracle.graal.python.runtime.PythonOptions;
import com.oracle.graal.python.runtime.object.PythonObjectFactory;
import com.oracle.graal.python.runtime.sequence.storage.BasicSequenceStorage;
import com.oracle.graal.python.runtime.sequence.storage.BoolSequenceStorage;
import com.oracle.graal.python.runtime.sequence.storage.ByteSequenceStorage;
import com.oracle.graal.python.runtime.sequence.storage.DoubleSequenceStorage;
import com.oracle.graal.python.runtime.sequence.storage.EmptySequenceStorage;
import com.oracle.graal.python.runtime.sequence.storage.IntSequenceStorage;
import com.oracle.graal.python.runtime.sequence.storage.ListSequenceStorage;
import com.oracle.graal.python.runtime.sequence.storage.LongSequenceStorage;
import com.oracle.graal.python.runtime.sequence.storage.ObjectSequenceStorage;
import com.oracle.graal.python.runtime.sequence.storage.SequenceStorage;
import com.oracle.graal.python.runtime.sequence.storage.SequenceStorage.ListStorageType;
import com.oracle.graal.python.runtime.sequence.storage.SequenceStorageFactory;
import com.oracle.graal.python.runtime.sequence.storage.TupleSequenceStorage;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerDirectives.ValueType;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.nodes.UnexpectedResultException;

public final class ListLiteralNode extends LiteralNode {
    @Child private PythonObjectFactory factory = PythonObjectFactory.create();
    @Children protected final ExpressionNode[] values;
    @Child private SequenceStorageNodes.ConcatNode concatStoragesNode;
    @Child private SequenceStorageNodes.AppendNode appendNode;

    /**
     * This class serves the purpose of updating the size estimate for the lists
     * constructed here over time. The estimate is updated slowly, it takes
     * {@link #NUM_DIGITS_POW2} lists to reach a size one larger than the
     * current estimate to increase the estimate for new lists.
     */
    @ValueType
    private static final class SizeEstimate {
        private static final int NUM_DIGITS = 3;
        private static final int NUM_DIGITS_POW2 = 1 << NUM_DIGITS;

        @CompilationFinal private int shiftedStorageSizeEstimate;

        private SizeEstimate(int storageSizeEstimate) {
            shiftedStorageSizeEstimate = storageSizeEstimate * NUM_DIGITS_POW2;
        }

        private int estimate() {
            return shiftedStorageSizeEstimate >> NUM_DIGITS;
        }

        private int updateFrom(int newSizeEstimate) {
            shiftedStorageSizeEstimate = shiftedStorageSizeEstimate + newSizeEstimate - estimate();
            return shiftedStorageSizeEstimate;
        }
    }

    @CompilationFinal private SizeEstimate initialCapacity;
    private final boolean hasStarredExpressions;

    @CompilationFinal private ListStorageType type = ListStorageType.Uninitialized;

    public ListLiteralNode(ExpressionNode[] values) {
        this.values = values;
        this.initialCapacity = new SizeEstimate(values.length);
        for (PNode v : values) {
            if (v instanceof StarredExpressionNode) {
                hasStarredExpressions = true;
                return;
            }
        }
        hasStarredExpressions = false;
    }

    public ExpressionNode[] getValues() {
        return values;
    }

    @ExplodeLoop
    private PList expandingList(VirtualFrame frame) {
        // we will usually have more than 'values.length' elements
        SequenceStorage storage = new ObjectSequenceStorage(values.length);
        for (ExpressionNode n : values) {
            if (n instanceof StarredExpressionNode) {
                SequenceStorage addElements = ((StarredExpressionNode) n).getStorage(frame);
                storage = ensureConcatStoragesNode().execute(storage, addElements);
            } else {
                Object element = n.execute(frame);
                storage = ensureAppendNode().execute(storage, element, ListGeneralizationNode.SUPPLIER);
            }
        }
        return factory.createList(storage);
    }

    @Override
    public PList execute(VirtualFrame frame) {
        if (!hasStarredExpressions) {
            return directList(frame);
        } else {
            return expandingList(frame);
        }
    }

    @ExplodeLoop
    private PList directList(VirtualFrame frame) {
        SequenceStorage storage;
        if (type == ListStorageType.Uninitialized) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            try {
                Object[] elements = new Object[values.length];
                for (int i = 0; i < values.length; i++) {
                    elements[i] = values[i].execute(frame);
                }
                storage = SequenceStorageFactory.createStorage(elements);
                type = storage.getElementType();
            } catch (Throwable t) {
                // we do not want to repeatedly deopt if a value execution
                // always raises, for example
                type = ListStorageType.Generic;
                throw t;
            }
        } else {
            int i = 0;
            Object array = null;
            try {
                switch (type) {
                    // Ugh. We want to use primitive arrays during unpacking, so
                    // we cannot dispatch generically here.
                    case Empty: {
                        assert values.length == 0;
                        storage = EmptySequenceStorage.INSTANCE;
                        break;
                    }
                    case Boolean: {
                        boolean[] elements = new boolean[initialCapacity.estimate()];
                        array = elements;
                        for (; i < values.length; i++) {
                            elements[i] = values[i].executeBoolean(frame);
                        }
                        storage = new BoolSequenceStorage(elements, values.length);
                        break;
                    }
                    case Byte: {
                        byte[] elements = new byte[initialCapacity.estimate()];
                        array = elements;
                        for (; i < values.length; i++) {
                            int element = values[i].executeInt(frame);
                            if (element <= Byte.MAX_VALUE && element >= Byte.MIN_VALUE) {
                                elements[i] = (byte) element;
                            } else {
                                CompilerDirectives.transferToInterpreterAndInvalidate();
                                throw new UnexpectedResultException(element);
                            }
                        }
                        storage = new ByteSequenceStorage(elements, values.length);
                        break;
                    }
                    case Char: {
                        // we don't support this directly, throw and continue
                        // generically
                        CompilerDirectives.transferToInterpreterAndInvalidate();
                        throw new UnexpectedResultException(values[0].execute(frame));
                    }
                    case Int: {
                        int[] elements = new int[initialCapacity.estimate()];
                        array = elements;
                        for (; i < values.length; i++) {
                            elements[i] = values[i].executeInt(frame);
                        }
                        storage = new IntSequenceStorage(elements, values.length);
                        break;
                    }
                    case Long: {
                        long[] elements = new long[initialCapacity.estimate()];
                        array = elements;
                        for (; i < values.length; i++) {
                            elements[i] = values[i].executeLong(frame);
                        }
                        storage = new LongSequenceStorage(elements, values.length);
                        break;
                    }
                    case Double: {
                        double[] elements = new double[initialCapacity.estimate()];
                        array = elements;
                        for (; i < values.length; i++) {
                            elements[i] = values[i].executeDouble(frame);
                        }
                        storage = new DoubleSequenceStorage(elements, values.length);
                        break;
                    }
                    case List: {
                        PList[] elements = new PList[initialCapacity.estimate()];
                        array = elements;
                        for (; i < values.length; i++) {
                            elements[i] = PList.expect(values[i].execute(frame));
                        }
                        storage = new ListSequenceStorage(elements, values.length);
                        break;
                    }
                    case Tuple: {
                        PTuple[] elements = new PTuple[initialCapacity.estimate()];
                        array = elements;
                        for (; i < values.length; i++) {
                            elements[i] = PTuple.expect(values[i].execute(frame));
                        }
                        storage = new TupleSequenceStorage(elements, values.length);
                        break;
                    }
                    case Generic: {
                        Object[] elements = new Object[initialCapacity.estimate()];
                        for (; i < values.length; i++) {
                            elements[i] = values[i].execute(frame);
                        }
                        storage = new ObjectSequenceStorage(elements, values.length);
                        break;
                    }
                    default:
                        throw new RuntimeException("unexpected state");
                }
            } catch (UnexpectedResultException e) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                storage = genericFallback(frame, array, i, e.getResult());
            }
        }
        return factory.createList(storage, this);
    }

    private SequenceStorage genericFallback(VirtualFrame frame, Object array, int count, Object result) {
        type = ListStorageType.Generic;
        Object[] elements = new Object[initialCapacity.estimate()];
        int i = 0;
        for (; i < count; i++) {
            elements[i] = Array.get(array, i);
        }
        elements[i++] = result;
        for (; i < values.length; i++) {
            elements[i] = values[i].execute(frame);
        }
        return new ObjectSequenceStorage(elements, values.length);
    }

    private SequenceStorageNodes.ConcatNode ensureConcatStoragesNode() {
        if (concatStoragesNode == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            concatStoragesNode = insert(SequenceStorageNodes.ConcatNode.create(ListGeneralizationNode::create));
        }
        return concatStoragesNode;
    }

    private SequenceStorageNodes.AppendNode ensureAppendNode() {
        if (appendNode == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            appendNode = insert(SequenceStorageNodes.AppendNode.create());
        }
        return appendNode;
    }

    public void reportUpdatedCapacity(BasicSequenceStorage newStore) {
        if (CompilerDirectives.inInterpreter()) {
            if (PythonOptions.getFlag(lookupContextReference(PythonLanguage.class).get(), PythonOptions.OverallocateLiteralLists)) {
                if (newStore.capacity() > initialCapacity.estimate()) {
                    initialCapacity.updateFrom(newStore.capacity());
                }
                if (newStore.getElementType().generalizesFrom(type)) {
                    type = newStore.getElementType();
                }
            }
        }
        // n.b.: it's ok that this races when the code is already being compiled
        // or if we're running on multiple threads. if the update isn't seen, we
        // are not incorrect, we just don't benefit from the optimization
    }

    public static ListLiteralNode create(ExpressionNode[] values) {
        return new ListLiteralNode(values);
    }
}
