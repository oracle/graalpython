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

import static com.oracle.graal.python.builtins.PythonBuiltinClassType.IndexError;
import static com.oracle.graal.python.builtins.PythonBuiltinClassType.SystemError;
import static com.oracle.graal.python.builtins.modules.cext.PythonCextBuiltins.CApiCallPath.Direct;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.Int;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.PyListObject;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.PyObject;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.PyObjectBorrowed;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.PyObjectTransfer;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.Py_ssize_t;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.Void;
import static com.oracle.graal.python.nodes.ErrorMessages.BAD_ARG_TO_INTERNAL_FUNC_S;

import java.util.Arrays;

import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.modules.cext.PythonCextBuiltins.CApiBinaryBuiltinNode;
import com.oracle.graal.python.builtins.modules.cext.PythonCextBuiltins.CApiBuiltin;
import com.oracle.graal.python.builtins.modules.cext.PythonCextBuiltins.CApiQuaternaryBuiltinNode;
import com.oracle.graal.python.builtins.modules.cext.PythonCextBuiltins.CApiTernaryBuiltinNode;
import com.oracle.graal.python.builtins.modules.cext.PythonCextBuiltins.CApiUnaryBuiltinNode;
import com.oracle.graal.python.builtins.modules.cext.PythonCextBuiltins.PromoteBorrowedValue;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.cext.PythonAbstractNativeObject;
import com.oracle.graal.python.builtins.objects.common.SequenceStorageNodes;
import com.oracle.graal.python.builtins.objects.common.SequenceStorageNodes.GetItemScalarNode;
import com.oracle.graal.python.builtins.objects.common.SequenceStorageNodes.ListGeneralizationNode;
import com.oracle.graal.python.builtins.objects.common.SequenceStorageNodes.SetItemScalarNode;
import com.oracle.graal.python.builtins.objects.list.ListBuiltins;
import com.oracle.graal.python.builtins.objects.list.ListBuiltins.ListExtendNode;
import com.oracle.graal.python.builtins.objects.list.ListBuiltins.ListInsertNode;
import com.oracle.graal.python.builtins.objects.list.ListBuiltins.ListSortNode;
import com.oracle.graal.python.builtins.objects.list.PList;
import com.oracle.graal.python.lib.PySliceNew;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.builtins.ListNodes.AppendNode;
import com.oracle.graal.python.nodes.builtins.ListNodes.GetNativeListStorage;
import com.oracle.graal.python.nodes.builtins.TupleNodes.ConstructTupleNode;
import com.oracle.graal.python.runtime.object.PythonObjectFactory;
import com.oracle.graal.python.runtime.sequence.storage.NativeSequenceStorage;
import com.oracle.graal.python.runtime.sequence.storage.SequenceStorage;
import com.oracle.graal.python.util.PythonUtils;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.profiles.ConditionProfile;

public final class PythonCextListBuiltins {

    ///////////// list /////////////
    @CApiBuiltin(ret = PyObjectTransfer, args = {Py_ssize_t}, call = Direct)
    abstract static class PyList_New extends CApiUnaryBuiltinNode {
        @Specialization(guards = "size < 0")
        static Object newListError(long size,
                        @Cached PRaiseNode raiseNode) {
            throw raiseNode.raise(SystemError, BAD_ARG_TO_INTERNAL_FUNC_S, size);
        }

        @SuppressWarnings("unused")
        @Specialization(guards = "size == 0")
        static Object newEmptyList(long size,
                        @Shared @Cached PythonObjectFactory factory) {
            return factory.createList(PythonUtils.EMPTY_OBJECT_ARRAY);
        }

        @Specialization(guards = "size > 0")
        static Object newList(long size,
                        @Shared @Cached PythonObjectFactory factory) {
            return factory.createList(array(size));
        }

        private static Object[] array(long size) {
            Object[] a = new Object[(int) size];
            Arrays.fill(a, PNone.NO_VALUE);
            return a;
        }
    }

    @CApiBuiltin(ret = PyObjectBorrowed, args = {PyObject, Py_ssize_t}, call = Direct)
    abstract static class PyList_GetItem extends CApiBinaryBuiltinNode {

