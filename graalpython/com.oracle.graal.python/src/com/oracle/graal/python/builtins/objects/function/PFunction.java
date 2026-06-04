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
package com.oracle.graal.python.builtins.objects.function;

import java.util.ArrayList;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.cell.PCell;
import com.oracle.graal.python.builtins.objects.code.PCode;
import com.oracle.graal.python.builtins.objects.common.HashingStorage;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodes.HashingStorageGetIterator;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodes.HashingStorageIterator;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodes.HashingStorageIteratorKey;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodes.HashingStorageIteratorNext;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodes.HashingStorageIteratorValue;
import com.oracle.graal.python.builtins.objects.common.KeywordsStorage;
import com.oracle.graal.python.builtins.objects.dict.PDict;
import com.oracle.graal.python.builtins.objects.object.PythonObject;
import com.oracle.graal.python.builtins.objects.str.PString;
import com.oracle.graal.python.compiler.CodeUnit;
import com.oracle.graal.python.lib.PyUnicodeCheckNode;
import com.oracle.graal.python.nodes.PRootNode;
import com.oracle.graal.python.runtime.GilNode;
import com.oracle.graal.python.runtime.object.PFactory;
import com.oracle.graal.python.util.PythonUtils;
import com.oracle.truffle.api.Assumption;
import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.source.SourceSection;
import com.oracle.truffle.api.strings.TruffleString;

@ExportLibrary(InteropLibrary.class)
public final class PFunction extends PythonObject {
    private TruffleString name;
    private TruffleString qualname;
    private final Assumption codeStableAssumption;
    private final PythonObject globals;
    @CompilationFinal private boolean isBuiltin;
    @CompilationFinal(dimensions = 1) private final PCell[] closure;
    @CompilationFinal private PCode finalCode;
    private PCode code;
    private RootCallTarget callTarget;
    @CompilationFinal(dimensions = 1) private Object[] finalDefaultValues;
    private Object[] defaultValues;
    @CompilationFinal(dimensions = 1) private PKeyword[] finalKwDefaultValues;
    private PKeyword[] kwDefaultValues;
    private PDict kwDefaultDict;
    private Object doc;

    public PFunction(PythonLanguage lang, TruffleString name, TruffleString qualname, PCode code, PythonObject globals, PCell[] closure) {
        this(lang, name, qualname, code, globals, PythonUtils.EMPTY_OBJECT_ARRAY, PKeyword.EMPTY_KEYWORDS, closure);
    }

    public PFunction(PythonLanguage lang, TruffleString name, TruffleString qualname, PCode code, PythonObject globals, Object[] defaultValues,
                    PKeyword[] kwDefaultValues, PCell[] closure) {
        this(lang, name, qualname, code, globals, defaultValues, kwDefaultValues, closure, Truffle.getRuntime().createAssumption());
    }

    public PFunction(PythonLanguage lang, TruffleString name, TruffleString qualname, PCode code, PythonObject globals, Object[] defaultValues,
                    PKeyword[] kwDefaultValues, PCell[] closure, Assumption codeStableAssumption) {
        super(PythonBuiltinClassType.PFunction, PythonBuiltinClassType.PFunction.getInstanceShape(lang));
        this.name = name;
        this.qualname = qualname;
        assert code != null;
        this.code = this.finalCode = code;
        this.globals = globals;
        this.defaultValues = this.finalDefaultValues = defaultValues == null ? PythonUtils.EMPTY_OBJECT_ARRAY : defaultValues;
        this.kwDefaultValues = this.finalKwDefaultValues = kwDefaultValues == null ? PKeyword.EMPTY_KEYWORDS : kwDefaultValues;
        this.closure = closure;
        this.codeStableAssumption = codeStableAssumption;
    }

    public Assumption getCodeStableAssumption() {
        return codeStableAssumption;
    }

    public PythonObject getGlobals() {
        return globals;
    }

    public TruffleString getName() {
        return name;
    }

    public void setName(TruffleString name) {
        this.name = name;
    }

    public TruffleString getQualname() {
        return this.qualname;
    }

    public void setQualname(TruffleString qualname) {
        this.qualname = qualname;
    }

    public PCell[] getClosure() {
        return closure;
    }

    public boolean isBuiltin() {
        return isBuiltin;
    }

    public void setBuiltin(boolean builtin) {
        isBuiltin = builtin;
    }

    public Object getDoc() {
        if (CompilerDirectives.injectBranchProbability(CompilerDirectives.SLOWPATH_PROBABILITY, doc == null)) {
            extractDoc();
        }
        return doc;
    }

    public void setDoc(Object doc) {
        this.doc = doc;
    }

    @TruffleBoundary
    private void extractDoc() {
        CodeUnit co = getCode().getCodeUnit();
        if (co != null && co.constants.length > 0 && PyUnicodeCheckNode.executeUncached(co.constants[0])) {
            doc = co.constants[0];
        } else {
            doc = PNone.NONE;
        }
    }

    @Override
    public final String toString() {
        CompilerAsserts.neverPartOfCompilation();
        return String.format("PFunction %s at 0x%x", getQualname(), hashCode());
    }

    public PCode getCode() {
        if (CompilerDirectives.inCompiledCode() && CompilerDirectives.isPartialEvaluationConstant(this)) {
            if (getCodeStableAssumption().isValid()) {
                return finalCode;
            }
        }
        return code;
    }

