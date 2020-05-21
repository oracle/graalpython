/*
 * Copyright (c) 2019, 2020, Oracle and/or its affiliates. All rights reserved.
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

import static com.oracle.graal.python.nodes.SpecialMethodNames.__ADD__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__CONTAINS__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__ENTER__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__EQ__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__EXIT__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__GETITEM__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__GE__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__GT__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__LEN__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__LE__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__LT__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__MUL__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__NE__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__REPR__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__RMUL__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__STR__;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.Channel;
import java.nio.channels.SeekableByteChannel;
import java.util.List;

import com.oracle.graal.python.builtins.Builtin;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.bytes.BytesBuiltins.BytesLikeNoGeneralizationNode;
import com.oracle.graal.python.builtins.objects.bytes.BytesNodes;
import com.oracle.graal.python.builtins.objects.bytes.PBytes;
import com.oracle.graal.python.builtins.objects.bytes.PIBytesLike;
import com.oracle.graal.python.builtins.objects.common.SequenceNodes;
import com.oracle.graal.python.builtins.objects.common.SequenceStorageNodes;
import com.oracle.graal.python.builtins.objects.exception.OSErrorEnum;
import com.oracle.graal.python.builtins.objects.function.PArguments;
import com.oracle.graal.python.builtins.objects.ints.PInt;
import com.oracle.graal.python.builtins.objects.memoryview.PMemoryView;
import com.oracle.graal.python.builtins.objects.object.PythonObjectLibrary;
import com.oracle.graal.python.builtins.objects.slice.PSlice;
import com.oracle.graal.python.builtins.objects.slice.PSlice.SliceInfo;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.SpecialMethodNames;
import com.oracle.graal.python.nodes.call.special.LookupAndCallUnaryNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonBinaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonTernaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonUnaryBuiltinNode;
import com.oracle.graal.python.nodes.truffle.PythonArithmeticTypes;
import com.oracle.graal.python.nodes.util.CannotCastException;
import com.oracle.graal.python.nodes.util.CastToByteNode;
import com.oracle.graal.python.nodes.util.CastToJavaLongLossyNode;
import com.oracle.graal.python.nodes.util.ChannelNodes;
import com.oracle.graal.python.nodes.util.ChannelNodes.ReadByteFromChannelNode;
import com.oracle.graal.python.nodes.util.ChannelNodes.ReadFromChannelNode;
import com.oracle.graal.python.nodes.util.ChannelNodes.WriteByteToChannelNode;
import com.oracle.graal.python.nodes.util.ChannelNodes.WriteToChannelNode;
import com.oracle.graal.python.runtime.exception.PythonErrorType;
import com.oracle.graal.python.runtime.sequence.storage.ByteSequenceStorage;
import com.oracle.graal.python.runtime.sequence.storage.SequenceStorage;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.dsl.TypeSystemReference;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.profiles.BranchProfile;
import com.oracle.truffle.api.profiles.ConditionProfile;

@CoreFunctions(extendClasses = PythonBuiltinClassType.PMMap)
public class MMapBuiltins extends PythonBuiltins {

    protected interface MMapBaseNode {
    }

    protected interface ByteReadingNode extends MMapBaseNode {

        default ReadByteFromChannelNode createValueError(PRaiseNode raise) {
            return ReadByteFromChannelNode.create(() -> new ChannelNodes.ReadByteErrorHandler() {

                @Override
                public int execute(Channel channel) {
                    throw raise.raise(PythonBuiltinClassType.ValueError, "read byte out of range");
                }
            });
        }

        default ReadByteFromChannelNode createIndexError(PRaiseNode raise) {
            return ReadByteFromChannelNode.create(() -> new ChannelNodes.ReadByteErrorHandler() {

                @Override
                public int execute(Channel channel) {
                    throw raise.raise(PythonBuiltinClassType.IndexError, "mmap index out of range");
                }
            });

        }
    }

    protected interface ByteWritingNode extends MMapBaseNode {

        default WriteByteToChannelNode createValueError(PRaiseNode raise) {
            return WriteByteToChannelNode.create(() -> new ChannelNodes.WriteByteErrorHandler() {

                @Override
                public void execute(Channel channel, byte b) {
                    throw raise.raise(PythonBuiltinClassType.ValueError, "write byte out of range");
                }
            });
        }

        default WriteByteToChannelNode createIndexError(PRaiseNode raise) {
            return WriteByteToChannelNode.create(() -> new ChannelNodes.WriteByteErrorHandler() {

                @Override
                public void execute(Channel channel, byte b) {
                    throw raise.raise(PythonBuiltinClassType.IndexError, "mmap index out of range");
                }
            });

        }
    }

    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return MMapBuiltinsFactory.getFactories();
    }

    @Builtin(name = __ADD__, minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    abstract static class AddNode extends PythonBinaryBuiltinNode {
    }

    @Builtin(name = __MUL__, minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    abstract static class MulNode extends PythonBuiltinNode {
    }

    @Builtin(name = __RMUL__, minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    abstract static class RMulNode extends MulNode {
    }

    @Builtin(name = __CONTAINS__, minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    abstract static class ContainsNode extends PythonBinaryBuiltinNode {
    }

    @Builtin(name = __LT__, minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    abstract static class LtNode extends PythonBinaryBuiltinNode {
    }

    @Builtin(name = __LE__, minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    abstract static class LeNode extends PythonBinaryBuiltinNode {
    }

    @Builtin(name = __GT__, minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    abstract static class GtNode extends PythonBinaryBuiltinNode {
    }

    @Builtin(name = __GE__, minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    abstract static class GeNode extends PythonBinaryBuiltinNode {
    }

    @Builtin(name = __NE__, minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    abstract static class NeNode extends PythonBinaryBuiltinNode {
    }

    @Builtin(name = __EQ__, minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    abstract static class EqNode extends PythonBinaryBuiltinNode {
    }

    @Builtin(name = __STR__, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class StrNode extends PythonUnaryBuiltinNode {
    }

    @Builtin(name = __REPR__, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class ReprNode extends StrNode {
    }

    @Builtin(name = __GETITEM__, minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    public abstract static class GetItemNode extends PythonBinaryBuiltinNode implements ByteReadingNode {

        public abstract Object executeObject(PMMap self, Object idxObj);

        public abstract int executeInt(PMMap self, Object idxObj);

        public abstract long executeLong(PMMap self, Object idxObj);

        @Specialization(guards = "!isPSlice(idxObj)", limit = "1")
        int doSingle(PMMap self, Object idxObj,
                        @SuppressWarnings("unused") @Cached PRaiseNode raise,
                        @Cached("createIndexError(raise)") ReadByteFromChannelNode readByteNode,
                        @CachedLibrary("idxObj") PythonObjectLibrary libIdx) {

            try {
                long i = libIdx.asJavaLong(idxObj);
                long len = self.getLength();
                SeekableByteChannel channel = self.getChannel();
                long idx = i < 0 ? i + len : i;

                // save current position
                long oldPos = PMMap.position(channel);

                PMMap.position(channel, idx);
                int res = readByteNode.execute(channel) & 0xFF;

                // restore position
                PMMap.position(channel, oldPos);

                return res;

            } catch (IOException e) {
                throw raise(PythonBuiltinClassType.OSError, e);
            }
        }

        @Specialization
        Object doSlice(PMMap self, PSlice idx,
                        @Cached("create()") ReadFromChannelNode readNode) {
            try {
                long len = self.getLength();
                SliceInfo info = idx.computeIndices(PInt.intValueExact(len));
                SeekableByteChannel channel = self.getChannel();

                // save current position
                long oldPos = PMMap.position(channel);

                PMMap.position(channel, info.start);
                ByteSequenceStorage s = readNode.execute(channel, info.length);

                // restore position
                PMMap.position(channel, oldPos);

                return factory().createBytes(s);
            } catch (IOException e) {
                throw raise(PythonBuiltinClassType.OSError, e);
            }
        }
    }

    @Builtin(name = SpecialMethodNames.__SETITEM__, minNumOfPositionalArgs = 3)
    @GenerateNodeFactory
    abstract static class SetItemNode extends PythonTernaryBuiltinNode implements ByteWritingNode {

        @Specialization(guards = "!isPSlice(idxObj)", limit = "1")
        PNone doSingle(PMMap self, Object idxObj, Object val,
                        @SuppressWarnings("unused") @Cached PRaiseNode raise,
                        @Cached("createIndexError(raise)") WriteByteToChannelNode writeByteNode,
                        @CachedLibrary("idxObj") PythonObjectLibrary libIdx,
                        @Cached("createCoerce()") CastToByteNode castToByteNode,
                        @Cached("createBinaryProfile()") ConditionProfile outOfRangeProfile) {

            try {
                long i = libIdx.asJavaLong(idxObj);
                long len = self.getLength();
                SeekableByteChannel channel = self.getChannel();
                long idx = i < 0 ? i + len : i;

                if (outOfRangeProfile.profile(idx < 0 || idx >= len)) {
                    throw raise(PythonBuiltinClassType.IndexError, "mmap index out of range");
                }

                // save current position
                long oldPos = PMMap.position(channel);

                PMMap.position(channel, idx);
                writeByteNode.execute(channel, castToByteNode.execute(null, val));

                // restore position
                PMMap.position(channel, oldPos);

                return PNone.NONE;

            } catch (IOException e) {
                throw raise(PythonBuiltinClassType.OSError, e);
            }
        }

        @Specialization
        PNone doSlice(PMMap self, PSlice idx, PIBytesLike val,
                        @Cached("create()") WriteToChannelNode writeNode,
                        @Cached("create()") SequenceNodes.GetSequenceStorageNode getStorageNode,
                        @Cached("createBinaryProfile()") ConditionProfile invalidStepProfile) {

            try {
                long len = self.getLength();
                SliceInfo info = idx.computeIndices(PInt.intValueExact(len));
                SeekableByteChannel channel = self.getChannel();

                if (invalidStepProfile.profile(info.step != 1)) {
                    throw raise(PythonBuiltinClassType.SystemError, "step != 1 not supported");
                }

                // save current position
                long oldPos = PMMap.position(channel);

                PMMap.position(channel, info.start);
                writeNode.execute(channel, getStorageNode.execute(val), info.length);

                // restore position
                PMMap.position(channel, oldPos);

                return PNone.NONE;

            } catch (IOException e) {
                throw raise(PythonBuiltinClassType.OSError, e);
            }
        }

        protected static CastToByteNode createCoerce() {
            return CastToByteNode.create(true);
        }
    }

    @Builtin(name = __LEN__, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class LenNode extends PythonUnaryBuiltinNode {
        @Specialization
        static long len(PMMap self) {
            return self.getLength();
        }
    }

    @Builtin(name = __ENTER__, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class EnterNode extends PythonUnaryBuiltinNode {
        @Specialization
        Object size(PMMap self) {
            return self;
        }
    }

    @Builtin(name = __EXIT__, minNumOfPositionalArgs = 4)
    @GenerateNodeFactory
    abstract static class ExitNode extends PythonBuiltinNode {
        protected static final String CLOSE = "close";

        @Specialization
        Object size(VirtualFrame frame, PMMap self, @SuppressWarnings("unused") Object typ, @SuppressWarnings("unused") Object val, @SuppressWarnings("unused") Object tb,
                        @Cached("create(CLOSE)") LookupAndCallUnaryNode callCloseNode) {
            return callCloseNode.executeObject(frame, self);
        }
    }

    @Builtin(name = "close", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class CloseNode extends PythonUnaryBuiltinNode {

        @Specialization
        PNone doClose(PMMap self,
                        @Cached PRaiseNode raiseNode) {
            try {
                close(self);
            } catch (IOException e) {
                throw raiseNode.raise(PythonErrorType.BufferError, "cannot close exported pointers exist");
            }
            return PNone.NONE;
        }

        @TruffleBoundary
        private static void close(PMMap self) throws IOException {
            self.getChannel().close();
        }
    }

    @Builtin(name = "closed", minNumOfPositionalArgs = 1, isGetter = true)
    @GenerateNodeFactory
    abstract static class ClosedNode extends PythonUnaryBuiltinNode {

        @Specialization
        @TruffleBoundary
        boolean close(PMMap self) {
            return !self.getChannel().isOpen();
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

    @Builtin(name = "tell", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class TellNode extends PythonBuiltinNode implements ByteReadingNode {
        @Specialization
        long readline(VirtualFrame frame, PMMap self) {
            try {
                SeekableByteChannel channel = self.getChannel();
                return PMMap.position(channel) - self.getOffset();
            } catch (IOException e) {
                throw raiseOSError(frame, OSErrorEnum.EIO, e);
            }
        }
    }

    @Builtin(name = "read_byte", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    @TypeSystemReference(PythonArithmeticTypes.class)
    abstract static class ReadByteNode extends PythonUnaryBuiltinNode implements ByteReadingNode {

        @Specialization
        int readByte(PMMap self,
                        @SuppressWarnings("unused") @Cached PRaiseNode raise,
                        @Cached("createValueError(raise)") ReadByteFromChannelNode readByteNode) {
            return readByteNode.execute(self.getChannel());
        }
    }

    @Builtin(name = "read", minNumOfPositionalArgs = 1, maxNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    @TypeSystemReference(PythonArithmeticTypes.class)
    abstract static class ReadNode extends PythonBuiltinNode {

        @Specialization
        PBytes readUnlimited(PMMap self, @SuppressWarnings("unused") PNone n,
                        @Cached("create()") ReadFromChannelNode readChannelNode) {
            // intentionally accept NO_VALUE and NONE; both mean that we read unlimited amount of
            // bytes
            ByteSequenceStorage res = readChannelNode.execute(self.getChannel(), ReadFromChannelNode.MAX_READ);
            return factory().createBytes(res);
        }

        @Specialization(guards = "!isNoValue(n)", limit = "getCallSiteInlineCacheMaxDepth()")
        PBytes read(VirtualFrame frame, PMMap self, Object n,
                        @Cached("create()") ReadFromChannelNode readChannelNode,
                        @CachedLibrary("n") PythonObjectLibrary lib,
                        @Cached("createBinaryProfile()") ConditionProfile negativeProfile) {
            int nread = lib.asSizeWithState(n, PArguments.getThreadState(frame));
            if (negativeProfile.profile(nread < 0)) {
                return readUnlimited(self, PNone.NO_VALUE, readChannelNode);
            }
            ByteSequenceStorage res = readChannelNode.execute(self.getChannel(), nread);
            return factory().createBytes(res);
        }

    }

    @Builtin(name = "readline", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class ReadlineNode extends PythonUnaryBuiltinNode implements ByteReadingNode {

        @Specialization
        Object readline(PMMap self,
                        @Cached SequenceStorageNodes.AppendNode appendNode) {

            try {
                ByteBuffer buf = ByteBuffer.allocate(4096);
                SeekableByteChannel channel = self.getChannel();
                ByteSequenceStorage res = new ByteSequenceStorage(16);
                // search for newline char
                outer: while (readIntoBuffer(channel, buf) > 0) {
                    buf.flip();
                    while (buf.hasRemaining()) {
                        byte b = buf.get();
                        // CPython really tests for '\n' only
                        if (b != (byte) '\n') {
                            appendNode.execute(res, b, BytesLikeNoGeneralizationNode.SUPPLIER);
                        } else {
                            // recover correct position (i.e. number of remaining bytes in buffer)
                            PMMap.position(channel, PMMap.position(channel) - buf.remaining() - 1);
                            break outer;
                        }
                    }
                    buf.clear();
                }
                return factory().createBytes(res);
            } catch (IOException e) {
                throw raise(PythonBuiltinClassType.OSError, e);
            }
        }

        @TruffleBoundary(allowInlining = true)
        private static int readIntoBuffer(SeekableByteChannel ch, ByteBuffer dst) throws IOException {
            return ch.read(dst);
        }
    }

    @Builtin(name = "write", minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    abstract static class WriteNode extends PythonBinaryBuiltinNode {

        @Specialization
        int writeBytesLike(PMMap self, PIBytesLike bytesLike,
                        @Cached("create()") WriteToChannelNode writeNode,
                        @Cached("create()") SequenceNodes.GetSequenceStorageNode getStorageNode) {
            SeekableByteChannel channel = self.getChannel();
            return writeNode.execute(channel, getStorageNode.execute(bytesLike), Integer.MAX_VALUE);
        }

        @Specialization
        int writeMemoryview(VirtualFrame frame, PMMap self, PMemoryView memoryView,
                        @Cached("create()") WriteToChannelNode writeNode,
                        @Cached("create()") BytesNodes.ToBytesNode toBytesNode) {
            byte[] data = toBytesNode.execute(frame, memoryView);
            return writeNode.execute(self.getChannel(), new ByteSequenceStorage(data), Integer.MAX_VALUE);
        }
    }

    @Builtin(name = "seek", minNumOfPositionalArgs = 2, maxNumOfPositionalArgs = 3)
    @GenerateNodeFactory
    @TypeSystemReference(PythonArithmeticTypes.class)
    abstract static class SeekNode extends PythonBuiltinNode implements MMapBaseNode {
        @Specialization(guards = "isNoValue(how)")
        Object seek(VirtualFrame frame, PMMap self, long dist, @SuppressWarnings("unused") PNone how,
                        @Shared("errorProfile") @Cached BranchProfile errorProfile,
                        @Shared("library") @CachedLibrary(limit = "getCallSiteInlineCacheMaxDepth()") PythonObjectLibrary lib) {
            return seek(frame, self, dist, 0, errorProfile, lib);
        }

        @Specialization(guards = "!isNoValue(how)")
        Object seek(VirtualFrame frame, PMMap self, long dist, Object how,
                        @Shared("errorProfile") @Cached BranchProfile errorProfile,
                        @Shared("library") @CachedLibrary(limit = "getCallSiteInlineCacheMaxDepth()") PythonObjectLibrary lib) {
            try {
                SeekableByteChannel channel = self.getChannel();
                long size;
                if (self.getLength() == 0) {
                    size = PMMap.size(channel) - self.getOffset();
                } else {
                    size = self.getLength();
                }
                long where;
                int ihow = lib.asSizeWithState(how, PArguments.getThreadState(frame));
                switch (ihow) {
                    case 0: /* relative to start */
                        where = dist;
                        break;
                    case 1: /* relative to current position */
                        where = PMMap.position(channel) + dist;
                        break;
                    case 2: /* relative to end */
                        where = size + dist;
                        break;
                    default:
                        errorProfile.enter();
                        throw raise(PythonBuiltinClassType.ValueError, "unknown seek type");
                }
                if (where > size || where < 0) {
                    errorProfile.enter();
                    throw raise(PythonBuiltinClassType.ValueError, "seek out of range");
                }
                PMMap.position(channel, where);
                return PNone.NONE;
            } catch (IOException e) {
                errorProfile.enter();
                throw raiseOSError(frame, OSErrorEnum.EIO, e);
            }
        }
    }

    @Builtin(name = "find", minNumOfPositionalArgs = 2, maxNumOfPositionalArgs = 4)
    @GenerateNodeFactory
    @TypeSystemReference(PythonArithmeticTypes.class)
    public abstract static class FindNode extends PythonBuiltinNode implements ByteReadingNode {

        @Child private SequenceStorageNodes.GetItemNode getRightItemNode;

        public abstract long execute(VirtualFrame frame, PMMap bytes, Object sub, Object starting, Object ending);

        @Specialization
        long find(VirtualFrame frame, PMMap primary, PIBytesLike sub, Object starting, Object ending,
                        @Shared("castLong") @Cached CastToJavaLongLossyNode castLong,
                        @SuppressWarnings("unused") @Cached PRaiseNode raise,
                        @Cached("createValueError(raise)") ReadByteFromChannelNode readByteNode) {
            try {
                SeekableByteChannel channel = primary.getChannel();
                long len1 = PMMap.size(channel);

                SequenceStorage needle = sub.getSequenceStorage();
                int len2 = needle.length();

                long s = castToLong(castLong, starting, 0);
                long e = castToLong(castLong, ending, len1);

                long start = s < 0 ? s + len1 : s;
                long end = e < 0 ? e + len1 : e;

                if (start >= len1 || len1 < len2) {
                    return -1;
                } else if (end > len1) {
                    end = len1;
                }

                // TODO implement a more efficient algorithm
                outer: for (long i = start; i < end; i++) {
                    // TODO(fa) don't seek but use circular buffer
                    PMMap.position(channel, i);
                    for (int j = 0; j < len2; j++) {
                        int hb = readByteNode.execute(channel);
                        int nb = getGetRightItemNode().executeInt(frame, needle, j);
                        if (nb != hb || i + j >= end) {
                            continue outer;
                        }
                    }
                    return i;
                }
                return -1;
            } catch (IOException e) {
                throw raise(PythonBuiltinClassType.OSError, e);
            }
        }

        @Specialization
        long find(PMMap primary, int sub, Object starting, @SuppressWarnings("unused") Object ending,
                        @Shared("castLong") @Cached CastToJavaLongLossyNode castLong,
                        @SuppressWarnings("unused") @Cached PRaiseNode raise,
                        @Cached("createValueError(raise)") ReadByteFromChannelNode readByteNode) {
            try {
                SeekableByteChannel channel = primary.getChannel();
                long len1 = PMMap.size(channel);

                long s = castToLong(castLong, starting, 0);
                long e = castToLong(castLong, ending, len1);

                long start = s < 0 ? s + len1 : s;
                long end = Math.max(e < 0 ? e + len1 : e, len1);

                PMMap.position(channel, start);

                for (long i = start; i < end; i++) {
                    int hb = readByteNode.execute(channel);
                    if (hb == sub) {
                        return i;
                    }
                }
                return -1;
            } catch (IOException e) {
                throw raise(PythonBuiltinClassType.OSError, e);
            }
        }

        private static long castToLong(CastToJavaLongLossyNode castLong, Object obj, long defaultVal) {
            try {
                return castLong.execute(obj);
            } catch (CannotCastException e) {
                return defaultVal;
            }
        }

        private SequenceStorageNodes.GetItemNode getGetRightItemNode() {
            if (getRightItemNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                getRightItemNode = insert(SequenceStorageNodes.GetItemNode.create());
            }
            return getRightItemNode;
        }
    }

    @Builtin(name = "flush", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class FlushNode extends PythonUnaryBuiltinNode {

        @Specialization
        static Object seek(@SuppressWarnings("unused") PMMap self) {
            return PNone.NONE;
        }

    }

}
