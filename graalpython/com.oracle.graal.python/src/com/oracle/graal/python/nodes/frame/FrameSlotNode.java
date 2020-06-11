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
package com.oracle.graal.python.nodes.frame;

import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.objects.ints.PInt;
import com.oracle.graal.python.nodes.expression.ExpressionNode;
import com.oracle.graal.python.nodes.object.IsBuiltinClassProfile;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.frame.FrameSlot;
import com.oracle.truffle.api.frame.FrameSlotKind;
import com.oracle.truffle.api.frame.FrameUtil;

public abstract class FrameSlotNode extends ExpressionNode {
    @Child private IsBuiltinClassProfile isPrimitiveIntProfile = IsBuiltinClassProfile.create();

    protected boolean isPrimitiveInt(PInt value) {
        return isPrimitiveIntProfile.profileObject(value, PythonBuiltinClassType.PInt);
    }

    protected final FrameSlot frameSlot;

    public FrameSlotNode(FrameSlot slot) {
        this.frameSlot = slot;
    }

    public final FrameSlot getSlot() {
        return frameSlot;
    }

    protected final void setObject(Frame frame, Object value) {
        frame.setObject(frameSlot, value);
    }

    protected final int getInteger(Frame frame) {
        return FrameUtil.getIntSafe(frame, frameSlot);
    }

    protected final long getLong(Frame frame) {
        return FrameUtil.getLongSafe(frame, frameSlot);
    }

    protected final boolean getBoolean(Frame frame) {
        return FrameUtil.getBooleanSafe(frame, frameSlot);
    }

    protected final double getDouble(Frame frame) {
        return FrameUtil.getDoubleSafe(frame, frameSlot);
    }

    protected final Object getObject(Frame frame) {
        return FrameUtil.getObjectSafe(frame, frameSlot);
    }

    protected final boolean isNotIllegal(Frame frame) {
        return frame.getFrameDescriptor().getFrameSlotKind(frameSlot) != FrameSlotKind.Illegal;
    }

    protected final boolean isBooleanKind(Frame frame) {
        return isKind(frame, FrameSlotKind.Boolean);
    }

    protected final boolean isIntegerKind(Frame frame) {
        return isKind(frame, FrameSlotKind.Int);
    }

    protected final boolean isLongKind(Frame frame) {
        return isKind(frame, FrameSlotKind.Long);
    }

    protected final boolean isDoubleKind(Frame frame) {
        return isKind(frame, FrameSlotKind.Double);
    }

    protected final boolean isIntOrObjectKind(Frame frame) {
        return isKind(frame, FrameSlotKind.Int) || isKind(frame, FrameSlotKind.Object);
    }

    protected final boolean isLongOrObjectKind(Frame frame) {
        return isKind(frame, FrameSlotKind.Long) || isKind(frame, FrameSlotKind.Object);
    }

    protected final boolean ensureObjectKind(Frame frame) {
        if (frame.getFrameDescriptor().getFrameSlotKind(frameSlot) != FrameSlotKind.Object) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            frame.getFrameDescriptor().setFrameSlotKind(frameSlot, FrameSlotKind.Object);
        }
        return true;
    }

    private boolean isKind(Frame frame, FrameSlotKind kind) {
        return frame.getFrameDescriptor().getFrameSlotKind(frameSlot) == kind || initialSetKind(frame, kind);
    }

    private boolean initialSetKind(Frame frame, FrameSlotKind kind) {
        if (frame.getFrameDescriptor().getFrameSlotKind(frameSlot) == FrameSlotKind.Illegal) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            frame.getFrameDescriptor().setFrameSlotKind(frameSlot, kind);
            return true;
        }
        return false;
    }
}
