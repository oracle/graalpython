/*
 * Copyright (c) 2017, 2024, Oracle and/or its affiliates.
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
package com.oracle.graal.python.builtins.objects.module;

import static com.oracle.graal.python.nodes.SpecialAttributeNames.T___CACHED__;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.T___DOC__;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.T___FILE__;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.T___LOADER__;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.T___NAME__;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.T___PACKAGE__;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.T___SPEC__;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.dict.PDict;
import com.oracle.graal.python.builtins.objects.object.PythonObject;
import com.oracle.graal.python.nodes.PGuards;
import com.oracle.graal.python.nodes.object.SetDictNode;
import com.oracle.graal.python.runtime.object.PythonObjectFactory;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.object.Shape;
import com.oracle.truffle.api.strings.TruffleString;

public final class PythonModule extends PythonObject {

    /**
     * Stores the native {@code PyModuleDef *} structure if this modules was created via the
     * multi-phase extension module initialization mechanism.
     */
    private Object nativeModuleDef;
    private Object nativeModuleState;

    private PythonBuiltins builtins;
    private Object internalAttributes;

    public PythonModule(Object clazz, Shape instanceShape) {
        super(clazz, instanceShape);
        setAttribute(T___NAME__, PNone.NO_VALUE);
        setAttribute(T___DOC__, PNone.NO_VALUE);
        setAttribute(T___PACKAGE__, PNone.NO_VALUE);
        setAttribute(T___LOADER__, PNone.NO_VALUE);
        setAttribute(T___SPEC__, PNone.NO_VALUE);
        setAttribute(T___CACHED__, PNone.NO_VALUE);
        setAttribute(T___FILE__, PNone.NO_VALUE);
    }

    /**
     * This constructor is just used to created built-in modules such that we can avoid the call to
     * {code __init__}.
     */
    private PythonModule(PythonLanguage lang, TruffleString moduleName) {
        super(PythonBuiltinClassType.PythonModule, PythonBuiltinClassType.PythonModule.getInstanceShape(lang));
        setAttribute(T___NAME__, moduleName);
        setAttribute(T___DOC__, PNone.NONE);
        setAttribute(T___PACKAGE__, PNone.NONE);
        setAttribute(T___LOADER__, PNone.NONE);
        setAttribute(T___SPEC__, PNone.NONE);
        setAttribute(T___CACHED__, PNone.NO_VALUE);
        setAttribute(T___FILE__, PNone.NO_VALUE);
    }

    /**
     * Only to be used during context creation
     */
    @TruffleBoundary
    public static PythonModule createInternal(TruffleString moduleName) {
        PythonObjectFactory factory = PythonObjectFactory.getUncached();
        PythonModule pythonModule = new PythonModule(PythonLanguage.get(null), moduleName);
        PDict dict = factory.createDictFixedStorage(pythonModule);
        SetDictNode.executeUncached(pythonModule, dict);
        return pythonModule;
    }

    public PythonBuiltins getBuiltins() {
        return builtins;
    }

    public void setBuiltins(PythonBuiltins builtins) {
        this.builtins = builtins;
    }

    @SuppressWarnings("unchecked")
    public <T> T getInternalAttributes() {
        return (T) internalAttributes;
    }

    public void setInternalAttributes(Object internalAttributes) {
        this.internalAttributes = internalAttributes;
    }

    @Override
    public String toString() {
        Object attribute = this.getAttribute(T___NAME__);
        return "<module '" + (PGuards.isNoValue(attribute) ? "?" : attribute) + "'>";
    }

    public Object getNativeModuleDef() {
        return nativeModuleDef;
    }

    public void setNativeModuleDef(Object nativeModuleDef) {
        this.nativeModuleDef = nativeModuleDef;
    }

    public Object getNativeModuleState() {
        return nativeModuleState;
    }

    public void setNativeModuleState(Object nativeModuleState) {
        this.nativeModuleState = nativeModuleState;
    }

}
