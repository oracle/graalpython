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

import static com.oracle.graal.python.runtime.exception.PythonErrorType.TypeError;

import com.oracle.graal.python.builtins.objects.function.Arity;
import com.oracle.graal.python.builtins.objects.function.PArguments;
import com.oracle.graal.python.builtins.objects.function.PKeyword;
import com.oracle.graal.python.builtins.objects.function.PythonCallable;
import com.oracle.graal.python.nodes.PBaseNode;
import com.oracle.graal.python.runtime.PythonOptions;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.nodes.ExplodeLoop;

@ImportStatic(PythonOptions.class)
public abstract class ArityCheckNode extends PBaseNode {
    public static ArityCheckNode create() {
        return ArityCheckNodeGen.create();
    }

    public abstract void execute(Object arityOrCallable, Object[] arguments, PKeyword[] keywords);

    @ExplodeLoop
    private static String[] extractKeywordNames(int length, PKeyword[] keywords) {
        String[] kwNames = new String[length];
        for (int i = 0; i < length; i++) {
            kwNames[i] = keywords[i].getName();
        }
        return kwNames;
    }

    @TruffleBoundary
    private static String[] extractKeywordNames(PKeyword[] keywords) {
        return extractKeywordNames(keywords.length, keywords);
    }

    @Specialization(guards = {"cachedLen == keywords.length", "cachedDeclLen == arity.getKeywordNames().length"}, limit = "getVariableArgumentInlineCacheLimit()")
    void arityCheck(Arity arity, Object[] arguments, PKeyword[] keywords,
                    @Cached("arity.getKeywordNames().length") int cachedDeclLen,
                    @Cached("keywords.length") int cachedLen) {
        String[] kwNames = extractKeywordNames(cachedLen, keywords);
        arityCheck(arity, arguments.length - PArguments.USER_ARGUMENTS_OFFSET, cachedDeclLen, cachedLen, kwNames);
    }

    @Specialization(guards = {"cachedLen == keywords.length", "cachedDeclLen == callee.getArity().getKeywordNames().length"}, limit = "getVariableArgumentInlineCacheLimit()")
    void arityCheckCallable(PythonCallable callee, Object[] arguments, PKeyword[] keywords,
                    @Cached("callee.getArity().getKeywordNames().length") int cachedDeclLen,
                    @Cached("keywords.length") int cachedLen) {
        String[] kwNames = extractKeywordNames(cachedLen, keywords);
        arityCheck(callee.getArity(), arguments.length - PArguments.USER_ARGUMENTS_OFFSET, cachedDeclLen, cachedLen, kwNames);
    }

    @Specialization(replaces = "arityCheck")
    void uncachedCheck(Arity arity, Object[] arguments, PKeyword[] keywords) {
        String[] kwNames = extractKeywordNames(keywords);
        arityCheck(arity, arguments.length - PArguments.USER_ARGUMENTS_OFFSET, kwNames);
    }

    @Specialization(replaces = "arityCheckCallable")
    void uncachedCheckCallable(PythonCallable callee, Object[] arguments, PKeyword[] keywords) {
        String[] kwNames = extractKeywordNames(keywords);
        arityCheck(callee.getArity(), arguments.length - PArguments.USER_ARGUMENTS_OFFSET, kwNames);
    }

    @TruffleBoundary
    private void arityCheck(Arity arity, int numOfArgs, String[] keywords) {
        arityCheck(arity, numOfArgs, arity.getKeywordNames().length, keywords.length, keywords);
    }

    @ExplodeLoop
    private void arityCheck(Arity arity, int numOfArgs, int numOfKeywordsDeclared, int numOfKeywordsGiven, String[] keywords) {
        if (numOfKeywordsGiven == 0) {
            arityCheck(arity, numOfArgs);
        } else if (!arity.takesKeywordArg() && numOfKeywordsGiven > 0) {
            Object[] args = {};
            throw raise(TypeError, arity.getFunctionName() + "() takes no keyword arguments", args);
        } else {
            for (int i = 0; i < numOfKeywordsGiven; i++) {
                String keyword = keywords[i];
                checkKeyword(arity, keyword, numOfKeywordsDeclared);
            }
        }
    }

    @ExplodeLoop
    private void checkKeyword(Arity arity, String keyword, int length) {
        if (arity.takesVarArgs()) {
            return;
        }
        String[] keywordNames = arity.getKeywordNames();
        for (int i = 0; i < length; i++) {
            String name = keywordNames[i];
            if (name.equals(keyword)) {
                return;
            }
        }
        Object[] args = {arity.getFunctionName(), keyword};
        throw raise(TypeError, "%s() got an unexpected keyword argument '%s'", args);
    }

    private void arityCheck(Arity arity, int numOfArgs) {
        String argMessage;
        if (!arity.takesVarArgs() && arity.getMinNumOfArgs() == arity.getMaxNumOfArgs()) {
            if (numOfArgs != arity.getMinNumOfArgs()) {
                if (arity.getMinNumOfArgs() == 0) {
                    argMessage = "no arguments";
                } else if (arity.getMinNumOfArgs() == 1) {
                    argMessage = "exactly one argument";
                } else {
                    argMessage = arity.getMinNumOfArgs() + " arguments";
                }
                Object[] args = {arity.getFunctionName(), argMessage, numOfArgs};
                throw raise(TypeError, "%s() takes %s (%d given)", args);
            }
        } else if (numOfArgs < arity.getMinNumOfArgs()) {
            /**
             * For ex, iter(object[, sentinel]) takes at least 1 argument.
             */
            Object[] args = {arity.getFunctionName(), arity.getMinNumOfArgs(), numOfArgs};
            throw raise(TypeError, "%s() expected at least %d arguments (%d) given", args);
        } else if (!arity.takesVarArgs() && numOfArgs > arity.getMaxNumOfArgs()) {
            /**
             * For ex, complex([real[, imag]]) takes at most 2 arguments.
             */
            argMessage = "at most " + arity.getMaxNumOfArgs() + " arguments";
            Object[] args = {arity.getFunctionName(), argMessage, numOfArgs};
            throw raise(TypeError, "%s() takes %s (%d given)", args);
        }
    }
}
