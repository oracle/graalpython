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

import com.oracle.graal.python.builtins.objects.common.SequenceStorageNodes;
import com.oracle.graal.python.builtins.objects.common.SequenceStorageNodes.NoGeneralizationNode;
import com.oracle.graal.python.builtins.objects.tuple.PTuple;
import com.oracle.graal.python.nodes.PNode;
import com.oracle.graal.python.nodes.expression.ExpressionNode;
import com.oracle.graal.python.runtime.object.PythonObjectFactory;
import com.oracle.graal.python.runtime.sequence.storage.ObjectSequenceStorage;
import com.oracle.graal.python.runtime.sequence.storage.SequenceStorage;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.ExplodeLoop;

public final class TupleLiteralNode extends LiteralNode {
    @Child private PythonObjectFactory factory = PythonObjectFactory.create();
    @Children private final ExpressionNode[] values;
    @Child private SequenceStorageNodes.ConcatNode concatStoragesNode;
    @Child private SequenceStorageNodes.AppendNode appendNode;
    private final boolean hasStarredExpressions;

    public ExpressionNode[] getValues() {
        return values;
    }

    public TupleLiteralNode(ExpressionNode[] values) {
        this.values = values;
        for (PNode v : values) {
            if (v instanceof StarredExpressionNode) {
                hasStarredExpressions = true;
                return;
            }
        }
        hasStarredExpressions = false;
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
        // we will usually have more than 'values.length' elements
        SequenceStorage storage = new ObjectSequenceStorage(values.length);
        for (ExpressionNode n : values) {
            if (n instanceof StarredExpressionNode) {
                SequenceStorage addElements = ((StarredExpressionNode) n).getStorage(frame);
                storage = ensureConcatStoragesNode().execute(storage, addElements);
            } else {
                Object element = n.execute(frame);
                storage = ensureAppendNode().execute(storage, element, NoGeneralizationNode.DEFAULT);
            }
        }
        return factory.createTuple(storage);
    }

    @ExplodeLoop
    private PTuple directTuple(VirtualFrame frame) {
        final Object[] elements = new Object[values.length];
        for (int i = 0; i < values.length; i++) {
            elements[i] = values[i].execute(frame);
        }
        return factory.createTuple(elements);
    }

    private SequenceStorageNodes.ConcatNode ensureConcatStoragesNode() {
        if (concatStoragesNode == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            concatStoragesNode = insert(SequenceStorageNodes.ConcatNode.create());
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
}
