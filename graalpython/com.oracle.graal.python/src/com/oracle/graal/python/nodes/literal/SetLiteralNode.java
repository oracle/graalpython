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

import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.common.HashingStorage;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodes;
import com.oracle.graal.python.builtins.objects.dict.PDict;
import com.oracle.graal.python.nodes.expression.ExpressionNode;
import com.oracle.graal.python.runtime.object.PythonObjectFactory;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.ExplodeLoop;

public final class SetLiteralNode extends LiteralNode {
    @Child private PythonObjectFactory factory = PythonObjectFactory.create();
    @Children private final ExpressionNode[] values;
    @Child HashingStorageNodes.SetItemNode setItemNode;

    public SetLiteralNode(ExpressionNode[] values) {
        this.values = values;
    }

    @Override
    @ExplodeLoop
    public Object execute(VirtualFrame frame) {
        HashingStorage storage = PDict.createNewStorage(true, values.length);

        if (setItemNode == null && values.length > 0) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            setItemNode = insert(HashingStorageNodes.SetItemNode.create());
        }
        for (ExpressionNode v : this.values) {
            storage = setItemNode.execute(frame, storage, v.execute(frame), PNone.NO_VALUE);
        }

        return factory.createSet(storage);
    }
}
