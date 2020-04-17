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
package com.oracle.graal.python.nodes.generator;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.nodes.control.GetNextNode;
import com.oracle.graal.python.nodes.control.LoopNode;
import com.oracle.graal.python.nodes.expression.ExpressionNode;
import com.oracle.graal.python.nodes.frame.WriteNode;
import com.oracle.graal.python.nodes.object.IsBuiltinClassProfile;
import com.oracle.graal.python.nodes.statement.StatementNode;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.graal.python.runtime.exception.YieldException;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.TruffleLanguage.ContextReference;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.profiles.BranchProfile;
import com.oracle.truffle.api.profiles.ConditionProfile;

public final class GeneratorForNode extends LoopNode implements GeneratorControlNode {

    @Child protected StatementNode body;
    @Child protected WriteNode target;
    @Child protected ExpressionNode getIterator;
    @Child protected GetNextNode getNext = GetNextNode.create();
    @Child protected GeneratorAccessNode gen = GeneratorAccessNode.create();

    private final IsBuiltinClassProfile errorProfile = IsBuiltinClassProfile.create();
    private final ConditionProfile executesHeadProfile = ConditionProfile.createBinaryProfile();
    private final ConditionProfile needsUpdateProfile = ConditionProfile.createBinaryProfile();
    private final BranchProfile seenYield = BranchProfile.create();
    @CompilationFinal private BranchProfile asyncProfile;
    @CompilationFinal private ContextReference<PythonContext> contextRef;

    private final int iteratorSlot;

    public GeneratorForNode(WriteNode target, ExpressionNode getIterator, StatementNode body, int iteratorSlot) {
        this.body = body;
        this.target = target;
        this.getIterator = getIterator;
        this.iteratorSlot = iteratorSlot;
    }

    public static GeneratorForNode create(WriteNode target, ExpressionNode getIterator, StatementNode body, int iteratorSlot) {
        return new GeneratorForNode(target, getIterator, body, iteratorSlot);
    }

    @Override
    public StatementNode getBody() {
        return body;
    }

    public final int getIteratorSlot() {
        return iteratorSlot;
    }

    @Override
    public void executeVoid(VirtualFrame frame) {
        Object startIterator = gen.getIterator(frame, iteratorSlot);

        Object iterator;
        if (executesHeadProfile.profile(startIterator == null)) {
            iterator = getIterator.execute(frame);
            Object value;
            try {
                value = getNext.execute(frame, iterator);
            } catch (PException e) {
                e.expectStopIteration(errorProfile);
                return;
            }
            target.doWrite(frame, value);
        } else {
            iterator = startIterator;
        }

        Object nextIterator = null;
        int count = 0;
        try {
            while (true) {
                body.executeVoid(frame);
                Object value;
                try {
                    value = getNext.execute(frame, iterator);
                } catch (PException e) {
                    e.expectStopIteration(errorProfile);
                    break;
                }
                target.doWrite(frame, value);
                if (CompilerDirectives.inInterpreter()) {
                    count++;
                }
                if (contextRef == null) {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    asyncProfile = BranchProfile.create();
                    contextRef = lookupContextReference(PythonLanguage.class);
                }
                contextRef.get().triggerAsyncActions(frame, asyncProfile);
            }
            return;
        } catch (YieldException e) {
            seenYield.enter();
            nextIterator = iterator;
            throw e;
        } finally {
            if (CompilerDirectives.inInterpreter()) {
                reportLoopCount(count);
            }
            if (needsUpdateProfile.profile(nextIterator != startIterator)) {
                gen.setIterator(frame, iteratorSlot, nextIterator);
            }
        }
    }
}
