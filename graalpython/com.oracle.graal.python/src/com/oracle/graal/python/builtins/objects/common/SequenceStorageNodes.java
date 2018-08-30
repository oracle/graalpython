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
package com.oracle.graal.python.builtins.objects.common;

import static com.oracle.graal.python.nodes.SpecialMethodNames.__EQ__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__GE__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__GT__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__LE__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__LT__;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.IndexError;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.MemoryError;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.OverflowError;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.SystemError;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.TypeError;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.ValueError;
import static com.oracle.graal.python.runtime.sequence.storage.SequenceStorage.ListStorageType.Boolean;
import static com.oracle.graal.python.runtime.sequence.storage.SequenceStorage.ListStorageType.Byte;
import static com.oracle.graal.python.runtime.sequence.storage.SequenceStorage.ListStorageType.Char;
import static com.oracle.graal.python.runtime.sequence.storage.SequenceStorage.ListStorageType.Double;
import static com.oracle.graal.python.runtime.sequence.storage.SequenceStorage.ListStorageType.Int;
import static com.oracle.graal.python.runtime.sequence.storage.SequenceStorage.ListStorageType.List;
import static com.oracle.graal.python.runtime.sequence.storage.SequenceStorage.ListStorageType.Long;
import static com.oracle.graal.python.runtime.sequence.storage.SequenceStorage.ListStorageType.Tuple;
import static com.oracle.graal.python.runtime.sequence.storage.SequenceStorage.ListStorageType.Uninitialized;

import java.util.Arrays;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;

import com.oracle.graal.python.builtins.objects.cext.CExtNodes.PCallBinaryCapiFunction;
import com.oracle.graal.python.builtins.objects.cext.NativeCAPISymbols;
import com.oracle.graal.python.builtins.objects.common.SequenceStorageNodesFactory.AppendNodeGen;
import com.oracle.graal.python.builtins.objects.common.SequenceStorageNodesFactory.CastToByteNodeGen;
import com.oracle.graal.python.builtins.objects.common.SequenceStorageNodesFactory.CmpNodeGen;
import com.oracle.graal.python.builtins.objects.common.SequenceStorageNodesFactory.ConcatBaseNodeGen;
import com.oracle.graal.python.builtins.objects.common.SequenceStorageNodesFactory.ConcatNodeGen;
import com.oracle.graal.python.builtins.objects.common.SequenceStorageNodesFactory.ContainsNodeGen;
import com.oracle.graal.python.builtins.objects.common.SequenceStorageNodesFactory.CreateEmptyNodeGen;
import com.oracle.graal.python.builtins.objects.common.SequenceStorageNodesFactory.DeleteItemNodeGen;
import com.oracle.graal.python.builtins.objects.common.SequenceStorageNodesFactory.DeleteNodeGen;
import com.oracle.graal.python.builtins.objects.common.SequenceStorageNodesFactory.DeleteSliceNodeGen;
import com.oracle.graal.python.builtins.objects.common.SequenceStorageNodesFactory.EnsureCapacityNodeGen;
import com.oracle.graal.python.builtins.objects.common.SequenceStorageNodesFactory.ExtendNodeGen;
import com.oracle.graal.python.builtins.objects.common.SequenceStorageNodesFactory.GetElementTypeNodeGen;
import com.oracle.graal.python.builtins.objects.common.SequenceStorageNodesFactory.GetItemNodeGen;
import com.oracle.graal.python.builtins.objects.common.SequenceStorageNodesFactory.GetItemScalarNodeGen;
import com.oracle.graal.python.builtins.objects.common.SequenceStorageNodesFactory.GetItemSliceNodeGen;
import com.oracle.graal.python.builtins.objects.common.SequenceStorageNodesFactory.ItemIndexNodeGen;
import com.oracle.graal.python.builtins.objects.common.SequenceStorageNodesFactory.LenNodeGen;
import com.oracle.graal.python.builtins.objects.common.SequenceStorageNodesFactory.ListGeneralizationNodeGen;
import com.oracle.graal.python.builtins.objects.common.SequenceStorageNodesFactory.NoGeneralizationNodeGen;
import com.oracle.graal.python.builtins.objects.common.SequenceStorageNodesFactory.NormalizeIndexNodeGen;
import com.oracle.graal.python.builtins.objects.common.SequenceStorageNodesFactory.RepeatNodeGen;
import com.oracle.graal.python.builtins.objects.common.SequenceStorageNodesFactory.SetItemNodeGen;
import com.oracle.graal.python.builtins.objects.common.SequenceStorageNodesFactory.SetItemScalarNodeGen;
import com.oracle.graal.python.builtins.objects.common.SequenceStorageNodesFactory.SetItemSliceNodeGen;
import com.oracle.graal.python.builtins.objects.common.SequenceStorageNodesFactory.SetLenNodeGen;
import com.oracle.graal.python.builtins.objects.common.SequenceStorageNodesFactory.SetStorageSliceNodeGen;
import com.oracle.graal.python.builtins.objects.common.SequenceStorageNodesFactory.StorageToNativeNodeGen;
import com.oracle.graal.python.builtins.objects.common.SequenceStorageNodesFactory.ToByteArrayNodeGen;
import com.oracle.graal.python.builtins.objects.common.SequenceStorageNodesFactory.VerifyNativeItemNodeGen;
import com.oracle.graal.python.builtins.objects.ints.PInt;
import com.oracle.graal.python.builtins.objects.list.PList;
import com.oracle.graal.python.builtins.objects.slice.PSlice;
import com.oracle.graal.python.builtins.objects.slice.PSlice.SliceInfo;
import com.oracle.graal.python.builtins.objects.str.PString;
import com.oracle.graal.python.builtins.objects.tuple.PTuple;
import com.oracle.graal.python.nodes.PBaseNode;
import com.oracle.graal.python.nodes.SpecialMethodNames;
import com.oracle.graal.python.nodes.builtins.ListNodes;
import com.oracle.graal.python.nodes.control.GetIteratorNode;
import com.oracle.graal.python.nodes.control.GetNextNode;
import com.oracle.graal.python.nodes.datamodel.IsIndexNode;
import com.oracle.graal.python.nodes.expression.BinaryComparisonNode;
import com.oracle.graal.python.nodes.expression.CastToBooleanNode;
import com.oracle.graal.python.nodes.util.CastToIndexNode;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.graal.python.runtime.exception.PythonErrorType;
import com.oracle.graal.python.runtime.object.PythonObjectFactory;
import com.oracle.graal.python.runtime.sequence.PSequence;
import com.oracle.graal.python.runtime.sequence.storage.BasicSequenceStorage;
import com.oracle.graal.python.runtime.sequence.storage.BoolSequenceStorage;
import com.oracle.graal.python.runtime.sequence.storage.ByteSequenceStorage;
import com.oracle.graal.python.runtime.sequence.storage.CharSequenceStorage;
import com.oracle.graal.python.runtime.sequence.storage.DoubleSequenceStorage;
import com.oracle.graal.python.runtime.sequence.storage.EmptySequenceStorage;
import com.oracle.graal.python.runtime.sequence.storage.IntSequenceStorage;
import com.oracle.graal.python.runtime.sequence.storage.ListSequenceStorage;
import com.oracle.graal.python.runtime.sequence.storage.LongSequenceStorage;
import com.oracle.graal.python.runtime.sequence.storage.NativeSequenceStorage;
import com.oracle.graal.python.runtime.sequence.storage.ObjectSequenceStorage;
import com.oracle.graal.python.runtime.sequence.storage.RangeSequenceStorage;
import com.oracle.graal.python.runtime.sequence.storage.SequenceStorage;
import com.oracle.graal.python.runtime.sequence.storage.SequenceStorage.ListStorageType;
import com.oracle.graal.python.runtime.sequence.storage.SequenceStoreException;
import com.oracle.graal.python.runtime.sequence.storage.TupleSequenceStorage;
import com.oracle.graal.python.runtime.sequence.storage.TypedSequenceStorage;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.interop.ForeignAccess;
import com.oracle.truffle.api.interop.InteropException;
import com.oracle.truffle.api.interop.Message;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.profiles.BranchProfile;
import com.oracle.truffle.api.profiles.ConditionProfile;
import com.oracle.truffle.api.profiles.ValueProfile;

public abstract class SequenceStorageNodes {

    abstract static class SequenceStorageBaseNode extends PBaseNode {

        @Child private GetElementType getElementTypeNode;

        protected static final int DEFAULT_CAPACITY = 8;

        protected static final int MAX_SEQUENCE_STORAGES = 12;
        protected static final int MAX_ARRAY_STORAGES = 9;

        protected static boolean isByteStorage(NativeSequenceStorage store) {
            return store.getElementType() == ListStorageType.Byte;
        }

        /**
         * Tests if {@code left} has the same element type as {@code right}.
         */
        protected boolean compatible(SequenceStorage left, NativeSequenceStorage right) {
            switch (right.getElementType()) {
                case Boolean:
                    return left instanceof BoolSequenceStorage;
                case Byte:
                    return left instanceof ByteSequenceStorage;
                case Char:
                    return left instanceof CharSequenceStorage;
                case Int:
                    return left instanceof IntSequenceStorage;
                case Long:
                    return left instanceof LongSequenceStorage;
                case Double:
                    return left instanceof DoubleSequenceStorage;
                case Generic:
                    return left instanceof ObjectSequenceStorage || left instanceof TupleSequenceStorage || left instanceof ListSequenceStorage;
            }
            assert false : "should not reach";
            return false;
        }

        /**
         * Tests if each element of {@code rhs} can be assign to {@code lhs} without casting.
         */
        protected boolean compatibleAssign(SequenceStorage lhs, SequenceStorage rhs) {
            ListStorageType rhsType = getElementType(rhs);
            switch (getElementType(lhs)) {
                case Boolean:
                    return rhsType == Boolean || rhsType == Uninitialized;
                case Byte:
                    return rhsType == Boolean || rhsType == Byte || rhsType == Uninitialized;
                case Int:
                    return rhsType == Boolean || rhsType == ListStorageType.Byte || rhsType == ListStorageType.Int || rhsType == Uninitialized;
                case Long:
                    return rhsType == Boolean || rhsType == Byte || rhsType == Int || rhsType == Long || rhsType == Uninitialized;
                case Double:
                    return rhsType == Double || rhsType == Uninitialized;
                case Char:
                    return rhsType == Char || rhsType == Uninitialized;
                case Tuple:
                    return rhsType == Tuple || rhsType == Uninitialized;
                case List:
                    return rhsType == List || rhsType == Uninitialized;
                case Generic:
                    return true;
                case Uninitialized:
                    return false;
            }
            assert false : "should not reach";
            return false;
        }

        /**
         * Tests if elements of {@code rhs} can be assign to {@code lhs} with casting.
         */
        protected boolean compatibleDataType(SequenceStorage lhs, SequenceStorage rhs) {
            ListStorageType rhsType = getElementType(rhs);
            switch (getElementType(lhs)) {
                case Boolean:
                case Byte:
                case Int:
                case Long:
                    return rhsType == Boolean || rhsType == Byte || rhsType == Int || rhsType == Long || rhsType == Uninitialized;
                case Double:
                    return rhsType == Double || rhsType == Uninitialized;
                case Char:
                    return rhsType == Char || rhsType == Uninitialized;
                case Tuple:
                    return rhsType == Tuple || rhsType == Uninitialized;
                case List:
                    return rhsType == List || rhsType == Uninitialized;
                case Generic:
                    return true;
                case Uninitialized:
                    return false;
            }
            assert false : "should not reach";
            return false;
        }

        protected static boolean isNative(SequenceStorage store) {
            return store instanceof NativeSequenceStorage;
        }

        protected boolean isEmpty(SequenceStorage left) {
            // TODO use a node or profile
            return left instanceof EmptySequenceStorage || left.length() == 0;
        }

        private ListStorageType getElementType(SequenceStorage s) {
            if (getElementTypeNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                getElementTypeNode = insert(GetElementTypeNodeGen.create());
            }
            return getElementTypeNode.execute(s);
        }

        protected boolean isBoolean(SequenceStorage s) {
            return getElementType(s) == ListStorageType.Boolean;
        }

        protected boolean isByte(SequenceStorage s) {
            return getElementType(s) == ListStorageType.Byte;
        }

        protected boolean isByteLike(SequenceStorage s) {
            return isByte(s) || isInt(s) || isLong(s);
        }

        protected boolean isChar(SequenceStorage s) {
            return getElementType(s) == ListStorageType.Char;
        }

        protected boolean isInt(SequenceStorage s) {
            return getElementType(s) == ListStorageType.Int;
        }

        protected boolean isLong(SequenceStorage s) {
            return getElementType(s) == ListStorageType.Long;
        }

        protected boolean isDouble(SequenceStorage s) {
            return getElementType(s) == ListStorageType.Double;
        }

        protected boolean isObject(SequenceStorage s) {
            return getElementType(s) == ListStorageType.Generic;
        }

        protected boolean isTuple(SequenceStorage s) {
            return getElementType(s) == ListStorageType.Tuple;
        }

        protected boolean isList(SequenceStorage s) {
            return getElementType(s) == ListStorageType.List;
        }

        protected static boolean hasStorage(Object source) {
            return source instanceof PSequence && !(source instanceof PString);
        }
    }

