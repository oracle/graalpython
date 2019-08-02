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

import com.oracle.graal.python.nodes.EmptyNode;
import com.oracle.graal.python.nodes.control.BlockNode;
import com.oracle.graal.python.nodes.expression.CastToBooleanNode;
import com.oracle.graal.python.nodes.statement.StatementNode;
import com.oracle.graal.python.runtime.exception.YieldException;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.profiles.BranchProfile;
import com.oracle.truffle.api.profiles.ConditionProfile;

public class GeneratorIfNode extends StatementNode implements GeneratorControlNode {

    @Child protected GeneratorAccessNode gen = GeneratorAccessNode.create();
    @Child protected CastToBooleanNode condition;
    @Child protected StatementNode then;
    @Child protected StatementNode orelse;

    protected final int thenFlagSlot;
    protected final int elseFlagSlot;

    protected final ConditionProfile needsConditionProfile = ConditionProfile.createBinaryProfile();
    protected final ConditionProfile needsThenUpdateProfile = ConditionProfile.createBinaryProfile();
    private final ConditionProfile needsElseUpdateProfile = ConditionProfile.createBinaryProfile();
    protected final BranchProfile seenYield = BranchProfile.create();
    protected final BranchProfile seenThen = BranchProfile.create();
    protected final BranchProfile seenElse = BranchProfile.create();

    public GeneratorIfNode(CastToBooleanNode condition, StatementNode then, StatementNode orelse, int thenFlagSlot, int elseFlagSlot) {
        this.condition = condition;
        this.then = then;
        this.orelse = orelse;
        this.thenFlagSlot = thenFlagSlot;
        this.elseFlagSlot = elseFlagSlot;
    }

    public static GeneratorIfNode create(CastToBooleanNode condition, StatementNode then, StatementNode orelse, int thenFlagSlot, int elseFlagSlot) {
        if (!EmptyNode.isEmpty(orelse)) {
            return new GeneratorIfNode(condition, then, orelse, thenFlagSlot, elseFlagSlot);
        } else {
            return new GeneratorIfWithoutElseNode(condition, then, thenFlagSlot);
        }
    }

    @Override
    public void executeVoid(VirtualFrame frame) {
        boolean startThenFlag = gen.isActive(frame, thenFlagSlot);
        boolean startElseFlag = gen.isActive(frame, elseFlagSlot);
        boolean thenFlag = startThenFlag;
        boolean nextThenFlag = false;
        boolean nextElseFlag = false;

        try {
            if (needsConditionProfile.profile(!startThenFlag && !startElseFlag)) {
                thenFlag = condition.executeBoolean(frame);
            }
            if (thenFlag) {
                seenThen.enter();
                then.executeVoid(frame);
            } else {
                seenElse.enter();
                orelse.executeVoid(frame);
            }
            return;
        } catch (YieldException e) {
            seenYield.enter();
            nextThenFlag = thenFlag;
            nextElseFlag = !thenFlag;
            throw e;
        } finally {
            if (needsThenUpdateProfile.profile(startThenFlag != nextThenFlag)) {
                gen.setActive(frame, thenFlagSlot, nextThenFlag);
            }
            if (needsElseUpdateProfile.profile(startElseFlag != nextElseFlag)) {
                gen.setActive(frame, elseFlagSlot, nextElseFlag);
            }
        }
    }

    public static final class GeneratorIfWithoutElseNode extends GeneratorIfNode {

        /**
         * Both flagSlot getter return the same slot.
         */
        public GeneratorIfWithoutElseNode(CastToBooleanNode condition, StatementNode then, int thenFlagSlot) {
            super(condition, then, BlockNode.create(), thenFlagSlot, thenFlagSlot);
        }

        @Override
        public void executeVoid(VirtualFrame frame) {
            boolean startThenFlag = gen.isActive(frame, thenFlagSlot);
            boolean thenFlag = startThenFlag;
            boolean nextThenFlag = false;

            try {
                if (needsConditionProfile.profile(!startThenFlag)) {
                    thenFlag = condition.executeBoolean(frame);
                }
                if (thenFlag) {
                    seenThen.enter();
                    then.executeVoid(frame);
                }
                return;
            } catch (YieldException e) {
                seenYield.enter();
                nextThenFlag = thenFlag;
                throw e;
            } finally {
                if (needsThenUpdateProfile.profile(startThenFlag != nextThenFlag)) {
                    gen.setActive(frame, thenFlagSlot, nextThenFlag);
                }
            }
        }
    }
}
