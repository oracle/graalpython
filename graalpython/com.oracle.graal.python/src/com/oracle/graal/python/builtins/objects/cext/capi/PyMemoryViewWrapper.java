/*
 * Copyright (c) 2018, 2024, Oracle and/or its affiliates. All rights reserved.
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

import static com.oracle.graal.python.builtins.PythonBuiltinClassType.ValueError;
import static com.oracle.graal.python.builtins.objects.cext.capi.NativeCAPISymbol.FUN_PTR_ADD;
import static com.oracle.graal.python.builtins.objects.cext.structs.CFields.PyMemoryViewObject__exports;
import static com.oracle.graal.python.builtins.objects.cext.structs.CFields.PyMemoryViewObject__flags;
import static com.oracle.graal.python.builtins.objects.cext.structs.CFields.PyObject__ob_refcnt;
import static com.oracle.graal.python.builtins.objects.cext.structs.CFields.PyObject__ob_type;
import static com.oracle.graal.python.builtins.objects.cext.structs.CStructs.PyMemoryViewObject;
import static com.oracle.truffle.api.CompilerDirectives.shouldNotReachHere;

import com.oracle.graal.python.builtins.objects.buffer.PythonBufferAccessLibrary;
import com.oracle.graal.python.builtins.objects.cext.capi.PythonNativeWrapper.PythonAbstractObjectNativeWrapper;
import com.oracle.graal.python.builtins.objects.cext.capi.transitions.CApiTransitions;
import com.oracle.graal.python.builtins.objects.cext.capi.transitions.CApiTransitions.PythonToNativeNewRefNode;
import com.oracle.graal.python.builtins.objects.cext.structs.CFields;
import com.oracle.graal.python.builtins.objects.cext.structs.CStructAccess;
import com.oracle.graal.python.builtins.objects.cext.structs.CStructAccess.GetElementPtrNode;
import com.oracle.graal.python.builtins.objects.ints.PInt;
import com.oracle.graal.python.builtins.objects.memoryview.PMemoryView;
import com.oracle.graal.python.builtins.objects.object.PythonObject;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.object.GetClassNode;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;

/**
 * Wrapper object for {@code PMemoryView}.
 */
@ExportLibrary(InteropLibrary.class)
public final class PyMemoryViewWrapper extends PythonAbstractObjectNativeWrapper {

    private Object replacement;

    public PyMemoryViewWrapper(PythonObject delegate) {
        super(delegate, true);
        assert delegate instanceof PMemoryView;
    }

    private static Object intArrayToNativePySSizeArray(int[] intArray) {
        Object mem = CStructAccess.AllocateNode.allocUncached(intArray.length * Long.BYTES);
        CStructAccess.WriteLongNode.getUncached().writeIntArray(mem, intArray);
        return mem;
    }

