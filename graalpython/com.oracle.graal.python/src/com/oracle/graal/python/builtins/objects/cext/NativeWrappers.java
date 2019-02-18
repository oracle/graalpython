/*
 * Copyright (c) 2018, 2019, Oracle and/or its affiliates. All rights reserved.
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

import java.nio.charset.Charset;
import java.nio.charset.CharsetEncoder;

import com.oracle.graal.python.builtins.objects.PythonAbstractObject;
import com.oracle.graal.python.builtins.objects.bytes.PIBytesLike;
import com.oracle.graal.python.builtins.objects.cext.CArrayWrappers.CStringWrapper;
import com.oracle.graal.python.builtins.objects.common.DynamicObjectStorage.PythonObjectDictStorage;
import com.oracle.graal.python.builtins.objects.common.SequenceNodes;
import com.oracle.graal.python.builtins.objects.common.SequenceStorageNodes;
import com.oracle.graal.python.builtins.objects.dict.PDict;
import com.oracle.graal.python.builtins.objects.ints.PInt;
import com.oracle.graal.python.builtins.objects.list.ListBuiltins;
import com.oracle.graal.python.builtins.objects.list.ListBuiltinsFactory;
import com.oracle.graal.python.builtins.objects.list.PList;
import com.oracle.graal.python.builtins.objects.str.PString;
import com.oracle.graal.python.builtins.objects.tuple.PTuple;
import com.oracle.graal.python.builtins.objects.tuple.TupleBuiltins;
import com.oracle.graal.python.builtins.objects.tuple.TupleBuiltinsFactory;
import com.oracle.graal.python.builtins.objects.type.PythonClass;
import com.oracle.graal.python.nodes.PNodeWithContext;
import com.oracle.graal.python.nodes.SpecialMethodNames;
import com.oracle.graal.python.nodes.call.special.LookupAndCallBinaryNode;
import com.oracle.graal.python.nodes.call.special.LookupAndCallTernaryNode;
import com.oracle.graal.python.nodes.truffle.PythonTypes;
import com.oracle.graal.python.runtime.sequence.PSequence;
import com.oracle.graal.python.runtime.sequence.storage.EmptySequenceStorage;
import com.oracle.graal.python.runtime.sequence.storage.NativeSequenceStorage;
import com.oracle.graal.python.runtime.sequence.storage.SequenceStorage;
import com.oracle.truffle.api.Assumption;
import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.dsl.TypeSystemReference;
import com.oracle.truffle.api.interop.ForeignAccess;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.InvalidArrayIndexException;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnknownIdentifierException;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.object.Layout;
import com.oracle.truffle.api.object.ObjectType;
import com.oracle.truffle.api.object.Shape;
import com.oracle.truffle.api.profiles.ConditionProfile;
import com.oracle.truffle.api.profiles.ValueProfile;

public abstract class NativeWrappers {

    public abstract static class PythonNativeWrapper implements TruffleObject {

        private Object delegate;
        private Object nativePointer;

        public PythonNativeWrapper() {
        }

        public PythonNativeWrapper(Object delegate) {
            this.delegate = delegate;
        }

        public final Object getDelegate() {
            return delegate;
        }

        protected void setDelegate(Object delegate) {
            this.delegate = delegate;
        }

        public Object getNativePointer() {
            return nativePointer;
        }

        public void setNativePointer(Object nativePointer) {
            // we should set the pointer just once
            assert this.nativePointer == null || this.nativePointer.equals(nativePointer) || nativePointer == null;
            this.nativePointer = nativePointer;
        }

        public boolean isNative() {
            return nativePointer != null;
        }

        static boolean isInstance(TruffleObject o) {
            return o instanceof DynamicObjectNativeWrapper || o instanceof TruffleObjectNativeWrapper;
        }

        @Override
        public ForeignAccess getForeignAccess() {
            return PythonObjectNativeWrapperMRForeign.ACCESS;
        }
    }

    public abstract static class DynamicObjectNativeWrapper extends PythonNativeWrapper {
        private static final Layout OBJECT_LAYOUT = Layout.newLayout().build();
        private static final Shape SHAPE = OBJECT_LAYOUT.createShape(new ObjectType());

        private PythonObjectDictStorage nativeMemberStore;

        public DynamicObjectNativeWrapper() {
        }

        public DynamicObjectNativeWrapper(Object delegate) {
            super(delegate);
        }

        public PythonObjectDictStorage createNativeMemberStore() {
            return createNativeMemberStore(null);
        }

        public PythonObjectDictStorage createNativeMemberStore(Assumption dictStableAssumption) {
            if (nativeMemberStore == null) {
                nativeMemberStore = new PythonObjectDictStorage(SHAPE.newInstance(), dictStableAssumption);
            }
            return nativeMemberStore;
        }

        public PythonObjectDictStorage getNativeMemberStore() {
            return nativeMemberStore;
        }
    }

    /**
     * Used to wrap {@link PythonAbstractObject} when used in native code. This wrapper mimics the
     * correct shape of the corresponding native type {@code struct _object}.
     */
    public static class PythonObjectNativeWrapper extends DynamicObjectNativeWrapper {

        public PythonObjectNativeWrapper(PythonAbstractObject object) {
            super(object);
        }

        public PythonAbstractObject getPythonObject() {
            return (PythonAbstractObject) getDelegate();
        }

        public static DynamicObjectNativeWrapper wrap(PythonAbstractObject obj, ConditionProfile noWrapperProfile) {
            // important: native wrappers are cached
            DynamicObjectNativeWrapper nativeWrapper = obj.getNativeWrapper();
            if (noWrapperProfile.profile(nativeWrapper == null)) {
                nativeWrapper = new PythonObjectNativeWrapper(obj);
                obj.setNativeWrapper(nativeWrapper);
            }
            return nativeWrapper;
        }

        @Override
        public String toString() {
            CompilerAsserts.neverPartOfCompilation();
            return String.format("PythonObjectNativeWrapper(%s, isNative=%s)", getDelegate(), isNative());
        }
    }

    public static class PrimitiveNativeWrapper extends DynamicObjectNativeWrapper {

        public static final byte PRIMITIVE_STATE_BOOL = 1 << 0;
        public static final byte PRIMITIVE_STATE_BYTE = 1 << 1;
        public static final byte PRIMITIVE_STATE_INT = 1 << 2;
        public static final byte PRIMITIVE_STATE_LONG = 1 << 3;
        public static final byte PRIMITIVE_STATE_DOUBLE = 1 << 4;

        private final byte state;
        private final long value;
        private final double dvalue;

        private PrimitiveNativeWrapper(byte state, long value) {
            assert state != PRIMITIVE_STATE_DOUBLE;
            this.state = state;
            this.value = value;
            this.dvalue = 0.0;
        }

        private PrimitiveNativeWrapper(double dvalue) {
            this.state = PRIMITIVE_STATE_DOUBLE;
            this.value = 0;
            this.dvalue = dvalue;
        }

        public byte getState() {
            return state;
        }

        public boolean getBool() {
            return value != 0;
        }

        public byte getByte() {
            return (byte) value;
        }

        public int getInt() {
            return (int) value;
        }

        public long getLong() {
            return value;
        }

        public double getDouble() {
            return dvalue;
        }

        public boolean isBool() {
            return state == PRIMITIVE_STATE_BOOL;
        }

        public boolean isByte() {
            return state == PRIMITIVE_STATE_BYTE;
        }

        public boolean isInt() {
            return state == PRIMITIVE_STATE_INT;
        }

        public boolean isLong() {
            return state == PRIMITIVE_STATE_LONG;
        }

        public boolean isDouble() {
            return state == PRIMITIVE_STATE_DOUBLE;
        }

        public boolean isIntLike() {
            return (state & (PRIMITIVE_STATE_BYTE | PRIMITIVE_STATE_INT | PRIMITIVE_STATE_LONG)) != 0;
        }

        // this method exists just for readability
        public Object getMaterializedObject() {
            return getDelegate();
        }

        // this method exists just for readability
        public void setMaterializedObject(Object materializedPrimitive) {
            setDelegate(materializedPrimitive);
        }

        public static PrimitiveNativeWrapper createBool(boolean val) {
            return new PrimitiveNativeWrapper(PRIMITIVE_STATE_BOOL, PInt.intValue(val));
        }

        public static PrimitiveNativeWrapper createByte(byte val) {
            return new PrimitiveNativeWrapper(PRIMITIVE_STATE_BYTE, val);
        }

        public static PrimitiveNativeWrapper createInt(int val) {
            return new PrimitiveNativeWrapper(PRIMITIVE_STATE_INT, val);
        }

        public static PrimitiveNativeWrapper createLong(long val) {
            return new PrimitiveNativeWrapper(PRIMITIVE_STATE_LONG, val);
        }

        public static PrimitiveNativeWrapper createDouble(double val) {
            return new PrimitiveNativeWrapper(val);
        }
    }

    /**
     * Used to wrap {@link PythonClass} when used in native code. This wrapper mimics the correct
     * shape of the corresponding native type {@code struct _typeobject}.
     */
    public static class PythonClassNativeWrapper extends PythonObjectNativeWrapper {
        private final CStringWrapper nameWrapper;
        private Object getBufferProc;
        private Object releaseBufferProc;

        public PythonClassNativeWrapper(PythonClass object) {
            super(object);
            this.nameWrapper = new CStringWrapper(object.getName());
        }

        public CStringWrapper getNameWrapper() {
            return nameWrapper;
        }

        public Object getGetBufferProc() {
            return getBufferProc;
        }

        public void setGetBufferProc(Object getBufferProc) {
            this.getBufferProc = getBufferProc;
        }

        public Object getReleaseBufferProc() {
            return releaseBufferProc;
        }

        public void setReleaseBufferProc(Object releaseBufferProc) {
            this.releaseBufferProc = releaseBufferProc;
        }

        public static PythonClassNativeWrapper wrap(PythonClass obj) {
            // important: native wrappers are cached
            PythonClassNativeWrapper nativeWrapper = obj.getNativeWrapper();
            if (nativeWrapper == null) {
                nativeWrapper = new PythonClassNativeWrapper(obj);
                obj.setNativeWrapper(nativeWrapper);
            }
            return nativeWrapper;
        }

        @Override
        public String toString() {
            return String.format("PythonClassNativeWrapper(%s, isNative=%s)", getPythonObject(), isNative());
        }
    }

    /**
     * Used to wrap {@link PythonClass} just for the time when a natively defined type is processed
     * in {@code PyType_Ready} and we need to pass the mirroring managed class to native to marry
     * these two objects.
     */
    public static class PythonClassInitNativeWrapper extends PythonObjectNativeWrapper {

        public PythonClassInitNativeWrapper(PythonClass object) {
            super(object);
        }

        @Override
        public String toString() {
            return String.format("PythonClassNativeInitWrapper(%s, isNative=%s)", getPythonObject(), isNative());
        }
    }

    /**
     * Wraps a sequence object (like a list) such that it behaves like a bare C array.
     */
    @ExportLibrary(InteropLibrary.class)
    public static final class PySequenceArrayWrapper extends PythonNativeWrapper {

        /** Number of bytes that constitute a single element. */
        private final int elementAccessSize;

        public PySequenceArrayWrapper(Object delegate, int elementAccessSize) {
            super(delegate);
            this.elementAccessSize = elementAccessSize;
        }

        public int getElementAccessSize() {
            return elementAccessSize;
        }

        static boolean isInstance(TruffleObject o) {
            return o instanceof PySequenceArrayWrapper;
        }

        @Override
        public ForeignAccess getForeignAccess() {
            return PySequenceArrayWrapperMRForeign.ACCESS;
        }

        @ExportMessage
        final long getArraySize(
                @Cached.Shared("getSequenceStorageNode") @Cached(allowUncached = true) SequenceNodes.GetSequenceStorageNode getSequenceStorageNode,
                @Cached.Shared("lenNode") @Cached(allowUncached = true) SequenceStorageNodes.LenNode lenNode) {
            return lenNode.execute(getSequenceStorageNode.execute(this.getDelegate()));
        }

        @ExportMessage
        final boolean hasArrayElements() {
            return true;
        }

        @ExportMessage
        final Object readArrayElement(long index,
                           @Cached.Exclusive  @Cached(allowUncached = true) ReadArrayItemNode readArrayItemNode) {
            return readArrayItemNode.execute(this.getDelegate(), index);
        }

        @ExportMessage
        final boolean isArrayElementReadable(long identifier,
                 @Cached.Shared("getSequenceStorageNode") @Cached(allowUncached = true) SequenceNodes.GetSequenceStorageNode getSequenceStorageNode,
                 @Cached.Shared("lenNode") @Cached(allowUncached = true) SequenceStorageNodes.LenNode lenNode) {
            return 0 <= identifier && identifier < getArraySize(getSequenceStorageNode, lenNode);
        }

        @ImportStatic(SpecialMethodNames.class)
        @TypeSystemReference(PythonTypes.class)
        abstract static class ReadArrayItemNode extends Node {

            public abstract Object execute(Object arrayObject, Object idx);

            @Specialization
            Object doTuple(PTuple tuple, long idx,
                           @Cached(value = "createTupleGetItem()", allowUncached = true) TupleBuiltins.GetItemNode getItemNode,
                           @Cached.Shared("toSulongNode") @Cached CExtNodes.ToSulongNode toSulongNode) {
                return toSulongNode.execute(getItemNode.execute(tuple, idx));
            }

            @Specialization
            Object doTuple(PList list, long idx,
                           @Cached("createListGetItem()") ListBuiltins.GetItemNode getItemNode,
                           @Cached.Shared("toSulongNode") @Cached CExtNodes.ToSulongNode toSulongNode) {
                return toSulongNode.execute(getItemNode.execute(list, idx));
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
                            @Cached("create()") SequenceStorageNodes.GetItemNode getItemNode,
                            @Cached.Shared("toSulongNode") @Cached CExtNodes.ToSulongNode toSulongNode) {
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
                             @Cached("create(__GETITEM__)") LookupAndCallBinaryNode getItemNode,
                             @Cached.Shared("toSulongNode") @Cached CExtNodes.ToSulongNode toSulongNode) {
                return toSulongNode.execute(getItemNode.executeObject(object, idx));
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

            public static ReadArrayItemNode create() {
                return NativeWrappersFactory.PySequenceArrayWrapperFactory.ReadArrayItemNodeGen.create();
            }
        }

        @ExportMessage
        public void writeArrayElement(long index, Object value,
              @Cached.Exclusive @Cached(allowUncached = true) WriteArrayItemNode writeArrayItemNode) {
            writeArrayItemNode.execute(this.getDelegate(), index, value);
        }

        @ExportMessage
        public void removeArrayElement(long index) throws UnsupportedMessageException, InvalidArrayIndexException {
            throw UnsupportedMessageException.create();
        }

        @ExportMessage
        public boolean isArrayElementModifiable(long index,
                @Cached.Shared("getSequenceStorageNode") @Cached(allowUncached = true) SequenceNodes.GetSequenceStorageNode getSequenceStorageNode,
                @Cached.Shared("lenNode") @Cached(allowUncached = true) SequenceStorageNodes.LenNode lenNode) {
            return 0 <= index && index < getArraySize(getSequenceStorageNode, lenNode);
        }

        @ExportMessage
        public boolean isArrayElementInsertable(long index,
                @Cached.Shared("getSequenceStorageNode") @Cached(allowUncached = true) SequenceNodes.GetSequenceStorageNode getSequenceStorageNode,
                @Cached.Shared("lenNode") @Cached(allowUncached = true) SequenceStorageNodes.LenNode lenNode) {
            return 0 <= index && index <= getArraySize(getSequenceStorageNode, lenNode);
        }

        @ExportMessage
        public boolean isArrayElementRemovable(long index,
               @Cached.Shared("getSequenceStorageNode") @Cached(allowUncached = true) SequenceNodes.GetSequenceStorageNode getSequenceStorageNode,
               @Cached.Shared("lenNode") @Cached(allowUncached = true) SequenceStorageNodes.LenNode lenNode) {
            return 0 <= index && index < getArraySize(getSequenceStorageNode, lenNode);
        }

        @ImportStatic(SpecialMethodNames.class)
        @TypeSystemReference(PythonTypes.class)
        abstract static class WriteArrayItemNode extends Node {
            public abstract Object execute(Object arrayObject, Object idx, Object value);

            @Specialization
            Object doBytes(PIBytesLike s, long idx, byte value,
                           @Cached.Shared("getSequenceStorageNode") @Cached SequenceNodes.GetSequenceStorageNode getSequenceStorageNode,
                           @Cached.Shared("setByteItemNode") @Cached("create(__SETITEM__)") SequenceStorageNodes.SetItemNode setByteItemNode) {
                setByteItemNode.executeLong(getSequenceStorageNode.execute(s), idx, value);
                return value;
            }

            @Specialization
            @ExplodeLoop
            Object doBytes(PIBytesLike s, long idx, short value,
                           @Cached.Shared("getSequenceStorageNode") @Cached SequenceNodes.GetSequenceStorageNode getSequenceStorageNode,
                           @Cached.Shared("setByteItemNode") @Cached("create(__SETITEM__)") SequenceStorageNodes.SetItemNode setByteItemNode) {
                for (int offset = 0; offset < Short.BYTES; offset++) {
                    setByteItemNode.executeLong(getSequenceStorageNode.execute(s), idx + offset, (byte) (value >> (8 * offset)) & 0xFF);
                }
                return value;
            }

            @Specialization
            @ExplodeLoop
            Object doBytes(PIBytesLike s, long idx, int value,
                           @Cached.Shared("getSequenceStorageNode") @Cached SequenceNodes.GetSequenceStorageNode getSequenceStorageNode,
                           @Cached.Shared("setByteItemNode") @Cached("create(__SETITEM__)") SequenceStorageNodes.SetItemNode setByteItemNode) {
                for (int offset = 0; offset < Integer.BYTES; offset++) {
                    setByteItemNode.executeLong(getSequenceStorageNode.execute(s), idx + offset, (byte) (value >> (8 * offset)) & 0xFF);
                }
                return value;
            }

            @Specialization
            @ExplodeLoop
            Object doBytes(PIBytesLike s, long idx, long value,
                           @Cached.Shared("getSequenceStorageNode") @Cached SequenceNodes.GetSequenceStorageNode getSequenceStorageNode,
                           @Cached.Shared("setByteItemNode") @Cached("create(__SETITEM__)") SequenceStorageNodes.SetItemNode setByteItemNode) {
                for (int offset = 0; offset < Long.BYTES; offset++) {
                    setByteItemNode.executeLong(getSequenceStorageNode.execute(s), idx + offset, (byte) (value >> (8 * offset)) & 0xFF);
                }
                return value;
            }

            @Specialization
            Object doList(PList s, long idx, Object value,
                          @Cached.Shared("toJavaNode") @Cached CExtNodes.ToJavaNode toJavaNode,
                          @Cached("createSetListItem()") SequenceStorageNodes.SetItemNode setListItemNode,
                          @Cached("createBinaryProfile()") ConditionProfile updateStorageProfile) {
                SequenceStorage storage = s.getSequenceStorage();
                SequenceStorage updatedStorage = setListItemNode.executeLong(storage, idx, toJavaNode.execute(value));
                if (updateStorageProfile.profile(storage != updatedStorage)) {
                    s.setSequenceStorage(updatedStorage);
                }
                return value;
            }

            @Specialization
            Object doTuple(PTuple s, long idx, Object value,
                           @Cached.Shared("toJavaNode") @Cached CExtNodes.ToJavaNode toJavaNode,
                           @Cached("createSetItem()") SequenceStorageNodes.SetItemNode setListItemNode) {
                setListItemNode.executeLong(s.getSequenceStorage(), idx, toJavaNode.execute(value));
                return value;
            }

            @Fallback
            Object doGeneric(Object sequence, Object idx, Object value) {
                CExtNodes.ToJavaNode toJavaNode = CExtNodes.ToJavaNode.getUncached();
                LookupAndCallTernaryNode setItemNode = LookupAndCallTernaryNode.getUncached();
                setItemNode.execute(sequence, idx, toJavaNode.execute(value));
                return value;
            }

            protected static SequenceStorageNodes.SetItemNode createSetListItem() {
                return SequenceStorageNodes.SetItemNode.create(SequenceStorageNodes.NormalizeIndexNode.forArrayAssign(), () -> SequenceStorageNodes.ListGeneralizationNode.create());
            }

            protected static SequenceStorageNodes.SetItemNode createSetItem() {
                return SequenceStorageNodes.SetItemNode.create("invalid item for assignment");
            }

            public static WriteArrayItemNode create() {
                return NativeWrappersFactory.PySequenceArrayWrapperFactory.WriteArrayItemNodeGen.create();
            }
        }

        @ExportMessage
        public void toNative(@Cached.Exclusive @Cached(allowUncached = true) ToNativeArrayNode toPyObjectNode,
                             @Cached.Exclusive @Cached(allowUncached = true) PythonObjectNativeWrapperMR.InvalidateNativeObjectsAllManagedNode invalidateNode) {
            invalidateNode.execute();
            if (!this.isNative()) {
                this.setNativePointer(toPyObjectNode.execute(this));
            }
        }

        abstract static class ToNativeArrayNode extends CExtNodes.CExtBaseNode {
            public abstract Object execute(PySequenceArrayWrapper object);

            @Specialization(guards = "isPSequence(object.getDelegate())")
            Object doPSequence(PySequenceArrayWrapper object,
                       @Cached.Exclusive @Cached ToNativeStorageNode toNativeStorageNode) {
                PSequence sequence = (PSequence) object.getDelegate();
                NativeSequenceStorage nativeStorage = toNativeStorageNode.execute(sequence.getSequenceStorage());
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
                PCallNativeNode callNativeBinary = PCallNativeNode.getUncached();
                TruffleObject PyObjectHandle_FromJavaObject = importCAPISymbol(NativeCAPISymbols.FUN_NATIVE_HANDLE_FOR_ARRAY);
                return callBinaryIntoCapi(PyObjectHandle_FromJavaObject, object, 8L, callNativeBinary);
            }


            protected boolean isPSequence(Object obj) {
                return obj instanceof PSequence;
            }

            private Object callBinaryIntoCapi(TruffleObject fun, Object arg0, Object arg1, PCallNativeNode callNativeBinary) {
                return callNativeBinary.execute(fun, new Object[]{arg0, arg1});
            }

            public static ToNativeArrayNode create() {
                return NativeWrappersFactory.PySequenceArrayWrapperFactory.ToNativeArrayNodeGen.create();
            }
        }

        static abstract class ToNativeStorageNode extends PNodeWithContext {
            @Child private SequenceStorageNodes.StorageToNativeNode storageToNativeNode;

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

            private SequenceStorageNodes.StorageToNativeNode getStorageToNativeNode() {
                if (storageToNativeNode == null) {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    storageToNativeNode = insert(SequenceStorageNodes.StorageToNativeNode.create());
                }
                return storageToNativeNode;
            }

            public static ToNativeStorageNode create() {
                return NativeWrappersFactory.PySequenceArrayWrapperFactory.ToNativeStorageNodeGen.create();
            }
        }

        @ExportMessage
        public boolean isPointer(@Cached.Exclusive @Cached(allowUncached = true) CExtNodes.IsPointerNode pIsPointerNode) {
            return pIsPointerNode.execute(this);
        }

        @ExportMessage
        public long asPointer(@CachedLibrary(limit = "1") InteropLibrary interopLibrary) throws UnsupportedMessageException {
            Object nativePointer = this.getNativePointer();
            if (nativePointer instanceof Long) {
                return (long) nativePointer;
            }
            return interopLibrary.asPointer(nativePointer);
        }

    }

    public static class TruffleObjectNativeWrapper extends PythonNativeWrapper {

        public TruffleObjectNativeWrapper(TruffleObject foreignObject) {
            super(foreignObject);
        }

        public static TruffleObjectNativeWrapper wrap(TruffleObject foreignObject) {
            assert !(foreignObject instanceof PythonNativeWrapper) : "attempting to wrap a native wrapper";
            return new TruffleObjectNativeWrapper(foreignObject);
        }
    }

    @ExportLibrary(InteropLibrary.class)
    abstract static class PyUnicodeWrapper extends PythonNativeWrapper {

        public PyUnicodeWrapper(PString delegate) {
            super(delegate);
        }

        public PString getPString() {
            return (PString) getDelegate();
        }

        @Override
        public ForeignAccess getForeignAccess() {
            return PyUnicodeWrapperMRForeign.ACCESS;
        }

        static boolean isInstance(TruffleObject o) {
            return o instanceof PyUnicodeWrapper;
        }
    }

    /**
     * A native wrapper for the {@code data} member of {@code PyUnicodeObject}.
     */
    @ExportLibrary(InteropLibrary.class)
    public static class PyUnicodeData extends PyUnicodeWrapper {
        public PyUnicodeData(PString delegate) {
            super(delegate);
        }

        @ExportMessage
        boolean hasMembers() {
            return true;
        }

        @ExportMessage
        protected boolean isMemberReadable(String member) {
            switch (member) {
                case NativeMemberNames.UNICODE_DATA_ANY:
                case NativeMemberNames.UNICODE_DATA_LATIN1:
                case NativeMemberNames.UNICODE_DATA_UCS2:
                case NativeMemberNames.UNICODE_DATA_UCS4:
                    return true;
                default:
                    return false;
            }
        }

        @ExportMessage
        protected Object getMembers(boolean includeInternal) throws UnsupportedMessageException {
            throw UnsupportedMessageException.create();
        }

        @ExportMessage
        protected Object readMember(String member,
                    @Cached.Exclusive @Cached(value = "create(0)", allowUncached = true) UnicodeObjectNodes.UnicodeAsWideCharNode asWideCharNode,
                    @Cached.Exclusive @Cached(allowUncached = true) CExtNodes.SizeofWCharNode sizeofWcharNode) {
            switch (member) {
                case NativeMemberNames.UNICODE_DATA_ANY:
                case NativeMemberNames.UNICODE_DATA_LATIN1:
                case NativeMemberNames.UNICODE_DATA_UCS2:
                case NativeMemberNames.UNICODE_DATA_UCS4:
                    int elementSize = (int) sizeofWcharNode.execute();
                    PString s = this.getPString();
                    return new PySequenceArrayWrapper(asWideCharNode.execute(s, elementSize, s.len()), elementSize);
            }
            throw UnknownIdentifierException.raise(member);
        }
    }

    /**
     * A native wrapper for the {@code state} member of {@code PyASCIIObject}.
     */
    @ExportLibrary(InteropLibrary.class)
    public static class PyUnicodeState extends PyUnicodeWrapper {
        @CompilationFinal private CharsetEncoder asciiEncoder;

        public PyUnicodeState(PString delegate) {
            super(delegate);
        }

        @ExportMessage
        boolean hasMembers() {
            return true;
        }

        @ExportMessage
        protected boolean isMemberReadable(String member) {
            switch (member) {
                case NativeMemberNames.UNICODE_STATE_INTERNED:
                case NativeMemberNames.UNICODE_STATE_KIND:
                case NativeMemberNames.UNICODE_STATE_COMPACT:
                case NativeMemberNames.UNICODE_STATE_ASCII:
                case NativeMemberNames.UNICODE_STATE_READY:
                    return true;
                default:
                    return false;
            }
        }

        @ExportMessage
        protected Object getMembers(boolean includeInternal) throws UnsupportedMessageException {
            throw UnsupportedMessageException.create();
        }

        @ExportMessage
        protected Object readMember(String member,
                                    @Cached.Exclusive @Cached(allowUncached = true) CExtNodes.SizeofWCharNode sizeofWcharNode) {
            // padding(24), ready(1), ascii(1), compact(1), kind(3), interned(2)
            int value = 0b000000000000000000000000_1_0_0_000_00;
            if (onlyAscii(this.getPString().getValue())) {
                value |= 0b1_0_000_00;
            }
            value |= ((int) sizeofWcharNode.execute() << 2) & 0b11100;
            switch (member) {
                case NativeMemberNames.UNICODE_STATE_INTERNED:
                case NativeMemberNames.UNICODE_STATE_KIND:
                case NativeMemberNames.UNICODE_STATE_COMPACT:
                case NativeMemberNames.UNICODE_STATE_ASCII:
                case NativeMemberNames.UNICODE_STATE_READY:
                    // it's a bit field; so we need to return the whole 32-bit word
                    return value;
            }
            throw UnknownIdentifierException.raise(member);
        }

        private boolean onlyAscii(String value) {
            if (asciiEncoder == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                asciiEncoder = Charset.forName("US-ASCII").newEncoder();
            }
            return doCheck(value, asciiEncoder);
        }

        @TruffleBoundary
        private static boolean doCheck(String value, CharsetEncoder asciiEncoder) {
            return asciiEncoder.canEncode(value);
        }


    }

    public static class PThreadState extends PythonNativeWrapper {

        private PDict dict;

        static boolean isInstance(TruffleObject obj) {
            return obj instanceof PThreadState;
        }

        @Override
        public ForeignAccess getForeignAccess() {
            return PThreadStateMRForeign.ACCESS;
        }

        public PDict getThreadStateDict() {
            return dict;
        }

        public void setThreadStateDict(PDict dict) {
            this.dict = dict;
        }
    }
}
