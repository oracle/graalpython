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
import com.oracle.graal.python.builtins.objects.bytes.PIBytesLike;
import com.oracle.graal.python.builtins.objects.cext.CExtNodes.CExtBaseNode;
import com.oracle.graal.python.builtins.objects.cext.CExtNodes.ToSulongNode;
import com.oracle.graal.python.builtins.objects.cext.NativeWrappers.PySequenceArrayWrapper;
import com.oracle.graal.python.builtins.objects.cext.PySequenceArrayWrapperMRFactory.GetTypeIDNodeGen;
import com.oracle.graal.python.builtins.objects.cext.PySequenceArrayWrapperMRFactory.ReadArrayItemNodeGen;
import com.oracle.graal.python.builtins.objects.cext.PySequenceArrayWrapperMRFactory.ToNativeArrayNodeGen;
import com.oracle.graal.python.builtins.objects.cext.PySequenceArrayWrapperMRFactory.ToNativeStorageNodeGen;
import com.oracle.graal.python.builtins.objects.cext.PySequenceArrayWrapperMRFactory.WriteArrayItemNodeGen;
import com.oracle.graal.python.builtins.objects.cext.PythonObjectNativeWrapperMR.InvalidateNativeObjectsAllManagedNode;
import com.oracle.graal.python.builtins.objects.common.SequenceNodes;
import com.oracle.graal.python.builtins.objects.common.SequenceNodes.GetSequenceStorageNode;
import com.oracle.graal.python.builtins.objects.common.SequenceStorageNodes;
import com.oracle.graal.python.builtins.objects.common.SequenceStorageNodes.ListGeneralizationNode;
import com.oracle.graal.python.builtins.objects.common.SequenceStorageNodes.NormalizeIndexNode;
import com.oracle.graal.python.builtins.objects.common.SequenceStorageNodes.StorageToNativeNode;
import com.oracle.graal.python.builtins.objects.list.ListBuiltins;
import com.oracle.graal.python.builtins.objects.list.ListBuiltinsFactory;
import com.oracle.graal.python.builtins.objects.list.PList;
import com.oracle.graal.python.builtins.objects.tuple.PTuple;
import com.oracle.graal.python.builtins.objects.tuple.TupleBuiltins;
import com.oracle.graal.python.builtins.objects.tuple.TupleBuiltinsFactory;
import com.oracle.graal.python.nodes.PNodeWithContext;
import com.oracle.graal.python.nodes.SpecialMethodNames;
import com.oracle.graal.python.nodes.call.special.LookupAndCallBinaryNode;
import com.oracle.graal.python.nodes.call.special.LookupAndCallTernaryNode;
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
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.profiles.ConditionProfile;
import com.oracle.truffle.api.profiles.ValueProfile;

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
        long doBytesI64(PIBytesLike bytesLike, long byteIdx,
                        @Cached("createClassProfile()") ValueProfile profile,
                        @Cached("create()") SequenceStorageNodes.LenNode lenNode,
                        @Cached("create()") SequenceStorageNodes.GetItemNode getItemNode) {
            PIBytesLike profiled = profile.profile(bytesLike);
            int len = lenNode.execute(profiled.getSequenceStorage());
            // simulate sentinel value
            if (byteIdx == len) {
                return 0L;
            }
            int i = (int) byteIdx;
            long result = 0;
            SequenceStorage store = profiled.getSequenceStorage();
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
        @Child private LookupAndCallTernaryNode setItemNode;
        @Child private SequenceNodes.GetSequenceStorageNode getSequenceStorageNode;
        @Child private SequenceStorageNodes.SetItemNode setByteItemNode;

        public abstract Object execute(Object arrayObject, Object idx, Object value);

        @Specialization
        Object doBytes(PIBytesLike s, long idx, byte value) {
            getSetByteItemNode().executeLong(getSequenceStorage(s), idx, value);
            return value;
        }

        @Specialization
        @ExplodeLoop
        Object doBytes(PIBytesLike s, long idx, short value) {
            for (int offset = 0; offset < Short.BYTES; offset++) {
                getSetByteItemNode().executeLong(getSequenceStorage(s), idx + offset, (byte) (value >> (8 * offset)) & 0xFF);
            }
            return value;
        }

        @Specialization
        @ExplodeLoop
        Object doBytes(PIBytesLike s, long idx, int value) {
            for (int offset = 0; offset < Integer.BYTES; offset++) {
                getSetByteItemNode().executeLong(getSequenceStorage(s), idx + offset, (byte) (value >> (8 * offset)) & 0xFF);
            }
            return value;
        }

        @Specialization
        @ExplodeLoop
        Object doBytes(PIBytesLike s, long idx, long value) {
            for (int offset = 0; offset < Long.BYTES; offset++) {
                getSetByteItemNode().executeLong(getSequenceStorage(s), idx + offset, (byte) (value >> (8 * offset)) & 0xFF);
            }
            return value;
        }

        @Specialization
        Object doList(PList s, long idx, Object value,
                        @Cached("createSetListItem()") SequenceStorageNodes.SetItemNode setListItemNode,
                        @Cached("createBinaryProfile()") ConditionProfile updateStorageProfile) {
            SequenceStorage storage = s.getSequenceStorage();
            SequenceStorage updatedStorage = setListItemNode.executeLong(storage, idx, getToJavaNode().execute(value));
            if (updateStorageProfile.profile(storage != updatedStorage)) {
                s.setSequenceStorage(updatedStorage);
            }
            return value;
        }

        @Specialization
        Object doTuple(PTuple s, long idx, Object value,
                        @Cached("createSetItem()") SequenceStorageNodes.SetItemNode setListItemNode) {
            setListItemNode.executeLong(s.getSequenceStorage(), idx, getToJavaNode().execute(value));
            return value;
        }

        @Fallback
        Object doGeneric(Object sequence, Object idx, Object value) {
            setItemNode().execute(sequence, idx, getToJavaNode().execute(value));
            return value;
        }

        protected static SequenceStorageNodes.SetItemNode createSetListItem() {
            return SequenceStorageNodes.SetItemNode.create(NormalizeIndexNode.forArrayAssign(), () -> ListGeneralizationNode.create());
        }

        protected static SequenceStorageNodes.SetItemNode createSetItem() {
            return SequenceStorageNodes.SetItemNode.create("invalid item for assignment");
        }

        private CExtNodes.ToJavaNode getToJavaNode() {
            if (toJavaNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                toJavaNode = insert(CExtNodes.ToJavaNode.create());
            }
            return toJavaNode;
        }

        private SequenceStorage getSequenceStorage(PIBytesLike seq) {
            if (getSequenceStorageNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                getSequenceStorageNode = insert(GetSequenceStorageNode.create());
            }
            return getSequenceStorageNode.execute(seq);
        }

        private SequenceStorageNodes.SetItemNode getSetByteItemNode() {
            if (setByteItemNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                setByteItemNode = insert(createSetItem());
            }
            return setByteItemNode;
        }

        private LookupAndCallTernaryNode setItemNode() {
            if (setItemNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                setItemNode = insert(LookupAndCallTernaryNode.create(__SETITEM__));
            }
            return setItemNode;
        }

        public static WriteArrayItemNode create() {
            return WriteArrayItemNodeGen.create();
        }
    }

    @Resolve(message = "TO_NATIVE")
    abstract static class ToNativeNode extends Node {
        @Child private ToNativeArrayNode toPyObjectNode = ToNativeArrayNode.create();
        @Child private InvalidateNativeObjectsAllManagedNode invalidateNode = InvalidateNativeObjectsAllManagedNode.create();

        Object access(PySequenceArrayWrapper obj) {
            invalidateNode.execute();
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
        @Child private CExtNodes.IsPointerNode pIsPointerNode = CExtNodes.IsPointerNode.create();

        boolean access(PySequenceArrayWrapper obj) {
            return pIsPointerNode.execute(obj);
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

        @CompilationFinal private TruffleObject funGetByteArrayTypeID;
        @CompilationFinal private TruffleObject funGetPtrArrayTypeID;

        public abstract Object execute(Object delegate);

        protected Object callGetByteArrayTypeID() {
            return callGetArrayTypeID(importCAPISymbol(NativeCAPISymbols.FUN_GET_BYTE_ARRAY_TYPE_ID));
        }

        protected Object callGetPtrArrayTypeID() {
            return callGetArrayTypeID(importCAPISymbol(NativeCAPISymbols.FUN_GET_PTR_ARRAY_TYPE_ID));
        }

        private Object callGetByteArrayTypeIDCached() {
            if (funGetByteArrayTypeID == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                funGetByteArrayTypeID = importCAPISymbol(NativeCAPISymbols.FUN_GET_BYTE_ARRAY_TYPE_ID);
            }
            return callGetArrayTypeID(funGetByteArrayTypeID);
        }

        private Object callGetPtrArrayTypeIDCached() {
            if (funGetPtrArrayTypeID == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                funGetPtrArrayTypeID = importCAPISymbol(NativeCAPISymbols.FUN_GET_PTR_ARRAY_TYPE_ID);
            }
            return callGetArrayTypeID(funGetPtrArrayTypeID);
        }

        private Object callGetArrayTypeID(TruffleObject fun) {
            // We use length=0 indicating an unknown length. This allows us to reuse the type but
            // Sulong will report the wrong length via interop for a pointer to this object.
            return callUnaryNode.execute(fun, new Object[]{0});
        }

        @Specialization(assumptions = "singleContextAssumption()", guards = "hasByteArrayContent(object)")
        Object doByteArray(@SuppressWarnings("unused") PSequence object,
                        @Cached("callGetByteArrayTypeID()") Object nativeType) {
            // TODO(fa): use weak reference ?
            return nativeType;
        }

        @Specialization(guards = "hasByteArrayContent(object)", replaces = "doByteArray")
        Object doByteArrayMultiCtx(@SuppressWarnings("unused") Object object) {
            return callGetByteArrayTypeIDCached();
        }

        @Specialization(assumptions = "singleContextAssumption()", guards = "!hasByteArrayContent(object)")
        Object doPtrArray(@SuppressWarnings("unused") Object object,
                        @Cached("callGetPtrArrayTypeID()") Object nativeType) {
            // TODO(fa): use weak reference ?
            return nativeType;
        }

        @Specialization(guards = "!hasByteArrayContent(object)", replaces = "doPtrArray")
        Object doPtrArrayMultiCtx(@SuppressWarnings("unused") PSequence object) {
            return callGetPtrArrayTypeIDCached();
        }

        protected static boolean hasByteArrayContent(Object object) {
            return object instanceof PBytes || object instanceof PByteArray;
        }

        public static GetTypeIDNode create() {
            return GetTypeIDNodeGen.create();
        }
    }

    static abstract class ToNativeStorageNode extends PNodeWithContext {
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
