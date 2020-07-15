/*
 * Copyright (c) 2017, 2020, Oracle and/or its affiliates.
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

import java.util.Map;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.builtins.objects.cell.PCell;
import com.oracle.graal.python.builtins.objects.code.PCode;
import com.oracle.graal.python.builtins.objects.function.PArguments;
import com.oracle.graal.python.builtins.objects.function.PFunction;
import com.oracle.graal.python.builtins.objects.function.PKeyword;
import com.oracle.graal.python.nodes.PRootNode;
import com.oracle.graal.python.nodes.expression.ExpressionNode;
import com.oracle.graal.python.nodes.generator.GeneratorFunctionRootNode;
import com.oracle.graal.python.parser.DefinitionCellSlots;
import com.oracle.graal.python.parser.ExecutionCellSlots;
import com.oracle.graal.python.parser.GeneratorInfo;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.ExplodeLoop;

public class GeneratorFunctionDefinitionNode extends FunctionDefinitionNode {
    protected final GeneratorInfo generatorInfo;
    protected final FrameDescriptor frameDescriptor;

    @CompilationFinal private RootCallTarget generatorCallTarget;
    @CompilationFinal private PCode generatorCode;

    public GeneratorFunctionDefinitionNode(String name, String qualname, String enclosingClassName, ExpressionNode doc, ExpressionNode[] defaults, KwDefaultExpressionNode[] kwDefaults,
                    RootCallTarget callTarget, FrameDescriptor frameDescriptor, DefinitionCellSlots definitionCellSlots, ExecutionCellSlots executionCellSlots, GeneratorInfo generatorInfo,
                    Map<String, ExpressionNode> annotations) {
        super(name, qualname, enclosingClassName, doc, defaults, kwDefaults, callTarget, definitionCellSlots, executionCellSlots, annotations);
        this.frameDescriptor = frameDescriptor;
        this.generatorInfo = generatorInfo;
    }

    public static GeneratorFunctionDefinitionNode create(String name, String qualname, String enclosingClassName, ExpressionNode doc, ExpressionNode[] defaults, KwDefaultExpressionNode[] kwDefaults,
                    RootCallTarget callTarget, FrameDescriptor frameDescriptor, DefinitionCellSlots definitionCellSlots, ExecutionCellSlots executionCellSlots, GeneratorInfo generatorInfo,
                    Map<String, ExpressionNode> annotations) {
        return new GeneratorFunctionDefinitionNode(name, qualname, enclosingClassName, doc, defaults, kwDefaults, callTarget,
                        frameDescriptor, definitionCellSlots, executionCellSlots, generatorInfo, annotations);
    }

    @Override
    @ExplodeLoop // this should always be safe, how many syntactic defaults can one function have...
                 // and these are constant
    public PFunction execute(VirtualFrame frame) {
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
        PCell[] closure = getClosureFromGeneratorOrFunctionLocals(frame);
        return withDocString(frame, factory().createFunction(functionName, qualname, enclosingClassName, getGeneratorCode(), PArguments.getGlobals(frame), defaultValues, kwDefaultValues, closure));
    }

    public GeneratorFunctionRootNode getGeneratorFunctionRootNode(PythonContext ctx) {
        if (generatorCallTarget == null) {
            return new GeneratorFunctionRootNode(ctx.getLanguage(), callTarget, functionName, frameDescriptor,
                            executionCellSlots, ((PRootNode) callTarget.getRootNode()).getSignature(), generatorInfo);
        }
        return (GeneratorFunctionRootNode) generatorCallTarget.getRootNode();
    }

    protected PCode getGeneratorCode() {
        if (generatorCallTarget == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            generatorCallTarget = Truffle.getRuntime().createCallTarget(getGeneratorFunctionRootNode(getContext()));
        }
        PythonLanguage lang = lookupLanguageReference(PythonLanguage.class).get();
        CompilerAsserts.partialEvaluationConstant(lang);
        if (lang.singleContextAssumption.isValid()) {
            if (generatorCode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                generatorCode = factory().createCode(generatorCallTarget);
            }
            return generatorCode;
        } else {
            return factory().createCode(generatorCallTarget);
        }
    }

    public GeneratorInfo getGeneratorInfo() {
        return generatorInfo;
    }

    public FrameDescriptor getFrameDescriptor() {
        return frameDescriptor;
    }

}
