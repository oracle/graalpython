/*
 * Copyright (c) 2018, 2025, Oracle and/or its affiliates. All rights reserved.
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

import static com.oracle.graal.python.builtins.objects.cext.structs.CFields.PyMemoryViewObject__exports;
import static com.oracle.graal.python.builtins.objects.cext.structs.CFields.PyMemoryViewObject__flags;
import static com.oracle.graal.python.builtins.objects.cext.structs.CFields.PyObject__ob_refcnt;
import static com.oracle.graal.python.builtins.objects.cext.structs.CFields.PyObject__ob_type;
import static com.oracle.graal.python.builtins.objects.cext.structs.CStructAccess.ensurePointerUncached;
import static com.oracle.graal.python.builtins.objects.cext.structs.CStructAccess.getFieldPtr;
import static com.oracle.graal.python.builtins.objects.cext.structs.CStructAccess.writeIntField;
import static com.oracle.graal.python.builtins.objects.cext.structs.CStructAccess.writeLongField;
import static com.oracle.graal.python.builtins.objects.cext.structs.CStructAccess.writePtrField;
import static com.oracle.graal.python.nfi2.NativeMemory.NULLPTR;
import static com.oracle.graal.python.nfi2.NativeMemory.calloc;
import static com.oracle.graal.python.nfi2.NativeMemory.mallocLongArray;
import static com.oracle.graal.python.nfi2.NativeMemory.writeLongArrayElement;
import static com.oracle.truffle.api.CompilerDirectives.shouldNotReachHere;

import com.oracle.graal.python.builtins.objects.buffer.PythonBufferAccessLibrary;
import com.oracle.graal.python.builtins.objects.cext.capi.PythonNativeWrapper.PythonAbstractObjectNativeWrapper;
import com.oracle.graal.python.builtins.objects.cext.capi.transitions.CApiTransitions;
import com.oracle.graal.python.builtins.objects.cext.capi.transitions.CApiTransitions.FirstToNativeNode;
import com.oracle.graal.python.builtins.objects.cext.capi.transitions.CApiTransitions.PythonToNativeNewRefRawNode;
import com.oracle.graal.python.builtins.objects.cext.structs.CFields;
import com.oracle.graal.python.builtins.objects.cext.structs.CStructs;
import com.oracle.graal.python.builtins.objects.ints.PInt;
import com.oracle.graal.python.builtins.objects.memoryview.PMemoryView;
import com.oracle.graal.python.builtins.objects.object.PythonObject;
import com.oracle.graal.python.builtins.objects.type.TypeFlags;
import com.oracle.graal.python.builtins.objects.type.TypeNodes.GetTypeFlagsNode;
import com.oracle.graal.python.nodes.object.GetClassNode.GetPythonObjectClassNode;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.library.ExportMessage.Ignore;
import com.oracle.truffle.api.nodes.Node;

/**
 * Wrapper object for {@code PMemoryView}.
 */
public final class PyMemoryViewWrapper extends PythonAbstractObjectNativeWrapper {
    private long replacement;

    public PyMemoryViewWrapper(PythonObject delegate) {
        super(delegate);
        assert delegate instanceof PMemoryView;
    }

    private static long intArrayToNativePySSizeArray(int[] intArray) {
        long mem = mallocLongArray(intArray.length);
        for (int i = 0; i < intArray.length; i++) {
            writeLongArrayElement(mem, i, intArray[i]);
        }
        return mem;
    }

    @TruffleBoundary
    private static long allocate(PMemoryView object) {
        CExtNodes.AsCharPointerNode asCharPointerNode = CExtNodes.AsCharPointerNode.getUncached();

        Object type = GetPythonObjectClassNode.executeUncached(object);
        boolean gc = (GetTypeFlagsNode.executeUncached(type) & TypeFlags.HAVE_GC) != 0;
        long presize = gc ? CStructs.PyGC_Head.size() : 0;
        long memWithHead = calloc(CStructs.PyMemoryViewObject.size() + presize);
        long mem = memWithHead + presize;

        writePtrField(mem, PyObject__ob_type, PythonToNativeNewRefRawNode.executeUncached(type));
        writeLongField(mem, PyObject__ob_refcnt, PythonAbstractObjectNativeWrapper.IMMORTAL_REFCNT);
        writeIntField(mem, PyMemoryViewObject__flags, object.getFlags());
        writeLongField(mem, PyMemoryViewObject__exports, object.getExports().get());
        // TODO: ignoring mbuf, hash and weakreflist for now

        long view = getFieldPtr(mem, CFields.PyMemoryViewObject__view);

        if (object.getBuffer() != null) {
            Object bufObj = object.getBufferPointer();
            if (bufObj == null) {
                bufObj = PythonBufferAccessLibrary.getUncached().getNativePointer(object.getBuffer());
                if (bufObj == null) {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    throw shouldNotReachHere("Cannot convert managed object to native storage: " + object.getBuffer().getClass().getSimpleName());
                }
            }
            long buf = ensurePointerUncached(bufObj);
            if (object.getOffset() != 0) {
                buf = buf + object.getOffset();
            }
            writePtrField(view, CFields.Py_buffer__buf, buf);
        }

        if (object.getOwner() != null) {
            writePtrField(view, CFields.Py_buffer__obj, PythonToNativeNewRefRawNode.executeUncached(object.getOwner()));
        }
        writeLongField(view, CFields.Py_buffer__len, object.getLength());
        writeLongField(view, CFields.Py_buffer__itemsize, object.getItemSize());
        writeIntField(view, CFields.Py_buffer__readonly, PInt.intValue(object.isReadOnly()));
        writeIntField(view, CFields.Py_buffer__ndim, object.getDimensions());
        if (object.getFormatString() != null) {
            writePtrField(view, CFields.Py_buffer__format, asCharPointerNode.execute(object.getFormatString()));
        }
        if (object.getBufferShape() != null) {
            writePtrField(view, CFields.Py_buffer__shape, intArrayToNativePySSizeArray(object.getBufferShape()));
        }
        if (object.getBufferStrides() != null) {
            writePtrField(view, CFields.Py_buffer__strides, intArrayToNativePySSizeArray(object.getBufferStrides()));
        }
        if (object.getBufferSuboffsets() != null) {
            writePtrField(view, CFields.Py_buffer__suboffsets, intArrayToNativePySSizeArray(object.getBufferSuboffsets()));
        }
        return mem;
    }

    public long getReplacement() {
        if (replacement == NULLPTR) {
            long ptr = allocate((PMemoryView) getDelegate());
            // TODO: need to convert to interop pointer for NFI for now
            replacement = ptr;
            // TODO: this passes "false" for allocatedFromJava, although it actually is. The
            // problem, however, is that this struct contains nested allocations from Java. This
            // needs to be cleaned up...
            CApiTransitions.createReference(this, ptr, false);
        }
        return replacement;
    }

    @Ignore
    @Override
    public void toNative(boolean newRef, Node inliningTarget, FirstToNativeNode firstToNativeNode) {
        /*
         * This is a wrapper that is eagerly transformed to its C layout in the Python-to-native
         * transition. Therefore, the wrapper is expected to be native already.
         */
        throw CompilerDirectives.shouldNotReachHere();
    }
}
