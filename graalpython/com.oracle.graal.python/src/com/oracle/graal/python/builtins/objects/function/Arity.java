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

import static com.oracle.graal.python.nodes.BuiltinNames.SELF;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;

public class Arity {

    private final int minNumOfPositionalArgs;
    private final int maxNumOfPositionalArgs;
    @CompilationFinal private String functionName;
    private final int minNumOfArgs;
    private final int maxNumOfArgs;
    private final int numRequiredKeywordArgs;

    private final boolean takesVarArgs;
    private final boolean varArgsMarker;
    private final boolean takesVarKeywordArgs;

    @CompilationFinal(dimensions = 1) private final String[] parameterIds;
    @CompilationFinal(dimensions = 1) private final KeywordName[] keywordNames;

    public Arity(String functionName, int minNumOfPositionalArgs, int maxNumOfPositionalArgs,
                    boolean takesVarKeywordArgs, boolean takesVarArgs,
                    String[] parameterIds, KeywordName[] keywordNames) {
        this(functionName, minNumOfPositionalArgs, maxNumOfPositionalArgs,
                        takesVarKeywordArgs, takesVarArgs, false,
                        parameterIds, keywordNames);
    }

    public Arity(String functionName, int minNumOfPositionalArgs, int maxNumOfPositionalArgs,
                    boolean takesVarKeywordArgs, boolean takesVarArgs, boolean varArgsMarker,
                    List<String> parameterIds, List<KeywordName> keywordNames) {
        this(functionName, minNumOfPositionalArgs, maxNumOfPositionalArgs, takesVarKeywordArgs, takesVarArgs, varArgsMarker,
                        parameterIds != null ? parameterIds.toArray(new String[0]) : null,
                        keywordNames != null ? keywordNames.toArray(new KeywordName[0]) : null);
    }

    public Arity(String functionName, int minNumOfPositionalArgs, int maxNumOfPositionalArgs,
                    boolean takesVarKeywordArgs, boolean takesVarArgs, boolean varArgsMarker,
                    String[] parameterIds, KeywordName[] keywordNames) {
        this.functionName = functionName;
        this.takesVarKeywordArgs = takesVarKeywordArgs;
        this.takesVarArgs = takesVarArgs;
        this.varArgsMarker = varArgsMarker;
        this.parameterIds = (parameterIds != null) ? parameterIds : new String[0];
        this.keywordNames = (keywordNames != null) ? keywordNames : new KeywordName[0];

        // computed
        this.numRequiredKeywordArgs = computeNumRequiredKeywordArgs();
        this.minNumOfPositionalArgs = computeMinNumPositionalArgs(minNumOfPositionalArgs);
        this.maxNumOfPositionalArgs = computeMaxNumPositionalArgs(maxNumOfPositionalArgs);
        this.minNumOfArgs = computeMinNumArgs();
        this.maxNumOfArgs = computeMaxNumArgs();
    }

    public static Arity createOneArgumentWithVarKwArgs(String functionName) {
        return new Arity(functionName, 1, 1, true, false, null, null);
    }

    public static Arity createVarArgsAndKwArgsOnly(String functionName) {
        return new Arity(functionName, 0, 0, true, true, null, null);
    }

    @TruffleBoundary
    public Arity createWithSelf(String name) {
        String[] parameterIdsWithSelf = new String[getParameterIds().length + 1];
        parameterIdsWithSelf[0] = SELF;
        System.arraycopy(getParameterIds(), 0, parameterIdsWithSelf, 1, parameterIdsWithSelf.length - 1);

        return new Arity(name,
                        minNumOfPositionalArgs + 1,
                        (maxNumOfPositionalArgs == -1) ? -1 : maxNumOfPositionalArgs + 1,
                        takesVarKeywordArgs, takesVarArgs, varArgsMarker,
                        parameterIdsWithSelf, keywordNames);
    }

    private int computeNumRequiredKeywordArgs() {
        int num = 0;
        if (takesVarArgs || varArgsMarker) {
            for (KeywordName kwName : keywordNames) {
                if (kwName.required) {
                    num += 1;
                }
            }
        }
        return num;
    }

    private static int computeMinNumPositionalArgs(int value) {
        return value < 0 ? 0 : value;
    }

    private int computeMaxNumPositionalArgs(int value) {
        if (takesVarArgs) {
            return -1;
        }
        return value < minNumOfPositionalArgs ? minNumOfPositionalArgs : value;
    }

    private int computeMinNumArgs() {
        return minNumOfPositionalArgs + numRequiredKeywordArgs;
    }

    private int computeMaxNumArgs() {
        if (takesVarArgs || takesVarKeywordArgs) {
            return -1;
        }
        return maxNumOfPositionalArgs + numRequiredKeywordArgs;
    }

    public int getNumOfRequiredKeywords() {
        return numRequiredKeywordArgs;
    }

    public int getMinNumOfPositionalArgs() {
        return minNumOfPositionalArgs;
    }

    public int getMaxNumOfPositionalArgs() {
        return maxNumOfPositionalArgs;
    }

    public int getMinNumOfArgs() {
        return minNumOfArgs;
    }

    public int getMaxNumOfArgs() {
        return this.maxNumOfArgs;
    }

    public boolean takesVarArgs() {
        return takesVarArgs;
    }

    public boolean isVarArgsMarker() {
        return varArgsMarker;
    }

    public boolean takesVarKeywordArgs() {
        return takesVarKeywordArgs;
    }

    public String getFunctionName() {
        return this.functionName;
    }

    public String setFunctionName(String name) {
        return this.functionName = name;
    }

    public String[] getParameterIds() {
        return parameterIds;
    }

    public KeywordName[] getKeywordNames() {
        return keywordNames;
    }

    public boolean takesKeywordArgs() {
        return keywordNames.length > 0 || takesVarKeywordArgs;
    }

    public boolean takesRequiredKeywordArgs() {
        return this.numRequiredKeywordArgs > 0;
    }

    public boolean takesPositionalOnly() {
        return !takesVarArgs && !takesVarKeywordArgs && !varArgsMarker && keywordNames.length == 0;
    }

    public boolean takesFixedNumOfPositionalArgs() {
        return takesPositionalOnly() && minNumOfArgs == maxNumOfArgs;
    }

    public boolean takesNoArguments() {
        return takesFixedNumOfPositionalArgs() && minNumOfArgs == 0;
    }

    public boolean takesOneArgument() {
        return takesFixedNumOfPositionalArgs() && minNumOfArgs == 1;
    }

    public int getNumParameterIds() {
        return parameterIds.length;
    }

    public int getNumKeywordNames() {
        return keywordNames.length;
    }

    public static class KeywordName {
        public final String name;
        public final boolean required;

        public KeywordName(String name) {
            this(name, false);
        }

        public KeywordName(String name, boolean required) {
            this.name = name;
            this.required = required;
        }
    }

    @TruffleBoundary
    public Set<String> getKeywordsOnlyArgs() {
        Set<String> kwOnly = new HashSet<>();
        for (KeywordName kw : keywordNames) {
            if (kw.required || takesVarArgs || varArgsMarker) {
                kwOnly.add(kw.name);
            }
        }
        return kwOnly;
    }
}
