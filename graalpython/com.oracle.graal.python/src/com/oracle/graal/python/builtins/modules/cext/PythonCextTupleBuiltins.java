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
import static com.oracle.graal.python.builtins.modules.cext.PythonCextBuiltins.CApiCallPath.Direct;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.Int;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.PyObject;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.PyObjectBorrowed;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.PyObjectTransfer;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.Py_ssize_t;

import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.modules.cext.PythonCextBuiltins.CApiBinaryBuiltinNode;
import com.oracle.graal.python.builtins.modules.cext.PythonCextBuiltins.CApiBuiltin;
import com.oracle.graal.python.builtins.modules.cext.PythonCextBuiltins.CApiTernaryBuiltinNode;
import com.oracle.graal.python.builtins.modules.cext.PythonCextBuiltins.CApiUnaryBuiltinNode;
import com.oracle.graal.python.builtins.modules.cext.PythonCextBuiltins.PromoteBorrowedValue;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.cext.PythonAbstractNativeObject;
import com.oracle.graal.python.builtins.objects.common.SequenceStorageNodes;
import com.oracle.graal.python.builtins.objects.common.SequenceStorageNodes.GetItemNode;
import com.oracle.graal.python.builtins.objects.common.SequenceStorageNodes.GetItemScalarNode;
import com.oracle.graal.python.builtins.objects.common.SequenceStorageNodes.ListGeneralizationNode;
import com.oracle.graal.python.builtins.objects.common.SequenceStorageNodes.SetItemNode;
import com.oracle.graal.python.builtins.objects.common.SequenceStorageNodes.SetItemScalarNode;
import com.oracle.graal.python.builtins.objects.tuple.PTuple;
import com.oracle.graal.python.lib.PySliceNew;
import com.oracle.graal.python.lib.PyTupleSizeNode;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.builtins.TupleNodes.GetNativeTupleStorage;
import com.oracle.graal.python.runtime.sequence.storage.NativeSequenceStorage;
import com.oracle.graal.python.runtime.sequence.storage.SequenceStorage;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.profiles.ConditionProfile;

public final class PythonCextTupleBuiltins {

    @CApiBuiltin(ret = PyObjectTransfer, args = {Py_ssize_t}, call = Direct)
    abstract static class PyTuple_New extends CApiUnaryBuiltinNode {

        @Specialization
        PTuple doGeneric(long size) {
            return factory().createTuple(new Object[(int) size]);
        }
    }

    @CApiBuiltin(ret = Int, args = {PyObject, Py_ssize_t, PyObjectTransfer}, call = Direct)
    abstract static class PyTuple_SetItem extends CApiTernaryBuiltinNode {
        @Specialization
        int doManaged(PTuple tuple, long index, Object element,
                        @Cached("createSetItem()") SequenceStorageNodes.SetItemNode setItemNode,
                        @Cached ConditionProfile generalizedProfile) {
            SequenceStorage sequenceStorage = tuple.getSequenceStorage();
            checkBounds(sequenceStorage, index);
            SequenceStorage newStorage = setItemNode.execute(null, sequenceStorage, (int) index, element);
            if (generalizedProfile.profile(tuple.getSequenceStorage() != newStorage)) {
                tuple.setSequenceStorage(newStorage);
            }
            return 0;
        }

        @Specialization
        int doNative(PythonAbstractNativeObject tuple, long index, Object element,
                        @Cached GetNativeTupleStorage asNativeStorage,
                        @Cached SequenceStorageNodes.SetNativeItemScalarNode setItemNode) {
            NativeSequenceStorage sequenceStorage = asNativeStorage.execute(tuple);
            checkBounds(sequenceStorage, index);
            setItemNode.execute(sequenceStorage, (int) index, element);
            return 0;
        }

        @Fallback
        @SuppressWarnings("unused")
        Object fallback(Object tuple, Object index, Object element) {
            throw raiseFallback(tuple, PythonBuiltinClassType.PTuple);
        }

        private void checkBounds(SequenceStorage sequenceStorage, long index) {
            // we must do a bounds-check but we must not normalize the index
            if (index < 0 || index >= sequenceStorage.length()) {
                throw raise(IndexError, ErrorMessages.TUPLE_OUT_OF_BOUNDS);
            }
        }

        protected static SetItemNode createSetItem() {
            return SetItemNode.create(null, ListGeneralizationNode::create);
        }
    }

    @CApiBuiltin(ret = Int, args = {PyObject, Py_ssize_t, PyObjectTransfer}, call = Direct)
    abstract static class _PyTuple_SET_ITEM extends CApiTernaryBuiltinNode {
        @Specialization
        int doManaged(PTuple tuple, long index, Object element,
                        @Cached ListGeneralizationNode generalizationNode,
                        @Cached SequenceStorageNodes.InitializeItemScalarNode setItemNode,
                        @Cached ConditionProfile generalizedProfile) {
            // we cannot assume that there is nothing already in the tuple, because the API usage
            // is valid if the tuple has never been visible to Python code so far, and it is up to
            // the extension author to take care of correct decref's for the previously contained
            // elements. c.f. _testcapi.c#test_k_code where a tuple's element 0 is set multiple
            // times
            SequenceStorage sequenceStorage = tuple.getSequenceStorage();
            checkBounds(sequenceStorage, index);
            SequenceStorage newStorage = generalizationNode.execute(sequenceStorage, element);
            setItemNode.execute(newStorage, (int) index, element);
            if (generalizedProfile.profile(tuple.getSequenceStorage() != newStorage)) {
                tuple.setSequenceStorage(newStorage);
            }
            return 0;
        }

