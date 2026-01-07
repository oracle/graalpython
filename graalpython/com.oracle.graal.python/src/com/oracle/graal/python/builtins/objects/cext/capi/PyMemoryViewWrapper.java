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
package com.oracle.graal.python.builtins.objects.cext.capi;

import static com.oracle.graal.python.builtins.objects.cext.structs.CFields.PyMemoryViewObject__exports;
import static com.oracle.graal.python.builtins.objects.cext.structs.CFields.PyMemoryViewObject__flags;
import static com.oracle.graal.python.builtins.objects.cext.structs.CFields.PyObject__ob_refcnt;
import static com.oracle.graal.python.builtins.objects.cext.structs.CFields.PyObject__ob_type;
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
import com.oracle.graal.python.builtins.objects.cext.capi.transitions.CApiTransitions.PythonToNativeNewRefNode;
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

/**
 * Wrapper object for {@code PMemoryView}.
 */
public abstract class PyMemoryViewWrapper {

    private PyMemoryViewWrapper() {
    }

    private static long intArrayToNativePySSizeArray(int[] intArray) {
        long mem = mallocLongArray(intArray.length);
        for (int i = 0; i < intArray.length; i++) {
            writeLongArrayElement(mem, i, intArray[i]);
        }
        return mem;
    }

    @TruffleBoundary
    public static long allocate(PMemoryView object) {
        CExtNodes.AsCharPointerNode asCharPointerNode = CExtNodes.AsCharPointerNode.getUncached();

        Object type = GetPythonObjectClassNode.executeUncached(object);
        boolean gc = (GetTypeFlagsNode.executeUncached(type) & TypeFlags.HAVE_GC) != 0;
        long presize = gc ? CStructs.PyGC_Head.size() : 0;
        long memWithHead = calloc(CStructs.PyMemoryViewObject.size() + presize);
        long mem = memWithHead + presize;

        writePtrField(mem, PyObject__ob_type, PythonToNativeNewRefNode.executeLongUncached(type));
        writeLongField(mem, PyObject__ob_refcnt, PythonObject.IMMORTAL_REFCNT);
        writeIntField(mem, PyMemoryViewObject__flags, object.getFlags());
        writeLongField(mem, PyMemoryViewObject__exports, object.getExports().get());
        // TODO: ignoring mbuf, hash and weakreflist for now

        long view = getFieldPtr(mem, CFields.PyMemoryViewObject__view);

        if (object.getBuffer() != null) {
            long buf = object.getBufferPointer();
            if (buf == NULLPTR) {
                buf = PythonBufferAccessLibrary.getUncached().getNativePointer(object.getBuffer());
                if (buf == NULLPTR) {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    throw shouldNotReachHere("Cannot convert managed object to native storage: " + object.getBuffer().getClass().getSimpleName());
                }
            }
            if (object.getOffset() != 0) {
                buf = buf + object.getOffset();
            }
            writePtrField(view, CFields.Py_buffer__buf, buf);
        }

        if (object.getOwner() != null) {
            writePtrField(view, CFields.Py_buffer__obj, PythonToNativeNewRefNode.executeLongUncached(object.getOwner()));
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
}
