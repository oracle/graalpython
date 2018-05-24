/*
 * Copyright (c) 2017, 2018, Oracle and/or its affiliates.
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

import com.oracle.graal.python.builtins.objects.list.PList;
import com.oracle.graal.python.builtins.objects.tuple.PTuple;
import com.oracle.graal.python.nodes.PNode;
import com.oracle.graal.python.runtime.PythonOptions;
import com.oracle.graal.python.runtime.sequence.storage.DoubleSequenceStorage;
import com.oracle.graal.python.runtime.sequence.storage.IntSequenceStorage;
import com.oracle.graal.python.runtime.sequence.storage.ListSequenceStorage;
import com.oracle.graal.python.runtime.sequence.storage.LongSequenceStorage;
import com.oracle.graal.python.runtime.sequence.storage.ObjectSequenceStorage;
import com.oracle.graal.python.runtime.sequence.storage.SequenceStorageFactory;
import com.oracle.graal.python.runtime.sequence.storage.TupleSequenceStorage;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.nodes.UnexpectedResultException;

public abstract class ListLiteralNode extends LiteralNode {

    @Children protected final PNode[] values;

    public ListLiteralNode(PNode[] values) {
        this.values = values;
    }

    public PNode[] getValues() {
        return values;
    }

    protected boolean unboxSequenceStorage() {
        return PythonOptions.getOption(getContext(), PythonOptions.UnboxSequenceStorage);
    }

    @Specialization(guards = {"values.length == 0", "unboxSequenceStorage()"})
    protected PList doEmpty() {
        return factory().createList();
    }

    private Object genericFallback(VirtualFrame frame, Object array, int count, Object result) {
        Object[] elements = new Object[values.length];
        int i = 0;
        for (; i < count; i++) {
            elements[i] = Array.get(array, i);
        }
        elements[i++] = result;
        for (; i < values.length; i++) {
            elements[i] = values[i].execute(frame);
        }
        return factory().createList(new SequenceStorageFactory().createStorage(elements));
    }

    @Specialization(guards = "unboxSequenceStorage()", rewriteOn = UnexpectedResultException.class)
    @ExplodeLoop
    protected PList doInt(VirtualFrame frame) throws UnexpectedResultException {
        int[] elements = new int[values.length];
        int i = 0;
        try {
            for (; i < values.length; i++) {
                elements[i] = values[i].executeInt(frame);
            }
            return factory().createList(new IntSequenceStorage(elements));
        } catch (UnexpectedResultException e) {
            throw new UnexpectedResultException(genericFallback(frame, elements, i, e.getResult()));
        }
    }

    @Specialization(guards = "unboxSequenceStorage()", rewriteOn = UnexpectedResultException.class)
    @ExplodeLoop
    protected PList doLong(VirtualFrame frame) throws UnexpectedResultException {
        long[] elements = new long[values.length];
        int i = 0;
        try {
            for (; i < values.length; i++) {
                elements[i] = values[i].executeLong(frame);
            }
            return factory().createList(new LongSequenceStorage(elements));
        } catch (UnexpectedResultException e) {
            throw new UnexpectedResultException(genericFallback(frame, elements, i, e.getResult()));
        }
    }

    @Specialization(guards = "unboxSequenceStorage()", rewriteOn = UnexpectedResultException.class)
    @ExplodeLoop
    protected PList doDouble(VirtualFrame frame) throws UnexpectedResultException {
        double[] elements = new double[values.length];
        int i = 0;
        try {
            for (; i < values.length; i++) {
                elements[i] = values[i].executeDouble(frame);
            }
            return factory().createList(new DoubleSequenceStorage(elements));
        } catch (UnexpectedResultException e) {
            throw new UnexpectedResultException(genericFallback(frame, elements, i, e.getResult()));
        }
    }

    @Specialization(guards = "unboxSequenceStorage()", rewriteOn = UnexpectedResultException.class)
    @ExplodeLoop
    protected PList doPList(VirtualFrame frame) throws UnexpectedResultException {
        PList[] elements = new PList[values.length];
        int i = 0;
        try {
            for (; i < values.length; i++) {
                elements[i] = PList.expect(values[i].execute(frame));
            }
            return factory().createList(new ListSequenceStorage(elements));
        } catch (UnexpectedResultException e) {
            throw new UnexpectedResultException(genericFallback(frame, elements, i, e.getResult()));
        }
    }

    @Specialization(guards = "unboxSequenceStorage()", rewriteOn = UnexpectedResultException.class)
    @ExplodeLoop
    protected PList doPTuple(VirtualFrame frame) throws UnexpectedResultException {
        PTuple[] elements = new PTuple[values.length];
        int i = 0;
        try {
            for (; i < values.length; i++) {
                elements[i] = PTuple.expect(values[i].execute(frame));
            }
            return factory().createList(new TupleSequenceStorage(elements));
        } catch (UnexpectedResultException e) {
            throw new UnexpectedResultException(genericFallback(frame, elements, i, e.getResult()));
        }
    }

    @Specialization
    @ExplodeLoop
    protected PList doGeneric(VirtualFrame frame) {
        Object[] elements = new Object[values.length];
        for (int i = 0; i < values.length; i++) {
            elements[i] = values[i].execute(frame);
        }
        return factory().createList(new ObjectSequenceStorage(elements));
    }

    public static ListLiteralNode create(PNode[] values) {
        return ListLiteralNodeGen.create(values);
    }
}
