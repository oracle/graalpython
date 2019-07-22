/*
 * Copyright (c) 2017, 2019, Oracle and/or its affiliates.
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
package com.oracle.graal.python.nodes.generator;

import com.oracle.graal.python.builtins.objects.function.PArguments;
import com.oracle.graal.python.builtins.objects.generator.GeneratorControlData;
import com.oracle.graal.python.nodes.util.ExceptionStateNodes.ExceptionState;
import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.frame.FrameSlot;
import com.oracle.truffle.api.frame.FrameUtil;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.NodeCost;
import com.oracle.truffle.api.profiles.ValueProfile;

final class GeneratorAccessNode extends Node {

    private final ValueProfile frameProfile = ValueProfile.createClassProfile();

    private GeneratorAccessNode() {
        // private constructor
    }

    @Override
    public NodeCost getCost() {
        return NodeCost.NONE;
    }

    private GeneratorControlData getControlData(VirtualFrame frame) {
        return PArguments.getControlDataFromGeneratorFrame(getGeneratorFrame(frame));
    }

    private Frame getGeneratorFrame(VirtualFrame frame) {
        return frameProfile.profile(PArguments.getGeneratorFrame(frame));
    }

    public boolean isActive(VirtualFrame frame, FrameSlot flagSlot) {
        return FrameUtil.getBooleanSafe(getGeneratorFrame(frame), flagSlot);
    }

    public void setActive(VirtualFrame frame, FrameSlot flagSlot, boolean value) {
        getGeneratorFrame(frame).setBoolean(flagSlot, value);
    }

    public int getIndex(VirtualFrame frame, FrameSlot blockIndexSlot) {
        return FrameUtil.getIntSafe(getGeneratorFrame(frame), blockIndexSlot);
    }

    public void setIndex(VirtualFrame frame, FrameSlot blockIndexSlot, int value) {
        getGeneratorFrame(frame).setInt(blockIndexSlot, value);
    }

    public Object getIterator(VirtualFrame frame, FrameSlot withObjectSlot) {
        return FrameUtil.getObjectSafe(getGeneratorFrame(frame), withObjectSlot);
    }

    public void setIterator(VirtualFrame frame, FrameSlot withObjectSlot, Object value) {
        getGeneratorFrame(frame).setObject(withObjectSlot, value);
    }

    public ExceptionState getActiveException(VirtualFrame frame) {
        return getControlData(frame).getActiveException();
    }

    public void setActiveException(VirtualFrame frame, ExceptionState ex) {
        getControlData(frame).setActiveException(ex);
    }

    public static GeneratorAccessNode create() {
        return new GeneratorAccessNode();
    }
}
