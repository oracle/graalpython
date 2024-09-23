/*
 * Copyright (c) 2023, 2024, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.builtins.objects.cext.structs;

import com.oracle.graal.python.annotations.CApiStructs;
import com.oracle.graal.python.builtins.objects.cext.capi.CExtNodes.PCallCapiFunction;
import com.oracle.graal.python.builtins.objects.cext.capi.NativeCAPISymbol;
import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;

@CApiStructs
public enum CStructs {
    PyModuleDef,
    PyModuleDef_Slot,
    PyMethodDef,
    PyObject,
    GraalPyObject,
    PyBytesObject,
    PyByteArrayObject,
    PyListObject,
    PyVarObject,
    GraalPyVarObject,
    PyMemoryViewObject,
    Py_buffer,
    PyDateTime_CAPI,
    PyNumberMethods,
    PySequenceMethods,
    PyMappingMethods,
    PyAsyncMethods,
    PyBufferProcs,
    PyTypeObject,
    PyHeapTypeObject,
    PyTupleObject,
    PyFloatObject,
    GraalPyFloatObject,
    PyLongObject,
    PyModuleDef_Base,
    Py_complex,
    PyComplexObject,
    PyDateTime_Date,
    PyDateTime_Time,
    PyDateTime_DateTime,
    PyDateTime_Delta,
    PyASCIIObject,
    PyCompactUnicodeObject,
    PyBaseExceptionObject,
    PyUnicodeObject,
    Py_UNICODE,
    PyGetSetDef,
    PyMemberDef,
    PyThreadState,
    wchar_t,
    long__long,
    Py_ssize_t,
    GCState,
    PyGC_Head,
    GraalPyGC_CycleNode,
    GCGeneration;

    @CompilationFinal(dimensions = 1) public static final CStructs[] VALUES = values();

    @CompilationFinal private int size = -1;

    public int size() {
        int o = size;
        if (o == -1) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            resolve();
            return size;
        }
        return o;
    }

    private static void resolve() {
        CompilerAsserts.neverPartOfCompilation();
        Object sizesPointer = PCallCapiFunction.callUncached(NativeCAPISymbol.FUN_PYTRUFFLE_STRUCT_SIZES);
        long[] sizes = CStructAccessFactory.ReadI64NodeGen.getUncached().readLongArray(sizesPointer, VALUES.length);
        for (CStructs struct : VALUES) {
            long size = sizes[struct.ordinal()];
            assert size > 0 && size < 1024;
            struct.size = (int) size;
        }
    }
}
