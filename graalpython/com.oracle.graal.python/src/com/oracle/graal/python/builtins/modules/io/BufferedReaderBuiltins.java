/*
 * Copyright (c) 2020, Oracle and/or its affiliates. All rights reserved.
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

import static com.oracle.graal.python.builtins.PythonBuiltinClassType.NotImplementedError;
import static com.oracle.graal.python.builtins.PythonBuiltinClassType.PBufferedReader;
import static com.oracle.graal.python.builtins.PythonBuiltinClassType.TypeError;
import static com.oracle.graal.python.builtins.modules.io.BufferedIOUtil.SEEK_CUR;
import static com.oracle.graal.python.builtins.modules.io.BufferedIOUtil.SEEK_END;
import static com.oracle.graal.python.builtins.modules.io.BufferedIOUtil.SEEK_SET;
import static com.oracle.graal.python.builtins.modules.io.BufferedIOUtil.append;
import static com.oracle.graal.python.builtins.modules.io.BufferedIOUtil.asArray;
import static com.oracle.graal.python.builtins.modules.io.BufferedIOUtil.createList;
import static com.oracle.graal.python.builtins.modules.io.BufferedIOUtil.rawOffset;
import static com.oracle.graal.python.builtins.modules.io.BufferedIOUtil.safeDowncast;
import static com.oracle.graal.python.builtins.modules.io.BufferedReaderNodes.ReadNode.bufferedreaderReadFast;
import static com.oracle.graal.python.nodes.ErrorMessages.BUF_SIZE_POS;
import static com.oracle.graal.python.nodes.ErrorMessages.IO_CLOSED;
import static com.oracle.graal.python.nodes.ErrorMessages.IO_STREAM_DETACHED;
import static com.oracle.graal.python.nodes.ErrorMessages.IO_STREAM_INVALID_POS;
import static com.oracle.graal.python.nodes.ErrorMessages.IO_UNINIT;
import static com.oracle.graal.python.nodes.ErrorMessages.MUST_BE_NON_NEG_OR_NEG_1;
import static com.oracle.graal.python.nodes.ErrorMessages.UNSUPPORTED_WHENCE;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__ENTER__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__EXIT__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__INIT__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__ITER__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__NEXT__;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.OSError;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.StopIteration;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.ValueError;

import java.util.ArrayList;
import java.util.List;

import com.oracle.graal.python.annotations.ArgumentClinic;
import com.oracle.graal.python.builtins.Builtin;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.bytes.PBytes;
import com.oracle.graal.python.builtins.objects.object.PythonObjectLibrary;
import com.oracle.graal.python.nodes.control.GetNextNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonBinaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonBinaryClinicBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonTernaryClinicBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonUnaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.clinic.ArgumentClinicProvider;
import com.oracle.graal.python.nodes.object.IsBuiltinClassProfile;
import com.oracle.graal.python.nodes.truffle.PythonArithmeticTypes;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.graal.python.util.PythonUtils;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.dsl.TypeSystemReference;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.profiles.ConditionProfile;

@CoreFunctions(extendClasses = PBufferedReader)
public class BufferedReaderBuiltins extends PythonBuiltins {
    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return BufferedReaderBuiltinsFactory.getFactories();
    }

    protected static final int DEFAULT_BUFFER_SIZE = IOModuleBuiltins.DEFAULT_BUFFER_SIZE;

    // BufferedReader(raw[, buffer_size=DEFAULT_BUFFER_SIZE])
    @Builtin(name = __INIT__, minNumOfPositionalArgs = 2, parameterNames = {"$self", "$raw", "buffer_size"})
    @ArgumentClinic(name = "buffer_size", conversion = ArgumentClinic.ClinicConversion.Int, defaultValue = "BufferedReaderBuiltins.DEFAULT_BUFFER_SIZE", useDefaultForNone = true)
    @GenerateNodeFactory
    public abstract static class InitNode extends PythonTernaryClinicBuiltinNode {

        @Child BufferedIONodes.IsReadableNode readableNode = BufferedIONodes.IsReadableNode.create();

        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return BufferedReaderBuiltinsClinicProviders.InitNodeClinicProviderGen.INSTANCE;
        }

        protected boolean isReadable(VirtualFrame frame, Object raw) {
            return readableNode.execute(frame, raw);
        }

        @Specialization(guards = {"bufferSize > 0", "isReadable(frame, raw)"}, limit = "1")
        public PNone doInit(VirtualFrame frame, PBuffered self, Object raw, int bufferSize,
                        @CachedLibrary("raw") PythonObjectLibrary libRaw,
                        @CachedLibrary(limit = "1") PythonObjectLibrary asSize,
                        @Cached ConditionProfile profile) {
            int absPos = getRawTell(frame, raw, libRaw, asSize);
            if (profile.profile(absPos < 0)) {
                throw raise(OSError, IO_STREAM_INVALID_POS, absPos);
            }
            int n;
            for (n = bufferSize - 1; (n & 1) != 0; n >>= 1) {
            }
            int mask = n == 0 ? bufferSize - 1 : 0;
            self.setOK(true);
            self.setRaw(raw);
            self.initBuffer(bufferSize);
            self.setBufferMask(mask);
            self.setAbsPos(absPos);
            // TODO: (mq) we should set `self.fastClosedChecks` once we implement FileIO.
            self.setFastClosedChecks(false);
            return PNone.NONE;
        }

        @SuppressWarnings("unused")
        @Specialization(guards = {"bufferSize > 0", "!isReadable(frame, raw)"})
        public PNone err(VirtualFrame frame, PBuffered self, Object raw, int bufferSize) {
            // TODO: raise(io.UnsupportedOperation, "File or stream is not readable.");
            throw raise(NotImplementedError);
        }

        @SuppressWarnings("unused")
        @Specialization(guards = "bufferSize <= 0")
        public PNone err2(VirtualFrame frame, PBuffered self, Object raw, int bufferSize) {
            throw raise(ValueError, BUF_SIZE_POS);
        }

        private static int getRawTell(VirtualFrame frame, Object raw,
                        PythonObjectLibrary callTell,
                        PythonObjectLibrary asSize) {
            int n = 0;
            Object res = null;
            try {
                res = callTell.lookupAndCallRegularMethod(raw, frame, "tell");
            } catch (PException e) {
                // pass through.
                // (mq) 'tell' is not a reqirement if it is not supported for `raw` input.
                // clear error?
            }
            if (res != null) {
                n = asSize.asSize(res, ValueError);
            }
            return n;
        }

    }

    abstract static class PythonBinaryWithInitErrorClinicBuiltinNode extends PythonBinaryClinicBuiltinNode {
        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            throw CompilerDirectives.shouldNotReachHere("abstract");
        }

        @SuppressWarnings("unused")
        @Specialization(guards = "!self.isOK()")
        Object initError(VirtualFrame frame, PBuffered self, Object o) {
            if (self.isDetached()) {
                throw raise(ValueError, IO_STREAM_DETACHED);
            } else {
                throw raise(ValueError, IO_UNINIT);
            }
        }
    }

    abstract static class PythonUnaryWithInitErrorBuiltinNode extends PythonUnaryBuiltinNode {
        @SuppressWarnings("unused")
        @Specialization(guards = "!self.isOK()")
        Object initError(VirtualFrame frame, PBuffered self) {
            if (self.isDetached()) {
                throw raise(ValueError, IO_STREAM_DETACHED);
            } else {
                throw raise(ValueError, IO_UNINIT);
            }
        }
    }

    @Builtin(name = "detach", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class DetachNode extends PythonUnaryWithInitErrorBuiltinNode {
        @Specialization(guards = "self.isOK()", limit = "1")
        Object doit(VirtualFrame frame, PBuffered self,
                        @CachedLibrary("self") PythonObjectLibrary libSelf) {
            libSelf.lookupAndCallRegularMethod(self, frame, "flush");
            Object raw = self.getRaw();
            self.setRaw(null);
            self.setDetached(true);
            self.setOK(false);
            return raw;
        }
    }

    @Builtin(name = "flush", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class FlushNode extends PythonUnaryWithInitErrorBuiltinNode {
        @Specialization(guards = "self.isOK()", limit = "1")
        Object doit(VirtualFrame frame, PBuffered self,
                        @CachedLibrary("self.getRaw()") PythonObjectLibrary libRaw) {
            return libRaw.lookupAndCallRegularMethod(self.getRaw(), frame, "flush");
        }
    }

    @Builtin(name = "close", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class CloseNode extends PythonUnaryWithInitErrorBuiltinNode {

        @Specialization(guards = "self.isOK()", limit = "1")
        Object doit(VirtualFrame frame, PBuffered self,
                        @Cached BufferedIONodes.IsClosedNode isClosedNode,
                        @CachedLibrary("self") PythonObjectLibrary libSelf,
                        @CachedLibrary("self.getRaw()") PythonObjectLibrary libRaw,
                        @Cached ConditionProfile profile) {
            if (profile.profile(isClosedNode.execute(frame, self))) {
                return PNone.NONE;
            }
            /*-
                XXX: (mq) this should only be done during object deallocation.
                if (self.getRaw() != null) {
                    libRaw.lookupAndCallRegularMethod(self.getRaw(), frame, "_dealloc_warn", self);
                }            
             */

            libSelf.lookupAndCallRegularMethod(self, frame, "flush");
            // (mq) Note: we might need to check the return of `flush`.
            Object res = libRaw.lookupAndCallRegularMethod(self.getRaw(), frame, "close");
            if (self.getBuffer() != null) {
                self.setBuffer(null);
            }
            // (mq) Note: we might need to deal with chained exceptions.
            return res;
        }
    }

    @Builtin(name = "closed", minNumOfPositionalArgs = 1, isGetter = true)
    @GenerateNodeFactory
    abstract static class ClosedNode extends PythonUnaryWithInitErrorBuiltinNode {

        @Specialization(guards = "self.isOK()")
        Object doit(VirtualFrame frame, PBuffered self,
                        @Cached BufferedIONodes.IsClosedNode isClosedNode) {
            return isClosedNode.execute(frame, self);
        }
    }

    @Builtin(name = "name", minNumOfPositionalArgs = 1, isGetter = true)
    @GenerateNodeFactory
    abstract static class NameNode extends PythonUnaryWithInitErrorBuiltinNode {

        @Specialization(guards = "self.isOK()", limit = "2")
        Object doit(VirtualFrame frame, PBuffered self,
                        @CachedLibrary("self.getRaw()") PythonObjectLibrary libRaw) {
            return libRaw.lookupAttribute(self.getRaw(), frame, "name");
        }
    }

    @Builtin(name = "mode", minNumOfPositionalArgs = 1, isGetter = true)
    @GenerateNodeFactory
    abstract static class ModeNode extends PythonUnaryWithInitErrorBuiltinNode {

        @Specialization(guards = "self.isOK()", limit = "2")
        Object doit(VirtualFrame frame, PBuffered self,
                        @CachedLibrary("self.getRaw()") PythonObjectLibrary libRaw) {
            return libRaw.lookupAttribute(self.getRaw(), frame, "mode");
        }
    }

    @Builtin(name = "seekable", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class SeekableNode extends PythonUnaryWithInitErrorBuiltinNode {
        @Specialization(guards = "self.isOK()", limit = "1")
        Object doit(VirtualFrame frame, PBuffered self,
                        @CachedLibrary("self.getRaw()") PythonObjectLibrary libRaw) {
            return libRaw.lookupAndCallRegularMethod(self.getRaw(), frame, "seekable");
        }
    }

    @Builtin(name = "readable", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class ReadableNode extends PythonUnaryWithInitErrorBuiltinNode {
        @Specialization(guards = "self.isOK()", limit = "1")
        Object doit(VirtualFrame frame, PBuffered self,
                        @CachedLibrary("self.getRaw()") PythonObjectLibrary libRaw) {
            return libRaw.lookupAndCallRegularMethod(self.getRaw(), frame, "readable");
        }
    }

    @Builtin(name = "fileno", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class FileNoNode extends PythonUnaryWithInitErrorBuiltinNode {
        @Specialization(guards = "self.isOK()", limit = "1")
        Object doit(VirtualFrame frame, PBuffered self,
                        @CachedLibrary("self.getRaw()") PythonObjectLibrary libRaw) {
            return libRaw.lookupAndCallRegularMethod(self.getRaw(), frame, "fileno");
        }
    }

    @Builtin(name = "isatty", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class IsAttyNode extends PythonUnaryWithInitErrorBuiltinNode {
        @Specialization(guards = "self.isOK()", limit = "1")
        Object doit(VirtualFrame frame, PBuffered self,
                        @CachedLibrary("self.getRaw()") PythonObjectLibrary libRaw) {
            return libRaw.lookupAndCallRegularMethod(self.getRaw(), frame, "isatty");
        }
    }

    @Builtin(name = "_dealloc_warn", minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    abstract static class DeallocWarnNode extends PythonBinaryBuiltinNode {
        @Specialization(guards = {"self.isOK()", "self.getRaw() != null"}, limit = "1")
        Object doit(VirtualFrame frame, PBuffered self, Object source,
                        @CachedLibrary("self.getRaw()") PythonObjectLibrary libRaw) {
            libRaw.lookupAndCallRegularMethod(self.getRaw(), frame, "_dealloc_warn", source);
            return PNone.NONE;
        }

        @SuppressWarnings("unused")
        @Fallback
        Object none(VirtualFrame frame, Object self, Object source) {
            return PNone.NONE;
        }
    }

    /*
     * Generic read function: read from the stream until enough bytes are read, or until an EOF
     * occurs or until read() would block.
     */

    @Builtin(name = "read", minNumOfPositionalArgs = 1, parameterNames = {"$self", "size"})
    @ArgumentClinic(name = "size", conversion = ArgumentClinic.ClinicConversion.Int, defaultValue = "-1", useDefaultForNone = true)
    @GenerateNodeFactory
    abstract static class ReadNode extends PythonBinaryWithInitErrorClinicBuiltinNode {

        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return BufferedReaderBuiltinsClinicProviders.ReadNodeClinicProviderGen.INSTANCE;
        }

        protected static boolean isValidSize(int size) {
            return size >= -1;
        }

        protected static final String CLOSE_ERROR_MSG = "read of closed file";

        @Specialization(guards = {"self.isOK()", "isValidSize(size)"})
        Object read(@SuppressWarnings("unused") VirtualFrame frame, PBuffered self, int size,
                        @Cached("create(CLOSE_ERROR_MSG)") BufferedIONodes.CheckIsClosedNode checkIsClosedNode,
                        @Cached BufferedReaderNodes.ReadNode readNode) {
            checkIsClosedNode.execute(frame, self);
            byte[] res = readNode.execute(frame, self, size);
            return factory().createBytes(res);
        }

        @SuppressWarnings("unused")
        @Specialization(guards = {"self.isOK()", "!isValidSize(size)"})
        Object initError(VirtualFrame frame, PBuffered self, int size) {
            throw raise(ValueError, MUST_BE_NON_NEG_OR_NEG_1);
        }
    }

    @Builtin(name = "peek", minNumOfPositionalArgs = 1, parameterNames = {"$self", "size"})
    @ArgumentClinic(name = "size", conversion = ArgumentClinic.ClinicConversion.Int, defaultValue = "0", useDefaultForNone = true)
    @GenerateNodeFactory
    abstract static class PeekNode extends PythonBinaryWithInitErrorClinicBuiltinNode {
        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return BufferedReaderBuiltinsClinicProviders.PeekNodeClinicProviderGen.INSTANCE;
        }

        protected static final String CLOSE_ERROR_MSG = "peek of closed file";

        @Specialization(guards = "self.isOK()")
        Object doit(VirtualFrame frame, PBuffered self, @SuppressWarnings("unused") int size,
                        @Cached("create(CLOSE_ERROR_MSG)") BufferedIONodes.CheckIsClosedNode checkIsClosedNode,
                        @Cached BufferedReaderNodes.PeekUnlockedNode peekUnlockedNode,
                        @Cached BufferedIONodes.FlushAndRewindUnlockedNode flushAndRewindUnlockedNode) {
            checkIsClosedNode.execute(frame, self);
            if (self.isWritable()) {
                flushAndRewindUnlockedNode.execute(frame, self);
            }
            return factory().createBytes(peekUnlockedNode.execute(frame, self));
        }
    }

    @Builtin(name = "read1", minNumOfPositionalArgs = 1, parameterNames = {"$self", "size"})
    @ArgumentClinic(name = "size", conversion = ArgumentClinic.ClinicConversion.Int, defaultValue = "-1", useDefaultForNone = true)
    @GenerateNodeFactory
    abstract static class Read1Node extends PythonBinaryWithInitErrorClinicBuiltinNode {
        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return BufferedReaderBuiltinsClinicProviders.Read1NodeClinicProviderGen.INSTANCE;
        }

        protected static final String CLOSE_ERROR_MSG = "read of closed file";

        @Specialization(guards = "self.isOK()")
        PBytes doit(VirtualFrame frame, PBuffered self, int size,
                        @Cached("create(CLOSE_ERROR_MSG)") BufferedIONodes.CheckIsClosedNode checkIsClosedNode,
                        @Cached BufferedReaderNodes.RawReadNode rawReadNode) {
            checkIsClosedNode.execute(frame, self);
            int n = size;
            if (n < 0) {
                n = self.getBufferSize();
            }

            if (n == 0) {
                return factory().createBytes(PythonUtils.EMPTY_BYTE_ARRAY);
            }
            /*- Return up to n bytes.  If at least one byte is buffered, we
               only return buffered bytes.  Otherwise, we do one raw read. */

            int have = safeDowncast(self);
            if (have > 0) {
                n = have < n ? have : n;
                byte[] b = bufferedreaderReadFast(self, n);
                return factory().createBytes(b);
            }
            self.resetRead(); // _bufferedreader_reset_buf
            byte[] fill = rawReadNode.execute(frame, self, n);
            return factory().createBytes(fill);
        }
    }

    @Builtin(name = "readinto", minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    abstract static class ReadIntoNode extends PythonBinaryBuiltinNode {

        protected static final String CLOSE_ERROR_MSG = "readline of closed file";

        @Specialization(guards = "self.isOK()", limit = "1")
        int doit(VirtualFrame frame, PBuffered self, Object buffer,
                        @Cached("create(CLOSE_ERROR_MSG)") BufferedIONodes.CheckIsClosedNode checkIsClosedNode,
                        @Cached BufferedReaderNodes.ReadintoNode readintoNode,
                        @CachedLibrary("buffer") PythonObjectLibrary getLen) {
            checkIsClosedNode.execute(frame, self);
            int bufLen = getLen.lengthWithFrame(buffer, frame);
            return readintoNode.execute(frame, self, buffer, bufLen, isReadinto1Mode());
        }

        @SuppressWarnings("unused")
        @Specialization(guards = "!self.isOK()")
        Object initError(VirtualFrame frame, PBuffered self, Object buffer) {
            if (self.isDetached()) {
                throw raise(ValueError, IO_STREAM_DETACHED);
            } else {
                throw raise(ValueError, IO_UNINIT);
            }
        }

        protected boolean isReadinto1Mode() {
            return false;
        }
    }

    @Builtin(name = "readinto1", minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    abstract static class ReadInto1Node extends ReadIntoNode {
        @Override
        protected boolean isReadinto1Mode() {
            return true;
        }
    }

    @Builtin(name = "readline", minNumOfPositionalArgs = 1, parameterNames = {"$self", "size"})
    @ArgumentClinic(name = "size", conversion = ArgumentClinic.ClinicConversion.Int, defaultValue = "-1", useDefaultForNone = true)
    @GenerateNodeFactory
    abstract static class ReadlineNode extends PythonBinaryWithInitErrorClinicBuiltinNode {
        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return BufferedReaderBuiltinsClinicProviders.ReadlineNodeClinicProviderGen.INSTANCE;
        }

        protected static final String CLOSE_ERROR_MSG = "readline of closed file";

        @Specialization(guards = "self.isOK()")
        PBytes doit(VirtualFrame frame, PBuffered self, int size,
                        @Cached("create(CLOSE_ERROR_MSG)") BufferedIONodes.CheckIsClosedNode checkIsClosedNode,
                        @Cached BufferedIONodes.ReadlineNode readlineNode) {
            checkIsClosedNode.execute(frame, self);
            byte[] res = readlineNode.execute(frame, self, size);
            return factory().createBytes(res);
        }
    }

    @Builtin(name = "seek", minNumOfPositionalArgs = 2, parameterNames = {"$self", "$offset", "whence"})
    @ArgumentClinic(name = "whence", conversion = ArgumentClinic.ClinicConversion.Int, defaultValue = "BufferedIOUtil.SEEK_SET", useDefaultForNone = true)
    @GenerateNodeFactory
    @TypeSystemReference(PythonArithmeticTypes.class)
    abstract static class SeekNode extends PythonTernaryClinicBuiltinNode {

        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return BufferedReaderBuiltinsClinicProviders.SeekNodeClinicProviderGen.INSTANCE;
        }

        protected static final String CLOSE_ERROR_MSG = "seek of closed file";
        protected static final String SEEKABLE_ERROR_MSG = "File or stream is not seekable.";

        protected static boolean isSupportedWhence(int whence) {
            return whence == SEEK_SET || whence == SEEK_CUR || whence == SEEK_END;
        }

        @Specialization(guards = {"self.isOK()", "isSupportedWhence(whence)"})
        long doit(VirtualFrame frame, PBuffered self, Object off, int whence,
                        @Cached("create(CLOSE_ERROR_MSG)") BufferedIONodes.CheckIsClosedNode checkIsClosedNode,
                        @Cached("create(SEEKABLE_ERROR_MSG)") BufferedIONodes.CheckIsSeekabledNode checkIsSeekabledNode,
                        @Cached BufferedIONodes.AsOffNumberNode asOffNumberNode,
                        @Cached BufferedIONodes.SeekNode seekNode) {
            checkIsClosedNode.execute(frame, self);
            checkIsSeekabledNode.execute(frame, self);
            return seekNode.execute(frame, self, asOffNumberNode.execute(frame, off, TypeError), whence);
        }

        @SuppressWarnings("unused")
        @Specialization(guards = {"self.isOK()", "!isSupportedWhence(whence)"})
        Object whenceError(VirtualFrame frame, PBuffered self, int off, int whence) {
            throw raise(ValueError, UNSUPPORTED_WHENCE, whence);
        }

        @SuppressWarnings("unused")
        @Specialization(guards = "!self.isOK()")
        Object initError(VirtualFrame frame, PBuffered self, int off, int whence) {
            if (self.isDetached()) {
                throw raise(ValueError, IO_STREAM_DETACHED);
            } else {
                throw raise(ValueError, IO_UNINIT);
            }
        }
    }

    @Builtin(name = "tell", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class TellNode extends PythonUnaryWithInitErrorBuiltinNode {
        @Specialization(guards = "self.isOK()")
        long doit(VirtualFrame frame, PBuffered self,
                        @Cached BufferedIONodes.RawTellNode rawTellNode) {
            long pos = rawTellNode.execute(frame, self);
            pos -= rawOffset(self);
            /* TODO: sanity check (pos >= 0) */
            return pos;
        }
    }

    @Builtin(name = "truncate", minNumOfPositionalArgs = 2, parameterNames = {"$self", "pos"})
    @ArgumentClinic(name = "pos", conversion = ArgumentClinic.ClinicConversion.Int, defaultValue = "-1")
    @GenerateNodeFactory
    abstract static class TruncateNode extends PythonBinaryWithInitErrorClinicBuiltinNode {
        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return BufferedReaderBuiltinsClinicProviders.TruncateNodeClinicProviderGen.INSTANCE;
        }

        protected static final String CLOSE_ERROR_MSG = "truncate of closed file";

        @Specialization(guards = {"self.isOK()", "self.isWritable()"}, limit = "1")
        Object doit(VirtualFrame frame, PBuffered self, int pos,
                        @Cached("create(CLOSE_ERROR_MSG)") BufferedIONodes.CheckIsClosedNode checkIsClosedNode,
                        @Cached BufferedIONodes.RawTellNode rawTellNode,
                        @Cached BufferedIONodes.FlushAndRewindUnlockedNode flushAndRewindUnlockedNode,
                        @CachedLibrary("self.getRaw()") PythonObjectLibrary libRaw) {
            checkIsClosedNode.execute(frame, self);
            flushAndRewindUnlockedNode.execute(frame, self);
            Object res = libRaw.lookupAndCallRegularMethod(self.getRaw(), frame, "truncate", pos);
            /* Reset cached position */
            rawTellNode.execute(frame, self);
            return res;
        }

        @SuppressWarnings("unused")
        @Specialization(guards = {"self.isOK()", "!self.isWritable()"})
        Object notWritable(VirtualFrame frame, PBuffered self, int pos) {
            throw raise(NotImplementedError, "truncate");
        }
    }

    @Builtin(name = __ENTER__, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class EnterNode extends PythonUnaryWithInitErrorBuiltinNode {

        protected static final String CLOSE_ERROR_MSG = IO_CLOSED;

        @Specialization(guards = "self.isOK()")
        Object doit(@SuppressWarnings("unused") VirtualFrame frame, PBuffered self,
                        @Cached("create(CLOSE_ERROR_MSG)") BufferedIONodes.CheckIsClosedNode checkIsClosedNode) {
            checkIsClosedNode.execute(frame, self);
            return self;
        }
    }

    @Builtin(name = __EXIT__, minNumOfPositionalArgs = 1, takesVarArgs = true)
    @GenerateNodeFactory
    public abstract static class ExitNode extends PythonBuiltinNode {
        @Specialization(limit = "1")
        static Object exit(VirtualFrame frame, PBuffered self, @SuppressWarnings("unused") Object[] args,
                        @CachedLibrary("self") PythonObjectLibrary libSelf) {
            libSelf.lookupAndCallRegularMethod(self, frame, "close");
            return PNone.NONE;
        }
    }

    @Builtin(name = "writable", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class WritableNode extends PythonUnaryBuiltinNode {
        @Specialization
        boolean doit(@SuppressWarnings("unused") PBuffered self) {
            return false;
        }
    }

    @Builtin(name = "raw", minNumOfPositionalArgs = 1, isGetter = true)
    @GenerateNodeFactory
    abstract static class RawNode extends PythonUnaryBuiltinNode {
        @Specialization
        Object doit(PBuffered self) {
            return self.getRaw();
        }
    }

    @Builtin(name = __ITER__, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class IterNode extends EnterNode {
    }

    @Builtin(name = __NEXT__, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class IternextNode extends PythonUnaryWithInitErrorBuiltinNode {

        protected static final String CLOSE_ERROR_MSG = "readline of closed file";

        @Specialization(guards = "self.isOK()")
        PBytes doit(VirtualFrame frame, PBuffered self,
                        @Cached("create(CLOSE_ERROR_MSG)") BufferedIONodes.CheckIsClosedNode checkIsClosedNode,
                        @Cached BufferedIONodes.ReadlineNode readlineNode) {
            checkIsClosedNode.execute(frame, self);
            byte[] line = readlineNode.execute(frame, self, -1);
            if (line.length == 0) {
                throw raise(StopIteration);
            }
            return factory().createBytes(line);
        }
    }

    @Builtin(name = "readlines", minNumOfPositionalArgs = 1, parameterNames = {"$self", "hint"})
    @ArgumentClinic(name = "hint", conversion = ArgumentClinic.ClinicConversion.Int, defaultValue = "-1", useDefaultForNone = true)
    @GenerateNodeFactory
    abstract static class ReadlinesNode extends PythonBinaryClinicBuiltinNode {
        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return BufferedReaderBuiltinsClinicProviders.PeekNodeClinicProviderGen.INSTANCE;
        }

        @Specialization(guards = "hint <= 0", limit = "1")
        Object doall(VirtualFrame frame, PBuffered self, @SuppressWarnings("unused") int hint,
                        @Cached GetNextNode next,
                        @Cached IsBuiltinClassProfile errorProfile,
                        @CachedLibrary("self") PythonObjectLibrary libSelf,
                        @CachedLibrary(limit = "1") PythonObjectLibrary libLen) {
            return withHint(frame, self, Integer.MAX_VALUE, next, errorProfile, libSelf, libLen);
        }

        @Specialization(guards = "hint > 0", limit = "1")
        Object withHint(VirtualFrame frame, PBuffered self, @SuppressWarnings("unused") int hint,
                        @Cached GetNextNode next,
                        @Cached IsBuiltinClassProfile errorProfile,
                        @CachedLibrary("self") PythonObjectLibrary libSelf,
                        @CachedLibrary(limit = "1") PythonObjectLibrary libLen) {
            int length = 0;
            Object iterator = libSelf.getIteratorWithFrame(self, frame);
            ArrayList<Object> list = createList();
            while (true) {
                try {
                    Object line = next.execute(frame, iterator);
                    append(list, line);
                    int lineLength = libLen.length(line);
                    if (lineLength > hint - length) {
                        break;
                    }
                    length += lineLength;
                } catch (PException e) {
                    e.expectStopIteration(errorProfile);
                    break;
                }
            }
            return factory().createList(asArray(list));
        }
    }

}
