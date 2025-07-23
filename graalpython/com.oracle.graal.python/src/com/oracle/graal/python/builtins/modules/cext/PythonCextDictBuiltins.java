/*
 * Copyright (c) 2021, 2025, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.builtins.modules.cext;

import static com.oracle.graal.python.builtins.PythonBuiltinClassType.AttributeError;
import static com.oracle.graal.python.builtins.PythonBuiltinClassType.SystemError;
import static com.oracle.graal.python.builtins.modules.cext.PythonCextBuiltins.CApiCallPath.Direct;
import static com.oracle.graal.python.builtins.modules.cext.PythonCextBuiltins.CApiCallPath.Ignored;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.Int;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.PY_HASH_T_PTR;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.PY_SSIZE_T_PTR;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.PyObject;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.PyObjectBorrowed;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.PyObjectPtr;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.PyObjectTransfer;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.Py_hash_t;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.Py_ssize_t;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.Void;
import static com.oracle.graal.python.nodes.ErrorMessages.BAD_ARG_TO_INTERNAL_FUNC_WAS_S_P;
import static com.oracle.graal.python.nodes.ErrorMessages.HASH_MISMATCH;
import static com.oracle.graal.python.nodes.ErrorMessages.OBJ_P_HAS_NO_ATTR_S;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T_KEYS;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T_UPDATE;

import java.util.logging.Level;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.modules.cext.PythonCextBuiltins.CApi5BuiltinNode;
import com.oracle.graal.python.builtins.modules.cext.PythonCextBuiltins.CApiBinaryBuiltinNode;
import com.oracle.graal.python.builtins.modules.cext.PythonCextBuiltins.CApiBuiltin;
import com.oracle.graal.python.builtins.modules.cext.PythonCextBuiltins.CApiNullaryBuiltinNode;
import com.oracle.graal.python.builtins.modules.cext.PythonCextBuiltins.CApiQuaternaryBuiltinNode;
import com.oracle.graal.python.builtins.modules.cext.PythonCextBuiltins.CApiTernaryBuiltinNode;
import com.oracle.graal.python.builtins.modules.cext.PythonCextBuiltins.CApiUnaryBuiltinNode;
import com.oracle.graal.python.builtins.modules.cext.PythonCextBuiltins.PromoteBorrowedValue;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.cext.capi.CApiContext;
import com.oracle.graal.python.builtins.objects.cext.capi.transitions.CApiTransitions;
import com.oracle.graal.python.builtins.objects.cext.structs.CStructAccess;
import com.oracle.graal.python.builtins.objects.common.DynamicObjectStorage;
import com.oracle.graal.python.builtins.objects.common.EconomicMapStorage;
import com.oracle.graal.python.builtins.objects.common.HashingCollectionNodes.SetItemNode;
import com.oracle.graal.python.builtins.objects.common.HashingStorage;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodes;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodes.HashingStorageCopy;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodes.HashingStorageForEachCallback;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodes.HashingStorageGetItem;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodes.HashingStorageGetItemWithHash;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodes.HashingStorageGetIterator;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodes.HashingStorageIterator;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodes.HashingStorageIteratorKey;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodes.HashingStorageIteratorKeyHash;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodes.HashingStorageIteratorNext;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodes.HashingStorageIteratorValue;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodes.HashingStorageLen;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodes.HashingStorageSetItem;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodes.HashingStorageSetItemWithHash;
import com.oracle.graal.python.builtins.objects.common.KeywordsStorage;
import com.oracle.graal.python.builtins.objects.common.SequenceStorageNodes.GetItemNode;
import com.oracle.graal.python.builtins.objects.dict.DictBuiltins.ClearNode;
import com.oracle.graal.python.builtins.objects.dict.DictBuiltins.PopNode;
import com.oracle.graal.python.builtins.objects.dict.DictNodes;
import com.oracle.graal.python.builtins.objects.dict.PDict;
import com.oracle.graal.python.builtins.objects.ints.PInt;
import com.oracle.graal.python.builtins.objects.list.PList;
import com.oracle.graal.python.builtins.objects.str.StringBuiltins;
import com.oracle.graal.python.lib.PyDictDelItem;
import com.oracle.graal.python.lib.PyDictSetDefault;
import com.oracle.graal.python.lib.PyObjectGetAttr;
import com.oracle.graal.python.lib.PyObjectHashNode;
import com.oracle.graal.python.lib.PyUnicodeCheckNode;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.builtins.ListNodes.ConstructListNode;
import com.oracle.graal.python.nodes.call.CallNode;
import com.oracle.graal.python.nodes.util.CastToJavaLongExactNode;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.graal.python.runtime.object.PFactory;
import com.oracle.graal.python.runtime.sequence.storage.SequenceStorage;
import com.oracle.graal.python.util.PythonUtils;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Exclusive;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.GenerateCached;
import com.oracle.truffle.api.dsl.GenerateInline;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.profiles.InlinedBranchProfile;
import com.oracle.truffle.api.profiles.InlinedConditionProfile;
import com.oracle.truffle.api.profiles.InlinedLoopConditionProfile;

public final class PythonCextDictBuiltins {

    @CApiBuiltin(ret = PyObjectTransfer, args = {}, call = Direct)
    abstract static class PyDict_New extends CApiNullaryBuiltinNode {

        @Specialization
        static Object run(
                        @Bind PythonLanguage language) {
            return PFactory.createDict(language);
        }
    }

    @CApiBuiltin(ret = Int, args = {PyObject, PY_SSIZE_T_PTR, PyObjectPtr, PyObjectPtr, PY_HASH_T_PTR}, call = Direct)
    abstract static class _PyDict_Next extends CApi5BuiltinNode {

        @Specialization
        static int next(PDict dict, Object posPtr, Object keyPtr, Object valuePtr, Object hashPtr,
                        @Bind Node inliningTarget,
                        @CachedLibrary(limit = "2") InteropLibrary lib,
                        @Cached CStructAccess.ReadI64Node readI64Node,
                        @Cached CStructAccess.WriteLongNode writeLongNode,
                        @Cached CStructAccess.WritePointerNode writePointerNode,
                        @Cached CApiTransitions.PythonToNativeNode toNativeNode,
                        @Cached InlinedBranchProfile needsRewriteProfile,
                        @Cached InlinedBranchProfile economicMapProfile,
                        @Cached HashingStorageLen lenNode,
                        @Cached HashingStorageGetIterator getIterator,
                        @Cached HashingStorageIteratorNext itNext,
                        @Cached HashingStorageIteratorKey itKey,
                        @Cached HashingStorageIteratorValue itValue,
                        @Cached HashingStorageIteratorKeyHash itKeyHash,
                        @Cached PromoteBorrowedValue promoteKeyNode,
                        @Cached PromoteBorrowedValue promoteValueNode,
                        @Cached HashingStorageSetItem setItem) {
            /*
             * We need to promote primitive values and strings to object types for borrowing to work
             * correctly. This is very hard to do mid-iteration, so we do all the promotion for the
             * whole dict at once in the first call (which is required to start with position 0). In
             * order to not violate the ordering, we construct a completely new storage.
             */
            long pos = readI64Node.read(posPtr);
            if (pos == 0) {
                HashingStorage storage = dict.getDictStorage();
                int len = lenNode.execute(inliningTarget, storage);
                if (len > 0) {
                    boolean needsRewrite = false;
                    if (storage instanceof EconomicMapStorage) {
                        economicMapProfile.enter(inliningTarget);
                        HashingStorageIterator it = getIterator.execute(inliningTarget, storage);
                        while (itNext.execute(inliningTarget, storage, it)) {
                            if (promoteKeyNode.execute(inliningTarget, itKey.execute(inliningTarget, storage, it)) != null ||
                                            promoteValueNode.execute(inliningTarget, itValue.execute(inliningTarget, storage, it)) != null) {
                                needsRewrite = true;
                                break;
                            }
                        }
                    } else {
                        /*
                         * Other storages always have string keys or have complex iterators, just
                         * convert them to economic map
                         */
                        needsRewrite = true;
                    }
                    if (needsRewrite) {
                        needsRewriteProfile.enter(inliningTarget);
                        EconomicMapStorage newStorage = EconomicMapStorage.create(len);
                        HashingStorageIterator it = getIterator.execute(inliningTarget, storage);
                        while (itNext.execute(inliningTarget, storage, it)) {
                            Object key = itKey.execute(inliningTarget, storage, it);
                            Object value = itValue.execute(inliningTarget, storage, it);
                            Object promotedKey = promoteKeyNode.execute(inliningTarget, key);
                            if (promotedKey != null) {
                                key = promotedKey;
                            }
                            Object promotedValue = promoteValueNode.execute(inliningTarget, value);
                            if (promotedValue != null) {
                                value = promotedValue;
                            }
                            // promoted key will never have side-effecting __hash__/__eq__
                            setItem.execute(null, inliningTarget, newStorage, key, value);
                        }
                        dict.setDictStorage(newStorage);
                    }
                }
            }

            HashingStorage storage = dict.getDictStorage();
            HashingStorageIterator it = getIterator.execute(inliningTarget, storage);
            /*
             * The iterator index starts from -1, but pos starts from 0, so we subtract 1 here and
             * add it back later when computing new pos.
             */
            it.setState((int) pos - 1);
            boolean hasNext = itNext.execute(inliningTarget, storage, it);
            if (!hasNext) {
                return 0;
            }
            long newPos = it.getState() + 1;
            writeLongNode.write(posPtr, newPos);
            if (!lib.isNull(keyPtr)) {
                Object key = itKey.execute(inliningTarget, storage, it);
                assert promoteKeyNode.execute(inliningTarget, key) == null;
                // Borrowed reference
                writePointerNode.write(keyPtr, toNativeNode.execute(key));
            }
            if (!lib.isNull(valuePtr)) {
                Object value = itValue.execute(inliningTarget, storage, it);
                assert promoteValueNode.execute(inliningTarget, value) == null;
                // Borrowed reference
                writePointerNode.write(valuePtr, toNativeNode.execute(value));
            }
            if (!lib.isNull(hashPtr)) {
                long hash = itKeyHash.execute(null, inliningTarget, storage, it);
                writeLongNode.write(hashPtr, hash);
            }
            return 1;
        }

        @Fallback
        @SuppressWarnings("unused")
        static int run(Object dict, Object posPtr, Object keyPtr, Object valuePtr, Object hashPtr) {
            return 0;
        }
    }

    @CApiBuiltin(ret = PyObjectTransfer, args = {PyObject, PyObject, PyObject}, call = Direct)
    abstract static class _PyDict_Pop extends CApiTernaryBuiltinNode {
        @Specialization
        static Object pop(PDict dict, Object key, Object defaultValue,
                        @Cached PopNode popNode) {
            return popNode.execute(null, dict, key, defaultValue);
        }

        @Fallback
        public Object fallback(Object dict, @SuppressWarnings("unused") Object key, @SuppressWarnings("unused") Object defaultValue) {
            throw raiseFallback(dict, PythonBuiltinClassType.PDict);
        }
    }

    @CApiBuiltin(ret = Py_ssize_t, args = {PyObject}, call = Direct)
    abstract static class PyDict_Size extends CApiUnaryBuiltinNode {
        @Specialization
        static int size(PDict dict,
                        @Bind Node inliningTarget,
                        @Cached HashingStorageLen lenNode) {
            return lenNode.execute(inliningTarget, dict.getDictStorage());
        }

        @Fallback
        public int fallback(Object dict) {
            throw raiseFallback(dict, PythonBuiltinClassType.PDict);
        }
    }

    @CApiBuiltin(ret = PyObjectTransfer, args = {PyObject}, call = Direct)
    abstract static class PyDict_Copy extends CApiUnaryBuiltinNode {
        @Specialization
        static Object copy(PDict dict,
                        @Bind Node inliningTarget,
                        @Cached HashingStorageCopy copyNode,
                        @Bind PythonLanguage language) {
            return PFactory.createDict(language, copyNode.execute(inliningTarget, dict.getDictStorage()));
        }

        @Fallback
        Object fallback(Object dict) {
            throw raiseFallback(dict, PythonBuiltinClassType.PDict);
        }
    }

    @CApiBuiltin(ret = PyObjectBorrowed, args = {PyObject, PyObject}, call = Direct)
    public abstract static class PyDict_GetItem extends CApiBinaryBuiltinNode {

        @Specialization
        static Object getItem(PDict dict, Object key,
                        @Bind Node inliningTarget,
                        @Cached HashingStorageGetItem getItem,
                        @Cached PromoteBorrowedValue promoteNode,
                        @Cached SetItemNode setItemNode,
                        @Cached InlinedBranchProfile noResultProfile) {
            try {
                Object res = getItem.execute(null, inliningTarget, dict.getDictStorage(), key);
                if (res == null) {
                    noResultProfile.enter(inliningTarget);
                    return getNativeNull(inliningTarget);
                }
                Object promotedValue = promoteNode.execute(inliningTarget, res);
                if (promotedValue != null) {
                    setItemNode.execute(null, inliningTarget, dict, key, promotedValue);
                    return promotedValue;
                }
                return res;
            } catch (PException e) {
                // PyDict_GetItem suppresses all exceptions for historical reasons
                return getNativeNull(inliningTarget);
            }
        }

        @Specialization(guards = "!isDict(obj)")
        static Object getItem(Object obj, @SuppressWarnings("unused") Object key,
                        @Bind Node inliningTarget,
                        @Cached StringBuiltins.StrNewNode strNode) {
            return PRaiseNode.raiseStatic(inliningTarget, SystemError, BAD_ARG_TO_INTERNAL_FUNC_WAS_S_P, strNode.executeWith(null, obj), obj);
        }

        protected boolean isDict(Object obj) {
            return obj instanceof PDict;
        }
    }

    @CApiBuiltin(ret = PyObjectBorrowed, args = {PyObject, PyObject}, call = Direct)
    abstract static class PyDict_GetItemWithError extends CApiBinaryBuiltinNode {
        @Specialization
        static Object getItem(PDict dict, Object key,
                        @Bind Node inliningTarget,
                        @Cached HashingStorageGetItem getItem,
                        @Cached PromoteBorrowedValue promoteNode,
                        @Cached SetItemNode setItemNode,
                        @Cached InlinedBranchProfile noResultProfile) {
            Object res = getItem.execute(null, inliningTarget, dict.getDictStorage(), key);
            if (res == null) {
                noResultProfile.enter(inliningTarget);
                return getNativeNull(inliningTarget);
            }
            Object promotedValue = promoteNode.execute(inliningTarget, res);
            if (promotedValue != null) {
                setItemNode.execute(null, inliningTarget, dict, key, promotedValue);
                return promotedValue;
            }
            return res;
        }

        @Fallback
        Object fallback(Object dict, @SuppressWarnings("unused") Object key) {
            throw raiseFallback(dict, PythonBuiltinClassType.PDict);
        }
    }

    @CApiBuiltin(ret = Int, args = {PyObject, PyObject, PyObject}, call = Direct)
    abstract static class PyDict_SetItem extends CApiTernaryBuiltinNode {
        @Specialization
        static int setItem(PDict dict, Object key, Object value,
                        @Bind Node inliningTarget,
                        @Cached SetItemNode setItemNode) {
            setItemNode.execute(null, inliningTarget, dict, key, value);
            return 0;
        }

        @SuppressWarnings("unused")
        @Fallback
        int fallback(Object dict, Object key, Object value) {
            throw raiseFallback(dict, PythonBuiltinClassType.PDict);
        }
    }

    @CApiBuiltin(ret = Int, args = {PyObject, PyObject, PyObject, Py_hash_t}, call = Direct)
    abstract static class _PyDict_SetItem_KnownHash extends CApiQuaternaryBuiltinNode {
        @Specialization
        static int setItem(PDict dict, Object key, Object value, Object givenHash,
                        @Bind Node inliningTarget,
                        @Cached PyObjectHashNode hashNode,
                        @Cached CastToJavaLongExactNode castToLong,
                        @Cached SetItemNode setItemNode,
                        @Cached InlinedBranchProfile wrongHashProfile,
                        @Cached PRaiseNode raiseNode) {
            if (hashNode.execute(null, inliningTarget, key) != castToLong.execute(inliningTarget, givenHash)) {
                wrongHashProfile.enter(inliningTarget);
                throw raiseNode.raise(inliningTarget, PythonBuiltinClassType.AssertionError, HASH_MISMATCH);
            }
            setItemNode.execute(null, inliningTarget, dict, key, value);
            return 0;
        }

        @SuppressWarnings("unused")
        @Fallback
        int fallback(Object dict, Object key, Object value, Object givenHash) {
            throw raiseFallback(dict, PythonBuiltinClassType.PDict);
        }
    }

    @CApiBuiltin(ret = PyObjectBorrowed, args = {PyObject, PyObject, PyObject}, call = Direct)
    abstract static class PyDict_SetDefault extends CApiTernaryBuiltinNode {
        @Specialization
        static Object setItem(PDict dict, Object key, Object value,
                        @Bind Node inliningTarget,
                        @Cached PyDictSetDefault setDefault) {
            return setDefault.execute(null, inliningTarget, dict, key, value);
        }

        @Fallback
        public Object fallback(Object dict, @SuppressWarnings("unused") Object key, @SuppressWarnings("unused") Object value) {
            throw raiseFallback(dict, PythonBuiltinClassType.PDict);
        }
    }

    @CApiBuiltin(ret = Int, args = {PyObject, PyObject}, call = Direct)
    abstract static class PyDict_DelItem extends CApiBinaryBuiltinNode {
        @Specialization
        static int delItem(PDict dict, Object key,
                        @Bind Node inliningTarget,
                        @Cached PyDictDelItem delItemNode) {
            delItemNode.execute(null, inliningTarget, dict, key);
            return 0;
        }

        @Fallback
        public int fallback(Object dict, @SuppressWarnings("unused") Object key) {
            throw raiseFallback(dict, PythonBuiltinClassType.PDict);
        }
    }

    @CApiBuiltin(ret = Int, args = {PyObject, PyObject}, call = Direct)
    abstract static class PyDict_Update extends CApiBinaryBuiltinNode {
        @Specialization
        static int update(PDict dict, Object other,
                        @Cached DictNodes.UpdateNode updateNode) {
            updateNode.execute(null, dict, other);
            return 0;
        }

        @Fallback
        public int fallback(Object dict, @SuppressWarnings("unused") Object other) {
            throw raiseFallback(dict, PythonBuiltinClassType.PDict);
        }
    }

    @CApiBuiltin(ret = Int, args = {PyObject, PyObject}, call = Direct)
    abstract static class PyDict_Contains extends CApiBinaryBuiltinNode {
        @Specialization
        static int contains(PDict dict, Object key,
                        @Bind Node inliningTarget,
                        @Cached HashingStorageGetItem getItem) {
            return PInt.intValue(getItem.hasKey(null, inliningTarget, dict.getDictStorage(), key));
        }

        @Fallback
        public int fallback(Object dict, @SuppressWarnings("unused") Object key) {
            throw raiseFallback(dict, PythonBuiltinClassType.PDict);
        }
    }

    @CApiBuiltin(ret = Void, args = {PyObject}, call = Direct)
    abstract static class PyDict_Clear extends CApiUnaryBuiltinNode {
        @Specialization
        static Object keys(PDict dict,
                        @Cached ClearNode clearNode) {
            return clearNode.execute(null, dict);
        }

        @Fallback
        public Object fallback(Object dict) {
            throw raiseFallback(dict, PythonBuiltinClassType.PDict);
        }
    }

    @CApiBuiltin(ret = PyObjectTransfer, args = {PyObject}, call = Direct)
    abstract static class PyDict_Keys extends CApiUnaryBuiltinNode {
        @Specialization
        static Object keys(PDict dict,
                        @Cached ConstructListNode listNode,
                        @Bind PythonLanguage language) {
            return listNode.execute(null, PFactory.createDictKeysView(language, dict));
        }

        @Fallback
        Object fallback(Object dict) {
            throw raiseFallback(dict, PythonBuiltinClassType.PDict);
        }
    }

    @CApiBuiltin(ret = PyObjectTransfer, args = {PyObject}, call = Direct)
    abstract static class PyDict_Values extends CApiUnaryBuiltinNode {
        @Specialization
        static Object values(PDict dict,
                        @Cached ConstructListNode listNode,
                        @Bind PythonLanguage language) {
            return listNode.execute(null, PFactory.createDictValuesView(language, dict));
        }

        @Fallback
        Object fallback(Object dict) {
            throw raiseFallback(dict, PythonBuiltinClassType.PDict);
        }
    }

    @CApiBuiltin(ret = Int, args = {PyObject, PyObject, Int}, call = Direct)
    abstract static class PyDict_Merge extends CApiTernaryBuiltinNode {

        @Specialization(guards = {"override != 0"})
        static int merge(PDict a, Object b, @SuppressWarnings("unused") int override,
                        @Bind Node inliningTarget,
                        @Exclusive @Cached PyObjectGetAttr getKeys,
                        @Exclusive @Cached PyObjectGetAttr getUpdate,
                        @Shared @Cached CallNode callNode,
                        @Cached PRaiseNode raiseNode) {
            // lookup "keys" to raise the right error:
            if (getKeys.execute(null, inliningTarget, b, T_KEYS) == PNone.NO_VALUE) {
                throw raiseNode.raise(inliningTarget, AttributeError, OBJ_P_HAS_NO_ATTR_S, b, T_KEYS);
            }
            Object updateCallable = getUpdate.execute(null, inliningTarget, a, T_UPDATE);
            callNode.executeWithoutFrame(updateCallable, new Object[]{b});
            return 0;
        }

        @Specialization(guards = "override == 0")
        static int merge(PDict a, PDict b, @SuppressWarnings("unused") int override,
                        @Bind Node inliningTarget,
                        @Cached HashingStorageGetIterator getBIter,
                        @Cached HashingStorageIteratorNext itBNext,
                        @Cached HashingStorageIteratorKey itBKey,
                        @Cached HashingStorageIteratorValue itBValue,
                        @Cached HashingStorageIteratorKeyHash itBKeyHash,
                        @Cached HashingStorageGetItemWithHash getAItem,
                        @Cached HashingStorageSetItemWithHash setAItem,
                        @Exclusive @Cached InlinedLoopConditionProfile loopProfile) {
            HashingStorage bStorage = b.getDictStorage();
            HashingStorageIterator bIt = getBIter.execute(inliningTarget, bStorage);
            HashingStorage aStorage = a.getDictStorage();
            while (loopProfile.profile(inliningTarget, itBNext.execute(inliningTarget, bStorage, bIt))) {
                Object key = itBKey.execute(inliningTarget, bStorage, bIt);
                long hash = itBKeyHash.execute(null, inliningTarget, bStorage, bIt);
                if (getAItem.execute(null, inliningTarget, aStorage, key, hash) != null) {
                    setAItem.execute(null, inliningTarget, aStorage, key, hash, itBValue.execute(inliningTarget, bStorage, bIt));
                }
            }
            return 0;
        }

        // @Exclusive for truffle-interpreted-performance
        @Specialization(guards = {"override == 0", "!isDict(b)"})
        static int merge(PDict a, Object b, @SuppressWarnings("unused") int override,
                        @Bind Node inliningTarget,
                        @Exclusive @Cached PyObjectGetAttr getKeys,
                        @Shared @Cached CallNode callNode,
                        @Cached ConstructListNode listNode,
                        @Cached GetItemNode getKeyNode,
                        @Cached com.oracle.graal.python.lib.PyObjectGetItem getValueNode,
                        @Cached HashingStorageGetItem getItemA,
                        @Cached HashingStorageSetItem setItemA,
                        @Exclusive @Cached InlinedLoopConditionProfile loopProfile,
                        @Cached InlinedBranchProfile noKeyProfile) {
            Object attr = getKeys.execute(null, inliningTarget, a, T_KEYS);
            PList keys = listNode.execute(null, callNode.execute(null, attr));

            SequenceStorage keysStorage = keys.getSequenceStorage();
            HashingStorage aStorage = a.getDictStorage();
            int size = keysStorage.length();
            loopProfile.profileCounted(inliningTarget, size);
            for (int i = 0; loopProfile.inject(inliningTarget, i < size); i++) {
                Object key = getKeyNode.execute(keysStorage, i);
                if (!getItemA.hasKey(null, inliningTarget, aStorage, key)) {
                    noKeyProfile.enter(inliningTarget);
                    Object value = getValueNode.execute(null, inliningTarget, b, key);
                    aStorage = setItemA.execute(null, inliningTarget, aStorage, key, value);
                }
            }
            a.setDictStorage(aStorage);
            return 0;
        }

        @Fallback
        int fallback(Object dict, @SuppressWarnings("unused") Object b, @SuppressWarnings("unused") Object override) {
            throw raiseFallback(dict, PythonBuiltinClassType.PDict);
        }
    }

    @CApiBuiltin(ret = Int, args = {PyObject}, call = Ignored)
    abstract static class GraalPyPrivate_Dict_MaybeUntrack extends CApiUnaryBuiltinNode {

        @Specialization
        static int doPDict(@SuppressWarnings("unused") PDict self,
                        @Bind Node inliningTarget,
                        @Cached HashingStorageNodes.HashingStorageForEach forEachNode,
                        @Cached DictTraverseCallback traverseCallback) {
            HashingStorage dictStorage = self.getDictStorage();
            boolean res = forEachNode.execute(null, inliningTarget, dictStorage, traverseCallback, false);
            if (CApiContext.GC_LOGGER.isLoggable(Level.FINE)) {
                CApiContext.GC_LOGGER.fine(PythonUtils.formatJString("Maybe untrack dict %s: %s", self, res));
            }
            return PInt.intValue(res);
        }
    }

    @GenerateInline
    @GenerateCached(false)
    abstract static class DictTraverseCallback extends HashingStorageForEachCallback<Boolean> {

        @Override
        public abstract Boolean execute(Frame frame, Node inliningTarget, HashingStorage storage, HashingStorageIterator it, Boolean s);

        @Specialization
        static Boolean doGeneric(@SuppressWarnings("unused") VirtualFrame frame, Node inliningTarget, HashingStorage storage, HashingStorageIterator it, Boolean accumulator,
                        @Cached HashingStorageIteratorKey nextKey,
                        @Cached HashingStorageIteratorValue nextValue) {
            if (!accumulator) {
                return false;
            }

            Object key = nextKey.execute(inliningTarget, storage, it);
            if (isTracked(key, null)) {
                return false;
            }

            Object value = nextValue.execute(inliningTarget, storage, it);
            if (isTracked(value, null)) {
                return false;
            }
            return true;
        }

        /*
         * #define _PyObject_GC_MAY_BE_TRACKED(obj) \ (PyObject_IS_GC(obj) && \
         * (!PyTuple_CheckExact(obj) || _PyObject_GC_IS_TRACKED(obj)))
         */
        static boolean isTracked(Object object, CStructAccess.ReadI64Node readI64Node) {
            // TODO(fa): implement properly
            return true;
            // #define _PyObject_GC_IS_TRACKED(o) (_PyGCHead_UNTAG(_Py_AS_GC(o))->_gc_next != 0)
            // long gcNext = readI64Node.read(gcUntagged, CFields.PyGC_Head___gc_prev);
            // if (_PyObject_GC_IS_TRACKED(op))
            // if (gcNext != 0) {
            // return true;
            // }
            // return false;
        }
    }

    @CApiBuiltin(ret = Int, args = {PyObject}, call = Direct)
    abstract static class _PyDict_HasOnlyStringKeys extends CApiUnaryBuiltinNode {

        @Specialization
        static int check(PDict dict,
                        @Bind Node inliningTarget,
                        @Cached InlinedConditionProfile storageProfile,
                        @Cached InlinedLoopConditionProfile loopConditionProfile,
                        @Cached HashingStorageGetIterator getIter,
                        @Cached HashingStorageIteratorNext getIterNext,
                        @Cached HashingStorageIteratorKey getIterKey,
                        @Cached PyUnicodeCheckNode check) {
            HashingStorage storage = dict.getDictStorage();
            // Keywords and dynamic object storages only allow strings
            if (storageProfile.profile(inliningTarget, storage instanceof KeywordsStorage || storage instanceof DynamicObjectStorage)) {
                return 1;
            }
            HashingStorageIterator it = getIter.execute(inliningTarget, storage);
            while (loopConditionProfile.profile(inliningTarget, getIterNext.execute(inliningTarget, storage, it))) {
                Object key = getIterKey.execute(inliningTarget, storage, it);
                if (!check.execute(inliningTarget, key)) {
                    return 0;
                }
            }
            return 1;
        }
    }
}
