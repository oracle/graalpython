/*
 * Copyright (c) 2020, 2025, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.builtins.objects.memoryview;

import static com.oracle.graal.python.builtins.PythonBuiltinClassType.NotImplementedError;
import static com.oracle.graal.python.builtins.PythonBuiltinClassType.TypeError;
import static com.oracle.graal.python.builtins.PythonBuiltinClassType.ValueError;
import static com.oracle.graal.python.builtins.objects.PythonAbstractObject.systemHashCodeAsHexString;
import static com.oracle.graal.python.nodes.BuiltinNames.J_MEMORYVIEW;
import static com.oracle.graal.python.nodes.ErrorMessages.DIM_MEMORY_HAS_NO_LENGTH;
import static com.oracle.graal.python.nodes.ErrorMessages.INVALID_INDEXING_OF_0_DIM_MEMORY;
import static com.oracle.graal.python.nodes.ErrorMessages.MULTI_DIMENSIONAL_SUB_VIEWS_NOT_IMPLEMENTED;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___ENTER__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___EXIT__;
import static com.oracle.graal.python.nodes.StringLiterals.T_EMPTY_STRING;
import static com.oracle.graal.python.util.BufferFormat.T_UINT_8_TYPE_CODE;
import static com.oracle.graal.python.util.PythonUtils.TS_ENCODING;
import static com.oracle.graal.python.util.PythonUtils.tsLiteral;

import java.util.Arrays;
import java.util.List;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.annotations.ArgumentClinic;
import com.oracle.graal.python.annotations.Builtin;
import com.oracle.graal.python.annotations.Slot;
import com.oracle.graal.python.annotations.Slot.SlotKind;
import com.oracle.graal.python.annotations.Slot.SlotSignature;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.PNotImplemented;
import com.oracle.graal.python.builtins.objects.bytes.BytesCommonBuiltins.ExpectIntNode;
import com.oracle.graal.python.builtins.objects.bytes.BytesCommonBuiltins.SepExpectByteNode;
import com.oracle.graal.python.builtins.objects.bytes.BytesNodes;
import com.oracle.graal.python.builtins.objects.bytes.PBytes;
import com.oracle.graal.python.builtins.objects.cext.capi.CExtNodes;
import com.oracle.graal.python.builtins.objects.cext.capi.NativeCAPISymbol;
import com.oracle.graal.python.builtins.objects.common.SequenceNodes;
import com.oracle.graal.python.builtins.objects.common.SequenceStorageNodes;
import com.oracle.graal.python.builtins.objects.ellipsis.PEllipsis;
import com.oracle.graal.python.builtins.objects.list.PList;
import com.oracle.graal.python.builtins.objects.memoryview.MemoryViewBuiltinsFactory.MemoryViewNodeFactory;
import com.oracle.graal.python.builtins.objects.memoryview.MemoryViewNodes.ReadItemAtNode;
import com.oracle.graal.python.builtins.objects.slice.PSlice;
import com.oracle.graal.python.builtins.objects.slice.SliceNodes;
import com.oracle.graal.python.builtins.objects.str.StringUtils.SimpleTruffleStringFormatNode;
import com.oracle.graal.python.builtins.objects.type.TpSlots;
import com.oracle.graal.python.builtins.objects.type.slots.TpSlotBinaryFunc.MpSubscriptBuiltinNode;
import com.oracle.graal.python.builtins.objects.type.slots.TpSlotHashFun.HashBuiltinNode;
import com.oracle.graal.python.builtins.objects.type.slots.TpSlotLen.LenBuiltinNode;
import com.oracle.graal.python.builtins.objects.type.slots.TpSlotMpAssSubscript.MpAssSubscriptBuiltinNode;
import com.oracle.graal.python.builtins.objects.type.slots.TpSlotRichCompare;
import com.oracle.graal.python.builtins.objects.type.slots.TpSlotSizeArgFun.SqItemBuiltinNode;
import com.oracle.graal.python.lib.PyMemoryViewFromObject;
import com.oracle.graal.python.lib.PyNumberAsSizeNode;
import com.oracle.graal.python.lib.PyObjectRichCompareBool;
import com.oracle.graal.python.lib.RichCmpOp;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PNodeWithContext;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonBinaryClinicBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonQuaternaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonTernaryClinicBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonUnaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.clinic.ArgumentClinicProvider;
import com.oracle.graal.python.runtime.AsyncHandler;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.graal.python.runtime.object.PFactory;
import com.oracle.graal.python.runtime.sequence.storage.IntSequenceStorage;
import com.oracle.graal.python.runtime.sequence.storage.SequenceStorage;
import com.oracle.graal.python.util.BufferFormat;
import com.oracle.graal.python.util.PythonUtils;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.HostCompilerDirectives.InliningCutoff;
import com.oracle.truffle.api.ThreadLocalAction.Access;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Exclusive;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.GenerateCached;
import com.oracle.truffle.api.dsl.GenerateInline;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.NeverDefault;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.profiles.InlinedConditionProfile;
import com.oracle.truffle.api.strings.TruffleString;

@CoreFunctions(extendClasses = PythonBuiltinClassType.PMemoryView)
public final class MemoryViewBuiltins extends PythonBuiltins {
    public static final TpSlots SLOTS = MemoryViewBuiltinsSlotsGen.SLOTS;

    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return MemoryViewBuiltinsFactory.getFactories();
    }

    static class NativeBufferReleaseCallback implements AsyncHandler.AsyncAction {
        private final BufferReference reference;

        public NativeBufferReleaseCallback(BufferReference reference) {
            this.reference = reference;
        }

        @Override
        public void execute(PythonContext context, Access access) {
            if (reference.isReleased()) {
                return;
            }
            MemoryViewNodes.ReleaseBufferNode.executeUncached(reference.getLifecycleManager());
        }
    }

    @Slot(value = SlotKind.tp_new, isComplex = true)
    @SlotSignature(name = J_MEMORYVIEW, minNumOfPositionalArgs = 2, parameterNames = {"$cls", "object"})
    @GenerateNodeFactory
    public abstract static class MemoryViewNode extends PythonBuiltinNode {

        public abstract PMemoryView execute(VirtualFrame frame, Object cls, Object object);

        public final PMemoryView execute(VirtualFrame frame, Object object) {
            return execute(frame, PythonBuiltinClassType.PMemoryView, object);
        }

        @Specialization
        PMemoryView fromObject(VirtualFrame frame, @SuppressWarnings("unused") Object cls, Object object,
                        @Cached PyMemoryViewFromObject memoryViewFromObject) {
            return memoryViewFromObject.execute(frame, object);
        }

        @NeverDefault
        public static MemoryViewNode create() {
            return MemoryViewNodeFactory.create(null);
        }
    }

    @Slot(value = SlotKind.sq_item, isComplex = true)
    @GenerateNodeFactory
    public abstract static class MemoryViewSqItemNode extends SqItemBuiltinNode {
        @Specialization
        static Object doInt(VirtualFrame frame, PMemoryView self, int index,
                        @Bind Node inliningTarget,
                        @Cached PRaiseNode raiseNode,
                        @Cached MemoryViewNodes.PointerLookupNode pointerFromIndexNode,
                        @Cached MemoryViewNodes.ReadItemAtNode readItemAtNode) {
            self.checkReleased(inliningTarget, raiseNode);
            MemoryViewNodes.MemoryPointer ptr = pointerFromIndexNode.execute(frame, self, index);
            return readItemAtNode.execute(frame, self, ptr.ptr, ptr.offset);
        }
    }

    @Slot(value = SlotKind.mp_subscript, isComplex = true)
    @GenerateNodeFactory
    abstract static class GetItemNode extends MpSubscriptBuiltinNode {

        @Specialization(guards = {"!isPSlice(index)", "!isEllipsis(index)"})
        static Object getitem(VirtualFrame frame, PMemoryView self, Object index,
                        @Bind Node inliningTarget,
                        @Cached MemoryViewNodes.PointerLookupNode pointerFromIndexNode,
                        @Cached MemoryViewNodes.ReadItemAtNode readItemAtNode,
                        @Exclusive @Cached PRaiseNode raiseNode) {
            self.checkReleased(inliningTarget, raiseNode);
            MemoryViewNodes.MemoryPointer ptr = pointerFromIndexNode.execute(frame, self, index);
            return readItemAtNode.execute(frame, self, ptr.ptr, ptr.offset);
        }

        @Specialization
        static Object getitemSlice(PMemoryView self, PSlice slice,
                        @Bind Node inliningTarget,
                        @Bind PythonLanguage language,
                        @Exclusive @Cached InlinedConditionProfile zeroDimProfile,
                        @Cached SliceNodes.SliceUnpack sliceUnpack,
                        @Cached SliceNodes.AdjustIndices adjustIndices,
                        @Cached MemoryViewNodes.InitFlagsNode initFlagsNode,
                        @Exclusive @Cached PRaiseNode raiseNode) {
            self.checkReleased(inliningTarget, raiseNode);
            if (zeroDimProfile.profile(inliningTarget, self.getDimensions() == 0)) {
                throw raiseNode.raise(inliningTarget, TypeError, INVALID_INDEXING_OF_0_DIM_MEMORY);
            }
            int[] shape = self.getBufferShape();
            PSlice.SliceInfo sliceInfo = adjustIndices.execute(inliningTarget, shape[0], sliceUnpack.execute(inliningTarget, slice));
            int[] strides = self.getBufferStrides();
            int[] newStrides = new int[strides.length];
            newStrides[0] = strides[0] * sliceInfo.step;
            PythonUtils.arraycopy(strides, 1, newStrides, 1, strides.length - 1);
            int[] newShape = new int[shape.length];
            newShape[0] = sliceInfo.sliceLength;
            PythonUtils.arraycopy(shape, 1, newShape, 1, shape.length - 1);
            int[] suboffsets = self.getBufferSuboffsets();
            int length = self.getLength() - (shape[0] - newShape[0]) * self.getItemSize();
            int flags = initFlagsNode.execute(inliningTarget, self.getDimensions(), self.getItemSize(), newShape, newStrides, suboffsets);
            return PFactory.createMemoryView(language, PythonContext.get(inliningTarget), self.getLifecycleManager(), self.getBuffer(), self.getOwner(), length, self.isReadOnly(),
                            self.getItemSize(), self.getFormat(), self.getFormatString(), self.getDimensions(), self.getBufferPointer(),
                            self.getOffset() + sliceInfo.start * strides[0], newShape, newStrides, suboffsets, flags);
        }

        @Specialization
        static Object getitemEllipsis(PMemoryView self, @SuppressWarnings("unused") PEllipsis ellipsis,
                        @Bind Node inliningTarget,
                        @Exclusive @Cached InlinedConditionProfile zeroDimProfile,
                        @Exclusive @Cached PRaiseNode raiseNode) {
            self.checkReleased(inliningTarget, raiseNode);
            if (zeroDimProfile.profile(inliningTarget, self.getDimensions() == 0)) {
                return self;
            }
            throw raiseNode.raise(inliningTarget, TypeError, ErrorMessages.MEMORYVIEW_INVALID_SLICE_KEY);
        }
    }

    @Slot(value = SlotKind.mp_ass_subscript, isComplex = true)
    @GenerateNodeFactory
    abstract static class SetItemNode extends MpAssSubscriptBuiltinNode {
        @Specialization(guards = {"!isNoValue(object)", "!isPSlice(index)", "!isEllipsis(index)"})
        static void setitem(VirtualFrame frame, PMemoryView self, Object index, Object object,
                        @Bind Node inliningTarget,
                        @Shared @Cached MemoryViewNodes.PointerLookupNode pointerLookupNode,
                        @Shared @Cached MemoryViewNodes.WriteItemAtNode writeItemAtNode,
                        @Exclusive @Cached PRaiseNode raiseNode) {
            self.checkReleased(inliningTarget, raiseNode);
            checkReadonly(inliningTarget, self, raiseNode);

            MemoryViewNodes.MemoryPointer ptr = pointerLookupNode.execute(frame, self, index);
            writeItemAtNode.execute(frame, self, ptr.ptr, ptr.offset, object);
        }

        @Specialization(guards = "!isNoValue(object)")
        static void setitem(VirtualFrame frame, PMemoryView self, PSlice slice, Object object,
                        @Bind Node inliningTarget,
                        @Cached GetItemNode getItemNode,
                        @Cached PyMemoryViewFromObject createMemoryView,
                        @Cached MemoryViewNodes.ReleaseNode releaseNode,
                        @Shared @Cached MemoryViewNodes.PointerLookupNode pointerLookupNode,
                        @Cached MemoryViewNodes.ToJavaBytesNode toJavaBytesNode,
                        @Cached MemoryViewNodes.WriteBytesAtNode writeBytesAtNode,
                        @Exclusive @Cached PRaiseNode raiseNode) {
            self.checkReleased(inliningTarget, raiseNode);
            checkReadonly(inliningTarget, self, raiseNode);
            if (self.getDimensions() != 1) {
                throw raiseNode.raise(inliningTarget, NotImplementedError, ErrorMessages.MEMORYVIEW_SLICE_ASSIGNMENT_RESTRICTED_TO_DIM_1);
            }
            PMemoryView srcView = createMemoryView.execute(frame, object);
            try {
                PMemoryView destView = (PMemoryView) getItemNode.execute(frame, self, slice);
                try {
                    if (srcView.getDimensions() != destView.getDimensions() || srcView.getBufferShape()[0] != destView.getBufferShape()[0] || srcView.getFormat() != destView.getFormat()) {
                        throw raiseNode.raise(inliningTarget, ValueError, ErrorMessages.MEMORYVIEW_DIFFERENT_STRUCTURES);
                    }
                    // The intermediate array is necessary for overlapping views (where src and dest
                    // are the same buffer)
                    byte[] srcBytes = toJavaBytesNode.execute(srcView);
                    int itemsize = srcView.getItemSize();
                    for (int i = 0; i < destView.getBufferShape()[0]; i++) {
                        MemoryViewNodes.MemoryPointer destPtr = pointerLookupNode.execute(frame, destView, i);
                        writeBytesAtNode.execute(inliningTarget, srcBytes, i * itemsize, itemsize, self, destPtr.ptr, destPtr.offset);
                    }
                } finally {
                    releaseNode.execute(frame, destView);
                }
            } finally {
                releaseNode.execute(frame, srcView);
            }
        }

        @Specialization(guards = "!isNoValue(object)")
        static void setitem(VirtualFrame frame, PMemoryView self, @SuppressWarnings("unused") PEllipsis ellipsis, Object object,
                        @Bind Node inliningTarget,
                        @Cached InlinedConditionProfile zeroDimProfile,
                        @Shared @Cached MemoryViewNodes.WriteItemAtNode writeItemAtNode,
                        @Exclusive @Cached PRaiseNode raiseNode) {
            self.checkReleased(inliningTarget, raiseNode);
            checkReadonly(inliningTarget, self, raiseNode);

            if (zeroDimProfile.profile(inliningTarget, self.getDimensions() == 0)) {
                writeItemAtNode.execute(frame, self, self.getBufferPointer(), 0, object);
            } else {
                throw raiseNode.raise(inliningTarget, TypeError, INVALID_INDEXING_OF_0_DIM_MEMORY);
            }
        }

        @Specialization(guards = "isNoValue(value)")
        static void error(@SuppressWarnings("unused") PMemoryView self, @SuppressWarnings("unused") Object key, @SuppressWarnings("unused") Object value,
                        @Bind Node inliningTarget) {
            throw PRaiseNode.raiseStatic(inliningTarget, TypeError, ErrorMessages.CANNOT_DELETE_MEMORY);
        }

        private static void checkReadonly(Node inliningTarget, PMemoryView self, PRaiseNode raiseNode) {
            if (self.isReadOnly()) {
                throw raiseNode.raise(inliningTarget, TypeError, ErrorMessages.CANNOT_MODIFY_READONLY_MEMORY);
            }
        }

    }

    @Slot(value = SlotKind.tp_richcompare, isComplex = true)
    @GenerateNodeFactory
    public abstract static class MemoryViewRichCmp extends TpSlotRichCompare.RichCmpBuiltinNode {
        @Specialization(guards = "op.isEqOrNe()")
        static Object doIt(VirtualFrame frame, Object a, Object b, RichCmpOp op,
                        @Bind Node inliningTarget,
                        @Cached MemoryViewEqNode eqNode) {
            Object result = eqNode.execute(frame, inliningTarget, a, b);
            if (result == PNotImplemented.NOT_IMPLEMENTED) {
                return result;
            }
            return (boolean) result == op.isEq();
        }

        @Specialization(guards = "!op.isEqOrNe()")
        @SuppressWarnings("unused")
        static Object doOthers(Object a, Object b, RichCmpOp op) {
            return PNotImplemented.NOT_IMPLEMENTED;
        }
    }

    @GenerateInline
    @GenerateCached(false)
    public abstract static class MemoryViewEqNode extends PNodeWithContext {
        abstract Object execute(VirtualFrame frame, Node inliningTarget, Object self, Object other);

        private static boolean eq(VirtualFrame frame, Node inliningTarget, PMemoryView self, PMemoryView other,
                        PyObjectRichCompareBool eqNode,
                        MemoryViewNodes.ReadItemAtNode readSelf,
                        MemoryViewNodes.ReadItemAtNode readOther,
                        CExtNodes.PCallCapiFunction callCapiFunction) {
            if (self.isReleased() || other.isReleased()) {
                return self == other;
            }

            int ndim = self.getDimensions();
            if (ndim != other.getDimensions()) {
                return false;
            }

            for (int i = 0; i < ndim; i++) {
                if (self.getBufferShape()[i] != other.getBufferShape()[i]) {
                    return false;
                }
                if (self.getBufferShape()[i] == 0) {
                    break;
                }
            }

            // TODO CPython supports only limited set of typed for reading and writing, but
            // for equality comparisons, it supports all the struct module formats. Implement that

            if (ndim == 0) {
                Object selfItem = readSelf.execute(frame, self, self.getBufferPointer(), 0);
                Object otherItem = readOther.execute(frame, other, other.getBufferPointer(), 0);
                return eqNode.executeEq(frame, inliningTarget, selfItem, otherItem);
            }

            return recursive(frame, inliningTarget, eqNode, callCapiFunction, self, other, readSelf, readOther, 0, ndim,
                            self.getBufferPointer(), self.getOffset(), other.getBufferPointer(), other.getOffset());
        }

        @Specialization
        static Object eq(VirtualFrame frame, Node inliningTarget, PMemoryView self, Object other,
                        @Cached InlinedConditionProfile otherIsMemoryViewProfile,
                        @Cached PyMemoryViewFromObject memoryViewNode,
                        @Cached MemoryViewNodes.ReleaseNode releaseNode,
                        @Cached PyObjectRichCompareBool eqNode,
                        @Cached MemoryViewNodes.ReadItemAtNode readSelf,
                        @Cached MemoryViewNodes.ReadItemAtNode readOther,
                        @Cached CExtNodes.PCallCapiFunction callCapiFunction) {
            PMemoryView memoryView;
            boolean otherIsMemoryView = otherIsMemoryViewProfile.profile(inliningTarget, other instanceof PMemoryView);
            if (otherIsMemoryView) {
                memoryView = (PMemoryView) other;
            } else {
                try {
                    memoryView = memoryViewNode.execute(frame, other);
                } catch (PException e) {
                    return PNotImplemented.NOT_IMPLEMENTED;
                }
            }
            try {
                return eq(frame, inliningTarget, self, memoryView, eqNode, readSelf, readOther, callCapiFunction);
            } finally {
                if (!otherIsMemoryView) {
                    releaseNode.execute(frame, memoryView);
                }
            }
        }

        @Fallback
        @SuppressWarnings("unused")
        static Object eq(Object self, Object other) {
            return PNotImplemented.NOT_IMPLEMENTED;
        }

        // TODO: recursion in PE
        @InliningCutoff
        private static boolean recursive(VirtualFrame frame, Node inliningTarget, PyObjectRichCompareBool eqNode, CExtNodes.PCallCapiFunction callCapiFunction,
                        PMemoryView self, PMemoryView other,
                        ReadItemAtNode readSelf, ReadItemAtNode readOther,
                        int dim, int ndim, Object selfPtr, int initialSelfOffset, Object otherPtr, int initialOtherOffset) {
            int selfOffset = initialSelfOffset;
            int otherOffset = initialOtherOffset;
            for (int i = 0; i < self.getBufferShape()[dim]; i++) {
                Object selfXPtr = selfPtr;
                int selfXOffset = selfOffset;
                Object otherXPtr = otherPtr;
                int otherXOffset = otherOffset;
                if (self.getBufferSuboffsets() != null && self.getBufferSuboffsets()[dim] >= 0) {
                    selfXPtr = callCapiFunction.call(NativeCAPISymbol.FUN_ADD_SUBOFFSET, selfPtr, selfOffset, self.getBufferSuboffsets()[dim]);
                    selfXOffset = 0;
                }
                if (other.getBufferSuboffsets() != null && other.getBufferSuboffsets()[dim] >= 0) {
                    otherXPtr = callCapiFunction.call(NativeCAPISymbol.FUN_ADD_SUBOFFSET, otherPtr, otherOffset, other.getBufferSuboffsets()[dim]);
                    otherXOffset = 0;
                }
                if (dim == ndim - 1) {
                    Object selfItem = readSelf.execute(frame, self, selfXPtr, selfXOffset);
                    Object otherItem = readOther.execute(frame, other, otherXPtr, otherXOffset);
                    if (!eqNode.executeEq(frame, inliningTarget, selfItem, otherItem)) {
                        return false;
                    }
                } else {
                    if (!recursive(frame, inliningTarget, eqNode, callCapiFunction, self, other, readSelf, readOther, dim + 1, ndim, selfXPtr, selfXOffset, otherXPtr, otherXOffset)) {
                        return false;
                    }
                }
                selfOffset += self.getBufferStrides()[dim];
                otherOffset += other.getBufferStrides()[dim];
            }
            return true;
        }
    }

    @Builtin(name = "tolist", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class ToListNode extends PythonUnaryBuiltinNode {
        @Child private CExtNodes.PCallCapiFunction callCapiFunction;

        @Specialization(guards = {"self.getDimensions() == cachedDimensions", "cachedDimensions < 8"}, limit = "3")
        Object tolistCached(VirtualFrame frame, PMemoryView self,
                        @Bind Node inliningTarget,
                        @Bind PythonLanguage language,
                        @Cached("self.getDimensions()") int cachedDimensions,
                        @Shared @Cached MemoryViewNodes.ReadItemAtNode readItemAtNode,
                        @Shared @Cached PRaiseNode raiseNode) {
            self.checkReleased(inliningTarget, raiseNode);
            if (cachedDimensions == 0) {
                // That's not a list but CPython does it this way
                return readItemAtNode.execute(frame, self, self.getBufferPointer(), self.getOffset());
            } else {
                return recursive(frame, self, readItemAtNode, 0, cachedDimensions, self.getBufferPointer(), self.getOffset(), language);
            }
        }

        @Specialization(replaces = "tolistCached")
        Object tolist(VirtualFrame frame, PMemoryView self,
                        @Bind Node inliningTarget,
                        @Bind PythonLanguage language,
                        @Shared @Cached MemoryViewNodes.ReadItemAtNode readItemAtNode,
                        @Shared @Cached PRaiseNode raiseNode) {
            self.checkReleased(inliningTarget, raiseNode);
            if (self.getDimensions() == 0) {
                return readItemAtNode.execute(frame, self, self.getBufferPointer(), self.getOffset());
            } else {
                return recursiveBoundary(frame, self, readItemAtNode, 0, self.getDimensions(), self.getBufferPointer(), self.getOffset(), language);
            }
        }

        private PList recursiveBoundary(VirtualFrame frame, PMemoryView self, MemoryViewNodes.ReadItemAtNode readItemAtNode, int dim, int ndim, Object ptr, int offset, PythonLanguage language) {
            return recursive(frame, self, readItemAtNode, dim, ndim, ptr, offset, language);
        }

        private PList recursive(VirtualFrame frame, PMemoryView self, MemoryViewNodes.ReadItemAtNode readItemAtNode, int dim, int ndim, Object ptr, int initialOffset, PythonLanguage language) {
            int offset = initialOffset;
            Object[] objects = new Object[self.getBufferShape()[dim]];
            for (int i = 0; i < self.getBufferShape()[dim]; i++) {
                Object xptr = ptr;
                int xoffset = offset;
                if (self.getBufferSuboffsets() != null && self.getBufferSuboffsets()[dim] >= 0) {
                    xptr = getCallCapiFunction().call(NativeCAPISymbol.FUN_ADD_SUBOFFSET, ptr, offset, self.getBufferSuboffsets()[dim]);
                    xoffset = 0;
                }
                if (dim == ndim - 1) {
                    objects[i] = readItemAtNode.execute(frame, self, xptr, xoffset);
                } else {
                    objects[i] = recursive(frame, self, readItemAtNode, dim + 1, ndim, xptr, xoffset, language);
                }
                offset += self.getBufferStrides()[dim];
            }
            return PFactory.createList(language, objects);
        }

        private CExtNodes.PCallCapiFunction getCallCapiFunction() {
            if (callCapiFunction == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                callCapiFunction = insert(CExtNodes.PCallCapiFunction.create());
            }
            return callCapiFunction;
        }
    }

    @Builtin(name = "tobytes", minNumOfPositionalArgs = 1, parameterNames = {"$self", "order"})
    @ArgumentClinic(name = "order", conversion = ArgumentClinic.ClinicConversion.TString, defaultValue = "T_C", useDefaultForNone = true)
    @GenerateNodeFactory
    public abstract static class ToBytesNode extends PythonBinaryClinicBuiltinNode {

        static final TruffleString T_A = tsLiteral("A");
        static final TruffleString T_C = tsLiteral("C");
        static final TruffleString T_F = tsLiteral("F");

        @Child private MemoryViewNodes.ToJavaBytesNode toJavaBytesNode;
        @Child private MemoryViewNodes.ToJavaBytesFortranOrderNode toJavaBytesFortranOrderNode;

        @Specialization
        PBytes tobytes(PMemoryView self, TruffleString order,
                        @Bind Node inliningTarget,
                        @Bind PythonLanguage language,
                        @Cached TruffleString.EqualNode equalNode,
                        @Cached PRaiseNode raiseNode) {
            self.checkReleased(inliningTarget, raiseNode);
            byte[] bytes;
            // The nodes act as branch profiles
            if (equalNode.execute(order, T_C, TS_ENCODING) || equalNode.execute(order, T_A, TS_ENCODING)) {
                bytes = getToJavaBytesNode().execute(self);
            } else if (equalNode.execute(order, T_F, TS_ENCODING)) {
                bytes = getToJavaBytesFortranOrderNode().execute(self);
            } else {
                throw raiseNode.raise(inliningTarget, ValueError, ErrorMessages.ORDER_MUST_BE_C_F_OR_A);
            }
            return PFactory.createBytes(language, bytes);
        }

        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return MemoryViewBuiltinsClinicProviders.ToBytesNodeClinicProviderGen.INSTANCE;
        }

        private MemoryViewNodes.ToJavaBytesNode getToJavaBytesNode() {
            if (toJavaBytesNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                toJavaBytesNode = insert(MemoryViewNodes.ToJavaBytesNode.create());
            }
            return toJavaBytesNode;
        }

        private MemoryViewNodes.ToJavaBytesFortranOrderNode getToJavaBytesFortranOrderNode() {
            if (toJavaBytesFortranOrderNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                toJavaBytesFortranOrderNode = insert(MemoryViewNodes.ToJavaBytesFortranOrderNode.create());
            }
            return toJavaBytesFortranOrderNode;
        }
    }

    @Builtin(name = "hex", minNumOfPositionalArgs = 1, parameterNames = {"$self", "sep", "bytes_per_sep"})
    @ArgumentClinic(name = "sep", conversionClass = SepExpectByteNode.class, defaultValue = "PNone.NO_VALUE")
    @ArgumentClinic(name = "bytes_per_sep", conversionClass = ExpectIntNode.class, defaultValue = "1")
    @GenerateNodeFactory
    abstract static class HexNode extends PythonTernaryClinicBuiltinNode {

        @Specialization
        TruffleString none(PMemoryView self, @SuppressWarnings("unused") PNone sep, @SuppressWarnings("unused") int bytesPerSepGroup,
                        @Bind Node inliningTarget,
                        @Shared("p") @Cached InlinedConditionProfile earlyExit,
                        @Shared("b") @Cached MemoryViewNodes.ToJavaBytesNode toJavaBytesNode,
                        @Shared("h") @Cached BytesNodes.ByteToHexNode toHexNode,
                        @Shared @Cached PRaiseNode raiseNode) {
            return hex(self, (byte) 0, 0, inliningTarget, earlyExit, toJavaBytesNode, toHexNode, raiseNode);
        }

        @Specialization
        TruffleString hex(PMemoryView self, byte sep, int bytesPerSepGroup,
                        @Bind Node inliningTarget,
                        @Shared("p") @Cached InlinedConditionProfile earlyExit,
                        @Shared("b") @Cached MemoryViewNodes.ToJavaBytesNode toJavaBytesNode,
                        @Shared("h") @Cached BytesNodes.ByteToHexNode toHexNode,
                        @Shared @Cached PRaiseNode raiseNode) {
            self.checkReleased(inliningTarget, raiseNode);
            if (earlyExit.profile(inliningTarget, self.getLength() == 0)) {
                return T_EMPTY_STRING;
            }
            byte[] b = toJavaBytesNode.execute(self);
            return toHexNode.execute(inliningTarget, b, b.length, sep, bytesPerSepGroup);
        }

        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return MemoryViewBuiltinsClinicProviders.HexNodeClinicProviderGen.INSTANCE;
        }
    }

    @Builtin(name = "toreadonly", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class ToReadonlyNode extends PythonUnaryBuiltinNode {

        public final Object call(VirtualFrame frame, Object arg) {
            return execute(frame, arg);
        }

        @Specialization
        static PMemoryView toreadonly(PMemoryView self,
                        @Bind PythonLanguage language,
                        @Bind Node inliningTarget,
                        @Cached PRaiseNode raiseNode) {
            self.checkReleased(inliningTarget, raiseNode);
            return PFactory.createMemoryView(language, PythonContext.get(inliningTarget), self.getLifecycleManager(), self.getBuffer(), self.getOwner(), self.getLength(), true,
                            self.getItemSize(), self.getFormat(), self.getFormatString(), self.getDimensions(), self.getBufferPointer(),
                            self.getOffset(), self.getBufferShape(), self.getBufferStrides(), self.getBufferSuboffsets(), self.getFlags());
        }
    }

    @Builtin(name = "cast", minNumOfPositionalArgs = 2, parameterNames = {"$self", "format", "shape"})
    @ArgumentClinic(name = "format", conversion = ArgumentClinic.ClinicConversion.TString)
    @GenerateNodeFactory
    public abstract static class CastNode extends PythonTernaryClinicBuiltinNode {

        @Specialization
        static PMemoryView cast(PMemoryView self, TruffleString formatString, @SuppressWarnings("unused") PNone none,
                        @Bind Node inliningTarget,
                        @Shared @Cached TruffleString.CodePointLengthNode lengthNode,
                        @Shared @Cached TruffleString.CodePointAtIndexUTF32Node atIndexNode,
                        @Exclusive @Cached PRaiseNode raiseNode) {
            self.checkReleased(inliningTarget, raiseNode);
            return doCast(inliningTarget, self, formatString, 1, null, PythonContext.get(inliningTarget), lengthNode, atIndexNode, raiseNode);
        }

        @Specialization(guards = "isPTuple(shapeObj) || isList(shapeObj)")
        static PMemoryView cast(VirtualFrame frame, PMemoryView self, TruffleString formatString, Object shapeObj,
                        @Bind Node inliningTarget,
                        @Cached SequenceNodes.GetSequenceStorageNode getSequenceStorageNode,
                        @Cached SequenceStorageNodes.GetItemScalarNode getItemScalarNode,
                        @Cached PyNumberAsSizeNode asSizeNode,
                        @Shared @Cached TruffleString.CodePointLengthNode lengthNode,
                        @Shared @Cached TruffleString.CodePointAtIndexUTF32Node atIndexNode,
                        @Exclusive @Cached PRaiseNode raiseNode) {
            self.checkReleased(inliningTarget, raiseNode);
            SequenceStorage storage = getSequenceStorageNode.execute(inliningTarget, shapeObj);
            int ndim = storage.length();
            int[] shape = new int[ndim];
            for (int i = 0; i < ndim; i++) {
                shape[i] = asSizeNode.executeExact(frame, inliningTarget, getItemScalarNode.execute(inliningTarget, storage, i));
                if (shape[i] <= 0) {
                    throw raiseNode.raise(inliningTarget, TypeError, ErrorMessages.MEMORYVIEW_CAST_ELEMENTS_MUST_BE_POSITIVE_INTEGERS);
                }
            }
            return doCast(inliningTarget, self, formatString, ndim, shape, PythonContext.get(inliningTarget), lengthNode, atIndexNode, raiseNode);
        }

        @Specialization(guards = {"!isPTuple(shape)", "!isList(shape)", "!isPNone(shape)"})
        @SuppressWarnings("unused")
        static PMemoryView error(PMemoryView self, TruffleString format, Object shape,
                        @Bind Node inliningTarget) {
            throw PRaiseNode.raiseStatic(inliningTarget, TypeError, ErrorMessages.ARG_S_MUST_BE_A_LIST_OR_TUPLE, "shape");
        }

        private static PMemoryView doCast(Node inliningTarget, PMemoryView self, TruffleString formatString, int ndim, int[] shape, PythonContext context, TruffleString.CodePointLengthNode lengthNode,
                        TruffleString.CodePointAtIndexUTF32Node atIndexNode, PRaiseNode raiseNode) {
            if (!self.isCContiguous()) {
                throw raiseNode.raise(inliningTarget, TypeError, ErrorMessages.MEMORYVIEW_CASTS_RESTRICTED_TO_C_CONTIGUOUS);
            }
            BufferFormat format = BufferFormat.forMemoryView(formatString, lengthNode, atIndexNode);
            int itemsize = format.bytesize;
            if (itemsize < 0) {
                throw raiseNode.raise(inliningTarget, ValueError, ErrorMessages.MEMORYVIEW_DESTINATION_FORMAT_ERROR);
            }
            if (!MemoryViewNodes.isByteFormat(format) && !MemoryViewNodes.isByteFormat(self.getFormat())) {
                throw raiseNode.raise(inliningTarget, TypeError, ErrorMessages.MEMORYVIEW_CANNOT_CAST_NON_BYTE);
            }
            if (self.getLength() % itemsize != 0) {
                throw raiseNode.raise(inliningTarget, TypeError, ErrorMessages.MEMORYVIEW_LENGTH_NOT_MULTIPLE_OF_ITEMSIZE);
            }
            if (shape != null || self.getDimensions() != 1) {
                for (int i = 0; i < self.getDimensions(); i++) {
                    if (self.getBufferShape()[i] == 0) {
                        throw raiseNode.raise(inliningTarget, TypeError, ErrorMessages.MEMORYVIEW_CANNOT_CAST_VIEW_WITH_ZEROS_IN_SHAPE_OR_STRIDES);
                    }
                }
            }
            int[] newShape;
            int[] newStrides;
            int flags = PMemoryView.FLAG_C;
            if (ndim == 0) {
                newShape = null;
                newStrides = null;
                flags |= PMemoryView.FLAG_SCALAR;
                if (self.getLength() != itemsize) {
                    throw raiseNode.raise(inliningTarget, TypeError, ErrorMessages.MEMORYVIEW_CAST_WRONG_LENGTH);
                }
            } else {
                if (shape == null) {
                    newShape = new int[]{self.getLength() / itemsize};
                } else {
                    if (ndim != 1 && self.getDimensions() != 1) {
                        throw raiseNode.raise(inliningTarget, TypeError, ErrorMessages.MEMORYVIEW_CAST_MUST_BE_1D_TO_ND_OR_ND_TO_1D);
                    }
                    if (ndim > PMemoryView.MAX_DIM) {
                        throw raiseNode.raise(inliningTarget, ValueError, ErrorMessages.MEMORYVIEW_NUMBER_OF_DIMENSIONS_MUST_NOT_EXCEED_D, ndim);
                    }
                    int newLength = itemsize;
                    for (int i = 0; i < ndim; i++) {
                        newLength *= shape[i];
                    }
                    if (newLength != self.getLength()) {
                        throw raiseNode.raise(inliningTarget, TypeError, ErrorMessages.MEMORYVIEW_CAST_WRONG_LENGTH);
                    }
                    newShape = shape;
                }
                newStrides = PMemoryView.initStridesFromShape(ndim, itemsize, shape);
            }
            return PFactory.createMemoryView(PythonLanguage.get(inliningTarget), context, self.getLifecycleManager(), self.getBuffer(), self.getOwner(), self.getLength(), self.isReadOnly(),
                            itemsize, format, formatString, ndim, self.getBufferPointer(),
                            self.getOffset(), newShape, newStrides, null, flags);
        }

        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return MemoryViewBuiltinsClinicProviders.CastNodeClinicProviderGen.INSTANCE;
        }
    }

    @Slot(SlotKind.sq_length)
    @Slot(SlotKind.mp_length)
    @GenerateUncached
    @GenerateNodeFactory
    public abstract static class LenNode extends LenBuiltinNode {
        @Specialization
        static int len(PMemoryView self,
                        @Bind Node inliningTarget,
                        @Cached InlinedConditionProfile zeroDimProfile,
                        @Cached PRaiseNode raiseNode) {
            self.checkReleased(inliningTarget, raiseNode);
            if (zeroDimProfile.profile(inliningTarget, self.getDimensions() == 0)) {
                throw raiseNode.raise(inliningTarget, TypeError, DIM_MEMORY_HAS_NO_LENGTH);
            } else {
                return self.getBufferShape()[0];
            }
        }
    }

    @Slot(value = SlotKind.tp_repr, isComplex = true)
    @GenerateNodeFactory
    abstract static class ReprNode extends PythonUnaryBuiltinNode {
        @Specialization
        static TruffleString repr(PMemoryView self,
                        @Cached SimpleTruffleStringFormatNode simpleTruffleStringFormatNode) {
            if (self.isReleased()) {
                return simpleTruffleStringFormatNode.format("<released memory at 0x%s>", systemHashCodeAsHexString(self));
            } else {
                return simpleTruffleStringFormatNode.format("<memory at 0x%s>", systemHashCodeAsHexString(self));
            }
        }
    }

    @Slot(value = SlotKind.tp_hash, isComplex = true)
    @GenerateNodeFactory
    public abstract static class HashNode extends HashBuiltinNode {
        @Specialization
        static long hash(PMemoryView self,
                        @Bind Node inliningTarget,
                        @Cached InlinedConditionProfile cachedProfile,
                        @Cached InlinedConditionProfile writableProfile,
                        @Cached MemoryViewNodes.ToJavaBytesNode toJavaBytesNode,
                        @Cached PRaiseNode raiseNode) {
            if (cachedProfile.profile(inliningTarget, self.getCachedHash() != -1)) {
                return self.getCachedHash();
            }
            self.checkReleased(inliningTarget, raiseNode);
            if (writableProfile.profile(inliningTarget, !self.isReadOnly())) {
                throw raiseNode.raise(inliningTarget, ValueError, ErrorMessages.CANNOT_HASH_WRITEABLE_MEMORYVIEW);
            } else {
                // TODO avoid copying
                int hash = hashArray(toJavaBytesNode.execute(self));
                self.setCachedHash(hash);
                return hash;
            }
        }

        @TruffleBoundary
        private static int hashArray(byte[] array) {
            return Arrays.hashCode(array);
        }
    }

    @Slot(value = SlotKind.tp_iter, isComplex = true)
    @GenerateNodeFactory
    public abstract static class IterNode extends PythonUnaryBuiltinNode {
        @Specialization
        static Object iter(PMemoryView self,
                        @Bind Node inliningTarget,
                        @Bind PythonLanguage language,
                        @Cached InlinedConditionProfile zeroDimProfile,
                        @Cached PRaiseNode raiseNode) {
            self.checkReleased(inliningTarget, raiseNode);
            int ndims = self.getDimensions();
            if (ndims == 0) {
                throw raiseNode.raise(inliningTarget, TypeError, INVALID_INDEXING_OF_0_DIM_MEMORY);
            }
            if (ndims != 1) {
                throw raiseNode.raise(inliningTarget, NotImplementedError, MULTI_DIMENSIONAL_SUB_VIEWS_NOT_IMPLEMENTED);
            }

            int length = LenNode.len(self, inliningTarget, zeroDimProfile, raiseNode);
            return PFactory.createMemoryViewIterator(language, self, 0, length, self.getFormat());
        }
    }

    @Builtin(name = J___ENTER__, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class EnterNode extends PythonUnaryBuiltinNode {
        @Specialization
        static Object enter(PMemoryView self,
                        @Bind Node inliningTarget,
                        @Cached PRaiseNode raiseNode) {
            self.checkReleased(inliningTarget, raiseNode);
            return self;
        }
    }

    @Builtin(name = J___EXIT__, minNumOfPositionalArgs = 4)
    @GenerateNodeFactory
    public abstract static class ExitNode extends PythonQuaternaryBuiltinNode {
        @Specialization
        @SuppressWarnings("unused")
        static Object exit(VirtualFrame frame, PMemoryView self, Object type, Object val, Object tb,
                        @Cached MemoryViewNodes.ReleaseNode releaseNode) {
            releaseNode.execute(frame, self);
            return PNone.NONE;
        }
    }

    @Builtin(name = "release", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class ReleaseNode extends PythonUnaryBuiltinNode {
        @Specialization
        Object release(VirtualFrame frame, PMemoryView self,
                        @Cached MemoryViewNodes.ReleaseNode releaseNode) {
            releaseNode.execute(frame, self);
            return PNone.NONE;
        }
    }

    @Builtin(name = "itemsize", minNumOfPositionalArgs = 1, isGetter = true)
    @GenerateNodeFactory
    public abstract static class ItemSizeNode extends PythonUnaryBuiltinNode {

        @Specialization
        static int get(PMemoryView self,
                        @Bind Node inliningTarget,
                        @Cached PRaiseNode raiseNode) {
            self.checkReleased(inliningTarget, raiseNode);
            return self.getItemSize();
        }
    }

    @Builtin(name = "nbytes", minNumOfPositionalArgs = 1, isGetter = true)
    @GenerateNodeFactory
    public abstract static class NBytesNode extends PythonUnaryBuiltinNode {

        @Specialization
        static int get(PMemoryView self,
                        @Bind Node inliningTarget,
                        @Cached PRaiseNode raiseNode) {
            self.checkReleased(inliningTarget, raiseNode);
            return self.getLength();
        }
    }

    @Builtin(name = "obj", minNumOfPositionalArgs = 1, isGetter = true)
    @GenerateNodeFactory
    public abstract static class ObjNode extends PythonUnaryBuiltinNode {

        @Specialization
        static Object get(PMemoryView self,
                        @Bind Node inliningTarget,
                        @Cached PRaiseNode raiseNode) {
            self.checkReleased(inliningTarget, raiseNode);
            return self.getOwner() != null ? self.getOwner() : PNone.NONE;
        }
    }

    @Builtin(name = "format", minNumOfPositionalArgs = 1, isGetter = true)
    @GenerateNodeFactory
    public abstract static class FormatNode extends PythonUnaryBuiltinNode {

        @Specialization
        static Object get(PMemoryView self,
                        @Bind Node inliningTarget,
                        @Cached PRaiseNode raiseNode) {
            self.checkReleased(inliningTarget, raiseNode);
            return self.getFormatString() != null ? self.getFormatString() : T_UINT_8_TYPE_CODE;
        }
    }

    @Builtin(name = "shape", minNumOfPositionalArgs = 1, isGetter = true)
    @GenerateNodeFactory
    public abstract static class ShapeNode extends PythonUnaryBuiltinNode {

        @Specialization
        static Object get(PMemoryView self,
                        @Bind Node inliningTarget,
                        @Bind PythonLanguage language,
                        @Cached InlinedConditionProfile nullProfile,
                        @Cached PRaiseNode raiseNode) {
            self.checkReleased(inliningTarget, raiseNode);
            if (nullProfile.profile(inliningTarget, self.getBufferShape() == null)) {
                return PFactory.createEmptyTuple(language);
            }
            return PFactory.createTuple(language, new IntSequenceStorage(self.getBufferShape()));
        }
    }

    @Builtin(name = "strides", minNumOfPositionalArgs = 1, isGetter = true)
    @GenerateNodeFactory
    public abstract static class StridesNode extends PythonUnaryBuiltinNode {

        @Specialization
        static Object get(PMemoryView self,
                        @Bind Node inliningTarget,
                        @Bind PythonLanguage language,
                        @Cached InlinedConditionProfile nullProfile,
                        @Cached PRaiseNode raiseNode) {
            self.checkReleased(inliningTarget, raiseNode);
            if (nullProfile.profile(inliningTarget, self.getBufferStrides() == null)) {
                return PFactory.createEmptyTuple(language);
            }
            return PFactory.createTuple(language, new IntSequenceStorage(self.getBufferStrides()));
        }
    }

    @Builtin(name = "suboffsets", minNumOfPositionalArgs = 1, isGetter = true)
    @GenerateNodeFactory
    public abstract static class SuboffsetsNode extends PythonUnaryBuiltinNode {

        @Specialization
        static Object get(PMemoryView self,
                        @Bind Node inliningTarget,
                        @Bind PythonLanguage language,
                        @Cached InlinedConditionProfile nullProfile,
                        @Cached PRaiseNode raiseNode) {
            self.checkReleased(inliningTarget, raiseNode);
            if (nullProfile.profile(inliningTarget, self.getBufferSuboffsets() == null)) {
                return PFactory.createEmptyTuple(language);
            }
            return PFactory.createTuple(language, new IntSequenceStorage(self.getBufferSuboffsets()));
        }
    }

    @Builtin(name = "readonly", minNumOfPositionalArgs = 1, isGetter = true)
    @GenerateNodeFactory
    public abstract static class ReadonlyNode extends PythonUnaryBuiltinNode {

        @Specialization
        static boolean get(PMemoryView self,
                        @Bind Node inliningTarget,
                        @Cached PRaiseNode raiseNode) {
            self.checkReleased(inliningTarget, raiseNode);
            return self.isReadOnly();
        }
    }

    @Builtin(name = "ndim", minNumOfPositionalArgs = 1, isGetter = true)
    @GenerateNodeFactory
    public abstract static class NDimNode extends PythonUnaryBuiltinNode {

        @Specialization
        static int get(PMemoryView self,
                        @Bind Node inliningTarget,
                        @Cached PRaiseNode raiseNode) {
            self.checkReleased(inliningTarget, raiseNode);
            return self.getDimensions();
        }
    }

    @Builtin(name = "c_contiguous", minNumOfPositionalArgs = 1, isGetter = true)
    @GenerateNodeFactory
    public abstract static class CContiguousNode extends PythonUnaryBuiltinNode {

        @Specialization
        static boolean get(PMemoryView self,
                        @Bind Node inliningTarget,
                        @Cached PRaiseNode raiseNode) {
            self.checkReleased(inliningTarget, raiseNode);
            return self.isCContiguous();
        }
    }

    @Builtin(name = "f_contiguous", minNumOfPositionalArgs = 1, isGetter = true)
    @GenerateNodeFactory
    public abstract static class FContiguousNode extends PythonUnaryBuiltinNode {

        @Specialization
        static boolean get(PMemoryView self,
                        @Bind Node inliningTarget,
                        @Cached PRaiseNode raiseNode) {
            self.checkReleased(inliningTarget, raiseNode);
            return self.isFortranContiguous();
        }
    }

    @Builtin(name = "contiguous", minNumOfPositionalArgs = 1, isGetter = true)
    @GenerateNodeFactory
    public abstract static class ContiguousNode extends PythonUnaryBuiltinNode {

        @Specialization
        static boolean get(PMemoryView self,
                        @Bind Node inliningTarget,
                        @Cached PRaiseNode raiseNode) {
            self.checkReleased(inliningTarget, raiseNode);
            return self.isAnyContiguous();
        }
    }
}
