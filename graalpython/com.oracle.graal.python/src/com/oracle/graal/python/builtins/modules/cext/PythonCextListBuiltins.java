/*
 * Copyright (c) 2021, 2026, Oracle and/or its affiliates. All rights reserved.
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

import static com.oracle.graal.python.builtins.PythonBuiltinClassType.IndexError;
import static com.oracle.graal.python.builtins.PythonBuiltinClassType.MemoryError;
import static com.oracle.graal.python.builtins.PythonBuiltinClassType.SystemError;
import static com.oracle.graal.python.builtins.PythonBuiltinClassType.TypeError;
import static com.oracle.graal.python.builtins.modules.cext.PythonCextBuiltins.CApiCallPath.Direct;
import static com.oracle.graal.python.builtins.modules.cext.PythonCextBuiltins.CApiCallPath.Ignored;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.INT64_T;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.Int;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.Pointer;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.PyListObject;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.PyObject;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.PyObjectBorrowed;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.PyObjectTransfer;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.Py_ssize_t;
import static com.oracle.graal.python.nodes.ErrorMessages.BAD_ARG_TO_INTERNAL_FUNC_S;
import static com.oracle.graal.python.runtime.nativeaccess.NativeMemory.writePtr;

import java.util.Arrays;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.modules.cext.PythonCextBuiltins.CApiBinaryBuiltinNode;
import com.oracle.graal.python.builtins.modules.cext.PythonCextBuiltins.CApiBuiltin;
import com.oracle.graal.python.builtins.modules.cext.PythonCextBuiltins.CApiQuaternaryBuiltinNode;
import com.oracle.graal.python.builtins.modules.cext.PythonCextBuiltins.CApiTernaryBuiltinNode;
import com.oracle.graal.python.builtins.modules.cext.PythonCextBuiltins.CApiUnaryBuiltinNode;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.cext.capi.CExtNodes.EnsurePythonObjectNode;
import com.oracle.graal.python.builtins.objects.cext.capi.PySequenceArrayWrapper;
import com.oracle.graal.python.builtins.objects.cext.capi.transitions.CApiTiming;
import com.oracle.graal.python.builtins.objects.cext.capi.transitions.CApiTransitions.NativeToPythonInternalNode;
import com.oracle.graal.python.builtins.objects.cext.capi.transitions.CApiTransitions.PythonToNativeInternalNode;
import com.oracle.graal.python.builtins.objects.common.SequenceStorageNodes;
import com.oracle.graal.python.builtins.objects.common.SequenceStorageNodes.GetItemScalarNode;
import com.oracle.graal.python.builtins.objects.common.SequenceStorageNodes.ListGeneralizationNode;
import com.oracle.graal.python.builtins.objects.common.SequenceStorageNodes.SetItemScalarNode;
import com.oracle.graal.python.builtins.objects.ints.PInt;
import com.oracle.graal.python.builtins.objects.list.ListBuiltins;
import com.oracle.graal.python.builtins.objects.list.ListBuiltins.ListExtendNode;
import com.oracle.graal.python.builtins.objects.list.ListBuiltins.ListInsertNode;
import com.oracle.graal.python.builtins.objects.list.ListBuiltins.ListSortNode;
import com.oracle.graal.python.builtins.objects.list.PList;
import com.oracle.graal.python.lib.PySliceNew;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.builtins.ListNodes.AppendNode;
import com.oracle.graal.python.nodes.builtins.TupleNodes.ConstructTupleNode;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.graal.python.runtime.object.PFactory;
import com.oracle.graal.python.runtime.sequence.storage.EmptySequenceStorage;
import com.oracle.graal.python.runtime.sequence.storage.NativeObjectSequenceStorage;
import com.oracle.graal.python.runtime.sequence.storage.ObjectSequenceStorage;
import com.oracle.graal.python.runtime.sequence.storage.SequenceStorage;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.nodes.Node;

public final class PythonCextListBuiltins {

    private static final CApiTiming TIMING_PYLIST_NEW = CApiTiming.create(false, "PyList_New");

    @CApiBuiltin(ret = PyObjectTransfer, args = {Py_ssize_t}, call = Direct, acquireGil = false)
    static long PyList_New(long size) {
        CApiTiming.enter();
        try {
            if (size < 0) {
                throw PRaiseNode.raiseStatic(null, SystemError, BAD_ARG_TO_INTERNAL_FUNC_S, size);
            }

            PythonLanguage language = PythonLanguage.get(null);
            PList result;
            if (size == 0) {
                result = PFactory.createList(language);
            } else {
                if (!PInt.fitsInInt(size)) {
                    throw PRaiseNode.raiseStatic(null, MemoryError);
                }
                Object[] a = new Object[(int) size];
                Arrays.fill(a, PNone.NO_VALUE);
                result = PFactory.createList(language, a);
            }
            return PythonToNativeInternalNode.executeNewRefUncached(result);
        } finally {
            CApiTiming.exit(TIMING_PYLIST_NEW);
        }
    }

    @CApiBuiltin(ret = PyObjectBorrowed, args = {PyObject, Py_ssize_t}, call = Direct)
    abstract static class PyList_GetItem extends CApiBinaryBuiltinNode {

        @Specialization
        static Object doPList(PList list, long key,
                        @Bind Node inliningTarget,
                        @Bind PythonContext context,
                        @Cached EnsurePythonObjectNode ensureNode,
                        @Cached ListGeneralizationNode generalizationNode,
                        @Cached SetItemScalarNode setItemNode,
                        @Cached GetItemScalarNode getItemNode,
                        @Cached PRaiseNode raiseNode) {
            SequenceStorage sequenceStorage = list.getSequenceStorage();
            // we must do a bounds-check but we must not normalize the index
            if (key < 0 || key >= sequenceStorage.length()) {
                throw raiseNode.raise(inliningTarget, IndexError, ErrorMessages.LIST_INDEX_OUT_OF_RANGE);
            }
            Object result = getItemNode.execute(inliningTarget, sequenceStorage, (int) key);
            Object promotedValue = ensureNode.execute(context, result, false);
            if (promotedValue != result) {
                sequenceStorage = generalizationNode.execute(inliningTarget, sequenceStorage, promotedValue);
                list.setSequenceStorage(sequenceStorage);
                setItemNode.execute(inliningTarget, sequenceStorage, (int) key, promotedValue);
                return promotedValue;
            }
            return result;
        }

        @Fallback
        Object fallback(Object list, @SuppressWarnings("unused") Object pos) {
            throw raiseFallback(list, PythonBuiltinClassType.PList);
        }
    }

    private static final CApiTiming TIMING_PYLIST_GETITEMREF = CApiTiming.create(false, "PyList_GetItemRef");

    @CApiBuiltin(ret = PyObjectTransfer, args = {PyObject, Py_ssize_t}, call = Direct)
    static long PyList_GetItemRef(long opPtr, long key) {
        CApiTiming.enter();
        try {
            Object op = NativeToPythonInternalNode.executeUncached(opPtr, false);
            if (!(op instanceof PList list)) {
                throw PRaiseNode.raiseStatic(null, TypeError, ErrorMessages.EXPECTED_A_LIST);
            }
            SequenceStorage sequenceStorage = list.getSequenceStorage();
            // we must do a bounds-check but we must not normalize the index
            if (key < 0 || key >= sequenceStorage.length()) {
                throw PRaiseNode.raiseStatic(null, IndexError, ErrorMessages.LIST_INDEX_OUT_OF_RANGE);
            }
            Object result = GetItemScalarNode.executeUncached(sequenceStorage, (int) key);
            // See the note in PyDict_GetItemRef
            Object promotedValue = EnsurePythonObjectNode.executeUncached(PythonContext.get(null), result, false);
            if (promotedValue != result) {
                sequenceStorage = ListGeneralizationNode.executeUncached(sequenceStorage, promotedValue);
                list.setSequenceStorage(sequenceStorage);
                SetItemScalarNode.executeUncached(sequenceStorage, (int) key, promotedValue);
            }
            return PythonToNativeInternalNode.executeNewRefUncached(promotedValue);
        } finally {
            CApiTiming.exit(TIMING_PYLIST_GETITEMREF);
        }
    }

    private static final CApiTiming TIMING_PYLIST_APPEND = CApiTiming.create(false, "PyList_Append");

    @CApiBuiltin(ret = Int, args = {PyObject, PyObject}, call = Direct)
    static int PyList_Append(long opPtr, long itemPtr) {
        CApiTiming.enter();
        try {
            Object op = NativeToPythonInternalNode.executeUncached(opPtr, false);
            Object item = NativeToPythonInternalNode.executeUncached(itemPtr, false);
            if (op instanceof PList list && item != PNone.NO_VALUE) {
                AppendNode.appendObjectGeneric(list, item, null, SequenceStorageNodes.AppendNode.getUncached(), AppendNode.getUpdateStoreProfileUncached());
                return 0;
            }
            throw PythonCextBuiltins.badInternalCall("PyList_Append", "op");
        } finally {
            CApiTiming.exit(TIMING_PYLIST_APPEND);
        }
    }

    @CApiBuiltin(ret = PyObjectTransfer, args = {PyObject}, call = Direct)
    abstract static class PyList_AsTuple extends CApiUnaryBuiltinNode {

        @Specialization
        Object append(PList list,
                        @Cached ConstructTupleNode constructTupleNode) {
            return constructTupleNode.execute(null, list);
        }

        @Fallback
        Object fallback(Object list) {
            throw raiseFallback(list, PythonBuiltinClassType.PList);
        }
    }

    @CApiBuiltin(ret = PyObjectTransfer, args = {PyObject, Py_ssize_t, Py_ssize_t}, call = Direct)
    abstract static class PyList_GetSlice extends CApiTernaryBuiltinNode {
        @Specialization
        Object getSlice(PList list, Object iLow, Object iHigh,
                        @Bind Node inliningTarget,
                        @Cached com.oracle.graal.python.builtins.objects.list.ListBuiltins.GetItemNode getItemNode,
                        @Cached PySliceNew sliceNode) {
            return getItemNode.execute(null, list, sliceNode.execute(inliningTarget, iLow, iHigh, PNone.NONE));
        }

        @Fallback
        Object fallback(Object list, @SuppressWarnings("unused") Object iLow, @SuppressWarnings("unused") Object iHigh) {
            throw raiseFallback(list, PythonBuiltinClassType.PList);
        }
    }

    @CApiBuiltin(ret = Int, args = {PyObject, Py_ssize_t, Py_ssize_t, PyObject}, call = Direct)
    abstract static class PyList_SetSlice extends CApiQuaternaryBuiltinNode {

        @Specialization
        static int getSlice(PList list, Object iLow, Object iHigh, Object s,
                        @Bind Node inliningTarget,
                        @Cached ListBuiltins.SetSubscriptNode setItemNode,
                        @Cached PySliceNew sliceNode) {
            setItemNode.executeVoid(null, list, sliceNode.execute(inliningTarget, iLow, iHigh, PNone.NONE), s);
            return 0;
        }

        @Fallback
        int fallback(Object list, @SuppressWarnings("unused") Object iLow, @SuppressWarnings("unused") Object iHigh, @SuppressWarnings("unused") Object s) {
            throw raiseFallback(list, PythonBuiltinClassType.PList);
        }
    }

    @CApiBuiltin(ret = Int, args = {PyObject, PyObject}, call = Direct)
    abstract static class PyList_Extend extends CApiBinaryBuiltinNode {

        @Specialization
        static int extend(PList list, Object iterable,
                        @Cached ListExtendNode extendNode) {
            extendNode.execute(null, list, iterable);
            return 0;
        }

        @Fallback
        int fallback(Object list, @SuppressWarnings("unused") Object iterable) {
            throw raiseFallback(list, PythonBuiltinClassType.PList);
        }
    }

    @CApiBuiltin(ret = PyObjectTransfer, args = {PyListObject, PyObject}, call = Direct)
    abstract static class _PyList_Extend extends CApiBinaryBuiltinNode {

        @Specialization
        Object extend(PList list, Object iterable,
                        @Cached ListExtendNode extendNode) {
            extendNode.execute(null, list, iterable);
            return PNone.NONE;
        }

        @Fallback
        Object fallback(Object list, @SuppressWarnings("unused") Object iterable) {
            throw raiseFallback(list, PythonBuiltinClassType.PList);
        }
    }

    private static final CApiTiming TIMING_PYLIST_SIZE = CApiTiming.create(false, "PyList_Size");

    /*
     * A pure-C Py_SIZE implementation regressed mixed managed/native/list-subclass workload by
     * about 1.26x.
     */
    @CApiBuiltin(ret = Py_ssize_t, args = {PyObject}, call = Direct, acquireGil = false)
    static long PyList_Size(long opPtr) {
        CApiTiming.enter();
        try {
            Object op = NativeToPythonInternalNode.executeUncached(opPtr, false);
            if (op instanceof PList list) {
                return list.getSequenceStorage().length();
            }
            throw PythonCextBuiltins.badInternalCall("PyList_Size", "op");
        } finally {
            CApiTiming.exit(TIMING_PYLIST_SIZE);
        }
    }

    @CApiBuiltin(ret = Int, args = {PyObject}, call = Direct)
    abstract static class PyList_Sort extends CApiUnaryBuiltinNode {

        @Specialization
        static int append(PList list,
                        @Cached ListSortNode sortNode) {
            sortNode.execute(null, list);
            return 0;
        }

        @Fallback
        int fallback(Object list) {
            throw raiseFallback(list, PythonBuiltinClassType.PList);
        }
    }

    @CApiBuiltin(ret = Int, args = {PyObject, Py_ssize_t, PyObject}, call = Direct)
    abstract static class PyList_Insert extends CApiTernaryBuiltinNode {

        @Specialization
        static int insert(PList list, Object i, Object item,
                        @Cached ListInsertNode insertNode) {
            insertNode.execute(null, list, i, item);
            return 0;
        }

        @Fallback
        int fallback(Object list, @SuppressWarnings("unused") Object i, @SuppressWarnings("unused") Object item) {
            throw raiseFallback(list, PythonBuiltinClassType.PList);
        }
    }

    @CApiBuiltin(ret = Int, args = {PyObject}, call = Direct)
    abstract static class PyList_Reverse extends CApiUnaryBuiltinNode {
        @Specialization
        static int reverse(PList self,
                        @Cached ListBuiltins.ListReverseNode reverseNode) {
            reverseNode.execute(null, self);
            return 0;
        }

        @Fallback
        static int error(@SuppressWarnings("unused") Object self,
                        @Bind Node inliningTarget) {
            throw PRaiseNode.raiseStatic(inliningTarget, SystemError, ErrorMessages.BAD_ARG_TO_INTERNAL_FUNC);
        }
    }

    @CApiBuiltin(ret = Int, args = {PyObject}, call = Direct)
    abstract static class PyList_Clear extends CApiUnaryBuiltinNode {

        @Specialization
        static int clear(PList self) {
            self.setSequenceStorage(EmptySequenceStorage.INSTANCE);
            return 0;
        }

        @Fallback
        static int error(@SuppressWarnings("unused") Object self,
                        @Bind Node inliningTarget) {
            throw PRaiseNode.raiseStatic(inliningTarget, SystemError, ErrorMessages.BAD_ARG_TO_INTERNAL_FUNC);
        }
    }

    @CApiBuiltin(ret = INT64_T, args = {PyObject, Pointer}, call = Ignored)
    abstract static class GraalPyPrivate_List_TruncateNativeStorage extends CApiBinaryBuiltinNode {

        @Specialization
        static long doGeneric(PList self, long outItems) {
            SequenceStorage sequenceStorage = self.getSequenceStorage();
            if (sequenceStorage instanceof NativeObjectSequenceStorage nativeStorage) {
                writePtr(outItems, nativeStorage.getPtr());
                int length = nativeStorage.length();
                nativeStorage.setNewLength(0);
                return length;
            }
            return 0;
        }
    }

    @CApiBuiltin(ret = INT64_T, args = {PyObject, Pointer}, call = Ignored)
    abstract static class GraalPyPrivate_List_TryGetItems extends CApiBinaryBuiltinNode {

        @Specialization
        static long doGeneric(PList self, long outItems,
                        @Bind Node inliningTarget,
                        @Cached PySequenceArrayWrapper.ToNativeStorageNode toNativeStorageNode) {
            SequenceStorage sequenceStorage = self.getSequenceStorage();
            if (sequenceStorage instanceof ObjectSequenceStorage objectStorage) {
                sequenceStorage = toNativeStorageNode.execute(inliningTarget, objectStorage, false);
                self.setSequenceStorage(sequenceStorage);
            }
            if (sequenceStorage instanceof NativeObjectSequenceStorage nativeStorage) {
                writePtr(outItems, nativeStorage.getPtr());
                return nativeStorage.length();
            }
            return 0;
        }

        @SuppressWarnings("unused")
        @Specialization
        static long doGeneric(PNone none, Object ignore) {
            return 0;
        }
    }
}
