/*
 * Copyright (c) 2020, 2022, Oracle and/or its affiliates. All rights reserved.
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

import static com.oracle.graal.python.builtins.objects.cext.capi.NativeCAPISymbol.FUN_GET_PY_BUFFER_TYPEID;
import static com.oracle.graal.python.builtins.objects.cext.capi.NativeCAPISymbol.FUN_PY_TRUFFLE_LONG_ARRAY_TO_NATIVE;

import com.oracle.graal.python.builtins.objects.bytes.PBytesLike;
import com.oracle.graal.python.builtins.objects.cext.capi.transitions.CApiTransitions;
import com.oracle.graal.python.builtins.objects.common.SequenceNodes;
import com.oracle.graal.python.builtins.objects.memoryview.PMemoryView;
import com.oracle.graal.python.builtins.objects.object.PythonObject;
import com.oracle.graal.python.nodes.SpecialMethodNames;
import com.oracle.graal.python.runtime.GilNode;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.graal.python.runtime.sequence.PSequence;
import com.oracle.graal.python.runtime.sequence.storage.NativeSequenceStorage;
import com.oracle.truffle.api.Assumption;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Exclusive;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.UnknownIdentifierException;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.profiles.ConditionProfile;
import com.oracle.truffle.llvm.spi.NativeTypeLibrary;

/**
 * Wraps MemoryView to provide a native view of the "view" struct element with a shape like
 * {@code struct Py_buffer}.
 */
@ExportLibrary(InteropLibrary.class)
@ExportLibrary(value = NativeTypeLibrary.class, useForAOT = false)
@ImportStatic(SpecialMethodNames.class)
public class PyMemoryViewBufferWrapper extends PythonNativeWrapper {
    public static final String J_BUF = "buf";
    public static final String J_OBJ = "obj";
    public static final String J_LEN = "len";
    public static final String J_ITEMSIZE = "itemsize";
    public static final String J_READONLY = "readonly";
    public static final String J_NDIM = "ndim";
    public static final String J_FORMAT = "format";
    public static final String J_SHAPE = "shape";
    public static final String J_STRIDES = "strides";
    public static final String J_SUBOFFSETS = "suboffsets";
    public static final String J_INTERNAL = "internal";

    public PyMemoryViewBufferWrapper(PythonObject delegate) {
        super(delegate);
    }

    @ExportMessage
    boolean hasMembers() {
        return true;
    }

    @ExportMessage
    protected boolean isMemberReadable(String member) {
        switch (member) {
            case J_BUF:
            case J_OBJ:
            case J_LEN:
            case J_ITEMSIZE:
            case J_READONLY:
            case J_NDIM:
            case J_FORMAT:
            case J_SHAPE:
            case J_STRIDES:
            case J_SUBOFFSETS:
            case J_INTERNAL:
                return true;
            default:
                return false;
        }
    }

    @ExportMessage
    protected Object getMembers(@SuppressWarnings("unused") boolean includeInternal) throws UnsupportedMessageException {
        throw UnsupportedMessageException.create();
    }

    @ExportMessage
    protected Object readMember(String member,
                    @Exclusive @Cached ReadFieldNode readFieldNode,
                    @Exclusive @Cached GilNode gil) throws UnknownIdentifierException {
        boolean mustRelease = gil.acquire();
        try {
            return readFieldNode.execute((PMemoryView) getDelegate(), member);
        } finally {
            gil.release(mustRelease);
        }
    }

    @GenerateUncached
    abstract static class IntArrayToNativePySSizeArray extends Node {
        public abstract Object execute(int[] array);

        @Specialization
        static Object getShape(int[] intArray,
                        @Cached CExtNodes.PCallCapiFunction callCapiFunction) {
            long[] longArray = new long[intArray.length];
            for (int i = 0; i < intArray.length; i++) {
                longArray[i] = intArray[i];
            }
            // TODO memory leak, see GR-26590
            return callCapiFunction.call(FUN_PY_TRUFFLE_LONG_ARRAY_TO_NATIVE, PythonContext.get(callCapiFunction).getEnv().asGuestValue(longArray), longArray.length);
        }
    }

