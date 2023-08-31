/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.PyObject;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.Py_ssize_t;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.Void;

import com.oracle.graal.python.builtins.modules.cext.PythonCextBuiltins.CApiBinaryBuiltinNode;
import com.oracle.graal.python.builtins.modules.cext.PythonCextBuiltins.CApiBuiltin;
import com.oracle.graal.python.builtins.modules.cext.PythonCextBuiltins.CApiTernaryBuiltinNode;
import com.oracle.graal.python.builtins.modules.cext.PythonCextBuiltins.CApiUnaryBuiltinNode;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.array.ArrayNodes;
import com.oracle.graal.python.builtins.objects.array.PArray;
import com.oracle.graal.python.builtins.objects.buffer.BufferFlags;
import com.oracle.graal.python.builtins.objects.cext.capi.transitions.CApiTransitions;
import com.oracle.graal.python.builtins.objects.cext.structs.CFields;
import com.oracle.graal.python.builtins.objects.cext.structs.CStructAccess;
import com.oracle.graal.python.builtins.objects.common.SequenceStorageNodes.StorageToNativeNode;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.graal.python.runtime.sequence.storage.ByteSequenceStorage;
import com.oracle.graal.python.runtime.sequence.storage.NativeByteSequenceStorage;
import com.oracle.graal.python.runtime.sequence.storage.NativeSequenceStorage;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.strings.TruffleString;

public final class PythonCextArrayBuiltins {

    /**
     * Graalpy-specific function implemented for Cython
     */
    @CApiBuiltin(ret = Int, args = {PyObject, Py_ssize_t}, call = Direct)
    abstract static class _PyArray_Resize extends CApiBinaryBuiltinNode {
        @Specialization
        int resize(PArray object, long newSize,
                        @Bind("this") Node inliningTarget,
                        @Cached ArrayNodes.EnsureCapacityNode ensureCapacityNode,
                        @Cached ArrayNodes.SetLengthNode setLengthNode) {
            ensureCapacityNode.execute(inliningTarget, object, (int) newSize);
            setLengthNode.execute(inliningTarget, object, (int) newSize);
            return 0;
        }
    }

    @CApiBuiltin(ret = CHAR_PTR, args = {PyObject}, call = Direct)
    abstract static class _PyArray_Data extends CApiUnaryBuiltinNode {
        @Specialization
        Object get(PArray object,
                        @Cached StorageToNativeNode storageToNativeNode) {
            if (object.getSequenceStorage() instanceof NativeByteSequenceStorage storage) {
                return storage.getPtr();
            } else if (object.getSequenceStorage() instanceof ByteSequenceStorage storage) {
                NativeSequenceStorage nativeStorage = storageToNativeNode.execute(storage.getInternalByteArray(), storage.length());
                return nativeStorage.getPtr();
            }
            throw CompilerDirectives.shouldNotReachHere("invalid storage for PArray");
        }
    }

