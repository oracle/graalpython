/*
 * Copyright (c) 2017, 2018, Oracle and/or its affiliates.
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

import com.oracle.graal.python.builtins.objects.cell.PCell;
import com.oracle.graal.python.nodes.PBaseNode;
import com.oracle.graal.python.nodes.PNode;
import com.oracle.graal.python.nodes.cell.ReadLocalCellNodeGen.ReadFromCellNodeGen;
import com.oracle.graal.python.nodes.frame.ReadLocalNode;
import com.oracle.graal.python.nodes.frame.ReadLocalVariableNode;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.FrameSlot;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.NodeCost;
import com.oracle.truffle.api.nodes.NodeInfo;
import com.oracle.truffle.api.profiles.ValueProfile;

@NodeInfo(shortName = "read_cell")
public abstract class ReadLocalCellNode extends PNode implements ReadLocalNode {
    @Child private PNode readLocal;
    @Child private ReadFromCellNode readCell;
    private final FrameSlot frameSlot;

    ReadLocalCellNode(FrameSlot frameSlot, boolean isFreeVar) {
        this.frameSlot = frameSlot;
        this.readLocal = ReadLocalVariableNode.create(frameSlot);
        this.readCell = ReadFromCellNodeGen.create(isFreeVar, frameSlot.getIdentifier());
    }

    public static PNode create(FrameSlot frameSlot, boolean isFreeVar) {
        return ReadLocalCellNodeGen.create(frameSlot, isFreeVar);
    }

    @Override
    public PNode makeWriteNode(PNode rhs) {
        return WriteLocalCellNode.create(frameSlot, rhs);
    }

    static abstract class ReadFromCellNode extends PBaseNode {
        private final boolean isFreeVar;
        private final Object identifier;

        public ReadFromCellNode(boolean isFreeVar, Object identifier) {
            this.isFreeVar = isFreeVar;
            this.identifier = identifier;
        }

        abstract Object execute(Object cell);

        @Specialization(guards = "cachedCell == cell", limit = "1", assumptions = "cachedCell.isEffectivelyFinalAssumption()")
        Object readFinal(@SuppressWarnings("unused") PCell cell,
                        @SuppressWarnings("unused") @Cached("cell") PCell cachedCell,
                        @Cached("cell.getRef()") Object ref) {
            if (ref != null) {
                return ref;
            } else {
                throw raiseUnbound();
            }
        }

        @Specialization(replaces = "readFinal")
        Object read(PCell cell,
                        @Cached("createClassProfile()") ValueProfile refTypeProfile) {
            Object ref = refTypeProfile.profile(cell.getRef());
            if (ref != null) {
                return ref;
            } else {
                throw raiseUnbound();
            }
        }

        @Fallback
        Object read(Object cell) {
            CompilerDirectives.transferToInterpreter();
            throw new IllegalStateException("Expected a cell, got: " + cell.toString() + " instead.");
        }

        private PException raiseUnbound() {
            if (isFreeVar) {
                return raise(NameError, "free variable '%s' referenced before assignment in enclosing scope", identifier);
            }
            return raise(UnboundLocalError, "local variable '%s' referenced before assignment", identifier);
        }
    }

    @Specialization
    Object readObject(VirtualFrame frame) {
        return readCell.execute(readLocal.execute(frame));
    }

    @Override
    public NodeCost getCost() {
        return NodeCost.NONE;
    }
}
