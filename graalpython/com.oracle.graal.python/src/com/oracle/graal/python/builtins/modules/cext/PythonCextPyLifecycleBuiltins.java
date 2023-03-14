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
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.Void;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.func_voidvoid;

import com.oracle.graal.python.builtins.modules.cext.PythonCextBuiltins.CApiBuiltin;
import com.oracle.graal.python.builtins.modules.cext.PythonCextBuiltins.CApiTernaryBuiltinNode;
import com.oracle.graal.python.builtins.modules.cext.PythonCextBuiltins.CApiUnaryBuiltinNode;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.cext.common.CExtCommonNodes;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.graal.python.util.ShutdownHook;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.interop.ArityException;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.interop.UnsupportedTypeException;
import com.oracle.truffle.api.strings.TruffleString;

public final class PythonCextPyLifecycleBuiltins {

    @CApiBuiltin(ret = Int, args = {func_voidvoid}, call = Direct)
    abstract static class Py_AtExit extends CApiUnaryBuiltinNode {

        @Specialization
        @TruffleBoundary
        int doGeneric(Object funcPtr) {
            getContext().registerAtexitHook(new ShutdownHook() {
                @Override
                public void call(@SuppressWarnings("unused") PythonContext context) {
                    try {
                        InteropLibrary.getUncached().execute(funcPtr);
                    } catch (UnsupportedTypeException | ArityException | UnsupportedMessageException e) {
                        // ignored
                    }
                }
            });
            return 0;
        }
    }

    @CApiBuiltin(ret = Void, args = {ConstCharPtrAsTruffleString, ConstCharPtrAsTruffleString, Int}, call = Ignored)
    abstract static class PyTruffle_FatalErrorFunc extends CApiTernaryBuiltinNode {

        @Specialization
        @TruffleBoundary
        Object doStrings(TruffleString func, TruffleString msg, int status) {
            CExtCommonNodes.fatalError(this, getContext(), func, msg, status);
            return PNone.NONE;
        }

        @Specialization
        @TruffleBoundary
        Object doGeneric(Object funcObj, Object msgObj, int status) {
            TruffleString func = funcObj == PNone.NO_VALUE ? null : (TruffleString) funcObj;
            TruffleString msg = msgObj == PNone.NO_VALUE ? null : (TruffleString) msgObj;
            return doStrings(func, msg, status);
        }
    }
}
