/*
 * Copyright (c) 2021, 2023, Oracle and/or its affiliates. All rights reserved.
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

import static com.oracle.graal.python.builtins.PythonBuiltinClassType.SystemError;
import static com.oracle.graal.python.builtins.modules.cext.PythonCextBuiltins.CApiCallPath.Direct;
import static com.oracle.graal.python.builtins.modules.cext.PythonCextBuiltins.CApiCallPath.Ignored;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.Int;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.PyObject;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.PyObjectTransfer;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.Py_ssize_t;
import static com.oracle.graal.python.nodes.ErrorMessages.BAD_ARG_TO_INTERNAL_FUNC_WAS_S_P;
import static com.oracle.graal.python.nodes.ErrorMessages.EXPECTED_S_NOT_P;
import static com.oracle.graal.python.nodes.ErrorMessages.NATIVE_S_SUBTYPES_NOT_IMPLEMENTED;

import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.modules.BuiltinConstructors.FrozenSetNode;
import com.oracle.graal.python.builtins.modules.BuiltinConstructors.StrNode;
import com.oracle.graal.python.builtins.modules.cext.PythonCextBuiltins.CApiBinaryBuiltinNode;
import com.oracle.graal.python.builtins.modules.cext.PythonCextBuiltins.CApiBuiltin;
import com.oracle.graal.python.builtins.modules.cext.PythonCextBuiltins.CApiUnaryBuiltinNode;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.common.HashingCollectionNodes;
import com.oracle.graal.python.builtins.objects.common.HashingStorage;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodes.HashingStorageGetItem;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodes.HashingStorageGetIterator;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodes.HashingStorageIterator;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodes.HashingStorageIteratorKey;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodes.HashingStorageIteratorKeyHash;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodes.HashingStorageIteratorNext;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodes.HashingStorageLen;
import com.oracle.graal.python.builtins.objects.ints.PInt;
import com.oracle.graal.python.builtins.objects.set.PBaseSet;
import com.oracle.graal.python.builtins.objects.set.PFrozenSet;
import com.oracle.graal.python.builtins.objects.set.PSet;
import com.oracle.graal.python.builtins.objects.set.SetBuiltins.ClearNode;
import com.oracle.graal.python.builtins.objects.set.SetNodes.ConstructSetNode;
import com.oracle.graal.python.builtins.objects.set.SetNodes.DiscardNode;
import com.oracle.graal.python.lib.PyObjectSizeNode;
import com.oracle.graal.python.nodes.classes.IsSubtypeNode;
import com.oracle.graal.python.nodes.object.GetClassNode;
import com.oracle.graal.python.nodes.truffle.PythonTypes;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.dsl.TypeSystemReference;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.profiles.LoopConditionProfile;

public final class PythonCextSetBuiltins {

    @CApiBuiltin(ret = PyObjectTransfer, args = {PyObject}, call = Direct)
    abstract static class PySet_New extends CApiUnaryBuiltinNode {
        @Specialization(guards = {"!isNone(iterable)", "!isNoValue(iterable)"})
        Object newSet(Object iterable,
                        @Cached ConstructSetNode constructSetNode) {
            return constructSetNode.executeWith(null, iterable);
        }

        @Specialization
        Object newSet(@SuppressWarnings("unused") PNone iterable) {
            return factory().createSet();
        }
    }

    @CApiBuiltin(ret = Int, args = {PyObject, PyObject}, call = Direct)
    abstract static class PySet_Contains extends CApiBinaryBuiltinNode {
        @Specialization
        static int contains(PSet anyset, Object item,
                        @Bind("this") Node inliningTarget,
                        @Cached HashingStorageGetItem getItem) {
            HashingStorage storage = anyset.getDictStorage();
            // TODO: FIXME: this might call __hash__ twice
            return PInt.intValue(getItem.hasKey(null, inliningTarget, storage, item));
        }

        @Specialization
        static int contains(PFrozenSet anyset, Object item,
                        @Bind("this") Node inliningTarget,
                        @Cached HashingStorageGetItem getItem) {
            HashingStorage storage = anyset.getDictStorage();
            // TODO: FIXME: this might call __hash__ twice
            return PInt.intValue(getItem.hasKey(null, inliningTarget, storage, item));
        }

        @Fallback
        int fallback(@SuppressWarnings("unused") Object anyset, @SuppressWarnings("unused") Object item) {
            throw raiseFallback(anyset, PythonBuiltinClassType.PSet, PythonBuiltinClassType.PFrozenSet);
        }
    }

    @CApiBuiltin(ret = PyObjectTransfer, args = {PyObject, Py_ssize_t}, call = Ignored)
    @TypeSystemReference(PythonTypes.class)
    abstract static class _PyTruffleSet_NextEntry extends CApiBinaryBuiltinNode {
        @Specialization(guards = "pos < size(inliningTarget, set, sizeNode)", limit = "3")
        Object nextEntry(PSet set, long pos,
                        @Bind("this") Node inliningTarget,
                        @SuppressWarnings("unused") @Cached PyObjectSizeNode sizeNode,
                        @Cached HashingStorageGetIterator getIterator,
                        @Cached HashingStorageIteratorNext itNext,
                        @Cached HashingStorageIteratorKey itKey,
                        @Cached HashingStorageIteratorKeyHash itKeyHash,
                        @Cached LoopConditionProfile loopProfile) {
            return next(inliningTarget, (int) pos, set.getDictStorage(), getIterator, itNext, itKey, itKeyHash, loopProfile);
        }

        @Specialization(guards = "pos < size(inliningTarget, set, sizeNode)", limit = "3")
        Object nextEntry(PFrozenSet set, long pos,
                        @Bind("this") Node inliningTarget,
                        @SuppressWarnings("unused") @Cached PyObjectSizeNode sizeNode,
                        @Cached HashingStorageGetIterator getIterator,
                        @Cached HashingStorageIteratorNext itNext,
                        @Cached HashingStorageIteratorKey itKey,
                        @Cached HashingStorageIteratorKeyHash itKeyHash,
                        @Cached LoopConditionProfile loopProfile) {
            return next(inliningTarget, (int) pos, set.getDictStorage(), getIterator, itNext, itKey, itKeyHash, loopProfile);
        }

        @Specialization(guards = {"isPSet(set) || isPFrozenSet(set)", "pos >= size(inliningTarget, set, sizeNode)"}, limit = "1")
        Object nextEntry(@SuppressWarnings("unused") Object set, @SuppressWarnings("unused") long pos,
                        @Bind("this") Node inliningTarget,
                        @SuppressWarnings("unused") @Cached PyObjectSizeNode sizeNode) {
            return getNativeNull();
        }

        @Specialization(guards = {"!isPSet(anyset)", "!isPFrozenSet(anyset)", "isSetSubtype(inliningTarget, anyset, getClassNode, isSubtypeNode)"})
        Object nextNative(@SuppressWarnings("unused") Object anyset, @SuppressWarnings("unused") Object pos,
                        @SuppressWarnings("unused") @Bind("this") Node inliningTarget,
                        @SuppressWarnings("unused") @Cached GetClassNode getClassNode,
                        @SuppressWarnings("unused") @Cached IsSubtypeNode isSubtypeNode) {
            throw raise(PythonBuiltinClassType.NotImplementedError, NATIVE_S_SUBTYPES_NOT_IMPLEMENTED, "set");
        }

        @Specialization(guards = {"!isPSet(anyset)", "!isPFrozenSet(anyset)", "!isSetSubtype(inliningTarget, anyset, getClassNode, isSubtypeNode)"})
        Object nextEntry(Object anyset, @SuppressWarnings("unused") Object pos,
                        @SuppressWarnings("unused") @Bind("this") Node inliningTarget,
                        @SuppressWarnings("unused") @Cached GetClassNode getClassNode,
                        @SuppressWarnings("unused") @Cached IsSubtypeNode isSubtypeNode,
                        @Cached StrNode strNode) {
            throw raise(SystemError, BAD_ARG_TO_INTERNAL_FUNC_WAS_S_P, strNode.executeWith(anyset), anyset);
        }

        protected boolean isSetSubtype(Node inliningTarget, Object obj, GetClassNode getClassNode, IsSubtypeNode isSubtypeNode) {
            return isSubtypeNode.execute(getClassNode.execute(inliningTarget, obj), PythonBuiltinClassType.PSet) ||
                            isSubtypeNode.execute(getClassNode.execute(inliningTarget, obj), PythonBuiltinClassType.PFrozenSet);
        }

        protected int size(Node inliningTarget, Object set, PyObjectSizeNode sizeNode) {
            return sizeNode.execute(null, inliningTarget, set);
        }

        private Object next(Node inliningTarget, int pos, HashingStorage storage, HashingStorageGetIterator getIterator,
                        HashingStorageIteratorNext itNext, HashingStorageIteratorKey itKey, HashingStorageIteratorKeyHash itKeyHash,
                        LoopConditionProfile loopProfile) {
            HashingStorageIterator it = getIterator.execute(inliningTarget, storage);
            loopProfile.profileCounted(pos);
            for (int i = 0; loopProfile.inject(i <= pos); i++) {
                if (!itNext.execute(inliningTarget, storage, it)) {
                    return getNativeNull();
                }
            }
            Object key = itKey.execute(inliningTarget, storage, it);
            long hash = itKeyHash.execute(inliningTarget, storage, it);
            return factory().createTuple(new Object[]{key, hash});
        }
    }

    @CApiBuiltin(ret = PyObjectTransfer, args = {PyObject}, call = Direct)
    abstract static class PySet_Pop extends CApiUnaryBuiltinNode {
        @Specialization
        static Object pop(PSet set,
                        @Cached com.oracle.graal.python.builtins.objects.set.SetBuiltins.PopNode popNode) {
            return popNode.execute(null, set);
        }

        @Fallback
        Object fallback(Object set) {
            throw raiseFallback(set, PythonBuiltinClassType.PSet);
        }
    }

    @CApiBuiltin(ret = PyObjectTransfer, args = {PyObject}, call = Direct)
    abstract static class PyFrozenSet_New extends CApiUnaryBuiltinNode {
        @Specialization(guards = {"!isNone(iterable)", "!isNoValue(iterable)"})
        Object newFrozenSet(Object iterable,
                        @Cached FrozenSetNode frozenSetNode) {
            return frozenSetNode.execute(null, PythonBuiltinClassType.PFrozenSet, iterable);
        }

        @SuppressWarnings("unused")
        @Specialization
        Object newFrozenSet(PNone iterable) {
            return factory().createFrozenSet(PythonBuiltinClassType.PFrozenSet);
        }
    }

    @CApiBuiltin(ret = Int, args = {PyObject, PyObject}, call = Direct)
    abstract static class PySet_Discard extends CApiBinaryBuiltinNode {

        @Specialization(guards = {"!isNone(s)", "!isNoValue(s)"})
        static Object discard(PSet s, Object key,
                        @Cached DiscardNode discardNode) {
            return discardNode.execute(null, s, key) ? 1 : 0;
        }

        @Fallback
        int fallback(Object set, @SuppressWarnings("unused") Object key) {
            throw raiseFallback(set, PythonBuiltinClassType.PSet);
        }
    }

    @CApiBuiltin(ret = Int, args = {PyObject}, call = Direct)
    abstract static class PySet_Clear extends CApiUnaryBuiltinNode {

        @Specialization(guards = {"!isNone(s)", "!isNoValue(s)"})
        static Object clear(PSet s,
                        @Cached ClearNode clearNode) {
            clearNode.execute(null, s);
            return 0;
        }

        @Fallback
        int fallback(Object set) {
            throw raiseFallback(set, PythonBuiltinClassType.PSet);
        }
    }

    @CApiBuiltin(ret = Int, args = {PyObject, PyObject}, call = Direct)
    abstract static class PySet_Add extends CApiBinaryBuiltinNode {

        @Specialization
        static int add(PBaseSet self, Object o,
                        @Bind("this") Node inliningTarget,
                        @Cached HashingCollectionNodes.SetItemNode setItemNode) {
            setItemNode.execute(null, inliningTarget, self, o, PNone.NO_VALUE);
            return 0;
        }

        @Specialization(guards = "!isAnySet(self)")
        int add(Object self, @SuppressWarnings("unused") Object o) {
            throw raise(SystemError, EXPECTED_S_NOT_P, "a set object", self);
        }
    }

    @CApiBuiltin(ret = Py_ssize_t, args = {PyObject}, call = Direct)
    abstract static class PySet_Size extends CApiUnaryBuiltinNode {
        @Specialization
        static long get(PBaseSet object,
                        @Bind("this") Node inliningTarget,
                        @Cached HashingStorageLen lenNode) {
            return lenNode.execute(inliningTarget, object.getDictStorage());
        }
    }
}