    abstract static class NormalizingNode extends PBaseNode {

        protected static final String KEY_TYPE_ERROR_MESSAGE = "indices must be integers or slices, not %p";
        @Child private NormalizeIndexNode normalizeIndexNode;
        @Child private CastToIndexNode castToIndexNode;
        @CompilationFinal private ValueProfile storeProfile;

        protected NormalizingNode(NormalizeIndexNode normalizeIndexNode) {
            this.normalizeIndexNode = normalizeIndexNode;
        }

        private CastToIndexNode getCastToIndexNode() {
            if (castToIndexNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                castToIndexNode = insert(CastToIndexNode.create());
            }
            return castToIndexNode;
        }

        protected final int normalizeIndex(Object idx, SequenceStorage store) {
            int intIdx = getCastToIndexNode().execute(idx);
            if (normalizeIndexNode != null) {
                return normalizeIndexNode.doInt(intIdx, getStoreProfile().profile(store).length());
            }
            return intIdx;
        }

        protected final int normalizeIndex(int idx, SequenceStorage store) {
            if (normalizeIndexNode != null) {
                return normalizeIndexNode.doInt(idx, getStoreProfile().profile(store).length());
            }
            return idx;
        }

        protected final int normalizeIndex(long idx, SequenceStorage store) {
            int intIdx = getCastToIndexNode().execute(idx);
            if (normalizeIndexNode != null) {
                return normalizeIndexNode.doInt(intIdx, getStoreProfile().profile(store).length());
            }
            return intIdx;
        }

        private ValueProfile getStoreProfile() {
            if (storeProfile == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                storeProfile = ValueProfile.createClassProfile();
            }
            return storeProfile;
        }

        protected static boolean isPSlice(Object obj) {
            return obj instanceof PSlice;
        }

    }

    public abstract static class GetItemNode extends NormalizingNode {

        @Child private GetItemScalarNode getItemScalarNode;
        @Child private GetItemSliceNode getItemSliceNode;
        private final String keyTypeErrorMessage;
        private final BiFunction<SequenceStorage, PythonObjectFactory, Object> factoryMethod;

        public GetItemNode(NormalizeIndexNode normalizeIndexNode, String keyTypeErrorMessage, BiFunction<SequenceStorage, PythonObjectFactory, Object> factoryMethod) {
            super(normalizeIndexNode);
            this.keyTypeErrorMessage = keyTypeErrorMessage;
            this.factoryMethod = factoryMethod;
        }

        public abstract Object execute(SequenceStorage s, Object key);

        public abstract Object execute(SequenceStorage s, int key);

        public abstract Object execute(SequenceStorage s, long key);

        public abstract int executeInt(SequenceStorage s, int key);

        public abstract long executeLong(SequenceStorage s, long key);

        @Specialization
        protected Object doScalarInt(SequenceStorage storage, int idx) {
            return getGetItemScalarNode().execute(storage, normalizeIndex(idx, storage));
        }

        @Specialization
        protected Object doScalarLong(SequenceStorage storage, long idx) {
            return getGetItemScalarNode().execute(storage, normalizeIndex(idx, storage));
        }

        @Specialization
        protected Object doScalarPInt(SequenceStorage storage, PInt idx) {
            return getGetItemScalarNode().execute(storage, normalizeIndex(idx, storage));
        }

        @Specialization(guards = "!isPSlice(idx)")
        protected Object doScalarGeneric(SequenceStorage storage, Object idx) {
            return getGetItemScalarNode().execute(storage, normalizeIndex(idx, storage));
        }

        @Specialization
        protected Object doSlice(SequenceStorage storage, PSlice slice) {
            SliceInfo info = slice.computeActualIndices(storage.length());
            if (factoryMethod != null) {
                return factoryMethod.apply(getGetItemSliceNode().execute(storage, info.start, info.stop, info.step, info.length), factory());
            }
            CompilerDirectives.transferToInterpreter();
            throw new IllegalStateException();
        }

        @Fallback
        protected Object doInvalidKey(@SuppressWarnings("unused") SequenceStorage storage, Object key) {
            throw raise(TypeError, keyTypeErrorMessage, key);
        }

        private GetItemScalarNode getGetItemScalarNode() {
            if (getItemScalarNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                getItemScalarNode = insert(GetItemScalarNode.create());
            }
            return getItemScalarNode;
        }

        private GetItemSliceNode getGetItemSliceNode() {
            if (getItemSliceNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                getItemSliceNode = insert(GetItemSliceNode.create());
            }
            return getItemSliceNode;
        }

        public static GetItemNode createNotNormalized() {
            return GetItemNodeGen.create(null, KEY_TYPE_ERROR_MESSAGE, null);
        }

        public static GetItemNode create(NormalizeIndexNode normalizeIndexNode) {
            return GetItemNodeGen.create(normalizeIndexNode, KEY_TYPE_ERROR_MESSAGE, null);
        }

        public static GetItemNode create() {
            return GetItemNodeGen.create(NormalizeIndexNode.create(), KEY_TYPE_ERROR_MESSAGE, null);
        }

        public static GetItemNode createNotNormalized(String keyTypeErrorMessage) {
            return GetItemNodeGen.create(null, keyTypeErrorMessage, null);
        }

        public static GetItemNode create(NormalizeIndexNode normalizeIndexNode, String keyTypeErrorMessage) {
            return GetItemNodeGen.create(normalizeIndexNode, keyTypeErrorMessage, null);
        }

        public static GetItemNode create(String keyTypeErrorMessage) {
            return GetItemNodeGen.create(NormalizeIndexNode.create(), keyTypeErrorMessage, null);
        }

        public static GetItemNode create(NormalizeIndexNode normalizeIndexNode, String keyTypeErrorMessage, BiFunction<SequenceStorage, PythonObjectFactory, Object> factoryMethod) {
            return GetItemNodeGen.create(normalizeIndexNode, keyTypeErrorMessage, factoryMethod);
        }

        public static GetItemNode create(NormalizeIndexNode normalizeIndexNode, BiFunction<SequenceStorage, PythonObjectFactory, Object> factoryMethod) {
            return GetItemNodeGen.create(normalizeIndexNode, KEY_TYPE_ERROR_MESSAGE, factoryMethod);
        }

    }

    abstract static class GetItemScalarNode extends SequenceStorageBaseNode {

        @Child private Node readNode;
        @Child private VerifyNativeItemNode verifyNativeItemNode;

        @CompilationFinal private BranchProfile invalidTypeProfile;

        public abstract Object execute(SequenceStorage s, int idx);

        public static GetItemScalarNode create() {
            return GetItemScalarNodeGen.create();
        }

        public abstract boolean executeBoolean(SequenceStorage s, int idx);

        public abstract byte executeByte(SequenceStorage s, int idx);

        public abstract char executeChar(SequenceStorage s, int idx);

        public abstract int executeInt(SequenceStorage s, int idx);

        public abstract long executeLong(SequenceStorage s, int idx);

        public abstract double executeDouble(SequenceStorage s, int idx);

        @Specialization
        protected boolean doBoolean(BoolSequenceStorage storage, int idx) {
            return storage.getBoolItemNormalized(idx);
        }

        @Specialization
        protected int doByte(ByteSequenceStorage storage, int idx) {
            return storage.getIntItemNormalized(idx);
        }

        @Specialization
        protected char doChar(CharSequenceStorage storage, int idx) {
            return storage.getCharItemNormalized(idx);
        }

        @Specialization
        protected int doInt(IntSequenceStorage storage, int idx) {
            return storage.getIntItemNormalized(idx);
        }

        @Specialization
        protected int doRange(RangeSequenceStorage storage, int idx) {
            return storage.getIntItemNormalized(idx);
        }

        @Specialization
        protected long doLong(LongSequenceStorage storage, int idx) {
            return storage.getLongItemNormalized(idx);
        }

        @Specialization
        protected double doDouble(DoubleSequenceStorage storage, int idx) {
            return storage.getDoubleItemNormalized(idx);
        }

        @Specialization
        protected PList doList(ListSequenceStorage storage, int idx) {
            return storage.getListItemNormalized(idx);
        }

        @Specialization
        protected PTuple doTuple(TupleSequenceStorage storage, int idx) {
            return storage.getPTupleItemNormalized(idx);
        }

        @Specialization
        protected Object doObject(ObjectSequenceStorage storage, int idx) {
            return storage.getItemNormalized(idx);
        }

        @Specialization(guards = "!isByteStorage(storage)")
        protected Object doNative(NativeSequenceStorage storage, int idx) {
            try {
                return verifyResult(storage, ForeignAccess.sendRead(getReadNode(), (TruffleObject) storage.getPtr(), idx));
            } catch (InteropException e) {
                throw e.raise();
            }
        }

        @Specialization(guards = "isByteStorage(storage)")
        protected int doNativeByte(NativeSequenceStorage storage, int idx) {
            Object result = doNative(storage, idx);
            return (byte) result & 0xFF;
        }

        private Object verifyResult(NativeSequenceStorage storage, Object item) {
            if (verifyNativeItemNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                verifyNativeItemNode = insert(VerifyNativeItemNode.create());
            }
            if (invalidTypeProfile == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                invalidTypeProfile = BranchProfile.create();
            }
            if (!verifyNativeItemNode.execute(storage.getElementType(), item)) {
                invalidTypeProfile.enter();
                throw raise(SystemError, "Invalid item type %s returned from native sequence storage (expected: %s)", item, storage.getElementType());
            }
            return item;
        }

        private Node getReadNode() {
            if (readNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                readNode = insert(Message.READ.createNode());
            }
            return readNode;
        }

    }

    @ImportStatic(ListStorageType.class)
    abstract static class GetItemSliceNode extends SequenceStorageBaseNode {

        @Child private Node readNode;
        @Child private Node executeNode;

        public abstract SequenceStorage execute(SequenceStorage s, int start, int stop, int step, int length);

        @Specialization
        @SuppressWarnings("unused")
        protected EmptySequenceStorage doEmpty(EmptySequenceStorage storage, int start, int stop, int step, int length) {
            return EmptySequenceStorage.INSTANCE;
        }

        @Specialization(limit = "MAX_ARRAY_STORAGES", guards = {"storage.getClass() == cachedClass"})
        protected SequenceStorage doManagedStorage(BasicSequenceStorage storage, int start, int stop, int step, int length,
                        @Cached("storage.getClass()") Class<? extends BasicSequenceStorage> cachedClass) {
            return cachedClass.cast(storage).getSliceInBound(start, stop, step, length);
        }

        @Specialization(guards = "storage.getElementType() == Byte")
        protected NativeSequenceStorage doNativeByte(NativeSequenceStorage storage, int start, @SuppressWarnings("unused") int stop, int step, int length,
                        @Cached("create()") StorageToNativeNode storageToNativeNode) {
            byte[] newArray = new byte[length];
            for (int i = start, j = 0; j < length; i += step, j++) {
                newArray[j] = (byte) readNativeElement((TruffleObject) storage.getPtr(), i);
            }
            return storageToNativeNode.execute(newArray);
        }

        @Specialization(guards = "storage.getElementType() == Int")
        protected NativeSequenceStorage doNativeInt(NativeSequenceStorage storage, int start, @SuppressWarnings("unused") int stop, int step, int length,
                        @Cached("create()") StorageToNativeNode storageToNativeNode) {
            int[] newArray = new int[length];
            for (int i = start, j = 0; j < length; i += step, j++) {
                newArray[j] = (int) readNativeElement((TruffleObject) storage.getPtr(), i);
            }
            return storageToNativeNode.execute(newArray);
        }

        @Specialization(guards = "storage.getElementType() == Long")
        protected NativeSequenceStorage doNativeLong(NativeSequenceStorage storage, int start, @SuppressWarnings("unused") int stop, int step, int length,
                        @Cached("create()") StorageToNativeNode storageToNativeNode) {
            long[] newArray = new long[length];
            for (int i = start, j = 0; j < length; i += step, j++) {
                newArray[j] = (long) readNativeElement((TruffleObject) storage.getPtr(), i);
            }
            return storageToNativeNode.execute(newArray);
        }

        @Specialization(guards = "storage.getElementType() == Double")
        protected NativeSequenceStorage doNativeDouble(NativeSequenceStorage storage, int start, @SuppressWarnings("unused") int stop, int step, int length,
                        @Cached("create()") StorageToNativeNode storageToNativeNode) {
            double[] newArray = new double[length];
            for (int i = start, j = 0; j < length; i += step, j++) {
                newArray[j] = (double) readNativeElement((TruffleObject) storage.getPtr(), i);
            }
            return storageToNativeNode.execute(newArray);
        }

        @Specialization(guards = "storage.getElementType() == Generic")
        protected NativeSequenceStorage doNativeObject(NativeSequenceStorage storage, int start, @SuppressWarnings("unused") int stop, int step, int length,
                        @Cached("create()") StorageToNativeNode storageToNativeNode) {
            Object[] newArray = new Object[length];
            for (int i = start, j = 0; j < length; i += step, j++) {
                newArray[j] = readNativeElement((TruffleObject) storage.getPtr(), i);
            }
            return storageToNativeNode.execute(newArray);
        }

