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

import static com.oracle.graal.python.builtins.PythonBuiltinClassType.OverflowError;
import static com.oracle.graal.python.builtins.modules.io.BufferedIOUtil.SEEK_CUR;
import static com.oracle.graal.python.builtins.modules.io.BufferedIOUtil.SEEK_SET;
import static com.oracle.graal.python.builtins.modules.io.BufferedIOUtil.rawOffset;
import static com.oracle.graal.python.builtins.modules.io.BufferedIOUtil.readahead;
import static com.oracle.graal.python.builtins.modules.io.IONodes.T_CLOSED;
import static com.oracle.graal.python.builtins.modules.io.IONodes.T_SEEK;
import static com.oracle.graal.python.builtins.modules.io.IONodes.T_SEEKABLE;
import static com.oracle.graal.python.builtins.modules.io.IONodes.T_TELL;
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
import static com.oracle.graal.python.util.PythonUtils.TS_ENCODING;

import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.modules.ThreadModuleBuiltins;
import com.oracle.graal.python.lib.PyNumberIndexNode;
import com.oracle.graal.python.lib.PyObjectCallMethodObjArgs;
import com.oracle.graal.python.lib.PyObjectGetAttr;
import com.oracle.graal.python.lib.PyObjectIsTrueNode;
import com.oracle.graal.python.nodes.PGuards;
import com.oracle.graal.python.nodes.PNodeWithContext;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.object.BuiltinClassProfiles.IsBuiltinObjectProfile;
import com.oracle.graal.python.nodes.truffle.PythonArithmeticTypes;
import com.oracle.graal.python.nodes.util.CannotCastException;
import com.oracle.graal.python.nodes.util.CastToJavaLongExactNode;
import com.oracle.graal.python.runtime.GilNode;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.GenerateCached;
import com.oracle.truffle.api.dsl.GenerateInline;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.dsl.TypeSystemReference;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.profiles.InlinedConditionProfile;
import com.oracle.truffle.api.strings.TruffleString;

public class BufferedIONodes {

    abstract static class CheckIsClosedNode extends PNodeWithContext {

        private final TruffleString method;
        private final TruffleString messageFmt;

        public CheckIsClosedNode(TruffleString method) {
            this.method = method;
            this.messageFmt = IONodes.T_WRITE.equalsUncached(method, TS_ENCODING) ? S_TO_CLOSED_FILE : S_OF_CLOSED_FILE;
        }

        public abstract boolean execute(VirtualFrame frame, PBuffered self);

        @Specialization
        boolean isClosedBuffered(VirtualFrame frame, PBuffered self,
                        @Bind("this") Node inliningTarget,
                        @Cached PRaiseNode.Lazy raiseNode,
                        @Cached IsClosedNode isClosedNode) {
            if (isClosedNode.execute(frame, inliningTarget, self)) {
                throw raiseNode.get(inliningTarget).raise(PythonBuiltinClassType.ValueError, messageFmt, method);
            }
            return false;
        }
    }

    @GenerateInline
    @GenerateCached(false)
    abstract static class IsClosedNode extends PNodeWithContext {

        public abstract boolean execute(VirtualFrame frame, Node inliningTarget, PBuffered self);

        @SuppressWarnings("unused")
        @Specialization(guards = {"self.getBuffer() == null"})
        static boolean isClosed(PBuffered self) {
            return true;
        }

        @SuppressWarnings("unused")
        @Specialization(guards = {"self.getBuffer() != null", "self.isFastClosedChecks()"})
        static boolean isClosedFileIO(PBuffered self) {
            return self.getFileIORaw().isClosed();
        }

        @Specialization(guards = {"self.getBuffer() != null", "!self.isFastClosedChecks()"})
        static boolean isClosedBuffered(VirtualFrame frame, Node inliningTarget, PBuffered self,
                        @Cached PyObjectGetAttr getAttr,
                        @Cached PyObjectIsTrueNode isTrue) {
            Object res = getAttr.execute(frame, inliningTarget, self.getRaw(), T_CLOSED);
            return isTrue.execute(frame, inliningTarget, res);
        }
    }

    @SuppressWarnings("truffle-inlining")       // footprint reduction 104 -> 85
    abstract static class CheckIsSeekabledNode extends Node {

