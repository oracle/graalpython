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
package com.oracle.graal.python.builtins.objects.function;

import static com.oracle.graal.python.nodes.StringLiterals.T_EMPTY_STRING;
import static com.oracle.graal.python.util.PythonUtils.TS_ENCODING;

import java.util.ArrayList;
import java.util.List;

import com.oracle.graal.python.util.PythonUtils;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.strings.TruffleString;

public final class Signature {
    public static final Signature EMPTY = new Signature(-1, false, -1, false, PythonUtils.EMPTY_TRUFFLESTRING_ARRAY, PythonUtils.EMPTY_TRUFFLESTRING_ARRAY);

    private final int varArgIndex;
    private final int positionalOnlyArgIndex;
    private final boolean isVarArgsMarker;
    private final boolean takesVarKeywordArgs;
    private final boolean checkEnclosingType;
    // See javadoc for isHidden
    private final boolean hidden;

    @CompilationFinal(dimensions = 1) private final TruffleString[] positionalParameterNames;
    @CompilationFinal(dimensions = 1) private final TruffleString[] keywordOnlyNames;

    private final TruffleString raiseErrorName;

    public Signature(boolean takesVarKeywordArgs, int takesVarArgs, boolean varArgsMarker,
                    TruffleString[] parameterIds, TruffleString[] keywordNames) {
        this(-1, takesVarKeywordArgs, takesVarArgs, varArgsMarker, parameterIds, keywordNames);
    }

    public Signature(int positionOnlyArgIndex, boolean takesVarKeywordArgs, int takesVarArgs, boolean varArgsMarker,
                    TruffleString[] parameterIds, TruffleString[] keywordNames) {
        this(positionOnlyArgIndex, takesVarKeywordArgs, takesVarArgs, varArgsMarker, parameterIds, keywordNames, false);
    }

    public Signature(int positionOnlyArgIndex, boolean takesVarKeywordArgs, int takesVarArgs, boolean varArgsMarker,
                    TruffleString[] parameterIds, TruffleString[] keywordNames, boolean checkEnclosingType) {
        this(positionOnlyArgIndex, takesVarKeywordArgs, takesVarArgs, varArgsMarker, parameterIds, keywordNames, checkEnclosingType, T_EMPTY_STRING);
    }

    public Signature(int positionOnlyArgIndex, boolean takesVarKeywordArgs, int takesVarArgs, boolean varArgsMarker,
                    TruffleString[] parameterIds, TruffleString[] keywordNames, boolean checkEnclosingType, TruffleString raiseErrorName) {
        this(positionOnlyArgIndex, takesVarKeywordArgs, takesVarArgs, varArgsMarker, parameterIds, keywordNames, checkEnclosingType, raiseErrorName, false);
    }

    public Signature(int positionOnlyArgIndex, boolean takesVarKeywordArgs, int takesVarArgs, boolean varArgsMarker,
                    TruffleString[] parameterIds, TruffleString[] keywordNames, boolean checkEnclosingType, TruffleString raiseErrorName, boolean hidden) {
        this.positionalOnlyArgIndex = positionOnlyArgIndex;
        this.takesVarKeywordArgs = takesVarKeywordArgs;
        this.varArgIndex = takesVarArgs;
        this.isVarArgsMarker = varArgsMarker;
        this.positionalParameterNames = (parameterIds != null) ? parameterIds : PythonUtils.EMPTY_TRUFFLESTRING_ARRAY;
        this.keywordOnlyNames = (keywordNames != null) ? keywordNames : PythonUtils.EMPTY_TRUFFLESTRING_ARRAY;
        this.checkEnclosingType = checkEnclosingType;
        this.raiseErrorName = raiseErrorName;
        this.hidden = hidden;
    }

    public static Signature createVarArgsAndKwArgsOnly() {
        return new Signature(-1, true, 0, false, null, null);
    }

    public int getNumOfRequiredKeywords() {
        return keywordOnlyNames.length;
    }

    public int getMaxNumOfPositionalArgs() {
        return positionalParameterNames.length;
    }

    /**
     *
     * @return The index to the positional only argument marker ('/'). Which means that all
     *         positional only argument have index smaller than this.
     */
    public int getPositionalOnlyArgIndex() {
        return positionalOnlyArgIndex;
    }

    public int getVarargsIdx() {
        return varArgIndex;
    }

    public boolean takesVarArgs() {
        return varArgIndex != -1;
    }

    public boolean isVarArgsMarker() {
        return isVarArgsMarker;
    }

    public boolean takesVarKeywordArgs() {
        return takesVarKeywordArgs;
    }

    public TruffleString[] getParameterIds() {
        return positionalParameterNames;
    }

    public TruffleString[] getKeywordNames() {
        return keywordOnlyNames;
    }

    @TruffleBoundary
    public TruffleString[] getVisibleKeywordNames() {
        /*
         * C slot wrappers (ab)use keyword defaults for storing the function pointers, we need to
         * filter them out. Their names start with a dollar sign.
         */
        List<TruffleString> visibleKeywordNames = new ArrayList<>(keywordOnlyNames.length);
        for (TruffleString k : keywordOnlyNames) {
            if (k.byteLength(TS_ENCODING) > 0 && k.codePointAtByteIndexUncached(0, TS_ENCODING) != '$') {
                visibleKeywordNames.add(k);
            }
        }
        return visibleKeywordNames.toArray(TruffleString[]::new);
    }

    public boolean takesKeywordArgs() {
        return keywordOnlyNames.length > 0 || takesVarKeywordArgs;
    }

    public boolean takesPositionalOnly() {
        return !takesVarArgs() && !takesVarKeywordArgs && !isVarArgsMarker && keywordOnlyNames.length == 0;
    }

    public boolean takesNoArguments() {
        return positionalParameterNames.length == 0 && takesPositionalOnly();
    }

    public boolean checkEnclosingType() {
        return checkEnclosingType;
    }

    public TruffleString getRaiseErrorName() {
        return raiseErrorName;
    }

    /**
     * Hidden signatures won't be shown to python as {@code __signature__} or
     * {@code __text_signature__}. This is done to hide generic C function signatures, because it
     * breaks assumptions of some packages, namely {@code pythran}
     */
    public boolean isHidden() {
        return hidden;
    }
}
