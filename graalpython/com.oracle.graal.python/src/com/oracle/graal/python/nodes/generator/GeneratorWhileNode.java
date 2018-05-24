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

import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.nodes.PNode;
import com.oracle.graal.python.nodes.control.LoopNode;
import com.oracle.graal.python.nodes.expression.CastToBooleanNode;
import com.oracle.graal.python.runtime.exception.BreakException;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.frame.VirtualFrame;

public final class GeneratorWhileNode extends LoopNode implements GeneratorControlNode {

    @Child protected PNode body;
    @Child protected CastToBooleanNode condition;

    private final int flagSlot;
    private int count;

    public GeneratorWhileNode(CastToBooleanNode condition, PNode body, int flagSlot) {
        this.body = body;
        this.condition = condition;
        this.flagSlot = flagSlot;
    }

    @Override
    public PNode getBody() {
        return body;
    }

    private void incrementCounter() {
        if (CompilerDirectives.inInterpreter()) {
            count++;
        }
    }

    private Object doReturn(VirtualFrame frame) {
        if (CompilerDirectives.inInterpreter()) {
            reportLoopCount(count);
            count = 0;
        }

        assert !isActive(frame, flagSlot);
        return PNone.NONE;
    }

    @Override
    public Object execute(VirtualFrame frame) {
        try {
            while (isActive(frame, flagSlot) || condition.executeBoolean(frame)) {
                setActive(frame, flagSlot, true);
                body.executeVoid(frame);
                setActive(frame, flagSlot, false);
                incrementCounter();
            }
        } catch (BreakException ex) {
            reset(frame);
        }

        return doReturn(frame);
    }

    public void reset(VirtualFrame frame) {
        setActive(frame, flagSlot, false);
    }

}
