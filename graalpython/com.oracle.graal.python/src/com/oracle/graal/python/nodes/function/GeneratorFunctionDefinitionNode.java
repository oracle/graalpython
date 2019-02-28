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
package com.oracle.graal.python.nodes.function;

import com.oracle.graal.python.builtins.objects.cell.PCell;
import com.oracle.graal.python.builtins.objects.function.PArguments;
import com.oracle.graal.python.builtins.objects.function.PGeneratorFunction;
import com.oracle.graal.python.builtins.objects.function.PKeyword;
import com.oracle.graal.python.nodes.PRootNode;
import com.oracle.graal.python.nodes.expression.ExpressionNode;
import com.oracle.graal.python.nodes.generator.GeneratorFunctionRootNode;
import com.oracle.graal.python.parser.DefinitionCellSlots;
import com.oracle.graal.python.parser.ExecutionCellSlots;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.ExplodeLoop;

public class GeneratorFunctionDefinitionNode extends FunctionDefinitionNode {
    protected final int numOfActiveFlags;
    protected final int numOfGeneratorBlockNode;
    protected final int numOfGeneratorForNode;
    protected final FrameDescriptor frameDescriptor;

    @CompilationFinal private RootCallTarget generatorCallTarget;

    public GeneratorFunctionDefinitionNode(String name, String enclosingClassName, ExpressionNode doc, ExpressionNode[] defaults, KwDefaultExpressionNode[] kwDefaults,
                    RootCallTarget callTarget, FrameDescriptor frameDescriptor, DefinitionCellSlots definitionCellSlots, ExecutionCellSlots executionCellSlots, int numOfActiveFlags,
                    int numOfGeneratorBlockNode, int numOfGeneratorForNode) {
        super(name, enclosingClassName, doc, defaults, kwDefaults, callTarget, definitionCellSlots, executionCellSlots);
        this.frameDescriptor = frameDescriptor;
        this.numOfActiveFlags = numOfActiveFlags;
        this.numOfGeneratorBlockNode = numOfGeneratorBlockNode;
        this.numOfGeneratorForNode = numOfGeneratorForNode;
    }

    public static GeneratorFunctionDefinitionNode create(String name, String enclosingClassName, ExpressionNode doc, ExpressionNode[] defaults, KwDefaultExpressionNode[] kwDefaults,
                    RootCallTarget callTarget, FrameDescriptor frameDescriptor, DefinitionCellSlots definitionCellSlots, ExecutionCellSlots executionCellSlots, int numOfActiveFlags,
                    int numOfGeneratorBlockNode, int numOfGeneratorForNode) {
        return new GeneratorFunctionDefinitionNode(name, enclosingClassName, doc, defaults, kwDefaults, callTarget,
                        frameDescriptor, definitionCellSlots, executionCellSlots,
                        numOfActiveFlags, numOfGeneratorBlockNode, numOfGeneratorForNode);
    }

    @Override
    @ExplodeLoop // this should always be safe, how many syntactic defaults can one function have...
                 // and these are constant
    public PGeneratorFunction execute(VirtualFrame frame) {
        Object[] defaultValues = null;
        if (defaults != null) {
            defaultValues = new Object[defaults.length];
            for (int i = 0; i < defaults.length; i++) {
                defaultValues[i] = defaults[i].execute(frame);
            }
        }
        PKeyword[] kwDefaultValues = null;
        if (kwDefaults != null) {
            kwDefaultValues = new PKeyword[kwDefaults.length];
            for (int i = 0; i < kwDefaults.length; i++) {
                kwDefaultValues[i] = new PKeyword(kwDefaults[i].name, kwDefaults[i].execute(frame));
            }
        }
        PCell[] closure = getClosureFromLocals(frame);
        return withDocString(frame, factory().createGeneratorFunction(functionName, enclosingClassName, getGeneratorCallTarget(), PArguments.getGlobals(frame), closure, defaultValues,
                        kwDefaultValues));
    }

    protected RootCallTarget getGeneratorCallTarget() {
        if (generatorCallTarget == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            GeneratorFunctionRootNode generatorFunctionRootNode = new GeneratorFunctionRootNode(getContext().getLanguage(), callTarget, functionName, frameDescriptor,
                            executionCellSlots, ((PRootNode) callTarget.getRootNode()).getSignature(), numOfActiveFlags, numOfGeneratorBlockNode, numOfGeneratorForNode);
            generatorCallTarget = Truffle.getRuntime().createCallTarget(generatorFunctionRootNode);
        }
        return generatorCallTarget;
    }
}
