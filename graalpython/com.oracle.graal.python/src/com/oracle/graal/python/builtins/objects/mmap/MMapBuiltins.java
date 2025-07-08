/*
 * Copyright (c) 2019, 2025, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.builtins.objects.mmap;

import static com.oracle.graal.python.builtins.PythonBuiltinClassType.OverflowError;
import static com.oracle.graal.python.builtins.objects.common.IndexNodes.checkBounds;
import static com.oracle.graal.python.builtins.objects.mmap.PMMap.ACCESS_COPY;
import static com.oracle.graal.python.builtins.objects.mmap.PMMap.ACCESS_READ;
import static com.oracle.graal.python.nodes.BuiltinNames.J_READLINE;
import static com.oracle.graal.python.nodes.ErrorMessages.MMAP_ASSIGNMENT_MUST_BE_LENGTH_1_BYTES;
import static com.oracle.graal.python.nodes.ErrorMessages.MMAP_CANNOT_MODIFY_READONLY_MEMORY;
import static com.oracle.graal.python.nodes.ErrorMessages.MMAP_CHANGED_LENGTH;
import static com.oracle.graal.python.nodes.ErrorMessages.MMAP_CLOSED_OR_INVALID;
import static com.oracle.graal.python.nodes.ErrorMessages.MMAP_INDEX_OUT_OF_RANGE;
import static com.oracle.graal.python.nodes.ErrorMessages.MMAP_INDICES_MUST_BE_INTEGER;
import static com.oracle.graal.python.nodes.ErrorMessages.MMAP_ITEM_VALUE_MUST_BE_AN_INT;
import static com.oracle.graal.python.nodes.ErrorMessages.MMAP_ITEM_VALUE_MUST_BE_IN_RANGE;
import static com.oracle.graal.python.nodes.ErrorMessages.MMAP_OBJECT_DOESNT_SUPPORT_ITEM_DELETION;
import static com.oracle.graal.python.nodes.ErrorMessages.MMAP_OBJECT_DOESNT_SUPPORT_SLICE_DELETION;
import static com.oracle.graal.python.nodes.ErrorMessages.MMAP_SLICE_ASSIGNMENT_IS_WRONG_SIZE;
import static com.oracle.graal.python.nodes.ErrorMessages.READ_BYTE_OUT_OF_RANGE;
import static com.oracle.graal.python.nodes.PGuards.isPNone;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___ENTER__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___EXIT__;
import static com.oracle.graal.python.runtime.PosixConstants.MAP_ANONYMOUS;
import static com.oracle.graal.python.runtime.PosixConstants.MAP_PRIVATE;
import static com.oracle.graal.python.runtime.PosixConstants.MAP_SHARED;
import static com.oracle.graal.python.runtime.PosixConstants.PROT_READ;
import static com.oracle.graal.python.runtime.PosixConstants.PROT_WRITE;
import static com.oracle.graal.python.runtime.PosixSupportLibrary.ST_SIZE;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.TypeError;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.ValueError;
import static com.oracle.graal.python.util.PythonUtils.tsLiteral;

import java.util.List;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.annotations.ArgumentClinic;
import com.oracle.graal.python.annotations.ArgumentClinic.ClinicConversion;
import com.oracle.graal.python.annotations.Slot;
import com.oracle.graal.python.annotations.Slot.SlotKind;
import com.oracle.graal.python.annotations.Slot.SlotSignature;
import com.oracle.graal.python.builtins.Builtin;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.modules.SysModuleBuiltins;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.buffer.PythonBufferAccessLibrary;
import com.oracle.graal.python.builtins.objects.buffer.PythonBufferAcquireLibrary;
import com.oracle.graal.python.builtins.objects.bytes.BytesNodes;
import com.oracle.graal.python.builtins.objects.bytes.PBytes;
import com.oracle.graal.python.builtins.objects.common.SequenceStorageNodes;
import com.oracle.graal.python.builtins.objects.ints.PInt;
import com.oracle.graal.python.builtins.objects.mmap.MMapBuiltinsClinicProviders.FindNodeClinicProviderGen;
import com.oracle.graal.python.builtins.objects.mmap.MMapBuiltinsClinicProviders.FlushNodeClinicProviderGen;
import com.oracle.graal.python.builtins.objects.mmap.MMapBuiltinsClinicProviders.MMapNodeClinicProviderGen;
import com.oracle.graal.python.builtins.objects.mmap.MMapBuiltinsClinicProviders.SeekNodeClinicProviderGen;
import com.oracle.graal.python.builtins.objects.mmap.MMapBuiltinsClinicProviders.WriteNodeClinicProviderGen;
import com.oracle.graal.python.builtins.objects.range.RangeNodes.LenOfRangeNode;
import com.oracle.graal.python.builtins.objects.slice.PSlice;
import com.oracle.graal.python.builtins.objects.slice.PSlice.SliceInfo;
import com.oracle.graal.python.builtins.objects.slice.SliceNodes;
import com.oracle.graal.python.builtins.objects.slice.SliceNodes.CoerceToIntSlice;
import com.oracle.graal.python.builtins.objects.slice.SliceNodes.ComputeIndices;
import com.oracle.graal.python.builtins.objects.type.TpSlots;
import com.oracle.graal.python.builtins.objects.type.TypeNodes;
import com.oracle.graal.python.builtins.objects.type.slots.TpSlotBinaryFunc.MpSubscriptBuiltinNode;
import com.oracle.graal.python.builtins.objects.type.slots.TpSlotLen.LenBuiltinNode;
import com.oracle.graal.python.builtins.objects.type.slots.TpSlotMpAssSubscript.MpAssSubscriptBuiltinNode;
import com.oracle.graal.python.builtins.objects.type.slots.TpSlotSizeArgFun.SqItemBuiltinNode;
import com.oracle.graal.python.builtins.objects.type.slots.TpSlotSqAssItem.SqAssItemBuiltinNode;
import com.oracle.graal.python.lib.PyBytesCheckNode;
import com.oracle.graal.python.lib.PyIndexCheckNode;
import com.oracle.graal.python.lib.PyLongAsLongNode;
import com.oracle.graal.python.lib.PyNumberAsSizeNode;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PConstructAndRaiseNode;
import com.oracle.graal.python.nodes.PGuards;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.call.special.LookupAndCallUnaryNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonBinaryClinicBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonClinicBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonQuaternaryClinicBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonTernaryClinicBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonUnaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.clinic.ArgumentClinicProvider;
import com.oracle.graal.python.nodes.function.builtins.clinic.LongIndexConverterNode;
import com.oracle.graal.python.runtime.AsyncHandler;
import com.oracle.graal.python.runtime.IndirectCallData;
import com.oracle.graal.python.runtime.PosixSupport;
import com.oracle.graal.python.runtime.PosixSupportLibrary;
import com.oracle.graal.python.runtime.PosixSupportLibrary.PosixException;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.graal.python.runtime.object.PFactory;
import com.oracle.graal.python.runtime.sequence.storage.ByteSequenceStorage;
import com.oracle.graal.python.util.OverflowException;
import com.oracle.graal.python.util.PythonUtils;
import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Exclusive;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.profiles.InlinedBranchProfile;
import com.oracle.truffle.api.profiles.InlinedConditionProfile;
import com.oracle.truffle.api.strings.TruffleString;

@CoreFunctions(extendClasses = PythonBuiltinClassType.PMMap)
public final class MMapBuiltins extends PythonBuiltins {
    public static final TpSlots SLOTS = MMapBuiltinsSlotsGen.SLOTS;

    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return MMapBuiltinsFactory.getFactories();
    }

    private static byte[] readBytes(VirtualFrame frame, Node inliningTarget, PMMap self, PosixSupportLibrary posixLib, Object posixSupport, long pos, int len,
                    PConstructAndRaiseNode.Lazy constructAndRaiseNode) {
        try {
            assert len > 0;
            assert pos + len <= self.getLength();
            byte[] buffer = new byte[len];
            posixLib.mmapReadBytes(posixSupport, self.getPosixSupportHandle(), pos, buffer, buffer.length);
            return buffer;
        } catch (PosixException e) {
            throw constructAndRaiseNode.get(inliningTarget).raiseOSErrorFromPosixException(frame, e);
        }
    }

    @Slot(value = SlotKind.tp_new, isComplex = true)
    @SlotSignature(name = "mmap", minNumOfPositionalArgs = 3, parameterNames = {"cls", "fd", "length", "flags", "prot", "access",
                    "offset"})
    @GenerateNodeFactory
    // Note: it really should not call fileno on fd as per Python spec
    @ArgumentClinic(name = "fd", conversion = ClinicConversion.Int)
    @ArgumentClinic(name = "length", conversion = ClinicConversion.LongIndex)
    @ArgumentClinic(name = "flags", conversion = ClinicConversion.Int, defaultValue = "FLAGS_DEFAULT")
    @ArgumentClinic(name = "prot", conversion = ClinicConversion.Int, defaultValue = "PROT_DEFAULT")
    @ArgumentClinic(name = "access", conversion = ClinicConversion.Int, defaultValue = "ACCESS_ARG_DEFAULT")
    @ArgumentClinic(name = "offset", conversion = ClinicConversion.Long, defaultValue = "0")
    public abstract static class MMapNode extends PythonClinicBuiltinNode {
        protected static final int ACCESS_ARG_DEFAULT = PMMap.ACCESS_DEFAULT;
        protected static final int FLAGS_DEFAULT = MAP_SHARED.value;
        protected static final int PROT_DEFAULT = PROT_WRITE.value | PROT_READ.value;

        private static final int ANONYMOUS_FD = -1;

        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return MMapNodeClinicProviderGen.INSTANCE;
        }

        // mmap(fileno, length, tagname=None, access=ACCESS_DEFAULT[, offset=0])
        @Specialization(guards = "!isIllegal(fd)")
        static PMMap doFile(VirtualFrame frame, Object clazz, int fd, long lengthIn, int flagsIn, int protIn, @SuppressWarnings("unused") int accessIn, long offset,
                        @Bind Node inliningTarget,
                        @Cached SysModuleBuiltins.AuditNode auditNode,
                        @CachedLibrary("getPosixSupport()") PosixSupportLibrary posixSupport,
                        @Cached PConstructAndRaiseNode.Lazy constructAndRaiseNode,
                        @Cached TypeNodes.GetInstanceShape getInstanceShape,
                        @Cached PRaiseNode raiseNode) {
            if (lengthIn < 0) {
                throw raiseNode.raise(inliningTarget, OverflowError, ErrorMessages.MEM_MAPPED_LENGTH_MUST_BE_POSITIVE);
            }
            if (offset < 0) {
                throw raiseNode.raise(inliningTarget, OverflowError, ErrorMessages.MEM_MAPPED_OFFSET_MUST_BE_POSITIVE);
            }
            int flags = flagsIn;
            int prot = protIn;
            int access = accessIn;
            switch (access) {
                case ACCESS_READ:
                    flags = MAP_SHARED.value;
                    prot = PROT_READ.value;
                    break;
                case PMMap.ACCESS_WRITE:
                    flags = MAP_SHARED.value;
                    prot = PROT_READ.value | PROT_WRITE.value;
                    break;
                case ACCESS_COPY:
                    flags = MAP_PRIVATE.value;
                    prot = PROT_READ.value | PROT_WRITE.value;
                    break;
                case PMMap.ACCESS_DEFAULT:
                    // map prot to access type
                    if (((prot & PROT_READ.value) != 0) && ((prot & PROT_WRITE.value) != 0)) {
                        // ACCESS_DEFAULT
                    } else if ((prot & PROT_WRITE.value) != 0) {
                        access = PMMap.ACCESS_WRITE;
                    } else {
                        access = ACCESS_READ;
                    }
                    break;
                default:
                    throw raiseNode.raise(inliningTarget, PythonBuiltinClassType.ValueError, ErrorMessages.MEM_MAPPED_OFFSET_INVALID_ACCESS);
            }

            auditNode.audit(inliningTarget, "mmap.__new__", fd, lengthIn, access, offset);

            // For file mappings we use fstat to validate the length or to initialize the length if
            // it is 0 meaning that we should find it out for the user
            long length = lengthIn;
            PosixSupport posixSupport1 = PosixSupport.get(inliningTarget);
            if (fd != ANONYMOUS_FD) {
                long[] fstatResult = null;
                try {
                    fstatResult = posixSupport.fstat(posixSupport1, fd);
                } catch (PosixException ignored) {
                }
                if (fstatResult != null && length == 0) {
                    if (fstatResult[ST_SIZE] == 0) {
                        throw raiseNode.raise(inliningTarget, PythonBuiltinClassType.ValueError, ErrorMessages.CANNOT_MMAP_AN_EMPTY_FILE);
                    }
                    if (offset >= fstatResult[ST_SIZE]) {
                        throw raiseNode.raise(inliningTarget, PythonBuiltinClassType.ValueError, ErrorMessages.MMAP_S_IS_GREATER_THAN_FILE_SIZE, "offset");
                    }
                    // Unlike in CPython, this always fits in the long range
                    length = fstatResult[ST_SIZE] - offset;
                } else if (fstatResult != null && (offset > fstatResult[ST_SIZE] || fstatResult[ST_SIZE] - offset < length)) {
                    throw raiseNode.raise(inliningTarget, PythonBuiltinClassType.ValueError, ErrorMessages.MMAP_S_IS_GREATER_THAN_FILE_SIZE, "length");
                }
            }

            // Fixup the flags if we want to use anonymous map
            int dupFd;
            if (fd == ANONYMOUS_FD) {
                dupFd = ANONYMOUS_FD;
                flags |= MAP_ANONYMOUS.value;
                // TODO: CPython uses mapping to "/dev/zero" on systems that do not support
                // MAP_ANONYMOUS, maybe this can be detected and handled by the POSIX layer
            } else {
                try {
                    dupFd = posixSupport.dup(posixSupport1, fd);
                } catch (PosixException e) {
                    throw constructAndRaiseNode.get(inliningTarget).raiseOSErrorFromPosixException(frame, e);
                }
            }

            Object mmapHandle;
            try {
                mmapHandle = posixSupport.mmap(posixSupport1, length, prot, flags, dupFd, offset);
            } catch (PosixException e) {
                throw constructAndRaiseNode.get(inliningTarget).raiseOSErrorFromPosixException(frame, e);
            }
            PythonContext context = PythonContext.get(inliningTarget);
            return PFactory.createMMap(context.getLanguage(inliningTarget), context, clazz, getInstanceShape.execute(clazz), mmapHandle, dupFd, length, access);
        }

        @Specialization(guards = "isIllegal(fd)")
        @SuppressWarnings("unused")
        static PMMap doIllegal(Object clazz, int fd, long lengthIn, int flagsIn, int protIn, int accessIn, long offset,
                        @Bind Node inliningTarget) {
            throw PRaiseNode.raiseStatic(inliningTarget, PythonBuiltinClassType.OSError);
        }

        protected static boolean isIllegal(int fd) {
            return fd < -1;
        }
    }

    @Slot(value = SlotKind.sq_item, isComplex = true)
    @GenerateNodeFactory
    public abstract static class MMapSqItemNode extends SqItemBuiltinNode {
        @Specialization
        static PBytes doInt(VirtualFrame frame, PMMap self, int index,
                        @Bind Node inliningTarget,
                        @Bind PythonContext context,
                        @Cached PRaiseNode raiseNode,
                        @Cached PConstructAndRaiseNode.Lazy constructAndRaiseNode,
                        @CachedLibrary("context.getPosixSupport()") PosixSupportLibrary posixSupportLib) {
            long len = self.getLength();
            checkBounds(inliningTarget, raiseNode, MMAP_INDEX_OUT_OF_RANGE, index, len);
            try {
                byte b = posixSupportLib.mmapReadByte(context.getPosixSupport(), self.getPosixSupportHandle(), index);
                // CPython indeed returns bytes object from sq_item, although it returns single byte
                // value as integer from mp_subscript, see, e.g.: `for i in mmap_object: print(i)`
                return PFactory.createBytes(context.getLanguage(inliningTarget), new byte[]{b});
            } catch (PosixException e) {
                throw constructAndRaiseNode.get(inliningTarget).raiseOSErrorFromPosixException(frame, e);
            }
        }
    }

    @Slot(value = SlotKind.mp_subscript, isComplex = true)
    @GenerateNodeFactory
    public abstract static class GetItemNode extends MpSubscriptBuiltinNode {

        @Specialization(guards = "!isPSlice(idxObj)")
        static int doSingle(VirtualFrame frame, PMMap self, Object idxObj,
                        @Bind Node inliningTarget,
                        @Bind PythonContext context,
                        @Exclusive @Cached InlinedConditionProfile negativeIndexProfile,
                        @CachedLibrary("context.getPosixSupport()") PosixSupportLibrary posixSupportLib,
                        @Cached PyLongAsLongNode asLongNode,
                        @Exclusive @Cached PConstructAndRaiseNode.Lazy constructAndRaiseNode,
                        @Exclusive @Cached PRaiseNode raiseNode) {
            long i = asLongNode.execute(frame, inliningTarget, idxObj);
            long len = self.getLength();
            long idx = negativeIndexProfile.profile(inliningTarget, i < 0) ? i + len : i;
            checkBounds(inliningTarget, raiseNode, MMAP_INDEX_OUT_OF_RANGE, idx, len);
            try {
                return posixSupportLib.mmapReadByte(context.getPosixSupport(), self.getPosixSupportHandle(), idx) & 0xFF;
            } catch (PosixException e) {
                throw constructAndRaiseNode.get(inliningTarget).raiseOSErrorFromPosixException(frame, e);
            }
        }

        @Specialization
        static Object doSlice(VirtualFrame frame, PMMap self, PSlice idx,
                        @Bind Node inliningTarget,
                        @Bind PythonContext context,
                        @CachedLibrary("context.getPosixSupport()") PosixSupportLibrary posixSupportLib,
                        @Exclusive @Cached InlinedConditionProfile emptyProfile,
                        @Cached CoerceToIntSlice sliceCast,
                        @Cached ComputeIndices compute,
                        @Cached LenOfRangeNode sliceLenNode,
                        @Exclusive @Cached PConstructAndRaiseNode.Lazy constructAndRaiseNode,
                        @Exclusive @Cached PRaiseNode raiseNode) {
            try {
                SliceInfo info = compute.execute(frame, sliceCast.execute(inliningTarget, idx), PInt.intValueExact(self.getLength()));
                int len = sliceLenNode.len(inliningTarget, info);
                if (emptyProfile.profile(inliningTarget, len == 0)) {
                    return PFactory.createEmptyBytes(context.getLanguage(inliningTarget));
                }
                byte[] result = readBytes(frame, inliningTarget, self, posixSupportLib, context.getPosixSupport(), info.start, len, constructAndRaiseNode);
                return PFactory.createBytes(context.getLanguage(inliningTarget), result);
            } catch (OverflowException e) {
                throw raiseNode.raise(inliningTarget, PythonBuiltinClassType.OverflowError, e);
            }
        }
    }

    @Slot(value = SlotKind.sq_ass_item, isComplex = true)
    @GenerateNodeFactory
    public abstract static class SetItemNode extends SqAssItemBuiltinNode {

        @Specialization
        static void doSingle(VirtualFrame frame, PMMap self, int index, Object val,
                        @Bind Node inliningTarget,
                        @Bind PythonContext context,
                        @CachedLibrary("context.getPosixSupport()") PosixSupportLibrary posixSupportLib,
                        @CachedLibrary(limit = "1") PythonBufferAccessLibrary bufferLib,
                        @Cached PyBytesCheckNode checkNode,
                        @Cached PConstructAndRaiseNode.Lazy constructAndRaiseNode,
                        @Cached PRaiseNode raiseNode) {
            // NB: sq_ass_item and mp_ass_subscript implementations behave differently even with
            // integer indices
            if (self.isClosed()) {
                throw raiseNode.raise(inliningTarget, PythonBuiltinClassType.ValueError, MMAP_CLOSED_OR_INVALID);
            }
            long len = self.getLength();
            long idx = index < 0 ? index + len : index;
            if (idx < 0 || idx >= len) {
                throw raiseNode.raise(inliningTarget, PythonBuiltinClassType.IndexError, MMAP_INDEX_OUT_OF_RANGE);
            }
            if (val == PNone.NO_VALUE) {
                throw raiseNode.raise(inliningTarget, PythonBuiltinClassType.TypeError, MMAP_OBJECT_DOESNT_SUPPORT_ITEM_DELETION);
            }
            if (!(checkNode.execute(inliningTarget, val) && bufferLib.getBufferLength(val) == 1)) {
                throw raiseNode.raise(inliningTarget, PythonBuiltinClassType.IndexError, MMAP_ASSIGNMENT_MUST_BE_LENGTH_1_BYTES);
            }
            if (self.isReadonly()) {
                throw raiseNode.raise(inliningTarget, PythonBuiltinClassType.TypeError, MMAP_CANNOT_MODIFY_READONLY_MEMORY);
            }
            byte b = bufferLib.readByte(val, 0);
            try {
                posixSupportLib.mmapWriteByte(context.getPosixSupport(), self.getPosixSupportHandle(), idx, b);
            } catch (PosixException ex) {
                throw constructAndRaiseNode.get(inliningTarget).raiseOSErrorFromPosixException(frame, ex);
            }
        }
    }

    @Slot(value = SlotKind.mp_ass_subscript, isComplex = true)
    @GenerateNodeFactory
    public abstract static class SetSubscriptNode extends MpAssSubscriptBuiltinNode {

        @Specialization(guards = "!isPSlice(idxObj)")
        static void doSingle(VirtualFrame frame, PMMap self, Object idxObj, Object valueObj,
                        @Bind Node inliningTarget,
                        @Bind PythonContext context,
                        @CachedLibrary("context.getPosixSupport()") PosixSupportLibrary posixSupportLib,
                        @Cached PyIndexCheckNode checkNode,
                        @Cached PyNumberAsSizeNode asSizeNode,
                        @Shared @Cached PConstructAndRaiseNode.Lazy constructAndRaiseNode,
                        @Shared @Cached PRaiseNode raiseNode) {
            // NB: sq_ass_item and mp_ass_subscript implementations behave differently even with
            // integer indices
            if (self.isClosed()) {
                throw raiseNode.raise(inliningTarget, PythonBuiltinClassType.ValueError, MMAP_CLOSED_OR_INVALID);
            }
            if (self.isReadonly()) {
                throw raiseNode.raise(inliningTarget, PythonBuiltinClassType.TypeError, MMAP_CANNOT_MODIFY_READONLY_MEMORY);
            }
            if (!checkNode.execute(inliningTarget, idxObj)) {
                throw raiseNode.raise(inliningTarget, PythonBuiltinClassType.TypeError, MMAP_INDICES_MUST_BE_INTEGER);
            }
            long idx = asSizeNode.executeExact(frame, inliningTarget, idxObj, PythonBuiltinClassType.IndexError);
            long len = self.getLength();
            idx = idx < 0 ? idx + len : idx;
            if (idx < 0 || idx >= len) {
                throw raiseNode.raise(inliningTarget, PythonBuiltinClassType.IndexError, MMAP_INDEX_OUT_OF_RANGE);
            }
            if (valueObj == PNone.NO_VALUE) {
                throw raiseNode.raise(inliningTarget, PythonBuiltinClassType.TypeError, MMAP_OBJECT_DOESNT_SUPPORT_ITEM_DELETION);
            }
            if (!checkNode.execute(inliningTarget, valueObj)) {
                throw raiseNode.raise(inliningTarget, PythonBuiltinClassType.TypeError, MMAP_ITEM_VALUE_MUST_BE_AN_INT);
            }
            int value = asSizeNode.executeExact(frame, inliningTarget, valueObj, PythonBuiltinClassType.TypeError);
            if (value < 0 || value > 255) {
                throw raiseNode.raise(inliningTarget, PythonBuiltinClassType.ValueError, MMAP_ITEM_VALUE_MUST_BE_IN_RANGE);
            }
            try {
                posixSupportLib.mmapWriteByte(context.getPosixSupport(), self.getPosixSupportHandle(), idx, (byte) value);
            } catch (PosixException ex) {
                throw constructAndRaiseNode.get(inliningTarget).raiseOSErrorFromPosixException(frame, ex);
            }
        }

        @Specialization
        static void doSlice(VirtualFrame frame, PMMap self, PSlice slice, Object valueObj,
                        @Bind Node inliningTarget,
                        @Bind PythonContext context,
                        @CachedLibrary("context.getPosixSupport()") PosixSupportLibrary posixSupportLib,
                        @CachedLibrary(limit = "3") PythonBufferAcquireLibrary acquireLib,
                        @CachedLibrary(limit = "3") PythonBufferAccessLibrary bufferLib,
                        @Cached SliceNodes.SliceUnpack sliceUnpack,
                        @Cached SliceNodes.AdjustIndices adjustIndices,
                        @Cached InlinedConditionProfile step1Profile,
                        @Shared @Cached PConstructAndRaiseNode.Lazy constructAndRaiseNode,
                        @Shared @Cached PRaiseNode raiseNode) {
            if (self.isClosed()) {
                throw raiseNode.raise(inliningTarget, PythonBuiltinClassType.ValueError, MMAP_CLOSED_OR_INVALID);
            }
            if (self.isReadonly()) {
                throw raiseNode.raise(inliningTarget, PythonBuiltinClassType.TypeError, MMAP_CANNOT_MODIFY_READONLY_MEMORY);
            }
            int len;
            try {
                len = PInt.intValueExact(self.getLength());
            } catch (OverflowException e) {
                throw raiseNode.raise(inliningTarget, PythonBuiltinClassType.OverflowError);
            }
            SliceInfo info = adjustIndices.execute(inliningTarget, len, sliceUnpack.execute(inliningTarget, slice));
            if (valueObj == PNone.NO_VALUE) {
                throw raiseNode.raise(inliningTarget, PythonBuiltinClassType.TypeError, MMAP_OBJECT_DOESNT_SUPPORT_SLICE_DELETION);
            }
            Object buffer = acquireLib.acquireReadonly(valueObj);
            try {
                int bufferLen = bufferLib.getBufferLength(buffer);
                if (info.sliceLength != bufferLen) {
                    throw raiseNode.raise(inliningTarget, PythonBuiltinClassType.IndexError, MMAP_SLICE_ASSIGNMENT_IS_WRONG_SIZE);
                }
                if (info.sliceLength > 0) {
                    try {
                        if (step1Profile.profile(inliningTarget, info.step == 1)) {
                            posixSupportLib.mmapWriteBytes(context.getPosixSupport(), self.getPosixSupportHandle(),
                                            info.start, bufferLib.getInternalOrCopiedByteArray(buffer), bufferLen);
                        } else {
                            for (int cur = info.start, i = 0; i < info.sliceLength; cur += info.step, i++) {
                                posixSupportLib.mmapWriteByte(context.getPosixSupport(), self.getPosixSupportHandle(), cur, bufferLib.readByte(buffer, i));
                            }
                        }
                    } catch (PosixException ex) {
                        throw constructAndRaiseNode.get(inliningTarget).raiseOSErrorFromPosixException(frame, ex);
                    }
                }
            } finally {
                bufferLib.release(buffer);
            }
        }
    }

    @Slot(SlotKind.sq_length)
    @Slot(SlotKind.mp_length)
    @GenerateUncached
    @GenerateNodeFactory
    public abstract static class LenNode extends LenBuiltinNode {
        @Specialization
        static int len(PMMap self,
                        @Bind Node inliningTarget,
                        @Cached PRaiseNode raiseNode) {
            return PyNumberAsSizeNode.doLongExact(inliningTarget, self.getLength(), PythonBuiltinClassType.OverflowError, raiseNode);
        }
    }

    @Builtin(name = J___ENTER__, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class EnterNode extends PythonUnaryBuiltinNode {
        @Specialization
        static Object size(PMMap self) {
            return self;
        }
    }

    @Builtin(name = J___EXIT__, minNumOfPositionalArgs = 4)
    @GenerateNodeFactory
    abstract static class ExitNode extends PythonBuiltinNode {
        protected static final TruffleString T_CLOSE = tsLiteral("close");

        @Specialization
        static Object size(VirtualFrame frame, PMMap self, @SuppressWarnings("unused") Object typ, @SuppressWarnings("unused") Object val, @SuppressWarnings("unused") Object tb,
                        @Cached("create(T_CLOSE)") LookupAndCallUnaryNode callCloseNode) {
            return callCloseNode.executeObject(frame, self);
        }
    }

    @Builtin(name = "close", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class CloseNode extends PythonUnaryBuiltinNode {

        @Specialization
        static PNone close(PMMap self,
                        @Bind PythonContext context,
                        @CachedLibrary("context.getPosixSupport()") PosixSupportLibrary posixSupportLib) {
            self.close(posixSupportLib, context.getPosixSupport());
            return PNone.NONE;
        }
    }

    @Builtin(name = "closed", minNumOfPositionalArgs = 1, isGetter = true)
    @GenerateNodeFactory
    abstract static class ClosedNode extends PythonUnaryBuiltinNode {

        @Specialization
        static boolean close(PMMap self) {
            return self.isClosed();
        }
    }

    @Builtin(name = "size", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class SizeNode extends PythonBuiltinNode {

        @Specialization
        static long size(PMMap self) {
            return self.getLength();
        }
    }

    @Builtin(name = "resize", minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    abstract static class ResizeNode extends PythonBuiltinNode {

        @Specialization
        @SuppressWarnings("unused")
        static long resize(PMMap self, Object n,
                        @Bind Node inliningTarget) {
            // TODO: implement resize in NFI
            throw PRaiseNode.raiseStatic(inliningTarget, PythonBuiltinClassType.SystemError, ErrorMessages.RESIZING_NOT_AVAILABLE);
        }
    }

    @Builtin(name = "tell", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class TellNode extends PythonBuiltinNode {
        @Specialization
        static long readline(PMMap self) {
            return self.getPos();
        }
    }

    @Builtin(name = "read_byte", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class ReadByteNode extends PythonUnaryBuiltinNode {

        @Specialization
        static int readByte(VirtualFrame frame, PMMap self,
                        @Bind Node inliningTarget,
                        @Bind PythonContext context,
                        @CachedLibrary("context.getPosixSupport()") PosixSupportLibrary posixSupportLib,
                        @Cached PConstructAndRaiseNode.Lazy constructAndRaiseNode,
                        @Cached PRaiseNode raiseNode) {
            if (self.getPos() >= self.getLength()) {
                throw raiseNode.raise(inliningTarget, PythonBuiltinClassType.ValueError, READ_BYTE_OUT_OF_RANGE);
            }
            try {
                byte res = posixSupportLib.mmapReadByte(context.getPosixSupport(), self.getPosixSupportHandle(), self.getPos());
                self.setPos(self.getPos() + 1);
                return res & 0xFF;
            } catch (PosixException e) {
                throw constructAndRaiseNode.get(inliningTarget).raiseOSErrorFromPosixException(frame, e);
            }
        }
    }

    @Builtin(name = "read", minNumOfPositionalArgs = 1, maxNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    abstract static class ReadNode extends PythonBuiltinNode {

        @Specialization
        static PBytes read(VirtualFrame frame, PMMap self, Object n,
                        @Bind Node inliningTarget,
                        @Bind PythonContext context,
                        @Cached InlinedConditionProfile noneProfile,
                        @Cached InlinedConditionProfile emptyProfile,
                        @CachedLibrary("context.getPosixSupport()") PosixSupportLibrary posixLib,
                        @Cached PyIndexCheckNode indexCheckNode,
                        @Cached PyNumberAsSizeNode asSizeNode,
                        @Cached InlinedConditionProfile negativeProfile,
                        @Cached PConstructAndRaiseNode.Lazy constructAndRaiseNode,
                        @Cached PRaiseNode raiseNode) {
            long nread;
            // intentionally accept NO_VALUE and NONE; both mean that we read unlimited # of bytes
            if (noneProfile.profile(inliningTarget, isPNone(n))) {
                nread = self.getRemaining();
            } else {
                // _Py_convert_optional_to_ssize_t:
                if (!indexCheckNode.execute(inliningTarget, n)) {
                    throw raiseNode.raise(inliningTarget, TypeError, ErrorMessages.ARG_SHOULD_BE_INT_OR_NONE, n);
                }
                nread = asSizeNode.executeExact(frame, inliningTarget, n);

                if (negativeProfile.profile(inliningTarget, nread < 0)) {
                    nread = self.getRemaining();
                } else if (nread > self.getRemaining()) {
                    nread = self.getRemaining();
                }
            }
            if (emptyProfile.profile(inliningTarget, nread == 0)) {
                return PFactory.createEmptyBytes(context.getLanguage(inliningTarget));
            }
            try {
                byte[] buffer = MMapBuiltins.readBytes(frame, inliningTarget, self, posixLib, context.getPosixSupport(), self.getPos(), PythonUtils.toIntExact(nread), constructAndRaiseNode);
                self.setPos(self.getPos() + buffer.length);
                return PFactory.createBytes(context.getLanguage(inliningTarget), buffer);
            } catch (OverflowException e) {
                throw raiseNode.raise(inliningTarget, PythonBuiltinClassType.OverflowError, ErrorMessages.TOO_MANY_REMAINING_BYTES_TO_BE_STORED);
            }
        }
    }

    @Builtin(name = J_READLINE, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class ReadlineNode extends PythonUnaryBuiltinNode {
        private static final int BUFFER_SIZE = 1024;

        @Specialization
        static Object readline(VirtualFrame frame, PMMap self,
                        @Bind Node inliningTarget,
                        @Bind PythonContext context,
                        @CachedLibrary("context.getPosixSupport()") PosixSupportLibrary posixLib,
                        @Cached SequenceStorageNodes.AppendNode appendNode,
                        @Cached PConstructAndRaiseNode.Lazy constructAndRaiseNode) {
            // Posix abstraction is leaking here a bit: with read mmapped memory, we'd just read
            // byte by byte, but that would be very inefficient with emulated mmap, so we use a
            // small buffer
            ByteSequenceStorage res = new ByteSequenceStorage(16);
            byte[] buffer = new byte[BUFFER_SIZE];
            int nread;
            outer: while (self.getPos() < self.getLength()) {
                try {
                    nread = posixLib.mmapReadBytes(context.getPosixSupport(), self.getPosixSupportHandle(), self.getPos(), buffer, (int) Math.min(self.getRemaining(), buffer.length));
                } catch (PosixException e) {
                    throw constructAndRaiseNode.get(inliningTarget).raiseOSErrorFromPosixException(frame, e);
                }
                for (int i = 0; i < nread; i++) {
                    byte b = buffer[i];
                    appendNode.execute(inliningTarget, res, b, BytesNodes.BytesLikeNoGeneralizationNode.SUPPLIER);
                    if (b == '\n') {
                        self.setPos(self.getPos() + i + 1);
                        break outer;
                    }
                }
                self.setPos(self.getPos() + nread);
            }
            return PFactory.createBytes(context.getLanguage(inliningTarget), res);
        }
    }

    @Builtin(name = "write", parameterNames = {"$self", "data"})
    @ArgumentClinic(name = "data", conversion = ClinicConversion.ReadableBuffer)
    @GenerateNodeFactory
    abstract static class WriteNode extends PythonBinaryClinicBuiltinNode {

        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return WriteNodeClinicProviderGen.INSTANCE;
        }

        @Specialization(limit = "3")
        static int doIt(VirtualFrame frame, PMMap self, Object dataBuffer,
                        @Bind Node inliningTarget,
                        @Bind PythonContext context,
                        @Cached("createFor(this)") IndirectCallData indirectCallData,
                        @CachedLibrary("dataBuffer") PythonBufferAccessLibrary bufferLib,
                        @CachedLibrary("context.getPosixSupport()") PosixSupportLibrary posixLib,
                        @Cached PConstructAndRaiseNode.Lazy constructAndRaiseNode,
                        @Cached PRaiseNode raiseNode) {
            try {
                if (!self.isWriteable()) {
                    throw raiseNode.raise(inliningTarget, TypeError, ErrorMessages.MMAP_CANNOT_MODIFY_READONLY_MEMORY);
                }
                byte[] dataBytes = bufferLib.getInternalOrCopiedByteArray(dataBuffer);
                int dataLen = bufferLib.getBufferLength(dataBuffer);
                if (self.getPos() > self.getLength() || self.getLength() - self.getPos() < dataLen) {
                    throw raiseNode.raise(inliningTarget, ValueError, ErrorMessages.DATA_OUT_OF_RANGE);
                }
                posixLib.mmapWriteBytes(context.getPosixSupport(), self.getPosixSupportHandle(), self.getPos(), dataBytes, dataLen);
                self.setPos(self.getPos() + dataLen);
                return dataLen;
            } catch (PosixException e) {
                throw constructAndRaiseNode.get(inliningTarget).raiseOSErrorFromPosixException(frame, e);
            } finally {
                bufferLib.release(dataBuffer, frame, indirectCallData);
            }
        }
    }

    @Builtin(name = "seek", parameterNames = {"$self", "dist", "how"})
    @ArgumentClinic(name = "dist", conversion = ClinicConversion.LongIndex)
    @ArgumentClinic(name = "how", conversion = ClinicConversion.Int)
    @GenerateNodeFactory
    abstract static class SeekNode extends PythonTernaryClinicBuiltinNode {
        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return SeekNodeClinicProviderGen.INSTANCE;
        }

        @Specialization
        static Object seek(PMMap self, long dist, int how,
                        @Bind Node inliningTarget,
                        @Cached InlinedBranchProfile errorProfile,
                        @Cached PRaiseNode raiseNode) {
            long where;
            switch (how) {
                case 0: // relative to start
                    where = dist;
                    break;
                case 1: // relative to current position
                    where = self.getPos() + dist;
                    break;
                case 2: // relative to end
                    where = self.getLength() + dist;
                    break;
                default:
                    errorProfile.enter(inliningTarget);
                    throw raiseNode.raise(inliningTarget, PythonBuiltinClassType.ValueError, ErrorMessages.UNKNOWN_S_TYPE, "seek");
            }
            if (where > self.getLength() || where < 0) {
                throw raiseNode.raise(inliningTarget, PythonBuiltinClassType.ValueError, ErrorMessages.SEEK_OUT_OF_RANGE);
            }
            self.setPos(where);
            return PNone.NONE;
        }
    }

    @Builtin(name = "find", minNumOfPositionalArgs = 2, parameterNames = {"$self", "sub", "start", "end"})
    @ArgumentClinic(name = "sub", conversion = ClinicConversion.ReadableBuffer)
    @GenerateNodeFactory
    public abstract static class FindNode extends PythonQuaternaryClinicBuiltinNode {
        private static final int BUFFER_SIZE = 1024; // keep in sync with test_mmap.py

        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return FindNodeClinicProviderGen.INSTANCE;
        }

        @Specialization(limit = "3")
        static long find(VirtualFrame frame, PMMap self, Object subBuffer, Object startIn, Object endIn,
                        @Bind Node inliningTarget,
                        @Bind PythonContext context,
                        @Cached("createFor(this)") IndirectCallData indirectCallData,
                        @CachedLibrary("subBuffer") PythonBufferAccessLibrary bufferLib,
                        @Cached LongIndexConverterNode startConverter,
                        @Cached LongIndexConverterNode endConverter,
                        @CachedLibrary("context.getPosixSupport()") PosixSupportLibrary posixLib,
                        @Cached PConstructAndRaiseNode.Lazy constructAndRaiseNode,
                        @Cached PRaiseNode raiseNode) {
            try {
                long start = normalizeIndex(frame, startConverter, startIn, self.getLength(), self.getPos());
                long end = normalizeIndex(frame, endConverter, endIn, self.getLength(), self.getLength());

                /*
                 * We use two arrays to implement circular buffer, once the search for the needle
                 * would overflow the second buffer, we load more data into the other buffer and
                 * then swap the buffers and continue. This is way more complicated than it needs to
                 * be, but we do not want to access the mmap byte-by-byte as with some
                 * implementations that could be very inefficient.
                 */
                byte[] sub = bufferLib.getInternalOrCopiedByteArray(subBuffer);
                int subLen = bufferLib.getBufferLength(subBuffer);
                int bufferSize = Math.max(BUFFER_SIZE, subLen);
                int buffersIndex = bufferSize;
                byte[] firstBuffer = new byte[bufferSize];
                byte[] secondBuffer = new byte[bufferSize];

                readBytes(frame, inliningTarget, self, posixLib, context.getPosixSupport(), start, secondBuffer, constructAndRaiseNode, raiseNode);
                for (long selfIdx = start; selfIdx <= end - subLen; selfIdx++, buffersIndex++) {
                    // Make sure that the buffers have enough room for the search
                    if (buffersIndex + subLen > bufferSize * 2) {
                        byte[] tmp = firstBuffer;
                        firstBuffer = secondBuffer;
                        secondBuffer = tmp;
                        buffersIndex -= bufferSize; // move to the tail of the first buffer now
                        long readIndex = selfIdx + subLen - 1;
                        readBytes(frame, inliningTarget, self, posixLib, context.getPosixSupport(), readIndex, secondBuffer, constructAndRaiseNode, raiseNode);
                        // It's OK if we read less than buffer size, the outer loop condition
                        // 'selfIdx <= end' and the check in readBytes should cover that we don't
                        // read
                        // garbage from the buffer
                    }
                    boolean found = true;
                    for (int subIdx = 0; subIdx < subLen; subIdx++) {
                        byte value;
                        int currentBuffersIdx = buffersIndex + subIdx;
                        if (currentBuffersIdx >= bufferSize) {
                            value = secondBuffer[currentBuffersIdx % bufferSize];
                        } else {
                            value = firstBuffer[currentBuffersIdx];
                        }
                        if (sub[subIdx] != value) {
                            found = false;
                            break;
                        }
                    }
                    if (found) {
                        return selfIdx;
                    }
                }
                return -1;
            } finally {
                bufferLib.release(subBuffer, frame, indirectCallData);
            }
        }

        private static void readBytes(VirtualFrame frame, Node inliningTarget, PMMap self, PosixSupportLibrary posixLib, PosixSupport posixSupport, long index, byte[] buffer,
                        PConstructAndRaiseNode.Lazy constructAndRaiseNode, PRaiseNode raiseNode) {
            try {
                long remaining = self.getLength() - index;
                int toReadLen = remaining > buffer.length ? buffer.length : (int) remaining;
                int nread = posixLib.mmapReadBytes(posixSupport, self.getPosixSupportHandle(), index, buffer, toReadLen);
                if (toReadLen != nread) {
                    throw raiseNode.raise(inliningTarget, PythonBuiltinClassType.SystemError, MMAP_CHANGED_LENGTH);
                }
            } catch (PosixException ex) {
                throw constructAndRaiseNode.get(inliningTarget).raiseOSErrorFromPosixException(frame, ex);
            }
        }

        private static long normalizeIndex(VirtualFrame frame, LongIndexConverterNode converter, Object idxObj, long len, long defaultValue) {
            if (PGuards.isNoValue(idxObj)) {
                return defaultValue;
            }
            long idx = converter.executeLong(frame, idxObj);
            if (idx < 0) {
                idx += len;
            }
            if (idx < 0) {
                idx = 0;
            } else if (idx > len) {
                idx = len;
            }
            return idx;
        }
    }

    @Builtin(name = "flush", minNumOfPositionalArgs = 1, parameterNames = {"$self", "offset", "size"})
    @GenerateNodeFactory
    @ArgumentClinic(name = "offset", conversion = ClinicConversion.LongIndex, defaultValue = "0")
    abstract static class FlushNode extends PythonTernaryClinicBuiltinNode {

        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return FlushNodeClinicProviderGen.INSTANCE;
        }

        @Specialization
        static Object flush(VirtualFrame frame, PMMap self, long offset, Object sizeObj,
                        @Bind Node inliningTarget,
                        @Bind PythonContext context,
                        @Cached LongIndexConverterNode sizeConversion,
                        @CachedLibrary("context.getPosixSupport()") PosixSupportLibrary posixLib,
                        @Cached PConstructAndRaiseNode.Lazy constructAndRaiseNode,
                        @Cached PRaiseNode raiseNode) {
            long size;
            if (sizeObj == PNone.NO_VALUE) {
                size = self.getLength();
            } else {
                size = sizeConversion.executeLong(frame, sizeObj);
            }

            if (size < 0 || offset < 0 || self.getLength() - offset < size) {
                throw raiseNode.raise(inliningTarget, PythonBuiltinClassType.ValueError, ErrorMessages.FLUSH_VALUES_OUT_OF_RANGE);
            }
            if (self.getAccess() == ACCESS_READ || self.getAccess() == ACCESS_COPY) {
                return PNone.NONE;
            }

            try {
                posixLib.mmapFlush(context.getPosixSupport(), self.getPosixSupportHandle(), offset, self.getLength());
            } catch (PosixException e) {
                throw constructAndRaiseNode.get(inliningTarget).raiseOSErrorFromPosixException(frame, e);
            }
            return PNone.NONE;
        }
    }

    static class ReleaseCallback implements AsyncHandler.AsyncAction {

        private final PMMap.MMapRef ref;

        ReleaseCallback(PMMap.MMapRef ref) {
            this.ref = ref;
        }

        @Override
        public void execute(PythonContext context) {
            if (ref.isReleased()) {
                return;
            }
            PythonLanguage language = context.getLanguage();
            CallTarget callTarget = language.createCachedCallTarget(MMapBuiltins.ReleaseCallback.ReleaserRootNode::new, MMapBuiltins.ReleaseCallback.ReleaserRootNode.class);
            callTarget.call(ref);
        }

        private static class ReleaserRootNode extends RootNode {
            @Child private PosixSupportLibrary posixSupportLibrary = PosixSupportLibrary.getFactory().createDispatched(1);

            ReleaserRootNode(TruffleLanguage<?> language) {
                super(language);
            }

            @Override
            public Object execute(VirtualFrame frame) {
                PMMap.MMapRef ref = (PMMap.MMapRef) frame.getArguments()[0];
                ref.close(posixSupportLibrary, PythonContext.get(this).getPosixSupport());
                return null;
            }
        }
    }
}
