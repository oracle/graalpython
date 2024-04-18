/*
 * Copyright (c) 2021, 2024, Oracle and/or its affiliates. All rights reserved.
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

import static com.oracle.graal.python.builtins.PythonBuiltinClassType.PBufferedRandom;
import static com.oracle.graal.python.builtins.PythonBuiltinClassType.PBufferedReader;
import static com.oracle.graal.python.builtins.modules.io.BufferedIOUtil.isValidReadBuffer;
import static com.oracle.graal.python.builtins.modules.io.BufferedIOUtil.minusLastBlock;
import static com.oracle.graal.python.builtins.modules.io.BufferedIOUtil.safeDowncast;
import static com.oracle.graal.python.builtins.modules.io.IONodes.J_PEEK;
import static com.oracle.graal.python.builtins.modules.io.IONodes.J_READ;
import static com.oracle.graal.python.builtins.modules.io.IONodes.J_READ1;
import static com.oracle.graal.python.builtins.modules.io.IONodes.J_READABLE;
import static com.oracle.graal.python.builtins.modules.io.IONodes.J_READINTO;
import static com.oracle.graal.python.builtins.modules.io.IONodes.J_READINTO1;
import static com.oracle.graal.python.builtins.modules.io.IONodes.J_READLINE;
import static com.oracle.graal.python.builtins.modules.io.IONodes.T_READ;
import static com.oracle.graal.python.builtins.modules.io.IONodes.T_READABLE;
import static com.oracle.graal.python.builtins.modules.io.IONodes.T_READINTO;
import static com.oracle.graal.python.builtins.modules.io.IONodes.T_READLINE;
import static com.oracle.graal.python.builtins.objects.bytes.BytesUtils.append;
import static com.oracle.graal.python.builtins.objects.bytes.BytesUtils.createOutputStream;
import static com.oracle.graal.python.builtins.objects.bytes.BytesUtils.toByteArray;
import static com.oracle.graal.python.nodes.ErrorMessages.IO_S_INVALID_LENGTH;
import static com.oracle.graal.python.nodes.ErrorMessages.IO_S_SHOULD_RETURN_BYTES;
import static com.oracle.graal.python.nodes.ErrorMessages.MUST_BE_NON_NEG_OR_NEG_1;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___NEXT__;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.OSError;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.TypeError;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.ValueError;
import static com.oracle.graal.python.util.PythonUtils.tsLiteral;

import java.io.ByteArrayOutputStream;
import java.util.List;

import com.oracle.graal.python.annotations.ArgumentClinic;
import com.oracle.graal.python.builtins.Builtin;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.modules.io.BufferedIONodes.CheckIsClosedNode;
import com.oracle.graal.python.builtins.modules.io.BufferedIONodes.EnterBufferedNode;
import com.oracle.graal.python.builtins.modules.io.BufferedIONodes.FlushAndRewindUnlockedNode;
import com.oracle.graal.python.builtins.modules.io.BufferedIONodesFactory.CheckIsClosedNodeGen;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.buffer.PythonBufferAccessLibrary;
import com.oracle.graal.python.builtins.objects.bytes.BytesNodes;
import com.oracle.graal.python.builtins.objects.bytes.BytesUtils;
import com.oracle.graal.python.builtins.objects.bytes.PByteArray;
import com.oracle.graal.python.builtins.objects.bytes.PBytes;
import com.oracle.graal.python.lib.PyNumberAsSizeNode;
import com.oracle.graal.python.lib.PyObjectCallMethodObjArgs;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PNodeWithContext;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.attributes.LookupAttributeInMRONode;
import com.oracle.graal.python.nodes.call.special.CallUnaryMethodNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.builtins.clinic.ArgumentClinicProvider;
import com.oracle.graal.python.nodes.object.GetClassNode;
import com.oracle.graal.python.runtime.IndirectCallData;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.graal.python.runtime.object.PythonObjectFactory;
import com.oracle.graal.python.util.PythonUtils;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Exclusive;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.GenerateCached;
import com.oracle.truffle.api.dsl.GenerateInline;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.profiles.InlinedConditionProfile;
import com.oracle.truffle.api.strings.TruffleString;

@CoreFunctions(extendClasses = {PBufferedReader, PBufferedRandom})
public final class BufferedReaderMixinBuiltins extends AbstractBufferedIOBuiltins {
    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return BufferedReaderMixinBuiltinsFactory.getFactories();
    }

    /**
     * implementation of cpython/Modules/_io/bufferedio.c:_bufferedreader_raw_read
     */

    private static final byte[] BLOCKED = new byte[0];

    @GenerateInline
    @GenerateCached(false)
    abstract static class RawReadNode extends PNodeWithContext {

        public abstract byte[] execute(VirtualFrame frame, Node inliningTarget, PBuffered self, int len);

        // This is the spec way
        @Specialization
        static byte[] bufferedreaderRawRead(VirtualFrame frame, Node inliningTarget, PBuffered self, int len,
                        @Cached(inline = false) BytesNodes.ToBytesNode toBytes,
                        @Cached(inline = false) PythonObjectFactory factory,
                        @Cached PyObjectCallMethodObjArgs callMethodReadInto,
                        @Cached PyNumberAsSizeNode asSizeNode,
                        @Cached InlinedConditionProfile osError,
                        @Cached PRaiseNode.Lazy lazyRaiseNode) {
            PByteArray memobj = factory.createByteArray(new byte[len]);
            // TODO _PyIO_trap_eintr [GR-23297]
            Object res = callMethodReadInto.execute(frame, inliningTarget, self.getRaw(), T_READINTO, memobj);
            if (res == PNone.NONE) {
                /* Non-blocking stream would have blocked. Special return code! */
                return BLOCKED;
            }
            int n;
            try {
                n = asSizeNode.executeExact(frame, inliningTarget, res, ValueError);
            } catch (PException e) {
                throw lazyRaiseNode.get(inliningTarget).raiseWithCause(OSError, e, ErrorMessages.RAW_READINTO_FAILED);
            }
            if (osError.profile(inliningTarget, n < 0 || n > len)) {
                throw lazyRaiseNode.get(inliningTarget).raise(OSError, IO_S_INVALID_LENGTH, "readinto()", n, len);
            }
            if (n > 0 && self.getAbsPos() != -1) {
                self.incAbsPos(n);
            }
            if (n == 0) {
                return PythonUtils.EMPTY_BYTE_ARRAY;
            }
            byte[] bytes = toBytes.execute(memobj);
            if (n < len) {
                return PythonUtils.arrayCopyOf(bytes, n);
            }
            return bytes;
        }

    }

    /**
     * implementation of cpython/Modules/_io/bufferedio.c:_bufferedreader_fill_buffer
     */
    @GenerateInline
    @GenerateCached(false)
    abstract static class FillBufferNode extends PNodeWithContext {

        public abstract int execute(VirtualFrame frame, Node inliningTarget, PBuffered self);

        @Specialization
        static int bufferedreaderFillBuffer(VirtualFrame frame, Node inliningTarget, PBuffered self,
                        @Cached RawReadNode rawReadNode) {
            int start;
            if (isValidReadBuffer(self)) {
                start = self.getReadEnd();
            } else {
                start = 0;
            }
            int len = self.getBufferSize() - start;
            byte[] fill = rawReadNode.execute(frame, inliningTarget, self, len);
            if (fill == BLOCKED) {
                return -2;
            }
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

    @Builtin(name = J_READABLE, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class ReadableNode extends PythonUnaryWithInitErrorBuiltinNode {
        @Specialization(guards = "self.isOK()")
        static Object doit(VirtualFrame frame, PBuffered self,
                        @Bind("this") Node inliningTarget,
                        @Cached PyObjectCallMethodObjArgs callMethod) {
            return callMethod.execute(frame, inliningTarget, self.getRaw(), T_READABLE);
        }
    }

    /*
     * Generic read function: read from the stream until enough bytes are read, or until an EOF
     * occurs or until read() would block.
     */

    @Builtin(name = J_READ, minNumOfPositionalArgs = 1, parameterNames = {"$self", "size"})
    @ArgumentClinic(name = "size", conversion = ArgumentClinic.ClinicConversion.Int, defaultValue = "-1", useDefaultForNone = true)
    @ImportStatic({AbstractBufferedIOBuiltins.class})
    @GenerateNodeFactory
    abstract static class ReadNode extends PythonBinaryWithInitErrorClinicBuiltinNode {

        @Child private CheckIsClosedNode checkIsClosedNode = CheckIsClosedNodeGen.create(T_READ);

        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return BufferedReaderMixinBuiltinsClinicProviders.ReadNodeClinicProviderGen.INSTANCE;
        }

        protected static boolean isValidSize(int size) {
            return size >= -1;
        }

        protected static boolean isReadAll(int size) {
            return size == -1;
        }

        public static boolean isReadFast(PBuffered self, int size) {
            return size <= safeDowncast(self);
        }

        @Specialization(guards = {"self.isOK()", "size == 0"})
        Object empty(VirtualFrame frame, PBuffered self, @SuppressWarnings("unused") int size,
                        @Shared @Cached PythonObjectFactory factory) {
            checkIsClosedNode.execute(frame, self);
            return factory.createEmptyBytes();
        }

        /*
         * Read n bytes from the buffer if it can, otherwise return None. This function is simple
         * enough that it can run unlocked.
         */
        /**
         * implementation of cpython/Modules/_io/bufferedio.c:_bufferedreader_read_fast
         */

        public static byte[] bufferedreaderReadFast(PBuffered self, int size) {
            /* Fast path: the data to read is fully buffered. */
            byte[] res = PythonUtils.arrayCopyOfRange(self.getBuffer(), self.getPos(), self.getPos() + size);
            self.incPos(size);
            return res;
        }

        @Specialization(guards = {"self.isOK()", "size > 0", "isReadFast(self, size)"})
        Object readFast(VirtualFrame frame, PBuffered self, int size,
                        @Shared @Cached PythonObjectFactory factory) {
            checkIsClosedNode.execute(frame, self);
            return factory.createBytes(bufferedreaderReadFast(self, size));
        }

        /**
         * implementation of cpython/Modules/_io/bufferedio.c:_bufferedreader_read_generic
         */
        @Specialization(guards = {"self.isOK()", "size > 0", "!isReadFast(self, size)"})
        @SuppressWarnings("truffle-static-method") // checkIsClosedNode
        Object bufferedreaderReadGeneric(VirtualFrame frame, PBuffered self, int size,
                        @Bind("this") Node inliningTarget,
                        @Exclusive @Cached EnterBufferedNode lock,
                        @Cached RawReadNode rawReadNode,
                        @Cached FillBufferNode fillBufferNode,
                        @Exclusive @Cached FlushAndRewindUnlockedNode flushAndRewindUnlockedNode,
                        @Shared @Cached PythonObjectFactory factory) {
            checkIsClosedNode.execute(frame, self);
            try {
                lock.enter(inliningTarget, self);
                int currentSize = safeDowncast(self);
                if (size <= currentSize) {
                    return factory.createBytes(bufferedreaderReadFast(self, size));
                }
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
                    flushAndRewindUnlockedNode.execute(frame, inliningTarget, self);
                }
                self.resetRead(); // _bufferedreader_reset_buf
                while (remaining > 0) {
                    /*- We want to read a whole block at the end into buffer.
                    If we had readv() we could do this in one pass. */
                    int r = minusLastBlock(self, remaining);
                    if (r == 0) {
                        break;
                    }
                    byte[] fill = rawReadNode.execute(frame, inliningTarget, self, r);
                    if (fill == BLOCKED) {
                        r = -2;
                    } else {
                        r = fill.length;
                        PythonUtils.arraycopy(fill, 0, res, written, r);
                    }
                    if (r == 0 || r == -2) {
                        /* EOF occurred */
                        if (r == 0 || written > 0) {
                            return factory.createBytes(PythonUtils.arrayCopyOf(res, written));
                        }
                        return PNone.NONE;
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
                    int r = fillBufferNode.execute(frame, inliningTarget, self);
                    if (r == 0 || r == -2) {
                        /* EOF occurred */
                        if (r == 0 || written > 0) {
                            return factory.createBytes(PythonUtils.arrayCopyOf(res, written));
                        }
                        return PNone.NONE;
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

                return factory.createBytes(res);
            } finally {
                EnterBufferedNode.leave(self);
            }
        }

        public static final TruffleString T_READALL = tsLiteral("readall");

        /**
         * implementation of cpython/Modules/_io/bufferedio.c:_bufferedreader_read_all
         */
        @Specialization(guards = {"self.isOK()", "isReadAll(size)"})
        @SuppressWarnings("truffle-static-method") // checkIsClosedNode
        Object bufferedreaderReadAll(VirtualFrame frame, PBuffered self, @SuppressWarnings("unused") int size,
                        @Bind("this") Node inliningTarget,
                        @Exclusive @Cached EnterBufferedNode lock,
                        @Exclusive @Cached FlushAndRewindUnlockedNode flushAndRewindUnlockedNode,
                        @Cached("create(T_READALL)") LookupAttributeInMRONode readallAttr,
                        @Cached InlinedConditionProfile hasReadallProfile,
                        @Cached InlinedConditionProfile currentSize0Profile,
                        @Cached CallUnaryMethodNode dispatchGetattribute,
                        @Cached GetClassNode getClassNode,
                        @Cached PyObjectCallMethodObjArgs callMethod,
                        @CachedLibrary(limit = "1") PythonBufferAccessLibrary bufferLib,
                        @Shared @Cached PythonObjectFactory factory,
                        @Cached PRaiseNode.Lazy raiseNode) {
            checkIsClosedNode.execute(frame, self);
            try {
                lock.enter(inliningTarget, self);
                byte[] data = PythonUtils.EMPTY_BYTE_ARRAY;
                /* First copy what we have in the current buffer. */
                int currentSize = safeDowncast(self);
                if (currentSize0Profile.profile(inliningTarget, currentSize != 0)) {
                    data = PythonUtils.arrayCopyOfRange(self.getBuffer(), self.getPos(), self.getPos() + currentSize);
                    self.incPos(currentSize);
                }

                /* We're going past the buffer's bounds, flush it */
                if (self.isWritable()) {
                    flushAndRewindUnlockedNode.execute(frame, inliningTarget, self);
                }

                self.resetRead(); // _bufferedreader_reset_buf

                Object clazz = getClassNode.execute(inliningTarget, self.getRaw());
                Object readall = readallAttr.execute(clazz);
                if (hasReadallProfile.profile(inliningTarget, readall != PNone.NO_VALUE)) {
                    Object tmp = dispatchGetattribute.executeObject(frame, readall, self.getRaw());
                    if (tmp != PNone.NONE && !(tmp instanceof PBytes)) {
                        throw raiseNode.get(inliningTarget).raise(TypeError, IO_S_SHOULD_RETURN_BYTES, "readall()");
                    }
                    if (currentSize0Profile.profile(inliningTarget, currentSize != 0)) {
                        if (tmp != PNone.NONE) {
                            int bytesLen = bufferLib.getBufferLength(tmp);
                            byte[] res = new byte[data.length + bytesLen];
                            PythonUtils.arraycopy(data, 0, res, 0, data.length);
                            bufferLib.readIntoByteArray(tmp, 0, res, data.length, bytesLen);
                            return factory.createBytes(res);
                        }
                        return factory.createBytes(data);
                    } else {
                        return tmp;
                    }
                }

                ByteArrayOutputStream chunks = createOutputStream();

                int dataLen = data.length;
                while (true) {
                    if (dataLen != 0) {
                        append(chunks, data, dataLen);
                        dataLen = 0;
                    }

                    /* Read until EOF or until read() would block. */
                    Object r = callMethod.execute(frame, inliningTarget, self.getRaw(), T_READ);
                    if (r != PNone.NONE && !(r instanceof PBytes)) {
                        throw raiseNode.get(inliningTarget).raise(TypeError, IO_S_SHOULD_RETURN_BYTES, "read()");
                    }
                    if (r != PNone.NONE) {
                        dataLen = bufferLib.getBufferLength(r);
                        data = bufferLib.getInternalOrCopiedByteArray(r);
                    }
                    if (dataLen == 0) {
                        if (currentSize == 0) {
                            return r;
                        } else {
                            return factory.createBytes(toByteArray(chunks));
                        }
                    }
                    currentSize += dataLen;
                    if (self.getAbsPos() != -1) {
                        self.incAbsPos(dataLen);
                    }
                }
            } finally {
                EnterBufferedNode.leave(self);
            }
        }

        @SuppressWarnings("unused")
        @Specialization(guards = {"self.isOK()", "!isValidSize(size)"})
        static Object initError(VirtualFrame frame, PBuffered self, int size,
                        @Cached PRaiseNode raiseNode) {
            throw raiseNode.raise(ValueError, MUST_BE_NON_NEG_OR_NEG_1);
        }
    }

    @Builtin(name = J_READ1, minNumOfPositionalArgs = 1, parameterNames = {"$self", "size"})
    @ArgumentClinic(name = "size", conversion = ArgumentClinic.ClinicConversion.Int, defaultValue = "-1", useDefaultForNone = true)
    @ImportStatic(IONodes.class)
    @GenerateNodeFactory
    abstract static class Read1Node extends PythonBinaryWithInitErrorClinicBuiltinNode {
        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return BufferedReaderMixinBuiltinsClinicProviders.Read1NodeClinicProviderGen.INSTANCE;
        }

        @Specialization(guards = "self.isOK()")
        static PBytes doit(VirtualFrame frame, PBuffered self, int size,
                        @Bind("this") Node inliningTarget,
                        @Cached EnterBufferedNode lock,
                        @Cached("create(T_READ)") CheckIsClosedNode checkIsClosedNode,
                        @Cached RawReadNode rawReadNode,
                        @Cached PythonObjectFactory factory) {
            checkIsClosedNode.execute(frame, self);
            int n = size;
            if (n < 0) {
                n = self.getBufferSize();
            }

            if (n == 0) {
                return factory.createEmptyBytes();
            }
            /*- Return up to n bytes.  If at least one byte is buffered, we
               only return buffered bytes.  Otherwise, we do one raw read. */

            int have = safeDowncast(self);
            if (have > 0) {
                n = have < n ? have : n;
                byte[] b = ReadNode.bufferedreaderReadFast(self, n);
                return factory.createBytes(b);
            }
            try {
                lock.enter(inliningTarget, self);
                self.resetRead(); // _bufferedreader_reset_buf
                byte[] fill = rawReadNode.execute(frame, inliningTarget, self, n);
                return factory.createBytes(fill == BLOCKED ? PythonUtils.EMPTY_BYTE_ARRAY : fill);
            } finally {
                EnterBufferedNode.leave(self);
            }
        }
    }

    @Builtin(name = J_READINTO, minNumOfPositionalArgs = 2, numOfPositionalOnlyArgs = 2, parameterNames = {"$self", "buffer"})
    @ArgumentClinic(name = "buffer", conversion = ArgumentClinic.ClinicConversion.WritableBuffer)
    @GenerateNodeFactory
    abstract static class ReadIntoNode extends PythonBinaryWithInitErrorClinicBuiltinNode {

        @Child private CheckIsClosedNode checkIsClosedNode = CheckIsClosedNodeGen.create(T_READLINE);

        /**
         * implementation of cpython/Modules/_io/bufferedio.c:_buffered_readinto_generic
         */
        @Specialization(guards = "self.isOK()", limit = "3")
        @SuppressWarnings("truffle-static-method")
        Object bufferedReadintoGeneric(VirtualFrame frame, PBuffered self, Object buffer,
                        @Bind("this") Node inliningTarget,
                        @Cached("createFor(this)") IndirectCallData indirectCallData,
                        @CachedLibrary("buffer") PythonBufferAccessLibrary bufferLib,
                        @Cached EnterBufferedNode lock,
                        @Cached FlushAndRewindUnlockedNode flushAndRewindUnlockedNode,
                        @Cached RawReadNode rawReadNode,
                        @Cached FillBufferNode fillBufferNode) {
            checkIsClosedNode.execute(frame, self);
            try {
                lock.enter(inliningTarget, self);
                int bufLen = bufferLib.getBufferLength(buffer);
                int written = 0;
                int n = safeDowncast(self);
                if (n > 0) {
                    if (n >= bufLen) {
                        // memcpy(buffer, self.buffer + self.pos, buffer.length);
                        bufferLib.writeFromByteArray(buffer, 0, self.getBuffer(), self.getPos(), bufLen);
                        self.incPos(bufLen);
                        return bufLen;
                    }
                    // memcpy(buffer, self.buffer + self.pos, n);
                    bufferLib.writeFromByteArray(buffer, 0, self.getBuffer(), self.getPos(), n);
                    self.incPos(n);
                    written = n;
                }

                if (self.isWritable()) {
                    flushAndRewindUnlockedNode.execute(frame, inliningTarget, self);
                }

                self.resetRead(); // _bufferedreader_reset_buf
                self.setPos(0);

                for (int remaining = bufLen - written; remaining > 0; written += n, remaining -= n) {
                    /*-
                     If remaining bytes is larger than internal buffer size, copy directly into
                     caller's buffer.
                     */
                    if (remaining > self.getBufferSize()) {
                        byte[] fill = rawReadNode.execute(frame, inliningTarget, self, remaining);
                        if (fill == BLOCKED) {
                            n = -2;
                        } else {
                            n = fill.length;
                            bufferLib.writeFromByteArray(buffer, written, fill, 0, n);
                        }
                    } else if (!(isReadinto1Mode() && written != 0)) {
                        /*-
                        In readinto1 mode, we do not want to fill the internal buffer if we already have
                        some data to return
                        */
                        n = fillBufferNode.execute(frame, inliningTarget, self);
                        if (n > 0) {
                            if (n > remaining) {
                                n = remaining;
                            }
                            // memcpy(buffer.buf + written, self.buffer + self.pos, n);
                            bufferLib.writeFromByteArray(buffer, written, self.getBuffer(), self.getPos(), n);
                            self.incPos(n);
                            continue; /* short circuit */
                        }
                    } else {
                        n = 0;
                    }

                    if (n == 0 || (n == -2 && written > 0)) {
                        break;
                    }
                    if (n == -2) {
                        return PNone.NONE;
                    }
                    /* At most one read in readinto1 mode */
                    if (isReadinto1Mode()) {
                        written += n;
                        break;
                    }
                }

                return written;
            } finally {
                EnterBufferedNode.leave(self);
                bufferLib.release(buffer, frame, indirectCallData);
            }
        }

        protected boolean isReadinto1Mode() {
            return false;
        }

        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return BufferedReaderMixinBuiltinsClinicProviders.ReadIntoNodeClinicProviderGen.INSTANCE;
        }
    }

    @Builtin(name = J_READINTO1, minNumOfPositionalArgs = 2, numOfPositionalOnlyArgs = 2, parameterNames = {"$self", "buffer"})
    @ArgumentClinic(name = "buffer", conversion = ArgumentClinic.ClinicConversion.WritableBuffer)
    @GenerateNodeFactory
    abstract static class ReadInto1Node extends ReadIntoNode {
        @Override
        protected boolean isReadinto1Mode() {
            return true;
        }

        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return BufferedReaderMixinBuiltinsClinicProviders.ReadInto1NodeClinicProviderGen.INSTANCE;
        }
    }

    /**
     * implementation of cpython/Modules/_io/bufferedio.c:_buffered_readline
     */
    @GenerateInline
    @GenerateCached(false)
    abstract static class BufferedReadlineNode extends PNodeWithContext {

        public abstract byte[] execute(VirtualFrame frame, Node inliningTarget, PBuffered self, int size);

        @Specialization
        static byte[] readline(VirtualFrame frame, Node inliningTarget, PBuffered self, int size,
                        @Cached EnterBufferedNode lock,
                        @Cached FlushAndRewindUnlockedNode flushAndRewindUnlockedNode,
                        @Cached FillBufferNode fillBufferNode,
                        @Cached InlinedConditionProfile notFound,
                        @Cached InlinedConditionProfile reachedLimit) {
            int limit = size;
            /*-
                First, try to find a line in the buffer. This can run unlocked because
                the calls to the C API are simple enough that they can't trigger
                any thread switch.
            */
            int n = safeDowncast(self);
            if (limit >= 0 && n > limit) {
                n = limit;
            }
            int idx = BytesUtils.memchr(self.getBuffer(), self.getPos(), (byte) '\n', n);
            if (notFound.profile(inliningTarget, idx != -1)) {
                byte[] res = PythonUtils.arrayCopyOfRange(self.getBuffer(), self.getPos(), idx + 1);
                self.incPos(idx - self.getPos() + 1);
                return res;
            }
            if (reachedLimit.profile(inliningTarget, n == limit)) {
                byte[] res = new byte[n];
                PythonUtils.arraycopy(self.getBuffer(), self.getPos(), res, 0, n);
                self.incPos(n);
                return res;
            }

            lock.enter(inliningTarget, self);
            try {
                /* Now we try to get some more from the raw stream */
                ByteArrayOutputStream chunks = createOutputStream();
                if (n > 0) {
                    append(chunks, self.getBuffer(), self.getPos(), n);
                    self.incPos(n);
                    if (limit >= 0) {
                        limit -= n;
                    }
                }
                if (self.isWritable()) {
                    flushAndRewindUnlockedNode.execute(frame, inliningTarget, self);
                }

                while (true) {
                    self.resetRead(); // _bufferedreader_reset_buf
                    n = fillBufferNode.execute(frame, inliningTarget, self);
                    if (n <= 0) {
                        break;
                    }
                    if (limit >= 0 && n > limit) {
                        n = limit;
                    }
                    int end = n;
                    int s = 0;
                    while (s < end) {
                        if (self.getBuffer()[s++] == '\n') {
                            append(chunks, self.getBuffer(), 0, s);
                            self.setPos(s);
                            return toByteArray(chunks);
                        }
                    }
                    if (n == limit) {
                        append(chunks, self.getBuffer(), 0, n);
                        self.setPos(n);
                        return toByteArray(chunks);
                    }
                    append(chunks, self.getBuffer(), 0, n);
                    if (limit >= 0) {
                        limit -= n;
                    }
                }
                return toByteArray(chunks);
            } finally {
                EnterBufferedNode.leave(self);
            }
        }
    }

    @Builtin(name = J_READLINE, minNumOfPositionalArgs = 1, parameterNames = {"$self", "size"})
    @ArgumentClinic(name = "size", conversion = ArgumentClinic.ClinicConversion.Int, defaultValue = "-1", useDefaultForNone = true)
    @ImportStatic(IONodes.class)
    @GenerateNodeFactory
    abstract static class ReadlineNode extends PythonBinaryWithInitErrorClinicBuiltinNode {
        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return BufferedReaderMixinBuiltinsClinicProviders.ReadlineNodeClinicProviderGen.INSTANCE;
        }

        @Specialization(guards = "self.isOK()")
        static PBytes doit(VirtualFrame frame, PBuffered self, int size,
                        @Bind("this") Node inliningTarget,
                        @Cached("create(T_READLINE)") CheckIsClosedNode checkIsClosedNode,
                        @Cached BufferedReadlineNode readlineNode,
                        @Cached PythonObjectFactory factory) {
            checkIsClosedNode.execute(frame, self);
            byte[] res = readlineNode.execute(frame, inliningTarget, self, size);
            return factory.createBytes(res);
        }
    }

    @Builtin(name = J_PEEK, minNumOfPositionalArgs = 1, parameterNames = {"$self", "size"})
    @ArgumentClinic(name = "size", conversion = ArgumentClinic.ClinicConversion.Index, defaultValue = "0", useDefaultForNone = true)
    @ImportStatic(IONodes.class)
    @GenerateNodeFactory
    abstract static class PeekNode extends PythonBinaryWithInitErrorClinicBuiltinNode {
        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return BufferedReaderMixinBuiltinsClinicProviders.PeekNodeClinicProviderGen.INSTANCE;
        }

        /**
         * implementation of cpython/Modules/_io/bufferedio.c:_bufferedreader_peek_unlocked
         */
        static byte[] bufferedreaderPeekUnlocked(VirtualFrame frame, Node inliningTarget, PBuffered self,
                        FillBufferNode fillBufferNode) {
            int have = safeDowncast(self);
            /*-
             * Constraints:
             * 1. we don't want to advance the file position.
             * 2. we don't want to lose block alignment, so we can't shift the buffer to make some place.
             * Therefore, we either return `have` bytes (if > 0), or a full buffer.
             */
            if (have > 0) {
                return PythonUtils.arrayCopyOfRange(self.getBuffer(), self.getPos(), self.getPos() + have);
            }

            /* Fill the buffer from the raw stream, and copy it to the result. */
            self.resetRead(); // _bufferedreader_reset_buf
            int r = fillBufferNode.execute(frame, inliningTarget, self);
            if (r == -2) {
                r = 0;
            }
            self.setPos(0);
            return PythonUtils.arrayCopyOf(self.getBuffer(), r);
        }

        @Specialization(guards = "self.isOK()")
        static Object doit(VirtualFrame frame, PBuffered self, @SuppressWarnings("unused") int size,
                        @Bind("this") Node inliningTarget,
                        @Cached EnterBufferedNode lock,
                        @Cached("create(T_PEEK)") CheckIsClosedNode checkIsClosedNode,
                        @Cached FillBufferNode fillBufferNode,
                        @Cached FlushAndRewindUnlockedNode flushAndRewindUnlockedNode,
                        @Cached PythonObjectFactory factory) {
            checkIsClosedNode.execute(frame, self);
            try {
                lock.enter(inliningTarget, self);
                if (self.isWritable()) {
                    flushAndRewindUnlockedNode.execute(frame, inliningTarget, self);
                }
                return factory.createBytes(bufferedreaderPeekUnlocked(frame, inliningTarget, self, fillBufferNode));
            } finally {
                EnterBufferedNode.leave(self);
            }
        }
    }

    @Builtin(name = J___NEXT__, minNumOfPositionalArgs = 1)
    @ImportStatic(IONodes.class)
    @GenerateNodeFactory
    abstract static class NextNode extends PythonUnaryWithInitErrorBuiltinNode {

        @Specialization(guards = "self.isOK()")
        static PBytes doit(VirtualFrame frame, PBuffered self,
                        @Bind("this") Node inliningTarget,
                        @Cached("create(T_READLINE)") CheckIsClosedNode checkIsClosedNode,
                        @Cached BufferedReadlineNode readlineNode,
                        @Cached PythonObjectFactory factory,
                        @Cached PRaiseNode.Lazy raiseNode) {
            checkIsClosedNode.execute(frame, self);
            byte[] line = readlineNode.execute(frame, inliningTarget, self, -1);
            if (line.length == 0) {
                throw raiseNode.get(inliningTarget).raiseStopIteration();
            }
            return factory.createBytes(line);
        }
    }
}
