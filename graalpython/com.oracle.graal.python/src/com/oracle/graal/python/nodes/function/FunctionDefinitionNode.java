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
package com.oracle.graal.python.nodes.function;

import com.oracle.graal.python.builtins.objects.cell.PCell;
import com.oracle.graal.python.builtins.objects.function.Arity;
import com.oracle.graal.python.builtins.objects.function.PArguments;
import com.oracle.graal.python.builtins.objects.function.PFunction;
import com.oracle.graal.python.builtins.objects.module.PythonModule;
import com.oracle.graal.python.nodes.PNode;
import com.oracle.graal.python.nodes.SpecialAttributeNames;
import com.oracle.graal.python.nodes.frame.ReadGlobalOrBuiltinNode;
import com.oracle.graal.python.parser.DefinitionCellSlots;
import com.oracle.graal.python.parser.ExecutionCellSlots;
import com.oracle.graal.python.runtime.PythonCore;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.RootNode;

public class FunctionDefinitionNode extends ExpressionDefinitionNode {

    protected final String functionName;
    protected final String enclosingClassName;
    protected final PythonCore core;
    protected final RootCallTarget callTarget;
    protected final FrameDescriptor frameDescriptor;
    protected final Arity arity;

    @Child protected PNode defaults;
    @Child private ReadGlobalOrBuiltinNode getModuleName;

    public FunctionDefinitionNode(String functionName, String enclosingClassName, PythonCore core, Arity arity, PNode defaults, RootCallTarget callTarget, FrameDescriptor frameDescriptor,
                    DefinitionCellSlots definitionCellSlots, ExecutionCellSlots executionCellSlots) {
        super(definitionCellSlots, executionCellSlots);
        this.functionName = functionName;
        this.enclosingClassName = enclosingClassName;
        this.core = core;
        this.callTarget = callTarget;
        this.frameDescriptor = frameDescriptor;
        this.arity = arity;
        this.defaults = defaults;
    }

    @Override
    public Object execute(VirtualFrame frame) {
        defaults.executeVoid(frame);

        PCell[] closure = getClosureFromGeneratorOrFunctionLocals(frame);
        PFunction func = factory().createFunction(functionName, enclosingClassName, arity, callTarget, frameDescriptor, PArguments.getGlobals(frame), closure);
        if (getContext().isInitialized()) {
            return func;
        } else {
            /*
             * (tfel): To be compatible with CPython, builtin module functions must be bound to
             * their respective builtin module. We ignore that builtin functions should really be
             * builtin methods here - it does not hurt if they are normal methods. What does hurt,
             * however, is if they are not bound, because then using these functions in class field
             * won't work when they are called from an instance of that class due to the implicit
             * currying with "self".
             */
            CompilerDirectives.transferToInterpreter();
            if (enclosingClassName == null) {
                if (getModuleName == null) {
                    getModuleName = insert(ReadGlobalOrBuiltinNode.create(SpecialAttributeNames.__NAME__));
                }
                Object moduleName = getModuleName.execute(frame);
                if (moduleName instanceof String) {
                    PythonModule builtinModule = getContext().getCore().lookupBuiltinModule((String) moduleName);
                    if (builtinModule != null) {
                        return factory().createMethod(builtinModule, func);
                    }
                }
            }
            return func;
        }
    }

    public String getFunctionName() {
        return functionName;
    }

    public RootNode getFunctionRoot() {
        return callTarget.getRootNode();
    }
}
