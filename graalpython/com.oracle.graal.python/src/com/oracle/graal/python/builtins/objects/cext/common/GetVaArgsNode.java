/*
 * Copyright (c) 2020, 2021, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.builtins.objects.cext.common;

import com.oracle.graal.python.builtins.objects.cext.capi.CApiContext.LLVMType;
import com.oracle.graal.python.builtins.objects.cext.capi.CExtNodes.GetLLVMType;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.interop.InteropException;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.nodes.Node;

/**
 * Gets the pointer to the outVar at the given index. This is basically an access to the varargs
 * like {@code va_arg(*valist, void *)}
 */
@GenerateUncached
@ImportStatic(LLVMType.class)
public abstract class GetVaArgsNode extends Node {

    public final Object getInt8Ptr(Object valist, int index) throws InteropException {
        return execute(valist, index, LLVMType.int8_ptr_t);
    }

    public final Object getInt16Ptr(Object valist, int index) throws InteropException {
        return execute(valist, index, LLVMType.int16_ptr_t);
    }

    public final Object getInt32Ptr(Object valist, int index) throws InteropException {
        return execute(valist, index, LLVMType.int32_ptr_t);
    }

    public final Object getInt63Ptr(Object valist, int index) throws InteropException {
        return execute(valist, index, LLVMType.int64_ptr_t);
    }

    public final Object getPyObjectPtr(Object valist, int index) throws InteropException {
        return execute(valist, index, LLVMType.PyObject_ptr_t);
    }

    public final Object getCharPtr(Object valist, int index) throws InteropException {
        return execute(valist, index, LLVMType.char_ptr_t);
    }

    public final Object getVoidPtr(Object valist, int index) throws InteropException {
        return execute(valist, index, LLVMType.void_ptr_t);
    }

    public final Object getPyComplexPtr(Object valist, int index) throws InteropException {
        return execute(valist, index, LLVMType.Py_complex_ptr_t);
    }

    public abstract Object execute(Object valist, int index, LLVMType llvmType) throws InteropException;

    @Specialization(limit = "1")
    static Object doGeneric(Object valist, int index, LLVMType llvmType,
                    @CachedLibrary("valist") InteropLibrary valistLib,
                    @Cached GetLLVMType getLLVMType) throws InteropException {
        try {
            return valistLib.invokeMember(valist, "get", index, getLLVMType.execute(llvmType));
        } catch (UnsupportedMessageException e) {
            throw CompilerDirectives.shouldNotReachHere(e);
        }
    }
}
