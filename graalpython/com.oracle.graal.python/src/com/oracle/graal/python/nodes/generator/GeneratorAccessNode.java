/*
 * Copyright (c) 2017, 2020, Oracle and/or its affiliates.
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

import java.util.Arrays;

import com.oracle.graal.python.builtins.objects.function.PArguments;
import com.oracle.graal.python.builtins.objects.generator.GeneratorControlData;
import com.oracle.graal.python.nodes.util.ExceptionStateNodes.ExceptionState;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.NodeCost;
import com.oracle.truffle.api.profiles.ValueProfile;

final class GeneratorAccessNode extends Node {

    private final ValueProfile frameProfile = ValueProfile.createClassProfile();

    private static final byte UNSET = -1;
    private static final byte VOLATILE = -2;
    private static final byte TRUE = 1;
    private static final byte FALSE = 2;

    @CompilationFinal(dimensions = 1) private byte[] active = new byte[0];
    @CompilationFinal(dimensions = 1) private int[] indices = new int[0];

    private GeneratorAccessNode() {
        // private constructor
    }

    @Override
    public NodeCost getCost() {
        return NodeCost.NONE;
    }

    private GeneratorControlData getControlData(VirtualFrame frame) {
        return PArguments.getControlDataFromGeneratorFrame(frameProfile.profile(PArguments.getGeneratorFrame(frame)));
    }

    public boolean isActive(VirtualFrame frame, int flagSlot) {
        if (active.length <= flagSlot) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            synchronized (this) {
                if (active.length <= flagSlot) {
                    byte[] newActive = new byte[flagSlot + 1];
                    Arrays.fill(newActive, UNSET);
                    System.arraycopy(active, 0, newActive, 0, active.length);
                    active = newActive;
                }
            }
        }
        if (active[flagSlot] == UNSET) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            active[flagSlot] = getControlData(frame).getActive(flagSlot) ? TRUE : FALSE;
        }
        if (active[flagSlot] == VOLATILE) {
            return getControlData(frame).getActive(flagSlot);
        }
        boolean returnValue = active[flagSlot] == TRUE;
        if (returnValue != getControlData(frame).getActive(flagSlot)) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            active[flagSlot] = VOLATILE;
            return getControlData(frame).getActive(flagSlot);
        } else {
            return returnValue;
        }
    }

    public void setActive(VirtualFrame frame, int flagSlot, boolean value) {
        getControlData(frame).setActive(flagSlot, value);
    }

    public int getIndex(VirtualFrame frame, int blockIndexSlot) {
        if (indices.length <= blockIndexSlot) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            synchronized (this) {
                if (indices.length <= blockIndexSlot) {
                    int[] newIndices = new int[blockIndexSlot + 1];
                    Arrays.fill(newIndices, UNSET);
                    System.arraycopy(indices, 0, newIndices, 0, indices.length);
                    indices = newIndices;
                }
            }
        }
        if (indices[blockIndexSlot] == UNSET) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            indices[blockIndexSlot] = getControlData(frame).getBlockIndexAt(blockIndexSlot);
        }
        if (indices[blockIndexSlot] == VOLATILE) {
            return getControlData(frame).getBlockIndexAt(blockIndexSlot);
        }
        int returnValue = indices[blockIndexSlot];
        if (returnValue != getControlData(frame).getBlockIndexAt(blockIndexSlot)) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            indices[blockIndexSlot] = VOLATILE;
            return getControlData(frame).getBlockIndexAt(blockIndexSlot);
        } else {
            return returnValue;
        }
    }

    public void setIndex(VirtualFrame frame, int blockIndexSlot, int value) {
        getControlData(frame).setBlockIndexAt(blockIndexSlot, value);
    }

    public Object getIterator(VirtualFrame frame, int iteratorSlot) {
        return getControlData(frame).getIteratorAt(iteratorSlot);
    }

    public void setIterator(VirtualFrame frame, int iteratorSlot, Object value) {
        getControlData(frame).setIteratorAt(iteratorSlot, value);
    }

    public PException getActiveException(VirtualFrame frame, int slot) {
        return getControlData(frame).getActiveException(slot);
    }

    public void setActiveException(VirtualFrame frame, int slot, PException ex) {
        getControlData(frame).setActiveException(slot, ex);
    }

    public static GeneratorAccessNode create() {
        return new GeneratorAccessNode();
    }

    public void setLastYieldIndex(VirtualFrame frame, int lastYieldIndex) {
        getControlData(frame).setLastYieldIndex(lastYieldIndex);
    }
}
