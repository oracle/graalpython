/*
 * Copyright (c) 2017, 2023, Oracle and/or its affiliates.
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

import static com.oracle.graal.python.util.PythonUtils.TS_ENCODING;

import java.util.Arrays;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.builtins.objects.function.PArguments;
import com.oracle.graal.python.builtins.objects.function.PKeyword;
import com.oracle.graal.python.runtime.PythonOptions;
import com.oracle.graal.python.runtime.object.PythonObjectFactory;
import com.oracle.graal.python.util.PythonUtils;
import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Exclusive;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.nodes.ExplodeLoop.LoopExplosionKind;
import com.oracle.truffle.api.nodes.NodeInfo;
import com.oracle.truffle.api.strings.TruffleString;

@NodeInfo(shortName = "**kwargs")
public abstract class ReadVarKeywordsNode extends ReadArgumentNode {
    @CompilationFinal(dimensions = 1) private final TruffleString[] keywordNames;
    @Child private PythonObjectFactory factory;

    public abstract PKeyword[] executePKeyword(VirtualFrame frame);

    public static ReadVarKeywordsNode create() {
        return ReadVarKeywordsNodeGen.create(PythonUtils.EMPTY_TRUFFLESTRING_ARRAY, false);
    }

    public static ReadVarKeywordsNode create(TruffleString[] keywordNames) {
        return ReadVarKeywordsNodeGen.create(keywordNames, false);
    }

    public static ReadVarKeywordsNode createForUserFunction(TruffleString[] names) {
        return ReadVarKeywordsNodeGen.create(names, true);
    }

    ReadVarKeywordsNode(TruffleString[] keywordNames, boolean doWrap) {
        this.keywordNames = keywordNames;
        this.factory = doWrap ? PythonObjectFactory.create() : null;
    }

    protected int getLimit() {
        return Math.max(keywordNames.length, 5);
    }

    protected int getAndCheckKwargLen(VirtualFrame frame) {
        CompilerAsserts.neverPartOfCompilation("caching the kwarg len should never be compiled");
        int length = getKwargLen(frame);
        if (length >= PythonLanguage.get(this).getEngineOption(PythonOptions.VariableArgumentReadUnrollingLimit)) {
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
                    @SuppressWarnings("unused") @Cached(value = "getAndCheckKwargLen(frame)") int cachedLen) {
        return returnValue(PKeyword.EMPTY_KEYWORDS);
    }

    @Specialization(guards = {"getKwargLen(frame) == cachedLen"}, limit = "getLimit()")
    @ExplodeLoop
    Object extractKwargs(VirtualFrame frame,
                    @Cached(value = "getAndCheckKwargLen(frame)") int cachedLen,
                    @Exclusive @Cached TruffleString.EqualNode equalNode) {
        PKeyword[] keywordArguments = PArguments.getKeywordArguments(frame);
        PKeyword[] remArguments = PKeyword.create(cachedLen);
        CompilerAsserts.compilationConstant(keywordNames.length);
        int i = 0;
        for (int j = 0; j < cachedLen; j++) {
            PKeyword keyword = keywordArguments[j];
            TruffleString kwName = keyword.getName();
            boolean kwFound = searchKeyword(kwName, equalNode);
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
    private boolean searchKeyword(TruffleString kwName, TruffleString.EqualNode equalNode) {
        for (TruffleString name : keywordNames) {
            if (equalNode.execute(kwName, name, TS_ENCODING)) {
                return true;
            }
        }
        return false;
    }

    @Specialization(replaces = "extractKwargs")
    Object extractVariableKwargs(VirtualFrame frame,
                    @Exclusive @Cached TruffleString.EqualNode equalNode) {
        PKeyword[] keywordArguments = PArguments.getKeywordArguments(frame);
        PKeyword[] remArguments = PKeyword.create(keywordArguments.length);
        int i = 0;
        outer: for (PKeyword keyword : keywordArguments) {
            TruffleString kwName = keyword.getName();
            for (TruffleString name : keywordNames) {
                if (equalNode.execute(kwName, name, TS_ENCODING)) {
                    continue outer;
                }
            }
            remArguments[i] = keyword;
            i++;
        }
        return returnValue(Arrays.copyOf(remArguments, i));
    }
}