        private Object readNativeElement(TruffleObject ptr, int idx) {
            try {
                return ForeignAccess.sendRead(getReadNode(), ptr, idx);
            } catch (InteropException e) {
                throw e.raise();
            }
        }

        private Node getReadNode() {
            if (readNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                readNode = insert(Message.READ.createNode());
            }
            return readNode;
        }

        public static GetItemSliceNode create() {
            return GetItemSliceNodeGen.create();
        }
    }

    public abstract static class SetItemNode extends NormalizingNode {
        @Child private SetItemScalarNode setItemScalarNode;
        @Child private SetItemSliceNode setItemSliceNode;
        @Child private GeneralizationNode generalizationNode;
        @Child private SetItemNode recursive;

        private final BranchProfile generalizeProfile = BranchProfile.create();
        private final Supplier<GeneralizationNode> generalizationNodeProvider;

        public SetItemNode(NormalizeIndexNode normalizeIndexNode, Supplier<GeneralizationNode> generalizationNodeProvider) {
            super(normalizeIndexNode);
            this.generalizationNodeProvider = generalizationNodeProvider;
        }

        public abstract SequenceStorage execute(SequenceStorage s, Object key, Object value);

        public abstract SequenceStorage executeInt(SequenceStorage s, int key, Object value);

        public abstract SequenceStorage executeLong(SequenceStorage s, long key, Object value);

        @Specialization
        protected SequenceStorage doScalarInt(SequenceStorage storage, int idx, Object value) {
            int normalized = normalizeIndex(idx, storage);
            try {
                getSetItemScalarNode().execute(storage, normalized, value);
                return storage;
            } catch (SequenceStoreException e) {
                generalizeProfile.enter();
                SequenceStorage generalized = generalizeStore(storage, e.getIndicationValue());
                try {
                    getSetItemScalarNode().execute(generalized, normalized, value);
                } catch (SequenceStoreException e1) {
                    CompilerDirectives.transferToInterpreter();
                    throw new IllegalStateException();
                }
                return generalized;
            }
        }

        @Specialization
        protected SequenceStorage doScalarLong(SequenceStorage storage, long idx, Object value) {
            int normalized = normalizeIndex(idx, storage);
            try {
                getSetItemScalarNode().execute(storage, normalized, value);
                return storage;
            } catch (SequenceStoreException e) {
                generalizeProfile.enter();
                SequenceStorage generalized = generalizeStore(storage, e.getIndicationValue());
                getSetItemScalarNode().execute(generalized, normalized, value);
                return generalized;
            }
        }

        @Specialization
        protected SequenceStorage doScalarPInt(SequenceStorage storage, PInt idx, Object value) {
            int normalized = normalizeIndex(idx, storage);
            try {
                getSetItemScalarNode().execute(storage, normalized, value);
                return storage;
            } catch (SequenceStoreException e) {
                generalizeProfile.enter();
                SequenceStorage generalized = generalizeStore(storage, e.getIndicationValue());
                getSetItemScalarNode().execute(generalized, normalized, value);
                return generalized;
            }
        }

        @Specialization(guards = "!isPSlice(idx)")
        protected SequenceStorage doScalarGeneric(SequenceStorage storage, Object idx, Object value) {
            int normalized = normalizeIndex(idx, storage);
            try {
                getSetItemScalarNode().execute(storage, normalized, value);
                return storage;
            } catch (SequenceStoreException e) {
                generalizeProfile.enter();
                SequenceStorage generalized = generalizeStore(storage, e.getIndicationValue());
                getSetItemScalarNode().execute(generalized, normalized, value);
                return generalized;
            }
        }

        @Specialization
        protected SequenceStorage doSlice(SequenceStorage storage, PSlice slice, Object iterable) {
            SliceInfo info = slice.computeActualIndices(storage.length());
            try {
                getSetItemSliceNode().execute(storage, info, iterable);
                return storage;
            } catch (SequenceStoreException e) {
                generalizeProfile.enter();
                SequenceStorage generalized = generalizeStore(storage, e.getIndicationValue());
                getSetItemSliceNode().execute(generalized, info, iterable);
                return generalized;
            }
        }

        private SequenceStorage generalizeStore(SequenceStorage storage, Object value) {
            if (generalizationNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                generalizationNode = insert(generalizationNodeProvider.get());
            }
            return generalizationNode.execute(storage, value);
        }

        private SetItemScalarNode getSetItemScalarNode() {
            if (setItemScalarNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                setItemScalarNode = insert(SetItemScalarNode.create());
            }
            return setItemScalarNode;
        }

        private SetItemSliceNode getSetItemSliceNode() {
            if (setItemSliceNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                setItemSliceNode = insert(SetItemSliceNode.create());
            }
            return setItemSliceNode;
        }

        public static SetItemNode create(NormalizeIndexNode normalizeIndexNode, Supplier<GeneralizationNode> generalizationNodeProvider) {
            return SetItemNodeGen.create(normalizeIndexNode, generalizationNodeProvider);
        }

        public static SetItemNode create(NormalizeIndexNode normalizeIndexNode, String invalidItemErrorMessage) {
            return SetItemNodeGen.create(normalizeIndexNode, () -> NoGeneralizationNode.create(invalidItemErrorMessage));
        }

        public static SetItemNode create(String invalidItemErrorMessage) {
            return SetItemNodeGen.create(NormalizeIndexNode.create(), () -> NoGeneralizationNode.create(invalidItemErrorMessage));
        }

    }

    abstract static class SetItemScalarNode extends SequenceStorageBaseNode {

        @Child private Node writeNode;
        @Child private VerifyNativeItemNode verifyNativeItemNode;
        @Child private CastToByteNode castToByteNode;

        @CompilationFinal private BranchProfile invalidTypeProfile;

        public abstract void execute(SequenceStorage s, int idx, Object value);

        @Specialization
        protected void doBoolean(BoolSequenceStorage storage, int idx, boolean value) {
            storage.setBoolItemNormalized(idx, value);
        }

        @Specialization
        protected void doByte(ByteSequenceStorage storage, int idx, Object value) {
            storage.setByteItemNormalized(idx, getCastToByteNode().execute(value));
        }

        @Specialization
        protected void doChar(CharSequenceStorage storage, int idx, char value) {
            storage.setCharItemNormalized(idx, value);
        }

        @Specialization
        protected void doInt(IntSequenceStorage storage, int idx, int value) {
            storage.setIntItemNormalized(idx, value);
        }

        @Specialization
        protected void doInt(IntSequenceStorage storage, int idx, PInt value) {
            try {
                storage.setIntItemNormalized(idx, value.intValueExact());
            } catch (ArithmeticException e) {
                throw new SequenceStoreException(value);
            }
        }

        @Specialization
        protected void doLong(LongSequenceStorage storage, int idx, long value) {
            storage.setLongItemNormalized(idx, value);
        }

        @Specialization
        protected void doLong(LongSequenceStorage storage, int idx, PInt value) {
            try {
                storage.setLongItemNormalized(idx, value.longValueExact());
            } catch (ArithmeticException e) {
                throw new SequenceStoreException(value);
            }
        }

        @Specialization
        protected void doDouble(DoubleSequenceStorage storage, int idx, double value) {
            storage.setDoubleItemNormalized(idx, value);
        }

        @Specialization
        protected void doList(ListSequenceStorage storage, int idx, PList value) {
            storage.setListItemNormalized(idx, value);
        }

        @Specialization
        protected void doTuple(TupleSequenceStorage storage, int idx, PTuple value) {
            storage.setPTupleItemNormalized(idx, value);
        }

        @Specialization
        protected void doObject(ObjectSequenceStorage storage, int idx, Object value) {
            storage.setItemNormalized(idx, value);
        }

        @Specialization(guards = "isByteStorage(storage)")
        protected void doNativeByte(NativeSequenceStorage storage, int idx, Object value) {
            try {
                ForeignAccess.sendWrite(getWriteNode(), (TruffleObject) storage.getPtr(), idx, getCastToByteNode().execute(value));
            } catch (InteropException e) {
                throw e.raise();
            }
        }

        @Specialization
        protected void doNative(NativeSequenceStorage storage, int idx, Object value) {
            try {
                ForeignAccess.sendWrite(getWriteNode(), (TruffleObject) storage.getPtr(), idx, verifyValue(storage, value));
            } catch (InteropException e) {
                throw e.raise();
            }
        }

        @Fallback
        @SuppressWarnings("unused")
        void doError(SequenceStorage s, int idx, Object item) {
            throw new SequenceStoreException(item);
        }

        private Node getWriteNode() {
            if (writeNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                writeNode = insert(Message.WRITE.createNode());
            }
            return writeNode;
        }

        private CastToByteNode getCastToByteNode() {
            if (castToByteNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                castToByteNode = insert(CastToByteNode.create());
            }
            return castToByteNode;
        }

        private Object verifyValue(NativeSequenceStorage storage, Object item) {
            if (verifyNativeItemNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                verifyNativeItemNode = insert(VerifyNativeItemNode.create());
            }
            if (invalidTypeProfile == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                invalidTypeProfile = BranchProfile.create();
            }
            if (!verifyNativeItemNode.execute(storage.getElementType(), item)) {
                invalidTypeProfile.enter();
                throw new SequenceStoreException(item);
            }
            return item;
        }

        public static SetItemScalarNode create() {
            return SetItemScalarNodeGen.create();
        }
    }

    @ImportStatic(ListStorageType.class)
    public abstract static class SetItemSliceNode extends SequenceStorageBaseNode {

        public abstract void execute(SequenceStorage s, SliceInfo info, Object iterable);

        @Specialization(guards = "hasStorage(seq)")
        void doStorage(SequenceStorage s, SliceInfo info, PSequence seq,
                        @Cached("create()") SetStorageSliceNode setStorageSliceNode) {
            setStorageSliceNode.execute(s, info, seq.getSequenceStorage());
        }

        @Specialization
        void doGeneric(SequenceStorage s, SliceInfo info, Object iterable,
                        @Cached("create()") SetStorageSliceNode setStorageSliceNode,
                        @Cached("create()") ListNodes.ConstructListNode constructListNode) {
            PList list = constructListNode.execute(iterable, null);
            setStorageSliceNode.execute(s, info, list.getSequenceStorage());
        }

        public static SetItemSliceNode create() {
            return SetItemSliceNodeGen.create();
        }
    }

    abstract static class SetStorageSliceNode extends SequenceStorageBaseNode {

        public abstract void execute(SequenceStorage s, SliceInfo info, SequenceStorage iterable);

        @Specialization(limit = "MAX_ARRAY_STORAGES", guards = {"self.getClass() == cachedClass", "self.getClass() == sequence.getClass()", "replacesWholeSequence(cachedClass, self, info)"})
        void doWholeSequence(BasicSequenceStorage self, @SuppressWarnings("unused") SliceInfo info, BasicSequenceStorage sequence,
                        @Cached("self.getClass()") Class<? extends BasicSequenceStorage> cachedClass) {
            BasicSequenceStorage selfProfiled = cachedClass.cast(self);
            BasicSequenceStorage otherProfiled = cachedClass.cast(sequence);
            selfProfiled.setInternalArrayObject(otherProfiled.getCopyOfInternalArrayObject());
            selfProfiled.setNewLength(otherProfiled.length());
            selfProfiled.minimizeCapacity();
        }

        @Specialization(guards = {"compatibleDataType(store, sequence)"})
        void setSlice(SequenceStorage store, SliceInfo sinfo, SequenceStorage sequence,
                        @Cached("createBinaryProfile()") ConditionProfile wrongLength,
                        @Cached("createBinaryProfile()") ConditionProfile clearProfile,
                        @Cached("create()") LenNode selfLenNode,
                        @Cached("create()") LenNode otherLenNode,
                        @Cached("create()") SetLenNode setLenNode,
                        @Cached("create()") SetItemScalarNode setLeftItemNode,
                        @Cached("create()") GetItemScalarNode getRightItemNode) {
            int length = selfLenNode.execute(store);
            int valueLen = otherLenNode.execute(sequence);
            if (wrongLength.profile(sinfo.step != 1 && sinfo.length != valueLen)) {
                throw raise(ValueError, "attempt to assign sequence of size %d to extended slice of size %d", valueLen, length);
            }
            int start = sinfo.start;
            int stop = sinfo.stop;
            int step = sinfo.step;
            boolean negativeStep = step < 0;

            if (negativeStep) {
                // For the simplicity of algorithm, then start and stop are swapped.
                // The start index has to recalculated according the step, because
                // the algorithm bellow removes the start item and then start + step ....
                step = Math.abs(step);
                stop++;
                int tmpStart = stop + ((start - stop) % step);
                stop = start + 1;
                start = tmpStart;
            }
            if (start < 0) {
                start = 0;
            } else if (start > length) {
                start = length;
            }
            if (stop < start) {
                stop = start;
            } else if (stop > length) {
                stop = length;
            }

            int norig = sinfo.length;
            int delta = valueLen - norig;
            int index;

            if (clearProfile.profile(length + delta == 0)) {
                setLenNode.execute(store, 0);
                return;
            }
            store.ensureCapacity(length + delta);
            // we need to work with the copy in the case if a[i:j] = a
            SequenceStorage workingValue = store == sequence ? store.copy() : sequence;

            if (step == 1) {
                if (delta < 0) {
                    // delete items
                    for (index = stop + delta; index < length + delta; index++) {
                        store.copyItem(index, index - delta);
                    }
                    length += delta;
                    stop += delta;
                } else if (delta > 0) {
                    // insert items
                    for (index = length - 1; index >= stop; index--) {
                        store.copyItem(index + delta, index);
                    }
                    length += delta;
                    stop += delta;
                }
            }

            if (!negativeStep) {
                for (int i = start, j = 0; i < stop; i += step, j++) {
                    setLeftItemNode.execute(store, i, getRightItemNode.execute(workingValue, j));
                }
            } else {
                for (int i = start, j = valueLen - 1; i < stop; i += step, j--) {
                    setLeftItemNode.execute(store, i, getRightItemNode.execute(workingValue, j));
                }
            }
            setLenNode.execute(store, length);
        }

