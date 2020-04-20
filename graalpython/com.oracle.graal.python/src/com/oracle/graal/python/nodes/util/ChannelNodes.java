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
package com.oracle.graal.python.nodes.util;

import static com.oracle.graal.python.builtins.PythonBuiltinClassType.OSError;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.Channel;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.SeekableByteChannel;
import java.nio.channels.WritableByteChannel;
import com.oracle.graal.python.util.Supplier;

import com.oracle.graal.python.builtins.objects.common.SequenceStorageNodes;
import com.oracle.graal.python.builtins.objects.common.SequenceStorageNodesFactory.ToByteArrayNodeGen;
import com.oracle.graal.python.nodes.PNodeWithContext;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.util.ChannelNodesFactory.ReadByteFromChannelNodeGen;
import com.oracle.graal.python.nodes.util.ChannelNodesFactory.ReadFromChannelNodeGen;
import com.oracle.graal.python.nodes.util.ChannelNodesFactory.WriteByteToChannelNodeGen;
import com.oracle.graal.python.nodes.util.ChannelNodesFactory.WriteToChannelNodeGen;
import com.oracle.graal.python.runtime.sequence.storage.ByteSequenceStorage;
import com.oracle.graal.python.runtime.sequence.storage.SequenceStorage;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.profiles.BranchProfile;
import com.oracle.truffle.api.profiles.ConditionProfile;

public abstract class ChannelNodes {
    public abstract static class ReadErrorHandler extends PNodeWithContext {
        public abstract Object execute(Channel channel, int nrequested, int nread);
    }

    public abstract static class ReadByteErrorHandler extends PNodeWithContext {
        public abstract int execute(Channel channel);
    }

    public abstract static class WriteByteErrorHandler extends PNodeWithContext {
        public abstract void execute(Channel channel, byte b);
    }

    protected interface ChannelBaseNode {
        @TruffleBoundary(allowInlining = true)
        default ByteBuffer allocate(int n) {
            return ByteBuffer.allocate(n);
        }

        @TruffleBoundary(transferToInterpreterOnException = false)
        static long availableSize(SeekableByteChannel channel) throws IOException {
            return channel.size() - channel.position();
        }
    }

    abstract static class ReadFromChannelBaseNode extends PNodeWithContext implements ChannelBaseNode {

        @TruffleBoundary(allowInlining = true)
        protected static byte[] getByteBufferArray(ByteBuffer dst) {
            return dst.array();
        }

        protected static int readIntoBuffer(ReadableByteChannel readableChannel, ByteBuffer dst, BranchProfile gotException, PRaiseNode raise) {
            try {
                return read(readableChannel, dst);
            } catch (IOException e) {
                gotException.enter();
                throw raise.raise(OSError, e);
            }
        }

        @TruffleBoundary(allowInlining = true, transferToInterpreterOnException = false)
        private static int read(ReadableByteChannel readableChannel, ByteBuffer dst) throws IOException {
            return readableChannel.read(dst);
        }
    }

    abstract static class WriteToChannelBaseNode extends PNodeWithContext implements ChannelBaseNode {

        @TruffleBoundary(allowInlining = true)
        protected static byte[] getByteBufferArray(ByteBuffer dst) {
            return dst.array();
        }

        protected static int writeFromBuffer(WritableByteChannel writableChannel, ByteBuffer src, BranchProfile gotException, PRaiseNode raise) {
            try {
                return write(writableChannel, src);
            } catch (IOException e) {
                gotException.enter();
                throw raise.raise(OSError, e);
            }
        }

        @TruffleBoundary(allowInlining = true, transferToInterpreterOnException = false)
        private static int write(WritableByteChannel writableChannel, ByteBuffer src) throws IOException {
            return writableChannel.write(src);
        }
    }

    @GenerateUncached
    public abstract static class ReadFromChannelNode extends ReadFromChannelBaseNode {
        public static final int MAX_READ = Integer.MAX_VALUE / 2;

        public abstract ByteSequenceStorage execute(Channel channel, int size);

