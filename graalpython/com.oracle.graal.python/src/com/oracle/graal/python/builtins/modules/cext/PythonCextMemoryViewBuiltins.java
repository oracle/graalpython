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

import static com.oracle.graal.python.annotations.ArgDescriptor.CHAR;
import static com.oracle.graal.python.annotations.ArgDescriptor.Int;
import static com.oracle.graal.python.annotations.ArgDescriptor.PY_BUFFER_PTR;
import static com.oracle.graal.python.annotations.ArgDescriptor.PyObject;
import static com.oracle.graal.python.annotations.ArgDescriptor.Void;
import static com.oracle.graal.python.builtins.PythonBuiltinClassType.BufferError;
import static com.oracle.graal.python.builtins.modules.cext.PythonCextBuiltins.CApiCallPath.Direct;
import static com.oracle.graal.python.builtins.modules.cext.PythonCextBuiltins.CApiCallPath.Ignored;
import static com.oracle.graal.python.builtins.objects.cext.structs.CStructAccess.writeIntField;
import static com.oracle.graal.python.builtins.objects.cext.structs.CStructAccess.writePtrField;
import static com.oracle.graal.python.nodes.ErrorMessages.UNDERLYING_BUFFER_IS_NOT_WRITABLE;
import static com.oracle.graal.python.nodes.ErrorMessages.WRITABLE_CONTIGUES_FOR_NON_CONTIGUOUS;
import static com.oracle.graal.python.runtime.nativeaccess.NativeMemory.NULLPTR;
import static com.oracle.graal.python.util.BufferFormat.T_UINT_8_TYPE_CODE;
import static com.oracle.graal.python.util.PythonUtils.TS_ENCODING;

import com.oracle.graal.python.builtins.modules.cext.PythonCextBuiltins.CApiBinaryBuiltinNode;
import com.oracle.graal.python.builtins.modules.cext.PythonCextBuiltins.CApiBuiltin;
import com.oracle.graal.python.builtins.modules.cext.PythonCextBuiltins.CApiTernaryBuiltinNode;
import com.oracle.graal.python.builtins.modules.cext.PythonCextBuiltins.CApiUnaryBuiltinNode;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.buffer.BufferFlags;
import com.oracle.graal.python.builtins.objects.cext.capi.transitions.CApiTransitions.NativeToPythonNode;
import com.oracle.graal.python.builtins.objects.cext.capi.transitions.CApiTransitions.PythonToNativeInternalNode;
import com.oracle.graal.python.builtins.objects.cext.capi.transitions.CApiTransitions.PythonToNativeNewRefNode;
import com.oracle.graal.python.builtins.objects.cext.capi.transitions.CApiTransitions.PythonToNativeNode;
import com.oracle.graal.python.builtins.objects.cext.structs.CFields;
import com.oracle.graal.python.builtins.objects.cext.structs.CStructs;
import com.oracle.graal.python.builtins.objects.memoryview.MemoryViewBuiltins.CastNode;
import com.oracle.graal.python.builtins.objects.memoryview.MemoryViewBuiltins.ContiguousNode;
import com.oracle.graal.python.builtins.objects.memoryview.MemoryViewBuiltins.ToBytesNode;
import com.oracle.graal.python.builtins.objects.memoryview.MemoryViewNodes.ReleaseNode;
import com.oracle.graal.python.builtins.objects.memoryview.PMemoryView;
import com.oracle.graal.python.lib.PyMemoryViewFromObject;
import com.oracle.graal.python.lib.PyObjectGetBuffer;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.runtime.nativeaccess.NativeMemory;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.strings.TruffleString;

public final class PythonCextMemoryViewBuiltins {

    public static final int PY_BUF_READ = 0x100;
    public static final int PY_BUF_WRITE = 0x200;

    @CApiBuiltin(ret = PY_BUFFER_PTR, args = {PyObject}, call = Ignored, retConversion = long.class, argConversions = {NativeToPythonNode.class})
    abstract static class GraalPyPrivate_MemoryViewBuffer extends CApiUnaryBuiltinNode {
        @Specialization
        static long get(PMemoryView self) {
            return self.getNativeBuffer();
        }
    }

    @CApiBuiltin(ret = PyObject, args = {PyObject}, call = Ignored, retConversion = PythonToNativeNode.class, argConversions = {NativeToPythonNode.class})
    abstract static class GraalPyPrivate_MemoryViewBase extends CApiUnaryBuiltinNode {
        @Specialization
        static Object get(PMemoryView self) {
            return self.getOwner() == null ? PNone.NO_VALUE : self.getOwner();
        }
    }

    @CApiBuiltin(ret = Int, args = {PyObject}, call = Ignored, retConversion = int.class, argConversions = {NativeToPythonNode.class})
    abstract static class GraalPyPrivate_MemoryViewIsReleased extends CApiUnaryBuiltinNode {
        @Specialization
        static int get(PMemoryView self) {
            return self.isReleased() ? 1 : 0;
        }
    }

