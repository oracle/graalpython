/*
 * Copyright (c) 2023, 2026, Oracle and/or its affiliates. All rights reserved.
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
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.CHAR_PTR;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.Int;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.PY_BUFFER_PTR;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.PyObjectRawPointer;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.Py_ssize_t;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.Void;
import static com.oracle.graal.python.builtins.objects.cext.structs.CStructAccess.writeIntField;
import static com.oracle.graal.python.builtins.objects.cext.structs.CStructAccess.writeLongField;
import static com.oracle.graal.python.builtins.objects.cext.structs.CStructAccess.writePtrField;
import static com.oracle.graal.python.nfi2.NativeMemory.NULLPTR;
import static com.oracle.graal.python.nfi2.NativeMemory.calloc;
import static com.oracle.graal.python.nfi2.NativeMemory.free;
import static com.oracle.graal.python.nfi2.NativeMemory.malloc;

import com.oracle.graal.python.builtins.modules.cext.PythonCextBuiltins.CApiBuiltin;
import com.oracle.graal.python.builtins.objects.array.ArrayNodes;
import com.oracle.graal.python.builtins.objects.array.PArray;
import com.oracle.graal.python.builtins.objects.buffer.BufferFlags;
import com.oracle.graal.python.builtins.objects.cext.capi.transitions.CApiTransitions.NativeToPythonNode;
import com.oracle.graal.python.builtins.objects.cext.capi.transitions.CApiTransitions.PythonToNativeNewRefNode;
import com.oracle.graal.python.builtins.objects.cext.structs.CFields;
import com.oracle.graal.python.builtins.objects.cext.structs.CStructAccess;
import com.oracle.graal.python.builtins.objects.cext.structs.CStructAccess.WriteTruffleStringNode;
import com.oracle.graal.python.nfi2.NativeMemory;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.strings.TruffleString;

public final class PythonCextArrayBuiltins {

    /**
     * Graalpy-specific function implemented for Cython
     */
    @CApiBuiltin(ret = Int, args = {PyObjectRawPointer, Py_ssize_t}, call = Direct)
    static int GraalPyArray_Resize(long arrayPtr, long newSize) {
        PArray array = expectArray(arrayPtr, "GraalPyArray_Resize");
        ArrayNodes.EnsureCapacityNode.executeUncached(array, (int) newSize);
        ArrayNodes.SetLengthNode.executeUncached(array, (int) newSize);
        return 0;
    }

    @CApiBuiltin(ret = CHAR_PTR, args = {PyObjectRawPointer}, call = Direct)
    static long GraalPyArray_Data(long arrayPtr) {
        PArray array = expectArray(arrayPtr, "GraalPyArray_Data");
        if (array.getBytesLength() > 0) {
            return ArrayNodes.EnsureNativeStorageNode.executeUncached(array).getPtr();
        }
        return NULLPTR;
    }

    @CApiBuiltin(ret = Int, args = {PyObjectRawPointer, PY_BUFFER_PTR, Int}, call = Ignored)
    static int GraalPyPrivate_Array_getbuffer(long arrayPtr, long pyBufferPtr, int flags) {
        PArray array = expectArray(arrayPtr, "GraalPyPrivate_Array_getbuffer");
        long bufPtr = ArrayNodes.EnsureNativeStorageNode.executeUncached(array).getPtr();
        writePtrField(pyBufferPtr, CFields.Py_buffer__buf, bufPtr);
        writePtrField(pyBufferPtr, CFields.Py_buffer__obj, PythonToNativeNewRefNode.executeLongUncached(array));
        writeLongField(pyBufferPtr, CFields.Py_buffer__len, array.getBytesLength());
        writeIntField(pyBufferPtr, CFields.Py_buffer__readonly, 0);
        writeIntField(pyBufferPtr, CFields.Py_buffer__ndim, 1);
        writeLongField(pyBufferPtr, CFields.Py_buffer__itemsize, array.getFormat().bytesize);
        writePtrField(pyBufferPtr, CFields.Py_buffer__suboffsets, NULLPTR);
        long shapePtr = NULLPTR;
        if ((flags & BufferFlags.PyBUF_ND) == BufferFlags.PyBUF_ND) {
            shapePtr = malloc(Long.BYTES);
            NativeMemory.writeLong(shapePtr, array.getLength());
        }
        writePtrField(pyBufferPtr, CFields.Py_buffer__shape, shapePtr);
        long stridesPtr = NULLPTR;
        if ((flags & BufferFlags.PyBUF_STRIDES) == BufferFlags.PyBUF_STRIDES) {
            stridesPtr = malloc(Long.BYTES);
            NativeMemory.writeLong(stridesPtr, array.getFormat().bytesize);
        }
        writePtrField(pyBufferPtr, CFields.Py_buffer__strides, stridesPtr);
        long formatPtr = NULLPTR;
        if ((flags & BufferFlags.PyBUF_FORMAT) == BufferFlags.PyBUF_FORMAT) {
            TruffleString format = array.getFormatString();
            // TODO wchar_t check
            TruffleString.Encoding formatEncoding = TruffleString.Encoding.US_ASCII;
            format = format.switchEncodingUncached(formatEncoding);
            int formatLen = format.byteLength(formatEncoding);
            formatPtr = calloc(formatLen + 1);
            WriteTruffleStringNode.writeUncached(formatPtr, format, formatEncoding);
        }
        writePtrField(pyBufferPtr, CFields.Py_buffer__format, formatPtr);
        writePtrField(pyBufferPtr, CFields.Py_buffer__internal, NULLPTR);

        array.getExports().incrementAndGet();
        return 0;
    }

    @CApiBuiltin(ret = Void, args = {PyObjectRawPointer, PY_BUFFER_PTR}, call = Ignored)
    static void GraalPyPrivate_Array_releasebuffer(long arrayPtr, long pyBufferPtr) {
        PArray array = expectArray(arrayPtr, "GraalPyPrivate_Array_releasebuffer");
        array.getExports().decrementAndGet();
        free(CStructAccess.readPtrField(pyBufferPtr, CFields.Py_buffer__shape));
        free(CStructAccess.readPtrField(pyBufferPtr, CFields.Py_buffer__strides));
        free(CStructAccess.readPtrField(pyBufferPtr, CFields.Py_buffer__format));
    }

    private static PArray expectArray(long arrayPtr, String where) {
        Object obj = NativeToPythonNode.executeRawUncached(arrayPtr);
        if (CompilerDirectives.injectBranchProbability(CompilerDirectives.UNLIKELY_PROBABILITY, !(obj instanceof PArray))) {
            throw PythonCextBuiltins.badInternalCall(where, "arrayPtr");
        }
        return (PArray) obj;
    }
}
