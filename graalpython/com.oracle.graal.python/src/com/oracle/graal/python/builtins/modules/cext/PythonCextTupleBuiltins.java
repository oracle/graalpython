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
import com.oracle.graal.python.builtins.objects.common.SequenceStorageNodes;
import com.oracle.graal.python.builtins.objects.common.SequenceStorageNodes.GetItemScalarNode;
import com.oracle.graal.python.builtins.objects.common.SequenceStorageNodes.ListGeneralizationNode;
import com.oracle.graal.python.builtins.objects.common.SequenceStorageNodes.SetItemScalarNode;
import com.oracle.graal.python.builtins.objects.tuple.PTuple;
import com.oracle.graal.python.builtins.objects.tuple.TupleBuiltins.GetItemNode;
import com.oracle.graal.python.lib.PySliceNew;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.runtime.sequence.storage.SequenceStorage;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.profiles.ConditionProfile;

public final class PythonCextTupleBuiltins {

    @CApiBuiltin(ret = PyObjectTransfer, args = {Py_ssize_t}, call = Direct)
    @GenerateNodeFactory
    public abstract static class PyTuple_New extends CApiUnaryBuiltinNode {

        @Specialization
        PTuple doGeneric(long size) {
            return factory().createTuple(new Object[(int) size]);
        }
    }

    @CApiBuiltin(ret = Int, args = {PyObject, Py_ssize_t, PyObjectTransfer}, call = Direct)
    @GenerateNodeFactory
    public abstract static class PyTuple_SetItem extends CApiTernaryBuiltinNode {
        @Specialization
        static int doManaged(PTuple tuple, Object position, Object element,
                        @Cached("createForList()") SequenceStorageNodes.SetItemNode setItemNode,
                        @Cached ConditionProfile generalizedProfile) {
            setItemNode.execute(null, tuple.getSequenceStorage(), position, element);
            SequenceStorage newStorage = setItemNode.execute(null, tuple.getSequenceStorage(), position, element);
            if (generalizedProfile.profile(tuple.getSequenceStorage() != newStorage)) {
                tuple.setSequenceStorage(newStorage);
            }
            return 0;
        }
    }

    @CApiBuiltin(ret = PyObjectBorrowed, args = {PyObject, Py_ssize_t}, call = Direct)
    @GenerateNodeFactory
    public abstract static class PyTuple_GetItem extends CApiBinaryBuiltinNode {

        public abstract Object execute(PTuple tuple, long key);

        @Specialization
        Object doPTuple(PTuple tuple, long key,
                        @Cached PromoteBorrowedValue promoteNode,
                        @Cached ListGeneralizationNode generalizationNode,
                        @Cached SetItemScalarNode setItemNode,
                        @Cached GetItemScalarNode getItemNode) {
            SequenceStorage sequenceStorage = tuple.getSequenceStorage();
            // we must do a bounds-check but we must not normalize the index
            if (key < 0 || key >= sequenceStorage.length()) {
                throw raise(IndexError, ErrorMessages.TUPLE_OUT_OF_BOUNDS);
            }
            Object result = getItemNode.execute(sequenceStorage, (int) key);
            Object promotedValue = promoteNode.execute(result);
            if (promotedValue != null) {
                sequenceStorage = generalizationNode.execute(sequenceStorage, promotedValue);
                tuple.setSequenceStorage(sequenceStorage);
                setItemNode.execute(sequenceStorage, (int) key, promotedValue);
                return promotedValue;
            }
            return result;
        }

        @Fallback
        Object fallback(Object tuple, @SuppressWarnings("unused") Object pos) {
            throw raiseFallback(tuple, PythonBuiltinClassType.PTuple);
        }
    }

    @CApiBuiltin(ret = Py_ssize_t, args = {PyObject}, call = Direct)
    @GenerateNodeFactory
    public abstract static class PyTuple_Size extends CApiUnaryBuiltinNode {
        @Specialization
        public static int size(Object tuple,
                        @Cached com.oracle.graal.python.lib.PyTupleSizeNode pyTupleSizeNode) {
            return pyTupleSizeNode.execute(tuple);
        }
    }

    @CApiBuiltin(ret = PyObjectTransfer, args = {PyObject, Py_ssize_t, Py_ssize_t}, call = Direct)
    @GenerateNodeFactory
    abstract static class PyTuple_GetSlice extends CApiTernaryBuiltinNode {
        @Specialization
        Object getSlice(PTuple tuple, Object iLow, Object iHigh,
                        @Cached GetItemNode getItemNode,
                        @Cached PySliceNew sliceNode) {
            return getItemNode.execute(null, tuple, sliceNode.execute(iLow, iHigh, PNone.NONE));
        }

        @SuppressWarnings("unused")
        @Fallback
        Object fallback(Object tuple, Object iLow, Object iHigh) {
            throw raiseFallback(tuple, PythonBuiltinClassType.PTuple);
        }
    }
}
