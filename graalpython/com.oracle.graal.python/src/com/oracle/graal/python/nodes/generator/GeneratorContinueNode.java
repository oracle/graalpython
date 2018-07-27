/*
 * Copyright (c) 2017, 2018, Oracle and/or its affiliates.
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

import com.oracle.graal.python.nodes.statement.StatementNode;
import com.oracle.graal.python.runtime.exception.ContinueException;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.ExplodeLoop;

public final class GeneratorContinueNode extends StatementNode implements GeneratorControlNode {

    @Child private GeneratorAccessNode gen = GeneratorAccessNode.create();

    @CompilationFinal(dimensions = 1) private final int[] enclosingBlockIndexSlots;
    @CompilationFinal(dimensions = 1) private final int[] enclosingIfFlagSlots;

    public GeneratorContinueNode(int[] enclosingBlockIndexSlots, int[] enclosingIfFlagSlots) {
        this.enclosingBlockIndexSlots = enclosingBlockIndexSlots;
        this.enclosingIfFlagSlots = enclosingIfFlagSlots;
    }

    @Override
    public Object execute(VirtualFrame frame) {
        iterateBlocks(frame);
        iterateIfs(frame);

        throw ContinueException.INSTANCE;
    }

    @ExplodeLoop
    private void iterateIfs(VirtualFrame frame) {
        for (int flagSlot : enclosingIfFlagSlots) {
            gen.setActive(frame, flagSlot, false);
        }
    }

    @ExplodeLoop
    private void iterateBlocks(VirtualFrame frame) {
        for (int indexSlot : enclosingBlockIndexSlots) {
            gen.setIndex(frame, indexSlot, 0);
        }
    }

    public void reset(VirtualFrame frame) {
        // empty
    }
}
