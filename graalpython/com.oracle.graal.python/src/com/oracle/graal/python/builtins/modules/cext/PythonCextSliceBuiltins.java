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

import static com.oracle.graal.python.builtins.modules.cext.PythonCextBuiltins.CApiCallPath.Direct;
import static com.oracle.graal.python.builtins.modules.cext.PythonCextBuiltins.CApiCallPath.Ignored;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.PyObject;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.PyObjectTransfer;

import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.modules.cext.PythonCextBuiltins.CApiBuiltin;
import com.oracle.graal.python.builtins.modules.cext.PythonCextBuiltins.CApiNullaryBuiltinNode;
import com.oracle.graal.python.builtins.modules.cext.PythonCextBuiltins.CApiTernaryBuiltinNode;
import com.oracle.graal.python.builtins.modules.cext.PythonCextBuiltins.CApiUnaryBuiltinNode;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.ellipsis.PEllipsis;
import com.oracle.graal.python.builtins.objects.slice.PIntSlice;
import com.oracle.graal.python.builtins.objects.slice.PObjectSlice;
import com.oracle.graal.python.lib.PySliceNew;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.nodes.Node;

public final class PythonCextSliceBuiltins {

    @CApiBuiltin(ret = PyObjectTransfer, args = {PyObject, PyObject, PyObject}, call = Direct)
    abstract static class PySlice_New extends CApiTernaryBuiltinNode {
        @Specialization
        static Object slice(Object start, Object stop, Object step,
                        @Bind Node inliningTarget,
                        @Cached PySliceNew sliceNode) {
            return sliceNode.execute(inliningTarget, prepare(start), prepare(stop), prepare(step));
        }

        private static Object prepare(Object obj) {
            if (obj == PNone.NO_VALUE) {
                return PNone.NONE;
            }
            return obj;
        }
    }

    @CApiBuiltin(ret = PyObjectTransfer, call = Ignored)
    abstract static class GraalPyPrivate_Ellipsis extends CApiNullaryBuiltinNode {
        @Specialization
        static Object run() {
            return PEllipsis.INSTANCE;
        }
    }

    @CApiBuiltin(ret = PyObjectTransfer, args = {PyObject}, call = Direct)
    abstract static class GraalPySlice_Start extends CApiUnaryBuiltinNode {
        @Specialization
        static Object get(PIntSlice slice) {
            return slice.getStart();
        }

        @Specialization
        static Object get(PObjectSlice slice) {
            return slice.getStart();
        }

        @Fallback
        static Object error(Object slice,
                        @Bind Node inliningTarget) {
            throw PRaiseNode.raiseStatic(inliningTarget, PythonBuiltinClassType.TypeError, ErrorMessages.MUST_BE_S_NOT_P, "slice", slice);
        }
    }

    @CApiBuiltin(ret = PyObjectTransfer, args = {PyObject}, call = Direct)
    abstract static class GraalPySlice_Stop extends CApiUnaryBuiltinNode {
        @Specialization
        static Object get(PIntSlice slice) {
            return slice.getStop();
        }

        @Specialization
        static Object get(PObjectSlice slice) {
            return slice.getStop();
        }

        @Fallback
        static Object error(Object slice,
                        @Bind Node inliningTarget) {
            throw PRaiseNode.raiseStatic(inliningTarget, PythonBuiltinClassType.TypeError, ErrorMessages.MUST_BE_S_NOT_P, "slice", slice);
        }
    }

    @CApiBuiltin(ret = PyObjectTransfer, args = {PyObject}, call = Direct)
    abstract static class GraalPySlice_Step extends CApiUnaryBuiltinNode {
        @Specialization
        static Object get(PIntSlice slice) {
            return slice.getStep();
        }

        @Specialization
        static Object get(PObjectSlice slice) {
            return slice.getStep();
        }

        @Fallback
        static Object error(Object slice,
                        @Bind Node inliningTarget) {
            throw PRaiseNode.raiseStatic(inliningTarget, PythonBuiltinClassType.TypeError, ErrorMessages.MUST_BE_S_NOT_P, "slice", slice);
        }
    }
}
