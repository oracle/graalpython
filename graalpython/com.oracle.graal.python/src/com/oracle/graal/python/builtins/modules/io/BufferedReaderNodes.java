/*
 * Copyright (c) 2020, 2021, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.builtins.modules.io;

import static com.oracle.graal.python.builtins.modules.io.BufferedIOUtil.append;
import static com.oracle.graal.python.builtins.modules.io.BufferedIOUtil.createStream;
import static com.oracle.graal.python.builtins.modules.io.BufferedIOUtil.isValidReadBuffer;
import static com.oracle.graal.python.builtins.modules.io.BufferedIOUtil.minusLastBlock;
import static com.oracle.graal.python.builtins.modules.io.BufferedIOUtil.safeDowncast;
import static com.oracle.graal.python.builtins.modules.io.BufferedIOUtil.toByteArray;
import static com.oracle.graal.python.nodes.ErrorMessages.IO_S_INVALID_LENGTH;
import static com.oracle.graal.python.nodes.ErrorMessages.IO_S_SHOULD_RETURN_BYTES;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.OSError;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.TypeError;

import java.io.ByteArrayOutputStream;
import java.util.Arrays;

import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.bytes.BytesNodes;
import com.oracle.graal.python.builtins.objects.common.SequenceStorageNodes;
import com.oracle.graal.python.builtins.objects.object.PythonObjectLibrary;
import com.oracle.graal.python.nodes.PNodeWithContext;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.attributes.LookupAttributeInMRONode;
import com.oracle.graal.python.nodes.call.special.CallUnaryMethodNode;
import com.oracle.graal.python.util.PythonUtils;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.profiles.ConditionProfile;

public class BufferedReaderNodes {

    /**
     * implementation of cpython/Modules/_io/bufferedio.c:_bufferedreader_raw_read
     */
    abstract static class RawReadNode extends PNodeWithContext {

        public abstract byte[] execute(VirtualFrame frame, PBuffered self, int len);

        // This might be more efficient
        @Specialization(limit = "2")
        static byte[] fastWay(VirtualFrame frame, PBuffered self, int len,
                        @Cached PRaiseNode raise,
                        @Cached BytesNodes.ToBytesNode toBytes,
                        @CachedLibrary("self.getRaw()") PythonObjectLibrary libRaw,
                        @Cached ConditionProfile osError) {
            Object res = libRaw.lookupAndCallRegularMethod(self.getRaw(), frame, "read", len);
            byte[] bytes = toBytes.execute(res);
            int n = bytes.length;
            if (osError.profile(n > len)) {
                throw raise.raise(OSError, IO_S_INVALID_LENGTH, "readinto()", n, len);
            }
            if (n > 0 && self.getAbsPos() != -1) {
                self.incAbsPos(n);
            }
            if (n == 0) {
                return PythonUtils.EMPTY_BYTE_ARRAY;
            }
            return bytes;
        }

        /*-
        // This is the spec way
        @Specialization(limit = "2")
        static byte[] bufferedreaderRawRead(VirtualFrame frame, PBuffered self, int len,
                        @Cached PRaiseNode raise,
                        @Cached BytesNodes.ToBytesNode toBytes,
                        @Cached PythonObjectFactory factory,
                        @CachedLibrary("self.getRaw()") PythonObjectLibrary libRaw,
                        @CachedLibrary(limit = "1") PythonObjectLibrary asSize,
                        @Cached ConditionProfile osError) {
            PByteArray memobj = factory.createByteArray(new byte[len]);
            Object res = libRaw.lookupAndCallRegularMethod(self.getRaw(), frame, "readinto", memobj);
            int n = asSize.asSize(res, ValueError);
            if (osError.profile(n < 0 || n > len)) {
                throw raise.raise(OSError, "raw readinto() returned invalid length %d (should have been between 0 and %d)", n, len);
            }
            if (n > 0 && self.getAbsPos() != -1) {
                self.incAbsPos(n);
            }
            if (n == 0) {
                return PythonUtils.EMPTY_BYTE_ARRAY;
            }
            byte[] bytes = toBytes.execute(memobj);
            if (n < len) {
                return Arrays.copyOf(bytes, n);
            }
            return bytes;
        }
        */
    }

    /**
     * implementation of cpython/Modules/_io/bufferedio.c:_bufferedreader_fill_buffer
     */
    abstract static class FillBufferNode extends PNodeWithContext {

        public abstract int execute(VirtualFrame frame, PBuffered self);

        @Specialization
        static int bufferedreaderFillBuffer(VirtualFrame frame, PBuffered self,
                        @Cached RawReadNode rawReadNode) {
            int start;
            if (isValidReadBuffer(self)) {
                start = self.getReadEnd();
            } else {
                start = 0;
            }
            int len = self.getBufferSize() - start;
            byte[] fill = rawReadNode.execute(frame, self, len);
            int n = fill.length;
            if (n == 0) {
                return n;
            }
            PythonUtils.arraycopy(fill, 0, self.getBuffer(), start, n);
            self.setReadEnd(start + n);
            self.setRawPos(start + n);
            return n;
        }
    }

    abstract static class ReadNode extends PNodeWithContext {

        public abstract byte[] execute(VirtualFrame frame, PBuffered self, int size);

        protected static boolean isReadAll(int size) {
            return size == -1;
        }

        protected static boolean isReadFast(PBuffered self, int size) {
            return size <= safeDowncast(self);
        }

        @SuppressWarnings("unused")
        @Specialization(guards = {"size == 0"})
        public static byte[] empty(PBuffered self, int size) {
            return PythonUtils.EMPTY_BYTE_ARRAY;
        }

        /*
         * Read n bytes from the buffer if it can, otherwise return None. This function is simple
         * enough that it can run unlocked.
         */
        /**
         * implementation of cpython/Modules/_io/bufferedio.c:_bufferedreader_read_fast
         */
        @Specialization(guards = {"size > 0", "isReadFast(self, size)"})
        public static byte[] bufferedreaderReadFast(PBuffered self, int size) {
            /* Fast path: the data to read is fully buffered. */
            byte[] res = Arrays.copyOfRange(self.getBuffer(), self.getPos(), self.getPos() + size);
            self.incPos(size);
            return res;
        }

        /**
         * implementation of cpython/Modules/_io/bufferedio.c:_bufferedreader_read_generic
         */
        @Specialization(guards = {"size > 0", "!isReadFast(self, size)"})
        static byte[] bufferedreaderReadGeneric(VirtualFrame frame, PBuffered self, int size,
                        @Cached RawReadNode rawReadNode,
                        @Cached FillBufferNode fillBufferNode,
                        @Cached BufferedIONodes.FlushAndRewindUnlockedNode flushAndRewindUnlockedNode) {
            int currentSize = safeDowncast(self);
            /*-
                (mq) this is not needed because of guards
                if (n <= currentSize) {
                    return bufferedreaderReadFast(self, n);
                }
             */
            byte[] res = new byte[size];
            int remaining = size;
            int written = 0;
            if (currentSize > 0) {
                // memcpy(out, self.buffer + self.pos, currentSize);
                PythonUtils.arraycopy(self.getBuffer(), self.getPos(), res, 0, currentSize);
                remaining -= currentSize;
                written += currentSize;
                self.incPos(currentSize);
            }
            /* Flush the write buffer if necessary */
            if (self.isWritable()) {
                flushAndRewindUnlockedNode.execute(frame, self);
            }
            self.resetRead(); // _bufferedreader_reset_buf
            while (remaining > 0) {
                /*- We want to read a whole block at the end into buffer.
                If we had readv() we could do this in one pass. */
                int r = minusLastBlock(self, remaining);
                if (r == 0) {
                    break;
                }
                byte[] fill = rawReadNode.execute(frame, self, r);
                r = fill.length;
                PythonUtils.arraycopy(fill, 0, res, written, r);
                if (r == 0) {
                    /* EOF occurred */
                    // XXX: (mq) do we need to deal with read() block?
                    return Arrays.copyOf(res, written);
                }
                remaining -= r;
                written += r;
            }
            assert remaining <= self.getBufferSize();
            self.setPos(0);
            self.setRawPos(0);
            self.setReadEnd(0);
            /*- NOTE: when the read is satisfied, we avoid issuing any additional
               reads, which could block indefinitely (e.g. on a socket).
               See issue #9550. */
            while (remaining > 0 && self.getReadEnd() < self.getBufferSize()) {
                int r = fillBufferNode.execute(frame, self);
                if (r == 0) {
                    /* EOF occurred */
                    // XXX: (mq) do we need to deal with read() block?
                    return Arrays.copyOf(res, written);
                }
                if (remaining > r) {
                    // memcpy(out + written, self.buffer + self.pos, r);
                    PythonUtils.arraycopy(self.getBuffer(), self.getPos(), res, written, r);
                    written += r;
                    self.incPos(r);
                    remaining -= r;
                } else { // (mq) `if (remaining > 0)` always true
                    // memcpy(out + written, self.buffer + self.pos, remaining);
                    PythonUtils.arraycopy(self.getBuffer(), self.getPos(), res, written, remaining);
                    written += remaining;
                    self.incPos(remaining);
                    remaining = 0;
                }
                if (remaining == 0) {
                    break;
                }
            }

            return res;
        }

        public static final String READALL = "readall";

        /**
         * implementation of cpython/Modules/_io/bufferedio.c:_bufferedreader_read_all
         */
        @Specialization(guards = "isReadAll(size)", limit = "2")
        byte[] bufferedreaderReadAll(VirtualFrame frame, PBuffered self, @SuppressWarnings("unused") int size,
                        @Cached PRaiseNode raiseNode,
                        @Cached BufferedIONodes.FlushAndRewindUnlockedNode flushAndRewindUnlockedNode,
                        @Cached("create(READALL)") LookupAttributeInMRONode readallAttr,
                        @Cached ConditionProfile hasReadallProfile,
                        @Cached CallUnaryMethodNode dispatchGetattribute,
                        @CachedLibrary(limit = "2") PythonObjectLibrary getBytes,
                        @CachedLibrary("self.getRaw()") PythonObjectLibrary libRaw) {
            byte[] data = null;
            /* First copy what we have in the current buffer. */
            int currentSize = safeDowncast(self);
            if (currentSize != 0) {
                data = Arrays.copyOfRange(self.getBuffer(), self.getPos(), self.getPos() + currentSize);
                self.incPos(currentSize);
            }

            /* We're going past the buffer's bounds, flush it */
            if (self.isWritable()) {
                flushAndRewindUnlockedNode.execute(frame, self);
            }

            self.resetRead(); // _bufferedreader_reset_buf

            Object clazz = libRaw.getLazyPythonClass(self.getRaw());
            Object readall = readallAttr.execute(clazz);
            if (hasReadallProfile.profile(readall != PNone.NO_VALUE)) {
                Object tmp = dispatchGetattribute.executeObject(frame, readall, self.getRaw());
                if (tmp == PNone.NONE) {
                    return data;
                } else if (getBytes.isBuffer(tmp)) {
                    try {
                        byte[] bytes = getBytes.getBufferBytes(tmp);
                        if (currentSize == 0) {
                            return bytes;
                        } else {
                            byte[] res = new byte[data.length + bytes.length];
                            PythonUtils.arraycopy(data, 0, res, 0, data.length);
                            PythonUtils.arraycopy(bytes, 0, res, data.length, bytes.length);
                            return res;
                        }
                    } catch (UnsupportedMessageException e) {
                        throw CompilerDirectives.shouldNotReachHere(e);
                    }
                } else {
                    throw raiseNode.raise(TypeError, IO_S_SHOULD_RETURN_BYTES, "readall()");
                }
            }

            ByteArrayOutputStream chunks = createStream();

            while (true) {
                if (data != null) {
                    append(chunks, data, data.length);
                    data = null;
                }

                /* Read until EOF or until read() would block. */
                Object r = libRaw.lookupAndCallRegularMethod(self.getRaw(), frame, "read");
                if (r != PNone.NONE && !getBytes.isBuffer(r)) {
                    throw raiseNode.raise(TypeError, IO_S_SHOULD_RETURN_BYTES, "read()");
                }
                int len = 0;
                if (r != PNone.NONE) {
                    try {
                        data = getBytes.getBufferBytes(r);
                        len = getBytes.getBufferLength(r);
                    } catch (UnsupportedMessageException e) {
                        throw CompilerDirectives.shouldNotReachHere(e);
                    }
                }
                if (r == PNone.NONE || len == 0) {
                    if (currentSize == 0) {
                        return data;
                    } else {
                        return toByteArray(chunks);
                    }
                }
                currentSize += len;
                if (self.getAbsPos() != -1) {
                    self.incAbsPos(len);
                }
            }
        }
    }

    /**
     * implementation of cpython/Modules/_io/bufferedio.c:_bufferedreader_peek_unlocked
     */
    abstract static class PeekUnlockedNode extends PNodeWithContext {

        public abstract byte[] execute(VirtualFrame frame, PBuffered self);

        @Specialization
        byte[] bufferedreaderPeekUnlocked(VirtualFrame frame, PBuffered self,
                        @Cached FillBufferNode fillBufferNode) {
            int have = safeDowncast(self);
            /*-
             * Constraints:
             * 1. we don't want to advance the file position.
             * 2. we don't want to lose block alignment, so we can't shift the buffer to make some place.
             * Therefore, we either return `have` bytes (if > 0), or a full buffer.
             */
            if (have > 0) {
                return Arrays.copyOfRange(self.getBuffer(), self.getPos(), self.getPos() + have);
            }

            /* Fill the buffer from the raw stream, and copy it to the result. */
            self.resetRead(); // _bufferedreader_reset_buf
            int r = fillBufferNode.execute(frame, self);
            self.setPos(0);
            return Arrays.copyOf(self.getBuffer(), r);
        }

    }

    abstract static class ReadintoNode extends PNodeWithContext {

        public abstract int execute(VirtualFrame frame, PBuffered self, Object buf, int bufLen, boolean isReadInto1);

        /**
         * implementation of cpython/Modules/_io/bufferedio.c:_buffered_readinto_generic
         */
        @Specialization
        protected int bufferedReadintoGeneric(VirtualFrame frame, PBuffered self, Object buf, int bufLen, boolean isReadInto1,
                        @Cached BufferedIONodes.FlushAndRewindUnlockedNode flushAndRewindUnlockedNode,
                        @Cached RawReadNode rawReadNode,
                        @Cached FillBufferNode fillBufferNode,
                        @Cached SequenceStorageNodes.BytesMemcpyNode memcpyNode) {
            int written = 0;
            int n = safeDowncast(self);
            if (n > 0) {
                if (n >= bufLen) {
                    // memcpy(buffer, self.buffer + self.pos, buffer.length);
                    memcpyNode.execute(frame, buf, 0, self.getBuffer(), self.getPos(), bufLen);
                    self.incPos(bufLen);
                    return bufLen;
                }
                // memcpy(buffer, self.buffer + self.pos, n);
                memcpyNode.execute(frame, buf, 0, self.getBuffer(), self.getPos(), n);
                self.incPos(n);
                written = n;
            }

            if (self.isWritable()) {
                flushAndRewindUnlockedNode.execute(frame, self);
            }

            self.resetRead(); // _bufferedreader_reset_buf
            self.setPos(0);

            for (int remaining = bufLen - written; remaining > 0; written += n, remaining -= n) {
                /*-
                 If remaining bytes is larger than internal buffer size, copy directly into
                 caller's buffer.
                 */
                if (remaining > self.getBufferSize()) {
                    byte[] fill = rawReadNode.execute(frame, self, remaining);
                    n = fill.length;
                    memcpyNode.execute(frame, buf, written, fill, 0, n);
                } else if (!(isReadInto1 && written != 0)) {
                    /*-
                    In readinto1 mode, we do not want to fill the internal buffer if we already have
                    some data to return
                    */
                    n = fillBufferNode.execute(frame, self);
                    if (n > 0) {
                        if (n > remaining) {
                            n = remaining;
                        }
                        // memcpy(buffer.buf + written, self.buffer + self.pos, n);
                        memcpyNode.execute(frame, buf, written, self.getBuffer(), self.getPos(), n);
                        self.incPos(n);
                        continue; /* short circuit */
                    }
                } else {
                    n = 0;
                }

                if (n == 0) {
                    break;
                }
                assert n > 0;
                /* At most one read in readinto1 mode */
                if (isReadInto1) {
                    written += n;
                    break;
                }
            }

            return written;
        }
    }
}
