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
package com.oracle.graal.python.nodes.generator;

import com.oracle.graal.python.builtins.objects.function.PArguments;
import com.oracle.graal.python.nodes.expression.ExpressionNode;
import com.oracle.graal.python.nodes.frame.FrameSlotNode;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.frame.FrameSlot;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.profiles.ValueProfile;

/**
 * Transfer a local variable value from the current frame to a cargo frame.
 */
@NodeChild(value = "right", type = ExpressionNode.class)
@GenerateNodeFactory
public abstract class FrameTransferNode extends FrameSlotNode {

    private final ValueProfile frameProfile = ValueProfile.createClassProfile();

    public FrameTransferNode(FrameSlot slot) {
        super(slot);
    }

    protected FrameTransferNode(FrameTransferNode prev) {
        super(prev.frameSlot);
    }

    protected abstract Object execute(VirtualFrame frame, Object value);

    private Frame getCargoFrame(VirtualFrame frame) {
        return frameProfile.profile(PArguments.getGeneratorFrame(frame));
    }

    @Specialization(guards = "isBooleanKind(frame)")
    public boolean write(VirtualFrame frame, boolean value) {
        getCargoFrame(frame).setBoolean(frameSlot, value);
        return value;
    }

    @Specialization(guards = "isIntegerKind(frame)")
    public int doInteger(VirtualFrame frame, int value) {
        getCargoFrame(frame).setInt(frameSlot, value);
        return value;
    }

    @Specialization(guards = "isLongKind(frame)")
    public long doLong(VirtualFrame frame, long value) {
        getCargoFrame(frame).setLong(frameSlot, value);
        return value;
    }

    @Specialization(guards = "isDoubleKind(frame)")
    public double doDouble(VirtualFrame frame, double value) {
        getCargoFrame(frame).setDouble(frameSlot, value);
        return value;
    }

    @Specialization
    public Object write(VirtualFrame frame, Object value) {
        ensureObjectKind(frame);
        setObject(getCargoFrame(frame), value);
        return value;
    }
}
