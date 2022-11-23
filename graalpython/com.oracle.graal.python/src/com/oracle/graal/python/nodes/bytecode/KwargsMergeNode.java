/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.nodes.bytecode;

import static com.oracle.graal.python.nodes.SpecialMethodNames.T_KEYS;

import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.objects.common.EmptyStorage;
import com.oracle.graal.python.builtins.objects.common.HashingStorage;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodes.HashingStorageCopy;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodes.HashingStorageGetItem;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodes.HashingStorageGetIterator;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodes.HashingStorageIterator;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodes.HashingStorageIteratorKey;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodes.HashingStorageIteratorNext;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodes.HashingStorageIteratorValue;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodes.HashingStorageSetItem;
import com.oracle.graal.python.builtins.objects.common.SequenceNodes;
import com.oracle.graal.python.builtins.objects.common.SequenceStorageNodes;
import com.oracle.graal.python.builtins.objects.dict.PDict;
import com.oracle.graal.python.builtins.objects.function.BuiltinMethodDescriptors;
import com.oracle.graal.python.builtins.objects.str.StringNodes;
import com.oracle.graal.python.lib.PyObjectCallMethodObjArgs;
import com.oracle.graal.python.lib.PyObjectFunctionStr;
import com.oracle.graal.python.lib.PyObjectGetItem;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PGuards;
import com.oracle.graal.python.nodes.PNodeWithContext;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.argument.keywords.NonMappingException;
import com.oracle.graal.python.nodes.argument.keywords.SameDictKeyException;
import com.oracle.graal.python.nodes.attributes.LookupCallableSlotInMRONode;
import com.oracle.graal.python.nodes.builtins.ListNodes;
import com.oracle.graal.python.nodes.object.GetClassNode;
import com.oracle.graal.python.nodes.object.IsBuiltinClassProfile;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.graal.python.runtime.sequence.PSequence;
import com.oracle.graal.python.runtime.sequence.storage.SequenceStorage;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.profiles.BranchProfile;

@GenerateUncached
public abstract class KwargsMergeNode extends PNodeWithContext {
    public abstract int execute(Frame frame, int stackTop);

    @Specialization
    static int merge(VirtualFrame frame, int initialStackTop,
                    @Cached ConcatDictToStorageNode concatNode,
                    @Cached PRaiseNode raise,
                    @Cached BranchProfile keywordsError,
                    @Cached StringNodes.CastToJavaStringCheckedNode castToStringNode) {
        int stackTop = initialStackTop;
        Object mapping = frame.getObject(stackTop);
        frame.setObject(stackTop--, null);
        PDict dict = (PDict) frame.getObject(stackTop);
        try {
            HashingStorage resultStorage = concatNode.execute(frame, dict.getDictStorage(), mapping);
            dict.setDictStorage(resultStorage);
        } catch (SameDictKeyException e) {
            keywordsError.enter();
            Object functionName = getFunctionName(frame, stackTop);
            String keyName = castToStringNode.cast(e.getKey(), ErrorMessages.KEYWORDS_S_MUST_BE_STRINGS, new Object[]{functionName});
            throw raise.raise(PythonBuiltinClassType.TypeError, ErrorMessages.GOT_MULTIPLE_VALUES_FOR_KEYWORD_ARG, functionName, keyName);
        } catch (NonMappingException e) {
            keywordsError.enter();
            Object functionName = getFunctionName(frame, stackTop);
            throw raise.raise(PythonBuiltinClassType.TypeError, ErrorMessages.ARG_AFTER_MUST_BE_MAPPING, functionName, e.getObject());
        }
        return stackTop;
    }

    private static Object getFunctionName(VirtualFrame frame, int stackTop) {
        /*
         * The instruction is only emitted when generating CALL_FUNCTION_KW. The stack layout at
         * this point is [kwargs dict, varargs, callable].
         */
        Object callable = frame.getObject(stackTop - 2);
        return PyObjectFunctionStr.execute(callable);
    }

    public static KwargsMergeNode create() {
        return KwargsMergeNodeGen.create();
    }

    public static KwargsMergeNode getUncached() {
        return KwargsMergeNodeGen.getUncached();
    }

