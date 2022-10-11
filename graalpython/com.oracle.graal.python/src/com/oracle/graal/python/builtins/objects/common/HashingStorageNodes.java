/*
 * Copyright (c) 2022, 2022, Oracle and/or its affiliates. All rights reserved.
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

import com.oracle.graal.python.builtins.objects.common.HashingStorageNodesFactory.HashingStorageGetItemNodeGen;
import com.oracle.graal.python.lib.PyObjectHashNode;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.strings.TruffleString;

public class HashingStorageNodes {

    public static abstract class HashingStorageGetItemWithHash extends Node {
        public abstract Object execute(HashingStorage self, Object key, long keyHash);
    }

    @GenerateUncached
    public static abstract class HashingStorageGetItem extends Node {
        public static boolean hasKeyUncached(HashingStorage storage, Object key) {
            return HashingStorageGetItemNodeGen.getUncached().execute(null, storage, key) != null;
        }

        public final boolean hasKey(HashingStorage self, TruffleString key) {
            return execute(null, self, key) != null;
        }

        public final boolean hasKey(Frame frame, HashingStorage self, Object key) {
            return execute(frame, self, key) != null;
        }

        public static Object executeUncached(HashingStorage storage, Object key) {
            return HashingStorageGetItemNodeGen.getUncached().execute(null, storage, key);
        }

        public final Object execute(HashingStorage self, TruffleString key) {
            // Shortcut for frequent usage with TruffleString. We do not need a frame in such case,
            // because the string's __hash__ does not need it. Some fast-paths avoid even invoking
            // __hash__ for string keys
            return execute(null, self, key);
        }

        public abstract Object execute(Frame frame, HashingStorage self, Object key);

        @Specialization
        static Object economicMap(Frame frame, EconomicMapStorage self, Object key,
                        @Shared("hash") @Cached PyObjectHashNode hashNode,
                        @Cached ObjectHashMap.GetNode getNode) {
            return getNode.execute(frame, self.map, key, hashNode.execute(frame, key));
        }

        @Specialization
        static Object locals(Frame frame, LocalsStorage self, Object key,
                        @Cached LocalsStorage.GetItemNode getNode) {
            return getNode.execute(frame, self, key);
        }

        @Specialization
        static Object dom(Frame frame, DynamicObjectStorage self, Object key,
                        @Cached DynamicObjectStorage.GetItemNode getNode) {
            return getNode.execute(frame, self, key);
        }

        @Specialization
        static Object empty(Frame frame, @SuppressWarnings("unused") EmptyStorage self, Object key,
                        @Shared("hash") @Cached PyObjectHashNode hashNode) {
            // We must not omit the potentially side-effecting call to __hash__
            hashNode.execute(frame, key);
            return null;
        }

        @Specialization
        static Object keywords(Frame frame, KeywordsStorage self, Object key,
                        @Cached KeywordsStorage.GetItemNode getNode) {
            return getNode.execute(frame, self, key);
        }
    }

    @GenerateUncached
    public static abstract class HashingStorageDelItem extends Node {
// public static HashingStorage executeUncached(HashingStorage storage, Object key) {
// return HashingStorageGetItemNodeGen.getUncached().execute(null, storage, key);
// }

        public final HashingStorage execute(HashingStorage self, TruffleString key) {
            // Shortcut for frequent usage with TruffleString. We do not need a frame in such case,
            // because the string's __hash__ does not need it. Some fast-paths avoid even invoking
            // __hash__ for string keys
            return execute(null, self, key);
        }

        /**
         * TODO: check if we really need the result for anything else but updating the PDict, PSet
         * storage. If not, provide an ecapsulation of that.
         */
        public abstract HashingStorage execute(Frame frame, HashingStorage self, Object key);
    }
}
