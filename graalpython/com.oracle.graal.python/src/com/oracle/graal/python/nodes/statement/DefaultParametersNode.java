/*
 * Copyright (c) 2017, Oracle and/or its affiliates.
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
package com.oracle.graal.python.nodes.statement;

import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.nodes.PNode;
import com.oracle.graal.python.nodes.argument.ReadDefaultArgumentNode;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.ExplodeLoop;

/**
 * Evaluates default arguments in the declaration scope and feeds the evaluated values to the
 * {@link ReadDefaultArgumentNode}s.
 */
public class DefaultParametersNode extends StatementNode {

    @Children private final PNode[] functionDefaults;

    private final ReadDefaultArgumentNode[] defaultReads; // It's parked here, but not adopted.

    public DefaultParametersNode(PNode[] functionDefaults, ReadDefaultArgumentNode[] defaultReads) {
        this.functionDefaults = functionDefaults;
        this.defaultReads = defaultReads;
        assert functionDefaults != null & functionDefaults.length > 0;
        assert defaultReads != null & defaultReads.length > 0;
    }

    @ExplodeLoop
    @Override
    public Object execute(VirtualFrame frame) {
        for (int i = 0; i < functionDefaults.length; i++) {
            final Object defaultVal = functionDefaults[i].execute(frame);
            defaultReads[i].setValue(defaultVal);
        }

        return PNone.NONE;
    }

    public PNode[] getFunctionDefaults() {
        return functionDefaults;
    }

    public ReadDefaultArgumentNode[] getDefaultReads() {
        return defaultReads;
    }
}