    @TruffleBoundary
    private static Object allocate(PMemoryView object) {
        if (object.isReleased()) {
            throw PRaiseNode.raiseUncached(null, ValueError, ErrorMessages.MEMORYVIEW_FORBIDDEN_RELEASED);
        }
        GetElementPtrNode getElementNode = GetElementPtrNode.getUncached();
        CStructAccess.WritePointerNode writePointerNode = CStructAccess.WritePointerNode.getUncached();
        CStructAccess.WriteLongNode writeI64Node = CStructAccess.WriteLongNode.getUncached();
        CStructAccess.WriteIntNode writeI32Node = CStructAccess.WriteIntNode.getUncached();
        CExtNodes.AsCharPointerNode asCharPointerNode = CExtNodes.AsCharPointerNode.getUncached();

        Object mem;
        if (!PythonContext.get(null).isNativeAccessAllowed()) { // accommodate managed mode.
            mem = CStructAccess.AllocateNode.allocUncached(PyMemoryViewObject.size()); /*- GC head alloc is not needed */
            writeI64Node.write(mem, PyObject__ob_refcnt, IMMORTAL_REFCNT); // TODO: immortal for now
            writePointerNode.write(mem, PyObject__ob_type, PythonToNativeNewRefNode.executeUncached(GetClassNode.executeUncached(object)));
        } else {
            long taggedPointer = CApiTransitions.FirstToNativeNode.executeUncached(object.getNativeWrapper(), true /*- TODO: immortal for now */);
            mem = CApiTransitions.HandlePointerConverter.pointerToStub(taggedPointer);
        }
        writeI32Node.write(mem, PyMemoryViewObject__flags, object.getFlags());
        writeI64Node.write(mem, PyMemoryViewObject__exports, object.getExports().get());
        // TODO: ignoring mbuf, hash and weakreflist for now

        Object view = getElementNode.getElementPtr(mem, CFields.PyMemoryViewObject__view);

        Object buf = object.getBufferPointer();
        if (buf == null) {
            buf = PythonBufferAccessLibrary.getUncached().getNativePointer(object.getBuffer());
            if (buf == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                throw shouldNotReachHere("Cannot convert managed object to native storage: " + object.getBuffer().getClass().getSimpleName());
            }
        }
        if (object.getOffset() != 0) {
            if (buf instanceof Long ptr) {
                buf = ptr + object.getOffset();
            } else {
                InteropLibrary ptrLib = InteropLibrary.getUncached(buf);
                if (ptrLib.isPointer(buf)) {
                    try {
                        buf = ptrLib.asPointer(buf) + object.getOffset();
                    } catch (UnsupportedMessageException e) {
                        throw CompilerDirectives.shouldNotReachHere(e);
                    }
                } else {
                    buf = CExtNodes.PCallCapiFunction.callUncached(FUN_PTR_ADD, buf, (long) object.getOffset());
                }
            }
        }
        writePointerNode.write(view, CFields.Py_buffer__buf, buf);

        if (object.getOwner() != null) {
            writePointerNode.write(view, CFields.Py_buffer__obj, PythonToNativeNewRefNode.executeUncached(object.getOwner()));
        }
        writeI64Node.write(view, CFields.Py_buffer__len, object.getLength());
        writeI64Node.write(view, CFields.Py_buffer__itemsize, object.getItemSize());
        writeI32Node.write(view, CFields.Py_buffer__readonly, PInt.intValue(object.isReadOnly()));
        writeI32Node.write(view, CFields.Py_buffer__ndim, object.getDimensions());
        if (object.getFormatString() != null) {
            writePointerNode.write(view, CFields.Py_buffer__format, asCharPointerNode.execute(object.getFormatString()));
        }
        if (object.getBufferShape() != null) {
            writePointerNode.write(view, CFields.Py_buffer__shape, intArrayToNativePySSizeArray(object.getBufferShape()));
        }
        if (object.getBufferStrides() != null) {
            writePointerNode.write(view, CFields.Py_buffer__strides, intArrayToNativePySSizeArray(object.getBufferStrides()));
        }
        if (object.getBufferSuboffsets() != null) {
            writePointerNode.write(view, CFields.Py_buffer__suboffsets, intArrayToNativePySSizeArray(object.getBufferSuboffsets()));
        }
        return mem;
    }

    @Override
    public Object getReplacement(InteropLibrary lib) {
        if (replacement == null) {
            Object pointerObject = allocate((PMemoryView) getDelegate());
            replacement = registerReplacement(pointerObject, false, lib);
        }
        return replacement;
    }

    @ExportMessage
    boolean isPointer() {
        return isNative();
    }

    @ExportMessage
    long asPointer() throws UnsupportedMessageException {
        if (!isNative()) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            throw UnsupportedMessageException.create();
        }
        return getNativePointer();
    }

    @ExportMessage
    void toNative() {
        if (!isNative()) {
            /*
             * This is a wrapper that is eagerly transformed to its C layout in the Python-to-native
             * transition. Therefore, the wrapper is expected to be native already.
             */
            throw CompilerDirectives.shouldNotReachHere();
        }
    }
}
