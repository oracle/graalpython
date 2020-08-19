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

import static com.oracle.graal.python.builtins.PythonBuiltinClassType.TypeError;

import java.util.Iterator;

import com.oracle.graal.python.builtins.objects.common.EmptyStorage;
import com.oracle.graal.python.builtins.objects.common.HashingStorage.DictEntry;
import com.oracle.graal.python.builtins.objects.common.HashingStorageLibrary;
import com.oracle.graal.python.builtins.objects.common.KeywordsStorage;
import com.oracle.graal.python.builtins.objects.dict.PDict;
import com.oracle.graal.python.builtins.objects.function.PKeyword;
import com.oracle.graal.python.builtins.objects.str.PString;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PGuards;
import com.oracle.graal.python.nodes.PNodeWithContext;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.argument.keywords.ExecuteKeywordStarargsNodeGen.ExpandKeywordStarargsNodeGen;
import com.oracle.graal.python.nodes.expression.ExpressionNode;
import com.oracle.graal.python.runtime.PythonOptions;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.nodes.ControlFlowException;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.profiles.BranchProfile;

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
                        @SuppressWarnings("unused") @CachedLibrary("starargs.getDictStorage()") HashingStorageLibrary lib,
                        @Cached("len(lib, starargs)") int cachedLen,
                        @Cached PRaiseNode raise,
                        @Cached BranchProfile errorProfile) {
            try {
                PKeyword[] keywords = new PKeyword[cachedLen];
                copyKeywords(starargs, cachedLen, keywords);
                return keywords;
            } catch (KeywordNotStringException e) {
                errorProfile.enter();
                throw raise.raise(TypeError, ErrorMessages.MUST_BE_STRINGS, "keywords");
            }
        }

        @TruffleBoundary
        private static void copyKeywords(PDict starargs, int cachedLen, PKeyword[] keywords) throws KeywordNotStringException {
            Iterator<DictEntry> iterator = starargs.entries().iterator();
            for (int i = 0; i < cachedLen; i++) {
                DictEntry entry = iterator.next();
                keywords[i] = new PKeyword(castToString(entry.getKey()), entry.getValue());
            }
        }

        @Specialization(replaces = "doDictCached", limit = "1")
        static PKeyword[] doDict(PDict starargs,
                        @CachedLibrary("starargs.getDictStorage()") HashingStorageLibrary lib,
                        @Cached PRaiseNode raise,
                        @Cached BranchProfile errorProfile) {
            return doDictCached(starargs, lib, len(lib, starargs), raise, errorProfile);
        }

        @Specialization(guards = "!isDict(object)")
        static PKeyword[] doNonMapping(@SuppressWarnings("unused") Object object) {
            return PKeyword.EMPTY_KEYWORDS;
        }

        @Specialization(replaces = {"doKeywordsArray", "doKeywordsStorage", "doEmptyStorage", "doDictCached", "doDict", "doNonMapping"})
        static PKeyword[] doGeneric(Object kwargs,
                        @CachedLibrary(limit = "1") HashingStorageLibrary lib,
                        @Cached PRaiseNode raise,
                        @Cached BranchProfile errorProfile) {
            if (kwargs instanceof PDict) {
                PDict d = (PDict) kwargs;
                if (isKeywordsStorage(d)) {
                    return doKeywordsStorage(d);
                } else if (isEmptyStorage(d)) {
                    return doEmptyStorage(d);
                }
                return doDict(d, lib, raise, errorProfile);
            }
            return PKeyword.EMPTY_KEYWORDS;
        }

        private static String castToString(Object key) throws KeywordNotStringException {
            if (key instanceof String) {
                return (String) key;
            } else if (key instanceof PString) {
                return ((PString) key).getValue();
            }
            throw new KeywordNotStringException();
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

        private static final class KeywordNotStringException extends ControlFlowException {
            private static final long serialVersionUID = 1L;
        }

        public static ExpandKeywordStarargsNode create() {
            return ExpandKeywordStarargsNodeGen.create();
        }

        public static ExpandKeywordStarargsNode getUncached() {
            return ExpandKeywordStarargsNodeGen.getUncached();
        }
    }

}
