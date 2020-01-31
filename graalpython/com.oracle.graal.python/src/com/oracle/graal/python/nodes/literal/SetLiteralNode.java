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

import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.common.HashingStorage;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodes;
import com.oracle.graal.python.builtins.objects.common.SequenceStorageNodes;
import com.oracle.graal.python.builtins.objects.dict.PDict;
import com.oracle.graal.python.builtins.objects.set.PSet;
import com.oracle.graal.python.nodes.PNode;
import com.oracle.graal.python.nodes.expression.ExpressionNode;
import com.oracle.graal.python.runtime.object.PythonObjectFactory;
import com.oracle.graal.python.runtime.sequence.storage.SequenceStorage;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.ExplodeLoop;

public final class SetLiteralNode extends LiteralNode {
    @Child private PythonObjectFactory factory = PythonObjectFactory.create();
    @Children private final ExpressionNode[] values;
    @Child private HashingStorageNodes.SetItemNode setItemNode;
    @Child private SequenceStorageNodes.LenNode lenNode;
    @Child private SequenceStorageNodes.GetItemNode getItemNode;

    private final boolean hasStarredExpressions;

    public SetLiteralNode(ExpressionNode[] values) {
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
    public PSet execute(VirtualFrame frame) {
        if (!hasStarredExpressions) {
            return directSet(frame);
        } else {
            return expandingSet(frame);
        }
    }

    @ExplodeLoop
    private PSet expandingSet(VirtualFrame frame) {
        // we will usually have more than 'values.length' elements
        HashingStorage storage = PDict.createNewStorage(true, values.length);
        for (ExpressionNode n : values) {
            if (n instanceof StarredExpressionNode) {
                storage = addAllElement(frame, storage, ((StarredExpressionNode) n).getStorage(frame));
            } else {
                Object element = n.execute(frame);
                storage = ensureSetItemNode().execute(frame, storage, element, PNone.NONE);
            }
        }
        return factory.createSet(storage);
    }

    private HashingStorage addAllElement(VirtualFrame frame, HashingStorage setStorage, SequenceStorage sequenceStorage) {
        HashingStorage storage = setStorage;
        int n = ensureLenNode().execute(sequenceStorage);
        for (int i = 0; i < n; i++) {
            Object element = ensureGetItemNode().execute(frame, sequenceStorage, i);
            storage = ensureSetItemNode().execute(frame, storage, element, PNone.NONE);
        }
        return storage;
    }

    @ExplodeLoop
    private PSet directSet(VirtualFrame frame) {
        HashingStorage storage = PDict.createNewStorage(true, values.length);
        for (ExpressionNode v : this.values) {
            storage = ensureSetItemNode().execute(frame, storage, v.execute(frame), PNone.NONE);
        }
        return factory.createSet(storage);
    }

    private SequenceStorageNodes.LenNode ensureLenNode() {
        if (lenNode == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            lenNode = insert(SequenceStorageNodes.LenNode.create());
        }
        return lenNode;
    }

    private SequenceStorageNodes.GetItemNode ensureGetItemNode() {
        if (getItemNode == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            getItemNode = insert(SequenceStorageNodes.GetItemNode.create());
        }
        return getItemNode;
    }

    private HashingStorageNodes.SetItemNode ensureSetItemNode() {
        if (setItemNode == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            setItemNode = insert(HashingStorageNodes.SetItemNode.create());
        }
        return setItemNode;
    }
}
