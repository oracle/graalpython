/*
 * Copyright (c) 2017, 2020, Oracle and/or its affiliates.
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

import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.common.HashingStorage;
import com.oracle.graal.python.builtins.objects.common.HashingStorageLibrary;
import com.oracle.graal.python.builtins.objects.dict.PDict;
import com.oracle.graal.python.builtins.objects.function.PKeyword;
import com.oracle.graal.python.nodes.EmptyNode;
import com.oracle.graal.python.nodes.PGuards;
import com.oracle.graal.python.nodes.expression.ExpressionNode;
import com.oracle.graal.python.runtime.PythonOptions;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.profiles.BranchProfile;

@NodeChild(value = "arguments", type = ExecuteKeywordArgumentsNode.class)
@NodeChild(value = "starKeywordArgs", type = ExpressionNode.class)
@ImportStatic({PythonOptions.class, PGuards.class})
public abstract class KeywordArgumentsNode extends Node {

    public static KeywordArgumentsNode create(ExpressionNode[] arguments, ExpressionNode starargs) {
        return KeywordArgumentsNodeGen.create(ExecuteKeywordArgumentsNode.create(arguments), starargs == null ? EmptyNode.create() : starargs);
    }

    public abstract PKeyword[] execute(VirtualFrame frame);

    @Specialization(guards = {
                    "arguments.length == cachedLenArguments", "arguments.length < 32"
    }, limit = "getVariableArgumentInlineCacheLimit()")
    @ExplodeLoop
    PKeyword[] doDictCached(PKeyword[] arguments, PDict starargs,
                    @Cached ExpandKeywordStarargsNode executeStarArgsNode,
                    @Cached KeywordArgumentsInternalNode internalNode,
                    @CachedLibrary(limit = "1") HashingStorageLibrary lib,
                    @Cached("arguments.length") int cachedLenArguments,
                    @Cached BranchProfile sameKeyProfile) {
        HashingStorage dictStorage = starargs.getDictStorage();
        for (int i = 0; i < cachedLenArguments; i++) {
            if (lib.hasKey(dictStorage, arguments[i].getName())) {
                sameKeyProfile.enter();
                throw new SameDictKeyException(arguments[i].getName());
            }
        }
        PKeyword[] starArgs = executeStarArgsNode.execute(starargs);
        return internalNode.execute(arguments, starArgs);
    }

    @Specialization(replaces = "doDictCached")
    PKeyword[] doDict(PKeyword[] arguments, PDict starargs,
                    @Cached ExpandKeywordStarargsNode executeStarArgsNode,
                    @Cached KeywordArgumentsInternalNode internalNode,
                    @CachedLibrary(limit = "1") HashingStorageLibrary lib,
                    @Cached BranchProfile sameKeyProfile) {
        HashingStorage dictStorage = starargs.getDictStorage();
        for (int i = 0; i < arguments.length; i++) {
            if (lib.hasKey(dictStorage, arguments[i].getName())) {
                sameKeyProfile.enter();
                throw new SameDictKeyException(arguments[i].getName());
            }
        }
        PKeyword[] starArgs = executeStarArgsNode.execute(starargs);
        return internalNode.execute(arguments, starArgs);
    }

    @Specialization
    PKeyword[] doNone(PKeyword[] arguments, @SuppressWarnings("unused") PNone starargs) {
        return arguments;
    }

    @Specialization(guards = {"!isDict(starargs)", "!isPNone(starargs)"})
    PKeyword[] doGeneral(@SuppressWarnings("unused") PKeyword[] arguments, Object starargs) {
        throw new NonMappingException(starargs);
    }

    @ImportStatic(PythonOptions.class)
    protected abstract static class KeywordArgumentsInternalNode extends Node {

        public abstract PKeyword[] execute(PKeyword[] arguments, PKeyword[] starargs);

        @Specialization(guards = {
                        "arguments.length == cachedLenArguments", "arguments.length < 32",
                        "starargs.length == cachedLenStarArgs", "starargs.length < 32"
        }, limit = "getVariableArgumentInlineCacheLimit()")
        @ExplodeLoop
        PKeyword[] makeKeywords(PKeyword[] arguments, PKeyword[] starargs,
                        @Cached("arguments.length") int cachedLenArguments,
                        @Cached("starargs.length") int cachedLenStarArgs) {
            PKeyword[] keywords = new PKeyword[cachedLenArguments + cachedLenStarArgs];
            for (int i = 0; i < cachedLenArguments; i++) {
                keywords[i] = arguments[i];
            }

            for (int i = 0; i < cachedLenStarArgs; i++) {
                keywords[cachedLenArguments + i] = starargs[i];
            }

            return keywords;
        }

        @Specialization(replaces = "makeKeywords")
        PKeyword[] makeKeywordsUncached(PKeyword[] arguments, PKeyword[] starargs) {
            int lengthArguments = arguments.length;
            int lengthStarArgs = starargs.length;
            PKeyword[] keywords = lengthArguments == 0 ? new PKeyword[lengthStarArgs] : new PKeyword[lengthArguments + lengthStarArgs];
            for (int i = 0; i < lengthArguments; i++) {
                keywords[i] = arguments[i];
            }

            for (int i = 0; i < lengthStarArgs; i++) {
                keywords[lengthArguments + i] = starargs[i];
            }
            return keywords;
        }

    }
}
