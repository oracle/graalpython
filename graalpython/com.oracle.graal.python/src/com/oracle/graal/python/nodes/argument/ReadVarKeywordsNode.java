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
package com.oracle.graal.python.nodes.argument;

import java.util.Arrays;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.builtins.objects.function.PArguments;
import com.oracle.graal.python.builtins.objects.function.PKeyword;
import com.oracle.graal.python.runtime.PythonOptions;
import com.oracle.graal.python.runtime.object.PythonObjectFactory;
import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.nodes.ExplodeLoop.LoopExplosionKind;
import com.oracle.truffle.api.nodes.NodeInfo;

@NodeInfo(shortName = "**kwargs")
public abstract class ReadVarKeywordsNode extends ReadArgumentNode {
    private static final String[] EMPTY = new String[0];
    @CompilationFinal(dimensions = 1) private final String[] keywordNames;
    @Child private PythonObjectFactory factory;

    public abstract PKeyword[] executePKeyword(VirtualFrame frame);

    public static ReadVarKeywordsNode create() {
        return ReadVarKeywordsNodeGen.create(EMPTY, false);
    }

    public static ReadVarKeywordsNode create(String[] keywordNames) {
        return ReadVarKeywordsNodeGen.create(keywordNames, false);
    }

    public static ReadVarKeywordsNode createForUserFunction(String[] names) {
        return ReadVarKeywordsNodeGen.create(names, true);
    }

    ReadVarKeywordsNode(String[] keywordNames, boolean doWrap) {
        this.keywordNames = keywordNames;
        this.factory = doWrap ? PythonObjectFactory.create() : null;
    }

    protected int getLimit() {
        return Math.max(keywordNames.length, 5);
    }

    protected int getAndCheckKwargLen(VirtualFrame frame) {
        CompilerAsserts.neverPartOfCompilation("caching the kwarg len should never be compiled");
        int length = getKwargLen(frame);
        if (length >= PythonLanguage.getCurrent().getEngineOption(PythonOptions.VariableArgumentReadUnrollingLimit)) {
            return -1;
        }
        return length;
    }

    protected static int getKwargLen(VirtualFrame frame) {
        return PArguments.getKeywordArguments(frame).length;
    }

    private Object returnValue(PKeyword[] keywords) {
        if (factory != null) {
            return factory.createDict(keywords);
        } else {
            return keywords;
        }
    }

    @Specialization(guards = {"getKwargLen(frame) == cachedLen", "cachedLen == 0"}, limit = "1")
    Object noKeywordArgs(@SuppressWarnings("unused") VirtualFrame frame,
                    @SuppressWarnings("unused") @Cached("getAndCheckKwargLen(frame)") int cachedLen) {
        return returnValue(PKeyword.EMPTY_KEYWORDS);
    }

    @Specialization(guards = {"getKwargLen(frame) == cachedLen"}, limit = "getLimit()")
    @ExplodeLoop
    Object extractKwargs(VirtualFrame frame,
                    @Cached("getAndCheckKwargLen(frame)") int cachedLen) {
        PKeyword[] keywordArguments = PArguments.getKeywordArguments(frame);
        PKeyword[] remArguments = new PKeyword[cachedLen];
        CompilerAsserts.compilationConstant(keywordNames.length);
        int i = 0;
        for (int j = 0; j < cachedLen; j++) {
            PKeyword keyword = keywordArguments[j];
            String kwName = keyword.getName();
            boolean kwFound = searchKeyword(kwName);
            if (!kwFound) {
                remArguments[i] = keyword;
                i++;
            }
        }
        if (remArguments.length != i) {
            return returnValue(Arrays.copyOf(remArguments, i));
        } else {
            return returnValue(remArguments);
        }
    }

    @ExplodeLoop(kind = LoopExplosionKind.FULL_UNROLL_UNTIL_RETURN)
    private boolean searchKeyword(String kwName) {
        for (String name : keywordNames) {
            if (kwName.equals(name)) {
                return true;
            }
        }
        return false;
    }

    @Specialization(replaces = "extractKwargs")
    Object extractVariableKwargs(VirtualFrame frame) {
        PKeyword[] keywordArguments = PArguments.getKeywordArguments(frame);
        PKeyword[] remArguments = new PKeyword[keywordArguments.length];
        int i = 0;
        outer: for (PKeyword keyword : keywordArguments) {
            String kwName = keyword.getName();
            for (String name : keywordNames) {
                if (kwName.equals(name)) {
                    continue outer;
                }
            }
            remArguments[i] = keyword;
            i++;
        }
        return returnValue(Arrays.copyOf(remArguments, i));
    }
}
