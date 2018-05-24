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
package com.oracle.graal.python.builtins.objects.function;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.builtins.objects.cell.PCell;
import com.oracle.graal.python.builtins.objects.object.PythonObject;
import com.oracle.graal.python.builtins.objects.type.PythonClass;
import com.oracle.graal.python.parser.ExecutionCellSlots;
import com.oracle.graal.python.runtime.PythonCore;
import com.oracle.graal.python.runtime.object.PythonObjectFactory;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.RootNode;

public final class PGeneratorFunction extends PFunction {

    public static PGeneratorFunction create(PythonClass clazz, PythonCore core, String name, String enclosingClassName, Arity arity, RootCallTarget callTarget,
                    FrameDescriptor frameDescriptor, PythonObject globals, PCell[] closure, ExecutionCellSlots cellSlots,
                    int numOfActiveFlags, int numOfGeneratorBlockNode, int numOfGeneratorForNode) {

        GeneratorFunctionRootNode generatorFunctionRootNode = new GeneratorFunctionRootNode(core.getLanguage(), callTarget,
                        frameDescriptor, closure, cellSlots, numOfActiveFlags, numOfGeneratorBlockNode, numOfGeneratorForNode);

        return new PGeneratorFunction(clazz, name, enclosingClassName, arity, Truffle.getRuntime().createCallTarget(generatorFunctionRootNode), frameDescriptor, globals, closure);
    }

    public PGeneratorFunction(PythonClass clazz, String name, String enclosingClassName, Arity arity, RootCallTarget callTarget,
                    FrameDescriptor frameDescriptor, PythonObject globals, PCell[] closure) {
        super(clazz, name, enclosingClassName, arity, callTarget, frameDescriptor, globals, closure);
    }

    @Override
    public boolean isGeneratorFunction() {
        return true;
    }

    @Override
    public PGeneratorFunction asGeneratorFunction() {
        return this;
    }

    private final static class GeneratorFunctionRootNode extends RootNode {
        private final RootCallTarget callTarget;
        private final FrameDescriptor frameDescriptor;
        private final int numOfActiveFlags;
        private final int numOfGeneratorBlockNode;
        private final int numOfGeneratorForNode;
        private final PCell[] closure;
        private final ExecutionCellSlots cellSlots;

        @Child private PythonObjectFactory factory = PythonObjectFactory.create();

        protected GeneratorFunctionRootNode(PythonLanguage language, RootCallTarget callTarget, FrameDescriptor frameDescriptor, PCell[] closure, ExecutionCellSlots cellSlots,
                        int numOfActiveFlags, int numOfGeneratorBlockNode, int numOfGeneratorForNode) {
            super(language);
            this.callTarget = callTarget;
            this.frameDescriptor = frameDescriptor;
            this.closure = closure;
            this.cellSlots = cellSlots;
            this.numOfActiveFlags = numOfActiveFlags;
            this.numOfGeneratorBlockNode = numOfGeneratorBlockNode;
            this.numOfGeneratorForNode = numOfGeneratorForNode;
        }

        @Override
        public Object execute(VirtualFrame frame) {
            return factory.createGenerator(getName(), callTarget, frameDescriptor, frame.getArguments(), closure, cellSlots, numOfActiveFlags, numOfGeneratorBlockNode, numOfGeneratorForNode);
        }
    }
}
