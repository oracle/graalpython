/*
 * Copyright (c) 2023, 2025, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.builtins.objects.cext.capi;

import static com.oracle.graal.python.builtins.objects.cext.structs.CFields.PyAsyncMethods__am_aiter;
import static com.oracle.graal.python.builtins.objects.cext.structs.CFields.PyAsyncMethods__am_anext;
import static com.oracle.graal.python.builtins.objects.cext.structs.CFields.PyAsyncMethods__am_await;
import static com.oracle.graal.python.builtins.objects.cext.structs.CFields.PyTypeObject__tp_call;
import static com.oracle.graal.python.builtins.objects.cext.structs.CFields.PyTypeObject__tp_init;
import static com.oracle.graal.python.builtins.objects.cext.structs.CFields.PyTypeObject__tp_repr;
import static com.oracle.graal.python.builtins.objects.cext.structs.CFields.PyTypeObject__tp_str;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___AITER__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___ANEXT__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___AWAIT__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___CALL__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___INIT__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___REPR__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___STR__;

import com.oracle.graal.python.builtins.objects.cext.capi.PyProcsWrapper.CallFunctionWrapper;
import com.oracle.graal.python.builtins.objects.cext.capi.PyProcsWrapper.InitWrapper;
import com.oracle.graal.python.builtins.objects.cext.capi.PyProcsWrapper.UnaryFuncLegacyWrapper;
import com.oracle.graal.python.builtins.objects.cext.structs.CFields;
import com.oracle.graal.python.builtins.objects.type.MethodsFlags;
import com.oracle.graal.python.util.Function;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.strings.TruffleString;

public enum SlotMethodDef {
    TP_CALL(PyTypeObject__tp_call, T___CALL__, CallFunctionWrapper::new),
    TP_INIT(PyTypeObject__tp_init, T___INIT__, InitWrapper::new),
    TP_REPR(PyTypeObject__tp_repr, T___REPR__, PyProcsWrapper.UnaryFuncLegacyWrapper::new),
    TP_STR(PyTypeObject__tp_str, T___STR__, UnaryFuncLegacyWrapper::new),

    AM_AWAIT(PyAsyncMethods__am_await, T___AWAIT__, UnaryFuncLegacyWrapper::new, MethodsFlags.AM_AWAIT),
    AM_AITER(PyAsyncMethods__am_aiter, T___AITER__, UnaryFuncLegacyWrapper::new, MethodsFlags.AM_AITER),
    AM_ANEXT(PyAsyncMethods__am_anext, T___ANEXT__, PyProcsWrapper.UnaryFuncLegacyWrapper::new, MethodsFlags.AM_ANEXT);
    // (mq) AM_SEND is an internal function and mostly called from within AWAIT, AITER, ANEXT.
    /*-  AM_SEND(PyAsyncMethods__am_send, ASYNC_AM_SEND, CallFunctionWrapper::new, MethodsFlags.AM_SEND), */

    public final TruffleString methodName;
    public final Function<Object, PyProcsWrapper> wrapperFactory;
    public final long methodFlag;

    @CompilationFinal public CFields typeField;
    @CompilationFinal public CFields methodsField;

    /**
     * Different slot that is C-compatible and maps to the same Python method.
     */
    @CompilationFinal public SlotMethodDef overlappingSlot;

    SlotMethodDef(CFields typeField, TruffleString methodName, Function<Object, PyProcsWrapper> wrapperFactory) {
        this(typeField, null, methodName, wrapperFactory, 0);
    }

    SlotMethodDef(CFields typeField, TruffleString methodName, Function<Object, PyProcsWrapper> wrapperFactory, long methodFlag) {
        this(typeField, null, methodName, wrapperFactory, methodFlag);
    }

    SlotMethodDef(CFields typeField, CFields methodsField, TruffleString methodName, Function<Object, PyProcsWrapper> wrapperFactory, long methodFlag) {
        this.typeField = typeField;
        this.methodsField = methodsField;
        this.methodName = methodName;
        this.wrapperFactory = wrapperFactory;
        this.methodFlag = methodFlag;
    }
}
