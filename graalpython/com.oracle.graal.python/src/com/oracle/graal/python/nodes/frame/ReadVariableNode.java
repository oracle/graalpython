/*
 * Copyright (c) 2017, 2018, Oracle and/or its affiliates.
 * Copyright (c) 2014, Regents of the University of California
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
package com.oracle.graal.python.nodes.frame;

import static com.oracle.graal.python.nodes.frame.FrameSlotIDs.RETURN_SLOT_ID;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.UnboundLocalError;

import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.frame.FrameSlot;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.NodeCost;
import com.oracle.truffle.api.nodes.UnexpectedResultException;

public abstract class ReadVariableNode extends FrameSlotNode implements ReadLocalNode {

    @Child private ReadVariableFromFrameNode readLocalNode;

    protected ReadVariableNode(FrameSlot slot) {
        super(slot);
        this.readLocalNode = ReadVariableFromFrameNode.create(slot);
    }

    @Override
    public final Object doWrite(VirtualFrame frame, Object value) {
        throw new UnsupportedOperationException();
    }

    protected abstract Frame getAccessingFrame(VirtualFrame frame);

    @Override
    public final NodeCost getCost() {
        // the actual reading is done in a child node
        return NodeCost.NONE;
    }

    @Override
    public final Object execute(VirtualFrame frame) {
        Object value = readLocalNode.execute(getAccessingFrame(frame));

        if (value == null) {
            if (frameSlot.getIdentifier().equals(RETURN_SLOT_ID)) {
                value = PNone.NONE;
            } else {
                throw raise(UnboundLocalError, "local variable '%s' referenced before assignment", frameSlot.getIdentifier());
            }
        }

        return value;
    }

    @Override
    public final boolean executeBoolean(VirtualFrame frame) throws UnexpectedResultException {
        return readLocalNode.executeBoolean(getAccessingFrame(frame));
    }

    @Override
    public final int executeInt(VirtualFrame frame) throws UnexpectedResultException {
        return readLocalNode.executeInt(getAccessingFrame(frame));
    }

    @Override
    public final long executeLong(VirtualFrame frame) throws UnexpectedResultException {
        return readLocalNode.executeLong(getAccessingFrame(frame));
    }

    @Override
    public final double executeDouble(VirtualFrame frame) throws UnexpectedResultException {
        return readLocalNode.executeDouble(getAccessingFrame(frame));
    }
}
