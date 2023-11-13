/*
 * Copyright (c) 2019, 2023, Oracle and/or its affiliates. All rights reserved.
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

import static com.oracle.graal.python.builtins.objects.mmap.PMMap.ACCESS_COPY;
import static com.oracle.graal.python.builtins.objects.mmap.PMMap.ACCESS_READ;
import static com.oracle.graal.python.nodes.BuiltinNames.J_READLINE;
import static com.oracle.graal.python.nodes.ErrorMessages.MMAP_CHANGED_LENGTH;
import static com.oracle.graal.python.nodes.ErrorMessages.MMAP_INDEX_OUT_OF_RANGE;
import static com.oracle.graal.python.nodes.ErrorMessages.READ_BYTE_OUT_OF_RANGE;
import static com.oracle.graal.python.nodes.PGuards.isPNone;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___ADD__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___CONTAINS__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___ENTER__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___EQ__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___EXIT__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___GETITEM__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___GE__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___GT__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___LEN__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___LE__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___LT__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___MUL__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___NE__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___REPR__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___RMUL__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___SETITEM__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___STR__;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.TypeError;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.ValueError;
import static com.oracle.graal.python.util.PythonUtils.tsLiteral;

import java.util.List;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.annotations.ArgumentClinic;
import com.oracle.graal.python.annotations.ArgumentClinic.ClinicConversion;
import com.oracle.graal.python.builtins.Builtin;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.buffer.PythonBufferAccessLibrary;
import com.oracle.graal.python.builtins.objects.bytes.BytesBuiltins.BytesLikeNoGeneralizationNode;
import com.oracle.graal.python.builtins.objects.bytes.PBytes;
import com.oracle.graal.python.builtins.objects.bytes.PBytesLike;
import com.oracle.graal.python.builtins.objects.common.SequenceStorageNodes;
import com.oracle.graal.python.builtins.objects.common.SequenceStorageNodes.ToByteArrayNode;
import com.oracle.graal.python.builtins.objects.ints.PInt;
import com.oracle.graal.python.builtins.objects.mmap.MMapBuiltinsClinicProviders.FindNodeClinicProviderGen;
import com.oracle.graal.python.builtins.objects.mmap.MMapBuiltinsClinicProviders.FlushNodeClinicProviderGen;
import com.oracle.graal.python.builtins.objects.mmap.MMapBuiltinsClinicProviders.SeekNodeClinicProviderGen;
import com.oracle.graal.python.builtins.objects.mmap.MMapBuiltinsClinicProviders.WriteNodeClinicProviderGen;
import com.oracle.graal.python.builtins.objects.range.RangeNodes.LenOfRangeNode;
import com.oracle.graal.python.builtins.objects.slice.PSlice;
import com.oracle.graal.python.builtins.objects.slice.PSlice.SliceInfo;
import com.oracle.graal.python.builtins.objects.slice.SliceNodes.CoerceToIntSlice;
import com.oracle.graal.python.builtins.objects.slice.SliceNodes.ComputeIndices;
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
import com.oracle.graal.python.nodes.function.builtins.PythonBinaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonBinaryClinicBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonQuaternaryClinicBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonTernaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonTernaryClinicBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonUnaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.clinic.ArgumentClinicProvider;
import com.oracle.graal.python.nodes.function.builtins.clinic.LongIndexConverterNode;
import com.oracle.graal.python.nodes.truffle.PythonArithmeticTypes;
import com.oracle.graal.python.nodes.util.CastToByteNode;
import com.oracle.graal.python.runtime.AsyncHandler;
import com.oracle.graal.python.runtime.IndirectCallData;
import com.oracle.graal.python.runtime.PosixSupport;
import com.oracle.graal.python.runtime.PosixSupportLibrary;
import com.oracle.graal.python.runtime.PosixSupportLibrary.PosixException;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.graal.python.runtime.object.PythonObjectFactory;
import com.oracle.graal.python.runtime.sequence.storage.ByteSequenceStorage;
import com.oracle.graal.python.util.OverflowException;
import com.oracle.graal.python.util.PythonUtils;
import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Exclusive;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.NeverDefault;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.dsl.TypeSystemReference;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.profiles.InlinedBranchProfile;
import com.oracle.truffle.api.profiles.InlinedConditionProfile;
import com.oracle.truffle.api.strings.TruffleString;

@CoreFunctions(extendClasses = PythonBuiltinClassType.PMMap)
public final class MMapBuiltins extends PythonBuiltins {

    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return MMapBuiltinsFactory.getFactories();
    }

    @Builtin(name = J___ADD__, minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    abstract static class AddNode extends PythonBinaryBuiltinNode {
    }

    @Builtin(name = J___RMUL__, minNumOfPositionalArgs = 2)
    @Builtin(name = J___MUL__, minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    abstract static class MulNode extends PythonBinaryBuiltinNode {
    }

    @Builtin(name = J___CONTAINS__, minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    abstract static class ContainsNode extends PythonBinaryBuiltinNode {
    }

    @Builtin(name = J___LT__, minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    abstract static class LtNode extends PythonBinaryBuiltinNode {
    }

    @Builtin(name = J___LE__, minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    abstract static class LeNode extends PythonBinaryBuiltinNode {
    }

    @Builtin(name = J___GT__, minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    abstract static class GtNode extends PythonBinaryBuiltinNode {
    }

    @Builtin(name = J___GE__, minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    abstract static class GeNode extends PythonBinaryBuiltinNode {
    }

    @Builtin(name = J___NE__, minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    abstract static class NeNode extends PythonBinaryBuiltinNode {
    }

    @Builtin(name = J___EQ__, minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    abstract static class EqNode extends PythonBinaryBuiltinNode {
    }

    @Builtin(name = J___STR__, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class StrNode extends PythonUnaryBuiltinNode {
    }

    @Builtin(name = J___REPR__, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class ReprNode extends StrNode {
    }

    private static PBytes createEmptyBytes(PythonObjectFactory factory) {
        if (CompilerDirectives.inInterpreter()) {
            return factory.createBytes(PythonUtils.EMPTY_BYTE_ARRAY);
        } else {
            return factory.createBytes(new byte[0]);
        }
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

    @Builtin(name = J___GETITEM__, minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    public abstract static class GetItemNode extends PythonBinaryBuiltinNode {

        @Specialization(guards = "!isPSlice(idxObj)")
        static int doSingle(VirtualFrame frame, PMMap self, Object idxObj,
                        @Bind("this") Node inliningTarget,
                        @CachedLibrary("getPosixSupport()") PosixSupportLibrary posixSupportLib,
                        @Cached PyLongAsLongNode asLongNode,
                        @Exclusive @Cached PConstructAndRaiseNode.Lazy constructAndRaiseNode,
                        @Exclusive @Cached PRaiseNode.Lazy raiseNode) {
            long i = asLongNode.execute(frame, inliningTarget, idxObj);
            long len = self.getLength();
            long idx = i < 0 ? i + len : i;
            if (idx < 0 || idx >= len) {
                throw raiseNode.get(inliningTarget).raise(PythonBuiltinClassType.IndexError, MMAP_INDEX_OUT_OF_RANGE);
            }
            try {
                return posixSupportLib.mmapReadByte(PosixSupport.get(inliningTarget), self.getPosixSupportHandle(), idx) & 0xFF;
            } catch (PosixException e) {
                throw constructAndRaiseNode.get(inliningTarget).raiseOSErrorFromPosixException(frame, e);
            }
        }

        @Specialization
        static Object doSlice(VirtualFrame frame, PMMap self, PSlice idx,
                        @Bind("this") Node inliningTarget,
                        @CachedLibrary("getPosixSupport()") PosixSupportLibrary posixSupportLib,
                        @Cached InlinedConditionProfile emptyProfile,
                        @Cached CoerceToIntSlice sliceCast,
                        @Cached ComputeIndices compute,
                        @Cached LenOfRangeNode sliceLenNode,
                        @Exclusive @Cached PConstructAndRaiseNode.Lazy constructAndRaiseNode,
                        @Cached PythonObjectFactory factory,
                        @Exclusive @Cached PRaiseNode.Lazy raiseNode) {
            try {
                SliceInfo info = compute.execute(frame, sliceCast.execute(inliningTarget, idx), PInt.intValueExact(self.getLength()));
                int len = sliceLenNode.len(inliningTarget, info);
                if (emptyProfile.profile(inliningTarget, len == 0)) {
                    return createEmptyBytes(factory);
                }
                byte[] result = readBytes(frame, inliningTarget, self, posixSupportLib, PosixSupport.get(inliningTarget), info.start, len, constructAndRaiseNode);
                return factory.createBytes(result);
            } catch (OverflowException e) {
                throw raiseNode.get(inliningTarget).raise(PythonBuiltinClassType.OverflowError, e);
            }
        }
    }

    @Builtin(name = J___SETITEM__, minNumOfPositionalArgs = 3)
    @GenerateNodeFactory
    public abstract static class SetItemNode extends PythonTernaryBuiltinNode {

        @Specialization(guards = "!isPSlice(idxObj)")
        static PNone doSingle(VirtualFrame frame, PMMap self, Object idxObj, Object val,
                        @Bind("this") Node inliningTarget,
                        @CachedLibrary("getPosixSupport()") PosixSupportLibrary posixSupportLib,
                        @Cached PyLongAsLongNode asLongNode,
                        @Cached("createCoerce()") CastToByteNode castToByteNode,
                        @Exclusive @Cached InlinedConditionProfile outOfRangeProfile,
                        @Exclusive @Cached PConstructAndRaiseNode.Lazy constructAndRaiseNode,
                        @Exclusive @Cached PRaiseNode.Lazy raiseNode) {
            long i = asLongNode.execute(frame, inliningTarget, idxObj);
            long len = self.getLength();
            long idx = i < 0 ? i + len : i;
            if (outOfRangeProfile.profile(inliningTarget, idx < 0 || idx >= len)) {
                throw raiseNode.get(inliningTarget).raise(PythonBuiltinClassType.IndexError, MMAP_INDEX_OUT_OF_RANGE);
            }
            byte[] bytes = {castToByteNode.execute(frame, val)};
            writeBuffer(frame, inliningTarget, posixSupportLib, self, idx, bytes, 1, constructAndRaiseNode);
            return PNone.NONE;
        }

        @Specialization
        static PNone doSlice(VirtualFrame frame, PMMap self, PSlice idx, PBytesLike val,
                        @Bind("this") Node inliningTarget,
                        @CachedLibrary("getPosixSupport()") PosixSupportLibrary posixSupportLib,
                        @Cached ToByteArrayNode toByteArrayNode,
                        @Exclusive @Cached InlinedConditionProfile invalidStepProfile,
                        @Cached CoerceToIntSlice sliceCast,
                        @Cached ComputeIndices compute,
                        @Cached LenOfRangeNode sliceLen,
                        @Exclusive @Cached PConstructAndRaiseNode.Lazy constructAndRaiseNode,
                        @Exclusive @Cached PRaiseNode.Lazy raiseNode) {
            try {
                long len = self.getLength();
                SliceInfo info = compute.execute(frame, sliceCast.execute(inliningTarget, idx), PInt.intValueExact(len));
                if (invalidStepProfile.profile(inliningTarget, info.step != 1)) {
                    throw raiseNode.get(inliningTarget).raise(PythonBuiltinClassType.SystemError, ErrorMessages.STEP_1_NOT_SUPPORTED);
                }
                byte[] bytes = toByteArrayNode.execute(inliningTarget, val.getSequenceStorage());
                writeBuffer(frame, inliningTarget, posixSupportLib, self, info.start, bytes, sliceLen.len(inliningTarget, info), constructAndRaiseNode);
                return PNone.NONE;
            } catch (OverflowException e) {
                throw raiseNode.get(inliningTarget).raise(PythonBuiltinClassType.OverflowError, e);
            }
        }

        private static void writeBuffer(VirtualFrame frame, Node inliningTarget, PosixSupportLibrary posixSupportLib, PMMap mmap, long idx, byte[] bytes, int len,
                        PConstructAndRaiseNode.Lazy constructAndRaiseNode) {
            try {
                posixSupportLib.mmapWriteBytes(PosixSupport.get(inliningTarget), mmap.getPosixSupportHandle(), idx, bytes, len);
            } catch (PosixException ex) {
                throw constructAndRaiseNode.get(inliningTarget).raiseOSErrorFromPosixException(frame, ex);
            }
        }

        @NeverDefault
        protected static CastToByteNode createCoerce() {
            return CastToByteNode.create(true);
        }
    }

    @Builtin(name = J___LEN__, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class LenNode extends PythonUnaryBuiltinNode {
        @Specialization
        static long len(PMMap self) {
            return self.getLength();
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
        PNone close(PMMap self,
                        @CachedLibrary("getPosixSupport()") PosixSupportLibrary posixSupportLib) {
            self.close(posixSupportLib, getPosixSupport());
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
                        @Cached PRaiseNode raiseNode) {
            // TODO: implement resize in NFI
            throw raiseNode.raise(PythonBuiltinClassType.SystemError, ErrorMessages.RESIZING_NOT_AVAILABLE);
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
    @TypeSystemReference(PythonArithmeticTypes.class)
    abstract static class ReadByteNode extends PythonUnaryBuiltinNode {

        @Specialization
        static int readByte(VirtualFrame frame, PMMap self,
                        @Bind("this") Node inliningTarget,
                        @CachedLibrary("getPosixSupport()") PosixSupportLibrary posixSupportLib,
                        @Cached PConstructAndRaiseNode.Lazy constructAndRaiseNode,
                        @Cached PRaiseNode.Lazy raiseNode) {
            if (self.getPos() >= self.getLength()) {
                throw raiseNode.get(inliningTarget).raise(PythonBuiltinClassType.ValueError, READ_BYTE_OUT_OF_RANGE);
            }
            try {
                byte res = posixSupportLib.mmapReadByte(PosixSupport.get(inliningTarget), self.getPosixSupportHandle(), self.getPos());
                self.setPos(self.getPos() + 1);
                return res & 0xFF;
            } catch (PosixException e) {
                throw constructAndRaiseNode.get(inliningTarget).raiseOSErrorFromPosixException(frame, e);
            }
        }
    }

    @Builtin(name = "read", minNumOfPositionalArgs = 1, maxNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    @TypeSystemReference(PythonArithmeticTypes.class)
    abstract static class ReadNode extends PythonBuiltinNode {

        @Specialization
        static PBytes read(VirtualFrame frame, PMMap self, Object n,
                        @Bind("this") Node inliningTarget,
                        @Cached InlinedConditionProfile noneProfile,
                        @Cached InlinedConditionProfile emptyProfile,
                        @CachedLibrary("getPosixSupport()") PosixSupportLibrary posixLib,
                        @Cached PyIndexCheckNode indexCheckNode,
                        @Cached PyNumberAsSizeNode asSizeNode,
                        @Cached InlinedConditionProfile negativeProfile,
                        @Cached PConstructAndRaiseNode.Lazy constructAndRaiseNode,
                        @Cached PythonObjectFactory factory,
                        @Cached PRaiseNode.Lazy raiseNode) {
            long nread;
            // intentionally accept NO_VALUE and NONE; both mean that we read unlimited # of bytes
            if (noneProfile.profile(inliningTarget, isPNone(n))) {
                nread = self.getRemaining();
            } else {
                // _Py_convert_optional_to_ssize_t:
                if (!indexCheckNode.execute(inliningTarget, n)) {
                    throw raiseNode.get(inliningTarget).raise(TypeError, ErrorMessages.ARG_SHOULD_BE_INT_OR_NONE, n);
                }
                nread = asSizeNode.executeExact(frame, inliningTarget, n);

                if (negativeProfile.profile(inliningTarget, nread < 0)) {
                    nread = self.getRemaining();
                } else if (nread > self.getRemaining()) {
                    nread = self.getRemaining();
                }
            }
            if (emptyProfile.profile(inliningTarget, nread == 0)) {
                return createEmptyBytes(factory);
            }
            try {
                byte[] buffer = MMapBuiltins.readBytes(frame, inliningTarget, self, posixLib, PosixSupport.get(inliningTarget), self.getPos(), PythonUtils.toIntExact(nread), constructAndRaiseNode);
                self.setPos(self.getPos() + buffer.length);
                return factory.createBytes(buffer);
            } catch (OverflowException e) {
                throw raiseNode.get(inliningTarget).raise(PythonBuiltinClassType.OverflowError, ErrorMessages.TOO_MANY_REMAINING_BYTES_TO_BE_STORED);
            }
        }
    }

    @Builtin(name = J_READLINE, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class ReadlineNode extends PythonUnaryBuiltinNode {
        private static final int BUFFER_SIZE = 1024;

        @Specialization
        Object readline(VirtualFrame frame, PMMap self,
                        @Bind("this") Node inliningTarget,
                        @CachedLibrary("getPosixSupport()") PosixSupportLibrary posixLib,
                        @Cached SequenceStorageNodes.AppendNode appendNode,
                        @Cached PConstructAndRaiseNode.Lazy constructAndRaiseNode,
                        @Cached PythonObjectFactory factory) {
            // Posix abstraction is leaking here a bit: with read mmapped memory, we'd just read
            // byte by byte, but that would be very inefficient with emulated mmap, so we use a
            // small buffer
            ByteSequenceStorage res = new ByteSequenceStorage(16);
            byte[] buffer = new byte[BUFFER_SIZE];
            int nread;
            outer: while (self.getPos() < self.getLength()) {
                try {
                    nread = posixLib.mmapReadBytes(getPosixSupport(), self.getPosixSupportHandle(), self.getPos(), buffer, (int) Math.min(self.getRemaining(), buffer.length));
                } catch (PosixException e) {
                    throw constructAndRaiseNode.get(inliningTarget).raiseOSErrorFromPosixException(frame, e);
                }
                for (int i = 0; i < nread; i++) {
                    byte b = buffer[i];
                    appendNode.execute(inliningTarget, res, b, BytesLikeNoGeneralizationNode.SUPPLIER);
                    if (b == '\n') {
                        self.setPos(self.getPos() + i + 1);
                        break outer;
                    }
                }
                self.setPos(self.getPos() + nread);
            }
            return factory.createBytes(res);
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
                        @Bind("this") Node inliningTarget,
                        @Cached("createFor(this)") IndirectCallData indirectCallData,
                        @CachedLibrary("dataBuffer") PythonBufferAccessLibrary bufferLib,
                        @CachedLibrary("getPosixSupport()") PosixSupportLibrary posixLib,
                        @Cached PConstructAndRaiseNode.Lazy constructAndRaiseNode,
                        @Cached PRaiseNode.Lazy raiseNode) {
            try {
                if (!self.isWriteable()) {
                    throw raiseNode.get(inliningTarget).raise(TypeError, ErrorMessages.MMAP_CANNOT_MODIFY_READONLY_MEMORY);
                }
                byte[] dataBytes = bufferLib.getInternalOrCopiedByteArray(dataBuffer);
                int dataLen = bufferLib.getBufferLength(dataBuffer);
                if (self.getPos() > self.getLength() || self.getLength() - self.getPos() < dataLen) {
                    throw raiseNode.get(inliningTarget).raise(ValueError, ErrorMessages.DATA_OUT_OF_RANGE);
                }
                posixLib.mmapWriteBytes(PosixSupport.get(inliningTarget), self.getPosixSupportHandle(), self.getPos(), dataBytes, dataLen);
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
    @TypeSystemReference(PythonArithmeticTypes.class)
    abstract static class SeekNode extends PythonTernaryClinicBuiltinNode {
        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return SeekNodeClinicProviderGen.INSTANCE;
        }

        @Specialization
        static Object seek(PMMap self, long dist, int how,
                        @Bind("this") Node inliningTarget,
                        @Cached InlinedBranchProfile errorProfile,
                        @Cached PRaiseNode.Lazy raiseNode) {
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
                    throw raiseNode.get(inliningTarget).raise(PythonBuiltinClassType.ValueError, ErrorMessages.UNKNOWN_S_TYPE, "seek");
            }
            if (where > self.getLength() || where < 0) {
                throw raiseNode.get(inliningTarget).raise(PythonBuiltinClassType.ValueError, ErrorMessages.SEEK_OUT_OF_RANGE);
            }
            self.setPos(where);
            return PNone.NONE;
        }
    }

    @Builtin(name = "find", minNumOfPositionalArgs = 2, parameterNames = {"$self", "sub", "start", "end"})
    @ArgumentClinic(name = "sub", conversion = ClinicConversion.ReadableBuffer)
    @GenerateNodeFactory
    @TypeSystemReference(PythonArithmeticTypes.class)
    public abstract static class FindNode extends PythonQuaternaryClinicBuiltinNode {
        private static final int BUFFER_SIZE = 1024; // keep in sync with test_mmap.py

        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return FindNodeClinicProviderGen.INSTANCE;
        }

        @Specialization(limit = "3")
        static long find(VirtualFrame frame, PMMap self, Object subBuffer, Object startIn, Object endIn,
                        @Bind("this") Node inliningTarget,
                        @Cached("createFor(this)") IndirectCallData indirectCallData,
                        @CachedLibrary("subBuffer") PythonBufferAccessLibrary bufferLib,
                        @Cached LongIndexConverterNode startConverter,
                        @Cached LongIndexConverterNode endConverter,
                        @CachedLibrary("getPosixSupport()") PosixSupportLibrary posixLib,
                        @Cached PConstructAndRaiseNode.Lazy constructAndRaiseNode,
                        @Cached PRaiseNode.Lazy raiseNode) {
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

                readBytes(frame, inliningTarget, self, posixLib, start, secondBuffer, constructAndRaiseNode, raiseNode);
                for (long selfIdx = start; selfIdx <= end - subLen; selfIdx++, buffersIndex++) {
                    // Make sure that the buffers have enough room for the search
                    if (buffersIndex + subLen > bufferSize * 2) {
                        byte[] tmp = firstBuffer;
                        firstBuffer = secondBuffer;
                        secondBuffer = tmp;
                        buffersIndex -= bufferSize; // move to the tail of the first buffer now
                        long readIndex = selfIdx + subLen - 1;
                        readBytes(frame, inliningTarget, self, posixLib, readIndex, secondBuffer, constructAndRaiseNode, raiseNode);
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

        private static void readBytes(VirtualFrame frame, Node inliningTarget, PMMap self, PosixSupportLibrary posixLib, long index, byte[] buffer, PConstructAndRaiseNode.Lazy constructAndRaiseNode,
                        PRaiseNode.Lazy raiseNode) {
            try {
                long remaining = self.getLength() - index;
                int toReadLen = remaining > buffer.length ? buffer.length : (int) remaining;
                int nread = posixLib.mmapReadBytes(PosixSupport.get(inliningTarget), self.getPosixSupportHandle(), index, buffer, toReadLen);
                if (toReadLen != nread) {
                    throw raiseNode.get(inliningTarget).raise(PythonBuiltinClassType.SystemError, MMAP_CHANGED_LENGTH);
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
        Object flush(VirtualFrame frame, PMMap self, long offset, Object sizeObj,
                        @Bind("this") Node inliningTarget,
                        @Cached LongIndexConverterNode sizeConversion,
                        @CachedLibrary("getPosixSupport()") PosixSupportLibrary posixLib,
                        @Cached PConstructAndRaiseNode.Lazy constructAndRaiseNode,
                        @Cached PRaiseNode.Lazy raiseNode) {
            long size;
            if (sizeObj == PNone.NO_VALUE) {
                size = self.getLength();
            } else {
                size = sizeConversion.executeLong(frame, sizeObj);
            }

            if (size < 0 || offset < 0 || self.getLength() - offset < size) {
                throw raiseNode.get(inliningTarget).raise(PythonBuiltinClassType.ValueError, ErrorMessages.FLUSH_VALUES_OUT_OF_RANGE);
            }
            if (self.getAccess() == ACCESS_READ || self.getAccess() == ACCESS_COPY) {
                return PNone.NONE;
            }

            try {
                posixLib.mmapFlush(getPosixSupport(), self.getPosixSupportHandle(), offset, self.getLength());
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
