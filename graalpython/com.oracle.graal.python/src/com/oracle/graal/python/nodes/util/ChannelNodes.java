package com.oracle.graal.python.nodes.util;

import static com.oracle.graal.python.builtins.PythonBuiltinClassType.OSError;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.Channel;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.SeekableByteChannel;

import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.nodes.PNodeWithContext;
import com.oracle.graal.python.nodes.util.ChannelNodesFactory.ReadByteFromChannelNodeGen;
import com.oracle.graal.python.nodes.util.ChannelNodesFactory.ReadFromChannelNodeGen;
import com.oracle.graal.python.runtime.sequence.storage.ByteSequenceStorage;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.profiles.BranchProfile;
import com.oracle.truffle.api.profiles.ConditionProfile;

public abstract class ChannelNodes {
    public abstract static class ReadErrorHandler extends PNodeWithContext {
        public abstract Object execute(Channel channel, int nrequested, int nread);
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

        public abstract int execute(Channel channel);

        @Specialization
        int readByte(ReadableByteChannel channel,
                        @Cached("createBinaryProfile()") ConditionProfile readProfile,
                        @Cached("create()") BranchProfile errorProfile) {
            ByteBuffer buf = allocate(1);
            int read = readIntoBuffer(channel, buf);
            if (readProfile.profile(read != 1)) {
                errorProfile.enter();
                throw raise(PythonBuiltinClassType.ValueError, "read byte out of range");
            }
            return get(buf);
        }

        @TruffleBoundary(allowInlining = true)
        private static int get(ByteBuffer buf) {
            return buf.get();
        }

        public static ReadByteFromChannelNode create() {
            return ReadByteFromChannelNodeGen.create();
        }
    }
}
