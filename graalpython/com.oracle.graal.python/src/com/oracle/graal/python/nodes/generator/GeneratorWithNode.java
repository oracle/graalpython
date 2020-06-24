/*
 * Copyright (c) 2018, 2020, Oracle and/or its affiliates. All rights reserved.
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
import com.oracle.graal.python.nodes.frame.WriteNode;
import com.oracle.graal.python.nodes.statement.StatementNode;
import com.oracle.graal.python.nodes.statement.WithNode;
import com.oracle.graal.python.parser.GeneratorInfo;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.graal.python.runtime.exception.YieldException;
import com.oracle.truffle.api.frame.VirtualFrame;

public class GeneratorWithNode extends WithNode implements GeneratorControlNode {
    @Child private GeneratorAccessNode gen = GeneratorAccessNode.create();
    private final int withObjectSlot;
    private final int enterSlot;
    private final int yieldSlot;

    public GeneratorWithNode(WriteNode targetNode, StatementNode body, ExpressionNode withContext, GeneratorInfo.Mutable generatorInfo) {
        super(targetNode, body, withContext);
        this.enterSlot = generatorInfo.nextActiveFlagIndex();
        this.withObjectSlot = generatorInfo.nextIteratorSlotIndex();
        this.yieldSlot = generatorInfo.nextActiveFlagIndex();
    }

    @Override
    protected Object getWithObject(VirtualFrame frame) {
        Object withObject = gen.getIterator(frame, withObjectSlot);
        if (withObject == null) {
            withObject = super.getWithObject(frame);
            gen.setIterator(frame, withObjectSlot, withObject);
        }
        return withObject;
    }

    @Override
    protected void doEnter(VirtualFrame frame, Object withObject, Object enterCallable) {
        if (!gen.isActive(frame, enterSlot)) {
            super.doEnter(frame, withObject, enterCallable);
            gen.setActive(frame, enterSlot, true);
        }
    }

    @Override
    protected void doBody(VirtualFrame frame) {
        try {
            super.doBody(frame);
        } catch (YieldException e) {
            gen.setActive(frame, yieldSlot, true);
            throw e;
        }
    }

    @Override
    protected void handleException(VirtualFrame frame, Object withObject, Object exitCallable, PException pException) {
        reset(frame);
        super.handleException(frame, withObject, exitCallable, pException);
    }

    @Override
    protected void doLeave(VirtualFrame frame, Object withObject, Object exitCallable) {
        if (gen.isActive(frame, yieldSlot)) {
            gen.setActive(frame, yieldSlot, false);
        } else {
            reset(frame);
            super.doLeave(frame, withObject, exitCallable);
        }
    }

    public void reset(VirtualFrame frame) {
        gen.setActive(frame, enterSlot, false);
        gen.setActive(frame, yieldSlot, false);
        gen.setIterator(frame, withObjectSlot, null);
    }
}
