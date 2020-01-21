/*
 * Copyright (c) 2017, 2020, Oracle and/or its affiliates.
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

import com.oracle.graal.python.nodes.expression.CoerceToBooleanNode;
import com.oracle.graal.python.nodes.statement.StatementNode;
import com.oracle.truffle.api.frame.VirtualFrame;

public final class IfNode extends StatementNode {

    @Child private CoerceToBooleanNode condition;
    @Child private StatementNode then;
    @Child private StatementNode orelse;

    public IfNode(CoerceToBooleanNode condition, StatementNode then, StatementNode orelse) {
        this.condition = condition;
        this.then = then;
        this.orelse = orelse;
    }

    public CoerceToBooleanNode getCondition() {
        return condition;
    }

    public StatementNode getThen() {
        return then;
    }

    public StatementNode getElse() {
        return orelse;
    }

    @Override
    public void executeVoid(VirtualFrame frame) {
        if (condition.executeBoolean(frame)) {
            then.executeVoid(frame);
        } else if (orelse != null) {
            orelse.executeVoid(frame);
        }
    }
}
