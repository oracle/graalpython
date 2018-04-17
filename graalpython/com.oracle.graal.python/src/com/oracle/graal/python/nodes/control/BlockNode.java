/*
 * Copyright (c) 2017, Oracle and/or its affiliates.
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
package com.oracle.graal.python.nodes.control;

import java.util.List;

import com.oracle.graal.python.nodes.EmptyNode;
import com.oracle.graal.python.nodes.PNode;
import com.oracle.graal.python.nodes.generator.YieldNode;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.nodes.UnexpectedResultException;

public final class BlockNode extends BaseBlockNode {

    private BlockNode(PNode[] statements) {
        super(statements);
    }

    public static PNode create(PNode... statements) {
        int length = statements.length;

        if (length == 0) {
            return EmptyNode.create();
        } else if (length == 1) {
            return statements[0] instanceof YieldNode ? new BlockNode(statements) : statements[0];
        } else {
            return new BlockNode(statements);
        }
    }

    @Override
    public BaseBlockNode insertNodesBefore(PNode insertBefore, List<PNode> insertees) {
        return new BlockNode(insertStatementsBefore(insertBefore, insertees));
    }

    @ExplodeLoop
    private void executeFirst(VirtualFrame frame) {
        for (int i = 0; i < statements.length - 1; i++) {
            statements[i].executeVoid(frame);
        }
    }

    @Override
    public Object execute(VirtualFrame frame) {
        executeFirst(frame);
        return statements[statements.length - 1].execute(frame);
    }

    @Override
    public boolean executeBoolean(VirtualFrame frame) throws UnexpectedResultException {
        executeFirst(frame);
        return statements[statements.length - 1].executeBoolean(frame);
    }

    @Override
    public int executeInt(VirtualFrame frame) throws UnexpectedResultException {
        executeFirst(frame);
        return statements[statements.length - 1].executeInt(frame);
    }

    @Override
    public long executeLong(VirtualFrame frame) throws UnexpectedResultException {
        executeFirst(frame);
        return statements[statements.length - 1].executeLong(frame);
    }

    @Override
    public double executeDouble(VirtualFrame frame) throws UnexpectedResultException {
        executeFirst(frame);
        return statements[statements.length - 1].executeDouble(frame);
    }

    @Override
    @ExplodeLoop
    public void executeVoid(VirtualFrame frame) {
        for (int i = 0; i < statements.length; i++) {
            statements[i].executeVoid(frame);
        }
    }
}
