/*
 * Copyright (c) 2017, 2022, Oracle and/or its affiliates.
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

import com.oracle.graal.python.builtins.objects.cell.PCell;
import com.oracle.graal.python.builtins.objects.function.PArguments;
import com.oracle.graal.python.nodes.expression.ExpressionNode;
import com.oracle.graal.python.nodes.frame.PythonFrame;
import com.oracle.graal.python.parser.DefinitionCellSlots;
import com.oracle.graal.python.parser.ExecutionCellSlots;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.profiles.ConditionProfile;
import com.oracle.truffle.api.profiles.ValueProfile;
import com.oracle.truffle.api.strings.TruffleString;

abstract class ExpressionDefinitionNode extends ExpressionNode {
    private final ValueProfile frameProfile = ValueProfile.createClassProfile();
    private final ConditionProfile isGeneratorProfile = ConditionProfile.createBinaryProfile();

    @CompilationFinal(dimensions = 1) private final int[] freeVarDefinitionSlots;
    protected final RootCallTarget callTarget;

    ExpressionDefinitionNode(DefinitionCellSlots definitionCellSlots, RootCallTarget callTarget) {
        this.freeVarDefinitionSlots = definitionCellSlots.getFreeVarSlots();
        this.callTarget = callTarget;
    }

    @ExplodeLoop
    final PCell[] getClosureFromLocals(Frame frame) {
        if (freeVarDefinitionSlots.length == 0) {
            return null;
        }

        PCell[] closure = new PCell[freeVarDefinitionSlots.length];

        for (int i = 0; i < freeVarDefinitionSlots.length; i++) {
            int defFrameSlot = freeVarDefinitionSlots[i];
            Object cell = frame.getObject(defFrameSlot);
            assert cell instanceof PCell : "getting closure from locals: expected a cell";
            closure[i] = (PCell) cell;
        }

        return closure;
    }

    final PCell[] getClosureFromGeneratorOrFunctionLocals(Frame frame) {
        PCell[] closure;
        Frame generatorFrame = PArguments.getGeneratorFrame(frame);
        if (isGeneratorProfile.profile(generatorFrame != null)) {
            closure = getClosureFromLocals(frameProfile.profile(generatorFrame));
        } else {
            closure = getClosureFromLocals(frame);
        }
        return closure;
    }

    public final RootCallTarget getCallTarget() {
        return callTarget;
    }

    public final ExecutionCellSlots getExecutionCellSlots() {
        return ((FunctionRootNode) callTarget.getRootNode()).getExecutionCellSlots();
    }

    public final int[] getFreeVarDefinitionSlots() {
        return freeVarDefinitionSlots;
    }

    public final TruffleString[] getFreeVarDefinitions() {
        return PythonFrame.extractSlotNames(getRootNode().getFrameDescriptor(), freeVarDefinitionSlots);
    }
}
