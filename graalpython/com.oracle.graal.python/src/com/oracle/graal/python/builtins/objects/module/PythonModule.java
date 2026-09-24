/*
 * Copyright (c) 2017, 2026, Oracle and/or its affiliates.
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
import static com.oracle.graal.python.runtime.nativeaccess.NativeMemory.NULLPTR;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.dict.PDict;
import com.oracle.graal.python.builtins.objects.object.PythonObject;
import com.oracle.graal.python.nodes.PGuards;
import com.oracle.graal.python.nodes.object.GetDictIfExistsNode;
import com.oracle.graal.python.nodes.object.GetOrCreateDictNode;
import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.object.Shape;
import com.oracle.truffle.api.strings.TruffleString;

public final class PythonModule extends PythonObject {

    private NativeModuleData nativeModuleData;

    private PythonBuiltins builtins;
    private Object moduleState;

    @TruffleBoundary
    public PythonModule(Object clazz, Shape instanceShape) {
        super(clazz, instanceShape);
        setAttribute(T___NAME__, PNone.NO_VALUE);
        setAttribute(T___DOC__, PNone.NO_VALUE);
        setAttribute(T___PACKAGE__, PNone.NO_VALUE);
        setAttribute(T___LOADER__, PNone.NO_VALUE);
        setAttribute(T___SPEC__, PNone.NO_VALUE);
        setAttribute(T___CACHED__, PNone.NO_VALUE);
        setAttribute(T___FILE__, PNone.NO_VALUE);
        GetOrCreateDictNode.ensureModuleDict(this);
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
        GetOrCreateDictNode.ensureModuleDict(lang, this);
    }

    /**
     * Only to be used during context creation
     */
    @TruffleBoundary
    public static PythonModule createInternal(TruffleString moduleName) {
        PythonLanguage language = PythonLanguage.get(null);
        return new PythonModule(language, moduleName);
    }

    public PythonBuiltins getBuiltins() {
        return builtins;
    }

    public void setBuiltins(PythonBuiltins builtins) {
        this.builtins = builtins;
    }

    public <T> T getModuleState(Class<T> clazz) {
        return clazz.cast(moduleState);
    }

    public void setModuleState(Object moduleState) {
        this.moduleState = moduleState;
    }

    @Override
    public String toString() {
        Object attribute = this.getAttribute(T___NAME__);
        return "<module '" + (PGuards.isNoValue(attribute) ? "?" : attribute) + "'>";
    }

    private NativeModuleData ensureNativeModuleData() {
        if (nativeModuleData == null) {
            nativeModuleData = new NativeModuleData();
        }
        return nativeModuleData;
    }

    public long getNativeModuleDef() {
        return nativeModuleData != null ? nativeModuleData.def : NULLPTR;
    }

    public void setNativeModuleDef(long nativeModuleDef) {
        ensureNativeModuleData().def = nativeModuleDef;
    }

    public long getNativeModuleState() {
        return nativeModuleData != null ? nativeModuleData.state : NULLPTR;
    }

    public void setNativeModuleState(long nativeModuleState) {
        ensureNativeModuleData().state = nativeModuleState;
    }

    public long getNativeModuleStateSize() {
        return nativeModuleData != null ? nativeModuleData.stateSize : 0;
    }

    public void setNativeModuleStateSize(long nativeModuleStateSize) {
        ensureNativeModuleData().stateSize = nativeModuleStateSize;
    }

    public long getNativeModuleTraverse() {
        return nativeModuleData != null ? nativeModuleData.stateTraverse : NULLPTR;
    }

    public void setNativeModuleTraverse(long nativeModuleTraverse) {
        ensureNativeModuleData().stateTraverse = nativeModuleTraverse;
    }

    public long getNativeModuleClear() {
        return nativeModuleData != null ? nativeModuleData.stateClear : NULLPTR;
    }

    public void setNativeModuleClear(long nativeModuleClear) {
        ensureNativeModuleData().stateClear = nativeModuleClear;
    }

    public long getNativeModuleFree() {
        return nativeModuleData != null ? nativeModuleData.stateFree : NULLPTR;
    }

    public void setNativeModuleFree(long nativeModuleFree) {
        ensureNativeModuleData().stateFree = nativeModuleFree;
    }

    public long getNativeModuleExec() {
        return nativeModuleData != null ? nativeModuleData.exec : NULLPTR;
    }

    public void setNativeModuleExec(long nativeModuleExec) {
        ensureNativeModuleData().exec = nativeModuleExec;
    }

    public long getNativeModuleToken() {
        return nativeModuleData != null ? nativeModuleData.token : NULLPTR;
    }

    public void setNativeModuleToken(long nativeModuleToken) {
        ensureNativeModuleData().token = nativeModuleToken;
    }

    public boolean isNativeModuleTokenIsDef() {
        return nativeModuleData != null && nativeModuleData.tokenIsDef;
    }

    public void setNativeModuleTokenIsDef(boolean nativeModuleTokenIsDef) {
        ensureNativeModuleData().tokenIsDef = nativeModuleTokenIsDef;
    }

    public boolean isNativeModuleRequiresGil() {
        return nativeModuleData != null && nativeModuleData.requiresGil;
    }

    public void setNativeModuleRequiresGil(boolean nativeModuleRequiresGil) {
        ensureNativeModuleData().requiresGil = nativeModuleRequiresGil;
    }

    /**
     * For a description, see {@link NativeModuleData#replicatedNativeReferences}.
     */
    public void setReplicatedNativeReferences(Object[] replicatedNativeReferences) {
        ensureNativeModuleData().replicatedNativeReferences = replicatedNativeReferences;
    }

    public Object[] getReplicatedNativeReferences() {
        return nativeModuleData != null ? nativeModuleData.replicatedNativeReferences : null;
    }

    public PDict getDict() {
        // PythonModule always have a dict
        CompilerAsserts.neverPartOfCompilation();
        return GetDictIfExistsNode.getDictUncached(this);
    }
}
