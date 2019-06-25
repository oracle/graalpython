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
package com.oracle.graal.python.nodes.control;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.nodes.PNodeWithContext;
import com.oracle.graal.python.nodes.expression.CastToBooleanNode;
import com.oracle.graal.python.nodes.statement.StatementNode;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.TruffleLanguage.ContextReference;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.NodeInfo;
import com.oracle.truffle.api.nodes.RepeatingNode;
import com.oracle.truffle.api.profiles.LoopConditionProfile;

final class WhileRepeatingNode extends PNodeWithContext implements RepeatingNode {

    private final LoopConditionProfile conditionProfile = LoopConditionProfile.createCountingProfile();
    private final ContextReference<PythonContext> contextRef = PythonLanguage.getContextRef();

    @Child CastToBooleanNode condition;
    @Child StatementNode body;

    WhileRepeatingNode(CastToBooleanNode condition, StatementNode body) {
        this.condition = condition;
        this.body = body;
    }

    @Override
    public boolean executeRepeating(VirtualFrame frame) {
        if (conditionProfile.profile(condition.executeBoolean(frame))) {
            body.executeVoid(frame);
            contextRef.get().triggerAsyncActions(frame, this);
            return true;
        }
        return false;
    }
}

@NodeInfo(shortName = "while")
public final class WhileNode extends LoopNode {

    @Child private com.oracle.truffle.api.nodes.LoopNode loopNode;

    public WhileNode(CastToBooleanNode condition, StatementNode body) {
        this.loopNode = Truffle.getRuntime().createLoopNode(new WhileRepeatingNode(condition, body));
    }

    @Override
    public StatementNode getBody() {
        return ((WhileRepeatingNode) loopNode.getRepeatingNode()).body;
    }

    public CastToBooleanNode getCondition() {
        return ((WhileRepeatingNode) loopNode.getRepeatingNode()).condition;
    }

    @Override
    public void executeVoid(VirtualFrame frame) {
        loopNode.executeLoop(frame);
    }
}
