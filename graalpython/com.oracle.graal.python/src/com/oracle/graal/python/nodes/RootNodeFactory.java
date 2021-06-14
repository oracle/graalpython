/*
 * Copyright (c) 2017, 2021, Oracle and/or its affiliates.
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
import com.oracle.graal.python.builtins.objects.function.Signature;
import com.oracle.graal.python.nodes.expression.ExpressionNode;
import com.oracle.graal.python.nodes.function.ClassBodyRootNode;
import com.oracle.graal.python.nodes.function.FunctionRootNode;
import com.oracle.graal.python.parser.ExecutionCellSlots;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.source.SourceSection;

public class RootNodeFactory {

    private final PythonLanguage language;

    private RootNodeFactory(PythonLanguage language) {
        this.language = language;
    }

    public static RootNodeFactory create(PythonLanguage language) {
        return new RootNodeFactory(language);
    }

    public ModuleRootNode createModuleRoot(String name, String doc, ExpressionNode file, FrameDescriptor fd, boolean hasAnnotations) {
        return new ModuleRootNode(language, name, doc, file, fd, null, hasAnnotations);
    }

    public FunctionRootNode createFunctionRoot(SourceSection sourceSection, String functionName, boolean isGenerator, FrameDescriptor frameDescriptor, ExpressionNode body,
                    ExecutionCellSlots cellSlots, Signature signature, ExpressionNode doc) {
        return new FunctionRootNode(language, sourceSection, functionName, isGenerator, false, frameDescriptor, body, cellSlots, signature, doc);
    }

    public ClassBodyRootNode createClassBodyRoot(SourceSection sourceSection, String functionName, FrameDescriptor frameDescriptor, ExpressionNode body, ExecutionCellSlots cellSlots) {
        return new ClassBodyRootNode(language, sourceSection, functionName, frameDescriptor, body, cellSlots);
    }
}