        public abstract boolean execute(VirtualFrame frame, PBuffered self);

        @Specialization
        static boolean isSeekable(VirtualFrame frame, PBuffered self,
                        @Bind("this") Node inliningTarget,
                        @Cached PyObjectCallMethodObjArgs callMethod,
                        @Cached PyObjectIsTrueNode isTrue,
                        @Cached PRaiseNode.Lazy raiseNode) {
            assert self.isOK();
            Object res = callMethod.execute(frame, inliningTarget, self.getRaw(), T_SEEKABLE);
            if (!isTrue.execute(frame, inliningTarget, res)) {
                throw raiseNode.get(inliningTarget).raise(IOUnsupportedOperation, FILE_OR_STREAM_IS_NOT_SEEKABLE);
            }
            return true;
        }
    }

    @ImportStatic(PGuards.class)
    @TypeSystemReference(PythonArithmeticTypes.class)
    @GenerateInline
    @GenerateCached(false)
    // PyNumber_AsOff_t
    abstract static class AsOffNumberNode extends PNodeWithContext {

        public abstract long execute(VirtualFrame frame, Node inliningTarget, Object number, PythonBuiltinClassType err);

        @Specialization
        static long doInt(long number, @SuppressWarnings("unused") PythonBuiltinClassType err) {
            return number;
        }

        @Specialization
        static long toLong(VirtualFrame frame, Node inliningTarget, Object number, PythonBuiltinClassType err,
                        @Cached PRaiseNode.Lazy raiseNode,
                        @Cached PyNumberIndexNode indexNode,
                        @Cached CastToJavaLongExactNode cast,
                        @Cached IsBuiltinObjectProfile errorProfile) {
            Object index = indexNode.execute(frame, inliningTarget, number);
            try {
                return cast.execute(inliningTarget, index);
            } catch (PException e) {
                e.expect(inliningTarget, OverflowError, errorProfile);
                throw raiseNode.get(inliningTarget).raise(err, CANNOT_FIT_P_IN_OFFSET_SIZE, number);
            } catch (CannotCastException e) {
                throw CompilerDirectives.shouldNotReachHere();
            }
        }
    }

    static long tell(VirtualFrame frame, Node inliningTarget, Object raw,
                    PyObjectCallMethodObjArgs callMethod,
                    AsOffNumberNode asOffNumberNode) {
        Object res = callMethod.execute(frame, inliningTarget, raw, T_TELL);
        return asOffNumberNode.execute(frame, inliningTarget, res, ValueError);
    }

    @GenerateInline(inlineByDefault = true)
    @GenerateCached
    abstract static class RawTellNode extends PNodeWithContext {
        public abstract long execute(VirtualFrame frame, Node inliningTarget, PBuffered self);

        public final long executeCached(VirtualFrame frame, PBuffered self) {
            return execute(frame, this, self);
        }

        /**
         * implementation of cpython/Modules/_io/bufferedio.c:_buffered_raw_tell
         */
        @Specialization
        static long bufferedRawTell(VirtualFrame frame, Node inliningTarget, PBuffered self,
                        @Cached PRaiseNode.Lazy lazyRaiseNode,
                        @Cached PyObjectCallMethodObjArgs callMethod,
                        @Cached AsOffNumberNode asOffNumberNode) {
            long n = tell(frame, inliningTarget, self.getRaw(), callMethod, asOffNumberNode);
            if (n < 0) {
                throw lazyRaiseNode.get(inliningTarget).raise(OSError, IO_STREAM_INVALID_POS, n);
            }
            self.setAbsPos(n);
            return n;
        }
    }

    @GenerateInline
    @GenerateCached(false)
    abstract static class LazyRawTellNode extends Node {
        public final RawTellNode get(Node inliningTarget) {
            return execute(inliningTarget);
        }

        protected abstract RawTellNode execute(Node inliningTarget);

        @Specialization
        RawTellNode doIt(@Cached(inline = false) RawTellNode node) {
            return node;
        }
    }

    @GenerateInline(inlineByDefault = true)
    @GenerateCached(false)
    abstract static class RawTellIgnoreErrorNode extends PNodeWithContext {
        public abstract long execute(VirtualFrame frame, Node inliningTarget, PBuffered self);

