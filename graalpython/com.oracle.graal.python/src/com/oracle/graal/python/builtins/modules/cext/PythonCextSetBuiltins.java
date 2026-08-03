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

import static com.oracle.graal.python.builtins.PythonBuiltinClassType.SystemError;
import static com.oracle.graal.python.builtins.modules.cext.PythonCextBuiltins.CApiCallPath.Direct;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.Int;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.PyObject;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.PyObjectTransfer;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.Py_ssize_t;
import static com.oracle.graal.python.nodes.ErrorMessages.EXPECTED_S_NOT_P;
import static com.oracle.graal.python.runtime.nativeaccess.NativeMemory.NULLPTR;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.modules.cext.PythonCextBuiltins.CApiBinaryBuiltinNode;
import com.oracle.graal.python.builtins.modules.cext.PythonCextBuiltins.CApiBuiltin;
import com.oracle.graal.python.builtins.modules.cext.PythonCextBuiltins.CApiUnaryBuiltinNode;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.cext.capi.CExtNodes.EnsurePythonObjectNode;
import com.oracle.graal.python.builtins.objects.cext.capi.transitions.CApiTiming;
import com.oracle.graal.python.builtins.objects.cext.capi.transitions.CApiTransitions.NativeToPythonInternalNode;
import com.oracle.graal.python.builtins.objects.cext.capi.transitions.CApiTransitions.PythonToNativeInternalNode;
import com.oracle.graal.python.builtins.objects.common.HashingCollectionNodes;
import com.oracle.graal.python.builtins.objects.common.HashingStorage;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodes.HashingStorageGetItem;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodes.HashingStorageLen;
import com.oracle.graal.python.builtins.objects.ints.PInt;
import com.oracle.graal.python.builtins.objects.set.FrozenSetBuiltins.FrozenSetNode;
import com.oracle.graal.python.builtins.objects.set.PBaseSet;
import com.oracle.graal.python.builtins.objects.set.PFrozenSet;
import com.oracle.graal.python.builtins.objects.set.PSet;
import com.oracle.graal.python.builtins.objects.set.SetBuiltins.ClearNode;
import com.oracle.graal.python.builtins.objects.set.SetNodes.ConstructSetNode;
import com.oracle.graal.python.builtins.objects.set.SetNodes.DiscardNode;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.runtime.object.PFactory;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.nodes.Node;

public final class PythonCextSetBuiltins {

    private static final CApiTiming TIMING_PYSET_NEW = CApiTiming.create(false, "PySet_New");

    @CApiBuiltin(ret = PyObjectTransfer, args = {PyObject}, call = Direct, acquireGil = false)
    static long PySet_New(long iterablePtr) {
        CApiTiming.enter();
        try {
            PSet set;
            if (iterablePtr == NULLPTR) {
                set = PFactory.createSet(PythonLanguage.get(null));
            } else {
                Object iterable = NativeToPythonInternalNode.executeUncached(iterablePtr, false);
                set = ConstructSetNode.getUncached().execute(null, iterable);
            }
            assert EnsurePythonObjectNode.doesNotNeedPromotion(set);
            return PythonToNativeInternalNode.executeUncached(set, true);
        } finally {
            CApiTiming.exit(TIMING_PYSET_NEW);
        }
    }

    @CApiBuiltin(ret = Int, args = {PyObject, PyObject}, call = Direct)
    abstract static class PySet_Contains extends CApiBinaryBuiltinNode {
        @Specialization
        static int contains(PSet anyset, Object item,
                        @Bind Node inliningTarget,
                        @Shared @Cached HashingStorageGetItem getItem) {
            HashingStorage storage = anyset.getDictStorage();
            // TODO: FIXME: this might call __hash__ twice
            return PInt.intValue(getItem.hasKey(null, inliningTarget, storage, item));
        }

        @Specialization
        static int contains(PFrozenSet anyset, Object item,
                        @Bind Node inliningTarget,
                        @Shared @Cached HashingStorageGetItem getItem) {
            HashingStorage storage = anyset.getDictStorage();
            // TODO: FIXME: this might call __hash__ twice
            return PInt.intValue(getItem.hasKey(null, inliningTarget, storage, item));
        }

        @Fallback
        int fallback(@SuppressWarnings("unused") Object anyset, @SuppressWarnings("unused") Object item) {
            throw raiseFallback(anyset, PythonBuiltinClassType.PSet, PythonBuiltinClassType.PFrozenSet);
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
        static Object newFrozenSet(Object iterable,
                        @Cached FrozenSetNode frozenSetNode) {
            return frozenSetNode.execute(null, PythonBuiltinClassType.PFrozenSet, iterable);
        }

        @SuppressWarnings("unused")
        @Specialization
        static Object newFrozenSet(PNone iterable,
                        @Bind PythonLanguage language) {
            return PFactory.createFrozenSet(language);
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
                        @Bind Node inliningTarget,
                        @Cached HashingCollectionNodes.SetItemNode setItemNode) {
            setItemNode.execute(null, inliningTarget, self, o, PNone.NO_VALUE);
            return 0;
        }

        @Specialization(guards = "!isAnySet(self)")
        static int add(Object self, @SuppressWarnings("unused") Object o,
                        @Bind Node inliningTarget) {
            throw PRaiseNode.raiseStatic(inliningTarget, SystemError, EXPECTED_S_NOT_P, "a set object", self);
        }
    }

    /*
     * A pure-C implementation regressed a mixed managed/native set/frozenset workload by about
     * 1.16x.
     */
    @CApiBuiltin(ret = Py_ssize_t, args = {PyObject}, call = Direct)
    abstract static class PySet_Size extends CApiUnaryBuiltinNode {
        @Specialization
        static long get(PBaseSet object,
                        @Bind Node inliningTarget,
                        @Cached HashingStorageLen lenNode) {
            return lenNode.execute(inliningTarget, object.getDictStorage());
        }

        @Fallback
        static long error(@SuppressWarnings("unused") Object self,
                        @Bind Node inliningTarget) {
            throw PRaiseNode.raiseStatic(inliningTarget, SystemError, ErrorMessages.BAD_ARG_TO_INTERNAL_FUNC);
        }
    }
}