        @Specialization(guards = "!compatibleAssign(self, sequence)")
        void doError(@SuppressWarnings("unused") SequenceStorage self, @SuppressWarnings("unused") SliceInfo info, SequenceStorage sequence) {
            throw new SequenceStoreException(sequence);
        }

        protected static boolean isValidReplacement(Class<? extends SequenceStorage> cachedClass, SequenceStorage s, SliceInfo info) {
            return info.step == 1 || info.length == cachedClass.cast(s).length();
        }

        protected static boolean replacesWholeSequence(Class<? extends BasicSequenceStorage> cachedClass, BasicSequenceStorage s, SliceInfo info) {
            return info.start == 0 && info.step == 1 && info.stop == cachedClass.cast(s).length();
        }

        public static SetStorageSliceNode create() {
            return SetStorageSliceNodeGen.create();
        }
    }

    abstract static class VerifyNativeItemNode extends PBaseNode {

        public abstract boolean execute(ListStorageType expectedType, Object item);

        @Specialization(guards = "elementType == cachedElementType", limit = "1")
        boolean doCached(@SuppressWarnings("unused") ListStorageType elementType, Object item,
                        @Cached("elementType") ListStorageType cachedElementType) {
            return doGeneric(cachedElementType, item);
        }

        @Specialization(replaces = "doCached")
        boolean doGeneric(ListStorageType expectedType, Object item) {
            switch (expectedType) {
                case Byte:
                    return item instanceof Byte;
                case Int:
                    return item instanceof Integer;
                case Long:
                    return item instanceof Long;
                case Double:
                    return item instanceof Double;
                case Generic:
                    return !(item instanceof Byte || item instanceof Integer || item instanceof Long || item instanceof Double);
            }
            return false;
        }

        public static VerifyNativeItemNode create() {
            return VerifyNativeItemNodeGen.create();
        }

    }

    @ImportStatic(NativeCAPISymbols.class)
    public abstract static class StorageToNativeNode extends PBaseNode {
        @Child private Node executeNode;

        public abstract NativeSequenceStorage execute(Object obj);

        @Specialization
        NativeSequenceStorage doByte(byte[] arr,
                        @Cached("create(FUN_PY_TRUFFLE_BYTE_ARRAY_TO_NATIVE)") PCallBinaryCapiFunction callNode) {
            return new NativeSequenceStorage(callNode.execute(wrap(arr), arr.length), arr.length, arr.length, ListStorageType.Byte);
        }

        @Specialization
        NativeSequenceStorage doInt(int[] arr,
                        @Cached("create(FUN_PY_TRUFFLE_INT_ARRAY_TO_NATIVE)") PCallBinaryCapiFunction callNode) {
            return new NativeSequenceStorage(callNode.execute(wrap(arr), arr.length), arr.length, arr.length, ListStorageType.Int);
        }

        @Specialization
        NativeSequenceStorage doLong(long[] arr,
                        @Cached("create(FUN_PY_TRUFFLE_LONG_ARRAY_TO_NATIVE)") PCallBinaryCapiFunction callNode) {
            return new NativeSequenceStorage(callNode.execute(wrap(arr), arr.length), arr.length, arr.length, ListStorageType.Long);
        }

        @Specialization
        NativeSequenceStorage doDouble(double[] arr,
                        @Cached("create(FUN_PY_TRUFFLE_DOUBLE_ARRAY_TO_NATIVE)") PCallBinaryCapiFunction callNode) {
            return new NativeSequenceStorage(callNode.execute(wrap(arr), arr.length), arr.length, arr.length, ListStorageType.Double);
        }

        @Specialization
        NativeSequenceStorage doObject(Object[] arr,
                        @Cached("create(FUN_PY_TRUFFLE_OBJECT_ARRAY_TO_NATIVE)") PCallBinaryCapiFunction callNode) {
            return new NativeSequenceStorage(callNode.execute(wrap(arr), arr.length), arr.length, arr.length, ListStorageType.Generic);
        }

        private Object wrap(Object arr) {
            return getContext().getEnv().asGuestValue(arr);
        }

        public static StorageToNativeNode create() {
            return StorageToNativeNodeGen.create();
        }
    }

    public abstract static class CastToByteNode extends PBaseNode {

        private final Function<Object, Byte> rangeErrorHandler;
        private final Function<Object, Byte> typeErrorHandler;

        protected CastToByteNode(Function<Object, Byte> rangeErrorHandler, Function<Object, Byte> typeErrorHandler) {
            this.rangeErrorHandler = rangeErrorHandler;
            this.typeErrorHandler = typeErrorHandler;
        }

        public abstract byte execute(Object val);

        @Specialization
        protected byte doByte(byte value) {
            return value;
        }

        @Specialization(rewriteOn = ArithmeticException.class)
        protected byte doInt(int value) {
            return PInt.byteValueExact(value);
        }

        @Specialization(replaces = "doInt")
        protected byte doIntOvf(int value) {
            try {
                return PInt.byteValueExact(value);
            } catch (ArithmeticException e) {
                return handleRangeError(value);
            }
        }

        @Specialization(rewriteOn = ArithmeticException.class)
        protected byte doLong(long value) {
            return PInt.byteValueExact(value);
        }

        @Specialization(replaces = "doLong")
        protected byte doLongOvf(long value) {
            try {
                return PInt.byteValueExact(value);
            } catch (ArithmeticException e) {
                return handleRangeError(value);
            }
        }

        @Specialization(rewriteOn = ArithmeticException.class)
        protected byte doPInt(PInt value) {
            return PInt.byteValueExact(value.longValueExact());
        }

        @Specialization(replaces = "doPInt")
        protected byte doPIntOvf(PInt value) {
            try {
                return PInt.byteValueExact(value.longValueExact());
            } catch (ArithmeticException e) {
                return handleRangeError(value);
            }
        }

        @Specialization
        protected byte doBoolean(boolean value) {
            return value ? (byte) 1 : (byte) 0;
        }

        @Fallback
        protected byte doGeneric(@SuppressWarnings("unused") Object val) {
            if (typeErrorHandler != null) {
                return typeErrorHandler.apply(val);
            } else {
                throw raise(TypeError, "an integer is required (got type %p)", val);
            }
        }

        private byte handleRangeError(Object val) {
            if (rangeErrorHandler != null) {
                return rangeErrorHandler.apply(val);
            } else {
                throw raise(ValueError, "byte must be in range(0, 256)");
            }
        }

        public static CastToByteNode create() {
            return CastToByteNodeGen.create(null, null);
        }

        public static CastToByteNode create(Function<Object, Byte> rangeErrorHandler, Function<Object, Byte> typeErrorHandler) {
            return CastToByteNodeGen.create(rangeErrorHandler, typeErrorHandler);
        }

    }

    protected abstract static class BinCmpOp {
        protected abstract boolean cmp(int l, int r);

        protected abstract boolean cmp(long l, long r);

        protected abstract boolean cmp(char l, char r);

        protected abstract boolean cmp(byte l, byte r);

        protected abstract boolean cmp(double l, double r);

        protected abstract boolean cmpLen(int l, int r);

        protected abstract BinaryComparisonNode createBinaryComparisonNode();
    }

    private static final class Le extends BinCmpOp {
        private static final Le INSTANCE = new Le();

        @Override
        protected boolean cmp(int l, int r) {
            return l <= r;
        }

        @Override
        protected boolean cmp(long l, long r) {
            return l <= r;
        }

        @Override
        protected boolean cmp(char l, char r) {
            return l <= r;
        }

        @Override
        protected boolean cmp(byte l, byte r) {
            return l <= r;
        }

        @Override
        protected boolean cmp(double l, double r) {
            return l <= r;
        }

        @Override
        protected BinaryComparisonNode createBinaryComparisonNode() {
            return BinaryComparisonNode.create(__LE__, __GE__, "<=");
        }

        @Override
        protected boolean cmpLen(int l, int r) {
            return l <= r;
        }

    }

    private static final class Lt extends BinCmpOp {

        private static final Lt INSTANCE = new Lt();

        @Override
        protected boolean cmp(int l, int r) {
            return l < r;
        }

        @Override
        protected boolean cmp(long l, long r) {
            return l < r;
        }

        @Override
        protected boolean cmp(char l, char r) {
            return l < r;
        }

        @Override
        protected boolean cmp(byte l, byte r) {
            return l < r;
        }

        @Override
        protected boolean cmp(double l, double r) {
            return l < r;
        }

        @Override
        protected BinaryComparisonNode createBinaryComparisonNode() {
            return BinaryComparisonNode.create(__LT__, __GT__, "<");
        }

        @Override
        protected boolean cmpLen(int l, int r) {
            return l < r;
        }

    }

    private static final class Ge extends BinCmpOp {

        private static final Ge INSTANCE = new Ge();

        @Override
        protected boolean cmp(int l, int r) {
            return l >= r;
        }

        @Override
        protected boolean cmp(long l, long r) {
            return l >= r;
        }

        @Override
        protected boolean cmp(char l, char r) {
            return l >= r;
        }

        @Override
        protected boolean cmp(byte l, byte r) {
            return l >= r;
        }

        @Override
        protected boolean cmp(double l, double r) {
            return l >= r;
        }

        @Override
        protected BinaryComparisonNode createBinaryComparisonNode() {
            return BinaryComparisonNode.create(__GE__, __LE__, ">=");
        }

        @Override
        protected boolean cmpLen(int l, int r) {
            return l >= r;
        }

    }

    private static final class Gt extends BinCmpOp {

        private static final Gt INSTANCE = new Gt();

        @Override
        protected boolean cmp(int l, int r) {
            return l > r;
        }

        @Override
        protected boolean cmp(long l, long r) {
            return l > r;
        }

        @Override
        protected boolean cmp(char l, char r) {
            return l > r;
        }

        @Override
        protected boolean cmp(byte l, byte r) {
            return l > r;
        }

        @Override
        protected boolean cmp(double l, double r) {
            return l > r;
        }

        @Override
        protected BinaryComparisonNode createBinaryComparisonNode() {
            return BinaryComparisonNode.create(__GT__, __LT__, ">");
        }

        @Override
        protected boolean cmpLen(int l, int r) {
            return l > r;
        }

    }

    private static final class Eq extends BinCmpOp {

        private static final Eq INSTANCE = new Eq();

        @Override
        protected boolean cmp(int l, int r) {
            return l == r;
        }

        @Override
        protected boolean cmp(long l, long r) {
            return l == r;
        }

        @Override
        protected boolean cmp(char l, char r) {
            return l == r;
        }

        @Override
        protected boolean cmp(byte l, byte r) {
            return l == r;
        }

        @Override
        protected boolean cmp(double l, double r) {
            return l == r;
        }

        @Override
        protected BinaryComparisonNode createBinaryComparisonNode() {
            return BinaryComparisonNode.create(__EQ__, __EQ__, "==");
        }

        @Override
        protected boolean cmpLen(int l, int r) {
            return l == r;
        }

    }

    public abstract static class CmpNode extends SequenceStorageBaseNode {
        @Child private GetItemScalarNode getItemNode;
        @Child private GetItemScalarNode getRightItemNode;
        @Child private BinaryComparisonNode eqNode;
        @Child private BinaryComparisonNode comparisonNode;
        @Child private CastToBooleanNode castToBooleanNode;

        private final BinCmpOp cmpOp;

        protected CmpNode(BinCmpOp cmpOp) {
            this.cmpOp = cmpOp;
        }

        public abstract boolean execute(SequenceStorage left, SequenceStorage right);

        @Specialization(guards = {"isEmpty(left)", "isEmpty(right)"})
        boolean doEmpty(@SuppressWarnings("unused") SequenceStorage left, @SuppressWarnings("unused") SequenceStorage right) {
            return cmpOp.cmp(0, 0);
        }

        @Specialization
        boolean doBoolStorage(BoolSequenceStorage left, BoolSequenceStorage right) {
            int llen = left.length();
            int rlen = right.length();
            for (int i = 0; i < Math.min(llen, rlen); i++) {
                int litem = PInt.intValue(left.getBoolItemNormalized(i));
                int ritem = PInt.intValue(right.getBoolItemNormalized(i));
                if (litem != ritem) {
                    return cmpOp.cmp(litem, ritem);
                }
            }
            return cmpOp.cmp(llen, rlen);
        }