    @ImportStatic(PyMemoryViewBufferWrapper.class)
    @GenerateUncached
    abstract static class ReadFieldNode extends Node {

        public abstract Object execute(PMemoryView delegate, String key) throws UnknownIdentifierException;

        protected static boolean eq(String expected, String actual) {
            return expected.equals(actual);
        }

        @Specialization(guards = {"eq(J_BUF, key)", "object.getBufferPointer() == null"})
        static Object getBufManaged(PMemoryView object, @SuppressWarnings("unused") String key,
                        @Cached SequenceNodes.GetSequenceStorageNode getStorage,
                        @Cached SequenceNodes.SetSequenceStorageNode setStorage,
                        @Shared("pointerAdd") @Cached CExtNodes.PointerAddNode pointerAddNode,
                        @Cached PySequenceArrayWrapper.ToNativeStorageNode toNativeStorageNode) {
            // TODO GR-21120: Add support for PArray
            PSequence owner = (PSequence) object.getOwner();
            NativeSequenceStorage nativeStorage = toNativeStorageNode.execute(getStorage.execute(owner), owner instanceof PBytesLike);
            if (nativeStorage == null) {
                throw CompilerDirectives.shouldNotReachHere("cannot allocate native storage");
            }
            setStorage.execute(owner, nativeStorage);
            Object pointer = nativeStorage.getPtr();
            if (object.getOffset() == 0) {
                return pointer;
            } else {
                return pointerAddNode.execute(pointer, object.getOffset());
            }
        }

        @Specialization(guards = {"eq(J_BUF, key)", "object.getBufferPointer() != null"})
        static Object getBufNative(PMemoryView object, @SuppressWarnings("unused") String key,
                        @Shared("pointerAdd") @Cached CExtNodes.PointerAddNode pointerAddNode) {
            if (object.getOffset() == 0) {
                return object.getBufferPointer();
            } else {
                return pointerAddNode.execute(object.getBufferPointer(), object.getOffset());
            }
        }

        @Specialization(guards = {"eq(J_OBJ, key)"})
        static Object getObj(PMemoryView object, @SuppressWarnings("unused") String key,
                        @Shared("toSulong") @Cached CExtNodes.ToSulongNode toSulongNode) {
            if (object.getOwner() != null) {
                return toSulongNode.execute(object.getOwner());
            } else {
                return toSulongNode.execute(PythonContext.get(toSulongNode).getNativeNull());
            }
        }

        @Specialization(guards = {"eq(J_LEN, key)"})
        static Object getLen(PMemoryView object, @SuppressWarnings("unused") String key) {
            return (long) object.getLength();
        }

        @Specialization(guards = {"eq(J_ITEMSIZE, key)"})
        static Object getItemsize(PMemoryView object, @SuppressWarnings("unused") String key) {
            return (long) object.getItemSize();
        }

        @Specialization(guards = {"eq(J_NDIM, key)"})
        static Object getINDim(PMemoryView object, @SuppressWarnings("unused") String key) {
            return object.getDimensions();
        }

        @Specialization(guards = {"eq(J_READONLY, key)"})
        static Object getReadonly(PMemoryView object, @SuppressWarnings("unused") String key) {
            return object.isReadOnly() ? 1 : 0;
        }

        @Specialization(guards = {"eq(J_FORMAT, key)"})
        static Object getFormat(PMemoryView object, @SuppressWarnings("unused") String key,
                        @Cached CExtNodes.AsCharPointerNode asCharPointerNode,
                        @Shared("toSulong") @Cached CExtNodes.ToSulongNode toSulongNode) {
            if (object.getFormatString() != null) {
                return asCharPointerNode.execute(object.getFormatString());
            } else {
                return toSulongNode.execute(PythonContext.get(toSulongNode).getNativeNull());
            }
        }

