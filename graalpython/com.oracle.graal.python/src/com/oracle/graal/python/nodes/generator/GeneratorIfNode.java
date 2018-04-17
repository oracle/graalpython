/*
 * Copyright (c) 2017, Oracle and/or its affiliates.
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
import com.oracle.graal.python.nodes.EmptyNode;
import com.oracle.graal.python.nodes.PNode;
import com.oracle.graal.python.nodes.control.IfNode;
import com.oracle.graal.python.nodes.expression.CastToBooleanNode;
import com.oracle.truffle.api.frame.VirtualFrame;

public class GeneratorIfNode extends IfNode implements GeneratorControlNode {

    protected final int thenFlagSlot;
    protected final int elseFlagSlot;

    public GeneratorIfNode(CastToBooleanNode condition, PNode then, PNode orelse, int thenFlagSlot, int elseFlagSlot) {
        super(condition, then, orelse);
        this.thenFlagSlot = thenFlagSlot;
        this.elseFlagSlot = elseFlagSlot;
    }

    public static GeneratorIfNode create(CastToBooleanNode condition, PNode then, PNode orelse, int thenFlagSlot, int elseFlagSlot) {
        if (!EmptyNode.isEmpty(orelse)) {
            return new GeneratorIfNode(condition, then, orelse, thenFlagSlot, elseFlagSlot);
        } else {
            return new GeneratorIfWithoutElseNode(condition, then, thenFlagSlot);
        }
    }

    public int getThenFlagSlot() {
        return thenFlagSlot;
    }

    public int getElseFlagSlot() {
        return elseFlagSlot;
    }

    protected final Object executeThen(VirtualFrame frame) {
        setActive(frame, thenFlagSlot, true);
        then.execute(frame);
        setActive(frame, thenFlagSlot, false);
        return PNone.NONE;
    }

    protected final Object executeElse(VirtualFrame frame) {
        setActive(frame, elseFlagSlot, true);
        orelse.execute(frame);
        setActive(frame, elseFlagSlot, false);
        return PNone.NONE;
    }

    public void reset(VirtualFrame frame) {
        setActive(frame, thenFlagSlot, false);
        setActive(frame, elseFlagSlot, false);
    }

    @Override
    public Object execute(VirtualFrame frame) {
        if (isActive(frame, thenFlagSlot)) {
            return executeThen(frame);
        }

        if (isActive(frame, elseFlagSlot)) {
            return executeElse(frame);
        }

        if (condition.executeBoolean(frame)) {
            return executeThen(frame);
        } else {
            return executeElse(frame);
        }
    }

    public static final class GeneratorIfWithoutElseNode extends GeneratorIfNode {

        /**
         * Both flagSlot getter return the same slot.
         */
        public GeneratorIfWithoutElseNode(CastToBooleanNode condition, PNode then, int thenFlagSlot) {
            super(condition, then, EmptyNode.create(), thenFlagSlot, thenFlagSlot);
        }

        @Override
        public Object execute(VirtualFrame frame) {
            if (isActive(frame, thenFlagSlot) || condition.executeBoolean(frame)) {
                return executeThen(frame);
            }

            return PNone.NONE;
        }
    }

}
