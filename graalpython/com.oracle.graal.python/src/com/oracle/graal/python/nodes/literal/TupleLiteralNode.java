/*
 * Copyright (c) 2017, 2020, Oracle and/or its affiliates.
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

import com.oracle.graal.python.builtins.objects.common.SequenceStorageNodes;
import com.oracle.graal.python.builtins.objects.common.SequenceStorageNodes.ListGeneralizationNode;
import com.oracle.graal.python.builtins.objects.tuple.PTuple;
import com.oracle.graal.python.nodes.PNode;
import com.oracle.graal.python.nodes.expression.ExpressionNode;
import com.oracle.graal.python.runtime.object.PythonObjectFactory;
import com.oracle.graal.python.runtime.sequence.storage.BoolSequenceStorage;
import com.oracle.graal.python.runtime.sequence.storage.ByteSequenceStorage;
import com.oracle.graal.python.runtime.sequence.storage.DoubleSequenceStorage;
import com.oracle.graal.python.runtime.sequence.storage.EmptySequenceStorage;
import com.oracle.graal.python.runtime.sequence.storage.IntSequenceStorage;
import com.oracle.graal.python.runtime.sequence.storage.ListSequenceStorage;
import com.oracle.graal.python.runtime.sequence.storage.LongSequenceStorage;
import com.oracle.graal.python.runtime.sequence.storage.ObjectSequenceStorage;
import com.oracle.graal.python.runtime.sequence.storage.SequenceStorage;
import com.oracle.graal.python.runtime.sequence.storage.TupleSequenceStorage;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.ExplodeLoop;

public final class TupleLiteralNode extends SequenceLiteralNode {
    @Child private PythonObjectFactory factory = PythonObjectFactory.create();
    @Child private SequenceStorageNodes.AppendNode appendNode;
    private final boolean hasStarredExpressions;

    public TupleLiteralNode(ExpressionNode[] values) {
        super(values);
        for (PNode v : values) {
            if (v instanceof StarredExpressionNode) {
                hasStarredExpressions = true;
                return;
            }
        }
        hasStarredExpressions = false;
    }

    @Override
    protected int getCapacityEstimate() {
        return values.length;
    }

    @Override
    public Object execute(VirtualFrame frame) {
        if (!hasStarredExpressions) {
            return directTuple(frame);
        } else {
            return expandingTuple(frame);
        }
    }

    @ExplodeLoop
    private PTuple expandingTuple(VirtualFrame frame) {
        SequenceStorage storage;
        // we will usually have more than 'values.length' elements
        switch (type) {
            case Uninitialized:
            case Empty:
                storage = EmptySequenceStorage.INSTANCE;
                break;
            case Boolean:
                storage = new BoolSequenceStorage(values.length);
                break;
            case Byte:
                storage = new ByteSequenceStorage(values.length);
                break;
            case Double:
                storage = new DoubleSequenceStorage(values.length);
                break;
            case List:
                storage = new ListSequenceStorage(values.length);
                break;
            case Tuple:
                storage = new TupleSequenceStorage(values.length);
                break;
            case Int:
                storage = new IntSequenceStorage(values.length);
                break;
            case Long:
                storage = new LongSequenceStorage(values.length);
                break;
            default:
                storage = new ObjectSequenceStorage(values.length);
                break;
        }
        for (ExpressionNode n : values) {
            Object element = n.execute(frame);
            if (n instanceof StarredExpressionNode) {
                storage = ((StarredExpressionNode) n).appendToStorage(frame, storage, element);
            } else {
                storage = ensureAppendNode().execute(storage, element, ListGeneralizationNode.SUPPLIER);
            }
        }
        if (type != storage.getElementType()) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            type = storage.getElementType();
        }
        return factory.createTuple(storage);
    }

    @ExplodeLoop
    private PTuple directTuple(VirtualFrame frame) {
        SequenceStorage storage = createSequenceStorageForDirect(frame);
        return factory.createTuple(storage);
    }

    private SequenceStorageNodes.AppendNode ensureAppendNode() {
        if (appendNode == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            appendNode = insert(SequenceStorageNodes.AppendNode.create());
        }
        return appendNode;
    }
}