        @Specialization
        int doNative(PythonAbstractNativeObject tuple, long index, Object element,
                        @Cached GetNativeTupleStorage asNativeStorage,
                        @Cached SequenceStorageNodes.InitializeNativeItemScalarNode setItemNode) {
            NativeSequenceStorage sequenceStorage = asNativeStorage.execute(tuple);
            checkBounds(sequenceStorage, index);
            setItemNode.execute(sequenceStorage, (int) index, element);
            return 0;
        }

        @Fallback
        @SuppressWarnings("unused")
        Object fallback(Object tuple, Object index, Object element) {
            throw raiseFallback(tuple, PythonBuiltinClassType.PTuple);
        }

        private void checkBounds(SequenceStorage sequenceStorage, long index) {
            // we must do a bounds-check but we must not normalize the index
            if (index < 0 || index >= sequenceStorage.length()) {
                throw raise(IndexError, ErrorMessages.TUPLE_OUT_OF_BOUNDS);
            }
        }
    }

    @CApiBuiltin(ret = PyObjectBorrowed, args = {PyObject, Py_ssize_t}, call = Direct)
    public abstract static class PyTuple_GetItem extends CApiBinaryBuiltinNode {

        public abstract Object execute(PTuple tuple, long key);

        @Specialization
        Object doPTuple(PTuple tuple, long key,
                        @Shared("promote") @Cached PromoteBorrowedValue promoteNode,
                        @Cached ListGeneralizationNode generalizationNode,
                        @Shared("setItem") @Cached SetItemScalarNode setItemNode,
                        @Shared("getItem") @Cached GetItemScalarNode getItemNode) {
            SequenceStorage sequenceStorage = tuple.getSequenceStorage();
            int index = checkIndex(key, sequenceStorage);
            Object result = getItemNode.execute(sequenceStorage, index);
            Object promotedValue = promoteNode.execute(result);
            if (promotedValue != null) {
                sequenceStorage = generalizationNode.execute(sequenceStorage, promotedValue);
                tuple.setSequenceStorage(sequenceStorage);
                setItemNode.execute(sequenceStorage, index, promotedValue);
                return promotedValue;
            }
            return result;
        }

        @Specialization
        Object doNative(PythonAbstractNativeObject tuple, long key,
                        @Cached GetNativeTupleStorage asNativeStorage,
                        @Shared("promote") @Cached PromoteBorrowedValue promoteNode,
                        @Shared("setItem") @Cached SetItemScalarNode setItemNode,
                        @Shared("getItem") @Cached GetItemScalarNode getItemNode) {
            SequenceStorage sequenceStorage = asNativeStorage.execute(tuple);
            int index = checkIndex(key, sequenceStorage);
            Object result = getItemNode.execute(sequenceStorage, index);
            Object promotedValue = promoteNode.execute(result);
            if (promotedValue != null) {
                setItemNode.execute(sequenceStorage, index, promotedValue);
                return promotedValue;
            }
            return result;
        }

        @Fallback
        Object fallback(Object tuple, @SuppressWarnings("unused") Object pos) {
            throw raiseFallback(tuple, PythonBuiltinClassType.PTuple);
        }

        private int checkIndex(long key, SequenceStorage sequenceStorage) {
            // we must do a bounds-check but we must not normalize the index
            if (key < 0 || key >= sequenceStorage.length()) {
                throw raise(IndexError, ErrorMessages.TUPLE_OUT_OF_BOUNDS);
            }
            return (int) key;
        }
    }

    @CApiBuiltin(ret = Py_ssize_t, args = {PyObject}, call = Direct)
    abstract static class PyTuple_Size extends CApiUnaryBuiltinNode {
        @Specialization
        public static int size(Object tuple,
                        @Cached PyTupleSizeNode pyTupleSizeNode) {
            return pyTupleSizeNode.execute(tuple);
        }
    }

    @CApiBuiltin(ret = PyObjectTransfer, args = {PyObject, Py_ssize_t, Py_ssize_t}, call = Direct)
    abstract static class PyTuple_GetSlice extends CApiTernaryBuiltinNode {
        @Specialization
        static Object getSlice(PTuple tuple, Object iLow, Object iHigh,
                        @Shared("getItem") @Cached("createForTuple()") SequenceStorageNodes.GetItemNode getItemNode,
                        @Shared("newSlice") @Cached PySliceNew sliceNode) {
            return doGetSlice(tuple.getSequenceStorage(), iLow, iHigh, getItemNode, sliceNode);
        }

        @Specialization
        static Object doNative(PythonAbstractNativeObject tuple, Object iLow, Object iHigh,
                        @Shared("getItem") @Cached("createForTuple()") SequenceStorageNodes.GetItemNode getItemNode,
                        @Shared("newSlice") @Cached PySliceNew sliceNode,
                        @Cached GetNativeTupleStorage asNativeStorage) {
            return doGetSlice(asNativeStorage.execute(tuple), iLow, iHigh, getItemNode, sliceNode);
        }

        @SuppressWarnings("unused")
        @Fallback
        Object fallback(Object tuple, Object iLow, Object iHigh) {
            throw raiseFallback(tuple, PythonBuiltinClassType.PTuple);
        }

        private static Object doGetSlice(SequenceStorage storage, Object iLow, Object iHigh, GetItemNode getItemNode, PySliceNew sliceNode) {
            return getItemNode.execute(null, storage, sliceNode.execute(iLow, iHigh, PNone.NONE));
        }
    }
}
