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
package com.oracle.graal.python.nodes.argument.keywords;

import static com.oracle.graal.python.builtins.PythonBuiltinClassType.TypeError;

import com.oracle.graal.python.builtins.objects.common.EmptyStorage;
import com.oracle.graal.python.builtins.objects.common.HashingStorage;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodes;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodes.HashingStorageIterator;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodes.HashingStorageLen;
import com.oracle.graal.python.builtins.objects.common.KeywordsStorage;
import com.oracle.graal.python.builtins.objects.dict.PDict;
import com.oracle.graal.python.builtins.objects.function.BuiltinMethodDescriptors;
import com.oracle.graal.python.builtins.objects.function.PKeyword;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PGuards;
import com.oracle.graal.python.nodes.PNodeWithContext;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.attributes.LookupCallableSlotInMRONode;
import com.oracle.graal.python.nodes.object.GetClassNode.GetPythonObjectClassNode;
import com.oracle.graal.python.nodes.util.CannotCastException;
import com.oracle.graal.python.nodes.util.CastToTruffleStringNode;
import com.oracle.graal.python.runtime.PythonOptions;
import com.oracle.truffle.api.CompilerDirectives.ValueType;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.GenerateCached;
import com.oracle.truffle.api.dsl.GenerateInline;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.profiles.InlinedIntValueProfile;
import com.oracle.truffle.api.strings.TruffleString;

@GenerateUncached
@GenerateInline
@GenerateCached(false)
public abstract class MappingToKeywordsNode extends PNodeWithContext {

    public abstract PKeyword[] execute(VirtualFrame frame, Node inliningTarget, Object starargs) throws SameDictKeyException, NonMappingException;

    @Specialization(guards = "hasBuiltinIter(inliningTarget, starargs, getClassNode, lookupIter)", limit = "1")
    static PKeyword[] doDict(VirtualFrame frame, Node inliningTarget, PDict starargs,
                    @SuppressWarnings("unused") @Cached GetPythonObjectClassNode getClassNode,
                    @SuppressWarnings("unused") @Cached(parameters = "Iter", inline = false) LookupCallableSlotInMRONode lookupIter,
                    @Shared("convert") @Cached HashingStorageToKeywords convert) {
        return convert.execute(frame, inliningTarget, starargs.getDictStorage());
    }

    @Fallback
    static PKeyword[] doMapping(VirtualFrame frame, Node inliningTarget, Object starargs,
                    @Cached(inline = false) ConcatDictToStorageNode concatDictToStorageNode,
                    @Shared("convert") @Cached HashingStorageToKeywords convert) throws SameDictKeyException, NonMappingException {
        HashingStorage storage = concatDictToStorageNode.execute(frame, EmptyStorage.INSTANCE, starargs);
        return convert.execute(frame, inliningTarget, storage);
    }

    /* CPython tests that tp_iter is dict_iter */
    protected static boolean hasBuiltinIter(Node inliningTarget, PDict dict, GetPythonObjectClassNode getClassNode, LookupCallableSlotInMRONode lookupIter) {
        return PGuards.isBuiltinDict(dict) || lookupIter.execute(getClassNode.execute(inliningTarget, dict)) == BuiltinMethodDescriptors.DICT_ITER;
    }

    @GenerateUncached
    @ImportStatic(PythonOptions.class)
    @GenerateInline(false) // footprint reduction 44 to 26
    abstract static class AddKeywordNode extends HashingStorageNodes.HashingStorageForEachCallback<CopyKeywordsState> {
        @Override
        public abstract CopyKeywordsState execute(Frame frame, Node Node, HashingStorage storage, HashingStorageIterator it, CopyKeywordsState accumulator);

        @Specialization
        public CopyKeywordsState add(@SuppressWarnings("unused") Node Node, HashingStorage storage, HashingStorageNodes.HashingStorageIterator it, CopyKeywordsState state,
                        @Bind("this") Node inliningTarget,
                        @Cached PRaiseNode raiseNode,
                        @Cached CastToTruffleStringNode castToTruffleStringNode,
                        @Cached HashingStorageNodes.HashingStorageIteratorKey itKey,
                        @Cached HashingStorageNodes.HashingStorageIteratorKeyHash itKeyHash,
                        @Cached HashingStorageNodes.HashingStorageGetItemWithHash getItem) {
            Object key = itKey.execute(inliningTarget, storage, it);
            long hash = itKeyHash.execute(inliningTarget, storage, it);
            Object value = getItem.execute(null, inliningTarget, storage, key, hash);
            try {
                state.addKeyword(castToTruffleStringNode.execute(inliningTarget, key), value);
            } catch (CannotCastException e) {
                throw raiseNode.raise(TypeError, ErrorMessages.KEYWORDS_S_MUST_BE_STRINGS);
            }
            return state;
        }
    }

    @ValueType
    protected static final class CopyKeywordsState {
        private final PKeyword[] keywords;
        private int i = 0;

        public CopyKeywordsState(PKeyword[] keywords) {
            this.keywords = keywords;
        }

        void addKeyword(TruffleString key, Object value) {
            assert i < keywords.length : "AddKeywordNode: current index (over hashingStorage) exceeds keywords array length!";
            keywords[i++] = new PKeyword(key, value);
        }
    }

    @GenerateUncached
    @GenerateInline
    @GenerateCached(false)
    abstract static class HashingStorageToKeywords extends PNodeWithContext {
        public abstract PKeyword[] execute(VirtualFrame frame, Node inliningTarget, HashingStorage storage);

        @Specialization
        static PKeyword[] doKeywordsStorage(KeywordsStorage storage) {
            return storage.getStore();
        }

        @Specialization
        static PKeyword[] doEmptyStorage(@SuppressWarnings("unused") EmptyStorage storage) {
            return PKeyword.EMPTY_KEYWORDS;
        }

        @Specialization(guards = {"!isKeywordsStorage(storage)", "!isEmptyStorage(storage)"})
        static PKeyword[] doCached(VirtualFrame frame, Node inliningTarget, HashingStorage storage,
                        @SuppressWarnings("unused") @Cached(parameters = "Iter", inline = false) LookupCallableSlotInMRONode lookupIter,
                        @Cached(inline = false) AddKeywordNode addKeywordNode,
                        @Cached HashingStorageNodes.HashingStorageForEach forEachNode,
                        @SuppressWarnings("unused") @Cached HashingStorageLen lenNode,
                        @Cached InlinedIntValueProfile lenProfile) {
            int profiledLen = lenProfile.profile(inliningTarget, len(inliningTarget, lenNode, storage));
            PKeyword[] keywords = PKeyword.create(profiledLen);
            forEachNode.execute(frame, inliningTarget, storage, addKeywordNode, new CopyKeywordsState(keywords));
            return keywords;
        }

        static boolean isKeywordsStorage(HashingStorage storage) {
            return storage instanceof KeywordsStorage;
        }

        static boolean isEmptyStorage(HashingStorage storage) {
            return storage instanceof EmptyStorage;
        }

        static int len(Node inliningTarget, HashingStorageLen lenNode, HashingStorage storage) {
            return lenNode.execute(inliningTarget, storage);
        }
    }
}
