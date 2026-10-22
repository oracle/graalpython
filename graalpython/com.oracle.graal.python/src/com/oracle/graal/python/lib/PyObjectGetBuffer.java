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
package com.oracle.graal.python.lib;

import static com.oracle.graal.python.builtins.PythonBuiltinClassType.BufferError;
import static com.oracle.graal.python.builtins.PythonBuiltinClassType.TypeError;
import static com.oracle.graal.python.builtins.PythonBuiltinClassType.ValueError;

import com.oracle.graal.python.builtins.modules.io.PBytesIOBuffer;
import com.oracle.graal.python.builtins.modules.pickle.PPickleBuffer;
import com.oracle.graal.python.builtins.objects.array.PArray;
import com.oracle.graal.python.builtins.objects.buffer.BufferFlags;
import com.oracle.graal.python.builtins.objects.buffer.ForeignBufferAdapter;
import com.oracle.graal.python.builtins.objects.buffer.PythonBufferAccessLibrary;
import com.oracle.graal.python.builtins.objects.bytes.PByteArray;
import com.oracle.graal.python.builtins.objects.bytes.PBytes;
import com.oracle.graal.python.builtins.objects.cext.PythonAbstractNativeObject;
import com.oracle.graal.python.builtins.objects.cext.capi.CExtNodes;
import com.oracle.graal.python.builtins.objects.memoryview.PMemoryView;
import com.oracle.graal.python.builtins.objects.mmap.PMMap;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PNodeWithContext;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.object.IsForeignObjectNode;
import com.oracle.graal.python.nodes.util.CastToJavaIntExactNode;
import com.oracle.graal.python.nodes.util.LazyInteropLibrary;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Exclusive;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.GenerateInline;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.NeverDefault;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.nodes.Node;

/**
 * Equivalent of CPython's {@code PyObject_GetBuffer}. The returned buffer must be accessed and
 * released using {@link PythonBufferAccessLibrary}.
 */
@GenerateUncached
@GenerateInline(false)
public abstract class PyObjectGetBuffer extends PNodeWithContext {
    /**
     * The flags are defined in {@link BufferFlags}. Managed buffers typically return themselves,
     * so callers must ignore format, shape and strides when the flags do not request them.
     */
    public abstract Object execute(VirtualFrame frame, Object object, int flags);

    public final Object executeReadonly(VirtualFrame frame, Object object) {
        return execute(frame, object, BufferFlags.PyBUF_SIMPLE);
    }

    public final Object executeWritable(VirtualFrame frame, Object object) {
        return execute(frame, object, BufferFlags.PyBUF_WRITABLE);
    }

    @Specialization
    static Object doBytes(PBytes object, int flags,
                    @Bind Node inliningTarget,
                    @Exclusive @Cached PRaiseNode raiseNode) {
        if (BufferFlags.requestsWritable(flags)) {
            throw raiseNode.raise(inliningTarget, BufferError, ErrorMessages.OBJ_IS_NOT_WRITABLE);
        }
        return object;
    }

    @Specialization
    static Object doPByteArray(PByteArray object, @SuppressWarnings("unused") int flags) {
        return object;
    }

    @Specialization
    static Object doPArray(PArray object, @SuppressWarnings("unused") int flags) {
        return object;
    }

    @Specialization
    static Object doPMMap(PMMap object, @SuppressWarnings("unused") int flags) {
        return object;
    }