        @Specialization
        boolean doByteStorage(ByteSequenceStorage left, ByteSequenceStorage right) {
            int llen = left.length();
            int rlen = right.length();
            for (int i = 0; i < Math.min(llen, rlen); i++) {
                int litem = left.getIntItemNormalized(i);
                int ritem = right.getIntItemNormalized(i);
                if (litem != ritem) {
                    return cmpOp.cmp(litem, ritem);
                }
            }
            return cmpOp.cmp(llen, rlen);
        }

        @Specialization
        boolean doCharStorage(CharSequenceStorage left, CharSequenceStorage right) {
            int llen = left.length();
            int rlen = right.length();
            for (int i = 0; i < Math.min(llen, rlen); i++) {
                char litem = left.getCharItemNormalized(i);
                char ritem = right.getCharItemNormalized(i);
                if (litem != ritem) {
                    return cmpOp.cmp(litem, ritem);
                }
            }
            return cmpOp.cmp(llen, rlen);
        }

        @Specialization
        boolean doIntStorage(IntSequenceStorage left, IntSequenceStorage right) {
            int llen = left.length();
            int rlen = right.length();
            for (int i = 0; i < Math.min(llen, rlen); i++) {
                int litem = left.getIntItemNormalized(i);
                int ritem = right.getIntItemNormalized(i);
                if (litem != ritem) {
                    return cmpOp.cmp(litem, ritem);
                }
            }
            return cmpOp.cmp(llen, rlen);
        }

        @Specialization
        boolean doLongStorage(LongSequenceStorage left, LongSequenceStorage right) {
            int llen = left.length();
            int rlen = right.length();
            for (int i = 0; i < Math.min(llen, rlen); i++) {
                long litem = left.getLongItemNormalized(i);
                long ritem = right.getLongItemNormalized(i);
                if (litem != ritem) {
                    return cmpOp.cmp(litem, ritem);
                }
            }
            return cmpOp.cmp(llen, rlen);
        }

        @Specialization
        boolean doDoubleStorage(DoubleSequenceStorage left, DoubleSequenceStorage right) {
            int llen = left.length();
            int rlen = right.length();
            for (int i = 0; i < Math.min(llen, rlen); i++) {
                double litem = left.getDoubleItemNormalized(i);
                double ritem = right.getDoubleItemNormalized(i);
                if (litem != ritem) {
                    return cmpOp.cmp(litem, ritem);
                }
            }
            return cmpOp.cmp(llen, rlen);
        }

        @Fallback
        boolean doGeneric(SequenceStorage left, SequenceStorage right) {
            int llen = left.length();
            int rlen = right.length();
            for (int i = 0; i < Math.min(llen, rlen); i++) {
                Object leftItem = getGetItemNode().execute(left, i);
                Object rightItem = getGetRightItemNode().execute(right, i);
                if (!eq(leftItem, rightItem)) {
                    return cmpGeneric(leftItem, rightItem);
                }
            }
            return cmpOp.cmp(llen, rlen);
        }

        private GetItemScalarNode getGetItemNode() {
            if (getItemNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                getItemNode = insert(GetItemScalarNode.create());
            }
            return getItemNode;
        }

        private GetItemScalarNode getGetRightItemNode() {
            if (getRightItemNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                getRightItemNode = insert(GetItemScalarNode.create());
            }
            return getRightItemNode;
        }

        private boolean eq(Object left, Object right) {
            if (eqNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                eqNode = insert(BinaryComparisonNode.create(__EQ__, __EQ__, "=="));
            }
            return castToBoolean(eqNode.executeWith(left, right));
        }

        private boolean cmpGeneric(Object left, Object right) {
            if (comparisonNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                comparisonNode = insert(cmpOp.createBinaryComparisonNode());
            }
            return castToBoolean(comparisonNode.executeWith(left, right));
        }

        private boolean castToBoolean(Object value) {
            if (castToBooleanNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                castToBooleanNode = insert(CastToBooleanNode.createIfTrueNode());
            }
            return castToBooleanNode.executeWith(value);
        }

        public static CmpNode createLe() {
            return CmpNodeGen.create(Le.INSTANCE);
        }

        public static CmpNode createLt() {
            return CmpNodeGen.create(Lt.INSTANCE);
        }

        public static CmpNode createGe() {
            return CmpNodeGen.create(Ge.INSTANCE);
        }

        public static CmpNode createGt() {
            return CmpNodeGen.create(Gt.INSTANCE);
        }

        public static CmpNode createEq() {
            return CmpNodeGen.create(Eq.INSTANCE);
        }
    }

    public abstract static class NormalizeIndexNode extends PBaseNode {
        public static final String INDEX_OUT_OF_BOUNDS = "index out of range";
        public static final String RANGE_OUT_OF_BOUNDS = "range index out of range";
        public static final String TUPLE_OUT_OF_BOUNDS = "tuple index out of range";
        public static final String TUPLE_ASSIGN_OUT_OF_BOUNDS = "tuple assignment index out of range";
        public static final String LIST_OUT_OF_BOUNDS = "list index out of range";
        public static final String LIST_ASSIGN_OUT_OF_BOUNDS = "list assignment index out of range";
        public static final String ARRAY_OUT_OF_BOUNDS = "array index out of range";
        public static final String ARRAY_ASSIGN_OUT_OF_BOUNDS = "array assignment index out of range";
        public static final String BYTEARRAY_OUT_OF_BOUNDS = "bytearray index out of range";

        private final String errorMessage;
        private final boolean boundsCheck;
        private final ConditionProfile negativeIndexProfile = ConditionProfile.createBinaryProfile();

        @CompilationFinal private ConditionProfile outOfBoundsProfile;

        public NormalizeIndexNode(String errorMessage, boolean boundsCheck) {
            this.errorMessage = errorMessage;
            this.boundsCheck = boundsCheck;
        }

        public abstract int execute(Object index, int length);

        @Specialization
        public int doInt(int index, int length) {
            int idx = index;
            if (negativeIndexProfile.profile(idx < 0)) {
                idx += length;
            }
            doBoundsCheck(idx, length);
            return idx;
        }

        @Specialization
        int doBool(boolean index, int length) {
            int idx = PInt.intValue(index);
            doBoundsCheck(idx, length);
            return idx;
        }

        private void doBoundsCheck(int idx, int length) {
            if (boundsCheck) {
                if (outOfBoundsProfile == null) {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    outOfBoundsProfile = ConditionProfile.createBinaryProfile();
                }
                if (outOfBoundsProfile.profile(idx < 0 || idx >= length)) {
                    throw raise(IndexError, errorMessage);
                }
            }
        }

        @Specialization(rewriteOn = ArithmeticException.class)
        int doLong(long index, int length) {
            int idx = PInt.intValueExact(index);
            return doInt(idx, length);
        }

        @Specialization(replaces = "doLong")
        int doLongOvf(long index, int length) {
            try {
                return doLong(index, length);
            } catch (ArithmeticException e) {
                throw raiseIndexError();
            }
        }

        @Specialization(rewriteOn = ArithmeticException.class)
        int doPInt(PInt index, int length) {
            int idx = index.intValueExact();
            return doInt(idx, length);
        }

        @Specialization(replaces = "doPInt")
        int doPIntOvf(PInt index, int length) {
            try {
                return doPInt(index, length);
            } catch (ArithmeticException e) {
                throw raiseIndexError();
            }
        }

        public static NormalizeIndexNode create() {
            return create(INDEX_OUT_OF_BOUNDS, true);
        }

        public static NormalizeIndexNode create(String errorMessage) {
            return NormalizeIndexNodeGen.create(errorMessage, true);
        }

        public static NormalizeIndexNode create(boolean boundsCheck) {
            return NormalizeIndexNodeGen.create(INDEX_OUT_OF_BOUNDS, boundsCheck);
        }

        public static NormalizeIndexNode create(String errorMessage, boolean boundsCheck) {
            return NormalizeIndexNodeGen.create(errorMessage, boundsCheck);
        }

        public static NormalizeIndexNode forList() {
            return create(LIST_OUT_OF_BOUNDS);
        }

        public static NormalizeIndexNode forListAssign() {
            return create(LIST_ASSIGN_OUT_OF_BOUNDS);
        }

        public static NormalizeIndexNode forTuple() {
            return create(TUPLE_OUT_OF_BOUNDS);
        }

        public static NormalizeIndexNode forTupleAssign() {
            return create(TUPLE_ASSIGN_OUT_OF_BOUNDS);
        }

        public static NormalizeIndexNode forArray() {
            return create(ARRAY_OUT_OF_BOUNDS);
        }

        public static NormalizeIndexNode forArrayAssign() {
            return create(ARRAY_ASSIGN_OUT_OF_BOUNDS);
        }

        public static NormalizeIndexNode forRange() {
            return create(RANGE_OUT_OF_BOUNDS);
        }

        public static NormalizeIndexNode forBytearray() {
            return create(BYTEARRAY_OUT_OF_BOUNDS);
        }
    }

    public abstract static class ToByteArrayNode extends SequenceStorageBaseNode {

        @Child private GetItemScalarNode getItemNode;

        private final boolean exact;

        public ToByteArrayNode(boolean exact) {
            this.exact = exact;
        }

        public abstract byte[] execute(SequenceStorage s);

        @Specialization
        byte[] doByteSequenceStorage(ByteSequenceStorage s) {
            byte[] barr = s.getInternalByteArray();
            if (exact) {
                return exactCopy(barr, s.length());
            }
            return barr;

        }

        @Specialization(guards = "isByteStorage(s)")
        byte[] doNativeByte(NativeSequenceStorage s) {
            byte[] barr = new byte[s.length()];
            for (int i = 0; i < barr.length; i++) {
                int elem = getGetItemNode().executeInt(s, i);
                assert elem >= 0 && elem < 256;
                barr[i] = (byte) elem;
            }
            return barr;
        }

        @Fallback
        byte[] doFallback(@SuppressWarnings("unused") SequenceStorage s) {
            throw raise(TypeError, "expected a bytes-like object");
        }

        @TruffleBoundary(transferToInterpreterOnException = false)
        private static byte[] exactCopy(byte[] barr, int len) {
            return Arrays.copyOf(barr, len);
        }

        protected GetItemScalarNode getGetItemNode() {
            if (getItemNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                getItemNode = insert(GetItemScalarNode.create());
            }
            return getItemNode;
        }

        public static ToByteArrayNode create() {
            return ToByteArrayNodeGen.create(true);
        }

        public static ToByteArrayNode create(boolean exact) {
            return ToByteArrayNodeGen.create(exact);
        }
    }

    abstract static class ConcatBaseNode extends SequenceStorageBaseNode {

        @Child private SetItemScalarNode setItemNode;
        @Child private GetItemScalarNode getItemNode;
        @Child private GetItemScalarNode getRightItemNode;
        @Child private SetLenNode setLenNode;

        public abstract SequenceStorage execute(SequenceStorage dest, SequenceStorage left, SequenceStorage right);

        @Specialization(guards = "!isNative(right)")
        SequenceStorage doLeftEmpty(@SuppressWarnings("unused") EmptySequenceStorage dest, @SuppressWarnings("unused") EmptySequenceStorage left, SequenceStorage right,
                        @Cached("create()") BranchProfile outOfMemProfile,
                        @Cached("createClassProfile()") ValueProfile storageTypeProfile) {
            try {
                return storageTypeProfile.profile(right).copy();
            } catch (OutOfMemoryError e) {
                outOfMemProfile.enter();
                throw raise(MemoryError);
            }
        }

        @Specialization(guards = "!isNative(left)")
        SequenceStorage doRightEmpty(@SuppressWarnings("unused") EmptySequenceStorage dest, SequenceStorage left, @SuppressWarnings("unused") EmptySequenceStorage right,
                        @Cached("create()") BranchProfile outOfMemProfile,
                        @Cached("createClassProfile()") ValueProfile storageTypeProfile) {
            try {
                return storageTypeProfile.profile(left).copy();
            } catch (OutOfMemoryError e) {
                outOfMemProfile.enter();
                throw raise(MemoryError);
            }
        }

        @Specialization(guards = {"dest.getClass() == left.getClass()", "left.getClass() == right.getClass()", "!isNative(dest)", "cachedClass == dest.getClass()"})
        SequenceStorage doManagedManagedSameType(SequenceStorage dest, SequenceStorage left, SequenceStorage right,
                        @Cached("left.getClass()") Class<? extends SequenceStorage> cachedClass) {
            SequenceStorage leftProfiled = cachedClass.cast(left);
            SequenceStorage rightProfiled = cachedClass.cast(right);
            Object arr1 = leftProfiled.getInternalArrayObject();
            int len1 = leftProfiled.length();
            Object arr2 = rightProfiled.getInternalArrayObject();
            int len2 = rightProfiled.length();
            concat(dest.getInternalArrayObject(), arr1, len1, arr2, len2);
            getSetLenNode().execute(dest, len1 + len2);
            return dest;
        }

        @Specialization(guards = {"dest.getClass() == right.getClass()", "!isNative(dest)", "cachedClass == dest.getClass()"})
        SequenceStorage doEmptyManagedSameType(SequenceStorage dest, @SuppressWarnings("unused") EmptySequenceStorage left, SequenceStorage right,
                        @Cached("left.getClass()") Class<? extends SequenceStorage> cachedClass) {
            SequenceStorage rightProfiled = cachedClass.cast(right);
            Object arr2 = rightProfiled.getInternalArrayObject();
            int len2 = rightProfiled.length();
            System.arraycopy(arr2, 0, dest.getInternalArrayObject(), 0, len2);
            getSetLenNode().execute(dest, len2);
            return dest;
        }

