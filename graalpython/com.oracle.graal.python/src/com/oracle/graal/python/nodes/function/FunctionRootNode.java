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
package com.oracle.graal.python.nodes.function;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.builtins.objects.cell.PCell;
import com.oracle.graal.python.builtins.objects.function.PArguments;
import com.oracle.graal.python.nodes.PClosureFunctionRootNode;
import com.oracle.graal.python.nodes.PNode;
import com.oracle.graal.python.nodes.cell.CellSupplier;
import com.oracle.graal.python.parser.ExecutionCellSlots;
import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.FrameSlot;
import com.oracle.truffle.api.frame.FrameUtil;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.nodes.NodeUtil;
import com.oracle.truffle.api.source.SourceSection;

/**
 * RootNode of a Python Function body. It is invoked by a CallTarget.
 */
public class FunctionRootNode extends PClosureFunctionRootNode implements CellSupplier {

    private final PCell[] cells;
    private final ExecutionCellSlots executionCellSlots;
    private final String functionName;
    private final SourceSection sourceSection;
    private final boolean isGenerator;

    @Child private PNode body;
    private PNode uninitializedBody;

    public FunctionRootNode(PythonLanguage language, SourceSection sourceSection, String functionName, boolean isGenerator, FrameDescriptor frameDescriptor, PNode body,
                    ExecutionCellSlots executionCellSlots) {
        super(language, frameDescriptor, executionCellSlots);
        this.executionCellSlots = executionCellSlots;
        this.cells = new PCell[this.cellVarSlots.length];

        this.sourceSection = sourceSection;
        assert sourceSection != null;
        this.functionName = functionName;
        this.isGenerator = isGenerator;
        this.body = NodeUtil.cloneNode(body);
        this.uninitializedBody = NodeUtil.cloneNode(body);
    }

    public String getFunctionName() {
        return functionName;
    }

    public PNode getBody() {
        return body;
    }

    public PNode getUninitializedBody() {
        return uninitializedBody;
    }

    @Override
    public String getName() {
        return functionName;
    }

    @Override
    public PCell[] getCells() {
        return cells;
    }

    @Override
    public FrameSlot[] getCellVarSlots() {
        return cellVarSlots;
    }

    @Override
    public FunctionRootNode copy() {
        return new FunctionRootNode(getLanguage(PythonLanguage.class), getSourceSection(), functionName, isGenerator, getFrameDescriptor(), uninitializedBody, executionCellSlots);
    }

    @ExplodeLoop
    private void initializeCellVars(Frame frame) {
        for (int i = 0; i < cellVarSlots.length; i++) {
            FrameSlot frameSlot = cellVarSlots[i];

            // get the cell
            PCell cell = null;
            if (isGenerator) {
                cell = (PCell) FrameUtil.getObjectSafe(frame, frameSlot);
            }
            if (cell == null) {
                cell = new PCell();
            }

            // store the cell as a local var
            frame.setObject(frameSlot, cell);
            this.cells[i] = cell;
        }
    }

    private void initClosureAndCellVars(VirtualFrame frame) {
        Frame accessingFrame = frame;
        if (isGenerator) {
            accessingFrame = PArguments.getGeneratorFrame(frame);
        }

        addClosureCellsToLocals(accessingFrame);
        initializeCellVars(accessingFrame);
    }

    @Override
    public Object execute(VirtualFrame frame) {
        initClosureAndCellVars(frame);
        return body.execute(frame);
    }

    @Override
    public String toString() {
        CompilerAsserts.neverPartOfCompilation();
        return "<function root " + functionName + " at " + Integer.toHexString(hashCode()) + ">";
    }

    @Override
    public SourceSection getSourceSection() {
        return sourceSection;
    }
}
