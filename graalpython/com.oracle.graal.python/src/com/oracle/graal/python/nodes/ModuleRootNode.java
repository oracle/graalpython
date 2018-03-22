/*
 * Copyright (c) 2017, 2018, Oracle and/or its affiliates.
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
package com.oracle.graal.python.nodes;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.builtins.objects.cell.PCell;
import com.oracle.graal.python.builtins.objects.function.PArguments;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.FrameSlot;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.source.SourceSection;

public class ModuleRootNode extends PRootNode {

    @CompilerDirectives.CompilationFinal(dimensions = 1) private final FrameSlot[] freeVarSlots;
    private final String name;

    @Child private PNode body;

    public ModuleRootNode(PythonLanguage language, String name, PNode body, FrameDescriptor descriptor, FrameSlot[] freeVarSlots) {
        super(language, descriptor);
        this.name = "<module '" + name + "'>";
        this.body = body;
        this.freeVarSlots = freeVarSlots;
    }

    private void addClosureCellsToLocals(Frame frame) {
        PCell[] closure = PArguments.getClosure(frame);
        if (closure != null) {
            assert freeVarSlots != null : "module root node: the free var slots cannot be null when the closure is not null";
            assert closure.length == freeVarSlots.length : "module root node: the closure must have the same length as the free var slots array";
            for (int i = 0; i < closure.length; i++) {
                frame.setObject(freeVarSlots[i], closure[i]);
            }
        }
    }

    @Override
    public Object execute(VirtualFrame frame) {
        addClosureCellsToLocals(frame);
        return body.execute(frame);
    }

    public PNode getBody() {
        return body;
    }

    @Override
    public String toString() {
        return "<module '" + name + "'>";
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public SourceSection getSourceSection() {
        return body.getSourceSection();
    }
}