        @Specialization
        static ByteSequenceStorage readSeekable(SeekableByteChannel channel, int size,
                        @Shared("gotException") @Cached BranchProfile gotException,
                        @Shared("raiseNode") @Cached PRaiseNode raiseNode) {
            long availableSize;
            try {
                availableSize = ChannelBaseNode.availableSize(channel);
            } catch (IOException e) {
                gotException.enter();
                throw raiseNode.raise(OSError, e);
            }
            if (availableSize > MAX_READ) {
                availableSize = MAX_READ;
            }
            int sz = (int) Math.min(availableSize, size);
            return readReadable(channel, sz, gotException, raiseNode);
        }

        @Specialization
        static ByteSequenceStorage readReadable(ReadableByteChannel channel, int size,
                        @Shared("gotException") @Cached BranchProfile gotException,
                        @Shared("raiseNode") @Cached PRaiseNode raiseNode) {
            int sz = Math.min(size, MAX_READ);
            ByteBuffer dst = allocateBuffer(sz);
            int readSize = readIntoBuffer(channel, dst, gotException, raiseNode);
            byte[] array;
            if (readSize <= 0) {
                array = new byte[0];
                readSize = 0;
            } else {
                array = getByteBufferArray(dst);
            }
            ByteSequenceStorage byteSequenceStorage = new ByteSequenceStorage(array);
            byteSequenceStorage.setNewLength(readSize);
            return byteSequenceStorage;
        }

        @Specialization
        static ByteSequenceStorage readGeneric(Channel channel, int size,
                        @Shared("gotException") @Cached BranchProfile gotException,
                        @Shared("raiseNode") @Cached PRaiseNode raiseNode) {
            if (channel instanceof SeekableByteChannel) {
                return readSeekable((SeekableByteChannel) channel, size, gotException, raiseNode);
            } else if (channel instanceof ReadableByteChannel) {
                return readReadable((ReadableByteChannel) channel, size, gotException, raiseNode);
            } else {
                throw raiseNode.raise(OSError, "file not opened for reading");
            }
        }

        @TruffleBoundary(allowInlining = true)
        private static ByteBuffer allocateBuffer(int sz) {
            return ByteBuffer.allocate(sz);
        }

        public static ReadFromChannelNode create() {
            return ReadFromChannelNodeGen.create();
        }
    }

    public abstract static class ReadByteFromChannelNode extends ReadFromChannelBaseNode {

        @Child private ReadByteErrorHandler errorHandler;

        private final Supplier<ReadByteErrorHandler> errorHandlerFactory;

        public ReadByteFromChannelNode(Supplier<ReadByteErrorHandler> errorHandlerFactory) {
            this.errorHandlerFactory = errorHandlerFactory;
        }

        public abstract int execute(Channel channel);

        @Specialization
        int readByte(ReadableByteChannel channel,
                        @Cached BranchProfile gotException,
                        @Cached PRaiseNode raiseNode,
                        @Cached("createBinaryProfile()") ConditionProfile readProfile) {
            ByteBuffer buf = allocate(1);
            int read = readIntoBuffer(channel, buf, gotException, raiseNode);
            if (readProfile.profile(read != 1)) {
                return handleError(channel);
            }
            return get(buf) & 0xFF;
        }

        private int handleError(Channel channel) {
            if (errorHandler == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                errorHandler = insert(errorHandlerFactory.get());
            }
            return errorHandler.execute(channel);
        }

        @TruffleBoundary(allowInlining = true)
        private static int get(ByteBuffer buf) {
            buf.flip();
            return buf.get();
        }

        public static ReadByteFromChannelNode create(Supplier<ReadByteErrorHandler> errorHandlerFactory) {
            return ReadByteFromChannelNodeGen.create(errorHandlerFactory);
        }
    }

    public abstract static class WriteByteToChannelNode extends WriteToChannelBaseNode {

        @Child private WriteByteErrorHandler errorHandler;

        private final Supplier<WriteByteErrorHandler> errorHandlerFactory;

        public WriteByteToChannelNode(Supplier<WriteByteErrorHandler> errorHandlerFactory) {
            this.errorHandlerFactory = errorHandlerFactory;
        }

