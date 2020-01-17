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
package com.oracle.graal.python.nodes.literal;

import com.oracle.graal.python.builtins.objects.list.PList;
import com.oracle.graal.python.builtins.objects.tuple.PTuple;
import com.oracle.graal.python.nodes.expression.ExpressionNode;
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
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.nodes.UnexpectedResultException;

import java.lang.reflect.Array;

public abstract class SequenceLiteralNode extends LiteralNode {
    @Children protected final ExpressionNode[] values;
    @CompilationFinal protected ListStorageType type = ListStorageType.Uninitialized;

    public SequenceLiteralNode(ExpressionNode[] values) {
        this.values = values;
    }

    public ExpressionNode[] getValues() {
        return values;
    }

    @ExplodeLoop
    protected SequenceStorage createSequenceStorageForDirect(VirtualFrame frame) {
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
                        boolean[] elements = new boolean[getCapacityEstimate()];
                        array = elements;
                        for (; i < values.length; i++) {
                            elements[i] = values[i].executeBoolean(frame);
                        }
                        storage = new BoolSequenceStorage(elements, values.length);
                        break;
                    }
                    case Byte: {
                        byte[] elements = new byte[getCapacityEstimate()];
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
                        int[] elements = new int[getCapacityEstimate()];
                        array = elements;
                        for (; i < values.length; i++) {
                            elements[i] = values[i].executeInt(frame);
                        }
                        storage = new IntSequenceStorage(elements, values.length);
                        break;
                    }
                    case Long: {
                        long[] elements = new long[getCapacityEstimate()];
                        array = elements;
                        for (; i < values.length; i++) {
                            elements[i] = values[i].executeLong(frame);
                        }
                        storage = new LongSequenceStorage(elements, values.length);
                        break;
                    }
                    case Double: {
                        double[] elements = new double[getCapacityEstimate()];
                        array = elements;
                        for (; i < values.length; i++) {
                            elements[i] = values[i].executeDouble(frame);
                        }
                        storage = new DoubleSequenceStorage(elements, values.length);
                        break;
                    }
                    case List: {
                        PList[] elements = new PList[getCapacityEstimate()];
                        array = elements;
                        for (; i < values.length; i++) {
                            elements[i] = PList.expect(values[i].execute(frame));
                        }
                        storage = new ListSequenceStorage(elements, values.length);
                        break;
                    }
                    case Tuple: {
                        PTuple[] elements = new PTuple[getCapacityEstimate()];
                        array = elements;
                        for (; i < values.length; i++) {
                            elements[i] = PTuple.expect(values[i].execute(frame));
                        }
                        storage = new TupleSequenceStorage(elements, values.length);
                        break;
                    }
                    case Generic: {
                        Object[] elements = new Object[getCapacityEstimate()];
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
        return storage;
    }

    protected abstract int getCapacityEstimate();

    private SequenceStorage genericFallback(VirtualFrame frame, Object array, int count, Object result) {
        type = ListStorageType.Generic;
        Object[] elements = new Object[getCapacityEstimate()];
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
}
