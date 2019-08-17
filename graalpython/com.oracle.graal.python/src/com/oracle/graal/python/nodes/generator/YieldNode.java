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

import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.function.PArguments;
import com.oracle.graal.python.nodes.PNode;
import com.oracle.graal.python.nodes.expression.ExpressionNode;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.graal.python.runtime.exception.YieldException;
import com.oracle.truffle.api.frame.VirtualFrame;

public class YieldNode extends AbstractYieldNode implements GeneratorControlNode {

    @Child private ExpressionNode right;
    @Child private GeneratorAccessNode access = GeneratorAccessNode.create();

    public YieldNode(ExpressionNode right) {
        this.right = right;
    }

    public PNode getRhs() {
        return right;
    }

    @Override
    public Object execute(VirtualFrame frame) {
        if (access.isActive(frame, flagSlot)) {
            // yield is active -> resume
            access.setActive(frame, flagSlot, false);
            Object specialArgument = PArguments.getSpecialArgument(frame);
            if (specialArgument == null) {
                gotNothing.enter();
                return PNone.NONE;
            } else if (specialArgument instanceof PException) {
                gotException.enter();
                throw (PException) specialArgument;
            } else {
                gotValue.enter();
                return specialArgument;
            }
        } else {
            access.setActive(frame, flagSlot, true);
            throw new YieldException(right.execute(frame));
        }
    }
}