        public abstract void execute(Channel channel, byte b);

        @Specialization
        void readByte(WritableByteChannel channel, byte b,
                        @Cached BranchProfile gotException,
                        @Cached PRaiseNode raiseNode,
                        @Cached("createBinaryProfile()") ConditionProfile readProfile) {
            ByteBuffer buf = allocate(1);
            put(b, buf);
            int read = writeFromBuffer(channel, buf, gotException, raiseNode);
            if (readProfile.profile(read != 1)) {
                handleError(channel, b);
            }
        }

        @TruffleBoundary(allowInlining = true)
        private static void put(byte b, ByteBuffer buf) {
            buf.put(b);
            buf.flip();
        }

        private void handleError(Channel channel, byte b) {
            if (errorHandler == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                errorHandler = insert(errorHandlerFactory.get());
            }
            errorHandler.execute(channel, b);
        }

        public static WriteByteToChannelNode create(Supplier<WriteByteErrorHandler> errorHandlerFactory) {
            return WriteByteToChannelNodeGen.create(errorHandlerFactory);
        }
    }

    public abstract static class WriteToChannelNode extends WriteToChannelBaseNode {
        @Child private SequenceStorageNodes.ToByteArrayNode toByteArrayNode;

        public static final int MAX_WRITE = Integer.MAX_VALUE / 2;

        public abstract int execute(Channel channel, SequenceStorage s, int len);

        @Specialization
        int writeSeekable(SeekableByteChannel channel, SequenceStorage s, int len,
                        @Cached BranchProfile limitProfile,
                        @Cached("createBinaryProfile()") ConditionProfile maxSizeProfile,
                        @Shared("gotException") @Cached BranchProfile gotException,
                        @Shared("raiseNode") @Cached PRaiseNode raiseNode) {
            long availableSize;
            try {
                availableSize = ChannelBaseNode.availableSize(channel);
            } catch (IOException e) {
                gotException.enter();
                throw raiseNode.raise(OSError, e);
            }
            if (maxSizeProfile.profile(availableSize > MAX_WRITE)) {
                availableSize = MAX_WRITE;
            }
            int sz = (int) Math.min(availableSize, len);
            return writeWritable(channel, s, sz, gotException, raiseNode, limitProfile);
        }

        @Specialization
        int writeWritable(WritableByteChannel channel, SequenceStorage s, int len,
                        @Shared("gotException") @Cached BranchProfile gotException,
                        @Shared("raiseNode") @Cached PRaiseNode raiseNode,
                        @Cached BranchProfile limitProfile) {
            ByteBuffer src = allocateBuffer(getBytes(s));
            if (src.remaining() > len) {
                limitProfile.enter();
                src.limit(len);
            }
            return writeFromBuffer(channel, src, gotException, raiseNode);
        }

        @Specialization
        int writeGeneric(Channel channel, SequenceStorage s, int len,
                        @Cached BranchProfile limitProfile,
                        @Cached("createBinaryProfile()") ConditionProfile maxSizeProfile,
                        @Shared("gotException") @Cached BranchProfile gotException,
                        @Shared("raiseNode") @Cached PRaiseNode raiseNode) {
            if (channel instanceof SeekableByteChannel) {
                return writeSeekable((SeekableByteChannel) channel, s, len, limitProfile, maxSizeProfile, gotException, raiseNode);
            } else if (channel instanceof ReadableByteChannel) {
                return writeWritable((WritableByteChannel) channel, s, len, gotException, raiseNode, limitProfile);
            } else {
                throw raiseNode.raise(OSError, "file not opened for reading");
            }
        }

        @TruffleBoundary(allowInlining = true)
        private static ByteBuffer allocateBuffer(byte[] data) {
            return ByteBuffer.wrap(data);
        }

        private byte[] getBytes(SequenceStorage s) {
            if (toByteArrayNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                toByteArrayNode = insert(ToByteArrayNodeGen.create());
            }
            return toByteArrayNode.execute(s);
        }

        public static WriteToChannelNode create() {
            return WriteToChannelNodeGen.create();
        }
    }
}
