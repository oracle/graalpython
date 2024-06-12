/*
 * Copyright (c) 2018, 2024, Oracle and/or its affiliates.
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

import static com.oracle.graal.python.builtins.objects.common.IndexNodes.checkBounds;
import static com.oracle.graal.python.builtins.objects.iterator.IteratorBuiltins.NextHelperNode.STOP_MARKER;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.IndexError;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.MemoryError;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.OverflowError;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.TypeError;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.ValueError;
import static com.oracle.graal.python.runtime.sequence.storage.SequenceStorage.StorageType.Boolean;
import static com.oracle.graal.python.runtime.sequence.storage.SequenceStorage.StorageType.Byte;
import static com.oracle.graal.python.runtime.sequence.storage.SequenceStorage.StorageType.Double;
import static com.oracle.graal.python.runtime.sequence.storage.SequenceStorage.StorageType.Empty;
import static com.oracle.graal.python.runtime.sequence.storage.SequenceStorage.StorageType.Int;
import static com.oracle.graal.python.runtime.sequence.storage.SequenceStorage.StorageType.Long;
import static com.oracle.graal.python.runtime.sequence.storage.SequenceStorage.StorageType.Uninitialized;

import java.lang.reflect.Array;
import java.util.Arrays;

import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.modules.SysModuleBuiltins;
import com.oracle.graal.python.builtins.objects.bytes.BytesNodes;
import com.oracle.graal.python.builtins.objects.bytes.PBytesLike;
import com.oracle.graal.python.builtins.objects.cext.capi.CExtNodes;
import com.oracle.graal.python.builtins.objects.cext.capi.transitions.CApiTransitions.NativeToPythonNode;
import com.oracle.graal.python.builtins.objects.cext.capi.transitions.CApiTransitions.PythonToNativeNewRefNode;
import com.oracle.graal.python.builtins.objects.cext.structs.CStructAccess;
import com.oracle.graal.python.builtins.objects.common.IndexNodes.NormalizeIndexCustomMessageNode;
import com.oracle.graal.python.builtins.objects.common.IndexNodes.NormalizeIndexNode;
import com.oracle.graal.python.builtins.objects.common.SequenceNodes.GetSequenceStorageNode;
import com.oracle.graal.python.builtins.objects.common.SequenceStorageNodesFactory.AppendNodeGen;
import com.oracle.graal.python.builtins.objects.common.SequenceStorageNodesFactory.CmpNodeGen;
import com.oracle.graal.python.builtins.objects.common.SequenceStorageNodesFactory.ConcatBaseNodeGen;
import com.oracle.graal.python.builtins.objects.common.SequenceStorageNodesFactory.ConcatNodeGen;
import com.oracle.graal.python.builtins.objects.common.SequenceStorageNodesFactory.CreateStorageFromIteratorNodeFactory.CreateStorageFromIteratorNodeCachedNodeGen;
import com.oracle.graal.python.builtins.objects.common.SequenceStorageNodesFactory.DeleteNodeGen;
import com.oracle.graal.python.builtins.objects.common.SequenceStorageNodesFactory.ExtendNodeGen;
import com.oracle.graal.python.builtins.objects.common.SequenceStorageNodesFactory.GetElementTypeNodeGen;
import com.oracle.graal.python.builtins.objects.common.SequenceStorageNodesFactory.GetItemDynamicNodeGen;
import com.oracle.graal.python.builtins.objects.common.SequenceStorageNodesFactory.GetItemNodeGen;
import com.oracle.graal.python.builtins.objects.common.SequenceStorageNodesFactory.GetItemScalarNodeGen;
import com.oracle.graal.python.builtins.objects.common.SequenceStorageNodesFactory.GetItemSliceNodeGen;
import com.oracle.graal.python.builtins.objects.common.SequenceStorageNodesFactory.InsertItemNodeGen;
import com.oracle.graal.python.builtins.objects.common.SequenceStorageNodesFactory.ListGeneralizationNodeGen;
import com.oracle.graal.python.builtins.objects.common.SequenceStorageNodesFactory.MemMoveNodeGen;
import com.oracle.graal.python.builtins.objects.common.SequenceStorageNodesFactory.NoGeneralizationCustomMessageNodeGen;
import com.oracle.graal.python.builtins.objects.common.SequenceStorageNodesFactory.NoGeneralizationNodeGen;
import com.oracle.graal.python.builtins.objects.common.SequenceStorageNodesFactory.RepeatNodeGen;
import com.oracle.graal.python.builtins.objects.common.SequenceStorageNodesFactory.SetItemDynamicNodeGen;
import com.oracle.graal.python.builtins.objects.common.SequenceStorageNodesFactory.SetItemNodeGen;
import com.oracle.graal.python.builtins.objects.common.SequenceStorageNodesFactory.StorageToNativeNodeGen;
import com.oracle.graal.python.builtins.objects.common.SequenceStorageNodesFactory.ToByteArrayNodeGen;
import com.oracle.graal.python.builtins.objects.ints.PInt;
import com.oracle.graal.python.builtins.objects.iterator.IteratorBuiltins.NextHelperNode;
import com.oracle.graal.python.builtins.objects.iterator.IteratorNodes.BuiltinIteratorLengthHint;
import com.oracle.graal.python.builtins.objects.iterator.IteratorNodes.GetInternalIteratorSequenceStorage;
import com.oracle.graal.python.builtins.objects.iterator.PBuiltinIterator;
import com.oracle.graal.python.builtins.objects.list.PList;
import com.oracle.graal.python.builtins.objects.range.RangeNodes.LenOfRangeNode;
import com.oracle.graal.python.builtins.objects.slice.PSlice;
import com.oracle.graal.python.builtins.objects.slice.PSlice.SliceInfo;
import com.oracle.graal.python.builtins.objects.slice.SliceNodes;
import com.oracle.graal.python.builtins.objects.slice.SliceNodes.CoerceToIntSlice;
import com.oracle.graal.python.builtins.objects.slice.SliceNodes.ComputeIndices;
import com.oracle.graal.python.builtins.objects.str.PString;
import com.oracle.graal.python.builtins.objects.type.PythonAbstractClass;
import com.oracle.graal.python.lib.GetNextNode;
import com.oracle.graal.python.lib.PyIndexCheckNode;
import com.oracle.graal.python.lib.PyNumberAsSizeNode;
import com.oracle.graal.python.lib.PyObjectGetIter;
import com.oracle.graal.python.lib.PyObjectRichCompareBool;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PGuards;
import com.oracle.graal.python.nodes.PNodeWithContext;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.SpecialMethodNames;
import com.oracle.graal.python.nodes.StringLiterals;
import com.oracle.graal.python.nodes.builtins.ListNodes;
import com.oracle.graal.python.nodes.expression.BinaryComparisonNode;
import com.oracle.graal.python.nodes.expression.CoerceToBooleanNode;
import com.oracle.graal.python.nodes.object.BuiltinClassProfiles.IsBuiltinObjectProfile;
import com.oracle.graal.python.nodes.object.GetClassNode;
import com.oracle.graal.python.nodes.object.GetClassNode.GetPythonObjectClassNode;
import com.oracle.graal.python.nodes.util.CastToByteNode;
import com.oracle.graal.python.nodes.util.CastToJavaByteNode;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.graal.python.runtime.PythonOptions;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.graal.python.runtime.object.PythonObjectFactory;
import com.oracle.graal.python.runtime.sequence.PSequence;
import com.oracle.graal.python.runtime.sequence.storage.ArrayBasedSequenceStorage;
import com.oracle.graal.python.runtime.sequence.storage.BoolSequenceStorage;
import com.oracle.graal.python.runtime.sequence.storage.ByteSequenceStorage;
import com.oracle.graal.python.runtime.sequence.storage.DoubleSequenceStorage;
import com.oracle.graal.python.runtime.sequence.storage.EmptySequenceStorage;
import com.oracle.graal.python.runtime.sequence.storage.IntSequenceStorage;
import com.oracle.graal.python.runtime.sequence.storage.LongSequenceStorage;
import com.oracle.graal.python.runtime.sequence.storage.MroSequenceStorage;
import com.oracle.graal.python.runtime.sequence.storage.NativeByteSequenceStorage;
import com.oracle.graal.python.runtime.sequence.storage.NativeObjectSequenceStorage;
import com.oracle.graal.python.runtime.sequence.storage.NativeSequenceStorage;
import com.oracle.graal.python.runtime.sequence.storage.ObjectSequenceStorage;
import com.oracle.graal.python.runtime.sequence.storage.SequenceStorage;
import com.oracle.graal.python.runtime.sequence.storage.SequenceStorage.StorageType;
import com.oracle.graal.python.runtime.sequence.storage.SequenceStorageFactory;
import com.oracle.graal.python.runtime.sequence.storage.SequenceStoreException;
import com.oracle.graal.python.runtime.sequence.storage.native2.ArrowSequenceStorage;
import com.oracle.graal.python.runtime.sequence.storage.native2.IntArrowSequenceStorage;
import com.oracle.graal.python.runtime.sequence.storage.native2.NativeBuffer;
import com.oracle.graal.python.util.BiFunction;
import com.oracle.graal.python.util.OverflowException;
import com.oracle.graal.python.util.PythonUtils;
import com.oracle.graal.python.util.Supplier;
import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.HostCompilerDirectives.InliningCutoff;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Exclusive;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.GenerateCached;
import com.oracle.truffle.api.dsl.GenerateInline;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.NeverDefault;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.nodes.LoopNode;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.UnexpectedResultException;
import com.oracle.truffle.api.profiles.InlinedBranchProfile;
import com.oracle.truffle.api.profiles.InlinedConditionProfile;
import com.oracle.truffle.api.profiles.InlinedCountingConditionProfile;
import com.oracle.truffle.api.profiles.InlinedExactClassProfile;
import com.oracle.truffle.api.profiles.InlinedLoopConditionProfile;
import com.oracle.truffle.api.strings.TruffleString;
import sun.misc.Unsafe;

public abstract class SequenceStorageNodes {

    @GenerateInline
    @GenerateCached(false)
    @GenerateUncached
    public abstract static class SequenceStorageSqItemNode extends Node {
        public abstract Object execute(Node inliningTarget, SequenceStorage storage, int index, TruffleString indexBoundsErrorMessage);

        @Specialization
        static Object doIt(Node inliningTarget, SequenceStorage self, int index, TruffleString errorMessage,
                        @Cached PRaiseNode.Lazy raiseNode,
                        @Cached SequenceStorageNodes.GetItemScalarNode getItemNode) {
            return getItem(inliningTarget, self, index, errorMessage, raiseNode, getItemNode);
        }

        private static Object getItem(Node inliningTarget, SequenceStorage storage, int index, TruffleString errorMessage, PRaiseNode.Lazy raiseNode, GetItemScalarNode getItemNode) {
            checkBounds(inliningTarget, raiseNode, errorMessage, index, storage.length());
            return getItemNode.execute(inliningTarget, storage, index);
        }
    }

    @FunctionalInterface
    public interface StorageWrapperFactory {
        Object create(PythonObjectFactory factory, SequenceStorage newStorage);
    }

    @GenerateInline
    @GenerateCached(false)
    public abstract static class SequenceStorageMpSubscriptNode extends PNodeWithContext {
        // This should be checked by the caller before calling execute
        public static boolean isValidIndex(Node inliningTarget, Object index, PyIndexCheckNode indexCheckNode) {
            return PGuards.isPSlice(index) || indexCheckNode.execute(inliningTarget, index);
        }

        public final Object execute(VirtualFrame frame, Node inliningTarget, SequenceStorage storage, Object index,
                        TruffleString indexBoundsErrorMessage, StorageWrapperFactory factory) {
            assert isValidIndex(null, index, PyIndexCheckNode.getUncached());
            return executeImpl(frame, inliningTarget, storage, index, indexBoundsErrorMessage, factory);
        }

        abstract Object executeImpl(VirtualFrame frame, Node inliningTarget, SequenceStorage storage, Object index,
                        TruffleString indexBoundsErrorMessage, StorageWrapperFactory factory);

        @Specialization(guards = "!isPSlice(idx)")
        static Object doNonSlice(VirtualFrame frame, Node inliningTarget, SequenceStorage storage, Object idx,
                        TruffleString indexBoundsErrorMessage, StorageWrapperFactory wrapperFactory,
                        @Cached PyNumberAsSizeNode numberAsSizeNode,
                        @Cached InlinedConditionProfile negativeIndexProfile,
                        @Cached PRaiseNode.Lazy raiseNode,
                        @Cached SequenceStorageNodes.GetItemScalarNode getItemNode) {
            int index = numberAsSizeNode.executeExact(frame, inliningTarget, idx, PythonBuiltinClassType.IndexError);
            if (negativeIndexProfile.profile(inliningTarget, index < 0)) {
                index += storage.length();
            }
            return SequenceStorageSqItemNode.getItem(inliningTarget, storage, index, indexBoundsErrorMessage, raiseNode, getItemNode);
        }

        @Specialization
        static Object doSlice(VirtualFrame frame, Node inliningTarget, SequenceStorage storage, PSlice slice,
                        @SuppressWarnings("unused") TruffleString indexBoundsErrorMessage, StorageWrapperFactory wrapperFactory,
                        @Cached(inline = false) PythonObjectFactory factory,
                        @Cached CoerceToIntSlice sliceCast,
                        @Cached(inline = false) ComputeIndices compute,
                        @Cached(inline = false) GetItemSliceNode getItemSliceNode,
                        @Cached LenOfRangeNode sliceLen) {
            SliceInfo info = compute.execute(frame, sliceCast.execute(inliningTarget, slice), storage.length());
            SequenceStorage newStorage = getItemSliceNode.execute(storage, info.start, info.stop, info.step, sliceLen.len(inliningTarget, info));
            return wrapperFactory.create(factory, newStorage);
        }
    }

    public interface GenNodeSupplier {
        GeneralizationNode create();

        GeneralizationNode getUncached();
    }

    @GenerateUncached
    @GenerateInline
    @GenerateCached(false)
    public abstract static class IsAssignCompatibleNode extends Node {

        protected abstract boolean execute(Node inliningTarget, SequenceStorage lhs, SequenceStorage rhs);

        /**
         * Tests if each element of {@code rhs} can be assigned to {@code lhs} without casting.
         */
        @Specialization
        static boolean compatibleAssign(Node inliningTarget, SequenceStorage lhs, SequenceStorage rhs,
                        @Cached GetElementType getElementTypeNode) {
            StorageType rhsType = getElementTypeNode.execute(inliningTarget, rhs);
            switch (getElementTypeNode.execute(inliningTarget, lhs)) {
                case Boolean:
                    return rhsType == Boolean || rhsType == Uninitialized || rhsType == Empty;
                case Byte:
                    return rhsType == Byte || rhsType == Uninitialized || rhsType == Empty;
                case Int:
                    return rhsType == StorageType.Byte || rhsType == StorageType.Int || rhsType == Uninitialized || rhsType == Empty;
                case Long:
                    return rhsType == Byte || rhsType == Int || rhsType == Long || rhsType == Uninitialized || rhsType == Empty;
                case Double:
                    return rhsType == Double || rhsType == Uninitialized || rhsType == Empty;
                case Generic:
                    return true;
                case Empty:
                case Uninitialized:
                    return false;
            }
            assert false : "should not reach";
            return false;
        }
    }

    @GenerateUncached
    @GenerateInline
    @GenerateCached(false)
    abstract static class IsDataTypeCompatibleNode extends Node {

        protected abstract boolean execute(Node inliningTarget, SequenceStorage lhs, SequenceStorage rhs);

        /**
         * Tests if each element of {@code rhs} can be assigned to {@code lhs} without casting.
         */
        @Specialization
        static boolean compatibleAssign(Node inliningTarget, SequenceStorage lhs, SequenceStorage rhs,
                        @Cached GetElementType getElementTypeNode) {
            StorageType rhsType = getElementTypeNode.execute(inliningTarget, rhs);
            switch (getElementTypeNode.execute(inliningTarget, lhs)) {
                case Boolean:
                case Byte:
                case Int:
                case Long:
                    return rhsType == Boolean || rhsType == Byte || rhsType == Int || rhsType == Long || rhsType == Uninitialized || rhsType == Empty;
                case Double:
                    return rhsType == Double || rhsType == Uninitialized || rhsType == Empty;
                case Generic:
                    return true;
                case Empty:
                case Uninitialized:
                    return false;
            }
            assert false : "should not reach";
            return false;
        }
    }

    @ImportStatic(PythonOptions.class)
    abstract static class SequenceStorageBaseNode extends PNodeWithContext {

        protected static final int MAX_SEQUENCE_STORAGES = 9;
        protected static final int MAX_BASIC_STORAGES = 7;

        protected static boolean isNative(SequenceStorage store) {
            return store instanceof NativeSequenceStorage;
        }

        protected static boolean isEmpty(SequenceStorage left) {
            return left.length() == 0;
        }

        protected static boolean isBoolean(StorageType et) {
            return et == SequenceStorage.StorageType.Boolean;
        }

        protected static boolean isByte(StorageType et) {
            return et == StorageType.Byte;
        }

        protected static boolean isByteLike(StorageType et) {
            return isByte(et) || isInt(et) || isLong(et);
        }

        protected static boolean isInt(StorageType et) {
            return et == StorageType.Int;
        }

        protected static boolean isLong(StorageType et) {
            return et == StorageType.Long;
        }

        protected static boolean isDouble(SequenceStorage.StorageType et) {
            return et == StorageType.Double;
        }

        protected static boolean isObject(StorageType et) {
            return et == StorageType.Generic;
        }

        protected static boolean hasStorage(Object source) {
            return source instanceof PSequence && !(source instanceof PString);
        }
    }

    abstract static class NormalizingNode extends PNodeWithContext {

        @Child private NormalizeIndexNode normalizeIndexNode;

        protected NormalizingNode(NormalizeIndexNode normalizeIndexNode) {
            this.normalizeIndexNode = normalizeIndexNode;
        }

        protected final int normalizeIndex(VirtualFrame frame, Node inliningTarget, PyNumberAsSizeNode asSizeNode, Object idx, SequenceStorage store) {
            int intIdx = asSizeNode.executeExact(frame, inliningTarget, idx, IndexError);
            if (normalizeIndexNode != null) {
                return normalizeIndexNode.execute(intIdx, store.length());
            }
            return intIdx;
        }

        protected final int normalizeIndex(int idx, SequenceStorage store) {
            if (normalizeIndexNode != null) {
                return normalizeIndexNode.execute(idx, store.length());
            }
            return idx;
        }

        protected final int normalizeIndex(VirtualFrame frame, Node inliningTarget, PyNumberAsSizeNode asSizeNode, long idx, SequenceStorage store) {
            int intIdx = asSizeNode.executeExact(frame, inliningTarget, idx, IndexError);
            if (normalizeIndexNode != null) {
                return normalizeIndexNode.execute(intIdx, store.length());
            }
            return intIdx;
        }

        protected static boolean isPSlice(Object obj) {
            return obj instanceof PSlice;
        }

    }

    public abstract static class GetItemNode extends NormalizingNode {

        @Child private GetItemScalarNode getItemScalarNode;
        @Child private GetItemSliceNode getItemSliceNode;
        private final BiFunction<SequenceStorage, PythonObjectFactory, Object> factoryMethod;

        public GetItemNode(NormalizeIndexNode normalizeIndexNode, BiFunction<SequenceStorage, PythonObjectFactory, Object> factoryMethod) {
            super(normalizeIndexNode);
            this.factoryMethod = factoryMethod;
        }

        public abstract Object execute(VirtualFrame frame, SequenceStorage s, Object key);

        public final Object execute(SequenceStorage s, int key) {
            return doScalarInt(s, key);
        }

        public final int executeInt(SequenceStorage s, int key) throws UnexpectedResultException {
            return getGetItemScalarNode().executeIntCached(s, normalizeIndex(key, s));
        }

        public final int executeKnownInt(SequenceStorage s, int key) {
            return getGetItemScalarNode().executeKnownIntCached(s, normalizeIndex(key, s));
        }

        public final double executeDouble(SequenceStorage s, int key) throws UnexpectedResultException {
            return getGetItemScalarNode().executeDoubleCached(s, normalizeIndex(key, s));
        }

        @Specialization
        protected Object doScalarInt(SequenceStorage storage, int idx) {
            return getGetItemScalarNode().executeCached(storage, normalizeIndex(idx, storage));
        }

        @Specialization
        protected Object doScalarLong(VirtualFrame frame, SequenceStorage storage, long idx,
                        @Bind("this") Node inliningTarget,
                        @Shared @Cached PyNumberAsSizeNode asSizeNode) {
            return getGetItemScalarNode().executeCached(storage, normalizeIndex(frame, inliningTarget, asSizeNode, idx, storage));
        }

        @InliningCutoff
        @Specialization
        protected Object doScalarPInt(VirtualFrame frame, SequenceStorage storage, PInt idx,
                        @Bind("this") Node inliningTarget,
                        @Shared @Cached PyNumberAsSizeNode asSizeNode) {
            return getGetItemScalarNode().executeCached(storage, normalizeIndex(frame, inliningTarget, asSizeNode, idx, storage));
        }

        @InliningCutoff
        @Specialization(guards = "!isPSlice(idx)")
        protected Object doScalarGeneric(VirtualFrame frame, SequenceStorage storage, Object idx,
                        @Bind("this") Node inliningTarget,
                        @Shared @Cached PyNumberAsSizeNode asSizeNode) {
            return getGetItemScalarNode().executeCached(storage, normalizeIndex(frame, inliningTarget, asSizeNode, idx, storage));
        }

        @InliningCutoff
        @Specialization
        @SuppressWarnings("truffle-static-method")
        protected Object doSlice(VirtualFrame frame, SequenceStorage storage, PSlice slice,
                        @Bind("this") Node inliningTarget,
                        @Cached PythonObjectFactory factory,
                        @Cached CoerceToIntSlice sliceCast,
                        @Cached ComputeIndices compute,
                        @Cached LenOfRangeNode sliceLen) {
            SliceInfo info = compute.execute(frame, sliceCast.execute(inliningTarget, slice), storage.length());
            if (factoryMethod != null) {
                return factoryMethod.apply(getGetItemSliceNode().execute(storage, info.start, info.stop, info.step, sliceLen.len(inliningTarget, info)), factory);
            }
            CompilerDirectives.transferToInterpreterAndInvalidate();
            throw new IllegalStateException();
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

        @NeverDefault
        public static GetItemNode createNotNormalized() {
            return GetItemNodeGen.create(null, null);
        }

        @NeverDefault
        public static GetItemNode create(NormalizeIndexNode normalizeIndexNode) {
            return GetItemNodeGen.create(normalizeIndexNode, null);
        }

        @NeverDefault
        public static GetItemNode create() {
            return GetItemNodeGen.create(NormalizeIndexNode.create(), null);
        }

        @NeverDefault
        public static GetItemNode create(NormalizeIndexNode normalizeIndexNode, BiFunction<SequenceStorage, PythonObjectFactory, Object> factoryMethod) {
            return GetItemNodeGen.create(normalizeIndexNode, factoryMethod);
        }

        @NeverDefault
        public static SequenceStorageNodes.GetItemNode createForList() {
            return SequenceStorageNodes.GetItemNode.create(NormalizeIndexNode.forList(), (s, f) -> f.createList(s));
        }

        @NeverDefault
        public static SequenceStorageNodes.GetItemNode createForTuple() {
            return SequenceStorageNodes.GetItemNode.create(NormalizeIndexNode.forTuple(), (s, f) -> f.createTuple(s));
        }
    }

    @GenerateUncached
    @GenerateInline
    @GenerateCached(false)
    @ImportStatic(PGuards.class)
    public abstract static class GetItemDynamicNode extends Node {

        public abstract Object execute(Node inliningTarget, SequenceStorage s, int key);

        public static Object executeUncached(SequenceStorage s, int key) {
            return GetItemDynamicNodeGen.getUncached().execute(null, s, key);
        }

        @Specialization
        protected static Object doScalarInt(Node inliningTarget, SequenceStorage storage, int idx,
                        @Cached GetItemScalarNode getItemScalarNode,
                        @Cached(inline = false) NormalizeIndexCustomMessageNode normalizeIndexNode) {
            return getItemScalarNode.execute(inliningTarget, storage, normalizeIndexNode.execute(idx, storage.length(), ErrorMessages.INDEX_OUT_OF_RANGE));
        }
    }

    @GenerateUncached
    @GenerateInline(inlineByDefault = true)
    @GenerateCached
    @ImportStatic(SequenceStorageBaseNode.class)
    public abstract static class GetItemScalarNode extends Node {

        public abstract Object execute(Node inliningTarget, SequenceStorage s, int idx);

        public final Object executeCached(SequenceStorage s, int idx) {
            return execute(null, s, idx);
        }

        @NeverDefault
        public static GetItemScalarNode create() {
            return GetItemScalarNodeGen.create();
        }

        public static Object executeUncached(SequenceStorage s, int idx) {
            return GetItemScalarNodeGen.getUncached().execute(null, s, idx);
        }

        public abstract int executeInt(Node inliningTarget, SequenceStorage s, int idx) throws UnexpectedResultException;

        public final int executeIntCached(SequenceStorage s, int idx) throws UnexpectedResultException {
            return executeInt(null, s, idx);
        }

        public final int executeKnownInt(Node inliningTarget, SequenceStorage s, int idx) {
            try {
                return executeInt(inliningTarget, s, idx);
            } catch (UnexpectedResultException e) {
                throw CompilerDirectives.shouldNotReachHere();
            }
        }

        public final int executeKnownIntCached(SequenceStorage s, int idx) {
            return executeKnownInt(null, s, idx);
        }

        public abstract double executeDouble(Node inliningTarget, SequenceStorage s, int idx) throws UnexpectedResultException;

        public final double executeDoubleCached(SequenceStorage s, int idx) throws UnexpectedResultException {
            return executeDouble(null, s, idx);
        }

        @Specialization
        protected static boolean doBoolean(BoolSequenceStorage storage, int idx) {
            return storage.getBoolItemNormalized(idx);
        }

        @Specialization
        protected static int doByte(ByteSequenceStorage storage, int idx) {
            return storage.getIntItemNormalized(idx);
        }

        @Specialization
        protected static int doInt(IntSequenceStorage storage, int idx) {
            return storage.getIntItemNormalized(idx);
        }

        @Specialization
        protected static long doLong(LongSequenceStorage storage, int idx) {
            return storage.getLongItemNormalized(idx);
        }

        @Specialization
        protected static double doDouble(DoubleSequenceStorage storage, int idx) {
            return storage.getDoubleItemNormalized(idx);
        }

        @Specialization
        protected static Object doObject(ObjectSequenceStorage storage, int idx) {
            return storage.getObjectItemNormalized(idx);
        }

        @Specialization
        protected static int doArrowInt(IntArrowSequenceStorage storage, int idx) {
            return storage.getIntItemNormalized(idx);
        }

        @Specialization
        protected static Object doMro(MroSequenceStorage storage, int idx) {
            return storage.getPythonClassItemNormalized(idx);
        }

        @InliningCutoff
        @Specialization
        protected static Object doNative(NativeSequenceStorage storage, int idx,
                        @Cached(inline = false) GetNativeItemScalarNode getItem) {
            return getItem.execute(storage, idx);
        }
    }

    @GenerateUncached
    @ImportStatic(SequenceStorageBaseNode.class)
    @SuppressWarnings("truffle-inlining")       // footprint reduction 48 -> 30
    protected abstract static class GetNativeItemScalarNode extends Node {
        public abstract Object execute(NativeSequenceStorage s, int idx);

        @Specialization
        protected static Object doNativeObject(NativeObjectSequenceStorage storage, int idx,
                        @Cached CStructAccess.ReadPointerNode readNode,
                        @Cached NativeToPythonNode toJavaNode) {
            return toJavaNode.execute(readNode.readArrayElement(storage.getPtr(), idx));
        }

        @Specialization
        protected static int doNativeByte(NativeByteSequenceStorage storage, int idx,
                        @Cached CStructAccess.ReadByteNode readNode) {
            return readNode.readArrayElement(storage.getPtr(), idx) & 0xff;
        }
    }

    @GenerateUncached
    @ImportStatic({SequenceStorage.StorageType.class, SequenceStorageBaseNode.class})
    @SuppressWarnings("truffle-inlining")       // footprint reduction 40 -> 21
    public abstract static class GetItemSliceNode extends Node {

        public abstract SequenceStorage execute(SequenceStorage s, int start, int stop, int step, int length);

        @Specialization
        @SuppressWarnings("unused")
        protected static EmptySequenceStorage doEmpty(EmptySequenceStorage storage, int start, int stop, int step, int length) {
            return EmptySequenceStorage.INSTANCE;
        }

        @Specialization
        protected static SequenceStorage doIntSequenceStorage(IntSequenceStorage storage, int start, int stop, int step, int length) {
            int[] newArray = new int[length];
            int[] values = storage.getInternalIntArray();

            if (step == 1) {
                PythonUtils.arraycopy(values, start, newArray, 0, length);
                return new IntSequenceStorage(newArray);
            }

            for (int i = start, j = 0; j < length; i += step, j++) {
                newArray[j] = values[i];
            }

            return new IntSequenceStorage(newArray);
        }

        @Specialization
        protected static SequenceStorage doLongSequenceStorage(LongSequenceStorage storage, int start, int stop, int step, int length) {
            long[] newArray = new long[length];
            long[] values = storage.getInternalLongArray();

            if (step == 1) {
                PythonUtils.arraycopy(values, start, newArray, 0, length);
                return new LongSequenceStorage(newArray);
            }

            for (int i = start, j = 0; j < length; i += step, j++) {
                newArray[j] = values[i];
            }

            return new LongSequenceStorage(newArray);
        }

        @Specialization
        protected static SequenceStorage doDoubleSequenceStorage(DoubleSequenceStorage storage, int start, int stop, int step, int length) {
            double[] newArray = new double[length];
            double[] values = storage.getInternalDoubleArray();

            if (step == 1) {
                PythonUtils.arraycopy(values, start, newArray, 0, length);
                return new DoubleSequenceStorage(newArray);
            }

            for (int i = start, j = 0; j < length; i += step, j++) {
                newArray[j] = values[i];
            }

            return new DoubleSequenceStorage(newArray);
        }

        @Specialization
        protected static SequenceStorage doBoolSequenceStorage(BoolSequenceStorage storage, int start, int stop, int step, int length) {
            boolean[] newArray = new boolean[length];
            boolean[] values = storage.getInternalBoolArray();

            if (step == 1) {
                PythonUtils.arraycopy(values, start, newArray, 0, length);
                return new BoolSequenceStorage(newArray);
            }

            for (int i = start, j = 0; j < length; i += step, j++) {
                newArray[j] = values[i];
            }

            return new BoolSequenceStorage(newArray);
        }

        @Specialization
        protected static SequenceStorage doByteSequenceStorage(ByteSequenceStorage storage, int start, int stop, int step, int length) {
            byte[] newArray = new byte[length];
            byte[] values = storage.getInternalByteArray();

            if (step == 1) {
                PythonUtils.arraycopy(values, start, newArray, 0, length);
                return new ByteSequenceStorage(newArray);
            }

            for (int i = start, j = 0; j < length; i += step, j++) {
                newArray[j] = values[i];
            }

            return new ByteSequenceStorage(newArray);
        }

        @Specialization
        protected static SequenceStorage doObjectSequenceStorage(ObjectSequenceStorage storage, int start, int stop, int step, int length) {
            Object[] newArray = new Object[length];
            Object[] values = storage.getInternalObjectArray();

            if (step == 1) {
                PythonUtils.arraycopy(values, start, newArray, 0, length);
                return new ObjectSequenceStorage(newArray);
            }

            for (int i = start, j = 0; j < length; i += step, j++) {
                newArray[j] = values[i];
            }

            return new ObjectSequenceStorage(newArray);
        }

        @Specialization
        protected static SequenceStorage doMroSequenceStorage(MroSequenceStorage storage, int start, int stop, int step, int length) {
            PythonAbstractClass[] newArray = new PythonAbstractClass[length];
            PythonAbstractClass[] values = storage.getInternalClassArray();

            if (step == 1) {
                PythonUtils.arraycopy(values, start, newArray, 0, length);
                return new MroSequenceStorage(storage.getClassName(), newArray);
            }

            for (int i = start, j = 0; j < length; i += step, j++) {
                newArray[j] = values[i];
            }

            return new ObjectSequenceStorage(newArray);
        }

        @Specialization
        protected static SequenceStorage doArrowInt(IntArrowSequenceStorage storage, int start, int stop, int step, int length,
                        @Bind("this") Node node) {
            var newBuffer = doNativeSliceInBound(node, start, step, length, storage);
            return new IntArrowSequenceStorage(newBuffer, length);
        }

        @Specialization
        protected static SequenceStorage doNativeByte(NativeByteSequenceStorage storage, int start, @SuppressWarnings("unused") int stop, int step, int length,
                        @Cached CStructAccess.ReadByteNode readNode) {

            byte[] newArray = new byte[length];
            for (int i = start, j = 0; j < length; i += step, j++) {
                newArray[j] = readNode.readArrayElement(storage.getPtr(), i);
            }
            return new ByteSequenceStorage(newArray);
        }

        @Specialization
        protected static SequenceStorage doNativeObject(NativeObjectSequenceStorage storage, int start, @SuppressWarnings("unused") int stop, int step, int length,
                        @Cached CStructAccess.ReadPointerNode readNode,
                        @Cached NativeToPythonNode toJavaNode) {
            Object[] newArray = new Object[length];
            for (int i = start, j = 0; j < length; i += step, j++) {
                newArray[j] = toJavaNode.execute(readNode.readArrayElement(storage.getPtr(), i));
            }
            return new ObjectSequenceStorage(newArray);
        }

        private static NativeBuffer doNativeSliceInBound(Node node, int start, int step, int sliceLength, ArrowSequenceStorage storage) {
            long typeWidth = storage.getTypeWidth();
            var context = PythonContext.get(node);
            var unsafe = context.getUnsafe();
            long sizeInBytes = sliceLength * typeWidth;
            var destNativeBuffer = context.nativeBufferContext.createNativeBuffer(sizeInBytes);

            if (step == 1) {
                var startAddress = storage.getNativeBuffer().getMemoryAddress() + (start * typeWidth);
                unsafe.copyMemory(startAddress, destNativeBuffer.getMemoryAddress(), sizeInBytes);
                return destNativeBuffer;
            }

            var stepInBytes = step * typeWidth;
            for (long srcAddr = storage.getNativeBuffer().getMemoryAddress() + (start * typeWidth), destAddr = destNativeBuffer.getMemoryAddress(),
                            j = 0; j < sliceLength; srcAddr += stepInBytes, destAddr += typeWidth, j++) {
                unsafe.copyMemory(srcAddr, destAddr, typeWidth);
            }

            return destNativeBuffer;
        }

        @NeverDefault
        public static GetItemSliceNode create() {
            return GetItemSliceNodeGen.create();
        }

        public static GetItemSliceNode getUncached() {
            return GetItemSliceNodeGen.getUncached();
        }
    }

    @GenerateUncached
    @ImportStatic(PGuards.class)
    @SuppressWarnings("truffle-inlining")       // footprint reduction 40 -> 23
    public abstract static class SetItemDynamicNode extends Node {

        public abstract SequenceStorage execute(Frame frame, GenNodeSupplier generalizationNodeProvider, SequenceStorage s, Object key, Object value);

        @Specialization
        protected static SequenceStorage doScalarInt(GenNodeSupplier generalizationNodeProvider, SequenceStorage storage, int idx, Object value,
                        @Bind("this") Node inliningTarget,
                        @Shared("generalizeProfile") @Cached InlinedBranchProfile generalizeProfile,
                        @Shared("setItemScalarNode") @Cached SetItemScalarNode setItemScalarNode,
                        @Shared("doGenNode") @Cached DoGeneralizationNode doGenNode,
                        @Shared("normalizeNode") @Cached NormalizeIndexCustomMessageNode normalizeNode) {
            int normalized = normalizeNode.execute(idx, storage.length(), ErrorMessages.INDEX_OUT_OF_RANGE);
            try {
                setItemScalarNode.execute(inliningTarget, storage, normalized, value);
                return storage;
            } catch (SequenceStoreException e) {
                generalizeProfile.enter(inliningTarget);
                SequenceStorage generalized = doGenNode.execute(inliningTarget, generalizationNodeProvider, storage, e.getIndicationValue());
                try {
                    setItemScalarNode.execute(inliningTarget, generalized, normalized, value);
                } catch (SequenceStoreException e1) {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    throw new IllegalStateException();
                }
                return generalized;
            }
        }

        @Specialization
        protected static SequenceStorage doScalarLong(GenNodeSupplier generalizationNodeProvider, SequenceStorage storage, long idx, Object value,
                        @Bind("this") Node inliningTarget,
                        @Shared("generalizeProfile") @Cached InlinedBranchProfile generalizeProfile,
                        @Shared("setItemScalarNode") @Cached SetItemScalarNode setItemScalarNode,
                        @Shared("doGenNode") @Cached DoGeneralizationNode doGenNode,
                        @Shared("normalizeNode") @Cached NormalizeIndexCustomMessageNode normalizeNode) {
            int normalized = normalizeNode.execute(idx, storage.length(), ErrorMessages.INDEX_OUT_OF_RANGE);
            try {
                setItemScalarNode.execute(inliningTarget, storage, normalized, value);
                return storage;
            } catch (SequenceStoreException e) {
                generalizeProfile.enter(inliningTarget);
                SequenceStorage generalized = doGenNode.execute(inliningTarget, generalizationNodeProvider, storage, e.getIndicationValue());
                setItemScalarNode.execute(inliningTarget, generalized, normalized, value);
                return generalized;
            }
        }

        @Specialization
        protected static SequenceStorage doScalarPInt(GenNodeSupplier generalizationNodeProvider, SequenceStorage storage, PInt idx, Object value,
                        @Bind("this") Node inliningTarget,
                        @Shared("generalizeProfile") @Cached InlinedBranchProfile generalizeProfile,
                        @Shared("setItemScalarNode") @Cached SetItemScalarNode setItemScalarNode,
                        @Shared("doGenNode") @Cached DoGeneralizationNode doGenNode,
                        @Shared("normalizeNode") @Cached NormalizeIndexCustomMessageNode normalizeNode) {
            int normalized = normalizeNode.execute(idx, storage.length(), ErrorMessages.INDEX_OUT_OF_RANGE);
            try {
                setItemScalarNode.execute(inliningTarget, storage, normalized, value);
                return storage;
            } catch (SequenceStoreException e) {
                generalizeProfile.enter(inliningTarget);
                SequenceStorage generalized = doGenNode.execute(inliningTarget, generalizationNodeProvider, storage, e.getIndicationValue());
                setItemScalarNode.execute(inliningTarget, generalized, normalized, value);
                return generalized;
            }
        }

        @Specialization(guards = "!isPSlice(idx)")
        protected static SequenceStorage doScalarGeneric(GenNodeSupplier generalizationNodeProvider, SequenceStorage storage, Object idx, Object value,
                        @Bind("this") Node inliningTarget,
                        @Shared("generalizeProfile") @Cached InlinedBranchProfile generalizeProfile,
                        @Shared("setItemScalarNode") @Cached SetItemScalarNode setItemScalarNode,
                        @Shared("doGenNode") @Cached DoGeneralizationNode doGenNode,
                        @Shared("normalizeNode") @Cached NormalizeIndexCustomMessageNode normalizeNode) {
            int normalized = normalizeNode.execute(idx, storage.length(), ErrorMessages.INDEX_OUT_OF_RANGE);
            try {
                setItemScalarNode.execute(inliningTarget, storage, normalized, value);
                return storage;
            } catch (SequenceStoreException e) {
                generalizeProfile.enter(inliningTarget);
                SequenceStorage generalized = doGenNode.execute(inliningTarget, generalizationNodeProvider, storage, e.getIndicationValue());
                setItemScalarNode.execute(inliningTarget, generalized, normalized, value);
                return generalized;
            }
        }

        @Specialization
        protected static SequenceStorage doSlice(VirtualFrame frame, GenNodeSupplier generalizationNodeProvider, SequenceStorage storage, PSlice slice, Object iterable,
                        @Bind("this") Node inliningTarget,
                        @Exclusive @Cached InlinedBranchProfile generalizeProfile,
                        @Cached SetItemSliceNode setItemSliceNode,
                        @Exclusive @Cached DoGeneralizationNode doGenNode,
                        @Cached ListNodes.ConstructListNode constructListNode,
                        @Cached CoerceToIntSlice sliceCast,
                        @Cached ComputeIndices compute) {
            SliceInfo info = compute.execute(frame, sliceCast.execute(inliningTarget, slice), storage.length());
            // We need to construct the list eagerly because if a SequenceStoreException occurs, we
            // must not use iterable again. It could have side-effects.
            PList values = constructListNode.execute(frame, iterable);
            try {
                setItemSliceNode.execute(frame, inliningTarget, storage, info, values);
                return storage;
            } catch (SequenceStoreException e) {
                generalizeProfile.enter(inliningTarget);
                SequenceStorage generalized = doGenNode.execute(inliningTarget, generalizationNodeProvider, storage, e.getIndicationValue());
                setItemSliceNode.execute(frame, inliningTarget, generalized, info, values);
                return generalized;
            }
        }

        public static SetItemDynamicNode getUncached() {
            return SetItemDynamicNodeGen.getUncached();
        }
    }

    @GenerateUncached
    @GenerateInline
    @GenerateCached(false)
    abstract static class DoGeneralizationNode extends Node {

        public abstract SequenceStorage execute(Node inliningTarget, GenNodeSupplier supplier, SequenceStorage storage, Object value);

        @Specialization(guards = "supplier == cachedSupplier", limit = "1")
        static SequenceStorage doCached(@SuppressWarnings("unused") GenNodeSupplier supplier, SequenceStorage storage, Object value,
                        @Cached("supplier") @SuppressWarnings("unused") GenNodeSupplier cachedSupplier,
                        @Cached(value = "supplier.create()", uncached = "supplier.getUncached()", inline = false) GeneralizationNode genNode) {

            return genNode.executeCached(storage, value);
        }

        @Specialization(replaces = "doCached")
        static SequenceStorage doUncached(GenNodeSupplier supplier, SequenceStorage storage, Object value) {
            return supplier.getUncached().executeCached(storage, value);
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

        protected abstract SequenceStorage execute(VirtualFrame frame, SequenceStorage s, int key, Object value);

        public final SequenceStorage execute(SequenceStorage s, int key, Object value) {
            return execute(null, s, key, value);
        }

        protected abstract SequenceStorage execute(VirtualFrame frame, SequenceStorage s, int key, int value);

        public final SequenceStorage execute(SequenceStorage s, int key, int value) {
            return execute(null, s, key, value);
        }

        protected abstract SequenceStorage execute(VirtualFrame frame, SequenceStorage s, int key, double value);

        public final SequenceStorage execute(SequenceStorage s, int key, double value) {
            return execute(null, s, key, value);
        }

        @Specialization
        protected SequenceStorage doScalarInt(IntSequenceStorage storage, int idx, int value) {
            int normalized = normalizeIndex(idx, storage);
            storage.setIntItemNormalized(normalized, value);
            return storage;
        }

        @Specialization
        protected SequenceStorage doScalarInt(DoubleSequenceStorage storage, int idx, double value) {
            int normalized = normalizeIndex(idx, storage);
            storage.setDoubleItemNormalized(normalized, value);
            return storage;
        }

        @Specialization
        @InliningCutoff
        protected SequenceStorage doScalarInt(SequenceStorage storage, int idx, Object value,
                        @Bind("this") Node inliningTarget,
                        @Shared("generalizeProfile") @Cached InlinedBranchProfile generalizeProfile,
                        @Shared("setItemScalarNode") @Cached SetItemScalarNode setItemScalarNode) {
            int normalized = normalizeIndex(idx, storage);
            try {
                setItemScalarNode.execute(inliningTarget, storage, normalized, value);
                return storage;
            } catch (SequenceStoreException e) {
                generalizeProfile.enter(inliningTarget);
                SequenceStorage generalized = generalizeStore(storage, e.getIndicationValue());
                try {
                    setItemScalarNode.execute(inliningTarget, generalized, normalized, value);
                } catch (SequenceStoreException e1) {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    throw new IllegalStateException();
                }
                return generalized;
            }
        }

        @Specialization
        @InliningCutoff
        @SuppressWarnings("truffle-static-method")
        protected SequenceStorage doScalarLong(VirtualFrame frame, SequenceStorage storage, long idx, Object value,
                        @Bind("this") Node inliningTarget,
                        @Shared @Cached PyNumberAsSizeNode asSizeNode,
                        @Shared("generalizeProfile") @Cached InlinedBranchProfile generalizeProfile,
                        @Shared("setItemScalarNode") @Cached SetItemScalarNode setItemScalarNode) {
            int normalized = normalizeIndex(frame, inliningTarget, asSizeNode, idx, storage);
            try {
                setItemScalarNode.execute(inliningTarget, storage, normalized, value);
                return storage;
            } catch (SequenceStoreException e) {
                generalizeProfile.enter(inliningTarget);
                SequenceStorage generalized = generalizeStore(storage, e.getIndicationValue());
                setItemScalarNode.execute(inliningTarget, generalized, normalized, value);
                return generalized;
            }
        }

        @Specialization
        @InliningCutoff
        protected SequenceStorage doScalarPInt(VirtualFrame frame, SequenceStorage storage, PInt idx, Object value,
                        @Bind("this") Node inliningTarget,
                        @Shared @Cached PyNumberAsSizeNode asSizeNode,
                        @Shared("generalizeProfile") @Cached InlinedBranchProfile generalizeProfile,
                        @Shared("setItemScalarNode") @Cached SetItemScalarNode setItemScalarNode) {
            int normalized = normalizeIndex(frame, inliningTarget, asSizeNode, idx, storage);
            try {
                setItemScalarNode.execute(inliningTarget, storage, normalized, value);
                return storage;
            } catch (SequenceStoreException e) {
                generalizeProfile.enter(inliningTarget);
                SequenceStorage generalized = generalizeStore(storage, e.getIndicationValue());
                setItemScalarNode.execute(inliningTarget, generalized, normalized, value);
                return generalized;
            }
        }

        @Specialization(guards = "!isPSlice(idx)")
        @InliningCutoff
        protected SequenceStorage doScalarGeneric(VirtualFrame frame, SequenceStorage storage, Object idx, Object value,
                        @Bind("this") Node inliningTarget,
                        @Shared @Cached PyNumberAsSizeNode asSizeNode,
                        @Shared("generalizeProfile") @Cached InlinedBranchProfile generalizeProfile,
                        @Shared("setItemScalarNode") @Cached SetItemScalarNode setItemScalarNode) {
            int normalized = normalizeIndex(frame, inliningTarget, asSizeNode, idx, storage);
            try {
                setItemScalarNode.execute(inliningTarget, storage, normalized, value);
                return storage;
            } catch (SequenceStoreException e) {
                generalizeProfile.enter(inliningTarget);
                SequenceStorage generalized = generalizeStore(storage, e.getIndicationValue());
                setItemScalarNode.execute(inliningTarget, generalized, normalized, value);
                return generalized;
            }
        }

        @Specialization
        protected SequenceStorage doSliceSequence(VirtualFrame frame, SequenceStorage storage, PSlice slice, PSequence sequence,
                        @Bind("this") Node inliningTarget,
                        @Shared("generalizeProfile") @Cached InlinedBranchProfile generalizeProfile,
                        @Shared @Cached SetItemSliceNode setItemSliceNode,
                        @Shared @Cached CoerceToIntSlice sliceCast,
                        @Shared @Cached SliceNodes.SliceUnpack unpack,
                        @Shared @Cached SliceNodes.AdjustIndices adjustIndices) {
            SliceInfo unadjusted = unpack.execute(inliningTarget, sliceCast.execute(inliningTarget, slice));
            int len = storage.length();
            SliceInfo info = adjustIndices.execute(inliningTarget, len, unadjusted);
            try {
                setItemSliceNode.execute(frame, inliningTarget, storage, info, sequence, true);
                return storage;
            } catch (SequenceStoreException e) {
                return generalize(frame, storage, sequence, inliningTarget, generalizeProfile, setItemSliceNode, info, e);
            }
        }

        @Specialization(replaces = "doSliceSequence")
        @InliningCutoff
        protected SequenceStorage doSliceGeneric(VirtualFrame frame, SequenceStorage storage, PSlice slice, Object iterable,
                        @Bind("this") Node inliningTarget,
                        @Shared("generalizeProfile") @Cached InlinedBranchProfile generalizeProfile,
                        @Shared @Cached SetItemSliceNode setItemSliceNode,
                        @Cached ListNodes.ConstructListNode constructListNode,
                        @Shared @Cached CoerceToIntSlice sliceCast,
                        @Shared @Cached SliceNodes.SliceUnpack unpack,
                        @Shared @Cached SliceNodes.AdjustIndices adjustIndices) {
            SliceInfo unadjusted = unpack.execute(inliningTarget, sliceCast.execute(inliningTarget, slice));
            int len = storage.length();
            SliceInfo info = adjustIndices.execute(inliningTarget, len, unadjusted);

            // We need to construct the list eagerly because if a SequenceStoreException occurs, we
            // must not use iterable again. It could have sice-effects.
            PList values = constructListNode.execute(frame, iterable);
            try {
                setItemSliceNode.execute(frame, inliningTarget, storage, info, values, true);
                return storage;
            } catch (SequenceStoreException e) {
                return generalize(frame, storage, values, inliningTarget, generalizeProfile, setItemSliceNode, info, e);
            }
        }

        @InliningCutoff
        private SequenceStorage generalize(VirtualFrame frame, SequenceStorage storage, PSequence sequence, Node inliningTarget, InlinedBranchProfile generalizeProfile,
                        SetItemSliceNode setItemSliceNode, SliceInfo info, SequenceStoreException e) {
            generalizeProfile.enter(inliningTarget);
            SequenceStorage generalized = generalizeStore(storage, e.getIndicationValue());
            setItemSliceNode.execute(frame, inliningTarget, generalized, info, sequence, false);
            return generalized;
        }

        private SequenceStorage generalizeStore(SequenceStorage storage, Object value) {
            if (generalizationNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                generalizationNode = insert(generalizationNodeProvider.get());
            }
            return generalizationNode.executeCached(storage, value);
        }

        @NeverDefault
        public static SetItemNode create(NormalizeIndexNode normalizeIndexNode, Supplier<GeneralizationNode> generalizationNodeProvider) {
            return SetItemNodeGen.create(normalizeIndexNode, generalizationNodeProvider);
        }

        @NeverDefault
        public static SetItemNode create(NormalizeIndexNode normalizeIndexNode, TruffleString invalidItemErrorMessage) {
            return SetItemNodeGen.create(normalizeIndexNode, () -> NoGeneralizationCustomMessageNode.create(invalidItemErrorMessage));
        }

        @NeverDefault
        public static SetItemNode create(TruffleString invalidItemErrorMessage) {
            return SetItemNodeGen.create(NormalizeIndexNode.create(), () -> NoGeneralizationCustomMessageNode.create(invalidItemErrorMessage));
        }

        @NeverDefault
        public static SequenceStorageNodes.SetItemNode createForList() {
            return SequenceStorageNodes.SetItemNode.create(NormalizeIndexNode.forListAssign(), ListGeneralizationNode::create);
        }
    }

    @GenerateUncached
    @GenerateInline(value = true)
    @GenerateCached(false)
    @ImportStatic({SequenceStorageBaseNode.class, PGuards.class})
    public abstract static class AbstractSetItemScalarNode extends Node {

        public abstract void execute(Node inliningTarget, SequenceStorage s, int idx, Object value);

        @Specialization
        protected static void doBoolean(@SuppressWarnings("unused") Node inliningTarget, BoolSequenceStorage storage, int idx, boolean value) {
            storage.setBoolItemNormalized(idx, value);
        }

        @Specialization
        protected static void doByteSimple(@SuppressWarnings("unused") Node inliningTarget, ByteSequenceStorage storage, int idx, byte value) {
            storage.setByteItemNormalized(idx, value);
        }

        @InliningCutoff
        @Specialization(replaces = "doByteSimple")
        protected static void doByte(@SuppressWarnings("unused") Node inliningTarget, ByteSequenceStorage storage, int idx, Object value,
                        @Cached(inline = false) CastToByteNode castToByteNode) {
            // TODO: clean this up, we really might need a frame
            storage.setByteItemNormalized(idx, castToByteNode.execute(null, value));
        }

        @Specialization
        protected static void doInt(@SuppressWarnings("unused") Node inliningTarget, IntSequenceStorage storage, int idx, int value) {
            storage.setIntItemNormalized(idx, value);
        }

        @Specialization
        protected static void doArrowInt(@SuppressWarnings("unused") Node inliningTarget, IntArrowSequenceStorage storage, int idx, int value) {
            storage.setIntItemNormalized(idx, value);
        }

        @Specialization(rewriteOn = OverflowException.class)
        protected static void doIntL(@SuppressWarnings("unused") Node inliningTarget, IntSequenceStorage storage, int idx, long value) throws OverflowException {
            storage.setIntItemNormalized(idx, PInt.intValueExact(value));
        }

        @InliningCutoff
        @Specialization(replaces = "doIntL")
        protected static void doIntLOvf(@SuppressWarnings("unused") Node inliningTarget, IntSequenceStorage storage, int idx, long value) {
            try {
                storage.setIntItemNormalized(idx, PInt.intValueExact(value));
            } catch (OverflowException e) {
                throw new SequenceStoreException(value);
            }
        }

        @InliningCutoff
        @Specialization(guards = "!isNativeWrapper(value)")
        protected static void doInt(@SuppressWarnings("unused") Node inliningTarget, IntSequenceStorage storage, int idx, PInt value) {
            try {
                storage.setIntItemNormalized(idx, value.intValueExact());
            } catch (OverflowException e) {
                throw new SequenceStoreException(value);
            }
        }

        @Specialization
        protected static void doLong(@SuppressWarnings("unused") Node inliningTarget, LongSequenceStorage storage, int idx, long value) {
            storage.setLongItemNormalized(idx, value);
        }

        @Specialization
        protected static void doLong(@SuppressWarnings("unused") Node inliningTarget, LongSequenceStorage storage, int idx, int value) {
            storage.setLongItemNormalized(idx, value);
        }

        @InliningCutoff
        @Specialization(guards = "!isNativeWrapper(value)")
        protected static void doLong(@SuppressWarnings("unused") Node inliningTarget, LongSequenceStorage storage, int idx, PInt value) {
            try {
                storage.setLongItemNormalized(idx, value.longValueExact());
            } catch (OverflowException e) {
                throw new SequenceStoreException(value);
            }
        }

        @Specialization
        protected static void doDouble(@SuppressWarnings("unused") Node inliningTarget, DoubleSequenceStorage storage, int idx, double value) {
            storage.setDoubleItemNormalized(idx, value);
        }

        @Specialization
        protected static void doObject(@SuppressWarnings("unused") Node inliningTarget, ObjectSequenceStorage storage, int idx, Object value) {
            storage.setObjectItemNormalized(idx, value);
        }

        @Fallback
        @SuppressWarnings("unused")
        static void doError(@SuppressWarnings("unused") Node inliningTarget, SequenceStorage s, int idx, Object item) {
            throw new SequenceStoreException(item);
        }
    }

    @GenerateUncached
    @GenerateInline
    @GenerateCached(false)
    public abstract static class SetItemScalarNode extends AbstractSetItemScalarNode {

        @InliningCutoff
        @Specialization
        protected static void doNative(NativeSequenceStorage storage, int idx, Object value,
                        @Cached(inline = false) SetNativeItemScalarNode setItem) {
            setItem.execute(storage, idx, value);
        }
    }

    @GenerateUncached
    @GenerateInline
    @GenerateCached(false)
    public abstract static class SetItemScalarGeneralizingNode extends Node {
        public abstract SequenceStorage execute(Node inliningTarget, SequenceStorage storage, int idx, Object value, GenNodeSupplier genNodeSupplier);

        @Specialization
        static SequenceStorage set(Node inliningTarget, SequenceStorage storage, int idx, Object value, GenNodeSupplier genNodeSupplier,
                        @Cached InlinedBranchProfile generalizeProfile,
                        @Cached SetItemScalarNode setItemScalarNode,
                        @Cached DoGeneralizationNode doGeneralizationNode) {
            try {
                setItemScalarNode.execute(inliningTarget, storage, idx, value);
                return storage;
            } catch (SequenceStoreException e) {
                generalizeProfile.enter(inliningTarget);
                SequenceStorage generalized = doGeneralizationNode.execute(inliningTarget, genNodeSupplier, storage, e.getIndicationValue());
                try {
                    setItemScalarNode.execute(inliningTarget, generalized, idx, value);
                } catch (SequenceStoreException e1) {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    throw new IllegalStateException();
                }
                return generalized;
            }
        }
    }

    @GenerateUncached
    @GenerateInline
    @GenerateCached(false)
    public abstract static class InitializeItemScalarNode extends AbstractSetItemScalarNode {

        @InliningCutoff
        @Specialization
        protected static void doNative(NativeSequenceStorage storage, int idx, Object value,
                        @Cached(inline = false) InitializeNativeItemScalarNode initializeItem) {
            initializeItem.execute(storage, idx, value);
        }
    }

    @GenerateUncached
    @ImportStatic(SequenceStorageBaseNode.class)
    @SuppressWarnings("truffle-inlining")       // footprint reduction 36 -> 18
    public abstract static class SetNativeItemScalarNode extends Node {
        public abstract void execute(NativeSequenceStorage s, int idx, Object value);

        @Specialization
        protected static void doNativeByte(NativeByteSequenceStorage storage, int idx, Object value,
                        @Cached CStructAccess.WriteByteNode writeNode,
                        @Cached CastToByteNode castToByteNode) {
            writeNode.writeArrayElement(storage.getPtr(), idx, castToByteNode.execute(null, value));
        }

        @Specialization
        protected static void doNativeObject(NativeObjectSequenceStorage storage, int idx, Object value,
                        @Bind("this") Node inliningTarget,
                        @Cached PythonToNativeNewRefNode toNative,
                        @Cached CStructAccess.ReadPointerNode readPointerNode,
                        @Cached CStructAccess.WritePointerNode writePointerNode,
                        @Cached CExtNodes.XDecRefPointerNode decRefPointerNode) {
            Object old = readPointerNode.readArrayElement(storage.getPtr(), idx);
            writePointerNode.writeArrayElement(storage.getPtr(), idx, toNative.execute(value));
            decRefPointerNode.execute(inliningTarget, old);
        }
    }

    @GenerateUncached
    @ImportStatic(SequenceStorageBaseNode.class)
    @SuppressWarnings("truffle-inlining")       // footprint reduction 36 -> 18
    public abstract static class InitializeNativeItemScalarNode extends Node {
        public abstract void execute(NativeSequenceStorage s, int idx, Object value);

        @Specialization
        protected static void doNativeByte(NativeByteSequenceStorage storage, int idx, Object value,
                        @Cached CStructAccess.WriteByteNode writeNode,
                        @Cached CastToByteNode castToByteNode) {
            writeNode.writeArrayElement(storage.getPtr(), idx, castToByteNode.execute(null, value));
        }

        @Specialization
        protected static void doNativeObject(NativeObjectSequenceStorage storage, int idx, Object value,
                        @Cached CStructAccess.WritePointerNode writePointerNode,
                        @Cached PythonToNativeNewRefNode toNative) {
            writePointerNode.writeArrayElement(storage.getPtr(), idx, toNative.execute(value));
        }
    }

    @GenerateUncached
    @GenerateInline
    @GenerateCached(false)
    @ImportStatic({StorageType.class, SequenceStorageBaseNode.class})
    public abstract static class SetItemSliceNode extends Node {

        public abstract void execute(Frame frame, Node inliningTarget, SequenceStorage s, SliceInfo info, Object iterable, boolean canGeneralize);

        public final void execute(Frame frame, Node inliningTarget, SequenceStorage s, SliceInfo info, Object iterable) {
            execute(frame, inliningTarget, s, info, iterable, true);
        }

        @Specialization(guards = "hasStorage(seq)")
        static void doStorage(Node inliningTarget, SequenceStorage s, SliceInfo info, PSequence seq, boolean canGeneralize,
                        @Shared("setStorageSliceNode") @Cached(inline = false) SetStorageSliceNode setStorageSliceNode,
                        @Cached GetSequenceStorageNode getSequenceStorageNode) {
            setStorageSliceNode.execute(s, info, getSequenceStorageNode.execute(inliningTarget, seq), canGeneralize);
        }

        @Specialization
        @InliningCutoff
        static void doGeneric(VirtualFrame frame, SequenceStorage s, SliceInfo info, Object iterable, boolean canGeneralize,
                        @Shared("setStorageSliceNode") @Cached(inline = false) SetStorageSliceNode setStorageSliceNode,
                        @Cached(inline = false) ListNodes.ConstructListNode constructListNode) {
            PList list = constructListNode.execute(frame, iterable);
            setStorageSliceNode.execute(s, info, list.getSequenceStorage(), canGeneralize);
        }
    }

    @GenerateUncached
    @GenerateInline
    @GenerateCached(false)
    @ImportStatic(SequenceStorageBaseNode.class)
    public abstract static class MemMoveNode extends Node {

        public abstract void execute(Node inliningTarget, SequenceStorage s, int distPos, int srcPos, int length);

        public static void executeUncached(SequenceStorage s, int distPos, int srcPos, int length) {
            MemMoveNodeGen.getUncached().execute(null, s, distPos, srcPos, length);
        }

        @SuppressWarnings("unused")
        @Specialization(guards = "length <= 0")
        protected static void nothing(SequenceStorage storage, int distPos, int srcPos, int length) {
        }

        @Specialization(limit = "MAX_BASIC_STORAGES", guards = {"length > 0", "storage.getClass() == cachedClass"})
        protected static void doArrayBasedMove(ArrayBasedSequenceStorage storage, int distPos, int srcPos, int length,
                        @Cached("storage.getClass()") Class<? extends ArrayBasedSequenceStorage> cachedClass) {
            Object array = cachedClass.cast(storage).getInternalArrayObject();
            PythonUtils.arraycopy(array, srcPos, array, distPos, length);
        }

        @Specialization(guards = {"length > 0", "!isArrayBasedSequenceStorage(storage)"})
        protected static void doOther(Node inliningTarget, SequenceStorage storage, int distPos, int srcPos, int length,
                        @Cached SetItemScalarNode setLeftItemNode,
                        @Cached GetItemScalarNode getRightItemNode) {

            if (srcPos < distPos && srcPos + length > distPos) {
                // case where we override values which we later want to read, therefore we need to
                // copy from the end
                copyBackward(inliningTarget, storage, getRightItemNode, setLeftItemNode, distPos, srcPos, length);

            } else {
                // otherwise copying forwards
                copyForward(inliningTarget, storage, getRightItemNode, setLeftItemNode, distPos, srcPos, length);
            }
        }

        private static void copyForward(Node inliningTarget, SequenceStorage storage, GetItemScalarNode getRightItemNode, SetItemScalarNode setLeftItemNode, int destPos, int srcPos, int length) {
            while (length > 0) {
                var value = getRightItemNode.execute(inliningTarget, storage, srcPos);
                setLeftItemNode.execute(inliningTarget, storage, destPos, value);
                destPos++;
                srcPos++;
                length--;

            }
        }

        private static void copyBackward(Node inliningTarget, SequenceStorage storage, GetItemScalarNode getRightItemNode, SetItemScalarNode setLeftItemNode, int destPos, int srcPos, int length) {
            destPos += length;
            srcPos += length;
            while (length > 0) {
                destPos--;
                srcPos--;
                length--;
                var value = getRightItemNode.execute(inliningTarget, storage, srcPos);
                setLeftItemNode.execute(inliningTarget, storage, destPos, value);
            }
        }

        protected static boolean isArrayBasedSequenceStorage(Object o) {
            return o instanceof ArrayBasedSequenceStorage;
        }
    }

    @GenerateUncached
    @GenerateInline
    @GenerateCached(false)
    @ImportStatic(SequenceStorageBaseNode.class)
    public abstract static class MemCopyNode extends Node {

        public abstract void execute(Node inliningTarget, SequenceStorage dist, int distPos, SequenceStorage src, int srcPos, int length);

        @SuppressWarnings("unused")
        @Specialization(guards = "length <= 0")
        protected static void nothing(SequenceStorage dist, int distPos, SequenceStorage src, int srcPos, int length) {
        }

        @Specialization(limit = "MAX_BASIC_STORAGES", guards = {"length > 0", "dist.getClass() == cachedClass", "src.getClass() == dist.getClass()"})
        protected static void doArrayBasedCopy(ArrayBasedSequenceStorage dist, int distPos, ArrayBasedSequenceStorage src, int srcPos, int length,
                        @Cached("dist.getClass()") Class<? extends ArrayBasedSequenceStorage> cachedClass) {
            Object distArray = cachedClass.cast(dist).getInternalArrayObject();
            Object srcArray = cachedClass.cast(src).getInternalArrayObject();
            PythonUtils.arraycopy(srcArray, srcPos, distArray, distPos, length);
        }

        @Specialization(guards = {"length > 0", "!isArrayBasedSequenceStorage(dist) || dist.getClass() != src.getClass()"})
        protected static void doOther(Node inliningTarget, SequenceStorage dist, int distPos, SequenceStorage src, int srcPos, int length,
                        @Cached SetItemScalarNode setLeftItemNode,
                        @Cached GetItemScalarNode getRightItemNode) {
            for (int cur = distPos, j = srcPos, i = 0; i < length; cur += 1, j++, i++) {
                setLeftItemNode.execute(inliningTarget, dist, cur, getRightItemNode.execute(inliningTarget, src, j));
            }
        }

        protected static boolean isArrayBasedSequenceStorage(Object o) {
            return o instanceof ArrayBasedSequenceStorage;
        }
    }

    @GenerateUncached
    @GenerateInline
    @GenerateCached(false)
    @ImportStatic(SequenceStorageBaseNode.class)
    public abstract static class ReverseNode extends Node {

        public abstract void execute(Node inliningTarget, SequenceStorage storage);

        @Specialization
        static void doEmpty(@SuppressWarnings("unused") EmptySequenceStorage storage) {
        }

        @Specialization
        static void doIntStorage(IntSequenceStorage storage) {
            storage.reverse();
        }

        @Specialization
        static void doDoubleStorage(DoubleSequenceStorage storage) {
            storage.reverse();
        }

        @Specialization
        static void doLongStorage(LongSequenceStorage storage) {
            storage.reverse();
        }

        @Specialization
        static void doByteStorage(ByteSequenceStorage storage) {
            storage.reverse();
        }

        @Specialization
        static void doObjectStorage(ObjectSequenceStorage storage) {
            storage.reverse();
        }

        @Specialization
        static void doArrow(Node inliningTarget, ArrowSequenceStorage storage) {
            var length = storage.length();
            var unsafe = PythonContext.get(inliningTarget).getUnsafe();
            long typeWidth = storage.getTypeWidth();
            long startAddress = storage.getNativeBuffer().getMemoryAddress();
            long endAddress = startAddress + ((length - 1) * typeWidth);
            byte[] tempBuffer = new byte[(int) typeWidth];
            while (startAddress < endAddress) {
                unsafe.copyMemory(null, startAddress, tempBuffer, Unsafe.ARRAY_BYTE_BASE_OFFSET, typeWidth);
                unsafe.copyMemory(endAddress, startAddress, typeWidth);
                unsafe.copyMemory(tempBuffer, Unsafe.ARRAY_BYTE_BASE_OFFSET, null, endAddress, typeWidth);
                startAddress += typeWidth;
                endAddress -= typeWidth;
            }
        }

        @Specialization
        static void doBoolStorage(BoolSequenceStorage storage) {
            storage.reverse();
        }

        @Specialization
        static void doMroStorage(MroSequenceStorage storage) {
            throw CompilerDirectives.shouldNotReachHere();
        }

        @Specialization
        @InliningCutoff
        static void doNative(NativeSequenceStorage storage,
                        @Cached(inline = false) ReverseNativeNode reverseNativeNode) {
            reverseNativeNode.execute(storage);
        }

        @GenerateUncached
        @GenerateInline(false)
        abstract static class ReverseNativeNode extends Node {

            abstract void execute(NativeSequenceStorage storage);

            @Specialization
            static void doNativeByte(NativeByteSequenceStorage storage,
                            @Cached CStructAccess.ReadByteNode readByteNode,
                            @Cached CStructAccess.WriteByteNode writeByteNode) {
                int length = storage.length();
                if (length > 0) {
                    int head = 0;
                    int tail = length - 1;
                    int middle = (length - 1) / 2;
                    Object ptr = storage.getPtr();

                    for (; head <= middle; head++, tail--) {
                        byte temp = readByteNode.readArrayElement(ptr, head);
                        writeByteNode.writeArrayElement(ptr, head, readByteNode.readArrayElement(ptr, tail));
                        writeByteNode.writeArrayElement(ptr, tail, temp);
                    }
                }
            }

            @Specialization
            static void doNativeObject(NativeObjectSequenceStorage storage,
                            @Cached CStructAccess.ReadPointerNode readPointerNode,
                            @Cached CStructAccess.WritePointerNode writePointerNode) {
                int length = storage.length();
                if (length > 0) {
                    int head = 0;
                    int tail = length - 1;
                    int middle = (length - 1) / 2;
                    Object ptr = storage.getPtr();

                    for (; head <= middle; head++, tail--) {
                        Object temp = readPointerNode.readArrayElement(ptr, head);
                        writePointerNode.writeArrayElement(ptr, head, readPointerNode.readArrayElement(ptr, tail));
                        writePointerNode.writeArrayElement(ptr, tail, temp);
                    }
                }
            }
        }
    }

    @GenerateUncached
    @ImportStatic(SequenceStorageBaseNode.class)
    @SuppressWarnings("truffle-inlining")       // footprint reduction 92 -> 75
    abstract static class SetStorageSliceNode extends Node {

        public abstract void execute(SequenceStorage s, SliceInfo info, SequenceStorage iterable, boolean canGeneralize);

        @Specialization(limit = "MAX_BASIC_STORAGES", guards = {"self.getClass() == cachedClass", "self.getClass() == sequence.getClass()", "replacesWholeSequence(cachedClass, self, info)"})
        static void doWholeSequence(ArrayBasedSequenceStorage self, @SuppressWarnings("unused") SliceInfo info, ArrayBasedSequenceStorage sequence, @SuppressWarnings("unused") boolean canGeneralize,
                        @Cached("self.getClass()") Class<? extends ArrayBasedSequenceStorage> cachedClass) {
            ArrayBasedSequenceStorage selfProfiled = cachedClass.cast(self);
            ArrayBasedSequenceStorage otherProfiled = cachedClass.cast(sequence);
            selfProfiled.setInternalArrayObject(otherProfiled.getCopyOfInternalArrayObject());
            selfProfiled.setNewLength(otherProfiled.length());
            selfProfiled.minimizeCapacity();
        }

        @Specialization(guards = {"!canGeneralize || isDataTypeCompatibleNode.execute(inliningTarget, self, values)", "sinfo.step == 1"}, limit = "1")
        static void singleStep(SequenceStorage self, SliceInfo sinfo, SequenceStorage values, @SuppressWarnings("unused") boolean canGeneralize,
                        @Bind("this") Node inliningTarget,
                        @Exclusive @Cached @SuppressWarnings("unused") IsDataTypeCompatibleNode isDataTypeCompatibleNode,
                        @Exclusive @Cached SetLenNode setLenNode,
                        @Exclusive @Cached EnsureCapacityNode ensureCapacityNode,
                        @Exclusive @Cached MemMoveNode memove,
                        @Exclusive @Cached MemCopyNode memcpy,
                        @Exclusive @Cached CopyNode copyNode,
                        @Exclusive @Cached InlinedConditionProfile memoryError,
                        @Exclusive @Cached InlinedConditionProfile negGrowth,
                        @Exclusive @Cached InlinedConditionProfile posGrowth,
                        @Shared @Cached PRaiseNode raiseNode) {
            int start = sinfo.start;
            int stop = sinfo.stop;
            int step = sinfo.step;

            SequenceStorage data = (values == self) ? copyNode.execute(inliningTarget, values) : values;
            int needed = data.length();
            /*- Make sure b[5:2] = ... inserts before 5, not before 2. */
            if ((step < 0 && start < stop) || (step > 0 && start > stop)) {
                stop = start;
            }
            singleStep(self, start, stop, data, needed, inliningTarget, setLenNode, ensureCapacityNode, memove, memcpy, memoryError, negGrowth, posGrowth, raiseNode);
        }

        @Specialization(guards = {"!canGeneralize || isDataTypeCompatibleNode.execute(inliningTarget, self, values)", "sinfo.step != 1"}, limit = "1")
        static void multiStep(SequenceStorage self, SliceInfo sinfo, SequenceStorage values, @SuppressWarnings("unused") boolean canGeneralize,
                        @Bind("this") Node inliningTarget,
                        @Exclusive @Cached @SuppressWarnings("unused") IsDataTypeCompatibleNode isDataTypeCompatibleNode,
                        @Exclusive @Cached InlinedConditionProfile wrongLength,
                        @Exclusive @Cached InlinedConditionProfile deleteSlice,
                        @Exclusive @Cached SetLenNode setLenNode,
                        @Exclusive @Cached EnsureCapacityNode ensureCapacityNode,
                        @Exclusive @Cached MemMoveNode memove,
                        @Cached SetItemScalarNode setLeftItemNode,
                        @Cached GetItemScalarNode getRightItemNode,
                        @Shared @Cached PRaiseNode raiseNode,
                        @Exclusive @Cached CopyNode copyNode) {
            int start = sinfo.start;
            int step = sinfo.step;
            int slicelen = sinfo.sliceLength;
            assert slicelen != -1 : "slice info has not been adjusted";

            SequenceStorage data = (values == self) ? copyNode.execute(inliningTarget, values) : values;
            int needed = data.length();
            if (deleteSlice.profile(inliningTarget, needed == 0)) {
                DeleteSliceNode.multipleSteps(self, sinfo, inliningTarget, setLenNode, ensureCapacityNode, memove);
            } else {
                /*- Assign slice */
                if (wrongLength.profile(inliningTarget, needed != slicelen)) {
                    raiseNode.raise(ValueError, ErrorMessages.ATTEMPT_TO_ASSIGN_SEQ_OF_SIZE_TO_SLICE_OF_SIZE, needed, slicelen);
                }
                for (int cur = start, i = 0; i < slicelen; cur += step, i++) {
                    setLeftItemNode.execute(inliningTarget, self, cur, getRightItemNode.execute(inliningTarget, data, i));
                }
            }
        }

        @Specialization(guards = {"canGeneralize", "!isAssignCompatibleNode.execute(inliningTarget, self, sequence)"}, limit = "1")
        static void doError(@SuppressWarnings("unused") SequenceStorage self, @SuppressWarnings("unused") SliceInfo info, SequenceStorage sequence, @SuppressWarnings("unused") boolean canGeneralize,
                        @SuppressWarnings("unused") @Bind("this") Node inliningTarget,
                        @Cached @SuppressWarnings("unused") IsAssignCompatibleNode isAssignCompatibleNode) {
            throw new SequenceStoreException(sequence.getIndicativeValue());
        }

        /**
         * based on CPython/Objects/listobject.c#list_ass_subscript and
         * CPython/Objects/bytearrayobject.c#bytearray_ass_subscript
         */
        static void singleStep(SequenceStorage self, int lo, int hi, SequenceStorage data, int needed,
                        Node inliningTarget,
                        SetLenNode setLenNode,
                        EnsureCapacityNode ensureCapacityNode,
                        MemMoveNode memove,
                        MemCopyNode memcpy,
                        InlinedConditionProfile memoryError,
                        InlinedConditionProfile negGrowth,
                        InlinedConditionProfile posGrowth,
                        PRaiseNode raiseNode) {
            int avail = hi - lo;
            int growth = needed - avail;
            assert avail >= 0 : "sliceInfo.start and sliceInfo.stop have not been adjusted.";
            int len = self.length();

            if (negGrowth.profile(inliningTarget, growth < 0)) {
                // ensure capacity will check if the storage can be resized.
                ensureCapacityNode.execute(inliningTarget, self, len + growth);

                // We are shrinking the list here

                /*-
                TODO: it might be a good idea to implement this logic
                if (lo == 0)
                Shrink the buffer by advancing its logical start
                  0   lo               hi             old_size
                  |   |<----avail----->|<-----tail------>|
                  |      |<-bytes_len->|<-----tail------>|
                  0    new_lo         new_hi          new_size
                */

                // For now we are doing the generic approach
                /*-
                  0   lo               hi               old_size
                  |   |<----avail----->|<-----tomove------>|
                  |   |<-bytes_len->|<-----tomove------>|
                  0   lo         new_hi              new_size
                */
                memove.execute(inliningTarget, self, lo + needed, hi, len - hi);
                setLenNode.execute(inliningTarget, self, len + growth); // growth is negative
                // (Shrinking)
            } else if (posGrowth.profile(inliningTarget, growth > 0)) {
                // ensure capacity will check if the storage can be resized.
                ensureCapacityNode.execute(inliningTarget, self, len + growth);

                if (memoryError.profile(inliningTarget, len > Integer.MAX_VALUE - growth)) {
                    throw raiseNode.raise(MemoryError);
                }

                len += growth;
                setLenNode.execute(inliningTarget, self, len);

                /*- Make the place for the additional bytes */
                /*-
                  0   lo        hi               old_size
                  |   |<-avail->|<-----tomove------>|
                  |   |<---bytes_len-->|<-----tomove------>|
                  0   lo            new_hi              new_size
                 */
                memove.execute(inliningTarget, self, lo + needed, hi, len - lo - needed);
            }

            if (needed > 0) {
                memcpy.execute(inliningTarget, self, lo, data, 0, needed);
            }
        }

        protected static boolean replacesWholeSequence(Class<? extends ArrayBasedSequenceStorage> cachedClass, ArrayBasedSequenceStorage s, SliceInfo info) {
            return info.start == 0 && info.step == 1 && info.stop == cachedClass.cast(s).length();
        }
    }

    @GenerateUncached
    @GenerateInline
    @GenerateCached(false)
    public abstract static class StorageToNativeNode extends Node {

        public abstract NativeByteSequenceStorage executeBytes(Node inliningTarget, byte[] obj, int length, boolean createRef);

        public abstract NativeSequenceStorage execute(Node inliningTarget, Object obj, int length, boolean createRef);

        public final NativeByteSequenceStorage executeBytes(Node inliningTarget, byte[] obj, int length) {
            return executeBytes(inliningTarget, obj, length, true);
        }

        public static NativeSequenceStorage executeUncached(Object obj, int length) {
            return StorageToNativeNodeGen.getUncached().execute(null, obj, length);
        }

        public final NativeSequenceStorage execute(Node inliningTarget, Object obj, int length) {
            return execute(inliningTarget, obj, length, true);
        }

        @Specialization
        static NativeByteSequenceStorage doByte(byte[] arr, int length, boolean createRef,
                        @Shared @Cached(inline = false) CStructAccess.AllocateNode alloc,
                        @Cached(inline = false) CStructAccess.WriteByteNode write) {
            Object mem = alloc.calloc(arr.length + 1, java.lang.Byte.BYTES);
            write.writeByteArray(mem, arr);
            return NativeByteSequenceStorage.create(mem, length, arr.length, createRef);
        }

        @Specialization
        static NativeSequenceStorage doObject(Object[] arr, int length, boolean createRef,
                        @Shared @Cached(inline = false) CStructAccess.AllocateNode alloc,
                        @Cached(inline = false) CStructAccess.WriteObjectNewRefNode write) {
            Object mem = alloc.calloc(arr.length + 1, CStructAccess.POINTER_SIZE);
            write.writeArray(mem, arr, length, 0, 0);
            return NativeObjectSequenceStorage.create(mem, length, arr.length, createRef);
        }
    }

    public abstract static class CmpNode extends SequenceStorageBaseNode {
        @Child private BinaryComparisonNode cmpOp;
        @Child private CoerceToBooleanNode castToBooleanNode;

        protected CmpNode(BinaryComparisonNode cmpOp) {
            this.cmpOp = cmpOp;
        }

        public abstract boolean execute(VirtualFrame frame, SequenceStorage left, SequenceStorage right);

        private boolean testingEqualsWithDifferingLengths(int llen, int rlen) {
            // shortcut: if the lengths differ, the lists differ.
            CompilerAsserts.compilationConstant(cmpOp.getClass());
            if (cmpOp instanceof BinaryComparisonNode.EqNode) {
                if (llen != rlen) {
                    return true;
                }
            }
            return false;
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
            if (testingEqualsWithDifferingLengths(llen, rlen)) {
                return false;
            }
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
            if (testingEqualsWithDifferingLengths(llen, rlen)) {
                return false;
            }
            for (int i = 0; i < Math.min(llen, rlen); i++) {
                byte litem = left.getByteItemNormalized(i);
                byte ritem = right.getByteItemNormalized(i);
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
            if (testingEqualsWithDifferingLengths(llen, rlen)) {
                return false;
            }
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
            if (testingEqualsWithDifferingLengths(llen, rlen)) {
                return false;
            }
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
            if (testingEqualsWithDifferingLengths(llen, rlen)) {
                return false;
            }
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
        @SuppressWarnings("truffle-static-method")
        boolean doGeneric(VirtualFrame frame, SequenceStorage left, SequenceStorage right,
                        @Bind("this") Node inliningTarget,
                        @Cached PyObjectRichCompareBool.EqNode eqNode,
                        @Cached GetItemScalarNode getLeftItemNode,
                        @Cached GetItemScalarNode getRightItemNode) {
            int llen = left.length();
            int rlen = right.length();
            if (testingEqualsWithDifferingLengths(llen, rlen)) {
                return false;
            }
            for (int i = 0; i < Math.min(llen, rlen); i++) {
                Object leftItem = getLeftItemNode.execute(inliningTarget, left, i);
                Object rightItem = getRightItemNode.execute(inliningTarget, right, i);
                if (!eqNode.compare(frame, inliningTarget, leftItem, rightItem)) {
                    return cmpGeneric(frame, leftItem, rightItem);
                }
            }
            return cmpOp.cmp(llen, rlen);
        }

        private boolean cmpGeneric(VirtualFrame frame, Object left, Object right) {
            return castToBoolean(frame, cmpOp.executeObject(frame, left, right));
        }

        private boolean castToBoolean(VirtualFrame frame, Object value) {
            if (castToBooleanNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                castToBooleanNode = insert(CoerceToBooleanNode.createIfTrueNode());
            }
            return castToBooleanNode.executeBooleanCached(frame, value);
        }

        @NeverDefault
        public static CmpNode createLe() {
            return CmpNodeGen.create(BinaryComparisonNode.LeNode.create());
        }

        @NeverDefault
        public static CmpNode createLt() {
            return CmpNodeGen.create(BinaryComparisonNode.LtNode.create());
        }

        @NeverDefault
        public static CmpNode createGe() {
            return CmpNodeGen.create(BinaryComparisonNode.GeNode.create());
        }

        @NeverDefault
        public static CmpNode createGt() {
            return CmpNodeGen.create(BinaryComparisonNode.GtNode.create());
        }

        @NeverDefault
        public static CmpNode createEq() {
            return CmpNodeGen.create(BinaryComparisonNode.EqNode.create());
        }

        @NeverDefault
        public static CmpNode createNe() {
            return CmpNodeGen.create(BinaryComparisonNode.NeNode.create());
        }
    }

    /**
     * Will try to get the internal byte[]. Otherwise, it will get a copy. Please note that the
     * actual length of the storage and the internal storage might differ.
     */
    @GenerateInline
    @GenerateCached(false)
    public abstract static class GetInternalBytesNode extends PNodeWithContext {

        public final byte[] execute(Node inliningTarget, PBytesLike bytes) {
            return execute(null, inliningTarget, bytes);
        }

        public abstract byte[] execute(VirtualFrame frame, Node inliningTarget, Object bytes);

        protected static boolean isByteSequenceStorage(PBytesLike bytes) {
            return bytes.getSequenceStorage() instanceof ByteSequenceStorage;
        }

        protected static boolean isSimple(Object bytes) {
            return bytes instanceof PBytesLike && isByteSequenceStorage((PBytesLike) bytes);
        }

        @Specialization(guards = "isByteSequenceStorage(bytes)")
        static byte[] doBytes(PBytesLike bytes) {
            return ((ByteSequenceStorage) bytes.getSequenceStorage()).getInternalByteArray();
        }

        @Specialization(guards = "!isSimple(bytes)")
        static byte[] doGeneric(VirtualFrame frame, Object bytes,
                        @Cached(inline = false) BytesNodes.ToBytesNode toBytesNode) {
            return toBytesNode.execute(frame, bytes);
        }
    }

    /**
     * Use this node to get the internal byte array of the storage (if possible) to avoid copying.
     * Otherwise, it will create a copy with the exact size of the stored data.
     */
    @GenerateUncached
    @GenerateInline
    @GenerateCached(false)
    @ImportStatic(SequenceStorageBaseNode.class)
    public abstract static class GetInternalByteArrayNode extends PNodeWithContext {

        public abstract byte[] execute(Node inliningTarget, SequenceStorage s);

        @Specialization
        static byte[] doByteSequenceStorage(ByteSequenceStorage s) {
            return s.getInternalByteArray();
        }

        @Specialization
        static byte[] doNativeByte(Node inliningTarget, NativeByteSequenceStorage s,
                        @Shared("getItemNode") @Cached GetItemScalarNode getItemNode) {
            byte[] barr = new byte[s.length()];
            for (int i = 0; i < barr.length; i++) {
                int elem = getItemNode.executeKnownInt(inliningTarget, s, i);
                assert elem >= 0 && elem < 256;
                barr[i] = (byte) elem;
            }
            return barr;
        }

        @Specialization(guards = {"s.length() == cachedLen", "cachedLen <= 32"}, limit = "1")
        @ExplodeLoop
        static byte[] doGenericLenCached(Node inliningTarget, SequenceStorage s,
                        @Shared("getItemNode") @Cached GetItemScalarNode getItemNode,
                        @Shared @Cached CastToJavaByteNode castToByteNode,
                        @Cached(value = "s.length()") int cachedLen) {
            byte[] barr = new byte[cachedLen];
            for (int i = 0; i < cachedLen; i++) {
                barr[i] = castToByteNode.execute(inliningTarget, getItemNode.execute(inliningTarget, s, i));
            }
            return barr;
        }

        @Specialization(replaces = "doGenericLenCached")
        @InliningCutoff
        static byte[] doGeneric(Node inliningTarget, SequenceStorage s,
                        @Shared("getItemNode") @Cached GetItemScalarNode getItemNode,
                        @Shared @Cached CastToJavaByteNode castToByteNode) {
            byte[] barr = new byte[s.length()];
            for (int i = 0; i < barr.length; i++) {
                barr[i] = castToByteNode.execute(inliningTarget, getItemNode.execute(inliningTarget, s, i));
            }
            return barr;
        }
    }

    @GenerateUncached
    @GenerateInline
    @GenerateCached(false)
    public abstract static class ToByteArrayNode extends Node {

        public abstract byte[] execute(Node inliningTarget, SequenceStorage s);

        public static byte[] executeUncached(SequenceStorage s) {
            return ToByteArrayNodeGen.getUncached().execute(null, s);
        }

        @Specialization
        static byte[] doByteSequenceStorage(Node inliningTarget, ByteSequenceStorage s,
                        @Cached InlinedConditionProfile profile) {
            byte[] bytes = GetInternalByteArrayNode.doByteSequenceStorage(s);
            int storageLength = s.length();
            if (profile.profile(inliningTarget, storageLength != bytes.length)) {
                return exactCopy(bytes, storageLength);
            }
            return bytes;
        }

        @Specialization(guards = "!isByteSequenceStorage(s)")
        static byte[] doOther(Node inliningTarget, SequenceStorage s,
                        @Cached GetInternalByteArrayNode getInternalByteArrayNode) {
            return getInternalByteArrayNode.execute(inliningTarget, s);
        }

        private static byte[] exactCopy(byte[] barr, int len) {
            return PythonUtils.arrayCopyOf(barr, len);
        }

        static boolean isByteSequenceStorage(SequenceStorage s) {
            return s instanceof ByteSequenceStorage;
        }

    }

    @SuppressWarnings("truffle-inlining")       // footprint reduction 68 -> 49
    abstract static class ConcatBaseNode extends SequenceStorageBaseNode {

        public abstract SequenceStorage execute(SequenceStorage dest, SequenceStorage left, SequenceStorage right);

        @Specialization(guards = "!isNative(right)")
        static SequenceStorage doLeftEmpty(@SuppressWarnings("unused") EmptySequenceStorage dest, @SuppressWarnings("unused") EmptySequenceStorage left, SequenceStorage right,
                        @Bind("this") Node inliningTarget,
                        @Shared("raiseNode") @Cached PRaiseNode raiseNode,
                        @Shared("copyNode") @Cached CopyNode copyNode) {
            try {
                return copyNode.execute(inliningTarget, right);
            } catch (OutOfMemoryError e) {
                throw raiseNode.raise(MemoryError);
            }
        }

        @Specialization(guards = "!isNative(left)")
        static SequenceStorage doRightEmpty(@SuppressWarnings("unused") EmptySequenceStorage dest, SequenceStorage left, @SuppressWarnings("unused") EmptySequenceStorage right,
                        @Bind("this") Node inliningTarget,
                        @Shared("raiseNode") @Cached PRaiseNode raiseNode,
                        @Shared("copyNode") @Cached CopyNode copyNode) {
            try {
                return copyNode.execute(inliningTarget, left);
            } catch (OutOfMemoryError e) {
                throw raiseNode.raise(MemoryError);
            }
        }

        @Specialization(guards = {"dest == left", "left.getClass() == right.getClass()", "cachedClass == left.getClass()"}, limit = "1")
        static SequenceStorage doArrayBasedManagedManagedSameTypeInplace(@SuppressWarnings("unused") ArrayBasedSequenceStorage dest, ArrayBasedSequenceStorage left, ArrayBasedSequenceStorage right,
                        @Bind("this") Node inliningTarget,
                        @Cached("left.getClass()") Class<? extends ArrayBasedSequenceStorage> cachedClass,
                        @Shared @Cached SetLenNode setLenNode) {
            ArrayBasedSequenceStorage leftProfiled = cachedClass.cast(left);
            ArrayBasedSequenceStorage rightProfiled = cachedClass.cast(right);
            Object arr1 = leftProfiled.getInternalArrayObject();
            int len1 = leftProfiled.length();
            Object arr2 = rightProfiled.getInternalArrayObject();
            int len2 = rightProfiled.length();
            PythonUtils.arraycopy(arr2, 0, arr1, len1, len2);
            setLenNode.execute(inliningTarget, leftProfiled, len1 + len2);
            return leftProfiled;
        }

        @Specialization(guards = {"dest != left", "dest.getClass() == left.getClass()", "left.getClass() == right.getClass()", "cachedClass == dest.getClass()"}, limit = "1")
        static SequenceStorage doArrayBasedManagedManagedSameType(ArrayBasedSequenceStorage dest, ArrayBasedSequenceStorage left, ArrayBasedSequenceStorage right,
                        @Bind("this") Node inliningTarget,
                        @Cached("left.getClass()") Class<? extends ArrayBasedSequenceStorage> cachedClass,
                        @Shared @Cached SetLenNode setLenNode) {
            ArrayBasedSequenceStorage destProfiled = cachedClass.cast(dest);
            ArrayBasedSequenceStorage leftProfiled = cachedClass.cast(left);
            ArrayBasedSequenceStorage rightProfiled = cachedClass.cast(right);
            Object arr1 = leftProfiled.getInternalArrayObject();
            int len1 = leftProfiled.length();
            Object arr2 = rightProfiled.getInternalArrayObject();
            int len2 = rightProfiled.length();
            concat(destProfiled.getInternalArrayObject(), arr1, len1, arr2, len2);
            setLenNode.execute(inliningTarget, destProfiled, len1 + len2);
            return destProfiled;
        }

        @Specialization(guards = {"dest.getClass() == right.getClass()", "cachedClass == dest.getClass()"}, limit = "1")
        static SequenceStorage doArrayBasedEmptyManagedSameType(ArrayBasedSequenceStorage dest, @SuppressWarnings("unused") EmptySequenceStorage left, ArrayBasedSequenceStorage right,
                        @Bind("this") Node inliningTarget,
                        @Cached("dest.getClass()") Class<? extends ArrayBasedSequenceStorage> cachedClass,
                        @Shared @Cached SetLenNode setLenNode) {
            ArrayBasedSequenceStorage destProfiled = cachedClass.cast(dest);
            ArrayBasedSequenceStorage rightProfiled = cachedClass.cast(right);
            Object arr2 = rightProfiled.getInternalArrayObject();
            int len2 = rightProfiled.length();
            PythonUtils.arraycopy(arr2, 0, destProfiled.getInternalArrayObject(), 0, len2);
            setLenNode.execute(inliningTarget, destProfiled, len2);
            return destProfiled;
        }

        @Specialization(guards = {"dest.getClass() == left.getClass()", "cachedClass == dest.getClass()"}, limit = "1")
        static SequenceStorage doArrayBasedManagedEmptySameType(ArrayBasedSequenceStorage dest, ArrayBasedSequenceStorage left, @SuppressWarnings("unused") EmptySequenceStorage right,
                        @Bind("this") Node inliningTarget,
                        @Cached("left.getClass()") Class<? extends ArrayBasedSequenceStorage> cachedClass,
                        @Shared @Cached SetLenNode setLenNode) {
            ArrayBasedSequenceStorage destProfiled = cachedClass.cast(dest);
            ArrayBasedSequenceStorage leftProfiled = cachedClass.cast(left);
            Object arr1 = leftProfiled.getInternalArrayObject();
            int len1 = leftProfiled.length();
            PythonUtils.arraycopy(arr1, 0, destProfiled.getInternalArrayObject(), 0, len1);
            setLenNode.execute(inliningTarget, destProfiled, len1);
            return destProfiled;
        }

        @Specialization(guards = "dest == left")
        static SequenceStorage doGenericInplace(@SuppressWarnings("unused") SequenceStorage dest, SequenceStorage left, SequenceStorage right,
                        @Bind("this") Node inliningTarget,
                        @Exclusive @Cached GetItemScalarNode getItemRightNode,
                        @Exclusive @Cached InitializeItemScalarNode initalizeItemNode,
                        @Exclusive @Cached SetLenNode setLenNode) {
            int len1 = left.length();
            int len2 = right.length();
            for (int i = 0; i < len2; i++) {
                initalizeItemNode.execute(inliningTarget, left, i + len1, getItemRightNode.execute(inliningTarget, right, i));
            }
            setLenNode.execute(inliningTarget, left, len1 + len2);
            return left;
        }

        @Specialization(guards = "dest != left")
        static SequenceStorage doGeneric(SequenceStorage dest, SequenceStorage left, SequenceStorage right,
                        @Bind("this") Node inliningTarget,
                        @Exclusive @Cached GetItemScalarNode getItemLeftNode,
                        @Exclusive @Cached GetItemScalarNode getItemRightNode,
                        @Exclusive @Cached InitializeItemScalarNode initalizeItemNode,
                        @Exclusive @Cached SetLenNode setLenNode) {
            int len1 = left.length();
            int len2 = right.length();
            for (int i = 0; i < len1; i++) {
                initalizeItemNode.execute(inliningTarget, dest, i, getItemLeftNode.execute(inliningTarget, left, i));
            }
            for (int i = 0; i < len2; i++) {
                initalizeItemNode.execute(inliningTarget, dest, i + len1, getItemRightNode.execute(inliningTarget, right, i));
            }
            setLenNode.execute(inliningTarget, dest, len1 + len2);
            return dest;
        }

        private static void concat(Object dest, Object arr1, int len1, Object arr2, int len2) {
            PythonUtils.arraycopy(arr1, 0, dest, 0, len1);
            PythonUtils.arraycopy(arr2, 0, dest, len1, len2);
        }
    }

    /**
     * Concatenates two sequence storages; creates a storage of a suitable type and writes the
     * result to the new storage.
     */
    public abstract static class ConcatNode extends SequenceStorageBaseNode {
        private static final TruffleString DEFAULT_ERROR_MSG = ErrorMessages.BAD_ARG_TYPE_FOR_BUILTIN_OP;

        @Child private ConcatBaseNode concatBaseNode = ConcatBaseNodeGen.create();
        @Child private GeneralizationNode genNode;

        private final Supplier<GeneralizationNode> genNodeProvider;

        /*
         * CPython is inconsistent when too repeats are done. Most types raise MemoryError, but e.g.
         * bytes raises OverflowError when the memory might be available but the size overflows
         * sys.maxint
         */
        private final PythonBuiltinClassType errorForOverflow;

        ConcatNode(Supplier<GeneralizationNode> genNodeProvider, PythonBuiltinClassType errorForOverflow) {
            this.genNodeProvider = genNodeProvider;
            this.errorForOverflow = errorForOverflow;
        }

        public abstract SequenceStorage execute(SequenceStorage left, SequenceStorage right);

        @Specialization
        SequenceStorage doRight(SequenceStorage left, SequenceStorage right,
                        @Bind("this") Node inliningTarget,
                        @Cached CreateEmptyNode createEmptyNode,
                        @Cached InlinedConditionProfile shouldOverflow,
                        @Cached PRaiseNode raiseNode) {
            int destlen = 0;
            try {
                int len1 = left.length();
                int len2 = right.length();
                // we eagerly generalize the store to avoid possible cascading generalizations
                destlen = PythonUtils.addExact(len1, len2);
                if (errorForOverflow == OverflowError && shouldOverflow.profile(inliningTarget, destlen >= SysModuleBuiltins.MAXSIZE)) {
                    // cpython raises an overflow error when this happens
                    throw raiseNode.raise(OverflowError);
                }
                SequenceStorage generalized = generalizeStore(createEmpty(createEmptyNode, inliningTarget, left, right, destlen), right);
                return doConcat(generalized, left, right);
            } catch (OutOfMemoryError e) {
                throw raiseNode.raise(MemoryError);
            } catch (OverflowException e) {
                throw raiseNode.raise(errorForOverflow);
            }
        }

        private SequenceStorage createEmpty(CreateEmptyNode createEmptyNode, Node inliningTarget, SequenceStorage l, SequenceStorage r, int len) {
            if (l instanceof EmptySequenceStorage) {
                return createEmptyNode.execute(inliningTarget, r, len, -1);
            }
            return createEmptyNode.execute(inliningTarget, l, len, len);
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
            return genNode.executeCached(storage, value);
        }

        @NeverDefault
        public static ConcatNode create() {
            return create(() -> NoGeneralizationCustomMessageNode.create(DEFAULT_ERROR_MSG), MemoryError);
        }

        @NeverDefault
        public static ConcatNode createWithOverflowError() {
            return create(() -> NoGeneralizationCustomMessageNode.create(DEFAULT_ERROR_MSG), OverflowError);
        }

        @NeverDefault
        public static ConcatNode create(TruffleString msg) {
            return create(() -> NoGeneralizationCustomMessageNode.create(msg), MemoryError);
        }

        @NeverDefault
        public static ConcatNode create(Supplier<GeneralizationNode> genNodeProvider) {
            return create(genNodeProvider, MemoryError);
        }

        @NeverDefault
        private static ConcatNode create(Supplier<GeneralizationNode> genNodeProvider, PythonBuiltinClassType errorForOverflow) {
            return ConcatNodeGen.create(genNodeProvider, errorForOverflow);
        }
    }

    @ImportStatic(PGuards.class)
    public abstract static class ExtendNode extends SequenceStorageBaseNode {
        @Child private GeneralizationNode genNode;

        private final GenNodeSupplier genNodeProvider;

        public ExtendNode(GenNodeSupplier genNodeProvider) {
            this.genNodeProvider = genNodeProvider;
        }

        public abstract SequenceStorage execute(VirtualFrame frame, SequenceStorage s, Object iterable, int len);

        private static int lengthResult(int current, int ext) {
            try {
                return PythonUtils.addExact(current, ext);
            } catch (OverflowException e) {
                // (mq) There is no need to ensure capacity as we either
                // run out of memory or dealing with a fake length.
                return current;
            }
        }

        @Specialization(guards = {"hasStorage(seq)", "isBuiltinSequence(seq)"})
        @SuppressWarnings("truffle-static-method")
        SequenceStorage doWithStorage(SequenceStorage left, PSequence seq, int len,
                        @Bind("this") Node inliningTarget,
                        @Cached GetSequenceStorageNode getStorageNode,
                        @Exclusive @Cached EnsureCapacityNode ensureCapacityNode,
                        @Cached ConcatBaseNode concatStoragesNode) {
            SequenceStorage right = getStorageNode.execute(inliningTarget, seq);
            int lenLeft = left.length();
            int lenResult;
            if (len > 0) {
                lenResult = lengthResult(lenLeft, len);
            } else {
                lenResult = lengthResult(lenLeft, right.length());
            }
            while (true) {
                // unbounded loop should not be a problem for PE because generalizeStore() in the
                // catch block immediately de-opts in the first iteration, i.e. if the storages are
                // compatible and SequenceStoreException does not happen, then this compiles as if
                // the while loop was not here at all
                try {
                    // EnsureCapacityNode handles the overflow and raises an error
                    ensureCapacityNode.execute(inliningTarget, left, lenResult);
                    return concatStoragesNode.execute(left, left, right);
                } catch (SequenceStoreException e) {
                    left = generalizeStore(left, e.getIndicationValue());
                }
            }
        }

        @Fallback
        @SuppressWarnings("truffle-static-method")
        @InliningCutoff
        SequenceStorage doWithoutStorage(VirtualFrame frame, SequenceStorage left, Object iterable, int len,
                        @Bind("this") Node inliningTarget,
                        @Cached PyObjectGetIter getIter,
                        @Exclusive @Cached EnsureCapacityNode ensureCapacityNode,
                        @Cached GetNextNode getNextNode,
                        @Cached IsBuiltinObjectProfile errorProfile,
                        @Cached AppendNode appendNode) {
            SequenceStorage currentStore = left;
            int lenLeft = currentStore.length();
            Object it = getIter.execute(frame, inliningTarget, iterable);
            if (len > 0) {
                ensureCapacityNode.execute(inliningTarget, left, lengthResult(lenLeft, len));
            }
            while (true) {
                Object value;
                try {
                    value = getNextNode.execute(frame, it);
                    currentStore = appendNode.execute(inliningTarget, currentStore, value, genNodeProvider);
                } catch (PException e) {
                    e.expectStopIteration(inliningTarget, errorProfile);
                    return currentStore;
                }
            }
        }

        private SequenceStorage generalizeStore(SequenceStorage storage, Object value) {
            if (genNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                genNode = insert(genNodeProvider.create());
            }
            return genNode.executeCached(storage, value);
        }

        @NeverDefault
        protected ExtendNode createRecursive() {
            return ExtendNodeGen.create(genNodeProvider);
        }

        @NeverDefault
        public static ExtendNode create(GenNodeSupplier genNodeProvider) {
            return ExtendNodeGen.create(genNodeProvider);
        }
    }

    public abstract static class RepeatNode extends SequenceStorageBaseNode {
        @Child private RepeatNode recursive;

        /*
         * CPython is inconsistent when too repeats are done. Most types raise MemoryError, but e.g.
         * bytes raises OverflowError when the memory might be available but the size overflows
         * sys.maxint
         */
        private final PythonBuiltinClassType errorForOverflow;

        protected RepeatNode(PythonBuiltinClassType errorForOverflow) {
            this.errorForOverflow = errorForOverflow;
        }

        public abstract SequenceStorage execute(VirtualFrame frame, SequenceStorage left, Object times);

        public abstract SequenceStorage execute(VirtualFrame frame, SequenceStorage left, int times);

        @Specialization
        static SequenceStorage doEmpty(EmptySequenceStorage s, @SuppressWarnings("unused") int times) {
            return s;
        }

        @Specialization(guards = "times <= 0")
        static SequenceStorage doZeroRepeat(SequenceStorage s, @SuppressWarnings("unused") int times,
                        @Bind("this") Node inliningTarget,
                        @Exclusive @Cached CreateEmptyNode createEmptyNode) {
            return createEmptyNode.execute(inliningTarget, s, 0, -1);
        }

        /* special but common case: something like '[False] * n' */
        @Specialization(guards = {"s.length() == 1", "times > 0"})
        BoolSequenceStorage doBoolSingleElement(BoolSequenceStorage s, int times,
                        @Shared @Cached PRaiseNode raiseNode) {
            try {
                boolean[] repeated = new boolean[PythonUtils.multiplyExact(s.length(), times)];
                Arrays.fill(repeated, s.getBoolItemNormalized(0));
                return new BoolSequenceStorage(repeated);
            } catch (OutOfMemoryError e) {
                throw raiseNode.raise(MemoryError);
            } catch (OverflowException e) {
                throw raiseNode.raise(errorForOverflow);
            }
        }

        /* special but common case: something like '["\x00"] * n' */
        @Specialization(guards = {"s.length() == 1", "times > 0"})
        ByteSequenceStorage doByteSingleElement(ByteSequenceStorage s, int times,
                        @Shared @Cached PRaiseNode raiseNode) {
            try {
                byte[] repeated = new byte[PythonUtils.multiplyExact(s.length(), times)];
                Arrays.fill(repeated, s.getByteItemNormalized(0));
                return new ByteSequenceStorage(repeated);
            } catch (OutOfMemoryError e) {
                throw raiseNode.raise(MemoryError);
            } catch (OverflowException e) {
                throw raiseNode.raise(errorForOverflow);
            }
        }

        /* special but common case: something like '[0] * n' */
        @Specialization(guards = {"s.length() == 1", "times > 0"})
        IntSequenceStorage doIntSingleElement(IntSequenceStorage s, int times,
                        @Shared @Cached PRaiseNode raiseNode) {
            try {
                int[] repeated = new int[PythonUtils.multiplyExact(s.length(), times)];
                Arrays.fill(repeated, s.getIntItemNormalized(0));
                return new IntSequenceStorage(repeated);
            } catch (OutOfMemoryError e) {
                throw raiseNode.raise(MemoryError);
            } catch (OverflowException e) {
                throw raiseNode.raise(errorForOverflow);
            }
        }

        /* special but common case: something like '[0L] * n' */
        @Specialization(guards = {"s.length() == 1", "times > 0"})
        LongSequenceStorage doLongSingleElement(LongSequenceStorage s, int times,
                        @Shared @Cached PRaiseNode raiseNode) {
            try {
                long[] repeated = new long[PythonUtils.multiplyExact(s.length(), times)];
                Arrays.fill(repeated, s.getLongItemNormalized(0));
                return new LongSequenceStorage(repeated);
            } catch (OutOfMemoryError e) {
                throw raiseNode.raise(MemoryError);
            } catch (OverflowException e) {
                throw raiseNode.raise(errorForOverflow);
            }
        }

        /* special but common case: something like '[0.0] * n' */
        @Specialization(guards = {"s.length() == 1", "times > 0"})
        DoubleSequenceStorage doDoubleSingleElement(DoubleSequenceStorage s, int times,
                        @Shared @Cached PRaiseNode raiseNode) {
            try {
                double[] repeated = new double[PythonUtils.multiplyExact(s.length(), times)];
                Arrays.fill(repeated, s.getDoubleItemNormalized(0));
                return new DoubleSequenceStorage(repeated);
            } catch (OutOfMemoryError e) {
                throw raiseNode.raise(MemoryError);
            } catch (OverflowException e) {
                throw raiseNode.raise(errorForOverflow);
            }
        }

        /* special but common case: something like '[None] * n' */
        @Specialization(guards = {"s.length() == 1", "times > 0"})
        ObjectSequenceStorage doObjectSingleElement(ObjectSequenceStorage s, int times,
                        @Shared @Cached PRaiseNode raiseNode) {
            try {
                Object[] repeated = new Object[PythonUtils.multiplyExact(s.length(), times)];
                Arrays.fill(repeated, s.getObjectItemNormalized(0));
                return new ObjectSequenceStorage(repeated);
            } catch (OutOfMemoryError e) {
                throw raiseNode.raise(MemoryError);
            } catch (OverflowException e) {
                throw raiseNode.raise(errorForOverflow);
            }
        }

        @Specialization(limit = "MAX_BASIC_STORAGES", guards = {"times > 0", "!isNative(s)", "s.getClass() == cachedClass"})
        SequenceStorage doArrayBasedManaged(ArrayBasedSequenceStorage s, int times,
                        @Shared @Cached PRaiseNode raiseNode,
                        @Cached("s.getClass()") Class<? extends ArrayBasedSequenceStorage> cachedClass) {
            try {
                ArrayBasedSequenceStorage profiled = cachedClass.cast(s);
                Object arr1 = profiled.getInternalArrayObject();
                int len = profiled.length();
                int newLength = PythonUtils.multiplyExact(len, times);
                ArrayBasedSequenceStorage repeated = profiled.createEmpty(newLength);
                Object destArr = repeated.getInternalArrayObject();
                repeat(destArr, arr1, len, times);
                repeated.setNewLength(newLength);
                return repeated;
            } catch (OutOfMemoryError e) {
                throw raiseNode.raise(MemoryError);
            } catch (OverflowException e) {
                throw raiseNode.raise(errorForOverflow);
            }
        }

        @Specialization(replaces = "doArrayBasedManaged", guards = "times > 0")
        @SuppressWarnings("truffle-static-method")
        SequenceStorage doGeneric(SequenceStorage s, int times,
                        @Bind("this") Node inliningTarget,
                        @Shared @Cached PRaiseNode raiseNode,
                        @Exclusive @Cached CreateEmptyNode createEmptyNode,
                        @Cached GetItemScalarNode getItemNode,
                        @Cached SetItemScalarNode setItemNode,
                        @Cached GetItemScalarNode getDestItemNode) {
            try {
                int len = s.length();
                int newLen = PythonUtils.multiplyExact(len, times);
                ArrayBasedSequenceStorage repeated = createEmptyNode.execute(inliningTarget, s, newLen, -1);

                for (int i = 0; i < len; i++) {
                    setItemNode.execute(inliningTarget, repeated, i, getItemNode.execute(inliningTarget, s, i));
                }

                // read from destination since that is potentially faster
                for (int j = 1; j < times; j++) {
                    for (int i = 0; i < len; i++) {
                        setItemNode.execute(inliningTarget, repeated, j * len + i, getDestItemNode.execute(inliningTarget, repeated, i));
                    }
                }

                repeated.setNewLength(newLen);
                return repeated;
            } catch (OutOfMemoryError e) {
                throw raiseNode.raise(MemoryError);
            } catch (OverflowException e) {
                throw raiseNode.raise(errorForOverflow);
            }
        }

        @Specialization(guards = "!isInt(times)")
        @SuppressWarnings("truffle-static-method")
        SequenceStorage doNonInt(VirtualFrame frame, SequenceStorage s, Object times,
                        @Bind("this") Node inliningTarget,
                        @Cached PyIndexCheckNode indexCheckNode,
                        @Cached PyNumberAsSizeNode asSizeNode,
                        @Shared @Cached PRaiseNode raiseNode) {
            if (!indexCheckNode.execute(inliningTarget, times)) {
                throw raiseNode.raise(TypeError, ErrorMessages.CANT_MULTIPLY_SEQ_BY_NON_INT, times);
            }
            int i = asSizeNode.executeExact(frame, inliningTarget, times);
            if (recursive == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                recursive = insert(RepeatNodeGen.create(errorForOverflow));
            }
            return recursive.execute(frame, s, i);
        }

        private static void repeat(Object dest, Object src, int len, int times) {
            for (int i = 0; i < times; i++) {
                PythonUtils.arraycopy(src, 0, dest, i * len, len);
            }
        }

        protected static boolean isInt(Object times) {
            return times instanceof Integer;
        }

        @NeverDefault
        public static RepeatNode create() {
            return RepeatNodeGen.create(MemoryError);
        }

        @NeverDefault
        public static RepeatNode createWithOverflowError() {
            return RepeatNodeGen.create(OverflowError);
        }
    }

    @GenerateInline
    @GenerateCached(false)
    public abstract static class ContainsNode extends SequenceStorageBaseNode {
        public abstract boolean execute(VirtualFrame frame, Node inliningTarget, SequenceStorage left, Object item);

        @Specialization
        public static boolean doIndexOf(VirtualFrame frame, Node inliningTarget, SequenceStorage left, Object item,
                        @Cached IndexOfNode indexOfNode) {
            return indexOfNode.execute(frame, inliningTarget, left, item) != -1;
        }

    }

    @GenerateInline
    @GenerateCached(false)
    public abstract static class IndexOfNode extends SequenceStorageBaseNode {
        public abstract int execute(VirtualFrame frame, Node inliningTarget, SequenceStorage left, Object item);

        @Specialization(guards = "left.length() == 0")
        @SuppressWarnings("unused")
        static int doEmpty(SequenceStorage left, Object item) {
            return -1;
        }

        @Specialization
        public static int doByteStorage(ByteSequenceStorage s, int item) {
            return s.indexOfInt(item);
        }

        @Specialization
        public static int doByteStorage(ByteSequenceStorage s, byte item) {
            return s.indexOfByte(item);
        }

        @Specialization
        public static int doIntStorage(IntSequenceStorage s, int item) {
            return s.indexOfInt(item);
        }

        @Specialization
        public static int doLongStorage(LongSequenceStorage s, long item) {
            return s.indexOfLong(item);
        }

        @Specialization
        public static int doDoubleStorage(DoubleSequenceStorage s, double item) {
            return s.indexOfDouble(item);
        }

        @Specialization
        static int doGeneric(VirtualFrame frame, Node inliningTarget, SequenceStorage self, Object item,
                        @Cached(inline = false) GetItemScalarNode getItemNode,
                        @Cached PyObjectRichCompareBool.EqNode eqNode) {
            for (int i = 0; i < self.length(); i++) {
                Object seqItem = getItemNode.execute(inliningTarget, self, i);
                if (eqNode.compare(frame, inliningTarget, seqItem, item)) {
                    return i;
                }
            }
            return -1;
        }
    }

    /**
     * Generalization node must convert given storage to a storage that is able to be written any
     * number of any valid elements. I.e., there must be a specialization handling that storage type
     * in {@link SetItemScalarNode}. Note: it is possible that the RHS of the write may be invalid
     * element, e.g., large integer when the storage is bytes array storage, but in such case the
     * {@link SetItemScalarNode} will correctly raise Python level {@code ValueError}.
     */
    public abstract static class GeneralizationNode extends Node {

        public abstract SequenceStorage executeCached(SequenceStorage toGeneralize, Object indicationValue);
    }

    /**
     * Does not allow any generalization but compatible types.
     */
    @GenerateUncached
    @SuppressWarnings("truffle-inlining")       // footprint reduction 36 -> 17
    public abstract static class NoGeneralizationNode extends GeneralizationNode {

        public static final GenNodeSupplier DEFAULT = new GenNodeSupplier() {

            @Override
            public GeneralizationNode getUncached() {
                return NoGeneralizationNodeGen.getUncached();
            }

            @Override
            public GeneralizationNode create() {
                return NoGeneralizationNodeGen.create();
            }
        };

        @Specialization
        protected SequenceStorage doGeneric(SequenceStorage s, Object indicationVal,
                        @Bind("this") Node inliningTarget,
                        @Cached IsAssignCompatibleNode isAssignCompatibleNode,
                        @Cached GetElementType getElementType,
                        @Cached InlinedExactClassProfile valTypeProfile,
                        @Cached PRaiseNode raiseNode) {

            Object val = valTypeProfile.profile(inliningTarget, indicationVal);
            if (val instanceof SequenceStorage && isAssignCompatibleNode.execute(inliningTarget, s, (SequenceStorage) val)) {
                return s;
            }

            StorageType et = getElementType.execute(inliningTarget, s);
            if (val instanceof Byte && SequenceStorageBaseNode.isByteLike(et) ||
                            val instanceof Integer && (SequenceStorageBaseNode.isInt(et) || SequenceStorageBaseNode.isLong(et)) ||
                            val instanceof Long && SequenceStorageBaseNode.isLong(et) || SequenceStorageBaseNode.isObject(et)) {
                return s;
            }

            throw raiseNode.raise(TypeError, getErrorMessage());
        }

        protected TruffleString getErrorMessage() {
            return StringLiterals.T_EMPTY_STRING;
        }
    }

    public abstract static class NoGeneralizationCustomMessageNode extends NoGeneralizationNode {

        private final TruffleString errorMessage;

        public NoGeneralizationCustomMessageNode(TruffleString errorMessage) {
            this.errorMessage = errorMessage;
        }

        @Override
        protected final TruffleString getErrorMessage() {
            return errorMessage;
        }

        @NeverDefault
        public static NoGeneralizationCustomMessageNode create(TruffleString msg) {
            return NoGeneralizationCustomMessageNodeGen.create(msg);
        }
    }

    /**
     * Implements list generalization rules; previously in 'SequenceStorage.generalizeFor'.
     */
    @GenerateUncached
    @GenerateInline(inlineByDefault = true)
    @GenerateCached
    public abstract static class ListGeneralizationNode extends GeneralizationNode {

        public static final GenNodeSupplier SUPPLIER = new GenNodeSupplier() {

            @Override
            public GeneralizationNode getUncached() {
                return ListGeneralizationNodeGen.getUncached();
            }

            @Override
            public GeneralizationNode create() {
                return ListGeneralizationNodeGen.create();
            }
        };

        private static final int DEFAULT_CAPACITY = 8;

        @Override
        public final SequenceStorage executeCached(SequenceStorage toGeneralize, Object indicationValue) {
            return execute(this, toGeneralize, indicationValue);
        }

        public abstract SequenceStorage execute(Node inliningTarget, SequenceStorage toGeneralize, Object indicationValue);

        @Specialization
        static ObjectSequenceStorage doObject(@SuppressWarnings("unused") ObjectSequenceStorage s, @SuppressWarnings("unused") Object indicationValue) {
            return s;
        }

        @Specialization
        static SequenceStorage doEmptyStorage(Node inliningTarget, @SuppressWarnings("unused") EmptySequenceStorage s, ArrayBasedSequenceStorage other,
                        @Exclusive @Cached InlinedExactClassProfile otherProfile) {
            return otherProfile.profile(inliningTarget, other).createEmpty(DEFAULT_CAPACITY);
        }

        @Specialization
        static ByteSequenceStorage doEmptyByte(@SuppressWarnings("unused") EmptySequenceStorage s, @SuppressWarnings("unused") byte val) {
            return new ByteSequenceStorage(DEFAULT_CAPACITY);
        }

        @Specialization
        static IntSequenceStorage doEmptyInteger(@SuppressWarnings("unused") EmptySequenceStorage s, @SuppressWarnings("unused") int val) {
            return new IntSequenceStorage();
        }

        @Specialization
        static LongSequenceStorage doEmptyLong(@SuppressWarnings("unused") EmptySequenceStorage s, @SuppressWarnings("unused") long val) {
            return new LongSequenceStorage();
        }

        @Specialization
        static DoubleSequenceStorage doEmptyDouble(@SuppressWarnings("unused") EmptySequenceStorage s, @SuppressWarnings("unused") double val) {
            return new DoubleSequenceStorage();
        }

        protected static boolean isKnownType(Object val) {
            return val instanceof Byte || val instanceof Integer || val instanceof Long || val instanceof Double;
        }

        @Specialization(guards = "!isKnownType(val)")
        static ObjectSequenceStorage doEmptyObject(@SuppressWarnings("unused") EmptySequenceStorage s, @SuppressWarnings("unused") Object val) {
            return new ObjectSequenceStorage(DEFAULT_CAPACITY);
        }

        @Specialization
        static ByteSequenceStorage doByteByte(ByteSequenceStorage s, @SuppressWarnings("unused") byte val) {
            return s;
        }

        @Specialization
        static IntSequenceStorage doByteInteger(@SuppressWarnings("unused") ByteSequenceStorage s, @SuppressWarnings("unused") int val) {
            int[] copied = new int[s.length()];
            for (int i = 0; i < copied.length; i++) {
                copied[i] = s.getIntItemNormalized(i);
            }
            return new IntSequenceStorage(copied);
        }

        @Specialization
        static LongSequenceStorage doByteLong(@SuppressWarnings("unused") ByteSequenceStorage s, @SuppressWarnings("unused") long val) {
            long[] copied = new long[s.length()];
            for (int i = 0; i < copied.length; i++) {
                copied[i] = s.getIntItemNormalized(i);
            }
            return new LongSequenceStorage(copied);
        }

        @Specialization
        static SequenceStorage doIntegerInteger(IntSequenceStorage s, @SuppressWarnings("unused") int val) {
            return s;
        }

        @Specialization
        static SequenceStorage doIntegerLong(@SuppressWarnings("unused") IntSequenceStorage s, @SuppressWarnings("unused") long val) {
            long[] copied = new long[s.length()];
            for (int i = 0; i < copied.length; i++) {
                copied[i] = s.getIntItemNormalized(i);
            }
            return new LongSequenceStorage(copied);
        }

        @Specialization
        static LongSequenceStorage doLongByte(LongSequenceStorage s, @SuppressWarnings("unused") byte val) {
            return s;
        }

        @Specialization
        static LongSequenceStorage doLongInteger(LongSequenceStorage s, @SuppressWarnings("unused") int val) {
            return s;
        }

        @Specialization
        static LongSequenceStorage doLongLong(LongSequenceStorage s, @SuppressWarnings("unused") long val) {
            return s;
        }

        @Specialization
        static NativeObjectSequenceStorage doNative(NativeObjectSequenceStorage s, @SuppressWarnings("unused") Object val) {
            return s;
        }

        @Specialization(guards = "isFallbackCase(s, value)")
        static SequenceStorage doGeneric(Node inliningTarget, SequenceStorage s, Object value,
                        @Cached GetInternalObjectArrayNode getInternalObjectArrayNode,
                        @Cached IsAssignCompatibleNode isAssignCompatibleNode) {
            if (value instanceof SequenceStorage other) {
                if (isAssignCompatibleNode.execute(inliningTarget, s, other)) {
                    return s;
                }
            }
            return new ObjectSequenceStorage(getInternalObjectArrayNode.execute(inliningTarget, s));
        }

        protected static boolean isFallbackCase(SequenceStorage s, Object value) {
            // there are explicit specializations for all cases with EmptySequenceStorage
            if (s instanceof EmptySequenceStorage || s instanceof ObjectSequenceStorage || s instanceof NativeSequenceStorage) {
                return false;
            }
            if ((s instanceof ByteSequenceStorage || s instanceof IntSequenceStorage || s instanceof LongSequenceStorage) &&
                            (value instanceof Byte || value instanceof Integer || value instanceof Long)) {
                return false;
            }
            return true;
        }

        public static ListGeneralizationNode create() {
            return ListGeneralizationNodeGen.create();
        }
    }

    @GenerateUncached
    @GenerateInline(inlineByDefault = true)
    @GenerateCached
    @ImportStatic(SequenceStorageBaseNode.class)
    public abstract static class AppendNode extends Node {

        public abstract SequenceStorage execute(Node inliningTarget, SequenceStorage s, Object val, GenNodeSupplier genNodeSupplier);

        public static SequenceStorage executeUncached(SequenceStorage s, Object val, GenNodeSupplier genNodeSupplier) {
            return AppendNodeGen.getUncached().execute(null, s, val, genNodeSupplier);
        }

        @Specialization
        static SequenceStorage doEmpty(Node inliningTarget, EmptySequenceStorage s, Object val, GenNodeSupplier genNodeSupplier,
                        @Cached(inline = false) AppendNode recursive,
                        @Exclusive @Cached DoGeneralizationNode doGenNode) {
            SequenceStorage newStorage = doGenNode.execute(inliningTarget, genNodeSupplier, s, val);
            return recursive.execute(inliningTarget, newStorage, val, genNodeSupplier);
        }

        @Fallback
        static SequenceStorage doNonEmpty(Node inliningTarget, SequenceStorage s, Object val, GenNodeSupplier genNodeSupplier,
                        @Cached EnsureCapacityNode ensureCapacity,
                        @Cached SetLenNode setLenNode,
                        @Cached InitializeItemScalarNode initializeItemNode,
                        @Exclusive @Cached DoGeneralizationNode doGenNode) {
            int len = s.length();
            int newLen = len + 1;
            int capacity = s.getCapacity();
            if (newLen > capacity) {
                ensureCapacity.execute(inliningTarget, s, len + 1);
            }
            try {
                initializeItemNode.execute(inliningTarget, s, len, val);
                setLenNode.execute(inliningTarget, s, len + 1);
                return s;
            } catch (SequenceStoreException e) {
                return generalize(inliningTarget, s, val, genNodeSupplier, ensureCapacity, setLenNode, initializeItemNode, doGenNode, len, e);
            }
        }

        @InliningCutoff
        private static SequenceStorage generalize(Node inliningTarget, SequenceStorage s, Object val, GenNodeSupplier genNodeSupplier, EnsureCapacityNode ensureCapacity, SetLenNode setLenNode,
                        InitializeItemScalarNode initializeItemNode, DoGeneralizationNode doGenNode, int len, SequenceStoreException e) {
            SequenceStorage generalized = doGenNode.execute(inliningTarget, genNodeSupplier, s, e.getIndicationValue());
            ensureCapacity.execute(inliningTarget, generalized, len + 1);
            try {
                initializeItemNode.execute(inliningTarget, generalized, len, val);
                setLenNode.execute(inliningTarget, generalized, len + 1);
                return generalized;
            } catch (SequenceStoreException e1) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                throw new IllegalStateException();
            }
        }

        @NeverDefault
        public static AppendNode create() {
            return AppendNodeGen.create();
        }

        public static AppendNode getUncached() {
            return AppendNodeGen.getUncached();
        }
    }

    @GenerateInline
    @GenerateCached(false)
    public abstract static class CreateEmptyNode extends SequenceStorageBaseNode {

        public abstract ArrayBasedSequenceStorage execute(Node inliningTarget, SequenceStorage s, int cap, int len);

        @Specialization
        static ArrayBasedSequenceStorage doIt(Node inliningTarget, SequenceStorage s, int cap, int len,
                        @Cached EnsureCapacityNode ensureCapacityNode,
                        @Cached GetElementType getElementType,
                        @Cached CreateEmptyForTypeNode createEmptyForTypeNode) {
            ArrayBasedSequenceStorage ss = createEmptyForTypeNode.execute(inliningTarget, getElementType.execute(inliningTarget, s), cap);
            if (len != -1) {
                ensureCapacityNode.execute(inliningTarget, ss, len);
                ss.setNewLength(len);
            }
            return ss;
        }
    }

    @GenerateInline
    @GenerateCached(false)
    abstract static class CreateEmptyForTypeNode extends SequenceStorageBaseNode {

        public abstract ArrayBasedSequenceStorage execute(Node inliningTarget, StorageType type, int cap);

        @Specialization(guards = "isBoolean(type)")
        static BoolSequenceStorage doBoolean(@SuppressWarnings("unused") StorageType type, int cap) {
            return new BoolSequenceStorage(cap);
        }

        @Specialization(guards = "isByte(type)")
        static ByteSequenceStorage doByte(@SuppressWarnings("unused") StorageType type, int cap) {
            return new ByteSequenceStorage(cap);
        }

        @Specialization(guards = "isInt(type)")
        static IntSequenceStorage doInt(@SuppressWarnings("unused") SequenceStorage.StorageType type, int cap) {
            return new IntSequenceStorage(cap);
        }

        @Specialization(guards = "isLong(type)")
        static LongSequenceStorage doLong(@SuppressWarnings("unused") StorageType type, int cap) {
            return new LongSequenceStorage(cap);
        }

        @Specialization(guards = "isDouble(type)")
        static DoubleSequenceStorage doDouble(@SuppressWarnings("unused") SequenceStorage.StorageType type, int cap) {
            return new DoubleSequenceStorage(cap);
        }

        @Fallback
        static ObjectSequenceStorage doObject(@SuppressWarnings("unused") StorageType type, int cap) {
            return new ObjectSequenceStorage(cap);
        }
    }

    @GenerateUncached
    @GenerateInline
    @GenerateCached(false)
    public abstract static class EnsureCapacityNode extends SequenceStorageBaseNode {

        public abstract void execute(Node inliningTarget, SequenceStorage s, int cap);

        @Specialization
        static void doEmpty(EmptySequenceStorage s, @SuppressWarnings("unused") int cap) {
            // do nothing
        }

        @Specialization
        static void doInt(IntSequenceStorage storage, int cap) {
            storage.ensureCapacity(cap);
        }

        @Specialization
        static void doLong(LongSequenceStorage storage, int cap) {
            storage.ensureCapacity(cap);
        }

        @Specialization
        static void doDouble(DoubleSequenceStorage storage, int cap) {
            storage.ensureCapacity(cap);
        }

        @Specialization
        static void doByte(ByteSequenceStorage storage, int cap) {
            storage.ensureCapacity(cap);
        }

        @Specialization
        static void doObject(ObjectSequenceStorage storage, int cap) {
            storage.ensureCapacity(cap);
        }

        @Specialization
        static void doArrow(Node inliningTarget, ArrowSequenceStorage storage, int cap) {
            if (CompilerDirectives.injectBranchProbability(CompilerDirectives.UNLIKELY_PROBABILITY, cap > storage.getCapacity())) {
                var context = getContext(inliningTarget);
                long newCapacityInBytes = storage.getTypeWidth() * cap;
                var oldNativeBuffer = storage.getNativeBuffer();
                var newNativeBuffer = context.nativeBufferContext.createNativeBuffer(newCapacityInBytes);
                context.getUnsafe().copyMemory(oldNativeBuffer.getMemoryAddress(), newNativeBuffer.getMemoryAddress(), oldNativeBuffer.getCapacityInBytes());
                storage.setCapacity(cap);
                storage.setNativeBuffer(newNativeBuffer);
            }
        }

        @Specialization
        static void doBool(BoolSequenceStorage storage, int cap) {
            storage.ensureCapacity(cap);
        }

        @Specialization
        @InliningCutoff
        static void doNative(NativeSequenceStorage s, int cap,
                        @Cached(inline = false) EnsureCapacityNativeNode helper) {
            helper.execute(s, cap);
        }

        @Specialization
        static void doMro(MroSequenceStorage storage, int cap) {
            throw CompilerDirectives.shouldNotReachHere();
        }

        @GenerateInline(false)
        @GenerateUncached
        abstract static class EnsureCapacityNativeNode extends Node {
            abstract void execute(NativeSequenceStorage s, int cap);

            @Specialization
            static void doNativeByte(NativeByteSequenceStorage s, int cap,
                            @Bind("this") Node inliningTarget,
                            @Shared @CachedLibrary(limit = "2") InteropLibrary lib,
                            @Shared @Cached CStructAccess.AllocateNode alloc,
                            @Shared @Cached CStructAccess.FreeNode free,
                            @Shared @Cached PRaiseNode.Lazy raiseNode,
                            @Cached CStructAccess.ReadByteNode read,
                            @Cached CStructAccess.WriteByteNode write) {
                int oldCapacity = s.getCapacity();
                if (cap > oldCapacity) {
                    int newCapacity = computeNewCapacity(cap);
                    Object oldMem = s.getPtr();
                    Object newMem = alloc.alloc(newCapacity);
                    if (lib.isNull(newMem)) {
                        throw raiseNode.get(inliningTarget).raise(MemoryError);
                    }
                    // TODO: turn this into a memcpy
                    for (long i = 0; i < oldCapacity; i++) {
                        write.writeArrayElement(newMem, i, read.readArrayElement(oldMem, i));
                    }
                    free.free(oldMem);
                    s.setPtr(newMem);
                    s.setCapacity(newCapacity);
                }
            }

            @Specialization
            static void doNativeObject(NativeObjectSequenceStorage s, int cap,
                            @Bind("this") Node inliningTarget,
                            @Shared @CachedLibrary(limit = "2") InteropLibrary lib,
                            @Shared @Cached CStructAccess.AllocateNode alloc,
                            @Shared @Cached CStructAccess.FreeNode free,
                            @Shared @Cached PRaiseNode.Lazy raiseNode,
                            @Cached CStructAccess.ReadPointerNode read,
                            @Cached CStructAccess.WritePointerNode write) {
                int oldCapacity = s.getCapacity();
                if (cap > oldCapacity) {
                    int newCapacity = computeNewCapacity(cap);
                    Object oldMem = s.getPtr();
                    long bytes = newCapacity * 8;
                    Object newMem = alloc.alloc(bytes);
                    if (lib.isNull(newMem)) {
                        throw raiseNode.get(inliningTarget).raise(MemoryError);
                    }
                    // TODO: turn this into a memcpy
                    for (long i = 0; i < oldCapacity; i++) {
                        write.writeArrayElement(newMem, i, read.readArrayElement(oldMem, i));
                    }
                    free.free(oldMem);
                    s.setPtr(newMem);
                    s.setCapacity(newCapacity);
                }
            }

            private static int computeNewCapacity(int cap) {
                int newCapacity;
                try {
                    newCapacity = Math.max(16, PythonUtils.multiplyExact(cap, 2));
                } catch (OverflowException e) {
                    newCapacity = cap;
                }
                return newCapacity;
            }
        }
    }

    @GenerateUncached
    @GenerateInline
    @GenerateCached(false)
    public abstract static class CopyNode extends Node {

        public abstract SequenceStorage execute(Node inliningTarget, SequenceStorage s);

        public static SequenceStorage executeUncached(SequenceStorage s) {
            return SequenceStorageNodesFactory.CopyNodeGen.getUncached().execute(null, s);
        }

        @Specialization
        static SequenceStorage doEmpty(EmptySequenceStorage s) {
            return s;
        }

        @Specialization
        static SequenceStorage doInt(IntSequenceStorage storage) {
            return new IntSequenceStorage(PythonUtils.arrayCopyOf(storage.getInternalIntArray(), storage.length()));
        }

        @Specialization
        static SequenceStorage doLong(LongSequenceStorage storage) {
            return new LongSequenceStorage(PythonUtils.arrayCopyOf(storage.getInternalLongArray(), storage.length()));
        }

        @Specialization
        static SequenceStorage doDouble(DoubleSequenceStorage storage) {
            return new DoubleSequenceStorage(PythonUtils.arrayCopyOf(storage.getInternalDoubleArray(), storage.length()));
        }

        @Specialization
        static SequenceStorage doByte(ByteSequenceStorage storage) {
            return new ByteSequenceStorage(PythonUtils.arrayCopyOf(storage.getInternalByteArray(), storage.length()));
        }

        @Specialization
        static SequenceStorage doBoolean(BoolSequenceStorage storage) {
            return new BoolSequenceStorage(PythonUtils.arrayCopyOf(storage.getInternalBoolArray(), storage.length()));
        }

        @Specialization
        static SequenceStorage doObject(ObjectSequenceStorage storage) {
            return new ObjectSequenceStorage(PythonUtils.arrayCopyOf(storage.getInternalObjectArray(), storage.length()));
        }

        @Specialization
        static SequenceStorage doMro(MroSequenceStorage storage) {
            return new ObjectSequenceStorage(PythonUtils.arrayCopyOf(storage.getInternalClassArray(), storage.length()));
        }

        @Specialization
        static SequenceStorage doArrowInt(Node inliningTarget, IntArrowSequenceStorage storage) {
            var copiedBuffer = copyNativeBuffer(inliningTarget, storage.getNativeBuffer());
            return new IntArrowSequenceStorage(copiedBuffer, storage.length());
        }

        @Specialization
        static SequenceStorage doNativeBytes(NativeByteSequenceStorage s,
                        @Shared @Cached(inline = false) GetNativeItemScalarNode getItem) {
            byte[] bytes = new byte[s.length()];
            for (int i = 0; i < s.length(); i++) {
                bytes[i] = (byte) (int) getItem.execute(s, i);
            }
            return new ByteSequenceStorage(bytes);
        }

        @Specialization
        static SequenceStorage doNativeObjects(NativeObjectSequenceStorage s,
                        @Shared @Cached(inline = false) GetNativeItemScalarNode getItem) {
            Object[] objects = new Object[s.length()];
            for (int i = 0; i < s.length(); i++) {
                objects[i] = getItem.execute(s, i);
            }
            return new ObjectSequenceStorage(objects);
        }

        private static NativeBuffer copyNativeBuffer(Node inliningTarget, NativeBuffer buffer) {
            var context = PythonContext.get(inliningTarget);
            var unsafe = context.getUnsafe();
            var newBuffer = context.nativeBufferContext.createNativeBuffer(buffer.getCapacityInBytes());

            unsafe.copyMemory(buffer.getMemoryAddress(), newBuffer.getMemoryAddress(), buffer.getCapacityInBytes());

            return newBuffer;
        }
    }

    @GenerateUncached
    @GenerateInline
    @GenerateCached(false)
    @ImportStatic(SequenceStorageBaseNode.class)
    public abstract static class CopyInternalArrayNode extends Node {

        public abstract Object[] execute(Node node, SequenceStorage s);

        public static Object[] executeUncached(SequenceStorage s) {
            return SequenceStorageNodesFactory.CopyInternalArrayNodeGen.getUncached().execute(null, s);
        }

        @Specialization
        static Object[] doInt(IntSequenceStorage storage) {
            return storage.getCopyOfInternalArray();
        }

        @Specialization
        static Object[] doDouble(DoubleSequenceStorage storage) {
            return storage.getCopyOfInternalArray();
        }

        @Specialization
        static Object[] doByte(ByteSequenceStorage storage) {
            return storage.getCopyOfInternalArray();
        }

        @Specialization
        static Object[] doLong(LongSequenceStorage storage) {
            return storage.getCopyOfInternalArray();
        }

        @Specialization
        static Object[] doObject(ObjectSequenceStorage storage) {
            return storage.getCopyOfInternalArray();
        }

        @Specialization
        static Object[] doBool(BoolSequenceStorage storage) {
            return storage.getCopyOfInternalArray();
        }

        @Specialization
        static Object[] doMro(MroSequenceStorage storage) {
            return storage.getCopyOfInternalArray();
        }

        @Specialization
        static Object[] doEmpty(@SuppressWarnings("unused") EmptySequenceStorage s) {
            return PythonUtils.EMPTY_OBJECT_ARRAY;
        }

        @Specialization
        static Object[] doNative(NativeSequenceStorage s,
                        @Cached(inline = false) GetNativeItemScalarNode getNativeItemScalarNode) {
            Object[] result = new Object[s.length()];
            for (int i = 0; i < s.length(); i++) {
                result[i] = getNativeItemScalarNode.execute(s, i);
            }
            return result;
        }
    }

    @GenerateUncached
    @GenerateInline
    @GenerateCached(false)
    @ImportStatic(SequenceStorageBaseNode.class)
    public abstract static class SetLenNode extends Node {

        public abstract void execute(Node inliningTarget, SequenceStorage s, int len);

        @Specialization
        static void doBasic(ArrayBasedSequenceStorage s, int len) {
            s.setNewLength(len);
        }

        @Specialization
        static void doEmpty(@SuppressWarnings("unused") EmptySequenceStorage s, int len) {
            assert len == 0;
        }

        @Specialization
        static void doNative(NativeSequenceStorage s, int len,
                        @Cached(inline = false) SetNativeLenNode setLen) {
            setLen.execute(s, len);
        }

        @Specialization
        static void doArrow(ArrowSequenceStorage s, int len) {
            s.setNewLength(len);
        }
    }

    @GenerateUncached
    @GenerateInline(false) // Uncommon node
    public abstract static class SetNativeLenNode extends Node {

        public abstract void execute(NativeSequenceStorage s, int len);

        @Specialization(guards = "len < s.length()")
        @InliningCutoff
        static void doShrink(NativeObjectSequenceStorage s, int len,
                        @Bind("this") Node inliningTarget,
                        @Cached CStructAccess.ReadPointerNode readNode,
                        @Cached CExtNodes.XDecRefPointerNode decRefPointerNode) {
            if (len < s.length()) {
                // When shrinking, we need to decref the items that are now past the end
                for (int i = len; i < s.length(); i++) {
                    Object elementPointer = readNode.readArrayElement(s.getPtr(), i);
                    decRefPointerNode.execute(inliningTarget, elementPointer);
                }
            }
            s.setNewLength(len);
        }

        @Fallback
        static void doOther(NativeSequenceStorage s, int len) {
            s.setNewLength(len);
        }
    }

    public abstract static class DeleteNode extends NormalizingNode {

        public DeleteNode(NormalizeIndexNode normalizeIndexNode) {
            super(normalizeIndexNode);
        }

        public abstract void execute(VirtualFrame frame, SequenceStorage s, Object indexOrSlice);

        public abstract void execute(VirtualFrame frame, SequenceStorage s, int index);

        public abstract void execute(VirtualFrame frame, SequenceStorage s, long index);

        @Specialization
        protected void doScalarInt(SequenceStorage storage, int idx,
                        @Bind("this") Node inliningTarget,
                        @Shared @Cached DeleteItemNode deleteItemNode) {
            deleteItemNode.execute(inliningTarget, storage, normalizeIndex(idx, storage));
        }

        @Specialization
        protected void doScalarLong(VirtualFrame frame, SequenceStorage storage, long idx,
                        @Bind("this") Node inliningTarget,
                        @Shared @Cached DeleteItemNode deleteItemNode,
                        @Shared @Cached PyNumberAsSizeNode asSizeNode) {
            deleteItemNode.execute(inliningTarget, storage, normalizeIndex(frame, inliningTarget, asSizeNode, idx, storage));
        }

        @Specialization
        protected void doScalarPInt(VirtualFrame frame, SequenceStorage storage, PInt idx,
                        @Bind("this") Node inliningTarget,
                        @Shared @Cached DeleteItemNode deleteItemNode,
                        @Shared @Cached PyNumberAsSizeNode asSizeNode) {
            deleteItemNode.execute(inliningTarget, storage, normalizeIndex(frame, inliningTarget, asSizeNode, idx, storage));
        }

        @Specialization(guards = "!isPSlice(idx)")
        protected void doScalarGeneric(VirtualFrame frame, SequenceStorage storage, Object idx,
                        @Bind("this") Node inliningTarget,
                        @Shared @Cached DeleteItemNode deleteItemNode,
                        @Shared @Cached PyNumberAsSizeNode asSizeNode) {
            deleteItemNode.execute(inliningTarget, storage, normalizeIndex(frame, inliningTarget, asSizeNode, idx, storage));
        }

        @Specialization
        protected static void doSlice(SequenceStorage storage, PSlice slice,
                        @Bind("this") Node inliningTarget,
                        @Cached CoerceToIntSlice sliceCast,
                        @Cached SliceNodes.SliceUnpack unpack,
                        @Cached SliceNodes.AdjustIndices adjustIndices,
                        @Cached DeleteSliceNode deleteSliceNode) {
            int len = storage.length();
            SliceInfo unadjusted = unpack.execute(inliningTarget, sliceCast.execute(inliningTarget, slice));
            SliceInfo info = adjustIndices.execute(inliningTarget, len, unadjusted);
            try {
                deleteSliceNode.execute(inliningTarget, storage, info);
            } catch (SequenceStoreException e) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                throw new IllegalStateException();
            }
        }

        @NeverDefault
        public static DeleteNode create(NormalizeIndexNode normalizeIndexNode) {
            return DeleteNodeGen.create(normalizeIndexNode);
        }

        @NeverDefault
        public static DeleteNode create() {
            return DeleteNodeGen.create(NormalizeIndexNode.create());
        }
    }

    @GenerateUncached
    @GenerateInline
    @GenerateCached(false)
    public abstract static class DeleteItemNode extends SequenceStorageBaseNode {

        public abstract void execute(Node node, SequenceStorage s, int idx);

        @Specialization(guards = "isLastItem(s, idx)")
        static void doLastItem(Node inliningTarget, SequenceStorage s, @SuppressWarnings("unused") int idx,
                        @Exclusive @Cached SetLenNode setLenNode) {
            setLenNode.execute(inliningTarget, s, s.length() - 1);
        }

        @Specialization
        static void doGeneric(Node inliningTarget, SequenceStorage s, int idx,
                        @Cached GetItemScalarNode getItemNode,
                        @Cached SetItemScalarNode setItemNode,
                        @Exclusive @Cached SetLenNode setLenNode) {
            int len = s.length();

            for (int i = idx; i < len - 1; i++) {
                setItemNode.execute(inliningTarget, s, i, getItemNode.execute(inliningTarget, s, i + 1));
            }
            setLenNode.execute(inliningTarget, s, len - 1);
        }

        protected static boolean isLastItem(SequenceStorage s, int idx) {
            return idx == s.length() - 1;
        }
    }

    @GenerateInline
    @GenerateCached(false)
    public abstract static class DeleteSliceNode extends SequenceStorageBaseNode {

        public abstract void execute(Node inliningTarget, SequenceStorage s, SliceInfo info);

        @Specialization(guards = "sinfo.step == 1")
        static void singleStep(Node inliningTarget, SequenceStorage store, SliceInfo sinfo,
                        @Cached InlinedConditionProfile shortCircuitProfile,
                        @Exclusive @Cached SetLenNode setLenNode,
                        @Exclusive @Cached MemMoveNode memove) {
            int length = store.length();
            int sliceLength = sinfo.sliceLength;

            if (shortCircuitProfile.profile(inliningTarget, sliceLength == 0)) {
                return;
            }
            int ilow = sinfo.start;
            int ihigh = sinfo.stop;
            int n = 0; /* # of elements in replacement list */

            ilow = (ilow < 0) ? 0 : Math.min(ilow, length);
            ihigh = (ihigh < ilow) ? ilow : Math.min(ihigh, length);

            int norig = ihigh - ilow; /* # of elements in list getting replaced */
            assert norig >= 0 : "Something wrong with slice info";
            int d = n - norig; /* Change in size */
            if (length + d == 0) {
                setLenNode.execute(inliningTarget, store, 0);
                return;
            }

            if (d == 0) {
                return;
            }

            int tail = length - ihigh;
            memove.execute(inliningTarget, store, ihigh + d, ihigh, tail);

            // change the result length
            // TODO reallocate array if the change is big?
            // Then unnecessary big array is kept in the memory.
            setLenNode.execute(inliningTarget, store, length + d);
        }

        @Specialization(guards = "sinfo.step != 1")
        static void multipleSteps(Node inliningTarget, SequenceStorage store, SliceInfo sinfo,
                        @Cached EnsureCapacityNode ensureCapacityNode,
                        @Exclusive @Cached SetLenNode setLenNode,
                        @Exclusive @Cached MemMoveNode memove) {
            multipleSteps(store, sinfo, inliningTarget, setLenNode, ensureCapacityNode, memove);
        }

        static void multipleSteps(SequenceStorage self, PSlice.SliceInfo sinfo,
                        Node inliningTarget,
                        SetLenNode setLenNode,
                        EnsureCapacityNode ensureCapacityNode,
                        MemMoveNode memove) {
            int start, stop, step, slicelen;
            start = sinfo.start;
            step = sinfo.step;
            slicelen = sinfo.sliceLength;
            int len = self.length();
            step = step > (len + 1) ? len : step;
            /*- Delete slice */
            int cur;
            int i;

            // ensure capacity will check if the storage can be resized.
            ensureCapacityNode.execute(inliningTarget, self, len - slicelen);

            if (slicelen == 0) {
                /*- Nothing to do here. */
                return;
            }

            if (step < 0) {
                stop = start + 1;
                start = stop + step * (slicelen - 1) - 1;
                step = -step;
            }
            for (cur = start, i = 0; i < slicelen; cur += step, i++) {
                int lim = step - 1;

                if (cur + step >= len) {
                    lim = len - cur - 1;
                }

                memove.execute(inliningTarget, self, cur - i, cur + 1, lim);
            }
            /*- Move the tail of the bytes, in one chunk */
            cur = start + slicelen * step;
            if (cur < len) {
                memove.execute(inliningTarget, self, cur - slicelen, cur, len - cur);
            }

            // change the result length
            // TODO reallocate array if the change is big?
            // Then unnecessary big array is kept in the memory.
            setLenNode.execute(inliningTarget, self, len - slicelen);
        }
    }

    @GenerateUncached
    @GenerateInline
    @GenerateCached(false)
    @ImportStatic(SequenceStorageBaseNode.class)
    public abstract static class GetElementType extends Node {

        public abstract SequenceStorage.StorageType execute(Node inliningTarget, SequenceStorage s);

        @Specialization(limit = "MAX_SEQUENCE_STORAGES", guards = {"s.getClass() == cachedClass"})
        static StorageType doCached(SequenceStorage s,
                        @Cached("s.getClass()") Class<? extends SequenceStorage> cachedClass) {
            return cachedClass.cast(s).getElementType();
        }

        @Specialization(replaces = "doCached")
        static SequenceStorage.StorageType doUncached(SequenceStorage s) {
            return s.getElementType();
        }

        public static GetElementType getUncached() {
            return GetElementTypeNodeGen.getUncached();
        }
    }

    @GenerateInline
    @GenerateCached(false)
    @ImportStatic(SpecialMethodNames.class)
    public abstract static class ItemIndexNode extends SequenceStorageBaseNode {

        public abstract int execute(VirtualFrame frame, Node inliningTarget, SequenceStorage s, Object item, int start, int end);

        @Specialization
        int doBoolean(BoolSequenceStorage s, boolean item, int start, int end) {
            for (int i = start; i < getLength(s, end); i++) {
                if (s.getBoolItemNormalized(i) == item) {
                    return i;
                }
            }
            return -1;
        }

        @Specialization
        int doInt(IntSequenceStorage s, int item, int start, int end) {
            for (int i = start; i < getLength(s, end); i++) {
                if (s.getIntItemNormalized(i) == item) {
                    return i;
                }
            }
            return -1;
        }

        @Specialization
        int doByte(ByteSequenceStorage s, int item, int start, int end) {
            for (int i = start; i < getLength(s, end); i++) {
                if (s.getIntItemNormalized(i) == item) {
                    return i;
                }
            }
            return -1;
        }

        @Specialization
        int doLong(LongSequenceStorage s, long item, int start, int end) {
            for (int i = start; i < getLength(s, end); i++) {
                if (s.getLongItemNormalized(i) == item) {
                    return i;
                }
            }
            return -1;
        }

        @Specialization
        int doDouble(DoubleSequenceStorage s, double item, int start, int end) {
            for (int i = start; i < getLength(s, end); i++) {
                if (java.lang.Double.compare(s.getDoubleItemNormalized(i), item) == 0) {
                    return i;
                }
            }
            return -1;
        }

        @Specialization
        static int doGeneric(VirtualFrame frame, Node inliningTarget, SequenceStorage s, Object item, int start, int end,
                        @Cached GetItemScalarNode getItemNode,
                        @Cached PyObjectRichCompareBool.EqNode eqNode) {
            for (int i = start; i < getLength(s, end); i++) {
                Object seqItem = getItemNode.execute(inliningTarget, s, i);
                if (eqNode.compare(frame, inliningTarget, seqItem, item)) {
                    return i;
                }
            }
            return -1;
        }

        private static int getLength(SequenceStorage s, int end) {
            return Math.min(s.length(), end);
        }
    }

    @GenerateUncached
    @GenerateInline
    @GenerateCached(false)
    public abstract static class GetInternalObjectArrayNode extends Node {

        public abstract Object[] execute(Node inliningTarget, SequenceStorage s);

        public static Object[] executeUncached(SequenceStorage s) {
            return SequenceStorageNodesFactory.GetInternalObjectArrayNodeGen.getUncached().execute(null, s);
        }

        @Specialization
        static Object[] doObjectSequenceStorage(ObjectSequenceStorage s) {
            return s.getInternalObjectArray();
        }

        @Specialization
        static Object[] doMroSequenceStorage(MroSequenceStorage s) {
            return s.getInternalClassArray();
        }

        @Specialization(guards = {"!isObjectStorage(s)", "!isMroStorage(s)"})
        static Object[] doArrayBasedSequenceStorage(Node inliningTarget, ArrayBasedSequenceStorage s,
                        @Cached CopyInternalArrayNode copy) {
            Object[] internalArray = copy.execute(inliningTarget, s);
            assert internalArray.length == s.length();
            return internalArray;
        }

        @Specialization
        static Object[] doNativeObject(Node inliningTarget, NativeSequenceStorage s,
                        @Exclusive @Cached GetItemScalarNode getItemNode) {
            return materializeGeneric(inliningTarget, s, s.length(), getItemNode);
        }

        @Specialization
        static Object[] doEmptySequenceStorage(@SuppressWarnings("unused") EmptySequenceStorage s) {
            return PythonUtils.EMPTY_OBJECT_ARRAY;
        }

        @Specialization(guards = {"!isObjectStorage(s)", "!isMroStorage(s)"}, replaces = {"doArrayBasedSequenceStorage", "doNativeObject", "doEmptySequenceStorage"})
        static Object[] doGeneric(Node inliningTarget, SequenceStorage s,
                        @Exclusive @Cached GetItemScalarNode getItemNode) {
            return materializeGeneric(inliningTarget, s, s.length(), getItemNode);
        }

        private static Object[] materializeGeneric(Node inliningTarget, SequenceStorage s, int len, GetItemScalarNode getItemNode) {
            Object[] barr = new Object[len];
            for (int i = 0; i < barr.length; i++) {
                barr[i] = getItemNode.execute(inliningTarget, s, i);
            }
            return barr;
        }

        protected static boolean isObjectStorage(SequenceStorage storage) {
            return storage instanceof ObjectSequenceStorage;
        }

        protected static boolean isMroStorage(SequenceStorage storage) {
            return storage instanceof MroSequenceStorage;
        }
    }

    @GenerateUncached
    @GenerateInline
    @GenerateCached(false)
    public abstract static class ToArrayNode extends Node {
        public abstract Object[] execute(Node inliningTarget, SequenceStorage s);

        @Specialization
        static Object[] doObjectSequenceStorage(Node inliningTarget, ObjectSequenceStorage s,
                        @Cached InlinedConditionProfile profile) {
            Object[] objects = GetInternalObjectArrayNode.doObjectSequenceStorage(s);
            int storageLength = s.length();
            if (profile.profile(inliningTarget, storageLength != objects.length)) {
                return exactCopy(objects, storageLength);
            }
            return objects;
        }

        @Specialization(guards = "!isObjectSequenceStorage(s)")
        static Object[] doOther(Node inliningTarget, SequenceStorage s,
                        @Cached GetInternalObjectArrayNode getInternalObjectArrayNode) {
            return getInternalObjectArrayNode.execute(inliningTarget, s);
        }

        private static Object[] exactCopy(Object[] barr, int len) {
            return PythonUtils.arrayCopyOf(barr, len);
        }

        static boolean isObjectSequenceStorage(SequenceStorage s) {
            return s instanceof ObjectSequenceStorage;
        }

    }

    @GenerateUncached
    @GenerateInline
    @ImportStatic(PInt.class)
    public abstract static class InsertItemArrayBasedStorageNode extends Node {

        public static SequenceStorage executeUncached(ArrayBasedSequenceStorage storage, int index, Object value) {
            return SequenceStorageNodesFactory.InsertItemArrayBasedStorageNodeGen.getUncached().execute(null, storage, index, value);
        }

        protected abstract SequenceStorage execute(Node inliningTarget, ArrayBasedSequenceStorage storage, int index, Object value);

        @Specialization
        static SequenceStorage doIntStorage(IntSequenceStorage storage, int idx, int value) {
            storage.insertIntItem(idx, value);
            return storage;
        }

        @Specialization
        static SequenceStorage doDoubleStorage(DoubleSequenceStorage storage, int idx, double value) {
            storage.insertDoubleItem(idx, value);
            return storage;
        }

        @Specialization
        static SequenceStorage doLongWithLongStorage(LongSequenceStorage storage, int idx, long value) {
            storage.insertLongItem(idx, value);
            return storage;
        }

        @Specialization
        static SequenceStorage doIntWithLongStorage(LongSequenceStorage storage, int idx, int value) {
            storage.insertLongItem(idx, value);
            return storage;
        }

        @Specialization(guards = "isIntRange(value)")
        static SequenceStorage doLongWithIntStorage(IntSequenceStorage storage, int idx, long value) {
            storage.insertIntItem(idx, (int) value);
            return storage;
        }

        @Specialization
        static SequenceStorage doByteWithByteStorage(ByteSequenceStorage storage, int idx, byte value) {
            storage.insertByteItem(idx, value);
            return storage;
        }

        @Specialization
        static SequenceStorage doBoolStorage(BoolSequenceStorage storage, int idx, boolean value) {
            storage.insertBoolItem(idx, value);
            return storage;
        }

        @Specialization
        static SequenceStorage doObjectStorage(ObjectSequenceStorage storage, int idx, Object value) {
            storage.insertItem(idx, value);
            return storage;
        }

        @Specialization
        static SequenceStorage doMroStorage(MroSequenceStorage storage, int idx, Object value) {
            throw CompilerDirectives.shouldNotReachHere();
        }

        @Fallback
        static SequenceStorage doGeneralization(Node inliningTarget, ArrayBasedSequenceStorage storage, int idx, Object value,
                        @Cached GetInternalObjectArrayNode getInternalObjectArrayNode) {
            Object[] values = getInternalObjectArrayNode.execute(inliningTarget, storage);
            ObjectSequenceStorage newStorage = new ObjectSequenceStorage(values);
            newStorage.insertItem(idx, value);
            return newStorage;
        }
    }

    @GenerateUncached
    @GenerateInline
    @GenerateCached(false)
    @ImportStatic(SequenceStorageBaseNode.class)
    public abstract static class InsertItemNode extends Node {
        public static SequenceStorage executeUncached(SequenceStorage storage, int index, Object value) {
            return InsertItemNodeGen.getUncached().execute(null, storage, index, value);
        }

        public abstract SequenceStorage execute(Node inliningTarget, SequenceStorage storage, int index, Object value);

        @Specialization
        protected static SequenceStorage doEmptyStorage(Node inliningTarget, EmptySequenceStorage storage, int index, Object value,
                        @Shared @Cached InsertItemArrayBasedStorageNode insertArrayBasedNode) {
            ArrayBasedSequenceStorage newStorage = storage.generalizeFor(value);
            return insertArrayBasedNode.execute(inliningTarget, newStorage, index, value);
        }

        @Specialization
        static SequenceStorage doArrayBasedStorage(Node inliningTarget, ArrayBasedSequenceStorage storage, int index, Object value,
                        @Shared @Cached InsertItemArrayBasedStorageNode insertArrayBasedNode) {
            return insertArrayBasedNode.execute(inliningTarget, storage, index, value);

        }

        // TODO introduce something similar to InsertItemArrayBasedStorageNode
        @Specialization
        static SequenceStorage doArrowInt(Node inliningTarget, IntArrowSequenceStorage storage, int index, int value,
                        @Exclusive @Cached EnsureCapacityNode ensureCapacity) {
            int length = storage.length();
            var context = PythonContext.get(inliningTarget);
            var unsafe = context.getUnsafe();
            long typeWidth = storage.getTypeWidth();
            ensureCapacity.execute(inliningTarget, storage, length + 1);
            // shifting tail to the right by one slot
            long startAddr = storage.getNativeBuffer().getMemoryAddress() + (index * typeWidth);
            long endAddr = startAddr + typeWidth;
            long sizeInBytes = (length - index) * typeWidth;
            unsafe.copyMemory(startAddr, endAddr, sizeInBytes);

            storage.setIntItemNormalized(index, value);
            storage.incLength();
            return storage;
        }

        @Specialization
        protected static SequenceStorage doNativeStorage(Node inliningTarget, NativeSequenceStorage storage, int index, Object value,
                        @Exclusive @Cached EnsureCapacityNode ensureCapacityNode,
                        @Cached(inline = false) GetItemScalarNode getItem,
                        @Cached SetItemScalarNode setItem) {
            int newLength = storage.length() + 1;
            ensureCapacityNode.execute(inliningTarget, storage, newLength);
            for (int i = storage.length(); i > index; i--) {
                setItem.execute(inliningTarget, storage, i, getItem.execute(inliningTarget, storage, i - 1));
            }
            setItem.execute(inliningTarget, storage, index, value);
            storage.setNewLength(newLength);
            return storage;
        }
    }

    public abstract static class CreateStorageFromIteratorNode extends Node {
        public abstract SequenceStorage execute(VirtualFrame frame, Object iterator, int len);

        public final SequenceStorage execute(VirtualFrame frame, Object iterator) {
            return execute(frame, iterator, -1);
        }

        private static final int START_SIZE = 4;

        protected SequenceStorage createStorage(VirtualFrame frame, Object iterator, int len, StorageType type, GetNextNode nextNode, IsBuiltinObjectProfile errorProfile,
                        Node inliningTarget, InlinedCountingConditionProfile growArrayProfile) {
            final int size = len > 0 ? len : START_SIZE;
            if (type == Uninitialized || type == Empty) {
                return createStorageUninitialized(frame, inliningTarget, iterator, nextNode, errorProfile, size);
            } else {
                int i = 0;
                Object array = null;
                try {
                    switch (type) {
                        case Boolean: {
                            boolean[] elements = new boolean[size];
                            array = elements;
                            try {
                                while (true) {
                                    boolean value = nextNode.executeBoolean(frame, iterator);
                                    if (growArrayProfile.profile(inliningTarget, i >= elements.length)) {
                                        array = elements = PythonUtils.arrayCopyOf(elements, elements.length * 2);
                                    }
                                    elements[i++] = value;
                                }
                            } catch (PException e) {
                                LoopNode.reportLoopCount(this, i);
                                e.expectStopIteration(inliningTarget, errorProfile);
                            }
                            return new BoolSequenceStorage(elements, i);
                        }
                        case Byte: {
                            byte[] elements = new byte[size];
                            array = elements;
                            try {
                                while (true) {
                                    int value = nextNode.executeInt(frame, iterator);
                                    byte bvalue;
                                    try {
                                        bvalue = PInt.byteValueExact(value);
                                        if (growArrayProfile.profile(inliningTarget, i >= elements.length)) {
                                            array = elements = PythonUtils.arrayCopyOf(elements, elements.length * 2);
                                        }
                                        elements[i++] = bvalue;
                                    } catch (OverflowException e) {
                                        throw new UnexpectedResultException(value);
                                    }
                                }
                            } catch (PException e) {
                                LoopNode.reportLoopCount(this, i);
                                e.expectStopIteration(inliningTarget, errorProfile);
                            }
                            return new ByteSequenceStorage(elements, i);
                        }
                        case Int: {
                            int[] elements = new int[size];
                            array = elements;
                            try {
                                while (true) {
                                    int value = nextNode.executeInt(frame, iterator);
                                    if (growArrayProfile.profile(inliningTarget, i >= elements.length)) {
                                        array = elements = PythonUtils.arrayCopyOf(elements, elements.length * 2);
                                    }
                                    elements[i++] = value;
                                }
                            } catch (PException e) {
                                LoopNode.reportLoopCount(this, i);
                                e.expectStopIteration(inliningTarget, errorProfile);
                            }
                            return new IntSequenceStorage(elements, i);
                        }
                        case Long: {
                            long[] elements = new long[size];
                            array = elements;
                            try {
                                while (true) {
                                    long value = nextNode.executeLong(frame, iterator);
                                    if (growArrayProfile.profile(inliningTarget, i >= elements.length)) {
                                        array = elements = PythonUtils.arrayCopyOf(elements, elements.length * 2);
                                    }
                                    elements[i++] = value;
                                }
                            } catch (PException e) {
                                LoopNode.reportLoopCount(this, i);
                                e.expectStopIteration(inliningTarget, errorProfile);
                            }
                            return new LongSequenceStorage(elements, i);
                        }
                        case Double: {
                            double[] elements = new double[size];
                            array = elements;
                            try {
                                while (true) {
                                    double value = nextNode.executeDouble(frame, iterator);
                                    if (growArrayProfile.profile(inliningTarget, i >= elements.length)) {
                                        array = elements = PythonUtils.arrayCopyOf(elements, elements.length * 2);
                                    }
                                    elements[i++] = value;
                                }
                            } catch (PException e) {
                                LoopNode.reportLoopCount(this, i);
                                e.expectStopIteration(inliningTarget, errorProfile);
                            }
                            return new DoubleSequenceStorage(elements, i);
                        }
                        case Generic: {
                            Object[] elements = new Object[size];
                            try {
                                while (true) {
                                    Object value = nextNode.execute(frame, iterator);
                                    if (growArrayProfile.profile(inliningTarget, i >= elements.length)) {
                                        elements = PythonUtils.arrayCopyOf(elements, elements.length * 2);
                                    }
                                    elements[i++] = value;
                                }
                            } catch (PException e) {
                                LoopNode.reportLoopCount(this, i);
                                e.expectStopIteration(inliningTarget, errorProfile);
                            }
                            return new ObjectSequenceStorage(elements, i);
                        }
                        default:
                            CompilerDirectives.transferToInterpreterAndInvalidate();
                            throw new RuntimeException("unexpected state");
                    }
                } catch (UnexpectedResultException e) {
                    return genericFallback(frame, iterator, array, i, e.getResult(), nextNode, errorProfile, inliningTarget, growArrayProfile);
                }
            }
        }

        private SequenceStorage createStorageUninitialized(VirtualFrame frame, Node inliningTarget, Object iterator, GetNextNode nextNode, IsBuiltinObjectProfile errorProfile, int size) {
            Object[] elements = new Object[size];
            int i = 0;
            while (true) {
                try {
                    Object value = nextNode.execute(frame, iterator);
                    if (i >= elements.length) {
                        // Intentionally not profiled, because "size" can be reprofiled after this
                        // first initialization run
                        elements = PythonUtils.arrayCopyOf(elements, elements.length * 2);
                    }
                    elements[i++] = value;
                } catch (PException e) {
                    e.expectStopIteration(inliningTarget, errorProfile);
                    LoopNode.reportLoopCount(this, i);
                    break;
                }
            }
            return SequenceStorageFactory.createStorage(PythonUtils.arrayCopyOf(elements, i));
        }

        private SequenceStorage genericFallback(VirtualFrame frame, Object iterator, Object array, int count, Object result, GetNextNode nextNode, IsBuiltinObjectProfile errorProfile,
                        Node inliningTarget, InlinedCountingConditionProfile growArrayProfile) {
            Object[] elements = new Object[Array.getLength(array) * 2];
            int i = 0;
            for (; i < count; i++) {
                elements[i] = Array.get(array, i);
            }
            elements[i++] = result;
            while (true) {
                try {
                    Object value = nextNode.execute(frame, iterator);
                    if (growArrayProfile.profile(inliningTarget, i >= elements.length)) {
                        elements = PythonUtils.arrayCopyOf(elements, elements.length * 2);
                    }
                    elements[i++] = value;
                } catch (PException e) {
                    LoopNode.reportLoopCount(this, i);
                    e.expectStopIteration(inliningTarget, errorProfile);
                    break;
                }
            }
            return new ObjectSequenceStorage(elements, i);
        }

        /**
         * This version is specific to builtin iterators and looks for STOP_MARKER instead of
         * StopIteration.
         */
        protected static SequenceStorage createStorageFromBuiltin(VirtualFrame frame, PBuiltinIterator iterator, int len, SequenceStorage.StorageType type, NextHelperNode nextNode,
                        IsBuiltinObjectProfile errorProfile, Node inliningTarget, InlinedCountingConditionProfile growArrayProfile, InlinedLoopConditionProfile loopProfile) {
            final int size = len > 0 ? len : START_SIZE;
            if (type == Uninitialized || type == Empty) {
                Object[] elements = new Object[size];
                int i = 0;
                try {
                    Object value;
                    for (; loopProfile.profile(inliningTarget, (value = nextNode.execute(frame, inliningTarget, iterator, false)) != STOP_MARKER); i++) {
                        if (growArrayProfile.profile(inliningTarget, i >= elements.length)) {
                            elements = PythonUtils.arrayCopyOf(elements, elements.length * 2);
                        }
                        elements[i] = value;
                    }
                } catch (PException e) {
                    e.expectStopIteration(inliningTarget, errorProfile);
                }
                return SequenceStorageFactory.createStorage(PythonUtils.arrayCopyOf(elements, i));
            } else {
                int i = 0;
                Object array = null;
                try {
                    Object value;
                    switch (type) {
                        case Boolean: {
                            boolean[] elements = new boolean[size];
                            array = elements;
                            try {
                                for (; loopProfile.profile(inliningTarget, (value = nextNode.execute(frame, inliningTarget, iterator, false)) != STOP_MARKER); i++) {
                                    if (growArrayProfile.profile(inliningTarget, i >= elements.length)) {
                                        elements = PythonUtils.arrayCopyOf(elements, elements.length * 2);
                                        array = elements;
                                    }
                                    elements[i] = PGuards.expectBoolean(value);
                                }
                            } catch (PException e) {
                                e.expectStopIteration(inliningTarget, errorProfile);
                            }
                            return new BoolSequenceStorage(elements, i);
                        }
                        case Byte: {
                            byte[] elements = new byte[size];
                            array = elements;
                            try {
                                for (; loopProfile.profile(inliningTarget, (value = nextNode.execute(frame, inliningTarget, iterator, false)) != STOP_MARKER); i++) {
                                    byte bvalue;
                                    try {
                                        bvalue = PInt.byteValueExact(PGuards.expectInteger(value));
                                        if (growArrayProfile.profile(inliningTarget, i >= elements.length)) {
                                            array = elements = PythonUtils.arrayCopyOf(elements, elements.length * 2);
                                        }
                                        elements[i] = bvalue;
                                    } catch (OverflowException e) {
                                        throw new UnexpectedResultException(value);
                                    }
                                }
                            } catch (PException e) {
                                e.expectStopIteration(inliningTarget, errorProfile);
                            }
                            return new ByteSequenceStorage(elements, i);
                        }
                        case Int: {
                            int[] elements = new int[size];
                            array = elements;
                            try {
                                for (; loopProfile.profile(inliningTarget, (value = nextNode.execute(frame, inliningTarget, iterator, false)) != STOP_MARKER); i++) {
                                    if (growArrayProfile.profile(inliningTarget, i >= elements.length)) {
                                        array = elements = PythonUtils.arrayCopyOf(elements, elements.length * 2);
                                    }
                                    elements[i] = PGuards.expectInteger(value);
                                }
                            } catch (PException e) {
                                e.expectStopIteration(inliningTarget, errorProfile);
                            }
                            return new IntSequenceStorage(elements, i);
                        }
                        case Long: {
                            long[] elements = new long[size];
                            array = elements;
                            try {
                                for (; loopProfile.profile(inliningTarget, (value = nextNode.execute(frame, inliningTarget, iterator, false)) != STOP_MARKER); i++) {
                                    if (growArrayProfile.profile(inliningTarget, i >= elements.length)) {
                                        array = elements = PythonUtils.arrayCopyOf(elements, elements.length * 2);
                                    }
                                    elements[i] = PGuards.expectLong(value);
                                }
                            } catch (PException e) {
                                e.expectStopIteration(inliningTarget, errorProfile);
                            }
                            return new LongSequenceStorage(elements, i);
                        }
                        case Double: {
                            double[] elements = new double[size];
                            array = elements;
                            try {
                                for (; loopProfile.profile(inliningTarget, (value = nextNode.execute(frame, inliningTarget, iterator, false)) != STOP_MARKER); i++) {
                                    if (growArrayProfile.profile(inliningTarget, i >= elements.length)) {
                                        array = elements = PythonUtils.arrayCopyOf(elements, elements.length * 2);
                                    }
                                    elements[i] = PGuards.expectDouble(value);
                                }
                            } catch (PException e) {
                                e.expectStopIteration(inliningTarget, errorProfile);
                            }
                            return new DoubleSequenceStorage(elements, i);
                        }
                        case Generic: {
                            Object[] elements = new Object[size];
                            try {
                                for (; loopProfile.profile(inliningTarget, (value = nextNode.execute(frame, inliningTarget, iterator, false)) != STOP_MARKER); i++) {
                                    if (growArrayProfile.profile(inliningTarget, i >= elements.length)) {
                                        elements = PythonUtils.arrayCopyOf(elements, elements.length * 2);
                                    }
                                    elements[i] = value;
                                }
                            } catch (PException e) {
                                e.expectStopIteration(inliningTarget, errorProfile);
                            }
                            return new ObjectSequenceStorage(elements, i);
                        }
                        default:
                            CompilerDirectives.transferToInterpreterAndInvalidate();
                            throw new RuntimeException("unexpected state");
                    }
                } catch (UnexpectedResultException e) {
                    return genericFallback(frame, iterator, array, i, e.getResult(), nextNode, inliningTarget, errorProfile);
                }
            }
        }

        private static SequenceStorage genericFallback(VirtualFrame frame, PBuiltinIterator iterator, Object array, int count, Object result, NextHelperNode nextNode, Node inliningTarget,
                        IsBuiltinObjectProfile errorProfile) {
            Object[] elements = new Object[Array.getLength(array) * 2];
            int i = 0;
            for (; i < count; i++) {
                elements[i] = Array.get(array, i);
            }
            elements[i++] = result;
            Object value;
            try {
                while ((value = nextNode.execute(frame, inliningTarget, iterator, false)) != STOP_MARKER) {
                    if (i >= elements.length) {
                        elements = PythonUtils.arrayCopyOf(elements, elements.length * 2);
                    }
                    elements[i++] = value;
                }
            } catch (PException e) {
                e.expectStopIteration(inliningTarget, errorProfile);
            }
            return new ObjectSequenceStorage(elements, i);
        }

        @SuppressWarnings("truffle-static-method")
        public abstract static class CreateStorageFromIteratorNodeCached extends CreateStorageFromIteratorNode {

            @CompilationFinal private SequenceStorage.StorageType expectedElementType = Uninitialized;

            private static final int MAX_PREALLOCATE_SIZE = 32;
            @CompilationFinal int startSizeProfiled = START_SIZE;

            public boolean isBuiltinIterator(GetClassNode getClass, Node inliningTarget, Object iterator) {
                return iterator instanceof PBuiltinIterator && getClass.execute(inliningTarget, (PBuiltinIterator) iterator) == PythonBuiltinClassType.PIterator;
            }

            public static SequenceStorage getSequenceStorage(Node inliningTarget, GetInternalIteratorSequenceStorage node, PBuiltinIterator iterator) {
                return iterator.index != 0 || iterator.isExhausted() ? null : node.execute(inliningTarget, iterator);
            }

            @Specialization(guards = {"isBuiltinIterator(getClassNode, inliningTarget, it)", "storage != null"}, limit = "3")
            public static SequenceStorage createBuiltinFastPath(PBuiltinIterator it, @SuppressWarnings("unused") int len,
                            @Bind("this") Node inliningTarget,
                            @SuppressWarnings("unused") @Exclusive @Cached GetClassNode getClassNode,
                            @SuppressWarnings("unused") @Cached GetInternalIteratorSequenceStorage getIterSeqStorageNode,
                            @Bind("getSequenceStorage(inliningTarget, getIterSeqStorageNode, it)") SequenceStorage storage,
                            @Cached CopyNode copyNode) {
                it.setExhausted();
                return copyNode.execute(inliningTarget, storage);
            }

            @Specialization(replaces = "createBuiltinFastPath", guards = {"isBuiltinIterator(getClassNode, inliningTarget, iterator)", "len < 0"}, limit = "3")
            public SequenceStorage createBuiltinUnknownLen(VirtualFrame frame, PBuiltinIterator iterator, @SuppressWarnings("unused") int len,
                            @Bind("this") Node inliningTarget,
                            @SuppressWarnings("unused") @Exclusive @Cached GetClassNode getClassNode,
                            @Cached BuiltinIteratorLengthHint lengthHint,
                            @Exclusive @Cached InlinedLoopConditionProfile loopProfile,
                            @Exclusive @Cached IsBuiltinObjectProfile errorProfile,
                            @Exclusive @Cached InlinedCountingConditionProfile arrayGrowProfile,
                            @Exclusive @Cached GetElementType getElementType,
                            @Exclusive @Cached NextHelperNode nextNode) {
                int expectedLen = lengthHint.execute(inliningTarget, iterator);
                if (expectedLen < 0) {
                    expectedLen = startSizeProfiled;
                }
                SequenceStorage s = createStorageFromBuiltin(frame, iterator, expectedLen, expectedElementType, nextNode, errorProfile, inliningTarget, arrayGrowProfile, loopProfile);
                return profileResult(getElementType, inliningTarget, s, true);
            }

            @Specialization(replaces = "createBuiltinFastPath", guards = {"isBuiltinIterator(getClassNode, inliningTarget, iterator)", "len >= 0"}, limit = "3")
            public SequenceStorage createBuiltinKnownLen(VirtualFrame frame, PBuiltinIterator iterator, int len,
                            @Bind("this") Node inliningTarget,
                            @SuppressWarnings("unused") @Exclusive @Cached GetClassNode getClassNode,
                            @Exclusive @Cached InlinedLoopConditionProfile loopProfile,
                            @Exclusive @Cached IsBuiltinObjectProfile errorProfile,
                            @Exclusive @Cached InlinedCountingConditionProfile arrayGrowProfile,
                            @Exclusive @Cached GetElementType getElementType,
                            @Exclusive @Cached NextHelperNode nextNode) {
                SequenceStorage s = createStorageFromBuiltin(frame, iterator, len, expectedElementType, nextNode, errorProfile, inliningTarget, arrayGrowProfile, loopProfile);
                return profileResult(getElementType, inliningTarget, s, false);
            }

            @Specialization(guards = {"!isBuiltinIterator(getClassNode, inliningTarget, iterator)", "len < 0"})
            public SequenceStorage createGenericUnknownLen(VirtualFrame frame, Object iterator, @SuppressWarnings("unused") int len,
                            @Bind("this") Node inliningTarget,
                            @SuppressWarnings("unused") @Shared @Cached GetClassNode getClassNode,
                            @Shared("errProfile") @Cached IsBuiltinObjectProfile errorProfile,
                            @Shared("arrayGrowProfile") @Cached InlinedCountingConditionProfile arrayGrowProfile,
                            @Shared @Cached GetElementType getElementType,
                            @Shared @Cached GetNextNode getNextNode) {
                SequenceStorage s = createStorage(frame, iterator, startSizeProfiled, expectedElementType, getNextNode, errorProfile, inliningTarget, arrayGrowProfile);
                return profileResult(getElementType, inliningTarget, s, true);
            }

            @Specialization(guards = {"!isBuiltinIterator(getClassNode, inliningTarget, iterator)", "len >= 0"})
            public SequenceStorage createGenericKnownLen(VirtualFrame frame, Object iterator, int len,
                            @Bind("this") Node inliningTarget,
                            @SuppressWarnings("unused") @Shared @Cached GetClassNode getClassNode,
                            @Shared("errProfile") @Cached IsBuiltinObjectProfile errorProfile,
                            @Shared("arrayGrowProfile") @Cached InlinedCountingConditionProfile arrayGrowProfile,
                            @Shared @Cached GetElementType getElementType,
                            @Shared @Cached GetNextNode getNextNode) {
                SequenceStorage s = createStorage(frame, iterator, len, expectedElementType, getNextNode, errorProfile, inliningTarget, arrayGrowProfile);
                return profileResult(getElementType, inliningTarget, s, false);
            }

            private SequenceStorage profileResult(GetElementType getElementType, Node inliningTarget, SequenceStorage storage, boolean profileLength) {
                if (CompilerDirectives.inInterpreter() && profileLength) {
                    int actualLen = storage.length();
                    if (startSizeProfiled < actualLen && actualLen <= MAX_PREALLOCATE_SIZE) {
                        startSizeProfiled = actualLen;
                    }
                }
                StorageType actualElementType = getElementType.execute(inliningTarget, storage);
                if (expectedElementType != actualElementType) {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    expectedElementType = actualElementType;
                }
                return storage;
            }
        }

        private static final class CreateStorageFromIteratorUncachedNode extends CreateStorageFromIteratorNode {
            public static final CreateStorageFromIteratorUncachedNode INSTANCE = new CreateStorageFromIteratorUncachedNode();

            @Override
            public SequenceStorage execute(VirtualFrame frame, Object iterator, int len) {
                return executeImpl(iterator, len);
            }

            @TruffleBoundary
            private static SequenceStorage executeImpl(Object iterator, int len) {
                if (iterator instanceof PBuiltinIterator pbi) {
                    if (GetPythonObjectClassNode.executeUncached(pbi) == PythonBuiltinClassType.PIterator && pbi.index == 0 && !pbi.isExhausted()) {
                        SequenceStorage s = GetInternalIteratorSequenceStorage.executeUncached(pbi);
                        if (s != null) {
                            return SequenceStorageNodes.CopyNode.executeUncached(s);
                        }
                    }
                }
                return create().createStorageUninitialized(null, null, iterator, GetNextNode.getUncached(), IsBuiltinObjectProfile.getUncached(), len >= 0 ? len : START_SIZE);
            }
        }

        @NeverDefault
        public static CreateStorageFromIteratorNode create() {
            return CreateStorageFromIteratorNodeCachedNodeGen.create();
        }

        public static CreateStorageFromIteratorNode getUncached() {
            return CreateStorageFromIteratorUncachedNode.INSTANCE;
        }
    }
}
