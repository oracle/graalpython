package com.oracle.graal.python.nodes.util;

import static com.oracle.graal.python.builtins.PythonBuiltinClassType.OSError;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.Channel;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.SeekableByteChannel;
import java.nio.channels.WritableByteChannel;
import java.util.function.Supplier;

import com.oracle.graal.python.builtins.objects.common.SequenceStorageNodes;
import com.oracle.graal.python.nodes.PNodeWithContext;
import com.oracle.graal.python.nodes.util.ChannelNodesFactory.ReadByteFromChannelNodeGen;
import com.oracle.graal.python.nodes.util.ChannelNodesFactory.ReadFromChannelNodeGen;
import com.oracle.graal.python.nodes.util.ChannelNodesFactory.WriteByteToChannelNodeGen;
import com.oracle.graal.python.nodes.util.ChannelNodesFactory.WriteToChannelNodeGen;
import com.oracle.graal.python.runtime.sequence.storage.ByteSequenceStorage;
import com.oracle.graal.python.runtime.sequence.storage.SequenceStorage;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Cached;
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

    abstract static class ReadFromChannelBaseNode extends PNodeWithContext {

        private final BranchProfile gotException = BranchProfile.create();

        @TruffleBoundary(allowInlining = true)
        protected static ByteBuffer allocate(int n) {
            return ByteBuffer.allocate(n);
        }

        @TruffleBoundary(transferToInterpreterOnException = false)
        protected static long availableSize(SeekableByteChannel channel) throws IOException {
            return channel.size() - channel.position();
        }

        @TruffleBoundary(allowInlining = true)
        protected static byte[] getByteBufferArray(ByteBuffer dst) {
            return dst.array();
        }

        @TruffleBoundary(allowInlining = true)
        protected int readIntoBuffer(ReadableByteChannel readableChannel, ByteBuffer dst) {
            try {
                return readableChannel.read(dst);
            } catch (IOException e) {
                gotException.enter();
                throw raise(OSError, e);
            }
        }
    }

    abstract static class WriteToChannelBaseNode extends PNodeWithContext {

        private final BranchProfile gotException = BranchProfile.create();

        @TruffleBoundary(allowInlining = true)
        protected static ByteBuffer allocate(int n) {
            return ByteBuffer.allocate(n);
        }

        @TruffleBoundary(transferToInterpreterOnException = false)
        protected static long availableSize(SeekableByteChannel channel) throws IOException {
            return channel.size() - channel.position();
        }

        @TruffleBoundary(allowInlining = true)
        protected static byte[] getByteBufferArray(ByteBuffer dst) {
            return dst.array();
        }

        @TruffleBoundary(allowInlining = true)
        protected int writeFromBuffer(WritableByteChannel writableChannel, ByteBuffer src) {
            try {
                return writableChannel.write(src);
            } catch (IOException e) {
                gotException.enter();
                throw raise(OSError, e);
            }
        }
    }

    public abstract static class ReadFromChannelNode extends ReadFromChannelBaseNode {
        public static final int MAX_READ = Integer.MAX_VALUE / 2;
        private final BranchProfile gotException = BranchProfile.create();

        public abstract ByteSequenceStorage execute(Channel channel, int size);

        @Specialization
        ByteSequenceStorage readSeekable(SeekableByteChannel channel, int size) {
            long availableSize;
            try {
                availableSize = availableSize(channel);
            } catch (IOException e) {
                gotException.enter();
                throw raise(OSError, e);
            }
            if (availableSize > MAX_READ) {
                availableSize = MAX_READ;
            }
            int sz = (int) Math.min(availableSize, size);
            return readReadable(channel, sz);
        }

        @Specialization
        ByteSequenceStorage readReadable(ReadableByteChannel channel, int size) {
            int sz = Math.min(size, MAX_READ);
            ByteBuffer dst = allocateBuffer(sz);
            int readSize = readIntoBuffer(channel, dst);
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
        ByteSequenceStorage readGeneric(Channel channel, int size) {
            if (channel instanceof SeekableByteChannel) {
                return readSeekable((SeekableByteChannel) channel, size);
            } else if (channel instanceof ReadableByteChannel) {
                return readReadable((ReadableByteChannel) channel, size);
            } else {
                throw raise(OSError, "file not opened for reading");
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
                        @Cached("createBinaryProfile()") ConditionProfile readProfile) {
            ByteBuffer buf = allocate(1);
            int read = readIntoBuffer(channel, buf);
            if (readProfile.profile(read != 1)) {
                return handleError(channel);
            }
            return get(buf);
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
                        @Cached("createBinaryProfile()") ConditionProfile readProfile) {
            ByteBuffer buf = allocate(1);
            put(b, buf);
            int read = writeFromBuffer(channel, buf);
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
        private final BranchProfile gotException = BranchProfile.create();

        public abstract int execute(Channel channel, SequenceStorage s, int len);

        @Specialization
        int writeSeekable(SeekableByteChannel channel, SequenceStorage s, int len) {
            long availableSize;
            try {
                availableSize = availableSize(channel);
            } catch (IOException e) {
                gotException.enter();
                throw raise(OSError, e);
            }
            if (availableSize > MAX_WRITE) {
                availableSize = MAX_WRITE;
            }
            int sz = (int) Math.min(availableSize, len);
            return writeWritable(channel, s, sz);

        }

        @Specialization
        int writeWritable(WritableByteChannel channel, SequenceStorage s, int len) {
            ByteBuffer src = allocateBuffer(getBytes(s));
            if (src.remaining() > len) {
                src.limit(len);
            }
            return writeFromBuffer(channel, src);
        }

        @Specialization
        int writeGeneric(Channel channel, SequenceStorage s, int len) {
            if (channel instanceof SeekableByteChannel) {
                return writeSeekable((SeekableByteChannel) channel, s, len);
            } else if (channel instanceof ReadableByteChannel) {
                return writeWritable((WritableByteChannel) channel, s, len);
            } else {
                throw raise(OSError, "file not opened for reading");
            }
        }

        @TruffleBoundary(allowInlining = true)
        private static ByteBuffer allocateBuffer(byte[] data) {
            return ByteBuffer.wrap(data);
        }

        private byte[] getBytes(SequenceStorage s) {
            if (toByteArrayNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                toByteArrayNode = insert(SequenceStorageNodes.ToByteArrayNode.create(true));
            }
            return toByteArrayNode.execute(s);
        }

        public static WriteToChannelNode create() {
            return WriteToChannelNodeGen.create();
        }
    }

}
