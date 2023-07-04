/*
 * Copyright (c) 2018, 2023, Oracle and/or its affiliates. All rights reserved.
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
import static com.oracle.graal.python.builtins.objects.cext.structs.CStructs.PyMemoryViewObject;

import com.oracle.graal.python.builtins.objects.bytes.PBytesLike;
import com.oracle.graal.python.builtins.objects.cext.capi.PyMemoryViewWrapperFactory.AllocateNodeGen;
import com.oracle.graal.python.builtins.objects.cext.capi.transitions.CApiTransitions.PythonToNativeNewRefNode;
import com.oracle.graal.python.builtins.objects.cext.structs.CFields;
import com.oracle.graal.python.builtins.objects.cext.structs.CStructAccess;
import com.oracle.graal.python.builtins.objects.common.SequenceNodes;
import com.oracle.graal.python.builtins.objects.ints.PInt;
import com.oracle.graal.python.builtins.objects.memoryview.PMemoryView;
import com.oracle.graal.python.builtins.objects.object.PythonObject;
import com.oracle.graal.python.nodes.PNodeWithContext;
import com.oracle.graal.python.nodes.object.InlinedGetClassNode;
import com.oracle.graal.python.runtime.sequence.PSequence;
import com.oracle.graal.python.runtime.sequence.storage.NativeSequenceStorage;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.nodes.Node;

/**
 * Wrapper object for {@code PMemoryView}.
 */
public final class PyMemoryViewWrapper extends PythonReplacingNativeWrapper {

    public PyMemoryViewWrapper(PythonObject delegate) {
        super(delegate);
        assert delegate instanceof PMemoryView;
    }

    @GenerateUncached
    public abstract static class AllocateNode extends PNodeWithContext {

        public abstract Object execute(Object obj);

        @GenerateUncached
        abstract static class IntArrayToNativePySSizeArray extends Node {
            public abstract Object execute(int[] array);

            @Specialization
            static Object getShape(int[] intArray,
                            @Cached CStructAccess.AllocateNode alloc,
                            @Cached CStructAccess.WriteLongNode write) {
                Object mem = alloc.alloc(intArray.length * Long.BYTES);
                for (int i = 0; i < intArray.length; i++) {
                    write.write(mem, intArray[i]);
                }
                return mem;
            }
        }

        @TruffleBoundary
        @Specialization
        Object doPythonNativeWrapper(PMemoryView object,
                        @Bind("this") Node inliningTarget,
                        @Cached InlinedGetClassNode getClass,
                        @Cached PythonToNativeNewRefNode toNative,
                        @Cached CStructAccess.AllocateNode allocNode,
                        @Cached CStructAccess.GetElementPtrNode getElementNode,
                        @Cached CStructAccess.WritePointerNode writePointerNode,
                        @Cached CStructAccess.WriteLongNode writeI64Node,
                        @Cached CStructAccess.WriteIntNode writeI32Node,
                        @Cached SequenceNodes.GetSequenceStorageNode getStorage,
                        @Cached SequenceNodes.SetSequenceStorageNode setStorage,
                        @Cached CExtNodes.PointerAddNode pointerAddNode,
                        @Cached PySequenceArrayWrapper.ToNativeStorageNode toNativeStorageNode,
                        @Cached IntArrayToNativePySSizeArray intArrayToNativePySSizeArray,
                        @Cached CExtNodes.AsCharPointerNode asCharPointerNode) {

            Object mem = allocNode.alloc(PyMemoryViewObject);
            writeI64Node.write(mem, PyObject__ob_refcnt, 0x1000); // TODO: immortal for now
            writePointerNode.write(mem, PyObject__ob_type, toNative.execute(getClass.execute(inliningTarget, object)));
            writeI32Node.write(mem, PyMemoryViewObject__flags, object.getFlags());
            writeI64Node.write(mem, PyMemoryViewObject__exports, object.getExports().get());
            // TODO: ignoring mbuf, hash and weakreflist for now

            Object view = getElementNode.getElementPtr(mem, CFields.PyMemoryViewObject__view);

            Object buf;
            if (object.getBufferPointer() == null) {
                // TODO GR-21120: Add support for PArray
                PSequence owner = (PSequence) object.getOwner();
                NativeSequenceStorage nativeStorage = toNativeStorageNode.execute(getStorage.execute(owner), owner instanceof PBytesLike);
                if (nativeStorage == null) {
                    throw CompilerDirectives.shouldNotReachHere("cannot allocate native storage");
                }
                setStorage.execute(inliningTarget, owner, nativeStorage);
                Object pointer = nativeStorage.getPtr();
                if (object.getOffset() == 0) {
                    buf = pointer;
                } else {
                    buf = pointerAddNode.execute(pointer, object.getOffset());
                }
            } else {
                if (object.getOffset() == 0) {
                    buf = object.getBufferPointer();
                } else {
                    buf = pointerAddNode.execute(object.getBufferPointer(), object.getOffset());
                }
            }
            writePointerNode.write(view, CFields.Py_buffer__buf, buf);

            if (object.getOwner() != null) {
                writePointerNode.write(view, CFields.Py_buffer__obj, toNative.execute(object.getOwner()));
            }
            writeI64Node.write(view, CFields.Py_buffer__len, object.getLength());
            writeI64Node.write(view, CFields.Py_buffer__itemsize, object.getItemSize());
            writeI32Node.write(view, CFields.Py_buffer__readonly, PInt.intValue(object.isReadOnly()));
            writeI32Node.write(view, CFields.Py_buffer__ndim, object.getDimensions());
            if (object.getFormatString() != null) {
                writePointerNode.write(view, CFields.Py_buffer__format, asCharPointerNode.execute(object.getFormatString()));
            }
            if (object.getBufferShape() != null) {
                writePointerNode.write(view, CFields.Py_buffer__shape, intArrayToNativePySSizeArray.execute(object.getBufferShape()));
            }
            if (object.getBufferStrides() != null) {
                writePointerNode.write(view, CFields.Py_buffer__strides, intArrayToNativePySSizeArray.execute(object.getBufferStrides()));
            }
            if (object.getBufferSuboffsets() != null) {
                writePointerNode.write(view, CFields.Py_buffer__suboffsets, intArrayToNativePySSizeArray.execute(object.getBufferSuboffsets()));
            }
            return mem;
        }
    }

    @Override
    protected Object allocateReplacememtObject() {
        return AllocateNodeGen.getUncached().execute(getDelegate());
    }
}
