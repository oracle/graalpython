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
package com.oracle.graal.python.builtins.objects.function;

import static com.oracle.graal.python.nodes.SpecialAttributeNames.__NAME__;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.__QUALNAME__;

import com.oracle.graal.python.builtins.objects.cell.PCell;
import com.oracle.graal.python.builtins.objects.code.PCode;
import com.oracle.graal.python.builtins.objects.object.PythonObject;
import com.oracle.graal.python.builtins.objects.type.LazyPythonClass;
import com.oracle.graal.python.nodes.SpecialMethodNames;
import com.oracle.graal.python.nodes.generator.GeneratorFunctionRootNode;
import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.object.DynamicObject;
import com.oracle.truffle.api.source.SourceSection;

public class PFunction extends PythonObject {

    private final String name;
    private final String enclosingClassName;
    private final Arity arity;
    private final RootCallTarget callTarget;
    private final PythonObject globals;
    private final PCell[] closure;
    private final boolean isStatic;
    private PCode code;
    private Object[] defaultValues;

    public PFunction(LazyPythonClass clazz, String name, String enclosingClassName, Arity arity, RootCallTarget callTarget, PythonObject globals, PCell[] closure) {
        this(clazz, name, enclosingClassName, arity, callTarget, globals, null, closure);
    }

    public PFunction(LazyPythonClass clazz, String name, String enclosingClassName, Arity arity, RootCallTarget callTarget, PythonObject globals, Object[] defaultValues,
                    PCell[] closure) {
        super(clazz);
        this.name = name;
        this.isStatic = name.equals(SpecialMethodNames.__NEW__);
        this.enclosingClassName = enclosingClassName;
        this.arity = arity;
        this.callTarget = callTarget;
        this.globals = globals;
        this.defaultValues = defaultValues;
        this.closure = closure;
        addDefaultConstants(this.getStorage(), name, enclosingClassName);
    }

    @TruffleBoundary
    private static void addDefaultConstants(DynamicObject storage, String name, String enclosingClassName) {
        storage.define(__NAME__, name);
        storage.define(__QUALNAME__, enclosingClassName != null ? enclosingClassName + "." + name : name);
    }

    public boolean isStatic() {
        return isStatic;
    }

    public RootCallTarget getCallTarget() {
        return callTarget;
    }

    public PythonObject getGlobals() {
        return globals;
    }

    public RootNode getFunctionRootNode() {
        return callTarget.getRootNode();
    }

    public String getName() {
        return name;
    }

    public Arity getArity() {
        return arity;
    }

    public PCell[] getClosure() {
        return closure;
    }

    public boolean isGeneratorFunction() {
        return false;
    }

    public PGeneratorFunction asGeneratorFunction() {
        return null;
    }

    @Override
    public final String toString() {
        CompilerAsserts.neverPartOfCompilation();
        if (enclosingClassName == null) {
            return String.format("PFunction %s at 0x%x", name, hashCode());
        } else {
            return String.format("PFunction %s.%s at 0x%x", enclosingClassName, name, hashCode());
        }
    }

    public PCode getCode() {
        return code;
    }

    public void setCode(PCode code) {
        this.code = code;
    }

    public String getEnclosingClassName() {
        return enclosingClassName;
    }

    public Object[] getDefaults() {
        return defaultValues;
    }

    public void setDefaults(Object[] defaults) {
        this.defaultValues = defaults;
    }

    @TruffleBoundary
    public String getSourceCode() {
        RootNode rootNode = callTarget.getRootNode();
        if (rootNode instanceof GeneratorFunctionRootNode) {
            rootNode = ((GeneratorFunctionRootNode) rootNode).getFunctionRootNode();
        }
        SourceSection sourceSection = rootNode.getSourceSection();
        if (sourceSection != null) {
            return sourceSection.getCharacters().toString();
        }
        return null;
    }
}