        @Specialization
        Object doPList(PList list, long key,
                        @Bind("this") Node inliningTarget,
                        @Cached PromoteBorrowedValue promoteNode,
                        @Cached ListGeneralizationNode generalizationNode,
                        @Cached SetItemScalarNode setItemNode,
                        @Cached GetItemScalarNode getItemNode) {
            SequenceStorage sequenceStorage = list.getSequenceStorage();
            // we must do a bounds-check but we must not normalize the index
            if (key < 0 || key >= sequenceStorage.length()) {
                throw raise(IndexError, ErrorMessages.LIST_INDEX_OUT_OF_RANGE);
            }
            Object result = getItemNode.execute(inliningTarget, sequenceStorage, (int) key);
            Object promotedValue = promoteNode.execute(result);
            if (promotedValue != null) {
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

    @CApiBuiltin(ret = Int, args = {PyObject, PyObject}, call = Direct)
    abstract static class PyList_Append extends CApiBinaryBuiltinNode {

        @Specialization
        int append(PList list, Object newItem,
                        @Cached AppendNode appendNode) {
            if (newItem == PNone.NO_VALUE) {
                throw badInternalCall("newitem");
            }
            appendNode.execute(list, newItem);
            return 0;
        }

        @Fallback
        int fallback(Object list, @SuppressWarnings("unused") Object newItem) {
            throw raiseFallback(list, PythonBuiltinClassType.PList);
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
                        @Bind("this") Node inliningTarget,
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
                        @Bind("this") Node inliningTarget,
                        @Cached com.oracle.graal.python.builtins.objects.list.ListBuiltins.SetItemNode setItemNode,
                        @Cached PySliceNew sliceNode) {
            setItemNode.execute(null, list, sliceNode.execute(inliningTarget, iLow, iHigh, PNone.NONE), s);
            return 0;
        }

        @Fallback
        int fallback(Object list, @SuppressWarnings("unused") Object iLow, @SuppressWarnings("unused") Object iHigh, @SuppressWarnings("unused") Object s) {
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

    @CApiBuiltin(ret = Py_ssize_t, args = {PyObject}, call = Direct)
    abstract static class PyList_Size extends CApiUnaryBuiltinNode {

        @Specialization
        static int size(PList list) {
            return list.getSequenceStorage().length();
        }

        @Fallback
        int fallback(Object list) {
            throw raiseFallback(list, PythonBuiltinClassType.PList);
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

    @CApiBuiltin(ret = Int, args = {PyObject, Py_ssize_t, PyObjectTransfer}, call = Direct)
    abstract static class PyList_SetItem extends CApiTernaryBuiltinNode {
        @Specialization
        int doManaged(PList list, Object position, Object element,
                        @Cached("createForList()") SequenceStorageNodes.SetItemNode setItemNode,
                        @Cached ConditionProfile generalizedProfile) {
            SequenceStorage newStorage = setItemNode.execute(null, list.getSequenceStorage(), position, element);
            if (generalizedProfile.profile(list.getSequenceStorage() != newStorage)) {
                list.setSequenceStorage(newStorage);
            }
            return 0;
        }

        @Fallback
        int fallback(Object list, @SuppressWarnings("unused") Object i, @SuppressWarnings("unused") Object item) {
            throw raiseFallback(list, PythonBuiltinClassType.PList);
        }
    }

    @CApiBuiltin(ret = Void, args = {PyObject, Py_ssize_t, PyObject}, call = Direct)
    abstract static class _PyList_SET_ITEM extends CApiTernaryBuiltinNode {
        @Specialization
        int doManaged(PList list, long index, Object element,
                        @Bind("this") Node inliningTarget,
                        @Cached ListGeneralizationNode generalizationNode,
                        @Cached SequenceStorageNodes.InitializeItemScalarNode setItemNode,
                        @Cached ConditionProfile generalizedProfile) {
            SequenceStorage sequenceStorage = list.getSequenceStorage();
            checkBounds(sequenceStorage, index);
            SequenceStorage newStorage = generalizationNode.execute(inliningTarget, sequenceStorage, element);
            setItemNode.execute(inliningTarget, newStorage, (int) index, element);
            if (generalizedProfile.profile(list.getSequenceStorage() != newStorage)) {
                list.setSequenceStorage(newStorage);
            }
            return 0;
        }

        @Specialization
        int doNative(PythonAbstractNativeObject list, long index, Object element,
                        @Cached GetNativeListStorage asNativeStorage,
                        @Cached SequenceStorageNodes.InitializeNativeItemScalarNode setItemNode) {
            NativeSequenceStorage sequenceStorage = asNativeStorage.execute(list);
            checkBounds(sequenceStorage, index);
            setItemNode.execute(sequenceStorage, (int) index, element);
            return 0;
        }

        @Fallback
        @SuppressWarnings("unused")
        Object fallback(Object list, Object index, Object element) {
            throw raiseFallback(list, PythonBuiltinClassType.PList);
        }

        private void checkBounds(SequenceStorage sequenceStorage, long index) {
            // we must do a bounds-check but we must not normalize the index
            if (index < 0 || index >= sequenceStorage.length()) {
                throw raise(IndexError, ErrorMessages.INDEX_OUT_OF_BOUNDS);
            }
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
    }
}
