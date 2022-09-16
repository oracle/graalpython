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

import com.oracle.graal.python.nodes.expression.ExpressionNode;
import com.oracle.graal.python.nodes.statement.StatementNode;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.instrumentation.StandardTags;
import com.oracle.truffle.api.instrumentation.Tag;
import com.oracle.truffle.api.nodes.NodeInfo;

@NodeInfo(shortName = "write_local")
@NodeChild(value = "rightNode", type = ExpressionNode.class)
@ImportStatic(FrameSlotGuards.class)
public abstract class WriteLocalVariableNode extends StatementNode implements WriteIdentifierNode, FrameSlotNode {

    protected final int frameSlot;

    public WriteLocalVariableNode(int frameSlot) {
        this.frameSlot = frameSlot;
    }

    public static WriteLocalVariableNode create(int frameSlot, ExpressionNode right) {
        return WriteLocalVariableNodeGen.create(frameSlot, right);
    }

    public abstract ExpressionNode getRightNode();

    @Override
    public final ExpressionNode getRhs() {
        return getRightNode();
    }

    @Override
    public final int getSlotIndex() {
        return frameSlot;
    }

    @Override
    public final Object getIdentifier() {
        return getRootNode().getFrameDescriptor().getSlotName(frameSlot);
    }

    @Specialization(guards = "isBooleanKind(frame, frameSlot)")
    void writeBoolean(VirtualFrame frame, boolean value) {
        frame.setBoolean(frameSlot, value);
    }

    @Specialization(guards = "isIntegerKind(frame, frameSlot)")
    void writeInt(VirtualFrame frame, int value) {
        frame.setInt(frameSlot, value);
    }

    @Specialization(guards = "isLongKind(frame, frameSlot)")
    void writeLong(VirtualFrame frame, long value) {
        frame.setLong(frameSlot, value);
    }

    @Specialization(guards = "isDoubleKind(frame, frameSlot)")
    void writeDouble(VirtualFrame frame, double value) {
        frame.setDouble(frameSlot, value);
    }

    @Specialization(replaces = {"writeBoolean", "writeInt", "writeDouble", "writeLong"})
    void writeObject(VirtualFrame frame, Object value) {
        FrameSlotGuards.ensureObjectKind(frame, frameSlot);
        frame.setObject(frameSlot, value);
    }

    @Override
    public boolean hasTag(Class<? extends Tag> tag) {
        return StandardTags.WriteVariableTag.class == tag || super.hasTag(tag);
    }
}
