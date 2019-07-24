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
package com.oracle.graal.python.nodes;

import static com.oracle.graal.python.nodes.SpecialAttributeNames.__DOC__;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.builtins.objects.function.Signature;
import com.oracle.graal.python.nodes.expression.ExpressionNode;
import com.oracle.graal.python.nodes.frame.WriteGlobalNode;
import com.oracle.graal.python.nodes.function.InnerRootNode;
import com.oracle.graal.python.runtime.ExecutionContext.CalleeContext;
import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.FrameSlot;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.profiles.ConditionProfile;
import com.oracle.truffle.api.source.SourceSection;

public class ModuleRootNode extends PClosureRootNode {
    private static final Signature SIGNATURE = new Signature(false, -1, false, new String[0], new String[0]);
    private final String name;
    private final String doc;
    private final ConditionProfile customLocalsProfile = ConditionProfile.createCountingProfile();
    @Child private ExpressionNode body;
    @Child private WriteGlobalNode writeModuleDoc;
    @Child private CalleeContext calleeContext = CalleeContext.create();

    public ModuleRootNode(PythonLanguage language, String name, String doc, ExpressionNode file, FrameDescriptor descriptor, FrameSlot[] freeVarSlots) {
        super(language, descriptor, freeVarSlots);
        this.name = "<module '" + name + "'>";
        this.doc = doc;
        this.body = new InnerRootNode(this, file);
    }

    private WriteGlobalNode getWriteModuleDoc() {
        if (writeModuleDoc == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            writeModuleDoc = insert(WriteGlobalNode.create(__DOC__));
        }
        return writeModuleDoc;
    }

    @Override
    public Object execute(VirtualFrame frame) {
        CalleeContext.enter(frame, customLocalsProfile);
        try {
            return body.execute(frame);
        } finally {
            calleeContext.exit(frame, this);
        }
    }

    @Override
    public void initializeFrame(VirtualFrame frame) {
        addClosureCellsToLocals(frame);
        if (doc != null) {
            getWriteModuleDoc().doWrite(frame, doc);
        }
    }

    @Override
    public String toString() {
        CompilerAsserts.neverPartOfCompilation();
        return name;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public SourceSection getSourceSection() {
        return body.getSourceSection();
    }

    @Override
    public boolean isInternal() {
        return getSourceSection() != null && getSourceSection().getSource().isInternal();
    }

    @Override
    public Signature getSignature() {
        return SIGNATURE;
    }

    @Override
    public boolean isPythonInternal() {
        return false;
    }
}