    @CApiBuiltin(ret = Int, args = {PyObject, PY_BUFFER_PTR, Int}, call = Ignored)
    abstract static class PyTruffle_Array_getbuffer extends CApiTernaryBuiltinNode {
        @Specialization
        static int getbuffer(PArray array, Object pyBufferPtr, int flags,
                        @Bind("this") Node inliningTarget,
                        @Cached ArrayNodes.EnsureNativeStorage ensureNativeStorage,
                        @Cached TruffleString.SwitchEncodingNode switchEncodingNode,
                        @Cached TruffleString.CopyToByteArrayNode copyToByteArrayNode,
                        @Cached CApiTransitions.PythonToNativeNewRefNode toNativeNewRefNode,
                        @Cached CStructAccess.WritePointerNode writePointerNode,
                        @Cached CStructAccess.WriteLongNode writeLongNode,
                        @Cached CStructAccess.WriteIntNode writeIntNode,
                        @Cached CStructAccess.WriteByteNode writeByteNode,
                        @Cached CStructAccess.AllocateNode allocateNode) {
            Object bufPtr = ensureNativeStorage.execute(inliningTarget, array).getPtr();
            Object nativeNull = PythonContext.get(inliningTarget).getNativeNull().getPtr();
            writePointerNode.write(pyBufferPtr, CFields.Py_buffer__buf, bufPtr);
            writePointerNode.write(pyBufferPtr, CFields.Py_buffer__obj, toNativeNewRefNode.execute(array));
            writeLongNode.write(pyBufferPtr, CFields.Py_buffer__len, array.getLength() * array.getFormat().bytesize);
            writeIntNode.write(pyBufferPtr, CFields.Py_buffer__readonly, 0);
            writeIntNode.write(pyBufferPtr, CFields.Py_buffer__ndim, 1);
            writeLongNode.write(pyBufferPtr, CFields.Py_buffer__itemsize, array.getFormat().bytesize);
            writePointerNode.write(pyBufferPtr, CFields.Py_buffer__suboffsets, nativeNull);
            Object shapePtr = nativeNull;
            if ((flags & BufferFlags.PyBUF_ND) == BufferFlags.PyBUF_ND) {
                shapePtr = allocateNode.alloc(Long.BYTES);
                writeLongNode.write(shapePtr, array.getLength());
            }
            writePointerNode.write(pyBufferPtr, CFields.Py_buffer__shape, shapePtr);
            Object stridesPtr = nativeNull;
            if ((flags & BufferFlags.PyBUF_STRIDES) == BufferFlags.PyBUF_STRIDES) {
                stridesPtr = allocateNode.alloc(Long.BYTES);
                writeLongNode.write(stridesPtr, array.getFormat().bytesize);
            }
            writePointerNode.write(pyBufferPtr, CFields.Py_buffer__strides, stridesPtr);
            Object formatPtr = nativeNull;
            if (((flags & BufferFlags.PyBUF_FORMAT) == BufferFlags.PyBUF_FORMAT)) {
                TruffleString format = array.getFormatString();
                // TODO wchar_t check
                TruffleString.Encoding formatEncoding = TruffleString.Encoding.US_ASCII;
                format = switchEncodingNode.execute(format, formatEncoding);
                int formatLen = format.byteLength(formatEncoding);
                byte[] bytes = new byte[formatLen + 1];
                copyToByteArrayNode.execute(format, 0, bytes, 0, formatLen, formatEncoding);
                formatPtr = allocateNode.alloc(bytes.length);
                writeByteNode.writeByteArray(formatPtr, bytes);
            }
            writePointerNode.write(pyBufferPtr, CFields.Py_buffer__format, formatPtr);
            writePointerNode.write(pyBufferPtr, CFields.Py_buffer__internal, nativeNull);

            array.getExports().incrementAndGet();
            return 0;
        }
    }

    @CApiBuiltin(ret = Void, args = {PyObject, PY_BUFFER_PTR}, call = Ignored)
    abstract static class PyTruffle_Array_releasebuffer extends CApiBinaryBuiltinNode {
        @Specialization
        static Object releasebuffer(PArray array, Object pyBufferPtr,
                        @CachedLibrary(limit = "1") InteropLibrary lib,
                        @Cached CStructAccess.ReadPointerNode readPointerNode,
                        @Cached CStructAccess.FreeNode freeNode) {
            array.getExports().decrementAndGet();
            freeArrayField(pyBufferPtr, CFields.Py_buffer__shape, readPointerNode, lib, freeNode);
            freeArrayField(pyBufferPtr, CFields.Py_buffer__strides, readPointerNode, lib, freeNode);
            freeArrayField(pyBufferPtr, CFields.Py_buffer__format, readPointerNode, lib, freeNode);
            return PNone.NO_VALUE;
        }

        private static void freeArrayField(Object pyBufferPtr, CFields cfield, CStructAccess.ReadPointerNode readPointerNode, InteropLibrary lib, CStructAccess.FreeNode freeNode) {
            Object field = readPointerNode.read(pyBufferPtr, cfield);
            if (!lib.isNull(field)) {
                freeNode.free(field);
            }
        }
    }
}
