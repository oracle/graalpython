/*
 * Copyright (c) 2020, 2022, Oracle and/or its affiliates. All rights reserved.
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

import static com.oracle.graal.python.builtins.PythonBuiltinClassType.OverflowError;
import static com.oracle.graal.python.builtins.modules.io.BufferedIOUtil.SEEK_CUR;
import static com.oracle.graal.python.builtins.modules.io.BufferedIOUtil.SEEK_SET;
import static com.oracle.graal.python.builtins.modules.io.BufferedIOUtil.rawOffset;
import static com.oracle.graal.python.builtins.modules.io.BufferedIOUtil.readahead;
import static com.oracle.graal.python.builtins.modules.io.IONodes.CLOSED;
import static com.oracle.graal.python.builtins.modules.io.IONodes.READABLE;
import static com.oracle.graal.python.builtins.modules.io.IONodes.SEEK;
import static com.oracle.graal.python.builtins.modules.io.IONodes.SEEKABLE;
import static com.oracle.graal.python.builtins.modules.io.IONodes.TELL;
import static com.oracle.graal.python.builtins.modules.io.IONodes.WRITABLE;
import static com.oracle.graal.python.nodes.ErrorMessages.CANNOT_FIT_P_IN_OFFSET_SIZE;
import static com.oracle.graal.python.nodes.ErrorMessages.FILE_OR_STREAM_IS_NOT_SEEKABLE;
import static com.oracle.graal.python.nodes.ErrorMessages.IO_STREAM_INVALID_POS;
import static com.oracle.graal.python.nodes.ErrorMessages.REENTRANT_CALL_INSIDE_P;
import static com.oracle.graal.python.nodes.ErrorMessages.SHUTDOWN_POSSIBLY_DUE_TO_DAEMON_THREADS;
import static com.oracle.graal.python.nodes.ErrorMessages.S_OF_CLOSED_FILE;
import static com.oracle.graal.python.nodes.ErrorMessages.S_TO_CLOSED_FILE;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.IOUnsupportedOperation;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.OSError;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.RuntimeError;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.SystemError;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.ValueError;

import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.modules.ThreadModuleBuiltins;
import com.oracle.graal.python.lib.PyNumberIndexNode;
import com.oracle.graal.python.lib.PyObjectCallMethodObjArgs;
import com.oracle.graal.python.lib.PyObjectGetAttr;
import com.oracle.graal.python.lib.PyObjectIsTrueNode;
import com.oracle.graal.python.nodes.PGuards;
import com.oracle.graal.python.nodes.PNodeWithContext;
import com.oracle.graal.python.nodes.PNodeWithRaise;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.object.IsBuiltinClassProfile;
import com.oracle.graal.python.nodes.truffle.PythonArithmeticTypes;
import com.oracle.graal.python.nodes.util.CannotCastException;
import com.oracle.graal.python.nodes.util.CastToJavaLongExactNode;
import com.oracle.graal.python.runtime.GilNode;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.dsl.TypeSystemReference;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.profiles.ConditionProfile;

public class BufferedIONodes {

    abstract static class CheckIsClosedNode extends PNodeWithContext {

        private final String method;
        private final String messageFmt;

        public CheckIsClosedNode(String method) {
            this.method = method;
            this.messageFmt = IONodes.WRITE.equals(method) ? S_TO_CLOSED_FILE : S_OF_CLOSED_FILE;
        }

        public abstract boolean execute(VirtualFrame frame, PBuffered self);

        @Specialization
        boolean isClosedBuffered(VirtualFrame frame, PBuffered self,
                        @Cached PRaiseNode raiseNode,
                        @Cached IsClosedNode isClosedNode,
                        @Cached ConditionProfile isError) {
            if (isError.profile(isClosedNode.execute(frame, self))) {
                throw raiseNode.raise(PythonBuiltinClassType.ValueError, messageFmt, method);
            }
            return false;
        }
    }

    abstract static class IsClosedNode extends PNodeWithContext {

        public abstract boolean execute(VirtualFrame frame, PBuffered self);

        @SuppressWarnings("unused")
        @Specialization(guards = {"self.getBuffer() == null"})
        static boolean isClosed(VirtualFrame frame, PBuffered self) {
            return true;
        }

        @SuppressWarnings("unused")
        @Specialization(guards = {"self.getBuffer() != null", "self.isFastClosedChecks()"})
        static boolean isClosedFileIO(VirtualFrame frame, PBuffered self) {
            return self.getFileIORaw().isClosed();
        }

        @Specialization(guards = {"self.getBuffer() != null", "!self.isFastClosedChecks()"})
        static boolean isClosedBuffered(VirtualFrame frame, PBuffered self,
                        @Cached PyObjectGetAttr getAttr,
                        @Cached PyObjectIsTrueNode isTrue) {
            Object res = getAttr.execute(frame, self.getRaw(), CLOSED);
            return isTrue.execute(frame, res);
        }
    }

    abstract static class CheckIsSeekabledNode extends PNodeWithRaise {

        public abstract boolean execute(VirtualFrame frame, PBuffered self);

        @Specialization
        boolean isSeekable(VirtualFrame frame, PBuffered self,
                        @Cached IsSeekableNode isSeekableNode,
                        @Cached ConditionProfile isError) {
            if (isError.profile(!isSeekableNode.execute(frame, self))) {
                throw raise(IOUnsupportedOperation, FILE_OR_STREAM_IS_NOT_SEEKABLE);
            }
            return true;
        }
    }

    abstract static class IsSeekableNode extends Node {

        public abstract boolean execute(VirtualFrame frame, PBuffered self);

        @Specialization
        static boolean isSeekable(VirtualFrame frame, PBuffered self,
                        @Cached PyObjectCallMethodObjArgs callMethod,
                        @Cached PyObjectIsTrueNode isTrue) {
            assert self.isOK();
            Object res = callMethod.execute(frame, self.getRaw(), SEEKABLE);
            return isTrue.execute(frame, res);
        }
    }

    abstract static class IsReadableNode extends Node {

        public abstract boolean execute(VirtualFrame frame, Object raw);

        public boolean isBufferReadable(VirtualFrame frame, PBuffered self) {
            assert self.isOK();
            return execute(frame, self.getRaw());
        }

        @Specialization
        static boolean isReadable(VirtualFrame frame, Object raw,
                        @Cached PyObjectCallMethodObjArgs callMethod,
                        @Cached PyObjectIsTrueNode isTrue) {
            Object res = callMethod.execute(frame, raw, READABLE);
            return isTrue.execute(frame, res);
        }

        public static IsReadableNode create() {
            return BufferedIONodesFactory.IsReadableNodeGen.create();
        }
    }

    abstract static class IsWritableNode extends PNodeWithContext {

        public abstract boolean execute(VirtualFrame frame, Object raw);

        public boolean isBufferWritable(VirtualFrame frame, PBuffered self) {
            assert self.isOK();
            return execute(frame, self.getRaw());
        }

        @Specialization
        static boolean isWritable(VirtualFrame frame, Object raw,
                        @Cached PyObjectCallMethodObjArgs callMethod,
                        @Cached PyObjectIsTrueNode isTrue) {
            Object res = callMethod.execute(frame, raw, WRITABLE);
            return isTrue.execute(frame, res);
        }

        public static IsWritableNode create() {
            return BufferedIONodesFactory.IsWritableNodeGen.create();
        }
    }

    @ImportStatic(PGuards.class)
    @TypeSystemReference(PythonArithmeticTypes.class)
    // PyNumber_AsOff_t
    abstract static class AsOffNumberNode extends PNodeWithContext {

        public abstract long execute(VirtualFrame frame, Object number, PythonBuiltinClassType err);

        @Specialization
        static long doInt(long number, @SuppressWarnings("unused") PythonBuiltinClassType err) {
            return number;
        }

        @Specialization
        static long toLong(VirtualFrame frame, Object number, PythonBuiltinClassType err,
                        @Cached PRaiseNode raiseNode,
                        @Cached PyNumberIndexNode indexNode,
                        @Cached CastToJavaLongExactNode cast,
                        @Cached IsBuiltinClassProfile errorProfile) {
            Object index = indexNode.execute(frame, number);
            try {
                return cast.execute(index);
            } catch (PException e) {
                e.expect(OverflowError, errorProfile);
                throw raiseNode.raise(err, CANNOT_FIT_P_IN_OFFSET_SIZE, number);
            } catch (CannotCastException e) {
                throw CompilerDirectives.shouldNotReachHere();
            }
        }
    }

    abstract static class RawTellNode extends PNodeWithRaise {

        protected final boolean ignore;

        public RawTellNode(boolean ignore) {
            this.ignore = ignore;
        }

        public abstract long execute(VirtualFrame frame, PBuffered self);

        private static long tell(VirtualFrame frame, Object raw,
                        PyObjectCallMethodObjArgs callMethod,
                        AsOffNumberNode asOffNumberNode) {
            Object res = callMethod.execute(frame, raw, TELL);
            return asOffNumberNode.execute(frame, res, ValueError);
        }

        /**
         * implementation of cpython/Modules/_io/bufferedio.c:_buffered_raw_tell
         */
        @Specialization(guards = "!ignore")
        long bufferedRawTell(VirtualFrame frame, PBuffered self,
                        @Shared("callMethod") @Cached PyObjectCallMethodObjArgs callMethod,
                        @Shared("asOffT") @Cached AsOffNumberNode asOffNumberNode,
                        @Cached ConditionProfile isValid) {
            long n = tell(frame, self.getRaw(), callMethod, asOffNumberNode);
            if (isValid.profile(n < 0)) {
                throw raise(OSError, IO_STREAM_INVALID_POS, n);
            }
            self.setAbsPos(n);
            return n;
        }

        @Specialization(guards = "ignore")
        static long bufferedRawTellIgnoreException(VirtualFrame frame, PBuffered self,
                        @Shared("callMethod") @Cached PyObjectCallMethodObjArgs callMethod,
                        @Shared("asOffT") @Cached AsOffNumberNode asOffNumberNode) {
            long n;
            try {
                n = tell(frame, self.getRaw(), callMethod, asOffNumberNode);
            } catch (PException e) {
                n = -1;
                // ignore
                // PyErr_Clear();
            }
            self.setAbsPos(n);
            return n;
        }

        public static RawTellNode create() {
            return BufferedIONodesFactory.RawTellNodeGen.create(false);
        }

    }

    /**
     * implementation of cpython/Modules/_io/bufferedio.c:_buffered_raw_seek
     */
    abstract static class RawSeekNode extends PNodeWithContext {

        public abstract long execute(VirtualFrame frame, PBuffered self, long target, int whence);

        @Specialization
        static long bufferedRawSeek(VirtualFrame frame, PBuffered self, long target, int whence,
                        @Cached PRaiseNode raise,
                        @Cached PyObjectCallMethodObjArgs callMethod,
                        @Cached AsOffNumberNode asOffNumberNode,
                        @Cached ConditionProfile profile) {
            Object res = callMethod.execute(frame, self.getRaw(), SEEK, target, whence);
            long n = asOffNumberNode.execute(frame, res, ValueError);
            if (profile.profile(n < 0)) {
                raise.raise(OSError, IO_STREAM_INVALID_POS, n);
            }
            self.setAbsPos(n);
            return n;
        }
    }

    /**
     * implementation of cpython/Modules/_io/bufferedio.c:buffered_flush_and_rewind_unlocked
     */
    abstract static class FlushAndRewindUnlockedNode extends PNodeWithContext {

        public abstract void execute(VirtualFrame frame, PBuffered self);

        @Specialization(guards = {"self.isReadable()", "!self.isWritable()"})
        protected static void readOnly(VirtualFrame frame, PBuffered self,
                        @Cached RawSeekNode rawSeekNode) {
            /*
             * Rewind the raw stream so that its position corresponds to the current logical
             * position.
             */
            long n = rawSeekNode.execute(frame, self, -rawOffset(self), 1);
            self.resetRead(); // _bufferedreader_reset_buf
            assert n != -1;
        }

        @Specialization(guards = {"!self.isReadable()", "self.isWritable()"})
        protected static void writeOnly(VirtualFrame frame, PBuffered self,
                        @Cached BufferedWriterNodes.FlushUnlockedNode flushUnlockedNode) {
            flushUnlockedNode.execute(frame, self);
        }

        @Specialization(guards = {"self.isReadable()", "self.isWritable()"})
        protected static void readWrite(VirtualFrame frame, PBuffered self,
                        @Cached BufferedWriterNodes.FlushUnlockedNode flushUnlockedNode,
                        @Cached RawSeekNode rawSeekNode) {
            flushUnlockedNode.execute(frame, self);
            /*
             * Rewind the raw stream so that its position corresponds to the current logical
             * position.
             */
            long n = rawSeekNode.execute(frame, self, -rawOffset(self), 1);
            self.resetRead(); // _bufferedreader_reset_buf
            assert n != -1;
        }
    }

    /**
     * implementation of cpython/Modules/_io/bufferedio.c:_io__Buffered_seek_impl
     */
    abstract static class SeekNode extends PNodeWithContext {

        public abstract long execute(VirtualFrame frame, PBuffered self, long off, int whence);

        @Specialization
        static long seek(VirtualFrame frame, PBuffered self, long off, int whence,
                        @Cached EnterBufferedNode lock,
                        @Cached BufferedWriterNodes.FlushUnlockedNode flushUnlockedNode,
                        @Cached RawSeekNode rawSeekNode,
                        @Cached RawTellNode rawTellNode,
                        @Cached ConditionProfile isSetOrCur,
                        @Cached ConditionProfile isAvail) {
            long target = off;
            /*
             * SEEK_SET and SEEK_CUR are special because we could seek inside the buffer. Other
             * whence values must be managed without this optimization. Some Operating Systems can
             * provide additional values, like SEEK_HOLE/SEEK_DATA.
             */
            if (isSetOrCur.profile(((whence == SEEK_SET) || (whence == SEEK_CUR)) && self.isReadable())) {
                /*
                 * Check if seeking leaves us inside the current buffer, so as to return quickly if
                 * possible. Also, we needn't take the lock in this fast path. Don't know how to do
                 * that when whence == 2, though.
                 */
                long current = self.getAbsPos() != -1 ? self.getAbsPos() : rawTellNode.execute(frame, self);
                int avail = readahead(self);
                if (isAvail.profile(avail > 0)) {
                    long offset = target;
                    if (whence == SEEK_SET) {
                        offset -= (current - rawOffset(self));
                    }
                    if (offset >= -self.getPos() && offset <= avail) {
                        self.incPos((int) offset); // this is safe hence the if condition
                        return (current - avail + offset);
                    }
                }
            }

            lock.enter(self);
            try {
                /* Fallback: invoke raw seek() method and clear buffer */
                if (self.isWritable()) {
                    flushUnlockedNode.execute(frame, self);
                }

                if (whence == SEEK_CUR) {
                    target -= rawOffset(self);
                }
                long n = rawSeekNode.execute(frame, self, target, whence);
                self.setRawPos(-1);
                if (self.isReadable()) {
                    self.resetRead(); // _bufferedreader_reset_buf
                }
                return n;
            } finally {
                EnterBufferedNode.leave(self);
            }
        }
    }

    // TODO: experiment with threads count to avoid locking.
    abstract static class EnterBufferedNode extends Node {

        public abstract void execute(PBuffered self);

        @Specialization
        static void doEnter(PBuffered self,
                        @Cached EnterBufferedBusyNode enterBufferedBusyNode,
                        @Cached ConditionProfile isBusy) {
            if (isBusy.profile(!self.getLock().acquireNonBlocking())) {
                enterBufferedBusyNode.execute(self);
            }
            self.setOwner(ThreadModuleBuiltins.GetCurrentThreadIdNode.getId());
        }

        void enter(PBuffered self) {
            execute(self);
        }

        static void leave(PBuffered self) {
            self.setOwner(0);
            self.getLock().release();
        }
    }

    /**
     * implementation of cpython/Modules/_io/bufferedio.c:_enter_buffered_busy
     */
    abstract static class EnterBufferedBusyNode extends PNodeWithRaise {

        public abstract void execute(PBuffered self);

        @Specialization(guards = {"!self.isOwn()", "!getContext().isFinalizing()"})
        void normal(PBuffered self,
                        @Cached GilNode gil) {
            gil.release(true);
            try {
                self.getLock().acquireBlocking(this);
            } finally {
                gil.acquire();
            }
        }

        @Specialization(guards = {"!self.isOwn()", "getContext().isFinalizing()"})
        void finalizing(PBuffered self) {
            /*
             * When finalizing, we don't want a deadlock to happen with daemon threads abruptly shut
             * down while they owned the lock. Therefore, only wait for a grace period (1 s.). Note
             * that non-daemon threads have already exited here, so this shouldn't affect carefully
             * written threaded I/O code.
             */
            if (!self.getLock().acquireTimeout(this, (long) 1e3)) {
                throw raise(SystemError, SHUTDOWN_POSSIBLY_DUE_TO_DAEMON_THREADS);
            }
        }

        @Specialization(guards = "self.isOwn()")
        void error(PBuffered self) {
            throw raise(RuntimeError, REENTRANT_CALL_INSIDE_P, self);
        }
    }
}
