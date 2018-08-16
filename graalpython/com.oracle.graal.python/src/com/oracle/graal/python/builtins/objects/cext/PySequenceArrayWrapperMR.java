/*
 * Copyright (c) 2018, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.builtins.objects.cext;

import static com.oracle.graal.python.nodes.SpecialMethodNames.__SETITEM__;

import com.oracle.graal.python.builtins.objects.bytes.PByteArray;
import com.oracle.graal.python.builtins.objects.bytes.PBytes;
import com.oracle.graal.python.builtins.objects.cext.CExtNodes.CExtBaseNode;
import com.oracle.graal.python.builtins.objects.cext.CExtNodes.ToSulongNode;
import com.oracle.graal.python.builtins.objects.cext.NativeWrappers.PySequenceArrayWrapper;
import com.oracle.graal.python.builtins.objects.cext.PySequenceArrayWrapperMRFactory.GetTypeIDNodeGen;
import com.oracle.graal.python.builtins.objects.cext.PySequenceArrayWrapperMRFactory.ReadArrayItemNodeGen;
import com.oracle.graal.python.builtins.objects.cext.PySequenceArrayWrapperMRFactory.ToNativeArrayNodeGen;
import com.oracle.graal.python.builtins.objects.cext.PySequenceArrayWrapperMRFactory.ToNativeStorageNodeGen;
import com.oracle.graal.python.builtins.objects.cext.PySequenceArrayWrapperMRFactory.WriteArrayItemNodeGen;
import com.oracle.graal.python.builtins.objects.common.SequenceStorageNodes;
import com.oracle.graal.python.builtins.objects.common.SequenceStorageNodes.StorageToNativeNode;
import com.oracle.graal.python.builtins.objects.list.ListBuiltins;
import com.oracle.graal.python.builtins.objects.list.ListBuiltinsFactory;
import com.oracle.graal.python.builtins.objects.list.PList;
import com.oracle.graal.python.builtins.objects.tuple.PTuple;
import com.oracle.graal.python.builtins.objects.tuple.TupleBuiltins;
import com.oracle.graal.python.builtins.objects.tuple.TupleBuiltinsFactory;
import com.oracle.graal.python.nodes.PBaseNode;
import com.oracle.graal.python.nodes.SpecialMethodNames;
import com.oracle.graal.python.nodes.call.special.LookupAndCallBinaryNode;
import com.oracle.graal.python.nodes.call.special.LookupAndCallTernaryNode;
import com.oracle.graal.python.nodes.call.special.LookupAndCallUnaryNode;
import com.oracle.graal.python.nodes.truffle.PythonTypes;
import com.oracle.graal.python.runtime.sequence.PSequence;
import com.oracle.graal.python.runtime.sequence.storage.EmptySequenceStorage;
import com.oracle.graal.python.runtime.sequence.storage.NativeSequenceStorage;
import com.oracle.graal.python.runtime.sequence.storage.SequenceStorage;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.dsl.TypeSystemReference;
import com.oracle.truffle.api.interop.ForeignAccess;
import com.oracle.truffle.api.interop.Message;
import com.oracle.truffle.api.interop.MessageResolution;
import com.oracle.truffle.api.interop.Resolve;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.UnexpectedResultException;

@MessageResolution(receiverType = PySequenceArrayWrapper.class)
public class PySequenceArrayWrapperMR {

    @SuppressWarnings("unknown-message")
    @Resolve(message = "com.oracle.truffle.llvm.spi.GetDynamicType")
    abstract static class GetDynamicTypeNode extends Node {
        @Child GetTypeIDNode getTypeIDNode = GetTypeIDNode.create();

        public Object access(PySequenceArrayWrapper object) {
            return getTypeIDNode.execute(object.getDelegate());
        }

    }

    @Resolve(message = "READ")
    abstract static class ReadNode extends Node {
        @Child private ReadArrayItemNode readArrayItemNode;

        public Object access(PySequenceArrayWrapper object, Object key) {
            return getReadArrayItemNode().execute(object.getDelegate(), key);
        }

        private ReadArrayItemNode getReadArrayItemNode() {
            if (readArrayItemNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                readArrayItemNode = insert(ReadArrayItemNode.create());
            }
            return readArrayItemNode;
        }

    }

    @Resolve(message = "WRITE")
    abstract static class WriteNode extends Node {
        @Child private WriteArrayItemNode writeArrayItemNode;

        public Object access(PySequenceArrayWrapper object, Object key, Object value) {
            if (writeArrayItemNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                writeArrayItemNode = insert(WriteArrayItemNode.create());
            }
            writeArrayItemNode.execute(object.getDelegate(), key, value);

            // A C expression assigning to an array returns the assigned value.
            return value;
        }

    }

    @ImportStatic(SpecialMethodNames.class)
    @TypeSystemReference(PythonTypes.class)
    abstract static class ReadArrayItemNode extends Node {

        @Child private ToSulongNode toSulongNode;

        public abstract Object execute(Object arrayObject, Object idx);

        @Specialization
        Object doTuple(PTuple tuple, long idx,
                        @Cached("createTupleGetItem()") TupleBuiltins.GetItemNode getItemNode) {
            return getToSulongNode().execute(getItemNode.execute(tuple, idx));
        }

        @Specialization
        Object doTuple(PList list, long idx,
                        @Cached("createListGetItem()") ListBuiltins.GetItemNode getItemNode) {
            return getToSulongNode().execute(getItemNode.execute(list, idx));
        }

        /**
         * The sequence array wrapper of a {@code bytes} object represents {@code ob_sval}. We type
         * it as {@code uint8_t*} and therefore we get a byte index. However, we return
         * {@code uint64_t} since we do not know how many bytes are requested.
         */
        @Specialization
        long doBytesI64(PBytes bytes, long byteIdx,
                        @Cached("create()") SequenceStorageNodes.GetItemNode getItemNode) {
            int len = bytes.len();
            // simulate sentinel value
            if (byteIdx == len) {
                return 0L;
            }
            int i = (int) byteIdx;
            long result = 0;
            SequenceStorage store = bytes.getSequenceStorage();
            result |= getItemNode.executeInt(store, i);
            if (i + 1 < len)
                result |= ((long) getItemNode.executeInt(store, i + 1) << 8L) & 0xFF00L;
            if (i + 2 < len)
                result |= ((long) getItemNode.executeInt(store, i + 2) << 16L) & 0xFF0000L;
            if (i + 3 < len)
                result |= ((long) getItemNode.executeInt(store, i + 3) << 24L) & 0xFF000000L;
            if (i + 4 < len)
                result |= ((long) getItemNode.executeInt(store, i + 4) << 32L) & 0xFF00000000L;
            if (i + 5 < len)
                result |= ((long) getItemNode.executeInt(store, i + 5) << 40L) & 0xFF0000000000L;
            if (i + 6 < len)
                result |= ((long) getItemNode.executeInt(store, i + 6) << 48L) & 0xFF000000000000L;
            if (i + 7 < len)
                result |= ((long) getItemNode.executeInt(store, i + 7) << 56L) & 0xFF00000000000000L;
            return result;
        }

        @Specialization(guards = {"!isTuple(object)", "!isList(object)"})
        Object doGeneric(Object object, long idx,
                        @Cached("create(__GETITEM__)") LookupAndCallBinaryNode getItemNode) {
            return getToSulongNode().execute(getItemNode.executeObject(object, idx));
        }

        protected static ListBuiltins.GetItemNode createListGetItem() {
            return ListBuiltinsFactory.GetItemNodeFactory.create();
        }

        protected static TupleBuiltins.GetItemNode createTupleGetItem() {
            return TupleBuiltinsFactory.GetItemNodeFactory.create();
        }

        protected boolean isTuple(Object object) {
            return object instanceof PTuple;
        }

        protected boolean isList(Object object) {
            return object instanceof PList;
        }

        private ToSulongNode getToSulongNode() {
            if (toSulongNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                toSulongNode = insert(ToSulongNode.create());
            }
            return toSulongNode;
        }

        public static ReadArrayItemNode create() {
            return ReadArrayItemNodeGen.create();
        }
    }

    @ImportStatic(SpecialMethodNames.class)
    @TypeSystemReference(PythonTypes.class)
    abstract static class WriteArrayItemNode extends Node {
        @Child private CExtNodes.ToJavaNode toJavaNode;

        public abstract Object execute(Object arrayObject, Object idx, Object value);

        @Specialization
        Object doTuple(PBytes s, long idx, byte value,
                        @Cached("createStorageSetItem()") SequenceStorageNodes.SetItemNode setItemNode) {
            setItemNode.executeLong(s.getSequenceStorage(), idx, value);
            return value;
        }

        @Specialization
        Object doTuple(PSequence s, long idx, Object value,
                        @Cached("createStorageSetItem()") SequenceStorageNodes.SetItemNode setItemNode) {
            setItemNode.execute(s.getSequenceStorage(), idx, getToJavaNode().execute(value));
            return value;
        }

        @Specialization
        Object doGeneric(Object tuple, Object idx, Object value,
                        @Cached("createSetItem()") LookupAndCallTernaryNode setItemNode) {
            setItemNode.execute(tuple, idx, value);
            return value;
        }

        protected static SequenceStorageNodes.SetItemNode createStorageSetItem() {
            return SequenceStorageNodes.SetItemNode.create("invalid item for assignment");
        }

        protected static LookupAndCallTernaryNode createSetItem() {
            return LookupAndCallTernaryNode.create(__SETITEM__);
        }

        private CExtNodes.ToJavaNode getToJavaNode() {
            if (toJavaNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                toJavaNode = insert(CExtNodes.ToJavaNode.create());
            }
            return toJavaNode;
        }

        public static WriteArrayItemNode create() {
            return WriteArrayItemNodeGen.create();
        }
    }

    @Resolve(message = "TO_NATIVE")
    abstract static class ToNativeNode extends Node {
        @Child private ToNativeArrayNode toPyObjectNode = ToNativeArrayNode.create();

        Object access(PySequenceArrayWrapper obj) {
            if (!obj.isNative()) {
                obj.setNativePointer(toPyObjectNode.execute(obj));
            }
            return obj;
        }
    }

    abstract static class ToNativeArrayNode extends CExtBaseNode {
        @CompilationFinal private TruffleObject PyObjectHandle_FromJavaObject;
        @Child private PCallNativeNode callNativeBinary;
        @Child private ToNativeStorageNode toNativeStorageNode;

        public abstract Object execute(PySequenceArrayWrapper object);

        @Specialization(guards = "isPSequence(object.getDelegate())")
        Object doPSequence(PySequenceArrayWrapper object) {
            PSequence sequence = (PSequence) object.getDelegate();
            NativeSequenceStorage nativeStorage = getToNativeStorageNode().execute(sequence.getSequenceStorage());
            if (nativeStorage == null) {
                throw new AssertionError("could not allocate native storage");
            }
            // switch to native storage
            sequence.setSequenceStorage(nativeStorage);
            return nativeStorage.getPtr();
        }

        @Fallback
        Object doGeneric(PySequenceArrayWrapper object) {
            // TODO correct element size
            return callBinaryIntoCapi(getNativeHandleForArray(), object, 8L);
        }

        private TruffleObject getNativeHandleForArray() {
            if (PyObjectHandle_FromJavaObject == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                PyObjectHandle_FromJavaObject = importCAPISymbol(NativeCAPISymbols.FUN_NATIVE_HANDLE_FOR_ARRAY);
            }
            return PyObjectHandle_FromJavaObject;
        }

        protected boolean isPSequence(Object obj) {
            return obj instanceof PSequence;
        }

        private ToNativeStorageNode getToNativeStorageNode() {
            if (toNativeStorageNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                toNativeStorageNode = insert(ToNativeStorageNode.create());
            }
            return toNativeStorageNode;
        }

        private Object callBinaryIntoCapi(TruffleObject fun, Object arg0, Object arg1) {
            if (callNativeBinary == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                callNativeBinary = insert(PCallNativeNode.create());
            }
            return callNativeBinary.execute(fun, new Object[]{arg0, arg1});
        }

        public static ToNativeArrayNode create() {
            return ToNativeArrayNodeGen.create();
        }
    }

    @Resolve(message = "IS_POINTER")
    abstract static class IsPointerNode extends Node {
        boolean access(PySequenceArrayWrapper obj) {
            return obj.isNative();
        }
    }

    @Resolve(message = "AS_POINTER")
    abstract static class AsPointerNode extends Node {
        @Child private Node asPointerNode;

        long access(PySequenceArrayWrapper obj) {
            // the native pointer object must either be a TruffleObject or a primitive
            Object nativePointer = obj.getNativePointer();
            if (nativePointer instanceof TruffleObject) {
                if (asPointerNode == null) {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    asPointerNode = insert(Message.AS_POINTER.createNode());
                }
                try {
                    return ForeignAccess.sendAsPointer(asPointerNode, (TruffleObject) nativePointer);
                } catch (UnsupportedMessageException e) {
                    throw e.raise();
                }
            }
            return (long) nativePointer;
        }
    }

    @ImportStatic(SpecialMethodNames.class)
    abstract static class GetTypeIDNode extends CExtBaseNode {

        @Child private PCallNativeNode callUnaryNode = PCallNativeNode.create();

        @CompilationFinal TruffleObject funGetByteArrayTypeID;
        @CompilationFinal TruffleObject funGetPtrArrayTypeID;

        public abstract Object execute(Object delegate);

        private Object callGetByteArrayTypeID(long len) {
            if (funGetByteArrayTypeID == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                funGetByteArrayTypeID = importCAPISymbol(NativeCAPISymbols.FUN_GET_BYTE_ARRAY_TYPE_ID);
            }
            return callUnaryNode.execute(funGetByteArrayTypeID, new Object[]{len});
        }

        private Object callGetPtrArrayTypeID(long len) {
            if (funGetPtrArrayTypeID == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                funGetPtrArrayTypeID = importCAPISymbol(NativeCAPISymbols.FUN_GET_PTR_ARRAY_TYPE_ID);
            }
            return callUnaryNode.execute(funGetPtrArrayTypeID, new Object[]{len});
        }

        @Specialization
        Object doTuple(PTuple tuple) {
            return callGetPtrArrayTypeID(tuple.len());
        }

        @Specialization
        Object doList(PList list) {
            return callGetPtrArrayTypeID(list.len());
        }

        @Specialization
        Object doBytes(PBytes bytes) {
            return callGetByteArrayTypeID(bytes.len());
        }

        @Specialization
        Object doByteArray(PByteArray bytes) {
            return callGetByteArrayTypeID(bytes.len());
        }

        @Specialization(guards = {"!isTuple(object)", "!isList(object)"})
        Object doGeneric(Object object,
                        @Cached("create(__LEN__)") LookupAndCallUnaryNode getLenNode) {
            try {
                return callGetPtrArrayTypeID(getLenNode.executeInt(object));
            } catch (UnexpectedResultException e) {
                // TODO do something useful
                throw new AssertionError();
            }
        }

        protected static ListBuiltins.GetItemNode createListGetItem() {
            return ListBuiltinsFactory.GetItemNodeFactory.create();
        }

        protected static TupleBuiltins.GetItemNode createTupleGetItem() {
            return TupleBuiltinsFactory.GetItemNodeFactory.create();
        }

        protected boolean isTuple(Object object) {
            return object instanceof PTuple;
        }

        protected boolean isList(Object object) {
            return object instanceof PList;
        }

        public static GetTypeIDNode create() {
            return GetTypeIDNodeGen.create();
        }
    }

    static abstract class ToNativeStorageNode extends PBaseNode {
        @Child private StorageToNativeNode storageToNativeNode;

        public abstract NativeSequenceStorage execute(SequenceStorage object);

        @Specialization(guards = "!isNative(s)")
        NativeSequenceStorage doManaged(SequenceStorage s) {
            return getStorageToNativeNode().execute(s.getInternalArrayObject());
        }

        @Specialization
        NativeSequenceStorage doNative(NativeSequenceStorage s) {
            return s;
        }

        @Specialization
        NativeSequenceStorage doEmptyStorage(@SuppressWarnings("unused") EmptySequenceStorage s) {
            // TODO(fa): not sure if that completely reflects semantics
            return getStorageToNativeNode().execute(new byte[0]);
        }

        @Fallback
        NativeSequenceStorage doGeneric(@SuppressWarnings("unused") SequenceStorage s) {
            CompilerDirectives.transferToInterpreter();
            throw new UnsupportedOperationException("Unknown storage type: " + s);
        }

        protected static boolean isNative(SequenceStorage s) {
            return s instanceof NativeSequenceStorage;
        }

        private StorageToNativeNode getStorageToNativeNode() {
            if (storageToNativeNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                storageToNativeNode = insert(StorageToNativeNode.create());
            }
            return storageToNativeNode;
        }

        public static ToNativeStorageNode create() {
            return ToNativeStorageNodeGen.create();
        }
    }

}
