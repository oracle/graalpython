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
package com.oracle.graal.python.nodes.cell;

import static com.oracle.graal.python.runtime.exception.PythonErrorType.NameError;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.UnboundLocalError;

import com.oracle.graal.python.builtins.objects.cell.CellBuiltins;
import com.oracle.graal.python.builtins.objects.cell.PCell;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.expression.ExpressionNode;
import com.oracle.graal.python.nodes.frame.ReadLocalNode;
import com.oracle.graal.python.nodes.frame.ReadLocalVariableNode;
import com.oracle.graal.python.nodes.statement.StatementNode;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.NodeCost;
import com.oracle.truffle.api.nodes.NodeInfo;
import com.oracle.truffle.api.profiles.ValueProfile;

@NodeInfo(shortName = "read_cell")
public abstract class ReadLocalCellNode extends ExpressionNode implements ReadLocalNode {
    @Child private ExpressionNode readLocal;
    private final int frameSlot;
    private final boolean isFreeVar;

    ReadLocalCellNode(int frameSlot, boolean isFreeVar) {
        this.frameSlot = frameSlot;
        this.readLocal = ReadLocalVariableNode.create(frameSlot);
        this.isFreeVar = isFreeVar;
    }

    ReadLocalCellNode(int frameSlot, boolean isFreeVar, ExpressionNode readLocal) {
        this.frameSlot = frameSlot;
        this.readLocal = readLocal;
        this.isFreeVar = isFreeVar;
    }

    public static ReadLocalCellNode create(int frameSlot, boolean isFreeVar) {
        return ReadLocalCellNodeGen.create(frameSlot, isFreeVar);
    }

    public static ReadLocalCellNode create(int frameSlot, boolean isFreeVar, ExpressionNode readLocal) {
        return ReadLocalCellNodeGen.create(frameSlot, isFreeVar, readLocal);
    }

    @Override
    public StatementNode makeWriteNode(ExpressionNode rhs) {
        return WriteLocalCellNode.create(frameSlot, readLocal, rhs);
    }

    @Specialization
    Object readObject(VirtualFrame frame,
                    @Cached PRaiseNode raise,
                    @Cached CellBuiltins.GetRefNode getRef,
                    @Cached("createClassProfile()") ValueProfile refTypeProfile) {
        Object value = readLocal.execute(frame);
        if (!(value instanceof PCell)) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            throw new IllegalStateException("Expected a cell, got: " + value.toString() + " instead.");
        }
        PCell cell = (PCell) value;
        Object ref = refTypeProfile.profile(getRef.execute(cell));
        if (ref != null) {
            return ref;
        } else {
            Object identifier = frame.getFrameDescriptor().getSlotName(frameSlot);
            if (isFreeVar) {
                throw raise.raise(NameError, ErrorMessages.FREE_VAR_REFERENCED_BEFORE_ASSIGMENT, identifier);
            }
            throw raise.raise(UnboundLocalError, ErrorMessages.LOCAL_VAR_REFERENCED_BEFORE_ASSIGMENT, identifier);
        }
    }

    @Override
    public NodeCost getCost() {
        return NodeCost.NONE;
    }
}
