/*
 * Copyright (c) 2020, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 *
 * Subject to the condition set forth below, permission is hereby granted to any
 * person obtaining a copy of this software, associated documentation and/or
 * data (collectively the "Software"), free of charge and under any and all
 * copyright rights in the Software, and any and all patent rights owned or
 * freely licensable by each licensor hereunder covering either (i) the
 * unmodified Software as contributed to or provided by such licensor, or (ii)
 * the Larger Works (as defined below), to deal in both
 *
 * (a) the Software, and
 *
 * (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
 * one is included with the Software each a "Larger Work" to which the Software
 * is contributed by such licensors),
 *
 * without restriction, including without limitation the rights to copy, create
 * derivative works of, display, perform, and distribute the Software and make,
 * use, sell, offer for sale, import, export, have made, and have sold the
 * Software and the Larger Work(s), and to sublicense the foregoing rights on
 * either these or other terms.
 *
 * This license is subject to the following condition:
 *
 * The above copyright notice and either this complete permission notice or at a
 * minimum a reference to the UPL must be included in all copies or substantial
 * portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package com.oracle.graal.python.nodes.generator;

import com.oracle.graal.python.nodes.expression.ExpressionNode;
import com.oracle.graal.python.nodes.statement.StatementNode;
import com.oracle.graal.python.parser.GeneratorInfo;
import com.oracle.graal.python.runtime.exception.YieldException;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.profiles.BranchProfile;
import com.oracle.truffle.api.profiles.ConditionProfile;

public final class GeneratorExpressionWithSideEffects extends ExpressionNode implements GeneratorControlNode {
    @Children private StatementNode[] statements;
    @Child private ExpressionNode expression;
    @Child private GeneratorAccessNode gen = GeneratorAccessNode.create();

    private final ConditionProfile needsUpdateProfile = ConditionProfile.createBinaryProfile();
    private final BranchProfile seenYield = BranchProfile.create();
    private final int indexSlot;

    public GeneratorExpressionWithSideEffects(ExpressionNode expression, StatementNode[] statements, GeneratorInfo.Mutable generatorInfo) {
        this.expression = expression;
        this.statements = statements;
        this.indexSlot = generatorInfo.nextBlockNodeIndex();
    }

    public static GeneratorExpressionWithSideEffects create(ExpressionNode expression, StatementNode[] statements, GeneratorInfo.Mutable generatorInfo) {
        return new GeneratorExpressionWithSideEffects(expression, statements, generatorInfo);
    }

    public int getIndexSlot() {
        return indexSlot;
    }

    @ExplodeLoop
    @Override
    public Object execute(VirtualFrame frame) {
        int startIndex = gen.getIndex(frame, indexSlot);
        int i = 0;
        int nextIndex = 0;
        try {
            for (i = 0; i < statements.length; i++) {
                if (i >= startIndex) {
                    statements[i].executeVoid(frame);
                }
            }
            return expression.execute(frame);
        } catch (YieldException e) {
            seenYield.enter();
            nextIndex = i;
            throw e;
        } finally {
            if (needsUpdateProfile.profile(nextIndex != startIndex)) {
                gen.setIndex(frame, indexSlot, nextIndex);
            }
        }
    }
}
