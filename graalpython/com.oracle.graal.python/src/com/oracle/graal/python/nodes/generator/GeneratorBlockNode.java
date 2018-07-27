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
package com.oracle.graal.python.nodes.generator;

import java.util.List;

import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.nodes.PNode;
import com.oracle.graal.python.nodes.control.BaseBlockNode;
import com.oracle.graal.python.runtime.exception.YieldException;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.profiles.BranchProfile;
import com.oracle.truffle.api.profiles.ConditionProfile;

public final class GeneratorBlockNode extends BaseBlockNode implements GeneratorControlNode {

    private static final boolean[] EMPTY = new boolean[0];

    @Child private GeneratorAccessNode gen = GeneratorAccessNode.create();

    private final ConditionProfile needsUpdateProfile = ConditionProfile.createBinaryProfile();
    private final BranchProfile seenYield = BranchProfile.create();
    @CompilationFinal(dimensions = 1) private final boolean[] isYield;
    private final int indexSlot;

    public GeneratorBlockNode(PNode[] statements, int indexSlot) {
        super(statements);
        boolean[] yields = EMPTY;
        for (int i = 0; i < statements.length; i++) {
            if (statements[i] instanceof YieldNode) {
                if (yields.length == 0) {
                    yields = new boolean[statements.length];
                }
                yields[i] = true;
            }
        }
        this.isYield = yields;
        this.indexSlot = indexSlot;
    }

    public static GeneratorBlockNode create(PNode[] statements, int indexSlot) {
        return new GeneratorBlockNode(statements, indexSlot);
    }

    public int getIndexSlot() {
        return indexSlot;
    }

    @Override
    public GeneratorBlockNode insertNodesBefore(PNode insertBefore, List<PNode> insertees) {
        return new GeneratorBlockNode(insertStatementsBefore(insertBefore, insertees), getIndexSlot());
    }

    @ExplodeLoop
    @Override
    public Object execute(VirtualFrame frame) {
        int startIndex = gen.getIndex(frame, indexSlot);
        int i = 0;
        int nextIndex = 0;
        try {
            Object result = PNone.NONE;
            for (i = 0; i < statements.length; i++) {
                if (i >= startIndex) {
                    result = statements[i].execute(frame);
                }
            }
            return result;
        } catch (YieldException e) {
            seenYield.enter();
            if (i < isYield.length && isYield[i]) {
                // continue after the yield
                nextIndex = i + 1;
            } else {
                nextIndex = i;
            }
            throw e;
        } finally {
            if (needsUpdateProfile.profile(nextIndex != startIndex)) {
                gen.setIndex(frame, indexSlot, nextIndex);
            }
        }
    }
}
