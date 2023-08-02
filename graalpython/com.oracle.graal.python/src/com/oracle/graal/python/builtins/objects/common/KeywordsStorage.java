/*
 * Copyright (c) 2018, 2023, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.builtins.objects.common;

import static com.oracle.graal.python.util.PythonUtils.TS_ENCODING;

import com.oracle.graal.python.builtins.objects.common.HashingStorageNodes.SpecializedSetStringKey;
import com.oracle.graal.python.builtins.objects.function.PKeyword;
import com.oracle.graal.python.builtins.objects.str.PString;
import com.oracle.graal.python.lib.PyObjectHashNode;
import com.oracle.graal.python.lib.PyObjectRichCompareBool;
import com.oracle.graal.python.nodes.PGuards;
import com.oracle.graal.python.nodes.object.BuiltinClassProfiles.IsBuiltinObjectProfile;
import com.oracle.graal.python.nodes.util.CastToTruffleStringNode;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Exclusive;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.GenerateCached;
import com.oracle.truffle.api.dsl.GenerateInline;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.nodes.ExplodeLoop.LoopExplosionKind;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.strings.TruffleString;

public class KeywordsStorage extends HashingStorage {

    final PKeyword[] keywords;

    protected KeywordsStorage(PKeyword[] keywords) {
        this.keywords = keywords;
    }

    public PKeyword[] getStore() {
        return keywords;
    }

    public int length() {
        return keywords.length;
    }

    @ExplodeLoop(kind = LoopExplosionKind.FULL_UNROLL_UNTIL_RETURN)
    protected int findCachedStringKey(TruffleString key, int len, TruffleString.EqualNode equalNode) {
        for (int i = 0; i < len; i++) {
            if (equalNode.execute(keywords[i].getName(), key, TS_ENCODING)) {
                return i;
            }
        }
        return -1;
    }

    protected int findStringKey(TruffleString key, TruffleString.EqualNode equalNode) {
        return findStringKey(keywords, key, equalNode);
    }

    public static int findStringKey(PKeyword[] keywords, TruffleString key, TruffleString.EqualNode equalNode) {
        for (int i = 0; i < keywords.length; i++) {
            if (equalNode.execute(keywords[i].getName(), key, TS_ENCODING)) {
                return i;
            }
        }
        return -1;
    }

    @ImportStatic(PGuards.class)
    @GenerateUncached
    @GenerateInline
    @GenerateCached(false)
    public abstract static class GetKeywordsStorageItemNode extends Node {
        /**
         * For builtin strings the {@code keyHash} value is ignored and can be garbage. If the
         * {@code keyHash} is equal to {@code -1} it will be computed for non-string keys.
         */
        public abstract Object execute(Frame frame, Node node, KeywordsStorage self, Object key, long hash);

        @Specialization(guards = {"self.length() == cachedLen", "cachedLen < 6"}, limit = "1")
        static Object cached(KeywordsStorage self, TruffleString key, @SuppressWarnings("unused") long hash,
                        @SuppressWarnings("unused") @Exclusive @Cached(value = "self.length()") int cachedLen,
                        @Shared("tsEqual") @Cached(inline = false) TruffleString.EqualNode equalNode) {
            final int idx = self.findCachedStringKey(key, cachedLen, equalNode);
            return idx != -1 ? self.keywords[idx].getValue() : null;
        }

        @Specialization(replaces = "cached")
        static Object string(KeywordsStorage self, TruffleString key, @SuppressWarnings("unused") long hash,
                        @Shared("tsEqual") @Cached(inline = false) TruffleString.EqualNode equalNode) {
            final int idx = self.findStringKey(key, equalNode);
            return idx != -1 ? self.keywords[idx].getValue() : null;
        }

        @Specialization(guards = "isBuiltinString(inliningTarget, key, profile)")
        static Object pstring(Node node, KeywordsStorage self, PString key, @SuppressWarnings("unused") long hash,
                        @SuppressWarnings("unused") @Bind("this") Node inliningTarget,
                        @Cached(inline = false) CastToTruffleStringNode castToTruffleStringNode,
                        @SuppressWarnings("unused") @Shared("builtinProfile") @Cached IsBuiltinObjectProfile profile,
                        @Shared("tsEqual") @Cached(inline = false) TruffleString.EqualNode equalNode) {
            return string(self, castToTruffleStringNode.execute(key), -1, equalNode);
        }

        @Specialization(guards = "!isBuiltinString(inliningTarget, key, profile)")
        static Object notString(Frame frame, Node node, KeywordsStorage self, Object key, long hashIn,
                        @SuppressWarnings("unused") @Bind("this") Node inliningTarget,
                        @SuppressWarnings("unused") @Shared("builtinProfile") @Cached IsBuiltinObjectProfile profile,
                        @Cached(inline = false) PyObjectHashNode hashNode,
                        @Cached(inline = false) PyObjectRichCompareBool.EqNode eqNode) {
            long hash = hashIn == -1 ? hashNode.execute(frame, key) : hashIn;
            for (int i = 0; i < self.keywords.length; i++) {
                TruffleString currentKey = self.keywords[i].getName();
                long keyHash = hashNode.execute(frame, currentKey);
                if (keyHash == hash && eqNode.execute(frame, key, currentKey)) {
                    return self.keywords[i].getValue();
                }
            }
            return null;
        }
    }

    void addAllTo(HashingStorage storage, SpecializedSetStringKey putNode) {
        for (PKeyword entry : keywords) {
            putNode.execute(storage, entry.getName(), entry.getValue());
        }
    }

    public HashingStorage copy() {
        // this storage is unmodifiable; just reuse it
        return this;
    }

    public static KeywordsStorage create(PKeyword[] keywords) {
        return new KeywordsStorage(keywords);
    }
}
