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

import java.util.ArrayList;
import java.util.List;

import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;

public class Arity {

    @CompilationFinal private String functionName;
    private final int minNumOfArgs;
    private final int maxNumOfArgs;

    private final boolean takesKeywordArg;
    private final boolean takesVarArgs;

    @CompilationFinal(dimensions = 1) private final String[] parameterIds;
    @CompilationFinal(dimensions = 1) private final String[] keywordNames;

    public Arity(String functionName, int minNumOfArgs, int maxNumOfArgs, List<String> parameterIds, List<String> keywordNames) {
        this.functionName = functionName;
        this.minNumOfArgs = minNumOfArgs;
        this.maxNumOfArgs = maxNumOfArgs;
        this.takesKeywordArg = true;
        this.takesVarArgs = false;
        this.parameterIds = parameterIds.toArray(new String[0]);
        this.keywordNames = keywordNames.toArray(new String[0]);
    }

    public Arity(String functionName, int minNumOfArgs, int maxNumOfArgs, boolean takesKeywordArg, boolean takesVarArgs, List<String> parameterIds, List<String> keywordNames) {
        this.functionName = functionName;
        this.minNumOfArgs = minNumOfArgs;
        this.maxNumOfArgs = maxNumOfArgs;
        this.takesKeywordArg = takesKeywordArg;
        this.takesVarArgs = takesVarArgs;
        this.parameterIds = parameterIds.toArray(new String[0]);
        this.keywordNames = keywordNames.toArray(new String[0]);
    }

    public boolean takesVarArgs() {
        return takesVarArgs;
    }

    public boolean takesKeywordArg() {
        return takesKeywordArg;
    }

    public static Arity createOneArgument(String functionName) {
        return new Arity(functionName, 1, 1, new ArrayList<String>(), new ArrayList<String>());
    }

    public int getMinNumOfArgs() {
        return this.minNumOfArgs;
    }

    public int getMaxNumOfArgs() {
        return this.maxNumOfArgs;
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

    public int parametersSize() {
        return parameterIds.length;
    }

    public String[] getKeywordNames() {
        return keywordNames;
    }
}
