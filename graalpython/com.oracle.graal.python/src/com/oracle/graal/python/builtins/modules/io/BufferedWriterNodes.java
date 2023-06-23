/*
 * Copyright (c) 2020, 2023, Oracle and/or its affiliates. All rights reserved.
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

import static com.oracle.graal.python.builtins.PythonBuiltinClassType.BlockingIOError;
import static com.oracle.graal.python.builtins.modules.io.BufferedIOUtil.SEEK_CUR;
import static com.oracle.graal.python.builtins.modules.io.BufferedIOUtil.isValidReadBuffer;
import static com.oracle.graal.python.builtins.modules.io.BufferedIOUtil.isValidWriteBuffer;
import static com.oracle.graal.python.builtins.modules.io.BufferedIOUtil.rawOffset;
import static com.oracle.graal.python.builtins.modules.io.IONodes.T_WRITE;
import static com.oracle.graal.python.nodes.ErrorMessages.IO_S_INVALID_LENGTH;
import static com.oracle.graal.python.nodes.ErrorMessages.WRITE_COULD_NOT_COMPLETE_WITHOUT_BLOCKING;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.OSError;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.ValueError;

import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.buffer.PythonBufferAccessLibrary;
import com.oracle.graal.python.builtins.objects.bytes.PBytes;
import com.oracle.graal.python.builtins.objects.common.SequenceStorageNodes;
import com.oracle.graal.python.builtins.objects.exception.ExceptionNodes;
import com.oracle.graal.python.builtins.objects.tuple.PTuple;
import com.oracle.graal.python.lib.PyNumberAsSizeNode;
import com.oracle.graal.python.lib.PyObjectCallMethodObjArgs;
import com.oracle.graal.python.nodes.PNodeWithContext;
import com.oracle.graal.python.nodes.PNodeWithRaise;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.object.BuiltinClassProfiles.IsBuiltinObjectProfile;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.graal.python.runtime.object.PythonObjectFactory;
import com.oracle.graal.python.util.PythonUtils;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateCached;
import com.oracle.truffle.api.dsl.GenerateInline;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.nodes.Node;

public class BufferedWriterNodes {

    private static void adjustPosition(PBuffered self, int newPos) {
        self.setPos(newPos);
        if (isValidReadBuffer(self) && self.getReadEnd() < newPos) {
            self.setReadEnd(newPos);
        }
    }

    abstract static class WriteNode extends PNodeWithRaise {

        public abstract int execute(VirtualFrame frame, Node inliningTarget, PBuffered self, Object buffer);

        /**
         * implementation of cpython/Modules/_io/bufferedio.c:_io_BufferedWriter_write_impl
         */
        @Specialization
        static int bufferedWriterWrite(VirtualFrame frame, @SuppressWarnings("unused") Node ignored, PBuffered self, Object buffer,
                        @Bind("this") Node inliningTarget,
                        @CachedLibrary(limit = "3") PythonBufferAccessLibrary bufferLib,
                        @Cached AbstractBufferedIOBuiltins.LazyRaiseBlockingIOError raiseBlockingIOError,
                        @Cached IsBuiltinObjectProfile isBuiltinClassProfile,
                        @Cached BufferedIONodes.RawSeekNode rawSeekNode,
                        @Cached RawWriteNode rawWriteNode,
                        @Cached FlushUnlockedNode flushUnlockedNode,
                        @Cached ExceptionNodes.GetArgsNode getArgsNode,
                        @Cached SequenceStorageNodes.GetItemScalarNode getItemScalarNode) {
            final int bufLen = bufferLib.getBufferLength(buffer);

            // TODO: check ENTER_BUFFERED(self)

            /* Fast path: the data to write can be fully buffered. */
            if (!isValidReadBuffer(self) && !isValidWriteBuffer(self)) {
                self.setPos(0);
                self.setRawPos(0);
            }
            int avail = self.getBufferSize() - self.getPos();
            if (bufLen <= avail) {
                // memcpy(self->buffer + self.getPos(), buffer, buffer.length);
                bufferLib.readIntoByteArray(buffer, 0, self.getBuffer(), self.getPos(), bufLen);
                if (!isValidWriteBuffer(self) || self.getWritePos() > self.getPos()) {
                    self.setWritePos(self.getPos());
                }
                adjustPosition(self, self.getPos() + bufLen);
                if (self.getPos() > self.getWriteEnd()) {
                    self.setWriteEnd(self.getPos());
                }
                return bufLen;
            }

            /* First write the current buffer */
            try {
                flushUnlockedNode.execute(frame, inliningTarget, self);
            } catch (PException e) {
                e.expect(inliningTarget, BlockingIOError, isBuiltinClassProfile);
                if (self.isReadable()) {
                    self.resetRead();
                }
                /* Make some place by shifting the buffer. */
                assert isValidWriteBuffer(self);
                int n = self.getWriteEnd() - self.getWritePos();
                byte[] from = PythonUtils.arrayCopyOfRange(self.getBuffer(), self.getWritePos(), self.getWritePos() + n);
                PythonUtils.arraycopy(from, 0, self.getBuffer(), 0, n);
                self.setWriteEnd(n);
                self.setRawPos(self.getRawPos() - self.getWritePos());
                self.setPos(self.getPos() - self.getWritePos());
                self.setWritePos(0);
                avail = self.getBufferSize() - self.getWriteEnd();
                if (bufLen <= avail) {
                    /* Everything can be buffered */
                    bufferLib.readIntoByteArray(buffer, 0, self.getBuffer(), self.getWriteEnd(), bufLen);
                    self.incWriteEnd(bufLen);
                    self.incPos(bufLen);
                    return bufLen;
                }
                bufferLib.readIntoByteArray(buffer, 0, self.getBuffer(), self.getWriteEnd(), avail);
                self.incWriteEnd(avail);
                self.incPos(avail);

                Object pythonException = e.getUnreifiedException();
                PTuple args = getArgsNode.execute(inliningTarget, pythonException);
                Object errno = getItemScalarNode.execute(args.getSequenceStorage(), 0);
                throw raiseBlockingIOError.get(inliningTarget).raise(errno, WRITE_COULD_NOT_COMPLETE_WITHOUT_BLOCKING, avail);
            }
            /*
             * Adjust the raw stream position if it is away from the logical stream position. This
             * happens if the read buffer has been filled but not modified (and therefore
             * _bufferedwriter_flush_unlocked() didn't rewind the raw stream by itself). Fixes issue
             * #6629.
             */
            long offset = rawOffset(self);
            if (offset != 0) {
                rawSeekNode.execute(frame, inliningTarget, self, -offset, 1);
                self.setRawPos(self.getRawPos() - offset);
            }

            /* Then write buf itself. At this point the buffer has been emptied. */
            int remaining = bufLen;
            int written = 0;
            while (remaining > self.getBufferSize()) {
                // TODO use memoryview
                byte[] buf = new byte[bufLen];
                bufferLib.readIntoByteArray(buffer, written, buf, 0, bufLen - written);
                int n = rawWriteNode.execute(frame, inliningTarget, self, buf, bufLen - written);
                if (n == -2) {
                    if (remaining > self.getBufferSize()) {
                        bufferLib.readIntoByteArray(buffer, written, self.getBuffer(), 0, self.getBufferSize());
                        self.setRawPos(0);
                        adjustPosition(self, self.getBufferSize());
                        self.setWriteEnd(self.getBufferSize());
                        written += self.getBufferSize();
                        throw raiseBlockingIOError.get(inliningTarget).raiseEWOULDBLOCK(WRITE_COULD_NOT_COMPLETE_WITHOUT_BLOCKING, written);
                    }
                    break;
                }
                written += n;
                remaining -= n;
            }
            if (self.isReadable()) {
                self.resetRead(); // _bufferedreader_reset_buf
            }
            if (remaining > 0) {
                // memcpy(self->buffer, buffer + written, remaining);
                bufferLib.readIntoByteArray(buffer, written, self.getBuffer(), 0, remaining);
                written += remaining;
            }
            self.setWritePos(0);
            /* TODO: sanity check (remaining >= 0) */
            self.setWriteEnd(remaining);
            adjustPosition(self, remaining);
            self.setRawPos(0);

            // TODO: LEAVE_BUFFERED(self)
            return written;
        }
    }

    @GenerateInline
    @GenerateCached(false)
    abstract static class RawWriteNode extends PNodeWithContext {

        public abstract int execute(VirtualFrame frame, Node inliningTarget, PBuffered self, byte[] buf, int len);

        /**
         * implementation of cpython/Modules/_io/bufferedio.c:_bufferedwriter_raw_write
         */
        @Specialization
        static int bufferedwriterRawWrite(VirtualFrame frame, Node inliningTarget, PBuffered self, byte[] buf, int len,
                        @Cached(inline = false) PythonObjectFactory factory,
                        @Cached(inline = false) PyObjectCallMethodObjArgs callMethod,
                        @Cached(inline = false) PyNumberAsSizeNode asSizeNode,
                        @Cached PRaiseNode.Lazy lazyRaiseNode) {
            PBytes memobj = factory.createBytes(buf, len);
            Object res = callMethod.execute(frame, self.getRaw(), T_WRITE, memobj);
            if (res == PNone.NONE) {
                /*
                 * Non-blocking stream would have blocked. Special return code!
                 */
                return -2;
            }
            int n = asSizeNode.executeExact(frame, res, ValueError);
            if (n < 0 || n > len) {
                throw lazyRaiseNode.get(inliningTarget).raise(OSError, IO_S_INVALID_LENGTH, "write()", n, len);
            }
            if (n > 0 && self.getAbsPos() != -1) {
                self.incAbsPos(n);
            }
            return n;
        }
    }

    @GenerateInline
    @GenerateCached(false)
    abstract static class FlushUnlockedNode extends PNodeWithContext {

        public abstract void execute(VirtualFrame frame, Node inliningTarget, PBuffered self);

        /**
         * implementation of cpython/Modules/_io/bufferedio.c:_bufferedwriter_flush_unlocked
         */
        @Specialization
        protected static void bufferedwriterFlushUnlocked(VirtualFrame frame, Node inliningTarget, PBuffered self,
                        @Cached AbstractBufferedIOBuiltins.LazyRaiseBlockingIOError raiseBlockingIOError,
                        @Cached RawWriteNode rawWriteNode,
                        @Cached BufferedIONodes.RawSeekNode rawSeekNode) {
            if (!isValidWriteBuffer(self) || self.getWritePos() == self.getWriteEnd()) {
                self.resetWrite(); // _bufferedwriter_reset_buf
                return;
            }
            /* First, rewind */
            long rewind = rawOffset(self) + (self.getPos() - self.getWritePos());
            if (rewind != 0) {
                rawSeekNode.execute(frame, inliningTarget, self, -rewind, SEEK_CUR);
                self.incRawPos(-rewind);
            }
            while (self.getWritePos() < self.getWriteEnd()) {
                byte[] buf = PythonUtils.arrayCopyOfRange(self.getBuffer(), self.getWritePos(), self.getWriteEnd());
                int n = rawWriteNode.execute(frame, inliningTarget, self, buf, buf.length);
                if (n == -2) {
                    throw raiseBlockingIOError.get(inliningTarget).raiseEAGAIN(WRITE_COULD_NOT_COMPLETE_WITHOUT_BLOCKING, 0);
                }
                self.incWritePos(n);
                self.setRawPos(self.getWritePos());
                /*- Partial writes can return successfully when interrupted by a
                   signal (see write(2)).  We must run signal handlers before
                   blocking another time, possibly indefinitely. */
                /*-
                (mq) Singles will be thrown elsewhere,
                 so we might not need to check it.
                 Though, mutil-threading might require it.
                if (PyErr_CheckSignals() < 0)
                    return null;
                */
            }

            /*-
               This ensures that after return from this function,
               VALID_WRITE_BUFFER(self) returns false.
            
               This is a required condition because when a tell() is called
               after flushing and if VALID_READ_BUFFER(self) is false, we need
               VALID_WRITE_BUFFER(self) to be false to have
               RAW_OFFSET(self) == 0.
            
               Issue: https://bugs.python.org/issue32228
            */
            self.resetWrite(); // _bufferedwriter_reset_buf
        }

    }
}