        @Specialization(guards = {"eq(J_SHAPE, key)"})
        static Object getShape(PMemoryView object, @SuppressWarnings("unused") String key,
                        @Shared("toSulong") @Cached CExtNodes.ToSulongNode toSulongNode,
                        @Shared("toArray") @Cached IntArrayToNativePySSizeArray intArrayToNativePySSizeArray) {
            int[] shape = object.getBufferShape();
            if (shape == null) {
                return toSulongNode.execute(PythonContext.get(toSulongNode).getNativeNull());
            }
            return intArrayToNativePySSizeArray.execute(shape);
        }

        @Specialization(guards = {"eq(J_STRIDES, key)"})
        static Object getStrides(PMemoryView object, @SuppressWarnings("unused") String key,
                        @Shared("toSulong") @Cached CExtNodes.ToSulongNode toSulongNode,
                        @Shared("toArray") @Cached IntArrayToNativePySSizeArray intArrayToNativePySSizeArray) {
            int[] strides = object.getBufferStrides();
            if (strides == null) {
                return toSulongNode.execute(PythonContext.get(toSulongNode).getNativeNull());
            }
            return intArrayToNativePySSizeArray.execute(strides);
        }

        @Specialization(guards = {"eq(J_SUBOFFSETS, key)"})
        static Object getSuboffsets(PMemoryView object, @SuppressWarnings("unused") String key,
                        @Shared("toSulong") @Cached CExtNodes.ToSulongNode toSulongNode,
                        @Shared("toArray") @Cached IntArrayToNativePySSizeArray intArrayToNativePySSizeArray) {
            if (object.getBufferSuboffsets() != null) {
                return intArrayToNativePySSizeArray.execute(object.getBufferSuboffsets());
            } else {
                return toSulongNode.execute(PythonContext.get(toSulongNode).getNativeNull());
            }
        }

        @Specialization(guards = {"eq(J_INTERNAL, key)"})
        static Object getInternal(@SuppressWarnings("unused") PMemoryView object, @SuppressWarnings("unused") String key,
                        @Shared("toSulong") @Cached CExtNodes.ToSulongNode toSulongNode) {
            return toSulongNode.execute(PythonContext.get(toSulongNode).getNativeNull());
        }

        @Fallback
        static Object error(@SuppressWarnings("unused") PMemoryView object, String key) throws UnknownIdentifierException {
            throw UnknownIdentifierException.create(key);
        }
    }

    // TO POINTER / AS POINTER / TO NATIVE

    @ExportMessage
    protected boolean isPointer() {
        return isNative();
    }

    @ExportMessage
    public long asPointer() {
        return getNativePointer();
    }

    @ExportMessage
    protected void toNative(
                    @Cached ConditionProfile isNativeProfile) {
        if (!isNative(isNativeProfile)) {
            CApiTransitions.firstToNative(this);
        }
    }

    @ExportMessage
    @SuppressWarnings("static-method")
    protected boolean hasNativeType() {
        return true;
    }

    @ExportMessage
    abstract static class GetNativeType {
        @Specialization(assumptions = "singleNativeContextAssumption()")
        static Object doByteArray(@SuppressWarnings("unused") PyMemoryViewBufferWrapper receiver,
                        @Exclusive @Cached(value = "callGetThreadStateTypeIDUncached()") Object nativeType) {
            return nativeType;
        }

        @Specialization(replaces = "doByteArray")
        static Object doByteArrayMultiCtx(@SuppressWarnings("unused") PyMemoryViewBufferWrapper receiver,
                        @Exclusive @Cached CExtNodes.PCallCapiFunction callUnaryNode) {
            return callUnaryNode.call(FUN_GET_PY_BUFFER_TYPEID);
        }

        protected static Object callGetThreadStateTypeIDUncached() {
            return CExtNodes.PCallCapiFunction.getUncached().call(FUN_GET_PY_BUFFER_TYPEID);
        }

        protected static Assumption singleNativeContextAssumption() {
            return PythonContext.getSingleNativeContextAssumption();
        }
    }
}
