/*
 * Copyright (c) 2017, 2021, Oracle and/or its affiliates.
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
package com.oracle.graal.python.nodes.frame;

import static com.oracle.graal.python.nodes.frame.FrameSlotIDs.RETURN_SLOT_ID;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.UnboundLocalError;

import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.expression.ExpressionNode;
import com.oracle.graal.python.nodes.instrumentation.NodeObjectDescriptor;
import com.oracle.graal.python.nodes.statement.StatementNode;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.FrameSlot;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.instrumentation.StandardTags;
import com.oracle.truffle.api.instrumentation.Tag;
import com.oracle.truffle.api.nodes.NodeInfo;

@NodeInfo(shortName = "read_local")
public abstract class ReadLocalVariableNode extends ExpressionNode implements ReadLocalNode, FrameSlotNode {

    protected final FrameSlot frameSlot;

    protected ReadLocalVariableNode(FrameSlot frameSlot) {
        this.frameSlot = frameSlot;
    }

    public static ReadLocalVariableNode create(FrameSlot slot) {
        assert slot != null;
        return ReadLocalVariableNodeGen.create(slot);
    }

    @Override
    public final FrameSlot getSlot() {
        return frameSlot;
    }

    @Specialization(guards = "frame.isBoolean(frameSlot)")
    boolean readLocalBoolean(VirtualFrame frame) {
        return frame.getBoolean(frameSlot);
    }

    @Specialization(guards = "frame.isInt(frameSlot)")
    int readLocalInt(VirtualFrame frame) {
        return frame.getInt(frameSlot);
    }

    @Specialization(guards = "frame.isLong(frameSlot)")
    long readLocalLong(VirtualFrame frame) {
        return frame.getLong(frameSlot);
    }

    @Specialization(guards = "frame.isDouble(frameSlot)")
    double readLocalDouble(VirtualFrame frame) {
        return frame.getDouble(frameSlot);
    }

    protected final Object getObjectResult(VirtualFrame frame) {
        return frame.getObject(frameSlot);
    }

    @Specialization(guards = {"frame.isObject(frameSlot)", "result != null"})
    static Object readLocalObject(@SuppressWarnings("unused") VirtualFrame frame,
                    @Bind("getObjectResult(frame)") Object result) {
        return result;
    }

    @Specialization(guards = {"frame.isObject(frameSlot)", "getObjectResult(frame) == null"})
    Object readLocalObjectNull(@SuppressWarnings("unused") VirtualFrame frame,
                    @Cached PRaiseNode raise) {
        if (frameSlot.getIdentifier() == RETURN_SLOT_ID) {
            return PNone.NONE;
        } else {
            throw raise.raise(UnboundLocalError, ErrorMessages.LOCAL_VAR_REFERENCED_BEFORE_ASSIGMENT, frameSlot.getIdentifier());
        }
    }

    @Override
    public StatementNode makeWriteNode(ExpressionNode rhs) {
        return WriteLocalVariableNode.create(frameSlot, rhs);
    }

    @Override
    public boolean hasTag(Class<? extends Tag> tag) {
        return StandardTags.ReadVariableTag.class == tag || super.hasTag(tag);
    }

    @Override
    public Object getNodeObject() {
        return NodeObjectDescriptor.createNodeObjectDescriptor(StandardTags.ReadVariableTag.NAME, frameSlot.getIdentifier());
    }
}
