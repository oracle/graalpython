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

import static com.oracle.graal.python.builtins.modules.cext.PythonCextBuiltins.CApiCallPath.Direct;
import static com.oracle.graal.python.builtins.modules.cext.PythonCextBuiltins.CApiCallPath.Ignored;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.ConstCharPtrAsTruffleString;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.Int;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.Pointer;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.PyObject;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.Py_hash_t;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.Void;

import com.oracle.graal.python.builtins.modules.SysModuleBuiltins;
import com.oracle.graal.python.builtins.modules.cext.PythonCextBuiltins.CApiBinaryBuiltinNode;
import com.oracle.graal.python.builtins.modules.cext.PythonCextBuiltins.CApiBuiltin;
import com.oracle.graal.python.builtins.modules.cext.PythonCextBuiltins.CApiUnaryBuiltinNode;
import com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor;
import com.oracle.graal.python.lib.PyObjectHashNode;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.InvalidArrayIndexException;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.interop.UnsupportedTypeException;

public final class PythonCextHashBuiltins {

    @CApiBuiltin(ret = Void, args = {Pointer}, call = Ignored)
    abstract static class PyTruffleHash_InitSecret extends CApiUnaryBuiltinNode {
        @Specialization
        @TruffleBoundary
        Object get(Object secretPtr) {
            try {
                InteropLibrary lib = InteropLibrary.getUncached(secretPtr);
                byte[] secret = getContext().getHashSecret();
                int len = (int) lib.getArraySize(secretPtr);
                for (int i = 0; i < len; i++) {
                    lib.writeArrayElement(secretPtr, i, secret[i]);
                }
                return 0;
            } catch (UnsupportedMessageException | UnsupportedTypeException | InvalidArrayIndexException e) {
                throw CompilerDirectives.shouldNotReachHere(e);
            }
        }
    }

    @CApiBuiltin(ret = ArgDescriptor.Long, args = {Int}, call = Ignored)
    abstract static class PyTruffle_HashConstant extends CApiUnaryBuiltinNode {

        @Specialization
        @TruffleBoundary
        static long doI(int idx) {
            switch (idx) {
                case 0:
                    return PyObjectHashNode.getUncached().execute(null, Double.POSITIVE_INFINITY);
                case 1:
                    return PyObjectHashNode.getUncached().execute(null, Double.NaN);
                case 2:
                    return SysModuleBuiltins.HASH_IMAG;
                default:
                    throw CompilerDirectives.shouldNotReachHere();

            }
        }
    }

    @CApiBuiltin(ret = Py_hash_t, args = {PyObject, ArgDescriptor.Double}, call = Direct)
    @ImportStatic(Double.class)
    abstract static class _Py_HashDouble extends CApiBinaryBuiltinNode {

        @Specialization(guards = "isFinite(value)")
        long doFinite(@SuppressWarnings("unused") Object inst, double value) {
            return PyObjectHashNode.hash(value);
        }

        @Specialization(guards = "!isFinite(value)")
        long doNonFinite(Object inst, @SuppressWarnings("unused") double value,
                        @Cached PyObjectHashNode hashNode) {
            return hashNode.execute(null, inst);
        }
    }

    @CApiBuiltin(ret = Py_hash_t, args = {ConstCharPtrAsTruffleString}, call = Ignored)
    abstract static class _PyTruffle_HashBytes extends CApiUnaryBuiltinNode {

        @Specialization
        @TruffleBoundary
        static long doI(Object value,
                        @Cached PyObjectHashNode hashNode) {
            return hashNode.execute(null, value);
        }
    }
}
