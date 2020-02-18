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
import com.oracle.graal.python.builtins.objects.common.HashingStorageLibrary;
import com.oracle.graal.python.builtins.objects.common.SequenceStorageNodes;
import com.oracle.graal.python.builtins.objects.dict.PDict;
import com.oracle.graal.python.builtins.objects.function.PArguments;
import com.oracle.graal.python.builtins.objects.function.PArguments.ThreadState;
import com.oracle.graal.python.builtins.objects.set.PSet;
import com.oracle.graal.python.nodes.PNode;
import com.oracle.graal.python.nodes.expression.ExpressionNode;
import com.oracle.graal.python.runtime.object.PythonObjectFactory;
import com.oracle.graal.python.runtime.sequence.storage.SequenceStorage;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.profiles.ConditionProfile;

@GenerateNodeFactory
public abstract class SetLiteralNode extends LiteralNode {
    @Child private PythonObjectFactory factory = PythonObjectFactory.create();
    @Children private final ExpressionNode[] values;
    @Child private SequenceStorageNodes.LenNode lenNode;
    @Child private SequenceStorageNodes.GetItemNode getItemNode;

    private final boolean hasStarredExpressions;

    protected SetLiteralNode(ExpressionNode[] values) {
        this.values = values;
        for (PNode v : values) {
            if (v instanceof StarredExpressionNode) {
                hasStarredExpressions = true;
                return;
            }
        }
        hasStarredExpressions = false;
    }

    @Specialization
    public PSet expand(VirtualFrame frame,
                    @Cached("createBinaryProfile()") ConditionProfile hasFrame,
                    @CachedLibrary(limit = "3") HashingStorageLibrary lib) {
        if (!hasStarredExpressions) {
            return directSet(frame, hasFrame, lib);
        } else {
            return expandingSet(frame, hasFrame, lib);
        }
    }

    @ExplodeLoop
    private PSet expandingSet(VirtualFrame frame, ConditionProfile hasFrame, HashingStorageLibrary lib) {
        // we will usually have more than 'values.length' elements
        HashingStorage storage = PDict.createNewStorage(true, values.length);
        ThreadState state = PArguments.getThreadState(frame);
        for (ExpressionNode n : values) {
            if (n instanceof StarredExpressionNode) {
                storage = addAllElement(frame, storage, ((StarredExpressionNode) n).getStorage(frame), hasFrame, lib);
            } else {
                Object element = n.execute(frame);
                if (hasFrame.profile(frame != null)) {
                    storage = lib.setItemWithState(storage, element, PNone.NONE, state);
                } else {
                    storage = lib.setItem(storage, element, PNone.NONE);
                }
            }
        }
        return factory.createSet(storage);
    }

    private HashingStorage addAllElement(VirtualFrame frame, HashingStorage setStorage, SequenceStorage sequenceStorage,
                    ConditionProfile hasFrame, HashingStorageLibrary lib) {
        HashingStorage storage = setStorage;
        ThreadState state = PArguments.getThreadState(frame);
        int n = ensureLenNode().execute(sequenceStorage);
        for (int i = 0; i < n; i++) {
            Object element = ensureGetItemNode().execute(frame, sequenceStorage, i);
            if (hasFrame.profile(frame != null)) {
                storage = lib.setItemWithState(storage, element, PNone.NONE, state);
            } else {
                storage = lib.setItem(storage, element, PNone.NONE);
            }
        }
        return storage;
    }

    @ExplodeLoop
    private PSet directSet(VirtualFrame frame, ConditionProfile hasFrame, HashingStorageLibrary lib) {
        HashingStorage storage = PDict.createNewStorage(true, values.length);
        ThreadState state = PArguments.getThreadState(frame);
        for (ExpressionNode v : this.values) {
            Object element = v.execute(frame);
            if (hasFrame.profile(frame != null)) {
                storage = lib.setItemWithState(storage, element, PNone.NONE, state);
            } else {
                storage = lib.setItem(storage, element, PNone.NONE);
            }
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

}