        @Specialization
        static long bufferedRawTellIgnoreException(VirtualFrame frame, Node inliningTarget, PBuffered self,
                        @Cached PyObjectCallMethodObjArgs callMethod,
                        @Cached AsOffNumberNode asOffNumberNode) {
            long n;
            try {
                n = tell(frame, inliningTarget, self.getRaw(), callMethod, asOffNumberNode);
            } catch (PException e) {
                n = -1;
                // ignore
                // PyErr_Clear();
            }
            self.setAbsPos(n);
            return n;
        }
    }

    /**
     * implementation of cpython/Modules/_io/bufferedio.c:_buffered_raw_seek
     */
    @GenerateInline(false) // Used lazily
    abstract static class RawSeekNode extends PNodeWithContext {

        public abstract long execute(VirtualFrame frame, PBuffered self, long target, int whence);

        @Specialization
        static long bufferedRawSeek(VirtualFrame frame, PBuffered self, long target, int whence,
                        @Bind("this") Node inliningTarget,
                        @Cached PRaiseNode.Lazy raise,
                        @Cached PyObjectCallMethodObjArgs callMethod,
                        @Cached AsOffNumberNode asOffNumberNode) {
            Object res = callMethod.execute(frame, inliningTarget, self.getRaw(), T_SEEK, target, whence);
            long n = asOffNumberNode.execute(frame, inliningTarget, res, ValueError);
            if (n < 0) {
                raise.get(inliningTarget).raise(OSError, IO_STREAM_INVALID_POS, n);
            }
            self.setAbsPos(n);
            return n;
        }
    }

    /**
     * implementation of cpython/Modules/_io/bufferedio.c:buffered_flush_and_rewind_unlocked
     */
    @GenerateInline
    @GenerateCached(false)
    abstract static class FlushAndRewindUnlockedNode extends PNodeWithContext {

        public abstract void execute(VirtualFrame frame, Node inliningTarget, PBuffered self);

        @Specialization(guards = {"self.isReadable()", "!self.isWritable()"})
        protected static void readOnly(VirtualFrame frame, PBuffered self,
                        @Shared @Cached(inline = false) RawSeekNode rawSeekNode) {
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
                        @Shared @Cached(inline = false) BufferedWriterNodes.FlushUnlockedNode flushUnlockedNode) {
            flushUnlockedNode.execute(frame, self);
        }