    @Specialization
    static Object doMemoryView(PMemoryView object, int requestedFlags,
                    @Bind Node inliningTarget,
                    @Exclusive @Cached PRaiseNode raiseNode) {
        object.checkReleased(inliningTarget, raiseNode);
        if (BufferFlags.requestsWritable(requestedFlags) && object.isReadOnly()) {
            throw raiseNode.raise(inliningTarget, BufferError, ErrorMessages.MV_UNDERLYING_BUF_ISNT_WRITABLE);
        }
        if (BufferFlags.requestsCContiguous(requestedFlags) && !object.isCContiguous()) {
            throw raiseNode.raise(inliningTarget, BufferError, ErrorMessages.MV_UNDERLYING_BUF_ISNT_C_CONTIGUOUS);
        }
        if (BufferFlags.requestsFContiguous(requestedFlags) && !object.isFortranContiguous()) {
            throw raiseNode.raise(inliningTarget, BufferError, ErrorMessages.MV_UNDERLYING_BUF_ISNT_FORTRAN_CONTIGUOUS);
        }
        if (BufferFlags.requestsAnyContiguous(requestedFlags) && !object.isAnyContiguous()) {
            throw raiseNode.raise(inliningTarget, BufferError, ErrorMessages.MV_UNDERLYING_BUF_ISNT_CONTIGUOUS);
        }
        if (!BufferFlags.requestsIndirect(requestedFlags) && (object.getFlags() & PMemoryView.FLAG_PIL) != 0) {
            throw raiseNode.raise(inliningTarget, BufferError, ErrorMessages.MV_UNDERLYING_BUF_REQUIRES_SUBOFFSETS);
        }
        if (!BufferFlags.requestsStrides(requestedFlags) && !object.isCContiguous()) {
            throw raiseNode.raise(inliningTarget, BufferError, ErrorMessages.MV_UNDERLYING_BUF_ISNT_C_CONTIGUOUS);
        }
        // TODO should reflect the cast to unsigned bytes if necessary
        if (!BufferFlags.requestsShape(requestedFlags) && BufferFlags.requestsFormat(requestedFlags)) {
            throw raiseNode.raise(inliningTarget, BufferError, ErrorMessages.MV_CANNOT_CAST_UNSIGNED_BYTES_IF_FMT_FLAG);
        }
        object.getExports().incrementAndGet();
        return object;
    }

    @Specialization
    static Object doNative(VirtualFrame frame, PythonAbstractNativeObject object, int flags,
                    @Bind Node inliningTarget,
                    @Cached CExtNodes.CreateMemoryViewFromNativeNode createMemoryView) {
        PMemoryView view = createMemoryView.execute(frame, inliningTarget, object, flags);
        view.setShouldReleaseImmediately(true);
        return view;
    }

    @Specialization
    static Object doBytesIO(PBytesIOBuffer object, @SuppressWarnings("unused") int flags) {
        return object.getDelegate();
    }

    @Specialization
    static Object doPickleBuffer(VirtualFrame frame, PPickleBuffer object, int flags,
                    @Bind Node inliningTarget,
                    @CachedLibrary(limit = "3") PythonBufferAccessLibrary bufferLib,
                    @Exclusive @Cached PyObjectGetBuffer recursive,
                    @Exclusive @Cached PRaiseNode raiseNode) {
        Object owner = null;
        if (object.getView() != null) {
            owner = bufferLib.getOwner(object.getView());
        }
        if (owner == null) {
            throw raiseNode.raise(inliningTarget, ValueError, ErrorMessages.OP_FORBIDDEN_ON_OBJECT, "PickleBuffer");
        }
        return recursive.execute(frame, owner, flags);
    }

    @Fallback
    static Object doOther(Object object, int flags,
                    @Bind Node inliningTarget,
                    @Cached IsForeignObjectNode isForeignObjectNode,
                    @Cached CastToJavaIntExactNode castInt,
                    @Cached LazyInteropLibrary lazyInteropLib) {
        if (isForeignObjectNode.execute(inliningTarget, object)) {
            InteropLibrary interopLib = lazyInteropLib.get(inliningTarget);
            if (interopLib.hasBufferElements(object)) {
                try {
                    long size = interopLib.getBufferSize(object);
                    boolean readonly = !interopLib.isBufferWritable(object);
                    if (BufferFlags.requestsWritable(flags) && readonly) {
                        throw PRaiseNode.raiseStatic(inliningTarget, BufferError, ErrorMessages.OBJ_IS_NOT_WRITABLE);
                    }
                    return new ForeignBufferAdapter(object, castInt.execute(inliningTarget, size), readonly);
                } catch (UnsupportedMessageException e) {
                    throw CompilerDirectives.shouldNotReachHere(e);
                }
            }
        }
        throw PRaiseNode.raiseStatic(inliningTarget, TypeError, ErrorMessages.BYTESLIKE_OBJ_REQUIRED, object);
    }

    @NeverDefault
    public static PyObjectGetBuffer create() {
        return PyObjectGetBufferNodeGen.create();
    }

    public static PyObjectGetBuffer getUncached() {
        return PyObjectGetBufferNodeGen.getUncached();
    }
}