    public RootCallTarget getCallTarget() {
        RootCallTarget ct = callTarget;
        if (CompilerDirectives.injectBranchProbability(CompilerDirectives.SLOWPATH_PROBABILITY, ct == null)) {
            ct = getCode().getRootCallTarget();
            callTarget = ct;
        }
        return ct;
    }

    @TruffleBoundary
    public void setCode(PCode code) {
        codeStableAssumption.invalidate("code changed for function " + getName());
        assert code != null : "code cannot be null";
        this.finalCode = null;
        this.code = code;
        this.callTarget = null;
    }

    public Object[] getDefaults() {
        if (CompilerDirectives.inCompiledCode() && CompilerDirectives.isPartialEvaluationConstant(this)) {
            if (codeStableAssumption.isValid()) {
                return finalDefaultValues;
            }
        }
        return defaultValues;
    }

    @TruffleBoundary
    public void setDefaults(Object[] defaults) {
        this.codeStableAssumption.invalidate("defaults changed for function " + getName());
        this.finalDefaultValues = null; // avoid leak, and make code that wrongly uses it crash
        this.defaultValues = defaults;
    }

    public PKeyword[] getKwDefaults() {
        if (CompilerDirectives.inCompiledCode() && CompilerDirectives.isPartialEvaluationConstant(this)) {
            if (codeStableAssumption.isValid()) {
                assert kwDefaultDict == null;
                return finalKwDefaultValues;
            }
        }
        if (kwDefaultDict != null) {
            HashingStorage storage = kwDefaultDict.getDictStorage();
            if (CompilerDirectives.injectBranchProbability(CompilerDirectives.LIKELY_PROBABILITY, storage instanceof KeywordsStorage)) {
                return ((KeywordsStorage) storage).getStore();
            }
            return kwDefaultsFromStorage(storage);
        }
        return kwDefaultValues;
    }

    public Object getKwDefaultsDict(PythonLanguage language) {
        if (kwDefaultDict == null) {
            PKeyword[] kwdefaults = getKwDefaults();
            if (kwdefaults.length == 0) {
                return PNone.NONE;
            }
            setKwDefaultsDict(PFactory.createDict(language, kwdefaults));
        }
        return kwDefaultDict;
    }

    @TruffleBoundary
    public void setKwDefaultsDict(PDict defaults) {
        this.codeStableAssumption.invalidate("kw defaults changed for function " + getName());
        this.finalKwDefaultValues = null; // avoid leak, and make code that wrongly uses it crash
        this.kwDefaultValues = PKeyword.EMPTY_KEYWORDS;
        this.kwDefaultDict = defaults;
    }

    @TruffleBoundary
    private static PKeyword[] kwDefaultsFromStorage(HashingStorage storage) {
        ArrayList<PKeyword> keywords = new ArrayList<>();
        HashingStorageIterator it = HashingStorageGetIterator.executeUncached(storage);
        while (HashingStorageIteratorNext.executeUncached(storage, it)) {
            Object key = HashingStorageIteratorKey.executeUncached(storage, it);
            if (key instanceof PString) {
                key = ((PString) key).getValueUncached();
            }
            if (key instanceof TruffleString) {
                keywords.add(new PKeyword((TruffleString) key, HashingStorageIteratorValue.executeUncached(storage, it)));
            }
        }
        return keywords.isEmpty() ? PKeyword.EMPTY_KEYWORDS : keywords.toArray(new PKeyword[keywords.size()]);
    }

    @TruffleBoundary
    String getSourceCode() {
        RootNode rootNode = getCode().getRootNode();
        SourceSection sourceSection = rootNode.getSourceSection();
        if (sourceSection != null) {
            return sourceSection.getCharacters().toString();
        }
        return null;
    }

    @ExportMessage
    @SuppressWarnings("static-method")
    boolean hasExecutableName() {
        return true;
    }

    @ExportMessage
    String getExecutableName(@Shared("gil") @Cached GilNode gil,
                    @Cached TruffleString.ToJavaStringNode toJavaStringNode) {
        boolean mustRelease = gil.acquire();
        try {
            return toJavaStringNode.execute(getName());
        } finally {
            gil.release(mustRelease);
        }
    }

    @ExportMessage
    public SourceSection getSourceLocation(
                    @Shared("gil") @Cached GilNode gil) throws UnsupportedMessageException {
        boolean mustRelease = gil.acquire();
        try {
            SourceSection result = getSourceLocationDirect();
            if (result == null) {
                throw UnsupportedMessageException.create();
            } else {
                return result;
            }
        } finally {
            gil.release(mustRelease);
        }
    }

    @TruffleBoundary
    private SourceSection getSourceLocationDirect() {
        RootNode rootNode = code.getRootNode();
        SourceSection result;
        if (rootNode instanceof PRootNode) {
            result = ((PRootNode) rootNode).getSourceSection();
        } else {
            result = getForeignSourceSection(rootNode);
        }
        return result;
    }

    @TruffleBoundary
    private static SourceSection getForeignSourceSection(RootNode rootNode) {
        return rootNode.getSourceSection();
    }

    @ExportMessage
    public boolean hasSourceLocation(
                    @Shared("gil") @Cached GilNode gil) {
        boolean mustRelease = gil.acquire();
        try {
            return getSourceLocationDirect() != null;
        } finally {
            gil.release(mustRelease);
        }
    }
}
