/*
 * Copyright (c) 2018, 2020, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.nodes.argument.keywords;

import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.common.EmptyStorage;
import com.oracle.graal.python.builtins.objects.common.HashingStorageLibrary;
import com.oracle.graal.python.builtins.objects.common.KeywordsStorage;
import com.oracle.graal.python.builtins.objects.dict.PDict;
import com.oracle.graal.python.builtins.objects.function.PKeyword;
import com.oracle.graal.python.nodes.PGuards;
import com.oracle.graal.python.nodes.PNodeWithContext;
import com.oracle.graal.python.nodes.argument.keywords.ExecuteKeywordStarargsNodeGen.ExpandKeywordStarargsNodeGen;
import com.oracle.graal.python.nodes.expression.ExpressionNode;
import com.oracle.graal.python.runtime.PythonOptions;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.nodes.Node;

@NodeChild(value = "starargs", type = ExpressionNode.class)
public abstract class ExecuteKeywordStarargsNode extends PNodeWithContext {

    public abstract PKeyword[] execute(VirtualFrame frame);

    @Specialization
    static PKeyword[] doIt(Object starargs,
                    @Cached ExpandKeywordStarargsNode expandKeywordStarargsNode) {
        return expandKeywordStarargsNode.executeWith(starargs);
    }

    @GenerateUncached
    @ImportStatic({PythonOptions.class, PGuards.class})
    public abstract static class ExpandKeywordStarargsNode extends Node {

        public abstract PKeyword[] executeWith(Object starargs);

        @Specialization
        static PKeyword[] doKeywordsArray(PKeyword[] starargs) {
            return starargs;
        }

        @Specialization(guards = "isKeywordsStorage(starargs)")
        static PKeyword[] doKeywordsStorage(PDict starargs) {
            return ((KeywordsStorage) starargs.getDictStorage()).getStore();
        }

        @Specialization(guards = "isEmptyStorage(starargs)")
        static PKeyword[] doEmptyStorage(@SuppressWarnings("unused") PDict starargs) {
            return PKeyword.EMPTY_KEYWORDS;
        }

        @Specialization(guards = {"len(lib, starargs) == cachedLen", "cachedLen < 32"}, limit = "getVariableArgumentInlineCacheLimit()")
        static PKeyword[] doDictCached(PDict starargs,
                        @Cached CopyKeywordsNode copyKeywordsNode,
                        @SuppressWarnings("unused") @CachedLibrary("starargs.getDictStorage()") HashingStorageLibrary lib,
                        @Cached("len(lib, starargs)") int cachedLen) {
            PKeyword[] keywords = new PKeyword[cachedLen];
            copyKeywordsNode.executeWithoutState(starargs, keywords);
            return keywords;
        }

        @Specialization(replaces = "doDictCached", limit = "1")
        static PKeyword[] doDict(PDict starargs,
                        @Cached CopyKeywordsNode copyKeywordsNode,
                        @CachedLibrary("starargs.getDictStorage()") HashingStorageLibrary lib) {
            return doDictCached(starargs, copyKeywordsNode, lib, len(lib, starargs));
        }

        @Specialization(guards = "!isDict(object)")
        static PKeyword[] doNonMapping(@SuppressWarnings("unused") Object object) {
            if (object instanceof PNone) {
                return PKeyword.EMPTY_KEYWORDS;
            }
            throw new NonMappingException(object);
        }

        @Specialization(replaces = {"doKeywordsArray", "doKeywordsStorage", "doEmptyStorage", "doDictCached", "doDict", "doNonMapping"})
        static PKeyword[] doGeneric(Object kwargs,
                        @Cached CopyKeywordsNode copyKeywordsNode,
                        @CachedLibrary(limit = "1") HashingStorageLibrary lib) {
            if (kwargs instanceof PDict) {
                PDict d = (PDict) kwargs;
                if (isKeywordsStorage(d)) {
                    return doKeywordsStorage(d);
                } else if (isEmptyStorage(d)) {
                    return doEmptyStorage(d);
                }
                return doDict(d, copyKeywordsNode, lib);
            }
            return PKeyword.EMPTY_KEYWORDS;
        }

        static boolean isKeywordsStorage(PDict dict) {
            return dict.getDictStorage() instanceof KeywordsStorage;
        }

        static boolean isEmptyStorage(PDict dict) {
            return dict.getDictStorage() instanceof EmptyStorage;
        }

        static int len(HashingStorageLibrary lib, PDict dict) {
            return lib.length(dict.getDictStorage());
        }

        public static ExpandKeywordStarargsNode create() {
            return ExpandKeywordStarargsNodeGen.create();
        }

        public static ExpandKeywordStarargsNode getUncached() {
            return ExpandKeywordStarargsNodeGen.getUncached();
        }
    }

}
