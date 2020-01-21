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

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.nodes.control.LoopNode;
import com.oracle.graal.python.nodes.expression.CoerceToBooleanNode;
import com.oracle.graal.python.nodes.statement.StatementNode;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.graal.python.runtime.exception.BreakException;
import com.oracle.graal.python.runtime.exception.YieldException;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.TruffleLanguage.ContextReference;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.profiles.BranchProfile;
import com.oracle.truffle.api.profiles.ConditionProfile;

public final class GeneratorWhileNode extends LoopNode implements GeneratorControlNode {

    @Child private StatementNode body;
    @Child private CoerceToBooleanNode condition;
    @Child private GeneratorAccessNode gen = GeneratorAccessNode.create();

    @CompilationFinal private ContextReference<PythonContext> contextRef;
    private final ConditionProfile needsUpdateProfile = ConditionProfile.createBinaryProfile();
    private final BranchProfile seenYield = BranchProfile.create();
    private final BranchProfile seenBreak = BranchProfile.create();
    private final int flagSlot;

    public GeneratorWhileNode(CoerceToBooleanNode condition, StatementNode body, int flagSlot) {
        this.body = body;
        this.condition = condition;
        this.flagSlot = flagSlot;
    }

    @Override
    public StatementNode getBody() {
        return body;
    }

    @Override
    public void executeVoid(VirtualFrame frame) {
        boolean startFlag = gen.isActive(frame, flagSlot);

        if (!startFlag) {
            if (!condition.executeBoolean(frame)) {
                return;
            }
        }
        boolean nextFlag = false;
        int count = 0;
        if (contextRef == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            contextRef = lookupContextReference(PythonLanguage.class);
        }
        PythonContext context = contextRef.get();
        try {
            do {
                body.executeVoid(frame);
                if (CompilerDirectives.inInterpreter()) {
                    count++;
                }
                context.triggerAsyncActions(frame, this);
            } while (condition.executeBoolean(frame));
            return;
        } catch (YieldException e) {
            seenYield.enter();
            nextFlag = true;
            throw e;
        } catch (BreakException ex) {
            seenBreak.enter();
            return;
        } finally {
            if (CompilerDirectives.inInterpreter()) {
                reportLoopCount(count);
            }
            if (needsUpdateProfile.profile(startFlag != nextFlag)) {
                gen.setActive(frame, flagSlot, nextFlag);
            }
        }
    }
}
