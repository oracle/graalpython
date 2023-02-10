/*
 * Copyright (c) 2020, 2022, Oracle and/or its affiliates. All rights reserved.
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
import com.oracle.graal.python.builtins.objects.cext.capi.CExtNodes.PCallCapiFunction;
import com.oracle.graal.python.builtins.objects.cext.capi.NativeCAPISymbol;
import com.oracle.graal.python.builtins.objects.complex.PComplex;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Exclusive;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.interop.InteropException;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.nodes.Node;

/**
 * Writes to an output variable in the varargs by doing the necessary native typing and
 * dereferencing. This is mostly like
 *
 * <pre>
 *     SomeType *outVar = va_arg(valist, SomeType *);
 *     *outVar = value;
 * </pre>
 *
 * It is important to use the appropriate {@code write*} functions!
 */
@GenerateUncached
@ImportStatic(LLVMType.class)
public abstract class WriteNextVaArgNode extends Node {

    public final void writeUInt8(Object valist, Object value) throws InteropException {
        execute(valist, LLVMType.uint8_ptr_t, value);
    }

    public final void writeInt8(Object valist, Object value) throws InteropException {
        execute(valist, LLVMType.int8_ptr_t, value);
    }

    public final void writeUInt16(Object valist, Object value) throws InteropException {
        execute(valist, LLVMType.uint16_ptr_t, value);
    }

    public final void writeInt16(Object valist, Object value) throws InteropException {
        execute(valist, LLVMType.int16_ptr_t, value);
    }

    public final void writeUInt32(Object valist, Object value) throws InteropException {
        execute(valist, LLVMType.uint32_ptr_t, value);
    }

    public final void writeInt32(Object valist, Object value) throws InteropException {
        execute(valist, LLVMType.int32_ptr_t, value);
    }

    public final void writeUInt64(Object valist, Object value) throws InteropException {
        execute(valist, LLVMType.uint64_ptr_t, value);
    }

    public final void writeInt64(Object valist, Object value) throws InteropException {
        execute(valist, LLVMType.int64_ptr_t, value);
    }

    public final void writePySsizeT(Object valist, Object value) throws InteropException {
        execute(valist, LLVMType.Py_ssize_ptr_t, value);
    }

    public final void writeFloat(Object valist, Object value) throws InteropException {
        execute(valist, LLVMType.float_ptr_t, value);
    }

    public final void writeDouble(Object valist, Object value) throws InteropException {
        execute(valist, LLVMType.double_ptr_t, value);
    }

    public final void writePyObject(Object valist, Object value) throws InteropException {
        execute(valist, LLVMType.PyObject_ptr_ptr_t, value);
    }

    public final void writeComplex(Object valist, PComplex value) throws InteropException {
        execute(valist, LLVMType.Py_complex_ptr_t, value);
    }

    public abstract void execute(Object valist, LLVMType accessType, Object value) throws InteropException;

    @Specialization(guards = "accessType != Py_complex_ptr_t")
    static void doPointer(Object valist, @SuppressWarnings("unused") LLVMType accessType, Object value,
                    @Cached PCallCapiFunction nextNode,
                    @Exclusive @CachedLibrary(limit = "1") InteropLibrary outVarPtrLib,
                    @Shared("getLLVMType") @Cached GetLLVMType getLLVMTypeNode) {
        try {
            // like 'some_type* out_var = va_arg(vl, some_type*)'
            Object outVarPtr = nextNode.call(NativeCAPISymbol.GRAALVM_LLVM_VA_ARG, valist, getLLVMTypeNode.execute(accessType));
            // like 'out_var[0] = value'
            outVarPtrLib.writeArrayElement(outVarPtr, 0, value);
        } catch (InteropException e) {
            throw CompilerDirectives.shouldNotReachHere(e);
        }
    }

    @Specialization(guards = "accessType == Py_complex_ptr_t")
    static void doComplex(Object valist, @SuppressWarnings("unused") LLVMType accessType, PComplex value,
                    @Cached PCallCapiFunction nextNode,
                    @Exclusive @CachedLibrary(limit = "1") InteropLibrary outVarPtrLib,
                    @Shared("getLLVMType") @Cached GetLLVMType getLLVMTypeNode) {
        try {
            // like 'some_type* out_var = va_arg(vl, some_type*)'
            Object outVarPtr = nextNode.call(NativeCAPISymbol.GRAALVM_LLVM_VA_ARG, valist, getLLVMTypeNode.execute(accessType));
            outVarPtrLib.writeMember(outVarPtr, "real", value.getReal());
            outVarPtrLib.writeMember(outVarPtr, "img", value.getImag());
        } catch (InteropException e) {
            throw CompilerDirectives.shouldNotReachHere(e);
        }
    }
}
