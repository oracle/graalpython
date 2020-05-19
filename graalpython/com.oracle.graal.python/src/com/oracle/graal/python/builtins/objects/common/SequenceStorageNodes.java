/*
 * Copyright (c) 2018, 2020, Oracle and/or its affiliates.
 * Copyright (c) 2013, Regents of the University of California
 *
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification, are
 * permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this list of
 * conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice, this list of
 * conditions and the following disclaimer in the documentation and/or other materials provided
 * with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS
 * OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
 * MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE
 * COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE
 * GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED
 * AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED
 * OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.oracle.graal.python.builtins.objects.common;

import static com.oracle.graal.python.builtins.objects.cext.NativeCAPISymbols.FUN_PY_TRUFFLE_BYTE_ARRAY_TO_NATIVE;
import static com.oracle.graal.python.builtins.objects.cext.NativeCAPISymbols.FUN_PY_TRUFFLE_DOUBLE_ARRAY_TO_NATIVE;
import static com.oracle.graal.python.builtins.objects.cext.NativeCAPISymbols.FUN_PY_TRUFFLE_INT_ARRAY_TO_NATIVE;
import static com.oracle.graal.python.builtins.objects.cext.NativeCAPISymbols.FUN_PY_TRUFFLE_LONG_ARRAY_TO_NATIVE;
import static com.oracle.graal.python.builtins.objects.cext.NativeCAPISymbols.FUN_PY_TRUFFLE_OBJECT_ARRAY_TO_NATIVE;
import static com.oracle.graal.python.builtins.objects.common.IndexNodes.NormalizeIndexNode.INDEX_OUT_OF_BOUNDS;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__EQ__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__GE__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__GT__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__LE__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__LT__;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.MemoryError;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.OverflowError;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.SystemError;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.TypeError;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.ValueError;
import static com.oracle.graal.python.runtime.sequence.storage.SequenceStorage.ListStorageType.Boolean;
import static com.oracle.graal.python.runtime.sequence.storage.SequenceStorage.ListStorageType.Byte;
import static com.oracle.graal.python.runtime.sequence.storage.SequenceStorage.ListStorageType.Char;
import static com.oracle.graal.python.runtime.sequence.storage.SequenceStorage.ListStorageType.Double;
import static com.oracle.graal.python.runtime.sequence.storage.SequenceStorage.ListStorageType.Empty;
import static com.oracle.graal.python.runtime.sequence.storage.SequenceStorage.ListStorageType.Int;
import static com.oracle.graal.python.runtime.sequence.storage.SequenceStorage.ListStorageType.List;
import static com.oracle.graal.python.runtime.sequence.storage.SequenceStorage.ListStorageType.Long;
import static com.oracle.graal.python.runtime.sequence.storage.SequenceStorage.ListStorageType.Tuple;
import static com.oracle.graal.python.runtime.sequence.storage.SequenceStorage.ListStorageType.Uninitialized;

import java.lang.reflect.Array;
import java.util.Arrays;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.objects.cext.CExtNodes;
import com.oracle.graal.python.builtins.objects.cext.CExtNodes.PCallCapiFunction;
import com.oracle.graal.python.builtins.objects.cext.CExtNodes.ToSulongNode;
import com.oracle.graal.python.builtins.objects.cext.NativeCAPISymbols;
import com.oracle.graal.python.builtins.objects.common.IndexNodes.NormalizeIndexCustomMessageNode;
import com.oracle.graal.python.builtins.objects.common.IndexNodes.NormalizeIndexNode;
import com.oracle.graal.python.builtins.objects.common.SequenceNodes.GetSequenceStorageNode;
import com.oracle.graal.python.builtins.objects.common.SequenceStorageNodesFactory.AppendNodeGen;
import com.oracle.graal.python.builtins.objects.common.SequenceStorageNodesFactory.CmpNodeGen;
import com.oracle.graal.python.builtins.objects.common.SequenceStorageNodesFactory.ConcatBaseNodeGen;
import com.oracle.graal.python.builtins.objects.common.SequenceStorageNodesFactory.ConcatNodeGen;
import com.oracle.graal.python.builtins.objects.common.SequenceStorageNodesFactory.ContainsNodeGen;
import com.oracle.graal.python.builtins.objects.common.SequenceStorageNodesFactory.CopyItemNodeGen;
import com.oracle.graal.python.builtins.objects.common.SequenceStorageNodesFactory.CopyNodeGen;
import com.oracle.graal.python.builtins.objects.common.SequenceStorageNodesFactory.CreateEmptyNodeGen;
import com.oracle.graal.python.builtins.objects.common.SequenceStorageNodesFactory.DeleteItemNodeGen;
import com.oracle.graal.python.builtins.objects.common.SequenceStorageNodesFactory.DeleteNodeGen;
import com.oracle.graal.python.builtins.objects.common.SequenceStorageNodesFactory.DeleteSliceNodeGen;
import com.oracle.graal.python.builtins.objects.common.SequenceStorageNodesFactory.DoGeneralizationNodeGen;
import com.oracle.graal.python.builtins.objects.common.SequenceStorageNodesFactory.EnsureCapacityNodeGen;
import com.oracle.graal.python.builtins.objects.common.SequenceStorageNodesFactory.ExtendNodeGen;
import com.oracle.graal.python.builtins.objects.common.SequenceStorageNodesFactory.GetElementTypeNodeGen;
import com.oracle.graal.python.builtins.objects.common.SequenceStorageNodesFactory.GetItemDynamicNodeGen;
import com.oracle.graal.python.builtins.objects.common.SequenceStorageNodesFactory.GetItemNodeGen;
import com.oracle.graal.python.builtins.objects.common.SequenceStorageNodesFactory.GetItemScalarNodeGen;
import com.oracle.graal.python.builtins.objects.common.SequenceStorageNodesFactory.GetItemSliceNodeGen;
import com.oracle.graal.python.builtins.objects.common.SequenceStorageNodesFactory.InsertItemNodeGen;
import com.oracle.graal.python.builtins.objects.common.SequenceStorageNodesFactory.IsAssignCompatibleNodeGen;
import com.oracle.graal.python.builtins.objects.common.SequenceStorageNodesFactory.IsDataTypeCompatibleNodeGen;
import com.oracle.graal.python.builtins.objects.common.SequenceStorageNodesFactory.ItemIndexNodeGen;
import com.oracle.graal.python.builtins.objects.common.SequenceStorageNodesFactory.LenNodeGen;
import com.oracle.graal.python.builtins.objects.common.SequenceStorageNodesFactory.ListGeneralizationNodeGen;
import com.oracle.graal.python.builtins.objects.common.SequenceStorageNodesFactory.NoGeneralizationCustomMessageNodeGen;
import com.oracle.graal.python.builtins.objects.common.SequenceStorageNodesFactory.NoGeneralizationNodeGen;
import com.oracle.graal.python.builtins.objects.common.SequenceStorageNodesFactory.RepeatNodeGen;
import com.oracle.graal.python.builtins.objects.common.SequenceStorageNodesFactory.SetItemDynamicNodeGen;
import com.oracle.graal.python.builtins.objects.common.SequenceStorageNodesFactory.SetItemNodeGen;
import com.oracle.graal.python.builtins.objects.common.SequenceStorageNodesFactory.SetItemScalarNodeGen;
import com.oracle.graal.python.builtins.objects.common.SequenceStorageNodesFactory.SetItemSliceNodeGen;
import com.oracle.graal.python.builtins.objects.common.SequenceStorageNodesFactory.SetLenNodeGen;
import com.oracle.graal.python.builtins.objects.common.SequenceStorageNodesFactory.SetStorageSliceNodeGen;
import com.oracle.graal.python.builtins.objects.common.SequenceStorageNodesFactory.StorageToNativeNodeGen;
import com.oracle.graal.python.builtins.objects.common.SequenceStorageNodesFactory.VerifyNativeItemNodeGen;
import com.oracle.graal.python.builtins.objects.function.PArguments;
import com.oracle.graal.python.builtins.objects.function.PArguments.ThreadState;
import com.oracle.graal.python.builtins.objects.ints.PInt;
import com.oracle.graal.python.builtins.objects.list.PList;
import com.oracle.graal.python.builtins.objects.object.PythonObjectLibrary;
import com.oracle.graal.python.builtins.objects.range.PRange;
import com.oracle.graal.python.builtins.objects.slice.PSlice;
import com.oracle.graal.python.builtins.objects.slice.PSlice.SliceInfo;
import com.oracle.graal.python.builtins.objects.str.PString;
import com.oracle.graal.python.builtins.objects.tuple.PTuple;
import com.oracle.graal.python.builtins.objects.type.LazyPythonClass;
import com.oracle.graal.python.nodes.PGuards;
import com.oracle.graal.python.nodes.PNodeWithContext;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.SpecialMethodNames;
import com.oracle.graal.python.nodes.builtins.ListNodes;
import com.oracle.graal.python.nodes.control.GetIteratorExpressionNode.GetIteratorNode;
import com.oracle.graal.python.nodes.control.GetNextNode;
import com.oracle.graal.python.nodes.control.GetNextNode.GetNextWithoutFrameNode;
import com.oracle.graal.python.nodes.control.GetNextNodeFactory.GetNextWithoutFrameNodeGen;
import com.oracle.graal.python.nodes.expression.BinaryComparisonNode;
import com.oracle.graal.python.nodes.expression.CoerceToBooleanNode;
import com.oracle.graal.python.nodes.object.GetLazyClassNode;
import com.oracle.graal.python.nodes.object.IsBuiltinClassProfile;
import com.oracle.graal.python.nodes.util.CastToByteNode;
import com.oracle.graal.python.nodes.util.CastToJavaByteNode;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.graal.python.runtime.PythonOptions;
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
import com.oracle.graal.python.runtime.sequence.storage.MroSequenceStorage;
import com.oracle.graal.python.runtime.sequence.storage.NativeSequenceStorage;
import com.oracle.graal.python.runtime.sequence.storage.ObjectSequenceStorage;
import com.oracle.graal.python.runtime.sequence.storage.RangeSequenceStorage;
import com.oracle.graal.python.runtime.sequence.storage.SequenceStorage;
import com.oracle.graal.python.runtime.sequence.storage.SequenceStorage.ListStorageType;
import com.oracle.graal.python.runtime.sequence.storage.SequenceStorageFactory;
import com.oracle.graal.python.runtime.sequence.storage.SequenceStoreException;
import com.oracle.graal.python.runtime.sequence.storage.TupleSequenceStorage;
import com.oracle.graal.python.runtime.sequence.storage.TypedSequenceStorage;
import com.oracle.graal.python.util.BiFunction;
import com.oracle.graal.python.util.PythonUtils;
import com.oracle.graal.python.util.Supplier;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Exclusive;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.CachedContext;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.InvalidArrayIndexException;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.interop.UnsupportedTypeException;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.UnexpectedResultException;
import com.oracle.truffle.api.profiles.BranchProfile;
import com.oracle.truffle.api.profiles.ConditionProfile;
import com.oracle.truffle.api.profiles.ValueProfile;

public abstract class SequenceStorageNodes {

    public static interface GenNodeSupplier {
        GeneralizationNode create();

        GeneralizationNode getUncached();
    }

    public static interface ContainerFactory {

        Object apply(SequenceStorage s, PythonObjectFactory factory);
    }

    @GenerateUncached
    public abstract static class IsAssignCompatibleNode extends Node {

        protected abstract boolean execute(SequenceStorage lhs, SequenceStorage rhs);

        /**
         * Tests if each element of {@code rhs} can be assign to {@code lhs} without casting.
         */
        @Specialization
        static boolean compatibleAssign(SequenceStorage lhs, SequenceStorage rhs,
                        @Cached GetElementType getElementTypeNode) {
            ListStorageType rhsType = getElementTypeNode.execute(rhs);
            switch (getElementTypeNode.execute(lhs)) {
                case Boolean:
                    return rhsType == Boolean || rhsType == Uninitialized || rhsType == Empty;
                case Byte:
                    return rhsType == Boolean || rhsType == Byte || rhsType == Uninitialized || rhsType == Empty;
                case Int:
                    return rhsType == Boolean || rhsType == ListStorageType.Byte || rhsType == ListStorageType.Int || rhsType == Uninitialized || rhsType == Empty;
                case Long:
                    return rhsType == Boolean || rhsType == Byte || rhsType == Int || rhsType == Long || rhsType == Uninitialized || rhsType == Empty;
                case Double:
                    return rhsType == Double || rhsType == Uninitialized || rhsType == Empty;
                case Char:
                    return rhsType == Char || rhsType == Uninitialized || rhsType == Empty;
                case Tuple:
                    return rhsType == Tuple || rhsType == Uninitialized || rhsType == Empty;
                case List:
                    return rhsType == List || rhsType == Uninitialized || rhsType == Empty;
                case Generic:
                    return true;
                case Empty:
                case Uninitialized:
                    return false;
            }
            assert false : "should not reach";
            return false;
        }

        public static IsAssignCompatibleNode create() {
            return IsAssignCompatibleNodeGen.create();
        }

        public static IsAssignCompatibleNode getUncached() {
            return IsAssignCompatibleNodeGen.getUncached();
        }
    }

    @GenerateUncached
    abstract static class IsDataTypeCompatibleNode extends Node {

        protected abstract boolean execute(SequenceStorage lhs, SequenceStorage rhs);

        /**
         * Tests if each element of {@code rhs} can be assign to {@code lhs} without casting.
         */
        @Specialization
        static boolean compatibleAssign(SequenceStorage lhs, SequenceStorage rhs,
                        @Cached GetElementType getElementTypeNode) {
            ListStorageType rhsType = getElementTypeNode.execute(rhs);
            switch (getElementTypeNode.execute(lhs)) {
                case Boolean:
                case Byte:
                case Int:
                case Long:
                    return rhsType == Boolean || rhsType == Byte || rhsType == Int || rhsType == Long || rhsType == Uninitialized || rhsType == Empty;
                case Double:
                    return rhsType == Double || rhsType == Uninitialized || rhsType == Empty;
                case Char:
                    return rhsType == Char || rhsType == Uninitialized || rhsType == Empty;
                case Tuple:
                    return rhsType == Tuple || rhsType == Uninitialized || rhsType == Empty;
                case List:
                    return rhsType == List || rhsType == Uninitialized || rhsType == Empty;
                case Generic:
                    return true;
                case Empty:
                case Uninitialized:
                    return false;
            }
            assert false : "should not reach";
            return false;
        }

        public static IsDataTypeCompatibleNode create() {
            return IsDataTypeCompatibleNodeGen.create();
        }

        public static IsDataTypeCompatibleNode getUncached() {
            return IsDataTypeCompatibleNodeGen.getUncached();
        }
    }

    @ImportStatic(PythonOptions.class)
    abstract static class SequenceStorageBaseNode extends PNodeWithContext {

        protected static final int DEFAULT_CAPACITY = 8;

        protected static final int MAX_SEQUENCE_STORAGES = 13;
        protected static final int MAX_ARRAY_STORAGES = 9;

        protected static boolean isByteStorage(NativeSequenceStorage store) {
            return store.getElementType() == ListStorageType.Byte;
        }

        /**
         * Tests if {@code left} has the same element type as {@code right}.
         */
        protected static boolean compatible(SequenceStorage left, NativeSequenceStorage right) {
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

        protected static boolean isNative(SequenceStorage store) {
            return store instanceof NativeSequenceStorage;
        }

        protected boolean isEmpty(LenNode lenNode, SequenceStorage left) {
            return lenNode.execute(left) == 0;
        }

        protected static boolean isBoolean(GetElementType getElementTypeNode, SequenceStorage s) {
            return getElementTypeNode.execute(s) == ListStorageType.Boolean;
        }

        protected static boolean isByte(GetElementType getElementTypeNode, SequenceStorage s) {
            return getElementTypeNode.execute(s) == ListStorageType.Byte;
        }

        protected static boolean isByteLike(GetElementType getElementTypeNode, SequenceStorage s) {
            return isByte(getElementTypeNode, s) || isInt(getElementTypeNode, s) || isLong(getElementTypeNode, s);
        }

        protected static boolean isChar(GetElementType getElementTypeNode, SequenceStorage s) {
            return getElementTypeNode.execute(s) == ListStorageType.Char;
        }

        protected static boolean isInt(GetElementType getElementTypeNode, SequenceStorage s) {
            return getElementTypeNode.execute(s) == ListStorageType.Int;
        }

        protected static boolean isLong(GetElementType getElementTypeNode, SequenceStorage s) {
            return getElementTypeNode.execute(s) == ListStorageType.Long;
        }

        protected static boolean isDouble(GetElementType getElementTypeNode, SequenceStorage s) {
            return getElementTypeNode.execute(s) == ListStorageType.Double;
        }

        protected static boolean isObject(GetElementType getElementTypeNode, SequenceStorage s) {
            return getElementTypeNode.execute(s) == ListStorageType.Generic;
        }

        protected static boolean isTuple(GetElementType getElementTypeNode, SequenceStorage s) {
            return getElementTypeNode.execute(s) == ListStorageType.Tuple;
        }

        protected static boolean isList(GetElementType getElementTypeNode, SequenceStorage s) {
            return getElementTypeNode.execute(s) == ListStorageType.List;
        }

        protected static boolean isBoolean(ListStorageType et) {
            return et == ListStorageType.Boolean;
        }

        protected static boolean isByte(ListStorageType et) {
            return et == ListStorageType.Byte;
        }

        protected static boolean isByteLike(ListStorageType et) {
            return isByte(et) || isInt(et) || isLong(et);
        }

        protected static boolean isChar(ListStorageType et) {
            return et == ListStorageType.Char;
        }

        protected static boolean isInt(ListStorageType et) {
            return et == ListStorageType.Int;
        }

        protected static boolean isLong(ListStorageType et) {
            return et == ListStorageType.Long;
        }

        protected static boolean isDouble(ListStorageType et) {
            return et == ListStorageType.Double;
        }

        protected static boolean isObject(ListStorageType et) {
            return et == ListStorageType.Generic;
        }

        protected static boolean isTuple(ListStorageType et) {
            return et == ListStorageType.Tuple;
        }

        protected static boolean isList(ListStorageType et) {
            return et == ListStorageType.List;
        }

        protected static boolean hasStorage(Object source) {
            return source instanceof PSequence && !(source instanceof PString);
        }
    }

    abstract static class NormalizingNode extends PNodeWithContext {

        protected static final String KEY_TYPE_ERROR_MESSAGE = "indices must be integers or slices, not %p";
        @Child private NormalizeIndexNode normalizeIndexNode;
        @Child private PythonObjectLibrary lib;
        @Child private LenNode lenNode;
        @CompilationFinal private ConditionProfile gotFrameProfile;

        protected NormalizingNode(NormalizeIndexNode normalizeIndexNode) {
            this.normalizeIndexNode = normalizeIndexNode;
        }

        private PythonObjectLibrary getLibrary() {
            if (lib == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                lib = insert(PythonObjectLibrary.getFactory().createDispatched(PythonOptions.getCallSiteInlineCacheMaxDepth()));
            }
            return lib;
        }

        protected final int normalizeIndex(VirtualFrame frame, Object idx, SequenceStorage store) {
            if (gotFrameProfile == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                gotFrameProfile = ConditionProfile.createBinaryProfile();
            }
            int intIdx;
            if (gotFrameProfile.profile(frame != null)) {
                intIdx = getLibrary().asSizeWithState(idx, PythonBuiltinClassType.IndexError, PArguments.getThreadState(frame));
            } else {
                intIdx = getLibrary().asSize(idx, PythonBuiltinClassType.IndexError);
            }
            if (normalizeIndexNode != null) {
                return normalizeIndexNode.execute(intIdx, getStoreLength(store));
            }
            return intIdx;
        }

        protected final int normalizeIndex(@SuppressWarnings("unused") VirtualFrame frame, int idx, SequenceStorage store) {
            if (normalizeIndexNode != null) {
                return normalizeIndexNode.execute(idx, getStoreLength(store));
            }
            return idx;
        }

        protected final int normalizeIndex(@SuppressWarnings("unused") VirtualFrame frame, long idx, SequenceStorage store) {
            int intIdx = getLibrary().asSize(idx, PythonBuiltinClassType.IndexError);
            if (normalizeIndexNode != null) {
                return normalizeIndexNode.execute(intIdx, getStoreLength(store));
            }
            return intIdx;
        }

        private int getStoreLength(SequenceStorage store) {
            if (lenNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                lenNode = insert(LenNode.create());
            }
            return lenNode.execute(store);
        }

        protected static boolean isPSlice(Object obj) {
            return obj instanceof PSlice;
        }

    }

    public abstract static class GetItemNode extends NormalizingNode {

        @Child private GetItemScalarNode getItemScalarNode;
        @Child private GetItemSliceNode getItemSliceNode;
        @Child private PRaiseNode raiseNode;
        private final String keyTypeErrorMessage;
        private final BiFunction<SequenceStorage, PythonObjectFactory, Object> factoryMethod;

        public GetItemNode(NormalizeIndexNode normalizeIndexNode, String keyTypeErrorMessage, BiFunction<SequenceStorage, PythonObjectFactory, Object> factoryMethod) {
            super(normalizeIndexNode);
            this.keyTypeErrorMessage = keyTypeErrorMessage;
            this.factoryMethod = factoryMethod;
        }

        public abstract Object execute(VirtualFrame frame, SequenceStorage s, Object key);

        public abstract Object execute(VirtualFrame frame, SequenceStorage s, int key);

        public abstract Object execute(VirtualFrame frame, SequenceStorage s, long key);

        public abstract int executeInt(VirtualFrame frame, SequenceStorage s, int key);

        public abstract long executeLong(VirtualFrame frame, SequenceStorage s, long key);

        @Specialization
        protected Object doScalarInt(VirtualFrame frame, SequenceStorage storage, int idx) {
            return getGetItemScalarNode().execute(storage, normalizeIndex(frame, idx, storage));
        }

        @Specialization
        protected Object doScalarLong(VirtualFrame frame, SequenceStorage storage, long idx) {
            return getGetItemScalarNode().execute(storage, normalizeIndex(frame, idx, storage));
        }

        @Specialization
        protected Object doScalarPInt(VirtualFrame frame, SequenceStorage storage, PInt idx) {
            return getGetItemScalarNode().execute(storage, normalizeIndex(frame, idx, storage));
        }

        @Specialization(guards = "!isPSlice(idx)")
        protected Object doScalarGeneric(VirtualFrame frame, SequenceStorage storage, Object idx) {
            return getGetItemScalarNode().execute(storage, normalizeIndex(frame, idx, storage));
        }

        @Specialization
        protected Object doSlice(SequenceStorage storage, PSlice slice,
                        @Cached LenNode lenNode,
                        @Cached PythonObjectFactory factory) {
            SliceInfo info = slice.computeIndices(lenNode.execute(storage));
            if (factoryMethod != null) {
                return factoryMethod.apply(getGetItemSliceNode().execute(storage, info.start, info.stop, info.step, info.length), factory);
            }
            CompilerDirectives.transferToInterpreterAndInvalidate();
            throw new IllegalStateException();
        }

        @Fallback
        protected Object doInvalidKey(@SuppressWarnings("unused") SequenceStorage storage, Object key) {
            throw ensureRaiseNode().raise(TypeError, keyTypeErrorMessage, key);
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

        private PRaiseNode ensureRaiseNode() {
            if (raiseNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                raiseNode = insert(PRaiseNode.create());
            }
            return raiseNode;
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

    @GenerateUncached
    @ImportStatic(PGuards.class)
    public abstract static class GetItemDynamicNode extends Node {

        public abstract Object execute(ContainerFactory factoryMethod, SequenceStorage s, Object key);

        public final Object execute(SequenceStorage s, int key) {
            return execute(null, s, key);
        }

        public final Object execute(SequenceStorage s, long key) {
            return execute(null, s, key);
        }

        public final Object execute(SequenceStorage s, PInt key) {
            return execute(null, s, key);
        }

        @Specialization
        protected Object doScalarInt(@SuppressWarnings("unused") ContainerFactory factoryMethod, SequenceStorage storage, int idx,
                        @Shared("getItemScalarNode") @Cached GetItemScalarNode getItemScalarNode,
                        @Shared("normalizeIndexNode") @Cached NormalizeIndexCustomMessageNode normalizeIndexNode,
                        @Shared("lenNode") @Cached LenNode lenNode) {
            return getItemScalarNode.execute(storage, normalizeIndexNode.execute(idx, lenNode.execute(storage), INDEX_OUT_OF_BOUNDS));
        }

        @Specialization
        protected Object doScalarLong(@SuppressWarnings("unused") ContainerFactory factoryMethod, SequenceStorage storage, long idx,
                        @Shared("getItemScalarNode") @Cached GetItemScalarNode getItemScalarNode,
                        @Shared("normalizeIndexNode") @Cached NormalizeIndexCustomMessageNode normalizeIndexNode,
                        @Shared("lenNode") @Cached LenNode lenNode) {
            return getItemScalarNode.execute(storage, normalizeIndexNode.execute(idx, lenNode.execute(storage), INDEX_OUT_OF_BOUNDS));
        }

        @Specialization
        protected Object doScalarPInt(@SuppressWarnings("unused") ContainerFactory factoryMethod, SequenceStorage storage, PInt idx,
                        @Shared("getItemScalarNode") @Cached GetItemScalarNode getItemScalarNode,
                        @Shared("normalizeIndexNode") @Cached NormalizeIndexCustomMessageNode normalizeIndexNode,
                        @Shared("lenNode") @Cached LenNode lenNode) {
            return getItemScalarNode.execute(storage, normalizeIndexNode.execute(idx, lenNode.execute(storage), INDEX_OUT_OF_BOUNDS));
        }

        @Specialization(guards = "!isPSlice(idx)")
        protected Object doScalarGeneric(@SuppressWarnings("unused") ContainerFactory factoryMethod, SequenceStorage storage, Object idx,
                        @Shared("getItemScalarNode") @Cached GetItemScalarNode getItemScalarNode,
                        @Shared("normalizeIndexNode") @Cached NormalizeIndexCustomMessageNode normalizeIndexNode,
                        @Shared("lenNode") @Cached LenNode lenNode) {
            return getItemScalarNode.execute(storage, normalizeIndexNode.execute(idx, lenNode.execute(storage), INDEX_OUT_OF_BOUNDS));
        }

        @Specialization
        protected Object doSlice(ContainerFactory factoryMethod, SequenceStorage storage, PSlice slice,
                        @Cached GetItemSliceNode getItemSliceNode,
                        @Cached PythonObjectFactory factory) {
            SliceInfo info = slice.computeIndices(storage.length());
            if (factoryMethod != null) {
                return factoryMethod.apply(getItemSliceNode.execute(storage, info.start, info.stop, info.step, info.length), factory);
            }
            CompilerDirectives.transferToInterpreterAndInvalidate();
            throw new IllegalStateException();
        }

        public static GetItemDynamicNode create() {
            return GetItemDynamicNodeGen.create();
        }

        public static GetItemDynamicNode getUncached() {
            return GetItemDynamicNodeGen.getUncached();
        }
    }

    @GenerateUncached
    @ImportStatic(SequenceStorageBaseNode.class)
    public abstract static class GetItemScalarNode extends Node {

        public abstract Object execute(SequenceStorage s, int idx);

        public static GetItemScalarNode create() {
            return GetItemScalarNodeGen.create();
        }

        public static GetItemScalarNode getUncached() {
            return GetItemScalarNodeGen.getUncached();
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

        @Specialization
        protected Object doMro(MroSequenceStorage storage, int idx) {
            return storage.getItemNormalized(idx);
        }

        @Specialization(guards = "isObject(getElementType, storage)", limit = "1")
        protected Object doNativeObject(NativeSequenceStorage storage, int idx,
                        @CachedLibrary("storage.getPtr()") InteropLibrary lib,
                        @Shared("verifyNativeItemNode") @Cached VerifyNativeItemNode verifyNativeItemNode,
                        @Shared("getElementType") @Cached @SuppressWarnings("unused") GetElementType getElementType,
                        @Cached CExtNodes.ToJavaNode toJavaNode,
                        @Cached BranchProfile errorProfile,
                        @Cached PRaiseNode raiseNode) {
            try {
                return verifyResult(verifyNativeItemNode, raiseNode, storage, toJavaNode.execute(lib.readArrayElement(storage.getPtr(), idx)));
            } catch (UnsupportedMessageException | InvalidArrayIndexException e) {
                // The 'InvalidArrayIndexExceptione' should really not happen since we did a bounds
                // check before.
                errorProfile.enter();
                throw raiseNode.raise(PythonBuiltinClassType.SystemError, e);
            }
        }

        @Specialization(guards = "isByteStorage(storage)", limit = "1")
        protected int doNativeByte(NativeSequenceStorage storage, int idx,
                        @CachedLibrary("storage.getPtr()") InteropLibrary lib,
                        @Shared("verifyNativeItemNode") @Cached VerifyNativeItemNode verifyNativeItemNode,
                        @Shared("getElementType") @Cached GetElementType getElementType,
                        @Cached BranchProfile errorProfile,
                        @Cached PRaiseNode raiseNode) {
            Object result = doNative(storage, idx, lib, verifyNativeItemNode, getElementType, errorProfile, raiseNode);
            return (byte) result & 0xFF;
        }

        @Specialization(guards = {"!isByteStorage(storage)", "!isObject(getElementType, storage)"}, limit = "1")
        protected Object doNative(NativeSequenceStorage storage, int idx,
                        @CachedLibrary("storage.getPtr()") InteropLibrary lib,
                        @Shared("verifyNativeItemNode") @Cached VerifyNativeItemNode verifyNativeItemNode,
                        @Shared("getElementType") @Cached @SuppressWarnings("unused") GetElementType getElementType,
                        @Cached BranchProfile errorProfile,
                        @Cached PRaiseNode raiseNode) {
            try {
                return verifyResult(verifyNativeItemNode, raiseNode, storage, lib.readArrayElement(storage.getPtr(), idx));
            } catch (UnsupportedMessageException | InvalidArrayIndexException e) {
                // The 'InvalidArrayIndexExceptione' should really not happen since we did a bounds
                // check before.
                errorProfile.enter();
                throw raiseNode.raise(PythonBuiltinClassType.SystemError, e);
            }
        }

        private static Object verifyResult(VerifyNativeItemNode verifyNativeItemNode, PRaiseNode raiseNode, NativeSequenceStorage storage, Object item) {
            if (verifyNativeItemNode.execute(storage.getElementType(), item)) {
                return item;
            }
            throw raiseNode.raise(SystemError, "Invalid item type %s returned from native sequence storage (expected: %s)", item, storage.getElementType());
        }
    }

    @GenerateUncached
    @ImportStatic({ListStorageType.class, SequenceStorageBaseNode.class})
    abstract static class GetItemSliceNode extends Node {

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

        @Specialization(guards = "isByte(storage.getElementType())")
        protected NativeSequenceStorage doNativeByte(NativeSequenceStorage storage, int start, @SuppressWarnings("unused") int stop, int step, int length,
                        @Cached PRaiseNode raise,
                        @Cached("create()") StorageToNativeNode storageToNativeNode,
                        @Shared("lib") @CachedLibrary(limit = "1") InteropLibrary lib) {
            byte[] newArray = new byte[length];
            for (int i = start, j = 0; j < length; i += step, j++) {
                newArray[j] = (byte) readNativeElement(lib, storage.getPtr(), i, raise);
            }
            return storageToNativeNode.execute(newArray);
        }

        @Specialization(guards = "isInt(storage.getElementType())")
        protected NativeSequenceStorage doNativeInt(NativeSequenceStorage storage, int start, @SuppressWarnings("unused") int stop, int step, int length,
                        @Cached PRaiseNode raise,
                        @Cached("create()") StorageToNativeNode storageToNativeNode,
                        @Shared("lib") @CachedLibrary(limit = "1") InteropLibrary lib) {
            int[] newArray = new int[length];
            for (int i = start, j = 0; j < length; i += step, j++) {
                newArray[j] = (int) readNativeElement(lib, storage.getPtr(), i, raise);
            }
            return storageToNativeNode.execute(newArray);
        }

        @Specialization(guards = "isLong(storage.getElementType())")
        protected NativeSequenceStorage doNativeLong(NativeSequenceStorage storage, int start, @SuppressWarnings("unused") int stop, int step, int length,
                        @Cached PRaiseNode raise,
                        @Cached("create()") StorageToNativeNode storageToNativeNode,
                        @Shared("lib") @CachedLibrary(limit = "1") InteropLibrary lib) {
            long[] newArray = new long[length];
            for (int i = start, j = 0; j < length; i += step, j++) {
                newArray[j] = (long) readNativeElement(lib, storage.getPtr(), i, raise);
            }
            return storageToNativeNode.execute(newArray);
        }

        @Specialization(guards = "isDouble(storage.getElementType())")
        protected NativeSequenceStorage doNativeDouble(NativeSequenceStorage storage, int start, @SuppressWarnings("unused") int stop, int step, int length,
                        @Cached PRaiseNode raise,
                        @Cached("create()") StorageToNativeNode storageToNativeNode,
                        @Shared("lib") @CachedLibrary(limit = "1") InteropLibrary lib) {
            double[] newArray = new double[length];
            for (int i = start, j = 0; j < length; i += step, j++) {
                newArray[j] = (double) readNativeElement(lib, storage.getPtr(), i, raise);
            }
            return storageToNativeNode.execute(newArray);
        }

        @Specialization(guards = "isObject(storage.getElementType())")
        protected NativeSequenceStorage doNativeObject(NativeSequenceStorage storage, int start, @SuppressWarnings("unused") int stop, int step, int length,
                        @Cached PRaiseNode raise,
                        @Cached("create()") StorageToNativeNode storageToNativeNode,
                        @Shared("lib") @CachedLibrary(limit = "1") InteropLibrary lib) {
            Object[] newArray = new Object[length];
            for (int i = start, j = 0; j < length; i += step, j++) {
                newArray[j] = readNativeElement(lib, storage.getPtr(), i, raise);
            }
            return storageToNativeNode.execute(newArray);
        }

        private static Object readNativeElement(InteropLibrary lib, Object ptr, int idx, PRaiseNode raise) {
            try {
                return lib.readArrayElement(ptr, idx);
            } catch (UnsupportedMessageException | InvalidArrayIndexException e) {
                throw raise.raise(PythonBuiltinClassType.SystemError, e);
            }
        }

        public static GetItemSliceNode create() {
            return GetItemSliceNodeGen.create();
        }

        public static GetItemSliceNode getUncached() {
            return GetItemSliceNodeGen.getUncached();
        }
    }

    @GenerateUncached
    @ImportStatic(PGuards.class)
    public abstract static class SetItemDynamicNode extends Node {

        public abstract SequenceStorage execute(GenNodeSupplier generalizationNodeProvider, SequenceStorage s, Object key, Object value);

        @Specialization
        protected static SequenceStorage doScalarInt(GenNodeSupplier generalizationNodeProvider, SequenceStorage storage, int idx, Object value,
                        @Shared("generalizeProfile") @Cached BranchProfile generalizeProfile,
                        @Shared("setItemScalarNode") @Cached SetItemScalarNode setItemScalarNode,
                        @Shared("doGenNode") @Cached DoGeneralizationNode doGenNode,
                        @Shared("normalizeNode") @Cached NormalizeIndexCustomMessageNode normalizeNode,
                        @Shared("lenNode") @Cached LenNode lenNode) {
            int normalized = normalizeNode.execute(idx, lenNode.execute(storage), INDEX_OUT_OF_BOUNDS);
            try {
                setItemScalarNode.execute(storage, normalized, value);
                return storage;
            } catch (SequenceStoreException e) {
                generalizeProfile.enter();
                SequenceStorage generalized = doGenNode.execute(generalizationNodeProvider, storage, e.getIndicationValue());
                try {
                    setItemScalarNode.execute(generalized, normalized, value);
                } catch (SequenceStoreException e1) {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    throw new IllegalStateException();
                }
                return generalized;
            }
        }

        @Specialization
        protected static SequenceStorage doScalarLong(GenNodeSupplier generalizationNodeProvider, SequenceStorage storage, long idx, Object value,
                        @Shared("generalizeProfile") @Cached BranchProfile generalizeProfile,
                        @Shared("setItemScalarNode") @Cached SetItemScalarNode setItemScalarNode,
                        @Shared("doGenNode") @Cached DoGeneralizationNode doGenNode,
                        @Shared("normalizeNode") @Cached NormalizeIndexCustomMessageNode normalizeNode,
                        @Shared("lenNode") @Cached LenNode lenNode) {
            int normalized = normalizeNode.execute(idx, lenNode.execute(storage), INDEX_OUT_OF_BOUNDS);
            try {
                setItemScalarNode.execute(storage, normalized, value);
                return storage;
            } catch (SequenceStoreException e) {
                generalizeProfile.enter();
                SequenceStorage generalized = doGenNode.execute(generalizationNodeProvider, storage, e.getIndicationValue());
                setItemScalarNode.execute(generalized, normalized, value);
                return generalized;
            }
        }

        @Specialization
        protected static SequenceStorage doScalarPInt(GenNodeSupplier generalizationNodeProvider, SequenceStorage storage, PInt idx, Object value,
                        @Shared("generalizeProfile") @Cached BranchProfile generalizeProfile,
                        @Shared("setItemScalarNode") @Cached SetItemScalarNode setItemScalarNode,
                        @Shared("doGenNode") @Cached DoGeneralizationNode doGenNode,
                        @Shared("normalizeNode") @Cached NormalizeIndexCustomMessageNode normalizeNode,
                        @Shared("lenNode") @Cached LenNode lenNode) {
            int normalized = normalizeNode.execute(idx, lenNode.execute(storage), INDEX_OUT_OF_BOUNDS);
            try {
                setItemScalarNode.execute(storage, normalized, value);
                return storage;
            } catch (SequenceStoreException e) {
                generalizeProfile.enter();
                SequenceStorage generalized = doGenNode.execute(generalizationNodeProvider, storage, e.getIndicationValue());
                setItemScalarNode.execute(generalized, normalized, value);
                return generalized;
            }
        }

        @Specialization(guards = "!isPSlice(idx)")
        protected static SequenceStorage doScalarGeneric(GenNodeSupplier generalizationNodeProvider, SequenceStorage storage, Object idx, Object value,
                        @Shared("generalizeProfile") @Cached BranchProfile generalizeProfile,
                        @Shared("setItemScalarNode") @Cached SetItemScalarNode setItemScalarNode,
                        @Shared("doGenNode") @Cached DoGeneralizationNode doGenNode,
                        @Shared("normalizeNode") @Cached NormalizeIndexCustomMessageNode normalizeNode,
                        @Shared("lenNode") @Cached LenNode lenNode) {
            int normalized = normalizeNode.execute(idx, lenNode.execute(storage), INDEX_OUT_OF_BOUNDS);
            try {
                setItemScalarNode.execute(storage, normalized, value);
                return storage;
            } catch (SequenceStoreException e) {
                generalizeProfile.enter();
                SequenceStorage generalized = doGenNode.execute(generalizationNodeProvider, storage, e.getIndicationValue());
                setItemScalarNode.execute(generalized, normalized, value);
                return generalized;
            }
        }

        @Specialization
        protected static SequenceStorage doSlice(GenNodeSupplier generalizationNodeProvider, SequenceStorage storage, PSlice slice, Object iterable,
                        @Shared("generalizeProfile") @Cached BranchProfile generalizeProfile,
                        @Cached SetItemSliceNode setItemSliceNode,
                        @Shared("doGenNode") @Cached DoGeneralizationNode doGenNode,
                        @Cached ListNodes.ConstructListNode constructListNode) {
            SliceInfo info = slice.computeIndices(storage.length());
            // We need to construct the list eagerly because if a SequenceStoreException occurs, we
            // must not use iterable again. It could have sice-effects.
            PList values = constructListNode.execute(iterable);
            try {
                setItemSliceNode.execute(storage, info, values);
                return storage;
            } catch (SequenceStoreException e) {
                generalizeProfile.enter();
                SequenceStorage generalized = doGenNode.execute(generalizationNodeProvider, storage, e.getIndicationValue());
                setItemSliceNode.execute(generalized, info, values);
                return generalized;
            }
        }

        public static SetItemDynamicNode create() {
            return SetItemDynamicNodeGen.create();
        }

        public static SetItemDynamicNode getUncached() {
            return SetItemDynamicNodeGen.getUncached();
        }
    }

    @GenerateUncached
    abstract static class DoGeneralizationNode extends Node {

        public abstract SequenceStorage execute(GenNodeSupplier supplier, SequenceStorage storage, Object value);

        @Specialization(guards = "supplier == cachedSupplier")
        static SequenceStorage doCached(@SuppressWarnings("unused") GenNodeSupplier supplier, SequenceStorage storage, Object value,
                        @Cached("supplier") @SuppressWarnings("unused") GenNodeSupplier cachedSupplier,
                        @Cached(value = "supplier.create()", uncached = "supplier.getUncached()") GeneralizationNode genNode) {

            return genNode.execute(storage, value);
        }

        @Specialization(replaces = "doCached")
        static SequenceStorage doUncached(GenNodeSupplier supplier, SequenceStorage storage, Object value) {
            return supplier.getUncached().execute(storage, value);
        }

        public static DoGeneralizationNode create() {
            return DoGeneralizationNodeGen.create();
        }

        public static DoGeneralizationNode getUncached() {
            return DoGeneralizationNodeGen.getUncached();
        }
    }

    public abstract static class SetItemNode extends NormalizingNode {
        @Child private GeneralizationNode generalizationNode;

        private final Supplier<GeneralizationNode> generalizationNodeProvider;

        public SetItemNode(NormalizeIndexNode normalizeIndexNode, Supplier<GeneralizationNode> generalizationNodeProvider) {
            super(normalizeIndexNode);
            this.generalizationNodeProvider = generalizationNodeProvider;
        }

        public abstract SequenceStorage execute(VirtualFrame frame, SequenceStorage s, Object key, Object value);

        public abstract SequenceStorage executeInt(VirtualFrame frame, SequenceStorage s, int key, Object value);

        public abstract SequenceStorage executeLong(VirtualFrame frame, SequenceStorage s, long key, Object value);

        @Specialization
        protected SequenceStorage doScalarInt(VirtualFrame frame, SequenceStorage storage, int idx, Object value,
                        @Shared("generalizeProfile") @Cached BranchProfile generalizeProfile,
                        @Shared("setItemScalarNode") @Cached SetItemScalarNode setItemScalarNode) {
            int normalized = normalizeIndex(frame, idx, storage);
            try {
                setItemScalarNode.execute(storage, normalized, value);
                return storage;
            } catch (SequenceStoreException e) {
                generalizeProfile.enter();
                SequenceStorage generalized = generalizeStore(storage, e.getIndicationValue());
                try {
                    setItemScalarNode.execute(generalized, normalized, value);
                } catch (SequenceStoreException e1) {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    throw new IllegalStateException();
                }
                return generalized;
            }
        }

        @Specialization
        protected SequenceStorage doScalarLong(VirtualFrame frame, SequenceStorage storage, long idx, Object value,
                        @Shared("generalizeProfile") @Cached BranchProfile generalizeProfile,
                        @Shared("setItemScalarNode") @Cached SetItemScalarNode setItemScalarNode) {
            int normalized = normalizeIndex(frame, idx, storage);
            try {
                setItemScalarNode.execute(storage, normalized, value);
                return storage;
            } catch (SequenceStoreException e) {
                generalizeProfile.enter();
                SequenceStorage generalized = generalizeStore(storage, e.getIndicationValue());
                setItemScalarNode.execute(generalized, normalized, value);
                return generalized;
            }
        }

        @Specialization
        protected SequenceStorage doScalarPInt(VirtualFrame frame, SequenceStorage storage, PInt idx, Object value,
                        @Shared("generalizeProfile") @Cached BranchProfile generalizeProfile,
                        @Shared("setItemScalarNode") @Cached SetItemScalarNode setItemScalarNode) {
            int normalized = normalizeIndex(frame, idx, storage);
            try {
                setItemScalarNode.execute(storage, normalized, value);
                return storage;
            } catch (SequenceStoreException e) {
                generalizeProfile.enter();
                SequenceStorage generalized = generalizeStore(storage, e.getIndicationValue());
                setItemScalarNode.execute(generalized, normalized, value);
                return generalized;
            }
        }

        @Specialization(guards = "!isPSlice(idx)")
        protected SequenceStorage doScalarGeneric(VirtualFrame frame, SequenceStorage storage, Object idx, Object value,
                        @Shared("generalizeProfile") @Cached BranchProfile generalizeProfile,
                        @Shared("setItemScalarNode") @Cached SetItemScalarNode setItemScalarNode) {
            int normalized = normalizeIndex(frame, idx, storage);
            try {
                setItemScalarNode.execute(storage, normalized, value);
                return storage;
            } catch (SequenceStoreException e) {
                generalizeProfile.enter();
                SequenceStorage generalized = generalizeStore(storage, e.getIndicationValue());
                setItemScalarNode.execute(generalized, normalized, value);
                return generalized;
            }
        }

        @Specialization
        protected SequenceStorage doSliceSequence(SequenceStorage storage, PSlice slice, PSequence sequence,
                        @Shared("generalizeProfile") @Cached BranchProfile generalizeProfile,
                        @Cached SetItemSliceNode setItemSliceNode,
                        @Cached LenNode lenNode) {
            SliceInfo info = slice.computeIndices(lenNode.execute(storage));
            try {
                setItemSliceNode.execute(storage, info, sequence);
                return storage;
            } catch (SequenceStoreException e) {
                generalizeProfile.enter();
                SequenceStorage generalized = generalizeStore(storage, e.getIndicationValue());
                setItemSliceNode.execute(generalized, info, sequence);
                return generalized;
            }
        }

        @Specialization(replaces = "doSliceSequence")
        protected SequenceStorage doSliceGeneric(SequenceStorage storage, PSlice slice, Object iterable,
                        @Shared("generalizeProfile") @Cached BranchProfile generalizeProfile,
                        @Cached SetItemSliceNode setItemSliceNode,
                        @Cached ListNodes.ConstructListNode constructListNode) {
            SliceInfo info = slice.computeIndices(storage.length());

            // We need to construct the list eagerly because if a SequenceStoreException occurs, we
            // must not use iterable again. It could have sice-effects.
            PList values = constructListNode.execute(iterable);
            try {
                setItemSliceNode.execute(storage, info, values);
                return storage;
            } catch (SequenceStoreException e) {
                generalizeProfile.enter();
                SequenceStorage generalized = generalizeStore(storage, e.getIndicationValue());
                setItemSliceNode.execute(generalized, info, values);
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

        public static SetItemNode create(NormalizeIndexNode normalizeIndexNode, Supplier<GeneralizationNode> generalizationNodeProvider) {
            return SetItemNodeGen.create(normalizeIndexNode, generalizationNodeProvider);
        }

        public static SetItemNode create(NormalizeIndexNode normalizeIndexNode, String invalidItemErrorMessage) {
            return SetItemNodeGen.create(normalizeIndexNode, () -> NoGeneralizationCustomMessageNode.create(invalidItemErrorMessage));
        }

        public static SetItemNode create(String invalidItemErrorMessage) {
            return SetItemNodeGen.create(NormalizeIndexNode.create(), () -> NoGeneralizationCustomMessageNode.create(invalidItemErrorMessage));
        }

    }

    @GenerateUncached
    @ImportStatic(SequenceStorageBaseNode.class)
    public abstract static class SetItemScalarNode extends Node {

        public abstract void execute(SequenceStorage s, int idx, Object value);

        @Specialization
        protected void doBoolean(BoolSequenceStorage storage, int idx, boolean value) {
            storage.setBoolItemNormalized(idx, value);
        }

        @Specialization
        protected void doByte(ByteSequenceStorage storage, int idx, Object value,
                        @Shared("castToByteNode") @Cached CastToByteNode castToByteNode) {
            // TODO: clean this up, we really might need a frame
            storage.setByteItemNormalized(idx, castToByteNode.execute(null, value));
        }

        @Specialization
        protected void doChar(CharSequenceStorage storage, int idx, char value) {
            storage.setCharItemNormalized(idx, value);
        }

        @Specialization
        protected void doInt(IntSequenceStorage storage, int idx, int value) {
            storage.setIntItemNormalized(idx, value);
        }

        @Specialization(guards = "!value.isNative()")
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
        protected void doLong(LongSequenceStorage storage, int idx, int value) {
            storage.setLongItemNormalized(idx, value);
        }

        @Specialization(guards = "!value.isNative()")
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
        protected void doNativeByte(NativeSequenceStorage storage, int idx, Object value,
                        @Shared("raiseNode") @Cached PRaiseNode raiseNode,
                        @Shared("lib") @CachedLibrary(limit = "1") InteropLibrary lib,
                        @Shared("castToByteNode") @Cached CastToByteNode castToByteNode) {
            try {
                lib.writeArrayElement(storage.getPtr(), idx, castToByteNode.execute(null, value));
            } catch (UnsupportedMessageException | UnsupportedTypeException | InvalidArrayIndexException e) {
                throw raiseNode.raise(SystemError, e);
            }
        }

        @Specialization
        protected void doNative(NativeSequenceStorage storage, int idx, Object value,
                        @Shared("raiseNode") @Cached PRaiseNode raiseNode,
                        @Shared("lib") @CachedLibrary(limit = "1") InteropLibrary lib,
                        @Cached VerifyNativeItemNode verifyNativeItemNode) {
            try {
                lib.writeArrayElement(storage.getPtr(), idx, verifyValue(storage, value, verifyNativeItemNode));
            } catch (UnsupportedMessageException | UnsupportedTypeException | InvalidArrayIndexException e) {
                throw raiseNode.raise(SystemError, e);
            }
        }

        @Fallback
        @SuppressWarnings("unused")
        void doError(SequenceStorage s, int idx, Object item) {
            throw new SequenceStoreException(item);
        }

        private static Object verifyValue(NativeSequenceStorage storage, Object item, VerifyNativeItemNode verifyNativeItemNode) {
            if (verifyNativeItemNode.execute(storage.getElementType(), item)) {
                return item;
            }
            throw new SequenceStoreException(item);
        }

        public static SetItemScalarNode create() {
            return SetItemScalarNodeGen.create();
        }
    }

    @GenerateUncached
    @ImportStatic({ListStorageType.class, SequenceStorageBaseNode.class})
    public abstract static class SetItemSliceNode extends Node {

        public abstract void execute(SequenceStorage s, SliceInfo info, Object iterable);

        @Specialization(guards = "hasStorage(seq)")
        void doStorage(SequenceStorage s, SliceInfo info, PSequence seq,
                        @Shared("setStorageSliceNode") @Cached SetStorageSliceNode setStorageSliceNode,
                        @Cached GetSequenceStorageNode getSequenceStorageNode) {
            setStorageSliceNode.execute(s, info, getSequenceStorageNode.execute(seq));
        }

        @Specialization
        void doGeneric(SequenceStorage s, SliceInfo info, Object iterable,
                        @Shared("setStorageSliceNode") @Cached SetStorageSliceNode setStorageSliceNode,
                        @Cached ListNodes.ConstructListNode constructListNode) {
            PList list = constructListNode.execute(iterable);
            setStorageSliceNode.execute(s, info, list.getSequenceStorage());
        }

        public static SetItemSliceNode create() {
            return SetItemSliceNodeGen.create();
        }

        public static SetItemSliceNode getUncached() {
            return SetItemSliceNodeGen.getUncached();
        }
    }

    @GenerateUncached
    @ImportStatic(SequenceStorageBaseNode.class)
    abstract static class SetStorageSliceNode extends Node {

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

        @Specialization(guards = {"isDataTypeCompatibleNode.execute(store, sequence)"})
        void setSlice(SequenceStorage store, SliceInfo sinfo, SequenceStorage sequence,
                        @Cached("createBinaryProfile()") ConditionProfile wrongLength,
                        @Cached("createBinaryProfile()") ConditionProfile clearProfile,
                        @Cached LenNode selfLenNode,
                        @Cached LenNode otherLenNode,
                        @Cached SetLenNode setLenNode,
                        @Cached SetItemScalarNode setLeftItemNode,
                        @Cached GetItemScalarNode getRightItemNode,
                        @Cached PRaiseNode raiseNode,
                        @Cached EnsureCapacityNode ensureCapacity,
                        @Cached CopyNode copyNode,
                        @Cached CopyItemNode copyItemNode,
                        @Cached @SuppressWarnings("unused") IsDataTypeCompatibleNode isDataTypeCompatibleNode) {
            int length = selfLenNode.execute(store);
            int valueLen = otherLenNode.execute(sequence);
            if (wrongLength.profile(sinfo.step != 1 && sinfo.length != valueLen)) {
                throw raiseNode.raise(ValueError, "attempt to assign sequence of size %d to extended slice of size %d", valueLen, length);
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
            ensureCapacity.execute(store, length + delta);
            // we need to work with the copy in the case if a[i:j] = a
            SequenceStorage workingValue = store == sequence ? copyNode.execute(store) : sequence;

            if (step == 1) {
                if (delta < 0) {
                    // delete items
                    for (index = stop + delta; index < length + delta; index++) {
                        copyItemNode.execute(store, index, index - delta);
                    }
                    length += delta;
                    stop += delta;
                } else if (delta > 0) {
                    // insert items
                    for (index = length - 1; index >= stop; index--) {
                        copyItemNode.execute(store, index + delta, index);
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

        @Specialization(guards = "!isAssignCompatibleNode.execute(self, sequence)")
        void doError(@SuppressWarnings("unused") SequenceStorage self, @SuppressWarnings("unused") SliceInfo info, SequenceStorage sequence,
                        @Cached @SuppressWarnings("unused") IsAssignCompatibleNode isAssignCompatibleNode) {
            throw new SequenceStoreException(sequence.getIndicativeValue());
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

        public static SetStorageSliceNode getUncached() {
            return SetStorageSliceNodeGen.getUncached();
        }
    }

    @GenerateUncached
    abstract static class VerifyNativeItemNode extends Node {

        public abstract boolean execute(ListStorageType expectedType, Object item);

        @Specialization(guards = "elementType == cachedElementType", limit = "1")
        boolean doCached(@SuppressWarnings("unused") ListStorageType elementType, Object item,
                        @Cached("elementType") ListStorageType cachedElementType,
                        @Shared("profile") @Cached("createBinaryProfile()") ConditionProfile profile) {
            return doGeneric(cachedElementType, item, profile);
        }

        @Specialization(replaces = "doCached")
        boolean doGeneric(ListStorageType expectedType, Object item,
                        @Shared("profile") @Cached("createBinaryProfile()") ConditionProfile profile) {
            boolean res = false;
            switch (expectedType) {
                case Byte:
                    res = item instanceof Byte;
                    break;
                case Int:
                    res = item instanceof Integer;
                    break;
                case Long:
                    res = item instanceof Long;
                    break;
                case Double:
                    res = item instanceof Double;
                    break;
                case Generic:
                    res = !(item instanceof Byte || item instanceof Integer || item instanceof Long || item instanceof Double);
                    break;
            }
            return profile.profile(res);
        }

        public static VerifyNativeItemNode create() {
            return VerifyNativeItemNodeGen.create();
        }

        public static VerifyNativeItemNode getUncached() {
            return VerifyNativeItemNodeGen.getUncached();
        }
    }

    @ImportStatic(NativeCAPISymbols.class)
    @GenerateUncached
    public abstract static class StorageToNativeNode extends Node {

        public abstract NativeSequenceStorage execute(Object obj);

        @Specialization
        NativeSequenceStorage doByte(byte[] arr,
                        @Exclusive @Cached PCallCapiFunction callNode,
                        @Shared("context") @CachedContext(PythonLanguage.class) PythonContext context) {
            return new NativeSequenceStorage(callNode.call(FUN_PY_TRUFFLE_BYTE_ARRAY_TO_NATIVE, wrap(context, arr), arr.length), arr.length, arr.length, ListStorageType.Byte);
        }

        @Specialization
        NativeSequenceStorage doInt(int[] arr,
                        @Exclusive @Cached PCallCapiFunction callNode,
                        @Shared("context") @CachedContext(PythonLanguage.class) PythonContext context) {
            return new NativeSequenceStorage(callNode.call(FUN_PY_TRUFFLE_INT_ARRAY_TO_NATIVE, wrap(context, arr), arr.length), arr.length, arr.length, ListStorageType.Int);
        }

        @Specialization
        NativeSequenceStorage doLong(long[] arr,
                        @Exclusive @Cached PCallCapiFunction callNode,
                        @Shared("context") @CachedContext(PythonLanguage.class) PythonContext context) {
            return new NativeSequenceStorage(callNode.call(FUN_PY_TRUFFLE_LONG_ARRAY_TO_NATIVE, wrap(context, arr), arr.length), arr.length, arr.length, ListStorageType.Long);
        }

        @Specialization
        NativeSequenceStorage doDouble(double[] arr,
                        @Exclusive @Cached PCallCapiFunction callNode,
                        @Shared("context") @CachedContext(PythonLanguage.class) PythonContext context) {
            return new NativeSequenceStorage(callNode.call(FUN_PY_TRUFFLE_DOUBLE_ARRAY_TO_NATIVE, wrap(context, arr), arr.length), arr.length, arr.length, ListStorageType.Double);
        }

        @Specialization
        NativeSequenceStorage doObject(Object[] arr,
                        @Exclusive @Cached PCallCapiFunction callNode,
                        @Exclusive @Cached ToSulongNode toSulongNode,
                        @Shared("context") @CachedContext(PythonLanguage.class) PythonContext context) {
            Object[] wrappedValues = new Object[arr.length];
            for (int i = 0; i < wrappedValues.length; i++) {
                wrappedValues[i] = toSulongNode.execute(arr[i]);
            }
            return new NativeSequenceStorage(callNode.call(FUN_PY_TRUFFLE_OBJECT_ARRAY_TO_NATIVE, wrap(context, wrappedValues), wrappedValues.length), wrappedValues.length, wrappedValues.length,
                            ListStorageType.Generic);
        }

        private static Object wrap(PythonContext context, Object arr) {
            return context.getEnv().asGuestValue(arr);
        }

        public static StorageToNativeNode create() {
            return StorageToNativeNodeGen.create();
        }

        public static StorageToNativeNode getUncached() {
            return StorageToNativeNodeGen.getUncached();
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
            return java.lang.Double.compare(l, r) == 0;
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
        @Child private BinaryComparisonNode comparisonNode;
        @Child private CoerceToBooleanNode castToBooleanNode;

        @Child private LenNode lenNode;

        private final BinCmpOp cmpOp;

        protected CmpNode(BinCmpOp cmpOp) {
            this.cmpOp = cmpOp;
        }

        public abstract boolean execute(VirtualFrame frame, SequenceStorage left, SequenceStorage right);

        protected boolean isEmpty(SequenceStorage left) {
            return getLenNode().execute(left) == 0;
        }

        private LenNode getLenNode() {
            if (lenNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                lenNode = insert(LenNode.create());
            }
            return lenNode;
        }

        @SuppressWarnings("unused")
        @Specialization(guards = {"isEmpty(left)", "isEmpty(right)"})
        boolean doEmpty(SequenceStorage left, SequenceStorage right) {
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
                if (java.lang.Double.compare(litem, ritem) != 0) {
                    return cmpOp.cmp(litem, ritem);
                }
            }
            return cmpOp.cmp(llen, rlen);
        }

        @Specialization
        boolean doGeneric(VirtualFrame frame, SequenceStorage left, SequenceStorage right,
                        @Cached ConditionProfile hasFrame,
                        @CachedLibrary(limit = "getCallSiteInlineCacheMaxDepth()") PythonObjectLibrary lib) {
            int llen = getLenNode().execute(left);
            int rlen = getLenNode().execute(right);
            ThreadState state;
            if (hasFrame.profile(frame != null)) {
                state = PArguments.getThreadState(frame);
            } else {
                state = null;
            }
            for (int i = 0; i < Math.min(llen, rlen); i++) {
                Object leftItem = getGetItemNode().execute(left, i);
                Object rightItem = getGetRightItemNode().execute(right, i);
                boolean isEqual;
                if (hasFrame.profile(state != null)) {
                    isEqual = lib.equalsWithState(leftItem, rightItem, lib, state);
                } else {
                    isEqual = lib.equals(leftItem, rightItem, lib);
                }
                if (!isEqual) {
                    return cmpGeneric(frame, leftItem, rightItem);
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

        private boolean cmpGeneric(VirtualFrame frame, Object left, Object right) {
            if (comparisonNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                comparisonNode = insert(cmpOp.createBinaryComparisonNode());
            }
            return castToBoolean(frame, comparisonNode.executeWith(frame, left, right));
        }

        private boolean castToBoolean(VirtualFrame frame, Object value) {
            if (castToBooleanNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                castToBooleanNode = insert(CoerceToBooleanNode.createIfTrueNode());
            }
            return castToBooleanNode.executeBoolean(frame, value);
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

    /**
     * Use this node to get the internal byte array of the storage (if possible). It will avoid
     * copying any data but this also means that the returned byte array may be larger than the
     * number of actual elements. So, you must also consider the sequence storage's size.
     */
    @GenerateUncached
    @ImportStatic(SequenceStorageBaseNode.class)
    public abstract static class GetInternalByteArrayNode extends PNodeWithContext {

        public abstract byte[] execute(SequenceStorage s);

        @Specialization
        static byte[] doByteSequenceStorage(ByteSequenceStorage s) {
            return s.getInternalByteArray();
        }

        @Specialization(guards = "isByteStorage(s)")
        static byte[] doNativeByte(NativeSequenceStorage s,
                        @Shared("getItemNode") @Cached GetItemScalarNode getItemNode) {
            byte[] barr = new byte[s.length()];
            for (int i = 0; i < barr.length; i++) {
                int elem = getItemNode.executeInt(s, i);
                assert elem >= 0 && elem < 256;
                barr[i] = (byte) elem;
            }
            return barr;
        }

        @Specialization(guards = {"len(lenNode, s) == cachedLen", "cachedLen <= 32"}, limit = "1")
        @ExplodeLoop
        static byte[] doGenericLenCached(SequenceStorage s,
                        @Shared("getItemNode") @Cached GetItemScalarNode getItemNode,
                        @Cached CastToJavaByteNode castToByteNode,
                        @Cached @SuppressWarnings("unused") LenNode lenNode,
                        @Cached("len(lenNode, s)") int cachedLen) {
            byte[] barr = new byte[cachedLen];
            for (int i = 0; i < cachedLen; i++) {
                barr[i] = castToByteNode.execute(getItemNode.execute(s, i));
            }
            return barr;
        }

        @Specialization(replaces = "doGenericLenCached")
        static byte[] doGeneric(SequenceStorage s,
                        @Shared("getItemNode") @Cached GetItemScalarNode getItemNode,
                        @Cached CastToJavaByteNode castToByteNode,
                        @Cached LenNode lenNode) {
            byte[] barr = new byte[lenNode.execute(s)];
            for (int i = 0; i < barr.length; i++) {
                barr[i] = castToByteNode.execute(getItemNode.execute(s, i));
            }
            return barr;
        }

        protected static int len(LenNode lenNode, SequenceStorage s) {
            return lenNode.execute(s);
        }
    }

    @GenerateUncached
    public abstract static class ToByteArrayNode extends Node {

        public abstract byte[] execute(SequenceStorage s);

        @Specialization
        byte[] doByteSequenceStorage(ByteSequenceStorage s,
                        @Cached("createBinaryProfile()") ConditionProfile profile) {
            byte[] bytes = GetInternalByteArrayNode.doByteSequenceStorage(s);
            int storageLength = s.length();
            if (profile.profile(storageLength != bytes.length)) {
                return exactCopy(bytes, storageLength);
            }
            return bytes;
        }

        @Specialization(guards = "!isByteSequenceStorage(s)")
        byte[] doOther(SequenceStorage s,
                        @Cached GetInternalByteArrayNode getInternalByteArrayNode) {
            return getInternalByteArrayNode.execute(s);
        }

        private static byte[] exactCopy(byte[] barr, int len) {
            return Arrays.copyOf(barr, len);
        }

        static boolean isByteSequenceStorage(SequenceStorage s) {
            return s instanceof ByteSequenceStorage;
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
                        @Shared("raiseNode") @Cached PRaiseNode raiseNode,
                        @Shared("outOfMemProfile") @Cached BranchProfile outOfMemProfile,
                        @Shared("copyNode") @Cached CopyNode copyNode) {
            try {
                return copyNode.execute(right);
            } catch (OutOfMemoryError e) {
                outOfMemProfile.enter();
                throw raiseNode.raise(MemoryError);
            }
        }

        @Specialization(guards = "!isNative(left)")
        SequenceStorage doRightEmpty(@SuppressWarnings("unused") EmptySequenceStorage dest, SequenceStorage left, @SuppressWarnings("unused") EmptySequenceStorage right,
                        @Shared("raiseNode") @Cached PRaiseNode raiseNode,
                        @Shared("outOfMemProfile") @Cached BranchProfile outOfMemProfile,
                        @Shared("copyNode") @Cached CopyNode copyNode) {
            try {
                return copyNode.execute(left);
            } catch (OutOfMemoryError e) {
                outOfMemProfile.enter();
                throw raiseNode.raise(MemoryError);
            }
        }

        @Specialization(guards = {"dest.getClass() == left.getClass()", "left.getClass() == right.getClass()", "!isNative(dest)", "cachedClass == dest.getClass()"})
        SequenceStorage doManagedManagedSameType(SequenceStorage dest, SequenceStorage left, SequenceStorage right,
                        @Cached("left.getClass()") Class<? extends SequenceStorage> cachedClass) {
            SequenceStorage destProfiled = cachedClass.cast(dest);
            SequenceStorage leftProfiled = cachedClass.cast(left);
            SequenceStorage rightProfiled = cachedClass.cast(right);
            Object arr1 = leftProfiled.getInternalArrayObject();
            int len1 = leftProfiled.length();
            Object arr2 = rightProfiled.getInternalArrayObject();
            int len2 = rightProfiled.length();
            concat(destProfiled.getInternalArrayObject(), arr1, len1, arr2, len2);
            getSetLenNode().execute(destProfiled, len1 + len2);
            return destProfiled;
        }

        @Specialization(guards = {"dest.getClass() == right.getClass()", "!isNative(dest)", "cachedClass == dest.getClass()"})
        SequenceStorage doEmptyManagedSameType(SequenceStorage dest, @SuppressWarnings("unused") EmptySequenceStorage left, SequenceStorage right,
                        @Cached("left.getClass()") Class<? extends SequenceStorage> cachedClass) {
            SequenceStorage destProfiled = cachedClass.cast(dest);
            SequenceStorage rightProfiled = cachedClass.cast(right);
            Object arr2 = rightProfiled.getInternalArrayObject();
            int len2 = rightProfiled.length();
            PythonUtils.arraycopy(arr2, 0, destProfiled.getInternalArrayObject(), 0, len2);
            getSetLenNode().execute(destProfiled, len2);
            return destProfiled;
        }

        @Specialization(guards = {"dest.getClass() == left.getClass()", "!isNative(dest)", "cachedClass == dest.getClass()"})
        SequenceStorage doManagedEmptySameType(SequenceStorage dest, SequenceStorage left, @SuppressWarnings("unused") EmptySequenceStorage right,
                        @Cached("left.getClass()") Class<? extends SequenceStorage> cachedClass) {
            SequenceStorage destProfiled = cachedClass.cast(dest);
            SequenceStorage leftProfiled = cachedClass.cast(left);
            Object arr1 = leftProfiled.getInternalArrayObject();
            int len1 = leftProfiled.length();
            PythonUtils.arraycopy(arr1, 0, destProfiled.getInternalArrayObject(), 0, len1);
            getSetLenNode().execute(destProfiled, len1);
            return destProfiled;
        }

        @Specialization
        SequenceStorage doGeneric(SequenceStorage dest, SequenceStorage left, SequenceStorage right,
                        @Cached LenNode lenNode) {
            int len1 = lenNode.execute(left);
            int len2 = lenNode.execute(right);
            for (int i = 0; i < len1; i++) {
                getSetItemNode().execute(dest, i, getGetItemNode().execute(left, i));
            }
            for (int i = 0; i < len2; i++) {
                getSetItemNode().execute(dest, i + len1, getGetRightItemNode().execute(right, i));
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
            PythonUtils.arraycopy(arr1, 0, dest, 0, len1);
            PythonUtils.arraycopy(arr2, 0, dest, len1, len2);
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
        @Child private EnsureCapacityNode ensureCapacityNode;
        @Child private SetLenNode setLenNode;

        private final Supplier<GeneralizationNode> genNodeProvider;

        ConcatNode(Supplier<GeneralizationNode> genNodeProvider) {
            this.genNodeProvider = genNodeProvider;
        }

        public abstract SequenceStorage execute(SequenceStorage left, SequenceStorage right);

        @Specialization
        SequenceStorage doRight(SequenceStorage left, SequenceStorage right,
                        @Cached PRaiseNode raiseNode,
                        @Cached LenNode lenNode,
                        @Cached BranchProfile outOfMemProfile) {
            try {
                int len1 = lenNode.execute(left);
                int len2 = lenNode.execute(right);
                // we eagerly generalize the store to avoid possible cascading generalizations
                SequenceStorage generalized = generalizeStore(createEmpty(left, right, Math.addExact(len1, len2)), right);
                return doConcat(generalized, left, right);
            } catch (ArithmeticException | OutOfMemoryError e) {
                outOfMemProfile.enter();
                throw raiseNode.raise(MemoryError);
            }
        }

        private SequenceStorage createEmpty(SequenceStorage l, SequenceStorage r, int len) {
            if (l instanceof EmptySequenceStorage) {
                return createEmptyNode.execute(r, len, -1);
            }
            SequenceStorage empty = createEmptyNode.execute(l, len, len);
            return empty;
        }

        private SequenceStorage doConcat(SequenceStorage dest, SequenceStorage leftProfiled, SequenceStorage rightProfiled) {
            try {
                return concatBaseNode.execute(dest, leftProfiled, rightProfiled);
            } catch (SequenceStoreException e) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
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
            return ConcatNodeGen.create(() -> NoGeneralizationCustomMessageNode.create(DEFAULT_ERROR_MSG));
        }

        public static ConcatNode create(String msg) {
            return ConcatNodeGen.create(() -> NoGeneralizationCustomMessageNode.create(msg));
        }

        public static ConcatNode create(Supplier<GeneralizationNode> genNodeProvider) {
            return ConcatNodeGen.create(genNodeProvider);
        }
    }

    @ImportStatic(PGuards.class)
    public abstract static class ExtendNode extends SequenceStorageBaseNode {
        @Child private GeneralizationNode genNode;

        private final GenNodeSupplier genNodeProvider;

        public ExtendNode(GenNodeSupplier genNodeProvider) {
            this.genNodeProvider = genNodeProvider;
        }

        public abstract SequenceStorage execute(VirtualFrame frame, SequenceStorage s, Object iterable);

        @Child private GetLazyClassNode getClassNode;

        protected LazyPythonClass getClass(Object value) {
            if (getClassNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                getClassNode = insert(GetLazyClassNode.create());
            }
            return getClassNode.execute(value);
        }

        @Specialization(guards = {"hasStorage(seq)", "cannotBeOverridden(getClass(seq))"})
        SequenceStorage doWithStorage(SequenceStorage left, PSequence seq,
                        @Cached GetSequenceStorageNode getStorageNode,
                        @Cached LenNode lenNode,
                        @Cached PRaiseNode raiseNode,
                        @Cached EnsureCapacityNode ensureCapacityNode,
                        @Cached BranchProfile overflowErrorProfile,
                        @Cached ConcatBaseNode concatStoragesNode) {
            SequenceStorage right = getStorageNode.execute(seq);
            int len1 = lenNode.execute(left);
            int len2 = lenNode.execute(right);
            SequenceStorage dest = null;
            try {
                dest = ensureCapacityNode.execute(left, Math.addExact(len1, len2));
                return concatStoragesNode.execute(dest, left, right);
            } catch (SequenceStoreException e) {
                dest = generalizeStore(dest, e.getIndicationValue());
                dest = ensureCapacityNode.execute(dest, Math.addExact(len1, len2));
                return concatStoragesNode.execute(dest, left, right);
            } catch (ArithmeticException e) {
                overflowErrorProfile.enter();
                throw raiseNode.raise(PythonErrorType.OverflowError);
            }
        }

        @Specialization(guards = "!hasStorage(iterable) || !cannotBeOverridden(getClass(iterable))")
        SequenceStorage doWithoutStorage(VirtualFrame frame, SequenceStorage s, Object iterable,
                        @Cached("create()") GetIteratorNode getIteratorNode,
                        @Cached("create()") GetNextNode getNextNode,
                        @Cached("create()") IsBuiltinClassProfile errorProfile,
                        @Cached AppendNode appendNode) {
            SequenceStorage currentStore = s;
            Object it = getIteratorNode.executeWith(frame, iterable);
            while (true) {
                Object value;
                try {
                    value = getNextNode.execute(frame, it);
                    currentStore = appendNode.execute(currentStore, value, genNodeProvider);
                } catch (PException e) {
                    e.expectStopIteration(errorProfile);
                    return currentStore;
                }
            }
        }

        private SequenceStorage generalizeStore(SequenceStorage storage, Object value) {
            if (genNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                genNode = insert(genNodeProvider.create());
            }
            return genNode.execute(storage, value);
        }

        protected ExtendNode createRecursive() {
            return ExtendNodeGen.create(genNodeProvider);
        }

        public static ExtendNode create(GenNodeSupplier genNodeProvider) {
            return ExtendNodeGen.create(genNodeProvider);
        }
    }

    public abstract static class RepeatNode extends SequenceStorageBaseNode {
        private static final String ERROR_MSG = "can't multiply sequence by non-int of type '%p'";

        @Child private GetItemScalarNode getItemNode;
        @Child private RepeatNode recursive;

        public abstract SequenceStorage execute(VirtualFrame frame, SequenceStorage left, Object times);

        public abstract SequenceStorage execute(VirtualFrame frame, SequenceStorage left, int times);

        @Specialization
        SequenceStorage doEmpty(EmptySequenceStorage s, @SuppressWarnings("unused") int times) {
            return s;
        }

        @Specialization(guards = "times <= 0")
        SequenceStorage doZeroRepeat(SequenceStorage s, @SuppressWarnings("unused") int times,
                        @Cached CreateEmptyNode createEmptyNode) {
            return createEmptyNode.execute(s, 0, -1);
        }

        /* special but common case: something like '[False] * n' */
        @Specialization(guards = {"s.length() == 1", "times > 0"})
        BoolSequenceStorage doBoolSingleElement(BoolSequenceStorage s, int times,
                        @Shared("raiseNode") @Cached PRaiseNode raiseNode,
                        @Cached BranchProfile outOfMemProfile) {
            try {
                boolean[] repeated = new boolean[Math.multiplyExact(s.length(), times)];
                Arrays.fill(repeated, s.getBoolItemNormalized(0));
                return new BoolSequenceStorage(repeated);
            } catch (OutOfMemoryError | ArithmeticException e) {
                outOfMemProfile.enter();
                throw raiseNode.raise(MemoryError);
            }
        }

        /* special but common case: something like '["\x00"] * n' */
        @Specialization(guards = {"s.length() == 1", "times > 0"})
        ByteSequenceStorage doByteSingleElement(ByteSequenceStorage s, int times,
                        @Shared("raiseNode") @Cached PRaiseNode raiseNode,
                        @Cached BranchProfile outOfMemProfile) {
            try {
                byte[] repeated = new byte[Math.multiplyExact(s.length(), times)];
                Arrays.fill(repeated, s.getByteItemNormalized(0));
                return new ByteSequenceStorage(repeated);
            } catch (OutOfMemoryError | ArithmeticException e) {
                outOfMemProfile.enter();
                throw raiseNode.raise(MemoryError);
            }
        }

        /* special but common case: something like '["0"] * n' */
        @Specialization(guards = {"s.length() == 1", "times > 0"})
        CharSequenceStorage doCharSingleElement(CharSequenceStorage s, int times,
                        @Shared("raiseNode") @Cached PRaiseNode raiseNode,
                        @Cached BranchProfile outOfMemProfile) {
            try {
                char[] repeated = new char[Math.multiplyExact(s.length(), times)];
                Arrays.fill(repeated, s.getCharItemNormalized(0));
                return new CharSequenceStorage(repeated);
            } catch (OutOfMemoryError | ArithmeticException e) {
                outOfMemProfile.enter();
                throw raiseNode.raise(MemoryError);
            }
        }

        /* special but common case: something like '[0] * n' */
        @Specialization(guards = {"s.length() == 1", "times > 0"})
        IntSequenceStorage doIntSingleElement(IntSequenceStorage s, int times,
                        @Shared("raiseNode") @Cached PRaiseNode raiseNode,
                        @Cached BranchProfile outOfMemProfile) {
            try {
                int[] repeated = new int[Math.multiplyExact(s.length(), times)];
                Arrays.fill(repeated, s.getIntItemNormalized(0));
                return new IntSequenceStorage(repeated);
            } catch (OutOfMemoryError | ArithmeticException e) {
                outOfMemProfile.enter();
                throw raiseNode.raise(MemoryError);
            }
        }

        /* special but common case: something like '[0L] * n' */
        @Specialization(guards = {"s.length() == 1", "times > 0"})
        LongSequenceStorage doLongSingleElement(LongSequenceStorage s, int times,
                        @Shared("raiseNode") @Cached PRaiseNode raiseNode,
                        @Cached BranchProfile outOfMemProfile) {
            try {
                long[] repeated = new long[Math.multiplyExact(s.length(), times)];
                Arrays.fill(repeated, s.getLongItemNormalized(0));
                return new LongSequenceStorage(repeated);
            } catch (OutOfMemoryError | ArithmeticException e) {
                outOfMemProfile.enter();
                throw raiseNode.raise(MemoryError);
            }
        }

        /* special but common case: something like '[0.0] * n' */
        @Specialization(guards = {"s.length() == 1", "times > 0"})
        DoubleSequenceStorage doDoubleSingleElement(DoubleSequenceStorage s, int times,
                        @Shared("raiseNode") @Cached PRaiseNode raiseNode,
                        @Cached BranchProfile outOfMemProfile) {
            try {
                double[] repeated = new double[Math.multiplyExact(s.length(), times)];
                Arrays.fill(repeated, s.getDoubleItemNormalized(0));
                return new DoubleSequenceStorage(repeated);
            } catch (OutOfMemoryError | ArithmeticException e) {
                outOfMemProfile.enter();
                throw raiseNode.raise(MemoryError);
            }
        }

        /* special but common case: something like '[None] * n' */
        @Specialization(guards = {"s.length() == 1", "times > 0"})
        ObjectSequenceStorage doObjectSingleElement(ObjectSequenceStorage s, int times,
                        @Shared("raiseNode") @Cached PRaiseNode raiseNode,
                        @Cached BranchProfile outOfMemProfile) {
            try {
                Object[] repeated = new Object[Math.multiplyExact(s.length(), times)];
                Arrays.fill(repeated, s.getItemNormalized(0));
                return new ObjectSequenceStorage(repeated);
            } catch (OutOfMemoryError | ArithmeticException e) {
                outOfMemProfile.enter();
                throw raiseNode.raise(MemoryError);
            }
        }

        @Specialization(limit = "MAX_ARRAY_STORAGES", guards = {"times > 0", "!isNative(s)", "s.getClass() == cachedClass"})
        SequenceStorage doManaged(BasicSequenceStorage s, int times,
                        @Exclusive @Cached PRaiseNode raiseNode,
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
                throw raiseNode.raise(MemoryError);
            }
        }

        @Specialization(replaces = "doManaged", guards = "times > 0")
        SequenceStorage doGeneric(SequenceStorage s, int times,
                        @Shared("raiseNode") @Cached PRaiseNode raiseNode,
                        @Cached("create()") CreateEmptyNode createEmptyNode,
                        @Cached("create()") BranchProfile outOfMemProfile,
                        @Cached("create()") SetItemScalarNode setItemNode,
                        @Cached("create()") GetItemScalarNode getDestItemNode,
                        @Cached("create()") LenNode lenNode) {
            try {
                int len = lenNode.execute(s);
                SequenceStorage repeated = createEmptyNode.execute(s, Math.multiplyExact(len, times), -1);

                for (int i = 0; i < len; i++) {
                    setItemNode.execute(repeated, i, getGetItemNode().execute(s, i));
                }

                // read from destination since that is potentially faster
                for (int j = 1; j < times; j++) {
                    for (int i = 0; i < len; i++) {
                        setItemNode.execute(repeated, j * len + i, getDestItemNode.execute(repeated, i));
                    }
                }

                return repeated;
            } catch (OutOfMemoryError | ArithmeticException e) {
                outOfMemProfile.enter();
                throw raiseNode.raise(MemoryError);
            }
        }

        @Specialization(guards = "!isInt(times)", limit = "1")
        SequenceStorage doNonInt(VirtualFrame frame, SequenceStorage s, Object times,
                        @Shared("raiseNode") @Cached PRaiseNode raiseNode,
                        @CachedLibrary("times") PythonObjectLibrary lib) {
            int i = toIndex(frame, times, raiseNode, lib);
            if (recursive == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                recursive = insert(RepeatNodeGen.create());
            }
            return recursive.execute(frame, s, i);
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
                PythonUtils.arraycopy(src, 0, dest, i * len, len);
            }
        }

        protected static boolean isInt(Object times) {
            return times instanceof Integer;
        }

        private static int toIndex(VirtualFrame frame, Object times, PRaiseNode raiseNode, PythonObjectLibrary lib) {
            if (lib.canBeIndex(times)) {
                return lib.asSizeWithState(times, PArguments.getThreadState(frame));
            }
            throw raiseNode.raise(TypeError, ERROR_MSG, times);
        }

        public static RepeatNode create() {
            return RepeatNodeGen.create();
        }
    }

    public abstract static class ContainsNode extends SequenceStorageBaseNode {
        public abstract boolean execute(VirtualFrame frame, SequenceStorage left, Object item);

        @Specialization(guards = "lenNode.execute(left) == 0")
        @SuppressWarnings("unused")
        boolean doEmpty(SequenceStorage left, Object item,
                        @Cached LenNode lenNode) {
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
        boolean doGeneric(VirtualFrame frame, SequenceStorage left, Object item,
                        @Cached LenNode lenNode,
                        @Cached GetItemScalarNode getItemNode,
                        @CachedLibrary(limit = "getCallSiteInlineCacheMaxDepth()") PythonObjectLibrary lib) {
            ThreadState threadState = PArguments.getThreadState(frame);
            for (int i = 0; i < lenNode.execute(left); i++) {
                Object leftItem = getItemNode.execute(left, i);
                if (lib.equalsWithState(leftItem, item, lib, threadState)) {
                    return true;
                }
            }
            return false;
        }

        public static ContainsNode create() {
            return ContainsNodeGen.create();
        }
    }

    public abstract static class GeneralizationNode extends Node {
        public abstract SequenceStorage execute(SequenceStorage toGeneralize, Object indicationValue);

    }

    /**
     * Does not allow any generalization but compatible types.
     */
    @GenerateUncached
    public abstract static class NoGeneralizationNode extends GeneralizationNode {

        public static final GenNodeSupplier DEFAULT = new GenNodeSupplier() {

            public GeneralizationNode getUncached() {
                return NoGeneralizationNodeGen.getUncached();
            }

            public GeneralizationNode create() {
                return NoGeneralizationNodeGen.create();
            }
        };

        @Specialization
        protected SequenceStorage doGeneric(SequenceStorage s, Object indicationVal,
                        @Cached IsAssignCompatibleNode isAssignCompatibleNode,
                        @Cached GetElementType getElementType,
                        @Cached("createClassProfile()") ValueProfile valTypeProfile,
                        @Cached PRaiseNode raiseNode) {

            Object val = valTypeProfile.profile(indicationVal);
            if (val instanceof SequenceStorage && isAssignCompatibleNode.execute(s, (SequenceStorage) val)) {
                return s;
            }

            ListStorageType et = getElementType.execute(s);
            if (val instanceof Byte && SequenceStorageBaseNode.isByteLike(et) ||
                            val instanceof Integer && (SequenceStorageBaseNode.isInt(et) || SequenceStorageBaseNode.isLong(et)) ||
                            val instanceof Long && SequenceStorageBaseNode.isLong(et) ||
                            val instanceof PList && SequenceStorageBaseNode.isList(et) ||
                            val instanceof PTuple && SequenceStorageBaseNode.isTuple(et) || SequenceStorageBaseNode.isObject(et)) {
                return s;
            }

            throw raiseNode.raise(TypeError, getErrorMessage());
        }

        protected String getErrorMessage() {
            return "";
        }
    }

    public abstract static class NoGeneralizationCustomMessageNode extends NoGeneralizationNode {

        private final String errorMessage;

        public NoGeneralizationCustomMessageNode(String errorMessage) {
            this.errorMessage = errorMessage;
        }

        @Override
        protected final String getErrorMessage() {
            return errorMessage;
        }

        public static NoGeneralizationCustomMessageNode create(String msg) {
            return NoGeneralizationCustomMessageNodeGen.create(msg);
        }
    }

    /**
     * Implements list generalization rules; previously in 'SequenceStroage.generalizeFor'.
     */
    @GenerateUncached
    public abstract static class ListGeneralizationNode extends GeneralizationNode {

        public static final GenNodeSupplier SUPPLIER = new GenNodeSupplier() {

            public GeneralizationNode getUncached() {
                return ListGeneralizationNodeGen.getUncached();
            }

            public GeneralizationNode create() {
                return ListGeneralizationNodeGen.create();
            }
        };

        private static final int DEFAULT_CAPACITY = 8;

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
        ListSequenceStorage doEmptyPList(@SuppressWarnings("unused") EmptySequenceStorage s, @SuppressWarnings("unused") PList val) {
            return new ListSequenceStorage(0);
        }

        @Specialization
        TupleSequenceStorage doEmptyPTuple(@SuppressWarnings("unused") EmptySequenceStorage s, @SuppressWarnings("unused") PTuple val) {
            return new TupleSequenceStorage();
        }

        protected static boolean isKnownType(Object val) {
            return val instanceof Byte || val instanceof Integer || val instanceof Long || val instanceof Double || val instanceof PList || val instanceof PTuple;
        }

        @Specialization(guards = "!isKnownType(val)")
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

        @Specialization(guards = "isAssignCompatibleNode.execute(s, indicationStorage)", limit = "1")
        TypedSequenceStorage doTyped(TypedSequenceStorage s, @SuppressWarnings("unused") SequenceStorage indicationStorage,
                        @Shared("isAssignCompatibleNode") @Cached @SuppressWarnings("unused") IsAssignCompatibleNode isAssignCompatibleNode) {
            return s;
        }

        @Specialization(guards = "isFallbackCase(s, value, isAssignCompatibleNode)", limit = "1")
        ObjectSequenceStorage doTyped(SequenceStorage s, @SuppressWarnings("unused") Object value,
                        @Cached("createClassProfile()") ValueProfile selfProfile,
                        @Shared("isAssignCompatibleNode") @Cached @SuppressWarnings("unused") IsAssignCompatibleNode isAssignCompatibleNode) {
            SequenceStorage profiled = selfProfile.profile(s);
            if (profiled instanceof BasicSequenceStorage) {
                return new ObjectSequenceStorage(profiled.getInternalArray());
            }
            // TODO copy all values
            return new ObjectSequenceStorage(DEFAULT_CAPACITY);
        }

        protected static boolean isFallbackCase(SequenceStorage s, Object value, IsAssignCompatibleNode isAssignCompatibleNode) {
            // there are explicit specializations for all cases with EmptySequenceStorage
            if (s instanceof EmptySequenceStorage || s instanceof ObjectSequenceStorage) {
                return false;
            }
            if ((s instanceof ByteSequenceStorage || s instanceof IntSequenceStorage || s instanceof LongSequenceStorage) &&
                            (value instanceof Byte || value instanceof Integer || value instanceof Long)) {
                return false;
            }
            if (value instanceof SequenceStorage && isAssignCompatibleNode.execute(s, (SequenceStorage) value)) {
                return false;
            }
            return true;
        }

        public static ListGeneralizationNode create() {
            return ListGeneralizationNodeGen.create();
        }
    }

    @GenerateUncached
    @ImportStatic(SequenceStorageBaseNode.class)
    public abstract static class AppendNode extends Node {

        public abstract SequenceStorage execute(SequenceStorage s, Object val, GenNodeSupplier genNodeSupplier);

        @Specialization
        SequenceStorage doEmpty(EmptySequenceStorage s, Object val, GenNodeSupplier genNodeSupplier,
                        @Cached AppendNode recursive,
                        @Exclusive @Cached DoGeneralizationNode doGenNode) {
            SequenceStorage newStorage = doGenNode.execute(genNodeSupplier, s, val);
            return recursive.execute(newStorage, val, genNodeSupplier);
        }

        @Specialization(limit = "MAX_ARRAY_STORAGES", guards = "s.getClass() == cachedClass")
        SequenceStorage doManaged(BasicSequenceStorage s, Object val, GenNodeSupplier genNodeSupplier,
                        @Cached BranchProfile increaseCapacity,
                        @Cached EnsureCapacityNode ensureCapacity,
                        @Cached("s.getClass()") Class<? extends BasicSequenceStorage> cachedClass,
                        @Cached("create()") SetItemScalarNode setItemNode,
                        @Cached("createClassProfile()") ValueProfile generalizedProfile,
                        @Exclusive @Cached DoGeneralizationNode doGenNode) {
            BasicSequenceStorage profiled = cachedClass.cast(s);
            int len = profiled.length();
            int newLen = len + 1;
            int capacity = profiled.capacity();
            if (newLen > capacity) {
                increaseCapacity.enter();
                profiled.ensureCapacity(len + 1);
            }
            try {
                setItemNode.execute(profiled, len, val);
                profiled.setNewLength(len + 1);
                return profiled;
            } catch (SequenceStoreException e) {
                SequenceStorage generalized = generalizedProfile.profile(doGenNode.execute(genNodeSupplier, profiled, e.getIndicationValue()));
                ensureCapacity.execute(generalized, len + 1);
                try {
                    setItemNode.execute(generalized, len, val);
                    generalized.setNewLength(len + 1);
                    return generalized;
                } catch (SequenceStoreException e1) {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    throw new IllegalStateException();
                }
            }
        }

        // TODO native sequence storage

        public static AppendNode create() {
            return AppendNodeGen.create();
        }

        public static AppendNode getUncached() {
            return AppendNodeGen.getUncached();
        }
    }

    public abstract static class CreateEmptyNode extends SequenceStorageBaseNode {

        @Child private GetElementType getElementType;

        public abstract SequenceStorage execute(SequenceStorage s, int cap, int len);

        private ListStorageType getElementType(SequenceStorage s) {
            if (getElementType == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                getElementType = insert(GetElementType.create());
            }
            return getElementType.execute(s);
        }

        protected boolean isBoolean(SequenceStorage s) {
            return getElementType(s) == ListStorageType.Boolean;
        }

        protected boolean isInt(SequenceStorage s) {
            return getElementType(s) == ListStorageType.Int;
        }

        protected boolean isLong(SequenceStorage s) {
            return getElementType(s) == ListStorageType.Long;
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

        @Specialization(guards = "isBoolean(s)")
        BoolSequenceStorage doBoolean(@SuppressWarnings("unused") SequenceStorage s, int cap, int len) {
            BoolSequenceStorage ss = new BoolSequenceStorage(cap);
            if (len != -1) {
                ss.ensureCapacity(len);
                ss.setNewLength(len);
            }
            return ss;
        }

        @Specialization(guards = "isByte(s)")
        ByteSequenceStorage doByte(@SuppressWarnings("unused") SequenceStorage s, int cap, int len) {
            ByteSequenceStorage ss = new ByteSequenceStorage(cap);
            if (len != -1) {
                ss.ensureCapacity(len);
                ss.setNewLength(len);
            }
            return ss;
        }

        @Specialization(guards = "isChar(s)")
        CharSequenceStorage doChar(@SuppressWarnings("unused") SequenceStorage s, int cap, int len) {
            CharSequenceStorage ss = new CharSequenceStorage(cap);
            if (len != -1) {
                ss.ensureCapacity(len);
                ss.setNewLength(len);
            }
            return ss;
        }

        @Specialization(guards = "isInt(s)")
        IntSequenceStorage doInt(@SuppressWarnings("unused") SequenceStorage s, int cap, int len) {
            IntSequenceStorage ss = new IntSequenceStorage(cap);
            if (len != -1) {
                ss.ensureCapacity(len);
                ss.setNewLength(len);
            }
            return ss;
        }

        @Specialization(guards = "isLong(s)")
        LongSequenceStorage doLong(@SuppressWarnings("unused") SequenceStorage s, int cap, int len) {
            LongSequenceStorage ss = new LongSequenceStorage(cap);
            if (len != -1) {
                ss.ensureCapacity(len);
                ss.setNewLength(len);
            }
            return ss;
        }

        @Specialization(guards = "isDouble(s)")
        DoubleSequenceStorage doDouble(@SuppressWarnings("unused") SequenceStorage s, int cap, int len) {
            DoubleSequenceStorage ss = new DoubleSequenceStorage(cap);
            if (len != -1) {
                ss.ensureCapacity(len);
                ss.setNewLength(len);
            }
            return ss;
        }

        @Specialization(guards = "isList(s)")
        ListSequenceStorage doList(@SuppressWarnings("unused") SequenceStorage s, int cap, int len) {
            // TODO not quite accurate in case of native sequence storage
            ListSequenceStorage ss = new ListSequenceStorage(cap);
            if (len != -1) {
                ss.ensureCapacity(len);
                ss.setNewLength(len);
            }
            return ss;
        }

        @Specialization(guards = "isTuple(s)")
        TupleSequenceStorage doTuple(@SuppressWarnings("unused") SequenceStorage s, int cap, int len) {
            TupleSequenceStorage ss = new TupleSequenceStorage(cap);
            if (len != -1) {
                ss.ensureCapacity(len);
                ss.setNewLength(len);
            }
            return ss;
        }

        @Fallback
        ObjectSequenceStorage doObject(@SuppressWarnings("unused") SequenceStorage s, int cap, int len) {
            ObjectSequenceStorage ss = new ObjectSequenceStorage(cap);
            if (len != -1) {
                ss.ensureCapacity(len);
                ss.setNewLength(len);
            }
            return ss;
        }

        public static CreateEmptyNode create() {
            return CreateEmptyNodeGen.create();
        }
    }

    @GenerateUncached
    public abstract static class EnsureCapacityNode extends SequenceStorageBaseNode {

        public abstract SequenceStorage execute(SequenceStorage s, int cap);

        @Specialization
        EmptySequenceStorage doEmpty(EmptySequenceStorage s, @SuppressWarnings("unused") int cap) {
            return s;
        }

        @Specialization(limit = "MAX_SEQUENCE_STORAGES", guards = "s.getClass() == cachedClass")
        BasicSequenceStorage doManaged(BasicSequenceStorage s, int cap,
                        @Cached PRaiseNode raiseNode,
                        @Cached("create()") BranchProfile overflowErrorProfile,
                        @Cached("s.getClass()") Class<? extends BasicSequenceStorage> cachedClass) {
            try {
                BasicSequenceStorage profiled = cachedClass.cast(s);
                profiled.ensureCapacity(cap);
                return profiled;
            } catch (ArithmeticException | OutOfMemoryError e) {
                overflowErrorProfile.enter();
                throw raiseNode.raise(OverflowError);
            }
        }

        @Specialization
        NativeSequenceStorage doObject(@SuppressWarnings("unused") NativeSequenceStorage s, @SuppressWarnings("unused") int cap) {
            // TODO re-allocate native memory
            CompilerDirectives.transferToInterpreterAndInvalidate();
            throw new UnsupportedOperationException();
        }

        public static EnsureCapacityNode create() {
            return EnsureCapacityNodeGen.create();
        }

        public static EnsureCapacityNode getUncached() {
            return EnsureCapacityNodeGen.getUncached();
        }

    }

    @GenerateUncached
    @ImportStatic(SequenceStorageBaseNode.class)
    public abstract static class CopyNode extends Node {

        public abstract SequenceStorage execute(SequenceStorage s);

        @Specialization(limit = "MAX_SEQUENCE_STORAGES", guards = "s.getClass() == cachedClass")
        static SequenceStorage doSpecial(SequenceStorage s,
                        @Cached("s.getClass()") Class<? extends SequenceStorage> cachedClass) {
            return CompilerDirectives.castExact(cachedClass.cast(s).copy(), cachedClass);
        }

        @Specialization(replaces = "doSpecial")
        @TruffleBoundary
        static SequenceStorage doGeneric(SequenceStorage s) {
            return s.copy();
        }

        public static CopyNode create() {
            return CopyNodeGen.create();
        }

        public static CopyNode getUncached() {
            return CopyNodeGen.getUncached();
        }
    }

    @GenerateUncached
    @ImportStatic(SequenceStorageBaseNode.class)
    public abstract static class CopyItemNode extends Node {

        public abstract void execute(SequenceStorage s, int to, int from);

        @Specialization(limit = "MAX_SEQUENCE_STORAGES", guards = "s.getClass() == cachedClass")
        static void doSpecial(SequenceStorage s, int to, int from,
                        @Cached("s.getClass()") Class<? extends SequenceStorage> cachedClass) {
            cachedClass.cast(s).copyItem(to, from);
        }

        @Specialization(replaces = "doSpecial")
        @TruffleBoundary
        static void doGeneric(SequenceStorage s, int to, int from) {
            s.copyItem(to, from);
        }

        public static CopyItemNode create() {
            return CopyItemNodeGen.create();
        }

        public static CopyItemNode getUncached() {
            return CopyItemNodeGen.getUncached();
        }
    }

    @GenerateUncached
    @ImportStatic(SequenceStorageBaseNode.class)
    public abstract static class LenNode extends Node {

        public abstract int execute(SequenceStorage s);

        @Specialization(limit = "MAX_SEQUENCE_STORAGES", guards = "s.getClass() == cachedClass")
        static int doSpecial(SequenceStorage s,
                        @Cached("s.getClass()") Class<? extends SequenceStorage> cachedClass) {
            return cachedClass.cast(s).length();
        }

        @Specialization(replaces = "doSpecial")
        @TruffleBoundary
        static int doGeneric(SequenceStorage s) {
            return s.length();
        }

        public static LenNode create() {
            return LenNodeGen.create();
        }

        public static LenNode getUncached() {
            return LenNodeGen.getUncached();
        }
    }

    @GenerateUncached
    @ImportStatic(SequenceStorageBaseNode.class)
    public abstract static class SetLenNode extends Node {

        public abstract void execute(SequenceStorage s, int len);

        @Specialization(limit = "MAX_SEQUENCE_STORAGES", guards = "s.getClass() == cachedClass")
        static void doSpecial(SequenceStorage s, int len,
                        @Cached("s.getClass()") Class<? extends SequenceStorage> cachedClass) {
            cachedClass.cast(s).setNewLength(len);
        }

        @Specialization(replaces = "doSpecial")
        static void doGeneric(SequenceStorage s, int len) {
            s.setNewLength(len);
        }

        public static SetLenNode create() {
            return SetLenNodeGen.create();
        }

        public static SetLenNode getUncached() {
            return SetLenNodeGen.getUncached();
        }
    }

    public abstract static class DeleteNode extends NormalizingNode {
        @Child private DeleteItemNode deleteItemNode;
        @Child private DeleteSliceNode deleteSliceNode;
        @Child private PRaiseNode raiseNode;
        private final String keyTypeErrorMessage;

        public DeleteNode(NormalizeIndexNode normalizeIndexNode, String keyTypeErrorMessage) {
            super(normalizeIndexNode);
            this.keyTypeErrorMessage = keyTypeErrorMessage;
        }

        public abstract void execute(VirtualFrame frame, SequenceStorage s, Object indexOrSlice);

        public abstract void execute(VirtualFrame frame, SequenceStorage s, int index);

        public abstract void execute(VirtualFrame frame, SequenceStorage s, long index);

        @Specialization
        protected void doScalarInt(VirtualFrame frame, SequenceStorage storage, int idx) {
            getGetItemScalarNode().execute(storage, normalizeIndex(frame, idx, storage));
        }

        @Specialization
        protected void doScalarLong(VirtualFrame frame, SequenceStorage storage, long idx) {
            getGetItemScalarNode().execute(storage, normalizeIndex(frame, idx, storage));
        }

        @Specialization
        protected void doScalarPInt(VirtualFrame frame, SequenceStorage storage, PInt idx) {
            getGetItemScalarNode().execute(storage, normalizeIndex(frame, idx, storage));
        }

        @Specialization(guards = "!isPSlice(idx)")
        protected void doScalarGeneric(VirtualFrame frame, SequenceStorage storage, Object idx) {
            getGetItemScalarNode().execute(storage, normalizeIndex(frame, idx, storage));
        }

        @Specialization
        protected void doSlice(SequenceStorage storage, PSlice slice,
                        @Cached LenNode lenNode) {
            SliceInfo info = slice.computeIndices(lenNode.execute(storage));
            try {
                getGetItemSliceNode().execute(storage, info);
            } catch (SequenceStoreException e) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                throw new IllegalStateException();
            }
        }

        @Fallback
        protected void doInvalidKey(@SuppressWarnings("unused") SequenceStorage storage, Object key) {
            if (raiseNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                raiseNode = insert(PRaiseNode.create());
            }
            throw raiseNode.raise(TypeError, keyTypeErrorMessage, key);
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

    @GenerateUncached
    public abstract static class DeleteItemNode extends SequenceStorageBaseNode {

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

    @GenerateUncached
    public abstract static class GetElementType extends Node {

        public abstract ListStorageType execute(SequenceStorage s);

        @Specialization(limit = "cacheLimit()", guards = {"s.getClass() == cachedClass"})
        static ListStorageType doCached(SequenceStorage s,
                        @Cached("s.getClass()") Class<? extends SequenceStorage> cachedClass) {
            return cachedClass.cast(s).getElementType();
        }

        @Specialization(replaces = "doCached")
        static ListStorageType doUncached(SequenceStorage s) {
            return s.getElementType();
        }

        protected static int cacheLimit() {
            return SequenceStorageBaseNode.MAX_SEQUENCE_STORAGES;
        }

        public static GetElementType create() {
            return GetElementTypeNodeGen.create();
        }

        public static GetElementType getUncached() {
            return GetElementTypeNodeGen.getUncached();
        }
    }

    @ImportStatic(SpecialMethodNames.class)
    public abstract static class ItemIndexNode extends SequenceStorageBaseNode {

        @Child private GetItemScalarNode getItemNode;
        @Child private LenNode lenNode;

        public abstract int execute(VirtualFrame frame, SequenceStorage s, Object item, int start, int end);

        public abstract int execute(VirtualFrame frame, SequenceStorage s, boolean item, int start, int end);

        public abstract int execute(VirtualFrame frame, SequenceStorage s, char item, int start, int end);

        public abstract int execute(VirtualFrame frame, SequenceStorage s, int item, int start, int end);

        public abstract int execute(VirtualFrame frame, SequenceStorage s, long item, int start, int end);

        public abstract int execute(VirtualFrame frame, SequenceStorage s, double item, int start, int end);

        @Specialization(guards = "isBoolean(getElementType, s)")
        int doBoolean(SequenceStorage s, boolean item, int start, int end,
                        @Cached @SuppressWarnings("unused") GetElementType getElementType) {
            for (int i = start; i < getLength(s, end); i++) {
                if (getItemScalarNode().executeBoolean(s, i) == item) {
                    return i;
                }
            }
            return -1;
        }

        @Specialization(guards = "isByte(getElementType, s)")
        int doByte(SequenceStorage s, byte item, int start, int end,
                        @Cached @SuppressWarnings("unused") GetElementType getElementType) {
            for (int i = start; i < getLength(s, end); i++) {
                if (getItemScalarNode().executeByte(s, i) == item) {
                    return i;
                }
            }
            return -1;
        }

        @Specialization(guards = "isChar(getElementType, s)")
        int doChar(SequenceStorage s, char item, int start, int end,
                        @Cached @SuppressWarnings("unused") GetElementType getElementType) {
            for (int i = start; i < getLength(s, end); i++) {
                if (getItemScalarNode().executeChar(s, i) == item) {
                    return i;
                }
            }
            return -1;
        }

        @Specialization(guards = "isInt(getElementType, s)")
        int doInt(SequenceStorage s, int item, int start, int end,
                        @Cached @SuppressWarnings("unused") GetElementType getElementType) {
            for (int i = start; i < getLength(s, end); i++) {
                if (getItemScalarNode().executeInt(s, i) == item) {
                    return i;
                }
            }
            return -1;
        }

        @Specialization(guards = "isLong(getElementType, s)")
        int doLong(SequenceStorage s, long item, int start, int end,
                        @Cached @SuppressWarnings("unused") GetElementType getElementType) {
            for (int i = start; i < getLength(s, end); i++) {
                if (getItemScalarNode().executeLong(s, i) == item) {
                    return i;
                }
            }
            return -1;
        }

        @Specialization(guards = "isDouble(getElementType, s)")
        int doDouble(SequenceStorage s, double item, int start, int end,
                        @Cached @SuppressWarnings("unused") GetElementType getElementType) {
            for (int i = start; i < getLength(s, end); i++) {
                if (java.lang.Double.compare(getItemScalarNode().executeDouble(s, i), item) == 0) {
                    return i;
                }
            }
            return -1;
        }

        @Specialization
        int doGeneric(VirtualFrame frame, SequenceStorage s, Object item, int start, int end,
                        @CachedLibrary(limit = "getCallSiteInlineCacheMaxDepth()") PythonObjectLibrary lib) {
            ThreadState state = PArguments.getThreadState(frame);
            for (int i = start; i < getLength(s, end); i++) {
                Object object = getItemScalarNode().execute(s, i);
                if (lib.equalsWithState(object, item, lib, state)) {
                    return i;
                }
            }
            return -1;
        }

        private GetItemScalarNode getItemScalarNode() {
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

    @GenerateUncached
    public abstract static class GetInternalObjectArrayNode extends Node {

        public abstract Object[] execute(SequenceStorage s);

        @Specialization
        static Object[] doObjectSequenceStorage(ObjectSequenceStorage s) {
            return s.getInternalArray();
        }

        @Specialization
        static Object[] doTypedSequenceStorage(TypedSequenceStorage s) {
            Object[] internalArray = s.getInternalArray();
            assert internalArray.length == s.length();
            return internalArray;
        }

        @Specialization
        static Object[] doNativeObject(NativeSequenceStorage s,
                        @Exclusive @Cached GetItemScalarNode getItemNode) {
            return materializeGeneric(s, s.length(), getItemNode);
        }

        @Specialization
        static Object[] doEmptySequenceStorage(EmptySequenceStorage s) {
            return s.getInternalArray();
        }

        @Specialization
        static Object[] doRangeSequenceStorage(RangeSequenceStorage s) {
            int length = s.length();
            PRange range = s.getRange();
            Object[] result = new Object[length];
            for (int i = 0, cur = range.getStart(); i < result.length; i++, cur += range.getStep()) {
                result[i] = cur;
            }
            return result;
        }

        @Specialization(replaces = {"doObjectSequenceStorage", "doTypedSequenceStorage", "doNativeObject", "doEmptySequenceStorage", "doRangeSequenceStorage"})
        static Object[] doGeneric(SequenceStorage s,
                        @Cached LenNode lenNode,
                        @Exclusive @Cached GetItemScalarNode getItemNode) {
            return materializeGeneric(s, lenNode.execute(s), getItemNode);
        }

        private static Object[] materializeGeneric(SequenceStorage s, int len, GetItemScalarNode getItemNode) {
            Object[] barr = new Object[len];
            for (int i = 0; i < barr.length; i++) {
                barr[i] = getItemNode.execute(s, i);
            }
            return barr;
        }
    }

    @GenerateUncached
    public abstract static class ToArrayNode extends Node {
        public abstract Object[] execute(SequenceStorage s);

        @Specialization
        static Object[] doObjectSequenceStorage(ObjectSequenceStorage s,
                        @Cached("createBinaryProfile()") ConditionProfile profile) {
            Object[] objects = GetInternalObjectArrayNode.doObjectSequenceStorage(s);
            int storageLength = s.length();
            if (profile.profile(storageLength != objects.length)) {
                return exactCopy(objects, storageLength);
            }
            return objects;
        }

        @Specialization(guards = "!isObjectSequenceStorage(s)")
        Object[] doOther(SequenceStorage s,
                        @Cached GetInternalObjectArrayNode getInternalObjectArrayNode) {
            return getInternalObjectArrayNode.execute(s);
        }

        private static Object[] exactCopy(Object[] barr, int len) {
            return Arrays.copyOf(barr, len);
        }

        static boolean isObjectSequenceStorage(SequenceStorage s) {
            return s instanceof ObjectSequenceStorage;
        }

    }

    @GenerateUncached
    @ImportStatic(SequenceStorageBaseNode.class)
    public abstract static class InsertItemNode extends Node {
        public final SequenceStorage execute(SequenceStorage storage, int index, Object value) {
            return execute(storage, index, value, true);
        }

        protected abstract SequenceStorage execute(SequenceStorage storage, int index, Object value, boolean recursive);

        @Specialization(limit = "MAX_ARRAY_STORAGES", guards = {"storage.getClass() == cachedClass"})
        protected SequenceStorage doStorage(SequenceStorage storage, int index, Object value, boolean recursive,
                        @Cached InsertItemNode recursiveNode,
                        @Cached("storage.getClass()") Class<? extends SequenceStorage> cachedClass) {
            try {
                cachedClass.cast(storage).insertItem(index, value);
                return storage;
            } catch (SequenceStoreException e) {
                if (!recursive) {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    throw new IllegalStateException();
                }
                SequenceStorage newStorage = cachedClass.cast(storage).generalizeFor(value, null);
                return recursiveNode.execute(newStorage, index, value, false);
            }
        }

        public static InsertItemNode create() {
            return InsertItemNodeGen.create();
        }

        public static InsertItemNode getUncached() {
            return InsertItemNodeGen.getUncached();
        }
    }

    public abstract static class CreateStorageFromIteratorHelper<T extends Node> {

        private static final int START_SIZE = 2;

        protected abstract boolean nextBoolean(VirtualFrame frame, T nextNode, Object iterator) throws UnexpectedResultException;

        protected abstract int nextInt(VirtualFrame frame, T nextNode, Object iterator) throws UnexpectedResultException;

        protected abstract long nextLong(VirtualFrame frame, T nextNode, Object iterator) throws UnexpectedResultException;

        protected abstract double nextDouble(VirtualFrame frame, T nextNode, Object iterator) throws UnexpectedResultException;

        protected abstract Object nextObject(VirtualFrame frame, T nextNode, Object iterator);

        protected SequenceStorage doIt(VirtualFrame frame, Object iterator, ListStorageType type, T nextNode, IsBuiltinClassProfile errorProfile) {
            SequenceStorage storage;
            if (type == Uninitialized || type == Empty) {
                Object[] elements = new Object[START_SIZE];
                int i = 0;
                while (true) {
                    try {
                        Object value = nextObject(frame, nextNode, iterator);
                        if (i >= elements.length) {
                            elements = Arrays.copyOf(elements, elements.length * 2);
                        }
                        elements[i++] = value;
                    } catch (PException e) {
                        e.expectStopIteration(errorProfile);
                        break;
                    }
                }
                storage = SequenceStorageFactory.createStorage(Arrays.copyOf(elements, i));
            } else {
                int i = 0;
                Object array = null;
                try {
                    switch (type) {
                        case Boolean: {
                            boolean[] elements = new boolean[START_SIZE];
                            array = elements;
                            while (true) {
                                try {
                                    boolean value = nextBoolean(frame, nextNode, iterator);
                                    if (i >= elements.length) {
                                        elements = Arrays.copyOf(elements, elements.length * 2);
                                        array = elements;
                                    }
                                    elements[i++] = value;
                                } catch (PException e) {
                                    e.expectStopIteration(errorProfile);
                                    break;
                                }
                            }
                            storage = new BoolSequenceStorage(elements, i);
                            break;
                        }
                        case Byte: {
                            byte[] elements = new byte[START_SIZE];
                            array = elements;
                            while (true) {
                                try {
                                    int value = nextInt(frame, nextNode, iterator);
                                    byte bvalue;
                                    try {
                                        bvalue = PInt.byteValueExact(value);
                                        if (i >= elements.length) {
                                            elements = Arrays.copyOf(elements, elements.length * 2);
                                            array = elements;
                                        }
                                        elements[i++] = bvalue;
                                    } catch (ArithmeticException e) {
                                        throw new UnexpectedResultException(value);
                                    }
                                } catch (PException e) {
                                    e.expectStopIteration(errorProfile);
                                    break;
                                }
                            }
                            storage = new ByteSequenceStorage(elements, i);
                            break;
                        }
                        case Int: {
                            int[] elements = new int[START_SIZE];
                            array = elements;
                            while (true) {
                                try {
                                    int value = nextInt(frame, nextNode, iterator);
                                    if (i >= elements.length) {
                                        elements = Arrays.copyOf(elements, elements.length * 2);
                                        array = elements;
                                    }
                                    elements[i++] = value;
                                } catch (PException e) {
                                    e.expectStopIteration(errorProfile);
                                    break;
                                }
                            }
                            storage = new IntSequenceStorage(elements, i);
                            break;
                        }
                        case Long: {
                            long[] elements = new long[START_SIZE];
                            array = elements;
                            while (true) {
                                try {
                                    long value = nextLong(frame, nextNode, iterator);
                                    if (i >= elements.length) {
                                        elements = Arrays.copyOf(elements, elements.length * 2);
                                        array = elements;
                                    }
                                    elements[i++] = value;
                                } catch (PException e) {
                                    e.expectStopIteration(errorProfile);
                                    break;
                                }
                            }
                            storage = new LongSequenceStorage(elements, i);
                            break;
                        }
                        case Double: {
                            double[] elements = new double[START_SIZE];
                            array = elements;
                            while (true) {
                                try {
                                    double value = nextDouble(frame, nextNode, iterator);
                                    if (i >= elements.length) {
                                        elements = Arrays.copyOf(elements, elements.length * 2);
                                        array = elements;
                                    }
                                    elements[i++] = value;
                                } catch (PException e) {
                                    e.expectStopIteration(errorProfile);
                                    break;
                                }
                            }
                            storage = new DoubleSequenceStorage(elements, i);
                            break;
                        }
                        case List: {
                            PList[] elements = new PList[START_SIZE];
                            array = elements;
                            while (true) {
                                try {
                                    PList value = PList.expect(nextObject(frame, nextNode, iterator));
                                    if (i >= elements.length) {
                                        elements = Arrays.copyOf(elements, elements.length * 2);
                                        array = elements;
                                    }
                                    elements[i++] = value;
                                } catch (PException e) {
                                    e.expectStopIteration(errorProfile);
                                    break;
                                }
                            }
                            storage = new ListSequenceStorage(elements, i);
                            break;
                        }
                        case Tuple: {
                            PTuple[] elements = new PTuple[START_SIZE];
                            array = elements;
                            while (true) {
                                try {
                                    PTuple value = PTuple.expect(nextObject(frame, nextNode, iterator));
                                    if (i >= elements.length) {
                                        elements = Arrays.copyOf(elements, elements.length * 2);
                                        array = elements;
                                    }
                                    elements[i++] = value;
                                } catch (PException e) {
                                    e.expectStopIteration(errorProfile);
                                    break;
                                }
                            }
                            storage = new TupleSequenceStorage(elements, i);
                            break;
                        }
                        case Generic: {
                            Object[] elements = new Object[START_SIZE];
                            while (true) {
                                try {
                                    Object value = nextObject(frame, nextNode, iterator);
                                    if (i >= elements.length) {
                                        elements = Arrays.copyOf(elements, elements.length * 2);
                                    }
                                    elements[i++] = value;
                                } catch (PException e) {
                                    e.expectStopIteration(errorProfile);
                                    break;
                                }
                            }
                            storage = new ObjectSequenceStorage(elements, i);
                            break;
                        }
                        default:
                            CompilerDirectives.transferToInterpreterAndInvalidate();
                            throw new RuntimeException("unexpected state");
                    }
                } catch (UnexpectedResultException e) {
                    storage = genericFallback(frame, iterator, array, i, e.getResult(), nextNode, errorProfile);
                }
            }
            return storage;
        }

        private SequenceStorage genericFallback(VirtualFrame frame, Object iterator, Object array, int count, Object result, T nextNode, IsBuiltinClassProfile errorProfile) {
            Object[] elements = new Object[Array.getLength(array) * 2];
            int i = 0;
            for (; i < count; i++) {
                elements[i] = Array.get(array, i);
            }
            elements[i++] = result;
            while (true) {
                try {
                    Object value = nextObject(frame, nextNode, iterator);
                    if (i >= elements.length) {
                        elements = Arrays.copyOf(elements, elements.length * 2);
                    }
                    elements[i++] = value;
                } catch (PException e) {
                    e.expectStopIteration(errorProfile);
                    break;
                }
            }
            return new ObjectSequenceStorage(elements, i);
        }

    }

    private static final class CreateStorageFromIteratorInternalNode extends CreateStorageFromIteratorHelper<GetNextNode> {

        @Override
        protected boolean nextBoolean(VirtualFrame frame, GetNextNode nextNode, Object iterator) throws UnexpectedResultException {
            return nextNode.executeBoolean(frame, iterator);
        }

        @Override
        protected int nextInt(VirtualFrame frame, GetNextNode nextNode, Object iterator) throws UnexpectedResultException {
            return nextNode.executeInt(frame, iterator);
        }

        @Override
        protected long nextLong(VirtualFrame frame, GetNextNode nextNode, Object iterator) throws UnexpectedResultException {
            return nextNode.executeLong(frame, iterator);
        }

        @Override
        protected double nextDouble(VirtualFrame frame, GetNextNode nextNode, Object iterator) throws UnexpectedResultException {
            return nextNode.executeDouble(frame, iterator);
        }

        @Override
        protected Object nextObject(VirtualFrame frame, GetNextNode nextNode, Object iterator) {
            return nextNode.execute(frame, iterator);
        }

    }

    public static final class CreateStorageFromIteratorNode extends Node {
        private static final CreateStorageFromIteratorInternalNode HELPER = new CreateStorageFromIteratorInternalNode();

        @Child private GetNextNode getNextNode = GetNextNode.create();
        @Child private GetElementType getElementType = GetElementType.create();

        private final IsBuiltinClassProfile errorProfile = IsBuiltinClassProfile.create();

        @CompilationFinal private ListStorageType expectedElementType = Uninitialized;

        public SequenceStorage execute(VirtualFrame frame, Object iterator) {
            SequenceStorage doIt = HELPER.doIt(frame, iterator, expectedElementType, getNextNode, errorProfile);
            ListStorageType actualElementType = getElementType.execute(doIt);
            if (expectedElementType != actualElementType) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                expectedElementType = actualElementType;
            }
            return doIt;
        }

        public static CreateStorageFromIteratorNode create() {
            return new CreateStorageFromIteratorNode();
        }
    }

    private static final class CreateStorageFromIteratorInteropHelper extends CreateStorageFromIteratorHelper<GetNextWithoutFrameNode> {

        @Override
        protected boolean nextBoolean(VirtualFrame frame, GetNextWithoutFrameNode nextNode, Object iterator) throws UnexpectedResultException {
            Object value = nextNode.executeWithGlobalState(iterator);
            if (value instanceof Boolean) {
                return (boolean) value;
            }
            throw new UnexpectedResultException(value);
        }

        @Override
        protected int nextInt(VirtualFrame frame, GetNextWithoutFrameNode nextNode, Object iterator) throws UnexpectedResultException {
            Object value = nextNode.executeWithGlobalState(iterator);
            if (value instanceof Integer) {
                return (int) value;
            }
            throw new UnexpectedResultException(value);
        }

        @Override
        protected long nextLong(VirtualFrame frame, GetNextWithoutFrameNode nextNode, Object iterator) throws UnexpectedResultException {
            Object value = nextNode.executeWithGlobalState(iterator);
            if (value instanceof Long) {
                return (long) value;
            }
            throw new UnexpectedResultException(value);
        }

        @Override
        protected double nextDouble(VirtualFrame frame, GetNextWithoutFrameNode nextNode, Object iterator) throws UnexpectedResultException {
            Object value = nextNode.executeWithGlobalState(iterator);
            if (value instanceof Double) {
                return (double) value;
            }
            throw new UnexpectedResultException(value);
        }

        @Override
        protected Object nextObject(VirtualFrame frame, GetNextWithoutFrameNode nextNode, Object iterator) {
            return nextNode.executeWithGlobalState(iterator);
        }
    }

    public abstract static class CreateStorageFromIteratorInteropNode extends PNodeWithContext {

        protected static final CreateStorageFromIteratorInteropHelper HELPER = new CreateStorageFromIteratorInteropHelper();

        public abstract SequenceStorage execute(Object iterator);

        public static CreateStorageFromIteratorInteropNode create() {
            return new CreateStorageFromIteratorCachedNode();
        }

        public static CreateStorageFromIteratorInteropNode getUncached() {
            return CreateStorageFromIteratorUncachedNode.INSTANCE;
        }
    }

    private static final class CreateStorageFromIteratorCachedNode extends CreateStorageFromIteratorInteropNode {

        @Child private GetNextWithoutFrameNode getNextNode = GetNextWithoutFrameNodeGen.create();

        private final IsBuiltinClassProfile errorProfile = IsBuiltinClassProfile.create();

        @CompilationFinal private ListStorageType expectedElementType = Uninitialized;

        @Override
        public SequenceStorage execute(Object iterator) {
            // NOTE: it is fine to pass 'null' frame because the callers must already take care of
            // the global state
            SequenceStorage doIt = HELPER.doIt(null, iterator, expectedElementType, getNextNode, errorProfile);
            ListStorageType actualElementType = doIt.getElementType();
            if (expectedElementType != actualElementType) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                expectedElementType = actualElementType;
            }
            return doIt;
        }
    }

    private static final class CreateStorageFromIteratorUncachedNode extends CreateStorageFromIteratorInteropNode {
        public static final CreateStorageFromIteratorUncachedNode INSTANCE = new CreateStorageFromIteratorUncachedNode();

        @Override
        public SequenceStorage execute(Object iterator) {
            // NOTE: it is fine to pass 'null' frame because the callers must already take care of
            // the global state
            return HELPER.doIt(null, iterator, Uninitialized, GetNextWithoutFrameNodeGen.getUncached(), IsBuiltinClassProfile.getUncached());
        }

    }
}
