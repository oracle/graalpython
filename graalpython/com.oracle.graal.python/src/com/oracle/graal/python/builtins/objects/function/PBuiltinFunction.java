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

import com.oracle.graal.python.builtins.objects.object.PythonBuiltinObject;
import com.oracle.graal.python.builtins.objects.type.PythonClass;
import com.oracle.graal.python.nodes.SpecialMethodNames;
import com.oracle.graal.python.nodes.function.BuiltinFunctionRootNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinNode;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.nodes.RootNode;

public class PBuiltinFunction extends PythonBuiltinObject implements PythonCallable {

    @CompilationFinal private String name;
    private final RootCallTarget callTarget;
    private final Arity arity;
    private final boolean isStatic;

    public PBuiltinFunction(PythonClass clazz, String name, Arity arity, RootCallTarget callTarget) {
        super(clazz);
        this.name = name;
        this.isStatic = name.equals(SpecialMethodNames.__NEW__);
        this.callTarget = callTarget;
        this.arity = arity;
    }

    public boolean isStatic() {
        return isStatic;
    }

    public RootNode getFunctionRootNode() {
        return callTarget.getRootNode();
    }

    public PythonBuiltinNode getBuiltinNode() {
        RootNode functionRootNode = getFunctionRootNode();
        if (functionRootNode instanceof BuiltinFunctionRootNode) {
            return ((BuiltinFunctionRootNode) functionRootNode).getBody();
        } else {
            return null;
        }
    }

    @Override
    public Arity getArity() {
        return arity;
    }

    @Override
    public RootCallTarget getCallTarget() {
        return callTarget;
    }

    @Override
    public String getName() {
        return name;
    }

    public void unsafeSetName(String newName) {
        name = newName;
    }

    @Override
    public String toString() {
        return "<built-in function " + name + ">";
    }
}
