/*
 * Copyright (c) 2018, 2026, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.builtins.objects.memoryview;

import static com.oracle.graal.python.builtins.objects.cext.structs.CStructAccess.writeIntField;
import static com.oracle.graal.python.builtins.objects.cext.structs.CStructAccess.writeLongField;
import static com.oracle.graal.python.builtins.objects.cext.structs.CStructAccess.writePtrField;
import static com.oracle.graal.python.runtime.nativeaccess.NativeMemory.NULLPTR;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.builtins.objects.buffer.PythonBufferAccessLibrary;
import com.oracle.graal.python.builtins.objects.cext.capi.CExtNodes.EnsurePythonObjectNode;
import com.oracle.graal.python.builtins.objects.cext.capi.transitions.CApiTransitions.PythonToNativeInternalNode;
import com.oracle.graal.python.builtins.objects.cext.structs.CFields;
import com.oracle.graal.python.builtins.objects.cext.structs.CStructAccess.WriteTruffleStringNode;
import com.oracle.graal.python.builtins.objects.cext.structs.CStructs;
import com.oracle.graal.python.runtime.AsyncHandler;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.graal.python.runtime.nativeaccess.NativeMemory;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.TruffleLogger;
import com.oracle.truffle.api.strings.TruffleString;

/** Native buffer metadata, owned by the memoryview, without a native replacement object. */
final class NativeMemoryViewBuffer extends AsyncHandler.SharedFinalizer.FinalizableReference {
    private static final TruffleLogger LOGGER = PythonLanguage.getLogger(NativeMemoryViewBuffer.class);

    // Keep the promoted owner alive while Py_buffer.obj holds a borrowed native reference.
    @SuppressWarnings("unused") private final Object ownerWrapper;

    NativeMemoryViewBuffer(PMemoryView memoryView, long pointer, Object ownerWrapper) {
        super(memoryView, pointer, PythonContext.get(null).getSharedFinalizer());
        this.ownerWrapper = ownerWrapper;
    }

    long getPointer() {
        return (long) getReference();
    }

    @Override
    public AsyncHandler.AsyncAction release() {
        NativeMemory.free(getPointer());
        return null;
    }

    @TruffleBoundary
    static NativeMemoryViewBuffer allocate(PMemoryView object) {
        TruffleString format = object.getFormatString();
        TruffleString.Encoding encoding = TruffleString.Encoding.US_ASCII;
        if (format != null) {
            format = format.switchEncodingUncached(encoding);
        }
        long buf = object.getBufferPointer();
        byte[] bufferCopy = null;
        if (object.getBuffer() != null) {
            if (buf == NULLPTR) {
                PythonBufferAccessLibrary bufferLib = PythonBufferAccessLibrary.getUncached();
                buf = bufferLib.getNativePointer(object.getBuffer());
                if (buf == NULLPTR) {
                    /*
                     * It would be more Pythonic to raise an exception rather than to copy, but because this is invoked from macros, there's no practical way of doing it
                     */
                    LOGGER.warning(() -> "Cannot obtain a native buffer pointer for interop buffer; allocating a copy. Writes to the copy will not update the original buffer.");
                    bufferCopy = bufferLib.getCopiedByteArray(object.getBuffer());
                }
            }
        }
        Object ownerWrapper = object.getOwner() == null ? null : EnsurePythonObjectNode.executeUncached(PythonContext.get(null), object.getOwner(), false);
        long owner = ownerWrapper == null ? NULLPTR : PythonToNativeInternalNode.executeUncached(ownerWrapper, false);
        int ndim = object.getDimensions();
        long bufferSize = CStructs.Py_buffer.size();
        long arraySize = (long) ndim * Long.BYTES;
        long formatSize = format == null ? 0 : format.byteLength(encoding) + 1L;
        // Space for the Py_buffer, shape, strides, suboffsets, null-terminated format, and any copy.
        long metadataSize = bufferSize + 3 * arraySize + formatSize;
        long view = NativeMemory.calloc(metadataSize + (bufferCopy == null ? 0 : bufferCopy.length));
        if (bufferCopy != null) {
            buf = view + metadataSize;
            NativeMemory.writeByteArrayElements(buf, 0, bufferCopy, 0, bufferCopy.length);
        }
        if (object.getBuffer() != null) {
            buf += object.getOffset();
        }
        long array = view + CStructs.Py_buffer.size();
        writePtrField(view, CFields.Py_buffer__buf, buf);
        // Borrowed, like CPython's embedded view.obj. This allocation retains the promoted owner.
        writePtrField(view, CFields.Py_buffer__obj, owner);
        writeLongField(view, CFields.Py_buffer__len, object.getLength());
        writeLongField(view, CFields.Py_buffer__itemsize, object.getItemSize());
        writeIntField(view, CFields.Py_buffer__readonly, object.isReadOnly() ? 1 : 0);
        writeIntField(view, CFields.Py_buffer__ndim, ndim);
        writePtrField(view, CFields.Py_buffer__shape, copyArray(array, object.getBufferShape()));
        array += (long) ndim * Long.BYTES;
        writePtrField(view, CFields.Py_buffer__strides, copyArray(array, object.getBufferStrides()));
        array += (long) ndim * Long.BYTES;
        writePtrField(view, CFields.Py_buffer__suboffsets, copyArray(array, object.getBufferSuboffsets()));
        array += (long) ndim * Long.BYTES;
        if (format != null) {
            WriteTruffleStringNode.writeUncached(array, format, encoding);
            writePtrField(view, CFields.Py_buffer__format, array);
        }
        return new NativeMemoryViewBuffer(object, view, ownerWrapper);
    }

    private static long copyArray(long pointer, int[] array) {
        if (array == null) {
            return NULLPTR;
        }
        for (int i = 0; i < array.length; i++) {
            NativeMemory.writeLongArrayElement(pointer, i, array[i]);
        }
        return pointer;
    }
}