        @Specialization(guards = {"dest.getClass() == left.getClass()", "!isNative(dest)", "cachedClass == dest.getClass()"})
        SequenceStorage doManagedEmptySameType(SequenceStorage dest, SequenceStorage left, @SuppressWarnings("unused") EmptySequenceStorage right,
                        @Cached("left.getClass()") Class<? extends SequenceStorage> cachedClass) {
            SequenceStorage leftProfiled = cachedClass.cast(left);
            Object arr1 = leftProfiled.getInternalArrayObject();
            int len1 = leftProfiled.length();
            System.arraycopy(arr1, 0, dest.getInternalArrayObject(), 0, len1);
            getSetLenNode().execute(dest, len1);
            return dest;
        }

        @Specialization
        SequenceStorage doGeneric(SequenceStorage dest, SequenceStorage left, SequenceStorage right,
                        @Cached("createClassProfile()") ValueProfile leftProfile,
                        @Cached("createClassProfile()") ValueProfile rightProfile) {
            SequenceStorage leftProfiled = leftProfile.profile(left);
            SequenceStorage rightProfiled = rightProfile.profile(right);
            int len1 = leftProfiled.length();
            int len2 = rightProfiled.length();
            for (int i = 0; i < len1; i++) {
                getSetItemNode().execute(dest, i, getGetItemNode().execute(leftProfiled, i));
            }
            for (int i = 0; i < len2; i++) {
                getSetItemNode().execute(dest, i + len1, getGetRightItemNode().execute(rightProfiled, i));
            }
            getSetLenNode().execute(dest, len1 + len2);
            return dest;
        }

        private SetItemScalarNode getSetItemNode() {
            if (setItemNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                setItemNode = insert(SetItemScalarNode.create());
            }
            return setItemNode;
        }

        private GetItemScalarNode getGetItemNode() {
            if (getItemNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                getItemNode = insert(GetItemScalarNode.create());
            }
            return getItemNode;
        }

        private GetItemScalarNode getGetRightItemNode() {
            if (getRightItemNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                getRightItemNode = insert(GetItemScalarNode.create());
            }
            return getRightItemNode;
        }

        private SetLenNode getSetLenNode() {
            if (setLenNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                setLenNode = insert(SetLenNode.create());
            }
            return setLenNode;
        }

        private static void concat(Object dest, Object arr1, int len1, Object arr2, int len2) {
            System.arraycopy(arr1, 0, dest, 0, len1);
            System.arraycopy(arr2, 0, dest, len1, len2);
        }

        public static ConcatBaseNode create() {
            return ConcatBaseNodeGen.create();
        }
    }

    /**
     * Concatenates two sequence storages; creates a storage of a suitable type and writes the
     * result to the new storage.
     */
    public abstract static class ConcatNode extends SequenceStorageBaseNode {
        private static final String DEFAULT_ERROR_MSG = "bad argument type for built-in operation";

        @Child private ConcatBaseNode concatBaseNode = ConcatBaseNodeGen.create();
        @Child private CreateEmptyNode createEmptyNode = CreateEmptyNode.create();
        @Child private GeneralizationNode genNode;

        private final Supplier<GeneralizationNode> genNodeProvider;

        ConcatNode(Supplier<GeneralizationNode> genNodeProvider) {
            this.genNodeProvider = genNodeProvider;
        }

        public abstract SequenceStorage execute(SequenceStorage left, SequenceStorage right);

        @Specialization
        SequenceStorage doRight(SequenceStorage left, SequenceStorage right,
                        @Cached("createClassProfile()") ValueProfile leftProfile,
                        @Cached("createClassProfile()") ValueProfile rightProfile,
                        @Cached("create()") BranchProfile outOfMemProfile) {
            try {
                SequenceStorage leftProfiled = leftProfile.profile(left);
                SequenceStorage rightProfiled = rightProfile.profile(right);
                int len1 = leftProfiled.length();
                int len2 = rightProfiled.length();
                // we eagerly generalize the store to avoid possible cascading generalizations
                SequenceStorage generalized = generalizeStore(createEmpty(leftProfiled, rightProfiled, Math.addExact(len1, len2)), rightProfiled);
                return doConcat(generalized, leftProfiled, rightProfiled);
            } catch (ArithmeticException | OutOfMemoryError e) {
                outOfMemProfile.enter();
                throw raise(MemoryError);
            }
        }

        private SequenceStorage createEmpty(SequenceStorage l, SequenceStorage r, int len) {
            if (l instanceof EmptySequenceStorage) {
                return createEmptyNode.execute(r, len);
            }
            SequenceStorage empty = createEmptyNode.execute(l, len);
            empty.setNewLength(len);
            return empty;
        }

        private SequenceStorage doConcat(SequenceStorage dest, SequenceStorage leftProfiled, SequenceStorage rightProfiled) {
            try {
                return concatBaseNode.execute(dest, leftProfiled, rightProfiled);
            } catch (SequenceStoreException e) {
                CompilerDirectives.transferToInterpreter();
                throw new IllegalStateException("generalized sequence storage cannot take value: " + e.getIndicationValue());
            }
        }

        private SequenceStorage generalizeStore(SequenceStorage storage, Object value) {
            if (genNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                genNode = insert(genNodeProvider.get());
            }
            return genNode.execute(storage, value);
        }

        public static ConcatNode create() {
            return ConcatNodeGen.create(() -> NoGeneralizationNode.create(DEFAULT_ERROR_MSG));
        }

        public static ConcatNode create(String msg) {
            return ConcatNodeGen.create(() -> NoGeneralizationNode.create(msg));
        }

        public static ConcatNode create(Supplier<GeneralizationNode> genNodeProvider) {
            return ConcatNodeGen.create(genNodeProvider);
        }
    }

    public abstract static class ExtendNode extends SequenceStorageBaseNode {
        @Child private CreateEmptyNode createEmptyNode = CreateEmptyNode.create();
        @Child private GeneralizationNode genNode;

        private final Supplier<GeneralizationNode> genNodeProvider;

        public ExtendNode(Supplier<GeneralizationNode> genNodeProvider) {
            this.genNodeProvider = genNodeProvider;
        }

        public abstract SequenceStorage execute(SequenceStorage s, Object iterable);

        @Specialization(guards = "hasStorage(seq)")
        SequenceStorage doWithStorage(SequenceStorage s, PSequence seq,
                        @Cached("createClassProfile()") ValueProfile leftProfile,
                        @Cached("createClassProfile()") ValueProfile rightProfile,
                        @Cached("createClassProfile()") ValueProfile sequenceProfile,
                        @Cached("create()") EnsureCapacityNode ensureCapacityNode,
                        @Cached("create()") BranchProfile overflowErrorProfile,
                        @Cached("create()") ConcatBaseNode concatStoragesNode) {
            SequenceStorage leftProfiled = leftProfile.profile(s);
            SequenceStorage rightProfiled = rightProfile.profile(sequenceProfile.profile(seq).getSequenceStorage());
            int len1 = leftProfiled.length();
            int len2 = rightProfiled.length();
            SequenceStorage dest = null;
            try {
                dest = ensureCapacityNode.execute(leftProfiled, Math.addExact(len1, len2));
                return concatStoragesNode.execute(dest, s, rightProfiled);
            } catch (SequenceStoreException e) {
                dest = generalizeStore(dest, e.getIndicationValue());
                return concatStoragesNode.execute(dest, s, rightProfiled);
            } catch (ArithmeticException e) {
                overflowErrorProfile.enter();
                throw raise(PythonErrorType.OverflowError);
            }
        }

        @Specialization(guards = "!hasStorage(iterable)")
        SequenceStorage doWithoutStorage(SequenceStorage s, Object iterable,
                        @Cached("create()") GetIteratorNode getIteratorNode,
                        @Cached("create()") GetNextNode getNextNode,
                        @Cached("createBinaryProfile()") ConditionProfile errorProfile,
                        @Cached("createAppend()") AppendNode appendNode) {
            SequenceStorage currentStore = s;
            Object it = getIteratorNode.executeWith(iterable);
            while (true) {
                Object value;
                try {
                    value = getNextNode.execute(it);
                    currentStore = appendNode.execute(currentStore, value);
                } catch (PException e) {
                    e.expectStopIteration(getCore(), errorProfile);
                    return currentStore;
                }
            }
        }

        private SequenceStorage generalizeStore(SequenceStorage storage, Object value) {
            if (genNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                genNode = insert(genNodeProvider.get());
            }
            return genNode.execute(storage, value);
        }

        protected AppendNode createAppend() {
            return AppendNode.create(genNodeProvider);
        }

        protected ExtendNode createRecursive() {
            return ExtendNodeGen.create(genNodeProvider);
        }

        public static ExtendNode create(Supplier<GeneralizationNode> genNodeProvider) {
            return ExtendNodeGen.create(genNodeProvider);
        }
    }

    public abstract static class RepeatNode extends SequenceStorageBaseNode {
        private static final String ERROR_MSG = "can't multiply sequence by non-int of type '%p'";

        @Child private SetItemScalarNode setItemNode;
        @Child private GetItemScalarNode getItemNode;
        @Child private GetItemScalarNode getRightItemNode;
        @Child private IsIndexNode isIndexNode;
        @Child private CastToIndexNode castToindexNode;
        @Child private RepeatNode recursive;

        public abstract SequenceStorage execute(SequenceStorage left, Object times);

        public abstract SequenceStorage execute(SequenceStorage left, int times);

        @Specialization
        SequenceStorage doEmpty(EmptySequenceStorage s, @SuppressWarnings("unused") int times) {
            return s;
        }

        @Specialization(guards = "times <= 0")
        SequenceStorage doZeroRepeat(SequenceStorage s, @SuppressWarnings("unused") int times,
                        @Cached("createClassProfile()") ValueProfile storageTypeProfile) {
            return storageTypeProfile.profile(s).createEmpty(0);
        }

        @Specialization(limit = "MAX_ARRAY_STORAGES", guards = {"times > 0", "!isNative(s)", "s.getClass() == cachedClass"})
        SequenceStorage doManaged(BasicSequenceStorage s, int times,
                        @Cached("create()") BranchProfile outOfMemProfile,
                        @Cached("s.getClass()") Class<? extends SequenceStorage> cachedClass) {
            try {
                SequenceStorage profiled = cachedClass.cast(s);
                Object arr1 = profiled.getInternalArrayObject();
                int len = profiled.length();
                int newLength = Math.multiplyExact(len, times);
                SequenceStorage repeated = profiled.createEmpty(newLength);
                Object destArr = repeated.getInternalArrayObject();
                repeat(destArr, arr1, len, times);
                repeated.setNewLength(newLength);
                return repeated;
            } catch (OutOfMemoryError | ArithmeticException e) {
                outOfMemProfile.enter();
                throw raise(MemoryError);
            }
        }

        @Specialization(replaces = "doManaged", guards = "times > 0")
        SequenceStorage doGeneric(SequenceStorage s, int times,
                        @Cached("create()") BranchProfile outOfMemProfile,
                        @Cached("create()") LenNode lenNode) {
            try {
                int len = lenNode.execute(s);

                ObjectSequenceStorage repeated = new ObjectSequenceStorage(Math.multiplyExact(len, times));

                // TODO avoid temporary array
                Object[] values = new Object[len];
                for (int i = 0; i < len; i++) {
                    values[i] = getGetItemNode().execute(s, i);
                }

                Object destArr = repeated.getInternalArrayObject();
                repeat(destArr, values, len, times);
                return repeated;
            } catch (OutOfMemoryError | ArithmeticException e) {
                outOfMemProfile.enter();
                throw raise(MemoryError);
            }
        }

        @Specialization(guards = "!isInt(times)")
        SequenceStorage doNonInt(SequenceStorage s, Object times) {
            int i = toIndex(times);
            if (recursive == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                recursive = insert(RepeatNodeGen.create());
            }
            return recursive.execute(s, i);
        }

        private GetItemScalarNode getGetItemNode() {
            if (getItemNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                getItemNode = insert(GetItemScalarNode.create());
            }
            return getItemNode;
        }

        private static void repeat(Object dest, Object src, int len, int times) {
            for (int i = 0; i < times; i++) {
                System.arraycopy(src, 0, dest, i * len, len);
            }
        }

        protected static boolean isInt(Object times) {
            return times instanceof Integer;
        }

        private int toIndex(Object times) {
            if (isIndexNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                isIndexNode = insert(IsIndexNode.create());
            }
            if (isIndexNode.execute(times)) {
                if (castToindexNode == null) {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    castToindexNode = insert(CastToIndexNode.createOverflow());
                }
                return castToindexNode.execute(times);
            }
            throw raise(TypeError, ERROR_MSG, times);
        }

        public static RepeatNode create() {
            return RepeatNodeGen.create();
        }
    }

    public abstract static class ContainsNode extends SequenceStorageBaseNode {
        @Child private GetItemScalarNode getItemNode;
        @Child private BinaryComparisonNode equalsNode;
        @Child private CastToBooleanNode castToBooleanNode;

