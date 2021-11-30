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
package com.oracle.graal.python.nodes.generator;

import static com.oracle.graal.python.nodes.frame.FrameSlotIDs.RETURN_SLOT_ID;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.UnboundLocalError;

import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.function.PArguments;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.expression.ExpressionNode;
import com.oracle.graal.python.nodes.frame.FrameSlotNode;
import com.oracle.graal.python.nodes.frame.PythonFrame;
import com.oracle.graal.python.nodes.frame.ReadLocalNode;
import com.oracle.graal.python.nodes.instrumentation.NodeObjectDescriptor;
import com.oracle.graal.python.nodes.statement.StatementNode;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.instrumentation.StandardTags;
import com.oracle.truffle.api.instrumentation.Tag;
import com.oracle.truffle.api.profiles.ValueProfile;

@ImportStatic(PythonFrame.class)
public abstract class ReadGeneratorFrameVariableNode extends ExpressionNode implements ReadLocalNode, FrameSlotNode {

    private final ValueProfile frameProfile = ValueProfile.createClassProfile();
    protected final int frameSlot;

    protected ReadGeneratorFrameVariableNode(int frameSlot) {
        this.frameSlot = frameSlot;
    }

    public static ReadGeneratorFrameVariableNode create(int slot) {
        return ReadGeneratorFrameVariableNodeGen.create(slot);
    }

    protected final Frame getGeneratorFrame(VirtualFrame frame) {
        return frameProfile.profile(PArguments.getGeneratorFrame(frame));
    }

    @Override
    public final int getSlotIndex() {
        return frameSlot;
    }

    @Specialization(guards = "generatorFrame.isBoolean(frameSlot)")
    boolean readLocalBoolean(@SuppressWarnings("unused") VirtualFrame frame,
                    @Bind("getGeneratorFrame(frame)") Frame generatorFrame) {
        return generatorFrame.getBoolean(frameSlot);
    }

    @Specialization(guards = "generatorFrame.isInt(frameSlot)")
    int readLocalInt(@SuppressWarnings("unused") VirtualFrame frame,
                    @Bind("getGeneratorFrame(frame)") Frame generatorFrame) {
        return generatorFrame.getInt(frameSlot);
    }

    @Specialization(guards = "generatorFrame.isLong(frameSlot)")
    long readLocalLong(@SuppressWarnings("unused") VirtualFrame frame,
                    @Bind("getGeneratorFrame(frame)") Frame generatorFrame) {
        return generatorFrame.getLong(frameSlot);
    }

    @Specialization(guards = "generatorFrame.isDouble(frameSlot)")
    double readLocalDouble(@SuppressWarnings("unused") VirtualFrame frame,
                    @Bind("getGeneratorFrame(frame)") Frame generatorFrame) {
        return generatorFrame.getDouble(frameSlot);
    }

    @Specialization(guards = {"generatorFrame.isObject(frameSlot)", "result != null"})
    static Object readLocalObject(@SuppressWarnings("unused") VirtualFrame frame,
                    @SuppressWarnings("unused") @Bind("getGeneratorFrame(frame)") Frame generatorFrame,
                    @Bind("generatorFrame.getObject(frameSlot)") Object result) {
        return result;
    }

    @Specialization(guards = {"generatorFrame.isObject(frameSlot)", "generatorFrame.getObject(frameSlot) == null"})
    Object readLocalObjectNull(@SuppressWarnings("unused") VirtualFrame frame,
                    @SuppressWarnings("unused") @Bind("getGeneratorFrame(frame)") Frame generatorFrame,
                    @Cached PRaiseNode raise) {
        assert frame.getFrameDescriptor() == generatorFrame.getFrameDescriptor();
        Object identifier = frame.getFrameDescriptor().getSlotName(frameSlot);
        if (identifier == RETURN_SLOT_ID) {
            return PNone.NONE;
        } else {
            throw raise.raise(UnboundLocalError, ErrorMessages.LOCAL_VAR_REFERENCED_BEFORE_ASSIGMENT, identifier);
        }
    }

    @Override
    public StatementNode makeWriteNode(ExpressionNode rhs) {
        return WriteGeneratorFrameVariableNode.create(frameSlot, rhs);
    }

    @Override
    public boolean hasTag(Class<? extends Tag> tag) {
        return StandardTags.ReadVariableTag.class == tag || super.hasTag(tag);
    }

    @Override
    public Object getNodeObject() {
        return NodeObjectDescriptor.createNodeObjectDescriptor(StandardTags.ReadVariableTag.NAME, getRootNode().getFrameDescriptor().getSlotName(frameSlot));
    }
}
