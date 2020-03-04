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
package com.oracle.graal.python.builtins.objects.function;

import static com.oracle.graal.python.nodes.SpecialAttributeNames.__NAME__;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.__QUALNAME__;

import com.oracle.graal.python.builtins.objects.cell.PCell;
import com.oracle.graal.python.builtins.objects.code.PCode;
import com.oracle.graal.python.builtins.objects.object.PythonObject;
import com.oracle.graal.python.builtins.objects.object.PythonObjectLibrary;
import com.oracle.graal.python.builtins.objects.type.LazyPythonClass;
import com.oracle.graal.python.nodes.PRootNode;
import com.oracle.graal.python.nodes.SpecialMethodNames;
import com.oracle.graal.python.nodes.attributes.WriteAttributeToDynamicObjectNode;
import com.oracle.graal.python.nodes.generator.GeneratorFunctionRootNode;
import com.oracle.truffle.api.Assumption;
import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.object.DynamicObject;
import com.oracle.truffle.api.source.SourceSection;

@ExportLibrary(PythonObjectLibrary.class)
public class PFunction extends PythonObject {
    private static final Object[] EMPTY_DEFAULTS = new Object[0];
    private final String name;
    private final String enclosingClassName;
    private final Assumption codeStableAssumption;
    private final Assumption defaultsStableAssumption;
    private final PythonObject globals;
    @CompilationFinal(dimensions = 1) private final PCell[] closure;
    private final boolean isStatic;
    @CompilationFinal private PCode code;
    @CompilationFinal(dimensions = 1) private Object[] defaultValues;
    @CompilationFinal(dimensions = 1) private PKeyword[] kwDefaultValues;

    public PFunction(LazyPythonClass clazz, String name, String enclosingClassName, PCode code, PythonObject globals, PCell[] closure) {
        this(clazz, name, enclosingClassName, code, globals, EMPTY_DEFAULTS, PKeyword.EMPTY_KEYWORDS, closure);
    }

    public PFunction(LazyPythonClass clazz, String name, String enclosingClassName, PCode code, PythonObject globals, Object[] defaultValues, PKeyword[] kwDefaultValues,
                    PCell[] closure) {
        this(clazz, name, enclosingClassName, code, globals, defaultValues, kwDefaultValues, closure, null, Truffle.getRuntime().createAssumption(), Truffle.getRuntime().createAssumption());
    }

    public PFunction(LazyPythonClass clazz, String name, String enclosingClassName, PCode code, PythonObject globals, Object[] defaultValues, PKeyword[] kwDefaultValues,
                    PCell[] closure, WriteAttributeToDynamicObjectNode writeAttrNode, Assumption codeStableAssumption, Assumption defaultsStableAssumption) {
        super(clazz);
        this.name = name;
        this.code = code;
        this.isStatic = name.equals(SpecialMethodNames.__NEW__);
        this.enclosingClassName = enclosingClassName;
        this.globals = globals;
        this.defaultValues = defaultValues == null ? EMPTY_DEFAULTS : defaultValues;
        this.kwDefaultValues = kwDefaultValues == null ? PKeyword.EMPTY_KEYWORDS : kwDefaultValues;
        this.closure = closure;
        this.codeStableAssumption = codeStableAssumption;
        this.defaultsStableAssumption = defaultsStableAssumption;
        addDefaultConstants(writeAttrNode, getStorage(), name, enclosingClassName);
    }

    private static void addDefaultConstants(WriteAttributeToDynamicObjectNode writeAttrNode, DynamicObject storage, String name, String enclosingClassName) {
        if (writeAttrNode != null) {
            writeAttrNode.execute(storage, __NAME__, name);
            writeAttrNode.execute(storage, __QUALNAME__, enclosingClassName != null ? enclosingClassName + "." + name : name);
        } else {
            storage.define(__NAME__, name);
            storage.define(__QUALNAME__, enclosingClassName != null ? enclosingClassName + "." + name : name);
        }
    }

    public boolean isStatic() {
        return isStatic;
    }

    public RootCallTarget getCallTarget() {
        return getCode().getRootCallTarget();
    }

    public Assumption getCodeStableAssumption() {
        return codeStableAssumption;
    }

    public Assumption getDefaultsStableAssumption() {
        return defaultsStableAssumption;
    }

    public PythonObject getGlobals() {
        return globals;
    }

    public RootNode getFunctionRootNode() {
        return getCallTarget().getRootNode();
    }

    public String getName() {
        return name;
    }

    public String getQualifiedName() {
        if (enclosingClassName == null) {
            return name;
        } else {
            return enclosingClassName + "." + name;
        }
    }

    public Signature getSignature() {
        return getCode().getSignature();
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
        return String.format("PFunction %s at 0x%x", getQualifiedName(), hashCode());
    }

    public PCode getCode() {
        return code;
    }

    public void setCode(PCode code) {
        codeStableAssumption.invalidate("code changed for function " + getName());
        this.code = code;
    }

    public String getEnclosingClassName() {
        return enclosingClassName;
    }

    public Object[] getDefaults() {
        return defaultValues;
    }

    public void setDefaults(Object[] defaults) {
        this.defaultsStableAssumption.invalidate("defaults changed for function " + getName());
        this.defaultValues = defaults;
    }

    public PKeyword[] getKwDefaults() {
        return kwDefaultValues;
    }

    public void setKwDefaults(PKeyword[] defaults) {
        this.defaultsStableAssumption.invalidate("kw defaults changed for function " + getName());
        this.kwDefaultValues = defaults;
    }

    @TruffleBoundary
    public String getSourceCode() {
        RootNode rootNode = getCallTarget().getRootNode();
        if (rootNode instanceof GeneratorFunctionRootNode) {
            rootNode = ((GeneratorFunctionRootNode) rootNode).getFunctionRootNode();
        }
        SourceSection sourceSection = rootNode.getSourceSection();
        if (sourceSection != null) {
            return sourceSection.getCharacters().toString();
        }
        return null;
    }

    @ExportMessage
    public boolean isCallable() {
        return true;
    }

    @ExportMessage
    public SourceSection getSourceLocation() {
        RootNode rootNode = getCallTarget().getRootNode();
        if (rootNode instanceof PRootNode) {
            return ((PRootNode) rootNode).getSourceSection();
        } else {
            return getForeignSourceSection(rootNode);
        }
    }

    @TruffleBoundary
    private static SourceSection getForeignSourceSection(RootNode rootNode) {
        return rootNode.getSourceSection();
    }

    @ExportMessage
    public boolean hasSourceLocation() {
        return true;
    }
}
