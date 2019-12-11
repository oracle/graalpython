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

import static com.oracle.graal.python.nodes.BuiltinNames.SELF;

import java.util.List;

import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;

public final class Signature {
    public static final Signature EMPTY = new Signature(-1, false, -1, false, new String[0], new String[0]);

    private final int varArgIndex;
    private final int positionalOnlyArgIndex;
    private final boolean isVarArgsMarker;
    private final boolean takesVarKeywordArgs;

    @CompilationFinal(dimensions = 1) private final String[] positionalParameterNames;
    @CompilationFinal(dimensions = 1) private final String[] keywordOnlyNames;

    public Signature(int positionOnlyArgIndex, boolean takesVarKeywordArgs, int takesVarArgs, boolean varArgsMarker,
                    List<String> parameterIds, List<String> keywordNames) {
        this(positionOnlyArgIndex, takesVarKeywordArgs, takesVarArgs, varArgsMarker,
                        parameterIds != null ? parameterIds.toArray(new String[0]) : null,
                        keywordNames != null ? keywordNames.toArray(new String[0]) : null);
    }

    public Signature(boolean takesVarKeywordArgs, int takesVarArgs, boolean varArgsMarker,
                    List<String> parameterIds, List<String> keywordNames) {
        this(-1, takesVarKeywordArgs, takesVarArgs, varArgsMarker,
                        parameterIds != null ? parameterIds.toArray(new String[0]) : null,
                        keywordNames != null ? keywordNames.toArray(new String[0]) : null);
    }

    public Signature(boolean takesVarKeywordArgs, int takesVarArgs, boolean varArgsMarker,
                    String[] parameterIds, String[] keywordNames) {
        this(-1, takesVarKeywordArgs, takesVarArgs, varArgsMarker, parameterIds, keywordNames);
    }

    public Signature(int positionOnlyArgIndex, boolean takesVarKeywordArgs, int takesVarArgs, boolean varArgsMarker,
                    String[] parameterIds, String[] keywordNames) {
        this.positionalOnlyArgIndex = positionOnlyArgIndex;
        this.takesVarKeywordArgs = takesVarKeywordArgs;
        this.varArgIndex = takesVarArgs;
        this.isVarArgsMarker = varArgsMarker;
        this.positionalParameterNames = (parameterIds != null) ? parameterIds : new String[0];
        this.keywordOnlyNames = (keywordNames != null) ? keywordNames : new String[0];
    }

    public static Signature createOneArgumentWithVarKwArgs() {
        return new Signature(-1, true, -1, false, new String[]{"a"}, null);
    }

    public static Signature createVarArgsAndKwArgsOnly() {
        return new Signature(-1, true, 0, false, (String[]) null, (String[]) null);
    }

    public Signature createWithSelf() {
        String[] parameterIdsWithSelf = new String[getParameterIds().length + 1];
        parameterIdsWithSelf[0] = SELF;
        System.arraycopy(getParameterIds(), 0, parameterIdsWithSelf, 1, parameterIdsWithSelf.length - 1);

        return new Signature(-1, takesVarKeywordArgs, varArgIndex, isVarArgsMarker,
                        parameterIdsWithSelf, keywordOnlyNames);
    }

    public final int getNumOfRequiredKeywords() {
        return keywordOnlyNames.length;
    }

    public final int getMaxNumOfPositionalArgs() {
        return positionalParameterNames.length;
    }

    /**
     * 
     * @return The index to the positional only argument marker ('/'). Which means that all
     *         positional only argument have index smaller then this.
     */
    public final int getPositionalOnlyArgIndex() {
        return positionalOnlyArgIndex;
    }

    public final int getVarargsIdx() {
        return varArgIndex;
    }

    public final boolean takesVarArgs() {
        return varArgIndex != -1;
    }

    public final boolean isVarArgsMarker() {
        return isVarArgsMarker;
    }

    public final boolean takesVarKeywordArgs() {
        return takesVarKeywordArgs;
    }

    public final String[] getParameterIds() {
        return positionalParameterNames;
    }

    public final String[] getKeywordNames() {
        return keywordOnlyNames;
    }

    public final boolean takesKeywordArgs() {
        return keywordOnlyNames.length > 0 || takesVarKeywordArgs;
    }

    public final boolean takesRequiredKeywordArgs() {
        return this.keywordOnlyNames.length > 0;
    }

    public final boolean takesPositionalOnly() {
        return !takesVarArgs() && !takesVarKeywordArgs && !isVarArgsMarker && keywordOnlyNames.length == 0;
    }

    public final boolean takesNoArguments() {
        return positionalParameterNames.length == 0 && takesPositionalOnly();
    }

    public final boolean takesOneArgument() {
        return positionalParameterNames.length == 1 && takesPositionalOnly();
    }
}
