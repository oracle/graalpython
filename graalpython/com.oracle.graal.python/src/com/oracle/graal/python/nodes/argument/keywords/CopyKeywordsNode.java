/*
 * Copyright (c) 2020, 2022, Oracle and/or its affiliates. All rights reserved.
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

import com.oracle.graal.python.builtins.objects.common.HashingStorage;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodes.HashingStorageForEach;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodes.HashingStorageForEachCallback;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodes.HashingStorageGetItemWithHash;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodes.HashingStorageIterator;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodes.HashingStorageIteratorKey;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodes.HashingStorageIteratorKeyHash;
import com.oracle.graal.python.builtins.objects.dict.PDict;
import com.oracle.graal.python.builtins.objects.function.PKeyword;
import com.oracle.graal.python.lib.PyObjectGetItem;
import com.oracle.graal.python.lib.PyObjectGetIter;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PNodeWithContext;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.control.GetNextNode;
import com.oracle.graal.python.nodes.object.IsBuiltinClassProfile;
import com.oracle.graal.python.nodes.util.CannotCastException;
import com.oracle.graal.python.nodes.util.CastToTruffleStringNode;
import com.oracle.graal.python.runtime.PythonOptions;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.strings.TruffleString;

@ImportStatic(PythonOptions.class)
@GenerateUncached
public abstract class CopyKeywordsNode extends PNodeWithContext {
    @CompilerDirectives.ValueType
    protected static final class CopyKeywordsState {
        private final HashingStorage hashingStorage;
        private final PKeyword[] keywords;
        private int i = 0;

        public CopyKeywordsState(HashingStorage hashingStorage, PKeyword[] keywords) {
            this.hashingStorage = hashingStorage;
            this.keywords = keywords;
        }

        void addKeyword(TruffleString key, Object value) {
            assert i < keywords.length : "CopyKeywordsNode: current index (over hashingStorage) exceeds keywords array length!";
            keywords[i++] = new PKeyword(key, value);
        }

        public HashingStorage getHashingStorage() {
            return hashingStorage;
        }
    }

    @GenerateUncached
    @ImportStatic(PythonOptions.class)
    abstract static class AddKeywordNode extends HashingStorageForEachCallback<CopyKeywordsState> {
        @Override
        public abstract CopyKeywordsState execute(Frame frame, HashingStorage storage, HashingStorageIterator it, CopyKeywordsState accumulator);

        @Specialization
        public CopyKeywordsState add(HashingStorage storage, HashingStorageIterator it, CopyKeywordsState state,
                        @Cached PRaiseNode raiseNode,
                        @Cached CastToTruffleStringNode castToTruffleStringNode,
                        @Cached HashingStorageIteratorKey itKey,
                        @Cached HashingStorageIteratorKeyHash itKeyHash,
                        @Cached HashingStorageGetItemWithHash getItem) {
            try {
                Object key = itKey.execute(storage, it);
                long hash = itKeyHash.execute(storage, it);
                Object value = getItem.execute(null, storage, key, hash);
                state.addKeyword(castToTruffleStringNode.execute(key), value);
            } catch (CannotCastException e) {
                throw raiseNode.raise(TypeError, ErrorMessages.MUST_BE_STRINGS, "keywords");
            }
            return state;
        }
    }

    public abstract void execute(PDict starargs, PKeyword[] keywords);

    @Specialization(guards = "isBuiltinDict(starargs)")
    void doBuiltinDict(PDict starargs, PKeyword[] keywords,
                    @Cached AddKeywordNode addKeywordNode,
                    @Cached HashingStorageForEach forEachNode) {
        HashingStorage hashingStorage = starargs.getDictStorage();
        forEachNode.execute(null, hashingStorage, addKeywordNode, new CopyKeywordsState(hashingStorage, keywords));
    }

    @Specialization(guards = "!isBuiltinDict(starargs)")
    void doDict(PDict starargs, PKeyword[] keywords,
                    @Cached GetNextNode getNextNode,
                    @Cached CastToTruffleStringNode castToTruffleStringNode,
                    @Cached IsBuiltinClassProfile errorProfile,
                    @Cached PyObjectGetIter getIter,
                    @Cached PyObjectGetItem getItem,
                    @Cached PRaiseNode raiseNode) {
        Object iter = getIter.execute(null, starargs);
        int i = 0;
        while (true) {
            Object key;
            try {
                key = getNextNode.execute(null, iter);
                Object value = getItem.execute(null, starargs, key);
                keywords[i++] = new PKeyword(castToTruffleStringNode.execute(key), value);
            } catch (PException e) {
                e.expectStopIteration(errorProfile);
                break;
            } catch (CannotCastException e) {
                throw raiseNode.raise(TypeError, ErrorMessages.MUST_BE_STRINGS, "keywords");
            }
        }
    }

    public static CopyKeywordsNode create() {
        return CopyKeywordsNodeGen.create();
    }
}
