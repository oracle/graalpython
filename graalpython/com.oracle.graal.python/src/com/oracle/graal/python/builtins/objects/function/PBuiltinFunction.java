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

import static com.oracle.graal.python.nodes.SpecialAttributeNames.__NAME__;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.__QUALNAME__;

import com.oracle.graal.python.builtins.BoundBuiltinCallable;
import com.oracle.graal.python.builtins.objects.module.PythonModule;
import com.oracle.graal.python.builtins.objects.object.PythonBuiltinObject;
import com.oracle.graal.python.builtins.objects.type.PythonClass;
import com.oracle.graal.python.nodes.SpecialMethodNames;
import com.oracle.graal.python.nodes.function.BuiltinFunctionRootNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.runtime.object.PythonObjectFactory;
import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.nodes.RootNode;

public final class PBuiltinFunction extends PythonBuiltinObject implements PythonCallable, BoundBuiltinCallable<PBuiltinFunction> {

    private final String name;
    private final PythonClass enclosingType;
    private final RootCallTarget callTarget;
    private final Arity arity;
    private final boolean isStatic;

    public PBuiltinFunction(PythonClass clazz, String name, PythonClass enclosingType, Arity arity, RootCallTarget callTarget) {
        super(clazz);
        this.name = name;
        this.isStatic = name.equals(SpecialMethodNames.__NEW__);
        this.enclosingType = enclosingType;
        this.callTarget = callTarget;
        this.arity = arity;
        this.getStorage().define(__NAME__, name);
        if (enclosingType != null) {
            this.getStorage().define("__objclass__", enclosingType);
            this.getStorage().define(__QUALNAME__, enclosingType.getName() + "." + name);
        } else {
            this.getStorage().define(__QUALNAME__, name);
        }
    }

    public boolean isStatic() {
        return isStatic;
    }

    public RootNode getFunctionRootNode() {
        return callTarget.getRootNode();
    }

    public NodeFactory<? extends PythonBuiltinBaseNode> getBuiltinNodeFactory() {
        RootNode functionRootNode = getFunctionRootNode();
        if (functionRootNode instanceof BuiltinFunctionRootNode) {
            return ((BuiltinFunctionRootNode) functionRootNode).getFactory();
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

    public PythonClass getEnclosingType() {
        return enclosingType;
    }

    @Override
    public String toString() {
        CompilerAsserts.neverPartOfCompilation();
        if (enclosingType == null) {
            return String.format("PBuiltinFunction %s at 0x%x", name, hashCode());
        } else {
            return String.format("PBuiltinFunction %s.%s at 0x%x", enclosingType.getName(), name, hashCode());
        }
    }

    public PBuiltinFunction boundToObject(Object klass, PythonObjectFactory factory) {
        if (klass == enclosingType) {
            return this;
        } else if (klass instanceof PythonModule) {
            return this;
        } else {
            return factory.createBuiltinFunction(name, (PythonClass) klass, arity, callTarget);
        }
    }
}
