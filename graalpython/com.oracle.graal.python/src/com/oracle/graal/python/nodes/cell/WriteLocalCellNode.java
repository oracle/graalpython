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
package com.oracle.graal.python.nodes.cell;

import static com.oracle.graal.python.builtins.objects.PNone.NO_VALUE;

import com.oracle.graal.python.builtins.objects.cell.PCell;
import com.oracle.graal.python.nodes.expression.ExpressionNode;
import com.oracle.graal.python.nodes.frame.WriteIdentifierNode;
import com.oracle.graal.python.nodes.statement.StatementNode;
import com.oracle.truffle.api.Assumption;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.FrameSlot;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.NodeInfo;
import com.oracle.truffle.api.profiles.ConditionProfile;

@NodeInfo(shortName = "write_cell")
@NodeChild(value = "rhs", type = ExpressionNode.class)
public abstract class WriteLocalCellNode extends StatementNode implements WriteIdentifierNode {
    @Child private ExpressionNode readLocal;

    private final FrameSlot frameSlot;

    WriteLocalCellNode(FrameSlot frameSlot, ExpressionNode readLocalNode) {
        this.frameSlot = frameSlot;
        this.readLocal = readLocalNode;
    }

    public static WriteLocalCellNode create(FrameSlot frameSlot, ExpressionNode readLocal, ExpressionNode right) {
        return WriteLocalCellNodeGen.create(frameSlot, readLocal, right);
    }

    @Override
    public void doWrite(VirtualFrame frame, Object value) {
        executeWithValue(frame, value);
    }

    public abstract void executeWithValue(VirtualFrame frame, Object value);

    @Specialization
    void writeObject(VirtualFrame frame, Object value,
                    @Cached WriteToCellNode writeToCellNode,
                    @Cached("createBinaryProfile()") ConditionProfile profile) {
        Object localValue = readLocal.execute(frame);
        if (profile.profile(localValue instanceof PCell)) {
            writeToCellNode.execute((PCell) localValue, value);
            return;
        }
        CompilerDirectives.transferToInterpreterAndInvalidate();
        throw new IllegalStateException("Expected a cell, got: " + localValue.toString() + " instead.");
    }

    @Override
    public Object getIdentifier() {
        return frameSlot.getIdentifier();
    }

    abstract static class WriteToCellNode extends Node {

        public abstract void execute(PCell cell, Object value);

        @Specialization(guards = "cell == cachedCell", limit = "1")
        void doWriteCached(@SuppressWarnings("unused") PCell cell, Object value,
                        @Cached("cell") PCell cachedCell) {
            doWriteGeneric(cachedCell, value);
        }

        @Specialization(guards = "cell.isEffectivelyFinalAssumption() == effectivelyFinalAssumption", limit = "1", assumptions = "effectivelyFinalAssumption")
        void doWriteCachedAssumption(PCell cell, Object value,
                        @SuppressWarnings("unused") @Cached("cell.isEffectivelyFinalAssumption()") Assumption effectivelyFinalAssumption) {
            if (value == NO_VALUE) {
                cell.clearRef();
            } else {
                cell.setRef(value, effectivelyFinalAssumption);
            }
        }

        @Specialization(replaces = {"doWriteCached", "doWriteCachedAssumption"})
        void doWriteGeneric(PCell cell, Object value) {
            if (value == NO_VALUE) {
                cell.clearRef();
            } else {
                cell.setRef(value);
            }
        }
    }
}
