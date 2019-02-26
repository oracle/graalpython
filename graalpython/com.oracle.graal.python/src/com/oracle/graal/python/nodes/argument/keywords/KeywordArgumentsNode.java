/*
 * Copyright (c) 2017, 2019, Oracle and/or its affiliates.
 * Copyright (c) 2014, Regents of the University of California
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
package com.oracle.graal.python.nodes.argument.keywords;

import com.oracle.graal.python.builtins.objects.function.PKeyword;
import com.oracle.graal.python.nodes.EmptyNode;
import com.oracle.graal.python.nodes.expression.ExpressionNode;
import com.oracle.graal.python.runtime.PythonOptions;
import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.nodes.Node;

@NodeChild(value = "splat", type = ExecuteKeywordStarargsNode.class)
@ImportStatic(PythonOptions.class)
public abstract class KeywordArgumentsNode extends Node {
    @Children private final ExpressionNode[] arguments;
    @Child private CompactKeywordsNode compactNode = CompactKeywordsNodeGen.create();

    public static KeywordArgumentsNode create(ExpressionNode[] arguments, ExpressionNode starargs) {
        return KeywordArgumentsNodeGen.create(arguments, ExecuteKeywordStarargsNodeGen.create(starargs == null ? EmptyNode.create() : starargs));
    }

    KeywordArgumentsNode(ExpressionNode[] arguments) {
        this.arguments = arguments;
    }

    public abstract PKeyword[] execute(VirtualFrame frame);

    @Specialization(guards = {"starargs.length == cachedLen", "starargs.length < 32"}, limit = "getVariableArgumentInlineCacheLimit()")
    @ExplodeLoop
    PKeyword[] makeKeywords(VirtualFrame frame, PKeyword[] starargs,
                    @Cached("starargs.length") int cachedLen) {
        int length = arguments.length;
        CompilerAsserts.partialEvaluationConstant(length);
        PKeyword[] keywords = new PKeyword[length + cachedLen];
        int reshape = 0;
        for (int i = 0; i < length; i++) {
            Object o = arguments[i].execute(frame);
            if (o instanceof PKeyword) {
                keywords[i] = (PKeyword) o;
            } else {
                reshape++;
            }
        }

        for (int i = 0; i < cachedLen; i++) {
            keywords[arguments.length + i] = starargs[i];
        }

        if (reshape > 0) {
            return compactNode.execute(keywords, reshape);
        } else {
            return keywords;
        }
    }

    @Specialization(replaces = "makeKeywords")
    @ExplodeLoop
    PKeyword[] makeKeywordsUncached(VirtualFrame frame, PKeyword[] starargs) {
        int length = arguments.length;
        CompilerAsserts.partialEvaluationConstant(length);
        PKeyword[] keywords = length == 0 ? new PKeyword[starargs.length] : new PKeyword[length + starargs.length];
        int reshape = 0;
        for (int i = 0; i < length; i++) {
            Object o = arguments[i].execute(frame);
            if (o instanceof PKeyword) {
                keywords[i] = (PKeyword) o;
            } else {
                reshape++;
            }
        }

        copyStarargs(keywords, starargs);

        if (reshape > 0) {
            return compactNode.execute(keywords, reshape);
        } else {
            return keywords;
        }
    }

    private void copyStarargs(PKeyword[] keywords, PKeyword[] starargs) {
        // This loop has deliberately been moved out such that it won't be exploded since the length
        // of iterations is not constant.
        for (int i = 0; i < starargs.length; i++) {
            keywords[arguments.length + i] = starargs[i];
        }
    }
}
