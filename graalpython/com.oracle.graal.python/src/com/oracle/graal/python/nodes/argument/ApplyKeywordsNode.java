/*
 * Copyright (c) 2018, 2019, Oracle and/or its affiliates. All rights reserved.
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

import java.util.Arrays;

import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.objects.function.Arity;
import com.oracle.graal.python.builtins.objects.function.PArguments;
import com.oracle.graal.python.builtins.objects.function.PKeyword;
import com.oracle.graal.python.nodes.PNodeWithContext;
import com.oracle.graal.python.nodes.argument.ApplyKeywordsNodeGen.SearchNamedParameterNodeGen;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.nodes.Node;

/**
 * This class is *only* used to apply arguments given as keywords by the caller to positional
 * arguments of the same names. The remaining arguments are left in the PKeywords of the combined
 * arguments.
 *
 * @author tim
 */
abstract class ApplyKeywordsNode extends PNodeWithContext {
    public abstract Object[] execute(Object callee, Arity calleeArity, Object[] arguments, PKeyword[] keywords);

    public static ApplyKeywordsNode create() {
        return ApplyKeywordsNodeGen.create();
    }

    int getUserArgumentLength(Object[] arguments) {
        return PArguments.getUserArgumentLength(arguments);
    }

    SearchNamedParameterNode createSearchNamedParameterNode() {
        return SearchNamedParameterNodeGen.create();
    }

    @Specialization(guards = {"kwLen == keywords.length", "calleeArity == cachedArity"})
    @ExplodeLoop
    Object[] applyCached(Object callee, @SuppressWarnings("unused") Arity calleeArity, Object[] arguments, PKeyword[] keywords,
                    @Cached("keywords.length") int kwLen,
                    @SuppressWarnings("unused") @Cached("calleeArity") Arity cachedArity,
                    @Cached("cachedArity.takesVarKeywordArgs()") boolean takesVarKwds,
                    @Cached(value = "cachedArity.getParameterIds()", dimensions = 1) String[] parameters,
                    @Cached("parameters.length") int positionalParamNum,
                    @Cached(value = "cachedArity.getKeywordNames()", dimensions = 1) String[] kwNames,
                    @Cached("createSearchNamedParameterNode()") SearchNamedParameterNode searchParamNode,
                    @Cached("createSearchNamedParameterNode()") SearchNamedParameterNode searchKwNode) {
        PKeyword[] unusedKeywords = takesVarKwds ? new PKeyword[kwLen] : null;
        // same as below
        int k = 0;
        int additionalKwds = 0;
        String lastWrongKeyword = null;
        for (int i = 0; i < kwLen; i++) {
            PKeyword kwArg = keywords[i];
            String name = kwArg.getName();
            int kwIdx = searchParamNode.execute(parameters, name);
            if (kwIdx == -1) {
                int kwOnlyIdx = searchKwNode.execute(kwNames, name);
                if (kwOnlyIdx != -1) {
                    kwIdx = kwOnlyIdx + positionalParamNum;
                }
            }

            if (kwIdx != -1) {
                if (PArguments.getArgument(arguments, kwIdx) != null) {
                    throw raise(PythonBuiltinClassType.TypeError, "%s() got multiple values for argument '%s'", CreateArgumentsNode.getName(callee), name);
                }
                PArguments.setArgument(arguments, kwIdx, kwArg.getValue());
            } else if (takesVarKwds) {
                unusedKeywords[k++] = kwArg;
            } else {
                additionalKwds++;
                lastWrongKeyword = name;
            }
        }
        storeKeywordsOrRaise(arguments, unusedKeywords, k, additionalKwds, lastWrongKeyword);
        return arguments;
    }

    @Specialization(replaces = "applyCached")
    Object[] applyUncached(Object callee, Arity calleeArity, Object[] arguments, PKeyword[] keywords,
                    @Cached("createSearchNamedParameterNode()") SearchNamedParameterNode searchParamNode,
                    @Cached("createSearchNamedParameterNode()") SearchNamedParameterNode searchKwNode) {
        boolean takesVarKwds = calleeArity.takesVarKeywordArgs();
        String[] parameters = calleeArity.getParameterIds();
        int positionalParamNum = parameters.length;
        String[] kwNames = calleeArity.getKeywordNames();
        PKeyword[] unusedKeywords = new PKeyword[keywords.length];
        // same as above
        int k = 0;
        int additionalKwds = 0;
        String lastWrongKeyword = null;
        for (int i = 0; i < keywords.length; i++) {
            PKeyword kwArg = keywords[i];
            String name = kwArg.getName();
            int kwIdx = searchParamNode.execute(parameters, name);
            if (kwIdx == -1) {
                int kwOnlyIdx = searchKwNode.execute(kwNames, name);
                if (kwOnlyIdx != -1) {
                    kwIdx = kwOnlyIdx + positionalParamNum;
                }
            }

            if (kwIdx != -1) {
                if (PArguments.getArgument(arguments, kwIdx) != null) {
                    throw raise(PythonBuiltinClassType.TypeError, "%s() got multiple values for argument '%s'", CreateArgumentsNode.getName(callee), name);
                }
                PArguments.setArgument(arguments, kwIdx, kwArg.getValue());
            } else if (takesVarKwds) {
                unusedKeywords[k++] = kwArg;
            } else {
                additionalKwds++;
                lastWrongKeyword = name;
            }
        }
        storeKeywordsOrRaise(arguments, unusedKeywords, k, additionalKwds, lastWrongKeyword);
        return arguments;
    }

    private void storeKeywordsOrRaise(Object[] arguments, PKeyword[] unusedKeywords, int unusedKeywordCount, int tooManyKeywords, String lastWrongKeyword) {
        if (tooManyKeywords == 1) {
            throw raise(PythonBuiltinClassType.TypeError, "got an unexpected keyword argument '%s'", lastWrongKeyword);
        } else if (tooManyKeywords > 1) {
            throw raise(PythonBuiltinClassType.TypeError, "got %d unexpected keyword arguments", tooManyKeywords);
        } else if (unusedKeywords != null) {
            PArguments.setKeywordArguments(arguments, Arrays.copyOf(unusedKeywords, unusedKeywordCount));
        }
    }

    protected abstract static class SearchNamedParameterNode extends Node {
        public abstract int execute(String[] parameters, String name);

        @Specialization(guards = "cachedLen == parameters.length")
        @ExplodeLoop
        int cached(String[] parameters, String name,
                        @Cached("parameters.length") int cachedLen) {
            int idx = -1;
            for (int i = 0; i < cachedLen; i++) {
                if (parameters[i].equals(name)) {
                    idx = i;
                }
            }
            return idx;
        }

        @Specialization(replaces = "cached")
        @ExplodeLoop
        int uncached(String[] parameters, String name) {
            for (int i = 0; i < parameters.length; i++) {
                if (parameters[i].equals(name)) {
                    return i;
                }
            }
            return -1;
        }
    }
}