        public abstract boolean execute(SequenceStorage left, Object item);

        @Specialization(guards = "isEmpty(left)")
        @SuppressWarnings("unused")
        boolean doEmpty(SequenceStorage left, Object item) {
            return false;
        }

        @Specialization
        public boolean doByteStorage(ByteSequenceStorage s, int item) {
            return s.indexOfInt(item) != -1;
        }

        @Specialization
        public boolean doIntStorage(IntSequenceStorage s, int item) {
            return s.indexOfInt(item) != -1;
        }

        @Specialization
        public boolean doLongStorage(LongSequenceStorage s, long item) {
            return s.indexOfLong(item) != -1;
        }

        @Specialization
        public boolean doDoubleStorage(DoubleSequenceStorage s, double item) {
            return s.indexOfDouble(item) != -1;
        }

        @Specialization
        boolean doGeneric(SequenceStorage left, Object item) {
            for (int i = 0; i < left.length(); i++) {
                Object leftItem = getGetItemNode().execute(left, i);
                if (eq(leftItem, item)) {
                    return true;
                }
            }
            return false;
        }

        private GetItemScalarNode getGetItemNode() {
            if (getItemNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                getItemNode = insert(GetItemScalarNode.create());
            }
            return getItemNode;
        }

        private boolean eq(Object left, Object right) {
            if (equalsNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                equalsNode = insert(BinaryComparisonNode.create(__EQ__, __EQ__, "=="));
            }
            return castToBoolean(equalsNode.executeWith(left, right));
        }

        private boolean castToBoolean(Object value) {
            if (castToBooleanNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                castToBooleanNode = insert(CastToBooleanNode.createIfTrueNode());
            }
            return castToBooleanNode.executeWith(value);
        }

        public static ContainsNode create() {
            return ContainsNodeGen.create();
        }
    }

    public abstract static class GeneralizationNode extends SequenceStorageBaseNode {
        public abstract SequenceStorage execute(SequenceStorage toGeneralize, Object indicationValue);

    }

    /**
     * Does not allow any generalization but compatible types.
     */
    public abstract static class NoGeneralizationNode extends GeneralizationNode {

        private final String errorMessage;

        public NoGeneralizationNode(String errorMessage) {
            this.errorMessage = errorMessage;
        }

        @Specialization(guards = "compatibleAssign(s, indicationStorage)")
        SequenceStorage doGeneric(SequenceStorage s, @SuppressWarnings("unused") SequenceStorage indicationStorage) {
            return s;
        }

        @Specialization(guards = "isByteLike(s)")
        SequenceStorage doLongByte(SequenceStorage s, @SuppressWarnings("unused") byte val) {
            return s;
        }

        @Specialization(guards = "isInt(s) || isLong(s)")
        SequenceStorage doLongInteger(SequenceStorage s, @SuppressWarnings("unused") int val) {
            return s;
        }

        @Specialization(guards = "isLong(s)")
        SequenceStorage doLongLong(SequenceStorage s, @SuppressWarnings("unused") long val) {
            return s;
        }

        @Specialization(guards = "isList(s)")
        SequenceStorage doListList(SequenceStorage s, @SuppressWarnings("unused") PList val) {
            return s;
        }

        @Specialization(guards = "isTuple(s)")
        SequenceStorage doTupleTuple(SequenceStorage s, @SuppressWarnings("unused") PTuple val) {
            return s;
        }

        @Specialization(guards = "isObject(s)")
        SequenceStorage doObjectObject(SequenceStorage s, @SuppressWarnings("unused") Object val) {
            return s;
        }

        @Fallback
        SequenceStorage doGeneric(@SuppressWarnings("unused") SequenceStorage s, @SuppressWarnings("unused") Object indicationValue) {
            throw raise(TypeError, errorMessage);
        }

        public static NoGeneralizationNode create(String invalidItemErrorMessage) {
            return NoGeneralizationNodeGen.create(invalidItemErrorMessage);
        }
    }

    /**
     * Implements list generalization rules; previously in 'SequenceStroage.generalizeFor'.
     */
    public abstract static class ListGeneralizationNode extends GeneralizationNode {

        private static final int DEFAULT_CAPACITY = 8;

        @CompilationFinal private ValueProfile selfProfile;

        @Specialization
        ObjectSequenceStorage doObject(@SuppressWarnings("unused") ObjectSequenceStorage s, @SuppressWarnings("unused") Object indicationValue) {
            return s;
        }

        @Specialization
        SequenceStorage doEmptyStorage(@SuppressWarnings("unused") EmptySequenceStorage s, SequenceStorage other,
                        @Cached("createClassProfile()") ValueProfile otherProfile) {
            return otherProfile.profile(other).createEmpty(DEFAULT_CAPACITY);
        }

        @Specialization
        ByteSequenceStorage doEmptyByte(@SuppressWarnings("unused") EmptySequenceStorage s, @SuppressWarnings("unused") byte val) {
            return new ByteSequenceStorage(DEFAULT_CAPACITY);
        }

        @Specialization
        IntSequenceStorage doEmptyInteger(@SuppressWarnings("unused") EmptySequenceStorage s, @SuppressWarnings("unused") int val) {
            return new IntSequenceStorage();
        }

        @Specialization
        LongSequenceStorage doEmptyLong(@SuppressWarnings("unused") EmptySequenceStorage s, @SuppressWarnings("unused") long val) {
            return new LongSequenceStorage();
        }

        @Specialization
        DoubleSequenceStorage doEmptyDouble(@SuppressWarnings("unused") EmptySequenceStorage s, @SuppressWarnings("unused") double val) {
            return new DoubleSequenceStorage();
        }

        @Specialization
        ListSequenceStorage doEmptyPList(@SuppressWarnings("unused") EmptySequenceStorage s, PList val) {
            return new ListSequenceStorage(val.getSequenceStorage());
        }

        @Specialization
        TupleSequenceStorage doEmptyPTuple(@SuppressWarnings("unused") EmptySequenceStorage s, @SuppressWarnings("unused") PTuple val) {
            return new TupleSequenceStorage();
        }

        @Specialization
        ObjectSequenceStorage doEmptyObject(@SuppressWarnings("unused") EmptySequenceStorage s, @SuppressWarnings("unused") Object val) {
            return new ObjectSequenceStorage(DEFAULT_CAPACITY);
        }

        @Specialization
        ByteSequenceStorage doByteByte(ByteSequenceStorage s, @SuppressWarnings("unused") byte val) {
            return s;
        }

        @Specialization
        IntSequenceStorage doByteInteger(@SuppressWarnings("unused") ByteSequenceStorage s, @SuppressWarnings("unused") int val) {
            int[] copied = new int[s.length()];
            for (int i = 0; i < copied.length; i++) {
                copied[i] = s.getIntItemNormalized(i);
            }
            return new IntSequenceStorage(copied);
        }

        @Specialization
        LongSequenceStorage doByteLong(@SuppressWarnings("unused") ByteSequenceStorage s, @SuppressWarnings("unused") long val) {
            long[] copied = new long[s.length()];
            for (int i = 0; i < copied.length; i++) {
                copied[i] = s.getIntItemNormalized(i);
            }
            return new LongSequenceStorage(copied);
        }

        @Specialization
        SequenceStorage doIntegerInteger(IntSequenceStorage s, @SuppressWarnings("unused") int val) {
            return s;
        }

        @Specialization
        SequenceStorage doIntegerLong(@SuppressWarnings("unused") IntSequenceStorage s, @SuppressWarnings("unused") long val) {
            long[] copied = new long[s.length()];
            for (int i = 0; i < copied.length; i++) {
                copied[i] = s.getIntItemNormalized(i);
            }
            return new LongSequenceStorage(copied);
        }

        @Specialization
        LongSequenceStorage doLongByte(LongSequenceStorage s, @SuppressWarnings("unused") byte val) {
            return s;
        }

        @Specialization
        LongSequenceStorage doLongInteger(LongSequenceStorage s, @SuppressWarnings("unused") int val) {
            return s;
        }

        @Specialization
        LongSequenceStorage doLongLong(LongSequenceStorage s, @SuppressWarnings("unused") long val) {
            return s;
        }

        // TODO native sequence storage

        @Specialization(guards = "compatibleAssign(s, indicationStorage)")
        TypedSequenceStorage doTyped(TypedSequenceStorage s, @SuppressWarnings("unused") SequenceStorage indicationStorage) {
            return s;
        }

        @Fallback
        ObjectSequenceStorage doTyped(SequenceStorage s, @SuppressWarnings("unused") Object value) {
            SequenceStorage profiled = getSelfProfile().profile(s);
            if (profiled instanceof BasicSequenceStorage) {
                return new ObjectSequenceStorage(profiled.getInternalArray());
            }
            // TODO copy all values
            return new ObjectSequenceStorage(DEFAULT_CAPACITY);
        }

        private ValueProfile getSelfProfile() {
            if (selfProfile == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                selfProfile = ValueProfile.createClassProfile();
            }
            return selfProfile;
        }

        public static ListGeneralizationNode create() {
            return ListGeneralizationNodeGen.create();
        }

    }

    public abstract static class AppendNode extends SequenceStorageBaseNode {

        @Child private GeneralizationNode genNode;

        private final Supplier<GeneralizationNode> genNodeProvider;

        public AppendNode(Supplier<GeneralizationNode> genNodeProvider) {
            this.genNodeProvider = genNodeProvider;
        }

        public abstract SequenceStorage execute(SequenceStorage s, Object val);

        @Specialization
        SequenceStorage doEmpty(EmptySequenceStorage s, Object val,
                        @Cached("createRecursive()") AppendNode recursive) {
            SequenceStorage newStorage = generalizeStore(s, val);
            return recursive.execute(newStorage, val);
        }

        @Specialization(limit = "MAX_ARRAY_STORAGES", guards = "s.getClass() == cachedClass")
        SequenceStorage doManaged(BasicSequenceStorage s, Object val,
                        @Cached("s.getClass()") Class<? extends BasicSequenceStorage> cachedClass,
                        @Cached("create()") SetItemScalarNode setItemNode) {
            BasicSequenceStorage profiled = cachedClass.cast(s);
            int len = profiled.length();
            profiled.ensureCapacity(len + 1);
            try {
                setItemNode.execute(profiled, len, val);
                profiled.setNewLength(len + 1);
                return profiled;
            } catch (SequenceStoreException e) {
                SequenceStorage generalized = generalizeStore(profiled, e.getIndicationValue());
                generalized.ensureCapacity(len + 1);
                try {
                    setItemNode.execute(generalized, len, val);
                    generalized.setNewLength(len + 1);
                    return generalized;
                } catch (SequenceStoreException e1) {
                    CompilerDirectives.transferToInterpreter();
                    throw new IllegalStateException();
                }
            }
        }

        // TODO native sequence storage

        protected AppendNode createRecursive() {
            return AppendNodeGen.create(genNodeProvider);
        }

        private SequenceStorage generalizeStore(SequenceStorage storage, Object value) {
            if (genNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                genNode = insert(genNodeProvider.get());
            }
            return genNode.execute(storage, value);
        }

        public static AppendNode create(Supplier<GeneralizationNode> genNodeProvider) {
            return AppendNodeGen.create(genNodeProvider);
        }

    }

    public abstract static class CreateEmptyNode extends SequenceStorageBaseNode {

        public abstract SequenceStorage execute(SequenceStorage s, int cap);

        @Specialization(guards = "isBoolean(s)")
        BoolSequenceStorage doBoolean(@SuppressWarnings("unused") SequenceStorage s, int cap) {
            return new BoolSequenceStorage(cap);
        }

        @Specialization(guards = "isByte(s)")
        ByteSequenceStorage doByte(@SuppressWarnings("unused") SequenceStorage s, int cap) {
            return new ByteSequenceStorage(cap);
        }

        @Specialization(guards = "isChar(s)")
        CharSequenceStorage doChar(@SuppressWarnings("unused") SequenceStorage s, int cap) {
            return new CharSequenceStorage(cap);
        }

        @Specialization(guards = "isInt(s)")
        IntSequenceStorage doInt(@SuppressWarnings("unused") SequenceStorage s, int cap) {
            return new IntSequenceStorage(cap);
        }

        @Specialization(guards = "isLong(s)")
        LongSequenceStorage doLong(@SuppressWarnings("unused") SequenceStorage s, int cap) {
            return new LongSequenceStorage(cap);
        }

        @Specialization(guards = "isDouble(s)")
        DoubleSequenceStorage doDouble(@SuppressWarnings("unused") SequenceStorage s, int cap) {
            return new DoubleSequenceStorage(cap);
        }

        @Specialization(guards = "isList(s)")
        ListSequenceStorage doList(@SuppressWarnings("unused") SequenceStorage s, @SuppressWarnings("unused") int cap) {
            // TODO not quite accurate in case of native sequence storage
            return new ListSequenceStorage(s);
        }

        @Specialization(guards = "isTuple(s)")
        TupleSequenceStorage doTuple(@SuppressWarnings("unused") SequenceStorage s, int cap) {
            return new TupleSequenceStorage(cap);
        }

        @Fallback
        ObjectSequenceStorage doObject(@SuppressWarnings("unused") SequenceStorage s, int cap) {
            return new ObjectSequenceStorage(cap);
        }

