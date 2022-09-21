/*
 * Copyright (c) 2017, 2022, Oracle and/or its affiliates.
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
package com.oracle.graal.python.nodes.function;

import java.util.Set;

import com.oracle.graal.python.builtins.objects.cell.PCell;
import com.oracle.graal.python.builtins.objects.function.PArguments;
import com.oracle.graal.python.nodes.expression.ExpressionNode;
import com.oracle.graal.python.nodes.generator.GeneratorFunctionRootNode;
import com.oracle.graal.python.parser.DefinitionCellSlots;
import com.oracle.graal.python.parser.GeneratorInfo;
import com.oracle.graal.python.runtime.object.PythonObjectFactory;
import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.strings.TruffleString;

public final class GeneratorExpressionNode extends ExpressionDefinitionNode {
    private final TruffleString name;
    private final TruffleString qualname;
    @CompilationFinal(dimensions = 1) private volatile RootCallTarget[] callTargets;
    private final FrameDescriptor frameDescriptor;
    private final GeneratorInfo generatorInfo;

    @CompilationFinal private Set<Object> enclosingFrameDescriptor;
    @CompilationFinal private boolean isEnclosingFrameGenerator;
    @Child private ExpressionNode getIterator;
    @Child private PythonObjectFactory factory = PythonObjectFactory.create();

    public GeneratorExpressionNode(TruffleString name, TruffleString qualname, RootCallTarget callTarget, ExpressionNode getIterator, FrameDescriptor descriptor,
                    DefinitionCellSlots definitionCellSlots,
                    GeneratorInfo generatorInfo) {
        super(definitionCellSlots, callTarget);
        this.name = name;
        this.qualname = qualname;
        this.getIterator = getIterator;
        this.frameDescriptor = descriptor;
        this.generatorInfo = generatorInfo;
    }

    public TruffleString getName() {
        return name;
    }

    public FrameDescriptor getFrameDescriptor() {
        return frameDescriptor;
    }

    public Set<Object> getEnclosingFrameDescriptor() {
        return enclosingFrameDescriptor;
    }

    public void setEnclosingFrameDescriptor(Set<Object> frameDescriptor) {
        CompilerAsserts.neverPartOfCompilation();
        enclosingFrameDescriptor = frameDescriptor;
    }

    public boolean isEnclosingFrameGenerator() {
        return isEnclosingFrameGenerator;
    }

    public void setEnclosingFrameGenerator(boolean value) {
        isEnclosingFrameGenerator = value;
    }

    public GeneratorInfo getGeneratorInfo() {
        return generatorInfo;
    }

    @Override
    public Object execute(VirtualFrame frame) {
        Object[] arguments;
        Object iterator = null;
        if (getIterator == null) {
            arguments = PArguments.create(0);
        } else {
            arguments = PArguments.create(1);
            iterator = getIterator.execute(frame);
            PArguments.setArgument(arguments, 0, iterator);
        }
        PArguments.setGlobals(arguments, PArguments.getGlobals(frame));

        if (callTargets == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            callTargets = GeneratorFunctionRootNode.createYieldTargets(callTarget);
        }

        PCell[] closure = getClosureFromGeneratorOrFunctionLocals(frame);
        return factory.createGenerator(name, qualname, callTargets, frameDescriptor, arguments, closure, getExecutionCellSlots(), generatorInfo, iterator);
    }

    @Override
    public String toString() {
        CompilerAsserts.neverPartOfCompilation();
        return name.toJavaStringUncached();
    }
}