    @CApiBuiltin(ret = Int, args = {PyObject, PY_BUFFER_PTR, Int}, call = Ignored, retConversion = int.class, argConversions = {NativeToPythonNode.class, long.class, int.class})
    abstract static class GraalPyPrivate_MemoryViewGetBuffer extends CApiTernaryBuiltinNode {
        @Specialization
        static int get(PMemoryView self, long view, int flags,
                        @Cached PyObjectGetBuffer getBuffer) {
            getBuffer.execute(null, self, flags);
            boolean success = false;
            try {
                NativeMemory.memcpy(view, self.getNativeBuffer(), CStructs.Py_buffer.size());
                writePtrField(view, CFields.Py_buffer__obj, NULLPTR);
                if (!BufferFlags.requestsFormat(flags)) {
                    writePtrField(view, CFields.Py_buffer__format, NULLPTR);
                }
                if (!BufferFlags.requestsStrides(flags)) {
                    writePtrField(view, CFields.Py_buffer__strides, NULLPTR);
                }
                if (!BufferFlags.requestsShape(flags)) {
                    writeIntField(view, CFields.Py_buffer__ndim, 1);
                    writePtrField(view, CFields.Py_buffer__shape, NULLPTR);
                }
                writePtrField(view, CFields.Py_buffer__obj, PythonToNativeInternalNode.executeNewRefUncached(self));
                success = true;
                return 0;
            } finally {
                if (!success) {
                    self.getExports().decrementAndGet();
                }
            }
        }
    }

    @CApiBuiltin(ret = Void, args = {PyObject, PY_BUFFER_PTR}, call = Ignored, retConversion = void.class, argConversions = {NativeToPythonNode.class, long.class})
    abstract static class GraalPyPrivate_MemoryViewReleaseBuffer extends CApiBinaryBuiltinNode {
        @Specialization
        static Object release(PMemoryView self, @SuppressWarnings("unused") long view) {
            long exports = self.getExports().decrementAndGet();
            assert exports >= 0;
            return PNone.NO_VALUE;
        }
    }

    @CApiBuiltin(ret = PyObject, args = {PyObject, Int, CHAR}, call = Direct, retConversion = PythonToNativeNewRefNode.class, argConversions = {NativeToPythonNode.class, int.class,
                    byte.class})
    abstract static class PyMemoryView_GetContiguous extends CApiTernaryBuiltinNode {
        @Specialization()
        static Object get(Object obj, int buffertype, byte orderByte,
                        @Bind Node inliningTarget,
                        @Cached PyMemoryViewFromObject memoryViewFromObject,
                        @Cached ReleaseNode releaseNode,
                        @Cached ToBytesNode toBytesNode,
                        @Cached CastNode castNode,
                        @Cached ContiguousNode contiguousNode,
                        @Cached TruffleString.EqualNode eqNode,
                        @Cached TruffleString.FromCodePointNode fromCodePointNode,
                        @Cached PRaiseNode raiseNode) {
            assert buffertype == PY_BUF_READ || buffertype == PY_BUF_WRITE;
            char order = (char) orderByte;
            assert order == 'C' || order == 'F' || order == 'A';

            PMemoryView mv = memoryViewFromObject.execute(null, obj);
            boolean release = true;
            try {
                if (buffertype == PY_BUF_WRITE && mv.isReadOnly()) {
                    throw raiseNode.raise(inliningTarget, BufferError, UNDERLYING_BUFFER_IS_NOT_WRITABLE);
                }
                if ((boolean) contiguousNode.execute(null, mv)) {
                    release = false;
                    return mv;
                }
                if (buffertype == PY_BUF_WRITE) {
                    throw raiseNode.raise(inliningTarget, BufferError, WRITABLE_CONTIGUES_FOR_NON_CONTIGUOUS);
                }
                PMemoryView mvBytes = memoryViewFromObject.execute(null, toBytesNode.execute(null, mv, fromCodePointNode.execute(order, TS_ENCODING, true)));
                if (eqNode.execute(T_UINT_8_TYPE_CODE, mv.getFormatString(), TS_ENCODING)) {
                    return mvBytes;
                } else {
                    try {
                        return castNode.execute(null, mvBytes, mv.getFormatString(), PNone.NONE);
                    } finally {
                        releaseNode.execute(mvBytes);
                    }
                }
            } finally {
                if (release && mv != null) {
                    releaseNode.execute(mv);
                }
            }
        }
    }

    @CApiBuiltin(ret = PyObject, args = {PyObject}, call = Direct, retConversion = PythonToNativeNewRefNode.class, argConversions = {NativeToPythonNode.class})
    abstract static class PyMemoryView_FromObject extends CApiUnaryBuiltinNode {
        @Specialization
        static Object wrap(Object object,
                        @Cached PyMemoryViewFromObject memoryViewNode) {
            return memoryViewNode.execute(null, object);
        }
    }
}