        @Specialization(guards = {"self.isReadable()", "self.isWritable()"})
        protected static void readWrite(VirtualFrame frame, PBuffered self,
                        @Shared @Cached(inline = false) BufferedWriterNodes.FlushUnlockedNode flushUnlockedNode,
                        @Shared @Cached(inline = false) RawSeekNode rawSeekNode) {
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
    @GenerateInline
    @GenerateCached
    abstract static class SeekNode extends PNodeWithContext {

        public abstract long execute(VirtualFrame frame, Node inliningTarget, PBuffered self, long off, int whence);

        @Specialization
        static long seek(VirtualFrame frame, @SuppressWarnings("unused") Node ignored, PBuffered self, long off, int whence,
                        @Bind("this") Node inliningTarget,
                        @Cached EnterBufferedNode lock,
                        @Cached(inline = false) BufferedWriterNodes.FlushUnlockedNode flushUnlockedNode,
                        @Cached(inline = false) RawSeekNode rawSeekNode,
                        @Cached LazyRawTellNode rawTellNode,
                        @Cached InlinedConditionProfile whenceSeekSetProfile,
                        @Cached InlinedConditionProfile whenceSeekCurProfile,
                        @Cached InlinedConditionProfile isReadbleProfile,
                        @Cached InlinedConditionProfile isWriteableProfile,
                        @Cached InlinedConditionProfile isSetOrCur,
                        @Cached InlinedConditionProfile isAvail) {
            long target = off;
            /*
             * SEEK_SET and SEEK_CUR are special because we could seek inside the buffer. Other
             * whence values must be managed without this optimization. Some Operating Systems can
             * provide additional values, like SEEK_HOLE/SEEK_DATA.
             */
            boolean whenceSeekSet = whenceSeekSetProfile.profile(inliningTarget, whence == SEEK_SET);
            boolean whenceSeekCur = whenceSeekCurProfile.profile(inliningTarget, whence == SEEK_CUR);
            boolean selfIsReadable = isReadbleProfile.profile(inliningTarget, self.isReadable());
            if (isSetOrCur.profile(inliningTarget, (whenceSeekSet || whenceSeekCur) && selfIsReadable)) {
                /*
                 * Check if seeking leaves us inside the current buffer, so as to return quickly if
                 * possible. Also, we needn't take the lock in this fast path. Don't know how to do
                 * that when whence == 2, though.
                 */
                long current = self.getAbsPos() != -1 ? self.getAbsPos()
                                : rawTellNode.get(inliningTarget).executeCached(frame, self);
                int avail = readahead(self);
                if (isAvail.profile(inliningTarget, avail > 0)) {
                    long offset = target;
                    if (whenceSeekSet) {
                        offset -= (current - rawOffset(self));
                    }
                    if (offset >= -self.getPos() && offset <= avail) {
                        self.incPos((int) offset); // this is safe hence the if condition
                        return (current - avail + offset);
                    }
                }
            }

            lock.enter(inliningTarget, self);
            try {
                /* Fallback: invoke raw seek() method and clear buffer */
                if (isWriteableProfile.profile(inliningTarget, self.isWritable())) {
                    flushUnlockedNode.execute(frame, self);
                }

                if (whenceSeekCur) {
                    target -= rawOffset(self);
                }
                long n = rawSeekNode.execute(frame, self, target, whence);
                self.setRawPos(-1);
                if (selfIsReadable) {
                    self.resetRead(); // _bufferedreader_reset_buf
                }
                return n;
            } finally {
                EnterBufferedNode.leave(self);
            }
        }
    }

    // TODO: experiment with threads count to avoid locking.
    @GenerateInline
    @GenerateCached(false)
    abstract static class EnterBufferedNode extends Node {

        public abstract void execute(Node inliningTarget, PBuffered self);

        @Specialization
        static void doEnter(Node inliningTarget, PBuffered self,
                        @Cached EnterBufferedBusyNode enterBufferedBusyNode,
                        @Cached InlinedConditionProfile isBusy) {
            if (isBusy.profile(inliningTarget, !self.getLock().acquireNonBlocking())) {
                enterBufferedBusyNode.execute(inliningTarget, self);
            }
            self.setOwner(ThreadModuleBuiltins.GetCurrentThreadIdNode.getId());
        }

        void enter(Node inliningTarget, PBuffered self) {
            execute(inliningTarget, self);
        }

        static void leave(PBuffered self) {
            self.setOwner(0);
            self.getLock().release();
        }
    }

    /**
     * implementation of cpython/Modules/_io/bufferedio.c:_enter_buffered_busy
     */
    @GenerateInline
    @GenerateCached(false)
    abstract static class EnterBufferedBusyNode extends PNodeWithContext {

        public abstract void execute(Node inliningTarget, PBuffered self);

        @Specialization(guards = {"!self.isOwn()", "!getContext().isFinalizing()"})
        static void normal(Node inliningTarget, PBuffered self,
                        @Cached(inline = false) GilNode gil) {
            gil.release(true);
            try {
                self.getLock().acquireBlocking(inliningTarget);
            } finally {
                gil.acquire();
            }
        }

        @Specialization(guards = {"!self.isOwn()", "getContext().isFinalizing()"})
        static void finalizing(Node inliningTarget, PBuffered self,
                        @Shared @Cached PRaiseNode.Lazy lazyRaise) {
            /*
             * When finalizing, we don't want a deadlock to happen with daemon threads abruptly shut
             * down while they owned the lock. Therefore, only wait for a grace period (1 s.). Note
             * that non-daemon threads have already exited here, so this shouldn't affect carefully
             * written threaded I/O code.
             */
            if (!self.getLock().acquireTimeout(inliningTarget, (long) 1e3)) {
                throw lazyRaise.get(inliningTarget).raise(SystemError, SHUTDOWN_POSSIBLY_DUE_TO_DAEMON_THREADS);
            }
        }

        @Specialization(guards = "self.isOwn()")
        static void error(Node inliningTarget, PBuffered self,
                        @Shared @Cached PRaiseNode.Lazy lazyRaise) {
            throw lazyRaise.get(inliningTarget).raise(RuntimeError, REENTRANT_CALL_INSIDE_P, self);
        }
    }
}
