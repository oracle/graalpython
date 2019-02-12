/*
 * Copyright (c) 2018, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 *
 * Subject to the condition set forth below, permission is hereby granted to any
 * person obtaining a copy of this software, associated documentation and/or
 * data (collectively the "Software"), free of charge and under any and all
 * copyright rights in the Software, and any and all patent rights owned or
 * freely licensable by each licensor hereunder covering either (i) the
 * unmodified Software as contributed to or provided by such licensor, or (ii)
 * the Larger Works (as defined below), to deal in both
 *
 * (a) the Software, and
 *
 * (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
 * one is included with the Software each a "Larger Work" to which the Software
 * is contributed by such licensors),
 *
 * without restriction, including without limitation the rights to copy, create
 * derivative works of, display, perform, and distribute the Software and make,
 * use, sell, offer for sale, import, export, have made, and have sold the
 * Software and the Larger Work(s), and to sublicense the foregoing rights on
 * either these or other terms.
 *
 * This license is subject to the following condition:
 *
 * The above copyright notice and either this complete permission notice or at a
 * minimum a reference to the UPL must be included in all copies or substantial
 * portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package com.oracle.graal.python.nodes.argument;

import static com.oracle.graal.python.runtime.exception.PythonErrorType.SystemError;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.TypeError;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import com.oracle.graal.python.builtins.objects.function.Arity;
import com.oracle.graal.python.builtins.objects.function.PArguments;
import com.oracle.graal.python.builtins.objects.function.PBuiltinFunction;
import com.oracle.graal.python.builtins.objects.function.PFunction;
import com.oracle.graal.python.builtins.objects.function.PKeyword;
import com.oracle.graal.python.nodes.PGuards;
import com.oracle.graal.python.nodes.PNodeWithContext;
import com.oracle.graal.python.runtime.PythonOptions;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.profiles.ConditionProfile;
import com.oracle.truffle.api.profiles.ValueProfile;

@ImportStatic({PythonOptions.class, PGuards.class})
public abstract class ArityCheckNode extends PNodeWithContext {
    public static ArityCheckNode create() {
        return ArityCheckNodeGen.create();
    }

    public static ArityCheckNode getUncached() {
        return ArityCheckNodeGen.create();
    }

    public abstract void execute(Object arityOrCallable, Object[] arguments, PKeyword[] keywords);

    private final ConditionProfile nonNameKwParam = ConditionProfile.createBinaryProfile();
    private final ConditionProfile missingPositionalArgs = ConditionProfile.createBinaryProfile();
    private final ConditionProfile lessPositionalArgs = ConditionProfile.createBinaryProfile();
    private final ConditionProfile morePositionalArgs = ConditionProfile.createBinaryProfile();
    private final ConditionProfile missingKeywordArgs = ConditionProfile.createBinaryProfile();
    private final ConditionProfile noKeywordArgs = ConditionProfile.createBinaryProfile();
    private final ConditionProfile unexpectedKeywordArg = ConditionProfile.createBinaryProfile();
    private final ValueProfile calleeTypeProfile = ValueProfile.createClassProfile();

    @ExplodeLoop
    private String[] extractKeywordNames(int length, PKeyword[] keywords) {
        String[] kwNames = new String[length];
        for (int i = 0; i < length; i++) {
            kwNames[i] = keywords[i].getName();
            if (nonNameKwParam.profile(kwNames[i].isEmpty())) {
                throw raise(SystemError, "Empty keyword parameter name");
            }
        }
        return kwNames;
    }

    @TruffleBoundary
    private String[] extractKeywordNames(PKeyword[] keywords) {
        return extractKeywordNames(keywords.length, keywords);
    }

    protected boolean arityCheckWithoutKeywords(Arity arity, Object[] arguments) {
        try {
            arityCheck(arity, arguments, arity.getNumParameterIds(), PArguments.getNumberOfUserArgs(arguments), 0, 0, new String[0]);
        } catch (PException e) {
            return false;
        }
        return true;
    }

    @SuppressWarnings("unused")
    @Specialization(guards = {
                    "arity == cachedArity",
                    "keywords.length == 0",
                    "arguments.length == cachedArgLen",
                    "isValid",
    }, limit = "1")
    void constantArityCheck(Arity arity, Object[] arguments, PKeyword[] keywords,
                    @Cached("arity") Arity cachedArity,
                    @Cached("arguments.length") int cachedArgLen,
                    @Cached("arityCheckWithoutKeywords(arity, arguments)") boolean isValid) {
    }

    @Specialization(guards = {
                    "cachedLen == keywords.length",
                    "cachedNumParamIds == arity.getNumParameterIds()",
                    "cachedDeclLen == arity.getNumKeywordNames()"
    }, limit = "getVariableArgumentInlineCacheLimit()", replaces = "constantArityCheck")
    void arityCheck(Arity arity, Object[] arguments, PKeyword[] keywords,
                    @Cached("arity.getNumParameterIds()") int cachedNumParamIds,
                    @Cached("arity.getNumKeywordNames()") int cachedDeclLen,
                    @Cached("keywords.length") int cachedLen) {
        String[] kwNames = extractKeywordNames(cachedLen, keywords);
        arityCheck(arity, arguments, cachedNumParamIds, PArguments.getNumberOfUserArgs(arguments), cachedDeclLen, cachedLen, kwNames);
    }

    @Specialization(guards = {
                    "isFunction(callee)",
                    "cachedLen == keywords.length",
                    "cachedNumParamIds == getArity(callee).getNumParameterIds()",
                    "cachedDeclLen == getArity(callee).getNumKeywordNames()"
    }, limit = "getVariableArgumentInlineCacheLimit()", replaces = "constantArityCheck")
    void arityCheckCallable(Object callee, Object[] arguments, PKeyword[] keywords,
                    @Cached("getArity(callee).getNumParameterIds()") int cachedNumParamIds,
                    @Cached("getArity(callee).getNumKeywordNames()") int cachedDeclLen,
                    @Cached("keywords.length") int cachedLen) {
        String[] kwNames = extractKeywordNames(cachedLen, keywords);
        Arity arity = getArity(callee);
        arityCheck(arity, arguments, cachedNumParamIds, PArguments.getNumberOfUserArgs(arguments), cachedDeclLen, cachedLen, kwNames);
    }

    @Specialization(replaces = {"arityCheck", "constantArityCheck"})
    void uncachedCheck(Arity arity, Object[] arguments, PKeyword[] keywords) {
        String[] kwNames = extractKeywordNames(keywords);
        arityCheck(arity, arguments, PArguments.getNumberOfUserArgs(arguments), kwNames);
    }

    @Specialization(guards = "isFunction(callee)", replaces = {"arityCheckCallable", "constantArityCheck"})
    void uncachedCheckCallable(Object callee, Object[] arguments, PKeyword[] keywords) {
        String[] kwNames = extractKeywordNames(keywords);
        arityCheck(getArity(callee), arguments, PArguments.getNumberOfUserArgs(arguments), kwNames);
    }

    @TruffleBoundary
    private void arityCheck(Arity arity, Object[] arguments, int numOfArgs, String[] keywords) {
        arityCheck(arity, arguments, arity.getParameterIds().length, numOfArgs, arity.getKeywordNames().length, keywords.length, keywords);
    }

    private void arityCheck(Arity arity, Object[] arguments, int numParameterIds, int numOfArgs, int numOfKeywordsDeclared, int numOfKeywordsGiven, String[] keywords) {
        checkPositional(arity, arguments, numParameterIds, numOfArgs, keywords);
        checkKeywords(arity, numOfKeywordsDeclared, numOfKeywordsGiven, keywords);
    }

    private void checkPositional(Arity arity, Object[] arguments, int numParameterIds, int numOfArgs, String[] keywords) {
        // check missing paramIds
        int cntMissingPositional = countMissingPositionalParamIds(numParameterIds, arguments);

        if (missingPositionalArgs.profile(cntMissingPositional > 0)) {
            throw raise(TypeError, getMissingPositionalArgsErrorMessage(arity, arguments, cntMissingPositional));
        }

        if (lessPositionalArgs.profile(numOfArgs < arity.getMinNumOfPositionalArgs())) {
            throw raise(TypeError, "%s() takes %s %d positional argument%s (%d given)",
                            arity.getFunctionName(),
                            (arity.getMinNumOfPositionalArgs() == arity.getMinNumOfPositionalArgs()) ? "exactly" : "at least",
                            arity.getMinNumOfPositionalArgs(),
                            arity.getMinNumOfPositionalArgs() == 1 ? "" : "s",
                            numOfArgs);
        } else if (morePositionalArgs.profile(arity.getMaxNumOfPositionalArgs() != -1 && numOfArgs > arity.getMaxNumOfPositionalArgs())) {
            throw raise(TypeError, getExtraPositionalArgsErrorMessage(arity, numOfArgs, keywords));
        }
    }

    private void checkKeywords(Arity arity, int numOfKeywordsDeclared, int numOfKeywordsGiven, String[] keywords) {
        int cntGivenRequiredKeywords = countGivenRequiredKeywords(arity, numOfKeywordsDeclared, numOfKeywordsGiven, keywords);

        if (missingKeywordArgs.profile(arity.takesRequiredKeywordArgs() && cntGivenRequiredKeywords < arity.getNumOfRequiredKeywords())) {
            throw raise(TypeError, getMissingRequiredKeywordsErrorMessage(arity, cntGivenRequiredKeywords, keywords));
        } else if (noKeywordArgs.profile(!arity.takesKeywordArgs() && numOfKeywordsGiven > 0)) {
            throw raise(TypeError, "%s() takes no keyword arguments",
                            arity.getFunctionName());
        }
    }

    @ExplodeLoop
    private static int countMissingPositionalParamIds(int numParameterIds, Object[] arguments) {
        int cntMissingPositional = 0;
        for (int i = 0; i < numParameterIds; i++) {
            if (PArguments.getArgument(arguments, i) == null) {
                cntMissingPositional += 1;
            }
        }
        return cntMissingPositional;
    }

    @ExplodeLoop
    private int countGivenRequiredKeywords(Arity arity, int numOfKeywordsDeclared, int numOfKeywordsGiven, String[] keywords) {
        int cntGivenRequiredKeywords = 0;

        for (int i = 0; i < numOfKeywordsGiven; i++) {
            String keyword = keywords[i];
            cntGivenRequiredKeywords += checkKeyword(arity, keyword, numOfKeywordsDeclared);
        }

        return cntGivenRequiredKeywords;
    }

    @ExplodeLoop(kind = ExplodeLoop.LoopExplosionKind.FULL_EXPLODE_UNTIL_RETURN)
    private int checkKeyword(Arity arity, String keyword, int length) {
        Arity.KeywordName[] keywordNames = arity.getKeywordNames();
        for (int i = 0; i < length; i++) {
            Arity.KeywordName kw = keywordNames[i];
            if (kw.name.equals(keyword)) {
                return (kw.required) ? 1 : 0;
            }
        }

        if (unexpectedKeywordArg.profile(!arity.takesVarKeywordArgs())) {
            throw raise(TypeError, "%s() got an unexpected keyword argument '%s'",
                            arity.getFunctionName(),
                            keyword);
        }

        return 0;
    }

    @TruffleBoundary
    private static String getMissingRequiredKeywordsErrorMessage(Arity arity, int cntGivenRequiredKeywords, String[] givenKeywords) {
        int missingRequiredKeywords = arity.getNumOfRequiredKeywords() - cntGivenRequiredKeywords;
        Set<String> givenKeywordsSet = new HashSet<>(Arrays.asList(givenKeywords));

        StringBuilder builder = new StringBuilder();
        String currentName = null;
        Arity.KeywordName[] declaredKeywords = arity.getKeywordNames();
        boolean first = true;
        for (Arity.KeywordName kw : declaredKeywords) {
            if (kw.required && !givenKeywordsSet.contains(kw.name)) {
                if (currentName != null) {
                    builder.append("'").append(currentName).append("'");
                    if (!first) {
                        builder.append(", ");
                    }
                    first = false;
                }
                currentName = kw.name;
            }
        }
        if (missingRequiredKeywords > 1) {
            builder.append(" and ");
        }
        builder.append("'").append(currentName).append("'");

        return String.format("%s() missing %d required keyword-only argument%s: %s",
                        arity.getFunctionName(),
                        missingRequiredKeywords,
                        ((missingRequiredKeywords > 1) ? "s" : ""),
                        builder.toString());
    }

    @TruffleBoundary
    private static String getMissingPositionalArgsErrorMessage(Arity arity, Object[] arguments, int cntMissingPositional) {
        StringBuilder builder = new StringBuilder();
        String currentName = null;
        String[] parameterIds = arity.getParameterIds();
        boolean first = true;
        for (int i = 0; i < parameterIds.length; i++) {
            String paramId = parameterIds[i];
            if (PArguments.getArgument(arguments, i) == null) {
                if (currentName != null) {
                    builder.append("'").append(currentName).append("'");
                    if (!first) {
                        builder.append(", ");
                    }
                    first = false;
                }
                currentName = paramId;
            }
        }
        if (cntMissingPositional > 1) {
            builder.append(" and ");
        }
        builder.append("'").append(currentName).append("'");

        return String.format("%s() missing %d required positional argument%s: %s",
                        arity.getFunctionName(),
                        cntMissingPositional,
                        (cntMissingPositional > 1) ? "s" : "",
                        builder.toString());
    }

    @TruffleBoundary
    private static String getExtraPositionalArgsErrorMessage(Arity arity, int numOfArgs, String[] givenKeywords) {
        String givenCountMessage = (numOfArgs == 1) ? "was" : "were";
        Set<String> keywordsOnly = arity.getKeywordsOnlyArgs();
        int givenKeywordOnlyArgs = 0;
        for (String givenKw : givenKeywords) {
            if (keywordsOnly.contains(givenKw)) {
                givenKeywordOnlyArgs += 1;
            }
        }

        if (givenKeywordOnlyArgs > 0) {
            givenCountMessage = String.format("positional argument%s (and %s keyword-only argument%s) were",
                            (numOfArgs > 1) ? "s" : "",
                            givenKeywordOnlyArgs,
                            (givenKeywordOnlyArgs > 1) ? "s" : "");
        }

        return String.format("%s() takes %s%d positional arguments but %d %s given",
                        arity.getFunctionName(),
                        (arity.getMinNumOfArgs() == arity.getMaxNumOfArgs()) ? "" : "from " + arity.getMinNumOfArgs() + " to ",
                        arity.getMaxNumOfArgs(),
                        numOfArgs,
                        givenCountMessage);
    }

    protected Arity getArity(Object callee) {
        Object profiled = calleeTypeProfile.profile(callee);
        if (profiled instanceof PFunction) {
            return ((PFunction) profiled).getArity();
        } else if (profiled instanceof PBuiltinFunction) {
            return ((PBuiltinFunction) profiled).getArity();
        }
        CompilerDirectives.transferToInterpreter();
        throw new IllegalStateException();
    }
}