        public static CreateEmptyNode create() {
            return CreateEmptyNodeGen.create();
        }
    }

    public abstract static class EnsureCapacityNode extends SequenceStorageBaseNode {

        public abstract SequenceStorage execute(SequenceStorage s, int cap);

        @Specialization
        EmptySequenceStorage doEmpty(EmptySequenceStorage s, @SuppressWarnings("unused") int cap) {
            return s;
        }

        @Specialization(limit = "MAX_SEQUENCE_STORAGES", guards = "s.getClass() == cachedClass")
        BasicSequenceStorage doManaged(BasicSequenceStorage s, int cap,
                        @Cached("create()") BranchProfile overflowErrorProfile,
                        @Cached("s.getClass()") Class<? extends BasicSequenceStorage> cachedClass) {
            try {
                BasicSequenceStorage profiled = cachedClass.cast(s);
                profiled.ensureCapacity(cap);
                return profiled;
            } catch (ArithmeticException | OutOfMemoryError e) {
                overflowErrorProfile.enter();
                throw raise(OverflowError);
            }
        }

        @Specialization
        NativeSequenceStorage doObject(@SuppressWarnings("unused") NativeSequenceStorage s, @SuppressWarnings("unused") int cap) {
            // TODO re-allocate native memory
            CompilerDirectives.transferToInterpreter();
            throw new UnsupportedOperationException();
        }

        public static EnsureCapacityNode create() {
            return EnsureCapacityNodeGen.create();
        }

    }

    public abstract static class LenNode extends SequenceStorageBaseNode {

        public abstract int execute(SequenceStorage s);

        @Specialization(limit = "MAX_SEQUENCE_STORAGES", guards = "s.getClass() == cachedClass")
        int doSpecial(SequenceStorage s,
                        @Cached("s.getClass()") Class<? extends SequenceStorage> cachedClass) {
            return cachedClass.cast(s).length();
        }

        public static LenNode create() {
            return LenNodeGen.create();
        }
    }

    public abstract static class SetLenNode extends SequenceStorageBaseNode {

        public abstract void execute(SequenceStorage s, int len);

        @Specialization(limit = "MAX_SEQUENCE_STORAGES", guards = "s.getClass() == cachedClass")
        void doSpecial(SequenceStorage s, int len,
                        @Cached("s.getClass()") Class<? extends SequenceStorage> cachedClass) {
            cachedClass.cast(s).setNewLength(len);
        }

        public static SetLenNode create() {
            return SetLenNodeGen.create();
        }
    }

    public abstract static class DeleteNode extends NormalizingNode {
        @Child private DeleteItemNode deleteItemNode;
        @Child private DeleteSliceNode deleteSliceNode;
        private final String keyTypeErrorMessage;

        public DeleteNode(NormalizeIndexNode normalizeIndexNode, String keyTypeErrorMessage) {
            super(normalizeIndexNode);
            this.keyTypeErrorMessage = keyTypeErrorMessage;
        }

        public abstract void execute(SequenceStorage s, Object indexOrSlice);

        public abstract void execute(SequenceStorage s, int index);

        public abstract void execute(SequenceStorage s, long index);

        @Specialization
        protected void doScalarInt(SequenceStorage storage, int idx) {
            getGetItemScalarNode().execute(storage, normalizeIndex(idx, storage));
        }

        @Specialization
        protected void doScalarLong(SequenceStorage storage, long idx) {
            getGetItemScalarNode().execute(storage, normalizeIndex(idx, storage));
        }

        @Specialization
        protected void doScalarPInt(SequenceStorage storage, PInt idx) {
            getGetItemScalarNode().execute(storage, normalizeIndex(idx, storage));
        }

        @Specialization(guards = "!isPSlice(idx)")
        protected void doScalarGeneric(SequenceStorage storage, Object idx) {
            getGetItemScalarNode().execute(storage, normalizeIndex(idx, storage));
        }

        @Specialization
        protected void doSlice(SequenceStorage storage, PSlice slice) {
            SliceInfo info = slice.computeActualIndices(storage.length());
            try {
                getGetItemSliceNode().execute(storage, info);
            } catch (SequenceStoreException e) {
                CompilerDirectives.transferToInterpreter();
                throw new IllegalStateException();
            }
        }

        @Fallback
        protected void doInvalidKey(@SuppressWarnings("unused") SequenceStorage storage, Object key) {
            throw raise(TypeError, keyTypeErrorMessage, key);
        }

        private DeleteItemNode getGetItemScalarNode() {
            if (deleteItemNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                deleteItemNode = insert(DeleteItemNode.create());
            }
            return deleteItemNode;
        }

        private DeleteSliceNode getGetItemSliceNode() {
            if (deleteSliceNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                deleteSliceNode = insert(DeleteSliceNode.create());
            }
            return deleteSliceNode;
        }

        public static DeleteNode createNotNormalized() {
            return DeleteNodeGen.create(null, KEY_TYPE_ERROR_MESSAGE);
        }

        public static DeleteNode create(NormalizeIndexNode normalizeIndexNode) {
            return DeleteNodeGen.create(normalizeIndexNode, KEY_TYPE_ERROR_MESSAGE);
        }

        public static DeleteNode create() {
            return DeleteNodeGen.create(NormalizeIndexNode.create(), KEY_TYPE_ERROR_MESSAGE);
        }

        public static DeleteNode createNotNormalized(String keyTypeErrorMessage) {
            return DeleteNodeGen.create(null, keyTypeErrorMessage);
        }

        public static DeleteNode create(NormalizeIndexNode normalizeIndexNode, String keyTypeErrorMessage) {
            return DeleteNodeGen.create(normalizeIndexNode, keyTypeErrorMessage);
        }

        public static DeleteNode create(String keyTypeErrorMessage) {
            return DeleteNodeGen.create(NormalizeIndexNode.create(), keyTypeErrorMessage);
        }
    }

    abstract static class DeleteItemNode extends SequenceStorageBaseNode {

        public abstract void execute(SequenceStorage s, int idx);

        @Specialization(limit = "MAX_SEQUENCE_STORAGES", guards = {"s.getClass() == cachedClass", "isLastItem(s, cachedClass, idx)"})
        void doLastItem(SequenceStorage s, @SuppressWarnings("unused") int idx,
                        @Cached("s.getClass()") Class<? extends SequenceStorage> cachedClass) {
            SequenceStorage profiled = cachedClass.cast(s);
            profiled.setNewLength(profiled.length() - 1);
        }

        @Specialization(limit = "MAX_SEQUENCE_STORAGES", guards = "s.getClass() == cachedClass")
        void doGeneric(SequenceStorage s, @SuppressWarnings("unused") int idx,
                        @Cached("create()") GetItemScalarNode getItemNode,
                        @Cached("create()") SetItemScalarNode setItemNode,
                        @Cached("s.getClass()") Class<? extends SequenceStorage> cachedClass) {
            SequenceStorage profiled = cachedClass.cast(s);
            int len = profiled.length();

            for (int i = idx; i < len - 1; i++) {
                setItemNode.execute(profiled, i, getItemNode.execute(profiled, i + 1));
            }
            profiled.setNewLength(len - 1);
        }

        protected static boolean isLastItem(SequenceStorage s, Class<? extends SequenceStorage> cachedClass, int idx) {
            return idx == cachedClass.cast(s).length() - 1;
        }

        protected static DeleteItemNode create() {
            return DeleteItemNodeGen.create();
        }
    }

    abstract static class DeleteSliceNode extends SequenceStorageBaseNode {

        public abstract void execute(SequenceStorage s, SliceInfo info);

        @Specialization
        void delSlice(SequenceStorage s, SliceInfo info,
                        @Cached("create()") LenNode lenNode,
                        @Cached("create()") SetLenNode setLenNode,
                        @Cached("create()") GetItemScalarNode getItemNode,
                        @Cached("create()") SetItemScalarNode setItemNode) {
            int length = lenNode.execute(s);
            int start = info.start;
            int stop = info.stop;
            int step = info.step;

            int decraseLen; // how much will be the result array shorter
            int index;  // index of the "old" array
            if (step < 0) {
                // For the simplicity of algorithm, then start and stop are swapped.
                // The start index has to recalculated according the step, because
                // the algorithm bellow removes the start itema and then start + step ....
                step = Math.abs(step);
                stop++;
                int tmpStart = stop + ((start - stop) % step);
                stop = start + 1;
                start = tmpStart;
            }
            int arrayIndex = start; // pointer to the "new" form of array
            if (step == 1) {
                // this is easy, just remove the part of array
                decraseLen = stop - start;
                index = start + decraseLen;
            } else {
                int nextStep = index = start; // nextStep is a pointer to the next removed item
                decraseLen = (stop - start - 1) / step + 1;
                for (; index < stop && nextStep < stop; index++) {
                    if (nextStep == index) {
                        nextStep += step;
                    } else {
                        setItemNode.execute(s, arrayIndex, getItemNode.execute(s, index));
                        arrayIndex++;
                    }
                }
            }
            if (decraseLen > 0) {
                // shift all other items in array behind the last change
                for (; index < length; arrayIndex++, index++) {
                    setItemNode.execute(s, arrayIndex, getItemNode.execute(s, index));
                }
                // change the result length
                // TODO reallocate array if the change is big?
                // Then unnecessary big array is kept in the memory.
                setLenNode.execute(s, length - decraseLen);
            }
        }

        protected static DeleteSliceNode create() {
            return DeleteSliceNodeGen.create();
        }
    }

    abstract static class GetElementType extends PBaseNode {

        public abstract ListStorageType execute(SequenceStorage s);

        @Specialization(limit = "cacheLimit()", guards = {"s.getClass() == cachedClass"})
        ListStorageType doCached(SequenceStorage s,
                        @Cached("s.getClass()") Class<? extends SequenceStorage> cachedClass) {
            return cachedClass.cast(s).getElementType();
        }

        protected static int cacheLimit() {
            return SequenceStorageBaseNode.MAX_SEQUENCE_STORAGES;
        }
    }

    @ImportStatic(SpecialMethodNames.class)
    public abstract static class ItemIndexNode extends SequenceStorageBaseNode {

        @Child private GetItemScalarNode getItemNode;
        @Child private LenNode lenNode;

        public abstract int execute(SequenceStorage s, Object item, int start, int end);

        public abstract int execute(SequenceStorage s, boolean item, int start, int end);

        public abstract int execute(SequenceStorage s, char item, int start, int end);

        public abstract int execute(SequenceStorage s, int item, int start, int end);

        public abstract int execute(SequenceStorage s, long item, int start, int end);

        public abstract int execute(SequenceStorage s, double item, int start, int end);

        @Specialization(guards = "isBoolean(s)")
        int doBoolean(SequenceStorage s, boolean item, int start, int end) {
            for (int i = start; i < getLength(s, end); i++) {
                if (getItemNode().executeBoolean(s, i) == item) {
                    return i;
                }
            }
            return -1;
        }

        @Specialization(guards = "isByte(s)")
        int doByte(SequenceStorage s, byte item, int start, int end) {
            for (int i = start; i < getLength(s, end); i++) {
                if (getItemNode().executeByte(s, i) == item) {
                    return i;
                }
            }
            return -1;
        }

        @Specialization(guards = "isChar(s)")
        int doChar(SequenceStorage s, char item, int start, int end) {
            for (int i = start; i < getLength(s, end); i++) {
                if (getItemNode().executeChar(s, i) == item) {
                    return i;
                }
            }
            return -1;
        }

        @Specialization(guards = "isInt(s)")
        int doInt(SequenceStorage s, int item, int start, int end) {
            for (int i = start; i < getLength(s, end); i++) {
                if (getItemNode().executeInt(s, i) == item) {
                    return i;
                }
            }
            return -1;
        }

        @Specialization(guards = "isLong(s)")
        int doLong(SequenceStorage s, long item, int start, int end) {
            for (int i = start; i < getLength(s, end); i++) {
                if (getItemNode().executeLong(s, i) == item) {
                    return i;
                }
            }
            return -1;
        }

        @Specialization(guards = "isDouble(s)")
        int doDouble(SequenceStorage s, double item, int start, int end) {
            for (int i = start; i < getLength(s, end); i++) {
                if (getItemNode().executeDouble(s, i) == item) {
                    return i;
                }
            }
            return -1;
        }

        @Specialization
        int doGeneric(SequenceStorage s, Object item, int start, int end,
                        @Cached("createIfTrueNode()") CastToBooleanNode castToBooleanNode,
                        @Cached("create(__EQ__, __EQ__, __EQ__)") BinaryComparisonNode eqNode) {
            for (int i = start; i < getLength(s, end); i++) {
                Object object = getItemNode().execute(s, i);
                if (castToBooleanNode.executeWith(eqNode.executeWith(object, item))) {
                    return i;
                }
            }
            return -1;
        }

        private GetItemScalarNode getItemNode() {
            if (getItemNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                getItemNode = insert(GetItemScalarNode.create());
            }
            return getItemNode;
        }

        private int getLength(SequenceStorage s, int end) {
            if (lenNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                lenNode = insert(LenNode.create());
            }
            return Math.min(lenNode.execute(s), end);
        }

        public static ItemIndexNode create() {
            return ItemIndexNodeGen.create();
        }
    }
}
