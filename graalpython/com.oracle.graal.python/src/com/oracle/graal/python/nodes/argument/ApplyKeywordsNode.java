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

import java.util.ArrayList;

import com.oracle.graal.python.builtins.objects.function.Arity;
import com.oracle.graal.python.builtins.objects.function.PArguments;
import com.oracle.graal.python.builtins.objects.function.PKeyword;
import com.oracle.graal.python.nodes.PBaseNode;
import com.oracle.graal.python.nodes.argument.ApplyKeywordsNodeGen.SearchNamedParameterNodeGen;
import com.oracle.graal.python.runtime.exception.PythonErrorType;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.nodes.Node;

public abstract class ApplyKeywordsNode extends PBaseNode {
    public abstract Object[] execute(Arity calleeArity, Object[] arguments, PKeyword[] keywords);

    public static ApplyKeywordsNode create() {
        return ApplyKeywordsNodeGen.create();
    }

    int getUserArgumentLength(Object[] arguments) {
        return PArguments.getUserArgumentLength(arguments);
    }

    SearchNamedParameterNode createSearchNamedParameterNode() {
        return SearchNamedParameterNodeGen.create();
    }

    @ExplodeLoop
    private static void copyArgs(Object[] src, Object[] dst, int len) {
        for (int i = 0; i < len; i++) {
            dst[i] = src[i];
        }
    }

    @Specialization(guards = {"kwLen == keywords.length", "argLen == arguments.length", "calleeArity == cachedArity"})
    @ExplodeLoop
    Object[] applyCached(Arity calleeArity, Object[] arguments, PKeyword[] keywords,
                    @Cached("keywords.length") int kwLen,
                    @Cached("arguments.length") int argLen,
                    @Cached("getUserArgumentLength(arguments)") int userArgLen,
                    @SuppressWarnings("unused") @Cached("calleeArity") Arity cachedArity,
                    @Cached(value = "cachedArity.getParameterIds()", dimensions = 1) String[] parameters,
                    @Cached("parameters.length") int paramLen,
                    @Cached("createSearchNamedParameterNode()") SearchNamedParameterNode searchParamNode) {
        Object[] combined = arguments;
        if (paramLen > userArgLen) {
            combined = PArguments.create(paramLen);
            copyArgs(arguments, combined, argLen);
        }

        ArrayList<PKeyword> unusedKeywords = new ArrayList<>();
        for (int i = 0; i < kwLen; i++) {
            PKeyword kwArg = keywords[i];
            int kwIdx = searchParamNode.execute(parameters, kwArg.getName());

            if (kwIdx != -1) {
                if (PArguments.getArgument(combined, kwIdx) != null) {
                    throw raise(PythonErrorType.TypeError, "%s() got multiple values for argument '%s'", calleeArity.getFunctionName(), kwArg.getName());
                }
                PArguments.setArgument(combined, kwIdx, kwArg.getValue());
            } else {
                unusedKeywords.add(kwArg);
            }
        }
        PArguments.setKeywordArguments(combined, unusedKeywords.toArray(new PKeyword[unusedKeywords.size()]));
        return combined;
    }

    @Specialization(replaces = "applyCached")
    Object[] applyUncached(Arity calleeArity, Object[] arguments, PKeyword[] keywords) {
        String[] parameters = calleeArity.getParameterIds();
        Object[] combined = arguments;
        if (parameters.length > PArguments.getUserArgumentLength(arguments)) {
            combined = PArguments.create(parameters.length);
            System.arraycopy(arguments, 0, combined, 0, arguments.length);
        }

        ArrayList<PKeyword> unusedKeywords = new ArrayList<>();
        for (int i = 0; i < keywords.length; i++) {
            PKeyword kwArg = keywords[i];
            int kwIdx = -1;
            for (int j = 0; j < parameters.length; j++) {
                if (parameters[j].equals(kwArg.getName())) {
                    kwIdx = j;
                    break;
                }
            }

            if (kwIdx != -1) {
                if (PArguments.getArgument(combined, kwIdx) != null) {
                    throw raise(PythonErrorType.TypeError, "%s() got multiple values for argument '%s'", calleeArity.getFunctionName(), kwArg.getName());
                }
                PArguments.setArgument(combined, kwIdx, kwArg.getValue());
            } else {
                unusedKeywords.add(kwArg);
            }
        }
        PArguments.setKeywordArguments(combined, unusedKeywords.toArray(new PKeyword[unusedKeywords.size()]));
        return combined;
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