    @GenerateUncached
    public abstract static class ConcatDictToStorageNode extends PNodeWithContext {
        public abstract HashingStorage execute(Frame frame, HashingStorage dest, Object other);

        @Specialization(guards = "hasBuiltinIter(other, getClassNode, lookupIter)", limit = "1")
        static HashingStorage doBuiltinDictEmptyDest(@SuppressWarnings("unused") EmptyStorage dest, PDict other,
                        @SuppressWarnings("unused") @Shared("getClassNode") @Cached GetClassNode getClassNode,
                        @SuppressWarnings("unused") @Shared("lookupIter") @Cached(parameters = "Iter") LookupCallableSlotInMRONode lookupIter,
                        @Cached HashingStorageCopy copyNode) {
            return copyNode.execute(other.getDictStorage());
        }

        @Specialization(guards = "hasBuiltinIter(other, getClassNode, lookupIter)", limit = "1")
        static HashingStorage doBuiltinDict(VirtualFrame frame, HashingStorage dest, PDict other,
                        @SuppressWarnings("unused") @Shared("getClassNode") @Cached GetClassNode getClassNode,
                        @SuppressWarnings("unused") @Shared("lookupIter") @Cached(parameters = "Iter") LookupCallableSlotInMRONode lookupIter,
                        @Cached HashingStorageGetItem resultGetItem,
                        @Cached HashingStorageSetItem resultSetItem,
                        @Cached HashingStorageGetIterator getIterator,
                        @Cached HashingStorageIteratorNext iterNext,
                        @Cached HashingStorageIteratorKey iterKey,
                        @Cached HashingStorageIteratorValue iterValue,
                        @Shared("sameKeyProfile") @Cached BranchProfile sameKeyProfile) {
            HashingStorage result = dest;
            HashingStorage otherStorage = other.getDictStorage();
            HashingStorageIterator it = getIterator.execute(otherStorage);
            while (iterNext.execute(otherStorage, it)) {
                Object key = iterKey.execute(otherStorage, it);
                Object value = iterValue.execute(otherStorage, it);
                if (resultGetItem.hasKey(frame, result, key)) {
                    sameKeyProfile.enter();
                    throw new SameDictKeyException(key);
                }
                result = resultSetItem.execute(frame, result, key, value);
            }
            return result;
        }

        @Fallback
        static HashingStorage doMapping(VirtualFrame frame, HashingStorage dest, Object other,
                        @Shared("sameKeyProfile") @Cached BranchProfile sameKeyProfile,
                        @Cached PyObjectCallMethodObjArgs callKeys,
                        @Cached IsBuiltinClassProfile errorProfile,
                        @Cached ListNodes.FastConstructListNode asList,
                        @Cached HashingStorageGetItem resultGetItem,
                        @Cached HashingStorageSetItem resultSetItem,
                        @Cached SequenceNodes.GetSequenceStorageNode getSequenceStorage,
                        @Cached SequenceStorageNodes.GetItemScalarNode sequenceGetItem,
                        @Cached PyObjectGetItem getItem) {
            HashingStorage result = dest;
            try {
                PSequence keys = asList.execute(frame, callKeys.execute(frame, other, T_KEYS));
                SequenceStorage keysStorage = getSequenceStorage.execute(keys);
                int keysLen = keysStorage.length();
                for (int i = 0; i < keysLen; i++) {
                    Object key = sequenceGetItem.execute(keysStorage, i);
                    if (resultGetItem.hasKey(frame, result, key)) {
                        sameKeyProfile.enter();
                        throw new SameDictKeyException(key);
                    }
                    Object value = getItem.execute(frame, other, key);
                    result = resultSetItem.execute(frame, result, key, value);
                }
                return result;
            } catch (PException e) {
                e.expectAttributeError(errorProfile);
                throw new NonMappingException(other);
            }
        }

        /* CPython tests that tp_iter is dict_iter */
        protected static boolean hasBuiltinIter(PDict dict, GetClassNode getClassNode, LookupCallableSlotInMRONode lookupIter) {
            return PGuards.isBuiltinDict(dict) || lookupIter.execute(getClassNode.execute(dict)) == BuiltinMethodDescriptors.DICT_ITER;
        }
    }
}
