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

import com.oracle.graal.python.builtins.objects.cell.PCell;
import com.oracle.graal.python.nodes.expression.ExpressionNode;
import com.oracle.graal.python.nodes.function.FunctionRootNode;
import com.oracle.graal.python.nodes.statement.StatementNode;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.FrameSlot;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.NodeInfo;

@NodeInfo(shortName = "write_cellvar")
public abstract class WriteCellVarNode extends StatementNode {
    private int cellIndex = -1;
    private final FunctionRootNode cellSupplier;
    @Child private ExpressionNode readNode;

    WriteCellVarNode(ExpressionNode readNode, FunctionRootNode cellSupplier, String identifier) {
        this.readNode = readNode;
        this.cellSupplier = cellSupplier;

        FrameSlot[] cellVarSlots = cellSupplier.getCellVarSlots();
        for (int i = 0; i < cellVarSlots.length; i++) {
            FrameSlot frameSlot = cellVarSlots[i];
            if (frameSlot.getIdentifier().equals(identifier)) {
                cellIndex = i;
                break;
            }
        }
    }

    public static WriteCellVarNode create(ExpressionNode readNode, FunctionRootNode cellSupplier, String identifier) {
        return WriteCellVarNodeGen.create(readNode, cellSupplier, identifier);
    }

    @Specialization
    void setCell(VirtualFrame frame) {
        PCell[] cells = cellSupplier.getCells();
        if (cellIndex >= 0) {
            PCell cell = cells[cellIndex];
            if (cell != null) {
                Object value = readNode.execute(frame);
                cell.setRef(value);
            }
        }
    }
}
